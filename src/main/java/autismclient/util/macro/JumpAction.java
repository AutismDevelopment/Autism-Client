package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class JumpAction implements MacroAction {
    public int durationTicks = 1;
    public boolean tap = true;
    private boolean enabled = true;

    public JumpAction() {}

    public JumpAction(int durationTicks, boolean tap) {
        this.durationTicks = durationTicks;
        this.tap = tap;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.options != null && mc.options.keyJump != null) {
            mc.options.keyJump.setDown(true);
        }
    }

    public void release(Minecraft mc) {
        if (mc.options != null && mc.options.keyJump != null) {
            mc.options.keyJump.setDown(false);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("durationTicks", durationTicks);
        tag.putBoolean("tap", tap);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("durationTicks")) durationTicks = tag.getIntOr("durationTicks", 1);
        if (tag.contains("tap")) tap = tag.getBooleanOr("tap", true);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.JUMP;
    }

    @Override
    public String getDisplayName() {
        return tap ? "Jump (tap)" : "Jump (hold " + durationTicks + "t)";
    }

    @Override
    public String getIcon() {
        return "J";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
