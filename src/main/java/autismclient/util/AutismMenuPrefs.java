package autismclient.util;

import autismclient.modules.PackHideState;

public final class AutismMenuPrefs {
    private AutismMenuPrefs() {
    }

    public static boolean customMainMenuEnabled() {

        if (AutismLiteVariant.enabled()) return false;
        AutismConfig config = AutismConfig.getGlobal();
        return config == null || config.customMainMenu;
    }

    public static boolean vanillaMenuVisuals() {
        return PackHideState.isActive() || !customMainMenuEnabled();
    }
}
