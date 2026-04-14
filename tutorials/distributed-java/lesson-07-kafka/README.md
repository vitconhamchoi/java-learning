# Lesson 07: Apache Kafka - Event Streaming

## Giới thiệu

Apache Kafka là một **distributed event streaming platform** được thiết kế để xử lý hàng triệu events mỗi giây với độ trễ thấp. Ban đầu được phát triển tại LinkedIn, Kafka hiện là nền tảng cho real-time data pipelines và streaming applications.

## Kafka Fundamentals

### Topic, Partition, Offset

```
TOPIC: "orders.created"
┌────────────────────────────────────────────────────────────────┐
│  Partition 0:  [msg0] [msg1] [msg2] [msg3] [msg4]             │
│                  ↑                            ↑                │
│               offset=0                    offset=4 (latest)   │
│                                                                │
│  Partition 1:  [msg0] [msg1] [msg2]                           │
│                                                                │
│  Partition 2:  [msg0] [msg1] [msg2] [msg3]                    │
└────────────────────────────────────────────────────────────────┘

- Topic: Danh mục/feed messages (giống một table trong DB)
- Partition: Đơn vị song song hóa, mỗi partition là log tuần tự
- Offset: Vị trí của message trong partition (tăng dần, bất biến)
- Retention: Kafka giữ messages trong 7 ngày (có thể cấu hình)
```

### Kafka Cluster Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      KAFKA CLUSTER                           │
│                                                             │
│   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐     │
│   │   Broker 1  │   │   Broker 2  │   │   Broker 3  │     │
│   │ (Controller)│   │             │   │             │     │
│   │             │   │             │   │             │     │
│   │ P0 Leader   │   │ P1 Leader   │   │ P2 Leader   │     │
│   │ P1 Replica  │   │ P2 Replica  │   │ P0 Replica  │     │
│   │ P2 Replica  │   │ P0 Replica  │   │ P1 Replica  │     │
│   └─────────────┘   └─────────────┘   └─────────────┘     │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐  │
│   │              ZooKeeper / KRaft                       │  │
│   │     (Cluster metadata, leader election)             │  │
│   └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
         ▲                              │
         │ Produce                      │ Consume
         │                              ▼
┌────────────────┐            ┌─────────────────────┐
│    Producer    │            │   Consumer Group     │
│                │            │                      │
│  Orders API    │            │  ┌───────┐ ┌───────┐│
│                │            │  │Consumer│ │Consumer││
│                │            │  │  1    │ │  2    ││
└────────────────┘            │  │(P0,P1)│ │ (P2)  ││
                              │  └───────┘ └───────┘│
                              └─────────────────────┘
```

### Replication Factor

```
Replication Factor = 3 nghĩa là:
- Mỗi partition có 1 Leader + 2 Replicas
- Leader xử lý tất cả reads/writes
- Replicas sync từ Leader
- Nếu Leader fail, một Replica được bầu làm Leader mới

Min.insync.replicas = 2 (trong acks=all):
- Producer chỉ nhận ack khi ít nhất 2 replicas đã ghi thành công
- Đảm bảo không mất data ngay cả khi 1 broker fail
```

## Producer Configuration

### Các tham số quan trọng

```java
// acks - Bao nhiêu replicas phải acknowledge trước khi producer nhận xác nhận
// acks=0: Fire and forget - không chờ ack, nhanh nhất, có thể mất data
// acks=1: Leader ack - chờ leader ghi xong, cân bằng giữa throughput và safety
// acks=all: All replicas ack - an toàn nhất, chậm nhất

props.put(ProducerConfig.ACKS_CONFIG, "all");

// Idempotent producer: Đảm bảo mỗi message chỉ được ghi đúng 1 lần
// Tự động set acks=all, max.in.flight.requests.per.connection=5
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

// Retries: Số lần thử lại khi gặp transient errors
props.put(ProducerConfig.RETRIES_CONFIG, 3);

// Batching: Gom nhiều records thành một batch trước khi gửi
// Giảm số network roundtrips, tăng throughput
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB

// Linger: Thời gian chờ để gom thêm records vào batch
// linger.ms=0: Gửi ngay (low latency)
// linger.ms=5: Chờ 5ms để gom batch (higher throughput)
props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

