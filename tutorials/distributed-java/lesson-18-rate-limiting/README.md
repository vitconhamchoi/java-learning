# Lesson 18: Rate Limiting

## Giới thiệu

Rate Limiting kiểm soát số lượng requests một client có thể gửi trong một khoảng thời gian. Bảo vệ service khỏi abuse, DDoS, và đảm bảo fair usage.

## Rate Limiting Algorithms

### 1. Fixed Window Counter
```
Window: 60 seconds
Limit: 10 requests

00:00:00 ─────────────── 00:01:00
│ req 1,2,3,4,5,6,7,8,9,10 │ req 11 → REJECTED
└──────────────────────────┘

Problem: Burst at window boundary
00:00:59: 10 requests (within limit)
00:01:00: 10 requests (new window, within limit)
Total: 20 requests in 2 seconds!
```

### 2. Sliding Window Log
```
Keep log of timestamps for each request
Window: last 60 seconds
Limit: 10

When new request arrives:
1. Remove timestamps older than now-60s
2. Count remaining
3. If count < limit: allow + add timestamp
4. Else: reject

More accurate but memory-intensive
```

### 3. Token Bucket
```
Bucket capacity: 10 tokens
Refill rate: 2 tokens/second

Start: [10 tokens]
10 requests → [0 tokens]
Wait 5s → [10 tokens] (refilled)
1 request → [9 tokens]

Allows bursting up to bucket capacity
Natural "leaky bucket" behavior
```

### 4. Leaky Bucket
```
Requests enter at any rate → Queue → Process at fixed rate

Input: burst    Output: steady
┌──────┐       ┌──────┐
│██ ██ │ ────► │ leak │ ────► 1 req/s
│██████│       └──────┘
└──────┘
```

## Redis Sliding Window (Lua Script)

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- Remove old entries
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- Count current
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, now)
    redis.call('EXPIRE', key, 61)
    return {1, count + 1}
else
    return {0, count}
end
```

## Redis Token Bucket (Lua Script)

```lua
local tokens_key = KEYS[1]
local last_refill_key = KEYS[2]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])  -- tokens per second
local now = tonumber(ARGV[3])           -- milliseconds

local tokens = tonumber(redis.call('GET', tokens_key) or capacity)
local last_refill = tonumber(redis.call('GET', last_refill_key) or now)

-- Calculate new tokens since last refill
local elapsed = (now - last_refill) / 1000  -- seconds
local new_tokens = math.min(capacity, tokens + elapsed * refill_rate)

if new_tokens >= 1 then
    redis.call('SET', tokens_key, new_tokens - 1, 'EX', 3600)
    redis.call('SET', last_refill_key, now, 'EX', 3600)
    return {1, math.floor(new_tokens - 1)}
else
    return {0, 0}
end
```

## Resilience4j RateLimiter

```yaml
resilience4j:
  ratelimiter:
    instances:
      apiRateLimiter:
        limitForPeriod: 5          # 5 requests per period
        limitRefreshPeriod: 10s    # Reset every 10s
        timeoutDuration: 0         # Don't wait, fail immediately
        registerHealthIndicator: true
```

```java
@RateLimiter(name = "apiRateLimiter", fallbackMethod = "rateLimitFallback")
public Map<String, Object> apiEndpoint() {
    return Map.of("status", "OK");
}

public Map<String, Object> rateLimitFallback(Exception ex) {
    return Map.of("status", "RATE_LIMITED", "retryAfter", "10s");
}
```

## HTTP Response Headers

```
Standard rate limit headers:
X-RateLimit-Limit: 100        # Max requests per window
X-RateLimit-Remaining: 45     # Remaining requests
X-RateLimit-Reset: 1706745600 # Unix timestamp of reset

429 Too Many Requests
Retry-After: 30               # Seconds until retry
```

```java
@GetMapping("/api/data")
public ResponseEntity<Object> getData(HttpServletResponse response) {
    var result = rateLimiterService.check(clientId, 100, 60);
    response.setHeader("X-RateLimit-Limit", "100");
    response.setHeader("X-RateLimit-Remaining", result.remaining().toString());
    
    if (!result.allowed()) {
        response.setHeader("Retry-After", "60");
        return ResponseEntity.status(429).body(Map.of("error", "Too Many Requests"));
    }
    return ResponseEntity.ok(data);
}
```

## Project Structure

```
lesson-18-rate-limiting/
├── rate-limiter-demo/
│   ├── src/main/java/com/distributed/ratelimit/
│   │   ├── RateLimiterDemoApplication.java
│   │   ├── config/RedisConfig.java
│   │   ├── service/RedisRateLimiterService.java
│   │   └── controller/RateLimiterController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Fixed window (10 requests/60s per client)
for i in {1..12}; do
  echo -n "Request $i: "
  curl -s "http://localhost:8080/api/rate-limit/fixed-window?clientId=client1&limit=10&windowSeconds=60" | jq .allowed
