# Lesson 15: Distributed Transactions (2PC & TCC)

## Giới thiệu

Distributed Transactions đảm bảo ACID properties qua nhiều services/databases. Hai pattern phổ biến: Two-Phase Commit (2PC) và TCC (Try-Confirm-Cancel).

## Two-Phase Commit (2PC)

```
Phase 1 - PREPARE:
Coordinator ──► Participant 1: "Can you commit?"  → YES/NO
            ──► Participant 2: "Can you commit?"  → YES/NO

Phase 2 - COMMIT or ABORT:
All YES → Coordinator ──► Participant 1: "COMMIT"
                      ──► Participant 2: "COMMIT"

Any NO  → Coordinator ──► Participant 1: "ABORT"
                      ──► Participant 2: "ABORT"
```

### 2PC Flow

```
┌─────────────┐         ┌──────────────┐       ┌──────────────┐
│ Coordinator │         │ Inventory    │       │ Payment      │
│             │         │ Participant  │       │ Participant  │
└──────┬──────┘         └──────┬───────┘       └──────┬───────┘
       │                       │                       │
       │── PREPARE ───────────►│                       │
       │                       │ Lock inventory        │
       │◄─ YES ────────────────│                       │
       │                       │                       │
       │── PREPARE ────────────────────────────────────►
       │                       │               Lock funds
       │◄─ YES ────────────────────────────────────────│
       │                       │                       │
       │── COMMIT ────────────►│                       │
       │                       │ Release lock          │
       │── COMMIT ─────────────────────────────────────►
       │                       │               Release lock
```

### 2PC Problems
```
1. Blocking protocol: nếu coordinator crash → participants blocked mãi
2. Single point of failure
3. Network partition: "in-doubt" transaction
4. Low performance: 2 round trips + locking
```

## TCC (Try-Confirm-Cancel)

```
Phase 1 - TRY:
  - Reserve resources (không commit)
  - Check business rules
  - Return success/failure

Phase 2 - CONFIRM (nếu all Try success):
  - Commit reserved resources
  - Final, cannot fail (must be idempotent)

Phase 2 - CANCEL (nếu any Try fails):
  - Release reserved resources
  - Undo Try phase
```

### TCC vs 2PC

```
2PC:
├── Database-level locking
├── Long-lived transactions
├── Blocking on failure
└── Works across any RDBMS

TCC:
├── Application-level
├── Short-lived locks in Try
├── Non-blocking (Confirm/Cancel always succeed)
└── Business-aware compensation
```

## Code Example

### 2PC Coordinator
```java
public DistributedTransaction execute2PC(String productId, int quantity, double amount) {
    // Phase 1: Prepare
    boolean inventoryVote = sendPrepare(inventoryUrl + "/api/2pc/prepare", txId, ...);
    boolean paymentVote = sendPrepare(paymentUrl + "/api/2pc/prepare", txId, ...);

    // Phase 2: Decision
    if (allVotedYes) {
        sendCommit(inventoryUrl + "/api/2pc/commit", txId);
        sendCommit(paymentUrl + "/api/2pc/commit", txId);
    } else {
        sendAbort(inventoryUrl + "/api/2pc/abort", txId);
        sendAbort(paymentUrl + "/api/2pc/abort", txId);
    }
}
```

### TCC Participant (Inventory)
```java
// TRY: Reserve
@PostMapping("/api/tcc/try")
public Map<String, Object> tccTry(@RequestBody Map<String, Object> request) {
    int available = inventory.get(productId);
    if (available >= quantity) {
        // Reserve but don't commit
        tccReservations.put(txId, quantity);
        inventory.put(productId, available - quantity);
        return Map.of("success", true);
    }
    return Map.of("success", false, "reason", "Insufficient stock");
}

// CONFIRM: Finalize
@PostMapping("/api/tcc/confirm")
public Map<String, Object> tccConfirm(@RequestBody Map<String, Object> request) {
    tccReservations.remove(txId); // Reservation becomes permanent
    return Map.of("status", "CONFIRMED");
}

// CANCEL: Undo Try
@PostMapping("/api/tcc/cancel")
public Map<String, Object> tccCancel(@RequestBody Map<String, Object> request) {
    Integer quantity = tccReservations.remove(txId);
    if (quantity != null) {
        inventory.merge(productId, quantity, Integer::sum); // Return stock
    }
    return Map.of("status", "CANCELLED");
}
```

