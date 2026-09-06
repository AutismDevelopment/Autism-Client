package autismclient.util.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AutoLoginEngineTest {

    private static final long T0 = 1_000_000L;

    private static final class FakeHost implements AutoLoginHost {
        final List<String> sent = new ArrayList<>();
        final List<CustomMenuSubmission> submitted = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        int needsPasswordCalls;
        String password = "hunter2";
        boolean spawned = true;
        boolean canSend = true;
        CustomMenuSnapshot menu;
        boolean ownedElsewhere;
        boolean submitSucceeds = true;
        boolean sendSucceeds = true;

        @Override public String password() { return password; }
        @Override public boolean spawnedInWorld() { return spawned; }
        @Override public boolean canSendChat() { return canSend; }
        @Override public CustomMenuSnapshot customMenu() { return menu; }

        @Override public boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
            if (!submitSucceeds) return false;
            submitted.add(submission);
            return true;
        }

        @Override public boolean sendCommandLine(String line) {
            if (!sendSucceeds) return false;
            sent.add(line);
            return true;
        }

        @Override public boolean screenOwnedElsewhere() { return ownedElsewhere; }
        @Override public void note(String message) { notes.add(message); }
        @Override public void needsPassword(String context) { needsPasswordCalls++; }
    }

    private static final class Driver {
        private final AutoLoginEngine engine;
        private long now = T0;

        Driver(AutoLoginEngine engine) {
            this.engine = engine;
        }

        void advanceTo(long target) {
            while (now < target) {
                now = Math.min(target, now + 50L);
                engine.tick(now);
            }
        }
    }

    private static AutoLoginConfig config() {
        return new AutoLoginConfig(10_000L, 2_000L, 2_500L, 4, 1_000L, 3_000L);
    }

    private static AutoLoginEngine armed(FakeHost host) {
        return armed(host, config());
    }

    private static AutoLoginEngine armed(FakeHost host, AutoLoginConfig config) {
        AutoLoginEngine engine = new AutoLoginEngine(host);
        engine.configure(config);
        engine.reset(T0);
        return engine;
    }

    private static CustomMenuInput text(int index, String key) {
        return new CustomMenuInput(index, key, key, CustomMenuInput.Kind.TEXT, "", 128, 0, 0, 0, List.of());
    }

    private static CustomMenuInput toggle(int index, String key, String initial) {
        return new CustomMenuInput(index, key, key, CustomMenuInput.Kind.BOOLEAN, initial, 0, 0, 0, 0, List.of());
    }

    private static CustomMenuButton button(int index, String label) {
        return new CustomMenuButton(index, label, label.toLowerCase(Locale.ROOT), CustomMenuButton.Kind.CUSTOM);
    }

    private static CustomMenuSnapshot screen(long generation, List<CustomMenuInput> inputs,
                                             List<CustomMenuButton> buttons) {
        return new CustomMenuSnapshot("test", "CONFIGURATION", generation, "Login", inputs, buttons, null);
    }

    @Test
    void loginPromptIsAnsweredOnlyAfterTheChatDelay() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");

        driver.advanceTo(T0 + 1_900);
        assertTrue(host.sent.isEmpty(), "must not fire the instant the prompt lands");

        driver.advanceTo(T0 + 2_100);
        assertEquals(List.of("/login hunter2"), host.sent);
    }

    @Test
    void theChatDelayIsMeasuredFromSpawningNotFromDetection() {
        FakeHost host = new FakeHost();
        host.spawned = false;
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");

        driver.advanceTo(T0 + 4_000);
        assertTrue(host.sent.isEmpty(), "not spawned yet, so the delay has not even started");

        host.spawned = true;
        driver.advanceTo(T0 + 5_900);
        assertTrue(host.sent.isEmpty(), "the delay runs from the spawn, not from the prompt");
        driver.advanceTo(T0 + 6_100);
        assertEquals(List.of("/login hunter2"), host.sent);
    }

    @Test
    void backToBackPromptsCollapseToOneSend() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Use /login <password>");
        driver.advanceTo(T0 + 2_100);
        engine.onChatLine("Use /login <password>");
        driver.advanceTo(T0 + 2_600);
        assertEquals(1, host.sent.size(), "the resend throttle collapses them");
    }

    @Test
    void registerPromptSendsThePasswordTwice() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Register with /register <password> <ConfirmPassword>");
        driver.advanceTo(T0 + 2_100);
        assertEquals(List.of("/register hunter2 hunter2"), host.sent);
        assertEquals(AutoLoginEngine.Phase.REGISTERING, engine.phase());
    }

    @Test
    void registerThenAPromptedLoginSendsBothInOrder() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Register with /register <password> <ConfirmPassword>");
        driver.advanceTo(T0 + 2_100);

        engine.onChatLine("Now login with /login <password>");
        driver.advanceTo(T0 + 2_300);
        assertEquals(1, host.sent.size(), "the resend throttle still applies");
        driver.advanceTo(T0 + 4_700);
        assertEquals(List.of("/register hunter2 hunter2", "/login hunter2"), host.sent);
    }

    @Test
    void registerWithNoFollowUpStillSendsALogin() {

        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Register with /register <password> <ConfirmPassword>");
        driver.advanceTo(T0 + 2_100);

        driver.advanceTo(T0 + 4_900);
        assertEquals(1, host.sent.size(), "not yet - the follow-up wait is 3s from the register");
        driver.advanceTo(T0 + 5_300);
        assertEquals(List.of("/register hunter2 hunter2", "/login hunter2"), host.sent);
        assertEquals(AutoLoginEngine.Phase.LOGGING_IN, engine.phase());
    }

    @Test
    void aRealLoginPromptCancelsTheSpeculativeOne() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Register with /register <password> <ConfirmPassword>");
        driver.advanceTo(T0 + 2_100);
        engine.onChatLine("Now login with /login <password>");
        driver.advanceTo(T0 + 4_700);

        driver.advanceTo(T0 + 6_500);
        assertEquals(2, host.sent.size(), "exactly one login, not two");
    }

    @Test
    void theSpeculativeLoginKeepsTheServersAbbreviation() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Registrierung: /reg <passwort> <passwort>");
        driver.advanceTo(T0 + 2_100);
        driver.advanceTo(T0 + 5_300);
        assertEquals(List.of("/reg hunter2 hunter2", "/log hunter2"), host.sent);
    }

    @Test
    void goingQuietAfterTheAnswerCountsAsSuccessAndLatches() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 2_100);
        driver.advanceTo(T0 + 4_800);
        assertEquals(AutoLoginEngine.Phase.DONE, engine.phase());
        assertTrue(engine.finished());

        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 6_000);
        assertEquals(1, host.sent.size());
    }

    @Test
    void aRePromptBeforeTheSettleIsAWrongPasswordAndRetries() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 2_100);

        engine.onChatLine("Wrong password! /login <password>");
        driver.advanceTo(T0 + 4_700);
        assertEquals(2, host.sent.size());
        assertFalse(engine.finished());
    }

    @Test
    void aWrongPasswordStopsAtTheAttemptCapWithOneNote() {
        FakeHost host = new FakeHost();

        AutoLoginEngine engine = armed(host, new AutoLoginConfig(60_000L, 2_000L, 2_500L, 4, 1_000L, 3_000L));
        Driver driver = new Driver(engine);
        long target = T0 + 2_100;
        for (int i = 0; i < 8; i++) {
            engine.onChatLine("Wrong password! /login <password>");
            driver.advanceTo(target);
            target += 2_600;
        }
        assertEquals(4, host.sent.size(), "capped at maxChatAttempts");
        assertEquals(1, host.notes.size(), "exactly one give-up message, never spam");
        assertEquals(AutoLoginEngine.Phase.GAVE_UP, engine.phase());
    }

    @Test
    void theWindowExpiringStopsEverything() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        driver.advanceTo(T0 + 10_100);
        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 12_000);
        assertTrue(host.sent.isEmpty());
        assertEquals(AutoLoginEngine.Phase.GAVE_UP, engine.phase());
    }

    @Test
    void withNoPasswordNothingIsSentAndTheUserIsAskedOnce() {
        FakeHost host = new FakeHost();
        host.password = "";
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 4_000);
        assertTrue(host.sent.isEmpty());
        assertEquals(1, host.needsPasswordCalls, "asked once, not once per tick");
    }

    @Test
    void aFailedSendDoesNotBurnAnAttempt() {
        FakeHost host = new FakeHost();
        host.sendSucceeds = false;
        AutoLoginEngine engine = armed(host);
        Driver driver = new Driver(engine);
        engine.onChatLine("Please login with /login <password>");
        driver.advanceTo(T0 + 2_100);
        assertEquals(0, engine.chatAttempts(), "a send that never left must not cost an attempt");

        host.sendSucceeds = true;
        driver.advanceTo(T0 + 2_500);
        assertEquals(List.of("/login hunter2"), host.sent);
        assertEquals(1, engine.chatAttempts());
    }

    @Test
    void resetReArmsEverythingForTheNextConnection() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        Driver first = new Driver(engine);
        first.advanceTo(T0 + 10_100);
        assertEquals(AutoLoginEngine.Phase.GAVE_UP, engine.phase());

        long t1 = T0 + 50_000;
        engine.reset(t1);
        engine.onChatLine("Please login with /login <password>");
        for (long t = t1; t <= t1 + 2_100; t += 50L) engine.tick(t);
        assertEquals(List.of("/login hunter2"), host.sent);
    }

    @Test
    void aPromptArrivingBeforeTheFirstTickIsNotLost() {

        FakeHost host = new FakeHost();
        AutoLoginEngine engine = new AutoLoginEngine(host);
        engine.configure(config());
        engine.reset(T0);
        engine.onChatLine("Please login with /login <password>");

        Driver driver = new Driver(engine);
        driver.advanceTo(T0 + 2_100);
        assertEquals(List.of("/login hunter2"), host.sent);
    }

    @Test
    void screensAreAnsweredImmediatelyEvenBeforeSpawning() {
        FakeHost host = new FakeHost();
        host.spawned = false;
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));

        engine.tick(T0);
        assertEquals(1, host.submitted.size(), "a screen gates the connection - no delay");
        assertEquals("hunter2", host.submitted.get(0).values().get("password"));
    }

    @Test
    void everyTextFieldGetsThePasswordAndOtherInputsKeepTheirValue() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1,
            List.of(text(1, "password"), text(2, "confirm"), toggle(3, "remember", "true")),
            List.of(button(1, "Register")));

        engine.tick(T0);
        CustomMenuSubmission submission = host.submitted.get(0);
        assertEquals("hunter2", submission.values().get("password"));
        assertEquals("hunter2", submission.values().get("confirm"));
        assertEquals("true", submission.values().get("remember"));
    }

    @Test
    void theSameScreenIsNeverPressedTwice() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));
        new Driver(engine).advanceTo(T0 + 1_000);
        assertEquals(1, host.submitted.size());
    }

    @Test
    void anAcceptPromptIsClickedButIsNotTheLoginAndTheGateBehindItIsStillAnswered() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);

        host.menu = screen(1, List.of(), List.of(button(1, "Decline"), button(2, "Accept")));
        engine.tick(T0);
        assertEquals(1, host.submitted.size());
        assertEquals("Accept", host.submitted.get(0).button().label());
        assertEquals(AutoLoginEngine.Phase.WATCHING, engine.phase(), "accepting rules is not logging in");

        host.menu = null;
        engine.tick(T0 + 100);
        host.menu = screen(2, List.of(text(1, "password")), List.of(button(1, "Login")));
        engine.tick(T0 + 200);
        assertEquals(2, host.submitted.size(), "the real gate behind the notice must still be answered");
    }

    @Test
    void aScreenOwnedByAMacroIsLeftAlone() {
        FakeHost host = new FakeHost();
        host.ownedElsewhere = true;
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));
        new Driver(engine).advanceTo(T0 + 1_000);
        assertTrue(host.submitted.isEmpty());
    }

    @Test
    void aFailedSubmitIsRetriedAfterTheBackoff() {
        FakeHost host = new FakeHost();
        host.submitSucceeds = false;
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));
        engine.tick(T0);
        assertTrue(host.submitted.isEmpty());

        host.submitSucceeds = true;
        engine.tick(T0 + 500);
        assertTrue(host.submitted.isEmpty(), "still inside the backoff");
        engine.tick(T0 + 1_000);
        assertEquals(1, host.submitted.size());
    }

    @Test
    void chatIsNotSentWhileAScreenIsOpen() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));
        engine.onChatLine("Please login with /login <password>");
        new Driver(engine).advanceTo(T0 + 3_000);
        assertTrue(host.sent.isEmpty(), "the screen owns the connection");
        assertEquals(1, host.submitted.size());
    }

    @Test
    void aScreenWithNothingPressableIsSkippedRatherThanCrashing() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")),
            List.of(new CustomMenuButton(1, "Website", "", CustomMenuButton.Kind.URL)));
        new Driver(engine).advanceTo(T0 + 3_000);
        assertTrue(host.submitted.isEmpty());
        assertFalse(engine.finished());
    }

    @Test
    void aTwoNeutralButtonGateIsAnsweredRatherThanSkipped() {

        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")),
            List.of(button(1, "Alpha"), button(2, "Beta")));
        engine.tick(T0);
        assertEquals(1, host.submitted.size());
        assertEquals("Alpha", host.submitted.get(0).button().label());
    }

    @Test
    void aGreenButtonWinsOverAPlainOne() {
        FakeHost host = new FakeHost();
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(
            button(1, "Alpha"),
            new CustomMenuButton(2, "Beta", "beta", CustomMenuButton.Kind.CUSTOM, "green")));
        engine.tick(T0);
        assertEquals("Beta", host.submitted.get(0).button().label());
    }

    @Test
    void aScreenWithoutAPasswordAsksOnceAndSendsNothing() {
        FakeHost host = new FakeHost();
        host.password = "";
        AutoLoginEngine engine = armed(host);
        host.menu = screen(1, List.of(text(1, "password")), List.of(button(1, "Login")));
        new Driver(engine).advanceTo(T0 + 1_000);
        assertTrue(host.submitted.isEmpty());
        assertEquals(1, host.needsPasswordCalls);
    }
}
