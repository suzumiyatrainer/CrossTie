package net.suzumiya.crosstie.asm;

import cpw.mods.fml.common.Loader;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * CrossTie Mixin Configuration Plugin
 * 
 * 特定のModが存在しない場合に、関連するMixinの適用を自動的にキャンセルし、
 * クラッシュを防ぐためのプラグインです。
 */
public class CrossTieMixinPlugin implements IMixinConfigPlugin {

    private boolean isAngelicaPresent;

    @Override
    public void onLoad(String mixinPackage) {
        // 現在のクラスローダーからAngelicaの存在を確認
        // AngelicaはCoreModとしてロードされるため、この時点でクラスパスに存在するはずであり、
        // Class.forNameを使っても安全に検出できます。
        try {
            Class.forName("com.gtnewhorizons.angelica.Angelica", false, Launch.classLoader);
            isAngelicaPresent = true;
        } catch (ClassNotFoundException e) {
            isAngelicaPresent = false;
        }

        // Bambooの検出ロジックは削除しました。
        // BambooはLate Mixinとしてロードされるため、Loader.isModLoadedを使用して判定します。
        // Early MixinのフェーズでClass.forNameを使用すると、LaunchClassLoaderのキャッシュが汚染され、
        // その後のModロード時にClassNotFoundExceptionが発生するリスクがあるためです。
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Angelica関連のMixinは、Angelicaが存在する場合のみ適用
        if (mixinClassName.contains("angelica.AngelicaDisplayListManagerMixin")) {
            return isAngelicaPresent;
        }

        // Bamboo関連のMixinは、Bambooが存在する場合のみ適用
        // Bamboo用のMixinはLate Mixinとしてロードされるため、Loader.isModLoadedが使用可能です。
        if (mixinClassName.contains("bamboo.Bamboo")) {
            return Loader.isModLoaded("Bamboo");
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
