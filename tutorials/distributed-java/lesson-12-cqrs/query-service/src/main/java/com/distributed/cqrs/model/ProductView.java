package com.distributed.cqrs.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_views")
public class ProductView {
    @Id
    private Long productId;
    private String name;
    private String description;
    private double price;
    private int stock;
    private long viewCount;
    private LocalDateTime lastUpdated;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
