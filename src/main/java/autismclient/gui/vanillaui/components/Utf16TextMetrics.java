package autismclient.gui.vanillaui.components;

final class Utf16TextMetrics {
    private Utf16TextMetrics() {
    }

    @FunctionalInterface
    interface CodePointWidth {
        int width(String text, int start, int end);
    }

    static int[] cumulativeWidths(String text, CodePointWidth measurer) {
        String value = text == null ? "" : text;
        int[] widths = new int[value.length() + 1];
        int running = 0;
        int index = 0;
        while (index < value.length()) {
            int end = value.offsetByCodePoints(index, 1);

            for (int intermediate = index + 1; intermediate < end; intermediate++) {
                widths[intermediate] = running;
            }
            running += Math.max(0, measurer.width(value, index, end));
            widths[end] = running;
            index = end;
        }
        return widths;
    }

    static int floorBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int clamped = Math.max(0, Math.min(index, value.length()));
        if (clamped > 0 && clamped < value.length()
                && Character.isHighSurrogate(value.charAt(clamped - 1))
                && Character.isLowSurrogate(value.charAt(clamped))) {
            return clamped - 1;
        }
        return clamped;
    }

    static int ceilBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int clamped = Math.max(0, Math.min(index, value.length()));
        if (clamped > 0 && clamped < value.length()
                && Character.isHighSurrogate(value.charAt(clamped - 1))
                && Character.isLowSurrogate(value.charAt(clamped))) {
            return clamped + 1;
        }
        return clamped;
    }

    static int previousBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int boundary = floorBoundary(value, index);
        return boundary <= 0 ? 0 : value.offsetByCodePoints(boundary, -1);
    }

    static int nextBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int boundary = ceilBoundary(value, index);
        return boundary >= value.length() ? value.length() : value.offsetByCodePoints(boundary, 1);
    }

    static int nearestBoundary(String text, int[] widths, int candidate, int targetWidth) {
        String value = text == null ? "" : text;
        int clamped = Math.max(0, Math.min(candidate, value.length()));
        int left = floorBoundary(value, clamped);
        int right = ceilBoundary(value, clamped);
        if (left == right) return left;
        return targetWidth - widths[left] < widths[right] - targetWidth ? left : right;
    }

    static String truncateAtBoundary(String text, int maxUtf16Units) {
        String value = text == null ? "" : text;
        if (value.length() <= maxUtf16Units) return value;
        return value.substring(0, floorBoundary(value, Math.max(0, maxUtf16Units)));
    }
}
