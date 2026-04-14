package com.distributed.cqrs.service;

import com.distributed.cqrs.model.ProductView;
import com.distributed.cqrs.repository.ProductViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class QueryService {

    private final ProductViewRepository repository;

    public QueryService(ProductViewRepository repository) {
        this.repository = repository;
    }

    public List<ProductView> getAllProducts() {
        return repository.findAll();
    }

    public ProductView getProduct(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public List<ProductView> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    public List<ProductView> searchByPriceRange(double min, double max) {
        return repository.findByPriceBetween(min, max);
    }

    public List<ProductView> getInStockProducts() {
        return repository.findByStockGreaterThan(0);
    }

    // Simulate event handler updating read model
    public ProductView upsertFromEvent(Map<String, Object> event) {
        Long productId = Long.valueOf(event.get("productId").toString());
        ProductView view = repository.findById(productId).orElse(new ProductView());
        view.setProductId(productId);
        view.setName((String) event.get("productName"));
        view.setPrice(((Number) event.get("price")).doubleValue());
        view.setStock(((Number) event.get("stock")).intValue());
        view.setLastUpdated(LocalDateTime.now());
        return repository.save(view);
    }
}
