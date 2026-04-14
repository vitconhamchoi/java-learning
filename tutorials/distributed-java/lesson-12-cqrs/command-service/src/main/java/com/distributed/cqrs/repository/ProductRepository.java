package com.distributed.cqrs.repository;

import com.distributed.cqrs.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}
