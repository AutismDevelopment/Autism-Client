package autismclient.util.multi;

import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.XCarryAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MultiXCarryPlanner {
    private static final int[] STORAGE = {1, 2, 3, 4, 0, 5, 6, 7, 8, 45};
    private static final Set<Integer> STORAGE_SET = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 45);
    private static final int MAX_CLICKS = 1024;

    record Click(int slot, int button, ContainerInput input, long delayAfterMs) {
    }

    private enum Pace {
        AFTER_PICKUP,
        BETWEEN_CLICKS,
        BEFORE_RETURN,
        COMPLETE
    }

    private static final class Row {
        final ItemTarget target;
        final int destination;
        final XCarryAction.AmountMode amountMode;
        final int customAmount;
        final int current;
        final int capacity;
        int allocated;

        Row(ItemTarget target, int destination, XCarryAction.AmountMode amountMode,
            int customAmount, int current, int capacity) {
            this.target = target;
            this.destination = destination;
            this.amountMode = amountMode;
            this.customAmount = customAmount;
            this.current = current;
            this.capacity = capacity;
        }
    }

    private MultiXCarryPlanner() {
    }

    static List<Click> plan(XCarryAction action, List<ItemStack> inventorySlots, ItemStack initialCursor) {
        if (action == null || inventorySlots == null || inventorySlots.size() < 46) return List.of();
        List<ItemStack> slots = copyStacks(inventorySlots);
        ItemStack[] cursor = {copy(initialCursor)};
        List<Click> clicks = new ArrayList<>();
        if (!cursor[0].isEmpty()) {
            planTrimCursor(action, slots, cursor, clicks);
            return List.copyOf(clicks);
        }
        List<ItemTarget> targets = targets(action);
        switch (action.mode == null ? XCarryAction.Mode.PUT_IN : action.mode) {
            case PUT_IN -> planPutIn(action, targets, slots, cursor, clicks);
            case TAKE_OUT -> planTakeOut(targets, slots, clicks);
            case DROP -> planDrop(targets, slots, clicks);
        }
        return List.copyOf(clicks);
    }

    private static void planTrimCursor(XCarryAction action, List<ItemStack> slots,
                                       ItemStack[] cursor, List<Click> clicks) {
        int safety = Math.max(0, cursor[0].getCount() - 1);
        while (!cursor[0].isEmpty() && cursor[0].getCount() > 1
            && safety-- > 0 && clicks.size() < MAX_CLICKS) {
            int destination = findCursorReturnSlot(slots, cursor[0]);
            if (destination < 0) break;
            addPickup(action, slots, cursor, destination, 1, clicks,
                cursor[0].getCount() > 2 ? Pace.BETWEEN_CLICKS : Pace.COMPLETE);
        }
    }

    private static int findCursorReturnSlot(List<ItemStack> slots, ItemStack cursor) {
        for (int slot = 9; slot <= 44; slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) return slot;
            if (ItemStack.isSameItemSameComponents(stack, cursor)
                && stack.getCount() < stack.getMaxStackSize()) return slot;
        }
        return -1;
    }

    static List<Integer> collectContainerSlots(XCarryAction action, List<ItemStack> menuSlots, int containerSlotCount) {
        if (action == null || action.mode != XCarryAction.Mode.PUT_IN || menuSlots == null || containerSlotCount <= 0) {
            return List.of();
        }
        List<ItemTarget> targets = targets(action);
        List<Integer> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (ItemTarget target : targets) {
            if (target == null || target.hasSlot() || !target.hasIdentity()) continue;
            for (int slot = 0; slot < containerSlotCount && slot < menuSlots.size(); slot++) {
                if (used.contains(slot) || !identityMatches(target, menuSlots.get(slot))) continue;
                result.add(slot);
                used.add(slot);
                break;
            }
        }
        return List.copyOf(result);
    }

    private static void planPutIn(XCarryAction action, List<ItemTarget> targets, List<ItemStack> slots,
                                  ItemStack[] cursor, List<Click> clicks) {
        if (targets.isEmpty()) return;
        int limit = Math.min(XCarryAction.MAX_ENTRIES, targets.size());
        Set<Integer> claimed = new HashSet<>();
        int autoIndex = 0;
        boolean explicitDestination = false;

        for (int i = 0; i < limit && cursor[0].isEmpty(); i++) {
            if (action.destinationFor(i) != XCarryAction.DEST_CURSOR) continue;
            int source = findSource(slots, targets.get(i));
            if (source >= 0) moveOneToCursor(action, slots, cursor, source, clicks);
        }

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ItemTarget target = targets.get(i);
            int configured = action.destinationFor(i);
            if (configured == XCarryAction.DEST_CURSOR) continue;
            int destination;
            if (configured == XCarryAction.DEST_AUTO) {
                destination = -1;
                while (autoIndex < STORAGE.length) {
                    int candidate = STORAGE[autoIndex++];
                    if (claimed.contains(candidate)) continue;
                    ItemStack current = slots.get(candidate);
                    if (current.isEmpty() || identityMatches(target, current)) {
                        destination = candidate;
                        break;
                    }
                }
                if (destination < 0) continue;
            } else {
                explicitDestination = true;
                destination = configured;
                if (!STORAGE_SET.contains(destination) || claimed.contains(destination)) continue;
            }
            ItemStack destinationStack = slots.get(destination);
            if (!destinationStack.isEmpty() && !identityMatches(target, destinationStack)) continue;
            ItemStack sample = destinationStack.isEmpty() ? firstSourceStack(slots, target) : destinationStack;
            if (sample.isEmpty()) continue;
            int capacity = Math.max(1, sample.getMaxStackSize());
            if (destinationStack.getCount() >= capacity) continue;
            claimed.add(destination);
            rows.add(new Row(target, destination, action.amountModeFor(i), action.amountFor(i),
                destinationStack.getCount(), capacity));
        }

        allocateRows(rows, slots);
        for (Row row : rows) {
            int needed = row.allocated;
            while (needed > 0 && clicks.size() < MAX_CLICKS) {
                int source = findSource(slots, row.target);
                if (source < 0) break;
                int moved = Math.min(needed, slots.get(source).getCount());
                moveCount(action, slots, cursor, source, row.destination, moved, clicks);
                needed -= moved;
            }
        }

        if (cursor[0].isEmpty() && !explicitDestination && limit > STORAGE.length) {
            int source = findSource(slots, targets.get(STORAGE.length));
            if (source >= 0) moveOneToCursor(action, slots, cursor, source, clicks);
        }
    }

    private static void allocateRows(List<Row> rows, List<ItemStack> slots) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) groups.computeIfAbsent(targetKey(row.target), ignored -> new ArrayList<>()).add(row);
        for (List<Row> group : groups.values()) {
            if (group.isEmpty()) continue;
            int available = 0;
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack stack = slots.get(slot);
                if (identityMatches(group.getFirst().target, stack)) available += stack.getCount();
            }
            for (Row row : group) available += row.current;
            List<Row> full = new ArrayList<>();
            for (Row row : group) {
                if (row.amountMode == XCarryAction.AmountMode.CUSTOM) {
                    int desired = Math.min(row.capacity, Math.min(row.customAmount, available));
                    row.allocated = Math.max(0, desired - row.current);
                    available = Math.max(0, available - Math.max(row.current, desired));
                } else {
                    full.add(row);
                }
            }
            Map<Row, Integer> desired = new HashMap<>();
            for (Row row : full) desired.put(row, 0);
            while (available > 0 && !full.isEmpty()) {
                int active = 0;
                for (Row row : full) if (desired.get(row) < row.capacity) active++;
                if (active == 0) break;
                int share = Math.max(1, available / active);
                boolean progressed = false;
                for (Row row : full) {
                    int room = row.capacity - desired.get(row);
                    if (room <= 0 || available <= 0) continue;
                    int take = Math.min(room, Math.min(share, available));
                    desired.put(row, desired.get(row) + take);
                    available -= take;
                    progressed |= take > 0;
                }
                if (!progressed) break;
            }
            for (Row row : full) row.allocated = Math.max(0, desired.getOrDefault(row, 0) - row.current);
        }
    }

    private static void moveCount(XCarryAction action, List<ItemStack> slots, ItemStack[] cursor,
                                  int source, int destination, int amount, List<Click> clicks) {
        if (amount <= 0 || !cursor[0].isEmpty()) return;
        addPickup(action, slots, cursor, source, 0, clicks, Pace.AFTER_PICKUP);
        while (!cursor[0].isEmpty() && cursor[0].getCount() > amount && clicks.size() < MAX_CLICKS) {
            addPickup(action, slots, cursor, source, 1, clicks, Pace.BETWEEN_CLICKS);
        }
        if (!cursor[0].isEmpty()) {
            addPickup(action, slots, cursor, destination, 0, clicks,
                cursor[0].getCount() > amount ? Pace.BEFORE_RETURN : Pace.COMPLETE);
        }
        if (!cursor[0].isEmpty()) addPickup(action, slots, cursor, source, 0, clicks, Pace.COMPLETE);
    }

    private static void moveOneToCursor(XCarryAction action, List<ItemStack> slots, ItemStack[] cursor,
                                        int source, List<Click> clicks) {
        int sourceCount = slots.get(source).getCount();
        addPickup(action, slots, cursor, source, 0, clicks,
            sourceCount > 1 ? Pace.AFTER_PICKUP : Pace.COMPLETE);
        while (!cursor[0].isEmpty() && cursor[0].getCount() > 1 && clicks.size() < MAX_CLICKS) {
            addPickup(action, slots, cursor, source, 1, clicks,
                cursor[0].getCount() > 2 ? Pace.BETWEEN_CLICKS : Pace.COMPLETE);
        }
    }

    private static void addPickup(XCarryAction action, List<ItemStack> slots, ItemStack[] cursor,
                                  int slot, int button, List<Click> clicks, Pace pace) {
        ItemStack[] result = predictPickup(cursor[0], slots.get(slot), button == 0);
        cursor[0] = result[0];
        slots.set(slot, result[1]);
        clicks.add(new Click(slot, button, ContainerInput.PICKUP, delay(action, pace)));
    }

    private static void planTakeOut(List<ItemTarget> targets, List<ItemStack> slots, List<Click> clicks) {
        for (int slot : STORAGE) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty() || !matchesStored(targets, stack, slot)) continue;
            clicks.add(new Click(slot, 0, ContainerInput.QUICK_MOVE, 50L));
        }
    }

    private static void planDrop(List<ItemTarget> targets, List<ItemStack> slots, List<Click> clicks) {
        for (int slot : STORAGE) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty() || !matchesStored(targets, stack, slot)) continue;
            clicks.add(new Click(slot, 1, ContainerInput.THROW, 50L));
        }
    }

    private static boolean matchesStored(List<ItemTarget> targets, ItemStack stack, int handlerSlot) {
        if (targets.isEmpty()) return true;
        for (ItemTarget target : targets) {
            if (target == null) continue;
            if (target.hasSlot() && target.slot != handlerSlot && !(target.slot == 100 && handlerSlot == 0)) continue;
            if (!target.hasIdentity() || identityMatches(target, stack)) return true;
        }
        return false;
    }

    private static int findSource(List<ItemStack> slots, ItemTarget target) {
        if (target != null && target.hasSlot()) {
            int handler = visibleToInventoryHandler(target.slot);
            if (handler >= 9 && handler <= 44 && sourceMatches(target, slots.get(handler), handler)) return handler;
            return -1;
        }
        for (int slot = 9; slot <= 44; slot++) {
            if (sourceMatches(target, slots.get(slot), slot)) return slot;
        }
        return -1;
    }

    private static ItemStack firstSourceStack(List<ItemStack> slots, ItemTarget target) {
        int source = findSource(slots, target);
        return source < 0 ? ItemStack.EMPTY : slots.get(source);
    }

    private static boolean sourceMatches(ItemTarget target, ItemStack stack, int handlerSlot) {
        if (stack == null || stack.isEmpty()) return false;
        if (target == null) return true;
        int visible = inventoryHandlerToVisible(handlerSlot);
        return target.score(stack, visible) >= 0;
    }

    private static boolean identityMatches(ItemTarget target, ItemStack stack) {
        if (stack == null || stack.isEmpty() || target == null) return false;
        ItemTarget identity = target.copy();
        identity.slot = -1;
        return !identity.hasIdentity() || identity.matches(stack, -1);
    }

    private static List<ItemTarget> targets(XCarryAction action) {
        List<ItemTarget> result = new ArrayList<>();
        if (!action.entryTargets.isEmpty()) {
            for (ItemTarget target : action.entryTargets) if (target != null) result.add(target.copy());
        } else {
            for (String entry : action.entries) {
                ItemTarget target = ItemTarget.fromLegacyEntry(entry);
                if (target.hasSlot() || target.hasIdentity()) result.add(target);
            }
        }
        if (result.size() > XCarryAction.MAX_ENTRIES) return List.copyOf(result.subList(0, XCarryAction.MAX_ENTRIES));
        return List.copyOf(result);
    }

    private static String targetKey(ItemTarget target) {
        if (target == null) return "";
        ItemTarget identity = target.copy();
        identity.slot = -1;
        return identity.toLegacyEntry().toLowerCase(Locale.ROOT);
    }

    private static long delay(XCarryAction action, Pace pace) {
        XCarryAction.TransferMode mode = action.transferMode == null ? XCarryAction.TransferMode.FAST : action.transferMode;
        if (mode == XCarryAction.TransferMode.FAST) return 0L;
        if (mode == XCarryAction.TransferMode.CLICK) return 50L;
        long configured = XCarryAction.clampSafeClickDelayTicks(action.safeClickDelayTicks) * 50L;
        if (configured <= 0L || pace == Pace.COMPLETE) return 0L;
        if (pace == Pace.AFTER_PICKUP && !action.safeClickDelayAfterPickup) return 0L;
        if (pace == Pace.BEFORE_RETURN && !action.safeClickDelayBeforeReturn) return 0L;
        return configured;
    }

    private static int visibleToInventoryHandler(int visible) {
        if (visible >= 0 && visible <= 8) return 36 + visible;
        if (visible >= 9 && visible <= 35) return visible;
        if (visible >= 36 && visible <= 39) return 44 - visible;
        return visible == 40 ? 45 : -1;
    }

    private static int inventoryHandlerToVisible(int handler) {
        if (handler >= 36 && handler <= 44) return handler - 36;
        if (handler >= 9 && handler <= 35) return handler;
        if (handler >= 5 && handler <= 8) return 44 - handler;
        return handler == 45 ? 40 : handler;
    }

    private static ItemStack[] predictPickup(ItemStack cursor, ItemStack slot, boolean primary) {
        ItemStack cur = copy(cursor);
        ItemStack target = copy(slot);
        if (target.isEmpty()) {
            if (!cur.isEmpty()) {
                int place = primary ? cur.getCount() : 1;
                target = cur.copyWithCount(place);
                cur.shrink(place);
            }
        } else if (cur.isEmpty()) {
            int take = primary ? target.getCount() : (target.getCount() + 1) / 2;
            cur = target.copyWithCount(take);
            target.shrink(take);
        } else if (ItemStack.isSameItemSameComponents(target, cur)) {
            int place = Math.min(primary ? cur.getCount() : 1,
                Math.max(0, target.getMaxStackSize() - target.getCount()));
            target.grow(place);
            cur.shrink(place);
        } else {
            ItemStack swap = target;
            target = cur;
            cur = swap;
        }
        return new ItemStack[]{empty(cur), empty(target)};
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) copy.add(copy(stack));
        return copy;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static ItemStack empty(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
