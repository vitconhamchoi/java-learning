# Lesson 09: Redis Caching Strategies

## Giới thiệu

Redis là in-memory data store thường dùng làm cache, session store, pub/sub broker. Caching giảm latency và tải cho database.

## Caching Strategies

### 1. Cache-Aside (Lazy Loading)
```
App → Read Cache → HIT → Return data
                → MISS → Read DB → Write Cache → Return data
```
Ưu: Chỉ cache data được request. Nhược: Cache miss tốn 2 round trips.

### 2. Read-Through
Cache layer tự động load từ DB khi miss. App chỉ giao tiếp với cache.

### 3. Write-Through
Mỗi write đến cache đồng thời write DB. Consistency cao, write chậm hơn.

### 4. Write-Behind (Write-Back)
Write đến cache trước, async write DB sau. Throughput cao, risk mất data khi crash.

## Cache Stampede / Thundering Herd

```
Vấn đề: Cache key expire → 100 requests đồng thời hit DB
Giải pháp 1: Distributed Lock (SETNX)
  if SETNX(lock_key):
    load from DB
    set cache
    delete lock
  else:
    wait and retry

Giải pháp 2: Probabilistic Early Expiration
  Tính xác suất expire sớm hơn dựa trên remaining TTL
```

## Spring Cache Annotations

```java
@Cacheable("products")           // Đọc từ cache, nếu miss thì gọi method
@CachePut(value="products", key="#p.id")  // Luôn gọi method và update cache
@CacheEvict("products")          // Xóa khỏi cache
@CacheEvict(value="products", allEntries=true)  // Xóa tất cả
```

## Redis Data Structures

| Structure | Use Case |
|-----------|----------|
| String | Cache key-value, counters, sessions |
| Hash | Object fields, user profiles |
| List | Queue, activity feed |
| Set | Unique members, tags |
| Sorted Set | Leaderboard, time-series |
| Pub/Sub | Cache invalidation, real-time events |

## Redis Cluster vs Sentinel

### Sentinel: High Availability
```
Master ──► Replica 1
       └── Replica 2
Sentinel monitors Master, promotes Replica khi Master fail
```

### Cluster: Horizontal Scaling
```
Cluster shards data: 16384 hash slots phân tán qua nodes
Node 1: slots 0-5460
Node 2: slots 5461-10922
Node 3: slots 10923-16383
```

## Redis Pub/Sub cho Cache Invalidation

```
Instance A (update) → PUBLISH "cache:invalidation" "product:123"
                                      ↓
Instance B (subscribe) ← Nhận message → Evict local cache
Instance C (subscribe) ← Nhận message → Evict local cache
```

## Lua Scripts - Atomic Operations

```lua
-- Atomic check-and-set (compare-and-swap)
local current = redis.call('GET', KEYS[1])
if current == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
else
    return 0
end
```

## Production Considerations

1. **TTL Strategy**: Mỗi cache key nên có TTL để tránh stale data và memory leak
2. **Eviction Policy**: `allkeys-lru` cho general cache; `volatile-lru` khi mix cached + persistent data
3. **Memory Limit**: Set `maxmemory` để tránh OOM; Redis evict keys khi đầy
4. **Serialization**: Jackson JSON vs Java Serialization - JSON dễ debug hơn
5. **Key Naming**: `<app>:<entity>:<id>` (e.g., `myapp:product:123`)
6. **Cache Warming**: Preload hot data sau khi deploy

## Anti-patterns

### ❌ Cache Everything
```java
// Không phải mọi thứ đều nên cache
// Dữ liệu thay đổi liên tục → cache ít giá trị
// Dữ liệu cá nhân của từng user → cache overhead lớn
```

### ❌ Missing TTL
```java
// KHÔNG NÊN: Không có TTL → memory leak
redisTemplate.opsForValue().set("key", value);

// NÊN: Luôn set TTL
redisTemplate.opsForValue().set("key", value, 10, TimeUnit.MINUTES);
```

### ❌ Caching Exceptions
```java
// Không nên cache exceptions/error responses
// Cache miss storm khi service downstream down
```

### ❌ Large Objects in Cache
```
Object > 1MB → Tiêu tốn bandwidth serialize/deserialize
Nên store reference + lấy từ S3/blob khi cần
```

## Interview Questions & Answers

**Q: Cache-Aside vs Read-Through?**
A: Cache-Aside: App tự quản lý cache (check → miss → load DB → populate cache). Read-Through: Cache layer tự load khi miss, app chỉ interact với cache. Cache-Aside linh hoạt hơn, Read-Through đơn giản hơn cho app code.

**Q: Làm thế nào giải quyết Cache Stampede?**
A: Distributed lock (SETNX): Chỉ 1 request load DB, các request khác chờ. Probabilistic early expiration: Expire cache trước TTL với xác suất tăng dần khi gần hết hạn. Stale-while-revalidate: Trả stale data trong khi async refresh.

**Q: Redis Sentinel vs Cluster?**
A: Sentinel: HA cho single shard, automatic failover, không scale horizontally. Cluster: Horizontal scaling, sharding 16384 slots, phức tạp hơn, cần client support.

**Q: Khi nào dùng Redis pub/sub cho cache invalidation?**
A: Khi có nhiều instances cần invalidate local cache đồng bộ. Nhanh hơn polling, eventual consistency. Chú ý: pub/sub messages không persist, nếu instance offline sẽ miss invalidation.

**Q: Write-Through vs Write-Behind?**
A: Write-Through: Đồng bộ write cả cache và DB → consistency cao, write chậm. Write-Behind: Write cache trước, async DB → throughput cao, risk mất data khi crash (dùng khi write volume rất cao).

## Cấu trúc Project

```
lesson-09-redis-cache/
├── README.md
├── docker-compose.yml
└── product-cache-service/
    └── src/main/java/com/distributed/cache/
        ├── ProductCacheApplication.java
        ├── model/Product.java
        ├── config/RedisConfig.java
        ├── service/
        │   ├── ProductService.java
        │   ├── CacheAsideService.java
        │   └── CacheInvalidationService.java
        └── controller/ProductController.java
```
