# Java Learning 🚀

Repo tổng hợp các bài học, ví dụ code và hướng dẫn thực hành về **Java** và hệ sinh thái **Spring Boot** — từ kiến trúc phân tán (Distributed Systems) đến ứng dụng **AI / Generative AI** với Spring AI.

> **Stack chính:** Java 17+ · Spring Boot 3.x / 4.x · Spring AI 2.x · Docker · Kubernetes

---

## 📑 Mục lục

- [Series 1 – Distributed Java](#-series-1--distributed-java-20-bài)
- [Series 2 – Spring AI & Generative AI](#-series-2--spring-ai--generative-ai-10-bài) ✨ **Mới**
- [Yêu cầu hệ thống](#-yêu-cầu-hệ-thống)
- [Cách chạy nhanh](#-chạy-nhanh)
- [Công nghệ sử dụng](#-công-nghệ-sử-dụng)
- [Đóng góp](#-đóng-góp)

---

## 📚 Series 1 – Distributed Java (20 bài)

Loạt bài hướng dẫn chi tiết về lập trình hệ thống phân tán với Spring Boot — từ nền tảng lý thuyết đến triển khai production trên Kubernetes.

### 🔵 Phần 1 – Nền tảng & Giao tiếp

| # | Bài học | Chủ đề |
|---|--------|--------|
| 01 | [Distributed Systems Fundamentals](tutorials/distributed-java/lesson-01-fundamentals/) | CAP theorem, consistency, fault tolerance |
| 02 | [REST Communication](tutorials/distributed-java/lesson-02-rest-communication/) | RestTemplate, WebClient, OpenFeign |
| 03 | [gRPC Communication](tutorials/distributed-java/lesson-03-grpc/) | Protocol Buffers, bidirectional streaming |
| 04 | [Service Discovery (Eureka)](tutorials/distributed-java/lesson-04-service-discovery/) | Eureka Server/Client, self-registration |
| 05 | [Load Balancing](tutorials/distributed-java/lesson-05-load-balancing/) | Spring Cloud LoadBalancer, round-robin / weighted |

### 🟡 Phần 2 – Infrastructure & Messaging

| # | Bài học | Chủ đề |
|---|--------|--------|
| 06 | [API Gateway](tutorials/distributed-java/lesson-06-api-gateway/) | Spring Cloud Gateway, routing, filters, rate limit |
| 07 | [Apache Kafka](tutorials/distributed-java/lesson-07-kafka/) | Producer/Consumer, partitions, consumer groups |
| 08 | [RabbitMQ](tutorials/distributed-java/lesson-08-rabbitmq/) | AMQP, exchanges, queues, dead-letter |
| 09 | [Redis Caching](tutorials/distributed-java/lesson-09-redis-cache/) | Cache-aside, write-through, TTL strategies |

### 🟠 Phần 3 – Resilience & Observability

| # | Bài học | Chủ đề |
|---|--------|--------|
| 10 | [Circuit Breaker](tutorials/distributed-java/lesson-10-circuit-breaker/) | Resilience4j `@CircuitBreaker`, `@Retry`, bulkhead |
| 11 | [Distributed Tracing](tutorials/distributed-java/lesson-11-distributed-tracing/) | Micrometer Tracing + Zipkin, B3 propagation |
| 19 | [Observability](tutorials/distributed-java/lesson-19-observability/) | Micrometer metrics, Prometheus, Grafana, SLO |

### 🔴 Phần 4 – Distributed Patterns

| # | Bài học | Chủ đề |
|---|--------|--------|
| 12 | [CQRS](tutorials/distributed-java/lesson-12-cqrs/) | Command/Query separation, read/write models |
| 13 | [Saga Pattern](tutorials/distributed-java/lesson-13-saga/) | Choreography & Orchestration, compensating txn |
| 14 | [Event Sourcing](tutorials/distributed-java/lesson-14-event-sourcing/) | Event store, aggregate replay, snapshot |
| 15 | [Distributed Transactions](tutorials/distributed-java/lesson-15-distributed-transactions/) | 2-Phase Commit (2PC), TCC (Try-Confirm-Cancel) |

### 🟣 Phần 5 – Scalability & Production

| # | Bài học | Chủ đề |
|---|--------|--------|
| 16 | [Database Sharding](tutorials/distributed-java/lesson-16-sharding/) | Hash, range, consistent hashing (virtual nodes) |
| 17 | [Distributed Locking](tutorials/distributed-java/lesson-17-distributed-locking/) | Redisson `RLock`, Redlock algorithm, watchdog TTL |
| 18 | [Rate Limiting](tutorials/distributed-java/lesson-18-rate-limiting/) | Token bucket, sliding window, Resilience4j RateLimiter |
| 20 | [Kubernetes & Production](tutorials/distributed-java/lesson-20-kubernetes/) | Deployment, HPA, probes, rolling/blue-green/canary |

👉 [Xem chi tiết Series Distributed Java →](tutorials/distributed-java/)

---

## 🤖 Series 2 – Spring AI & Generative AI (10 bài)

> ✨ **Mới** — Series hướng dẫn tích hợp AI / LLM vào ứng dụng Java với **Spring AI 2.x** và **Spring Boot 4.x**.

Nắm bắt xu hướng AI-first trong phát triển phần mềm: từ gọi API ChatGPT đơn giản đến xây dựng hệ thống RAG, AI Agent, và triển khai production.

### 🧠 Phần 1 – Nền tảng Spring AI

| # | Bài học | Chủ đề |
|---|--------|--------|
| 01 | [Spring AI Fundamentals](tutorials/spring-ai/lesson-01-fundamentals/) | Giới thiệu Spring AI, ChatClient, kết nối OpenAI / Ollama |
| 02 | [Prompt Engineering](tutorials/spring-ai/lesson-02-prompt-engineering/) | PromptTemplate, system/user messages, output parsers |
| 03 | [Structured Output](tutorials/spring-ai/lesson-03-structured-output/) | BeanOutputConverter, JSON schema, type-safe responses |

### 🔗 Phần 2 – Tích hợp nâng cao

| # | Bài học | Chủ đề |
|---|--------|--------|
| 04 | [Function Calling](tutorials/spring-ai/lesson-04-function-calling/) | Tool/Function registration, `@McpTool`, callback chains |
| 05 | [RAG – Retrieval-Augmented Generation](tutorials/spring-ai/lesson-05-rag/) | Embeddings, VectorStore (pgvector / Redis), document loaders |
| 06 | [Multimodal AI](tutorials/spring-ai/lesson-06-multimodal/) | Image/audio input, vision models, speech-to-text |

### 🤖 Phần 3 – AI Agents & Production

| # | Bài học | Chủ đề |
|---|--------|--------|
| 07 | [AI Agents & Orchestration](tutorials/spring-ai/lesson-07-agents/) | Multi-step agents, MCP (Model Context Protocol), Koog |
| 08 | [Conversational Memory](tutorials/spring-ai/lesson-08-memory/) | Chat memory, MessageWindowChatMemory, persistent history |
| 09 | [AI Observability & Testing](tutorials/spring-ai/lesson-09-observability/) | Micrometer + AI metrics, tracing LLM calls, testing strategies |
| 10 | [Production Deployment](tutorials/spring-ai/lesson-10-production/) | Cost optimization, caching, rate limiting, security best practices |

👉 [Xem chi tiết Series Spring AI →](tutorials/spring-ai/)

---

## ⚙️ Yêu cầu hệ thống

| Tool | Phiên bản | Ghi chú |
|------|-----------|---------|
| Java | 17+ (khuyến nghị 21+) | Virtual Threads từ Java 21 |
| Maven | 3.8+ | |
| Docker & Docker Compose | Latest | Chạy infrastructure local |
| kubectl & Helm | Latest | Dành cho bài Kubernetes |
| API Key OpenAI / Ollama | — | Dành cho series Spring AI |

## 🚀 Chạy nhanh

```bash
# Clone repo
git clone https://github.com/vitconhamchoi/java-learning.git
cd java-learning

# Vào bài học muốn chạy
cd tutorials/distributed-java/lesson-10-circuit-breaker

# Khởi động services
docker-compose up -d

# Xem logs
docker-compose logs -f
```

## 🛠 Công nghệ sử dụng

<table>
<tr>
<td>

**Backend & Framework**
- Java 17 / 21
- Spring Boot 3.x / 4.x
- Spring Cloud (Gateway, Eureka, LoadBalancer)
- Spring AI 2.x

</td>
<td>

**Messaging & Data**
- Apache Kafka
- RabbitMQ
- Redis
- PostgreSQL (pgvector)

</td>
<td>

**Infrastructure & DevOps**
- Docker & Docker Compose
- Kubernetes (Deployment, HPA, Ingress)
- Prometheus & Grafana
- Zipkin

</td>
</tr>
<tr>
<td>

**AI / LLM Providers**
- OpenAI (GPT-4o, GPT-5)
- Ollama (chạy local)
- Google Gemini
- Anthropic Claude

</td>
<td>

**Patterns**
- CQRS & Event Sourcing
- Saga (Choreography / Orchestration)
- Circuit Breaker & Retry
- RAG, Function Calling, MCP

</td>
<td>

**Observability**
- Micrometer Tracing
- OpenTelemetry
- Structured Logging
- AI-specific metrics

</td>
</tr>
</table>

## 🤝 Đóng góp

Mọi đóng góp đều được chào đón! Hãy tạo Issue hoặc Pull Request nếu bạn muốn:

- 🐛 Báo lỗi hoặc sửa lỗi
- 📝 Cải thiện tài liệu
- ✨ Thêm bài học mới
- 💡 Đề xuất chủ đề

---

<p align="center">
  ⭐ Nếu repo hữu ích, hãy cho một star nhé! ⭐
</p>