package net.suzumiya.crosstie.mixins.angelica;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Syncs block rebuild requests from Angelica/Celeritas to RTM rail TESR
 * lighting.
 * <p>
 * When a block update occurs (e.g., placing/removing a light source),
 * Angelica's
 * {@code scheduleRebuildForBlockArea()} rebuilds the chunk mesh but does not
 * notify
 * TESRs. This mixin detects RTM rail tile entities in the affected area and
 * forces
 * them to recompile their display lists so lighting updates are reflected
 * immediately.
 */
@Mixin(org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer.class)
public abstract class AngelicaRebuildSyncRTMRailMixin {

    // 【修正】クラッシュの原因となっていたターゲット内に存在しない @Shadow private World world; を削除

    /**
     * Injected at the tail of scheduleRebuildForBlockArea to sync lighting to RTM
     * rails.
     */
    @Inject(method = "scheduleRebuildForBlockArea", at = @At("TAIL"), require = 0, remap = false)
    private void crosstie$syncRails(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important,
            CallbackInfo ci) {
        if (!CrossTieConfig.fixAngelicaRebuildSync) {
            return;
        }
        // 【修正】Minecraftのクライアントインスタンスから現在のワールドを安全に取得
        World clientWorld = Minecraft.getMinecraft().theWorld;
        if (clientWorld == null) {
            return;
        }

        // Iterate through chunks that intersect the rebuild region instead of the global TileEntity list.
        // This reduces the number of checked entities from thousands (or tens of thousands) to a few dozens.
        int chunkMinX = minX >> 4;
        int chunkMaxX = maxX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = maxZ >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                if (!clientWorld.getChunkProvider().chunkExists(cx, cz)) {
                    continue;
                }
                net.minecraft.world.chunk.Chunk chunk = clientWorld.getChunkFromChunkCoords(cx, cz);
                @SuppressWarnings("unchecked")
                java.util.Collection<TileEntity> chunkTiles = ((java.util.Map<?, TileEntity>) chunk.chunkTileEntityMap).values();
                for (TileEntity te : chunkTiles) {
                    if (te instanceof TileEntityLargeRailCore) {
                        TileEntityLargeRailCore rail = (TileEntityLargeRailCore) te;
                        int tx = te.xCoord;
                        int ty = te.yCoord;
                        int tz = te.zCoord;
                        if (tx >= minX && tx <= maxX && ty >= minY && ty <= maxY && tz >= minZ && tz <= maxZ) {
                            rail.shouldRerenderRail = true;
                        }
                    }
                }
            }
        }
    }
}