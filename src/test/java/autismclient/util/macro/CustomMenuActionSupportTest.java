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
}
