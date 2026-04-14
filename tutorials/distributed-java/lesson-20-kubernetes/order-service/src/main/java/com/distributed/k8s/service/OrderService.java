package com.distributed.k8s.service;

import com.distributed.k8s.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    @Value("${app.replica-id:unknown}")
    private String replicaId;

    @Value("${app.environment:local}")
    private String environment;

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public Order createOrder(String productId, int quantity, double amount) {
        Order order = new Order(productId, quantity, amount, replicaId);
        orders.put(order.getOrderId(), order);
        return order;
    }

    public Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new RuntimeException("Order not found: " + orderId);
        return order;
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }

    public Map<String, Object> getInstanceInfo() {
        return Map.of(
            "replicaId", replicaId,
            "environment", environment,
            "hostname", getHostname(),
            "totalOrders", orders.size(),
            "javaVersion", System.getProperty("java.version"),
            "pid", ProcessHandle.current().pid()
        );
    }

    private String getHostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
