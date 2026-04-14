package com.distributed.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend Service Application.
 *
 * Chạy nhiều instances với PORT khác nhau để demo load balancing:
 *   java -jar target/backend-service.jar --server.port=8081
 *   java -jar target/backend-service.jar --server.port=8082
 *   java -jar target/backend-service.jar --server.port=8083
 *
 * Mỗi instance đăng ký với Eureka với weight metadata khác nhau
 * để test Weighted Load Balancer.
 */
@SpringBootApplication
public class BackendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendServiceApplication.class, args);
    }
}
