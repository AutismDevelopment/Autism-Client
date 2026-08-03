package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectViewport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AutismSharePickerOverlay extends AutismOverlayBase {
    private static final int ROW = 14;
    private static final int PAD = 8;
    private static final int SEARCH_H = 16;
    private static final int HEADER_COL = 0xFFFFC857;
    private static final int TEXT_COL = 0xFFF2F2F2;
    private static final int SUB_COL = 0xFF9A9A9A;
    private static final int HOVER = 0x33FFFFFF;

    public record Row(boolean header, String text, String sub, Runnable action) {
        public static Row header(String text) { return new Row(true, text, null, null); }
        public static Row item(String text, String sub, Runnable action) { return new Row(false, text, sub, action); }
    }

    private final Font font;
    private final CompactTheme theme = new CompactTheme();
    private final CompactTextInput search = new CompactTextInput();
    private String title = "Share";
    private Function<String, List<Row>> builder = s -> List.of();
    private final List<Row> rows = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private int contentH;
    private int scrollY;
    private boolean isDragging;
    private boolean scrollbarDragging;
    private int scrollGrab;
    private double dragOffsetX, dragOffsetY;

    public AutismSharePickerOverlay(Font font) {
        super("autism-share-picker", 280, 260);
        this.font = font;
        this.panelX = 140;
        this.panelY = 44;
        search.setMaxLength(48).setOnChange(t -> { rebuild(); scrollY = 0; });
    }

    public void open(String title, String placeholder, Function<String, List<Row>> builder) {
        this.title = title;
        this.builder = builder;
        search.setPlaceholder(placeholder).setText("");
        rebuild();
        scrollY = 0;
        AutismOverlayManager.get().register(this);
        setVisible(true);
        setBounds(new AutismWindowLayout(panelX, panelY, Math.max(getMinWidth(), 280), 260, true, false));
        AutismOverlayManager.get().bringToFront(this);
        search.setFocused(true);
    }

    private void rebuild() {
        rows.clear();
        rows.addAll(builder.apply(search.text()));
        contentH = rows.size() * ROW;
    }

    @Override public int getMinWidth() { return 220; }
    @Override public int getMinHeight() { return HEADER_HEIGHT + SEARCH_H + 6 + ROW * 3 + PAD; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }
    @Override public boolean hasTextFieldFocused() { return search.isFocused(); }
    @Override public void clearTextFieldFocus() { search.setFocused(false); }

    private int listTop() { return panelY + HEADER_HEIGHT + 3 + SEARCH_H + 4; }
    private int listAreaH() { return Math.max(ROW, panelY + panelHeight - PAD - listTop()); }
    private int maxScroll() { return Math.max(0, contentH - listAreaH()); }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        return CompactScrollbar.compute(contentH, listAreaH(), panelX + panelWidth - 5, listTop(), 3, listAreaH(), scrollY);
    }

    private DirectRenderContext ctx(GuiGraphicsExtractor g, double mx, double my, float delta) {
        return new DirectRenderContext(g, font, DirectViewport.current(1.0f), theme, (int) Math.round(mx), (int) Math.round(my), delta);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x; panelY = bounds.y; panelWidth = bounds.width; panelHeight = bounds.height;
        renderWindowFrame(ctx, mx, my, getBounds(), title, collapsed, isDragging);
        if (collapsed) return;

        boolean clipped = beginWindowBodyClip(ctx, getBounds(), collapsed);
        search.setBounds(panelX + PAD, panelY + HEADER_HEIGHT + 3, panelWidth - PAD * 2, SEARCH_H);
        search.render(ctx(ctx, mx, my, delta));

        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
        int lt = listTop();
        int areaH = listAreaH();
        UiScissorStack.global().push(ctx, UiBounds.of(panelX + 1, lt, Math.max(0, panelWidth - 2), areaH));
        hits.clear();
        int y = lt - scrollY;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (y + ROW > lt && y < lt + areaH) {
                if (r.header()) {
                    ctx.text(font, r.text(), panelX + PAD, y + 3, HEADER_COL, false);
                } else {
                    boolean hover = mx >= panelX + 2 && mx < panelX + panelWidth - 6 && my >= y && my < y + ROW && my >= lt && my < lt + areaH;
                    if (hover) UiRenderer.rect(ctx, UiBounds.of(panelX + 2, y, Math.max(0, panelWidth - 8), ROW), HOVER);
                    ctx.text(font, r.text(), panelX + PAD, y + 3, TEXT_COL, false);
                    if (r.sub() != null && !r.sub().isEmpty()) {
                        int sw = font.width(r.sub());
                        ctx.text(font, r.sub(), panelX + panelWidth - PAD - 6 - sw, y + 3, SUB_COL, false);
                    }
                    hits.add(new int[]{y, i});
                }
            }
            y += ROW;
        }
        UiScissorStack.global().pop(ctx);

        CompactScrollbar.Metrics sb = scrollbarMetrics();
        CompactScrollbar.draw(ctx, sb, sb.contains(mx, my), scrollbarDragging);
        endWindowBodyClip(ctx, clipped);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        if (isOverCloseButton(mx, my, bounds)) { setVisible(false); isDragging = false; return true; }
        if (isOverCollapseButton(mx, my, bounds)) { setCollapsed(!collapsed); return true; }
        CompactScrollbar.Metrics sb = scrollbarMetrics();
        if (button == 0 && sb.overThumb(mx, my)) { scrollbarDragging = true; scrollGrab = (int) Math.round(my) - sb.thumbY(); return true; }
        if (button == 0 && isOverDragBar(mx, my)) { isDragging = true; dragOffsetX = mx - panelX; dragOffsetY = my - panelY; return true; }
        if (search.mouseClicked(ctx(null, mx, my, 0f), (float) mx, (float) my, button)) return true;
        if (button == 0) {
            int lt = listTop(), areaH = listAreaH();
            for (int[] h : hits) {
                int ry = h[0];
                if (my >= Math.max(lt, ry) && my < Math.min(lt + areaH, ry + ROW)) {
                    Row r = rows.get(h[1]);
                    if (r.action() != null) {
                        try { r.action().run(); } catch (Throwable ignored) {  }
                        setVisible(false);
                        return true;
                    }
                }
            }
        }
        return isMouseOver(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (scrollbarDragging) { scrollbarDragging = false; return true; }
        if (isDragging) { isDragging = false; saveLayout(); return true; }
        return search.mouseReleased(ctx(null, mx, my, 0f), (float) mx, (float) my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollbarDragging) {
            scrollY = CompactScrollbar.scrollFromThumb(scrollbarMetrics(), my, scrollGrab);
            return true;
        }
        if (isDragging) {
            AutismWindowLayout c = clampToScreen(this, new AutismWindowLayout(
                (int) Math.round(mx - dragOffsetX), (int) Math.round(my - dragOffsetY),
                panelWidth, panelHeight, visible, collapsed));
            panelX = c.x; panelY = c.y; return true;
        }

        return search.mouseDragged(ctx(null, mx, my, 0f), (float) mx, (float) my, button, (float) dx, (float) dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) Math.signum(amount) * ROW * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !search.isFocused()) { setVisible(false); return true; }
        return search.keyPressed(ctx(null, 0, 0, 0f), keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        return search.charTyped(ctx(null, 0, 0, 0f), chr, modifiers);
    }
}
