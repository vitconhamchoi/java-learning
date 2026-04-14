package com.distributed.locking.service;

import org.redisson.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private final RedissonClient redisson;
    private final AtomicLong counter = new AtomicLong(0);
    private final Map<String, Long> operationLog = new ConcurrentHashMap<>();

    public DistributedLockService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public Map<String, Object> incrementWithLock(String resourceId) throws InterruptedException {
        RLock lock = redisson.getLock("lock:" + resourceId);
        long start = System.currentTimeMillis();
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                return Map.of("status", "FAILED", "reason", "Could not acquire lock");
            }
            log.info("Lock acquired for resource: {}", resourceId);
            // Simulate work
            Thread.sleep(100);
            long value = counter.incrementAndGet();
            operationLog.put(resourceId + "-" + value, System.currentTimeMillis());
            return Map.of(
                "status", "SUCCESS",
                "resourceId", resourceId,
                "counterValue", value,
                "lockWaitMs", System.currentTimeMillis() - start
            );
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released for resource: {}", resourceId);
            }
        }
    }

    public Map<String, Object> useReadWriteLock(String resourceId, boolean write) throws InterruptedException {
        RReadWriteLock rwLock = redisson.getReadWriteLock("rwlock:" + resourceId);
        RLock lock = write ? rwLock.writeLock() : rwLock.readLock();
        String lockType = write ? "WRITE" : "READ";
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                return Map.of("status", "FAILED", "lockType", lockType, "reason", "Could not acquire " + lockType + " lock");
            }
            log.info("{} lock acquired for resource: {}", lockType, resourceId);
            Thread.sleep(write ? 200 : 50);
            return Map.of(
                "status", "SUCCESS",
                "lockType", lockType,
                "resourceId", resourceId,
                "threadId", Thread.currentThread().getId()
            );
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public Map<String, Object> useFairLock(String resourceId) throws InterruptedException {
        RLock fairLock = redisson.getFairLock("fairlock:" + resourceId);
        boolean locked = false;
        try {
            locked = fairLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                return Map.of("status", "FAILED", "reason", "Could not acquire fair lock");
            }
            log.info("Fair lock acquired for resource: {}", resourceId);
            Thread.sleep(150);
            return Map.of(
                "status", "SUCCESS",
                "lockType", "FAIR",
                "resourceId", resourceId,
                "threadId", Thread.currentThread().getId()
            );
        } finally {
            if (locked && fairLock.isHeldByCurrentThread()) {
                fairLock.unlock();
            }
        }
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "totalOperations", counter.get(),
            "loggedOperations", operationLog.size()
        );
    }
}
