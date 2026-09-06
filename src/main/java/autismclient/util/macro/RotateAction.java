package autismclient.util.macro;

import net.minecraft.client.Minecraft;

public class RotateAction implements MacroAction {
    public float yaw;
    public float pitch;
    public boolean smooth;
    public int smoothness = 6;
    public boolean waitForCompletion = true;

    public RotateAction() {}

    public RotateAction(float yaw, float pitch, boolean smooth, int smoothness) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.smooth = smooth;
        this.smoothness = clampSmoothness(smoothness);
    }

    public static int clampSmoothness(int value) {
        return Math.max(1, Math.min(10, value));
    }

    public static double smoothnessToRotationStep(int smoothness) {
        double minStep = 0.5;
        double maxStep = 9.05;
        double normalized = (10.0 - clampSmoothness(smoothness)) / 9.0;
        return minStep * Math.pow(maxStep / minStep, normalized);
    }

    public double getRotationStep() {
        return smoothnessToRotationStep(smoothness);
    }

    @Override
    public void execute(Minecraft mc) {

        if (mc.player != null && !smooth) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.ROTATE;
    }

    @Override
    public net.minecraft.nbt.CompoundTag toTag() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("type", getType().name());
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putBoolean("smooth", smooth);
        tag.putInt("smoothness", clampSmoothness(smoothness));
        tag.putBoolean("waitForCompletion", waitForCompletion);
        return tag;
    }

    @Override
    public void fromTag(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("yaw")) yaw = tag.getFloatOr("yaw", 0.0f);
        if (tag.contains("pitch")) pitch = tag.getFloatOr("pitch", 0.0f);
        if (tag.contains("smooth")) smooth = tag.getBooleanOr("smooth", false);
        if (tag.contains("smoothness")) smoothness = clampSmoothness(tag.getIntOr("smoothness", 6));
        else smoothness = 6;
        if (tag.contains("waitForCompletion")) waitForCompletion = tag.getBooleanOr("waitForCompletion", true);
        else waitForCompletion = true;
    }

    @Override
    public String getDisplayName() {
        String suffix = smooth ? " (Smooth)" : "";
        if (!waitForCompletion) suffix += " [NoWait]";
        return String.format("Rot %.1f / %.1f%s", yaw, pitch, suffix);
    }

    @Override
    public String getIcon() {
        return "R";
    }
}
