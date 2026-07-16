package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.RenderVehicleBase;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.accessors.rtm.IEntityVehicleBaseRenderContextAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: RenderVehicleBase doRender() 描画コンテキストフラグ設定Mixin
 *
 * <h3>役割</h3>
 * <p>
 * {@code RenderVehicleBase#doRender()} の開始/終了に合わせて、 対象の
 * {@code EntityVehicleBase} が保持する描画コンテキストフラグを ON/OFF する。
 * </p>
 *
 * <p>
 * このフラグが立っている間は {@link EntityVehicleBaseModelSetGuardMixin} が
 * {@code onModelChanged()} をキャンセルするため、描画フレーム中に DataWatcher
 * の非同期更新によって発生する誤モデル切り替えが抑制される。
 * </p>
 *
 * <p>
 * <b>Angelica環境でも有効。PICK パスの有無に依存しない。</b>
 * </p>
 */
@Mixin(value = RenderVehicleBase.class, remap = false)
public abstract class RenderVehicleBaseContextMixin {

    @Inject(method = "doRender", at = @At("HEAD"), remap = false)
    private void crosstie$setRenderContextOn(Entity par1, double par2, double par4, double par6, float par8, float par9,
            CallbackInfo ci) {
        if (par1 instanceof IEntityVehicleBaseRenderContextAccessor) {
            ((IEntityVehicleBaseRenderContextAccessor) par1).crosstie$setInRenderContext(true);
        }
    }

    @Inject(method = "doRender", at = @At("RETURN"), remap = false)
    private void crosstie$setRenderContextOff(Entity par1, double par2, double par4, double par6, float par8,
            float par9, CallbackInfo ci) {
        if (par1 instanceof IEntityVehicleBaseRenderContextAccessor) {
            ((IEntityVehicleBaseRenderContextAccessor) par1).crosstie$setInRenderContext(false);
        }
    }
}
