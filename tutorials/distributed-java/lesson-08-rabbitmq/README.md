# Lesson 08: RabbitMQ - Message Broker với AMQP

## Giới thiệu

RabbitMQ là một **message broker** triển khai giao thức **AMQP** (Advanced Message Queuing Protocol). Khác với Kafka (distributed log), RabbitMQ là traditional message broker với routing linh hoạt qua exchange types.

## AMQP Concepts

### Kiến trúc tổng quan

```
Producer
   │
   │ Publish message với routing key
   ▼
┌─────────────────────────────────────────────────────────────┐
│                    EXCHANGE                                  │
│   (Routing engine - quyết định message đi đâu)             │
└──────────────────┬──────────────────────────────────────────┘
                   │ Binding rules
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌───────┐     ┌───────┐     ┌───────┐
│Queue 1│     │Queue 2│     │Queue 3│
└───┬───┘     └───┬───┘     └───┬───┘
    │             │             │
    ▼             ▼             ▼
Consumer 1   Consumer 2   Consumer 3
```

### Components

- **Broker**: RabbitMQ server lưu và route messages
- **Exchange**: Routing engine nhận messages từ producer
- **Queue**: Buffer lưu messages chờ consumer
- **Binding**: Quy tắc kết nối exchange với queue (routing key pattern)
- **Routing Key**: Label producer đặt trên message để exchange routing
- **Virtual Host (vhost)**: Logical separation trong 1 broker

## Exchange Types

### 1. Direct Exchange
```
Producer: routing_key="notification.email"
     │
     ▼
Direct Exchange
     │
     ├── binding: "notification.email" → email.queue ✓
     │
     ├── binding: "notification.sms"   → sms.queue  ✗
     │
     └── binding: "notification.push"  → push.queue ✗

Chỉ email.queue nhận được message
Use case: Task distribution, load balancing
```

### 2. Topic Exchange
```
Producer: routing_key="notification.email.transactional"
     │
     ▼
Topic Exchange
     │
     ├── binding: "notification.email.#"  → email.queue  ✓ (# match nhiều words)
     │
     ├── binding: "notification.*.otp"    → otp.queue    ✗ (chỉ match 1 word)
     │
     └── binding: "notification.#"        → all.queue    ✓ (# match tất cả)

Wildcard patterns:
  * = match exactly 1 word
  # = match 0 hoặc nhiều words

Use case: Flexible routing, pub/sub với filtering
```

### 3. Fanout Exchange
```
Producer: routing_key="any" (bị ignore)
     │
     ▼
Fanout Exchange
     │
     ├──────────────► queue1 (Consumer A)
     │
     ├──────────────► queue2 (Consumer B)
     │
     └──────────────► queue3 (Consumer C)

TẤT CẢ queues được binding đều nhận message
Use case: Broadcast, notifications, cache invalidation
```

### 4. Headers Exchange
```
Producer: headers={format="pdf", type="report"}
     │
     ▼
Headers Exchange
     │
     ├── binding: {format="pdf"} x-match=any → pdf.queue  ✓ (any = OR)
     │
     ├── binding: {format="pdf", type="report"} x-match=all → report.queue ✓ (all = AND)
     │
     └── binding: {format="xml"} → xml.queue ✗

x-match=any: Match nếu có ít nhất 1 header khớp (OR)
x-match=all: Match khi tất cả headers đều khớp (AND)
Use case: Complex routing logic không thể express bằng routing key
```

## Message Lifecycle

```
1. Producer gửi message đến Exchange với routing key
      │
      ▼
2. Exchange routing → Queue phù hợp
   - Nếu không có queue nào match: message bị drop (hoặc returned nếu mandatory=true)
      │
      ▼
3. Message lưu trong Queue (RAM hoặc Disk)
   - Persistent message: Ghi ra disk → survive broker restart
   - Transient message: Chỉ trong RAM → mất khi restart
      │
      ▼
4. Consumer poll hoặc được push message (push là default)
      │
      ▼
5. Consumer xử lý message
      │
   ┌──┴──┐
   │     │
   ▼     ▼
  ✓ OK  ✗ FAIL
basicAck  basicNack
  │         │
  │         ├─► requeue=true → Queue lại
  │         └─► requeue=false → Dead Letter Exchange (DLX)
  ▼         
Message bị xóa khỏi Queue
```

## Dead Letter Exchange (DLX) Pattern

