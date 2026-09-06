package autismclient.util.macro;

import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;

public class XCarryAction implements MacroAction {
    public static final int CRAFT_RESULT_SLOT = 0;
    public static final int GRID_ENTRY_LIMIT = 4;
    public static final int CRAFT_RESULT_ENTRY_LIMIT = 1;
    public static final int ARMOR_ENTRY_LIMIT = 4;
    public static final int OFFHAND_ENTRY_LIMIT = 1;
    public static final int CURSOR_ENTRY_LIMIT = 1;
    public static final int STORAGE_ENTRY_LIMIT = GRID_ENTRY_LIMIT + CRAFT_RESULT_ENTRY_LIMIT + ARMOR_ENTRY_LIMIT + OFFHAND_ENTRY_LIMIT;
    public static final int MAX_ENTRIES = STORAGE_ENTRY_LIMIT + CURSOR_ENTRY_LIMIT;
    private static final int MAX_PUT_IN_ATTEMPTS = MAX_ENTRIES;

    public enum Mode { PUT_IN, TAKE_OUT, DROP }
    public enum TransferMode { FAST, CLICK, SAFE_CLICK }
    public enum AmountMode { FULL_STK, CUSTOM }

    public static final class PutInMove {
        final int sourceSlotId;
        final int targetSlotId;
        final int count;

        PutInMove(int sourceSlotId, int targetSlotId, int count) {
            this.sourceSlotId = sourceSlotId;
            this.targetSlotId = targetSlotId;
            this.count = count;
        }

        public int sourceSlotId() { return sourceSlotId; }
        public int targetSlotId() { return targetSlotId; }
        public int count() { return count; }
    }

    public final List<String> entries = new ArrayList<>();
    public final List<ItemTarget> entryTargets = new ArrayList<>();
    public Mode mode = Mode.PUT_IN;
    public TransferMode transferMode = TransferMode.FAST;
    public int safeClickDelayTicks = 1;
    public boolean safeClickDelayAfterPickup = true;
    public boolean safeClickDelayBeforeReturn = true;
    public boolean carryCursor = true;

    public boolean useCrafting = true;
    public boolean useArmor    = true;
    public boolean useOffhand  = true;

    public static final int DEST_AUTO   = Integer.MIN_VALUE;
    public static final int DEST_CURSOR = -1;
    private static final int[] DESTINATION_VALUES = new int[] {
            DEST_AUTO,
            DEST_CURSOR,
            1, 2, 3, 4,
            CRAFT_RESULT_SLOT,
            5, 6, 7, 8,
            45
    };
    public final java.util.List<Integer> entryDestinations = new java.util.ArrayList<>();
    public final java.util.List<AmountMode> entryAmountModes = new java.util.ArrayList<>();
    public final java.util.List<Integer> entryAmounts = new java.util.ArrayList<>();
    private boolean enabled = true;

    public static int[] destinationValues() {
        return DESTINATION_VALUES.clone();
    }

    public static java.util.List<String> destinationLabels() {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>(DESTINATION_VALUES.length);
        for (int value : DESTINATION_VALUES) labels.add(destinationLabel(value));
        return labels;
    }

    public static int destinationIndex(int destination) {
        for (int i = 0; i < DESTINATION_VALUES.length; i++) {
            if (DESTINATION_VALUES[i] == destination) return i;
        }
        return 0;
    }

    public static String destinationLabel(int destination) {
        if (destination == DEST_AUTO) return "Auto";
        if (destination == DEST_CURSOR) return "Cursor";
        return switch (destination) {
            case 1 -> "Craft 1";
            case 2 -> "Craft 2";
            case 3 -> "Craft 3";
            case 4 -> "Craft 4";
            case CRAFT_RESULT_SLOT -> "Craft 5";
            case 5 -> "Helmet";
            case 6 -> "Chestplate";
            case 7 -> "Leggings";
            case 8 -> "Boots";
            case 45 -> "Offhand";
            default -> "Auto";
        };
    }

    private void forceAllDestinationsEnabled() {
        carryCursor = true;
        useCrafting = true;
        useArmor = true;
        useOffhand = true;
    }

