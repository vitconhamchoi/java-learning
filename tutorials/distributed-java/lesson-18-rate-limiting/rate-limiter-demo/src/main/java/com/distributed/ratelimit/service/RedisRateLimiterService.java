package com.distributed.ratelimit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisRateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimiterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Fixed Window Rate Limiter
    public Map<String, Object> fixedWindowCheck(String clientId, int limit, int windowSeconds) {
        String key = "rl:fixed:" + clientId + ":" + (Instant.now().getEpochSecond() / windowSeconds);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

        boolean allowed = count <= limit;
        log.info("Fixed window: client={}, count={}, limit={}, allowed={}", clientId, count, limit, allowed);
        return Map.of(
            "strategy", "FIXED_WINDOW",
            "clientId", clientId,
            "allowed", allowed,
            "currentCount", count,
            "limit", limit,
            "windowSeconds", windowSeconds,
            "remaining", Math.max(0, limit - count)
        );
    }

    // Sliding Window Log Rate Limiter (using Sorted Set)
    public Map<String, Object> slidingWindowCheck(String clientId, int limit, int windowMs) {
        String key = "rl:sliding:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        // Lua script for atomic operation
        String script = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_start = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local window_ms = tonumber(ARGV[4])
            -- Remove old entries
            redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
            -- Count current entries
            local count = redis.call('ZCARD', key)
            -- Check limit
            if count < limit then
                redis.call('ZADD', key, now, now)
                redis.call('EXPIRE', key, math.ceil(window_ms / 1000) + 1)
                return {1, count + 1}
            else
                return {0, count}
            end
            """;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        List<?> result = (List<?>) redisTemplate.execute(redisScript,
            List.of(key), String.valueOf(now), String.valueOf(windowStart),
            String.valueOf(limit), String.valueOf(windowMs));

        boolean allowed = result != null && ((Number) result.get(0)).intValue() == 1;
        long count = result != null ? ((Number) result.get(1)).longValue() : 0;

        return Map.of(
            "strategy", "SLIDING_WINDOW",
            "clientId", clientId,
            "allowed", allowed,
            "currentCount", count,
            "limit", limit,
            "windowMs", windowMs,
            "remaining", Math.max(0, limit - count)
        );
    }

    // Token Bucket Rate Limiter
    public Map<String, Object> tokenBucketCheck(String clientId, int capacity, int refillRate) {
        String tokensKey = "rl:bucket:tokens:" + clientId;
        String lastRefillKey = "rl:bucket:lastRefill:" + clientId;

        String script = """
            local tokens_key = KEYS[1]
            local last_refill_key = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local tokens = tonumber(redis.call('GET', tokens_key) or capacity)
            local last_refill = tonumber(redis.call('GET', last_refill_key) or now)
            local elapsed = (now - last_refill) / 1000
            local new_tokens = math.min(capacity, tokens + elapsed * refill_rate)
            if new_tokens >= 1 then
                redis.call('SET', tokens_key, new_tokens - 1, 'EX', 3600)
                redis.call('SET', last_refill_key, now, 'EX', 3600)
                return {1, math.floor(new_tokens - 1)}
            else
                redis.call('SET', tokens_key, new_tokens, 'EX', 3600)
                redis.call('SET', last_refill_key, now, 'EX', 3600)
                return {0, 0}
            end
            """;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        List<?> result = (List<?>) redisTemplate.execute(redisScript,
            List.of(tokensKey, lastRefillKey),
            String.valueOf(capacity), String.valueOf(refillRate),
            String.valueOf(System.currentTimeMillis()));

        boolean allowed = result != null && ((Number) result.get(0)).intValue() == 1;
        long remainingTokens = result != null ? ((Number) result.get(1)).longValue() : 0;

        return Map.of(
            "strategy", "TOKEN_BUCKET",
            "clientId", clientId,
            "allowed", allowed,
            "remainingTokens", remainingTokens,
            "capacity", capacity,
            "refillRate", refillRate + " tokens/sec"
        );
    }
}
