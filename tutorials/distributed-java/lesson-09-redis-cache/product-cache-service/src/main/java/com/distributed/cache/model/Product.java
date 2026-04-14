package com.distributed.cache.model;
import java.io.Serializable;
import java.math.BigDecimal;
public record Product(String id, String name, BigDecimal price, String category) implements Serializable {}
