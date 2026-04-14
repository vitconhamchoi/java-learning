package com.distributed.eventsourcing.store;

import com.distributed.eventsourcing.model.AccountEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class EventStore {

    private final List<AccountEvent> globalEventLog = new CopyOnWriteArrayList<>();
    private final Map<String, List<AccountEvent>> eventsByAggregate = new ConcurrentHashMap<>();

    public void append(AccountEvent event) {
        eventsByAggregate.computeIfAbsent(event.getAccountId(), k -> new CopyOnWriteArrayList<>()).add(event);
        globalEventLog.add(event);
    }

    public List<AccountEvent> getEvents(String accountId) {
        return eventsByAggregate.getOrDefault(accountId, List.of());
    }

    public List<AccountEvent> getEventsFromVersion(String accountId, int fromVersion) {
        return getEvents(accountId).stream()
            .filter(e -> e.getVersion() >= fromVersion)
            .collect(Collectors.toList());
    }

    public List<AccountEvent> getAllEvents() {
        return new ArrayList<>(globalEventLog);
    }

    public int getCurrentVersion(String accountId) {
        List<AccountEvent> events = getEvents(accountId);
        return events.isEmpty() ? 0 : events.get(events.size() - 1).getVersion();
    }
}