// Compression: Nén data trước khi gửi
// snappy: Cân bằng giữa CPU và compression ratio
// lz4: Nhanh hơn snappy
// gzip: Compression ratio tốt nhất, CPU cao nhất
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
```

### Batching và Linger

```
Không có Linger (linger.ms=0):
  msg1 → gửi ngay
  msg2 → gửi ngay
  msg3 → gửi ngay
  (3 network requests)

Với Linger (linger.ms=5):
  msg1 → đợi 5ms...
  msg2 → đợi 5ms...
  msg3 → đợi 5ms...
  → [msg1, msg2, msg3] gửi 1 lần (1 network request, throughput cao hơn)
```

## Consumer Configuration

### Consumer Group và Partition Assignment

```
Topic: orders.created (3 partitions)

Consumer Group "order-processor" có 2 consumers:
  Consumer 1 → Partition 0, Partition 1
  Consumer 2 → Partition 2

Consumer Group "analytics" có 3 consumers:
  Consumer A → Partition 0
  Consumer B → Partition 1
  Consumer C → Partition 2

=> Kafka đảm bảo mỗi partition chỉ được consume bởi 1 consumer trong cùng group
=> Nhiều groups có thể consume cùng topic độc lập (mỗi group có offset riêng)
```

### Poll Loop và Manual Commit

```java
@KafkaListener(topics = "orders.created", groupId = "order-processor")
public void processOrder(ConsumerRecord<String, OrderEvent> record,
                         Acknowledgment ack) {
    try {
        // 1. Xử lý business logic
        orderService.process(record.value());
        
        // 2. Chỉ commit offset khi xử lý THÀNH CÔNG
        // Manual commit đảm bảo at-least-once semantics
        ack.acknowledge();
        
    } catch (Exception e) {
        // 3. Khi lỗi: gửi đến Dead Letter Queue
        // Không commit offset → message sẽ được redelivered? KHÔNG!
        // Phải gửi DLQ rồi acknowledge để tránh vòng lặp vô hạn
        deadLetterService.sendToDlq(record, e);
        ack.acknowledge();  // Vẫn phải acknowledge!
    }
}
```

### Partition Rebalancing

```
Rebalancing xảy ra khi:
- Consumer mới join group
- Consumer rời group (crash hoặc đóng)
- Topic partition thay đổi

Trong lúc rebalancing:
- TẤT CẢ consumers trong group pause
- Coordinator redistribute partitions
- Consumers tiếp tục từ committed offset

=> Rebalancing gây ra "stop the world" pause!
=> Tối thiểu hóa bằng: max.poll.interval.ms, session.timeout.ms
```

## Dead Letter Queue Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                    DLQ Pattern                               │
│                                                             │
│  orders.created ──► Consumer ──► Process ──► ✓ Success     │
│                         │                                   │
│                         │ ✗ Failure (sau N retries)        │
│                         ▼                                   │
│                    orders.dlq                               │
│                         │                                   │
│                         ▼                                   │
│                   DLQ Monitor                               │
│                   - Alert team                              │
│                   - Inspect message                         │
│                   - Replay sau khi fix bug                  │
└─────────────────────────────────────────────────────────────┘

DLQ message headers:
- X-Original-Topic: orders.created
- X-Exception-Type: java.lang.NullPointerException
- X-Exception-Message: Order id cannot be null
- X-Original-Partition: 2
- X-Original-Offset: 12345
- X-Failed-Timestamp: 2024-01-15T10:30:00Z
```

## Exactly-Once Semantics

```
Delivery guarantees:
1. At-most-once:  Commit trước khi xử lý → có thể mất messages
2. At-least-once: Commit sau khi xử lý → có thể duplicate messages  
3. Exactly-once:  Transactional API → mỗi message xử lý đúng 1 lần

Exactly-once trong Kafka:
- Producer: enable.idempotence=true + transactions
- Consumer: isolation.level=read_committed
- Database: Idempotent operations hoặc deduplication

Trong thực tế:
- Exactly-once phức tạp và tốn performance
- Thường chọn at-least-once + idempotent consumers
```

## Kafka Streams

### Stream Processing Topology

