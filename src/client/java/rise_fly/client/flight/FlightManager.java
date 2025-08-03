package rise_fly.client.flight;

public class FlightManager {
    // 按键状态
    public static boolean holdForward = false;
    public static int jumpPressTicks = 0;
    public static int sneakPressTicks = 0; // 新增：下降按键

    public static void resetControls() {
        holdForward = false;
        jumpPressTicks = 0;
        sneakPressTicks = 0;
    }
}