package com.distributed.kafka.consumer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Admin Configuration - Tự động tạo topics khi application start.
 *
 * KafkaAdmin sử dụng AdminClient để tạo topics.
 * Topics chỉ được tạo nếu chưa tồn tại (idempotent).
 */
@Configuration
public class KafkaAdminConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * KafkaAdmin là wrapper cho Kafka AdminClient.
     * Tự động tạo topics được khai báo dưới dạng @Bean NewTopic.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bootstrap.servers", bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Topic chính chứa order events.
     *
     * 3 partitions: Cho phép tối đa 3 consumers trong cùng group xử lý song song
     * replication-factor=1: Phù hợp cho dev/test (production dùng 3)
     *
     * Trong production:
     * - partitions >= số consumers dự kiến tối đa
     * - replication-factor=3 để HA
     * - retention.ms=604800000 (7 ngày)
     */
    @Bean
    public NewTopic ordersCreatedTopic() {
        return TopicBuilder.name("orders.created")
                .partitions(3)
                .replicas(1)
                // Retention: Giữ messages trong 7 ngày
                .config("retention.ms", "604800000")
                // Cleanup policy: delete (xóa khi hết retention)
                .config("cleanup.policy", "delete")
                .build();
    }

    /**
     * Dead Letter Queue (DLQ) topic.
     *
     * Lưu các messages không xử lý được sau tất cả retries.
     * 1 partition vì thường có ít messages và cần ordering để inspect
     *
     * Retention dài hơn để team có thời gian investigate và replay
     */
    @Bean
    public NewTopic ordersDeadLetterTopic() {
        return TopicBuilder.name("orders.dlq")
                .partitions(1)
                .replicas(1)
                // DLQ giữ lâu hơn để investigate (30 ngày)
                .config("retention.ms", "2592000000")
                .build();
    }

    /**
     * Topic cho high-value orders (dùng bởi Kafka Streams)
     */
    @Bean
    public NewTopic ordersHighValueTopic() {
        return TopicBuilder.name("orders.highvalue")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic cho customer order counts (output của Kafka Streams)
     */
    @Bean
    public NewTopic ordersCustomerCountTopic() {
        return TopicBuilder.name("orders.customer-count")
                .partitions(3)
                .replicas(1)
                // Compacted topic: chỉ giữ latest value cho mỗi key
                .config("cleanup.policy", "compact")
                .build();
    }
}
