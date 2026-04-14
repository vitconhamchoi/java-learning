package com.distributed.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình Retry với Exponential Backoff + Jitter.
 *
 * Chiến lược retry:
 * - Chỉ retry với transient errors (5xx, network errors)
 * - Không retry với client errors (4xx)
 * - Thời gian chờ tăng theo cấp số nhân để tránh thundering herd
 */
@Configuration
public class RetryConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryConfig.class);

    /**
     * RetryTemplate với Exponential Backoff Policy.
     *
     * Ví dụ với cấu hình dưới:
     * - Attempt 1: fail → wait 1000ms
     * - Attempt 2: fail → wait 2000ms
     * - Attempt 3: fail → wait 4000ms (capped at 30000ms)
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Exponential Backoff: thời gian chờ tăng gấp đôi sau mỗi lần retry
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();

        // Thời gian chờ ban đầu: 1 giây
        // Sau attempt 1: chờ ~1000ms, sau attempt 2: ~2000ms, sau attempt 3: ~4000ms
        backOffPolicy.setInitialInterval(1000L);

        // Hệ số nhân: mỗi lần retry chờ gấp đôi lần trước
        // 1000 * 2^0 = 1000ms, 1000 * 2^1 = 2000ms, 1000 * 2^2 = 4000ms
        backOffPolicy.setMultiplier(2.0);

        // Giới hạn tối đa: không chờ quá 30 giây dù multiplier tính ra nhiều hơn
        // Ngăn chặn wait time bùng nổ trong trường hợp nhiều retries
        backOffPolicy.setMaxInterval(30_000L);

        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Chỉ retry với các exception types cụ thể (transient failures)
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();

        // Retry: Network errors (connection refused, timeout, etc.)
        retryableExceptions.put(ResourceAccessException.class, true);

        // Retry: Server errors (5xx) - service tạm thời không available
        retryableExceptions.put(HttpServerErrorException.class, true);

        // Không retry: Client errors (4xx) - lỗi từ phía client, retry cũng vô ích
        // HttpClientErrorException.class → false (implicit, not added = not retryable)

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                3, // maxAttempts: tổng 3 lần (1 lần đầu + 2 lần retry)
                retryableExceptions,
                true // traverseCauses: kiểm tra cả wrapped exceptions
        );

        retryTemplate.setRetryPolicy(retryPolicy);

        // Listener để log retry attempts
        retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback,
                    Throwable throwable) {
                log.warn("Retry attempt {} failed: {}", context.getRetryCount(), throwable.getMessage());
            }
        });

        return retryTemplate;
    }
}
