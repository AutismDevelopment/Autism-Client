package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismDropHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class DropAction implements MacroAction, WaitsForGui {
    public enum DropMode { ALL, TIMES }

    public DropMode mode = DropMode.TIMES;
    public int dropCount = 1;
    public List<String> itemNames = new ArrayList<>();
    public List<ItemTarget> itemTargets = new ArrayList<>();
    public List<Integer> itemCounts = new ArrayList<>();
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";
    public boolean useHandlerSlots = true;
    private boolean enabled = true;

    public DropAction() {}

    public int getItemCount(int index) {
        if (index >= 0 && index < itemCounts.size()) return Math.max(0, itemCounts.get(index));
        return mode == DropMode.ALL ? 0 : Math.max(1, dropCount);
    }

    public boolean hasSpecificTargets() {
        for (ItemTarget target : resolvedItemTargets()) {
            if (target != null && (target.hasIdentity() || target.hasSlot())) return true;
        }
        return false;
    }

    public List<ItemTarget> resolvedTargets() {
        return List.copyOf(resolvedItemTargets());
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;

        List<ItemTarget> targets = resolvedItemTargets();
        if (!hasSpecificTargets()) {
            if (mode == DropMode.ALL) {

                for (int i = 0; i < 36; i++) {
                    if (!mc.player.getInventory().getItem(i).isEmpty()) {
                        if (useHandlerSlots) {
                            int resolved = AutismInventoryHelper.toHandlerSlot(mc, i);
                            if (resolved >= 0) dropFromHandlerSlot(mc, resolved, 0);
                        } else {
                            dropFromInvSlot(mc, i, 0);
                        }
                    }
                }
            } else {

                int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
                int count = Math.max(1, dropCount);
                if (useHandlerSlots) {
                    int resolved = AutismInventoryHelper.toHandlerSlot(mc, selected);
                    if (resolved >= 0) dropFromHandlerSlot(mc, resolved, count);
                } else {
                    dropFromInvSlot(mc, selected, count);
                }
            }
            return;
        }

        if (!targets.isEmpty()) {
            for (int ei = 0; ei < targets.size(); ei++) {
                ItemTarget entryTarget = getItemTarget(ei).resolveTemplate(mc);
                if (entryTarget == null) continue;
                int count = getItemCount(ei);
                String entryName = parseEntryName(entryTarget.toLegacyEntry());
                int configuredSlot = entryTarget.hasSlot() ? entryTarget.slot : -1;
                if (configuredSlot >= 0 && !entryName.isEmpty()) {
                    dropMatchingItemInSpecificSlot(mc, configuredSlot, entryTarget, count);
                } else if (configuredSlot >= 0) {
                    if (useHandlerSlots) {
                        int resolvedSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredSlot);
                        if (resolvedSlot >= 0) dropFromHandlerSlot(mc, resolvedSlot, count);
                    } else {
                        dropFromInvSlot(mc, configuredSlot, count);
                    }
                } else if (!entryName.isEmpty()) {
                    dropMatchingItemsByTarget(mc, entryTarget, count);
                }
            }
        }
    }

    private void dropMatchingItemInSpecificSlot(Minecraft mc, int configuredSlot, String itemName, int count) {
        dropMatchingItemInSpecificSlot(mc, configuredSlot, ItemTarget.fromLegacyEntry(itemName), count);
    }

    private void dropMatchingItemInSpecificSlot(Minecraft mc, int configuredSlot, ItemTarget target, int count) {
        if (!target.hasIdentity()) return;
        if (useHandlerSlots) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return;
            int resolvedSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredSlot);
            if (resolvedSlot < 0 || resolvedSlot >= handler.slots.size()) return;
            Slot slot = handler.slots.get(resolvedSlot);
            if (slot == null || slot.getItem().isEmpty()) return;
            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, resolvedSlot);
            if (!target.matches(slot.getItem(), visibleSlot)) return;
            int toDrop = count == 0 ? 0 : Math.min(count, slot.getItem().getCount());
            dropFromHandlerSlot(mc, resolvedSlot, toDrop);
            return;
        }

        if (configuredSlot < 0 || configuredSlot >= 36) return;
        net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(configuredSlot);
        if (stack.isEmpty() || !target.matches(stack, configuredSlot)) return;
        int toDrop = count == 0 ? 0 : Math.min(count, stack.getCount());
        dropFromInvSlot(mc, configuredSlot, toDrop);
    }

    private void dropMatchingItemsByName(Minecraft mc, String itemName, int count) {
        dropMatchingItemsByTarget(mc, ItemTarget.fromLegacyEntry(itemName), count);
    }

    private void dropMatchingItemsByTarget(Minecraft mc, ItemTarget target, int count) {
        if (!target.hasIdentity()) return;
        int remaining = count;
        if (useHandlerSlots) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return;
            for (Slot s : handler.slots) {
                if (s == null || s.getItem().isEmpty()) continue;
                int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, s.index);
                if (!target.matches(s.getItem(), visibleSlot)) continue;
                if (count == 0) {
                    dropFromHandlerSlot(mc, s.index, 0);
                    continue;
                }
                if (remaining <= 0) break;
                int toDrop = Math.min(remaining, s.getItem().getCount());
                dropFromHandlerSlot(mc, s.index, toDrop);
                remaining -= toDrop;
            }
            return;
        }

        for (int i = 0; i < 36; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !target.matches(stack, i)) continue;
            if (count == 0) {
                dropFromInvSlot(mc, i, 0);
                continue;
            }
            if (remaining <= 0) break;
            int toDrop = Math.min(remaining, stack.getCount());
            dropFromInvSlot(mc, i, toDrop);
            remaining -= toDrop;
        }
    }

    private void dropFromInvSlot(Minecraft mc, int invSlot, int count) {
        AutismDropHelper.dropFromInventorySlot(mc, invSlot, count);
    }

    private void dropFromHandlerSlot(Minecraft mc, int handlerSlotId, int count) {
        AutismDropHelper.dropFromHandlerSlot(mc, handlerSlotId, count);
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }

    @Override
    public MacroActionType getType() { return MacroActionType.DROP; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "DROP");
        tag.putString("mode", mode.name());
        tag.putInt("count", dropCount);
        tag.put("itemNames", ItemTarget.toTagList(resolvedItemTargets()));
        ListTag counts = new ListTag();
        for (int c : itemCounts) counts.add(StringTag.valueOf(String.valueOf(c)));
        tag.put("itemCounts", counts);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("useHandlerSlots", useHandlerSlots);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        try { this.mode = DropMode.valueOf(tag.getStringOr("mode", "TIMES")); }
        catch (IllegalArgumentException ignored) { this.mode = DropMode.TIMES; }
        this.dropCount = tag.getIntOr("count", 1);
        itemTargets.clear();
        itemNames.clear();
        if (tag.contains("itemNames")) {
            ListTag nl = tag.getList("itemNames").orElse(new ListTag());
            itemTargets.addAll(ItemTarget.fromElementList(nl));
        } else if (tag.contains("itemName")) {
            String old = tag.getStringOr("itemName", "");
            if (!old.isEmpty()) itemTargets.add(ItemTarget.fromLegacyEntry(old));
        }
        syncLegacyItemNames();
        itemCounts.clear();
        if (tag.contains("itemCounts")) {
            ListTag cl = tag.getList("itemCounts").orElse(new ListTag());
            for (Tag el : cl) {
                try { itemCounts.add(Integer.parseInt(el.asString().orElse("1"))); }
                catch (NumberFormatException e) { itemCounts.add(1); }
            }
        }

        while (itemCounts.size() < itemNames.size()) itemCounts.add(getItemCount(itemCounts.size()));
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        if (tag.contains("guiName")) this.guiName = tag.getStringOr("guiName", "");
        this.useHandlerSlots = true;
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        List<ItemTarget> targets = resolvedItemTargets();
        if (!targets.isEmpty()) {
            String e0 = targets.get(0).toLegacyEntry();
            int slot = parseEntrySlot(e0);
            String itemName = parseEntryName(e0);
            String first;
            if (slot >= 0 && !itemName.isEmpty()) first = "\"" + itemName + "\" @ Slot " + slot;
            else if (slot >= 0) first = "Slot " + slot;
            else first = "\"" + itemName + "\"";
            if (targets.size() > 1) first += " (+" + (targets.size() - 1) + ")";
            String base = mode == DropMode.ALL ? "Drop All [" + first + "]" : "Drop [" + first + "]";
            return base + WaitsForGui.timingLabel(this);
        }
        String untargeted = mode == DropMode.ALL
            ? "Drop Entire Inventory"
            : "Drop Held Item x" + Math.max(1, dropCount);
        return untargeted + WaitsForGui.timingLabel(this);
    }

    @Override
    public String getIcon() { return "D"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private static int parseEntrySlot(String entry) {
        if (entry == null || !entry.startsWith("#")) return -1;
        int separator = entry.indexOf('|');
        String raw = separator >= 0 ? entry.substring(1, separator) : entry.substring(1);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String parseEntryName(String entry) {
        if (entry == null || entry.isBlank()) return "";
        if (!entry.startsWith("#")) return entry.trim();
        int separator = entry.indexOf('|');
        return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
    }

    private List<ItemTarget> resolvedItemTargets() {
        if (!itemTargets.isEmpty()) return itemTargets;
        for (String itemName : itemNames) {
            ItemTarget target = ItemTarget.fromLegacyEntry(itemName);
            if (target.hasSlot() || target.hasIdentity()) itemTargets.add(target);
        }
        return itemTargets;
    }

    private ItemTarget getItemTarget(int index) {
        List<ItemTarget> targets = resolvedItemTargets();
        if (index >= 0 && index < targets.size()) return targets.get(index);
        return new ItemTarget();
    }

    private void syncLegacyItemNames() {
        itemNames.clear();
        for (ItemTarget target : itemTargets) {
            if (target == null) continue;
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) itemNames.add(entry);
        }
    }

}
