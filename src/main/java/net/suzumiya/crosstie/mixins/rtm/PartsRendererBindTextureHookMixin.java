package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.render.PartsRenderer;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.client.CrossTieRailCompileTextureHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5-4: DisplayList コンパイル中にスクリプトから呼ばれる {@link PartsRenderer#bindTexture} を
 * {@link CrossTieRailCompileTextureHook} へ委譲し、テクスチャバインドを DisplayList の外へ分離する。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererBindTextureHookMixin {

    @Inject(method = "bindTexture", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$captureBindTextureDuringCompile(ResourceLocation texture, CallbackInfo ci) {
        if (CrossTieRailCompileTextureHook.onBindTexture(texture)) {
            ci.cancel();
        }
    }
}
