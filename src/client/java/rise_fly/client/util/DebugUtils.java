package rise_fly.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import rise_fly.client.config.ConfigManager;

public class DebugUtils {
    private static final String PREFIX = "§b[RiseFly Debug] §7";

    public static void log(String message) {
        if (ConfigManager.getConfig().debugMode) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.of(PREFIX + message), false);
            }
        }
    }
}