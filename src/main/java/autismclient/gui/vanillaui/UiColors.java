package autismclient.gui.vanillaui;

import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;

public final class UiColors {
    public int screenScrim;
    public int window;
    public int windowStrong;
    public int header;
    public int headerHover;
    public int row;
    public int rowAlt;
    public int rowHover;
    public int field;
    public int fieldFocused;
    public int border;
    public int borderSoft;
    public int buttonBorder;
    public int hairline;
    public int hoverVeil;
    public int accent;
    public int accentDark;
    public int accentSoft;
    public int success;
    public int successSoft;
    public int text;
    public int muted;
    public int disabled;
    public int bad;

    public UiColors() {
        recompute();
    }

    public void recompute() {
        screenScrim = 0x66000000;
        window = 0xD80B0C10;
        windowStrong = 0xEE0A0A0D;
        header = 0xF0181A20;
        headerHover = 0xFF242832;

        row = 0xB012141A;
        rowAlt = 0x94161A21;
        rowHover = 0xC91D222B;
        field = 0xD9121419;
        fieldFocused = 0xE81A1E26;
        border = AutismTheme.recolor(0xFFCE4949, Channel.OUTLINE);
        borderSoft = AutismTheme.recolor(0xCC9F3A3A, Channel.OUTLINE);

        buttonBorder = AutismTheme.recolor(0xE0A85E5E, Channel.OUTLINE);
        hairline = 0x2BFFFFFF;
        hoverVeil = 0x10FFFFFF;
        accent = AutismTheme.recolor(0xFFFF3B3B, Channel.ACCENT);
        accentDark = AutismTheme.recolor(0xFF8F1F24, Channel.ACCENT);
        accentSoft = AutismTheme.recolor(0x44FF3B3B, Channel.ACCENT);
        success = AutismTheme.recolor(0xFF3FE87E, Channel.SUCCESS);
        successSoft = AutismTheme.recolor(0x663FE87E, Channel.SUCCESS);
        text = AutismTheme.recolor(0xFFF3ECE7, Channel.TEXT);
        muted = AutismTheme.recolor(0xFFB79E9E, Channel.TEXT);
        disabled = AutismTheme.recolor(0xFF766A6A, Channel.TEXT);
        bad = AutismTheme.recolor(0xFFFF5555, Channel.DANGER);
    }
}
