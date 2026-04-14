package com.distributed.sharding.service;

import com.distributed.sharding.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ShardingService {

    private static final Logger log = LoggerFactory.getLogger(ShardingService.class);
    private static final int NUM_SHARDS = 3;
    private static final int VIRTUAL_NODES = 150;

    // In-memory shards simulation
    private final Map<Integer, Map<Long, User>> shards = new HashMap<>();
    private final TreeMap<Integer, Integer> hashRing = new TreeMap<>();

    public ShardingService() {
        for (int i = 0; i < NUM_SHARDS; i++) {
            shards.put(i, new ConcurrentHashMap<>());
        }
        buildConsistentHashRing();
    }

    private void buildConsistentHashRing() {
        for (int shard = 0; shard < NUM_SHARDS; shard++) {
            for (int v = 0; v < VIRTUAL_NODES / NUM_SHARDS; v++) {
                int hash = Math.abs(("shard-" + shard + "-vnode-" + v).hashCode()) % 1000;
                hashRing.put(hash, shard);
            }
        }
    }

    // Hash sharding: shard = id % NUM_SHARDS
    public User saveWithHashSharding(User user) {
        int shardId = (int) (Math.abs(user.getId()) % NUM_SHARDS);
        user.setShardId(shardId);
        shards.get(shardId).put(user.getId(), user);
        log.info("Hash sharding: User {} -> Shard {}", user.getId(), shardId);
        return user;
    }

    // Range sharding: shard by ID range
    public User saveWithRangeSharding(User user) {
        int shardId;
        long id = user.getId();
        if (id < 1000) shardId = 0;
        else if (id < 2000) shardId = 1;
        else shardId = 2;
        user.setShardId(shardId);
        shards.get(shardId).put(user.getId(), user);
        log.info("Range sharding: User {} (id={}) -> Shard {}", user.getUsername(), id, shardId);
        return user;
    }

    // Consistent hashing
    public User saveWithConsistentHashing(User user) {
        int hash = Math.abs(user.getId().hashCode()) % 1000;
        Map.Entry<Integer, Integer> entry = hashRing.ceilingEntry(hash);
        if (entry == null) entry = hashRing.firstEntry();
        int shardId = entry.getValue();
        user.setShardId(shardId);
        shards.get(shardId).put(user.getId(), user);
        log.info("Consistent hashing: User {} (hash={}) -> Shard {}", user.getId(), hash, shardId);
        return user;
    }

    public Optional<User> findById(Long id) {
        for (Map<Long, User> shard : shards.values()) {
            if (shard.containsKey(id)) return Optional.of(shard.get(id));
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        return shards.values().stream()
            .flatMap(shard -> shard.values().stream())
            .collect(Collectors.toList());
    }

    public Map<String, Object> getShardStats() {
        Map<String, Object> stats = new HashMap<>();
        for (int i = 0; i < NUM_SHARDS; i++) {
            stats.put("shard-" + i, Map.of("count", shards.get(i).size()));
        }
        stats.put("hashRingNodes", hashRing.size());
        stats.put("totalUsers", findAll().size());
        return stats;
    }

    public Map<String, Object> getHashRingInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("numShards", NUM_SHARDS);
        info.put("virtualNodes", hashRing.size());
        Map<Integer, Long> distribution = hashRing.values().stream()
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        info.put("vnodeDistribution", distribution);
        return info;
    }
}
