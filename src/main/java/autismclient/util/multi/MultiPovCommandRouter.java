package autismclient.util.multi;

import autismclient.util.AutismClientMessaging;

import java.util.Locale;
import java.util.Set;

public final class MultiPovCommandRouter {
    private static final Set<String> SESSION_COMMANDS = Set.of(
        "click-slot", "click-item", "change-slot", "drop", "send", "say", "damage",
        "hclip", "vclip", "tp"
    );
    private static final Set<String> BLOCKED_MAIN_COMMANDS = Set.of(
        "delay", "gamemode", "server", "plugins", "matchmaking", "mm",
        "dismount"
    );

    private MultiPovCommandRouter() {}

    public static boolean route(String body) {
        MultiSession session = MultiPilot.activeCommandSession();
        if (session == null) return false;
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) return false;
        int split = trimmed.indexOf(' ');
        String name = (split < 0 ? trimmed : trimmed.substring(0, split)).toLowerCase(Locale.ROOT);
        String args = split < 0 ? "" : trimmed.substring(split + 1).trim();

        if (routesToSession(name)) {
            String result;
            try {
                result = switch (name) {
                    case "hclip" -> MultiPilot.runPovHClip(args);
                    case "vclip" -> MultiPilot.runPovVClip(args);
                    case "tp" -> MultiPilot.runPovTp(args);
                    default -> session.runClientAction(name, args);
                };
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            report(session, name, result);
            return true;
        }
        if ("disconnect".equals(name)) {
            String account = session.accountId();
            session.disconnect("Disconnected via POV command");
            AutismClientMessaging.sendPrefixed("§aDisconnected POV bot §f" + account + "§a.");
            return true;
        }
        if (blocksMainFallback(name)) {
            AutismClientMessaging.sendPrefixed(
                "§c" + name + " cannot safely run on this POV bot yet; the main account was left untouched.");
            return true;
        }
        return false;
    }

    static boolean routesToSession(String name) {
        return name != null && SESSION_COMMANDS.contains(name.toLowerCase(Locale.ROOT));
    }

    static boolean blocksMainFallback(String name) {
        return name != null && BLOCKED_MAIN_COMMANDS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static void report(MultiSession session, String command, String result) {
        String detail = result == null || result.isBlank() ? "No result" : result;
        String color = "Sent".equals(detail) || detail.startsWith("Queued ")
            || detail.startsWith("TP started") || detail.startsWith("TP defaults")
            ? "§a" : detail.contains("Nothing") || detail.startsWith("No active") || detail.startsWith("Usage")
            ? "§e" : "§c";
        AutismClientMessaging.sendPrefixed(color + "POV §f" + session.accountId() + color
            + " · " + command + ": §f" + detail);
    }
}
