package autismclient.util;

import autismclient.commands.AutismCommands;
import autismclient.mixin.accessor.AutismChatComponentAccessor;
import autismclient.mixin.accessor.AutismCommandHistoryAccessor;
import autismclient.modules.PackHideState;
import net.minecraft.client.CommandHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AutismClientMessaging {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final long DUPLICATE_WINDOW_MS = 600L;
    private static final String PREFIX_TEXT = "[AUTISM] ";

    private static String lastMessageKey = "";
    private static long lastMessageAtMs = 0L;
    private static String lastReplaceGroup = "";
    private static long lastReplaceAtMs = 0L;
    private static final long REPLACE_WINDOW_MS = 4500L;
    private static final Set<String> SENT_MESSAGE_KEYS = new LinkedHashSet<>();

    private AutismClientMessaging() {}

    public static void send(String message) {
        send(buildBodyText(message));
    }

    public static void sendPrefixed(String message) {
        MutableComponent text = Component.empty()
            .append(themedTag("AUTISM"))
            .append(buildBodyText(message));
        send(text);
    }

    public static MutableComponent themedTag(String tag) {
        return Component.empty()
            .append(styled("[", AutismColors.textMuted()))
            .append(styled(tag == null ? "" : tag, AutismColors.accent(), true))
            .append(styled("] ", AutismColors.textMuted()));
    }

    public static MutableComponent themedBody(String message) {
        return buildBodyText(message == null ? "" : message);
    }

    public static void send(Component text) {
        if (MC == null || text == null) return;
        if (PackHideState.shouldSuppressClientOutput()) return;

        String key = normalizeDuplicateKey(text.getString());
        long now = System.currentTimeMillis();
        synchronized (AutismClientMessaging.class) {
            if (!key.isEmpty() && key.equals(lastMessageKey) && now - lastMessageAtMs <= DUPLICATE_WINDOW_MS) {
                return;
            }
            lastMessageKey = key;
            lastMessageAtMs = now;
            if (!key.isEmpty()) SENT_MESSAGE_KEYS.add(key);
        }

        String replaceGroup = replacementGroupKey(text.getString());
        MC.execute(() -> {
            if (MC.player != null) {
                if (!replaceGroup.isEmpty()) removeReplaceableMessage(replaceGroup);
                MC.gui.hud.getChat().addClientSystemMessage(text);
            } else if (MC.gui.chatListener() != null) {
                MC.gui.chatListener().handleSystemMessage(text, false);
            }
        });
    }

    public static void clearClientMessages() {
        if (MC == null) return;
        MC.execute(() -> {
            if (MC.gui == null || MC.gui.hud.getChat() == null) return;
            try {
                AutismChatComponentAccessor chat = (AutismChatComponentAccessor) MC.gui.hud.getChat();
                List<GuiMessage> messages = chat.autism$getAllMessages();
                boolean changed = false;
                ListIterator<GuiMessage> iterator = messages.listIterator();
                while (iterator.hasNext()) {
                    GuiMessage existing = iterator.next();
                    if (isClientGeneratedMessage(existing.content().getString())) {
                        iterator.remove();
                        changed = true;
                    }
                }
                if (changed) chat.autism$refreshTrimmedMessages();
            } catch (Throwable ignored) {  }
        });
    }

    public static void rememberRecentChat(String line) {
        if (MC == null || line == null || line.isBlank()) return;
        try {
            if (MC.gui == null || MC.gui.hud.getChat() == null) return;
            ChatComponent chat = MC.gui.hud.getChat();
            chat.addRecentChat(line);
            if (AutismCommands.isAutismCommandMessage(line)) {
                CommandHistory history = ((AutismChatComponentAccessor) chat).autism$getCommandHistory();
                if (history != null) persistCommandHistoryAsync(history, line);
            }
        } catch (Throwable ignored) {  }
    }

    private static final Object HISTORY_LOCK = new Object();
    private static final int HISTORY_QUEUE_CAP = 64;
    private static final java.util.concurrent.ExecutorService HISTORY_WRITER =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Autism-CommandHistory-Writer");
            thread.setDaemon(true);
            return thread;
        });
    private static final java.util.ArrayDeque<String> pendingHistoryLines = new java.util.ArrayDeque<>();
    private static boolean historyDrainQueued;

    private static void persistCommandHistoryAsync(CommandHistory history, String line) {
        synchronized (HISTORY_LOCK) {
            if (pendingHistoryLines.size() >= HISTORY_QUEUE_CAP) pendingHistoryLines.pollFirst();
            pendingHistoryLines.addLast(line);
            if (historyDrainQueued) return;
            historyDrainQueued = true;
        }
        HISTORY_WRITER.execute(() -> {
            try {
                for (;;) {
                    String next;
                    synchronized (HISTORY_LOCK) {
                        next = pendingHistoryLines.pollFirst();
                        if (next == null) {
                            historyDrainQueued = false;
                            return;
                        }
                    }
                    try {
                        history.addCommand(next);
                    } catch (Throwable t) {
                        autismclient.AutismClientAddon.LOG.warn("[Chat] Failed to persist command history", t);
                    }
                }
            } catch (Throwable t) {
                synchronized (HISTORY_LOCK) { historyDrainQueued = false; }
                autismclient.AutismClientAddon.LOG.warn("[Chat] Command history writer failed", t);
            }
        });
    }

    public static void clearChatInputHistory() {
        if (MC == null) return;
        MC.execute(() -> {
            try {
                if (MC.gui == null || MC.gui.hud.getChat() == null) return;
                ChatComponent chat = MC.gui.hud.getChat();
                chat.getRecentChat().clear();
                CommandHistory history = ((AutismChatComponentAccessor) chat).autism$getCommandHistory();
                if (history == null) return;
                AutismCommandHistoryAccessor accessor = (AutismCommandHistoryAccessor) history;
                accessor.autism$getLastCommands().clear();
                accessor.autism$save();
            } catch (Throwable ignored) {  }
        });
    }

    private static boolean isClientGeneratedMessage(String message) {
        String clean = stripLegacyCodes(normalizeLegacyFormatting(message)).replaceAll("\\s+", " ").trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[autism]") || lower.startsWith("[lan]")) return true;
        String key = normalizeDuplicateKey(message);
        synchronized (AutismClientMessaging.class) {
            return !key.isEmpty() && SENT_MESSAGE_KEYS.contains(key);
        }
    }

    private static MutableComponent buildBodyText(String message) {
        String normalized = normalizeLegacyFormatting(message);
        MutableComponent lanChat = tryBuildLanChatText(normalized);
        if (lanChat != null) return lanChat;

        int defaultColor = inferDefaultColor(stripLegacyCodes(normalized));
        if (normalized.indexOf('\u00a7') >= 0) {
            return parseLegacyStyled(normalized, defaultColor);
        }

        return colorizeStructuredText(normalized, defaultColor);
    }

    private static MutableComponent tryBuildLanChatText(String message) {
        if (message == null || !message.startsWith("[LAN] <")) return null;

        int close = message.indexOf("> ");
        if (close <= 7) return null;

        String sender = message.substring(7, close);
        String body = message.substring(close + 2);
        return Component.empty()
            .append(styled("[", AutismColors.textMuted()))
            .append(styled("LAN", AutismColors.packetCyan(), true))
            .append(styled("] ", AutismColors.textMuted()))
            .append(styled("<", AutismColors.textMuted()))
            .append(styled(sender, AutismColors.packetPink(), true))
            .append(styled("> ", AutismColors.textMuted()))
            .append(colorizeTokenStream(body, AutismColors.textPrimary()));
    }

    private static MutableComponent colorizeStructuredText(String message, int defaultColor) {
        if (message == null || message.isEmpty()) return Component.empty();

        int colon = message.indexOf(':');
        if (colon > 0 && colon < 24 && isLikelyLabel(message.substring(0, colon))) {
            String label = message.substring(0, colon);
            String rest = message.substring(colon + 1);
            MutableComponent root = Component.empty()
                .append(styled(label, AutismColors.packetGray(), true))
                .append(styled(":", AutismColors.textMuted()));

            if (!rest.isEmpty()) {
                root.append(styled(leadingWhitespace(rest), AutismColors.textMuted()));
                root.append(colorizeTokenStream(rest.stripLeading(), inferDefaultColor(rest.trim())));
            }
            return root;
        }

        return colorizeTokenStream(message, defaultColor);
    }

    private static MutableComponent colorizeTokenStream(String message, int defaultColor) {
        MutableComponent root = Component.empty();
        if (message == null || message.isEmpty()) return root;

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '#' || ch == '.' || ch == '/') {
                token.append(ch);
                continue;
            }

            if (token.length() > 0) {
                String word = token.toString();
                root.append(styled(word, colorForToken(word, defaultColor), isEmphasizedToken(word)));
                token.setLength(0);
            }

            root.append(styled(String.valueOf(ch), punctuationColor(ch, defaultColor)));
        }

        if (token.length() > 0) {
            String word = token.toString();
            root.append(styled(word, colorForToken(word, defaultColor), isEmphasizedToken(word)));
        }

        return root;
    }

    private static boolean isLikelyLabel(String value) {
        if (value == null || value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || ch == '[' || ch == ']' || ch == '+' || ch == '-' || ch == '/' || ch == '#')) {
                return false;
            }
        }
        return true;
    }

    private static String leadingWhitespace(String value) {
        if (value == null || value.isEmpty()) return "";
        int i = 0;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        return value.substring(0, i);
    }

    private static int punctuationColor(char ch, int defaultColor) {
        return switch (ch) {
            case '[', ']', '(', ')', '<', '>', ':', '|', ',' -> AutismColors.textMuted();
            case '"' -> AutismColors.packetLightYellow();
            default -> defaultColor;
        };
    }

    private static boolean isEmphasizedToken(String token) {
        String normalized = normalizeToken(token);
        return normalized.equals("enabled")
            || normalized.equals("disabled")
            || normalized.equals("started")
            || normalized.equals("stopped")
            || normalized.equals("failed")
            || normalized.equals("error")
            || normalized.equals("queue")
            || normalized.equals("packet")
            || normalized.equals("packets")
            || normalized.equals("gui")
            || normalized.equals("macro")
            || normalized.equals("macros")
            || normalized.equals("sync")
            || normalized.equals("lan");
    }

    private static int colorForToken(String token, int defaultColor) {
        String normalized = normalizeToken(token);
        if (normalized.isEmpty()) return defaultColor;

        if (isNumericToken(normalized)) return AutismColors.packetLightYellow();

        if (normalized.equals("enabled")
            || normalized.equals("started")
            || normalized.equals("joined")
            || normalized.equals("restored")
            || normalized.equals("saved")
            || normalized.equals("loaded")
            || normalized.equals("copied")
            || normalized.equals("imported")
            || normalized.equals("received")
            || normalized.equals("executed")
            || normalized.equals("finished")
            || normalized.equals("reconnected")
            || normalized.equals("host")
            || normalized.equals("sent")
            || normalized.equals("on")
            || normalized.equals("true")) {
            return AutismColors.successText();
        }

        if (normalized.equals("disabled")
            || normalized.equals("stopped")
            || normalized.equals("empty")
            || normalized.equals("already")
            || normalized.equals("requested")
            || normalized.equals("left")
            || normalized.equals("attempting")
            || normalized.equals("timeout")
            || normalized.equals("timed")
            || normalized.equals("out")
            || normalized.equals("off")
            || normalized.equals("false")) {
            return AutismColors.packetYellow();
        }

        if (normalized.equals("failed")
            || normalized.equals("error")
            || normalized.equals("errors")
            || normalized.equals("denied")
            || normalized.equals("blocked")
            || normalized.equals("crashed")
            || normalized.equals("cannot")
            || normalized.equals("missing")
            || normalized.equals("unavailable")
            || normalized.equals("invalid")
            || normalized.equals("not")
            || normalized.equals("null")) {
            return AutismColors.dangerText();
        }

        if (normalized.equals("queue")
            || normalized.equals("packet")
            || normalized.equals("packets")
            || normalized.equals("sync")
            || normalized.equals("lan")
            || normalized.equals("session")
            || normalized.equals("tcp")) {
            return AutismColors.packetCyan();
        }

        if (normalized.equals("gui")
            || normalized.equals("screen")
            || normalized.equals("inventory")
            || normalized.equals("slot")
            || normalized.equals("slots")) {
            return AutismColors.packetOrange();
        }

        if (normalized.equals("macro")
            || normalized.equals("macros")
            || normalized.equals("preset")
            || normalized.equals("presets")) {
            return AutismColors.packetPink();
        }

        return defaultColor;
    }

    private static boolean isNumericToken(String token) {
        int start = token.startsWith("-") ? 1 : 0;
        if (start >= token.length()) return false;
        for (int i = start; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (!Character.isDigit(ch) && ch != '.' && ch != '/' && ch != '#') return false;
        }
        return true;
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isEmpty()) return "";
        int start = 0;
        int end = token.length();
        while (start < end && !Character.isLetterOrDigit(token.charAt(start))) start++;
        while (end > start && !Character.isLetterOrDigit(token.charAt(end - 1))) end--;
        if (start >= end) return "";
        return token.substring(start, end).toLowerCase();
    }

    private static MutableComponent parseLegacyStyled(String message, int defaultColor) {
        MutableComponent root = Component.empty();
        if (message == null || message.isEmpty()) return root;

        StringBuilder buffer = new StringBuilder();
        int currentColor = defaultColor;
        boolean bold = false;

        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            if (ch == '\u00a7' && i + 1 < message.length()) {
                if (buffer.length() > 0) {
                    root.append(styled(buffer.toString(), currentColor, bold));
                    buffer.setLength(0);
                }

                char code = Character.toLowerCase(message.charAt(++i));
                Integer mapped = mapLegacyColor(code);
                if (mapped != null) {
                    currentColor = mapped;
                    if (isColorCode(code) || code == 'r') bold = false;
                    continue;
                }

                if (code == 'l') {
                    bold = true;
                } else if (code == 'r') {
                    currentColor = defaultColor;
                    bold = false;
                }
                continue;
            }
            buffer.append(ch);
        }

        if (buffer.length() > 0) {
            root.append(styled(buffer.toString(), currentColor, bold));
        }

        return root;
    }

    private static MutableComponent styled(String value, int color) {
        return styled(value, color, false);
    }

    private static MutableComponent styled(String value, int color, boolean bold) {
        Style style = Style.EMPTY.withColor(color);
        if (bold) style = style.withBold(true);
        return Component.literal(value).setStyle(style);
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static Integer mapLegacyColor(char code) {
        return switch (code) {
            case '0' -> 0xFF5E4B4E;
            case '1' -> 0xFF6B92D8;
            case '2' -> 0xFF59B974;
            case '3' -> AutismColors.packetCyan();
            case '4' -> AutismColors.dangerText();
            case '5' -> 0xFFE78ACB;
            case '6' -> AutismColors.packetOrange();
            case '7' -> AutismColors.packetGray();
            case '8' -> AutismColors.textMuted();
            case '9' -> AutismColors.packetBlue();
            case 'a' -> AutismColors.successText();
            case 'b' -> AutismColors.packetCyan();
            case 'c' -> AutismColors.dangerText();
            case 'd' -> AutismColors.packetPink();
            case 'e' -> AutismColors.packetYellow();
            case 'f' -> AutismColors.textSecondary();
            case 'r' -> null;
            default -> null;
        };
    }

    private static String normalizeLegacyFormatting(String message) {
        if (message == null) return "";
        return message.replace("\u00c2\u00a7", "\u00a7");
    }

    private static String stripLegacyCodes(String message) {
        if (message == null || message.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            if (ch == '\u00a7' && i + 1 < message.length()) {
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString().trim();
    }

    private static int inferDefaultColor(String message) {
        String lower = message == null ? "" : message.trim().toLowerCase();
        if (lower.isEmpty()) return AutismColors.textSecondary();

        if (lower.contains("failed")
            || lower.contains("error")
            || lower.startsWith("no ")
            || lower.startsWith("not ")
            || lower.contains(" not ")
            || lower.contains("cannot")
            || lower.contains("denied")
            || lower.contains("blocked")
            || lower.contains("crashed")) {
            return AutismColors.dangerText();
        }

        if (lower.contains("enabled")
            || lower.contains("started")
            || lower.contains("joined")
            || lower.contains("restored")
            || lower.contains("saved")
            || lower.contains("loaded")
            || lower.contains("copied")
            || lower.contains("imported")
            || lower.contains("received")
            || lower.contains("executed")
            || lower.contains("finished")
            || lower.startsWith("sent ")
            || lower.startsWith("signed ")
            || lower.startsWith("deleted ")
            || lower.startsWith("broadcasted ")
            || lower.startsWith("queue sent")) {
            return AutismColors.successText();
        }

        if (lower.contains("disabled")
            || lower.contains("stopped")
            || lower.contains("empty")
            || lower.contains("already")
            || lower.contains("requested")
            || lower.contains("left")
            || lower.contains("attempting")
            || lower.contains("timed out")) {
            return AutismColors.packetYellow();
        }

        if (lower.startsWith("[lan] <")) {
            return AutismColors.textPrimary();
        }

        return AutismColors.textSecondary();
    }

    private static String normalizeDuplicateKey(String message) {
        if (message == null) return "";
        return stripLegacyCodes(normalizeLegacyFormatting(message))
            .replace(PREFIX_TEXT, "")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase();
    }

    private static String replacementGroupKey(String message) {
        String clean = stripAutismPrefix(stripLegacyCodes(normalizeLegacyFormatting(message)).replaceAll("\\s+", " ").trim());
        if (clean.isBlank()) return "";
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[lan]")) return "";
        if (isErrorLike(lower)) return "";

        int colon = clean.indexOf(':');
        if (colon > 0 && colon <= 38) {
            String label = clean.substring(0, colon).trim();
            if (isReplaceableLabel(label)) return "label:" + label.toLowerCase(Locale.ROOT);
        }

        if (lower.startsWith("macro stopped")) return "macro:stopped";
        if (lower.startsWith("macro finished")) return "macro:finished";
        if (lower.startsWith("tick sync:")) return "macro:tick-sync";
        if (lower.startsWith("rev sync:")) return "macro:rev-sync";
        if (lower.startsWith("serversync:")) return "macro:server-sync";
        return "";
    }

    private static void removeReplaceableMessage(String group) {
        long now = System.currentTimeMillis();
        synchronized (AutismClientMessaging.class) {
            if (!group.equals(lastReplaceGroup) || now - lastReplaceAtMs > REPLACE_WINDOW_MS) {
                lastReplaceGroup = group;
                lastReplaceAtMs = now;
                return;
            }
            lastReplaceAtMs = now;
        }

        try {
            AutismChatComponentAccessor chat = (AutismChatComponentAccessor) MC.gui.hud.getChat();
            List<GuiMessage> messages = chat.autism$getAllMessages();
            ListIterator<GuiMessage> iterator = messages.listIterator();
            while (iterator.hasNext()) {
                GuiMessage existing = iterator.next();
                if (group.equals(replacementGroupKey(existing.content().getString()))) {
                    iterator.remove();
                    chat.autism$refreshTrimmedMessages();
                    return;
                }
            }
        } catch (Throwable ignored) {  }
    }

    private static String stripAutismPrefix(String message) {
        if (message == null) return "";
        String trimmed = message.trim();
        if (trimmed.startsWith(PREFIX_TEXT)) return trimmed.substring(PREFIX_TEXT.length()).trim();
        if (trimmed.startsWith("[AUTISM]")) return trimmed.substring("[AUTISM]".length()).trim();
        return trimmed;
    }

    private static boolean isReplaceableLabel(String label) {
        if (label == null || label.isBlank()) return false;
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("error") || normalized.contains("invalid") || normalized.contains("missing")) return false;
        if (normalized.equals("saved packet canceller preset") || normalized.equals("loaded packet canceller preset")) return false;
        return isLikelyLabel(label);
    }

    private static boolean isErrorLike(String lower) {
        return lower.startsWith("failed")
            || lower.startsWith("error")
            || lower.startsWith("invalid")
            || lower.startsWith("missing")
            || lower.startsWith("cannot")
            || lower.contains(" crashed:");
    }
}
