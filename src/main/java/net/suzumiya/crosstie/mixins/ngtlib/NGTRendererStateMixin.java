package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.block.NGTObject;
import jp.ngt.ngtlib.world.IBlockAccessNGT;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
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
    private static final ThreadLocal<Integer> CROSSTIE_FRONT_FACE = new ThreadLocal<Integer>();
    @Unique
    private static final ThreadLocal<Boolean> CROSSTIE_CULL_ENABLED = new ThreadLocal<Boolean>();

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("HEAD"), remap = false)
    private static void crosstie$stabilizeRenderStateHead(IBlockAccessNGT blockAccess, NGTObject ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        CROSSTIE_FRONT_FACE.set(Integer.valueOf(GL11.glGetInteger(GL11.GL_FRONT_FACE)));
        CROSSTIE_CULL_ENABLED.set(Boolean.valueOf(GL11.glIsEnabled(GL11.GL_CULL_FACE)));

        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("RETURN"), remap = false)
    private static void crosstie$stabilizeRenderStateReturn(IBlockAccessNGT blockAccess, NGTObject ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        Integer frontFace = CROSSTIE_FRONT_FACE.get();
        Boolean cullEnabled = CROSSTIE_CULL_ENABLED.get();

        if (frontFace != null) {
            GL11.glFrontFace(frontFace.intValue());
        }

        if (Boolean.TRUE.equals(cullEnabled)) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        CROSSTIE_FRONT_FACE.remove();
        CROSSTIE_CULL_ENABLED.remove();
    }
}
