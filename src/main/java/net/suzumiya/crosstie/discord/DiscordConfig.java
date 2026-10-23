package net.suzumiya.crosstie.discord;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.suzumiya.crosstie.CrossTie;

/**
 * CrossTie Discord 連携の設定保持クラス。
 * <p>
 * {@code config/CrossTie/discord.cfg} を専用設定ファイルとして自動生成・管理する。
 */
public final class DiscordConfig {

    public static final String CONFIG_FILE_NAME = "discord.cfg";

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_EVENTS = "events";
    public static final String CATEGORY_FORMATTING = "formatting";

    // 旧設定との互換用カテゴリ名
    private static final String LEGACY_CATEGORY = "discord";

    private static File configFile;
    private static Configuration config;

    // ---- General 設定 ---- //
    public static boolean enabled = false;
    public static String rawBotToken = "file:config/CrossTie/discord.token";
    public static String guildId = "";
    public static String channelId = "";
    public static String rawWebhookUrl = "";
    public static boolean enablePlayerFaceEmojis = true;
    public static boolean preferApplicationEmojis = true;
    public static String avatarUrlTemplate = "https://crafatar.com/avatars/{uuid-nodashes}?size=256&overlay";

    // ---- Event 通知設定 ---- //
    public static boolean sendChat = true;
    public static boolean sendJoinLeave = true;
    public static boolean sendDeath = true;
    public static boolean sendServerLifecycle = true;

    // ---- Formatting 設定 ---- //
    public static String formatDiscordToMc = "§9[Discord] §r<%name%> %message%";
    public static String formatMcToDiscord = "%emoji% %name%: %message%";
    public static String formatPlayerJoin = "%emoji% %name%: がサーバーに参加しました";
    public static String formatPlayerLeave = "%emoji% %name%: がサーバーから退出しました";
    public static String formatPlayerDeath = "%emoji% %deathmessage%";
    public static String formatServerStart = "サーバーが起動しました";
    public static String formatServerStop = "サーバーが停止しました";

    // 解決済みの実行時トークン / URL
    public static String resolvedBotToken = "";
    public static String resolvedWebhookUrl = "";

    private DiscordConfig() {
    }

