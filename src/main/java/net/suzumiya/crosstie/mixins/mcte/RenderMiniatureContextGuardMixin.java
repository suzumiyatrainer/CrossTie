package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCTE ミニチュアのブロック状態レンダリング時の GL ステート汚染を防ぐ。
 *
 * <p>renderTileEntityAt の前後で GL ステートを保存・復元し、
 * McteMiniatureRenderContext を有効化する。これにより AngelicaDisplayListManagerMixin
 * および GLHelperMixin の MCTE バイパスが正しく機能する。
 *
 * <p>注意: renderBlocks は RenderMiniature の private メソッドであるため、
 * @Inject による直接の注入は行わない。GL_COMPILE_AND_EXECUTE の昇格は
 * GLHelperMixin 側で処理する。
 */
@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureContextGuardMixin {

    @Unique
    private static final ThreadLocal<RenderMiniatureSavedState> CROSSTIE_SAVED_STATE =
            ThreadLocal.withInitial(RenderMiniatureSavedState::new);

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), remap = false)
    private void crosstie$prepareMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        RenderMiniatureSavedState state = CROSSTIE_SAVED_STATE.get();
        state.lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);

        // ライトマップ状態の保存
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        state.lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        state.brightnessX = OpenGlHelper.lastBrightnessX;
        state.brightnessY = OpenGlHelper.lastBrightnessY;

        // テクスチャ行列とカラーのリセット（Angelica の行列汚染対策）
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        // Tessellator の色汚染を防ぐ
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator.instance.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        // hi03 コンテキストが残っている場合はリセット
        if (Hi03ExpressRailwayContext.isActive() || Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.reset();
        }

        McteMiniatureRenderContext.enter();
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        RenderMiniatureSavedState state = CROSSTIE_SAVED_STATE.get();
        Minecraft mc = Minecraft.getMinecraft();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // ライティング復元
        if (state.lightingEnabled) GL11.glEnable(GL11.GL_LIGHTING);
        else GL11.glDisable(GL11.GL_LIGHTING);

        // ライトマップ復元
        if (state.lightmapEnabled) {
            if (mc != null && mc.entityRenderer != null) {
                mc.entityRenderer.enableLightmap(0.0D);
            }
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, state.brightnessX, state.brightnessY);
        } else {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        // テクスチャ行列の復元
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        McteMiniatureRenderContext.exit();
    }

    @Unique
    private static final class RenderMiniatureSavedState {
        boolean lightingEnabled;
        boolean lightmapEnabled;
        float brightnessX;
        float brightnessY;
    }
}
