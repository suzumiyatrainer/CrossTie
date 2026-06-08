package net.suzumiya.crosstie.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

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

    public static IIcon getPaneIcon(Block block, IBlockAccess blockAccess, int x, int y, int z, int side) {
        IIcon icon = block.getIcon(blockAccess, x, y, z, side);
        if (isUsableIcon(icon)) {
            return icon;
        }
        return getPaneFallback(block);
    }

    public static IIcon getPaneIcon(Block block, int meta) {
        IIcon icon = block.getIcon(0, meta);
        if (isUsableIcon(icon)) {
            return icon;
        }
        return getPaneFallback(block);
    }

    private static IIcon getPaneFallback(Block block) {
        if (block instanceof BlockPane) {
            IIcon paneIcon = ((BlockPane) block).func_150097_e();
            if (isUsableIcon(paneIcon)) {
                return paneIcon;
            }
        }
        IIcon missing = getMissingIcon();
        return missing != null ? missing : null;
    }

    private static IIcon getMissingIcon() {
        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("missingno");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
