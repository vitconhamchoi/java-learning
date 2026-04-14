package com.distributed.order.controller;

import com.distributed.order.service.OrderService;
import com.distributed.order.service.OrderService.CreateOrderRequest;
import com.distributed.order.service.OrderService.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST Controller cho Order Service.
 *
 * Expose hai endpoints:
 * 1. POST /orders - Synchronous order creation (blocking, dùng Feign)
 * 2. POST /orders/reactive - Reactive order creation (non-blocking, dùng WebClient)
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Tạo order theo cách synchronous.
     *
     * Thread bị block cho đến khi toàn bộ process hoàn thành:
     * check stock → reserve → save → response
     *
     * Phù hợp với: Traditional Spring MVC, JDBC-based persistence
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            Order order = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (OrderService.InsufficientStockException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Tạo order theo cách reactive (non-blocking).
     *
     * Trả về Mono<ResponseEntity> - Spring sẽ subscribe và send response khi Mono completes.
     * Thread không bị block trong quá trình chờ Inventory Service response.
     *
     * Phù hợp với: Reactive stack, R2DBC, high concurrency scenarios
     */
    @PostMapping("/reactive")
    public Mono<ResponseEntity<Order>> createOrderReactive(@RequestBody CreateOrderRequest request) {
        return orderService.createOrderReactive(request)
                .map(order -> ResponseEntity.status(HttpStatus.CREATED).body(order))
                .onErrorResume(OrderService.InsufficientStockException.class,
                        ex -> Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<Order>build()))
                .onErrorResume(Exception.class,
                        ex -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .<Order>build()));
    }

    /**
     * Lấy thông tin order theo ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        try {
            return ResponseEntity.ok(orderService.getOrder(orderId));
        } catch (OrderService.OrderNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Health check endpoint cho demo.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "order-service");
    }
}
