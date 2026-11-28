package net.suzumiya.crosstie.mixins.webctc;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.SwitchType;
import net.suzumiya.crosstie.accessors.rtm.IPointRSCacheAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2-6: WebCTC {@code RailRouterKt.isTurning(TileEntityLargeRailCore)} において、
 * {@code PointRSCacheMixin} の {@code RailDir.NONE} 除外と整合させ、
 * 分岐なし点への偶然の RS 入力による「永久転轍中」誤判定を防止する Late Mixin。
 */
@Mixin(targets = "org.webctc.router.api.RailRouterKt", remap = false)
public abstract class RailRouterTurningOptimizationMixin {

    @Inject(
            method = "isTurning(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void crosstie$fastIsTurning(TileEntityLargeRailCore railCore, CallbackInfoReturnable<Boolean> cir) {
        if (!(railCore instanceof TileEntityLargeRailSwitchCore)) {
            cir.setReturnValue(false);
            return;
        }
        TileEntityLargeRailSwitchCore sc = (TileEntityLargeRailSwitchCore) railCore;
        SwitchType st = sc.getSwitch();
        if (st == null || st.getPoints() == null) {
            cir.setReturnValue(false);
            return;
        }
        Point[] points = st.getPoints();
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            if (p == null || p.branchDir == RailDir.NONE) {
                continue;
            }
            boolean rs;
            if (p instanceof IPointRSCacheAccessor) {
                rs = ((IPointRSCacheAccessor) p).crosstie$getCachedRSInput(railCore.getWorldObj());
            } else {
                rs = p.rpRoot != null && railCore.getWorldObj() != null && p.rpRoot.checkRSInput(railCore.getWorldObj());
            }
            float mov = p.getMovement();
            boolean settled = (mov == 0.0F && !rs) || (mov == 1.0F && rs);
            if (!settled) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