```
Một message bị "dead lettered" khi:
1. Bị rejected với requeue=false (basicNack/basicReject)
2. TTL hết hạn
3. Queue đầy (x-max-length)

┌──────────────────────────────────────────────────────────────┐
│                    DLX Pattern                                │
│                                                              │
│  notifications.email.queue                                   │
│  Arguments:                                                  │
│    x-dead-letter-exchange: notifications.dlx                 │
│    x-dead-letter-routing-key: email.failed                   │
│         │                                                    │
│         │ basicNack(requeue=false)                          │
│         ▼                                                    │
│  notifications.dlx (DirectExchange)                         │
│         │                                                    │
│         │ routing key: email.failed                         │
│         ▼                                                    │
│  notifications.dlq                                          │
│         │                                                    │
│         ▼                                                    │
│  DLQ Consumer (Alert, Inspect, Replay)                      │
└──────────────────────────────────────────────────────────────┘
```

## TTL - Time To Live

```java
// TTL trên message: Message hết hạn sau X milliseconds
rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
    msg.getMessageProperties().setExpiration("60000");  // 60 giây
    return msg;
});

// TTL trên queue: Tất cả messages trong queue hết hạn sau X ms
@Bean
public Queue ttlQueue() {
    return QueueBuilder.durable("my.queue")
        .ttl(60000)  // 60 giây
        .deadLetterExchange("my.dlx")
        .build();
}
```

## Priority Queues

```java
// Queue với max priority = 10
@Bean
public Queue priorityQueue() {
    return QueueBuilder.durable("priority.queue")
        .maxPriority(10)
        .build();
}

// Gửi message với priority cao
rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
    msg.getMessageProperties().setPriority(8);  // 0-10, cao hơn = ưu tiên hơn
    return msg;
});
```

## Publisher Confirms

```java
// Publisher Confirms đảm bảo broker đã nhận message
// Tránh mất message khi network issue hoặc broker overload

rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
    if (ack) {
        log.info("Message confirmed: {}", correlationData.getId());
    } else {
        log.error("Message NOT confirmed: {}, cause: {}", 
                  correlationData.getId(), cause);
        // Retry hoặc lưu vào outbox
    }
});

// Gửi với correlation data để track
CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation);
```

## Consumer Prefetch

```
Prefetch = số messages broker gửi tới consumer TRƯỚC KHI nhận ack

Prefetch = 1:
  Broker → Consumer: [msg1]
  Chờ ack...
  Consumer → Broker: ACK msg1
  Broker → Consumer: [msg2]
  (Fair dispatch: đảm bảo consumer không bị overwhelmed)

Prefetch = 10:
  Broker → Consumer: [msg1, msg2, ..., msg10]
  Consumer xử lý từng message, gửi ack ngay
  Broker gửi thêm khi có slot
  (Higher throughput nhưng cần consumer đủ mạnh)
```

```java
factory.setConsumerFactory(consumerFactory());
factory.setPrefetchCount(10);  // Batch processing
```

## Production Considerations

### 1. Cluster và HA
```
RabbitMQ Cluster: Nhiều nodes chia sẻ metadata (exchanges, queues, bindings)
Nhưng: Messages mặc định chỉ lưu trên 1 node

Quorum Queues (recommended thay Classic Mirrored Queues):
@Bean
Queue quorumQueue() {
    return QueueBuilder.durable("my.queue")
        .quorum()  // Raft-based replication
        .build();
}
```

### 2. Persistent Messages
```java
// Mặc định messages là PERSISTENT khi dùng Jackson2JsonMessageConverter
// Nhưng cần đảm bảo:
message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);

// Queue cũng phải durable
QueueBuilder.durable("my.queue").build()  // durable = survive restart
```

### 3. Connection Management
```yaml
# Tránh tạo quá nhiều connections (1 connection = 1 TCP connection)
# Dùng channel multiplexing thay
spring:
  rabbitmq:
    # Connection pool
    cache:
      connection:
        mode: channel
        size: 25  # Số channels per connection
```

### 4. Message Size
```
Khuyến nghị: < 128KB per message
Nếu cần lớn hơn: Lưu payload vào S3/blob storage, gửi reference qua RabbitMQ
```

### 5. Monitoring
```
Metrics cần theo dõi:
- Queue depth: Số messages chờ → Consumer lag
- Consumer utilization: % thời gian consumer bận
- Publish rate vs Deliver rate: Balance
- Memory và Disk usage: Alert khi > 80%
- Connection và Channel counts
```

## Anti-patterns

### ❌ Quá nhiều Queues và Bindings
```
Mỗi queue tiêu thụ RAM (~2MB overhead)
Không nên tạo queue per request hay per user
```

