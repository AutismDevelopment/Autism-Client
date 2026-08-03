package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public class WaitForHealthAction implements MacroAction, MacroCaptureOutput {
    public static final String COMPARISON_DROPS_BELOW = "Drops Below";
    public static final String COMPARISON_RISES_ABOVE = "Rises Above";

    public float healthThreshold = 20.0f;
    public boolean below = true;
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";

    public WaitForHealthAction() {}

    public WaitForHealthAction(float healthThreshold, boolean below) {
        this.healthThreshold = healthThreshold;
        this.below = below;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putFloat("healthThreshold", healthThreshold);
        tag.putBoolean("below", below);
        tag.putString("comparison", comparisonLabel());
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("healthThreshold")) healthThreshold = tag.getFloatOr("healthThreshold", 20.0f);
        if (tag.contains("comparison")) {
            below = COMPARISON_DROPS_BELOW.equals(tag.getStringOr("comparison", COMPARISON_DROPS_BELOW));
        } else if (tag.contains("below")) {
            below = tag.getBooleanOr("below", true);
        }
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_HEALTH;
    }

    @Override
    public String getDisplayName() {
        return "Wait HP: " + comparisonLabel() + " " + thresholdText();
    }

    @Override
    public String getIcon() {
        return "HP";
    }

    public String comparisonLabel() {
        return below ? COMPARISON_DROPS_BELOW : COMPARISON_RISES_ABOVE;
    }

    public String thresholdText() {
        return String.format(Locale.ROOT, "%.1f", healthThreshold);
    }

    public String waitingStatusText() {
        return below
            ? "Waiting for health to drop below " + thresholdText()
            : "Waiting for health to rise above " + thresholdText();
    }

    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
