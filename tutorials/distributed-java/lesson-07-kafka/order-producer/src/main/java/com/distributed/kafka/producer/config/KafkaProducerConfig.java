package com.distributed.kafka.producer.config;

import com.distributed.kafka.producer.model.OrderEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration
 *
 * Cấu hình Producer với các tùy chọn tối ưu cho production:
 * - Idempotent: Đảm bảo mỗi message chỉ được ghi đúng 1 lần vào Kafka
 * - acks=all: Chờ tất cả in-sync replicas confirm trước khi trả về ack
 * - Batching + Compression: Tăng throughput, giảm network overhead
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Tạo ProducerFactory với cấu hình production-ready.
     *
     * ProducerFactory là factory tạo ra các Kafka Producer instances.
     * Mỗi KafkaTemplate sử dụng 1 ProducerFactory.
     */
    @Bean
    public ProducerFactory<String, OrderEvent> orderProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // ─── CONNECTION ───
        // Địa chỉ Kafka brokers (có thể nhiều broker cho HA)
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ─── SERIALIZERS ───
        // Key: String (orderId) → StringSerializer
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Value: OrderEvent (POJO) → JsonSerializer (chuyển thành JSON)
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // ─── RELIABILITY ───
        // acks=all: Producer chờ xác nhận từ tất cả in-sync replicas (ISR)
        // Đảm bảo không mất data ngay cả khi broker fail
        // Đánh đổi: latency cao hơn acks=1
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent Producer: Tránh duplicate messages khi retry
        // Kafka gán sequence number cho mỗi message, broker dedup khi nhận duplicate
        // Tự động set: acks=all, max.in.flight.requests.per.connection<=5
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Số lần retry khi gặp transient errors (network blip, leader election)
        // Với idempotence, retry an toàn (không gây duplicate)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        // ─── PERFORMANCE ───
        // Batch size: Gom nhiều records vào 1 batch trước khi gửi
        // 16KB batch = cân bằng giữa throughput và latency
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // Linger time: Chờ tối đa 5ms để gom thêm records vào batch
        // linger.ms=0: Gửi ngay (low latency nhưng nhiều network requests)
        // linger.ms=5: Tăng throughput, chấp nhận 5ms latency
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Compression: Nén batch trước khi gửi qua mạng
        // snappy: Nhanh, CPU thấp, compression ratio 30-50%
        // Giảm network bandwidth, tăng throughput
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Buffer memory: Tổng memory dành cho buffering (32MB)
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate là high-level API để gửi messages.
     * Cung cấp convenient methods: send(), sendDefault(), flush()
     */
    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
        KafkaTemplate<String, OrderEvent> template =
                new KafkaTemplate<>(orderProducerFactory());

        // Bật observation cho distributed tracing (Micrometer)
        template.setObservationEnabled(true);

        return template;
    }
}
