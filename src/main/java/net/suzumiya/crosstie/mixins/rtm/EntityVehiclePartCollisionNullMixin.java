package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CrossTie: EntityVehiclePart の getCollisionBox を全エンティティに対して null 返しにする。
 *
 * <h3>背景</h3>
 * <p>KaizPatchX の元実装は車両エンティティ同士では null を返すが、プレイヤー等には
 * {@code par1.boundingBox}（移動エンティティ自身のAABB）を返していた。
 * Minecraft の衝突システムでは自身のAABBを障害物として受け取っても無効（自己衝突不可）なため、
 * 元から全エンティティに対して実質 null と等価の動作をしていた。
 * 本Mixinはその挙動を明示的な null 返しで表現する。</p>
 *
 * <h3>影響範囲</h3>
 * <ul>
 *   <li>プレイヤー: 元から通過可能（par1.boundingBox 返し = 実質 null） → 変化なし</li>
 *   <li>モブ/NPC: 同上 → 変化なし</li>
 *   <li>レール追従: EntityBogie が主体であり EntityVehiclePart 経由の衝突判定に依存しない → 影響なし</li>
 *   <li>EntityFloor: EntityVehiclePart のサブクラスのため本Mixinで自動カバー</li>
 * </ul>
 */
@Mixin(value = EntityVehiclePart.class, remap = false)
public abstract class EntityVehiclePartCollisionNullMixin {

    /**
     * {@code getCollisionBox} の先頭で全エンティティに対して null を返してキャンセルする。
     *
     * <p>元の実装 {@code return par1.boundingBox;} は Minecraft の衝突システム上
     * null と等価だが、本Mixinで明示的に null を返すことで AppleExtended と設計的に対称にする。</p>
     */
    @Inject(
            method = "getCollisionBox",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void crosstie$nullifyCollisionBox(Entity par1, CallbackInfoReturnable<AxisAlignedBB> cir) {
        // 全エンティティに対して明示的に null を返す
        // 元の挙動（par1.boundingBox 返し）と実質等価だが、コードの意図を明確化する
        cir.setReturnValue(null);
    }
}
