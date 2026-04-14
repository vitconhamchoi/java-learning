package com.distributed.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class OrderTracingApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderTracingApplication.class, args);
    }
}
