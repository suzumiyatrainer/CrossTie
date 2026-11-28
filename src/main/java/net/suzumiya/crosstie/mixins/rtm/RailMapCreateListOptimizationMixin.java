package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.modelset.ModelSetRail;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.List;

/**
 * 2-5: {@link RailMap#createRailList(RailProperty)} の O(N²) 線形重複探索を
 * {@code HashMap<Long, Integer>} による O(N) へ最適化する Mixin。
 */
@Mixin(value = RailMap.class, remap = false)
public abstract class RailMapCreateListOptimizationMixin {

    @Shadow
    @Final
    protected List<int[]> rails;

    @Shadow
    public abstract double getLength();

    @Shadow
    public abstract double[] getRailPos(int paramInt1, int paramInt2);

    @Shadow
    public abstract float getRailYaw(int paramInt1, int paramInt2);

    @Shadow
    public abstract double getRailHeight(int paramInt1, int paramInt2);

    @Shadow
    public abstract RailPosition getStartRP();

    @Shadow
    public abstract RailPosition getEndRP();

    /**
     * @author CrossTie
     * @reason addRailBlock() の O(N²) 線形走査を (x, z) パックキーの HashMap で O(N) 化する。
     *         同一 (x, z) は最小の y を残し、start/end の neighborPos を除外する元仕様を 100% 維持。
     */
    @Overwrite
    protected void createRailList(RailProperty prop) {
        ModelSetRail modelSet = prop.getModelSet();
        int width = modelSet.getConfig().ballastWidth >> 1;

        this.rails.clear();
        int split = (int) (this.getLength() * 4.0D);
        if (split <= 0) {
            return;
        }

        int[] startNeighbor = this.getStartRP().getNeighborPos();
        int[] endNeighbor = this.getEndRP().getNeighborPos();
        HashMap<Long, Integer> xzToIndex = new HashMap<Long, Integer>(Math.max(16, split));

        double halfPi = Math.PI * 0.5D;
        for (int j = 0; j < split; j++) {
            double[] point = this.getRailPos(split, j);
            double x = point[1];
            double z = point[0];
            double slope = Math.toRadians(this.getRailYaw(split, j));
            double height = this.getRailHeight(split, j);
            int y = (int) height;
            int x0 = MathHelper.floor_double(x);
            int z0 = MathHelper.floor_double(z);

            double sinSlopePlus = Math.sin(slope + halfPi);
            double cosSlopePlus = Math.cos(slope + halfPi);
            double sinSlopeMinus = Math.sin(slope - halfPi);
            double cosSlopeMinus = Math.cos(slope - halfPi);

            for (int i = 1; i <= width; i++) {
                int x1 = MathHelper.floor_double(x + sinSlopePlus * i);
                int z1 = MathHelper.floor_double(z + cosSlopePlus * i);
                int x2 = MathHelper.floor_double(x + sinSlopeMinus * i);
                int z2 = MathHelper.floor_double(z + cosSlopeMinus * i);
                this.crosstie$putRailBlock(xzToIndex, x1, y, z1, startNeighbor, endNeighbor);
                this.crosstie$putRailBlock(xzToIndex, x2, y, z2, startNeighbor, endNeighbor);
            }
            this.crosstie$putRailBlock(xzToIndex, x0, y, z0, startNeighbor, endNeighbor);
        }
    }

    @Unique
    private void crosstie$putRailBlock(HashMap<Long, Integer> xzToIndex, int x, int y, int z,
            int[] startNeighbor, int[] endNeighbor) {
        if ((x == startNeighbor[0] && y == startNeighbor[1] && z == startNeighbor[2])
                || (x == endNeighbor[0] && y == endNeighbor[1] && z == endNeighbor[2])) {
            return;
        }
        Long key = Long.valueOf((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
        Integer existingIdx = xzToIndex.get(key);
        if (existingIdx != null) {
            int[] existing = this.rails.get(existingIdx.intValue());
            if (existing[1] <= y) {
                return;
            }
            existing[1] = y;
            return;
        }
        xzToIndex.put(key, Integer.valueOf(this.rails.size()));
        this.rails.add(new int[] { x, y, z });
    }
}
