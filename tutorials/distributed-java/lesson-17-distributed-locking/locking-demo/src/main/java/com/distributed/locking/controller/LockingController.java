package com.distributed.locking.controller;

import com.distributed.locking.service.DistributedLockService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/locks")
public class LockingController {

    private final DistributedLockService lockService;

    public LockingController(DistributedLockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping("/increment/{resourceId}")
    public Map<String, Object> increment(@PathVariable String resourceId) throws InterruptedException {
        return lockService.incrementWithLock(resourceId);
    }

    @PostMapping("/read/{resourceId}")
    public Map<String, Object> readLock(@PathVariable String resourceId) throws InterruptedException {
        return lockService.useReadWriteLock(resourceId, false);
    }

    @PostMapping("/write/{resourceId}")
    public Map<String, Object> writeLock(@PathVariable String resourceId) throws InterruptedException {
        return lockService.useReadWriteLock(resourceId, true);
    }

    @PostMapping("/fair/{resourceId}")
    public Map<String, Object> fairLock(@PathVariable String resourceId) throws InterruptedException {
        return lockService.useFairLock(resourceId);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return lockService.getStats();
    }
}
