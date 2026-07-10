package net.suzumiya.crosstie.mixins.discordsrv;

import github.scarsz.discordsrv.util.NMSUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NMSUtil.class, remap = false)
public class NMSUtilMixin {

    /**
     * Prevents NullPointerException in DiscordSRV's NMSUtil.getTexture(Player) when
     * running on older Minecraft versions (like 1.7.10) where
     * class_ResolvableProfile is null.
     */
    @Redirect(method = "getTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Class;isInstance(Ljava/lang/Object;)Z"), remap = false)
    private static boolean onIsInstance(Class<?> clazz, Object obj) {
        if (clazz == null) {
            return false;
        }
        return clazz.isInstance(obj);
    }
}
