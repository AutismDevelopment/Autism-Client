package autismclient.util.macro;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SendCommandPacketAction implements MacroAction {
    public String command = "";
    public boolean stripLeadingSlash = true;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc == null || mc.getConnection() == null) return;
        MacroTemplate.Resolution resolution = MacroVariables.resolve(command, mc);
        if (!resolution.success()) return;
        String normalized = resolution.value().trim();
        if (stripLeadingSlash && normalized.startsWith("/")) normalized = normalized.substring(1);
        if (!normalized.isBlank()) mc.getConnection().sendCommand(normalized);
    }

    @Override public MacroActionType getType() { return MacroActionType.SEND_COMMAND_PACKET; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "SEND_COMMAND_PACKET");
        tag.putString("command", command);
        tag.putBoolean("stripLeadingSlash", stripLeadingSlash);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        command = tag.getStringOr("command", "");
        stripLeadingSlash = tag.getBooleanOr("stripLeadingSlash", true);
    }

    @Override public String getDisplayName() { return "Command packet " + command; }
    @Override public String getIcon() { return "/"; }
}
