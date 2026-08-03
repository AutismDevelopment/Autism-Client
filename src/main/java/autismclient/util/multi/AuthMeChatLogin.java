package autismclient.util.multi;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AuthMeChatLogin {
    private AuthMeChatLogin() {}

    enum Kind { NONE, REGISTER, LOGIN }

    record Detection(Kind kind, String command, int passwordArgs) {
        static final Detection NONE = new Detection(Kind.NONE, "", 0);

        String commandLine(String password) {
            String line = command + " " + password;
            return passwordArgs >= 2 ? line + " " + password : line;
        }
    }

    private static final Pattern REGISTER = Pattern.compile("(?i)/reg(?:ister)?\\b");

    private static final Pattern LOGIN = Pattern.compile("(?i)/log(?:in)?\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]{1,32}>");

    static Detection detect(String rawLine) {
        if (rawLine == null) return Detection.NONE;
        String line = rawLine.trim();
        if (line.isEmpty()) return Detection.NONE;

        Matcher reg = REGISTER.matcher(line);
        if (reg.find()) {
            String command = line.substring(reg.start(), reg.end()).toLowerCase(Locale.ROOT);
            return new Detection(Kind.REGISTER, command, registerPasswordArgs(line, reg.end()));
        }
        Matcher log = LOGIN.matcher(line);
        if (log.find()) {
            return new Detection(Kind.LOGIN, line.substring(log.start(), log.end()).toLowerCase(Locale.ROOT), 1);
        }
        return Detection.NONE;
    }

    private static int registerPasswordArgs(String line, int afterCommand) {
        String tail = line.substring(afterCommand);
        Matcher ph = PLACEHOLDER.matcher(tail);
        int count = 0;
        boolean email = false;
        while (ph.find()) {
            count++;
            String token = ph.group().toLowerCase(Locale.ROOT);
            if (token.contains("mail") || token.contains("correo") || token.contains("courriel")) email = true;
        }
        if (email) return 1;
        if (count == 1) return 1;
        return 2;
    }
}
