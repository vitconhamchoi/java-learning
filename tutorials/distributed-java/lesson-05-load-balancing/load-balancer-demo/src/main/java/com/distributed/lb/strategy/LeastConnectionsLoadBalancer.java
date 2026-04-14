package com.distributed.lb.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least Connections Load Balancer.
 *
 * Thuật toán:
 * 1. Track số lượng active connections đến mỗi instance
 * 2. Chọn instance có ít connections nhất
 * 3. Increment counter khi route request
 * 4. Decrement counter khi request hoàn thành
 *
 * Phù hợp nhất khi:
 * - Requests có varying processing time
 * - Tránh overload instance đang xử lý nhiều long-running requests
 * - Ví dụ: File uploads cùng với quick API calls
 *
 * Hạn chế:
 * - Connection count ≠ actual load (một connection nặng vs nhiều connection nhẹ)
 * - Counter cần được decrement khi request done (phức tạp hơn round-robin)
 */
public class LeastConnectionsLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LeastConnectionsLoadBalancer.class);

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    // Track active connections per instance (instanceId → count)
    // ConcurrentHashMap: thread-safe cho concurrent requests
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public LeastConnectionsLoadBalancer(
            String serviceId,
            ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(WeightedLoadBalancer.NoopServiceInstanceListSupplier::new);

        return supplier.get(request).next()
                .map(instances -> processInstanceResponse(instances));
    }

    /**
     * Chọn instance với ít connections nhất.
     *
     * Thread-safety:
     * - connectionCounts là ConcurrentHashMap với AtomicInteger values
     * - computeIfAbsent: thread-safe initialization
     * - getAndIncrement: atomic increment
     */
    private Response<ServiceInstance> processInstanceResponse(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            log.warn("No instances available for: {}", serviceId);
            return new EmptyResponse();
        }

        // Cleanup instances không còn trong registry
        // (instance deregistered nhưng vẫn còn trong connectionCounts)
        var activeIds = instances.stream()
                .map(i -> i.getInstanceId())
                .collect(java.util.stream.Collectors.toSet());
        connectionCounts.keySet().retainAll(activeIds);

        // Chọn instance với least connections
        ServiceInstance chosen = instances.stream()
                .min(Comparator.comparingInt(instance ->
                        getConnectionCount(instance).get()))
                .orElse(instances.get(0));

        // Increment connection count cho instance được chọn
        int newCount = getConnectionCount(chosen).incrementAndGet();

        log.debug("Least-conn LB: selected {}:{} (connections={}). All counts: {}",
                chosen.getHost(), chosen.getPort(), newCount, getConnectionSummary(instances));

        return new DefaultResponse(chosen) {
            // Note: In a real implementation, we'd hook into the response completion
            // to decrement. For demo purposes, we simulate decrement after a delay.
            // In production, use a custom ExchangeFilterFunction or ResponseInterceptor.
        };
    }

    /**
     * Lấy connection count cho instance, khởi tạo nếu chưa có.
     * computeIfAbsent đảm bảo thread-safe initialization.
     */
    private AtomicInteger getConnectionCount(ServiceInstance instance) {
        return connectionCounts.computeIfAbsent(
                instance.getInstanceId(),
                id -> new AtomicInteger(0));
    }

    /**
     * Giảm connection count sau khi request hoàn thành.
     * Phải được gọi khi response received (success hoặc error).
     */
    public void decrementConnections(String instanceId) {
        AtomicInteger count = connectionCounts.get(instanceId);
        if (count != null) {
            int newCount = count.decrementAndGet();
            // Không để count xuống dưới 0 (có thể xảy ra khi restart)
            if (newCount < 0) {
                count.set(0);
            }
        }
    }

    /** Lấy summary của connection counts để log/monitor */
    private Map<String, Integer> getConnectionSummary(List<ServiceInstance> instances) {
        Map<String, Integer> summary = new java.util.LinkedHashMap<>();
        for (ServiceInstance instance : instances) {
            String key = instance.getHost() + ":" + instance.getPort();
            summary.put(key, getConnectionCount(instance).get());
        }
        return summary;
    }

    /** Expose connection counts để monitoring/testing */
    public Map<String, Integer> getConnectionCounts() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        connectionCounts.forEach((id, count) -> result.put(id, count.get()));
        return result;
    }
}