    public int destinationFor(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entryDestinations.size()) return DEST_AUTO;
        Integer v = entryDestinations.get(entryIndex);
        return v == null ? DEST_AUTO : v;
    }
    public void setDestinationFor(int entryIndex, int destSlotId) {
        while (entryDestinations.size() <= entryIndex) entryDestinations.add(DEST_AUTO);
        entryDestinations.set(entryIndex, destSlotId);
    }
    public void resizeDestinations(int targetSize) {
        while (entryDestinations.size() < targetSize) entryDestinations.add(DEST_AUTO);
        while (entryDestinations.size() > targetSize) entryDestinations.remove(entryDestinations.size() - 1);
    }

    public AmountMode amountModeFor(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entryAmountModes.size()) return AmountMode.FULL_STK;
        AmountMode mode = entryAmountModes.get(entryIndex);
        return mode == null ? AmountMode.FULL_STK : mode;
    }

    public void setAmountModeFor(int entryIndex, AmountMode mode) {
        resizeAmountSettings(entryIndex + 1);
        entryAmountModes.set(entryIndex, mode == null ? AmountMode.FULL_STK : mode);
    }

    public int amountFor(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entryAmounts.size()) return 1;
        Integer amount = entryAmounts.get(entryIndex);
        return Math.max(1, amount == null ? 1 : amount);
    }

    public void setAmountFor(int entryIndex, int amount) {
        resizeAmountSettings(entryIndex + 1);
        entryAmounts.set(entryIndex, Math.max(1, amount));
    }

    public void resizeAmountSettings(int targetSize) {
        while (entryAmountModes.size() < targetSize) entryAmountModes.add(AmountMode.FULL_STK);
        while (entryAmountModes.size() > targetSize) entryAmountModes.remove(entryAmountModes.size() - 1);
        while (entryAmounts.size() < targetSize) entryAmounts.add(1);
        while (entryAmounts.size() > targetSize) entryAmounts.remove(entryAmounts.size() - 1);
    }

    public static int clampSafeClickDelayTicks(int ticks) {
        return Math.max(0, Math.min(10, ticks));
    }

    public java.util.Set<Integer> activeStorageSlotIds() {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        s.add(1); s.add(2); s.add(3); s.add(4);
        s.add(CRAFT_RESULT_SLOT);
        s.add(5); s.add(6); s.add(7); s.add(8);
        s.add(45);
        return s;
    }

    private java.util.List<Integer> putInAutoSlotIds() {
        return new java.util.ArrayList<>(activeStorageSlotIds());
    }

    private java.util.Set<Integer> actionSlotIds() {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        s.addAll(activeStorageSlotIds());
        return s;
    }

    public static boolean isCraftResultSlot(int slotId) {
        return slotId == CRAFT_RESULT_SLOT;
    }

    public void preparePutInStorageGuard() {
        markXCarryForcedForPutIn(carryCursor);
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) return;
        forceAllDestinationsEnabled();

        autismclient.util.AutismSharedState shared = autismclient.util.AutismSharedState.get();
        boolean prevBypass = shared.isXCarryArmorBypass();
        shared.setXCarryArmorBypass(true);
        try {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                executeWithContainerOpen(mc);
                return;
            }

            InventoryMenu handler = mc.player.inventoryMenu;
            if (!handler.getCarried().isEmpty()) {
                trimCursorToOne(handler, mc);
                updateXCarryState(handler, true);
                return;
            }

            switch (mode) {
                case PUT_IN -> executePutIn(handler, mc);
                case TAKE_OUT -> executeTakeOut(handler, mc);
                case DROP -> executeDrop(handler, mc);
            }
        } finally {
            shared.setXCarryArmorBypass(prevBypass);
        }
    }

    private void executeWithContainerOpen(Minecraft mc) {
        if (mc.getConnection() == null) return;
        forceAllDestinationsEnabled();

        AutismContainerTarget containerTarget = AutismSharedState.get().getLastContainerTarget();
        if (containerTarget == null) return;

        InventoryMenu playerHandler = mc.player.inventoryMenu;
        if (!playerHandler.getCarried().isEmpty()) {
            trimCursorToOne(playerHandler, mc);
            updateXCarryState(playerHandler, true);
            return;
        }

        AbstractContainerMenu containerHandler = mc.player.containerMenu;

        if (mode == Mode.PUT_IN) {
            for (int slotId : collectContainerTransferSlots(containerHandler, playerHandler)) {
                mc.gameMode.handleContainerInput(containerHandler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
            }
        }

        mc.getConnection().send(new ServerboundContainerClosePacket(containerHandler.containerId));
        mc.player.containerMenu = playerHandler;

        switch (mode) {
            case PUT_IN -> executePutIn(playerHandler, mc);
            case TAKE_OUT -> executeTakeOut(playerHandler, mc);
            case DROP -> executeDrop(playerHandler, mc);
        }

        mc.player.containerMenu = containerHandler;
        containerTarget.interact(mc);
    }

    private void executePutIn(InventoryMenu handler, Minecraft mc) {
        forceAllDestinationsEnabled();
        java.util.List<ItemTarget> targets = runtimeEntryTargets();
        if (targets.isEmpty()) {
            if (hasStoredItems(handler, true)) updateXCarryState(handler, true);
            return;
        }

        markXCarryForcedForPutIn(true);

        boolean moved = false;
        boolean cursorClaimed = false;
        for (int i = 0; i < targets.size(); i++) {
            if (destinationFor(i) != DEST_CURSOR) continue;
            if (cursorClaimed || !handler.getCarried().isEmpty()) continue;
            if (moveOneItemToCursor(handler, mc, i, i + 1)) {
                moved = true;
                cursorClaimed = true;
            }
        }

        int safety = 0;
        while (safety++ < 512) {
            PutInMove move = findNextPutInMove(handler);
            if (move == null) break;
            if (!movePlannedCount(handler, mc, move.sourceSlotId, move.targetSlotId, move.count)) break;
            moved = true;
        }

        int gridCapacity = putInAutoSlotIds().size();
        if (!cursorClaimed && handler.getCarried().isEmpty() && targets.size() > gridCapacity) {
            boolean anyExplicit = false;
            for (int i = 0; i < targets.size(); i++) {
                int dest = destinationFor(i);
                if (dest != DEST_AUTO && dest != DEST_CURSOR) {
                    anyExplicit = true;
                    break;
                }
            }
            if (!anyExplicit && moveOneItemToCursor(handler, mc, gridCapacity, gridCapacity + CURSOR_ENTRY_LIMIT)) {
                moved = true;
            }
        }

        updateXCarryState(handler, true);
    }

    private void executeTakeOut(InventoryMenu handler, Minecraft mc) {
        forceAllDestinationsEnabled();
        for (Integer slotId : actionSlotIds()) {
            if (slotId == null || slotId < 0 || slotId >= handler.slots.size()) continue;
            Slot slot = handler.slots.get(slotId);
            if (slot.getItem().isEmpty()) continue;
            if (!entries.isEmpty() && !matchesCraftingSlot(slot, slotId)) continue;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
        }
        updateXCarryState(handler, true);
    }

    private void executeDrop(InventoryMenu handler, Minecraft mc) {
        forceAllDestinationsEnabled();
        for (Integer slotId : actionSlotIds()) {
            if (slotId == null || slotId < 0 || slotId >= handler.slots.size()) continue;
            Slot slot = handler.slots.get(slotId);
            if (slot.getItem().isEmpty()) continue;
            if (!entries.isEmpty() && !matchesCraftingSlot(slot, slotId)) continue;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, 1, ContainerInput.THROW, mc.player);
        }
        updateXCarryState(handler, true);
    }

    private boolean matchesCraftingSlot(Slot slot, int slotId) {
        for (ItemTarget entryTarget : runtimeEntryTargets()) {
            if (entryTarget == null) continue;
            if (entryTarget.hasSlot()) {
                if (matchesConfiguredStorageSlot(entryTarget.slot, slotId)
                        && (!entryTarget.hasIdentity() || matchesDestinationStack(slot.getItem(), entryTarget))) return true;
            } else if (entryTarget.hasIdentity() && matchesTarget(slot.getItem(), entryTarget, slotId)) {
                return true;
            }
        }
        return false;
    }

    public PutInMove findNextPutInMove(InventoryMenu handler) {
        java.util.List<PutInMove> moves = planPutInMoves(handler);
        return moves.isEmpty() ? null : moves.get(0);
    }

    public java.util.List<PutInMove> planPutInMoves(InventoryMenu handler) {
        java.util.ArrayList<PutInMove> moves = new java.util.ArrayList<>();
        if (handler == null) return moves;

        forceAllDestinationsEnabled();
        java.util.Set<Integer> storageSlotSet = activeStorageSlotIds();
        java.util.List<Integer> storageSlots = putInAutoSlotIds();
        List<ItemTarget> targets = runtimeEntryTargets();
        int limit = Math.min(maxConfiguredEntries(), targets.size());
        resizeDestinations(limit);
        resizeAmountSettings(limit);
        int nextTargetIdx = 0;
        java.util.Set<Integer> claimedDestinations = new java.util.HashSet<>();
        java.util.List<PutInRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int dest = destinationFor(i);
            if (dest == DEST_CURSOR) continue;

            int targetSlotId = -1;
            if (dest == DEST_AUTO) {
                while (nextTargetIdx < storageSlots.size()) {
                    int candidate = storageSlots.get(nextTargetIdx);
                    if (candidate < 0 || candidate >= handler.slots.size()) { nextTargetIdx++; continue; }
                    if (claimedDestinations.contains(candidate)) { nextTargetIdx++; continue; }
                    ItemStack stack = handler.slots.get(candidate).getItem();
                    if (stack.isEmpty() || matchesDestinationStack(stack, targets.get(i))) break;
                    nextTargetIdx++;
                }
                if (nextTargetIdx >= storageSlots.size()) continue;
                targetSlotId = storageSlots.get(nextTargetIdx++);
            } else {
                targetSlotId = dest;
                if (targetSlotId < 0 || targetSlotId >= handler.slots.size()) continue;
            }

            Slot destSlot = handler.slots.get(targetSlotId);
            ItemStack destStack = destSlot.getItem();
            ItemTarget target = targets.get(i);
            if (!destStack.isEmpty() && !matchesDestinationStack(destStack, target)) continue;
            int maxCount = slotCapacity(destSlot, destStack.isEmpty() ? firstMatchingSourceStack(handler, target, storageSlotSet) : destStack);
            int currentCount = destStack.isEmpty() ? 0 : destStack.getCount();
            if (maxCount <= currentCount) continue;
            claimedDestinations.add(targetSlotId);
            rows.add(new PutInRow(i, target, targetSlotId, amountModeFor(i), amountFor(i), currentCount, maxCount));
        }

        if (rows.isEmpty()) return moves;
        allocatePutInRows(handler, rows, storageSlotSet);
        appendOptimizedPutInMoves(handler, rows, storageSlotSet, moves);
        return moves;
    }

    private static void appendOptimizedPutInMoves(InventoryMenu handler, java.util.List<PutInRow> rows,
                                                  java.util.Set<Integer> storageSlotSet,
                                                  java.util.List<PutInMove> moves) {
        if (handler == null || rows == null || rows.isEmpty() || moves == null) return;
        java.util.Map<Integer, Integer> sourceUsed = new java.util.HashMap<>();
        java.util.LinkedHashMap<String, java.util.List<PutInRow>> byTarget = new java.util.LinkedHashMap<>();
        for (PutInRow row : rows) {
            if (row == null || row.allocated <= 0 || row.target == null) continue;
            byTarget.computeIfAbsent(targetKey(row.target), ignored -> new java.util.ArrayList<>()).add(row);
        }

        for (java.util.List<PutInRow> group : byTarget.values()) {
            if (group == null || group.isEmpty()) continue;
            int totalDemand = 0;
            for (PutInRow row : group) totalDemand += Math.max(0, row.allocated);
            if (totalDemand <= 0) continue;

            int sourceSlot = findBestPutInSourceSlot(handler, group.get(0).target, storageSlotSet, sourceUsed, totalDemand);
            if (sourceSlot >= 0) {
                appendPutInMovesFromSingleSource(handler, group, sourceSlot, sourceUsed, moves);
                continue;
            }

            for (PutInRow row : group) {
                int remaining = row.allocated;
                if (remaining <= 0) continue;
                for (Slot slot : handler.slots) {
                    if (remaining <= 0) break;
                    if (!canUsePutInSourceSlot(handler, slot, row.target, storageSlotSet, sourceUsed)) continue;
                    if (slot.index == row.targetSlotId) continue;
                    int used = sourceUsed.getOrDefault(slot.index, 0);
                    int available = slot.getItem().getCount() - used;
                    if (available <= 0) continue;
                    int take = Math.min(remaining, available);
                    moves.add(new PutInMove(slot.index, row.targetSlotId, take));
                    sourceUsed.put(slot.index, used + take);
                    remaining -= take;
                }
            }
        }
    }

    private static int findBestPutInSourceSlot(InventoryMenu handler, ItemTarget target,
                                               java.util.Set<Integer> storageSlotSet,
                                               java.util.Map<Integer, Integer> sourceUsed,
                                               int totalDemand) {
        if (handler == null || target == null || totalDemand <= 0) return -1;
        int exact = -1;
        int smallestEnough = -1;
        int smallestEnoughCount = Integer.MAX_VALUE;
        for (Slot slot : handler.slots) {
            if (!canUsePutInSourceSlot(handler, slot, target, storageSlotSet, sourceUsed)) continue;
            int available = slot.getItem().getCount() - sourceUsed.getOrDefault(slot.index, 0);
            if (available <= 0) continue;
            if (available == totalDemand) {
                exact = slot.index;
                break;
            }
            if (available > totalDemand && available < smallestEnoughCount) {
                smallestEnough = slot.index;
                smallestEnoughCount = available;
            }
        }
        return exact >= 0 ? exact : smallestEnough;
    }

    private static boolean canUsePutInSourceSlot(InventoryMenu handler, Slot slot, ItemTarget target,
                                                 java.util.Set<Integer> storageSlotSet,
                                                 java.util.Map<Integer, Integer> sourceUsed) {
        if (handler == null || slot == null || target == null || storageSlotSet == null) return false;
        if (storageSlotSet.contains(slot.index) || !isEligibleSourceSlot(slot)) return false;
        if (!matchesSourceSlot(handler, slot, target)) return false;
        int available = slot.getItem().getCount() - (sourceUsed == null ? 0 : sourceUsed.getOrDefault(slot.index, 0));
        return available > 0;
    }

    private static void appendPutInMovesFromSingleSource(InventoryMenu handler, java.util.List<PutInRow> rows,
                                                         int sourceSlotId,
                                                         java.util.Map<Integer, Integer> sourceUsed,
                                                         java.util.List<PutInMove> moves) {
        if (handler == null || rows == null || sourceUsed == null || moves == null) return;
        if (sourceSlotId < 0 || sourceSlotId >= handler.slots.size()) return;
        Slot sourceSlot = handler.slots.get(sourceSlotId);
        if (sourceSlot == null || sourceSlot.getItem().isEmpty()) return;
        int used = sourceUsed.getOrDefault(sourceSlotId, 0);
        int available = sourceSlot.getItem().getCount() - used;
        for (PutInRow row : rows) {
            if (row == null || row.allocated <= 0 || available <= 0) continue;
            if (row.targetSlotId == sourceSlotId) continue;
            int take = Math.min(row.allocated, available);
            if (take <= 0) continue;
            moves.add(new PutInMove(sourceSlotId, row.targetSlotId, take));
            available -= take;
            used += take;
        }
        sourceUsed.put(sourceSlotId, used);
    }

    public boolean requiresCursorStorage() {
        forceAllDestinationsEnabled();
        List<ItemTarget> targets = runtimeEntryTargets();
        int limit = Math.min(maxConfiguredEntries(), targets.size());
        boolean anyExplicit = false;
        int autoCount = 0;
        for (int i = 0; i < limit; i++) {
            int dest = destinationFor(i);
            if (dest == DEST_CURSOR) return true;
            if (dest == DEST_AUTO) autoCount++;
            else anyExplicit = true;
        }
        return !anyExplicit && autoCount > putInAutoSlotIds().size();
    }

    public List<Integer> collectContainerTransferSlots(AbstractContainerMenu containerHandler) {
        return collectContainerTransferSlots(containerHandler, null);
    }

    public List<Integer> collectContainerTransferSlots(AbstractContainerMenu containerHandler, InventoryMenu playerHandler) {
        List<Integer> slotIds = new ArrayList<>();
        if (containerHandler == null) return slotIds;

        List<ItemTarget> targets = runtimeEntryTargets();
        int limit = Math.min(maxConfiguredEntries(), targets.size());
        java.util.Map<String, Integer> neededByKey = new java.util.LinkedHashMap<>();
        if (playerHandler != null) {
            java.util.List<PutInRow> rows = previewPutInRows(playerHandler);
            allocatePutInRows(playerHandler, rows, activeStorageSlotIds());
            for (PutInRow row : rows) {
                if (row.allocated <= 0 || row.target == null || !row.target.hasIdentity()) continue;
                String key = targetKey(row.target);
                neededByKey.put(key, neededByKey.getOrDefault(key, 0) + row.allocated);
            }
        }
        java.util.Set<Integer> usedContainerSlots = new java.util.HashSet<>();
        for (int i = 0; i < limit; i++) {
            ItemTarget itemTarget = targets.get(i);
            if (itemTarget == null || itemTarget.hasSlot() || !itemTarget.hasIdentity()) continue;
            String key = targetKey(itemTarget);
            if (playerHandler != null && !neededByKey.isEmpty() && neededByKey.getOrDefault(key, 0) <= 0) continue;
            for (Slot slot : containerHandler.slots) {
                if (usedContainerSlots.contains(slot.index)) continue;
                if (slot.getItem().isEmpty()) continue;
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (!matchesTarget(slot.getItem(), itemTarget, -1)) continue;
                slotIds.add(slot.index);
                usedContainerSlots.add(slot.index);
                if (playerHandler != null) {
                    neededByKey.put(key, neededByKey.getOrDefault(key, 0) - slot.getItem().getCount());
                }
                break;
            }
        }

        return slotIds;
    }

    @Override
    public CompoundTag toTag() {
        forceAllDestinationsEnabled();
        CompoundTag tag = new CompoundTag();
        tag.putString("type", MacroActionType.XCARRY.name());
        tag.putString("mode", mode.name());
        tag.putString("transferMode", transferMode.name());
        tag.putInt("safeClickDelayTicks", clampSafeClickDelayTicks(safeClickDelayTicks));
        tag.putBoolean("safeClickDelayAfterPickup", safeClickDelayAfterPickup);
        tag.putBoolean("safeClickDelayBeforeReturn", safeClickDelayBeforeReturn);
        tag.putBoolean("carryCursor", true);
        tag.putBoolean("useCrafting", true);
        tag.putBoolean("useArmor", true);
        tag.putBoolean("useOffhand", true);
        List<ItemTarget> targets = resolvedEntryTargets();
        int savedCount = Math.min(maxConfiguredEntries(), targets.size());
        tag.put("entries", ItemTarget.toTagList(targets.subList(0, savedCount)));
        resizeDestinations(savedCount);
        resizeAmountSettings(savedCount);
        ListTag destinations = new ListTag();
        ListTag amountModes = new ListTag();
        ListTag amounts = new ListTag();
        for (int i = 0; i < savedCount; i++) {
            destinations.add(StringTag.valueOf(String.valueOf(destinationFor(i))));
            amountModes.add(StringTag.valueOf(amountModeFor(i).name()));
            amounts.add(StringTag.valueOf(String.valueOf(amountFor(i))));
        }
        tag.put("entryDestinations", destinations);
        tag.put("entryAmountModes", amountModes);
        tag.put("entryAmounts", amounts);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        entries.clear();
        entryTargets.clear();
        entryDestinations.clear();
        entryAmountModes.clear();
        entryAmounts.clear();
        String modeStr = tag.getStringOr("mode", "PUT_IN");
        try {
            mode = Mode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            mode = Mode.PUT_IN;
        }

        String transferModeStr = tag.getStringOr("transferMode", "FAST");
        try {
            transferMode = TransferMode.valueOf(transferModeStr);
        } catch (IllegalArgumentException e) {
            transferMode = TransferMode.FAST;
        }
        safeClickDelayTicks = clampSafeClickDelayTicks(tag.getIntOr("safeClickDelayTicks", 1));
        safeClickDelayAfterPickup = tag.getBooleanOr("safeClickDelayAfterPickup", true);
        safeClickDelayBeforeReturn = tag.getBooleanOr("safeClickDelayBeforeReturn", true);

        forceAllDestinationsEnabled();
        if (tag.contains("entries")) {
            ListTag list = tag.getList("entries").orElse(new ListTag());
            for (ItemTarget target : ItemTarget.fromElementList(list)) {
                if (target == null || (!target.hasSlot() && !target.hasIdentity())) continue;
                entryTargets.add(target);
                if (entryTargets.size() >= maxConfiguredEntries()) break;
            }
        }
        if (tag.contains("entryDestinations")) {
            ListTag destinations = tag.getList("entryDestinations").orElse(new ListTag());
            for (Tag element : destinations) {
                int dest = DEST_AUTO;
                try {
                    dest = Integer.parseInt(element.asString().orElse(String.valueOf(DEST_AUTO)));
                } catch (NumberFormatException ignored) {  }
                entryDestinations.add(dest);
            }
        }
        if (tag.contains("entryAmountModes")) {
            ListTag modes = tag.getList("entryAmountModes").orElse(new ListTag());
            for (Tag element : modes) {
                AmountMode amountMode = AmountMode.FULL_STK;
                try {
                    amountMode = AmountMode.valueOf(element.asString().orElse(AmountMode.FULL_STK.name()));
                } catch (IllegalArgumentException ignored) {  }
                entryAmountModes.add(amountMode);
            }
        }
        if (tag.contains("entryAmounts")) {
            ListTag amounts = tag.getList("entryAmounts").orElse(new ListTag());
            for (Tag element : amounts) {
                int amount = 1;
                try {
                    amount = Integer.parseInt(element.asString().orElse("1"));
                } catch (NumberFormatException ignored) {  }
                entryAmounts.add(Math.max(1, amount));
            }
        }
        resizeDestinations(entryTargets.size());
        resizeAmountSettings(entryTargets.size());
        syncLegacyEntries();

        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.XCARRY;
    }

    @Override
    public String getDisplayName() {
        String prefix = switch (mode) {
            case PUT_IN -> switch (transferMode) {
                case CLICK -> "XCarry Click";
                case SAFE_CLICK -> "XCarry Safe";
                case FAST -> "XCarry";
            };
            case TAKE_OUT -> "XCarry Out";
            case DROP -> "XCarry Drop";
        };
        forceAllDestinationsEnabled();

        if (entries.isEmpty()) return prefix;
        String first = formatEntry(entries.get(0));
        if (entries.size() > 1) first += " (+" + (entries.size() - 1) + ")";
        return prefix + " [" + first + "]";
    }

    @Override
    public String getIcon() {
        return "XC";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static String normalizeEntry(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("#")) return trimmed;
        int separator = trimmed.indexOf('|');
        String slotPart = separator >= 0 ? trimmed.substring(1, separator) : trimmed.substring(1);
        try {
            int slot = Integer.parseInt(slotPart.trim());
            if (slot < 0) return null;
            String name = separator >= 0 && separator + 1 < trimmed.length()
                    ? trimmed.substring(separator + 1).trim()
                    : "";
            if (!name.isEmpty()) return "#" + slot + "|" + name;
            return "#" + slot;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String formatEntry(String entry) {
        String normalized = normalizeEntry(entry);
        if (normalized == null) return "";
        int slot = parseEntrySlot(normalized);
        String name = parseEntryName(normalized);
        if (slot >= 0 && !name.isEmpty()) return name + " @ Slot " + slot;
        if (slot >= 0) return "Slot " + slot;
        return normalized;
    }

    private static int findSourceSlot(InventoryMenu handler, ItemTarget target) {
        return findSourceSlot(handler, target, java.util.Set.of());
    }

    private static int findSourceSlot(InventoryMenu handler, ItemTarget target, java.util.Set<Integer> excludedSlotIds) {
        if (target == null) return -1;
        java.util.Set<Integer> excluded = excludedSlotIds == null ? java.util.Set.of() : excludedSlotIds;

        if (target.hasSlot()) {
            int exactSlot = resolvePlayerVisibleSlot(handler, target.slot);
            if (exactSlot < 0) return -1;
            if (excluded.contains(exactSlot)) return -1;
            if (!target.hasIdentity()) return exactSlot;
            Slot slot = handler.slots.get(exactSlot);
            return matchesTarget(slot.getItem(), target, target.slot) ? exactSlot : -1;
        }

        for (Slot slot : handler.slots) {
            if (excluded.contains(slot.index)) continue;
            if (!isEligibleSourceSlot(slot)) continue;
            int visibleSlot = resolveVisibleSlotForPlayerSlot(handler, slot.index);
            if (matchesTarget(slot.getItem(), target, visibleSlot)) return slot.index;
        }
        return -1;
    }

    public static boolean movePlannedCount(InventoryMenu handler, Minecraft mc, int fromSlotId, int toSlotId, int count) {
        if (count <= 0) return false;
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (fromSlotId < 0 || fromSlotId >= handler.slots.size()) return false;
        if (toSlotId < 0 || toSlotId >= handler.slots.size()) return false;
        if (fromSlotId == toSlotId) return true;
        if (!handler.getCarried().isEmpty()) return false;

        Slot sourceSlot = handler.slots.get(fromSlotId);
        Slot targetSlot = handler.slots.get(toSlotId);
        ItemStack sourceBefore = sourceSlot.getItem();
        if (sourceBefore.isEmpty()) return false;
        ItemStack expectedSource = sourceBefore.copy();
        ItemStack targetBefore = targetSlot.getItem();
        if (!targetBefore.isEmpty() && !ItemStack.isSameItemSameComponents(sourceBefore, targetBefore)) return false;
        int targetCapacity = slotCapacity(targetSlot, targetBefore.isEmpty() ? sourceBefore : targetBefore);
        int targetCountBefore = targetBefore.isEmpty() ? 0 : targetBefore.getCount();
        int amount = Math.min(count, Math.min(expectedSource.getCount(), Math.max(0, targetCapacity - targetCountBefore)));
        if (amount <= 0) return false;

        if (amount == expectedSource.getCount() && targetBefore.isEmpty()) {
            return moveStack(handler, mc, fromSlotId, toSlotId);
        }

        mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
        if (handler.getCarried().isEmpty()) return false;

        int trimSafety = Math.max(0, handler.getCarried().getCount() - amount);
        while (!handler.getCarried().isEmpty() && handler.getCarried().getCount() > amount && trimSafety-- > 0) {
            mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 1, ContainerInput.PICKUP, mc.player);
        }

        if (handler.getCarried().isEmpty() || handler.getCarried().getCount() != amount) {
            returnCarriedStack(handler, mc, fromSlotId);
            return false;
        }

        mc.gameMode.handleContainerInput(handler.containerId, toSlotId, 0, ContainerInput.PICKUP, mc.player);

        if (!handler.getCarried().isEmpty()) {
            returnCarriedStack(handler, mc, fromSlotId);
        }

        ItemStack targetAfter = targetSlot.getItem();
        return !targetAfter.isEmpty()
                && ItemStack.isSameItemSameComponents(targetAfter, expectedSource)
                && targetAfter.getCount() >= targetCountBefore + amount
                && handler.getCarried().isEmpty();
    }

    private static boolean returnCarriedStack(InventoryMenu handler, Minecraft mc, int preferredSlotId) {
        if (handler == null || mc == null || mc.player == null || mc.gameMode == null) return false;
        if (handler.getCarried().isEmpty()) return true;
        int returnSlot = preferredSlotId;
        if (returnSlot < 0 || returnSlot >= handler.slots.size() || !canReturnToSlot(handler.slots.get(returnSlot), handler.getCarried())) {
            returnSlot = findCursorReturnSlot(handler, handler.getCarried());
        }
        if (returnSlot < 0 || returnSlot >= handler.slots.size()) return false;
        mc.gameMode.handleContainerInput(handler.containerId, returnSlot, 0, ContainerInput.PICKUP, mc.player);
        return handler.getCarried().isEmpty();
    }

    private static boolean moveStack(InventoryMenu handler, Minecraft mc, int fromSlotId, int toSlotId) {
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (fromSlotId < 0 || fromSlotId >= handler.slots.size()) return false;
        if (toSlotId < 0 || toSlotId >= handler.slots.size()) return false;
        if (fromSlotId == toSlotId) return true;
        if (!handler.getCarried().isEmpty()) return false;

        if (handler.slots.get(fromSlotId).getItem().isEmpty()) return false;
        if (!handler.slots.get(toSlotId).getItem().isEmpty()) return false;

        mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
        if (handler.getCarried().isEmpty()) return false;

        mc.gameMode.handleContainerInput(handler.containerId, toSlotId, 0, ContainerInput.PICKUP, mc.player);
        if (handler.getCarried().isEmpty()) {
            return handler.slots.get(fromSlotId).getItem().isEmpty()
                    && !handler.slots.get(toSlotId).getItem().isEmpty();
        }

        mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
        return false;
    }

    private boolean moveOneItemToCursor(InventoryMenu handler, Minecraft mc, int startTargetIndex, int maxTargets) {
        if (handler == null || mc == null || mc.player == null || mc.gameMode == null) return false;
        if (!handler.getCarried().isEmpty()) return trimCursorToOne(handler, mc);

        List<ItemTarget> targets = runtimeEntryTargets();
        java.util.Set<Integer> storageSlotSet = activeStorageSlotIds();
        int limit = Math.min(maxTargets, targets.size());
        for (int i = Math.max(0, startTargetIndex); i < limit; i++) {
            int sourceSlotId = findSourceSlot(handler, targets.get(i), storageSlotSet);
            if (sourceSlotId < 0 || sourceSlotId >= handler.slots.size()) continue;
            Slot sourceSlot = handler.slots.get(sourceSlotId);
            if (sourceSlot == null || sourceSlot.getItem().isEmpty()) continue;

            ItemStack expected = sourceSlot.getItem().copy();
            mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 0, ContainerInput.PICKUP, mc.player);
            if (handler.getCarried().isEmpty()) return false;

            int safety = Math.max(0, handler.getCarried().getCount() - 1);
            while (handler.getCarried().getCount() > 1 && safety-- > 0) {
                mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 1, ContainerInput.PICKUP, mc.player);
            }

            ItemStack carried = handler.getCarried();
            return !carried.isEmpty()
                    && carried.getCount() == 1
                    && ItemStack.isSameItemSameComponents(carried, expected);
        }
        return false;
    }

    private static boolean trimCursorToOne(InventoryMenu handler, Minecraft mc) {
        if (handler == null || mc == null || mc.player == null || mc.gameMode == null) return false;
        if (handler.getCarried().isEmpty()) return false;
        if (handler.getCarried().getCount() <= 1) return true;

        int safety = Math.max(0, handler.getCarried().getCount() - 1);
        while (!handler.getCarried().isEmpty() && handler.getCarried().getCount() > 1 && safety-- > 0) {
            int returnSlotId = findCursorReturnSlot(handler, handler.getCarried());
            if (returnSlotId < 0) break;
            mc.gameMode.handleContainerInput(handler.containerId, returnSlotId, 1, ContainerInput.PICKUP, mc.player);
        }
        return !handler.getCarried().isEmpty() && handler.getCarried().getCount() == 1;
    }

    private static int findCursorReturnSlot(InventoryMenu handler, ItemStack carried) {
        if (handler == null || carried == null || carried.isEmpty()) return -1;
        for (Slot slot : handler.slots) {
            if (slot == null || slot.index < 5) continue;
            if (slot.index >= 5 && slot.index <= 8) continue;
            if (slot.index == 45) continue;
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return slot.index;
            if (ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack))) {
                return slot.index;
            }
        }
        return -1;
    }

    private static boolean canReturnToSlot(Slot slot, ItemStack carried) {
        if (slot == null || carried == null || carried.isEmpty()) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(stack, carried)
                && stack.getCount() < slotCapacity(slot, stack);
    }

    private static int slotCapacity(Slot slot, ItemStack stack) {
        if (slot == null || stack == null || stack.isEmpty()) return 0;
        return Math.max(0, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack)));
    }

    private ItemStack firstMatchingSourceStack(InventoryMenu handler, ItemTarget target, java.util.Set<Integer> excludedSlotIds) {
        if (handler == null || target == null) return ItemStack.EMPTY;
        for (Slot slot : handler.slots) {
            if (slot == null || excludedSlotIds.contains(slot.index) || !isEligibleSourceSlot(slot)) continue;
            if (matchesSourceSlot(handler, slot, target)) return slot.getItem();
        }
        return ItemStack.EMPTY;
    }

    private java.util.List<PutInRow> previewPutInRows(InventoryMenu handler) {
        java.util.List<PutInRow> rows = new java.util.ArrayList<>();
        if (handler == null) return rows;
        java.util.Set<Integer> storageSlotSet = activeStorageSlotIds();
        java.util.List<Integer> storageSlots = putInAutoSlotIds();
        List<ItemTarget> targets = runtimeEntryTargets();
        int limit = Math.min(maxConfiguredEntries(), targets.size());
        resizeDestinations(limit);
        resizeAmountSettings(limit);
        int nextTargetIdx = 0;
        java.util.Set<Integer> claimedDestinations = new java.util.HashSet<>();
        for (int i = 0; i < limit; i++) {
            int dest = destinationFor(i);
            if (dest == DEST_CURSOR) continue;
            int targetSlotId;
            if (dest == DEST_AUTO) {
                targetSlotId = -1;
                while (nextTargetIdx < storageSlots.size()) {
                    int candidate = storageSlots.get(nextTargetIdx++);
                    if (candidate < 0 || candidate >= handler.slots.size()) continue;
                    if (claimedDestinations.contains(candidate)) continue;
                    targetSlotId = candidate;
                    break;
                }
                if (targetSlotId < 0) continue;
            } else {
                targetSlotId = dest;
                if (targetSlotId < 0 || targetSlotId >= handler.slots.size()) continue;
            }
            Slot destSlot = handler.slots.get(targetSlotId);
            ItemStack destStack = destSlot.getItem();
            ItemTarget target = targets.get(i);
            if (!destStack.isEmpty() && !matchesDestinationStack(destStack, target)) continue;
            ItemStack sample = destStack.isEmpty() ? firstMatchingSourceStack(handler, target, storageSlotSet) : destStack;
            int maxCount = slotCapacity(destSlot, sample);
            int currentCount = destStack.isEmpty() ? 0 : destStack.getCount();
            if (maxCount <= currentCount) continue;
            claimedDestinations.add(targetSlotId);
            rows.add(new PutInRow(i, target, targetSlotId, amountModeFor(i), amountFor(i), currentCount, maxCount));
        }
        return rows;
    }

    private static void allocatePutInRows(InventoryMenu handler, java.util.List<PutInRow> rows, java.util.Set<Integer> excludedSlotIds) {
        if (handler == null || rows == null || rows.isEmpty()) return;
        java.util.Map<String, Integer> available = new java.util.LinkedHashMap<>();
        for (Slot slot : handler.slots) {
            if (slot == null || excludedSlotIds.contains(slot.index) || !isEligibleSourceSlot(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            for (PutInRow row : rows) {
                if (row.target == null || !matchesSourceSlot(handler, slot, row.target)) continue;
                String key = targetKey(row.target);
                available.put(key, available.getOrDefault(key, 0) + stack.getCount());
                break;
            }
        }

        java.util.Map<String, java.util.List<PutInRow>> byKey = new java.util.LinkedHashMap<>();
        for (PutInRow row : rows) {
            if (row.target == null) continue;
            byKey.computeIfAbsent(targetKey(row.target), ignored -> new java.util.ArrayList<>()).add(row);
        }

        for (java.util.Map.Entry<String, java.util.List<PutInRow>> entry : byKey.entrySet()) {
            int remaining = available.getOrDefault(entry.getKey(), 0);
            for (PutInRow row : entry.getValue()) remaining += row.currentCount;
            java.util.List<PutInRow> fullRows = new java.util.ArrayList<>();
            for (PutInRow row : entry.getValue()) {
                row.allocated = 0;
                row.desiredFinalCount = row.currentCount;
                if (row.amountMode == AmountMode.CUSTOM) {
                    int desired = Math.min(Math.min(row.customCount, row.maxCount), Math.max(0, remaining));
                    int reserved = Math.max(row.currentCount, desired);
                    row.desiredFinalCount = desired;
                    row.allocated = Math.max(0, desired - row.currentCount);
                    remaining = Math.max(0, remaining - reserved);
                } else {
                    fullRows.add(row);
                }
            }
            if (!fullRows.isEmpty()) {
                int totalForFull = remaining;
                java.util.Map<PutInRow, Integer> desired = new java.util.LinkedHashMap<>();
                for (PutInRow row : fullRows) desired.put(row, 0);
                while (totalForFull > 0) {
                    int active = 0;
                    for (PutInRow row : fullRows) if (desired.get(row) < row.maxCount) active++;
                    if (active <= 0) break;
                    int share = Math.max(1, totalForFull / active);
                    boolean progressed = false;
                    for (PutInRow row : fullRows) {
                        if (totalForFull <= 0) break;
                        int currentDesired = desired.get(row);
                        int room = row.maxCount - currentDesired;
                        if (room <= 0) continue;
                        int take = Math.min(room, Math.min(share, totalForFull));
                        desired.put(row, currentDesired + take);
                        totalForFull -= take;
                        progressed = true;
                    }
                    if (!progressed) break;
                }
                for (PutInRow row : fullRows) {
                    int finalCount = desired.getOrDefault(row, 0);
                    row.desiredFinalCount = finalCount;
                    row.allocated = Math.max(0, finalCount - row.currentCount);
                }
            }
        }
    }

    private static final class PutInRow {
        final int index;
        final ItemTarget target;
        final int targetSlotId;
        final AmountMode amountMode;
        final int customCount;
        final int currentCount;
        final int maxCount;
        int allocated;
        int desiredFinalCount;

        PutInRow(int index, ItemTarget target, int targetSlotId, AmountMode amountMode, int customCount, int currentCount, int maxCount) {
            this.index = index;
            this.target = target;
            this.targetSlotId = targetSlotId;
            this.amountMode = amountMode;
            this.customCount = customCount;
            this.currentCount = currentCount;
            this.maxCount = maxCount;
        }
    }

    public static boolean hasCraftingGridItems(InventoryMenu handler) {
        if (handler == null) return false;
        for (int slotId = 1; slotId <= 4 && slotId < handler.slots.size(); slotId++) {
            if (!handler.slots.get(slotId).getItem().isEmpty()) return true;
        }
        return false;
    }

    public static boolean hasCursorItem(InventoryMenu handler) {
        return handler != null && !handler.getCarried().isEmpty();
    }

    public static boolean hasStoredItems(InventoryMenu handler, boolean includeCursor) {
        return hasCraftingGridItems(handler) || (includeCursor && hasCursorItem(handler));
    }

    public static boolean hasStoredItems(InventoryMenu handler, boolean includeCursor, java.util.Set<Integer> slotIds) {
        if (handler == null) return false;
        if (slotIds != null) {
            for (Integer id : slotIds) {
                if (id == null || id < 0 || id >= handler.slots.size()) continue;
                if (!handler.slots.get(id).getItem().isEmpty()) return true;
            }
        }
        return includeCursor && hasCursorItem(handler);
    }

    private void updateXCarryState(InventoryMenu handler, boolean includeCursor) {
        boolean active = hasStoredItems(handler, includeCursor, activeStorageSlotIds());
        AutismSharedState shared = AutismSharedState.get();
        if (active) {
            shared.mergeXCarryForcedTargets(activeStorageSlotIds(), includeCursor);
            shared.setXCarryForced(true);
            shared.setXCarryActive(true);
            return;
        }

        if (shared.isXCarryForced()) {
            boolean stillHeld = hasStoredItems(
                    handler,
                    shared.isXCarryForcedCarryCursor(),
                    shared.getXCarryForcedSlotMask());
            if (stillHeld) {
                shared.setXCarryActive(true);
                return;
            }
            shared.setXCarryForced(false);
        }
        shared.setXCarryActive(false);
    }

    private void markXCarryForcedForPutIn(boolean includeCursor) {
        AutismSharedState shared = AutismSharedState.get();
        shared.mergeXCarryForcedTargets(activeStorageSlotIds(), includeCursor);
        shared.setXCarryForced(true);
        shared.setXCarryActive(true);
    }

    public int maxConfiguredEntries() {

        return MAX_ENTRIES;
    }

    public static int maxEntriesFor(boolean carryCursor) {
        return STORAGE_ENTRY_LIMIT + (carryCursor ? CURSOR_ENTRY_LIMIT : 0);
    }

    static void sendOpenTarget(Minecraft mc, AutismContainerTarget target) {
        if (target == null) return;
        target.interact(mc);
    }

    private static boolean isEligibleSourceSlot(Slot slot) {
        return slot != null && slot.index >= 5 && !slot.getItem().isEmpty();
    }

    private static int resolvePlayerVisibleSlot(AbstractContainerMenu handler, int configuredSlot) {
        for (Slot slot : handler.slots) {
            if (slot == null || slot.index < 0 || slot.index >= handler.slots.size()) continue;
            if (slot.index <= 4) continue;
            if (slot.index >= 5 && slot.index <= 8) continue;
            if (slot.index == 45) continue;
            if (slot.container == null) continue;
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                int visibleSlot = slot.getContainerSlot();
                if (visibleSlot == configuredSlot) return slot.index;
            }
        }
        return -1;
    }

    private static boolean matchesTarget(ItemStack stack, ItemTarget target, int visibleSlot) {
        if (stack == null || stack.isEmpty()) return false;
        if (target == null || !target.hasIdentity()) return true;
        return target.matches(stack, visibleSlot);
    }

    private static boolean matchesSourceSlot(InventoryMenu handler, Slot slot, ItemTarget target) {
        if (handler == null || slot == null || target == null || slot.getItem().isEmpty()) return false;
        int visibleSlot = resolveVisibleSlotForPlayerSlot(handler, slot.index);
        if (target.hasSlot() && target.slot != visibleSlot) return false;
        return matchesTarget(slot.getItem(), target, visibleSlot);
    }

    private static boolean matchesConfiguredStorageSlot(int configuredSlot, int handlerSlotId) {
        if (configuredSlot == handlerSlotId) return true;
        return configuredSlot == AutismInventoryHelper.FIRST_GUI_SLOT && handlerSlotId == CRAFT_RESULT_SLOT;
    }

    private static boolean matchesDestinationStack(ItemStack stack, ItemTarget target) {
        if (stack == null || stack.isEmpty()) return false;
        if (target == null || !target.hasIdentity()) return true;
        ItemTarget identityOnly = target.copy();
        identityOnly.slot = -1;
        return identityOnly.matches(stack, -1);
    }

    private static String targetKey(ItemTarget target) {
        if (target == null) return "";
        String legacy = normalizeEntry(target.toLegacyEntry());
        if (legacy != null && !legacy.isBlank()) return legacy.toLowerCase(java.util.Locale.ROOT);
        if (target.hasSlot()) return "#slot:" + target.slot;
        return target.editorText() == null ? "" : target.editorText().toLowerCase(java.util.Locale.ROOT);
    }

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

    private static int resolveVisibleSlotForPlayerSlot(InventoryMenu handler, int handlerSlotId) {
        if (handlerSlotId < 0 || handlerSlotId >= handler.slots.size()) return -1;
        Slot slot = handler.slots.get(handlerSlotId);
        if (slot == null || !(slot.container instanceof net.minecraft.world.entity.player.Inventory)) return -1;
        return slot.getContainerSlot();
    }

    private List<ItemTarget> resolvedEntryTargets() {
        if (!entryTargets.isEmpty()) return entryTargets;
        for (String entry : entries) {
            String normalized = normalizeEntry(entry);
            if (normalized == null) continue;
            ItemTarget target = ItemTarget.fromLegacyEntry(normalized);
            if (target.hasSlot() || target.hasIdentity()) entryTargets.add(target);
            if (entryTargets.size() >= maxConfiguredEntries()) break;
        }
        return entryTargets;
    }

    private List<ItemTarget> runtimeEntryTargets() {
        List<ItemTarget> runtime = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        for (ItemTarget target : resolvedEntryTargets()) {
            if (target == null) continue;
            ItemTarget resolved = target.resolveTemplate(mc);
            if (resolved != null && (resolved.hasSlot() || resolved.hasIdentity())) runtime.add(resolved);
        }
        return runtime;
    }

    private void syncLegacyEntries() {
        entries.clear();
        for (ItemTarget target : entryTargets) {
            if (target == null) continue;
            String entry = normalizeEntry(target.toLegacyEntry());
            if (entry == null) continue;
            entries.add(entry);
            if (entries.size() >= maxConfiguredEntries()) break;
        }
    }
}
