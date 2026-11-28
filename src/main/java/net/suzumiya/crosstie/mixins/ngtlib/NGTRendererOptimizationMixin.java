package net.suzumiya.crosstie.mixins.ngtlib;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.block.NGTObject;
import jp.ngt.ngtlib.renderer.NGTRenderer;
import jp.ngt.ngtlib.world.IBlockAccessNGT;
import jp.ngt.ngtlib.world.NGTWorld;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.client.CrossTieBlendStateHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Phase 5-1 & 5-2: {@link NGTRenderer} の空リスト早期リターン・Stream 廃止および
 * {@code renderNGTObject} の pass 1 における Angelica FBO スラッシング防止。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = NGTRenderer.class, remap = false)
public abstract class NGTRendererOptimizationMixin {

    @Unique
    private static boolean crosstie$ngtoBlendEnabledByUs = false;

    @Shadow
    private static void loadTileEntity(NGTWorld par1) {
    }

    @Shadow
    public static void renderTileEntityByRenderer(TileEntity tile, double par2, double par4, double par6, float par8, int pass) {
    }

    @Shadow
    public static void renderEntityByRenderer(Entity entity, float par8, int pass) {
    }

    @Inject(
        method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void crosstie$beginNgtoPass(IBlockAccessNGT par1, NGTObject par2, boolean changeLightting, int mode, int pass,
            CallbackInfo ci) {
        crosstie$ngtoBlendEnabledByUs = false;
    }

    @Redirect(
        method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false),
        require = 0,
        remap = false
    )
    private static void crosstie$redirectNgtoEnable(int cap) {
        if (cap == GL11.GL_BLEND) {
            crosstie$ngtoBlendEnabledByUs = CrossTieBlendStateHelper.enterPass1Blend();
        } else {
            GL11.glEnable(cap);
        }
    }

    @Redirect(
        method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OpenGlHelper;glBlendFunc(IIII)V", remap = true),
        require = 0,
        remap = false
    )
    private static void crosstie$redirectNgtoBlendFunc(int sFactorRGB, int dFactorRGB, int sFactorAlpha, int dFactorAlpha) {
        // enterPass1Blend() 内で必要な場合のみ 4 引数 glBlendFunc を設定済み
    }

    @Redirect(
        method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/block/NGTObject;ZII)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false),
        require = 0,
        remap = false
    )
    private static void crosstie$redirectNgtoDisable(int cap) {
        if (cap == GL11.GL_BLEND) {
            CrossTieBlendStateHelper.exitPass1Blend(crosstie$ngtoBlendEnabledByUs);
            crosstie$ngtoBlendEnabledByUs = false;
        } else {
            GL11.glDisable(cap);
        }
    }

    /**
     * @author CrossTie
     * @reason 空リスト早期リターンと通常 for ループ化でミニチュア内部 TE 描画のオーバーヘッドを排除
     */
    @Overwrite(remap = false)
    public static void renderTileEntities(NGTWorld par1, float par8, int pass) {
        if (!par1.tileEntityLoaded) {
            loadTileEntity(par1);
            par1.tileEntityLoaded = true;
        }
        List<TileEntity> list = par1.getTileEntityList();
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = 0, len = list.size(); i < len; ++i) {
            TileEntity tile = list.get(i);
            renderTileEntityByRenderer(tile, tile.xCoord, tile.yCoord, tile.zCoord, par8, pass);
        }
    }

    /**
     * @author CrossTie
     * @reason 空リスト早期リターンと Stream 廃止で毎フレームの Stream/ラムダ生成を排除
     */
    @Overwrite(remap = false)
    public static void renderEntities(NGTWorld par1, float par8, int pass) {
        List<Entity> list = par1.getEntityList();
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = 0, len = list.size(); i < len; ++i) {
            Entity entity = list.get(i);
            if (entity != null && !entity.isDead) {
                renderEntityByRenderer(entity, par8, pass);
            }
        }
    }
}
