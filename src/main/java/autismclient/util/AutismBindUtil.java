package autismclient.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class AutismBindUtil {
    private static final int MOUSE_BIND_BASE = -1000;

    private AutismBindUtil() {
    }

    public static boolean isMouseBind(int bindCode) {
        return bindCode <= MOUSE_BIND_BASE;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_BIND_BASE - button;
    }

    public static int decodeMouseButton(int bindCode) {
        return MOUSE_BIND_BASE - bindCode;
    }

    public static boolean isAllowedMouseButton(int button) {
        return button > GLFW.GLFW_MOUSE_BUTTON_LEFT && button <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }

    public static boolean isBindPressed(Minecraft client, int bindCode) {
        if (bindCode == -1 || client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().handle();
        if (isMouseBind(bindCode)) {
            int button = decodeMouseButton(bindCode);
            return isAllowedMouseButton(button) && GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, bindCode) == GLFW.GLFW_PRESS;
    }

    public static String getBindName(int bindCode) {
        if (bindCode == -1) return "None";
        if (isMouseBind(bindCode)) return getMouseButtonName(decodeMouseButton(bindCode));
        String name = GLFW.glfwGetKeyName(bindCode, 0);
        if (name != null) return name.toUpperCase();
        return switch (bindCode) {
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L.Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R.Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L.Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R.Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L.Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R.Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CapsLk";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backsp";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NumLk";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PrtSc";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "ScrLk";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            default -> "Key " + bindCode;
        };
    }

    public static String getMouseButtonName(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
            default -> "M" + (button + 1);
        };
    }
}
