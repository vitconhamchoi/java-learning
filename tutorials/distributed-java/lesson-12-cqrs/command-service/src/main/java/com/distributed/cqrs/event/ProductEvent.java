package com.distributed.cqrs.event;

import java.time.LocalDateTime;

public record ProductEvent(
    String eventType,
    Long productId,
    String productName,
    double price,
    int stock,
    LocalDateTime occurredAt
) {}
