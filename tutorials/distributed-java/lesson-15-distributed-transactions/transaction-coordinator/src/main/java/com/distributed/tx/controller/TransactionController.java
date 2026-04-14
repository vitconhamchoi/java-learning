package com.distributed.tx.controller;

import com.distributed.tx.model.DistributedTransaction;
import com.distributed.tx.service.TCCCoordinator;
import com.distributed.tx.service.TwoPhaseCommitCoordinator;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TwoPhaseCommitCoordinator tpcCoordinator;
    private final TCCCoordinator tccCoordinator;

    public TransactionController(TwoPhaseCommitCoordinator tpcCoordinator, TCCCoordinator tccCoordinator) {
        this.tpcCoordinator = tpcCoordinator;
        this.tccCoordinator = tccCoordinator;
    }

    @PostMapping("/2pc")
    public DistributedTransaction execute2PC(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        double amount = ((Number) request.get("amount")).doubleValue();
        return tpcCoordinator.execute2PC(productId, quantity, amount);
    }

    @PostMapping("/tcc")
    public DistributedTransaction executeTCC(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();
        double amount = ((Number) request.get("amount")).doubleValue();
        return tccCoordinator.executeTCC(productId, quantity, amount);
    }

    @GetMapping
    public Collection<DistributedTransaction> getAllTransactions() {
        var all = new java.util.ArrayList<DistributedTransaction>();
        all.addAll(tpcCoordinator.getAllTransactions());
        all.addAll(tccCoordinator.getAllTransactions());
        return all;
    }
}
