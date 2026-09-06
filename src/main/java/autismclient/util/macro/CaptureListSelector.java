package autismclient.util.macro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CaptureListSelector {
    public enum Selection { RANDOM, SEQUENTIAL, RANDOM_NO_REPEAT, FIRST, LAST, POSITION }
    public enum Filter { NONE, PREFIX, SUFFIX, CONTAINS, NOT_CONTAINS, REGEX }

    public static final class State {
        int cursor;
        final Set<String> used = new HashSet<>();
    }

    public record Pick(String value, int index, List<String> pool) {}

    private CaptureListSelector() {}

    public static String filterError(Filter mode, String text) {
        if (mode != Filter.REGEX || text == null || text.isBlank()) return "";
        try {
            Pattern.compile(text);
            return "";
        } catch (PatternSyntaxException invalid) {
            return "Invalid filter regex";
        }
    }

    public static List<String> filter(List<String> candidates, Filter mode, String text) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Filter effective = mode == null ? Filter.NONE : mode;
        if (effective == Filter.NONE || text == null || text.isBlank()) return candidates;
        if (effective == Filter.REGEX) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(text);
            } catch (PatternSyntaxException invalid) {
                return List.of();
            }
            List<String> matched = new ArrayList<>();
            for (String candidate : candidates) {
                if (candidate != null && pattern.matcher(candidate).find()) matched.add(candidate);
            }
            return matched;
        }

        String needle = text.trim().toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String lower = candidate.toLowerCase(Locale.ROOT);
            boolean hit = switch (effective) {
                case PREFIX -> lower.startsWith(needle);
                case SUFFIX -> lower.endsWith(needle);
                case CONTAINS -> lower.contains(needle);
                case NOT_CONTAINS -> !lower.contains(needle);
                default -> true;
            };
            if (hit) matched.add(candidate);
        }
        return matched;
    }

    public static List<String> exclude(List<String> pool, String excludeText) {
        if (pool == null || pool.isEmpty() || excludeText == null || excludeText.isBlank()) {
            return pool == null ? List.of() : pool;
        }
        List<String> tokens = new ArrayList<>();
        for (String token : excludeText.split(",")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) tokens.add(trimmed);
        }
        if (tokens.isEmpty()) return pool;
        List<String> kept = new ArrayList<>(pool.size());
        for (String entry : pool) {
            if (entry == null) continue;
            String lower = entry.toLowerCase(Locale.ROOT);
            boolean excluded = false;
            for (String token : tokens) {
                if (lower.contains(token)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) kept.add(entry);
        }
        return kept;
    }

    public static String stripMatch(String value, Filter mode, String text) {
        if (value == null || text == null) return value;
        String match = text.trim();
        if (match.isEmpty()) return value;
        if (mode == Filter.PREFIX && value.regionMatches(true, 0, match, 0, match.length())) {
            return value.substring(match.length());
        }
        if (mode == Filter.SUFFIX && value.length() >= match.length()
                && value.regionMatches(true, value.length() - match.length(), match, 0, match.length())) {
            return value.substring(0, value.length() - match.length());
        }
        return value;
    }

    public static Optional<Pick> pick(List<String> pool, Selection selection, State state) {
        return pick(pool, selection, 1, state);
    }

    public static Optional<Pick> pick(List<String> pool, Selection selection, int position, State state) {
        if (pool == null || pool.isEmpty()) return Optional.empty();
        Selection effective = selection == null ? Selection.RANDOM : selection;
        int index = switch (effective) {
            case FIRST -> 0;
            case LAST -> pool.size() - 1;
            case POSITION -> Math.max(1, Math.min(position, pool.size())) - 1;
            case RANDOM -> state == null ? 0 : ThreadLocalRandom.current().nextInt(pool.size());
            case SEQUENTIAL -> {
                if (state == null) yield 0;
                int next = Math.floorMod(state.cursor, pool.size());
                state.cursor = next + 1;
                yield next;
            }
            case RANDOM_NO_REPEAT -> {
                if (state == null) yield 0;
                List<Integer> unused = new ArrayList<>();
                for (int i = 0; i < pool.size(); i++) {
                    if (!state.used.contains(lowered(pool.get(i)))) unused.add(i);
                }
                if (unused.isEmpty()) {

                    state.used.clear();
                    for (int i = 0; i < pool.size(); i++) unused.add(i);
                }
                int picked = unused.get(ThreadLocalRandom.current().nextInt(unused.size()));
                state.used.add(lowered(pool.get(picked)));
                yield picked;
            }
        };
        return Optional.of(new Pick(pool.get(index), index, pool));
    }

    private static String lowered(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
