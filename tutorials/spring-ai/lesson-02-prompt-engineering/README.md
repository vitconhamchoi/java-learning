# Bài 02 – Prompt Engineering với Spring AI

## 🎯 Mục tiêu

- Sử dụng `PromptTemplate` để tạo prompt có tham số
- Phân biệt System Message, User Message, Assistant Message
- Áp dụng các kỹ thuật prompt: few-shot, chain-of-thought
- Sử dụng Output Parsers để định dạng kết quả

## 📝 Nội dung chính

### PromptTemplate

Spring AI cung cấp `PromptTemplate` hoạt động giống `MessageFormat` — cho phép tạo prompt có biến và tái sử dụng.

### Vai trò của các Message Types

| Loại | Vai trò | Ví dụ |
|------|---------|-------|
| **SystemMessage** | Thiết lập ngữ cảnh, "nhân cách" cho AI | "Bạn là chuyên gia Java..." |
| **UserMessage** | Câu hỏi / yêu cầu từ người dùng | "Giải thích SOLID principles" |
| **AssistantMessage** | Phản hồi từ AI (dùng cho few-shot) | Ví dụ câu trả lời mẫu |

### Kỹ thuật Prompt nâng cao

- **Few-shot prompting** — Đưa ví dụ mẫu trong prompt
- **Chain-of-thought** — Yêu cầu AI suy luận từng bước
- **Role-based prompting** — Gán vai trò cụ thể cho AI

> 🚧 Đang cập nhật code ví dụ...
