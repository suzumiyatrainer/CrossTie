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
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureContextGuardMixin {
    @Unique
    private static final ThreadLocal<RenderMiniatureLightmapState> CROSSTIE_LIGHTMAP_STATE =
            ThreadLocal.withInitial(RenderMiniatureLightmapState::new);

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), remap = false)
    private void crosstie$prepareMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        RenderMiniatureLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        state.lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        
        // 1. ライトマップ状態の保存
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        state.lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        state.brightnessX = OpenGlHelper.lastBrightnessX;
        state.brightnessY = OpenGlHelper.lastBrightnessY;

        // 2. 重要: テクスチャ行列とカラーのリセット
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        
        // カラーを白に戻し、Tessellatorの色汚染を防ぐ
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator.instance.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        // 3. 外部Modのリセット
        if (Hi03ExpressRailwayContext.isActive() || Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.reset();
        }
        McteMiniatureRenderContext.enter();
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        RenderMiniatureLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        Minecraft mc = Minecraft.getMinecraft();
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // ライティング・ライトマップの復元
        if (state.lightingEnabled) GL11.glEnable(GL11.GL_LIGHTING);
        else GL11.glDisable(GL11.GL_LIGHTING);

        if (state.lightmapEnabled) {
            if (mc != null && mc.entityRenderer != null) {
                mc.entityRenderer.enableLightmap(0.0D);
            }
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, state.brightnessX, state.brightnessY);
        } else {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        // 4. 重要: テクスチャ行列の復元
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        
        McteMiniatureRenderContext.exit();
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$prepareMiniatureBlockRender(@Coerce Object world, @Coerce Object tile, float partialTicks,
            int pass, CallbackInfo ci) {
        RenderMiniatureLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        state.cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        // MCTE blocks are rendered with NGTRenderer which assumes back-face culling is setup correctly.
        // If it was disabled by a previous render pass or the item-renderer path, miniature blocks
        // might look wrong or have visual artifacts. We ensure it's enabled here.
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupMiniatureBlockRender(@Coerce Object world, @Coerce Object tile, float partialTicks,
            int pass, CallbackInfo ci) {
        RenderMiniatureLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        if (state.cullFaceEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Unique
    private static final class RenderMiniatureLightmapState {
        private boolean lightingEnabled;
        private boolean lightmapEnabled;
        private float brightnessX;
        private float brightnessY;
        private boolean cullFaceEnabled;
    }
}
