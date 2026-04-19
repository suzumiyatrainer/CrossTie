package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import net.suzumiya.crosstie.util.AngelicaShaderFlagBridge;
import net.suzumiya.crosstie.util.IFTTTRenderSnapshotCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = {
        "jp.ngt.rtm.electric.RenderSignal",
        "jp.ngt.rtm.render.RenderSignal"
}, remap = false)
public abstract class RenderSignalMixin {

    @Unique
    private static final Map<Long, Long> CROSSTIE_LAST_RENDERED_VERSION = new ConcurrentHashMap<Long, Long>();

    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$applySignalShaderFixFlags(TileEntity tileEntity, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
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

        long key = crosstie$key(tileEntity);
        Long previous = CROSSTIE_LAST_RENDERED_VERSION.put(key, Long.valueOf(currentVersion));
        boolean dirty = previous == null || previous.longValue() != currentVersion;

        AngelicaShaderFlagBridge.applyFlags(true, dirty, AngelicaRenderGuard.isFallbackActive());
    }

    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$clearSignalShaderFixFlags(TileEntity tileEntity, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        AngelicaShaderFlagBridge.applyFlags(false, false, AngelicaRenderGuard.isFallbackActive());
    }

    @Unique
    private long crosstie$key(TileEntity tileEntity) {
        int dim = tileEntity.getWorldObj().provider.dimensionId;
        long hash = ((long) dim & 0xFFFFL) << 48;
        hash ^= ((long) (tileEntity.xCoord & 0x3FFFFFF)) << 22;
        hash ^= ((long) (tileEntity.zCoord & 0x3FFFFFF));
        hash ^= ((long) (tileEntity.yCoord & 0xFFFL)) << 10;
        return hash;
    }
}
