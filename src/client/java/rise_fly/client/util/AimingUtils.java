// 文件路径: rise_fly/client/util/AimingUtils.java
package rise_fly.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimingUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // --- 后台旋转状态管理 (来自 RotationManager) ---
    private static Float serverYaw = null;
    private static Float serverPitch = null;

    // =================================================================================
    // ### 公共接口 (Public API) - 你的其他模块应该只调用这部分的方法 ###
    // =================================================================================

    public static void aimAt(Vec3d target) {
        float[] rotations = getRotations(target);
        if (rotations != null) {
            setRotation(rotations[0], rotations[1]);
        }
    }

    public static void resetAim() {
        serverYaw = null;
        serverPitch = null;
    }

    // =================================================================================
    // ### 【核心修正】 - 全新的 MoveFix 逻辑 ###
    // =================================================================================
    public static float[] getCorrectedMovement(float forward, float strafe) {
        if (!isRotating() || mc.player == null) {
            return null; // 如果没有静默旋转或玩家不存在，则无需修复
        }

        float serverYaw = getServerYaw();
        float clientYaw = mc.player.getYaw();

        // 【关键修正】使用 MathHelper.wrapDegrees 来计算最短的角度差
        // 这解决了“误差90度内才有效”和“飞过头不回头”的问题
        float yawDiff = MathHelper.wrapDegrees(clientYaw - serverYaw);

        // 如果没有按键输入，则无需计算
        if (forward == 0.0f && strafe == 0.0f) {
            return new float[]{0.0f, 0.0f};
        }

        // 将移动输入转换为相对于玩家当前视角的运动向量
        double angle = Math.atan2(strafe, forward); // 注意参数顺序 atan2(y, x)
        angle = angle * (180.0 / Math.PI); // 弧度转角度
        angle -= yawDiff; // 将运动向量旋转到服务器视角的坐标系下

        // 将新的角度转换回 correctedForward 和 correctedStrafe
        double radian = angle * (Math.PI / 180.0);
        float correctedForward = (float) Math.cos(radian);
        float correctedStrafe = (float) Math.sin(radian);

        return new float[]{correctedForward, correctedStrafe};
    }


    // =================================================================================
    // ### 后台管理方法 (Backend Management) - 由 Mixin 在游戏循环中调用 ###
    // =================================================================================

    public static void onTick() {
        // onTick不再需要做任何事，因为旋转状态由aimAt和resetAim管理
    }

    // =================================================================================
    // ### 内部实现 (Internal Implementation) - 以下为私有方法和Getter ###
    // =================================================================================

    private static void setRotation(float yaw, float pitch) {
        serverYaw = normalizeYaw(yaw);
        serverPitch = clampPitch(pitch);
    }

    public static float getServerYaw() {
        return serverYaw != null ? serverYaw : (mc.player != null ? mc.player.getYaw() : 0);
    }

    public static float getServerPitch() {
        return serverPitch != null ? serverPitch : (mc.player != null ? mc.player.getPitch() : 0);
    }

    public static boolean isRotating() {
        return serverYaw != null || serverPitch != null;
    }

    // --- 数学计算 (来自 RotationUtils) ---

    private static float[] getRotations(Vec3d target) {
        if (target == null || mc.player == null) {
            return null;
        }

        Vec3d playerPos = mc.player.getEyePos();
        double diffX = target.x - playerPos.x;
        double diffY = target.y - playerPos.y;
        double diffZ = target.z - playerPos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));

        return new float[]{yaw, pitch};
    }

    private static float normalizeYaw(float yaw) {
        return MathHelper.wrapDegrees(yaw);
    }

    private static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -90.0F, 90.0F);
    }
}