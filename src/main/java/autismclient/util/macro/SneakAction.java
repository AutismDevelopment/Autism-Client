package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SneakAction implements MacroAction {
    public boolean sneak = true;
    private boolean enabled = true;
    public boolean persistent = false;

    public SneakAction() {}

    public SneakAction(boolean sneak) {
        this.sneak = sneak;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.options != null && mc.options.keyShift != null) {
            mc.options.keyShift.setDown(sneak);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putBoolean("sneak", sneak);
        tag.putBoolean("persistent", persistent);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("sneak")) sneak = tag.getBooleanOr("sneak", true);
        if (tag.contains("persistent")) persistent = tag.getBooleanOr("persistent", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.SNEAK;
    }

    @Override
    public String getDisplayName() {
        return "Sneak: " + (sneak ? "ON" : "OFF") + (persistent ? " [P]" : "");
    }

    @Override
    public String getIcon() {
        return "SNK";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
