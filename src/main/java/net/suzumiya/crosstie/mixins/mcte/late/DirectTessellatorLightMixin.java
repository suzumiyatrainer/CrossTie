package net.suzumiya.crosstie.mixins.mcte.late;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.suzumiya.crosstie.util.NGTRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DirectTessellator.class, remap = false)
public abstract class DirectTessellatorLightMixin {

    @Shadow
    private int brightness;
    @Shadow
    private boolean hasBrightness;

    @Inject(method = "setBrightness", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void crosstie$forceMCTELightmap(int brightness, CallbackInfo ci) {
        if (!NGTRenderState.isRendering()) {
            return;
        }

        // 単一staticフィールドの直接参照ではなく、スタック最上位（＝現在描画中のTileEntity）の値を取得
        int packedLight = RenderMiniatureAngelicaLightMixin.crosstie$peekTargetBlockLight();

        if (packedLight == -1) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null && mc.renderViewEntity != null) {
                int tx = MathHelper.floor_double(mc.renderViewEntity.posX);
                int ty = MathHelper.floor_double(mc.renderViewEntity.posY + mc.renderViewEntity.getEyeHeight());
                int tz = MathHelper.floor_double(mc.renderViewEntity.posZ);
                packedLight = mc.theWorld.getLightBrightnessForSkyBlocks(tx, ty, tz, 0);
            }
        }

        if (packedLight != -1) {
            // 直接フィールドへ書き込み。setBrightness()を再呼び出ししない（再帰防止）
            this.hasBrightness = true;
            this.brightness = packedLight;
            ci.cancel();
        }
    }
}