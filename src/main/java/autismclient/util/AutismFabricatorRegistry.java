package autismclient.util;

import org.jetbrains.annotations.Nullable;

public class AutismFabricatorRegistry {
    private static volatile AutismFabricatorOverlay activeOverlay = null;

    public static void setActiveOverlay(@Nullable AutismFabricatorOverlay overlay) {
        activeOverlay = overlay;
    }

    @Nullable
    public static AutismFabricatorOverlay getActiveOverlay() {
        return activeOverlay;
    }
}
