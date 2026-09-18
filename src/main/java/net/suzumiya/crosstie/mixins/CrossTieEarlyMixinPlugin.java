package net.suzumiya.crosstie.mixins;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.lib.tree.ClassNode;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;
import net.suzumiya.crosstie.asm.CrossTieCorePlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CrossTieEarlyMixinPlugin implements IMixinConfigPlugin {
    private boolean isClient;

    @Override
    public void onLoad(String mixinPackage) {
        isClient = FMLLaunchHandler.side() == Side.CLIENT;
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

    private boolean isModPresent(String modName) {
        net.suzumiya.crosstie.utils.ModDetector detector = CrossTieCorePlugin.getModDetector();
        if (detector == null) {
            detector = new net.suzumiya.crosstie.utils.ModDetector(null);
        }
        return detector.isModPresent(modName);
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        if (isClient && isModPresent("lwjgl3ify")) {
            mixins.add("lwjgl3ify.Lwjgl3ifyKeyboardIsKeyDownMixin");
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
