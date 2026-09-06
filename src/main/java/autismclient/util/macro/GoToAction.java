package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCompatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class GoToAction implements MacroAction {
    public double x = 0;
    public double y = 0;
    public double z = 0;
    private boolean enabled = true;
    public boolean waitForArrival = true;
    public double arrivalRadius = 2.0;
    public int timeoutMs = 60000;

    public GoToAction() {}

    public GoToAction(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void execute(Minecraft mc) {
        if (mc.player == null) return;

        String command = String.format("#goto %.1f %.1f %.1f", x, y, z);
        if (!AutismCompatManager.sendBaritoneCommand(mc, command)) {
            AutismClientMessaging.sendPrefixed("§cBaritone is not available, cannot send goto command.");
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putBoolean("waitForArrival", waitForArrival);
        tag.putDouble("arrivalRadius", arrivalRadius);
        tag.putInt("timeoutMs", timeoutMs);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("x")) x = tag.getDoubleOr("x", 0);
        if (tag.contains("y")) y = tag.getDoubleOr("y", 0);
        if (tag.contains("z")) z = tag.getDoubleOr("z", 0);
        if (tag.contains("waitForArrival")) waitForArrival = tag.getBooleanOr("waitForArrival", true);
        if (tag.contains("arrivalRadius")) arrivalRadius = tag.getDoubleOr("arrivalRadius", 2.0);
        if (tag.contains("timeoutMs")) timeoutMs = tag.getIntOr("timeoutMs", 60000);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.GO_TO;
    }

    @Override
    public String getDisplayName() {
        return String.format("Go To (%.0f, %.0f, %.0f)%s", x, y, z, waitForArrival ? " [W]" : "");
    }

    @Override
    public String getIcon() {
        return "GO";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
