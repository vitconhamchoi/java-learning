package com.distributed.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Order Service Application với Load-Balanced RestTemplate.
 *
 * @LoadBalanced: Tạo RestTemplate có tích hợp Spring Cloud LoadBalancer.
 * Cho phép dùng service name (http://product-service/...) thay vì IP:port.
 * LoadBalancer tự resolve service name qua Eureka và áp dụng load balancing.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    /**
     * RestTemplate với @LoadBalanced.
     *
     * Khi RestTemplate gọi http://product-service/products/{id}:
     * 1. LoadBalancerInterceptor intercept request
     * 2. Query Eureka: lấy danh sách instances của "product-service"
     * 3. Áp dụng round-robin để chọn instance
     * 4. Thay "product-service" bằng actual IP:port
     * 5. Execute HTTP request
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
