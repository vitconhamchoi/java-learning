# Bài 01 – Spring AI Fundamentals

## 🎯 Mục tiêu

- Hiểu Spring AI là gì và vì sao cần nó
- Cấu hình Spring AI với OpenAI và Ollama
- Sử dụng `ChatClient` để giao tiếp với LLM
- Phân biệt các model provider (OpenAI, Ollama, Anthropic, Gemini)

## 📝 Nội dung chính

### Spring AI là gì?

Spring AI là framework giúp Java developer tích hợp AI/LLM vào ứng dụng Spring Boot theo phong cách quen thuộc — dependency injection, auto-configuration, và abstraction layer giống Spring Data.

### Kiến trúc tổng quan

```
┌─────────────────────────────────────────┐
│           Spring Boot Application       │
├─────────────────────────────────────────┤
│              ChatClient API             │
├──────────┬──────────┬───────────────────┤
│  OpenAI  │  Ollama  │  Anthropic / ...  │
└──────────┴──────────┴───────────────────┘
```

### Các khái niệm cốt lõi

- **ChatClient** — Interface chính để giao tiếp với LLM
- **Prompt** — Lời nhắc gửi tới model
- **ChatResponse** — Phản hồi từ model
- **Model Provider** — Adapter kết nối tới từng LLM cụ thể

## 🔧 Dependency

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

> 🚧 Đang cập nhật code ví dụ...
