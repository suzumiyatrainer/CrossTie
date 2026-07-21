package net.suzumiya.crosstie.mixins.rtm;

import java.util.List;
import jp.ngt.ngtlib.math.PooledVec3;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import net.minecraftforge.client.MinecraftForgeClient;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.mixins.CrossTieMixinPlugin;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class RenderElectricalWiringOptimizationMixin {

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

    @Redirect(
            method = "renderElectricalWiring",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/MinecraftForgeClient;getRenderPass()I",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private int crosstie$normalizeShadowPass() {
        int pass = MinecraftForgeClient.getRenderPass();
        if (CrossTieMixinPlugin.hasOptiFineLikeEnv() && CrossTieConfig.fixOptiFineWireShadowPass) {
            return (pass < 0) ? 0 : pass;
        }
        return pass;
    }

    @Shadow
    protected abstract void renderWire(TileEntityElectricalWiring tileEntity, Connection connection, double par2, double par4, double par6, float par8, int pass);

    /**
     * @author CrossTie
     * @reason Optimize renderAllWire by removing high-overhead Java Stream API calls.
     */
    @Overwrite
    protected void renderAllWire(TileEntityElectricalWiring tileEntity, double par2, double par4, double par6, float par8, int pass) {
        boolean hasShadersFix = CrossTieMixinPlugin.hasOptiFineLikeEnv() && CrossTieConfig.fixOptiFineWireNormalize;
        if (hasShadersFix) {
            GL11.glEnable(GL11.GL_NORMALIZE);
        }

        GL11.glPushMatrix();
        Vec3 vec = tileEntity.getWirePos();
        GL11.glTranslatef((float) par2 + 0.5F + (float) vec.getX(), (float) par4 + 0.5F + (float) vec.getY(), (float) par6 + 0.5F + (float) vec.getZ());

        List<Connection> connections = tileEntity.getConnectionList();
        if (connections != null) {
            int size = connections.size();
            for (int i = 0; i < size; i++) {
                Connection connection = connections.get(i);
                if (connection != null && connection.type.isVisible && connection.isRoot) {
                    this.renderWire(tileEntity, connection, par2, par4, par6, par8, pass);
                }
            }
        }
        GL11.glPopMatrix();

        if (hasShadersFix) {
            GL11.glDisable(GL11.GL_NORMALIZE);
        }
    }
}
