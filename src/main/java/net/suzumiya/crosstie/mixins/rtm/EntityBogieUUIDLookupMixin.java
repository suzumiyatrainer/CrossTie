package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * EntityBogie.loadTrainFromUUID() の O(N) loadedEntityList 線形スキャンを最適化する。
 *
 * <h3>問題</h3>
 * <p>
 * オリジナル実装はチャンクロード時に全 Entity を線形スキャンして UUID 一致を探す。
 * ワールドに多数のエンティティが存在する場合、チャンクロードごとに MSPT スパイクを引き起こす。
 *
 * <h3>対策</h3>
 * <p>
 * Minecraft の {@code World.func_152378_a(UUID)} は内部で O(1) の UUID to Entity
 * マップを持つため、 それを使うことで線形スキャンを排除する。
 * 結果が見つかった場合はオリジナルの処理をスキップ、見つからない場合はオリジナルにフォールバック。
 */
@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogieUUIDLookupMixin {

    @Inject(method = "loadTrainFromUUID", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        EntityBogie self = (EntityBogie) (Object) this;
        if (self.worldObj == null) {
            return;
        }

        // World.func_152378_a(UUID) = getEntityByUniqueId --- O(1) ハッシュマップルックアップ
        Entity entity = self.worldObj.func_152378_a(uuid);
        if (entity instanceof EntityTrainBase) {
            self.setTrain((EntityTrainBase) entity);
            ((EntityTrainBase) entity).setBogie(self.getBogieId(), self);
            cir.setReturnValue(true);
        }
        // null の場合はオリジナルの線形スキャンにフォールバック（チャンクロード順序の問題に備える）
    }
}
