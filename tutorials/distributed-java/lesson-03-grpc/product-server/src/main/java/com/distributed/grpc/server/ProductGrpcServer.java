package com.distributed.grpc.server;

import com.distributed.grpc.proto.BatchCreateResponse;
import com.distributed.grpc.proto.CreateProductRequest;
import com.distributed.grpc.proto.GetProductRequest;
import com.distributed.grpc.proto.ListProductsRequest;
import com.distributed.grpc.proto.ProductMessage;
import com.distributed.grpc.proto.ProductResponse;
import com.distributed.grpc.proto.ProductServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC Server implementation cho Product Service.
 *
 * @GrpcService: Đánh dấu class này là gRPC service, sẽ được tự động register với gRPC server.
 * Extends ProductServiceGrpc.ProductServiceImplBase: class được generate từ .proto file.
 *
 * Demo 4 loại gRPC calls:
 * 1. Unary (getProduct): Client gửi 1 request, server trả về 1 response
 * 2. Server Streaming (listProducts): Client gửi 1 request, server stream nhiều responses
 * 3. Client Streaming (createProducts): Client stream nhiều requests, server trả về 1 response
 * 4. Bidirectional Streaming (chatProducts): Cả hai bên đều stream
 */
@GrpcService
public class ProductGrpcServer extends ProductServiceGrpc.ProductServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ProductGrpcServer.class);

    // In-memory product store (thực tế dùng database)
    private final Map<String, ProductResponse> productStore = new ConcurrentHashMap<>();

    public ProductGrpcServer() {
        // Khởi tạo data mẫu
        addProduct("1", "Laptop Dell XPS 13", 1299.99, "electronics");
        addProduct("2", "iPhone 15 Pro", 999.99, "electronics");
        addProduct("3", "Sony WH-1000XM5", 349.99, "audio");
        addProduct("4", "Samsung 4K Monitor", 599.99, "electronics");
        addProduct("5", "Mechanical Keyboard", 149.99, "peripherals");
        addProduct("6", "AirPods Pro", 249.99, "audio");
    }

    private void addProduct(String id, String name, double price, String category) {
        productStore.put(id, ProductResponse.newBuilder()
                .setId(id)
                .setName(name)
                .setPrice(price)
                .setCategory(category)
                .setInStock(true)
                .build());
    }

    /**
     * UNARY RPC: Lấy thông tin một product theo ID.
     *
     * Pattern: Client gọi method, server xử lý và gọi:
     * - responseObserver.onNext(result)  → gửi response
     * - responseObserver.onCompleted()   → đánh dấu kết thúc
     * - responseObserver.onError(status) → báo lỗi
     */
    @Override
    public void getProduct(GetProductRequest request, StreamObserver<ProductResponse> responseObserver) {
        log.info("Unary getProduct called: id={}", request.getId());

        ProductResponse product = productStore.get(request.getId());
        if (product == null) {
            // Báo lỗi NOT_FOUND (tương tự HTTP 404)
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Product not found: " + request.getId())
                    .asRuntimeException());
            return;
        }

        // Gửi response và đánh dấu hoàn thành
        responseObserver.onNext(product);
        responseObserver.onCompleted();
    }

    /**
     * SERVER STREAMING RPC: List products theo category.
     *
     * Server gửi nhiều ProductResponse qua stream, mỗi lần một product.
     * Client nhận và xử lý từng product khi chúng đến.
     *
     * Pattern: server gọi onNext() nhiều lần, cuối cùng gọi onCompleted()
     */
    @Override
    public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
        log.info("Server streaming listProducts: category={}, pageSize={}",
                request.getCategory(), request.getPageSize());

        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        String categoryFilter = request.getCategory();

        List<ProductResponse> filtered = productStore.values().stream()
                .filter(p -> categoryFilter.isEmpty() || p.getCategory().equals(categoryFilter))
                .limit(pageSize)
                .toList();

        if (filtered.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No products found for category: " + categoryFilter)
                    .asRuntimeException());
            return;
        }

        // Gửi từng product qua stream với delay nhỏ (mô phỏng database pagination)
        for (ProductResponse product : filtered) {
            try {
                Thread.sleep(100); // Mô phỏng latency khi query từng page
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(Status.CANCELLED
                        .withDescription("Stream cancelled")
                        .asRuntimeException());
                return;
            }
            log.info("Streaming product: {}", product.getId());
            responseObserver.onNext(product); // Gửi từng product
        }

        responseObserver.onCompleted(); // Đánh dấu stream kết thúc
    }

    /**
     * CLIENT STREAMING RPC: Batch create products.
     *
     * Client gửi nhiều CreateProductRequest, server thu thập tất cả
     * rồi xử lý batch và trả về một BatchCreateResponse duy nhất.
     *
     * Pattern: Server trả về StreamObserver cho client stream vào.
     * - onNext(): Nhận từng request từ client
     * - onError(): Xử lý lỗi từ client
     * - onCompleted(): Client đã gửi xong → server xử lý và trả về response
     */
    @Override
    public StreamObserver<CreateProductRequest> createProducts(
            StreamObserver<BatchCreateResponse> responseObserver) {

        log.info("Client streaming createProducts started");

        // Buffer để thu thập tất cả requests từ client
        List<CreateProductRequest> receivedRequests = new ArrayList<>();
        List<String> createdIds = new ArrayList<>();

        // Return StreamObserver để client gọi vào
        return new StreamObserver<>() {
            @Override
            public void onNext(CreateProductRequest request) {
                // Nhận từng product request từ client
                log.info("Received product from client: name={}, price={}",
                        request.getName(), request.getPrice());
                receivedRequests.add(request);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Client streaming error: {}", throwable.getMessage());
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Client error: " + throwable.getMessage())
                        .asRuntimeException());
            }

            @Override
            public void onCompleted() {
                // Client đã gửi xong tất cả requests → xử lý batch
                log.info("Client completed streaming. Processing {} products...",
                        receivedRequests.size());

                for (CreateProductRequest req : receivedRequests) {
                    String newId = UUID.randomUUID().toString().substring(0, 8);
                    ProductResponse newProduct = ProductResponse.newBuilder()
                            .setId(newId)
                            .setName(req.getName())
                            .setPrice(req.getPrice())
                            .setCategory(req.getCategory())
                            .setInStock(true)
                            .build();
                    productStore.put(newId, newProduct);
                    createdIds.add(newId);
                }

                // Gửi một response duy nhất sau khi xử lý xong batch
                BatchCreateResponse response = BatchCreateResponse.newBuilder()
                        .setCreatedCount(createdIds.size())
                        .addAllIds(createdIds)
                        .setMessage("Successfully created " + createdIds.size() + " products")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * BIDIRECTIONAL STREAMING RPC: Chat-like product interaction.
     *
     * Cả client và server đều có thể gửi messages bất kỳ lúc nào.
     * Phù hợp cho: real-time search, collaborative filtering, live product updates.
     *
     * Pattern: Server return StreamObserver để nhận từ client,
     * đồng thời giữ responseObserver để gửi ngược về client.
     */
    @Override
    public StreamObserver<ProductMessage> chatProducts(
            StreamObserver<ProductMessage> responseObserver) {

        log.info("Bidirectional streaming chatProducts started");

        return new StreamObserver<>() {
            @Override
            public void onNext(ProductMessage message) {
                log.info("Received message: type={}, payload={}", message.getType(), message.getPayload());

                // Xử lý message dựa trên type
                switch (message.getType()) {
                    case "SEARCH" -> {
                        // Client yêu cầu search → server stream kết quả tìm kiếm
                        String keyword = message.getPayload().toLowerCase();
                        productStore.values().stream()
                                .filter(p -> p.getName().toLowerCase().contains(keyword)
                                        || p.getCategory().toLowerCase().contains(keyword))
                                .forEach(product -> {
                                    // Gửi từng kết quả tìm kiếm về cho client
                                    responseObserver.onNext(ProductMessage.newBuilder()
                                            .setType("RESULT")
                                            .setPayload(String.format(
                                                    "{\"id\":\"%s\",\"name\":\"%s\",\"price\":%.2f}",
                                                    product.getId(), product.getName(), product.getPrice()))
                                            .build());
                                });
                        // Thông báo search đã xong
                        responseObserver.onNext(ProductMessage.newBuilder()
                                .setType("SEARCH_COMPLETE")
                                .setPayload("Search completed for: " + keyword)
                                .build());
                    }
                    case "FILTER" -> {
                        // Client yêu cầu filter theo category
                        String category = message.getPayload();
                        long count = productStore.values().stream()
                                .filter(p -> p.getCategory().equals(category))
                                .count();
                        responseObserver.onNext(ProductMessage.newBuilder()
                                .setType("FILTER_RESULT")
                                .setPayload(String.format("{\"category\":\"%s\",\"count\":%d}",
                                        category, count))
                                .build());
                    }
                    default -> {
                        // Echo unknown messages
                        responseObserver.onNext(ProductMessage.newBuilder()
                                .setType("ECHO")
                                .setPayload("Unknown type: " + message.getType())
                                .build());
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Bidirectional stream error: {}", throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("Client closed bidirectional stream");
                responseObserver.onCompleted();
            }
        };
    }
}