## Project Structure

```
lesson-15-distributed-transactions/
├── transaction-coordinator/
│   ├── src/main/java/com/distributed/tx/
│   │   ├── TransactionCoordinatorApplication.java
│   │   ├── model/DistributedTransaction.java
│   │   ├── service/
│   │   │   ├── TwoPhaseCommitCoordinator.java
│   │   │   └── TCCCoordinator.java
│   │   └── controller/TransactionController.java
│   └── src/main/resources/application.yml
├── inventory-participant/
│   └── src/main/java/com/distributed/tx/
│       └── controller/InventoryParticipantController.java
├── payment-participant/
│   └── src/main/java/com/distributed/tx/
│       └── controller/PaymentParticipantController.java
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# 2PC - Thành công
curl -X POST http://localhost:8080/api/transactions/2pc \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":5,"amount":100.0}'

# 2PC - Thất bại (stock không đủ)
curl -X POST http://localhost:8080/api/transactions/2pc \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":999,"amount":9999.0}'

# TCC - Thành công
curl -X POST http://localhost:8080/api/transactions/tcc \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-002","quantity":5,"amount":200.0}'

# Xem tất cả transactions
curl http://localhost:8080/api/transactions

# Kiểm tra inventory status
curl http://localhost:8081/api/inventory/status

# Kiểm tra payment status
curl http://localhost:8082/api/payment/status
```

## Alternatives to 2PC

```
1. Saga Pattern (Lesson 13)
   - Compensating transactions
   - Eventual consistency
   - Better availability

2. Outbox Pattern
   - Atomic write + event publication
   - Transactional outbox table
   - CDC (Change Data Capture)

3. Best Effort Delivery
   - Retry until success
   - Idempotent operations
   - Simpler implementation
```

## Outbox Pattern (Modern Alternative)

```java
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    
    // Atomic: save event in same transaction
    outboxRepository.save(new OutboxEvent(
        "ORDER_CREATED", order.getId(), orderJson
    ));
    // Separate process polls outbox and publishes to Kafka
}
```

## Production Tips

### 1. Timeout handling trong 2PC
```java
// Coordinator ghi WAL (Write-Ahead Log) trước khi gửi prepare
// Nếu crash, recovery process đọc WAL và tiếp tục
```

### 2. TCC Idempotency
```java
// Confirm/Cancel phải idempotent
@PostMapping("/api/tcc/confirm")
public Map<String, Object> confirm(@RequestBody Map<String, Object> request) {
    // Check if already confirmed
    if (confirmedTx.contains(txId)) {
        return Map.of("status", "ALREADY_CONFIRMED");
    }
    // ...
}
```

### 3. Hanger transactions
```java
// Scheduled job tìm transactions không complete
@Scheduled(fixedDelay = 60000)
public void cleanupHangingTransactions() {
    transactions.values().stream()
        .filter(tx -> tx.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(5)))
        .filter(tx -> !tx.isTerminal())
        .forEach(tx -> abort(tx));
}
```

## Interview Q&A

**Q: 2PC có đảm bảo atomicity không?**
A: Có, nhưng với trade-off về availability. Khi coordinator crash sau phase 1, participants bị blocked (in-doubt state). Modern alternatives: Saga + Outbox.

**Q: TCC vs Saga khác nhau thế nào?**
A: TCC synchronous, coordinator-driven, shorter locking. Saga: async, event-driven (choreography) hoặc orchestrator, longer compensation chains. TCC phức tạp hơn nhưng faster.

**Q: Khi nào cần distributed transactions?**
A: Khi cần strict consistency qua nhiều services/DBs. Ví dụ: banking transfers. Trong nhiều cases, eventual consistency (Saga) là đủ và better scalability.

**Q: Outbox pattern giải quyết vấn đề gì?**
A: Đảm bảo atomicity giữa DB write và event publish. Không dùng 2PC, thay vào đó: ghi event vào outbox table trong cùng transaction, sau đó relay sang message broker.

**Q: CAP theorem liên quan 2PC thế nào?**
A: 2PC chọn CP (Consistency + Partition tolerance). Trong network partition, 2PC blocks (không available). Saga chọn AP (Available + Partition tolerant) với eventual consistency.
