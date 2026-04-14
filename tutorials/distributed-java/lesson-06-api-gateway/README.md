# Lesson 06: API Gateway Pattern

## Giới thiệu

API Gateway là một design pattern quan trọng trong kiến trúc microservices. Thay vì để client gọi trực tiếp đến từng service, tất cả request đều đi qua một điểm trung tâm gọi là API Gateway.

## Tại sao cần API Gateway?

Trong kiến trúc microservices, hệ thống thường có hàng chục hoặc hàng trăm service nhỏ. Nếu không có API Gateway:

- **Client phải biết địa chỉ của từng service** → coupling cao
- **Authentication/Authorization phải implement ở mỗi service** → code trùng lặp
- **Khó kiểm soát rate limiting** → dễ bị DDoS
- **CORS phải cấu hình ở mỗi service** → phức tạp
- **Logging và monitoring phân tán** → khó debug
- **SSL termination phải ở mỗi service** → tốn tài nguyên

API Gateway giải quyết tất cả vấn đề trên bằng cách tập trung tại một điểm.

```
                    ┌─────────────────────────────────────────┐
                    │              API GATEWAY                 │
  Client ──────────►│  Auth | Rate Limit | Logging | CORS      │
                    │  Load Balance | Circuit Breaker          │
                    └──────┬──────────┬──────────┬────────────┘
                           │          │          │
                    ┌──────▼──┐  ┌────▼───┐  ┌──▼─────┐
                    │ Order   │  │Product │  │Payment │
                    │Service  │  │Service │  │Service │
                    └─────────┘  └────────┘  └────────┘
```

## Spring Cloud Gateway

Spring Cloud Gateway là implementation phổ biến nhất cho API Gateway trong hệ sinh thái Spring. Nó được xây dựng trên **Netty** và **Project Reactor**, hoạt động theo mô hình **reactive/non-blocking**.

### Tại sao Netty thay vì Tomcat?

| Tiêu chí | Tomcat (Servlet) | Netty (Reactive) |
|----------|-----------------|------------------|
| Thread model | Thread per request | Event loop |
| Blocking I/O | Có | Không |
| Memory | Cao (mỗi thread ~1MB) | Thấp |
| Throughput | Thấp hơn | Cao hơn |
| Back-pressure | Không hỗ trợ | Hỗ trợ đầy đủ |

### Kiến trúc xử lý request

```
Request
   │
   ▼
┌──────────────────────────────────────────┐
│           Gateway Handler Mapping         │
│  (Tìm Route phù hợp với predicates)      │
└────────────────┬─────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│         Gateway Web Handler              │
│  (Thực thi chain of filters)             │
└────────────────┬─────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│              Filter Chain                 │
│                                          │
│  GlobalFilter 1 (JWT Auth)               │
│  GlobalFilter 2 (Rate Limit)             │
│  GlobalFilter 3 (Logging)                │
│  Route Filter 1 (StripPrefix)            │
│  Route Filter 2 (AddRequestHeader)       │
└────────────────┬─────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│           Upstream Service               │
└──────────────────────────────────────────┘
```

## Route Configuration trong YAML

