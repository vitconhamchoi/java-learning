# Lesson 14: Event Sourcing

## Giới thiệu

Event Sourcing lưu trữ tất cả thay đổi của một entity dưới dạng sequence of events, thay vì lưu current state. State hiện tại được tính toán bằng cách replay tất cả events.

## Traditional vs Event Sourcing

```
Traditional (Current State):
┌────────────────┐
│ accounts table │
├────────────────┤
│ id   │ balance │
│ ACC1 │ $500    │  ← Chỉ biết current state, không biết lịch sử
└────────────────┘

Event Sourcing:
┌─────────────────────────────────────────────────────────┐
│                     event_store                          │
├─────────────────────────────────────────────────────────┤
│ v1 │ ACCOUNT_CREATED   │ balance=+1000 │ owner=Alice    │
│ v2 │ MONEY_DEPOSITED   │ amount=+500   │ desc=Salary    │
│ v3 │ MONEY_WITHDRAWN   │ amount=-200   │ desc=Rent      │
│ v4 │ MONEY_DEPOSITED   │ amount=+100   │ desc=Bonus     │
├─────────────────────────────────────────────────────────┤
│ Current state = replay v1+v2+v3+v4 → balance=$1400      │
└─────────────────────────────────────────────────────────┘
```

## Benefits

```
1. Complete Audit Trail    - Biết ai làm gì lúc nào
2. Time Travel             - Query state tại bất kỳ thời điểm nào
3. Event Replay            - Rebuild projections
4. Debugging               - Full history của system
5. CQRS Integration        - Events drive read models
```

## Event Store Design

```
Event:
├── eventId      (UUID)
├── aggregateId  (entity ID - e.g., accountId)
├── eventType    (ACCOUNT_CREATED, MONEY_DEPOSITED, ...)
├── payload      (event data)
├── version      (optimistic locking)
└── occurredAt   (timestamp)
```

```java
@Component
public class EventStore {
    // Global log: all events in order
    private final List<AccountEvent> globalEventLog = new CopyOnWriteArrayList<>();
    // Per-aggregate: events grouped by entity ID
    private final Map<String, List<AccountEvent>> eventsByAggregate;
    
    public void append(AccountEvent event) {
        eventsByAggregate.computeIfAbsent(event.getAccountId(), k -> new CopyOnWriteArrayList<>())
            .add(event);
        globalEventLog.add(event);
    }
    
    public List<AccountEvent> getEvents(String aggregateId) {
        return eventsByAggregate.getOrDefault(aggregateId, List.of());
    }
}
```

## Aggregate với Event Sourcing

```java
public class Account {
    private String accountId;
    private double balance;
    private AccountStatus status;
    
    // Reconstruct from events (event replay)
    public static Account replay(List<AccountEvent> events) {
        Account account = new Account();
        for (AccountEvent event : events) {
            account.apply(event);  // Apply each event in order
        }
        return account;
    }
    
    private void apply(AccountEvent event) {
        switch (event.getEventType()) {
            case ACCOUNT_CREATED  -> { this.accountId = event.getAccountId(); this.balance = event.getAmount(); }
            case MONEY_DEPOSITED  -> this.balance += event.getAmount();
            case MONEY_WITHDRAWN  -> this.balance -= event.getAmount();
            case ACCOUNT_CLOSED   -> this.status = AccountStatus.CLOSED;
        }
    }
}
```

## Time Travel Query

```java
// State tại version 3 (sau 3 events đầu)
public Account getAccountAtVersion(String accountId, int version) {
    List<AccountEvent> events = eventStore.getEvents(accountId).stream()
        .filter(e -> e.getVersion() <= version)
        .toList();
    return Account.replay(events);
}

// State lúc 3 giờ chiều hôm qua
public Account getAccountAtTime(String accountId, LocalDateTime pointInTime) {
    List<AccountEvent> events = eventStore.getEvents(accountId).stream()
        .filter(e -> e.getOccurredAt().isBefore(pointInTime))
        .toList();
    return Account.replay(events);
}
```

