package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.math.PooledVec3;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avoids RenderElectricalWiring's per-frame loadedEntityList scan for TO_ENTITY wires.
 *
 * <p>Connection already exposes a cached electrical-wiring lookup path, so this mixin
 * reuses that for WIRE/TO_ENTITY while preserving the original coordinate math.
 *
 * <p><b>リフレクション排除</b>: 以前はキャッシュ越しにリフレクションでメソッド・フィールドを参照していましたが、
 * パフォーマンスを最大化するため、直接キャストとメソッド呼び出しを行う実装に変更しました。
 */
@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class RenderElectricalWiringConnectionCacheMixin {

    @Inject(method = "getConnectedTarget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$useCachedConnectedTarget(
            @Coerce Object tileEntity,
            @Coerce Object connection,
            float partialTicks,
            CallbackInfoReturnable<Object> cir) {
        if (!CrossTieConfig.connectionCacheEnabled) {
            return;
        }
        if (!(tileEntity instanceof TileEntityElectricalWiring) || !(connection instanceof Connection)) {
            return;
        }

        TileEntityElectricalWiring sourceTile = (TileEntityElectricalWiring) tileEntity;
        Connection conn = (Connection) connection;
        if (sourceTile.getWorldObj() == null) {
            return;
        }

        String typeName = conn.type != null ? conn.type.name() : "";
        if (!"WIRE".equals(typeName) && !"TO_ENTITY".equals(typeName)) {
            return;
        }

        TileEntityElectricalWiring target = conn.getElectricalWiring(sourceTile.getWorldObj());
        if (target == null || target.isInvalid()) {
            return;
        }

        Vec3 posMain = sourceTile.getWirePos();
        Vec3 posTarget = target.getWirePos();
        if (posMain == null || posTarget == null) {
            return;
        }

        double thisX = sourceTile.xCoord + 0.5D + posMain.getX();
        double thisY = sourceTile.yCoord
                + (sourceTile instanceof TileEntityDummyEW ? 0.0D : 0.5D)
                + posMain.getY();
        double thisZ = sourceTile.zCoord + 0.5D + posMain.getZ();
        double targetYOffset = target instanceof TileEntityDummyEW ? 0.0D : 0.5D;

        double x = target.xCoord + 0.5D + posTarget.getX() - thisX;
        double y = target.yCoord + targetYOffset + posTarget.getY() - thisY;
        double z = target.zCoord + 0.5D + posTarget.getZ() - thisZ;
        
        Vec3 pooledVec = PooledVec3.create(x, y, z);
        if (pooledVec != null) {
            cir.setReturnValue(pooledVec);
        }
    }
}
