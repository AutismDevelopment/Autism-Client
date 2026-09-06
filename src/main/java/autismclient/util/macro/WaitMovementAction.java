package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitMovementAction implements MacroAction {
    public enum Mode { POSITION, POSITION_DELTA, WORLD_CHANGE, TELEPORT }

    public Mode mode = Mode.POSITION;

    public double x = 0.0;
    public double y = 0.0;
    public double z = 0.0;
    public double leeway = 1.0;
    public boolean checkRotation = false;
    public double yaw = 0.0;
    public double pitch = 0.0;
    public double rotLeeway = 5.0;

    public double distance = 5.0;

    public double minDistance = 5.0;

    public boolean horizontalOnly = false;

    public String targetDimension = "";

    public boolean listenDuringPreviousAction = false;

    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {

    }

    public MacroAction resolveSubAction() {
        return switch (mode) {
            case POSITION -> toPosition();
            case POSITION_DELTA -> toPositionDelta();
            case WORLD_CHANGE -> toWorldChange();
            case TELEPORT -> toTeleport();
        };
    }

    public WaitPosAction toPosition() {
        WaitPosAction a = new WaitPosAction();
        a.x = x;
        a.y = y;
        a.z = z;
        a.leeway = leeway;
        a.checkRotation = checkRotation;
        a.yaw = (float) yaw;
        a.pitch = (float) pitch;
        a.rotLeeway = (float) rotLeeway;
        a.listenDuringPreviousAction = listenDuringPreviousAction;
        return a;
    }

    public WaitForPositionDeltaAction toPositionDelta() {
        WaitForPositionDeltaAction a = new WaitForPositionDeltaAction();
        a.distance = distance;
        a.horizontalOnly = horizontalOnly;
        a.listenDuringPreviousAction = listenDuringPreviousAction;
        return a;
    }

    public WaitForWorldChangeAction toWorldChange() {
        WaitForWorldChangeAction a = new WaitForWorldChangeAction();
        a.targetDimension = targetDimension;
        a.listenDuringPreviousAction = listenDuringPreviousAction;
        return a;
    }

    public WaitForTeleportAction toTeleport() {
        WaitForTeleportAction a = new WaitForTeleportAction();
        a.minDistance = minDistance;
        a.horizontalOnly = horizontalOnly;
        a.listenDuringPreviousAction = listenDuringPreviousAction;
        return a;
    }

    @Override
    public MacroActionType getType() { return MacroActionType.WAIT_MOVEMENT; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "WAIT_MOVEMENT");
        tag.putString("mode", mode.name());
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putDouble("leeway", leeway);
        tag.putBoolean("checkRotation", checkRotation);
        tag.putDouble("yaw", yaw);
        tag.putDouble("pitch", pitch);
        tag.putDouble("rotLeeway", rotLeeway);
        tag.putDouble("distance", distance);
        tag.putDouble("minDistance", minDistance);
        tag.putBoolean("horizontalOnly", horizontalOnly);
        tag.putString("targetDimension", targetDimension == null ? "" : targetDimension);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        mode = MacroStringList.enumValue(Mode.class, tag.getStringOr("mode", "POSITION"), Mode.POSITION);
        x = tag.getDoubleOr("x", 0.0);
        y = tag.getDoubleOr("y", 0.0);
        z = tag.getDoubleOr("z", 0.0);
        leeway = tag.getDoubleOr("leeway", 1.0);
        checkRotation = tag.getBooleanOr("checkRotation", false);
        yaw = tag.getDoubleOr("yaw", 0.0);
        pitch = tag.getDoubleOr("pitch", 0.0);
        rotLeeway = tag.getDoubleOr("rotLeeway", 5.0);
        distance = tag.getDoubleOr("distance", 5.0);
        minDistance = Math.max(0.0, tag.getDoubleOr("minDistance", 5.0));
        horizontalOnly = tag.getBooleanOr("horizontalOnly", false);
        targetDimension = tag.getStringOr("targetDimension", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }

    @Override
    public String getDisplayName() {
        return switch (mode) {
            case POSITION -> "Wait Pos " + String.format(java.util.Locale.ROOT, "%.0f,%.0f,%.0f", x, y, z);
            case POSITION_DELTA -> "Wait Moved " + String.format(java.util.Locale.ROOT, "%.1f", distance);
            case WORLD_CHANGE -> targetDimension == null || targetDimension.isBlank()
                    ? "Wait World Change" : "Wait World: " + targetDimension;
            case TELEPORT -> minDistance <= 0.0
                    ? "Wait Teleport" : "Wait Teleport " + String.format(java.util.Locale.ROOT, "%.1f", minDistance);
        };
    }

    @Override
    public String getIcon() { return "MV"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
