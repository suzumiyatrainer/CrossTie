package net.suzumiya.crosstie.client.weather;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 車両モデルのローカル外形寸法（幅・長さ・屋根高さ）を超軽量に計算・キャッシュするクラス。
 * <p>
 * Minecraft の雨・雪は 1x1m ブロック柱単位かつ整数 Y 座標で遮蔽されるため、
 * 高コストな 2D ポリゴンラスタライズや KaizPatchX モデルの強制同期ロードは行わず、
 * 1パスの頂点 min/max 走査（ロード済み時のみ）または Config 寸法から O(1) で参照可能な
 * バウンディング情報を生成する。
 */
@SideOnly(Side.CLIENT)
public final class VehicleModelHeightMap {

    /** 異常値に対する安全クランプ範囲（車両ローカル Y） */
    private static final float MIN_VALID_ROOF_Y = 2.0f;
    private static final float MAX_VALID_ROOF_Y = 8.0f;

    /** 非同期ロード待ちモデルの再確認間隔（ミリ秒） */
    private static final long RETRY_RESOLVE_INTERVAL_MS = 2000L;

    private static final Map<String, VehicleModelHeightMap> CACHE = new ConcurrentHashMap<>();

    public final float minX;
    public final float maxX;
    public final float minZ;
    public final float maxZ;
    public final float roofY;
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;
    public final boolean resolvedFromModel;
    private final long createdAtMs;

