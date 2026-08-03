package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class BreakAction implements MacroAction, WaitsForGui {
    public BlockPos blockPos = BlockPos.ZERO;
    public Direction direction = Direction.UP;
    public int delayTicks = 0;
    public int times = 1;
    public boolean autoTool = true;
    public boolean considerInventory = false;
    public boolean manualDirection = false;
    public boolean interact = false;
    public boolean runNextSteps = false;
    public InteractTiming interactTiming = InteractTiming.WITH;
    public int interactCustomMs = 0;
    public boolean sneak = false;
    public String sneakMode = "Packet";
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";
    private transient List<MacroAction> nextActions = null;
    private transient boolean nextActionsRan = false;
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
        tag.putBoolean("autoTool", autoTool);
        tag.putBoolean("considerInventory", considerInventory);
        tag.putBoolean("manualDirection", manualDirection);
        tag.putBoolean("interact", interact);
        tag.putBoolean("runNextSteps", runNextSteps);
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
        delayTicks = Math.max(0, tag.getIntOr("delayTicks", 0));
        times = Math.max(0, tag.getIntOr("times", 1));
        autoTool = tag.getBooleanOr("autoTool", true);
        considerInventory = tag.getBooleanOr("considerInventory", false);
        manualDirection = tag.getBooleanOr("manualDirection", false);
        interact = tag.getBooleanOr("interact", false);
        runNextSteps = tag.getBooleanOr("runNextSteps", false);
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
        return MacroActionType.BREAK;
    }

    public void setNextActions(List<MacroAction> nextActions) {
        this.nextActions = nextActions;
        this.nextActionsRan = false;
    }

    public List<MacroAction> getNextActions() {
        return nextActions;
    }

    public void markNextActionsRan() {
        this.nextActionsRan = true;
    }

    public boolean didRunNextActions() {
        return nextActionsRan;
    }

    @Override
    public String getDisplayName() {
        return "Break @ " + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ()
            + (times == 0 ? " ∞" : " x" + times)
            + (interact ? (runNextSteps ? " +Next" : " +GUI") : "")
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
        return "BRK";
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
