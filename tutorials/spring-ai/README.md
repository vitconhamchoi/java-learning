# Spring AI & Generative AI Series 🤖

Tổng hợp các tutorial về tích hợp **AI / LLM** vào ứng dụng Java với **Spring AI 2.x** và **Spring Boot 4.x**.

> **Mục tiêu:** Giúp Java developer nắm vững cách xây dựng ứng dụng AI-powered — từ chatbot đơn giản đến hệ thống RAG và AI Agent phức tạp trong môi trường production.

---

## 📚 Danh sách bài học

### 🧠 Phần 1 – Nền tảng Spring AI

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 01 | [Spring AI Fundamentals](lesson-01-fundamentals/) | Giới thiệu Spring AI, ChatClient, kết nối OpenAI / Ollama | ⭐ |
| 02 | [Prompt Engineering](lesson-02-prompt-engineering/) | PromptTemplate, system/user messages, output parsers | ⭐ |
| 03 | [Structured Output](lesson-03-structured-output/) | BeanOutputConverter, JSON schema, type-safe responses | ⭐⭐ |

### 🔗 Phần 2 – Tích hợp nâng cao

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 04 | [Function Calling](lesson-04-function-calling/) | Tool/Function registration, `@McpTool`, callback chains | ⭐⭐ |
| 05 | [RAG – Retrieval-Augmented Generation](lesson-05-rag/) | Embeddings, VectorStore (pgvector / Redis), document loaders | ⭐⭐⭐ |
| 06 | [Multimodal AI](lesson-06-multimodal/) | Image/audio input, vision models, speech-to-text | ⭐⭐ |

### 🤖 Phần 3 – AI Agents & Production

| # | Lesson | Chủ đề | Độ khó |
|---|--------|--------|--------|
| 07 | [AI Agents & Orchestration](lesson-07-agents/) | Multi-step agents, MCP (Model Context Protocol), Koog | ⭐⭐⭐ |
| 08 | [Conversational Memory](lesson-08-memory/) | Chat memory, MessageWindowChatMemory, persistent history | ⭐⭐ |
| 09 | [AI Observability & Testing](lesson-09-observability/) | Micrometer + AI metrics, tracing LLM calls, testing strategies | ⭐⭐⭐ |
| 10 | [Production Deployment](lesson-10-production/) | Cost optimization, caching, rate limiting, security best practices | ⭐⭐⭐ |

---

## 🛠️ Yêu cầu

- Java 21+ (khuyến nghị cho Virtual Threads)
- Maven 3.8+
- Docker & Docker Compose
- API Key: OpenAI hoặc chạy local với [Ollama](https://ollama.ai/)

## 🔑 Cấu hình API Key

```bash
# Option 1: Dùng OpenAI
export SPRING_AI_OPENAI_API_KEY=sk-xxx

# Option 2: Dùng Ollama (miễn phí, chạy local)
docker run -d -p 11434:11434 ollama/ollama
ollama pull llama3
```

## 🚀 Chạy nhanh

```bash
# Vào lesson muốn học
cd lesson-05-rag

# Khởi động infrastructure (nếu có)
docker-compose up -d

# Chạy ứng dụng
./mvnw spring-boot:run
```

## 📖 Kiến thức nền tảng

Trước khi bắt đầu series này, bạn nên có kiến thức cơ bản về:

- **Spring Boot 3.x** — dependency injection, REST API, auto-configuration
- **REST API** — HTTP methods, request/response
- **Docker** — chạy container cơ bản

> 💡 Không cần biết trước về Machine Learning hay AI — series sẽ giải thích từ đầu!
