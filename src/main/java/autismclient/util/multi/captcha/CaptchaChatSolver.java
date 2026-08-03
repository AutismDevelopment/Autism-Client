package autismclient.util.multi.captcha;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CaptchaChatSolver {

    public enum Kind { CHAT, COMMAND }

    public record Answer(Kind kind, String text) {
    }

    private record Button(String command, String label, TextColor color) {
    }

    private static final Pattern MATH = Pattern.compile("(\\d{1,3})\\s*([+\\-])\\s*(\\d{1,3})");

    private static final Pattern CODE_TOKEN = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z0-9]{3,8})(?![A-Za-z0-9])");
    private static final Pattern CAPTCHA_HINT = Pattern.compile(
        "captcha|verif|\\bcode\\b|type the|enter the|solve|try again|➤", Pattern.CASE_INSENSITIVE);

    private static final String[][] COLOR_WORDS = {
        {"pink", "light_purple"}, {"purple", "dark_purple"}, {"blue", "blue"}, {"aqua", "aqua"},
        {"cyan", "aqua"}, {"green", "green"}, {"lime", "green"}, {"yellow", "yellow"}, {"gold", "gold"},
        {"orange", "gold"}, {"red", "red"}, {"white", "white"}, {"gray", "gray"}, {"grey", "gray"},
    };

    private final ArrayDeque<String> recentLines = new ArrayDeque<>();

    public Answer onChat(Component component) {
        if (component == null) return null;
        String plain = component.getString();

        List<Button> buttons = new ArrayList<>();
        collectButtons(component, buttons);
        if (!buttons.isEmpty()) {
            Answer a = solveButtons(buttons, plain);
            if (a != null) { pushRecent(plain); return a; }
        }

        Answer code = solveChatCode(plain);
        pushRecent(plain);
        return code;
    }

    private void collectButtons(Component node, List<Button> out) {
        Style style = node.getStyle();
        if (style != null && style.getClickEvent() instanceof ClickEvent.RunCommand rc) {
            String cmd = rc.command();
            if (cmd != null && cmd.toLowerCase(Locale.ROOT).contains("captcha_click")) {
                out.add(new Button(cmd, node.getString().trim(), style.getColor()));
            }
        }
        for (Component sibling : node.getSiblings()) collectButtons(sibling, out);
    }

    private Answer solveButtons(List<Button> buttons, String currentLine) {

        String context = String.join(" ", recentLines).toLowerCase(Locale.ROOT);

        Matcher m = MATH.matcher(context);
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(3));
            int result = m.group(2).equals("-") ? a - b : a + b;
            for (Button btn : buttons) {
                String label = btn.label().replaceAll("[^0-9-]", "");
                if (!label.isEmpty() && parseIntSafe(label) == result) {
                    return command(btn.command());
                }
            }
        }

        List<Button> labelHits = new ArrayList<>();
        for (Button btn : buttons) {
            String label = btn.label().toLowerCase(Locale.ROOT).trim();
            if (label.length() >= 2 && context.contains(label)) labelHits.add(btn);
        }
        if (labelHits.size() == 1) return command(labelHits.get(0).command());
        if (labelHits.size() > 1) {
            for (Button btn : labelHits) {
                if (colorWordInContext(btn.color(), context)) return command(btn.command());
            }
            return command(labelHits.get(0).command());
        }

        for (Button btn : buttons) {
            if (colorWordInContext(btn.color(), context)) return command(btn.command());
        }
        return null;
    }

    private boolean colorWordInContext(TextColor color, String context) {
        if (color == null) return false;
        String name = color.serialize().toLowerCase(Locale.ROOT);
        for (String[] pair : COLOR_WORDS) {
            if (pair[1].equals(name) && context.contains(pair[0])) return true;
        }
        return false;
    }

    private static Answer command(String clickCommand) {
        String cmd = clickCommand.startsWith("/") ? clickCommand.substring(1) : clickCommand;
        return new Answer(Kind.COMMAND, cmd);
    }

    private Answer solveChatCode(String line) {
        if (line == null || line.isBlank()) return null;

        boolean hint = CAPTCHA_HINT.matcher(line).find();
        int arrow = line.indexOf('➤');
        if (!hint && arrow < 0) return null;

        String search = arrow >= 0 ? line.substring(arrow + 1) : line;
        Matcher m = CODE_TOKEN.matcher(search);
        String candidate = null;
        while (m.find()) {
            String tok = m.group(1);

            if (tok.chars().anyMatch(Character::isDigit) || tok.equals(tok.toUpperCase(Locale.ROOT))) {
                candidate = tok;
            }
        }
        return candidate == null ? null : new Answer(Kind.CHAT, candidate);
    }

    private void pushRecent(String line) {
        if (line == null || line.isBlank()) return;
        recentLines.addLast(line);
        while (recentLines.size() > 6) recentLines.removeFirst();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }
}
