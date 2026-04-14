package com.distributed.lb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Load Balancer Demo Application.
 *
 * Cung cấp cả @LoadBalanced RestTemplate và WebClient.Builder để demo
 * hai cách tiếp cận load balancing trong Spring Boot.
 */
@SpringBootApplication
public class LoadBalancerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadBalancerApplication.class, args);
    }

    /**
     * @LoadBalanced RestTemplate: Blocking, synchronous load-balanced HTTP client.
     * Spring Cloud LB intercept mọi request và resolve service name.
     */
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }

    /**
     * @LoadBalanced WebClient.Builder: Non-blocking, reactive load-balanced HTTP client.
     * Cần inject WebClient.Builder (không phải WebClient) vì builder cần được
     * decorated với load balancing capabilities trước khi build.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
