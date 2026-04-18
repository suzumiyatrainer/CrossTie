package net.suzumiya.crosstie.mixins.atsassistmod;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.suzumiya.crosstie.util.TrainSpatialTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "jp.kaiz.atsassistmod.block.tileentity.TileEntityIFTTT", remap = false)
public abstract class MixinTileEntityIFTTT {

    @Redirect(
        method = "updateEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntitiesWithinAABB(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;")
    )
    private List<?> crosstie$redirectGetEntitiesWithinAABB(World world, Class<?> clazz, AxisAlignedBB aabb) {
        if (clazz != null && "jp.ngt.rtm.entity.train.EntityTrainBase".equals(clazz.getName())) {
            return TrainSpatialTracker.getTrainsWithinAABB(world, aabb);
        }
        return world.getEntitiesWithinAABB(clazz, aabb);
    }
}
