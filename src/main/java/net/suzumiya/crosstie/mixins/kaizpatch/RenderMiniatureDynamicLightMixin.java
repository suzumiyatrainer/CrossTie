package net.suzumiya.crosstie.mixins.kaizpatch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureDynamicLightMixin {

    @Unique
    private static final Map<TileEntity, Integer> crosstie$lastLightByTile =
            new WeakHashMap<TileEntity, Integer>();

    @Unique
    private static Field crosstie$tileGlListsField;

    @Unique
    private static Method crosstie$deleteGlListMethod;

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$invalidateDisplayListOnLightChange(
            TileEntity tile, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (tile == null || tile.getWorldObj() == null) {
            return;
        }

        int lightSignature = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        Integer previous = crosstie$lastLightByTile.put(tile, Integer.valueOf(lightSignature));
        if (previous != null && previous.intValue() != lightSignature) {
            Object[] glLists = crosstie$getDisplayLists(tile);
            crosstie$deleteDisplayLists(glLists);
            crosstie$setDisplayLists(tile, null);
        }
    }

    @Unique
    private static Object[] crosstie$getDisplayLists(TileEntity tile) {
        try {
            if (crosstie$tileGlListsField == null) {
                crosstie$tileGlListsField = tile.getClass().getDeclaredField("glLists");
                crosstie$tileGlListsField.setAccessible(true);
            }
            return (Object[]) crosstie$tileGlListsField.get(tile);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setDisplayLists(TileEntity tile, Object[] glLists) {
        try {
            if (crosstie$tileGlListsField == null) {
                crosstie$tileGlListsField = tile.getClass().getDeclaredField("glLists");
                crosstie$tileGlListsField.setAccessible(true);
            }
            crosstie$tileGlListsField.set(tile, glLists);
        } catch (ReflectiveOperationException ignored) {
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
        try {
            if (crosstie$deleteGlListMethod == null) {
                Class<?> glHelperClass = Class.forName("jp.ngt.ngtlib.renderer.GLHelper");
                Class<?> displayListClass = Class.forName("jp.ngt.ngtlib.renderer.DisplayList");
                crosstie$deleteGlListMethod = glHelperClass.getMethod("deleteGLList", displayListClass);
            }
            crosstie$deleteGlListMethod.invoke(null, glList);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
