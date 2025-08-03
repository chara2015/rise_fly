package rise_fly.client.cache;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldCache {
    public static final WorldCache INSTANCE = new WorldCache();
    private final Map<ChunkPos, CachedChunk> chunkCache = new ConcurrentHashMap<>();

    private WorldCache() {}

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
                    }
                }
            }
        }
    }

    public boolean isSolid(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        CachedChunk cachedChunk = chunkCache.get(chunkPos);
        if (cachedChunk == null) return true;
        return cachedChunk.isSolid(pos);
    }
}