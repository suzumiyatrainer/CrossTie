package net.suzumiya.crosstie.mixins.mcte;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.mcte.world.MCTEWorld;
import jp.ngt.ngtlib.world.NGTWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = MCTEWorld.class, remap = false)
public abstract class McteWorldOptifineLightMixin {

    /**
     * ミニチュア内部の明るさ取得処理（非AO）。
     * 実行時のターゲットメソッド名である Srg 名（func_72802_i）でオーバーライドします。
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public int func_72802_i(int x, int y, int z, int defaultValue) {
        NGTWorld self = (NGTWorld) (Object) this;
        return self.world.getLightBrightnessForSkyBlocks(self.posX, self.posY, self.posZ, defaultValue);
    }

    /**
     * ミニチュア内部の明るさ取得処理（AO）。
     * ターゲットメソッド名である Srg 名（func_72801_o）で注入します。
     */
    @SideOnly(Side.CLIENT)
    public float func_72801_o(int x, int y, int z) {
        NGTWorld self = (NGTWorld) (Object) this;
        return self.world.getLightBrightness(self.posX, self.posY, self.posZ);
    }

    /**
     * ブロックライト値の取得処理。
     * ターゲットメソッド名である Srg 名（func_72957_L）で注入します。
     */
    @SideOnly(Side.CLIENT)
    public int func_72957_L(int x, int y, int z) {
        NGTWorld self = (NGTWorld) (Object) this;
        return self.world.getBlockLightValue(self.posX, self.posY, self.posZ);
    }
}
