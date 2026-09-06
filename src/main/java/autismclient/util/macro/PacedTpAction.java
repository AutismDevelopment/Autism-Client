package autismclient.util.macro;

import autismclient.util.multi.PacketTeleportController;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public final class PacedTpAction implements MacroAction {
    public double x;
    public double y;
    public double z;
    public boolean relativeX;
    public boolean relativeY;
    public boolean relativeZ;
    public int maxPackets = PacketTeleportController.DEFAULT_MAX_PACKETS;
    public int pauseMs = PacketTeleportController.DEFAULT_PAUSE_MS;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        PacketTeleportController.startMacro(this);
    }

    public String commandArguments() {
        return coordinate(x, relativeX) + " " + coordinate(y, relativeY) + " " + coordinate(z, relativeZ)
            + " " + clamp(maxPackets, 1, 100) + " " + clamp(pauseMs, 50, 10_000);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putDouble("x", finite(x));
        tag.putDouble("y", finite(y));
        tag.putDouble("z", finite(z));
        tag.putBoolean("relativeX", relativeX);
        tag.putBoolean("relativeY", relativeY);
        tag.putBoolean("relativeZ", relativeZ);
        tag.putInt("maxPackets", clamp(maxPackets, 1, 100));
        tag.putInt("pauseMs", clamp(pauseMs, 50, 10_000));
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        x = finite(tag.getDoubleOr("x", 0.0D));
        y = finite(tag.getDoubleOr("y", 0.0D));
        z = finite(tag.getDoubleOr("z", 0.0D));
        relativeX = tag.getBooleanOr("relativeX", false);
        relativeY = tag.getBooleanOr("relativeY", false);
        relativeZ = tag.getBooleanOr("relativeZ", false);
        maxPackets = clamp(tag.getIntOr("maxPackets", PacketTeleportController.DEFAULT_MAX_PACKETS), 1, 100);
        pauseMs = clamp(tag.getIntOr("pauseMs", PacketTeleportController.DEFAULT_PAUSE_MS), 50, 10_000);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override public MacroActionType getType() { return MacroActionType.TP; }

    @Override
    public String getDisplayName() {
        return "TP " + coordinate(x, relativeX) + " "
            + coordinate(y, relativeY) + " " + coordinate(z, relativeZ);
    }

    @Override public String getIcon() { return "TP"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    private static String coordinate(double value, boolean relative) {
        return (relative ? "~" : "") + String.format(Locale.ROOT, "%.2f", finite(value));
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0D; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

}
