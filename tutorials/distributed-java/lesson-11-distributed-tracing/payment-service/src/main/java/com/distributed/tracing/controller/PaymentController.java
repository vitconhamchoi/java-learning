package com.distributed.tracing.controller;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final Tracer tracer;

    public PaymentController(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostMapping("/process")
    @NewSpan("payment-process")
    public Map<String, Object> processPayment(@RequestBody Map<String, Object> request) {
        String orderId = (String) request.get("orderId");
        double amount = ((Number) request.get("amount")).doubleValue();
        
        log.info("Processing payment for order: {}, amount: {}", orderId, amount);
        
        var span = tracer.currentSpan();
        if (span != null) {
            span.tag("payment.order_id", orderId);
            span.tag("payment.amount", String.valueOf(amount));
        }

        String paymentId = chargeCard(orderId, amount);
        log.info("Payment {} completed for order: {}", paymentId, orderId);

        return Map.of(
            "paymentId", paymentId,
            "status", "SUCCESS",
            "transactionId", UUID.randomUUID().toString(),
            "traceId", span != null ? span.context().traceId() : "N/A"
        );
    }

    @NewSpan("charge-card")
    private String chargeCard(@SpanTag("order.id") String orderId, double amount) {
        log.info("Charging card for order: {}, amount: {}", orderId, amount);
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
