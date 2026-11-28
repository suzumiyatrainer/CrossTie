package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntitySignalConverter;
import jp.ngt.rtm.electric.TileEntitySignalConverter.TileEntitySC_Wireless;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * 3-5: {@link TileEntitySC_Wireless} の {@code ADAPTER_MAP} 永続メモリリークを修正し、
 * 毎 tick の {@code ForgeChunkManager.forceChunk} 連打を状態変化時のみに限定する Mixin。
 */
@Mixin(TileEntitySC_Wireless.class)
public abstract class TileEntitySCWirelessOptimizationMixin extends TileEntitySignalConverter {

    @Shadow(remap = false)
    @Final
    private static Map<Integer, List<TileEntitySC_Wireless>> ADAPTER_MAP;

    @Shadow(remap = false)
    private int prevChannel;

    @Shadow(remap = false)
    private boolean finishSetup;

    @Shadow(remap = false)
    public abstract int getChannel();

    @Shadow(remap = false)
    public abstract int getChunkLoadRange();

    @Shadow(remap = false)
    private void releaseTicket() {
    }

    @Shadow(remap = false)
    private void updateChunks() {
    }

    @Unique
    private int crosstie$lastLoadedRange = Integer.MIN_VALUE;

    @Unique
    private void crosstie$unregisterFromAdapterMap() {
        TileEntitySC_Wireless self = (TileEntitySC_Wireless) (Object) this;
        List<TileEntitySC_Wireless> listPrev = ADAPTER_MAP.get(this.prevChannel);
        if (listPrev != null) {
            listPrev.remove(self);
            if (listPrev.isEmpty()) {
                ADAPTER_MAP.remove(this.prevChannel);
            }
        }
        int curCh = this.getChannel();
        if (curCh != this.prevChannel) {
            List<TileEntitySC_Wireless> listCur = ADAPTER_MAP.get(curCh);
            if (listCur != null) {
                listCur.remove(self);
                if (listCur.isEmpty()) {
                    ADAPTER_MAP.remove(curCh);
                }
            }
        }
    }

    @Inject(method = "invalidate", at = @At("TAIL"))
    private void crosstie$onInvalidate(CallbackInfo ci) {
        this.crosstie$unregisterFromAdapterMap();
        this.crosstie$lastLoadedRange = Integer.MIN_VALUE;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.crosstie$unregisterFromAdapterMap();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.releaseTicket();
        }
        this.crosstie$lastLoadedRange = Integer.MIN_VALUE;
    }

    @Inject(method = "validate", at = @At("TAIL"))
    private void crosstie$onValidate(CallbackInfo ci) {
        TileEntitySC_Wireless self = (TileEntitySC_Wireless) (Object) this;
        int ch = this.getChannel();
        List<TileEntitySC_Wireless> list = ADAPTER_MAP.get(ch);
        if (list == null) {
            list = new java.util.ArrayList<TileEntitySC_Wireless>();
            ADAPTER_MAP.put(ch, list);
        }
        if (!list.contains(self)) {
            list.add(self);
        }
        this.prevChannel = ch;
        this.crosstie$lastLoadedRange = this.finishSetup ? this.getChunkLoadRange() : Integer.MIN_VALUE;
    }

    @Inject(method = "setSignalLevel(II)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetSignalLevel(int par1, int par2, CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            int newRange = this.getChunkLoadRange();
            if (newRange != this.crosstie$lastLoadedRange) {
                this.finishSetup = false;
                this.updateChunks();
                this.crosstie$lastLoadedRange = newRange;
            }
        }
    }

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$skipRedundantForceChunk(CallbackInfo ci) {
        if (this.worldObj == null || this.worldObj.isRemote) {
            ci.cancel();
            return;
        }
        int range = this.getChunkLoadRange();
        if (range <= 0) {
            if (this.crosstie$lastLoadedRange == 0) {
                ci.cancel();
                return;
            }
            this.crosstie$lastLoadedRange = 0;
            return;
        }
        if (this.finishSetup && this.crosstie$lastLoadedRange == range) {
            ci.cancel();
            return;
        }
        this.crosstie$lastLoadedRange = range;
    }
}
