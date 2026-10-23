package net.suzumiya.crosstie.discord.emoji;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.discord.DiscordConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord Bot アプリケーション絵文字 (Application Emojis) を使用した
 * プレイヤー顔絵文字の自動同期・SHA-256完全一致比較マネージャー。
 */
public final class PlayerEmojiManager {

    private static final String CACHE_FILE_PATH = "config/CrossTie/skin_cache.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<UUID, EmojiData> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrossTie-Emoji-Worker");
        t.setDaemon(true);
        return t;
    });

    private static String applicationId = "";

    private PlayerEmojiManager() {
    }

    public static void init(String botToken) {
        loadCache();
        // Bot アカウント情報から Application ID を取得 (非同期で初期化)
        if (botToken != null && !botToken.isEmpty()) {
            WORKER.submit(() -> fetchApplicationId(botToken));
        }
    }

    public static void shutdown() {
        saveCache();
        WORKER.shutdownNow();
    }

    /**
     * プレイヤーの顔絵文字タグを取得する (<:mc_PlayerName:emoji_id>)。 キャッシュが存在しない場合は空文字を返す
     * (0ms、メインスレッド非ブロック)。
     */
    public static String getEmojiTag(UUID uuid, String playerName) {
        if (!DiscordConfig.enablePlayerFaceEmojis) {
            return "";
        }
        EmojiData data = CACHE.get(uuid);
        if (data != null && data.emojiId != null && !data.emojiId.isEmpty()) {
            String sanitizedName = sanitizeEmojiName(playerName);
            return "<:" + sanitizedName + ":" + data.emojiId + ">";
        }
        return "";
    }

    /**
     * プレイヤー参加時に非同期でスキンの取得・SHA-256判定・絵文字同期を実行する。
     */
    public static void onPlayerJoin(UUID uuid, String playerName) {
        if (!DiscordConfig.enablePlayerFaceEmojis) {
            return;
        }
        WORKER.submit(() -> checkAndUpdateSkin(uuid, playerName));
    }

    private static void checkAndUpdateSkin(UUID uuid, String playerName) {
        String token = DiscordConfig.resolvedBotToken;
        if (token == null || token.isEmpty()) {
            return;
        }
        if (applicationId.isEmpty()) {
            fetchApplicationId(token);
        }

        try {
            // 1. 設定されたテンプレート URL (デフォルト: Crafatar 128x128 + overlay) から顔画像を取得
            String urlStr = DiscordConfig.getAvatarUrl(uuid, playerName, 128);
            byte[] pngBytes = fetchBytes(urlStr);
            if (pngBytes == null || pngBytes.length == 0) {
                // フォールバック: mc-heads
                String fallbackUrl = "https://mc-heads.net/avatar/" + uuid.toString().replace("-", "") + "/128.png";
                pngBytes = fetchBytes(fallbackUrl);
                if (pngBytes == null || pngBytes.length == 0) {
                    CrossTie.LOGGER.warn("[Discord] Failed to download skin head image for {} from: {}", playerName, urlStr);
                    return;
                }
            }

            // 2. SHA-256 を計算
            String sha256 = calculateSha256(pngBytes);

            // 3. 既存キャッシュと比較
            EmojiData existing = CACHE.get(uuid);
            if (existing != null && sha256.equals(existing.sha256) && existing.emojiId != null
                    && !existing.emojiId.isEmpty()) {
                // 1ピクセルも変化なし (通信スキップ)
                return;
            }

            // 4. スキン変更または新規参加: 古い絵文字を削除
            String sanitizedName = sanitizeEmojiName(playerName);
            if (existing != null && existing.emojiId != null && !existing.emojiId.isEmpty()) {
                if (existing.targetGuildId != null && !existing.targetGuildId.isEmpty()) {
                    deleteGuildEmoji(existing.targetGuildId, existing.emojiId, token);
                } else if (!applicationId.isEmpty()) {
                    deleteApplicationEmoji(applicationId, existing.emojiId, token);
                }
            }

            // 5. 新規絵文字を作成 (preferApplicationEmojis が有効な場合は Application Emoji を最優先)
            String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
            String newEmojiId = null;
            String usedGuildId = null;

            if (DiscordConfig.preferApplicationEmojis && !applicationId.isEmpty()) {
                newEmojiId = createApplicationEmoji(applicationId, sanitizedName, base64Image, token);
            }

            // アプリ絵文字が未作成、またはギルド優先設定の場合は Guild Emoji を試行
            if ((newEmojiId == null || newEmojiId.isEmpty()) && DiscordConfig.guildId != null && !DiscordConfig.guildId.isEmpty()) {
                newEmojiId = createGuildEmoji(DiscordConfig.guildId, sanitizedName, base64Image, token);
                if (newEmojiId != null && !newEmojiId.isEmpty()) {
                    usedGuildId = DiscordConfig.guildId;
                }
            }

            // ギルド優先時にギルド側が失敗した場合のフォールバックでアプリ絵文字を試行
            if ((newEmojiId == null || newEmojiId.isEmpty()) && !applicationId.isEmpty()) {
                newEmojiId = createApplicationEmoji(applicationId, sanitizedName, base64Image, token);
            }

            if (newEmojiId != null && !newEmojiId.isEmpty()) {
                EmojiData newData = new EmojiData(playerName, newEmojiId, sha256, System.currentTimeMillis(), usedGuildId);
                CACHE.put(uuid, newData);
                saveCache();
                CrossTie.LOGGER.info("[Discord] Synchronized face emoji for {}: <:{}::{}> (Type: {})", playerName, sanitizedName,
                        newEmojiId, (usedGuildId != null ? "Guild" : "Application"));
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to synchronize emoji for {}: {}", playerName, t.getMessage());
        }
    }

    private static String calculateSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static String sanitizeEmojiName(String name) {
        if (name == null || name.isEmpty()) {
            return "mc_player";
        }
        // Discord 絵文字名は英数字とアンダースコアのみ、2〜32文字
        String sanitized = "mc_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return sanitized;
    }

    private static void fetchApplicationId(String botToken) {
        try {
            // 1. 公式 Application オブジェクト取得エンドポイント
            URL url = new URL("https://discord.com/api/v10/oauth2/applications/@me");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    Map<?, ?> map = GSON.fromJson(reader, Map.class);
                    if (map != null && map.containsKey("id")) {
                        applicationId = String.valueOf(map.get("id"));
                        CrossTie.LOGGER.info("[Discord] Application ID resolved: {}", applicationId);
                        return;
                    }
                }
            } else {
                String err = readErrorStream(conn);
                CrossTie.LOGGER.warn("[Discord] oauth2/applications/@me returned HTTP {}: {}. Trying users/@me...", conn.getResponseCode(), err);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to fetch application ID via oauth2: {}", t.getMessage());
        }

        // 2. フォールバック: users/@me
        try {
            URL url = new URL("https://discord.com/api/v10/users/@me");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    Map<?, ?> map = GSON.fromJson(reader, Map.class);
                    if (map != null && map.containsKey("id")) {
                        applicationId = String.valueOf(map.get("id"));
                        CrossTie.LOGGER.info("[Discord] Application ID resolved (fallback): {}", applicationId);
                    }
                }
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to fetch user ID fallback: {}", t.getMessage());
        }
    }

    private static String createGuildEmoji(String guildId, String name, String base64Image, String botToken) {
        try {
            URL url = new URL("https://discord.com/api/v10/guilds/" + guildId + "/emojis");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("image", base64Image);
            payload.put("roles", Collections.emptyList());

            String jsonPayload = GSON.toJson(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200 || code == 201) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    Map<?, ?> res = GSON.fromJson(reader, Map.class);
                    if (res != null && res.containsKey("id")) {
                        return String.valueOf(res.get("id"));
                    }
                }
            } else {
                String err = readErrorStream(conn);
                CrossTie.LOGGER.warn("[Discord] Failed to create Guild emoji (HTTP {}): {}", code, err);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Error creating Guild emoji: {}", t.getMessage());
        }
        return null;
    }

    private static void deleteGuildEmoji(String guildId, String emojiId, String botToken) {
        try {
            URL url = new URL("https://discord.com/api/v10/guilds/" + guildId + "/emojis/" + emojiId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getResponseCode();
        } catch (Throwable ignored) {
        }
    }

    private static String createApplicationEmoji(String appId, String name, String base64Image, String botToken) {
        try {
            URL url = new URL("https://discord.com/api/v10/applications/" + appId + "/emojis");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("image", base64Image);

            String jsonPayload = GSON.toJson(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200 || code == 201) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    Map<?, ?> res = GSON.fromJson(reader, Map.class);
                    if (res != null && res.containsKey("id")) {
                        return String.valueOf(res.get("id"));
                    }
                }
            } else {
                String err = readErrorStream(conn);
                CrossTie.LOGGER.warn("[Discord] Failed to create Application emoji (HTTP {}): {}", code, err);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Error creating Application emoji: {}", t.getMessage());
        }
        return null;
    }

    private static void deleteApplicationEmoji(String appId, String emojiId, String botToken) {
        try {
            URL url = new URL("https://discord.com/api/v10/applications/" + appId + "/emojis/" + emojiId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getResponseCode();
        } catch (Throwable ignored) {
        }
    }

    private static String readErrorStream(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null)
                return "";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = es.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static byte[] fetchBytes(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CrossTie-Minecraft-1.7.10");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                }
                return baos.toByteArray();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void loadCache() {
        File file = new File(CACHE_FILE_PATH);
        if (!file.exists())
            return;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, EmojiData>>() {
            }.getType();
            Map<String, EmojiData> map = GSON.fromJson(r, type);
            if (map != null) {
                for (Map.Entry<String, EmojiData> e : map.entrySet()) {
                    try {
                        CACHE.put(UUID.fromString(e.getKey()), e.getValue());
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void saveCache() {
        File file = new File(CACHE_FILE_PATH);
        if (file.getParentFile() != null)
            file.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(CACHE, w);
        } catch (Throwable ignored) {
        }
    }

    public static final class EmojiData {
        public String name;
        public String emojiId;
        public String sha256;
        public long lastUpdated;
        public String targetGuildId;

        public EmojiData(String name, String emojiId, String sha256, long lastUpdated, String targetGuildId) {
            this.name = name;
            this.emojiId = emojiId;
            this.sha256 = sha256;
            this.lastUpdated = lastUpdated;
            this.targetGuildId = targetGuildId;
        }

        public EmojiData(String name, String emojiId, String sha256, long lastUpdated) {
            this(name, emojiId, sha256, lastUpdated, null);
        }
    }

    private static final class EmojiCreateRequest {
        @SuppressWarnings("unused")
        public final String name;
        @SuppressWarnings("unused")
        public final String image;

        public EmojiCreateRequest(String name, String image) {
            this.name = name;
            this.image = image;
        }
    }
}
