package net.suzumiya.crosstie.mixins.gtnhlib;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Block.class, remap = false)
public abstract class MixinBlockPaneIconFallback {

    @Inject(method = "getIcon(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void crosstie$fixPaneIcon(IBlockAccess blockAccess, int x, int y, int z, int side,
                                     CallbackInfoReturnable<IIcon> cir) {
        if (isPaneIconCirCompatible(cir)) {
            IIcon paneIcon = ((BlockPane) (Object) this).func_150097_e();
            if (isUsableIcon(paneIcon)) {
                cir.setReturnValue(paneIcon);
            }
        }
    }

    @Inject(method = "getIcon(II)Lnet/minecraft/util/IIcon;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void crosstie$fixPaneItemIcon(int side, int meta, CallbackInfoReturnable<IIcon> cir) {
        if (isPaneIconCirCompatible(cir)) {
            IIcon paneIcon = ((BlockPane) (Object) this).func_150097_e();
            if (isUsableIcon(paneIcon)) {
                cir.setReturnValue(paneIcon);
            }
        }
    }

    private boolean isPaneIconCirCompatible(CallbackInfoReturnable<IIcon> cir) {
        if (!((Object) this instanceof BlockPane)) {
            return false;
        }
        IIcon icon = cir.getReturnValue();
        return icon == null || !isUsableIcon(icon);
    }

    private boolean isUsableIcon(IIcon icon) {
        if (icon == null) {
            return false;
        }
        String iconName = icon.getIconName();
        return iconName == null || !iconName.endsWith("missingno");
    }
}
