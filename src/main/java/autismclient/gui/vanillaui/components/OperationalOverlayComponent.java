package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiComponent;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.util.AutismWindowLayout;
import autismclient.util.AutismWindow;
import autismclient.util.IAutismOverlay;

public final class OperationalOverlayComponent implements UiComponent {
    private final IAutismOverlay overlay;
    private boolean renderSuppressed;
    private boolean hoverBlocked;
    private boolean inputSuppressed;

    public OperationalOverlayComponent(IAutismOverlay overlay) {
        this.overlay = overlay;
    }

    public void setRenderSuppressed(boolean renderSuppressed) {
        this.renderSuppressed = renderSuppressed;
    }

    public void setHoverBlocked(boolean hoverBlocked) {
        this.hoverBlocked = hoverBlocked;
    }

    public void setInputSuppressed(boolean inputSuppressed) {
        this.inputSuppressed = inputSuppressed;
    }

    @Override
    public UiBounds bounds() {
        AutismWindowLayout bounds = overlay.getBounds();
        return UiBounds.of(bounds.x, bounds.y, bounds.width,
            bounds.collapsed ? AutismWindow.sharedHeaderHeight() : bounds.height);
    }

    @Override
    public void setBounds(UiBounds bounds) {
        if (bounds == null) return;
        AutismWindowLayout current = overlay.getBounds();
        overlay.setBounds(new AutismWindowLayout(bounds.x(), bounds.y(), bounds.width(), bounds.height(), current.visible, current.collapsed));
    }

    @Override
    public UiBounds hitBounds() {
        return inputSuppressed || !overlay.isVisible() ? null : bounds();
    }

    @Override
    public void render(UiContext context) {
        if (renderSuppressed || !overlay.isVisible()) return;
        long perfStart = autismclient.util.AutismPerf.beginSampled();
        overlay.render(context.graphics(), hoverBlocked ? -10000 : context.mouseX(), hoverBlocked ? -10000 : context.mouseY(), context.delta());

        if (perfStart != 0L) autismclient.util.AutismPerf.end("overlay." + overlay.getOverlayId(), perfStart);
    }

    @Override
    public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
        overlay.mouseClicked(mouseX, mouseY, button);
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
        return handled(overlay.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        return handled(overlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
    }

    @Override
    public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
        overlay.mouseScrolled(mouseX, mouseY, amount);
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult keyPressed(int key, int scanCode, int modifiers) {
        return handled(overlay.keyPressed(key, scanCode, modifiers));
    }

    @Override
    public UiInputResult charTyped(char chr) {
        return charTyped(chr, 0);
    }

    public UiInputResult charTyped(char chr, int modifiers) {
        return handled(overlay.charTyped(chr, modifiers));
    }

    private static UiInputResult handled(boolean handled) {
        return handled ? UiInputResult.HANDLED : UiInputResult.IGNORED;
    }
}
