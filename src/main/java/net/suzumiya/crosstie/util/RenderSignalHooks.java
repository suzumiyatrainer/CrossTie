package net.suzumiya.crosstie.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.config.CrossTieConfig;

public final class RenderSignalHooks {

    private static final Map<Long, Long> LAST_RENDERED_VERSION = new ConcurrentHashMap<Long, Long>();

    private RenderSignalHooks() {
    }

    public static void onRenderStart(TileEntity tileEntity, double x, double y, double z) {
        if (tileEntity == null || tileEntity.getWorldObj() == null) {
            AngelicaShaderFlagBridge.applyFlags(true, false, AngelicaRenderGuard.isFallbackActive());
            return;
        }

        if (CrossTieConfig.enableAngelicaFallbackGuard && (AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z))) {
            AngelicaRenderGuard.triggerFallback();
        }

        long currentVersion = 0L;
        if (CrossTieConfig.enableAngelicaIfTTTCache) {
            currentVersion = IFTTTRenderSnapshotCache.getVersion(
                    tileEntity.getWorldObj(),
                    tileEntity.xCoord,
                    tileEntity.yCoord,
                    tileEntity.zCoord);
        }

        long key = getTileKey(tileEntity);
        Long previous = LAST_RENDERED_VERSION.put(Long.valueOf(key), Long.valueOf(currentVersion));
        boolean dirty = previous == null || previous.longValue() != currentVersion;

        AngelicaShaderFlagBridge.applyFlags(true, dirty, AngelicaRenderGuard.isFallbackActive());
    }

    public static void onRenderEnd() {
        AngelicaShaderFlagBridge.applyFlags(false, false, AngelicaRenderGuard.isFallbackActive());
    }

    private static long getTileKey(TileEntity tileEntity) {
        int dim = tileEntity.getWorldObj().provider.dimensionId;
        long hash = ((long) dim & 0xFFFFL) << 48;
        hash ^= ((long) (tileEntity.xCoord & 0x3FFFFFF)) << 22;
        hash ^= ((long) (tileEntity.zCoord & 0x3FFFFFF));
        hash ^= ((long) (tileEntity.yCoord & 0xFFFL)) << 10;
        return hash;
    }
}
