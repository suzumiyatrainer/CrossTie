package net.suzumiya.crosstie.mixins.ngtlib;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTObject 描画時の cull/front-face 状態を安定化する。
 *
 * Angelica 併用時に GL 状態が汚染されると方角依存の面欠けが発生するため、
 * renderNGTObject 実行中だけ明示的に固定し、終了後に元へ戻す。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.NGTRenderer", remap = false)
public class NGTRendererStateMixin {

    @Unique
    private static final ThreadLocal<int[]> CROSSTIE_STATE =
            new ThreadLocal<int[]>() {
                @Override
                protected int[] initialValue() {
                    // [0] = front face enum, [1] = cull enabled flag
                    return new int[2];
                }
            };

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("HEAD"), remap = false)
    private static void crosstie$stabilizeRenderStateHead(@Coerce Object blockAccess, @Coerce Object ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        int[] state = CROSSTIE_STATE.get();
        state[0] = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        state[1] = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0;

        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("RETURN"), remap = false)
    private static void crosstie$stabilizeRenderStateReturn(@Coerce Object blockAccess, @Coerce Object ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        int[] state = CROSSTIE_STATE.get();
        GL11.glFrontFace(state[0]);
        if (state[1] != 0) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }
}
