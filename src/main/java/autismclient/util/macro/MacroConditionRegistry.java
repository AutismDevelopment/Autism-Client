package autismclient.util.macro;

import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismFakeGamemode;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class MacroConditionRegistry {

    private static final List<PendingCondition> pendingConditions = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong inventorySyncRevision = new AtomicLong();

    public static long inventorySyncRevision() {
        return inventorySyncRevision.get();
    }

    public static int pendingConditionCount() {
        return pendingConditions.size();
    }

    public static void recordInventorySync() {
        inventorySyncRevision.incrementAndGet();
    }

    public static CompletableFuture<Void> waitForGui(String guiTitle) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Void> future = new CompletableFuture<>();

        GuiCondition condition = new GuiCondition(guiTitle, mc.gui.screen(), future);
        pendingConditions.add(condition);
        return future;
    }

    public static CompletableFuture<Void> waitForGuiClose(String guiTitle) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingConditions.add(new GuiCloseCondition(guiTitle, future));
        return future;
    }

    public static CompletableFuture<Void> waitForGuiChange(Screen prevScreen) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingConditions.add(new GuiChangeCondition(prevScreen, future));
        return future;
    }

    public static CompletableFuture<Void> waitForItem(String itemName) {
        return waitForItem(ItemTarget.fromLegacyEntry(itemName));
    }

    public static CompletableFuture<Void> waitForItem(ItemTarget target) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ItemCondition condition = new ItemCondition(target, future);
        pendingConditions.add(condition);

        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }

        return future;
    }

    public static CompletableFuture<Void> waitForHealth(float threshold, boolean below) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        HealthCondition condition = new HealthCondition(threshold, below, future);
        pendingConditions.add(condition);

        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }

        return future;
    }

    public static CompletableFuture<Void> waitForBlock(WaitForBlockAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        BlockCondition condition = new BlockCondition(action, future);
        pendingConditions.add(condition);
        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }
        return future;
    }

    public static CompletableFuture<Void> waitForCooldown(String itemName, boolean checkMainHand) {
        return waitForCooldown(ItemTarget.fromLegacyEntry(itemName), checkMainHand);
    }

    public static CompletableFuture<Void> waitForCooldown(ItemTarget target, boolean checkMainHand) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CooldownCondition condition = new CooldownCondition(target, checkMainHand, future);
        pendingConditions.add(condition);

        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }

        return future;
    }

    public static CompletableFuture<Void> waitForPos(double x, double y, double z, double leeway,
                                                       boolean checkRotation, float yaw, float pitch, float rotLeeway) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        WaitPosCondition condition = new WaitPosCondition(x, y, z, leeway, checkRotation, yaw, pitch, rotLeeway, future);
        pendingConditions.add(condition);

        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }

        return future;
    }

    public static CompletableFuture<Void> waitForWorldChange() {
        return waitForWorldChange(new WaitForWorldChangeAction());
    }

    public static CompletableFuture<Void> waitForWorldChange(WaitForWorldChangeAction action) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Void> future = new CompletableFuture<>();
        WorldChangeCondition condition = new WorldChangeCondition(currentDimensionId(mc), action == null ? "" : action.targetDimension, future);
        pendingConditions.add(condition);
        return future;
    }

    public static CompletableFuture<Void> waitForGamemodeChange(WaitGamemodeChangeAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        GamemodeChangeCondition condition = new GamemodeChangeCondition(action, AutismFakeGamemode.snapshot(), future);
        pendingConditions.add(condition);
        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }
        return future;
    }

    public static CompletableFuture<Void> waitForPositionDelta(double distance, boolean horizontalOnly) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 origin = mc.player == null ? Vec3.ZERO : mc.player.position();
        return waitForPositionDeltaFrom(origin, distance, horizontalOnly);
    }

    public static CompletableFuture<Void> waitForPositionDeltaFrom(Vec3 origin, double distance, boolean horizontalOnly) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        PositionDeltaCondition condition = new PositionDeltaCondition(origin, distance, horizontalOnly, future);
        pendingConditions.add(condition);
        return future;
    }

    public static CompletableFuture<Void> waitForNextTick() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TickSyncCondition condition = new TickSyncCondition(future);
        pendingConditions.add(condition);

        return future;
    }

    public static void onInventorySync(Minecraft mc) {
        if (pendingConditions.isEmpty()) return;
        Screen current = mc.gui.screen();

        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond.isCancelled()) { toRemove.add(cond); continue; }
                if (cond instanceof GuiCondition gc) {
                    if (current != null && gc.checkScreen(current)) {
                        gc.complete();
                        toRemove.add(gc);
                    }
                } else if (cond instanceof HandlerItemCondition hic) {
                    if (hic.check(mc)) {
                        hic.complete();
                        toRemove.add(hic);
                    }
                } else if (cond instanceof ItemCondition ic) {
                    if (ic.check(mc)) {
                        ic.complete();
                        toRemove.add(ic);
                    }
                } else if (cond instanceof SlotChangeCondition scc) {

                    if (scc.check(mc)) {
                        scc.complete();
                        toRemove.add(scc);
                    }
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    public static void onSlotUpdate(int slot) {
        if (pendingConditions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond.isCancelled()) { toRemove.add(cond); continue; }
                if (cond instanceof SlotChangeCondition scc) {
                    if (scc.check(mc)) { scc.complete(); toRemove.add(scc); }
                } else if (cond instanceof HandlerItemCondition hic) {
                    if (hic.check(mc)) { hic.complete(); toRemove.add(hic); }
                } else if (cond instanceof ItemCondition ic) {
                    if (ic.check(mc)) { ic.complete(); toRemove.add(ic); }
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    public static void onTick(Minecraft mc) {
        if (pendingConditions.isEmpty()) return;

        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();

        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond.isCancelled()) {
                    toRemove.add(cond);
                    continue;
                }
                try {
                    if (cond.check(mc)) {
                        cond.complete();
                        toRemove.add(cond);
                    }
                } catch (Throwable t) {

                    cond.cancel();
                    toRemove.add(cond);
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    public static void onScreenChange(Screen screen) {
        if (pendingConditions.isEmpty()) return;

        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();

        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond instanceof GuiCondition gc) {
                    if (gc.checkScreen(screen)) {
                        gc.complete();
                        toRemove.add(gc);
                    }
                } else if (cond instanceof HandlerItemCondition hic) {
                    Minecraft mc = Minecraft.getInstance();
                    if (hic.check(mc)) {
                        hic.complete();
                        toRemove.add(hic);
                    }
                } else if (cond instanceof SlotChangeCondition scc) {
                    Minecraft mc = Minecraft.getInstance();
                    if (scc.check(mc)) {
                        scc.complete();
                        toRemove.add(scc);
                    }
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    private static boolean checkOnClientThread(Minecraft mc, PendingCondition condition) {
        if (mc == null || condition == null) return false;
        try {
            if (mc.isSameThread()) return condition.check(mc);
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    result.complete(condition.check(mc));
                } catch (Throwable t) {
                    result.complete(false);
                }
            });
            return Boolean.TRUE.equals(result.get(250, java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void runOnClientThread(Minecraft mc, Runnable task) {
        if (mc == null || task == null) return;
        try {
            if (mc.isSameThread()) {
                task.run();
                return;
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    task.run();
                } finally {
                    result.complete(null);
                }
            });
            result.get(250, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {  }
    }

    public static void cancelAll() {
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                cond.cancel();
            }
            pendingConditions.clear();
        }
    }

    public static boolean hasPendingConditions() {
        return !pendingConditions.isEmpty();
    }

    public static boolean hasPendingInventoryConditions() {
        if (pendingConditions.isEmpty()) return false;
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond instanceof GuiCondition
                    || cond instanceof HandlerItemCondition
                    || cond instanceof ItemCondition
                    || cond instanceof SlotChangeCondition) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasPendingSoundConditions() {
        if (pendingConditions.isEmpty()) return false;
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond instanceof SoundCondition) return true;
            }
        }
        return false;
    }

    public static CompletableFuture<Void> await(autismclient.api.macro.AddonCondition condition) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (condition == null) {
            future.complete(null);
            return future;
        }
        AddonConditionAdapter adapter = new AddonConditionAdapter(condition, future);
        pendingConditions.add(adapter);
        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, adapter)) {
            adapter.complete();
            pendingConditions.remove(adapter);
        }
        return future;
    }

    interface PendingCondition {
        boolean check(Minecraft mc);
        void complete();
        void cancel();
        boolean isCancelled();
    }

    static final class AddonConditionAdapter implements PendingCondition {
        private final autismclient.api.macro.AddonCondition condition;
        private final CompletableFuture<Void> future;

        AddonConditionAdapter(autismclient.api.macro.AddonCondition condition, CompletableFuture<Void> future) {
            this.condition = condition;
            this.future = future;
        }

        @Override public boolean check(Minecraft mc) { return condition.check(mc); }

        @Override public void complete() {
            try { condition.onComplete(); } catch (Throwable ignored) {  }
            future.complete(null);
        }

        @Override public void cancel() { future.cancel(true); }

        @Override public boolean isCancelled() { return future.isCancelled(); }
    }

    static class GuiCondition implements PendingCondition {
        final String title;
        final String titleLower;
        final String[] titleWords;

        final java.lang.ref.WeakReference<Screen> prevScreen;
        final CompletableFuture<Void> future;

        GuiCondition(String title, Screen prevScreen, CompletableFuture<Void> future) {
            this.title = MacroExecutor.normalizeManualText(title);
            this.titleLower = this.title.toLowerCase();
            this.titleWords = titleLower.isEmpty() ? new String[0] : titleLower.split("\\s+");
            this.prevScreen = new java.lang.ref.WeakReference<>(prevScreen);
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            return checkScreen(mc.gui.screen());
        }

        public boolean checkScreen(Screen screen) {
            if (screen == null) return false;
            if (MacroGuiMatcher.isOwnScreen(screen)) return false;

            if (screen == prevScreen.get()) return false;
            if (!titleLower.isEmpty() && !MacroGuiMatcher.matches(screen, title)) {
                return false;
            }

            if (!titleLower.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null
                        && mc.player.containerMenu != null
                        && mc.player.containerMenu != mc.player.inventoryMenu
                        && screen instanceof AbstractContainerScreen<?>) {
                    return mc.player.containerMenu.getStateId() > 0;
                }
            }
            return true;
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class GuiChangeCondition implements PendingCondition {

        final java.lang.ref.WeakReference<Screen> prevScreen;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;
        GuiChangeCondition(Screen prevScreen, CompletableFuture<Void> future) {
            this.prevScreen = new java.lang.ref.WeakReference<>(prevScreen);
            this.future = future;
        }
        @Override public boolean check(Minecraft mc) { return mc.gui.screen() != prevScreen.get(); }
        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    static class GuiCloseCondition implements PendingCondition {
        final String titleLower;
        final String[] titleWords;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;
        private boolean sawMatchingGui = false;

        GuiCloseCondition(String title, CompletableFuture<Void> future) {
            this.titleLower = MacroExecutor.normalizeManualText(title).toLowerCase();
            this.titleWords = titleLower.isEmpty() ? new String[0] : titleLower.split("\\s+");
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            Screen screen = mc.gui.screen();
            if (screen != null && !titleLower.isEmpty()
                    && MacroGuiMatcher.matches(screen, titleLower)) {
                sawMatchingGui = true;
                return false;
            }

            if (titleLower.isEmpty()) {
                if (screen != null) { sawMatchingGui = true; return false; }
                return sawMatchingGui;
            }

            return sawMatchingGui || screen == null;
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    static class ItemCondition implements PendingCondition {
        final ItemTarget target;
        final CompletableFuture<Void> future;

        ItemCondition(ItemTarget target, CompletableFuture<Void> future) {
            this.target = target == null ? new ItemTarget() : target.copy();
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null || !target.hasIdentity()) return false;
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && target.matches(stack, i)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class HealthCondition implements PendingCondition {
        final float threshold;
        final boolean below;
        final CompletableFuture<Void> future;

        HealthCondition(float threshold, boolean below, CompletableFuture<Void> future) {
            this.threshold = threshold;
            this.below = below;
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null) return false;
            float health = mc.player.getHealth();
            return below ? (health < threshold) : (health > threshold);
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class CooldownCondition implements PendingCondition {
        final ItemTarget target;
        final boolean checkMainInteractionHand;
        final CompletableFuture<Void> future;

        CooldownCondition(ItemTarget target, boolean checkMainHand, CompletableFuture<Void> future) {
            this.target = target == null ? new ItemTarget() : target.copy();
            this.checkMainInteractionHand = checkMainHand;
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null) return false;
            if (target.hasIdentity()) {
                boolean foundAny = false;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && target.matches(stack, i)) {
                        foundAny = true;
                        if (mc.player.getCooldowns().isOnCooldown(stack)) {
                            return false;
                        }
                    }
                }
                return foundAny;
            } else {
                ItemStack stack = checkMainInteractionHand ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
                if (stack.isEmpty()) return true;
                return !mc.player.getCooldowns().isOnCooldown(stack);
            }
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class WaitPosCondition implements PendingCondition {
        final double x, y, z;
        final double leewaySquared;
        final boolean checkRotation;
        final float yaw, pitch, rotLeeway;
        final CompletableFuture<Void> future;

        WaitPosCondition(double x, double y, double z, double leeway,
                         boolean checkRotation, float yaw, float pitch, float rotLeeway,
                         CompletableFuture<Void> future) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.leewaySquared = leeway * leeway;
            this.checkRotation = checkRotation;
            this.yaw = yaw;
            this.pitch = pitch;
            this.rotLeeway = rotLeeway;
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null) return false;

            double dx = mc.player.getX() - x, dy = mc.player.getY() - y, dz = mc.player.getZ() - z;
            if (dx*dx + dy*dy + dz*dz > leewaySquared) return false;

            if (checkRotation) {
                float currentYaw   = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot());
                float currentPitch = net.minecraft.util.Mth.wrapDegrees(mc.player.getXRot());
                float targetYaw    = net.minecraft.util.Mth.wrapDegrees(yaw);
                float targetPitch  = net.minecraft.util.Mth.wrapDegrees(pitch);
                if (Math.abs(currentYaw - targetYaw)     > rotLeeway) return false;
                if (Math.abs(currentPitch - targetPitch) > rotLeeway) return false;
            }
            return true;
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class WorldChangeCondition implements PendingCondition {
        final String initialDimension;
        final String targetDimension;
        final long createdAtNanos;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        WorldChangeCondition(String initialDimension, String targetDimension, CompletableFuture<Void> future) {
            this.initialDimension = initialDimension == null ? "" : initialDimension;
            this.targetDimension = normalizeDimensionId(targetDimension);
            this.future = future;
            this.createdAtNanos = System.nanoTime();
        }

        @Override
        public boolean check(Minecraft mc) {

            if (lastRespawnNanos > createdAtNanos && targetDimension.isEmpty()) return true;

            String current = currentDimensionId(mc);

            if (!current.isEmpty() && !current.equals(initialDimension)) {
                return targetDimension.isEmpty() || dimensionMatches(current, targetDimension);
            }

            if (lastRespawnNanos > createdAtNanos && !targetDimension.isEmpty()
                && !current.isEmpty() && dimensionMatches(current, targetDimension)) {
                return true;
            }
            return false;
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    static class GamemodeChangeCondition implements PendingCondition {
        final WaitGamemodeChangeAction action;
        final AutismFakeGamemode.Snapshot baseline;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        GamemodeChangeCondition(WaitGamemodeChangeAction action, AutismFakeGamemode.Snapshot baseline, CompletableFuture<Void> future) {
            this.action = action == null ? new WaitGamemodeChangeAction() : action;
            this.baseline = baseline == null ? AutismFakeGamemode.snapshot() : baseline;
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            AutismFakeGamemode.Snapshot now = AutismFakeGamemode.snapshot();
            if (now.realRevision() > baseline.realRevision() && action.accepts(safeMode(now.realMode()))) {
                return true;
            }
            return action.detectFake
                && now.fakeRevision() > baseline.fakeRevision()
                && action.accepts(safeMode(now.displayedMode()));
        }

        private GameType safeMode(GameType mode) {
            return mode == null ? GameType.DEFAULT_MODE : mode;
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    private static volatile long lastRespawnNanos = 0L;
    private static volatile long lastTeleportNanos = 0L;

    public static void onRespawnPacket() {
        lastRespawnNanos = System.nanoTime();
    }

    public static void onTeleportPacket() {
        lastTeleportNanos = System.nanoTime();
    }

    static class PositionDeltaCondition implements PendingCondition {
        final Vec3 origin;
        final double distanceSquared;
        final boolean horizontalOnly;
        final boolean anyMovement;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        PositionDeltaCondition(Vec3 origin, double distance, boolean horizontalOnly, CompletableFuture<Void> future) {
            this.origin = origin == null ? Vec3.ZERO : origin;
            double d = Math.max(0.0, distance);
            this.anyMovement = d <= 0.0;
            this.distanceSquared = d * d;
            this.horizontalOnly = horizontalOnly;
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null) return false;
            Vec3 now = mc.player.position();
            double dx = now.x - origin.x;
            double dy = horizontalOnly ? 0.0 : now.y - origin.y;
            double dz = now.z - origin.z;
            double d2 = dx * dx + dy * dy + dz * dz;

            if (anyMovement) return d2 > 0.0001;
            return d2 >= distanceSquared;
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    private static String currentDimensionId(Minecraft mc) {
        try {
            return mc != null && mc.level != null && mc.level.dimension() != null
                ? mc.level.dimension().identifier().toString()
                : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean dimensionMatches(String current, String target) {
        if (target == null || target.isBlank()) return true;
        return normalizeDimensionId(current).equals(normalizeDimensionId(target));
    }

    private static String normalizeDimensionId(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return "";
        if (value.equals("nether") || value.equals("the_nether")) return "minecraft:the_nether";
        if (value.equals("overworld") || value.equals("world")) return "minecraft:overworld";
        if (value.equals("end") || value.equals("the_end")) return "minecraft:the_end";
        if (!value.contains(":")) return "minecraft:" + value;
        return value;
    }

    static class BlockCondition implements PendingCondition {
        final WaitForBlockAction action;
        final java.util.Set<String> blockIdSet;
        final CompletableFuture<Void> future;

        BlockCondition(WaitForBlockAction action, CompletableFuture<Void> future) {
            this.action = action;
            this.blockIdSet = new java.util.HashSet<>(action.blockIds);
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.level == null || mc.player == null) return false;
            boolean destroyed = action.waitBehavior == WaitForBlockAction.WaitBehavior.DESTROYED;
            boolean anyBlock = action.anyBlock;
            switch (action.checkMode) {
                case AT_POSITION: {
                    Block b = mc.level.getBlockState(action.blockPos).getBlock();
                    boolean isAir = b == Blocks.AIR;
                    if (destroyed) {

                        if (anyBlock) return isAir;

                        if (isAir) return true;
                        return !blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                    } else {

                        if (isAir) return false;
                        if (!anyBlock && !blockIdSet.isEmpty()) {
                            if (!blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString())) return false;
                        }
                    }
                    if (action.mustBeInReach) return isAtPosReachable(mc);
                    return true;
                }
                case IN_REACH: {
                    int r = (int) Math.ceil(action.searchRadius);
                    int cx = (int) mc.player.getX();
                    int cy = (int) mc.player.getY();
                    int cz = (int) mc.player.getZ();
                    BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
                    if (destroyed) {

                        if (anyBlock) return false;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dy = -r; dy <= r; dy++) {
                                for (int dz = -r; dz <= r; dz++) {
                                    mut.set(cx + dx, cy + dy, cz + dz);
                                    Block b = mc.level.getBlockState(mut).getBlock();
                                    if (b != Blocks.AIR && blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString())) return false;
                                }
                            }
                        }
                        return true;
                    } else {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dy = -r; dy <= r; dy++) {
                                for (int dz = -r; dz <= r; dz++) {
                                    mut.set(cx + dx, cy + dy, cz + dz);
                                    Block b = mc.level.getBlockState(mut).getBlock();
                                    if (b == Blocks.AIR) continue;
                                    if (anyBlock || blockIdSet.isEmpty()) return true;
                                    if (blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString())) return true;
                                }
                            }
                        }
                        return false;
                    }
                }
                case LOOKING_AT: {
                    if (destroyed) {

                        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                            return anyBlock;
                        }
                        if (!anyBlock && !blockIdSet.isEmpty()) {
                            BlockHitResult bhr = (BlockHitResult) mc.hitResult;
                            Block b = mc.level.getBlockState(bhr.getBlockPos()).getBlock();
                            return !blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                        }
                        return false;
                    } else {
                        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return false;
                        BlockHitResult bhr = (BlockHitResult) mc.hitResult;
                        if (anyBlock || blockIdSet.isEmpty()) return true;
                        Block b = mc.level.getBlockState(bhr.getBlockPos()).getBlock();
                        return blockIdSet.contains(BuiltInRegistries.BLOCK.getKey(b).toString());
                    }
                }
                default: return false;
            }
        }

        private boolean isAtPosReachable(Minecraft mc) {
            Vec3 center = Vec3.atCenterOf(action.blockPos);
            if (mc.player.distanceToSqr(center) > 36.0) return false;
            ClipContext ctx = new ClipContext(
                mc.player.getEyePosition(), center,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
            BlockHitResult result = mc.level.clip(ctx);
            return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(action.blockPos);
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { future.cancel(true); }
        @Override public boolean isCancelled() { return future.isCancelled(); }
    }

    static class TickSyncCondition implements PendingCondition {
        final CompletableFuture<Void> future;

        TickSyncCondition(CompletableFuture<Void> future) {
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {

            return true;
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    static class ServerTickSyncCondition implements PendingCondition {
        final CompletableFuture<Void> future;
        final int bufferMs;
        final int maxWaitMs;
        final boolean ignorePing;
        final long startTimeNanos;

        ServerTickSyncCondition(CompletableFuture<Void> future, int bufferMs, int maxWaitMs, boolean ignorePing) {
            this.future = future;
            this.bufferMs = bufferMs;
            this.maxWaitMs = maxWaitMs;
            this.ignorePing = ignorePing;
            this.startTimeNanos = System.nanoTime();
        }

        @Override
        public boolean check(Minecraft mc) {
            long now = System.nanoTime();

            long elapsedMs = (now - startTimeNanos) / 1_000_000;
            if (elapsedMs >= maxWaitMs) {
                return true;
            }

            if (!ServerTickTracker.isReady()) {
                return false;
            }

            long optimalTime = ServerTickTracker.getOptimalSendTime(bufferMs, ignorePing);
            return now >= optimalTime;
        }

        @Override
        public void complete() { future.complete(null); }

        @Override
        public void cancel() { future.cancel(true); }

        @Override
        public boolean isCancelled() { return future.isCancelled(); }
    }

    public static CompletableFuture<Void> waitForServerTick(int bufferMs, int maxWaitMs, boolean ignorePing) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerTickSyncCondition condition = new ServerTickSyncCondition(future, bufferMs, maxWaitMs, ignorePing);
        pendingConditions.add(condition);
        return future;
    }

    public static CompletableFuture<Void> waitForEntity(WaitForEntityAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        EntityCondition condition = new EntityCondition(action, future);
        pendingConditions.add(condition);
        Minecraft mc = Minecraft.getInstance();
        if (condition.check(mc)) {
            condition.complete();
            pendingConditions.remove(condition);
        }
        return future;
    }

    public static CompletableFuture<Void> waitForItemInHandler(String itemName) {
        return waitForItemInHandler(ItemTarget.fromLegacyEntry(itemName));
    }

    public static CompletableFuture<Void> waitForItemInHandler(ItemTarget target) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        HandlerItemCondition condition = new HandlerItemCondition(target, future);
        pendingConditions.add(condition);
        Minecraft mc = Minecraft.getInstance();
        if (checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }
        return future;
    }

    public static CompletableFuture<Void> waitForSlotChange(int slotNumber) {
        return waitForSlotChange(new WaitForSlotChangeAction(slotNumber));
    }

    public static CompletableFuture<Void> waitForSound(WaitForSoundAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingConditions.add(new SoundCondition(action, future));
        return future;
    }

    public static void onSoundPacket(String soundId, double x, double y, double z) {
        if (pendingConditions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond.isCancelled()) { toRemove.add(cond); continue; }
                if (cond instanceof SoundCondition sc && sc.matches(mc, soundId, x, y, z)) {
                    sc.action.recordMatch(soundId, x, y, z);
                    sc.complete();
                    toRemove.add(sc);
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    public static CompletableFuture<Void> waitForSlotChange(WaitForSlotChangeAction action) {
        Minecraft mc = Minecraft.getInstance();

        java.util.Map<Integer, String>  initialNames  = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> initialCounts = new java.util.HashMap<>();
        runOnClientThread(mc, () -> {
        if (mc.player != null) {
            net.minecraft.world.inventory.AbstractContainerMenu handler = mc.player.containerMenu;
            for (int i = 0; i < action.entries.size(); i++) {
                WaitForSlotChangeAction.WaitEntry e = action.entries.get(i);
                ItemTarget entryTarget = e.resolvedTarget();
                if (e.waitMode != WaitForSlotChangeAction.WaitMode.ANY_CHANGE) continue;
                if (entryTarget == null || !entryTarget.hasSlot()) continue;
                int slotNum = entryTarget.slot;
                int handlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, slotNum);
                if (handler != null && handlerSlot >= 0 && handlerSlot < handler.slots.size()) {
                    ItemStack s = handler.slots.get(handlerSlot).getItem();
                    initialNames .put(i, snapshotStackIdentity(s));
                    initialCounts.put(i, s.isEmpty() ? 0  : s.getCount());
                }
            }
        }
        });
        CompletableFuture<Void> future = new CompletableFuture<>();
        SlotChangeCondition condition = new SlotChangeCondition(action, initialNames, initialCounts, future);
        pendingConditions.add(condition);

        boolean hasAnyChange = action.entries.stream()
                .anyMatch(e -> e.waitMode == WaitForSlotChangeAction.WaitMode.ANY_CHANGE);
        if (!hasAnyChange && checkOnClientThread(mc, condition)) {
            condition.complete();
            pendingConditions.remove(condition);
        }
        return future;
    }

    static class EntityCondition implements PendingCondition {
        final WaitForEntityAction action;
        final java.util.Set<String> entityIdSet;
        final java.util.Set<String> specificUuids;
        final double radiusSq;
        final CompletableFuture<Void> future;

        EntityCondition(WaitForEntityAction action, CompletableFuture<Void> future) {
            this.action = action;
            this.entityIdSet = new java.util.HashSet<>();
            this.specificUuids = new java.util.HashSet<>();
            for (String entry : action.entityIds) {
                if (entry.startsWith("~")) {
                    String[] p = entry.split("~", 4);
                    if (p.length >= 2 && !p[1].isEmpty()) specificUuids.add(p[1]);
                } else {
                    entityIdSet.add(entry);
                }
            }
            this.radiusSq = action.radius * action.radius;
            this.future = future;
        }

        private boolean entityMatches(net.minecraft.world.entity.Entity entity) {
            if (action.containerEntitiesOnly) {
                String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                if (!(typeId.contains("boat") || typeId.contains("minecart") || typeId.contains("llama") || typeId.contains("chest"))) return false;
            }
            if (entityIdSet.isEmpty() && specificUuids.isEmpty()) return true;
            if (!entityIdSet.isEmpty()) {
                String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                if (entityIdSet.contains(typeId)) return true;
            }
            if (!specificUuids.isEmpty() && specificUuids.contains(entity.getStringUUID())) return true;
            return false;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.level == null || mc.player == null) return false;
            switch (action.checkMode) {
                case LOOKING_AT: {
                    net.minecraft.world.entity.Entity targeted = mc.crosshairPickEntity;
                    if (targeted == null || targeted == mc.player) return false;
                    return entityMatches(targeted);
                }
                case MOUNTED_IN: {
                    net.minecraft.world.entity.Entity vehicle = mc.player.getVehicle();
                    return vehicle != null && entityMatches(vehicle);
                }
                case WITHIN_REACH: {
                    double reachSq = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                        if (entity == mc.player) continue;
                        if (entity.distanceToSqr(mc.player) > reachSq) continue;
                        if (entityMatches(entity)) return true;
                    }
                    return false;
                }
                case NEARBY:
                default: {
                    Vec3 center = action.centerOnPlayer
                        ? new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                        : new Vec3(action.x, action.y, action.z);
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                        if (entity == mc.player) continue;
                        if (entity.distanceToSqr(center) > radiusSq) continue;
                        if (entityMatches(entity)) return true;
                    }
                    return false;
                }
            }
        }

        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { future.cancel(true); }
        @Override public boolean isCancelled() { return future.isCancelled(); }
    }

    static class SlotChangeCondition implements PendingCondition {
        final WaitForSlotChangeAction action;

        final java.util.Map<Integer, String>  initialNames;
        final java.util.Map<Integer, Integer> initialCounts;
        final CompletableFuture<Void> future;

        SlotChangeCondition(WaitForSlotChangeAction action,
                            java.util.Map<Integer, String>  initialNames,
                            java.util.Map<Integer, Integer> initialCounts,
                            CompletableFuture<Void> future) {
            this.action        = action;
            this.initialNames  = initialNames;
            this.initialCounts = initialCounts;
            this.future        = future;
        }

        SlotChangeCondition(int slotNumber, String initialItemName, int initialCount, CompletableFuture<Void> future) {
            WaitForSlotChangeAction a = new WaitForSlotChangeAction(slotNumber);
            this.action = a;
            this.initialNames  = new java.util.HashMap<>();
            this.initialCounts = new java.util.HashMap<>();
            if (slotNumber >= 0) {
                this.initialNames .put(0, initialItemName);
                this.initialCounts.put(0, initialCount);
            }
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null) return false;
            if (action.entries.isEmpty()) return true;
            net.minecraft.world.inventory.AbstractContainerMenu handler = mc.player.containerMenu;
            for (int i = 0; i < action.entries.size(); i++) {
                if (!checkEntry(mc, handler, action.entries.get(i), i)) return false;
            }
            return true;
        }

        private boolean checkEntry(Minecraft mc,
                                   net.minecraft.world.inventory.AbstractContainerMenu handler,
                                   WaitForSlotChangeAction.WaitEntry e,
                                   int entryIdx) {
                ItemTarget entryTarget = e.resolvedTarget();
                if (entryTarget == null) entryTarget = new ItemTarget();

                if (entryTarget.hasSlot()) {

                int slotNum = entryTarget.slot;
                if (handler == null)
                    return e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
                int handlerSlot = AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, slotNum);
                if (handlerSlot < 0 || handlerSlot >= handler.slots.size())
                    return e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
                ItemStack stack = handler.slots.get(handlerSlot).getItem();
                return checkStack(stack, entryTarget, slotNum, e, entryIdx);
            } else {

                if (e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY) {

                    if (handler != null) {
                        for (net.minecraft.world.inventory.Slot s : handler.slots) {
                            if (s == null || s.getItem().isEmpty()) continue;
                            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, s.index);
                            if (!entryTarget.hasIdentity() || entryTarget.matches(s.getItem(), visibleSlot))
                                return false;
                        }
                    } else {
                        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                            ItemStack stack = mc.player.getInventory().getItem(i);
                            if (stack.isEmpty()) continue;
                            if (!entryTarget.hasIdentity() || entryTarget.matches(stack, i))
                                return false;
                        }
                    }
                    return true;
                } else {

                    if (handler != null) {
                        for (net.minecraft.world.inventory.Slot s : handler.slots) {
                            if (s == null || s.getItem().isEmpty()) continue;
                            ItemStack stack = s.getItem();
                            int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, s.index);
                            if (entryTarget.hasIdentity() && !entryTarget.matches(stack, visibleSlot)) continue;
                            if (checkStack(stack, entryTarget, visibleSlot, e, entryIdx)) return true;
                        }
                    } else {
                        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                            ItemStack stack = mc.player.getInventory().getItem(i);
                            if (stack.isEmpty()) continue;
                            if (entryTarget.hasIdentity() && !entryTarget.matches(stack, i)) continue;
                            if (checkStack(stack, entryTarget, i, e, entryIdx)) return true;
                        }
                    }
                    return false;
                }
            }
        }

        private boolean checkStack(ItemStack stack, ItemTarget target, int visibleSlot,
                                   WaitForSlotChangeAction.WaitEntry e, int entryIdx) {
            boolean nameMatches = target == null || !target.hasIdentity() || (!stack.isEmpty() && target.matches(stack, visibleSlot));
            return switch (e.waitMode) {
                case NOT_EMPTY      -> !stack.isEmpty() && nameMatches;
                case IS_EMPTY       -> stack.isEmpty();
                case COUNT_AT_LEAST -> !stack.isEmpty() && nameMatches && stack.getCount() >= e.targetCount;
                case COUNT_BELOW    -> stack.isEmpty() || (nameMatches && stack.getCount() < e.targetCount);
                case ANY_CHANGE     -> {
                    String initName  = initialNames .getOrDefault(entryIdx, "");
                    int    initCount = initialCounts.getOrDefault(entryIdx, 0);
                    String curName   = snapshotStackIdentity(stack);
                    int    curCount  = stack.isEmpty() ? 0  : stack.getCount();
                    yield !curName.equals(initName) || curCount != initCount;
                }
            };
        }

        @Override public void complete()       { future.complete(null); }
        @Override public void cancel()         { future.cancel(true); }
        @Override public boolean isCancelled() { return future.isCancelled(); }
    }

    private static String snapshotStackIdentity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String rich = MacroExecutor.serializeTextComponent(stack.getHoverName());
        String components = String.valueOf(stack.getComponents());
        return id + "|" + stack.getCount() + "|" + (rich == null ? "" : rich) + "|" + components.hashCode();
    }

    static class HandlerItemCondition implements PendingCondition {
        final ItemTarget target;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        HandlerItemCondition(ItemTarget target, CompletableFuture<Void> future) {
            this.target = target == null ? new ItemTarget() : target.copy();
            this.future = future;
        }

        @Override
        public boolean check(Minecraft mc) {
            if (mc.player == null || !target.hasIdentity()) return false;
            net.minecraft.world.inventory.AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return false;
            for (net.minecraft.world.inventory.Slot slot : handler.slots) {
                if (slot == null || slot.getItem().isEmpty()) continue;
                int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                if (target.matches(slot.getItem(), visibleSlot)) return true;
            }
            return false;
        }

        @Override public void complete()            { future.complete(null); }
        @Override public void cancel()              { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled()      { return cancelled || future.isCancelled(); }
    }

    static class SoundCondition implements PendingCondition {
        final WaitForSoundAction action;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        SoundCondition(WaitForSoundAction action, CompletableFuture<Void> future) {
            this.action = action;
            this.future = future;
        }

        public boolean matches(Minecraft mc, String soundId, double x, double y, double z) {
            if (!action.soundIds.isEmpty()) {
                boolean found = false;
                String played = normalizeSoundId(soundId);
                String playedPath = soundPath(played);
                for (String id : action.soundIds) {
                    String expected = normalizeSoundId(id);
                    if (expected.equals(played)
                            || expected.equals(playedPath)
                            || soundPath(expected).equals(played)
                            || soundPath(expected).equals(playedPath)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            if (action.checkDistance && mc != null && mc.player != null) {
                double dx = mc.player.getX() - x;
                double dy = mc.player.getY() - y;
                double dz = mc.player.getZ() - z;
                if (dx*dx + dy*dy + dz*dz > action.maxDistance * action.maxDistance) return false;
            }
            return true;
        }

        private static String normalizeSoundId(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private static String soundPath(String value) {
            String normalized = normalizeSoundId(value);
            int split = normalized.indexOf(':');
            return split >= 0 && split + 1 < normalized.length() ? normalized.substring(split + 1) : normalized;
        }

        @Override public boolean check(Minecraft mc) { return false; }
        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    public static CompletableFuture<Void> waitForLanStep(WaitForLanStepAction action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        LanStepCondition cond = new LanStepCondition(action, future);

        if (cond.checkNow()) {
            future.complete(null);
        } else {
            pendingConditions.add(cond);
        }
        return future;
    }

    public static void onLanStepProgress() {
        if (pendingConditions.isEmpty()) return;
        java.util.List<PendingCondition> toRemove = new java.util.ArrayList<>();
        synchronized (pendingConditions) {
            for (PendingCondition cond : pendingConditions) {
                if (cond.isCancelled()) { toRemove.add(cond); continue; }
                if (cond instanceof LanStepCondition lsc && lsc.checkNow()) {
                    lsc.complete();
                    toRemove.add(lsc);
                }
            }
            pendingConditions.removeAll(toRemove);
        }
    }

    static class LanStepCondition implements PendingCondition {
        final WaitForLanStepAction action;
        final CompletableFuture<Void> future;
        private volatile boolean cancelled = false;

        LanStepCondition(WaitForLanStepAction action, CompletableFuture<Void> future) {
            this.action = action;
            this.future = future;
        }

        boolean checkNow() {
            autismclient.util.AutismLANSync sync = autismclient.util.AutismLANSync.getInstance();
            if (!sync.isInSession()) return true;

            String myUsername = sync.getMyUsername();
            java.util.Map<String, Integer> steps = sync.getAllPeerSteps();

            if (!action.filterByUser) {
                return anyOtherPeerReached(steps, myUsername, action.defaultStep);
            }

            if (action.entries.isEmpty()) return anyOtherPeerReached(steps, myUsername, action.defaultStep);

            int peerCount = sync.getConnectedCount();
            for (WaitForLanStepAction.LanStepEntry req : action.entries) {
                String targetUser = req.username;

                if (peerCount <= 2 && !targetUser.isEmpty()) {
                    boolean nameExists = false;
                    for (String name : steps.keySet()) {

                        if (name.equals(targetUser) && !name.equals(myUsername)) { nameExists = true; break; }
                    }
                    if (!nameExists) {

                        targetUser = "";
                    }
                }

                if (targetUser.isEmpty()) {

                    boolean anyReached = false;
                    for (java.util.Map.Entry<String, Integer> entry : steps.entrySet()) {
                        if (entry.getKey().equals(myUsername)) continue;
                        if (entry.getValue() >= req.step) { anyReached = true; break; }
                    }
                    if (!anyReached) return false;
                } else {

                    java.util.Map<String, ?> clients = sync.getConnectedClients();
                    if (!clients.containsKey(targetUser)) continue;
                    int peerStep = steps.getOrDefault(targetUser, 0);
                    if (peerStep < req.step) return false;
                }
            }
            return true;
        }

        private boolean anyOtherPeerReached(java.util.Map<String, Integer> steps, String myUsername, int targetStep) {
            for (java.util.Map.Entry<String, Integer> entry : steps.entrySet()) {
                if (entry.getKey().equals(myUsername)) continue;
                if (entry.getValue() >= targetStep) return true;
            }
            return false;
        }

        @Override public boolean check(net.minecraft.client.Minecraft mc) { return checkNow(); }
        @Override public void complete() { future.complete(null); }
        @Override public void cancel() { cancelled = true; future.cancel(true); }
        @Override public boolean isCancelled() { return cancelled || future.isCancelled(); }
    }

    private static boolean matchesLower(String searchLower, String[] searchWords, String target) {
        String normalizedTarget = MacroExecutor.normalizeManualText(target);
        if (searchLower.isEmpty() || normalizedTarget.isEmpty()) return false;
        String tl = normalizedTarget.toLowerCase();
        if (searchLower.equals(tl)) return true;
        if (tl.contains(searchLower)) return true;
        if (searchWords.length == 0) return false;
        String[] targetWords = tl.split("\\s+");
        for (String word : searchWords) {
            boolean found = false;
            for (String tWord : targetWords) {
                if (tWord.contains(word) || word.contains(tWord)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean matchesText(String search, String target) {
        if (search.isEmpty() || target.isEmpty()) return false;
        if (search.equals(target)) return true;
        if (target.toLowerCase().contains(search.toLowerCase())) return true;

        String[] searchWords = search.toLowerCase().split("\\s+");
        String[] targetWords = target.toLowerCase().split("\\s+");

        for (String word : searchWords) {
            boolean found = false;
            for (String tWord : targetWords) {
                if (tWord.contains(word) || word.contains(tWord)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
