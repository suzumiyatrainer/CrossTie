package net.suzumiya.crosstie.mixins.mcte;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureOptifineLightMixin {

    @Unique
    private static final Map<TileEntity, Integer> crosstie$lastLightByTile = new WeakHashMap<TileEntity, Integer>();

    @Unique
    private static Field crosstie$tileGlListsField;
    @Unique
    private static boolean crosstie$tileGlListsFieldInitialized = false;

    @Unique
    private static Method crosstie$deleteGlListMethod;
    @Unique
    private static boolean crosstie$deleteGlListMethodInitialized = false;

    @Unique
    private static Method crosstie$setBrightnessMethod;
    @Unique
    private static boolean crosstie$setBrightnessMethodInitialized = false;

    @Inject(method = "func_147500_a", at = @At("HEAD"), require = 1, remap = false)
    private void crosstie$invalidateDisplayListOnLightChange(TileEntity tile, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        if (tile == null || tile.getWorldObj() == null) {
            return;
        }

        World world = tile.getWorldObj();
        int tx = tile.xCoord;
        int ty = tile.yCoord;
        int tz = tile.zCoord;

        // 周囲6マスの最大ブロックライト・スカイライトをサンプリング
        int maxBlockLight = world.getSavedLightValue(EnumSkyBlock.Block, tx, ty, tz);
        int maxSkyLight = world.getSavedLightValue(EnumSkyBlock.Sky, tx, ty, tz);

        final int[] dx = { 1, -1, 0, 0, 0, 0 };
        final int[] dy = { 0, 0, 1, -1, 0, 0 };
        final int[] dz = { 0, 0, 0, 0, 1, -1 };
        for (int i = 0; i < 6; i++) {
            int bl = world.getSavedLightValue(EnumSkyBlock.Block, tx + dx[i], ty + dy[i], tz + dz[i]);
            int sl = world.getSavedLightValue(EnumSkyBlock.Sky, tx + dx[i], ty + dy[i], tz + dz[i]);
            if (bl > maxBlockLight)
                maxBlockLight = bl;
            if (sl > maxSkyLight)
                maxSkyLight = sl;
        }

        int lightSignature = (maxBlockLight << 4) | maxSkyLight;

        Integer previous = crosstie$lastLightByTile.put(tile, Integer.valueOf(lightSignature));
        if (previous != null && previous.intValue() != lightSignature) {
            Object[] glLists = crosstie$getDisplayLists(tile);
            if (glLists != null) {
                // リストを破棄して null にすることで、次フレームで再コンパイルを強制
                crosstie$deleteDisplayLists(glLists);
                crosstie$setDisplayLists(tile, null);

                // 【超重要】
                // 再コンパイルが走る直前のコンテキストに、最新の正確な明るさをOpenGL/OptiFineに流し込む
                int currentLight = world.getLightBrightnessForSkyBlocks(tx, ty, tz, 0);
                crosstie$invokeSetBrightness(currentLight);
            }
        }
    }

    @Unique
    private static void crosstie$invokeSetBrightness(int brightness) {
        if (!crosstie$setBrightnessMethodInitialized) {
            crosstie$setBrightnessMethodInitialized = true;
            try {
                Class<?> glHelperClass = Class.forName("jp.ngt.ngtlib.renderer.GLHelper");
                crosstie$setBrightnessMethod = glHelperClass.getMethod("setBrightness", int.class);
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
    private static Object[] crosstie$getDisplayLists(TileEntity tile) {
        if (!crosstie$tileGlListsFieldInitialized) {
            crosstie$tileGlListsFieldInitialized = true;
            try {
                crosstie$tileGlListsField = tile.getClass().getDeclaredField("glLists");
                crosstie$tileGlListsField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$tileGlListsField = null;
            }
        }

        if (crosstie$tileGlListsField == null) {
            return null;
        }

        try {
            return (Object[]) crosstie$tileGlListsField.get(tile);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setDisplayLists(TileEntity tile, Object[] glLists) {
        if (!crosstie$tileGlListsFieldInitialized) {
            crosstie$tileGlListsFieldInitialized = true;
            try {
                crosstie$tileGlListsField = tile.getClass().getDeclaredField("glLists");
                crosstie$tileGlListsField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$tileGlListsField = null;
            }
        }

        if (crosstie$tileGlListsField != null) {
            try {
                crosstie$tileGlListsField.set(tile, glLists);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Unique
    private static void crosstie$deleteDisplayLists(Object[] glLists) {
        if (glLists == null) {
            return;
        }
        for (Object glList : glLists) {
            if (glList != null) {
                crosstie$deleteDisplayList(glList);
            }
        }
    }

    @Unique
    private static void crosstie$deleteDisplayList(Object glList) {
        if (!crosstie$deleteGlListMethodInitialized) {
            crosstie$deleteGlListMethodInitialized = true;
            try {
                Class<?> glHelperClass = Class.forName("jp.ngt.ngtlib.renderer.GLHelper");
                Class<?> displayListClass = Class.forName("jp.ngt.ngtlib.renderer.DisplayList");
                crosstie$deleteGlListMethod = glHelperClass.getMethod("deleteGLList", displayListClass);
            } catch (ReflectiveOperationException ignored) {
                crosstie$deleteGlListMethod = null;
            }
        }

        if (crosstie$deleteGlListMethod != null) {
            try {
                crosstie$deleteGlListMethod.invoke(null, glList);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}