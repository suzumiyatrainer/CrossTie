package net.suzumiya.crosstie.client.weather;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 車両モデルの上面（屋根、クーラー等の突起物面）のローカルハイトマップを管理・計算するクラス。
 * <p>
 * 幾何学的解析により：
 * - クーラーやベンチレーター等の「面積を持つ箱状突起物」は天面で雨・雪を遮蔽
 * - パンタグラフやアンテナ等の「細い骨組み・棒状突起物」は雨・雪が突き抜けて下の屋根面に打ち付ける
 * 動的判定を行う。
 */
@SideOnly(Side.CLIENT)
public class VehicleModelHeightMap {

    public static final float CELL_SIZE = 0.20f; // 20cm 解像度（細部追従と隙間防止）
    public static final float NO_HEIGHT = Float.NEGATIVE_INFINITY;

    private static final Map<String, VehicleModelHeightMap> CACHE = new ConcurrentHashMap<>();

    private final float minX;
    private final float maxX;
    private final float minZ;
    private final float maxZ;
    private final float roofMinX;
    private final float roofMaxX;
    private final float roofMinZ;
    private final float roofMaxZ;
    private final float nominalRoofY;
    private final int cellsX;
    private final int cellsZ;
    private final float[] heightMap;
    private final boolean hasFaces;

