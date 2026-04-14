package com.distributed.fundamentals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CAP Theorem Demo - minh họa sự trade-off giữa Consistency và Availability
 * trong trường hợp Network Partition.
 *
 * CP Cluster: Ưu tiên Consistency, từ chối write khi không có quorum.
 *             Ví dụ thực tế: MongoDB (write concern majority), HBase, Zookeeper.
 *
 * AP Cluster: Ưu tiên Availability, luôn accept write, sync sau.
 *             Ví dụ thực tế: Cassandra, DynamoDB, CouchDB.
 */
public class CAPTheoremDemo {

    /** Trạng thái của một node */
    enum NodeState { HEALTHY, PARTITIONED }

    /**
     * CP Cluster - Consistency + Partition Tolerance.
     *
     * Sử dụng Quorum Writes: majority của nodes phải ACK trước khi confirm write.
     * Nếu không đủ quorum (ví dụ do partition), write bị từ chối.
     *
     * Trade-off: Hy sinh Availability khi partition xảy ra.
     */
    static class CPCluster {
        private final List<Map<String, String>> nodeStores;
        private final List<NodeState> nodeStates;
        private final int totalNodes;
        private final int quorum;

        public CPCluster(int nodeCount) {
            this.totalNodes = nodeCount;
            this.quorum = (nodeCount / 2) + 1; // Majority quorum
            this.nodeStores = new ArrayList<>();
            this.nodeStates = new ArrayList<>();
            for (int i = 0; i < nodeCount; i++) {
                nodeStores.add(new HashMap<>());
                nodeStates.add(NodeState.HEALTHY);
            }
            System.out.printf("[CP Cluster] Initialized %d nodes, quorum=%d%n", nodeCount, quorum);
        }

        /** Simulate network partition: một số nodes bị cô lập */
        public void partitionNodes(int... partitionedNodeIds) {
            for (int id : partitionedNodeIds) {
                nodeStates.set(id, NodeState.PARTITIONED);
            }
            long available = nodeStates.stream().filter(s -> s == NodeState.HEALTHY).count();
            System.out.printf("[CP Cluster] PARTITION! Nodes %s isolated. Available nodes: %d/%d%n",
                    java.util.Arrays.toString(partitionedNodeIds), available, totalNodes);
        }

        /** Heal partition: tất cả nodes healthy lại */
        public void healPartition() {
            for (int i = 0; i < totalNodes; i++) {
                nodeStates.set(i, NodeState.HEALTHY);
            }
            System.out.println("[CP Cluster] Partition healed. All nodes healthy.");
            // Sync tất cả nodes với node 0 (leader)
            if (!nodeStores.get(0).isEmpty()) {
                for (int i = 1; i < totalNodes; i++) {
                    nodeStores.get(i).putAll(nodeStores.get(0));
                }
                System.out.println("[CP Cluster] Synced all nodes with leader (Node 0).");
            }
        }

        /**
         * Write với quorum requirement.
         * Yêu cầu majority nodes ACK trước khi confirm write.
         * Nếu không đủ quorum → từ chối write (hy sinh Availability).
         */
        public boolean write(String key, String value) {
            // Đếm số nodes available (không bị partition)
            List<Integer> availableNodes = new ArrayList<>();
            for (int i = 0; i < totalNodes; i++) {
                if (nodeStates.get(i) == NodeState.HEALTHY) {
                    availableNodes.add(i);
                }
            }

            if (availableNodes.size() < quorum) {
                // Không đủ quorum → từ chối write để đảm bảo consistency!
                System.out.printf("[CP Cluster] WRITE REJECTED: '%s=%s' | Available=%d < Quorum=%d | SACRIFICING AVAILABILITY!%n",
                        key, value, availableNodes.size(), quorum);
                return false;
            }

            // Đủ quorum → write vào tất cả available nodes
            for (int nodeId : availableNodes) {
                nodeStores.get(nodeId).put(key, value);
            }
            System.out.printf("[CP Cluster] WRITE OK: '%s=%s' | Replicated to %d/%d nodes (quorum met)%n",
                    key, value, availableNodes.size(), totalNodes);
            return true;
        }

