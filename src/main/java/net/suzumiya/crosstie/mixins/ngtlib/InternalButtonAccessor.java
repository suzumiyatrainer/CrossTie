package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.gui.InternalButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = InternalButton.class, remap = false)
public interface InternalButtonAccessor {
    @Accessor("startX") float getStartX();
    @Accessor("startY") float getStartY();
    @Accessor("width") float getWidth();
    @Accessor("height") float getHeight();
}
