package net.suzumiya.crosstie.mixins.hodgepodge;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftPreloadMixin {

    @Inject(method = {"startGame", "func_71384_a"}, at = @At("HEAD"))
    private void crosstie$preloadClassesToPreventDeadlock(CallbackInfo ci) {
        System.out.println("[CrossTie] Preloading HttpUtil and MusicTicker to prevent deadlock...");
        try {
            Class<?> c1 = net.minecraft.util.HttpUtil.class;
            Class<?> c2 = net.minecraft.client.audio.MusicTicker.class;
            System.out.println("[CrossTie] Preload successful. c1: " + c1.getName() + ", c2: " + c2.getName());
        } catch (Throwable t) {
            System.out.println("[CrossTie] Preload failed!");
            t.printStackTrace();
        }
    }
}
