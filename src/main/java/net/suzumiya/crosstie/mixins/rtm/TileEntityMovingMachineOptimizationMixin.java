package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.NetworkRegistry;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.block.tileentity.TileEntityMovingMachine;
import jp.ngt.rtm.entity.EntityMMBoundingBox;
import jp.ngt.rtm.network.PacketMoveMM;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Phase 4-4: {@link TileEntityMovingMachine} の 1,677 万ブロック走査 ({@code searchMM}) と
 * {@code moveEntities} の {@code IntStream} / {@code sendToAll} を最適化する。
 */
@Mixin(value = TileEntityMovingMachine.class, remap = false)
public abstract class TileEntityMovingMachineOptimizationMixin extends TileEntity {

    @Shadow
    public boolean isCore;

    @Shadow
    public double posX;

    @Shadow
    public double posY;

    @Shadow
    public double posZ;

    @Shadow
    public double prevPosX;

    @Shadow
    public double prevPosY;

    @Shadow
    public double prevPosZ;

    @Shadow
    private List<EntityMMBoundingBox> bbList;

    @Shadow
    private int[] bbIds;

    @Shadow
    public abstract void setPair(TileEntityMovingMachine par1);

    /**
     * @author CrossTie
     * @reason 256x256x256 (16,777,216 ブロック) の 3 重ループを loadedTileEntityList 走査へ置換しフリーズを根絶
     */
    @Overwrite(remap = false)
    public void searchMM(int x, int y, int z) {
        if (this.worldObj == null) {
            return;
        }
        TileEntityMovingMachine self = (TileEntityMovingMachine) (Object) this;
        int minX = x - 128;
        int maxX = x + 128;
        int minZ = z - 128;
        int maxZ = z + 128;
        List<?> tiles = this.worldObj.loadedTileEntityList;
        for (int i = 0, len = tiles.size(); i < len; ++i) {
            Object obj = tiles.get(i);
            if (!(obj instanceof TileEntityMovingMachine) || obj == self) {
                continue;
            }
            TileEntityMovingMachine tile = (TileEntityMovingMachine) obj;
            if (tile.isInvalid()) {
                continue;
            }
            int tx = tile.xCoord;
            int ty = tile.yCoord;
            int tz = tile.zCoord;
            if (tx >= minX && tx < maxX && tz >= minZ && tz < maxZ && ty >= 0 && ty < 256) {
                if (tx == x && ty == y && tz == z) {
                    continue;
                }
                if (!tile.hasPair()) {
                    this.isCore = true;
                    this.setPair(tile);
                    tile.setPair(self);
                    return;
                }
            }
        }
    }

    /**
     * @author CrossTie
     * @reason IntStream 廃止と PacketMoveMM の sendToAll -> sendToAllAround 化
     */
    @Overwrite(remap = false)
    private void moveEntities() {
        if (!this.worldObj.isRemote) {
            double mX = this.posX - this.prevPosX;
            double mY = this.posY - this.prevPosY;
            double mZ = this.posZ - this.prevPosZ;

            int size = this.bbList.size();
            for (int i = 0; i < size; ++i) {
                EntityMMBoundingBox entity = this.bbList.get(i);
                entity.setPosition(entity.posX + mX, entity.posY + mY, entity.posZ + mZ);
            }
            for (int i = 0; i < size; ++i) {
                EntityMMBoundingBox entity = this.bbList.get(i);
                entity.moveMM(mX, mY, mZ);
            }

            if (this.bbIds != null) {
                RTMCore.NETWORK_WRAPPER.sendToAllAround(
                        new PacketMoveMM(this.bbIds, mX, mY, mZ),
                        new NetworkRegistry.TargetPoint(
                                this.worldObj.provider.dimensionId,
                                this.xCoord + this.posX,
                                this.yCoord + this.posY,
                                this.zCoord + this.posZ,
                                256.0D));
            }
        }
    }
}
