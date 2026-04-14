package com.distributed.fundamentals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eventual Consistency Demo sử dụng Gossip Protocol.
 *
 * Gossip Protocol (Anti-entropy):
 * - Mỗi node giữ local state (key-value store) với version numbers
 * - Định kỳ, mỗi node "gossip" với một peer ngẫu nhiên
 * - Khi gossip: so sánh versions, merge state với version cao hơn
 * - Cuối cùng, tất cả nodes sẽ converge về cùng state
 *
 * Eventual Consistency đảm bảo:
 * - Nếu không có update mới, tất cả nodes sẽ đạt được cùng state
 * - Không đảm bảo thứ tự nhất quán trong quá trình sync
 */
public class EventualConsistencyDemo {

    /**
     * Node trong gossip cluster.
     * Mỗi node duy trì:
     * - data: actual key-value data
     * - versions: version number cho mỗi key (để resolve conflicts - last-write-wins)
     */
    static class Node {
        private final String nodeId;
        private final Map<String, String> data;
        private final Map<String, Long> versions;

        public Node(String nodeId) {
            this.nodeId = nodeId;
            this.data = new HashMap<>();
            this.versions = new HashMap<>();
        }

        /**
         * Write local data với version increment.
         * Sử dụng timestamp làm version (trong thực tế nên dùng Hybrid Logical Clock).
         */
        public void update(String key, String value) {
            long newVersion = System.nanoTime(); // Dùng nanotime làm version
            data.put(key, value);
            versions.put(key, newVersion);
            System.out.printf("[%s] Local write: %s=%s (version=%d)%n",
                    nodeId, key, value, newVersion);
        }

        /**
         * Anti-entropy gossip với một peer node.
         * Merge strategy: Last-Write-Wins (LWW) dựa trên version number.
         *
         * Quá trình:
         * 1. So sánh versions cho mỗi key
         * 2. Nếu peer có version cao hơn → accept peer's value
         * 3. Nếu local có version cao hơn → giữ local value
         * 4. Nếu bằng nhau → không thay đổi
         */
        public void gossip(Node peer) {
            System.out.printf("[%s] Gossiping với %s...%n", nodeId, peer.nodeId);
            int updated = 0;

            // Kiểm tra tất cả keys của peer
            for (Map.Entry<String, Long> entry : peer.versions.entrySet()) {
                String key = entry.getKey();
                long peerVersion = entry.getValue();
                long localVersion = versions.getOrDefault(key, -1L);

                if (peerVersion > localVersion) {
                    // Peer có version mới hơn → update local
                    data.put(key, peer.data.get(key));
                    versions.put(key, peerVersion);
                    System.out.printf("  [%s] Updated %s=%s (local v=%d < peer v=%d)%n",
                            nodeId, key, peer.data.get(key), localVersion, peerVersion);
                    updated++;
                }
            }

            if (updated == 0) {
                System.out.printf("  [%s] No updates needed from %s%n", nodeId, peer.nodeId);
            }
        }

        /** Kiểm tra xem hai nodes đã converge chưa */
        public boolean isConsistentWith(Node other) {
            return data.equals(other.data) && versions.equals(other.versions);
        }

        public void printState() {
            System.out.printf("[%s] State: %s%n", nodeId, data);
        }

        public String getNodeId() {
            return nodeId;
        }

        public Map<String, String> getData() {
            return new HashMap<>(data);
        }
    }

