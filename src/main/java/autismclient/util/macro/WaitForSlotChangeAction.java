package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public class WaitForSlotChangeAction implements MacroAction, MacroCaptureOutput {

    public enum WaitMode {
        NOT_EMPTY,
        IS_EMPTY,
        COUNT_AT_LEAST,
        COUNT_BELOW,
        ANY_CHANGE
    }

    public static class WaitEntry {
        public String target = "";
        public ItemTarget itemTarget = new ItemTarget();
        public WaitMode waitMode = WaitMode.NOT_EMPTY;
        public int targetCount = 1;

        public WaitEntry() {}

        public WaitEntry(String target, WaitMode waitMode, int targetCount) {
            this.target = target;
            this.itemTarget = ItemTarget.fromLegacyEntry(target);
            this.waitMode = waitMode;
            this.targetCount = targetCount;
        }

        public WaitEntry copy() {
            WaitEntry copy = new WaitEntry(target, waitMode, targetCount);
            copy.itemTarget = itemTarget == null ? new ItemTarget() : itemTarget.copy();
            return copy;
        }

        public String modeLabel() {
            return switch (waitMode) {
                case NOT_EMPTY -> "has";
                case IS_EMPTY -> "empty";
                case COUNT_AT_LEAST -> ">=" + targetCount;
                case COUNT_BELOW -> "<" + targetCount;
                case ANY_CHANGE -> "~";
            };
        }

        public void cycleMode() {
            WaitMode[] modes = WaitMode.values();
            waitMode = modes[(waitMode.ordinal() + 1) % modes.length];
        }

        public void cycleModeBackwards() {
            WaitMode[] modes = WaitMode.values();
            waitMode = modes[(waitMode.ordinal() - 1 + modes.length) % modes.length];
        }

        public ItemTarget resolvedTarget() {
            if (itemTarget != null && (itemTarget.hasSlot() || itemTarget.hasIdentity())) return itemTarget;
            itemTarget = ItemTarget.fromLegacyEntry(target);
            return itemTarget;
        }
    }

    public List<WaitEntry> entries = new ArrayList<>();
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    private boolean enabled = true;

    public WaitForSlotChangeAction() {}

    public WaitForSlotChangeAction(int slotNumber) {
        WaitEntry entry = new WaitEntry();
        entry.target = slotNumber >= 0 ? "#" + slotNumber : "";
        entry.itemTarget = ItemTarget.fromLegacyEntry(entry.target);
        entries.add(entry);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        ListTag list = new ListTag();
        for (WaitEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            ItemTarget target = entry.resolvedTarget();
            if (target.hasSlot() || target.hasIdentity()) entryTag.put("target", target.toTag());
            entryTag.putString("mode", entry.waitMode.name());
            entryTag.putInt("count", entry.targetCount);
            list.add(entryTag);
        }
        tag.put("entries", list);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        entries.clear();

        if (tag.contains("entries")) {
            ListTag list = tag.getList("entries").orElse(new ListTag());
            for (Tag element : list) {
                if (!(element instanceof CompoundTag entryTag)) continue;
                WaitEntry entry = new WaitEntry();
                entry.itemTarget = entryTag.getCompound("target").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(entryTag.getStringOr("target", "")));
                entry.target = entry.itemTarget.toLegacyEntry();
                entry.targetCount = entryTag.getIntOr("count", 1);
                try { entry.waitMode = WaitMode.valueOf(entryTag.getStringOr("mode", "NOT_EMPTY")); }
                catch (IllegalArgumentException ignored) { entry.waitMode = WaitMode.NOT_EMPTY; }
                entries.add(entry);
            }
        } else {
            WaitMode globalMode = WaitMode.NOT_EMPTY;
            int globalCount = 1;
            if (tag.contains("waitMode")) {
                try {
                    String mode = tag.getStringOr("waitMode", "NOT_EMPTY");
                    if ("HAS_ITEM".equals(mode)) mode = "NOT_EMPTY";
                    globalMode = WaitMode.valueOf(mode);
                } catch (IllegalArgumentException ignored) {  }
            }
            if (tag.contains("targetCount")) globalCount = tag.getIntOr("targetCount", 1);

            if (tag.contains("targetEntries")) {
                ListTag list = tag.getList("targetEntries").orElse(new ListTag());
                for (Tag element : list) {
                    String value = element.asString().orElse("").strip();
                    if (value.isBlank()) continue;
                    entries.add(new WaitEntry(value, globalMode, globalCount));
                }
            } else {
                boolean slotSpecific = true;
                if (tag.contains("scope")) {
                    String scope = tag.getStringOr("scope", "HANDLER_SLOT");
                    slotSpecific = "SLOT".equals(scope) || "HANDLER_SLOT".equals(scope);
                }
                int slotNumber = tag.contains("slotNumber") && slotSpecific ? tag.getIntOr("slotNumber", -1) : -1;
                String itemName = tag.getStringOr("itemName", "");
                String target;
                if (slotNumber >= 0 && !itemName.isEmpty()) target = "#" + slotNumber + "|" + itemName;
                else if (slotNumber >= 0) target = "#" + slotNumber;
                else target = itemName;
                entries.add(new WaitEntry(target, globalMode, globalCount));
            }
        }

        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    public void fromTagLegacyItem(CompoundTag tag) {
        entries.clear();
        String itemName = tag.getStringOr("itemName", "");
        boolean useSlot = tag.contains("useSlot") && tag.getBooleanOr("useSlot", false);
        int targetSlot = tag.contains("targetSlot") ? tag.getIntOr("targetSlot", -1) : -1;
        int slotNumber = (useSlot && targetSlot >= 0) ? targetSlot : -1;
        String target;
        if (slotNumber >= 0 && !itemName.isEmpty()) target = "#" + slotNumber + "|" + itemName;
        else if (slotNumber >= 0) target = "#" + slotNumber;
        else target = itemName;
        entries.add(new WaitEntry(target, WaitMode.NOT_EMPTY, 1));
        enabled = true;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_SLOT_CHANGE;
    }

    @Override
    public String getDisplayName() {
        if (entries.isEmpty()) return "Wait Slot (empty)";
        if (entries.size() == 1) {
            WaitEntry entry = entries.get(0);
            String display = StoreItemAction.formatTargetEntry(entry.target.isEmpty() ? "" : entry.target);
            if (display.isEmpty()) display = "any slot";
            return "Wait " + display + " " + entry.modeLabel();
        }
        return "Wait all(" + entries.size() + ")";
    }

    @Override public String getIcon() { return "WSL"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
