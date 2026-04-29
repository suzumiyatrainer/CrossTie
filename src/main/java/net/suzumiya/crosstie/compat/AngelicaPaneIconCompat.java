package net.suzumiya.crosstie.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;

public final class AngelicaPaneIconCompat {

    private AngelicaPaneIconCompat() {
    }

    public static IIcon getPaneIcon(Block block, int side, int meta) {
        IIcon icon = block.getIcon(side, meta);
        if (isUsableIcon(icon)) {
            return icon;
        }

        if (block instanceof BlockPane) {
            IIcon paneIcon = ((BlockPane) block).func_150097_e();
            if (isUsableIcon(paneIcon)) {
                return paneIcon;
            }
        }

        IIcon missing = getMissingIcon();
        return missing != null ? missing : icon;
    }

    private static boolean isUsableIcon(IIcon icon) {
        if (icon == null) {
            return false;
        }
        String iconName = icon.getIconName();
        return iconName == null || !iconName.endsWith("missingno");
    }

    private static IIcon getMissingIcon() {
        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("missingno");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
