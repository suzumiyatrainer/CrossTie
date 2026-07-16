package net.suzumiya.crosstie.mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.suzumiya.crosstie.asm.CrossTieCorePlugin;
import net.suzumiya.crosstie.utils.ModDetector;
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

        // Prevent Nashorn compiled classes from going through LaunchClassLoader's ASM
        // transformers
        try {
            if (net.minecraft.launchwrapper.Launch.classLoader != null) {
                Object classLoader = net.minecraft.launchwrapper.Launch.classLoader;
                java.lang.reflect.Method regTransEx = classLoader.getClass().getMethod("registerTransformerException",
                        String.class);

                regTransEx.invoke(classLoader, "jdk.nashorn.internal.");
                regTransEx.invoke(classLoader, "jdk.nashorn.api.scripting.");
                regTransEx.invoke(classLoader, "org.openjdk.nashorn.");
                System.out.println(
                        "[CrossTie] Registered Nashorn transformer exceptions in LaunchClassLoader via reflection");
            }
        } catch (Throwable t) {
            System.err.println(
                    "[CrossTie] Failed to register Nashorn exceptions in LaunchClassLoader: " + t.getMessage());
        }
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
            } else if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                shouldApply = isClient && hasAngelicaGlsm && hasKaizPatch && hasNgtScriptUtil;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm + ", KaizPatch="
                        + hasKaizPatch + ", NGTScriptUtil=" + hasNgtScriptUtil;
            } else if (mixinClassName.endsWith(".ModelPackManagerScriptRedirectMixin")) {
                shouldApply = isClient && hasAngelicaGlsm && hasRtm && hasNgtScriptUtil;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm + ", RTM=" + hasRtm
                        + ", NGTScriptUtil=" + hasNgtScriptUtil;
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
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.mcte.")) {
            boolean hasMcte = isModPresent("MCTE");
            boolean hasAngelicaGlsm = isModPresent("AngelicaGlsm");
            if (mixinClassName.endsWith(".RenderMiniatureOptifineLightMixin") || mixinClassName.endsWith(".RenderItemMiniatureOptifineLightMixin")) {
                shouldApply = isClient && hasMcte && !hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", MCTE=" + hasMcte + ", !AngelicaGlsm=" + (!hasAngelicaGlsm);
            /*
            } else if (mixinClassName.endsWith(".RenderMiniatureAngelicaLightMixin") || mixinClassName.endsWith(".RenderItemMiniatureAngelicaLightMixin")) {
                shouldApply = isClient && hasMcte && hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", MCTE=" + hasMcte + ", AngelicaGlsm=" + hasAngelicaGlsm;
            */
            } else {
                shouldApply = isClient && hasMcte;
                debugReason = "isClient=" + isClient + ", MCTE=" + hasMcte;
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
            if (mixinClassName.endsWith(".PolygonRendererMixin")) {
                shouldApply = isClient && hasAngelicaGlsm;
                debugReason = "isClient=" + isClient + ", AngelicaGlsm=" + hasAngelicaGlsm;
            } else if (mixinClassName.endsWith(".EntityVehicleBaseModelSetGuardMixin")
                    || mixinClassName.endsWith(".RenderVehicleBaseContextMixin")
                    || mixinClassName.endsWith(".PartsRendererCheckMouseActionGuardMixin")
                    || mixinClassName.endsWith(".PartsRendererPickPassGuardMixin")) {
                // ホバー時モデルデータ混線防止: クライアントかつRTM存在時のみ適用
                shouldApply = isClient && hasRtm;
                debugReason = "isClient=" + isClient + ", RTM=" + hasRtm;
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
            if (mixinClassName.endsWith(".RailBrightnessDisplayListSafeMixin")
                    || mixinClassName.endsWith(".WireShadowPassRenderMixin")
                    || mixinClassName.endsWith(".WireNormalizeShaderFixMixin")
                    || mixinClassName.endsWith(".WireColorShaderFixMixin")) {
                shouldApply = (hasOptiFine || hasFastCraft) && !hasAngelicaGlsm;
                debugReason = "OptiFine=" + hasOptiFine + ", FastCraft=" + hasFastCraft + ", AngelicaGlsm="
                        + hasAngelicaGlsm;
            } else {
                shouldApply = (hasOptiFine || hasFastCraft) && !hasAngelicaGlsm;
                debugReason = "OptiFine=" + hasOptiFine + ", FastCraft=" + hasFastCraft + ", AngelicaGlsm="
                        + hasAngelicaGlsm;
            }
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
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.customnpc.")) {
            boolean hasCustomNpc = isModPresent("CustomNpc");
            shouldApply = hasCustomNpc;
            debugReason = "CustomNpc=" + hasCustomNpc;
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
                mixins.add("rtm.RailTessellateOptimizationMixin");
                mixins.add("rtm.RenderMarkerBlockBaseMixin");
                mixins.add("rtm.NGTTessellatorMixin");
                mixins.add("rtm.TileEntitySignalNoCullingMixin");
                mixins.add("rtm.TileEntityCrossingGateNoCullingMixin");
                mixins.add("rtm.RenderEntityInstalledObjectCullingMixin");
                mixins.add("rtm.GuiSelectTexturePagingMixin");
                mixins.add("rtm.RtmPartsMatrixPushMixin");
                mixins.add("rtm.BasicVehiclePartsRendererMixin");

                // GL_SELECT (マウスピッキング) 回避パッチ
                //
                // 各Mixin実装のコメントが示す通り、GL_SELECTが正常に機能しない問題は
                // Angelica(GLSMによるGL_SELECT系APIのスタブ化)だけでなく、
                // OptiFine/FastCraft(シェーダー・高速化パイプラインがGL_SELECTパスを無視/破壊する)
                // でも同様に発生する。
                // 従来はAngelicaGlsm限定で適用されており、OptiFine単体環境では
                // 1.12.2マーカーのアンカー線やbasicwireのピッキング判定描画が
                // 崩れる問題が修正されないまま残っていたため、適用条件を拡張する。
                boolean hasAngelicaGlsmForPicking = isModPresent("AngelicaGlsm");
                boolean hasOptiFineForPicking = (isModPresent("OptiFine") || isModPresent("FastCraft"))
                        && !hasAngelicaGlsmForPicking;

                if (hasAngelicaGlsmForPicking || hasOptiFineForPicking) {
                    mixins.add("ngtlib.InternalButtonAccessor");
                    mixins.add("ngtlib.InternalGUIMathPickingMixin");
                    mixins.add("ngtlib.NGTTessellatorSelectModeFixMixin");
                    mixins.add("ngtlib.RenderMarkerBlock1122MathPickingMixin");

                    // GLHelperSelectBypassMixin / ActionPartsLoadNameMixin は
                    // Angelica(GLSM)がglRenderMode/glLoadNameをバイトコード置換で
                    // スタブ化していることへの回避策であり、TrueGL経由でリフレクション
                    // 呼び出しに切り替える。OptiFine単体ではこれらのAPIはスタブ化
                    // されておらず素通りするだけなので、常に併用しても副作用はない。
                    // 加えて、TrueGL.glRenderMode() を通すことで
                    // NGTTessellatorSelectModeFixMixin/RenderMarkerBlock1122MathPickingMixin
                    // が判定に用いる TrueGL.isSelectMode() フラグが両環境で正しく
                    // 更新されるようになる（Angelica限定適用のままだとOptiFine単体では
                    // このフラグが常にfalseのままになり、上記2つのMathPicking系Mixinが
                    // 事実上無効化されてしまう）。
                    mixins.add("ngtlib.GLHelperSelectBypassMixin");
                    mixins.add("ngtlib.ActionPartsLoadNameMixin");
                }
            }

            // MCTE client
            if (isModPresent("MCTE")) {
                // mixins.add("mcte.RenderMiniatureAngelicaLightMixin");
                // mixins.add("mcte.RenderItemMiniatureAngelicaLightMixin");
                mixins.add("mcte.RenderMiniatureOptifineLightMixin");
                mixins.add("mcte.RenderItemMiniatureOptifineLightMixin");
            }
        }

        // RTM サーバー側 Mixin（クライアント・サーバー共通）
        if (isModPresent("RTM")) {
            mixins.add("rtm.ElectricalWiringDecorativeMixin");
            mixins.add("rtm.EntityTrainDetectorThrottleMixin");
        }

        // WorldEdit
        if (isModPresent("WorldEdit")) {
            mixins.add("worldedit.MixinBaseBlock");
            mixins.add("worldedit.MixinSchematicWriter");
            mixins.add("worldedit.MixinSchematicReader");
        }

        // ProjectRed mixins are handled in CrossTieLateMixinLoader to prevent early
        // classloading crashes

        System.out.println(
                "[CrossTieMixin] Dynamic mixin count: " + mixins.size() + " / " + (isClient ? "CLIENT" : "SERVER"));
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}