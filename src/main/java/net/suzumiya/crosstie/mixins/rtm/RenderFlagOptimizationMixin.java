package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.RenderFlag;
import jp.ngt.rtm.block.tileentity.TileEntityFlag;
import jp.ngt.rtm.modelpack.texture.FlagProperty;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Phase 4-4: {@link RenderFlag#renderFlag} の二重ネスト {@code IntStream} を通常の {@code for} ループへ置換する。
 */
@Mixin(value = RenderFlag.class, remap = false)
public abstract class RenderFlagOptimizationMixin extends TileEntitySpecialRenderer {

    @Shadow
    private float getR(float r, float u, float v) {
        return 0.0F;
    }

    @Shadow
    private float getWave(float r, float u) {
        return 0.0F;
    }

    @Shadow
    private float getNormalR(float r) {
        return 0.0F;
    }

    /**
     * @author CrossTie
     * @reason ネスト IntStream による毎フレームのラムダ/Stream アロケーションを排除
     */
    @Overwrite(remap = false)
    private void renderFlag(TileEntityFlag tileEntity, double par2, double par4, double par6, float par8) {
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glTranslatef((float) par2 + 0.5F, (float) par4 + 1.0F, (float) par6 + 0.5F);
        float roll = tileEntity.getRotationRoll();
        GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
        float pitch = tileEntity.getRotationPitch();
        GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
        float yaw = tileEntity.getRotationYaw();
        GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);

        FlagProperty property = tileEntity.getProperty();
        float wind = 1.0F;
        float windInv = 0.0F;
        float h = property.height;
        float w = property.width;
        float wave = tileEntity.wave;
        int resV = property.resolutionV;
        int resU = property.resolutionU;
        float invResV = 1.0F / (float) resV;
        float invResU = 1.0F / (float) resU;

        GL11.glShadeModel(GL11.GL_SMOOTH);
        this.bindTexture(property.getTexture());
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int i = 0; i < resV; ++i) {
            float v0 = (float) i * invResV;
            float v1 = (float) (i + 1) * invResV;
            for (int j = 0; j < resU; ++j) {
                float u0 = (float) j * invResU;
                float u1 = (float) (j + 1) * invResU;
                float u0w = u0 * w;
                float u1w = u1 * w;

                float r0 = this.getR(wave, u1, v0);
                float d0 = this.getWave(r0, u1);
                float nr0 = this.getNormalR(r0 + yaw);
                tessellator.setNormal(MathHelper.sin(nr0), 0.0F, MathHelper.cos(nr0));
                tessellator.addVertexWithUV(d0, -(v0 + windInv * u1w) * h, u1w * wind, u1, v0);

                float r1 = this.getR(wave, u1, v1);
                float d1 = this.getWave(r1, u1);
                float nr1 = this.getNormalR(r1 + yaw);
                tessellator.setNormal(MathHelper.sin(nr1), 0.0F, MathHelper.cos(nr1));
                tessellator.addVertexWithUV(d1, -(v1 + windInv * u1w) * h, u1w * wind, u1, v1);

                float r2 = this.getR(wave, u0, v1);
                float d2 = this.getWave(r2, u0);
                float nr2 = this.getNormalR(r2 + yaw);
                tessellator.setNormal(MathHelper.sin(nr2), 0.0F, MathHelper.cos(nr2));
                tessellator.addVertexWithUV(d2, -(v1 + windInv * u0w) * h, u0w * wind, u0, v1);

                float r3 = this.getR(wave, u0, v0);
                float d3 = this.getWave(r3, u0);
                float nr3 = this.getNormalR(r3 + yaw);
                tessellator.setNormal(MathHelper.sin(nr3), 0.0F, MathHelper.cos(nr3));
                tessellator.addVertexWithUV(d3, -(v0 + windInv * u0w) * h, u0w * wind, u0, v0);
            }
        }
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_CULL_FACE);

        GL11.glPopMatrix();
    }
}
