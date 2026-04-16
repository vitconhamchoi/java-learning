# Bài 04 – Function Calling (Tool Use)

## 🎯 Mục tiêu

- Hiểu cách LLM gọi function/tool bên ngoài
- Đăng ký Java function cho Spring AI
- Sử dụng annotation `@McpTool` (Spring AI 2.x)
- Xây dựng callback chain với nhiều tools

## 📝 Nội dung chính

### Function Calling là gì?

Cho phép LLM "gọi" code Java thật khi cần dữ liệu thực tế — ví dụ tra cứu database, gọi API, tính toán — thay vì hallucinate.

### Luồng hoạt động

```
User ──▶ LLM ──▶ "Tôi cần gọi function getWeather(city)"
                        │
                        ▼
              Spring AI gọi Java method
                        │
                        ▼
              LLM nhận kết quả ──▶ Trả lời user
```

### Đăng ký Function

```java
@Bean
@Description("Lấy thông tin thời tiết theo thành phố")
public Function<WeatherRequest, WeatherResponse> getWeather() {
    return request -> weatherService.getWeather(request.city());
}
```

### Model Context Protocol (MCP)

Spring AI 2.x hỗ trợ **MCP** — giao thức chuẩn cho phép AI agent tương tác với tools, resources và context bên ngoài theo cách thống nhất.

> 🚧 Đang cập nhật code ví dụ đầy đủ...
