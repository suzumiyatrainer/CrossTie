package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.state.DataEntry;
import jp.ngt.rtm.modelpack.state.DataMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Objects;

/**
 * DataMap の状態更新において、Suzu Throttle Lib と同様の
 * パケットキャッシュ（間引き）処理をコアレベルで適用する Mixin。
 * 値が変更されていない場合は処理をキャンセルし、無駄なパケット送信を防ぐ。
 */
@Mixin(value = DataMap.class, remap = false)
public abstract class DataMapMixin {

    @Shadow private HashMap<Object, DataEntry<?>> map;

    @Inject(method = "set(Ljp/ngt/rtm/modelpack/state/DataMap$DataKey;Ljp/ngt/rtm/modelpack/state/DataEntry;I)V", at = @At("HEAD"), cancellable = true)
    private void onSet(@Coerce Object key, DataEntry<?> value, int flag, CallbackInfo ci) {
        DataEntry<?> current = this.map.get(key);
        if (current != null && Objects.equals(current.get(), value.get())) {
            // 値が変更されていない場合は Map の更新およびパケット送信をキャンセルする
            ci.cancel();
        }
    }
}
