package net.suzumiya.crosstie;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CrossTie の設定管理クラス。
 *
 * <p>
 * Forge の {@link Configuration} を用いて {@code config/CrossTie/CrossTie.cfg}
 * を自動生成する。 設定値は static フィールドとして公開され、Mod 内の各コンポーネントから参照できる。
 * 将来別の設定ファイルを追加する場合は、このクラスにカテゴリや新しい設定項目を追加するか、 同ディレクトリに別の Configuration
 * インスタンスを作成すること。
 *
 * <p>
 * 本クラスの初期化は {@link CrossTie#preInit(FMLPreInitializationEvent)} で行われる。
 */
public final class CrossTieConfig {

    private static final Logger LOGGER = LogManager.getLogger("CrossTie-Config");

    /**
     * CrossTie の設定ディレクトリ名。 config/CrossTie/ 以下に各種設定ファイルが配置される。
     */
    private static final String CONFIG_DIR_NAME = "CrossTie";

    /**
     * メイン設定ファイル名。
     */
    private static final String CONFIG_FILE_NAME = "CrossTie.cfg";

    /**
     * Config ファイルフォーマットのバージョン。 Mod のアップデートにより設定項目が追加/削除された場合にインクリメントする。 この値が config
     * ファイル内の値と異なる場合、不要になった設定項目が自動削除される。
     */
    private static final int CONFIG_VERSION = 1;

    /**
     * コード上で定義された全設定キーの一覧。 この Set に含まれないキーが config ファイル上に存在する場合、 バージョン不一致時に自動削除される。
     */
    private static final Set<String> KNOWN_KEYS = new HashSet<>(Arrays.asList(
            // General
            "enableNativeRenderGlobalDisplayLists", "enableDiagnostics", "enableSoundDebug", "config_version",
            // Performance
            "trainDistantCullingEnabled", "trainSpeedSyncEnabled", "railTesrThrottleEnabled", "largeRailCullingEnabled",
            "railTessellateOptimizationEnabled", "connectionCacheEnabled", "signboardGuiPagingEnabled",
            "installedObjectCullingEnabled", "decorativeWireOptimizationEnabled", "detectorThrottlingEnabled",
            "detectorThrottleInterval",
            // Fixes
            "fixAngelicaCloudRendering", "fixAngelicaRebuildSync", "fixAngelicaWaterRenderDistance",
            "disableSignalCulling", "fixOptiFineRailBrightness", "fixOptiFineWireNormalize",
            "fixOptiFineWireShadowPass",
            // Features
            "enableWireFastRemove"));

    /** config_version プロパティの名前 */
    private static final String PROP_CONFIG_VERSION = "config_version";

    /** config ファイルパス（{@link #reload()} で使用） */
    private static File configFile;

    private static Configuration config;

    // ---- カテゴリ名 ---- //

    /** パフォーマンス最適化カテゴリ */
    private static final String CAT_PERFORMANCE = "performance";

    /** 互換性修正カテゴリ */
    private static final String CAT_FIXES = "fixes";

    /** 追加機能カテゴリ */
    private static final String CAT_FEATURES = "features";

    // ---- 再起動必須設定項目 ---- //

    /**
     * 再起動が必要な設定項目が変更されたかどうか。 {@link #reload()} 実行後に true の場合、Minecraft の再起動が必要。
     */
    public static boolean requiresRestart = false;

    // ---- 設定項目 (General) ---- //

    /** ネイティブな RenderGlobal.displayList 最適化を有効にするかどうか。再起動必須。 */
    public static boolean enableNativeRenderGlobalDisplayLists;

    /** 診断ログ (CrossTieDiagnostics) を有効にするかどうか。 */
    public static boolean enableDiagnostics;

    /** サウンド系のデバッグログ出力を有効にするかどうか。 */
    public static boolean enableSoundDebug;

    /** Rキー＋右クリックでのワイヤー物理削除機能を有効にするかどうか。 */
    public static boolean enableWireFastRemove;

    // ---- 設定項目 (Performance) ---- //

    /** 256m以上離れたTrainの更新頻度低減 */
    public static boolean trainDistantCullingEnabled;

    /** Train速度DataWatcher同期の最適化 */
    public static boolean trainSpeedSyncEnabled;

    /** LargeRail TESRの距離別スロットル */
    public static boolean railTesrThrottleEnabled;

    /** LargeRailの距離/フラストラムカリング */
    public static boolean largeRailCullingEnabled;

    /** レールテッセレーションループ最適化。再起動必須。 */
    public static boolean railTessellateOptimizationEnabled;

    /** 配線/支柱の接続キャッシュ */
    public static boolean connectionCacheEnabled;

    /** 設置物エンティティ（車止め）のカリング有効化 */
    public static boolean installedObjectCullingEnabled;

    /** お飾り架線（電力供給に関与しない架線）の信号伝播除外 */
    public static boolean decorativeWireOptimizationEnabled;

    /** EntityTrainDetector の更新スロットリング有効化 */
    public static boolean detectorThrottlingEnabled;

    /** EntityTrainDetector のスロットリング間隔（tick毎）。デフォルト4。 */
    public static int detectorThrottleInterval;

    /**
     * サインボード選択 GUI の仮想スクロールを有効にするかどうか。 有効時は画面に表示される行数分のボタンのみ生成するため、 テクスチャ数が多い環境での
     * GUI 開き遅延を大幅に改善する。
     */
    public static boolean signboardGuiPagingEnabled;

    // ---- 設定項目 (Fixes) ---- //

    /** Angelicaの雲描画問題修正 */
    public static boolean fixAngelicaCloudRendering;

    /** Angelica+RTMのリビルド同期修正 */
    public static boolean fixAngelicaRebuildSync;

    /** Angelicaの水描画距離問題修正 */
    public static boolean fixAngelicaWaterRenderDistance;

    /** シグナル/踏切のカリング無効化 */
    public static boolean disableSignalCulling;

    /**
     * OptiFine/FastCraft 環境で OpenGlHelper.lightmapTexUnit が 0 になることによる LargeRail の
     * UV 座標破壊（緑の縦線）を修正する。 Angelica が存在する場合はMixin自体が適用されないため、実質的にAngelica専用修正と完全分離。
     */
    public static boolean fixOptiFineRailBrightness;

    /**
     * RTMのワイヤー描画の非均等スケールで法線が歪み、シェーダー環境で完全に透明になる問題を修正する。 描画時に {@code GL_NORMALIZE}
     * を有効化する。
     */
    public static boolean fixOptiFineWireNormalize;

    /**
     * OptiFine/FastCraft + shadersmod 環境で、shadow pass 中に
     * {@code MinecraftForgeClient.getRenderPass()} が {@code -1} を返すことで ワイヤー（電線）が
     * shadow map に描画されず画面上で消えてしまう問題を修正する。 Angelica が存在する場合はMixin自体が適用されないため自動的に無効。
     */
    public static boolean fixOptiFineWireShadowPass;

    // ---- 初期化 ---- //

    private CrossTieConfig() {
    }

    /**
     * 設定を初期化し、config ファイルをロード/生成する。
     *
     * <p>
     * 初回起動時にはデフォルト値で {@code config/CrossTie/CrossTie.cfg} が自動生成される。
     * 既存のファイルがある場合は既存の値を保持しつつ、コード上の既知キーに含まれない 不要な設定項目は自動削除される。
     *
     * @param event FMLPreInitializationEvent
     */
    public static void init(FMLPreInitializationEvent event) {
        File configDir = new File(event.getModConfigurationDirectory(), CONFIG_DIR_NAME);
        configFile = new File(configDir, CONFIG_FILE_NAME);
        config = new Configuration(configFile);

        try {
            config.load();

            // ---- Config バージョンチェック ---- //
            int storedVersion = config.get(Configuration.CATEGORY_GENERAL, PROP_CONFIG_VERSION, 0).getInt();
            if (storedVersion != CONFIG_VERSION) {
                removeUnknownKeys();
                config.get(Configuration.CATEGORY_GENERAL, PROP_CONFIG_VERSION, CONFIG_VERSION).set(CONFIG_VERSION);
                LOGGER.info("Config version updated: {} -> {}", storedVersion, CONFIG_VERSION);
            }

            loadValues();

        } catch (Exception e) {
            LOGGER.error("Failed to load CrossTie configuration. Using defaults.", e);
            setDefaults();
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }

        LOGGER.info("Configuration loaded from {}", configFile.getAbsolutePath());
    }

    /**
     * config ファイルを再読み込みし、全設定値をディスク上の値で更新する。
     *
     * <p>
     * 再起動が必要な設定項目が変更された場合は {@link #requiresRestart} が true になる。 このメソッドはゲーム内コマンド
     * {@code /crosstie cfgr} から呼ばれる。
     */
    public static void reload() {
        if (config == null) {
            LOGGER.warn("Cannot reload config: not initialized");
            return;
        }

        // 現在の値をスナップショット（再起動必須項目のみ）
        boolean oldNativeRender = enableNativeRenderGlobalDisplayLists;
        boolean oldRailTessellate = railTessellateOptimizationEnabled;

        config.load();

        // 再読み込み
        try {
            loadValues();
        } catch (Exception e) {
            LOGGER.error("Failed to reload CrossTie configuration.", e);
        }

        // 再起動必須項目の変更検出
        requiresRestart = (oldNativeRender != enableNativeRenderGlobalDisplayLists)
                || (oldRailTessellate != railTessellateOptimizationEnabled);

        if (config.hasChanged()) {
            config.save();
        }

        LOGGER.info("Configuration reloaded. requiresRestart={}", requiresRestart);
    }

    /**
     * config ファイルから全設定値を読み込み、static フィールドに反映する。
     */
    private static void loadValues() {
        // ---- 1. General カテゴリ ---- //
        Property prop = config.get(Configuration.CATEGORY_GENERAL, "enableNativeRenderGlobalDisplayLists", false);
        prop.comment = "RenderGlobal.displayList のネイティブ最適化を有効にします。"
                + " Angelica GLSM 使用時に RenderGlobal が DisplayList を使用する場合に適用されます。"
                + " システムプロパティ 'crosstie.enableNativeRenderGlobalDisplayLists' でも設定可能です。" + " [再起動必須]";
        prop.setRequiresMcRestart(true);
        enableNativeRenderGlobalDisplayLists = prop.getBoolean(false);

        enableDiagnostics = config.getBoolean("enableDiagnostics", Configuration.CATEGORY_GENERAL, false,
                "診断ログ (CrossTieDiagnostics) を有効にします。" + " システムプロパティ 'crosstie.diagnostics=true' でも設定可能です。");

        enableSoundDebug = config.getBoolean("enableSoundDebug", Configuration.CATEGORY_GENERAL, false,
                "サウンド系API（車内放送等）のデバッグログ出力を有効にします。");

        // ---- 1.5. Features カテゴリ ---- //
        enableWireFastRemove = config.getBoolean("enableWireFastRemove", CAT_FEATURES, true,
                "Rキー＋右クリックでのワイヤー物理削除機能を有効にします。");

        // ---- 2. Performance カテゴリ ---- //
        trainDistantCullingEnabled = config.getBoolean("trainDistantCullingEnabled", CAT_PERFORMANCE, true,
                "256m以上離れたTrain Entityのクライアント側更新頻度を低減します。");

        trainSpeedSyncEnabled = config.getBoolean("trainSpeedSyncEnabled", CAT_PERFORMANCE, true,
                "Train速度の DataWatcher 同期を最適化し、ネットワーク負荷を低減します。");

        railTesrThrottleEnabled = config.getBoolean("railTesrThrottleEnabled", CAT_PERFORMANCE, true,
                "LargeRail TESR の距離に応じた描画頻度スロットルを有効にします。");

        largeRailCullingEnabled = config.getBoolean("largeRailCullingEnabled", CAT_PERFORMANCE, true,
                "LargeRail の距離カリングとフラストラムカリングを有効にします。");

        prop = config.get(CAT_PERFORMANCE, "railTessellateOptimizationEnabled", true);
        prop.comment = "レールテッセレーションループの最適化を有効にします。 [再起動必須]";
        prop.setRequiresMcRestart(true);
        railTessellateOptimizationEnabled = prop.getBoolean(true);

        connectionCacheEnabled = config.getBoolean("connectionCacheEnabled", CAT_PERFORMANCE, true,
                "配線/支柱の接続判定結果をキャッシュし、パフォーマンスを向上します。");

        signboardGuiPagingEnabled = config.getBoolean("signboardGuiPagingEnabled", CAT_PERFORMANCE, true,
                "サインボード選択 GUI を仮想スクロール方式にし、テクスチャ数が多い環境での" + " GUI 開き遅延を改善します。");

        installedObjectCullingEnabled = config.getBoolean("installedObjectCullingEnabled", CAT_PERFORMANCE, true,
                "車止め（EntityBumpingPost）の描画距離カリングとフラストラムカリングを有効にします。" + " 描画距離はバニラの Render Distance 設定を使用します。");

        decorativeWireOptimizationEnabled = config.getBoolean("decorativeWireOptimizationEnabled", CAT_PERFORMANCE,
                true, "電力供給に関与しないお飾り架線のネットワークを信号伝播処理から除外し、MSPT を低減します。");

        detectorThrottlingEnabled = config.getBoolean("detectorThrottlingEnabled", CAT_PERFORMANCE, true,
                "EntityTrainDetector の在線確認処理をスロットリングし、MSPT を低減します。" + " ATC および信号連動設備には適用されません。");

        detectorThrottleInterval = config.getInt("detectorThrottleInterval", CAT_PERFORMANCE, 4, 1, 20,
                "EntityTrainDetector のスロットリング間隔（tick単位）。デフォルト: 4。" + " 320km/h列車でも1ブロック未満の移動量なので安全です。");

        // ---- 3. Fixes カテゴリ ---- //
        fixAngelicaCloudRendering = config.getBoolean("fixAngelicaCloudRendering", CAT_FIXES, true,
                "Angelica シェーダー有効時にバニラ雲が二重描画される問題を修正します。");

        fixAngelicaRebuildSync = config.getBoolean("fixAngelicaRebuildSync", CAT_FIXES, true,
                "Angelica/Celeritas のブロックリビルド時に RTM レール TESR の" + " ライティングが更新されない問題を修正します。");

        fixAngelicaWaterRenderDistance = config.getBoolean("fixAngelicaWaterRenderDistance", CAT_FIXES, true,
                "Angelica シェーダー有効時に水の描画距離が不正になる問題を修正します。");

        disableSignalCulling = config.getBoolean("disableSignalCulling", CAT_FIXES, true,
                "シグナル/踏切のカリングを無効化し、遠距離でも描画されるようにします。");

        fixOptiFineRailBrightness = config.getBoolean("fixOptiFineRailBrightness", CAT_FIXES, true,
                "OptiFine/FastCraft 環境で OpenGlHelper.lightmapTexUnit が 0 になることによる" + " LargeRail の UV 座標破壊（緑の縦線）を修正します。"
                        + " Angelica 環境では自動的に無効化されます。");

        fixOptiFineWireNormalize = config.getBoolean("fixOptiFineWireNormalize", CAT_FIXES, true,
                "RTMのワイヤー描画時の非均等スケールで法線が歪み、シェーダー環境で完全に透明になる問題を修正します。" + " Angelica 環境では自動的に無効化されます。");

        fixOptiFineWireShadowPass = config.getBoolean("fixOptiFineWireShadowPass", CAT_FIXES, true,
                "OptiFine/FastCraft + shadersmod 環境で shadow pass 中にワイヤー（電線）が" + " 描画されず画面上で消えてしまう問題を修正します。"
                        + " Angelica 環境では自動的に無効化されます。");
    }

    /**
     * デフォルト値を全フィールドにセットする（エラーフォールバック用）。
     */
    private static void setDefaults() {
        enableNativeRenderGlobalDisplayLists = false;
        enableDiagnostics = false;
        enableSoundDebug = false;
        enableWireFastRemove = true;
        trainDistantCullingEnabled = true;
        trainSpeedSyncEnabled = true;
        railTesrThrottleEnabled = true;
        largeRailCullingEnabled = true;
        railTessellateOptimizationEnabled = true;
        connectionCacheEnabled = true;
        signboardGuiPagingEnabled = true;
        installedObjectCullingEnabled = true;
        decorativeWireOptimizationEnabled = true;
        detectorThrottlingEnabled = true;
        detectorThrottleInterval = 4;
        fixAngelicaCloudRendering = true;
        fixAngelicaRebuildSync = true;
        fixAngelicaWaterRenderDistance = true;
        fixOptiFineRailBrightness = true;
        fixOptiFineWireNormalize = true;
        fixOptiFineWireShadowPass = true;
    }

    /**
     * 現在の設定ファイルオブジェクトを取得する。
     *
     * @return Configuration オブジェクト
     */
    public static Configuration getConfig() {
        return config;
    }

    /**
     * 設定値をシステムプロパティで上書きする。
     *
     * <p>
     * システムプロパティが設定されている場合、config ファイルの値より優先される。 このメソッドは
     * {@link CrossTie#preInit(FMLPreInitializationEvent)} の末尾で呼ばれる。
     */
    public static void applySystemPropertyOverrides() {
        String propNative = System.getProperty("crosstie.enableNativeRenderGlobalDisplayLists");
        if (propNative != null) {
            enableNativeRenderGlobalDisplayLists = Boolean.parseBoolean(propNative);
            LOGGER.info("System property override: crosstie.enableNativeRenderGlobalDisplayLists={}",
                    enableNativeRenderGlobalDisplayLists);
        }

        String propDiag = System.getProperty("crosstie.diagnostics");
        if (propDiag != null) {
            enableDiagnostics = Boolean.parseBoolean(propDiag);
            LOGGER.info("System property override: crosstie.diagnostics={}", enableDiagnostics);
        }
    }

    /**
     * config ファイル上に存在するが {@link #KNOWN_KEYS} に含まれないキーを 全てのカテゴリから削除する。
     *
     * <p>
     * これにより Mod のアップデートで不要になった設定項目が config ファイルに
     * 残り続けることを防ぐ。各カテゴリの残存キーが0になった場合、カテゴリ自体も削除される。
     */
    private static void removeUnknownKeys() {
        int totalRemoved = 0;

        for (String catName : config.getCategoryNames()) {
            net.minecraftforge.common.config.ConfigCategory cat = config.getCategory(catName);
            if (cat.isChild()) {
                continue; // 子カテゴリはスキップ
            }

            Set<String> keysInCat = new HashSet<>(cat.keySet());
            int removedInCat = 0;
            for (String key : keysInCat) {
                if (!KNOWN_KEYS.contains(key)) {
                    cat.remove(key);
                    removedInCat++;
                    LOGGER.debug("Removed obsolete config key: {}.{}", catName, key);
                }
            }

            if (removedInCat > 0) {
                totalRemoved += removedInCat;
                LOGGER.info("Removed {} obsolete key(s) from category '{}'", removedInCat, catName);
            }

            // 空になったカテゴリも削除
            if (cat.keySet().isEmpty()) {
                config.removeCategory(cat);
                LOGGER.info("Removed empty config category '{}'", catName);
            }
        }

        if (totalRemoved > 0) {
            LOGGER.info("Config cleanup complete: {} obsolete key(s) removed", totalRemoved);
        }
    }
}