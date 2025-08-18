package rise_fly.client.cache;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public class CachedChunk {
    private static final int WORLD_HEIGHT = 384;
    private static final int MIN_Y = -64;
    // 将 byte[][][] 更改为 BlockState[][][] 来存储更完整的信息
    private final BlockState[][][] blockStates = new BlockState[16][WORLD_HEIGHT][16];

    public CachedChunk(WorldChunk chunk) {
        scan(chunk);
    }

    private void scan(WorldChunk chunk) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y < WORLD_HEIGHT + MIN_Y; y++) {
                    mutablePos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    int arrayY = y - MIN_Y;
                    // 直接存储 BlockState
                    blockStates[x][arrayY][z] = chunk.getBlockState(mutablePos);
                }
            }
        }
    }

    /**
     * 获取指定位置的方块状态。
     * @param pos 世界坐标
     * @return BlockState 对象，如果坐标无效则返回 null
     */
    public BlockState getBlockState(BlockPos pos) {
        if (pos.getY() < MIN_Y || pos.getY() >= WORLD_HEIGHT + MIN_Y) {
            return Blocks.VOID_AIR.getDefaultState(); // 对于世界外的区域，视为空
        }
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int arrayY = pos.getY() - MIN_Y;
        return blockStates[localX][arrayY][localZ];
    }
}