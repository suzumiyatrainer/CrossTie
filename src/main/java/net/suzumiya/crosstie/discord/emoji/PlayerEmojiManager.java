package net.suzumiya.crosstie.discord.emoji;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import javax.annotation.Nonnull;

/**
 * Discord Bot アプリケーション絵文字 (Application Emojis) を使用した
 * プレイヤー顔絵文字の自動同期・SHA-256完全一致比較マネージャー。
 */
public final class PlayerEmojiManager {

    private static final String CACHE_FILE_PATH = "config/CrossTie/skin_cache.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @SuppressWarnings("deprecation")
    private static final JsonParser PARSER = new JsonParser();

    private static final Map<UUID, EmojiData> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrossTie-Emoji-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final Map<UUID, Long> LAST_CHECK_TIMES = new ConcurrentHashMap<>();
    private static final Map<UUID, java.util.concurrent.Future<?>> PENDING_FUTURES = new ConcurrentHashMap<>();
    private static final long CHECK_COOLDOWN_MS = 60_000L;

    private static String applicationId = "";

    private PlayerEmojiManager() {
    }

    public static void init(String botToken) {
        loadCache();
        // Bot アカウント情報から Application ID を取得し、起動時に全キャッシュプレイヤーのスキン変更をチェック
        if (botToken != null && !botToken.isEmpty()) {
            WORKER.submit(() -> {
                fetchApplicationId(botToken);
                checkAllCachedSkinsOnStartup();
            });
        }
    }

