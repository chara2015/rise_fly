package rise_fly.client.flight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public class FlightControl {
    public static final FlightControl INSTANCE = new FlightControl();
    private boolean enabled = false;
    private boolean debugEnabled = false;
    private List<ChunkPos> currentPath = null;
    private int pathIndex = 0;

    public static final double TARGET_SPEED_PER_TICK = 2.87;
    public static final double ASCEND_SPEED = 0.5;
    public static final double ANTI_GRAVITY_SPEED = 0.045;

    private FlightControl() {}

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!this.enabled) {
            this.currentPath = null;
            this.pathIndex = 0;
        }
    }

    public boolean toggleDebug() {
        this.debugEnabled = !this.debugEnabled;
        return this.debugEnabled;
    }

    public void setPath(List<ChunkPos> path) {
        this.currentPath = path;
        this.pathIndex = 0;
    }

    public void onClientTick(MinecraftClient client) {
        if (!this.enabled || client.player == null || !client.player.isFallFlying()) {
            return;
        }
        ClientPlayerEntity player = client.player;

        Vec3d direction;
        if (currentPath != null && !currentPath.isEmpty()) {
            if (pathIndex >= currentPath.size()) {
                setEnabled(false);
                return;
            }
            ChunkPos nextChunk = currentPath.get(pathIndex);
            Vec3d nextTarget = new Vec3d(nextChunk.getCenterX(), 200, nextChunk.getCenterZ());
            direction = nextTarget.subtract(player.getPos()).normalize();
            if (player.getPos().distanceTo(nextTarget) < 32) {
                pathIndex++;
            }
        } else {
            direction = Vec3d.fromPolar(0, player.getYaw()).normalize();
        }

        Vec3d targetHorizontalVelocity = new Vec3d(direction.x, 0, direction.z).normalize().multiply(TARGET_SPEED_PER_TICK);
        double verticalSpeed = ANTI_GRAVITY_SPEED;

        boolean isMoving = client.options.forwardKey.isPressed() || client.options.backKey.isPressed() ||
                client.options.leftKey.isPressed() || client.options.rightKey.isPressed();
        if (client.options.jumpKey.isPressed() && isMoving) {
            verticalSpeed = ASCEND_SPEED;
        }

        player.setVelocity(targetHorizontalVelocity.x, verticalSpeed, targetHorizontalVelocity.z);
    }
}