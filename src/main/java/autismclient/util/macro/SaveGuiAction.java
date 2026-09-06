package autismclient.util.macro;

import autismclient.util.AutismGuiActions;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SaveGuiAction implements MacroAction {
    private boolean enabled   = true;
    public boolean closeAfter = false;
    public boolean sendPacket = true;

    public SaveGuiAction() {}

    @Override
    public void execute(Minecraft mc) {
        if (!AutismGuiActions.saveCurrentGui(mc, true)) return;
        if (closeAfter) {
            AutismGuiActions.closeCurrentScreen(mc, sendPacket, false);
        }
    }

    @Override
    public MacroActionType getType() { return MacroActionType.SAVE_GUI; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SAVE_GUI");
        tag.putBoolean("enabled",    enabled);
        tag.putBoolean("closeAfter", closeAfter);
        tag.putBoolean("sendPacket", sendPacket);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("enabled"))    this.enabled    = tag.getBooleanOr("enabled", true);
        if (tag.contains("closeAfter")) this.closeAfter = tag.getBooleanOr("closeAfter", false);
        if (tag.contains("sendPacket")) this.sendPacket = tag.getBooleanOr("sendPacket", true);
    }

    @Override public String getDisplayName() {
        if (!closeAfter) return "Save GUI";
        return sendPacket ? "Save GUI (close)" : "Save GUI (close, desync)";
    }
    @Override public String getIcon()           { return "S"; }
    @Override public boolean isEnabled()        { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
