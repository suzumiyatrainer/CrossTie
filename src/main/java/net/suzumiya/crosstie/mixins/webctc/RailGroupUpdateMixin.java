package net.suzumiya.crosstie.mixins.webctc;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.webctc.railgroup.RailGroupUtilsKt", remap = false)
public abstract class RailGroupUpdateMixin {

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z"),
            require = 0,
            remap = true)
    private static boolean crosstie$skipUnchangedSetBlock(
            World world,
            int x,
            int y,
            int z,
            Block block,
            int metadata,
            int flags) {
        if (world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == metadata) {
            return false;
        }
        return world.setBlock(x, y, z, block, metadata, flags);
    }
}
