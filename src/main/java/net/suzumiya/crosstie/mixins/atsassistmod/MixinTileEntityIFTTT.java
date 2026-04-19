package net.suzumiya.crosstie.mixins.atsassistmod;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.IFTTTRenderSnapshotCache;
import net.suzumiya.crosstie.util.TrainSpatialTracker;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Mixin(targets = "jp.kaiz.atsassistmod.block.tileentity.TileEntityIFTTT", remap = false)
public abstract class MixinTileEntityIFTTT {

    @Unique
    private static final ThreadLocal<ArrayList<Entity>> CROSSTIE_RESULT_CACHE =
            new ThreadLocal<ArrayList<Entity>>() {
                @Override
                protected ArrayList<Entity> initialValue() {
                    return new ArrayList<Entity>(16);
                }
            };

    @Unique
    private static final ThreadLocal<Set<Entity>> CROSSTIE_VISITED_CACHE =
            new ThreadLocal<Set<Entity>>() {
                @Override
                protected Set<Entity> initialValue() {
                    return Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
                }
            };

    @Redirect(
        method = "updateEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntitiesWithinAABB(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;")
    )
    private List<?> crosstie$redirectGetEntitiesWithinAABB(World world, Class<?> clazz, AxisAlignedBB aabb) {
        if (clazz != null && "jp.ngt.rtm.entity.train.EntityTrainBase".equals(clazz.getName())) {
            ArrayList<Entity> result = CROSSTIE_RESULT_CACHE.get();
            Set<Entity> visited = CROSSTIE_VISITED_CACHE.get();
            TrainSpatialTracker.fillTrainsWithinAABB(world, aabb, result, visited);
            return result;
        }
        return world.getEntitiesWithinAABB(clazz, aabb);
    }

    @Inject(method = "updateEntity", at = @At("TAIL"), remap = false)
    private void crosstie$captureImmutableIfTTTSnapshot(CallbackInfo ci) {
        if (!CrossTieConfig.enableAngelicaIfTTTCache) {
            return;
        }
        IFTTTRenderSnapshotCache.updateFromTile((net.minecraft.tileentity.TileEntity) (Object) this);
    }
}
