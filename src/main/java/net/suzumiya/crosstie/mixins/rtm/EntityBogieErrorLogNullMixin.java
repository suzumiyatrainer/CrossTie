package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 乗車中の列車が存在するレールを破壊した際、台車の getTrain() が null のまま
 * errorLog() が呼ばれて NullPointerException でサーバーがクラッシュする問題の修正。
 *
 * <p>列車が null の場合はプレイヤーへの通知対象が存在しないため、errorLog() を何もせず終了させる。</p>
 */
@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogieErrorLogNullMixin {

    @Inject(method = "errorLog", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$skipErrorLogWithoutTrain(double px, double pz, String msg, CallbackInfo ci) {
        if (((EntityBogie) (Object) this).getTrain() == null) {
            ci.cancel();
        }
    }
}
