package rise_fly.client.cache;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.BitSet;

public class CachedChunk {
    // 世界的Y轴范围常量
    private static final int WORLD_HEIGHT = 384;
    private static final int MIN_Y = -64;
    private static final int Y_LEVEL_OFFSET = -MIN_Y; // 64

    // 使用字节数组存储固体方块信息以节省内存
    private final byte[][][] solidBlocks = new byte[16][WORLD_HEIGHT][16];

    // 使用BitSet高效追踪哪些Y层已被扫描
    private final BitSet scannedYLevels = new BitSet(WORLD_HEIGHT);

    /**
     * 构造函数现在不执行任何扫描操作。
     * 扫描将由WorldCache根据需要进行调度。
     */
    public CachedChunk() {
        // Empty constructor
    }

    /**
     * 扫描并缓存指定Y轴范围内的方块固体状态。
     * @param chunk 要扫描的Minecraft世界区块
     * @param minY 要扫描的最低Y坐标
     * @param maxY 要扫描的最高Y坐标
     */
    public void scanYRange(WorldChunk chunk, int minY, int maxY) {
        // 保证扫描范围在世界高度内
        minY = Math.max(MIN_Y, minY);
        maxY = Math.min(WORLD_HEIGHT + MIN_Y - 1, maxY);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            int arrayY = y + Y_LEVEL_OFFSET;
            // 如果这一层已经扫描过，就跳过，避免重复工作
            if (scannedYLevels.get(arrayY)) {
                continue;
            }

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    mutablePos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(mutablePos);
                    if (state.blocksMovement()) {
                        solidBlocks[x][arrayY][z] = 1;
                    }
                }
            }
            // 标记这一整层Y都已扫描完毕
            scannedYLevels.set(arrayY);
        }
    }

    /**
     * 获取指定坐标的方块状态。
     * @param pos 方块位置
     * @return 方块的状态 (SOLID, AIR, 或 UNSCANNED_Y)
     */
    public BlockStatus getBlockStatus(BlockPos pos) {
        int y = pos.getY();

        // 检查坐标是否在世界有效范围内
        if (y < MIN_Y || y >= WORLD_HEIGHT + MIN_Y) {
            return BlockStatus.SOLID; // 视作固体以保证安全
        }

        int arrayY = y + Y_LEVEL_OFFSET;

        // 如果此Y层尚未被扫描，返回UNSCANNED_Y
        if (!scannedYLevels.get(arrayY)) {
            return BlockStatus.UNSCANNED_Y;
        }

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        return solidBlocks[localX][arrayY][localZ] == 1 ? BlockStatus.SOLID : BlockStatus.AIR;
    }
}