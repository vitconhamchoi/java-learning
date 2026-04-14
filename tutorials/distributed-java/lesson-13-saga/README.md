# Lesson 13: Saga Pattern

## Giới thiệu

Saga là pattern quản lý distributed transactions bằng chuỗi local transactions. Mỗi bước có compensation transaction để rollback nếu cần.

## Saga vs 2PC

```
2PC (Two-Phase Commit):
- Blocking protocol
- Coordinator lock tất cả participants
- Single point of failure
- Không scale tốt

Saga:
- Non-blocking, async
- Compensation-based rollback
- Decentralized
- Eventual consistency
```

## Hai loại Saga

### 1. Choreography (Sự kiện)
```
Order Service ──► OrderCreated event ──► Inventory Service
                                             │
                                    InventoryReserved event
                                             │
                                             ▼
                                        Payment Service
                                             │
                                    PaymentProcessed event
                                             │
                                             ▼
                                        Order Completed
```

### 2. Orchestration (Điều phối)
```
                    ┌──────────────────┐
           ┌───────►│ Saga Orchestrator │◄───────┐
           │        └────────┬─────────┘         │
           │                 │                   │
    Reserve Inventory   Process Payment    Notify Success
           │                 │                   │
           ▼                 ▼                   ▼
  ┌─────────────────┐  ┌──────────────┐  ┌──────────────┐
  │InventoryService │  │PaymentService│  │EmailService  │
  └─────────────────┘  └──────────────┘  └──────────────┘
```

## Saga States

```
STARTED → INVENTORY_RESERVED → PAYMENT_PROCESSED → COMPLETED
    │              │                   │
    ▼              ▼                   ▼
 FAILED      COMPENSATING         COMPENSATING
                 │                     │
                 ▼                     ▼
           RELEASE_INVENTORY    RELEASE_INVENTORY
                                + REFUND_PAYMENT
                                     │
                                     ▼
                                 COMPENSATED
```

## Code Example

### Orchestrator
```java
@Service
public class SagaOrchestrationService {
    
    public OrderSaga startSaga(String productId, int quantity, double amount) {
        OrderSaga saga = new OrderSaga(sagaId, orderId, ...);
        
        try {
            // Step 1: Reserve inventory
            reserveInventory(saga);
            
            // Step 2: Process payment
            processPayment(saga);
            
            saga.setState(COMPLETED);
        } catch (Exception e) {
            compensate(saga, e); // Rollback
        }
        
        return saga;
    }
    
    private void compensate(OrderSaga saga, Exception cause) {
        saga.setState(COMPENSATING);
        
        // Undo trong thứ tự ngược lại
        if (saga.getSteps().contains("INVENTORY_RESERVED")) {
            releaseInventory(saga);
        }
        
        saga.setState(FAILED);
    }
}
```

### Choreography với Kafka
```java
// Inventory Service - lắng nghe event
@KafkaListener(topics = "order-events")
public void handleOrderCreated(OrderCreatedEvent event) {
    if (canReserve(event.getProductId(), event.getQuantity())) {
        kafkaTemplate.send("inventory-events", 
            new InventoryReservedEvent(event.getOrderId()));
    } else {
        kafkaTemplate.send("inventory-events",
            new InventoryReservationFailedEvent(event.getOrderId()));
    }
}

// Payment Service - lắng nghe inventory event
@KafkaListener(topics = "inventory-events")
public void handleInventoryReserved(InventoryReservedEvent event) {
    // Process payment
    kafkaTemplate.send("payment-events", new PaymentProcessedEvent(event.getOrderId()));
}
```

## Idempotency trong Saga

```java
// Mỗi step cần idempotent - safe to retry
@PostMapping("/reserve")
public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, Object> request) {
    String sagaId = (String) request.get("sagaId");
    
    // Check if already processed (idempotency key)
    if (reservations.containsKey(sagaId)) {
        return ResponseEntity.ok(Map.of("status", "ALREADY_RESERVED"));
    }
    
    // Process reservation
    // ...
}
```

