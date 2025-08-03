package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.pathing.Pathfinder;

import java.util.List;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();
    private boolean enabled = false;
    private boolean debugEnabled = false;
    private List<ChunkPos> currentPath = null;
    private int pathIndex = 0;

    // 卡住检测变量
    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

    // 飞行参数
    public static final double TARGET_SPEED_PER_TICK = 2.87;
    public static final double ASCEND_SPEED = 0.5;
    public static final double ANTI_GRAVITY_SPEED = 0.045;

    // 避障参数
    private static final double AVOIDANCE_DISTANCE = 32;
    private static final double AVOIDANCE_STRENGTH = 1.5;

    private FlightControl() {}

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!this.enabled) {
            this.currentPath = null;
            this.pathIndex = 0;
            this.ticksStuck = 0;
            this.lastCheckPosition = Vec3d.ZERO;
        }
    }

    public boolean toggleDebug() {
        this.debugEnabled = !this.debugEnabled;
        return this.debugEnabled;
    }

    public void setPath(List<ChunkPos> path) {
        this.currentPath = path;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.lastCheckPosition = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPos() : Vec3d.ZERO;
    }

    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying()) {
            return;
        }
        ClientPlayerEntity player = client.player;

        // 1. 计算主方向
        Vec3d primaryDirection;
        if (currentPath != null && !currentPath.isEmpty()) {
            if (pathIndex >= currentPath.size()) {
                setEnabled(false); // 路径走完，停止
                return;
            }
            ChunkPos nextChunk = currentPath.get(pathIndex);
            Vec3d nextTarget = new Vec3d(nextChunk.getCenterX(), 200, nextChunk.getCenterZ());
            primaryDirection = nextTarget.subtract(player.getPos()).normalize();
            if (player.getPos().distanceTo(nextTarget) < 48) {
                pathIndex++;
            }
        } else {
            // 没有路径时，使用玩家视线方向作为主方向
            primaryDirection = Vec3d.fromPolar(0, player.getYaw()).normalize();
        }

        // 2. 计算避障修正向量
        Vec3d avoidanceVector = calculateAvoidanceVector(player, primaryDirection);

        // 3. 合成最终方向
        Vec3d finalDirection = primaryDirection.add(avoidanceVector).normalize();

        // 4. 计算并应用速度
        Vec3d targetHorizontalVelocity = new Vec3d(finalDirection.x, 0, finalDirection.z).normalize().multiply(TARGET_SPEED_PER_TICK);
        double verticalSpeed = ANTI_GRAVITY_SPEED;

        if(avoidanceVector.y > 0) {
            verticalSpeed += avoidanceVector.y * TARGET_SPEED_PER_TICK;
        }

        boolean isMoving = client.options.forwardKey.isPressed() || client.options.backKey.isPressed() ||
                client.options.leftKey.isPressed() || client.options.rightKey.isPressed();
        if (client.options.jumpKey.isPressed() && isMoving) {
            verticalSpeed = ASCEND_SPEED;
        }

        player.setVelocity(targetHorizontalVelocity.x, verticalSpeed, targetHorizontalVelocity.z);

        // 5. 卡住检测和重规划逻辑
        tickCounter++;
        if (tickCounter > 20) { // 每秒检测一次
            tickCounter = 0;
            // 只有在自动寻路时才检测
            if (currentPath != null && player.getPos().distanceTo(lastCheckPosition) < 2.0) {
                ticksStuck++;
            } else {
                ticksStuck = 0;
            }
            lastCheckPosition = player.getPos();

            if (ticksStuck > 3) {
                ticksStuck = 0;
                Pathfinder.INSTANCE.reportBadChunk(player.getChunkPos());
                Rise_flyClient.requestReplan();
            }
        }
    }

    private Vec3d calculateAvoidanceVector(ClientPlayerEntity player, Vec3d primaryDirection) {
        Vec3d totalAvoidance = Vec3d.ZERO;
        Vec3d playerPos = player.getPos();

        Vec3d[] feelers = {
                primaryDirection,
                rotate(primaryDirection, 25, 0),
                rotate(primaryDirection, -25, 0),
                rotate(primaryDirection, 0, 20),
                rotate(primaryDirection, 0, -15)
        };

        for (Vec3d feeler : feelers) {
            Vec3d samplePos = playerPos.add(feeler.multiply(AVOIDANCE_DISTANCE));
            if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(samplePos))) {
                totalAvoidance = totalAvoidance.add(feeler.multiply(-AVOIDANCE_STRENGTH));
            }
        }

        return totalAvoidance;
    }

    private Vec3d rotate(Vec3d vec, float yaw, float pitch) {
        return vec.rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(-pitch));
    }
}