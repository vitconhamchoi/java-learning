package com.distributed.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Manual WebClient-based HTTP client cho Inventory Service.
 *
 * WebClient là non-blocking, reactive HTTP client của Spring WebFlux.
 * Trả về Mono<T> (0 hoặc 1 phần tử) hoặc Flux<T> (0 đến N phần tử).
 */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final WebClient webClient;

    public InventoryClient(WebClient inventoryWebClient) {
        this.webClient = inventoryWebClient;
    }

    /**
     * Kiểm tra xem có đủ stock cho product không.
     *
     * @param productId ID của sản phẩm
     * @param quantity  Số lượng cần kiểm tra
     * @return Mono<Boolean> true nếu đủ stock, false nếu không
     */
    public Mono<Boolean> checkStock(String productId, int quantity) {
        return webClient.get()
                .uri("/inventory/{productId}/stock?quantity={qty}", productId, quantity)
                .retrieve()
                // Xử lý 4xx errors
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new IllegalArgumentException("Client error: " + body))))
                // Xử lý 5xx errors
                .onStatus(status -> status.is5xxServerError(), response ->
                        Mono.error(new RuntimeException("Inventory service error")))
                .bodyToMono(StockCheckResponse.class)
                .map(StockCheckResponse::available)
                // Timeout: 5 giây cho stock check (cần nhanh cho user experience)
                .timeout(Duration.ofSeconds(5))
                // Fallback khi timeout hoặc error: assume không có stock (conservative)
                .onErrorResume(ex -> {
                    log.warn("Stock check failed for product {}: {}. Falling back to false.",
                            productId, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Reserve stock cho một order.
     *
     * @param productId ID của sản phẩm
     * @param quantity  Số lượng cần reserve
     * @return Mono<StockReservation> kết quả reservation
     */
    public Mono<StockReservation> reserveStock(String productId, int quantity) {
        ReserveRequest request = new ReserveRequest(productId, quantity);

        return webClient.post()
                .uri("/inventory/{productId}/reserve", productId)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new InsufficientStockException("Cannot reserve: " + body))))
                .onStatus(status -> status.is5xxServerError(), response ->
                        Mono.error(new RuntimeException("Inventory service unavailable")))
                .bodyToMono(StockReservation.class)
                // Reservation có thể cần nhiều thời gian hơn stock check
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(InsufficientStockException.class, ex -> Mono.error(ex)) // Re-throw business exceptions
                .onErrorResume(ex -> {
                    log.error("Stock reservation failed for product {}: {}", productId, ex.getMessage());
                    return Mono.error(new RuntimeException("Failed to reserve stock", ex));
                });
    }

    // ========== Inner Records ==========

    /** Response từ stock check endpoint */
    public record StockCheckResponse(boolean available, int currentStock, int requested) {}

    /** Request body để reserve stock */
    public record ReserveRequest(String productId, int quantity) {}

    /**
     * Kết quả của stock reservation.
     *
     * @param reservationId  ID unique của reservation
     * @param productId      Sản phẩm đã reserve
     * @param quantity       Số lượng đã reserve
     * @param status         Trạng thái: RESERVED, FAILED
     * @param expiresAt      Thời gian reservation hết hạn (ISO-8601)
     */
    public record StockReservation(
            String reservationId,
            String productId,
            int quantity,
            String status,
            String expiresAt
    ) {}

    /** Custom exception cho business logic error */
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }
}
