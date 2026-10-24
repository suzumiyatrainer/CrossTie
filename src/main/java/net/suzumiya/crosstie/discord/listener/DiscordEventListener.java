package net.suzumiya.crosstie.discord.listener;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
// import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.suzumiya.crosstie.discord.DiscordConfig;
import net.suzumiya.crosstie.discord.DiscordMessageQueue;
import net.suzumiya.crosstie.discord.emoji.PlayerEmojiManager;

import java.util.UUID;

/**
 * Minecraft サーバー内イベント (チャット、参加、退出、死亡、Tick) を購読し、 Discord 連携と安全に双方向同期するイベントリスナー。
 */
public final class DiscordEventListener {

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!DiscordConfig.sendChat)
            return;

        EntityPlayerMP player = event.player;
        String rawName = player.getCommandSenderName();
        UUID uuid = player.getUniqueID();
        String message = event.message;

        String emojiTag = PlayerEmojiManager.getEmojiTag(uuid, rawName);
        String formatted = DiscordConfig.formatMcToDiscord.replace("%emoji%", emojiTag).replace("%mcid%", rawName)
                .replace("%name%", rawName).replace("%message%", message).trim();

        String avatarUrl = DiscordConfig.getAvatarUrl(uuid, rawName);
        DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(formatted, rawName, avatarUrl));
    }

    private static final java.util.Set<UUID> LOGGED_IN_PLAYERS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        String name = player.getCommandSenderName();
        UUID uuid = player.getUniqueID();

        LOGGED_IN_PLAYERS.add(uuid);

        // 参加時に非同期でスキン更新判定
        PlayerEmojiManager.onPlayerJoin(uuid, name);

        if (DiscordConfig.sendJoinLeave) {
            // メインスレッドをブロックせず非同期で絵文字取得完了を待機 (最大1.5秒)
            PlayerEmojiManager.runAsync(() -> {
                String emojiTag = PlayerEmojiManager.getOrAwaitEmojiTag(uuid, name, 1500);
                String formatted = DiscordConfig.formatPlayerJoin;
                if (emojiTag != null && !emojiTag.isEmpty()) {
                    formatted = formatted.replace("%emoji%", emojiTag);
                } else {
                    formatted = formatted.replace("%emoji% ", "").replace("%emoji%", "");
                }
                formatted = formatted.replace("%mcid%", name).replace("%name%", name).trim();

                String avatarUrl = DiscordConfig.getAvatarUrl(uuid, name);
                DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(formatted, name, avatarUrl));
            });
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!DiscordConfig.sendJoinLeave)
            return;

        EntityPlayer player = event.player;
        String name = player.getCommandSenderName();
        UUID uuid = player.getUniqueID();

        // 正常にログイン完了していなかった切断 (Mod不一致による接続拒否等) は退出メッセージを送信しない
        if (!LOGGED_IN_PLAYERS.remove(uuid)) {
            return;
        }

        String emojiTag = PlayerEmojiManager.getEmojiTag(uuid, name);
        String formatted = DiscordConfig.formatPlayerLeave;
        if (emojiTag != null && !emojiTag.isEmpty()) {
            formatted = formatted.replace("%emoji%", emojiTag);
        } else {
            formatted = formatted.replace("%emoji% ", "").replace("%emoji%", "");
        }
        formatted = formatted.replace("%mcid%", name).replace("%name%", name).trim();

        String avatarUrl = DiscordConfig.getAvatarUrl(uuid, name);
        DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(formatted, name, avatarUrl));
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!DiscordConfig.sendDeath)
            return;
        if (!(event.entityLiving instanceof EntityPlayerMP))
            return;

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        String deathMessage = player.func_110142_aN().func_151521_b().getUnformattedText();
        String name = player.getCommandSenderName();
        UUID uuid = player.getUniqueID();

        String emojiTag = PlayerEmojiManager.getEmojiTag(uuid, name);
        String formatted = DiscordConfig.formatPlayerDeath.replace("%emoji%", emojiTag).replace("%mcid%", name)
                .replace("%name%", name).replace("%deathmessage%", deathMessage).trim();

        String avatarUrl = DiscordConfig.getAvatarUrl(uuid, name);
        DiscordMessageQueue.enqueueOutbound(new DiscordMessageQueue.OutboundMessage(formatted, name, avatarUrl));
    }

    /**
     * Discord から受信したメッセージを、サーバーメインスレッドで安全にプレイヤーへ配信する。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null)
            return;

        net.minecraft.util.IChatComponent msg;
        // 1 tick あたり最大 20 件を処理してスプレッド
        int count = 0;
        while ((msg = DiscordMessageQueue.pollInbound()) != null && count < 20) {
            server.getConfigurationManager().sendChatMsg(msg);
            count++;
        }
    }
}
