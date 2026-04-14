package com.distributed.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server Application.
 *
 * @EnableEurekaServer: Kích hoạt Eureka Service Registry.
 * Server sẽ lắng nghe registrations từ các services và cung cấp
 * REST API cho clients để query service instances.
 *
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
