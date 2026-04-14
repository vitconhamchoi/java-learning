# Lesson 02: REST Communication Between Microservices

## Mục lục
1. [HTTP/REST Communication Overview](#1-httprest-communication-overview)
2. [RestTemplate vs WebClient](#2-resttemplate-vs-webclient)
3. [Timeout Configuration](#3-timeout-configuration)
4. [Retry với Exponential Backoff](#4-retry-với-exponential-backoff)
5. [OpenFeign Declarative Client](#5-openfeign-declarative-client)
6. [Request/Response Interceptors](#6-requestresponse-interceptors)
7. [Error Handling and Propagation](#7-error-handling-and-propagation)
8. [Production Considerations](#8-production-considerations)
9. [Anti-patterns](#9-anti-patterns)
10. [Câu hỏi phỏng vấn](#10-câu-hỏi-phỏng-vấn)

---

## 1. HTTP/REST Communication Overview

### 1.1 Tại sao dùng REST cho Microservices?

REST (Representational State Transfer) là lựa chọn phổ biến nhất cho microservices communication vì:

```
Ưu điểm:
✓ Human-readable (JSON/XML)
✓ Stateless - dễ scale
✓ Caching support (HTTP cache headers)
✓ Widely supported (mọi ngôn ngữ)
✓ Debugging dễ (curl, Postman)
✓ Firewall friendly (HTTP port 80/443)

Nhược điểm:
✗ Text-based → overhead so với binary (gRPC)
✗ Không có schema enforcement (cần OpenAPI)
✗ Request-response pattern (không native streaming)
✗ Verbose headers overhead
```

### 1.2 Microservices Communication Patterns

```
Synchronous (Request-Response):
┌──────────┐  HTTP/REST  ┌──────────┐
│  Order   │────────────►│Inventory │
│  Service │◄────────────│ Service  │
└──────────┘   Response  └──────────┘
Pros: Simple, immediate response
Cons: Tight coupling, cascading failures

Asynchronous (Event-driven):
┌──────────┐   Event    ┌────────┐   Event   ┌──────────┐
│  Order   │───────────►│ Kafka  │───────────►│Inventory │
│  Service │            │ Topic  │            │ Service  │
└──────────┘            └────────┘            └──────────┘
Pros: Loose coupling, resilient
Cons: Eventual consistency, complexity
```

---

## 2. RestTemplate vs WebClient

### 2.1 RestTemplate (Blocking/Synchronous)

```java
// RestTemplate - blocking, mỗi request chiếm 1 thread
RestTemplate restTemplate = new RestTemplate();
String result = restTemplate.getForObject(
    "http://inventory-service/api/stock/{id}",
    String.class,
    productId
);
// Thread bị block cho đến khi có response!
```

**Thread model của RestTemplate:**
```
Thread 1: Request → [BLOCKED waiting for response] → Process response
Thread 2: Request → [BLOCKED waiting for response] → Process response
Thread 3: Request → [BLOCKED waiting for response] → Process response
...
Thread N: Request → [BLOCKED waiting for response] → Process response

Vấn đề: Với 1000 concurrent requests → cần 1000 threads!
Mỗi thread tốn ~1MB stack → 1GB RAM chỉ cho threads
```

### 2.2 WebClient (Non-blocking/Reactive)

```java
// WebClient - non-blocking, reactive
WebClient webClient = WebClient.create("http://inventory-service");
Mono<String> result = webClient.get()
    .uri("/api/stock/{id}", productId)
    .retrieve()
    .bodyToMono(String.class);
// Không block thread! Callback khi có response
```

**Thread model của WebClient:**
```
Event Loop Thread 1: Handle req1 → send → [FREE to handle other requests] → process resp1
Event Loop Thread 1: Handle req2 → send → [FREE to handle other requests] → process resp2

Với 1000 concurrent requests → vẫn chỉ cần ~8 event loop threads!
Throughput cao hơn, latency thấp hơn, ít memory hơn
```

### 2.3 So sánh

| Feature | RestTemplate | WebClient |
|---------|-------------|-----------|
| Model | Blocking/Sync | Non-blocking/Async |
| Thread usage | 1 thread/request | Few event loop threads |
| Throughput | Lower | Higher |
| Complexity | Simple | Moderate (reactive) |
| Spring version | Since 3.x | Since 5.x (Spring Boot 2.x+) |
| Status | @Deprecated (5.x) | Recommended |
| Return type | Direct object | Mono<T> / Flux<T> |
| Streaming | Not native | Native support |

---

## 3. Timeout Configuration

### 3.1 Tại sao Timeout quan trọng?

```
Không có timeout:
Client ──request──► Service B [DOWN/SLOW]
                         │
                    (mãi mãi...)
Client: Threads accumulate, eventually OOM!

Với timeout:
Client ──request──► Service B [SLOW]
           │
      [5s timeout]
           │
      TimeoutException → Fallback → Fast fail
Client: Free threads for other requests
```

### 3.2 Các loại Timeout

```
Connection Timeout (10s):
Client ──TCP SYN──► Server
         ↑
    Bao lâu để establish TCP connection?
    Nếu server không respond SYN-ACK trong thời gian này → error

Read Timeout (30s):
Client ──established──► Server [processing...]
                               ↑
                         Bao lâu để nhận data?
                         Sau khi connected, bao lâu để nhận response body?

Response Timeout (WebClient):
Total time from request sent to full response received

Request Timeout (Circuit Breaker):
Timeout cho toàn bộ retry chain
```

### 3.3 Timeout trong WebClient

```java
// Timeout ở connection level (Netty)
HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
    .responseTimeout(Duration.ofSeconds(30))
    .doOnConnected(conn -> conn
        .addHandlerLast(new ReadTimeoutHandler(30))
        .addHandlerLast(new WriteTimeoutHandler(10)));

// Timeout ở request level (override per-request)
webClient.get()
    .uri("/slow-endpoint")
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(5)); // Timeout cụ thể cho request này
```

---

## 4. Retry với Exponential Backoff

### 4.1 Tại sao cần Retry?

```
Transient failures (tạm thời, nên retry):
- Network hiccup: 5xx errors
- Service restart: Connection refused
- Temporary overload: 503 Service Unavailable
- GC pause causing slow responses

Permanent failures (không nên retry):
- Business logic errors: 400 Bad Request, 404 Not Found
- Authentication: 401, 403
- Validation: 422 Unprocessable Entity
```

### 4.2 Exponential Backoff + Jitter

```
Fixed retry (BAD - thundering herd):
Time: 0s  1s  2s  3s  4s  5s  6s  7s  8s
Req1: ─── retry retry retry
Req2: ─── retry retry retry    [1000 clients all retry at same time!]
Req3: ─── retry retry retry

Exponential Backoff (Better):
Attempt 1: wait 1s
Attempt 2: wait 2s
Attempt 3: wait 4s
Attempt 4: wait 8s → max 30s

Exponential Backoff + Jitter (BEST):
Attempt 1: wait 1s ± random(0, 1s) → e.g. 0.7s, 1.3s, 0.9s...
Attempt 2: wait 2s ± random(0, 2s) → e.g. 1.5s, 2.8s, 1.1s...
[Randomization spreads out retry storms]
```

### 4.3 Retry Strategy

```java
// Chỉ retry với transient errors
@Retryable(
    value = {ResourceAccessException.class, HttpServerErrorException.class},
    exclude = {HttpClientErrorException.class}, // Không retry 4xx
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2, random = true)
)
public Product getProduct(String id) {
    return restTemplate.getForObject("/products/" + id, Product.class);
}
```

---

## 5. OpenFeign Declarative Client

### 5.1 Feign vs Manual HTTP Client

```java
// Manual WebClient - verbose
public Mono<Product> getProduct(String id) {
    return webClient
        .get()
        .uri("/products/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError,
            resp -> Mono.error(new ProductNotFoundException(id)))
        .bodyToMono(Product.class)
        .timeout(Duration.ofSeconds(5))
        .retry(3);
}

// Feign - declarative (như gọi method local!)
@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @GetMapping("/products/{id}")
    Product getProduct(@PathVariable String id);
}
```

### 5.2 Feign Configuration

```java
@Configuration
public class FeignConfig {

    // Custom error decoder
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new ResourceNotFoundException("Not found");
            }
            return new RetryableException(response.status(),
                "Service unavailable", Request.HttpMethod.GET, null, null);
        };
    }

    // Request interceptor for auth
    @Bean
    public RequestInterceptor authInterceptor() {
        return template -> template.header("Authorization", "Bearer " + getToken());
    }

    // Feign logger
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // Log headers, body
    }
}
```

---

## 6. Request/Response Interceptors

### 6.1 WebClient ExchangeFilterFunction

```
Request Flow:
Client → [LoggingFilter] → [AuthFilter] → [MetricsFilter] → HTTP Request → Server
Client ← [LoggingFilter] ← [AuthFilter] ← [MetricsFilter] ← HTTP Response ← Server
```

### 6.2 Use cases cho Interceptors

| Interceptor | Mục đích |
|-------------|---------|
| Logging | Log request/response để debug |
| Authentication | Inject Authorization header |
| Correlation ID | Propagate trace ID across services |
| Metrics | Record latency, success rate |
| Rate Limiting | Client-side rate limit |
| Circuit Breaker | Fail-fast when service down |

---

## 7. Error Handling and Propagation

### 7.1 HTTP Status Codes trong Microservices

```
2xx Success:
  200 OK             - Thành công
  201 Created        - Resource đã được tạo
  204 No Content     - Thành công, không có response body

4xx Client Errors (Không nên retry!):
  400 Bad Request    - Dữ liệu request sai
  401 Unauthorized   - Chưa xác thực
  403 Forbidden      - Không có quyền
  404 Not Found      - Resource không tồn tại
  409 Conflict       - Conflict (optimistic locking)
  422 Unprocessable  - Business validation failed
  429 Too Many Req   - Rate limit exceeded

5xx Server Errors (Có thể retry):
  500 Internal Error - Server lỗi
  502 Bad Gateway    - Downstream service lỗi
  503 Unavailable    - Service tạm thời unavailable
  504 Gateway Timeout- Downstream timeout
```

### 7.2 Problem Details (RFC 7807)

```json
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "https://api.example.com/problems/insufficient-stock",
  "title": "Insufficient Stock",
  "status": 422,
  "detail": "Product 'ABC-123' only has 5 units, but 10 were requested",
  "instance": "/orders/request-id-456",
  "productId": "ABC-123",
  "requested": 10,
  "available": 5
}
```

---

## 8. Production Considerations

### 8.1 Circuit Breaker Pattern

```
CLOSED → OPEN → HALF-OPEN → CLOSED
  │         │         │
  │    [Trip threshold met]
  │         │
  │    [Timeout expired]
  │                   │
  │         [Test requests pass]
  └──────────────────┘

States:
CLOSED:    Normal operation, requests flow through
OPEN:      Circuit tripped, fail-fast (no calls to downstream)
HALF-OPEN: Allow test requests to see if service recovered
```

```java
// Resilience4j Circuit Breaker
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)          // Trip khi 50% requests fail
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .permittedNumberOfCallsInHalfOpenState(3)
    .slidingWindowSize(10)
    .build();
```

### 8.2 Bulkhead Pattern

```
Không có Bulkhead:
┌─────────────────────────────────────┐
│         Thread Pool (100)           │
│  [Inventory calls] [Payment calls]  │
│  inventory down → all 100 threads   │
│  occupied → Payment also fails!     │
└─────────────────────────────────────┘

Với Bulkhead:
┌──────────────────┐  ┌──────────────────┐
│  Inventory Pool  │  │  Payment Pool    │
│    (30 threads)  │  │   (30 threads)   │
│  Inventory down  │  │  Payment still   │
│  → only affects  │  │  works fine!     │
│  its pool        │  │                  │
└──────────────────┘  └──────────────────┘
```

### 8.3 Health Checks và Readiness

```yaml
# Kubernetes liveness and readiness probes
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

### 8.4 Distributed Tracing

```
Request ID: abc-123
  Order Service    → "Processing order" [trace: abc-123, span: 1]
    ├── Inventory  → "Checking stock"   [trace: abc-123, span: 2]
    └── Payment    → "Processing pay"   [trace: abc-123, span: 3]

Headers được propagate:
X-Trace-Id: abc-123
X-Span-Id: 2
X-Parent-Span-Id: 1
```

---

## 9. Anti-patterns

### ❌ Anti-pattern 1: Synchronous Chain of Calls

```
Order → Inventory → Warehouse → Supplier
         (500ms)    (800ms)    (1200ms)
Total latency: 2500ms minimum!
Nếu Supplier down → toàn chain fail
```
**Giải pháp**: Async messaging (Kafka), Saga pattern, hoặc reduce chain depth.

### ❌ Anti-pattern 2: No Timeout

```java
// SAI: Không có timeout
RestTemplate rt = new RestTemplate();
Product p = rt.getForObject(url, Product.class); // Block forever!
```
**Giải pháp**: Luôn set connect và read timeout.

### ❌ Anti-pattern 3: Retry on Business Errors

```java
// SAI: Retry tất cả errors
@Retryable(value = Exception.class) // Retry cả 404, 400!
public Product getProduct(String id) { ... }
```
**Giải pháp**: Chỉ retry 5xx và network errors, không retry 4xx.

### ❌ Anti-pattern 4: Chatty Services

```
Order Service: GET /inventory/{id}  [1 request per product]
With 100 products in order → 100 HTTP calls!
```
**Giải pháp**: Batch API (`POST /inventory/bulk-check`) hoặc GraphQL.

### ❌ Anti-pattern 5: Ignoring Idempotency

```
Client: POST /orders (network timeout after 29.9s)
Server: Order created! (response lost)
Client: Retry → POST /orders
Server: Second order created! (DUPLICATE!)
```
**Giải pháp**: Idempotency keys, check-before-insert, database unique constraints.

### ❌ Anti-pattern 6: Exposing Internal DTOs

```java
// SAI: Expose database entity directly to other services
// If DB schema changes → all clients break!
@GetMapping("/product/{id}")
public ProductEntity getProduct(@PathVariable String id) {
    return productRepository.findById(id); // Database entity!
}
```
**Giải pháp**: API DTOs với clear versioning (`/api/v1/products`).

---

## 10. Câu hỏi phỏng vấn

### Q1: Khi nào dùng RestTemplate và khi nào dùng WebClient?
**Trả lời:**
> RestTemplate phù hợp với traditional blocking code, đơn giản, nhưng deprecated từ Spring 5. WebClient phù hợp với reactive/non-blocking, high concurrency, streaming. Trong Spring Boot 3.x nên luôn dùng WebClient. Nếu code cần blocking behavior vẫn có thể dùng `webClient.get()...block()`.

### Q2: Exponential Backoff + Jitter là gì? Tại sao cần Jitter?
**Trả lời:**
> Exponential backoff tăng thời gian chờ theo cấp số nhân (1s, 2s, 4s, 8s...) để tránh quá tải. Jitter thêm randomization để tránh thundering herd problem - khi 1000 clients cùng retry vào đúng giây thứ 4 sẽ tạo spike load lớn. Jitter phân tán retry của chúng qua nhiều thời điểm khác nhau.

### Q3: Circuit Breaker Pattern giải quyết vấn đề gì?
**Trả lời:**
> Khi downstream service down, không có circuit breaker, requests sẽ accumulate và timeout, tiêu tốn threads và memory. Circuit Breaker fail-fast ngay lập tức (không đợi timeout) khi đã phát hiện service down, giải phóng resources và cho service thời gian recover.

### Q4: Làm sao implement Exactly-once delivery với REST?
**Trả lời:**
> Dùng Idempotency Key pattern: Client generate UUID, gửi trong header `Idempotency-Key`. Server lưu (key → response) trong Redis với TTL. Nếu nhận cùng key lần 2, trả về cached response. Cần kết hợp với database transactions để đảm bảo tính atomic.

### Q5: Timeout nên set bao nhiêu?
**Trả lời:**
> Dựa trên SLA: nếu SLA là 200ms, upstream timeout phải nhỏ hơn (ví dụ 150ms) để còn thời gian cho processing overhead. Đo P99 latency của downstream service, set timeout = P99 * 1.5 + buffer. Connect timeout thường 5-10s, read timeout theo use case (real-time: 5s, batch: 60s).

---

## Chạy ví dụ

```bash
# Terminal 1: Start inventory-service
cd inventory-service
mvn spring-boot:run

# Terminal 2: Start order-service
cd order-service
mvn spring-boot:run

# Test endpoints
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"P001","quantity":5,"customerId":"C001"}'

curl -X POST http://localhost:8080/orders/reactive \
  -H "Content-Type: application/json" \
  -d '{"productId":"P001","quantity":3,"customerId":"C002"}'

# Hoặc dùng docker-compose
docker-compose up --build
```
