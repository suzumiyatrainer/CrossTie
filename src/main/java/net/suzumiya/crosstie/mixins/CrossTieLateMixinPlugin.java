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
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.atsassist.")) {
            return true;
        }
        if (mixinClassName.startsWith("net.suzumiya.crosstie.mixins.mcte.late.")) {
            ModDetector detector = CrossTieCorePlugin.getModDetector();
            return detector != null && detector.isModPresent("MCTE")
                    && (detector.isModPresent("Angelica") || detector.isModPresent("AngelicaGlsm"));
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();

        ModDetector detector = CrossTieCorePlugin.getModDetector();
        if (detector != null && detector.isModPresent("MCTE")) {
            if (detector.isModPresent("Angelica") || detector.isModPresent("AngelicaGlsm")) {
                mixins.add("mcte.late.RenderMiniatureAngelicaLightMixin");
                mixins.add("mcte.late.RenderItemMiniatureAngelicaLightMixin");
                mixins.add("mcte.late.McteWorldAngelicaLightMixin");
                mixins.add("mcte.late.NGTRendererMixin");
                mixins.add("mcte.late.DirectTessellatorLightMixin");
                System.out.println("[CrossTieLate] Registered MCTE Angelica-compat mixins successfully.");
            }
        }

        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
