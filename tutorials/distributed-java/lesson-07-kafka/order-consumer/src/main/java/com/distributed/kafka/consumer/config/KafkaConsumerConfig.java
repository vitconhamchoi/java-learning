package com.distributed.kafka.consumer.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration
 *
 * Cấu hình Consumer với:
 * - Manual acknowledge để kiểm soát khi nào commit offset
 * - Earliest offset reset để đọc từ đầu khi consumer mới join
 * - Auto-commit tắt để tránh mất messages khi processing fail
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-processor}")
    private String groupId;

    /**
     * ConsumerFactory tạo ra các Kafka Consumer instances.
     *
     * Cấu hình quan trọng:
     * - enable.auto.commit=false: Tắt auto commit offset
     *   Thay vào đó commit thủ công sau khi xử lý thành công
     *   Đảm bảo at-least-once delivery semantics
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // ─── CONNECTION ───
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ─── CONSUMER GROUP ───
        // Group ID xác định consumer group
        // Tất cả consumers cùng group ID chia sẻ partitions của topic
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // ─── OFFSET MANAGEMENT ───
        // auto.offset.reset: Hành vi khi không có committed offset
        // "earliest": Đọc từ đầu topic (dùng khi muốn process tất cả messages)
        // "latest": Chỉ đọc messages mới (dùng khi chỉ cần real-time)
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Tắt auto commit - consumer tự quyết định khi nào commit
        // Kết hợp với AckMode.MANUAL_IMMEDIATE trong container factory
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // ─── PERFORMANCE ───
        // Số records tối đa trả về mỗi lần poll()
        // Giảm nếu processing mỗi record mất nhiều thời gian
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // Thời gian tối đa giữa 2 lần poll() trước khi bị coi là dead
        // Tăng nếu processing batch records mất > 5 phút
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 phút

        // Session timeout: Nếu không gửi heartbeat trong thời gian này → consumer bị kick
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 30 giây

        // ─── DESERIALIZERS ───
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Cho phép deserialize tất cả packages (trong production giới hạn lại)
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * ConcurrentKafkaListenerContainerFactory tạo listener containers.
     *
     * Mỗi @KafkaListener sẽ được tạo trong container factory này.
     * Concurrency = số threads xử lý song song (nên = số partitions)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
            kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Số threads concurrent (nên = số partitions của topic)
        // 3 partitions → concurrency=3 → 3 consumer threads
        factory.setConcurrency(3);

        // MANUAL_IMMEDIATE: Commit offset ngay khi gọi ack.acknowledge()
        // MANUAL: Batch nhiều acks rồi commit cùng lúc (throughput cao hơn)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Bật observation cho distributed tracing
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }
}
