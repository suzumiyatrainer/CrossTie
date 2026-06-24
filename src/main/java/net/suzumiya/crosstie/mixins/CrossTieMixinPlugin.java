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
     * <p>
     * システムプロパティが設定されていればそれを優先し、未設定の場合は
     * {@link net.suzumiya.crosstie.CrossTieConfig#enableNativeRenderGlobalDisplayLists}
     * の値を参照する。
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
        String debugReason = "";

        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.macros.")) {
            shouldApply = isClient && isModPresent("MacroMod");
            debugReason = "isClient=" + isClient + ", MacroMod=" + isModPresent("MacroMod");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.gtnhlib.")) {
            if (mixinClassName.endsWith(".ObjectPoolerThreadSafeMixin")) {
                shouldApply = isModPresent("GTNHLib");
                debugReason = "GTNHLib=" + isModPresent("GTNHLib");
            } else {
                shouldApply = isClient && isModPresent("GTNHLib");
                debugReason = "isClient=" + isClient + ", GTNHLib=" + isModPresent("GTNHLib");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.kaizpatch.")) {
            if (mixinClassName.endsWith(".McteWorldSetBlockDiffMixin")) {
                shouldApply = isModPresent("MCTE");
                debugReason = "MCTE=" + isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".RenderMiniatureDynamicLightMixin")) {
                shouldApply = isClient && isModPresent("MCTE");
                debugReason = "isClient=" + isClient + ", MCTE=" + isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".RenderItemMiniatureDynamicLightMixin")) {
                shouldApply = isClient && isModPresent("MCTE");
                debugReason = "isClient=" + isClient + ", MCTE=" + isModPresent("MCTE");
            } else if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm")
                        && isModPresent("KaizPatch")
                        && isModPresent("NGTScriptUtil");
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + isModPresent("AngelicaGlsm")
                        + ", KaizPatch=" + isModPresent("KaizPatch") + ", NGTScriptUtil="
                        + isModPresent("NGTScriptUtil");
            } else if (mixinClassName.endsWith(".ModelPackManagerScriptRedirectMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm")
                        && isModPresent("RTM")
                        && isModPresent("NGTScriptUtil");
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + isModPresent("AngelicaGlsm")
                        + ", RTM=" + isModPresent("RTM") + ", NGTScriptUtil=" + isModPresent("NGTScriptUtil");
            } else if (mixinClassName.endsWith(".RailMapCustomCacheMixin")) {
                shouldApply = isModPresent("RailMapCustom");
                debugReason = "RailMapCustom=" + isModPresent("RailMapCustom");
            } else if (mixinClassName.endsWith(".ScriptUtilInvocableCacheMixin")) {
                shouldApply = isModPresent("NGTScriptUtil");
                debugReason = "NGTScriptUtil=" + isModPresent("NGTScriptUtil");
            } else {
                shouldApply = isModPresent("NGTScriptUtil");
                debugReason = "NGTScriptUtil=" + isModPresent("NGTScriptUtil");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.angelica.")) {
            if (mixinClassName.endsWith(".AngelicaRenderGlobalDisplayListCrashMixin")) {
                shouldApply = isClient && isModPresent("AngelicaGlsm") && isNativeRenderGlobalDisplayListsEnabled();
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + isModPresent("AngelicaGlsm")
                        + ", nativeLists=" + isNativeRenderGlobalDisplayListsEnabled();
            } else {
                shouldApply = isClient && isModPresent("AngelicaGlsm");
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + isModPresent("AngelicaGlsm");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            shouldApply = isModPresent("RTM");
            debugReason = "RTM=" + isModPresent("RTM");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.liteloader.")) {
            shouldApply = isModPresent("LiteLoader") || isModPresent("MacroMod");
            debugReason = "LiteLoader=" + isModPresent("LiteLoader") + ", MacroMod=" + isModPresent("MacroMod");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            shouldApply = isClient && isModPresent("GTNHLib");
            debugReason = "isClient=" + isClient + ", GTNHLib=" + isModPresent("GTNHLib");
        } else {
            shouldApply = true;
            debugReason = "default=true";
        }

        System.out.println("[CrossTieMixin] " + mixinClassName + " -> " + (shouldApply ? "APPLY" : "SKIP") + " ("
                + debugReason + ")");
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
                mixins.add("rtm.GuiSelectTexturePagingMixin");
            }

            // GTNHLib client icons
            if (isModPresent("GTNHLib")) {
                mixins.add("gtnhlib.MixinBlockPaneIconFallback");
                mixins.add("gtnhlib.MixinBlockIconFallback");
            }

            // Angelica splash screen blackout fix
            if (isModPresent("AngelicaGlsm")) {
                if (!isAngelicaFontRendererEnabled()) {
                    System.out.println(
                            "[CrossTieMixin] Angelica FontRenderer is disabled. Applying Splash Screen Blackout Fix.");
                    mixins.add("splash.MixinSplashProgressMatrixSync");
                    mixins.add("splash.MixinSplashProgressRunnableSync");
                }
            }
        }

        System.out.println("[CrossTieMixin] Dynamic mixin count: " + mixins.size() + " / "
                + (isClient ? "CLIENT" : "SERVER"));
        return mixins;
    }

    /**
     * Angelica の enableFontRenderer 設定が有効かどうかを判定する。
     */
    private boolean isAngelicaFontRendererEnabled() {
        java.io.File mcDataDir = CrossTieCorePlugin.getMcDataDir();
        if (mcDataDir == null) {
            // CorePluginから取得できない場合はカレントディレクトリ(.minecraft)をフォールバックに
            mcDataDir = new java.io.File(".");
        }

        java.io.File configFile = new java.io.File(mcDataDir, "config/angelica-modules.cfg");
        if (!configFile.exists()) {
            return true; // ファイルがない場合はAngelicaのデフォルト値（有効）とみなす
        }

        try {
            for (String line : java.nio.file.Files.readAllLines(configFile.toPath())) {
                String trimmed = line.trim();
                // コメントアウトされている行をスキップ
                if (trimmed.startsWith("#")) {
                    continue;
                }

                // 設定行のパースを堅牢にするため、全ての空白文字（スペースやインデント）を除去
                String cleanLine = line.replaceAll("\\s+", "");

                if (cleanLine.startsWith("B:enableFontRenderer=")) {
                    // "B:enableFontRenderer=false" と完全一致する場合のみ false と判定
                    return !cleanLine.equals("B:enableFontRenderer=false");
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("[CrossTieMixin] Failed to read angelica-modules.cfg: " + e.getMessage());
        }

        // 設定項目自体が見つからない場合はデフォルトの true
        return true;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}