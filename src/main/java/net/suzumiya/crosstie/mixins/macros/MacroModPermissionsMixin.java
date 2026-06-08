package net.suzumiya.crosstie.mixins.macros;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CrossTie compatibility mixin for MacroMod's permission system.
 * 
 * MacroMod calls registerPermission, hasPermission, and refreshPermissions
 * which internally call LiteLoader's PermissionsManagerClient.tamperCheck().
 * If the permission manager was not ticked for 60 seconds, tamperCheck()
 * throws an IllegalStateException that crashes the client.
 *
 * This mixin silently catches these exceptions to allow the game to continue
 * without crashing, while the user still gets to see the pause menu.
 */
@Mixin(targets = "net.eq2online.macros.permissions.MacroModPermissions", remap = false)
public abstract class MacroModPermissionsMixin {

    /**
     * Prevents registerPermission from crashing when the permissions manager is stale.
     */
    @Inject(method = "registerPermission(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$registerPermission(String permission, CallbackInfo ci) {
        // Always cancel and do nothing to prevent IllegalStateException from tamperCheck()
        ci.cancel();
    }

    /**
     * Prevents hasPermission from crashing when the permissions manager is stale.
     */
    @Inject(method = "hasPermission(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$hasPermission(String permission, CallbackInfoReturnable<Boolean> cir) {
        // Always cancel and return false as safe default
        cir.setReturnValue(false);
    }

    /**
     * Prevents refreshPermissions from crashing when the permissions manager is stale.
     */
    @Inject(method = "refreshPermissions(Lbao;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$refreshPermissions(Object world, CallbackInfo ci) {
        // Always cancel and do nothing to prevent IllegalStateException from tamperCheck()
        ci.cancel();
    }
}
