package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.PartsRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Phase 4-4: {@link PartsRenderer} のメモリリーク修正および {@code renderLightEffectS}
 * の高速化。
 *
 * <ul>
 * <li>{@code hittedParts} を {@link WeakHashMap} 化し、{@code parts == null} 時は
 * {@code remove} することで アンロード済み Entity / TileEntity の強参照リークを防止する。</li>
 * <li>{@code renderLightEffectS} の {@code IntStream}＋ボクシングを廃止し、32 分割の sin/cos
 * を静的テーブル化する。</li>
 * </ul>
 */
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererOptimizationMixin {

    @Shadow
    protected Map<Object, ActionParts> hittedParts;

    @Unique
    private static final int CROSSTIE$DIV_NUM = 32;

    @Unique
    private static final double CROSSTIE$BRIGHTNESS_RATE = 1.0D / 256.0D;

    @Unique
    private static final float[] CROSSTIE$COS_POS = new float[CROSSTIE$DIV_NUM + 1];

    @Unique
    private static final float[] CROSSTIE$SIN_POS = new float[CROSSTIE$DIV_NUM + 1];

    @Unique
    private static final float[] CROSSTIE$COS_NEG = new float[CROSSTIE$DIV_NUM + 1];

    @Unique
    private static final float[] CROSSTIE$SIN_NEG = new float[CROSSTIE$DIV_NUM + 1];

    static {
        float angleStep = 360.0F / CROSSTIE$DIV_NUM;
        for (int i = 0; i <= CROSSTIE$DIV_NUM; ++i) {
            float radPos = NGTMath.toRadians((float) i * angleStep);
            CROSSTIE$COS_POS[i] = MathHelper.cos(radPos);
            CROSSTIE$SIN_POS[i] = MathHelper.sin(radPos);
            float radNeg = NGTMath.toRadians((float) -i * angleStep);
            CROSSTIE$COS_NEG[i] = MathHelper.cos(radNeg);
            CROSSTIE$SIN_NEG[i] = MathHelper.sin(radNeg);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void crosstie$useWeakHittedParts(String[] par1, CallbackInfo ci) {
        this.hittedParts = new WeakHashMap<>();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), require = 0, remap = false)
    private Object crosstie$removeNullHitEntry(Map<Object, Object> map, Object key, Object value) {
        if (value == null) {
            return map.remove(key);
        }
        return map.put(key, value);
    }

    /**
     * @author CrossTie
     * @reason IntStream/ボクシング廃止＋sin/cos 静的テーブル化でライトエフェクト描画の GC と CPU 負荷を削減
     */
    @SuppressWarnings("null")
    @Overwrite(remap = false)
    public static void renderLightEffectS(Vector3f normal, double x, double y, double z, float rL, float rS,
            float length, int color, int type, boolean reverse) {
        boolean useVec = (normal != null);
        Vector3f viewerVec = null;
        float viewerAngle = 0.0F;
        if (useVec) {
            viewerVec = PartsRenderer.getViewerVec(x, y, z);
            viewerAngle = NGTMath.toDegrees(Vector3f.angle(normal, viewerVec));
        }

        if (reverse) {
            viewerAngle = MathHelper.wrapAngleTo180_float(viewerAngle + 180.0F);
        }

        if (viewerAngle > 90.0F) {
            viewerAngle = 180.0F - viewerAngle;
        }

        float lightStrength = 1.0F;
        if (viewerAngle > 45.0F) {
            lightStrength = (90.0F - viewerAngle) / 45.0F;
        }

        Tessellator tessellator = Tessellator.instance;

        if (type == 0) {
            float radius = rL * lightStrength;
            tessellator.startDrawing(GL11.GL_TRIANGLE_FAN);
            tessellator.setColorRGBA_I(color, 0xFF);
            tessellator.addVertex(0.0D, 0.0D, 0.0D);
            tessellator.setColorRGBA_I(0x000000, 0x00);
            for (int i = 0; i <= CROSSTIE$DIV_NUM; ++i) {
                tessellator.addVertex(CROSSTIE$COS_POS[i] * radius, CROSSTIE$SIN_POS[i] * radius, 0.0D);
            }
            tessellator.draw();
        } else if (type == 1) {
            float angle = NGTMath.toDegrees((float) Math.atan2(rL, length));
            float distance = useVec ? viewerVec.lengthSquared() : 256.0F;
            float brightness;
            if (viewerAngle < angle) {
                brightness = 1.0F - (viewerAngle / angle);
            } else {
                float b0 = (viewerAngle - angle) / (90.0F - angle);
                float b1 = (float) ((double) distance * CROSSTIE$BRIGHTNESS_RATE);
                if (b1 > 1.0F) {
                    b1 = 1.0F;
                }
                brightness = b0 * b1;
            }

            if (brightness > 0.0F) {
                int alpha = (int) (255.0F * brightness);
                tessellator.startDrawing(GL11.GL_TRIANGLE_FAN);
                tessellator.setColorRGBA_I(color, alpha);
                tessellator.addVertex(0.0D, 0.0D, 0.0D);
                tessellator.setColorRGBA_I(0x000000, 0x00);
                for (int i = 0; i <= CROSSTIE$DIV_NUM; ++i) {
                    tessellator.addVertex(CROSSTIE$COS_NEG[i] * rL, CROSSTIE$SIN_NEG[i] * rL, length);
                }
                tessellator.draw();

                float b3 = (float) ((double) distance * CROSSTIE$BRIGHTNESS_RATE);
                if (b3 > 1.0F) {
                    b3 = 1.0F;
                }
                float f3 = rS * b3;
                int halfAlpha = alpha >> 1;

                tessellator.startDrawing(GL11.GL_TRIANGLES);
                for (int i = 0; i <= CROSSTIE$DIV_NUM; ++i) {
                    float c = CROSSTIE$COS_POS[i];
                    float s = CROSSTIE$SIN_POS[i];
                    tessellator.setColorRGBA_I(0x000000, 0x00);
                    tessellator.addVertex(c * rL, s * rL, length);
                    tessellator.setColorRGBA_I(color, halfAlpha);
                    tessellator.addVertex(0.0D, 0.0D, 0.0D);
                    tessellator.setColorRGBA_I(0x000000, 0x00);
                    tessellator.addVertex(c * f3, s * f3, 0.0D);
                }
                tessellator.draw();
            }
        }
    }
}
