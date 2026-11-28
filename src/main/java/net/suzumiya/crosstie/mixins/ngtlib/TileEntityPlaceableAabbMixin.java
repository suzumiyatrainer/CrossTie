package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.block.TileEntityCustom;
import jp.ngt.ngtlib.block.TileEntityPlaceable;
import net.minecraft.nbt.NBTTagCompound;
import net.suzumiya.crosstie.accessors.ngtlib.IAabbDirtyHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3-6: {@link TileEntityPlaceable} の全位置・回転・スケール変更および NBT 読込を検知して
 * 有限 AABB キャッシュの dirty フラグを立てる Mixin。
 */
@Mixin(TileEntityPlaceable.class)
public abstract class TileEntityPlaceableAabbMixin extends TileEntityCustom implements IAabbDirtyHolder {

    @Unique
    private boolean crosstie$aabbDirty = true;

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Inject(method = "setOffset(FFFZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetOffset(float x, float y, float z, boolean sync, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Inject(method = "setRotationRoll(FZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetRoll(float par1, boolean synch, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Inject(method = "setRotationPitch(FZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetPitch(float par1, boolean synch, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Inject(method = "setRotationYaw(FZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetYaw(float par1, boolean synch, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Inject(method = "setScale(FZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetScale(float par1, boolean synch, CallbackInfo ci) {
        this.crosstie$aabbDirty = true;
    }

    @Override
    public boolean crosstie$isAabbDirty() {
        return this.crosstie$aabbDirty;
    }

    @Override
    public void crosstie$markAabbDirty() {
        this.crosstie$aabbDirty = true;
    }

    @Override
    public void crosstie$clearAabbDirty() {
        this.crosstie$aabbDirty = false;
    }
}
