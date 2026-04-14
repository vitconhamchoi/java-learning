package com.distributed.cb.service;

import com.distributed.cb.config.InventoryClient;
import com.distributed.cb.model.OrderRequest;
import com.distributed.cb.model.OrderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final InventoryClient inventoryClient;

    public OrderService(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @Retry(name = "inventoryService")
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Checking inventory for product: {}", request.getProductId());
        var inventory = inventoryClient.checkInventory(request.getProductId(), request.getQuantity());
        
        if (inventory.sufficient()) {
            String orderId = UUID.randomUUID().toString();
            log.info("Order created: {} for product {}", orderId, request.getProductId());
            return new OrderResponse(orderId, "CREATED", 
                "Order placed successfully. Available: " + inventory.available());
        } else {
            return new OrderResponse(null, "REJECTED", 
                "Insufficient inventory. Available: " + inventory.available());
        }
    }

    public OrderResponse inventoryFallback(OrderRequest request, Exception ex) {
        log.warn("Circuit breaker triggered for product: {}. Error: {}", request.getProductId(), ex.getMessage());
        return new OrderResponse(null, "FALLBACK", 
            "Inventory service unavailable. Order queued for later processing.");
    }
}
