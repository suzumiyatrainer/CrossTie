package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.world.World;
import net.suzumiya.crosstie.accessors.rtm.IPointRSCacheAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P-10 & 2-6: {@code Point#onUpdate()} / {@code getActiveRailMap()} の {@code checkRSInput()} 重複排除に加え、
 * 分岐なし点 ({@code branchDir == RailDir.NONE}) の不要な毎 tick RS チェックをスキップする。
 *
 * <p>※ WebCTC 導入環境での永久転轍中誤判定を防ぐため、{@code TileEntityLargeRailCoreWebCtcHookMixin} および
 * {@code RailRouterTurningOptimizationMixin} でも同一の {@code RailDir.NONE} 除外を行う。</p>
 */
@Mixin(value = Point.class, remap = false)
public abstract class PointRSCacheMixin implements IPointRSCacheAccessor {

    @Unique
    private boolean crosstie$rsInputCached = false;

    @Unique
    private boolean crosstie$cachedRSInput = false;

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void crosstie$preCacheRSInput(World world, CallbackInfo ci) {
        Point self = (Point) (Object) this;
        if (self.branchDir == RailDir.NONE) {
            this.crosstie$cachedRSInput = false;
            this.crosstie$rsInputCached = true;
            ci.cancel();
            return;
        }
        this.crosstie$cachedRSInput = self.rpRoot.checkRSInput(world);
        this.crosstie$rsInputCached = true;
    }

    @Redirect(method = "onUpdate", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/rail/util/RailPosition;checkRSInput(Lnet/minecraft/world/World;)Z"))
    private boolean crosstie$redirectOnUpdateRSCheck(RailPosition rp, World world) {
        return this.crosstie$cachedRSInput;
    }

    @Redirect(method = "getActiveRailMap", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/rail/util/RailPosition;checkRSInput(Lnet/minecraft/world/World;)Z"))
    private boolean crosstie$redirectGetActiveRSCheck(RailPosition rp, World world) {
        if (this.crosstie$rsInputCached) {
            return this.crosstie$cachedRSInput;
        }
        return rp.checkRSInput(world);
    }

    @Override
    public boolean crosstie$getCachedRSInput(World world) {
        Point self = (Point) (Object) this;
        if (self.branchDir == RailDir.NONE) {
            return false;
        }
        if (this.crosstie$rsInputCached) {
            return this.crosstie$cachedRSInput;
        }
        return self.rpRoot != null && world != null && self.rpRoot.checkRSInput(world);
    }
}
