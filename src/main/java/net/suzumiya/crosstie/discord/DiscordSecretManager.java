package net.suzumiya.crosstie.discord;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Discord Bot トークンおよび Webhook URL 等の機密情報を安全に取得・解決するマネージャー。
 *
 * <p>
 * 多層解決方式 (12-Factor App 準拠): 1. 環境変数 CROSSTIE_DISCORD_TOKEN 2. JVM システムプロパティ
 * -Dcrosstie.discord.token 3. 専用シークレットファイル (config/CrossTie/discord.token) 4.
 * config 記述 (file: / env: プレフィックス対応、平文フォールバック)
 */
public final class DiscordSecretManager {

    private static final String ENV_TOKEN_KEY = "CROSSTIE_DISCORD_TOKEN";
    private static final String SYS_PROP_TOKEN_KEY = "crosstie.discord.token";
    private static final String DEFAULT_TOKEN_FILE = "config/CrossTie/discord.token";

    private DiscordSecretManager() {
    }

    /**
     * トークンを多層フォールバック方式で安全に解決する。
     *
     * @param configuredValue crosstie.cfg に記述された文字列 (未設定時は空文字)
     * @return 解決されたトークン文字列。存在しない場合は空文字。
     */
    public static String resolveToken(String configuredValue) {
        // 1. 環境変数
        String env = System.getenv(ENV_TOKEN_KEY);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }

        // 2. JVM システムプロパティ
        String sysProp = System.getProperty(SYS_PROP_TOKEN_KEY);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }

        // 3. 設定値のプレフィックス判定
        if (configuredValue != null && !configuredValue.trim().isEmpty()) {
            String val = configuredValue.trim();
            if (val.startsWith("env:")) {
                String envName = val.substring(4).trim();
                String v = System.getenv(envName);
                return v != null ? v.trim() : "";
            } else if (val.startsWith("file:")) {
                String filePath = val.substring(5).trim();
                return readFileContent(new File(filePath));
            } else if (!val.equals(DEFAULT_TOKEN_FILE)) {
                // 平文指定 (非推奨だが互換性のため維持)
                return val;
            }
        }

        // 4. デフォルトシークレットファイル
        File defaultFile = new File(DEFAULT_TOKEN_FILE);
        if (defaultFile.exists() && defaultFile.isFile()) {
            return readFileContent(defaultFile);
        }

        return "";
    }

    /**
     * Webhook URL などの汎用文字列を解決する (file: や env: に対応)。
     */
    public static String resolveString(String configuredValue) {
        if (configuredValue == null || configuredValue.trim().isEmpty()) {
            return "";
        }
        String val = configuredValue.trim();
        if (val.startsWith("env:")) {
            String envName = val.substring(4).trim();
            String v = System.getenv(envName);
            return v != null ? v.trim() : "";
        } else if (val.startsWith("file:")) {
            String filePath = val.substring(5).trim();
            return readFileContent(new File(filePath));
        }
        return val;
    }

    private static String readFileContent(File file) {
        try {
            if (file.exists() && file.isFile()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8).trim();
                // 1行目のみを取り出す (改行コード除去)
                int newlineIdx = content.indexOf('\n');
                if (newlineIdx != -1) {
                    content = content.substring(0, newlineIdx).trim();
                }
                return content;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * ログ出力用にトークンを安全にマスキングする。
     */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "(none)";
        }
        if (token.length() <= 8) {
            return "********";
        }
        return token.substring(0, 4) + "****************" + token.substring(token.length() - 4);
    }
}
