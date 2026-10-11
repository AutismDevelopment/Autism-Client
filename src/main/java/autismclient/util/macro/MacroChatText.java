package autismclient.util.macro;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class MacroChatText {
    private MacroChatText() {
    }

    private static final Gson GSON = new Gson();
    public static String sanitizeChatText(String text) {
        if (text == null) return "";
        String stripped = stripLegacyFormatting(text).replace('\n', ' ').replace('\r', ' ');
        StringBuilder out = new StringBuilder(stripped.length());
        for (int offset = 0; offset < stripped.length();) {
            int cp = stripped.codePointAt(offset);
            offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.FORMAT
                    || type == Character.CONTROL
                    || cp == 0x200B
                    || cp == 0x200C
                    || cp == 0x200D
                    || cp == 0x2060
                    || cp == 0xFE0E
                    || cp == 0xFE0F) {
                continue;
            }
            out.appendCodePoint(cp);
        }
        return out.toString().trim();
    }

    public static String normalizeManualText(String text) {
        if (text == null || text.isBlank()) return "";
        String stripped = stripLegacyFormatting(text).trim();
        String flattened = flattenManualTextComponentJson(stripped);
        return sanitizeChatText(flattened != null ? flattened : stripped);
    }

    private static String flattenManualTextComponentJson(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\""))) return null;
        try {
            com.google.gson.JsonElement element = JsonParser.parseString(trimmed);
            if (element == null || element.isJsonNull()) return "";
            if (element.isJsonArray()) {
                StringBuilder out = new StringBuilder();
                for (com.google.gson.JsonElement child : element.getAsJsonArray()) {
                    Component component = parseTextComponentElement(child);
                    if (component != null) out.append(component.getString());
                }
                return out.toString();
            }
            Component component = parseTextComponentElement(element);
            return component == null ? null : component.getString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Component parseTextComponentElement(com.google.gson.JsonElement element) {
        try {
            Component component = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
            return literalizeTextComponent(component);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stripLegacyFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '\u00A7' || ch == '&') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if ((next >= '0' && next <= '9')
                        || (next >= 'a' && next <= 'f')
                        || (next >= 'k' && next <= 'o')
                        || next == 'r'
                        || next == 'x') {
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean isLikelyPlayerSender(String sender) {
        String cleanSender = sanitizeChatText(sender);
        if (cleanSender.isBlank()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (var entry : mc.getConnection().getListedOnlinePlayers()) {
                    String playerName = entry != null && entry.getProfile() != null ? extractTextValue(entry.getProfile()) : null;
                    if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (var player : mc.level.players()) {
                    String playerName = player != null && player.getGameProfile() != null ? extractTextValue(player.getGameProfile()) : null;
                    if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        return false;
    }

    public static boolean fuzzyChatMatch(String pattern, String candidate, int thresholdPercent) {
        String normalizedPattern = normalizeChatText(pattern);
        String normalizedCandidate = normalizeChatText(candidate);
        if (normalizedPattern.isBlank()) return true;
        if (normalizedCandidate.isBlank()) return false;
        if (normalizedCandidate.contains(normalizedPattern)) return true;

        List<String> patternTokens = tokenizeChat(normalizedPattern);
        List<String> candidateTokens = tokenizeChat(normalizedCandidate);
        if (patternTokens.isEmpty()) return true;

        if (thresholdPercent >= 100) {
            return allPatternTokensPresent(patternTokens, normalizedCandidate);
        }

        double tokenScore = computeTokenCoverage(patternTokens, candidateTokens);
        double charScore = similarityRatio(normalizedPattern.replace(" ", ""), normalizedCandidate.replace(" ", ""));
        double score = Math.max(tokenScore, tokenScore * 0.7 + charScore * 0.3);
        return score + 1.0e-9 >= (Math.max(40, Math.min(100, thresholdPercent)) / 100.0);
    }

    private static String normalizeChatForCompare(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) out.append(ch);
            else if (Character.isWhitespace(ch)) out.append(' ');
            else if (ch == '\'' || ch == '"' || ch == '`' || ch == '-' || ch == '_' || ch == '\u2019') {

            } else {
                out.append(' ');
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    public static String normalizeChatText(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        boolean lastWasSpace = true;
        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);

            cp = foldStyledLatinCodePoint(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK
                    || type == Character.FORMAT
                    || type == Character.CONTROL
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE) {
                continue;
            }

            if (Character.isLetterOrDigit(cp)) {
                out.appendCodePoint(cp);
                lastWasSpace = false;
            } else if (Character.isWhitespace(cp)) {
                if (!lastWasSpace) out.append(' ');
                lastWasSpace = true;
            } else if (isJoinerPunctuation(cp)) {

            } else {
                if (!lastWasSpace) out.append(' ');
                lastWasSpace = true;
            }
        }
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) == ' ') out.setLength(len - 1);
        return out.toString();
    }

    private static boolean isJoinerPunctuation(int cp) {
        return switch (cp) {
            case '\'', '"', '`', '-', '_', '\u2019', '\u2018', '\u201B', '\u2032', '\u02BC', '\uFF07',
                 '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> true;
            default -> false;
        };
    }

    private static int foldStyledLatinCodePoint(int cp) {
        return switch (cp) {
            case '\u1D00' -> 'a';
            case '\u0299' -> 'b';
            case '\u1D04' -> 'c';
            case '\u1D05' -> 'd';
            case '\u1D07' -> 'e';
            case '\uA730' -> 'f';
            case '\u0262' -> 'g';
            case '\u029C' -> 'h';
            case '\u026A' -> 'i';
            case '\u1D0A' -> 'j';
            case '\u1D0B' -> 'k';
            case '\u029F' -> 'l';
            case '\u1D0D' -> 'm';
            case '\u0274' -> 'n';
            case '\u1D0F' -> 'o';
            case '\u1D18' -> 'p';
            case '\u01EB' -> 'q';
            case '\u0280' -> 'r';
            case '\uA731' -> 's';
            case '\u1D1B' -> 't';
            case '\u1D1C' -> 'u';
            case '\u1D20' -> 'v';
            case '\u1D21' -> 'w';
            case '\u02E3' -> 'x';
            case '\u028F' -> 'y';
            case '\u1D22' -> 'z';
            case '\u00DF' -> 's';
            case '\u00E6' -> 'a';
            case '\u0153' -> 'o';
            default -> cp;
        };
    }

    private static List<String> tokenizeChat(String normalized) {
        if (normalized == null || normalized.isBlank()) return List.of();
        return List.of(normalized.split(" "));
    }

    private static boolean allPatternTokensPresent(List<String> patternTokens, String normalizedCandidate) {
        for (String token : patternTokens) {
            if (!normalizedCandidate.contains(token)) return false;
        }
        return true;
    }

    private static double computeTokenCoverage(List<String> patternTokens, List<String> candidateTokens) {
        if (patternTokens.isEmpty()) return 1.0;
        if (candidateTokens.isEmpty()) return 0.0;
        double total = 0.0;
        for (String patternToken : patternTokens) {
            double best = 0.0;
            for (String candidateToken : candidateTokens) {
                best = Math.max(best, tokenSimilarity(patternToken, candidateToken));
                if (best >= 1.0) break;
            }
            total += best;
        }
        return total / patternTokens.size();
    }

    private static double tokenSimilarity(String left, String right) {
        if (left.equals(right)) return 1.0;
        if (left.isBlank() || right.isBlank()) return 0.0;
        if (left.contains(right) || right.contains(left)) {
            return (double) Math.min(left.length(), right.length()) / Math.max(left.length(), right.length());
        }
        return similarityRatio(left, right);
    }

    private static double similarityRatio(String left, String right) {
        if (left.equals(right)) return 1.0;
        int maxLen = Math.max(left.length(), right.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshteinDistance(left, right);
        return Math.max(0.0, 1.0 - (distance / (double) maxLen));
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private static String extractChatName(Object value) {
        Object direct = firstNonNull(
            invokeFirstNoArg(value, "name", "getName", "targetName", "getTargetName"),
            getRecordComponentValue(value, 0),
            findRecordComponentByMethodNames(value, 2, "name", "targetName", "type")
        );
        return sanitizeChatText(extractTextValue(direct));
    }

    public static String resolvePlayerName(UUID uuid) {
        if (uuid == null) return "";
        try {
            if (Minecraft.getInstance().getConnection() != null) {
                var entry = Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
                if (entry != null && entry.getProfile() != null) {
                    String profileName = extractTextValue(entry.getProfile());
                    if (profileName != null && !profileName.isBlank()) return profileName;
                }
            }
        } catch (Throwable ignored) {  }
        try {
            if (Minecraft.getInstance().level != null) {
                for (var player : Minecraft.getInstance().level.players()) {
                    if (uuid.equals(player.getUUID()) && player.getGameProfile() != null) {
                        String profileName = extractTextValue(player.getGameProfile());
                        if (profileName != null && !profileName.isBlank()) return profileName;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        return "";
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static Object invokeFirstNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static Object getRecordComponentValue(Object target, int index) {
        if (target == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null || index < 0 || index >= components.length) return null;
            Method accessor = components[index].getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findRecordComponentByValueType(Object target, Class<?> type) {
        if (target == null || type == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) return null;
            for (RecordComponent component : components) {
                if (type.isAssignableFrom(component.getType())) {
                    Method accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    return accessor.invoke(target);
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object findRecordComponentByMethodNames(Object target, int fallbackIndex, String... names) {
        Object direct = invokeFirstNoArg(target, names);
        return direct != null ? direct : getRecordComponentValue(target, fallbackIndex);
    }

    private static Object findRecordTextComponent(Object target, Object... excludedValues) {
        if (target == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) return null;
            outer:
            for (RecordComponent component : components) {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(target);
                if (value == null) continue;
                for (Object excluded : excludedValues) {
                    if (excluded == value) continue outer;
                }
                if (value instanceof Component || value instanceof CharSequence || value instanceof Optional<?>) {
                    return value;
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Component extractTextComponent(Object value) {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? extractTextComponent(optional.get()) : null;
        }
        if (value instanceof Component text) return literalizeTextComponent(text);
        if (value instanceof CharSequence chars) return Component.literal(chars.toString());
        if (value instanceof UUID uuid) return Component.literal(resolvePlayerName(uuid));
        try {
            Method getComponent = value.getClass().getMethod("getText");
            getComponent.setAccessible(true);
            Object result = getComponent.invoke(value);
            Component text = extractTextComponent(result);
            if (text != null) return text;
        } catch (Throwable ignored) {  }
        try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) return Component.literal(String.valueOf(result));
        } catch (Throwable ignored) {  }
        return null;
    }

    public static Component literalizeTextComponent(Component text) {
        if (text == null) return null;
        MutableComponent literalized = Component.empty();
        try {
            text.visit((style, string) -> {
                if (string != null && !string.isEmpty()) {
                    literalized.append(Component.literal(string).setStyle(copyVisualStyle(style)));
                }
                return Optional.empty();
            }, Style.EMPTY);
        } catch (Throwable ignored) {  }
        if (!literalized.getString().isEmpty()) {
            return literalized;
        }
        return Component.literal(text.getString());
    }

    private static Style copyVisualStyle(Style style) {
        if (style == null || style.isEmpty()) return Style.EMPTY;
        Style safe = Style.EMPTY;
        if (style.getFont() != null) safe = safe.withFont(style.getFont());
        if (style.getColor() != null) safe = safe.withColor(style.getColor());
        if (style.isBold()) safe = safe.withBold(true);
        if (style.isItalic()) safe = safe.withItalic(true);
        if (style.isUnderlined()) safe = safe.withUnderlined(true);
        if (style.isStrikethrough()) safe = safe.withStrikethrough(true);
        if (style.isObfuscated()) safe = safe.withObfuscated(true);
        return safe;
    }

    public static String serializeTextComponent(Component text) {
        if (text == null) return "";
        try {
            Component safeComponent = literalizeTextComponent(text);
            return safeComponent == null ? "" : GSON.toJson(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, safeComponent).getOrThrow());
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static Component deserializeTextComponent(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return literalizeTextComponent(ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow().copy());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractTextValue(Object value) {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? extractTextValue(optional.get()) : null;
        }
        if (value instanceof Component text) return text.getString();
        if (value instanceof CharSequence chars) return chars.toString();
        if (value instanceof UUID uuid) return resolvePlayerName(uuid);
        try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) return String.valueOf(result);
        } catch (Throwable ignored) {  }
        try {
            Method getName = value.getClass().getMethod("getName");
            getName.setAccessible(true);
            Object result = getName.invoke(value);
            if (result instanceof CharSequence chars) return chars.toString();
        } catch (Throwable ignored) {  }
        return null;
    }
}