    /**
     * Chạy gossip rounds cho đến khi tất cả nodes converge.
     */
    static void runGossipUntilConvergent(Node[] nodes, int maxRounds) {
        for (int round = 1; round <= maxRounds; round++) {
            System.out.printf("%n--- Gossip Round %d ---%n", round);

            // Mỗi node gossip với một peer ngẫu nhiên (simplified: gossip với tất cả)
            for (int i = 0; i < nodes.length; i++) {
                int peerIdx = ThreadLocalRandom.current().nextInt(nodes.length);
                if (peerIdx != i) {
                    nodes[i].gossip(nodes[peerIdx]);
                }
            }

            // Kiểm tra convergence
            boolean allConsistent = true;
            for (int i = 0; i < nodes.length - 1; i++) {
                if (!nodes[i].isConsistentWith(nodes[i + 1])) {
                    allConsistent = false;
                    break;
                }
            }

            System.out.printf("Round %d complete. All nodes consistent: %s%n", round, allConsistent);

            if (allConsistent) {
                System.out.printf("✓ Convergence đạt được sau %d gossip round(s)!%n", round);
                return;
            }
        }
        System.out.println("⚠ Chưa converge sau " + maxRounds + " rounds");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Eventual Consistency với Gossip Protocol ===\n");

        // Tạo 3 nodes
        Node node1 = new Node("Node-1");
        Node node2 = new Node("Node-2");
        Node node3 = new Node("Node-3");
        Node[] nodes = {node1, node2, node3};

        // === Scenario 1: Writes vào các nodes khác nhau ===
        System.out.println("=== Scenario 1: Concurrent writes vào các nodes khác nhau ===");
        System.out.println("Mô phỏng: User A update profile trên Node-1, User B update trên Node-2\n");

        node1.update("user:alice:name", "Alice Nguyen");
        node1.update("user:alice:email", "alice@example.com");

        Thread.sleep(1); // Đảm bảo version khác nhau

        node2.update("user:bob:name", "Bob Tran");
        node2.update("user:bob:status", "active");

        Thread.sleep(1);

        node3.update("config:feature_flag", "enabled");

        System.out.println("\n--- Trạng thái trước khi gossip (inconsistent) ---");
        for (Node node : nodes) {
            node.printState();
        }

        // Chạy gossip
        System.out.println();
        runGossipUntilConvergent(nodes, 5);

        System.out.println("\n--- Trạng thái sau khi gossip (converged) ---");
        for (Node node : nodes) {
            node.printState();
        }

        // === Scenario 2: Conflict resolution với Last-Write-Wins ===
        System.out.println("\n=== Scenario 2: Conflict Resolution (Last-Write-Wins) ===");
        System.out.println("Hai nodes cùng update một key → node nào update sau sẽ thắng\n");

        Node n1 = new Node("N1");
        Node n2 = new Node("N2");

        n1.update("cart:user123", "product-A,product-B");
        Thread.sleep(2); // Đảm bảo n2 có timestamp cao hơn
        n2.update("cart:user123", "product-A,product-C"); // Override với value mới hơn

        System.out.println("\nTrước gossip:");
        n1.printState();
        n2.printState();

        n1.gossip(n2);
        n2.gossip(n1);

        System.out.println("\nSau gossip (LWW resolution):");
        n1.printState(); // Phải thấy product-A,product-C (n2's later write wins)
        n2.printState();

        // === Scenario 3: Network partition simulation ===
        System.out.println("\n=== Scenario 3: Network Partition Simulation ===");
        System.out.println("Mô phỏng: Partition xảy ra, các nodes update độc lập, rồi reconcile\n");

        Node partA = new Node("PartA");
        Node partB = new Node("PartB");

        // Updates trước partition
        partA.update("counter", "10");
        partB.gossip(partA);

        System.out.println("--- Network partition xảy ra! ---");
        // Mỗi bên update độc lập trong thời gian partition
        Thread.sleep(1);
        partA.update("counter", "11"); // A tăng counter
        Thread.sleep(1);
        partB.update("counter", "15"); // B tăng counter nhiều hơn (sẽ win với LWW)

        System.out.println("\nTrong partition:");
        partA.printState();
        partB.printState();

        System.out.println("\n--- Partition heal: nodes reconcile ---");
        partA.gossip(partB);
        partB.gossip(partA);

        System.out.println("\nSau reconciliation:");
        partA.printState(); // LWW: B's "15" thắng vì có timestamp cao hơn
        partB.printState();

        System.out.println("\n=== Kết luận ===");
        System.out.println("✓ Eventual Consistency đảm bảo convergence cuối cùng");
        System.out.println("✓ Gossip protocol lan truyền updates theo cách epidemic");
        System.out.println("✓ LWW (Last-Write-Wins) là conflict resolution đơn giản nhất");
        System.out.println("⚠ LWW có thể mất updates! Xem xét dùng CRDTs cho critical data");
        System.out.println("⚠ Trong Scenario 3: update từ PartA (11→) bị mất do LWW");
    }
}
