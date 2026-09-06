package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public class FpsAction implements MacroAction {

    public enum EndMode {
        TIMEOUT,
        STEPS,
        END;

        public static EndMode parse(String raw) {
            if (raw == null) return TIMEOUT;
            try {
                return EndMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TIMEOUT;
            }
        }
    }

    public int maxFps = 60;
    public String endMode = EndMode.TIMEOUT.name();
    public int stepCount = 1;
    public boolean useTicks = false;
    public int durationMs = 1000;
    public int durationTicks = 20;
    public boolean continueNextActions = false;
    private boolean enabled = true;

    public FpsAction() {}

    public FpsAction(int maxFps, int durationMs) {
        this.maxFps = Math.max(0, maxFps);
        this.durationMs = Math.max(0, durationMs);
    }

    public EndMode endMode() {
        return EndMode.parse(endMode);
    }

    public int normalizedStepCount() {
        return Math.max(1, stepCount);
    }

    public long durationMillis() {
        return useTicks ? Math.max(0, durationTicks) * 50L : Math.max(0, durationMs);
    }

    @Override
    public void execute(Minecraft mc) {

        long owner = MacroExecutor.currentRunId();
        FpsLimitController.apply(owner < 0L ? 0L : owner, Math.max(0, maxFps), durationMillis() * 1_000_000L);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("maxFps", maxFps);
        tag.putString("endMode", endMode().name());
        tag.putInt("stepCount", Math.max(1, stepCount));
        tag.putBoolean("useTicks", useTicks);
        tag.putInt("durationMs", durationMs);
        tag.putInt("durationTicks", durationTicks);
        tag.putBoolean("continueNextActions", continueNextActions);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("maxFps")) maxFps = Math.max(0, tag.getIntOr("maxFps", 60));
        if (tag.contains("endMode")) endMode = EndMode.parse(tag.getStringOr("endMode", EndMode.TIMEOUT.name())).name();
        if (tag.contains("stepCount")) stepCount = Math.max(1, tag.getIntOr("stepCount", 1));
        if (tag.contains("useTicks")) useTicks = tag.getBooleanOr("useTicks", false);
        if (tag.contains("durationMs")) durationMs = Math.max(0, tag.getIntOr("durationMs", 1000));
        if (tag.contains("durationTicks")) durationTicks = Math.max(0, tag.getIntOr("durationTicks", 20));
        if (tag.contains("continueNextActions")) continueNextActions = tag.getBooleanOr("continueNextActions", false);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.FPS;
    }

    @Override
    public String getDisplayName() {
        String cap = maxFps <= 0 ? "Freeze" : (maxFps + " fps");
        String window = switch (endMode()) {
            case STEPS -> normalizedStepCount() + (normalizedStepCount() == 1 ? " step" : " steps");
            case END -> "until end";
            case TIMEOUT -> useTicks ? (Math.max(0, durationTicks) + " ticks") : (Math.max(0, durationMs) + " ms");
        };
        String suffix = endMode() == EndMode.TIMEOUT && continueNextActions ? " [cont]" : "";
        return "FPS: " + cap + " (" + window + ")" + suffix;
    }

    @Override
    public String getIcon() {
        return "FPS";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
