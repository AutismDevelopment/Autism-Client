package autismclient.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class SnbtRepair {
    private SnbtRepair() {}

    private enum Kind { OPEN, CLOSE, COMMA, COLON, SEMI, ATOM, STRING }

    private record Tok(Kind kind, String text, int line, int lineIndent) {}

    public static String repair(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        try {
            return repairTokens(tokenize(raw));
        } catch (Throwable t) {
            return raw.trim();
        }
    }

    private static String repairTokens(List<Tok> in) {

        int deficit = 0;
        for (Tok t : in) deficit += t.kind == Kind.OPEN ? 1 : t.kind == Kind.CLOSE ? -1 : 0;

        List<Tok> out = new ArrayList<>(in.size() + 8);
        ArrayDeque<Tok> open = new ArrayDeque<>();
        Tok prev = null;
        for (Tok t : in) {

            if (deficit > 0 && t.kind != Kind.CLOSE && prev != null && t.line > prev.line) {
                while (deficit > 0 && !open.isEmpty() && open.peek().line < t.line
                        && t.lineIndent <= open.peek().lineIndent) {
                    insertCloser(out, open.pop());
                    deficit--;
                }
                prev = last(out);
            }
            switch (t.kind) {
                case OPEN -> {
                    maybeComma(out, prev, t);
                    open.push(t);
                    out.add(t);
                }
                case CLOSE -> {
                    if (open.isEmpty()) continue;
                    dropTrailingComma(out);
                    Tok opener = open.pop();

                    out.add(new Tok(Kind.CLOSE, opener.text.equals("{") ? "}" : "]", t.line, t.lineIndent));
                }
                case COMMA -> {
                    Tok lastTok = last(out);
                    if (lastTok == null || lastTok.kind == Kind.OPEN || lastTok.kind == Kind.COMMA
                        || lastTok.kind == Kind.COLON || lastTok.kind == Kind.SEMI) continue;
                    out.add(t);
                }
                default -> {
                    maybeComma(out, prev, t);
                    out.add(t);
                }
            }
            prev = last(out);
        }
        dropTrailingComma(out);
        while (!open.isEmpty()) {
            dropTrailingComma(out);
            insertCloser(out, open.pop());
        }
        StringBuilder sb = new StringBuilder();
        for (Tok t : out) sb.append(t.text);
        return sb.isEmpty() ? "{}" : sb.toString();
    }

    private static void maybeComma(List<Tok> out, Tok prev, Tok t) {
        if (prev == null) return;
        boolean prevEnds = prev.kind == Kind.ATOM || prev.kind == Kind.STRING || prev.kind == Kind.CLOSE;
        boolean starts = t.kind == Kind.ATOM || t.kind == Kind.STRING || t.kind == Kind.OPEN;
        if (prevEnds && starts) out.add(new Tok(Kind.COMMA, ",", t.line, t.lineIndent));
    }

    private static void insertCloser(List<Tok> out, Tok opener) {
        Tok trailing = null;
        if (!out.isEmpty() && out.get(out.size() - 1).kind == Kind.COMMA) trailing = out.remove(out.size() - 1);
        out.add(new Tok(Kind.CLOSE, opener.text.equals("{") ? "}" : "]", opener.line, opener.lineIndent));
        if (trailing != null) out.add(trailing);
    }

    private static void dropTrailingComma(List<Tok> out) {
        if (!out.isEmpty() && out.get(out.size() - 1).kind == Kind.COMMA) out.remove(out.size() - 1);
    }

    private static Tok last(List<Tok> out) {
        return out.isEmpty() ? null : out.get(out.size() - 1);
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int line = 0;
        int lineIndent = 0;
        boolean measuring = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') { line++; lineIndent = 0; measuring = true; continue; }
            if (measuring) {
                if (c == ' ') { lineIndent++; continue; }
                if (c == '\t') { lineIndent += 4; continue; }
                measuring = false;
            }
            if (Character.isWhitespace(c)) continue;
            if (c == '"' || c == '\'') {
                int j = i + 1;
                boolean esc = false;
                while (j < s.length()) {
                    char d = s.charAt(j);
                    if (esc) esc = false;
                    else if (d == '\\') esc = true;
                    else if (d == c) break;
                    if (d == '\n') line++;
                    j++;
                }
                String body = j < s.length() ? s.substring(i, j + 1)
                                             : s.substring(i) + c;
                out.add(new Tok(Kind.STRING, body, line, lineIndent));
                i = Math.min(j, s.length() - 1);
                continue;
            }
            switch (c) {
                case '{', '[' -> out.add(new Tok(Kind.OPEN, String.valueOf(c), line, lineIndent));
                case '}', ']' -> out.add(new Tok(Kind.CLOSE, String.valueOf(c), line, lineIndent));
                case ',' -> out.add(new Tok(Kind.COMMA, ",", line, lineIndent));
                case ':' -> out.add(new Tok(Kind.COLON, ":", line, lineIndent));
                case ';' -> out.add(new Tok(Kind.SEMI, ";", line, lineIndent));
                default -> {
                    int j = i;
                    while (j < s.length() && !Character.isWhitespace(s.charAt(j))
                            && "{}[],:;\"'".indexOf(s.charAt(j)) < 0) j++;
                    out.add(new Tok(Kind.ATOM, s.substring(i, j), line, lineIndent));
                    i = j - 1;
                }
            }
        }
        return out;
    }
}
