package autismclient.util.macro;

import net.minecraft.nbt.CompoundTag;

public interface WaitsForGui {
    boolean isWaitForGuiBefore();
    void setWaitForGuiBefore(boolean v);
    boolean isWaitForGuiAfter();
    void setWaitForGuiAfter(boolean v);
    String getWaitGuiName();
    void setWaitGuiName(String name);

    default boolean isWaitForGui() {
        return isWaitForGuiBefore() || isWaitForGuiAfter();
    }

    default void setWaitForGui(boolean v) {
        setWaitForGuiBefore(false);
        setWaitForGuiAfter(v);
    }

    default boolean isWaitForGuiChange() { return false; }

    static boolean loadBefore(CompoundTag tag, boolean defaultBefore) {
        if (tag == null) return defaultBefore;
        if (tag.contains("waitForGuiBefore")) return tag.getBooleanOr("waitForGuiBefore", defaultBefore);
        return false;
    }

    static boolean loadAfter(CompoundTag tag, boolean defaultAfter) {
        if (tag == null) return defaultAfter;
        if (tag.contains("waitForGuiAfter") || tag.contains("waitForGuiBefore")) {
            return tag.getBooleanOr("waitForGuiAfter", defaultAfter);
        }
        return tag.getBooleanOr("waitForGui", defaultAfter);
    }

    static String timingLabel(WaitsForGui wait) {
        if (wait == null) return "";
        boolean before = wait.isWaitForGuiBefore();
        boolean after = wait.isWaitForGuiAfter();
        if (before && after) return " [before+after]";
        if (before) return " [before]";
        if (after) return " [after]";
        return "";
    }
}
