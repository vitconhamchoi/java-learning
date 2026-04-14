# Lesson 16: Database Sharding

## Giới thiệu

Database Sharding là kỹ thuật phân chia dữ liệu ngang (horizontal partitioning) ra nhiều database instances để tăng khả năng scale và performance.

## Sharding vs Replication

```
Replication (Vertical scale reads):
┌──────────┐  copy  ┌──────────┐  copy  ┌──────────┐
│ Primary  │───────►│ Replica 1│        │ Replica 2│
│ All data │        │ All data │        │ All data │
└──────────┘        └──────────┘        └──────────┘
Read → any replica, Write → primary only

Sharding (Horizontal scale):
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Shard 0 │  │  Shard 1 │  │  Shard 2 │
│ User 0-33│  │ User 34-66│  │ User 67-99│
└──────────┘  └──────────┘  └──────────┘
Each shard has DIFFERENT data
```

## Sharding Strategies

### 1. Hash Sharding
```
shard_id = hash(key) % num_shards

User ID 100 → hash(100) = 2 → Shard 2
User ID 101 → hash(101) = 0 → Shard 0
User ID 102 → hash(102) = 1 → Shard 1

Pros: Even distribution
Cons: Adding shard requires data migration (rehashing)
```

### 2. Range Sharding
```
Shard 0: User ID 0-999
Shard 1: User ID 1000-1999
Shard 2: User ID 2000+

Pros: Simple, supports range queries
Cons: Hot spots (new users all go to last shard)
```

### 3. Directory-Based Sharding
```
Lookup table:
UserID → ShardID
100    → Shard 2
200    → Shard 0
300    → Shard 1

Pros: Flexible, can rebalance easily
Cons: Single point of failure (lookup service)
```

### 4. Consistent Hashing
```
Hash ring (0-999):
    0
   / \
 999   1
  |     |
 998   ...
  \   /
   500

Shard 0: nodes 0-333
Shard 1: nodes 334-666
Shard 2: nodes 667-999

Virtual nodes giúp distribute đều hơn
Adding shard: chỉ migrate từ adjacent shards
```

```java
// Consistent Hash Ring implementation
private TreeMap<Integer, Integer> hashRing = new TreeMap<>();

private void buildHashRing() {
    for (int shard = 0; shard < NUM_SHARDS; shard++) {
        for (int v = 0; v < VIRTUAL_NODES; v++) {
            int hash = Math.abs(("shard-" + shard + "-vnode-" + v).hashCode()) % 1000;
            hashRing.put(hash, shard);
        }
    }
}

public int getShardForKey(Long key) {
    int hash = Math.abs(key.hashCode()) % 1000;
    Map.Entry<Integer, Integer> entry = hashRing.ceilingEntry(hash);
    if (entry == null) entry = hashRing.firstEntry(); // wrap around
    return entry.getValue();
}
```

## Code Example

```java
@Service
public class ShardingService {
    private final Map<Integer, Map<Long, User>> shards = new HashMap<>();
    
    // Hash sharding
    public User saveWithHashSharding(User user) {
        int shardId = (int) (Math.abs(user.getId()) % NUM_SHARDS);
        user.setShardId(shardId);
        shards.get(shardId).put(user.getId(), user);
        return user;
    }
    
    // Range sharding
    public User saveWithRangeSharding(User user) {
        int shardId;
        long id = user.getId();
        if (id < 1000) shardId = 0;
        else if (id < 2000) shardId = 1;
        else shardId = 2;
        // save to appropriate shard
    }
}
```

## Spring Dynamic Datasource Routing

```java
public class ShardRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.getCurrentShard(); // Thread-local
    }
}

// Usage
public User findById(Long userId) {
    int shardId = consistentHash.getShardFor(userId);
    ShardContext.setCurrentShard(shardId);
    try {
        return userRepository.findById(userId);
    } finally {
        ShardContext.clear();
    }
}
```

## Project Structure

