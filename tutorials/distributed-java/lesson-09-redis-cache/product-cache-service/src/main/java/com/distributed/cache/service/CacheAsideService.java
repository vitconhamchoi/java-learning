package com.distributed.cache.service;
import com.distributed.cache.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache-Aside implementation với distributed lock để ngăn Cache Stampede.
 *
 * Vấn đề Cache Stampede:
 * - Cache key expire → nhiều requests đồng thời hit DB
 * - DB bị quá tải → latency tăng → timeout → cascade failure
 *
 * Giải pháp: Distributed Lock (Redis SETNX)
 * - Chỉ 1 request được load DB (holder của lock)
 * - Các requests khác chờ hoặc trả stale data
 */
@Service
public class CacheAsideService {
    private static final Logger log = LoggerFactory.getLogger(CacheAsideService.class);
    private static final String CACHE_PREFIX = "product:cache:";
    private static final String LOCK_PREFIX = "product:lock:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Fake DB
    private final Map<String, Product> db = new ConcurrentHashMap<>(Map.of(
        "1", new Product("1", "iPad Pro", new BigDecimal("25990000"), "Tablet"),
        "2", new Product("2", "AirPods Pro", new BigDecimal("6990000"), "Audio")
    ));

    public CacheAsideService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Cache-Aside với distributed lock để ngăn stampede.
     * Nếu cache miss: acquire lock → load DB → populate cache → release lock.
     */
    public Product getProduct(String id) {
        String cacheKey = CACHE_PREFIX + id;
        String lockKey = LOCK_PREFIX + id;

        // Bước 1: Check cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT: {}", id);
            return objectMapper.convertValue(cached, Product.class);
        }

        // Bước 2: Cache miss - thử lấy distributed lock
        log.info("Cache MISS: {} - Attempting to acquire lock", id);
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);  // SETNX: set if not exists

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // Bước 3: Double-check cache (có thể được populate bởi thread khác)
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return objectMapper.convertValue(cached, Product.class);
                }

                // Bước 4: Load từ DB
                Product product = loadFromDb(id);
                if (product != null) {
                    // Bước 5: Populate cache
                    redisTemplate.opsForValue().set(cacheKey, product, CACHE_TTL);
                    log.info("Cache populated for product: {}", id);
                }
                return product;
            } finally {
                // Bước 6: Release lock
                redisTemplate.delete(lockKey);
            }
        } else {
            // Lock bị hold bởi thread khác - chờ ngắn và retry từ cache
            log.debug("Lock held by another thread, waiting...");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? objectMapper.convertValue(cached, Product.class) : loadFromDb(id);
        }
    }

    private Product loadFromDb(String id) {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return db.get(id);
    }
}
