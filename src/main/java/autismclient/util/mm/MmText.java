package autismclient.util.mm;

public final class MmText {
    private MmText() {}

    public static String clean(String s, int maxLen) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(Math.min(s.length(), maxLen));
        for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
            char c = s.charAt(i);
            if (c == '§') continue;
            if (c < 0x20 || c == 0x7F) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    public static String clean(String s) { return clean(s, 256); }
}
