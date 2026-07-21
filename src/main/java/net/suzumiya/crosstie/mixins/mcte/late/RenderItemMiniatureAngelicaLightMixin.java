package net.suzumiya.crosstie.mixins.mcte.late;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica環境向け 手持ちミニチュアアイテム光源修正 Mixin。
 *
 * <p>
 * OptiFine版（RenderItemMiniatureOptifineLightMixin）と同じ実装方針。 Angelica の
 * DynamicLights は getLightBrightnessForSkyBlocks を通して反映されるため、 光源シグネチャの計算に
 * packed brightness の下位nibbleを含めることで 手持ち光源の変化も検出できる。
 */
@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureAngelicaLightMixin {

    @Shadow
    @Final
    private Map<ItemStack, Object> propMap;

    @Unique
    private static final Map<Object, LightState> crosstie$lightStateByProp = new WeakHashMap<>();

    @Unique
    private static Constructor<?> crosstie$renderPropConstructor;

    @Unique
    private static Constructor<?> crosstie$mcteWorldConstructor;

    @Unique
    private static Method crosstie$getNgtObjectMethod;

    @Unique
    private static Method crosstie$deleteGlListMethod;

    @Unique
    private static Method crosstie$setBrightnessMethod;

    @Unique
    private static Field crosstie$renderPropWorldField;

    @Unique
    private static Field crosstie$renderPropGlListsField;

    @Unique
    private static Field crosstie$renderPropNgtoField;

    @Inject(method = "renderItem", at = @At("HEAD"), require = 1, remap = false)
    private void crosstie$refreshHeldMiniatureLighting(ItemRenderType type, ItemStack item, Object[] data,
            CallbackInfo ci) {
        if (item == null || !item.hasTagCompound() || type == ItemRenderType.INVENTORY) {
            return;
        }

        Object renderProp = this.propMap.get(item);
        if (renderProp == null) {
            renderProp = crosstie$createRenderProp(item);
            if (renderProp == null) {
                return;
            }
            this.propMap.put(item, renderProp);
        }

        Entity anchor = crosstie$resolveAnchorEntity(data);
        if (anchor == null || anchor.worldObj == null) {
            return;
        }

        int x = MathHelper.floor_double(anchor.posX);
        int y = MathHelper.floor_double(anchor.posY + anchor.getEyeHeight());
        int z = MathHelper.floor_double(anchor.posZ);

        // Angelica では DisplayList コンパイル時に頂点へ明るさが焼き込まれるため、
        // 昼夜変化を検出して再コンパイルする必要がある。
        // skylightSubtracted はバニラが時刻に応じて直接更新するフィールドなので
        // これをシグネチャに含めることで昼夜変化を確実に検出できる。
        int rawBlock = anchor.worldObj.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
        int rawSky = anchor.worldObj.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
        // skylightSubtracted を上位ビットに含める（0〜11、時刻依存）
        int lightSignature = (anchor.worldObj.skylightSubtracted << 8) | (rawBlock << 4) | rawSky;

        LightState state = crosstie$getOrCreateState(renderProp);
        boolean moved = state.world != anchor.worldObj || state.x != x || state.y != y || state.z != z;
        boolean relight = moved || state.lightSignature != lightSignature;

        if (moved) {
            Object ngto = crosstie$getRenderPropNgto(renderProp);
            Object ngtWorld = crosstie$createNgtWorld(anchor.worldObj, ngto, x, y, z);
            if (ngtWorld != null) {
                crosstie$setRenderPropWorld(renderProp, ngtWorld);
            }
        }

        // 光源環境の変化、あるいは別次元等への移動を検知した場合にキャッシュをクリア
        if (relight) {
            crosstie$deleteDisplayLists(crosstie$getRenderPropGlLists(renderProp));
            crosstie$setRenderPropGlLists(renderProp, null);
        }

        // 【修正の核心】
        // 手持ちアイテムが初めて描画される際、または上記でキャッシュが破棄されたタイミングにおいて、
        // ディスプレイリストのコンパイル直前に最新のプレイヤー周囲の光量をOpenGLコンテキストへ同期する。
        if (crosstie$getRenderPropGlLists(renderProp) == null) {
            int brightness = anchor.worldObj.getLightBrightnessForSkyBlocks(x, y, z, 0);
            crosstie$invokeSetBrightness(brightness);
        }

        if (relight) {
            state.world = anchor.worldObj;
            state.x = x;
            state.y = y;
            state.z = z;
            state.lightSignature = lightSignature;
        }
    }

    // ---- ユーティリティ ----

    @Unique
    private static void crosstie$invokeSetBrightness(int brightness) {
        try {
            if (crosstie$setBrightnessMethod == null) {
                crosstie$setBrightnessMethod = Class.forName("jp.ngt.ngtlib.renderer.GLHelper")
                        .getMethod("setBrightness", int.class);
            }
            crosstie$setBrightnessMethod.invoke(null, brightness);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static Object crosstie$createRenderProp(ItemStack item) {
        Object ngto = crosstie$getNgtObject(item.getTagCompound());
        if (ngto == null)
            return null;
        try {
            if (crosstie$renderPropConstructor == null) {
                Class<?> clazz = Class.forName("jp.ngt.mcte.item.RenderItemMiniature$RenderProp");
                crosstie$renderPropConstructor = clazz
                        .getDeclaredConstructor(Class.forName("jp.ngt.ngtlib.block.NGTObject"), ItemStack.class);
                crosstie$renderPropConstructor.setAccessible(true);
            }
            return crosstie$renderPropConstructor.newInstance(ngto, item);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object crosstie$getNgtObject(NBTTagCompound tag) {
        try {
            if (crosstie$getNgtObjectMethod == null) {
                crosstie$getNgtObjectMethod = Class.forName("jp.ngt.mcte.item.ItemMiniature").getMethod("getNGTObject",
                        NBTTagCompound.class);
            }
            return crosstie$getNgtObjectMethod.invoke(null, tag);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object crosstie$createNgtWorld(net.minecraft.world.World world, Object ngto, int x, int y, int z) {
        if (world == null || ngto == null)
            return null;
        try {
            if (crosstie$mcteWorldConstructor == null) {
                crosstie$mcteWorldConstructor = Class.forName("jp.ngt.mcte.world.MCTEWorld").getConstructor(
                        net.minecraft.world.World.class, Class.forName("jp.ngt.ngtlib.block.NGTObject"), Integer.TYPE,
                        Integer.TYPE, Integer.TYPE);
            }
            return crosstie$mcteWorldConstructor.newInstance(world, ngto, x, y, z);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Entity crosstie$resolveAnchorEntity(Object[] data) {
        if (data != null) {
            for (Object datum : data) {
                if (datum instanceof Entity)
                    return (Entity) datum;
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.renderViewEntity;
    }

    @Unique
    private static LightState crosstie$getOrCreateState(Object renderProp) {
        LightState state = crosstie$lightStateByProp.get(renderProp);
        if (state == null) {
            state = new LightState();
            crosstie$lightStateByProp.put(renderProp, state);
        }
        return state;
    }

    @Unique
    private static Object crosstie$getRenderPropNgto(Object renderProp) {
        try {
            if (crosstie$renderPropNgtoField == null) {
                crosstie$renderPropNgtoField = renderProp.getClass().getDeclaredField("ngto");
                crosstie$renderPropNgtoField.setAccessible(true);
            }
            return (Object[]) crosstie$renderPropNgtoField.get(renderProp);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setRenderPropWorld(Object renderProp, Object world) {
        try {
            if (crosstie$renderPropWorldField == null) {
                crosstie$renderPropWorldField = renderProp.getClass().getDeclaredField("world");
                crosstie$renderPropWorldField.setAccessible(true);
            }
            crosstie$renderPropWorldField.set(renderProp, world);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static Field crosstie$findRenderPropCacheField(Class<?> clazz) {
        if (crosstie$renderPropGlListsField != null) {
            return crosstie$renderPropGlListsField;
        }
        try {
            Field f = clazz.getDeclaredField("glLists");
            f.setAccessible(true);
            crosstie$renderPropGlListsField = f;
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getDeclaredField("glVaos");
                f.setAccessible(true);
                crosstie$renderPropGlListsField = f;
                return f;
            } catch (NoSuchFieldException ex) {
                for (Field f : clazz.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (type.isArray()) {
                        String compName = type.getComponentType().getName();
                        if (compName.equals("jp.ngt.ngtlib.renderer.DisplayList")
                                || compName.contains("IVertexArrayObject")
                                || compName.contains("VertexArray")) {
                            f.setAccessible(true);
                            crosstie$renderPropGlListsField = f;
                            return f;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Unique
    private static Object[] crosstie$getRenderPropGlLists(Object renderProp) {
        try {
            Field f = crosstie$findRenderPropCacheField(renderProp.getClass());
            if (f != null) {
                return (Object[]) f.get(renderProp);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    @Unique
    private static void crosstie$setRenderPropGlLists(Object renderProp, Object[] glLists) {
        try {
            Field f = crosstie$findRenderPropCacheField(renderProp.getClass());
            if (f != null) {
                f.set(renderProp, glLists);
            }
        } catch (ReflectiveOperationException ignored) {
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
        try {
            if (element.getClass().getName().contains("IVertexArrayObject") || element instanceof AutoCloseable) {
                try {
                    Method deleteMethod = element.getClass().getMethod("delete");
                    deleteMethod.invoke(element);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }

            if (crosstie$deleteGlListMethod == null) {
                Class<?> displayListClass = Class.forName("jp.ngt.ngtlib.renderer.DisplayList");
                crosstie$deleteGlListMethod = Class.forName("jp.ngt.ngtlib.renderer.GLHelper").getMethod("deleteGLList",
                        displayListClass);
            }
            crosstie$deleteGlListMethod.invoke(null, element);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method deleteMethod = element.getClass().getMethod("delete");
                deleteMethod.invoke(element);
            } catch (Throwable ignored2) {
            }
        }
    }

    @Unique
    private static final class LightState {
        private net.minecraft.world.World world;
        private int x = Integer.MIN_VALUE;
        private int y = Integer.MIN_VALUE;
        private int z = Integer.MIN_VALUE;
        private int lightSignature = Integer.MIN_VALUE;
    }
}