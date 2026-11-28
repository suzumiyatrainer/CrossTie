package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.state.DataEntry;
import jp.ngt.rtm.modelpack.state.DataMap;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.suzumiya.crosstie.utils.DataMapSyncContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Objects;

/**
 * DataMap の状態更新において、同一値の冗長な同期パケット送信を安全に間引く Mixin。
 *
 * <p>{@code this.map} ではなく「最後にネットワーク送受信された値」({@code crosstie$lastSyncedValues}) を
 * 別管理することで、{@code flag=0}（ローカル書き込み）直後の {@code flag=1}（同期要求）で
 * 同期パケットが誤キャンセルされる同期ズレを完全に防止する。</p>
 */
@Mixin(value = DataMap.class, remap = false)
public abstract class DataMapMixin {

    @Shadow
    private HashMap<Object, DataEntry<?>> map;

    @Shadow
    private Object entity;

    @Unique
    private final HashMap<Object, Object> crosstie$lastSyncedValues = new HashMap<Object, Object>();

    @Inject(method = "set(Ljp/ngt/rtm/modelpack/state/DataMap$DataKey;Ljp/ngt/rtm/modelpack/state/DataEntry;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$onSet(@Coerce Object key, DataEntry<?> value, int flag, CallbackInfo ci) {
        if (key == null || value == null) {
            return;
        }
        Object newVal = value.get();

        // flag=0（ローカル書き込み / パケット受信反映）は絶対にキャンセルしない
        if ((flag & 1) == 0) {
            if (DataMapSyncContext.isApplyingPacket()) {
                this.crosstie$lastSyncedValues.put(key, newVal);
            }
            return;
        }

        // entity が null または Item の場合はパケット送信されないため通常処理を通す
        if (this.entity == null || this.entity instanceof Item) {
            return;
        }

        // 直近のネットワーク同期値 AND 現在のローカル値の両方が一致する場合のみキャンセル
        if (this.crosstie$lastSyncedValues.containsKey(key)
                && Objects.equals(this.crosstie$lastSyncedValues.get(key), newVal)) {
            DataEntry<?> current = this.map.get(key);
            if (current != null && Objects.equals(current.get(), newVal)) {
                ci.cancel();
                return;
            }
        }

        this.crosstie$lastSyncedValues.put(key, newVal);
    }

    @Inject(method = "remove(Ljp/ngt/rtm/modelpack/state/DataMap$DataKey;I)Z", at = @At("TAIL"), require = 0)
    private void crosstie$onRemove(@Coerce Object key, int flag, CallbackInfoReturnable<Boolean> cir) {
        if (key != null) {
            this.crosstie$lastSyncedValues.remove(key);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), require = 0)
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$lastSyncedValues.clear();
    }
}
