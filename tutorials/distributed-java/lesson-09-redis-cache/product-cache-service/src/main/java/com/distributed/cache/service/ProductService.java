package com.distributed.cache.service;
import com.distributed.cache.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProductService dùng Spring Cache annotations.
 * @Cacheable: Check cache trước, nếu miss thì gọi method và cache kết quả.
 * @CachePut: Luôn gọi method và update cache (dùng khi update data).
 * @CacheEvict: Xóa khỏi cache (dùng khi delete data).
 */
@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // Fake "database" in-memory
    private final Map<String, Product> db = new ConcurrentHashMap<>(Map.of(
        "1", new Product("1", "iPhone 15 Pro", new BigDecimal("29990000"), "Smartphone"),
        "2", new Product("2", "Samsung S24", new BigDecimal("24990000"), "Smartphone"),
        "3", new Product("3", "MacBook Air M3", new BigDecimal("32990000"), "Laptop")
    ));

    /** Cache theo id. Cache key mặc định = method argument = id */
    @Cacheable(value = "products", key = "#id")
    public Product findById(String id) {
        log.info("Cache MISS - Loading product {} from DB", id);
        simulateDbLatency();
        return db.get(id);
    }

    /** Luôn update cache khi save. key = product id */
    @CachePut(value = "products", key = "#p.id")
    public Product save(Product p) {
        log.info("Saving product {} and updating cache", p.id());
        db.put(p.id(), p);
        return p;
    }

    /** Xóa khỏi cache khi delete */
    @CacheEvict(value = "products", key = "#id")
    public void delete(String id) {
        log.info("Deleting product {} and evicting cache", id);
        db.remove(id);
    }

    /** Cache danh sách tất cả products */
    @Cacheable(value = "all-products", key = "'all'")
    public List<Product> findAll() {
        log.info("Cache MISS - Loading all products from DB");
        simulateDbLatency();
        return new ArrayList<>(db.values());
    }

    /** Evict all caches khi data thay đổi nhiều */
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#id"),
        @CacheEvict(value = "all-products", allEntries = true)
    })
    public void deleteWithAllEviction(String id) {
        db.remove(id);
    }

    private void simulateDbLatency() {
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
