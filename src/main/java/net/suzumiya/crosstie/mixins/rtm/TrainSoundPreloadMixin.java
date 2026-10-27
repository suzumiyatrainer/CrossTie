package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.FMLCommonHandler;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.client.sound.SoundPreloadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 列車のドア開閉音・チャイム等のサウンド未ロード遅延（初回再生ラグ）を解消するため、
 * クライアント側で車両スポーン時・モデル設定時・ドア操作時に自動プリロードを行う Mixin。
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class TrainSoundPreloadMixin {

    @Unique
    private boolean crosstie$soundPreloaded = false;

    /**
     * クライアント側での初回更新時、車両の全サウンド（ドア音・チャイム等）を自動プリロードする。
     */
    @Inject(method = "onVehicleUpdate", at = @At("HEAD"), require = 0)
    private void crosstie$preloadSoundsOnUpdate(CallbackInfo ci) {
        if (!crosstie$soundPreloaded && CrossTieConfig.enableSoundPreload) {
            EntityTrainBase train = (EntityTrainBase) (Object) this;
            if (train.worldObj != null && train.worldObj.isRemote) {
                SoundPreloadManager.preloadTrain(train);
                this.crosstie$soundPreloaded = true;
            }
        }
    }

    /**
     * モデル変更時（車庫での形式変更やテクスチャ変更時）に新しいモデルのサウンドを自動プリロードする。
     */
    @Inject(method = "setModelName", at = @At("RETURN"), require = 0)
    private void crosstie$preloadSoundsOnModelChange(String name, CallbackInfo ci) {
        if (CrossTieConfig.enableSoundPreload && FMLCommonHandler.instance().getSide().isClient()) {
            EntityTrainBase train = (EntityTrainBase) (Object) this;
            if (train.worldObj != null && train.worldObj.isRemote) {
                SoundPreloadManager.preloadTrain(train);
            }
        }
    }

    /**
     * ドア開閉状態（State_Door, id=4）の DataWatcher パケットを受信した瞬間、
     * ドアアニメーション開始前の段階で確実にサウンドを事前ウォームアップする。
     */
    @Inject(method = "setByteToDataWatcher", at = @At("HEAD"), require = 0)
    private void crosstie$preloadSoundsOnDoorStateChange(int id, byte data, CallbackInfoReturnable<Byte> cir) {
        if (CrossTieConfig.enableSoundPreload && id == 4 && FMLCommonHandler.instance().getSide().isClient()) {
            EntityTrainBase train = (EntityTrainBase) (Object) this;
            if (train.worldObj != null && train.worldObj.isRemote) {
                SoundPreloadManager.preloadTrain(train);
            }
        }
    }
}
