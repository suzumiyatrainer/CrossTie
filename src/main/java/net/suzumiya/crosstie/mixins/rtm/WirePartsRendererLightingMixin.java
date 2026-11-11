package net.suzumiya.crosstie.mixins.rtm;

import javax.annotation.Nullable;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.modelpack.cfg.WireConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetWireClient;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.RenderPass;
import jp.ngt.rtm.render.TileEntityPartsRenderer;
import jp.ngt.rtm.render.WirePartsRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = WirePartsRenderer.class, remap = false)
public abstract class WirePartsRendererLightingMixin extends TileEntityPartsRenderer<ModelSetWireClient> {

    public WirePartsRendererLightingMixin(String... par1) {
        super(par1);
    }

    @Shadow
    private boolean shouldRenderObject(TileEntityElectricalWiring tileEntity, int len, int pos, int pass) {
        return true;
    }

    @Unique
    private static void crosstie$updateWireSectionBrightness(World world, double startX, double startY, double startZ,
            double wx, double wy, double wz) {
        if (world == null || OpenGlHelper.lightmapTexUnit == 0 || GLHelper.isCompiling()) {
            return;
        }
        int bx = MathHelper.floor_double(startX + wx);
        int by = MathHelper.floor_double(startY + wy);
        int bz = MathHelper.floor_double(startZ + wz);
        if (by < 0) {
            by = 0;
        }
        if (world.blockExists(bx, by, bz)) {
            int brightness = world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);
            GLHelper.setBrightness(brightness);
        }
    }

    /**
     * @author CrossTie
     * @reason Update world lightmap per wire section so a lit insulator at one end
     *         does not force the entire wire span bright in the dark, and avoid
     *         IntStream allocation overhead per frame.
     */
    @Overwrite
    public void renderWireStraight(TileEntityElectricalWiring tileEntity, Connection connection, Vec3 target,
            float par8, int pass, @Nullable Parts parts) {
        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glRotatef(target.getYaw() + 180.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(target.getPitch() - 90.0F, 1.0F, 0.0F, 0.0F);

        ModelSetWireClient modelSet = (ModelSetWireClient) connection.getModelSet();
        WireConfig cfg = modelSet.getConfig();
        double length = target.length();
        int split = MathHelper.floor_double(length / cfg.sectionLength);
        if (split <= 0) {
            GL11.glPopMatrix();
            return;
        }
        float scaleY = (float) ((length / (double) split) / cfg.sectionLength);
        GL11.glScalef(1.0F, scaleY, 1.0F);

        World world = tileEntity != null ? tileEntity.getWorldObj() : null;
        boolean updateLight = world != null && pass != RenderPass.LIGHT.id && OpenGlHelper.lightmapTexUnit != 0
                && !GLHelper.isCompiling();

        double startX = 0.0D;
        double startY = 0.0D;
        double startZ = 0.0D;
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        if (updateLight) {
            @SuppressWarnings("null")
            Vec3 posMain = tileEntity.getWirePos();
            double yOffset = (tileEntity instanceof TileEntityDummyEW) ? 0.0D : 0.5D;
            startX = tileEntity.xCoord + 0.5D + (posMain != null ? posMain.getX() : 0.0D);
            startY = tileEntity.yCoord + yOffset + (posMain != null ? posMain.getY() : 0.0D);
            startZ = tileEntity.zCoord + 0.5D + (posMain != null ? posMain.getZ() : 0.0D);
        }

        double invSplit = 1.0D / (double) split;
        for (int i = 0; i < split; ++i) {
            if (this.shouldRenderObject(tileEntity, split, i, pass)) {
                if (updateLight) {
                    double t = ((double) i + 0.5D) * invSplit;
                    crosstie$updateWireSectionBrightness(world, startX, startY, startZ, targetX * t, targetY * t,
                            targetZ * t);
                }
                if (parts == null) {
                    modelSet.modelObj.model.renderAll(cfg.smoothing);
                } else {
                    parts.render((WirePartsRenderer) (Object) this);
                }
            }
            GL11.glTranslatef(0.0F, cfg.sectionLength, 0.0F);
        }

        if (updateLight) {
            crosstie$updateWireSectionBrightness(world, startX, startY, startZ, 0.0D, 0.0D, 0.0D);
        }

        GL11.glPopMatrix();
    }

    /**
     * @author CrossTie
     * @reason Update world lightmap per deflected wire section so a lit insulator
     *         at one end does not force the entire catenary wire bright in the
     *         dark.
     */
    @Overwrite
    public void renderWireDeflection(TileEntityElectricalWiring tileEntity, Connection connection, Vec3 target,
            float par8, int pass, @Nullable Parts parts) {
        double lx = Math.sqrt(target.getX() * target.getX() + target.getZ() * target.getZ());
        if (lx == 0.0D) {
            this.renderWireStraight(tileEntity, connection, target, par8, pass, parts);
            return;
        }

        ModelSetWireClient modelSet = (ModelSetWireClient) connection.getModelSet();
        WireConfig cfg = modelSet.getConfig();

        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glRotatef(target.getYaw(), 0.0F, 1.0F, 0.0F);
        float pitch = target.getPitch();
        double ly = target.getY();
        float lc = 1.0F + cfg.lengthCoefficient;
        double alpha = cfg.deflectionCoefficient * NGTMath.cos(pitch) / Math.pow(lc, lx);
        double a = 0.0D;
        if (lx > 0.0D) {
            a = (lx - (ly / (alpha * lx))) / 2.0D;
        }

        World world = tileEntity != null ? tileEntity.getWorldObj() : null;
        boolean updateLight = world != null && pass != RenderPass.LIGHT.id && OpenGlHelper.lightmapTexUnit != 0
                && !GLHelper.isCompiling();

        double startX = 0.0D;
        double startY = 0.0D;
        double startZ = 0.0D;
        double dirX = target.getX() / lx;
        double dirZ = target.getZ() / lx;
        if (updateLight) {
            @SuppressWarnings("null")
            Vec3 posMain = tileEntity.getWirePos();
            double yOffset = (tileEntity instanceof TileEntityDummyEW) ? 0.0D : 0.5D;
            startX = tileEntity.xCoord + 0.5D + (posMain != null ? posMain.getX() : 0.0D);
            startY = tileEntity.yCoord + yOffset + (posMain != null ? posMain.getY() : 0.0D);
            startZ = tileEntity.zCoord + 0.5D + (posMain != null ? posMain.getZ() : 0.0D);
        }

        double x = 0.0D;
        int i = 0;
        while (x < lx) {
            GL11.glPushMatrix();
            double y = alpha * ((x * x) - (2.0D * a * x));
            double slope = 2.0D * alpha * (x - a);
            double slopeRad = Math.atan(slope);
            float dx = MathHelper.cos((float) slopeRad) * cfg.sectionLength * 0.99F;
            if (dx <= 1.0E-4F) {
                dx = cfg.sectionLength * 0.99F;
            }
            double nextX = x + dx;
            double nextY = alpha * ((nextX * nextX) - (2.0D * a * nextX));
            double cX = (x + nextX) * 0.5D;
            double cY = (y + nextY) * 0.5D;
            double difX = nextX - x;
            double difY = nextY - y;
            float pitchC = -(float) NGTMath.toDegrees(Math.atan2(difY, difX));
            GL11.glTranslatef(0.0F, (float) cY, (float) cX);
            GL11.glRotatef(pitchC + 90.0F, 1.0F, 0.0F, 0.0F);
            GL11.glTranslatef(0.0F, -cfg.sectionLength * 0.5F, 0.0F);
            if (this.shouldRenderObject(tileEntity, 0, i, pass)) {
                if (updateLight) {
                    crosstie$updateWireSectionBrightness(world, startX, startY, startZ, dirX * cX, cY, dirZ * cX);
                }
                if (parts == null) {
                    modelSet.modelObj.model.renderAll(cfg.smoothing);
                } else {
                    parts.render((WirePartsRenderer) (Object) this);
                }
            }
            GL11.glPopMatrix();

            x = nextX;
            ++i;
        }

        if (updateLight) {
            crosstie$updateWireSectionBrightness(world, startX, startY, startZ, 0.0D, 0.0D, 0.0D);
        }

        GL11.glPopMatrix();
    }
}
