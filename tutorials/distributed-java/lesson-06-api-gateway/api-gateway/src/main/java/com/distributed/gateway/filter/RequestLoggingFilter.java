package com.distributed.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Request Logging Filter - Ghi log toàn bộ request và response đi qua Gateway.
 *
 * Log bao gồm:
 * - Khi request đến: method, path, query params, headers quan trọng
 * - Khi response trả về: status code, thời gian xử lý (duration)
 *
 * Kỹ thuật: Lưu startTime vào ServerWebExchange attributes,
 * sau đó đọc lại khi response hoàn thành để tính duration.
 *
 * Thứ tự: HIGHEST_PRECEDENCE + 2 để chạy sau JwtAuthFilter nhưng trước các filter khác.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // Key để lưu start time trong exchange attributes
    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Ghi lại thời điểm bắt đầu xử lý request
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        ServerHttpRequest request = exchange.getRequest();

        // Log thông tin request đến
        logRequest(request);

        // Tiếp tục chain và log response khi hoàn thành
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> logResponse(exchange, startTime)));
    }

    /**
     * Ghi log thông tin request đến.
     * Bao gồm method, path, query string và các headers quan trọng.
     */
    private void logRequest(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String query = request.getQueryParams().isEmpty()
                ? ""
                : "?" + request.getQueryParams().toSingleValueMap().toString();

        // Log user agent và content type (nếu có)
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String contentType = request.getHeaders().getFirst("Content-Type");
        String requestId = request.getHeaders().getFirst("X-Request-Id");

        log.info("→ REQUEST | {} {}{} | RequestId: {} | UserAgent: {} | ContentType: {}",
                method, path, query,
                requestId != null ? requestId : "N/A",
                userAgent != null ? truncate(userAgent, 50) : "N/A",
                contentType != null ? contentType : "N/A");

        // Log headers chi tiết ở DEBUG level (quá verbose cho INFO)
        if (log.isDebugEnabled()) {
            request.getHeaders().forEach((name, values) ->
                    log.debug("  Header: {} = {}", name, values));
        }
    }

    /**
     * Ghi log thông tin response.
     * Tính thời gian xử lý bằng cách trừ startTime đã lưu trước đó.
     */
    private void logResponse(ServerWebExchange exchange, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        // Lấy status code của response
        int statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;

        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Log với màu sắc theo status code:
        // 2xx → INFO, 4xx → WARN, 5xx → ERROR
        if (statusCode >= 500) {
            log.error("← RESPONSE | {} {} | Status: {} | Duration: {}ms",
                    method, path, statusCode, duration);
        } else if (statusCode >= 400) {
            log.warn("← RESPONSE | {} {} | Status: {} | Duration: {}ms",
                    method, path, statusCode, duration);
        } else {
            log.info("← RESPONSE | {} {} | Status: {} | Duration: {}ms",
                    method, path, statusCode, duration);
        }

        // Cảnh báo nếu request quá chậm (> 3 giây)
        if (duration > 3000) {
            log.warn("⚠ SLOW REQUEST detected: {} {} took {}ms", method, path, duration);
        }
    }

    /**
     * Cắt ngắn chuỗi nếu quá dài để log không bị quá verbose.
     */
    private String truncate(String s, int maxLength) {
        if (s == null) return null;
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
    }
}
