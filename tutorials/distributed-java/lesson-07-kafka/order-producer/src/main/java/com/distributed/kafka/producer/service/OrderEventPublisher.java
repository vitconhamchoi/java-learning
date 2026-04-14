package com.distributed.kafka.producer.service;

import com.distributed.kafka.producer.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OrderEventPublisher - Service chịu trách nhiệm publish OrderEvents đến Kafka.
 *
 * Cung cấp 3 kiểu publish:
 * 1. Fire-and-forget: Gửi không chờ result (không khuyến khích trong production)
 * 2. Callback-based: Async với success/failure callback
 * 3. Batch: Gửi nhiều events cùng lúc
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    @Value("${kafka.topics.orders-created:orders.created}")
    private String ordersCreatedTopic;

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish order event đơn giản - không xử lý callback.
     *
     * Kafka message key = orderId đảm bảo:
     * - Tất cả events của cùng 1 order đi vào cùng 1 partition
     * - Ordering được đảm bảo trong 1 partition
     * - Cùng key luôn đến cùng consumer (trong cùng group)
     *
     * @param event OrderEvent cần publish
     */
    public void publishOrderCreated(OrderEvent event) {
        log.info("Publishing order event: orderId={}, amount={}", 
                event.orderId(), event.amount());

        // Gửi với key=orderId để đảm bảo ordering và partition consistency
        kafkaTemplate.send(ordersCreatedTopic, event.orderId(), event);

        log.debug("Order event sent to topic: {}", ordersCreatedTopic);
    }

    /**
     * Publish order event với callback để xử lý success/failure.
     *
     * CompletableFuture<SendResult> cho phép:
     * - Xử lý kết quả bất đồng bộ
     * - Log offset thực tế message được ghi vào
     * - Retry hoặc xử lý lỗi khi fail
     *
     * @param event OrderEvent cần publish
     * @return CompletableFuture để caller có thể chờ hoặc handle async
     */
    public CompletableFuture<SendResult<String, OrderEvent>> publishOrderCreatedWithCallback(
            OrderEvent event) {

        log.info("Publishing order event with callback: orderId={}", event.orderId());

        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(ordersCreatedTopic, event.orderId(), event);

        // Đăng ký callbacks
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                // Xử lý lỗi: log, alert, hoặc retry
                log.error("Failed to publish order event: orderId={}, error={}",
                        event.orderId(), throwable.getMessage(), throwable);
                // Trong production: gửi alert, lưu vào outbox table để retry
            } else {
                // Log thông tin ghi thành công
                var metadata = result.getRecordMetadata();
                log.info("Order event published successfully: " +
                        "orderId={}, topic={}, partition={}, offset={}",
                        event.orderId(),
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset());
            }
        });

        return future;
    }

    /**
     * Publish batch of order events.
     *
     * Gửi tất cả events rồi gọi flush() để đảm bảo tất cả đã được gửi
     * trước khi method trả về.
     *
     * flush() block cho đến khi tất cả buffered records được gửi đến Kafka.
     *
     * @param events Danh sách OrderEvents cần publish
     */
    public void publishBatch(List<OrderEvent> events) {
        log.info("Publishing batch of {} order events", events.size());

        // Gửi tất cả events (chúng được buffer trong producer)
        for (OrderEvent event : events) {
            kafkaTemplate.send(ordersCreatedTopic, event.orderId(), event);
        }

        // flush() đảm bảo tất cả buffered records đã được gửi đến Kafka
        // Sau flush(), tất cả records đã ở trên broker (hoặc đã fail)
        kafkaTemplate.flush();

        log.info("Batch of {} events flushed to Kafka", events.size());
    }
}
