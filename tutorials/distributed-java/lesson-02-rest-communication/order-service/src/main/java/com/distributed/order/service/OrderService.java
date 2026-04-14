package com.distributed.order.service;

import com.distributed.order.client.InventoryClient;
import com.distributed.order.client.InventoryFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Order Service - business logic xử lý đơn hàng.
 *
 * Demo hai cách gọi Inventory Service:
 * 1. Synchronous (Feign) - blocking, đơn giản, dùng trong traditional Spring MVC
 * 2. Reactive (WebClient) - non-blocking, dùng trong reactive pipeline
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // WebClient-based client (reactive, non-blocking)
    private final InventoryClient inventoryClient;

    // Feign declarative client (blocking, synchronous)
    private final InventoryFeignClient inventoryFeignClient;

    // In-memory order store (thực tế dùng database)
    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    public OrderService(InventoryClient inventoryClient,
                        InventoryFeignClient inventoryFeignClient) {
        this.inventoryClient = inventoryClient;
        this.inventoryFeignClient = inventoryFeignClient;
    }

    /**
     * Tạo order theo cách synchronous sử dụng Feign client.
     *
     * Approach: Synchronous/Blocking
     * - Đơn giản, dễ hiểu
     * - Mỗi request chiếm 1 thread trong suốt thời gian xử lý
     * - Phù hợp với traditional Spring MVC
     *
     * Flow: checkStock → reserveStock → saveOrder
     */
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for product={}, qty={}, customer={}",
                request.productId(), request.quantity(), request.customerId());

        // Bước 1: Kiểm tra stock bằng Feign (synchronous)
        InventoryFeignClient.StockResponse stockInfo =
                inventoryFeignClient.getStock(request.productId(), request.quantity());

        if (!stockInfo.available() || stockInfo.currentStock() < request.quantity()) {
            throw new InsufficientStockException(
                    String.format("Insufficient stock for %s: available=%d, requested=%d",
                            request.productId(), stockInfo.currentStock(), request.quantity()));
        }

        // Bước 2: Reserve stock bằng Feign (synchronous)
        InventoryFeignClient.ReservationRequest reserveReq =
                new InventoryFeignClient.ReservationRequest(
                        UUID.randomUUID().toString(), // orderId tạm thời
                        request.quantity()
                );
        InventoryFeignClient.ReservationResponse reservation =
                inventoryFeignClient.reserveStock(request.productId(), reserveReq);

        if (!"RESERVED".equals(reservation.status())) {
            throw new RuntimeException("Failed to reserve stock: " + reservation.message());
        }

        // Bước 3: Tạo và lưu order
        Order order = new Order(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.productId(),
                request.quantity(),
                "CONFIRMED",
                reservation.reservationId(),
                Instant.now().toString()
        );

        orderStore.put(order.orderId(), order);
        log.info("Order created successfully: {}", order.orderId());
        return order;
    }

    /**
     * Tạo order theo cách reactive sử dụng WebClient.
     *
     * Approach: Reactive/Non-blocking
     * - Không block thread khi chờ I/O
     * - Throughput cao hơn, ít thread hơn
     * - Phức tạp hơn (reactive streams)
     * - Dùng flatMap để chain async operations
     *
     * Flow: checkStock → flatMap(reserveStock) → flatMap(saveOrder)
     * Tất cả đều non-blocking!
     */
    public Mono<Order> createOrderReactive(CreateOrderRequest request) {
        log.info("Creating order reactively for product={}, qty={}, customer={}",
                request.productId(), request.quantity(), request.customerId());

        // Bước 1: Non-blocking stock check với WebClient
        return inventoryClient.checkStock(request.productId(), request.quantity())
                .flatMap(hasStock -> {
                    if (!hasStock) {
                        // Không đủ stock → error (propagate upstream)
                        return Mono.error(new InsufficientStockException(
                                "No stock available for product: " + request.productId()));
                    }
                    // Bước 2: Non-blocking stock reservation
                    return inventoryClient.reserveStock(request.productId(), request.quantity());
                })
                .flatMap(reservation -> {
                    // Bước 3: Tạo order (thực tế sẽ là async DB call)
                    Order order = new Order(
                            UUID.randomUUID().toString(),
                            request.customerId(),
                            request.productId(),
                            request.quantity(),
                            "CONFIRMED",
                            reservation.reservationId(),
                            Instant.now().toString()
                    );
                    orderStore.put(order.orderId(), order);
                    log.info("Reactive order created: {}", order.orderId());
                    return Mono.just(order);
                })
                .doOnError(ex -> log.error("Failed to create reactive order: {}", ex.getMessage()));
    }

    public Order getOrder(String orderId) {
        Order order = orderStore.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    // ========== Inner Records and Exceptions ==========

    /**
     * Domain model cho Order.
     *
     * @param orderId       ID unique của order
     * @param customerId    ID khách hàng
     * @param productId     ID sản phẩm
     * @param quantity      Số lượng
     * @param status        PENDING | CONFIRMED | CANCELLED
     * @param reservationId ID của stock reservation
     * @param createdAt     Thời gian tạo (ISO-8601)
     */
    public record Order(
            String orderId,
            String customerId,
            String productId,
            int quantity,
            String status,
            String reservationId,
            String createdAt
    ) {}

    /**
     * Request DTO để tạo order.
     *
     * @param productId  Sản phẩm muốn mua
     * @param quantity   Số lượng
     * @param customerId ID khách hàng
     */
    public record CreateOrderRequest(String productId, int quantity, String customerId) {}

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }
}
