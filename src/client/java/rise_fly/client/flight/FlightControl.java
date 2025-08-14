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
            setEnabled(false);
            return;
        }

        FlightManager.resetControls();

        Vec3d nextTarget = currentPath.get(pathIndex);

        // 重新引入视角控制，使其始终对准下一个路径点
        AimingUtils.aimAt(player, nextTarget);

        double horizontalDistance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        double verticalDistance = nextTarget.y - player.getY();

        if (horizontalDistance < 16 && verticalDistance > 5) {
            executeSpiralClimb();
        } else {
            executeCruising(verticalDistance);
            this.spiralClimbTicks = 0;
        }

        if (horizontalDistance < 16) {
            DebugUtils.log("到达路径点 " + pathIndex + "/" + currentPath.size() + ", 切换到下一个点。");
            pathIndex++;
            if (pathIndex >= currentPath.size()) {
                setEnabled(false);
            }
        }

        proactiveCheckCounter++;
        if (proactiveCheckCounter > 40) {
            proactiveCheckCounter = 0;
            if (currentPath.size() > pathIndex + 2) {
                Vec3d startPoint = player.getPos();
                Vec3d endPoint = currentPath.get(pathIndex + 2);
                if (Pathfinder.INSTANCE.isTraversable(startPoint, endPoint)) {
                    DebugUtils.log("§b发现更优路径，正在前瞻性重新规划...");
                }
            }
        }

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
                ticksStuck = 0;
                Pathfinder.INSTANCE.reportBadChunk(player.getChunkPos());
                Rise_flyClient.requestReplan();
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
        FlightManager.jumpPressTicks = 2;

        if (this.spiralClimbTicks % 40 < 20) {
            FlightManager.holdLeft = true;
            FlightManager.holdRight = false;
        } else {
            FlightManager.holdLeft = false;
            FlightManager.holdRight = true;
        }
        this.spiralClimbTicks++;
    }
}