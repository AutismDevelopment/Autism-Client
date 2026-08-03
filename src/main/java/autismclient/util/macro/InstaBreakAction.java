package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public class InstaBreakAction implements MacroAction, WaitsForGui {
    public BlockPos blockPos = BlockPos.ZERO;
    public Direction direction = Direction.UP;
    public int delayTicks = 0;
    public int times = 1;
    public boolean autoPickaxe = true;
    public boolean manualDirection = false;
    public boolean interact = false;
    public InteractTiming interactTiming = InteractTiming.WITH;
    public int interactCustomMs = 0;
    public boolean sneak = false;
    public String sneakMode = "Packet";
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("x", blockPos.getX());
        tag.putInt("y", blockPos.getY());
        tag.putInt("z", blockPos.getZ());
        tag.putString("direction", direction.name());
        tag.putInt("delayTicks", delayTicks);
        tag.putInt("times", times);
        tag.putBoolean("autoPickaxe", autoPickaxe);
        tag.putBoolean("manualDirection", manualDirection);
        tag.putBoolean("interact", interact);
        tag.putString("interactTiming", interactTiming.name());
        tag.putInt("interactCustomMs", interactCustomMs);
        tag.putBoolean("sneak", sneak);
        tag.putString("sneakMode", sneakMode);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            blockPos = new BlockPos(
                tag.getIntOr("x", 0),
                tag.getIntOr("y", 0),
                tag.getIntOr("z", 0)
            );
        }
        if (tag.contains("direction")) {
            try {
                direction = Direction.valueOf(tag.getStringOr("direction", "UP"));
            } catch (IllegalArgumentException ignored) {
                direction = Direction.UP;
            }
        }
        delayTicks = Math.max(0, tag.getIntOr("delayTicks", tag.getIntOr("delay", 0)));
        times = Math.max(0, tag.getIntOr("times", 1));
        autoPickaxe = tag.getBooleanOr("autoPickaxe", true);
        manualDirection = tag.getBooleanOr("manualDirection", false);
        interact = tag.getBooleanOr("interact", false);
        interactTiming = InteractTiming.parse(tag.getStringOr("interactTiming", "WITH"), InteractTiming.WITH);
        interactCustomMs = Math.max(-5000, Math.min(5000, tag.getIntOr("interactCustomMs", 0)));
        sneak = tag.getBooleanOr("sneak", false);
        sneakMode = tag.getStringOr("sneakMode", "Packet");
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        guiName = tag.getStringOr("guiName", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.INSTA_BREAK;
    }

    @Override
    public String getDisplayName() {
        return "InstaBreak @ " + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ()
            + (times == 0 ? " ∞" : " x" + times)
            + WaitsForGui.timingLabel(this);
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }

    @Override
    public String getIcon() {
        return "IB";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
