# Bài 09 – AI Observability & Testing

## 🎯 Mục tiêu

- Theo dõi metrics của LLM calls (latency, tokens, cost)
- Tích hợp Micrometer + OpenTelemetry cho AI workflows
- Viết test cho AI-powered features
- Debug và troubleshoot prompt issues

## 📝 Nội dung chính

### Vì sao cần Observability cho AI?

Khác với API thông thường, LLM calls có đặc thù riêng:
- **Latency cao** (2-30 giây mỗi request)
- **Chi phí theo token** — cần tracking để kiểm soát budget
- **Non-deterministic** — cùng input có thể cho output khác nhau
- **Rate limiting** từ providers

### Metrics quan trọng

| Metric | Mô tả |
|--------|-------|
| `ai.chat.tokens.input` | Số tokens đầu vào |
| `ai.chat.tokens.output` | Số tokens đầu ra |
| `ai.chat.duration` | Thời gian phản hồi |
| `ai.chat.cost` | Chi phí ước tính (USD) |

### Testing Strategies

- **Mock ChatClient** — Test logic business không cần gọi LLM thật
- **Deterministic assertions** — Kiểm tra structure, không kiểm tra nội dung chính xác
- **Evaluation metrics** — Dùng LLM khác để đánh giá chất lượng output
- **Snapshot testing** — So sánh output với baseline đã review

### Tích hợp Prometheus + Grafana

Spring AI auto-expose metrics qua Micrometer → Prometheus scrape → Grafana dashboard.

> 🚧 Đang cập nhật code ví dụ đầy đủ...
