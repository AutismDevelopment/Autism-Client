package autismclient.util.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MacroCapturePattern {
    public enum Mode { MATCH, CAPTURE, REGEX }

    public record Result(String matchedText, Map<String, MacroValue> values) {
        public Result {
            matchedText = matchedText == null ? "" : matchedText;
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_-]{0,63})}");
    private static final Pattern NAMED_GROUP = Pattern.compile("\\(\\?<([A-Za-z_][A-Za-z0-9_]{0,63})>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final char LITERAL_OPEN_BRACE = '\uE000';
    private static final char LITERAL_CLOSE_BRACE = '\uE001';

    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> COMPILED = new java.util.concurrent.ConcurrentHashMap<>();

    public static Pattern compiled(String regex) {
        Pattern cached = COMPILED.get(regex);
        if (cached != null) return cached;
        if (COMPILED.size() > 256) COMPILED.clear();
        Pattern fresh = Pattern.compile(regex);
        COMPILED.put(regex, fresh);
        return fresh;
    }

    private MacroCapturePattern() {}

    public static Optional<Result> match(Mode mode, String pattern, String candidate) {
        if (candidate == null) return Optional.empty();
        Mode effective = mode == null ? Mode.MATCH : mode;
        return switch (effective) {
            case MATCH -> Optional.empty();
            case CAPTURE -> capturePattern(pattern, candidate);
            case REGEX -> regexPattern(pattern, candidate);
        };
    }

    public static Set<String> declaredNames(Mode mode, String pattern) {
        Pattern parser = mode == Mode.REGEX ? NAMED_GROUP : TOKEN;
        String source = pattern == null ? "" : pattern;
        if (mode != Mode.REGEX) source = protectLiteralBraces(source);
        Matcher matcher = parser.matcher(source);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return names;
    }

    private static Optional<Result> capturePattern(String sourcePattern, String sourceCandidate) {
        String capturePattern = protectLiteralBraces(sourcePattern == null ? "" : sourcePattern.trim());
        String candidate = normalize(sourceCandidate);
        if (capturePattern.isEmpty()) return Optional.of(new Result(candidate, Map.of()));

        Matcher tokenMatcher = TOKEN.matcher(capturePattern);
        StringBuilder regex = new StringBuilder("(?iu)");
        List<String> names = new ArrayList<>();
        int cursor = 0;
        while (tokenMatcher.find()) {
            if (tokenMatcher.start() == cursor && cursor > 0) return Optional.empty();
            appendFlexibleLiteral(regex, capturePattern.substring(cursor, tokenMatcher.start()));
            String name = tokenMatcher.group(1).toLowerCase(Locale.ROOT);
            if (names.contains(name)) return Optional.empty();
            names.add(name);
            boolean capturesRest = capturePattern.substring(tokenMatcher.end()).trim().isEmpty();
            regex.append("(?<g").append(names.size() - 1).append(capturesRest ? ">.+)" : ">.+?)");
            cursor = tokenMatcher.end();
        }
        if (names.isEmpty()) return Optional.empty();
        appendFlexibleLiteral(regex, capturePattern.substring(cursor));

        try {
            Matcher matcher = compiled(regex.toString()).matcher(candidate);
            if (!matcher.find()) return Optional.empty();
            Map<String, MacroValue> values = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                String value = matcher.group("g" + i);
                if (value == null || value.isBlank()) return Optional.empty();
                values.put(name, MacroValue.text(value));
            }
            return Optional.of(new Result(matcher.group(), values));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Result> regexPattern(String sourcePattern, String sourceCandidate) {
        String regex = sourcePattern == null ? "" : sourcePattern;
        if (regex.isBlank()) return Optional.of(new Result(sourceCandidate, Map.of()));
        List<String> names = new ArrayList<>(declaredNames(Mode.REGEX, regex));
        try {
            Matcher matcher = compiled(regex).matcher(sourceCandidate);
            if (!matcher.find()) return Optional.empty();
            Map<String, MacroValue> values = new LinkedHashMap<>();
            for (String name : names) {
                String value = matcher.group(name);
                if (value != null) values.put(name, MacroValue.text(value));
            }
            return Optional.of(new Result(matcher.group(), values));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static void appendFlexibleLiteral(StringBuilder regex, String literal) {
        if (literal == null || literal.isEmpty()) return;
        literal = literal.replace(LITERAL_OPEN_BRACE, '{').replace(LITERAL_CLOSE_BRACE, '}');
        String[] pieces = literal.trim().split("\\s+");
        if (literal.startsWith(" ") || literal.startsWith("\t")) regex.append("\\s+");
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].isEmpty()) continue;
            if (i > 0) regex.append("\\s+");
            regex.append(Pattern.quote(pieces[i]));
        }
        if (literal.endsWith(" ") || literal.endsWith("\t")) regex.append("\\s+");
    }

    private static String protectLiteralBraces(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("{{", String.valueOf(LITERAL_OPEN_BRACE))
            .replace("}}", String.valueOf(LITERAL_CLOSE_BRACE));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return WHITESPACE.matcher(value.replace('\n', ' ').replace('\r', ' ')).replaceAll(" ").trim();
    }
}
