package com.distributed.cb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile boolean forceFailure = false;
    private volatile int failEveryN = 0; // 0 = no failure simulation

    @GetMapping("/check")
    public Map<String, Object> checkInventory(@RequestParam String productId, @RequestParam int quantity) {
        int count = requestCount.incrementAndGet();
        log.info("Inventory check #{} for product: {}, quantity: {}", count, productId, quantity);

        if (forceFailure || (failEveryN > 0 && count % failEveryN == 0)) {
            log.warn("Simulating failure for request #{}", count);
            throw new RuntimeException("Inventory service temporarily unavailable");
        }

        // Simulate slow response occasionally
        if (count % 5 == 0) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        int available = 100;
        return Map.of(
            "productId", productId,
            "available", available,
            "sufficient", quantity <= available,
            "requestCount", count
        );
    }

    @PostMapping("/simulate/failure")
    public Map<String, Object> setForceFailure(@RequestParam boolean enabled) {
        this.forceFailure = enabled;
        return Map.of("forceFailure", enabled, "message", "Failure simulation " + (enabled ? "enabled" : "disabled"));
    }

    @PostMapping("/simulate/fail-every")
    public Map<String, Object> setFailEveryN(@RequestParam int n) {
        this.failEveryN = n;
        return Map.of("failEveryN", n, "message", "Will fail every " + n + " requests");
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return Map.of("totalRequests", requestCount.get(), "forceFailure", forceFailure, "failEveryN", failEveryN);
    }
}
