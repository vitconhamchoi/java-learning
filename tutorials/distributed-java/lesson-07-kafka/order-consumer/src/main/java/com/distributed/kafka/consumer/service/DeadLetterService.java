package com.distributed.kafka.consumer.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * DeadLetterService - Gửi failed messages đến Dead Letter Queue.
 *
 * DLQ Pattern:
 * - Khi consumer fail sau tất cả retries, gửi message vào DLQ
 * - Thêm metadata headers để biết tại sao message fail
 * - Team có thể inspect DLQ, fix bug, rồi replay messages
 *
 * Headers được thêm vào DLQ message:
 * - X-Original-Topic: Topic gốc của message
 * - X-Original-Partition: Partition gốc
 * - X-Original-Offset: Offset gốc
 * - X-Exception-Type: Class name của exception
 * - X-Exception-Message: Message của exception
 * - X-Failed-Timestamp: Thời điểm fail
 * - X-Consumer-Group: Consumer group đã fail
 */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    @Value("${kafka.topics.orders-dlq:orders.dlq}")
    private String dlqTopic;

    @Value("${spring.kafka.consumer.group-id:order-processor}")
    private String consumerGroup;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeadLetterService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Gửi failed message đến DLQ với đầy đủ metadata.
     *
     * @param originalRecord  Message gốc bị fail
     * @param exception       Exception gây ra fail
     */
    public void sendToDlq(ConsumerRecord<String, Object> originalRecord, Exception exception) {
        log.info("Gửi message đến DLQ: key={}, originalTopic={}, error={}",
                originalRecord.key(), originalRecord.topic(), exception.getMessage());

        // Tạo ProducerRecord cho DLQ với key giống message gốc
        ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                dlqTopic,
                originalRecord.key(),
                originalRecord.value()  // Giữ nguyên payload gốc để có thể replay
        );

        // Thêm metadata headers để trace tại sao message fail
        addDlqHeaders(dlqRecord, originalRecord, exception);

        // Gửi đến DLQ
        kafkaTemplate.send(dlqRecord)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("CRITICAL: Không thể gửi vào DLQ! Message bị mất: key={}, error={}",
                                originalRecord.key(), throwable.getMessage());
                        // Trong production: alert ngay lập tức, đây là critical failure
                    } else {
                        log.info("Message đã gửi vào DLQ thành công: " +
                                "dlqPartition={}, dlqOffset={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Thêm metadata headers vào DLQ message.
     * Các headers này giúp team debug tại sao message fail.
     */
    private void addDlqHeaders(ProducerRecord<String, Object> dlqRecord,
                                ConsumerRecord<String, Object> originalRecord,
                                Exception exception) {

        // Thông tin về message gốc
        addHeader(dlqRecord, "X-Original-Topic", originalRecord.topic());
        addHeader(dlqRecord, "X-Original-Partition", String.valueOf(originalRecord.partition()));
        addHeader(dlqRecord, "X-Original-Offset", String.valueOf(originalRecord.offset()));

        // Thông tin về exception
        addHeader(dlqRecord, "X-Exception-Type", exception.getClass().getName());
        addHeader(dlqRecord, "X-Exception-Message",
                exception.getMessage() != null ? exception.getMessage() : "No message");

        // Thông tin về thời gian và consumer
        addHeader(dlqRecord, "X-Failed-Timestamp", Instant.now().toString());
        addHeader(dlqRecord, "X-Consumer-Group", consumerGroup);

        // Root cause nếu có
        if (exception.getCause() != null) {
            addHeader(dlqRecord, "X-Root-Cause", exception.getCause().getClass().getName());
        }

        // Copy headers gốc từ original message (preserve tracing headers)
        originalRecord.headers().forEach(header -> {
            // Không overwrite các DLQ headers đã set
            if (!header.key().startsWith("X-Original-") &&
                    !header.key().startsWith("X-Exception-") &&
                    !header.key().startsWith("X-Failed-")) {
                dlqRecord.headers().add(header);
            }
        });
    }

    private void addHeader(ProducerRecord<String, Object> record, String key, String value) {
        record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }
}
