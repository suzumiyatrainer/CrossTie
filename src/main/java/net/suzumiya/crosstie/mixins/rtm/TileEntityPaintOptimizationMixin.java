package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.rtm.block.tileentity.TileEntityPaint;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.accessors.rtm.IPaintDisplayListHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3-4: {@link TileEntityPaint} の毎 tick 空回し廃止 ({@code canUpdate() = false}) と、
 * {@code RenderPaint} 用 DisplayList キャッシュの dirty 管理・確実な解放を行う Mixin。
 */
@Mixin(TileEntityPaint.class)
public abstract class TileEntityPaintOptimizationMixin extends TileEntity implements IPaintDisplayListHolder {

    @SideOnly(Side.CLIENT)
    @Unique
    private DisplayList crosstie$paintGLList;

    @Unique
    private boolean crosstie$paintDirty = true;

    @Unique
    private boolean crosstie$paintEmpty = false;

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$paintDirty = true;
    }

    @Inject(method = "setColor(IIIII)V", at = @At("TAIL"), remap = false)
    private void crosstie$onSetColor(int color, int alpha, int p1, int p2, int dir, CallbackInfo ci) {
        this.crosstie$paintDirty = true;
    }

    @Inject(method = "clearColor(III)V", at = @At("TAIL"), remap = false)
    private void crosstie$onClearColor(int p1, int p2, int dir, CallbackInfo ci) {
        this.crosstie$paintDirty = true;
    }

    @Unique
    @SideOnly(Side.CLIENT)
    private void crosstie$releasePaintGLList() {
        if (this.crosstie$paintGLList != null) {
            GLHelper.deleteGLList(this.crosstie$paintGLList);
            this.crosstie$paintGLList = null;
        }
        this.crosstie$paintDirty = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (this.worldObj != null && this.worldObj.isRemote) {
            this.crosstie$releasePaintGLList();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (this.worldObj != null && this.worldObj.isRemote) {
            this.crosstie$releasePaintGLList();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public DisplayList crosstie$getPaintGLList() {
        return this.crosstie$paintGLList;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void crosstie$setPaintGLList(DisplayList list) {
        this.crosstie$paintGLList = list;
    }

    @Override
    public boolean crosstie$isPaintDirty() {
        return this.crosstie$paintDirty;
    }

    @Override
    public void crosstie$setPaintDirty(boolean dirty) {
        this.crosstie$paintDirty = dirty;
    }

    @Override
    public boolean crosstie$isPaintEmpty() {
        return this.crosstie$paintEmpty;
    }

    @Override
    public void crosstie$setPaintEmpty(boolean empty) {
        this.crosstie$paintEmpty = empty;
    }
}
