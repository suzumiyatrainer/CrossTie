package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.Minecraft;
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

@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureContextGuardMixin {
    @Unique
    private static final ThreadLocal<RenderItemLightmapState> CROSSTIE_LIGHTMAP_STATE =
            ThreadLocal.withInitial(RenderItemLightmapState::new);

    @Inject(method = "renderItem", at = @At("HEAD"), remap = false)
    private void crosstie$captureGuiItemRenderState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                    CallbackInfo ci) {
        RenderItemLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        state.lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        state.brightnessX = OpenGlHelper.lastBrightnessX;
        state.brightnessY = OpenGlHelper.lastBrightnessY;
        
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "renderItem", at = @At("RETURN"), remap = false)
    private void crosstie$restoreGuiItemRenderState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                    CallbackInfo ci) {
        RenderItemLightmapState state = CROSSTIE_LIGHTMAP_STATE.get();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$prepareMiniatureItemRender(CallbackInfo ci) {
        // 重要: 車が緑色になるのを防ぐ
        Tessellator.instance.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        
        McteMiniatureRenderContext.enter();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupMiniatureItemRender(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        McteMiniatureRenderContext.exit();
    }

    @Unique
    private static final class RenderItemLightmapState {
        private boolean lightmapEnabled;
        private float brightnessX;
        private float brightnessY;
    }
}