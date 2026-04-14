package com.distributed.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate Limit Filter - Giới hạn số request từ mỗi IP.
 *
 * Thuật toán: Token Bucket
 * - Mỗi IP có một "bucket" chứa tối đa 10 tokens
 * - Mỗi giây, bucket được refill về 10 tokens
 * - Mỗi request tiêu thụ 1 token
 * - Nếu bucket rỗng (token = 0): trả về 429 Too Many Requests
 *
 * Cải tiến: ConcurrentHashMap và AtomicLong đảm bảo thread-safety
 * trong môi trường reactive/multi-threaded.
 *
 * Lưu ý Production: Nên dùng Redis cho distributed rate limiting
 * khi có nhiều Gateway instances.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Số request tối đa mỗi giây cho mỗi IP
    private static final long MAX_REQUESTS_PER_SECOND = 10;

    // Lưu trạng thái token bucket cho từng IP
    // Key: IP address, Value: [lastRefillTime (millis), currentTokens]
    private final ConcurrentHashMap<String, long[]> ipBuckets = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Lấy địa chỉ IP của client
        String clientIp = getClientIp(exchange);

        // Kiểm tra rate limit cho IP này
        if (!allowRequest(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return writeTooManyRequestsResponse(exchange, clientIp);
        }

        return chain.filter(exchange);
    }

    /**
     * Kiểm tra và cập nhật token bucket cho IP.
     * Trả về true nếu request được phép, false nếu bị rate limit.
     *
     * Thread-safe: Sử dụng synchronized trên bucket array để tránh race condition.
     */
    private boolean allowRequest(String clientIp) {
        long now = Instant.now().toEpochMilli();

        // Tạo bucket mới cho IP nếu chưa tồn tại
        // bucket[0] = lastRefillTime (milliseconds)
        // bucket[1] = currentTokens
        long[] bucket = ipBuckets.computeIfAbsent(clientIp,
                k -> new long[]{now, MAX_REQUESTS_PER_SECOND});

        synchronized (bucket) {
            long lastRefillTime = bucket[0];
            long currentTokens = bucket[1];

            // Tính số tokens cần refill dựa trên thời gian đã trôi qua
            long elapsedMs = now - lastRefillTime;
            long tokensToAdd = (elapsedMs * MAX_REQUESTS_PER_SECOND) / 1000;

            if (tokensToAdd > 0) {
                // Refill tokens, không vượt quá max capacity
                currentTokens = Math.min(MAX_REQUESTS_PER_SECOND, currentTokens + tokensToAdd);
                bucket[0] = now;  // Cập nhật thời gian refill cuối
                bucket[1] = currentTokens;
            }

            // Kiểm tra có đủ token không
            if (currentTokens > 0) {
                bucket[1] = currentTokens - 1;  // Tiêu thụ 1 token
                return true;
            } else {
                return false;  // Bucket rỗng, từ chối request
            }
        }
    }

    /**
     * Lấy IP thực của client.
     * Ưu tiên X-Forwarded-For (khi có proxy/load balancer phía trước),
     * fallback về remote address.
     */
    private String getClientIp(ServerWebExchange exchange) {
        // Kiểm tra X-Forwarded-For header (set bởi load balancer/proxy)
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For có thể có nhiều IPs: "client, proxy1, proxy2"
            // IP thực của client là phần đầu tiên
            return forwardedFor.split(",")[0].trim();
        }

        // Fallback: lấy từ remote address
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * Trả về response 429 Too Many Requests.
     * Bao gồm header Retry-After để client biết khi nào có thể thử lại.
     */
    private Mono<Void> writeTooManyRequestsResponse(ServerWebExchange exchange, String clientIp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Thêm Retry-After header (giây) - RFC 6585
        response.getHeaders().set("Retry-After", "1");
        response.getHeaders().set("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_SECOND));
        response.getHeaders().set("X-RateLimit-Remaining", "0");

        String body = """
                {
                    "error": "Too Many Requests",
                    "message": "Vượt quá giới hạn %d requests/giây. Vui lòng thử lại sau.",
                    "status": 429,
                    "retryAfter": 1
                }
                """.formatted(MAX_REQUESTS_PER_SECOND);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
