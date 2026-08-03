package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;

public final class CompactForm {
    private CompactForm() {
    }

    public static UiBounds cell(UiBounds row, int count, int index, int gap) {
        return CompactActionGrid.cell(row, count, index, gap);
    }

    public static UiBounds inset(UiBounds bounds, int horizontal, int vertical) {
        if (bounds == null) return UiBounds.of(0, 0, 0, 0);
        return UiBounds.of(
            bounds.x() + Math.max(0, horizontal),
            bounds.y() + Math.max(0, vertical),
            Math.max(0, bounds.width() - Math.max(0, horizontal) * 2),
            Math.max(0, bounds.height() - Math.max(0, vertical) * 2)
        );
    }
}
