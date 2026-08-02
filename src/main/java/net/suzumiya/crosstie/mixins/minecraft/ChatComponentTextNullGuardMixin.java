package net.suzumiya.crosstie.mixins.minecraft;

import java.util.Objects;
import net.minecraft.util.ChatComponentStyle;
import net.minecraft.util.ChatComponentText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponentText.class)
public abstract class ChatComponentTextNullGuardMixin extends ChatComponentStyle {

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void crosstie$onEqualsNullGuard(Object obj, CallbackInfoReturnable<Boolean> cir) {
        if (this == obj) {
            cir.setReturnValue(true);
            return;
        }
        if (!(obj instanceof ChatComponentText)) {
            cir.setReturnValue(false);
            return;
        }
        ChatComponentText self = (ChatComponentText) (Object) this;
        ChatComponentText other = (ChatComponentText) obj;

        String selfText = self.getUnformattedTextForChat();
        String otherText = other.getUnformattedTextForChat();

        if (!Objects.equals(selfText, otherText)) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(super.equals(obj));
    }
}
