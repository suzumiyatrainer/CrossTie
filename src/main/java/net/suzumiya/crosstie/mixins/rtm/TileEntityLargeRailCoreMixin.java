package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.rail.TileEntityLargeRailCore", remap = false)
public abstract class TileEntityLargeRailCoreMixin extends TileEntity {

    @Unique
    private static final int CROSSTIE_FORCE_RENDER_CHUNKS = 2;

    @Unique
    private static final double CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS = CROSSTIE_FORCE_RENDER_CHUNKS * 16.0D;

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        int renderDistance = CrossTie.proxy.getClientRenderDistance();
        if (renderDistance > 0) {
            double blockDistance = (renderDistance + CROSSTIE_FORCE_RENDER_CHUNKS) * 16.0D;
            return blockDistance * blockDistance;
        }
        return CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS * CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS;
    }

    @Inject(method = "getRenderBoundingBox", at = @At("RETURN"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    private void crosstie$fixAngelicaRailCulling(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (CrossTieConfig.fixAngelicaRailCulling) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null) {
            return;
        }

        AxisAlignedBB railAabb = this.crosstie$getEffectiveRailAabb(cir.getReturnValue());
        if (railAabb == null) {
            return;
        }

        double px = mc.renderViewEntity.posX;
        double py = mc.renderViewEntity.posY;
        double pz = mc.renderViewEntity.posZ;
        AxisAlignedBB playerRange = AxisAlignedBB.getBoundingBox(px, py, pz, px, py, pz)
                .expand(CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS, CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS,
                        CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS);

        if (railAabb.intersectsWith(playerRange)) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

    }

    @Unique
    private AxisAlignedBB crosstie$getEffectiveRailAabb(AxisAlignedBB baseAabb) {
        AxisAlignedBB mapAabb = this.crosstie$buildRailMapAabb();
        if (baseAabb == null) {
            return mapAabb;
        }
        if (mapAabb == null) {
            return baseAabb;
        }
        return baseAabb.func_111270_a(mapAabb);
    }

    @Unique
    private AxisAlignedBB crosstie$buildRailMapAabb() {
        try {
            Method getAllRailMaps = this.crosstie$findMethod(this.getClass(), "getAllRailMaps");
            Object mapsObj = getAllRailMaps.invoke(this);
            if (!(mapsObj instanceof Object[])) {
                return null;
            }

            Object[] maps = (Object[]) mapsObj;
            if (maps.length == 0) {
                return null;
            }

            int[] holder = {
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
            };
            boolean hasPoint = false;
            for (Object map : maps) {
                if (map == null) {
                    continue;
                }
                Method getStartRP = this.crosstie$findMethod(map.getClass(), "getStartRP");
                Method getEndRP = this.crosstie$findMethod(map.getClass(), "getEndRP");
                hasPoint |= this.crosstie$accumulateRailPos(getStartRP.invoke(map), holder);
                hasPoint |= this.crosstie$accumulateRailPos(getEndRP.invoke(map), holder);
            }
            if (!hasPoint) {
                return null;
            }

            return AxisAlignedBB.getBoundingBox(holder[0] - 3.5D, holder[1] - 10.0D, holder[2] - 3.5D,
                    holder[3] + 5.5D, holder[4] + 2.0D, holder[5] + 5.5D);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private boolean crosstie$accumulateRailPos(Object railPos, int[] holder) {
        if (railPos == null) {
            return false;
        }
        try {
            Field xField = this.crosstie$findField(railPos.getClass(), "blockX");
            Field yField = this.crosstie$findField(railPos.getClass(), "blockY");
            Field zField = this.crosstie$findField(railPos.getClass(), "blockZ");
            int x = xField.getInt(railPos);
            int y = yField.getInt(railPos);
            int z = zField.getInt(railPos);
            holder[0] = Math.min(holder[0], x);
            holder[1] = Math.min(holder[1], y);
            holder[2] = Math.min(holder[2], z);
            holder[3] = Math.max(holder[3], x);
            holder[4] = Math.max(holder[4], y);
            holder[5] = Math.max(holder[5], z);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Unique
    private Method crosstie$findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Unique
    private Field crosstie$findField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
