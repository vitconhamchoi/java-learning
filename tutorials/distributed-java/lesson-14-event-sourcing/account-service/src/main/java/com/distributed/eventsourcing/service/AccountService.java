package com.distributed.eventsourcing.service;

import com.distributed.eventsourcing.model.Account;
import com.distributed.eventsourcing.model.AccountEvent;
import com.distributed.eventsourcing.store.EventStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final EventStore eventStore;

    public AccountService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public Account createAccount(String owner, double initialBalance) {
        String accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int version = 1;

        AccountEvent event = new AccountEvent(accountId, AccountEvent.EventType.ACCOUNT_CREATED,
            initialBalance, owner, version);
        eventStore.append(event);

        return Account.replay(eventStore.getEvents(accountId));
    }

    public Account deposit(String accountId, double amount, String description) {
        validateAccount(accountId);
        int nextVersion = eventStore.getCurrentVersion(accountId) + 1;

        AccountEvent event = new AccountEvent(accountId, AccountEvent.EventType.MONEY_DEPOSITED,
            amount, description, nextVersion);
        eventStore.append(event);

        return Account.replay(eventStore.getEvents(accountId));
    }

    public Account withdraw(String accountId, double amount, String description) {
        Account current = getAccount(accountId);
        if (current.getBalance() < amount) {
            throw new IllegalStateException("Insufficient balance. Current: " + current.getBalance() + ", Requested: " + amount);
        }

        int nextVersion = eventStore.getCurrentVersion(accountId) + 1;
        AccountEvent event = new AccountEvent(accountId, AccountEvent.EventType.MONEY_WITHDRAWN,
            amount, description, nextVersion);
        eventStore.append(event);

        return Account.replay(eventStore.getEvents(accountId));
    }

    public Account getAccount(String accountId) {
        List<AccountEvent> events = eventStore.getEvents(accountId);
        if (events.isEmpty()) throw new RuntimeException("Account not found: " + accountId);
        return Account.replay(events);
    }

    public Account getAccountAtVersion(String accountId, int version) {
        List<AccountEvent> events = eventStore.getEvents(accountId).stream()
            .filter(e -> e.getVersion() <= version)
            .toList();
        if (events.isEmpty()) throw new RuntimeException("No events found for account " + accountId + " at version " + version);
        return Account.replay(events);
    }

    public List<AccountEvent> getEventHistory(String accountId) {
        return eventStore.getEvents(accountId);
    }

    public List<AccountEvent> getAllEvents() {
        return eventStore.getAllEvents();
    }

    private void validateAccount(String accountId) {
        Account account = getAccount(accountId);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Account is closed: " + accountId);
        }
    }
}
