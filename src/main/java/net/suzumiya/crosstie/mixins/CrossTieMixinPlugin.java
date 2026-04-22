package net.suzumiya.crosstie.mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class CrossTieMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> CLIENT_ONLY_MIXINS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "net.suzumiya.crosstie.mixins.ngtlib.ScriptUtilMixin",
            "net.suzumiya.crosstie.mixins.ngtlib.GLHelperMixin",
            "net.suzumiya.crosstie.mixins.angelica.RenderGlobalInitMixin",
            "net.suzumiya.crosstie.mixins.angelica.RenderGlobalFrameTickMixin",
            "net.suzumiya.crosstie.mixins.angelica.AngelicaGLStateManagerDrawArraysMixin",
            "net.suzumiya.crosstie.mixins.angelica.ThreadedAngelicaChunkBuilderMeshingTaskMixin",
            "net.suzumiya.crosstie.mixins.intelliinput.RedirectWindowProcMixin",
            "net.suzumiya.crosstie.mixins.rtm.RenderVehicleBaseMixin",
            "net.suzumiya.crosstie.mixins.rtm.RTMMiscRenderMixin",
            "net.suzumiya.crosstie.mixins.rtm.RTMRailPartsRenderSafeMixin",
            "net.suzumiya.crosstie.mixins.rtm.RenderSignalElectricMixin",
            "net.suzumiya.crosstie.mixins.rtm.RenderSignalLegacyMixin",
            "net.suzumiya.crosstie.mixins.rtm.RTMWirePartsRenderMixin",
            "net.suzumiya.crosstie.mixins.mcte.RenderMiniatureContextGuardMixin",
            "net.suzumiya.crosstie.mixins.mcte.RenderItemMiniatureContextGuardMixin",
            "net.suzumiya.crosstie.mixins.rtm.TileEntityLargeRailCoreMixin",
            "net.suzumiya.crosstie.mixins.rtm.TileEntitySignalMixin",
            "net.suzumiya.crosstie.mixins.ngtlib.NGTRendererStateMixin",
            "net.suzumiya.crosstie.mixins.ngtlib.PolygonRendererMixin",
            "net.suzumiya.crosstie.mixins.angelica.AngelicaDisplayListManagerMixin")));

    private boolean isClient;
    private boolean hasAngelica;
    private boolean hasIntelliInput;
    private boolean hasRtm;
    private boolean hasBamboo;
    private boolean hasATSAssist;
    private boolean hasMcte;
    private boolean hasElectricRenderSignal;
    private boolean hasLegacyRenderSignal;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;
        hasAngelica = getClass().getClassLoader()
                .getResource("com/gtnewhorizons/angelica/glsm/DisplayListManager.class") != null;
        hasIntelliInput = getClass().getClassLoader()
                .getResource("com/tsoft_web/IntelliInput/RedirectWindowProc.class") != null;
        hasRtm = getClass().getClassLoader().getResource("jp/ngt/rtm/RTMCore.class") != null
                || getClass().getClassLoader().getResource("jp/ngt/ngtlib/io/ScriptUtil.class") != null;
        hasBamboo = getClass().getClassLoader().getResource("ruby/bamboo/BambooCore.class") != null;
        hasATSAssist = getClass().getClassLoader()
                .getResource("jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.class") != null;
        hasMcte = getClass().getClassLoader().getResource("jp/ngt/mcte/block/RenderMiniature.class") != null;
        hasElectricRenderSignal = getClass().getClassLoader().getResource("jp/ngt/rtm/electric/RenderSignal.class") != null;
        hasLegacyRenderSignal = getClass().getClassLoader().getResource("jp/ngt/rtm/render/RenderSignal.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if ("net.suzumiya.crosstie.mixins.angelica.AngelicaDisplayListManagerMixin".equals(mixinClassName)
                || "net.suzumiya.crosstie.mixins.angelica.AngelicaGLStateManagerDrawArraysMixin".equals(mixinClassName)
                || "net.suzumiya.crosstie.mixins.angelica.RenderGlobalInitMixin".equals(mixinClassName)
                || "net.suzumiya.crosstie.mixins.angelica.RenderGlobalFrameTickMixin".equals(mixinClassName)
                || "net.suzumiya.crosstie.mixins.angelica.ThreadedAngelicaChunkBuilderMeshingTaskMixin".equals(mixinClassName)) {
            return isClient && hasAngelica;
        }
        if ("net.suzumiya.crosstie.mixins.intelliinput.RedirectWindowProcMixin".equals(mixinClassName)) {
            return isClient && hasIntelliInput;
        }
        if ("net.suzumiya.crosstie.mixins.rtm.RenderSignalElectricMixin".equals(mixinClassName)) {
            return isClient && hasElectricRenderSignal;
        }
        if ("net.suzumiya.crosstie.mixins.rtm.RenderSignalLegacyMixin".equals(mixinClassName)) {
            return isClient && hasLegacyRenderSignal;
        }

        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            return hasRtm;
        }

        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.ngtlib.")) {
            return hasRtm;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.bamboo.")) {
            return hasBamboo;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.mcte.")) {
            return isClient && hasMcte;
        }
        if ("net.suzumiya.crosstie.mixins.atsassistmod.MixinTileEntityIFTTT".equals(mixinClassName)) {
            // Temporarily disabled to avoid potential behavioral issues with signal detection.
            return false;
        }

        if (!isClient && CLIENT_ONLY_MIXINS.contains(mixinClassName)) {
            return false;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
