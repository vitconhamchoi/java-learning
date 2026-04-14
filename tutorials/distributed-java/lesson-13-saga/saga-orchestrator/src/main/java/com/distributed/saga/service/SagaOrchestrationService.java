package com.distributed.saga.service;

import com.distributed.saga.model.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SagaOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrationService.class);
    private final Map<String, OrderSaga> sagas = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://localhost:8081}")
    private String inventoryUrl;

    @Value("${payment.service.url:http://localhost:8082}")
    private String paymentUrl;

    public SagaOrchestrationService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = new RestTemplate();
    }

    public OrderSaga startSaga(String productId, int quantity, double amount) {
        String sagaId = UUID.randomUUID().toString();
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderSaga saga = new OrderSaga(sagaId, orderId, productId, quantity, amount);
        sagas.put(sagaId, saga);

        log.info("Starting saga {} for order {}", sagaId, orderId);
        saga.addStep("SAGA_STARTED");

        try {
            // Step 1: Reserve inventory
            reserveInventory(saga);

            // Step 2: Process payment
            processPayment(saga);

            // Step 3: Complete
            saga.setState(OrderSaga.SagaState.COMPLETED);
            saga.addStep("SAGA_COMPLETED");
            log.info("Saga {} completed successfully", sagaId);

            // Publish success event
            kafkaTemplate.send("order-events", Map.of(
                "eventType", "ORDER_COMPLETED",
                "sagaId", sagaId,
                "orderId", orderId
            ));
        } catch (Exception e) {
            log.error("Saga {} failed: {}", sagaId, e.getMessage());
            compensate(saga, e);
        }

        return saga;
    }

    private void reserveInventory(OrderSaga saga) {
        log.info("Reserving inventory for saga {}", saga.getSagaId());
        try {
            restTemplate.postForObject(
                inventoryUrl + "/api/inventory/reserve",
                Map.of("productId", saga.getProductId(), "quantity", saga.getQuantity(), "sagaId", saga.getSagaId()),
                Map.class
            );
            saga.setState(OrderSaga.SagaState.INVENTORY_RESERVED);
            saga.addStep("INVENTORY_RESERVED");
        } catch (Exception e) {
            throw new RuntimeException("Inventory reservation failed: " + e.getMessage());
        }
    }

    private void processPayment(OrderSaga saga) {
        log.info("Processing payment for saga {}", saga.getSagaId());
        try {
            restTemplate.postForObject(
                paymentUrl + "/api/payments/process",
                Map.of("orderId", saga.getOrderId(), "amount", saga.getAmount(), "sagaId", saga.getSagaId()),
                Map.class
            );
            saga.setState(OrderSaga.SagaState.PAYMENT_PROCESSED);
            saga.addStep("PAYMENT_PROCESSED");
        } catch (Exception e) {
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    private void compensate(OrderSaga saga, Exception cause) {
        saga.setState(OrderSaga.SagaState.COMPENSATING);
        saga.addStep("COMPENSATION_STARTED: " + cause.getMessage());

        if (saga.getSteps().contains("INVENTORY_RESERVED")) {
            try {
                restTemplate.postForObject(
                    inventoryUrl + "/api/inventory/release",
                    Map.of("productId", saga.getProductId(), "quantity", saga.getQuantity(), "sagaId", saga.getSagaId()),
                    Map.class
                );
                saga.addCompensation("INVENTORY_RELEASED");
                log.info("Inventory released for saga {}", saga.getSagaId());
            } catch (Exception e) {
                log.error("Failed to release inventory for saga {}: {}", saga.getSagaId(), e.getMessage());
            }
        }

        saga.setState(OrderSaga.SagaState.FAILED);
        saga.addStep("SAGA_FAILED");

        kafkaTemplate.send("order-events", Map.of(
            "eventType", "ORDER_FAILED",
            "sagaId", saga.getSagaId(),
            "reason", cause.getMessage()
        ));
    }

    public OrderSaga getSaga(String sagaId) {
        return sagas.get(sagaId);
    }

    public Collection<OrderSaga> getAllSagas() {
        return sagas.values();
    }
}
