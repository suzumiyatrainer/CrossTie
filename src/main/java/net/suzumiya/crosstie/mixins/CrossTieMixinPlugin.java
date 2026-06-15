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
    private boolean enableNativeRenderGlobalDisplayLists;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;
        enableNativeRenderGlobalDisplayLists = Boolean.getBoolean("crosstie.enableNativeRenderGlobalDisplayLists");

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
            if (mixinClassName.endsWith(".MixinBlockPaneFix")) {
                shouldApply = isClient && isModPresent("GTNHLib");
            } else {
                shouldApply = isModPresent("GTNHLib");
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
                shouldApply = isClient && isModPresent("AngelicaGlsm") && enableNativeRenderGlobalDisplayLists;
            } else {
                shouldApply = isClient && isModPresent("AngelicaGlsm");
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            shouldApply = isModPresent("RTM");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.liteloader.")) {
            shouldApply = isModPresent("LiteLoader");
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            // MixinGuiScreenBackgroundFix: apply when font renderer is force-disabled due
            // to MinFo
            if (mixinClassName.endsWith(".MixinGuiScreenBackgroundFix")) {
                shouldApply = isClient && isModPresent("GTNHLib") && isModPresent("MinFo");
            } else {
                shouldApply = isClient && isModPresent("GTNHLib");
            }
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
                + ", nativeRenderGlobalDisplayLists=" + enableNativeRenderGlobalDisplayLists);
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

        // LiteLoader
        if (isModPresent("LiteLoader")) {
            mixins.add("liteloader.MixinPermissionsManagerClient");
        }

        // Client-side mixins
        if (isClient) {
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
                mixins.add("rtm.RailPartsRendererOptimizationMixin");
            }

            // MacroMod
            if (isModPresent("MacroMod")) {
                mixins.add("macros.MacroInputHandlerMixin");
                mixins.add("macros.MacroModCoreMixin");
                mixins.add("macros.MacroModPermissionsMixin");
            }

            // GTNHLib client icons
            if (isModPresent("GTNHLib")) {
                mixins.add("gtnhlib.MixinBlockPaneIconFallback");
                mixins.add("gtnhlib.MixinTripWireHookIconFallback");
                mixins.add("gtnhlib.MixinRedstoneBlockIconFallback");
                mixins.add("gtnhlib.MixinTripWireIconFallback");
                mixins.add("gtnhlib.MixinBedIconFallback");

                // MinFo (background fix)
                if (isModPresent("MinFo")) {
                    mixins.add("minecraft.MixinGuiScreenBackgroundFix");
                }
            }
        }

        System.out.println("[CrossTieMixin] Dynamic mixin count: " + mixins.size() + " / "
                + (isClient ? "CLIENT" : "SERVER"));
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}