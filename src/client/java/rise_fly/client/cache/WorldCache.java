package rise_fly.client.cache;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
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

        // [修复] 清理逻辑：移除距离玩家太远的区块，防止内存泄漏
        int unloadDistance = renderDistance + 2;
        chunkCache.keySet().removeIf(chunkPos -> {
            int dx = playerChunkPos.x - chunkPos.x;
            int dz = playerChunkPos.z - chunkPos.z;
            return (dx * dx + dz * dz) > (unloadDistance * unloadDistance);
        });
    }

    public BlockStatus getBlockStatus(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        CachedChunk cachedChunk = chunkCache.get(chunkPos);
        if (cachedChunk == null) {
            return BlockStatus.UNKNOWN;
        }
        return cachedChunk.isSolid(pos) ? BlockStatus.SOLID : BlockStatus.AIR;
    }

    public boolean isSolid(BlockPos pos) {
        return getBlockStatus(pos) == BlockStatus.SOLID;
    }
}