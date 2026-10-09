package net.suzumiya.crosstie.asm;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;
import net.suzumiya.crosstie.utils.ModDetector;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("CrossTieCore")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 4)
public class CrossTieCorePlugin implements IFMLLoadingPlugin,
        com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader,
        io.github.tox1cozz.mixinbooterlegacy.IEarlyMixinLoader {

    private static boolean minfoDetected;
    private static ModDetector modDetector;
    private static File mcDataDir;

    @Override
    public String getMixinConfig() {
        return "mixins.crosstie.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoremods) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getMixinConfigs() {
        // GTNHMixins registers the early config with its selected mixin list.
        // Avoid queueing the same config a second time through MixinBooterLegacy.
        return Collections.emptyList();
    }

    public static boolean isMinFoDetected() {
        return minfoDetected;
    }

    public static ModDetector getModDetector() {
        return modDetector;
    }

    /** Minecraft の実行ディレクトリ。{@code injectData()} 完了後に参照可能。 */
    public static File getMcDataDir() {
        return mcDataDir;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "net.suzumiya.crosstie.asm.CrossTieClassTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // macOS + lwjgl3ify 環境では GLFW がメインスレッドを占有するため AWT は headless 必須。
        // RFB が通常セットするが、非 RFB 起動パスでも動作するよう防御的にここでも設定する。
        // （LWJGL3ify-rtm-1.0.0 由来）
        forceHeadlessOnMac();

        // Resolve mcDataDir - the Minecraft run directory
        File mcDataDir = null;
        if (data != null) {
            // Try to get the Minecraft data directory from the data map.
            // Forge 1.7.10 provides it under the key "mcDataDir", while
            // vanilla/Forge may use "mcLocation". We fallback to the latter
            // if the former is not present.
            Object mcDir = data.get("mcDataDir");
            if (mcDir instanceof File) {
                mcDataDir = (File) mcDir;
            } else {
                Object mcLoc = data.get("mcLocation");
                if (mcLoc instanceof File) {
                    mcDataDir = (File) mcLoc;
                }
            }
        }

        // Initialize mod detector and scan for MinFo by JAR file name
        CrossTieCorePlugin.mcDataDir = mcDataDir;
        modDetector = new ModDetector(mcDataDir);
        System.out.println("[CrossTieCore] mcDataDir: " + (mcDataDir != null ? mcDataDir.getAbsolutePath() : "null"));
        minfoDetected = modDetector.isModPresent("MinFo");

        System.out.println("[CrossTieCore] MinFo detected: " + minfoDetected);

        if (minfoDetected) {
            disableAngelicaFontRenderer(mcDataDir);
        }

        // mods/modelpacks/ 内の ZIP / JAR をクラスパスに追加する。
        // KaizPatchX の FIXFileLoader.getInputStream() が ResourceLocation レベルで
        // 解決するが、Minecraft 本体のリソースシステムが ZIP 内を参照できるよう
        // LaunchClassLoader にも登録しておく。（LWJGL3ify-rtm-1.0.0 由来）
        injectModelpackZips(mcDataDir);
    }

    /**
     * macOS かつ {@code java.awt.headless} が未設定の場合にのみ {@code true} をセットする。
     *
     * <p>lwjgl3ify は macOS で GLFW がメインスレッドを占有するため AWT を headless にしなければならない。
     * RTM の {@code ModelPackLoadThread} は headless 時に Swing 進捗ウィンドウをスキップするため、
     * この設定が RTM 側の Swing クラッシュも合わせて防ぐ。
     * ユーザーが明示的に設定済みの場合は上書きしない。
     *
     * <p><b>原作:</b> LWJGL3ify-rtm-1.0.0 by 325 (LGPL-3.0-or-later)
     */
    private static void forceHeadlessOnMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return;
        }
        if (System.getProperty("java.awt.headless") != null) {
            return;
        }
        System.setProperty("java.awt.headless", "true");
        System.out.println("[CrossTieCore] macOS を検出しました。lwjgl3ify 互換のため java.awt.headless=true を設定しました。");
    }

    /**
     * {@code mods/modelpacks/} 内の {@code *.zip} / {@code *.jar} を
     * {@link Launch#classLoader} に追加する。
     *
     * <p>KaizPatchX の {@code FIXFileLoader} が {@code ResourceLocation} レベルで
     * モデルパックを解決するが、Minecraft 本体のクラスローダー経由のリソース検索でも
     * 同 ZIP 内のアセットが参照できるよう classpath に登録する。
     * 登録順はファイル名の辞書順とし再現性を確保する。
     *
     * <p><b>原作:</b> LWJGL3ify-rtm-1.0.0 by 325 (LGPL-3.0-or-later)
     */
    private static void injectModelpackZips(File mcDataDir) {
        if (mcDataDir == null) {
            return;
        }
        File modelpackDir = new File(mcDataDir, "mods/modelpacks");
        if (!modelpackDir.isDirectory()) {
            System.out.println("[CrossTieCore] mods/modelpacks ディレクトリが見つかりません: " + modelpackDir.getAbsolutePath());
            return;
        }
        File[] files = modelpackDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".zip") || lower.endsWith(".jar");
        });
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files);
        for (File archive : files) {
            try {
                Launch.classLoader.addURL(archive.toURI().toURL());
                System.out.println("[CrossTieCore] モデルパックアーカイブをクラスパスに追加しました: " + archive.getName());
            } catch (MalformedURLException e) {
                System.err.println("[CrossTieCore] モデルパックアーカイブの追加に失敗しました: "
                        + archive.getAbsolutePath() + " - " + e.getMessage());
            }
        }
    }

    private void disableAngelicaFontRenderer(File mcDataDir) {
        if (mcDataDir == null) {
            System.out.println("[CrossTieCore] mcDataDir is null, cannot modify angelica-modules.cfg");
            return;
        }

        Path configDir = mcDataDir.toPath().resolve("config");
        Path configFile = configDir.resolve("angelica-modules.cfg");

        if (Files.exists(configFile)) {
            try {
                List<String> lines = Files.readAllLines(configFile);
                boolean modified = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("B:enableFontRenderer=")) {
                        String current = line.substring("B:enableFontRenderer=".length());
                        if (!current.equals("false")) {
                            lines.set(i, "B:enableFontRenderer=false");
                            modified = true;
                            System.out.println(
                                    "[CrossTieCore] Overriding angelica-modules.cfg: enableFontRenderer=false (MinFo conflict)");
                        }
                        break;
                    }
                }
                if (modified) {
                    Files.write(configFile, lines);
                }
                return;
            } catch (IOException e) {
                System.err.println("[CrossTieCore] Failed to modify " + configFile + ": " + e.getMessage());
            }
        }

        // If no config file found, create one
        try {
            Files.createDirectories(configDir);
            String content = "# Angelica modules configuration\n"
                    + "# Modified by CrossTie: MinFo detected, disabling font renderer to prevent conflict\n"
                    + "B:enableFontRenderer=false\n";
            Files.write(configFile, content.getBytes());
            System.out.println(
                    "[CrossTieCore] Created angelica-modules.cfg with enableFontRenderer=false (MinFo conflict)");
        } catch (IOException e) {
            System.err.println("[CrossTieCore] Failed to create angelica-modules.cfg: " + e.getMessage());
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
