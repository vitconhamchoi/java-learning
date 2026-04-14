package com.distributed.kafka.streams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * Kafka Streams Application.
 *
 * @EnableKafkaStreams: Kích hoạt Kafka Streams support trong Spring.
 * Tự động tạo và start StreamsBuilderFactoryBean.
 */
@SpringBootApplication
@EnableKafkaStreams
public class KafkaStreamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaStreamsApplication.class, args);
    }
}
