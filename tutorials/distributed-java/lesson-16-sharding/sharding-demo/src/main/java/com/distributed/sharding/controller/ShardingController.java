package com.distributed.sharding.controller;

import com.distributed.sharding.model.User;
import com.distributed.sharding.service.ShardingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sharding")
public class ShardingController {

    private final ShardingService shardingService;

    public ShardingController(ShardingService shardingService) {
        this.shardingService = shardingService;
    }

    @PostMapping("/hash")
    public User saveHashShard(@RequestBody User user) {
        return shardingService.saveWithHashSharding(user);
    }

    @PostMapping("/range")
    public User saveRangeShard(@RequestBody User user) {
        return shardingService.saveWithRangeSharding(user);
    }

    @PostMapping("/consistent-hashing")
    public User saveConsistentHash(@RequestBody User user) {
        return shardingService.saveWithConsistentHashing(user);
    }

    @GetMapping("/users/{id}")
    public User findById(@PathVariable Long id) {
        return shardingService.findById(id).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @GetMapping("/users")
    public List<User> findAll() {
        return shardingService.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return shardingService.getShardStats();
    }

    @GetMapping("/hash-ring")
    public Map<String, Object> getHashRing() {
        return shardingService.getHashRingInfo();
    }

    @PostMapping("/demo/populate")
    public Map<String, Object> populateDemo() {
        for (long i = 1; i <= 30; i++) {
            User user = new User(i, "user" + i, "user" + i + "@example.com", "VN", 0);
            shardingService.saveWithHashSharding(user);
        }
        return Map.of("message", "Populated 30 users with hash sharding", "stats", shardingService.getShardStats());
    }
}
