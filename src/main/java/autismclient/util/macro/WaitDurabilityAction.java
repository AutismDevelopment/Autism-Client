package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WaitDurabilityAction implements MacroAction {
    public enum TargetMode {
        HELD,
        ITEM,
        SLOT
    }

    public enum Measurement {
        REMAINING,
        DAMAGE_USED,
        PERCENT_REMAINING
    }

    public enum Comparison {
        BELOW,
        AT_MOST,
        EXACT,
        AT_LEAST,
        ABOVE
    }

    public TargetMode targetMode = TargetMode.HELD;
    public String itemName = "";
    public int slot = 0;
    public Measurement measurement = Measurement.REMAINING;
    public Comparison comparison = Comparison.AT_MOST;
    public int value = 2;
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;
    public boolean useNext = false;
    private boolean enabled = true;

    @Override public void execute(Minecraft mc) {}

    @Override public MacroActionType getType() { return MacroActionType.WAIT_DURABILITY; }

    public boolean matches(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        return switch (targetMode) {
            case HELD -> matchesStack(mc.player.getMainHandItem()) || matchesStack(mc.player.getOffhandItem());
            case SLOT -> matchesStack(stackForSlot(mc, slot));
            case ITEM -> matchesItemTarget(mc);
        };
    }

    private boolean matchesItemTarget(Minecraft mc) {
        ItemTarget target = ItemTarget.fromLegacyEntry(itemName).resolveTemplate(mc);
        if (target == null) return false;
        if (!target.hasIdentity() && !target.hasSlot()) {
            return matchesStack(mc.player.getMainHandItem()) || matchesStack(mc.player.getOffhandItem());
        }
        ItemTarget itemOnly = target.copy();
        if (itemOnly.hasIdentity()) itemOnly.slot = -1;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (itemOnly.score(stack, i) >= 0 && matchesStack(stack)) return true;
        }
        return itemOnly.score(mc.player.getOffhandItem(), 40) >= 0 && matchesStack(mc.player.getOffhandItem());
    }

    private ItemStack stackForSlot(Minecraft mc, int visibleSlot) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        if (visibleSlot == 40) return mc.player.getOffhandItem();
        int size = mc.player.getInventory().getContainerSize();
        int index = Math.max(0, Math.min(Math.max(0, size - 1), visibleSlot));
        return mc.player.getInventory().getItem(index);
    }

    public boolean matchesStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        int metric = durabilityMetric(stack);
        int target = clampTargetValue(stack);
        return compare(metric, target);
    }

    private int durabilityMetric(ItemStack stack) {
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        return switch (measurement) {
            case REMAINING -> remaining;
            case DAMAGE_USED -> Math.max(0, stack.getDamageValue());
            case PERCENT_REMAINING -> stack.getMaxDamage() <= 0 ? 100 : Math.round(remaining * 100.0f / stack.getMaxDamage());
        };
    }

    private int clampTargetValue(ItemStack stack) {
        int max = measurement == Measurement.PERCENT_REMAINING ? 100 : Math.max(0, stack == null ? 2048 : stack.getMaxDamage());
        return Math.max(0, Math.min(max, value));
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
        tag.putString("targetMode", targetMode.name());
        tag.putString("itemName", itemName == null ? "" : itemName);
        tag.putInt("slot", Math.max(0, Math.min(40, slot)));
        tag.putString("measurement", measurement.name());
        tag.putString("comparison", comparison.name());
        tag.putInt("value", Math.max(0, value));
        tag.putInt("timeoutMs", Math.max(0, timeoutMs));
        tag.putBoolean("useNext", useNext);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        targetMode = MacroStringList.enumValue(TargetMode.class, tag.getStringOr("targetMode", TargetMode.HELD.name()), TargetMode.HELD);
        itemName = tag.getStringOr("itemName", "");
        slot = Math.max(0, Math.min(40, tag.getIntOr("slot", 0)));
        measurement = MacroStringList.enumValue(Measurement.class, tag.getStringOr("measurement", Measurement.REMAINING.name()), Measurement.REMAINING);
        comparison = MacroStringList.enumValue(Comparison.class, tag.getStringOr("comparison", Comparison.AT_MOST.name()), Comparison.AT_MOST);
        value = Math.max(0, tag.getIntOr("value", 2));
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
        useNext = tag.getBooleanOr("useNext", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }

    @Override
    public String getDisplayName() {
        return "Wait Durability " + targetLabel() + " " + metricLabel() + " " + comparisonLabel() + " " + Math.max(0, value)
            + (useNext ? " + next" : "");
    }

    private String targetLabel() {
        return switch (targetMode) {
            case HELD -> "Held";
            case ITEM -> itemName == null || itemName.isBlank() ? "Held" : ItemTarget.fromLegacyEntry(itemName).summaryText();
            case SLOT -> "#" + Math.max(0, Math.min(40, slot));
        };
    }

    private String metricLabel() {
        return switch (measurement) {
            case REMAINING -> "remaining";
            case DAMAGE_USED -> "damage";
            case PERCENT_REMAINING -> "%";
        };
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

    @Override public String getIcon() { return "DUR"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }
}
