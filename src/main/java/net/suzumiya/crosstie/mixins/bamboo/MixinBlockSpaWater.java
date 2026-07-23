package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
}