package rise_fly.client.cache;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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

    // 初始扫描的垂直半径
    private static final int INITIAL_SCAN_VERTICAL_RADIUS = 30;

    private WorldCache() {}

    public void setOnChunkLoadedCallback(Consumer<Void> callback) {
        this.onChunkLoadedCallback = callback;
    }

    /**
     * 在每个客户端tick上调用，用于加载和初步扫描新进入视野的区块。
     */
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            if (!chunkCache.isEmpty()) chunkCache.clear();
            return;
        }

        int renderDistance = client.options.getClampedViewDistance();
        ChunkPos playerChunkPos = player.getChunkPos();
        int playerY = player.getBlockY();

        // 遍历玩家视野范围内的所有区块
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos currentChunkPos = new ChunkPos(playerChunkPos.x + x, playerChunkPos.z + z);
                // 如果区块尚未被缓存，则进行加载和初始扫描
                if (!chunkCache.containsKey(currentChunkPos)) {
                    WorldChunk chunk = world.getChunk(currentChunkPos.x, currentChunkPos.z);
                    if (chunk != null) {
                        CachedChunk newCachedChunk = new CachedChunk();
                        // 【核心变更】只扫描玩家当前高度上下30格的范围
                        newCachedChunk.scanYRange(chunk, playerY - INITIAL_SCAN_VERTICAL_RADIUS, playerY + INITIAL_SCAN_VERTICAL_RADIUS);
                        chunkCache.put(currentChunkPos, newCachedChunk);

                        if (onChunkLoadedCallback != null) {
                            onChunkLoadedCallback.accept(null);
                        }
                    }
                }
            }
        }

        // 清理逻辑：移除距离玩家太远的区块，防止内存泄漏
        int unloadDistance = renderDistance + 2;
        chunkCache.keySet().removeIf(chunkPos -> {
            int dx = playerChunkPos.x - chunkPos.x;
            int dz = playerChunkPos.z - chunkPos.z;
            return (dx * dx + dz * dz) > (unloadDistance * unloadDistance);
        });
    }

    /**
     * 【新方法】为指定的区块扩大Y轴扫描范围。
     * 这是实现“按需扫描”策略的关键接口。
     * @param chunkPos 要扩大扫描的区块位置
     * @param centerY 扫描范围的中心Y坐标
     * @param verticalRadius 扫描的垂直半径
     */
    public void expandScanYRange(ChunkPos chunkPos, int centerY, int verticalRadius) {
        CachedChunk cachedChunk = chunkCache.get(chunkPos);
        ClientWorld world = MinecraftClient.getInstance().world;
        if (cachedChunk != null && world != null) {
            WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                cachedChunk.scanYRange(chunk, centerY - verticalRadius, centerY + verticalRadius);
            }
        }
    }


    /**
     * 获取指定方块位置的状态。
     * @param pos 方块位置
     * @return 方块状态
     */
    public BlockStatus getBlockStatus(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        CachedChunk cachedChunk = chunkCache.get(chunkPos);
        if (cachedChunk == null) {
            // 如果连区块缓存对象都不存在，说明区块完全未知
            return BlockStatus.UNKNOWN_CHUNK;
        }
        // 否则，查询具体的方块状态 (SOLID, AIR, 或 UNSCANNED_Y)
        return cachedChunk.getBlockStatus(pos);
    }

    /**
     * 检查一个方块是否为固体。
     * 只有当方块状态明确为SOLID时才返回true。
     * @param pos 方块位置
     * @return 如果是固体方块则为true，否则为false
     */
    public boolean isSolid(BlockPos pos) {
        return getBlockStatus(pos) == BlockStatus.SOLID;
    }
}