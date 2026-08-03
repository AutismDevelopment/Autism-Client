package autismclient.util.macro;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SendChatAction implements MacroAction, WaitsForGui {
    public String message = "";
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = true;
    public String guiName = "";
    private boolean enabled = true;

    public SendChatAction() {}

    public SendChatAction(String message) {
        this.message = message;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", MacroActionType.SEND_CHAT.name());
        tag.putString("message", message);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("message")) {
            this.message = tag.getStringOr("message", "");
        }
        this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        this.waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
        if (tag.contains("guiName")) {
            this.guiName = tag.getStringOr("guiName", "");
        }
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (message == null || message.isEmpty()) return;
        if (mc.getConnection() == null) return;
        MacroTemplate.Resolution resolution = MacroVariables.resolve(message, mc);
        if (!resolution.success()) return;
        String resolved = resolution.value();
        if (resolved.startsWith("/") && resolved.length() > 1) {
            mc.getConnection().sendCommand(resolved.substring(1));
        } else {
            mc.getConnection().sendChat(resolved);
        }
    }

    @Override
    public MacroActionType getType() { return MacroActionType.SEND_CHAT; }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name != null ? name : ""; }

    @Override
    public String getDisplayName() {
        String preview = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        return "Chat: " + preview + WaitsForGui.timingLabel(this);
    }

    @Override
    public String getIcon() { return "CH"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
