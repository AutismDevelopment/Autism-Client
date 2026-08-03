package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiComponent;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismUiScale;

import java.util.List;
import java.util.function.Consumer;

public final class Dropdown implements UiComponent {
    private static final int SCREEN_MARGIN = 4;

    private static int lerpColor(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF, aa = (from >>> 24) & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF, ba = (to >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        int a = Math.round(aa + (ba - aa) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private UiBounds bounds;
    private UiBounds menuBounds = UiBounds.of(0, 0, 0, 0);
    private List<String> options;
    private String selected;
    private final Consumer<String> onSelect;
    private int scroll;
    private int visibleRows;
    private boolean open;
    private long openedAtNanos;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private Scrollbar.Metrics scrollbar;
    private boolean menuWidthDirty = true;
    private int cachedMenuWidth = 1;
    private int cachedMenuScreenWidth = -1;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private int lastRowHeight = -1;

    public Dropdown(UiBounds bounds, List<String> options, String selected, Consumer<String> onSelect) {
        this.bounds = bounds == null ? UiBounds.of(0, 0, 0, 0) : bounds;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.selected = selected == null ? "" : selected;
        this.onSelect = onSelect == null ? ignored -> {} : onSelect;
    }

    @Override
    public UiBounds bounds() {
        return bounds;
    }

    @Override
    public UiBounds hitBounds() {
        return open ? bounds.union(menuBounds) : bounds;
    }

    @Override
    public void setBounds(UiBounds bounds) {
        if (bounds != null) {
            if (this.bounds.width() != bounds.width()) menuWidthDirty = true;
            this.bounds = bounds;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOptions(List<String> options) {
        List<String> next = options == null ? List.of() : List.copyOf(options);
        if (this.options.equals(next)) return;
        this.options = next;
        menuWidthDirty = true;
        if (!this.options.contains(selected)) selected = this.options.isEmpty() ? "" : this.options.get(0);
        scroll = clamp(scroll, 0, maxScroll());
        ensureSelectedVisible();
    }

    public void setSelected(String selected) {
        this.selected = selected == null ? "" : selected;
        ensureSelectedVisible();
    }

    public boolean containsMenu(int mouseX, int mouseY) {
        prepareInputLayout();
        return open && menuBounds.contains(mouseX, mouseY);
    }

    public void open() {
        open = true;
        openedAtNanos = System.nanoTime();
        ensureSelectedVisible();
        prepareInputLayout();
    }

    public void close() {
        open = false;
        openedAtNanos = 0L;
        draggingScrollbar = false;
    }

    public static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
        renderControl(context, bounds, label, hovered, open, false, true);
    }

    public static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open,
                                     boolean enabled) {
        renderControl(context, bounds, label, hovered, open, false, enabled);
    }

    public static void renderControlCentered(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open) {
        renderControl(context, bounds, label, hovered, open, true, true);
    }

    private static void renderControl(UiContext context, UiBounds bounds, String label, boolean hovered, boolean open,
                                      boolean centered, boolean enabled) {
        var colors = context.theme().colors();
        int fill = !enabled ? colors.field : open ? colors.fieldFocused : (hovered ? colors.rowHover : colors.field);

        UiRenderer.frame(context.graphics(), bounds, fill, open && enabled ? colors.accent : colors.borderSoft);
        int textColor = enabled ? colors.text : colors.disabled;
        if (centered) {
            context.text().drawCentered(context.graphics(), label, bounds, textColor);
        } else {
            context.text().drawFitted(context.graphics(), label, bounds.x() + 5,
                context.text().centeredY(bounds), Math.max(1, bounds.width() - 18), textColor);
        }
        UiRenderer.chevron(context.graphics(),
            UiBounds.of(bounds.right() - 10, bounds.y() + Math.max(0, (bounds.height() - 8) / 2), 8, 8), open, textColor);
    }

    @Override
    public void render(UiContext context) {
        if (!open || options.isEmpty()) return;
        int rowHeight = context.theme().spacing().rowHeight;
        lastScreenWidth = context.screenWidth();
        lastScreenHeight = context.screenHeight();
        lastRowHeight = rowHeight;
        visibleRows = visibleRows(context.screenHeight(), rowHeight);
        int width = menuWidth(context);
        layoutMenu(context.screenWidth(), context.screenHeight(), rowHeight, width);

        var colors = context.theme().colors();

        float fade = openedAtNanos == 0L ? 1.0f
            : Math.max(0.0f, Math.min(1.0f, (System.nanoTime() - openedAtNanos) / 70_000_000.0f));
        UiRenderer.frame(context.graphics(), menuBounds,
            UiRenderer.applyAlpha(colors.windowStrong, fade), UiRenderer.applyAlpha(colors.borderSoft, fade));
        int contentWidth = options.size() > visibleRows ? menuBounds.width() - context.theme().spacing().scrollbarWidth - 3 : menuBounds.width() - 2;
        for (int rowIndex = 0; rowIndex < visibleRows; rowIndex++) {
            int optionIndex = scroll + rowIndex;
            if (optionIndex >= options.size()) break;
            String option = options.get(optionIndex);
            UiBounds row = UiBounds.of(menuBounds.x() + 1, menuBounds.y() + 1 + rowIndex * rowHeight, Math.max(1, contentWidth), rowHeight);
            boolean hovered = row.contains(context.mouseX(), context.mouseY());
            boolean active = option.equals(selected);
            float hoverT = autismclient.gui.vanillaui.HoverFades.get("dropdown:" + option, hovered);
            int rowFill = active ? colors.accentSoft : lerpColor(colors.row, colors.rowHover, hoverT);
            UiRenderer.rect(context.graphics(), row, UiRenderer.applyAlpha(rowFill, fade));
            context.text().drawFitted(context.graphics(), option, row.x() + 4, context.text().centeredY(row),
                Math.max(1, row.width() - 18), UiRenderer.applyAlpha(colors.text, fade));
            if (active) {
                UiRenderer.check(context.graphics(),
                    UiBounds.of(row.right() - 11, row.y() + Math.max(0, (row.height() - 8) / 2), 8, 8),
                    UiRenderer.applyAlpha(colors.accent, fade));
            }
        }

        if (options.size() > visibleRows) {
            UiBounds track = UiBounds.of(menuBounds.right() - context.theme().spacing().scrollbarWidth - 1, menuBounds.y() + 1,
                context.theme().spacing().scrollbarWidth, Math.max(1, menuBounds.height() - 2));
            scrollbar = Scrollbar.metrics(track, options.size() * rowHeight, visibleRows * rowHeight, scroll * rowHeight);
            Scrollbar.render(context, scrollbar, scrollbar.track().contains(context.mouseX(), context.mouseY()), draggingScrollbar);
        } else {
            scrollbar = null;
        }
    }

    @Override
    public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
        prepareInputLayout();
        if (button != 0) {
            if (!open) return UiInputResult.IGNORED;
            if (!bounds.contains(mouseX, mouseY) && !menuBounds.contains(mouseX, mouseY)) close();
            return UiInputResult.HANDLED;
        }
        if (open) {
            if (menuBounds.contains(mouseX, mouseY)) {
                if (scrollbar != null && scrollbar.track().contains(mouseX, mouseY)) {
                    scrollbarGrabOffset = scrollbar.thumb().contains(mouseX, mouseY) ? mouseY - scrollbar.thumb().y() : scrollbar.thumb().height() / 2;
                    draggingScrollbar = true;
                    updateScrollbar(mouseY);
                    return UiInputResult.HANDLED;
                }
                int index = scroll + Math.max(0, (mouseY - menuBounds.y() - 1) / Math.max(1, rowHeight()));
                if (index >= 0 && index < options.size()) {
                    selected = options.get(index);
                    onSelect.accept(selected);
                }
                close();
                return UiInputResult.HANDLED;
            }
            if (bounds.contains(mouseX, mouseY)) {
                close();
                return UiInputResult.HANDLED;
            }
            close();
            return UiInputResult.HANDLED;
        }
        if (bounds.contains(mouseX, mouseY)) {
            open();
            return UiInputResult.HANDLED;
        }
        return UiInputResult.IGNORED;
    }

    @Override
    public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
        prepareInputLayout();
        if (!draggingScrollbar) return open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
        draggingScrollbar = false;
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        prepareInputLayout();
        if (!draggingScrollbar || button != 0) return open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
        updateScrollbar(mouseY);
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
        prepareInputLayout();
        if (!open) return UiInputResult.IGNORED;
        if (menuBounds.contains(mouseX, mouseY)) {
            scroll = clamp(scroll + (amount < 0 ? 1 : -1), 0, maxScroll());
        }
        return UiInputResult.HANDLED;
    }

    private int menuWidth(UiContext context) {
        if (!menuWidthDirty && cachedMenuScreenWidth == context.screenWidth()) return cachedMenuWidth;
        int width = Math.max(1, bounds.width());
        for (String option : options) width = Math.max(width, context.text().width(option) + 12);
        cachedMenuWidth = Math.min(width, Math.max(1, context.screenWidth() - SCREEN_MARGIN * 2));
        cachedMenuScreenWidth = context.screenWidth();
        menuWidthDirty = false;
        return cachedMenuWidth;
    }

    private void prepareInputLayout() {
        if (!open) return;
        int screenWidth = lastScreenWidth > 0 ? lastScreenWidth : Math.max(1, AutismUiScale.getVirtualScreenWidth());
        int screenHeight = lastScreenHeight > 0 ? lastScreenHeight : Math.max(1, AutismUiScale.getVirtualScreenHeight());
        int rowHeight = lastRowHeight > 0 ? lastRowHeight : 16;
        visibleRows = visibleRows(screenHeight, rowHeight);
        int width = Math.max(1, Math.min(cachedMenuWidth > 1 ? cachedMenuWidth : bounds.width(), screenWidth - SCREEN_MARGIN * 2));
        layoutMenu(screenWidth, screenHeight, rowHeight, width);
    }

    private void layoutMenu(int screenWidth, int screenHeight, int rowHeight, int width) {
        visibleRows = Math.max(1, Math.min(options.size(), visibleRows <= 0 ? visibleRows(screenHeight, rowHeight) : visibleRows));
        int height = Math.max(1, visibleRows * rowHeight + 2);
        int x = bounds.x();
        int below = bounds.bottom() + 1;
        int above = bounds.y() - height - 1;
        int availableBelow = screenHeight - SCREEN_MARGIN - below;
        int availableAbove = bounds.y() - SCREEN_MARGIN;
        int y = availableBelow >= height || availableBelow >= availableAbove ? below : above;
        menuBounds = UiBounds.of(
            clamp(x, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - width)),
            clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - height)),
            width,
            height
        );
    }

    private int visibleRows(int screenHeight, int rowHeight) {
        int below = Math.max(0, (screenHeight - SCREEN_MARGIN - bounds.bottom() - 1) / Math.max(1, rowHeight));
        int above = Math.max(0, (bounds.y() - SCREEN_MARGIN - 1) / Math.max(1, rowHeight));
        return Math.max(1, Math.min(options.size(), Math.max(below, above)));
    }

    private int rowHeight() {
        return visibleRows <= 0 ? 1 : Math.max(1, (menuBounds.height() - 2) / visibleRows);
    }

    private int maxScroll() {
        return Math.max(0, options.size() - Math.max(1, visibleRows));
    }

    private void ensureSelectedVisible() {
        int index = options.indexOf(selected);
        if (index < 0) return;
        if (index < scroll) scroll = index;
        if (visibleRows > 0 && index >= scroll + visibleRows) scroll = index - visibleRows + 1;
        scroll = clamp(scroll, 0, maxScroll());
    }

    private void updateScrollbar(int mouseY) {
        if (scrollbar == null || scrollbar.maxScroll() <= 0) return;
        int pixelScroll = Scrollbar.scrollFromMouse(scrollbar, mouseY, scrollbarGrabOffset);
        scroll = clamp(Math.round(pixelScroll / (float) Math.max(1, rowHeight())), 0, maxScroll());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
