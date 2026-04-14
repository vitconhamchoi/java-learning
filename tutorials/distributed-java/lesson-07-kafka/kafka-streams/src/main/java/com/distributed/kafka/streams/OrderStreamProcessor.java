package com.distributed.kafka.streams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Order Stream Processor - Kafka Streams topology để xử lý order events.
 *
 * Streams Topology:
 *
 * orders.created (source)
 *      │
 *      ├──► filter(amount > 1000) ──► orders.highvalue
 *      │
 *      └──► groupBy(customerId) ──► count(1-min window) ──► orders.customer-count
 *
 * Kafka Streams xử lý real-time, stateful, fault-tolerant:
 * - Dữ liệu state được lưu vào local RocksDB + Kafka changelog topics
 * - Khi restart, state được phục hồi từ changelog topics
 * - Exactly-once processing với transactions
 */
@Configuration
public class OrderStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamProcessor.class);

    private static final String INPUT_TOPIC = "orders.created";
    private static final String HIGH_VALUE_TOPIC = "orders.highvalue";
    private static final String CUSTOMER_COUNT_TOPIC = "orders.customer-count";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Định nghĩa Kafka Streams topology.
     *
     * @Autowired vào StreamsBuilder được Spring tự động tạo nhờ @EnableKafkaStreams
     *
     * Topology flow:
     * 1. Source: Đọc từ orders.created topic
     * 2. Branch 1: Filter high-value orders → ghi vào orders.highvalue
     * 3. Branch 2: Count orders per customer trong 1-minute windows
     */
    @Bean
    public KStream<String, JsonNode> orderProcessingStream(StreamsBuilder streamsBuilder) {
        // ─── SOURCE ───
        // Tạo KStream từ topic nguồn
        // KStream: Infinite, unbounded stream of records
        KStream<String, JsonNode> ordersStream = streamsBuilder.stream(
                INPUT_TOPIC,
                Consumed.with(Serdes.String(), new JsonSerde<>(JsonNode.class))
        );

        // ─── BRANCH 1: High-Value Orders Filter ───
        // Lọc chỉ các orders có amount > 1000
        // KStream.filter() không thay đổi key hay value, chỉ bỏ records không thỏa mãn
        KStream<String, JsonNode> highValueOrders = ordersStream
                .filter((orderId, orderData) -> {
                    // Extract amount từ JSON
                    try {
                        if (orderData == null || !orderData.has("amount")) {
                            return false;
                        }
                        BigDecimal amount = new BigDecimal(orderData.get("amount").asText("0"));
                        boolean isHighValue = amount.compareTo(HIGH_VALUE_THRESHOLD) > 0;

                        if (isHighValue) {
                            log.info("High-value order detected: orderId={}, amount={}",
                                    orderId, amount);
                        }
                        return isHighValue;
                    } catch (Exception e) {
                        log.warn("Không thể parse amount cho order: {}", orderId);
                        return false;
                    }
                })
                // Thêm field "flagged=true" vào message
                .mapValues(orderData -> {
                    try {
                        var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) 
                                objectMapper.readTree(orderData.toString());
                        objectNode.put("flagged", true);
                        objectNode.put("flagReason", "high_value");
                        return (JsonNode) objectNode;
                    } catch (Exception e) {
                        return orderData;
                    }
                });

        // Ghi high-value orders vào output topic
        highValueOrders.to(HIGH_VALUE_TOPIC,
                Produced.with(Serdes.String(), new JsonSerde<>(JsonNode.class)));

        // ─── BRANCH 2: Customer Order Count với Tumbling Window ───
        // Đếm số orders của mỗi customer trong mỗi khoảng 1 phút
        //
        // Tumbling Window: Không overlap, cố định kích thước
        // [0:00-1:00), [1:00-2:00), [2:00-3:00)...
        //
        // KTable: Bảng materialized view, aggregation result
        ordersStream
                // Re-key theo customerId để group theo customer
                // Sau selectKey, messages được re-partitioned theo key mới
                .selectKey((orderId, orderData) -> {
                    String customerId = orderData != null && orderData.has("customerId")
                            ? orderData.get("customerId").asText()
                            : "unknown";
                    return customerId;
                })
                // Group records theo key (customerId)
                .groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(JsonNode.class)))
                // Tạo tumbling window 1 phút
                // windowedBy(): Chuyển KGroupedStream thành TimeWindowedKStream
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                // Đếm số records trong mỗi window
                // count() trả về KTable<Windowed<String>, Long>
                .count(Materialized.as("customer-order-counts"))
                // Chuyển KTable thành KStream để ghi ra topic
                .toStream()
                // Convert Windowed<String> key thành String: "customerId@windowStart"
                .selectKey((windowedKey, count) -> {
                    String customerId = windowedKey.key();
                    long windowStart = windowedKey.window().start();
                    return customerId + "@" + windowStart;
                })
                // Map count (Long) thành string để ghi vào topic
                .mapValues(count -> {
                    try {
                        return (JsonNode) objectMapper.readTree(
                                "{\"count\": " + count + "}");
                    } catch (Exception e) {
                        return (JsonNode) objectMapper.createObjectNode();
                    }
                })
                // Ghi vào output topic
                .to(CUSTOMER_COUNT_TOPIC,
                        Produced.with(Serdes.String(), new JsonSerde<>(JsonNode.class)));

        log.info("Kafka Streams topology đã được khởi tạo: " +
                "Input={}, HighValue={}, CustomerCount={}",
                INPUT_TOPIC, HIGH_VALUE_TOPIC, CUSTOMER_COUNT_TOPIC);

        return ordersStream;
    }
}
