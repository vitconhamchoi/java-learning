package com.distributed.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Authentication Filter - Chạy trước tất cả filters khác.
 *
 * Luồng xử lý:
 * 1. Đọc Authorization header từ request
 * 2. Extract Bearer token
 * 3. Validate JWT signature và expiry
 * 4. Nếu hợp lệ: thêm X-User-Id và X-User-Role vào request header cho downstream
 * 5. Nếu không hợp lệ: trả về 401 Unauthorized với JSON body
 *
 * Sử dụng GlobalFilter để áp dụng cho TẤT CẢ routes.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // Danh sách các path không cần xác thực
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Value("${jwt.secret:mySecretKey12345678901234567890123456789012}")
    private String jwtSecret;

    /**
     * Thứ tự thực thi: HIGHEST_PRECEDENCE + 1 đảm bảo filter này chạy rất sớm,
     * ngay sau các system-level filters.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Bỏ qua các public endpoints không cần auth
        if (isPublicPath(path)) {
            log.debug("Public path, skipping JWT validation: {}", path);
            return chain.filter(exchange);
        }

        // Lấy Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        // Kiểm tra header tồn tại và có định dạng "Bearer <token>"
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return writeUnauthorizedResponse(exchange, "Authorization header thiếu hoặc không hợp lệ");
        }

        // Extract token (bỏ prefix "Bearer ")
        String token = authHeader.substring(7);

        try {
            // Parse và validate JWT token
            Claims claims = parseToken(token);

            // Lấy thông tin user từ claims
            String userId = claims.getSubject();
            String userRole = claims.get("role", String.class);
            String userEmail = claims.get("email", String.class);

            log.debug("JWT validated for user: {}, role: {}", userId, userRole);

            // Thêm thông tin user vào request headers để downstream services sử dụng
            // Downstream services có thể trust các headers này vì chỉ Gateway mới set chúng
            ServerHttpRequest mutatedRequest = exchange.getRequest()
                    .mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Role", userRole != null ? userRole : "USER")
                    .header("X-User-Email", userEmail != null ? userEmail : "")
                    .build();

            // Tiếp tục filter chain với request đã được mutate
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            // Token không hợp lệ: signature sai, expired, malformed...
            log.warn("Invalid JWT token for path {}: {}", path, e.getMessage());
            return writeUnauthorizedResponse(exchange, "Token không hợp lệ hoặc đã hết hạn");
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return writeUnauthorizedResponse(exchange, "Lỗi xác thực token");
        }
    }

    /**
     * Parse và validate JWT token.
     * Sử dụng JJWT 0.12.x API với Jwts.parser()
     */
    private Claims parseToken(String token) {
        // Tạo secret key từ chuỗi bí mật (phải đủ độ dài cho HS256)
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)        // Set key để verify signature
                .build()
                .parseSignedClaims(token)   // Parse và validate (throws exception nếu invalid)
                .getPayload();
    }

    /**
     * Kiểm tra xem path có phải là public endpoint không.
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Trả về response 401 Unauthorized với JSON body.
     * Vì Gateway là reactive, phải trả về Mono<Void>.
     */
    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Tạo JSON response body
        String body = """
                {
                    "error": "Unauthorized",
                    "message": "%s",
                    "status": 401
                }
                """.formatted(message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        // Ghi response và complete
        return response.writeWith(Mono.just(buffer));
    }
}
