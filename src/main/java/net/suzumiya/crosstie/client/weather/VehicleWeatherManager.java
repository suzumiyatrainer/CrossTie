package net.suzumiya.crosstie.client.weather;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 降水（雨・雪・パーティクル・雨音）の車両モデル面による遮蔽を管理するマネージャー。
 */
@SideOnly(Side.CLIENT)
public final class VehicleWeatherManager {

    private static final List<CachedVehicleEntry> NEARBY_VEHICLES = new ArrayList<>();
    private static long lastCacheUpdateTick = -1;
    private static int lastCameraChunkX = Integer.MIN_VALUE;
    private static int lastCameraChunkZ = Integer.MIN_VALUE;

    private static final class CachedVehicleEntry {
        final EntityVehicleBase<?> vehicle;
        final VehicleModelHeightMap heightMap;
        final float[] modelOffset;
        final double posX, posY, posZ;
        final float yaw;
        final double minWorldX, maxWorldX;
        final double minWorldZ, maxWorldZ;
        final double bboxMaxY;

        CachedVehicleEntry(EntityVehicleBase<?> vehicle, VehicleModelHeightMap heightMap, float[] offset) {
            this.vehicle = vehicle;
            this.heightMap = heightMap;
            this.modelOffset = offset != null ? offset : new float[] { 0, 0, 0 };
            this.posX = vehicle.posX;
            this.posY = vehicle.posY;
            this.posZ = vehicle.posZ;
            this.yaw = vehicle.rotationYaw;
            this.bboxMaxY = vehicle.boundingBox != null ? vehicle.boundingBox.maxY : vehicle.posY + 3.0;

            // 回転を考慮したバウンディング半径（余裕を持ったXZ範囲）
            double radius = 18.0;
            if (vehicle.boundingBox != null) {
                double dx = vehicle.boundingBox.maxX - vehicle.boundingBox.minX;
                double dz = vehicle.boundingBox.maxZ - vehicle.boundingBox.minZ;
                radius = Math.max(radius, Math.sqrt(dx * dx + dz * dz) * 0.5 + 2.0);
            }
            this.minWorldX = this.posX - radius;
            this.maxWorldX = this.posX + radius;
            this.minWorldZ = this.posZ - radius;
            this.maxWorldZ = this.posZ + radius;
        }
    }

    private VehicleWeatherManager() {
    }

    /**
     * カメラ周囲の車両キャッシュを更新する（必要時のみ）。
     */
    private static void updateCacheIfNeeded(World world) {
        if (world == null)
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        long currentTick = world.getTotalWorldTime();
        int camChunkX = ((int) mc.renderViewEntity.posX) >> 4;
        int camChunkZ = ((int) mc.renderViewEntity.posZ) >> 4;

        if (currentTick == lastCacheUpdateTick && camChunkX == lastCameraChunkX && camChunkZ == lastCameraChunkZ) {
            return;
        }

        lastCacheUpdateTick = currentTick;
        lastCameraChunkX = camChunkX;
        lastCameraChunkZ = camChunkZ;

        NEARBY_VEHICLES.clear();

        double camX = mc.renderViewEntity.posX;
        double camY = mc.renderViewEntity.posY;
        double camZ = mc.renderViewEntity.posZ;
        double range = 48.0; // カメラから48m以内の車両を対象

        @SuppressWarnings("unused")
        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(camX - range, camY - range, camZ - range, camX + range,
                camY + range, camZ + range);

        List<?> list = world.loadedEntityList;
        if (list == null || list.isEmpty())
            return;

        double rangeSq = range * range;
        for (Object obj : list) {
            if (obj instanceof EntityVehicleBase) {
                EntityVehicleBase<?> vehicle = (EntityVehicleBase<?>) obj;
                if (vehicle.isDead)
                    continue;

                double dX = vehicle.posX - camX;
                double dZ = vehicle.posZ - camZ;
                if (dX * dX + dZ * dZ > rangeSq)
                    continue;

                VehicleModelHeightMap hmap = VehicleModelHeightMap.getOrCompute(vehicle);
                float[] offset = null;
                try {
                    ModelSetVehicleBaseClient<?> modelSet = (ModelSetVehicleBaseClient<?>) vehicle.getModelSet();
                    if (modelSet != null && modelSet.getConfig() != null) {
                        offset = modelSet.getConfig().offset;
                    }
                } catch (Throwable ignored) {
                }

                NEARBY_VEHICLES.add(new CachedVehicleEntry(vehicle, hmap, offset));
            }
        }
    }

    /**
     * ワールド座標 (x, z) において、車両のモデル面による遮蔽高さを考慮した降水高さを返す。
     *
     * @param world         対象ワールド
     * @param x             ブロックX
     * @param z             ブロックZ
     * @param defaultHeight バニラまたは呼び出し元が計算した元の降水高さ
     * @return 遮蔽考慮後の降水高さ
     */
    public static int getOccludedPrecipitationHeight(World world, int x, int z, int defaultHeight) {
        if (!CrossTieConfig.preventRainInVehicle) {
            return defaultHeight;
        }

        updateCacheIfNeeded(world);
        if (NEARBY_VEHICLES.isEmpty()) {
            return defaultHeight;
        }

        double blockCenterX = x + 0.5;
        double blockCenterZ = z + 0.5;
        double highestRoofY = defaultHeight;

        for (int i = 0; i < NEARBY_VEHICLES.size(); i++) {
            CachedVehicleEntry entry = NEARBY_VEHICLES.get(i);

            // XZバウンディング外ならスキップ
            if (blockCenterX < entry.minWorldX || blockCenterX > entry.maxWorldX || blockCenterZ < entry.minWorldZ
                    || blockCenterZ > entry.maxWorldZ) {
                continue;
            }

            // ワールド座標から車両ローカル座標系への逆変換 (dx, dz) -> (lx, lz)
            double dx = blockCenterX - entry.posX;
            double dz = blockCenterZ - entry.posZ;

            // OpenGL glRotatef(yaw, 0, 1, 0) の逆回転 R_y(-yaw):
            // lx = dx * cos(yaw) - dz * sin(yaw)
            // lz = dx * sin(yaw) + dz * cos(yaw)
            double rad = Math.toRadians(entry.yaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            double lx = (dx * cos - dz * sin) - entry.modelOffset[0];
            double lz = (dx * sin + dz * cos) - entry.modelOffset[2];

            if (entry.heightMap != null) {
                // モデル面のハイトマップからローカル高さを照会
                float localY = entry.heightMap.getHeight((float) lx, (float) lz);
                if (localY != VehicleModelHeightMap.NO_HEIGHT) {
                    double worldRoofY = entry.posY + entry.modelOffset[1] + localY;
                    if (worldRoofY > highestRoofY) {
                        highestRoofY = worldRoofY;
                    }
                }
            } else {
                // モデルポリゴン情報がない場合のフォールバック: AABB判定
                if (entry.vehicle.boundingBox != null && blockCenterX >= entry.vehicle.boundingBox.minX
                        && blockCenterX <= entry.vehicle.boundingBox.maxX
                        && blockCenterZ >= entry.vehicle.boundingBox.minZ
                        && blockCenterZ <= entry.vehicle.boundingBox.maxZ) {
                    if (entry.bboxMaxY > highestRoofY) {
                        highestRoofY = entry.bboxMaxY;
                    }
                }
            }
        }

        if (highestRoofY > defaultHeight) {
            return (int) Math.ceil(highestRoofY);
        }

        return defaultHeight;
    }
}
