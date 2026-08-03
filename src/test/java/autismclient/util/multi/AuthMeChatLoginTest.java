package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthMeChatLoginTest {

    private static AuthMeChatLogin.Detection detect(String line) {
        return AuthMeChatLogin.detect(line);
    }

    @Test
    void englishRegisterRequestIsRegisterWithConfirmation() {
        AuthMeChatLogin.Detection d = detect("Please, register to the server with the command: /register <password> <ConfirmPassword>");
        assertEquals(AuthMeChatLogin.Kind.REGISTER, d.kind());
        assertEquals("/register", d.command());
        assertEquals(2, d.passwordArgs());
        assertEquals("/register hunter2 hunter2", d.commandLine("hunter2"));
    }

    @Test
    void englishLoginRequestIsLoginWithOnePassword() {
        AuthMeChatLogin.Detection d = detect("Please, login with the command: /login <password>");
        assertEquals(AuthMeChatLogin.Kind.LOGIN, d.kind());
        assertEquals("/login", d.command());
        assertEquals(1, d.passwordArgs());
        assertEquals("/login hunter2", d.commandLine("hunter2"));
    }

    @Test
    void germanPrompts() {
        assertEquals(AuthMeChatLogin.Kind.REGISTER,
            detect("Bitte registriere dich mit \"/register <passwort> <passwortBestaetigen>\"").kind());
        AuthMeChatLogin.Detection login = detect("Bitte logge dich ein mit \"/login <passwort>\"");
        assertEquals(AuthMeChatLogin.Kind.LOGIN, login.kind());
        assertEquals("/login", login.command());
    }

    @Test
    void russianUsesRegAbbreviation() {
        AuthMeChatLogin.Detection d = detect("Registration: /reg <password> <repeat password>");
        assertEquals(AuthMeChatLogin.Kind.REGISTER, d.kind());
        assertEquals("/reg", d.command());
        assertEquals(2, d.passwordArgs());
        assertEquals("/reg pw pw", d.commandLine("pw"));
    }

    @Test
    void spanishLoginWithoutAnglePlaceholders() {

        AuthMeChatLogin.Detection d = detect("Inicia sesion con \"/login contrasena\"");
        assertEquals(AuthMeChatLogin.Kind.LOGIN, d.kind());
        assertEquals(1, d.passwordArgs());
    }

    @Test
    void spanishRegisterConfirmation() {
        AuthMeChatLogin.Detection d = detect("Por favor, registrate con \"/register <contrasena> <confirmarContrasena>\"");
        assertEquals(AuthMeChatLogin.Kind.REGISTER, d.kind());
        assertEquals(2, d.passwordArgs());
    }

    @Test
    void registerUsageAlsoAnswered() {

        assertEquals(AuthMeChatLogin.Kind.REGISTER, detect("Usage: /register <password> <ConfirmPassword>").kind());
        assertEquals(AuthMeChatLogin.Kind.LOGIN, detect("Usage: /login <password>").kind());
    }

    @Test
    void emailRegistrationSendsSinglePassword() {
        AuthMeChatLogin.Detection d = detect("Please register with /register <password> <email>");
        assertEquals(AuthMeChatLogin.Kind.REGISTER, d.kind());
        assertEquals(1, d.passwordArgs());
        assertEquals("/register pw", d.commandLine("pw"));
    }

    @Test
    void singlePlaceholderRegisterHasNoConfirmation() {
        AuthMeChatLogin.Detection d = detect("Register now: /register <password>");
        assertEquals(1, d.passwordArgs());
    }

    @Test
    void unrelatedChatIsIgnored() {
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("Welcome to the server!").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("Steve joined the game").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect(null).kind());
    }

    @Test
    void similarCommandsDoNotFalseTrigger() {
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("Type /logout to leave your session").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("See /logs for details").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("Warp to /region spawn").kind());
        assertEquals(AuthMeChatLogin.Kind.NONE, detect("Registered players list below").kind());
    }

    @Test
    void registerTakesPriorityWhenBothTokensPresent() {

        AuthMeChatLogin.Detection d = detect("Use /register <pw> <pw> if new, otherwise /login <pw>");
        assertEquals(AuthMeChatLogin.Kind.REGISTER, d.kind());
    }

    @Test
    void colorCodePrefixDoesNotBlockDetection() {

        assertEquals(AuthMeChatLogin.Kind.LOGIN, detect("§cPlease, login with the command: §f/login <password>").kind());
    }
}
