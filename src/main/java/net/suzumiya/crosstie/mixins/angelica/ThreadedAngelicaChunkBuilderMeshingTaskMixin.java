package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GTNHLib model rendering keeps shared block-state objects, so it must stay on the main thread.
 */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedAngelicaChunkBuilderMeshingTask", remap = false)
public abstract class ThreadedAngelicaChunkBuilderMeshingTaskMixin {

    @Inject(method = "canRenderOffThread", at = @At("HEAD"), cancellable = true)
    private void crosstie$forceMainThreadForGtnhLibModels(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (crosstie$isGtnhLibModeled(block)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean crosstie$isGtnhLibModeled(Block block) {
        try {
            return (Boolean) block.getClass().getMethod("nhlib$isModeled").invoke(block);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false;
        }
    }
}
