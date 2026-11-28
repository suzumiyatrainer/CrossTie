package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.renderer.NGTTessellator;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.renderer.model.TextureCoordinate;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.Connection.ConnectionType;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.modelpack.cfg.WireConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetWireClient;
import jp.ngt.rtm.render.RenderPass;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.suzumiya.crosstie.accessors.rtm.IWireDisplayListHolder;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Phase 5-3: 架線・桁橋 ({@code WirePartsRenderer}, {@code useScript == false}) の 1
 * 本 1 単一 VBO DisplayList キャッシュ。
 *
 * <p>
 * 各セグメントの平行移動・回転・スケールを CPU 側で行列変換し、セグメントごとのワールド明るさを
 * {@link NGTTessellator#setBrightness(int)} として頂点属性に焼き込むことで、 Angelica 環境でも
 * {@code drawBarrier()} 0 回の単一 VBO 描画、OptiFine 環境でも単一ネイティブ DisplayList 描画を実現する。
 * </p>
 * <p>
 * {@code bindTexture} は呼び出し元（{@code WirePartsRenderer.renderWire}）で DisplayList
 * の外側にて実行されるため、 OptiFine シェーダーの法線・スペキュラテクスチャ ({@code MultiTexID}) も 100%
 * 正常動作する。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class WireDisplayListCache {

    private static final int BLOCK_LIGHT_CHECK_INTERVAL = 60;

    private static final class Entry {
        Connection connection;
        int passId;
        ModelSetWireClient modelSet;
        int txBits;
        int tyBits;
        int tzBits;
        int skylightSubtracted;
        int blockLightHash;
        long lastLightCheckWorldTime;
        DisplayList glList;
        boolean empty;
    }

    private Entry[] entries = new Entry[4];
    private int count = 0;

    /**
     * Phase 5.5: 標準スクリプト架線（{@code RenderBasicWire.js} / {@code RenderSimpleCatenary.js}）用の
     * ネイティブ単一 VBO DisplayList キャッシュ描画。
     *
     * @param scriptKind 1 = RenderBasicWire, 2 = RenderSimpleCatenary
     */
    public static boolean tryRenderStandardScriptWire(
            TileEntityElectricalWiring tileEntity,
            Connection connection,
            Vec3 target,
            int passId,
            int scriptKind) {
        if (tileEntity == null || connection == null || target == null || GLHelper.isCompiling()) {
            return false;
        }
        if (connection.type != ConnectionType.WIRE || !connection.isRoot) {
            return false;
        }
        if (tileEntity instanceof TileEntityDummyEW || !(tileEntity instanceof IWireDisplayListHolder)) {
            return false;
        }
        World world = tileEntity.getWorldObj();
        if (world == null || world.getTileEntity(connection.x, connection.y, connection.z) == null) {
            return false;
        }
        Object rawModelSet = connection.getModelSet();
        if (!(rawModelSet instanceof ModelSetWireClient)) {
            return false;
        }
        ModelSetWireClient modelSet = (ModelSetWireClient) rawModelSet;
        if (modelSet.isDummy()) {
            return false;
        }

        IWireDisplayListHolder holder = (IWireDisplayListHolder) tileEntity;
        WireDisplayListCache cache = (WireDisplayListCache) holder.crosstie$getWireCache();
        if (cache == null) {
            cache = new WireDisplayListCache();
            holder.crosstie$setWireCache(cache);
        }

        Entry entry = cache.findOrCreate(connection, passId);
        int txBits = Float.floatToIntBits((float) target.getX());
        int tyBits = Float.floatToIntBits((float) target.getY());
        int tzBits = Float.floatToIntBits((float) target.getZ());

        boolean needsCompile = (!entry.empty && !GLHelper.isValid(entry.glList))
                || entry.modelSet != modelSet
                || entry.txBits != txBits
                || entry.tyBits != tyBits
                || entry.tzBits != tzBits;

        if (needsCompile) {
            DisplayList dl = GLHelper.generateGLList(entry.glList);
            entry.glList = dl;
            GLHelper.startCompile(dl);
            try {
                if (scriptKind == 1) {
                    tessellateBasicWire(target);
                } else {
                    tessellateSimpleCatenary(target);
                }
            } finally {
                GLHelper.endCompile();
            }
            entry.modelSet = modelSet;
            entry.txBits = txBits;
            entry.tyBits = tyBits;
            entry.tzBits = tzBits;
            entry.empty = false;
        }

        if (GLHelper.isValid(entry.glList)) {
            if (scriptKind == 1) {
                // GL_TEXTURE_2D 切替は必ず DisplayList 外側（OptiFine Shaders 安全）
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GLHelper.callList(entry.glList);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GLHelper.callList(entry.glList);
            }
        }
        return true;
    }

    private static void tessellateBasicWire(Vec3 vec) {
        double x = vec.getX();
        double y = vec.getY();
        double z = vec.getZ();
        double length = Math.sqrt(x * x + z * z);
        double x1 = (length > 1.0E-6D) ? (x / length) : 0.0D;
        double z1 = (length > 1.0E-6D) ? (z / length) : 0.0D;
        double split = 16.0D;

        NGTTessellator tess = NGTTessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
        tess.setColorOpaque_I(0);
        for (int j = 0; j <= 16; ++j) {
            double ft = j / split;
            double f2 = (j - 8) / split;
            double fh = (f2 * f2 - 0.25D) * 1.5D;
            tess.addVertex((float) (x * ft - 0.025D * z1), (float) (y * ft + fh), (float) (z * ft + 0.025D * x1));
            tess.addVertex((float) (x * ft + 0.025D * z1), (float) (y * ft + fh), (float) (z * ft - 0.025D * x1));
        }
        tess.draw();

        tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
        tess.setColorOpaque_I(0);
        for (int j = 0; j <= 16; ++j) {
            double ft = j / split;
            double f2 = (j - 8) / split;
            double fh = (f2 * f2 - 0.25D) * 1.5D;
            tess.addVertex((float) (x * ft), (float) (y * ft + fh + 0.025D), (float) (z * ft));
            tess.addVertex((float) (x * ft), (float) (y * ft + fh - 0.025D), (float) (z * ft));
        }
        tess.draw();
    }

    private static void tessellateSimpleCatenary(Vec3 vec) {
        double x = vec.getX();
        double y = vec.getY();
        double z = vec.getZ();
        NGTTessellator tess = NGTTessellator.instance;
        tess.startDrawingQuads();
        double split = 8.0D;
        for (int i = 0; i < 8; ++i) {
            double ft = i / split;
            double ft2 = (i - 4) / split;
            double fh = (ft2 * ft2 - 0.25D) * 1.2D;
            double ftNext = (i + 1) / split;
            double ftn2 = ((i + 1) - 4) / split;
            double fhn = (ftn2 * ftn2 - 0.25D) * 1.2D;

            tess.addVertexWithUV((float) (x * ftNext), (float) (y * ftNext - 0.8125D), (float) (z * ftNext), 1.0F, 1.0F);
            tess.addVertexWithUV((float) (x * ftNext), (float) (y * ftNext + fhn), (float) (z * ftNext), 1.0F, 0.0F);
            tess.addVertexWithUV((float) (x * ft), (float) (y * ft + fh), (float) (z * ft), 0.0F, 0.0F);
            tess.addVertexWithUV((float) (x * ft), (float) (y * ft - 0.8125D), (float) (z * ft), 0.0F, 1.0F);
        }
        tess.draw();
    }

    public static boolean tryRenderCached(TileEntityElectricalWiring tileEntity, Connection connection, Vec3 target,
            int passId) {
        if (tileEntity == null || connection == null || target == null) {
            return false;
        }
        if (GLHelper.isCompiling()) {
            return false;
        }
        if (connection.type != ConnectionType.WIRE || !connection.isRoot) {
            return false;
        }
        if (tileEntity instanceof TileEntityDummyEW) {
            return false;
        }
        if (!(tileEntity instanceof IWireDisplayListHolder)) {
            return false;
        }
        World world = tileEntity.getWorldObj();
        if (world == null) {
            return false;
        }
        // 接続先チャンク未ロード時は (0,0,0) を誤キャッシュしないよう除外
        if (world.getTileEntity(connection.x, connection.y, connection.z) == null) {
            return false;
        }

        Object rawModelSet = connection.getModelSet();
        if (!(rawModelSet instanceof ModelSetWireClient)) {
            return false;
        }
        ModelSetWireClient modelSet = (ModelSetWireClient) rawModelSet;
        if (modelSet.isDummy() || modelSet.modelObj == null || modelSet.modelObj.model == null) {
            return false;
        }
        IModelNGT model = modelSet.modelObj.model;
        List<GroupObject> groups = model.getGroupObjects();
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        int drawMode = model.getDrawMode();
        if (drawMode != GL11.GL_TRIANGLES && drawMode != GL11.GL_QUADS) {
            return false;
        }

        IWireDisplayListHolder holder = (IWireDisplayListHolder) tileEntity;
        WireDisplayListCache cache = (WireDisplayListCache) holder.crosstie$getWireCache();
        if (cache == null) {
            cache = new WireDisplayListCache();
            holder.crosstie$setWireCache(cache);
        }

        return cache.renderOrCompile(tileEntity, world, connection, modelSet, model, groups, drawMode, target, passId);
    }

    public void releaseAll() {
        for (int i = 0; i < this.count; ++i) {
            Entry e = this.entries[i];
            if (e != null && e.glList != null) {
                GLHelper.deleteGLList(e.glList);
                e.glList = null;
            }
            this.entries[i] = null;
        }
        this.count = 0;
    }

    private Entry findOrCreate(Connection connection, int passId) {
        for (int i = 0; i < this.count; ++i) {
            Entry e = this.entries[i];
            if (e.connection == connection && e.passId == passId) {
                return e;
            }
        }
        if (this.count == this.entries.length) {
            Entry[] next = new Entry[this.entries.length << 1];
            System.arraycopy(this.entries, 0, next, 0, this.count);
            this.entries = next;
        }
        Entry created = new Entry();
        created.connection = connection;
        created.passId = passId;
        this.entries[this.count++] = created;
        return created;
    }

    private boolean renderOrCompile(TileEntityElectricalWiring tileEntity, World world, Connection connection,
            ModelSetWireClient modelSet, IModelNGT model, List<GroupObject> groups, int drawMode, Vec3 target,
            int passId) {
        Entry entry = this.findOrCreate(connection, passId);

        int txBits = Float.floatToIntBits((float) target.getX());
        int tyBits = Float.floatToIntBits((float) target.getY());
        int tzBits = Float.floatToIntBits((float) target.getZ());
        int skySub = world.skylightSubtracted;
        boolean useLight = (passId != RenderPass.LIGHT.id);

        boolean needsCompile = (!entry.empty && !GLHelper.isValid(entry.glList)) || entry.modelSet != modelSet
                || entry.txBits != txBits || entry.tyBits != tyBits || entry.tzBits != tzBits
                || (useLight && entry.skylightSubtracted != skySub);

        long worldTime = world.getTotalWorldTime();
        if (!needsCompile && useLight
                && Math.abs(worldTime - entry.lastLightCheckWorldTime) >= BLOCK_LIGHT_CHECK_INTERVAL) {
            entry.lastLightCheckWorldTime = worldTime;
            int hash = sampleThreePointLightHash(world, tileEntity, target);
            if (hash != entry.blockLightHash) {
                entry.blockLightHash = hash;
                needsCompile = true;
            }
        }

        if (needsCompile) {
            WireConfig cfg = modelSet.getConfig();
            if (cfg == null || cfg.sectionLength <= 1.0E-4F) {
                return false;
            }
            DisplayList dl = GLHelper.generateGLList(entry.glList);
            entry.glList = dl;
            boolean emitted = false;
            GLHelper.startCompile(dl);
            try {
                emitted = tessellateWireSingleVbo(tileEntity, world, cfg, groups, drawMode, target, useLight);
            } finally {
                GLHelper.endCompile();
            }
            entry.modelSet = modelSet;
            entry.txBits = txBits;
            entry.tyBits = tyBits;
            entry.tzBits = tzBits;
            entry.skylightSubtracted = skySub;
            entry.lastLightCheckWorldTime = worldTime;
            entry.blockLightHash = useLight ? sampleThreePointLightHash(world, tileEntity, target) : 0;
            entry.empty = !emitted;
            if (!emitted) {
                GLHelper.deleteGLList(dl);
                entry.glList = null;
            }
        }

        if (!entry.empty && GLHelper.isValid(entry.glList)) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GLHelper.callList(entry.glList);
        }
        return true;
    }

    private static int sampleThreePointLightHash(World world, TileEntityElectricalWiring tileEntity, Vec3 target) {
        Vec3 posMain = tileEntity.getWirePos();
        double sx = tileEntity.xCoord + 0.5D + (posMain != null ? posMain.getX() : 0.0D);
        double sy = tileEntity.yCoord + 0.5D + (posMain != null ? posMain.getY() : 0.0D);
        double sz = tileEntity.zCoord + 0.5D + (posMain != null ? posMain.getZ() : 0.0D);
        int b0 = getBlockLight(world, sx, sy, sz);
        int b1 = getBlockLight(world, sx + target.getX() * 0.5D, sy + target.getY() * 0.5D, sz + target.getZ() * 0.5D);
        int b2 = getBlockLight(world, sx + target.getX(), sy + target.getY(), sz + target.getZ());
        return (b0 * 31 + b1) * 31 + b2;
    }

    private static int getBlockLight(World world, double x, double y, double z) {
        int bx = MathHelper.floor_double(x);
        int by = MathHelper.floor_double(y);
        int bz = MathHelper.floor_double(z);
        if (by < 0) {
            by = 0;
        }
        if (world.blockExists(bx, by, bz)) {
            return world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);
        }
        return 0;
    }

    private static boolean tessellateWireSingleVbo(TileEntityElectricalWiring tileEntity, World world, WireConfig cfg,
            List<GroupObject> groups, int drawMode, Vec3 target, boolean useLight) {
        double lx = Math.sqrt(target.getX() * target.getX() + target.getZ() * target.getZ());
        boolean straight = (cfg.deflectionCoefficient <= 0.0F || lx == 0.0D);

        Vec3 posMain = tileEntity.getWirePos();
        double startX = tileEntity.xCoord + 0.5D + (posMain != null ? posMain.getX() : 0.0D);
        double startY = tileEntity.yCoord + 0.5D + (posMain != null ? posMain.getY() : 0.0D);
        double startZ = tileEntity.zCoord + 0.5D + (posMain != null ? posMain.getZ() : 0.0D);

        NGTTessellator tess = NGTTessellator.instance;
        tess.startDrawing(drawMode);
        tess.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);
        boolean emitted = false;

        if (straight) {
            double length = target.length();
            int split = MathHelper.floor_double(length / cfg.sectionLength);
            if (split > 0) {
                float scaleY = (float) ((length / (double) split) / cfg.sectionLength);
                float yawRad = (float) Math.toRadians(target.getYaw() + 180.0F);
                float pitchRad = (float) Math.toRadians(target.getPitch() - 90.0F);
                float cy = MathHelper.cos(yawRad);
                float sy = MathHelper.sin(yawRad);
                float cp = MathHelper.cos(pitchRad);
                float sp = MathHelper.sin(pitchRad);

                // M = Ry(yaw) * Rx(pitch)
                // 列 0 (X 軸): (cy, 0, -sy)
                // 列 1 (Y 軸): (sy*sp, cp, cy*sp)
                // 列 2 (Z 軸): (sy*cp, -sp, cy*cp)
                float m00 = cy, m01 = sy * sp, m02 = sy * cp;
                float m10 = 0.0F, m11 = cp, m12 = -sp;
                float m20 = -sy, m21 = cy * sp, m22 = cy * cp;

                float s01 = m01 * scaleY;
                float s11 = m11 * scaleY;
                float s21 = m21 * scaleY;

                double invSplit = 1.0D / (double) split;
                double tX = target.getX();
                double tY = target.getY();
                double tZ = target.getZ();

                for (int i = 0; i < split; ++i) {
                    if (useLight) {
                        double t = ((double) i + 0.5D) * invSplit;
                        tess.setBrightness(getBlockLight(world, startX + tX * t, startY + tY * t, startZ + tZ * t));
                    }
                    float yShift = (float) i * cfg.sectionLength;
                    float tx = s01 * yShift;
                    float ty = s11 * yShift;
                    float tz = s21 * yShift;

                    if (emitModelTransformed(tess, groups, cfg.smoothing, m00, s01, m02, tx, m10, s11, m12, ty, m20,
                            s21, m22, tz, m00, m01, m02, m10, m11, m12, m20, m21, m22)) {
                        emitted = true;
                    }
                }
            }
        } else {
            float yawRad = (float) Math.toRadians(target.getYaw());
            float cy = MathHelper.cos(yawRad);
            float sy = MathHelper.sin(yawRad);

            float pitch = target.getPitch();
            double ly = target.getY();
            float lc = 1.0F + cfg.lengthCoefficient;
            double alpha = cfg.deflectionCoefficient * NGTMath.cos(pitch) / Math.pow(lc, lx);
            double a = (lx - (ly / (alpha * lx))) * 0.5D;
            double dirX = target.getX() / lx;
            double dirZ = target.getZ() / lx;
            float halfSec = -cfg.sectionLength * 0.5F;

            double x = 0.0D;
            while (x < lx) {
                double y = alpha * ((x * x) - (2.0D * a * x));
                double slope = 2.0D * alpha * (x - a);
                double slopeRad = Math.atan(slope);
                float dx = MathHelper.cos((float) slopeRad) * cfg.sectionLength * 0.99F;
                if (dx <= 1.0E-4F) {
                    dx = cfg.sectionLength * 0.99F;
                }
                double nextX = x + dx;
                double nextY = alpha * ((nextX * nextX) - (2.0D * a * nextX));
                double cX = (x + nextX) * 0.5D;
                double cY = (y + nextY) * 0.5D;
                double difX = nextX - x;
                double difY = nextY - y;
                float pitchCRad = (float) Math.toRadians(-(float) NGTMath.toDegrees(Math.atan2(difY, difX)) + 90.0F);
                float cp = MathHelper.cos(pitchCRad);
                float sp = MathHelper.sin(pitchCRad);

                // M = Ry(yaw) * T(0, cY, cX) * Rx(pitchC + 90) * T(0, halfSec, 0)
                float m00 = cy, m01 = sy * sp, m02 = sy * cp;
                float m10 = 0.0F, m11 = cp, m12 = -sp;
                float m20 = -sy, m21 = cy * sp, m22 = cy * cp;

                float tx = sy * (float) cX + m01 * halfSec;
                float ty = (float) cY + m11 * halfSec;
                float tz = cy * (float) cX + m21 * halfSec;

                if (useLight) {
                    tess.setBrightness(getBlockLight(world, startX + dirX * cX, startY + cY, startZ + dirZ * cX));
                }

                if (emitModelTransformed(tess, groups, cfg.smoothing, m00, m01, m02, tx, m10, m11, m12, ty, m20, m21,
                        m22, tz, m00, m01, m02, m10, m11, m12, m20, m21, m22)) {
                    emitted = true;
                }

                x = nextX;
            }
        }

        tess.draw();
        return emitted;
    }

    @SuppressWarnings("null")
    private static boolean emitModelTransformed(NGTTessellator tess, List<GroupObject> groups, boolean smoothing,
            float m00, float m01, float m02, float tx, float m10, float m11, float m12, float ty, float m20, float m21,
            float m22, float tz, float n00, float n01, float n02, float n10, float n11, float n12, float n20, float n21,
            float n22) {
        boolean emitted = false;
        for (int g = 0, gLen = groups.size(); g < gLen; ++g) {
            GroupObject group = groups.get(g);
            if (group == null || group.faces == null) {
                continue;
            }
            List<Face> faces = group.faces;
            for (int f = 0, fLen = faces.size(); f < fLen; ++f) {
                Face face = faces.get(f);
                if (face == null || face.vertices == null) {
                    continue;
                }
                if (!smoothing && face.faceNormal != null) {
                    float fnx = face.faceNormal.getX();
                    float fny = face.faceNormal.getY();
                    float fnz = face.faceNormal.getZ();
                    tess.setNormal(n00 * fnx + n01 * fny + n02 * fnz, n10 * fnx + n11 * fny + n12 * fnz,
                            n20 * fnx + n21 * fny + n22 * fnz);
                }
                Vertex[] verts = face.vertices;
                Vertex[] vNorms = face.vertexNormals;
                TextureCoordinate[] uvs = face.textureCoordinates;
                boolean hasUv = (uvs != null && uvs.length >= verts.length);
                for (int v = 0; v < verts.length; ++v) {
                    if (smoothing && vNorms != null && v < vNorms.length && vNorms[v] != null) {
                        float vnx = vNorms[v].getX();
                        float vny = vNorms[v].getY();
                        float vnz = vNorms[v].getZ();
                        tess.setNormal(n00 * vnx + n01 * vny + n02 * vnz, n10 * vnx + n11 * vny + n12 * vnz,
                                n20 * vnx + n21 * vny + n22 * vnz);
                    }
                    Vertex vert = verts[v];
                    float vx = vert.getX();
                    float vy = vert.getY();
                    float vz = vert.getZ();
                    float wx = m00 * vx + m01 * vy + m02 * vz + tx;
                    float wy = m10 * vx + m11 * vy + m12 * vz + ty;
                    float wz = m20 * vx + m21 * vy + m22 * vz + tz;
                    if (hasUv && uvs[v] != null) {
                        tess.addVertexWithUV(wx, wy, wz, uvs[v].getU(), uvs[v].getV());
                    } else {
                        tess.addVertexWithUV(wx, wy, wz, 0.0F, 0.0F);
                    }
                    emitted = true;
                }
            }
        }
        return emitted;
    }
}
