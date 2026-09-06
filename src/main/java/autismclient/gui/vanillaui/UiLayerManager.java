package autismclient.gui.vanillaui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class UiLayerManager {
    private final Map<UiLayer, List<UiComponent>> layers = new EnumMap<>(UiLayer.class);

    public void clear() {
        layers.clear();
    }

    public void add(UiLayer layer, UiComponent component) {
        if (layer == null || component == null) return;
        layers.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(component);
    }

    public void remove(UiLayer layer, UiComponent component) {
        if (layer == null || component == null) return;
        List<UiComponent> components = layers.get(layer);
        if (components == null) return;
        components.remove(component);
        if (components.isEmpty()) layers.remove(layer);
    }

    public void bringToFront(UiLayer layer, UiComponent component) {
        if (layer == null || component == null) return;
        remove(layer, component);
        add(layer, component);
    }

    public void clear(UiLayer layer) {
        if (layer != null) layers.remove(layer);
    }

    public void render(UiContext context) {
        boolean first = true;
        for (UiLayer layer : UiLayer.values()) {
            List<UiComponent> components = layers.get(layer);
            if (components == null) continue;
            for (UiComponent component : components) {
                if (!first) context.graphics().nextStratum();
                first = false;
                UiScissorStack.global().clear(context.graphics());
                try {
                    component.render(context);
                } finally {

                    UiScissorStack.global().clear(context.graphics());
                }
            }
        }
    }

    public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
        return routePointer(mouseX, mouseY, (component) -> component.mouseClicked(mouseX, mouseY, button));
    }

    public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
        return routePointer(mouseX, mouseY, (component) -> component.mouseReleased(mouseX, mouseY, button));
    }

    public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        return routePointer(mouseX, mouseY, (component) -> component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
    }

    public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
        return routePointer(mouseX, mouseY, (component) -> component.mouseScrolled(mouseX, mouseY, amount));
    }

    public UiInputResult keyPressed(int key, int scanCode, int modifiers) {
        return route((component) -> component.keyPressed(key, scanCode, modifiers));
    }

    public UiInputResult charTyped(char chr) {
        return route((component) -> component.charTyped(chr));
    }

    private UiInputResult route(InputCall call) {
        UiLayer[] order = UiLayer.values();
        for (int layerIndex = order.length - 1; layerIndex >= 0; layerIndex--) {
            UiLayer layer = order[layerIndex];
            List<UiComponent> components = layers.get(layer);
            if (components == null || components.isEmpty()) continue;
            for (int i = components.size() - 1; i >= 0; i--) {
                if (call.invoke(components.get(i)) == UiInputResult.HANDLED) return UiInputResult.HANDLED;
            }
            if (layer == UiLayer.MODAL) return UiInputResult.HANDLED;
        }
        return UiInputResult.IGNORED;
    }

    private UiInputResult routePointer(int mouseX, int mouseY, InputCall call) {
        UiLayer[] order = UiLayer.values();
        for (int layerIndex = order.length - 1; layerIndex >= 0; layerIndex--) {
            UiLayer layer = order[layerIndex];
            List<UiComponent> components = layers.get(layer);
            if (components == null || components.isEmpty()) continue;
            for (int i = components.size() - 1; i >= 0; i--) {
                UiComponent component = components.get(i);
                UiBounds bounds = component.hitBounds();
                if (bounds == null || !bounds.contains(mouseX, mouseY)) continue;
                if (call.invoke(component) == UiInputResult.HANDLED) return UiInputResult.HANDLED;
            }
            if (blocksLowerPointerInput(layer)) return UiInputResult.HANDLED;
        }
        return UiInputResult.IGNORED;
    }

    private static boolean blocksLowerPointerInput(UiLayer layer) {
        return layer == UiLayer.DROPDOWN || layer == UiLayer.MODAL;
    }

    @FunctionalInterface
    private interface InputCall {
        UiInputResult invoke(UiComponent component);
    }
}
