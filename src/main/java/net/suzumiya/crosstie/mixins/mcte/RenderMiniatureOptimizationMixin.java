package net.suzumiya.crosstie.mixins.mcte;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.mcte.block.RenderMiniature;
import jp.ngt.mcte.block.TileEntityMiniature;
import jp.ngt.mcte.world.MCTEWorld;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.renderer.NGTRenderer;
import jp.ngt.ngtlib.util.NGTUtilClient;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.suzumiya.crosstie.accessors.mcte.IMiniatureOptimizationAccessor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Phase 5-1: {@link RenderMiniature} の空 pass・空 TE/Entity 描画スキップと pass 別 dirty 再コンパイル対応。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RenderMiniature.class, remap = false)
public abstract class RenderMiniatureOptimizationMixin extends TileEntitySpecialRenderer {

    /**
     * @author CrossTie
     * @reason 空の TE/Entity リストや空の pass に対する無駄な GL ステート切替・DisplayList 再生を排除する
     */
    @Overwrite(remap = false)
    private void renderMiniatureAt(TileEntityMiniature tile, double par2, double par4, double par6, float par8) {
        if (tile.blocksObject == null) {
            return;
        }

        int pass = MinecraftForgeClient.getRenderPass();
        if (pass == -1) {
            pass = 0;
        }
        if (pass < 0 || pass > 1) {
            return;
        }

        IMiniatureOptimizationAccessor opt = (tile instanceof IMiniatureOptimizationAccessor)
                ? (IMiniatureOptimizationAccessor) tile
                : null;
        if (opt != null && !opt.crosstie$hasPassContent(pass)) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_CULL_FACE);

        GL11.glTranslatef((float) par2 + 0.5F, (float) par4 + 0.5F, (float) par6 + 0.5F);
        switch (tile.attachSide) {
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
        GL11.glTranslatef(0.0F, -0.5F, 0.0F);
        GL11.glTranslatef(tile.getOffsetX(), tile.getOffsetY(), tile.getOffsetZ());
        GL11.glRotatef(tile.getRotation(), 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(tile.offsetX, tile.offsetY, tile.offsetZ);
        float f0 = tile.scale;
        GL11.glScalef(f0, f0, f0);

        float x = (float) tile.blocksObject.xSize * 0.5F;
        float z = (float) tile.blocksObject.zSize * 0.5F;
        GL11.glTranslatef(-x, 0.0F, -z);

        MCTEWorld world = tile.getDummyWorld();
        if (world != null) {
            world.setRenderTransform(
                    tile.scale,
                    tile.offsetX, tile.offsetY, tile.offsetZ,
                    tile.getOffsetX(), tile.getOffsetY(), tile.getOffsetZ(),
                    tile.getRotation(), tile.attachSide
            );
            // ブロック描画後だと明るさが変になるため先に内部 TE / Entity を描画
            if (!world.tileEntityLoaded || !world.getTileEntityList().isEmpty()) {
                NGTRenderer.renderTileEntities(world, par8, pass);
            }
            if (!world.getEntityList().isEmpty()) {
                NGTRenderer.renderEntities(world, par8, pass);
            }
            if (opt == null || opt.crosstie$hasBlockContent(pass)) {
                this.renderBlocks(world, tile, par8, pass);
            }
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    /**
     * @author CrossTie
     * @reason world.updated を pass 別に伝播させ、pass 1 更新漏れを解消しつつ不要コンパイルを抑止する
     */
    @Overwrite(remap = false)
    private void renderBlocks(MCTEWorld world, TileEntityMiniature tile, float par3, int pass) {
        if (tile.glLists == null) {
            tile.glLists = new DisplayList[2];
        }

        IMiniatureOptimizationAccessor opt = (tile instanceof IMiniatureOptimizationAccessor)
                ? (IMiniatureOptimizationAccessor) tile
                : null;
        if (world.updated) {
            world.updated = false;
            if (opt != null) {
                opt.crosstie$markAllPassesDirty();
            }
        }

        GLHelper.disableLighting();
        int light = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        GLHelper.setBrightness(light);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        this.bindTexture(TextureMap.locationBlocksTexture);
        boolean smoothing = NGTUtilClient.getMinecraft().gameSettings.ambientOcclusion != 0;
        if (smoothing) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
        }

        boolean needsCompile = !GLHelper.isValid(tile.glLists[pass])
                || (opt != null && opt.crosstie$isPassDirty(pass));
        if (needsCompile) {
            if (!GLHelper.isValid(tile.glLists[pass])) {
                tile.glLists[pass] = GLHelper.generateGLList(tile.glLists[pass]);
            }
            GLHelper.startCompile(tile.glLists[pass]);
            try {
                NGTRenderer.renderNGTObject(world, tile.blocksObject, false, tile.mode.id, pass);
            } finally {
                GLHelper.endCompile();
            }
            if (opt != null) {
                opt.crosstie$clearPassDirty(pass);
            }
            // 初回コンパイル直後はまだ描画されていない（GL_COMPILE）ため即再生
            GLHelper.callList(tile.glLists[pass]);
        } else {
            GLHelper.callList(tile.glLists[pass]);
        }

        if (smoothing) {
            GL11.glShadeModel(GL11.GL_FLAT);
        }

        GLHelper.enableLighting();
        NGTUtilClient.getMinecraft().entityRenderer.enableLightmap(par3);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
