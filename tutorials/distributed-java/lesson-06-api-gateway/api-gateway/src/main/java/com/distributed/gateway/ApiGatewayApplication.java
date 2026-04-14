package com.distributed.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 * Điểm vào duy nhất cho tất cả client requests trong hệ thống microservices.
 * Sử dụng Spring Cloud Gateway (Netty-based, reactive) để xử lý routing.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
