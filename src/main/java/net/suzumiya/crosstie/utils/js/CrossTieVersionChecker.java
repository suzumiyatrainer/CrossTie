package net.suzumiya.crosstie.utils.js;

import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS(Nashorn)側からCrossTieの存在・バージョンを安全に確認するためのユーティリティ。
 *
 * 【使い方(JS側)】 1. Java.type(...) 自体をtry/catchで包み、クラスの存在有無を確認する 2. exists() /
 * getVersion() / isAtLeast() の呼び出しもtry/catchで包む
 * (将来のCrossTie側でメソッドが削除/変更される可能性に備えるため)
 *
 * 【build.gradle側の前提】 jar化時にManifestへ Implementation-Version を書き込む必要があります。
 * build.gradle の jar { } ブロックに以下を追加してください:
 *
 * jar { manifest { attributes 'Implementation-Version': project.version } }
 *
 * これにより project.version (例: "1.0.0-Alpha13") が自動的にここへ反映され、
 * build.gradle側の変更のたびにこのファイルを書き換える必要がなくなります。 (Manifestが無い開発環境実行時は
 * propertiesファイルから読み取ります)
 */
public class CrossTieVersionChecker {

    private static final String MOD_ID_PREFIX = "CrossTie";
    private static final String FALLBACK_VERSION = "UnkownVersion";

    private CrossTieVersionChecker() {
    }

    /**
     * JS側の存在確認用。 このメソッドが呼べている時点でCrossTieはクラスパス上に存在するので、常にtrueを返す。 (JS側では
     * Java.type(...) のtry/catchで「クラスが無い」ケースを既に弾いている前提)
     */
    public static boolean exists() {
        return true;
    }

    /**
     * "CrossTie-1.0.0-Alpha13" の形式でフルバージョン文字列を返す。
     */
    public static String getVersion() {
        return MOD_ID_PREFIX + "-" + getRawVersion();
    }

    /**
     * 接頭辞なしの生バージョン文字列("1.0.0-Alpha13")を返す。 バージョン比較にはこちらを使う。
     */
    public static String getRawVersion() {
        try (InputStream is = CrossTieVersionChecker.class
                .getResourceAsStream("/net/suzumiya/crosstie/crosstie_version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String propVer = props.getProperty("version");
                if (propVer != null && !propVer.isEmpty() && !propVer.startsWith("${")) {
                    return propVer;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        Package pkg = CrossTieVersionChecker.class.getPackage();
        String implVersion = (pkg != null) ? pkg.getImplementationVersion() : null;
        if (implVersion == null || implVersion.isEmpty()) {
            return FALLBACK_VERSION;
        }
        return implVersion;
    }

    /**
     * 現在のCrossTieバージョンが指定バージョン以上かどうかを判定する。
     *
     * @param requiredRawVersion 例: "1.0.0-Alpha13" (接頭辞 "CrossTie-" は付けない)
     * @return 現在バージョン &gt;= requiredRawVersion なら true。
     *         バージョン情報が取得できない(FALLBACK_VERSION)場合は安全側に倒してfalseを返す。
     */
    public static boolean isAtLeast(String requiredRawVersion) {
        if (requiredRawVersion == null || requiredRawVersion.isEmpty()) {
            return true;
        }
        String current = getRawVersion();
        if (FALLBACK_VERSION.equals(current)) {
            // Manifestが取れない(=開発環境でjar化されていない等)場合は
            // 「未対応」として安全側に倒す。本番jarでは通常発生しない。
            return false;
        }
        return compareVersions(current, requiredRawVersion) >= 0;
    }

    // ==================== バージョン比較ロジック ====================
    // "MAJOR.MINOR.PATCH[-Stage][StageNum]" 形式を想定
    // 例: "1.0.0-Alpha13" -> numeric=[1,0,0], stage="Alpha", stageNum=13
    // ステージ順位: Alpha < Beta < RC < (無印/Release)

    private static final Pattern STAGE_PATTERN = Pattern.compile("^([a-zA-Z]+)(\\d*)$");

    private static int compareVersions(String v1, String v2) {
        VersionParts p1 = parse(v1);
        VersionParts p2 = parse(v2);

        for (int i = 0; i < 3; i++) {
            int a = i < p1.numeric.length ? p1.numeric[i] : 0;
            int b = i < p2.numeric.length ? p2.numeric[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }

        int stageCmp = Integer.compare(stageRank(p1.stage), stageRank(p2.stage));
        if (stageCmp != 0) {
            return stageCmp;
        }

        return Integer.compare(p1.stageNum, p2.stageNum);
    }

    private static int stageRank(String stage) {
        if (stage == null) {
            return 100; // ステージ表記なし = 正式リリース扱い
        }
        String s = stage.toLowerCase();
        if (s.startsWith("alpha")) {
            return 0;
        }
        if (s.startsWith("beta")) {
            return 1;
        }
        if (s.startsWith("rc")) {
            return 2;
        }
        return 50; // 未知のステージ名はBetaとReleaseの中間扱い
    }

    private static class VersionParts {
        int[] numeric;
        String stage;
        int stageNum;
    }

    private static VersionParts parse(String raw) {
        VersionParts vp = new VersionParts();
        String numericPart = raw;
        String stagePart = null;

        int dashIdx = raw.indexOf('-');
        if (dashIdx >= 0) {
            numericPart = raw.substring(0, dashIdx);
            stagePart = raw.substring(dashIdx + 1);
        }

        String[] nums = numericPart.split("\\.");
        vp.numeric = new int[nums.length];
        for (int i = 0; i < nums.length; i++) {
            vp.numeric[i] = safeParseInt(nums[i]);
        }

        if (stagePart != null) {
            Matcher m = STAGE_PATTERN.matcher(stagePart);
            if (m.matches()) {
                vp.stage = m.group(1);
                vp.stageNum = m.group(2).isEmpty() ? 0 : safeParseInt(m.group(2));
            } else {
                vp.stage = stagePart;
                vp.stageNum = 0;
            }
        } else {
            vp.stage = null;
            vp.stageNum = 0;
        }

        return vp;
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}