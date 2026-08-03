package autismclient.util.macro;

import autismclient.util.AutismDropAction;
import autismclient.util.AutismDropHelper;
import autismclient.util.AutismCursorClickHelper;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class ItemAction implements MacroAction, WaitsForGui {
    public List<String> itemNames = new ArrayList<>();
    public List<ItemTarget> itemTargets = new ArrayList<>();
    public List<Integer> itemTimes = new ArrayList<>();
    public List<Integer> itemActionIdx = new ArrayList<>();
    public List<Integer> itemButtons = new ArrayList<>();
    public List<Integer> itemSearchScopes = new ArrayList<>();
    @Deprecated
    public List<Boolean> preferPlayerInventory = new ArrayList<>();
    public List<Integer> stackAmountModes = new ArrayList<>();
    public int targetSlot = -1;
    public boolean useSlot = false;
    public int actionIndex = 0;
    public int button = 0;
    public int times = 1;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String guiName = "";
    public boolean waitForItem = false;
    public boolean useCursorItemForPickupAll = true;

    public boolean useClickDelay = false;
    public int clickDelayMs = 50;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final AutismDropAction[] ACTIONS = AutismDropAction.values();
    private static final int[] ITEM_CLICK_ACTIONS = {
        AutismDropAction.PICKUP.ordinal(),
        AutismDropAction.QUICK_MOVE.ordinal(),
        AutismDropAction.SWAP.ordinal(),
        AutismDropAction.CLONE.ordinal(),
        AutismDropAction.DROP_ITEM.ordinal(),
        AutismDropAction.DROP_STACK.ordinal()
    };
    private static final int[] LEGACY_ITEM_CLICK_ACTIONS = {
        AutismDropAction.PICKUP.ordinal(),
        AutismDropAction.PICKUP_ALL.ordinal(),
        AutismDropAction.QUICK_MOVE.ordinal(),
        AutismDropAction.SWAP.ordinal(),
        AutismDropAction.CLONE.ordinal(),
        AutismDropAction.DROP_ITEM.ordinal(),
        AutismDropAction.DROP_STACK.ordinal()
    };

    public enum StackAmountMode {
        DEFAULT("Default"),
        LEAST("Least"),
        MOST("Most");

        public final String label;

        StackAmountMode(String label) {
            this.label = label;
        }
    }

    public enum ItemSearchScope {
        BOTH("Both"),
        GUI("GUI"),
        PLAYER_INVENTORY("Player Inventory");

        public final String label;

        ItemSearchScope(String label) {
            this.label = label;
        }
    }

    public ItemAction() {}

    public int getItemTime(int index) {
        return (index < itemTimes.size() && itemTimes.get(index) > 0) ? itemTimes.get(index) : 1;
    }

    public int getItemActionIdx(int index) {
        int stored = (index < itemActionIdx.size())
            ? itemActionIdx.get(index)
            : actionIndex;
        return normalizeItemClickActionIndex(stored);
    }

    public int getItemButton(int index) {
        int raw = (index < itemButtons.size()) ? itemButtons.get(index) : button;
        return normalizeButtonForItemClick(getItemAction(index), raw);
    }

    public AutismDropAction getItemAction(int index) {
        return ACTIONS[getItemActionIdx(index)];
    }

    public String getItemButtonName(int index) {
        if (getItemAction(index) == AutismDropAction.PICKUP_ALL) return "All";
        if (getItemAction(index) == AutismDropAction.SWAP) {
            int hotbarSlot = Math.max(0, Math.min(8, getItemButton(index))) + 1;
            return "Hotbar " + hotbarSlot;
        }
        return switch (getItemButton(index)) {
            case 0 -> "Left"; case 1 -> "Right"; case 2 -> "Middle"; default -> "?";
        };
    }

    public void cycleItemAction(int index) {
        while (itemActionIdx.size() <= index) itemActionIdx.add(actionIndex);
        itemActionIdx.set(index, cycleItemClickActionIndex(itemActionIdx.get(index), 1));
        normalizeStoredItemButton(index);
    }

    public void cycleItemActionBackwards(int index) {
        while (itemActionIdx.size() <= index) itemActionIdx.add(actionIndex);
        itemActionIdx.set(index, cycleItemClickActionIndex(itemActionIdx.get(index), -1));
        normalizeStoredItemButton(index);
    }

    public void cycleItemButton(int index) {
        while (itemButtons.size() <= index) itemButtons.add(button);
        if (getItemAction(index) == AutismDropAction.PICKUP_ALL) return;
        int limit = buttonLimitForItemClick(getItemAction(index));
        if (limit <= 1) return;
        int current = getItemButton(index);
        itemButtons.set(index, (current + 1) % limit);
    }

    public void cycleItemButtonBackwards(int index) {
        while (itemButtons.size() <= index) itemButtons.add(button);
        if (getItemAction(index) == AutismDropAction.PICKUP_ALL) return;
        int limit = buttonLimitForItemClick(getItemAction(index));
        if (limit <= 1) return;
        int current = getItemButton(index);
        itemButtons.set(index, (current - 1 + limit) % limit);
    }

    public static java.util.List<AutismDropAction> itemClickActions() {
        java.util.List<AutismDropAction> list = new java.util.ArrayList<>();
        for (int ordinal : ITEM_CLICK_ACTIONS) list.add(ACTIONS[ordinal]);
        return list;
    }

    public int getItemClickActionOptionIndex(int index) {
        int ordinal = getItemAction(index).ordinal();
        java.util.List<AutismDropAction> opts = itemClickActions();
        for (int i = 0; i < opts.size(); i++) if (opts.get(i).ordinal() == ordinal) return i;
        return 0;
    }

    public void setItemActionByOptionIndex(int index, int optionIndex) {
        if (index < 0) return;
        java.util.List<AutismDropAction> opts = itemClickActions();
        if (optionIndex < 0 || optionIndex >= opts.size()) return;
        while (itemActionIdx.size() <= index) itemActionIdx.add(actionIndex);
        itemActionIdx.set(index, normalizeItemClickActionIndex(opts.get(optionIndex).ordinal()));
        normalizeStoredItemButton(index);
    }

    public int itemButtonOptionCount(int index) {
        return buttonLimitForItemClick(getItemAction(index));
    }

    public java.util.List<String> itemButtonOptions(int index) {
        AutismDropAction action = getItemAction(index);
        int limit = Math.max(1, buttonLimitForItemClick(action));
        java.util.List<String> opts = new java.util.ArrayList<>();
        for (int b = 0; b < limit; b++) opts.add(itemButtonLabelFor(action, b));
        return opts;
    }

    private static String itemButtonLabelFor(AutismDropAction action, int b) {
        if (action == AutismDropAction.PICKUP_ALL) return "All";
        if (action == AutismDropAction.SWAP) return "H" + (Math.max(0, Math.min(8, b)) + 1);
        return switch (b) { case 1 -> "R"; case 2 -> "M"; default -> "L"; };
    }

    public void setItemButton(int index, int optionIndex) {
        if (index < 0) return;
        while (itemButtons.size() <= index) itemButtons.add(this.button);
        int limit = buttonLimitForItemClick(getItemAction(index));
        if (limit <= 1) return;
        itemButtons.set(index, Math.floorMod(optionIndex, limit));
    }

    public boolean getPreferPlayerInventory(int index) {
        return getItemSearchScope(index) == ItemSearchScope.PLAYER_INVENTORY;
    }

    public void setPreferPlayerInventory(int index, boolean value) {
        setItemSearchScope(index, value ? ItemSearchScope.PLAYER_INVENTORY : ItemSearchScope.BOTH);
    }

    public ItemSearchScope getItemSearchScope(int index) {
        if (index >= 0 && index < itemSearchScopes.size()) {
            return ItemSearchScope.values()[normalizeItemSearchScope(itemSearchScopes.get(index))];
        }
        if (index >= 0 && index < preferPlayerInventory.size()
                && Boolean.TRUE.equals(preferPlayerInventory.get(index))) {
            return ItemSearchScope.PLAYER_INVENTORY;
        }
        return ItemSearchScope.BOTH;
    }

    public void cycleItemSearchScope(int index, int direction) {
        if (index < 0) return;
        int next = Math.floorMod(getItemSearchScope(index).ordinal() + direction, ItemSearchScope.values().length);
        setItemSearchScope(index, ItemSearchScope.values()[next]);
    }

    public void setItemSearchScope(int index, ItemSearchScope scope) {
        if (index < 0) return;
        ItemSearchScope safe = scope == null ? ItemSearchScope.BOTH : scope;
        while (itemSearchScopes.size() <= index) itemSearchScopes.add(ItemSearchScope.BOTH.ordinal());
        itemSearchScopes.set(index, safe.ordinal());
        while (preferPlayerInventory.size() <= index) preferPlayerInventory.add(false);
        preferPlayerInventory.set(index, safe == ItemSearchScope.PLAYER_INVENTORY);
    }

    public StackAmountMode getStackAmountMode(int index) {
        int raw = index >= 0 && index < stackAmountModes.size()
                ? stackAmountModes.get(index)
                : StackAmountMode.DEFAULT.ordinal();
        return StackAmountMode.values()[normalizeStackAmountMode(raw)];
    }

    public void cycleStackAmountMode(int index, int direction) {
        if (index < 0) return;
        while (stackAmountModes.size() <= index) stackAmountModes.add(StackAmountMode.DEFAULT.ordinal());
        int current = normalizeStackAmountMode(stackAmountModes.get(index));
        int next = (current + direction) % StackAmountMode.values().length;
        if (next < 0) next += StackAmountMode.values().length;
        stackAmountModes.set(index, next);
    }

    private record ResolvedClickEntry(AutismDropAction action, int button, int slot, ItemTarget cursorTarget, int count) {}

    private List<ResolvedClickEntry> resolveClickEntries(Minecraft mc, AbstractContainerMenu handler) {
        List<ResolvedClickEntry> out = new ArrayList<>();
        if (!itemNames.isEmpty()) {
            for (int ei = 0; ei < itemNames.size(); ei++) {
                int clickCount = getItemTime(ei);
                AutismDropAction eiAction = getItemAction(ei);
                int eiButton = getItemButton(ei);
                if (usesFixedButtonForItemClick(eiAction)) {
                    eiButton = eiAction.getButton();
                }
                ItemTarget entryTarget = getItemTarget(ei).resolveTemplate(mc);
                if (entryTarget == null) continue;
                String entryName = parseEntryName(entryTarget.toLegacyEntry());
                int configuredEntrySlot = entryTarget.hasSlot() ? entryTarget.slot : -1;
                if (eiAction == AutismDropAction.PICKUP_ALL && useCursorItemForPickupAll) {
                    out.add(new ResolvedClickEntry(eiAction, eiButton, -1, entryTarget, clickCount));
                    continue;
                }
                int slotToUse;
                if (configuredEntrySlot >= 0 && !entryName.isEmpty()) {
                    int resolvedSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredEntrySlot);
                    if (resolvedSlot < 0) continue;
                    slotToUse = findExactMatchingSlot(handler, entryTarget, resolvedSlot);
                } else if (configuredEntrySlot >= 0) {
                    slotToUse = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredEntrySlot);
                } else if (useSlot && targetSlot >= 0) {
                    int configuredSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, targetSlot);
                    if (configuredSlot < 0) continue;
                    slotToUse = findExactMatchingSlot(handler, entryTarget, configuredSlot);
                } else {
                    slotToUse = findSlotByItemTarget(
                            handler,
                            entryTarget,
                            getItemSearchScope(ei),
                            getStackAmountMode(ei)
                    );
                }
                if (slotToUse < 0) continue;
                out.add(new ResolvedClickEntry(eiAction, eiButton, slotToUse, null, clickCount));
            }
        } else if (useSlot && targetSlot >= 0) {
            AutismDropAction action = getAction();
            int effectiveButton = getButton();
            if (usesFixedButtonForItemClick(action)) {
                effectiveButton = action.getButton();
            }
            if (action == AutismDropAction.PICKUP_ALL && useCursorItemForPickupAll) {
                out.add(new ResolvedClickEntry(action, effectiveButton, -1, ItemTarget.slotOnly(targetSlot), times));
                return out;
            }
            int resolvedTargetSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, targetSlot);
            if (resolvedTargetSlot < 0) return out;
            out.add(new ResolvedClickEntry(action, effectiveButton, resolvedTargetSlot, null, times));
        } else if (getAction() == AutismDropAction.PICKUP_ALL && useCursorItemForPickupAll) {
            out.add(new ResolvedClickEntry(getAction(), getButton(), -1, null, times));
        }
        return out;
    }

    private static void performEntry(Minecraft mc, ResolvedClickEntry e) {
        boolean cursorPickupAll = e.cursorTarget() != null
                || (e.action() == AutismDropAction.PICKUP_ALL && e.slot() < 0);
        if (cursorPickupAll) {
            AutismCursorClickHelper.click(mc, e.cursorTarget(), e.count());
            return;
        }
        if (e.action() == AutismDropAction.DROP_ITEM || e.action() == AutismDropAction.DROP_STACK) {
            AutismDropHelper.dropFromHandlerSlot(mc, e.slot(), e.action() == AutismDropAction.DROP_STACK ? 0 : e.count());
            return;
        }
        for (int j = 0; j < e.count(); j++) {
            AutismInventoryClickHelper.click(mc, e.slot(), e.button(), e.action().toContainerInput());
        }
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return;
        for (ResolvedClickEntry e : resolveClickEntries(mc, handler)) {
            performEntry(mc, e);
        }
    }

    public List<Runnable> buildClickPlan(Minecraft mc) {
        List<Runnable> plan = new ArrayList<>();
        if (mc.player == null || mc.getConnection() == null) return plan;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return plan;
        for (ResolvedClickEntry e : resolveClickEntries(mc, handler)) {
            boolean cursorPickupAll = e.cursorTarget() != null
                    || (e.action() == AutismDropAction.PICKUP_ALL && e.slot() < 0);
            if (cursorPickupAll) {
                final ItemTarget t = e.cursorTarget();
                for (int j = 0; j < e.count(); j++) plan.add(() -> AutismCursorClickHelper.click(mc, t, 1));
            } else if (e.action() == AutismDropAction.DROP_STACK) {
                final int fs = e.slot();
                plan.add(() -> AutismDropHelper.dropFromHandlerSlot(mc, fs, 0));
            } else if (e.action() == AutismDropAction.DROP_ITEM) {
                final int fs = e.slot();
                for (int j = 0; j < e.count(); j++) plan.add(() -> AutismDropHelper.dropFromHandlerSlot(mc, fs, 1));
            } else {
                final int fs = e.slot();
                final int fb = e.button();
                final net.minecraft.world.inventory.ContainerInput input = e.action().toContainerInput();
                for (int j = 0; j < e.count(); j++) plan.add(() -> AutismInventoryClickHelper.click(mc, fs, fb, input));
            }
        }
        return plan;
    }

    private int findSlotByItemName(AbstractContainerMenu handler, String name) {
        return findSlotByItemTarget(handler, ItemTarget.fromLegacyEntry(name));
    }

    private int findSlotByItemTarget(AbstractContainerMenu handler, ItemTarget target) {
        return findSlotByItemTarget(handler, target, ItemSearchScope.BOTH, StackAmountMode.DEFAULT);
    }

    private int findSlotByItemTarget(AbstractContainerMenu handler, ItemTarget target, ItemSearchScope searchScope, StackAmountMode amountMode) {
        if (!target.hasIdentity()) return -1;
        StackAmountMode mode = amountMode == null ? StackAmountMode.DEFAULT : amountMode;
        ItemSearchScope scope = searchScope == null ? ItemSearchScope.BOTH : searchScope;
        return switch (scope) {
            case BOTH -> findMatchingSlotInPartition(handler, target, mode, null);
            case GUI -> findMatchingSlotInPartition(handler, target, mode, false);
            case PLAYER_INVENTORY -> findMatchingSlotInPartition(handler, target, mode, true);
        };
    }

    private int findMatchingSlotInPartition(AbstractContainerMenu handler, ItemTarget target, StackAmountMode mode, Boolean playerInventoryOnly) {
        int bestSlot = -1;
        int bestScore = -1;
        int bestCount = mode == StackAmountMode.LEAST ? Integer.MAX_VALUE : -1;

        for (Slot slot : handler.slots) {
            if (slot == null || slot.getItem().isEmpty()) continue;
            if (playerInventoryOnly != null) {
                boolean isPlayerInventory = AutismInventoryHelper.isInventorySlot(MC, slot);
                if (playerInventoryOnly.booleanValue() != isPlayerInventory) continue;
            }
            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(MC, slot.index);
            int score = target.score(slot.getItem(), visibleSlot);
            if (score < 0) continue;
            int count = slot.getItem().getCount();
            if (isBetterItemCandidate(mode, score, count, bestScore, bestCount)) {
                bestScore = score;
                bestCount = count;
                bestSlot = slot.index;
            }
        }

        return bestScore >= 0 ? bestSlot : -1;
    }

    private boolean isBetterItemCandidate(StackAmountMode mode, int score, int count, int bestScore, int bestCount) {
        if (bestScore < 0) return true;
        if (mode == StackAmountMode.LEAST) {
            if (count != bestCount) return count < bestCount;
            return score > bestScore;
        }
        if (mode == StackAmountMode.MOST) {
            if (count != bestCount) return count > bestCount;
            return score > bestScore;
        }
        return score > bestScore;
    }

    private int findExactMatchingSlot(AbstractContainerMenu handler, String name, int requiredSlot) {
        return findExactMatchingSlot(handler, ItemTarget.fromLegacyEntry(name), requiredSlot);
    }

    private int findExactMatchingSlot(AbstractContainerMenu handler, ItemTarget target, int requiredSlot) {
        if (requiredSlot < 0 || requiredSlot >= handler.slots.size()) return -1;
        Slot slot = handler.slots.get(requiredSlot);
        if (slot == null || slot.getItem().isEmpty()) return -1;
        if (!target.hasIdentity()) return requiredSlot;
        int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(MC, requiredSlot);
        return target.matches(slot.getItem(), visibleSlot) ? requiredSlot : -1;
    }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return guiName; }
    @Override public void setWaitGuiName(String name) { this.guiName = name; }
    @Override public boolean isWaitForGuiChange() { return true; }

    @Override
    public MacroActionType getType() { return MacroActionType.ITEM; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "ITEM");
        tag.put("itemNames", ItemTarget.toTagList(resolvedItemTargets()));
        ListTag times2 = new ListTag();
        for (int t : itemTimes) times2.add(StringTag.valueOf(String.valueOf(t)));
        tag.put("itemTimes", times2);
        ListTag aidx = new ListTag();
        for (int a : itemActionIdx) aidx.add(StringTag.valueOf(String.valueOf(a)));
        tag.put("itemActionIdx", aidx);
        ListTag btns = new ListTag();
        for (int b : itemButtons) btns.add(StringTag.valueOf(String.valueOf(b)));
        tag.put("itemButtons", btns);
        ListTag preferInv = new ListTag();
        ListTag searchScopes = new ListTag();
        for (int i = 0; i < itemNames.size(); i++) {
            preferInv.add(StringTag.valueOf(String.valueOf(getPreferPlayerInventory(i))));
            searchScopes.add(StringTag.valueOf(String.valueOf(getItemSearchScope(i).ordinal())));
        }
        tag.put("preferPlayerInventory", preferInv);
        tag.put("itemSearchScopes", searchScopes);
        ListTag stackModes = new ListTag();
        for (int i = 0; i < itemNames.size(); i++) {
            stackModes.add(StringTag.valueOf(String.valueOf(getStackAmountMode(i).ordinal())));
        }
        tag.put("stackAmountModes", stackModes);
        tag.putInt("targetSlot", targetSlot);
        tag.putBoolean("useSlot", useSlot);
        tag.putInt("actionIndex", actionIndex);
        tag.putInt("button", button);
        tag.putInt("times", times);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("guiName", guiName);
        tag.putBoolean("waitForItem", waitForItem);
        tag.putBoolean("useCursorItemForPickupAll", useCursorItemForPickupAll);
        tag.putBoolean("useClickDelay", useClickDelay);
        tag.putInt("clickDelayMs", Math.max(0, clickDelayMs));
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
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
        itemTimes.clear();
        if (tag.contains("itemTimes")) {
            ListTag tl = tag.getList("itemTimes").orElse(new ListTag());
            for (Tag el : tl) {
                try { itemTimes.add(Integer.parseInt(el.asString().orElse("1"))); }
                catch (NumberFormatException e) { itemTimes.add(1); }
            }
        }

        while (itemTimes.size() < itemNames.size()) itemTimes.add(1);
        itemActionIdx.clear();
        if (tag.contains("itemActionIdx")) {
            ListTag al = tag.getList("itemActionIdx").orElse(new ListTag());
            for (Tag el : al) {
                try { itemActionIdx.add(Integer.parseInt(el.asString().orElse("0"))); }
                catch (NumberFormatException e) { itemActionIdx.add(0); }
            }
        }
        while (itemActionIdx.size() < itemNames.size()) itemActionIdx.add(0);
        for (int i = 0; i < itemActionIdx.size(); i++) {
            itemActionIdx.set(i, normalizeItemClickActionIndex(itemActionIdx.get(i)));
        }
        itemButtons.clear();
        if (tag.contains("itemButtons")) {
            ListTag bl = tag.getList("itemButtons").orElse(new ListTag());
            for (Tag el : bl) {
                try { itemButtons.add(Integer.parseInt(el.asString().orElse("0"))); }
                catch (NumberFormatException e) { itemButtons.add(0); }
            }
        }
        while (itemButtons.size() < itemNames.size()) itemButtons.add(0);
        for (int i = 0; i < itemButtons.size(); i++) {
            itemButtons.set(i, normalizeButtonForItemClick(getItemAction(i), itemButtons.get(i)));
        }
        preferPlayerInventory.clear();
        if (tag.contains("preferPlayerInventory")) {
            ListTag pl = tag.getList("preferPlayerInventory").orElse(new ListTag());
            for (Tag el : pl) {
                preferPlayerInventory.add(Boolean.parseBoolean(el.asString().orElse("false")));
            }
        }
        while (preferPlayerInventory.size() < itemNames.size()) preferPlayerInventory.add(false);
        while (preferPlayerInventory.size() > itemNames.size()) preferPlayerInventory.remove(preferPlayerInventory.size() - 1);
        itemSearchScopes.clear();
        if (tag.contains("itemSearchScopes")) {
            ListTag scopes = tag.getList("itemSearchScopes").orElse(new ListTag());
            for (Tag el : scopes) {
                try { itemSearchScopes.add(normalizeItemSearchScope(Integer.parseInt(el.asString().orElse("0")))); }
                catch (NumberFormatException ignored) { itemSearchScopes.add(ItemSearchScope.BOTH.ordinal()); }
            }
        } else {
            for (int i = 0; i < itemNames.size(); i++) {
                itemSearchScopes.add(Boolean.TRUE.equals(preferPlayerInventory.get(i))
                    ? ItemSearchScope.PLAYER_INVENTORY.ordinal()
                    : ItemSearchScope.BOTH.ordinal());
            }
        }
        while (itemSearchScopes.size() < itemNames.size()) itemSearchScopes.add(ItemSearchScope.BOTH.ordinal());
        while (itemSearchScopes.size() > itemNames.size()) itemSearchScopes.remove(itemSearchScopes.size() - 1);
        for (int i = 0; i < itemSearchScopes.size(); i++) {
            setItemSearchScope(i, ItemSearchScope.values()[normalizeItemSearchScope(itemSearchScopes.get(i))]);
        }
        stackAmountModes.clear();
        if (tag.contains("stackAmountModes")) {
            ListTag ml = tag.getList("stackAmountModes").orElse(new ListTag());
            for (Tag el : ml) {
                try { stackAmountModes.add(normalizeStackAmountMode(Integer.parseInt(el.asString().orElse("0")))); }
                catch (NumberFormatException ignored) { stackAmountModes.add(StackAmountMode.DEFAULT.ordinal()); }
            }
        }
        while (stackAmountModes.size() < itemNames.size()) stackAmountModes.add(StackAmountMode.DEFAULT.ordinal());
        while (stackAmountModes.size() > itemNames.size()) stackAmountModes.remove(stackAmountModes.size() - 1);
        targetSlot = tag.getIntOr("targetSlot", -1);
        useSlot = tag.getBooleanOr("useSlot", false);
        actionIndex = normalizeItemClickActionIndex(tag.getIntOr("actionIndex", 0));
        button = normalizeButtonForItemClick(getAction(), tag.getIntOr("button", 0));
        times = tag.getIntOr("times", 1);
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        if (tag.contains("guiName")) guiName = tag.getStringOr("guiName", "");
        if (tag.contains("waitForItem")) waitForItem = tag.getBooleanOr("waitForItem", false);
        useCursorItemForPickupAll = tag.getBooleanOr("useCursorItemForPickupAll", true);
        useClickDelay = tag.getBooleanOr("useClickDelay", false);
        clickDelayMs = Math.max(0, tag.getIntOr("clickDelayMs", 50));
    }

    @Override
    public String getDisplayName() {
        List<ItemTarget> targets = resolvedItemTargets();
        if (!targets.isEmpty()) {

            String actionName = getItemAction(0).shortName;
            String e0 = targets.get(0).toLegacyEntry();
            String first;
            int slotNumber = parseEntrySlot(e0);
            String itemName = parseEntryName(e0);
            if (slotNumber >= 0 && !itemName.isEmpty()) {
                first = itemName + " @ " + slotNumber;
            } else if (slotNumber >= 0) {
                first = "#" + slotNumber;
            } else {
                first = itemName;
            }
            String shortName = first.length() > 12 ? first.substring(0, 10) + ".." : first;
            String suffix = itemNames.size() > 1 ? " (+" + (itemNames.size() - 1) + ")" : "";
            String base = "Item " + shortName + suffix + " (" + actionName + ")";
            if (waitForItem) base += " [wait]";
            return base + WaitsForGui.timingLabel(this);
        } else if (useSlot && targetSlot >= 0) {
            return "#" + targetSlot + " (" + getAction().shortName + ")" + WaitsForGui.timingLabel(this);
        }
        return "Item (empty)" + WaitsForGui.timingLabel(this);
    }

    @Override
    public String getIcon() { return "I"; }

    public AutismDropAction getAction() {
        return ACTIONS[normalizeItemClickActionIndex(actionIndex)];
    }

    public void cycleAction() {
        actionIndex = cycleItemClickActionIndex(actionIndex, 1);
        button = normalizeButtonForItemClick(getAction(), button);
    }

    public void cycleActionBackwards() {
        actionIndex = cycleItemClickActionIndex(actionIndex, -1);
        button = normalizeButtonForItemClick(getAction(), button);
    }

    public String getButtonName() {
        if (getAction() == AutismDropAction.PICKUP_ALL) return "All";
        if (getAction() == AutismDropAction.SWAP) {
            int hotbarSlot = Math.max(0, Math.min(8, getButton())) + 1;
            return "Hotbar " + hotbarSlot;
        }
        return switch (getButton()) {
            case 0 -> "Left";
            case 1 -> "Right";
            case 2 -> "Middle";
            default -> "?";
        };
    }

    public void cycleButton() {
        if (getAction() == AutismDropAction.PICKUP_ALL) return;
        int limit = buttonLimitForItemClick(getAction());
        if (limit <= 1) return;
        button = (getButton() + 1) % limit;
    }

    public void cycleButtonBackwards() {
        if (getAction() == AutismDropAction.PICKUP_ALL) return;
        int limit = buttonLimitForItemClick(getAction());
        if (limit <= 1) return;
        button = (getButton() - 1 + limit) % limit;
    }

    public int getButton() {
        return normalizeButtonForItemClick(getAction(), button);
    }

    private void normalizeStoredItemButton(int index) {
        while (itemButtons.size() <= index) itemButtons.add(button);
        itemButtons.set(index, normalizeButtonForItemClick(getItemAction(index), itemButtons.get(index)));
    }

    private static boolean usesFixedButtonForItemClick(AutismDropAction action) {
        return action != AutismDropAction.QUICK_MOVE && action.usesFixedButton();
    }

    private static int buttonLimitForItemClick(AutismDropAction action) {
        return switch (action) {
            case SWAP -> 9;
            case PICKUP -> 3;
            case QUICK_MOVE -> 2;
            default -> 1;
        };
    }

    private static int normalizeButtonForItemClick(AutismDropAction action, int rawButton) {
        if (action == null) action = AutismDropAction.PICKUP;
        if (usesFixedButtonForItemClick(action)) return action.getButton();
        int limit = buttonLimitForItemClick(action);
        if (limit <= 1) return 0;
        int normalized = rawButton % limit;
        return normalized < 0 ? normalized + limit : normalized;
    }

    private static int normalizeItemClickActionIndex(int rawIndex) {
        for (int allowed : LEGACY_ITEM_CLICK_ACTIONS) {
            if (rawIndex == allowed) {
                return rawIndex;
            }
        }
        return AutismDropAction.PICKUP.ordinal();
    }

    private static int normalizeStackAmountMode(int rawMode) {
        return rawMode >= 0 && rawMode < StackAmountMode.values().length
                ? rawMode
                : StackAmountMode.DEFAULT.ordinal();
    }

    private static int normalizeItemSearchScope(int rawScope) {
        return rawScope >= 0 && rawScope < ItemSearchScope.values().length
                ? rawScope
                : ItemSearchScope.BOTH.ordinal();
    }

    private static int cycleItemClickActionIndex(int current, int direction) {
        int selected = direction >= 0 ? -1 : 0;
        for (int i = 0; i < ITEM_CLICK_ACTIONS.length; i++) {
            if (ITEM_CLICK_ACTIONS[i] == current) {
                selected = i;
                break;
            }
        }

        int next = (selected + direction) % ITEM_CLICK_ACTIONS.length;
        if (next < 0) next += ITEM_CLICK_ACTIONS.length;
        return ITEM_CLICK_ACTIONS[next];
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
        if (!entry.startsWith("#")) return entry;
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
