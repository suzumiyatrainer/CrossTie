package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.modelpack.cfg.ModelConfig;
import jp.ngt.rtm.render.ModelObject;
import net.suzumiya.crosstie.client.CrossTieBlendStateHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 5-2: {@link ModelObject#render} の pass 1 における無条件 {@code glDisable(GL_BLEND)} と
 * 2 引数 {@code glBlendFunc} を {@link CrossTieBlendStateHelper} 経由に置き換え、
 * Angelica の {@code iris$endBlockEntity} での FBO スラッシングを根絶する。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = ModelObject.class, remap = false)
public abstract class ModelObjectPass1BlendMixin {

    @Unique
    private boolean crosstie$blendEnabledByUs = false;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void crosstie$resetBlendFlag(Object entity, ModelConfig cfg, int pass, float par3, CallbackInfo ci) {
        this.crosstie$blendEnabledByUs = false;
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false),
        require = 0,
        remap = false
    )
    private void crosstie$redirectEnableBlend(int cap) {
        if (cap == GL11.GL_BLEND) {
            this.crosstie$blendEnabledByUs = CrossTieBlendStateHelper.enterPass1Blend();
        } else {
            GL11.glEnable(cap);
        }
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false),
        require = 0,
        remap = false
    )
    private void crosstie$redirectBlendFunc(int sfactor, int dfactor) {
        // enterPass1Blend() 内で必要な場合のみ 4 引数 glBlendFunc を設定済み
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false),
        require = 0,
        remap = false
    )
    private void crosstie$redirectDisableBlend(int cap) {
        if (cap == GL11.GL_BLEND) {
            CrossTieBlendStateHelper.exitPass1Blend(this.crosstie$blendEnabledByUs);
            this.crosstie$blendEnabledByUs = false;
        } else {
            GL11.glDisable(cap);
        }
    }
}
