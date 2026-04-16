# Bài 05 – RAG (Retrieval-Augmented Generation)

## 🎯 Mục tiêu

- Hiểu kiến trúc RAG và vì sao cần nó
- Sử dụng Embedding models để vector hóa dữ liệu
- Lưu trữ vectors với VectorStore (pgvector, Redis)
- Xây dựng pipeline RAG end-to-end với Spring AI

## 📝 Nội dung chính

### RAG là gì?

**Retrieval-Augmented Generation** — kỹ thuật bổ sung kiến thức riêng (tài liệu, database) vào LLM mà không cần fine-tune model.

### Kiến trúc RAG

```
                    ┌──────────────┐
                    │   Documents  │
                    └──────┬───────┘
                           │ ETL (load, split, embed)
                           ▼
┌──────────┐      ┌──────────────┐
│   User   │─────▶│  Vector Store │
│  Query   │      │  (pgvector)  │
└──────────┘      └──────┬───────┘
                         │ top-K similar docs
                         ▼
                  ┌──────────────┐
                  │     LLM      │
                  │  + context   │
                  └──────────────┘
```

### Các thành phần chính

- **DocumentReader** — Đọc PDF, HTML, Markdown, JSON
- **TextSplitter** — Chia document thành chunks nhỏ
- **EmbeddingModel** — Chuyển text thành vector (OpenAI, Ollama)
- **VectorStore** — Lưu và tìm kiếm vectors (pgvector, Redis, Milvus)

### VectorStore được hỗ trợ

| VectorStore | Mô tả |
|-------------|-------|
| pgvector | PostgreSQL extension, dễ triển khai |
| Redis | Nhanh, hỗ trợ cả cache lẫn vector search |
| Milvus | Chuyên biệt cho vector search quy mô lớn |
| Weaviate | Cloud-native, schema-based |

> 🚧 Đang cập nhật code ví dụ đầy đủ...
