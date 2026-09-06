package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;

public final class CompactActionGrid {
    private CompactActionGrid() {
    }

    public static UiBounds cell(UiBounds row, int count, int index, int gap) {
        int safeCount = Math.max(1, count);
        int safeGap = Math.max(0, gap);
        int width = Math.max(1, (row.width() - safeGap * (safeCount - 1)) / safeCount);
        int x = row.x() + index * (width + safeGap);
        int resolvedWidth = index == safeCount - 1 ? Math.max(1, row.right() - x) : width;
        return UiBounds.of(x, row.y(), resolvedWidth, row.height());
    }
}
