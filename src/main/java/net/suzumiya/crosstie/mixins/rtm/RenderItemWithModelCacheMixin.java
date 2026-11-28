package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.item.RenderItemWithModel;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 4-4: {@link RenderItemWithModel#getResourceLocation(String)} の毎フレーム正規表現分割・インスタンス生成をキャッシュ化する。
 */
@Mixin(value = RenderItemWithModel.class, remap = false)
public abstract class RenderItemWithModelCacheMixin {

    @Unique
    private final Map<String, ResourceLocation> crosstie$resourceCache = new HashMap<>();

    /**
     * @author CrossTie
     * @reason インベントリ・手に持ったカスタムアイコン描画ごとの split / new ResourceLocation を排除
     */
    @Overwrite(remap = false)
    private ResourceLocation getResourceLocation(String location) {
        ResourceLocation cached = this.crosstie$resourceCache.get(location);
        if (cached != null) {
            return cached;
        }
        int colon = location.indexOf(':');
        ResourceLocation rl;
        if (colon >= 0) {
            rl = new ResourceLocation(location.substring(0, colon), location.substring(colon + 1));
        } else {
            rl = new ResourceLocation(location);
        }
        this.crosstie$resourceCache.put(location, rl);
        return rl;
    }
}
