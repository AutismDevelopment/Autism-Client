package autismclient.util.multi;

import net.minecraft.world.inventory.ContainerInput;

import java.util.Locale;
import java.util.Set;

public final class MultiClientCommands {
    private MultiClientCommands() {
    }

    public record ClickSpec(ContainerInput input, int button) {
    }

    private static final Set<String> GUI = Set.of("matchmaking", "mm", "nbt", "server", "plugins", "multi");

    private static final Set<String> LOCAL = Set.of(
        "toggle", "t", "delay", "gamemode", "bind", "binds", "prefix", "irc", "macro",
        "modules", "features", "commands", "cmds", "help", "dismount", "give", "xcarry");

    private static final Set<String> SUPPORTED = Set.of(
        "click-slot", "click-item", "change-slot", "drop", "close", "use", "swing", "send", "say", "damage",
        "vclip", "hclip");

    public static String denyReason(String name) {
        if (name == null || name.isBlank()) return "empty command";
        String key = name.toLowerCase(Locale.ROOT);
        if (SUPPORTED.contains(key)) return null;
        if (GUI.contains(key)) return "opens a GUI - not available in the headless console";
        if (LOCAL.contains(key)) return "local-only - not possible on a headless bot";
        return "unknown or unsupported client command";
    }

    public static String batchDenyReason(String name, String args) {
        String deny = denyReason(name);
        if (deny != null) return deny;
        String key = name.toLowerCase(Locale.ROOT);
        if (!key.equals("vclip") && !key.equals("hclip")) return null;
        ClipRequest request = parseClip(key, args);
        return request.spec() == null ? request.error() : null;
    }

    public record ClipSpec(double blocks, int segments, boolean forceGround) {
    }

    public record ClipRequest(ClipSpec spec, String error) {
        static ClipRequest of(ClipSpec spec) { return new ClipRequest(spec, null); }
        static ClipRequest error(String message) { return new ClipRequest(null, message); }
    }

    public static ClipRequest parseClip(String command, String args) {
        String name = command == null ? "clip" : command.toLowerCase(Locale.ROOT);
        boolean vertical = name.equals("vclip");
        String[] parts = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        if (parts.length == 0) return ClipRequest.error("Usage: " + name + " <blocks>");

        String mode = parts[0].toLowerCase(Locale.ROOT);
        if (isScanMode(mode)) {
            return ClipRequest.error(name + " " + mode + " needs client world collision data - "
                + "headless bots only take exact distances");
        }

        int offset = 0;
        boolean segmented = true;
        boolean custom = false;
        switch (mode) {
            case "single", "normal" -> { segmented = false; offset = 1; }
            case "default", "paper", "padding" -> offset = 1;
            case "custom" -> { custom = true; offset = 1; }
            default -> { }
        }
        if (offset >= parts.length) return ClipRequest.error("Usage: " + name + " <blocks>");
        Double blocks = finiteDouble(parts[offset]);
        if (blocks == null) return ClipRequest.error("Bad " + name + " distance: " + parts[offset]);

        int segment = custom && offset + 1 < parts.length ? boundedInt(parts[offset + 1], 1, 50, 10) : 10;
        int maxPackets = custom && offset + 2 < parts.length ? boundedInt(parts[offset + 2], 1, 100, 20) : 20;

        boolean forceGround = custom && offset + 5 < parts.length
            ? Boolean.parseBoolean(parts[offset + 5]) : vertical;

        int segments = segmented ? Math.max(1, (int) Math.ceil(Math.abs(blocks) / segment)) : 1;
        if (segments > maxPackets) segments = 1;
        return ClipRequest.of(new ClipSpec(blocks, segments, forceGround));
    }

    public static boolean isScanMode(String mode) {
        if (mode == null) return false;
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "top", "bottom", "forward", "back" -> true;
            default -> false;
        };
    }

    private static Double finiteDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int boundedInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static ClickSpec parseClick(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "", "left", "pickup" -> new ClickSpec(ContainerInput.PICKUP, 0);
            case "right" -> new ClickSpec(ContainerInput.PICKUP, 1);
            case "middle" -> new ClickSpec(ContainerInput.PICKUP, 2);
            case "clone" -> new ClickSpec(ContainerInput.CLONE, 2);
            case "shift", "shift-left", "quick-move" -> new ClickSpec(ContainerInput.QUICK_MOVE, 0);
            case "shift-right" -> new ClickSpec(ContainerInput.QUICK_MOVE, 1);
            case "drop", "drop-item", "throw" -> new ClickSpec(ContainerInput.THROW, 0);
            case "drop-stack", "throw-all" -> new ClickSpec(ContainerInput.THROW, 1);
            default -> {
                if (m.startsWith("swap")) {
                    try {
                        int n = Integer.parseInt(m.substring(4).trim());
                        if (n >= 1 && n <= 9) yield new ClickSpec(ContainerInput.SWAP, n - 1);
                    } catch (RuntimeException ignored) {

                    }
                }
                yield null;
            }
        };
    }

    public static ClickSpec fromMouse(int button, boolean shift, boolean ctrl) {
        if (button == 2) return new ClickSpec(ContainerInput.CLONE, 2);
        int mouseButton = button == 1 ? 1 : 0;
        return new ClickSpec(shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, mouseButton);
    }

    public static ClickSpec dropSpec(boolean wholeStack) {
        return new ClickSpec(ContainerInput.THROW, wholeStack ? 1 : 0);
    }
}
