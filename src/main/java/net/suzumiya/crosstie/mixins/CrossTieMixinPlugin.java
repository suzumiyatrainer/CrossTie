package net.suzumiya.crosstie.mixins;

import net.suzumiya.crosstie.util.ModDetector;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CrossTie Mixin Plugin
 * 
 * 存在するModに応じて動的にMixinを有効/無効化します。
 * これにより、ターゲットModが存在しない環境でのクラッシュを防ぎます。
 */
public class CrossTieMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Logging via System.out is safer at this stage
        System.out.println("[CrossTie] Mixin Plugin loaded");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // RTM関連のMixin
        if (mixinClassName.contains(".rtm.")) {
            if (!ModDetector.isClassLoaded("jp.ngt.rtm.RTMCore")) {
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
        return null; // mixins.crosstie.jsonですべて定義し、shouldApplyMixinでフィルタリングする
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
