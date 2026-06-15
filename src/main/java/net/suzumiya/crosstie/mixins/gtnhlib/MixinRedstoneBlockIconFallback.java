// src/main/java/net/suzumiya/crosstie/mixins/gtnhlib/MixinRedstoneBlockIconFallback.java
package net.suzumiya.crosstie.mixins.gtnhlib;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RedstoneWire の missing texture を防ぐフォールバック。
 * <p>
 * GTNHLib が {@code nhlib$isModeled} を true に設定したブロックでは、
 * getIcon メソッドに ASM インジェクションが行われ、強制的に ModelISBRH の
 * アイコン（missingno）が返されます。この mixin は、そのような場合に
 * 本来のブロック固有アイコンを取得するため、一時的に {@code nhlib$isModeled}
 * をオフにして再呼び出しを行います。
 */
@Mixin(BlockRedstoneWire.class)
public abstract class MixinRedstoneBlockIconFallback {

    @Unique
    private static Field crosstie$modeledField;

    @Unique
    private static boolean crosstie$modeledFieldResolved;

    @Unique
    private final ThreadLocal<Boolean> crosstie$reentrantGuard = new ThreadLocal<Boolean>();

    // SRG: func_150160_a(IBlockAccess,IIII) -> IIcon
    @Inject(method = "getIcon(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$fixWorld(IBlockAccess world, int x, int y, int z, int side,
            CallbackInfoReturnable<IIcon> cir) {
        if (Boolean.TRUE.equals(crosstie$reentrantGuard.get())) {
            return;
        }
        IIcon icon = cir.getReturnValue();
        if (icon == null || isBad(icon)) {
            IIcon fallback = crosstie$getOriginalIcon(world, x, y, z, side);
            if (!isBad(fallback) && fallback != icon) {
                cir.setReturnValue(fallback);
            }
        }
    }

    // SRG: func_149720_a(II) -> IIcon (アイテムアイコン)
    @Inject(method = "getIcon(II)Lnet/minecraft/util/IIcon;", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$fixItem(int side, int meta,
            CallbackInfoReturnable<IIcon> cir) {
        if (Boolean.TRUE.equals(crosstie$reentrantGuard.get())) {
            return;
        }
        IIcon icon = cir.getReturnValue();
        if (icon == null || isBad(icon)) {
            IIcon fallback = crosstie$getOriginalIcon(null, 0, 0, 0, side);
            if (!isBad(fallback) && fallback != icon) {
                cir.setReturnValue(fallback);
            }
        }
    }

    /**
     * GTNHLib のモデリングを一時的に無効化し、本来のブロックアイコンを取得します。
     * これにより {@code nhlib$isModeled} チェックでの早期リターンをバイパスします。
     */
    @Unique
    private IIcon crosstie$getOriginalIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (!crosstie$modeledFieldResolved) {
            crosstie$resolveModeledField();
        }
        if (crosstie$modeledField == null) {
            return null;
        }
        try {
            crosstie$reentrantGuard.set(Boolean.TRUE);
            crosstie$modeledField.setBoolean(this, false);
            return ((BlockRedstoneWire) (Object) this).getIcon(world, x, y, z, side);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                crosstie$modeledField.setBoolean(this, true);
            } catch (Exception ignored) {
            }
            crosstie$reentrantGuard.remove();
        }
    }

    @Unique
    private static void crosstie$resolveModeledField() {
        crosstie$modeledFieldResolved = true;
        try {
            crosstie$modeledField = Block.class.getDeclaredField("nhlib$isModeled");
            crosstie$modeledField.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static boolean isBad(IIcon i) {
        if (i == null) {
            return true;
        }
        String n = i.getIconName();
        return n == null || n.endsWith("missingno");
    }
}