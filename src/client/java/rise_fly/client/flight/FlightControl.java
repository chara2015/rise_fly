// 文件路径: rise_fly/client/flight/FlightControl.java
package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.cache.BlockStatus;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.util.AimingUtils;
import rise_fly.client.util.DebugUtils;

import java.util.List;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();

    private enum State {
        FOLLOWING_PATH,
        AVOIDING_OBSTACLE,
        RETREATING
    }
    private enum Maneuver { NONE, STRAFE_LEFT, STRAFE_RIGHT, HALT }

    private boolean enabled = false;
    private List<Vec3d> currentPath = null;
    private int pathIndex = 0;
    private State currentState = State.FOLLOWING_PATH;
    private Maneuver currentManeuver = Maneuver.NONE;
    private int avoidanceStuckTicks = 0;

    // 【新增】航线恢复计时器，用于在规避后提供缓冲
    private int regainCourseTicks = 0;

    private int retreatTicks = 0;
    private int spiralClimbTicks = 0;
    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

    private FlightControl() {}

    public void setEnabled(boolean enabled) { this.enabled = enabled; if (!this.enabled) { reset(); } }
    private void reset() {
        FlightManager.resetControls();
        AimingUtils.resetAim();
        this.currentPath = null;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.lastCheckPosition = Vec3d.ZERO;
        this.spiralClimbTicks = 0;
        this.currentState = State.FOLLOWING_PATH;
        this.currentManeuver = Maneuver.NONE;
        this.avoidanceStuckTicks = 0;
        this.regainCourseTicks = 0; // 重置计时器
    }

    public boolean isEnabled() { return enabled; }
    public List<Vec3d> getCurrentPath() { return currentPath; }
    public void setPath(List<Vec3d> path) { this.currentPath = path; this.pathIndex = 0; this.ticksStuck = 0; this.spiralClimbTicks = 0; this.currentState = State.FOLLOWING_PATH; this.lastCheckPosition = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPos() : Vec3d.ZERO; }
    public void updatePathToSegment(List<Vec3d> newPath) { this.currentPath = newPath; this.pathIndex = 0; this.currentState = State.FOLLOWING_PATH; DebugUtils.log("§a路径已无缝切换，继续飞行。"); }


    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying()) {
            if (enabled) FlightManager.resetControls();
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
            case RETREATING:
                executeRetreat(player);
                break;
        }
        handleStuckDetection(player);
    }

    private void executePathFollowing(ClientPlayerEntity player) {
        // 【核心修复】在正常飞行前，先处理“航线恢复”缓冲阶段
        if (regainCourseTicks > 0) {
            regainCourseTicks--;
            FlightManager.holdForward = true; // 保持向前直飞
            // 在此期间，不进行朝向调整，也不进行障碍扫描，以确保拉开距离
            DebugUtils.log("§b拉开安全距离... (" + regainCourseTicks + ")");
            return;
        }

        Maneuver requiredManeuver = proactiveScan(player);
        if (requiredManeuver != Maneuver.NONE) {
            this.currentManeuver = requiredManeuver;
            if (this.currentManeuver == Maneuver.HALT) {
                DebugUtils.log("§c前方是死路，进入后退程序...");
                this.currentState = State.RETREATING;
                this.retreatTicks = 40;
            } else {
                DebugUtils.log("§6检测到障碍，进入连续规避状态...");
                this.currentState = State.AVOIDING_OBSTACLE;
                this.avoidanceStuckTicks = 0;
            }
            executeObstacleAvoidance(player);
            return;
        }

        if (this.currentPath == null || pathIndex >= currentPath.size()) {
            setEnabled(false);
            return;
        }
        Vec3d nextTarget = currentPath.get(pathIndex);
        AimingUtils.aimAt(nextTarget);
        double horizontalDistance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        double verticalDistance = nextTarget.y - player.getY();

        if (horizontalDistance < 16 && verticalDistance > 5) {
            executeSpiralClimb(player);
        } else {
            executeCruising(player, verticalDistance);
            this.spiralClimbTicks = 0;
        }
        if (player.getPos().distanceTo(nextTarget) < 10) {
            pathIndex++;
        }
    }

    private void executeObstacleAvoidance(ClientPlayerEntity player) {
        final double SCAN_DISTANCE = 20.0;
        Vec3d playerPos = player.getEyePos();
        Vec3d lookVec = player.getRotationVector();
        Vec3d forwardCheckPos = playerPos.add(lookVec.multiply(SCAN_DISTANCE));

        avoidanceStuckTicks++;
        if (avoidanceStuckTicks > 200) {
            DebugUtils.log("§c在规避状态卡死过久，强制后退并重规划！");
            this.currentState = State.RETREATING;
            this.retreatTicks = 40;
            return;
        }

        // 1. 持续检查正前方是否已经安全
        if (WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(forwardCheckPos)) != BlockStatus.SOLID) {
            DebugUtils.log("§a前方路径已清空，恢复正常航线。");
            this.currentState = State.FOLLOWING_PATH;
            this.currentManeuver = Maneuver.NONE;
            // 【核心修复】启动航线恢复计时器，向前直飞20ticks（1秒）
            this.regainCourseTicks = 20;
            return;
        }

        // 2. 如果正前方仍然有危险，则继续执行“贴边”动作
        FlightManager.holdForward = true;
        if (currentManeuver == Maneuver.STRAFE_LEFT) {
            FlightManager.holdLeft = true;
        } else if (currentManeuver == Maneuver.STRAFE_RIGHT) {
            FlightManager.holdRight = true;
        }

        BlockPos headPos = player.getBlockPos().up(2);
        boolean ceilingBlocked = WorldCache.INSTANCE.isSolid(headPos) || WorldCache.INSTANCE.isSolid(headPos.up(1));
        if (!ceilingBlocked) {
            FlightManager.jumpPressTicks = 2;
        }
    }

    private Maneuver proactiveScan(ClientPlayerEntity player) {
        final double SCAN_DISTANCE = 20.0;
        Vec3d playerPos = player.getEyePos();
        Vec3d lookVec = player.getRotationVector();
        Vec3d frontCheckPos = playerPos.add(lookVec.multiply(SCAN_DISTANCE));
        if (WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(frontCheckPos)) != BlockStatus.SOLID) {
            return Maneuver.NONE;
        }
        final int scanAngleStep = 5;
        final int scanAngleMax = 60;
        Integer firstClearAngleLeft = null;
        Integer firstClearAngleRight = null;
        for (int angle = scanAngleStep; angle <= scanAngleMax; angle += scanAngleStep) {
            Vec3d scanVec = lookVec.rotateY((float) Math.toRadians(angle));
            Vec3d checkPos = playerPos.add(scanVec.multiply(SCAN_DISTANCE));
            if (WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(checkPos)) != BlockStatus.SOLID) {
                firstClearAngleLeft = angle;
                break;
            }
        }
        for (int angle = -scanAngleStep; angle >= -scanAngleMax; angle -= scanAngleStep) {
            Vec3d scanVec = lookVec.rotateY((float) Math.toRadians(angle));
            Vec3d checkPos = playerPos.add(scanVec.multiply(SCAN_DISTANCE));
            if (WorldCache.INSTANCE.getBlockStatus(BlockPos.ofFloored(checkPos)) != BlockStatus.SOLID) {
                firstClearAngleRight = angle;
                break;
            }
        }
        if (firstClearAngleLeft == null && firstClearAngleRight == null) {
            return Maneuver.HALT;
        }
        if (firstClearAngleLeft != null && (firstClearAngleRight == null || firstClearAngleLeft <= -firstClearAngleRight)) {
            return Maneuver.STRAFE_LEFT;
        } else {
            return Maneuver.STRAFE_RIGHT;
        }
    }

    // [其他方法保持不变]
    private void executeRetreat(ClientPlayerEntity player) { if (retreatTicks > 0) { FlightManager.holdBack = true; FlightManager.jumpPressTicks = 2; retreatTicks--; } else { DebugUtils.log("§e后退阶段完成，请求路径重规划..."); this.currentState = State.FOLLOWING_PATH; Rise_flyClient.requestReplan(); } }
    private void handleStuckDetection(ClientPlayerEntity player) { tickCounter++; if (tickCounter > 20) { tickCounter = 0; if (player.getPos().distanceTo(lastCheckPosition) < 1.0) { ticksStuck++; } else { ticksStuck = 0; } lastCheckPosition = player.getPos(); if (ticksStuck > 5) { DebugUtils.log("§c检测到卡死，强制进入后退模式以脱困！"); ticksStuck = 0; currentState = State.RETREATING; retreatTicks = 40; } } }
    private void executeCruising(ClientPlayerEntity player, double verticalDistance) { FlightManager.holdForward = true; BlockPos headPos = player.getBlockPos().up(2); boolean ceilingBlocked = WorldCache.INSTANCE.isSolid(headPos) || WorldCache.INSTANCE.isSolid(headPos.up(1)); if (verticalDistance > 1.5) { if (!ceilingBlocked) { FlightManager.jumpPressTicks = 2; } } else if (verticalDistance < -1.5) { FlightManager.sneakPressTicks = 2; } }
    private void executeSpiralClimb(ClientPlayerEntity player) { FlightManager.holdForward = false; BlockPos headPos = player.getBlockPos().up(2); boolean ceilingBlocked = WorldCache.INSTANCE.isSolid(headPos) || WorldCache.INSTANCE.isSolid(headPos.up(1)); if (!ceilingBlocked) { FlightManager.jumpPressTicks = 1; } if (this.spiralClimbTicks % 2 == 0) { FlightManager.holdLeft = true; FlightManager.holdRight = false; } else { FlightManager.holdLeft = false; FlightManager.holdRight = true; } this.spiralClimbTicks++; }
}