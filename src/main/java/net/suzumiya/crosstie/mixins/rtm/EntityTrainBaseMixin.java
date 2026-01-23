package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityTrainBase最適化Mixin
 * 
 * TPS（サーバー処理）最適化:
 * - 静止中の列車の更新処理をスキップ
 * - チャンク更新頻度を削減
 * - アニメーション計算をクライアント専用に
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseMixin {

    // Shadow fields - 元のクラスのフィールドにアクセス
    @Shadow
    protected float trainSpeed;

    @Shadow
    protected int brakeCount;

    @Shadow
    public int ticksExisted;

    @Shadow
    public net.minecraft.world.World worldObj;

    // Shadow methods - 元のクラスのメソッドにアクセス
    @Shadow
    public abstract int getNotch();

    @Shadow
    protected abstract void updateChunks();

    // Unique fields - Mixin専用のフィールド
    @Unique
    private static final float CROSSTIE$SPEED_THRESHOLD = 0.001F;

    @Unique
    private static final int CROSSTIE$CHUNK_UPDATE_INTERVAL = 5;

    /**
     * 静止中の列車の速度更新をスキップ
     * 
     * 列車が完全に停止しており、ノッチが0、ブレーキカウントも0の場合、
     * 速度計算をスキップして処理負荷を削減します。
     */
    @Inject(method = "updateSpeed", at = @At("HEAD"), cancellable = true)
    private void crosstie$skipStaticTrainSpeed(CallbackInfo ci) {
        // 速度がほぼ0、ノッチが0、ブレーキカウントが0なら処理をスキップ
        if (Math.abs(this.trainSpeed) < CROSSTIE$SPEED_THRESHOLD
                && this.getNotch() == 0
                && this.brakeCount == 0) {
            ci.cancel();
        }
    }

    /**
     * チャンク更新頻度を削減
     * 
     * 元は毎tick実行されていたチャンク更新を、5tickに1回に削減します。
     * これによりチャンクローダーのオーバーヘッドを削減します。
     */
    @Redirect(method = "onVehicleUpdate", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/entity/train/EntityTrainBase;updateChunks()V"), require = 0 // 見つからなくてもエラーにしない（互換性のため）
    )
    private void crosstie$reduceChunkUpdateFrequency(Object instance) {
        // 5tickに1回だけチャンク更新を実行
        if (this.ticksExisted % CROSSTIE$CHUNK_UPDATE_INTERVAL == 0) {
            this.updateChunks();
        }
    }

    /**
     * アニメーション計算をクライアント専用に
     * 
     * アニメーション計算はクライアント側でのみ必要なため、
     * サーバー側では実行をスキップします。
     */
    @Inject(method = "updateAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$clientOnlyAnimation(CallbackInfo ci) {
        // サーバー側ではアニメーション計算をスキップ
        if (!this.worldObj.isRemote) {
            ci.cancel();

            // 初回のみログ出力
            if (this.ticksExisted == 1) {
                CrossTie.LOGGER.debug("Skipping server-side animation update for train entity");
            }
        }
    }

    /**
     * 編成更新の頻度を削減 (Formation System Optimization)
     * 
     * 毎tick実行される編成チェックを、20tickに1回に削減します。
     * 編成変更は頻繁には起きないため、これで十分です。
     */
    @Inject(method = "updateFormation", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$throttleFormationUpdate(CallbackInfo ci) {
        // 20tickに1回だけ実行 (1秒に1回)
        if (this.ticksExisted % 20 != 0) {
            ci.cancel();
        }
    }
}