### ❌ Không set Dead Letter Exchange
```java
// KHÔNG NÊN: Không có DLX
QueueBuilder.durable("my.queue").build()

// NÊN: Luôn có DLX để không mất failed messages
QueueBuilder.durable("my.queue")
    .deadLetterExchange("my.dlx")
    .deadLetterRoutingKey("my.dlq")
    .build()
```

### ❌ basicReject với requeue=true vô hạn
```java
// KHÔNG NÊN: Loop vô hạn nếu message luôn fail
channel.basicReject(deliveryTag, true);  // requeue=true

// NÊN: Giới hạn số lần retry, sau đó gửi DLQ
// Sử dụng x-death header để đếm số lần bị dead-lettered
```

### ❌ Dùng RabbitMQ như database
```
RabbitMQ không phải event store như Kafka
Messages bị xóa sau khi consumed
Không thể replay từ đầu như Kafka
```

### ❌ Blocking quá lâu trong consumer
```java
// KHÔNG NÊN: Block consumer thread
@RabbitListener
public void consume(Message msg) {
    Thread.sleep(300000);  // Block 5 phút!
    // Prefetch messages bị giữ, queue backup
}
```

## Interview Questions & Answers

**Q: RabbitMQ khác gì với Kafka?**
A: RabbitMQ là traditional message broker (push-based, smart broker/dumb consumer). Kafka là distributed commit log (pull-based, dumb broker/smart consumer). RabbitMQ: messages bị xóa sau ack, routing linh hoạt qua exchanges. Kafka: messages persist theo retention, không thể xóa 1 message, consumer tự quản lý offset.

**Q: Khi nào dùng Topic Exchange vs Direct Exchange?**
A: Direct: routing key khớp chính xác, dùng cho task distribution rõ ràng. Topic: cần wildcard routing (*.*.critical, notification.#), phù hợp pub/sub với filtering phức tạp.

**Q: Publisher Confirms vs Transactions?**
A: Publisher Confirms: Async, broker ack từng message hoặc batch → throughput cao. Transactions (tx.select/tx.commit): Synchronous, atomic → rất chậm (10-100x slower). Trong production luôn dùng Publisher Confirms.

**Q: Làm thế nào xử lý poison messages?**
A: Dùng x-death header để đếm số lần dead-lettered. Sau N lần → quarantine queue hoặc manual inspection. Không dùng basicNack với requeue=true vô hạn vì tạo infinite loop.

**Q: Quorum Queue vs Classic Mirrored Queue?**
A: Classic Mirrored (deprecated): Sync đến tất cả mirrors → quorum-based khó maintain. Quorum Queue: Dùng Raft consensus (như etcd), ít overhead hơn, được recommend từ RabbitMQ 3.8+.

**Q: Giải thích Consumer Prefetch và ảnh hưởng đến throughput?**
A: Prefetch=1: Fair dispatch, consumer không overwhelmed, throughput thấp. Prefetch=10: Consumer buffer nhiều messages, throughput cao hơn nhưng cần consumer ổn định. Prefetch=0: Unlimited, có thể overwhelm consumer, không khuyến khích.

## Cấu trúc Project

```
lesson-08-rabbitmq/
├── README.md
├── docker-compose.yml
├── notification-producer/
│   ├── pom.xml
│   └── src/main/java/com/distributed/rabbitmq/producer/
│       ├── NotificationProducerApplication.java
│       ├── model/NotificationEvent.java
│       ├── config/RabbitMQConfig.java
│       ├── service/NotificationPublisher.java
│       └── controller/NotificationController.java
└── notification-consumer/
    ├── pom.xml
    └── src/main/java/com/distributed/rabbitmq/consumer/
        ├── NotificationConsumerApplication.java
        ├── config/RabbitMQConsumerConfig.java
        └── listener/
            ├── EmailNotificationListener.java
            └── SmsNotificationListener.java
```

## Chạy Demo

```bash
# 1. Start RabbitMQ
docker-compose up -d rabbitmq

# 2. Mở RabbitMQ Management UI
open http://localhost:15672
# Username: guest, Password: guest

# 3. Start services
docker-compose up -d notification-producer notification-consumer

# 4. Gửi email notification
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Xác nhận đơn hàng",
    "body": "Đơn hàng của bạn đã được xác nhận"
  }'

# 5. Gửi SMS notification
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "SMS",
    "recipient": "+84901234567",
    "subject": "OTP",
    "body": "Mã OTP của bạn là: 123456"
  }'
```