Route trong Spring Cloud Gateway gồm 3 thành phần chính:
- **Predicate**: Điều kiện để match request
- **Filter**: Xử lý request/response
- **URI**: Địa chỉ upstream service

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Route 1: Forward request đến order-service
        - id: order-service
          uri: lb://order-service          # lb:// = load balanced qua Eureka
          predicates:
            - Path=/api/orders/**          # Match nếu path bắt đầu bằng /api/orders/
            - Method=GET,POST              # Chỉ GET và POST
            - Header=X-Request-Id, \d+    # Header phải match regex
          filters:
            - StripPrefix=1               # Xóa /api trước khi forward
            - AddRequestHeader=X-Gateway-Source, spring-gateway
            - AddResponseHeader=X-Response-Time, {responseTime}
            - RewritePath=/api/(?<segment>.*), /$\{segment}

        # Route 2: Redirect cũ sang mới
        - id: legacy-redirect
          uri: https://new-api.example.com
          predicates:
            - Path=/old-api/**
          filters:
            - RedirectTo=301, https://new-api.example.com

        # Route 3: Circuit Breaker
        - id: resilient-route
          uri: lb://payment-service
          predicates:
            - Path=/api/payment/**
          filters:
            - name: CircuitBreaker
              args:
                name: paymentCircuitBreaker
                fallbackUri: forward:/fallback/payment
```

## Built-in Filters

### 1. StripPrefix Filter
```yaml
filters:
  - StripPrefix=2
# /api/v1/orders/123 → /orders/123
```

### 2. RewritePath Filter
```yaml
filters:
  - RewritePath=/service/(?<segment>.*), /$\{segment}
# /service/users/123 → /users/123
```

### 3. AddRequestHeader Filter
```yaml
filters:
  - AddRequestHeader=X-Tenant-Id, tenant-abc
  - AddRequestHeader=X-Gateway-Version, 1.0
```

### 4. AddResponseHeader Filter
```yaml
filters:
  - AddResponseHeader=X-Response-Source, api-gateway
  - AddResponseHeader=Strict-Transport-Security, max-age=31536000
```

### 5. RedirectTo Filter
```yaml
filters:
  - RedirectTo=301, https://newsite.com
  - RedirectTo=302, /new-path
```

### 6. RequestRateLimiter Filter (Built-in Redis-based)
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10    # tokens/second
      redis-rate-limiter.burstCapacity: 20    # max burst
      redis-rate-limiter.requestedTokens: 1   # tokens per request
      key-resolver: "#{@ipKeyResolver}"
```

## Custom Filters

### JWT Authentication Filter

```java
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    // Chạy trước tất cả filters khác
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange);
        if (token == null) {
            return unauthorized(exchange);
        }
        try {
            Claims claims = validateToken(token);
            // Thêm thông tin user vào header để downstream service sử dụng
            ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Role", claims.get("role", String.class))
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException e) {
            return unauthorized(exchange);
        }
    }
}
```

### Rate Limiting Filter (Token Bucket)

```
Token Bucket Algorithm:
┌──────────────────────────────────────────┐
│  Bucket có capacity = 10 tokens          │
│  Refill 10 tokens/second                 │
│                                          │
│  Request đến → lấy 1 token              │
│  Nếu bucket rỗng → 429 Too Many Requests │
└──────────────────────────────────────────┘
```

### Request Logging Filter

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startTime = System.currentTimeMillis();
    
    // Lưu start time vào exchange attributes
    exchange.getAttributes().put("startTime", startTime);
    
    log.info("→ {} {}", exchange.getRequest().getMethod(), 
             exchange.getRequest().getPath());
    
    return chain.filter(exchange)
        .then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - 
                           (Long) exchange.getAttributes().get("startTime");
            log.info("← {} {} {}ms", 
                    exchange.getResponse().getStatusCode(),
                    exchange.getRequest().getPath(),
                    duration);
        }));
}
```

## CORS Configuration

Cross-Origin Resource Sharing phải được cấu hình ở Gateway level:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "https://app.example.com"
              - "https://admin.example.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - Authorization
              - Content-Type
              - X-Requested-With
            exposedHeaders:
              - X-Total-Count
              - X-Request-Id
            allowCredentials: true
            maxAge: 3600
```

**Lưu ý quan trọng**: Không nên dùng `allowedOrigins: "*"` khi `allowCredentials: true` vì browser sẽ reject.

## Circuit Breaker Integration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payment/**
          filters:
            - name: CircuitBreaker
              args:
                name: paymentCB
                fallbackUri: forward:/gateway/fallback
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE
                methods: GET
                backoff:
                  firstBackoff: 100ms
                  maxBackoff: 500ms
                  factor: 2
```

```java
@RestController
public class FallbackController {
    
    @GetMapping("/gateway/fallback")
    public ResponseEntity<Map<String, Object>> fallback(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return ResponseEntity.status(503).body(Map.of(
            "error", "Service temporarily unavailable",
            "path", path,
            "message", "Vui lòng thử lại sau",
            "timestamp", Instant.now()
        ));
    }
}
```

## Service Discovery Integration

```
API Gateway ──► Eureka Server ──► Danh sách instances
     │
     ▼
lb://order-service → [order-service:8081, order-service:8082, order-service:8083]
     │
     ▼ (Round Robin / Weighted)
order-service:8082 (được chọn)
```

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
```

## Programmatic Route Configuration

```java
@Configuration
public class GatewayConfig {
    
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("order-service", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .addRequestHeader("X-Gateway-Source", "api-gateway")
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.SERVICE_UNAVAILABLE))
                    .circuitBreaker(config -> config
                        .setName("orderCB")
                        .setFallbackUri("forward:/fallback")))
                .uri("lb://order-service"))
            .build();
    }
}
```

## Production Considerations

### 1. Security
- **Luôn validate JWT** tại Gateway, không phụ thuộc downstream services
- **Rate limiting theo nhiều chiều**: IP, User ID, API Key
- **Input validation**: Kiểm tra content-type, content-length, reject oversized requests
- **Security headers**: HSTS, X-Frame-Options, X-Content-Type-Options

### 2. Performance
- **Connection pooling**: Tối ưu kết nối đến upstream services
- **Timeout configuration**: connectTimeout, responseTimeout phải hợp lý
- **Caching**: Cache responses cho GET endpoints không thay đổi thường xuyên

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 2000        # 2 seconds
        response-timeout: 10s
        pool:
          max-connections: 500
          acquire-timeout: 3000
```

### 3. Observability
```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 100% trong dev, 0.1 trong prod
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

### 4. High Availability
- Deploy **ít nhất 2 instances** của API Gateway
- Đặt phía sau **Load Balancer** (nginx, AWS ALB)
- Stateless design: không lưu session tại Gateway

### 5. Timeout Hierarchy
```
Client timeout (30s)
  └── Gateway timeout (25s)
        └── Service timeout (20s)
              └── DB timeout (15s)
```

## Anti-patterns

### ❌ Business Logic trong Gateway
```java
// KHÔNG NÊN - Gateway không nên chứa business logic
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // Tính giá sản phẩm, áp dụng discount... → SAI!
    if (isVipUser) {
        applyDiscount(order);  // Business logic thuộc về Order Service
    }
    return chain.filter(exchange);
}
```

### ❌ Direct DB Access từ Gateway
Gateway không được kết nối trực tiếp database của services. Nếu cần data, gọi qua API của service đó.

### ❌ Quá nhiều responsibility
Một Gateway không nên làm quá nhiều việc. Nếu quá phức tạp, xem xét tách thành nhiều gateway theo domain (BFF - Backend for Frontend pattern).

### ❌ Missing Circuit Breaker
Nếu upstream service chậm và không có circuit breaker, Gateway sẽ giữ nhiều connection → exhaustion → cascade failure.

### ❌ Hardcode URLs
```yaml
# KHÔNG NÊN
uri: http://192.168.1.100:8081

# NÊN DÙNG Service Discovery
uri: lb://order-service
```

## Interview Questions & Answers

**Q: API Gateway khác gì với Load Balancer?**
A: Load Balancer chỉ phân phối traffic. API Gateway thực hiện thêm: authentication, rate limiting, request transformation, protocol translation, aggregation. Load Balancer hoạt động ở L4/L7, Gateway hoạt động ở L7 application level.

**Q: Tại sao Spring Cloud Gateway dùng Netty thay vì Tomcat?**
A: Gateway là I/O intensive (nhiều network calls). Netty với event loop và non-blocking I/O xử lý nhiều concurrent connections với ít threads hơn. Tomcat cần 1 thread/request → không scale tốt khi có nhiều concurrent requests.

**Q: Làm thế nào để tránh Single Point of Failure cho Gateway?**
A: Deploy nhiều instances Gateway phía sau Load Balancer. Gateway phải stateless (JWT validation không cần session). Dùng distributed cache (Redis) cho rate limiting state.

**Q: Khi nào nên dùng BFF (Backend for Frontend) thay vì một Gateway duy nhất?**
A: Khi mobile app và web app có nhu cầu khác nhau đáng kể (data shape, protocol). BFF cho phép optimize riêng cho từng client type. Thường áp dụng khi team size lớn và cần độc lập deploy.

**Q: Circuit Breaker ở Gateway level khác gì circuit breaker ở service level?**
A: Gateway CB bảo vệ gateway khỏi bị block bởi slow upstream. Service-level CB bảo vệ service khỏi dependencies của nó. Cả hai nên được implement để có defense in depth.

**Q: Làm thế nào handle JWT refresh token ở Gateway?**
A: Gateway nên validate access token nhưng không xử lý refresh. Client tự refresh token với Auth Service. Gateway chỉ cần check token validity và expiry. Một số thiết kế cho phép Gateway proxy refresh request đến Auth Service.

**Q: Rate limiting tốt nhất nên implement ở đâu - Gateway hay Service?**
A: Cả hai. Gateway protect toàn bộ system (global rate limit). Service implement riêng cho business rules (user-specific limits, endpoint-specific). Gateway dùng Redis Lua script cho distributed rate limiting chính xác.

**Q: Giải thích request tracing flow qua Gateway?**
A: Gateway generate X-Request-Id và Trace-Id nếu không có. Propagate headers này đến tất cả downstream services. Kết hợp với Zipkin/Jaeger để visualize distributed traces. Spring Cloud Sleuth tự động inject trace context.

## Cấu trúc Project

```
lesson-06-api-gateway/
├── README.md
├── docker-compose.yml
├── api-gateway/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/distributed/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   ├── config/GatewayConfig.java
│       │   └── filter/
│       │       ├── JwtAuthFilter.java
│       │       ├── RequestLoggingFilter.java
│       │       └── RateLimitFilter.java
│       └── resources/application.yml
└── upstream-service/
    ├── pom.xml
    └── src/main/
        ├── java/com/distributed/upstream/
        │   ├── UpstreamServiceApplication.java
        │   └── controller/UpstreamController.java
        └── resources/application.yml
```

## Chạy Demo

```bash
# 1. Start với Docker Compose
docker-compose up -d

# 2. Test request qua Gateway
curl -H "Authorization: Bearer <jwt_token>" \
     http://localhost:8080/api/products/123

# 3. Test rate limiting (gửi > 10 requests/second)
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products/1
done

# 4. Check actuator endpoints
curl http://localhost:8080/actuator/gateway/routes
curl http://localhost:8080/actuator/health
```

## Tóm tắt

| Tính năng | Spring Cloud Gateway | Kong | NGINX |
|-----------|---------------------|------|-------|
| Language | Java/Kotlin | Lua | C |
| Reactive | Có | Không | Không |
| Spring Integration | Native | Plugin | Hạn chế |
| Dynamic Routes | Có | Có | Reload cần thiết |
| Plugin Ecosystem | Spring | Phong phú | Modules |
| Learning Curve | Thấp (Spring devs) | Trung bình | Cao |

Spring Cloud Gateway là lựa chọn tự nhiên cho Java/Spring teams vì integration sâu với Spring ecosystem, reactive performance, và dễ customize với Java code.
