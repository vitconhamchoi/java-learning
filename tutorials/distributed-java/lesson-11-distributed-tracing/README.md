# Lesson 11: Distributed Tracing (Micrometer Tracing + Zipkin)

## Giới thiệu

Distributed Tracing giúp theo dõi request flow qua nhiều microservices. Mỗi request có một `traceId` duy nhất, mỗi operation là một `span`.

## Concepts

```
┌─────────────────────────────────────────────────────────────┐
│                    Distributed Trace                         │
│                                                             │
│  TraceId: abc123                                            │
│  ├── Span 1: order-service [create-order] 250ms            │
│  │   ├── Span 2: order-service [validate-order] 50ms       │
│  │   └── Span 3: order-service [process-payment] 180ms     │
│  │       └── Span 4: payment-service [payment-process] 150ms│
│  │           └── Span 5: payment-service [charge-card] 100ms│
│  └─────────────────────────────────────────────────────────│
│                                                             │
│  Total latency: 250ms                                       │
└─────────────────────────────────────────────────────────────┘
```

## Trace Context Propagation

```
order-service → HTTP Request → payment-service
Headers:
  X-B3-TraceId: abc123def456     (global trace ID)
  X-B3-SpanId: 111aaa            (current span ID)
  X-B3-ParentSpanId: 000000      (parent span ID)
  X-B3-Sampled: 1                (sampling decision)
```

## Architecture

```
User Request
    │
    ▼
┌───────────────┐    OpenFeign      ┌────────────────┐
│ order-service │ ────────────────► │ payment-service │
│   port:8080   │                   │   port:8081    │
└───────┬───────┘                   └────────┬───────┘
        │                                    │
        │ Zipkin Reporter                    │ Zipkin Reporter
        │ (async, non-blocking)              │
        ▼                                    ▼
┌───────────────────────────────────────────────────┐
│              Zipkin Server port:9411              │
│  - Stores traces                                  │
│  - Provides UI for visualization                  │
│  - Supports search by traceId, service, time      │
└───────────────────────────────────────────────────┘
```

## Dependencies

```xml
<!-- Micrometer Tracing với Brave (Zipkin) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
<!-- Feign với Micrometer -->
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-micrometer</artifactId>
</dependency>
```

## Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 100% sampling (dev only!)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans

logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]"
```

## Code Examples

### Custom Spans
```java
@NewSpan("create-order")
public Map<String, Object> createOrder(@SpanTag("product.id") String productId, double amount) {
    var span = tracer.currentSpan();
    span.tag("order.amount", String.valueOf(amount));
    span.event("order.validation.started");
    // business logic
    span.event("order.created");
    return result;
}
```

### Manual Span
```java
Span span = tracer.nextSpan().name("database-query");
try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
    span.tag("db.table", "orders");
    // database operation
} finally {
    span.end();
}
```

## Project Structure

```
lesson-11-distributed-tracing/
├── order-service/
│   ├── src/main/java/com/distributed/tracing/
│   │   ├── OrderTracingApplication.java
│   │   ├── config/PaymentClient.java
│   │   ├── controller/OrderController.java
│   │   └── service/OrderTracingService.java
│   └── src/main/resources/application.yml
├── payment-service/
│   ├── src/main/java/com/distributed/tracing/
│   │   ├── PaymentTracingApplication.java
│   │   └── controller/PaymentController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Test với tracing
curl -X POST "http://localhost:8080/api/orders?productId=PROD-001&amount=100.0"

# Xem Zipkin UI
open http://localhost:9411

# Tìm trace theo service
# Zipkin UI → Find Traces → Service Name: order-service

# Lấy traceId từ response
TRACE_ID=$(curl -s -X POST "http://localhost:8080/api/orders?productId=PROD-001&amount=100.0" | jq -r '.traceId')

# Xem trace trong Zipkin
open "http://localhost:9411/zipkin/traces/$TRACE_ID"
```

## Sampling Strategies

```yaml
# Development: 100% sampling
management.tracing.sampling.probability: 1.0

# Production: 10% sampling
management.tracing.sampling.probability: 0.1

# Production with adaptive sampling
# Custom sampler based on endpoint or user
```

```java
// Custom Sampler
@Bean
public Sampler sampler() {
    return (context) -> {
        // Always trace errors
        if (context.tags().containsKey("error")) return true;
        // Sample 10% of traffic
        return Math.random() < 0.1;
    };
}
```

## Baggage (Cross-service context)

```java
// Trong order-service - set baggage
BaggageField userId = BaggageField.create("userId");
userId.updateValue(tracer.currentTraceContext(), "user-123");

// Trong payment-service - read baggage
String userId = BaggageField.create("userId").getValue();
```

## Production Tips

### 1. Async Context Propagation
```java
// Tracing tự động propagate qua @Async nếu dùng executor đúng cách
@Bean
public Executor taskExecutor(Tracer tracer) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    return new LazyTraceExecutor(executor, tracer);
}
```

### 2. Storage Backend
```
Zipkin hỗ trợ multiple storage:
- In-memory (dev only)
- Elasticsearch (production)
- Cassandra (high volume)
- MySQL/PostgreSQL

# Zipkin với Elasticsearch
docker run -e STORAGE_TYPE=elasticsearch \
  -e ES_HOSTS=http://elasticsearch:9200 \
  openzipkin/zipkin
```

### 3. Jaeger thay thế Zipkin
```yaml
# Jaeger với OpenTelemetry
management:
  otlp:
    tracing:
      endpoint: http://jaeger:4318/v1/traces
```

## Interview Q&A

**Q: Trace ID vs Span ID?**
A: TraceId = unique ID cho toàn bộ request flow qua tất cả services. SpanId = unique ID cho một operation trong một service. Một trace có nhiều spans tạo thành cây.

**Q: Sampling rate bao nhiêu là tốt?**
A: Dev: 100%. Prod: 1-10% cho high-traffic services. Critical paths (errors, slow queries): 100%. Tail-based sampling tốt hơn head-based.

**Q: Tracing overhead như thế nào?**
A: CPU overhead ~1-2%, Memory ~10MB. Async reporters không block request thread. Batch sending giảm network overhead.

**Q: Khác biệt Zipkin, Jaeger, Tempo?**
A: Zipkin: simple, Brave library. Jaeger: CNCF, OpenTelemetry native. Grafana Tempo: cost-effective, chỉ store traces (query qua TraceQL). Tất cả đều support W3C TraceContext.

**Q: Làm sao correlate logs với traces?**
A: Inject traceId/spanId vào log pattern: `%X{traceId}`. Sau đó trong ELK/Loki search log theo traceId để xem full context.
