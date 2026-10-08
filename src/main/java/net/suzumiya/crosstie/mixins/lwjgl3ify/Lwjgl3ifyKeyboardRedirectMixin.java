package net.suzumiya.crosstie.mixins.lwjgl3ify;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
    "jp.ngt.rtm.block.BlockMachineBase",
    "jp.ngt.rtm.block.BlockOrnamentBase",
    "jp.ngt.rtm.block.BlockSignBoard",
    "jp.ngt.rtm.electric.BlockElectricalWiring",
    "jp.ngt.rtm.electric.BlockSignal",
    "jp.ngt.rtm.event.RTMKeyHandlerClient",
    "jp.ngt.rtm.gui.camera.CameraKey",
    "jp.ngt.rtm.rail.RenderMarkerBlock1710",
    "jp.ngt.rtm.rail.RenderMarkerBlock1122"
}, remap = false)
public class Lwjgl3ifyKeyboardRedirectMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;isKeyDown(I)Z", remap = false), remap = false, require = 0)
    private boolean redirectIsKeyDownLwjgl2(int key) {
        boolean original = org.lwjgl.input.Keyboard.isKeyDown(key);
        return net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardCompat.mergeIsKeyDownResult(key, original);
    }
}

