package com.distributed.observability.service;

import com.distributed.observability.config.MetricsConfig;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private final MeterRegistry registry;
    private final MetricsConfig metricsConfig;
    private final Counter orderCounter;
    private final Counter errorCounter;
    private final Timer orderProcessingTimer;
    private final DistributionSummary orderValueSummary;
    private final AtomicLong totalRevenue = new AtomicLong(0);
    private final Random random = new Random();

    public MetricsService(MeterRegistry registry, MetricsConfig metricsConfig) {
        this.registry = registry;
        this.metricsConfig = metricsConfig;

        this.orderCounter = Counter.builder("app.orders.total")
            .description("Total orders processed")
            .tag("service", "order")
            .register(registry);

        this.errorCounter = Counter.builder("app.errors.total")
            .description("Total errors")
            .tag("type", "processing")
            .register(registry);

        this.orderProcessingTimer = Timer.builder("app.order.processing.time")
            .description("Order processing time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.orderValueSummary = DistributionSummary.builder("app.order.value")
            .description("Distribution of order values")
            .baseUnit("dollars")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);

        // Gauge for total revenue
        Gauge.builder("app.revenue.total", totalRevenue, AtomicLong::get)
            .description("Total revenue")
            .register(registry);
    }

    public Map<String, Object> processOrder(String productId, int quantity) {
        metricsConfig.getActiveUsers().incrementAndGet();
        metricsConfig.getQueueSize().incrementAndGet();

        return orderProcessingTimer.record(() -> {
            try {
                // Simulate variable processing time
                Thread.sleep(random.nextInt(200) + 50);

                double price = (random.nextInt(100) + 1) * quantity;

                if (random.nextDouble() < 0.05) { // 5% error rate
                    errorCounter.increment();
                    registry.counter("app.orders.failed", "reason", "processing_error").increment();
                    throw new RuntimeException("Processing error for product: " + productId);
                }

                orderCounter.increment();
                orderValueSummary.record(price);
                totalRevenue.addAndGet((long) price);
                registry.counter("app.orders.success", "product", productId).increment();

                log.info("Processed order for {}, quantity: {}, price: {}", productId, quantity, price);

                return Map.of(
                    "orderId", "ORD-" + System.currentTimeMillis(),
                    "productId", productId,
                    "quantity", quantity,
                    "price", price,
                    "status", "PROCESSED"
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            } finally {
                metricsConfig.getActiveUsers().decrementAndGet();
                metricsConfig.getQueueSize().decrementAndGet();
            }
        });
    }

    public Map<String, Object> getMetricsSummary() {
        return Map.of(
            "totalOrders", (long) orderCounter.count(),
            "totalErrors", (long) errorCounter.count(),
            "totalRevenue", totalRevenue.get(),
            "activeUsers", metricsConfig.getActiveUsers().get()
        );
    }
}
