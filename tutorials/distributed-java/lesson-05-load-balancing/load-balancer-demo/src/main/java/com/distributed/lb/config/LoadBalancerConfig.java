package com.distributed.lb.config;

import com.distributed.lb.strategy.WeightedLoadBalancer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Load Balancer Configuration.
 *
 * @LoadBalancerClient: Đăng ký custom LB cho một service cụ thể.
 * name: tên service (phải khớp với service name trong Eureka)
 * configuration: class chứa @Bean định nghĩa custom LoadBalancer
 *
 * QUAN TRỌNG: WeightedLoadBalancerConfig KHÔNG được @Configuration-annotated
 * ở component scan level để tránh được picked up globally.
 * Chỉ được apply cho "backend-service" qua @LoadBalancerClient.
 */
@Configuration
@LoadBalancerClient(name = "backend-service", configuration = LoadBalancerConfig.WeightedLoadBalancerConfig.class)
public class LoadBalancerConfig {

    /**
     * Weighted LB Configuration cho backend-service.
     *
     * Đây là inner static class để tránh Spring Boot auto-detect
     * và apply globally (chỉ apply cho backend-service).
     */
    public static class WeightedLoadBalancerConfig {

        /**
         * Bean định nghĩa Weighted Load Balancer.
         *
         * ServiceInstanceListSupplier: Provider cung cấp danh sách instances
         * từ Eureka (hoặc bất kỳ discovery mechanism nào).
         */
        @Bean
        public ReactorServiceInstanceLoadBalancer weightedLoadBalancer(
                ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider) {
            return new WeightedLoadBalancer(
                    "backend-service",
                    serviceInstanceListSupplierProvider
            );
        }
    }
}
