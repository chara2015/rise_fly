package rise_fly.client.flight;

public class FlightManager {
    public static boolean holdForward = false;
    public static boolean holdBack = false; // 【新增】后退状态
    public static boolean holdLeft = false;
    public static boolean holdRight = false;
    public static int jumpPressTicks = 0;
    public static int sneakPressTicks = 0;

    public static void resetControls() {
        holdForward = false;
        holdBack = false; // 【新增】重置后退状态
        holdLeft = false;
        holdRight = false;
        jumpPressTicks = 0;
        sneakPressTicks = 0;
    }
}