package net.suzumiya.crosstie.mixins;

import net.suzumiya.crosstie.CrossTie;
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
        CrossTie.LOGGER.info("CrossTie Mixin Plugin loaded");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // RTM関連のMixin
        if (mixinClassName.contains(".rtm.")) {
            if (!ModDetector.isRTMPresent()) {
                CrossTie.LOGGER.debug("Skipping RTM mixin {} - RTM not present", mixinClassName);
                return false;
            }
        }

        // Bamboo関連のMixin
        if (mixinClassName.contains(".bamboo.")) {
            if (!ModDetector.hasBamboo) {
                CrossTie.LOGGER.debug("Skipping Bamboo mixin {} - Bamboo not present", mixinClassName);
                return false;
            }
        }

        // OEMod関連のMixin
        if (mixinClassName.contains(".oemod.")) {
            if (!ModDetector.hasOEMod) {
                CrossTie.LOGGER.debug("Skipping OEMod mixin {} - OEMod not present", mixinClassName);
                return false;
            }
        }

        CrossTie.LOGGER.debug("Applying mixin: {}", mixinClassName);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 必要に応じて処理
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();

        // RTMが存在する場合のみRTM関連Mixinを登録
        if (ModDetector.isRTMPresent()) {
            mixins.add("rtm.EntityTrainBaseMixin");
            mixins.add("rtm.EntityBogieMixin");
            // クライアント側Mixinは別途client配列で定義されているため、ここでは登録不要
        }

        // Bamboo Modが存在する場合
        if (ModDetector.hasBamboo) {
            // TODO: Bamboo最適化Mixinを追加
            // mixins.add("bamboo.SomeBambooMixin");
        }

        // OEModが存在する場合
        if (ModDetector.hasOEMod) {
            // TODO: OEMod最適化Mixinを追加
            // mixins.add("oemod.SomeOEModMixin");
        }

        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Mixin適用前の処理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        CrossTie.LOGGER.info("Applied mixin {} to {}", mixinClassName, targetClassName);
    }
}
