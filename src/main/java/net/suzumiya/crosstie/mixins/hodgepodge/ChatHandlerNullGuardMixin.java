package net.suzumiya.crosstie.mixins.hodgepodge;

import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mitchej123.hodgepodge.client.chat.ChatHandler", remap = false)
public abstract class ChatHandlerNullGuardMixin {

    @Inject(method = "tryCompactMessage", at = @At("HEAD"), cancellable = true)
    private static void crosstie$onTryCompactMessageNullGuard(IChatComponent imsg, List<ChatLine> chatLines,
            CallbackInfoReturnable<Boolean> cir) {
        if (imsg == null || chatLines == null || chatLines.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        ChatLine chatLine = chatLines.get(0);
        if (chatLine == null || chatLine.func_151461_a() == null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "areMessagesIdentical", at = @At("HEAD"), cancellable = true)
    private static void crosstie$onAreMessagesIdenticalNullGuard(IChatComponent imsg, IChatComponent prevMsg,
            CallbackInfoReturnable<Boolean> cir) {
        if (imsg == null || prevMsg == null || imsg.getSiblings() == null || prevMsg.getSiblings() == null) {
            cir.setReturnValue(false);
        }
    }
}
