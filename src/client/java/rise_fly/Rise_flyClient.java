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
import rise_fly.client.pathing.FlightMode;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.DebugUtils;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Rise_flyClient implements ClientModInitializer {

    public static Rise_flyClient INSTANCE;
    private Vec3d finalTargetPosition;
    private List<Vec3d> currentPath;

    private final ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
    private Future<?> currentPathfindingTask;

    private static FlightMode flightMode = FlightMode.NORMAL;

    public Rise_flyClient() {
        INSTANCE = this;
    }

    public static boolean isLongDistanceFlight() {
        return false;
    }

    public static void setFlightMode(FlightMode mode) {
        flightMode = mode;
    }

    public static FlightMode getFlightMode() {
        return flightMode;
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

        WorldCache.INSTANCE.setOnChunkLoadedCallback(this::onNewChunkLoaded);
    }

    public void onClientClose() {
        pathfindingExecutor.shutdownNow();
    }


    private void onNewChunkLoaded(Void unused) {
        if (FlightControl.INSTANCE.isEnabled()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            client.execute(this::proactiveReplan);
        }
    }

    private void proactiveReplan() {
        if (currentPathfindingTask != null && !currentPathfindingTask.isDone()) {
            currentPathfindingTask.cancel(true);
        }
        DebugUtils.log("§b后台实时重新规划启动...");
        currentPathfindingTask = pathfindingExecutor.submit(() -> {
            List<Vec3d> path = Pathfinder.INSTANCE.findPath(MinecraftClient.getInstance().player.getPos(), finalTargetPosition, flightMode);

            MinecraftClient.getInstance().execute(() -> {
                if (path != null && !path.isEmpty()) {
                    DebugUtils.log("§a后台精确路径已生成，正在无缝切换！");
                    this.currentPath = path;
                    FlightControl.INSTANCE.updatePathToSegment(path);
                } else {
                    DebugUtils.log("§c后台实时规划失败，将继续使用旧路径。");
                }
            });
        });
    }

    public void startFlight(Vec3d target) {
        stopFlight();
        this.finalTargetPosition = target;
        Pathfinder.INSTANCE.clearCosts();
        startGlobalReplan(MinecraftClient.getInstance().player.getPos());
    }

    public void replan(List<Vec3d> oldPath, int oldPathIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (currentPathfindingTask != null && !currentPathfindingTask.isDone()) {
            currentPathfindingTask.cancel(true);
        }

        Vec3d startVec = client.player.getPos();

        if (oldPath != null && !oldPath.isEmpty() && oldPathIndex < oldPath.size()) {
            DebugUtils.log("§e正在进行局部增量规划...");
            Vec3d localTarget = oldPath.get(Math.min(oldPathIndex + 5, oldPath.size() - 1));

            currentPathfindingTask = pathfindingExecutor.submit(() -> {
                List<Vec3d> localPath = Pathfinder.INSTANCE.findPath(startVec, localTarget, flightMode);
                client.execute(() -> {
                    if (localPath != null && !localPath.isEmpty()) {
                        DebugUtils.log("§a局部增量规划成功，正在更新路径...");

                        List<Vec3d> newPath = Stream.concat(
                                localPath.stream().limit(localPath.size() - 1),
                                oldPath.stream().skip(oldPathIndex)
                        ).collect(Collectors.toList());

                        this.currentPath = newPath;
                        FlightControl.INSTANCE.updatePathToSegment(newPath);
                    } else {
                        startGlobalReplan(startVec);
                    }
                });
            });
        } else {
            startGlobalReplan(startVec);
        }
    }

    private void startGlobalReplan(Vec3d startVec) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.player.sendMessage(Text.literal("§e[RiseFly] 正在进行全局精确路径规划..."), true);

        if (currentPathfindingTask != null && !currentPathfindingTask.isDone()) {
            currentPathfindingTask.cancel(true);
        }

        currentPathfindingTask = pathfindingExecutor.submit(() -> {
            List<Vec3d> path = Pathfinder.INSTANCE.findPath(startVec, this.finalTargetPosition, flightMode);

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
        });
    }

    public static void requestReplan() {
        if(INSTANCE != null) {
            FlightControl control = FlightControl.INSTANCE;
            INSTANCE.replan(control.getCurrentPath(), control.getPathIndex());
        }
    }

    public void stopFlight() {
        if (currentPathfindingTask != null && !currentPathfindingTask.isDone()) {
            currentPathfindingTask.cancel(true);
            currentPathfindingTask = null;
        }
        FlightControl.INSTANCE.setEnabled(false);
        Pathfinder.INSTANCE.clearCosts();
        this.currentPath = null;
    }
}