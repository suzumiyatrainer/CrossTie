package net.suzumiya.crosstie.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/**
 * チャット内の画像クリックイベント等から呼び出され、
 * クライアント側で即座に画像プレビュー画面を表示するクライアント専用コマンド。
 */
@SideOnly(Side.CLIENT)
public class CommandViewImage extends CommandBase {

    @Override
    public String getCommandName() {
        return "crosstieview";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/crosstieview <url> [filename]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // 一般プレイヤーがクライアント側で実行可能
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            return;
        }

        String url = args[0];
        String filename = (args.length > 1) ? args[1] : "image.png";

        // メインスレッドで GUI を開く
        Minecraft.getMinecraft().func_152344_a(() -> {
            Minecraft.getMinecraft().displayGuiScreen(new GuiImagePreview(url, filename));
        });
    }
}
