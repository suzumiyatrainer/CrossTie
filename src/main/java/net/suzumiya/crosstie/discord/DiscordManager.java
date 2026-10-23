package net.suzumiya.crosstie.discord;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.discord.client.DiscordGatewayClient;
import net.suzumiya.crosstie.discord.client.DiscordWebhookClient;
import net.suzumiya.crosstie.discord.emoji.PlayerEmojiManager;
import net.suzumiya.crosstie.discord.listener.DiscordEventListener;

/**
 * CrossTie Discord 連携の統合ライフサイクルマネージャー。
 * Dedicated Server かつ config で明示的に有効化された場合のみ起動する完全オプトイン設計。
 */
public final class DiscordManager {

    private static boolean active = false;
    private static DiscordWebhookClient webhookClient;
    private static Thread outboundThread;
    private static DiscordGatewayClient gatewayClient;
    private static DiscordEventListener eventListener;

    private DiscordManager() {}

    public static boolean isActive() {
        return active;
    }

    public static void onServerStarting(FMLServerStartingEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        // 統合サーバー (シングルプレイ) では一切動作させない
        if (server == null || !server.isDedicatedServer()) {
            return;
        }

        if (!DiscordConfig.enabled) {
            CrossTie.LOGGER.info("[Discord] Discord integration is disabled in config.");
            return;
        }

        String token = DiscordConfig.resolvedBotToken;
        String channelId = DiscordConfig.channelId;

        if ((token == null || token.isEmpty()) && (DiscordConfig.resolvedWebhookUrl == null || DiscordConfig.resolvedWebhookUrl.isEmpty())) {
            CrossTie.LOGGER.warn("[Discord] Integration is enabled, but neither botToken nor webhookUrl is configured. Disabling.");
            return;
        }

        CrossTie.LOGGER.info("[Discord] Starting Discord integration (Token: {})...", DiscordSecretManager.maskToken(token));

        try {
            // 1. 絵文字マネージャー初期化
            PlayerEmojiManager.init(token);

            // 2. 送信ワーカースレッド起動
            webhookClient = new DiscordWebhookClient();
            outboundThread = new Thread(webhookClient, "CrossTie-Discord-Outbound");
            outboundThread.setDaemon(true);
            outboundThread.start();

            // 3. 受信 WebSocket Gateway 起動 (Bot Token がある場合)
            if (token != null && !token.isEmpty() && channelId != null && !channelId.isEmpty()) {
                gatewayClient = new DiscordGatewayClient();
                gatewayClient.start();
            }

            // 4. イベントリスナー登録
            eventListener = new DiscordEventListener();
            MinecraftForge.EVENT_BUS.register(eventListener);
            FMLCommonHandler.instance().bus().register(eventListener);

            active = true;

            // 5. サーバー起動通知
            if (DiscordConfig.sendServerLifecycle) {
                DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(DiscordConfig.formatServerStart));
            }

            CrossTie.LOGGER.info("[Discord] Integration successfully activated!");
        } catch (Throwable t) {
            CrossTie.LOGGER.error("[Discord] Failed to start Discord integration: {}", t.getMessage(), t);
        }
    }

    public static void onServerStopping(FMLServerStoppingEvent event) {
        if (!active) return;

        CrossTie.LOGGER.info("[Discord] Stopping Discord integration...");

        try {
            // サーバー停止通知
            if (DiscordConfig.sendServerLifecycle) {
                DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(DiscordConfig.formatServerStop));
                // 送信完了を少し待機
                Thread.sleep(800);
            }
        } catch (Throwable ignored) {}

        try {
            if (eventListener != null) {
                MinecraftForge.EVENT_BUS.unregister(eventListener);
                FMLCommonHandler.instance().bus().unregister(eventListener);
                eventListener = null;
            }
            if (gatewayClient != null) {
                gatewayClient.stop();
                gatewayClient = null;
            }
            if (webhookClient != null) {
                webhookClient.stop();
                webhookClient = null;
            }
            if (outboundThread != null) {
                outboundThread.interrupt();
                outboundThread = null;
            }
            PlayerEmojiManager.shutdown();
            DiscordMessageQueue.clear();
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Error during shutdown: {}", t.getMessage());
        } finally {
            active = false;
        }
    }
}
