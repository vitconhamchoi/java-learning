# Lesson 19: Observability (Micrometer + Prometheus + Grafana)

## Giới thiệu

Observability là khả năng hiểu trạng thái nội tại của hệ thống từ bên ngoài. Ba trụ cột: Metrics, Logs, Traces.

## Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────────┐
│                  Three Pillars                              │
├───────────────┬───────────────────┬─────────────────────────┤
│    METRICS    │      LOGS         │       TRACES            │
├───────────────┼───────────────────┼─────────────────────────┤
│ Aggregated    │ Discrete events   │ Request flows           │
│ numbers       │ with context      │ across services         │
│               │                   │                         │
│ "What is the  │ "What happened    │ "Why did request X      │
│ error rate?"  │ at 3pm?"          │ take 5 seconds?"        │
│               │                   │                         │
│ Prometheus    │ ELK Stack         │ Zipkin/Jaeger           │
│ Grafana       │ Grafana Loki      │ Grafana Tempo           │
└───────────────┴───────────────────┴─────────────────────────┘
```

## Micrometer Metric Types

### Counter
```java
// Monotonically increasing count
Counter orderCounter = Counter.builder("app.orders.total")
    .description("Total orders processed")
    .tag("status", "success")
    .register(registry);

orderCounter.increment();         // +1
orderCounter.increment(5);        // +5
// PromQL: rate(app_orders_total[5m])  → requests/second
```

### Gauge
```java
// Current value (can go up or down)
Gauge.builder("app.active_connections", connectionPool, ConnectionPool::getActive)
    .description("Active DB connections")
    .register(registry);
// PromQL: app_active_connections
```

### Timer
```java
// Measure duration + count
Timer timer = Timer.builder("app.http.request.duration")
    .description("HTTP request duration")
    .publishPercentiles(0.5, 0.95, 0.99)  // p50, p95, p99
    .publishPercentileHistogram()
    .register(registry);

timer.record(() -> {
    // measured operation
});
// PromQL: histogram_quantile(0.95, rate(app_http_request_duration_bucket[5m]))
```

### Distribution Summary
```java
// Like Timer but for non-time values
DistributionSummary orderValue = DistributionSummary.builder("app.order.value")
    .baseUnit("dollars")
    .publishPercentiles(0.5, 0.9, 0.99)
    .register(registry);

orderValue.record(149.99);
// PromQL: histogram_quantile(0.99, rate(app_order_value_bucket[5m]))
```

## Spring Boot Actuator Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: my-service    # Global tags on all metrics
      environment: production
```

```bash
# Available metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Prometheus format
curl http://localhost:8080/actuator/prometheus
```

## @Timed Annotation

```java
@Timed(value = "http.orders.create", 
       description = "Time to create an order",
       percentiles = {0.5, 0.95, 0.99})
@PostMapping("/orders")
public Order createOrder(@RequestBody OrderRequest request) {
    return orderService.create(request);
}
```

## Prometheus Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-app'
    static_configs:
      - targets: ['metrics-demo:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
```

## Grafana Dashboards

### Important PromQL Queries
```promql
# Request rate per second
rate(http_server_requests_seconds_count[5m])

# Error rate (4xx + 5xx)
sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))

# 95th percentile latency
histogram_quantile(0.95, 
    rate(http_server_requests_seconds_bucket[5m])
)

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# Custom business metrics
rate(app_orders_total{status="success"}[5m])
sum(app_revenue_total)
```

## Project Structure

```
lesson-19-observability/
├── metrics-demo/
│   ├── src/main/java/com/distributed/observability/
│   │   ├── MetricsDemoApplication.java
│   │   ├── config/MetricsConfig.java
│   │   ├── service/MetricsService.java
│   │   └── controller/MetricsController.java
│   └── src/main/resources/application.yml
├── prometheus.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Tạo orders để generate metrics
for i in {1..20}; do
  curl -s -X POST "http://localhost:8080/api/metrics/orders?productId=PROD-$(($i%5+1))&quantity=$((i%3+1))" > /dev/null
