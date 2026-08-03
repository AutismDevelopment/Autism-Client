package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class StopMacroAction implements MacroAction {
    public String macroName = "";
    public StopTarget target = StopTarget.SELF;
    private boolean enabled = true;

    public enum StopTarget {
        SELF,
        SELECTED,
        ALL
    }

    public StopMacroAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (target == StopTarget.ALL) {
            MacroExecutor.stop();
        } else if (target == StopTarget.SELECTED && macroName != null && !macroName.isBlank()) {
            MacroExecutor.stopMacro(macroName);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("target", target.name());
        tag.putString("macroName", macroName == null ? "" : macroName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        try {
            target = StopTarget.valueOf(tag.getStringOr("target", StopTarget.SELF.name()));
        } catch (Exception ignored) {
            target = StopTarget.SELF;
        }
        macroName = tag.getStringOr("macroName", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.STOP_MACRO;
    }

    @Override
    public String getDisplayName() {
        if (target == StopTarget.SELECTED && macroName != null && !macroName.isBlank()) return "Stop: " + macroName;
        if (target == StopTarget.ALL) return "Stop All Macros";
        return "Stop This Macro";
    }

    @Override
    public String getIcon() {
        return "STP";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
