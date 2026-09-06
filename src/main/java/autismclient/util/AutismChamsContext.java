package autismclient.util;

public final class AutismChamsContext {
    private static boolean active;
    private static boolean bodyClaimed;
    private static boolean drawArmor;
    private static int visibleColor;
    private static int occludedColor;

    private AutismChamsContext() {
    }

    public static void set(int visible, int occluded, boolean armor) {
        active = true;
        bodyClaimed = false;
        drawArmor = armor;
        visibleColor = visible;
        occludedColor = occluded;
    }

    public static boolean drawArmor() {
        return drawArmor;
    }

    public static void clear() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static boolean claimBody() {
        if (bodyClaimed) return false;
        bodyClaimed = true;
        return true;
    }

    public static int visible() {
        return visibleColor;
    }

    public static int occluded() {
        return occludedColor;
    }
}
