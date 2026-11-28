package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 4-2: {@link EntityTrainBase} の停車中パンタグラフ高さ計算キャッシュおよび
 * {@code updateBlockCollisionState()} の軽量化。
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBaseTickOptimizationMixin extends Entity {

    public EntityTrainBaseTickOptimizationMixin(World world) {
        super(world);
    }

    @Shadow
    public abstract void setSignal(int par1);

    @Unique
    private int[] crosstie$cachedPantoHeight;

    @Unique
    private double crosstie$pantoX = Double.NaN;

    @Unique
    private double crosstie$pantoY;

    @Unique
    private double crosstie$pantoZ;

    @Unique
    private float crosstie$pantoYaw;

    @Unique
    private float crosstie$pantoPitch;

    @Unique
    private int crosstie$pantoCacheTick = -100;

    @Unique
    private int crosstie$lastRailBlockX = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastRailBlockY = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastRailBlockZ = Integer.MIN_VALUE;

    @Unique
    private TileEntityLargeRailCore crosstie$cachedRailCore;

    @Inject(method = "getPantographMaxHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$tryReusePantographHeight(CallbackInfoReturnable<int[]> cir) {
        EntityTrainBase self = (EntityTrainBase) (Object) this;
        if (this.crosstie$cachedPantoHeight != null
                && self.getSpeed() == 0.0F
                && (self.ticksExisted - this.crosstie$pantoCacheTick) < 20
                && self.posX == this.crosstie$pantoX
                && self.posY == this.crosstie$pantoY
                && self.posZ == this.crosstie$pantoZ
                && self.rotationYaw == this.crosstie$pantoYaw
                && self.rotationPitch == this.crosstie$pantoPitch) {
            cir.setReturnValue(this.crosstie$cachedPantoHeight);
        }
    }

    @Inject(method = "getPantographMaxHeight", at = @At("RETURN"), remap = false)
    private void crosstie$cachePantographHeight(CallbackInfoReturnable<int[]> cir) {
        EntityTrainBase self = (EntityTrainBase) (Object) this;
        this.crosstie$cachedPantoHeight = cir.getReturnValue();
        this.crosstie$pantoX = self.posX;
        this.crosstie$pantoY = self.posY;
        this.crosstie$pantoZ = self.posZ;
        this.crosstie$pantoYaw = self.rotationYaw;
        this.crosstie$pantoPitch = self.rotationPitch;
        this.crosstie$pantoCacheTick = self.ticksExisted;
    }

    @Inject(method = "updateBlockCollisionState", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$optimizeUpdateBlockCollisionState(CallbackInfo ci) {
        EntityTrainBase self = (EntityTrainBase) (Object) this;
        int bx = MathHelper.floor_double(self.posX);
        int by = MathHelper.floor_double(self.posY);
        int bz = MathHelper.floor_double(self.posZ);

        boolean sameBlock = (bx == this.crosstie$lastRailBlockX
                && by == this.crosstie$lastRailBlockY
                && bz == this.crosstie$lastRailBlockZ);

        if (sameBlock && self.getSpeed() == 0.0F && ((self.ticksExisted + self.getEntityId()) & 3) != 0) {
            TileEntityLargeRailCore cached = this.crosstie$cachedRailCore;
            if (cached != null && !cached.isInvalid()) {
                this.setSignal(cached.getSignal());
                ci.cancel();
                return;
            }
        }

        // 通常更新またはキャッシュ更新
        TileEntityLargeRailBase rail = TileEntityLargeRailBase.getRailFromCoordinates(self.worldObj, self.posX, self.posY, self.posZ);
        TileEntityLargeRailCore railCore = (rail != null) ? rail.getRailCore() : null;
        this.crosstie$lastRailBlockX = bx;
        this.crosstie$lastRailBlockY = by;
        this.crosstie$lastRailBlockZ = bz;
        this.crosstie$cachedRailCore = railCore;

        if (railCore != null) {
            this.setSignal(railCore.getSignal());
        }
        this.func_145775_I();
        ci.cancel();
    }
}
