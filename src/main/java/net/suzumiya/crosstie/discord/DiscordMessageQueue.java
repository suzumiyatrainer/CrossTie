package net.suzumiya.crosstie.discord;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * メインスレッドと非同期ネットワークスレッド間を完全非同期・安全に中継するメッセージキュー。
 */
public final class DiscordMessageQueue {

    /** Minecraft → Discord への送信待ちキュー (容量制限付きでメモリ保護) */
    private static final BlockingQueue<OutboundMessage> OUTBOUND_QUEUE = new LinkedBlockingQueue<>(1000);

    /** Discord → Minecraft への受信済みチャットコンポーネントキュー (メインスレッドの tick で安全にポーリング配信) */
    private static final ConcurrentLinkedQueue<net.minecraft.util.IChatComponent> INBOUND_QUEUE = new ConcurrentLinkedQueue<>();

    private DiscordMessageQueue() {}

    public static boolean enqueueOutbound(OutboundMessage msg) {
        return OUTBOUND_QUEUE.offer(msg);
    }

    public static OutboundMessage takeOutbound() throws InterruptedException {
        return OUTBOUND_QUEUE.take();
    }

    public static void enqueueInbound(String chatMessage) {
        if (chatMessage != null && !chatMessage.isEmpty()) {
            INBOUND_QUEUE.offer(new net.minecraft.util.ChatComponentText(chatMessage));
        }
    }

    public static void enqueueInbound(net.minecraft.util.IChatComponent chatComponent) {
        if (chatComponent != null) {
            INBOUND_QUEUE.offer(chatComponent);
        }
    }

    public static net.minecraft.util.IChatComponent pollInbound() {
        return INBOUND_QUEUE.poll();
    }

    public static void clear() {
        OUTBOUND_QUEUE.clear();
        INBOUND_QUEUE.clear();
    }

    /**
     * 送信メッセージのデータクラス
     */
    public static final class OutboundMessage {
        public final String content;
        public final String username;
        public final String avatarUrl;

        public OutboundMessage(String content) {
            this(content, null, null);
        }

        public OutboundMessage(String content, String username, String avatarUrl) {
            this.content = content;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }
}
