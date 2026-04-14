# Lesson 12: CQRS Pattern (Command Query Responsibility Segregation)

## Giới thiệu

CQRS tách biệt hoàn toàn đọc (Query) và ghi (Command) thành các model riêng biệt, thậm chí là các service và database riêng biệt.

## CQRS Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CQRS Architecture                        │
│                                                                 │
│  Client                                                         │
│    │                                                            │
│    ├── POST/PUT/DELETE ──► Command Side ──► Command DB          │
│    │                           │              (Write-optimized) │
│    │                           │                                │
│    │                           └── Events ──► Event Bus         │
│    │                                               │            │
│    │                                               ▼            │
│    └── GET ─────────────► Query Side ◄── Event Handler          │
│                               │              Read DB            │
│                               │         (Read-optimized)        │
│                               └── Denormalized Views            │
└─────────────────────────────────────────────────────────────────┘
```

## So sánh CRUD vs CQRS

```
CRUD (Traditional):
├── Một model cho cả đọc và ghi
├── Conflict giữa read và write concerns
├── Khó optimize riêng cho đọc/ghi
└── Locking issues khi concurrent

CQRS:
├── Command Model: normalized, consistency-focused
├── Query Model: denormalized, performance-focused
├── Độc lập scale read/write
└── Eventual consistency
```

## Command Side

```java
// Command: ý định thay đổi state
public record CreateProductCommand(String name, double price, int stock) {}

// Command Handler
@Service
public class CommandService {
    @Transactional
    public Product createProduct(Map<String, Object> request) {
        Product product = new Product();
        // ... set fields
        Product saved = productRepository.save(product);
        
        // Publish event cho Query side
        var event = new ProductEvent("PRODUCT_CREATED", saved.getId(), ...);
        eventPublisher.publishEvent(event);
        return saved;
    }
}
```

## Query Side

```java
// Read model: denormalized cho performance
@Entity
public class ProductView {
    private Long productId;
    private String name;
    private double price;
    private int stock;
    private long viewCount;     // Computed field
    private LocalDateTime lastUpdated;
}

// Query: chỉ đọc, không side effects
@Service
public class QueryService {
    public List<ProductView> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }
    
    public List<ProductView> searchByPriceRange(double min, double max) {
        return repository.findByPriceBetween(min, max);
    }
}
```

## Event-Driven Sync (Eventual Consistency)

```
Command DB ──► Domain Event ──► Event Bus ──► Query DB
              ProductCreated              Event Handler
              StockUpdated                updates read model
```

```java
@EventListener
public void onProductCreated(ProductEvent event) {
    ProductView view = new ProductView();
    view.setProductId(event.productId());
    view.setName(event.productName());
    view.setPrice(event.price());
    view.setLastUpdated(LocalDateTime.now());
    productViewRepository.save(view);
}
```

## Project Structure

```
lesson-12-cqrs/
├── command-service/         # Write side
│   ├── src/main/java/com/distributed/cqrs/
│   │   ├── CommandServiceApplication.java
│   │   ├── model/Product.java
│   │   ├── event/ProductEvent.java
│   │   ├── repository/ProductRepository.java
│   │   ├── service/CommandService.java
│   │   └── controller/CommandController.java
│   └── src/main/resources/application.yml
├── query-service/           # Read side
│   ├── src/main/java/com/distributed/cqrs/
│   │   ├── QueryServiceApplication.java
│   │   ├── model/ProductView.java
│   │   ├── repository/ProductViewRepository.java
│   │   ├── service/QueryService.java
│   │   └── controller/QueryController.java
│   └── src/main/resources/application.yml
└── docker-compose.yml
```

## Chạy ứng dụng

```bash
docker-compose up -d

# Command: Tạo sản phẩm
curl -X POST http://localhost:8080/api/commands/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"Gaming laptop","price":1500.0,"stock":50}'

# Command: Update stock
curl -X PATCH "http://localhost:8080/api/commands/products/1/stock?delta=-5"

# Sync event to query side
curl -X POST http://localhost:8081/api/queries/products/sync \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"productName":"Laptop","price":1500.0,"stock":45}'

# Query: Tìm tất cả sản phẩm
curl http://localhost:8081/api/queries/products

# Query: Tìm theo tên
curl "http://localhost:8081/api/queries/products/search?name=laptop"

# Query: Tìm theo khoảng giá
curl "http://localhost:8081/api/queries/products/search?minPrice=1000&maxPrice=2000"
```

## Advanced CQRS với Axon Framework

```java
// Command
public record PlaceOrderCommand(@TargetAggregateIdentifier String orderId, String product) {}

// Aggregate (Command side)
@Aggregate
public class OrderAggregate {
    @CommandHandler
    public OrderAggregate(PlaceOrderCommand command) {
        apply(new OrderPlacedEvent(command.orderId(), command.product()));
    }
    
    @EventSourcingHandler
    public void on(OrderPlacedEvent event) {
        this.orderId = event.orderId();
    }
}

// Projection (Query side)
@Component
public class OrderProjection {
    @EventHandler
    public void on(OrderPlacedEvent event, OrderRepository repo) {
        repo.save(new OrderView(event.orderId(), event.product()));
    }
    
    @QueryHandler
    public OrderView handle(FindOrderQuery query) {
        return orderRepository.findById(query.orderId());
    }
}
```

## Production Tips

### 1. Eventual Consistency lag
```
Vấn đề: Command committed nhưng Query chưa update
Giải pháp:
- Version-based consistency check
- Return command ID, client poll until consistent
- Optimistic UI update
```

### 2. Read Model Rebuild
```java
// Rebuild read model từ event log
@Component
public class ReadModelRebuilder {
    public void rebuild() {
        eventStore.getAllEvents()
            .forEach(event -> projectionHandler.handle(event));
    }
}
```

### 3. Multiple Read Models
```
Cùng command side, nhiều query models khác nhau:
- ProductListView: id, name, price (cho listing)
- ProductDetailView: all fields (cho detail page)
- ProductSearchIndex: Elasticsearch document
```

## Interview Q&A

**Q: Khi nào nên dùng CQRS?**
A: Khi read/write workload rất khác nhau, cần scale độc lập. Khi read model cần denormalized views phức tạp. Không nên dùng cho CRUD đơn giản - overhead quá cao.

**Q: CQRS có cần Event Sourcing không?**
A: Không bắt buộc. CQRS và Event Sourcing thường đi cùng nhưng độc lập. Có thể dùng CQRS với traditional database, hoặc Event Sourcing mà không có CQRS.

**Q: Xử lý eventual consistency như thế nào?**
A: (1) Accept it và document SLA. (2) Use optimistic UI. (3) Return version number, client waits. (4) Saga cho complex scenarios.

**Q: Read model bị corrupt, làm sao fix?**
A: Nếu dùng Event Sourcing: replay tất cả events. Nếu không: rebuild từ command DB (snapshot approach). Đây là lý do CQRS + Event Sourcing thường đi cùng.

**Q: Performance improvement của CQRS?**
A: Read side: có thể cache aggressively (immutable projections). Write side: optimize cho consistency. Có thể scale read replicas riêng mà không ảnh hưởng write.
