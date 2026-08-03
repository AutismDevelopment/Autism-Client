package autismclient.util.macro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

public final class MacroTemplate {
    private static final Pattern EXPRESSION = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,63}(?:\\|[^{}|]+)*");
    private static final Pattern RANDOM_RANGE = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)\\s*-\\s*([-+]?\\d+(?:\\.\\d+)?)");
    private static final Pattern PICK_WEIGHT = Pattern.compile("(.*?)\\s*\\*\\s*(\\d{1,3})\\s*$");

    private static final Pattern QUANTIFIER_SHAPE = Pattern.compile("\\d+,\\d+");

    private static final String PICK_FORBIDDEN = "\"':={}\\";
    public record Resolution(boolean success, String value, List<String> missing, String error) {
        public static Resolution ok(String value) { return new Resolution(true, value, List.of(), ""); }
        public static Resolution failed(List<String> missing, String error) {
            return new Resolution(false, "", List.copyOf(missing), error == null ? "" : error);
        }
    }

    private MacroTemplate() {}

    public static Resolution resolve(String template, MacroVariableContext context, Minecraft mc) {
        if (template == null || template.isEmpty()) return Resolution.ok(template == null ? "" : template);
        StringBuilder out = new StringBuilder(template.length());
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < template.length();) {
            char ch = template.charAt(i);
            if (ch == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                out.append('{');
                i += 2;
                continue;
            }
            if (ch == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                out.append('}');
                i += 2;
                continue;
            }
            if (ch != '{') {
                out.append(ch);
                i++;
                continue;
            }
            int end = template.indexOf('}', i + 1);
            if (end < 0) {
                out.append(ch);
                i++;
                continue;
            }
            String expression = template.substring(i + 1, end).trim();
            ValueResolution value;
            if (EXPRESSION.matcher(expression).matches()) {
                value = resolveExpression(expression, context, mc);
            } else if (isBareRandomSpec(expression)) {
                value = resolveBareRandomSpec(expression);
            } else {
                out.append(ch);
                i++;
                continue;
            }
            if (!value.success) {
                missing.add(value.missingName.isEmpty() ? expression : value.missingName);
            } else {
                out.append(value.value);
            }
            i = end + 1;
        }
        return missing.isEmpty() ? Resolution.ok(out.toString()) : Resolution.failed(missing, "Missing macro value");
    }

    public static boolean hasVariables(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '{' || (i + 1 < text.length() && text.charAt(i + 1) == '{')) continue;
            int end = text.indexOf('}', i + 1);
            if (end <= i) continue;

            String expression = text.substring(i + 1, end).trim();
            if (EXPRESSION.matcher(expression).matches() || isBareRandomSpec(expression)) return true;
        }
        return false;
    }

    private static ValueResolution resolveExpression(String expression, MacroVariableContext context, Minecraft mc) {
        String[] parts = expression.split("\\|", -1);
        String name = parts.length == 0 ? "" : parts[0].trim();
        Optional<MacroValue> found = context == null ? Optional.empty() : context.get(name);

        if (found.isEmpty() && "random".equalsIgnoreCase(name)) return resolveRandom(parts);
        if (found.isEmpty()) found = builtIn(name, mc);
        String fallback = null;
        for (int i = 1; i < parts.length; i++) {
            String formatter = parts[i].trim();
            if (formatter.regionMatches(true, 0, "default:", 0, 8)) fallback = formatter.substring(8);
        }
        if (found.isEmpty()) {
            return fallback == null ? ValueResolution.missing(name) : ValueResolution.ok(fallback);
        }
        MacroValue value = found.get();
        String rendered = value.value();
        try {
            for (int i = 1; i < parts.length; i++) {
                String formatter = parts[i].trim();
                if (formatter.isEmpty() || formatter.regionMatches(true, 0, "default:", 0, 8)) continue;
                rendered = applyFormatter(rendered, formatter, value);
            }
            return ValueResolution.ok(rendered);
        } catch (IllegalArgumentException invalid) {
            return ValueResolution.missing(name);
        }
    }

    private static String applyFormatter(String text, String formatter, MacroValue value) {
        String key = formatter.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "trim" -> text.trim();
            case "lower" -> text.toLowerCase(Locale.ROOT);
            case "upper" -> text.toUpperCase(Locale.ROOT);
            case "number" -> parseCompactNumber(text);
            case "name" -> value.property("name").map(MacroValue::value).orElse(text);
            case "id" -> value.property("id").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No id"));
            case "count" -> value.property("count").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No count"));
            case "slot" -> value.property("slot").map(MacroValue::value).orElseThrow(() -> new IllegalArgumentException("No slot"));

            default -> value.property(key).map(MacroValue::value)
                .orElseThrow(() -> new IllegalArgumentException("Unknown formatter: " + formatter));
        };
    }

    private static ValueResolution resolveRandom(String[] parts) {
        int specIndex = -1;
        String rendered = null;
        for (int i = 1; i < parts.length && specIndex < 0; i++) {
            String token = parts[i].trim();
            if (token.isEmpty() || token.regionMatches(true, 0, "default:", 0, 8)) continue;
            if (token.regionMatches(true, 0, "pick:", 0, 5)) {
                List<String> options = new ArrayList<>();
                for (String option : token.substring(5).split(",")) {
                    String trimmed = option.trim();
                    if (!trimmed.isEmpty()) options.add(trimmed);
                }
                if (options.isEmpty()) return ValueResolution.missing("random");
                rendered = options.get(ThreadLocalRandom.current().nextInt(options.size()));
                specIndex = i;
                continue;
            }
            Matcher range = RANDOM_RANGE.matcher(token);
            if (range.matches()) {
                rendered = randomInRange(range.group(1), range.group(2));
                specIndex = i;
            }
        }
        if (rendered == null) rendered = Integer.toString(ThreadLocalRandom.current().nextInt(100));
        return applyTrailingFormatters(rendered, parts, specIndex, "random");
    }

    private static ValueResolution applyTrailingFormatters(String rendered, String[] parts, int specIndex, String missingName) {
        MacroValue value = MacroValue.text(rendered);
        try {
            for (int i = 1; i < parts.length; i++) {
                if (i == specIndex) continue;
                String formatter = parts[i].trim();
                if (formatter.isEmpty() || formatter.regionMatches(true, 0, "default:", 0, 8)) continue;
                rendered = applyFormatter(rendered, formatter, value);
            }
            return ValueResolution.ok(rendered);
        } catch (IllegalArgumentException invalid) {
            return ValueResolution.missing(missingName);
        }
    }

    private static boolean isBareRandomSpec(String expression) {
        String spec = specOf(expression);
        if (spec.isEmpty()) return false;
        if (RANDOM_RANGE.matcher(spec).matches()) return true;
        return parseBarePick(spec) != null;
    }

    private static ValueResolution resolveBareRandomSpec(String expression) {
        String[] parts = expression.split("\\|", -1);
        String spec = parts[0].trim();
        Matcher range = RANDOM_RANGE.matcher(spec);
        String rendered = range.matches()
            ? randomInRange(range.group(1), range.group(2))
            : rollPick(parseBarePick(spec));
        return applyTrailingFormatters(rendered, parts, 0, spec);
    }

    private static String specOf(String expression) {
        int pipe = expression.indexOf('|');
        return (pipe < 0 ? expression : expression.substring(0, pipe)).trim();
    }

    private record PickOption(String text, int weight, String lo, String hi) {}

    private static List<PickOption> parseBarePick(String spec) {
        if (spec.indexOf(',') < 0) return null;
        for (int i = 0; i < spec.length(); i++) {
            if (PICK_FORBIDDEN.indexOf(spec.charAt(i)) >= 0) return null;
        }
        if (QUANTIFIER_SHAPE.matcher(spec).matches()) return null;
        List<PickOption> options = new ArrayList<>();
        for (String entry : spec.split(",", -1)) {
            String text = entry.trim();
            if (text.isEmpty()) continue;
            int weight = 1;
            Matcher weighted = PICK_WEIGHT.matcher(text);
            if (weighted.matches()) {
                String base = weighted.group(1).trim();
                int parsed = Integer.parseInt(weighted.group(2));

                if (!base.isEmpty() && parsed >= 1 && parsed <= 100) {
                    text = base;
                    weight = parsed;
                }
            }
            Matcher range = RANDOM_RANGE.matcher(text);
            options.add(range.matches()
                ? new PickOption(text, weight, range.group(1), range.group(2))
                : new PickOption(text, weight, null, null));
        }
        return options.size() >= 2 ? options : null;
    }

    private static String rollPick(List<PickOption> options) {
        int total = 0;
        for (PickOption option : options) total += option.weight();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (PickOption option : options) {
            roll -= option.weight();
            if (roll < 0) return option.lo() == null ? option.text() : randomInRange(option.lo(), option.hi());
        }
        return options.get(options.size() - 1).text();
    }

    private static String randomInRange(String loText, String hiText) {
        BigDecimal lo = new BigDecimal(loText);
        BigDecimal hi = new BigDecimal(hiText);
        if (lo.compareTo(hi) > 0) {
            BigDecimal swap = lo;
            lo = hi;
            hi = swap;
        }
        if (loText.indexOf('.') < 0 && hiText.indexOf('.') < 0) {
            try {
                long min = lo.longValueExact();
                long max = hi.longValueExact();
                long picked;
                if (min == max) picked = min;
                else if (max != Long.MAX_VALUE) picked = ThreadLocalRandom.current().nextLong(min, max + 1);
                else if (min != Long.MIN_VALUE) picked = ThreadLocalRandom.current().nextLong(min - 1, max) + 1;
                else picked = ThreadLocalRandom.current().nextLong();
                int padWidth = zeroPadWidth(loText, hiText);
                if (padWidth > 0 && picked != Long.MIN_VALUE) {
                    String digits = Long.toString(Math.abs(picked));
                    StringBuilder padded = new StringBuilder(padWidth + 1);
                    if (picked < 0) padded.append('-');
                    for (int i = digits.length(); i < padWidth; i++) padded.append('0');
                    return padded.append(digits).toString();
                }
                return Long.toString(picked);
            } catch (ArithmeticException tooBig) {  }
        }
        int scale = Math.max(lo.scale(), hi.scale());
        BigDecimal picked = lo.add(hi.subtract(lo).multiply(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())));
        return picked.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static int zeroPadWidth(String loText, String hiText) {
        String loDigits = stripSign(loText);
        String hiDigits = stripSign(hiText);
        boolean padded = (loDigits.length() >= 2 && loDigits.charAt(0) == '0')
            || (hiDigits.length() >= 2 && hiDigits.charAt(0) == '0');
        return padded ? Math.max(loDigits.length(), hiDigits.length()) : 0;
    }

    private static String stripSign(String text) {
        return text.startsWith("-") || text.startsWith("+") ? text.substring(1) : text;
    }

    public static String parseCompactNumber(String raw) {
        if (raw == null) throw new IllegalArgumentException("Empty number");
        String cleaned = raw.trim().replace(" ", "").replace(",", "").toUpperCase(Locale.ROOT);
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Empty number");
        BigDecimal multiplier = BigDecimal.ONE;
        char suffix = cleaned.charAt(cleaned.length() - 1);
        multiplier = switch (suffix) {
            case 'K' -> new BigDecimal("1000");
            case 'M' -> new BigDecimal("1000000");
            case 'B' -> new BigDecimal("1000000000");
            case 'T' -> new BigDecimal("1000000000000");
            default -> BigDecimal.ONE;
        };
        if (multiplier.compareTo(BigDecimal.ONE) != 0) cleaned = cleaned.substring(0, cleaned.length() - 1);
        BigDecimal number = new BigDecimal(cleaned).multiply(multiplier).setScale(0, RoundingMode.DOWN);
        return number.toPlainString();
    }

    public static final java.util.Set<String> BUILT_IN_NAMES = java.util.Set.of(
        "timestamp", "player", "user", "username", "uuid",
        "x", "y", "z", "bx", "by", "bz", "pos",
        "yaw", "pitch", "rot", "facing",
        "dimension", "dim", "selected_slot", "target_slot", "server",
        "random"
    );

    private static Optional<MacroValue> builtIn(String name, Minecraft mc) {
        if (name == null) return Optional.empty();
        String key = name.trim().toLowerCase(Locale.ROOT);
        if ("timestamp".equals(key)) return Optional.of(MacroValue.text(java.time.Instant.now().toString()));
        if (mc == null || mc.player == null) return Optional.empty();
        return switch (key) {
            case "player", "user", "username" -> Optional.of(MacroValue.text(mc.player.getName().getString()));
            case "uuid" -> Optional.of(MacroValue.text(mc.player.getUUID().toString()));
            case "x" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getX())));
            case "y" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getY())));
            case "z" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.3f", mc.player.getZ())));
            case "bx" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getX())));
            case "by" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getY())));
            case "bz" -> Optional.of(MacroValue.text(Integer.toString(mc.player.blockPosition().getZ())));
            case "pos" -> {
                net.minecraft.core.BlockPos pos = mc.player.blockPosition();
                yield Optional.of(MacroValue.text(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            }
            case "yaw" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f", mc.player.getYRot())));
            case "pitch" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f", mc.player.getXRot())));
            case "rot" -> Optional.of(MacroValue.text(String.format(Locale.ROOT, "%.2f %.2f", mc.player.getYRot(), mc.player.getXRot())));
            case "facing" -> Optional.of(MacroValue.text(net.minecraft.core.Direction.fromYRot(mc.player.getYRot()).getName()));
            case "dimension", "dim" -> mc.level == null
                ? Optional.empty()
                : Optional.of(MacroValue.text(mc.level.dimension().identifier().toString()));
            case "selected_slot", "target_slot" -> Optional.of(MacroValue.slot(mc.player.getInventory().getSelectedSlot()));
            case "server" -> {
                var server = mc.getCurrentServer();
                yield server == null || server.ip == null ? Optional.empty() : Optional.of(MacroValue.text(server.ip));
            }
            default -> Optional.empty();
        };
    }

    private record ValueResolution(boolean success, String value, String missingName) {
        static ValueResolution ok(String value) { return new ValueResolution(true, value, ""); }
        static ValueResolution missing(String name) { return new ValueResolution(false, "", name == null ? "" : name); }
    }
}
