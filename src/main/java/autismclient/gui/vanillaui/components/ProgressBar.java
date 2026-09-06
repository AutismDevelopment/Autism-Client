package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class ProgressBar {
    private ProgressBar() {
    }

    public static void render(UiContext context, UiBounds bounds, double progress) {
        var colors = context.theme().colors();
        progress = Math.max(0.0, Math.min(1.0, progress));
        UiRenderer.frame(context.graphics(), bounds, colors.field, colors.borderSoft);
        int innerW = Math.max(0, bounds.width() - 2);
        int fillW = Math.max(0, Math.min(innerW, (int) Math.round(innerW * progress)));
        if (fillW > 0) {
            UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x() + 1, bounds.y() + 1, fillW, Math.max(0, bounds.height() - 2)),
                lerp(colors.accent, colors.success, progress));
        }
    }

    private static int lerp(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int ca = (int) Math.round(aa + (ba - aa) * t);
        int cr = (int) Math.round(ar + (br - ar) * t);
        int cg = (int) Math.round(ag + (bg - ag) * t);
        int cb = (int) Math.round(ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }
}
