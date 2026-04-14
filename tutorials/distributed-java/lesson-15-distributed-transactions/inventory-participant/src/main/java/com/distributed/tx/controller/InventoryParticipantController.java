package com.distributed.tx.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class InventoryParticipantController {

    private static final Logger log = LoggerFactory.getLogger(InventoryParticipantController.class);
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>(Map.of("PROD-001", 100, "PROD-002", 50));
    private final Map<String, Map<String, Object>> preparedTx = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> tccReservations = new ConcurrentHashMap<>();

    // 2PC endpoints
    @PostMapping("/api/2pc/prepare")
    public Map<String, Object> prepare2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();

        int available = inventory.getOrDefault(productId, 0);
        if (available >= quantity) {
            preparedTx.put(txId, Map.of("productId", productId, "quantity", quantity));
            inventory.put(productId, available - quantity);
            log.info("[2PC] Prepared tx {} - reserved {} of {}", txId, quantity, productId);
            return Map.of("vote", "YES", "txId", txId);
        }
        log.warn("[2PC] Vote NO for tx {} - insufficient stock", txId);
        return Map.of("vote", "NO", "reason", "Insufficient stock: " + available);
    }

    @PostMapping("/api/2pc/commit")
    public Map<String, Object> commit2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        preparedTx.remove(txId);
        log.info("[2PC] Committed tx {}", txId);
        return Map.of("status", "COMMITTED", "txId", txId);
    }

    @PostMapping("/api/2pc/abort")
    public Map<String, Object> abort2PC(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        Map<String, Object> prepared = preparedTx.remove(txId);
        if (prepared != null) {
            String productId = (String) prepared.get("productId");
            int quantity = (int) prepared.get("quantity");
            inventory.merge(productId, quantity, Integer::sum);
            log.info("[2PC] Aborted tx {} - released {} of {}", txId, quantity, productId);
        }
        return Map.of("status", "ABORTED", "txId", txId);
    }

    // TCC endpoints
    @PostMapping("/api/tcc/try")
    public Map<String, Object> tccTry(@RequestBody Map<String, Object> request) {
        String txId = (String) request.get("txId");
        String productId = (String) request.get("productId");
        int quantity = ((Number) request.get("quantity")).intValue();

        int available = inventory.getOrDefault(productId, 0);
        if (available >= quantity) {
            tccReservations.put(txId, Map.of("productId", productId, "quantity", quantity));
            inventory.put(productId, available - quantity);
            log.info("[TCC] Try success for tx {}", txId);
            return Map.of("success", true, "txId", txId);
        }
        log.warn("[TCC] Try failed for tx {} - insufficient stock", txId);
        return Map.of("success", false, "reason", "Insufficient stock");
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
        Map<String, Object> reservation = tccReservations.remove(txId);
        if (reservation != null) {
            inventory.merge((String) reservation.get("productId"), (int) reservation.get("quantity"), Integer::sum);
        }
        log.info("[TCC] Cancelled tx {}", txId);
        return Map.of("status", "CANCELLED");
    }

    @GetMapping("/api/inventory/status")
    public Map<String, Object> getStatus() {
        return Map.of("inventory", inventory, "preparedTx", preparedTx.size(), "tccReservations", tccReservations.size());
    }
}
