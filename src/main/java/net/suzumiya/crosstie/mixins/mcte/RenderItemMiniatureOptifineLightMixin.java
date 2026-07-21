package net.suzumiya.crosstie.mixins.mcte;

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
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureOptifineLightMixin {

    @Shadow
    @Final
    private Map<ItemStack, Object> propMap;

    @Unique
    private static final Map<Object, LightState> crosstie$lightStateByProp = new WeakHashMap<Object, LightState>();

    @Unique
    private static Constructor<?> crosstie$renderPropConstructor;
    @Unique
    private static boolean crosstie$renderPropConstructorInitialized = false;

    @Unique
    private static Constructor<?> crosstie$mcteWorldConstructor;
    @Unique
    private static boolean crosstie$mcteWorldConstructorInitialized = false;

    @Unique
    private static Method crosstie$getNgtObjectMethod;
    @Unique
    private static boolean crosstie$getNgtObjectMethodInitialized = false;

    @Unique
    private static Method crosstie$deleteGlListMethod;
    @Unique
    private static boolean crosstie$deleteGlListMethodInitialized = false;

    @Unique
    private static Method crosstie$setBrightnessMethod;
    @Unique
    private static boolean crosstie$setBrightnessMethodInitialized = false;

    @Unique
    private static Field crosstie$renderPropWorldField;
    @Unique
    private static boolean crosstie$renderPropWorldFieldInitialized = false;

    @Unique
    private static Field crosstie$renderPropGlListsField;
    @Unique
    private static boolean crosstie$renderPropGlListsFieldInitialized = false;

    @Unique
    private static Field crosstie$renderPropNgtoField;
    @Unique
    private static boolean crosstie$renderPropNgtoFieldInitialized = false;

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

        // 手持ちアイテム用も生データ(Block/Sky)を取得してパック
        int blockLight = anchor.worldObj.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Block, x, y, z);
        int skyLight = anchor.worldObj.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Sky, x, y, z);
        int lightSignature = (blockLight << 4) | skyLight;

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
        if (relight) {
            crosstie$deleteDisplayLists(crosstie$getRenderPropGlLists(renderProp));
            crosstie$setRenderPropGlLists(renderProp, null);

            // 【修正ポイント】再コンパイルが走る直前に、最新の明るさをOpenGL/OptiFineにバインドさせます
            int brightness = anchor.worldObj.getLightBrightnessForSkyBlocks(x, y, z, 0);
            crosstie$invokeSetBrightness(brightness);

            state.world = anchor.worldObj;
            state.x = x;
            state.y = y;
            state.z = z;
            state.lightSignature = lightSignature;
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
    private static Object crosstie$createRenderProp(ItemStack item) {
        Object ngto = crosstie$getNgtObject(item.getTagCompound());
        if (ngto == null) {
            return null;
        }

        if (!crosstie$renderPropConstructorInitialized) {
            crosstie$renderPropConstructorInitialized = true;
            try {
                Class<?> clazz = Class.forName("jp.ngt.mcte.item.RenderItemMiniature$RenderProp");
                crosstie$renderPropConstructor = clazz
                        .getDeclaredConstructor(Class.forName("jp.ngt.ngtlib.block.NGTObject"), ItemStack.class);
                crosstie$renderPropConstructor.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$renderPropConstructor = null;
            }
        }

        if (crosstie$renderPropConstructor == null) {
            return null;
        }

        try {
            return crosstie$renderPropConstructor.newInstance(ngto, item);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object crosstie$getNgtObject(NBTTagCompound tag) {
        if (!crosstie$getNgtObjectMethodInitialized) {
            crosstie$getNgtObjectMethodInitialized = true;
            try {
                Class<?> itemMiniatureClass = Class.forName("jp.ngt.mcte.item.ItemMiniature");
                crosstie$getNgtObjectMethod = itemMiniatureClass.getMethod("getNGTObject", NBTTagCompound.class);
            } catch (ReflectiveOperationException ignored) {
                crosstie$getNgtObjectMethod = null;
            }
        }

        if (crosstie$getNgtObjectMethod == null) {
            return null;
        }

        try {
            return crosstie$getNgtObjectMethod.invoke(null, tag);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object crosstie$createNgtWorld(net.minecraft.world.World world, Object ngto, int x, int y, int z) {
        if (world == null || ngto == null) {
            return null;
        }

        if (!crosstie$mcteWorldConstructorInitialized) {
            crosstie$mcteWorldConstructorInitialized = true;
            try {
                crosstie$mcteWorldConstructor = Class.forName("jp.ngt.mcte.world.MCTEWorld").getConstructor(
                        net.minecraft.world.World.class, Class.forName("jp.ngt.ngtlib.block.NGTObject"), Integer.TYPE,
                        Integer.TYPE, Integer.TYPE);
            } catch (ReflectiveOperationException ignored) {
                crosstie$mcteWorldConstructor = null;
            }
        }

        if (crosstie$mcteWorldConstructor == null) {
            return null;
        }

        try {
            return crosstie$mcteWorldConstructor.newInstance(world, ngto, x, y, z);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Entity crosstie$resolveAnchorEntity(Object[] data) {
        if (data != null) {
            for (Object datum : data) {
                if (datum instanceof Entity) {
                    return (Entity) datum;
                }
            }
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.renderViewEntity;
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
        if (!crosstie$renderPropNgtoFieldInitialized) {
            crosstie$renderPropNgtoFieldInitialized = true;
            try {
                crosstie$renderPropNgtoField = renderProp.getClass().getDeclaredField("ngto");
                crosstie$renderPropNgtoField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$renderPropNgtoField = null;
            }
        }

        if (crosstie$renderPropNgtoField == null) {
            return null;
        }

        try {
            return crosstie$renderPropNgtoField.get(renderProp);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setRenderPropWorld(Object renderProp, Object world) {
        if (!crosstie$renderPropWorldFieldInitialized) {
            crosstie$renderPropWorldFieldInitialized = true;
            try {
                crosstie$renderPropWorldField = renderProp.getClass().getDeclaredField("world");
                crosstie$renderPropWorldField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$renderPropWorldField = null;
            }
        }

        if (crosstie$renderPropWorldField != null) {
            try {
                crosstie$renderPropWorldField.set(renderProp, world);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Unique
    private static Object[] crosstie$getRenderPropGlLists(Object renderProp) {
        if (!crosstie$renderPropGlListsFieldInitialized) {
            crosstie$renderPropGlListsFieldInitialized = true;
            try {
                crosstie$renderPropGlListsField = renderProp.getClass().getDeclaredField("glLists");
                crosstie$renderPropGlListsField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$renderPropGlListsField = null;
            }
        }

        if (crosstie$renderPropGlListsField == null) {
            return null;
        }

        try {
            return (Object[]) crosstie$renderPropGlListsField.get(renderProp);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setRenderPropGlLists(Object renderProp, Object[] glLists) {
        if (!crosstie$renderPropGlListsFieldInitialized) {
            crosstie$renderPropGlListsFieldInitialized = true;
            try {
                crosstie$renderPropGlListsField = renderProp.getClass().getDeclaredField("glLists");
                crosstie$renderPropGlListsField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                crosstie$renderPropGlListsField = null;
            }
        }

        if (crosstie$renderPropGlListsField != null) {
            try {
                crosstie$renderPropGlListsField.set(renderProp, glLists);
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

    @Unique
    private static final class LightState {
        private net.minecraft.world.World world;
        private int x = Integer.MIN_VALUE;
        private int y = Integer.MIN_VALUE;
        private int z = Integer.MIN_VALUE;
        private int lightSignature = Integer.MIN_VALUE;
    }
}