package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ruby.bamboo.tileentity.spa.ITileEntitySpa;
import ruby.bamboo.tileentity.spa.TileEntitySpaChild;

@Mixin(targets = "ruby.bamboo.block.BlockSpaWater", remap = false)
public class MixinBlockSpaWater {

    /**
     * 温泉ブロックのマテリアルを強制的にバニラの水(Material.water)にすることで、
     * OptiFineおよびシェーダーに「水」として認識させ、シェーダーの波・反射効果を適用します。
     */
    @Inject(method = {"getMaterial", "func_149688_o"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetMaterial(CallbackInfoReturnable<Material> cir) {
        cir.setReturnValue(Material.water);
    }

    /**
     * @author Antigravity
     * @reason Fix off-thread world access in Angelica chunk rendering by not relying on the child's worldObj.
     */
    @Overwrite
    public int func_149720_d(IBlockAccess blockAccess, int x, int y, int z) {
        TileEntity tile = blockAccess.getTileEntity(x, y, z);
        if (tile instanceof ITileEntitySpa) {
            ITileEntitySpa spa = (ITileEntitySpa) tile;
            if (spa instanceof TileEntitySpaChild) {
                int[] parentPos = ((TileEntitySpaChild) spa).getParentPosition();
                TileEntity parentTile = blockAccess.getTileEntity(parentPos[0], parentPos[1], parentPos[2]);
                if (parentTile instanceof ITileEntitySpa) {
                    return ((ITileEntitySpa) parentTile).getColor();
                }
            } else {
                return spa.getColor();
            }
        }
        return 16777215; // 0xFFFFFF
    }
}