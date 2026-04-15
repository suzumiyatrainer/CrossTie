package net.suzumiya.crosstie.mixins.rtm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RailPartsRenderer optimization and compatibility mixin.
 *
 * 讖溯・:
 * 1. 霍晞屬繝吶・繧ｹ縺ｮ繧ｫ繝ｪ繝ｳ繧ｰ譛驕ｩ蛹・
 * 2. hi03ExpressRailway Angelica莠呈鋤諤ｧ菫ｮ豁｣
 */
@Mixin(targets = "jp.ngt.rtm.render.RailPartsRenderer", remap = false)
public abstract class RTMRailPartsRenderMixin {

    /**
     * hi03ExpressRailway繝｢繝・Ν讀懷・縺ｨ繧ｳ繝ｳ繝・く繧ｹ繝域怏蜉ｹ蛹・
     * renderRail髢句ｧ区凾縺ｫ繝｢繝・Ν蜷阪ｒ繝√ぉ繝・け
     */
    @Inject(method = "renderRail", at = @At("HEAD"), remap = false)
    private void crosstie$enterHi03Context(TileEntity tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        try {
            String railModel = crosstie$getRailModel(tileEntity);
            if (railModel != null && railModel.contains("hi03ExpressRailway")) {
                Hi03ExpressRailwayContext.enter();
            }
        } catch (Exception e) {
            // 繝｢繝・Ν蜷榊叙蠕怜､ｱ謨玲凾縺ｯ辟｡隕・螳牙・縺ｫ繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ)
        }
    }

    /**
     * renderRail邨ゆｺ・凾縺ｫ繧ｳ繝ｳ繝・く繧ｹ繝医ｒ遒ｺ螳溘↓邨ゆｺ・
     */
    @Inject(method = "renderRail", at = @At("RETURN"), remap = false)
    private void crosstie$exitHi03Context(TileEntity tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        Hi03ExpressRailwayContext.exit();
    }

    /**
     * 霍晞屬繝吶・繧ｹ縺ｮ繧ｫ繝ｪ繝ｳ繧ｰ譛驕ｩ蛹・
     * 繝励Ξ繧､繝､繝ｼ縺九ｉ驕縺吶℃繧九Ξ繝ｼ繝ｫ縺ｮ繝ｬ繝ｳ繝繝ｪ繝ｳ繧ｰ繧偵せ繧ｭ繝・・
     */
    @Inject(method = "renderRail", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRailParts(TileEntity tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null) {
            return;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        double cullDist = renderChunks * 16.0;

        // TileEntity.getDistanceFrom returns squared distance
        if (tileEntity.getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist) {
            ci.cancel();
        }
    }

    private String crosstie$getRailModel(TileEntity tileEntity) {
        try {
            Method getProperty = tileEntity.getClass().getMethod("getProperty");
            Object property = getProperty.invoke(tileEntity);
            if (property == null) {
                return null;
            }

            Field railModelField = property.getClass().getField("railModel");
            Object railModel = railModelField.get(property);
            return railModel instanceof String ? (String) railModel : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
