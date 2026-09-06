package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.components.FocusableTextInput;
import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.direct.DirectViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DirectSurface {
    private final CompactTheme theme;
    private final DirectUiNode root;
    private final float density;
    private long lastLayoutRevision = Long.MIN_VALUE;
    private float lastLayoutViewportWidth = Float.NaN;
    private float lastLayoutViewportHeight = Float.NaN;
    private float lastRootX = Float.NaN;
    private float lastRootY = Float.NaN;
    private float lastRootWidth = Float.NaN;
    private float lastRootHeight = Float.NaN;

    public DirectSurface(CompactTheme theme, DirectUiNode root) {
        this(theme, root, CompactTheme.DEFAULT_DENSITY);
    }

    public DirectSurface(CompactTheme theme, DirectUiNode root, float density) {
        this.theme = theme == null ? new CompactTheme() : theme;
        this.root = root;
        this.density = density <= 0 ? CompactTheme.DEFAULT_DENSITY : density;
    }

    public DirectViewport viewport() {
        return DirectViewport.current(density);
    }

    public void render(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float delta) {
        if (root == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        DirectViewport viewport = viewport();
        viewport.push(drawContext);
        try {
            DirectRenderContext context = new DirectRenderContext(
                drawContext,
                mc.font,
                viewport,
                theme,
                viewport.toUiX(mouseX),
                viewport.toUiY(mouseY),
                delta
            );
            layoutIfNeeded(context, viewport);
            root.render(context);
        } finally {
            viewport.pop(drawContext);
        }
    }

    public void invalidateLayout() {
        lastLayoutRevision = Long.MIN_VALUE;
    }

    private void layoutIfNeeded(DirectRenderContext context, DirectViewport viewport) {
        long revision = DirectUiNode.globalLayoutRevision();
        boolean viewportChanged = Float.compare(lastLayoutViewportWidth, viewport.uiWidth()) != 0
            || Float.compare(lastLayoutViewportHeight, viewport.uiHeight()) != 0;
        boolean rootBoundsChanged = Float.compare(lastRootX, root.x()) != 0
            || Float.compare(lastRootY, root.y()) != 0
            || Float.compare(lastRootWidth, root.width()) != 0
            || Float.compare(lastRootHeight, root.height()) != 0;

        if (!viewportChanged && !rootBoundsChanged && revision == lastLayoutRevision) {
            return;
        }

        root.layout(context);
        lastLayoutRevision = DirectUiNode.globalLayoutRevision();
        lastLayoutViewportWidth = viewport.uiWidth();
        lastLayoutViewportHeight = viewport.uiHeight();
        lastRootX = root.x();
        lastRootY = root.y();
        lastRootWidth = root.width();
        lastRootHeight = root.height();
    }

    public DirectRenderContext measurementContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return null;
        DirectViewport viewport = viewport();
        return new DirectRenderContext(null, mc.font, viewport, theme, 0, 0, 0);
    }

    public float measurePreferredHeight(float availableWidth) {
        if (root == null) return 0.0f;
        DirectRenderContext context = measurementContext();
        if (context == null) return 0.0f;
        return root.preferredHeight(context, availableWidth);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0);
        return root.mouseClicked(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0);
        return root.mouseReleased(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0);
        return root.mouseDragged(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), button, (float) (deltaX / viewport.drawScaleX()), (float) (deltaY / viewport.drawScaleY()));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0);
        return root.mouseScrolled(context, viewport.toUiX(mouseX), viewport.toUiY(mouseY), (float) amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, 0, 0, 0);
        return root.keyPressed(context, keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        if (root == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;
        DirectViewport viewport = viewport();
        DirectRenderContext context = new DirectRenderContext(null, mc.font, viewport, theme, 0, 0, 0);
        return root.charTyped(context, chr, modifiers);
    }

    public boolean hasFocusedTextInput() {
        return hasFocusedTextInput(root);
    }

    public void clearFocusedTextInputs() {
        clearFocusedTextInputs(root);
    }

    private boolean hasFocusedTextInput(DirectUiNode node) {
        if (node == null || !node.isVisible()) return false;
        if (node instanceof FocusableTextInput input && input.isFocused()) return true;
        if (node instanceof DirectUiContainer container) {
            for (DirectUiNode child : container.children()) {
                if (hasFocusedTextInput(child)) return true;
            }
        }
        return false;
    }

    private void clearFocusedTextInputs(DirectUiNode node) {
        if (node == null || !node.isVisible()) return;
        if (node instanceof FocusableTextInput input) input.setFocused(false);
        if (node instanceof DirectUiContainer container) {
            for (DirectUiNode child : container.children()) {
                clearFocusedTextInputs(child);
            }
        }
    }
}
