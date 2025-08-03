package rise_fly.client.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class AimingUtils {
    public static void aimAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d eyePos = player.getEyePos();
        Vec3d direction = target.subtract(eyePos).normalize();
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0;
        double pitch = -Math.toDegrees(Math.asin(direction.y));
        player.setYaw((float) yaw);
        player.setPitch((float) pitch);
    }
}