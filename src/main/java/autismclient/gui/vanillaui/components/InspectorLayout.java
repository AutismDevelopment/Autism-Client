package autismclient.gui.vanillaui.components;

public final class InspectorLayout {
    public static final int SCROLLBAR_WIDTH = 4;
    private static final int VIEWPORT_GUTTER = 3;

    private InspectorLayout() {
    }

    public static int contentWidth(int viewportWidth, int innerPadding) {
        return Math.max(22, viewportWidth - (innerPadding * 2) - SCROLLBAR_WIDTH - VIEWPORT_GUTTER);
    }

    public static int clampTextOffset(int contentWidth, int wantedOffset) {
        return Math.max(0, Math.min(wantedOffset, Math.max(0, contentWidth - 1)));
    }

    public static int remainingTextWidth(int contentWidth, int offset) {
        return Math.max(1, contentWidth - clampTextOffset(contentWidth, offset));
    }
}
