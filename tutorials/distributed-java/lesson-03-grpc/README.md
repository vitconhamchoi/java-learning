# Lesson 03: gRPC Communication

## Mục lục
1. [gRPC vs REST](#1-grpc-vs-rest)
2. [Protocol Buffers](#2-protocol-buffers)
3. [4 loại gRPC calls](#3-4-loại-grpc-calls)
4. [gRPC Status Codes](#4-grpc-status-codes)
5. [Interceptors](#5-interceptors)
6. [Deadline Propagation](#6-deadline-propagation)
7. [Production Considerations](#7-production-considerations)
8. [Anti-patterns](#8-anti-patterns)
9. [Câu hỏi phỏng vấn](#9-câu-hỏi-phỏng-vấn)

---

## 1. gRPC vs REST

### So sánh chi tiết

| Feature | REST/HTTP | gRPC |
|---------|-----------|------|
| Protocol | HTTP/1.1 | HTTP/2 |
| Format | JSON/XML (text) | Protocol Buffers (binary) |
| Schema | Optional (OpenAPI) | Mandatory (.proto) |
| Code gen | Manual hoặc tools | Auto-generated |
| Streaming | Workaround (SSE, WebSocket) | Native 4 types |
| Performance | Baseline | ~7-10x faster (binary) |
| Browser support | Native | Cần grpc-web proxy |
| Human readable | Yes | No (binary) |
| Languages | Universal | 10+ officially supported |
| Load balancing | HTTP LB | Application-level |

### Khi nào dùng gRPC?

```
Dùng gRPC khi:
✓ Internal microservices communication (không expose ra browser)
✓ High-performance requirements (latency critical)
✓ Streaming data (real-time feeds, IoT telemetry)
✓ Polyglot environments (Java service gọi Go service)
✓ Contract-first API design
✓ Bidirectional streaming (chat, game state sync)

Dùng REST khi:
✓ Public API (browsers, mobile)
✓ Simple CRUD operations
✓ Third-party integration
✓ Caching important (HTTP cache)
✓ Team chưa quen với Protobuf
```

### Performance Comparison

```
Payload size:
{
  "id": "1",
  "name": "Laptop",
  "price": 999.99,
  "category": "electronics"
}
JSON:  ~70 bytes
Protobuf: ~20 bytes (3.5x smaller)

Throughput (benchmark):
REST (JSON): 10,000 req/sec
gRPC (Protobuf): 70,000 req/sec (same hardware)

Latency:
REST P99: ~50ms
gRPC P99: ~8ms
```

---

## 2. Protocol Buffers

### 2.1 Tại sao Binary Encoding?

```
JSON encoding "name": "Alice":
'n''a''m''e'':'' ''A''l''i''c''e' = 14 bytes
Cần parse string, validate JSON syntax

Protobuf encoding field_1="Alice":
[0x0A][0x05]['A''l''i''c''e'] = 7 bytes
Field 1, length 5, then 5 bytes of data
CPU chỉ cần copy bytes, không cần parse
```

### 2.2 Proto3 Syntax

```protobuf
syntax = "proto3";

// Package xác định namespace
package com.distributed.grpc;

// Options cho code generation
option java_package = "com.distributed.grpc.proto";
option java_outer_classname = "ProductProto";

// Message definition (tương tự class trong Java)
message Product {
  string id = 1;        // Field number 1 (không phải value!)
  string name = 2;      // Field number 2
  double price = 3;
  repeated string tags = 4;  // repeated = List
  ProductStatus status = 5;  // Enum
}

enum ProductStatus {
  UNKNOWN = 0;     // Default value phải là 0
  ACTIVE = 1;
  DISCONTINUED = 2;
}

// Service definition
service ProductService {
  rpc GetProduct (GetProductRequest) returns (ProductResponse);
}
```

### 2.3 Field Numbers

```
Field numbers (1-15): encoded in 1 byte → dùng cho frequent fields
Field numbers (16-2047): encoded in 2 bytes → dùng cho less frequent fields

QUAN TRỌNG: KHÔNG BAO GIỜ thay đổi field numbers!
// BAD: Đã deploy version với price=3, giờ đổi price=5 → clients cũ sẽ miss price!
message Product {
  string id = 1;
  string name = 2;
  // string description = 3; // Removed field!
  repeated string tags = 4;
  double price = 5; // WAS 3, now 5 → BREAKING CHANGE!
}

// GOOD: Giữ nguyên field numbers, thêm field mới với number mới
message Product {
  string id = 1;
  string name = 2;
  reserved 3; // Đánh dấu field đã bị removed, không ai dùng lại
  repeated string tags = 4;
  double price = 5; // Move to 5 nhưng phải update tất cả clients trước
  string description = 6; // New field
}
```

---

## 3. 4 loại gRPC calls

### 3.1 Unary RPC (Request-Response)

```
Client                    Server
  │                         │
  │──── GetProduct(req) ────►│
  │                         │ [process]
  │◄─── ProductResponse ────│
  │                         │
Giống REST: 1 request, 1 response
Dùng cho: CRUD, lookups
```

```protobuf
rpc GetProduct (GetProductRequest) returns (ProductResponse);
```

### 3.2 Server Streaming

```
Client                    Server
  │                         │
  │──── ListProducts(req) ──►│
  │◄─── ProductResponse[1] ─│
  │◄─── ProductResponse[2] ─│
  │◄─── ProductResponse[3] ─│
  │◄─── (stream ends) ──────│
1 request, nhiều responses
Dùng cho: Pagination, file download, real-time feeds
```

```protobuf
rpc ListProducts (ListProductsRequest) returns (stream ProductResponse);
```

### 3.3 Client Streaming

```
Client                    Server
  │                         │
  │──── CreateProduct[1] ───►│
  │──── CreateProduct[2] ───►│
  │──── CreateProduct[3] ───►│
  │──── (stream ends) ──────►│
  │                         │ [process all]
  │◄─── BatchCreateResponse ─│
Nhiều requests, 1 response
Dùng cho: File upload, batch create, sensor data ingestion
```

```protobuf
rpc CreateProducts (stream CreateProductRequest) returns (BatchCreateResponse);
```

### 3.4 Bidirectional Streaming

```
Client                    Server
  │                         │
  │──── Message[1] ────────►│
  │◄─── Message[A] ─────────│
  │──── Message[2] ────────►│
  │◄─── Message[B] ─────────│
  │──── Message[3] ────────►│
  │◄─── Message[C] ─────────│
  │──── (close) ────────────►│
Nhiều requests VÀ nhiều responses, độc lập
Dùng cho: Chat, real-time collaboration, game state
```

```protobuf
rpc ChatProducts (stream ProductMessage) returns (stream ProductMessage);
```

---

## 4. gRPC Status Codes

| Code | Tên | HTTP tương đương | Mô tả |
|------|-----|------------------|-------|
| 0 | OK | 200 | Thành công |
| 1 | CANCELLED | 499 | Client hủy request |
| 2 | UNKNOWN | 500 | Lỗi không xác định |
| 3 | INVALID_ARGUMENT | 400 | Invalid input |
| 4 | DEADLINE_EXCEEDED | 504 | Quá timeout |
| 5 | NOT_FOUND | 404 | Không tìm thấy |
| 6 | ALREADY_EXISTS | 409 | Đã tồn tại |
| 7 | PERMISSION_DENIED | 403 | Không có quyền |
| 8 | RESOURCE_EXHAUSTED | 429 | Rate limit / quota |
| 10 | ABORTED | 409 | Transaction conflict |
| 11 | OUT_OF_RANGE | 400 | Out of valid range |
| 12 | UNIMPLEMENTED | 501 | Method chưa implement |
| 13 | INTERNAL | 500 | Internal server error |
| 14 | UNAVAILABLE | 503 | Service không available |
| 16 | UNAUTHENTICATED | 401 | Chưa xác thực |

---

## 5. Interceptors

### 5.1 Server Interceptor

```
Request  → [AuthInterceptor] → [LoggingInterceptor] → ServiceHandler
Response ← [AuthInterceptor] ← [LoggingInterceptor] ← ServiceHandler

AuthInterceptor:
- Check Authorization metadata header
- Verify token (JWT, API key)
- Reject với UNAUTHENTICATED nếu invalid

LoggingInterceptor:
- Log method name, start time
- Wrap listener để log completion + duration
```

### 5.2 Client Interceptor

```java
// Client interceptor để inject auth token
public class AuthClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(
                    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + tokenProvider.getToken()
                );
                super.start(responseListener, headers);
            }
        };
    }
}
```

---

## 6. Deadline Propagation

### 6.1 Tại sao Deadline quan trọng?

```
Không có deadline:
Client ──────────────────────────────────────────── [hanging forever]
Service A ──────────────────────────────────────── [hanging forever]
Service B ──────────────────────────────────────── [hanging forever]

Với deadline propagation:
Client: deadline=2s
  Service A: passes remaining deadline (~1.9s) to B
    Service B: remaining deadline (~1.8s)
      [If any step times out, entire chain fails fast]
```

### 6.2 Deadline vs Timeout

```
Deadline: absolute point in time (timestamp)
  → Tốt cho cross-service propagation

Timeout: duration from now
  → Tốt cho per-service configuration

gRPC dùng Deadline vì dễ propagate across services:
Client: deadline = now + 5s = 10:00:05.000
  Service A nhận: còn 4.8s → pass 4.8s đến Service B
    Service B nhận: còn 4.6s → consistent!
```

---

## 7. Production Considerations

### 7.1 Load Balancing với gRPC

```
Vấn đề: gRPC dùng HTTP/2 persistent connections
Traditional L4 LB (TCP): Một connection đến một backend → không load balance!

Giải pháp 1: L7 Load Balancing (Envoy, nginx)
Client ──HTTP/2──► Envoy ──HTTP/2──► Backend 1
                         ──HTTP/2──► Backend 2

Giải pháp 2: Client-side LB với service discovery (Eureka + Spring Cloud LB)
Client ──resolve("product-service")──► [Backend1:9090, Backend2:9090]
      ──round-robin──► Backend1 or Backend2

Giải pháp 3: Headless service trong Kubernetes
Service ──DNS──► [Pod1:9090, Pod2:9090] (no proxy, direct connection)
```

### 7.2 TLS/mTLS

```yaml
# Server với TLS
grpc:
  server:
    port: 9090
    security:
      certificate-chain: classpath:server.crt
      private-key: classpath:server.key

# mTLS: Server verify client certificate
grpc:
  server:
    security:
      trust-certificate-collection: classpath:ca.crt
      client-auth: require
```

### 7.3 Retry và Hedging

```proto
// gRPC service config (JSON)
{
  "methodConfig": [{
    "name": [{"service": "com.distributed.grpc.ProductService"}],
    "retryPolicy": {
      "maxAttempts": 3,
      "initialBackoff": "0.5s",
      "maxBackoff": "30s",
      "backoffMultiplier": 2,
      "retryableStatusCodes": ["UNAVAILABLE", "DEADLINE_EXCEEDED"]
    }
  }]
}
```

### 7.4 Health Checking

```java
// gRPC Health Check Protocol (standard)
// Implement grpc.health.v1.Health service
@GrpcService
public class HealthCheckService extends HealthGrpc.HealthImplBase {
    @Override
    public void check(HealthCheckRequest req, StreamObserver<HealthCheckResponse> obs) {
        HealthCheckResponse response = HealthCheckResponse.newBuilder()
            .setStatus(ServingStatus.SERVING)
            .build();
        obs.onNext(response);
        obs.onCompleted();
    }
}
```

---

## 8. Anti-patterns

### ❌ Anti-pattern 1: Không dùng Deadline

```java
// SAI: Không set deadline → có thể hang forever
ProductResponse product = stub.getProduct(request);

// ĐÚNG: Set deadline cho mọi call
ProductResponse product = stub
    .withDeadlineAfter(5, TimeUnit.SECONDS)
    .getProduct(request);
```

### ❌ Anti-pattern 2: Thay đổi Field Numbers

```protobuf
// SAI: Removed field 3 và reuse number cho field mới
message Product {
  string id = 1;
  string name = 2;
  // Removed: string description = 3;
  double price = 3; // BREAKING! Old clients decode price as description!
}

// ĐÚNG: Reserve removed field numbers
message Product {
  string id = 1;
  string name = 2;
  reserved 3; // description was here
  reserved "description"; // Optional: reserve the name too
  double price = 4; // New field number
}
```

### ❌ Anti-pattern 3: Large Messages

```
Protobuf không designed cho large payloads (default max 4MB)
SAI: gRPC request mang cả file content trong message
ĐÚNG: Upload file qua GCS/S3, truyền URL qua gRPC
```

### ❌ Anti-pattern 4: Không handle Error Codes

```java
// SAI: Không check status code
ProductResponse response = stub.getProduct(request);

// ĐÚNG: Handle specific status codes
try {
    ProductResponse response = stub.getProduct(request);
} catch (StatusRuntimeException ex) {
    if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return Optional.empty();
    } else if (ex.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
        refreshToken();
        return retry();
    }
    throw ex;
}
```

---

## 9. Câu hỏi phỏng vấn

### Q1: gRPC có những lợi thế gì so với REST?
**Trả lời:**
> gRPC dùng Protocol Buffers (binary) → nhỏ hơn 3-10x và nhanh hơn 7-10x so với JSON. HTTP/2 multiplexing cho phép nhiều requests trên cùng connection. Native streaming support (4 types). Strong typing với .proto schema → contract-first development. Code generation giảm boilerplate. Nhược điểm: không browser-friendly, khó debug hơn.

### Q2: Khi nào dùng Bidirectional Streaming?
**Trả lời:**
> Khi cả client lẫn server cần gửi nhiều messages độc lập: real-time chat, collaborative editing (Google Docs), game state sync, IoT sensor bidirectional control, live trading feeds với acknowledgments. Key characteristic: messages từ hai phía không cần phải alternating (không phải request-response).

### Q3: Làm sao handle backward compatibility với Protobuf?
**Trả lời:**
> Quy tắc: KHÔNG bao giờ thay đổi field numbers, KHÔNG remove fields (thay bằng reserve), KHÔNG đổi field type. Chỉ ADD new fields với new numbers. Optional fields mới sẽ có zero-value nếu client cũ không send. Dùng oneof cho optional variants. Versioning service với /v1, /v2 packages khi có breaking changes.

### Q4: gRPC Deadline Propagation là gì?
**Trả lời:**
> Client set deadline (absolute timestamp), gRPC tự động propagate deadline khi call services khác. Service B nhận deadline = deadline_of_A - elapsed_time. Nếu deadline đã qua → DEADLINE_EXCEEDED. Giúp toàn bộ call chain fail-fast, tránh wasted work. Implement bằng withDeadlineAfter() trong stub.

### Q5: Interceptors trong gRPC để làm gì?
**Trả lời:**
> Tương tự Servlet Filter trong REST. Dùng cho: Authentication (check token trước khi vào service), Logging (log method, duration), Tracing (inject/extract trace context), Rate Limiting, Metrics collection, Error handling. Có thể chain nhiều interceptors. Server interceptor implement ServerInterceptor, client interceptor implement ClientInterceptor.

---

## Chạy ví dụ

```bash
# Build proto files
cd product-server
mvn generate-sources

# Start server
mvn spring-boot:run

# Start client (terminal khác)
cd ../product-client
mvn spring-boot:run

# Test via REST endpoint của client
curl http://localhost:8080/products/1
curl http://localhost:8080/products?category=electronics

# Test với grpcurl (nếu đã install)
grpcurl -plaintext -d '{"id":"1"}' localhost:9090 \
  com.distributed.grpc.ProductService/GetProduct
```
