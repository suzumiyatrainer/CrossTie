package net.suzumiya.crosstie.mixins.kaizpatch;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.suzumiya.crosstie.utils.CrossTieDiagnostics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.mcte.world.MCTEWorld", remap = false)
public abstract class McteWorldSetBlockDiffMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$skipUnchangedMcteSetBlock(int x, int y, int z, Block block, int meta, int flag,
            CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (!world.isRemote && world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == meta) {
            if (CrossTieDiagnostics.isEnabled()) {
                CrossTieDiagnostics.skippedSetBlockCalls.incrementAndGet();
            }
            cir.setReturnValue(false);
        }
    }
}
