package autismclient.gui.multi;

import org.lwjgl.glfw.GLFW;

public final class MultiRenameField {
    private static final int MAX = 50;
    private final StringBuilder text = new StringBuilder();
    private boolean focused;

    public boolean focused() {
        return focused;
    }

    public void focus() {
        focused = true;
    }

    public void blur() {
        focused = false;
    }

    public String text() {
        return text.toString();
    }

    public void set(String value) {
        text.setLength(0);
        if (value != null) text.append(value.length() > MAX ? value.substring(0, MAX) : value);
    }

    public boolean charTyped(char c) {
        if (!focused) return false;
        if (c >= ' ' && c != 127 && text.length() < MAX) {
            text.append(c);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!focused) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (text.length() > 0) text.deleteCharAt(text.length() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
