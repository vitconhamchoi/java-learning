package com.distributed.k8s.controller;

import com.distributed.k8s.model.Order;
import com.distributed.k8s.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Order createOrder(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        double amount = ((Number) request.get("amount")).doubleValue();
        return orderService.createOrder(productId, quantity, amount);
    }

    @GetMapping
    public List<Order> getOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        return orderService.getInstanceInfo();
    }
}
