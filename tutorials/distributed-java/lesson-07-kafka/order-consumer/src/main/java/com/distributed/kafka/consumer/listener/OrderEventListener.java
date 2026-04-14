package com.distributed.kafka.consumer.listener;

import com.distributed.kafka.consumer.service.DeadLetterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OrderEventListener - Consumer nhận và xử lý order events từ Kafka.
 *
 * Pattern: At-least-once delivery với Dead Letter Queue
 *
 * Nguyên tắc quan trọng:
 * 1. Luôn gọi ack.acknowledge() - dù thành công hay thất bại
 *    Không acknowledge → consumer bị block, partition không tiến
 * 2. Khi fail: Gửi message đến DLQ TRƯỚC khi acknowledge
 * 3. Business logic phải idempotent để xử lý duplicate messages an toàn
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(DeadLetterService deadLetterService, ObjectMapper objectMapper) {
        this.deadLetterService = deadLetterService;
        this.objectMapper = objectMapper;
    }

    /**
     * Lắng nghe orders.created topic.
     *
     * @param record     ConsumerRecord chứa đầy đủ metadata (partition, offset, key, value)
     * @param ack        Acknowledgment object để commit offset thủ công
     * @param partition  Partition số bao nhiêu (từ header)
     * @param offset     Offset của message trong partition
     */
    @KafkaListener(
            topics = "${kafka.topics.orders-created:orders.created}",
            groupId = "${spring.kafka.consumer.group-id:order-processor}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(
            ConsumerRecord<String, Object> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Nhận order event: key={}, partition={}, offset={}, topic={}",
                record.key(), partition, offset, record.topic());

        try {
            // 1. Deserialize message value thành Map (hoặc OrderEvent POJO)
            Object value = record.value();
            String orderJson = objectMapper.writeValueAsString(value);
            Map<?, ?> orderData = objectMapper.readValue(orderJson, Map.class);

            String orderId = (String) orderData.get("orderId");
            log.info("Xử lý order: orderId={}", orderId);

            // 2. Xử lý business logic
            processOrder(orderData);

            // 3. Commit offset SAU KHI xử lý thành công
            // Đảm bảo at-least-once: nếu app crash trước khi commit,
            // message sẽ được nhận lại (duplicate nhưng không mất)
            ack.acknowledge();

            log.info("Order xử lý thành công, offset committed: {}/{}", partition, offset);

        } catch (Exception e) {
            log.error("Lỗi xử lý order event: partition={}, offset={}, error={}",
                    partition, offset, e.getMessage(), e);

            // 4. Khi fail: Gửi message đến DLQ để không mất data
            try {
                deadLetterService.sendToDlq(record, e);
                log.info("Message đã được gửi đến DLQ");
            } catch (Exception dlqException) {
                log.error("Không thể gửi đến DLQ: {}", dlqException.getMessage());
            }

            // 5. PHẢI acknowledge dù fail để tránh infinite loop
            // Message đã được lưu vào DLQ nên an toàn để acknowledge
            ack.acknowledge();
        }
    }

    /**
     * Monitor Dead Letter Queue.
     * Lắng nghe DLQ để alert khi có messages failed.
     *
     * Trong production: Tích hợp với PagerDuty, Slack, hoặc email alert
     */
    @KafkaListener(
            topics = "${kafka.topics.orders-dlq:orders.dlq}",
            groupId = "dlq-monitor"
    )
    public void monitorDlq(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        log.error("⚠️ DLQ ALERT - Message failed processing: " +
                "key={}, originalTopic={}, errorType={}",
                record.key(),
                record.headers().lastHeader("X-Original-Topic") != null
                        ? new String(record.headers().lastHeader("X-Original-Topic").value())
                        : "unknown",
                record.headers().lastHeader("X-Exception-Type") != null
                        ? new String(record.headers().lastHeader("X-Exception-Type").value())
                        : "unknown");

        // Trong production: gửi alert, tạo incident ticket
        // alertService.sendDlqAlert(record);

        ack.acknowledge();
    }

    /**
     * Xử lý business logic cho order.
     * Trong thực tế: validate, lưu DB, gọi services khác...
     */
    private void processOrder(Map<?, ?> orderData) throws Exception {
        // Simulate processing time
        Thread.sleep(50);

        // Simulate occasional failures (10% of time) for demo
        if (Math.random() < 0.1) {
            throw new RuntimeException("Simulated processing failure for demo purposes");
        }

        log.info("Order processed: orderId={}, status={}",
                orderData.get("orderId"), orderData.get("status"));
    }
}
