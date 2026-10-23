package net.suzumiya.crosstie.client.weather;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 降水（雨・雪・パーティクル・雨音）の車両モデル面による遮蔽を管理するマネージャー。 高速走行時にも追従するよう、partialTicks
 * によるフレーム補間座標をサポート。
 */
@SideOnly(Side.CLIENT)
public final class VehicleWeatherManager {

    private static final List<CachedVehicleEntry> NEARBY_VEHICLES = new ArrayList<>();
    @SuppressWarnings("unused")
    private static long lastCacheUpdateTick = -1;
    private static float currentPartialTicks = 0.0f;
    private static int lastFrameHash = 0;

    public static final class CachedVehicleEntry {
        public final EntityVehicleBase<?> vehicle;
        public final VehicleModelHeightMap heightMap;
        public final float[] modelOffset;
        public final double posX, posY, posZ;
        public final float yaw;
        public final double minWorldX, maxWorldX;
        public final double minWorldZ, maxWorldZ;
        public final double halfWidth;
        public final double halfLength;
        public final double bboxMaxY;
        public final double nominalRoofY;

        public CachedVehicleEntry(EntityVehicleBase<?> vehicle, VehicleModelHeightMap heightMap, float[] offset,
                float partialTicks) {
            this.vehicle = vehicle;
            this.heightMap = heightMap;
            this.modelOffset = offset != null ? offset : new float[] { 0, 0, 0 };

            // 高速走行対応: レンダリングフレームでの線形補間座標・回転角
            this.posX = vehicle.prevPosX + (vehicle.posX - vehicle.prevPosX) * partialTicks;
            this.posY = vehicle.prevPosY + (vehicle.posY - vehicle.prevPosY) * partialTicks;
            this.posZ = vehicle.prevPosZ + (vehicle.posZ - vehicle.prevPosZ) * partialTicks;
            this.yaw = vehicle.prevRotationYaw
                    + MathHelper.wrapAngleTo180_float(vehicle.rotationYaw - vehicle.prevRotationYaw) * partialTicks;
            this.bboxMaxY = vehicle.boundingBox != null ? vehicle.boundingBox.maxY : (this.posY + 3.6);

            // 車両の寸法（半幅・半長）の決定
            double hw = 1.6; // デフォルト半幅 1.6m（全幅 3.2m）
            double hl = 11.0; // デフォルト半長 11.0m（全長 22.0m）

            if (heightMap != null && heightMap.hasFaces()) {
                double spanX = Math.max(Math.abs(heightMap.getMinX()), Math.abs(heightMap.getMaxX()));
                double spanZ = Math.max(Math.abs(heightMap.getMinZ()), Math.abs(heightMap.getMaxZ()));
                hw = Math.max(hw, spanX);
                hl = Math.max(hl, spanZ);
                this.nominalRoofY = heightMap.getNominalRoofY();
            } else {
                if (vehicle instanceof EntityTrainBase) {
                    try {
                        ModelSetVehicleBaseClient<?> mset = (ModelSetVehicleBaseClient<?>) vehicle.getModelSet();
                        if (mset != null && mset.getConfig() instanceof TrainConfig) {
                            TrainConfig tcfg = (TrainConfig) mset.getConfig();
                            float[][] bpos = tcfg.getBogiePos();
                            if (bpos != null && bpos.length >= 2) {
                                double maxBogieZ = Math.max(Math.abs(bpos[0][2]), Math.abs(bpos[1][2]));
                                hl = Math.max(hl, maxBogieZ + 3.5); // 台車外側オーバーハングを加算
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                this.nominalRoofY = 3.5;
            }

            this.halfWidth = hw;
            this.halfLength = hl;

            // 回転を考慮したバウンディング半径
            double boundingRadius = Math.sqrt(hw * hw + hl * hl) + 2.0;
            this.minWorldX = this.posX - boundingRadius;
            this.maxWorldX = this.posX + boundingRadius;
            this.minWorldZ = this.posZ - boundingRadius;
            this.maxWorldZ = this.posZ + boundingRadius;
        }

        /**
         * ローカル座標 (lx, lz) が車体の外形輪郭内にあるかを判定する。
         */
        public boolean isInsideVehicleBounds(double lx, double lz) {
            return Math.abs(lx) <= halfWidth && Math.abs(lz) <= halfLength;
        }
    }

    private VehicleWeatherManager() {
    }

    /**
     * レンダリングフレームの partialTicks をセットする。
     */
    public static void setPartialTicks(float partialTicks) {
        currentPartialTicks = partialTicks;
    }

    /**
     * カメラ周囲に車両が存在するかを判定する（Angelica等の再構築トリガー用）。
     */
    public static boolean hasNearbyVehicles(World world) {
        if (!CrossTieConfig.preventRainInVehicle || world == null) {
            return false;
        }
        updateCacheIfNeeded(world);
        return !NEARBY_VEHICLES.isEmpty();
    }

    /**
     * カメラ周囲の車両キャッシュを更新する（高速走行時のフレーム補間に常時追従）。
     */
    private static void updateCacheIfNeeded(World world) {
        if (world == null)
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        long currentTick = world.getTotalWorldTime();
        // フレームの一意性を表すハッシュ（Tick + partialTicks整数表現）
        int frameHash = (int) (currentTick * 31 + Float.floatToIntBits(currentPartialTicks));

        if (frameHash == lastFrameHash && !NEARBY_VEHICLES.isEmpty()) {
            return;
        }

        lastCacheUpdateTick = currentTick;
        lastFrameHash = frameHash;

        NEARBY_VEHICLES.clear();

        double camX = mc.renderViewEntity.posX;
        @SuppressWarnings("unused")
        double camY = mc.renderViewEntity.posY;
        double camZ = mc.renderViewEntity.posZ;
        double range = 64.0; // 描画距離に合わせて十分な探索範囲
        double rangeSq = range * range;

        List<?> list = world.loadedEntityList;
        if (list == null || list.isEmpty())
            return;

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

                NEARBY_VEHICLES.add(new CachedVehicleEntry(vehicle, hmap, offset, currentPartialTicks));
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

            // OpenGL glRotatef(yaw, 0, 1, 0) の逆回転 R_y(-yaw)
            double rad = Math.toRadians(entry.yaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            double lx = (dx * cos - dz * sin) - entry.modelOffset[0];
            double lz = (dx * sin + dz * cos) - entry.modelOffset[2];

            if (entry.heightMap != null && entry.heightMap.hasFaces()) {
                // モデル面のハイトマップからローカル高さを照会
                float localY = entry.heightMap.getHeight((float) lx, (float) lz);
                if (localY != VehicleModelHeightMap.NO_HEIGHT) {
                    double worldRoofY = entry.posY + entry.modelOffset[1] + localY;
                    if (worldRoofY > highestRoofY) {
                        highestRoofY = worldRoofY;
                    }
                } else if (entry.heightMap.isInsideRoofBounds((float) lx, (float) lz)) {
                    // 屋根の輪郭内側で微小サンプリング抜けが生じた場合: モデルの基準屋根高さを適用
                    double worldRoofY = entry.posY + entry.modelOffset[1] + entry.nominalRoofY;
                    if (worldRoofY > highestRoofY) {
                        highestRoofY = worldRoofY;
                    }
                } else if (entry.isInsideVehicleBounds(lx, lz)) {
                    // 車内全域セーフティネット: モデルパーツ分割等でハイトマップ境界が短い場合でも、車体内側全体を確実に遮蔽
                    double worldRoofY = entry.posY + entry.modelOffset[1] + entry.nominalRoofY;
                    if (worldRoofY > highestRoofY) {
                        highestRoofY = worldRoofY;
                    }
                }
            } else {
                // モデルポリゴン情報がない場合のフォールバック:
                if (entry.isInsideVehicleBounds(lx, lz)) {
                    double fallbackRoofY = entry.posY + entry.modelOffset[1] + entry.nominalRoofY;
                    if (fallbackRoofY > highestRoofY) {
                        highestRoofY = fallbackRoofY;
                    }
                } else if (entry.vehicle.boundingBox != null && blockCenterX >= entry.vehicle.boundingBox.minX
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
