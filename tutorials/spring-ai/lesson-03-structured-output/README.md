# Bài 03 – Structured Output

## 🎯 Mục tiêu

- Chuyển đổi phản hồi LLM sang Java object (type-safe)
- Sử dụng `BeanOutputConverter` và `ListOutputConverter`
- Định nghĩa JSON Schema cho output
- Xử lý lỗi khi LLM trả về format sai

## 📝 Nội dung chính

### Vì sao cần Structured Output?

LLM trả về text tự do — khó parse và không type-safe. Spring AI cung cấp Output Converters để tự động map response sang Java POJO.

### Các Output Converter có sẵn

| Converter | Mô tả |
|-----------|-------|
| `BeanOutputConverter<T>` | Map sang một Java object |
| `ListOutputConverter` | Map sang `List<String>` |
| `MapOutputConverter` | Map sang `Map<String, Object>` |

### Ví dụ

```java
record BookRecommendation(String title, String author, String reason) {}

var converter = new BeanOutputConverter<>(BookRecommendation.class);
String response = chatClient.prompt()
    .user("Gợi ý 1 cuốn sách Java hay nhất")
    .call()
    .content();
BookRecommendation book = converter.convert(response);
```

> 🚧 Đang cập nhật code ví dụ đầy đủ...