```
orders.created (source)
        │
        ▼
   filter(amount > 1000)
        │
        ▼
orders.highvalue (sink)

orders.created (source)
        │
        ▼
  groupBy(customerId)
        │
        ▼
  count(1-minute window)
        │
        ▼
orders.customer-count (sink)
```

### Windowing

```java
// Tumbling Window: Không overlap, cố định
TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1))
// [0:00-1:00), [1:00-2:00), [2:00-3:00)

// Hopping Window: Overlap
TimeWindows.ofSizeAndGrace(Duration.ofMinutes(5), Duration.ofSeconds(30))
  .advanceBy(Duration.ofMinutes(1))
// [0:00-5:00), [1:00-6:00), [2:00-7:00) - overlap 4 phút

// Session Window: Dựa trên activity, gap-based
SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(5))
// Window kéo dài khi có activity, đóng sau 5 phút idle
```

## Consumer Lag Monitoring

```
Consumer Lag = Latest Offset - Consumer Committed Offset

Ví dụ:
  Partition 0: Latest=1000, Committed=950, Lag=50
  Partition 1: Latest=800, Committed=800, Lag=0
  Partition 2: Latest=1200, Committed=900, Lag=300
  
  Total Lag = 350 messages chưa được xử lý

Alert khi lag > threshold (ví dụ > 10000):
  → Consumer quá chậm
  → Cần scale up consumers (thêm instances)
  → Cần tối ưu processing logic
```

```bash
# Kiểm tra consumer lag
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group order-processor \
  --describe
```

## Production Considerations

### 1. Topic Design
- Số partitions = số consumers dự kiến tối đa trong 1 group
- Không thể giảm partitions sau khi tạo (chỉ tăng được)
- Naming convention: `<domain>.<entity>.<event>` (e.g., `orders.order.created`)

### 2. Message Size
```yaml
# Mặc định: 1MB per message
# Nếu cần lớn hơn (không khuyến khích):
message.max.bytes: 10485760  # 10MB
fetch.max.bytes: 10485760
```

### 3. Retention Policy
```yaml
# Theo thời gian (mặc định 7 ngày)
retention.ms: 604800000

# Theo kích thước
retention.bytes: 1073741824  # 1GB per partition
```

### 4. Security
```yaml
security.protocol: SASL_SSL
sasl.mechanism: PLAIN
ssl.truststore.location: /path/to/truststore.jks
```

### 5. Monitoring Metrics
- **Under-replicated partitions**: > 0 → broker có vấn đề
- **Consumer lag**: Tăng liên tục → consumer cần scale
- **Producer error rate**: Cao → network/broker issue
- **Request latency p99**: > 100ms → performance issue

## Anti-patterns

### ❌ Quá nhiều partitions
```
Mỗi partition = file handles + memory overhead trên brokers
Nhiều partitions quá → rebalancing chậm hơn
Rule of thumb: <= 10K partitions per broker
```

### ❌ Message size quá lớn
```java
// KHÔNG NÊN: Gửi file 10MB qua Kafka
producer.send(new ProducerRecord<>("files", fileId, fileContent));

// NÊN: Lưu file vào S3/MinIO, gửi reference qua Kafka
producer.send(new ProducerRecord<>("files", fileId, 
    new FileEvent(fileId, "s3://bucket/path/to/file")));
```

### ❌ Không xử lý consumer rebalancing
```java
// KHÔNG NÊN: Không implement ConsumerRebalanceListener
// Khi rebalance, uncommitted messages sẽ bị reprocess

// NÊN: Commit trước khi rebalance
consumer.subscribe(topics, new ConsumerRebalanceListener() {
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        consumer.commitSync();  // Commit trước khi mất partitions
    }
});
```

### ❌ Blocking trong consumer
```java
// KHÔNG NÊN: Blocking call trong consumer thread
@KafkaListener
public void consume(OrderEvent event) {
    Thread.sleep(10000);  // Block consumer thread!
    // max.poll.interval.ms exceeded → consumer kicked out!
}

// NÊN: Xử lý async hoặc tăng max.poll.interval.ms
@KafkaListener
public void consume(OrderEvent event) {
    CompletableFuture.runAsync(() -> slowProcess(event));
    // Hoặc publish to internal queue for async processing
}
```

