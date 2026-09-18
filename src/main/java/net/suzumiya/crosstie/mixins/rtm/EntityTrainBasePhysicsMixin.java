package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.suzumiya.crosstie.physics.PhysicsEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBasePhysicsMixin extends EntityVehicleBase<TrainConfig> {

    @Shadow
    private Formation formation;

    /** Eager Global Physics Update で最後に計算された tick */
    @Unique
    private int crosstie$lastCalculatedTick = -1;

    public EntityTrainBasePhysicsMixin(net.minecraft.world.World world) {
        super(world);
    }

    /**
     * EntityTrainBase の updateMovement が呼ばれる直前で、
     * ワールド全体の編成物理計算を一気に終わらせる（先取り計算）。
     */
    @Inject(method = "updateMovement", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$onUpdateMovement(CallbackInfo ci) {
        if (this.worldObj.isRemote) {
            return; // クライアント側は現状維持
        }

        int currentTick = NGTUtil.getServer().getTickCounter();

        // 1. ワールド全体の先行並列計算をトリガー
        PhysicsEngine.ensureGlobalPhysics(this.worldObj, currentTick);

        // 2. すでにこの tick の計算は終わっているので、実際の計算メソッドはバイパスする
        // ただし、計算済みであることを記録しておく
        if (this.crosstie$lastCalculatedTick != currentTick) {
            this.crosstie$lastCalculatedTick = currentTick;
            // 注意: EntityTrainBase は updateMovement() 内で formation.updateTrainMovement() しか呼ばず、
            // moveEntity() を呼んでいないため、計算を完全にバイパスして問題ない。
            ci.cancel();
        }
    }
}
