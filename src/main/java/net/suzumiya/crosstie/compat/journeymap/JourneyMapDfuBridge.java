package net.suzumiya.crosstie.compat.journeymap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

import com.mojang.serialization.DataResult;

/**
 * GTNHLib (FalsePatternLib) が導入する最新 DataFixerUpper (DataResult が interface) と、
 * 旧 DFU を前提にコンパイルされた JourneyMap 1.7.10 との互換性ブリッジ。
 *
 * <p>JourneyMap のコードは DataResult.success / error を Methodref (クラスメソッド) として
 * 呼び出すようにコンパイルされており、実行時に DataResult が interface になっていると
 * {@link IncompatibleClassChangeError} が発生してウェイポイントの保存・読み込みがクラッシュする。
 * このブリッジクラスはクラスとして存在するため、JourneyMap から Methodref のまま安全に呼び出せる。
 */
public final class JourneyMapDfuBridge {

    private static final MethodHandle MH_SUCCESS;
    private static final MethodHandle MH_ERROR_SUP;
    private static final MethodHandle MH_ERROR_SUP_PART;
    private static final MethodHandle MH_ERROR_STR;
    private static final MethodHandle MH_ERROR_STR_PART;

    static {
        MethodHandle mhSuccess = null;
        MethodHandle mhErrSup = null;
        MethodHandle mhErrSupPart = null;
        MethodHandle mhErrStr = null;
        MethodHandle mhErrStrPart = null;

        try {
            Class<?> drCls = Class.forName("com.mojang.serialization.DataResult");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            try {
                mhSuccess = lookup.findStatic(drCls, "success", MethodType.methodType(drCls, Object.class));
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // 新 DFU (GTNHLib): error(Supplier)
            try {
                mhErrSup = lookup.findStatic(drCls, "error", MethodType.methodType(drCls, Supplier.class));
            } catch (Throwable ignored) {}

            // 新 DFU (GTNHLib): error(Supplier, Object)
            try {
                mhErrSupPart = lookup.findStatic(drCls, "error", MethodType.methodType(drCls, Supplier.class, Object.class));
            } catch (Throwable ignored) {}

            // 旧 DFU (JourneyMap 同梱): error(String)
            try {
                mhErrStr = lookup.findStatic(drCls, "error", MethodType.methodType(drCls, String.class));
            } catch (Throwable ignored) {}

            // 旧 DFU (JourneyMap 同梱): error(String, Object)
            try {
                mhErrStrPart = lookup.findStatic(drCls, "error", MethodType.methodType(drCls, String.class, Object.class));
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            t.printStackTrace();
        }

        MH_SUCCESS = mhSuccess;
        MH_ERROR_SUP = mhErrSup;
        MH_ERROR_SUP_PART = mhErrSupPart;
        MH_ERROR_STR = mhErrStr;
        MH_ERROR_STR_PART = mhErrStrPart;
    }

    private JourneyMapDfuBridge() {}

    /**
     * {@code DataResult.success(Object)} の互換ブリッジ。
     */
    @SuppressWarnings("unchecked")
    public static <R> DataResult<R> success(R result) {
        if (MH_SUCCESS != null) {
            try {
                return (DataResult<R>) MH_SUCCESS.invoke(result);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke DataResult.success", t);
            }
        }
        return null;
    }

    /**
     * {@code DataResult.error(String)} の互換ブリッジ。
     * 旧 DFU の {@code String} 引数を新 DFU の {@code Supplier<String>} に動的に適合させる。
     */
    @SuppressWarnings("unchecked")
    public static <R> DataResult<R> error(String message) {
        if (MH_ERROR_SUP != null) {
            try {
                Supplier<String> sup = () -> message;
                return (DataResult<R>) MH_ERROR_SUP.invoke(sup);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke DataResult.error", t);
            }
        }
        if (MH_ERROR_STR != null) {
            try {
                return (DataResult<R>) MH_ERROR_STR.invoke(message);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke DataResult.error", t);
            }
        }
        return null;
    }

    /**
     * {@code DataResult.error(String, Object)} の互換ブリッジ。
     * 旧 DFU の {@code String} 引数を新 DFU の {@code Supplier<String>} に動的に適合させる。
     */
    @SuppressWarnings("unchecked")
    public static <R> DataResult<R> error(String message, R partialResult) {
        if (MH_ERROR_SUP_PART != null) {
            try {
                Supplier<String> sup = () -> message;
                return (DataResult<R>) MH_ERROR_SUP_PART.invoke(sup, (Object) partialResult);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke DataResult.error", t);
            }
        }
        if (MH_ERROR_STR_PART != null) {
            try {
                return (DataResult<R>) MH_ERROR_STR_PART.invoke(message, (Object) partialResult);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke DataResult.error", t);
            }
        }
        return null;
    }
}