### ❌ Commit offset trước khi xử lý
```java
// KHÔNG NÊN: enable-auto-commit=true với business logic phức tạp
// Offset tự động commit → fail sau commit → mất message

// NÊN: Manual commit SAU khi xử lý thành công
```

## Interview Questions & Answers

**Q: Kafka khác gì với RabbitMQ?**
A: Kafka là distributed log, messages được lưu theo offset và retain theo thời gian (7 ngày mặc định). Multiple consumers có thể đọc cùng message. RabbitMQ là traditional message broker, message bị xóa sau khi consumed. Kafka phù hợp cho event streaming và audit log; RabbitMQ phù hợp cho task queues và RPC.

**Q: Tại sao Kafka rất nhanh?**
A: Sequential disk writes (append-only log), zero-copy (sendfile syscall), batching, compression, và page cache của OS. Kafka tránh random I/O bằng cách ghi tuần tự.

**Q: Exactly-once semantics hoạt động thế nào?**
A: Producer idempotence (PID + sequence number để dedup), Kafka transactions (atomic write to multiple partitions), và consumer với isolation.level=read_committed chỉ đọc committed transactions.

**Q: Khi nào nên tăng số partitions?**
A: Khi consumer lag tăng liên tục và không thể tăng throughput của 1 consumer instance. Số partitions quyết định max parallelism của consumer group. Tăng cẩn thận vì partition ordering chỉ đảm bảo trong cùng partition.

**Q: Giải thích consumer group rebalancing?**
A: Khi consumer join/leave group hoặc partition thay đổi, Kafka coordinator (một broker) phân phối lại partitions. Trong lúc rebalance, tất cả consumers pause. Cooperative incremental rebalancing (Kafka 2.4+) chỉ move partitions cần thiết thay vì revoke tất cả.

**Q: Làm thế nào xử lý duplicate messages?**
A: Idempotent consumer: Dùng unique key (message ID) để check trước khi process. Lưu processed IDs vào Redis hoặc DB. Hoặc dùng Kafka transactions nếu cần exactly-once.

**Q: Ordering guarantee trong Kafka?**
A: Ordering chỉ đảm bảo trong cùng partition. Nếu cần order toàn bộ, dùng 1 partition (giảm throughput). Nếu cần order theo entity (e.g., theo orderId), dùng entity ID làm partition key.

**Q: Kafka Streams vs Flink vs Spark Streaming?**
A: Kafka Streams: Nhẹ, chạy trong JVM app, phù hợp simple stream processing. Flink: Stateful processing mạnh, exactly-once, phù hợp complex event processing. Spark Streaming: Micro-batch (không phải real-time thực sự), phù hợp khi đã dùng Spark.

## Cấu trúc Project

```
lesson-07-kafka/
├── README.md
├── docker-compose.yml
├── order-producer/
│   ├── pom.xml
│   └── src/main/java/com/distributed/kafka/producer/
│       ├── OrderProducerApplication.java
│       ├── model/OrderEvent.java
│       ├── config/KafkaProducerConfig.java
│       ├── service/OrderEventPublisher.java
│       └── controller/OrderController.java
├── order-consumer/
│   ├── pom.xml
│   └── src/main/java/com/distributed/kafka/consumer/
│       ├── OrderConsumerApplication.java
│       ├── config/
│       │   ├── KafkaConsumerConfig.java
│       │   └── KafkaAdminConfig.java
│       ├── listener/OrderEventListener.java
│       └── service/DeadLetterService.java
└── kafka-streams/
    ├── pom.xml
    └── src/main/java/com/distributed/kafka/streams/
        ├── KafkaStreamsApplication.java
        └── OrderStreamProcessor.java
```

## Chạy Demo

```bash
# 1. Start Kafka infrastructure
docker-compose up -d zookeeper kafka kafka-ui

# 2. Tạo topics (hoặc để KafkaAdmin tự tạo)
kafka-topics.sh --create --topic orders.created \
  --partitions 3 --replication-factor 1 \
  --bootstrap-server localhost:9092

# 3. Start producer và consumer
docker-compose up -d order-producer order-consumer

# 4. Gửi order event
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-123","amount":500.00}'

# 5. Xem messages trong Kafka UI
open http://localhost:8090

# 6. Kiểm tra consumer lag
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group order-processor \
  --describe
```