done

# Load test (nhiều requests)
curl -X POST "http://localhost:8080/api/metrics/load-test?requests=50"

# Xem summary
curl http://localhost:8080/api/metrics/summary

# Xem metrics ở Prometheus format
curl http://localhost:8080/actuator/prometheus | grep app_

# Prometheus UI
open http://localhost:9090

# Grafana
open http://localhost:3000  # admin/admin

# Prometheus trong Grafana:
# 1. Go to Configuration → Data Sources
# 2. Add Prometheus: http://prometheus:9090
# 3. Import dashboard ID: 12900 (Spring Boot)

# Useful PromQL in Prometheus UI:
# rate(app_orders_total[1m])
# app_revenue_total
# histogram_quantile(0.95, rate(app_order_processing_time_seconds_bucket[5m]))
```

## Golden Signals (SRE)

```
1. LATENCY
   - Request duration (p50, p95, p99)
   - Separate success vs error latency

2. TRAFFIC
   - Requests per second
   - Business metrics (orders/sec, users/sec)

3. ERRORS
   - Error rate (HTTP 5xx)
   - Business error rate (failed payments)

4. SATURATION
   - CPU, Memory utilization
   - Thread pool exhaustion
   - Queue depth
```

## SLI/SLO/SLA

```
SLI (Service Level Indicator):
- Availability: successful_requests / total_requests
- Latency: p99 < 500ms

SLO (Service Level Objective):
- 99.9% availability (allows 8.7 hours downtime/year)
- p99 latency < 500ms, 99.5% of the time

SLA (Service Level Agreement):
- Legal contract based on SLOs
- Penalties if violated
```

```promql
# Availability SLI
sum(rate(http_server_requests_seconds_count{status!~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))

# Error budget remaining (SLO: 99.9%)
1 - (sum(rate(http_server_requests_seconds_count{status=~"5.."}[30d])) 
     / sum(rate(http_server_requests_seconds_count[30d])))
```

## Production Tips

### 1. Cardinality Management
```java
// Bad: high cardinality tag (every user ID is unique)
counter.tag("userId", userId); // AVOID

// Good: low cardinality
counter.tag("userType", "premium"); // OK
counter.tag("endpoint", "/api/orders"); // OK
```

### 2. Alerting Rules
```yaml
# Prometheus alerting
groups:
  - name: application
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate: {{ $value | humanizePercentage }}"
          
      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 10m
        labels:
          severity: warning
```

### 3. Exemplars (Traces from Metrics)
```java
// Link metrics to traces
Timer.Sample sample = Timer.start(registry);
// ... do work ...
sample.stop(timer.withTag("traceId", currentTraceId));
```

## Interview Q&A

**Q: Tại sao cần cả Metrics, Logs, và Traces?**
A: Metrics nói "có vấn đề" (error rate tăng). Logs nói "vấn đề là gì" (exception details). Traces nói "vấn đề xảy ra ở đâu" (which service, which call). Cần cả 3 để debug hiệu quả.

**Q: Prometheus pull vs push model?**
A: Prometheus pull: scrapes targets theo schedule. Dễ phát hiện service down. Push: service gửi metrics tới collector. Tốt cho batch jobs. StatsD dùng push, Prometheus prefer pull.

**Q: Cardinality vấn đề như thế nào?**
A: Mỗi unique label combination = một time series. High cardinality (userId, requestId) = millions of series → memory explosion, query performance degradation. Giữ cardinality < 100 values per label.

**Q: p99 latency so với average?**
A: Average ẩn đi tail latency. p99 = 99% requests faster than this. Nếu p99=5s, 1% users (= 10K/1M requests/day) trải nghiệm 5s latency. Nên monitor p50, p95, p99, p999.

**Q: RED vs USE method?**
A: RED (Rate, Errors, Duration): cho request-driven services. USE (Utilization, Saturation, Errors): cho resources (CPU, memory, disk). Dùng RED cho microservices APIs, USE cho infrastructure.
