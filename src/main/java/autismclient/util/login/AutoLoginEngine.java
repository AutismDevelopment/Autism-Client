package autismclient.util.login;

import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;
import autismclient.util.macro.CustomMenuActionSupport;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AutoLoginEngine {

    public enum Phase {

        IDLE,

        WATCHING,

        REGISTERING,

        LOGGING_IN,

        DONE,

        GAVE_UP
    }

    private final AutoLoginHost host;

    private volatile AuthMeChatLogin.Detection pendingChat = AuthMeChatLogin.Detection.NONE;
    private volatile long resetRequestedAt;
    private volatile AutoLoginConfig config = AutoLoginConfig.playerDefaults();
    private volatile Phase phase = Phase.IDLE;

    private long startedAt;
    private long spawnedAt;
    private long nextChatSendAt;
    private long screenRetryAt;
    private long settleAt;
    private long owedLoginAt;
    private boolean owedLogin;
    private int chatAttempts;
    private boolean gaveUpNoted;
    private boolean passwordAsked;
    private long answeredGen = Long.MIN_VALUE;
    private String registerToken = "";
    private String loginToken = "";

    public AutoLoginEngine(AutoLoginHost host) {
        this.host = host;
    }

    public void configure(AutoLoginConfig newConfig) {
        if (newConfig != null) config = newConfig;
    }

    public void reset(long now) {
        pendingChat = AuthMeChatLogin.Detection.NONE;
        resetRequestedAt = now == 0L ? 1L : now;
    }

    public void onChatLine(String line) {
        AuthMeChatLogin.Detection detection = AuthMeChatLogin.detect(line);
        if (detection.kind() != AuthMeChatLogin.Kind.NONE) pendingChat = detection;
    }

    public Phase phase() {
        return phase;
    }

    public boolean finished() {
        Phase current = phase;
        return current == Phase.DONE || current == Phase.GAVE_UP;
    }

    public int chatAttempts() {
        return chatAttempts;
    }

    public void tick(long now) {
        applyPendingReset();
        if (phase == Phase.IDLE || finished()) return;
        if (now - startedAt >= config.windowMs()) {
            phase = Phase.GAVE_UP;
            return;
        }
        if (tickScreen(now)) return;
        tickChat(now);
    }

    private void applyPendingReset() {
        long requested = resetRequestedAt;
        if (requested == 0L) return;
        resetRequestedAt = 0L;
        startedAt = requested;
        spawnedAt = 0L;
        nextChatSendAt = 0L;
        screenRetryAt = 0L;
        settleAt = 0L;
        owedLoginAt = 0L;
        owedLogin = false;
        chatAttempts = 0;
        gaveUpNoted = false;
        passwordAsked = false;
        answeredGen = Long.MIN_VALUE;
        registerToken = "";
        loginToken = "";

        phase = Phase.WATCHING;
    }

    private boolean tickScreen(long now) {
        CustomMenuSnapshot snapshot = host.customMenu();
        if (snapshot == null) {

            answeredGen = Long.MIN_VALUE;
            return false;
        }
        if (host.screenOwnedElsewhere()) return true;
        if (snapshot.generation() == answeredGen) return true;
        if (now < screenRetryAt) return true;

        boolean hasText = false;
        for (CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == CustomMenuInput.Kind.TEXT) {
                hasText = true;
                break;
            }
        }
        String password = host.password();
        if (hasText && password.isEmpty()) {
            askForPassword(snapshot.title());
            return true;
        }

        CustomMenuButton button;
        Map<String, String> values;
        try {

            CustomMenuButton accept = hasText ? null
                : CustomMenuActionSupport.acceptButton(snapshot.buttons());
            button = accept != null ? accept : CustomMenuActionSupport.loginButton(snapshot.buttons());
            if (button == null) {

                screenRetryAt = now + config.screenRetryMs();
                return true;
            }
            values = new LinkedHashMap<>();
            for (CustomMenuInput input : snapshot.inputs()) {
                values.put(input.key(), input.kind() == CustomMenuInput.Kind.TEXT ? password : input.initialValue());
            }
        } catch (RuntimeException error) {
            screenRetryAt = now + config.screenRetryMs();
            return true;
        }

        if (!host.submitCustomMenu(snapshot, new CustomMenuSubmission(values, button))) {
            screenRetryAt = now + config.screenRetryMs();
            return true;
        }
        answeredGen = snapshot.generation();
        if (hasText) {

            phase = Phase.LOGGING_IN;
            settleAt = now + config.chatResendMs();
        }
        return true;
    }

    private void tickChat(long now) {
        if (!host.spawnedInWorld()) {
            spawnedAt = 0L;
            return;
        }
        if (spawnedAt == 0L) spawnedAt = now;
        if (now - spawnedAt < config.chatDelayMs()) return;

        AuthMeChatLogin.Detection detection = pendingChat;
        if (detection.kind() == AuthMeChatLogin.Kind.NONE) {

            if (owedLogin && now >= owedLoginAt) {
                sendSpeculativeLogin(now);
            } else if (phase == Phase.LOGGING_IN && settleAt != 0L && now >= settleAt) {
                phase = Phase.DONE;
            }
            return;
        }
        if (now < nextChatSendAt) return;
        if (chatAttempts >= config.maxChatAttempts()) {
            giveUp();
            return;
        }
        String password = host.password();
        if (password.isEmpty()) {
            askForPassword("AuthMe login");
            return;
        }
        if (!host.canSendChat()) return;

        pendingChat = AuthMeChatLogin.Detection.NONE;
        if (!host.sendCommandLine(detection.commandLine(password))) {

            pendingChat = detection;
            nextChatSendAt = now + 250L;
            return;
        }
        chatAttempts++;
        nextChatSendAt = now + config.chatResendMs();
        if (detection.kind() == AuthMeChatLogin.Kind.REGISTER) {
            registerToken = detection.command();
            phase = Phase.REGISTERING;
            owedLogin = true;
            owedLoginAt = now + config.registerFollowUpMs();
        } else {
            loginToken = detection.command();
            phase = Phase.LOGGING_IN;
            owedLogin = false;
            settleAt = now + config.chatResendMs();
        }
    }

    private void sendSpeculativeLogin(long now) {
        owedLogin = false;
        if (chatAttempts >= config.maxChatAttempts()) {
            giveUp();
            return;
        }
        String password = host.password();
        if (password.isEmpty() || !host.canSendChat()) return;
        String token = AuthMeChatLogin.loginCommandFor(registerToken, loginToken);
        if (!host.sendCommandLine(token + " " + password)) {
            owedLogin = true;
            owedLoginAt = now + 250L;
            return;
        }
        chatAttempts++;
        nextChatSendAt = now + config.chatResendMs();
        phase = Phase.LOGGING_IN;
        settleAt = now + config.chatResendMs();
    }

    private void giveUp() {
        if (!gaveUpNoted) {
            gaveUpNoted = true;
            host.note("Auto login failed after " + config.maxChatAttempts() + " attempts - check the password.");
        }
        phase = Phase.GAVE_UP;
    }

    private void askForPassword(String context) {
        if (passwordAsked) return;
        passwordAsked = true;
        host.needsPassword(context == null ? "" : context);
    }
}
