package com.distributed.cache.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/**
 * Cache Invalidation qua Redis Pub/Sub.
 * Khi 1 instance update data, publish invalidation message.
 * Tất cả instances (kể cả local cache) nhận message và evict.
 */
@Service
public class CacheInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);
    private static final String INVALIDATION_CHANNEL = "cache:invalidation";

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;
    private final RedisMessageListenerContainer listenerContainer;

    public CacheInvalidationService(RedisTemplate<String, Object> redisTemplate,
                                    CacheManager cacheManager,
                                    RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
        this.listenerContainer = listenerContainer;
    }

    /** Subscribe to invalidation channel on startup */
    @PostConstruct
    public void subscribeToInvalidation() {
        listenerContainer.addMessageListener((message, pattern) -> {
            String productId = new String(message.getBody());
            log.info("Received cache invalidation for productId: {}", productId);
            evictLocalCache(productId);
        }, new PatternTopic(INVALIDATION_CHANNEL));
    }

    /** Publish invalidation message - tất cả instances sẽ nhận và evict */
    public void publishInvalidation(String productId) {
        log.info("Publishing cache invalidation for productId: {}", productId);
        redisTemplate.convertAndSend(INVALIDATION_CHANNEL, productId);
    }

    /** Evict product khỏi local Spring Cache */
    private void evictLocalCache(String productId) {
        var productsCache = cacheManager.getCache("products");
        if (productsCache != null) productsCache.evict(productId);
        var allProductsCache = cacheManager.getCache("all-products");
        if (allProductsCache != null) allProductsCache.clear();
        log.info("Local cache evicted for productId: {}", productId);
    }
}
