package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.PartsRenderer;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.utils.CrossTiePartsRenderContext;
import net.suzumiya.crosstie.utils.texture.CrossTieGifTextureManager;
import net.suzumiya.crosstie.utils.texture.CrossTieTextureOverrideManager;
import net.suzumiya.crosstie.utils.texture.GifTextureRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import jp.ngt.ngtlib.util.NGTUtilClient;

@Mixin(value = ModelObject.class, remap = false)
public class ModelObjectMixin {

    @Shadow
    public TextureSet[] textures;

    @Shadow
    public boolean useTexture;

    @SuppressWarnings("rawtypes")
    @Shadow
    public PartsRenderer renderer;

    /**
     * @author SuzumiyaTrainer
     * @reason Intercept texture binding for dynamic overrides and automatic GIF
     *         animation support.
     */
    @SuppressWarnings("unchecked")
    @Overwrite
    public void renderWithTexture(Object entity, int pass, float par3) {
        for (TextureSet texture : this.textures) {
            if (texture == null)
                continue;

            if (this.useTexture) {
                ResourceLocation targetLoc = null;
                boolean shouldBind = false;

                if (pass == 0) {
                    targetLoc = texture.material.texture;
                    shouldBind = true;
                } else if (pass == 1) {
                    if (texture.doAlphaBlend) {
                        targetLoc = texture.material.texture;
                        shouldBind = true;
                    } else {
                        // 元のModelObject: pass==1でdoAlphaBlend==falseの場合はcontinue
                        continue;
                    }
                } else {
                    if (texture.subTextures != null) {
                        targetLoc = texture.subTextures[pass - 2];
                        shouldBind = true;
                    } else {
                        // 元のModelObject: pass>=2でsubTexturesがない場合はcontinue
                        continue;
                    }
                }

                boolean handled = false;
                // CrossTiePartsRenderContextの代わりに、renderWithTextureに渡されたentityを直接使う
                // (Angelica等がdoRenderを経由せず直接renderWithTextureを呼ぶ場合に対応)
                Object vehicleObj = (entity != null) ? entity : CrossTiePartsRenderContext.getCurrentVehicle();
                if (vehicleObj != null) {
                    int objId = CrossTieTextureOverrideManager.getObjectId(vehicleObj);
                    Object override = CrossTieTextureOverrideManager.getOverride(objId, (byte) texture.material.id);
                    if (override instanceof ResourceLocation) {
                        ResourceLocation loc = (ResourceLocation) override;
                        // overrideがある場合、passに関係なく強制バインド（発光パス等でも適用）
                        if (loc.getResourcePath().toLowerCase().endsWith(".gif")) {
                            GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(loc);
                            if (record != null) {
                                int tex = record.getCurrentTextureId(System.currentTimeMillis());
                                if (tex != 0) {
                                    CrossTieGifTextureManager.bindTexture(tex);
                                    handled = true;
                                }
                            }
                        } else {
                            NGTUtilClient.bindTexture(loc);
                            handled = true;
                        }
                    } else if (override instanceof GifTextureRecord) {
                        int tex = ((GifTextureRecord) override).getCurrentTextureId(System.currentTimeMillis());
                        if (tex != 0) {
                            CrossTieGifTextureManager.bindTexture(tex);
                            handled = true;
                        }
                    }
                }

                // overrideがなければ、RTMの通常のテクスチャバインド処理
                if (!handled && shouldBind && targetLoc != null) {
                    if (targetLoc.getResourcePath().toLowerCase().endsWith(".gif")) {
                        GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(targetLoc);
                        if (record != null) {
                            int tex = record.getCurrentTextureId(System.currentTimeMillis());
                            if (tex != 0) {
                                CrossTieGifTextureManager.bindTexture(tex);
                                handled = true;
                            }
                        }
                    }

                    if (!handled) {
                        NGTUtilClient.bindTexture(targetLoc);
                    }
                }
            }
            this.renderer.currentMatId = texture.material.id;
            this.renderer.render(entity, pass, par3);

        }
    }
}