    private VehicleModelHeightMap(
            float minX, float maxX, float minZ, float maxZ, float roofY,
            float offsetX, float offsetY, float offsetZ,
            boolean resolvedFromModel) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.roofY = roofY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.resolvedFromModel = resolvedFromModel;
        this.createdAtMs = System.currentTimeMillis();
    }

    /**
     * 車両モデルの外形バウンディングを取得（または極軽量に生成してキャッシュ）する。
     * KaizPatchX の非同期モデルロードをブロックしない。
     */
    public static VehicleModelHeightMap getOrCompute(EntityVehicleBase<?> vehicle) {
        if (vehicle == null) {
            return null;
        }
        String modelName = vehicle.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = vehicle.getDefaultName();
        }
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }

        VehicleModelHeightMap cached = CACHE.get(modelName);
        if (cached != null) {
            if (cached.resolvedFromModel) {
                return cached;
            }
            // モデルが非同期ロード完了していれば1回だけ実測値に昇格させる
            if (System.currentTimeMillis() - cached.createdAtMs < RETRY_RESOLVE_INTERVAL_MS) {
                return cached;
            }
        }

        VehicleModelHeightMap computed = computeLightweight(vehicle);
        if (computed != null) {
            CACHE.put(modelName, computed);
            return computed;
        }
        return cached;
    }

    private static VehicleModelHeightMap computeLightweight(EntityVehicleBase<?> vehicle) {
        ModelSetVehicleBaseClient<?> modelSet;
        try {
            Object raw = vehicle.getModelSet();
            if (!(raw instanceof ModelSetVehicleBaseClient)) {
                return createDefaultFallback(vehicle, null);
            }
            modelSet = (ModelSetVehicleBaseClient<?>) raw;
        } catch (Throwable ignored) {
            return createDefaultFallback(vehicle, null);
        }

        VehicleBaseConfig cfg = modelSet.getConfig();
        float offX = 0.0f;
        float offY = 0.0f;
        float offZ = 0.0f;
        if (cfg != null && cfg.offset != null && cfg.offset.length >= 3) {
            offX = cfg.offset[0];
            offY = cfg.offset[1];
            offZ = cfg.offset[2];
        }

        // 既にロード済みの PolygonModel であれば、同期ロードを起こさず1パス頂点スキャンのみ実行
        if (modelSet.vehicleModel != null && modelSet.vehicleModel.model instanceof PolygonModel) {
            PolygonModel polygonModel = (PolygonModel) modelSet.vehicleModel.model;
            List<GroupObject> groups = polygonModel.getGroupObjects();
            if (groups != null && !groups.isEmpty()) {
                VehicleModelHeightMap scanned = scanVertices(groups, cfg, offX, offY, offZ);
                if (scanned != null) {
                    return scanned;
                }
            }
        }

        return createDefaultFallback(vehicle, cfg);
    }

    /**
     * パンタグラフ・アンテナ等を除外した1パスの頂点 min/max 走査で正確な車体外形と屋根高さを求める。
     * ラスタライズ・メモリ確保を一切行わないため極軽量。
     */
    private static VehicleModelHeightMap scanVertices(
            List<GroupObject> groups, VehicleBaseConfig cfg,
            float offX, float offY, float offZ) {
        Set<String> excludedNames = null;
        if (cfg != null && (cfg.pantograph_front != null || cfg.pantograph_back != null)) {
            excludedNames = new HashSet<>();
            collectConfigParts(excludedNames, cfg.pantograph_front);
            collectConfigParts(excludedNames, cfg.pantograph_back);
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean found = false;

        for (int g = 0, gLen = groups.size(); g < gLen; g++) {
            GroupObject group = groups.get(g);
            if (group == null || group.faces == null || group.faces.isEmpty()) {
                continue;
            }
            if (isExcludedRoofPart(group.name, excludedNames)) {
                continue;
            }

            List<Face> faces = group.faces;
            for (int f = 0, fLen = faces.size(); f < fLen; f++) {
                Face face = faces.get(f);
                if (face == null || face.vertices == null) {
                    continue;
                }
                Vertex[] verts = face.vertices;
                for (int v = 0, vLen = verts.length; v < vLen; v++) {
                    Vertex vertex = verts[v];
                    if (vertex == null) {
                        continue;
                    }
                    float vx = vertex.getX();
                    float vy = vertex.getY();
                    float vz = vertex.getZ();
                    if (vx < minX) minX = vx;
                    if (vx > maxX) maxX = vx;
                    if (vz < minZ) minZ = vz;
                    if (vz > maxZ) maxZ = vz;
                    if (vy > maxY) maxY = vy;
                    found = true;
                }
            }
        }

        if (!found || minX >= maxX || minZ >= maxZ) {
            return null;
        }

        // TrainConfig に車体半長が定義されている場合、モデルが分割パーツ等で短く見積もられないよう下限を保証
        if (cfg instanceof TrainConfig) {
            TrainConfig tcfg = (TrainConfig) cfg;
            if (tcfg.trainDistance > 0.0f) {
                if (minZ > -tcfg.trainDistance) minZ = -tcfg.trainDistance;
                if (maxZ < tcfg.trainDistance) maxZ = tcfg.trainDistance;
            }
        }

        float roofY = clampRoofY(maxY);
        return new VehicleModelHeightMap(minX, maxX, minZ, maxZ, roofY, offX, offY, offZ, true);
    }

    private static VehicleModelHeightMap createDefaultFallback(EntityVehicleBase<?> vehicle, VehicleBaseConfig cfg) {
        float offX = 0.0f;
        float offY = 0.0f;
        float offZ = 0.0f;
        float halfW = 1.5f;
        float halfL = 10.25f;
        float roofY = 4.1f;

        if (cfg != null) {
            if (cfg.offset != null && cfg.offset.length >= 3) {
                offX = cfg.offset[0];
                offY = cfg.offset[1];
                offZ = cfg.offset[2];
            }
            float[] size = cfg.getSize();
            if (size != null && size.length >= 2) {
                halfW = Math.max(halfW, size[0] * 0.5f);
            }
            if (cfg instanceof TrainConfig) {
                TrainConfig tcfg = (TrainConfig) cfg;
                if (tcfg.trainDistance > 0.0f) {
                    halfL = Math.max(halfL, tcfg.trainDistance);
                }
                float[][] bpos = tcfg.getBogiePos();
                if (bpos != null && bpos.length >= 2 && bpos[0] != null && bpos[1] != null
                        && bpos[0].length >= 3 && bpos[1].length >= 3) {
                    float maxBogieZ = Math.max(Math.abs(bpos[0][2]), Math.abs(bpos[1][2]));
                    halfL = Math.max(halfL, maxBogieZ + 3.5f);
                }
            } else if (!(vehicle instanceof EntityTrainBase)) {
                // 自動車・バス・船等の非鉄道車両
                halfL = Math.max(halfW * 1.5f, 2.5f);
                roofY = 3.2f;
            }
        } else if (!(vehicle instanceof EntityTrainBase)) {
            halfL = 2.5f;
            roofY = 3.2f;
        }

        return new VehicleModelHeightMap(-halfW, halfW, -halfL, halfL, roofY, offX, offY, offZ, false);
    }

    private static float clampRoofY(float y) {
        if (y < MIN_VALID_ROOF_Y) return MIN_VALID_ROOF_Y;
        if (y > MAX_VALID_ROOF_Y) return MAX_VALID_ROOF_Y;
        return y;
    }

    private static void collectConfigParts(Set<String> set, VehicleBaseConfig.VehicleParts[] partsArray) {
        if (partsArray == null) return;
        for (VehicleBaseConfig.VehicleParts part : partsArray) {
            if (part == null) continue;
            if (part.objects != null) {
                for (String objName : part.objects) {
                    if (objName != null) {
                        set.add(objName.toLowerCase(Locale.ROOT).trim());
                    }
                }
            }
            if (part.childParts != null) {
                collectConfigParts(set, part.childParts);
            }
        }
    }

    private static boolean isExcludedRoofPart(String partName, Set<String> configExcludedNames) {
        if (partName == null || partName.isEmpty()) return false;
        String lower = partName.toLowerCase(Locale.ROOT).trim();
        if (configExcludedNames != null && configExcludedNames.contains(lower)) {
            return true;
        }
        return lower.contains("panto")
                || lower.contains("panta")
                || lower.contains("antenna")
                || lower.contains("anten")
                || lower.contains("musen")
                || lower.contains("trolley")
                || lower.contains("pole");
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
