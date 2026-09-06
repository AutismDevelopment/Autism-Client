package autismclient.modules;
import autismclient.api.module.*;

public final class BlinkModule extends Module {

    public BlinkModule() {
        super("blink", "Blink", ModuleCategory.MISC, "Suspends packets to/from the server, released on disable.");
        add(new BoolSetting("outgoing", "Outgoing", true)
            .description("Hold sent packets").build());
        add(new BoolSetting("incoming", "Incoming", false)
            .description("Hold received packets").build());

        add(new BoolSetting("hold-movement", "Hold Movement", true)
            .visibleWhen(() -> bool("outgoing"))
            .description("Delay movement packets").build());
        add(new BoolSetting("hold-actions", "Hold Attack/Interact", true)
            .visibleWhen(() -> bool("outgoing"))
            .description("Delay action packets").build());
        add(new BoolSetting("show-position", "Show Position", true)
            .description("Draw server position").build());
        add(new BoolSetting("auto-reset", "Auto Reset", false)
            .description("Flush periodically").build());

        add(new IntSetting("reset-after", "Reset After (ticks)", 50, 1, 100000, 10)
            .sliderRange(1, 100)
            .visibleWhen(() -> bool("auto-reset"))
            .description("Ticks between flushes").build());
    }

    @Override
    public void onEnable() {
        pushConfig();
        AutismBlinkManager.captureServerPos();
    }

    @Override
    public void onDisable() {
        AutismBlinkManager.disableAndFlush();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if (isEnabled()) pushConfig();
    }

    @Override
    public String info() {
        int held = AutismBlinkManager.held();
        if (held <= 0) return "";
        int reset = AutismBlinkManager.ticksUntilReset();

        return reset >= 0
            ? held + " | " + String.format(java.util.Locale.ROOT, "%.1fs", reset / 20.0)
            : Integer.toString(held);
    }

    private void pushConfig() {
        AutismBlinkManager.setDirections(bool("incoming"), bool("outgoing"));
        AutismBlinkManager.setScope(bool("hold-movement"), bool("hold-actions"));
        AutismBlinkManager.setShowPosition(bool("show-position"));
        AutismBlinkManager.setAutoReset(bool("auto-reset"), integer("reset-after"));
    }
}
