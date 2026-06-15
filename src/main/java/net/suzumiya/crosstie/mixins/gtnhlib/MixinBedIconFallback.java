package net.suzumiya.crosstie.mixins.gtnhlib;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockBed 用のアイコンフォールバック。
 * <p>
 * GTNHLib が {@code nhlib$isModeled} を true に設定したブロックでは、
 * getIcon メソッドに ASM インジェクションが行われ、強制的に ModelISBRH の
 * アイコン（missingno）が返されます。この mixin は、そのような場合に
 * 本来のブロック固有アイコンを取得するため、一時的に {@code nhlib$isModeled}
 * をオフにして再呼び出しを行います。
 */
@Mixin(BlockBed.class)
public abstract class MixinBedIconFallback {

    @Unique
    private static Field crosstie$modeledField;

    @Unique
    private static boolean crosstie$modeledFieldResolved;

    @Unique
    private final ThreadLocal<Boolean> crosstie$reentrantGuard = new ThreadLocal<Boolean>();

    // SRG: func_149691_a(IBlockAccess,IIII) -> IIcon (ワールドアイコン)
    @Inject(method = "getIcon(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$fixWorldIcon(IBlockAccess world, int x, int y, int z, int side,
            CallbackInfoReturnable<IIcon> cir) {
        if (Boolean.TRUE.equals(crosstie$reentrantGuard.get())) {
            return;
        }
        IIcon icon = cir.getReturnValue();
        if (icon == null || isBad(icon)) {
            IIcon fallback = crosstie$getOriginalWorldIcon(world, x, y, z, side);
            if (!isBad(fallback) && fallback != icon) {
                cir.setReturnValue(fallback);
            }
        }
    }

    // SRG: func_149689_b(II) -> IIcon (アイテム表示用)
    @Inject(method = "getBlockTexture(II)Lnet/minecraft/util/IIcon;", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$fixItemIcon(int side, int meta,
            CallbackInfoReturnable<IIcon> cir) {
        if (Boolean.TRUE.equals(crosstie$reentrantGuard.get())) {
            return;
        }
        IIcon icon = cir.getReturnValue();
        if (icon == null || isBad(icon)) {
            IIcon fallback = crosstie$getOriginalWorldIcon(null, 0, 0, 0, side);
            if (!isBad(fallback) && fallback != icon) {
                cir.setReturnValue(fallback);
            }
        }
    }

    /**
     * GTNHLib のモデリングを一時的に無効化し、本来のブロックアイコンを取得します。
     */
    @Unique
    private IIcon crosstie$getOriginalWorldIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (!crosstie$modeledFieldResolved) {
            crosstie$resolveModeledField();
        }
        if (crosstie$modeledField == null) {
            return null;
        }
        try {
            crosstie$reentrantGuard.set(Boolean.TRUE);
            crosstie$modeledField.setBoolean(this, false);
            return ((BlockBed) (Object) this).getIcon(world, x, y, z, side);
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