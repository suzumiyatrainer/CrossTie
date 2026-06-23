package net.suzumiya.crosstie.mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.suzumiya.crosstie.asm.CrossTieCorePlugin;
import net.suzumiya.crosstie.util.ModDetector;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class CrossTieMixinPlugin implements IMixinConfigPlugin {

    private boolean isClient;
    private ModDetector modDetector;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;

        // Try to get ModDetector from CorePlugin; may be null if injectData() hasn't
        // run yet. will be lazily resolved in shouldApplyMixin().
        modDetector = CrossTieCorePlugin.getModDetector();

        logDetectedCompatMods();
    }

    /**
     * Lazily resolve the ModDetector. If the CorePlugin's detector was not yet
     * available during {@link #onLoad}, retry here since {@code injectData()}
     * will have been called by the time mixins are actually applied.
     */
    /**
     * RenderGlobal.displayList ネイティブ最適化が有効かどうかを返す。
     *
     * <p>システムプロパティが設定されていればそれを優先し、未設定の場合は
     * {@link net.suzumiya.crosstie.CrossTieConfig#enableNativeRenderGlobalDisplayLists} の値を参照する。
     * config は preInit 後に読み込まれるため、それまではデフォルト false となる。
     *
     * @return 有効なら true
     */
    private boolean isNativeRenderGlobalDisplayListsEnabled() {
        // システムプロパティが設定されていれば常にそれを優先
        String prop = System.getProperty("crosstie.enableNativeRenderGlobalDisplayLists");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        // システムプロパティ未設定 → config の値を参照（preInit 前に呼ばれた場合はデフォルト false）
        return net.suzumiya.crosstie.CrossTieConfig.enableNativeRenderGlobalDisplayLists;
    }

    private boolean isModPresent(String modName) {
        if (modDetector == null) {
            modDetector = CrossTieCorePlugin.getModDetector();
        }
        return modDetector != null && modDetector.isModPresent(modName);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean shouldApply;

        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.macros.")) {
            shouldApply = isClient && isModPresent("MacroMod");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.gtnhlib.")) {
            if (mixinClassName.endsWith(".ObjectPoolerThreadSafeMixin")) {
                shouldApply = isModPresent("GTNHLib");
            } else {
                shouldApply = isClient && isModPresent("GTNHLib");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.kaizpatch.")) {
            if (mixinClassName.endsWith(".McteWorldSetBlockDiffMixin")) {
                shouldApply = isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".RenderMiniatureDynamicLightMixin")) {
                shouldApply = isClient && isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".RenderItemMiniatureDynamicLightMixin")) {
                shouldApply = isClient && isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm")
                        && isModPresent("KaizPatch")
                        && isModPresent("NGTScriptUtil");
            } else if (mixinClassName.endsWith(".ModelPackManagerScriptRedirectMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm")
                        && isModPresent("RTM")
                        && isModPresent("NGTScriptUtil");
            } else if (mixinClassName.endsWith(".RailMapCustomCacheMixin")) {
                shouldApply = isModPresent("RailMapCustom");
            } else if (mixinClassName.endsWith(".ScriptUtilInvocableCacheMixin")) {
                shouldApply = isModPresent("NGTScriptUtil");
            } else {
                shouldApply = isModPresent("NGTScriptUtil");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.angelica.")) {
            if (mixinClassName.endsWith(".AngelicaRenderGlobalDisplayListCrashMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm") && isNativeRenderGlobalDisplayListsEnabled();
            } else {
                shouldApply = isClient && isModPresent("AngelicaGlsm");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            shouldApply = isModPresent("RTM");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.liteloader.")) {
            shouldApply = isModPresent("LiteLoader") || isModPresent("MacroMod");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            shouldApply = isClient && isModPresent("GTNHLib");
        } else {
            shouldApply = true;
        }

        System.out.println("[CrossTieMixin] " + mixinClassName + " -> " + (shouldApply ? "APPLY" : "SKIP"));
        return shouldApply;
    }

    private void logDetectedCompatMods() {
        System.out.println("[CrossTieMixin] Detected mods (file-based scan): "
                + "Angelica=" + isModPresent("Angelica")
                + ", AngelicaGLSM=" + isModPresent("AngelicaGlsm")
                + ", ArchaicFix=" + isModPresent("ArchaicFix")
                + ", CoreTweaks=" + isModPresent("CoreTweaks")
                + ", GTNHLib=" + isModPresent("GTNHLib")
                + ", Hodgepodge=" + isModPresent("Hodgepodge")
                + ", UniMixins=" + isModPresent("UniMixins")
                + ", KaizPatch=" + isModPresent("KaizPatch")
                + ", MinFo=" + isModPresent("MinFo")
                + ", MCTE=" + isModPresent("MCTE")
                + ", LiteLoader=" + isModPresent("LiteLoader")
                + ", MacroMod=" + isModPresent("MacroMod")
                + ", nativeRenderGlobalDisplayLists=" + isNativeRenderGlobalDisplayListsEnabled());
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();

        // Angelica
        mixins.add("angelica.AngelicaRenderGlobalDisplayListCrashMixin");

        // FontConfigScreen が Keyboard.enableRepeatEvents(true) を適切に無効化しない問題の修正
        if (isClient && isModPresent("AngelicaGlsm")) {
            mixins.add("angelica.FontConfigScreenFixMixin");
        }

        // enableFontRenderer=false 時のスプラッシュ画面暗転修正。
        // MixinFontRenderer が無効だと SplashFontRenderer の GL 初期化が不完全になり
        // GLStateManager のキャッシュが GL_TEXTURE_2D=false と誤認して画面が真っ黒になる。
        // MinFo の有無に関わらず enableFontRenderer=false であれば暗転するため、
        // 設定ファイルの実際の値を読んで判定する。
        if (isClient && isModPresent("AngelicaGlsm") && !isAngelicaFontRendererEnabled()) {
            mixins.add("angelica.SplashProgressBlackoutFixMixin");
        }

        // GTNHLib - always present
        mixins.add("gtnhlib.ObjectPoolerThreadSafeMixin");
        if (isClient) {
            mixins.add("gtnhlib.MixinBlockPaneFix");
        }

        // KaizPatch / NGTScriptUtil
        if (isModPresent("NGTScriptUtil")) {
            mixins.add("kaizpatch.ScriptUtilInvocableCacheMixin");
            if (isClient && isModPresent("AngelicaGlsm") && isModPresent("KaizPatch")) {
                mixins.add("kaizpatch.AngelicaScriptTransformCacheMixin");
                if (isModPresent("RTM")) {
                    mixins.add("kaizpatch.ModelPackManagerScriptRedirectMixin");
                }
            }
        }
        if (isModPresent("MCTE")) {
            mixins.add("kaizpatch.McteWorldSetBlockDiffMixin");
        }
        if (isModPresent("RailMapCustom")) {
            mixins.add("kaizpatch.RailMapCustomCacheMixin");
        }

        // RTM
        if (isModPresent("RTM")) {
            mixins.add("rtm.EntityTrainBaseSpeedSyncMixin");
            mixins.add("rtm.EntityTrainBaseOptimizationMixin");
        }

        // LiteLoader / Macro / Keybind Mod
        if (isModPresent("LiteLoader") || isModPresent("MacroMod")) {
            mixins.add("liteloader.MixinPermissionsManagerClient");
        }

        // Client-side mixins
        if (isClient) {
            if (isModPresent("MacroMod")) {
                // MacroInputHandlerMixin は update() を全キャンセルするため削除
                // mixins.add("macros.MacroInputHandlerMixin"); ← 絶対に追加しない
                mixins.add("macros.MacroModCoreMixin");
            }

            // MCTE client
            if (isModPresent("MCTE")) {
                mixins.add("kaizpatch.RenderMiniatureDynamicLightMixin");
                mixins.add("kaizpatch.RenderItemMiniatureDynamicLightMixin");
            }

            // RTM client
            if (isModPresent("RTM")) {
                mixins.add("rtm.RenderElectricalWiringConnectionCacheMixin");
                mixins.add("rtm.BlockLinePoleConnectionCacheMixin");
                mixins.add("rtm.RenderLargeRailOptimizationMixin");
                mixins.add("rtm.RenderLargeRailChunkBatchMixin");
                mixins.add("rtm.RTMRailTESRThrottleMixin");
                mixins.add("rtm.RailTessellateOptimizationMixin");
                mixins.add("rtm.TileEntitySignalNoCullingMixin");
                mixins.add("rtm.TileEntityCrossingGateNoCullingMixin");
            }

            // GTNHLib client icons
            if (isModPresent("GTNHLib")) {
                mixins.add("gtnhlib.MixinBlockPaneIconFallback");
                mixins.add("gtnhlib.MixinBlockIconFallback");
            }
        }

        System.out.println("[CrossTieMixin] Dynamic mixin count: " + mixins.size() + " / "
                + (isClient ? "CLIENT" : "SERVER"));
        return mixins;
    }

    /**
     * Angelica の {@code enableFontRenderer} 設定が有効かどうかを判定する。
     *
     * <p>
     * {@code getMixins()} 呼び出し時点では {@code injectData()} が完了しており
     * {@code CrossTieCorePlugin.getMcDataDir()} で実行ディレクトリが取得できる。
     * 設定ファイル({@code config/angelica-modules.cfg})を直接読んで判定する。
     * ファイルが存在しない、または読み取れない場合は Angelica のデフォルト値({@code true})を返す。
     */
    private boolean isAngelicaFontRendererEnabled() {
        java.io.File mcDataDir = CrossTieCorePlugin.getMcDataDir();
        if (mcDataDir == null) {
            return true; // 判定不能の場合はデフォルト(有効)扱い
        }
        java.io.File configFile = new java.io.File(mcDataDir, "config/angelica-modules.cfg");
        if (!configFile.exists()) {
            return true; // ファイルなし = Angelica デフォルト = 有効
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(configFile.toPath())) {
                String trimmed = line.trim();
                if (trimmed.startsWith("B:enableFontRenderer=")) {
                    return !"false".equals(trimmed.substring("B:enableFontRenderer=".length()).trim());
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("[CrossTieMixin] Failed to read angelica-modules.cfg: " + e.getMessage());
        }
        return true; // 該当行なし = デフォルト = 有効
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}