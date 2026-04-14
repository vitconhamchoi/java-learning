package com.distributed.fundamentals;

import java.util.Arrays;

/**
 * Vector Clock implementation.
 *
 * Vector Clock khắc phục hạn chế của Lamport Clock:
 * - Phát hiện được concurrent events (events không có quan hệ causal)
 * - Mỗi process duy trì một vector [t1, t2, ..., tn] với n = số processes
 *
 * Quy tắc:
 * 1. Khi local event: tăng clock[processId]++
 * 2. Khi send: gửi kèm copy của vector hiện tại
 * 3. Khi receive(msgClock): merge = max(local[i], msgClock[i]) cho mọi i,
 *    sau đó tăng clock[processId]++
 *
 * Happens-before A → B:
 *   ∀i: VC(A)[i] ≤ VC(B)[i]  AND  ∃j: VC(A)[j] < VC(B)[j]
 *
 * Concurrent A ∥ B:
 *   !(A → B)  AND  !(B → A)
 */
public class VectorClock {

    private final int[] clock;
    private final int processId;
    private final int numProcesses;
    private final String processName;

    public VectorClock(int processId, int numProcesses, String processName) {
        this.processId = processId;
        this.numProcesses = numProcesses;
        this.processName = processName;
        this.clock = new int[numProcesses];
    }

    /** Tăng slot của process này khi có local event */
    public int[] tick() {
        clock[processId]++;
        System.out.printf("[%s] Local tick → %s%n", processName, Arrays.toString(clock));
        return Arrays.copyOf(clock, numProcesses);
    }

    /**
     * Chuẩn bị gửi message: trả về bản copy của vector clock hiện tại.
     * Caller đính kèm vector này vào message.
     * (Không tăng clock khi send trong mô hình này - tick đã được gọi trước khi send)
     */
    public int[] send() {
        clock[processId]++;
        int[] snapshot = Arrays.copyOf(clock, numProcesses);
        System.out.printf("[%s] SEND → clock=%s%n", processName, Arrays.toString(snapshot));
        return snapshot;
    }

    /**
     * Nhận message với vector clock từ sender.
     * merge: clock[i] = max(clock[i], msgClock[i]) cho mọi i
     * sau đó: clock[processId]++
     */
    public int[] receive(int processIdSender, int[] msgClock) {
        for (int i = 0; i < numProcesses; i++) {
            clock[i] = Math.max(clock[i], msgClock[i]);
        }
        clock[processId]++;
        System.out.printf("[%s] RECV from P%d (msgClock=%s) → %s%n",
                processName, processIdSender, Arrays.toString(msgClock), Arrays.toString(clock));
        return Arrays.copyOf(clock, numProcesses);
    }

    /** Lấy snapshot của vector clock hiện tại */
    public int[] getSnapshot() {
        return Arrays.copyOf(clock, numProcesses);
    }

    /**
     * Kiểm tra a happens-before b (a → b).
     * Điều kiện: ∀i: a[i] ≤ b[i]  AND  ∃j: a[j] < b[j]
     */
    public static boolean happensBefore(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector clocks must have same size");
        }
        boolean strictlyLess = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                return false; // a[i] > b[i] → a không thể happen-before b
            }
            if (a[i] < b[i]) {
                strictlyLess = true;
            }
        }
        return strictlyLess;
    }

    /**
     * Kiểm tra a và b là concurrent (không có quan hệ causal).
     * a ∥ b ⟺ !(a → b) AND !(b → a)
     */
    public static boolean concurrent(int[] a, int[] b) {
        return !happensBefore(a, b) && !happensBefore(b, a) && !Arrays.equals(a, b);
    }

    @Override
    public String toString() {
        return String.format("VectorClock{process='%s', clock=%s}", processName, Arrays.toString(clock));
    }

    public static void main(String[] args) {
        System.out.println("=== Vector Clock Demo ===");
        System.out.println("Mô phỏng 3 processes, phát hiện causality và concurrency\n");

        // 3 processes: P0, P1, P2
        VectorClock p0 = new VectorClock(0, 3, "P0");
        VectorClock p1 = new VectorClock(1, 3, "P1");
        VectorClock p2 = new VectorClock(2, 3, "P2");

        System.out.println("=== Scenario: Causally related events ===");

        // P0 thực hiện local event
        int[] p0_e1 = p0.tick(); // P0: [1,0,0]

        // P1 thực hiện local event
        int[] p1_e1 = p1.tick(); // P1: [0,1,0]

        // P0 gửi message cho P1 (causal dependency)
        System.out.println();
        int[] p0_send1 = p0.send(); // P0: [2,0,0]

        // P1 nhận message từ P0 (bây giờ P1 biết về P0's events)
        int[] p1_after_recv = p1.receive(0, p0_send1); // P1: [2,2,0]

        // P1 gửi message cho P2 (mang theo causality từ P0)
        System.out.println();
        int[] p1_send1 = p1.send(); // P1: [2,3,0]

        // P2 nhận message từ P1 (biết về cả P0 và P1's history)
        int[] p2_after_recv = p2.receive(1, p1_send1); // P2: [2,3,1]

        System.out.println();
        System.out.println("=== Phân tích Causality ===");
        System.out.printf("p0_e1 = %s%n", Arrays.toString(p0_e1));
        System.out.printf("p1_after_recv = %s%n", Arrays.toString(p1_after_recv));
        System.out.printf("p2_after_recv = %s%n", Arrays.toString(p2_after_recv));

        System.out.printf("%np0_e1 → p1_after_recv: %s (expected: true)%n",
                happensBefore(p0_e1, p1_after_recv));
        System.out.printf("p1_after_recv → p2_after_recv: %s (expected: true)%n",
                happensBefore(p1_after_recv, p2_after_recv));
        System.out.printf("p0_e1 → p2_after_recv: %s (expected: true, transitive)%n",
                happensBefore(p0_e1, p2_after_recv));

        System.out.println();
        System.out.println("=== Scenario: Concurrent events ===");

        // Reset và demo concurrent events
        VectorClock q0 = new VectorClock(0, 3, "Q0");
        VectorClock q1 = new VectorClock(1, 3, "Q1");
        VectorClock q2 = new VectorClock(2, 3, "Q2");

        // Q0 và Q1 thực hiện events độc lập (concurrent)
        int[] q0_event = q0.tick(); // Q0: [1,0,0]
        int[] q1_event = q1.tick(); // Q1: [0,1,0]

        System.out.printf("%nq0_event = %s%n", Arrays.toString(q0_event));
        System.out.printf("q1_event = %s%n", Arrays.toString(q1_event));
        System.out.printf("q0_event → q1_event: %s%n", happensBefore(q0_event, q1_event));
        System.out.printf("q1_event → q0_event: %s%n", happensBefore(q1_event, q0_event));
        System.out.printf("q0_event ∥ q1_event (concurrent): %s (expected: true)%n",
                concurrent(q0_event, q1_event));

        System.out.println();
        System.out.println("=== Kết luận ===");
        System.out.println("Vector Clock phát hiện được concurrent events!");
        System.out.println("→ Lamport Clock không phân biệt được causal vs concurrent");
        System.out.println("→ Vector Clock cần O(n) space nhưng đầy đủ thông tin hơn");
        System.out.println("→ Ứng dụng: Conflict detection trong CRDT, distributed databases");
    }
}
