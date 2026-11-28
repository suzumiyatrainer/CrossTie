package net.suzumiya.crosstie.cache;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ワールド単位で {@link EntityVehicleBase} を UUID から O(1) で引けるインデックス。
 *
 * <p>Minecraft 1.7.10 の {@code World.func_152378_a(UUID)} はプレイヤー専用線形探索であるため、
 * 代わりに {@code World.onEntityAdded} / {@code World.onEntityRemoved} で車両エンティティのみを
 * 追跡し、チャンクロード・スポーン双方を取りこぼしゼロ・メモリリークゼロで高速化する。</p>
 */
public final class VehicleUUIDIndex {

    public static final VehicleUUIDIndex INSTANCE = new VehicleUUIDIndex();

    private static final Map<World, Map<UUID, EntityVehicleBase<?>>> INDEX =
            Collections.synchronizedMap(new WeakHashMap<World, Map<UUID, EntityVehicleBase<?>>>());

    private VehicleUUIDIndex() {
    }

    public static void onEntityAdded(World world, Entity entity) {
        if (world == null || !(entity instanceof EntityVehicleBase)) {
            return;
        }
        UUID uuid = entity.getUniqueID();
        if (uuid == null) {
            return;
        }
        Map<UUID, EntityVehicleBase<?>> worldMap = getOrCreateWorldMap(world);
        synchronized (worldMap) {
            worldMap.put(uuid, (EntityVehicleBase<?>) entity);
        }
    }

    public static void onEntityRemoved(World world, Entity entity) {
        if (world == null || !(entity instanceof EntityVehicleBase)) {
            return;
        }
        UUID uuid = entity.getUniqueID();
        if (uuid == null) {
            return;
        }
        Map<UUID, EntityVehicleBase<?>> worldMap = INDEX.get(world);
        if (worldMap != null) {
            synchronized (worldMap) {
                EntityVehicleBase<?> current = worldMap.get(uuid);
                if (current == entity) {
                    worldMap.remove(uuid);
                }
            }
        }
    }

    public static EntityVehicleBase<?> get(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return null;
        }
        Map<UUID, EntityVehicleBase<?>> worldMap = INDEX.get(world);
        if (worldMap == null) {
            return null;
        }
        EntityVehicleBase<?> vehicle;
        synchronized (worldMap) {
            vehicle = worldMap.get(uuid);
        }
        if (vehicle != null && !vehicle.isDead && vehicle.worldObj == world) {
            return vehicle;
        }
        return null;
    }

    public static void clearWorld(World world) {
        if (world != null) {
            INDEX.remove(world);
        }
    }

    private static Map<UUID, EntityVehicleBase<?>> getOrCreateWorldMap(World world) {
        synchronized (INDEX) {
            Map<UUID, EntityVehicleBase<?>> worldMap = INDEX.get(world);
            if (worldMap == null) {
                worldMap = new HashMap<UUID, EntityVehicleBase<?>>();
                INDEX.put(world, worldMap);
            }
            return worldMap;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null) {
            clearWorld(event.world);
        }
    }
}