## Project Structure

```
lesson-14-event-sourcing/
├── account-service/
│   ├── src/main/java/com/distributed/eventsourcing/
│   │   ├── AccountServiceApplication.java
│   │   ├── model/
│   │   │   ├── Account.java         # Aggregate
│   │   │   └── AccountEvent.java    # Domain event
│   │   ├── store/
│   │   │   └── EventStore.java      # Event storage
│   │   ├── service/
│   │   │   └── AccountService.java  # Business logic
│   │   └── controller/
│   │       └── AccountController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Tạo tài khoản
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"owner":"Alice","initialBalance":1000}'

# Lưu account ID
ACCOUNT_ID="ACC-XXXXXXXX"

# Nạp tiền
curl -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount":500,"description":"Salary"}'

# Rút tiền
curl -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount":200,"description":"Rent"}'

# Xem state hiện tại
curl http://localhost:8080/api/accounts/$ACCOUNT_ID

# Time travel: state tại version 2
curl http://localhost:8080/api/accounts/$ACCOUNT_ID/version/2

# Xem event history
curl http://localhost:8080/api/accounts/$ACCOUNT_ID/events

# Xem tất cả events
curl http://localhost:8080/api/accounts/events/all
```

## Snapshots (Performance Optimization)

```java
// Sau N events, tạo snapshot để tránh replay toàn bộ history
public Account getAccount(String accountId) {
    // 1. Load latest snapshot
    AccountSnapshot snapshot = snapshotStore.getLatestSnapshot(accountId);
    
    // 2. Load events sau snapshot
    List<AccountEvent> events = eventStore.getEventsFromVersion(
        accountId, 
        snapshot != null ? snapshot.getVersion() + 1 : 0
    );
    
    // 3. Apply events on top of snapshot
    Account account = snapshot != null ? Account.fromSnapshot(snapshot) : new Account();
    return account.applyAll(events);
}

// Tạo snapshot sau mỗi 100 events
@Scheduled(fixedDelay = 60000)
public void createSnapshots() {
    accounts.forEach((id, account) -> {
        if (eventStore.getEventCount(id) % 100 == 0) {
            snapshotStore.save(AccountSnapshot.from(account));
        }
    });
}
```

## Production Event Store Options

```
1. EventStoreDB (Axon Server)
   - Purpose-built for Event Sourcing
   - Streams, subscriptions, projections

2. Apache Kafka (as Event Log)
   - Durable, distributed
   - Compaction cho snapshots
   - Kafka Streams cho projections

3. PostgreSQL (with event sourcing tables)
   - Familiar technology
   - ACID guarantees
   - Logical replication for read models
```

## Interview Q&A

**Q: Nhược điểm của Event Sourcing?**
A: (1) Complexity cao. (2) Eventual consistency. (3) Schema evolution khó (events cũ phải compatible). (4) Performance kém khi aggregate có nhiều events (giải quyết bằng snapshots).

**Q: Event Sourcing và audit log khác nhau?**
A: Audit log là by-product của application. Event Sourcing là source of truth - state được DERIVED từ events. Trong ES, bạn không thể delete/update events (immutable).

**Q: Làm sao evolve event schema?**
A: (1) Upcasting: transform old event format khi replay. (2) Versioning: EventV1, EventV2. (3) Additive changes: chỉ thêm fields, không xóa.

**Q: CQRS và Event Sourcing có phải luôn đi cùng?**
A: Không. ES thường kết hợp với CQRS vì events tự nhiên drive projections (read models). Nhưng có thể dùng ES mà không có CQRS.

**Q: Làm sao handle large aggregates với nhiều events?**
A: Snapshots: sau N events, lưu current state. Khi load, chỉ replay từ snapshot. Thường snapshot sau 100-1000 events tùy use case.
