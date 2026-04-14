# Lesson 01: Distributed Systems Fundamentals

## Mục lục
1. [Distributed Systems là gì?](#1-distributed-systems-là-gì)
2. [Tại sao cần Distributed Systems?](#2-tại-sao-cần-distributed-systems)
3. [CAP Theorem](#3-cap-theorem)
4. [Consistency Models](#4-consistency-models)
5. [Failure Modes](#5-failure-modes)
6. [Two Generals Problem](#6-two-generals-problem)
7. [FLP Impossibility](#7-flp-impossibility)
8. [Logical Clocks](#8-logical-clocks)
9. [Câu hỏi phỏng vấn](#9-câu-hỏi-phỏng-vấn)
10. [Production Considerations](#10-production-considerations)
11. [Anti-patterns](#11-anti-patterns)

---

## 1. Distributed Systems là gì?

**Distributed System** (Hệ thống phân tán) là tập hợp các máy tính độc lập (nodes) hoạt động cùng nhau và xuất hiện với người dùng cuối như một hệ thống duy nhất, thống nhất.

### Đặc điểm chính:
- **Concurrency**: Các components thực thi đồng thời
- **No global clock**: Không có đồng hồ toàn cục, thời gian là tương đối
- **Independent failures**: Các thành phần có thể fail độc lập
- **Message passing**: Giao tiếp qua network (không qua shared memory)

### Ví dụ thực tế:
| Hệ thống | Loại | Mô tả |
|----------|------|--------|
| Google Search | Distributed | Hàng nghìn servers xử lý queries |
| Netflix | Distributed | Microservices streaming video |
| Bitcoin | P2P Distributed | Blockchain phi tập trung |
| Kubernetes | Distributed | Container orchestration cluster |
| Apache Kafka | Distributed | Message streaming platform |

---

## 2. Tại sao cần Distributed Systems?

### 2.1 Scalability (Khả năng mở rộng)
```
Vertical Scaling (Scale Up):          Horizontal Scaling (Scale Out):
┌─────────────────────┐               ┌───────┐ ┌───────┐ ┌───────┐
│  Server 1           │               │ Srv 1 │ │ Srv 2 │ │ Srv 3 │
│  CPU: 128 cores     │     vs        │ 8 CPU │ │ 8 CPU │ │ 8 CPU │
│  RAM: 2TB           │               │ 32GB  │ │ 32GB  │ │ 32GB  │
│  Giá: $$$$$         │               │ $100  │ │ $100  │ │ $100  │
└─────────────────────┘               └───────┘ └───────┘ └───────┘
   Giới hạn phần cứng               Thêm nodes dễ dàng, chi phí tuyến tính
```

### 2.2 Fault Tolerance (Chịu lỗi)
- **Single Point of Failure (SPOF)**: Hệ thống tập trung có một điểm failure
- **Redundancy**: Phân tán tạo ra bản sao dự phòng
- **High Availability**: 99.99% uptime = ~52 phút downtime/năm

### 2.3 Performance (Hiệu suất)
- **Data locality**: Dữ liệu gần với user (CDN, geo-distribution)
- **Parallel processing**: Xử lý song song nhiều tác vụ
- **Low latency**: Giảm độ trễ cho người dùng toàn cầu

### 2.4 Cost Efficiency
- **Commodity hardware**: Dùng phần cứng giá rẻ thay vì mainframe
- **Cloud elasticity**: Tăng/giảm resources theo nhu cầu
- **Pay-as-you-go**: Chỉ trả tiền cho những gì dùng

---

## 3. CAP Theorem

**CAP Theorem** (Brewer's Theorem, 2000) phát biểu rằng một hệ thống phân tán **không thể đảm bảo cùng lúc** cả ba thuộc tính:

```
                    C
                 Consistency
                /     \
               /       \
              /         \
         CP  /     ?     \  CA
            /             \
           /_______________\
          A                 P
     Availability      Partition
                       Tolerance

   CP: MongoDB, HBase, Zookeeper, Redis Cluster
   AP: Cassandra, CouchDB, DynamoDB, Riak
   CA: PostgreSQL (single node), MySQL (single node)
       [Thực tế CA không tồn tại trong distributed systems]
```

### 3.1 Consistency (Nhất quán)
Sau khi write thành công, mọi read phải trả về giá trị mới nhất.
```
Node A  ──write(x=1)──►  Node A [x=1]
                              │
                              │ sync
                              ▼
Node B  ──read(x)──►     Node B [x=1]  ✓ Consistent
```

### 3.2 Availability (Khả dụng)
Mọi request phải nhận được response (không phải lỗi), kể cả khi có node bị lỗi.
```
Request ──► Node A [DOWN]
               │
               │ redirect
               ▼
            Node B [x=0]  ──► Response: x=0
            (có thể stale)       [available nhưng có thể inconsistent]
```

### 3.3 Partition Tolerance (Chịu phân vùng)
Hệ thống tiếp tục hoạt động kể cả khi network partition xảy ra.
```
┌─────────────────────────────────────┐
│          Network Partition          │
│                                     │
│  [Node A] ──╳── [Node B] [Node C]  │
│    Write        Can't sync!         │
│                                     │
└─────────────────────────────────────┘
```

### 3.4 Ví dụ thực tế

#### CP Systems (Consistency + Partition Tolerance):
**MongoDB** (với Write Concern majority):
```
Client ──write(x=1)──► Primary ──────► Replica 1 ✓
                                  └──► Replica 2 ✓
                                  ACK khi majority confirm
                         [Từ chối write nếu không đủ replicas]
```

**Zookeeper**:
- Sử dụng ZAB (Zookeeper Atomic Broadcast) protocol
- Leader phải có quorum (majority) để commit
- Từ chối reads/writes nếu không có quorum

#### AP Systems (Availability + Partition Tolerance):
**Cassandra**:
```
Client ──write(x=1)──► Node A ──write locally──► ACK immediately
                           │
                    (later, async)
                           ▼
                       Node B, C sync eventually
                  [Luôn accept writes, sync sau]
```

**DynamoDB**:
- Eventually consistent reads (mặc định)
- Strongly consistent reads (tùy chọn, cao hơn latency)

### 3.5 PACELC Extension
PACELC mở rộng CAP để xét cả trường hợp không có partition:

```
If Partition:  choose A or C
Else:          choose L (Latency) or C (Consistency)

PACELC Classifications:
- DynamoDB:  PA/EL  (AP when partition, favor latency normally)
- Cassandra: PA/EL
- MongoDB:   PC/EC  (CP when partition, favor consistency normally)
- MySQL:     PC/EC  (single master, strong consistency)
```

---

## 4. Consistency Models

### 4.1 Strong Consistency (Nhất quán mạnh)
```
Timeline:
T1: Client A writes x=1
T2: Client B reads x    → PHẢI thấy x=1

Ví dụ: Bank balance, stock trading
```

### 4.2 Eventual Consistency (Nhất quán cuối cùng)
```
Timeline:
T1: Client A writes x=1 to Node 1
T2: Client B reads x from Node 2    → có thể thấy x=0 (stale)
T3: Nodes sync
T4: Client B reads x from Node 2    → thấy x=1

Ví dụ: DNS propagation, shopping cart, likes/views counter
```

### 4.3 Causal Consistency (Nhất quán nhân quả)
```
If A happens before B (A → B), then everyone must see A before B.

Process 1: write(x=1)
Process 2: read(x=1) → write(y=2)   [y=2 causally depends on x=1]
Process 3: must see x=1 BEFORE y=2

Ví dụ: Comment threads, version control
```

### 4.4 Read-your-writes Consistency
```
Client A: write(x=1) → subsequent reads MUST return x=1
(có thể inconsistent với other clients)

Ví dụ: User profile updates, session data
```

### 4.5 Monotonic Read Consistency
```
If Client sees x=2, subsequent reads must see x≥2 (never x=1)
Không được "đi lùi" về giá trị cũ

Ví dụ: Timeline feeds, message history
```

---

## 5. Failure Modes

### 5.1 Network Partition
```
Datacenter A                    Datacenter B
┌──────────┐                   ┌──────────┐
│ Node 1   │╔═══════════╗      │ Node 3   │
│ Node 2   │║  NETWORK  ║      │ Node 4   │
│          │║  FAILURE  ║      │          │
└──────────┘╚═══════════╝      └──────────┘
   Vẫn hoạt động                  Vẫn hoạt động
   nhưng split brain!
```

**Hậu quả**: Split-brain scenario, conflicting updates, data divergence.
**Giải pháp**: Fencing tokens, STONITH (Shoot The Other Node In The Head), consensus protocols.

### 5.2 Crash Failures (Fail-stop)
Node ngừng hoạt động hoàn toàn và không bao giờ gửi message sai.
```
Node A: [Working] → [CRASH] → [Silent forever]
```
- Dễ detect (timeout, heartbeat)
- Ví dụ: Server bị tắt nguồn

### 5.3 Byzantine Failures
Node hoạt động sai, gửi messages không nhất quán hoặc độc hại.
```
Node A (Byzantine):
  → Sends x=1 to Node B
  → Sends x=2 to Node C
  → Sends nothing to Node D
```
- Cần Byzantine Fault Tolerant (BFT) protocols
- Cần ít nhất 3f+1 nodes để tolerate f Byzantine failures
- Ví dụ: Blockchain consensus (PBFT), hardware faults

### 5.4 Omission Failures
Node bỏ qua (drop) một số messages.
```
Node A ──msg1──► Node B ✓
Node A ──msg2──► [dropped] ✗
Node A ──msg3──► Node B ✓
```

### 5.5 Timing Failures
Node phản hồi nhưng quá chậm (vượt deadline).
```
Request ──► Node A [processing...]
   timeout! ──► retry ──► Node A [responds with old result]
```

---

## 6. Two Generals Problem

**Vấn đề**: Hai tướng quân muốn phối hợp tấn công cùng lúc, nhưng sứ giả qua lãnh thổ địch có thể bị bắt.

```
┌─────────────┐    sứ giả    ┌─────────────┐
│  Tướng A    │─────────────►│  Tướng B    │
│             │◄─────────────│             │
│ "Tôi sẽ    │  (có thể bị  │ "Tôi đồng  │
│  tấn công  │   bắt bất    │  ý, tấn    │
│  lúc 5pm"  │   kỳ lúc    │  công 5pm" │
└─────────────┘    nào)      └─────────────┘
```

**Tại sao không thể giải quyết:**
- Tướng A gửi kế hoạch → không biết B nhận được chưa
- Tướng B gửi ACK → không biết A nhận được chưa
- Tướng A gửi ACK của ACK → không biết B nhận được chưa
- *Vô hạn*

**Bài học cho distributed systems:**
- **Không thể đảm bảo** message delivery trong unreliable network
- TCP handshake giải quyết được vấn đề practical (nhưng không hoàn toàn lý thuyết)
- Cần thiết kế hệ thống **idempotent** để handle duplicate messages
- **At-least-once** vs **At-most-once** vs **Exactly-once** delivery

---

## 7. FLP Impossibility

**FLP Impossibility** (Fischer, Lynch, Paterson - 1985): Trong một hệ thống distributed **asynchronous** với ít nhất một node có thể **crash**, không thể đạt được **consensus** một cách deterministic.

```
Điều kiện:
✗ Asynchronous network (không giới hạn độ trễ)
✗ At least 1 node có thể crash
✗ Deterministic algorithm

→ KHÔNG thể đảm bảo consensus!
```

**Tại sao quan trọng:**
- Mọi consensus algorithm đều phải trade-off
- Cần giả định về timing (partial synchrony)

**Giải pháp thực tế:**
| Protocol | Cách vượt qua FLP |
|----------|-------------------|
| Paxos | Randomization, timeouts |
| Raft | Leader election với term |
| PBFT | Bounded number of failures |
| Zab (Zookeeper) | Timeout-based leader |

---

## 8. Logical Clocks

### 8.1 Vấn đề với Physical Time
```
Node A: T=10:00:00.001  ──────────────────
Node B: T=10:00:00.003  ──────────────────
                              NTP sync có thể có sai số ms đến giây!
```

Physical time không reliable vì:
- Clock drift
- NTP synchronization có sai số
- Leap seconds

### 8.2 Lamport Clock
**Quy tắc:**
1. Mỗi local event: `time++`
2. Trước khi send: `time++`, attach time vào message
3. Khi receive: `time = max(local, msg_time) + 1`

```
Process A:    1    2    3              7    8
                   │                  ▲
                  send               recv
                   │                  │
Process B:              4    5    6   │
                             │        │
                            send──────┘
                             │
Process C:                        depends on B
```

**Hạn chế**: Lamport Clock chỉ đảm bảo: `A → B ⟹ LC(A) < LC(B)`
Nhưng KHÔNG đảm bảo ngược lại: `LC(A) < LC(B) ⟹ A → B`

### 8.3 Vector Clock
**Mỗi process giữ vector [t1, t2, ..., tn] cho n processes**

```
Process 1: [1,0,0]  [2,0,0]  [3,2,1]
                │              ▲
               send            recv([2,2,0])
                │              │
Process 2: [0,1,0]  [0,2,0]──┘
                         │
                        send
```

**Happens-before**: `A → B ⟺ ∀i: VC(A)[i] ≤ VC(B)[i] AND ∃j: VC(A)[j] < VC(B)[j]`

**Concurrent**: `A ∥ B ⟺ !(A → B) AND !(B → A)`

---

## 9. Câu hỏi phỏng vấn

### Q1: CAP Theorem là gì? Hãy giải thích với ví dụ.
**Trả lời mẫu:**
> CAP Theorem nói rằng distributed system chỉ có thể đảm bảo 2 trong 3: Consistency, Availability, Partition Tolerance. Vì Partition Tolerance là bắt buộc trong network thực tế, ta chọn giữa CP và AP.
>
> Ví dụ: Cassandra chọn AP - luôn available, eventual consistent. MongoDB với write concern majority chọn CP - consistent nhưng từ chối write nếu không có quorum.

### Q2: Khi nào dùng Eventual Consistency vs Strong Consistency?
**Trả lời mẫu:**
> **Strong Consistency**: Bank transactions, stock trading, inventory count (khi data phải chính xác 100%)
>
> **Eventual Consistency**: Social media likes/views, shopping cart (không critical), DNS, CDN cache (latency quan trọng hơn consistency)

### Q3: Lamport Clock và Vector Clock khác nhau thế nào?
**Trả lời mẫu:**
> **Lamport Clock**: Single integer, lightweight. Chỉ chứng minh được happens-before một chiều (A→B ⟹ LC(A)<LC(B)), không phát hiện được concurrent events.
>
> **Vector Clock**: Array of integers (1 per process). Phát hiện được cả concurrent events. Nhưng tốn memory O(n) và bandwidth.

### Q4: FLP Impossibility ảnh hưởng thế nào đến thiết kế hệ thống?
**Trả lời mẫu:**
> FLP chứng minh consensus không thể đạt được trong async network với crash failures. Thực tế, Paxos/Raft giải quyết bằng cách assume partial synchrony (timeout có nghĩa là failure), và dùng leader election để tránh vòng lặp vô hạn.

### Q5: Split-brain là gì? Làm sao ngăn chặn?
**Trả lời mẫu:**
> Split-brain xảy ra khi network partition khiến cluster bị chia thành 2 nhóm, cả hai đều nghĩ mình là primary. Ngăn chặn bằng:
> - Quorum voting (majority wins)
> - Fencing tokens
> - STONITH (kill losing side)
> - External arbitrator (Zookeeper)

---

## 10. Production Considerations

### 10.1 Monitoring & Observability
```yaml
# Metrics cần monitor:
- Network latency (p50, p95, p99)
- Packet loss rate
- Consensus round-trip time
- Replication lag
- Failed node count
- Partition events
```

### 10.2 Testing Chaos
- **Chaos Monkey** (Netflix): Kill random nodes
- **Chaos Engineering**: Simulate network latency, packet loss
- **Jepsen Testing**: Verify consistency guarantees

### 10.3 Capacity Planning
```
Đọc kỹ SLA:
- 99.9%   uptime = 8.7 giờ downtime/năm
- 99.99%  uptime = 52 phút downtime/năm
- 99.999% uptime = 5 phút downtime/năm

Đặt ra replication factor dựa trên SLA.
```

### 10.4 Network Design
```
Latency thực tế (approximate):
- Same rack:      ~0.1ms
- Same datacenter: ~0.5ms
- Cross region:   ~10-100ms
- Cross continent: ~100-300ms

Thiết kế hệ thống phải account for worst case!
```

### 10.5 Idempotency
Mọi operation nên idempotent để safe retry:
```java
// Idempotency key pattern
POST /payments
{
  "idempotency_key": "uuid-v4",
  "amount": 100,
  "to": "account-123"
}
// Gọi nhiều lần với cùng key → kết quả giống nhau
```

---

## 11. Anti-patterns

### ❌ Anti-pattern 1: Assuming Network is Reliable
```java
// SAI: Không handle network failures
ProductDto product = httpClient.get("/products/" + id);
return product.getPrice(); // NullPointerException if request fails
```
```java
// ĐÚNG: Handle với fallback và retry
ProductDto product = httpClient.get("/products/" + id)
    .retry(3)
    .onErrorReturn(defaultProduct);
```

### ❌ Anti-pattern 2: Distributed Monolith
```
Microservices nhưng tightly coupled:
Service A ──synchronous──► Service B ──synchronous──► Service C
                                                         │
         ◄───────────────────────────────────────────────┘
[Nếu C down → B fail → A fail: worse than monolith!]
```
**Giải pháp**: Async messaging, circuit breaker, bulkhead pattern.

### ❌ Anti-pattern 3: Ignoring Clock Skew
```java
// SAI: Dùng System.currentTimeMillis() để ordering events
if (event1.timestamp < event2.timestamp) {
    process(event1, event2); // Clock skew có thể reverse order!
}
```
```java
// ĐÚNG: Dùng Logical Clocks hoặc Hybrid Logical Clocks (HLC)
if (vectorClock.happensBefore(event1.vc, event2.vc)) {
    process(event1, event2);
}
```

### ❌ Anti-pattern 4: Chatty Microservices
```
Một request user → 50 internal service calls
→ High latency, cascading failures risk
```
**Giải pháp**: Aggregate API, Backend for Frontend (BFF), GraphQL.

### ❌ Anti-pattern 5: No Backpressure
```java
// SAI: Consumer không thể handle tốc độ của producer
while (true) {
    Message msg = queue.poll(); // Queue đầy → OutOfMemory
    processSlowly(msg);
}
```
**Giải pháp**: Reactive streams với backpressure (Project Reactor), rate limiting.

### ❌ Anti-pattern 6: Synchronous Everything
```
API Gateway ──sync──► Service A ──sync──► Service B ──sync──► DB
Total latency = sum of all calls!
```
**Giải pháp**: Async messaging (Kafka, RabbitMQ) cho non-critical paths.

---

## Tài nguyên học thêm

- 📚 **Designing Data-Intensive Applications** - Martin Kleppmann (bắt buộc đọc!)
- 📚 **Distributed Systems** - Maarten van Steen & Andrew Tanenbaum
- 🎓 MIT 6.824: Distributed Systems (lecture notes + labs online)
- 📝 Papers: Paxos Made Simple, Raft Consensus Algorithm, Dynamo: Amazon's Highly Available Key-value Store
- 🛠️ **Jepsen** (jepsen.io) - Testing distributed systems correctness

---

## Chạy Code Examples

```bash
cd lesson-01-fundamentals
mvn compile
mvn exec:java -Dexec.mainClass="com.distributed.fundamentals.LamportClock"
mvn exec:java -Dexec.mainClass="com.distributed.fundamentals.VectorClock"
mvn exec:java -Dexec.mainClass="com.distributed.fundamentals.EventualConsistencyDemo"
mvn exec:java -Dexec.mainClass="com.distributed.fundamentals.CAPTheoremDemo"
```
