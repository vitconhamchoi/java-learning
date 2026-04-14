package com.distributed.lb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo Controller để test load balancing.
 *
 * GET /demo/call   - Gọi backend-service, track instance nào được hit
 * GET /demo/stats  - Xem phân phối traffic giữa các instances
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    // Load-balanced RestTemplate (từ LoadBalancerApplication bean)
    private final RestTemplate loadBalancedRestTemplate;

    // Track số lần mỗi instance được gọi (instanceId → count)
    private final ConcurrentHashMap<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalCalls = new AtomicInteger(0);

    public DemoController(RestTemplate loadBalancedRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
    }

    /**
     * GET /demo/call - Gọi backend-service qua load-balanced RestTemplate.
     *
     * Dùng service name "backend-service" thay vì hardcoded host:port.
     * Spring Cloud LB tự resolve và route theo weighted algorithm.
     */
    @GetMapping("/call")
    public Map<String, Object> callBackend() {
        try {
            // URL với service name (không phải host:port)
            @SuppressWarnings("unchecked")
            Map<String, Object> backendInfo = loadBalancedRestTemplate
                    .getForObject("http://backend-service/backend/info", Map.class);

            if (backendInfo != null) {
                // Track instance được hit
                String instanceId = backendInfo.getOrDefault("instanceId", "unknown").toString();
                callCounts.computeIfAbsent(instanceId, k -> new AtomicInteger(0)).incrementAndGet();
                int total = totalCalls.incrementAndGet();

                log.info("Call #{}: routed to instance {}", total, instanceId);

                return Map.of(
                        "callNumber", total,
                        "routedTo", backendInfo,
                        "totalCalls", total
                );
            }

            return Map.of("error", "Empty response from backend");
        } catch (Exception e) {
            log.error("Failed to call backend: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * GET /demo/stats - Xem phân phối traffic giữa các instances.
     *
     * Dùng để verify load balancing đang hoạt động đúng:
     * - Weighted: instances với weight cao hơn được gọi nhiều hơn
     * - Round Robin: phân phối đều
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        int total = totalCalls.get();

        // Tính % cho mỗi instance
        Map<String, Object> distribution = new java.util.LinkedHashMap<>();
        callCounts.forEach((instanceId, count) -> {
            int calls = count.get();
            double percentage = total > 0 ? (double) calls / total * 100 : 0;
            distribution.put(instanceId, Map.of(
                    "calls", calls,
                    "percentage", String.format("%.1f%%", percentage)
            ));
        });

        return Map.of(
                "totalCalls", total,
                "distribution", distribution,
                "algorithm", "WeightedRandom (check weight metadata per instance)"
        );
    }

    /**
     * GET /demo/reset - Reset thống kê.
     */
    @GetMapping("/reset")
    public Map<String, String> reset() {
        callCounts.clear();
        totalCalls.set(0);
        return Map.of("status", "Stats reset successfully");
    }
}
