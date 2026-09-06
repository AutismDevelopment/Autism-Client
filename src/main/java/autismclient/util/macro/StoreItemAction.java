package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismGuiActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;

public class StoreItemAction implements MacroAction {

    public enum Mode { LOOT, STORE }

    public Mode         mode        = Mode.STORE;

    public List<String> targetItems = new ArrayList<>();
    public List<ItemTarget> itemTargets = new ArrayList<>();

    public boolean      persistent  = false;

    public boolean      closeAfter  = false;

    public boolean      allItems    = false;

    public boolean      closeSendPkt = true;

    public int          delayTicks  = 0;

    private boolean enabled = true;

    public StoreItemAction() {}

    public void doTransfer(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        var h = mc.player.containerMenu;
        if (h == mc.player.inventoryMenu) return;

        for (int slotId : collectTransferSlots(mc)) {
            AutismInventoryClickHelper.click(mc, slotId, 0, ContainerInput.QUICK_MOVE);
        }

        if (closeAfter && !persistent) {
            AutismGuiActions.closeCurrentScreen(mc, closeSendPkt, false);
        }
    }

    @Override
    public void execute(Minecraft mc) {

        doTransfer(mc);
    }

    public boolean matchesAny(ItemStack stack) {
        return matchesAny(stack, -1);
    }

    public boolean matchesAny(ItemStack stack, int visibleSlot) {
        if (stack.isEmpty()) return false;
        if (allItems) return true;
        List<ItemTarget> targets = runtimeTargets(Minecraft.getInstance());
        if (targets.isEmpty()) return false;

        for (ItemTarget target : targets) {
            if (matchesTargetEntry(stack, visibleSlot, target)) {
                return true;
            }
        }
        return false;
    }

