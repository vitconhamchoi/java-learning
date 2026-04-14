package com.distributed.kafka.producer.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderEvent - Biểu diễn một sự kiện đặt hàng.
 * Sử dụng Java record (Java 16+) để immutable, concise data carrier.
 *
 * @param orderId    ID duy nhất của đơn hàng (dùng làm Kafka message key để routing)
 * @param customerId ID của khách hàng đặt hàng
 * @param amount     Tổng giá trị đơn hàng
 * @param status     Trạng thái: CREATED, PROCESSING, COMPLETED, CANCELLED
 * @param timestamp  Thời điểm tạo event
 */
public record OrderEvent(
        String orderId,
        String customerId,
        BigDecimal amount,
        String status,
        Instant timestamp
) {
    // Compact constructor để validate dữ liệu
    public OrderEvent {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId không được null hoặc rỗng");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount phải >= 0");
        }
        // Mặc định timestamp là hiện tại nếu null
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
