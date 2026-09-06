package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SprintAction implements MacroAction, PacketOrdered {
    public boolean sprint = true;
    private boolean enabled = true;
    public boolean persistent = false;
    public volatile PacketOrder packetOrder = PacketOrder.INSTANT;

    public SprintAction() {}

    public SprintAction(boolean sprint) {
        this.sprint = sprint;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.options != null && mc.options.keySprint != null) {
            mc.options.keySprint.setDown(sprint);
        }
        if (mc.player != null) {
            mc.player.setSprinting(sprint);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putBoolean("sprint", sprint);
        tag.putBoolean("persistent", persistent);
        tag.putBoolean("enabled", enabled);
        PacketOrdered.save(tag, packetOrder);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("sprint")) sprint = tag.getBooleanOr("sprint", true);
        if (tag.contains("persistent")) persistent = tag.getBooleanOr("persistent", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        packetOrder = PacketOrdered.load(tag);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.SPRINT;
    }

    @Override public PacketOrder getPacketOrder() { return packetOrder; }

    @Override
    public String getDisplayName() {
        return "Sprint: " + (sprint ? "ON" : "OFF") + (persistent ? " [P]" : "");
    }

    @Override
    public String getIcon() {
        return "SPR";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
