package com.distributed.eventsourcing.controller;

import com.distributed.eventsourcing.model.Account;
import com.distributed.eventsourcing.model.AccountEvent;
import com.distributed.eventsourcing.service.AccountService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public Account createAccount(@RequestBody Map<String, Object> request) {
        String owner = (String) request.get("owner");
        double initialBalance = ((Number) request.getOrDefault("initialBalance", 0)).doubleValue();
        return accountService.createAccount(owner, initialBalance);
    }

    @GetMapping("/{accountId}")
    public Account getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }

    @GetMapping("/{accountId}/version/{version}")
    public Account getAccountAtVersion(@PathVariable String accountId, @PathVariable int version) {
        return accountService.getAccountAtVersion(accountId, version);
    }

    @PostMapping("/{accountId}/deposit")
    public Account deposit(@PathVariable String accountId, @RequestBody Map<String, Object> request) {
        double amount = ((Number) request.get("amount")).doubleValue();
        String description = (String) request.getOrDefault("description", "Deposit");
        return accountService.deposit(accountId, amount, description);
    }

    @PostMapping("/{accountId}/withdraw")
    public Account withdraw(@PathVariable String accountId, @RequestBody Map<String, Object> request) {
        double amount = ((Number) request.get("amount")).doubleValue();
        String description = (String) request.getOrDefault("description", "Withdrawal");
        return accountService.withdraw(accountId, amount, description);
    }

    @GetMapping("/{accountId}/events")
    public List<AccountEvent> getEventHistory(@PathVariable String accountId) {
        return accountService.getEventHistory(accountId);
    }

    @GetMapping("/events/all")
    public List<AccountEvent> getAllEvents() {
        return accountService.getAllEvents();
    }
}
