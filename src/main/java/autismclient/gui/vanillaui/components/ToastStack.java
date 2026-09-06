package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ToastStack {
    private static final long DEFAULT_LIFETIME_MS = 1800L;
    private static final float DEFAULT_ENTER_MS = 140.0f;
    private static final float DEFAULT_EXIT_MS = 220.0f;
    private static final int DEFAULT_MAX_VISIBLE = 4;
    private static final int DEFAULT_GAP = 4;
    private static final int DEFAULT_HEIGHT = 18;
    private static final int LINE_HEIGHT = 10;

    private final List<ToastEntry> toasts = new CopyOnWriteArrayList<>();

    private volatile boolean maybeVisible;
    private final long lifetimeMs;
    private final float enterMs;
    private final float exitMs;
    private final int maxVisible;
    private final int gap;
    private final int height;

    public ToastStack() {
        this(DEFAULT_LIFETIME_MS, DEFAULT_ENTER_MS, DEFAULT_EXIT_MS, DEFAULT_MAX_VISIBLE, DEFAULT_GAP, DEFAULT_HEIGHT);
    }

    public ToastStack(long lifetimeMs, float enterMs, float exitMs, int maxVisible, int gap, int height) {
        this.lifetimeMs = Math.max(1L, lifetimeMs);
        this.enterMs = Math.max(1.0f, enterMs);
        this.exitMs = Math.max(1.0f, exitMs);
        this.maxVisible = Math.max(1, maxVisible);
        this.gap = Math.max(0, gap);
        this.height = Math.max(1, height);
    }

    public void show(String message, int accentColor) {
        if (message == null || message.isBlank()) return;
        long nowNanos = System.nanoTime();
        prune(nowNanos);
        if (toasts.size() >= maxVisible) toasts.remove(0);
        toasts.add(new ToastEntry(message, nowNanos, accentColor));
        maybeVisible = true;
    }

    public boolean hasVisibleToasts() {
        if (!maybeVisible) return false;
        prune(System.nanoTime());
        if (toasts.isEmpty()) {
            maybeVisible = false;
            return false;
        }
        return true;
    }

    public int size() {
        if (!maybeVisible) return 0;
        prune(System.nanoTime());
        if (toasts.isEmpty()) maybeVisible = false;
        return toasts.size();
    }

    public void clear() {
        toasts.clear();
        maybeVisible = false;
    }

    public void render(UiContext context, int anchorX, int anchorY, int anchorWidth) {
        if (context == null || anchorWidth <= 0) return;
        long nowNanos = System.nanoTime();
        prune(nowNanos);
        if (toasts.isEmpty()) {
            maybeVisible = false;
            return;
        }

        int maxToastWidth = Math.max(1, Math.min(anchorWidth, 260));
        int padX = 9;
        int lineHeight = LINE_HEIGHT;
        int verticalPad = Math.max(4, height - lineHeight);
        int maxTextWidth = Math.max(1, maxToastWidth - padX * 2);
        int y = anchorY;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            ToastEntry toast = toasts.get(i);
            float ageMs = Math.max(0.0f, (nowNanos - toast.shownAtNanos()) / 1_000_000.0f);
            float alpha = Math.min(clamp01(ageMs / enterMs), clamp01((lifetimeMs - ageMs) / exitMs));
            if (alpha <= 0.001f) continue;

            List<String> lines = context.text().wrapFully(toast.message(), maxTextWidth);
            int widest = 0;
            for (String line : lines) widest = Math.max(widest, context.text().width(line));
            int toastWidth = Math.min(maxToastWidth, Math.max(Math.min(48, maxToastWidth), widest + padX * 2));
            int textBlockHeight = lines.size() * lineHeight;
            int toastHeight = Math.max(height, textBlockHeight + verticalPad);
            int drawX = anchorX + Math.max(0, (anchorWidth - toastWidth) / 2);

            float slide = clamp01(ageMs / enterMs);
            float eased = 1.0f - (1.0f - slide) * (1.0f - slide) * (1.0f - slide);
            int drawY = y - Math.round((1.0f - eased) * (toastHeight + gap + 2));
            UiBounds bounds = UiBounds.of(drawX, drawY, toastWidth, toastHeight);

            int fill = tintToward(0x121014, toast.accentColor(), 0.22f);
            UiRenderer.roundFrame(context.graphics(), bounds, 5,
                UiRenderer.applyAlpha(0xD6000000 | fill, alpha), UiRenderer.applyAlpha(toast.accentColor(), alpha));
            int textColor = UiRenderer.applyAlpha(0xFFF4F4F4, alpha);

            int textTop = drawY + Math.max(2, (toastHeight - textBlockHeight + 1) / 2 + 1);
            for (int li = 0; li < lines.size(); li++) {
                String line = lines.get(li);
                int lineX = drawX + Math.max(padX, (toastWidth - context.text().width(line)) / 2);
                context.text().draw(context.graphics(), line, lineX, textTop + li * lineHeight, textColor);
            }
            y += toastHeight + gap;
        }
    }

    private void prune(long nowNanos) {
        toasts.removeIf(toast -> (nowNanos - toast.shownAtNanos()) / 1_000_000L >= lifetimeMs);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int tintToward(int baseRgb, int accentColor, float t) {
        int r = (baseRgb >> 16) & 0xFF, g = (baseRgb >> 8) & 0xFF, b = baseRgb & 0xFF;
        int ar = (accentColor >> 16) & 0xFF, ag = (accentColor >> 8) & 0xFF, ab = accentColor & 0xFF;
        r += Math.round((ar - r) * t);
        g += Math.round((ag - g) * t);
        b += Math.round((ab - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    private record ToastEntry(String message, long shownAtNanos, int accentColor) {
    }
}
