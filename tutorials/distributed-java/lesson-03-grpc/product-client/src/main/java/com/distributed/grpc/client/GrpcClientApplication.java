package com.distributed.grpc.client;

import com.distributed.grpc.proto.ProductResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * gRPC Client Application.
 *
 * Expose REST endpoints để demo gRPC calls.
 * Người dùng gọi REST API → client gọi gRPC → server xử lý → response.
 *
 * Đây là pattern "gRPC backend, REST frontend" phổ biến.
 */
@SpringBootApplication
public class GrpcClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcClientApplication.class, args);
    }

    /**
     * REST Controller để expose gRPC calls qua HTTP endpoints.
     * Dễ dàng test với curl, Postman thay vì phải dùng grpcurl.
     */
    @RestController
    @RequestMapping("/products")
    static class ProductClientController {

        private final ProductGrpcClient grpcClient;

        ProductClientController(ProductGrpcClient grpcClient) {
            this.grpcClient = grpcClient;
        }

        /**
         * GET /products/{id} - Lấy product bằng Unary gRPC call.
         */
        @GetMapping("/{id}")
        public ResponseEntity<ProductInfo> getProduct(@PathVariable String id) {
            return grpcClient.getProduct(id)
                    .map(p -> ResponseEntity.ok(toProductInfo(p)))
                    .orElse(ResponseEntity.notFound().build());
        }

        /**
         * GET /products?category=electronics - List products bằng Server Streaming gRPC call.
         */
        @GetMapping
        public List<ProductInfo> listProducts(
                @RequestParam(defaultValue = "") String category) {
            return grpcClient.listProducts(category).stream()
                    .map(this::toProductInfo)
                    .toList();
        }

        /**
         * GET /products/health - Check client connectivity.
         */
        @GetMapping("/health")
        public Map<String, String> health() {
            return Map.of(
                    "status", "UP",
                    "service", "product-grpc-client",
                    "grpcServer", "localhost:9090"
            );
        }

        private ProductInfo toProductInfo(ProductResponse p) {
            return new ProductInfo(p.getId(), p.getName(), p.getPrice(),
                    p.getCategory(), p.getInStock());
        }

        record ProductInfo(String id, String name, double price, String category, boolean inStock) {}
    }
}
