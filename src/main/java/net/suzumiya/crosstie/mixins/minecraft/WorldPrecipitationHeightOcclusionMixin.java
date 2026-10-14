package net.suzumiya.crosstie.mixins.minecraft;

import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * クライアント側で World.getPrecipitationHeight が呼ばれた際、
 * 車両モデル面による屋根遮蔽高さを反映するMixin。
 * <p>
 * バニラ、Angelica、OptiFineなど全ての天候レンダラーが共通して
 * World.getPrecipitationHeight を参照するため、この単一地点への介入により
 * あらゆる描画パイプラインで確実に雨・雪の車内侵入が防止される。
 */
@Mixin(World.class)
public abstract class WorldPrecipitationHeightOcclusionMixin {

    @Inject(
            method = {"getPrecipitationHeight", "func_72874_g"},
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void crosstie$occludePrecipitationHeight(int x, int z, CallbackInfoReturnable<Integer> cir) {
        World world = (World) (Object) this;
        if (world.isRemote) {
            int original = cir.getReturnValue();
            int occluded = VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, original);
            if (occluded > original) {
                cir.setReturnValue(occluded);
            }
        }
    }
}
