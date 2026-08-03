package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.util.mm.MmBlobs;
import autismclient.util.mm.msg.MmMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class AutismGuiViewOverlay extends AutismOverlayBase {
    private static final int CELL = 18;
    private static final int PAD = 7;
    private static final int TITLE_H = 12;
    private static final int GRID_TOP_GAP = 6;
    private static final int GRID_INNER_TOP = 4;

    private static final int PANEL_BG   = 0xFFC6C6C6;
    private static final int SLOT_FILL   = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_LIGHT  = 0xFFFFFFFF;
    private static final int SLOT_HOVER  = 0x80FFFFFF;

    private final Font font;
    private Component title = Component.literal("GUI");
    private List<MmBlobs.SlotView> slots = List.of();
    private int guiW, guiH;
    private int scrollY;
    private boolean isDragging;
    private boolean scrollbarDragging;
    private int scrollGrab;
    private double dragOffsetX, dragOffsetY;

    public AutismGuiViewOverlay(Font font) {
        super("autism-gui-view", 180, 180);
        this.font = font;
        this.panelX = 120;
        this.panelY = 50;
    }

    public boolean open(MmMessages.BlobOffer blob) {
        MmBlobs.GuiSnapshot snap = MmBlobs.decodeGui(blob);
        if (snap == null) { AutismNotifications.show("Could not read shared GUI.", 0xFFFF5B5B); return false; }

        this.title = snap.title() == null ? Component.literal("GUI") : snap.title();
        this.slots = snap.slots();
        this.guiW = Math.max(CELL, snap.width());
        this.guiH = Math.max(CELL, snap.height());
        this.scrollY = 0;
        AutismOverlayManager.get().register(this);
        setVisible(true);
        int wantW = guiW + 2 + PAD * 2;
        int wantH = HEADER_HEIGHT + TITLE_H + GRID_TOP_GAP + guiH + 2 + PAD;
        setBounds(new AutismWindowLayout(panelX, panelY, wantW, wantH, true, false));
        AutismOverlayManager.get().bringToFront(this);
        return true;
    }

    @Override public int getMinWidth() { return CELL * 3 + PAD * 2; }
    @Override public int getMinHeight() { return HEADER_HEIGHT + TITLE_H + GRID_TOP_GAP + CELL + PAD; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }

    private int gridLeft() { return panelX + PAD + 1; }
    private int gridTop() { return panelY + HEADER_HEIGHT + TITLE_H + GRID_TOP_GAP; }
    private int gridAreaH() { return Math.max(CELL, panelY + panelHeight - PAD - gridTop()); }
    private int maxScroll() { return Math.max(0, (guiH + 2) - gridAreaH()); }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        return CompactScrollbar.compute(guiH + 2, gridAreaH(), panelX + panelWidth - 5, gridTop(), 3, gridAreaH(), scrollY);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x; panelY = bounds.y; panelWidth = bounds.width; panelHeight = bounds.height;
        renderWindowFrame(ctx, mx, my, getBounds(), "Shared GUI", collapsed, isDragging);
        if (collapsed) return;
        boolean clipped = beginWindowBodyClip(ctx, getBounds(), collapsed);

        ctx.text(font, title.getVisualOrderText(), panelX + 6, panelY + HEADER_HEIGHT + 3, 0xFFF2F2F2, false);

        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
        int gl = gridLeft(), gt = gridTop() - scrollY;

        UiBounds gridArea = UiBounds.of(panelX + PAD - 1, gridTop() - GRID_INNER_TOP, guiW + 4, gridAreaH() + GRID_INNER_TOP);
        UiRenderer.rect(ctx, gridArea, PANEL_BG);

        UiScissorStack.global().push(ctx, gridArea);
        ItemStack hoveredStack = ItemStack.EMPTY;
        for (MmBlobs.SlotView slot : slots) {
            int itemX = gl + slot.x(), itemY = gt + slot.y();

            if (itemY + CELL < gridTop() || itemY > gridTop() + gridAreaH()) continue;
            drawSlotCell(ctx, itemX - 1, itemY - 1);
            ItemStack st = slot.item();
            if (st != null && !st.isEmpty()) {
                try {
                    ctx.item(st, itemX, itemY);
                    ctx.itemDecorations(font, st, itemX, itemY);
                } catch (Throwable ignored) {

                }
            }
            boolean hover = mx >= itemX && mx < itemX + 16 && my >= itemY && my < itemY + 16
                && my >= gridTop() && my <= gridTop() + gridAreaH();
            if (hover) {
                UiRenderer.rect(ctx, UiBounds.of(itemX, itemY, 16, 16), SLOT_HOVER);
                if (st != null && !st.isEmpty()) { hoveredStack = st; }
            }
        }
        UiScissorStack.global().pop(ctx);

        CompactScrollbar.Metrics sb = scrollbarMetrics();
        CompactScrollbar.draw(ctx, sb, sb.contains(mx, my), scrollbarDragging);
        endWindowBodyClip(ctx, clipped);

        if (!hoveredStack.isEmpty()) renderItemTooltip(ctx, hoveredStack, mx, my);
    }

    private void renderItemTooltip(GuiGraphicsExtractor ctx, ItemStack stack, int mx, int my) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            List<Component> lines = Screen.getTooltipFromItem(mc, stack);
            if (lines == null || lines.isEmpty()) return;
            int w = 0;
            for (Component c : lines) w = Math.max(w, font.width(c));
            int h = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            int x = mx + 12, y = my - 12;
            if (x + w + 4 > sw) x = Math.max(4, mx - w - 16);
            if (y + h + 4 > sh) y = sh - h - 4;
            if (y < 4) y = 4;

            ctx.nextStratum();

            UiRenderer.rect(ctx, UiBounds.of(x - 3, y - 3, w + 6, h + 6), 0xF0100010);
            UiRenderer.frame(ctx, UiBounds.of(x - 3, y - 3, w + 6, h + 6), 0, 0x505000A0);
            int ty = y;
            for (Component c : lines) {

                ctx.text(font, c.getVisualOrderText(), x, ty, 0xFFFFFFFF, true);
                ty += 10;
            }
        } catch (Throwable ignored) {

        }
    }

    private void drawSlotCell(GuiGraphicsExtractor ctx, int cx, int cy) {
        UiRenderer.rect(ctx, UiBounds.of(cx, cy, CELL, CELL), SLOT_FILL);
        UiRenderer.rect(ctx, UiBounds.of(cx, cy, CELL, 1), SLOT_SHADOW);
        UiRenderer.rect(ctx, UiBounds.of(cx, cy, 1, CELL), SLOT_SHADOW);
        UiRenderer.rect(ctx, UiBounds.of(cx, cy + CELL - 1, CELL, 1), SLOT_LIGHT);
        UiRenderer.rect(ctx, UiBounds.of(cx + CELL - 1, cy, 1, CELL), SLOT_LIGHT);
    }

    private ItemStack slotAt(double mx, double my) {
        if (my < gridTop() || my > gridTop() + gridAreaH()) return ItemStack.EMPTY;
        int gl = gridLeft(), gt = gridTop() - scrollY;
        for (MmBlobs.SlotView slot : slots) {
            int itemX = gl + slot.x(), itemY = gt + slot.y();
            if (mx >= itemX && mx < itemX + 16 && my >= itemY && my < itemY + 16) {
                return slot.item() == null ? ItemStack.EMPTY : slot.item();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        if (isOverCloseButton(mx, my, bounds)) { setVisible(false); isDragging = false; return true; }
        if (isOverCollapseButton(mx, my, bounds)) { setCollapsed(!collapsed); return true; }
        CompactScrollbar.Metrics sb = scrollbarMetrics();
        if (button == 0 && sb.overThumb(mx, my)) {
            scrollbarDragging = true; scrollGrab = (int) Math.round(my) - sb.thumbY(); return true;
        }
        if (button == 0 && isOverDragBar(mx, my)) {
            isDragging = true; dragOffsetX = mx - panelX; dragOffsetY = my - panelY; return true;
        }
        if (collapsed) return false;
        ItemStack st = slotAt(mx, my);
        if (st != null && !st.isEmpty()) {
            AutismItemNbtInspectOverlay ov = AutismItemNbtInspectOverlay.getSharedOverlay(font);
            if (ov != null) {
                ov.open(st, (int) mx + 8, (int) my);
                AutismOverlayManager.get().register(ov, OverlayScope.BACKGROUND_STATUS);
                AutismOverlayManager.get().bringToFront(ov);
            }
            return true;
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
        scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) Math.signum(amount) * CELL));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override public boolean charTyped(char chr, int modifiers) { return false; }
}
