package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.Pathfinder;
import java.util.List;

public class Rise_flyClient implements ClientModInitializer {

	// --- 新增：静态实例和目标变量 ---
	private static Rise_flyClient INSTANCE;
	private Vec3d finalTargetPosition;

	public Rise_flyClient() {
		INSTANCE = this;
	}

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				CommandManager.register(dispatcher, this));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			WorldCache.INSTANCE.onTick(client);
			FlightControl.INSTANCE.onClientTick(client);
		});
	}

	public void startFlight(Vec3d target) {
		this.finalTargetPosition = target;
		Pathfinder.INSTANCE.clearCosts(); // 开始新任务时，清空旧的成本记录
		replan();
	}

	private void replan() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		ChunkPos startChunk = client.player.getChunkPos();
		ChunkPos targetChunk = new ChunkPos(BlockPos.ofFloored(finalTargetPosition));

		client.player.sendMessage(Text.literal("§e[RiseFly] (重)计算路径..."), true);

		new Thread(() -> {
			List<ChunkPos> path = Pathfinder.INSTANCE.findPath(startChunk, targetChunk);
			client.execute(() -> {
				if (path != null && !path.isEmpty()) {
					FlightControl.INSTANCE.setEnabled(true);
					FlightControl.INSTANCE.setPath(path);
				} else {
					client.player.sendMessage(Text.literal("§c[RiseFly] 重规划失败，找不到路径！"), false);
					FlightControl.INSTANCE.setEnabled(false);
				}
			});
		}).start();
	}

	// --- 新增：静态方法供FlightControl调用 ---
	public static void requestReplan() {
		if(INSTANCE != null) {
			INSTANCE.replan();
		}
	}

	public void stopFlight() {
		FlightControl.INSTANCE.setEnabled(false);
	}
}