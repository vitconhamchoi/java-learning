package com.distributed.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Gateway Route Configuration - Cấu hình routes theo cách programmatic (Java code).
 *
 * Ưu điểm của programmatic routing so với YAML:
 * - Type-safe: compile-time checking
 * - Dễ test hơn
 * - Có thể dùng conditional logic
 * - Tích hợp với Spring dependency injection
 *
 * Lưu ý: Routes trong class này CỘNG THÊM với routes trong application.yml.
 * Thứ tự ưu tiên: route có order thấp hơn được kiểm tra trước.
 */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    /**
     * Định nghĩa các routes programmatically.
     *
     * Route matching theo thứ tự đăng ký:
     * 1. order-service route: /api/orders/** → order-service
     * 2. product-service route: /api/products/** → product-service  
     * 3. fallback route: tất cả còn lại → trả về 503
     */
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                // Route 1: Order Service
                // /api/orders/123 → StripPrefix(1) → /orders/123 → order-service
                .route("order-service-programmatic", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                // Xóa prefix đầu tiên: /api/orders → /orders
                                .stripPrefix(1)
                                // Thêm header để downstream service biết request đến từ Gateway
                                .addRequestHeader("X-Gateway-Source", "api-gateway")
                                // Thêm response header để client biết service nào xử lý
                                .addResponseHeader("X-Served-By", "order-service")
                                // Retry tối đa 3 lần khi 503 Service Unavailable
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(HttpStatus.SERVICE_UNAVAILABLE,
                                                HttpStatus.BAD_GATEWAY))
                        )
                        // lb:// = load balanced, sử dụng Eureka để resolve địa chỉ
                        .uri("lb://order-service"))

                // Route 2: Product Service
                // /api/products/456 → StripPrefix(1) → /products/456 → product-service
                .route("product-service-programmatic", r -> r
                        .path("/api/products/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Gateway-Source", "api-gateway")
                                .addResponseHeader("X-Served-By", "product-service")
                                // Rewrite path: /api/products → /products
                                // (StripPrefix và RewritePath có thể dùng thay nhau)
                                .circuitBreaker(config -> config
                                        .setName("productServiceCB")
                                        // Khi circuit breaker open, redirect đến fallback endpoint
                                        .setFallbackUri("forward:/fallback/product"))
                        )
                        .uri("lb://product-service"))

                // Route 3: Upstream Service (demo trực tiếp - không qua Eureka)
                // Dùng cho local testing khi không có Eureka
                .route("upstream-service-direct", r -> r
                        .path("/direct/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Via-Gateway", "true")
                        )
                        .uri("http://localhost:8081"))

                // Route 4: Health check aggregation
                // Tổng hợp health từ nhiều services (đơn giản hóa)
                .route("health-aggregated", r -> r
                        .path("/health/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("http://localhost:8081"))

                .build();
    }
}
