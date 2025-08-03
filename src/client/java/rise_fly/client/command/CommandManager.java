package rise_fly.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.Rise_flyClient;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.Pathfinder;
import rise_fly.client.util.AimingUtils;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CommandManager {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Rise_flyClient mod) {
        dispatcher.register(literal("fly")
                .executes(CommandManager::executeHelp)
                .then(literal("to")
                        .then(argument("x", StringArgumentType.word())
                                .then(argument("y", StringArgumentType.word())
                                        .then(argument("z", StringArgumentType.word())
                                                .executes(context -> executeFlyTo(context, mod))))))
                .then(literal("stop")
                        .executes(context -> executeStop(context, mod)))
                .then(literal("debug")
                        .executes(CommandManager::executeDebug))
                .then(literal("help")
                        .executes(CommandManager::executeHelp))
        );
    }

    private static int executeFlyTo(CommandContext<FabricClientCommandSource> context, Rise_flyClient mod) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;
        try {
            String yStr = context.getArgument("y", String.class);
            double x = Double.parseDouble(context.getArgument("x", String.class));
            double z = Double.parseDouble(context.getArgument("z", String.class));
            double y = (yStr.equalsIgnoreCase("x") || yStr.equalsIgnoreCase("~")) ? Double.NaN : Double.parseDouble(yStr);

            Vec3d targetAimPos = new Vec3d(x, Double.isNaN(y) ? player.getY() : y, z);
            AimingUtils.aimAt(player, targetAimPos);

            ChunkPos startChunk = player.getChunkPos();
            ChunkPos targetChunk = new ChunkPos(BlockPos.ofFloored(x, 0, z));
            context.getSource().sendFeedback(Text.literal("§a[RiseFly] §f正在计算路径..."));

            new Thread(() -> {
                List<ChunkPos> path = Pathfinder.INSTANCE.findPath(startChunk, targetChunk);
                context.getSource().getClient().execute(() -> {
                    if (path != null && !path.isEmpty()) {
                        mod.startFlightWithPath(path);
                        context.getSource().sendFeedback(Text.literal("§a[RiseFly] §f路径计算完成，开始飞行！"));
                    } else {
                        context.getSource().sendFeedback(Text.literal("§c[RiseFly] 未能找到通往目标的路径！"));
                    }
                });
            }).start();
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFeedback(Text.literal("§c[RiseFly] 无效的坐标格式！"));
            return 0;
        }
    }

    private static int executeStop(CommandContext<FabricClientCommandSource> context, Rise_flyClient mod) {
        mod.stopFlight();
        context.getSource().sendFeedback(Text.literal("§a[RiseFly] §e所有任务已停止。"));
        return 1;
    }

    private static int executeDebug(CommandContext<FabricClientCommandSource> context) {
        boolean newState = FlightControl.INSTANCE.toggleDebug();
        String feedback = "§a[RiseFly] §fDebug模式已 " + (newState ? "§a开启" : "§c关闭");
        context.getSource().sendFeedback(Text.literal(feedback));
        return 1;
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.of(
                "§a---------- [RiseFly 帮助] ----------\n" +
                        "§6/fly to <x> <y> <z> §f- 飞往指定三维坐标。\n" +
                        "§6/fly to <x> x <z> §f- 飞往指定XZ坐标，智能选择高度。\n" +
                        "§6/fly stop §f- 停止当前所有飞行任务。\n" +
                        "§6/fly debug §f- 开启/关闭路径渲染等Debug信息。\n" +
                        "§6/fly help §f- 显示此帮助菜单。\n" +
                        "§a------------------------------------"
        ));
        return 1;
    }
}