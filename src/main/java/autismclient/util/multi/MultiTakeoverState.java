package autismclient.util.multi;

import autismclient.AutismClientAddon;
import autismclient.modules.PackFreecamState;
import autismclient.util.AutismNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MultiTakeoverState {
    private enum Phase { IDLE, ACTIVE }

    private static volatile Phase phase = Phase.IDLE;
    private static volatile String targetId;

    private static final ConcurrentHashMap<String, long[]> availableCache = new ConcurrentHashMap<>();
    private static final long AVAILABLE_TTL_MS = 400;

    private MultiTakeoverState() {
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static boolean isActive(String accountId) {
        return accountId != null && accountId.equals(targetId);
    }

    public static String activeAccountId() {
        return targetId;
    }

    public static boolean available(String accountId) {
        if (accountId == null) return false;
        if (accountId.equals(targetId)) return true;
        if (phase != Phase.IDLE) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return false;
        MultiManager mgr = MultiManager.getIfInitialized();
        if (mgr == null || !mgr.isActive()) return false;
        MultiSession session = mgr.session(accountId);
        if (session == null || !session.ready()) return false;
        long now = System.currentTimeMillis();
        long[] cached = availableCache.get(accountId);
        if (cached != null && now < cached[0]) return cached[1] != 0;
        boolean ok = findBotEntity(mc, session) != null;
        availableCache.put(accountId, new long[]{now + AVAILABLE_TTL_MS, ok ? 1 : 0});
        return ok;
    }

    public static void toggle(String accountId, Screen screen) {
        if (accountId == null) return;
        if (accountId.equals(targetId)) {
            exit();
            return;
        }
        if (phase != Phase.IDLE) {
            note("Leave the current bot first.");
            return;
        }
        enter(accountId);
    }

    private static void enter(String accountId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            note("Join a world near the bot first.");
            return;
        }
        if (PackFreecamState.isActive()) {
            note("Disable freecam first.");
            return;
        }
        MultiManager mgr = MultiManager.getIfInitialized();
        if (mgr == null || !mgr.isActive()) {
            note("No bot batch is running.");
            return;
        }
        MultiSession session = mgr.session(accountId);
        if (session == null || !session.ready()) {
            note("That bot isn't ready.");
            return;
        }
        if (session.takeoverHealth() <= 0.0F) {
            note("That bot is dead - wait for its respawn.");
            return;
        }
        RemotePlayer bot = findBotEntity(mc, session);
        if (bot == null) {
            note("That bot isn't near you (must be visible in your world).");
            return;
        }
        try {
            session.setPiloted(true);
            MultiPilot.begin(session, bot);
            MultiPilot.neutralizeMainPlayer(mc);
            mc.setCameraEntity(bot);

            mc.gui.setScreen((Screen) null);

            autismclient.util.AutismOverlayManager.get().clearTextFieldFocus();
            targetId = accountId;
            phase = Phase.ACTIVE;
            note("You are now " + label(accountId) + ".");
        } catch (Throwable error) {
            AutismClientAddon.LOG.error("POV pilot enter failed", error);
            note("Could not become that bot: " + error.getClass().getSimpleName());
            try {
                MultiPilot.end(bot);
            } catch (Throwable ignored) {

            }
            session.setPiloted(false);
            if (mc.player != null) mc.setCameraEntity(mc.player);
            clearState();
        }
    }

    public static void exit() {
        if (phase == Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();

        try {
            Screen current = mc.gui.screen();
            if (current instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
                || current instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen
                || current instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen) {
                mc.gui.setScreen((Screen) null);
            }
        } catch (Throwable ignored) {

        }
        MultiManager mgr = MultiManager.getIfInitialized();
        MultiSession session = mgr == null ? null : mgr.session(targetId);
        RemotePlayer bot = session == null ? null : findBotEntity(mc, session);
        boolean macroAuthority = session != null && session.macroOwnsPilot();

        try {
            MultiPilot.end(bot);
        } catch (Throwable error) {
            AutismClientAddon.LOG.error("POV pilot end failed", error);
        }
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (session != null) {
            try {

                if (!macroAuthority && session.ready()) session.resumeAfterPilot();
            } catch (Throwable error) {
                AutismClientAddon.LOG.warn("POV exit position sync failed", error);
            }
            session.setPiloted(false);
        }
        restoreMultiUi(mc);
        clearState();
        autismclient.util.AutismInventoryMoveHelper.resyncMovementKeysAfterPov();
    }

    public static void tick() {
        if (phase == Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();

        if (!PackFreecamState.isActive()
            && (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
            mc.gui.setScreen((Screen) null);
        }
        MultiManager mgr = MultiManager.getIfInitialized();
        MultiSession session = mgr == null ? null : mgr.session(targetId);
        if (session == null || mc.level == null || mc.player == null) {
            exit();
            return;
        }
        if (!session.ready()) {
            MultiSession.Status status = session.statusValue();
            boolean switching = status == MultiSession.Status.CONFIGURING
                || status == MultiSession.Status.CONNECTING
                || status == MultiSession.Status.LOGIN
                || status == MultiSession.Status.JOINED;

            exit();
            if (switching) note("Bot switched servers - session continues headless.");
            return;
        }
        if (session.takeoverHealth() <= 0.0F) {

            exit();
            note("Bot died - POV ended.");
            return;
        }
        RemotePlayer bot = findBotEntity(mc, session);
        if (bot == null || bot.isRemoved() || bot.isPassenger()) {
            exit();
            if (bot == null || bot.isRemoved()) note("Bot changed world - session stays connected.");
            return;
        }
        if (mc.getCameraEntity() != bot) {
            exit();
        }
    }

    private static RemotePlayer findBotEntity(Minecraft mc, MultiSession session) {
        if (mc.level == null) return null;
        try {
            UUID uuid = session.serverUuid();
            if (uuid != null && mc.level.getPlayerByUUID(uuid) instanceof RemotePlayer byUuid) return byUuid;
            String name = session.takeoverProfile() == null ? null : session.takeoverProfile().name();
            if (name != null && !name.isBlank()) {
                for (Player player : mc.level.players()) {
                    if (player != mc.player && player instanceof RemotePlayer remote
                        && player.getName().getString().equalsIgnoreCase(name)) {
                        return remote;
                    }
                }
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static void restoreMultiUi(Minecraft mc) {
        try {
            if (mc.gui.screen() instanceof autismclient.gui.screen.AutismOverlayHostScreen) {
                mc.gui.setScreen((Screen) null);
            }
        } catch (Throwable ignored) {

        }
    }

    private static void clearState() {
        phase = Phase.IDLE;
        targetId = null;
        availableCache.clear();
    }

    private static String label(String accountId) {
        MultiManager mgr = MultiManager.getIfInitialized();
        if (mgr != null) {
            for (MultiSession.Snapshot s : mgr.snapshots()) {
                if (s.accountId().equals(accountId)) return s.accountName();
            }
        }
        return accountId == null ? "bot" : accountId;
    }

    private static void note(String message) {
        try {
            AutismNotifications.show(message, 0xFFFFC857);
        } catch (Throwable ignored) {

        }
    }
}
