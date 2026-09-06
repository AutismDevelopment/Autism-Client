package autismclient.util.macro;

import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SendToggleAction implements MacroAction {

    public enum ToggleMode { ENABLE, DISABLE }

    public ToggleMode mode = ToggleMode.ENABLE;
    private boolean enabled = true;

    public SendToggleAction() {}

    public void cycleMode() {
        mode = ToggleMode.values()[(mode.ordinal() + 1) % ToggleMode.values().length];
    }

    public void cycleModeBackwards() {
        mode = ToggleMode.values()[(mode.ordinal() - 1 + ToggleMode.values().length) % ToggleMode.values().length];
    }

    @Override
    public void execute(Minecraft mc) {
        AutismSharedState shared = AutismSharedState.get();
        shared.setSendGuiPackets(mode == ToggleMode.ENABLE);
    }

    @Override
    public MacroActionType getType() { return MacroActionType.SEND_TOGGLE; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SEND_TOGGLE");
        tag.putString("mode", mode.name());
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("mode")) {
            try { mode = ToggleMode.valueOf(tag.getStringOr("mode", "ENABLE")); }
            catch (IllegalArgumentException ignored) { mode = ToggleMode.ENABLE; }
        }
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        return mode == ToggleMode.ENABLE ? "Send Pkts: ON" : "Send Pkts: OFF";
    }

    @Override public String getIcon()           { return "T"; }
    @Override public boolean isEnabled()        { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
