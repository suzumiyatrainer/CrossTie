package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.math.NGTMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Phase 4-4: {@link NGTMath#pow(double, int)} および {@link NGTMath#compare(double, double, int)} から
 * {@code IntStream} アロケーションを排除する。
 */
@Mixin(value = NGTMath.class, remap = false)
public abstract class NGTMathOptimizationMixin {

    /**
     * @author CrossTie
     * @reason IntStream による毎回のオブジェクト生成を排除し、ゼロアロケーションの繰り返し二乗法へ置換
     */
    @Overwrite(remap = false)
    public static double pow(double par1, int par2) {
        if (par2 <= 0) {
            return 1.0D;
        }
        double result = 1.0D;
        double base = par1;
        int exp = par2;
        while (exp > 0) {
            if ((exp & 1) != 0) {
                result *= base;
            }
            base *= base;
            exp >>>= 1;
        }
        return result;
    }

    /**
     * @author CrossTie
     * @reason pow 呼び出しを 1 回に集約して高速化
     */
    @Overwrite(remap = false)
    public static boolean compare(double p1, double p2, int p3) {
        double scale = NGTMath.pow(10.0D, p3);
        long i0 = Math.round(p1 * scale);
        long i1 = Math.round(p2 * scale);
        return i0 == i1;
    }
}
