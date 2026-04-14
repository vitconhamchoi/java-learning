package com.distributed.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Order Service - demo hai cách gọi Product Service qua Service Discovery.
 *
 * Cách 1: Manual discovery với DiscoveryClient
 *   - Explicit query Eureka để lấy instances
 *   - Manual selection (no LB)
 *   - Dùng khi cần control chi tiết hơn (ví dụ: filter by metadata)
 *
 * Cách 2: @LoadBalanced RestTemplate
 *   - Transparent LB (client code không biết về discovery)
 *   - Spring Cloud tự handle: discovery + round-robin LB
 *   - Đơn giản, ít boilerplate
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // DiscoveryClient: Spring Cloud abstraction để query service registry
    // Hoạt động với Eureka, Consul, Zookeeper, Kubernetes, etc.
    private final DiscoveryClient discoveryClient;

    // RestTemplate với @LoadBalanced (từ OrderServiceApplication bean)
    private final RestTemplate restTemplate;

    public OrderService(DiscoveryClient discoveryClient, RestTemplate restTemplate) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Cách 1: Manual discovery với DiscoveryClient.
     *
     * Dùng khi cần:
     * - Filter instances theo metadata (zone, version, weight)
     * - Custom load balancing logic
     * - Inspect instance details trước khi call
     *
     * Nhược điểm: Phải implement LB logic thủ công.
     */
    public Map<String, Object> getProductFromDiscovery(String productId) {
        // Query Eureka để lấy tất cả instances của "product-service"
        List<ServiceInstance> instances = discoveryClient.getInstances("product-service");

        if (instances.isEmpty()) {
            throw new RuntimeException("No instances of product-service found in registry!");
        }

        log.info("Found {} instance(s) of product-service:", instances.size());
        for (ServiceInstance instance : instances) {
            log.info("  - {}:{} [zone={}, version={}]",
                    instance.getHost(),
                    instance.getPort(),
                    instance.getMetadata().get("zone"),
                    instance.getMetadata().get("version"));
        }

        // Simple selection: chọn instance đầu tiên (trong thực tế dùng LB algorithm)
        ServiceInstance selectedInstance = instances.get(0);
        String url = String.format("http://%s:%d/products/%s",
                selectedInstance.getHost(),
                selectedInstance.getPort(),
                productId);

        log.info("Calling: {} on instance {}:{}",
                url, selectedInstance.getHost(), selectedInstance.getPort());

        // Dùng RestTemplate thông thường (không @LoadBalanced) để gọi trực tiếp
        RestTemplate plainRestTemplate = new RestTemplate();
        @SuppressWarnings("unchecked")
        Map<String, Object> product = plainRestTemplate.getForObject(url, Map.class);
        return product;
    }

    /**
     * Cách 2: @LoadBalanced RestTemplate.
     *
     * Dùng service name thay vì IP:port.
     * Spring Cloud LoadBalancer tự:
     * - Query Eureka để lấy instances
     * - Áp dụng round-robin (default) để chọn instance
     * - Replace "product-service" bằng actual host:port
     *
     * Ưu điểm: Transparent, ít code, automatic LB.
     */
    public Map<String, Object> getProductLoadBalanced(String productId) {
        // Dùng service name trong URL thay vì hardcoded IP
        // restTemplate là @LoadBalanced → tự resolve "product-service" từ Eureka
        String url = "http://product-service/products/" + productId;

        log.info("Load-balanced call to: {}", url);

        @SuppressWarnings("unchecked")
        Map<String, Object> product = restTemplate.getForObject(url, Map.class);
        return product;
    }

    /**
     * Lấy danh sách tất cả services đã đăng ký trong Eureka.
     */
    public List<String> getAllRegisteredServices() {
        return discoveryClient.getServices();
    }

    /**
     * Lấy thông tin chi tiết của một service cụ thể.
     */
    public List<Map<String, Object>> getServiceInstances(String serviceName) {
        return discoveryClient.getInstances(serviceName).stream()
                .map(instance -> Map.<String, Object>of(
                        "host", instance.getHost(),
                        "port", instance.getPort(),
                        "instanceId", instance.getInstanceId(),
                        "uri", instance.getUri().toString(),
                        "metadata", instance.getMetadata(),
                        "scheme", instance.getScheme()
                ))
                .toList();
    }
}
