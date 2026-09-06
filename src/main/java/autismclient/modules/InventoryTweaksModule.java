package autismclient.modules;
import autismclient.api.module.*;

import autismclient.util.AutismBindUtil;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import autismclient.util.AutismDropHelper;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class InventoryTweaksModule extends Module {
    private static final Minecraft MC = Minecraft.getInstance();
    private SortingOperation sortingOperation;
    private TimedOperation timedOperation;
    private boolean warnedAutoConflict;
    private final Map<String, NormalizedListCache> normalizedListCaches = new HashMap<>();

    InventoryTweaksModule() {
        super("inventory-tweaks", "InventoryTweaks", ModuleCategory.PLAYER, "Inventory sorting, protection, auto-drop, and container steal/dump tools.");

        add(new BoolSetting("mouse-drag-item-move", "Shift Drag Move", true)
            .description("Shift-drag transfers hovered stacks.").build());
        add(new BoolSetting("uncap-bundle-scrolling", "Uncap Bundle Scroll", true)
            .description("Allow unrestricted bundle scrolling.").build());
        add(new BoolSetting("shulker-preview", "Shulker Preview", true)
            .description("Preview container on hover").build());

        add(new BoolSetting("sorting-enabled", "Sorting", true).group("Sorting").description("Enable inventory sorting.").build());
        add(new KeybindSetting("sorting-key", "Sort Key", AutismBindUtil.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
            .group("Sorting").visibleWhen(() -> bool("sorting-enabled")).description("Sort key").build());
        add(new IntSetting("sorting-delay", "Sort Delay", 1, 0, 20, 1)
            .group("Sorting").visibleWhen(() -> bool("sorting-enabled")).description("Ticks between sorting moves.").build());
        add(new BoolSetting("disable-in-creative", "Disable In Creative", true)
            .group("Sorting").visibleWhen(() -> bool("sorting-enabled")).description("Skip creative screens").build());

        add(RegistryListSetting.items("anti-drop-items", "Anti Drop Items", "")
            .group("Anti Drop").description("Undroppable items").build());
        add(new BoolSetting("item-frames", "Frames / Pots", true)
            .group("Anti Drop").description("Block frame/pot placing").build());
        add(new KeybindSetting("override-bind", "Override Key", -1)
            .group("Anti Drop").description("Hold to bypass anti-drop.").build());

        add(RegistryListSetting.items("auto-drop-items", "Auto Drop Items", "")
            .group("Auto Drop").description("Items dropped automatically.").build());
        add(new BoolSetting("exclude-equipped", "Exclude Equipped", true)
            .group("Auto Drop").description("Keep armor/offhand items.").build());
        add(new BoolSetting("exclude-hotbar", "Exclude Hotbar", false)
            .group("Auto Drop").description("Keep hotbar stacks").build());
        add(new BoolSetting("only-full-stacks", "Only Full Stacks", false)
            .group("Auto Drop").description("Only drop complete stacks.").build());

        add(new StringListSetting("steal-screens", "Steal Screens", "minecraft:generic_9x3|minecraft:generic_9x6")
            .group("Steal / Dump").description("Menu ids").build());
        add(new BoolSetting("inventory-buttons", "Inventory Buttons", true)
            .group("Steal / Dump").description("Show steal/dump buttons").build());
        add(new BoolSetting("steal-drop", "Steal Drop", false)
            .group("Steal / Dump").description("Drop instead of move").build());
        add(new BoolSetting("drop-backwards", "Drop Backwards", false)
            .group("Steal / Dump").visibleWhen(() -> bool("steal-drop")).description("Turn while dropping").build());
        add(new ChoiceSetting("dump-filter", "Dump Filter", "None", "None", "Whitelist", "Blacklist")
            .group("Steal / Dump").description("Filter dumped player items.").build());
        add(RegistryListSetting.items("dump-items", "Dump Items", "")
            .group("Steal / Dump").visibleWhen(() -> !"None".equals(value("dump-filter"))).description("Dump filter items").build());
        add(new ChoiceSetting("steal-filter", "Steal Filter", "None", "None", "Whitelist", "Blacklist")
            .group("Steal / Dump").description("Filter stolen container items.").build());
        add(RegistryListSetting.items("steal-items", "Steal Items", "")
            .group("Steal / Dump").visibleWhen(() -> !"None".equals(value("steal-filter"))).description("Steal filter items").build());

        add(new BoolSetting("auto-steal", "Auto Steal", false).group("Auto Steal").description("Automatically steal supported containers.").build());
        add(new BoolSetting("auto-dump", "Auto Dump", false).group("Auto Steal").description("Auto-dump supported containers.").build());
        add(new IntSetting("delay-ms", "Delay", 0, 0, 1000, 5).group("Auto Steal").description("Move delay (ms)").build());
        add(new IntSetting("initial-delay-ms", "Initial Delay", 0, 0, 1000, 5).group("Auto Steal").description("First-move delay (ms)").build());
        add(new IntSetting("random-ms", "Random", 0, 0, 1000, 5).group("Auto Steal").description("Random extra delay").build());

        boolean firstInstall = !AutismConfig.getGlobal().modules.containsKey("inventory-tweaks");
        if (firstInstall) setEnabledSilently(true);
    }

    public static InventoryTweaksModule get() {
        Module module = ModuleRegistry.get("inventory-tweaks");
        return module instanceof InventoryTweaksModule tweaks ? tweaks : null;
    }

    private static int lastShowRevision = Integer.MIN_VALUE;
    private static java.lang.ref.WeakReference<AbstractContainerMenu> lastShowMenu = new java.lang.ref.WeakReference<>(null);
    private static boolean lastShowResult;

    public static boolean shouldShowButtons(AbstractContainerMenu handler) {
        int revision = ModuleRegistry.revision();
        if (revision == lastShowRevision && lastShowMenu.get() == handler) return lastShowResult;
        InventoryTweaksModule module = get();
        boolean result = module != null && module.isEnabled() && module.bool("inventory-buttons") && module.canSteal(handler);
        lastShowRevision = revision;
        lastShowMenu = new java.lang.ref.WeakReference<>(handler);
        lastShowResult = result;
        return result;
    }

    public static void stealFromButton() {
        InventoryTweaksModule module = get();
        if (module != null && module.isEnabled()) module.startTimedOperation(true, false);
    }

    public static void dumpFromButton() {
        InventoryTweaksModule module = get();
        if (module != null && module.isEnabled()) module.startTimedOperation(false, false);
    }

    public static boolean handleSortMouse(int button, Slot hoveredSlot) {
        InventoryTweaksModule module = get();
        if (module == null || !module.isEnabled() || !module.bool("sorting-enabled")) return false;
        int bind = module.integer("sorting-key");
        if (!AutismBindUtil.isMouseBind(bind) || AutismBindUtil.decodeMouseButton(bind) != button) return false;
        return module.startSorting(hoveredSlot);
    }

    public static boolean handleSortKey(int keyCode, Slot hoveredSlot) {
        InventoryTweaksModule module = get();
        if (module == null || !module.isEnabled() || !module.bool("sorting-enabled")) return false;
        int bind = module.integer("sorting-key");
        if (AutismBindUtil.isMouseBind(bind) || bind != keyCode) return false;
        return module.startSorting(hoveredSlot);
    }

    public static boolean shouldShiftDragMove() {
        InventoryTweaksModule module = get();
        return module != null && module.isEnabled() && module.bool("mouse-drag-item-move");
    }

    public static boolean shulkerPreviewEnabled() {
        InventoryTweaksModule module = get();
        return module != null && module.isEnabled() && module.bool("shulker-preview");
    }

    public static boolean hasContainerSyncWork() {
        InventoryTweaksModule module = get();
        return module != null && module.isEnabled() && (module.bool("auto-steal") || module.bool("auto-dump"));
    }

    public static int bundleScrollLimit(ItemStack stack, int vanillaLimit) {
        InventoryTweaksModule module = get();
        if (module == null || !module.isEnabled() || !module.bool("uncap-bundle-scrolling")) return vanillaLimit;
        BundleContents contents = stack == null ? null : stack.get(DataComponents.BUNDLE_CONTENTS);
        return contents == null ? vanillaLimit : Math.max(vanillaLimit, contents.size());
    }

    public static void onContainerSynced(int containerId) {
        InventoryTweaksModule module = get();
        if (module != null && module.isEnabled()) module.handleContainerSynced(containerId);
    }

    @Override
    public void onDisable() {
        sortingOperation = null;
        timedOperation = null;
        warnedAutoConflict = false;
        normalizedListCaches.clear();
    }

    @Override
    public void tick() {
        tickSorting();
        tickTimedOperation();
        tickAutoDrop();
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        return shouldCancelProtectedDrop(packet);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof ClientboundContainerSetContentPacket content) handleContainerSynced(content.containerId());
        if (packet instanceof ClientboundContainerSetSlotPacket slot) handleContainerSynced(slot.getContainerId());
        return false;
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
        if (hitResult == null || hand == null || MC.player == null) return false;
        if (!bool("item-frames") || isOverrideHeld()) return false;
        ItemStack held = MC.player.getItemInHand(hand);
        if (!isInItemList(held, "anti-drop-items")) return false;

        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemFrame) return true;
        if (hitResult instanceof BlockHitResult blockHit && MC.level != null) {
            return MC.level.getBlockState(blockHit.getBlockPos()).is(Blocks.DECORATED_POT);
        }
        return false;
    }

    private boolean startSorting(Slot focusedSlot) {
        if (focusedSlot == null || MC.player == null || MC.gameMode == null) return false;
        if (bool("disable-in-creative") && MC.player.hasInfiniteMaterials()) return false;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null || !handler.getCarried().isEmpty()) {
            AutismClientMessaging.sendPrefixed("Inventory Tweaks: clear your cursor before sorting.");
            return true;
        }

        List<Slot> slots = sortableSlotsFor(focusedSlot, handler);
        if (slots.size() < 2) return true;
        List<SwapAction> actions = buildSortActions(slots);
        if (actions.isEmpty()) {
            AutismClientMessaging.sendPrefixed("Inventory Tweaks: already sorted.");
            return true;
        }
        sortingOperation = new SortingOperation(handler.containerId, actions, Math.max(0, integer("sorting-delay")));
        AutismClientMessaging.sendPrefixed("Inventory Tweaks: sorting " + slots.size() + " slots.");
        return true;
    }

    private void tickSorting() {
        if (sortingOperation == null) return;
        if (!sortingOperation.tick()) sortingOperation = null;
    }

    private List<Slot> sortableSlotsFor(Slot focusedSlot, AbstractContainerMenu handler) {
        boolean focusedPlayer = AutismInventoryHelper.isInventorySlot(MC, focusedSlot);
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot == null || !slot.mayPickup(MC.player)) continue;
            int invSlot = inventorySlot(slot);
            if (focusedPlayer) {
                if (invSlot >= 9 && invSlot < 36) slots.add(slot);
            } else if (invSlot < 0 && slot.container == focusedSlot.container) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private List<SwapAction> buildSortActions(List<Slot> slots) {
        List<ItemStack> simulated = new ArrayList<>();
        for (Slot slot : slots) simulated.add(slot.getItem().copy());

        List<ItemStack> desired = new ArrayList<>();
        for (ItemStack stack : simulated) {
            if (!stack.isEmpty()) desired.add(stack.copy());
        }
        desired.sort(STACK_COMPARATOR);
        while (desired.size() < simulated.size()) desired.add(ItemStack.EMPTY);

        List<SwapAction> actions = new ArrayList<>();
        for (int i = 0; i < simulated.size(); i++) {
            if (sameStack(simulated.get(i), desired.get(i))) continue;
            int match = -1;
            for (int j = i + 1; j < simulated.size(); j++) {
                if (sameStack(simulated.get(j), desired.get(i))) {
                    match = j;
                    break;
                }
            }
            if (match < 0) continue;
            actions.add(new SwapAction(slots.get(i).index, slots.get(match).index));
            ItemStack tmp = simulated.get(i);
            simulated.set(i, simulated.get(match));
            simulated.set(match, tmp);
        }
        return actions;
    }

    private void handleContainerSynced(int containerId) {
        if (MC.player == null || MC.player.containerMenu == null) return;
        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler.containerId != containerId || !canSteal(handler) || timedOperation != null) return;
        if (bool("auto-steal") && bool("auto-dump")) {
            if (!warnedAutoConflict) {
                warnedAutoConflict = true;
                setValue("auto-dump", "false");
                AutismClientMessaging.sendPrefixed("Inventory Tweaks: Auto Dump disabled because Auto Steal is enabled.");
            }
        }
        if (bool("auto-steal")) startTimedOperation(true, true);
        else if (bool("auto-dump")) startTimedOperation(false, true);
    }

    private void startTimedOperation(boolean steal, boolean automatic) {
        if (MC.player == null || MC.gameMode == null) return;
        AbstractContainerMenu handler = MC.player.containerMenu;
        if (!canSteal(handler)) {
            if (!automatic) AutismClientMessaging.sendPrefixed("Inventory Tweaks: this menu is not enabled for Steal/Dump.");
            return;
        }

        List<TimedClick> clicks = buildTimedClicks(handler, steal);
        if (clicks.isEmpty()) {
            if (!automatic) AutismClientMessaging.sendPrefixed("Inventory Tweaks: nothing to " + (steal ? "steal." : "dump."));
            return;
        }
        timedOperation = new TimedOperation(handler.containerId, clicks, steal, bool("drop-backwards"),
            Math.max(0, integer("delay-ms")), Math.max(0, integer("initial-delay-ms")), Math.max(0, integer("random-ms")));
        if (!automatic) AutismClientMessaging.sendPrefixed("Inventory Tweaks: " + (steal ? "stealing " : "dumping ") + clicks.size() + " stacks.");
    }

    private List<TimedClick> buildTimedClicks(AbstractContainerMenu handler, boolean steal) {
        List<TimedClick> clicks = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot == null || slot.getItem().isEmpty()) continue;
            int invSlot = inventorySlot(slot);
            if (steal) {
                if (invSlot >= 0) continue;
                if (!passesFilter(slot.getItem(), "steal-filter", "steal-items")) continue;
                clicks.add(new TimedClick(slot.index, bool("steal-drop")));
            } else {
                if (invSlot < 0 || invSlot >= 36) continue;
                if (!passesFilter(slot.getItem(), "dump-filter", "dump-items")) continue;
                clicks.add(new TimedClick(slot.index, false));
            }
        }
        return clicks;
    }

    private void tickTimedOperation() {
        if (timedOperation == null) return;
        if (!timedOperation.tick()) timedOperation = null;
    }

    private void tickAutoDrop() {
        if (MC.player == null || MC.gameMode == null || MC.gui.screen() != null) return;
        if (list("auto-drop-items").isEmpty()) return;
        int start = bool("exclude-hotbar") ? 9 : 0;
        for (int slot = start; slot < 36; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (stack.isEmpty() || !isInItemList(stack, "auto-drop-items")) continue;
            if (bool("only-full-stacks") && stack.getCount() < stack.getMaxStackSize()) continue;
            AutismDropHelper.dropFromInventorySlot(MC, slot, 0);
            return;
        }
    }

    private boolean canSteal(AbstractContainerMenu handler) {
        if (handler == null || MC.player == null || handler == MC.player.inventoryMenu) return false;
        Identifier id = menuId(handler);
        if (id == null) return false;
        Set<String> allowed = normalizedList("steal-screens");
        return allowed.contains(id.toString()) || allowed.contains(id.getPath());
    }

    private Identifier menuId(AbstractContainerMenu handler) {
        try {
            MenuType<?> type = handler.getType();
            return type == null ? null : BuiltInRegistries.MENU.getKey(type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean passesFilter(ItemStack stack, String modeOption, String listOption) {
        String mode = choice(modeOption);
        if ("None".equals(mode)) return true;
        boolean listed = isInItemList(stack, listOption);
        return "Whitelist".equals(mode) ? listed : !listed;
    }

    private boolean isInItemList(ItemStack stack, String optionId) {
        if (stack == null || stack.isEmpty()) return false;
        String full = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        String path = full.startsWith("minecraft:") ? full.substring("minecraft:".length()) : full;
        Set<String> ids = normalizedList(optionId);
        return ids.contains(full) || ids.contains(path);
    }

    private Set<String> normalizedList(String optionId) {
        List<String> raw = list(optionId);
        String source = String.join("|", raw);
        NormalizedListCache cached = normalizedListCaches.get(optionId);
        if (cached != null && cached.source().equals(source)) return cached.values();
        Set<String> values = new HashSet<>();
        for (String entry : raw) {
            String id = normalizeId(entry);
            if (id.isBlank()) continue;
            values.add(id);
            if (id.startsWith("minecraft:")) values.add(id.substring("minecraft:".length()));
            else values.add("minecraft:" + id);
        }
        Set<String> immutable = Set.copyOf(values);
        normalizedListCaches.put(optionId, new NormalizedListCache(source, immutable));
        return immutable;
    }

    private boolean isOverrideHeld() {
        int bind = integer("override-bind");
        return bind != -1 && AutismBindUtil.isBindPressed(MC, bind);
    }

    private boolean shouldCancelProtectedDrop(Packet<?> packet) {
        if (MC.player == null || isOverrideHeld() || list("anti-drop-items").isEmpty()) return false;
        if (packet instanceof ServerboundPlayerActionPacket action
            && (action.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            return isInItemList(MC.player.getMainHandItem(), "anti-drop-items");
        }

        if (packet instanceof ServerboundContainerClickPacket click) {
            ContainerInput input = packetEnum(click, ContainerInput.class);
            if (input != ContainerInput.THROW) return false;
            int slotId = packetInt(click, "getSlot", "slot", "slotNum");
            AbstractContainerMenu handler = MC.player.containerMenu;
            if (handler == null || slotId < 0 || slotId >= handler.slots.size()) return false;
            return isInItemList(handler.slots.get(slotId).getItem(), "anti-drop-items");
        }
        return false;
    }

    private static int inventorySlot(Slot slot) {
        if (slot == null || MC.player == null || slot.container != MC.player.getInventory()) return -1;
        int index = slot.getContainerSlot();
        return index >= 0 && index < MC.player.getInventory().getContainerSize() ? index : -1;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) return true;
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return a.getCount() == b.getCount()
            && a.getDamageValue() == b.getDamageValue()
            && ItemStack.isSameItemSameComponents(a, b);
    }

    private static String normalizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static <T extends Enum<T>> T packetEnum(Object packet, Class<T> enumClass) {
        for (Method method : packet.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && enumClass.isAssignableFrom(method.getReturnType())) {
                try {
                    method.setAccessible(true);
                    return enumClass.cast(method.invoke(packet));
                } catch (Throwable ignored) {  }
            }
        }
        for (Field field : packet.getClass().getDeclaredFields()) {
            if (enumClass.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return enumClass.cast(field.get(packet));
                } catch (Throwable ignored) {  }
            }
        }
        return null;
    }

    private static int packetInt(Object packet, String... names) {
        for (String name : names) {
            try {
                Method method = packet.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(packet);
                if (value instanceof Number number) return number.intValue();
            } catch (Throwable ignored) {  }
            try {
                Field field = packet.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value instanceof Number number) return number.intValue();
            } catch (Throwable ignored) {  }
        }
        return -1;
    }

    private static final Comparator<ItemStack> STACK_COMPARATOR = Comparator
        .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
        .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
        .thenComparing(Comparator.comparingInt(ItemStack::getDamageValue).reversed());

    private record SwapAction(int fromSlot, int toSlot) {
    }

    private record TimedClick(int slot, boolean drop) {
    }

    private record NormalizedListCache(String source, Set<String> values) {
    }

    private final class SortingOperation {
        private final int containerId;
        private final List<SwapAction> actions;
        private final int delayTicks;
        private int index;
        private int delayLeft;

        private SortingOperation(int containerId, List<SwapAction> actions, int delayTicks) {
            this.containerId = containerId;
            this.actions = actions;
            this.delayTicks = delayTicks;
        }

        private boolean tick() {
            if (MC.player == null || MC.player.containerMenu == null || MC.player.containerMenu.containerId != containerId) return false;
            if (delayLeft > 0) {
                delayLeft--;
                return true;
            }
            if (index >= actions.size()) return false;
            SwapAction action = actions.get(index++);
            AutismInventoryHelper.swapHandlerSlots(MC, action.fromSlot(), action.toSlot());
            delayLeft = delayTicks;
            return index < actions.size() || delayLeft > 0;
        }
    }

    private final class TimedOperation {
        private final int containerId;
        private final List<TimedClick> clicks;
        private final boolean steal;
        private final boolean dropBackwards;
        private final int delayMs;
        private final int randomMs;
        private int index;
        private long nextClickAt;

        private TimedOperation(int containerId, List<TimedClick> clicks, boolean steal, boolean dropBackwards, int delayMs, int initialDelayMs, int randomMs) {
            this.containerId = containerId;
            this.clicks = clicks;
            this.steal = steal;
            this.dropBackwards = dropBackwards;
            this.delayMs = delayMs;
            this.randomMs = randomMs;
            this.nextClickAt = System.currentTimeMillis() + initialDelayMs;
        }

        private boolean tick() {
            if (MC.player == null || MC.player.containerMenu == null || MC.gameMode == null) return false;
            if (MC.player.containerMenu.containerId != containerId) return false;
            if (System.currentTimeMillis() < nextClickAt) return true;

            while (index < clicks.size()) {
                TimedClick click = clicks.get(index++);
                if (click.slot() < 0 || click.slot() >= MC.player.containerMenu.slots.size()) continue;
                Slot slot = MC.player.containerMenu.slots.get(click.slot());
                if (slot == null || slot.getItem().isEmpty()) continue;
                if (steal && !passesFilter(slot.getItem(), "steal-filter", "steal-items")) continue;
                if (!steal && !passesFilter(slot.getItem(), "dump-filter", "dump-items")) continue;

                if (click.drop()) {
                    float oldYaw = MC.player.getYRot();
                    if (dropBackwards) MC.player.setYRot(oldYaw + 180.0f);
                    try {
                        AutismInventoryClickHelper.click(MC, click.slot(), 1, ContainerInput.THROW);
                    } finally {
                        if (dropBackwards) MC.player.setYRot(oldYaw);
                    }
                } else {
                    AutismInventoryClickHelper.click(MC, click.slot(), 0, ContainerInput.QUICK_MOVE);
                }

                if (delayMs <= 0 && randomMs <= 0) continue;
                nextClickAt = System.currentTimeMillis() + delayMs + ThreadLocalRandom.current().nextInt(randomMs + 1);
                return true;
            }
            return false;
        }
    }
}
