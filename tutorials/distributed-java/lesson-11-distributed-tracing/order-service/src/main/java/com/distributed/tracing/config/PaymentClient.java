package com.distributed.tracing.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${payment.service.url}")
public interface PaymentClient {

    @PostMapping("/api/payments/process")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    record PaymentRequest(String orderId, double amount, String currency) {}
    record PaymentResponse(String paymentId, String status, String transactionId) {}
}
