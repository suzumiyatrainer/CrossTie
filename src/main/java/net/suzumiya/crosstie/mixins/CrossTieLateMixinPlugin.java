package net.suzumiya.crosstie.mixins;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.lib.tree.ClassNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.suzumiya.crosstie.util.ModDetector;
import net.suzumiya.crosstie.asm.CrossTieCorePlugin;

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
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        
        // At this point ModDetector can be used because we're in LATE phase
        ModDetector detector = CrossTieCorePlugin.getModDetector();
        if (detector != null && detector.isModPresent("ProjectRed")) {
            mixins.add("projectred.TileLampMixin");
        } else if (detector == null) {
            // Fallback just in case
            mixins.add("projectred.TileLampMixin");
        }
        
        System.out.println("[CrossTieLateMixin] Registered late mixins: " + mixins.size());
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
