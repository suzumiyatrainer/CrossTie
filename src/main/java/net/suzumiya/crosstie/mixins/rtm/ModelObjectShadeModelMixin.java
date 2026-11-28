package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.render.ModelObject;
import net.suzumiya.crosstie.client.CrossTieShadeModelTracker;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 3-3: {@link ModelObject#render} の開始・終了における {@code GL11.glShadeModel} を
 * {@link CrossTieShadeModelTracker} のスコープ管理へリダイレクトする。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = ModelObject.class, remap = false)
public abstract class ModelObjectShadeModelMixin {

    @Redirect(
            method = "render(Ljava/lang/Object;Ljp/ngt/rtm/modelpack/cfg/ModelConfig;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glShadeModel(I)V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private void crosstie$redirectModelShadeModel(int mode) {
        if (mode == GL11.GL_SMOOTH) {
            CrossTieShadeModelTracker.enterModelSmoothScope();
        } else {
            CrossTieShadeModelTracker.exitModelSmoothScope();
        }
    }
}
