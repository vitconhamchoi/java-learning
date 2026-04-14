package com.distributed.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign Declarative HTTP Client cho Inventory Service.
 *
 * Feign tự động generate implementation cho interface này.
 * Đây là cách khai báo HTTP client ngắn gọn nhất - như gọi method local!
 *
 * name: logical name của service (dùng với service discovery)
 * url: URL cụ thể (override service discovery, dùng cho dev/testing)
 */
@FeignClient(name = "inventory-service", url = "${inventory.service.url:http://localhost:8081}")
public interface InventoryFeignClient {

    /**
     * Lấy thông tin stock của một product.
     * Feign sẽ generate: GET http://inventory-service/inventory/{productId}/stock
     */
    @GetMapping("/inventory/{productId}/stock")
    StockResponse getStock(@PathVariable("productId") String productId,
                           @RequestParam(value = "quantity", required = false) Integer quantity);

    /**
     * Reserve stock cho một order.
     * Feign sẽ generate: POST http://inventory-service/inventory/{productId}/reserve
     * với body là ReservationRequest
     */
    @PostMapping("/inventory/{productId}/reserve")
    ReservationResponse reserveStock(@PathVariable("productId") String productId,
                                     @RequestBody ReservationRequest request);

    // ========== Inner Records (API DTOs) ==========

    /**
     * Response từ GET /inventory/{productId}/stock.
     *
     * @param productId    ID sản phẩm
     * @param productName  Tên sản phẩm
     * @param currentStock Số lượng hiện có
     * @param available    True nếu còn hàng
     * @param warehouseId  Kho hàng
     */
    record StockResponse(
            String productId,
            String productName,
            int currentStock,
            boolean available,
            String warehouseId
    ) {}

    /**
     * Request body cho POST /inventory/{productId}/reserve.
     *
     * @param orderId   ID của order cần reserve cho
     * @param quantity  Số lượng cần reserve
     */
    record ReservationRequest(String orderId, int quantity) {}

    /**
     * Response từ POST /inventory/{productId}/reserve.
     *
     * @param reservationId  ID unique của reservation (để cancel sau nếu cần)
     * @param productId      Sản phẩm đã reserve
     * @param quantity       Số lượng đã reserve
     * @param status         RESERVED | FAILED | PARTIAL
     * @param message        Thông báo chi tiết
     */
    record ReservationResponse(
            String reservationId,
            String productId,
            int quantity,
            String status,
            String message
    ) {}
}
