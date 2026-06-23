package net.suzumiya.crosstie.command;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.suzumiya.crosstie.CrossTieConfig;

/**
 * CrossTie のゲーム内コマンド {@code /crosstie}。
 *
 * <p>使用法:
 * <ul>
 *   <li>{@code /crosstie cfgr} — config を再読み込みする</li>
 * </ul>
 *
 * <p>メッセージは {@code assets/crosstie/lang/} 以下の言語ファイルから
 * {@link StatCollector#translateToLocal(String)} 経由で取得される。
 */
public class CommandCrossTie extends CommandBase {

    @Override
    public String getCommandName() {
        return "crosstie";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/crosstie <cfgr>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP 権限が必要
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "cfgr");
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendMessage(sender, "crosstie.command.usage", EnumChatFormatting.RED);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "cfgr":
                handleCfgr(sender);
                break;
            default:
                sendMessage(sender, "crosstie.command.unknown", EnumChatFormatting.RED);
                sendMessage(sender, "crosstie.command.usage", EnumChatFormatting.RED);
                break;
        }
    }

    /**
     * {@code /crosstie cfgr} サブコマンドの処理。
     * config を再読み込みし、再起動が必要な場合はその旨を通知する。
     */
    private void handleCfgr(ICommandSender sender) {
        CrossTieConfig.reload();

        // 成功メッセージ（緑色）
        sendMessage(sender, "crosstie.command.cfgr.success", EnumChatFormatting.GREEN);

        // 再起動が必要な設定項目が変更された場合、黄色で警告
        if (CrossTieConfig.requiresRestart) {
            sendMessage(sender, "crosstie.command.cfgr.restart_note", EnumChatFormatting.YELLOW);
        }
    }

    /**
     * プレフィックス付きのメッセージを送信する。
     *
     * <p>「CrossTie」部分は Dark Aqua 色で表示され、翻訳テキストは指定された色で表示される。
     *
     * @param sender  コマンド送信者
     * @param langKey 翻訳キー
     * @param color   メッセージ本文の色
     */
    private void sendMessage(ICommandSender sender, String langKey, EnumChatFormatting color) {
        String prefix = EnumChatFormatting.DARK_AQUA + "[CrossTie] " + EnumChatFormatting.RESET;
        String message = StatCollector.translateToLocal(langKey);
        sender.addChatMessage(new ChatComponentText(prefix + color + message + EnumChatFormatting.RESET));
    }
}