        /** Read: chỉ đọc từ healthy node đầu tiên (simplified) */
        public String read(String key) {
            for (int i = 0; i < totalNodes; i++) {
                if (nodeStates.get(i) == NodeState.HEALTHY) {
                    String value = nodeStores.get(i).get(key);
                    System.out.printf("[CP Cluster] READ '%s' from Node %d → '%s'%n", key, i, value);
                    return value;
                }
            }
            System.out.printf("[CP Cluster] READ FAILED: No healthy nodes available%n");
            return null;
        }

        public void printState() {
            System.out.println("[CP Cluster] State per node:");
            for (int i = 0; i < totalNodes; i++) {
                System.out.printf("  Node %d [%s]: %s%n", i, nodeStates.get(i), nodeStores.get(i));
            }
        }
    }

    /**
     * AP Cluster - Availability + Partition Tolerance.
     *
     * Luôn accept writes vào local node, sync với peers sau (eventual consistency).
     * Ngay cả khi partition, vẫn accept writes → có thể có conflicting updates.
     *
     * Trade-off: Hy sinh Consistency, có thể đọc stale data.
     */
    static class APCluster {
        private final List<Map<String, String>> nodeStores;
        private final List<Map<String, Long>> versions; // version để merge conflicts
        private final List<NodeState> nodeStates;
        private final int totalNodes;
        private final AtomicInteger primaryNode;

        public APCluster(int nodeCount) {
            this.totalNodes = nodeCount;
            this.nodeStores = new ArrayList<>();
            this.versions = new ArrayList<>();
            this.nodeStates = new ArrayList<>();
            this.primaryNode = new AtomicInteger(0);
            for (int i = 0; i < nodeCount; i++) {
                nodeStores.add(new HashMap<>());
                versions.add(new HashMap<>());
                nodeStates.add(NodeState.HEALTHY);
            }
            System.out.printf("[AP Cluster] Initialized %d nodes (always available!)%n", nodeCount);
        }

        public void partitionNodes(int... partitionedNodeIds) {
            for (int id : partitionedNodeIds) {
                nodeStates.set(id, NodeState.PARTITIONED);
            }
            System.out.printf("[AP Cluster] PARTITION! Nodes %s isolated. But writes still ACCEPTED!%n",
                    java.util.Arrays.toString(partitionedNodeIds));
        }

        public void healPartition() {
            for (int i = 0; i < totalNodes; i++) {
                nodeStates.set(i, NodeState.HEALTHY);
            }
            System.out.println("[AP Cluster] Partition healed. Performing anti-entropy sync...");
            // Merge tất cả nodes: LWW (Last-Write-Wins)
            Map<String, String> merged = new HashMap<>();
            Map<String, Long> mergedVersions = new HashMap<>();
            for (int i = 0; i < totalNodes; i++) {
                for (Map.Entry<String, Long> entry : versions.get(i).entrySet()) {
                    String key = entry.getKey();
                    long ver = entry.getValue();
                    if (!mergedVersions.containsKey(key) || ver > mergedVersions.get(key)) {
                        mergedVersions.put(key, ver);
                        merged.put(key, nodeStores.get(i).get(key));
                    }
                }
            }
            for (int i = 0; i < totalNodes; i++) {
                nodeStores.get(i).putAll(merged);
                versions.get(i).putAll(mergedVersions);
            }
            System.out.println("[AP Cluster] Anti-entropy complete. Nodes may have had conflicting values!");
        }

        /**
         * Write: LUÔN ACCEPT, ghi vào node local (hoặc một node available).
         * Sync với các nodes khác sau (eventual consistency).
         */
        public boolean write(String key, String value, int targetNode) {
            // AP: Luôn accept write, kể cả khi partitioned!
            long version = System.nanoTime();
            nodeStores.get(targetNode).put(key, value);
            versions.get(targetNode).put(key, version);

            String partStatus = nodeStates.get(targetNode) == NodeState.PARTITIONED
                    ? " [PARTITIONED - will sync later]" : "";

            System.out.printf("[AP Cluster] WRITE ACCEPTED: '%s=%s' to Node %d%s | ALWAYS AVAILABLE!%n",
                    key, value, targetNode, partStatus);

            // Sync với healthy nodes ngay lập tức (nếu không bị partition)
            if (nodeStates.get(targetNode) == NodeState.HEALTHY) {
                for (int i = 0; i < totalNodes; i++) {
                    if (i != targetNode && nodeStates.get(i) == NodeState.HEALTHY) {
                        nodeStores.get(i).put(key, value);
                        versions.get(i).put(key, version);
                    }
                }
            }
            return true;
        }

