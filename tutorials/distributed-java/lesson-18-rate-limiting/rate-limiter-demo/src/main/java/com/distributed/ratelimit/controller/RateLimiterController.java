package com.distributed.ratelimit.controller;

import com.distributed.ratelimit.service.RedisRateLimiterService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rate-limit")
public class RateLimiterController {

    private final RedisRateLimiterService rateLimiterService;

    public RateLimiterController(RedisRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/fixed-window")
    public ResponseEntity<Map<String, Object>> fixedWindow(
            @RequestParam(defaultValue = "default-client") String clientId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "60") int windowSeconds) {
        Map<String, Object> result = rateLimiterService.fixedWindowCheck(clientId, limit, windowSeconds);
        boolean allowed = (boolean) result.get("allowed");
        return ResponseEntity.status(allowed ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS).body(result);
    }

    @GetMapping("/sliding-window")
    public ResponseEntity<Map<String, Object>> slidingWindow(
            @RequestParam(defaultValue = "default-client") String clientId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "60000") int windowMs) {
        Map<String, Object> result = rateLimiterService.slidingWindowCheck(clientId, limit, windowMs);
        boolean allowed = (boolean) result.get("allowed");
        return ResponseEntity.status(allowed ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS).body(result);
    }

    @GetMapping("/token-bucket")
    public ResponseEntity<Map<String, Object>> tokenBucket(
            @RequestParam(defaultValue = "default-client") String clientId,
            @RequestParam(defaultValue = "10") int capacity,
            @RequestParam(defaultValue = "2") int refillRate) {
        Map<String, Object> result = rateLimiterService.tokenBucketCheck(clientId, capacity, refillRate);
        boolean allowed = (boolean) result.get("allowed");
        return ResponseEntity.status(allowed ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS).body(result);
    }

    @GetMapping("/resilience4j")
    @RateLimiter(name = "apiRateLimiter", fallbackMethod = "rateLimitFallback")
    public Map<String, Object> resilience4jRateLimit() {
        return Map.of("status", "OK", "message", "Request processed successfully", "strategy", "RESILIENCE4J");
    }

    public Map<String, Object> rateLimitFallback(Exception ex) {
        return Map.of("status", "RATE_LIMITED", "message", "Too many requests. Please slow down.", "strategy", "RESILIENCE4J");
    }
}
