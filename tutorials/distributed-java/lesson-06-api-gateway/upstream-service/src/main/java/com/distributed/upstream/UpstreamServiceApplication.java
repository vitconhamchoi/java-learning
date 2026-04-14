package com.distributed.upstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Upstream Service - Đại diện cho một microservice thực tế phía sau Gateway.
 * Nhận request đã được Gateway xử lý (auth, rate limit, etc.)
 */
@SpringBootApplication
public class UpstreamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpstreamServiceApplication.class, args);
    }
}
