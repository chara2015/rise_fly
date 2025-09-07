package rise_fly.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rise_fly.client.flight.FlightControl;
import rise_fly.client.flight.FlightManager;
import rise_fly.client.util.AimingUtils;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Unique
    private float rise_fly$originalYaw;
    @Unique
    private float rise_fly$originalPitch;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        AimingUtils.onTick();

        if (FlightControl.INSTANCE.isEnabled()) {
            MinecraftClient client = MinecraftClient.getInstance();

            // 【修改】将前进和后退输入分开处理
            float forward_input = FlightManager.holdForward ? 1.0f : (FlightManager.holdBack ? -1.0f : 0.0f);
            float strafe_input = FlightManager.holdRight ? 1.0f : (FlightManager.holdLeft ? -1.0f : 0.0f);

            float final_forward = forward_input;
            float final_strafe = strafe_input;

            float[] corrected = AimingUtils.getCorrectedMovement(forward_input, strafe_input);
            if (corrected != null) {
                final_forward = corrected[0];
                final_strafe = corrected[1];
            }

            client.options.forwardKey.setPressed(final_forward > 0.01);
            // 【修改】应用后退键
            client.options.backKey.setPressed(final_forward < -0.01);
            client.options.rightKey.setPressed(final_strafe > 0.01);
            client.options.leftKey.setPressed(final_strafe < -0.01);

            if (FlightManager.jumpPressTicks > 0) {
                client.options.jumpKey.setPressed(true);
                FlightManager.jumpPressTicks--;
            } else {
                client.options.jumpKey.setPressed(false);
            }

            if (FlightManager.sneakPressTicks > 0) {
                client.options.sneakKey.setPressed(true);
                FlightManager.sneakPressTicks--;
            } else {
                client.options.sneakKey.setPressed(false);
            }
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHEAD(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        this.rise_fly$originalYaw = player.getYaw();
        this.rise_fly$originalPitch = player.getPitch();

        if (AimingUtils.isRotating()) {
            player.setYaw(AimingUtils.getServerYaw());
            player.setPitch(AimingUtils.getServerPitch());
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTAIL(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        player.setYaw(this.rise_fly$originalYaw);
        player.setPitch(this.rise_fly$originalPitch);
    }
}