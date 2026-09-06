package autismclient.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AntiVanishText {
    private AntiVanishText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String decomposed = Normalizer.normalize(stripLegacyCodes(text), Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean spaced = true;
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isMark(codePoint) || isFormatCode(codePoint)) continue;
            int folded = foldConfusable(codePoint);
            if (folded >= 'a' && folded <= 'z' || folded >= '0' && folded <= '9') {
                out.appendCodePoint(folded);
                spaced = false;
            } else if (!spaced) {
                out.append(' ');
                spaced = true;
            }
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') out.setLength(length - 1);
        return out.toString();
    }

    public static String firstMatch(String text, String playerName, List<String> keywords, String mode) {
        String normalized = normalize(text);
        String normalizedName = normalize(playerName);
        if (!normalizedName.isBlank()) normalized = removePhrase(normalized, normalizedName);

        String matchMode = mode == null ? "Contains" : mode;
        for (String raw : keywords == null ? List.<String>of() : keywords) {
            String keyword = normalize(raw);
            if (keyword.isBlank()) {
                String literal = stripLegacyCodes(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
                if (!literal.isBlank() && stripLegacyCodes(text).toLowerCase(Locale.ROOT).contains(literal)) return raw.trim();
                continue;
            }
            boolean matches = switch (matchMode) {
                case "Exact" -> normalized.equals(keyword);
                case "Word" -> containsPhrase(normalized, keyword);
                default -> normalized.replace(" ", "").contains(keyword.replace(" ", ""))
                    || normalized.contains(keyword);
            };
            if (matches) return raw == null ? keyword : raw.trim();
        }
        return "";
    }

    public static List<String> normalizedKeywords(List<String> keywords) {
        List<String> out = new ArrayList<>();
        if (keywords == null) return out;
        for (String keyword : keywords) {
            String normalized = normalize(keyword);
            if (!normalized.isBlank() && !out.contains(normalized)) out.add(normalized);
        }
        return out;
    }

    public static boolean containsPlayerName(String displayedName, String playerName) {
        String rawDisplay = stripLegacyCodes(displayedName == null ? "" : displayedName).toLowerCase(Locale.ROOT);
        String rawPlayer = stripLegacyCodes(playerName == null ? "" : playerName).trim().toLowerCase(Locale.ROOT);
        if (rawPlayer.matches("[a-z0-9_]{1,16}")) {
            int from = 0;
            while (from <= rawDisplay.length() - rawPlayer.length()) {
                int match = rawDisplay.indexOf(rawPlayer, from);
                if (match < 0) return false;
                int end = match + rawPlayer.length();
                boolean leftBoundary = match == 0 || !isUsernameCharacter(rawDisplay.charAt(match - 1));
                boolean rightBoundary = end == rawDisplay.length() || !isUsernameCharacter(rawDisplay.charAt(end));
                if (leftBoundary && rightBoundary) return true;
                from = match + 1;
            }
            return false;
        }
        String displayed = normalize(displayedName);
        String player = normalize(playerName);
        if (displayed.isBlank() || player.isBlank()) return false;
        return displayed.equals(player) || (" " + displayed + " ").contains(" " + player + " ");
    }

    public static boolean looksLikeLeaveMessage(String message, String playerName) {
        if (!containsPlayerName(message, playerName)) return false;
        String text = " " + normalize(message) + " ";
        String player = normalize(playerName);
        if (player.isBlank()) return false;
        String prefix = " " + player + " ";
        return text.contains(prefix + "left the game ")
            || text.contains(prefix + "left ")
            || text.contains(prefix + "has left ")
            || text.contains(prefix + "quit ")
            || text.contains(prefix + "has quit ")
            || text.contains(prefix + "disconnected ")
            || text.contains(prefix + "logged out ");
    }

    public static boolean isPlausiblePlayerName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        int len = trimmed.length();
        if (len < 3 || len > 16) return false;
        return trimmed.indexOf(' ') < 0;
    }

    private static boolean isUsernameCharacter(char character) {
        return character >= 'a' && character <= 'z'
            || character >= '0' && character <= '9'
            || character == '_';
    }

    private static boolean containsPhrase(String text, String phrase) {
        if (text.equals(phrase)) return true;
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static String removePhrase(String text, String phrase) {
        String padded = " " + text + " ";
        String needle = " " + phrase + " ";
        return padded.replace(needle, " ").trim().replaceAll("\\s+", " ");
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
            || type == Character.COMBINING_SPACING_MARK
            || type == Character.ENCLOSING_MARK;
    }

    private static boolean isFormatCode(int codePoint) {
        return codePoint == '\u00A7' || codePoint == '\u200B' || codePoint == '\u200C'
            || codePoint == '\u200D' || codePoint == '\uFEFF';
    }

    private static String stripLegacyCodes(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int foldConfusable(int codePoint) {
        return switch (codePoint) {

            case 'ᴀ', 'ɑ', 'α', 'а' -> 'a';
            case 'ʙ', 'β', 'в' -> 'b';
            case 'ᴄ', 'ϲ', 'с' -> 'c';
            case 'ᴅ', 'ԁ' -> 'd';
            case 'ᴇ', 'ε', 'е' -> 'e';
            case 'ꜰ' -> 'f';
            case 'ɢ', 'ɡ' -> 'g';
            case 'ʜ', 'н' -> 'h';
            case 'ɪ', 'ι', 'і' -> 'i';
            case 'ᴊ', 'ј' -> 'j';
            case 'ᴋ', 'κ', 'к' -> 'k';
            case 'ʟ' -> 'l';
            case 'ᴍ', 'м' -> 'm';
            case 'ɴ', 'ո' -> 'n';
            case 'ᴏ', 'ο', 'о' -> 'o';
            case 'ᴘ', 'ρ', 'р' -> 'p';
            case 'ԛ' -> 'q';
            case 'ʀ' -> 'r';
            case 'ꜱ', 'ѕ' -> 's';
            case 'ᴛ', 'τ', 'т' -> 't';
            case 'ᴜ', 'υ' -> 'u';
            case 'ᴠ', 'ν' -> 'v';
            case 'ᴡ', 'ѡ' -> 'w';
            case 'х' -> 'x';
            case 'ʏ', 'у' -> 'y';
            case 'ᴢ', 'ᴣ' -> 'z';
            default -> codePoint;
        };
    }
}
