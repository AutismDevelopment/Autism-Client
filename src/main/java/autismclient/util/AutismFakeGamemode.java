package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

public final class AutismFakeGamemode {
    private static boolean fakeActive;
    private static boolean applyingFake;
    private static GameType serverKnownMode;
    private static GameType fakeMode;
    private static long realRevision;
    private static long fakeRevision;

    private AutismFakeGamemode() {
    }

    public static synchronized Result apply(GameType mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return Result.fail("Not connected.");
        }
        if (mode == null) {
            return Result.fail("Unknown game mode.");
        }
        if (!fakeActive) {
            serverKnownMode = mc.gameMode.getPlayerMode();
        }
        GameType previousDisplayed = displayedMode();
        fakeActive = true;
        fakeMode = mode;
        if (previousDisplayed != mode) fakeRevision++;
        applyLocal(mc, mode);
        return Result.ok("Fake gamemode: " + mode.getName());
    }

    public static synchronized Result reset() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            clear();
            return Result.fail("Not connected.");
        }
        if (!fakeActive) {
            serverKnownMode = mc.gameMode.getPlayerMode();
            return Result.ok("Fake gamemode already reset.");
        }
        GameType restore = serverKnownMode != null ? serverKnownMode : GameType.DEFAULT_MODE;
        GameType previousDisplayed = displayedMode();
        fakeActive = false;
        fakeMode = null;
        if (previousDisplayed != restore) fakeRevision++;
        applyLocal(mc, restore);
        return Result.ok("Fake gamemode reset: " + restore.getName());
    }

    public static synchronized void onVanillaLocalMode(GameType mode) {
        if (applyingFake || mode == null) return;
        if (serverKnownMode != mode) {
            serverKnownMode = mode;
            realRevision++;
        }
        if (fakeActive && fakeMode != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode() != fakeMode) {
                applyLocal(mc, fakeMode);
            }
        }
    }

    public static synchronized void clear() {
        fakeActive = false;
        applyingFake = false;
        serverKnownMode = null;
        fakeMode = null;
        realRevision++;
        fakeRevision++;
    }

    public static synchronized Snapshot snapshot() {
        Minecraft mc = Minecraft.getInstance();
        GameType current = mc != null && mc.gameMode != null ? mc.gameMode.getPlayerMode() : null;
        GameType real = serverKnownMode != null ? serverKnownMode : current;
        GameType displayed = fakeActive && fakeMode != null ? fakeMode : real;
        return new Snapshot(real, displayed, fakeActive, realRevision, fakeRevision);
    }

    public static GameType parseMode(String input) {
        if (input == null) return null;
        return switch (input.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "s", "0", "survival" -> GameType.SURVIVAL;
            case "c", "1", "creative" -> GameType.CREATIVE;
            case "a", "2", "adventure" -> GameType.ADVENTURE;
            case "sp", "spec", "3", "spectator" -> GameType.SPECTATOR;
            default -> null;
        };
    }

    public static String displayName(GameType mode) {
        if (mode == null) return "Reset";
        String name = mode.getName();
        return name.isEmpty() ? mode.name() : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void applyLocal(Minecraft mc, GameType mode) {
        applyingFake = true;
        try {
            mc.gameMode.setLocalMode(mode);
        } finally {
            applyingFake = false;
        }
    }

    private static GameType displayedMode() {
        if (fakeActive && fakeMode != null) return fakeMode;
        if (serverKnownMode != null) return serverKnownMode;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gameMode != null ? mc.gameMode.getPlayerMode() : null;
    }

    public record Snapshot(GameType realMode, GameType displayedMode, boolean fakeActive, long realRevision, long fakeRevision) {
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message == null ? "" : message);
        }

        public static Result fail(String message) {
            return new Result(false, message == null ? "" : message);
        }
    }
}
