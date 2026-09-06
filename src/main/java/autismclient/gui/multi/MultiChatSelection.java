package autismclient.gui.multi;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MultiChatSelection {

    public record Row(long seq, int lineIndex, String text) {
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean active;
    private boolean dragging;
    private long anchorSeq;
    private long focusSeq;
    private int anchorLine;
    private int focusLine;
    private int anchorChar;
    private int focusChar;

    public void setRows(List<Row> current) {
        rows.clear();
        if (current != null) rows.addAll(current);
    }

    public void begin(long seq, int lineIndex, int charIndex) {
        anchorSeq = focusSeq = seq;
        anchorLine = focusLine = lineIndex;
        anchorChar = focusChar = charIndex;
        dragging = true;
        active = false;
    }

    public void extend(long seq, int lineIndex, int charIndex) {
        if (!dragging) return;
        focusSeq = seq;
        focusLine = lineIndex;
        focusChar = charIndex;
        if (seq != anchorSeq || lineIndex != anchorLine || charIndex != anchorChar) active = true;
    }

    public void finishDrag() {
        dragging = false;
    }

    public boolean dragging() {
        return dragging;
    }

    public boolean hasSelection() {
        return active;
    }

    public void clear() {
        active = false;
        dragging = false;
    }

    private int ordinal(long seq, int lineIndex) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.seq() == seq && row.lineIndex() == lineIndex) return i;
        }
        return -1;
    }

    public int[] rangeFor(long seq, int lineIndex, int rowLength) {
        if (!active) return null;
        long[] bounds = orderedBounds();
        if (bounds == null) return null;
        int loOrd = (int) bounds[0], loChar = (int) bounds[1], hiOrd = (int) bounds[2], hiChar = (int) bounds[3];
        int rOrd = ordinal(seq, lineIndex);
        if (rOrd < 0 || rOrd < loOrd || rOrd > hiOrd) return null;
        int start = rOrd == loOrd ? loChar : 0;
        int end = rOrd == hiOrd ? hiChar : rowLength;
        start = Math.max(0, Math.min(start, rowLength));
        end = Math.max(start, Math.min(end, rowLength));
        return new int[]{start, end};
    }

    public String selectedText() {
        if (!active) return "";
        long[] bounds = orderedBounds();
        if (bounds == null) return "";
        int loOrd = (int) bounds[0], loChar = (int) bounds[1], hiOrd = (int) bounds[2], hiChar = (int) bounds[3];
        StringBuilder sb = new StringBuilder();
        for (int o = loOrd; o <= hiOrd; o++) {
            String t = rows.get(o).text();
            int start = o == loOrd ? Math.min(loChar, t.length()) : 0;
            int end = o == hiOrd ? Math.min(hiChar, t.length()) : t.length();
            if (o > loOrd) sb.append('\n');
            sb.append(t, Math.max(0, Math.min(start, end)), Math.max(start, end));
        }
        return sb.toString();
    }

    private long[] orderedBounds() {
        int aOrd = ordinal(anchorSeq, anchorLine);
        int fOrd = ordinal(focusSeq, focusLine);
        if (aOrd < 0 || fOrd < 0) return null;
        if (aOrd < fOrd || (aOrd == fOrd && anchorChar <= focusChar)) {
            return new long[]{aOrd, anchorChar, fOrd, focusChar};
        }
        return new long[]{fOrd, focusChar, aOrd, anchorChar};
    }

    public static int widthOfStyled(Font font, FormattedText line, int chars) {
        if (font == null || line == null || chars <= 0) return 0;
        return font.width(prefixStyled(line, chars));
    }

    public static int charIndexAtStyled(Font font, FormattedText line, int relativeX) {
        if (font == null || line == null || relativeX <= 0) return 0;
        int[] index = {0};
        float[] acc = {0f};
        int[] result = {-1};
        line.visit((style, content) -> {
            for (int i = 0; i < content.length(); i++) {
                float cw = font.width(FormattedText.of(content.substring(i, i + 1), style));
                if (relativeX < acc[0] + cw / 2f) {
                    result[0] = index[0];
                    return Optional.of(Boolean.TRUE);
                }
                acc[0] += cw;
                index[0]++;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result[0] >= 0 ? result[0] : index[0];
    }

    private static FormattedText prefixStyled(FormattedText line, int chars) {
        List<FormattedText> parts = new ArrayList<>();
        int[] remaining = {chars};
        line.visit((style, content) -> {
            int take = Math.min(remaining[0], content.length());
            if (take > 0) {
                parts.add(FormattedText.of(content.substring(0, take), style));
                remaining[0] -= take;
            }
            return remaining[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return FormattedText.composite(parts);
    }

    public static String plain(FormattedText text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        text.visit(part -> {
            sb.append(part);
            return Optional.empty();
        });
        return sb.toString();
    }

    public static String plainStyled(FormattedText text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        text.visit((style, part) -> {
            sb.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }
}
