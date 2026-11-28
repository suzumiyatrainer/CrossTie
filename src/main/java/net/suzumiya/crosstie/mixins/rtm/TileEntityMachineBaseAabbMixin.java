package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.block.TileEntityPlaceable;
import jp.ngt.rtm.block.tileentity.TileEntityCrossingGate;
import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import jp.ngt.rtm.modelpack.cfg.MachineConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetMachine;
import jp.ngt.rtm.modelpack.modelset.ModelSetMachineClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.accessors.ngtlib.IAabbDirtyHolder;
import net.suzumiya.crosstie.utils.AabbCacheHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3-6: {@link TileEntityMachineBase} の {@code getRenderBoundingBox()} を
 * Arvo 法 0 アロケーション自前キャッシュ化する Mixin。
 *
 * <p>既存の {@code TileEntityCrossingGateNoCullingMixin} と競合しないよう {@code @Inject(HEAD, cancellable=true)}
 * とし、{@code CrossTieConfig.disableSignalCulling && this instanceof TileEntityCrossingGate} 時はスルーする。</p>
 */
@Mixin(TileEntityMachineBase.class)
public abstract class TileEntityMachineBaseAabbMixin extends TileEntityPlaceable {

    @Shadow(remap = false)
    public abstract ModelSetMachine getModelSet();

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
    @SideOnly(Side.CLIENT)
    @Unique
    private int crosstie$lastAabbMeta = Integer.MIN_VALUE;

    @Inject(method = "setModelName(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetModelName(String par1, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onMachineReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    @SideOnly(Side.CLIENT)
    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$getCachedMachineAABB(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (CrossTieConfig.disableSignalCulling && ((Object) this) instanceof TileEntityCrossingGate) {
            return; // 既存 TileEntityCrossingGateNoCullingMixin の INFINITE_EXTENT_AABB を優先
        }

        IAabbDirtyHolder dirtyHolder = (IAabbDirtyHolder) this;
        int meta = (this.worldObj != null) ? this.getBlockMetadata() : 0;
        boolean changed = (this.xCoord != this.crosstie$lastAabbX
                || this.yCoord != this.crosstie$lastAabbY
                || this.zCoord != this.crosstie$lastAabbZ
                || meta != this.crosstie$lastAabbMeta);

        if (!changed && !dirtyHolder.crosstie$isAabbDirty() && this.crosstie$cachedRenderAABB != null) {
            cir.setReturnValue(this.crosstie$cachedRenderAABB);
            return;
        }

        ModelSetMachine modelSet = this.getModelSet();
        if (modelSet == null || modelSet.isDummy()) {
            this.crosstie$cachedRenderAABB = AabbCacheHelper.setFallbackBounds(
                    this.crosstie$cachedRenderAABB,
                    this.xCoord, this.yCoord, this.zCoord,
                    this.getOffsetX(), this.getOffsetY(), this.getOffsetZ(),
                    this.getScale());
            cir.setReturnValue(this.crosstie$cachedRenderAABB);
            return;
        }

        MachineConfig cfg = modelSet.getConfig();
        jp.ngt.ngtlib.renderer.model.IModelNGT model =
                (modelSet instanceof ModelSetMachineClient && ((ModelSetMachineClient) modelSet).modelObj != null)
                        ? ((ModelSetMachineClient) modelSet).modelObj.model
                        : null;

        this.crosstie$cachedRenderAABB = AabbCacheHelper.computeOrientedBounds(
                this.crosstie$cachedRenderAABB,
                this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D,
                this.getOffsetX(), this.getOffsetY(), this.getOffsetZ(),
                this.getRotationYaw(), this.getRotationPitch(), this.getRotationRoll(),
                this.getScale(),
                cfg != null ? cfg.renderAABB : null,
                model,
                cfg != null && cfg.rotateByMetadata,
                meta);
        this.crosstie$lastAabbX = this.xCoord;
        this.crosstie$lastAabbY = this.yCoord;
        this.crosstie$lastAabbZ = this.zCoord;
        this.crosstie$lastAabbMeta = meta;
        dirtyHolder.crosstie$clearAabbDirty();
        cir.setReturnValue(this.crosstie$cachedRenderAABB);
    }
}
