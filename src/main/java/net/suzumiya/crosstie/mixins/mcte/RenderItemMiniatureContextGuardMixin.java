package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCTE ミニチュアのアイテム/インベントリレンダリング時の GL ステート汚染を防ぐ。
 *
 * <p>renderItem の前後で GL ステートを保存・復元する。
 * renderBlocks は RenderItemMiniature の private メソッドなので直接注入せず、
 * McteMiniatureRenderContext を renderItem 全体でカバーすることで
 * GLHelperMixin の GL_COMPILE_AND_EXECUTE バイパスが機能するようにする。
 */
@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureContextGuardMixin {

    @Unique
    private static final ThreadLocal<RenderItemSavedState> CROSSTIE_SAVED_STATE =
            ThreadLocal.withInitial(RenderItemSavedState::new);

    @Inject(method = "renderItem", at = @At("HEAD"), remap = false)
    private void crosstie$captureRenderItemState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                 CallbackInfo ci) {
        RenderItemSavedState state = CROSSTIE_SAVED_STATE.get();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        state.lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        state.brightnessX = OpenGlHelper.lastBrightnessX;
        state.brightnessY = OpenGlHelper.lastBrightnessY;

        // テクスチャ行列リセット（Angelica の行列汚染対策）
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator.instance.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        // renderItem → renderBlocks の GL_COMPILE_AND_EXECUTE 昇格を有効にする
        McteMiniatureRenderContext.enter();
    }

    @Inject(method = "renderItem", at = @At("RETURN"), remap = false)
    private void crosstie$restoreRenderItemState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                 CallbackInfo ci) {
        RenderItemSavedState state = CROSSTIE_SAVED_STATE.get();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (state.lightmapEnabled) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, state.brightnessX, state.brightnessY);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        McteMiniatureRenderContext.exit();
    }

    @Unique
    private static final class RenderItemSavedState {
        boolean lightmapEnabled;
        float brightnessX;
        float brightnessY;
    }
}
