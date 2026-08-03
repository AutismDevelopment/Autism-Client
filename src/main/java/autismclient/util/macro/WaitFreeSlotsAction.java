package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitFreeSlotsAction implements MacroAction {
    public enum CountMode {
        FREE_SLOTS,
        FILLED_SLOTS
    }

    public enum Comparison {
        BELOW,
        AT_MOST,
        EXACT,
        AT_LEAST,
        ABOVE
    }

    public CountMode countMode = CountMode.FREE_SLOTS;
    public Comparison comparison = Comparison.AT_MOST;
    public int slots = 0;
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;
    private boolean enabled = true;

    @Override public void execute(Minecraft mc) {}

    @Override public MacroActionType getType() { return MacroActionType.WAIT_FREE_SLOTS; }

    public boolean matches(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        int free = 0;
        int filled = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) free++;
            else filled++;
        }
        int actual = countMode == CountMode.FILLED_SLOTS ? filled : free;
        return compare(actual, Math.max(0, Math.min(36, slots)));
    }

    private boolean compare(int actual, int target) {
        return switch (comparison) {
            case BELOW -> actual < target;
            case AT_MOST -> actual <= target;
            case EXACT -> actual == target;
            case AT_LEAST -> actual >= target;
            case ABOVE -> actual > target;
        };
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("countMode", countMode.name());
        tag.putString("comparison", comparison.name());
        tag.putInt("slots", Math.max(0, Math.min(36, slots)));
        tag.putInt("timeoutMs", Math.max(0, timeoutMs));
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        countMode = MacroStringList.enumValue(CountMode.class, tag.getStringOr("countMode", CountMode.FREE_SLOTS.name()), CountMode.FREE_SLOTS);
        comparison = MacroStringList.enumValue(Comparison.class, tag.getStringOr("comparison", Comparison.AT_MOST.name()), Comparison.AT_MOST);
        slots = Math.max(0, Math.min(36, tag.getIntOr("slots", 0)));
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }

    @Override
    public String getDisplayName() {
        return "Wait " + (countMode == CountMode.FILLED_SLOTS ? "Filled" : "Free")
            + " Slots " + comparisonLabel() + " " + Math.max(0, Math.min(36, slots));
    }

    private String comparisonLabel() {
        return switch (comparison) {
            case BELOW -> "<";
            case AT_MOST -> "<=";
            case EXACT -> "=";
            case AT_LEAST -> ">=";
            case ABOVE -> ">";
        };
    }

    @Override public String getIcon() { return "FS"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }
}
