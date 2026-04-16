# Bài 10 – Production Deployment

## 🎯 Mục tiêu

- Tối ưu chi phí khi chạy AI ở production
- Caching responses để giảm API calls
- Rate limiting và circuit breaker cho LLM calls
- Security best practices (API key management, input validation)

## 📝 Nội dung chính

### Thách thức ở Production

| Thách thức | Giải pháp |
|------------|-----------|
| Chi phí cao | Caching, model selection, prompt optimization |
| Latency cao | Streaming, async processing, CDN |
| Rate limiting | Queue + retry, circuit breaker |
| Bảo mật | API key vault, input sanitization, output filtering |

### Caching Strategy

```
User Query ──▶ Cache Check ──▶ Cache Hit? ──▶ Return cached
                                    │ No
                                    ▼
                              LLM Call ──▶ Cache Store ──▶ Return
```

- **Semantic caching** — Cache theo ý nghĩa, không chỉ exact match
- **TTL-based** — Set thời gian hết hạn phù hợp
- **Prompt fingerprint** — Hash prompt + params làm cache key

### Security Checklist

- ✅ API keys trong Vault / Secret Manager, KHÔNG hardcode
- ✅ Input validation — chống prompt injection
- ✅ Output filtering — loại bỏ nội dung nhạy cảm
- ✅ Rate limiting per user/tenant
- ✅ Audit logging cho mọi LLM calls
- ✅ Cost alerts khi vượt ngưỡng

### Architecture cho Production

```
┌─────────┐     ┌──────────┐     ┌───────────┐
│  Client  │────▶│ Gateway  │────▶│ AI Service│
└─────────┘     │ (rate    │     │ (Spring   │
                │  limit)  │     │  AI)      │
                └──────────┘     └─────┬─────┘
                                       │
                          ┌────────────┼────────────┐
                          ▼            ▼            ▼
                    ┌──────────┐ ┌──────────┐ ┌──────────┐
                    │  Cache   │ │ Vector   │ │  LLM     │
                    │  (Redis) │ │  Store   │ │ Provider │
                    └──────────┘ └──────────┘ └──────────┘
```

> 🚧 Đang cập nhật code ví dụ đầy đủ...
