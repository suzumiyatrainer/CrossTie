package net.suzumiya.crosstie.mixins.ats;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.suzumiya.crosstie.util.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies the same setBlock diff guard to ATSAssist utility calls used from
 * scripts and helper actions.
 */
@Mixin(targets = "jp.kaiz.atsassistmod.utils.KaizWorldUtils", remap = false)
public abstract class KaizWorldUtilsSetBlockDiffMixin {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;)Z"),
            require = 0,
            remap = true)
    private static boolean crosstie$skipUnchangedUtilitySetBlockNoMeta(
            World world, int x, int y, int z, Block block) {
        if (world.getBlock(x, y, z) == block) {
            if (CrossTieDiagnostics.isEnabled()) {
                CrossTieDiagnostics.skippedSetBlockCalls.incrementAndGet();
            }
            return false;
        }
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.blockUpdates.incrementAndGet();
        }
        return world.setBlock(x, y, z, block);
    }

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z"),
            require = 0,
            remap = true)
    private static boolean crosstie$skipUnchangedUtilitySetBlock(
            World world, int x, int y, int z, Block block, int metadata, int flags) {
        if (world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == metadata) {
            if (CrossTieDiagnostics.isEnabled()) {
                CrossTieDiagnostics.skippedSetBlockCalls.incrementAndGet();
            }
            return false;
        }
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.blockUpdates.incrementAndGet();
        }
        return world.setBlock(x, y, z, block, metadata, flags);
    }
}
