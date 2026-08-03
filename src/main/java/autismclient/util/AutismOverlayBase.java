package autismclient.util;

public abstract class AutismOverlayBase extends AutismWindow implements IAutismOverlay {
    private final String overlayId;

    protected int panelX;
    protected int panelY;
    protected int panelWidth;
    protected int panelHeight;
    protected boolean visible;
    protected boolean collapsed;

    private AutismContextMenu<?> contextMenu;

    protected AutismOverlayBase() {
        this(null, 0, 0);
    }

    protected AutismOverlayBase(String overlayId, int initialWidth, int initialHeight) {
        this.overlayId = overlayId;
        this.panelWidth = initialWidth;
        this.panelHeight = initialHeight;
    }

    @Override
    public String getOverlayId() {
        return overlayId != null ? overlayId : getClass().getSimpleName();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean v) {
        visible = v;
        if (!v) {
            clearHiddenInteractionState();
        }
        saveLayout();
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean c) {
        if (collapsed == c) return;
        collapsed = c;
        if (c) {
            clearHiddenInteractionState();
        }
        saveLayout();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout c = clampToScreen(this, bounds);
        panelX = c.x;
        panelY = c.y;
        panelWidth = c.width;
        panelHeight = c.height;
        visible = c.visible;
        collapsed = c.collapsed;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        if (!visible) return false;
        int frameH = currentFrameHeight();
        boolean overPanel = mx >= panelX && mx <= panelX + panelWidth
            && my >= panelY && my <= panelY + frameH;
        if (contextMenu != null && contextMenu.isMouseOver(mx, my)) return true;
        return overPanel;
    }

    @Override
    public boolean isOverDragBar(double mx, double my) {
        if (!visible) return false;
        if (mx < panelX || mx > panelX + panelWidth) return false;
        if (my < panelY || my > panelY + HEADER_HEIGHT) return false;
        return !isOverWindowControl(mx, my, getBounds());
    }

    protected int currentFrameHeight() {
        return collapsed ? HEADER_HEIGHT : panelHeight;
    }

    protected void setContextMenu(AutismContextMenu<?> menu) {
        this.contextMenu = menu;
    }

    protected AutismContextMenu<?> contextMenu() {
        return contextMenu;
    }

    protected final void clearHiddenInteractionState() {
        clearTextFieldFocus();
        if (contextMenu != null) contextMenu.close();
    }

    protected int hoverBlocked(int mouse, int axis) {
        if (contextMenu == null || !contextMenu.isOpen()) return mouse;
        int otherAxis = axis == 0 ? lastSeenMy : lastSeenMx;
        boolean covered = axis == 0
            ? contextMenu.isMouseOver(mouse, otherAxis)
            : contextMenu.isMouseOver(otherAxis, mouse);
        return covered ? AutismOverlayManager.HOVER_BLOCKED_MOUSE : mouse;
    }

    private int lastSeenMx;
    private int lastSeenMy;

    protected final void recordMouse(int mx, int my) {
        this.lastSeenMx = mx;
        this.lastSeenMy = my;
    }

    protected int bodyMouseX(int mx, int my) {
        if (contextMenu != null && contextMenu.isMouseOver(mx, my)) {
            return AutismOverlayManager.HOVER_BLOCKED_MOUSE;
        }
        return mx;
    }

    protected int bodyMouseY(int mx, int my) {
        if (contextMenu != null && contextMenu.isMouseOver(mx, my)) {
            return AutismOverlayManager.HOVER_BLOCKED_MOUSE;
        }
        return my;
    }
}
