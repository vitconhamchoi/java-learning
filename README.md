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

**Nội dung:** CAP theorem · REST/gRPC · Service Discovery · Load Balancing · API Gateway · Kafka/RabbitMQ · Redis · Circuit Breaker · Distributed Tracing · CQRS · Saga · Event Sourcing · Distributed Transactions · Sharding · Distributed Locking · Rate Limiting · Kubernetes

👉 **[Xem chi tiết Series Distributed Java →](tutorials/distributed-java/)**

---

## 🤖 Series 2 – Spring AI & Generative AI (10 bài)

> ✨ **Mới** — Series hướng dẫn tích hợp AI / LLM vào ứng dụng Java với **Spring AI 2.x** và **Spring Boot 4.x**.

Nắm bắt xu hướng AI-first trong phát triển phần mềm: từ gọi API ChatGPT đơn giản đến xây dựng hệ thống RAG, AI Agent, và triển khai production.

**Nội dung:** Spring AI Fundamentals · Prompt Engineering · Structured Output · Function Calling · RAG (Retrieval-Augmented Generation) · Multimodal AI · AI Agents & MCP · Conversational Memory · AI Observability · Production Deployment

👉 **[Xem chi tiết Series Spring AI →](tutorials/spring-ai/)**

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