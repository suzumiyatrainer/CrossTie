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
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RailPartsRenderer の描画を安定化しつつ、hi03ExpressRailway だけ特別扱いする。
 */
@Mixin(targets = "jp.ngt.rtm.render.RailPartsRenderer", remap = false)
public abstract class RTMRailPartsRenderMixin {

    /**
     * hi03ExpressRailway のときだけ、描画コンテキストを有効にする。
     */
    @Inject(method = "renderRail(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;IDDDF)V", at = @At("HEAD"), remap = false)
    private void crosstie$enterHi03Context(@Coerce Object tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        try {
            String railModel = crosstie$getRailModel((TileEntity) tileEntity);
            if (railModel != null && railModel.contains("hi03ExpressRailway")) {
                Hi03ExpressRailwayContext.enter();
            }
        } catch (Exception e) {
            // 失敗しても通常描画に戻すだけにする
        }
    }

    /**
     * renderRail の終了時にコンテキストを必ず解除する。
     */
    @Inject(method = "renderRail(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;IDDDF)V", at = @At("RETURN"), remap = false)
    private void crosstie$exitHi03Context(@Coerce Object tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        Hi03ExpressRailwayContext.exit();
    }

    /**
     * 描画距離外のレール部品は描画しない。
     */
    @Inject(method = "renderRail(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;IDDDF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRailParts(@Coerce Object tileEntity, int index, double x, double y, double z,
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

        // TileEntity の距離判定は二乗距離
        if (((TileEntity) tileEntity).getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
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
