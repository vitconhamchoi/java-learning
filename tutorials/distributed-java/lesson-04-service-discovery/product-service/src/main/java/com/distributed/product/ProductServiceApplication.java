package com.distributed.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product Service Application.
 *
 * @SpringBootApplication kết hợp với spring-cloud-starter-netflix-eureka-client
 * trong classpath sẽ tự động đăng ký service với Eureka Server.
 *
 * Không cần @EnableDiscoveryClient trong Spring Cloud 3.x (auto-configured).
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
