package com.distributed.upstream.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Upstream Controller - Xử lý các request đến từ API Gateway.
 *
 * Controller này demonstrate:
 * 1. Cách nhận và sử dụng headers được inject bởi Gateway (X-User-Id, X-User-Role)
 * 2. REST endpoints đơn giản để test Gateway routing
 * 3. Echo headers để debug Gateway behavior
 */
@RestController
@RequestMapping("/products")
public class UpstreamController {

    // Record đại diện cho Product response
    record Product(String id, String name, BigDecimal price, String category,
                   Map<String, String> requestHeaders) {}

    // Response wrapper chứa data và gateway headers
    record ProductResponse(Product product, Map<String, String> gatewayInfo) {}

    /**
     * GET /products/{id} - Lấy thông tin một sản phẩm.
     *
     * Headers được Gateway inject:
     * - X-User-Id: ID của user đã đăng nhập (từ JWT)
     * - X-User-Role: Role của user (từ JWT)
     * - X-Gateway-Source: Tên của Gateway
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable String id,
            HttpServletRequest request) {

        // Đọc thông tin user được Gateway inject vào header
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        String gatewaySource = request.getHeader("X-Gateway-Source");

        // Tạo product data (trong thực tế lấy từ DB)
        Map<String, String> receivedHeaders = new HashMap<>();
        receivedHeaders.put("X-User-Id", userId != null ? userId : "anonymous");
        receivedHeaders.put("X-User-Role", userRole != null ? userRole : "GUEST");
        receivedHeaders.put("X-Gateway-Source", gatewaySource != null ? gatewaySource : "direct");

        Product product = new Product(
                id,
                "Sản phẩm " + id,
                new BigDecimal("299000"),
                "Electronics",
                receivedHeaders
        );

        // Gateway info để demonstrate headers đã được set đúng
        Map<String, String> gatewayInfo = Map.of(
                "processedBy", "upstream-service",
                "userId", userId != null ? userId : "anonymous",
                "userRole", userRole != null ? userRole : "GUEST",
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(new ProductResponse(product, gatewayInfo));
    }

    /**
     * GET /products - Lấy danh sách tất cả sản phẩm.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProducts(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");

        List<Product> products = List.of(
                new Product("1", "iPhone 15 Pro", new BigDecimal("29990000"), "Smartphone", Map.of()),
                new Product("2", "Samsung Galaxy S24", new BigDecimal("24990000"), "Smartphone", Map.of()),
                new Product("3", "MacBook Air M3", new BigDecimal("32990000"), "Laptop", Map.of())
        );

        return ResponseEntity.ok(Map.of(
                "products", products,
                "total", products.size(),
                "requestedBy", userId != null ? userId : "anonymous",
                "userRole", userRole != null ? userRole : "GUEST",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * GET /products/headers/echo - Echo tất cả headers nhận được.
     * Dùng để debug và kiểm tra headers nào Gateway đã thêm.
     */
    @GetMapping("/headers/echo")
    public ResponseEntity<Map<String, Object>> echoHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();

        // Thu thập tất cả headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        // Highlight các gateway-injected headers
        Map<String, String> gatewayHeaders = new HashMap<>();
        if (headers.containsKey("x-user-id")) {
            gatewayHeaders.put("X-User-Id", headers.get("x-user-id"));
        }
        if (headers.containsKey("x-user-role")) {
            gatewayHeaders.put("X-User-Role", headers.get("x-user-role"));
        }
        if (headers.containsKey("x-gateway-source")) {
            gatewayHeaders.put("X-Gateway-Source", headers.get("x-gateway-source"));
        }

        return ResponseEntity.ok(Map.of(
                "allHeaders", headers,
                "gatewayInjectedHeaders", gatewayHeaders,
                "totalHeaders", headers.size()
        ));
    }
}