    /**
     * Discord 設定ファイルを初期化・ロードする。
     *
     * @param configDir     設定ディレクトリ (config/CrossTie/)
     * @param legacyConfig  CrossTie.cfg (旧設定のマイグレーション用、null可)
     */
    public static void init(File configDir, Configuration legacyConfig) {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        configFile = new File(configDir, CONFIG_FILE_NAME);
        boolean isNewFile = !configFile.exists();
        config = new Configuration(configFile);

        try {
            config.load();

            // 旧 CrossTie.cfg からのマイグレーション (新規作成時かつ旧設定が存在する場合)
            Configuration migrationSource = isNewFile ? legacyConfig : null;
            loadValues(migrationSource);

        } catch (Exception e) {
            CrossTie.LOGGER.error("[Discord] Failed to load Discord configuration from: {}", configFile.getAbsolutePath(), e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }

        CrossTie.LOGGER.info("[Discord] Configuration initialized at: {}", configFile.getAbsolutePath());
    }

    /**
     * Discord 設定ファイルを再読み込みする。
     */
    public static void reload() {
        if (config == null || configFile == null) {
            return;
        }

        try {
            config.load();
            loadValues(null);
            CrossTie.LOGGER.info("[Discord] Configuration reloaded from: {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            CrossTie.LOGGER.error("[Discord] Failed to reload Discord configuration from: {}", configFile.getAbsolutePath(), e);
        }
    }

    /**
     * 設定ファイルから各プロパティを読み込む。
     */
    private static void loadValues(Configuration migrationSource) {
        config.setCategoryComment(CATEGORY_GENERAL, "General Discord Integration Settings");
        config.setCategoryComment(CATEGORY_EVENTS, "Discord Event Notification Toggles");
        config.setCategoryComment(CATEGORY_FORMATTING, "Message Formatting Templates");

        // ---- General ---- //
        boolean defEnabled = getLegacyBoolean(migrationSource, "enabled", false);
        Property propEnabled = config.get(CATEGORY_GENERAL, "enabled", defEnabled);
        propEnabled.comment = "Enable built-in Discord integration (Dedicated Server only). Default: false";
        enabled = propEnabled.getBoolean();

        String defBotToken = getLegacyString(migrationSource, "botToken", "file:config/CrossTie/discord.token");
        Property propToken = config.get(CATEGORY_GENERAL, "botToken", defBotToken);
        propToken.comment = "Discord Bot Token. Can use 'env:VAR_NAME' or 'file:path/to/token'.";
        rawBotToken = propToken.getString();

        String defGuildId = getLegacyString(migrationSource, "guildId", "");
        Property propGuild = config.get(CATEGORY_GENERAL, "guildId", defGuildId);
        propGuild.comment = "Target Discord Server (Guild) ID.";
        guildId = propGuild.getString().trim();

        String defChannelId = getLegacyString(migrationSource, "channelId", "");
        Property propChannel = config.get(CATEGORY_GENERAL, "channelId", defChannelId);
        propChannel.comment = "Target Discord Text Channel ID for chat synchronization.";
        channelId = propChannel.getString().trim();

        String defWebhookUrl = getLegacyString(migrationSource, "webhookUrl", "");
        Property propWebhook = config.get(CATEGORY_GENERAL, "webhookUrl", defWebhookUrl);
        propWebhook.comment = "Discord Webhook URL for rich outbound messages (optional, falls back to Bot API).";
        rawWebhookUrl = propWebhook.getString();

        boolean defFaceEmojis = getLegacyBoolean(migrationSource, "enablePlayerFaceEmojis", true);
        Property propEmojis = config.get(CATEGORY_GENERAL, "enablePlayerFaceEmojis", defFaceEmojis);
        propEmojis.comment = "Automatically upload and synchronize player face emojis to the Bot Application or Guild.";
        enablePlayerFaceEmojis = propEmojis.getBoolean();

        boolean defPreferAppEmojis = getLegacyBoolean(migrationSource, "preferApplicationEmojis", true);
        Property propPreferApp = config.get(CATEGORY_GENERAL, "preferApplicationEmojis", defPreferAppEmojis);
        propPreferApp.comment = "Prefer Discord Application Emojis (Bot-owned, 2000 slots) over Guild Emojis. Default: true";
        preferApplicationEmojis = propPreferApp.getBoolean();

        String defAvatarUrl = getLegacyString(migrationSource, "avatarUrlTemplate", "https://crafatar.com/avatars/{uuid-nodashes}?size=256&overlay");
        Property propAvatar = config.get(CATEGORY_GENERAL, "avatarUrlTemplate", defAvatarUrl);
        propAvatar.comment = "URL template to fetch player avatar/skin image for webhooks and face emojis. Placeholders: {uuid}, {uuid-nodashes}, {name}, {size}.";
        avatarUrlTemplate = propAvatar.getString();

        // ---- Events ---- //
        boolean defSendChat = getLegacyBoolean(migrationSource, "sendChat", true);
        Property propSendChat = config.get(CATEGORY_EVENTS, "sendChat", defSendChat);
        propSendChat.comment = "Send player chat messages to Discord.";
        sendChat = propSendChat.getBoolean();

        boolean defSendJoin = getLegacyBoolean(migrationSource, "sendJoinLeave", true);
        Property propSendJoin = config.get(CATEGORY_EVENTS, "sendJoinLeave", defSendJoin);
        propSendJoin.comment = "Send player join and leave messages to Discord.";
        sendJoinLeave = propSendJoin.getBoolean();

        boolean defSendDeath = getLegacyBoolean(migrationSource, "sendDeath", true);
        Property propSendDeath = config.get(CATEGORY_EVENTS, "sendDeath", defSendDeath);
        propSendDeath.comment = "Send player death messages to Discord.";
        sendDeath = propSendDeath.getBoolean();

        boolean defSendLifecycle = getLegacyBoolean(migrationSource, "sendServerLifecycle", true);
        Property propSendLifecycle = config.get(CATEGORY_EVENTS, "sendServerLifecycle", defSendLifecycle);
        propSendLifecycle.comment = "Send server start and stop messages to Discord.";
        sendServerLifecycle = propSendLifecycle.getBoolean();

        // ---- Formatting ---- //
        String defFmtD2M = getLegacyString(migrationSource, "formatDiscordToMc", "§9[Discord] §r<%name%> %message%");
        Property propFmtD2M = config.get(CATEGORY_FORMATTING, "formatDiscordToMc", defFmtD2M);
        propFmtD2M.comment = "Message format when receiving from Discord to Minecraft.";
        formatDiscordToMc = propFmtD2M.getString();

        String defFmtM2D = getLegacyString(migrationSource, "formatMcToDiscord", "%emoji% %name%: %message%");
        Property propFmtM2D = config.get(CATEGORY_FORMATTING, "formatMcToDiscord", defFmtM2D);
        propFmtM2D.comment = "Message format when sending player chat to Discord.";
        formatMcToDiscord = propFmtM2D.getString();

        String defFmtJoin = getLegacyString(migrationSource, "formatPlayerJoin", "%emoji% %name%: がサーバーに参加しました");
        Property propFmtJoin = config.get(CATEGORY_FORMATTING, "formatPlayerJoin", defFmtJoin);
        propFmtJoin.comment = "Message format when player joins the server.";
        formatPlayerJoin = propFmtJoin.getString();

        String defFmtLeave = getLegacyString(migrationSource, "formatPlayerLeave", "%emoji% %name%: がサーバーから退出しました");
        Property propFmtLeave = config.get(CATEGORY_FORMATTING, "formatPlayerLeave", defFmtLeave);
        propFmtLeave.comment = "Message format when player leaves the server.";
        formatPlayerLeave = propFmtLeave.getString();

        String defFmtDeath = getLegacyString(migrationSource, "formatPlayerDeath", "%emoji% %deathmessage%");
        Property propFmtDeath = config.get(CATEGORY_FORMATTING, "formatPlayerDeath", defFmtDeath);
        propFmtDeath.comment = "Message format when player dies. Use %emoji%, %name%, %deathmessage%.";
        formatPlayerDeath = propFmtDeath.getString();

        String defFmtStart = getLegacyString(migrationSource, "formatServerStart", "サーバーが起動しました");
        Property propFmtStart = config.get(CATEGORY_FORMATTING, "formatServerStart", defFmtStart);
        propFmtStart.comment = "Message format when server starts.";
        formatServerStart = propFmtStart.getString();

        String defFmtStop = getLegacyString(migrationSource, "formatServerStop", "サーバーが停止しました");
        Property propFmtStop = config.get(CATEGORY_FORMATTING, "formatServerStop", defFmtStop);
        propFmtStop.comment = "Message format when server stops.";
        formatServerStop = propFmtStop.getString();

        // 秘密情報・トークンを解決
        resolvedBotToken = DiscordSecretManager.resolveToken(rawBotToken);
        resolvedWebhookUrl = DiscordSecretManager.resolveString(rawWebhookUrl);
    }

    public static String getAvatarUrl(java.util.UUID uuid, String name) {
        return getAvatarUrl(uuid, name, 256);
    }

    public static String getAvatarUrl(java.util.UUID uuid, String name, int size) {
        String tpl = avatarUrlTemplate;
        if (tpl == null || tpl.trim().isEmpty()) {
            tpl = "https://crafatar.com/avatars/{uuid-nodashes}?size=256&overlay";
        }
        String uuidStr = uuid != null ? uuid.toString() : "";
        String uuidNoDashes = uuidStr.replace("-", "");
        String safeName = name != null ? name : "";
        String url = tpl
                .replace("{uuid}", uuidStr)
                .replace("{uuid-nodashes}", uuidNoDashes)
                .replace("{name}", safeName)
                .replace("{size}", String.valueOf(size));
        if (size != 256 && url.contains("size=")) {
            url = url.replaceAll("size=\\d+", "size=" + size);
        }
        return url;
    }

    private static boolean getLegacyBoolean(Configuration legacy, String key, boolean defaultValue) {
        if (legacy != null && legacy.hasCategory(LEGACY_CATEGORY) && legacy.getCategory(LEGACY_CATEGORY).containsKey(key)) {
            return legacy.get(LEGACY_CATEGORY, key, defaultValue).getBoolean();
        }
        return defaultValue;
    }

    private static String getLegacyString(Configuration legacy, String key, String defaultValue) {
        if (legacy != null && legacy.hasCategory(LEGACY_CATEGORY) && legacy.getCategory(LEGACY_CATEGORY).containsKey(key)) {
            return legacy.get(LEGACY_CATEGORY, key, defaultValue).getString();
        }
        return defaultValue;
    }

    public static Configuration getConfig() {
        return config;
    }

    public static File getConfigFile() {
        return configFile;
    }
}
