package com.distributed.observability.config;

import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MetricsConfig {

    private final AtomicInteger activeUsers = new AtomicInteger(0);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    @Bean
    public Gauge activeUsersGauge(MeterRegistry registry) {
        return Gauge.builder("app.active_users", activeUsers, AtomicInteger::get)
            .description("Number of currently active users")
            .tag("environment", "demo")
            .register(registry);
    }

    @Bean
    public Gauge queueSizeGauge(MeterRegistry registry) {
        return Gauge.builder("app.queue_size", queueSize, AtomicInteger::get)
            .description("Current queue depth")
            .tag("queue", "main")
            .register(registry);
    }

    public AtomicInteger getActiveUsers() { return activeUsers; }
    public AtomicInteger getQueueSize() { return queueSize; }
}
