package com.distributed.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Backend Controller - expose instance information để verify load balancing.
 *
 * Khi gọi nhiều lần, response sẽ cho thấy các instances khác nhau được hit.
 * Giúp verify weighted distribution đang hoạt động đúng.
 */
@RestController
@RequestMapping("/backend")
public class BackendController {

    private final Environment environment;

    // Unique ID cho instance này (tạo một lần khi khởi động)
    private final String instanceUuid = UUID.randomUUID().toString().substring(0, 8);

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${eureka.instance.metadata-map.weight:1}")
    private String weight;

    public BackendController(Environment environment) {
        this.environment = environment;
    }

    /**
     * GET /backend/info - Trả về thông tin của instance hiện tại.
     *
     * Client có thể xem instance nào đang phục vụ request.
     * Load balancer demo sẽ gọi endpoint này nhiều lần và track distribution.
     */
    @GetMapping("/info")
    public Map<String, Object> getInstanceInfo() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }

        // Lấy actual port đang listen (có thể khác server.port nếu dùng PORT env var)
        String port = environment.getProperty("local.server.port", serverPort);

        return Map.of(
                "instanceId", hostname + ":" + port,
                "instanceUuid", instanceUuid,
                "hostname", hostname,
                "port", port,
                "weight", weight,
                "timestamp", Instant.now().toString(),
                "message", "Response from instance " + instanceUuid + " (weight=" + weight + ")"
        );
    }

    /**
     * GET /backend/health - Simple health check endpoint.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "instance", instanceUuid,
                "port", serverPort
        );
    }
}
