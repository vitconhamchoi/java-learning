# Bài 08 – Conversational Memory

## 🎯 Mục tiêu

- Hiểu cách LLM xử lý lịch sử hội thoại
- Sử dụng `MessageWindowChatMemory` để giữ context
- Lưu trữ chat history vào database
- Quản lý token budget và sliding window

## 📝 Nội dung chính

### Vì sao cần Chat Memory?

LLM là stateless — mỗi request là độc lập. Để chatbot "nhớ" cuộc trò chuyện, ta cần gửi lại lịch sử messages trong mỗi request.

### Các chiến lược Memory

| Chiến lược | Mô tả | Khi nào dùng |
|------------|-------|-------------|
| **Window Memory** | Giữ N messages gần nhất | Chat thông thường |
| **Token Buffer** | Giữ messages trong giới hạn token | Kiểm soát chi phí |
| **Summary Memory** | Tóm tắt hội thoại cũ | Hội thoại dài |

### Persistent Memory

```java
@Bean
ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
    return JdbcChatMemory.builder()
        .jdbcTemplate(jdbcTemplate)
        .build();
}
```

### Quản lý Token Budget

- Mỗi model có giới hạn context window (4K → 128K tokens)
- Chi phí tỷ lệ với số tokens gửi đi
- Sliding window + summarization giúp tối ưu

> 🚧 Đang cập nhật code ví dụ đầy đủ...
