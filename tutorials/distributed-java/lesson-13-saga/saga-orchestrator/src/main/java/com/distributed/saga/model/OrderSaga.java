package com.distributed.saga.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderSaga {
    private String sagaId;
    private String orderId;
    private String productId;
    private int quantity;
    private double amount;
    private SagaState state;
    private List<String> steps;
    private List<String> compensations;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;

    public enum SagaState {
        STARTED, INVENTORY_RESERVED, PAYMENT_PROCESSED, COMPLETED, FAILED, COMPENSATING, COMPENSATED
    }

    public OrderSaga(String sagaId, String orderId, String productId, int quantity, double amount) {
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.state = SagaState.STARTED;
        this.steps = new ArrayList<>();
        this.compensations = new ArrayList<>();
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addStep(String step) { steps.add(step); updatedAt = LocalDateTime.now(); }
    public void addCompensation(String compensation) { compensations.add(compensation); }

    public String getSagaId() { return sagaId; }
    public String getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getAmount() { return amount; }
    public SagaState getState() { return state; }
    public void setState(SagaState state) { this.state = state; this.updatedAt = LocalDateTime.now(); }
    public List<String> getSteps() { return steps; }
    public List<String> getCompensations() { return compensations; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
