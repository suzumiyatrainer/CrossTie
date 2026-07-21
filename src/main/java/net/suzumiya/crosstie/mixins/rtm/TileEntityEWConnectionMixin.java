package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.cache.ElectricalWiringCacheManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.electric.TileEntityElectricalWiring", remap = false)
public abstract class TileEntityEWConnectionMixin {

    /**
     * 架線の接続が変更された際にお飾り判定キャッシュをクリアする。
     * KaizPatchX 環境では setConnectionTo が TileEntityElectricalWiring に存在する。
     */
    @Inject(method = "setConnectionTo", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$invalidateCacheOnConnectionChange(CallbackInfoReturnable<Boolean> cir) {
        ElectricalWiringCacheManager.clear();
    }
}
