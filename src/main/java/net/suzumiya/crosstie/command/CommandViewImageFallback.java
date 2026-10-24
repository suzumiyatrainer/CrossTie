package net.suzumiya.crosstie.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

/**
 * クライアントに CrossTie が導入されていない環境でチャット内のプレビューリンクをクリックした場合の
 * サーバー側フォールバックハンドラ。ブラウザで開くリンクを案内する。
 */
public class CommandViewImageFallback extends CommandBase {

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
        return 0; // 一般プレイヤーが実行可能
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
        ChatComponentText msg = new ChatComponentText("§9[CrossTie] §eマイクラ画面内プレビューを利用するにはクライアント側に CrossTie が必要です。ブラウザで開くにはこちらをクリック: ");
        ChatComponentText link = new ChatComponentText("§b§n" + url);
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.setChatStyle(style);
        msg.appendSibling(link);
        sender.addChatMessage(msg);
    }
}
