package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailMapSlope;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RailMap.class, RailMapSlope.class}, remap = false)
public class RailMapChunkLoadMixin {

    // Target setRail and breakRail. Since breakRail is not in RailMapSlope, we use require = 0 for breakRail if needed,
    // or just separate them. Actually, Spongepowered Mixin allows multiple targets even if some are missing in some classes
    // if we use require = 0, but it's cleaner to just use two @Injects.
    // Wait, the safest way is to provide exact methods.
    
    @Inject(method = "setRail(Lnet/minecraft/world/World;Lnet/minecraft/block/Block;IIILjp/ngt/rtm/rail/RailProperty;)V", at = @At("HEAD"), require = 0)
    private void crosstie$onSetRailStart(World world, Object b, int x, int y, int z, Object p, CallbackInfo ci) {
        if (world instanceof WorldServer) {
            ((ChunkProviderServer) world.getChunkProvider()).loadChunkOnProvideRequest = true;
        }
    }

    @Inject(method = "setRail(Lnet/minecraft/world/World;Lnet/minecraft/block/Block;IIILjp/ngt/rtm/rail/RailProperty;)V", at = @At("RETURN"), require = 0)
    private void crosstie$onSetRailEnd(World world, Object b, int x, int y, int z, Object p, CallbackInfo ci) {
        if (world instanceof WorldServer) {
            ((ChunkProviderServer) world.getChunkProvider()).loadChunkOnProvideRequest = false;
        }
    }

    @Inject(method = "breakRail(Lnet/minecraft/world/World;Ljp/ngt/rtm/rail/RailProperty;Ljp/ngt/rtm/rail/TileEntityLargeRailCore;)V", at = @At("HEAD"), require = 0)
    private void crosstie$onBreakRailStart(World world, Object p, Object c, CallbackInfo ci) {
        if (world instanceof WorldServer) {
            ((ChunkProviderServer) world.getChunkProvider()).loadChunkOnProvideRequest = true;
        }
    }

    @Inject(method = "breakRail(Lnet/minecraft/world/World;Ljp/ngt/rtm/rail/RailProperty;Ljp/ngt/rtm/rail/TileEntityLargeRailCore;)V", at = @At("RETURN"), require = 0)
    private void crosstie$onBreakRailEnd(World world, Object p, Object c, CallbackInfo ci) {
        if (world instanceof WorldServer) {
            ((ChunkProviderServer) world.getChunkProvider()).loadChunkOnProvideRequest = false;
        }
    }
}
