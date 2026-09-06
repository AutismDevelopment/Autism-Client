package autismclient.gui.vanillaui;

public final class UiTheme {
    private final UiColors colors = new UiColors();
    private final UiSpacing spacing = new UiSpacing();
    private final UiTypography typography = new UiTypography();

    public UiColors colors() {
        return colors;
    }

    public void refresh() {
        colors.recompute();
    }

    public UiSpacing spacing() {
        return spacing;
    }

    public UiTypography typography() {
        return typography;
    }
}
