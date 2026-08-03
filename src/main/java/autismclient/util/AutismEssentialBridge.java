package autismclient.util;

public final class AutismEssentialBridge {

    private AutismEssentialBridge() {
    }

    public static void disable(AutismConfig config) {

    }

    public static void restoreIfOrphaned(AutismConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
            config.save();
        }
    }

    public static void restore(AutismConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
        }
    }
}
