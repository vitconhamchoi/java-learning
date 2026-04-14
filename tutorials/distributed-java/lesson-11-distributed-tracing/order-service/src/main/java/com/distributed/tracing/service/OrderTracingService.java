package com.distributed.tracing.service;

import com.distributed.tracing.config.PaymentClient;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class OrderTracingService {

    private static final Logger log = LoggerFactory.getLogger(OrderTracingService.class);
    private final PaymentClient paymentClient;
    private final Tracer tracer;

    public OrderTracingService(PaymentClient paymentClient, Tracer tracer) {
        this.paymentClient = paymentClient;
        this.tracer = tracer;
    }

    @NewSpan("create-order")
    public Map<String, Object> createOrder(@SpanTag("product.id") String productId, double amount) {
        String orderId = UUID.randomUUID().toString();
        log.info("Creating order {} for product {}", orderId, productId);

        // Add custom span attributes
        var span = tracer.currentSpan();
        if (span != null) {
            span.tag("order.id", orderId);
            span.tag("order.amount", String.valueOf(amount));
        }

        var validation = validateOrder(productId, amount);
        log.info("Order {} validation result: {}", orderId, validation);

        var payment = processPayment(orderId, amount);
        log.info("Payment for order {}: {}", orderId, payment.status());

        return Map.of(
            "orderId", orderId,
            "productId", productId,
            "amount", amount,
            "paymentStatus", payment.status(),
            "paymentId", payment.paymentId(),
            "traceId", tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "N/A"
        );
    }

    @NewSpan("validate-order")
    private String validateOrder(@SpanTag("product.id") String productId, double amount) {
        log.info("Validating order for product: {}", productId);
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        return "VALID";
    }

    @NewSpan("process-payment")
    private PaymentClient.PaymentResponse processPayment(String orderId, double amount) {
        log.info("Processing payment for order: {}", orderId);
        return paymentClient.processPayment(new PaymentClient.PaymentRequest(orderId, amount, "USD"));
    }
}
