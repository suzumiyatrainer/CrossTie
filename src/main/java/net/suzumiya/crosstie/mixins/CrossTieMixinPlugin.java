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
     * available during {@link #onLoad}, retry here since {@code injectData()} will
     * have been called by the time mixins are actually applied.
     */
    /**
     * RenderGlobal.displayList ネイティブ最適化が有効かどうかを返す。
     *
     * <p>
     * システムプロパティが設定されていればそれを優先し、未設定の場合は
     * {@link net.suzumiya.crosstie.CrossTieConfig#enableNativeRenderGlobalDisplayLists}
     * の値を参照する。 config は preInit 後に読み込まれるため、それまではデフォルト false となる。
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
            if (modDetector == null) {
                // Initialize manually if injectData hasn't run yet, relying on jar location
                // fallback
                modDetector = new ModDetector(null);
            }
        }
        return modDetector.isModPresent(modName);
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
            boolean hasMacroMod = isModPresent("MacroMod");
            shouldApply = isClient && hasMacroMod;
            debugReason = "isClient=" + isClient + ", MacroMod=" + hasMacroMod;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.gtnhlib.")) {
            boolean hasGtnhLib = isModPresent("GTNHLib");
            if (mixinClassName.endsWith(".ObjectPoolerThreadSafeMixin")) {
                shouldApply = hasGtnhLib;
                debugReason = "GTNHLib=" + hasGtnhLib;
            } else {
                shouldApply = isClient && hasGtnhLib;
                debugReason = "isClient=" + isClient + ", GTNHLib=" + hasGtnhLib;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.kaizpatch.")) {
            boolean hasMcte = isModPresent("MCTE");
            boolean hasAngelicaGlsm = isModPresent("AngelicaGlsm");
            boolean hasKaizPatch = isModPresent("KaizPatch");
            boolean hasNgtScriptUtil = isModPresent("NGTScriptUtil");
            boolean hasRtm = isModPresent("RTM");
            boolean hasRailMapCustom = isModPresent("RailMapCustom");
            
            if (mixinClassName.endsWith(".McteWorldSetBlockDiffMixin")) {
                shouldApply = hasMcte;
                debugReason = "MCTE=" + hasMcte;
            } else if (mixinClassName.endsWith(".RenderMiniatureDynamicLightMixin")) {
                shouldApply = isClient && hasMcte;
                debugReason = "isClient=" + isClient + ", MCTE=" + hasMcte;
            } else if (mixinClassName.endsWith(".RenderItemMiniatureDynamicLightMixin")) {
                shouldApply = isClient && hasMcte;
                debugReason = "isClient=" + isClient + ", MCTE=" + hasMcte;
            } else if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                shouldApply = isClient && hasAngelicaGlsm && hasKaizPatch && hasNgtScriptUtil;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm + ", KaizPatch="
                        + hasKaizPatch + ", NGTScriptUtil=" + hasNgtScriptUtil;
            } else if (mixinClassName.endsWith(".ModelPackManagerScriptRedirectMixin")) {
                shouldApply = isClient && hasAngelicaGlsm && hasRtm && hasNgtScriptUtil;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm + ", RTM="
                        + hasRtm + ", NGTScriptUtil=" + hasNgtScriptUtil;
            } else if (mixinClassName.endsWith(".RailMapCustomCacheMixin")) {
                shouldApply = hasRailMapCustom;
                debugReason = "RailMapCustom=" + hasRailMapCustom;
            } else if (mixinClassName.endsWith(".ScriptUtilInvocableCacheMixin")) {
                shouldApply = hasNgtScriptUtil;
                debugReason = "NGTScriptUtil=" + hasNgtScriptUtil;
            } else if (mixinClassName.endsWith(".ModelLoaderKtFallbackMixin")) {
                shouldApply = hasKaizPatch;
                debugReason = "KaizPatch=" + hasKaizPatch;
            } else {
                shouldApply = hasNgtScriptUtil;
                debugReason = "NGTScriptUtil=" + hasNgtScriptUtil;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.angelica.")) {
            boolean hasAngelicaGlsm = isModPresent("AngelicaGlsm");
            boolean hasAngelica = isModPresent("Angelica");
            boolean hasAnyAngelica = hasAngelica || hasAngelicaGlsm;
            
            if (mixinClassName.endsWith(".AngelicaRenderGlobalDisplayListCrashMixin")) {
                shouldApply = isClient && hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm
                        + ", nativeLists=forced_for_rtm_parts";
            } else if (mixinClassName.endsWith(".IdMapOrderFixMixin")
                    || mixinClassName.endsWith(".AngelicaBlockMaterialMappingCaseFixMixin")
                    || mixinClassName.endsWith(".AngelicaItemMaterialHelperCaseFixMixin")) {
                shouldApply = isClient && hasAnyAngelica;
                debugReason = "isClient=" + isClient + ", Angelica(GLSM)?=" + hasAnyAngelica;
            } else {
                shouldApply = isClient && hasAnyAngelica;
                debugReason = "isClient=" + isClient + ", Angelica(GLSM)?=" + hasAnyAngelica;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            boolean hasAngelicaGlsm = isModPresent("AngelicaGlsm");
            boolean hasRtm = isModPresent("RTM");
            if (mixinClassName.endsWith(".RtmPartsDisplayListBypassMixin")) {
                shouldApply = isClient && hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm;
            } else if (mixinClassName.endsWith(".PolygonRendererMixin")) {
                shouldApply = isClient && hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm;
            } else {
                shouldApply = hasRtm;
                debugReason = "RTM=" + hasRtm;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.liteloader.")) {
            boolean hasLiteLoader = isModPresent("LiteLoader");
            boolean hasMacroMod = isModPresent("MacroMod");
            shouldApply = hasLiteLoader || hasMacroMod;
            debugReason = "LiteLoader=" + hasLiteLoader + ", MacroMod=" + hasMacroMod;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.optifine.")) {
            boolean hasOptiFine = isModPresent("OptiFine");
            boolean hasFastCraft = isModPresent("FastCraft");
            boolean hasAngelicaGlsm = isModPresent("AngelicaGlsm");
            // Angelicaがある場合は絶対に適用しない（二重安全網）
            shouldApply = (hasOptiFine || hasFastCraft) && !hasAngelicaGlsm;
            debugReason = "OptiFine=" + hasOptiFine + ", FastCraft=" + hasFastCraft
                    + ", AngelicaGlsm=" + hasAngelicaGlsm;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            boolean hasGtnhLib = isModPresent("GTNHLib");
            shouldApply = isClient && hasGtnhLib;
            debugReason = "isClient=" + isClient + ", GTNHLib=" + hasGtnhLib;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.worldedit.")) {
            boolean hasWorldEdit = isModPresent("WorldEdit");
            shouldApply = hasWorldEdit;
            debugReason = "WorldEdit=" + hasWorldEdit;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.projectred.")) {
            boolean hasProjectRed = isModPresent("ProjectRed");
            shouldApply = hasProjectRed;
            debugReason = "ProjectRed=" + hasProjectRed;
        } else {
            shouldApply = true;
            debugReason = "default=true";
        }

        System.out.println("[CrossTieMixin] " + mixinClassName + " -> " + (shouldApply ? "APPLY" : "SKIP") + " ("
                + debugReason + ")");
        return shouldApply;
    }

    private void logDetectedCompatMods() {
        System.out.println("[CrossTieMixin] Detected mods (file-based scan): " + "Angelica=" + isModPresent("Angelica")
                + ", AngelicaGLSM=" + isModPresent("AngelicaGlsm") + ", ArchaicFix=" + isModPresent("ArchaicFix")
                + ", CoreTweaks=" + isModPresent("CoreTweaks") + ", GTNHLib=" + isModPresent("GTNHLib")
                + ", Hodgepodge=" + isModPresent("Hodgepodge") + ", UniMixins=" + isModPresent("UniMixins")
                + ", KaizPatch=" + isModPresent("KaizPatch") + ", MinFo=" + isModPresent("MinFo") + ", MCTE="
                + isModPresent("MCTE") + ", LiteLoader=" + isModPresent("LiteLoader") + ", MacroMod="
                + isModPresent("MacroMod") + ", OptiFine=" + isModPresent("OptiFine") + ", FastCraft="
                + isModPresent("FastCraft") + ", nativeRenderGlobalDisplayLists="
                + isNativeRenderGlobalDisplayListsEnabled());
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();

        // GTNHLib - always present
        mixins.add("gtnhlib.ObjectPoolerThreadSafeMixin");
        if (isClient) {
            mixins.add("gtnhlib.MixinBlockPaneFix");
        }

        // RTM
        if (isModPresent("RTM")) {
            mixins.add("rtm.EntityTrainBaseSpeedSyncMixin");
            mixins.add("rtm.EntityTrainBaseOptimizationMixin");
            mixins.add("rtm.MixinSoundAPIEntityTrainBase");
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
        
        if (isModPresent("KaizPatch")) {
            mixins.add("kaizpatch.ModelLoaderKtFallbackMixin");
        }

        if (isModPresent("RailMapCustom")) {
            mixins.add("kaizpatch.RailMapCustomCacheMixin");
        }

        if (isModPresent("MCTE")) {
            mixins.add("kaizpatch.McteWorldSetBlockDiffMixin");
        }

        // LiteLoader / Macro / Keybind Mod
        if (isModPresent("LiteLoader") || isModPresent("MacroMod")) {
            mixins.add("liteloader.MixinPermissionsManagerClient");
        }

        // Client-side mixins
        if (isClient) {
            // OptiFine / FastCraft - LargeRail brightness fix (Angelicaがある場合は追加しない)
            if ((isModPresent("OptiFine") || isModPresent("FastCraft")) && !isModPresent("AngelicaGlsm")) {
                mixins.add("optifine.RailBrightnessDisplayListSafeMixin");
                mixins.add("optifine.WireShadowPassRenderMixin");
                mixins.add("optifine.WireNormalizeShaderFixMixin");
                mixins.add("optifine.WireColorShaderFixMixin");
            }

            // Angelica
            if (isModPresent("AngelicaGlsm")) {
                mixins.add("angelica.AngelicaRenderGlobalDisplayListCrashMixin");
            }

            // Angelica splash screen blackout fix
            if (isModPresent("AngelicaGlsm")) {
                if (!isAngelicaFontRendererEnabled()) {
                    System.out.println(
                            "[CrossTieMixin] Angelica FontRenderer is disabled. Applying Splash Screen Blackout Fix.");
                    mixins.add("splash.MixinSplashProgressEarlyGLStateManagerLoad");
                    mixins.add("splash.MixinGLStateManagerFallbackDraw");
                }
            }

            // GTNHLib client icons
            if (isModPresent("GTNHLib")) {
                mixins.add("gtnhlib.MixinBlockPaneIconFallback");
                mixins.add("gtnhlib.MixinBlockIconFallback");
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
                mixins.add("rtm.RtmPartsMatrixPushMixin");
                mixins.add("rtm.BasicVehiclePartsRendererMixin");

                // 1.12.2マーカーUIのマウスピッキング修正:
                // AngelicaやOptiFine環境ではGL_SELECTモードが無視されて画面に描画されてしまうため、
                // ピッキング中の描画はカラー/深度バッファへの書き込みをマスクして防ぐ。
                if (isModPresent("AngelicaGlsm") || isModPresent("OptiFine") || isModPresent("FastCraft")) {
                    // mixins.add("ngtlib.GLHelperMousePickingFixMixin");
                    // mixins.add("ngtlib.NGTTessellatorSelectModeFixMixin");
                }
            }

            // MCTE client
            if (isModPresent("MCTE")) {
                mixins.add("kaizpatch.RenderMiniatureDynamicLightMixin");
                mixins.add("kaizpatch.RenderItemMiniatureDynamicLightMixin");
            }
        }

        // WorldEdit
        if (isModPresent("WorldEdit")) {
            mixins.add("worldedit.MixinBaseBlock");
            mixins.add("worldedit.MixinSchematicWriter");
            mixins.add("worldedit.MixinSchematicReader");
        }

        // ProjectRed mixins are handled in CrossTieLateMixinLoader to prevent early classloading crashes

        System.out.println(
                "[CrossTieMixin] Dynamic mixin count: " + mixins.size() + " / " + (isClient ? "CLIENT" : "SERVER"));
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