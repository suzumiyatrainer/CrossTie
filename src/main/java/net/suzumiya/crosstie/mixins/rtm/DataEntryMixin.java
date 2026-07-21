package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.state.DataEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = DataEntry.class, remap = false)
public abstract class DataEntryMixin {

    @Shadow
    public abstract Object get();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        DataEntry<?> other = (DataEntry<?>) obj;
        Object thisData = this.get();
        Object otherData = other.get();
        if (thisData == null) {
            return otherData == null;
        }
        return thisData.equals(otherData);
    }

    @Override
    public int hashCode() {
        Object thisData = this.get();
        return thisData != null ? thisData.hashCode() : 0;
    }
}