    public VehicleModelHeightMap(float minX, float maxX, float minZ, float maxZ,
                                float roofMinX, float roofMaxX, float roofMinZ, float roofMaxZ,
                                float nominalRoofY, int cellsX, int cellsZ, float[] heightMap, boolean hasFaces) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.roofMinX = roofMinX;
        this.roofMaxX = roofMaxX;
        this.roofMinZ = roofMinZ;
        this.roofMaxZ = roofMaxZ;
        this.nominalRoofY = nominalRoofY;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.heightMap = heightMap;
        this.hasFaces = hasFaces;
    }

    public float getMinX() { return minX; }
    public float getMaxX() { return maxX; }
    public float getMinZ() { return minZ; }
    public float getMaxZ() { return maxZ; }
    public float getRoofMinX() { return roofMinX; }
    public float getRoofMaxX() { return roofMaxX; }
    public float getRoofMinZ() { return roofMinZ; }
    public float getRoofMaxZ() { return roofMaxZ; }
    public float getNominalRoofY() { return nominalRoofY; }
    public boolean hasFaces() { return hasFaces; }

    /**
     * 指定のローカル座標が車体の屋根カバー範囲の内側にあるかを判定する。
     */
    public boolean isInsideRoofBounds(float lx, float lz) {
        return hasFaces && lx >= roofMinX && lx <= roofMaxX && lz >= roofMinZ && lz <= roofMaxZ;
    }

    /**
     * 車両からモデルハイトマップを取得（または生成してキャッシュ）する。
     */
    public static VehicleModelHeightMap getOrCompute(EntityVehicleBase<?> vehicle) {
        if (vehicle == null) return null;
        String modelName = vehicle.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = vehicle.getDefaultName();
        }
        if (modelName == null) return null;

        VehicleModelHeightMap cached = CACHE.get(modelName);
        if (cached != null) {
            return cached;
        }

        try {
            ModelSetVehicleBaseClient<?> modelSet = (ModelSetVehicleBaseClient<?>) vehicle.getModelSet();
            if (modelSet == null || modelSet.vehicleModel == null || modelSet.vehicleModel.model == null) {
                return null;
            }

            IModelNGT model = modelSet.vehicleModel.model;
            ensureModelLoaded(model);
            VehicleModelHeightMap generated = computeFromModel(modelName, model, modelSet.getConfig());
            compactModelIfPossible(model);

            if (generated != null) {
                CACHE.put(modelName, generated);
            }
            return generated;
        } catch (Throwable t) {
            net.suzumiya.crosstie.CrossTie.LOGGER.warn("[CrossTie] 車両モデルハイトマップ生成中にエラーが発生しました: " + modelName, t);
            return null;
        }
    }

    /**
     * KaizPatchX (fixRTM) の AsyncCachedModel 等でモデルがアンロードされている場合に同期ロードを保証する。
     */
    private static void ensureModelLoaded(IModelNGT model) {
        if (model == null) return;
        try {
            Class<?> utilCls = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedModelUtil");
            java.lang.reflect.Method m = utilCls.getMethod("prepareSharedUse", IModelNGT.class);
            m.invoke(null, model);
            return;
        } catch (Throwable ignored) {
        }

        try {
            java.lang.reflect.Method m = model.getClass().getMethod("ensureLoadedForSharedUse");
            m.setAccessible(true);
            m.invoke(model);
        } catch (Throwable ignored) {
        }
    }

    /**
     * ハイトマップ生成完了後、メモリ節約のためアンロード状態に戻す（KaizPatchXキャッシュ協調）。
     */
    private static void compactModelIfPossible(IModelNGT model) {
        if (model == null) return;
        try {
            Class<?> utilCls = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedModelUtil");
            java.lang.reflect.Method m = utilCls.getMethod("compact", IModelNGT.class);
            m.invoke(null, model);
        } catch (Throwable ignored) {
        }
    }

    /**
     * モデルからローカルハイトマップを動的生成する。
     */
    private static VehicleModelHeightMap computeFromModel(String modelName, IModelNGT model, jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig cfg) {
        if (!(model instanceof PolygonModel)) {
            return null;
        }

        PolygonModel polygonModel = (PolygonModel) model;
        if (polygonModel.getGroupObjects() == null || polygonModel.getGroupObjects().isEmpty()) {
            ensureModelLoaded(model);
        }
        if (polygonModel.getGroupObjects() == null || polygonModel.getGroupObjects().isEmpty()) {
            net.suzumiya.crosstie.CrossTie.LOGGER.warn("[CrossTie] モデルポリゴン面が空のためハイトマップを生成できませんでした: " + modelName);
            return null;
        }

        java.util.Set<String> configExcludedNames = new java.util.HashSet<>();
        if (cfg != null) {
            collectConfigParts(configExcludedNames, cfg.pantograph_front);
            collectConfigParts(configExcludedNames, cfg.pantograph_back);
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        // 全頂点からバウンディングXZを走査
        for (GroupObject group : polygonModel.getGroupObjects()) {
            if (group.faces == null) continue;
            for (Face face : group.faces) {
                if (face.vertices == null) continue;
                for (Vertex v : face.vertices) {
                    float vx = v.getX();
                    float vz = v.getZ();
                    if (vx < minX) minX = vx;
                    if (vx > maxX) maxX = vx;
                    if (vz < minZ) minZ = vz;
                    if (vz > maxZ) maxZ = vz;
                }
            }
        }

        if (minX >= maxX || minZ >= maxZ) {
            return null;
        }

        // マージンを追加
        minX -= CELL_SIZE;
        maxX += CELL_SIZE;
        minZ -= CELL_SIZE;
        maxZ += CELL_SIZE;

        int cellsX = (int) Math.ceil((maxX - minX) / CELL_SIZE) + 1;
        int cellsZ = (int) Math.ceil((maxZ - minZ) / CELL_SIZE) + 1;

        if (cellsX <= 0 || cellsZ <= 0 || cellsX > 2000 || cellsZ > 2000) {
            return null;
        }

        int totalCells = cellsX * cellsZ;
        float[] topHeight = new float[totalCells];
        float[] subHeight = new float[totalCells];
        Arrays.fill(topHeight, NO_HEIGHT);
        Arrays.fill(subHeight, NO_HEIGHT);

        boolean foundFace = false;

        // 1. 各Faceを多重ハイトマップ（最高面 topHeight とその直下の面 subHeight）にラスタライズ
        for (GroupObject group : polygonModel.getGroupObjects()) {
            if (group.faces == null) continue;
            // 明示的なパンタグラフパーツ名定義がある場合は事前フィルタ
            boolean isKnownExcluded = isKnownExcludedPart(group.name, configExcludedNames);

            for (Face face : group.faces) {
                if (face.vertices == null || face.vertices.length < 3) continue;

                foundFace = true;
                Vertex v0 = face.vertices[0];
                Vertex v1 = face.vertices[1];
                Vertex v2 = face.vertices[2];

                rasterizeTriangle(v0, v1, v2, minX, minZ, cellsX, cellsZ, topHeight, subHeight, isKnownExcluded);

                if (face.vertices.length >= 4) {
                    Vertex v3 = face.vertices[3];
                    rasterizeTriangle(v0, v2, v3, minX, minZ, cellsX, cellsZ, topHeight, subHeight, isKnownExcluded);
                }
            }
        }

        // 2. 動的幾何学フィルタリング:
        // クーラー等の「一定の面積を持つ突起物」は topHeight を採用
        // パンタグラフ等の「細い線・骨組み・孤立突起物」は subHeight（直下の屋根面）を採用
        float[] finalHeight = new float[totalCells];
        Arrays.fill(finalHeight, NO_HEIGHT);

        for (int gz = 0; gz < cellsZ; gz++) {
            for (int gx = 0; gx < cellsX; gx++) {
                int idx = gz * cellsX + gx;
                float top = topHeight[idx];
                if (top == NO_HEIGHT) continue;

                float sub = subHeight[idx];
                if (sub == NO_HEIGHT || (top - sub) < 0.15f) {
                    // 屋根そのもの、または突起と屋根に段差がない
                    finalHeight[idx] = top;
                    continue;
                }

                // 屋根の上に突起物がある場合: 周囲の連続天面面積（Support Area）をチェック
                int continuousNeighborCount = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    int nz = gz + dz;
                    if (nz < 0 || nz >= cellsZ) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = gx + dx;
                        if (nx < 0 || nx >= cellsX) continue;

                        int nIdx = nz * cellsX + nx;
                        float nTop = topHeight[nIdx];
                        // ほぼ同等（高さ誤差18cm以内）の天面が隣接しているか
                        if (nTop != NO_HEIGHT && Math.abs(nTop - top) <= 0.18f) {
                            continuousNeighborCount++;
                        }
                    }
                }

                // 8近傍中2つ以上のセルで天面が連続していれば、クーラーやベンチレーター等のまとまった面
                if (continuousNeighborCount >= 2) {
                    finalHeight[idx] = top; // クーラーの天面に雨・雪が当たる
                } else {
                    finalHeight[idx] = sub; // 細い突起物は突き抜けて屋根に当たる
                }
            }
        }

        // 3. ホールフィリング（穴埋め処理）:
        // 細いポリゴン境界やキールライン（中央線）の離散サンプリング抜けで生じた NO_HEIGHT セルを
        // 左右および前後の有効な屋根面から線形補間して完全に塞ぐ
        float roofMinX = Float.POSITIVE_INFINITY;
        float roofMaxX = Float.NEGATIVE_INFINITY;
        float roofMinZ = Float.POSITIVE_INFINITY;
        float roofMaxZ = Float.NEGATIVE_INFINITY;

        // まず屋根の実際の範囲を特定
        for (int gz = 0; gz < cellsZ; gz++) {
            float pz = minZ + gz * CELL_SIZE;
            for (int gx = 0; gx < cellsX; gx++) {
                int idx = gz * cellsX + gx;
                if (finalHeight[idx] != NO_HEIGHT) {
                    float px = minX + gx * CELL_SIZE;
                    if (px < roofMinX) roofMinX = px;
                    if (px > roofMaxX) roofMaxX = px;
                    if (pz < roofMinZ) roofMinZ = pz;
                    if (pz > roofMaxZ) roofMaxZ = pz;
                }
            }
        }

        // 左右（X方向）の隙間埋め
        for (int gz = 0; gz < cellsZ; gz++) {
            int firstValidGx = -1;
            int lastValidGx = -1;
            for (int gx = 0; gx < cellsX; gx++) {
                if (finalHeight[gz * cellsX + gx] != NO_HEIGHT) {
                    if (firstValidGx == -1) firstValidGx = gx;
                    lastValidGx = gx;
                }
            }

            if (firstValidGx != -1 && lastValidGx > firstValidGx) {
                for (int gx = firstValidGx + 1; gx < lastValidGx; gx++) {
                    int idx = gz * cellsX + gx;
                    if (finalHeight[idx] == NO_HEIGHT) {
                        int leftGx = gx - 1;
                        while (leftGx >= firstValidGx && finalHeight[gz * cellsX + leftGx] == NO_HEIGHT) {
                            leftGx--;
                        }
                        int rightGx = gx + 1;
                        while (rightGx <= lastValidGx && finalHeight[gz * cellsX + rightGx] == NO_HEIGHT) {
                            rightGx++;
                        }

                        if (leftGx >= firstValidGx && rightGx <= lastValidGx) {
                            float leftH = finalHeight[gz * cellsX + leftGx];
                            float rightH = finalHeight[gz * cellsX + rightGx];
                            float t = (float) (gx - leftGx) / (float) (rightGx - leftGx);
                            finalHeight[idx] = leftH + t * (rightH - leftH);
                        }
                    }
                }
            }
        }

        // 前後（Z方向）の隙間埋め
        for (int gx = 0; gx < cellsX; gx++) {
            int firstValidGz = -1;
            int lastValidGz = -1;
            for (int gz = 0; gz < cellsZ; gz++) {
                if (finalHeight[gz * cellsX + gx] != NO_HEIGHT) {
                    if (firstValidGz == -1) firstValidGz = gz;
                    lastValidGz = gz;
                }
            }

            if (firstValidGz != -1 && lastValidGz > firstValidGz) {
                for (int gz = firstValidGz + 1; gz < lastValidGz; gz++) {
                    int idx = gz * cellsX + gx;
                    if (finalHeight[idx] == NO_HEIGHT) {
                        int prevGz = gz - 1;
                        while (prevGz >= firstValidGz && finalHeight[prevGz * cellsX + gx] == NO_HEIGHT) {
                            prevGz--;
                        }
                        int nextGz = gz + 1;
                        while (nextGz <= lastValidGz && finalHeight[nextGz * cellsX + gx] == NO_HEIGHT) {
                            nextGz++;
                        }

                        if (prevGz >= firstValidGz && nextGz <= lastValidGz) {
                            float prevH = finalHeight[prevGz * cellsX + gx];
                            float nextH = finalHeight[nextGz * cellsX + gx];
                            float t = (float) (gz - prevGz) / (float) (nextGz - prevGz);
                            finalHeight[idx] = prevH + t * (nextH - prevH);
                        }
                    }
                }
            }
        }

        // 4. nominalRoofY（基準屋根高さ）の計算
        float heightSum = 0;
        int validCellCount = 0;
        for (int i = 0; i < totalCells; i++) {
            if (finalHeight[i] != NO_HEIGHT) {
                heightSum += finalHeight[i];
                validCellCount++;
            }
        }
        float nominalRoofY = validCellCount > 0 ? (heightSum / validCellCount) : 3.3f;

        float spanX = maxX - minX;
        float spanZ = maxZ - minZ;
        net.suzumiya.crosstie.CrossTie.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[CrossTie] 車両モデルハイトマップ生成完了: %s (幅: %.2fm, 長さ: %.2fm, 屋根基準高: %.2fm, グリッド: %dx%d=%dセル)",
                modelName, spanX, spanZ, nominalRoofY, cellsX, cellsZ, totalCells));

        return new VehicleModelHeightMap(minX, maxX, minZ, maxZ, roofMinX, roofMaxX, roofMinZ, roofMaxZ, nominalRoofY, cellsX, cellsZ, finalHeight, foundFace);
    }

    /**
     * 3頂点からなる三角形を多層グリッド上にラスタライズする。
     */
    private static void rasterizeTriangle(Vertex v0, Vertex v1, Vertex v2,
                                         float minX, float minZ, int cellsX, int cellsZ,
                                         float[] topHeight, float[] subHeight, boolean isKnownExcluded) {
        float x0 = v0.getX(), y0 = v0.getY(), z0 = v0.getZ();
        float x1 = v1.getX(), y1 = v1.getY(), z1 = v1.getZ();
        float x2 = v2.getX(), y2 = v2.getY(), z2 = v2.getZ();

        // 平面法線: N = (v1 - v0) x (v2 - v0)
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float triMinX = Math.min(x0, Math.min(x1, x2));
        float triMaxX = Math.max(x0, Math.max(x1, x2));
        float triMinZ = Math.min(z0, Math.min(z1, z2));
        float triMaxZ = Math.max(z0, Math.max(z1, z2));

        int gxStart = Math.max(0, (int) Math.floor((triMinX - minX) / CELL_SIZE));
        int gxEnd = Math.min(cellsX - 1, (int) Math.ceil((triMaxX - minX) / CELL_SIZE));
        int gzStart = Math.max(0, (int) Math.floor((triMinZ - minZ) / CELL_SIZE));
        int gzEnd = Math.min(cellsZ - 1, (int) Math.ceil((triMaxZ - minZ) / CELL_SIZE));

        boolean isNearlyVertical = Math.abs(ny) < 1e-4f;
        float maxTriY = Math.max(y0, Math.max(y1, y2));

        for (int gz = gzStart; gz <= gzEnd; gz++) {
            float pz = minZ + gz * CELL_SIZE;
            for (int gx = gxStart; gx <= gxEnd; gx++) {
                float px = minX + gx * CELL_SIZE;

                // 巻き順（CW/CCW）に依存しない外積による内外判定
                float c0 = (px - x0) * (z1 - z0) - (pz - z0) * (x1 - x0);
                float c1 = (px - x1) * (z2 - z1) - (pz - z1) * (x2 - x1);
                float c2 = (px - x2) * (z0 - z2) - (pz - z2) * (x0 - x2);

                boolean hasNeg = (c0 < -1e-3f) || (c1 < -1e-3f) || (c2 < -1e-3f);
                boolean hasPos = (c0 > 1e-3f) || (c1 > 1e-3f) || (c2 > 1e-3f);

                if (!(hasNeg && hasPos)) {
                    float y = !isNearlyVertical ? (y0 - (nx * (px - x0) + nz * (pz - z0)) / ny) : maxTriY;
                    int idx = gz * cellsX + gx;

                    if (isKnownExcluded) {
                        continue;
                    }

                    float currentTop = topHeight[idx];
                    if (currentTop == NO_HEIGHT) {
                        topHeight[idx] = y;
                    } else if (y > currentTop) {
                        if ((y - currentTop) >= 0.15f) {
                            subHeight[idx] = Math.max(subHeight[idx], currentTop);
                        }
                        topHeight[idx] = y;
                    } else if ((currentTop - y) >= 0.15f) {
                        if (y > subHeight[idx]) {
                            subHeight[idx] = y;
                        }
                    }
                }
            }
        }
    }

    /**
     * ローカル座標 (lx, lz) における面の最高Yを取得する。
     * 面が存在しない箇所（車体外や開口部）は NO_HEIGHT を返す。
     */
    public float getHeight(float lx, float lz) {
        if (!hasFaces) return NO_HEIGHT;

        int gx = Math.round((lx - minX) / CELL_SIZE);
        int gz = Math.round((lz - minZ) / CELL_SIZE);

        if (gx < 0 || gx >= cellsX || gz < 0 || gz >= cellsZ) {
            return NO_HEIGHT;
        }

        float h = heightMap[gz * cellsX + gx];
        if (h != NO_HEIGHT) {
            return h;
        }

        // 周囲3x3の最近傍探索フォールバック（微小なサンプリング欠けの補間）
        float sum = 0;
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) {
            int nz = gz + dz;
            if (nz < 0 || nz >= cellsZ) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int nx = gx + dx;
                if (nx < 0 || nx >= cellsX) continue;
                float nh = heightMap[nz * cellsX + nx];
                if (nh != NO_HEIGHT) {
                    sum += nh;
                    count++;
                }
            }
        }

        if (count > 0) {
            return sum / count;
        }

        return NO_HEIGHT;
    }

    private static void collectConfigParts(java.util.Set<String> set, jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts[] partsArray) {
        if (partsArray == null) return;
        for (jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts part : partsArray) {
            if (part == null) continue;
            if (part.objects != null) {
                for (String objName : part.objects) {
                    if (objName != null) {
                        set.add(objName.toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            if (part.childParts != null) {
                collectConfigParts(set, part.childParts);
            }
        }
    }

    private static boolean isKnownExcludedPart(String partName, java.util.Set<String> configExcludedNames) {
        if (partName == null) return false;
        String lower = partName.toLowerCase(java.util.Locale.ROOT).trim();

        if (configExcludedNames != null && configExcludedNames.contains(lower)) {
            return true;
        }

        if (lower.contains("panto") || lower.contains("panta") || lower.matches(".*(ps|pt)\\d+.*") ||
            lower.contains("antenna") || lower.contains("anten") || lower.contains("musen") ||
            lower.contains("pole") || lower.contains("trolley")) {
            return true;
        }

        return false;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
