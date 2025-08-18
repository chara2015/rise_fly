package rise_fly.client.cache;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WorldCache {
    public static final WorldCache INSTANCE = new WorldCache();
    private final Map<ChunkPos, CachedChunk> chunkCache = new ConcurrentHashMap<>();
    private Consumer<Void> onChunkLoadedCallback = null;

    private WorldCache() {}

    public void setOnChunkLoadedCallback(Consumer<Void> callback) {
        this.onChunkLoadedCallback = callback;
    }

    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (!chunkCache.isEmpty()) chunkCache.clear();
            return;
        }
        ClientWorld world = client.world;
        int renderDistance = client.options.getClampedViewDistance();
        ChunkPos playerChunkPos = client.player.getChunkPos();

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos currentChunkPos = new ChunkPos(playerChunkPos.x + x, playerChunkPos.z + z);
                if (!chunkCache.containsKey(currentChunkPos)) {
                    WorldChunk chunk = world.getChunk(currentChunkPos.x, currentChunkPos.z);
                    if (chunk != null) {
                        chunkCache.put(currentChunkPos, new CachedChunk(chunk));
                        if(onChunkLoadedCallback != null) {
                            onChunkLoadedCallback.accept(null);
                        }
                    }
                }
            }
        }

        chunkCache.keySet().removeIf(chunkPos -> {
            int dx = Math.abs(playerChunkPos.x - chunkPos.x);
            int dz = Math.abs(playerChunkPos.z - chunkPos.z);
            return dx > renderDistance + 1 || dz > renderDistance + 1;
        });
    }

    /**
     * 核心感知方法：获取一个位置的状态，供AI决策使用。
     * @param pos 目标位置
     * @return BlockStatus 状态
     */
    public BlockStatus getBlockStatus(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        CachedChunk cachedChunk = chunkCache.get(chunkPos);

        if (cachedChunk == null || pos.getY() >= 320 || pos.getY() <= -64) {
            return BlockStatus.UNKNOWN;
        }

        BlockState blockState = cachedChunk.getBlockState(pos);
        if (blockState == null) return BlockStatus.UNKNOWN;

        FluidState fluidState = blockState.getFluidState();

        if (blockState.blocksMovement() || !fluidState.isEmpty() || blockState.isOf(Blocks.COBWEB)) {
            return BlockStatus.OBSTACLE;
        }

        return BlockStatus.TRAVERSABLE;
    }
}