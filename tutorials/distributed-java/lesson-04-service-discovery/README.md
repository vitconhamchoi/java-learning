# Lesson 04: Service Discovery với Eureka

## Mục lục
1. [Service Discovery là gì?](#1-service-discovery-là-gì)
2. [Client-side vs Server-side Discovery](#2-client-side-vs-server-side-discovery)
3. [Netflix Eureka](#3-netflix-eureka)
4. [Health Check Integration](#4-health-check-integration)
5. [Service Metadata](#5-service-metadata)
6. [Zone-aware Routing](#6-zone-aware-routing)
7. [Production Considerations](#7-production-considerations)
8. [Anti-patterns](#8-anti-patterns)
9. [Câu hỏi phỏng vấn](#9-câu-hỏi-phỏng-vấn)

---

## 1. Service Discovery là gì?

Trong microservices, các services cần biết địa chỉ (host:port) của nhau để giao tiếp. Trong môi trường dynamic (containers, cloud, auto-scaling), địa chỉ IP thay đổi liên tục.

### Vấn đề với Hardcoded Addresses

```
# KHÔNG dùng trong production!
inventory.service.url=192.168.1.100:8081

Vấn đề:
- Service scale up: thêm instance → phải update config
- Service restart: IP mới → config lỗi thời
- Rolling deployment: cùng lúc nhiều versions
- Kubernetes pod: IP thay đổi mỗi lần restart
```

### Service Discovery giải quyết

```
Service Registry (Eureka Server):
┌──────────────────────────────────┐
│  Registry                         │
│  product-service → [10.0.0.1:8081,│
│                     10.0.0.2:8081] │
│  order-service   → [10.0.0.3:8080] │
│  payment-service → [10.0.0.4:9090] │
└──────────────────────────────────┘
        ▲ Register/Heartbeat          ▲ Lookup
        │                            │
[product-service]              [order-service]
  "I'm alive at               "Give me address
   10.0.0.1:8081!"             of product-service"
```

---

## 2. Client-side vs Server-side Discovery

### 2.1 Client-side Discovery (Eureka pattern)

```
Client ──query──► Registry    [1. Query registry for service instances]
Client ◄──list─── Registry    [2. Get list: [10.0.0.1:8081, 10.0.0.2:8081]]
Client ──balance────────────► Server [3. Client picks instance (round-robin, etc.)]

Pros: No extra hop, client controls LB algorithm
Cons: Client needs discovery logic, tightly coupled to registry
```

### 2.2 Server-side Discovery (Kubernetes, AWS ALB pattern)

```
Client ──request──► Load Balancer ──lookup──► Registry
                        │
                        ├──────────────────────► Server 1
                        └──────────────────────► Server 2

Pros: Client simple (no discovery logic), LB centralized
Cons: Extra network hop, LB is potential bottleneck/SPOF
```

| Feature | Client-side | Server-side |
|---------|-------------|-------------|
| Discovery logic | In client | In LB/proxy |
| Network hops | Direct | Extra hop via LB |
| LB algorithms | Flexible (client controls) | Centralized |
| Examples | Eureka + Ribbon/Spring LB | Kubernetes Service, AWS ALB |
| Client complexity | Higher | Lower |

---

## 3. Netflix Eureka

### 3.1 Kiến trúc Eureka

```
┌────────────────────────────────────────────┐
│              Eureka Server                  │
│                                            │
│  Service Registry:                         │
│  ┌──────────────────────────────────┐     │
│  │ "product-service"               │     │
│  │   Instance 1: 10.0.0.1:8081     │     │
│  │   Instance 2: 10.0.0.2:8081     │     │
│  │   lastHeartbeat: 10:00:29        │     │
│  ├──────────────────────────────────┤     │
│  │ "order-service"                 │     │
│  │   Instance 1: 10.0.0.3:8080     │     │
│  │   lastHeartbeat: 10:00:28        │     │
│  └──────────────────────────────────┘     │
└────────────────────────────────────────────┘
         ▲ Register        ▲ Register
         │ Heartbeat/30s   │ Heartbeat/30s
         │                 │
[product-service]     [order-service]
  8081 (instance 1)     8080
  8082 (instance 2)
         │                 │
         └─────────────────┘
         Fetch registry (30s)
         → Contact product-service directly
```

### 3.2 Eureka Lifecycle

```
1. STARTUP:
   Service → POST /eureka/apps/{appName}
   Body: {ipAddr, port, status: STARTING}

2. REGISTRATION COMPLETE:
   Service → PUT /eureka/apps/{appName}/{instanceId}?status=UP

3. HEARTBEAT (mỗi 30 giây):
   Service → PUT /eureka/apps/{appName}/{instanceId}
   Eureka: Reset expiration timer

4. FETCH REGISTRY (mỗi 30 giây):
   Service → GET /eureka/apps (full refresh)
   hoặc GET /eureka/apps/delta (incremental)

5. DEREGISTRATION:
   Service → DELETE /eureka/apps/{appName}/{instanceId}
   (hoặc timeout: 3 missed heartbeats = 90s)
```

### 3.3 Self-preservation Mode

```
Self-preservation kích hoạt khi:
- Số heartbeats nhận được < ngưỡng kỳ vọng trong 1 phút
- Mặc định ngưỡng: 85% expected heartbeats

Ví dụ: 10 instances, mỗi cái heartbeat mỗi 30s
Expected: 20 heartbeats/phút (10 * 2)
Threshold: 17 heartbeats/phút (85%)
Nếu chỉ nhận 15 → Self-preservation ON!

Khi ON: Eureka KHÔNG evict instances dù chúng timeout
Tại sao? Network partition có thể khiến heartbeats không đến được
Nếu evict khi partition → toàn bộ instances bị remove (sai!)

Trong development: thường disable để instances removed nhanh hơn
Trong production: giữ ON để safety
```

---

## 4. Health Check Integration

### 4.1 Eureka + Spring Actuator

```
Spring Boot Actuator /actuator/health:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}

Eureka dùng status này để:
- Mark instance UP/DOWN/OUT_OF_SERVICE
- Routes traffic chỉ đến UP instances
```

### 4.2 Custom Health Indicator

```java
@Component
public class ExternalDependencyHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        try {
            // Kiểm tra external dependency (database, cache, etc.)
            externalService.ping();
            return Health.up()
                .withDetail("externalService", "Connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## 5. Service Metadata

### 5.1 Metadata Map

```yaml
eureka:
  instance:
    metadata-map:
      version: "1.0"          # API version
      zone: "us-east-1a"      # Availability zone
      weight: "3"             # Load balancer weight
      build: "abc123"         # Git commit hash
      team: "platform"        # Owning team
```

### 5.2 Sử dụng Metadata

```java
// Đọc metadata từ instance info
DiscoveryClient discoveryClient;
List<ServiceInstance> instances = discoveryClient.getInstances("product-service");

for (ServiceInstance instance : instances) {
    String version = instance.getMetadata().get("version");
    String zone = instance.getMetadata().get("zone");
    String weight = instance.getMetadata().get("weight");

    // Dùng metadata để filter: chỉ call version 2.0
    if ("2.0".equals(version)) {
        callService(instance);
    }
}
```

---

## 6. Zone-aware Routing

### 6.1 Zone-aware Load Balancing

```
us-east-1a:
┌────────────────────────────┐
│ product-service instance 1 │ ← Prefer này nếu order-service cũng ở east-1a
│ zone: "us-east-1a"         │   (reduce cross-AZ data transfer cost)
└────────────────────────────┘

us-west-2a:
┌────────────────────────────┐
│ product-service instance 2 │ ← Fallback khi east-1a instances down
│ zone: "us-west-2a"         │
└────────────────────────────┘

order-service (us-east-1a) → prefer product-service (us-east-1a)
                            → fallback to us-west-2a nếu east-1a down
```

---

## 7. Production Considerations

### 7.1 Eureka Cluster (High Availability)

```yaml
# Eureka Server 1
eureka:
  client:
    service-url:
      defaultZone: http://eureka2:8762/eureka/,http://eureka3:8763/eureka/

# Eureka Server 2
eureka:
  client:
    service-url:
      defaultZone: http://eureka1:8761/eureka/,http://eureka3:8763/eureka/

# Client - đăng ký với tất cả Eureka servers
eureka:
  client:
    service-url:
      defaultZone: http://eureka1:8761/eureka/,http://eureka2:8762/eureka/,http://eureka3:8763/eureka/
```

### 7.2 Registry Caching

```
Client cache registry locally:
- Fetch mỗi 30s (full) hoặc khi có delta
- Nếu Eureka server down, client vẫn dùng cached registry
- Eureka server down → không register mới nhưng existing services vẫn giao tiếp

Kubernetes alternative:
- CoreDNS: Service discovery qua DNS
- Service: ClusterIP với kube-proxy load balancing
- Không cần Eureka trong K8s environment
```

### 7.3 Eureka vs Consul vs Kubernetes

| Feature | Eureka | Consul | Kubernetes |
|---------|--------|--------|------------|
| Discovery | Self-registration | Agent-based | Built-in |
| Health check | Heartbeat | TCP/HTTP/Script | Liveness/Readiness |
| KV Store | No | Yes | ConfigMap/Secret |
| Service Mesh | No | Yes (Connect) | Istio/Linkerd |
| DNS interface | No | Yes | Yes |
| Open Source | Yes | Yes | Yes |

---

## 8. Anti-patterns

### ❌ Anti-pattern 1: Calling Eureka API Directly

```java
// SAI: Gọi Eureka REST API trực tiếp
RestTemplate rt = new RestTemplate();
String apps = rt.getForObject("http://localhost:8761/eureka/apps", String.class);
// Parse XML manually... terrible!

// ĐÚNG: Dùng DiscoveryClient abstraction
@Autowired DiscoveryClient discoveryClient;
List<ServiceInstance> instances = discoveryClient.getInstances("product-service");
```

### ❌ Anti-pattern 2: Not Handling Instance Removal Delay

```
Service down:
T=0:   Service stops
T=30s: First missed heartbeat
T=60s: Second missed heartbeat
T=90s: Third missed heartbeat → Eureka evicts
T=90s+30s: Clients refresh registry, see removal

Trong 2 phút, clients vẫn có thể route đến dead instance!
PHẢI implement retry + circuit breaker để handle failures trong window này.
```

### ❌ Anti-pattern 3: Disable Self-preservation in Production

```yaml
# SAI cho production:
eureka:
  server:
    enable-self-preservation: false  # Nguy hiểm!
    # Network partition → tất cả instances bị evict!

# OK cho development (nhanh hơn):
eureka:
  server:
    enable-self-preservation: false  # Chỉ dùng ở dev
    eviction-interval-timer-in-ms: 3000
```

### ❌ Anti-pattern 4: Single Eureka Server

```
Single Eureka = Single Point of Failure!
Eureka down → các services không thể register/deregister
(existing connections vẫn work do client cache, nhưng scaling không hoạt động)

Luôn deploy ít nhất 3 Eureka servers trong production (odd number cho quorum).
```

---

## 9. Câu hỏi phỏng vấn

### Q1: Client-side và Server-side discovery khác gì nhau?
**Trả lời:**
> Client-side: Client query registry, lấy danh sách instances, tự chọn instance và gọi trực tiếp (Eureka + Spring Cloud LB). Server-side: Client gọi đến LB/proxy, proxy query registry và forward đến backend (Kubernetes Service, AWS ALB). Client-side linh hoạt hơn về LB algorithm, Server-side đơn giản hơn cho client.

### Q2: Eureka Self-preservation mode là gì?
**Trả lời:**
> Khi Eureka nhận ít heartbeats hơn ngưỡng (85% mặc định), nó bật self-preservation và KHÔNG evict instances. Lý do: network partition có thể làm heartbeats không đến được Eureka, nếu evict → xóa toàn bộ cluster sai. Trong production nên giữ ON; trong dev có thể OFF để instances removed nhanh hơn.

### Q3: Có những thay thế cho Eureka không?
**Trả lời:**
> Consul: Full-featured với KV store, DNS, health checks; Zookeeper: CP system cho config và discovery; etcd: Sử dụng trong Kubernetes; Kubernetes Service: Built-in cho K8s environment (dùng CoreDNS và kube-proxy). Netflix đã "maintenance mode" Eureka nên nhiều teams chuyển sang Consul hoặc K8s native discovery.

### Q4: Làm sao handle khi Eureka server down?
**Trả lời:**
> Client cache registry locally mỗi 30s. Nếu Eureka down, client dùng cached data để tiếp tục route traffic. Các services đã registered vẫn có thể giao tiếp với nhau. Vấn đề: không register được services mới, không deregister services down. Giải pháp: cluster 3 Eureka servers, circuit breaker ở client side.

### Q5: @LoadBalanced RestTemplate hoạt động như thế nào?
**Trả lời:**
> @LoadBalanced là annotation marker. Spring Cloud thêm LoadBalancerInterceptor vào RestTemplate. Khi RestTemplate gọi URL với service name (http://product-service/api/), interceptor intercept, query DiscoveryClient để lấy instances, áp dụng LB algorithm (round-robin mặc định), thay service name bằng actual host:port.

---

## Chạy ví dụ

```bash
# 1. Start Eureka Server
cd eureka-server && mvn spring-boot:run

# 2. Start Product Service (instance 1)
cd product-service && PORT=8081 mvn spring-boot:run

# 3. Start Product Service (instance 2)  
cd product-service && PORT=8082 mvn spring-boot:run

# 4. Start Order Service
cd order-service && mvn spring-boot:run

# Kiểm tra Eureka dashboard
open http://localhost:8761

# Test service discovery
curl http://localhost:8080/orders/demo/discovery/P001
curl http://localhost:8080/orders/demo/loadbalanced/P001

# Docker Compose
docker-compose up --build
```
