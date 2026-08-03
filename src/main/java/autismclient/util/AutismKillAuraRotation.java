package autismclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class AutismKillAuraRotation {
    public static final float LINEAR_TURN_SPEED = 180.0f;
    public static final float RESET_THRESHOLD = 2.0f;
    public static final int TICKS_UNTIL_RESET = 5;

    private static AutismRotationUtil.Rotation currentRotation = null;
    private static AutismRotationUtil.Rotation targetRotation = null;

    private static int resetTicks = 0;

    private AutismKillAuraRotation() {
    }

    public static void setTarget(AutismRotationUtil.Rotation rotation) {
        targetRotation = rotation;
        resetTicks = TICKS_UNTIL_RESET;
    }

    public static void clearTarget() {

    }

    public static void reset() {
        currentRotation = null;
        targetRotation = null;
        resetTicks = 0;
    }

    public static boolean hasCurrentRotation() {
        return currentRotation != null;
    }

    public static AutismRotationUtil.Rotation getCurrentRotation() {
        return currentRotation;
    }

    public static void update(LocalPlayer player) {
        if (player == null) {
            reset();
            return;
        }

        AutismRotationUtil.Rotation playerRotation = AutismRotationUtil.playerRotation(player);
        if (targetRotation == null || resetTicks <= 0) {
            targetRotation = null;
            if (currentRotation == null) {
                return;
            }

            AutismRotationUtil.Rotation next = step(currentRotation, playerRotation);
            next = AutismRotationUtil.normalizeToSensitivity(next, currentRotation);

            if (AutismRotationUtil.rotationAngleTo(next, playerRotation) <= RESET_THRESHOLD) {

                float fixedYaw = currentRotation.yaw()
                    + AutismRotationUtil.angleDifference(player.getYRot(), currentRotation.yaw());
                player.setYRot(fixedYaw);
                player.yBob = fixedYaw;
                player.yBobO = fixedYaw;
                currentRotation = null;
            } else {
                currentRotation = next;
            }
            return;
        }

        AutismRotationUtil.Rotation from = currentRotation != null ? currentRotation : playerRotation;
        AutismRotationUtil.Rotation next = step(from, targetRotation);
        next = AutismRotationUtil.normalizeToSensitivity(next, from);
        currentRotation = next;

        resetTicks--;
    }

    private static AutismRotationUtil.Rotation step(AutismRotationUtil.Rotation from, AutismRotationUtil.Rotation to) {
        return AutismRotationUtil.towardsLinear(from, to, LINEAR_TURN_SPEED, LINEAR_TURN_SPEED);
    }
}
