package net.suzumiya.crosstie.mixins.mcte.late;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.mcte.world.MCTEWorld;
import jp.ngt.ngtlib.world.NGTWorld;
import net.minecraft.world.EnumSkyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = MCTEWorld.class, remap = false)
public abstract class McteWorldAngelicaLightMixin {

    /**
     * @author suzumiya
     * @reason Angelica環境下で複合ライトマップ座標を実世界から確実に同期する
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public int func_72802_i(int x, int y, int z, int defaultValue) {
        NGTWorld self = (NGTWorld) (Object) this;
        // ミニチュアが配置されている実世界の座標を取得
        int sampleX = self.posX;
        int sampleY = self.posY + 1; // 足元ではなく1ブロック上をサンプリングして安定させる
        int sampleZ = self.posZ;

        if (self.world == null) {
            return defaultValue;
        }

        // 実世界のライトマップ（ブロックライト・スカイライト・動的光源すべて込み）を直接取得して返す
        return self.world.getLightBrightnessForSkyBlocks(sampleX, sampleY, sampleZ, defaultValue);
    }

    /**
     * @author suzumiya
     * @reason 個別のライト値（ブロックライト）要求に対しても実世界の値を返す
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public int getSavedLightValue(EnumSkyBlock type, int x, int y, int z) {
        NGTWorld self = (NGTWorld) (Object) this;
        if (self.world == null)
            return 0;

        // ボクセル内部の座標ではなく、ミニチュアが置かれている実世界の値をそのまま返す
        return self.world.getSavedLightValue(type, self.posX, self.posY + 1, self.posZ);
    }
}