package autismclient.util;

import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class AutismContextMenu<T> {
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 2;
    private static final int BORDER = 1;

    private final CompactTheme theme;
    private final Font font;
    private final java.util.function.Function<T, String[]> itemProvider;
    private final int lineHeight;

    private boolean open;
    private int x, y;
    private T target;
    private String[] cachedItems = new String[0];
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedScreenWidth = -1;
    private int cachedScreenHeight = -1;

    public AutismContextMenu(CompactTheme theme, Font font, java.util.function.Function<T, String[]> itemProvider, int lineHeight) {
        this.theme = theme;
        this.font = font;
        this.itemProvider = itemProvider;
        this.lineHeight = lineHeight;
    }

    public boolean isOpen() {
        return open && target != null;
    }

    public T target() {
        return target;
    }

    public void open(int mouseX, int mouseY, T target) {
        this.target = target;
        this.x = mouseX;
        this.y = mouseY;
        this.open = true;
        String[] items = itemProvider.apply(target);
        this.cachedItems = items == null ? new String[0] : items;
        invalidateMetrics();
        clampToScreen();
    }

    public void close() {
        this.open = false;
        this.target = null;
        this.cachedItems = new String[0];
        invalidateMetrics();
    }

    private String[] currentItems() {
        return !open || target == null ? new String[0] : cachedItems;
    }

    private int width(String[] items) {
        ensureMetrics(items);
        return cachedWidth;
    }

    private int height(String[] items) {
        ensureMetrics(items);
        return cachedHeight;
    }

    private void ensureMetrics(String[] items) {
        int screenWidth = Math.max(1, AutismUiScale.getVirtualScreenWidth());
        int screenHeight = Math.max(1, AutismUiScale.getVirtualScreenHeight());
        if (cachedWidth >= 0 && cachedHeight >= 0 && cachedScreenWidth == screenWidth && cachedScreenHeight == screenHeight) return;
        int maxW = 0;
        for (String s : items) {
            maxW = Math.max(maxW, UiText.width(font, s, theme.fontFor(UiTone.BODY), 0xFFFFFFFF));
        }
        cachedWidth = Math.min(Math.max(1, screenWidth - 4), maxW + (PADDING_X * 2));
        cachedHeight = visibleItemCount(items) * lineHeight + (PADDING_Y * 2);
        cachedScreenWidth = screenWidth;
        cachedScreenHeight = screenHeight;
    }

    private void invalidateMetrics() {
        cachedWidth = -1;
        cachedHeight = -1;
        cachedScreenWidth = -1;
        cachedScreenHeight = -1;
    }

    private int visibleItemCount(String[] items) {
        int available = Math.max(1, AutismUiScale.getVirtualScreenHeight() - 4 - (PADDING_Y * 2));
        return Math.max(0, Math.min(items.length, available / Math.max(1, lineHeight)));
    }

    private void clampToScreen() {
        if (!open) return;
        String[] items = currentItems();
        int w = width(items);
        int h = height(items);
        int sw = Math.max(1, AutismUiScale.getVirtualScreenWidth());
        int sh = Math.max(1, AutismUiScale.getVirtualScreenHeight());
        x = Math.max(2, Math.min(x, sw - w - 2));
        y = Math.max(2, Math.min(y, sh - h - 2));
    }

    public boolean isMouseOver(double mx, double my) {
        if (!isOpen()) return false;
        String[] items = currentItems();
        int w = width(items);
        int h = height(items);
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(GuiGraphicsExtractor ctx, int mx, int my) {
        if (!isOpen()) return;
        clampToScreen();
        String[] items = currentItems();
        int w = width(items);
        int h = height(items);

        UiRenderer.popup(ctx, UiBounds.of(x - BORDER, y - BORDER, w + BORDER * 2, h + BORDER * 2),
            theme.windowFill(), theme.borderColor(), theme.headerAccent());

        int visibleItems = visibleItemCount(items);
        for (int i = 0; i < visibleItems; i++) {
            int iy = y + PADDING_Y + i * lineHeight;
            boolean hov = mx >= x && mx < x + w && my >= iy && my < iy + lineHeight;
            if (hov) CompactSurfaces.tintedRow(ctx, x + 1, iy, w - 2, lineHeight, AutismColors.popupHover());
            String display = UiText.trimToWidth(font, items[i], Math.max(1, w - PADDING_X * 2), theme.fontFor(UiTone.BODY), AutismColors.textLight());
            UiText.draw(ctx, font, display, theme.fontFor(UiTone.BODY),
                hov ? 0xFFFFFFFF : AutismColors.textLight(),
                x + PADDING_X, iy + PADDING_Y, false);
        }
    }

    public boolean handleClick(double mouseX, double mouseY, int button, ItemPickedCallback<T> onPick) {
        if (!isOpen()) return false;
        if (button != 0) {
            close();
            return true;
        }

        String[] items = currentItems();
        int w = width(items);
        boolean inX = mouseX >= x && mouseX < x + w;
        if (inX) {
            int visibleItems = visibleItemCount(items);
            for (int i = 0; i < visibleItems; i++) {
                int iy = y + PADDING_Y + i * lineHeight;
                if (mouseY >= iy && mouseY < iy + lineHeight) {
                    T t = target;
                    String label = items[i];
                    int idx = i;
                    close();
                    if (onPick != null) onPick.onPicked(t, label, idx);
                    return true;
                }
            }
        }

        close();
        return true;
    }

    @FunctionalInterface
    public interface ItemPickedCallback<T> {
        void onPicked(T target, String label, int index);
    }
}
