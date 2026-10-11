package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

final class MacroDurability {
    private MacroDurability() {
    }
    public static void waitForDurabilityUseNext(Minecraft mc, WaitDurabilityAction action) throws InterruptedException {
        AtomicReference<DurabilityUseNextSession> sessionRef = new AtomicReference<>();
        long start = System.currentTimeMillis();
        while (MacroExecutor.isCurrentRunActive()) {
            if (Boolean.TRUE.equals(MacroExecutor.callOnClientThread(mc, () -> tickDurabilityUseNext(mc, action, sessionRef), Boolean.FALSE))) {
                return;
            }
            if (action.timeoutMs > 0 && System.currentTimeMillis() - start >= action.timeoutMs) return;
            MacroExecutor.waitOneTick();
        }
    }

    public static boolean durabilityObservationMatches(Minecraft mc, WaitDurabilityAction action) {
        if (action == null || !action.useNext) return action != null && action.matches(mc);
        DurabilityUseNextSession session = createDurabilityUseNextSession(mc, action);
        if (session == null || session.fallbackOnly) return action.matches(mc);
        if (session.firstSlotPending) {
            ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
            if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
                session.lockedRegistryId = stackRegistryId(stack);
            }
            if (session.lockedRegistryId.isBlank()) return false;
            return isSameDamageableItem(stack, session.lockedRegistryId)
                && action.matchesStack(stack)
                && findDurabilityReplacement(mc, action, session, session.activeSlot) == null;
        }
        if (session.lockedRegistryId.isBlank()) return action.matches(mc);
        return findDurabilityReplacement(mc, action, session, -1) == null;
    }

    private static boolean tickDurabilityUseNext(Minecraft mc, WaitDurabilityAction action, AtomicReference<DurabilityUseNextSession> sessionRef) {
        if (mc == null || mc.player == null || action == null) return false;
        DurabilityUseNextSession session = sessionRef.get();
        if (session == null) {
            session = createDurabilityUseNextSession(mc, action);
            sessionRef.set(session);
        }
        if (session == null || session.fallbackOnly) return action.matches(mc);

        if (session.firstSlotPending) {
            return tickDurabilityInitialSlot(mc, action, session);
        }

        adoptCurrentSameItem(mc, session);
        ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
        if (isSameDamageableItem(active, session.lockedRegistryId) && !action.matchesStack(active)) {
            return false;
        }
        return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
    }

    private static boolean tickDurabilityInitialSlot(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session) {
        ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
        if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
            session.lockedRegistryId = stackRegistryId(stack);
        }

        if (!session.lockedRegistryId.isBlank() && isSameDamageableItem(stack, session.lockedRegistryId)) {
            session.observedInitialSlot = true;
            if (!action.matchesStack(stack)) return false;
            session.firstSlotPending = false;
            return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
        }

        if (session.observedInitialSlot) {
            session.firstSlotPending = false;
            return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
        }
        return false;
    }

    private static boolean switchToNextDurabilityItemOrComplete(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, int excludeSlot) {
        DurabilityCandidate replacement = findDurabilityReplacement(mc, action, session, excludeSlot);
        if (replacement == null) return true;
        if (!switchToDurabilityReplacement(mc, session, replacement)) return false;
        return false;
    }

    private static DurabilityUseNextSession createDurabilityUseNextSession(Minecraft mc, WaitDurabilityAction action) {
        if (mc == null || mc.player == null || action == null) return null;
        DurabilityUseNextSession session = new DurabilityUseNextSession();
        session.preferredHotbarSlot = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        ItemTarget target = action.targetMode == WaitDurabilityAction.TargetMode.ITEM
            ? ItemTarget.fromLegacyEntry(action.itemName)
            : new ItemTarget();
        session.initialTarget = target;

        switch (action.targetMode) {
            case SLOT -> {
                session.activeSlot = Math.max(0, Math.min(40, action.slot));
                session.firstSlotPending = true;
                ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
                if (canLockDurabilityInitialStack(action, session, stack)) {
                    session.lockedRegistryId = stackRegistryId(stack);
                    session.observedInitialSlot = true;
                }
            }
            case HELD -> initializeHeldDurabilitySession(mc, action, session);
            case ITEM -> initializeItemDurabilitySession(mc, action, session, target);
        }

        if (session.lockedRegistryId == null) session.lockedRegistryId = "";
        if (session.lockedRegistryId.isBlank() && !session.firstSlotPending) {
            session.fallbackOnly = true;
        }
        return session;
    }

    private static void initializeHeldDurabilitySession(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session) {
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        ItemStack main = mc.player.getInventory().getItem(selected);
        if (main != null && !main.isEmpty() && main.isDamageableItem()) {
            session.activeSlot = selected;
            session.lockedRegistryId = stackRegistryId(main);
            return;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty() && offhand.isDamageableItem()) {
            session.activeSlot = 40;
            session.lockedRegistryId = stackRegistryId(offhand);
            return;
        }
        session.fallbackOnly = true;
    }

    private static void initializeItemDurabilitySession(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, ItemTarget target) {
        if (target != null && target.hasSlot()) {
            session.activeSlot = Math.max(0, Math.min(40, target.slot));
            session.firstSlotPending = true;
            String explicitId = target.hasRegistryId() ? canonicalItemRegistryId(target.registryId) : "";
            if (!explicitId.isBlank()) session.lockedRegistryId = explicitId;
            ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
            if (canLockDurabilityInitialStack(action, session, stack)) {
                session.lockedRegistryId = stackRegistryId(stack);
                session.observedInitialSlot = true;
            }
            return;
        }

        if (target != null && target.hasRegistryId()) {
            session.lockedRegistryId = canonicalItemRegistryId(target.registryId);
            session.activeSlot = findCurrentUsableDurabilitySlot(mc, action, session.lockedRegistryId, target);
            return;
        }

        if (target == null || !target.hasIdentity()) {
            initializeHeldDurabilitySession(mc, action, session);
            return;
        }

        int slot = findInitialDurabilitySlot(mc, action, "", target);
        if (slot >= 0) {
            session.activeSlot = slot;
            session.lockedRegistryId = stackRegistryId(stackForVisibleSlot(mc, slot));
        } else {
            session.fallbackOnly = true;
        }
    }

    private static int findCurrentUsableDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
        if (mc == null || mc.player == null) return -1;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) return selected;
        return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
    }

    private static boolean canLockDurabilityInitialStack(WaitDurabilityAction action, DurabilityUseNextSession session, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        if (action != null && action.targetMode == WaitDurabilityAction.TargetMode.ITEM
            && session != null && session.initialTarget != null && session.initialTarget.hasIdentity()) {
            return session.initialTarget.score(stack, session.activeSlot) >= 0;
        }
        return true;
    }

    private static int findInitialDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
        if (mc == null || mc.player == null) return -1;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) return selected;
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slot)) return slot;
        }
        for (int slot = 9; slot < Math.min(36, mc.player.getInventory().getContainerSize()); slot++) {
            if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slot)) return slot;
        }
        return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
    }

    private static boolean initialDurabilitySlotMatches(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target, int slot) {
        ItemStack stack = stackForVisibleSlot(mc, slot);
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        if (lockedRegistryId != null && !lockedRegistryId.isBlank() && !isSameDamageableItem(stack, lockedRegistryId)) return false;
        return target == null || !target.hasIdentity() || target.score(stack, slot) >= 0;
    }

    private static void adoptCurrentSameItem(Minecraft mc, DurabilityUseNextSession session) {
        if (mc == null || mc.player == null || session == null || session.lockedRegistryId.isBlank()) return;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (isSameDamageableItem(mc.player.getInventory().getItem(selected), session.lockedRegistryId)) {
            session.activeSlot = selected;
            session.preferredHotbarSlot = selected;
            return;
        }
        if (isSameDamageableItem(mc.player.getOffhandItem(), session.lockedRegistryId)) {
            ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
            if (!isSameDamageableItem(active, session.lockedRegistryId)) session.activeSlot = 40;
        }
    }

    private static DurabilityCandidate findDurabilityReplacement(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, int excludeSlot) {
        if (mc == null || mc.player == null || action == null || session == null || session.lockedRegistryId.isBlank()) return null;
        DurabilityCandidate hotbar = null;
        DurabilityCandidate inventory = null;
        DurabilityCandidate offhand = null;
        for (int slot = 0; slot < 9; slot++) {
            DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slot, excludeSlot);
            if (candidate != null && (hotbar == null || candidate.score() > hotbar.score())) hotbar = candidate;
        }
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 9; slot < size; slot++) {
            DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slot, excludeSlot);
            if (candidate != null && (inventory == null || candidate.score() > inventory.score())) inventory = candidate;
        }
        offhand = durabilityCandidate(mc, action, session.lockedRegistryId, 40, excludeSlot);
        if (hotbar != null) return hotbar;
        if (inventory != null) return inventory;
        return offhand;
    }

    private static DurabilityCandidate durabilityCandidate(Minecraft mc, WaitDurabilityAction action, String registryId, int slot, int excludeSlot) {
        if (slot == excludeSlot) return null;
        ItemStack stack = stackForVisibleSlot(mc, slot);
        if (!isSameDamageableItem(stack, registryId)) return null;
        if (action.matchesStack(stack)) return null;
        return new DurabilityCandidate(slot, remainingDurability(stack));
    }

    private static boolean switchToDurabilityReplacement(Minecraft mc, DurabilityUseNextSession session, DurabilityCandidate replacement) {
        if (mc == null || mc.player == null || replacement == null) return false;
        int slot = replacement.slot();
        if (slot >= 0 && slot < 9) {
            AutismInventoryHelper.selectHotbarSlot(mc, slot);
            session.activeSlot = slot;
            session.preferredHotbarSlot = slot;
            return true;
        }
        if (slot >= 9 && slot < 36) {
            int target = Math.max(0, Math.min(8, session.preferredHotbarSlot));
            if (!AutismInventoryHelper.swapInventorySlots(mc, slot, target)) return false;
            AutismInventoryHelper.selectHotbarSlot(mc, target);
            session.activeSlot = target;
            session.preferredHotbarSlot = target;
            return true;
        }
        if (slot == 40) {
            session.activeSlot = 40;
            return true;
        }
        return false;
    }

    private static ItemStack stackForVisibleSlot(Minecraft mc, int visibleSlot) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        if (visibleSlot == 40) return mc.player.getOffhandItem();
        if (visibleSlot < 0 || visibleSlot >= mc.player.getInventory().getContainerSize()) return ItemStack.EMPTY;
        return mc.player.getInventory().getItem(visibleSlot);
    }

    private static boolean isSameDamageableItem(ItemStack stack, String registryId) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem() && stackRegistryId(stack).equals(registryId);
    }

    private static int remainingDurability(ItemStack stack) {
        return stack == null || !stack.isDamageableItem() ? 0 : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static String stackRegistryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String canonicalItemRegistryId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        Identifier parsed = Identifier.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
        if (parsed == null) return "";
        return BuiltInRegistries.ITEM.getOptional(parsed)
            .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
            .orElse("");
    }

    private static final class DurabilityUseNextSession {
        private String lockedRegistryId = "";
        private int activeSlot = -1;
        private int preferredHotbarSlot = 0;
        private boolean firstSlotPending = false;
        private boolean observedInitialSlot = false;
        private boolean fallbackOnly = false;
        private ItemTarget initialTarget = new ItemTarget();
    }

    private record DurabilityCandidate(int slot, int score) {}
}
