package autismclient.util;

import net.minecraft.client.player.LocalPlayer;

public final class AutismKillAuraRotation {

    public static final float TURN_SPEED = 72.0f;

    public static final float WIND_DOWN_MAX_YAW_STEP = 37.0f;
    public static final float WIND_DOWN_MAX_PITCH_STEP = 37.0f;
    public static final float RESET_THRESHOLD = 2.0f;
    public static final int TICKS_UNTIL_RESET = 5;

    public static final int PRIORITY_BED_DEFENDER = 30;
    public static final int PRIORITY_SURROUND = 25;
    public static final int PRIORITY_ANCHOR_AURA = 20;
    public static final int PRIORITY_CRYSTAL_AURA = 18;
    public static final int PRIORITY_AUTO_TRAP = 15;
    public static final int PRIORITY_KILL_AURA = 10;
    public static final int PRIORITY_AUTO_FARM = 5;

    public static final String OWNER_BED_DEFENDER = "bed-defender";
    public static final String OWNER_SURROUND = "surround";
    public static final String OWNER_CRYSTAL_AURA = "crystal-aura";
    public static final String OWNER_ANCHOR_AURA = "anchor-aura";
    public static final String OWNER_AUTO_TRAP = "auto-trap";
    public static final String OWNER_KILL_AURA = "kill-aura";
    public static final String OWNER_AUTO_FARM = "auto-farm";

    private static final AutismHumanRotation.Stream STREAM = new AutismHumanRotation.Stream();
    private static AutismRotationUtil.Rotation currentRotation = null;
    private static AutismRotationUtil.Rotation targetRotation = null;

    private static String owner = null;

    private static String tickWinner = null;
    private static int tickWinnerPriority = Integer.MIN_VALUE;
    private static int arbitrationTick = Integer.MIN_VALUE;

    private static int streamStepTick = Integer.MIN_VALUE;

    private static final float PIN_HOLD_MAX_DEGREES = 0.05F;

    private static int resetTicks = 0;

    private static final int WIND_DOWN_MAX_TICKS = 27;
    private static int windDownTicks = 0;

    private static boolean windingDown = false;

    private AutismKillAuraRotation() {
    }

    public static void setTarget(AutismRotationUtil.Rotation rotation) {
        setTarget(OWNER_KILL_AURA, PRIORITY_KILL_AURA, rotation);
    }

    public static void setTarget(String ownerId, int priority, AutismRotationUtil.Rotation rotation) {
        if (ownerId == null || rotation == null) return;
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != arbitrationTick) {
            arbitrationTick = tick;
            tickWinner = null;
            tickWinnerPriority = Integer.MIN_VALUE;
        }
        if (tickWinner != null && priority <= tickWinnerPriority) return;
        tickWinner = ownerId;
        tickWinnerPriority = priority;
        owner = ownerId;
        targetRotation = rotation;
        resetTicks = TICKS_UNTIL_RESET;
        windingDown = false;
    }

    public static String currentOwner() {
        return owner;
    }

    public static void reset() {
        currentRotation = null;
        targetRotation = null;
        owner = null;
        tickWinner = null;
        tickWinnerPriority = Integer.MIN_VALUE;
        arbitrationTick = Integer.MIN_VALUE;
        streamStepTick = Integer.MIN_VALUE;
        resetTicks = 0;
        windDownTicks = 0;
        windingDown = false;
        AutismHumanRotation.clear(STREAM);
    }

    public static void beginWindDown(String ownerId) {
        if (owner != null && !owner.equals(ownerId)) return;
        targetRotation = null;
        resetTicks = 0;

        windingDown = currentRotation != null;
    }

    public static boolean isWindingDown() {
        return windingDown && currentRotation != null;
    }

    public static boolean hasCurrentRotation() {
        return currentRotation != null;
    }

    public static AutismRotationUtil.Rotation getCurrentRotation() {
        return currentRotation;
    }

    public static void update(String ownerId, LocalPlayer player) {
        update(ownerId, player, TURN_SPEED, TURN_SPEED);
    }

    public static void update(String ownerId, LocalPlayer player, boolean pinQuiet) {
        update(ownerId, player, TURN_SPEED, TURN_SPEED, AutismHumanRotation.MotionProfile.STANDARD, pinQuiet);
    }

    public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep) {
        update(ownerId, player, maxYawStep, maxPitchStep, AutismHumanRotation.MotionProfile.STANDARD);
    }

    public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep,
                              AutismHumanRotation.MotionProfile profile) {
        update(ownerId, player, maxYawStep, maxPitchStep, profile, false);
    }

    private static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep,
                               AutismHumanRotation.MotionProfile profile, boolean pinQuiet) {
        if (player == null) {
            reset();
            return;
        }
        if (owner != null && !owner.equals(ownerId)) return;
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == streamStepTick) return;
        streamStepTick = tick;

        AutismRotationUtil.Rotation playerRotation = AutismRotationUtil.playerRotation(player);
        if (targetRotation == null || resetTicks <= 0) {
            targetRotation = null;
            if (currentRotation == null) {
                windDownTicks = 0;
                windingDown = false;
                return;
            }

            if (windDownTicks <= 0) windDownTicks = WIND_DOWN_MAX_TICKS;

            AutismRotationUtil.Rotation next = AutismHumanRotation.step(
                STREAM, playerRotation, WIND_DOWN_MAX_YAW_STEP, WIND_DOWN_MAX_PITCH_STEP,
                AutismRotationUtil.sensitivityGcd(), false);
            windDownTicks--;

            if (AutismRotationUtil.rotationAngleTo(next, playerRotation) <= RESET_THRESHOLD
                || windDownTicks <= 0) {

                float fixedYaw = currentRotation.yaw()
                    + AutismRotationUtil.angleDifference(player.getYRot(), currentRotation.yaw());
                player.setYRot(fixedYaw);
                player.yBob = fixedYaw;
                player.yBobO = fixedYaw;
                currentRotation = null;
                owner = null;
                windDownTicks = 0;
                windingDown = false;
                AutismHumanRotation.clear(STREAM);
            } else {
                currentRotation = next;
            }
            return;
        }

        if (!AutismHumanRotation.isInitialized(STREAM)) {

            AutismRotationUtil.Rotation seedFrom = currentRotation;
            if (seedFrom == null) {
                AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
                seedFrom = wire.initialized()
                    ? new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
                    : playerRotation;
            }
            AutismHumanRotation.seed(STREAM, seedFrom);
        }

        if (pinQuiet && currentRotation != null
            && AutismRotationUtil.rotationAngleTo(currentRotation, targetRotation)
                <= PIN_HOLD_MAX_DEGREES) {

            resetTicks--;
            return;
        }
        currentRotation = AutismHumanRotation.step(
            STREAM, targetRotation, maxYawStep, maxPitchStep, AutismRotationUtil.sensitivityGcd(),
            false, profile);

        resetTicks--;
    }
}
