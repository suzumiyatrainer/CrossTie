package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.block.NGTObject;
import jp.ngt.ngtlib.world.IBlockAccessNGT;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTO 描画本体を Angelica の display-list 変換対象から外すため、
 * renderNGTObject 実行中に Legacy コンテキストを有効化する。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.NGTRenderer", remap = false)
public class NGTRendererLegacyContextMixin {

    @Unique
    private static final ThreadLocal<Integer> CROSSTIE_OWNED_CONTEXT_DEPTH =
            new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return Integer.valueOf(0);
                }
            };

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("HEAD"), remap = false)
    private static void crosstie$enterLegacyContext(IBlockAccessNGT blockAccess, NGTObject ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        if (!Hi03ExpressRailwayContext.isActive()) {
            Hi03ExpressRailwayContext.enter();
            CROSSTIE_OWNED_CONTEXT_DEPTH.set(Integer.valueOf(CROSSTIE_OWNED_CONTEXT_DEPTH.get().intValue() + 1));
        }
    }

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V", at = @At("RETURN"), remap = false)
    private static void crosstie$exitLegacyContext(IBlockAccessNGT blockAccess, NGTObject ngto, boolean changeLighting, int mode, int pass, CallbackInfo ci) {
        int depth = CROSSTIE_OWNED_CONTEXT_DEPTH.get().intValue();
        if (depth <= 0) {
            return;
        }

        depth--;
        CROSSTIE_OWNED_CONTEXT_DEPTH.set(Integer.valueOf(depth));
        if (depth == 0) {
            Hi03ExpressRailwayContext.exit();
        }
    }
}
