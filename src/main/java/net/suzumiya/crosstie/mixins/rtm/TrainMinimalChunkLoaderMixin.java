package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.world.TrainChunkLoaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link EntityTrainBase} に極軽量・最小限チャンクローダー（車両周囲+3ブロック）を統合する Mixin。
 *
 * <p>
 * サーバー側で毎Tick実行される {@code onVehicleUpdate} の末尾で必要チャンクを forceChunk 状態に維持し、
 * 8両以上の編成において後方車両や台車がプレイヤーの描画距離境界外でアンロードされることによる
 * 台車消失バグや、ドア・ドアチャイム・ホームドアの動作・再生遅延（1tick以上のズレ）を防止する。
 * </p>
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class TrainMinimalChunkLoaderMixin {

    @Inject(method = "onVehicleUpdate", at = @At("RETURN"), require = 0)
    private void crosstie$onVehicleUpdateMinimalChunkLoader(CallbackInfo ci) {
        if (!CrossTieConfig.enableTrainMinimalChunkLoader) {
            return;
        }

        EntityTrainBase train = (EntityTrainBase) (Object) this;
        if (train.worldObj != null && !train.worldObj.isRemote) {
            TrainChunkLoaderManager.INSTANCE.onTrainUpdate(train);
        }
    }

    @Inject(method = "setDead", at = @At("HEAD"), require = 0)
    private void crosstie$onTrainSetDead(CallbackInfo ci) {
        EntityTrainBase train = (EntityTrainBase) (Object) this;
        if (train.worldObj != null && !train.worldObj.isRemote) {
            TrainChunkLoaderManager.INSTANCE.onTrainRemoved(train);
        }
    }

    @Inject(method = { "onChunkUnload", "func_70106_y" }, at = @At("HEAD"), require = 0)
    private void crosstie$onTrainChunkUnload(CallbackInfo ci) {
        EntityTrainBase train = (EntityTrainBase) (Object) this;
        if (train.worldObj != null && !train.worldObj.isRemote) {
            TrainChunkLoaderManager.INSTANCE.onTrainRemoved(train);
        }
    }
}
