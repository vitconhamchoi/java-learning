# Lesson 05: Load Balancing

## Mục lục
1. [Load Balancing là gì?](#1-load-balancing-là-gì)
2. [Client-side vs Server-side LB](#2-client-side-vs-server-side-lb)
3. [Load Balancing Algorithms](#3-load-balancing-algorithms)
4. [Spring Cloud LoadBalancer](#4-spring-cloud-loadbalancer)
5. [Custom Load Balancer](#5-custom-load-balancer)
6. [Sticky Sessions](#6-sticky-sessions)
7. [Production Considerations](#7-production-considerations)
8. [Anti-patterns](#8-anti-patterns)
9. [Câu hỏi phỏng vấn](#9-câu-hỏi-phỏng-vấn)

---

## 1. Load Balancing là gì?

Load Balancing phân phối traffic đến nhiều instances của một service để:
- **Scale**: Xử lý nhiều requests hơn
- **High Availability**: Nếu một instance down, traffic route đến instances khác
- **Performance**: Tận dụng tất cả resources

```
Không có Load Balancer:
Client ──────────────────────────────────► Server A (100% load)
                                           Server B (0% load, idle)
                                           Server C (0% load, idle)

Với Load Balancer:
Client ──────► Load Balancer ────────────► Server A (33% load)
                             ────────────► Server B (33% load)
                             ────────────► Server C (33% load)
```

---

## 2. Client-side vs Server-side LB

### 2.1 Server-side Load Balancing

```
         ┌────────────────┐
Client ──►│  Load Balancer │──► Server A
         │  (HAProxy,     │──► Server B
         │  Nginx, ALB)   │──► Server C
         └────────────────┘
           Single entry point

Pros:
✓ Client đơn giản (không biết về LB)
✓ Centralized management
✓ TLS termination

Cons:
✗ Extra network hop (latency)
✗ LB là SPOF nếu không HA
✗ LB có thể bottleneck
```

### 2.2 Client-side Load Balancing (Spring Cloud LB)

```
Client ──query Eureka──► [Server A: 10.0.0.1:8081]
                         [Server B: 10.0.0.2:8081]
                         [Server C: 10.0.0.3:8081]
Client ──round-robin──► Server A (request 1)
Client ──round-robin──► Server B (request 2)
Client ──round-robin──► Server C (request 3)

Pros:
✓ No extra hop
✓ Flexible LB algorithms
✓ Works with service discovery
✓ Client-side awareness (retry on different instance)

Cons:
✗ LB logic in every client
✗ Client must know about discovery
```

---

## 3. Load Balancing Algorithms

### 3.1 Round Robin (Mặc định trong Spring Cloud LB)

```
Requests:  1    2    3    4    5    6
Instances: A    B    C    A    B    C

Pros: Simple, fair distribution
Cons: Không account cho server capacity khác nhau
```

### 3.2 Weighted Round Robin

```
Instance A: weight=3
Instance B: weight=2
Instance C: weight=1

Pattern: A A A B B C A A A B B C...
(A nhận 50%, B 33%, C 17%)

Dùng khi: Instances có capacity khác nhau
Ví dụ: Server mạnh hơn có weight cao hơn
```

### 3.3 Random

```
Mỗi request: random chọn instance
Simple nhưng không guaranteed even distribution với traffic thấp
```

### 3.4 Least Connections

```
State:
  Instance A: 50 active connections
  Instance B: 10 active connections
  Instance C: 30 active connections

New request → Route to Instance B (least connections)

Dùng khi: Requests có varying processing time
Ví dụ: File uploads (long connection) + API calls (short connection)
```

### 3.5 IP Hash (Sticky)

```
hash(clientIP) % numberOfInstances = instanceIndex

Client 1 (IP: 10.0.0.1): hash → always Instance B
Client 2 (IP: 10.0.0.2): hash → always Instance A

Pros: Session affinity (sticky sessions)
Cons: Uneven distribution nếu few clients; instance down → rehash
```

### 3.6 So sánh Algorithms

| Algorithm | Distribution | Session Affinity | Complexity | Use Case |
|-----------|-------------|------------------|------------|----------|
| Round Robin | Equal | No | Low | Stateless services |
| Weighted RR | Proportional | No | Low | Mixed capacity |
| Random | Stochastic | No | Low | Simple, low traffic |
| Least Connections | Dynamic | No | Medium | Varying request times |
| IP Hash | Consistent | Yes | Medium | Stateful sessions |

---

## 4. Spring Cloud LoadBalancer

### 4.1 Cấu hình cơ bản

```yaml
spring:
  cloud:
    loadbalancer:
      configurations: default   # Round-robin
      retry:
        enabled: true
        max-retries-on-same-service-instance: 0
        max-retries-on-next-service-instance: 2
        retryable-status-codes: 500,502,503
```

### 4.2 @LoadBalanced WebClient

```java
@Bean
@LoadBalanced
public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
}

// Sử dụng với service name
WebClient client = webClientBuilder.build();
client.get().uri("http://backend-service/backend/info").retrieve()...
// Spring Cloud LB tự resolve "backend-service" và load balance
```

### 4.3 Health-aware Load Balancing

```java
// Spring Cloud LB tự động skip DOWN instances
// nhờ tích hợp với Spring Actuator health
@Bean
public HealthCheckServiceInstanceListSupplier healthCheckSupplier(...) {
    return DiscoveryClientServiceInstanceListSupplier
        .builder()
        .withBlockingDiscoveryClient()
        .withHealthChecks()  // Chỉ route đến healthy instances
        .build(context);
}
```

---

## 5. Custom Load Balancer

### 5.1 Implement ReactorServiceInstanceLoadBalancer

```java
public class WeightedLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return serviceInstanceListSupplierProvider
            .getIfAvailable(NoopServiceInstanceListSupplier::new)
            .get(request)
            .next()
            .map(instances -> processInstanceResponse(instances));
    }

    private Response<ServiceInstance> processInstanceResponse(
            List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return new EmptyResponse();
        }
        ServiceInstance chosen = weightedSelect(instances);
        return new DefaultResponse(chosen);
    }
}
```

### 5.2 Đăng ký Custom LoadBalancer

```java
// Per-service configuration
@LoadBalancerClient(
    name = "backend-service",
    configuration = WeightedLoadBalancerConfig.class
)
@Configuration
public class LoadBalancerConfig {}

// Config class (không được @Configuration-scan toàn cục!)
public class WeightedLoadBalancerConfig {
    @Bean
    public ReactorServiceInstanceLoadBalancer weightedLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> supplier) {
        return new WeightedLoadBalancer("backend-service", supplier);
    }
}
```

---

## 6. Sticky Sessions

### 6.1 Cookie-based Sticky Sessions

```
Request 1: Client → LB → Instance A
           LB sets cookie: SERVERID=A
Request 2: Client sends SERVERID=A cookie → LB → Instance A (sticky!)
Request 3: Client sends SERVERID=A cookie → LB → Instance A (sticky!)

Vấn đề khi Instance A down:
Request 4: Client sends SERVERID=A → Instance A [DOWN!]
           LB → failover to Instance B, set new cookie SERVERID=B
```

### 6.2 Consistent Hashing (Tốt hơn cho stateless)

```
hash(userId) % numInstances = targetInstance

User 123 → hash(123) % 3 = 0 → Instance A (always)
User 456 → hash(456) % 3 = 2 → Instance C (always)

Khi add/remove instance:
% numInstances thay đổi → nhiều users bị rehash
→ Dùng consistent hashing ring để minimize disruption
```

---

## 7. Production Considerations

### 7.1 Health-based Routing

```
Chỉ route đến healthy instances:
Instance A: UP    → receives traffic
Instance B: DOWN  → no traffic
Instance C: UNKNOWN → may receive traffic (grace period)

Cần:
- Liveness probe: is the process alive?
- Readiness probe: is the instance ready to receive traffic?
- Startup probe: has the app finished starting?
```

### 7.2 Slow Start (Warmup)

```
New instance thêm vào cluster:
- JVM warm-up cần thời gian (JIT compilation)
- Cache chưa được populated
- Connection pool chưa warm

Solution: Start với low weight, tăng dần
t=0s:  weight=1 (ít traffic)
t=30s: weight=3
t=60s: weight=10 (full weight)
```

### 7.3 Circuit Breaker + LB

```
LB request → Instance A [slow/failing]
           → Circuit Breaker: OPEN (fail-fast)
           → LB automatically routes to B, C
           → Instance A gets time to recover
           → Circuit Breaker: HALF-OPEN (test request)
           → If OK: CLOSED, back to rotation
```

### 7.4 Metrics để Monitor

```
Per-instance metrics:
- Request rate (RPS)
- Error rate (%)
- Latency p50, p95, p99
- Active connections
- JVM heap usage

LB metrics:
- Traffic distribution (% per instance)
- Health check failures
- Circuit breaker state
- Retry count
```

---

## 8. Anti-patterns

### ❌ Anti-pattern 1: Not Accounting for Slow Instances

```
Round Robin với một instance đang slow:
Instance A: fast (10ms)
Instance B: SLOW (5000ms) - memory leak, GC pause
Instance C: fast (10ms)

Round Robin: A B C A B C...
1/3 requests stuck on B!

Solution: Least Connections hoặc Outlier Detection
```

### ❌ Anti-pattern 2: Sticky Sessions + Stateful Service

```
User A → Instance 1 (session stored locally)
Instance 1 restarts → session LOST!

Solution: Externalize session state (Redis, Hazelcast)
Sau đó không cần sticky sessions nữa.
```

### ❌ Anti-pattern 3: Không Retry trên Instance khác

```
// SAI: Retry trên cùng instance (có thể down)
@Retryable(maxAttempts = 3)
public Product getProduct(String id) {
    return loadBalancedRestTemplate.getForObject("http://product-service/...", Product.class);
}
// Nếu instance được chọn down → tất cả 3 retries fail trên cùng instance!

// ĐÚNG: Cấu hình LB retry on next instance
spring.cloud.loadbalancer.retry.max-retries-on-next-service-instance: 2
```

### ❌ Anti-pattern 4: Không Monitor Traffic Distribution

```
Không biết:
- Instance A nhận 80% traffic do bug trong LB config
- Instance B/C idle

Luôn export và monitor metrics:
- http_server_requests_per_instance
- connection_count_per_instance
```

---

## 9. Câu hỏi phỏng vấn

### Q1: Round Robin và Least Connections khác nhau thế nào?
**Trả lời:**
> Round Robin phân phối requests theo thứ tự luân phiên, mỗi instance nhận lượng requests bằng nhau. Least Connections route đến instance có ít active connections nhất. Round Robin tốt cho stateless services với uniform request time. Least Connections tốt hơn khi requests có varying processing time (uploads vs queries) - tránh overload instances đang xử lý long requests.

### Q2: Weighted Load Balancing dùng khi nào?
**Trả lời:**
> Khi instances có capacity khác nhau (khác CPU, RAM, phần cứng), hoặc khi deploy rolling update (new version bắt đầu với weight thấp, tăng dần khi confident). Cũng dùng cho canary deployment: 95% traffic đến stable, 5% đến canary version.

### Q3: Sticky sessions có vấn đề gì?
**Trả lời:**
> Phân phối không đều (một số instances overload), không hoạt động khi instance down (session lost), scale out khó hơn. Best practice: externalize session state vào Redis/Memcached, không cần sticky sessions. Khi buộc phải dùng sticky sessions (legacy), dùng consistent hashing để minimize disruption khi scale.

### Q4: Spring Cloud LoadBalancer vs Nginx?
**Trả lời:**
> Spring Cloud LB là client-side, không cần extra hop, tích hợp tốt với Eureka discovery, flexible algorithms via Java code. Nginx là server-side proxy, ngôn ngữ agnostic, cần separate deployment. Trong K8s thường dùng Nginx Ingress hoặc Envoy. Trong non-K8s microservices, Spring Cloud LB với Eureka là lựa chọn phổ biến.

### Q5: Làm sao implement canary deployment với LB?
**Trả lời:**
> Dùng Weighted LB: stable version weight=95, canary weight=5. Hoặc dùng header-based routing: requests với header X-Canary=true đến canary instances. Monitor error rate và latency của canary. Nếu OK, tăng weight dần. Nếu bad, weight=0 để drain traffic. Tools: Argo Rollouts, Flagger (K8s), hoặc custom Spring Cloud LB.

---

## Chạy ví dụ

```bash
# Start Eureka (từ lesson 04)
# Start 3 backend instances với weights khác nhau
cd backend-service
PORT=8081 WEIGHT=3 mvn spring-boot:run &
PORT=8082 WEIGHT=2 mvn spring-boot:run &
PORT=8083 WEIGHT=1 mvn spring-boot:run &

# Start load balancer demo
cd load-balancer-demo && mvn spring-boot:run

# Test: gọi nhiều lần và xem distribution
for i in {1..10}; do
  curl http://localhost:8080/demo/call
  echo ""
done

# Xem stats
curl http://localhost:8080/demo/stats

# Docker Compose (bao gồm 3 backend instances)
docker-compose up --build
```
