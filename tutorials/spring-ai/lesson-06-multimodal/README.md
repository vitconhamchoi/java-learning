# Bài 06 – Multimodal AI

## 🎯 Mục tiêu

- Gửi image/audio cùng text prompt tới LLM
- Sử dụng vision models (GPT-4o, Gemini)
- Tích hợp speech-to-text và text-to-speech
- Xử lý nhiều loại media trong một request

## 📝 Nội dung chính

### Multimodal là gì?

Các model hiện đại (GPT-4o, Gemini) không chỉ xử lý text mà còn hiểu hình ảnh, âm thanh. Spring AI hỗ trợ gửi nhiều loại media trong cùng một prompt.

### Gửi ảnh kèm câu hỏi

```java
var imageResource = new ClassPathResource("diagram.png");
String response = chatClient.prompt()
    .user(u -> u.text("Mô tả kiến trúc trong hình này")
                .media(MimeTypeUtils.IMAGE_PNG, imageResource))
    .call()
    .content();
```

### Các use case phổ biến

- 📸 Phân tích ảnh sản phẩm, hóa đơn
- 🎤 Chuyển đổi giọng nói thành text (transcription)
- 🔊 Tạo audio từ text (ElevenLabs, OpenAI TTS)
- 📄 OCR thông minh — đọc và hiểu nội dung tài liệu

> 🚧 Đang cập nhật code ví dụ đầy đủ...
