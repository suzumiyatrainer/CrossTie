package net.suzumiya.crosstie.mixins.optifine;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine/FastCraft + ShadersMod 環境で、ワイヤーが完全に非表示になる問題を修正する。
 *
 * <p>RTMのワイヤー描画（{@code WirePartsRenderer.renderWireStraight} 等）では
 * {@code GL11.glScalef} を用いた非均等スケール（Non-uniform scaling）が使用される。
 * 通常の描画では {@code GL_RESCALE_NORMAL} で十分だが、非均等スケールで
 * 法線ベクトルが歪むと、ShadersModの G-Buffer エンコードで法線が NaN になったり
 * 壊れたりして、該当オブジェクトが完全に透明（discard）になる現象が発生する。</p>
 *
 * <p>これを防ぐため、ワイヤーの描画時に {@code GL_NORMALIZE} を強制的に有効化し、
 * シェーダーに渡る法線ベクトルが必ず単位ベクトルになるようにする。</p>
 */
@SuppressWarnings("all")
@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class WireNormalizeShaderFixMixin {

    @Inject(method = "renderAllWire", at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$enableNormalize(CallbackInfo ci) {
        if (net.suzumiya.crosstie.CrossTieConfig.fixOptiFineWireNormalize) {
            GL11.glEnable(GL11.GL_NORMALIZE);
        }
    }

    @Inject(method = "renderAllWire", at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$disableNormalize(CallbackInfo ci) {
        if (net.suzumiya.crosstie.CrossTieConfig.fixOptiFineWireNormalize) {
            GL11.glDisable(GL11.GL_NORMALIZE);
        }
    }
}
