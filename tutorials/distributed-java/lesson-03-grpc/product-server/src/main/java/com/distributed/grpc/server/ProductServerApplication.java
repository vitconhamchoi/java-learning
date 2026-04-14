package com.distributed.grpc.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product gRPC Server Application.
 *
 * gRPC server tự động start trên port được cấu hình (grpc.server.port=9090).
 * Spring Boot HTTP server cũng start (port 8090) để expose actuator endpoints.
 */
@SpringBootApplication
public class ProductServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServerApplication.class, args);
    }
}
