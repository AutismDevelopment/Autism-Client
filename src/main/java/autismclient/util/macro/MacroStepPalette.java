package autismclient.util.macro;

public final class MacroStepPalette {
    public static final int DONE = 0xFF555555;
    public static final int WAIT = 0xFFC6C6C6;

    private MacroStepPalette() {
    }

    public static int colorFor(int index, int current, int lastCompletedStep, int nowColor) {
        if (index == current) return nowColor;
        return index < lastCompletedStep ? DONE : WAIT;
    }
}
