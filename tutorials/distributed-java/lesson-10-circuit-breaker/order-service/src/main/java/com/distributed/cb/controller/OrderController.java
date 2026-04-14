package com.distributed.cb.controller;

import com.distributed.cb.model.OrderRequest;
import com.distributed.cb.model.OrderResponse;
import com.distributed.cb.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public OrderController(OrderService orderService, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderService = orderService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/circuit-breaker/status")
    public Map<String, Object> getCircuitBreakerStatus() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("inventoryService");
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        return Map.of(
            "state", cb.getState().name(),
            "failureRate", metrics.getFailureRate(),
            "slowCallRate", metrics.getSlowCallRate(),
            "numberOfBufferedCalls", metrics.getNumberOfBufferedCalls(),
            "numberOfFailedCalls", metrics.getNumberOfFailedCalls(),
            "numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls()
        );
    }
}
