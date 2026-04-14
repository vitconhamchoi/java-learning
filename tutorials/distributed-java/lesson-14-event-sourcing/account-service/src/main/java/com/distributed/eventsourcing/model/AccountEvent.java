package com.distributed.eventsourcing.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class AccountEvent {
    private String eventId;
    private String accountId;
    private EventType eventType;
    private double amount;
    private String description;
    private LocalDateTime occurredAt;
    private int version;

    public enum EventType {
        ACCOUNT_CREATED, MONEY_DEPOSITED, MONEY_WITHDRAWN, ACCOUNT_CLOSED
    }

    public AccountEvent(String accountId, EventType eventType, double amount, String description, int version) {
        this.eventId = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.eventType = eventType;
        this.amount = amount;
        this.description = description;
        this.occurredAt = LocalDateTime.now();
        this.version = version;
    }

    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public EventType getEventType() { return eventType; }
    public double getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public int getVersion() { return version; }
}
