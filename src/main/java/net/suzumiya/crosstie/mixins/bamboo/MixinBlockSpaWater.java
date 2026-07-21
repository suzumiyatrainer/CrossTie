package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
@Mixin(targets = "ruby.bamboo.block.BlockSpaWater", remap = false)
public abstract class MixinBlockSpaWater extends Block {

    // 必須となる親クラスのコンストラクタ（コンパイルを通すため）
    protected MixinBlockSpaWater(Material material) {
        super(material);
    }

    /**
     * @author Suzumiya
     * @reason 温泉ブロックのマテリアルを強制的にバニラの水(Material.water)にすることで、
     *         OptiFineおよびシェーダーに「水」として認識させ、シェーダーの波・反射効果を適用します。
     */
    @Override
    @Overwrite
    public Material getMaterial() { // func_149688_o は 1.7.10における getMaterial() の難読化名です
        return Material.water; // field_151586_h は Material.water の難読化名です
    }
}