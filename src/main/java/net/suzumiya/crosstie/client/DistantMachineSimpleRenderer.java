package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.ngtlib.util.NGTUtilClient;
import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import jp.ngt.rtm.modelpack.cfg.MachineConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetMachineClient;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.WeakHashMap;

/**
 * Phase 5.5: 16 チャンク（256m）より遠い RTM 機械類 ({@link TileEntityMachineBase}) の超軽量簡略レンダラー。
 *
 * <ul>
 *   <li>TileEntity 内部の変数・状態・Tick は一切変更せず完全維持する。</li>
 *   <li>pass 1（半透明・発光）は描画しない。</li>
 *   <li>pass 0 では JS スクリプト（{@code PartsRenderer}）・パーツ分割・アニメーション・スムージング・複数マテリアルを
 *       すべてバイパスし、{@link ModelSetMachineClient} ごとに 1 つだけ共有する静的 1-VBO DisplayList
 *       （{@code bindTexture} は外側）を 1 回 {@code glCallList} するだけで描画する。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class DistantMachineSimpleRenderer {

    private static final class SimpleEntry {
        DisplayList glList;
        ResourceLocation texture;
        boolean empty;
    }

    private static final WeakHashMap<ModelSetMachineClient, SimpleEntry> CACHE = new WeakHashMap<>();

    private DistantMachineSimpleRenderer() {
    }

    public static boolean renderSimplifiedPass0(
            TileEntityMachineBase tile,
            double x, double y, double z) {
        Object rawSet = tile.getModelSet();
        if (!(rawSet instanceof ModelSetMachineClient)) {
            return false;
        }
        ModelSetMachineClient modelSet = (ModelSetMachineClient) rawSet;
        if (modelSet.isDummy() || modelSet.modelObj == null || modelSet.modelObj.model == null) {
            return true; // ダミーは遠距離で描く必要なし
        }

        SimpleEntry entry = CACHE.get(modelSet);
        if (entry == null) {
            entry = new SimpleEntry();
            CACHE.put(modelSet, entry);
        }

        if (!entry.empty && !GLHelper.isValid(entry.glList)) {
            if (GLHelper.isCompiling()) {
                return false;
            }
            IModelNGT model = modelSet.modelObj.model;
            if (model.getGroupObjects() == null || model.getGroupObjects().isEmpty()) {
                entry.empty = true;
                return true;
            }
            ResourceLocation tex = null;
            TextureSet[] textures = modelSet.modelObj.textures;
            if (textures != null && textures.length > 0 && textures[0] != null && textures[0].material != null) {
                tex = textures[0].material.texture;
            }
            entry.texture = tex;

            DisplayList dl = GLHelper.generateGLList(entry.glList);
            entry.glList = dl;
            GLHelper.startCompile(dl);
            try {
                // スムージング OFF・JS バイパスでモデル全体を単一 DisplayList へ静的に焼き込む
                model.renderAll(false);
            } finally {
                GLHelper.endCompile();
            }
            entry.empty = false;
        }

        if (entry.empty || !GLHelper.isValid(entry.glList)) {
            return true;
        }

        MachineConfig cfg = modelSet.getConfig();

        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glTranslatef((float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F);
        if (cfg.rotateByMetadata) {
            switch (tile.getBlockMetadata()) {
                case 0:
                    GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
                    break;
                case 1:
                    break;
                case 2:
                    GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 3:
                    GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 4:
                    GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
                    break;
                case 5:
                    GL11.glRotatef(-90.0F, 0.0F, 0.0F, 1.0F);
                    break;
            }
        }
        GL11.glTranslatef(0.0F, -0.5F, 0.0F);
        GL11.glTranslatef(tile.getOffsetX(), tile.getOffsetY(), tile.getOffsetZ());

        float scale = tile.getScale();
        if (scale != 1.0F) {
            GL11.glScalef(scale, scale, scale);
        }

        float roll = tile.getRotationRoll();
        if (roll != 0.0F) {
            GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
        }
        float pitch = tile.getRotationPitch();
        if (pitch != 0.0F) {
            GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
        }
        float yaw = tile.getRotationYaw();
        if (cfg.rotateByMetadata && tile.getBlockMetadata() == 0) {
            yaw = -yaw;
        }
        if (yaw != 0.0F) {
            GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);
        }

        if (!cfg.doCulling) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        // bindTexture は DisplayList 外側（OptiFine シェーダー MultiTexID 安全）
        if (entry.texture != null) {
            NGTUtilClient.bindTexture(entry.texture);
        }
        GLHelper.callList(entry.glList);

        if (!cfg.doCulling) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        return true;
    }
}
