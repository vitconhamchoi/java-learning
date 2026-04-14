package com.distributed.tx.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DistributedTransaction {
    private String txId;
    private TxState state;
    private TxProtocol protocol;
    private List<ParticipantVote> votes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String productId;
    private int quantity;
    private double amount;

    public enum TxState { PREPARING, PREPARED, COMMITTING, COMMITTED, ABORTING, ABORTED }
    public enum TxProtocol { TWO_PHASE_COMMIT, TCC }

    public record ParticipantVote(String participant, String vote, String reason) {}

    public DistributedTransaction(String txId, TxProtocol protocol, String productId, int quantity, double amount) {
        this.txId = txId;
        this.protocol = protocol;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.state = TxState.PREPARING;
        this.votes = new ArrayList<>();
        this.startedAt = LocalDateTime.now();
    }

    public void addVote(String participant, String vote, String reason) {
        votes.add(new ParticipantVote(participant, vote, reason));
    }

    public boolean allVotedYes() {
        return votes.stream().allMatch(v -> "YES".equals(v.vote()));
    }

    public String getTxId() { return txId; }
    public TxState getState() { return state; }
    public void setState(TxState state) { this.state = state; if (state == TxState.COMMITTED || state == TxState.ABORTED) completedAt = LocalDateTime.now(); }
    public TxProtocol getProtocol() { return protocol; }
    public List<ParticipantVote> getVotes() { return votes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getAmount() { return amount; }
}
