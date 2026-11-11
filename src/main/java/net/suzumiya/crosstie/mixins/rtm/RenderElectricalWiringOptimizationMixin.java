package net.suzumiya.crosstie.mixins.rtm;

import java.util.List;
import jp.ngt.ngtlib.math.PooledVec3;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.TileEntityConnectorBase;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import jp.ngt.rtm.electric.TileEntityInsulator;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.cache.ElectricalWiringCacheManager;
import net.suzumiya.crosstie.utils.CrossTieDiagnostics;
import net.suzumiya.crosstie.mixins.CrossTieMixinPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class RenderElectricalWiringOptimizationMixin {

    @Unique
    private static final double LOD_DISTANCE_SQ = 64.0D * 64.0D; // 64m

    /**
     * 遠距離・視界限界外の架線・碍子描画をカリング
     */
    @Inject(method = "renderElectricalWiring", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$cullDistantWiring(TileEntityElectricalWiring tileEntity, double par2, double par4, double par6, float par8, CallbackInfo ci) {
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.electricalWiringRenderCalls.incrementAndGet();
        }

        double distSq = par2 * par2 + par4 * par4 + par6 * par6;
        double maxDistSq = NGTUtil.getChunkLoadDistanceSq();

        // 視界限界距離外は完全カリング
        if (distSq > maxDistSq) {
            ci.cancel();
            return;
        }

        // 64m 以降でかつ純粋な碍子（BlockInsulator）の場合は高負荷パスをカリング/簡易描画
        if (distSq > LOD_DISTANCE_SQ && tileEntity instanceof TileEntityInsulator) {
            Boolean isDecorative = ElectricalWiringCacheManager.get(tileEntity);
            if (isDecorative != null && isDecorative) {
                // パス1(透明/エフェクト)の重い描画を64m以降省略
                int pass = MinecraftForgeClient.getRenderPass();
                if (pass == 1) {
                    ci.cancel();
                }
            }
        }
    }

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

    @Unique
    private static int crosstie$getConnectorBrightness(TileEntityConnectorBase tileEntity) {
        World world = tileEntity.getWorldObj();
        if (world == null) {
            return -1;
        }
        int bx = MathHelper.floor_double(tileEntity.xCoord + 0.5D + tileEntity.getOffsetX());
        int by = MathHelper.floor_double(tileEntity.yCoord + 0.5D + tileEntity.getOffsetY());
        int bz = MathHelper.floor_double(tileEntity.zCoord + 0.5D + tileEntity.getOffsetZ());
        if (by < 0) {
            by = 0;
        }
        if (world.blockExists(bx, by, bz)) {
            return world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);
        }
        if (world.blockExists(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
            return world.getLightBrightnessForSkyBlocks(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, 0);
        }
        return -1;
    }

    @Unique
    private static int crosstie$getWireRootBrightness(TileEntityElectricalWiring tileEntity) {
        World world = tileEntity.getWorldObj();
        if (world == null) {
            return -1;
        }
        Vec3 pos = tileEntity.getWirePos();
        double yOffset = (tileEntity instanceof TileEntityDummyEW) ? 0.0D : 0.5D;
        int bx = MathHelper.floor_double(tileEntity.xCoord + 0.5D + (pos != null ? pos.getX() : 0.0D));
        int by = MathHelper.floor_double(tileEntity.yCoord + yOffset + (pos != null ? pos.getY() : 0.0D));
        int bz = MathHelper.floor_double(tileEntity.zCoord + 0.5D + (pos != null ? pos.getZ() : 0.0D));
        if (by < 0) {
            by = 0;
        }
        if (world.blockExists(bx, by, bz)) {
            return world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);
        }
        if (world.blockExists(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
            return world.getLightBrightnessForSkyBlocks(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, 0);
        }
        return -1;
    }

    @Inject(method = "renderConnector", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$preRenderConnector(TileEntityConnectorBase tileEntity, double par2, double par4, double par6, float par8, int pass, CallbackInfo ci) {
        GL11.glEnable(GL11.GL_NORMALIZE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (OpenGlHelper.lightmapTexUnit != 0 && !GLHelper.isCompiling()) {
            int brightness = crosstie$getConnectorBrightness(tileEntity);
            if (brightness >= 0) {
                GLHelper.setBrightness(brightness);
            }
        }
    }

    @Inject(method = "renderConnector", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$postRenderConnector(TileEntityConnectorBase tileEntity, double par2, double par4, double par6, float par8, int pass, CallbackInfo ci) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_NORMALIZE);
        if (OpenGlHelper.lightmapTexUnit != 0 && !GLHelper.isCompiling()) {
            int brightness = crosstie$getWireRootBrightness(tileEntity);
            if (brightness >= 0) {
                GLHelper.setBrightness(brightness);
            }
        }
    }

    @Inject(method = "renderElectricalWiring", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$postRenderElectricalWiring(TileEntityElectricalWiring tileEntity, double par2, double par4, double par6, float par8, CallbackInfo ci) {
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * @author CrossTie
     * @reason Optimize renderAllWire by removing high-overhead Java Stream API calls
     *         and ensure normal/color/lightmap state is clean across each wire connection.
     */
    @Overwrite
    protected void renderAllWire(TileEntityElectricalWiring tileEntity, double par2, double par4, double par6, float par8, int pass) {
        boolean hasShadersFix = CrossTieConfig.fixOptiFineWireNormalize;
        if (hasShadersFix) {
            GL11.glEnable(GL11.GL_NORMALIZE);
        }

        int baseBrightness = (OpenGlHelper.lightmapTexUnit != 0 && !GLHelper.isCompiling())
                ? crosstie$getWireRootBrightness(tileEntity)
                : -1;

        GL11.glPushMatrix();
        Vec3 vec = tileEntity.getWirePos();
        GL11.glTranslatef((float) par2 + 0.5F + (float) vec.getX(), (float) par4 + 0.5F + (float) vec.getY(), (float) par6 + 0.5F + (float) vec.getZ());

        List<Connection> connections = tileEntity.getConnectionList();
        if (connections != null) {
            int size = connections.size();
            for (int i = 0; i < size; i++) {
                Connection connection = connections.get(i);
                if (connection != null && connection.type.isVisible && connection.isRoot) {
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    if (baseBrightness >= 0 && !GLHelper.isCompiling()) {
                        GLHelper.setBrightness(baseBrightness);
                    }
                    this.renderWire(tileEntity, connection, par2, par4, par6, par8, pass);
                }
            }
        }
        GL11.glPopMatrix();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (hasShadersFix) {
            GL11.glDisable(GL11.GL_NORMALIZE);
        }
    }
}
