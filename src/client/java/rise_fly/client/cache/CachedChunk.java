package rise_fly.client.cache;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public class CachedChunk {
    private static final int WORLD_HEIGHT = 384;
    private static final int MIN_Y = -64;
    private final byte[][][] solidBlocks = new byte[16][WORLD_HEIGHT][16];

    public CachedChunk(WorldChunk chunk) {
        scan(chunk);
    }

    private void scan(WorldChunk chunk) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y < WORLD_HEIGHT + MIN_Y; y++) {
                    mutablePos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(mutablePos);
                    if (state.blocksMovement()) {
                        int arrayY = y - MIN_Y;
                        solidBlocks[x][arrayY][z] = 1;
                    }
                }
            }
        }
    }

    public boolean isSolid(BlockPos pos) {
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        if (pos.getY() < MIN_Y || pos.getY() >= WORLD_HEIGHT + MIN_Y) return true;
        int arrayY = pos.getY() - MIN_Y;
        return solidBlocks[localX][arrayY][localZ] == 1;
    }
}