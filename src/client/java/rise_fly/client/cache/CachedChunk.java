// 文件路径: rise_fly/client/cache/CachedChunk.java
package rise_fly.client.cache;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.BitSet;

public class CachedChunk {
    // 世界的Y轴范围常量
    private static final int WORLD_HEIGHT = 384;
    private static final int MIN_Y = -64;
    private static final int Y_LEVEL_OFFSET = -MIN_Y; // 64

    private final byte[][][] solidBlocks = new byte[16][WORLD_HEIGHT][16];
    private final BitSet scannedYLevels = new BitSet(WORLD_HEIGHT);

    public CachedChunk() {
        // Empty constructor
    }

    public void scanYRange(WorldChunk chunk, int minY, int maxY) {
        minY = Math.max(MIN_Y, minY);
        maxY = Math.min(WORLD_HEIGHT + MIN_Y - 1, maxY);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            int arrayY = y + Y_LEVEL_OFFSET;
            if (scannedYLevels.get(arrayY)) {
                continue;
            }

            // 【核心修复】为了创建“安全边界”，我们采用两遍扫描法
            // isSolidOriginal 是一个临时数组，只记录方块原始的固体状态
            boolean[][] isSolidOriginal = new boolean[16][16];

            // --- 第一遍：找出当前Y层所有原始的固体方块 ---
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    mutablePos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(mutablePos);
                    if (state.blocksMovement() || state.isIn(BlockTags.LEAVES)) {
                        isSolidOriginal[x][z] = true;
                    }
                }
            }

            // --- 第二遍：将原始固体方块及其周围的邻居“加厚”写入最终的缓存中 ---
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // 如果当前方块是原始固体，或者它的上下左右邻居是原始固体，
                    // 那么就在最终缓存中把当前方块标记为固体。
                    if (isSolidOriginal[x][z] ||
                            (x > 0  && isSolidOriginal[x - 1][z]) || // 左邻居
                            (x < 15 && isSolidOriginal[x + 1][z]) || // 右邻居
                            (z > 0  && isSolidOriginal[x][z - 1]) || // 上邻居
                            (z < 15 && isSolidOriginal[x][z + 1])) { // 下邻居
                        solidBlocks[x][arrayY][z] = 1;
                    }
                }
            }
            scannedYLevels.set(arrayY);
        }
    }

    public BlockStatus getBlockStatus(BlockPos pos) {
        int y = pos.getY();
        if (y < MIN_Y || y >= WORLD_HEIGHT + MIN_Y) {
            return BlockStatus.SOLID;
        }

        int arrayY = y + Y_LEVEL_OFFSET;
        if (!scannedYLevels.get(arrayY)) {
            return BlockStatus.UNSCANNED_Y;
        }

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        return solidBlocks[localX][arrayY][localZ] == 1 ? BlockStatus.SOLID : BlockStatus.AIR;
    }
}