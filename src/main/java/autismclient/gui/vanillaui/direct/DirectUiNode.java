package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

public abstract class DirectUiNode {
    private static volatile long globalLayoutRevision = 1L;

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean growX = false;
    protected float hoverProgress = 0.0f;

    public static long globalLayoutRevision() {
        return globalLayoutRevision;
    }

    protected void markLayoutDirty() {
        globalLayoutRevision++;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public DirectUiNode setBounds(float x, float y, float width, float height) {
        float nextWidth = Math.max(0, width);
        float nextHeight = Math.max(0, height);
        if (Float.compare(this.x, x) != 0
            || Float.compare(this.y, y) != 0
            || Float.compare(this.width, nextWidth) != 0
            || Float.compare(this.height, nextHeight) != 0) {
            this.x = x;
            this.y = y;
            this.width = nextWidth;
            this.height = nextHeight;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiNode setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiNode setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiNode setGrowX(boolean growX) {
        if (this.growX != growX) {
            this.growX = growX;
            markLayoutDirty();
        }
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean growX() {
        return growX;
    }

    public boolean contains(float px, float py) {
        return visible && px >= x && px <= x + width && py >= y && py <= y + height;
    }

    protected float animate(float current, float target, float speed, float delta) {
        float amount = Math.max(0.04f, delta * speed);
        if (current < target) return Math.min(target, current + amount);
        if (current > target) return Math.max(target, current - amount);
        return current;
    }

    protected float updateHover(boolean hovered, float delta) {
        hoverProgress = animate(hoverProgress, hovered ? 1.0f : 0.0f, 7.5f, delta);
        return hoverProgress;
    }

    public float preferredWidth(DirectRenderContext context) {
        return width;
    }

    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return height;
    }

    public void layout(DirectRenderContext context) {
    }

    public void render(DirectRenderContext context) {
    }

    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
        return false;
    }

    public boolean mouseScrolled(DirectRenderContext context, float mouseX, float mouseY, float amount) {
        return false;
    }

    public boolean keyPressed(DirectRenderContext context, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(DirectRenderContext context, char chr, int modifiers) {
        return false;
    }
}
