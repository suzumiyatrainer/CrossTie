package net.suzumiya.crosstie.compat.journeymap;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NBTBase;

/**
 * GTNHLib の新 DFU (DynamicOps) で abstract になった {@code getBooleanValue} を
 * JourneyMap の {@code Jm12NbtOps} に補完するためのヘルパー。
 */
public final class Jm12NbtOpsHelper {

    private Jm12NbtOpsHelper() {}

    /**
     * NBTBase から Boolean 値を取得し、DataResult として返す。
     */
    public static DataResult<Boolean> getBooleanValue(Object ops, NBTBase input) {
        if (input instanceof NBTBase.NBTPrimitive) {
            byte b = ((NBTBase.NBTPrimitive) input).func_150290_f();
            return JourneyMapDfuBridge.success(b != 0);
        }
        return JourneyMapDfuBridge.error("Not a boolean: " + input);
    }
}