## Project Structure

```
lesson-13-saga/
├── saga-orchestrator/
│   ├── src/main/java/com/distributed/saga/
│   │   ├── SagaOrchestratorApplication.java
│   │   ├── model/OrderSaga.java
│   │   ├── service/SagaOrchestrationService.java
│   │   └── controller/SagaController.java
│   └── src/main/resources/application.yml
├── inventory-service/
│   └── src/main/java/com/distributed/saga/
│       ├── SagaInventoryApplication.java
│       └── controller/InventoryController.java
├── payment-service/
│   └── src/main/java/com/distributed/saga/
│       ├── SagaPaymentApplication.java
│       └── controller/PaymentController.java
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Happy path - saga thành công
curl -X POST http://localhost:8080/api/sagas/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":5,"amount":100.0}'

# Simulate inventory failure (PROD-003 có stock=0)
curl -X POST http://localhost:8080/api/sagas/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-003","quantity":5,"amount":100.0}'

# Simulate payment failure
curl -X POST "http://localhost:8082/api/payments/simulate/failure?enabled=true"

curl -X POST http://localhost:8080/api/sagas/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":5,"amount":100.0}'

# Check saga states
curl http://localhost:8080/api/sagas

# Inventory status
curl http://localhost:8081/api/inventory/status

# Payment status
curl http://localhost:8082/api/payments/status
```

## Choreography vs Orchestration

| Aspect | Choreography | Orchestration |
|--------|-------------|---------------|
| Coupling | Low | Medium |
| Complexity | Distributed | Centralized |
| Debugging | Hard | Easier |
| Testing | Complex | Simpler |
| Visibility | Need event tracking | Central monitor |

## Production Tips

### 1. Saga Log (persistent)
```java
// Lưu saga state vào database để survive crashes
@Entity
public class SagaState {
    @Id
    private String sagaId;
    @Enumerated(EnumType.STRING)
    private SagaStatus status;
    @Column(columnDefinition = "TEXT")
    private String steps; // JSON
    // ...
}
```

### 2. Timeout và Retry
```java
// Mỗi step có timeout
@CircuitBreaker(name = "inventoryService")
@TimeLimiter(name = "inventoryService")
private CompletableFuture<String> reserveInventory(OrderSaga saga) {
    return CompletableFuture.supplyAsync(() -> {
        return restTemplate.postForObject(...);
    });
}
```

### 3. Dead Letter Queue cho failed compensations
```
Nếu compensation cũng fail:
→ Send to DLQ (Dead Letter Queue)
→ Manual intervention required
→ Alert on-call team
```

## Interview Q&A

**Q: Saga đảm bảo gì về consistency?**
A: Eventual consistency. Trong quá trình saga running, có thể có dirty reads (intermediate states). Cần design UI và business logic để xử lý trạng thái trung gian.

**Q: Phân biệt compensation và rollback?**
A: Rollback: undo database operation (ACID). Compensation: thực hiện hành động ngược lại về mặt business (ví dụ: refund thay vì undo payment). Compensation có thể không hoàn toàn reverse state ban đầu.

**Q: Khi nào dùng choreography vs orchestration?**
A: Choreography: services ít, flows đơn giản, prefer loose coupling. Orchestration: flows phức tạp, cần central monitoring, nhiều teams, debugging quan trọng.

**Q: Làm sao handle distributed deadlock trong Saga?**
A: Saga không có locking nên không có traditional deadlock. Tuy nhiên có thể có starvation. Dùng timeouts và compensation để resolve stuck sagas.

**Q: Idempotency trong Saga quan trọng thế nào?**
A: Cực kỳ quan trọng. Messages có thể delivered nhiều lần (at-least-once). Mỗi step phải safe to retry - lưu processed saga IDs để detect duplicates.
