package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 2-6: {@link SwitchType#onUpdate(World)} の毎 tick {@code Arrays.stream(this.points).forEach(...)} を
 * 単純な {@code for} ループに置換し、Stream / Lambda アロケーションを排除する。
 */
@Mixin(value = SwitchType.class, remap = false)
public abstract class SwitchTypeOptimizationMixin {

    @Shadow
    protected Point[] points;

    /**
     * @author CrossTie
     * @reason 毎 tick の Arrays.stream() アロケーションを排除する。
     */
    @Overwrite
    public void onUpdate(World world) {
        Point[] pts = this.points;
        if (pts == null) {
            return;
        }
        for (int i = 0; i < pts.length; i++) {
            Point p = pts[i];
            if (p != null) {
                p.onUpdate(world);
            }
        }
    }
}
