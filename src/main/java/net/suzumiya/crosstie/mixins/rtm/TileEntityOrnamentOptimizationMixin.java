package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.block.TileEntityPlaceable;
import jp.ngt.rtm.block.tileentity.TileEntityOrnament;
import jp.ngt.rtm.modelpack.cfg.OrnamentConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetOrnament;
import jp.ngt.rtm.modelpack.modelset.ModelSetOrnamentClient;
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
 * 3-5 & 3-6: {@link TileEntityOrnament} 系の毎 tick 空回し廃止 ({@code canUpdate() = false}) と、
 * 毎フレーム Stream 5 本を生成していた {@code getRenderBoundingBox()} を Arvo 法 0 アロケーション自前キャッシュ化する Mixin。
 */
@Mixin(TileEntityOrnament.class)
public abstract class TileEntityOrnamentOptimizationMixin extends TileEntityPlaceable {

    @Shadow(remap = false)
    public abstract ModelSetOrnament getModelSet();

    @Shadow(remap = false)
    public abstract float getRandomScale();

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

    /**
     * Forge 追加メソッド ({@code remap = false})。全 Ornament サブクラスを毎 tick 更新リストから除外。
     * ({@code TileEntityPole} の旧データ移行は {@code TileEntityPoleOptimizationMixin.validate()} で実行)
     */
    @Override
    public boolean canUpdate() {
        return false;
    }

    @Inject(method = "setModelName(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetModelName(String par1, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    @Inject(method = "setModelNameNoSync(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetModelNameNoSync(String par1, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onOrnamentReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        ((IAabbDirtyHolder) this).crosstie$markAabbDirty();
    }

    /**
     * @author CrossTie
     * @reason 毎フレームの Stream 5本 + Vec3×4 生成を廃止し、scale/randomScale/sizeBox/全回転を反映した
     *         0 アロケーション自前キャッシュを返す。
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

        ModelSetOrnament modelSet = this.getModelSet();
        float totalScale = this.getScale() * this.getRandomScale();
        if (modelSet == null || modelSet.isDummy()) {
            // 未ロード時は有限フォールバックを返し、dirty を維持してロード完了時に即更新する
            this.crosstie$cachedRenderAABB = AabbCacheHelper.setFallbackBounds(
                    this.crosstie$cachedRenderAABB,
                    this.xCoord, this.yCoord, this.zCoord,
                    this.getOffsetX(), this.getOffsetY(), this.getOffsetZ(),
                    totalScale);
            return this.crosstie$cachedRenderAABB;
        }

        OrnamentConfig cfg = modelSet.getConfig();
        jp.ngt.ngtlib.renderer.model.IModelNGT model =
                (modelSet instanceof ModelSetOrnamentClient && ((ModelSetOrnamentClient) modelSet).model != null)
                        ? ((ModelSetOrnamentClient) modelSet).model.model
                        : null;

        this.crosstie$cachedRenderAABB = AabbCacheHelper.computeOrientedBounds(
                this.crosstie$cachedRenderAABB,
                this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D,
                this.getOffsetX(), this.getOffsetY(), this.getOffsetZ(),
                this.getRotationYaw(), this.getRotationPitch(), this.getRotationRoll(),
                totalScale,
                cfg != null ? cfg.renderAABB : null,
                model,
                false,
                0);
        this.crosstie$lastAabbX = this.xCoord;
        this.crosstie$lastAabbY = this.yCoord;
        this.crosstie$lastAabbZ = this.zCoord;
        dirtyHolder.crosstie$clearAabbDirty();
        return this.crosstie$cachedRenderAABB;
    }
}
