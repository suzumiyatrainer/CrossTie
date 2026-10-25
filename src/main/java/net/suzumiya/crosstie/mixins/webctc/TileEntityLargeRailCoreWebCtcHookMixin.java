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

    @Shadow
    public abstract boolean isLogicalRailOccupied();

    @Unique
    private boolean crosstie$initialTickDone = false;

    @Unique
    private boolean crosstie$lastOccupied = false;

    @Unique
    private boolean crosstie$lastTurning = false;

    @Inject(method = "updateEntity", at = @At("RETURN"))
    private void crosstie$onUpdateEntityReturn(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            boolean occupied = this.isLogicalRailOccupied();
            boolean turning = false;

            if ((Object) this instanceof jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore) {
                jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore sc = (jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore) (Object) this;
                jp.ngt.rtm.rail.util.SwitchType st = sc.getSwitch();
                if (st != null && st.getPoints() != null) {
                    for (jp.ngt.rtm.rail.util.Point p : st.getPoints()) {
                        if (p != null) {
                            boolean rs = p.rpRoot != null && p.rpRoot.checkRSInput(this.worldObj);
                            boolean settled = (p.getMovement() == 0.0F && !rs) || (p.getMovement() == 1.0F && rs);
                            if (!settled) {
                                turning = true;
                                break;
                            }
                        }
                    }
                }
            }

            // 初回 tick 時に Dirty 登録（サーバー起動時・チャンクロード時の初期状態取得）
            if (!this.crosstie$initialTickDone) {
                this.crosstie$initialTickDone = true;
                this.crosstie$lastOccupied = occupied;
                this.crosstie$lastTurning = turning;
                WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
                return;
            }

            // 占有状態または転轍状態が変化した時のみ Dirty 登録
            if (occupied != this.crosstie$lastOccupied || turning != this.crosstie$lastTurning) {
                this.crosstie$lastOccupied = occupied;
                this.crosstie$lastTurning = turning;
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