done

# Sliding window (10 requests/60s)
for i in {1..12}; do
  curl -s "http://localhost:8080/api/rate-limit/sliding-window?clientId=client2&limit=10&windowMs=60000" | jq '{allowed: .allowed, count: .currentCount}'
done

# Token bucket (capacity=5, refill=1 token/sec)
for i in {1..8}; do
  curl -s "http://localhost:8080/api/rate-limit/token-bucket?clientId=client3&capacity=5&refillRate=1" | jq '{allowed: .allowed, tokens: .remainingTokens}'
done

# Resilience4j (5 requests per 10s)
for i in {1..7}; do
  echo -n "R4J Request $i: "
  curl -s "http://localhost:8080/api/rate-limit/resilience4j" | jq .status
done

# Different clients (different limits)
curl "http://localhost:8080/api/rate-limit/fixed-window?clientId=premium-client&limit=100"
curl "http://localhost:8080/api/rate-limit/fixed-window?clientId=free-client&limit=10"
```

## Multi-Level Rate Limiting

```
Level 1: Per IP Address      → 1000 req/min
Level 2: Per API Key          → 100 req/min
Level 3: Per User             → 50 req/min
Level 4: Per Endpoint         → 10 req/min

Check all levels, reject if any exceeded
```

```java
public RateLimitResult checkAll(String ip, String apiKey, String userId, String endpoint) {
    RateLimitResult ipCheck = check("ip:" + ip, 1000, 60);
    RateLimitResult apiKeyCheck = check("apikey:" + apiKey, 100, 60);
    RateLimitResult userCheck = check("user:" + userId, 50, 60);
    RateLimitResult endpointCheck = check("endpoint:" + endpoint, 10, 60);
    
    return Stream.of(ipCheck, apiKeyCheck, userCheck, endpointCheck)
        .filter(r -> !r.allowed())
        .findFirst()
        .orElse(RateLimitResult.allowed());
}
```

## Production Tips

### 1. Distributed vs Local Rate Limiter
```
Local (in-memory):
✓ Fast, no network overhead
✗ Each instance has own counter
✗ With 10 instances: effective limit = limit * 10

Redis-based:
✓ Shared across all instances
✓ Accurate rate limiting
✗ Network overhead (~1ms)
```

### 2. Graceful Degradation
```java
// If Redis is down, fallback to local limiter
try {
    return redisRateLimiter.check(key, limit);
} catch (RedisException e) {
    log.warn("Redis unavailable, using local limiter");
    return localRateLimiter.check(key, limit);
}
```

### 3. Rate Limit Headers in OpenAPI
```yaml
responses:
  '429':
    description: Too Many Requests
    headers:
      X-RateLimit-Limit:
        schema:
          type: integer
      Retry-After:
        schema:
          type: integer
```

## Interview Q&A

**Q: Phân biệt Rate Limiting vs Throttling?**
A: Rate Limiting: giới hạn số requests (count-based). Throttling: giảm tốc độ xử lý (delay requests). Rate limiting thường binary (allow/reject), throttling có thể queue và delay.

**Q: Token bucket vs sliding window nên dùng khi nào?**
A: Token bucket: cho phép bursting (flash sales), natural smoothing. Sliding window: chính xác hơn, không burst, tốt cho APIs cần strict fairness.

**Q: Rate limit headers nên trả về gì?**
A: Tối thiểu: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After (trong 429). Giúp clients implement backoff correctly.

**Q: Làm sao identify client cho rate limiting?**
A: (1) IP address (có thể shared qua NAT), (2) API key (best practice), (3) User ID (authenticated), (4) Combination. API key tốt nhất vì accurate và per-client.

**Q: Distributed rate limiting challenges?**
A: Race conditions (dùng Lua scripts cho atomicity), clock skew giữa servers (Redis single server clock), Redis failure (fallback strategy), key expiration management.
