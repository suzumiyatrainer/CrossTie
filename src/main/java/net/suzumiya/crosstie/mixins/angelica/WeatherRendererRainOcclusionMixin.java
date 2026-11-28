package net.suzumiya.crosstie.mixins.angelica;

import com.gtnewhorizons.angelica.render.WeatherColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica の WeatherRenderer（Instanced Weather / Tessellator Weather / 雨パーティクル）に対して
 * RTM 車両の屋根による降水遮蔽を極軽量に適用する Mixin。
 * <p>
 * WeatherColumns の地形キャッシュ自体は汚染せず（通過後のゴースト屋根を防止）、
 * 描画・パーティクル生成時の読み出し側でのみ車両遮蔽を合成する。
 * また、近傍車両の状態ハッシュが変化したフレームのみ Instanced VBO の再構築をトリガーするため、
 * 停車中は再構築コストゼロで動作する。
 */
@Mixin(targets = "com.gtnewhorizons.angelica.render.WeatherRenderer", remap = false)
public abstract class WeatherRendererRainOcclusionMixin {

    @Shadow
    private static int builtRevision;

    @Unique
    private static int crosstie$lastVehicleStateHash = 0;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void crosstie$onWeatherRenderHead(float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        VehicleWeatherManager.setPartialTicks(partialTicks);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            int stateHash = VehicleWeatherManager.getNearbyVehiclesStateHash(mc.theWorld);
            if (stateHash != crosstie$lastVehicleStateHash) {
                crosstie$lastVehicleStateHash = stateHash;
                // 車両の出現・移動・退出があったフレームのみ Instanced バッファを再構築
                builtRevision = Integer.MIN_VALUE;
            }
        }
    }

    @Inject(method = "clear", at = @At("TAIL"), require = 0)
    private static void crosstie$onWeatherClear(CallbackInfo ci) {
        crosstie$lastVehicleStateHash = 0;
        VehicleWeatherManager.clearRuntimeCache();
    }

    /**
     * Instanced Weather (writeColumns) およびシェーダー互換 Tessellator Weather (renderTessellator)
     * の両方で使用される WeatherColumns.heightAtSlot(slot) に車両遮蔽高さを反映する。
     */
    @Redirect(
            method = {"writeColumns", "renderTessellator"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/render/WeatherColumns;heightAtSlot(I)I"
            ),
            require = 0
    )
    private static int crosstie$redirectHeightAtSlot(WeatherColumns columns, int slot) {
        int defaultHeight = columns.heightAtSlot(slot);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return defaultHeight;
        }
        WeatherColumnsAccessor accessor = (WeatherColumnsAccessor) (Object) columns;
        int[] keyX = accessor.getKeyX();
        int[] keyZ = accessor.getKeyZ();
        if (keyX != null && keyZ != null && slot >= 0 && slot < keyX.length) {
            return VehicleWeatherManager.getOccludedPrecipitationHeight(mc.theWorld, keyX[slot], keyZ[slot], defaultHeight);
        }
        return defaultHeight;
    }

    /**
     * 雨パーティクル・雨音生成時 (spawnRainParticles) のキャッシュ済み高度参照に車両遮蔽を反映する。
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/render/WeatherColumns;heightAt(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectHeightAtParticles(WeatherColumns columns, int x, int z) {
        int defaultHeight = columns.heightAt(x, z);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            return VehicleWeatherManager.getOccludedPrecipitationHeight(mc.theWorld, x, z, defaultHeight);
        }
        return defaultHeight;
    }

    /**
     * spawnRainParticles のフォールバック参照 (WorldClient / MCP名)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectParticleHeightClientMcp(WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    /**
     * spawnRainParticles のフォールバック参照 (WorldClient / SRG名)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;func_72874_g(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectParticleHeightClientSrg(WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    /**
     * spawnRainParticles のフォールバック参照 (World / MCP名)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectParticleHeightWorldMcp(World world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    /**
     * spawnRainParticles のフォールバック参照 (World / SRG名)
     */
    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72874_g(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectParticleHeightWorldSrg(World world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
