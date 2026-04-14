package com.distributed.saga.controller;

import com.distributed.saga.model.OrderSaga;
import com.distributed.saga.service.SagaOrchestrationService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/sagas")
public class SagaController {

    private final SagaOrchestrationService sagaService;

    public SagaController(SagaOrchestrationService sagaService) {
        this.sagaService = sagaService;
    }

    @PostMapping("/orders")
    public OrderSaga startOrderSaga(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        double amount = ((Number) request.get("amount")).doubleValue();
        return sagaService.startSaga(productId, quantity, amount);
    }

    @GetMapping("/{sagaId}")
    public OrderSaga getSaga(@PathVariable String sagaId) {
        OrderSaga saga = sagaService.getSaga(sagaId);
        if (saga == null) throw new RuntimeException("Saga not found: " + sagaId);
        return saga;
    }

    @GetMapping
    public Collection<OrderSaga> getAllSagas() {
        return sagaService.getAllSagas();
    }
}
