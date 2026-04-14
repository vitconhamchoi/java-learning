package com.distributed.cqrs.repository;

import com.distributed.cqrs.model.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductViewRepository extends JpaRepository<ProductView, Long> {
    List<ProductView> findByNameContainingIgnoreCase(String name);
    List<ProductView> findByPriceBetween(double min, double max);
    List<ProductView> findByStockGreaterThan(int minStock);
}
