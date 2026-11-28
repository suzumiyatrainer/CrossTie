package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.minecraft.nbt.NBTTagCompound;
import net.suzumiya.crosstie.accessors.rtm.IWireDisplayListHolder;
import net.suzumiya.crosstie.client.WireDisplayListCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5-3: {@link TileEntityElectricalWiring} に {@link WireDisplayListCache} を保持し、
 * 接続変更（{@code readFromNBT}）・破棄（{@code invalidate}）・チャンクアンロード（{@code onChunkUnload}）時に
 * DisplayList を確実に解放するクライアント限定 Mixin。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = TileEntityElectricalWiring.class, remap = true)
public abstract class TileEntityEWDisplayListCleanupMixin implements IWireDisplayListHolder {

    @Unique
    private Object crosstie$wireCache;

    @Override
    public Object crosstie$getWireCache() {
        return this.crosstie$wireCache;
    }

    @Override
    public void crosstie$setWireCache(Object cache) {
        this.crosstie$wireCache = cache;
    }

    @Override
    public void crosstie$releaseWireCache() {
        if (this.crosstie$wireCache instanceof WireDisplayListCache) {
            ((WireDisplayListCache) this.crosstie$wireCache).releaseAll();
            this.crosstie$wireCache = null;
        }
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$releaseWireCache();
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void crosstie$onInvalidate(CallbackInfo ci) {
        this.crosstie$releaseWireCache();
    }

    @Inject(method = "onChunkUnload", at = @At("HEAD"))
    private void crosstie$onChunkUnload(CallbackInfo ci) {
        this.crosstie$releaseWireCache();
    }
}
