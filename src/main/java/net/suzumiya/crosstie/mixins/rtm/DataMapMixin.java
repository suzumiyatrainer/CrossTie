package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.state.DataEntry;
import jp.ngt.rtm.modelpack.state.DataFormatter;
import jp.ngt.rtm.modelpack.state.DataMap;
import jp.ngt.ngtlib.io.NGTLog;
import jp.ngt.ngtlib.util.NGTUtil;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@SuppressWarnings("rawtypes")
@Mixin(value = DataMap.class, remap = false)
public abstract class DataMapMixin {

    @Shadow
    private Map<String, DataEntry> map;

    @Shadow
    private DataFormatter dataFormatter;

    @Shadow
    private Object entity;

    @Shadow
    protected abstract void sendPacket(String key, DataEntry value, boolean toClient);

    /**
     * @author Antigravity
     * @reason パケット送信を初回および値が変更された時のみに制限する
     */
    @Inject(method = "set(Ljava/lang/String;Ljp/ngt/rtm/modelpack/state/DataEntry;I)V", at = @At("HEAD"), cancellable = true)
    private void onSet(String key, DataEntry value, int flag, CallbackInfo ci) {
        if (!this.dataFormatter.check(key, value)) {
            NGTLog.debug("Invalid data : %s=%s", key, value.toString());
            ci.cancel();
            return;
        }

        DataEntry oldValue = this.map.get(key);
        boolean isChanged = (oldValue == null || !oldValue.equals(value));

        boolean sync = ((flag & DataMap.SYNC_FLAG) != 0);
        boolean onServerSide = NGTUtil.isServer();
        if (onServerSide || !sync || (this.entity == null) || this.entity instanceof Item) {
            this.map.put(key, value);
        }

        if (sync && isChanged) {
            this.sendPacket(key, value, onServerSide);
        }
        ci.cancel();
    }
}
