package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.util.AutismBindUtil;
import org.lwjgl.glfw.GLFW;

public final class CompactKeybindButton {
    public static final int WIDTH = 34;
    public static final int HEIGHT = 11;

    private CompactKeybindButton() {
    }

    public static UiBounds atRowEnd(UiBounds row, int rightInset, int verticalInset) {
        int height = Math.max(1, row.height() - verticalInset * 2);
        return UiBounds.of(row.right() - WIDTH - rightInset, row.y() + verticalInset, WIDTH, height);
    }

    public static String label(int bindCode, boolean capturing) {
        if (capturing) return "Key";
        return bindCode == -1 ? "Bind" : AutismBindUtil.getBindName(bindCode);
    }

    public static int keyOrClear(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE
            || keyCode == GLFW.GLFW_KEY_BACKSPACE
            || keyCode == GLFW.GLFW_KEY_DELETE
            ? -1
            : keyCode;
    }

    public static void render(UiContext context, UiBounds bounds, int bindCode, boolean capturing, boolean hovered) {
        Button.render(context, bounds, label(bindCode, capturing), Button.Tone.NORMAL, hovered, capturing);
    }
}
