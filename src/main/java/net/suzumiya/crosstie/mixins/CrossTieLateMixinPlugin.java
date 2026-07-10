package net.suzumiya.crosstie.mixins;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.lib.tree.ClassNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.suzumiya.crosstie.asm.CrossTieCorePlugin;
import net.suzumiya.crosstie.utils.ModDetector;

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
        if (mixinClassName.equals("net.suzumiya.crosstie.mixins.projectred.RenderHaloMixin")) {
            ModDetector detector = CrossTieCorePlugin.getModDetector();
            if (detector != null && detector.isModPresent("ProjectRed")) {
                return true;
            }
            try {
                Class.forName("mrtjp.projectred.core.RenderHalo$", false, this.getClass().getClassLoader());
                System.out.println(
                        "[CrossTie] ProjectRed RenderHalo$ class detected via Class.forName. Applying RenderHaloMixin.");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
