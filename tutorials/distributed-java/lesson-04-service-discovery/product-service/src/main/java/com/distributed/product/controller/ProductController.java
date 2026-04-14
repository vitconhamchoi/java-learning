package com.distributed.product.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Product Controller - expose REST endpoints.
 *
 * GET /products/instance: Trả về thông tin instance hiện tại.
 * Dùng để verify load balancing - mỗi request có thể đến instance khác nhau.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final Environment environment;

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${spring.application.name:product-service}")
    private String applicationName;

    public ProductController(Environment environment) {
        this.environment = environment;
    }

    /**
     * GET /products/{id} - Lấy thông tin product.
     * Trong thực tế query từ database.
     */
    @GetMapping("/{id}")
    public Product getProduct(@PathVariable String id) {
        return switch (id) {
            case "P001" -> new Product(id, "Laptop Dell XPS 13", 1299.99, "electronics");
            case "P002" -> new Product(id, "iPhone 15 Pro", 999.99, "electronics");
            case "P003" -> new Product(id, "Sony Headphones", 349.99, "audio");
            default -> new Product(id, "Unknown Product", 0.0, "unknown");
        };
    }

    /**
     * GET /products/instance - Thông tin về instance đang phục vụ request.
     *
     * Endpoint này rất hữu ích để demo load balancing:
     * Gọi nhiều lần → thấy các instances khác nhau respond.
     */
    @GetMapping("/instance")
    public Map<String, String> getInstanceInfo() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }

        String port = environment.getProperty("local.server.port", serverPort);

        return Map.of(
                "serviceName", applicationName,
                "instanceId", hostname + ":" + port,
                "hostname", hostname,
                "port", port,
                "version", environment.getProperty("eureka.instance.metadata-map.version", "1.0"),
                "zone", environment.getProperty("eureka.instance.metadata-map.zone", "default")
        );
    }

    /**
     * Product domain record.
     */
    public record Product(String id, String name, double price, String category) {}
}
