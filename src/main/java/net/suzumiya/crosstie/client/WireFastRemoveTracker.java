package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.network.CrossTiePacketHandler;
import net.suzumiya.crosstie.network.MessageRemoveWire;

public class WireFastRemoveTracker {

    private static int startX = 0;
    private static int startY = -1;
    private static int startZ = 0;
    private static boolean hasStart = false;

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.world.isRemote && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            if (!CrossTieConfig.enableWireFastRemove) {
                return;
            }

            int keyCode = CrossTieKeyBindings.removeWireKey.getKeyCode();
            int useKeyCode = Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode();

            // ワイヤー削除キーが未設定、または右クリック（アイテム使用）と同じに設定されている場合は無効化
            if (keyCode == 0 || keyCode == useKeyCode) {
                return;
            }

            // MC標準の操作設定で割り当てられたキーが厳密に押されているか
            boolean isPressed = false;
            if (keyCode < 0) {
                isPressed = org.lwjgl.input.Mouse.isButtonDown(keyCode + 100);
            } else if (keyCode > 0) {
                isPressed = org.lwjgl.input.Keyboard.isKeyDown(keyCode);
            }

            if (isPressed) {
                TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);

                if (te instanceof TileEntityElectricalWiring) {
                    // キーが押されている場合、RTM本体の処理（ワイヤーアイテムによる切断等）やブロック設置を無効化する
                    event.setCanceled(true);

                    // 素手でのみ機能する
                    if (event.entityPlayer.getHeldItem() != null) {
                        if (!hasStart) {
                            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                                    new ChatComponentText("§cワイヤー削除は素手でのみ実行できます。"), 777123);
                        } else {
                            hasStart = false;
                            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                                    new ChatComponentText("§c手にアイテムを持ったため、ワイヤー削除の選択をキャンセルしました。"), 777123);
                        }
                        return;
                    }

                    if (!hasStart) {
                        startX = event.x;
                        startY = event.y;
                        startZ = event.z;
                        hasStart = true;
                        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                                new ChatComponentText("§a1点目の碍子を選択しました。2点目を同じキー+右クリックしてください。"), 777123);
                    } else {
                        // 同一ブロックならキャンセル
                        if (startX == event.x && startY == event.y && startZ == event.z) {
                            hasStart = false;
                            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                                    new ChatComponentText("§cワイヤー削除の選択をキャンセルしました。"), 777123);
                        } else {
                            // 2点目が選択された
                            CrossTiePacketHandler.INSTANCE.sendToServer(
                                    new MessageRemoveWire(startX, startY, startZ, event.x, event.y, event.z));
                            hasStart = false;
                            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                                    new ChatComponentText("§aワイヤーの削除要求を送信しました。"), 777123);
                        }
                    }
                }
            }
        }
    }
}
