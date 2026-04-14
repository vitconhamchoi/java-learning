package com.distributed.cqrs.service;

import com.distributed.cqrs.event.ProductEvent;
import com.distributed.cqrs.model.Product;
import com.distributed.cqrs.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CommandService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Product createProduct(Map<String, Object> request) {
        Product product = new Product();
        product.setName((String) request.get("name"));
        product.setDescription((String) request.get("description"));
        product.setPrice(((Number) request.get("price")).doubleValue());
        product.setStock(((Number) request.get("stock")).intValue());
        
        Product saved = productRepository.save(product);
        
        var event = new ProductEvent("PRODUCT_CREATED", saved.getId(), saved.getName(),
            saved.getPrice(), saved.getStock(), LocalDateTime.now());
        eventPublisher.publishEvent(event);
        log.info("Published PRODUCT_CREATED event for product: {}", saved.getId());
        
        return saved;
    }

    @Transactional
    public Product updateStock(Long productId, int delta) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        
        int newStock = product.getStock() + delta;
        if (newStock < 0) throw new IllegalStateException("Insufficient stock");
        product.setStock(newStock);
        
        Product saved = productRepository.save(product);
        
        var event = new ProductEvent("STOCK_UPDATED", saved.getId(), saved.getName(),
            saved.getPrice(), saved.getStock(), LocalDateTime.now());
        eventPublisher.publishEvent(event);
        log.info("Published STOCK_UPDATED event for product: {}", saved.getId());
        
        return saved;
    }
}
