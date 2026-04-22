package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTObject 描画時の GL ステートを安定化する。
 *
 * <p>Angelica 併用時に GL ステートが汚染されると方角依存の面欠けが発生するため、
 * renderNGTObject 実行中は front-face を固定する。
 *
 * <p>GL_CULL_FACE の扱い:
 * <ul>
 *   <li>MCTE ミニチュアコンテキスト中: cull face を無効化しない（RenderMiniature.renderMiniatureAt で
 *       明示的に有効化されており、無効化するとZ-fightingが発生する）</li>
 *   <li>その他のコンテキスト（RTM など）: 従来通り無効化して復元</li>
 * </ul>
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.NGTRenderer", remap = false)
public class NGTRendererStateMixin {

    @Unique
    private static final ThreadLocal<int[]> CROSSTIE_STATE =
            new ThreadLocal<int[]>() {
                @Override
                protected int[] initialValue() {
                    // [0] = saved GL_FRONT_FACE value
                    // [1] = saved GL_CULL_FACE enabled flag (0=was disabled, 1=was enabled)
                    // [2] = whether we touched cull face (0=no, 1=yes)
                    return new int[3];
                }
            };

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
            at = @At("HEAD"), remap = false)
    private static void crosstie$stabilizeRenderStateHead(
            @Coerce Object blockAccess, @Coerce Object ngto,
            boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        int[] state = CROSSTIE_STATE.get();
        state[0] = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        GL11.glFrontFace(GL11.GL_CCW);

        // MCTE ミニチュアコンテキストでは cull face を触らない
        if (McteMiniatureRenderContext.isActive()) {
            state[2] = 0;
        } else {
            state[1] = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0;
            state[2] = 1;
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
            at = @At("RETURN"), remap = false)
    private static void crosstie$stabilizeRenderStateReturn(
            @Coerce Object blockAccess, @Coerce Object ngto,
            boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        int[] state = CROSSTIE_STATE.get();
        GL11.glFrontFace(state[0]);

        if (state[2] != 0) {
            if (state[1] != 0) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
        }
    }
}
