package com.distributed.tx.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicDouble;

@RestController
public class PaymentParticipantController {

    private static final Logger log = LoggerFactory.getLogger(PaymentParticipantController.class);
    private final AtomicDouble balance = new AtomicDouble(10000.0);
    private final Map<String, Double> preparedTx = new ConcurrentHashMap<>();
    private final Map<String, Double> tccReservations = new ConcurrentHashMap<>();

    @PostMapping("/api/2pc/prepare")
    public Map<String, Object> prepare2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        double amount = ((Number) request.get("amount")).doubleValue();

        if (balance.get() >= amount) {
            preparedTx.put(txId, amount);
            balance.addAndGet(-amount);
            log.info("[2PC] Prepared tx {} - reserved amount {}", txId, amount);
            return Map.of("vote", "YES", "txId", txId);
        }
        log.warn("[2PC] Vote NO for tx {} - insufficient balance", txId);
        return Map.of("vote", "NO", "reason", "Insufficient balance: " + balance.get());
    }

    @PostMapping("/api/2pc/commit")
    public Map<String, Object> commit2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        preparedTx.remove(txId);
        log.info("[2PC] Committed tx {}", txId);
        return Map.of("status", "COMMITTED");
    }

    @PostMapping("/api/2pc/abort")
    public Map<String, Object> abort2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        Double amount = preparedTx.remove(txId);
        if (amount != null) balance.addAndGet(amount);
        log.info("[2PC] Aborted tx {} - released amount {}", txId, amount);
        return Map.of("status", "ABORTED");
    }

    @PostMapping("/api/tcc/try")
    public Map<String, Object> tccTry(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        double amount = ((Number) request.get("amount")).doubleValue();

        if (balance.get() >= amount) {
            tccReservations.put(txId, amount);
            balance.addAndGet(-amount);
            log.info("[TCC] Try success for tx {}", txId);
            return Map.of("success", true);
        }
        log.warn("[TCC] Try failed for tx {} - insufficient balance", txId);
        return Map.of("success", false, "reason", "Insufficient balance");
    }

    @PostMapping("/api/tcc/confirm")
    public Map<String, Object> tccConfirm(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        tccReservations.remove(txId);
        log.info("[TCC] Confirmed tx {}", txId);
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/api/tcc/cancel")
    public Map<String, Object> tccCancel(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        Double amount = tccReservations.remove(txId);
        if (amount != null) balance.addAndGet(amount);
        log.info("[TCC] Cancelled tx {}", txId);
        return Map.of("status", "CANCELLED");
    }

    @GetMapping("/api/payment/status")
    public Map<String, Object> getStatus() {
        return Map.of("balance", balance.get(), "preparedTx", preparedTx.size(), "tccReservations", tccReservations.size());
    }
}
