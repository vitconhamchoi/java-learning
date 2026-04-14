package com.distributed.fundamentals;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lamport Logical Clock implementation.
 *
 * Quy tắc Lamport Clock:
 * 1. Mỗi process duy trì một counter (timestamp)
 * 2. Trước mỗi event (kể cả send): tăng counter lên 1
 * 3. Mỗi message mang timestamp của sender
 * 4. Khi nhận message: time = max(local, msg_time) + 1
 *
 * Đảm bảo: A → B ⟹ LC(A) < LC(B)
 * Nhưng KHÔNG đảm bảo ngược lại!
 */
public class LamportClock {

    private final AtomicLong time;
    private final String processId;

    public LamportClock(String processId) {
        this.processId = processId;
        this.time = new AtomicLong(0);
    }

    /** Tăng clock khi có local event */
    public long tick() {
        return time.incrementAndGet();
    }

    /**
     * Gửi message: tăng clock trước khi send, trả về timestamp để đính kèm message.
     * Caller phải attach timestamp này vào message.
     */
    public long send() {
        long ts = time.incrementAndGet();
        System.out.printf("[%s] SEND event at T=%d%n", processId, ts);
        return ts;
    }

    /**
     * Nhận message với timestamp từ sender.
     * Cập nhật: time = max(local, msgTime) + 1
     */
    public long receive(long msgTime) {
        long updated = time.updateAndGet(current -> Math.max(current, msgTime) + 1);
        System.out.printf("[%s] RECV message (msgTime=%d) → local time now T=%d%n",
                processId, msgTime, updated);
        return updated;
    }

    /** Trả về thời gian hiện tại (không tăng) */
    public long getTime() {
        return time.get();
    }

    public String getProcessId() {
        return processId;
    }

    @Override
    public String toString() {
        return String.format("LamportClock{process='%s', time=%d}", processId, time.get());
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Lamport Clock Demo ===");
        System.out.println("Mô phỏng 3 processes giao tiếp theo happens-before ordering\n");

        // Khởi tạo 3 processes
        LamportClock p1 = new LamportClock("P1");
        LamportClock p2 = new LamportClock("P2");
        LamportClock p3 = new LamportClock("P3");

        System.out.println("--- Giai đoạn 1: Local events ---");
        long p1_e1 = p1.tick();
        System.out.printf("[P1] Local event e1 at T=%d%n", p1_e1);

        long p2_e1 = p2.tick();
        System.out.printf("[P2] Local event e1 at T=%d%n", p2_e1);

        System.out.println();
        System.out.println("--- Giai đoạn 2: P1 gửi message cho P2 ---");

        // P1 gửi message cho P2
        long p1SendTs = p1.send();
        System.out.printf("[P1] Gửi message với timestamp=%d cho P2%n", p1SendTs);

        // P2 nhận message từ P1
        p2.receive(p1SendTs);

        System.out.println();
        System.out.println("--- Giai đoạn 3: P2 gửi message cho P3 ---");

        // P2 gửi message cho P3 (phụ thuộc vào message từ P1)
        long p2SendTs = p2.send();
        System.out.printf("[P2] Gửi message với timestamp=%d cho P3%n", p2SendTs);

        // P3 nhận message từ P2
        p3.receive(p2SendTs);

        System.out.println();
        System.out.println("--- Giai đoạn 4: P1 gửi message cho P3 (đường trực tiếp) ---");

        // P1 cũng gửi thẳng cho P3
        long p1SendTs2 = p1.send();
        System.out.printf("[P1] Gửi message với timestamp=%d trực tiếp cho P3%n", p1SendTs2);

        // P3 nhận message từ P1 (có thể đến sau message P2→P3)
        p3.receive(p1SendTs2);

        System.out.println();
        System.out.println("--- Trạng thái cuối cùng ---");
        System.out.println(p1);
        System.out.println(p2);
        System.out.println(p3);

        System.out.println();
        System.out.println("--- Phân tích Happens-Before ---");
        System.out.println("P1.e1 → P2.receive(P1's msg): " + (p1_e1 < p2.getTime()));
        System.out.println("P2.receive(P1's msg) → P3.receive(P2's msg): happens-before đúng về mặt causal");
        System.out.println();
        System.out.println("Lưu ý: Lamport Clock đảm bảo A→B ⟹ LC(A)<LC(B)");
        System.out.println("Nhưng KHÔNG đảm bảo: LC(A)<LC(B) ⟹ A→B");
        System.out.println("→ Dùng Vector Clock để detect concurrent events!");
    }
}
