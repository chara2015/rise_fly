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

    private int tickCounter = 0;
    private Vec3d lastCheckPosition = Vec3d.ZERO;
    private int ticksStuck = 0;

    // 特殊机动：螺旋上升计时器
    private int spiralClimbTicks = 0;

    private FlightControl() {}

    public boolean isEnabled() {
        return enabled;
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

        // 重置按键状态，由下面的逻辑决定本tick是否按下
        FlightManager.resetControls();

        Vec3d nextTarget = currentPath.get(pathIndex);
        AimingUtils.aimAt(player, nextTarget);

        // --- 核心决策逻辑 ---
        double horizontalDistance = player.getPos().multiply(1, 0, 1).distanceTo(nextTarget.multiply(1, 0, 1));
        double verticalDistance = nextTarget.y - player.getY();

        // 特殊机动：如果离目标很近但目标在很高的地方，执行螺旋上升
        if (horizontalDistance < 16 && verticalDistance > 5) {
            executeSpiralClimb();
        } else {
            // 常规巡航
            executeCruising(verticalDistance);
            this.spiralClimbTicks = 0; // 重置螺旋上升计时器
        }

        // 检查是否抵达路径点
        if (horizontalDistance < 16) {
            pathIndex++;
        }

        // 卡住检测
        tickCounter++;
        if (tickCounter > 20) {
            tickCounter = 0;
            if (player.getPos().distanceTo(lastCheckPosition) < 1.0) { // 卡住阈值改得更严格
                ticksStuck++;
            } else {
                ticksStuck = 0;
            }
            lastCheckPosition = player.getPos();
            if (ticksStuck > 4) { // 5秒卡住才重规划
                ticksStuck = 0;
                Pathfinder.INSTANCE.reportBadChunk(player.getChunkPos());
                Rise_flyClient.requestReplan();
            }
        }
    }

    private void executeCruising(double verticalDistance) {
        FlightManager.holdForward = true;

        if (verticalDistance > 1.5) {
            FlightManager.jumpPressTicks = 2; // 上升
        } else if (verticalDistance < -1.5) {
            FlightManager.sneakPressTicks = 2; // 下降
        }
    }

    private void executeSpiralClimb() {
        FlightManager.holdForward = false; // 螺旋上升时不前进
        FlightManager.jumpPressTicks = 2; // 持续按住跳跃

        // 模拟左右交替按键来实现盘旋
        if (this.spiralClimbTicks % 40 < 20) {
            // 示例代码是左右，但对于鞘翅飞行，不按前进键只转头就是盘旋
            // 这里我们保持向前看目标即可，由ElytraFly处理盘旋上升
        }
        this.spiralClimbTicks++;
    }
}