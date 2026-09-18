package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.PartsRenderer;
import jp.ngt.ngtlib.util.NGTUtilClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * S-2: ModelObject.renderWithTexture() での {@code Arrays.stream()} + ラムダによる
 * 毎フレームオブジェクト生成を排除する Mixin。
 *
 * <p>元の実装:
 * <pre>Arrays.stream(this.textures).filter(Objects::nonNull).forEach(texture -> { ... });</pre>
 * を通常の for ループに置き換えることで、ストリーム生成コストをゼロにする。</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = ModelObject.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class ModelObjectRenderMixin {

    @Shadow
    public TextureSet[] textures;

    @Shadow
    public PartsRenderer renderer;

    @Shadow
    private boolean useTexture;

    /**
     * @author CrossTie
     * @reason Arrays.stream() を for ループに置換してアロケーションをなくす
     */
    @Overwrite(remap = false)
    public void renderWithTexture(Object entity, int pass, float par3) {
        for (TextureSet texture : this.textures) {
            if (texture == null) continue;

            if (this.useTexture) {
                if (pass == 0) {
                    NGTUtilClient.bindTexture(texture.material.texture);
                } else if (pass == 1) {
                    if (!texture.doAlphaBlend) {
                        continue;
                    }
                    NGTUtilClient.bindTexture(texture.material.texture);
                } else {
                    if (texture.subTextures == null) {
                        continue;
                    }
                    NGTUtilClient.bindTexture(texture.subTextures[pass - 2]);
                }
            }
            this.renderer.currentMatId = texture.material.id;
            this.renderer.render(entity, pass, par3);
        }
    }
}
