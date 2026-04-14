package com.distributed.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Service Application.
 *
 * @EnableFeignClients: Kích hoạt Feign client scanning trong package này.
 * Feign clients được định nghĩa với @FeignClient sẽ được auto-configured.
 */
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
