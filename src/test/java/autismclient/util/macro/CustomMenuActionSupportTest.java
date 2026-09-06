package autismclient.util.macro;

import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomMenuActionSupportTest {
    private static CustomMenuInput text(int index, String key) {
        return new CustomMenuInput(index, key, key, CustomMenuInput.Kind.TEXT, "", 128, 0, 0, 0, List.of());
    }

    private static CustomMenuButton button(int index, String label, String id) {
        return new CustomMenuButton(index, label, id, CustomMenuButton.Kind.CUSTOM);
    }

    @Test
    void oneValueFillsEveryTextFieldAndAutoPicksTheSubmitButton() {
        CustomMenuAction action = new CustomMenuAction();
        action.fieldValues.add("{secret.password}");
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "CONFIGURATION", 1, "Register",
            List.of(text(1, "password"), text(2, "confirm_password")),
            List.of(button(1, "Quit", "sparklogin:cancel"),
                button(2, "Register", "sparklogin:register_submit")), null);

        CustomMenuActionSupport.Prepared prepared =
            CustomMenuActionSupport.prepare(action, snapshot, ignored -> "correct horse");

        assertTrue(prepared.success());
        assertEquals("correct horse", prepared.submission().values().get("password"));
        assertEquals("correct horse", prepared.submission().values().get("confirm_password"));

        assertEquals("sparklogin:register_submit", prepared.submission().button().actionId());
    }

    @Test
    void emptyActionFillsEveryFieldWithTheLoginPassword() {

        CustomMenuAction action = new CustomMenuAction();
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "CONFIGURATION", 1, "Register",
            List.of(text(1, "password"), text(2, "confirm_password")),
            List.of(button(1, "Register", "sparklogin:register_submit")), null);

        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(action, snapshot,
            template -> "{secret.password}".equals(template) ? "hunter2" : template);

        assertTrue(prepared.success());
        assertEquals("hunter2", prepared.submission().values().get("password"));
        assertEquals("hunter2", prepared.submission().values().get("confirm_password"));
    }

    @Test
    void buttonOnlyScreenNeedsNoValues() {

        CustomMenuAction action = new CustomMenuAction();
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "PLAY", 1, "Rules",
            List.of(), List.of(button(1, "Accept", "rules:accept")), null);

        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(action, snapshot, template -> {
            throw new IllegalStateException("no password stored");
        });

        assertTrue(prepared.success());
        assertTrue(prepared.submission().values().isEmpty());
        assertEquals("rules:accept", prepared.submission().button().actionId());
    }

    @Test
    void configuredValuesAreIgnoredWhenTheScreenHasNoTextFields() {
        CustomMenuAction action = new CustomMenuAction();
        action.fieldValues.add("{secret.password}");
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "PLAY", 1, "Confirm",
            List.of(), List.of(button(1, "Continue", "menu:continue")), null);

        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(action, snapshot, template -> {
            throw new IllegalStateException("no password stored");
        });

        assertTrue(prepared.success());
        assertEquals("menu:continue", prepared.submission().button().actionId());
    }

    @Test
    void missingValueStillFailsWhenTheScreenHasTextFields() {
        CustomMenuAction action = new CustomMenuAction();
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "PLAY", 1, "Login",
            List.of(text(1, "password")), List.of(button(1, "Login", "login:submit")), null);

        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(action, snapshot, template -> {
            throw new IllegalStateException("no password stored");
        });

        assertFalse(prepared.success());
        assertTrue(prepared.error().contains("unavailable"));
    }

    @Test
    void autoSubmitButtonPicksTheLoneSubmit() {

        CustomMenuButton picked = CustomMenuActionSupport.autoSubmitButton(
            List.of(button(1, "Quit", "cancel"), button(2, "Register", "sparklogin:register_submit")));
        assertEquals("sparklogin:register_submit", picked == null ? null : picked.actionId());
    }

    @Test
    void orderedValuesFillFieldsInOrder() {
        CustomMenuAction action = new CustomMenuAction();
        action.fieldValues.add("first");
        action.fieldValues.add("second");
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "PLAY", 2, "Mixed",
            List.of(text(1, "password"), text(2, "code")), List.of(button(1, "OK", "submit")), null);

        CustomMenuActionSupport.Prepared prepared =
            CustomMenuActionSupport.prepare(action, snapshot, value -> value);

        assertTrue(prepared.success());
        assertEquals("first", prepared.submission().values().get("password"));
        assertEquals("second", prepared.submission().values().get("code"));
    }

    @Test
    void buttonTextMatchesByLabelSubstring() {
        CustomMenuAction action = new CustomMenuAction();
        action.clickButton = "regis";
        CustomMenuSnapshot snapshot = new CustomMenuSnapshot("test", "PLAY", 3, "Register",
            List.of(text(1, "password")),
            List.of(button(1, "Quit", "x"), button(2, "Register", "sparklogin:register_submit")), null);

        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(action, snapshot, v -> v);
        assertTrue(prepared.success());
        assertEquals("sparklogin:register_submit", prepared.submission().button().actionId());
    }

    @Test
    void ambiguousAutoAndUnsafeButtonsAreRejected() {
        CustomMenuAction action = new CustomMenuAction();

        assertNull(CustomMenuActionSupport.selectButton(action,
            List.of(button(1, "Alpha", "alpha"), button(2, "Beta", "beta"))));

        assertNull(CustomMenuActionSupport.selectButton(action, List.of(
            new CustomMenuButton(1, "Website", "", CustomMenuButton.Kind.URL))));
    }

    @Test
    void wrongButtonTextMatchesNothing() {
        CustomMenuAction action = new CustomMenuAction();
        action.clickButton = "nonexistent";
        assertNull(CustomMenuActionSupport.selectButton(action,
            List.of(button(1, "Register", "sparklogin:register_submit"))));
    }

    private static CustomMenuButton colored(int index, String label, String id, String color) {
        return new CustomMenuButton(index, label, id, CustomMenuButton.Kind.CUSTOM, color);
    }

    @Test
    void loginButtonPrefersAGreenLabelOverAPositiveWord() {
        CustomMenuButton picked = CustomMenuActionSupport.loginButton(List.of(
            colored(1, "Cancel", "cancel", "red"),
            colored(2, "Continue", "continue2", "green"),
            button(3, "Register", "register_submit")));
        assertEquals("Continue", picked.label());
    }

    @Test
    void loginButtonReadsHexGreenAndRejectsOtherHues() {
        assertTrue(CustomMenuActionSupport.isGreen("#55FF55"));
        assertTrue(CustomMenuActionSupport.isGreen("#00AA00"));
        assertTrue(CustomMenuActionSupport.isGreen("#32cd32"));
        assertTrue(CustomMenuActionSupport.isGreen("dark_green"));
        assertFalse(CustomMenuActionSupport.isGreen("#FFFF55"));
        assertFalse(CustomMenuActionSupport.isGreen("#55FFFF"));
        assertFalse(CustomMenuActionSupport.isGreen("#AAAAAA"));
        assertFalse(CustomMenuActionSupport.isGreen("#003300"));
        assertFalse(CustomMenuActionSupport.isGreen("notacolor"));
        assertFalse(CustomMenuActionSupport.isGreen(""));
        assertFalse(CustomMenuActionSupport.isGreen(null));
    }

    @Test
    void loginButtonFallsBackToThePositiveWord() {
        CustomMenuButton picked = CustomMenuActionSupport.loginButton(List.of(
            button(1, "Alpha", "alpha"),
            button(2, "Register", "register_submit")));
        assertEquals("Register", picked.label());
    }

    @Test
    void loginButtonFallsBackToTheFirstNonNegativeWhereAutoSubmitGivesUp() {
        List<CustomMenuButton> ambiguous = List.of(button(1, "Alpha", "alpha"), button(2, "Beta", "beta"));

        assertNull(CustomMenuActionSupport.autoSubmitButton(ambiguous));
        assertEquals("Alpha", CustomMenuActionSupport.loginButton(ambiguous).label());
    }

    @Test
    void loginButtonSkipsCancelEvenWhenItIsGreen() {
        CustomMenuButton picked = CustomMenuActionSupport.loginButton(List.of(
            colored(1, "Cancel", "cancel", "green"),
            button(2, "Alpha", "alpha")));
        assertEquals("Alpha", picked.label());
    }

    @Test
    void loginButtonStillPressesSomethingWhenEveryButtonLooksNegative() {

        CustomMenuButton picked = CustomMenuActionSupport.loginButton(List.of(
            button(1, "Back", "back"), button(2, "Quit", "quit")));
        assertEquals("Back", picked.label());
    }

    @Test
    void loginButtonReturnsNullOnlyWhenNothingCouldBeSent() {
        assertNull(CustomMenuActionSupport.loginButton(List.of(
            new CustomMenuButton(1, "Website", "", CustomMenuButton.Kind.URL),
            new CustomMenuButton(2, "Copy", "", CustomMenuButton.Kind.CLIPBOARD))));
        assertNull(CustomMenuActionSupport.loginButton(List.of()));
        assertNull(CustomMenuActionSupport.loginButton(null));
    }

    @Test
    void loginButtonIgnoresNonServerButtonsWhenRanking() {
        CustomMenuButton picked = CustomMenuActionSupport.loginButton(List.of(
            new CustomMenuButton(1, "Website", "", CustomMenuButton.Kind.URL, "green"),
            button(2, "Alpha", "alpha")));
        assertEquals("Alpha", picked.label());
    }
}
