package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * EntityVehiclePart.loadTrainFromUUID() の O(N) loadedEntityList 線形スキャンを最適化する。
 *
 * <h3>問題</h3>
 * <p>
 * オリジナル実装はチャンクロード時に全 Entity を線形スキャンして UUID 一致を探す。 EntityBogie
 * 版と同様の問題で、チャンクロードごとに MSPT スパイクを引き起こす。
 *
 * <h3>対策</h3>
 * <p>
 * Minecraft の {@code World.func_152378_a(UUID)} による O(1) ルックアップに置き換え。 見つかった場合は
 * {@code setVehicle / onLoadVehicle} を呼んでオリジナル処理をスキップ。 見つからない場合はオリジナルにフォールバック。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = EntityVehiclePart.class, remap = false)
public abstract class EntityVehiclePartUUIDLookupMixin {

    @Inject(method = "loadTrainFromUUID", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        EntityVehiclePart self = (EntityVehiclePart) (Object) this;
        if (self.worldObj == null) {
            return;
        }

        // World.func_152378_a(UUID) = getEntityByUniqueId --- O(1) ハッシュマップルックアップ
        Entity entity = self.worldObj.func_152378_a(uuid);
        if (entity instanceof EntityVehicleBase) {
            self.setVehicle((EntityVehicleBase) entity);
            self.onLoadVehicle();
            cir.setReturnValue(true);
        }
        // null の場合はオリジナルの線形スキャンにフォールバック（チャンクロード順序の問題に備える）
    }
}
