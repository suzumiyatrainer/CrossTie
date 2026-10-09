package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.block.TileEntityPlaceable;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【診断専用】
 * ディメンションアンロード時に「モデル名が空のRTMオブジェクト」が大量発生する問題の原因特定用。
 * <p>
 * readFromNBT を一度も通っていない(=コンストラクタ直後の初期値のままの) TileEntity が、
 * サーバー側で writeToNBT(=セーブ)される瞬間に、
 * ・そのTEが生成された時のスタックトレース
 * ・保存が呼ばれた時のスタックトレース
 * をログ([CrossTie-DIAG])へ出力する。ログ件数は上限あり。挙動は一切変更しない。
 */
@Mixin(value = {TileEntityPlaceable.class, TileEntityLargeRailCore.class}, remap = false)
public abstract class TileEntityUnreadSaveDiagnosticMixin {

    @Unique
    private static final Logger CROSSTIE$LOG = LogManager.getLogger("CrossTie-DIAG");
    @Unique
    private static final AtomicInteger CROSSTIE$LOG_COUNT = new AtomicInteger();
    @Unique
    private static final int CROSSTIE$LOG_LIMIT = 40;

    @Unique
    private boolean crosstie$nbtRead = false;
    @Unique
    private boolean crosstie$reported = false;
    @Unique
    private Throwable crosstie$createdAt;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void crosstie$onInit(CallbackInfo ci) {
        this.crosstie$createdAt = new Throwable("created on thread " + Thread.currentThread().getName());
    }

    @Inject(method = {"readFromNBT", "func_145839_a"}, at = @At("RETURN"), require = 0)
    private void crosstie$afterRead(net.minecraft.nbt.NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$nbtRead = true;
        this.crosstie$createdAt = null;
    }

    @Inject(method = {"writeToNBT", "func_145841_b"}, at = @At("HEAD"), require = 0)
    private void crosstie$beforeWrite(net.minecraft.nbt.NBTTagCompound nbt, CallbackInfo ci) {
        if (this.crosstie$nbtRead || this.crosstie$reported) {
            return;
        }
        TileEntity self = (TileEntity) (Object) this;
        if (self.getWorldObj() == null || self.getWorldObj().isRemote) {
            return;
        }
        if (CROSSTIE$LOG_COUNT.incrementAndGet() > CROSSTIE$LOG_LIMIT) {
            return;
        }
        this.crosstie$reported = true;
        CROSSTIE$LOG.warn("[CrossTie-DIAG] NBT未読込のTEが保存されます: {} dim={} pos=({},{},{}) thread={}",
                self.getClass().getName(), self.getWorldObj().provider.dimensionId,
                self.xCoord, self.yCoord, self.zCoord, Thread.currentThread().getName());
        if (this.crosstie$createdAt != null) {
            CROSSTIE$LOG.warn("[CrossTie-DIAG] 生成元:", this.crosstie$createdAt);
        }
        CROSSTIE$LOG.warn("[CrossTie-DIAG] 保存元:", new Throwable("save called"));
    }
}
