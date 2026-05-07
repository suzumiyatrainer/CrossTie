package net.suzumiya.crosstie.mixins.kaizpatch;

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
public abstract class RenderItemMiniatureDynamicLightMixin {

    @Shadow
    @Final
    private Map<ItemStack, Object> propMap;

    @Unique
    private static final Map<Object, LightState> crosstie$lightStateByProp = new WeakHashMap<Object, LightState>();

    @Unique
    private static Constructor<?> crosstie$renderPropConstructor;

    @Unique
    private static Constructor<?> crosstie$ngtWorldConstructor;

    @Unique
    private static Method crosstie$getNgtObjectMethod;

    @Unique
    private static Method crosstie$deleteGlListMethod;

    @Unique
    private static Field crosstie$renderPropWorldField;

    @Unique
    private static Field crosstie$renderPropGlListsField;

    @Unique
    private static Field crosstie$renderPropNgtoField;

    @Inject(method = "renderItem", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$refreshHeldMiniatureLighting(
            ItemRenderType type, ItemStack item, Object[] data, CallbackInfo ci) {
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
        int lightSignature = anchor.worldObj.getLightBrightnessForSkyBlocks(x, y, z, 0);

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
            state.world = anchor.worldObj;
            state.x = x;
            state.y = y;
            state.z = z;
            state.lightSignature = lightSignature;
        }
    }

    @Unique
    private static Object crosstie$createRenderProp(ItemStack item) {
        Object ngto = crosstie$getNgtObject(item.getTagCompound());
        if (ngto == null) {
            return null;
        }

        try {
            if (crosstie$renderPropConstructor == null) {
                Class<?> clazz = Class.forName("jp.ngt.mcte.item.RenderItemMiniature$RenderProp");
                crosstie$renderPropConstructor =
                        clazz.getDeclaredConstructor(Class.forName("jp.ngt.ngtlib.block.NGTObject"), ItemStack.class);
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
                Class<?> itemMiniatureClass = Class.forName("jp.ngt.mcte.item.ItemMiniature");
                crosstie$getNgtObjectMethod = itemMiniatureClass.getMethod("getNGTObject", NBTTagCompound.class);
            }
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
        try {
            if (crosstie$ngtWorldConstructor == null) {
                crosstie$ngtWorldConstructor = Class.forName("jp.ngt.ngtlib.world.NGTWorld")
                        .getConstructor(net.minecraft.world.World.class,
                                Class.forName("jp.ngt.ngtlib.block.NGTObject"),
                                Integer.TYPE, Integer.TYPE, Integer.TYPE);
            }
            return crosstie$ngtWorldConstructor.newInstance(world, ngto, x, y, z);
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
        try {
            if (crosstie$renderPropNgtoField == null) {
                crosstie$renderPropNgtoField = renderProp.getClass().getDeclaredField("ngto");
                crosstie$renderPropNgtoField.setAccessible(true);
            }
            return crosstie$renderPropNgtoField.get(renderProp);
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
    private static Object[] crosstie$getRenderPropGlLists(Object renderProp) {
        try {
            if (crosstie$renderPropGlListsField == null) {
                crosstie$renderPropGlListsField = renderProp.getClass().getDeclaredField("glLists");
                crosstie$renderPropGlListsField.setAccessible(true);
            }
            return (Object[]) crosstie$renderPropGlListsField.get(renderProp);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void crosstie$setRenderPropGlLists(Object renderProp, Object[] glLists) {
        try {
            if (crosstie$renderPropGlListsField == null) {
                crosstie$renderPropGlListsField = renderProp.getClass().getDeclaredField("glLists");
                crosstie$renderPropGlListsField.setAccessible(true);
            }
            crosstie$renderPropGlListsField.set(renderProp, glLists);
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

    @Unique
    private static final class LightState {
        private net.minecraft.world.World world;
        private int x = Integer.MIN_VALUE;
        private int y = Integer.MIN_VALUE;
        private int z = Integer.MIN_VALUE;
        private int lightSignature = Integer.MIN_VALUE;
    }
}
