package net.suzumiya.crosstie.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.suzumiya.crosstie.CrossTie;

/**
 * Discord サーバー (Guild) のロール情報 (ロール名、階層位置、色) を管理し、
 * Minecraft チャット用カラーコードへの変換や最上位ロール解決を行うマネージャー。
 */
public final class DiscordRoleManager {

    private static final Gson GSON = new Gson();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrossTie-DiscordRole-Worker");
        t.setDaemon(true);
        return t;
    });

    // guild_id -> (role_id -> RoleInfo)
    private static final Map<String, Map<String, RoleInfo>> GUILD_ROLES = new ConcurrentHashMap<>();

    private static final int[][] MC_PALETTE = {
        {0x00, 0x00, 0x00, 0}, // §0
        {0x00, 0x00, 0xAA, 1}, // §1
        {0x00, 0xAA, 0x00, 2}, // §2
        {0x00, 0xAA, 0xAA, 3}, // §3
        {0xAA, 0x00, 0x00, 4}, // §4
        {0xAA, 0x00, 0xAA, 5}, // §5
        {0xFF, 0xAA, 0x00, 6}, // §6
        {0xAA, 0xAA, 0xAA, 7}, // §7
        {0x55, 0x55, 0x55, 8}, // §8
        {0x55, 0x55, 0xFF, 9}, // §9
        {0x55, 0xFF, 0x55, 10}, // §a
        {0x55, 0xFF, 0xFF, 11}, // §b
        {0xFF, 0x55, 0x55, 12}, // §c
        {0xFF, 0x55, 0xFF, 13}, // §d
        {0xFF, 0xFF, 0x55, 14}, // §e
        {0xFF, 0xFF, 0xFF, 15}  // §f
    };
    private static final char[] CODE_CHARS = "0123456789abcdef".toCharArray();

    private DiscordRoleManager() {}

    /**
     * GUILD_CREATE イベントや REST API レスポンスからギルド全ロール情報をキャッシュする。
     */
    public static void updateGuildRoles(String guildId, JsonArray rolesArray) {
        if (guildId == null || guildId.isEmpty() || rolesArray == null) return;
        Map<String, RoleInfo> map = GUILD_ROLES.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());
        for (JsonElement elem : rolesArray) {
            if (elem.isJsonObject()) {
                RoleInfo role = parseRole(elem.getAsJsonObject());
                if (role != null) {
                    map.put(role.id, role);
                }
            }
        }
        CrossTie.LOGGER.info("[Discord] Cached {} roles for guild {}", map.size(), guildId);
    }

    public static void updateRole(String guildId, JsonObject roleObj) {
        if (guildId == null || guildId.isEmpty() || roleObj == null) return;
        RoleInfo role = parseRole(roleObj);
        if (role != null) {
            GUILD_ROLES.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>()).put(role.id, role);
        }
    }

    public static void removeRole(String guildId, String roleId) {
        if (guildId == null || roleId == null) return;
        Map<String, RoleInfo> map = GUILD_ROLES.get(guildId);
        if (map != null) {
            map.remove(roleId);
        }
    }

    /**
     * メンバーのロール ID 配列から、最も階層位置 (position) が高い最上位ロールを取得する。
     */
    public static RoleInfo getHighestRole(String guildId, JsonArray memberRoles) {
        if (guildId == null || guildId.isEmpty()) {
            guildId = DiscordConfig.guildId;
        }
        if (guildId == null || guildId.isEmpty()) return null;

        Map<String, RoleInfo> map = GUILD_ROLES.get(guildId);
        if (map == null || map.isEmpty()) {
            fetchRolesAsync(guildId);
            return null;
        }

        if (memberRoles == null || memberRoles.size() == 0) {
            return null;
        }

        RoleInfo highest = null;
        for (JsonElement elem : memberRoles) {
            String roleId = elem.getAsString();
            RoleInfo r = map.get(roleId);
            if (r != null) {
                // @everyone (guild_id と同値) は除外
                if (r.id.equals(guildId)) continue;
                if (highest == null || r.position > highest.position) {
                    highest = r;
                }
            }
        }
        return highest;
    }

    /**
     * メンバーが持つロールの中で、色が設定されている最上位ロールの Minecraft カラーコードを取得する。
     */
    public static String getMemberColorCode(String guildId, JsonArray memberRoles) {
        if (guildId == null || guildId.isEmpty()) {
            guildId = DiscordConfig.guildId;
        }
        if (guildId == null || guildId.isEmpty() || memberRoles == null || memberRoles.size() == 0) {
            return "§7";
        }

        Map<String, RoleInfo> map = GUILD_ROLES.get(guildId);
        if (map == null || map.isEmpty()) {
            fetchRolesAsync(guildId);
            return "§7";
        }

        RoleInfo highestColored = null;
        for (JsonElement elem : memberRoles) {
            String roleId = elem.getAsString();
            RoleInfo r = map.get(roleId);
            if (r != null && r.color != 0) {
                if (highestColored == null || r.position > highestColored.position) {
                    highestColored = r;
                }
            }
        }

        if (highestColored != null) {
            return highestColored.mcColor;
        }
        return "§7";
    }

    /**
     * キャッシュが空の場合に REST API から全ロールを非同期取得する。
     */
    public static void fetchRolesAsync(String guildId) {
        if (guildId == null || guildId.isEmpty()) return;
        String token = DiscordConfig.resolvedBotToken;
        if (token == null || token.isEmpty()) return;

        WORKER.submit(() -> {
            try {
                URL url = new URL("https://discord.com/api/v10/guilds/" + guildId + "/roles");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bot " + token);
                conn.setRequestProperty("User-Agent", "CrossTie-Discord/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonElement json = GSON.fromJson(reader, JsonElement.class);
                        if (json != null && json.isJsonArray()) {
                            updateGuildRoles(guildId, json.getAsJsonArray());
                        }
                    }
                }
            } catch (Throwable t) {
                CrossTie.LOGGER.warn("[Discord] Failed to fetch guild roles via REST for {}: {}", guildId, t.getMessage());
            }
        });
    }

    private static RoleInfo parseRole(JsonObject obj) {
        if (obj == null || !obj.has("id") || !obj.has("name")) return null;
        String id = obj.get("id").getAsString();
        String name = obj.get("name").getAsString();
        int color = obj.has("color") ? obj.get("color").getAsInt() : 0;
        int position = obj.has("position") ? obj.get("position").getAsInt() : 0;
        String mcColor = rgbToMinecraftCode(color);
        return new RoleInfo(id, name, color, position, mcColor);
    }

    /**
     * RGB 整数値を、視覚的ユークリッド距離 (Redmean) で最も近い Minecraft チャット装飾コード (§0〜§f) にマッピングする。
     */
    public static String rgbToMinecraftCode(int rgb) {
        if (rgb == 0) return "§7"; // 色未設定は薄灰色
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int bestIndex = 7;
        double minDistance = Double.MAX_VALUE;

        // 純黒 (§0) は見づらいためインデックス 1 以降から探索
        for (int i = 1; i < MC_PALETTE.length; i++) {
            int pr = MC_PALETTE[i][0];
            int pg = MC_PALETTE[i][1];
            int pb = MC_PALETTE[i][2];

            double rmean = (r + pr) / 2.0;
            double dr = r - pr;
            double dg = g - pg;
            double db = b - pb;
            double distance = Math.sqrt((2 + rmean / 256.0) * dr * dr + 4.0 * dg * dg + (2 + (255 - rmean) / 256.0) * db * db);

            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        return "§" + CODE_CHARS[bestIndex];
    }

    public static final class RoleInfo {
        public final String id;
        public final String name;
        public final int color;
        public final int position;
        public final String mcColor;

        public RoleInfo(String id, String name, int color, int position, String mcColor) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.position = position;
            this.mcColor = mcColor;
        }
    }
}
