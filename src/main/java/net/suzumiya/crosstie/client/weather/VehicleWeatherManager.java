package net.suzumiya.crosstie.client.weather;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * RTM車両（Train / Vehicle）による降水（雨・雪・雨パーティクル・雨音）遮蔽を
 * 極軽量かつ確実に管理するマネージャー。
 * <p>
 * 設計上のポイント:
 * <ul>
 *   <li>world.loadedEntityList の走査は 1 tick に 1 回のみ実施（近傍48m以内の車両参照をキャッシュ）</li>
 *   <li>フレーム内は近傍の数両だけ補間座標と sin/cos を事前計算（オブジェクト再利用でGCゼロ）</li>
 *   <li>Minecraft / Angelica の雨クワッドはブロック中心から水平 ±0.5m 広がるため、
 *       車体ローカル OBB に +0.55m のマージンを持たせて壁際・斜め走行・連結部からの貫通を完全に防止</li>
 *   <li>勾配（rotationPitch）に応じた屋根 Y 座標の傾きも考慮</li>
 *   <li>車両の位置・向きハッシュを提供し、停車中は Angelica の Instanced Weather バッファ再構築をゼロに抑制</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class VehicleWeatherManager {

    /** 車両探索半径（雨描画距離最大16 + 車体半長 + 余裕） */
    private static final double SEARCH_RANGE_SQ = 48.0 * 48.0;

    /**
     * 雨クワッドのビルボード半径(0.5m) + 微小マージン(0.05m)。
     * ブロック中心が車体のすぐ外にあってもクワッド端が車内に食い込む現象と、
     * 連結部の妻面隙間からの侵入を同時に防ぐ。
     */
    private static final double RAIN_QUAD_MARGIN = 0.55;

    /** 近傍車両のエンティティ参照キャッシュ（1 tick に 1 回更新） */
    private static final List<EntityVehicleBase<?>> SCANNED_VEHICLES = new ArrayList<>(16);

    /** フレーム内で実際に使用する事前計算済みエントリのプール（GCゼロ） */
    private static final List<CachedVehicleEntry> ENTRY_POOL = new ArrayList<>(16);

    private static int activeCount = 0;
    private static int currentStateHash = 0;
    private static float currentPartialTicks = 0.0f;

    private static WeakReference<World> lastWorldRef = new WeakReference<>(null);
    private static long lastScanTick = Long.MIN_VALUE;
    private static long lastUpdateTick = Long.MIN_VALUE;
    private static int lastPartialBits = Integer.MIN_VALUE;

    /**
     * 再利用可能な車両遮蔽エントリ（毎フレームのアロケーションを回避）。
     */
    private static final class CachedVehicleEntry {
        double posX, posY, posZ;
        double cosYaw, sinYaw;
        double cosPitch, sinPitch;
        double minX, maxX, minZ, maxZ;
        double roofY;
        double offsetX, offsetY, offsetZ;
        double minWorldX, maxWorldX, minWorldZ, maxWorldZ;

        void update(EntityVehicleBase<?> vehicle, VehicleModelHeightMap bounds, float partialTicks) {
            // RenderManager と同じ lastTickPos ベースの線形補間
            this.posX = vehicle.lastTickPosX + (vehicle.posX - vehicle.lastTickPosX) * partialTicks;
            this.posY = vehicle.lastTickPosY + (vehicle.posY - vehicle.lastTickPosY) * partialTicks;
            this.posZ = vehicle.lastTickPosZ + (vehicle.posZ - vehicle.lastTickPosZ) * partialTicks;

            float yaw = vehicle.prevRotationYaw
                    + MathHelper.wrapAngleTo180_float(vehicle.rotationYaw - vehicle.prevRotationYaw) * partialTicks;
            float pitch = vehicle.prevRotationPitch
                    + (vehicle.rotationPitch - vehicle.prevRotationPitch) * partialTicks;

            double radYaw = Math.toRadians(yaw);
            this.cosYaw = Math.cos(radYaw);
            this.sinYaw = Math.sin(radYaw);

            double radPitch = Math.toRadians(pitch);
            this.cosPitch = Math.cos(radPitch);
            this.sinPitch = Math.sin(radPitch);

            if (bounds != null) {
                this.minX = bounds.minX - RAIN_QUAD_MARGIN;
                this.maxX = bounds.maxX + RAIN_QUAD_MARGIN;
                this.minZ = bounds.minZ - RAIN_QUAD_MARGIN;
                this.maxZ = bounds.maxZ + RAIN_QUAD_MARGIN;
                this.roofY = bounds.roofY;
                this.offsetX = bounds.offsetX;
                this.offsetY = bounds.offsetY;
                this.offsetZ = bounds.offsetZ;
            } else {
                this.minX = -1.55 - RAIN_QUAD_MARGIN;
                this.maxX = 1.55 + RAIN_QUAD_MARGIN;
                this.minZ = -10.5 - RAIN_QUAD_MARGIN;
                this.maxZ = 10.5 + RAIN_QUAD_MARGIN;
                this.roofY = 4.1;
                this.offsetX = 0.0;
                this.offsetY = 0.0;
                this.offsetZ = 0.0;
            }

            // 粗いワールド XZ AABB（高速リジェクト用）
            double extX = Math.max(Math.abs(this.minX + this.offsetX), Math.abs(this.maxX + this.offsetX));
            double extZ = Math.max(Math.abs(this.minZ + this.offsetZ), Math.abs(this.maxZ + this.offsetZ));
            double boundR = Math.hypot(extX, extZ) + 0.5;
            this.minWorldX = this.posX - boundR;
            this.maxWorldX = this.posX + boundR;
            this.minWorldZ = this.posZ - boundR;
            this.maxWorldZ = this.posZ + boundR;
        }
    }

    private VehicleWeatherManager() {
    }

    /**
     * レンダリングフレームの partialTicks を設定する。
     */
    public static void setPartialTicks(float partialTicks) {
        currentPartialTicks = partialTicks;
    }

    /**
     * 近傍車両の位置・向きを表す軽量ハッシュを返す。
     * 車両が存在しない場合は 0 を返す。
     * 停車中の車両はハッシュが変化しないため、Angelica の VBO 再構築を完全にスキップできる。
     */
    public static int getNearbyVehiclesStateHash(World world) {
        if (!CrossTieConfig.preventRainInVehicle || world == null) {
            return 0;
        }
        ensureUpdated(world);
        return currentStateHash;
    }

    /**
     * 近傍に車両が存在するかどうかを返す。
     */
    public static boolean hasNearbyVehicles(World world) {
        if (!CrossTieConfig.preventRainInVehicle || world == null) {
            return false;
        }
        ensureUpdated(world);
        return activeCount > 0;
    }

    /**
     * ワールド座標 (x, z) において、車両の屋根による遮蔽を考慮した降水高さを返す。
     * 近傍に車両が無い場合は分岐1回で即座に defaultHeight を返す。
     */
    public static int getOccludedPrecipitationHeight(World world, int x, int z, int defaultHeight) {
        if (!CrossTieConfig.preventRainInVehicle || world == null) {
            return defaultHeight;
        }

        ensureUpdated(world);
        final int count = activeCount;
        if (count == 0) {
            return defaultHeight;
        }

        final double bx = x + 0.5;
        final double bz = z + 0.5;
        double highestRoofY = defaultHeight;

        for (int i = 0; i < count; i++) {
            final CachedVehicleEntry e = ENTRY_POOL.get(i);

            // 1. 粗い AABB による高速リジェクト
            if (bx < e.minWorldX || bx > e.maxWorldX || bz < e.minWorldZ || bz > e.maxWorldZ) {
                continue;
            }

            // 2. ワールド差分 -> 車両ローカル座標への逆回転 R_y(-yaw)（事前計算済み cos/sin 使用）
            final double dx = bx - e.posX;
            final double dz = bz - e.posZ;
            final double rotX = dx * e.cosYaw - dz * e.sinYaw;
            final double rotZ = dx * e.sinYaw + dz * e.cosYaw;

            final double lx = rotX - e.offsetX;
            final double lz = rotZ - e.offsetZ;

            // 3. 雨クワッド半径マージン込みのローカル OBB 判定
            if (lx >= e.minX && lx <= e.maxX && lz >= e.minZ && lz <= e.maxZ) {
                // RenderVehicleBase: glRotatef(-pitch, 1, 0, 0) による勾配時の屋根高さ補正
                // y' = y * cos(pitch) + z * sin(pitch)
                final double localRoofY = e.roofY + e.offsetY;
                final double worldRoofY = e.posY + localRoofY * e.cosPitch + rotZ * e.sinPitch;
                if (worldRoofY > highestRoofY) {
                    highestRoofY = worldRoofY;
                }
            }
        }

        if (highestRoofY > defaultHeight) {
            return (int) Math.ceil(highestRoofY);
        }
        return defaultHeight;
    }

    /**
     * 1 tick に 1 回だけ loadedEntityList を走査し、フレーム内では近傍車両の補間のみ更新する。
     */
    private static void ensureUpdated(World world) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase view = mc.renderViewEntity;
        if (view == null) {
            activeCount = 0;
            currentStateHash = 0;
            return;
        }

        final long tick = world.getTotalWorldTime();
        final int partialBits = Float.floatToIntBits(currentPartialTicks);
        final boolean worldChanged = lastWorldRef.get() != world;

        if (!worldChanged && tick == lastUpdateTick && partialBits == lastPartialBits) {
            return;
        }

        if (worldChanged) {
            lastWorldRef = new WeakReference<>(world);
            lastScanTick = Long.MIN_VALUE;
            SCANNED_VEHICLES.clear();
        }

        // 1 tick に 1 回だけ loadedEntityList から近傍車両を抽出
        if (tick != lastScanTick) {
            lastScanTick = tick;
            SCANNED_VEHICLES.clear();

            final List<?> loaded = world.loadedEntityList;
            if (loaded != null && !loaded.isEmpty()) {
                final double camX = view.posX;
                final double camZ = view.posZ;
                for (int i = 0, len = loaded.size(); i < len; i++) {
                    Object obj = loaded.get(i);
                    if (obj instanceof EntityVehicleBase) {
                        EntityVehicleBase<?> vehicle = (EntityVehicleBase<?>) obj;
                        if (vehicle.isDead) {
                            continue;
                        }
                        double dX = vehicle.posX - camX;
                        double dZ = vehicle.posZ - camZ;
                        if (dX * dX + dZ * dZ <= SEARCH_RANGE_SQ) {
                            SCANNED_VEHICLES.add(vehicle);
                        }
                    }
                }
            }
        }

        lastUpdateTick = tick;
        lastPartialBits = partialBits;

        // 近傍車両の補間エントリを更新（プール再利用で GC ゼロ）
        final int scannedSize = SCANNED_VEHICLES.size();
        int count = 0;
        int hash = 0;

        for (int i = 0; i < scannedSize; i++) {
            EntityVehicleBase<?> vehicle = SCANNED_VEHICLES.get(i);
            if (vehicle.isDead) {
                continue;
            }
            VehicleModelHeightMap bounds = VehicleModelHeightMap.getOrCompute(vehicle);
            CachedVehicleEntry entry;
            if (count < ENTRY_POOL.size()) {
                entry = ENTRY_POOL.get(count);
            } else {
                entry = new CachedVehicleEntry();
                ENTRY_POOL.add(entry);
            }
            entry.update(vehicle, bounds, currentPartialTicks);
            count++;

            // 状態ハッシュ（0.25m 単位の位置 + 約2度単位の向き + 0.5m単位の高さ）
            // 停車中・微振動では変化せず、走行時にのみ変化して再構築を促す
            int qx = MathHelper.floor_double(entry.posX * 4.0);
            int qy = MathHelper.floor_double(entry.posY * 2.0);
            int qz = MathHelper.floor_double(entry.posZ * 4.0);
            int qYaw = MathHelper.floor_float(vehicle.rotationYaw * 0.5f);
            hash = hash * 31 + vehicle.getEntityId();
            hash = hash * 31 + qx;
            hash = hash * 31 + qy;
            hash = hash * 31 + qz;
            hash = hash * 31 + qYaw;
        }

        activeCount = count;
        currentStateHash = count == 0 ? 0 : (hash == 0 ? 1 : hash);
    }

    /**
     * ワールド切替時などのキャッシュリセット。
     */
    public static void clearRuntimeCache() {
        SCANNED_VEHICLES.clear();
        activeCount = 0;
        currentStateHash = 0;
        lastScanTick = Long.MIN_VALUE;
        lastUpdateTick = Long.MIN_VALUE;
        lastPartialBits = Integer.MIN_VALUE;
    }
}
