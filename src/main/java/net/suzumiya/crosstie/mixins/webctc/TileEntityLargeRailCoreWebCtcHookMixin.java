package net.suzumiya.crosstie.mixins.webctc;

import jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.accessors.rtm.IPointRSCacheAccessor;
import net.suzumiya.crosstie.utils.WebCtcDirtyTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * WebCTC 向けレール状態変化フック（Phase 2 最適化版）。
 *
 * <ul>
 *   <li>通常 tick はローカル {@code this.isCollidedTrain} の O(1) 比較のみを行い、
 *       {@link TileEntityLargeRailSectionCore} での毎 tick O(N²) {@code isLogicalRailOccupied()} 走査を排除。</li>
 *   <li>分岐レールの転轍判定は {@code RailDir.NONE} をスキップし、分岐あり点は
 *       {@link IPointRSCacheAccessor} のキャッシュを参照して重複 RS チェックをゼロ化。</li>
 * </ul>
 */
@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityLargeRailCoreWebCtcHookMixin extends TileEntityLargeRailBase {

    @Shadow
    public boolean colliding;

    @Shadow
    protected boolean isCollidedTrain;

    @Unique
    private boolean crosstie$initialTickDone = false;

    @Unique
    private boolean crosstie$lastLocalOccupied = false;

    @Unique
    private boolean crosstie$lastTurning = false;

    @Inject(method = "updateEntity", at = @At("RETURN"))
    private void crosstie$onUpdateEntityReturn(CallbackInfo ci) {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        boolean localOccupied = this.isCollidedTrain;
        boolean turning = false;

        if ((Object) this instanceof TileEntityLargeRailSwitchCore) {
            TileEntityLargeRailSwitchCore sc = (TileEntityLargeRailSwitchCore) (Object) this;
            SwitchType st = sc.getSwitch();
            if (st != null && st.getPoints() != null) {
                Point[] points = st.getPoints();
                for (int i = 0; i < points.length; i++) {
                    Point p = points[i];
                    if (p == null || p.branchDir == RailDir.NONE) {
                        continue;
                    }
                    boolean rs;
                    if (p instanceof IPointRSCacheAccessor) {
                        rs = ((IPointRSCacheAccessor) p).crosstie$getCachedRSInput(this.worldObj);
                    } else {
                        rs = p.rpRoot != null && p.rpRoot.checkRSInput(this.worldObj);
                    }
                    float mov = p.getMovement();
                    boolean settled = (mov == 0.0F && !rs) || (mov == 1.0F && rs);
                    if (!settled) {
                        turning = true;
                        break;
                    }
                }
            }
        }

        // 初回 tick 時に Dirty 登録（サーバー起動時・チャンクロード時の初期状態取得）
        if (!this.crosstie$initialTickDone) {
            this.crosstie$initialTickDone = true;
            this.crosstie$lastLocalOccupied = localOccupied;
            this.crosstie$lastTurning = turning;
            WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);
            return;
        }

        boolean occupiedChanged = (localOccupied != this.crosstie$lastLocalOccupied);
        boolean turningChanged = (turning != this.crosstie$lastTurning);

        if (occupiedChanged || turningChanged) {
            this.crosstie$lastLocalOccupied = localOccupied;
            this.crosstie$lastTurning = turning;
            WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) (Object) this);

            // SectionCore のローカル占有が変化した瞬間だけ、ロード済みの同一論理レール仲間セクションも Dirty 化
            if (occupiedChanged && (Object) this instanceof TileEntityLargeRailSectionCore) {
                TileEntityLargeRailSectionCore section = (TileEntityLargeRailSectionCore) (Object) this;
                if (section.isRailSection()) {
                    List<int[]> groupPositions = section.getRailGroupCorePositions();
                    if (groupPositions != null) {
                        for (int i = 0, size = groupPositions.size(); i < size; i++) {
                            int[] p = groupPositions.get(i);
                            if (p[0] == this.xCoord && p[1] == this.yCoord && p[2] == this.zCoord) {
                                continue;
                            }
                            if (this.worldObj.blockExists(p[0], p[1], p[2])) {
                                TileEntity te = this.worldObj.getTileEntity(p[0], p[1], p[2]);
                                if (te instanceof TileEntityLargeRailCore) {
                                    WebCtcDirtyTracker.markRailDirty((TileEntityLargeRailCore) te);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "breakLogicalRail", at = @At("HEAD"))
    private void crosstie$onBreakLogicalRail(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markRailRemoved(this.xCoord, this.yCoord, this.zCoord);
            if ((Object) this instanceof TileEntityLargeRailSectionCore) {
                List<int[]> groupPositions = ((TileEntityLargeRailSectionCore) (Object) this).getRailGroupCorePositions();
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
            if ((Object) this instanceof TileEntityLargeRailSectionCore) {
                List<int[]> groupPositions = ((TileEntityLargeRailSectionCore) (Object) this).getRailGroupCorePositions();
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
