package net.suzumiya.crosstie.mixins.webctc;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.utils.TrackOccupancyTracker;
import org.webctc.cache.rail.RailCacheData;
import org.webctc.common.types.PosInt;
import org.webctc.common.types.rail.LargeRailData;

@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityLargeRailCoreOccupancyMixin extends TileEntity {

    @Shadow public boolean colliding;

    @Inject(method = "updateEntity", at = @At("HEAD"))
    public void onUpdateEntityHead(CallbackInfo ci) {
        if (!this.worldObj.isRemote) {
            PosInt pos = new PosInt(this.xCoord, this.yCoord, this.zCoord);
            Map<PosInt, LargeRailData> cache = RailCacheData.Companion.getRailMapCache();
            LargeRailData cachedData = cache.get(pos);
            
            if (cachedData != null) {
                // Check if WebCTC's truth differs from reality
                if (cachedData.isTrainOnRail() != this.colliding) {
                    TrackOccupancyTracker.latestStates.put(pos, this.colliding);
                }
            } else if (this.colliding) {
                // If it's not in the cache but a train is on it, we queue it to ensure WebCTC picks it up
                TrackOccupancyTracker.latestStates.put(pos, this.colliding);
            }
        }
    }
}
