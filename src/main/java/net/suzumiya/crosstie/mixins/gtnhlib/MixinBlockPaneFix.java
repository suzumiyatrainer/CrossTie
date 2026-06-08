package net.suzumiya.crosstie.mixins.gtnhlib;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Block.class)
public abstract class MixinBlockPaneFix {

    /**
     * GTNHLibが実行時に追加する nhlib$isModeled メソッドに介入します。
     * 鉄格子や板ガラスの場合、強制的に false を返すことでGTNHLibのレンダリングを無効化します。
     */
    @Inject(method = "nhlib$isModeled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$fixPaneModeling(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof BlockPane) {
            cir.setReturnValue(false);
        }
    }

    /**
     * GTNHLibが実行時に追加する nhlib$setModeled メソッドに介入します。
     * 鉄格子や板ガラスの場合、強制的にフィールドを false に設定し、メソッドの実行をキャンセルします。
     */
    @Inject(method = "nhlib$setModeled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$fixPaneSetModeled(boolean modeled, CallbackInfo ci) {
        if ((Object) this instanceof BlockPane) {
            try {
                java.lang.reflect.Field field = Block.class.getDeclaredField("nhlib$isModeled");
                field.setAccessible(true);
                field.setBoolean(this, false);
            } catch (Throwable ignored) {
            }
            ci.cancel();
        }
    }
}
