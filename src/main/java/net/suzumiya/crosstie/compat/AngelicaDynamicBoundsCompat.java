package net.suzumiya.crosstie.compat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.lang.reflect.Method;

/**
 * 3-6: Angelica 2.2.46 の {@code TileEntityRenderBoundsRegistry.registerDynamicClass(String)} へ
 * 有限 AABB キャッシュ対象となる全具象 TileEntity クラス名を登録する互換ヘルパー。
 *
 * <p>{@code TileEntityRenderBoundsRegistry.classify()} は具象クラス名の完全一致で判定するため、
 * 抽象親クラスではなく各具象サブクラス名をワールドロード前に登録する。</p>
 */
@SideOnly(Side.CLIENT)
public final class AngelicaDynamicBoundsCompat {

    private static final String[] DYNAMIC_TE_CLASSES = new String[] {
            // TileEntityOrnament 具象サブクラス
            "jp.ngt.rtm.block.tileentity.TileEntityPole",
            "jp.ngt.rtm.block.tileentity.TileEntityPlantOrnament",
            "jp.ngt.rtm.block.tileentity.TileEntityFluorescent",
            "jp.ngt.rtm.block.tileentity.TileEntityPipe",
            "jp.ngt.rtm.block.tileentity.TileEntityScaffold",
            "jp.ngt.rtm.block.tileentity.TileEntityScaffoldStairs",
            // TileEntityMachineBase 具象サブクラス
            "jp.ngt.rtm.block.tileentity.TileEntityLight",
            "jp.ngt.rtm.block.tileentity.TileEntityTurnstile",
            "jp.ngt.rtm.block.tileentity.TileEntitySpeaker",
            "jp.ngt.rtm.block.tileentity.TileEntityTicketVendor",
            "jp.ngt.rtm.block.tileentity.TileEntityPoint",
            "jp.ngt.rtm.block.tileentity.TileEntityCrossingGate",
            // その他有限 AABB 化対象
            "jp.ngt.rtm.block.tileentity.TileEntityFlag",
            "jp.ngt.rtm.block.tileentity.TileEntitySignBoard",
            "jp.ngt.rtm.block.tileentity.TileEntityRailroadSign",
            // Phase 5-1: MCTE ミニチュア有限 AABB 化
            "jp.ngt.mcte.block.TileEntityMiniature"
    };

    private AngelicaDynamicBoundsCompat() {
    }

    public static void registerDynamicTileEntities() {
        try {
            Class<?> registryClass = Class.forName("com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry");
            Method registerMethod = registryClass.getMethod("registerDynamicClass", String.class);
            for (int i = 0; i < DYNAMIC_TE_CLASSES.length; i++) {
                registerMethod.invoke(null, DYNAMIC_TE_CLASSES[i]);
            }
        } catch (Throwable ignored) {
            // Angelica 非導入環境または旧バージョンでは何もしない
        }
    }
}
