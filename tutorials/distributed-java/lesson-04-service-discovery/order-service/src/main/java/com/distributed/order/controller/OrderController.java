package com.distributed.order.controller;

import com.distributed.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/demo/discovery/{productId}")
    public Map<String, Object> getProductViaDiscovery(@PathVariable String productId) {
        return orderService.getProductFromDiscovery(productId);
    }

    @GetMapping("/demo/loadbalanced/{productId}")
    public Map<String, Object> getProductLoadBalanced(@PathVariable String productId) {
        return orderService.getProductLoadBalanced(productId);
    }

    @GetMapping("/services")
    public List<String> getAllServices() {
        return orderService.getAllRegisteredServices();
    }

    @GetMapping("/services/{serviceName}/instances")
    public List<Map<String, Object>> getServiceInstances(@PathVariable String serviceName) {
        return orderService.getServiceInstances(serviceName);
    }
}
