package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@SuppressWarnings("all")
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererOptimizationMixin {

    /**
     * @author CrossTie
     * @reason Optimize hot loop: replace IntStream with basic for-loop and cache
     *         brightness queries for identical block coordinates.
     */
    @Overwrite
    protected final int[] getRailBrightness(World world, int x, int y, int z, float[][] rp) {
        int len = rp.length;
        int[] fa = new int[len];
        if (len == 0) {
            return fa;
        }

        // Cache last query block coordinate and brightness
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int lastBrightness = 0;

        RailPartsRenderer thisRenderer = (RailPartsRenderer) (Object) this;

        for (int i = 0; i < len; i++) {
            float[] pos = rp[i];
            int x0 = x + (int) pos[0];
            int y0 = y + (int) pos[1];
            int z0 = z + (int) pos[2];

            if (x0 == lastX && y0 == lastY && z0 == lastZ) {
                fa[i] = lastBrightness;
            } else {
                lastBrightness = thisRenderer.getBrightness(world, x0, y0, z0);
                fa[i] = lastBrightness;
                lastX = x0;
                lastY = y0;
                lastZ = z0;
            }
        }
        return fa;
    }
}

// Minimal stub to satisfy compile-time reference to the real target type.
// The real RailPartsRenderer comes from the target mod at runtime; this
// placeholder only exists to allow compilation in this project setup.
abstract class RailPartsRenderer {
    // Signature matches the method used by the mixin. Implementation is unused
    // at compile-time and will be shadowed by the runtime class.
    protected int getBrightness(World world, int x, int y, int z) {
        return 0;
    }
}