    /**
     * サーバー起動時にキャッシュされている全プレイヤーのスキン変更・絵文字同期を非同期で実行する。
     */
    private static void checkAllCachedSkinsOnStartup() {
        if (!DiscordConfig.enablePlayerFaceEmojis || CACHE.isEmpty()) {
            return;
        }
        CrossTie.LOGGER.info("[Discord] Checking skin updates for {} cached player(s) on startup...", CACHE.size());
        for (Map.Entry<UUID, EmojiData> entry : CACHE.entrySet()) {
            UUID uuid = entry.getKey();
            EmojiData data = entry.getValue();
            if (uuid != null && data != null && data.name != null) {
                checkAndUpdateSkin(uuid, data.name, true);
            }
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
     * プレイヤーの顔絵文字タグを取得する。 キャッシュに存在する場合は即座に返し (0ms)、キャッシュに存在せず更新タスクが進行中の場合は
     * 指定ミリ秒まで完了を待機して最新タグを返す。タイムアウト時は空文字を返す。
     */
    public static String getOrAwaitEmojiTag(UUID uuid, String playerName, long timeoutMs) {
        if (!DiscordConfig.enablePlayerFaceEmojis || uuid == null) {
            return "";
        }
        String tag = getEmojiTag(uuid, playerName);
        if (!tag.isEmpty()) {
            return tag;
        }

        java.util.concurrent.Future<?> future = PENDING_FUTURES.get(uuid);
        if (future != null) {
            try {
                future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
        return getEmojiTag(uuid, playerName);
    }

    /**
     * 非同期ワーカースレッド上で処理を実行する。
     */
    public static void runAsync(Runnable runnable) {
        if (runnable != null) {
            WORKER.submit(runnable);
        }
    }

    /**
     * プレイヤー参加試行時 (allowUserToConnect) に非同期でスキンの取得・SHA-256判定・絵文字同期を先行実行する。
     */
    public static void onPlayerJoinAttempt(UUID uuid, String playerName) {
        if (!DiscordConfig.enablePlayerFaceEmojis) {
            return;
        }
        if (uuid == null) {
            return;
        }
        java.util.concurrent.Future<?> future = WORKER.submit(() -> checkAndUpdateSkin(uuid, playerName, false));
        PENDING_FUTURES.put(uuid, future);
    }

    /**
     * プレイヤー参加時に非同期でスキンの取得・SHA-256判定・絵文字同期を実行する。
     */
    public static void onPlayerJoin(UUID uuid, String playerName) {
        onPlayerJoinAttempt(uuid, playerName);
    }

    @SuppressWarnings("null")
    private static void checkAndUpdateSkin(UUID uuid, String playerName, boolean force) {
        String token = DiscordConfig.resolvedBotToken;
        if (token == null || token.isEmpty()) {
            return;
        }

        if (!force) {
            Long lastCheck = LAST_CHECK_TIMES.get(uuid);
            long now = System.currentTimeMillis();
            if (lastCheck != null && (now - lastCheck) < CHECK_COOLDOWN_MS) {
                return;
            }
        }
        LAST_CHECK_TIMES.put(uuid, System.currentTimeMillis());

        if (applicationId.isEmpty()) {
            fetchApplicationId(token);
        }

        try {
            // 1. 設定されたテンプレート URL (デフォルト: Crafatar 128x128 + overlay) から顔画像を取得
            String urlStr = DiscordConfig.getAvatarUrl(uuid, playerName, 128);
            byte[] pngBytes = fetchBytes(urlStr);
            if (pngBytes == null || pngBytes.length == 0) {
                // フォールバック 1: mc-heads (UUID)
                String fallbackUrl = "https://mc-heads.net/avatar/" + uuid.toString().replace("-", "") + "/128.png";
                pngBytes = fetchBytes(fallbackUrl);
                // フォールバック 2: mc-heads (プレイヤー名指定、オフラインモードや認証不調時対策)
                if ((pngBytes == null || pngBytes.length == 0) && playerName != null && !playerName.isEmpty()) {
                    pngBytes = fetchBytes("https://mc-heads.net/avatar/" + playerName + "/128.png");
                }
                if (pngBytes == null || pngBytes.length == 0) {
                    CrossTie.LOGGER.warn("[Discord] Failed to download skin head image for {} from: {}", playerName,
                            urlStr);
                    return;
                }
            }

            // 2. SHA-256 を計算
            String sha256 = calculateSha256(pngBytes);
            // 名前は MCID と完全一致、プレフィックスなし
            String sanitizedName = sanitizeEmojiName(playerName);

            // 3. 既存キャッシュと比較
            @Nonnull
            EmojiData existing = CACHE.get(uuid);
            boolean sameSkin = existing != null && sha256.equals(existing.sha256) && existing.emojiId != null
                    && !existing.emojiId.isEmpty();

            // 4. Discord 上の既存絵文字を調査 (アプリ絵文字優先)
            // キャッシュ消去や手動作成等で NAME_ALREADY_TAKEN になるのを防ぐため、同名および旧 mc_ プレフィックス絵文字を検索
            Map<String, String> appEmojis = !applicationId.isEmpty() ? fetchApplicationEmojis(applicationId, token)
                    : Collections.emptyMap();
            String existingAppEmojiId = appEmojis.get(sanitizedName);
            String legacyAppEmojiId = appEmojis.get("mc_" + sanitizedName);

            Map<String, String> guildEmojis = (DiscordConfig.guildId != null && !DiscordConfig.guildId.isEmpty())
                    ? fetchGuildEmojis(DiscordConfig.guildId, token)
                    : Collections.emptyMap();
            String existingGuildEmojiId = guildEmojis.get(sanitizedName);
            String legacyGuildEmojiId = guildEmojis.get("mc_" + sanitizedName);

            // スキンに変更がなく、かつ既にアプリ絵文字として同名絵文字が存在している場合
            if (sameSkin && existingAppEmojiId != null && existingAppEmojiId.equals(existing.emojiId)) {
                // 余分な旧プレフィックス絵文字やギルド絵文字があれば掃除して完了
                if (legacyAppEmojiId != null) {
                    deleteApplicationEmoji(applicationId, legacyAppEmojiId, token);
                }
                if (existingGuildEmojiId != null) {
                    deleteGuildEmoji(DiscordConfig.guildId, existingGuildEmojiId, token);
                }
                if (legacyGuildEmojiId != null) {
                    deleteGuildEmoji(DiscordConfig.guildId, legacyGuildEmojiId, token);
                }
                return;
            }

            // スキン更新または新規作成、あるいはアプリ絵文字への再昇格が必要な場合:
            // Discord 上の旧絵文字を確実に削除 (400 APPLICATION_EMOJI_NAME_ALREADY_TAKEN 回避)
            if (existingAppEmojiId != null) {
                deleteApplicationEmoji(applicationId, existingAppEmojiId, token);
            }
            if (legacyAppEmojiId != null) {
                deleteApplicationEmoji(applicationId, legacyAppEmojiId, token);
            }
            if (existing != null && existing.emojiId != null && !existing.emojiId.isEmpty()) {
                if (existing.targetGuildId != null && !existing.targetGuildId.isEmpty()) {
                    deleteGuildEmoji(existing.targetGuildId, existing.emojiId, token);
                } else if (!applicationId.isEmpty()) {
                    deleteApplicationEmoji(applicationId, existing.emojiId, token);
                }
            }
            if (existingGuildEmojiId != null) {
                deleteGuildEmoji(DiscordConfig.guildId, existingGuildEmojiId, token);
            }
            if (legacyGuildEmojiId != null) {
                deleteGuildEmoji(DiscordConfig.guildId, legacyGuildEmojiId, token);
            }

            // 5. 新規絵文字を作成 (アプリ絵文字優先)
            String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
            String newEmojiId = null;
            String usedGuildId = null;

            if (DiscordConfig.preferApplicationEmojis && !applicationId.isEmpty()) {
                newEmojiId = createApplicationEmoji(applicationId, sanitizedName, base64Image, token);
            }

            // アプリ絵文字作成が失敗した場合のみ、ギルド絵文字をフォールバック試行
            if ((newEmojiId == null || newEmojiId.isEmpty()) && DiscordConfig.guildId != null
                    && !DiscordConfig.guildId.isEmpty()) {
                newEmojiId = createGuildEmoji(DiscordConfig.guildId, sanitizedName, base64Image, token);
                if (newEmojiId != null && !newEmojiId.isEmpty()) {
                    usedGuildId = DiscordConfig.guildId;
                }
            }

            if (newEmojiId != null && !newEmojiId.isEmpty()) {
                EmojiData newData = new EmojiData(playerName, newEmojiId, sha256, System.currentTimeMillis(),
                        usedGuildId);
                CACHE.put(uuid, newData);
                saveCache();
                CrossTie.LOGGER.info("[Discord] Synchronized face emoji for {}: <:{}::{}> (Type: {})", playerName,
                        sanitizedName, newEmojiId, (usedGuildId != null ? "Guild" : "Application"));
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to synchronize emoji for {}: {}", playerName, t.getMessage());
        } finally {
            PENDING_FUTURES.remove(uuid);
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

    /**
     * 絵文字名を MCID と完全一致 (プレフィックスなし) で生成する。 Discord の命名規則 (英数字とアンダースコアのみ、2〜32文字)
     * に適合させる。
     */
    private static String sanitizeEmojiName(String name) {
        if (name == null || name.isEmpty()) {
            return "player";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.length() < 2) {
            sanitized = sanitized + "_";
        }
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return sanitized;
    }

    /**
     * Discord アプリケーション絵文字一覧を取得し、名前 -> ID のマップを返す。
     */
    private static Map<String, String> fetchApplicationEmojis(String appId, String botToken) {
        Map<String, String> map = new HashMap<>();
        try {
            URL url = new URL("https://discord.com/api/v10/applications/" + appId + "/emojis");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    @SuppressWarnings("deprecation")
                    JsonObject json = PARSER.parse(reader).getAsJsonObject();
                    if (json.has("items") && json.get("items").isJsonArray()) {
                        JsonArray items = json.getAsJsonArray("items");
                        for (JsonElement elem : items) {
                            if (elem.isJsonObject()) {
                                JsonObject obj = elem.getAsJsonObject();
                                if (obj.has("id") && obj.has("name")) {
                                    map.put(obj.get("name").getAsString(), obj.get("id").getAsString());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to fetch application emojis: {}", t.getMessage());
        }
        return map;
    }

    /**
     * Discord ギルド絵文字一覧を取得し、名前 -> ID のマップを返す。
     */
    private static Map<String, String> fetchGuildEmojis(String guildId, String botToken) {
        Map<String, String> map = new HashMap<>();
        try {
            URL url = new URL("https://discord.com/api/v10/guilds/" + guildId + "/emojis");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + botToken);
            conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    @SuppressWarnings("deprecation")
                    JsonArray arr = PARSER.parse(reader).getAsJsonArray();
                    for (JsonElement elem : arr) {
                        if (elem.isJsonObject()) {
                            JsonObject obj = elem.getAsJsonObject();
                            if (obj.has("id") && obj.has("name")) {
                                map.put(obj.get("name").getAsString(), obj.get("id").getAsString());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to fetch guild emojis: {}", t.getMessage());
        }
        return map;
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
                CrossTie.LOGGER.warn("[Discord] oauth2/applications/@me returned HTTP {}: {}. Trying users/@me...",
                        conn.getResponseCode(), err);
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

    @SuppressWarnings("unused")
    private static final class EmojiCreateRequest {
        public final String name;
        public final String image;

        public EmojiCreateRequest(String name, String image) {
            this.name = name;
            this.image = image;
        }
    }
}
