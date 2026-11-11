package net.suzumiya.crosstie.compat.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * RecipePlusMod のガラス・色付きガラス・氷系ブロック (ハーフブロック・1/4ブロック・階段) の
 * 隣接面カリング ({@link Block#shouldSideBeRendered}) をバニラガラスと同様に機能させる互換ヘルパー。
 */
public final class RecipePlusGlassCompat {

    private static final double EPS = 1.0E-4D;

    private RecipePlusGlassCompat() {
    }

    /**
     * 対象ブロックの元ブロックが、内部の接合面をカリングすべき透過ガラス・氷系ブロックか判定する。
     */
    public static boolean isCullableGlassBase(Block base) {
        if (base == null || base.isOpaqueCube() || base instanceof BlockLeavesBase) {
            return false;
        }
        if (base instanceof BlockBreakable) {
            return true;
        }
        Material mat = base.getMaterial();
        return mat == Material.glass || mat == Material.ice || mat == Material.packedIce;
    }

    /**
     * {@code BlockExSlab} / {@code BlockExQuarterSlab} / {@code BlockExStairs} の
     * {@code shouldSideBeRendered} 判定を行う。
     *
     * @param selfBlock 呼び出し元 Block インスタンス
     * @param selfGlass 呼び出し元 IRecipePlusGlassBlock
     * @param world     ワールド
     * @param nx        隣接ブロック X (side 方向へ1進んだ座標)
     * @param ny        隣接ブロック Y
     * @param nz        隣接ブロック Z
     * @param side      描画対象の面 (0:-Y, 1:+Y, 2:-Z, 3:+Z, 4:-X, 5:+X)
     * @return 描画すべきなら {@code Boolean.TRUE}、カリングするなら {@code Boolean.FALSE}、
     *         ガラス・氷系以外でバニラ判定に委ねる場合は {@code null}
     */
    public static Boolean shouldSideBeRendered(
            Block selfBlock,
            IRecipePlusGlassBlock selfGlass,
            IBlockAccess world,
            int nx, int ny, int nz,
            int side) {
        if (world == null || side < 0 || side > 5) {
            return null;
        }

        Block base = selfGlass.crosstie$getBaseBlock();
        if (!isCullableGlassBase(base)) {
            return null;
        }

        int cx = nx - Facing.offsetsXForSide[side];
        int cy = ny - Facing.offsetsYForSide[side];
        int cz = nz - Facing.offsetsZForSide[side];

        int selfKind = selfGlass.crosstie$getShapeKind();
        double cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ;

        if ((selfKind == IRecipePlusGlassBlock.SHAPE_SLAB || selfKind == IRecipePlusGlassBlock.SHAPE_QUARTER_SLAB)
                && world.getBlock(cx, cy, cz) == selfBlock) {
            int cMeta = world.getBlockMetadata(cx, cy, cz);
            double t = (selfKind == IRecipePlusGlassBlock.SHAPE_SLAB) ? 0.5D : 0.25D;
            double[] bounds = computeSlabBounds(cMeta, t);
            cMinX = bounds[0];
            cMinY = bounds[1];
            cMinZ = bounds[2];
            cMaxX = bounds[3];
            cMaxY = bounds[4];
            cMaxZ = bounds[5];
        } else {
            // BlockExStairs (RenderBlocks がサブ直方体ごとに BlockBounds をセット済み) やフォールバック
            cMinX = selfBlock.getBlockBoundsMinX();
            cMinY = selfBlock.getBlockBoundsMinY();
            cMinZ = selfBlock.getBlockBoundsMinZ();
            cMaxX = selfBlock.getBlockBoundsMaxX();
            cMaxY = selfBlock.getBlockBoundsMaxY();
            cMaxZ = selfBlock.getBlockBoundsMaxZ();
        }

        // 1. 自身の対象面が 1x1x1 セル境界上にあるか確認。
        // セル内部にある面 (例: 北寄り縦ハーフの南面 Z=0.5) は隣接ブロックと接していないため必ず描画する。
        if (!isOnCellBoundary(cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ, side)) {
            return Boolean.TRUE;
        }

        // 2. 隣接ブロックが不透明フルブロックなら無条件でカリング
        Block neighbor = world.getBlock(nx, ny, nz);
        if (neighbor == null) {
            return Boolean.TRUE;
        }
        if (neighbor.isOpaqueCube()) {
            return Boolean.FALSE;
        }

        // 3. 隣接ブロックの元ブロック・メタデータが自身と一致するか確認 (同色ガラス・同種氷同士のみ結合)
        int selfBaseMeta = selfGlass.crosstie$getBaseBlockMeta();
        Block nBase;
        int nBaseMeta;
        if (neighbor instanceof IRecipePlusGlassBlock) {
            IRecipePlusGlassBlock nGlass = (IRecipePlusGlassBlock) neighbor;
            nBase = nGlass.crosstie$getBaseBlock();
            nBaseMeta = nGlass.crosstie$getBaseBlockMeta();
        } else {
            nBase = neighbor;
            nBaseMeta = world.getBlockMetadata(nx, ny, nz);
        }

        if (nBase != base || nBaseMeta != selfBaseMeta) {
            return Boolean.TRUE;
        }

        // 4. 隣接ブロックのジオメトリが共有境界の反対側から自身の面領域を完全に覆っているか判定
        int oppositeSide = Facing.oppositeSide[side];
        if (neighbor instanceof IRecipePlusGlassBlock) {
            IRecipePlusGlassBlock nGlass = (IRecipePlusGlassBlock) neighbor;
            int nKind = nGlass.crosstie$getShapeKind();
            int nMeta = world.getBlockMetadata(nx, ny, nz);

            if (nKind == IRecipePlusGlassBlock.SHAPE_SLAB || nKind == IRecipePlusGlassBlock.SHAPE_QUARTER_SLAB) {
                double nt = (nKind == IRecipePlusGlassBlock.SHAPE_SLAB) ? 0.5D : 0.25D;
                double[] nb = computeSlabBounds(nMeta, nt);
                if (isOnCellBoundary(nb[0], nb[1], nb[2], nb[3], nb[4], nb[5], oppositeSide)
                        && coversFace2D(cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ,
                                nb[0], nb[1], nb[2], nb[3], nb[4], nb[5], side)) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            } else if (nKind == IRecipePlusGlassBlock.SHAPE_STAIRS) {
                if (doesNeighborStairCoverFace(world, cx, cy, cz, selfKind,
                        cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ, nMeta, side, oppositeSide)) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            }
        } else if (neighbor == base) {
            // バニラのフルサイズガラス/色ガラス/氷ブロックは [0, 1]^3 全体を占めるため常に覆う
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }

    /**
     * バニラの {@link BlockBreakable} (フルサイズのガラス・色付きガラス・氷) の隣に
     * RecipePlusMod のガラス系ブロックがある場合の追加カリング判定。
     *
     * @return カリングすべきなら {@code true} (shouldSideBeRendered を false にする)
     */
    public static boolean shouldCullVanillaBreakableSide(
            Block selfBreakable,
            IBlockAccess world,
            int nx, int ny, int nz,
            int side) {
        if (world == null || side < 0 || side > 5) {
            return false;
        }
        Block neighbor = world.getBlock(nx, ny, nz);
        if (!(neighbor instanceof IRecipePlusGlassBlock)) {
            return false;
        }

        IRecipePlusGlassBlock nGlass = (IRecipePlusGlassBlock) neighbor;
        Block nBase = nGlass.crosstie$getBaseBlock();
        if (nBase != selfBreakable) {
            return false;
        }

        int cx = nx - Facing.offsetsXForSide[side];
        int cy = ny - Facing.offsetsYForSide[side];
        int cz = nz - Facing.offsetsZForSide[side];
        int selfMeta = world.getBlockMetadata(cx, cy, cz);
        if (nGlass.crosstie$getBaseBlockMeta() != selfMeta) {
            return false;
        }

        // フルブロックの面は [0,1]x[0,1] なので、隣接する RecipePlus ブロックが反対側の境界全面 [0,1]x[0,1] を覆っている場合のみカリング
        int oppositeSide = Facing.oppositeSide[side];
        int nKind = nGlass.crosstie$getShapeKind();
        int nMeta = world.getBlockMetadata(nx, ny, nz);

        if (nKind == IRecipePlusGlassBlock.SHAPE_SLAB || nKind == IRecipePlusGlassBlock.SHAPE_QUARTER_SLAB) {
            double nt = (nKind == IRecipePlusGlassBlock.SHAPE_SLAB) ? 0.5D : 0.25D;
            double[] nb = computeSlabBounds(nMeta, nt);
            return isOnCellBoundary(nb[0], nb[1], nb[2], nb[3], nb[4], nb[5], oppositeSide)
                    && coversFace2D(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                            nb[0], nb[1], nb[2], nb[3], nb[4], nb[5], side);
        } else if (nKind == IRecipePlusGlassBlock.SHAPE_STAIRS) {
            return isStairFullFace(nMeta, oppositeSide);
        }
        return false;
    }

    /**
     * 板ガラス・鉄格子 ({@link net.minecraft.block.BlockPane}) が指定座標 {@code (nx, ny, nz)} の
     * RecipePlusMod ブロック (ハーフブロック・1/4ブロック・階段) に接続可能か判定する。
     *
     * @param world ワールド
     * @param nx    隣接ブロック X
     * @param ny    隣接ブロック Y
     * @param nz    隣接ブロック Z
     * @param dir   板ガラスから隣接ブロックへ向かう方向 (NORTH, SOUTH, WEST, EAST)
     * @return 接続すべきなら {@code true}
     */
    public static boolean canPaneConnectToRecipePlus(
            IBlockAccess world,
            int nx, int ny, int nz,
            ForgeDirection dir) {
        if (world == null || dir == null) {
            return false;
        }
        Block neighbor = world.getBlock(nx, ny, nz);
        if (!(neighbor instanceof IRecipePlusGlassBlock)) {
            return false;
        }

        IRecipePlusGlassBlock nGlass = (IRecipePlusGlassBlock) neighbor;
        int nKind = nGlass.crosstie$getShapeKind();
        int nMeta = world.getBlockMetadata(nx, ny, nz);
        int oppositeSide = dir.getOpposite().ordinal();

        if (nKind == IRecipePlusGlassBlock.SHAPE_SLAB || nKind == IRecipePlusGlassBlock.SHAPE_QUARTER_SLAB) {
            double nt = (nKind == IRecipePlusGlassBlock.SHAPE_SLAB) ? 0.5D : 0.25D;
            double[] nb = computeSlabBounds(nMeta, nt);
            // 1. 板ガラス側の境界面に接触しているか
            if (!isOnCellBoundary(nb[0], nb[1], nb[2], nb[3], nb[4], nb[5], oppositeSide)) {
                return false;
            }
            // 2. 板ガラスの板厚中心 (0.4375..0.5625) と交差しているか (水平ハーフも含む)
            if (dir == ForgeDirection.NORTH || dir == ForgeDirection.SOUTH) {
                return nb[0] <= 0.5625D - EPS && nb[3] >= 0.4375D + EPS;
            } else if (dir == ForgeDirection.WEST || dir == ForgeDirection.EAST) {
                return nb[2] <= 0.5625D - EPS && nb[5] >= 0.4375D + EPS;
            }
            return false;
        } else if (nKind == IRecipePlusGlassBlock.SHAPE_STAIRS) {
            return true;
        }
        return false;
    }

    /**
     * BlockExSlab / BlockExQuarterSlab のメタデータと厚み {@code t} からバウンディングボックスを計算する。
     */
    private static double[] computeSlabBounds(int meta, double t) {
        switch (meta) {
            case 0: // 上付き (+Y)
                return new double[] { 0.0D, 1.0D - t, 0.0D, 1.0D, 1.0D, 1.0D };
            case 1: // 下付き (-Y)
                return new double[] { 0.0D, 0.0D, 0.0D, 1.0D, t, 1.0D };
            case 2: // 南付き (+Z)
                return new double[] { 0.0D, 0.0D, 1.0D - t, 1.0D, 1.0D, 1.0D };
            case 3: // 北付き (-Z)
                return new double[] { 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, t };
            case 4: // 東付き (+X)
                return new double[] { 1.0D - t, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D };
            case 5: // 西付き (-X)
                return new double[] { 0.0D, 0.0D, 0.0D, t, 1.0D, 1.0D };
            default:
                return new double[] { 0.0D, 0.0D, 0.0D, 1.0D, t, 1.0D };
        }
    }

    private static boolean isOnCellBoundary(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int side) {
        switch (side) {
            case 0: // -Y
                return minY <= EPS;
            case 1: // +Y
                return maxY >= 1.0D - EPS;
            case 2: // -Z
                return minZ <= EPS;
            case 3: // +Z
                return maxZ >= 1.0D - EPS;
            case 4: // -X
                return minX <= EPS;
            case 5: // +X
                return maxX >= 1.0D - EPS;
            default:
                return false;
        }
    }

    private static boolean coversFace2D(
            double cMinX, double cMinY, double cMinZ,
            double cMaxX, double cMaxY, double cMaxZ,
            double nMinX, double nMinY, double nMinZ,
            double nMaxX, double nMaxY, double nMaxZ,
            int side) {
        switch (side) {
            case 0:
            case 1: // XZ 平面の矩形包含判定
                return nMinX <= cMinX + EPS && nMaxX >= cMaxX - EPS
                        && nMinZ <= cMinZ + EPS && nMaxZ >= cMaxZ - EPS;
            case 2:
            case 3: // XY 平面の矩形包含判定
                return nMinX <= cMinX + EPS && nMaxX >= cMaxX - EPS
                        && nMinY <= cMinY + EPS && nMaxY >= cMaxY - EPS;
            case 4:
            case 5: // YZ 平面の矩形包含判定
                return nMinY <= cMinY + EPS && nMaxY >= cMaxY - EPS
                        && nMinZ <= cMinZ + EPS && nMaxZ >= cMaxZ - EPS;
            default:
                return false;
        }
    }

    /**
     * 階段ブロックの指定面 {@code stairSide} が 1x1 全面を覆っているか判定する。
     */
    private static boolean isStairFullFace(int stairMeta, int stairSide) {
        boolean upsideDown = (stairMeta & 4) != 0;
        if (stairSide == 0 && !upsideDown) {
            return true;
        }
        if (stairSide == 1 && upsideDown) {
            return true;
        }
        return false;
    }

    /**
     * 隣接ブロックが階段 ({@code SHAPE_STAIRS}) の場合に、現在の面を覆っているか判定する。
     */
    private static boolean doesNeighborStairCoverFace(
            IBlockAccess world,
            int cx, int cy, int cz,
            int selfKind,
            double cMinX, double cMinY, double cMinZ,
            double cMaxX, double cMaxY, double cMaxZ,
            int nMeta,
            int side,
            int oppositeSide) {
        boolean nUpsideDown = (nMeta & 4) != 0;
        // 階段のベースハーフ部分: 通常なら Y=[0, 0.5]、逆さなら Y=[0.5, 1.0]
        double sMinY = nUpsideDown ? 0.5D : 0.0D;
        double sMaxY = nUpsideDown ? 1.0D : 0.5D;
        if (isOnCellBoundary(0.0D, sMinY, 0.0D, 1.0D, sMaxY, 1.0D, oppositeSide)
                && coversFace2D(cMinX, cMinY, cMinZ, cMaxX, cMaxY, cMaxZ,
                        0.0D, sMinY, 0.0D, 1.0D, sMaxY, 1.0D, side)) {
            return true;
        }

        // 同じ向きのガラス階段同士が横に並んでいる場合、上段ステップ部分の側面同士も一致するためカリング
        if (selfKind == IRecipePlusGlassBlock.SHAPE_STAIRS) {
            int cMeta = world.getBlockMetadata(cx, cy, cz);
            if (cMeta == nMeta) {
                int dir = cMeta & 3;
                // dir 0(+X), 1(-X) は Z方向(side 2, 3)に並ぶと断面が一致
                if ((dir == 0 || dir == 1) && (side == 2 || side == 3)) {
                    return true;
                }
                // dir 2(+Z), 3(-Z) は X方向(side 4, 5)に並ぶと断面が一致
                if ((dir == 2 || dir == 3) && (side == 4 || side == 5)) {
                    return true;
                }
            }
        }
        return false;
    }
}

