# Tutorials

Tổng hợp các series tutorial về **Java** và hệ sinh thái **Spring Boot** — từ kiến trúc phân tán đến ứng dụng AI.

---

## 📂 Các Series hiện có

| # | Series | Số bài | Chủ đề chính |
|---|--------|--------|-------------|
| 1 | [🔗 Distributed Java](distributed-java/) | 20 bài | Spring Cloud, Kafka, Redis, CQRS, Saga, K8s |
| 2 | [🤖 Spring AI & Generative AI](spring-ai/) | 10 bài | ChatClient, RAG, Function Calling, AI Agents, MCP |

---

## 🔗 Series 1 – Distributed Java (20 bài)

Series từ cơ bản đến nâng cao về lập trình hệ thống phân tán. Mỗi bài gồm lý thuyết (tiếng Việt), ví dụ code Spring Boot 3.x / Java 17+ và docker-compose chạy local.

### 🔵 Phần 1 – Nền tảng & Giao tiếp

| # | Lesson | Chủ đề |
|---|--------|--------|
| 01 | [Distributed Systems Fundamentals](distributed-java/lesson-01-fundamentals/) | CAP theorem, consistency, fault tolerance |
| 02 | [REST Communication](distributed-java/lesson-02-rest-communication/) | RestTemplate, WebClient, OpenFeign |
| 03 | [gRPC Communication](distributed-java/lesson-03-grpc/) | Protocol Buffers, bidirectional streaming |
| 04 | [Service Discovery (Eureka)](distributed-java/lesson-04-service-discovery/) | Eureka Server/Client, self-registration |
| 05 | [Load Balancing](distributed-java/lesson-05-load-balancing/) | Spring Cloud LoadBalancer, round-robin / weighted |

### 🟡 Phần 2 – Infrastructure & Messaging

| # | Lesson | Chủ đề |
|---|--------|--------|
| 06 | [API Gateway](distributed-java/lesson-06-api-gateway/) | Spring Cloud Gateway, routing, filters, rate limit |
| 07 | [Apache Kafka](distributed-java/lesson-07-kafka/) | Producer/Consumer, partitions, consumer groups |
| 08 | [RabbitMQ](distributed-java/lesson-08-rabbitmq/) | AMQP, exchanges, queues, dead-letter |
| 09 | [Redis Caching](distributed-java/lesson-09-redis-cache/) | Cache-aside, write-through, TTL strategies |

### 🟠 Phần 3 – Resilience & Observability

| # | Lesson | Chủ đề |
|---|--------|--------|
| 10 | [Circuit Breaker](distributed-java/lesson-10-circuit-breaker/) | Resilience4j `@CircuitBreaker`, `@Retry`, bulkhead |
| 11 | [Distributed Tracing](distributed-java/lesson-11-distributed-tracing/) | Micrometer Tracing + Zipkin, B3 propagation |
| 19 | [Observability](distributed-java/lesson-19-observability/) | Micrometer metrics, Prometheus, Grafana, SLO |

### 🔴 Phần 4 – Distributed Patterns

| # | Lesson | Chủ đề |
|---|--------|--------|
| 12 | [CQRS](distributed-java/lesson-12-cqrs/) | Command/Query separation, read/write models |
| 13 | [Saga Pattern](distributed-java/lesson-13-saga/) | Choreography & Orchestration, compensating txn |
| 14 | [Event Sourcing](distributed-java/lesson-14-event-sourcing/) | Event store, aggregate replay, snapshot |
| 15 | [Distributed Transactions](distributed-java/lesson-15-distributed-transactions/) | 2-Phase Commit (2PC), TCC (Try-Confirm-Cancel) |

### 🟣 Phần 5 – Scalability & Production

| # | Lesson | Chủ đề |
|---|--------|--------|
| 16 | [Database Sharding](distributed-java/lesson-16-sharding/) | Hash, range, consistent hashing (virtual nodes) |
| 17 | [Distributed Locking](distributed-java/lesson-17-distributed-locking/) | Redisson `RLock`, Redlock algorithm, watchdog TTL |
| 18 | [Rate Limiting](distributed-java/lesson-18-rate-limiting/) | Token bucket, sliding window, Resilience4j RateLimiter |
| 20 | [Kubernetes & Production](distributed-java/lesson-20-kubernetes/) | Deployment, HPA, probes, rolling/blue-green/canary |

👉 [Xem chi tiết Series Distributed Java →](distributed-java/)

---

## 🤖 Series 2 – Spring AI & Generative AI (10 bài)

> ✨ **Mới** — Tích hợp AI / LLM vào ứng dụng Java với Spring AI 2.x.

| # | Lesson | Chủ đề |
|---|--------|--------|
| 01 | [Spring AI Fundamentals](spring-ai/lesson-01-fundamentals/) | ChatClient, kết nối OpenAI / Ollama |
| 02 | [Prompt Engineering](spring-ai/lesson-02-prompt-engineering/) | PromptTemplate, system/user messages |
| 03 | [Structured Output](spring-ai/lesson-03-structured-output/) | BeanOutputConverter, JSON schema |
| 04 | [Function Calling](spring-ai/lesson-04-function-calling/) | Tool registration, `@McpTool`, callback chains |
| 05 | [RAG](spring-ai/lesson-05-rag/) | Embeddings, VectorStore (pgvector / Redis) |
| 06 | [Multimodal AI](spring-ai/lesson-06-multimodal/) | Image/audio input, vision models |
| 07 | [AI Agents & Orchestration](spring-ai/lesson-07-agents/) | Multi-step agents, MCP, Koog |
| 08 | [Conversational Memory](spring-ai/lesson-08-memory/) | Chat memory, persistent history |
| 09 | [AI Observability & Testing](spring-ai/lesson-09-observability/) | AI metrics, tracing LLM calls, testing |
| 10 | [Production Deployment](spring-ai/lesson-10-production/) | Cost optimization, caching, security |

👉 [Xem chi tiết Series Spring AI →](spring-ai/)

---

## 🛠️ Yêu cầu chung

- Java 17+ (khuyến nghị 21+ cho series Spring AI)
- Maven 3.8+
- Docker & Docker Compose
- (Tùy lesson) kubectl, Helm, API Key OpenAI / Ollama

## 🚀 Chạy nhanh

```bash
# Vào lesson muốn học
cd distributed-java/lesson-10-circuit-breaker

# Khởi động tất cả services
docker-compose up -d

# Kiểm tra logs
docker-compose logs -f
```