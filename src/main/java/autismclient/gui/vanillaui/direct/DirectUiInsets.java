package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

public record DirectUiInsets(int left, int top, int right, int bottom) {
    public static final DirectUiInsets NONE = new DirectUiInsets(0, 0, 0, 0);

    public static DirectUiInsets all(int value) {
        return new DirectUiInsets(value, value, value, value);
    }

    public static DirectUiInsets symmetric(int horizontal, int vertical) {
        return new DirectUiInsets(horizontal, vertical, horizontal, vertical);
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }
}
