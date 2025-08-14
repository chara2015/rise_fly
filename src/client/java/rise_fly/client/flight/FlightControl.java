package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.AimingUtils;
import rise_fly.client.util.DebugUtils;
import java.util.List;
import java.util.ArrayList;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();
    private boolean enabled = false;
    private List<Vec3d> currentPath = null;
    private int pathIndex = 0;

    // 常量替换魔术数字
    private static final int TICK_CHECK_INTERVAL = 20;
    private static final double STUCK_DISTANCE_THRESHOLD = 1.0;
    private static final int STUCK_TICK_LIMIT = 4;
    private static final int PROACTIVE_CHECK_INTERVAL = 40;
    private static final double MIN_SPIRAL_CLIMB_DISTANCE = 16.0;
    private static final double MIN_VERTICAL_CLIMB_DISTANCE = 5.0;
    private static final double MIN_CRUISING_UP_DISTANCE = 1.5;
    private static final double MIN_CRUISING_DOWN_DISTANCE = -1.5;
    private static final int SPIRAL_TICK_HALFLIFE = 20;

    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

    private int proactiveCheckCounter = 0;

    private int spiralClimbTicks = 0;

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
        FlightManager.resetControls();
        if (!this.enabled) {
            this.currentPath = null;
            this.pathIndex = 0;
            this.ticksStuck = 0;
            this.lastCheckPosition = Vec3d.ZERO;
            this.spiralClimbTicks = 0;
        }
    }

    public void setPath(List<Vec3d> path) {
        this.currentPath = path;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.spiralClimbTicks = 0;
        this.lastCheckPosition = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPos() : Vec3d.ZERO;
    }

    public void updatePathToSegment(List<Vec3d> newPath) {
        this.currentPath = newPath;
        this.pathIndex = 0;
        DebugUtils.log("§a路径已无缝切换，继续飞行。");
    }

    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying() || this.currentPath == null || this.currentPath.isEmpty()) {
            if(enabled) FlightManager.resetControls();
            return;
        }
        ClientPlayerEntity player = client.player;

        if (pathIndex >= currentPath.size()) {
            // 优化：当到达终点时，停止飞行
            Rise_flyClient.INSTANCE.stopFlight();
            return;
        }

        FlightManager.resetControls();

        Vec3d nextTarget = currentPath.get(pathIndex);

        // 重新引入视角控制，使其始终对准下一个路径点
        AimingUtils.aimAt(player, nextTarget);

        double horizontalDistance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        double verticalDistance = nextTarget.y - player.getY();

        if (horizontalDistance < MIN_SPIRAL_CLIMB_DISTANCE && verticalDistance > MIN_VERTICAL_CLIMB_DISTANCE) {
            executeSpiralClimb();
        } else {
            executeCruising(verticalDistance);
            this.spiralClimbTicks = 0;
        }

        if (horizontalDistance < MIN_SPIRAL_CLIMB_DISTANCE) {
            DebugUtils.log("到达路径点 " + pathIndex + "/" + currentPath.size() + ", 切换到下一个点。");
            pathIndex++;
        }

        proactiveCheckCounter++;
        if (proactiveCheckCounter > PROACTIVE_CHECK_INTERVAL) {
            proactiveCheckCounter = 0;
            // 前瞻性检查逻辑
        }

        tickCounter++;
        if (tickCounter > TICK_CHECK_INTERVAL) {
            tickCounter = 0;
            if (player.getPos().distanceTo(lastCheckPosition) < STUCK_DISTANCE_THRESHOLD) {
                ticksStuck++;
            } else {
                ticksStuck = 0;
            }
            lastCheckPosition = player.getPos();
            if (ticksStuck > STUCK_TICK_LIMIT) {
                ticksStuck = 0;
                Pathfinder.INSTANCE.reportBadChunk(player.getChunkPos());
                Rise_flyClient.requestReplan();
            }
        }
    }

    private void executeCruising(double verticalDistance) {
        FlightManager.holdForward = true;

        if (verticalDistance > MIN_CRUISING_UP_DISTANCE) {
            FlightManager.jumpPressTicks = 2;
        } else if (verticalDistance < MIN_CRUISING_DOWN_DISTANCE) {
            FlightManager.sneakPressTicks = 2;
        }
    }

    private void executeSpiralClimb() {
        FlightManager.jumpPressTicks = 2;

        if (this.spiralClimbTicks % (SPIRAL_TICK_HALFLIFE * 2) < SPIRAL_TICK_HALFLIFE) {
            FlightManager.holdLeft = true;
            FlightManager.holdRight = false;
        } else {
            FlightManager.holdLeft = false;
            FlightManager.holdRight = true;
        }
        this.spiralClimbTicks++;
    }
}