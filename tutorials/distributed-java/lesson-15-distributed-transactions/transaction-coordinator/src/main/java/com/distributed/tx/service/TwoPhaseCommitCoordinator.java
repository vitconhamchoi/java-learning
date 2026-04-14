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
public class TwoPhaseCommitCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TwoPhaseCommitCoordinator.class);
    private final Map<String, DistributedTransaction> transactions = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${inventory.participant.url:http://localhost:8081}")
    private String inventoryUrl;

    @Value("${payment.participant.url:http://localhost:8082}")
    private String paymentUrl;

    public DistributedTransaction execute2PC(String productId, int quantity, double amount) {
        String txId = "2PC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DistributedTransaction tx = new DistributedTransaction(txId, DistributedTransaction.TxProtocol.TWO_PHASE_COMMIT, productId, quantity, amount);
        transactions.put(txId, tx);

        // Phase 1: Prepare
        log.info("[2PC] Phase 1 - PREPARE for tx: {}", txId);
        tx.setState(DistributedTransaction.TxState.PREPARING);

        boolean inventoryVote = sendPrepare(inventoryUrl + "/api/2pc/prepare", txId, productId, quantity, amount);
        tx.addVote("inventory", inventoryVote ? "YES" : "NO", inventoryVote ? "OK" : "Failed to reserve");

        boolean paymentVote = sendPrepare(paymentUrl + "/api/2pc/prepare", txId, productId, quantity, amount);
        tx.addVote("payment", paymentVote ? "YES" : "NO", paymentVote ? "OK" : "Insufficient funds");

        tx.setState(DistributedTransaction.TxState.PREPARED);

        // Phase 2: Commit or Abort
        if (tx.allVotedYes()) {
            log.info("[2PC] Phase 2 - COMMIT for tx: {}", txId);
            tx.setState(DistributedTransaction.TxState.COMMITTING);
            sendCommit(inventoryUrl + "/api/2pc/commit", txId);
            sendCommit(paymentUrl + "/api/2pc/commit", txId);
            tx.setState(DistributedTransaction.TxState.COMMITTED);
        } else {
            log.warn("[2PC] Phase 2 - ABORT for tx: {}", txId);
            tx.setState(DistributedTransaction.TxState.ABORTING);
            sendAbort(inventoryUrl + "/api/2pc/abort", txId);
            sendAbort(paymentUrl + "/api/2pc/abort", txId);
            tx.setState(DistributedTransaction.TxState.ABORTED);
        }

        return tx;
    }

    private boolean sendPrepare(String url, String txId, String productId, int quantity, double amount) {
        try {
            Map<?, ?> response = restTemplate.postForObject(url, Map.of("txId", txId, "productId", productId, "quantity", quantity, "amount", amount), Map.class);
            return response != null && "YES".equals(response.get("vote"));
        } catch (Exception e) {
            log.error("Prepare failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private void sendCommit(String url, String txId) {
        try { restTemplate.postForObject(url, Map.of("txId", txId), Map.class); }
        catch (Exception e) { log.error("Commit failed for {}: {}", url, e.getMessage()); }
    }

    private void sendAbort(String url, String txId) {
        try { restTemplate.postForObject(url, Map.of("txId", txId), Map.class); }
        catch (Exception e) { log.error("Abort failed for {}: {}", url, e.getMessage()); }
    }

    public DistributedTransaction getTransaction(String txId) { return transactions.get(txId); }
    public java.util.Collection<DistributedTransaction> getAllTransactions() { return transactions.values(); }
}
