package com.distributed.cache.controller;
import com.distributed.cache.model.Product;
import com.distributed.cache.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;
    private final CacheAsideService cacheAsideService;
    private final CacheInvalidationService invalidationService;

    public ProductController(ProductService ps, CacheAsideService cas, CacheInvalidationService cis) {
        this.productService = ps; this.cacheAsideService = cas; this.invalidationService = cis;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        Product p = productService.findById(id);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/cache-aside")
    public ResponseEntity<Product> getProductCacheAside(@PathVariable String id) {
        Product p = cacheAsideService.getProduct(id);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable String id, @RequestBody Product p) {
        Product saved = productService.save(new Product(id, p.name(), p.price(), p.category()));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.delete(id);
        invalidationService.publishInvalidation(id);
        return ResponseEntity.noContent().build();
    }
}
