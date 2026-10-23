package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.gtnewhorizons.angelica.render.WeatherRenderer", remap = false)
public abstract class WeatherRendererRainOcclusionMixin {

    @Shadow
    private static int builtRevision;

    @Shadow
    private static int builtUpdateCount;

    /**
     * 高速走行対応: レンダリングフレームの partialTicks を記録し、
     * 車両が存在する場合は雨インスタンスの再構築を毎フレーム促す。
     */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void crosstie$onWeatherRenderHead(float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        VehicleWeatherManager.setPartialTicks(partialTicks);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            if (VehicleWeatherManager.hasNearbyVehicles(mc.theWorld)) {
                // 車両移動に1フレームの遅延もなく追従させるため再構築をトリガー
                builtRevision = -9999;
                builtUpdateCount = -9999;
            }
        }
    }

    /**
     * Instanced Weather の各列書き込み時、リアルタイムな車両遮蔽高さを反映
     */
    @Redirect(
            method = "writeColumns",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/render/WeatherColumns;heightAtSlot(I)I"
            ),
            require = 0
    )
    private static int crosstie$redirectHeightAtSlot(com.gtnewhorizons.angelica.render.WeatherColumns columns, int slot) {
        int defaultHeight = 0;
        try {
            defaultHeight = columns.heightAtSlot(slot);
        } catch (Throwable ignored) {
        }

        try {
            WeatherColumnsAccessor accessor = (WeatherColumnsAccessor) (Object) columns;
            int[] keyX = accessor.getKeyX();
            int[] keyZ = accessor.getKeyZ();
            if (keyX != null && keyZ != null && slot >= 0 && slot < keyX.length) {
                int x = keyX[slot];
                int z = keyZ[slot];
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.theWorld != null) {
                    return VehicleWeatherManager.getOccludedPrecipitationHeight(mc.theWorld, x, z, defaultHeight);
                }
            }
        } catch (Throwable ignored) {
        }

        return defaultHeight;
    }

    /**
     * Tessellator Weather（シェーダー互換モード等）の描画時、リアルタイムな車両遮蔽高さを反映
     */
    @Redirect(
            method = "renderTessellator",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/render/WeatherColumns;heightAt(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectHeightAtTessellator(com.gtnewhorizons.angelica.render.WeatherColumns columns, int x, int z) {
        int defaultHeight = columns.heightAt(x, z);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            return VehicleWeatherManager.getOccludedPrecipitationHeight(mc.theWorld, x, z, defaultHeight);
        }
        return defaultHeight;
    }

    /**
     * 雨パーティクル生成時、WeatherColumns からの高度参照をリダイレクト
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/render/WeatherColumns;heightAt(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectHeightAtParticles(com.gtnewhorizons.angelica.render.WeatherColumns columns, int x, int z) {
        int defaultHeight = columns.heightAt(x, z);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            return VehicleWeatherManager.getOccludedPrecipitationHeight(mc.theWorld, x, z, defaultHeight);
        }
        return defaultHeight;
    }

    /**
     * 雨パーティクル生成時、World からのフォールバック高度参照をリダイレクト (WorldClient)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectAngelicaParticleHeightClient(WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    /**
     * 雨パーティクル生成時、World からのフォールバック高度参照をリダイレクト (World)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectAngelicaParticleHeightWorld(World world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
