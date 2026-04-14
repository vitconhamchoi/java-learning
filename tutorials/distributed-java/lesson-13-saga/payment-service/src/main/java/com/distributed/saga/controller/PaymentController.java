package com.distributed.saga.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final Map<String, String> payments = new ConcurrentHashMap<>();
    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile boolean simulateFailure = false;

    @PostMapping("/process")
    public Map<String, Object> processPayment(@RequestBody Map<String, Object> request) {
        String orderId = (String) request.get("orderId");
        double amount = ((Number) request.get("amount")).doubleValue();
        String sagaId = (String) request.get("sagaId");

        if (simulateFailure) {
            failCount.incrementAndGet();
            throw new RuntimeException("Payment gateway unavailable (simulated failure #" + failCount.get() + ")");
        }

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        payments.put(sagaId, paymentId);
        log.info("Payment {} processed for order {} (saga: {}), amount: {}", paymentId, orderId, sagaId, amount);

        return Map.of("paymentId", paymentId, "status", "SUCCESS", "orderId", orderId, "amount", amount);
    }

    @PostMapping("/refund")
    public Map<String, Object> refundPayment(@RequestBody Map<String, Object> request) {
        String sagaId = (String) request.get("sagaId");
        String paymentId = payments.remove(sagaId);
        log.info("Refund processed for saga {}, payment: {}", sagaId, paymentId);
        return Map.of("status", "REFUNDED", "paymentId", paymentId != null ? paymentId : "NOT_FOUND");
    }

    @PostMapping("/simulate/failure")
    public Map<String, Object> toggleFailure(@RequestParam boolean enabled) {
        this.simulateFailure = enabled;
        return Map.of("simulateFailure", enabled);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of("totalPayments", payments.size(), "failCount", failCount.get(), "simulateFailure", simulateFailure);
    }
}
