package autismclient.util.macro;

import autismclient.util.AutismSharedState;
import autismclient.util.AutismNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class RestoreGuiAction implements MacroAction, WaitsForGui {
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = true;
    private boolean enabled = true;

    public RestoreGuiAction() {}

    @Override
    public void execute(Minecraft mc) {
        AutismSharedState shared = AutismSharedState.get();
        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            AutismNotifications.error("No stored GUI.");
            return;
        }
        mc.gui.setScreen(shared.getStoredScreen());
        if (mc.player != null) {
            mc.player.containerMenu = shared.getStoredAbstractContainerMenu();
        }
        AutismNotifications.show("GUI restored.", 0xFF35D873);
    }

    @Override public boolean isWaitForGuiBefore()   { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter()    { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName()        { return ""; }
    @Override public void setWaitGuiName(String n)  {}

    @Override
    public MacroActionType getType() { return MacroActionType.RESTORE_GUI; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "RESTORE_GUI");
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
        if (tag.contains("enabled"))    this.enabled    = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        return "Restore GUI" + WaitsForGui.timingLabel(this);
    }

    @Override public String getIcon()           { return "R"; }
    @Override public boolean isEnabled()        { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
