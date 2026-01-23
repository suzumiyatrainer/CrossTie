package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityBogie最適化Mixin
 * 
 * 台車エンティティの負荷を軽減します。
 */
@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogieMixin {

    @Shadow
    public abstract float getSpeed();

    @Unique
    private static final float CROSSTIE$SPEED_THRESHOLD = 0.001F;

    /**
     * サーバー側でのアニメーション/エフェクト処理をスキップ
     * 
     * EntityBogieには車輪の回転などのアニメーションがありますが、
     * サーバー側では不要です。
     */
    @Inject(method = "updateWheelRotation", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$skipServerAnimation(CallbackInfo ci) {
        EntityBogie bogie = (EntityBogie) (Object) this;
        if (!bogie.worldObj.isRemote) {
            ci.cancel();
        }
    }

    /**
     * 静止時の重い更新処理をスキップ
     * 
     * 速度がほぼ0の場合、レール検索などの重い更新をスキップします。
     * ただし、必要な更新（当たり判定など）は残すため、一部分のみスキップします。
     */
    /*
     * Note: RTMのコード構造に深く依存するため、現時点では安全のためアニメーションのみ最適化します。
     * 
     * @Inject(method = "updateBogiePos", at = @At("HEAD"), cancellable = true)
     * private void crosstie$skipStaticUpdate(CallbackInfo ci) {
     * if (Math.abs(this.getSpeed()) < CROSSTIE$SPEED_THRESHOLD) {
     * ci.cancel();
     * }
     * }
     */
}
