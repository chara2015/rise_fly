package rise_fly.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.flight.FlightManager;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (FlightControl.INSTANCE.isEnabled()) {
            MinecraftClient client = MinecraftClient.getInstance();

            // 模拟长按W键
            client.options.forwardKey.setPressed(FlightManager.holdForward);

            // 模拟按一下跳跃键
            if (FlightManager.jumpPressTicks > 0) {
                client.options.jumpKey.setPressed(true);
                FlightManager.jumpPressTicks--;
            } else {
                client.options.jumpKey.setPressed(false);
            }

            // 模拟按一下潜行键 (新)
            if (FlightManager.sneakPressTicks > 0) {
                client.options.sneakKey.setPressed(true);
                FlightManager.sneakPressTicks--;
            } else {
                client.options.sneakKey.setPressed(false);
            }
        }
    }
}