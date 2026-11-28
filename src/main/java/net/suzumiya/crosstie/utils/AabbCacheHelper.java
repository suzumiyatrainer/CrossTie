package net.suzumiya.crosstie.utils;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import net.minecraft.util.AxisAlignedBB;

/**
 * 3-6: Arvo 法（3x3 回転行列の絶対値）を用いた 0 アロケーション OBB → AABB 算出ヘルパー。
 *
 * <p>{@code RenderOrnament} / {@code RenderMachine} の実際の GL 変換順序
 * ({@code Rz(roll) * Rx(pitch) * Ry(yaw)} および {@code rotateByMetadata}) に厳密一致させ、
 * かつ {@code renderAABB} と {@code PolygonModel.sizeBox} の和集合＋安全マージン {@code ±0.25} を取ることで
 * 画面端ポップアウト・OptiFine {@code ClippingHelperShadow} の影欠けを防止する。</p>
 */
public final class AabbCacheHelper {

    private static final double SAFETY_MARGIN = 0.25D;

    private AabbCacheHelper() {
    }

    /**
     * 未ロード時フォールバック（Angelica の {@code TileEntityRenderBoundsRegistry} が初回に
     * {@code INFINITE} を永続キャッシュする罠を回避するため、常に有限 AABB を返す）。
     */
    public static AxisAlignedBB setFallbackBounds(AxisAlignedBB reuse, int x, int y, int z,
            float offX, float offY, float offZ, float scale) {
        double r = Math.max(4.0D, 4.0D * Math.abs((double) scale));
        double cx = x + 0.5D + offX;
        double cy = y + 0.5D + offY;
        double cz = z + 0.5D + offZ;
        return setOrCreate(reuse, cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    /**
     * {@code cfg.renderAABB} と {@code PolygonModel.sizeBox} の和集合からローカル範囲を算出し、
     * Arvo 法でワールド AABB を {@code reuse} へ書き込む。
     */
    public static AxisAlignedBB computeOrientedBounds(
            AxisAlignedBB reuse,
            double baseX, double baseY, double baseZ,
            float offX, float offY, float offZ,
            float yawDeg, float pitchDeg, float rollDeg,
            float scale,
            float[] renderAABB,
            IModelNGT model,
            boolean rotateByMeta,
            int meta) {

        double minX = 0.0D, minY = 0.0D, minZ = 0.0D;
        double maxX = 1.0D, maxY = 1.0D, maxZ = 1.0D;
        if (renderAABB != null && renderAABB.length >= 6) {
            minX = renderAABB[0];
            minY = renderAABB[1];
            minZ = renderAABB[2];
            maxX = renderAABB[3];
            maxY = renderAABB[4];
            maxZ = renderAABB[5];
        }

        if (model instanceof PolygonModel) {
            float[] sb = ((PolygonModel) model).sizeBox;
            if (sb != null && sb.length >= 6
                    && (sb[0] != 0.0F || sb[1] != 0.0F || sb[2] != 0.0F
                            || sb[3] != 0.0F || sb[4] != 0.0F || sb[5] != 0.0F)) {
                // sizeBox はモデル原点中心なのでブロック中心基準 (+0.5) に揃えて和集合
                if (sb[0] < minX) minX = sb[0];
                if (sb[1] < minY) minY = sb[1];
                if (sb[2] < minZ) minZ = sb[2];
                if (sb[3] > maxX) maxX = sb[3];
                if (sb[4] > maxY) maxY = sb[4];
                if (sb[5] > maxZ) maxZ = sb[5];
            }
        }

        // ローカル中心・半サイズ（scale 反映 + 安全マージン）
        double s = (scale == 0.0F) ? 1.0D : (double) scale;
        double absS = Math.abs(s);
        double lcx = ((minX + maxX) * 0.5D - 0.5D) * s;
        double lcy = ((minY + maxY) * 0.5D - 0.5D) * s;
        double lcz = ((minZ + maxZ) * 0.5D - 0.5D) * s;
        double ex = Math.abs(maxX - minX) * 0.5D * absS + SAFETY_MARGIN;
        double ey = Math.abs(maxY - minY) * 0.5D * absS + SAFETY_MARGIN;
        double ez = Math.abs(maxZ - minZ) * 0.5D * absS + SAFETY_MARGIN;

        float effYaw = (rotateByMeta && meta == 0) ? -yawDeg : yawDeg;
        double ry = NGTMath.toRadians(effYaw);
        double rx = NGTMath.toRadians(pitchDeg);
        double rz = NGTMath.toRadians(rollDeg);

        double cy = Math.cos(ry), sy = Math.sin(ry);
        double cx = Math.cos(rx), sx = Math.sin(rx);
        double cz = Math.cos(rz), sz = Math.sin(rz);

        // R = Rz(roll) * Rx(pitch) * Ry(yaw) (OpenGL glRotatef 順)
        double r00 = cz * cy - sz * sx * sy;
        double r01 = -sz * cx;
        double r02 = cz * sy + sz * sx * cy;

        double r10 = sz * cy + cz * sx * sy;
        double r11 = cz * cx;
        double r12 = sz * sy - cz * sx * cy;

        double r20 = -cx * sy;
        double r21 = sx;
        double r22 = cx * cy;

        // Arvo 法: E_i = |R_i0|*ex + |R_i1|*ey + |R_i2|*ez
        double wcx = r00 * lcx + r01 * lcy + r02 * lcz;
        double wcy = r10 * lcx + r11 * lcy + r12 * lcz;
        double wcz = r20 * lcx + r21 * lcy + r22 * lcz;

        double wex = Math.abs(r00) * ex + Math.abs(r01) * ey + Math.abs(r02) * ez;
        double wey = Math.abs(r10) * ex + Math.abs(r11) * ey + Math.abs(r12) * ez;
        double wez = Math.abs(r20) * ex + Math.abs(r21) * ey + Math.abs(r22) * ez;

        if (rotateByMeta) {
            // RenderMachine のメタデータ 90度/180度回転に対応（軸入替のみ）
            double t;
            switch (meta) {
                case 0: // X 180度
                    wcy = -wcy;
                    wcz = -wcz;
                    break;
                case 2: // X -90度, Y 180度
                    t = wcy; wcy = -wcz; wcz = t;
                    wcx = -wcx; wcz = -wcz;
                    t = wey; wey = wez; wez = t;
                    break;
                case 3: // X 90度
                    t = wcy; wcy = -wcz; wcz = t;
                    t = wey; wey = wez; wez = t;
                    break;
                case 4: // Z 90度, Y -90度
                case 5: // Z -90度, Y 90度
                    double maxHoriz = Math.max(wex, Math.max(wey, wez));
                    wex = maxHoriz;
                    wey = maxHoriz;
                    wez = maxHoriz;
                    double rad = Math.sqrt(wcx * wcx + wcy * wcy + wcz * wcz);
                    wcx = 0.0D; wcy = 0.0D; wcz = 0.0D;
                    wex += rad; wey += rad; wez += rad;
                    break;
                default:
                    break;
            }
        }

        double fx = baseX + offX + wcx;
        double fy = baseY + offY + wcy;
        double fz = baseZ + offZ + wcz;
        return setOrCreate(reuse, fx - wex, fy - wey, fz - wez, fx + wex, fy + wey, fz + wez);
    }

    public static AxisAlignedBB setOrCreate(AxisAlignedBB reuse,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (reuse != null) {
            reuse.setBounds(minX, minY, minZ, maxX, maxY, maxZ);
            return reuse;
        }
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
