package autismclient.util.macro;

import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class CustomMenuActionSupport {
    public record Prepared(CustomMenuSubmission submission, String error) {
        public boolean success() { return submission != null && error.isEmpty(); }
        static Prepared ok(CustomMenuSubmission submission) { return new Prepared(submission, ""); }
        static Prepared fail(String error) { return new Prepared(null, error == null ? "Custom screen failed" : error); }
    }

    private static final List<String> POSITIVE = List.of(
        "login", "register", "submit", "continue", "confirm", "yes", "ok", "accept", "agree", "agreed",
        "done", "join", "enter");
    private static final List<String> NEGATIVE = List.of(
        "cancel", "quit", "back", "deny", "decline", "no", "exit", "disconnect", "close");

    private static final List<String> ACCEPT = List.of(
        "accept", "agree", "agreed", "consent", "understood", "acknowledge", "confirm");

    private CustomMenuActionSupport() {}

    public static boolean titleMatches(CustomMenuAction action, String title) {
        return true;
    }

    public static String titleMatcherError(CustomMenuAction action) {
        return "";
    }

    public static Prepared prepare(CustomMenuAction action, CustomMenuSnapshot snapshot, Function<String, String> resolver) {
        if (action == null || snapshot == null) return Prepared.fail("No custom screen is open");
        Function<String, String> safeResolver = resolver == null ? Function.identity() : resolver;

        Map<String, String> values = new LinkedHashMap<>();
        for (CustomMenuInput input : snapshot.inputs()) values.put(input.key(), input.initialValue());

        List<CustomMenuInput> textInputs = new ArrayList<>();
        for (CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == CustomMenuInput.Kind.TEXT) textInputs.add(input);
        }

        List<String> fields = action.fieldValues.isEmpty() ? List.of("{secret.password}") : action.fieldValues;

        List<String> resolved = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String out;
            try { out = safeResolver.apply(safe(fields.get(i))); }
            catch (RuntimeException error) { return Prepared.fail("Text field value " + (i + 1) + " is unavailable"); }
            if (out == null) return Prepared.fail("Text field value " + (i + 1) + " is unavailable");
            resolved.add(out);
        }

        if (!textInputs.isEmpty() && !resolved.isEmpty()) {
            if (resolved.size() == 1) {

                for (CustomMenuInput input : textInputs) values.put(input.key(), resolved.get(0));
            } else {

                for (int i = 0; i < textInputs.size() && i < resolved.size(); i++) {
                    values.put(textInputs.get(i).key(), resolved.get(i));
                }
            }
        }

        CustomMenuButton button = selectButton(action, snapshot.buttons());
        if (button == null) {
            return Prepared.fail(safe(action.clickButton).isBlank()
                ? "No obvious button to press; type the button's text (e.g. Register)"
                : "No button matched \"" + action.clickButton + "\" on this screen");
        }
        return Prepared.ok(new CustomMenuSubmission(values, button));
    }

    public static CustomMenuButton selectButton(CustomMenuAction action, List<CustomMenuButton> buttons) {
        if (buttons == null || buttons.isEmpty()) return null;
        List<CustomMenuButton> usable = new ArrayList<>();
        for (CustomMenuButton button : buttons) if (button.serverRelevant()) usable.add(button);
        if (usable.isEmpty()) return null;

        String want = safe(action == null ? "" : action.clickButton).trim();
        if (want.isEmpty()) return autoButton(usable);

        if (want.startsWith("#")) {
            try {
                int index = Integer.parseInt(want.substring(1).trim());
                for (CustomMenuButton button : usable) if (button.index() == index) return button;
            } catch (NumberFormatException ignored) {  }
        }
        String lower = want.toLowerCase(Locale.ROOT);
        for (CustomMenuButton button : usable) if (button.label().equalsIgnoreCase(want)) return button;
        for (CustomMenuButton button : usable) {
            if (button.label().toLowerCase(Locale.ROOT).contains(lower)) return button;
        }
        for (CustomMenuButton button : usable) {
            if (!button.actionId().isBlank()
                && button.actionId().toLowerCase(Locale.ROOT).contains(lower)) return button;
        }
        return null;
    }

    public static CustomMenuButton acceptButton(List<CustomMenuButton> buttons) {
        if (buttons == null) return null;
        for (CustomMenuButton button : buttons) {
            if (!button.serverRelevant()) continue;
            if (containsWord(button.label(), ACCEPT) || containsWord(button.actionId(), ACCEPT)) return button;
        }
        return null;
    }

    public static CustomMenuButton autoSubmitButton(List<CustomMenuButton> buttons) {
        if (buttons == null) return null;
        List<CustomMenuButton> usable = new ArrayList<>();
        for (CustomMenuButton button : buttons) if (button.serverRelevant()) usable.add(button);
        return usable.isEmpty() ? null : autoButton(usable);
    }

    private static CustomMenuButton autoButton(List<CustomMenuButton> usable) {
        List<CustomMenuButton> nonNegative = new ArrayList<>();
        for (CustomMenuButton button : usable) {
            if (!containsWord(button.label(), NEGATIVE) && !containsWord(button.actionId(), NEGATIVE)) nonNegative.add(button);
        }
        if (nonNegative.size() == 1) return nonNegative.get(0);
        List<CustomMenuButton> positive = new ArrayList<>();
        for (CustomMenuButton button : nonNegative) {
            if (containsWord(button.label(), POSITIVE) || containsWord(button.actionId(), POSITIVE)) positive.add(button);
        }
        if (positive.size() == 1) return positive.get(0);
        return null;
    }

    private static boolean containsWord(String value, List<String> words) {
        String[] tokens = safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String word : words) if (token.equals(word)) return true;
        }
        return false;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
