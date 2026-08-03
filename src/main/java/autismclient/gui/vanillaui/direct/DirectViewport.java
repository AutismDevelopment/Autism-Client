package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiScissorStack;

import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DirectViewport {
    private final float uiWidth;
    private final float uiHeight;

    private DirectViewport(float uiWidth, float uiHeight) {
        this.uiWidth = Math.max(1.0f, uiWidth);
        this.uiHeight = Math.max(1.0f, uiHeight);
    }

    public static DirectViewport current(float density) {
        return new DirectViewport(
            AutismUiScale.getVirtualScreenWidth(),
            AutismUiScale.getVirtualScreenHeight()
        );
    }

    public float uiWidth() {
        return uiWidth;
    }

    public float uiHeight() {
        return uiHeight;
    }

    public float drawScaleX() {
        return 1.0f;
    }

    public float drawScaleY() {
        return 1.0f;
    }

    public float toUiX(double screenX) {
        return (float) screenX;
    }

    public float toUiY(double screenY) {
        return (float) screenY;
    }

    public void push(GuiGraphicsExtractor context) {
        AutismUiScale.pushOverlayScale(context);
    }

    public void pop(GuiGraphicsExtractor context) {
        AutismUiScale.popOverlayScale(context);
    }

    public void enableScissor(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2) {
        int left = (int) Math.floor(Math.min(x1, x2));
        int top = (int) Math.floor(Math.min(y1, y2));
        int right = (int) Math.ceil(Math.max(x1, x2));
        int bottom = (int) Math.ceil(Math.max(y1, y2));
        UiScissorStack.global().push(context, UiBounds.of(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
    }

    public void disableScissor(GuiGraphicsExtractor context) {
        UiScissorStack.global().pop(context);
    }
}
