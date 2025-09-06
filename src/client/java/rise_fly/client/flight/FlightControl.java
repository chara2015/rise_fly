package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.util.AimingUtils;
import rise_fly.client.util.DebugUtils;
import java.util.List;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();

    private enum State {
        FOLLOWING_PATH,
        AVOIDING_OBSTACLE
    }

    private boolean enabled = false;
    private List<Vec3d> currentPath = null;
    private int pathIndex = 0;
    private State currentState = State.FOLLOWING_PATH;
    private int avoidanceTicks = 0;
    private int spiralClimbTicks = 0;

    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

    private FlightControl() {}

    public boolean isEnabled() {
        return enabled;
    }

    public List<Vec3d> getCurrentPath() {
        return currentPath;
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!this.enabled) {
            reset();
        }
    }

    public void setPath(List<Vec3d> path) {
        this.currentPath = path;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.spiralClimbTicks = 0;
        this.currentState = State.FOLLOWING_PATH;
        this.lastCheckPosition = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPos() : Vec3d.ZERO;
    }

    public void updatePathToSegment(List<Vec3d> newPath) {
        this.currentPath = newPath;
        this.pathIndex = 0;
        this.currentState = State.FOLLOWING_PATH;
        DebugUtils.log("§a路径已无缝切换，继续飞行。");
    }

    private void reset() {
        FlightManager.resetControls();
        this.currentPath = null;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.lastCheckPosition = Vec3d.ZERO;
        this.spiralClimbTicks = 0;
        this.currentState = State.FOLLOWING_PATH;
    }

    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying()) {
            if(enabled) FlightManager.resetControls();
            return;
        }

        ClientPlayerEntity player = client.player;
        FlightManager.resetControls();

        switch (currentState) {
            case FOLLOWING_PATH:
                executePathFollowing(player);
                break;
            case AVOIDING_OBSTACLE:
                executeObstacleAvoidance(player);
                break;
        }

        handleStuckDetection(player);
    }

    private void executePathFollowing(ClientPlayerEntity player) {
        // --- 实时障碍物扫描 (最终修正版) ---
        Vec3d lookVec = player.getRotationVector();
        Vec3d horizontalForwardVec = new Vec3d(lookVec.x, 0, lookVec.z).normalize();

        Vec3d eyeCheckPos = player.getEyePos().add(horizontalForwardVec.multiply(2.5));
        Vec3d feetCheckPos = player.getPos().add(horizontalForwardVec.multiply(2.5));

        if (WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(eyeCheckPos)) || WorldCache.INSTANCE.isSolid(BlockPos.ofFloored(feetCheckPos))) {
            DebugUtils.log("§c[实时避障-水平] 前方发现障碍！切换到规避模式！");
            currentState = State.AVOIDING_OBSTACLE;
            avoidanceTicks = 20;
            return;
        }

        // --- 路径跟随逻辑 ---
        if (this.currentPath == null || pathIndex >= currentPath.size()) {
            DebugUtils.log("路径完成或无效，停止飞行。");
            setEnabled(false);
            return;
        }

        Vec3d nextTarget = currentPath.get(pathIndex);
        AimingUtils.aimAt(player, nextTarget);

        double horizontalDistance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        double verticalDistance = nextTarget.y - player.getY();

        if (horizontalDistance < 16 && verticalDistance > 5) {
            executeSpiralClimb();
        } else {
            executeCruising(verticalDistance);
            this.spiralClimbTicks = 0;
        }

        if (player.getPos().distanceTo(nextTarget) < 10) {
            pathIndex++;
        }
    }

    private void executeObstacleAvoidance(ClientPlayerEntity player) {
        if (avoidanceTicks > 0) {
            FlightManager.holdForward = false;
            FlightManager.jumpPressTicks = 2;
            avoidanceTicks--;
        } else {
            DebugUtils.log("§e规避机动完成，请求路径重规划...");
            Rise_flyClient.requestReplan();
            currentState = State.FOLLOWING_PATH;
        }
    }

    private void handleStuckDetection(ClientPlayerEntity player) {
        tickCounter++;
        if (tickCounter > 20) {
            tickCounter = 0;
            if (player.getPos().distanceTo(lastCheckPosition) < 1.0) {
                ticksStuck++;
            } else {
                ticksStuck = 0;
            }
            lastCheckPosition = player.getPos();
            if (ticksStuck > 4) {
                DebugUtils.log("§c检测到卡死，强制进入规避模式以脱困！");
                ticksStuck = 0;
                currentState = State.AVOIDING_OBSTACLE;
                avoidanceTicks = 20;
            }
        }
    }

    private void executeCruising(double verticalDistance) {
        FlightManager.holdForward = true;
        if (verticalDistance > 1.5) {
            FlightManager.jumpPressTicks = 2;
        } else if (verticalDistance < -1.5) {
            FlightManager.sneakPressTicks = 2;
        }
    }

    private void executeSpiralClimb() {
        FlightManager.holdForward = false;
        FlightManager.jumpPressTicks = 1;

        if (this.spiralClimbTicks % 2 == 0) {
            FlightManager.holdLeft = true;
            FlightManager.holdRight = false;
        } else {
            FlightManager.holdLeft = false;
            FlightManager.holdRight = true;
        }

        this.spiralClimbTicks++;
    }
}