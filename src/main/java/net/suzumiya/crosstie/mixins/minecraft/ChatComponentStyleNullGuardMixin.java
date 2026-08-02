package net.suzumiya.crosstie.mixins.minecraft;

import java.util.List;
import java.util.Objects;
import net.minecraft.util.ChatComponentStyle;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponentStyle.class)
public abstract class ChatComponentStyleNullGuardMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void crosstie$onStyleEqualsNullGuard(Object obj, CallbackInfoReturnable<Boolean> cir) {
        if (this == obj) {
            cir.setReturnValue(true);
            return;
        }
        if (!(obj instanceof ChatComponentStyle)) {
            cir.setReturnValue(false);
            return;
        }
        ChatComponentStyle self = (ChatComponentStyle) (Object) this;
        ChatComponentStyle other = (ChatComponentStyle) obj;

        List<IChatComponent> selfSiblings = self.getSiblings();
        List<IChatComponent> otherSiblings = other.getSiblings();

        ChatStyle selfStyle = self.getChatStyle();
        ChatStyle otherStyle = other.getChatStyle();

        boolean siblingsEqual = Objects.equals(selfSiblings, otherSiblings);
        boolean styleEqual = Objects.equals(selfStyle, otherStyle);

        cir.setReturnValue(siblingsEqual && styleEqual);
    }
}
