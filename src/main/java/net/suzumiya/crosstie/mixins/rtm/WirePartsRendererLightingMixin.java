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
import net.suzumiya.crosstie.client.WireDisplayListCache;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WirePartsRenderer.class, remap = false)
public abstract class WirePartsRendererLightingMixin extends TileEntityPartsRenderer<ModelSetWireClient> {

    public WirePartsRendererLightingMixin(String... par1) {
        super(par1);
    }

    @Shadow
    private boolean useScript;

    @Shadow
    private boolean shouldRenderObject(TileEntityElectricalWiring tileEntity, int len, int pos, int pass) {
        return true;
    }

    /** 0=未判定, 1=空/no-op, 2=非空 */
    @Unique
    private byte crosstie$wireStaticMode = 0;

    /** 0=未判定, 1=RenderBasicWire, 2=RenderSimpleCatenary, 3=その他カスタム */
    @Unique
    private byte crosstie$wireDynamicKind = 0;

    @Unique
    private static boolean crosstie$isEmptyJsFunc(String fnSource) {
        if (fnSource == null || fnSource.isEmpty()) {
            return true;
        }
        String noComments = fnSource.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//[^\\r\\n]*", "");
        int open = noComments.indexOf('{');
        int close = noComments.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return false;
        }
        String compact = noComments.substring(open + 1, close).replaceAll("\\s+", "");
        return compact.isEmpty() || ";".equals(compact) || "return;".equals(compact) || "return".equals(compact);
    }

    @Unique
    private byte crosstie$resolveWireStaticMode() {
        if (this.crosstie$wireStaticMode != 0) {
            return this.crosstie$wireStaticMode;
        }
        javax.script.ScriptEngine se = this.getScript();
        if (se == null) {
            this.crosstie$wireStaticMode = 1;
            return 1;
        }
        try {
            Object srcObj = se.eval("typeof renderWireStatic === 'function' ? String(renderWireStatic) : ''");
            String src = srcObj != null ? srcObj.toString() : "";
            this.crosstie$wireStaticMode = crosstie$isEmptyJsFunc(src) ? (byte) 1 : (byte) 2;
        } catch (Throwable t) {
            this.crosstie$wireStaticMode = 2;
        }
        return this.crosstie$wireStaticMode;
    }

    @Unique
    private byte crosstie$resolveWireDynamicKind() {
        if (this.crosstie$wireDynamicKind != 0) {
            return this.crosstie$wireDynamicKind;
        }
        javax.script.ScriptEngine se = this.getScript();
        if (se == null) {
            this.crosstie$wireDynamicKind = 3;
            return 3;
        }
        try {
            Object srcObj = se.eval("typeof renderWireDynamic === 'function' ? String(renderWireDynamic) : ''");
            String src = srcObj != null ? srcObj.toString() : "";
            String compact = src.replaceAll("/\\*[\\s\\S]*?\\*/", "")
                    .replaceAll("//[^\\r\\n]*", "")
                    .replaceAll("\\s+", "");
            if (compact.contains("GL_TRIANGLE_STRIP")
                    && compact.contains("0.025")
                    && compact.contains("setColorOpaque_I(0)")
                    && !compact.contains("tileEntity.")) {
                this.crosstie$wireDynamicKind = 1;
            } else if (compact.contains("startDrawingQuads()")
                    && compact.contains("-0.8125")
                    && compact.contains("*1.2")
                    && !compact.contains("tileEntity.")) {
                this.crosstie$wireDynamicKind = 2;
            } else {
                this.crosstie$wireDynamicKind = 3;
            }
        } catch (Throwable t) {
            this.crosstie$wireDynamicKind = 3;
        }
        return this.crosstie$wireDynamicKind;
    }

    @Inject(method = "renderWireStatic", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipEmptyWireStatic(TileEntityElectricalWiring tileEntity, Connection connection, Vec3 target,
            float par8, int pass, CallbackInfo ci) {
        if (!this.useScript || this.crosstie$resolveWireStaticMode() == 1) {
            ci.cancel();
        }
    }

    /**
     * Phase 5-3 & 5.5: スクリプト無し架線・桁橋 ({@code !useScript}) および標準スクリプト架線
     * ({@code RenderBasicWire.js} / {@code RenderSimpleCatenary.js}) の 1 本 1 単一 VBO DisplayList 描画。
     * {@code bindTexture} は親の {@code renderWire} で外側にて完了済みのため OptiFine シェーダー安全。
     */
    @Inject(method = "renderWireDynamic", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$tryRenderWireSingleVbo(TileEntityElectricalWiring tileEntity, Connection connection, Vec3 target,
            float par8, int pass, CallbackInfo ci) {
        if (!this.useScript) {
            if (pass == RenderPass.NORMAL.id && WireDisplayListCache.tryRenderCached(tileEntity, connection, target, pass)) {
                ci.cancel();
            }
            return;
        }
        byte kind = this.crosstie$resolveWireDynamicKind();
        if ((kind == 1 || kind == 2)
                && WireDisplayListCache.tryRenderStandardScriptWire(tileEntity, connection, target, pass, kind)) {
            ci.cancel();
        }
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
