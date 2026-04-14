package com.distributed.kafka.producer.controller;

import com.distributed.kafka.producer.model.OrderEvent;
import com.distributed.kafka.producer.service.OrderEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Order Controller - REST API để tạo và publish order events.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderEventPublisher publisher;

    public OrderController(OrderEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * POST /orders - Tạo order mới và publish event đến Kafka.
     * Trả về 202 Accepted (không phải 201) vì xử lý bất đồng bộ.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody CreateOrderRequest request) {

        // Tạo OrderEvent với unique ID
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.amount(),
                "CREATED",
                Instant.now()
        );

        // Publish đến Kafka với callback
        publisher.publishOrderCreatedWithCallback(event);

        // Trả về 202 Accepted - request đã nhận, xử lý bất đồng bộ
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "orderId", event.orderId(),
                "status", "ACCEPTED",
                "message", "Order đã được nhận và đang xử lý",
                "timestamp", event.timestamp().toString()
        ));
    }

    // Request body record
    record CreateOrderRequest(String customerId, BigDecimal amount) {}
}
