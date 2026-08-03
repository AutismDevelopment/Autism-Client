package autismclient.gui.vanillaui;

public final class UiInputRouter {
    private final UiLayerManager layers;

    public UiInputRouter(UiLayerManager layers) {
        this.layers = layers;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return layers.mouseClicked(mouseX, mouseY, button) == UiInputResult.HANDLED;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return layers.mouseReleased(mouseX, mouseY, button) == UiInputResult.HANDLED;
    }

    public boolean mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        return layers.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        return layers.mouseScrolled(mouseX, mouseY, amount) == UiInputResult.HANDLED;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return layers.keyPressed(key, scanCode, modifiers) == UiInputResult.HANDLED;
    }

    public boolean charTyped(char chr) {
        return layers.charTyped(chr) == UiInputResult.HANDLED;
    }
}
