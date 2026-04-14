package com.distributed.grpc.client;

import com.distributed.grpc.proto.BatchCreateResponse;
import com.distributed.grpc.proto.CreateProductRequest;
import com.distributed.grpc.proto.GetProductRequest;
import com.distributed.grpc.proto.ListProductsRequest;
import com.distributed.grpc.proto.ProductResponse;
import com.distributed.grpc.proto.ProductServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC Client Service cho Product Service.
 *
 * @GrpcClient: Inject gRPC stub được cấu hình trong application.yml.
 * "product-service" là tên channel, mapping đến grpc.client.product-service config.
 *
 * ProductServiceBlockingStub: Blocking (synchronous) stub, giống gọi method thông thường.
 * ProductServiceStub: Non-blocking (async) stub, dùng với StreamObserver.
 */
@Service
public class ProductGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ProductGrpcClient.class);

    // Blocking stub: synchronous calls (unary + server streaming với Iterator)
    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceBlockingStub blockingStub;

    // Async stub: asynchronous calls (client streaming + bidirectional)
    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceStub asyncStub;

    /**
     * Unary call: Lấy thông tin một product theo ID.
     *
     * withDeadlineAfter(5, SECONDS): Set deadline 5 giây cho call này.
     * Nếu server không respond trong 5 giây → DEADLINE_EXCEEDED exception.
     *
     * @param id Product ID
     * @return Optional<ProductResponse> (empty nếu không tìm thấy)
     */
    public Optional<ProductResponse> getProduct(String id) {
        log.info("gRPC getProduct: id={}", id);
        try {
            // withDeadlineAfter: deadline propagation quan trọng để fail-fast!
            ProductResponse response = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getProduct(GetProductRequest.newBuilder().setId(id).build());
            return Optional.of(response);
        } catch (io.grpc.StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                log.info("Product not found: {}", id);
                return Optional.empty();
            }
            log.error("gRPC error getting product {}: {} - {}",
                    id, ex.getStatus().getCode(), ex.getStatus().getDescription());
            throw new RuntimeException("Failed to get product: " + ex.getMessage(), ex);
        }
    }

    /**
     * Server Streaming: List products theo category.
     *
     * blockingStub.listProducts() trả về Iterator<ProductResponse>.
     * Mỗi lần gọi next() block cho đến khi có response tiếp theo từ server.
     *
     * @param category Category filter (empty string để lấy tất cả)
     * @return List<ProductResponse> tất cả products trong category
     */
    public List<ProductResponse> listProducts(String category) {
        log.info("gRPC listProducts: category={}", category);
        List<ProductResponse> results = new ArrayList<>();

        try {
            ListProductsRequest request = ListProductsRequest.newBuilder()
                    .setCategory(category)
                    .setPageSize(20)
                    .build();

            // Iterator<T> từ server streaming: block per item
            Iterator<ProductResponse> iterator = blockingStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS) // Streaming có thể lâu hơn
                    .listProducts(request);

            while (iterator.hasNext()) {
                ProductResponse product = iterator.next(); // Block cho đến khi nhận được item tiếp
                log.info("  Received streamed product: id={}, name={}", product.getId(), product.getName());
                results.add(product);
            }

            log.info("Server streaming completed: {} products received", results.size());
            return results;

        } catch (io.grpc.StatusRuntimeException ex) {
            log.error("gRPC streaming error: {}", ex.getMessage());
            throw new RuntimeException("Failed to list products", ex);
        }
    }

    /**
     * Client Streaming: Gửi nhiều products, nhận một BatchCreateResponse.
     *
     * Dùng async stub với CountDownLatch để đợi response.
     * Client gửi nhiều requests qua StreamObserver, server trả về 1 response khi nhận xong.
     *
     * @param requests List các CreateProductRequest cần tạo
     * @return BatchCreateResponse kết quả batch create
     */
    public BatchCreateResponse createProductsBatch(List<CreateProductRequest> requests)
            throws InterruptedException {

        log.info("gRPC createProductsBatch: {} products", requests.size());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BatchCreateResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // Async stub: server sends response vào responseObserver khi done
        StreamObserver<CreateProductRequest> requestObserver =
                asyncStub.withDeadlineAfter(60, TimeUnit.SECONDS)
                        .createProducts(new StreamObserver<>() {
                            @Override
                            public void onNext(BatchCreateResponse response) {
                                log.info("Batch create response: created={}", response.getCreatedCount());
                                responseRef.set(response);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                log.error("Batch create error: {}", throwable.getMessage());
                                errorRef.set(throwable);
                                latch.countDown();
                            }

                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        });

        // Gửi từng request
        for (CreateProductRequest req : requests) {
            requestObserver.onNext(req);
            log.info("  Sent: name={}", req.getName());
        }

        // Đánh dấu kết thúc client streaming
        requestObserver.onCompleted();

        // Đợi server response (tối đa 60 giây)
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for batch create response");
        }

        if (errorRef.get() != null) {
            throw new RuntimeException("Batch create failed", errorRef.get());
        }

        return responseRef.get();
    }
}
