package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.EntityElectricalWiring;
import jp.ngt.rtm.entity.EntityTrainDetector;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code EntityTrainDetector} の在線確認処理をフリッカーゼロかつゼロアロケーションでスロットリングする。
 *
 * <ul>
 *   <li>スキップ tick は {@code findTrain} を上書きせず前回値を維持するため、信号・踏切の点滅が発生しない。</li>
 *   <li>実スキャン時も {@code IntStream} を使わず単純な {@code for} ループで走査する。</li>
 *   <li>{@code remap = true} で {@code onUpdate} / {@code super.onUpdate()} を SRG 名 ({@code func_70071_h_}) へ確実にリマップする。</li>
 * </ul>
 */
@Mixin(EntityTrainDetector.class)
public abstract class EntityTrainDetectorThrottleMixin extends EntityElectricalWiring {

    @Shadow(remap = false)
    private boolean findTrain;

    @Unique
    private int crosstie$tickCount = -1;

    protected EntityTrainDetectorThrottleMixin(World world) {
        super(world);
    }

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void crosstie$throttledOnUpdate(CallbackInfo ci) {
        if (!CrossTieConfig.detectorThrottlingEnabled) {
            return;
        }

        int interval = CrossTieConfig.detectorThrottleInterval;
        if (interval <= 1) {
            return;
        }

        if (this.worldObj != null && !this.worldObj.isRemote && this.tileEW != null) {
            boolean shouldScan;
            if (this.crosstie$tickCount < 0) {
                // 初回は必ずスキャンし、以降は EntityId で位相を分散する
                this.crosstie$tickCount = Math.abs(this.getEntityId()) % interval;
                shouldScan = true;
            } else {
                this.crosstie$tickCount = (this.crosstie$tickCount + 1) % interval;
                shouldScan = (this.crosstie$tickCount == 0);
            }

            if (shouldScan) {
                boolean found = false;
                int baseX = this.tileEW.xCoord;
                int baseY = this.tileEW.yCoord;
                int baseZ = this.tileEW.zCoord;
                for (int i = 0; i < 8; i++) {
                    TileEntity te = this.worldObj.getTileEntity(baseX, baseY - i, baseZ);
                    if (te instanceof TileEntityLargeRailBase) {
                        found = ((TileEntityLargeRailBase) te).isTrainOnRail();
                        break;
                    }
                }
                this.findTrain = found;
            }
            // スキップ tick は this.findTrain を前回値のまま維持
        }

        super.onUpdate();
        ci.cancel();
    }
}
