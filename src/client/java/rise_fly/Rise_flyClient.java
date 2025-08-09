package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.config.ConfigManager;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.DebugUtils;

import java.util.List;

public class Rise_flyClient implements ClientModInitializer {

    private static Rise_flyClient INSTANCE;
    private Vec3d finalTargetPosition;
    private List<Vec3d> currentPath;

    private static boolean isPredictiveFlight = false;

    public Rise_flyClient() {
        INSTANCE = this;
    }

    public static boolean isPredictiveFlight() {
        return isPredictiveFlight;
    }

    @Override
    public void onInitializeClient() {
        ConfigManager.loadConfig();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                CommandManager.register(dispatcher, this));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WorldCache.INSTANCE.onTick(client);
            FlightControl.INSTANCE.onClientTick(client);
        });
    }

    public void startFlight(Vec3d target) {
        this.finalTargetPosition = target;
        Pathfinder.INSTANCE.clearCosts();
        isPredictiveFlight = false;
        replan(null, 0);
    }

    public void replan(List<Vec3d> oldPath, int oldPathIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d startVec = client.player.getPos();

        if (oldPath != null && !oldPath.isEmpty() && oldPathIndex < oldPath.size() && !isPredictiveFlight) {
            Vec3d nextTarget = oldPath.get(oldPathIndex);
            DebugUtils.log("§e正在尝试从当前位置到旧路径点进行局部重新规划...");

            // 关键的多线程实现：在后台线程进行计算，避免主线程卡顿
            new Thread(() -> {
                List<Vec3d> newLocalPath = Pathfinder.INSTANCE.findPath(startVec, nextTarget);

                // 计算完成后，将结果传回主线程进行处理
                client.execute(() -> {
                    if (newLocalPath != null && !newLocalPath.isEmpty()) {
                        DebugUtils.log("§a局部重新规划成功！正在拼接路径...");
                        newLocalPath.addAll(oldPath.subList(oldPathIndex, oldPath.size()));
                        this.currentPath = newLocalPath;
                        FlightControl.INSTANCE.setEnabled(true);
                        FlightControl.INSTANCE.setPath(this.currentPath);
                    } else {
                        DebugUtils.log("§c局部重新规划失败，尝试全局重新规划。");
                        replanToFinalTarget(startVec);
                    }
                });
            }).start();
        } else {
            replanToFinalTarget(startVec);
        }
    }

    private void replanToFinalTarget(Vec3d startVec) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        double distance = startVec.distanceTo(finalTargetPosition);
        int renderDistance = client.options.getClampedViewDistance() * 16;

        if (distance > renderDistance * 0.8) {
            DebugUtils.log("§e目标距离过远，进入预测性路径规划模式...");
            isPredictiveFlight = true;

            // 关键的多线程实现：在后台线程进行计算，避免主线程卡顿
            new Thread(() -> {
                List<Vec3d> predictivePath = Pathfinder.INSTANCE.findPredictivePath(startVec, this.finalTargetPosition, renderDistance);

                // 计算完成后，将结果传回主线程进行处理
                client.execute(() -> {
                    if (predictivePath != null && !predictivePath.isEmpty()) {
                        this.currentPath = predictivePath;
                        FlightControl.INSTANCE.setEnabled(true);
                        FlightControl.INSTANCE.setPath(this.currentPath);
                        client.player.sendMessage(Text.literal("§e[RiseFly] 目标距离过远，正在进行预测性飞行。"), true);
                    } else {
                        client.player.sendMessage(Text.literal("§c[RiseFly] 预测性路径规划失败，请手动靠近目标。"), false);
                        FlightControl.INSTANCE.setEnabled(false);
                    }
                });
            }).start();
        } else {
            isPredictiveFlight = false;
            client.player.sendMessage(Text.literal("§e[RiseFly] 正在进行精确路径规划..."), true);

            // 关键的多线程实现：在后台线程进行计算，避免主线程卡顿
            new Thread(() -> {
                List<Vec3d> path = Pathfinder.INSTANCE.findPath(startVec, this.finalTargetPosition);

                // 计算完成后，将结果传回主线程进行处理
                client.execute(() -> {
                    if (path != null && !path.isEmpty()) {
                        this.currentPath = path;
                        FlightControl.INSTANCE.setEnabled(true);
                        FlightControl.INSTANCE.setPath(this.currentPath);
                        client.player.sendMessage(Text.literal("§a[RiseFly] 精确路径已生成，开始飞行！"), true);
                    } else {
                        client.player.sendMessage(Text.literal("§c[RiseFly] 精确路径规划失败，找不到路径！"), false);
                        FlightControl.INSTANCE.setEnabled(false);
                    }
                });
            }).start();
        }
    }

    public static void requestReplan() {
        if(INSTANCE != null) {
            FlightControl control = FlightControl.INSTANCE;
            INSTANCE.replan(control.getCurrentPath(), control.getPathIndex());
        }
    }

    public static void requestProactiveReplan(Vec3d target) {
        if (INSTANCE != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // 关键的多线程实现：在后台线程进行快速的局部规划
            new Thread(() -> {
                List<Vec3d> newPathSegment = Pathfinder.INSTANCE.findPath(client.player.getPos(), target);

                // 计算完成后，将结果传回主线程进行处理
                client.execute(() -> {
                    if (newPathSegment != null && !newPathSegment.isEmpty()) {
                        DebugUtils.log("§b前瞻性重新规划成功！正在更新路径...");
                        FlightControl.INSTANCE.updatePathSegment(newPathSegment);
                    }
                });
            }).start();
        }
    }

    public void stopFlight() {
        isPredictiveFlight = false;
        FlightControl.INSTANCE.setEnabled(false);
    }
}