package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public interface MacroAction {
    String LISTEN_DURING_PREVIOUS_KEY = "listenDuringPreviousAction";

    void execute(Minecraft mc);

    CompoundTag toTag();
    void fromTag(CompoundTag tag);

    default MacroActionType getType() { return null; }

    default String getTypeId() {
        MacroActionType type = getType();
        return type != null ? type.name() : getClass().getName();
    }

    String getDisplayName();
    String getIcon();

    default boolean isEnabled() { return true; }

    default void setEnabled(boolean enabled) {}

    default void sanitizeForSharing() {}

    default boolean listensDuringPreviousAction() {
        try {
            var field = getClass().getField(LISTEN_DURING_PREVIOUS_KEY);
            return field.getBoolean(this);
        } catch (Throwable ignored) {
            return false;
        }
    }

    default void setListenDuringPreviousAction(boolean enabled) {
        try {
            var field = getClass().getField(LISTEN_DURING_PREVIOUS_KEY);
            field.setBoolean(this, enabled);
        } catch (Throwable ignored) {  }
    }
}
