package com.distributed.observability.controller;

import com.distributed.observability.service.MetricsService;
import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostMapping("/orders")
    @Timed(value = "http.orders.create", description = "Time to create an order")
    public Map<String, Object> createOrder(@RequestParam String productId, @RequestParam(defaultValue = "1") int quantity) {
        return metricsService.processOrder(productId, quantity);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return metricsService.getMetricsSummary();
    }

    @PostMapping("/load-test")
    public Map<String, Object> loadTest(@RequestParam(defaultValue = "10") int requests) throws InterruptedException {
        int success = 0;
        int failed = 0;
        for (int i = 0; i < requests; i++) {
            try {
                metricsService.processOrder("PROD-" + (i % 5 + 1), i % 3 + 1);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        return Map.of("requested", requests, "success", success, "failed", failed);
    }
}
