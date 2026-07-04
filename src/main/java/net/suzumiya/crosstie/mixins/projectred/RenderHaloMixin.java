package net.suzumiya.crosstie.mixins.projectred;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "mrtjp.projectred.core.RenderHalo$", remap = false)
public class RenderHaloMixin {

    private static boolean crosstie$hasLoggedAddLight = false;
    private static boolean crosstie$hasLoggedRenderHalo = false;

    @ModifyVariable(
        method = "addLight(IIIILcodechicken/lib/vec/Cuboid6;)V",
        at = @At("HEAD"),
        ordinal = 3,
        argsOnly = true,
        remap = false
    )
    private int crosstie$clampColorAddLight(int color) {
        if (color < 0 || color > 15) {
            if (!crosstie$hasLoggedAddLight) {
                System.out.println("[CrossTie] RenderHalo$.addLight color clamped to 0 (original value: " + color + "). Further warnings suppressed.");
                crosstie$hasLoggedAddLight = true;
            }
            return 0;
        }
        return color;
    }

    @ModifyVariable(
        method = "renderHalo(Lcodechicken/lib/vec/Cuboid6;ILcodechicken/lib/vec/Transformation;)V",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true,
        remap = false
    )
    private int crosstie$clampColorRenderHalo(int color) {
        if (color < 0 || color > 15) {
            if (!crosstie$hasLoggedRenderHalo) {
                System.out.println("[CrossTie] RenderHalo$.renderHalo color clamped to 0 (original value: " + color + "). Further warnings suppressed.");
                crosstie$hasLoggedRenderHalo = true;
            }
            return 0;
        }
        return color;
    }
}
