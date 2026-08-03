package autismclient.gui.vanillaui.components;

public final class ScrollState {
    private int targetOffset = 0;

    public void restore(int offset) {
        targetOffset = Math.max(0, offset);
    }

    public void setTarget(int offset, int maxScroll) {
        targetOffset = clamp(offset, maxScroll);
    }

    public void jumpTo(int offset, int maxScroll) {
        targetOffset = clamp(offset, maxScroll);
    }

    public void nudge(double amount, float stepPixels, int maxScroll) {
        setTarget(targetOffset - Math.round((float) amount * stepPixels), maxScroll);
    }

    public void setFromThumb(CompactScrollbar.Metrics metrics, double mouseY, int grabOffset) {
        if (metrics == null) {
            jumpTo(0, 0);
            return;
        }
        setTarget(CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset), metrics.maxScroll());
    }

    public void setFromThumbStepped(CompactScrollbar.Metrics metrics, double mouseY, int grabOffset, int stepSize) {
        if (metrics == null) {
            jumpTo(0, 0);
            return;
        }
        int rawScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset);
        int steppedScroll = (rawScroll / stepSize) * stepSize;
        setTarget(steppedScroll, metrics.maxScroll());
    }

    public int tick(float delta, int maxScroll) {
        targetOffset = clamp(targetOffset, maxScroll);
        return targetOffset;
    }

    public int targetOffset() {
        return targetOffset;
    }

    public int visualOffsetInt() {
        return targetOffset;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