    public List<Integer> collectTransferSlots(Minecraft mc) {
        List<Integer> slotsToMove = new ArrayList<>();
        if (mc == null || mc.player == null || mc.gameMode == null) return slotsToMove;
        var h = mc.player.containerMenu;
        if (h == null || h == mc.player.inventoryMenu) return slotsToMove;

        for (int i = 0; i < h.slots.size(); i++) {
            var slot = h.slots.get(i);
            if (slot == null) continue;

            boolean playerInventorySlot = AutismInventoryHelper.isInventorySlot(mc, slot);
            if (mode == Mode.LOOT && playerInventorySlot) continue;
            if (mode == Mode.STORE && !playerInventorySlot) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, i);
            if (matchesAny(stack, visibleSlot)) slotsToMove.add(i);
        }
        return slotsToMove;
    }

    private boolean matchesTargetEntry(ItemStack stack, int visibleSlot, ItemTarget target) {
        if (target == null || (!target.hasSlot() && !target.hasIdentity())) return false;
        return target.matches(stack, visibleSlot);
    }

    @Override
    public MacroActionType getType() { return MacroActionType.STORE_ITEM; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type",        "STORE_ITEM");
        tag.putString("mode",        mode.name());
        tag.putBoolean("persistent", persistent);
        tag.putBoolean("closeAfter", closeAfter);
        tag.putBoolean("allItems",    allItems);
        tag.putBoolean("closeSendPkt", closeSendPkt);
        tag.putInt("delayTicks", delayTicks);
        tag.put("targetItems", ItemTarget.toTagList(resolvedTargets()));
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("mode")) {
            try { this.mode = Mode.valueOf(tag.getStringOr("mode", "LOOT")); }
            catch (IllegalArgumentException ignored) { this.mode = Mode.LOOT; }
        }
        if (tag.contains("persistent")) this.persistent = tag.getBooleanOr("persistent", false);
        if (tag.contains("closeAfter")) this.closeAfter = tag.getBooleanOr("closeAfter", false);
        if (tag.contains("allItems"))    this.allItems    = tag.getBooleanOr("allItems", false);
        if (tag.contains("closeSendPkt")) this.closeSendPkt = tag.getBooleanOr("closeSendPkt", true);
        if (tag.contains("delayTicks")) this.delayTicks = clampDelayTicks(tag.getIntOr("delayTicks", 0));
        itemTargets.clear();
        targetItems.clear();
        if (tag.contains("targetItems")) {
            ListTag list = tag.getList("targetItems").orElse(new ListTag());
            itemTargets.addAll(ItemTarget.fromElementList(list));
        }
        syncLegacyTargetItems();
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public String getDisplayName() {
        String modeStr = mode == Mode.LOOT ? "Loot" : "Store";
        if (allItems) return modeStr + " All" + (persistent ? " [Inf]" : "");
        List<ItemTarget> targets = resolvedTargets();
        if (targets.isEmpty()) return modeStr + " (nothing)";
        String what = targets.size() == 1
                ? formatTargetEntry(targets.get(0).toLegacyEntry())
                : targets.size() + " items";
        return modeStr + " " + what + (persistent ? " [Inf]" : "");
    }

    public static String normalizeTargetEntry(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("#") && trimmed.matches("\\d+")) {
            trimmed = "#" + trimmed;
        }
        if (!trimmed.startsWith("#")) return trimmed;

        int separator = trimmed.indexOf('|');
        String slotRaw = separator >= 0 ? trimmed.substring(1, separator).trim() : trimmed.substring(1).trim();
        if (slotRaw.isEmpty()) return null;
        try {
            int slot = Integer.parseInt(slotRaw);
            if (slot < 0) return null;
            String itemPart = separator >= 0 && separator + 1 < trimmed.length()
                    ? trimmed.substring(separator + 1).trim()
                    : "";
            return itemPart.isEmpty() ? "#" + slot : "#" + slot + "|" + itemPart;
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    public static String buildCapturedTargetEntry(String itemName, String registryId, int visibleSlot) {
        String itemPart = registryId != null && !registryId.isBlank()
                ? registryId.trim()
                : (itemName != null ? itemName.trim() : "");
        if (!itemPart.isEmpty()) return itemPart;
        return visibleSlot >= 0 ? "#" + visibleSlot : null;
    }

    public static String formatTargetEntry(String entry) {
        if (entry == null || entry.isBlank()) return "";
        ItemTarget target = ItemTarget.fromLegacyEntry(entry);
        String label = target.displayLabel();
        if (target.hasSlot() && label.isBlank()) return "#" + target.slot;
        if (target.hasSlot()) return target.slot + ": " + label;
        return label;
    }

    private static int parseTargetSlot(String entry) {
        if (entry == null || !entry.startsWith("#")) return -1;
        int separator = entry.indexOf('|');
        String slotRaw = separator >= 0 ? entry.substring(1, separator).trim() : entry.substring(1).trim();
        if (slotRaw.isEmpty()) return -1;
        try {
            int slot = Integer.parseInt(slotRaw);
            return slot >= 0 ? slot : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String parseTargetName(String entry) {
        if (entry == null || entry.isBlank()) return "";
        int separator = entry.indexOf('|');
        if (separator < 0 || separator + 1 >= entry.length()) return "";
        return entry.substring(separator + 1).trim();
    }

    @Override public String  getIcon()             { return mode == Mode.LOOT ? "LOT" : "STR"; }
    @Override public boolean isEnabled()           { return enabled; }
    @Override public void    setEnabled(boolean e) { this.enabled = e; }

    private List<ItemTarget> resolvedTargets() {
        if (!itemTargets.isEmpty()) return itemTargets;
        for (String target : targetItems) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(target);
            if (parsed.hasSlot() || parsed.hasIdentity()) itemTargets.add(parsed);
        }
        return itemTargets;
    }

    private List<ItemTarget> runtimeTargets(Minecraft mc) {
        List<ItemTarget> runtime = new ArrayList<>();
        for (ItemTarget target : resolvedTargets()) {
            if (target == null) continue;
            ItemTarget resolved = target.resolveTemplate(mc);
            if (resolved != null && (resolved.hasSlot() || resolved.hasIdentity())) runtime.add(resolved);
        }
        return runtime;
    }

    private void syncLegacyTargetItems() {
        targetItems.clear();
        for (ItemTarget target : itemTargets) {
            if (target == null) continue;
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) targetItems.add(entry);
        }
    }

    public static int clampDelayTicks(int value) {
        return Math.max(0, Math.min(200, value));
    }
}