```
lesson-16-sharding/
├── sharding-demo/
│   ├── src/main/java/com/distributed/sharding/
│   │   ├── ShardingDemoApplication.java
│   │   ├── model/User.java
│   │   ├── config/ShardDataSourceConfig.java
│   │   ├── service/ShardingService.java
│   │   └── controller/ShardingController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Populate demo data (30 users với hash sharding)
curl -X POST http://localhost:8080/api/sharding/demo/populate

# Xem distribution stats
curl http://localhost:8080/api/sharding/stats

# Hash sharding
curl -X POST http://localhost:8080/api/sharding/hash \
  -H "Content-Type: application/json" \
  -d '{"id":100,"username":"user100","email":"user100@example.com","region":"VN"}'

# Range sharding
curl -X POST http://localhost:8080/api/sharding/range \
  -H "Content-Type: application/json" \
  -d '{"id":500,"username":"rangeuser","email":"range@example.com","region":"US"}'

# Consistent hashing
curl -X POST http://localhost:8080/api/sharding/consistent-hashing \
  -H "Content-Type: application/json" \
  -d '{"id":1234,"username":"hashuser","email":"hash@example.com","region":"EU"}'

# Find user (searches all shards)
curl http://localhost:8080/api/sharding/users/100

# Hash ring info
curl http://localhost:8080/api/sharding/hash-ring
```

## Cross-Shard Queries (Scatter-Gather)

```java
// Query phải scan tất cả shards
public List<User> findByEmail(String email) {
    return shards.values().parallelStream()
        .flatMap(shard -> shard.values().stream())
        .filter(user -> user.getEmail().equals(email))
        .collect(Collectors.toList());
}

// Aggregate across shards
public long countAllUsers() {
    return shards.values().stream()
        .mapToLong(shard -> shard.size())
        .sum();
}
```

## Resharding (Adding new shard)

```
Before: 3 shards (0, 1, 2)
After: 4 shards (0, 1, 2, 3)

Hash sharding:
- All data must be remapped: id % 4 instead of id % 3
- Migration required: ~75% of data moves!

Consistent hashing:
- Only ~25% of data moves to new shard
- Much less disruption
```

## Production Tips

### 1. Global Unique IDs
```java
// Không dùng auto-increment (sẽ bị conflict giữa shards)
// Dùng Snowflake ID hoặc UUID

// Snowflake: timestamp + machine_id + sequence
// ID structure: 41bit timestamp | 10bit machine | 12bit sequence
public long generateId() {
    long timestamp = System.currentTimeMillis() - EPOCH;
    return (timestamp << 22) | (machineId << 12) | sequence++;
}
```

### 2. Shard Key Selection
```
Good shard keys:
✓ High cardinality (many unique values)
✓ Evenly distributed
✓ Frequently used in WHERE clauses
✓ Not changing over time

Bad shard keys:
✗ Low cardinality (gender, status)
✗ Sequential (timestamp, auto-increment)
✗ Expensive to compute
```

### 3. Vitess (MySQL sharding)
```yaml
# Vitess: Production-grade MySQL sharding
# Used by YouTube, Slack
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vtgate
spec:
  containers:
    - name: vtgate
      image: vitess/vtgate
```

## Interview Q&A

**Q: Sharding khác với partitioning?**
A: Partitioning thường trong cùng một database server (logical). Sharding là phân tán ra nhiều servers khác nhau (physical distribution). Sharding là horizontal partitioning across multiple nodes.

**Q: Tại sao consistent hashing tốt hơn hash modulo?**
A: Hash modulo (id % N): khi thêm node, gần như tất cả keys phải remap (1 - N/(N+1) = 1/N+1 remain). Consistent hashing: chỉ remap K/N keys (K=total, N=nodes). Ít disruption hơn nhiều.

**Q: Cross-shard transactions xử lý như thế nào?**
A: Avoid nếu có thể. Nếu bắt buộc: (1) Saga pattern, (2) Two-Phase Commit, (3) Compensating transactions. Đây là major complexity của sharding.

**Q: Shard hot spots là gì?**
A: Một shard nhận quá nhiều traffic. Ví dụ: range sharding với timestamp - new writes luôn vào shard cuối. Giải pháp: composite shard key, hash trong range.

**Q: Khi nào nên dùng sharding?**
A: Khi đã tối ưu hết (indexes, caching, read replicas) mà vẫn không đủ. Single server: ~10TB data, ~100K QPS limit. Sharding adds significant complexity.
