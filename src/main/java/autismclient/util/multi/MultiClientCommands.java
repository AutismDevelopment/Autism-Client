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
        "modules", "features", "commands", "cmds", "help", "dismount", "vclip", "hclip", "give", "xcarry");

    private static final Set<String> SUPPORTED = Set.of(
        "click-slot", "click-item", "change-slot", "drop", "close", "use", "swing", "send", "say", "damage");

    public static String denyReason(String name) {
        if (name == null || name.isBlank()) return "empty command";
        String key = name.toLowerCase(Locale.ROOT);
        if (SUPPORTED.contains(key)) return null;
        if (GUI.contains(key)) return "opens a GUI - not available in the headless console";
        if (LOCAL.contains(key)) return "local-only - not possible on a headless bot";
        return "unknown or unsupported client command";
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
