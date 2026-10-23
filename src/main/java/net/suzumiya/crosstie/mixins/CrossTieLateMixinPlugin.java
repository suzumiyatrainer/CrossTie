package net.suzumiya.crosstie.mixins;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.lib.tree.ClassNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CrossTieLateMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.atsassist.")) {
            return cpw.mods.fml.common.Loader.isModLoaded("ATSAssistMod");
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.webctc.")) {
            return cpw.mods.fml.common.Loader.isModLoaded("webctc") || cpw.mods.fml.common.Loader.isModLoaded("WebCTC");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();

        // ModDetector detector = CrossTieCorePlugin.getModDetector();

        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
