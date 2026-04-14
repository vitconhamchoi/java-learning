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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Weighted Random Load Balancer.
 *
 * Thuật toán:
 * 1. Đọc "weight" từ instance metadata (mặc định = 1)
 * 2. Xây dựng weighted list: instance A với weight=3 xuất hiện 3 lần trong list
 * 3. Random pick từ weighted list
 *
 * Ví dụ:
 * Instance A: weight=3 → [A, A, A]
 * Instance B: weight=2 → [A, A, A, B, B]
 * Instance C: weight=1 → [A, A, A, B, B, C]
 * Random pick từ 6 phần tử → A được chọn 50%, B 33%, C 17%
 */
public class WeightedLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(WeightedLoadBalancer.class);

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    // AtomicInteger để thread-safe position tracking (dùng cho deterministic testing)
    private final AtomicInteger position;

    public WeightedLoadBalancer(String serviceId,
                                ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.position = new AtomicInteger(0);
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);

        return supplier.get(request).next()
                .map(serviceInstances -> processInstanceResponse(serviceInstances, request));
    }

    /**
     * Chọn instance dựa trên weighted algorithm.
     *
     * @param instances Danh sách instances available từ registry
     * @param request   Request context (có thể dùng để inspect headers, etc.)
     * @return Response chứa instance được chọn
     */
    private Response<ServiceInstance> processInstanceResponse(
            List<ServiceInstance> instances, Request<?> request) {

        if (instances.isEmpty()) {
            log.warn("No instances available for service: {}", serviceId);
            return new EmptyResponse();
        }

        // Xây dựng weighted list: mỗi instance xuất hiện theo weight của nó
        List<ServiceInstance> weightedList = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            // Đọc weight từ metadata, mặc định = 1 nếu không có
            String weightStr = instance.getMetadata().getOrDefault("weight", "1");
            int weight;
            try {
                weight = Integer.parseInt(weightStr);
                weight = Math.max(1, weight); // Tối thiểu weight = 1
            } catch (NumberFormatException e) {
                weight = 1;
            }

            // Thêm instance vào list 'weight' lần
            for (int i = 0; i < weight; i++) {
                weightedList.add(instance);
            }

            log.debug("Instance {}:{} weight={}", instance.getHost(), instance.getPort(), weight);
        }

        // Chọn ngẫu nhiên từ weighted list (giống random nhưng weighted)
        // Dùng AtomicInteger để có thể trace distribution nếu cần
        int totalWeight = weightedList.size();
        int selectedIndex = (int) (Math.random() * totalWeight);
        ServiceInstance chosen = weightedList.get(selectedIndex);

        log.debug("Weighted LB: selected {}:{} from {} weighted entries",
                chosen.getHost(), chosen.getPort(), totalWeight);

        return new DefaultResponse(chosen);
    }

    /** No-op supplier khi không có ServiceInstanceListSupplier available */
    static class NoopServiceInstanceListSupplier implements ServiceInstanceListSupplier {
        @Override
        public String getServiceId() {
            return "noop";
        }

        @Override
        public reactor.core.publisher.Flux<List<ServiceInstance>> get() {
            return reactor.core.publisher.Flux.just(List.of());
        }
    }
}
