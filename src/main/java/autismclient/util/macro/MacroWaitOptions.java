package autismclient.util.macro;

import net.minecraft.nbt.CompoundTag;

final class MacroWaitOptions {
    private MacroWaitOptions() {}

    static void write(CompoundTag tag, MacroAction action) {
        if (tag == null || action == null) return;
        tag.putBoolean(MacroAction.LISTEN_DURING_PREVIOUS_KEY, action.listensDuringPreviousAction());
    }

    static void read(CompoundTag tag, MacroAction action) {
        if (tag == null || action == null) return;
        action.setListenDuringPreviousAction(tag.getBooleanOr(MacroAction.LISTEN_DURING_PREVIOUS_KEY, false));
    }
}
