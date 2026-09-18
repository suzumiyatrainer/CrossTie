package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RailProperty.class)
public class RailPropertyAutoSplitLegacyMixin {

    /**
     * 1.10.1以前に作成されたレール（NBTにAutoSplitキーがない）場合、
     * 1.10.2以降のデフォルトであるtrueに上書きされてしまうと、コピー・再設置時に
     * 意図せずレールが自動分割（RailChunkSectioner.split）され、曲線がジグザグに分断される不整合が発生します。
     * これを防ぐため、キーが存在しない過去バージョンのレールデータの場合はfalseをデフォルトとするように修正します。
     */
    @Inject(method = "readAutoSplit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onReadAutoSplit(NBTTagCompound nbt, CallbackInfoReturnable<Boolean> cir) {
        if (!nbt.hasKey("AutoSplit")) {
            cir.setReturnValue(false);
        }
    }
}
