package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailPosition;
import net.suzumiya.crosstie.accessors.rtm.ISwitchDynamicDisplayListHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2-3 & 2-6: {@link TileEntityLargeRailCore} の毎フレーム Stream アロケーション排除、
 * および分岐レール静止時動的パーツ用 DisplayList の安全な解放管理。
 */
@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityLargeRailCoreOptimizationMixin extends TileEntityLargeRailBase
        implements ISwitchDynamicDisplayListHolder {

    @Shadow
    protected RailPosition[] railPositions;

    @SideOnly(Side.CLIENT)
    @Unique
    private DisplayList crosstie$switchDynamicGLList;

    @SideOnly(Side.CLIENT)
    @Unique
    private int crosstie$switchDynamicStateKey = Integer.MIN_VALUE;

    /**
     * @author CrossTie
     * @reason 毎フレーム呼ばれる isLoaded() から Arrays.stream() + Lambda アロケーションを排除。
     */
    @Overwrite
    public boolean isLoaded() {
        RailPosition[] rps = this.railPositions;
        if (rps == null || rps.length == 0) {
            return false;
        }
        for (int i = 0; i < rps.length; i++) {
            if (rps[i] == null) {
                return false;
            }
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Inject(method = "deleteGLList", at = @At("TAIL"), require = 0, remap = false)
    private void crosstie$onDeleteGLList(CallbackInfo ci) {
        this.crosstie$deleteSwitchDynamicGLList();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public DisplayList crosstie$getSwitchDynamicGLList() {
        return this.crosstie$switchDynamicGLList;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void crosstie$setSwitchDynamicGLList(DisplayList list) {
        this.crosstie$switchDynamicGLList = list;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int crosstie$getSwitchDynamicStateKey() {
        return this.crosstie$switchDynamicStateKey;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void crosstie$setSwitchDynamicStateKey(int key) {
        this.crosstie$switchDynamicStateKey = key;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void crosstie$deleteSwitchDynamicGLList() {
        if (this.crosstie$switchDynamicGLList != null) {
            GLHelper.deleteGLList(this.crosstie$switchDynamicGLList);
            this.crosstie$switchDynamicGLList = null;
        }
        this.crosstie$switchDynamicStateKey = Integer.MIN_VALUE;
    }
}
