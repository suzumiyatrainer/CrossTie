package net.suzumiya.crosstie.mixins.mcte.late;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureAngelicaLightMixin {

    @Unique
    private static Method crosstie$setBrightnessMethod;
    @Unique
    private static boolean crosstie$setBrightnessMethodInitialized = false;

    @Unique
    private static Method crosstie$deleteGlListMethod;
    @Unique
    private static boolean crosstie$deleteGlListMethodInitialized = false;

    @Unique
    private static final Map<Class<?>, Field> crosstie$renderCacheFields = new WeakHashMap<>();

    @Unique
    private static final Map<Class<?>, Method> crosstie$deleteMethods = new WeakHashMap<>();

    @Unique
    private static final Map<TileEntity, Integer> crosstie$blockLightCache = new WeakHashMap<>();

    // 単一staticフィールドではなくスタックで管理し、ネスト/複数TileEntityの描画順が
    // 交錯しても各インスタンスに正しいライト値が渡るようにする
    @Unique
    private static final ArrayDeque<Integer> crosstie$lightStack = new ArrayDeque<>();

    @Inject(method = "func_147500_a", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void crosstie$synchronizeShaderLighting(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        // 1. シャドウパス中の描画スキップ
        try {
            Class<?> shadowStateClass = Class.forName("net.coderbot.iris.shadows.ShadowRenderingState");
            if ((boolean) shadowStateClass.getMethod("areShadowsCurrentlyBeingRendered").invoke(null)) {
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {
        }

        if (tile == null || tile.getWorldObj() == null)
            return;
        World world = tile.getWorldObj();

        // 2. ブロックが設置されている「その場所」の正確なライト環境（昼夜・光源変化）をサンプリング
        int bx = tile.xCoord;
        int by = tile.yCoord + 1;
        int bz = tile.zCoord;
        int currentLight = world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);

        // キャッシュ（前回の明るさ）と現在の明るさを比較し、変化があれば再コンパイルさせる
        Integer lastLight = crosstie$blockLightCache.get(tile);
        if (lastLight == null) {
            crosstie$blockLightCache.put(tile, currentLight);
        } else if (lastLight != currentLight) {
            Object[] glLists = crosstie$getGlLists(tile);
            if (glLists != null) {
                crosstie$deleteDisplayLists(glLists);
                crosstie$setGlLists(tile, null);
            }
            crosstie$blockLightCache.put(tile, currentLight);
        }

        // 4. コンパイルが走る直前に、このブロック専用 of ライト値をスタックへpush
        // （単一staticフィールドの上書き競合を避けるため）
        crosstie$lightStack.push(currentLight);

        // 描画全体のライトマップ状態も同期
        crosstie$invokeSetBrightness(currentLight);
    }

    @Inject(method = "func_147500_a", at = @At("RETURN"), remap = false)
    private void crosstie$postRenderCleanup(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        // 描画終了後はこの呼び出しに対応する分だけスタックからpop
        if (!crosstie$lightStack.isEmpty()) {
            crosstie$lightStack.pop();
        }
    }

    /**
     * DirectTessellatorLightMixinから呼ばれる。 現在描画中（スタック最上位）のブロックライト値を返す。無ければ -1。
     */
    @Unique
    public static int crosstie$peekTargetBlockLight() {
        Integer top = crosstie$lightStack.peek();
        return top == null ? -1 : top;
    }

    // ---- ユーティリティ群（リフレクション） ----
    @Unique
    private static void crosstie$invokeSetBrightness(int brightness) {
        if (!crosstie$setBrightnessMethodInitialized) {
            crosstie$setBrightnessMethodInitialized = true;
            try {
                crosstie$setBrightnessMethod = Class.forName("jp.ngt.ngtlib.renderer.GLHelper")
                        .getMethod("setBrightness", int.class);
            } catch (ReflectiveOperationException ignored) {
                crosstie$setBrightnessMethod = null;
            }
        }

        if (crosstie$setBrightnessMethod != null) {
            try {
                crosstie$setBrightnessMethod.invoke(null, brightness);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Unique
    private static Field crosstie$findRenderCacheField(Class<?> clazz) {
        if (crosstie$renderCacheFields.containsKey(clazz)) {
            return crosstie$renderCacheFields.get(clazz);
        }

        Field foundField = null;
        try {
            foundField = clazz.getDeclaredField("glLists");
            foundField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                foundField = clazz.getDeclaredField("glVaos");
                foundField.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                for (Field f : clazz.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (type.isArray()) {
                        String compName = type.getComponentType().getName();
                        if (compName.equals("jp.ngt.ngtlib.renderer.DisplayList")
                                || compName.contains("IVertexArrayObject")
                                || compName.contains("VertexArray")) {
                            f.setAccessible(true);
                            foundField = f;
                            break;
                        }
                    }
                }
            }
        }
        
        crosstie$renderCacheFields.put(clazz, foundField);
        return foundField;
    }

    @Unique
    private static Object[] crosstie$getGlLists(TileEntity tile) {
        Field f = crosstie$findRenderCacheField(tile.getClass());
        if (f != null) {
            try {
                return (Object[]) f.get(tile);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    @Unique
    private static void crosstie$setGlLists(TileEntity tile, Object[] glLists) {
        Field f = crosstie$findRenderCacheField(tile.getClass());
        if (f != null) {
            try {
                f.set(tile, glLists);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Unique
    private static void crosstie$deleteDisplayLists(Object[] glLists) {
        if (glLists == null)
            return;
        for (Object glList : glLists) {
            if (glList != null)
                crosstie$deleteCacheElement(glList);
        }
    }

    @Unique
    private static void crosstie$deleteCacheElement(Object element) {
        if (element == null)
            return;

        Class<?> clazz = element.getClass();
        Method deleteMethod = null;

        if (crosstie$deleteMethods.containsKey(clazz)) {
            deleteMethod = crosstie$deleteMethods.get(clazz);
        } else {
            try {
                deleteMethod = clazz.getMethod("delete");
                deleteMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                deleteMethod = null;
            }
            crosstie$deleteMethods.put(clazz, deleteMethod);
        }

        if (deleteMethod != null) {
            try {
                deleteMethod.invoke(element);
                return;
            } catch (Throwable ignored) {
            }
        }

        if (!crosstie$deleteGlListMethodInitialized) {
            crosstie$deleteGlListMethodInitialized = true;
            try {
                Class<?> displayListClass = Class.forName("jp.ngt.ngtlib.renderer.DisplayList");
                crosstie$deleteGlListMethod = Class.forName("jp.ngt.ngtlib.renderer.GLHelper").getMethod("deleteGLList",
                        displayListClass);
            } catch (ReflectiveOperationException ignored) {
                crosstie$deleteGlListMethod = null;
            }
        }

        if (crosstie$deleteGlListMethod != null) {
            try {
                crosstie$deleteGlListMethod.invoke(null, element);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}