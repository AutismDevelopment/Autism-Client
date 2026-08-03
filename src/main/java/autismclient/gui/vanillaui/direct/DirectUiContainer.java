package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DirectUiContainer extends DirectUiNode {
    protected final List<DirectUiNode> children = new ArrayList<>();

    public <T extends DirectUiNode> T add(T child) {
        if (child != null) {
            children.add(child);
            markLayoutDirty();
        }
        return child;
    }

    public void clearChildren() {
        if (!children.isEmpty()) {
            children.clear();
            markLayoutDirty();
        }
    }

    public List<DirectUiNode> children() {
        return Collections.unmodifiableList(children);
    }

    protected abstract void layoutChildren(DirectRenderContext context);

    @Override
    public void layout(DirectRenderContext context) {
        if (!visible) return;
        layoutChildren(context);
        for (DirectUiNode child : children) {
            if (child != null && child.isVisible()) {
                child.layout(context);
            }
        }
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        for (DirectUiNode child : children) {
            if (child != null && child.isVisible()) {
                child.render(context);
            }
        }
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.contains(mouseX, mouseY) && child.mouseClicked(context, mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.mouseReleased(context, mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.mouseDragged(context, mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(DirectRenderContext context, float mouseX, float mouseY, float amount) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.contains(mouseX, mouseY) && child.mouseScrolled(context, mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(DirectRenderContext context, int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.keyPressed(context, keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(DirectRenderContext context, char chr, int modifiers) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            DirectUiNode child = children.get(i);
            if (child != null && child.isVisible() && child.charTyped(context, chr, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
