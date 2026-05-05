package net.suzumiya.crosstie.mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class CrossTieMixinPlugin implements IMixinConfigPlugin {

    private boolean isClient;
    private boolean hasMacroMod;
    private boolean hasGtnhLib;
    private boolean hasSignalController;
    private boolean hasWebCtc;
    private boolean hasAts;
    private boolean hasRtm;
    private boolean hasNgtScriptUtil;
    private boolean hasMcte;
    private boolean hasKaizAngelicaCompat;
    private boolean hasRailMapCustom;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;
        hasMacroMod = getClass().getClassLoader().getResource("net/eq2online/macros/input/InputHandler.class") != null;
        hasGtnhLib = getClass().getClassLoader().getResource("com/gtnewhorizon/gtnhlib/util/ObjectPooler.class") != null;
        hasSignalController = getClass().getClassLoader()
                .getResource("jp/masa/signalcontrollermod/block/tileentity/TileEntitySignalController.class") != null;
        hasWebCtc = getClass().getClassLoader().getResource("org/webctc/railgroup/RailGroupUtilsKt.class") != null;
        hasAts = getClass().getClassLoader()
                .getResource("jp/kaiz/atsassistmod/block/tileentity/TileEntityCustom.class") != null;
        hasRtm = getClass().getClassLoader().getResource("jp/ngt/rtm/entity/train/EntityTrainBase.class") != null;
        hasNgtScriptUtil = getClass().getClassLoader().getResource("jp/ngt/ngtlib/io/ScriptUtil.class") != null;
        hasMcte = getClass().getClassLoader().getResource("jp/ngt/mcte/world/MCTEWorld.class") != null;
        hasKaizAngelicaCompat = getClass().getClassLoader()
                .getResource("jp/kaiz/kaizpatch/compat/AngelicaCompat.class") != null;
        hasRailMapCustom = getClass().getClassLoader().getResource("jp/ngt/rtm/rail/util/RailMapCustom.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.macros.")) {
            return isClient && hasMacroMod;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.gtnhlib.")) {
            if (mixinClassName.endsWith(".MixinBlockPaneFix")) {
                return isClient && hasGtnhLib;
            }
            return hasGtnhLib;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.kaizpatch.")) {
            if (mixinClassName.endsWith(".McteWorldSetBlockDiffMixin")) {
                return hasMcte;
            }
            if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                return isClient && hasKaizAngelicaCompat;
            }
            if (mixinClassName.endsWith(".RailMapCustomCacheMixin")) {
                return hasRailMapCustom;
            }
            return hasNgtScriptUtil;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.signal.")) {
            return hasSignalController;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.webctc.")) {
            return hasWebCtc;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.ats.")) {
            return hasAts;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            return hasRtm;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            return isClient && hasGtnhLib;
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
