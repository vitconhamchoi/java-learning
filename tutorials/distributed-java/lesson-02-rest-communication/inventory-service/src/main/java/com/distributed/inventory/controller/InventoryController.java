package com.distributed.inventory.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inventory Controller - quản lý tồn kho sản phẩm.
 *
 * Thread-safe với:
 * - ConcurrentHashMap: concurrent reads, synchronized writes per key
 * - AtomicInteger: lock-free atomic operations cho stock counts
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    // Thread-safe stock storage: productId → stock count
    // ConcurrentHashMap đảm bảo thread-safe cho concurrent reads/writes
    private final Map<String, AtomicInteger> stockMap = new ConcurrentHashMap<>();

    // Product metadata
    private final Map<String, String> productNames = new ConcurrentHashMap<>();

    // Khởi tạo với một số sản phẩm mẫu
    public InventoryController() {
        stockMap.put("P001", new AtomicInteger(100));
        stockMap.put("P002", new AtomicInteger(50));
        stockMap.put("P003", new AtomicInteger(0));   // Out of stock
        stockMap.put("P004", new AtomicInteger(200));

        productNames.put("P001", "Laptop Dell XPS 13");
        productNames.put("P002", "iPhone 15 Pro");
        productNames.put("P003", "Sony WH-1000XM5");
        productNames.put("P004", "Samsung 4K Monitor");
    }

    /**
     * GET /inventory/{productId}/stock - lấy thông tin stock của sản phẩm.
     *
     * @param productId ID sản phẩm
     * @param quantity  (optional) Số lượng cần kiểm tra
     * @return StockInfo với current stock và availability
     */
    @GetMapping("/{productId}/stock")
    public ResponseEntity<StockInfo> getStock(
            @PathVariable String productId,
            @RequestParam(required = false, defaultValue = "1") int quantity) {

        AtomicInteger stock = stockMap.get(productId);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }

        int currentStock = stock.get();
        StockInfo info = new StockInfo(
                productId,
                productNames.getOrDefault(productId, "Unknown Product"),
                currentStock,
                currentStock >= quantity,
                quantity,
                "WAREHOUSE-01"
        );

        log.info("Stock check: productId={}, current={}, requested={}, available={}",
                productId, currentStock, quantity, info.available());
        return ResponseEntity.ok(info);
    }

    /**
     * POST /inventory/{productId}/reserve - reserve stock cho một order.
     *
     * Thread-safe: Sử dụng compareAndSet để atomic decrement,
     * đảm bảo không oversell trong concurrent requests.
     *
     * @param productId ID sản phẩm
     * @param request   ReservationRequest với orderId và quantity
     * @return ReservationResult
     */
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<ReservationResult> reserveStock(
            @PathVariable String productId,
            @RequestBody ReservationRequest request) {

        AtomicInteger stock = stockMap.get(productId);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }

        // Atomic compare-and-set loop để thread-safe decrement
        // Tránh race condition: nhiều threads cùng decrement cùng lúc
        boolean reserved = false;
        int attempts = 0;
        int currentStock;

        do {
            currentStock = stock.get();
            if (currentStock < request.quantity()) {
                // Không đủ stock
                log.warn("Insufficient stock: productId={}, current={}, requested={}",
                        productId, currentStock, request.quantity());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new ReservationResult(
                                null,
                                productId,
                                request.quantity(),
                                "FAILED",
                                String.format("Insufficient stock: available=%d, requested=%d",
                                        currentStock, request.quantity()),
                                null
                        ));
            }
            // Atomic: chỉ decrement nếu giá trị vẫn là currentStock
            // Nếu thread khác đã thay đổi → retry
            reserved = stock.compareAndSet(currentStock, currentStock - request.quantity());
            attempts++;
        } while (!reserved && attempts < 10);

        if (!reserved) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ReservationResult(null, productId, request.quantity(),
                            "FAILED", "Concurrent modification, please retry", null));
        }

        String reservationId = UUID.randomUUID().toString();
        String expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).toString();

        log.info("Stock reserved: productId={}, quantity={}, reservationId={}",
                productId, request.quantity(), reservationId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReservationResult(
                        reservationId,
                        productId,
                        request.quantity(),
                        "RESERVED",
                        "Stock reserved successfully",
                        expiresAt
                ));
    }

    // ========== Records ==========

    public record StockInfo(
            String productId,
            String productName,
            int currentStock,
            boolean available,
            int requested,
            String warehouseId
    ) {}

    public record ReservationRequest(String orderId, int quantity) {}

    public record ReservationResult(
            String reservationId,
            String productId,
            int quantity,
            String status,
            String message,
            String expiresAt
    ) {}
}
