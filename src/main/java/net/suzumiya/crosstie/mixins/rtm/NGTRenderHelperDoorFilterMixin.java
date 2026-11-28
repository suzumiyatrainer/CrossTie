package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.IRenderer;
import jp.ngt.ngtlib.renderer.NGTRenderHelper;
import jp.ngt.ngtlib.renderer.PolygonRenderer;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.renderer.model.TextureCoordinate;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.renderer.Tessellator;
import net.suzumiya.crosstie.utils.CrossTiePartsRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * 2-4: {@link NGTRenderHelper} の固定ドア重複（残像）フィルタリング ＋ Stream / IntStream 排除最適化。
 *
 * <ul>
 * <li>{@code renderCustomModelEveryParts}: 三重 Stream を通常 {@code for} ループに置換し、
 * 固定ドア重複フィルタを直接呼び出すことでゼロアロケーション化と競合回避を両立。</li>
 * <li>{@code addFace} / {@code addFaceWithMatrix}: 1 ポリゴン毎の
 * {@code IntStream.range} を通常 {@code for} ループ化し、 4x4 行列要素を頂点ループ外へ巻き上げる。</li>
 * </ul>
 */
@Mixin(value = NGTRenderHelper.class, remap = false)
public abstract class NGTRenderHelperDoorFilterMixin {

    /**
     * @author CrossTie
     * @reason 三重 Stream を for ループに置換しつつ、ドア動作中の固定ドアグループフィルタを統合する。
     */
    @SuppressWarnings("null")
    @Overwrite
    public static void renderCustomModelEveryParts(IModelNGT model, byte matId, boolean except, boolean smoothing,
            int mode, String... parts) {
        IRenderer tessellator = PolygonRenderer.INSTANCE;
        tessellator.startDrawing(mode);
        List<GroupObject> list = model.getGroupObjects();
        boolean hasPartsFilter = parts != null && parts.length > 0;

        Object vehicleObj = CrossTiePartsRenderContext.getCurrentVehicle();
        boolean checkDoorFilter = false;
        if (vehicleObj instanceof EntityVehicleBase) {
            EntityVehicleBase<?> vehicle = (EntityVehicleBase<?>) vehicleObj;
            checkDoorFilter = (vehicle.doorMoveL > 0 || vehicle.doorMoveR > 0);
        }

        for (int g = 0, gSize = list.size(); g < gSize; g++) {
            GroupObject group = list.get(g);
            if (group == null) {
                continue;
            }
            if (hasPartsFilter) {
                boolean matched = false;
                String gName = group.name;
                for (int p = 0; p < parts.length; p++) {
                    if (gName != null && gName.equals(parts[p])) {
                        matched = true;
                        break;
                    }
                }
                if (matched == except) {
                    continue;
                }
            }
            if (checkDoorFilter && !crosstie$shouldRenderDoorGroup(group)) {
                continue;
            }

            List<Face> faces = group.faces;
            for (int f = 0, fSize = faces.size(); f < fSize; f++) {
                Face face = faces.get(f);
                if (face.materialId == matId) {
                    NGTRenderHelper.addFace(face, tessellator, smoothing);
                }
            }
        }
        tessellator.draw();
    }

    /**
     * @author CrossTie
     * @reason 毎 Face の IntStream.range アロケーションを排除。
     */
    @Overwrite
    public static void addFace(Face face, Tessellator tessellator, boolean smoothing) {
        if (!smoothing) {
            tessellator.setNormal(face.faceNormal.getX(), face.faceNormal.getY(), face.faceNormal.getZ());
        }

        Vertex[] vertices = face.vertices;
        Vertex[] vNormals = face.vertexNormals;
        TextureCoordinate[] uvs = face.textureCoordinates;
        boolean hasUV = uvs != null && uvs.length > 0;

        for (int i = 0, len = vertices.length; i < len; i++) {
            if (smoothing) {
                Vertex vn = vNormals[i];
                tessellator.setNormal(vn.getX(), vn.getY(), vn.getZ());
            }
            Vertex v = vertices[i];
            if (hasUV) {
                @SuppressWarnings("null")
                TextureCoordinate tc = uvs[i];
                tessellator.addVertexWithUV(v.getX(), v.getY(), v.getZ(), tc.getU(), tc.getV());
            } else {
                tessellator.addVertexWithUV(v.getX(), v.getY(), v.getZ(), 0.0F, 0.0F);
            }
        }
    }

