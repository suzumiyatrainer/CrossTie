package net.suzumiya.crosstie.mixins.mcte;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.mcte.block.TileEntityMiniature;
import jp.ngt.mcte.item.ItemMiniature.MiniatureMode;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import jp.ngt.ngtlib.block.TileEntityPlaceable;
import jp.ngt.ngtlib.renderer.DisplayList;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.accessors.mcte.IMiniatureOptimizationAccessor;
import net.suzumiya.crosstie.accessors.ngtlib.IAabbDirtyHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Phase 5-1: {@link TileEntityMiniature} の有限 AABB キャッシュ（全 offset / rotation / attachSide 対応）と
 * 空 pass 判定キャッシュ・pass 別 dirty 管理。
 */
@Mixin(value = TileEntityMiniature.class, remap = true)
public abstract class TileEntityMiniatureOptimizationMixin extends TileEntityPlaceable
        implements IMiniatureOptimizationAccessor {

    @Shadow(remap = false)
    public NGTObject blocksObject;

    @Shadow(remap = false)
    public float scale;

    @Shadow(remap = false)
    public float offsetX;

    @Shadow(remap = false)
    public float offsetY;

    @Shadow(remap = false)
    public float offsetZ;

    @Shadow(remap = false)
    public byte attachSide;

    @Shadow(remap = false)
    public DisplayList[] glLists;

    @Unique
    private boolean crosstie$passScanValid = false;

    @Unique
    private boolean crosstie$hasPass0Blocks = true;

    @Unique
    private boolean crosstie$hasPass1Blocks = true;

    @Unique
    private boolean crosstie$hasInnerObjects = true;

    @Unique
    private final boolean[] crosstie$passDirty = new boolean[] { true, true };

    @Unique
    private AxisAlignedBB crosstie$cachedRenderAabb;

    @Unique
    private boolean crosstie$renderAabbDirty = true;

    @Unique
    private void crosstie$ensurePassScan() {
        if (this.crosstie$passScanValid) {
            return;
        }
        NGTObject ngto = this.blocksObject;
        if (ngto == null || ngto.blockList == null) {
            this.crosstie$hasPass0Blocks = false;
            this.crosstie$hasPass1Blocks = false;
            this.crosstie$hasInnerObjects = false;
            this.crosstie$passScanValid = true;
            return;
        }

        boolean p0 = false;
        boolean p1 = false;
        boolean hasTE = false;
        List<BlockSet> list = ngto.blockList;
        for (int i = 0, len = list.size(); i < len; ++i) {
            BlockSet set = list.get(i);
            if (set == null || set.block == null || set.block.getMaterial() == Material.air) {
                continue;
            }
            int rp = set.block.getRenderBlockPass();
            if (rp == 0) {
                p0 = true;
            } else if (rp == 1) {
                p1 = true;
            }
            if (!hasTE && set.block.hasTileEntity(set.metadata)) {
                hasTE = true;
            }
            if (p0 && p1 && hasTE) {
                break;
            }
        }

        boolean hasEntities = ngto.getEntityList() != null && ngto.getEntityList().tagCount() > 0;
        this.crosstie$hasPass0Blocks = p0;
        this.crosstie$hasPass1Blocks = p1;
        this.crosstie$hasInnerObjects = hasTE || hasEntities;
        this.crosstie$passScanValid = true;
    }

    @Override
    public boolean crosstie$hasPassContent(int pass) {
        this.crosstie$ensurePassScan();
        if (pass == 0) {
            return this.crosstie$hasPass0Blocks || this.crosstie$hasInnerObjects;
        }
        if (pass == 1) {
            return this.crosstie$hasPass1Blocks || this.crosstie$hasInnerObjects;
        }
        return false;
    }

    @Override
    public boolean crosstie$hasBlockContent(int pass) {
        this.crosstie$ensurePassScan();
        if (pass == 0) {
            return this.crosstie$hasPass0Blocks;
        }
        if (pass == 1) {
            return this.crosstie$hasPass1Blocks;
        }
        return false;
    }

    @Override
    public void crosstie$invalidateMiniatureCache() {
        this.crosstie$passScanValid = false;
        this.crosstie$renderAabbDirty = true;
        this.crosstie$passDirty[0] = true;
        this.crosstie$passDirty[1] = true;
    }

    @Override
    public boolean crosstie$isPassDirty(int pass) {
        return pass >= 0 && pass < 2 && this.crosstie$passDirty[pass];
    }

    @Override
    public void crosstie$markAllPassesDirty() {
        this.crosstie$passDirty[0] = true;
        this.crosstie$passDirty[1] = true;
        this.crosstie$passScanValid = false;
    }

    @Override
    public void crosstie$clearPassDirty(int pass) {
        if (pass >= 0 && pass < 2) {
            this.crosstie$passDirty[pass] = false;
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$invalidateMiniatureCache();
    }

    @Inject(method = "setBlockState", at = @At("TAIL"), remap = false)
    private void crosstie$onSetBlockState(NGTObject par1, float par2, float x, float y, float z, MiniatureMode par6,
            CallbackInfo ci) {
        this.crosstie$invalidateMiniatureCache();
    }

    @Inject(method = "setRotation(FZ)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetRotation(float par1, boolean synch, CallbackInfo ci) {
        this.crosstie$renderAabbDirty = true;
    }

    @Inject(method = "invalidate", at = @At("TAIL"))
    private void crosstie$onInvalidate(CallbackInfo ci) {
        this.glLists = null;
    }

    @Inject(method = "onChunkUnload", at = @At("TAIL"))
    private void crosstie$onChunkUnload(CallbackInfo ci) {
        this.glLists = null;
    }

    @SideOnly(Side.CLIENT)
    @Inject(method = "shouldRenderInPass", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipEmptyPass(int pass, CallbackInfoReturnable<Boolean> cir) {
        if (this.blocksObject == null) {
            cir.setReturnValue(false);
            return;
        }
        if (pass == 0 || pass == 1) {
            cir.setReturnValue(this.crosstie$hasPassContent(pass));
        } else {
            cir.setReturnValue(false);
        }
    }

    @SideOnly(Side.CLIENT)
    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$computeExactFiniteRenderAabb(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (this.blocksObject == null) {
            return;
        }
        boolean placeableDirty = (this instanceof IAabbDirtyHolder) && ((IAabbDirtyHolder) this).crosstie$isAabbDirty();
        if (this.crosstie$cachedRenderAabb != null && !this.crosstie$renderAabbDirty && !placeableDirty) {
            cir.setReturnValue(this.crosstie$cachedRenderAabb);
            return;
        }

        double sc = this.scale;
        double halfX = (double) this.blocksObject.xSize * 0.5D;
        double sizeY = (double) this.blocksObject.ySize;
        double halfZ = (double) this.blocksObject.zSize * 0.5D;

        double mOffX = this.offsetX;
        double mOffY = this.offsetY;
        double mOffZ = this.offsetZ;

        double pOffX = this.getOffsetX();
        double pOffY = this.getOffsetY();
        double pOffZ = this.getOffsetZ();

        double yawRad = Math.toRadians(this.getRotation());
        double yawCos = Math.cos(yawRad);
        double yawSin = Math.sin(yawRad);
        byte side = this.attachSide;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        // MCTEWorld.getLightBrightnessForSkyBlocks と完全一致する 8 頂点変換
        for (int corner = 0; corner < 8; ++corner) {
            double lx = (((corner & 1) == 0 ? -halfX : halfX) * sc) + mOffX;
            double ly = (((corner & 2) == 0 ? 0.0D : sizeY) * sc) + mOffY;
            double lz = (((corner & 4) == 0 ? -halfZ : halfZ) * sc) + mOffZ;

            double rx = lx * yawCos + lz * yawSin + pOffX;
            double ry = ly + pOffY - 0.5D;
            double rz = lz * yawCos - lx * yawSin + pOffZ;

            double ax = rx;
            double ay = ry;
            double az = rz;
            switch (side) {
                case 0:
                    ax = -rx;
                    ay = -ry;
                    break;
                case 2:
                    ay = rz;
                    az = -ry;
                    break;
                case 3:
                    ay = -rz;
                    az = ry;
                    break;
                case 4:
                    ax = -ry;
                    ay = rx;
                    break;
                case 5:
                    ax = ry;
                    ay = -rx;
                    break;
                default:
                    break;
            }

            if (ax < minX) minX = ax;
            if (ay < minY) minY = ay;
            if (az < minZ) minZ = az;
            if (ax > maxX) maxX = ax;
            if (ay > maxY) maxY = ay;
            if (az > maxZ) maxZ = az;
        }

        double baseX = (double) this.xCoord + 0.5D;
        double baseY = (double) this.yCoord + 0.5D;
        double baseZ = (double) this.zCoord + 0.5D;
        double margin = 0.0625D;

        this.crosstie$cachedRenderAabb = AxisAlignedBB.getBoundingBox(
                baseX + minX - margin,
                baseY + minY - margin,
                baseZ + minZ - margin,
                baseX + maxX + margin,
                baseY + maxY + margin,
                baseZ + maxZ + margin
        );
        this.crosstie$renderAabbDirty = false;
        if (this instanceof IAabbDirtyHolder) {
            ((IAabbDirtyHolder) this).crosstie$clearAabbDirty();
        }
        cir.setReturnValue(this.crosstie$cachedRenderAabb);
    }
}
