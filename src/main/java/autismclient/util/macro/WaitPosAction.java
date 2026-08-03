package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitPosAction implements MacroAction, MacroCaptureOutput {
    public double x = 0;
    public double y = 0;
    public double z = 0;
    public double leeway = 1.0;

    public boolean checkRotation = false;
    public float yaw = 0;
    public float pitch = 0;
    public float rotLeeway = 5.0f;

    private boolean enabled = true;
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";

    public WaitPosAction() {}

    public WaitPosAction(double x, double y, double z, double leeway) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.leeway = leeway;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putDouble("leeway", leeway);
        tag.putBoolean("checkRotation", checkRotation);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putFloat("rotLeeway", rotLeeway);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("x")) x = tag.getDoubleOr("x", 0);
        if (tag.contains("y")) y = tag.getDoubleOr("y", 0);
        if (tag.contains("z")) z = tag.getDoubleOr("z", 0);
        if (tag.contains("leeway")) leeway = tag.getDoubleOr("leeway", 1.0);
        if (tag.contains("checkRotation")) checkRotation = tag.getBooleanOr("checkRotation", false);
        if (tag.contains("yaw")) yaw = tag.getFloatOr("yaw", 0);
        if (tag.contains("pitch")) pitch = tag.getFloatOr("pitch", 0);
        if (tag.contains("rotLeeway")) rotLeeway = tag.getFloatOr("rotLeeway", 5.0f);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_POS;
    }

    @Override
    public String getDisplayName() {
        if (checkRotation) {
            return String.format("Wait Pos & Rot (%.0f, %.0f, %.0f)", x, y, z);
        }
        return String.format("Wait Pos (%.0f, %.0f, %.0f) +/-%.1f", x, y, z, leeway);
    }

    @Override
    public String getIcon() {
        return "LOC";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
