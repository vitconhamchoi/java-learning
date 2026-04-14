package com.distributed.rabbitmq.producer.model;
import java.time.Instant;
public record NotificationEvent(
    String id,
    Type type,
    String recipient,
    String subject,
    String body,
    int priority,
    Instant timestamp
) {
    public enum Type { EMAIL, SMS, PUSH }
    public NotificationEvent {
        if (timestamp == null) timestamp = Instant.now();
    }
}
