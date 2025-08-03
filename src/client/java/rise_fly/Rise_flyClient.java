package rise_fly.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import rise_fly.client.cache.WorldCache;
import rise_fly.client.command.CommandManager;
import rise_fly.client.flight.FlightControl;
import java.util.List;

public class Rise_flyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				CommandManager.register(dispatcher, this));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			WorldCache.INSTANCE.onTick(client);
			FlightControl.INSTANCE.onClientTick(client);
		});
	}

	public void startFlightWithPath(List<ChunkPos> path) {
		FlightControl.INSTANCE.setEnabled(true);
		FlightControl.INSTANCE.setPath(path);
	}

	public void stopFlight() {
		FlightControl.INSTANCE.setEnabled(false);
	}
}