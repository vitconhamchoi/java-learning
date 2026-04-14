package com.distributed.tx.service;

import com.distributed.tx.model.DistributedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TCCCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TCCCoordinator.class);
    private final Map<String, DistributedTransaction> transactions = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${inventory.participant.url:http://localhost:8081}")
    private String inventoryUrl;

    @Value("${payment.participant.url:http://localhost:8082}")
    private String paymentUrl;

    public DistributedTransaction executeTCC(String productId, int quantity, double amount) {
        String txId = "TCC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DistributedTransaction tx = new DistributedTransaction(txId, DistributedTransaction.TxProtocol.TCC, productId, quantity, amount);
        transactions.put(txId, tx);

        // Try phase
        log.info("[TCC] TRY phase for tx: {}", txId);
        boolean inventoryTried = tccTry(inventoryUrl + "/api/tcc/try", txId, productId, quantity, amount);
        boolean paymentTried = tccTry(paymentUrl + "/api/tcc/try", txId, productId, quantity, amount);

        if (inventoryTried && paymentTried) {
            // Confirm phase
            log.info("[TCC] CONFIRM phase for tx: {}", txId);
            tx.setState(DistributedTransaction.TxState.COMMITTING);
            tccConfirm(inventoryUrl + "/api/tcc/confirm", txId);
            tccConfirm(paymentUrl + "/api/tcc/confirm", txId);
            tx.setState(DistributedTransaction.TxState.COMMITTED);
            tx.addVote("overall", "YES", "TCC Confirmed");
        } else {
            // Cancel phase
            log.warn("[TCC] CANCEL phase for tx: {}", txId);
            tx.setState(DistributedTransaction.TxState.ABORTING);
            if (inventoryTried) tccCancel(inventoryUrl + "/api/tcc/cancel", txId);
            if (paymentTried) tccCancel(paymentUrl + "/api/tcc/cancel", txId);
            tx.setState(DistributedTransaction.TxState.ABORTED);
            tx.addVote("overall", "NO", "TCC Cancelled");
        }

        return tx;
    }

    private boolean tccTry(String url, String txId, String productId, int quantity, double amount) {
        try {
            Map<?, ?> response = restTemplate.postForObject(url, Map.of("txId", txId, "productId", productId, "quantity", quantity, "amount", amount), Map.class);
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (Exception e) {
            log.error("TCC Try failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private void tccConfirm(String url, String txId) {
        try { restTemplate.postForObject(url, Map.of("txId", txId), Map.class); }
        catch (Exception e) { log.error("TCC Confirm failed for {}: {}", url, e.getMessage()); }
    }

    private void tccCancel(String url, String txId) {
        try { restTemplate.postForObject(url, Map.of("txId", txId), Map.class); }
        catch (Exception e) { log.error("TCC Cancel failed for {}: {}", url, e.getMessage()); }
    }

    public DistributedTransaction getTransaction(String txId) { return transactions.get(txId); }
    public java.util.Collection<DistributedTransaction> getAllTransactions() { return transactions.values(); }
}
