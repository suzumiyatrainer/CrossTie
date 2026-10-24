package net.suzumiya.crosstie.mixins.minecraft;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
import net.minecraft.server.management.ServerConfigurationManager;
import net.suzumiya.crosstie.discord.emoji.PlayerEmojiManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * プレイヤー参加試行時 (allowUserToConnect) をフックし、
 * Discord 連携用のプレイヤースキンアバター・顔絵文字の先行更新を実行する Mixin。
 */
@Mixin(ServerConfigurationManager.class)
public abstract class ServerConfigurationManagerLoginMixin {

    @Inject(method = "allowUserToConnect", at = @At("HEAD"))
    private void crosstie$onAllowUserToConnect(SocketAddress address, GameProfile profile, CallbackInfoReturnable<String> cir) {
        if (profile != null && profile.getId() != null) {
            PlayerEmojiManager.onPlayerJoinAttempt(profile.getId(), profile.getName());
        }
    }
}
