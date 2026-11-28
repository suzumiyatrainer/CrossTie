package net.suzumiya.crosstie.mixins.ngtlib;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.NGTTessellator;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3-2: {@link NGTTessellator#drawVertexArray()} 内のバッファ縮小判定タイポ
 * ({@code rawBufferIndex < (rawBufferSize << 3)}) を {@code >> 3} へ修正する共通 Mixin。
 *
 * <p>特に OptiFine（シェーダー OFF）/ FastCraft / バニラ環境で毎回 512KB→256KB の
 * 配列再確保スラッシングが発生していた問題を根治する。既存 3 つの {@code draw()} Mixin とは完全無競合。</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorDrawVertexArrayFixMixin {

    @Shadow
    private int[] rawBuffer;

    @Shadow
    private int rawBufferSize;

    @Shadow
    private int rawBufferIndex;

    @Redirect(
            method = "drawVertexArray()I",
            at = @At(
                    value = "FIELD",
                    target = "Ljp/ngt/ngtlib/renderer/NGTTessellator;rawBufferSize:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0,
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private int crosstie$bypassBrokenShrinkCheck(NGTTessellator self) {
        return 0; // rawBufferSize > 0x20000 を false にして元のバグブロックをバイパス
    }

    @Inject(
            method = "drawVertexArray()I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/ngt/ngtlib/renderer/NGTTessellator;reset()V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private void crosstie$applyCorrectShrinkCheck(CallbackInfoReturnable<Integer> cir) {
        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize >> 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }
    }
}
