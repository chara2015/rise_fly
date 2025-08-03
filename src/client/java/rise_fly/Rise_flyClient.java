package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.pathing.Pathfinder;
import java.util.List;

public class Rise_flyClient implements ClientModInitializer {

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
		Pathfinder.INSTANCE.clearCosts();
		replan();
	}

	private void replan() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		Vec3d startVec = client.player.getPos();

		client.player.sendMessage(Text.literal("§e[RiseFly] (重)计算并优化路径..."), true);

		new Thread(() -> {
			List<Vec3d> path = Pathfinder.INSTANCE.findPath(startVec, this.finalTargetPosition);

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

	public static void requestReplan() {
		if(INSTANCE != null) {
			INSTANCE.replan();
		}
	}

	public void stopFlight() {
		FlightControl.INSTANCE.setEnabled(false);
	}
}