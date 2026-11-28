package net.suzumiya.crosstie.utils;

/**
 * {@code PacketDataMap.apply} の実行中であることをスレッドローカルに伝えるコンテキスト。
 * {@code DataMapMixin} がネットワーク受信によるローカル反映と、通常の {@code flag=0} ローカル書き込みを
 * 区別するために使用する。
 */
public final class DataMapSyncContext {

    private static final ThreadLocal<Integer> PACKET_APPLY_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private DataMapSyncContext() {
    }

    public static void enterPacketApply() {
        PACKET_APPLY_DEPTH.set(PACKET_APPLY_DEPTH.get() + 1);
    }

    public static void exitPacketApply() {
        int next = PACKET_APPLY_DEPTH.get() - 1;
        if (next <= 0) {
            PACKET_APPLY_DEPTH.remove();
        } else {
            PACKET_APPLY_DEPTH.set(next);
        }
    }

    public static boolean isApplyingPacket() {
        return PACKET_APPLY_DEPTH.get() > 0;
    }
}
