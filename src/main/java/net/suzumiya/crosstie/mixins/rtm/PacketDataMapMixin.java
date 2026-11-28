package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.world.World;
import net.suzumiya.crosstie.utils.DataMapSyncContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code PacketDataMap.apply} 実行中のコンテキストフラグを管理する Mixin。
 */
@Mixin(targets = "jp.ngt.rtm.network.PacketDataMap", remap = false)
public abstract class PacketDataMapMixin {

    @Inject(method = "apply", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$onApplyHead(World world, boolean onClient, CallbackInfo ci) {
        DataMapSyncContext.enterPacketApply();
    }

    @Inject(method = "apply", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$onApplyReturn(World world, boolean onClient, CallbackInfo ci) {
        DataMapSyncContext.exitPacketApply();
    }
}
