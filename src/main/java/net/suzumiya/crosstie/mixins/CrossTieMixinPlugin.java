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
    private boolean hasAngelica;
    private boolean hasArchaicFix;
    private boolean hasCoreTweaks;
    private boolean hasGtnhLib;
    private boolean hasHodgepodge;
    private boolean hasUniMixins;
    private boolean hasSignalController;
    private boolean hasWebCtc;
    private boolean hasAts;
    private boolean hasRtm;
    private boolean hasNgtScriptUtil;
    private boolean hasMcte;
    private boolean hasKaizAngelicaCompat;
    private boolean hasRailMapCustom;
    private boolean hasAngelicaGlsm;
    private boolean enableNativeRenderGlobalDisplayLists;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;
        enableNativeRenderGlobalDisplayLists =
                Boolean.getBoolean("crosstie.enableNativeRenderGlobalDisplayLists");
        
        // 安全なクラス存在チェック
        hasMacroMod = checkClassExists("net.eq2online.macros.input.InputHandler");
        hasAngelica = checkClassExists("com.gtnewhorizons.angelica.AngelicaMod");
        hasArchaicFix = checkClassExists("org.embeddedt.archaicfix.ArchaicFix");
        hasCoreTweaks = checkClassExists("makamys.coretweaks.CoreTweaksMod");
        hasGtnhLib = checkClassExists("com.gtnewhorizon.gtnhlib.util.ObjectPooler");
        hasHodgepodge = checkClassExists("com.mitchej123.hodgepodge.Hodgepodge");
        hasUniMixins = checkClassExists("io.github.legacymoddingmc.unimixins.all.AllModule")
                || checkClassExists("com.gtnewhorizon.gtnhmixins.GTNHMixins");
        hasSignalController = checkClassExists("jp.masa.signalcontrollermod.block.tileentity.TileEntitySignalController");
        hasWebCtc = checkClassExists("org.webctc.railgroup.RailGroupUtilsKt");
        hasAts = checkClassExists("jp.kaiz.atsassistmod.block.tileentity.TileEntityCustom");
        hasRtm = checkClassExists("jp.ngt.rtm.entity.train.EntityTrainBase");
        hasNgtScriptUtil = checkClassExists("jp.ngt.ngtlib.io.ScriptUtil");
        hasMcte = checkClassExists("jp.ngt.mcte.world.MCTEWorld");
        hasKaizAngelicaCompat = checkClassExists("jp.kaiz.kaizpatch.compat.AngelicaCompat");
        hasRailMapCustom = checkClassExists("jp.ngt.rtm.rail.util.RailMapCustom");
        hasAngelicaGlsm = checkClassExists("com.gtnewhorizons.angelica.glsm.GLStateManager");
        logDetectedCompatMods();
    }

    private boolean checkClassExists(String className) {
        try {
            String resource = className.replace('.', '/') + ".class";
            return getClass().getClassLoader().getResource(resource) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean shouldApply;
        
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.macros.")) {
            shouldApply = isClient && hasMacroMod;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.gtnhlib.")) {
            if (mixinClassName.endsWith(".MixinBlockPaneFix")) {
                shouldApply = isClient && hasGtnhLib;
            } else {
                shouldApply = hasGtnhLib;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.kaizpatch.")) {
            if (mixinClassName.endsWith(".McteWorldSetBlockDiffMixin")) {
                shouldApply = hasMcte;
            } else if (mixinClassName.endsWith(".RenderMiniatureDynamicLightMixin")
                    || mixinClassName.endsWith(".RenderItemMiniatureDynamicLightMixin")) {
                shouldApply = isClient && hasMcte;
            } else if (mixinClassName.endsWith(".AngelicaScriptTransformCacheMixin")) {
                shouldApply = isClient && hasKaizAngelicaCompat;
            } else if (mixinClassName.endsWith(".RailMapCustomCacheMixin")) {
                shouldApply = hasRailMapCustom;
            } else {
                shouldApply = hasNgtScriptUtil;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.angelica.")) {
            if (mixinClassName.endsWith(".AngelicaRenderGlobalDisplayListCrashMixin")) {
                shouldApply = isClient && hasAngelicaGlsm && enableNativeRenderGlobalDisplayLists;
            } else {
                shouldApply = isClient && hasAngelicaGlsm;
            }
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.signal.")) {
            shouldApply = hasSignalController;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.webctc.")) {
            shouldApply = hasWebCtc;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.ats.")) {
            shouldApply = hasAts;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.rtm.")) {
            shouldApply = hasRtm;
        } else if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.minecraft.")) {
            shouldApply = isClient && hasGtnhLib;
        } else {
            shouldApply = true;
        }
        
        System.out.println("[CrossTieMixin] " + mixinClassName + " -> " + (shouldApply ? "APPLY" : "SKIP"));
        return shouldApply;
    }

    private void logDetectedCompatMods() {
        System.out.println("[CrossTieMixin] Detected performance mods: "
                + "Angelica=" + hasAngelica
                + ", AngelicaGLSM=" + hasAngelicaGlsm
                + ", ArchaicFix=" + hasArchaicFix
                + ", CoreTweaks=" + hasCoreTweaks
                + ", GTNHLib=" + hasGtnhLib
                + ", Hodgepodge=" + hasHodgepodge
                + ", UniMixins=" + hasUniMixins
                + ", nativeRenderGlobalDisplayLists=" + enableNativeRenderGlobalDisplayLists);
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
