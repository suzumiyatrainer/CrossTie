package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.entity.vehicle.RenderVehicleBase;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.Rollsign;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Phase 4-4: {@link RenderVehicleBase} の未使用 {@code getLightValue} 呼び出し排除と
 * {@code renderRollsign} の {@code Arrays.stream} 廃止。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = RenderVehicleBase.class, remap = false)
public abstract class RenderVehicleBaseOptimizationMixin extends Render {

    /**
     * renderVehicleMain L107 の {@code int value = this.getLightValue(vehicle);} は
     * 戻り値が一切使用されないデッドコード（毎フレームワールド光源取得を実行）のため 0 を返してスキップする。
     */
    @Redirect(
        method = "renderVehicleMain(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;Ljp/ngt/rtm/modelpack/modelset/ModelSetVehicleBaseClient;F)V",
        at = @At(
            value = "INVOKE",
            target = "Ljp/ngt/rtm/entity/vehicle/RenderVehicleBase;getLightValue(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;)I",
            remap = false
        ),
        require = 0,
        remap = false
    )
    private int crosstie$skipUnusedGetLightValue(RenderVehicleBase self, EntityVehicleBase vehicle) {
        return 0;
    }

    /**
     * @author CrossTie
     * @reason Arrays.stream による毎フレームの Stream 生成を排除
     */
    @Overwrite(remap = false)
    protected void renderRollsign(EntityVehicleBase vehicle, ModelSetVehicleBaseClient modelset) {
        GL11.glPushMatrix();
        this.bindTexture(modelset.rollsignTexture);

        Rollsign[] rollsigns = modelset.getConfig().rollsigns;
        int rollsignNameCount = modelset.getConfig().rollsignNames.length;
        Tessellator tessellator = Tessellator.instance;

        for (int i = 0; i < rollsigns.length; ++i) {
            Rollsign rollsign = rollsigns[i];

            if (!rollsign.disableLighting) {
                GLHelper.disableLighting();
                GLHelper.setLightmapMaxBrightness();
            }

            float f0 = (rollsign.uv[3] - rollsign.uv[2]) / rollsignNameCount;
            float uMin = rollsign.uv[0];
            float uMax = rollsign.uv[1];
            float f1 = 0.0F;
            if (vehicle instanceof EntityTrainBase) {
                EntityTrainBase train = (EntityTrainBase) vehicle;
                f1 = rollsign.doAnimation ? train.getRollsignAnimation() : train.getTrainStateData(8);
            }
            float vMin = rollsign.uv[2] + f0 * f1;
            float vMax = rollsign.uv[2] + f0 * (f1 + 1.0F);

            tessellator.startDrawingQuads();
            float[][][] positions = rollsign.pos;
            for (int p = 0; p < positions.length; ++p) {
                float[][] pos = positions[p];
                tessellator.addVertexWithUV(pos[3][0], pos[3][1], pos[3][2], uMin, vMin);
                tessellator.addVertexWithUV(pos[2][0], pos[2][1], pos[2][2], uMin, vMax);
                tessellator.addVertexWithUV(pos[1][0], pos[1][1], pos[1][2], uMax, vMax);
                tessellator.addVertexWithUV(pos[0][0], pos[0][1], pos[0][2], uMax, vMin);
            }
            tessellator.draw();

            if (!rollsign.disableLighting) {
                GLHelper.enableLighting();
            }
        }

        GL11.glPopMatrix();
    }
}
