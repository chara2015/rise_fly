// 文件路径: rise_fly/client/Rise_flyClient.java
package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.config.ConfigManager;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.FlightMode;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.DebugUtils;
import rise_fly.client.util.Render;

import java.util.List;

public class Rise_flyClient implements ClientModInitializer {

    private static Rise_flyClient INSTANCE;

    // --- 【修复】为跨线程访问的变量添加 volatile 关键字确保线程安全 ---
    private volatile Vec3d finalTargetPosition;
    private volatile Vec3d currentSegmentTarget;
    private volatile List<Vec3d> currentPath;
    private volatile boolean isLongRangeFlight = false;
    private volatile boolean isCalculatingNextSegment = false;

    // --- 寻路重试逻辑的常量 ---
    private static final int INITIAL_SCAN_VERTICAL_RADIUS = 30;
    private static final int MAX_PATHFINDING_ATTEMPTS = 4;
    private static final int SCAN_CENTER_Y = -1;
    private static final int SCAN_RADIUS_INCREMENT = 30;

    // --- 分段路径逻辑的常量 ---
    private static final double PATH_SEGMENT_LENGTH = 800.0;
    private static final double NEXT_SEGMENT_TRIGGER_DISTANCE = 200.0;

    public Rise_flyClient() {
        INSTANCE = this;
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
            handleLongRangeFlightTick(client);
        });

        Render.register();
    }

    public void startFlight(Vec3d target) {
        stopFlight(); // 开始新任务前先重置状态
        this.finalTargetPosition = target;

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        double distanceToFinalTarget = player.getPos().distanceTo(target);

        if (distanceToFinalTarget > PATH_SEGMENT_LENGTH) {
            this.isLongRangeFlight = true;
            DebugUtils.log("§d检测到长途飞行，启用分段路径模式。");
            this.currentSegmentTarget = calculateNextSegmentTarget(player.getPos());
            executePathfindingTask(player.getPos(), this.currentSegmentTarget, true);
        } else {
            this.isLongRangeFlight = false;
            executePathfindingTask(player.getPos(), this.finalTargetPosition, true);
        }
    }

    private void handleLongRangeFlightTick(MinecraftClient client) {
        if (!isLongRangeFlight || isCalculatingNextSegment || !FlightControl.INSTANCE.isEnabled() || client.player == null || currentSegmentTarget == null) {
            return;
        }

        if (client.player.getPos().distanceTo(currentSegmentTarget) < NEXT_SEGMENT_TRIGGER_DISTANCE) {
            isCalculatingNextSegment = true; // 加锁，防止重复计算
            DebugUtils.log("§d接近路径段终点，开始异步计算下一段路径...");

            Vec3d nextStartPos = currentSegmentTarget;
            this.currentSegmentTarget = calculateNextSegmentTarget(nextStartPos);

            if (this.currentSegmentTarget.equals(this.finalTargetPosition)) {
                this.isLongRangeFlight = false;
                DebugUtils.log("§d已规划至最后一段路径。");
            }

            executePathfindingTask(nextStartPos, this.currentSegmentTarget, false);
        }
    }

    private Vec3d calculateNextSegmentTarget(Vec3d fromPos) {
        double remainingDistance = fromPos.distanceTo(this.finalTargetPosition);
        if (remainingDistance <= PATH_SEGMENT_LENGTH) {
            return this.finalTargetPosition;
        }
        Vec3d direction = finalTargetPosition.subtract(fromPos).normalize();
        return fromPos.add(direction.multiply(PATH_SEGMENT_LENGTH));
    }

    public static void requestReplan() {
        if (INSTANCE == null) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        DebugUtils.log("§6收到路径重规划请求...");

        // 【修复】重规划时应优先飞向当前分段目标，而不是最终目标，以遵守分段逻辑
        final Vec3d replanTarget = INSTANCE.isLongRangeFlight && INSTANCE.currentSegmentTarget != null
                ? INSTANCE.currentSegmentTarget
                : INSTANCE.finalTargetPosition;

        if (replanTarget == null) {
            DebugUtils.log("§c重规划失败：无有效目标。");
            INSTANCE.stopFlight();
            return;
        }

        // 【修复】重规划时不应该禁用长途模式
        // INSTANCE.isLongRangeFlight = false;
        INSTANCE.executePathfindingTask(player.getPos(), replanTarget, false);
    }

    private void executePathfindingTask(Vec3d startVec, Vec3d targetVec, boolean isInitialFlight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (isInitialFlight && client.player != null) {
            client.player.sendMessage(Text.literal("§e[RiseFly] 开始路径规划..."), true);
        }

        new Thread(() -> {
            List<Vec3d> path = null;
            for (int attempt = 0; attempt < MAX_PATHFINDING_ATTEMPTS; attempt++) {
                int verticalRadius = INITIAL_SCAN_VERTICAL_RADIUS + (SCAN_RADIUS_INCREMENT * attempt);
                int centerY = (SCAN_CENTER_Y == -1) ? (int) startVec.y : SCAN_CENTER_Y;

                if (attempt > 0) {
                    DebugUtils.log(String.format("§6路径规划失败，开始第 %d/%d 次重试，扩大扫描范围至 Y:%d ± %d...",
                            attempt, MAX_PATHFINDING_ATTEMPTS - 1, centerY, verticalRadius));

                    ChunkPos startChunk = new ChunkPos(BlockPos.ofFloored(startVec));
                    // 【改进】计算路径中点，并在中点区域也扩大扫描，以解决路径中途的未知区域问题
                    Vec3d midPoint = startVec.lerp(targetVec, 0.5);
                    ChunkPos midChunk = new ChunkPos(BlockPos.ofFloored(midPoint));

                    // 扫描起点周围 (3x3 chunks)
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            WorldCache.INSTANCE.expandScanYRange(new ChunkPos(startChunk.x + x, startChunk.z + z), centerY, verticalRadius);
                        }
                    }

                    // 【新增】扫描中点周围 (3x3 chunks)
                    DebugUtils.log(String.format("§6同时扩大中点区域 %s 的扫描范围...", midChunk.toString()));
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            WorldCache.INSTANCE.expandScanYRange(new ChunkPos(midChunk.x + x, midChunk.z + z), centerY, verticalRadius);
                        }
                    }
                }

                path = (getFlightMode() == FlightMode.X_MODE)
                        ? Pathfinder.INSTANCE.findPathXMode(startVec, targetVec)
                        : Pathfinder.INSTANCE.findPath(startVec, targetVec);

                if (path != null && !path.isEmpty()) {
                    DebugUtils.log("§a在第 " + (attempt + 1) + " 次尝试中成功找到路径!");
                    break;
                }
            }

            final List<Vec3d> finalPath = path;
            client.execute(() -> {
                ClientPlayerEntity player = client.player;
                if (player == null) return;

                if (finalPath != null && !finalPath.isEmpty()) {
                    this.currentPath = finalPath;
                    if (isInitialFlight) {
                        player.sendMessage(Text.literal("[RiseFly] §a路径已生成，开始飞行！"), true);
                        FlightControl.INSTANCE.setEnabled(true);
                        FlightControl.INSTANCE.setPath(this.currentPath);
                    } else {
                        player.sendMessage(Text.literal("[RiseFly] §a路径已成功修正！"), true);
                        FlightControl.INSTANCE.updatePathToSegment(this.currentPath);
                        this.isCalculatingNextSegment = false; // 解锁，允许计算再下一段
                    }
                } else {
                    player.sendMessage(Text.literal("[RiseFly] " + (isInitialFlight ? "§c路径规划失败！" : "§c路径修正失败！")), false);
                    if (isInitialFlight) {
                        stopFlight();
                    }
                    this.isCalculatingNextSegment = false; // 失败也要解锁
                }
            });
        }).start();
    }

    public void stopFlight() {
        FlightControl.INSTANCE.setEnabled(false);
        Pathfinder.INSTANCE.clearCosts();
        this.currentPath = null;
        this.finalTargetPosition = null;
        this.currentSegmentTarget = null;
        this.isLongRangeFlight = false;
        this.isCalculatingNextSegment = false;
    }

    public static FlightMode getFlightMode() {
        return FlightMode.NORMAL;
    }
}