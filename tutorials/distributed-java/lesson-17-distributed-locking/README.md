# Lesson 17: Distributed Locking (Redisson + Redis)

## Giới thiệu

Distributed Lock đảm bảo chỉ một process trong hệ thống distributed được thực thi critical section tại một thời điểm. Redis là backend phổ biến nhất nhờ atomic operations.

## Tại sao cần Distributed Lock?

```
Single JVM: synchronized, ReentrantLock (chỉ trong 1 process)

Distributed (nhiều instances):
Instance 1 ────────────────────────────────────────────►
Instance 2 ────────────────────────────────────────────►
Instance 3 ────────────────────────────────────────────►
           │
           ▼
   Race condition: đồng thời update inventory
   Result: overselling, data corruption
   
Solution: Distributed Lock
   Only one instance executes critical section at a time
```

## Redis-based Distributed Lock

### Manual Implementation (SET NX PX)
```
SET lock:resource1 "instance-uuid" NX PX 30000
                                   ↑       ↑
                              if not exist  30 second TTL

Release:
if GET lock:resource1 == "instance-uuid":
    DEL lock:resource1
```

### Redisson (Production-grade)
```java
// Redisson handles: automatic renewal, failover, fairness
RLock lock = redisson.getLock("lock:resource1");
boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
//                             ↑      ↑
//                      wait up to 5s  hold max 30s

if (locked) {
    try {
        // critical section
    } finally {
        lock.unlock();
    }
}
```

## Lock Types

### 1. Basic Lock (Mutual Exclusion)
```java
RLock lock = redisson.getLock("my-lock");
lock.lock(30, TimeUnit.SECONDS);  // Auto-expire after 30s
try {
    // Only one thread/instance runs this
} finally {
    lock.unlock();
}
```

### 2. Read-Write Lock
```java
RReadWriteLock rwLock = redisson.getReadWriteLock("rw-lock");

// Multiple readers can hold simultaneously
RLock readLock = rwLock.readLock();
readLock.lock();  // shared

// Only one writer, exclusive
RLock writeLock = rwLock.writeLock();
writeLock.lock(); // exclusive
```

```
State       │ New Read │ New Write
────────────┼──────────┼──────────
No lock     │ Allow    │ Allow
Read held   │ Allow    │ Block
Write held  │ Block    │ Block
```

### 3. Fair Lock
```java
RLock fairLock = redisson.getFairLock("fair-lock");
fairLock.tryLock(10, 30, TimeUnit.SECONDS);
// FIFO ordering: first come, first served
// No starvation
```

### 4. MultiLock (Lock multiple resources)
```java
RLock lock1 = redisson.getLock("lock:resource1");
RLock lock2 = redisson.getLock("lock:resource2");
RLock multiLock = redisson.getMultiLock(lock1, lock2);
multiLock.lock(); // Acquire all or none (prevent partial locking)
```

## Watchdog (Automatic Renewal)

```
Default lock timeout = 30s (lockWatchdogTimeout)
Watchdog thread renews lock every 10s if thread still alive

Thread alive ──────────────────────────────────────────►
Lock timeout    30s       30s       30s
Renewal          ↑(10s)    ↑(20s)    ↑(30s)  keeps alive

Thread dies ────────►
Lock expires              30s later lock auto-released
```

## Project Structure

```
lesson-17-distributed-locking/
├── locking-demo/
│   ├── src/main/java/com/distributed/locking/
│   │   ├── LockingDemoApplication.java
│   │   ├── service/DistributedLockService.java
│   │   └── controller/LockingController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Increment với lock (tránh race condition)
curl -X POST http://localhost:8080/api/locks/increment/inventory-1

# Read lock (concurrent OK)
curl -X POST http://localhost:8080/api/locks/read/product-1 &
curl -X POST http://localhost:8080/api/locks/read/product-1 &
wait

# Write lock (exclusive)
curl -X POST http://localhost:8080/api/locks/write/product-1

# Fair lock (FIFO)
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/locks/fair/resource-1 &
done
wait

# Stats
curl http://localhost:8080/api/locks/stats

# Simulate race condition without lock (10 concurrent)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/locks/increment/no-lock-resource &
done
wait
```

## RedLock Algorithm (Multi-Master Redis)

```
Quorum locking across N Redis masters (N >= 3, usually 5)

1. Get current time T1
2. Try to acquire lock on all N masters
3. Lock acquired if:
   - Majority (N/2 + 1) acquired
   - Total time < lock TTL
4. Actual TTL = requested_TTL - (T2 - T1)

Problems: Clock drift, network delays
Controversial: Martin Kleppmann critique
Production: Use single Redis (sentinel/cluster) instead
```

## Fencing Token Pattern

```
Lock acquired with token T=1
Client 1: acquires lock T=1
Client 1: PAUSE (GC, network)
Lock expires!
Client 2: acquires lock T=2
Client 2: writes with token T=2
Client 1: resumes, tries write with T=1
Server: rejects T=1 < T=2 (monotonic counter)
```

```java
// Resource server validates token
public void updateResource(String resourceId, Object data, long fencingToken) {
    long currentToken = resourceTokens.getOrDefault(resourceId, 0L);
    if (fencingToken <= currentToken) {
        throw new StaleTokenException("Token expired");
    }
    resourceTokens.put(resourceId, fencingToken);
    // proceed with update
}
```

## Production Tips

### 1. Always use try-finally
```java
RLock lock = redisson.getLock("lock");
boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
try {
    if (!locked) throw new LockAcquisitionException("Could not acquire lock");
    // critical section
} finally {
    if (locked && lock.isHeldByCurrentThread()) {
        lock.unlock(); // ALWAYS in finally
    }
}
```

### 2. Lock granularity
```java
// Coarse-grained (bad): all orders blocked
RLock lock = redisson.getLock("order-service-lock");

// Fine-grained (good): only same product blocked
RLock lock = redisson.getLock("lock:product:" + productId);
```

### 3. Deadlock prevention
```java
// Acquire locks in consistent order
List<String> resources = Arrays.asList(r1, r2, r3);
Collections.sort(resources); // Always same order
for (String r : resources) {
    locks.add(redisson.getLock(r));
}
```

## Interview Q&A

**Q: Distributed lock vs synchronized?**
A: `synchronized` chỉ work trong single JVM. Distributed lock cần external coordinator (Redis, ZooKeeper) để coordinate qua multiple processes/servers.

**Q: Redisson vs manual SET NX?**
A: Manual SET NX: simple nhưng cần tự handle renewal, race in release. Redisson: watchdog auto-renewal, atomic compare-and-delete, fair lock, reentrant, much safer.

**Q: Lock expire quá sớm gây vấn đề gì?**
A: Nếu lock expire trong khi still holding, another process acquires it → two processes in critical section simultaneously. Giải pháp: watchdog renewal, generous TTL, fencing tokens.

**Q: ZooKeeper vs Redis cho distributed lock?**
A: ZooKeeper: CP, strong consistency, ephemeral nodes tự cleanup khi client disconnect. Redis: AP, faster, simpler. ZooKeeper tốt hơn cho critical locking (distributed coordination). Redis tốt cho performance-sensitive.

**Q: Khi nào KHÔNG dùng distributed lock?**
A: Khi có thể dùng optimistic locking (version-based), database-level locking, hoặc design data model để avoid conflicts (append-only, idempotent operations).
