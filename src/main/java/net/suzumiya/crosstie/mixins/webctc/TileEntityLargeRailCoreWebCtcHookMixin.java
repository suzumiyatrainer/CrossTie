package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.suzumiya.crosstie.utils.WebCtcDirtyTracker;

@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityLargeRailCoreWebCtcHookMixin extends TileEntityLargeRailBase {

    @Shadow
    public boolean colliding;

    @Shadow
    protected boolean isCollidedTrain;

    @Unique
    private boolean crosstie$initialTickDone = false;

    @Unique
    private boolean crosstie$lastColliding = false;

    @Inject(method = "updateEntity", at = @At("HEAD"))
    private void crosstie$onUpdateEntityHead(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            // 初回 tick 時に Dirty 登録（サーバー起動時・チャンクロード時の初期状態取得）
            if (!this.crosstie$initialTickDone) {
                this.crosstie$initialTickDone = true;
                this.crosstie$lastColliding = this.colliding;
                WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
                return;
            }

            // 占有状態が変化した時のみ Dirty 登録
            if (this.colliding != this.crosstie$lastColliding) {
                this.crosstie$lastColliding = this.colliding;
                WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
            }
        }
    }

    @Inject(method = "breakLogicalRail", at = @At("HEAD"))
    private void crosstie$onBreakLogicalRail(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markRailRemoved(this.xCoord, this.yCoord, this.zCoord);
            if ((Object) this instanceof jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore) {
                java.util.List<int[]> groupPositions = ((jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore) (Object) this).getRailGroupCorePositions();
                if (groupPositions != null) {
                    for (int[] p : groupPositions) {
                        WebCtcDirtyTracker.markRailRemoved(p[0], p[1], p[2]);
                    }
                }
            }
        }
    }

    @Inject(method = "invalidate", at = @At("HEAD"), remap = true)
    private void crosstie$onInvalidate(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markRailRemoved(this.xCoord, this.yCoord, this.zCoord);
            if ((Object) this instanceof jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore) {
                java.util.List<int[]> groupPositions = ((jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore) (Object) this).getRailGroupCorePositions();
                if (groupPositions != null) {
                    for (int[] p : groupPositions) {
                        WebCtcDirtyTracker.markRailRemoved(p[0], p[1], p[2]);
                    }
                }
            }
        }
    }

    @Inject(method = "setRailPositions", at = @At("RETURN"))
    private void crosstie$onSetRailPositions(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
        }
    }

    @Inject(method = "setProperty(Ljp/ngt/rtm/rail/util/RailProperty;)V", at = @At("RETURN"))
    private void crosstie$onSetProperty(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
        }
    }
}