        /** Read: đọc từ local node, có thể stale nếu chưa sync */
        public String read(String key, int fromNode) {
            String value = nodeStores.get(fromNode).get(key);
            boolean isPartitioned = nodeStates.get(fromNode) == NodeState.PARTITIONED;
            System.out.printf("[AP Cluster] READ '%s' from Node %d%s → '%s'%s%n",
                    key, fromNode,
                    isPartitioned ? " [PARTITIONED]" : "",
                    value,
                    isPartitioned ? " (possibly STALE!)" : "");
            return value;
        }

        public void printState() {
            System.out.println("[AP Cluster] State per node:");
            for (int i = 0; i < totalNodes; i++) {
                System.out.printf("  Node %d [%s]: %s%n", i, nodeStates.get(i), nodeStores.get(i));
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║           CAP Theorem Demo - Trade-off Analysis        ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");

        // ====================================================
        System.out.println("\n══════════════ CP CLUSTER (MongoDB-style) ══════════════");
        System.out.println("Chọn Consistency + Partition Tolerance, hy sinh Availability");
        // ====================================================

        CPCluster cpCluster = new CPCluster(3);

        System.out.println("\n[Normal Operation - No Partition]");
        cpCluster.write("balance:alice", "1000");
        cpCluster.write("balance:bob", "500");
        cpCluster.read("balance:alice");

        System.out.println("\n[Simulating Network Partition - Node 2 isolated]");
        cpCluster.partitionNodes(2);

        System.out.println("\n[During Partition: Writes with quorum=2]");
        cpCluster.write("balance:alice", "900"); // Node 0,1 available → quorum met, OK
        cpCluster.write("balance:bob", "600");

        System.out.println("\n[Partition gets worse - Nodes 1,2 both isolated]");
        cpCluster.partitionNodes(1, 2);

        System.out.println("\n[With only Node 0 available, quorum NOT met]");
        cpCluster.write("balance:alice", "800"); // REJECTED! Only 1 of 3 nodes available
        cpCluster.read("balance:alice"); // Still readable from Node 0

        System.out.println("\n[CP Cluster state during partition]");
        cpCluster.printState();

        System.out.println("\n[Healing partition]");
        cpCluster.healPartition();
        cpCluster.printState();

        // ====================================================
        System.out.println("\n══════════════ AP CLUSTER (Cassandra-style) ══════════════");
        System.out.println("Chọn Availability + Partition Tolerance, hy sinh Consistency");
        // ====================================================

        APCluster apCluster = new APCluster(3);

        System.out.println("\n[Normal Operation - No Partition]");
        apCluster.write("inventory:product-A", "100", 0);
        apCluster.write("inventory:product-B", "50", 0);

        System.out.println("\n[Simulating Network Partition - Nodes 1,2 isolated]");
        apCluster.partitionNodes(1, 2);

        System.out.println("\n[AP Cluster: Writes ALWAYS accepted on any partition side]");
        Thread.sleep(1);
        apCluster.write("inventory:product-A", "90", 0);  // Partition 1: sold 10
        Thread.sleep(1);
        apCluster.write("inventory:product-A", "75", 2);  // Partition 2: sold 25 (CONCURRENT!)

        System.out.println("\n[Reading during partition - INCONSISTENT reads!]");
        apCluster.read("inventory:product-A", 0); // Returns 90
        apCluster.read("inventory:product-A", 2); // Returns 75 - STALE! Inconsistent!

        System.out.println("\n[AP Cluster state during partition]");
        apCluster.printState();

        System.out.println("\n[Healing partition - Anti-entropy reconciliation]");
        apCluster.healPartition();
        System.out.println("\nAfter reconciliation (LWW):");
        apCluster.printState();

        // Summary
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║                    Summary                              ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  CP (MongoDB-style):                                    ║");
        System.out.println("║  ✓ Strong consistency - no conflicting data             ║");
        System.out.println("║  ✗ May reject writes during partition (less available)  ║");
        System.out.println("║  Dùng cho: Banking, inventory count, config data       ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  AP (Cassandra-style):                                  ║");
        System.out.println("║  ✓ Always available - accepts writes even in partition  ║");
        System.out.println("║  ✗ May have inconsistent data across nodes             ║");
        System.out.println("║  Dùng cho: Shopping carts, metrics, user sessions      ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }
}
