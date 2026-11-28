package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.block.TileEntityPlaceable;
import jp.ngt.rtm.block.tileentity.TileEntityFlag;
import jp.ngt.rtm.modelpack.texture.FlagProperty;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.accessors.ngtlib.IAabbDirtyHolder;
import net.suzumiya.crosstie.utils.AabbCacheHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3-6: {@link TileEntityFlag#getRenderBoundingBox()} の {@code INFINITE_EXTENT_AABB} を廃止し、
 * 旗の実寸＋波打ち振幅＋任意回転を包含する有限 AABB キャッシュを返す Mixin。
 */
@Mixin(TileEntityFlag.class)
public abstract class TileEntityFlagAabbMixin extends TileEntityPlaceable {

    @Shadow(remap = false)
    public abstract FlagProperty getProperty();

    @SideOnly(Side.CLIENT)
    @Unique
    private AxisAlignedBB crosstie$cachedRenderAABB;

    @SideOnly(Side.CLIENT)
    @Unique
    private int crosstie$lastAabbX = Integer.MIN_VALUE;
    @SideOnly(Side.CLIENT)
    @Unique
    private int crosstie$lastAabbY = Integer.MIN_VALUE;
    @SideOnly(Side.CLIENT)
    @Unique
    private int crosstie$lastAabbZ = Integer.MIN_VALUE;

    @Inject(method = "setTexture(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetTexture(String name, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onFlagReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    /**
     * @author CrossTie
     * @reason INFINITE_EXTENT_AABB を廃止し、旗サイズと波打ち(±0.15)を包含する有限 AABB キャッシュを返す。
     */
    @SideOnly(Side.CLIENT)
    @Overwrite(remap = false)
    public AxisAlignedBB getRenderBoundingBox() {
        IAabbDirtyHolder dirtyHolder = (IAabbDirtyHolder) this;
        boolean posChanged = (this.xCoord != this.crosstie$lastAabbX
                || this.yCoord != this.crosstie$lastAabbY
                || this.zCoord != this.crosstie$lastAabbZ);

        if (!posChanged && !dirtyHolder.crosstie$isAabbDirty() && this.crosstie$cachedRenderAABB != null) {
            return this.crosstie$cachedRenderAABB;
        }

        FlagProperty prop = this.getProperty();
        double r;
        if (prop == null) {
            r = 4.0D;
        } else {
            double w = prop.width;
            double h = prop.height;
            r = Math.sqrt(w * w + h * h + 0.0225D) + 0.25D;
        }

        double cx = this.xCoord + 0.5D;
        double cy = this.yCoord + 1.0D;
        double cz = this.zCoord + 0.5D;
        this.crosstie$cachedRenderAABB = AabbCacheHelper.setOrCreate(
                this.crosstie$cachedRenderAABB,
                cx - r, cy - r, cz - r,
                cx + r, cy + r, cz + r);
        this.crosstie$lastAabbX = this.xCoord;
        this.crosstie$lastAabbY = this.yCoord;
        this.crosstie$lastAabbZ = this.zCoord;
        if (prop != null) {
            dirtyHolder.crosstie$clearAabbDirty();
        }
        return this.crosstie$cachedRenderAABB;
    }
}
