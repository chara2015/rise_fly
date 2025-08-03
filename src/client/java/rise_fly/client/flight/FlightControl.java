package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.AimingUtils;
import java.util.List;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();
    private boolean enabled = false;
    private List<Vec3d> currentPath = null;
    private int pathIndex = 0;

    // 卡住检测变量
    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

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

    public void setPath(List<Vec3d> path) {
        this.currentPath = path;
        this.pathIndex = 0;
        this.ticksStuck = 0;
        this.lastCheckPosition = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPos() : Vec3d.ZERO;
    }

    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying() || this.currentPath == null || this.currentPath.isEmpty()) {
            return;
        }
        ClientPlayerEntity player = client.player;

        // 1. 检查是否已到达路径终点
        if (pathIndex >= currentPath.size()) {
            setEnabled(false);
            return;
        }

        // 2. 获取下一个路径点并调整玩家朝向
        Vec3d nextTarget = currentPath.get(pathIndex);
        AimingUtils.aimAt(player, nextTarget);

        // 3. 检查是否应该切换到下一个路径点
        double distance = player.getPos().distanceTo(nextTarget);
        if(nextTarget.y < player.getY()) {
            distance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        }
        if (distance < 16) {
            pathIndex++;
        }

        // 4. 卡住检测 (仍然需要，因为即使方向正确，也可能被墙挡住)
        tickCounter++;
        if (tickCounter > 20) {
            tickCounter = 0;
            if (player.getPos().distanceTo(lastCheckPosition) < 2.0) {
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
}