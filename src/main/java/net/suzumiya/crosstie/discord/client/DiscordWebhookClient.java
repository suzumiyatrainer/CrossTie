package net.suzumiya.crosstie.discord.client;

import com.google.gson.Gson;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.discord.DiscordConfig;
import net.suzumiya.crosstie.discord.DiscordMessageQueue;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 非同期ワーカースレッド上で動作し、Discord Webhook または Bot REST API へメッセージを送信するクライアント。
 */
public final class DiscordWebhookClient implements Runnable {

    private static final Gson GSON = new Gson();
    private volatile boolean running = true;

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        CrossTie.LOGGER.info("[Discord] Outbound message dispatcher started.");
        while (running) {
            try {
                DiscordMessageQueue.OutboundMessage msg = DiscordMessageQueue.takeOutbound();
                if (msg == null) continue;
                send(msg);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                CrossTie.LOGGER.warn("[Discord] Error dispatching message: {}", t.getMessage());
            }
        }
        CrossTie.LOGGER.info("[Discord] Outbound message dispatcher stopped.");
    }

    private void send(DiscordMessageQueue.OutboundMessage msg) {
        String webhookUrl = DiscordConfig.resolvedWebhookUrl;
        if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
            sendWebhook(webhookUrl.trim(), msg);
        } else {
            // Webhook が未設定の場合は Bot REST API にフォールバック
            String botToken = DiscordConfig.resolvedBotToken;
            String channelId = DiscordConfig.channelId;
            if (botToken != null && !botToken.isEmpty() && channelId != null && !channelId.isEmpty()) {
                sendBotApi(botToken, channelId, msg.content);
            }
        }
    }

    private void sendWebhook(String webhookUrl, DiscordMessageQueue.OutboundMessage msg) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("content", msg.content);
            if (msg.username != null && !msg.username.isEmpty()) {
                payload.put("username", msg.username);
            }
            if (msg.avatarUrl != null && !msg.avatarUrl.isEmpty()) {
                payload.put("avatar_url", msg.avatarUrl);
            }

            byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 204) {
                CrossTie.LOGGER.warn("[Discord] Webhook returned HTTP {}", code);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to send webhook: {}", t.getMessage());
        }
    }

    private void sendBotApi(String botToken, String channelId, String content) {
        try {
            URL url = new URL("https://discord.com/api/v10/channels/" + channelId + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("content", content);

            byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 201) {
                CrossTie.LOGGER.warn("[Discord] Bot API returned HTTP {}", code);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to send via Bot API: {}", t.getMessage());
        }
    }
}
