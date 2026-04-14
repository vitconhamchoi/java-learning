package com.distributed.saga.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final Map<String, Integer> reservations = new ConcurrentHashMap<>();

    public InventoryController() {
        inventory.put("PROD-001", 100);
        inventory.put("PROD-002", 50);
        inventory.put("PROD-003", 0);
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        String sagaId = (String) request.get("sagaId");

        int available = inventory.getOrDefault(productId, 0);
        if (available < quantity) {
            throw new RuntimeException("Insufficient inventory. Available: " + available + ", Requested: " + quantity);
        }

        inventory.put(productId, available - quantity);
        reservations.put(sagaId, quantity);
        log.info("Reserved {} units of {} for saga {}", quantity, productId, sagaId);

        return Map.of("status", "RESERVED", "productId", productId, "quantity", quantity, "remaining", available - quantity);
    }

    @PostMapping("/release")
    public Map<String, Object> release(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        String sagaId = (String) request.get("sagaId");

        inventory.merge(productId, quantity, Integer::sum);
        reservations.remove(sagaId);
        log.info("Released {} units of {} for saga {}", quantity, productId, sagaId);

        return Map.of("status", "RELEASED", "productId", productId, "quantity", quantity);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of("inventory", inventory, "activeReservations", reservations.size());
    }
}
