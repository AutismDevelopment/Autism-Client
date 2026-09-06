package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;

public final class CompactToolbar {
    private CompactToolbar() {
    }

    public static UiBounds cell(UiBounds row, int count, int index, int gap) {
        return CompactActionGrid.cell(row, count, index, gap);
    }
}
