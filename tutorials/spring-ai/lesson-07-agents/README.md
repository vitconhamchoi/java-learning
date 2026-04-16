# Bài 07 – AI Agents & Orchestration

## 🎯 Mục tiêu

- Hiểu khái niệm AI Agent và multi-step reasoning
- Xây dựng agent với Spring AI + Function Calling
- Sử dụng Model Context Protocol (MCP) cho agent-tool interaction
- Tìm hiểu Koog orchestration framework

## 📝 Nội dung chính

### AI Agent là gì?

AI Agent là hệ thống LLM có khả năng **tự quyết định** hành động tiếp theo — gọi tools, tìm kiếm dữ liệu, và lặp lại cho đến khi hoàn thành task.

### Kiến trúc Agent

```
┌──────────────────────────────────────────┐
│                AI Agent                   │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ Planner │─▶│ Executor│─▶│ Evaluator│  │
│  └─────────┘  └────┬────┘  └─────────┘  │
│                    │                      │
│         ┌──────────┼──────────┐           │
│         ▼          ▼          ▼           │
│    ┌────────┐ ┌────────┐ ┌────────┐      │
│    │ Tool 1 │ │ Tool 2 │ │ Tool N │      │
│    └────────┘ └────────┘ └────────┘      │
└──────────────────────────────────────────┘
```

### Model Context Protocol (MCP)

MCP là giao thức chuẩn (tương tự LSP cho code editors) cho phép AI agent tương tác với các tool/resource bên ngoài:

- **Tools** — Hàm mà agent có thể gọi
- **Resources** — Dữ liệu mà agent có thể đọc
- **Prompts** — Template prompt có sẵn

### Koog Framework

JetBrains Koog — framework orchestration cho Spring AI:
- Multi-step reasoning strategies
- Parallel execution & fault tolerance
- Cost optimization & caching

> 🚧 Đang cập nhật code ví dụ đầy đủ...
