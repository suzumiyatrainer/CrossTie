package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityTrainBase の総合最適化:
 *
 * <ul>
 *   <li><b>P3 (遠距離間引き)</b> - クライアント側でプレイヤーから 256m 以上離れた車両の更新頻度を低減</li>
 *   <li><b>P4 (onUpdate軽量化)</b> - onUpdate 内での重複 getBlock() 呼び出しを簡易キャッシュ</li>
 * </ul>
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseOptimizationMixin {

    @Unique
    private static final double DISTANT_THRESHOLD_SQ = 256.0D * 256.0D;

    @Unique
    private static final int DISTANT_SKIP_INTERVAL = 2;

    @Unique
    private int crosstie$distantSkipCounter = 0;

    // ========================
    // P3: 遠距離間引き (Client only)
    // ========================

    /**
     * クライアント側でプレイヤーから 256m 以上離れた車両の onUpdate を
     * DISTANT_SKIP_INTERVAL ティックに1回だけ実行する。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 0, remap = true)
    private void crosstie$skipDistantUpdate(CallbackInfo ci) {
        if (!CrossTieConfig.trainDistantCullingEnabled) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        if (!entity.worldObj.isRemote) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null) {
            return;
        }
        double dx = entity.posX - mc.renderViewEntity.posX;
        double dz = entity.posZ - mc.renderViewEntity.posZ;
        if (dx * dx + dz * dz > DISTANT_THRESHOLD_SQ) {
            if (++this.crosstie$distantSkipCounter % DISTANT_SKIP_INTERVAL != 0) {
                ci.cancel();
            }
        } else {
            this.crosstie$distantSkipCounter = 0;
        }
    }

    @Unique
    private int crosstie$lastBX = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastBY = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastBZ = Integer.MIN_VALUE;

    @Unique
    private Block crosstie$cachedBlock = null;

    // ========================
    // P4: onUpdate 内の getBlock() 呼び出しキャッシュ
    // ========================

    @Redirect(method = "onUpdate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;"),
            require = 0)
    private Block crosstie$cachedGetBlock(World world, int x, int y, int z) {
        if (!CrossTieConfig.trainGetBlockCacheEnabled) {
            return world.getBlock(x, y, z);
        }
        if (x == crosstie$lastBX && y == crosstie$lastBY && z == crosstie$lastBZ && crosstie$cachedBlock != null) {
            return crosstie$cachedBlock;
        }
        crosstie$lastBX = x;
        crosstie$lastBY = y;
        crosstie$lastBZ = z;
        crosstie$cachedBlock = world.getBlock(x, y, z);
        return crosstie$cachedBlock;
    }
}