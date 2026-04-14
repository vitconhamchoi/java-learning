package com.distributed.sharding.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;
    private String username;
    private String email;
    private String region;
    private int shardId;

    public User() {}
    public User(Long id, String username, String email, String region, int shardId) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.region = region;
        this.shardId = shardId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getShardId() { return shardId; }
    public void setShardId(int shardId) { this.shardId = shardId; }
}
