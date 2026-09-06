package autismclient.util.macro;

import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class StartMacroAction implements MacroAction {
    public String macroName = "";
    public boolean restartIfRunning = false;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        if (macroName == null || macroName.isBlank()) return;
        if (restartIfRunning && MacroExecutor.isMacroRunning(macroName)) {
            MacroExecutor.stopMacro(macroName);
        } else if (MacroExecutor.isMacroRunning(macroName)) {
            return;
        }
        AutismMacro macro = AutismMacroManager.get().get(macroName);
        if (macro != null) macro.execute();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("macroName", macroName == null ? "" : macroName);
        tag.putBoolean("restartIfRunning", restartIfRunning);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        macroName = tag.getStringOr("macroName", "");
        restartIfRunning = tag.getBooleanOr("restartIfRunning", false);
        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override public MacroActionType getType() { return MacroActionType.START_MACRO; }
    @Override public String getDisplayName() { return macroName == null || macroName.isBlank() ? "Start Macro" : "Start: " + macroName; }
    @Override public String getIcon() { return "RUN"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
