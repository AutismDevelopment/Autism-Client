package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.util.mm.MmBlobs;
import autismclient.util.mm.msg.MmMessages;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class AutismFilterViewOverlay extends AutismOverlayBase {
    private static final int TITLE_H = 12;
    private static final int ROW = 11;
    private static final int PAD = 8;
    private static final int MAX_LIST_H = 220;

    private static final int HEADER_COL = 0xFFFFC857;
    private static final int C2S_COL    = 0xFF8EE6A0;
    private static final int S2C_COL    = 0xFF8EA0FF;
    private static final int MUTED      = 0xFF9A9A9A;

    private final Font font;
    private String title = "Packet Filter";
    private final List<Line> lines = new ArrayList<>();
    private int contentH;
    private int scrollY;
    private boolean isDragging;
    private boolean scrollbarDragging;
    private int scrollGrab;
    private double dragOffsetX, dragOffsetY;

    private record Line(String text, int color) {}

    public AutismFilterViewOverlay(Font font) {
        super("autism-filter-view", 220, 200);
        this.font = font;
        this.panelX = 130;
        this.panelY = 48;
    }

    public boolean open(MmMessages.BlobOffer blob) {
        MmBlobs.FilterView fv = MmBlobs.decodeFilter(blob);
        if (fv == null) { AutismNotifications.show("Could not read shared filter.", 0xFFFF5B5B); return false; }

        lines.clear();
        lines.add(new Line("Client → Server  (" + fv.c2s().size() + ")", HEADER_COL));
        if (fv.c2s().isEmpty()) lines.add(new Line("   (none)", MUTED));
        else for (String n : fv.c2s()) lines.add(new Line("   " + n, C2S_COL));
        lines.add(new Line("", MUTED));
        lines.add(new Line("Server → Client  (" + fv.s2c().size() + ")", HEADER_COL));
        if (fv.s2c().isEmpty()) lines.add(new Line("   (none)", MUTED));
        else for (String n : fv.s2c()) lines.add(new Line("   " + n, S2C_COL));

        this.title = "Packet Filter  (" + (fv.c2s().size() + fv.s2c().size()) + " types)";
        this.contentH = lines.size() * ROW;
        this.scrollY = 0;

        AutismOverlayManager.get().register(this);
        setVisible(true);
        int wantW = Math.max(getMinWidth(), maxLineWidth() + PAD * 2 + 6);
        int wantH = HEADER_HEIGHT + TITLE_H + 4 + Math.min(contentH, MAX_LIST_H) + PAD;
        setBounds(new AutismWindowLayout(panelX, panelY, wantW, wantH, true, false));
        AutismOverlayManager.get().bringToFront(this);
        return true;
    }

    private int maxLineWidth() {
        int w = font.width(title);
        for (Line l : lines) w = Math.max(w, font.width(l.text()));
        return w;
    }

    @Override public int getMinWidth() { return 170; }
    @Override public int getMinHeight() { return HEADER_HEIGHT + TITLE_H + 4 + ROW * 3 + PAD; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }

    private int listTop() { return panelY + HEADER_HEIGHT + TITLE_H + 4; }
    private int listAreaH() { return Math.max(ROW, panelY + panelHeight - PAD - listTop()); }
    private int maxScroll() { return Math.max(0, contentH - listAreaH()); }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        return CompactScrollbar.compute(contentH, listAreaH(), panelX + panelWidth - 5, listTop(), 3, listAreaH(), scrollY);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x; panelY = bounds.y; panelWidth = bounds.width; panelHeight = bounds.height;
        renderWindowFrame(ctx, mx, my, getBounds(), "Shared Filter", collapsed, isDragging);
        if (collapsed) return;

        boolean clipped = beginWindowBodyClip(ctx, getBounds(), collapsed);
        ctx.text(font, title, panelX + PAD, panelY + HEADER_HEIGHT + 3, 0xFFF2F2F2, false);

        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
        int lt = listTop();
        UiScissorStack.global().push(ctx, UiBounds.of(panelX + 1, lt, Math.max(0, panelWidth - 2), listAreaH()));
        int y = lt - scrollY;
        for (Line l : lines) {
            if (y + ROW > lt && y < lt + listAreaH() && !l.text().isEmpty())
                ctx.text(font, l.text(), panelX + PAD, y, l.color(), false);
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
        if (button == 0 && sb.overThumb(mx, my)) {
            scrollbarDragging = true;
            scrollGrab = (int) Math.round(my) - sb.thumbY();
            return true;
        }
        if (button == 0 && isOverDragBar(mx, my)) {
            isDragging = true; dragOffsetX = mx - panelX; dragOffsetY = my - panelY; return true;
        }
        return isMouseOver(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (scrollbarDragging) { scrollbarDragging = false; return true; }
        if (isDragging) { isDragging = false; saveLayout(); return true; }
        return false;
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
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) Math.signum(amount) * ROW * 2));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override public boolean charTyped(char chr, int modifiers) { return false; }
}
