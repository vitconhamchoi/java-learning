# Lesson 10: Circuit Breaker Pattern (Resilience4j)

## Giới thiệu

Circuit Breaker là pattern bảo vệ hệ thống khỏi cascade failures khi một service bị lỗi. Giống như cầu dao điện - khi quá tải, nó ngắt mạch để bảo vệ hệ thống.

## Các trạng thái Circuit Breaker

```
┌─────────────────────────────────────────────────────────┐
│                    Circuit Breaker States                │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   ┌─────────┐    failure threshold    ┌──────────┐      │
│   │  CLOSED  │ ─────────────────────► │   OPEN   │      │
│   │(requests)│                        │(blocked) │      │
│   └─────────┘ ◄─────────────────────  └──────────┘      │
│        ▲           reset after              │           │
│        │           wait duration            │           │
│        │                                    ▼           │
│        │           success            ┌───────────┐     │
│        └────────────────────────────  │ HALF_OPEN │     │
│                                       │(test req) │     │
│                                       └───────────┘     │
└─────────────────────────────────────────────────────────┘
```

### CLOSED (Đóng)
- Requests chạy bình thường
- Circuit Breaker theo dõi failure rate
- Khi failure rate > threshold → chuyển sang OPEN

### OPEN (Mở)
- Tất cả requests bị từ chối ngay lập tức (fail fast)
- Trả về fallback response
- Sau `waitDurationInOpenState` → chuyển sang HALF_OPEN

### HALF_OPEN (Nửa mở)
- Cho phép một số requests thử nghiệm
- Nếu thành công → về CLOSED
- Nếu thất bại → về OPEN

## Sliding Window Types

### Count-Based Sliding Window
```
Window size = 10 calls
[✓, ✗, ✓, ✗, ✗, ✗, ✓, ✗, ✗, ✗]
Failure rate = 7/10 = 70% > 50% threshold → OPEN
```

### Time-Based Sliding Window
```
Window = last 60 seconds
Count failures in rolling time window
```

## Code Example

### Circuit Breaker Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50        # 50% failure → OPEN
        waitDurationInOpenState: 10s    # Wait 10s before HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3
        slowCallRateThreshold: 80       # 80% slow calls → OPEN
        slowCallDurationThreshold: 2s   # > 2s = slow call
```

### Service với Circuit Breaker
```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
@Retry(name = "inventoryService")
public OrderResponse createOrder(OrderRequest request) {
    var inventory = inventoryClient.checkInventory(request.getProductId(), request.getQuantity());
    if (inventory.sufficient()) {
        return new OrderResponse(UUID.randomUUID().toString(), "CREATED", "OK");
    }
    return new OrderResponse(null, "REJECTED", "Insufficient inventory");
}

public OrderResponse inventoryFallback(OrderRequest request, Exception ex) {
    log.warn("Circuit breaker triggered: {}", ex.getMessage());
    return new OrderResponse(null, "FALLBACK", "Order queued for later processing");
}
```

### Feign Client
```java
@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryClient {
    @GetMapping("/api/inventory/check")
    InventoryResponse checkInventory(@RequestParam String productId, @RequestParam int quantity);
}
```

## Retry Pattern kết hợp Circuit Breaker

```yaml
resilience4j:
  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - java.io.IOException
```

```
Request → Retry 1 → Retry 2 → Retry 3 → Circuit Breaker → Fallback
```

## Project Structure

```
lesson-10-circuit-breaker/
├── order-service/
│   ├── src/main/java/com/distributed/cb/
│   │   ├── OrderServiceApplication.java
│   │   ├── config/InventoryClient.java
│   │   ├── controller/OrderController.java
│   │   ├── model/{OrderRequest, OrderResponse}.java
│   │   └── service/OrderService.java
│   └── src/main/resources/application.yml
├── inventory-service/
│   ├── src/main/java/com/distributed/cb/
│   │   ├── InventoryServiceApplication.java
│   │   └── controller/InventoryController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
# Start all services
docker-compose up -d

# Test normal request
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "quantity": 5}'

# Enable failure simulation
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=true"

# Watch circuit breaker status
watch -n 1 curl -s http://localhost:8080/api/orders/circuit-breaker/status

# Disable failure
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=false"

# Check via actuator
curl http://localhost:8080/actuator/health
```

## Demo Circuit Breaker States

```bash
# 1. Normal state (CLOSED)
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"productId":"PROD-001","quantity":5}'
done

# 2. Enable failures
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=true"

# 3. Send requests until OPEN
for i in {1..10}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"productId":"PROD-001","quantity":5}'
  echo ""
done

# 4. Check state (should be OPEN)
curl http://localhost:8080/api/orders/circuit-breaker/status

# 5. Disable failures and wait for HALF_OPEN
curl -X POST "http://localhost:8081/api/inventory/simulate/failure?enabled=false"
sleep 10

# 6. Check recovery
curl http://localhost:8080/api/orders/circuit-breaker/status
```

## Production Tips

### 1. Tuning Circuit Breaker
```yaml
# Aggressive settings (fast fail)
failureRateThreshold: 30      # Open at 30% failure
waitDurationInOpenState: 30s  # Long recovery window

# Conservative settings (tolerate more failures)
failureRateThreshold: 70      # Open at 70% failure
minimumNumberOfCalls: 20      # Need more samples
```

### 2. Bulkhead Pattern kết hợp
```java
@Bulkhead(name = "inventoryService", type = Bulkhead.Type.THREADPOOL)
@CircuitBreaker(name = "inventoryService", fallbackMethod = "fallback")
public CompletableFuture<OrderResponse> createOrderAsync(OrderRequest request) {
    // Isolated thread pool prevents resource exhaustion
}
```

### 3. Metrics và Monitoring
```
Prometheus metrics từ Resilience4j:
- resilience4j_circuitbreaker_state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- resilience4j_circuitbreaker_failure_rate
- resilience4j_circuitbreaker_calls_total
- resilience4j_circuitbreaker_buffered_calls
```

### 4. Multi-Circuit Breaker
```java
// Mỗi downstream service một circuit breaker riêng
@CircuitBreaker(name = "paymentService")
public PaymentResult processPayment(Order order) { ... }

@CircuitBreaker(name = "inventoryService")
public boolean checkInventory(String productId) { ... }
```

## Interview Q&A

**Q: Tại sao cần Circuit Breaker?**
A: Ngăn chặn cascade failures. Khi service A gọi service B bị lỗi, nếu không có CB, service A sẽ giữ connection và timeout → thread pool exhaustion → service A cũng sập.

**Q: Phân biệt Circuit Breaker vs Retry?**
A: Retry thích hợp cho transient errors (network glitch). Circuit Breaker cho sustained failures - sau nhiều retries vẫn lỗi, CB ngắt mạch ngay, không lãng phí resources.

**Q: Fallback nên làm gì?**
A: (1) Return cached data, (2) Return default values, (3) Queue request for later, (4) Degrade gracefully với partial data. Không nên fallback gọi service khác cũng có thể lỗi.

**Q: Sliding window size bao nhiêu là hợp lý?**
A: Count-based: 10-20 calls. Time-based: 30-60 seconds. Nhỏ quá = đánh giá không chính xác. Lớn quá = chậm react với failures.

**Q: Làm thế nào test Circuit Breaker?**
A: Dùng chaos engineering tools (Chaos Monkey, Toxiproxy), mock downstream failures trong unit tests, hoặc endpoint để simulate failure như trong lesson này.
