package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos; // 【修正 #2】添加缺失的 BlockPos 导入
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.config.ConfigManager;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.FlightMode;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.DebugUtils;

import java.util.List;

public class Rise_flyClient implements ClientModInitializer {

    private static Rise_flyClient INSTANCE;
    private Vec3d finalTargetPosition;
    private List<Vec3d> currentPath;

    // --- 寻路重试逻辑的常量 ---
    /**
     * 【修正 #1】在这里定义初始扫描半径常量
     */
    private static final int INITIAL_SCAN_VERTICAL_RADIUS = 30;
    /**
     * 最大寻路尝试次数。
     */
    private static final int MAX_PATHFINDING_ATTEMPTS = 4;
    /**
     * 每次重试时，扫描半径的中心Y坐标。
     * -1 表示使用玩家当前的Y坐标。
     */
    private static final int SCAN_CENTER_Y = -1;
    /**
     * 每次重试时，扫描范围扩大的半径增量。
     */
    private static final int SCAN_RADIUS_INCREMENT = 30;


    public Rise_flyClient() {
        INSTANCE = this;
    }

    public static void setFlightMode(FlightMode mode) {
        // ...
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ConfigManager.loadConfig();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                CommandManager.register(dispatcher, this));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WorldCache.INSTANCE.onTick(client);
            FlightControl.INSTANCE.onClientTick(client);
        });
    }

    /**
     * 开始一次全新的飞行任务。
     * @param target 最终目标点
     */
    public void startFlight(Vec3d target) {
        this.finalTargetPosition = target;
        Pathfinder.INSTANCE.clearCosts();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        // 触发带重试逻辑的路径规划任务
        executePathfindingTask(player.getPos(), this.finalTargetPosition, true);
    }

    /**
     * 由外部（如FlightControl）请求的一次路径重规划。
     */
    public static void requestReplan() {
        if (INSTANCE == null) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        DebugUtils.log("§6收到路径重规划请求...");
        // 同样触发带重试逻辑的路径规划任务
        INSTANCE.executePathfindingTask(player.getPos(), INSTANCE.finalTargetPosition, false);
    }

    /**
     * 【核心重构】执行包含“扫描-重试”循环的异步路径规划任务。
     * @param startVec 规划的起始点
     * @param targetVec 规划的目标点
     * @param isInitialFlight 这是否是任务的第一次规划
     */
    private void executePathfindingTask(Vec3d startVec, Vec3d targetVec, boolean isInitialFlight) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (isInitialFlight && client.player != null) {
            client.player.sendMessage(Text.literal("§e[RiseFly] 开始路径规划..."), true);
        }

        new Thread(() -> {
            List<Vec3d> path = null;

            for (int attempt = 0; attempt < MAX_PATHFINDING_ATTEMPTS; attempt++) {
                // 1. 计算本次尝试的扫描半径和中心
                int verticalRadius = (INITIAL_SCAN_VERTICAL_RADIUS) + (SCAN_RADIUS_INCREMENT * attempt);
                int centerY = (SCAN_CENTER_Y == -1) ? (int)startVec.y : SCAN_CENTER_Y;

                if (attempt > 0) {
                    DebugUtils.log(String.format("§6路径规划失败，开始第 %d/%d 次重试，扩大扫描范围至 Y:%d ± %d...",
                            attempt, MAX_PATHFINDING_ATTEMPTS - 1, centerY, verticalRadius));
                    // 2. 扩大扫描范围 (仅在重试时)
                    // 为简单起见，我们扩大玩家周围3x3个区块的扫描范围
                    ChunkPos playerChunk = new ChunkPos(BlockPos.ofFloored(startVec));
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            WorldCache.INSTANCE.expandScanYRange(new ChunkPos(playerChunk.x + x, playerChunk.z + z), centerY, verticalRadius);
                        }
                    }
                }

                // 3. 尝试寻路
                if (getFlightMode() == FlightMode.X_MODE) {
                    path = Pathfinder.INSTANCE.findPathXMode(startVec, targetVec);
                } else {
                    path = Pathfinder.INSTANCE.findPath(startVec, targetVec);
                }

                // 4. 检查结果
                if (path != null && !path.isEmpty()) {
                    DebugUtils.log("§a在第 " + (attempt + 1) + " 次尝试中成功找到路径!");
                    break; // 寻路成功，跳出循环
                }
            }

            // 5. 将最终结果交还给主线程处理
            final List<Vec3d> finalPath = path;
            client.execute(() -> {
                ClientPlayerEntity player = client.player;
                if (player == null) return; // 玩家可能已退出

                if (finalPath != null && !finalPath.isEmpty()) {
                    this.currentPath = finalPath;
                    String message = isInitialFlight ? "§a路径已生成，开始飞行！" : "§a路径已成功修正！";
                    player.sendMessage(Text.literal("[RiseFly] " + message), true);

                    if (isInitialFlight) {
                        FlightControl.INSTANCE.setEnabled(true);
                        FlightControl.INSTANCE.setPath(this.currentPath);
                    } else {
                        FlightControl.INSTANCE.updatePathToSegment(this.currentPath);
                    }
                } else {
                    String message = isInitialFlight ? "§c路径规划失败，找不到可用路径！" : "§c路径修正失败！";
                    player.sendMessage(Text.literal("[RiseFly] " + message), false);
                    if (isInitialFlight) {
                        stopFlight();
                    }
                }
            });
        }).start();
    }

    public void stopFlight() {
        FlightControl.INSTANCE.setEnabled(false);
        Pathfinder.INSTANCE.clearCosts();
        this.currentPath = null;
    }

    public static FlightMode getFlightMode() {
        // 这是一个示例，你需要根据你的逻辑实现它
        return FlightMode.NORMAL;
    }
}