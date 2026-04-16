# Distributed Java Series 🔗

Loạt bài hướng dẫn chi tiết về lập trình hệ thống phân tán với Spring Boot — từ nền tảng lý thuyết đến triển khai production trên Kubernetes.

> **Stack chính:** Java 17+ · Spring Boot 3.x · Spring Cloud · Docker · Kubernetes

---

## 📚 Danh sách bài học

### 🔵 Phần 1 – Nền tảng & Giao tiếp

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 01 | [Distributed Systems Fundamentals](lesson-01-fundamentals/) | CAP theorem, consistency, fault tolerance | ⭐ |
| 02 | [REST Communication](lesson-02-rest-communication/) | RestTemplate, WebClient, OpenFeign | ⭐ |
| 03 | [gRPC Communication](lesson-03-grpc/) | Protocol Buffers, bidirectional streaming | ⭐⭐ |
| 04 | [Service Discovery (Eureka)](lesson-04-service-discovery/) | Eureka Server/Client, self-registration | ⭐⭐ |
| 05 | [Load Balancing](lesson-05-load-balancing/) | Spring Cloud LoadBalancer, round-robin / weighted | ⭐⭐ |

### 🟡 Phần 2 – Infrastructure & Messaging

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 06 | [API Gateway](lesson-06-api-gateway/) | Spring Cloud Gateway, routing, filters, rate limit | ⭐⭐ |
| 07 | [Apache Kafka](lesson-07-kafka/) | Producer/Consumer, partitions, consumer groups | ⭐⭐ |
| 08 | [RabbitMQ](lesson-08-rabbitmq/) | AMQP, exchanges, queues, dead-letter | ⭐⭐ |
| 09 | [Redis Caching](lesson-09-redis-cache/) | Cache-aside, write-through, TTL strategies | ⭐⭐ |

### 🟠 Phần 3 – Resilience & Observability

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 10 | [Circuit Breaker](lesson-10-circuit-breaker/) | Resilience4j `@CircuitBreaker`, `@Retry`, bulkhead | ⭐⭐⭐ |
| 11 | [Distributed Tracing](lesson-11-distributed-tracing/) | Micrometer Tracing + Zipkin, B3 propagation | ⭐⭐⭐ |
| 19 | [Observability](lesson-19-observability/) | Micrometer metrics, Prometheus, Grafana, SLO | ⭐⭐⭐ |

### 🔴 Phần 4 – Distributed Patterns

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 12 | [CQRS](lesson-12-cqrs/) | Command/Query separation, read/write models | ⭐⭐⭐ |
| 13 | [Saga Pattern](lesson-13-saga/) | Choreography & Orchestration, compensating txn | ⭐⭐⭐ |
| 14 | [Event Sourcing](lesson-14-event-sourcing/) | Event store, aggregate replay, snapshot | ⭐⭐⭐ |
| 15 | [Distributed Transactions](lesson-15-distributed-transactions/) | 2-Phase Commit (2PC), TCC (Try-Confirm-Cancel) | ⭐⭐⭐⭐ |

### 🟣 Phần 5 – Scalability & Production

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 16 | [Database Sharding](lesson-16-sharding/) | Hash, range, consistent hashing (virtual nodes) | ⭐⭐⭐ |
| 17 | [Distributed Locking](lesson-17-distributed-locking/) | Redisson `RLock`, Redlock algorithm, watchdog TTL | ⭐⭐⭐ |
| 18 | [Rate Limiting](lesson-18-rate-limiting/) | Token bucket, sliding window, Resilience4j RateLimiter | ⭐⭐⭐ |
| 20 | [Kubernetes & Production](lesson-20-kubernetes/) | Deployment, HPA, probes, rolling/blue-green/canary | ⭐⭐⭐⭐ |

---

## 🛠️ Yêu cầu

| Tool | Phiên bản | Ghi chú |
|------|-----------|---------|
| Java | 17+ | |
| Maven | 3.8+ | |
| Docker & Docker Compose | Latest | Chạy infrastructure local |
| kubectl & Helm | Latest | Dành cho bài Kubernetes |

## 🚀 Chạy nhanh

```bash
# Vào lesson muốn học
cd lesson-10-circuit-breaker

# Khởi động tất cả services
docker-compose up -d

# Kiểm tra logs
docker-compose logs -f
```

## 🧪 Testing & Troubleshooting

Mỗi lesson đều có hướng dẫn chi tiết trong README riêng của nó. Các lệnh chung:

```bash
# Build project
./mvnw clean install

# Chạy tests
./mvnw test

# Dọn dẹp containers
docker-compose down -v
```

## 📖 Kiến thức nền tảng

Trước khi bắt đầu series này, bạn nên có:

- **Java Core** — OOP, collections, streams
- **Spring Boot basics** — dependency injection, REST API
- **Docker basics** — chạy container, docker-compose

---

👉 [Quay lại trang chủ](../../README.md)
