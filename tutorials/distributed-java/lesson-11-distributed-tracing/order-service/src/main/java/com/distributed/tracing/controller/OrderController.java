package com.distributed.tracing.controller;

import com.distributed.tracing.service.OrderTracingService;
import io.micrometer.tracing.Tracer;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderTracingService orderService;
    private final Tracer tracer;

    public OrderController(OrderTracingService orderService, Tracer tracer) {
        this.orderService = orderService;
        this.tracer = tracer;
    }

    @PostMapping
    public Map<String, Object> createOrder(@RequestParam String productId, @RequestParam double amount) {
        return orderService.createOrder(productId, amount);
    }

    @GetMapping("/trace-info")
    public Map<String, Object> getTraceInfo() {
        var span = tracer.currentSpan();
        if (span != null) {
            return Map.of(
                "traceId", span.context().traceId(),
                "spanId", span.context().spanId()
            );
        }
        return Map.of("message", "No active trace");
    }
}
