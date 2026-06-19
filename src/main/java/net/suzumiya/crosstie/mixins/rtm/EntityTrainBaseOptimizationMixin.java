package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
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

    @Unique
    private int crosstie$lastBX = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastBY = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastBZ = Integer.MIN_VALUE;

    @Unique
    private Block crosstie$cachedBlock = null;

    // ========================
    // P3: onUpdate の遠距離間引き (client only)
    // ========================

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$distantThrottle(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        World world = self.worldObj;
        if (world != null && world.isRemote) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.renderViewEntity != null) {
                double dx = self.posX - mc.renderViewEntity.posX;
                double dy = self.posY - mc.renderViewEntity.posY;
                double dz = self.posZ - mc.renderViewEntity.posZ;
                if (dx * dx + dy * dy + dz * dz > DISTANT_THRESHOLD_SQ) {
                    crosstie$distantSkipCounter++;
                    if (crosstie$distantSkipCounter % DISTANT_SKIP_INTERVAL != 0) {
                        ci.cancel();
                        return;
                    }
                } else {
                    crosstie$distantSkipCounter = 0;
                }
            }
        }
    }

    // ========================
    // P4: onUpdate 内の getBlock() 呼び出しキャッシュ
    // ========================

    @Redirect(method = "onUpdate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;"),
            require = 0)
    private Block crosstie$cachedGetBlock(World world, int x, int y, int z) {
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