package com.distributed.eventsourcing.model;

import java.util.ArrayList;
import java.util.List;

public class Account {
    private String accountId;
    private String owner;
    private double balance;
    private AccountStatus status;
    private int version;

    public enum AccountStatus { ACTIVE, CLOSED }

    private Account() {}

    public static Account replay(List<AccountEvent> events) {
        if (events.isEmpty()) throw new IllegalStateException("No events to replay");
        Account account = new Account();
        for (AccountEvent event : events) {
            account.apply(event);
        }
        return account;
    }

    private void apply(AccountEvent event) {
        switch (event.getEventType()) {
            case ACCOUNT_CREATED -> {
                this.accountId = event.getAccountId();
                this.owner = event.getDescription();
                this.balance = event.getAmount();
                this.status = AccountStatus.ACTIVE;
            }
            case MONEY_DEPOSITED -> this.balance += event.getAmount();
            case MONEY_WITHDRAWN -> this.balance -= event.getAmount();
            case ACCOUNT_CLOSED -> this.status = AccountStatus.CLOSED;
        }
        this.version = event.getVersion();
    }

    public String getAccountId() { return accountId; }
    public String getOwner() { return owner; }
    public double getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
}
