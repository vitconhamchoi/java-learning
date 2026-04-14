package com.distributed.cb.model;

import java.time.LocalDateTime;

public class OrderResponse {
    private String orderId;
    private String status;
    private String message;
    private LocalDateTime timestamp;

    public OrderResponse(String orderId, String status, String message) {
        this.orderId = orderId;
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
