package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InventoryAuditAction implements MacroAction {
    public enum Mode { DUPE, DUPE_SPAM }

    public enum OpenMode { COMMAND, CONTAINER }

    public enum DupeVector {

        DESYNC_REOPEN,

        CLOSE_NO_PACKET,

        SHIFT_CLICK_REOPEN,

        DELAYED_PACKETS,

        SWAP_HOTBAR,

        DROP_EXPLOIT,
        DELAYED_DESYNC_REOPEN,
        SWAP_DESYNC_REOPEN,
        DROP_DELAYED_PACKETS
    }

    private static final int MAX_DELAY_MS = 10_000;
    private static final int MAX_ITERATIONS = 100;
    private static final int AUTO_GUI_TIMEOUT_MS = 12_000;
    private static final int GUI_POLL_MS = 50;
    private static final int GUI_STABLE_POLLS_REQUIRED = 3;
    private static final int ACTION_WAIT_POLL_MS = 25;

    public Mode mode = Mode.DUPE;

    public List<String> targetItems = new ArrayList<>();
    public List<ItemTarget> itemTargets = new ArrayList<>();

    public String openCommand = "/ec";

    public OpenMode openMode = OpenMode.COMMAND;

    public BlockPos containerPos = BlockPos.ZERO;

    public DupeVector dupeVector = DupeVector.DESYNC_REOPEN;

    public int delayBeforeReopen = 200;

    public int delayAfterReopen = 500;

    public int iterations = 1;

    public int maxTransferAttempts = 5;

    public int transferRetryDelayMs = 50;

    public boolean multipleStacks = false;

    public int spamCount = 3;

    public int spamDelayMs = 50;

    private boolean enabled = true;

    public boolean hasValidTargetSelection() {
        return hasConfiguredTargets();
    }

    public boolean hasConfiguredTargets() {
        for (ItemTarget target : resolvedTargets()) {
            if (target != null && (target.hasSlot() || target.hasIdentity())) return true;
        }
        return false;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc == null || mc.player == null) return;

        if (mode == Mode.DUPE || mode == Mode.DUPE_SPAM) return;
    }

    public void executeDupe(Minecraft mc) throws InterruptedException {
        if (mc == null || mc.player == null) return;

        MacroTemplate.Resolution commandResolution = MacroVariables.resolve(normalizedOpenCommand(), mc);
        if (!commandResolution.success()) return;
        String cmd = commandResolution.value().trim();
        if (cmd.isBlank()) return;
        int runIterations = clampIterations(iterations);
        int maxAttempts = Math.max(1, Math.min(maxTransferAttempts, 20));
        int retryDelay = Math.max(10, Math.min(transferRetryDelayMs, 500));

        for (int iter = 0; iter < runIterations; iter++) {
            ensureMacroRunning();

            if (!openTargetAndWaitForGuiStable(mc, cmd, AUTO_GUI_TIMEOUT_MS)) {
                return;
            }

            InventorySnapshot before = captureOnGameThread(mc);
            if (before == null || before.slots().isEmpty()) {
                return;
            }

            Map<String, Integer> targetContainerItems = collectTargetContainerTotals(before);
            if (targetContainerItems.isEmpty()) {
                InventorySnapshot playerSnapshot = captureInventoryOnly(mc);
                Map<String, Integer> playerTargetItems = collectTargetPlayerTotals(playerSnapshot);

                if (!playerTargetItems.isEmpty()) {
                    boolean transferred = transferItemsToContainer(mc, playerTargetItems);
                    if (transferred) {
                        closeGuiOnGameThread(mc, true);
                        if (!waitForGuiClosed(mc, AUTO_GUI_TIMEOUT_MS)) {
                            return;
                        }

                        if (!openTargetAndWaitForGuiStable(mc, cmd, AUTO_GUI_TIMEOUT_MS)) {
                            return;
                        }

                        before = captureOnGameThread(mc);
                        if (before == null || before.slots().isEmpty()) {
                            return;
                        }

                        targetContainerItems = collectTargetContainerTotals(before);
                    }
                }

                if (targetContainerItems.isEmpty()) {
                    closeGuiOnGameThread(mc, true);
                    continue;
                }
            }

            if (mode == Mode.DUPE_SPAM) {
                int spamOpens = Math.max(1, Math.min(spamCount, 20));
                int spamDelay = Math.max(10, Math.min(spamDelayMs, 1000));

                spamOpenTarget(mc, cmd, spamOpens, spamDelay);
                sleepInterruptibly(100);
            }

            executeDupeVector(mc);

            int totalTransferAttempts = 0;
            int emptyVisibleTransferPasses = 0;
            Set<Integer> targetSlots = getTargetSlots(mc);

            while (totalTransferAttempts < maxAttempts) {
                ensureMacroRunning();
                if (!openTargetAndWaitForGuiStable(mc, cmd, AUTO_GUI_TIMEOUT_MS)) {
                    break;
                }

                sleepInterruptibly(retryDelay);

                TransferAttempt attempt = transferTargetsAggressive(mc, targetSlots);
                totalTransferAttempts++;

                if (attempt.slotCount() <= 0 && targetSlots.isEmpty()) {
                    emptyVisibleTransferPasses++;
                    if (emptyVisibleTransferPasses >= 2) {
                        break;
                    }
                } else {
                    emptyVisibleTransferPasses = 0;
                }
            }

            if (!prepareForReopen(mc, AUTO_GUI_TIMEOUT_MS)) {
                return;
            }

            if (!openTargetAndWaitForGuiStable(mc, cmd, AUTO_GUI_TIMEOUT_MS)) {
                return;
            }

            closeGuiOnGameThread(mc, true);
            if (iter < runIterations - 1) sleepInterruptibly(500);
        }
    }

    private Set<Integer> getTargetSlots(Minecraft mc) {
        Set<Integer> slots = new HashSet<>();

        List<ItemTarget> targets = runtimeTargets();

        if (!targets.isEmpty()) {
            for (ItemTarget target : targets) {
                if (target != null && target.hasSlot()) slots.add(target.slot);
            }
        }
        return slots;
    }

    private TransferAttempt transferTargetsAggressive(Minecraft mc, Set<Integer> targetSlots) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);

        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                Set<Integer> firedSlots = new HashSet<>();

                for (int visibleSlot : targetSlots) {
                    int handlerSlot = AutismInventoryHelper.toHandlerSlot(mc, visibleSlot);
                    if (handlerSlot >= 0 && handlerSlot < handler.slots.size()
                            && firedSlots.add(handlerSlot)) {
                        Slot slot = handler.slots.get(handlerSlot);

                        if (slot != null && !AutismInventoryHelper.isInventorySlot(mc, slot)) {
                            ItemStack stack = slot.getItem();
                            if (stack != null && !stack.isEmpty()) {
                                slotCount++;
                                items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                            }
                            mc.gameMode.handleContainerInput(handler.containerId, handlerSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        }
                    }
                }

                for (int i = 0; i < handler.slots.size(); i++) {
                    if (firedSlots.contains(i)) continue;
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;

                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;

                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (shouldTransferSlot(stack, visibleSlot)) {
                        firedSlots.add(i);
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                        mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                    }
                }

                result[0] = new TransferAttempt(slotCount, items);
            } finally {
                latch.countDown();
            }
        });

        awaitLatch(latch, 2000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private void verifyActualDuplication(InventorySnapshot before, InventorySnapshot after,
                                        TransferAttempt transfer, String iterLabel, int transferAttempts) {
    }

    private TransferAttempt executeDupeVector(Minecraft mc) throws InterruptedException {
        return switch (dupeVector) {
            case DESYNC_REOPEN -> executeDesyncReopen(mc);
            case CLOSE_NO_PACKET -> executeCloseNoPacket(mc);
            case SHIFT_CLICK_REOPEN -> executeShiftClickReopen(mc);
            case DELAYED_PACKETS -> executeDelayedPackets(mc);
            case SWAP_HOTBAR -> executeSwapHotbar(mc);
            case DROP_EXPLOIT -> executeDropExploit(mc);
            case DELAYED_DESYNC_REOPEN -> executeDelayedDesyncReopen(mc);
            case SWAP_DESYNC_REOPEN -> executeSwapDesyncReopen(mc);
            case DROP_DELAYED_PACKETS -> executeDropDelayedPackets(mc);
        };
    }

    private TransferAttempt executeDesyncReopen(Minecraft mc) throws InterruptedException {

        TransferAttempt transfer = shiftClickTargets(mc);

        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player != null && mc.getConnection() != null
                        && mc.player.containerMenu != null) {
                    mc.getConnection().send(
                        new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                }
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 2000);
        sleepInterruptibly(100);

        closeGuiOnGameThread(mc, false);
        return transfer;
    }

    private TransferAttempt executeCloseNoPacket(Minecraft mc) throws InterruptedException {
        TransferAttempt transfer = shiftClickTargets(mc);
        closeGuiOnGameThread(mc, false);
        return transfer;
    }

    private TransferAttempt executeShiftClickReopen(Minecraft mc) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                    }
                }
                result[0] = new TransferAttempt(slotCount, items);

                mc.player.closeContainer();
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 3000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private TransferAttempt executeSwapDesyncReopen(Minecraft mc) throws InterruptedException {
        TransferAttempt transfer = swapTargetsToHotbar(mc);
        sendClosePacketOnGameThread(mc);
        sleepInterruptibly(100);
        closeGuiOnGameThread(mc, false);
        return transfer;
    }

    private TransferAttempt executeDelayedPackets(Minecraft mc) throws InterruptedException {
        AutismSharedState shared = AutismSharedState.get();
        boolean previousDelayPackets = shared.shouldDelayGuiPackets();
        boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
        Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
        Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());

        shared.setUseCustomPackets(true);
        shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
        shared.setS2CPackets(Set.of());
        shared.setDelayGuiPackets(true);

        try {

            TransferAttempt transfer = shiftClickTargets(mc);

            int[] flushed = {0};
            CountDownLatch flushLatch = new CountDownLatch(1);
            mc.execute(() -> {
                try {
                    if (mc.getConnection() != null && mc.player != null) {

                        flushed[0] = shared.flushDelayedPackets(mc.getConnection());

                        if (mc.player.containerMenu != null) {
                            mc.getConnection().send(
                                new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                        }
                    }
                } finally {
                    flushLatch.countDown();
                }
            });
            awaitLatch(flushLatch, 3000);

            closeGuiOnGameThread(mc, false);
            return transfer;
        } finally {
            shared.setDelayGuiPackets(previousDelayPackets);
            shared.setUseCustomPackets(previousUseCustomPackets);
            shared.setC2SPackets(previousC2SPackets);
            shared.setS2CPackets(previousS2CPackets);
        }
    }

    private TransferAttempt executeDelayedDesyncReopen(Minecraft mc) throws InterruptedException {
        AutismSharedState shared = AutismSharedState.get();
        boolean previousDelayPackets = shared.shouldDelayGuiPackets();
        boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
        Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
        Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());

        shared.setUseCustomPackets(true);
        shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
        shared.setS2CPackets(Set.of());
        shared.setDelayGuiPackets(true);

        try {
            TransferAttempt transfer = shiftClickTargets(mc);

            CountDownLatch flushLatch = new CountDownLatch(1);
            mc.execute(() -> {
                try {
                    if (mc.getConnection() != null && mc.player != null) {
                        shared.flushDelayedPackets(mc.getConnection());
                        if (mc.player.containerMenu != null) {
                            mc.getConnection().send(
                                    new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                        }
                    }
                } finally {
                    flushLatch.countDown();
                }
            });
            awaitLatch(flushLatch, 3000);

            sleepInterruptibly(100);
            closeGuiOnGameThread(mc, false);
            return transfer;
        } finally {
            shared.setDelayGuiPackets(previousDelayPackets);
            shared.setUseCustomPackets(previousUseCustomPackets);
            shared.setC2SPackets(previousC2SPackets);
            shared.setS2CPackets(previousS2CPackets);
        }
    }

    private TransferAttempt executeSwapHotbar(Minecraft mc) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                int hotbarSlot = 0;
                for (int i = 0; i < handler.slots.size(); i++) {
                    if (hotbarSlot > 8) break;
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                    }
                }
                result[0] = new TransferAttempt(slotCount, items);

                mc.player.closeContainer();
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 3000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private TransferAttempt executeDropExploit(Minecraft mc) throws InterruptedException {
        TransferAttempt transfer = throwTargets(mc);
        closeGuiOnGameThread(mc, false);
        return transfer;
    }

    private TransferAttempt executeDropDelayedPackets(Minecraft mc) throws InterruptedException {
        AutismSharedState shared = AutismSharedState.get();
        boolean previousDelayPackets = shared.shouldDelayGuiPackets();
        boolean previousUseCustomPackets = shared.shouldUseCustomPackets();
        Set<Class<? extends Packet<?>>> previousC2SPackets = new HashSet<>(shared.getC2SPackets());
        Set<Class<? extends Packet<?>>> previousS2CPackets = new HashSet<>(shared.getS2CPackets());

        shared.setUseCustomPackets(true);
        shared.setC2SPackets(Set.of(ServerboundContainerClickPacket.class));
        shared.setS2CPackets(Set.of());
        shared.setDelayGuiPackets(true);

        try {
            TransferAttempt transfer = throwTargets(mc);

            CountDownLatch flushLatch = new CountDownLatch(1);
            mc.execute(() -> {
                try {
                    if (mc.getConnection() != null && mc.player != null) {
                        shared.flushDelayedPackets(mc.getConnection());
                        if (mc.player.containerMenu != null) {
                            mc.getConnection().send(
                                    new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                        }
                    }
                } finally {
                    flushLatch.countDown();
                }
            });
            awaitLatch(flushLatch, 3000);

            closeGuiOnGameThread(mc, false);
            return transfer;
        } finally {
            shared.setDelayGuiPackets(previousDelayPackets);
            shared.setUseCustomPackets(previousUseCustomPackets);
            shared.setC2SPackets(previousC2SPackets);
            shared.setS2CPackets(previousS2CPackets);
        }
    }

    private void sendCommand(Minecraft mc, String cmd) {
        mc.execute(() -> {
            if (PackHideState.isHardLocked() || !MacroExecutor.isCurrentActionRunActive() || mc.getConnection() == null) return;
            String clean = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            mc.getConnection().sendCommand(clean);
        });
    }

    private void sendContainerOpen(Minecraft mc, BlockPos pos) {
        mc.execute(() -> {
            if (PackHideState.isHardLocked() || !MacroExecutor.isCurrentActionRunActive() || mc.player == null || mc.getConnection() == null || pos == null) return;
            AutismContainerTarget target = AutismContainerTarget.forBlock(pos);
            if (target != null) {
                target.interact(mc);
            }
        });
    }

    private void sendClosePacketOnGameThread(Minecraft mc) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player != null && mc.getConnection() != null
                        && mc.player.containerMenu != null) {
                    mc.getConnection().send(
                            new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                }
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 2000);
    }

    private boolean waitForGui(Minecraft mc, long timeoutMs) throws InterruptedException {
        CompletableFuture<Void> future = MacroConditionRegistry.waitForGui("");
        try {
            return awaitFutureCompletion(future, timeoutMs);
        } finally {
            future.cancel(true);
        }
    }

    private boolean waitForGuiStable(Minecraft mc, long timeoutMs) throws InterruptedException {
        if (!readGuiState(mc).open() && !waitForGui(mc, timeoutMs)) return false;
        long deadline = System.currentTimeMillis() + timeoutMs;
        GuiState previous = null;
        int stablePolls = 0;

        while (System.currentTimeMillis() <= deadline) {
            ensureMacroRunning();
            GuiState current = readGuiState(mc);
            if (current.open()) {
                if (current.equals(previous)) {
                    stablePolls++;
                } else {
                    previous = current;
                    stablePolls = 1;
                }
                if (stablePolls >= GUI_STABLE_POLLS_REQUIRED) {
                    return true;
                }
            } else {
                previous = null;
                stablePolls = 0;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) break;
            sleepInterruptibly(Math.min((long) GUI_POLL_MS, remaining));
        }

        return false;
    }

    private boolean waitForGuiClosed(Minecraft mc, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            ensureMacroRunning();
            if (!readGuiState(mc).open()) return true;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) break;
            sleepInterruptibly(Math.min((long) GUI_POLL_MS, remaining));
        }
        return !readGuiState(mc).open();
    }

    private boolean prepareForReopen(Minecraft mc, long timeoutMs) throws InterruptedException {
        if (readGuiState(mc).open()) {
            closeGuiOnGameThread(mc, true);
        }
        return waitForGuiClosed(mc, timeoutMs);
    }

    private boolean openTargetAndWaitForGuiStable(Minecraft mc, String cmd, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long retryIntervalMs = resolveOpenRetryIntervalMs();
        long nextAttemptAt = 0L;
        int attempts = 0;
        int maxAttempts = resolveOpenAttemptBudget();
        GuiState previous = null;
        int stablePolls = 0;

        while (System.currentTimeMillis() <= deadline) {
            ensureMacroRunning();
            long now = System.currentTimeMillis();
            GuiState current = readGuiState(mc);

            if (current.open()) {
                if (current.equals(previous)) {
                    stablePolls++;
                } else {
                    previous = current;
                    stablePolls = 1;
                }
                if (stablePolls >= GUI_STABLE_POLLS_REQUIRED) {
                    return true;
                }
            } else {
                previous = null;
                stablePolls = 0;
                if (attempts < maxAttempts && now >= nextAttemptAt) {
                    if (openMode == OpenMode.CONTAINER) {
                        sendContainerOpen(mc, containerPos);
                    } else {
                        sendCommand(mc, cmd);
                    }
                    attempts++;
                    nextAttemptAt = now + retryIntervalMs;
                }
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) break;
            sleepInterruptibly(Math.min((long) GUI_POLL_MS, remaining));
        }

        return false;
    }

    private void spamOpenTarget(Minecraft mc, String cmd, int totalOpens, int delayMs) throws InterruptedException {
        for (int spam = 1; spam < totalOpens; spam++) {
            sleepInterruptibly(delayMs);
            if (openMode == OpenMode.CONTAINER) {
                sendContainerOpen(mc, containerPos);
            } else {
                sendCommand(mc, cmd);
            }
        }
    }

    private int resolveOpenAttemptBudget() {
        if (mode == Mode.DUPE_SPAM) {
            return Math.max(2, Math.min(spamCount, openMode == OpenMode.CONTAINER ? 8 : 4));
        }
        return openMode == OpenMode.CONTAINER ? 4 : 2;
    }

    private long resolveOpenRetryIntervalMs() {
        int configuredDelay = mode == Mode.DUPE_SPAM
                ? Math.max(10, Math.min(spamDelayMs, 1000))
                : Math.max(10, Math.min(transferRetryDelayMs, 500));
        long fallback = openMode == OpenMode.CONTAINER ? 175L : 250L;
        return Math.max((long) GUI_POLL_MS, Math.min(configuredDelay > 0 ? configuredDelay : fallback, 400L));
    }

    private GuiState readGuiState(Minecraft mc) throws InterruptedException {
        GuiState[] state = {new GuiState(false, -1, -1, 0, 0)};
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.player.containerMenu == null
                        || mc.player.containerMenu == mc.player.inventoryMenu) {
                    return;
                }

                AbstractContainerMenu handler = mc.player.containerMenu;
                int filledSlots = 0;
                int totalItems = 0;
                for (Slot slot : handler.slots) {
                    if (slot == null) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    filledSlots++;
                    totalItems += stack.getCount();
                }
                state[0] = new GuiState(true, handler.containerId, handler.slots.size(), filledSlots, totalItems);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 2000);
        return state[0];
    }

    private void closeGuiOnGameThread(Minecraft mc, boolean sendPacket) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player != null) {
                    if (!sendPacket) {
                        AutismSharedState.get().setSuppressNextClosePacket(true);
                    }
                    if (mc.player.containerMenu != mc.player.inventoryMenu) {
                        mc.player.closeContainer();
                    }
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                }
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 2000);
    }

    private TransferAttempt shiftClickTargets(Minecraft mc) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                    }
                }
                result[0] = new TransferAttempt(slotCount, items);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 3000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private TransferAttempt swapTargetsToHotbar(Minecraft mc) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                int hotbarSlot = 0;
                for (int i = 0; i < handler.slots.size(); i++) {
                    if (hotbarSlot > 8) break;
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                    }
                }
                result[0] = new TransferAttempt(slotCount, items);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 3000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private TransferAttempt throwTargets(Minecraft mc) throws InterruptedException {
        TransferAttempt[] result = new TransferAttempt[1];
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.gameMode == null) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null || handler == mc.player.inventoryMenu) {
                    result[0] = new TransferAttempt(0, new LinkedHashMap<>());
                    return;
                }

                Map<String, Integer> items = new LinkedHashMap<>();
                int slotCount = 0;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot == null) continue;
                    if (AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 1, ContainerInput.THROW)) {
                        slotCount++;
                        items.merge(shortItemId(stack), stack.getCount(), Integer::sum);
                    }
                }
                result[0] = new TransferAttempt(slotCount, items);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 3000);
        return result[0] != null ? result[0] : new TransferAttempt(0, new LinkedHashMap<>());
    }

    private boolean clickContainerSlotExtracted(Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input) {
        if (mc == null || mc.player == null || mc.gameMode == null || handler == null) return false;
        if (mc.player.containerMenu != handler) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;

        Slot slot = handler.slots.get(slotId);
        if (slot == null || AutismInventoryHelper.isInventorySlot(mc, slot)) return false;

        ItemStack beforeSlot = slot.getItem().copy();
        if (beforeSlot.isEmpty()) return false;

        mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);

        if (slotId >= handler.slots.size()) return true;
        Slot afterSlot = handler.slots.get(slotId);
        if (afterSlot == null) return true;

        ItemStack afterStack = afterSlot.getItem();
        if (afterStack.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(beforeSlot, afterStack) && afterStack.getCount() < beforeSlot.getCount();
    }

    private boolean clickPlayerSlotDeposited(Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input) {
        if (mc == null || mc.player == null || mc.gameMode == null || handler == null) return false;
        if (mc.player.containerMenu != handler) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;

        Slot slot = handler.slots.get(slotId);
        if (slot == null || !AutismInventoryHelper.isInventorySlot(mc, slot)) return false;

        ItemStack beforeSlot = slot.getItem().copy();
        if (beforeSlot.isEmpty()) return false;

        mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);

        if (slotId >= handler.slots.size()) return true;
        Slot afterSlot = handler.slots.get(slotId);
        if (afterSlot == null) return true;

        ItemStack afterStack = afterSlot.getItem();
        if (afterStack.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(beforeSlot, afterStack) && afterStack.getCount() < beforeSlot.getCount();
    }
    private boolean shouldTransferSlot(ItemStack stack, int visibleSlot) {
        if (stack == null || stack.isEmpty()) return false;
        if (targetItems.isEmpty()) return false;
        return matchesTargetEntry(stack, visibleSlot);
    }

    private boolean matchesTargetEntry(ItemStack stack, int visibleSlot) {
        List<ItemTarget> targets = runtimeTargets();
        if (targets.isEmpty()) return false;
        for (ItemTarget itemTarget : targets) {
            if (itemTarget.hasSlot() && itemTarget.slot != visibleSlot) {
                if (!itemTarget.hasIdentity()) continue;
                ItemTarget identityTarget = itemTarget.copy();
                identityTarget.slot = -1;
                if (identityTarget.matches(stack, visibleSlot)) return true;
                continue;
            }
            if (!itemTarget.hasIdentity() || itemTarget.matches(stack, visibleSlot)) return true;
        }
        return false;
    }

    private Map<String, Integer> collectTargetContainerTotals(InventorySnapshot snapshot) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        if (snapshot == null) return totals;

        for (SlotSnapshot slot : snapshot.slots().values()) {
            ItemStack stack = slot.stack();
            if (stack == null || stack.isEmpty()) continue;
            if (isPlayerVisibleSlot(slot.visibleSlot())) continue;
            if (!shouldTransferSlot(stack, slot.visibleSlot())) continue;
            totals.merge(shortItemId(stack), stack.getCount(), Integer::sum);
        }
        return totals;
    }

    private int sumTotals(Map<String, Integer> totals, Set<String> items) {
        int sum = 0;
        for (String item : items) {
            sum += totals.getOrDefault(item, 0);
        }
        return sum;
    }

    private boolean isPlayerVisibleSlot(int visibleSlot) {
        return visibleSlot >= 0 && visibleSlot < AutismInventoryHelper.PLAYER_VISIBLE_SLOT_COUNT;
    }

    private InventorySnapshot captureInventoryOnly(Minecraft mc) throws InterruptedException {
        CompletableFuture<InventorySnapshot> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.player.containerMenu == null) {
                    future.complete(null);
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                String title = mc.gui.screen() != null && mc.gui.screen().getTitle() != null
                    ? mc.gui.screen().getTitle().getString()
                    : "";
                Map<Integer, SlotSnapshot> slots = new LinkedHashMap<>();
                for (Slot slot : handler.slots) {
                    if (slot == null) continue;
                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);

                    if (isPlayerVisibleSlot(visibleSlot)) {
                        slots.put(visibleSlot, new SlotSnapshot(visibleSlot, formatSlotLabel(visibleSlot), copyStack(slot.getItem())));
                    }
                }
                future.complete(new InventorySnapshot(title, System.currentTimeMillis(), slots));
            } catch (Exception e) {
                future.complete(null);
            }
        });
        return awaitFutureValue(future, 3000);
    }

    private Map<String, Integer> collectTargetPlayerTotals(InventorySnapshot snapshot) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        List<ItemTarget> targets = runtimeTargets();
        if (snapshot == null || targets.isEmpty()) return totals;

        for (SlotSnapshot slot : snapshot.slots().values()) {
            ItemStack stack = slot.stack();
            if (stack == null || stack.isEmpty()) continue;
            String itemId = shortItemId(stack);
            for (ItemTarget itemTarget : targets) {
                if (matchesPlayerInventoryTarget(itemTarget, stack, slot.visibleSlot())) {
                    totals.merge(itemId, stack.getCount(), Integer::sum);
                    break;
                }
            }
        }
        return totals;
    }

    private boolean matchesPlayerInventoryTarget(ItemTarget itemTarget, ItemStack stack, int visibleSlot) {
        if (itemTarget == null) return false;
        if (itemTarget.hasIdentity()) {
            ItemTarget identityTarget = itemTarget.copy();
            identityTarget.slot = -1;
            return identityTarget.matches(stack, visibleSlot);
        }
        return itemTarget.matches(stack, visibleSlot);
    }

    private boolean transferItemsToContainer(Minecraft mc, Map<String, Integer> targetItemsToTransfer) throws InterruptedException {
        if (targetItemsToTransfer == null || targetItemsToTransfer.isEmpty()) return false;
        boolean[] result = {false};
        CountDownLatch latch = new CountDownLatch(1);

        mc.execute(() -> {
            try {
                if (mc.player == null || mc.player.containerMenu == null || mc.gameMode == null) return;
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == mc.player.inventoryMenu) return;

                for (int i = 0; i < handler.slots.size(); i++) {
                    if (!MacroExecutor.isCurrentActionRunActive()) return;
                    Slot slot = handler.slots.get(i);
                    if (slot == null || !AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;

                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!isPlayerVisibleSlot(visibleSlot)) continue;
                    if (!targetItemsToTransfer.containsKey(shortItemId(stack))) continue;

                    if (clickPlayerSlotDeposited(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        result[0] = true;
                        if (!multipleStacks) return;
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        awaitLatch(latch, 3000);
        return result[0];
    }

    private boolean matchesPlayerInventoryStack(ItemStack stack, int visibleSlot) {
        List<ItemTarget> targets = runtimeTargets();
        if (targets.isEmpty()) return false;
        for (ItemTarget itemTarget : targets) {
            if (matchesPlayerInventoryTarget(itemTarget, stack, visibleSlot)) return true;
        }
        return false;
    }

    private boolean extractTargetsFromContainerToInventory(Minecraft mc, Map<String, Integer> targetItemsToExtract) throws InterruptedException {
        if (targetItemsToExtract == null || targetItemsToExtract.isEmpty()) return false;
        boolean[] result = {false};
        CountDownLatch latch = new CountDownLatch(1);

        mc.execute(() -> {
            try {
                if (mc.player == null || mc.player.containerMenu == null || mc.gameMode == null) return;
                AbstractContainerMenu handler = mc.player.containerMenu;

                for (int i = 0; i < handler.slots.size(); i++) {
                    if (!MacroExecutor.isCurrentActionRunActive()) return;
                    Slot slot = handler.slots.get(i);
                    if (slot == null || AutismInventoryHelper.isInventorySlot(mc, slot)) continue;
                    ItemStack stack = slot.getItem();
                    if (stack == null || stack.isEmpty()) continue;

                    int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                    if (!shouldTransferSlot(stack, visibleSlot)) continue;
                    if (!targetItemsToExtract.containsKey(shortItemId(stack))) continue;

                    if (clickContainerSlotExtracted(mc, handler, i, 0, ContainerInput.QUICK_MOVE)) {
                        result[0] = true;
                        if (!multipleStacks) return;
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        awaitLatch(latch, 3000);
        return result[0];
    }

    private InventorySnapshot captureOnGameThread(Minecraft mc) throws InterruptedException {
        CompletableFuture<InventorySnapshot> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                if (mc.player == null || mc.player.containerMenu == null) {
                    future.complete(null);
                    return;
                }
                AbstractContainerMenu handler = mc.player.containerMenu;
                future.complete(captureAutoTestSnapshot(mc, handler));
            } catch (Exception e) {
                future.complete(null);
            }
        });
        return awaitFutureValue(future, 3000);
    }

    private InventorySnapshot captureAutoTestSnapshot(Minecraft mc, AbstractContainerMenu handler) {
        String title = mc.gui.screen() != null && mc.gui.screen().getTitle() != null
            ? mc.gui.screen().getTitle().getString()
            : "";
        Map<Integer, SlotSnapshot> slots = captureAllSlots(mc, handler);
        return new InventorySnapshot(title, System.currentTimeMillis(), slots);
    }

    private Map<Integer, SlotSnapshot> captureAllSlots(Minecraft mc, AbstractContainerMenu handler) {
        Map<Integer, SlotSnapshot> slots = new LinkedHashMap<>();
        for (Slot slot : handler.slots) {
            if (slot == null) continue;
            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
            slots.putIfAbsent(visibleSlot, new SlotSnapshot(visibleSlot, formatSlotLabel(visibleSlot), copyStack(slot.getItem())));
        }
        return slots;
    }

    private ItemStack copyStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private String formatSlotLabel(int visibleSlot) {
        return "Slot " + visibleSlot;
    }

    private String shortItemId(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
    }

    private void ensureMacroRunning() throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || !MacroExecutor.isCurrentActionRunActive()) {
            throw new InterruptedException("Macro stopped");
        }
    }

    private void sleepInterruptibly(long millis) throws InterruptedException {
        long remaining = Math.max(0L, millis);
        while (remaining > 0L) {
            ensureMacroRunning();
            long slice = Math.min(remaining, ACTION_WAIT_POLL_MS);
            Thread.sleep(slice);
            remaining -= slice;
        }
    }

    private boolean awaitLatch(CountDownLatch latch, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (latch.getCount() > 0L) {
            ensureMacroRunning();
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                return false;
            }
            if (latch.await(Math.min(remaining, ACTION_WAIT_POLL_MS), TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return true;
    }

    private boolean awaitFutureCompletion(CompletableFuture<?> future, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (true) {
            ensureMacroRunning();
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                future.cancel(true);
                return false;
            }
            try {
                future.get(Math.min(remaining, ACTION_WAIT_POLL_MS), TimeUnit.MILLISECONDS);
                return true;
            } catch (TimeoutException ignored) {  } catch (CancellationException | ExecutionException e) {
                return false;
            }
        }
    }

    private <T> T awaitFutureValue(CompletableFuture<T> future, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (true) {
            ensureMacroRunning();
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                future.cancel(true);
                return null;
            }
            try {
                return future.get(Math.min(remaining, ACTION_WAIT_POLL_MS), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {  } catch (CancellationException | ExecutionException e) {
                return null;
            }
        }
    }

    private int clampDelayMs(int value) {
        return Math.max(0, Math.min(value, MAX_DELAY_MS));
    }

    private int clampIterations(int value) {
        return Math.max(1, Math.min(value, MAX_ITERATIONS));
    }

    private String normalizedOpenCommand() {
        String raw = openCommand == null ? "" : openCommand.trim();
        return raw.isEmpty() ? "/ec" : raw;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("mode", mode.name());
        tag.put("targetItems", ItemTarget.toTagList(resolvedTargets()));

        tag.putString("openCommand", normalizedOpenCommand());
        tag.putString("openMode", openMode.name());
        tag.putInt("containerX", containerPos.getX());
        tag.putInt("containerY", containerPos.getY());
        tag.putInt("containerZ", containerPos.getZ());

        tag.putString("dupeVector", dupeVector.name());
        tag.putInt("delayBeforeReopen", clampDelayMs(delayBeforeReopen));
        tag.putInt("delayAfterReopen", clampDelayMs(delayAfterReopen));
        tag.putInt("iterations", clampIterations(iterations));
        tag.putInt("maxTransferAttempts", Math.max(1, Math.min(maxTransferAttempts, 20)));
        tag.putInt("transferRetryDelayMs", Math.max(10, Math.min(transferRetryDelayMs, 500)));
        tag.putBoolean("multipleStacks", multipleStacks);

        tag.putInt("spamCount", Math.max(1, Math.min(spamCount, 20)));
        tag.putInt("spamDelayMs", Math.max(10, Math.min(spamDelayMs, 1000)));

        tag.putInt("grabPreDelayMs", 0);
        tag.putBoolean("closeAfterGrab", true);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {

        if (tag.contains("mode")) {
            try {
                String modeStr = tag.getStringOr("mode", "DUPE");

                if ("AUTO_TEST".equals(modeStr) || "GRAB".equals(modeStr) ||
                    "SAVE".equals(modeStr) || "COMPARE".equals(modeStr) || "CLEAR".equals(modeStr)) {
                    mode = Mode.DUPE;
                } else {
                    mode = Mode.valueOf(modeStr);
                }
            } catch (IllegalArgumentException ignored) {
                mode = Mode.DUPE;
            }
        } else {
            mode = Mode.DUPE;
        }
        itemTargets.clear();
        targetItems.clear();
        if (tag.contains("targetItems")) {
            ListTag list = tag.getList("targetItems").orElse(new ListTag());
            itemTargets.addAll(ItemTarget.fromElementList(list));
        }
        syncLegacyTargetItems();

        if (tag.contains("openCommand")) openCommand = tag.getStringOr("openCommand", "/ec");
        if (tag.contains("openMode")) {
            try {
                openMode = OpenMode.valueOf(tag.getStringOr("openMode", "COMMAND"));
            } catch (IllegalArgumentException ignored) {
                openMode = OpenMode.COMMAND;
            }
        }
        if (tag.contains("containerX") || tag.contains("containerY") || tag.contains("containerZ")) {
            containerPos = new BlockPos(
                tag.getIntOr("containerX", 0),
                tag.getIntOr("containerY", 0),
                tag.getIntOr("containerZ", 0));
        }

        if (tag.contains("dupeVector")) {
            try {
                dupeVector = DupeVector.valueOf(tag.getStringOr("dupeVector", "DESYNC_REOPEN"));
            } catch (IllegalArgumentException ignored) {
                dupeVector = DupeVector.DESYNC_REOPEN;
            }
        }
        if (tag.contains("delayBeforeReopen")) delayBeforeReopen = tag.getIntOr("delayBeforeReopen", 200);
        if (tag.contains("delayAfterReopen")) delayAfterReopen = tag.getIntOr("delayAfterReopen", 500);
        if (tag.contains("iterations")) iterations = tag.getIntOr("iterations", 1);
        if (tag.contains("maxTransferAttempts")) maxTransferAttempts = tag.getIntOr("maxTransferAttempts", 5);
        if (tag.contains("transferRetryDelayMs")) transferRetryDelayMs = tag.getIntOr("transferRetryDelayMs", 50);
        if (tag.contains("multipleStacks")) multipleStacks = tag.getBooleanOr("multipleStacks", false);

        if (tag.contains("spamCount")) spamCount = tag.getIntOr("spamCount", 3);
        if (tag.contains("spamDelayMs")) spamDelayMs = tag.getIntOr("spamDelayMs", 50);
        delayBeforeReopen = clampDelayMs(delayBeforeReopen);
        delayAfterReopen = clampDelayMs(delayAfterReopen);
        iterations = clampIterations(iterations);
        openCommand = normalizedOpenCommand();
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.INVENTORY_AUDIT;
    }

    @Override
    public String getDisplayName() {
        String src = openMode == OpenMode.CONTAINER
            ? "(" + containerPos.getX() + "," + containerPos.getY() + "," + containerPos.getZ() + ")"
            : normalizedOpenCommand();
        return switch (mode) {
            case DUPE -> "Dupe " + dupeVector.name() + " " + src;
            case DUPE_SPAM -> "DupeSpam " + dupeVector.name() + " " + src + " (x" + spamCount + ")";
        };
    }

    @Override
    public String getIcon() {
        return mode == Mode.DUPE_SPAM ? "DSP" : "DUP";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private List<ItemTarget> resolvedTargets() {
        if (!itemTargets.isEmpty()) return itemTargets;
        for (String target : targetItems) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(target);
            if (parsed.hasSlot() || parsed.hasIdentity()) itemTargets.add(parsed);
        }
        return itemTargets;
    }

    private List<ItemTarget> runtimeTargets() {
        List<ItemTarget> runtime = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
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

    private record GuiState(boolean open, int syncId, int slotCount, int filledSlots, int totalItems) {}

    private record TransferAttempt(int slotCount, Map<String, Integer> itemCounts) {}

    private record SlotSnapshot(int visibleSlot, String label, ItemStack stack) {}

    private record InventorySnapshot(String title, long capturedAtMs, Map<Integer, SlotSnapshot> slots) {}
}
