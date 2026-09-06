package autismclient.security;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public final class AutismNumericSanity {

    public static final double SANE_LIMIT = 1.0e9;

    public static final double MAX_MOTION_PER_AXIS = 1.0e4;

    private AutismNumericSanity() {
    }

    public static boolean outOfRange(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > SANE_LIMIT;
    }

    public static boolean outOfRange(Vec3 vec) {
        return vec == null || outOfRange(vec.x) || outOfRange(vec.y) || outOfRange(vec.z);
    }

    public static boolean motionOutOfRange(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > MAX_MOTION_PER_AXIS;
    }

    public static boolean motionOutOfRange(Vec3 vec) {
        return vec == null || motionOutOfRange(vec.x) || motionOutOfRange(vec.y) || motionOutOfRange(vec.z);
    }

    public static boolean positionMoveOutOfRange(PositionMoveRotation change) {
        return change == null || outOfRange(change.position()) || motionOutOfRange(change.deltaMovement())
            || outOfRange(change.yRot()) || outOfRange(change.xRot());
    }
}
