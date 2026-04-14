package com.distributed.k8s.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Order {
    private String orderId;
    private String productId;
    private int quantity;
    private double amount;
    private String status;
    private String processedBy;
    private LocalDateTime createdAt;

    public Order(String productId, int quantity, double amount, String replicaId) {
        this.orderId = UUID.randomUUID().toString();
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = "CREATED";
        this.processedBy = replicaId;
        this.createdAt = LocalDateTime.now();
    }

    public String getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProcessedBy() { return processedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