    /**
     * @author CrossTie
     * @reason 毎 Face の IntStream.range アロケーションを排除し、行列要素をループ外へ巻き上げる。
     */
    @Overwrite
    public static void addFaceWithMatrix(Face face, IRenderer tessellator, FloatBuffer matrix, int index,
            boolean smoothing) {
        if (!smoothing) {
            tessellator.setNormal(face.faceNormal.getX(), face.faceNormal.getY(), face.faceNormal.getZ());
        }

        Vertex[] vertices = face.vertices;
        Vertex[] vNormals = face.vertexNormals;
        TextureCoordinate[] uvs = face.textureCoordinates;
        boolean hasUV = uvs != null && uvs.length > 0;
        int len = vertices.length;

        if (matrix == null) {
            for (int i = 0; i < len; i++) {
                if (smoothing) {
                    Vertex vn = vNormals[i];
                    tessellator.setNormal(vn.getX(), vn.getY(), vn.getZ());
                }
                Vertex v = vertices[i];
                if (hasUV) {
                    @SuppressWarnings("null")
                    TextureCoordinate tc = uvs[i];
                    tessellator.addVertexWithUV(v.getX(), v.getY(), v.getZ(), tc.getU(), tc.getV());
                } else {
                    tessellator.addVertexWithUV(v.getX(), v.getY(), v.getZ(), 0.0F, 0.0F);
                }
            }
            return;
        }

        int base = index << 4;
        float m0 = matrix.get(base);
        float m1 = matrix.get(base + 1);
        float m2 = matrix.get(base + 2);
        float m4 = matrix.get(base + 4);
        float m5 = matrix.get(base + 5);
        float m6 = matrix.get(base + 6);
        float m8 = matrix.get(base + 8);
        float m9 = matrix.get(base + 9);
        float m10 = matrix.get(base + 10);
        float m12 = matrix.get(base + 12);
        float m13 = matrix.get(base + 13);
        float m14 = matrix.get(base + 14);

        for (int i = 0; i < len; i++) {
            if (smoothing) {
                Vertex vn = vNormals[i];
                tessellator.setNormal(vn.getX(), vn.getY(), vn.getZ());
            }
            Vertex v = vertices[i];
            float vx = v.getX();
            float vy = v.getY();
            float vz = v.getZ();
            float x0 = vx * m0 + vy * m4 + vz * m8 + m12;
            float y0 = vx * m1 + vy * m5 + vz * m9 + m13;
            float z0 = vx * m2 + vy * m6 + vz * m10 + m14;
            if (hasUV) {
                @SuppressWarnings("null")
                TextureCoordinate tc = uvs[i];
                tessellator.addVertexWithUV(x0, y0, z0, tc.getU(), tc.getV());
            } else {
                tessellator.addVertexWithUV(x0, y0, z0, 0.0F, 0.0F);
            }
        }
    }

    @Unique
    private static boolean crosstie$shouldRenderDoorGroup(GroupObject group) {
        if (group == null || group.name == null) {
            return true;
        }

        String name = group.name.toLowerCase().trim();

        // アニメーションドアは通常描画
        if (name.equals("doorfl") || name.equals("doorfr") || name.equals("doorbl") || name.equals("doorbr")
                || name.equals("doorfl1") || name.equals("doorfr1") || name.equals("doorbl1") || name.equals("doorbr1")
                || name.equals("door_fl") || name.equals("door_fr") || name.equals("door_bl") || name.equals("door_br")
                || name.equals("doorl") || name.equals("doorr") || name.equals("door_l") || name.equals("door_r")
                || name.equals("door1") || name.equals("door2") || name.equals("door3") || name.equals("door4")
                || name.startsWith("doorfl") || name.startsWith("doorfr") || name.startsWith("doorbl")
                || name.startsWith("doorbr")) {
            return true;
        }

        // ドア動作中、固定ドアグループ（doorLa_f_L, door_close等）は非表示
        if (name.contains("doorla_f") || name.contains("door_close") || name.contains("doorclose")
                || name.contains("door_static") || name.contains("doorstatic") || name.contains("door_base")
                || name.contains("door_fix") || name.contains("doorfix") || name.contains("door_c")
                || name.contains("door_bg") || name.contains("door_def") || name.contains("door_closed")
                || name.equals("door_0") || name.equals("door0") || name.contains("door_stop")
                || name.contains("doorstop")) {
            return false;
        }

        return true;
    }
}
