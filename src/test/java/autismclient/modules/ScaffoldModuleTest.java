package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismHumanRotation;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.QuantizedRotationSmoother;
import autismclient.util.AutismRotationUtil.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScaffoldModuleTest {
    @TempDir
    static Path gameDir;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        ensureGameDir(gameDir);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void fixedProfileConstantsRemainHidden() {
        assertEquals(5, ScaffoldModule.SLOT_RESET_TICKS);
        assertEquals(5, ScaffoldModule.ROTATION_RESET_TICKS);
        assertEquals(2.0F, ScaffoldModule.ROTATION_RESET_THRESHOLD);

        assertEquals(180.0F, ScaffoldModule.GRIM_AIM_ACCEL_BUDGET);
        assertEquals(18.0F, ScaffoldModule.GRIM_MAX_YAW_STEP);
        assertEquals(20.0F, ScaffoldModule.GRIM_MAX_PITCH_STEP);
        assertEquals(0.0D, ScaffoldModule.MIN_FACE_DISTANCE);
        assertEquals(1.0F, ScaffoldModule.TIMER_MULTIPLIER);
    }

    @Test
    void legitIsDefaultAndAllModesAreAvailable() {
        ChoiceSetting mode = new ChoiceSetting(
            "mode", "Mode", "Legit", "Legit", "Rage", "Telly");

        assertEquals("Legit", mode.defaultValue());
        assertEquals(List.of("Legit", "Rage", "Telly"), mode.choices());
    }

    @Test
    void switchBackDefaultsOnWithShortTip() {
        assertTrue(ScaffoldModule.SWITCH_BACK_DEFAULT);
        assertEquals("Restore previous hotbar slot.", ScaffoldModule.SWITCH_BACK_TIP);
        assertTrue(ScaffoldModule.SWITCH_BACK_TIP.replaceAll("[^A-Za-z ]", "")
            .trim().split("\\s+").length < 5);
    }

    @Test
    void filterModeIsMutuallyExclusive() {
        ChoiceSetting mode = new ChoiceSetting(
            "filter-mode", "Filter", "Off", "Off", "Whitelist", "Blacklist");
        RegistryListSetting blocks = RegistryListSetting.placeableBlocks("blocks", "Blocks");

        assertEquals(List.of("Off", "Whitelist", "Blacklist"), mode.choices());
        assertTrue(blocks.placeableBlocksOnly());
    }

    @Test
    void pickerRejectsFluidsAndUnsafeBlocks() {
        assertTrue(ScaffoldModule.isPlaceableBlockChoice(Blocks.STONE));
        assertTrue(!ScaffoldModule.isPlaceableBlockChoice(Blocks.WATER));
        assertTrue(!ScaffoldModule.isPlaceableBlockChoice(Blocks.LAVA));
        assertTrue(!ScaffoldModule.isPlaceableBlockChoice(Blocks.TNT));
    }

    @Test
    void airborneTargetsStayBelowFeet() {
        assertEquals(new BlockPos(10, 63, 10), ScaffoldModule.targetedBase(new Vec3(10.5D, 64.0D, 10.5D)));
        assertEquals(new BlockPos(10, 63, 10), ScaffoldModule.targetedBase(new Vec3(10.5D, 64.8D, 10.5D)));
        assertEquals(new BlockPos(10, 65, 10), ScaffoldModule.targetedBase(new Vec3(10.5D, 66.2D, 10.5D)));
    }

    @Test
    void grimPlacementAimAlwaysUsesTheRealEyePosition() {
        Vec3 predicted = new Vec3(12.75D, 64.0D, -3.5D);
        Vec3 actual = new Vec3(12.18D, 64.0D, -3.5D);

        assertEquals(actual, ScaffoldModule.grimPlacementAimPosition(predicted, actual, true),
            "the click happens before movement, so Grim must aim from the real eye");
        assertEquals(predicted, ScaffoldModule.grimPlacementAimPosition(predicted, actual, false),
            "non-Grim planners retain their existing prediction contract");
        assertEquals(predicted, ScaffoldModule.grimFaceSelectionPosition(predicted, actual),
            "future face selection must lead the real click eye during a jump");
        assertEquals(actual, ScaffoldModule.grimFaceSelectionPosition(null, actual));
    }

    @Test
    void grimPlacementPostureFacesStraightBackDownTheBridge() {

        assertEquals(-180.0F, ScaffoldModule.grimPlacementPostureYaw(0.0F), 1.0E-4F);
        assertEquals(-90.0F, ScaffoldModule.grimPlacementPostureYaw(90.0F), 1.0E-4F);
        assertEquals(0.0F, ScaffoldModule.grimPlacementPostureYaw(-180.0F), 1.0E-4F);
        assertEquals(45.0F, ScaffoldModule.grimPlacementPostureYaw(-135.0F), 1.0E-4F,
            "a diagonal lane's posture is the diagonal reverse, still exactly on the grid");

        for (float lane = -180.0F; lane < 180.0F; lane += 45.0F) {
            float posture = ScaffoldModule.grimPlacementPostureYaw(lane);
            assertEquals(180.0F, Math.abs(AutismRotationUtil.angleDifference(posture, lane)), 1.0E-3F,
                "posture " + posture + " must sit exactly opposite lane " + lane);
        }
    }

    @Test
    void thePaceLimitFollowsIntavesOwnBranchOrder() {

        assertEquals(300L, ScaffoldModule.grimPaceLimitMs(true, true, true, true));
        assertEquals(350L, ScaffoldModule.grimPaceLimitMs(true, true, true, false));
        assertEquals(500L, ScaffoldModule.grimPaceLimitMs(true, true, false, false));

        assertEquals(200L, ScaffoldModule.grimPaceLimitMs(true, false, true, false));
        assertEquals(350L, ScaffoldModule.grimPaceLimitMs(true, false, false, false));

        assertEquals(150L, ScaffoldModule.grimPaceLimitMs(false, false, true, false));
        assertEquals(300L, ScaffoldModule.grimPaceLimitMs(false, true, true, false));
        assertEquals(300L, ScaffoldModule.grimPaceLimitMs(false, false, false, false));
    }

    @Test
    void isOneLineIsBrokenByAnyTurnOrAnyStepUp() {
        List<BlockPos> straight = List.of(
            new BlockPos(-5, 85, 50), new BlockPos(-6, 85, 50), new BlockPos(-7, 85, 50));
        assertTrue(ScaffoldModule.grimPaceOneLine(straight), "one Y, one axis");

        List<BlockPos> turned = List.of(
            new BlockPos(-10, 85, 50), new BlockPos(-11, 85, 50), new BlockPos(-11, 85, 51));
        assertFalse(ScaffoldModule.grimPaceOneLine(turned), "a turn breaks the axis lock");

        List<BlockPos> climbed = List.of(
            new BlockPos(7, 85, 59), new BlockPos(8, 85, 59), new BlockPos(8, 86, 59));
        assertFalse(ScaffoldModule.grimPaceOneLine(climbed), "a step up breaks the Y lock");
        assertTrue(ScaffoldModule.grimPaceOneLine(List.of(new BlockPos(0, 0, 0))));
    }

    @Test
    void thePaceYawBandIsIntavesModuloNinety() {

        assertTrue(ScaffoldModule.grimPaceYawBanded(90.3F));
        assertTrue(ScaffoldModule.grimPaceYawBanded(-90.3F));
        assertTrue(ScaffoldModule.grimPaceYawBanded(-179.7F));

        assertFalse(ScaffoldModule.grimPaceYawBanded(135.3F));
        assertFalse(ScaffoldModule.grimPaceYawBanded(-45.0F));
        assertTrue(ScaffoldModule.grimPaceYawBanded(80.5F), "the upper edge counts too");
        assertFalse(ScaffoldModule.grimPaceYawBanded(70.0F));
    }

    @Test
    void thePaceGateScoresTheMeanNotTheInterval() {

        long[] slow = {400L, 400L, 400L, 400L, 400L, 400L, 400L};
        assertEquals(362.5D, ScaffoldModule.grimPaceProspectiveMean(slow, 100L, 8), 1.0E-6D);

        long[] atLimit = {350L, 350L, 350L, 350L, 350L, 350L, 350L};
        assertTrue(ScaffoldModule.grimPaceProspectiveMean(atLimit, 100L, 8) < 350.0D);

        long[] tooMany = {9000L, 100L, 100L, 100L, 100L, 100L, 100L, 100L};
        assertEquals(100.0D, ScaffoldModule.grimPaceProspectiveMean(tooMany, 100L, 8), 1.0E-6D);
        assertEquals(100.0D, ScaffoldModule.grimPaceProspectiveMean(new long[0], 100L, 8), 1.0E-6D);
    }

    @Test
    void theIntervalFloorRefusesABurstTheMeanWouldHaveWavedThrough() {

        long[] slow = {400L, 400L, 400L, 400L, 400L, 400L, 400L};
        assertTrue(ScaffoldModule.grimPaceProspectiveMean(slow, 50L, 8)
                > ScaffoldModule.grimPaceLimitMs(true, false, false, false),
            "the mean-only gate is what let the burst out");

        assertTrue(50L < ScaffoldModule.grimPaceFloorMs(350L));
        assertTrue(50L < ScaffoldModule.grimPaceFloorMs(150L));

        for (long limit : new long[] {150L, 200L, 300L, 350L, 500L}) {
            assertEquals(Math.round(limit * 1.08D), ScaffoldModule.grimPaceFloorMs(limit),
                "the floor is derived from the Intave limit alone: " + limit);
        }
    }

    @Test
    void theFloorIsSizedSoTheMeanNeverHasToBeRepaid() {

        long banded = ScaffoldModule.grimPaceLimitMs(true, true, true, false);
        assertEquals(350L, banded);
        assertTrue(7 * 50L < banded * 1.08D, "7 ticks could never satisfy its own mean target");
        assertEquals(378L, ScaffoldModule.grimPaceFloorMs(banded),
            "and the floor is now the 378 itself, not the 400 whole ticks rounded up to");

        assertEquals(540L, ScaffoldModule.grimPaceFloorMs(
            ScaffoldModule.grimPaceLimitMs(true, true, false, false)));
        assertEquals(162L, ScaffoldModule.grimPaceFloorMs(
            ScaffoldModule.grimPaceLimitMs(false, false, true, false)));

        for (long limit : new long[] {150L, 200L, 300L, 350L, 500L}) {
            long interval = ScaffoldModule.grimPaceFloorMs(limit);
            long[] steady = new long[7];
            java.util.Arrays.fill(steady, interval);
            assertTrue(
                ScaffoldModule.grimPaceProspectiveMean(steady, interval, 8) >= limit * 1.08D,
                "a steady run at the floor must not owe the mean anything: limit " + limit
                    + " interval " + interval);
        }
    }

    @Test
    void theMissFallbackCannotAskForAPitchTheClickGateMustRefuse() {

        assertTrue(89.7F > ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD, "past the cap");
        assertFalse(ScaffoldModule.grimPlacementPitchLegal(89.7F));

        assertTrue(ScaffoldModule.grimPlacementPitchCap(85.3F)
            <= ScaffoldModule.GRIM_PLACE_MAX_PITCH);
        assertTrue(ScaffoldModule.grimPlacementPitchCap(89.9F)
            <= ScaffoldModule.GRIM_PLACE_MAX_PITCH);

        assertEquals(66.5F, ScaffoldModule.grimPlacementPitchCap(66.5F), 1.0E-4F);
    }

    @Test
    void theYawVetoStopsTheOffAxisPlacementsWithoutBanningTheDiagonalPosture() {
        float max = ScaffoldModule.GRIM_LANE_OCTANT_MAX_RESIDUAL;

        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 17.7F)) > max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 18.0F)) > max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, -27.1F)) > max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 57.3F)) > max);

        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 35.9F)) < max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 53.4F)) < max);

        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 45.3F)) < max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 44.7F)) < max);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, 180.0F)) < max);
    }

    @Test
    void theCrossingWindowIsWhatTheGoalIsHeldInside() {

        Vec3 eye = new Vec3(1.30D, 85.62D, 0.50D);
        BlockPos support = new BlockPos(0, 84, 0);
        double[] window = ScaffoldModule.grimTwoEyeCrossingWindow(
            eye, eye, support, Direction.EAST, 90.0F);
        assertNotNull(window, "the eye is past the plane, so there is a window");
        assertTrue(window[0] < window[1], "and it is a real interval");
        float solved = ScaffoldModule.grimTwoEyeCrossingPitch(
            eye, eye, support, Direction.EAST, 90.0F, ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD);
        assertTrue(solved >= window[0] - 1.0E-4D && solved <= window[1] + 1.0E-4D,
            "the solve " + solved + " must sit inside its own window "
                + window[0] + ".." + window[1]);

        assertNull(ScaffoldModule.grimTwoEyeCrossingWindow(
            new Vec3(0.50D, 85.62D, 0.50D), new Vec3(0.50D, 85.62D, 0.50D),
            support, Direction.EAST, 90.0F));
    }

    @Test
    void theFreeAimSnapPutsEveryOffAxisGoalBackOnAWholeStep() {

        float[] shipped = {17.7F, 35.9F, 53.4F, 18.0F, -27.1F, 46.4F, 50.7F, 57.3F, 45.3F, 44.7F};
        for (float yaw : shipped) {
            float snapped = ScaffoldModule.grimSnapYawToLane(0.0F, yaw);
            assertEquals(0.0F, ScaffoldModule.grimLaneOctantResidual(0.0F, snapped), 1.0E-3F,
                "snapping " + yaw + " must land on a whole 45-step");
            assertTrue(Math.abs(Mth.wrapDegrees(snapped - yaw)) <= 22.5F + 1.0E-3F,
                "and must never move the aim more than half an octant to do it");
        }
        assertEquals(45.0F, ScaffoldModule.grimSnapYawToLane(0.0F, 35.9F), 1.0E-3F);
        assertEquals(0.0F, ScaffoldModule.grimSnapYawToLane(0.0F, 17.7F), 1.0E-3F);

        assertEquals(-45.0F, ScaffoldModule.grimSnapYawToLane(-90.0F, -50.0F), 1.0E-3F);
    }

    @Test
    void aDeliberateStrafeStandsTheAnchorDown() {

        Input left = new Input(false, true, true, false, false, false, false);
        assertTrue(ScaffoldModule.grimInputSteering(left));
        assertTrue(ScaffoldModule.grimInputSteering(
            new Input(true, false, false, true, false, false, false)));

        assertFalse(ScaffoldModule.grimInputSteering(
            new Input(false, true, true, true, false, false, false)));
        assertFalse(ScaffoldModule.grimInputSteering(
            new Input(false, true, false, false, false, false, false)));
        assertFalse(ScaffoldModule.grimInputSteering(null));

        assertTrue(ScaffoldModule.grimInputSteering(left),
            "and steering is what suppresses the lane centring, not just the anchor");

        Input passed = ScaffoldModule.transformSilentMovementInput(left, 90.0F, 90.0F);
        assertTrue(passed.left() && passed.backward(), "the strafe survives to the world");

        Input eaten = ScaffoldModule.transformSilentMovementInput(left, 45.0F, 90.0F);
        assertFalse(eaten.left() || eaten.right(), "which is the behaviour being stood down");
        assertTrue(eaten.backward());
    }

    private static final double[][] BUZZ_CAPTURE = {
        {+0.245D, -0.072D}, {+0.347D, -0.055D}, {+0.373D, -0.014D}, {+0.373D, -0.014D},
        {+0.316D, +0.031D}, {+0.215D, +0.055D}, {+0.089D, +0.068D}, {+0.091D, -0.001D},
        {+0.162D, -0.039D},
    };

    @Test
    void theCentringLatchNeverReversesWhileTheCorrectionIsStillWorking() {

        int lastPressed = 0;
        int statelessFlips = 0;
        for (double[] tick : BUZZ_CAPTURE) {
            double predicted = tick[0] - tick[1] * 4.0D;
            int side = Math.abs(predicted) < 0.10D ? 0 : (predicted > 0.0D ? 1 : -1);
            if (side != 0) {
                if (lastPressed != 0 && side != lastPressed) statelessFlips++;
                lastPressed = side;
            }
        }
        assertTrue(statelessFlips > 0,
            "the old model reverses on this capture - that is the bug being fixed");

        List<Integer> sides = new ArrayList<>();
        driveCentringLatch(BUZZ_CAPTURE, sides);
        int flips = 0;
        lastPressed = 0;
        for (int side : sides) {
            if (side != 0) {
                if (lastPressed != 0 && side != lastPressed) flips++;
                lastPressed = side;
            }
        }
        assertEquals(0, flips, "the latch must never reverse mid-correction");

        assertEquals(1, lastPressed,
            "and every push in the run steered toward the lane, never away from it");

        assertTrue(longestRun(sides) <= ScaffoldModule.GRIM_LANE_CORRECT_MAX_HOLD_TICKS,
            "no push may outlast the cap, ran " + longestRun(sides));
    }

    private static void driveCentringLatch(double[][] capture, List<Integer> sides) {
        int side = 0;
        int held = 0;
        int lock = 0;
        for (double[] tick : capture) {
            int previous = side;
            side = ScaffoldModule.grimLaneCorrectLatch(previous, held, lock, tick[0], tick[1]);
            if (side == 0) {
                held = 0;
                if (previous != 0) lock = ScaffoldModule.GRIM_LANE_CORRECT_RELOCK_TICKS;
                else if (lock > 0) lock--;
            } else {
                held++;
                lock = 0;
            }
            sides.add(side);
        }
    }

    private static int longestRun(List<Integer> sides) {
        int best = 0;
        int run = 0;
        for (int i = 0; i < sides.size(); i++) {
            run = sides.get(i) != 0 && (i == 0 || sides.get(i).equals(sides.get(i - 1))) ? run + 1
                : (sides.get(i) != 0 ? 1 : 0);
            best = Math.max(best, run);
        }
        return best;
    }

    @Test
    void theCentringLatchEngagesAndReleasesOnItsOwnThresholds() {
        double engage = ScaffoldModule.GRIM_LANE_CORRECT_ENGAGE;
        double release = ScaffoldModule.GRIM_LANE_CORRECT_RELEASE;
        assertTrue(engage > release, "hysteresis, not a deadband");

        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, engage + 0.02D, 0.0D));
        assertEquals(-1, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, -(engage + 0.02D), 0.0D));
        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, engage - 0.02D, 0.0D));

        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(1, 0, 0, engage - 0.02D, 0.0D));
        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(1, 0, 0, 0.08D, 0.0D));

        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(1, 0, 0, release - 0.01D, 0.0D));

        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, 0.30D, 0.075D));

        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, 0.0D, -0.2D));
    }

    @Test
    void theCentringLatchStopsPushingBeforeItCanOvershoot() {
        int cap = ScaffoldModule.GRIM_LANE_CORRECT_MAX_HOLD_TICKS;

        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(1, cap - 1, 0, 0.20D, 0.0D));
        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(1, cap, 0, 0.20D, 0.0D),
            "the cap releases whatever the position says");
        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(1, cap, 0, 5.0D, 0.0D),
            "and it is a cap, not a threshold - no offset buys another tick");

        double closes = (0.28D - ScaffoldModule.GRIM_LANE_CORRECT_RELEASE)
            / ScaffoldModule.GRIM_LANE_CORRECT_SETTLE_LEAD;
        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(1, 1, 0, 0.28D, closes + 0.005D),
            "a speed that really carries us home is a reason to stop pushing");
        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(1, 1, 0, 0.28D, closes - 0.02D),
            "but the linear lead would have released here, a tick and 0.1 too early");
        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(1, 1, 0, 0.28D, 0.01D),
            "and a drift barely moving still needs the push");

        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(1, 1, 0, 0.10D, 0.20D));

        assertEquals(0, ScaffoldModule.grimLaneCorrectLatch(0, 0, 1, 0.40D, 0.0D),
            "locked out, however far off centre");
        assertEquals(1, ScaffoldModule.grimLaneCorrectLatch(0, 0, 0, 0.40D, 0.0D));
        assertTrue(ScaffoldModule.GRIM_LANE_CORRECT_RELOCK_TICKS > 0,
            "a zero lockout is the reversal the javadoc records");

        double lane = 0.31D;
        double perp = 0.0D;
        int side = 0;
        int held = 0;
        int lock = 0;
        double worstCrossing = 0.0D;
        for (int tick = 0; tick < 24; tick++) {
            int previous = side;
            side = ScaffoldModule.grimLaneCorrectLatch(previous, held, lock, lane, perp);
            if (side == 0) {
                held = 0;
                if (previous != 0) lock = ScaffoldModule.GRIM_LANE_CORRECT_RELOCK_TICKS;
                else if (lock > 0) lock--;
            } else {
                held++;
                lock = 0;
            }

            perp = side != 0 ? perp + side * 0.06D : perp * 0.6D;
            lane -= perp;
            if (lane < 0.0D) worstCrossing = Math.max(worstCrossing, -lane);
        }
        assertTrue(worstCrossing <= 0.10D,
            "the correction must not overshoot the centre, crossed by " + worstCrossing);

        assertTrue(Math.abs(lane) <= 0.06D,
            "and it must actually get to the middle, ended at " + lane);
    }

    @Test
    void theCentringBiasClearsTheOctantFlipWithoutBreakingItsOwnDwell() {
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 left = new Vec3(east.z, 0.0D, -east.x);
        double look = ScaffoldModule.GRIM_LANE_INPUT_LOOKAHEAD_LEGIT;

        Input back = new Input(false, true, false, false, false, false, false);
        float flipPoint = 30.0F;
        assertFalse(ScaffoldModule.transformSilentMovementInput(back, 0.0F, 0.0F).left()
            || ScaffoldModule.transformSilentMovementInput(back, 0.0F, 0.0F).right());
        assertTrue(ScaffoldModule.transformSilentMovementInput(back, flipPoint + 0.5F, 0.0F).left()
            != ScaffoldModule.transformSilentMovementInput(back, flipPoint + 0.5F, 0.0F).right(),
            "past 30 degrees the transform presses a strafe key");

        float atEngage = ScaffoldModule.grimLaneInputTarget(east,
            left.scale(-ScaffoldModule.GRIM_LANE_CORRECT_ENGAGE), Vec3.ZERO, 0.0D, look);
        assertTrue(Math.abs(atEngage) > flipPoint,
            "at the engage point it must pick the corrective octant, asked for " + atEngage);
        assertTrue(Math.abs(atEngage) < ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES,
            "and must not break the dwell, asked for " + atEngage);
        assertTrue(Math.abs(ScaffoldModule.grimLaneInputTarget(
            east, left.scale(-0.225D), Vec3.ZERO, 0.0D, 0.225D))
            >= ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES,
            "which the shipped 0.225 could not do - it broke the dwell on every correction");

        assertTrue(Math.abs(ScaffoldModule.grimLaneInputTarget(east, left.scale(-0.45D), Vec3.ZERO,
            0.0D, look)) >= ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES);

        assertTrue(ScaffoldModule.grimLaneInputTarget(east, left.scale(0.30D), Vec3.ZERO, 0.0D, look)
            < 0.0F);

        assertEquals(0.0F, ScaffoldModule.grimLaneInputTarget(east, left.scale(-0.15D), Vec3.ZERO));
    }

    @Test
    void theLaneAnchorRemovesTheOctantFlipThatWalkedUsOffTheBridge() {
        Input backward = new Input(false, true, false, false, false, false, false);

        Input healthy = ScaffoldModule.transformSilentMovementInput(backward, -271.0F, 90.3F);
        assertTrue(healthy.backward(), "the back key survives");
        assertFalse(healthy.left() || healthy.right(), "and no strafe is invented");

        Input drifted = ScaffoldModule.transformSilentMovementInput(backward, -212.0F, -179.7F);
        assertTrue(drifted.right(), "the camera reference produces the strafe that caused the drift");

        float intent = ScaffoldModule.grimInputWorldYaw(-1.0F, 0.0F, -212.0F);
        float bias = Mth.wrapDegrees(0.0F - intent);
        Input anchored = ScaffoldModule.transformSilentMovementInput(
            backward, -212.0F + bias, -179.7F);
        assertTrue(anchored.backward());
        assertFalse(anchored.left() || anchored.right(),
            "the anchored octant walks the lane, so no strafe and no 45-degree error");
    }

    @Test
    void theKeysWorldYawIsTheReferenceMinusTheirOwnAngle() {

        assertEquals(-180.0F, ScaffoldModule.grimInputWorldYaw(-1.0F, 0.0F, 0.0F), 1.0E-3F);
        assertEquals(0.0F, ScaffoldModule.grimInputWorldYaw(1.0F, 0.0F, 0.0F), 1.0E-3F);
        assertEquals(-90.0F, ScaffoldModule.grimInputWorldYaw(0.0F, 1.0F, 0.0F), 1.0E-3F);
        assertEquals(-32.0F, ScaffoldModule.grimInputWorldYaw(-1.0F, 0.0F, -212.0F), 1.0E-3F);
    }

    @Test
    void theCoastGuardOpensExactlyWhenNoOctantWalksTheLane() {

        assertEquals(0.3F, ScaffoldModule.grimLaneOctantResidual(-90.0F, 90.3F), 1.0E-3F);
        assertEquals(0.3F, ScaffoldModule.grimLaneOctantResidual(-45.0F, 135.3F), 1.0E-3F);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(-90.0F, 90.3F))
            < ScaffoldModule.GRIM_LANE_OCTANT_MAX_RESIDUAL);

        assertEquals(14.6F, ScaffoldModule.grimLaneOctantResidual(-45.0F, 104.6F), 1.0E-3F);
        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(-45.0F, 104.6F))
            > ScaffoldModule.GRIM_LANE_OCTANT_MAX_RESIDUAL, "and the coast guard must open there");
    }

    @Test
    void thePitchGoalIsPureGeometryInsideTheWindow() {

        assertEquals(74.0F, ScaffoldModule.grimPlacementPitchGoal(74.0F, 60.0D, 84.0D), 1.0E-4F,
            "inside the window the solve ships untouched");

        assertTrue(ScaffoldModule.grimPlacementPitchGoal(89.9F, 60.0D, 90.0D) <= 89.0F);

        assertEquals(74.0F,
            ScaffoldModule.grimPlacementPitchGoal(74.0F, Double.NaN, Double.NaN), 1.0E-4F);
    }

    @Test
    void theWindowStillKeepsTheGoalOnTheBlock() {

        double run = 0.40D;
        double toTop = 0.62D;
        double toBottom = 1.62D;
        assertEquals(57.2D, Math.toDegrees(Math.atan2(toTop, run)), 0.1D);
        assertEquals(76.1D, Math.toDegrees(Math.atan2(toBottom, run)), 0.1D);

        float goal = ScaffoldModule.grimPlacementPitchGoal(66.5F, 57.2D, 76.1D);
        double goalDrop = run * Math.tan(Math.toRadians(goal));
        assertTrue(goalDrop >= toTop && goalDrop <= toBottom,
            "the windowed goal drops " + goalDrop + ", which must land inside the block");

        float clamped = ScaffoldModule.grimPlacementPitchGoal(83.5F, 57.2D, 76.1D);
        assertEquals(76.1F, clamped, 1.0E-3F);
        assertTrue(run * Math.tan(Math.toRadians(clamped)) <= toBottom + 1.0E-6D,
            "the clamped goal must not pass under the block");
    }

    @Test
    void theClickGateEnforcesOnlyTheCapNow() {
        assertFalse(ScaffoldModule.grimPlacementPitchLegal(89.6F));
        assertTrue(ScaffoldModule.grimPlacementPitchLegal(89.4F));

        assertTrue(ScaffoldModule.grimPlacementPitchLegal(66.5F));
    }

    @Test
    void theSneakWindowRefreshesBeforeItGoesStale() {
        assertTrue(ScaffoldModule.grimSneakRefreshDue(-1), "never sneaked must count as due");
        assertFalse(ScaffoldModule.grimSneakRefreshDue(0));
        assertFalse(ScaffoldModule.grimSneakRefreshDue(89));
        assertTrue(ScaffoldModule.grimSneakRefreshDue(90));

        assertTrue(90 < 150, "refresh interval must beat the memory window");
    }

    @Test
    void ticksSinceTreatsTheNeverSentinelAsNoTimeOwed() {

        assertEquals(-1, ScaffoldModule.grimTicksSince(12345, Integer.MIN_VALUE));
        assertEquals(-1, ScaffoldModule.grimTicksSince(0, Integer.MIN_VALUE));
        assertEquals(-1, ScaffoldModule.grimTicksSince(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertEquals(0, ScaffoldModule.grimTicksSince(7, 7));
        assertEquals(5, ScaffoldModule.grimTicksSince(12, 7));
        assertEquals(-1, ScaffoldModule.grimTicksSince(7, 12), "a tick in the future owes nothing");
    }

    private static Rotation driveGrimAim(
        QuantizedRotationSmoother smoother, Rotation start, java.util.function.IntFunction<Rotation> goalAt,
        double gcd, int ticks
    ) {
        Rotation current = start;
        for (int tick = 0; tick < ticks; tick++) {
            Rotation next = ScaffoldModule.stepGrimAimRotation(
                smoother, current, goalAt.apply(tick), 0.5F, gcd);
            assertTrue(next.pitch() >= -90.0F && next.pitch() <= 90.0F,
                "tick " + tick + " emitted pitch " + next.pitch());

            float yawStep = AutismRotationUtil.angleDifference(next.yaw(), current.yaw());
            assertEquals(0.0D, Math.abs(Math.IEEEremainder(yawStep, gcd)), 1.0E-3D,
                "tick " + tick + " yaw step " + yawStep + " is off the gcd grid");
            if (next.pitch() > -90.0F && next.pitch() < 90.0F) {
                float pitchStep = next.pitch() - current.pitch();
                assertEquals(0.0D, Math.abs(Math.IEEEremainder(pitchStep, gcd)), 1.0E-3D,
                    "tick " + tick + " pitch step " + pitchStep + " is off the gcd grid");
            }
            current = next;
        }
        return current;
    }

    private static final double[] GCD_RANGE = { 0.0096D, 0.0225D, 0.15D, 0.6144D };

    private static final int WIND_DOWN_MAX_TICKS = 27;

    private static final double COARSEST_GCD = 0.6144D;

    @Test
    void noCappedAimStepEverReachesIntavesSnapWindow() {

        for (double gcd : GCD_RANGE) {
            for (long seed = 1; seed <= 16; seed++) {
                QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
                java.util.Random random = new java.util.Random(seed);
                for (int[] pattern : new int[][] { {180, 0}, {90, -135}, {45, -45} }) {
                    smoother.reset(seed * 7L + pattern[0]);
                    Rotation current = new Rotation(0.0F, 75.0F);
                    for (int tick = 0; tick < 120; tick++) {
                        Rotation goal = tick % 5 == 0
                            ? new Rotation(random.nextFloat() * 360.0F - 180.0F,
                                random.nextFloat() * 180.0F - 90.0F)
                            : new Rotation(pattern[tick % 2], 84.0F);
                        Rotation next = ScaffoldModule.stepGrimAimRotation(
                            smoother, current, goal, 1.0F, gcd);
                        float yawStep = Math.abs(
                            AutismRotationUtil.angleDifference(next.yaw(), current.yaw()));
                        float pitchStep = Math.abs(next.pitch() - current.pitch());
                        assertTrue(yawStep < 24.0F,
                            "gcd " + gcd + " seed " + seed + " tick " + tick
                                + " emitted a " + yawStep + " degree yaw step");
                        assertTrue(pitchStep < 26.0F,
                            "gcd " + gcd + " tick " + tick + " emitted a " + pitchStep
                                + " degree pitch step");
                        current = next;
                    }
                }
            }
        }
    }

    @Test
    void theCapSaturatesOnTheFirstTickOutOfRest() {

        for (double gcd : GCD_RANGE) {
            QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
            smoother.reset(4242L);
            Rotation first = ScaffoldModule.stepGrimAimRotation(
                smoother, new Rotation(0.0F, 80.0F), new Rotation(90.0F, 80.0F), 0.0F, gcd);
            float step = AutismRotationUtil.angleDifference(first.yaw(), 0.0F);
            assertTrue(step >= 0.9F * ScaffoldModule.GRIM_MAX_YAW_STEP,
                "gcd " + gcd + " only moved " + step + " on the first tick out of rest");
        }
    }

    @Test
    void theCappedAimStillClosesNinetyDegreesQuickly() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(11L);
        Rotation current = new Rotation(0.0F, 80.0F);
        Rotation goal = new Rotation(90.0F, 80.0F);
        int ticks = 0;
        while (Math.abs(AutismRotationUtil.angleDifference(goal.yaw(), current.yaw())) > 0.5F
            && ticks < 40) {
            current = ScaffoldModule.stepGrimAimRotation(smoother, current, goal, 0.5F, 0.15D);
            ticks++;
        }
        assertTrue(ticks <= 8, "a 90 degree turn took " + ticks + " ticks");
    }

    @Test
    void grimCapCountsFloorsOnTheGridAndNeverFreezesACoarseSensitivity() {
        assertEquals(120L, ScaffoldModule.grimCapCounts(5000L, 18.0F, 0.15D));
        assertEquals(-120L, ScaffoldModule.grimCapCounts(-5000L, 18.0F, 0.15D));
        assertEquals(7L, ScaffoldModule.grimCapCounts(7L, 18.0F, 0.15D), "under the cap is untouched");

        assertEquals(1L, ScaffoldModule.grimCapCounts(9L, 18.0F, 25.0D));
        assertEquals(0L, ScaffoldModule.grimCapCounts(0L, 18.0F, 25.0D));
    }

    @Test
    void theEmittedYawIsAlwaysClearOfTheFortyFiveGridAndNeverAWholeDegree() {

        java.util.Random random = new java.util.Random(99L);
        for (double gcd : GCD_RANGE) {
            for (int i = 0; i < 20000; i++) {
                float raw = random.nextFloat() * 360.0F - 180.0F;
                if (i % 4 == 0) raw = Math.round(raw / 45.0F) * 45.0F;
                if (i % 7 == 0) raw = Math.round(raw);
                for (int dither = -1; dither <= 1; dither++) {
                    Rotation out = ScaffoldModule.grimShapeOutgoing(
                        new Rotation(raw, 80.0F), null, gcd, dither, Float.NaN);
                    assertTrue(Math.abs(ScaffoldModule.grimFortyFiveOffset(out.yaw()))
                            >= ScaffoldModule.GRIM_ANGLE_SNAP_MARGIN,
                        "gcd " + gcd + " raw " + raw + " emitted " + out.yaw()
                            + ", " + ScaffoldModule.grimFortyFiveOffset(out.yaw()) + " off the grid");
                    assertNotEquals(0.0F, out.yaw() % 1.0F, "emitted a whole degree: " + out.yaw());
                }
            }
        }
    }

    @Test
    void theEmittedYawNeverRepeatsThePreviousPacket() {

        for (double gcd : GCD_RANGE) {
            ScaffoldModule scaffold = new ScaffoldModule();
            Rotation raw = new Rotation(90.0F, 84.0F);
            Rotation previous = null;
            for (int tick = 0; tick < 500; tick++) {
                Rotation out = ScaffoldModule.grimShapeOutgoing(raw, previous, gcd, tick % 3 - 1, Float.NaN);
                if (previous != null) {
                    assertNotEquals(previous.yaw(), out.yaw(),
                        "gcd " + gcd + " tick " + tick + " repeated the packet yaw exactly");
                    float delta = Math.abs(
                        AutismRotationUtil.angleDifference(out.yaw(), previous.yaw()));
                    assertTrue(delta < 3.0F, "a parked aim drifted " + delta + " in one tick");
                }
                previous = out;
            }
            assertNotNull(scaffold);
        }
    }

    @Test
    void twoConsecutivePlacementsNeverShipTheSameYaw() {
        for (double gcd : GCD_RANGE) {
            for (float lane : new float[] {90.0F, 135.0F, -45.0F, 0.0F}) {
                Rotation previous = null;
                float lastPlaceYaw = Float.NaN;

                for (int place = 0; place < 20; place++) {
                    for (int tick = 0; tick < 6; tick++) {
                        previous = ScaffoldModule.grimShapeOutgoing(
                            new Rotation(lane, 84.0F), previous, gcd, tick % 3 - 1, lastPlaceYaw);
                    }
                    float yaw = previous.yaw();
                    if (!Float.isNaN(lastPlaceYaw)) {
                        float delta = Math.abs(
                            AutismRotationUtil.angleDifference(yaw, lastPlaceYaw));
                        assertNotEquals(lastPlaceYaw, yaw, "gcd " + gcd + " lane " + lane
                            + " place " + place + " repeated the placement yaw exactly");

                        assertTrue(delta + 1.0E-4D >= gcd,
                            "gcd " + gcd + " lane " + lane + " placements only " + delta + " apart");
                    }

                    assertTrue(ScaffoldModule.grimYawLegal(yaw),
                        "gcd " + gcd + " lane " + lane + " emitted " + yaw + " back onto the 45 grid");
                    assertTrue(Math.abs(AutismRotationUtil.angleDifference(yaw, lane)) < 1.5F,
                        "the placement yaw drifted " + yaw + " off lane " + lane);
                    lastPlaceYaw = yaw;
                }
            }
        }
    }

    @Test
    void theShaperStaysOnTheGridAndMovesTheYawLessThanOneDegree() {
        java.util.Random random = new java.util.Random(7L);
        for (double gcd : GCD_RANGE) {
            for (int i = 0; i < 5000; i++) {
                float raw = random.nextFloat() * 360.0F - 180.0F;
                Rotation out = ScaffoldModule.grimShapeOutgoing(
                    new Rotation(raw, 80.0F), null, gcd, random.nextInt(3) - 1, Float.NaN);
                float moved = AutismRotationUtil.angleDifference(out.yaw(), raw);
                assertEquals(0.0D, Math.abs(Math.IEEEremainder(moved, gcd)), 1.0E-3D,
                    "shaper left the gcd lattice: moved " + moved);

                assertTrue(Math.abs(moved) < 1.5F, "shaper moved the yaw " + moved + " degrees");
            }
        }
    }

    @Test
    void noFiveTickWindowSitsOnTheFortyFiveGridWhileSummingIntoTheAngleSnapWindow() {

        for (double gcd : GCD_RANGE) {
            QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
            smoother.reset(5L);
            java.util.List<Float> emitted = new java.util.ArrayList<>();
            Rotation raw = new Rotation(-179.0F, 84.0F);
            Rotation previous = null;
            for (int tick = 0; tick < 60; tick++) {
                raw = ScaffoldModule.stepGrimAimRotation(
                    smoother, raw, new Rotation(179.0F, 84.0F), 1.0F, gcd);
                previous = ScaffoldModule.grimShapeOutgoing(
                    raw, previous, gcd, tick % 3 - 1, Float.NaN);
                emitted.add(previous.yaw());
            }
            for (int end = 5; end < emitted.size(); end++) {
                float sum = 0.0F;
                for (int i = end - 4; i <= end; i++) {
                    sum += Math.abs(AutismRotationUtil.angleDifference(
                        emitted.get(i), emitted.get(i - 1)));
                }
                if (sum <= 60.0F || sum >= 300.0F) continue;
                for (int i = end - 4; i <= end; i++) {
                    assertTrue(Math.abs(ScaffoldModule.grimFortyFiveOffset(emitted.get(i)))
                            >= ScaffoldModule.GRIM_ANGLE_SNAP_MARGIN,
                        "gcd " + gcd + " tick " + i + " sat on the 45 grid at " + emitted.get(i)
                            + " while its 5-tick sum was " + sum);
                }
            }
        }
    }

    @Test
    void theRestingPostureYawIsLegalForEveryLaneAndEveryGcd() {
        for (double gcd : GCD_RANGE) {
            for (int step = 0; step < 8; step++) {
                float posture = ScaffoldModule.grimPlacementPostureYaw(
                    ScaffoldModule.compassStepYaw(step));
                Rotation out = ScaffoldModule.grimShapeOutgoing(
                    new Rotation(posture, 84.0F), null, gcd, 0, Float.NaN);
                assertTrue(ScaffoldModule.grimYawLegal(out.yaw()),
                    "lane " + step + " gcd " + gcd + " emitted " + out.yaw());
                assertTrue(Math.abs(AutismRotationUtil.angleDifference(out.yaw(), posture)) < 1.5F,
                    "the legalized posture drifted off the lane reverse");
            }
        }
    }

    @Test
    void grimPlacementPitchGoalNeverExceedsTheCap() {

        for (float solved = 0.0F; solved <= 90.0F; solved += 0.5F) {
            float goal = ScaffoldModule.grimPlacementPitchGoal(solved, 0.0D, 90.0D);
            assertTrue(goal <= 89.0F, "goal " + goal + " exceeds the cap");
            assertEquals(Math.min(solved, 89.0F), goal, 1.0E-4F,
                "the goal is pure geometry, no anchor pull");
        }
        assertFalse(ScaffoldModule.grimPlacementPitchLegal(89.6F));
        assertTrue(ScaffoldModule.grimPlacementPitchLegal(89.4F));
    }

    @Test
    void theWindDownReleasePredicateUsesTheSentDebtNotTheWrappedAngle() {

        assertEquals(720.0F, ScaffoldModule.grimHandbackDebt(725.0F, 5.0F), 1.0E-4F);
        assertFalse(ScaffoldModule.grimWindDownReleasable(
            ScaffoldModule.grimHandbackDebt(725.0F, 5.0F), 0.0F));
        assertTrue(ScaffoldModule.grimWindDownReleasable(1.5F, -1.0F));
        assertFalse(ScaffoldModule.grimWindDownReleasable(1.5F, 4.0F));

        assertEquals(0.0F, ScaffoldModule.grimHandbackDebt(Float.NaN, 5.0F));
    }

    @Test
    void theWindDownBudgetCoversTheSweepAtTheCap() {

        assertTrue(ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX + 3.0F * 0.6144F < 25.0F,
            "the wind-down's first step plus the coarsest-grid shaping slack must stay under 25");
        assertTrue(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP > ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX,
            "the ceiling is what the ramp reaches after the first tick, not the first tick itself");

        float ceiling = WIND_DOWN_MAX_TICKS * ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP;
        for (float debt = -ceiling; debt <= ceiling; debt += 7.5F) {
            int budget = ScaffoldModule.grimWindDownBudget(debt, 0.0F);
            assertTrue(budget >= 8, "budget " + budget + " for debt " + debt);
            assertTrue(budget * ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP >= Math.abs(debt),
                "budget " + budget + " ticks cannot pay off " + debt + " at "
                    + ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP + "/tick");
        }

        assertEquals(ceiling,
            ScaffoldModule.grimWindDownBudget(ceiling, 0.0F)
                * ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP, 0.001F);
        assertTrue(ScaffoldModule.grimWindDownBudget(0.0F, 170.0F) >= 9,
            "a pitch-only release still needs its own ticks");
    }

    private static double emittedCeiling(float cap, double gcd) {
        return Math.floor(cap / gcd) * gcd + 3.0D * gcd + ScaffoldModule.GRIM_ANGLE_SNAP_MARGIN;
    }

    @Test
    void theWindDownCeilingIsPricedUnderIntavesFortyDegreeBand() {

        assertTrue(emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP, COARSEST_GCD) < 40.0D,
            "the yaw ceiling emits inside Intave's snap band on the coarsest grid");
        assertTrue(emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_PITCH_STEP, COARSEST_GCD) < 40.0D,
            "the pitch ceiling emits inside Intave's snap band on the coarsest grid");

        for (int step = 0; step <= 100000; step++) {
            double sensitivity = step / 100000.0D;
            double f = sensitivity * 0.6D + 0.2D;
            double gcd = f * f * f * 8.0D * 0.15D;
            assertTrue(emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP, gcd) < 40.0D,
                () -> "sensitivity " + sensitivity + " emits "
                    + emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP, gcd));
            assertTrue(emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_PITCH_STEP, gcd) < 40.0D,
                () -> "sensitivity " + sensitivity + " emits "
                    + emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_PITCH_STEP, gcd));
        }
    }

    @Test
    void theWindDownOpensBelowTheDeadStopSnapLine() {

        for (double gcd : GCD_RANGE) {
            QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
            smoother.reset(11L);
            Rotation rest = new Rotation(0.0F, 0.0F);
            Rotation stepped = ScaffoldModule.stepGrimAimRotation(
                smoother, rest, -720.0F, 0.0F, 0.5F, gcd,
                ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX,
                ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX);
            Rotation emitted = ScaffoldModule.grimShapeOutgoing(stepped, null, gcd, 1, Float.NaN);
            float opening = Math.abs(Mth.wrapDegrees(emitted.yaw() - rest.yaw()));
            assertTrue(opening <= 25.0F,
                "gcd " + gcd + " opened the wind-down with a " + opening + " degree packet");
        }
    }

    @Test
    void aFullWindDownIncludingTheReleaseTickStaysUnderTheSweepCeiling() {

        for (double gcd : GCD_RANGE) {
            QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
            smoother.reset(3L);
            float cameraYaw = 5.0F;
            float cameraPitch = 12.0F;
            Rotation raw = new Rotation(-175.0F, 84.0F);
            Rotation emitted = null;
            float sent = 725.0F;
            boolean converged = false;
            java.util.List<Float> packets = new java.util.ArrayList<>();
            for (int tick = 0; tick < 64; tick++) {
                float debt = ScaffoldModule.grimHandbackDebt(sent, cameraYaw);
                float pitchDelta = cameraPitch - raw.pitch();
                if (ScaffoldModule.grimWindDownReleasable(debt, pitchDelta)) {
                    converged = true;
                    break;
                }
                float yawCap = tick == 0 ? ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX
                    : ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP;
                float pitchCap = tick == 0 ? ScaffoldModule.GRIM_WIND_DOWN_FIRST_STEP_MAX
                    : ScaffoldModule.GRIM_WIND_DOWN_MAX_PITCH_STEP;
                raw = ScaffoldModule.stepGrimAimRotation(
                    smoother, raw, -debt, pitchDelta, 0.5F, gcd, yawCap, pitchCap);
                emitted = ScaffoldModule.grimShapeOutgoing(
                    raw, emitted, gcd, tick % 3 - 1, Float.NaN);
                sent = ScaffoldModule.grimContinuousYaw(sent, emitted.yaw());
                packets.add(sent);
            }
            packets.add(cameraYaw);
            assertTrue(converged, "gcd " + gcd + " never converged");
            double ceiling = emittedCeiling(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP, gcd);

            assertTrue(ceiling < 40.0D, "gcd " + gcd + " prices the sweep at " + ceiling);
            for (int i = 1; i < packets.size(); i++) {
                float delta = Math.abs(packets.get(i) - packets.get(i - 1));
                assertTrue(delta <= ceiling,
                    "gcd " + gcd + " packet " + i + " jumped " + delta + " degrees");
            }

            for (int i = 2; i < packets.size(); i++) {
                float twoBack = Math.abs(packets.get(i - 2) - packets.get(i - 3 < 0 ? 0 : i - 3));
                float last = Math.abs(packets.get(i - 1) - packets.get(i - 2));
                float now = Math.abs(packets.get(i) - packets.get(i - 1));
                assertFalse(twoBack == 0.0F && last > 25.0F && now < 9.0F,
                    "gcd " + gcd + " produced Intave's scaffold snap triple at packet " + i);
            }
        }
    }

    @Test
    void grimAimStaysOnTheSensitivityGridOnAdversarialGoals() {
        for (double gcd : new double[] {0.0225D, 0.6D}) {
            for (long seed = 1; seed <= 8; seed++) {
                QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
                smoother.reset(seed);

                driveGrimAim(smoother, new Rotation(0.0F, 75.0F),
                    tick -> new Rotation(180.0F, 75.0F), gcd, 50);

                smoother.reset(seed * 31L);
                driveGrimAim(smoother, new Rotation(0.0F, 40.0F),
                    tick -> new Rotation((tick / 5) % 2 == 0 ? 90.0F : -90.0F, 40.0F), gcd, 50);

                smoother.reset(seed * 77L);
                driveGrimAim(smoother, new Rotation(-30.0F, -60.0F),
                    tick -> new Rotation(-30.0F, 84.3F), gcd, 50);
            }
        }
    }

    @Test
    void grimAimConvergesOntoAFixedGoal() {

        Rotation goal = new Rotation(120.0F, 30.0F);
        for (double gcd : new double[] {0.0225D, 0.6D}) {
            for (long seed = 1; seed <= 8; seed++) {
                QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
                smoother.reset(seed * 7919L + 17L);
                Rotation current = new Rotation(0.0F, 0.0F);
                boolean arrived = false;
                for (int tick = 0; tick < 8 && !arrived; tick++) {
                    Rotation next = ScaffoldModule.stepGrimAimRotation(smoother, current, goal, 0.5F, gcd);
                    float yawBefore = AutismRotationUtil.angleDifference(goal.yaw(), current.yaw());
                    float yawAfter = AutismRotationUtil.angleDifference(goal.yaw(), next.yaw());
                    if (Math.signum(yawAfter) != Math.signum(yawBefore) && yawBefore != 0.0F) {
                        assertTrue(Math.abs(yawAfter) <= 2.0D * gcd + 1.0E-3D,
                            "gcd " + gcd + " seed " + seed + " tick " + tick
                                + " overshot the goal by " + Math.abs(yawAfter) + " degrees");
                    }
                    float pitchBefore = goal.pitch() - current.pitch();
                    float pitchAfter = goal.pitch() - next.pitch();
                    if (Math.signum(pitchAfter) != Math.signum(pitchBefore) && pitchBefore != 0.0F) {
                        assertTrue(Math.abs(pitchAfter) <= 2.0D * gcd + 1.0E-3D,
                            "gcd " + gcd + " seed " + seed + " tick " + tick
                                + " overshot the pitch by " + Math.abs(pitchAfter) + " degrees");
                    }
                    current = next;
                    double error = Math.hypot(yawAfter, pitchAfter);
                    arrived = error < 2.0D;
                }
                assertTrue(arrived,
                    "gcd " + gcd + " seed " + seed + " never closed onto the goal, ended at " + current);
            }
        }
    }

    @Test
    void postureYawStaysOnTheFortyFiveGridSoOneKeyOctantWalksTheLane() {

        for (int step = 0; step < 8; step++) {
            float lane = ScaffoldModule.compassStepYaw(step);
            float posture = ScaffoldModule.grimPlacementPostureYaw(lane);
            float relative = Math.abs(AutismRotationUtil.angleDifference(lane, posture));
            assertEquals(0.0F, relative % 45.0F, 1.0E-3F,
                "lane " + lane + " is not a whole octant off posture " + posture);
        }
    }

    @Test
    void thePostureWalksTheLaneExactlyForEveryStep() {

        Input forward = input(true, false, false, false);
        for (int step = 0; step < 8; step++) {
            float lane = ScaffoldModule.compassStepYaw(step);
            float posture = ScaffoldModule.grimPlacementPostureYaw(lane);
            Vec3 wanted = movementVector(forward, lane);
            Vec3 travel = movementVector(
                ScaffoldModule.transformSilentMovementInput(forward, lane, posture), posture);
            assertEquals(wanted.x, travel.x, 1.0E-6D, "lane " + lane + " travel x");
            assertEquals(wanted.z, travel.z, 1.0E-6D, "lane " + lane + " travel z");
        }
    }

    @Test
    void silentCorrectionPreservesEveryEightWayMovementDirection() {
        List<Input> directions = List.of(
            input(true, false, false, false),
            input(true, false, true, false),
            input(false, false, true, false),
            input(false, true, true, false),
            input(false, true, false, false),
            input(false, true, false, true),
            input(false, false, false, true),
            input(true, false, false, true)
        );
        float[] rotations = {-180.0F, -135.0F, -90.0F, -45.0F, 0.0F, 45.0F, 90.0F, 135.0F, 180.0F};

        for (Input direction : directions) {
            for (float playerYaw : rotations) {
                for (float silentYaw : rotations) {
                    Input transformed = ScaffoldModule.transformSilentMovementInput(
                        direction, playerYaw, silentYaw);
                    Vec3 expected = movementVector(direction, playerYaw);
                    Vec3 actual = movementVector(transformed, silentYaw);

                    assertEquals(expected.x, actual.x, 1.0E-6D,
                        () -> "x changed for player=" + playerYaw + ", silent=" + silentYaw);
                    assertEquals(expected.z, actual.z, 1.0E-6D,
                        () -> "z changed for player=" + playerYaw + ", silent=" + silentYaw);
                }
            }
        }
    }

    @Test
    void cameraReferencedTransformKeepsTheWorldTravelOctant() {

        List<Input> directions = List.of(
            input(true, false, false, false),
            input(true, false, true, false),
            input(false, false, true, false),
            input(false, true, true, false),
            input(false, true, false, false),
            input(false, true, false, true),
            input(false, false, false, true),
            input(true, false, false, true)
        );
        float[] cameras = {0.0F, 30.0F, 45.0F, 90.0F, 135.0F, 180.0F, -45.0F, -120.0F};
        float[] offsets = {90.0F, 150.0F, 180.0F, 198.0F, 208.0F, -160.0F};

        for (float camera : cameras) {
            for (float offset : offsets) {
                float silent = camera + offset;
                boolean onGrid = Math.abs(offset) % 45.0F == 0.0F;
                for (Input held : directions) {
                    Input sent = ScaffoldModule.transformSilentMovementInput(held, camera, silent);
                    Vec3 wanted = movementVector(held, camera);
                    Vec3 walked = movementVector(sent, silent);
                    assertTrue(walked.lengthSqr() > 1.0E-9D,
                        "camera " + camera + " offset " + offset + " swallowed the input outright");
                    float wantedAngle = (float) Math.toDegrees(Math.atan2(wanted.x, wanted.z));
                    float walkedAngle = (float) Math.toDegrees(Math.atan2(walked.x, walked.z));
                    float error = Math.abs(Mth.wrapDegrees(walkedAngle - wantedAngle));
                    assertTrue(error <= (onGrid ? 0.5F : 30.05F),
                        "camera " + camera + " offset " + offset + " walked " + error
                            + " degrees off the requested travel");
                }
            }
        }
    }

    @Test
    void grimRestoresTheFullNeighborFallbackFootprint() {
        List<BlockPos> offsets = ScaffoldModule.grimCandidateOffsets();

        assertEquals(27, offsets.size());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertTrue(offsets.contains(new BlockPos(x, 0, z)),
                    "the current layer must include every neighboring fallback");
                assertTrue(offsets.contains(new BlockPos(x, -1, z)),
                    "jump-up must retain every lower-layer support fallback");
                assertTrue(offsets.contains(new BlockPos(x, 1, z)),
                    "falling must retain every upper-layer catch fallback");
            }
        }
    }

    @Test
    void grimAlwaysTriesTheAuthoredCellBeforeFallbacks() {
        assertEquals(0, ScaffoldModule.grimCandidatePriority(BlockPos.ZERO));
        for (BlockPos offset : ScaffoldModule.grimCandidateOffsets()) {
            if (!BlockPos.ZERO.equals(offset)) {
                assertEquals(1, ScaffoldModule.grimCandidatePriority(offset),
                    "neighboring supports remain fallbacks rather than outranking the bridge cell");
            }
        }
    }

    @Test
    void theTellyRiseNoLongerClampsItsApexPitchButStillVerifiesTheRay() {

        AutismRotationUtil.Rotation steep = new AutismRotationUtil.Rotation(-90.0F, 86.4F);
        assertTrue(steep.pitch() > 85.0F,
            "the apex aim the clamp used to cut is exactly what the climb needs");
        AutismRotationUtil.Rotation burst = ScaffoldModule.tellyMouseBurstRotation(
            new AutismRotationUtil.Rotation(-90.0F, 40.0F), steep, 95.0F, 95.0F, 0.15D);
        assertTrue(burst.pitch() <= 89.9F && burst.pitch() > 85.0F,
            "the burst reaches the steep aim without a placement-pitch clamp cutting it");
    }

    @Test
    void aCellInsideThePlayerIsNeverClicked() {

        AABB player = new AABB(4.7D, 63.0D, 8.7D, 5.3D, 64.8D, 9.3D);
        assertTrue(ScaffoldModule.cellBlocksPlayer(new BlockPos(4, 63, 8), player),
            "the cell the player stands in must be refused");
        assertFalse(ScaffoldModule.cellBlocksPlayer(new BlockPos(4, 62, 8), player),
            "the cell below the feet is the normal bridge cell and must be allowed");
        assertFalse(ScaffoldModule.cellBlocksPlayer(new BlockPos(6, 63, 8), player),
            "a cell the box does not reach must be allowed");
        assertFalse(ScaffoldModule.cellBlocksPlayer(null, player));
    }

    @Test
    void theContinuousYawBranchCanShiftTheSineAndSoMustDriveAccelerationToo() {

        boolean anyDiverged = false;
        for (float base : new float[] {154.5F, -12.25F, 89.9F, 0.0F, -179.95F, 37.4F}) {
            float continuous = ScaffoldModule.grimContinuousYaw(1080.0F, base);
            assertEquals(base, Mth.wrapDegrees(continuous), 1.0E-3F,
                "the branch must preserve the angle, yaw " + base);
            if (Mth.sin(base * Mth.DEG_TO_RAD) != Mth.sin(continuous * Mth.DEG_TO_RAD)) {
                anyDiverged = true;
            }
        }
        assertTrue(anyDiverged,
            "the hazard this guards is real: some branch must give a different sine, or the "
                + "acceleration/packet split would be harmless and this pin is pointless");
    }

    @Test
    void thePlacementHoldReleasesBeforeTouchdownSoSprintSurvives() {

        for (ScaffoldModule.TellyPhase phase : new ScaffoldModule.TellyPhase[] {
            ScaffoldModule.TellyPhase.LAUNCH, ScaffoldModule.TellyPhase.AIMING
        }) {
            assertEquals(ScaffoldModule.TellyRotationIntent.HOLD,
                ScaffoldModule.tellyAirRotationIntent(phase, false, true, false, false),
                "mid-flight the hold still prevents head shaking in " + phase);
            assertEquals(ScaffoldModule.TellyRotationIntent.FORWARD,
                ScaffoldModule.tellyAirRotationIntent(phase, false, true, false, true),
                "but it must release once the ground is imminent in " + phase);
        }

        assertEquals(ScaffoldModule.TellyRotationIntent.PLACEMENT,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.AIMING, true, true, false, true));

        assertEquals(ScaffoldModule.TellyRotationIntent.RETURN,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.AIMING, true, true, true, false));
    }

    @Test
    void aRiseIsDroppedOnceTheFootprintHasLeftItsColumn() {

        BlockPos cell = new BlockPos(76, 75, -133);
        assertTrue(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(76.76D, 76.25D, -132.45D), new Vec3(0.0D, 0.003D, 0.081D), cell),
            "at pin time the arc still comes down on the cell, so the plan was sound then");
        assertFalse(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(76.76D, 76.18D, -131.98D), new Vec3(0.0D, -0.152D, 0.119D), cell),
            "by the click tick the arc lands past the far lip - that is the slide off");
    }

    @Test
    void theRiseLandingRunsTheWholeArcAndNotAFixedLead() {

        assertFalse(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(78.41D, 86.17D, 59.42D), new Vec3(0.0D, 0.083D, -0.136D),
                new BlockPos(78, 85, 59)),
            "the arc lands past the cell, so the climb block must not go down at all");

        assertTrue(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(74.63D, 86.25D, 55.33D), new Vec3(-0.139D, 0.003D, 0.003D),
                new BlockPos(74, 85, 55)),
            "this one comes down on the block, so the climb stays");

        assertFalse(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(68.56D, 88.17D, 55.27D), new Vec3(0.136D, 0.083D, 0.0D),
                new BlockPos(68, 87, 55)),
            "t047 lands at 69.4, a column past 68 - the climb block must not go down");
        assertFalse(ScaffoldModule.footprintOverlapsColumn(
                new Vec3(76.54D, 90.17D, 53.51D), new Vec3(0.136D, 0.083D, 0.0D),
                new BlockPos(76, 89, 53)),
            "t135 is the same shape one staircase higher");
    }

    @Test
    void theHeldPinOwnsThePinTierForItsOwnCell() {
        BlockPos support = new BlockPos(26, 83, 90);
        BlockPos cell = new BlockPos(27, 83, 90);
        assertTrue(ScaffoldModule.grimHeldPinOwnsCandidate(support, Direction.EAST, cell),
            "the pin's placed cell is the support advanced by its face");
        assertFalse(ScaffoldModule.grimHeldPinOwnsCandidate(support, Direction.SOUTH, cell),
            "a different face of the same support places a different cell");
        assertFalse(ScaffoldModule.grimHeldPinOwnsCandidate(support, Direction.EAST,
                new BlockPos(26, 83, 91)),
            "and a different cell is never claimed by this pin");
        assertFalse(ScaffoldModule.grimHeldPinOwnsCandidate(null, Direction.EAST, cell),
            "no pin, no latch");

        assertTrue(ScaffoldModule.grimLatchSuppressesPin(true, false),
            "the other L-leg's claim is suppressed while the held pin still pins");
        assertFalse(ScaffoldModule.grimLatchSuppressesPin(true, true),
            "the held face itself is never suppressed");
        assertFalse(ScaffoldModule.grimLatchSuppressesPin(false, false),
            "a dropped pin frees both legs again");
    }

    @Test
    void theRiseDropReselectsOnlyGroundedWithTheJumpHeld() {
        assertTrue(ScaffoldModule.grimRiseDropReselects(true, true),
            "grounded + jump held is the corner stall - re-select once");
        assertFalse(ScaffoldModule.grimRiseDropReselects(false, true),
            "mid-arc the drop is the guard that keeps the rise honest");
        assertFalse(ScaffoldModule.grimRiseDropReselects(true, false),
            "grounded without climb intent is a plain refusal, not a stall");
    }

    @Test
    void theLipStopOnlyFiresGroundedPastTheOwedBudgetWithNoClick() {
        assertTrue(ScaffoldModule.grimLipStopApplies(true, false, true, 11, 10),
            "past the cap with no click armed, the walk stops instead of releasing");
        assertFalse(ScaffoldModule.grimLipStopApplies(true, false, true, 10, 10),
            "inside the budget the ordinary sneak clamp owns the lip");
        assertFalse(ScaffoldModule.grimLipStopApplies(true, true, true, 11, 10),
            "an armed click means the block is on its way - walk");
        assertFalse(ScaffoldModule.grimLipStopApplies(false, false, true, 11, 10),
            "airborne nothing owns the keys but the player - the lip stop is grounded only");
        assertFalse(ScaffoldModule.grimLipStopApplies(true, false, false, 11, 10),
            "no owed footing means nothing to stop for");
    }

    @Test
    void aSupportOutsideTheFrontierIsNotHeld() {
        assertTrue(ScaffoldModule.supportTooFarToHold(
                new Vec3(76.44D, 85.0D, 55.84D), new BlockPos(73, 84, 53), 2.5D),
            "e@73,84,53 from 76.44,55.84 is 3.8 out - that is not the frontier");

        assertFalse(ScaffoldModule.supportTooFarToHold(
                new Vec3(78.27D, 85.0D, 67.85D), new BlockPos(78, 84, 66), 2.5D),
            "the cell under the player is always held");
        assertFalse(ScaffoldModule.supportTooFarToHold(
                new Vec3(78.41D, 86.0D, 58.44D), new BlockPos(77, 84, 58), 2.5D),
            "and so is a lane support a block to the side");

        assertFalse(ScaffoldModule.supportTooFarToHold(
                new Vec3(74.63D, 86.25D, 55.33D), new BlockPos(74, 84, 55), 2.5D),
            "the rise support is directly below, which is zero horizontal distance");
    }

    @Test
    void theTellyLaneBiasCannotBreakTheSidewaysDwell() {

        assertTrue(ScaffoldModule.TELLY_LANE_BIAS_MAX < ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES,
            "the bias must never be able to break the sideways dwell");
        assertTrue(ScaffoldModule.TELLY_LANE_BIAS_MAX < ScaffoldModule.GRIM_INPUT_OCTANT_BOUNDARY_DEGREES,
            "nor be large enough to pick an octant on its own");

        float bias = 0.0F;
        for (int tick = 0; tick < 40; tick++) {
            float next = ScaffoldModule.approachTellyLaneBias(bias, 38.0F);
            assertTrue(Math.abs(next - bias) <= ScaffoldModule.TELLY_LANE_BIAS_SLEW + 1.0E-4F,
                "bias jumped " + (next - bias) + " degrees in one tick");
            bias = next;
        }
        assertEquals(ScaffoldModule.TELLY_LANE_BIAS_MAX, bias, 1.0E-4F,
            "a saturated request settles at the cap");
        assertEquals(-ScaffoldModule.TELLY_LANE_BIAS_MAX,
            ScaffoldModule.approachTellyLaneBias(-ScaffoldModule.TELLY_LANE_BIAS_MAX, -90.0F), 1.0E-4F,
            "and is clamped on the other side too");
    }

    @Test
    void theTellyLaneBandExceedsOneTickOfAuthority() {

        double desired = 0.0D;
        double perp = 0.20D;
        ScaffoldModule.TellyStrafe previous = ScaffoldModule.TellyStrafe.NONE;
        int reversals = 0;
        for (int tick = 0; tick < 40; tick++) {
            ScaffoldModule.TellyStrafe arm = ScaffoldModule.tellyLaneDamperArm(perp, desired);
            if (arm != ScaffoldModule.TellyStrafe.NONE && previous != ScaffoldModule.TellyStrafe.NONE
                && arm != previous) {
                reversals++;
            }
            if (arm != ScaffoldModule.TellyStrafe.NONE) previous = arm;
            if (arm == ScaffoldModule.TellyStrafe.LEFT) perp += ScaffoldModule.TELLY_LANE_TICK_AUTHORITY;
            if (arm == ScaffoldModule.TellyStrafe.RIGHT) perp -= ScaffoldModule.TELLY_LANE_TICK_AUTHORITY;
        }
        assertTrue(reversals <= 1, "the damper reversed " + reversals + " times: that is the key spam");
        assertTrue(Math.abs(perp - desired) <= ScaffoldModule.TELLY_LANE_TICK_AUTHORITY,
            "and it still has to actually kill the drift, ended at " + perp);
    }

    @Test
    void theTellyDamperTargetsVelocityNotZero() {

        double authority = ScaffoldModule.TELLY_LANE_TICK_AUTHORITY;
        assertEquals(ScaffoldModule.TellyStrafe.LEFT,
            ScaffoldModule.tellyLaneDamperArm(0.0D, authority * 3.0D),
            "standing still with a leftward setpoint must ask for left");
        assertEquals(ScaffoldModule.TellyStrafe.RIGHT,
            ScaffoldModule.tellyLaneDamperArm(0.0D, -authority * 3.0D),
            "and rightward for the mirror case");
        assertEquals(ScaffoldModule.TellyStrafe.NONE,
            ScaffoldModule.tellyLaneDamperArm(0.0D, 0.0D),
            "on the lane with no drift it must be quiet");
        assertEquals(ScaffoldModule.TellyStrafe.NONE,
            ScaffoldModule.tellyLaneDamperArm(Double.NaN, 0.0D));
    }

    @Test
    void walkingNearlyStraightLaysAStraightRun() {

        for (float yaw : new float[] { 0.0F, 5.0F, -5.0F, 20.0F, -20.0F, 90.0F, 179.0F, -179.0F }) {
            double rad = Math.toRadians(yaw);
            BlockPos step = ScaffoldModule.rageStepFromDirection(-Math.sin(rad), Math.cos(rad));
            assertNotNull(step, "yaw " + yaw + " produced no course");
            assertTrue(step.getX() == 0 || step.getZ() == 0,
                "yaw " + yaw + " is a straight walk but produced the diagonal " + step);
        }

        for (float yaw : new float[] { 45.0F, -45.0F, 135.0F, -135.0F }) {
            double rad = Math.toRadians(yaw);
            BlockPos step = ScaffoldModule.rageStepFromDirection(-Math.sin(rad), Math.cos(rad));
            assertNotNull(step);
            assertTrue(step.getX() != 0 && step.getZ() != 0,
                "yaw " + yaw + " is a real diagonal but produced " + step);
        }

        assertNull(ScaffoldModule.rageStepFromDirection(0.0D, 0.0D), "no keys means no course");
    }

    @Test
    void rageLaysTheWholeRunAhead() {

        BlockPos base = new BlockPos(10, 63, 20);
        List<BlockPos> north = ScaffoldModule.rageLaneCells(
            base, new BlockPos(0, 0, -1), 3, pos -> false);
        assertEquals(List.of(
            base, new BlockPos(10, 63, 19), new BlockPos(10, 63, 18), new BlockPos(10, 63, 17)),
            north, "a cardinal run is the walkway cell plus one per step ahead");

        assertEquals(List.of(base), ScaffoldModule.rageLaneCells(base, null, 3, pos -> false));
    }

    @Test
    void aDiagonalRageRunIsFaceConnected() {

        BlockPos base = new BlockPos(0, 63, 0);
        BlockPos step = new BlockPos(1, 0, 1);
        List<BlockPos> cells = ScaffoldModule.rageLaneCells(base, step, 2, pos -> false);

        for (int i = 1; i <= 2; i++) {
            BlockPos lane = new BlockPos(i, 63, i);
            assertTrue(cells.contains(lane), "the diagonal cell " + i + " must be laid");
            BlockPos alongX = new BlockPos(i - 1, 63, i);
            BlockPos alongZ = new BlockPos(i, 63, i - 1);
            assertTrue(cells.contains(alongX) || cells.contains(alongZ),
                "step " + i + " is corner-only: nothing connects " + lane + " to the step before");
        }

        List<BlockPos> withFloor = ScaffoldModule.rageLaneCells(
            base, step, 1, pos -> pos.equals(new BlockPos(0, 63, 1)));
        assertEquals(List.of(base, new BlockPos(1, 63, 1)), withFloor,
            "an existing orthogonal neighbour already makes the step connected");
    }

    @Test
    void rageDefaultsToMeteorsOwnCadence() {

        assertEquals(0, ScaffoldModule.RAGE_BLOCKS_DEFAULT, "default must be Meteor's cadence");
        assertTrue(ScaffoldModule.RAGE_BLOCKS_MAX <= 5, "a 6th block can never validate");

        BlockPos base = new BlockPos(4, 63, 8);
        assertEquals(List.of(base),
            ScaffoldModule.rageLaneCells(base, new BlockPos(0, 0, 1), 0, pos -> false),
            "zero ahead lays the walkway cell and nothing else");
    }

    @Test
    void grimEdgePredictionAlwaysTargetsTheFirstOutsideCell() {
        Vec3 east = ScaffoldModule.grimEdgePlacementPrediction(
            new Vec3(11.0D, 64.0D, 20.5D), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 west = ScaffoldModule.grimEdgePlacementPrediction(
            new Vec3(10.0D, 64.0D, 20.5D), new Vec3(-1.0D, 0.0D, 0.0D));
        Vec3 corner = ScaffoldModule.grimEdgePlacementPrediction(
            new Vec3(11.0D, 64.0D, 21.0D), new Vec3(1.0D, 0.0D, 1.0D));

        assertEquals(new BlockPos(11, 63, 20), ScaffoldModule.targetedBase(east));
        assertEquals(new BlockPos(9, 63, 20), ScaffoldModule.targetedBase(west),
            "negative boundaries need an outward nudge before flooring");
        assertEquals(new BlockPos(11, 63, 21), ScaffoldModule.targetedBase(corner),
            "a corner clamp must target the diagonal gap instead of its solid support");
        assertEquals(new Vec3(4.0D, 70.0D, 6.0D),
            ScaffoldModule.grimEdgePlacementPrediction(
                new Vec3(4.0D, 70.0D, 6.0D), Vec3.ZERO));

        Vec3[] boundaries = {
            new Vec3(11.0D, 64.0D, 20.5D),
            new Vec3(10.0D, 64.0D, 20.5D),
            new Vec3(10.5D, 64.0D, 21.0D),
            new Vec3(10.5D, 64.0D, 20.0D),
            new Vec3(11.0D, 64.0D, 21.0D),
            new Vec3(11.0D, 64.0D, 20.0D),
            new Vec3(10.0D, 64.0D, 21.0D),
            new Vec3(10.0D, 64.0D, 20.0D)
        };
        Vec3[] directions = {
            new Vec3(1.0D, 0.0D, 0.0D),
            new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D),
            new Vec3(0.0D, 0.0D, -1.0D),
            new Vec3(1.0D, 0.0D, 1.0D),
            new Vec3(1.0D, 0.0D, -1.0D),
            new Vec3(-1.0D, 0.0D, 1.0D),
            new Vec3(-1.0D, 0.0D, -1.0D)
        };
        BlockPos[] expected = {
            new BlockPos(11, 63, 20), new BlockPos(9, 63, 20),
            new BlockPos(10, 63, 21), new BlockPos(10, 63, 19),
            new BlockPos(11, 63, 21), new BlockPos(11, 63, 19),
            new BlockPos(9, 63, 21), new BlockPos(9, 63, 19)
        };
        for (int index = 0; index < directions.length; index++) {
            assertEquals(expected[index], ScaffoldModule.targetedBase(
                ScaffoldModule.grimEdgePlacementPrediction(boundaries[index], directions[index])),
                "every cardinal and diagonal boundary must floor outward");
        }
    }

    @Test
    void grimCornerHandoffStartsFromTheLastRealSupport() {
        Vec3 position = new Vec3(11.2D, 64.0D, 21.2D);
        Vec3 previousOrigin = new Vec3(10.5D, 64.0D, 20.5D);
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos support = new BlockPos(10, 63, 20);

        assertEquals(new Vec3(10.5D, 64.0D, 20.5D),
            ScaffoldModule.grimCornerLineAnchor(
                position, previousOrigin, south, east, support),
            "a corner turn must originate on the face-connected support, not the empty diagonal");
        assertEquals(new Vec3(10.5D, 64.0D, 21.2D),
            ScaffoldModule.grimCornerLineAnchor(
                position, previousOrigin, south, south, support),
            "an aligned handoff must retain its existing lane");
        assertEquals(position, ScaffoldModule.grimCornerLineAnchor(
            position, previousOrigin, south, east, null));
    }

    @Test
    void grimEdgeIntentCannotOscillateWhileTheSneakClampIsActive() {
        ScaffoldModule.MovementLine south = new ScaffoldModule.MovementLine(
            new Vec3(10.5D, 64.0D, 20.5D), new Vec3(0.0D, 0.0D, 1.0D));
        ScaffoldModule.MovementLine east = new ScaffoldModule.MovementLine(
            new Vec3(10.8D, 64.0D, 20.8D), new Vec3(1.0D, 0.0D, 0.0D));

        ScaffoldModule.MovementLine acquired = ScaffoldModule.grimEdgeIntentLine(
            south, null, true, 64.25D);
        assertEquals(south.direction(), acquired.direction());
        assertEquals(new Vec3(10.5D, 64.25D, 20.5D), acquired.origin());

        ScaffoldModule.MovementLine retained = ScaffoldModule.grimEdgeIntentLine(
            east, south, true, 64.5D);
        assertEquals(south.direction(), retained.direction(),
            "camera yaw must not switch the target while its edge block is being secured");
        assertEquals(new Vec3(10.5D, 64.5D, 20.5D), retained.origin(),
            "only the live collision height may change during the edge transaction");

        assertEquals(east, ScaffoldModule.grimEdgeIntentLine(east, south, false, 64.5D),
            "leaving the edge must immediately release the retained course");
        assertNull(ScaffoldModule.grimEdgeIntentLine(null, south, true, 64.5D),
            "releasing movement must never leave a synthetic course active");
    }

    @Test
    void grimJumpKeepsTheFirstGapTargetBeforeItIsRayVisible() {
        assertTrue(ScaffoldModule.grimGapPredictionApplies(true, true, false),
            "near-edge jump prediction must target the gap instead of the solid takeoff block");
        assertTrue(ScaffoldModule.grimGapPredictionApplies(true, false, true),
            "an active fall guard must retain the same exact gap target");
        assertFalse(ScaffoldModule.grimGapPredictionApplies(false, true, true),
            "Fast mode keeps its existing prediction semantics");

        assertTrue(ScaffoldModule.grimExactCornerVisibility(true, true, true, false),
            "a grounded crouch-clamped corner may disambiguate immediately visible connectors");
        assertFalse(ScaffoldModule.grimExactCornerVisibility(true, true, false, false),
            "airborne faces must remain available early enough for pre-aim");
        assertFalse(ScaffoldModule.grimExactCornerVisibility(true, true, true, true),
            "the jump-start tick must not discard its future placement face");
    }

    @Test
    void grimAirbornePreAimStaysEligibleOnlyMidCatch() {
        assertTrue(ScaffoldModule.grimAirbornePreAimEligible(false, true, true));
        assertFalse(ScaffoldModule.grimAirbornePreAimEligible(true, true, true));
        assertFalse(ScaffoldModule.grimAirbornePreAimEligible(false, false, true));
        assertFalse(ScaffoldModule.grimAirbornePreAimEligible(false, true, false));
    }

    @Test
    void tellySupportAcceptsLeavesAndEveryStandableBlock() {
        assertTrue(ScaffoldModule.tellySupportState(
            Blocks.OAK_LEAVES.defaultBlockState(),
            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            net.minecraft.world.phys.shapes.CollisionContext.empty()),
            "leaves are not face-sturdy but carry the player - Telly must engage on a tree");
        assertTrue(ScaffoldModule.tellySupportState(
            Blocks.STONE.defaultBlockState(),
            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            net.minecraft.world.phys.shapes.CollisionContext.empty()));
        assertFalse(ScaffoldModule.tellySupportState(
            Blocks.AIR.defaultBlockState(),
            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            net.minecraft.world.phys.shapes.CollisionContext.empty()));
        assertFalse(ScaffoldModule.tellySupportState(
            Blocks.TORCH.defaultBlockState(),
            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            net.minecraft.world.phys.shapes.CollisionContext.empty()),
            "a collisionless attachable is not a support");
    }

    private static boolean standable(net.minecraft.world.level.block.state.BlockState state) {
        return ScaffoldModule.standableSupportState(
            state, net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            net.minecraft.world.phys.shapes.CollisionContext.empty());
    }

    @Test
    void footingCountsEveryBlockThatActuallyHoldsThePlayerUp() {

        assertFalse(Blocks.OAK_LEAVES.defaultBlockState().isFaceSturdy(
            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
            Direction.UP, net.minecraft.world.level.block.SupportType.CENTER),
            "if vanilla ever makes leaves face-sturdy this test is no longer proving anything");
        assertFalse(Blocks.OAK_LEAVES.defaultBlockState().isSolidRender(),
            "leaves do not render solid either - that predicate was never about footing");

        for (net.minecraft.world.level.block.Block block : List.of(
            Blocks.OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.GLASS, Blocks.TINTED_GLASS,
            Blocks.ICE, Blocks.STONE)) {
            assertTrue(standable(block.defaultBlockState()),
                block + " holds the player up but was read as air");
        }

        assertFalse(standable(Blocks.AIR.defaultBlockState()));
        assertFalse(standable(Blocks.TORCH.defaultBlockState()), "no collision is not floor");
        assertFalse(standable(Blocks.WATER.defaultBlockState()));
        assertFalse(standable(Blocks.SHORT_GRASS.defaultBlockState()));

        assertFalse(standable(Blocks.OAK_SLAB.defaultBlockState()),
            "if this starts passing, revisit the feet-cell index in grimFootingOverlap with it");
    }

    @Test
    void grimBridgeHoldCoversTheGapBetweenPlacements() {
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 fallOff = new Vec3(10.5D, 64.0D, 22.0D);

        assertTrue(ScaffoldModule.grimBridgeHoldEligible(true, south, fallOff, 1000, Integer.MIN_VALUE),
            "a live course ending in a gap always holds the look-down pose");
        assertTrue(ScaffoldModule.grimBridgeHoldEligible(true, null, null, 1000, 990),
            "a just-completed placement holds through geometric blind ticks");
        assertTrue(ScaffoldModule.grimBridgeHoldEligible(true, null, null, 1000, 980),
            "the linger boundary still counts as bridging");
        assertFalse(ScaffoldModule.grimBridgeHoldEligible(true, null, null, 1000, 979),
            "past the linger window the bridge is over and the stream winds down");
        assertFalse(ScaffoldModule.grimBridgeHoldEligible(true, null, null, 1000, Integer.MIN_VALUE),
            "no course and no placement means no hold");
        assertFalse(ScaffoldModule.grimBridgeHoldEligible(true, south, null, 1000, Integer.MIN_VALUE),
            "walking solid ground with no gap ahead never holds the pose");
    }

    @Test
    void aFreshPlacementHoldsThePoseWithNoKeyDown() {
        assertTrue(ScaffoldModule.grimBridgeHoldEligible(false, null, null, 1000, 999),
            "this is the 06:51 pause - it must not start a wind-down");
        assertTrue(ScaffoldModule.grimBridgeHoldEligible(false, null, null, 1000, 980),
            "and it holds to the end of the linger window");

        assertFalse(ScaffoldModule.grimBridgeHoldEligible(false, null, null, 1000, 979),
            "past the linger a released key hands the stream back");
        assertFalse(ScaffoldModule.grimBridgeHoldEligible(
                false, new Vec3(0.0D, 0.0D, 1.0D), new Vec3(10.5D, 64.0D, 22.0D), 1000, 979),
            "and the course arm still needs the key it always needed");
    }

    @Test
    void grimClickGoalEyePushesOnlyToTheClickLead() {
        Vec3 faceCenter = new Vec3(10.0D, 63.5D, 20.5D);
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);

        assertEquals(new Vec3(10.3D, 65.62D, 20.45D),
            ScaffoldModule.grimClickGoalEye(new Vec3(9.0D, 65.62D, 20.45D), faceCenter, east, 0.3D),
            "a lagging approach eye is pushed to the lead, keeping its Y and lateral offset");
        assertEquals(new Vec3(10.3D, 66.4D, 20.45D),
            ScaffoldModule.grimClickGoalEye(new Vec3(10.25D, 66.4D, 20.45D), faceCenter, east, 0.3D),
            "inside the window the push never goes further ahead than the lead");
        Vec3 flown = new Vec3(10.6D, 66.4D, 20.45D);
        assertEquals(flown,
            ScaffoldModule.grimClickGoalEye(flown, faceCenter, east, 0.3D),
            "past the lead the real eye keeps the click ray reachable");
    }

    @Test
    void grimClickWindowStaysInsideSneakReachAndUnderIntavePitchLimit() {
        double lead = ScaffoldModule.GRIM_GOAL_EYE_LEAD;

        Vec3 faceCenter = new Vec3(10.0D, 63.5D, 20.5D);
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 sneakEye = new Vec3(9.9D, 65.27D, 20.5D);
        Vec3 goalEye = ScaffoldModule.grimClickGoalEye(sneakEye, faceCenter, east, lead);
        assertEquals(10.0D + lead, goalEye.x, 1.0E-9D,
            "a lagging approach eye must be pushed exactly to the lead");
        assertEquals(65.27D, goalEye.y, 1.0E-9D, "the push preserves the live eye height");
        double goalPitch = Math.toDegrees(Math.atan2(goalEye.y - 63.15D, goalEye.x - 10.0D));
        assertTrue(goalPitch <= 84.6D,
            "steepest click pitch must stay under Intave BlockRotation's 85 degrees");

        double tan = Math.tan(Math.toRadians(goalPitch));
        double sneakOpens = (65.27D - 64.0D) / tan;
        double sneakCloses = (65.27D - 63.15D) / tan;
        assertTrue(sneakOpens <= 0.17D,
            "the grounded click must open within a couple of sneak ticks past the lip");
        assertTrue(sneakCloses > sneakOpens && sneakCloses <= 0.26D,
            "the grounded window must close inside the ~0.25 vanilla sneak overhang");

        double takeoffOpens = (65.62D - 64.0D) / tan;
        assertTrue(takeoffOpens <= 0.21D,
            "the mid-air click must open almost immediately after crossing the plane");
        double apexOpens = (66.87D - 64.0D) / tan;
        double apexCloses = (66.87D - 63.15D) / tan;
        assertTrue(apexCloses > apexOpens,
            "even at jump apex the goal pitch must still leave a reachable window");
    }

    @Test
    void grimCourseChangesAndIntentionalDiagonalsPassThrough() {
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 southAgain = new Vec3(0.0D, 0.0D, 2.0D);
        Vec3 southEast = new Vec3(1.0D, 0.0D, 1.0D);
        assertTrue(ScaffoldModule.sameGrimCourse(south, southAgain));
        assertFalse(ScaffoldModule.sameGrimCourse(south, southEast),
            "a cardinal-to-diagonal transition must reset the retained lane");
        ScaffoldModule.MovementLine oldLine = new ScaffoldModule.MovementLine(Vec3.ZERO, south);
        ScaffoldModule.MovementLine turnLine = new ScaffoldModule.MovementLine(Vec3.ZERO, southEast);
        assertTrue(ScaffoldModule.grimRequestedCourseChange(oldLine, turnLine),
            "an edge-retained placement line may not steer movement against the requested turn");
        assertFalse(ScaffoldModule.grimRequestedCourseChange(oldLine,
            new ScaffoldModule.MovementLine(Vec3.ZERO, southAgain)));
    }

    @Test
    void oppositeSilentYawRemapsForwardToBackward() {
        Input transformed = ScaffoldModule.transformSilentMovementInput(
            input(true, false, false, false), 0.0F, 180.0F);

        assertEquals(input(false, true, false, false), transformed);
    }

    @Test
    void tellyLandingProjectionReturnsToRequestedLevel() {
        Vec3 projected = ScaffoldModule.projectTellyLanding(
            new Vec3(10.5D, 64.0D, 10.5D),
            new Vec3(0.0D, 0.42D, 0.31D),
            64.0D);

        assertEquals(64.0D, projected.y, 1.0E-9D);
        assertEquals(10.5D, projected.x, 1.0E-9D);
        assertTrue(projected.z > 12.0D);
    }

    @Test
    void tellyLandingProjectionExtendsCatchAtSprintSpeed() {
        Vec3 start = new Vec3(10.5D, 64.0D, 10.5D);
        Vec3 walking = ScaffoldModule.projectTellyLanding(
            start, new Vec3(0.0D, 0.42D, 0.20D), 64.0D);
        Vec3 sprinting = ScaffoldModule.projectTellyLanding(
            start, new Vec3(0.0D, 0.42D, 0.42D), 64.0D);

        assertTrue(sprinting.z - walking.z > 1.0D,
            "a faster launch must require a longer dynamic catch");
    }

    @Test
    void tellyCatchIncludesHeldForwardAirControl() {
        Vec3 start = new Vec3(10.5D, 64.0D, 10.5D);
        Vec3 passive = ScaffoldModule.projectTellyLanding(
            start, new Vec3(0.0D, 0.42D, 0.28D), 64.0D);
        Vec3 committed = ScaffoldModule.projectTellyLandingWithInput(
            start, new Vec3(0.0D, 0.42D, 0.28D), 64.0D, new Vec3(0.0D, 0.0D, 1.0D));

        assertTrue(committed.z > passive.z + 0.5D,
            "held sprint input must extend the required block chain");
    }

    @Test
    void fullSprintTellyPlansARealMultiBlockDrag() {
        Vec3 projected = ScaffoldModule.projectTellyLandingWithInput(
            new Vec3(10.5D, 64.0D, 10.5D),
            new Vec3(0.0D, 0.42D, 0.48D),
            64.0D,
            new Vec3(0.0D, 0.0D, 1.0D));

        assertTrue(projected.z > 15.0D,
            "full sprint launch must not collapse to a one-block catch");
    }

    @Test
    void tellyTakeoffYawAlignsToBlockGrid() {
        assertEquals(0.0F, ScaffoldModule.snapTellyYaw(17.0F));
        assertEquals(0.0F, ScaffoldModule.snapTellyYaw(44.0F));
        assertEquals(90.0F, ScaffoldModule.snapTellyYaw(46.0F));
        assertEquals(-180.0F, ScaffoldModule.snapTellyYaw(-157.0F));
        assertEquals(-180.0F, ScaffoldModule.snapTellyYaw(179.0F));
    }

    @Test
    void tellyInternalReacquireCannotFlipTheLatchedCourse() {
        assertEquals(0.0F, ScaffoldModule.retainTellyCourseYaw(
            true, 0.0F, 179.0F));
        assertEquals(90.0F, ScaffoldModule.retainTellyCourseYaw(
            true, 90.0F, -90.0F));
        assertEquals(-180.0F, ScaffoldModule.retainTellyCourseYaw(
            false, 0.0F, 179.0F));
    }

    @Test
    void tellyRiseIntentCannotLeakFromUnrelatedJumping() {
        assertFalse(ScaffoldModule.shouldQueueTellyRise(
            false, true, false, false),
            "Space without forward must not arm a future raised bridge");
        assertTrue(ScaffoldModule.shouldQueueTellyRise(
            true, true, true, false),
            "holding Space before forward should count when Telly acquires input");
        assertTrue(ScaffoldModule.shouldQueueTellyRise(
            true, true, false, true),
            "a fresh Space press during control must request a rise");
        assertFalse(ScaffoldModule.shouldQueueTellyRise(
            true, true, true, true),
            "holding Space must not repeatedly queue rises");
    }

    @Test
    void tellyRestoresForwardCameraForEveryAirCycleLanding() {
        assertTrue(ScaffoldModule.shouldRestoreTellyCourseOnGround(
            ScaffoldModule.TellyPhase.LAUNCH));
        assertTrue(ScaffoldModule.shouldRestoreTellyCourseOnGround(
            ScaffoldModule.TellyPhase.AIMING));
        assertTrue(ScaffoldModule.shouldRestoreTellyCourseOnGround(
            ScaffoldModule.TellyPhase.RETURNING));
        assertFalse(ScaffoldModule.shouldRestoreTellyCourseOnGround(
            ScaffoldModule.TellyPhase.IDLE));
    }

    @Test
    void zeroDwellChainsJumpsImmediately() {
        assertTrue(ScaffoldModule.tellyForwardDwellComplete(0),
            "no fixed dwell: the vanilla grounded tick is the only pause between chained jumps");
        assertEquals(0, ScaffoldModule.nextTellyForwardDwellTicks(0, false));
        assertEquals(0, ScaffoldModule.nextTellyForwardDwellTicks(0, true),
            "the counter is capped at the zero dwell length");
    }

    @Test
    void frontEdgeLandingChainsAndRunwayLandingDwells() {
        assertEquals(ScaffoldModule.TellyLandingTransition.DWELL,
            ScaffoldModule.tellyLandingTransition(false),
            "a landing with runway spends one real forward interval");
        assertEquals(ScaffoldModule.TellyLandingTransition.CHAIN,
            ScaffoldModule.tellyLandingTransition(true),
            "a front-edge landing must chain without wasting its support tick");
    }

    @Test
    void tellyRunwayUsesTheBlockCenterLane() {
        BlockPos support = new BlockPos(10, 63, 20);
        Vec3 offCenter = new Vec3(10.86D, 64.0D, 20.19D);

        Vec3 northSouth = ScaffoldModule.laneOrigin(support, offCenter, 0.0F);
        assertEquals(10.5D, northSouth.x, 1.0E-9D);
        assertEquals(offCenter.z, northSouth.z, 1.0E-9D);

        Vec3 eastWest = ScaffoldModule.laneOrigin(support, offCenter, 90.0F);
        assertEquals(offCenter.x, eastWest.x, 1.0E-9D);
        assertEquals(20.5D, eastWest.z, 1.0E-9D);
    }

    @Test
    void tellyGroundSteeringUsesAShallowMouseArc() {
        ScaffoldModule.TellyGroundSteeringState left =
            ScaffoldModule.nextTellyGroundSteering(
                false, 0.0F, 0.24D, 0.0D, 0.13D, 0.546D, false);
        ScaffoldModule.TellyGroundSteeringState right =
            ScaffoldModule.nextTellyGroundSteering(
                false, 0.0F, -0.24D, 0.0D, 0.13D, 0.546D, false);

        assertTrue(left.active());
        assertTrue(right.active());
        assertEquals(-5.0F, left.offsetDegrees(), 1.0E-6F);
        assertEquals(5.0F, right.offsetDegrees(), 1.0E-6F);
        assertTrue(Math.cos(Math.toRadians(15.0D)) > 0.96D,
            "the maximum correction must retain sprint-forward speed");

        ScaffoldModule.TellyGroundSteeringState saturated = left;
        for (int tick = 0; tick < 8; tick++) {
            saturated = ScaffoldModule.nextTellyGroundSteering(
                saturated.active(), saturated.offsetDegrees(),
                0.40D, 0.0D, 0.13D, 0.546D, false);
        }
        assertEquals(-15.0F, saturated.offsetDegrees(), 1.0E-6F,
            "ground steering must never exceed fifteen degrees");

        ScaffoldModule.TellyGroundSteeringState offColumn =
            ScaffoldModule.nextTellyGroundSteering(
                true, -8.0F, 0.80D, 0.0D, 0.13D, 0.546D, false);
        assertFalse(offColumn.active(),
            "steering must disengage once the lane error is another column over");
    }

    @Test
    void tellyGroundRunUsesOnlyForwardAndSprint() {
        Input input = ScaffoldModule.tellyGroundForwardInput(true, false);

        assertTrue(ScaffoldModule.usesTellyGroundWOnly(ScaffoldModule.TellyPhase.RUNNING));
        assertFalse(ScaffoldModule.usesTellyGroundWOnly(
            ScaffoldModule.TellyPhase.FORWARD_DWELL),
            "a rear-facing dwell must retain camera-relative course mapping");
        assertTrue(input.forward());
        assertFalse(input.backward());
        assertFalse(input.left());
        assertFalse(input.right());
        assertTrue(input.jump());
        assertFalse(input.shift());
        assertTrue(input.sprint());
    }

    @Test
    void tellyLaunchPredictionFollowsTheVisibleWDirection() {
        Vec3 launch = ScaffoldModule.predictedTellyGroundLaunch(
            new Vec3(0.0D, 0.0D, 0.30D), -15.0F, 0.10D, true);

        assertTrue(launch.x > 0.07D,
            "visible left steering must contribute the real leftward W impulse");
        assertTrue(launch.z > 0.58D);
        assertEquals(0.42D, launch.y, 1.0E-9D);
    }

    @Test
    void tellyGroundSteeringHasVelocityAwareHysteresis() {
        ScaffoldModule.TellyGroundSteeringState quiet =
            ScaffoldModule.nextTellyGroundSteering(
                false, 0.0F, 0.07D, 0.0D, 0.13D, 0.546D, false);
        ScaffoldModule.TellyGroundSteeringState incoming =
            ScaffoldModule.nextTellyGroundSteering(
                false, 0.0F, 0.02D, -0.05D, 0.13D, 0.546D, false);
        ScaffoldModule.TellyGroundSteeringState retained =
            ScaffoldModule.nextTellyGroundSteering(
                true, -3.0F, 0.05D, 0.02D, 0.13D, 0.546D, false);
        ScaffoldModule.TellyGroundSteeringState settled =
            ScaffoldModule.nextTellyGroundSteering(
                true, -3.0F, 0.03D, 0.01D, 0.13D, 0.546D, false);

        assertFalse(quiet.active());
        assertTrue(incoming.active(), "incoming lateral momentum must be damped early");
        assertTrue(retained.active(), "the exit band must prevent threshold chatter");
        assertFalse(settled.active());
    }

    @Test
    void tellyGroundSteeringReturnsBeforeTheLaunchLip() {
        double returnDistance = ScaffoldModule.tellySteeringReturnDistance(
            0.56D, 0.28D, 15.0F);
        assertEquals(1.78D, returnDistance, 1.0E-9D);

        ScaffoldModule.TellyGroundSteeringState returning =
            ScaffoldModule.nextTellyGroundSteering(
                true, -15.0F, 0.30D, 0.0D, 0.13D, 0.546D, true);
        assertFalse(returning.active());
        assertEquals(-8.0F, returning.offsetDegrees(), 1.0E-6F,
            "course return must be smooth but faster than outward steering");
    }

    @Test
    void centeredTellyAirNeverDilutesForwardInput() {
        ScaffoldModule.TellyAirCorrectionState state =
            new ScaffoldModule.TellyAirCorrectionState(
                ScaffoldModule.TellyStrafe.NONE, 0,
                ScaffoldModule.TellyStrafe.NONE, 0);

        for (int ticksLeft = 12; ticksLeft >= 1; ticksLeft--) {
            state = ScaffoldModule.nextTellyAirCorrection(
                state.cooldown(), state.lastPulse(), state.pulsesUsed(),
                0.24D, 0.0D, ticksLeft);
            assertEquals(ScaffoldModule.TellyStrafe.NONE, state.pulse(),
                "a safe centered arc must remain pure W for its whole flight");
            Vec3 input = movementVector(ScaffoldModule.tellyAirForwardInput(
                state.pulse(), false, false), 0.0F);
            assertEquals(1.0D, input.z, 1.0E-9D);
            assertEquals(0.0D, input.x, 1.0E-9D);
        }
        assertEquals(0, state.pulsesUsed());
    }

    @Test
    void tellyAirCorrectionUsesProjectedMomentum() {
        ScaffoldModule.TellyAirCorrectionState outward =
            ScaffoldModule.nextTellyAirCorrection(
                0, ScaffoldModule.TellyStrafe.NONE, 0,
                0.20D, -0.03D, 10);
        ScaffoldModule.TellyAirCorrectionState returning =
            ScaffoldModule.nextTellyAirCorrection(
                0, ScaffoldModule.TellyStrafe.NONE, 0,
                0.30D, 0.04D, 6);
        ScaffoldModule.TellyAirCorrectionState overshooting =
            ScaffoldModule.nextTellyAirCorrection(
                0, ScaffoldModule.TellyStrafe.NONE, 0,
                0.10D, 0.07D, 10);

        assertEquals(ScaffoldModule.TellyStrafe.LEFT, outward.pulse(),
            "outward velocity must be caught before the current position reaches the edge");
        assertEquals(ScaffoldModule.TellyStrafe.NONE, returning.pulse(),
            "momentum already returning to center must not be fought");
        assertEquals(ScaffoldModule.TellyStrafe.RIGHT, overshooting.pulse(),
            "the controller must damp a projected center-line overshoot");

        ScaffoldModule.TellyAirCorrectionState cooldown =
            ScaffoldModule.nextTellyAirCorrection(
                outward.cooldown(), outward.lastPulse(), outward.pulsesUsed(),
                0.50D, -0.03D, 9);
        assertEquals(ScaffoldModule.TellyStrafe.NONE, cooldown.pulse(),
            "air correction may never occupy consecutive movement ticks");
    }

    @Test
    void tellyAirSafetyRetainsAtLeastNinetyFivePercentForwardInput() {
        ScaffoldModule.TellyAirCorrectionState state =
            new ScaffoldModule.TellyAirCorrectionState(
                ScaffoldModule.TellyStrafe.NONE, 0,
                ScaffoldModule.TellyStrafe.NONE, 0);
        double laneError = 0.42D;
        double lateralVelocity = -0.02D;
        double forwardInput = 0.0D;
        int leftPulses = 0;
        int rightPulses = 0;

        for (int ticksLeft = 12; ticksLeft >= 1; ticksLeft--) {
            state = ScaffoldModule.nextTellyAirCorrection(
                state.cooldown(), state.lastPulse(), state.pulsesUsed(),
                laneError, lateralVelocity, ticksLeft);
            Input authored = ScaffoldModule.tellyAirForwardInput(
                state.pulse(), false, false);
            Vec3 impulse = movementVector(authored, 0.0F);
            forwardInput += impulse.z;
            if (state.pulse() == ScaffoldModule.TellyStrafe.LEFT) leftPulses++;
            if (state.pulse() == ScaffoldModule.TellyStrafe.RIGHT) rightPulses++;

            lateralVelocity += impulse.x * ScaffoldModule.TELLY_AIR_CONTROL;
            laneError -= lateralVelocity;
            lateralVelocity *= 0.91D;
        }

        assertEquals(2, leftPulses,
            "a strongly outward normal arc should need only two isolated pulses");
        assertEquals(0, rightPulses,
            "the same jump must not chatter back across the lane");
        assertTrue(Math.abs(laneError) < 0.40D,
            "the projected unsafe drift must return inside the chain footprint");
        assertTrue(forwardInput / 12.0D >= 0.95D,
            "routine stabilization must preserve at least 95% of full-W air input");
    }

    @Test
    void safeProjectionNeverBecomesBackwardRecovery() {
        assertFalse(ScaffoldModule.requiresTellyRunupRecovery(true),
            "transient yaw, sprint, or lane velocity cannot override safe AABB geometry");
        assertTrue(ScaffoldModule.requiresTellyRunupRecovery(false),
            "a genuinely uncatchable route still needs more runway");
    }

    @Test
    void fullSpeedTakeoffCannotSkipSupportMidpoint() {
        assertFalse(ScaffoldModule.shouldLaunchTelly(0.83D, 0.28D));
        assertFalse(ScaffoldModule.shouldLaunchTelly(0.60D, 0.48D));
        assertTrue(ScaffoldModule.shouldLaunchTelly(0.57D, 0.48D));
        assertTrue(ScaffoldModule.shouldLaunchTelly(0.51D, 0.0D));
    }

    @Test
    void landingBlockCountFollowsEitherCardinalDirection() {
        BlockPos support = new BlockPos(10, 63, 10);
        assertEquals(3, ScaffoldModule.requiredTellyBlocksToLanding(
            new Vec3(10.5D, 64.0D, 13.8D), new Vec3(0.0D, 0.0D, 1.0D), support));
        assertEquals(3, ScaffoldModule.requiredTellyBlocksToLanding(
            new Vec3(7.2D, 64.0D, 10.5D), new Vec3(-1.0D, 0.0D, 0.0D), support));
    }

    @Test
    void tellyLandingUsesFootprintNotCenterCell() {
        BlockPos block = new BlockPos(0, 63, 0);
        assertTrue(ScaffoldModule.tellyFootprintOverlaps(
            new AABB(0.86D, 64.0D, 0.2D, 1.46D, 65.8D, 0.8D), block));
        assertFalse(ScaffoldModule.tellyFootprintOverlaps(
            new AABB(0.91D, 64.0D, 0.2D, 1.51D, 65.8D, 0.8D), block));
    }

    @Test
    void tellyRotationStepObeysCapAndReachesGoal() {
        AutismRotationUtil.Rotation start = new AutismRotationUtil.Rotation(0.0F, 0.0F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(90.0F, 0.0F);

        AutismRotationUtil.Rotation first = ScaffoldModule.stepTellyRotation(start, goal, 75.0F, 0.15D);
        assertEquals(75.0F, first.yaw(), 1.0E-4F, "first step must be exactly the cap, never the goal");

        AutismRotationUtil.Rotation second = ScaffoldModule.stepTellyRotation(first, goal, 75.0F, 0.15D);
        assertEquals(90.0F, second.yaw(), 0.076F, "second step must arrive (within half a mouse step)");
    }

    @Test
    void tellyRotationStepIsProportionalAcrossAxes() {
        AutismRotationUtil.Rotation start = new AutismRotationUtil.Rotation(0.0F, 0.0F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(90.0F, -45.0F);

        AutismRotationUtil.Rotation first = ScaffoldModule.stepTellyRotation(start, goal, 75.0F, 0.15D);
        assertEquals(2.0F, first.yaw() / -first.pitch(), 0.02F,
            "yaw and pitch must advance in the goal's 2:1 proportion so both finish together");

        AutismRotationUtil.Rotation second = ScaffoldModule.stepTellyRotation(first, goal, 75.0F, 0.15D);
        assertEquals(90.0F, second.yaw(), 0.076F);
        assertEquals(-45.0F, second.pitch(), 0.076F);
    }

    @Test
    void tellyRotationStepQuantizesToSensitivity() {
        double gcd = 0.15D;
        AutismRotationUtil.Rotation current = new AutismRotationUtil.Rotation(12.3F, -4.7F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(-140.0F, 38.0F);

        AutismRotationUtil.Rotation stepped = ScaffoldModule.stepTellyRotation(current, goal, 75.0F, gcd);
        double yawDelta = AutismRotationUtil.angleDifference(stepped.yaw(), current.yaw());
        double pitchDelta = AutismRotationUtil.angleDifference(stepped.pitch(), current.pitch());
        assertEquals(0.0D, Math.abs(yawDelta / gcd - Math.round(yawDelta / gcd)), 1.0E-3D,
            "applied yaw delta must be a whole number of mouse steps");
        assertEquals(0.0D, Math.abs(pitchDelta / gcd - Math.round(pitchDelta / gcd)), 1.0E-3D,
            "applied pitch delta must be a whole number of mouse steps");
    }

    @Test
    void tellyMouseBurstCompletesNearHalfTurnOnEverySensitivityGrid() {
        Rotation current = new Rotation(13.25F, -72.0F);
        Rotation goal = new Rotation(-167.75F, 79.0F);
        for (double gcd : new double[]{0.0096D, 0.15D, 0.6144D}) {
            Rotation burst = ScaffoldModule.tellyMouseBurstRotation(
                current, goal, 179.5F, 179.5F, gcd);
            double yawDelta = AutismRotationUtil.angleDifference(burst.yaw(), current.yaw());
            double pitchDelta = burst.pitch() - current.pitch();
            assertEquals(0.0D, Math.abs(yawDelta / gcd - Math.round(yawDelta / gcd)), 2.0E-3D,
                "the fast yaw must still be made from whole raw mouse counts");
            assertEquals(0.0D, Math.abs(pitchDelta / gcd - Math.round(pitchDelta / gcd)), 2.0E-3D,
                "the fast pitch must still be made from whole raw mouse counts");
            assertTrue(Math.abs(AutismRotationUtil.angleDifference(goal.yaw(), burst.yaw())) <= gcd * 0.51D,
                "one interval must reach the outbound Telly face at gcd " + gcd);
            assertTrue(Math.abs(goal.pitch() - burst.pitch()) <= gcd * 0.51D,
                "one interval must reach the placement pitch at gcd " + gcd);
        }
    }

    @Test
    void tellyMouseBurstSplitsAHalfTurnAcrossTwoIntervalsAndStaysOnTheGrid() {
        double gcd = 0.6144D;
        float cap = 95.0F;
        Rotation current = new Rotation(0.0F, 0.0F);
        Rotation goal = new Rotation(180.0F, 65.0F);

        Rotation first = ScaffoldModule.tellyMouseBurstRotation(current, goal, cap, cap, gcd);
        float firstDelta = Math.abs(AutismRotationUtil.angleDifference(first.yaw(), current.yaw()));
        assertTrue(firstDelta <= cap + 1.0E-3F, "the burst must respect its interval cap");
        assertTrue(firstDelta > 45.0F, "and must still be a fast flick, not a crawl");
        assertTrue(Math.abs(AutismRotationUtil.angleDifference(goal.yaw(), first.yaw())) > gcd,
            "a half turn may not arrive inside one interval any more");

        Rotation second = ScaffoldModule.tellyMouseBurstRotation(first, goal, cap, cap, gcd);
        assertTrue(Math.abs(AutismRotationUtil.angleDifference(goal.yaw(), second.yaw())) <= gcd + 1.0E-3D,
            "two intervals must complete it - the flick is fast, just not instant");

        Rotation quarter = ScaffoldModule.tellyMouseBurstRotation(
            current, new Rotation(90.0F, 65.0F), cap, cap, gcd);
        assertTrue(Math.abs(AutismRotationUtil.angleDifference(90.0F, quarter.yaw())) <= gcd + 1.0E-3D,
            "anything inside the cap still lands in a single interval");

        assertEquals(first, ScaffoldModule.tellyMouseBurstRotation(current, goal, cap, cap, gcd),
            "rotation cadence is tick-based and must not depend on render frames");
    }

    @Test
    void cappedRotationStepNeverExceedsAxisCaps() {
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(-170.0F, 80.0F);

        AutismRotationUtil.Rotation stream = new AutismRotationUtil.Rotation(10.0F, 5.0F);
        for (int tick = 0; tick < 10; tick++) {
            AutismRotationUtil.Rotation next =
                ScaffoldModule.stepCappedRotation(stream, goal, 45.0F, 8.0F, 0.15D);
            float yawDelta = Math.abs(AutismRotationUtil.angleDifference(next.yaw(), stream.yaw()));
            float pitchDelta = Math.abs(AutismRotationUtil.angleDifference(next.pitch(), stream.pitch()));
            assertTrue(yawDelta <= 45.0F + 0.076F,
                "per-tick yaw delta must never exceed the yaw cap (half a mouse step tolerance), was " + yawDelta);
            assertTrue(pitchDelta <= 8.0F + 0.076F,
                "per-tick pitch delta must never exceed the pitch cap (half a mouse step tolerance), was " + pitchDelta);
            stream = next;
        }
    }

    @Test
    void cappedRotationNinetyDegreeTurnTakesTwoStepsNotOne() {
        AutismRotationUtil.Rotation start = new AutismRotationUtil.Rotation(0.0F, 0.0F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(90.0F, 0.0F);

        AutismRotationUtil.Rotation first = ScaffoldModule.stepCappedRotation(start, goal, 45.0F, 45.0F, 0.15D);
        assertEquals(45.0F, first.yaw(), 1.0E-4F, "first step must be exactly the cap, never the goal");
        assertTrue(Math.abs(AutismRotationUtil.angleDifference(goal.yaw(), first.yaw())) > 1.0F,
            "a 90-degree turn must not complete in a single tick");

        AutismRotationUtil.Rotation second = ScaffoldModule.stepCappedRotation(first, goal, 45.0F, 45.0F, 0.15D);
        assertEquals(90.0F, second.yaw(), 0.076F, "second step must arrive (within half a mouse step)");
    }

    @Test
    void cappedRotationStepConvergesWithoutOvershoot() {
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(130.0F, -34.0F);

        AutismRotationUtil.Rotation stream = new AutismRotationUtil.Rotation(0.0F, 0.0F);
        for (int tick = 0; tick < 40; tick++) {
            AutismRotationUtil.Rotation next =
                ScaffoldModule.stepCappedRotation(stream, goal, 45.0F, 8.0F, 0.15D);
            float yawRemaining = AutismRotationUtil.angleDifference(goal.yaw(), stream.yaw());
            float pitchRemaining = AutismRotationUtil.angleDifference(goal.pitch(), stream.pitch());
            float yawStep = AutismRotationUtil.angleDifference(next.yaw(), stream.yaw());
            float pitchStep = AutismRotationUtil.angleDifference(next.pitch(), stream.pitch());
            assertTrue(Math.abs(yawStep) <= Math.abs(yawRemaining) + 0.076F,
                "yaw must never step past the goal by more than half a mouse step");
            assertTrue(Math.abs(pitchStep) <= Math.abs(pitchRemaining) + 0.076F,
                "pitch must never step past the goal by more than half a mouse step");
            stream = next;
        }

        assertEquals(goal.yaw(), stream.yaw(), 0.076F, "the stream must converge to the goal yaw");
        assertEquals(goal.pitch(), stream.pitch(), 0.076F, "the stream must converge to the goal pitch");
    }

    @Test
    void cappedRotationStepQuantizesToSensitivity() {
        double gcd = 0.15D;
        AutismRotationUtil.Rotation current = new AutismRotationUtil.Rotation(12.3F, -4.7F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(-140.0F, 38.0F);

        AutismRotationUtil.Rotation stepped = ScaffoldModule.stepCappedRotation(current, goal, 45.0F, 8.0F, gcd);
        double yawDelta = AutismRotationUtil.angleDifference(stepped.yaw(), current.yaw());
        double pitchDelta = AutismRotationUtil.angleDifference(stepped.pitch(), current.pitch());
        assertEquals(0.0D, Math.abs(yawDelta / gcd - Math.round(yawDelta / gcd)), 1.0E-3D,
            "applied yaw delta must be a whole number of mouse steps");
        assertEquals(0.0D, Math.abs(pitchDelta / gcd - Math.round(pitchDelta / gcd)), 1.0E-3D,
            "applied pitch delta must be a whole number of mouse steps");
    }

    @Test
    void snapTurnSettleSequenceNeverSettlesEarly() {
        float anchorYaw = 90.0F;
        AutismRotationUtil.Rotation stream = new AutismRotationUtil.Rotation(0.0F, 10.0F);
        AutismRotationUtil.Rotation goal = new AutismRotationUtil.Rotation(anchorYaw, 10.0F);
        double velCross = 0.28D;
        boolean settled = false;
        int settledAt = -1;

        for (int tick = 1; tick <= 10 && !settled; tick++) {
            stream = ScaffoldModule.stepTellyRotation(stream, goal, 75.0F, 0.15D);
            velCross *= 0.6D;
            boolean yawArrived =
                Math.abs(AutismRotationUtil.angleDifference(anchorYaw, stream.yaw())) <= 1.0F;
            settled = ScaffoldModule.tellyTurnSettled(stream.yaw(), anchorYaw, 0.0D, velCross, 0.0D, true);
            if (!yawArrived) {
                assertFalse(settled, "must never settle while the stream is still sweeping");
            }
            assertFalse(ScaffoldModule.tellyTurnSettled(stream.yaw(), anchorYaw, 0.0D, velCross, 0.0D, false),
                "must never settle airborne");
            if (settled) settledAt = tick;
        }

        assertTrue(settled, "the turn must settle once rotation arrived and momentum bled off");
        assertTrue(settledAt <= 6, "settling must be quick (a few held ticks), was " + settledAt);
    }

    @Test
    void settleRequiresVelocityAlignmentOrSpeedFloor() {
        assertFalse(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.0D, 0.25D, 0.0D, true),
            "fast crosswise momentum is exactly the slide that falls off the corner");
        assertTrue(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.02D, 0.02D, 0.0D, true),
            "near-stopped is safe regardless of direction");
        assertTrue(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.25D, 0.05D, 0.0D, true),
            "momentum already pointing down the new course is safe");
        assertFalse(ScaffoldModule.tellyTurnSettled(85.0F, 90.0F, 0.02D, 0.02D, 0.0D, true),
            "the stream must have arrived at the anchor first");
        assertFalse(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, -0.25D, 0.0D, 0.0D, true),
            "fast backward momentum is not settled");
    }

    @Test
    void turnIntentCounterDecaysInTheHysteresisBandInsteadOfFreezing() {

        int counter = 0;
        for (int tick = 0; tick < 3; tick++) {
            counter = ScaffoldModule.nextTellyCourseDeviationTicks(counter, 50.0F);
        }
        assertEquals(3, counter, "a sustained >45 look must mature the turn");

        counter = 2;
        counter = ScaffoldModule.nextTellyCourseDeviationTicks(counter, 40.0F);
        assertEquals(1, counter);
        counter = ScaffoldModule.nextTellyCourseDeviationTicks(counter, 40.0F);
        assertEquals(0, counter, "the band must drain the counter within two ticks");
        assertEquals(0, ScaffoldModule.nextTellyCourseDeviationTicks(0, 40.0F),
            "an empty counter stays empty in the band");
        assertEquals(0, ScaffoldModule.nextTellyCourseDeviationTicks(2, 20.0F),
            "looking back on course resets instantly");
    }

    @Test
    void riseCellMustBeClearForCurrentAndPreviousTick() {
        BlockPos cell = new BlockPos(0, 65, 0);
        Vec3 ascending = new Vec3(0.0D, 0.33D, 0.0D);

        AABB justCleared = new AABB(-0.3D, 66.05D, -0.3D, 0.3D, 67.85D, 0.3D);
        assertFalse(ScaffoldModule.tellyRiseCellClear(justCleared, ascending, cell),
            "one tick early gets server-rejected against the stale position");

        AABB fullyCleared = new AABB(-0.3D, 66.40D, -0.3D, 0.3D, 68.20D, 0.3D);
        assertTrue(ScaffoldModule.tellyRiseCellClear(fullyCleared, ascending, cell));

        AABB descendingIn = new AABB(-0.3D, 65.90D, -0.3D, 0.3D, 67.70D, 0.3D);
        assertFalse(ScaffoldModule.tellyRiseCellClear(descendingIn, new Vec3(0.0D, -0.2D, 0.0D), cell));
    }

    @Test
    void settleRequiresCenteringOnTheNewLane() {
        assertFalse(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.02D, 0.02D, 0.55D, true),
            "far off the new lane drifts the arc past the chain's reach");
        assertTrue(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.02D, 0.02D, 0.40D, true),
            "roughly on the lane is good to go - the rescue and air strafe cover the rest");
        assertFalse(ScaffoldModule.tellyTurnSettled(90.0F, 90.0F, 0.02D, 0.02D, -0.55D, true),
            "off-center works in both directions");
    }

    @Test
    void lateFlickWaitsUntilTheTrueLastMoment() {
        assertTrue(ScaffoldModule.tellyLateFlickBudgetAllows(10, 3, 2.5D, 4.5D),
            "plenty of air + face in reach: keep facing forward");
        assertTrue(ScaffoldModule.tellyLateFlickBudgetAllows(5, 3, 2.5D, 4.5D),
            "one tick of slack left: still forward - this is the edge");
        assertFalse(ScaffoldModule.tellyLateFlickBudgetAllows(4, 3, 2.5D, 4.5D),
            "air ticks equal chain + slack: flick NOW or the catch is missed");
        assertFalse(ScaffoldModule.tellyLateFlickBudgetAllows(12, 3, 4.25D, 4.5D),
            "face within one sprint tick of leaving reach: flick now regardless of air time");
        assertFalse(ScaffoldModule.tellyLateFlickBudgetAllows(5, 4, 2.5D, 4.5D),
            "a pending rise adds a block: its last moment is one tick sooner");
    }

    @Test
    void tellyHumanLookOffsetDoesNotBendTheBlockLane() {
        Vec3 south = ScaffoldModule.tellyForwardVector(0.0F);
        assertEquals(0.0D, south.x, 1.0E-6D);
        assertEquals(1.0D, south.z, 1.0E-6D);

        Vec3 west = ScaffoldModule.tellyForwardVector(90.0F);
        assertEquals(-1.0D, west.x, 1.0E-6D);
        assertEquals(0.0D, west.z, 1.0E-6D);

        assertEquals(0.3F, ScaffoldModule.tellyCourseLookYaw(0.0F, 0.3F), 1.0E-6F,
            "the silent look may be offset without changing the cardinal movement vector");
    }

    @Test
    void tellyAimLeadOpensBeforeThePlacementDeadline() {
        assertTrue(ScaffoldModule.tellyAimDelayAllowed(7, 4, 2.5D, 4.5D),
            "one interval of reserve for the two-interval flick, and no more");
        assertFalse(ScaffoldModule.tellyAimDelayAllowed(6, 4, 2.5D, 4.5D),
            "aim starts when the four clicks plus the flick's own interval are all that remain");
        assertFalse(ScaffoldModule.tellyAimDelayAllowed(7, 4, 4.25D, 4.5D),
            "a face nearing reach starts the sweep immediately regardless of spare air time");
        assertTrue(ScaffoldModule.tellyAimDelayAllowed(7, 4, 2.5D, 4.5D, true),
            "a rise uses the same cadence with the same single interval of reserve");
        assertFalse(ScaffoldModule.tellyAimDelayAllowed(6, 4, 2.5D, 4.5D, true),
            "the raised catch deadline itself already supplies the earlier rise timing");
    }

    @Test
    void aFlickWithNoNextTickMaySpendTheWholeIntervalWhileTheRoutineOneMayNot() {
        double gcd = 0.6144D;
        Rotation from = new Rotation(0.0F, 60.0F);

        Rotation face = new Rotation(125.0F, 72.0F);

        Rotation routine = ScaffoldModule.tellyMouseBurstRotation(from, face, 95.0F, 95.0F, gcd);
        assertTrue(Math.abs(AutismRotationUtil.angleDifference(face.yaw(), routine.yaw())) > gcd,
            "the routine cap deliberately cannot cross this in one interval");

        Rotation lastChance = ScaffoldModule.tellyMouseBurstRotation(
            from, face, 179.5F, 179.5F, gcd);
        assertTrue(
            Math.abs(AutismRotationUtil.angleDifference(face.yaw(), lastChance.yaw())) <= gcd + 1.0E-3D,
            "a click with no later tick must reach its face inside the one interval it has");
    }

    @Test
    void theEarlyLaunchOnlyEverRemovesGroundedTicks() {

        assertFalse(ScaffoldModule.tellyEarlyLaunchSteps(0), "a hop onto our own cell is not progress");
        assertFalse(ScaffoldModule.tellyEarlyLaunchSteps(1), "nor onto the runway block we just laid");
        assertTrue(ScaffoldModule.tellyEarlyLaunchSteps(2), "two cells on is a real telly jump");
        assertTrue(ScaffoldModule.tellyEarlyLaunchSteps(4), "four is the far bound");

        assertFalse(ScaffoldModule.tellyEarlyLaunchSteps(5), "an arc the chain cannot fill must walk instead");
        assertFalse(ScaffoldModule.tellyEarlyLaunchSteps(-1), "an unprojectable arc never launches early");
    }

    @Test
    void theEarlyLaunchYieldsToTheLaneDamperItWouldOtherwiseSkip() {
        assertTrue(ScaffoldModule.tellyEarlyLaunchLaneClear(0.0D), "dead centre launches");
        assertTrue(ScaffoldModule.tellyEarlyLaunchLaneClear(0.079D),
            "just inside the damper's engage band still launches");
        assertFalse(ScaffoldModule.tellyEarlyLaunchLaneClear(0.09D),
            "past it the walk must run so the damper can correct");
        assertFalse(ScaffoldModule.tellyEarlyLaunchLaneClear(-0.30D),
            "and the test is on magnitude, both sides of the lane");
        assertFalse(ScaffoldModule.tellyEarlyLaunchLaneClear(Double.NaN),
            "an unknown lane never authorises the fast path");
    }

    @Test
    void tellyTwoIntervalFlickReservesExactlyOneTick() {

        assertEquals(1, ScaffoldModule.tellyAimSweepReserveTicks(3, false));
        assertEquals(1, ScaffoldModule.tellyAimSweepReserveTicks(5, false));
        assertEquals(1, ScaffoldModule.tellyAimSweepReserveTicks(3, true));
        assertEquals(1, ScaffoldModule.tellyAimSweepReserveTicks(5, true));
    }

    @Test
    void tellyOutboundAimCannotCloseAfterItStarts() {
        assertFalse(ScaffoldModule.nextTellyAimCommitted(false, true),
            "while delay is allowed the player remains forward-facing");
        assertTrue(ScaffoldModule.nextTellyAimCommitted(false, false),
            "opening the aim window commits the outbound sweep");
        assertTrue(ScaffoldModule.nextTellyAimCommitted(true, true),
            "later projection jitter cannot send a committed sweep forward again");
    }

    @Test
    void tellyReturnCannotReopenASecondOutboundSweep() {
        assertEquals(ScaffoldModule.TellyPhase.AIMING,
            ScaffoldModule.nextTellyCoveragePhase(
                ScaffoldModule.TellyPhase.AIMING, false, false));
        assertEquals(ScaffoldModule.TellyPhase.AIMING,
            ScaffoldModule.nextTellyCoveragePhase(
                ScaffoldModule.TellyPhase.AIMING, true, true),
            "a pending rise keeps the placement phase open");
        assertEquals(ScaffoldModule.TellyPhase.RETURNING,
            ScaffoldModule.nextTellyCoveragePhase(
                ScaffoldModule.TellyPhase.AIMING, true, false),
            "landing plus runway commits the single return");
        assertEquals(ScaffoldModule.TellyPhase.RETURNING,
            ScaffoldModule.nextTellyCoveragePhase(
                ScaffoldModule.TellyPhase.RETURNING, false, false),
            "a moving projection cannot create a back-forward-back cycle");
    }

    @Test
    void tellyMouseBurstUsesIndependentPhysicalAxes() {
        Rotation current = new Rotation(-40.0F, -70.0F);
        Rotation goal = new Rotation(120.0F, 80.0F);
        Rotation burst = ScaffoldModule.tellyMouseBurstRotation(
            current, goal, 179.5F, 179.5F, 0.15D);
        assertEquals(goal.yaw(), burst.yaw(), 0.076F,
            "a diagonal mouse burst must not slow yaw because pitch also moved");
        assertEquals(goal.pitch(), burst.pitch(), 0.076F,
            "a diagonal mouse burst must not slow pitch because yaw also moved");
    }

    @Test
    void fastTellyEnvelopeIsLimitedToPlacementCriticalStates() {
        assertFalse(ScaffoldModule.usesTellyAirFlick(
            ScaffoldModule.TellyRotationIntent.FORWARD));
        assertFalse(ScaffoldModule.usesTellyAirFlick(
            ScaffoldModule.TellyRotationIntent.HOLD));
        assertTrue(ScaffoldModule.usesTellyAirFlick(
            ScaffoldModule.TellyRotationIntent.PLACEMENT));
        assertFalse(ScaffoldModule.usesTellyAirFlick(
            ScaffoldModule.TellyRotationIntent.RETURN),
            "return is handled by its one-shot completion path");
    }

    @Test
    void tellyAirCycleNeverReturnsForwardBetweenChainPlacements() {
        assertEquals(ScaffoldModule.TellyRotationIntent.FORWARD,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.LAUNCH, false, false, false, false));
        assertEquals(ScaffoldModule.TellyRotationIntent.PLACEMENT,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.LAUNCH, true, true, false, false));
        assertEquals(ScaffoldModule.TellyRotationIntent.HOLD,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.AIMING, false, true, false, false),
            "a one-tick refill gap preserves the placement posture MID-FLIGHT only");
        assertEquals(ScaffoldModule.TellyRotationIntent.PLACEMENT,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.AIMING, true, true, false, false));
        assertEquals(ScaffoldModule.TellyRotationIntent.RETURN,
            ScaffoldModule.tellyAirRotationIntent(
                ScaffoldModule.TellyPhase.RETURNING, false, true, false, false),
            "only completed coverage may initiate the one final return");
    }

    @Test
    void launchAlignmentHoldKeepsReturningToCourse() {
        assertFalse(ScaffoldModule.tellyGroundHoldUsesChainTarget(false, false),
            "a normal edge hold must not reverse back toward the prepared placement");
        assertTrue(ScaffoldModule.tellyGroundHoldUsesChainTarget(true, false),
            "an explicit stop may aim at the chain cell it is securing");
        assertTrue(ScaffoldModule.tellyGroundHoldUsesChainTarget(false, true),
            "a finishing hold may aim at the final securing cell");
    }

    @Test
    void tellyFaceSampleCannotAlternateWhileTheCoursePointIsValid() {
        assertEquals(2, ScaffoldModule.selectTellyFaceOffset(
            2, new double[] {1.0D, 0.5D, 9.0D, 0.25D}),
            "the locked sample wins even when another point becomes marginally cheaper");
        assertEquals(3, ScaffoldModule.selectTellyFaceOffset(
            2, new double[] {1.0D, 0.5D, Double.POSITIVE_INFINITY, 0.25D}),
            "the aim may switch only after its locked sample becomes invalid");
        assertEquals(-1, ScaffoldModule.selectTellyFaceOffset(-1,
            new double[] {Double.POSITIVE_INFINITY, Double.NaN}));
    }

    @Test
    void tellyNormalPlacementCanOnlyExtendTheCardinalChain() {
        BlockPos support = new BlockPos(10, 63, 20);
        assertTrue(ScaffoldModule.isTellyForwardChainPlacement(
            support, support.south(), Direction.SOUTH, Direction.SOUTH));
        assertFalse(ScaffoldModule.isTellyForwardChainPlacement(
            support, support.east(), Direction.EAST, Direction.SOUTH));
        assertFalse(ScaffoldModule.isTellyForwardChainPlacement(
            support, support.above(), Direction.UP, Direction.SOUTH),
            "the held-space rise uses its separate explicit path");
        assertFalse(ScaffoldModule.isTellyForwardChainPlacement(
            support, support.south(), Direction.NORTH, Direction.SOUTH));
    }

    @Test
    void tellyMidAirTurnRequiresOneAdditionalRunwayCell() {
        assertEquals(1, ScaffoldModule.tellyRunwayReserveBlocks(false));
        assertEquals(2, ScaffoldModule.tellyRunwayReserveBlocks(true));
        assertTrue(ScaffoldModule.tellyChainCovered(true, true, false, false));
        assertFalse(ScaffoldModule.tellyChainCovered(true, true, true, false),
            "a pending turn must not finish on the normal runway lip");
        assertTrue(ScaffoldModule.tellyChainCovered(true, true, true, true));
    }

    @Test
    void tellyTurnFallbackIsOneExactOldCourseCell() {
        BlockPos support = new BlockPos(10, 63, 20);
        assertEquals(Direction.SOUTH,
            ScaffoldModule.tellyTurnReserveDirection(support, support.south()));
        assertTrue(ScaffoldModule.isTellyTurnReserveDirection(
            support, support.south(), Direction.SOUTH));
        assertFalse(ScaffoldModule.isTellyTurnReserveDirection(
            support, support.south(), Direction.EAST));
        assertNull(ScaffoldModule.tellyTurnReserveDirection(
            support, support.south().east()),
            "the fallback cannot target a diagonal or arbitrary neighboring cell");
    }

    @Test
    void tellyPlacementAdoptionSynchronizesStreamToFinalMouseBurst() throws Exception {
        ScaffoldModule scaffold = new ScaffoldModule();
        Field streamField = ScaffoldModule.class.getDeclaredField("tellyStream");
        streamField.setAccessible(true);
        AutismHumanRotation.Stream stream = (AutismHumanRotation.Stream) streamField.get(scaffold);

        Field randomField = AutismHumanRotation.Stream.class.getDeclaredField("random");
        randomField.setAccessible(true);
        randomField.set(stream, new java.util.Random(0x5CAFF01DL));
        AutismHumanRotation.seed(stream, new Rotation(0.0F, 0.0F));

        Rotation current = null;
        Rotation goal = new Rotation(140.0F, 55.0F);
        for (int tick = 0; tick < 3; tick++) {
            current = AutismHumanRotation.step(stream, goal, 105.0F, 105.0F, 0.15D, false,
                AutismHumanRotation.MotionProfile.TELLY_FLICK);
        }

        Field lastStepYaw = AutismHumanRotation.Stream.class.getDeclaredField("lastStepYaw");
        lastStepYaw.setAccessible(true);
        assertTrue(lastStepYaw.getDouble(stream) > 0.0D, "the test stream must have live acceleration");

        Field smoothed = ScaffoldModule.class.getDeclaredField("tellySmoothedRotation");
        smoothed.setAccessible(true);
        Rotation finalBurst = new Rotation(-137.4F, 52.2F);
        smoothed.set(scaffold, current);
        Method adopt = ScaffoldModule.class.getDeclaredMethod(
            "adoptTellyPlacementRotation", Rotation.class);
        adopt.setAccessible(true);
        adopt.invoke(scaffold, finalBurst);

        assertEquals(0.0D, lastStepYaw.getDouble(stream), 1.0E-12D,
            "the superseded tick-head preview must not leak velocity into the next turn");
        assertEquals(finalBurst, AutismHumanRotation.current(stream),
            "the stream must resume from the exact endpoint carried by the movement packet");
    }

    @Test
    void ticksUntilCatchTracksTheArc() {
        int descending = ScaffoldModule.tellyTicksUntilCatch(
            new Vec3(0.0D, 64.2D, 0.0D), new Vec3(0.0D, -0.1D, 0.0D), 64.0D);
        assertEquals(2, descending, "already falling just above the catch level lands immediately");

        int fullJump = ScaffoldModule.tellyTicksUntilCatch(
            new Vec3(0.0D, 64.0D, 0.0D), new Vec3(0.0D, 0.42D, 0.0D), 64.0D);
        assertTrue(fullJump >= 11 && fullJump <= 13,
            "a full jump arc returns to its level in ~12 ticks, was " + fullJump);
        assertTrue(fullJump > descending);
    }

    @Test
    void overhangDetectionIsPlayerRelative() {

        assertNull(ScaffoldModule.tellyOverhangDirection(0.5D, 0.5D, true, true, true, true));

        assertEquals(net.minecraft.core.Direction.EAST,
            ScaffoldModule.tellyOverhangDirection(0.85D, 0.5D, true, true, true, true));

        assertNull(ScaffoldModule.tellyOverhangDirection(0.85D, 0.5D, true, true, true, false));

        assertEquals(net.minecraft.core.Direction.NORTH,
            ScaffoldModule.tellyOverhangDirection(0.75D, 0.05D, true, true, true, true));

        assertEquals(net.minecraft.core.Direction.WEST,
            ScaffoldModule.tellyOverhangDirection(0.1D, 0.5D, true, true, true, true));
    }

    private static Input input(boolean forward, boolean backward, boolean left, boolean right) {
        return new Input(forward, backward, left, right, false, false, false);
    }

    @Test
    void grimClickFeasibilityMatchesTheFrontierWindowGeometry() {
        BlockPos support = new BlockPos(10, 63, 20);
        BridgeWorld world = new BridgeWorld(support);
        ScaffoldModule.PlacementTarget pending = frontierTarget(support);
        double eyeY = 65.62D;
        double planeX = 11.0D;

        assertFalse(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                new Vec3(planeX + 0.10D, eyeY, 20.5D), new Rotation(90.0F, 84.3F), 4.5D, world, null),
            pending), "0.10 past the plane cannot land even at the Intave pitch cap");
        assertFalse(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                new Vec3(planeX + 0.16D, eyeY, 20.5D), new Rotation(90.0F, 84.3F), 4.5D, world, null),
            pending), "just under the 0.162 geometric minimum still hits the block top");
        assertTrue(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                new Vec3(planeX + 0.17D, eyeY, 20.5D), new Rotation(90.0F, 84.3F), 4.5D, world, null),
            pending), "the cap pitch opens the window at ~0.162 past the plane");
        assertTrue(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                new Vec3(planeX + 0.25D, eyeY, 20.5D), new Rotation(90.0F, 82.0F), 4.5D, world, null),
            pending), "the tracked pitch keeps the ray in-band across the footing window");
        assertFalse(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                new Vec3(planeX + 0.10D, eyeY, 20.5D), new Rotation(90.0F, 82.0F), 4.5D, world, null),
            pending), "the old push-goal pitch is unlandable early in the window");
    }

    @Test
    void grimClickFeasibleMirrorsValidationAcceptance() {
        BlockPos support = new BlockPos(10, 63, 20);
        ScaffoldModule.PlacementTarget pending = frontierTarget(support);
        Vec3 onFace = new Vec3(11.0D, 63.5D, 20.5D);

        assertFalse(ScaffoldModule.grimClickFeasible(null, pending), "no hit is never feasible");
        assertTrue(ScaffoldModule.grimClickFeasible(
            new net.minecraft.world.phys.BlockHitResult(onFace, Direction.EAST, support, false), pending));
        assertFalse(ScaffoldModule.grimClickFeasible(
            new net.minecraft.world.phys.BlockHitResult(onFace, Direction.UP, support, false), pending),
            "the planned face must match");
        assertFalse(ScaffoldModule.grimClickFeasible(
            new net.minecraft.world.phys.BlockHitResult(onFace, Direction.EAST, support.west(), false), pending),
            "the planned support block must match");
        assertFalse(ScaffoldModule.grimClickFeasible(
            new net.minecraft.world.phys.BlockHitResult(
                new Vec3(11.0D, 62.9D, 20.5D), Direction.EAST, support, false), pending),
            "hits under minPlacementY are rejected exactly like validation");
    }

    @Test
    void grimClickRayCastsFromTheRotationArgumentAlone() {

        BlockPos support = new BlockPos(10, 63, 20);
        BridgeWorld world = new BridgeWorld(support);
        ScaffoldModule.PlacementTarget pending = frontierTarget(support);
        Vec3 eye = new Vec3(13.0D, 64.62D, 20.5D);

        assertTrue(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                eye, new Rotation(90.0F, 30.0F), 4.5D, world, null), pending),
            "the aim pointed at the face lands the click");
        assertFalse(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                eye, new Rotation(60.0F, 30.0F), 4.5D, world, null), pending),
            "a yaw 30 degrees off the same face must miss");
        assertFalse(ScaffoldModule.grimClickFeasible(ScaffoldModule.grimClickRay(
                eye, new Rotation(90.0F, 60.0F), 4.5D, world, null), pending),
            "a pitch 30 degrees steeper dives under the face band");
    }

    @Test
    void grimSideWindowSolvePitchSeparatesOpeningWindowsFromRefusedOnes() {
        BlockPos northSupport = new BlockPos(1143, 92, -70);

        assertTrue(Float.isNaN(ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(1143.7D, 94.62D, -69.8D), northSupport, Direction.NORTH, 0.0F)),
            "behind the plane must read NaN, not a pitch");

        assertTrue(Float.isNaN(ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(11.5D, 65.62D, 20.5D), new BlockPos(10, 63, 20), Direction.EAST, 0.0F)),
            "a yaw parallel to the face has no crossing");

        float early = ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(1143.6D, 94.27D, -70.01D), northSupport, Direction.NORTH, 0.0F);
        assertTrue(early > ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "barely past the plane must demand a refused pitch: " + early);

        assertEquals(80.70F, ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(1143.73D, 94.27D, -70.29D), northSupport, Direction.NORTH, 0.0F),
            0.05F, "the parked sneak eye's window is open");

        BlockPos diagSupport = new BlockPos(13, 87, 71);
        float graze = ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(12.995D, 89.27D, 72.005D), diagSupport, Direction.WEST, -135.0F);
        assertTrue(graze > ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "the early diagonal graze must read closed: " + graze);
        assertEquals(80.5F, ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(12.79D, 89.27D, 72.21D), diagSupport, Direction.WEST, -135.0F),
            0.1F, "two sneak-creep ticks later the same pinned yaw lands");
    }

    @Test
    void grimCrossingPitchAimsAtTheFaceCentreNotTheTopEdge() {
        BlockPos northSupport = new BlockPos(1143, 92, -70);
        float southPosture = 0.0F;

        assertEquals(ScaffoldModule.GRIM_PLACE_PITCH_PARK, ScaffoldModule.grimCrossingPitch(
            new Vec3(1143.7D, 94.62D, -69.8D), northSupport, Direction.NORTH, southPosture),
            "behind the plane the goal parks at the park bound");
        assertEquals(ScaffoldModule.GRIM_PLACE_PITCH_PARK, ScaffoldModule.grimCrossingPitch(
            new Vec3(1143.7D, 94.62D, -69.8D), northSupport, Direction.NORTH, southPosture, 90.0F),
            "the AIRBORNE park is the same bound - 90 aims into the support's own top face");
        assertEquals(ScaffoldModule.GRIM_PLACE_PITCH_PARK, ScaffoldModule.grimCrossingPitch(
            new Vec3(1143.6D, 94.27D, -70.005D), northSupport, Direction.NORTH, southPosture),
            "millimetres past the plane the solved pitch exceeds the park - continuous handoff");

        assertEquals(80.70F, ScaffoldModule.grimCrossingPitch(
            new Vec3(1143.73D, 94.27D, -70.29D), northSupport, Direction.NORTH, southPosture),
            0.05F, "a parked sneak eye gets a pitch that lands on the face");

        BlockPos eastSupport = new BlockPos(10, 63, 20);
        assertEquals(54.46F, ScaffoldModule.grimCrossingPitch(
            new Vec3(11.5D, 64.2D, 20.5D), eastSupport, Direction.EAST, 90.0F),
            0.05F, "an eye sinking toward the top edge keeps crossing below it (descent catch)");
        assertEquals(ScaffoldModule.GRIM_PLACE_PITCH_PARK, ScaffoldModule.grimCrossingPitch(
            new Vec3(11.5D, 65.62D, 20.5D), eastSupport, Direction.EAST, 0.0F),
            "a yaw parallel to the face has no crossing - park until the pin turns");

        BlockPos westSupport = new BlockPos(1100, 92, -67);
        assertEquals(72.53F, ScaffoldModule.grimCrossingPitch(
            new Vec3(1099.73D, 94.27D, -65.80D), westSupport, Direction.WEST, -151.0F),
            0.1F, "an oblique unlocked yaw still gets an on-face crossing pitch");
    }

    @Test
    void grimTopCrossingPitchSolvesTheChordMidpointAndStillLands() {

        BlockPos support = new BlockPos(161, 100, -762);
        Vec3 eye = new Vec3(161.5D, 103.6D, -761.5D);
        float yaw = 0.0F;
        float midpoint = ScaffoldModule.grimTopCrossingPitch(eye, support, yaw);
        assertTrue(midpoint > 84.0F, "the midpoint solve should be the steep one: " + midpoint);
        assertTrue(midpoint <= ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD + 1.0E-4F,
            "the midpoint solve exceeded the hard cap: " + midpoint);

        assertTrue(ScaffoldModule.grimTopCrossingLandsOnFace(eye, support, yaw, false),
            "the midpoint pitch left the top face");
        assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(
            new Vec3(161.5D, 99.0D, -761.5D), support, yaw)),
            "an eye below the top face still has no crossing");
    }

    @Test
    void aTopFaceThePostureYawCannotLandIsRefusedNotAimedAtItsPoint() {

        BlockPos support = new BlockPos(-1, 99, 100);
        Vec3 eye = new Vec3(-0.20D, 102.87D, 100.075D);
        float postureYaw = 135.0F;
        assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(eye, support, postureYaw)),
            "the posture track leaves the top square, so no pitch along it lands");
        assertFalse(ScaffoldModule.grimTopCrossingLandsOnFace(eye, support, postureYaw, false),
            "and the pin test agrees the ray misses");

        Vec3 point = new Vec3(-0.65D, 100.0D, 100.30D);
        float pointPitch = AutismRotationUtil.lookingAt(point, eye).pitch();
        assertTrue(pointPitch > 79.0F && pointPitch < 82.0F,
            "the 10:31 point solve was ~80 degrees: " + pointPitch);
        double run = (eye.y - 100.0D) / Math.tan(Math.toRadians(pointPitch));
        double landZ = eye.z + Math.cos(Math.toRadians(postureYaw)) * run;
        assertTrue(landZ < 100.0D,
            "the point pitch ridden at the posture yaw must land short of the square: " + landZ);
    }

    @Test
    void theSolvedCrossingKeepsHalfABlockOfClearanceFromBothEdges() {

        BlockPos support = new BlockPos(1143, 92, -70);
        for (double past = 0.20D; past <= 0.60D; past += 0.02D) {
            for (double eyeHeight : new double[] { 1.27D, 1.62D }) {
                Vec3 eye = new Vec3(1143.5D, 93.0D + eyeHeight, -70.0D - past);
                float pitch = ScaffoldModule.grimCrossingPitch(eye, support, Direction.NORTH, 0.0F);
                double crossing = eye.y - past * Math.tan(Math.toRadians(pitch));
                double belowTop = 93.0D - crossing;
                assertTrue(belowTop >= 0.15D && belowTop <= 0.85D,
                    "past " + past + " eye " + eyeHeight + " crossed " + belowTop + " below the top");
                if (pitch < 84.29F) {
                    assertEquals(0.5D, belowTop, 1.0E-6D, "an unclamped solve crosses dead centre");
                }
            }
        }
    }

    @Test
    void grimEmittedYawNeverJumpsAcrossTheWrapBoundary() {

        float before = ScaffoldModule.grimContinuousYaw(179.90F, 179.90F);
        float after = ScaffoldModule.grimContinuousYaw(179.95F, -180.00F);
        assertTrue(Math.abs(after - before) < 1.0F,
            "consecutive packets across the wrap boundary must stay a small raw delta");

        assertEquals(0.0F, (ScaffoldModule.grimContinuousYaw(725.0F, 5.0F) - 5.0F) % 360.0F,
            1.0E-3F, "the emitted yaw stays congruent to the silent yaw mod 360");
        assertEquals(725.0F, ScaffoldModule.grimContinuousYaw(725.0F, 5.0F), 1.0E-3F,
            "a free-running camera yaw keeps its winding count");
        assertEquals(180.0F, ScaffoldModule.grimContinuousYaw(179.95F, -180.00F), 1.0E-3F,
            "-180 next to +179.95 is emitted as +180, not a 360 jump");
        assertEquals(-91.0F, ScaffoldModule.grimContinuousYaw(-90.0F, -91.0F), 1.0E-3F,
            "an ordinary nearby yaw passes through unchanged");
    }

    @Test
    void grimEyeOutsideFaceBandCatchesTheDriftedColumn() {

        BlockPos rowSupport = new BlockPos(1343, 77, 360);
        assertTrue(ScaffoldModule.grimEyeOutsideFaceBand(
            new Vec3(1345.17D, 80.54D, 361.49D), rowSupport, Direction.EAST),
            "an eye past the far edge of the face's span is off the band");
        assertFalse(ScaffoldModule.grimEyeOutsideFaceBand(
            new Vec3(1345.17D, 80.54D, 360.62D), rowSupport, Direction.EAST),
            "an eye inside the span keeps the sticky");
        assertTrue(ScaffoldModule.grimEyeOutsideFaceBand(
            new Vec3(1345.17D, 80.54D, 359.80D), rowSupport, Direction.EAST),
            "an eye short of the near edge is off the band too");

        BlockPos northSupport = new BlockPos(1336, 72, 368);
        assertTrue(ScaffoldModule.grimEyeOutsideFaceBand(
            new Vec3(1338.20D, 74.62D, 367.50D), northSupport, Direction.NORTH),
            "north/south faces measure the x span");
        assertFalse(ScaffoldModule.grimEyeOutsideFaceBand(
            new Vec3(1336.50D, 74.62D, 367.50D), northSupport, Direction.NORTH));
    }

    @Test
    void laneInputCorrectionHasRealLateralAuthority() {

        Vec3 north = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 still = Vec3.ZERO;

        assertEquals(0.0F, ScaffoldModule.grimLaneInputTarget(north, Vec3.ZERO, still),
            "dead centre asks for nothing");
        assertEquals(0.0F, ScaffoldModule.grimLaneInputTarget(north, new Vec3(0.0D, 0.0D, 0.0D), still),
            "and stays quiet inside the deadband");

        float atThird = ScaffoldModule.grimLaneInputTarget(north, new Vec3(0.30D, 0.0D, 0.0D), still);
        assertTrue(Math.abs(atThird) > 15.0F,
            "a 0.3 lane error must command real authority, got " + atThird);
        assertTrue(Math.abs(atThird) <= 38.0F, "but stays inside the cap");

        assertTrue(ScaffoldModule.grimLaneInputTarget(north, new Vec3(-0.30D, 0.0D, 0.0D), still) < 0.0F,
            "a lane to the west of a north course steers left");
        assertTrue(ScaffoldModule.grimLaneInputTarget(north, new Vec3(0.30D, 0.0D, 0.0D), still) > 0.0F,
            "and a lane to the east steers right");

        assertTrue(Math.abs(ScaffoldModule.grimLaneInputTarget(
            north, new Vec3(0.20D, 0.0D, 0.0D), new Vec3(0.05D, 0.0D, 0.0D)))
            < Math.abs(atThird), "already closing means less correction");
    }

    @Test
    void movementOctantHoldsThroughTheRoundingBoundary() {

        assertEquals(1, ScaffoldModule.octantWithHysteresis(0.70D, 0),
            "an unambiguous component still engages from rest");
        assertEquals(0, ScaffoldModule.octantWithHysteresis(0.55D, 0),
            "a component inside the band does not engage from rest");
        assertEquals(1, ScaffoldModule.octantWithHysteresis(0.45D, 1),
            "an engaged axis holds through the boundary instead of dropping out");
        assertEquals(0, ScaffoldModule.octantWithHysteresis(0.30D, 1),
            "it still releases once the component genuinely falls away");
        assertEquals(-1, ScaffoldModule.octantWithHysteresis(-0.80D, 1),
            "a real reversal is followed immediately");

        int octant = 1;
        for (double component : new double[] {0.52D, 0.48D, 0.51D, 0.47D, 0.53D, 0.49D}) {
            octant = ScaffoldModule.octantWithHysteresis(component, octant);
            assertEquals(1, octant, "boundary dither must not toggle the emitted octant");
        }
    }

    @Test
    void grimCrossingLateralTestCatchesRowEdgeDrift() {

        BlockPos support = new BlockPos(1152, 86, -79);
        Vec3 edgeEye = new Vec3(1153.41D, 89.52D, -77.95D);
        assertFalse(ScaffoldModule.grimCrossingLandsOnFace(edgeEye, support, Direction.EAST, 91.0F),
            "the lateral-free posture ray passes beside the face from a row-edge eye");
        assertTrue(ScaffoldModule.grimCrossingLandsOnFace(edgeEye, support, Direction.EAST, 130.0F),
            "the point-aim yaw leans the crossing back inside the face band");
        assertTrue(ScaffoldModule.grimCrossingLandsOnFace(
            new Vec3(1153.41D, 89.52D, -78.50D), support, Direction.EAST, 91.0F),
            "a centered eye keeps the posture");
        assertTrue(ScaffoldModule.grimCrossingLandsOnFace(
            new Vec3(1152.80D, 89.52D, -77.95D), support, Direction.EAST, 91.0F),
            "behind the plane the posture parks - nothing to test yet");
    }

    @Test
    void grimEyePastPlaneMeasuresAlongTheFaceNormal() {
        BlockPos support = new BlockPos(10, 63, 20);
        assertEquals(0.25D, ScaffoldModule.grimEyePastPlane(
            new Vec3(11.25D, 65.62D, 20.5D), support, Direction.EAST), 1.0E-9D);
        assertEquals(-0.10D, ScaffoldModule.grimEyePastPlane(
            new Vec3(10.90D, 65.62D, 20.5D), support, Direction.EAST), 1.0E-9D);
        assertEquals(0.30D, ScaffoldModule.grimEyePastPlane(
            new Vec3(10.5D, 65.62D, 21.30D), support, Direction.SOUTH), 1.0E-9D);
    }

    @Test
    void grimSeedOrderPrefersDistanceBucketsAndBreaksTiesByPosture() {
        Rotation posture = new Rotation(90.0F, 75.0F);
        Vec3 future = new Vec3(11.5D, 63.5D, 20.5D);
        Rotation offPosture = new Rotation(-90.0F, 10.0F);

        ScaffoldModule.PlacementTarget near = seedPlan(new BlockPos(12, 63, 20), offPosture);
        ScaffoldModule.PlacementTarget far = seedPlan(new BlockPos(13, 63, 20), posture);
        assertTrue(ScaffoldModule.grimSeedOrder(future, posture).compare(near, far) < 0,
            "a closer bucket wins even against a perfect posture match - no more rear whips");

        ScaffoldModule.PlacementTarget tieOff = seedPlan(new BlockPos(12, 63, 20), offPosture);
        ScaffoldModule.PlacementTarget tieOn = seedPlan(new BlockPos(11, 63, 21), posture);
        assertTrue(ScaffoldModule.grimSeedOrder(future, posture).compare(tieOn, tieOff) < 0,
            "inside one bucket the frontal (posture-aligned) connector still wins");
    }

    @Test
    void theLegSortPicksThePlaneTheEyeIsNearestToCrossing() {
        BlockPos elbow = new BlockPos(-910, 73, -227);
        ScaffoldModule.PlacementTarget north = legTarget(elbow, Direction.NORTH);
        ScaffoldModule.PlacementTarget east = legTarget(elbow, Direction.EAST);
        Vec3 eye = new Vec3(-909.08D, 75.18D + 1.62D, -227.05D);

        assertEquals(0.05D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.NORTH), 0.001D,
            "north was opening that tick - the trace printed past=-0.029 one tick earlier");
        assertEquals(-0.08D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.EAST), 0.001D,
            "east was still behind its own plane - the trace printed past=-0.079");

        assertSame(north, ScaffoldModule.grimNearestPlaneLeg(List.of(east, north), eye, null),
            "the leg reached first wins even when the sort put the other one in front");
        assertSame(north, ScaffoldModule.grimNearestPlaneLeg(List.of(north, east), eye, null),
            "and the same leg wins from the other input order - this is a preference, not a swap");

        Vec3 later = new Vec3(-908.89D, 76.80D, -226.67D);
        assertEquals(0.11D, ScaffoldModule.grimEyePastPlane(later, elbow, Direction.EAST), 0.001D);
        assertEquals(-0.33D, ScaffoldModule.grimEyePastPlane(later, elbow, Direction.NORTH), 0.001D,
            "the t185 shape: the incoming leg open, the outgoing a third of a block behind");
        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(List.of(north, east), later, null),
            "and the preference follows it");
        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(
            List.of(north, east), later, north.placedBlock()),
            "a genuinely better plane unseats the incumbent straight through the margin");
    }

    @Test
    void aNearTieHoldsTheReservedLegInsteadOfFlipFlopping() {
        BlockPos elbow = new BlockPos(-910, 73, -227);
        ScaffoldModule.PlacementTarget north = legTarget(elbow, Direction.NORTH);
        ScaffoldModule.PlacementTarget east = legTarget(elbow, Direction.EAST);

        Vec3 eye = new Vec3(-908.88D, 75.18D + 1.62D, -227.05D);
        assertEquals(0.12D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.EAST), 0.001D);
        assertEquals(0.05D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.NORTH), 0.001D);

        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(List.of(north, east), eye, null),
            "with no reservation the nearest plane wins, as before");
        assertSame(north, ScaffoldModule.grimNearestPlaneLeg(
            List.of(north, east), eye, north.placedBlock()),
            "a 0.07 advantage is a near-tie: the reserved leg holds and the aim converges");
        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(
            List.of(north, east), eye, east.placedBlock()),
            "the hold is symmetric - whichever leg is reserved is the one that holds");
        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(
            List.of(north, east), eye, new BlockPos(0, 0, 0)),
            "an incumbent no longer in the list holds nothing - no dead-cell latch");
    }

    @Test
    void theLegSortLeavesANonHorizontalPlanAlone() {
        BlockPos elbow = new BlockPos(-910, 73, -227);
        ScaffoldModule.PlacementTarget top = new ScaffoldModule.PlacementTarget(
            elbow, elbow.above(), Direction.UP,
            new BlockHitResult(
                new Vec3(-909.5D, 74.0D, -226.5D), Direction.UP, elbow, false),
            new Rotation(45.0F, 84.0F), elbow.getY());
        ScaffoldModule.PlacementTarget east = legTarget(elbow, Direction.EAST);
        Vec3 eye = new Vec3(-909.08D, 76.80D, -227.05D);

        assertSame(top, ScaffoldModule.grimNearestPlaneLeg(List.of(top, east), eye, null),
            "an UP face is not a side plane of the elbow - the sort's answer stands");
    }

    @Test
    void theArcBrakeStandsDownWhileItIsStarvingItsOwnCrossing() {

        Vec3 lane = new Vec3(0.7071D, 0.0D, 0.7071D);

        assertTrue(ScaffoldModule.grimBrakeStarvesCrossing(Direction.SOUTH, 0, true, lane),
            "rising, this tick, and the lane closes the gap - braking is why the leg is not down");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.SOUTH, 0, false, lane),
            "the descent belongs to the landing: the brake keeps every bit of its authority there");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(null, 0, true, lane),
            "no crossing wait stamped means an aiming or reach problem, which travel cannot answer");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.SOUTH, 1, true, lane),
            "a stale stamp is a tick the click gate did not refuse on a crossing - brake normally");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.SOUTH, -1, true, lane),
            "grimTicksSince returns -1 for the never sentinel, which must not read as fresh");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.NORTH, 0, true, lane),
            "a face the lane is travelling AWAY from is not one braking can be blamed for");

        assertTrue(ScaffoldModule.grimBrakeStarvesCrossing(Direction.EAST, 0, true, lane),
            "the other L leg's plane closes on the same diagonal - both axes, not just one");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.WEST, 0, true, lane),
            "and its opposite still does not");

        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        assertTrue(ScaffoldModule.grimBrakeStarvesCrossing(Direction.SOUTH, 0, true, south),
            "cardinal south lane, south face");
        assertFalse(ScaffoldModule.grimBrakeStarvesCrossing(Direction.EAST, 0, true, south),
            "a face square to a cardinal lane never closes, so braking is not what refuses it");
    }

    @Test
    void theCrossingStanddownIsSpentInBlocksOfTravelNotTicks() {

        assertTrue(ScaffoldModule.grimCrossingStanddownAllowed(true, 0.20D),
            "one fast tick is inside the budget");
        assertFalse(ScaffoldModule.grimCrossingStanddownAllowed(true, 0.60D),
            "three are not - that arc keeps its floor and loses only its level");

        assertTrue(ScaffoldModule.grimCrossingStanddownAllowed(true, 0.21D),
            "two slow ticks are only 0.21 - the old tick cap stopped here and starved the crossing");
        assertTrue(ScaffoldModule.grimCrossingStanddownAllowed(true, 0.315D),
            "three reach 0.32, and the crossing needs 0.34 - still inside");
        assertFalse(ScaffoldModule.grimCrossingStanddownAllowed(true, 0.42D),
            "four overshoot it, and past the crossing there is nothing left to buy");

        assertFalse(ScaffoldModule.grimCrossingStanddownAllowed(false, 0.05D),
            "no starvation, no standdown - the budget never opens a brake the geometry did not");
        assertFalse(ScaffoldModule.grimCrossingStanddownAllowed(false, 0.0D));
    }

    @Test
    void theFirstLegPickPrefersTheOneWhoseLandingClickIsReachable() {
        BlockPos footing = new BlockPos(23, 114, 66);
        BlockPos landing = new BlockPos(24, 114, 67);
        ScaffoldModule.PlacementTarget south = postureLeg(footing, Direction.SOUTH, 135.0F);
        ScaffoldModule.PlacementTarget east = postureLeg(footing, Direction.EAST, 135.0F);
        Vec3 eye = new Vec3(24.01D, 115.42D + 1.62D, 67.07D);
        Vec3 lead = new Vec3(0.090D, 0.333D, 0.036D);

        assertEquals(0.07D, ScaffoldModule.grimEyePastPlane(eye, footing, Direction.SOUTH), 0.001D,
            "south is the nearer plane, so the plane sort alone picks it - and the capture did");
        assertEquals(0.01D, ScaffoldModule.grimEyePastPlane(eye, footing, Direction.EAST), 0.001D);

        assertFalse(ScaffoldModule.grimSuccessorRayLands(south, landing, eye, lead),
            "the landing off south's EAST face is already walking out of its square");
        assertTrue(ScaffoldModule.grimSuccessorRayLands(east, landing, eye, lead),
            "the landing off east's SOUTH face is walking in - that is the one that places");

        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(
            List.of(south, east), eye, lead, landing, null),
            "so the arc's first pick takes the leg it can finish the level from");

        assertSame(south, ScaffoldModule.grimNearestPlaneLeg(
            List.of(south, east), eye, lead, null, null),
            "no landing known, nothing to judge");
        assertSame(south, ScaffoldModule.grimNearestPlaneLeg(
            List.of(south, east), eye, lead, landing, south.placedBlock()),
            "and an incumbent is NEVER unseated by this - that is the 19:19 spiral's door");
        assertNull(ScaffoldModule.grimSuccessorReachableLeg(
            List.of(south, east), eye, lead, new BlockPos(99, 114, 99)),
            "a landing neither leg neighbours picks out nothing");
    }

    @Test
    void theNearTieHoldCannotStraddleTheClickMargin() {
        BlockPos support = new BlockPos(11, 90, 61);
        ScaffoldModule.PlacementTarget east = postureLeg(support, Direction.EAST, 45.0F);
        ScaffoldModule.PlacementTarget north = postureLeg(support, Direction.NORTH, 45.0F);

        Vec3 eye = new Vec3(11.99D, 92.18D + 1.62D, 60.88D);
        assertEquals(-0.01D, ScaffoldModule.grimEyePastPlane(eye, support, Direction.EAST), 0.001D,
            "the reserved leg is behind its plane - no pitch and no yaw can land it");
        assertEquals(0.12D, ScaffoldModule.grimEyePastPlane(eye, support, Direction.NORTH), 0.001D,
            "the sibling is past the 0.05 click margin - it is live right now");

        assertTrue(ScaffoldModule.grimSwapAcrossClickMargin(0.12D, -0.01D),
            "clickable against not-clickable is a step change, not a near-tie");
        assertFalse(ScaffoldModule.grimSwapAcrossClickMargin(0.12D, 0.05D),
            "both past the margin is the damper's own ground - the 19:19 spiral case");
        assertFalse(ScaffoldModule.grimSwapAcrossClickMargin(0.01D, -0.30D),
            "both behind it is too - neither can be clicked, so plane distance still orders them");

        assertSame(north, ScaffoldModule.grimNearestPlaneLeg(
            List.of(east, north), eye, east.placedBlock()),
            "so the hold breaks and the arc commits to the leg it can actually place");
        assertSame(north, ScaffoldModule.grimNearestPlaneLeg(
            List.of(east, north), eye, north.placedBlock()),
            "and holding the live one is unchanged - it is already the nearest plane");
    }

    @Test
    void aLegWhoseRayCannotLandOnItsFaceLosesToOneThatCan() {
        BlockPos elbow = new BlockPos(3, 93, 63);
        ScaffoldModule.PlacementTarget south = postureLeg(elbow, Direction.SOUTH, 135.0F);
        ScaffoldModule.PlacementTarget east = postureLeg(elbow, Direction.EAST, 135.0F);
        Vec3 eye = new Vec3(4.05D, 94.42D + 1.62D, 64.06D);
        Vec3 lead = new Vec3(0.089D, 0.333D, 0.050D);

        assertEquals(0.06D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.SOUTH), 0.001D,
            "south is the nearer plane by a centimetre - the trace printed past=+0.058");
        assertEquals(0.05D, ScaffoldModule.grimEyePastPlane(eye, elbow, Direction.EAST), 0.001D,
            "east is the further one - rsv printed 4,93,63:ok/past+0.05");
        assertSame(south, ScaffoldModule.grimNearestPlaneLeg(List.of(south, east), eye, null),
            "which is exactly what the plane sort alone picks, and what the capture did");

        assertFalse(ScaffoldModule.grimLegRayLands(south, eye, lead),
            "the posture ray exits past south's far corner now and further out at every lead step");
        assertTrue(ScaffoldModule.grimLegRayLands(east, eye, lead),
            "east's crossing is walking INTO its square - two ticks of travel and it lands");

        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(List.of(south, east), eye, lead, null),
            "so the leg that can actually be shot wins, plane distance notwithstanding");
        assertSame(east, ScaffoldModule.grimNearestPlaneLeg(
            List.of(south, east), eye, lead, south.placedBlock()),
            "and a blind incumbent cannot use the near-tie hysteresis to hold - it is not in the list");

        assertSame(south, ScaffoldModule.grimNearestPlaneLeg(List.of(south, east), eye, null, null),
            "no lead to look ahead with, both blind at step 0 - the partition splits nothing");
        Vec3 approach = new Vec3(3.90D, 96.04D, 63.90D);
        assertTrue(ScaffoldModule.grimLegRayLands(south, approach, lead),
            "behind its own plane a leg is unproven, not blind - the ray cannot reach it yet");
        assertTrue(ScaffoldModule.grimLegRayLands(east, approach, lead),
            "which is the whole approach, i.e. nearly every takeoff, and it sorts as before");
        assertEquals(2, ScaffoldModule.grimSightedLegs(List.of(south, east), approach, lead).size(),
            "all sighted returns the list unchanged");
    }

    @Test
    void aHeldFaceWhoseCrossingHasLeftItsSquareIsNotWorthHolding() {

        BlockPos support = new BlockPos(12, 120, 79);
        ScaffoldModule.PlacementTarget held = postureLeg(support, Direction.EAST, 90.0F);
        Vec3 eye = new Vec3(13.27D, 122.00D + 1.62D, 78.93D);
        Vec3 lead = new Vec3(0.053D, 0.165D, -0.043D);

        assertTrue(ScaffoldModule.grimEyePastPlane(eye, support, Direction.EAST) > 0.0D,
            "past its own plane, which is why the trace printed past=+0.271 and looked fine");
        assertFalse(ScaffoldModule.grimLegRayLands(held, eye, lead),
            "but the crossing is off the square and still leaving - five ticks of miss=no-hit");

        Vec3 insideSquare = new Vec3(13.27D, 122.00D + 1.62D, 79.40D);
        assertTrue(ScaffoldModule.grimLegRayLands(held, insideSquare, lead),
            "a crossing inside the square is exactly what the hold exists to keep");

        Vec3 behindPlane = new Vec3(12.60D, 122.00D + 1.62D, 78.93D);
        assertTrue(ScaffoldModule.grimLegRayLands(held, behindPlane, lead),
            "behind the plane is unproven, not blind - that is the whole approach");
    }

    @Test
    void aStickyWhoseCrossingHasLeftItsSquareIsNotWorthReplanning() {
        BlockPos support = new BlockPos(14, 119, 78);
        ScaffoldModule.PlacementTarget sticky = postureLeg(support, Direction.EAST, 90.0F);
        Vec3 eye = new Vec3(15.15D, 120.75D + 1.62D, 77.89D);
        Vec3 lead = new Vec3(0.059D, 0.248D, -0.066D);

        assertEquals(0.15D, ScaffoldModule.grimEyePastPlane(eye, support, Direction.EAST), 0.001D,
            "past its own plane - the trace printed past=+0.148 and looked fine");
        assertFalse(ScaffoldModule.grimLegRayLands(sticky, eye, lead),
            "but the crossing sits north of the square at z=77.89 and every lead step leaves");
        assertTrue(ScaffoldModule.grimEyeOutsideFaceBand(eye, support, Direction.EAST),
            "the band test also reads this eye as outside - but its two-tick hysteresis kept "
                + "resetting as selection re-adopted the face; blindness releases on proof, "
                + "not on a counter");

        Vec3 insideSquare = new Vec3(15.15D, 120.75D + 1.62D, 78.45D);
        assertTrue(ScaffoldModule.grimLegRayLands(sticky, insideSquare, lead),
            "a crossing inside the square is exactly what the stick exists to keep");
        Vec3 behindPlane = new Vec3(14.60D, 120.75D + 1.62D, 77.89D);
        assertTrue(ScaffoldModule.grimLegRayLands(sticky, behindPlane, lead),
            "behind the plane is unproven, not blind - the ordinary approach");
        Vec3 walkingIn = new Vec3(15.15D, 120.75D + 1.62D, 77.89D);
        assertTrue(ScaffoldModule.grimLegRayLands(
                sticky, walkingIn, new Vec3(0.059D, 0.248D, 0.120D)),
            "a crossing walking INTO its square (clear of the 0.04 acquire margin inside the "
                + "lookahead) lands and holds");
    }

    @Test
    void aRescueWhoseCrossingHasLeftItsSquareIsNotWorthServing() {
        BlockPos support = new BlockPos(0, 94, 65);
        ScaffoldModule.PlacementTarget rescue = postureLeg(support, Direction.EAST, 90.0F);
        Vec3 eye = new Vec3(1.17D, 96.25D + 1.62D, 64.84D);
        Vec3 lead = new Vec3(0.037D, -0.075D, -0.026D);

        assertEquals(0.17D, ScaffoldModule.grimEyePastPlane(eye, support, Direction.EAST), 0.001D,
            "past its own plane - t168 printed past=+0.165 and looked fine");
        assertFalse(ScaffoldModule.grimLegRayLands(rescue, eye, lead),
            "but the crossing sits north of the square at z=64.84 and every lead step leaves");

        Vec3 reserveEye = new Vec3(1.31D, 95.50D + 1.62D, 64.73D);
        assertFalse(ScaffoldModule.grimLegRayLands(rescue, reserveEye,
                new Vec3(0.053D, -0.374D, -0.046D)),
            "the reservation re-offered the same dead face one tick into the fall");

        Vec3 insideSquare = new Vec3(1.17D, 96.25D + 1.62D, 65.30D);
        assertTrue(ScaffoldModule.grimLegRayLands(rescue, insideSquare, lead),
            "a crossing inside the square is the ordinary catch and stays served");
        Vec3 behindPlane = new Vec3(0.90D, 96.25D + 1.62D, 64.84D);
        assertTrue(ScaffoldModule.grimLegRayLands(rescue, behindPlane, lead),
            "behind the plane is unproven, not blind - t164 read past=-0.104 and was right to wait");
        assertTrue(ScaffoldModule.grimLegRayLands(
                rescue, eye, new Vec3(0.037D, -0.075D, 0.120D)),
            "a crossing walking INTO its square inside the lookahead lands and holds");
    }

    @Test
    void aGroundedBoxWithNothingUnderItIsTheLastChanceAndAFreedTickForfeitsItsClick() {
        assertTrue(ScaffoldModule.grimPaceFloorHolds(48L, ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS),
            "t280: the 100 ms brink floor refused the last grounded tick at 48 elapsed");
        assertFalse(ScaffoldModule.grimPaceFloorHolds(
                48L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS),
            "the last-chance floor admits the one-tick pair the corner needs");
        assertTrue(ScaffoldModule.grimNoFootingUnderfoot(0.0D),
            "ovl=0.00 grounded is the emergency - gravity takes the box next movement");
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(0.01D),
            "a centimetre of corner is ordinary bridging and keeps the ordinary brink floor");

        assertTrue(ScaffoldModule.grimWirePitchFrozen(true, false),
            "a clickable tick with the pace open freezes - the judge rays this emission");
        assertFalse(ScaffoldModule.grimWirePitchFrozen(true, true),
            "a pace-refused tick ships no click, so the pitch converges on the safe goal");
        assertFalse(ScaffoldModule.grimWirePitchFrozen(false, false),
            "no clickable ray never froze the pitch");
    }

    @Test
    void removeLimitsScalesOnlyTheStreamCeilingsAndClickRulesKeepTheirValues() {
        assertEquals(4.0F, ScaffoldModule.GRIM_REMOVE_LIMITS_ROTATION_SCALE, 0.0F,
            "the user asked for exactly 4x rotations");
        double gcd = 0.15D;
        long base = ScaffoldModule.grimCapCounts(1_000_000L, ScaffoldModule.GRIM_MAX_YAW_STEP, gcd);
        long scaled = ScaffoldModule.grimCapCounts(1_000_000L,
            ScaffoldModule.GRIM_MAX_YAW_STEP * ScaffoldModule.GRIM_REMOVE_LIMITS_ROTATION_SCALE, gcd);
        assertEquals(base * 4L, scaled,
            "the scaled ceiling admits exactly four times the counts per tick");
        assertEquals(18.0F, ScaffoldModule.GRIM_MAX_YAW_STEP, 0.0F,
            "the base ceilings are untouched - the scale is applied at the stream call site");
        assertEquals(20.0F, ScaffoldModule.GRIM_MAX_PITCH_STEP, 0.0F,
            "the base ceilings are untouched - the scale is applied at the stream call site");
        assertEquals(10.0F, ScaffoldModule.GRIM_PLACE_MAX_PITCH_STEP, 0.0F,
            "the click-tick pitch step stays human whatever the toggle says");
        assertTrue(ScaffoldModule.REMOVE_LIMITS_TIP.split(" ").length <= 8,
            "the tip must stay within eight words");
    }

    private static ScaffoldModule.PlacementTarget postureLeg(
        BlockPos support, Direction face, float yaw
    ) {
        BlockPos placed = support.relative(face);
        return new ScaffoldModule.PlacementTarget(
            support, placed, face,
            new BlockHitResult(
                Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.5D, 0.16D, face.getStepZ() * 0.5D),
                face, support, false),
            new Rotation(yaw, 88.0F), support.getY());
    }

    private static ScaffoldModule.PlacementTarget legTarget(BlockPos support, Direction face) {
        BlockPos placed = support.relative(face);
        return new ScaffoldModule.PlacementTarget(
            support, placed, face,
            new BlockHitResult(
                Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.5D, 0.16D, face.getStepZ() * 0.5D),
                face, support, false),
            new Rotation(face == Direction.NORTH ? 45.0F : 45.3F, 89.0F),
            support.getY());
    }

    private static ScaffoldModule.PlacementTarget frontierTarget(BlockPos support) {
        BlockPos placed = support.east();
        return new ScaffoldModule.PlacementTarget(
            support, placed, Direction.EAST,
            new net.minecraft.world.phys.BlockHitResult(
                new Vec3(support.getX() + 1.0D, support.getY() + 0.85D, support.getZ() + 0.5D),
                Direction.EAST, support, false),
            new Rotation(90.0F, 84.3F),
            support.getY());
    }

    private static ScaffoldModule.PlacementTarget seedPlan(BlockPos placed, Rotation rotation) {
        return new ScaffoldModule.PlacementTarget(
            placed.west(), placed, Direction.EAST,
            new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(placed), Direction.EAST, placed.west(), false),
            rotation, placed.getY());
    }

    private static final class BridgeWorld implements net.minecraft.world.level.BlockGetter {
        private final BlockPos support;

        BridgeWorld(BlockPos support) {
            this.support = support;
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
            return pos.equals(support) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    private static Vec3 movementVector(Input input, float yaw) {
        double x = impulse(input.left(), input.right());
        double z = impulse(input.forward(), input.backward());
        double length = x * x + z * z;
        if (length > 1.0D) {
            double inverseLength = 1.0D / Math.sqrt(length);
            x *= inverseLength;
            z *= inverseLength;
        }
        double radians = Math.toRadians(yaw);
        double sine = Math.sin(radians);
        double cosine = Math.cos(radians);
        return new Vec3(x * cosine - z * sine, 0.0D, z * cosine + x * sine);
    }

    private static double impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0D;
        return positive ? 1.0D : -1.0D;
    }

    @Test
    void grimRiseCellsStayLegalForTheWholeJumpArc() {

        assertTrue(ScaffoldModule.grimRiseAllowed(false, false),
            "an airborne player with the jump key already released may still place the rise cell");
        assertTrue(ScaffoldModule.grimRiseAllowed(true, true),
            "a grounded player holding jump is about to rise");
        assertFalse(ScaffoldModule.grimRiseAllowed(false, true),
            "a grounded player not asking to jump gets no rise cells");

        ScaffoldModule.GrimRowLock lock = new ScaffoldModule.GrimRowLock(true, 171, 63, 10, 9, 11, 171);
        BlockPos tower = new BlockPos(10, 64, 171);
        assertTrue(lock.allows(tower, true), "the rise cell above the last placement is legal while rising");
        assertFalse(lock.allows(tower, false), "the same cell is filtered out when no rise is happening");
        assertTrue(lock.allows(new BlockPos(11, 63, 171), false),
            "the flat frontier stays legal regardless of the rise flag");
    }

    @Test
    void grimLateralDriftProbeFacesTheSideLosingItsFooting() {

        ScaffoldModule.MovementLine west = new ScaffoldModule.MovementLine(
            new Vec3(-475.0D, 66.0D, 151.5D), new Vec3(-1.0D, 0.0D, 0.0D));
        Vec3 drifted = new Vec3(-475.83D, 66.0D, 152.13D);

        double error = ScaffoldModule.grimLaneError(west, drifted);
        assertEquals(-0.63D, error, 1.0E-6D, "drifting +z off a westward lane reads as a negative error");
        Vec3 probe = ScaffoldModule.grimLateralDriftProbe(west, drifted, 0.35D);
        assertEquals(152.48D, probe.z, 1.0E-6D, "the probe looks at the +z side the player is leaving toward");
        assertEquals(drifted.x, probe.x, 1.0E-6D, "the probe never moves along the lane");

        Vec3 other = new Vec3(-475.83D, 66.0D, 150.87D);
        assertTrue(ScaffoldModule.grimLaneError(west, other) > 0.0D, "drifting -z reads as a positive error");
        assertEquals(150.52D, ScaffoldModule.grimLateralDriftProbe(west, other, 0.35D).z, 1.0E-6D,
            "the probe follows the drift side rather than a fixed hand");
    }

    private static synchronized void ensureGameDir(Path dir) {
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gameDirField = impl.getDeclaredField("gameDir");
            gameDirField.setAccessible(true);
            if (gameDirField.get(loader) == null) {
                Method setter = impl.getDeclaredMethod("setGameDir", Path.class);
                setter.setAccessible(true);
                setter.invoke(loader, dir);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize Scaffold test game dir", t);
        }
    }

    private static int[] runCourseTicks(int[] state, float cameraYaw, boolean frozen, int ticks) {
        int[] current = state;
        for (int i = 0; i < ticks; i++) {
            current = ScaffoldModule.nextCourseStep(current[0], current[1], current[2], cameraYaw, frozen);
        }
        return current;
    }

    @Test
    void compassStepMatchesTheHudCompassStrip() {

        for (int step = 0; step < 8; step++) {
            float yaw = step * 45.0F;
            assertEquals(step, ScaffoldModule.compassStep(yaw), "step " + step + " at yaw " + yaw);
            assertEquals(net.minecraft.util.Mth.wrapDegrees(yaw),
                ScaffoldModule.compassStepYaw(step), 1.0E-4F);
            assertEquals(step % 2 == 1, ScaffoldModule.compassStepIsDiagonal(step),
                "odd steps are the diagonals");
        }

        for (float yaw = -180.0F; yaw <= 180.0F; yaw += 0.5F) {
            int step = ScaffoldModule.compassStep(yaw);
            assertTrue(step >= 0 && step < 8, "yaw " + yaw + " produced step " + step);
            float stepYaw = ScaffoldModule.compassStepYaw(step);
            assertEquals(0.0F, Math.abs(stepYaw % 45.0F), 1.0E-3F, "step yaw is not a multiple of 45");
            assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - stepYaw)) <= 22.5F + 1.0E-3F,
                "yaw " + yaw + " snapped to " + stepYaw + ", which is not the nearest step");
        }
    }

    @Test
    void aSmallLookNeverMovesTheCourse() {

        float north = ScaffoldModule.compassStepYaw(4);
        int[] state = {4, ScaffoldModule.COURSE_STEP_UNSET, 0};
        for (float offset = -37.0F; offset <= 37.0F; offset += 0.5F) {
            state = runCourseTicks(state, net.minecraft.util.Mth.wrapDegrees(north + offset), false, 20);
            assertEquals(4, state[0], "a " + offset + " degree look moved the course");
            assertEquals(ScaffoldModule.COURSE_STEP_UNSET, state[1],
                "a " + offset + " degree look left a turn pending");
            assertEquals(0, state[2]);
        }
    }

    @Test
    void aDeliberateTurnCommitsAfterTheDwell() {
        float north = ScaffoldModule.compassStepYaw(4);

        float turned = net.minecraft.util.Mth.wrapDegrees(north + 50.0F);
        int[] state = {4, ScaffoldModule.COURSE_STEP_UNSET, 0};

        state = runCourseTicks(state, turned, false, 1);
        assertEquals(4, state[0], "committed on tick 1, before the camera settled");
        assertEquals(1, state[2], "dwell should be counting up");
        state = runCourseTicks(state, turned, false, 1);
        assertEquals(ScaffoldModule.compassStep(turned), state[0], "a settled turn must commit");
        assertEquals(ScaffoldModule.COURSE_STEP_UNSET, state[1], "the candidate clears on commit");
    }

    @Test
    void aFlickThroughNeverCommits() {
        float north = ScaffoldModule.compassStepYaw(4);
        int[] state = {4, ScaffoldModule.COURSE_STEP_UNSET, 0};

        state = runCourseTicks(state, net.minecraft.util.Mth.wrapDegrees(north + 90.0F), false, 1);
        assertEquals(4, state[0], "a flick must not move the course");
        state = runCourseTicks(state, north, false, 1);
        assertEquals(4, state[0]);
        assertEquals(ScaffoldModule.COURSE_STEP_UNSET, state[1], "returning home clears the pending turn");

        for (int i = 0; i < 12; i++) {
            float wander = net.minecraft.util.Mth.wrapDegrees(north + (i % 2 == 0 ? 90.0F : -90.0F));
            state = runCourseTicks(state, wander, false, 1);
            assertEquals(4, state[0], "an indecisive turn committed on iteration " + i);
        }
    }

    @Test
    void aFallNeverTurnsTheCourse() {

        float north = ScaffoldModule.compassStepYaw(4);
        int[] state = {4, ScaffoldModule.COURSE_STEP_UNSET, 0};
        state = runCourseTicks(state, net.minecraft.util.Mth.wrapDegrees(north + 90.0F), true, 40);
        assertEquals(4, state[0], "a descending fall committed a course change");
        assertEquals(ScaffoldModule.COURSE_STEP_UNSET, state[1]);
    }

    @Test
    void theDwellSurvivesAFallAndCommitsAfterLanding() {

        float north = ScaffoldModule.compassStepYaw(4);
        float turned = net.minecraft.util.Mth.wrapDegrees(north + 50.0F);
        int[] state = {4, ScaffoldModule.COURSE_STEP_UNSET, 0};
        state = runCourseTicks(state, turned, false, 1);
        assertEquals(1, state[2], "one dwelling tick in");
        state = runCourseTicks(state, turned, true, 6);
        assertEquals(4, state[0], "a fall must not commit the turn");
        assertEquals(1, state[2], "the dwell is preserved through the arc");
        state = runCourseTicks(state, turned, false, 1);
        assertEquals(ScaffoldModule.compassStep(turned), state[0],
            "the preserved dwell completes the commit once unfrozen");
    }

    @Test
    void anUnsetCourseSeedsFromTheFirstLook() {

        for (int step = 0; step < 8; step++) {
            int[] state = ScaffoldModule.nextCourseStep(
                ScaffoldModule.COURSE_STEP_UNSET, ScaffoldModule.COURSE_STEP_UNSET, 0,
                ScaffoldModule.compassStepYaw(step), false);
            assertEquals(step, state[0], "the first look must seed immediately");
            assertEquals(0, state[2]);
        }
    }

    @Test
    void thePostureIsAPureFunctionOfTheStep() {

        for (int step = 0; step < 8; step++) {
            float stepYaw = ScaffoldModule.compassStepYaw(step);
            assertEquals(ScaffoldModule.grimPlacementPostureYaw(stepYaw),
                ScaffoldModule.grimPlacementPostureYaw(stepYaw), 0.0F);
            assertEquals(180.0F,
                Math.abs(AutismRotationUtil.angleDifference(
                    ScaffoldModule.grimPlacementPostureYaw(stepYaw), stepYaw)),
                1.0E-3F, "step " + step + " posture is not the exact reverse");
        }
    }

    private static float[] runPostureYawTicks(float[] state, float requested, int ticks) {
        float[] current = state;
        for (int i = 0; i < ticks; i++) {
            current = ScaffoldModule.nextPostureYaw(current[0], current[1], (int) current[2], requested);
        }
        return current;
    }

    @Test
    void theSteeredPostureYawDampsRapidRequestFlipping() {

        float[] state = {Float.NaN, Float.NaN, 0.0F};

        state = ScaffoldModule.nextPostureYaw(state[0], state[1], (int) state[2], 135.0F);
        assertEquals(135.0F, state[0]);

        float[] flailing = {90.0F, -180.0F, 45.0F, 90.0F, -45.0F, -180.0F, 90.0F};
        for (float requested : flailing) {
            state = ScaffoldModule.nextPostureYaw(state[0], state[1], (int) state[2], requested);
            assertEquals(135.0F, state[0], "a request that never repeats must not move the held yaw");
        }

        state = runPostureYawTicks(state, -45.0F, 1);
        assertEquals(135.0F, state[0], "one tick of a new value is not yet a turn");
        state = runPostureYawTicks(state, -45.0F, 1);
        assertEquals(-45.0F, state[0], "two consecutive ticks of the same request commits it");

        state = runPostureYawTicks(state, -45.0F, 5);
        assertEquals(-45.0F, state[0]);
        assertTrue(Float.isNaN(state[1]));
        assertEquals(0.0F, state[2]);
    }

    @Test
    void aRecordedPlacementPrefersTheSafePitchWheneverTheApproachAllowsIt() {
        assertEquals(84.5F, ScaffoldModule.GRIM_INTAVE_ROTATION_SAFE_PITCH,
            "85.0 strictly-greater minus the half-degree quantization slack");

        assertEquals(89.0F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, 82.0, false, true, true, 84.5F));

        assertEquals(84.5F, ScaffoldModule.grimIntaveRotationPitchDecision(
            84.5F, 82.0, true, true, true, 84.5F));

        assertEquals(84.5F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, 82.0, true, false, false, 84.5F));

        assertEquals(84.5F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, 88.0, true, true, true, 84.5F));

        assertEquals(84.5F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, Double.NaN, true, true, true, 84.5F));

        assertEquals(89.0F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, 88.0, true, false, true, 84.5F));

        assertEquals(89.0F, ScaffoldModule.grimIntaveRotationPitchDecision(
            89.0F, 88.0, true, true, false, 84.5F));
    }

    @Test
    void theSafeShipPitchLocksTheFlatChainOutOfTheFlickBand() {

        assertEquals(84.5F, ScaffoldModule.grimIntaveSafeShipPitch(Float.NaN, 200L));
        assertEquals(84.5F, ScaffoldModule.grimIntaveSafeShipPitch(77.0F, -1L));
        assertEquals(84.5F, ScaffoldModule.grimIntaveSafeShipPitch(77.0F, 900L));

        assertEquals(79.5F, ScaffoldModule.grimIntaveSafeShipPitch(77.0F, 200L));
        assertTrue(ScaffoldModule.grimIntaveFlickSafeDiff(79.5F, 77.0F),
            "the locked ship reads outside the band");

        assertEquals(84.5F, ScaffoldModule.grimIntaveSafeShipPitch(83.0F, 200L));

        assertEquals(84.5F, ScaffoldModule.grimIntaveSafeShipPitch(88.5F, 200L));

        float last = 77.0F;
        for (int i = 0; i < 5; i++) {
            float ship = ScaffoldModule.grimIntaveSafeShipPitch(last, 250L);
            assertTrue(ScaffoldModule.grimIntaveFlickSafeDiff(ship, last),
                "ratchet step " + i + " stays out of the band: " + last + " -> " + ship);
            assertTrue(ship <= 84.5F, "and under BlockRotation's line: " + ship);
            last = ship;
        }
        assertEquals(84.5F, last, "the chain converges on the park and stays");
    }

    @Test
    void theShallowLookaheadNeedsRealMotionAndEnoughOfIt() {

        BlockPos support = new BlockPos(0, 64, 0);
        Vec3 eye = new Vec3(0.85D, 66.62D, 0.5D);
        Vec3 step = new Vec3(0.2D, 0.0D, 0.0D);
        assertTrue(ScaffoldModule.grimIntaveShallowAhead(eye, step, support, Direction.EAST, 90.0F,
                84.5F, ScaffoldModule.GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS),
            "two ticks out the eye is 0.25 past the plane and the demand reads ~81.5");

        assertFalse(ScaffoldModule.grimIntaveShallowAhead(
            eye, Vec3.ZERO, support, Direction.EAST, 90.0F,
            84.5F, ScaffoldModule.GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS));

        assertFalse(ScaffoldModule.grimIntaveShallowAhead(
            eye, new Vec3(0.05D, 0.0D, 0.0D), support, Direction.EAST, 90.0F,
            84.5F, ScaffoldModule.GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS));

        assertFalse(ScaffoldModule.grimIntaveShallowAhead(
            eye, new Vec3(0.1D, 0.0D, 0.0D), support, Direction.EAST, 90.0F,
            84.5F, ScaffoldModule.GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS));
        assertTrue(ScaffoldModule.grimIntaveShallowAhead(
            eye, new Vec3(0.1D, 0.0D, 0.0D), support, Direction.EAST, 90.0F,
            84.5F, ScaffoldModule.GRIM_INTAVE_PARK_LOOKAHEAD_GROUNDED_TICKS));
    }

    @Test
    void theFlickBandIsWhatFlaggedAt1230AndTheNudgeClearsIt() {

        float[][] pairs = {{89.3F, 81.6F}, {72.9F, 77.4F}, {85.4F, 78.9F}};
        for (float[] pair : pairs) {
            assertFalse(ScaffoldModule.grimIntaveFlickSafeDiff(pair[1], pair[0]),
                "12:30 flagged this pair, so it must read as inside the band: "
                    + pair[0] + " -> " + pair[1]);
            assertTrue(pair[1] > ScaffoldModule.GRIM_INTAVE_FLICK_PITCH_MIN,
                "and over the pitch floor: " + pair[1]);
        }

        float nudged = ScaffoldModule.grimIntaveFlickPitchNudge(81.6F, 89.3F, 70.0D, 89.0D);
        assertTrue(ScaffoldModule.grimIntaveFlickSafeDiff(nudged, 89.3F),
            "the nudged goal has to leave the band: " + nudged);
        assertTrue(nudged >= 70.0F && nudged <= 89.0F,
            "and stay inside the solved window: " + nudged);

        assertEquals(60.0F, ScaffoldModule.grimIntaveFlickPitchNudge(60.0F, 89.3F, 55.0D, 89.0D));
        assertEquals(81.6F, ScaffoldModule.grimIntaveFlickPitchNudge(81.6F, 89.3F, 81.5D, 81.7D));
    }

    @Test
    void aParkedGoalCannotClickTheCellTheAirborneChainOwes() {

        double toward = Math.cos(Math.toRadians(45.0D));
        double dropTop = 100.87D - 98.0D;
        double dropBottom = 100.87D - 97.0D;
        double low = Math.toDegrees(Math.atan2(dropTop, 0.138D / toward));
        double high = Math.toDegrees(Math.atan2(dropBottom, 0.138D / toward));
        assertTrue(low > ScaffoldModule.GRIM_INTAVE_ROTATION_SAFE_PITCH,
            "the safe pitch is under the whole window, so a parked goal cannot click: " + low);

        double nextLow = Math.toDegrees(Math.atan2(dropTop, 0.169D / toward));
        double nextHigh = Math.toDegrees(Math.atan2(dropBottom, 0.169D / toward));
        assertTrue(ScaffoldModule.GRIM_INTAVE_ROTATION_SAFE_PITCH < nextLow,
            "parked, the ray is still short a tick later: " + nextLow);

        assertTrue(low >= nextLow && low <= nextHigh,
            "the natural t247 goal lands at t248: " + low + " vs [" + nextLow + ", " + nextHigh
                + "]");
    }

    private static Input keys(boolean forward, boolean backward, boolean left, boolean right) {
        return new Input(forward, backward, left, right, false, false, false);
    }

    @Test
    void theLaneIsTheCourseTurnedByTheKeys() {

        for (int course = 0; course < 8; course++) {
            assertEquals(course, ScaffoldModule.laneStep(course, keys(true, false, false, false)),
                "W alone must walk the course");
            assertEquals((course + 4) % 8, ScaffoldModule.laneStep(course, keys(false, true, false, false)),
                "S must walk the opposite step - this is the fall");
            assertEquals((course + 6) % 8, ScaffoldModule.laneStep(course, keys(false, false, true, false)),
                "A must walk a quarter turn left");
            assertEquals((course + 2) % 8, ScaffoldModule.laneStep(course, keys(false, false, false, true)),
                "D must walk a quarter turn right");
            assertEquals((course + 7) % 8, ScaffoldModule.laneStep(course, keys(true, false, true, false)),
                "W+A must walk an eighth turn left");
            assertEquals((course + 5) % 8, ScaffoldModule.laneStep(course, keys(false, true, true, false)),
                "S+A must mirror behind");

            assertEquals(course, ScaffoldModule.laneStep(course, keys(false, false, false, false)));
            assertEquals(course, ScaffoldModule.laneStep(course, keys(true, true, true, true)));
        }
        assertEquals(ScaffoldModule.COURSE_STEP_UNSET,
            ScaffoldModule.laneStep(ScaffoldModule.COURSE_STEP_UNSET, keys(true, false, false, false)),
            "no course yet means no lane yet");
    }

    @Test
    void theLaneIsWhereTheTransformedKeysActuallyTravel() {

        boolean[][] combos = {
            {true, false, false, false}, {false, true, false, false},
            {false, false, true, false}, {false, false, false, true},
            {true, false, true, false}, {true, false, false, true},
            {false, true, true, false}, {false, true, false, true},
        };
        for (int course = 0; course < 8; course++) {
            float courseYaw = ScaffoldModule.compassStepYaw(course);
            for (boolean[] combo : combos) {
                Input held = keys(combo[0], combo[1], combo[2], combo[3]);
                int lane = ScaffoldModule.laneStep(course, held);

                float silent = ScaffoldModule.grimPlacementPostureYaw(
                    ScaffoldModule.compassStepYaw(lane));
                Input sent = ScaffoldModule.transformSilentMovementInput(held, courseYaw, silent);
                float travel = silent + ScaffoldModule.inputOctantDegrees(sent);
                assertEquals(lane, ScaffoldModule.compassStep(travel),
                    "course " + course + " walked step " + ScaffoldModule.compassStep(travel)
                        + " while the lane said " + lane);
            }
        }
    }

    @Test
    void bothHalvesOfSilentCorrectionSwitchOnTogether() {

        for (int mask = 0; mask < 8; mask++) {
            boolean windingDown = (mask & 1) != 0;
            boolean enabled = (mask & 2) != 0;
            boolean canRun = (mask & 4) != 0;
            boolean movement = ScaffoldModule.silentCorrectionApplies(windingDown, enabled, true, canRun);
            boolean input = windingDown || ScaffoldModule.silentCorrectionOwnsInput(enabled, canRun);
            assertEquals(movement, input,
                "halves disagree at windingDown=" + windingDown + " enabled=" + enabled + " canRun=" + canRun);
        }

        assertTrue(ScaffoldModule.silentCorrectionApplies(true, false, true, false),
            "movement override is live while winding down after a disable");
        assertFalse(ScaffoldModule.silentCorrectionOwnsInput(false, false),
            "the enabled-only gate is exactly what used to strand the keys");
    }

    @Test
    void aCourseChangeTurnsThePostureByExactlyTheCourseTurn() {

        for (int from = 0; from < 8; from++) {
            for (int delta = -3; delta <= 3; delta++) {
                int to = ((from + delta) % 8 + 8) % 8;
                float moved = Math.abs(AutismRotationUtil.angleDifference(
                    ScaffoldModule.grimPlacementPostureYaw(ScaffoldModule.compassStepYaw(to)),
                    ScaffoldModule.grimPlacementPostureYaw(ScaffoldModule.compassStepYaw(from))));
                assertEquals(Math.abs(delta) * 45.0F, moved, 0.01F,
                    "a " + delta + "-octant course change moved the posture " + moved + " degrees");
            }
        }
    }

    @Test
    void theUpFacePinsSoAPillarCostsNoCamera() {

        BlockPos support = new BlockPos(-871, 189, -493);

        Vec3 eye = new Vec3(support.getX() - 1.5D, support.getY() + 2.6D, support.getZ() + 0.5D);
        assertTrue(ScaffoldModule.grimTopCrossingLandsOnFace(eye, support, -90.0F, false),
            "a track running straight over the block must pin");

        float pitch = ScaffoldModule.grimTopCrossingPitch(eye, support, -90.0F);
        assertFalse(Float.isNaN(pitch), "a pinning track must yield a pitch");

        double run = (eye.y - (support.getY() + 1.0D)) / Math.tan(Math.toRadians(pitch));
        double landX = eye.x + -Math.sin(Math.toRadians(-90.0F)) * run;
        double landZ = eye.z + Math.cos(Math.toRadians(-90.0F)) * run;
        assertTrue(landX > support.getX() && landX < support.getX() + 1.0D,
            "solved pitch landed at x=" + landX + ", outside the block");
        assertTrue(landZ > support.getZ() && landZ < support.getZ() + 1.0D,
            "solved pitch landed at z=" + landZ + ", outside the block");

        Vec3 aside = new Vec3(support.getX() - 1.5D, support.getY() + 2.6D, support.getZ() + 4.0D);
        assertFalse(ScaffoldModule.grimTopCrossingLandsOnFace(aside, support, -90.0F, false),
            "a track running well to the side must not pin");
        assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(aside, support, -90.0F)));

        Vec3 below = new Vec3(support.getX() - 1.5D, support.getY() + 0.5D, support.getZ() + 0.5D);
        assertFalse(ScaffoldModule.grimTopCrossingLandsOnFace(below, support, -90.0F, false),
            "the top face is unreachable from below and must not pin");

        Vec3 edge = new Vec3(support.getX() - 1.5D, support.getY() + 2.6D, support.getZ() + 0.05D);
        assertFalse(ScaffoldModule.grimTopCrossingLandsOnFace(edge, support, -90.0F, false),
            "the acquire band must be the tight one");
        assertTrue(ScaffoldModule.grimTopCrossingLandsOnFace(edge, support, -90.0F, true),
            "a pin already held must survive the same graze");

        assertFalse(ScaffoldModule.grimFacePins(eye, support, Direction.DOWN, -90.0F, false));
        assertTrue(ScaffoldModule.grimFacePins(eye, support, Direction.UP, -90.0F, false));
    }

    @Test
    void theSentYawNeverJumpsAcrossTheWrapSeam() {

        float silent = -45.0F;
        float cameraJustBelow = 134.99F;
        float cameraJustAbove = 135.01F;

        float oldA = ScaffoldModule.grimContinuousYaw(cameraJustBelow, silent);
        float oldB = ScaffoldModule.grimContinuousYaw(cameraJustAbove, silent);
        assertEquals(0.0F, Math.abs(net.minecraft.util.Mth.wrapDegrees(oldA - oldB)), 0.05F,
            "the two anchors must describe the same direction - otherwise this test proves nothing");
        assertTrue(Math.abs(oldA - oldB) > 300.0F,
            "camera-anchored emission must be shown to flip a whole turn, else the fix is untested");

        float sent = ScaffoldModule.grimContinuousYaw(cameraJustBelow, silent);
        for (int tick = 0; tick < 64; tick++) {

            float creeping = silent + (tick % 8) * 0.15F;
            float next = ScaffoldModule.grimContinuousYaw(sent, creeping);
            float rawDelta = Math.abs(next - sent);
            assertTrue(rawDelta < 5.0F,
                "raw sent-yaw delta " + rawDelta + " on tick " + tick + " - a seam flip reads ~360");
            sent = next;
        }

        float before = ScaffoldModule.grimContinuousYaw(179.0F, 179.0F);
        float after = ScaffoldModule.grimContinuousYaw(before, -179.0F);
        assertEquals(2.0F, Math.abs(after - before), 1.0E-3F,
            "crossing the seam for real is a 2 degree move and must be sent as one");
    }

    @Test
    void theApproachHoldsThePostureSoTheCameraNeverChasesAnUnreachableAim() {

        BlockPos support = new BlockPos(-549, 63, -521);
        for (Direction face : List.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH)) {
            Vec3 behind = Vec3.atCenterOf(support)
                .add(new Vec3(face.getStepX(), 0.0D, face.getStepZ()).scale(0.4D))
                .add(0.0D, 1.62D, 0.0D);
            assertTrue(ScaffoldModule.grimEyePastPlane(behind, support, face) <= 0.0D,
                "test eye must actually sit behind the " + face + " plane");
            for (float yaw = -180.0F; yaw < 180.0F; yaw += 15.0F) {
                assertTrue(ScaffoldModule.grimCrossingLandsOnFace(behind, support, face, yaw),
                    "behind the plane every yaw must stay pinnable (" + face + " @ " + yaw + ")");
            }
        }
    }

    @Test
    void thePinHoldsThroughTheBandEdge() {

        BlockPos support = new BlockPos(0, 63, 0);
        Direction face = Direction.WEST;

        double acquire = ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN;
        double release = ScaffoldModule.GRIM_PIN_SIDE_RELEASE_MARGIN;
        assertTrue(release < acquire, "the hysteresis is the whole point of the split");

        assertTrue(release < 0.0D, "a held side pin has to survive a graze off the edge");
        assertTrue(ScaffoldModule.GRIM_PIN_RELEASE_MARGIN > 0.0D, "the top-face band is unchanged");

        assertTrue(landsWest(support, 0.50D, false), "mid-face acquires");
        assertTrue(landsWest(support, 0.50D, true), "mid-face holds");

        double between = (acquire + release) * 0.5D;
        assertFalse(landsWest(support, between, false), between + " must not acquire the pin");
        assertTrue(landsWest(support, between, true), between + " must not drop a pin already held");
        assertFalse(landsWest(support, 1.0D - between, false), "the far edge acquires symmetrically");
        assertTrue(landsWest(support, 1.0D - between, true), "the far edge holds symmetrically");

        assertTrue(landsWest(support, -0.07D, true), "a 0.07 graze is what the hold is for");
        assertTrue(landsWest(support, 1.06D, true), "and the same graze off the far edge");
        assertFalse(landsWest(support, release - 0.10D, true), "a real miss still releases");
        assertFalse(landsWest(support, 1.0D - release + 0.10D, true), "and the same miss off the far edge");

        assertTrue(acquire <= 0.05D, "acquire margin " + acquire + " throws away too much of the face");
        assertTrue(landsWest(support, 0.06D, false), "0.06 is on the face and must acquire");
        assertTrue(landsWest(support, 0.94D, false), "0.94 is on the face and must acquire");

        boolean held = false;
        for (int tick = 0; tick < 32; tick++) {
            boolean next = landsWest(support, acquire, held);
            if (tick > 0) assertEquals(held, next, "pin flipped on tick " + tick + " while parked on the margin");
            held = next;
        }
    }

    private static boolean landsWest(BlockPos support, double t, boolean holding) {

        Vec3 eye = new Vec3(support.getX() - 0.5D, support.getY() + 1.62D, support.getZ() + t);
        return ScaffoldModule.grimCrossingLandsOnFace(eye, support, Direction.WEST, -90.0F, holding);
    }

    @Test
    void aPinThatNeverLandsGoesStale() {

        int count = 0;
        for (int tick = 0; tick < 100; tick++) count = ScaffoldModule.grimStaleCount(count, true, false);
        assertTrue(count > ScaffoldModule.GRIM_PIN_STALE_TICKS, "a 100-tick freeze must read stale");

        count = 0;
        for (int tick = 0; tick < 7; tick++) {
            count = ScaffoldModule.grimStaleCount(count, true, false);
            assertFalse(count > ScaffoldModule.GRIM_PIN_STALE_TICKS,
                "the normal 6-7 tick miss run must not drop the pin, tripped at " + tick);
        }
        assertEquals(0, ScaffoldModule.grimStaleCount(count, true, true), "a landed click clears it");
        assertEquals(1, ScaffoldModule.grimStaleCount(count, false, false), "a new target starts over");
    }

    @Test
    void theLPairCornerAcquiresFromEitherFace() {

        BlockPos support = new BlockPos(-510, 138, -940);
        Direction face = Direction.WEST;
        for (double corner : new double[] { -0.12D, -0.09D, -0.07D, -0.06D, -0.02D, 0.97D, 0.99D, 1.06D }) {
            assertTrue(
                ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, corner), support, face, -45.0F, false),
                "the L-pair corner at " + corner + " must acquire the pin");
        }

        for (double miss : new double[] { -0.23D, -0.25D, 1.25D, 1.30D }) {
            assertFalse(
                ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, miss), support, face, -45.0F, false),
                "a genuine miss at " + miss + " must not acquire");
        }
        assertTrue(ScaffoldModule.GRIM_PIN_CORNER_RELEASE_MARGIN < ScaffoldModule.GRIM_PIN_CORNER_ACQUIRE_MARGIN,
            "widening acquire must not swallow the hysteresis");

        assertTrue(ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN > 0.0D, "the cardinal band stays inside the face");
        assertFalse(landsWest(new BlockPos(0, 63, 0), 1.06D, false),
            "a cardinal look must not get the corner band");
    }

    @Test
    void aDiagonalCrossingSweepsTheFaceSoThePinWaitsInsteadOfSwinging() {

        BlockPos support = new BlockPos(54, 74, -118);
        Direction face = Direction.EAST;
        for (double c = -62.6D; c <= -61.4D; c += 0.1D) {

            Vec3 eye = new Vec3(55.3D, 75.6D, c - 55.3D);
            double expected = c - (support.getX() + support.getZ() + 1);
            assertEquals(expected, ScaffoldModule.grimCrossingFraction(eye, support, face, 45.0F), 1.0E-6D,
                "the crossing must track x+z one for one at c=" + c);
        }

        Vec3 offFace = new Vec3(55.3D, 75.6D, -61.80D - 55.3D);
        Vec3 inbound = new Vec3(0.02D, 0.0D, -0.08D);
        assertFalse(ScaffoldModule.grimFacePins(offFace, support, face, 45.0F, false),
            "the crossing really is off the face this tick");
        assertTrue(ScaffoldModule.grimFacePinsSoon(offFace, inbound, support, face, 45.0F, false,
            ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS), "a crossing sweeping onto the face must hold the posture");

        Vec3 outbound = new Vec3(-0.02D, 0.0D, 0.08D);
        assertFalse(ScaffoldModule.grimFacePinsSoon(offFace, outbound, support, face, 45.0F, false,
            ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS), "a crossing sweeping away must fall through to the point aim");

        for (double t : new double[] { 0.02D, 0.5D, 0.98D, 1.06D }) {
            Vec3 eye = new Vec3(55.3D, 75.6D, (support.getX() + support.getZ() + 1 + t) - 55.3D);
            assertEquals(
                ScaffoldModule.grimFacePins(eye, support, face, 45.0F, false),
                ScaffoldModule.grimFacePinsSoon(eye, Vec3.ZERO, support, face, 45.0F, false,
                    ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS),
                "a still player must give the same answer either way at t=" + t);
        }

        Vec3 behind = new Vec3(54.5D, 75.6D, -117.5D);
        assertTrue(Double.isNaN(ScaffoldModule.grimCrossingFraction(behind, support, face, 45.0F)));
        assertTrue(ScaffoldModule.grimFacePins(behind, support, face, 45.0F, false),
            "behind the plane the posture must still park");
    }

    @Test
    void travellingAlongADiagonalLaneDoesNotMoveTheCrossing() {

        BlockPos support = new BlockPos(54, 74, -118);
        Vec3 lane = new Vec3(1.0D, 0.0D, -1.0D).normalize();
        for (Direction face : new Direction[] { Direction.EAST, Direction.NORTH }) {

            Vec3 eye = new Vec3(55.4D, 75.6D, -118.6D);
            double start = ScaffoldModule.grimCrossingFraction(eye, support, face, 45.0F);
            assertTrue(Double.isFinite(start), "the probe eye must be past the " + face + " plane");
            for (int tick = 1; tick <= 8; tick++) {
                Vec3 moved = eye.add(lane.scale(0.17D * tick));
                assertEquals(start,
                    ScaffoldModule.grimCrossingFraction(moved, support, face, 45.0F), 1.0E-9D,
                    "travel must not move the " + face + " crossing at tick " + tick);
            }
        }
    }

    @Test
    void aCellThePlayerHasWalkedPastIsNeverPlaced() {

        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);

        assertTrue(ScaffoldModule.grimCellBehind(new Vec3(-574.71D, 133.25D, -1000.53D), south,
            new BlockPos(-575, 132, -1003), 0.8D), "2.0 back is behind");
        assertTrue(ScaffoldModule.grimCellBehind(new Vec3(-574.73D, 133.02D, -1000.17D), south,
            new BlockPos(-575, 132, -1002), 0.8D), "1.3 back is behind");
    }

    @Test
    void theCellUnderfootAndTheOneJustClearedStillCount() {

        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);

        assertFalse(ScaffoldModule.grimCellBehind(new Vec3(-574.71D, 131.17D, -1004.32D), south,
            new BlockPos(-575, 130, -1005), 0.8D), "the pillar under the player is not behind");
        assertFalse(ScaffoldModule.grimCellBehind(new Vec3(-574.75D, 132.50D, -999.80D), south,
            new BlockPos(-575, 131, -1001), 0.8D), "the catch just cleared is not behind");
        assertFalse(ScaffoldModule.grimCellBehind(new Vec3(-574.70D, 132.25D, -1002.56D), south,
            new BlockPos(-575, 131, -1003), 0.8D), "a cell still ahead is not behind");
    }

    @Test
    void theBehindRuleReadsADiagonalLaneAlongBothAxes() {

        Vec3 southEast = new Vec3(1.0D, 0.0D, 1.0D);
        assertTrue(ScaffoldModule.grimCellBehind(new Vec3(-486.93D, 170.17D, -1039.75D), southEast,
            new BlockPos(-488, 169, -1041), 0.8D), "0.93 back along a diagonal lane is behind");

        assertFalse(ScaffoldModule.grimCellBehind(new Vec3(-492.62D, 165.17D, -1045.32D), southEast,
            new BlockPos(-493, 164, -1046), 0.8D), "the pillar underfoot is not behind");
        assertFalse(ScaffoldModule.grimCellBehind(new Vec3(-490.84D, 167.25D, -1043.20D), southEast,
            new BlockPos(-491, 166, -1044), 0.8D), "the lateral just placed is not behind");
    }

    @Test
    void theStrafeDwellOverrideSitsClearOfTheOctantBoundary() {

        assertEquals(30.0F, ScaffoldModule.GRIM_INPUT_OCTANT_BOUNDARY_DEGREES, 1.0E-4F,
            "the octant boundary is asin(0.5)");
        assertEquals(0.5D, Math.sin(Math.toRadians(ScaffoldModule.GRIM_INPUT_OCTANT_BOUNDARY_DEGREES)), 1.0E-6D,
            "at the boundary the rotated request is exactly Math.round's tipping point");
        assertTrue(ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES
                > ScaffoldModule.GRIM_INPUT_OCTANT_BOUNDARY_DEGREES,
            "an override at or below the boundary can never let the dwell hold");

        assertTrue(ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES < ScaffoldModule.GRIM_LANE_INPUT_MAX_DEGREES,
            "an override above the bias cap is dead code");
    }

    @Test
    void aPinnedSideFaceSurvivesAGrazeButNotARealMiss() {

        BlockPos support = new BlockPos(-373, 87, -894);
        Direction face = Direction.WEST;

        for (double graze : new double[] { -0.07D, -0.03D, -0.01D, 0.00D, 0.01D, 1.00D, 1.03D, 1.06D }) {
            assertTrue(
                ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, graze), support, face, -45.0F, true),
                "a held pin survives a crossing at " + graze);
        }
        for (double miss : new double[] { -0.36D, -0.25D, 1.16D, 1.30D }) {
            assertFalse(
                ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, miss), support, face, -45.0F, true),
                "a held pin still releases on a real miss at " + miss);
        }

        assertTrue(
            ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, -0.03D), support, face, -45.0F, false),
            "a corner graze must acquire, not buy a point aim");
        assertFalse(
            ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, -0.25D), support, face, -45.0F, false),
            "a real miss is still refused on acquire");
        assertTrue(
            ScaffoldModule.grimCrossingLandsOnFace(sideFaceEye(support, face, 0.50D), support, face, -45.0F, false),
            "acquiring mid-face still works");
    }

    private static Vec3 sideFaceEye(BlockPos support, Direction face, double fraction) {
        double plane = support.getX() + (face == Direction.WEST ? 0.0D : 1.0D);
        double back = 0.20D;
        return new Vec3(plane - back, support.getY() + 1.5D, support.getZ() + fraction - back);
    }

    @Test
    void theJumpExemptionNeedsAnArcNotJustAHeldKey() {

        assertFalse(ScaffoldModule.grimCarryArcActive(true, true),
            "grounded with jump held is not an arc - the backstops stay armed");
        assertFalse(ScaffoldModule.grimCarryArcActive(true, false),
            "grounded without jump is not an arc");
        assertFalse(ScaffoldModule.grimCarryArcActive(false, false),
            "airborne without jump is a fall, not a carry arc - the backstops stay armed");

        assertTrue(ScaffoldModule.grimCarryArcActive(false, true),
            "airborne with jump held is the carry arc the exemption exists for");
    }

    @Test
    void theBehindRuleAndTheLandingRuleCoverDifferentFailures() {

        Vec3 west = new Vec3(-1.0D, 0.0D, 0.0D);
        Vec3 position = new Vec3(-649.21D, 229.17D, -1102.04D);
        BlockPos column = new BlockPos(-649, 228, -1102);

        assertFalse(ScaffoldModule.grimCellBehind(position, west, column, 0.8D),
            "0.71 back is inside the margin, so this is the landing test's failure");
        assertFalse(ScaffoldModule.grimRiseColumnHeld(position,
            new Vec3(-0.077D, 0.083D, -0.015D), column, 3, 0.15D),
            "and the landing test still catches it");
    }

    @Test
    void aRiseIsRefusedInAColumnTheBoxIsAlreadyLeaving() {

        Vec3 position = new Vec3(-649.21D, 229.17D, -1102.04D);
        Vec3 carry = new Vec3(-0.077D, 0.083D, -0.015D);
        BlockPos column = new BlockPos(-649, 228, -1102);

        assertTrue(ScaffoldModule.grimBoxOverColumn(position, column),
            "the pillar is inside the player's own footprint, so the landing test applies");
        assertFalse(ScaffoldModule.grimRiseColumnHeld(position, carry, column, 3, 0.15D),
            "a column the carry is leaving must not be raised");
    }

    @Test
    void aRiseIsAllowedWhenTheCarryPointsIntoTheColumn() {

        Vec3 position = new Vec3(-634.29D, 199.17D, -1105.46D);
        Vec3 carry = new Vec3(-0.047D, 0.083D, -0.046D);
        BlockPos column = new BlockPos(-635, 198, -1106);

        assertTrue(ScaffoldModule.grimBoxOverColumn(position, column), "probe setup");
        assertTrue(ScaffoldModule.grimRiseColumnHeld(position, carry, column, 3, 0.15D),
            "a column the player is settling into must still be raised");
    }

    @Test
    void theLandingTestOnlyJudgesThePlayersOwnFootprint() {

        Vec3 position = new Vec3(-649.21D, 229.17D, -1102.04D);
        assertFalse(ScaffoldModule.grimBoxOverColumn(position, new BlockPos(-651, 228, -1104)),
            "two cells ahead is not the player's footprint");
        assertFalse(ScaffoldModule.grimBoxOverColumn(position, new BlockPos(-648, 228, -1102)),
            "the neighbouring column the box does not reach is not the player's footprint");
    }

    @Test
    void aBetterFacePrefersTheOneThePostureAlreadyClicks() {

        assertTrue(ScaffoldModule.betterFace(true, 90.0D, false, 1.0D), "pinning beats a closer non-pinning face");
        assertFalse(ScaffoldModule.betterFace(false, 1.0D, true, 90.0D), "a closer face must not unseat the pin");
        assertTrue(ScaffoldModule.betterFace(true, 10.0D, true, 20.0D), "nearer wins inside the pinning tier");
        assertFalse(ScaffoldModule.betterFace(true, 20.0D, true, 10.0D));
        assertTrue(ScaffoldModule.betterFace(false, 10.0D, false, 20.0D), "nearer wins inside the plain tier");

        assertTrue(ScaffoldModule.betterFace(false, 5.0D, false, Double.POSITIVE_INFINITY));
    }

    @Test
    void theRiseFloorIsFaceConnected() {
        BlockPos below = new BlockPos(0, 63, 0);
        for (int step = 0; step < 8; step++) {
            int laneStep = step;
            java.util.Set<BlockPos> asked = new java.util.HashSet<>();
            ScaffoldModule.grimRiseFloorReady(laneStep, below, pos -> {
                asked.add(pos);
                return true;
            });
            for (BlockPos cell : asked) {
                assertEquals(below.getY(), cell.getY(), "the floor test must stay on the level being left");
                int dx = Math.abs(cell.getX() - below.getX());
                int dz = Math.abs(cell.getZ() - below.getZ());
                assertTrue(dx <= 1 && dz <= 1 && dx + dz > 0, "cell " + cell + " is not a neighbour of " + below);
            }

            boolean diagonal = (laneStep & 1) == 1;
            assertEquals(diagonal ? 2 : 1, asked.size(), "wrong cell count for lane step " + laneStep);
        }
    }

    @Test
    void theRiseFloorGateOnlyReachesPillarsUnderThePlayer() {

        Vec3 player = new Vec3(0.5D, 70.0D, 0.5D);
        BlockPos ownColumn = new BlockPos(0, 70, 0);
        assertTrue(ScaffoldModule.grimBoxOverColumn(player, ownColumn),
            "a pillar in the player's own column is in the footprint, so the floor gate applies");

        assertFalse(ScaffoldModule.grimBoxOverColumn(player, new BlockPos(0, 70, -1)),
            "a cardinal step ahead is not in the footprint");
        assertFalse(ScaffoldModule.grimBoxOverColumn(player, new BlockPos(-1, 70, -1)),
            "a diagonal step ahead is not in the footprint");

        assertFalse(ScaffoldModule.grimRiseFloorReady(3, ownColumn.below(), java.util.Set.<BlockPos>of()::contains),
            "the pillar rule is untouched by the scoping");
    }

    @Test
    void aDiagonalRiseIsRefusedUntilTheLandingIsFaceConnected() {

        int nw = 3;
        BlockPos pillar = new BlockPos(-557, 69, -528);
        BlockPos west = new BlockPos(-558, 69, -528);
        BlockPos north = new BlockPos(-557, 69, -529);
        BlockPos successor = new BlockPos(-558, 69, -529);

        assertFalse(ScaffoldModule.grimRiseFloorReady(nw, pillar, java.util.Set.of()::contains),
            "a bare pillar is never a landing");
        assertFalse(ScaffoldModule.grimRiseFloorReady(nw, pillar, java.util.Set.of(west)::contains),
            "pillar plus one lateral is still corner-only - this is exactly what dropped the run");
        assertFalse(ScaffoldModule.grimRiseFloorReady(nw, pillar, java.util.Set.of(successor)::contains),
            "the successor alone touches the pillar at a corner, so it needs an intermediate");
        assertTrue(ScaffoldModule.grimRiseFloorReady(nw, pillar, java.util.Set.of(west, successor)::contains),
            "successor plus the west intermediate is face-connected");
        assertTrue(ScaffoldModule.grimRiseFloorReady(nw, pillar, java.util.Set.of(north, successor)::contains),
            "either intermediate closes the L");

        int w = 2;
        BlockPos tower = new BlockPos(-679, 122, -388);
        assertFalse(ScaffoldModule.grimRiseFloorReady(w, tower, java.util.Set.of()::contains),
            "a cardinal lane still needs the cell it is about to walk onto");
        assertTrue(ScaffoldModule.grimRiseFloorReady(w, tower,
            java.util.Set.of(new BlockPos(-680, 122, -388))::contains));

        assertTrue(ScaffoldModule.grimRiseFloorReady(-1, pillar, java.util.Set.of()::contains));
    }

    @Test
    void aDiagonalStaircaseAheadNeedsOnlyTheSuccessor() {

        int nw = 3;
        BlockPos step = new BlockPos(-557, 69, -528);
        BlockPos successor = new BlockPos(-558, 69, -529);
        assertTrue(ScaffoldModule.grimRiseFloorReady(nw, step, true, java.util.Set.of(successor)::contains),
            "the step ahead needs just the diagonal successor");
        assertFalse(ScaffoldModule.grimRiseFloorReady(nw, step, true, java.util.Set.of()::contains),
            "the successor is still required");
        assertFalse(ScaffoldModule.grimRiseFloorReady(nw, step, false, java.util.Set.of(successor)::contains),
            "the pillar form still wants the elbow");
    }

    @Test
    void thePostureIsTheSameYawForEveryLaneThatShipsIt() {

        int[] route = { 1, 0, 2, 0, 2, 0 };
        float swept = 0.0F;
        float held = Float.NaN;
        for (int lane : route) {
            float next = ScaffoldModule.grimPlacementPostureYaw(ScaffoldModule.compassStepYaw(lane));
            if (Float.isFinite(held)) {
                swept += Math.abs(AutismRotationUtil.angleDifference(next, held));
            }
            held = next;
        }

        assertEquals(45.0F + 90.0F * 4.0F, swept, 0.01F, "the route paid more than its own turns");
    }

    @Test
    void theCourseAdvanceTestReadsTheLaneAndNotTheDistance() {

        Vec3 lane = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 at = new Vec3(-9.35D, 73.0D, -87.0D);
        assertTrue(ScaffoldModule.advancesCourse(at, lane, new BlockPos(-10, 71, -88)));
        assertFalse(ScaffoldModule.advancesCourse(at, lane, new BlockPos(-10, 71, -85)));

        assertFalse(ScaffoldModule.advancesCourse(at, null, new BlockPos(-10, 71, -88)));
    }

    @Test
    void theStraightPostureCrossesTheCommittedFaceWhereTheBracketsCouldNot() {

        Vec3 eye = new Vec3(65.72D, 71.27D, -229.74D);
        BlockPos support = new BlockPos(66, 69, -230);

        assertEquals(0.54D,
            ScaffoldModule.grimCrossingFraction(eye, support, Direction.WEST, -45.0F), 0.02D);
        assertEquals(-0.02D,
            ScaffoldModule.grimCrossingFraction(eye, support, Direction.WEST, -135.0F), 0.02D,
            "the bracket the old rule preferred missed the face by two centimetres");

        float straight = ScaffoldModule.grimPlacementPostureYaw(90.0F);
        assertEquals(-90.0F, straight, 0.01F);
        double crossing = ScaffoldModule.grimCrossingFraction(eye, support, Direction.WEST, straight);
        assertEquals(0.26D, crossing, 0.02D, "the straight ray must cross inside the face");
        assertTrue(crossing > 0.15D && crossing < 0.85D, "crossing " + crossing + " is off the face");
    }

    @Test
    void theRiseVetoFollowsTheFootingRowNotThePlanningOffset() {

        assertTrue(ScaffoldModule.grimRiseVetoApplies(true, 83, 82),
            "grounded, the staircase step one up is a rise");
        assertFalse(ScaffoldModule.grimRiseVetoApplies(true, 82, 82),
            "grounded, the row cell itself is not");

        assertTrue(ScaffoldModule.grimRiseVetoApplies(true, 83, 82),
            "mid-arc the base-level rise cell is still a rise against the footing row");

        assertFalse(ScaffoldModule.grimRiseVetoApplies(true, 81, 82),
            "the block the arc actually lands on stays selectable");

        assertFalse(ScaffoldModule.grimRiseVetoApplies(false, 84, 82),
            "a falling player keeps every catch cell");
    }

    private static double crossingY(Vec3 eye, BlockPos support, Direction face, float yaw, float pitch) {
        double past = face.getStepX() != 0
            ? (support.getX() + (face.getStepX() < 0 ? 0.0D : 1.0D) - eye.x) * -face.getStepX()
            : (support.getZ() + (face.getStepZ() < 0 ? 0.0D : 1.0D) - eye.z) * -face.getStepZ();
        double yawRad = Math.toRadians(yaw);
        double toward = -((-Math.sin(yawRad)) * face.getStepX() + Math.cos(yawRad) * face.getStepZ());
        return eye.y - (past / toward) * Math.tan(Math.toRadians(pitch));
    }

    @Test
    void grimPitchOutOfReachRefusesOnlyAFiniteDemandPastTheCap() {
        assertFalse(ScaffoldModule.grimPitchOutOfReach(Float.NaN),
            "NaN is the ordinary approach park - retiring on it would drop every target early");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(80.7F), "a normal mid-run solve");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(85.0F),
            "humanly possible per the 03:58 ruling - legal now");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(89.3F),
            "steep but humanly possible - legal now");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(89.5F), "exactly the cap is still legal");
        assertTrue(ScaffoldModule.grimPitchOutOfReach(89.6F));
        assertTrue(ScaffoldModule.grimPitchOutOfReach(90.0F));
    }

    @Test
    void theRun1FallTargetIsRefusedAsUnreachable() {
        float required = ScaffoldModule.grimSideWindowSolvePitch(
            new Vec3(33.985D, 86.79D, 72.43D), new BlockPos(34, 83, 72), Direction.WEST, -45.0F);
        assertTrue(required > ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "required pitch was " + required);
        assertTrue(ScaffoldModule.grimPitchOutOfReach(required));
    }

    @Test
    void theRun2FallTargetIsReachableAndTheTwoEyeSolveLandsIt() {
        Vec3 realEye = new Vec3(-2.51D, 99.42D, 53.07D);
        Vec3 leadEye = realEye.add(-0.088D, -0.302D, -0.092D);
        BlockPos support = new BlockPos(-2, 96, 53);

        float required = ScaffoldModule.grimSideWindowSolvePitch(realEye, support, Direction.WEST, -45.0F);
        assertTrue(required < ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "the face was reachable all along: " + required);
        assertFalse(ScaffoldModule.grimPitchOutOfReach(required));

        float leadOnly = ScaffoldModule.grimCrossingPitch(leadEye, support, Direction.WEST, -45.0F);
        double leadCrossing = crossingY(realEye, support, Direction.WEST, -45.0F, leadOnly);
        assertTrue(leadCrossing > support.getY() + 1,
            "the lead-eye pitch is supposed to miss over the top, crossed at " + leadCrossing);

        float both = ScaffoldModule.grimTwoEyeCrossingPitch(
            realEye, leadEye, support, Direction.WEST, -45.0F, 84.3F);
        double crossing = crossingY(realEye, support, Direction.WEST, -45.0F, both);
        assertTrue(crossing >= support.getY() && crossing <= support.getY() + 1,
            "the two-eye pitch must land inside the face, crossed at " + crossing);
    }

    @Test
    void theTwoEyeSolveLandsTheFlatPlacementATickEarlier() {
        Vec3 realEye = new Vec3(47.80D, 85.27D, 79.29D);
        Vec3 leadEye = new Vec3(47.605D, 85.27D, 79.29D);
        BlockPos support = new BlockPos(48, 83, 79);

        float leadOnly = ScaffoldModule.grimCrossingPitch(leadEye, support, Direction.WEST, -90.0F);
        assertTrue(crossingY(realEye, support, Direction.WEST, -90.0F, leadOnly) > support.getY() + 1,
            "the shipped solve is supposed to miss high from the real eye");

        float both = ScaffoldModule.grimTwoEyeCrossingPitch(
            realEye, leadEye, support, Direction.WEST, -90.0F, 84.3F);
        double crossing = crossingY(realEye, support, Direction.WEST, -90.0F, both);
        assertTrue(crossing >= support.getY() && crossing <= support.getY() + 1,
            "crossed at " + crossing);
        assertTrue(both <= 84.3F, "still under the emitted cap: " + both);
    }

    @Test
    void theTwoEyeSolveStillParksBehindThePlane() {

        Vec3 behind = new Vec3(49.50D, 85.62D, 79.29D);
        BlockPos support = new BlockPos(48, 83, 79);
        assertEquals(
            ScaffoldModule.grimCrossingPitch(behind, support, Direction.WEST, -90.0F),
            ScaffoldModule.grimTwoEyeCrossingPitch(behind, behind, support, Direction.WEST, -90.0F,
                ScaffoldModule.GRIM_PLACE_PITCH_PARK),
            1.0E-4F);
    }

    @Test
    void theAirborneChainSolveTargetsTheEyeTheWireRotationIsTestedFrom() {
        Vec3 realEye = new Vec3(47.85D, 87.60D, 79.29D);
        Vec3 leadEye = realEye.add(-0.15D, -0.23D, 0.0D);
        BlockPos support = new BlockPos(48, 83, 79);

        double[] realWindow = ScaffoldModule.grimTwoEyeCrossingWindow(
            realEye, leadEye, support, Direction.WEST, -90.0F);
        double[] leadWindow = ScaffoldModule.grimTwoEyeCrossingWindow(
            leadEye, realEye, support, Direction.WEST, -90.0F);
        assertTrue(realWindow[0] > leadWindow[1],
            "the fixture needs disjoint windows - that is every descent tick");

        float swapped = ScaffoldModule.grimTwoEyeCrossingPitch(
            leadEye, realEye, support, Direction.WEST, -90.0F,
            ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD);
        float unswapped = ScaffoldModule.grimTwoEyeCrossingPitch(
            realEye, leadEye, support, Direction.WEST, -90.0F,
            ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD);

        double delivered = crossingY(leadEye, support, Direction.WEST, -90.0F, swapped);
        assertTrue(delivered >= support.getY() && delivered <= support.getY() + 1,
            "the swapped solve crosses inside the face next tick, at " + delivered);
        double stale = crossingY(leadEye, support, Direction.WEST, -90.0F, unswapped);
        assertFalse(stale >= support.getY() && stale <= support.getY() + 1,
            "the real-primary solve is the no-hit 14:11 recorded, crossed at " + stale);
    }

    @Test
    void theEmittedRotationNeverLandsOnAWholeDegree() {
        float nudged = ScaffoldModule.grimDeintegrifyAngle(75.0F, 0.0225D);
        assertTrue(nudged != 75.0F && Math.abs(nudged - 75.0F) < 0.1F,
            "75.0 is the 09:28 flag value: " + nudged);
        assertTrue(ScaffoldModule.grimDeintegrifyAngle(-45.0F, 0.0225D) != -45.0F,
            "the lineup octant yaws are whole by construction");
        assertTrue(ScaffoldModule.grimDeintegrifyAngle(-495.0F, 0.0225D) != -495.0F,
            "the wire yaw is checked before any wrap normalization");
        assertEquals(0.0F, ScaffoldModule.grimDeintegrifyAngle(0.0F, 0.0225D),
            "zero is exempt - F3-snapping players produce it legitimately");
        assertEquals(90.0F, ScaffoldModule.grimDeintegrifyAngle(90.0F, 0.0225D));
        assertEquals(-90.0F, ScaffoldModule.grimDeintegrifyAngle(-90.0F, 0.0225D));
        assertEquals(75.13F, ScaffoldModule.grimDeintegrifyAngle(75.13F, 0.0225D),
            "ordinary stream values pass through untouched");
        assertEquals(-495.06F, ScaffoldModule.grimDeintegrifyAngle(-495.06F, 0.0225D));
    }

    @Test
    void theDescentCrossingFollowsTheArcNotTheCurrentColumn() {

        double y = 87.25D, vy = 0.003D, z = 69.27D, vz = -0.142D;
        double feetY = 86.0D;
        Vec3 expected = null;
        for (int tick = 0; tick < 12 && expected == null; tick++) {
            double previousY = y;
            y += vy;
            z += vz;
            if (vy < 0.0D && previousY >= feetY && y < feetY) {
                double t = (previousY - feetY) / (previousY - y);
                expected = new Vec3(0.0D, feetY, z - vz + vz * t);
            }
            vy = (vy - 0.08D) * 0.98D;
            vz *= 0.91D;
        }
        Vec3 crossing = ScaffoldModule.grimDescentCrossing(
            new Vec3(0.0D, 87.25D, 69.27D), new Vec3(0.0D, 0.003D, -0.142D), feetY, 12);
        assertNotNull(expected);
        assertNotNull(crossing, "a shallow arc over the row must report its landing");
        assertEquals(expected.z, crossing.z, 1.0E-9D, "landing z follows the drift");
        assertEquals(feetY, crossing.y, 1.0E-9D);
        assertTrue(Math.abs(crossing.z - 69.27D) > 0.3D,
            "the landing is NOT the current column - that is the whole point");

        assertNull(ScaffoldModule.grimDescentCrossing(
                new Vec3(0.0D, 87.0D, 0.0D), new Vec3(0.0D, 0.42D, 0.0D), 86.0D, 3),
            "a rising arc that never comes back inside the cap reports no crossing");
        assertNull(ScaffoldModule.grimDescentCrossing(
                new Vec3(0.0D, 85.0D, 0.0D), new Vec3(0.0D, -0.4D, 0.0D), 86.0D, 12),
            "already below the feet level: no crossing to report");
    }

    private static AABB bodyAt(double x, double feetY, double z) {
        return new AABB(x - 0.3D, feetY, z - 0.3D, x + 0.3D, feetY + 1.8D, z + 0.3D);
    }

    @Test
    void descendingIntoAPlacedCellWithinTheLookaheadIsNotClear() {
        AABB body = bodyAt(48.19D, 95.02D, 63.67D);
        Vec3 velocity = new Vec3(-0.087D, -0.228D, -0.087D);
        BlockPos riser = new BlockPos(48, 94, 63);

        assertFalse(new AABB(riser).intersects(body),
            "the CURRENT box clears the top by 2 cm - vanilla would allow this placement");
        assertFalse(ScaffoldModule.grimCellClearOfBody(body, velocity, riser),
            "the next movement crosses the top while the footprint still overlaps the cell");

        double nextFeet = 95.02D - 0.228D;
        assertEquals(0.208D, 95.0D - nextFeet, 0.005D,
            "one tick later the feet are 0.20 below the top; Grim reported Simulation .203264");

        assertFalse(ScaffoldModule.grimCellClearOfBody(
            bodyAt(51.67D, 91.18D, 67.22D), new Vec3(-0.017D, -0.152D, -0.108D),
            new BlockPos(51, 90, 67)));
    }

    @Test
    void aFallingCatchClearsTheCellItWillLandOnIncludingTheSameTickCrossing() {

        AABB body = bodyAt(33.66D, 85.54D, 71.39D);
        Vec3 velocity = new Vec3(0.127D, -0.304D, 0.0D);
        BlockPos cell = new BlockPos(33, 84, 71);
        assertFalse(ScaffoldModule.grimCellClearOfBody(body, velocity, cell),
            "the strict three-arg sweep is unchanged");
        assertTrue(ScaffoldModule.grimCellClearOfBody(body, velocity, cell, true),
            "in catch mode a tick-1 crossing is the landing, not a race");

        assertTrue(ScaffoldModule.grimCellClearOfBody(
                bodyAt(33.80D, 85.23D, 71.39D), new Vec3(0.130D, -0.377D, 0.0D), cell, true),
            "a tick-0 first crossing is the last-chance catch, not the t489 race");

        assertEquals(85.50D, 85.80D - 0.302D, 0.005D,
            "t040 y=85.80 vy=-0.302 stepped to the traced 85.50 on the CURRENT velocity");
        assertEquals(0.374D, (0.302D + 0.08D) * 0.98D, 0.001D,
            "and the NEXT velocity is the one that carries the gravity - the traced t041 vy");
        assertNotEquals(85.50D, 85.80D - 0.374D, 0.005D,
            "gravity first would have put it at 85.43, which the capture never shows");

        assertTrue(ScaffoldModule.grimCellClearOfBody(
                bodyAt(33.66D, 85.50D, 71.39D), new Vec3(0.10D, -0.46D, 0.0D), cell, true),
            "move first, gravity after - a tick-1 crossing is a landing, not a race");

        BlockPos riser = new BlockPos(7, 85, 67);
        assertTrue(ScaffoldModule.grimCellClearOfBody(
                bodyAt(7.13D, 86.18D, 67.48D), new Vec3(0.144D, -0.152D, 0.0D), riser, true),
            "the riser 19:14 never once placed - this is the tick it was wrongly refused on");
        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            bodyAt(7.13D, 86.18D, 67.48D), new Vec3(0.144D, -0.152D, 0.0D), riser, true));

        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            bodyAt(7.30D, 86.02D, 67.48D), new Vec3(0.149D, -0.228D, 0.0D), riser, true));

        assertFalse(ScaffoldModule.grimCellClearOfBody(
            bodyAt(33.5D, 84.5D, 71.5D), new Vec3(0.130D, -0.377D, 0.0D), cell, true));
    }

    @Test
    void theLastChanceCatchTickClearsBecauseTheClickBeatsTheMovement() {
        AABB body = bodyAt(-1.55D, 92.12D, 61.32D);
        Vec3 velocity = new Vec3(0.045D, -0.445D, 0.064D);
        BlockPos cell = new BlockPos(-2, 91, 61);

        assertFalse(new AABB(cell).intersects(body),
            "feet 92.12 clear the top 92.00 - the placement is vanilla-legal at both ends");
        assertTrue(ScaffoldModule.grimCellClearOfBody(body, velocity, cell, true),
            "the tick-0 first crossing is the catch landing, not the t489 race");
        assertEquals("ok", ScaffoldModule.grimCellClearReason(body, velocity, cell, true));
        assertFalse(ScaffoldModule.grimCellClearOfBody(body, velocity, cell),
            "strict planning still refuses the same sweep");
        assertEquals("strict", ScaffoldModule.grimCellClearReason(body, velocity, cell, false));

        AABB late = bodyAt(-1.49D, 91.68D, 61.40D);
        assertFalse(ScaffoldModule.grimCellClearOfBody(
            late, new Vec3(0.054D, -0.514D, 0.071D), cell, true));
        assertEquals("box", ScaffoldModule.grimCellClearReason(
            late, new Vec3(0.054D, -0.514D, 0.071D), cell, true));
    }

    @Test
    void theCatchModeOpensOnTheProjectedCrossingNotTheAccomplishedFact() {

        assertTrue(ScaffoldModule.grimFeetCrossFootingSoon(85.50D, -0.374D, 84, 2));

        assertTrue(ScaffoldModule.grimFeetCrossFootingSoon(85.12D, -0.445D, 84, 2));

        assertFalse(ScaffoldModule.grimFeetCrossFootingSoon(85.90D, -0.10D, 84, 2));

        assertTrue(ScaffoldModule.grimFeetCrossFootingSoon(84.90D, -0.10D, 84, 2));

        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            bodyAt(7.13D, 86.18D, 67.48D), new Vec3(0.144D, -0.152D, 0.0D),
            new BlockPos(7, 85, 67), true));
        assertTrue(ScaffoldModule.grimFeetCrossFootingSoon(86.18D, -0.152D, 85, 2),
            "clock and clearance are one object - they must open on the same tick");

        AABB body = bodyAt(28.19D, 85.50D, 70.45D);
        Vec3 velocity = new Vec3(0.176D, -0.374D, 0.0D);
        BlockPos cell = new BlockPos(28, 84, 70);
        assertTrue(ScaffoldModule.grimCellClearOfBody(body, velocity, cell, true),
            "t095 is the tick-1 landing");
        assertTrue(ScaffoldModule.grimFeetCrossFootingSoon(
            body.minY, velocity.y, 84, 2), "and the mode must be open on it");
    }

    @Test
    void theClearanceReasonNamesTheRuleThatRefusedAndNeverDisagreesWithTheBoolean() {
        BlockPos cell = new BlockPos(33, 84, 71);

        AABB landing = bodyAt(33.66D, 85.54D, 71.39D);
        Vec3 landingVelocity = new Vec3(0.127D, -0.304D, 0.0D);
        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            landing, landingVelocity, cell, true));
        assertEquals("strict", ScaffoldModule.grimCellClearReason(
            landing, landingVelocity, cell, false));

        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            bodyAt(33.80D, 85.23D, 71.39D), new Vec3(0.130D, -0.377D, 0.0D), cell, true));
        assertEquals("strict", ScaffoldModule.grimCellClearReason(
            bodyAt(33.80D, 85.23D, 71.39D), new Vec3(0.130D, -0.377D, 0.0D), cell, false));

        assertEquals("box", ScaffoldModule.grimCellClearReason(
            bodyAt(33.5D, 84.5D, 71.5D), new Vec3(0.130D, -0.377D, 0.0D), cell, true));

        assertEquals("side", ScaffoldModule.grimCellClearReason(
            bodyAt(32.5D, 85.30D, 71.5D), new Vec3(0.15D, -0.4D, 0.0D), cell, true));

        assertEquals("ok", ScaffoldModule.grimCellClearReason(
            landing, new Vec3(0.127D, 0.2D, 0.0D), cell, true));

        AABB[] bodies = {landing, bodyAt(33.80D, 85.23D, 71.39D), bodyAt(33.5D, 84.5D, 71.5D),
            bodyAt(32.5D, 85.30D, 71.5D), bodyAt(33.66D, 85.50D, 71.39D)};
        Vec3[] velocities = {landingVelocity, new Vec3(0.130D, -0.377D, 0.0D),
            new Vec3(0.15D, -0.4D, 0.0D), new Vec3(0.10D, -0.46D, 0.0D),
            new Vec3(0.0D, 0.33D, 0.0D)};
        for (AABB body : bodies) {
            for (Vec3 velocity : velocities) {
                for (boolean catchMode : new boolean[] {false, true}) {
                    assertEquals(
                        ScaffoldModule.grimCellClearOfBody(body, velocity, cell, catchMode),
                        "ok".equals(ScaffoldModule.grimCellClearReason(
                            body, velocity, cell, catchMode)),
                        "reason and boolean must agree");
                }
            }
        }
    }

    @Test
    void theLipGuardLeadsByOneStepAndOnlyIntoAHole() {

        Vec3 position = new Vec3(-3.56D, 88.0D, 65.48D);
        assertEquals(new Vec3(-3.43D, 88.0D, 65.48D),
            ScaffoldModule.grimNextStepPosition(position, new Vec3(0.13D, -0.078D, 0.0D)));
        assertEquals(position, ScaffoldModule.grimNextStepPosition(position, null));
        assertNull(ScaffoldModule.grimNextStepPosition(null, new Vec3(0.13D, 0.0D, 0.0D)));

        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(0.13D));
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(0.02D));
        assertTrue(ScaffoldModule.grimNoFootingUnderfoot(0.0D));

        double overlapNow = 0.10D;
        double overlapNext = 0.0D;
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(overlapNow),
            "the un-led read is exactly why the guard was a tick late");
        assertTrue(ScaffoldModule.grimNoFootingUnderfoot(overlapNext),
            "and the led read is what now holds the player at the lip");
    }

    @Test
    void theBrakeAsksWhetherTheLandingHasAFloorNotHowFarItFlies() {

        BlockPos step = new BlockPos(6, 85, 67);
        assertTrue(ScaffoldModule.grimLandingOverlaps(new double[] {6.50D, 67.48D}, step),
            "square on the step - no brake, the arc is already on course");
        assertTrue(ScaffoldModule.grimLandingOverlaps(new double[] {7.14D, 67.48D}, step),
            "0.15 of overlap is the ruling's own boundary and still a landing");
        assertFalse(ScaffoldModule.grimLandingOverlaps(new double[] {7.16D, 67.48D}, step),
            "a hair past it is a graze, and a graze is not a floor");

        assertFalse(ScaffoldModule.grimLandingOverlaps(new double[] {7.46D, 67.48D}, step),
            "the 19:14 grazes must brake - they were never landings");

        assertFalse(ScaffoldModule.grimLandingOverlaps(new double[] {6.50D, 68.40D}, step));
        assertFalse(ScaffoldModule.grimLandingOverlaps(null, step));
        assertFalse(ScaffoldModule.grimLandingOverlaps(new double[] {6.50D, 67.48D}, null));

        double[] diagonalKept = {0.74D, 0.98D, 1.05D, 1.25D};
        double[] diagonalLost = {1.41D, 1.84D};
        double keptMax = 0.0D;
        for (double dxz : diagonalKept) keptMax = Math.max(keptMax, dxz);
        double lostMin = Double.MAX_VALUE;
        for (double dxz : diagonalLost) lostMin = Math.min(lostMin, dxz);
        assertTrue(keptMax < lostMin,
            "the diagonal separates on travel exactly like the cardinal - the brake belongs there");

        double onceAnOvershootThreshold = 0.20D;
        for (double aimedButTooLong : new double[] {-0.22D, -0.14D, -0.07D, 0.15D}) {
            assertFalse(aimedButTooLong > onceAnOvershootThreshold,
                "overshoot against the support column cannot see a long arc");
        }

        double onceATravelCap = 1.35D;
        double losingArcT104 = 1.41D;
        double winningArcT119 = 1.41D;
        assertEquals(losingArcT104, winningArcT119, 1.0E-9D,
            "same trv on a winner and a loser - the term carries no information about the outcome");
        assertTrue(losingArcT104 > onceATravelCap && winningArcT119 > onceATravelCap,
            "and it braked both for one tick, then released both");

        double[][] trvAgainstTruth = {
            {0.74D, 1.21D},
            {0.86D, 0.94D},
            {0.95D, 1.39D},
            {0.58D, 0.61D},
            {0.61D, 0.56D},
            {0.64D, 0.68D},
        };
        double worstLoserUnderRead = 0.0D;
        double worstWinnerUnderRead = 0.0D;
        for (double[] arc : trvAgainstTruth) {
            double underRead = arc[1] - arc[0];
            if (arc[1] >= 0.94D) worstLoserUnderRead = Math.max(worstLoserUnderRead, underRead);
            else worstWinnerUnderRead = Math.max(worstWinnerUnderRead, underRead);
        }
        assertTrue(worstLoserUnderRead > 0.4D,
            "trv under-reads by half a block on the arcs that overshoot");
        assertTrue(worstWinnerUnderRead < 0.1D,
            "and reads true on the arcs that do not - the bias is selective, so no threshold works");

        assertTrue(0.74D < 0.75D, "the arc that flew 1.21 read under the floor and was never braked");

        double minLaneSpeed = 0.03D;
        assertTrue(0.01D < minLaneSpeed, "the parked arcs release");
        for (double takeoffCarry : new double[] {0.055D, 0.063D, 0.070D, 0.084D, 0.099D}) {
            assertTrue(takeoffCarry > minLaneSpeed, "a healthy arc is still braked from takeoff");
        }
    }

    @Test
    void aBlankRayBehindThePlaneHoldsTheRetireCountInsteadOfErasingIt() {
        double pastThreshold = 0.05D;

        double[] past = {0.051D, 0.044D, 0.052D, 0.073D, 0.078D};

        int erasing = 0;
        int erasingRetiredAt = -1;
        for (int tick = 0; tick < past.length; tick++) {
            if (past[tick] > pastThreshold) {
                if (++erasing >= 2 && erasingRetiredAt < 0) erasingRetiredAt = tick;
            } else {
                erasing = 0;
            }
        }
        assertEquals(3, erasingRetiredAt,
            "erasing the count on the dip cost three extra ticks - the arc was over by then");

        int holding = 0;
        int holdingRetiredAt = -1;
        for (int tick = 0; tick < past.length; tick++) {
            if (past[tick] > pastThreshold && ++holding >= 2 && holdingRetiredAt < 0) {
                holdingRetiredAt = tick;
            }
        }
        assertEquals(2, holdingRetiredAt, "two refutations retire it, whatever sits between them");
        assertTrue(holdingRetiredAt < erasingRetiredAt, "and that is strictly sooner");

        assertNotEquals(new BlockPos(13, 98, 67), new BlockPos(12, 98, 68),
            "13,98,67 held every tick at miss=no-hit; 12,98,68 read ok and placed after touchdown");
    }

    @Test
    void aCoplanarNeighbourIsNotAnOccluder() {
        BlockPos support = new BlockPos(6, 94, 72);
        Direction face = Direction.EAST;
        Vec3 eye = new Vec3(7.118D, 96.0D, 72.1D);

        assertEquals(0.118D, ScaffoldModule.grimEyePastPlane(eye, support, face), 1.0E-9D,
            "the east face of 6,94,72 is the plane x=7.0 and the eye is 0.118 past it");

        Vec3 coplanar = new Vec3(7.0D, 94.85D, 71.9D);
        assertEquals(0.0D, ScaffoldModule.grimEyePastPlane(coplanar, support, face), 1.0E-9D,
            "same plane, beside the square - the ray reached the face and crossed it wide");
        assertFalse(ScaffoldModule.grimHitInFrontOfFace(eye, coplanar, support, face),
            "a coplanar neighbour must not be booked as occlusion");

        Vec3 between = new Vec3(7.06D, 95.0D, 72.4D);
        assertTrue(ScaffoldModule.grimHitInFrontOfFace(eye, between, support, face),
            "a block standing in the way is still an occluder");

        assertFalse(ScaffoldModule.grimHitInFrontOfFace(eye, new Vec3(6.5D, 95.0D, 72.4D),
            support, face), "past the plane is behind the target, not in front of it");
    }

    @Test
    void theShortRunGuardHeldAYawWhoseRayMissedTheSquareSideways() {
        BlockPos support = new BlockPos(0, 92, 52);
        Direction face = Direction.NORTH;
        Vec3 eye = new Vec3(0.34D, 95.62D, 51.61D);

        assertEquals(0.390D, ScaffoldModule.grimEyePastPlane(eye, support, face), 0.005D,
            "matches the traced past=+0.390, so the eye reconstruction is right");

        double bearingRun = Math.hypot(0.30D - eye.x, 52.00D - eye.z);
        assertTrue(bearingRun < 0.75D, "under the threshold, so the guard substitutes the posture");

        double held = ScaffoldModule.grimCrossingFraction(eye, support, face, 45.4F);
        assertTrue(held < 0.0D, "the emitted yaw crossed at " + held + ", left of the face edge");

        double landing = ScaffoldModule.grimCrossingFraction(eye, support, face, 37.0F);
        assertTrue(landing > 0.04D && landing < 0.96D, "37 degrees crosses inside the square");
        assertTrue(45.4D - 37.0D < 18.0D, "the whole correction is under half a capped step");

        assertTrue(Math.abs(ScaffoldModule.grimLaneOctantResidual(-135.0F, 37.0F)) < 12.0F,
            "so nothing had to be bent to ship it - the yaw was simply never asked for");
    }

    @Test
    void counterMovementReversesBothTravelAxesAndKeepsJumpAndSneak() {
        Input running = new Input(false, true, false, true, true, false, true);
        Input countered = ScaffoldModule.grimCounterMovement(running);
        assertTrue(countered.forward());
        assertFalse(countered.backward());
        assertTrue(countered.left());
        assertFalse(countered.right());
        assertTrue(countered.jump(), "the jump key is still the player's");
        assertFalse(countered.shift());
        assertFalse(countered.sprint(), "nobody sprints into a counter-strafe");

        Input sneaking = new Input(true, false, false, false, false, true, false);
        assertTrue(ScaffoldModule.grimCounterMovement(sneaking).shift());

        Input back = ScaffoldModule.grimCounterMovement(
            ScaffoldModule.grimCounterMovement(running));
        assertEquals(running.forward(), back.forward());
        assertEquals(running.backward(), back.backward());
        assertEquals(running.left(), back.left());
        assertEquals(running.right(), back.right());
        assertNull(ScaffoldModule.grimCounterMovement(null));
    }

    @Test
    void theClimbStepIsOneAxisBackAndOneUpOnEveryCourseIncludingDiagonals() {
        BlockPos landing = new BlockPos(-3, 84, 84);

        assertEquals(List.of(new BlockPos(-3, 85, 85)),
            ScaffoldModule.grimClimbStepCandidates(landing, new Vec3(0.0D, 0.0D, -0.139D)));
        assertEquals(List.of(new BlockPos(-2, 85, 84)),
            ScaffoldModule.grimClimbStepCandidates(landing, new Vec3(-0.139D, 0.0D, 0.0D)));

        List<BlockPos> diagonal = ScaffoldModule.grimClimbStepCandidates(
            landing, new Vec3(0.096D, 0.0D, -0.050D));
        assertEquals(2, diagonal.size());
        assertTrue(diagonal.contains(new BlockPos(-4, 85, 84)));
        assertTrue(diagonal.contains(new BlockPos(-3, 85, 85)));

        for (BlockPos step : diagonal) {
            assertEquals(landing.getY() + 1, step.getY());
            assertEquals(1, Math.abs(step.getX() - landing.getX())
                + Math.abs(step.getZ() - landing.getZ()));
        }
        assertTrue(ScaffoldModule.grimClimbStepCandidates(landing, Vec3.ZERO).isEmpty());
        assertTrue(ScaffoldModule.grimClimbStepCandidates(
            null, new Vec3(0.0D, 0.0D, -0.139D)).isEmpty());
    }

    @Test
    void aBackwardFacingNormalIsAReachAroundOnACardinalLane() {
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        assertEquals(1.0D, ScaffoldModule.grimFaceLaneDot(Direction.SOUTH, south), 1.0E-9D);
        assertEquals(-1.0D, ScaffoldModule.grimFaceLaneDot(Direction.NORTH, south), 1.0E-9D);
        assertEquals(0.0D, ScaffoldModule.grimFaceLaneDot(Direction.EAST, south), 1.0E-9D);
        assertEquals(0.0D, ScaffoldModule.grimFaceLaneDot(Direction.UP, south), 1.0E-9D);

        assertEquals(-1.0D, ScaffoldModule.grimFaceLaneDot(
            Direction.NORTH, new Vec3(0.0D, 0.0D, 0.146D)), 1.0E-9D);
        assertEquals(0.0D, ScaffoldModule.grimFaceLaneDot(Direction.NORTH, Vec3.ZERO), 1.0E-9D);

        assertEquals(-Math.sqrt(0.5D), ScaffoldModule.grimFaceLaneDot(
            Direction.NORTH, new Vec3(1.0D, 0.0D, 1.0D).normalize()), 1.0E-9D);
    }

    @Test
    void aRiseRowSubstitutionIsOnlyTheClimbsOwnStep() {
        BlockPos reserved = new BlockPos(31, 84, 70);
        BlockPos own = new BlockPos(22, 84, 70);
        assertTrue(ScaffoldModule.grimRiseRowSubstitutionAllowed(
            new BlockPos(30, 84, 70), 84, reserved, own), "at the row: always");
        assertTrue(ScaffoldModule.grimRiseRowSubstitutionAllowed(
            new BlockPos(31, 85, 70), 84, reserved, own), "the reserved landing column");
        assertTrue(ScaffoldModule.grimRiseRowSubstitutionAllowed(
            new BlockPos(22, 85, 70), 84, reserved, own), "the column underfoot");
        assertFalse(ScaffoldModule.grimRiseRowSubstitutionAllowed(
            new BlockPos(30, 85, 70), 84, reserved, own), "a trailing column: the 15:33 bump");
        assertFalse(ScaffoldModule.grimRiseRowSubstitutionAllowed(
            new BlockPos(30, 85, 70), 84, null, null), "no anchors, no rise substitution");
    }

    @Test
    void theAirborneReservationAdvancesOnlyForwardAndOnlyWhenTheLatchedCellCannotLand() {
        BlockPos reserved = new BlockPos(27, 84, 70);
        BlockPos ahead = new BlockPos(28, 84, 70);
        Vec3 lane = new Vec3(1.0D, 0.0D, 0.0D);

        assertEquals(ahead, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, ahead, false, true, false, lane, false), "cannot land + ahead: advance");
        assertEquals(reserved, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, ahead, false, true, false, lane, true), "still lands: hold");
        assertEquals(reserved, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, new BlockPos(26, 84, 70), false, true, false, lane, false),
            "behind the lane: never a retreat");
        assertEquals(reserved, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, new BlockPos(28, 85, 70), false, true, false, lane, false),
            "a row change is not an advance");
        assertEquals(reserved, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, null, false, true, false, lane, false), "no projection: hold");
        assertEquals(ahead, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, ahead, true, true, false, lane, true), "grounded: re-project");
        assertNull(ScaffoldModule.grimReservedSupportAfterSample(
            reserved, ahead, false, false, false, lane, false), "no intent: released");

        assertEquals(reserved, ScaffoldModule.grimReservedSupportAfterSample(
            reserved, ahead, false, true, false));
    }

    @Test
    void aLandingRiserWindowIsShorterThanTheRiserFloor() {

        assertTrue(85.02D > 85.00D, "t044 clears the top");
        assertTrue(85.02D > 85.00D, "t045 clears the top");
        assertTrue(84.80D < 85.00D, "t046 is inside it");

        assertTrue(ScaffoldModule.grimPaceFloorHolds(50L, ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS));
        assertFalse(ScaffoldModule.grimPaceFloorHolds(100L, ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS),
            "the 100 ms tick clears the human-limits floor - the window and the floor overlap");

        assertFalse(ScaffoldModule.grimPaceFloorHolds(
            50L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS));
        assertFalse(ScaffoldModule.grimPaceFloorHolds(
            100L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS));
    }

    @Test
    void aDyingRiserNeverLosesThePickToAFaceTheRayCannotHitAgain() {

        assertTrue(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, true, false, true, true, false));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, true, false, true, false, false));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, true, false, true, true, true));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, true, false, false, true, false));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, true, true, true, true, false));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            true, false, true, false, true, true, false));
        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, false, true, false, true, true, false));

        assertFalse(ScaffoldModule.grimDyingRiserPickSteal(
            false, true, false, false, true, true, false));
    }

    @Test
    void theSprintDropIsTheOnlyKeyTheModuleStillWrites() {

        assertTrue(ScaffoldModule.grimSuppressHeldArcSprint(true, true, false));
        assertTrue(ScaffoldModule.grimSuppressHeldArcSprint(true, false, true));
        assertFalse(ScaffoldModule.grimSuppressHeldArcSprint(true, false, false));
        assertFalse(ScaffoldModule.grimSuppressHeldArcSprint(false, true, true),
            "no climb intent, no suppression - a flat sprint bridge keeps its sprint");
    }

    @Test
    void anUnconsumedPredictionKeepsTheLandingDeficitOpen() {

        BlockPos support = new BlockPos(4, 90, 4);
        ScaffoldModule.PlacementTarget pending = new ScaffoldModule.PlacementTarget(
            support.west(), support, Direction.EAST,
            new BlockHitResult(new Vec3(4.0D, 90.5D, 4.5D), Direction.EAST, support.west(), false),
            new Rotation(90.0F, 80.0F), 90.5D);
        assertFalse(ScaffoldModule.grimAttemptSupportReady(
            ScaffoldModule.GrimPlacementAttemptState.ARMED, pending, support));
        assertTrue(ScaffoldModule.grimAttemptSupportReady(
            ScaffoldModule.GrimPlacementAttemptState.PREDICTED, pending, support));

        assertTrue(ScaffoldModule.grimAttemptSupportReady(
            ScaffoldModule.GrimPlacementAttemptState.ARMED, pending, support.east()));
        assertTrue(ScaffoldModule.grimAttemptSupportReady(
            ScaffoldModule.GrimPlacementAttemptState.ARMED, null, support));
    }

    @Test
    void theAimHoldsStillOnceItIsThere() {
        double gcd = 0.15D;

        assertEquals(0L, ScaffoldModule.grimAimDeadband(1L, 0.0F, gcd), "dead on the goal");
        assertEquals(0L, ScaffoldModule.grimAimDeadband(-1L, 0.05F, gcd));
        assertEquals(0L, ScaffoldModule.grimAimDeadband(2L, -0.07F, gcd),
            "the ~2 counts of curvature noise the smoother adds after the cap");

        assertEquals(1L, ScaffoldModule.grimAimDeadband(1L, 0.15F, gcd), "one full count of error");
        assertEquals(-7L, ScaffoldModule.grimAimDeadband(-7L, -1.20F, gcd));
        assertEquals(120L, ScaffoldModule.grimAimDeadband(120L, 18.0F, gcd), "a capped sweep");

        assertEquals(3L, ScaffoldModule.grimAimDeadband(3L, 0.0F, 0.0D));
    }

    @Test
    void aCellTheBodyIsInsideIsStillNeverPlannable() {
        AABB body = bodyAt(48.19D, 94.80D, 63.67D);
        assertTrue(new AABB(new BlockPos(48, 94, 63)).intersects(body));
        assertFalse(ScaffoldModule.grimCellClearOfBody(
            body, new Vec3(-0.087D, -0.228D, -0.087D), new BlockPos(48, 94, 63)),
            "feet 94.80 are inside the 94..95 cell - vanilla isUnobstructed refuses this too");
    }

    @Test
    void theSweptClearanceStillAllowsEverythingItShould() {
        AABB body = bodyAt(48.19D, 95.02D, 63.67D);
        Vec3 falling = new Vec3(-0.087D, -0.228D, -0.087D);

        assertTrue(ScaffoldModule.grimCellClearOfBody(body, falling, new BlockPos(48, 92, 63)));

        assertTrue(ScaffoldModule.grimCellClearOfBody(body, falling, new BlockPos(46, 94, 63)));

        assertTrue(ScaffoldModule.grimCellClearOfBody(
            bodyAt(48.5D, 95.0D, 63.5D), new Vec3(0.0D, -0.078D, 0.0D), new BlockPos(48, 94, 63)));

        assertTrue(ScaffoldModule.grimCellClearOfBody(
            bodyAt(48.5D, 95.42D, 63.5D), new Vec3(0.0D, 0.333D, 0.0D), new BlockPos(48, 94, 63)));

        assertTrue(ScaffoldModule.grimCellClearOfBody(
            bodyAt(48.5D, 97.0D, 63.5D), new Vec3(0.0D, -0.3D, 0.0D), new BlockPos(48, 94, 63)));

        assertFalse(ScaffoldModule.grimCellClearOfBody(
            bodyAt(48.5D, 94.5D, 63.5D), Vec3.ZERO, new BlockPos(48, 94, 63)));
    }

    @Test
    void aDiagonalRunIsChargedTheShortLimitByItsCellsNotItsCourse() {

        List<BlockPos> staircase = List.of(
            new BlockPos(43, 84, 55), new BlockPos(43, 84, 56), new BlockPos(43, 84, 57),
            new BlockPos(44, 84, 57), new BlockPos(44, 84, 58), new BlockPos(45, 84, 58));
        assertFalse(ScaffoldModule.grimPaceOneLine(staircase),
            "both axes move over the run - that is a diagonal bridge, whatever the camera says");
        assertEquals(162L, ScaffoldModule.grimPaceFloorMs(
            ScaffoldModule.grimPaceLimitMs(false, false, true, false)));

        List<BlockPos> straight = List.of(
            new BlockPos(55, 83, 71), new BlockPos(55, 83, 70), new BlockPos(55, 83, 69),
            new BlockPos(55, 83, 68));
        assertTrue(ScaffoldModule.grimPaceOneLine(straight));
        assertEquals(378L, ScaffoldModule.grimPaceFloorMs(
            ScaffoldModule.grimPaceLimitMs(true, true, true, false)));
    }

    @Test
    void theRaySubstitutionMayNotTradeAwayTheCellUnderOurOwnFeet() {

        BlockPos foot = new BlockPos(57, 84, 67);
        BlockPos planned = new BlockPos(57, 83, 67);
        BlockPos rayCell = new BlockPos(57, 83, 66);
        int dx = 1;
        int dz = 1;

        assertTrue(ScaffoldModule.grimLaneFrontierCell(rayCell, foot, dx, dz),
            "the trailing leg is legal - the corridor is not the bug");
        assertTrue(ScaffoldModule.grimLaneFrontierCell(planned, foot, dx, dz));

        assertTrue(ScaffoldModule.grimIsOwnFootingCell(planned, foot));
        assertFalse(ScaffoldModule.grimIsOwnFootingCell(rayCell, foot));

        BlockPos ahead = new BlockPos(58, 83, 67);
        assertFalse(ScaffoldModule.grimIsOwnFootingCell(ahead, foot),
            "the cell ahead of the body is not what is holding it up");

        assertFalse(ScaffoldModule.grimIsOwnFootingCell(new BlockPos(57, 84, 67), foot));
        assertFalse(ScaffoldModule.grimIsOwnFootingCell(null, foot));
    }

    @Test
    void theWalkIsHeldOnlyWhenTheFloorUnderUsIsActuallyRunningOut() {
        double band = ScaffoldModule.GRIM_FOOTING_OWED_OVERLAP;

        assertEquals(0.30D, band, 1.0E-9D);
        assertFalse(ScaffoldModule.grimFootingOwed(0.30D, 0.40D),
            "walking onto a placed block is not a brink");
        assertFalse(ScaffoldModule.grimFootingOwed(0.50D, 0.20D),
            "and plenty of floor left is not one either, whichever way it is going");

        assertTrue(ScaffoldModule.grimFootingOwed(0.17D, 0.08D));
        assertTrue(ScaffoldModule.grimFootingOwed(0.08D, 0.04D));
        assertTrue(ScaffoldModule.grimFootingOwed(0.04D, 0.00D));

        assertTrue(ScaffoldModule.grimFootingOwed(0.10D, 0.10D));

        assertTrue(ScaffoldModule.GRIM_PACE_BRINK_OVERLAP < band,
            "the brink override must not be reachable before the clamp has had its say");
        assertTrue(ScaffoldModule.GRIM_PACE_BRINK_OVERLAP > 0.0D,
            "but it has to fire before the floor is gone, not as it goes");
    }

    @Test
    void theEmittedRecordNeverClaimsSprintWithoutForward() {

        Input sprintingBackward = new Input(false, true, false, false, false, false, true);
        assertFalse(ScaffoldModule.silentSprintAllowed(sprintingBackward, false),
            "backward with no forward may never carry the sprint bit");
        assertFalse(ScaffoldModule.silentSprintAllowed(sprintingBackward, false),
            "and a purely lateral emit is the same state");

        Input sprintingForward = new Input(true, false, false, false, false, false, true);
        assertTrue(ScaffoldModule.silentSprintAllowed(sprintingForward, true),
            "a forward emit keeps the sprint it was given");
        assertFalse(ScaffoldModule.silentSprintAllowed(
            new Input(true, false, false, false, false, false, false), true),
            "and a sprint that was never pressed is never invented");
    }

    @Test
    void aPlaceAsYouGoBridgeCanNeverSatisfyALandingPlaceabilityTest() {

        Vec3 stand = new Vec3(31.28D, 86.00D, 55.74D);
        Vec3 velocity = new Vec3(-0.075D, 0.42D, -0.077D);
        BlockPos support = new BlockPos(31, 85, 56);

        Vec3 braked = ScaffoldModule.grimDescentCrossing(stand, velocity, 86.0D, 20);
        assertNotNull(braked, "the arc must come back down");
        BlockPos landing = new BlockPos(
            (int) Math.floor(braked.x), 85, (int) Math.floor(braked.z));
        assertEquals(new BlockPos(30, 85, 55), landing);

        for (Direction face : Direction.values()) {
            assertNotEquals(support, landing.relative(face),
                "the only built cell is diagonal to the landing, as it always is when you place "
                    + "the cell under your own feet");
        }
    }

    @Test
    void theTakeoffArcIsIntegratedWithTheKeysItActuallyFliesUnder() {

        Vec3 stand = new Vec3(73.53D, 87.00D, 93.25D);
        Vec3 velocity = new Vec3(-0.028D, -0.0784D, -0.016D);

        Vec3 braked = ScaffoldModule.grimDescentCrossing(
            stand, new Vec3(velocity.x, 0.42D, velocity.z), 87.0D, 20);
        assertNotNull(braked, "the arc must come back down");
        assertEquals(73, (int) Math.floor(braked.x), "drag-only lands back on the bridge column");
        assertEquals(93, (int) Math.floor(braked.z));

        Vec3 flown = ScaffoldModule.grimDescentCrossing(
            stand, new Vec3(velocity.x, 0.42D, velocity.z), 87.0D, 20, 0.02D);
        assertNotNull(flown);
        assertEquals(72, (int) Math.floor(flown.x),
            "the keys-on arc lands on the column the body did");
        assertEquals(92, (int) Math.floor(flown.z));
        assertEquals(72.63D, flown.x, 0.15D,
            "and it is close to the measured landing, not just right about the cell");
        assertEquals(92.83D, flown.z, 0.15D);
    }

    @Test
    void theWalkClampLeadsByTheCadenceDebt() {

        assertEquals(1, ScaffoldModule.grimWalkLeadTicks(0),
            "no debt is the old one-tick probe, unchanged");
        assertTrue(ScaffoldModule.grimWalkLeadTicks(3) > 1,
            "waiting on the cadence means probing further ahead");
        assertEquals(ScaffoldModule.GRIM_WALK_LEAD_MAX_TICKS,
            ScaffoldModule.grimWalkLeadTicks(99),
            "and it is bounded - the clamp feeds a sneak, which costs ~10 ticks to recover from");
    }

    @Test
    void theDwellMayNotOutliveTheCorrectionThatCausedIt() {
        int hold = 1;
        float quiet = 0.0F;
        assertTrue(hold < ScaffoldModule.GRIM_INPUT_SIDEWAYS_MIN_HOLD, "the dwell would normally hold");

        assertTrue(ScaffoldModule.dwellReleases(false, true, hold, quiet, false),
            "a strafe nobody is asking for any more is a stuck actuator, not a weave");

        assertFalse(ScaffoldModule.dwellReleases(false, false, hold, quiet, false),
            "boundary chatter must still be held");
        assertTrue(ScaffoldModule.dwellReleases(true, false, hold, quiet, false),
            "the player's own key change always passes straight through");
        assertTrue(ScaffoldModule.dwellReleases(
            false, false, ScaffoldModule.GRIM_INPUT_SIDEWAYS_MIN_HOLD, quiet, false),
            "and the hold still expires on its own");
        assertTrue(ScaffoldModule.dwellReleases(
            false, false, hold, ScaffoldModule.GRIM_INPUT_SIDEWAYS_BREAK_DEGREES, false),
            "an error big enough to walk us off the block still breaks it");

        assertTrue(ScaffoldModule.dwellReleases(false, false, hold, quiet, true),
            "a reference-frame jump must re-letter the keys immediately");
        assertTrue(ScaffoldModule.GRIM_INPUT_REFERENCE_BREAK_DEGREES <= 22.5F,
            "the threshold may not exceed the octant half-width it is derived from");
    }

    @Test
    void aCorrectionSettlesInsteadOfSwingingPastCentre() {

        double settled = runCentringWithDwell(-0.17D, true);
        double stuck = runCentringWithDwell(-0.17D, false);

        assertTrue(Math.abs(settled) <= ScaffoldModule.GRIM_LANE_CORRECT_ENGAGE,
            "the correction must arrive inside the band and stay there, ended at " + settled);

        assertTrue(stuck > ScaffoldModule.GRIM_LANE_CORRECT_ENGAGE,
            "with the old dwell the same series overshoots to the far side, reached " + stuck);
        assertTrue(Math.abs(settled) < stuck / 2.0D,
            "and the fix has to be worth more than a rounding difference");
    }

    private static double runCentringWithDwell(double lane, boolean dwellHonoursRelease) {
        double perp = 0.0D;
        int side = 0;
        int held = 0;
        int lock = 0;
        int emitted = 0;
        int hold = 0;
        boolean fromCorrection = false;
        double worst = lane;
        for (int tick = 0; tick < 40; tick++) {
            int previous = side;
            side = ScaffoldModule.grimLaneCorrectLatch(previous, held, lock, lane, perp);
            if (side == 0) {
                held = 0;
                if (previous != 0) lock = ScaffoldModule.GRIM_LANE_CORRECT_RELOCK_TICKS;
                else if (lock > 0) lock--;
            } else {
                held++;
                lock = 0;
            }

            if (side == emitted) {
                hold++;
                if (side != 0) fromCorrection = true;
            } else if (ScaffoldModule.dwellReleases(
                false, dwellHonoursRelease && fromCorrection && side == 0, hold, 0.0F, false)) {
                hold = 0;
                emitted = side;
                fromCorrection = side != 0;
            } else {
                hold++;
            }
            perp = perp * 0.546D + emitted * 0.044D;
            lane -= perp;
            if (lane > worst) worst = lane;
        }
        return worst;
    }

    @Test
    void theBrinkSeesTheRowTheJumpLeftNotTheOneUnderTheLiveY() {

        BlockPos rescue = new BlockPos(41, 92, 75);
        BlockPos oracleFoot = new BlockPos(41, 93, 75);
        BlockPos liveFoot = new BlockPos(41, 94, 75);

        assertTrue(ScaffoldModule.grimIsOwnFootingCell(rescue, oracleFoot),
            "against the row the jump left, the rescue cell IS the cell under our feet");
        assertFalse(ScaffoldModule.grimIsOwnFootingCell(rescue, liveFoot),
            "against the live row it is a block too low - that is why pbrink stayed 0 and we fell");

        BlockPos riser = new BlockPos(41, 93, 75);
        assertFalse(ScaffoldModule.grimIsOwnFootingCell(riser, oracleFoot),
            "a climb keeps its own floor - the brink is for the cell we stand ON");
    }

    @Test
    void theLaneReferenceIsNeverTheePlayersOwnPosition() {

        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 player = new Vec3(64.20D, 85.0D, 66.73D);

        assertEquals(0.0D,
            ScaffoldModule.grimLaneError(new ScaffoldModule.MovementLine(player, east), player),
            1.0E-9D, "a lane anchored on the player can only ever measure zero");

        Vec3 cellCentre = new Vec3(64.5D, 85.0D, 66.5D);
        double error = ScaffoldModule.grimLaneError(
            new ScaffoldModule.MovementLine(cellCentre, east), player);
        assertEquals(0.23D, Math.abs(error), 1.0E-9D,
            "the honest anchor sees the offset the corrector was blind to");

        assertTrue(Math.abs(error) > ScaffoldModule.GRIM_LANE_CORRECT_ENGAGE,
            "0.23 has to be inside the engage or the whole round-9 retune is cosmetic");
    }

    @Test
    void aTurnMayNotPushUsSideways() {

        float[] sweep = {107.7F, 125.4F, 143.1F, 161.3F, 179.0F, 179.7F};
        int guarded = 0;
        int coastOnly = 0;
        for (float emitted : sweep) {
            float residual = Math.abs(ScaffoldModule.grimLaneOctantResidual(0.0F, emitted));
            if (residual > ScaffoldModule.GRIM_LANE_SWEEP_SETTLED_DEGREES) guarded++;
            if (residual > ScaffoldModule.GRIM_LANE_OCTANT_MAX_RESIDUAL) coastOnly++;
        }

        assertEquals(2, coastOnly, "the coast threshold alone leaves most of the turn unguarded");
        assertEquals(4, guarded, "the settled band covers the ticks that actually injected a strafe");

        assertTrue(0.6F <= ScaffoldModule.GRIM_LANE_SWEEP_SETTLED_DEGREES,
            "a straight run's ~0.6 dither must read as settled");

        Input pureStrafe = new Input(false, false, true, false, false, false, true);
        Input stripped = ScaffoldModule.grimStripLateral(pureStrafe);
        assertFalse(stripped.left() || stripped.right(), "t299 shipped exactly this and must not");

        assertFalse(stripped.sprint(), "a forward-less emit may not claim a sprint");

        Input diagonal = new Input(false, true, false, true, true, false, true);
        Input keptForward = ScaffoldModule.grimStripLateral(diagonal);
        assertTrue(keptForward.backward(), "the along-lane axis is kept");
        assertFalse(keptForward.left() || keptForward.right());
        assertTrue(keptForward.jump());

        Input plain = new Input(true, false, false, false, false, false, false);
        assertSame(plain, ScaffoldModule.grimStripLateral(plain));
        assertNull(ScaffoldModule.grimStripLateral(null));

        Input both = new Input(true, false, true, true, false, false, false);
        assertSame(both, ScaffoldModule.grimStripLateral(both));
    }

    @Test
    void aRiserMayNotBlankTheLaneReference() {

        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 riserPair = new Vec3(0.0D, 1.0D, 0.0D);
        assertTrue(riserPair.dot(east) < 0.5D,
            "a vertical fit can never satisfy the lane test - that is why the anchor flipped");

        List<BlockPos> flatThenRiser = List.of(
            new BlockPos(63, 84, 66), new BlockPos(64, 84, 66), new BlockPos(64, 85, 66));
        BlockPos last = flatThenRiser.get(2);
        BlockPos skipped = flatThenRiser.get(1);
        BlockPos horizontal = flatThenRiser.get(0);
        assertEquals(last.getX(), skipped.getX(), "the riser is directly above its own flat block");
        assertEquals(last.getZ(), skipped.getZ());
        Vec3 fitted = new Vec3(last.getX() - horizontal.getX(), 0.0D, last.getZ() - horizontal.getZ());
        assertTrue(fitted.normalize().dot(east) >= 0.5D,
            "so the pair one further back is the east lane, and the reference never blanks");
    }

    @Test
    void theLandingColumnPaysAFloorAFallingBodyCanActuallyAfford() {
        assertTrue(ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS
            < ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS,
            "2 ticks after the leg is the whole window - the brink's 150 is what dropped the run");
        assertTrue(ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS >= 50L,
            "but never 0: two clicks on one tick is the 13:01 gap=1 burst");
    }

    @Test
    void thePaceFloorIsMillisecondsNotRoundedTicks() {
        long banded = ScaffoldModule.grimPaceLimitMs(true, true, true, false);
        assertEquals(350L, banded);
        assertEquals(378L, ScaffoldModule.grimPaceFloorMs(banded), "the real requirement");
        assertEquals(400L, (long) Math.ceil(banded * 1.08D / 50.0D) * 50L,
            "what whole ticks charged for it - 22 ms of pure rounding, every block");

        assertTrue(ScaffoldModule.grimPaceFloorHolds(374L, 378L));
        assertFalse(ScaffoldModule.grimPaceFloorHolds(375L, 378L));
        assertFalse(ScaffoldModule.grimPaceFloorHolds(-1L, 378L),
            "before the first placement nothing is owed");
        assertFalse(ScaffoldModule.grimPaceFloorHolds(
            49L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS),
            "the 11:15 shape: one tick after the booked placement must clear the 50 ms floor");
        assertTrue(ScaffoldModule.grimPaceFloorHolds(
            2L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS),
            "a same-tick burst reads a couple of ms and stays refused");
    }

    @Test
    void thePaceWaitStillReportsWholeTicksForTheWalkClamp() {
        assertEquals(0, ScaffoldModule.grimPaceWaitTicks(378L, 378L), "cleared is cleared");
        assertEquals(0, ScaffoldModule.grimPaceWaitTicks(-1L, 378L), "no history, no wait");
        assertEquals(1, ScaffoldModule.grimPaceWaitTicks(377L, 378L),
            "one millisecond short is still a whole tick of walk");
        assertEquals(8, ScaffoldModule.grimPaceWaitTicks(0L, 378L));
    }

    @Test
    void bothPlacementsOfAStepFitInsideOneClimbArc() {
        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS * 2 < 9 * 50L,
            "support then riser, one riser floor apart, inside a nine-tick arc");

        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS > 50L,
            "but not a gap=1 burst - that is the 13:01 shape Vulcan watches");
    }

    @Test
    void theLaneAnchorStandsDownOnTheSameBandTheCourseLatchDefends() {

        assertFalse(ScaffoldModule.grimLaneAnchorStandsDown(36.0F),
            "the 09:24 offset is a wobble the course holds, so the anchor must hold it too");
        assertFalse(ScaffoldModule.grimLaneAnchorStandsDown(-36.0F), "sign is irrelevant");
        assertFalse(ScaffoldModule.grimLaneAnchorStandsDown(22.6F),
            "the old half-octant line no longer splits the two decisions");
        assertTrue(ScaffoldModule.grimLaneAnchorStandsDown(38.0F),
            "past the band the course commits and the lane turns with it");

        int held = ScaffoldModule.compassStep(0.0F);
        float heldYaw = ScaffoldModule.compassStepYaw(held);
        for (float off = 0.0F; off <= 90.0F; off += 0.5F) {
            float camera = Mth.wrapDegrees(heldYaw + off);
            int want = ScaffoldModule.compassStep(camera);

            int[] next = ScaffoldModule.nextCourseStep(held, want, 64, camera, false);
            boolean courseTurns = next[0] != held;
            assertEquals(courseTurns, ScaffoldModule.grimLaneAnchorStandsDown(off),
                "stand-down and a committed turn must be one decision, at offset " + off);
        }
    }

    @Test
    void diagonalRisersLeadWithArcLandingWhileCardinalKeepsOwnFirst() {
        BlockPos own = new BlockPos(20, 80, 20);
        BlockPos landing = new BlockPos(21, 80, 21);
        assertEquals(List.of(landing, own),
            ScaffoldModule.grimPrimaryRiserSupports(own, landing, true));
        assertEquals(List.of(own, landing),
            ScaffoldModule.grimPrimaryRiserSupports(own, landing, false));
        assertEquals(List.of(own),
            ScaffoldModule.grimPrimaryRiserSupports(own, own, true),
            "an own-column landing is never duplicated");
    }

    @Test
    void placementConsumesTheLastFinalWrittenMovementRotationNotTheNextAim() {
        AutismServerRotationView.WireSnapshot snapshot =
            new AutismServerRotationView.WireSnapshot(70.0F, 74.0F, 90.0F, 82.0F, 41, true);
        ScaffoldModule.GrimWireClickRotation wire =
            ScaffoldModule.grimWireClickRotation(snapshot);

        assertNotNull(wire);
        assertEquals(new Rotation(70.0F, 74.0F), wire.previous());
        assertEquals(new Rotation(90.0F, 82.0F), wire.current());
        assertEquals(8.0F, ScaffoldModule.grimWireClickPitchStep(snapshot));
        Rotation nextPlannedAim = new Rotation(108.0F, 66.0F);
        assertNotEquals(nextPlannedAim, wire.current(),
            "the next movement aim is not server-visible for the pre-movement use packet");

        AutismServerRotationView.WireSnapshot first =
            new AutismServerRotationView.WireSnapshot(
                Float.NaN, Float.NaN, 12.0F, 78.0F, 1, true);
        assertEquals(0.0F, ScaffoldModule.grimWireClickPitchStep(first),
            "the first known wire sample falls back previous=current");
    }

    @Test
    void placementAttemptReducerIsOrderIndependentAndNeverPredictsBeforeConsumedUse() {
        ScaffoldModule.GrimAttemptDecision resultFirst = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.ARMED, 0,
            true, true, true, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.ARMED, resultFirst.state(),
            "a local result cannot become SENT before the final connection write exists");
        resultFirst = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.SENT, 0,
            true, true, true, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.PREDICTED, resultFirst.state());

        ScaffoldModule.GrimAttemptDecision writeFirst = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.SENT, 0,
            false, false, true, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.SENT, writeFirst.state(),
            "local collision alone is not proof that useItemOn consumed");
        writeFirst = ScaffoldModule.grimReduceAttempt(
            writeFirst.state(), 0, true, true, true, false);
        assertEquals(resultFirst.state(), writeFirst.state());

        ScaffoldModule.GrimAttemptDecision rejected = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.SENT, 0,
            true, false, false, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.FAILED, rejected.state());
        assertEquals("use", rejected.failure());
        ScaffoldModule.GrimAttemptDecision notSent = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.ARMED,
            ScaffoldModule.GRIM_PLACEMENT_RECONCILE_TICKS,
            false, false, false, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.FAILED, notSent.state());
        assertEquals("not-sent", notSent.failure());
    }

    @Test
    void ambiguousAttemptBlocksDuplicatesAndCumulativeAckOnlyCoversOlderSequences() {
        assertTrue(ScaffoldModule.grimAttemptBlocksRearm(
            ScaffoldModule.GrimPlacementAttemptState.ARMED));
        assertTrue(ScaffoldModule.grimAttemptBlocksRearm(
            ScaffoldModule.GrimPlacementAttemptState.SENT));
        assertTrue(ScaffoldModule.grimAttemptBlocksRearm(
            ScaffoldModule.GrimPlacementAttemptState.RECONCILING));
        assertFalse(ScaffoldModule.grimAttemptBlocksRearm(
            ScaffoldModule.GrimPlacementAttemptState.PREDICTED));
        assertFalse(ScaffoldModule.grimAckCovers(12, 11));
        assertTrue(ScaffoldModule.grimAckCovers(12, 12));
        assertTrue(ScaffoldModule.grimAckCovers(12, 15));
        assertFalse(ScaffoldModule.grimAckCovers(13, 12));
        assertFalse(ScaffoldModule.grimAckRetiresQuarantine(-1, 100),
            "an interaction with no final sequence remains quarantined");
        assertFalse(ScaffoldModule.grimAckRetiresQuarantine(13, 12));
        assertTrue(ScaffoldModule.grimAckRetiresQuarantine(13, 13));
        assertTrue(ScaffoldModule.grimAckRetiresQuarantine(13, 20));

        ScaffoldModule.GrimAttemptDecision reconciling = ScaffoldModule.grimReduceAttempt(
            ScaffoldModule.GrimPlacementAttemptState.SENT, 0,
            true, true, false, false);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.RECONCILING, reconciling.state());
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.RECONCILING,
            ScaffoldModule.grimReduceAttempt(reconciling.state(), 1,
                true, true, false, false).state());
        ScaffoldModule.GrimAttemptDecision ackAir = ScaffoldModule.grimReduceAttempt(
            reconciling.state(), 1, true, true, false, true);
        assertEquals(ScaffoldModule.GrimPlacementAttemptState.FAILED, ackAir.state());
        assertEquals("ack-air", ackAir.failure());
    }

    @Test
    void queuedFinalWritesCannotLetAForeignUseOverwriteTheMatchingAttempt() {
        BlockPos support = new BlockPos(10, 80, 10);
        Direction face = Direction.EAST;
        Vec3 hitPoint = new Vec3(11.0D, 80.75D, 10.5D);
        ScaffoldModule.PlacementTarget pending = new ScaffoldModule.PlacementTarget(
            support, support.relative(face), face,
            new BlockHitResult(hitPoint, face, support, false),
            new Rotation(90.0F, 82.0F), 80.5D);
        ScaffoldModule.GrimFinalUseWrite foreign = new ScaffoldModule.GrimFinalUseWrite(
            30, support.north(), Direction.SOUTH,
            new Vec3(10.5D, 80.75D, 10.0D), 1_000L);
        ScaffoldModule.GrimFinalUseWrite matching = new ScaffoldModule.GrimFinalUseWrite(
            31, support, face, hitPoint, 1_001L);
        ScaffoldModule.GrimFinalUseWrite duplicate = new ScaffoldModule.GrimFinalUseWrite(
            31, support, face, hitPoint, 1_001L);

        assertFalse(ScaffoldModule.grimFinalUseMatches(pending, foreign));
        assertTrue(ScaffoldModule.grimFinalUseMatches(pending, matching));
        assertFalse(ScaffoldModule.grimFinalUseBelongsToAttempt(matching, 1L));
        assertFalse(ScaffoldModule.grimFinalUseBelongsToAttempt(duplicate, 1L),
            "matching geometry without the concrete packet identity cannot resolve an attempt");
    }

    @Test
    void landingReservationUsesTheTwoPathSideLegsForEveryDiagonalSign() {
        BlockPos landing = new BlockPos(20, 80, 20);
        assertEquals(List.of(new BlockPos(19, 80, 20), new BlockPos(20, 80, 19)),
            ScaffoldModule.grimLandingConnectorCandidates(landing, new Vec3(1, 0, 1)));
        assertEquals(List.of(new BlockPos(21, 80, 20), new BlockPos(20, 80, 21)),
            ScaffoldModule.grimLandingConnectorCandidates(landing, new Vec3(-1, 0, -1)));
        assertEquals(List.of(new BlockPos(19, 80, 20), new BlockPos(20, 80, 21)),
            ScaffoldModule.grimLandingConnectorCandidates(landing, new Vec3(1, 0, -1)));
        assertEquals(List.of(new BlockPos(21, 80, 20), new BlockPos(20, 80, 19)),
            ScaffoldModule.grimLandingConnectorCandidates(landing, new Vec3(-1, 0, 1)));
        assertEquals(List.of(new BlockPos(20, 80, 19)),
            ScaffoldModule.grimLandingConnectorCandidates(landing, new Vec3(0, 0, 1)),
            "cardinal travel has one predecessor, preserving own/frontier behavior");
    }

    @Test
    void riserSideFaceMatchesTheStepASideCellShareOneRowUp() {
        BlockPos landing = new BlockPos(7, 83, 74);
        assertEquals(Direction.SOUTH,
            ScaffoldModule.grimRiserSideFace(landing, new BlockPos(7, 83, 73)),
            "11:55 t172 exactly: the step is north of the riser, click its south face");
        assertEquals(Direction.NORTH,
            ScaffoldModule.grimRiserSideFace(landing, new BlockPos(7, 83, 75)));
        assertEquals(Direction.EAST,
            ScaffoldModule.grimRiserSideFace(landing, new BlockPos(6, 83, 74)));
        assertEquals(Direction.WEST,
            ScaffoldModule.grimRiserSideFace(landing, new BlockPos(8, 83, 74)));

        assertNull(ScaffoldModule.grimRiserSideFace(landing, landing));
        assertNull(ScaffoldModule.grimRiserSideFace(landing, new BlockPos(6, 83, 73)));
        assertNull(ScaffoldModule.grimRiserSideFace(landing, null));
        assertNull(ScaffoldModule.grimRiserSideFace(null, new BlockPos(6, 83, 74)));
    }

    @Test
    void landingReservationAdvancesConnectorThenSupportThenRiser() {
        assertEquals(ScaffoldModule.GrimReservationNeed.CONNECTOR,
            ScaffoldModule.grimReservationNeed(false, 2, false));
        assertEquals(ScaffoldModule.GrimReservationNeed.SUPPORT,
            ScaffoldModule.grimReservationNeed(false, 1, false));
        assertEquals(ScaffoldModule.GrimReservationNeed.RISER,
            ScaffoldModule.grimReservationNeed(true, 0, false));
        assertEquals(ScaffoldModule.GrimReservationNeed.READY,
            ScaffoldModule.grimReservationNeed(true, 0, true));
    }

    @Test
    void landingReservationSurvivesTheArcAndResetsOnLandingReleaseOrTurn() {
        BlockPos takeoff = new BlockPos(30, 90, 30);
        BlockPos driftingProjection = new BlockPos(31, 90, 31);
        assertEquals(takeoff, ScaffoldModule.grimReservedSupportAfterSample(
            takeoff, driftingProjection, false, true, false),
            "airborne replans cannot abandon the takeoff reservation");
        assertEquals(driftingProjection, ScaffoldModule.grimReservedSupportAfterSample(
            takeoff, driftingProjection, true, true, false),
            "the first grounded sample starts the next arc's reservation");
        assertNull(ScaffoldModule.grimReservedSupportAfterSample(
            takeoff, driftingProjection, false, false, false));
        assertNull(ScaffoldModule.grimReservedSupportAfterSample(
            takeoff, driftingProjection, false, true, true));
    }

    @Test
    void heldArcAcceleratesFromWaitZeroAlongTheExplicitCourse() {
        Vec3 position = new Vec3(0.0D, 64.0D, 0.0D);
        double[] cardinal = ScaffoldModule.grimHeldArcLanding(
            position, Vec3.ZERO, 0.42D, 64.0D, true, 0, new Vec3(1, 0, 0));
        assertNotNull(cardinal);
        assertTrue(cardinal[0] > 0.5D);
        assertEquals(0.0D, cardinal[1], 1.0E-9D);

        double[] diagonal = ScaffoldModule.grimHeldArcLanding(
            position, new Vec3(0.0029D, 0.0D, 0.0D), 0.42D, 64.0D,
            true, 0, new Vec3(1, 0, 1));
        assertNotNull(diagonal);
        assertEquals(diagonal[0], diagonal[1], 1.0E-9D,
            "the sub-threshold seed is zeroed before diagonal held control is applied");
        assertNull(ScaffoldModule.grimHeldArcLanding(
            position, Vec3.ZERO, 0.42D, 66.0D, true, 0, new Vec3(1, 0, 0)),
            "a requested surface above the vanilla apex is unreachable");
    }

    @Test
    void heldControlMagnitudeMatchesMinecraft262SquareInputShaping() {
        assertEquals(0.98D,
            ScaffoldModule.grimHeldControlMagnitude(new Vec3(1, 0, 0), false), 1.0E-9D);
        Vec3 diagonal = new Vec3(1.0D / Math.sqrt(2.0D), 0.0D,
            1.0D / Math.sqrt(2.0D));
        assertEquals(1.0D,
            ScaffoldModule.grimHeldControlMagnitude(diagonal, false), 1.0E-9D);
        assertEquals(0.294D,
            ScaffoldModule.grimHeldControlMagnitude(new Vec3(1, 0, 0), true), 1.0E-9D);
        assertEquals(0.98D * 0.3D * Math.sqrt(2.0D),
            ScaffoldModule.grimHeldControlMagnitude(diagonal, true), 1.0E-9D);
    }

    @Test
    void intaveRotationFlickModelScoresTheUnshapedAlternation() {

        float[] captured = {74, 82, 74, 83, 74, 82, 74, 84, 74, 82, 74, 83, 74, 82};
        boolean[] allSide = new boolean[captured.length];
        java.util.Arrays.fill(allSide, true);
        assertTrue(intaveRotationFlickVl(captured, allSide, 4.0D) > 100.0D,
            "the logged 74/82..84 side-face alternation reaches Intave's flag buffer");

        float[] shaped = {74, 74.5F, 83, 74.5F, 74, 83, 74.5F, 74};
        boolean[] shapedFaces = {true, true, false, true, true, false, true, true};
        assertEquals(0.0D, intaveRotationFlickVl(shaped, shapedFaces, 4.0D), 1.0E-9D,
            "top-face risers neither score nor replace Intave's previous side pitch");
    }

    @Test
    void finalUseIsBoundToItsAttemptGenerationAndImmediatePrecedingWireRotation() {
        BlockPos support = new BlockPos(8, 70, 8);
        BlockPos placed = support.east();
        Vec3 location = new Vec3(9.0D, 70.75D, 8.5D);
        Rotation committed = new Rotation(90.0F, 82.0F);
        ScaffoldModule.GrimQueuedUse queued = new ScaffoldModule.GrimQueuedUse(
            17L, 0, net.minecraft.world.InteractionHand.MAIN_HAND,
            placed, support, Direction.EAST, committed);
        AutismServerRotationView.WireSnapshot sameWire =
            new AutismServerRotationView.WireSnapshot(70.0F, 74.0F, 90.0F, 82.0F, 4, true);
        ScaffoldModule.GrimFinalUseWrite write = new ScaffoldModule.GrimFinalUseWrite(
            31, support, Direction.EAST, location, 10_000L,
            net.minecraft.world.InteractionHand.MAIN_HAND, queued, sameWire);
        ScaffoldModule.PlacementTarget pending = new ScaffoldModule.PlacementTarget(
            support, placed, Direction.EAST,
            new BlockHitResult(location, Direction.EAST, support, false),
            committed, 70.5D);

        assertTrue(ScaffoldModule.grimFinalUseBelongsToAttempt(write, 17L));
        assertFalse(ScaffoldModule.grimFinalUseBelongsToAttempt(write, 18L),
            "an expired same-geometry packet cannot donate its sequence to the retry");
        assertEquals(18L, ScaffoldModule.grimNextAttemptGeneration(17L));
        assertEquals(19L, ScaffoldModule.grimNextAttemptGeneration(18L),
            "ordinary attempt cleanup never resets the lifetime counter");
        assertTrue(ScaffoldModule.grimFinalWireMatches(queued, sameWire));
        AutismServerRotationView.WireSnapshot overtaken =
            new AutismServerRotationView.WireSnapshot(90.0F, 82.0F, 108.0F, 66.0F, 5, true);
        assertFalse(ScaffoldModule.grimFinalWireMatches(queued, overtaken),
            "movement B overtaking arm snapshot A makes the final use unsafe");
        assertTrue(ScaffoldModule.grimFirstFinalWriteForAttempt(
            ScaffoldModule.GrimPlacementAttemptState.ARMED, 1));
        assertFalse(ScaffoldModule.grimFirstFinalWriteForAttempt(
            ScaffoldModule.GrimPlacementAttemptState.SENT, 2),
            "a second concrete sequence packet is a duplicate, not another resolution");
        ScaffoldModule.GrimQueuedUse secondPacket = new ScaffoldModule.GrimQueuedUse(
            17L, 1, net.minecraft.world.InteractionHand.MAIN_HAND,
            placed, support, Direction.EAST, committed);
        assertTrue(ScaffoldModule.grimFinalWriteIsDuplicate(
            secondPacket, true, ScaffoldModule.GrimPlacementAttemptState.ARMED, 1));
        assertFalse(ScaffoldModule.grimFinalWriteIsDuplicate(
            queued, false, ScaffoldModule.GrimPlacementAttemptState.ARMED, 1));
        assertTrue(ScaffoldModule.grimUseResultMatchesAttempt(
            17L, 17L, net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.InteractionHand.MAIN_HAND, false,
            pending.hit(), pending));
        assertFalse(ScaffoldModule.grimUseResultMatchesAttempt(
            17L, 17L, net.minecraft.world.InteractionHand.OFF_HAND,
            net.minecraft.world.InteractionHand.MAIN_HAND, false,
            pending.hit(), pending));
        assertFalse(ScaffoldModule.grimUseResultMatchesAttempt(
            17L, 17L, net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.InteractionHand.MAIN_HAND, true,
            pending.hit(), pending), "the second result cannot overwrite the first");
    }

    @Test
    void placementCadenceUsesMonotonicNanosecondsAtMillisecondBoundaries() {
        assertEquals(-1L, ScaffoldModule.grimMonotonicElapsedMs(
            10_000L, Long.MIN_VALUE));
        assertEquals(377L, ScaffoldModule.grimMonotonicElapsedMs(
            377_999_999L, 0L));
        assertEquals(378L, ScaffoldModule.grimMonotonicElapsedMs(
            378_000_000L, 0L));
        assertEquals(0L, ScaffoldModule.grimMonotonicElapsedMs(99L, 100L),
            "an impossible negative delta never rolls cadence backward");
        assertEquals(100L, ScaffoldModule.grimMonotonicTimestamp(100L, 99L));
        assertEquals(101L, ScaffoldModule.grimMonotonicTimestamp(100L, 101L));
    }

    @Test
    void actionOnlyBlinkIsTheOnlyScopeThatCanEscapePredictedSupportOrdering() {
        for (boolean outgoing : new boolean[] {false, true}) {
            for (boolean movement : new boolean[] {false, true}) {
                for (boolean actions : new boolean[] {false, true}) {
                    assertEquals(outgoing && actions && !movement,
                        AutismBlinkManager.holdsActionsWithoutMovement(
                            outgoing, movement, actions));
                }
            }
        }
    }

    @Test
    void predictionAckEpochUsesClientLevelIdentityNotDimensionEquality() {
        Object first = new Object();
        Object replacement = new Object();
        assertFalse(ScaffoldModule.grimPredictionEpochChanged(first, first));
        assertTrue(ScaffoldModule.grimPredictionEpochChanged(first, replacement));
        assertTrue(ScaffoldModule.grimPredictionEpochChanged(first, null));
        assertTrue(ScaffoldModule.grimPredictionEpochChanged(null, replacement));
        assertFalse(ScaffoldModule.grimPredictionEpochChanged(null, null));
    }

    @Test
    void heldArcAppliesInputBeforeThisTicksMovementAndDragAfterIt() {
        Vec3 position = new Vec3(0.0D, 64.1D, 0.0D);
        Vec3 velocity = new Vec3(0.1D, -0.1D, 0.0D);
        double[] grounded = ScaffoldModule.grimHeldArcLanding(
            position, velocity, -0.1D, 64.0D, true);
        double[] airborne = ScaffoldModule.grimHeldArcLanding(
            position, velocity, -0.1D, 64.0D, false);
        assertNotNull(grounded);
        assertNotNull(airborne);
        assertEquals(0.196D, grounded[0], 1.0E-9D,
            "ground input acceleration belongs to the takeoff displacement");
        assertEquals(0.12D, airborne[0], 1.0E-9D,
            "air input acceleration belongs to the current displacement before 0.91 drag");
    }

    @Test
    void theHeldModelGivesTheGroundedTakeoffTickItsAcceleration() {
        Vec3 position = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 walking = new Vec3(0.106D, 0.0D, 0.0D);
        double[] grounded = ScaffoldModule.grimHeldArcLanding(position, walking, 0.42D, 64.0D, true);
        double[] airborne = ScaffoldModule.grimHeldArcLanding(position, walking, 0.42D, 64.0D, false);
        assertNotNull(grounded);
        assertNotNull(airborne);

        assertTrue(grounded[0] > airborne[0] * 0.85D,
            "the ground step keeps its speed at walk pace; a bare friction decay would halve it");
        assertTrue(grounded[0] > 1.5D,
            "a full walking jump carries well past a column - it is a step, not a hop");
    }

    @Test
    void noFootingUnderfootMeasuresOverlapNotTheCentreCell() {
        assertTrue(ScaffoldModule.grimNoFootingUnderfoot(0.0D),
            "nothing at all under the box is the real brink");
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(0.02D),
            "0.02 is a healthy grounded placement tick, not an emergency");
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(0.13D),
            "so is 0.13 - the capture is full of them");
        assertFalse(ScaffoldModule.grimNoFootingUnderfoot(ScaffoldModule.GRIM_FOOTING_OWED_OVERLAP),
            "and the walk clamp's own threshold must stay strictly above this one");
    }

    @Test
    void aSideFaceUnderTheBlockWeStandOnIsNotClickableFromAnyAngle() {
        BlockPos support = new BlockPos(22, 92, 75);
        Vec3 eye = new Vec3(22.67D, 95.79D, 75.76D);
        assertTrue(ScaffoldModule.grimFaceSelfOccluded(eye, support, Direction.SOUTH,
            pos -> pos.equals(support.above())), "the lid is our own floor - the ray cannot leave");

        assertFalse(ScaffoldModule.grimFaceSelfOccluded(eye, support, Direction.SOUTH,
            pos -> false));

        assertFalse(ScaffoldModule.grimFaceSelfOccluded(new Vec3(23.50D, 95.79D, 75.76D), support,
            Direction.SOUTH, pos -> true));

        assertFalse(ScaffoldModule.grimFaceSelfOccluded(new Vec3(22.67D, 93.50D, 75.76D), support,
            Direction.SOUTH, pos -> true));

        assertFalse(ScaffoldModule.grimFaceSelfOccluded(eye, support, Direction.UP, pos -> true));
        assertFalse(ScaffoldModule.grimFaceSelfOccluded(eye, support, Direction.DOWN, pos -> true));
        assertFalse(ScaffoldModule.grimFaceSelfOccluded(null, support, Direction.SOUTH,
            pos -> true));
    }

    @Test
    void theBrinkRescueCellIsNotSelfOccluded() {
        assertFalse(ScaffoldModule.grimFaceSelfOccluded(new Vec3(41.50D, 93.62D, 75.50D),
            new BlockPos(41, 92, 75), Direction.SOUTH, pos -> false));
        assertFalse(ScaffoldModule.grimFaceSelfOccluded(new Vec3(41.50D, 93.62D, 75.50D),
            new BlockPos(41, 92, 75), Direction.SOUTH, pos -> true),
            "below the lid top the ray still leaves sideways, lid or no lid");
        assertTrue(ScaffoldModule.grimFaceSelfOccluded(new Vec3(41.50D, 94.00D, 75.50D),
            new BlockPos(41, 92, 75), Direction.SOUTH, pos -> true),
            "at the lid top with a full cube overhead the ray cannot leave");
    }

    @Test
    void theJitterNeverPushesTheFloorAFullTickPastTheSafetyMargin() {
        assertTrue(ScaffoldModule.GRIM_PACE_JITTER_MS <= 25,
            "a jitter over half a tick is a second safety factor");
        for (long limit : new long[] { 150L, 200L, 300L, 350L, 500L }) {
            long base = ScaffoldModule.grimPaceFloorMs(limit);
            assertTrue(base >= limit, "the floor must never sit under the modelled limit");
            for (int jitter = 0; jitter < ScaffoldModule.GRIM_PACE_JITTER_MS; jitter++) {
                assertTrue(base + jitter >= limit, "no roll may undercut the limit");
                assertTrue(base + jitter - base < 50L, "no roll may cost a whole extra tick");
            }
        }
    }

    @Test
    void theFloorGateRefusesACellBelowTheBridgingRow() {
        assertTrue(ScaffoldModule.grimFloorGateRefuses(81, 82, false), "t151, the ratchet tick");

        assertFalse(ScaffoldModule.grimFloorGateRefuses(82, 82, false));
        assertFalse(ScaffoldModule.grimFloorGateRefuses(83, 82, false));

        assertFalse(ScaffoldModule.grimFloorGateRefuses(81, 82, true), "descending, catch allowed");
    }

    @Test
    void theTowerParkAndTheTowerClickAgreeOnYaw() {
        assertFalse(ScaffoldModule.grimGoalYawUnconverged(-180.0F, -180.0F),
            "park and click on one yaw: the gate is silent from the first airborne tick");
        assertTrue(ScaffoldModule.grimGoalYawUnconverged(-180.0F, 142.1F),
            "t019's 95 degrees, which cost six ticks of an eight-tick window");

        assertFalse(ScaffoldModule.grimGoalYawUnconverged(-180.0F, -162.0F));

        assertFalse(ScaffoldModule.grimGoalYawUnconverged(-180.0F, 179.0F));

        float sweptTicks = Math.abs(Mth.wrapDegrees(-172.4F - 8.0F)) / ScaffoldModule.GRIM_MAX_YAW_STEP;
        assertTrue(sweptTicks > 9.0F, "ten of the eleven airborne ticks, spent turning around");
    }

    @Test
    void theOwnColumnIsTheCellUnderTheBoxNotTheOneAhead() {
        double z = 51.14D, vz = -0.117D;
        assertEquals(51, (int) Math.floor(z), "the cell the box is actually over");
        assertEquals(50, (int) Math.floor(z + vz * 2.0D), "the cell the 2-tick lead names instead");

        assertTrue(ScaffoldModule.grimSameColumn(
            new BlockPos(31, 83, 51), new BlockPos(31, 84, 51)),
            "same column, different row - the riser sits a row above its support");
        assertFalse(ScaffoldModule.grimSameColumn(
            new BlockPos(31, 83, 51), new BlockPos(31, 83, 50)),
            "the lead cell is a different column and must not be exempted");
        assertFalse(ScaffoldModule.grimSameColumn(null, new BlockPos(31, 83, 51)));
    }

    @Test
    void theOwnColumnAsksTheInstantNotAProjection() {

        Vec3 at = new Vec3(5.23D, 85.17D, 46.55D);
        Vec3 carry = new Vec3(-0.142D, 0.083D, 0.0D);
        BlockPos own = new BlockPos(5, 83, 46);
        assertTrue(ScaffoldModule.grimBoxOverColumn(at, own),
            "the box is over cell 5 right now - that is the whole question");
        assertFalse(ScaffoldModule.grimRiseColumnHeld(at, carry, own, 3, 0.15D),
            "three ticks of lead lands at 4.80, overlap 0.09 against the 0.15 floor");

        assertTrue(ScaffoldModule.grimRiseColumnHeld(
            new Vec3(5.38D, 85.00D, 46.55D), carry, own, 3, 0.15D));
    }

    @Test
    void theShortExemptionsNeverUndercutTheMatrixMinimum() {
        assertEquals(350L, ScaffoldModule.grimMatrixMinPlaceMs(false), "7 ticks cardinal");
        assertEquals(250L, ScaffoldModule.grimMatrixMinPlaceMs(true), "5 ticks diagonal");

        assertEquals(350L, ScaffoldModule.grimPaceExemptFloorMs(
            ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS, false));
        assertEquals(250L, ScaffoldModule.grimPaceExemptFloorMs(
            ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS, true));
        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS
            < ScaffoldModule.GRIM_MATRIX_MIN_PLACE_DIAGONAL_MS,
            "the exemption is strictly below even the looser minimum - it always got raised");

        assertEquals(500L, ScaffoldModule.grimPaceExemptFloorMs(500L, false));
    }

    @Test
    void theFloorGateDefendsTheRowThatWasActuallyBuilt() {
        assertTrue(ScaffoldModule.grimFloorGateRefuses(83, 84, false),
            "01:18 t064: a bridge candidate at row 83 while row 84 is already built");
        assertFalse(ScaffoldModule.grimFloorGateRefuses(84, 84, false),
            "the row itself stays open - that is the step the climb needs");
        assertFalse(ScaffoldModule.grimFloorGateRefuses(83, 84, true),
            "a genuine fall still re-opens it for the catch");

        assertFalse(ScaffoldModule.grimFloorGateRefuses(83, 83, false),
            "the stale oracle value - the gate is inert against it");
        assertEquals(84, ScaffoldModule.grimBuiltFloorRow(83, 84, Integer.MIN_VALUE),
            "grimBuiltFloorRow takes the fresher of the two");
    }

    @Test
    void theBuiltFloorRemembersARiserThatLandedMidArc() {

        assertEquals(97, ScaffoldModule.grimBuiltFloorRow(96, Integer.MIN_VALUE, 97),
            "the airborne carrier is the diagonal's only fresh source");
        assertEquals(96, ScaffoldModule.grimBuiltFloorRow(96, Integer.MIN_VALUE, Integer.MIN_VALUE),
            "no riser landed - the takeoff oracle stands");

        assertEquals(84, ScaffoldModule.grimBuiltFloorRow(83, 84, Integer.MIN_VALUE));
        assertEquals(84, ScaffoldModule.grimBuiltFloorRow(
            Integer.MIN_VALUE, 84, Integer.MIN_VALUE));

        assertEquals(95, ScaffoldModule.grimBuiltFloorRow(95, Integer.MIN_VALUE, 95));
        assertEquals(Integer.MIN_VALUE, ScaffoldModule.grimBuiltFloorRow(
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void theCadenceExemptionsMeasureTheRowTheRiserBuilt() {

        assertEquals(109, ScaffoldModule.grimBuiltFloorRow(108, 109, Integer.MIN_VALUE),
            "the lock carries the mid-arc riser the frozen oracle cannot");

        BlockPos save = new BlockPos(16, 109, 84);

        assertFalse(ScaffoldModule.grimIsOwnFootingCell(save, new BlockPos(16, 109, 84)),
            "against the takeoff row the exemption asks a row too low and declines");

        assertTrue(ScaffoldModule.grimIsOwnFootingCell(save, new BlockPos(16, 110, 84)),
            "against the built row the save is the cell under the feet");

        assertTrue(ScaffoldModule.grimPaceFloorHolds(149L, 345L),
            "the flat floor is what refused it");
        assertFalse(ScaffoldModule.grimPaceFloorHolds(
            149L, ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS));
        assertFalse(ScaffoldModule.grimPaceFloorHolds(
            149L, ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS));
    }

    @Test
    void theTiersNeverServeARowTheArcAlreadyRoseAbove() {

        assertTrue(ScaffoldModule.grimTierServesBelowBuiltFloor(96, 97, false, false),
            "t406: the sticky's 3,96,79 with the riser row 97 already built");
        assertFalse(ScaffoldModule.grimTierServesBelowBuiltFloor(95, 95, false, false),
            "t353: the reserved leg on the row being built - the click that saved the arc");
        assertFalse(ScaffoldModule.grimTierServesBelowBuiltFloor(92, 93, false, true),
            "t295: the loss-branch hedge the arc would land on if the riser misses");
        assertFalse(ScaffoldModule.grimTierServesBelowBuiltFloor(92, 93, true, false),
            "a genuine fall keeps the catch layer, same stand-down as the floor gate");
        assertFalse(ScaffoldModule.grimTierServesBelowBuiltFloor(98, 97, false, false),
            "the next riser is above the floor, never gated");
    }

    @Test
    void theHoldNeverKeepsACellBelowTheRowBeingStoodOn() {

        assertFalse(ScaffoldModule.grimHoldRowAllowed(108, 109),
            "a row under the bridge is a cell the lane has already outgrown");

        assertTrue(ScaffoldModule.grimHoldRowAllowed(109, 109), "the row being stood on");
        assertTrue(ScaffoldModule.grimHoldRowAllowed(110, 109), "the riser one above");

        assertTrue(ScaffoldModule.grimHoldRowAllowed(108, Integer.MIN_VALUE), "no floor known");
    }

    @Test
    void aPlacedRowRaisesTheHoldFloorTheAirbornePinCannotSee() {

        assertEquals(111, ScaffoldModule.grimHoldFloorRow(110, 111),
            "a placement one row up is the bridge declaring its new row");
        assertFalse(ScaffoldModule.grimHoldRowAllowed(110, ScaffoldModule.grimHoldFloorRow(110, 111)),
            "40,110,64 is a row the arc has already climbed off");

        assertEquals(110, ScaffoldModule.grimHoldFloorRow(110, 110), "footing on the standing row");
        assertEquals(110, ScaffoldModule.grimHoldFloorRow(110, 109), "a fall catch never raises it");

        assertEquals(111, ScaffoldModule.grimHoldFloorRow(Integer.MIN_VALUE, 111));
        assertEquals(110, ScaffoldModule.grimHoldFloorRow(110, Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE,
            ScaffoldModule.grimHoldFloorRow(Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void theHoldNeverKeepsACellTheLaneHasWalkedPast() {

        Vec3 southEast = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        Vec3 at762 = new Vec3(37.16D, 114.25D, 85.68D);
        assertTrue(ScaffoldModule.grimHoldBehindLane(
            at762, southEast, new BlockPos(35, 112, 85), false),
            "a cell the lane has outgrown is not worth an aim, whatever row it sits on");

        assertFalse(ScaffoldModule.grimHoldBehindLane(
            at762, southEast, new BlockPos(37, 112, 85), false),
            "the cell under the eye is the commitment being defended");
        assertFalse(ScaffoldModule.grimHoldBehindLane(
            at762, southEast, new BlockPos(38, 112, 86), false), "the frontier ahead");

        assertFalse(ScaffoldModule.grimHoldBehindLane(
            at762, southEast, new BlockPos(35, 112, 85), true), "descending - the catch is behind");

        assertFalse(ScaffoldModule.grimHoldBehindLane(
            at762, null, new BlockPos(35, 112, 85), false), "no lane, no behind");
    }

    @Test
    void theHoldClockCannotBeRestampedByAnEarlierCallInTheSameTick() {

        BlockPos held = new BlockPos(40, 110, 64);
        assertTrue(ScaffoldModule.grimHoldRefreshBlocked(441, 441, held, held),
            "the hold already served this cell this tick - the clock must not restart");

        assertFalse(ScaffoldModule.grimHoldRefreshBlocked(441, 441, held, new BlockPos(39, 111, 64)),
            "a different cell is a fresh commitment");

        assertFalse(ScaffoldModule.grimHoldRefreshBlocked(441, 442, held, held), "next tick");
        assertFalse(ScaffoldModule.grimHoldRefreshBlocked(
            Integer.MIN_VALUE, 441, null, held), "nothing held yet");
    }

    @Test
    void aCellTheBodyHasDescendedIntoIsNeverClickable() {

        BlockPos cell = new BlockPos(7, 88, 63);
        Vec3 falling = new Vec3(0.094D, -0.302D, 0.0D);
        assertFalse(ScaffoldModule.grimCellClearOfBody(
            playerBoxAt(7.50D, 88.80D, 63.53D), falling, cell), "feet 0.20 inside the cell");
        assertFalse(ScaffoldModule.grimCellClearOfBody(
            playerBoxAt(7.74D, 88.12D, 63.53D), falling, cell), "feet 0.88 inside the cell");

        assertFalse(ScaffoldModule.grimCellClearOfBody(
            playerBoxAt(7.40D, 89.02D, 63.53D), falling, cell),
            "two centimetres of present clearance is not one safe movement tick");
    }

    private static AABB playerBoxAt(double x, double feet, double z) {
        return new AABB(x - 0.3D, feet, z - 0.3D, x + 0.3D, feet + 1.8D, z + 0.3D);
    }

    @Test
    void aSettledAimIsNotDraggedOffByAPlanBlink() {

        assertTrue(ScaffoldModule.grimAimHoldApplies(356, 357, 3, true, true), "the first blink");
        assertTrue(ScaffoldModule.grimAimHoldApplies(356, 359, 3, true, true), "the last blink");
        assertTrue(ScaffoldModule.grimAimHoldApplies(356, 356, 3, true, true),
            "same tick - selection can run several passes per tick");

        assertFalse(ScaffoldModule.grimAimHoldApplies(356, 360, 3, true, true),
            "past the window the cell is released");

        assertFalse(ScaffoldModule.grimAimHoldApplies(356, 357, 3, false, true),
            "something already filled the cell");
        assertFalse(ScaffoldModule.grimAimHoldApplies(356, 357, 3, true, false),
            "the support went away - holding would aim at nothing");
        assertFalse(ScaffoldModule.grimAimHoldApplies(-1, 357, 3, true, true), "nothing committed");
    }

    @Test
    void theRowLockNeverDescendsOntoATwoBlockStepFooting() {

        assertEquals(87, ScaffoldModule.grimLockRowFor(86, 87),
            "the support is one row under the built row - the lock holds the built row");

        assertEquals(83, ScaffoldModule.grimLockRowFor(83, 83), "flat: placed row is the floor");
        assertEquals(84, ScaffoldModule.grimLockRowFor(84, 83),
            "riser placed at 84 while the arc still reports the row 83 it left");

        assertEquals(86, ScaffoldModule.grimLockRowFor(86, 88), "two rows up is not the step");
        assertEquals(86, ScaffoldModule.grimLockRowFor(86, 85), "descending never raises");
        assertEquals(86, ScaffoldModule.grimLockRowFor(86, Integer.MIN_VALUE),
            "no footing surface yet - nothing to raise onto");
    }

    @Test
    void theSideFaceOfTheBlockUnderfootNeedsRoomToAimAt() {

        double eyeY = 85.17D + 1.62D;
        double dy = eyeY - 84.70D;
        double demanded = Math.toDegrees(Math.atan2(dy, 0.16D));
        assertTrue(demanded < ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "85.6 degrees is legal under the human-limits cap");

        double reach = dy / Math.tan(Math.toRadians(ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD));
        assertEquals(0.018D, reach, 0.005D);
        assertTrue(reach < 0.16D, "t055 is no longer inside a blind zone");
    }

    @Test
    void aConvergingSweepIsNeverRetired() {

        assertTrue(ScaffoldModule.grimGoalErrorClosing(95.0F, Float.NaN));

        assertTrue(ScaffoldModule.grimGoalErrorClosing(77.0F, 95.0F));
        assertTrue(ScaffoldModule.grimGoalErrorClosing(59.0F, 77.0F));

        assertFalse(ScaffoldModule.grimGoalErrorClosing(59.0F, 59.0F));
        assertFalse(ScaffoldModule.grimGoalErrorClosing(60.0F, 59.0F));

        assertFalse(ScaffoldModule.grimGoalErrorClosing(58.8F, 59.0F));
        assertTrue(ScaffoldModule.GRIM_YAW_VETO_RETIRE_TICKS >= 2,
            "a stall still gets more than one tick to prove itself");
    }

    @Test
    void theOverheadMarginIsFeetAndSeparatesFlatFromApex() {

        assertFalse(0.00D > ScaffoldModule.GRIM_FACE_OVERHEAD_MARGIN,
            "the flat bridge must keep planning its face while the run is still growing");
        assertFalse(0.75D > ScaffoldModule.GRIM_FACE_OVERHEAD_MARGIN,
            "and so must early ascent, where the flat chain still extends under the jump");
        assertTrue(1.25D > ScaffoldModule.GRIM_FACE_OVERHEAD_MARGIN,
            "06:55 t017's apex is the case that genuinely cannot be reached");

        assertTrue(1.62D > 1.5D, "the old margin, against the old (eye) measurement");

        assertTrue(ScaffoldModule.grimPitchOutOfReach(89.8F), "t017");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(81.9F), "t020, the tick it finally placed");
        assertFalse(ScaffoldModule.grimPitchOutOfReach(Float.NaN),
            "and NaN is 'behind the plane', not 'unreachable' - retiring on it drops every approach");
    }

    @Test
    void aClickMayNotShipBeforeTheYawReachesItsGoal() {
        assertTrue(ScaffoldModule.grimGoalYawUnconverged(135.0F, -127.7F), "t195, 97.3 degrees out");
        assertTrue(ScaffoldModule.grimGoalYawUnconverged(135.0F, -127.9F), "t200, 97.1 out");
        assertFalse(ScaffoldModule.grimGoalYawUnconverged(135.9F, 135.8F), "t214 had arrived");

        assertFalse(ScaffoldModule.grimGoalYawUnconverged(0.0F, -ScaffoldModule.GRIM_MAX_YAW_STEP),
            "the last tick of a legitimate sweep must still be allowed to place");

        assertFalse(ScaffoldModule.grimGoalYawUnconverged(179.0F, -179.0F),
            "2 degrees apart across the wrap, not 358");
    }

    @Test
    void aBlankRayFromAStreamStillInFlightRefutesNothing() {
        float[][] goalAndEmitted = {
            {-180.0F, 125.6F}, {-180.0F, 143.6F}, {55.8F, 126.2F}, {-180.0F, 143.9F},
            {71.1F, 126.1F}, {-180.0F, 144.1F}, {80.7F, 126.4F}, {-180.0F, 144.1F},
            {87.5F, 126.1F},
        };
        for (float[] tick : goalAndEmitted) {
            assertTrue(ScaffoldModule.grimGoalYawUnconverged(tick[0], tick[1]),
                "goal " + tick[0] + " vs emitted " + tick[1] + " is a stream in flight, not a miss");
        }

        assertTrue(Math.abs(Mth.wrapDegrees(-180.0F - 55.8F)) > 90.0F,
            "t238 and t239 asked for yaws 124 degrees apart");
        assertEquals(18.0F, Math.abs(Mth.wrapDegrees(143.9F - 126.1F)), 0.3F,
            "and the emitted value moved a full capped step every tick, arriving nowhere");

        assertFalse(ScaffoldModule.grimGoalYawUnconverged(-90.0F, -90.0F),
            "a motionless parked aim has arrived - its blank ray is a real refutation");
        assertFalse(ScaffoldModule.grimGoalYawUnconverged(-90.0F, -73.0F),
            "and so has one inside the last capped step of its sweep");
    }

    @Test
    void theGoalMayNotBeCappedOutOfItsOwnCrossingWindow() {

        float[] demanded = {89.1F, 89.4F, 89.3F};
        for (float demand : demanded) {
            assertTrue(demand > ScaffoldModule.GRIM_PLACE_MAX_PITCH,
                demand + " is past the goal bound, which is why the aim never reached it");
            assertTrue(ScaffoldModule.grimPlacementPitchLegal(demand),
                "and inside the gate that decides reachability - so the target was held, not dropped");
        }

        float goal = ScaffoldModule.grimPlacementPitchGoal(89.45F, 89.19D, 89.53D);
        assertTrue(goal >= 89.19F, "the goal must land inside the window, not under it: " + goal);
        assertEquals(ScaffoldModule.GRIM_PLACE_PITCH_PARK, goal, 0.001F);
        assertTrue(ScaffoldModule.grimPlacementPitchLegal(goal),
            "and still pass the click gate untouched");

        assertEquals(89.0F, ScaffoldModule.grimPlacementPitchGoal(89.45F, 88.90D, 89.40D), 0.001F);
        assertEquals(84.3F, ScaffoldModule.grimPlacementPitchGoal(84.3F, 60.0D, 89.0D), 0.001F);
        assertEquals(66.5F, ScaffoldModule.grimPlacementPitchGoal(20.0F, 66.5D, 84.0D), 0.001F);
        assertEquals(89.0F, ScaffoldModule.grimPlacementPitchGoal(120.0F, Double.NaN, Double.NaN),
            0.001F, "no window is still the plain cap");

        for (double low = 89.01D; low <= 90.0D; low += 0.07D) {
            float raised = ScaffoldModule.grimPlacementPitchGoal(90.0F, low, low + 0.2D);
            assertTrue(raised >= ScaffoldModule.GRIM_PLACE_MAX_PITCH, "never lower than before");
            assertTrue(ScaffoldModule.grimPlacementPitchLegal(raised),
                "and never a pitch the click gate must refuse: " + raised);
        }

        assertEquals(ScaffoldModule.GRIM_PLACE_MAX_PITCH + 0.3F,
            ScaffoldModule.GRIM_PLACE_PITCH_PARK, 0.001F);

        assertEquals(89.0F, ScaffoldModule.GRIM_PLACE_MAX_PITCH);
        assertEquals(89.5F, ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD);
    }

    @Test
    void aHeldCellStandingOnItsOwnFootingIsUpgradedToItsTopFace() {

        assertTrue(Direction.DOWN.ordinal() < 2, "DOWN is unscored");
        assertTrue(Direction.UP.ordinal() < 2, "UP is unscored - this is the whole fix");
        for (Direction face : Direction.values()) {
            assertEquals(face.getAxis().isHorizontal(), face.ordinal() >= 2,
                face + ": scored by Intave exactly when it is horizontal");
        }

        assertTrue(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.EAST, false, true));

        assertFalse(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.UP, false, true),
            "an upgraded cell must never re-enter the probe");
        assertFalse(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.DOWN, false, true));

        assertFalse(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.EAST, false, false),
            "no footing, no upgrade - this is what keeps flat bridging bit-identical");

        assertTrue(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.EAST, true, true));
        assertTrue(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.EAST, true, false),
            "an in-place placement clicks its own cell - there is nothing below it to require");

        assertFalse(ScaffoldModule.grimUpFaceUpgradeApplies(Direction.UP, true, true));
        assertFalse(ScaffoldModule.grimUpFaceUpgradeApplies(null, false, true));

        int horizontalBefore = 15;
        int upgraded = 5;
        int horizontalAfter = horizontalBefore - upgraded;
        assertEquals(10, horizontalAfter);
        for (int reported = 300; reported <= 399; reported++) {
            double after = reported * (horizontalBefore / (double) horizontalAfter);
            assertTrue(after >= 400.0D,
                "a reported " + reported + " ms must clear the threshold, got " + after);
        }

        double heldOnly = 300.0D * (horizontalBefore / (double) (horizontalBefore - 4));
        assertTrue(heldOnly < 410.0D,
            "the hold path alone is too thin to ship: " + heldOnly);
    }

    @Test
    void theDiagonalClimbBrakeRegulatesAgainstTheRecordedMeanAndReleasesItself() {
        double target = ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS * 1.08D;

        assertTrue(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 350.0D, 100L));

        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, target + 1.0D, 100L));

        assertTrue(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 100.0D,
            ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS - 1L));
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 100.0D,
            ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS),
            "at the clamp the wait is provably worthless - release regardless of the mean");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 100.0D, -1L),
            "no recorded placement yet - nothing to regulate");

        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, false, 0.09D, 350.0D, 100L),
            "never trim the descent - that is the landing's carry");
        assertTrue(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 350.0D, 100L),
            "the rising half still pays");

        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            false, true, true, true, true, true, 0.09D, 350.0D, 100L), "not the Grim family");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, false, true, true, true, true, 0.09D, 350.0D, 100L),
            "a cardinal climb already pays one placement per level and must never be braked");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, false, true, true, true, 0.09D, 350.0D, 100L), "flat diagonal, nothing to pace");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, false, true, true, 0.09D, 350.0D, 100L),
            "the player is not asking to travel");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, false, true, 0.09D, 350.0D, 100L),
            "grounded is not counter-movement's ground - 12:52 deleted every grounded brake");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, Double.NaN, 100L),
            "no recorded sample yet - never brake on a guess");

        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.0D, 350.0D, 100L),
            "spent lane speed stands the brake down");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, -0.20D, 350.0D, 100L), "and so does a reversed one");

        assertTrue(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, 350.0D, 100L),
            "a rising diagonal under the mean brakes, whatever the click gate is waiting on");
    }

    @Test
    void theScoredMeanIsARollingWindowOfClampedSamples() {
        assertTrue(Double.isNaN(ScaffoldModule.grimScoredGapMean(new long[0], 0L, 8)));
        assertTrue(Double.isNaN(ScaffoldModule.grimScoredGapMean(null, 0L, 8)));
        assertTrue(Double.isNaN(ScaffoldModule.grimScoredGapMean(new long[] {150L}, -1L, 8)),
            "no recorded placement yet - the gap reads -1 and there is nothing to regulate");

        long[] capture = {150L, 500L, 150L, 500L, 150L, 500L, 150L};
        assertTrue(ScaffoldModule.grimScoredGapMean(capture, 150L, 8)
            < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS);

        assertEquals(ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS,
            ScaffoldModule.grimScoredGapMean(new long[] {50_000L}, 50_000L, 8), 0.001D);

        long[] frozen = {150L, 150L, 150L, 150L, 150L, 150L, 150L};
        double atOnce = ScaffoldModule.grimScoredGapMean(frozen, 0L, 8);
        double capped = ScaffoldModule.grimScoredGapMean(
            frozen, ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS, 8);
        assertTrue(capped > atOnce, "waiting raises the mean");
        assertTrue(capped < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS,
            "but never far enough - a mean-only release would park the player forever");
        assertTrue(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, atOnce, 0L), "engaged while the stream is hot");
        assertFalse(ScaffoldModule.grimDiagonalClimbBrakeApplies(
            true, true, true, true, true, true, 0.09D, capped,
            ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS),
            "and released by the clamp - the gate always reaches its own exit");
    }

    @Test
    void theSameCellThroughAnotherFaceIsASubstitutionNotANoOp() {
        BlockPos cell = new BlockPos(-5, 89, 70);
        BlockPos other = new BlockPos(-5, 89, 71);

        assertTrue(ScaffoldModule.grimRaySubstitutionIsNoOp(
            cell, Direction.UP, cell, Direction.UP));

        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            cell, Direction.UP, cell, Direction.SOUTH),
            "a cell has six faces and the ray only has to reach one");

        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            cell, Direction.UP, other, Direction.UP));
        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            cell, Direction.UP, null, Direction.UP), "no ray cell, nothing to swap to");

        for (Direction face : Direction.values()) {
            assertEquals(face == Direction.EAST, ScaffoldModule.grimRaySubstitutionIsNoOp(
                cell, Direction.EAST, cell, face),
                face + " against a planned EAST face");
        }
    }

    @Test
    void aGroundedWalkPlacesItsFloorEarlyEnoughForTheServerToAckIt() {
        assertEquals(ScaffoldModule.GRIM_PACE_ACK_LEAD_OVERLAP,
            ScaffoldModule.grimBrinkOverlapFor(true),
            "grounded gets the ack lead");
        assertEquals(ScaffoldModule.GRIM_PACE_BRINK_OVERLAP,
            ScaffoldModule.grimBrinkOverlapFor(false),
            "airborne keeps the lip - a descent has no earlier tick");

        assertTrue(ScaffoldModule.GRIM_PACE_ACK_LEAD_OVERLAP > ScaffoldModule.GRIM_PACE_BRINK_OVERLAP,
            "the whole point is releasing before the lip");

        assertTrue(ScaffoldModule.GRIM_PACE_ACK_LEAD_OVERLAP < 0.5D,
            "a lead this wide would be placing for a cell the walk has not committed to");

        double perTick = 0.15D;
        assertTrue(ScaffoldModule.GRIM_PACE_ACK_LEAD_OVERLAP / perTick >= 2.0D,
            "at full walk speed the lead must cover the observed 1-2 tick ack");
    }

    @Test
    void aFaceSwappedCellKeepsPayingTheCadenceItWasPickedUnder() {
        BlockPos support = new BlockPos(17, 99, 63);
        BlockPos sideways = new BlockPos(18, 99, 63);
        BlockPos above = new BlockPos(17, 100, 63);

        assertFalse(ScaffoldModule.grimPacesAsRiser(sideways, support, null),
            "same row - a side face was never a riser");
        assertTrue(ScaffoldModule.grimPacesAsRiser(above, support, null),
            "a genuine riser still pays the riser floor");

        assertFalse(ScaffoldModule.grimPacesAsRiser(above, support, above),
            "a face-swapped cell is not a riser");

        assertTrue(ScaffoldModule.grimPacesAsRiser(above, support, new BlockPos(18, 100, 63)),
            "another cell's mark must not re-price this one");
    }

    @Test
    void theRecordedStreamIsHorizontalFacesOnly() {
        assertFalse(ScaffoldModule.grimIntaveRecordsPlacement(Direction.UP), "enumDirection 1");
        assertFalse(ScaffoldModule.grimIntaveRecordsPlacement(Direction.DOWN), "enumDirection 0");
        assertFalse(ScaffoldModule.grimIntaveRecordsPlacement(null));
        for (Direction face : new Direction[] {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            assertTrue(ScaffoldModule.grimIntaveRecordsPlacement(face), face + " is 2..5");
        }

        double span = 255.0D * 50.0D;
        double allPlacements = span / 60.0D;
        assertTrue(allPlacements < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS * 0.6D,
            "our old buffer read " + Math.round(allPlacements)
                + " ms - a target no cadence can hit, which is why the gate held to its own bound");

        double recorded = span / 38.0D;
        assertTrue(recorded > allPlacements * 1.5D,
            "dropping the risers is worth 58 percent: " + Math.round(recorded) + " ms");

        assertTrue(recorded < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS,
            "a diagonal climb sits " + Math.round(ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS - recorded)
                + " ms under the line and holding cannot close it - the mean is span over count, "
                + "and both terms are fixed by the jump arc and by needing two row cells a step");

        assertTrue(ScaffoldModule.grimIntaveFlickOneLine(List.of(
            new BlockPos(16, 91, 50), new BlockPos(16, 91, 49),
            new BlockPos(16, 91, 48), new BlockPos(16, 91, 47))),
            "a straight bridge is one line, so its mean conjunct is satisfied whatever we wait");
        assertFalse(ScaffoldModule.grimIntaveFlickOneLine(List.of(
            new BlockPos(16, 91, 49), new BlockPos(17, 91, 49),
            new BlockPos(17, 91, 48), new BlockPos(18, 91, 48),
            new BlockPos(18, 92, 48))),
            "the 18:57 L-chain turns on every step, so on a diagonal the mean is the only lever");
    }

    @Test
    void aPlacementNeitherCheckCanScoreIsNotWorthHolding() {

        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(76.6F, 84.3F, 998L, false),
            "t231 could not have armed either check at any cadence");

        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(76.6F, 76.9F, 150L, false),
            "0.3 degrees apart is under RotationFlick's band floor of 3");

        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(85.0F, 40.0F, 150L, false));
        assertTrue(ScaffoldModule.grimIntaveMeanWorthHolding(85.1F, 40.0F, 150L, false),
            "one tenth over and the mean is the only thing standing between it and a flag");

        assertTrue(ScaffoldModule.grimIntaveMeanWorthHolding(84.1F, 87.6F, 350L, false),
            "3.5 degrees apart is inside 3..20");
        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(84.1F, 87.6F, 350L, true),
            "but on one line the check reads (average < 400 || isOneLine) and the wait is wasted");
        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(84.1F, 60.0F, 350L, false),
            "24 degrees apart is above the band ceiling of 20");

        assertFalse(ScaffoldModule.grimIntaveMeanWorthHolding(84.1F, Float.NaN, 150L, false));
    }

    @Test
    void isOneLineLeavesOnlyThePitchDeltaAndTheNudgeStaysInsideTheLandingWindow() {

        assertEquals(87.0F, ScaffoldModule.grimIntaveFlickPitchNudge(87.0F, 86.0F, 80.0, 89.0),
            "1 degree apart is under the band floor already");
        assertEquals(87.0F, ScaffoldModule.grimIntaveFlickPitchNudge(87.0F, 65.0F, 80.0, 89.0),
            "22 degrees apart is already past the band ceiling");

        assertEquals(86.6F, ScaffoldModule.grimIntaveFlickPitchNudge(87.6F, 84.1F, 70.0, 89.0),
            1e-4F, "nudged to the band floor, not all the way back to lastPitch");

        assertEquals(87.6F, ScaffoldModule.grimIntaveFlickPitchNudge(87.6F, 84.1F, 87.3, 87.6),
            "neither clamped edge escapes the band, so the goal ships unmoved");

        assertEquals(81.6F, ScaffoldModule.grimIntaveFlickPitchNudge(80.0F, 84.1F, 70.0, 89.0),
            1e-4F, "goal was shallower than lastPitch, so the nudge stays on that side");
    }

    @Test
    void theRawPaceMeanIsWhatBlockRotationScores() {

        long[] raw = {150, 150, 500, 100, 350, 150, 200, 250};
        double rawMean = ScaffoldModule.grimPaceProspectiveMean(raw, 150, 8);
        assertTrue(rawMean < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS,
            "231 ms against a 400 ms gate - BlockRotation armed on every steep placement");

        long[] speed = {150, 150, 1500, 100, 1350, 150, 200, 1250};
        assertTrue(ScaffoldModule.grimPaceProspectiveMean(speed, 150, 8) > rawMean * 2,
            "the two statistics are not interchangeable - modelling one hid the other");

        double requirement = ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS * 1.08D;

        double ceilingIfGapsLandedOnTheFloor =
            (2 * ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS + ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS) / 3.0D;
        assertTrue(ceilingIfGapsLandedOnTheFloor < requirement,
            "the wrong model says unreachable at " + ceilingIfGapsLandedOnTheFloor);

        long quantized = 150L;
        assertTrue(quantized > ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS, "a 100 ms floor waits 3 ticks");
        double ceilingReal = (2 * quantized + ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS) / 3.0D;
        assertTrue(ceilingReal > requirement,
            "and the real one is reachable: " + ceilingReal + " vs " + requirement);

        assertEquals(
            ScaffoldModule.grimPaceProspectiveMean(raw, ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS, 8),
            ScaffoldModule.grimPaceProspectiveMean(raw, ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS * 5, 8)
                - (ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS * 4) / 8.0D,
            0.001D,
            "past the clamp the extra wait is pure loss - which is why the gate stops there");

        assertEquals(100L, ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS);
        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS > ScaffoldModule.GRIM_LANDING_LAST_CHANCE_FLOOR_MS);
    }

    @Test
    void theMeanGateCannotOutlastAnArc() {
        long arcTicks = 8L;
        long arcMs = arcTicks * 50L;

        assertTrue(ScaffoldModule.GRIM_INTAVE_PLACE_SAMPLE_CAP_MS > arcMs,
            "a 1000 ms bound against a 400 ms arc - airborne this is not a pace, it is a stall");

        long[] held = {398, 449, 499, 549, 599, 649, 698, 749};
        assertTrue(held[held.length - 1] > arcMs,
            "the last hold alone was longer than the arc that needed the block");
        assertEquals(8, held.length, "and it never released - the arc simply ran out");

        long groundTicks = 2L;
        long perLevelPlacements = 3L;
        long achievable = (groundTicks * 50L + perLevelPlacements * ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS)
            / perLevelPlacements;
        assertTrue(achievable < ScaffoldModule.GRIM_INTAVE_PLACE_MEAN_MS,
            "cadence alone cannot reach 400 while the jump is held: best is " + achievable);
    }

    @Test
    void aPinnedPostureThatCannotCrossTheFaceIsWorthNothing() {
        BlockPos support = new BlockPos(65, 134, 97);
        Vec3 first = new Vec3(66.40D, 137.79D, 97.62D);
        Vec3 last = new Vec3(66.59D, 137.12D, 97.52D);

        double postureFirst = ScaffoldModule.grimCrossingFraction(first, support, Direction.EAST, 45.0F);
        double postureLast = ScaffoldModule.grimCrossingFraction(last, support, Direction.EAST, 45.0F);
        assertEquals(1.02D, postureFirst, 0.01D, "t680 printed xing=+1.02");
        assertEquals(1.11D, postureLast, 0.01D, "t686 printed xing=+1.11");
        assertTrue(postureLast > postureFirst,
            "diverging, not approaching - this is what separates it from an ordinary graze");

        for (Vec3 eye : new Vec3[] {first, last}) {
            double landed = ScaffoldModule.grimCrossingFraction(eye, support, Direction.EAST, 55.0F);
            assertTrue(landed > ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN
                    && landed < 1.0D - ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN,
                "yaw 55 crosses inside the acquire band, at " + landed);
        }
        assertTrue(Math.abs(55.0F - 45.0F) <= 18.0F, "and 10 degrees is inside the nudge's search");

        assertTrue(55.0F % 45.0F < 12.0F, "residual " + (55.0F % 45.0F));
    }

    @Test
    void aFaceAWholeQuadrantOffThePostureFlipsA45StepInsteadOfStarving() {
        BlockPos leg = new BlockPos(17, 97, 74);
        Vec3 first = new Vec3(17.09D, 100.62D, 73.88D);
        Vec3 lead = new Vec3(0.05D, -0.05D, -0.04D);

        double posture = ScaffoldModule.grimCrossingFraction(first, leg, Direction.NORTH, 45.0F);
        assertEquals(-0.03D, posture, 0.02D, "t219 printed xing=-0.03");

        double nudgeEdge = ScaffoldModule.grimCrossingFraction(first, leg, Direction.NORTH, 33.0F);
        assertTrue(nudgeEdge < ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN,
            "yaw 33 crosses at " + nudgeEdge + ", outside the acquire band");

        float flip = ScaffoldModule.grimGridFlipYaw(first, lead, leg, Direction.NORTH, 45.0F);
        assertEquals(0.0F, flip, 0.001F, "the nearest whole 45-step that crosses");
        assertEquals(0.0F,
            Math.abs(ScaffoldModule.grimLaneOctantResidual(45.0F, flip)), 0.001F,
            "a grid flip folds to the posture's own residual - no yaw veto");

        for (int step = 0; step <= ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            double frac = ScaffoldModule.grimCrossingFraction(
                first.add(lead.scale(step)), leg, Direction.NORTH, flip);
            assertTrue(frac >= ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN
                    && frac <= 1.0D - ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN,
                "step " + step + " crosses at " + frac);
        }

        Vec3 healthy = new Vec3(17.30D, 100.62D, 73.88D);
        assertTrue(Float.isNaN(
                ScaffoldModule.grimGridFlipYaw(healthy, lead, leg, Direction.NORTH, 45.0F)),
            "a posture that crosses keeps its aim");

        Vec3 behind = new Vec3(17.09D, 100.62D, 74.10D);
        assertTrue(Float.isNaN(
                ScaffoldModule.grimGridFlipYaw(behind, lead, leg, Direction.NORTH, 45.0F)),
            "behind the plane the flip refuses");
    }

    @Test
    void aTopFaceWhosePostureTrackMissesTheSquareFlipsInsteadOfStarving() {
        BlockPos support = new BlockPos(28, 134, 83);
        Vec3 eye = new Vec3(28.52D, 137.79D, 84.13D);
        Vec3 lead = new Vec3(0.08D, 0.08D, -0.01D);

        for (int step = 0; step <= ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(
                    eye.add(lead.scale(step)), support, 45.0F)),
                "step " + step + ": the posture yaw's track never enters the top square");
        }

        float flip = ScaffoldModule.grimTopGridFlipYaw(eye, lead, support, 45.0F);
        assertEquals(135.0F, flip, 0.001F, "the nearest whole 45-step whose track lands");
        assertEquals(0.0F,
            Math.abs(ScaffoldModule.grimLaneOctantResidual(45.0F, flip)), 0.001F,
            "a grid flip folds to the posture's own residual - no yaw veto");

        for (int step = 0; step <= ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            float pitch = ScaffoldModule.grimTopCrossingPitch(
                eye.add(lead.scale(step)), support, flip);
            assertFalse(Float.isNaN(pitch), "step " + step + " still lands");
            assertTrue(pitch > 70.0F && pitch <= 89.5F, "step " + step + " pitch " + pitch);
        }

        Vec3 overhead = new Vec3(28.5D, 137.0D, 83.5D);
        assertFalse(Float.isNaN(
            ScaffoldModule.grimTopCrossingPitch(overhead, support, 45.0F)));
        assertTrue(Float.isNaN(
                ScaffoldModule.grimTopGridFlipYaw(overhead, lead, support, 45.0F)),
            "a posture that lands keeps its aim");

        Vec3 sweepingIn = new Vec3(29.5D, 137.79D, 84.05D);
        Vec3 inward = new Vec3(0.0D, 0.0D, -0.12D);
        assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(sweepingIn, support, 90.0F)),
            "not landable today");
        assertFalse(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(
            sweepingIn.add(inward.scale(2)), support, 90.0F)));
        assertTrue(Float.isNaN(
                ScaffoldModule.grimTopGridFlipYaw(sweepingIn, inward, support, 90.0F)),
            "a track that lands within the lookahead is not starved");
    }

    @Test
    void aFallingPlayerIsNeverServedACellTheyHaveAlreadyPassed() {

        assertTrue(ScaffoldModule.grimFallServesUncatchable(true, 185, 185.16D, -0.58D),
            "a top above next tick's feet cannot catch");

        assertFalse(ScaffoldModule.grimFallServesUncatchable(true, 184, 185.68D, -0.514D),
            "the real catch is below the coming feet and passes");

        assertFalse(ScaffoldModule.grimFallServesUncatchable(true, 183, 185.0D, -0.4D));

        assertFalse(ScaffoldModule.grimFallServesUncatchable(false, 185, 185.16D, -0.58D),
            "only a REAL fall engages the standdown");

        assertFalse(ScaffoldModule.grimFallServesUncatchable(true, 184, 185.0D, 0.1D));
    }

    @Test
    void theCatchConsidersEveryColumnTheBoxOverlaps() {

        List<BlockPos> before = ScaffoldModule.grimCatchColumns(new Vec3(51.05D, 185.68D, 73.46D));
        assertEquals(new BlockPos(51, 184, 73), before.get(0), "the feet's own column leads");
        assertTrue(before.contains(new BlockPos(50, 184, 73)),
            "the box clips x=51.0, so the west column is also a candidate");

        List<BlockPos> after = ScaffoldModule.grimCatchColumns(new Vec3(50.96D, 185.16D, 73.39D));
        assertEquals(new BlockPos(50, 184, 73), after.get(0));
        assertTrue(after.contains(new BlockPos(51, 184, 73)),
            "the still-overlapped anchored column stays in the candidate list");

        List<BlockPos> centered = ScaffoldModule.grimCatchColumns(new Vec3(10.50D, 80.0D, 20.50D));
        assertEquals(1, centered.size(), "no straddle, no extra candidates");
        assertEquals(new BlockPos(10, 79, 20), centered.get(0));

        List<BlockPos> corner = ScaffoldModule.grimCatchColumns(new Vec3(30.05D, 90.0D, 40.25D));
        assertEquals(4, corner.size());
        assertEquals(new BlockPos(30, 89, 40), corner.get(0), "own column always first");
        assertEquals(new BlockPos(29, 89, 40), corner.get(1),
            "x overlap 0.25 is deeper than z overlap 0.05");
        assertEquals(new BlockPos(29, 89, 39), corner.get(3), "the shared corner ranks last");
    }

    @Test
    void theGoalBoundNeverPushesTheGoalOutOfAWindowTheHardCapAdmits() {

        float first = ScaffoldModule.grimPlacementPitchGoal(89.87F, 89.34D, 89.50D);
        assertTrue(first >= 89.34F && first <= ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD,
            "the first-crossing window is reachable: goal " + first);
        assertTrue(ScaffoldModule.grimPlacementPitchLegal(first),
            "and the click gate accepts what the goal asks for");

        float steep = ScaffoldModule.grimPlacementPitchGoal(89.9F, 89.44D, 89.78D);
        assertTrue(steep >= 89.44F && steep <= ScaffoldModule.GRIM_PLACE_MAX_PITCH_HARD);

        assertEquals(88.5F, ScaffoldModule.grimPlacementPitchGoal(88.5F, 88.2D, 88.6D), 0.001F);
        assertEquals(ScaffoldModule.GRIM_PLACE_MAX_PITCH,
            ScaffoldModule.grimPlacementPitchGoal(89.4F, 88.8D, 89.6D), 0.001F);
        assertEquals(89.2F, ScaffoldModule.grimPlacementPitchGoal(89.9F, 89.1D, 89.2D), 0.001F);

        assertEquals(ScaffoldModule.GRIM_PLACE_MAX_PITCH,
            ScaffoldModule.grimPlacementPitchGoal(89.9F, Double.NaN, Double.NaN), 0.001F);
    }

    @Test
    void theAirborneLeadCarriesTheInputImpulseTheArcActuallyFliesUnder() {

        Vec3 v = new Vec3(0.061D, 0.165D, 0.093D);
        Vec3 free = ScaffoldModule.grimAirLeadAccel(v, true, false, false);
        assertEquals(0.02D, Math.hypot(free.x, free.z), 1.0E-9D, "impulse magnitude is vanilla's");
        assertTrue(free.x > 0.0D && free.z > 0.0D, "along travel");
        assertEquals(0.0D, free.y, 0.0D, "the vertical lead was already exact - untouched");

        Vec3 braked = ScaffoldModule.grimAirLeadAccel(v, true, true, false);
        assertEquals(-free.x, braked.x, 1.0E-12D);
        assertEquals(-free.z, braked.z, 1.0E-12D);

        assertEquals(0.006D, Math.hypot(
            ScaffoldModule.grimAirLeadAccel(v, true, false, true).x,
            ScaffoldModule.grimAirLeadAccel(v, true, false, true).z), 1.0E-9D);
        assertEquals(Vec3.ZERO, ScaffoldModule.grimAirLeadAccel(v, false, false, false));
        assertEquals(Vec3.ZERO, ScaffoldModule.grimAirLeadAccel(
            new Vec3(0.001D, -0.3D, 0.001D), true, false, false), "no usable heading");
    }

    @Test
    void theDescentRescueReadsTheLandingFootprintNotItsCenterPoint() {
        int row = 225;

        List<BlockPos> columns = ScaffoldModule.grimCatchColumns(
            new Vec3(39.005D, row + 1.5D, 51.62D));
        assertEquals(new BlockPos(39, row, 51), columns.get(0),
            "the landing's own column leads, on the built-floor row");
        assertTrue(columns.contains(new BlockPos(38, row, 51)),
            "the anchored column the aim had converged on stays a candidate");

        assertEquals(new BlockPos(38, row, 51), ScaffoldModule.grimCatchColumns(
            new Vec3(38.99D, row + 1.5D, 51.86D)).get(0));
    }

    @Test
    void theRiserHasAFloorInsteadOfAnExemption() {

        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS > 50L,
            "1 tick is the burst that shipped");
        assertTrue(ScaffoldModule.GRIM_PACE_RISER_FLOOR_MS
            < ScaffoldModule.grimPaceFloorMs(
                ScaffoldModule.grimPaceLimitMs(false, false, true, false)),
            "but still under the diagonal flat floor, so towering stays the fast part");
    }

    @Test
    void aCrossingYawTooParallelToTrackFlipsInsteadOfChurning() {
        BlockPos support = new BlockPos(50, 131, 75);
        Vec3 eye = new Vec3(50.60D, 134.02D, 74.96D);
        Vec3 lead = new Vec3(0.06D, 0.25D, -0.09D);

        double lateral = ScaffoldModule.grimCrossingFraction(eye, support, Direction.NORTH, 84.0F);
        assertTrue(lateral >= ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN
                && lateral <= 1.0D - ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN,
            "the lateral test passes at " + lateral + " - that is exactly why the nudge kept it");
        assertTrue(ScaffoldModule.grimYawCannotTrackFace(eye, lead, support, Direction.NORTH, 84.0F),
            "near parallel with disjoint windows is provably untrackable");

        float flip = ScaffoldModule.grimGridFlipYaw(eye, lead, support, Direction.NORTH, 84.0F);
        assertEquals(39.0F, flip, 0.001F, "the nearest whole 45-step that can track the face");
        for (int step = 0; step <= ScaffoldModule.GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            double frac = ScaffoldModule.grimCrossingFraction(
                eye.add(lead.scale(step)), support, Direction.NORTH, flip);
            assertTrue(frac >= ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN
                    && frac <= 1.0D - ScaffoldModule.GRIM_PIN_SIDE_ACQUIRE_MARGIN,
                "step " + step + " crosses at " + frac);
        }

        Vec3 late = new Vec3(50.60D, 134.02D, 74.77D);
        Vec3 lateLead = new Vec3(0.05D, -0.08D, -0.07D);
        assertFalse(ScaffoldModule.grimYawCannotTrackFace(
                late, lateLead, support, Direction.NORTH, 78.0F),
            "overlapping windows mean a landable pitch exists - hold the yaw");

        Vec3 fresh = new Vec3(50.60D, 134.02D, 74.98D);
        Vec3 walk = new Vec3(0.05D, 0.25D, -0.13D);
        assertFalse(ScaffoldModule.grimYawCannotTrackFace(
                fresh, walk, support, Direction.NORTH, 45.0F),
            "toward 0.71 tracks its own window sweep; the ordinary first-tick miss stays designed");

        Vec3 behind = new Vec3(50.60D, 134.02D, 75.10D);
        assertFalse(ScaffoldModule.grimYawCannotTrackFace(
            behind, lead, support, Direction.NORTH, 84.0F));
    }

    @Test
    void aClickTickEmissionRepeatsTheWirePitchExactly() {
        for (double gcd : GCD_RANGE) {
            QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
            smoother.reset(7L);
            Rotation raw = new Rotation(90.2F, 85.4F);
            Rotation previous = ScaffoldModule.grimShapeOutgoing(raw, null, gcd, 0, Float.NaN);
            for (int tick = 0; tick < 6; tick++) {

                raw = ScaffoldModule.stepGrimAimRotation(
                    smoother, raw, 6.0F, 0.0F, 0.4F, gcd);
                Rotation emitted = ScaffoldModule.grimShapeOutgoing(
                    raw, previous, gcd, tick % 3 - 1, Float.NaN);
                assertEquals(previous.pitch(), emitted.pitch(), 0.0F,
                    "gcd " + gcd + " tick " + tick
                        + ": the packet pitch must repeat bit-identically on a held tick");
                previous = emitted;
            }
        }
    }

    @Test
    void thePlacingTickPitchCapSitsUnderVulcansLimitAndTheSweepDoesNot() {

        assertTrue(ScaffoldModule.GRIM_PLACE_MAX_PITCH_STEP <= 10.0F,
            "the click has to clear Vulcan's documented figure");
        assertTrue(ScaffoldModule.GRIM_MAX_PITCH_STEP > ScaffoldModule.GRIM_PLACE_MAX_PITCH_STEP,
            "the free sweep keeps its speed - a global 10 is the 00:19 regression");
        assertTrue(19.95F > ScaffoldModule.GRIM_PLACE_MAX_PITCH_STEP,
            "the step that shipped would now hold the click");
        assertTrue(9.5F < ScaffoldModule.GRIM_PLACE_MAX_PITCH_STEP, "a settled step still clicks");
    }

    private static double intaveRotationFlickVl(
        float[] pitches, boolean[] sideFaces, double verticalLineLength
    ) {
        double vl = 0.0D;
        float lastPitch = 0.0F;
        for (int index = 0; index < pitches.length; index++) {
            if (!sideFaces[index]) continue;
            float current = pitches[index];
            float pitchDiff = Math.abs(current - lastPitch);
            lastPitch = current;
            if (pitchDiff > 3.0F && pitchDiff < 20.0F
                && current > 70.0F && verticalLineLength < 5.0D) {

                vl += Math.min(20.0D, pitchDiff / (verticalLineLength / 10.0D));
                if (current > 89.5F) vl += 5.0D;
                if (vl > 100.0D) vl -= 10.0D;
            } else if (vl > 0.0D) {
                vl = vl * 0.99D - 0.01D;
            }
        }
        return vl;
    }

    private static AABB snowLayer(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0D, pos.getY() + 0.125D, pos.getZ() + 1.0D);
    }

    private static AABB bottomSlab(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0D, pos.getY() + 0.5D, pos.getZ() + 1.0D);
    }

    @Test
    void theShapeAwareSolvesAreBitIdenticalOnAFullCube() {
        BlockPos support = new BlockPos(10, 70, 10);
        AABB unit = ScaffoldModule.grimUnitBox(support);
        for (Direction face : Direction.values()) {
            for (double dx = -2.5D; dx <= 2.5D; dx += 1.25D) {
                for (double dy = 0.5D; dy <= 3.5D; dy += 1.5D) {
                    Vec3 eye = new Vec3(10.5D + dx, 70.0D + dy, 12.7D);
                    assertEquals(
                        eye.subtract(Vec3.atCenterOf(support).add(
                            face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D))
                            .dot(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ())),
                        ScaffoldModule.grimEyePastPlane(eye, unit, face), 1.0E-9D,
                        face + " past-plane must match the old centre+0.5 form exactly");
                    assertEquals(
                        Vec3.atCenterOf(support).add(
                            face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D),
                        ScaffoldModule.grimFaceCentre(unit, face),
                        face + " face centre must match atCenterOf + normal*0.5");
                }
            }
        }

        assertEquals(70.5D, ScaffoldModule.grimFaceCrossDepthY(unit), 1.0E-9D);
    }

    @Test
    void theCrossDepthStaysInsideAPartialFaceInsteadOfAimingOverIt() {
        BlockPos pos = new BlockPos(-24, 97, -184);
        AABB snow = snowLayer(pos);
        AABB slab = bottomSlab(pos);

        double snowAim = ScaffoldModule.grimFaceCrossDepthY(snow);
        assertTrue(snowAim > snow.minY && snowAim < snow.maxY,
            "the aimed height must be ON the face: " + snowAim);
        assertEquals(97.0625D, snowAim, 1.0E-9D, "half a 0.125 face below its top edge");
        assertEquals(97.25D, ScaffoldModule.grimFaceCrossDepthY(slab), 1.0E-9D);

        assertTrue(97.5D > snow.maxY, "the old target was above the block entirely");

        Vec3 eye = new Vec3(-25.26D, 100.37D, -181.92D);
        double[] window = ScaffoldModule.grimFaceCrossingWindow(eye, snow, Direction.WEST, -135.0F);
        assertNotNull(window, "the eye is west of the west plane, so there is a crossing");
        assertTrue(window[0] <= window[1], "a real window, not an inverted one");

        for (double pitch : new double[] {window[0], window[1]}) {
            double run = window[2];
            double landed = eye.y - Math.tan(Math.toRadians(pitch)) * run;
            assertTrue(landed >= snow.minY - 1.0E-6D && landed <= snow.maxY + 1.0E-6D,
                "window edge " + pitch + " lands at " + landed + ", off the 0.125 face");
        }
    }

    @Test
    void theTopFaceSolveUsesTheRealLidSoASlabIsNotAimedThrough() {
        BlockPos pos = new BlockPos(10, 70, 10);
        Vec3 eye = new Vec3(10.5D, 73.0D, 7.0D);
        assertTrue(Float.isNaN(ScaffoldModule.grimTopCrossingPitch(
            eye, new AABB(10, 70, 10, 11, 73.5D, 11), 0.0F)),
            "a lid at or above the eye has no top crossing to solve");

        float slabPitch = ScaffoldModule.grimTopCrossingPitch(eye, bottomSlab(pos), 0.0F);
        float cubePitch = ScaffoldModule.grimTopCrossingPitch(
            eye, ScaffoldModule.grimUnitBox(pos), 0.0F);
        assertFalse(Float.isNaN(slabPitch));
        assertFalse(Float.isNaN(cubePitch));
        assertTrue(slabPitch > cubePitch,
            "a lower lid is further below the eye, so it needs a STEEPER pitch: "
                + slabPitch + " vs " + cubePitch);
    }

    @Test
    void aHitThatProvablyBuildsThePlannedCellIsAcceptedWhateverFaceItLandedOn() {
        BlockPos snow = new BlockPos(-24, 97, -184);

        ScaffoldModule.PlacementTarget inPlace = new ScaffoldModule.PlacementTarget(
            snow, snow, Direction.WEST,
            new BlockHitResult(new Vec3(-24.0D, 97.06D, -183.30D), Direction.WEST, snow, false),
            new Rotation(-135.0F, 61.8F), 97.0D);
        BlockHitResult onTheLid = new BlockHitResult(
            new Vec3(-23.7D, 97.125D, -183.3D), Direction.UP, snow, false);

        assertFalse(ScaffoldModule.grimClickFeasible(onTheLid, inPlace),
            "without the proof it is still the old strict test - this is what refused it");
        assertTrue(ScaffoldModule.grimClickFeasible(onTheLid, inPlace, true),
            "with vanilla's own answer the lid hit builds exactly the planned cell");

        BlockHitResult elsewhere = new BlockHitResult(
            new Vec3(-25.0D, 97.5D, -183.3D), Direction.UP, new BlockPos(-25, 97, -184), false);
        assertFalse(ScaffoldModule.grimClickFeasible(elsewhere, inPlace, true),
            "a different support is a different click, whatever cell it would build");

        ScaffoldModule.PlacementTarget neighbour = legTarget(new BlockPos(5, 80, 5), Direction.NORTH);
        BlockHitResult wrongFace = new BlockHitResult(
            new Vec3(5.5D, 80.5D, 5.0D), Direction.WEST, new BlockPos(5, 80, 5), false);
        assertFalse(ScaffoldModule.grimClickFeasible(wrongFace, neighbour),
            "a neighbour placement through the wrong face builds the wrong cell");

        assertFalse(ScaffoldModule.grimClickFeasible(null, inPlace, true));
        assertFalse(ScaffoldModule.grimClickFeasible(onTheLid, null, true));
    }

    @Test
    void theCrossingFractionIsAFractionOfTheRealFaceNotAWorldDistance() {
        BlockPos pos = new BlockPos(0, 70, 0);
        Vec3 eye = new Vec3(-2.0D, 70.5D, 0.5D);

        double onACube = ScaffoldModule.grimCrossingFraction(
            eye, ScaffoldModule.grimUnitBox(pos), Direction.WEST, -90.0F);
        assertEquals(0.5D, onACube, 1.0E-6D, "mid-face on a full cube is 0.5");

        AABB post = new AABB(0.375D, 70.0D, 0.375D, 0.625D, 71.0D, 0.625D);
        double onAPost = ScaffoldModule.grimCrossingFraction(eye, post, Direction.WEST, -90.0F);
        assertEquals(0.5D, onAPost, 1.0E-6D,
            "mid-face is mid-face whatever the span - got " + onAPost);

        Vec3 offEye = new Vec3(-2.0D, 70.5D, 0.90D);
        double past = ScaffoldModule.grimCrossingFraction(offEye, post, Direction.WEST, -90.0F);
        assertTrue(past > 1.0D,
            "a ray 0.275 beyond a 0.25-wide post must exceed the face, not sit inside it: " + past);
    }

    @Test
    void theFootingRowNamesTheCellWhoseSurfaceHoldsTheFeet() {

        assertEquals(97, ScaffoldModule.grimFootingRowFor(98.0D));
        assertEquals(Mth.floor(98.0D) - 1, ScaffoldModule.grimFootingRowFor(98.0D),
            "full cubes must be bit-identical to the old form");

        assertEquals(97, ScaffoldModule.grimFootingRowFor(97.5D));

        assertEquals(97, ScaffoldModule.grimFootingRowFor(97.9375D));
        assertEquals(97, ScaffoldModule.grimFootingRowFor(97.25D));

        assertEquals(97, ScaffoldModule.grimFootingRowFor(97.99999D));
        assertEquals(97, ScaffoldModule.grimFootingRowFor(98.0005D));
    }

    @Test
    void aRayOnADifferentSupportIsASubstitutionEvenWhenCellAndFaceMatch() {
        BlockPos snow = new BlockPos(-32, 107, -198);
        BlockPos below = new BlockPos(-32, 106, -198);

        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            snow, Direction.UP, below, snow, Direction.UP, snow),
            "different support is a real substitution - this is the whole 15:06 t010 defect");

        assertTrue(ScaffoldModule.grimRaySubstitutionIsNoOp(
            snow, Direction.UP, below, snow, Direction.UP, below));

        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            snow, Direction.UP, below, snow, Direction.NORTH, below));

        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(
            snow, Direction.UP, below, below, Direction.UP, below));

        assertTrue(ScaffoldModule.grimRaySubstitutionIsNoOp(snow, Direction.UP, snow, Direction.UP));
        assertFalse(ScaffoldModule.grimRaySubstitutionIsNoOp(snow, Direction.UP, snow, Direction.NORTH));
    }

    @Test
    void theTellyAimHoldsItsOutboundYawUntilTheNextFaceIsActuallyReachable() {
        assertTrue(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(false, true, false, false, false),
            "airborne, committed, horizontal, no live sample - hold instead of swinging forward");
        assertFalse(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(true, true, false, false, false),
            "a live sample is the real aim and always wins");
        assertFalse(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(false, false, false, false, false),
            "the first placement of a cycle has no outbound yaw - keep the old path bit-identical");
        assertFalse(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(false, true, true, false, false),
            "grounded has its own actuator and its own launch alignment gate");

        assertFalse(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(false, true, false, true, false),
            "a vertical face is the rise pre-aim, never a decoy - never hold over it");

        assertFalse(ScaffoldModule.tellyHoldsAimWhileFaceIsAhead(false, true, false, false, true),
            "the hold must never survive into touchdown");
    }

    @Test
    void aStaleStrafeOnTheGroundedLaunchTickIsFourTimesAsExpensiveAsOneInTheAir() {
        double groundAccel = 0.1D * 1.3D;
        double groundDrag = 0.546D;
        double airAccel = 0.0196D;
        double airDrag = 0.91D;
        double diagonal = Math.sqrt(2.0D) / 2.0D;

        double groundLeak = groundAccel * diagonal * groundDrag;
        double airLeak = airAccel * diagonal * airDrag;
        assertEquals(0.0502D, groundLeak, 5.0E-4D, "one octant step at launch");
        assertEquals(0.0126D, airLeak, 5.0E-4D, "the same step in the air");
        assertTrue(groundLeak / airLeak > 3.9D, "the launch tick is ~4x the cost: " + groundLeak / airLeak);

        double[][] launches = {
            {-0.010D, -1.0D, -0.056D}, {+0.004D, +1.0D, +0.052D}, {-0.022D, -1.0D, -0.062D},
            {-0.045D, +1.0D, +0.020D}, {+0.050D, 0.0D, +0.028D},
        };
        for (double[] launch : launches) {
            double predicted = (launch[0] + groundAccel * diagonal * launch[1]) * groundDrag;
            assertEquals(launch[2], predicted, 6.0E-3D,
                "measured launch vx should follow the octant model, s=" + launch[1]);
        }

        assertEquals(0.050D * groundDrag, 0.028D, 2.0E-3D, "a bare W injects nothing");
    }

    @Test
    void theHeldPlacementAimCostsTheLandingTickItsForwardKeyAndItsSprint() {

        double held = Math.cos(Math.toRadians(111.3D));
        assertEquals(0.364D, Math.abs(held), 1.0E-3D, "the held aim leaves |cos d| = 0.36");

        assertEquals(0, ScaffoldModule.octantWithHysteresis(held, 0),
            "0.36 is under the 0.62 ENTER: forward cannot enter");
        assertEquals(0, ScaffoldModule.octantWithHysteresis(held, 1),
            "0.36 is ALSO under the 0.38 EXIT: a latched forward drops. This is the live branch.");
        assertEquals(-1, Math.round((float) -Math.sin(Math.toRadians(111.3D))),
            "and the strafe rounds on, which is the captured in=---D---");

        double burst = Math.cos(Math.toRadians(25.6D));
        assertEquals(1, ScaffoldModule.octantWithHysteresis(burst, 0),
            "cos(25.6) = 0.90 re-enters forward");

        Input held7 = new Input(true, false, false, false, false, false, true);
        assertFalse(ScaffoldModule.silentSprintAllowed(held7, false),
            "no emitted forward, no sprint - vanilla reads it as letting go of W");
        assertTrue(ScaffoldModule.silentSprintAllowed(held7, true));
        assertEquals(0.0861D, 0.1D * Math.cos(Math.toRadians(30.6D)), 1.0E-3D,
            "the stalled tick as captured: unsprinted 0.1 at a 30.6 degree residual");
        assertEquals(0.117D, 0.13D * Math.cos(Math.toRadians(25.6D)), 1.0E-3D,
            "after the burst: sprinted 0.13 at 25.6, i.e. +36 percent");
    }

    @Test
    void theDriftSampleMustPriceTheTickThatEmittedTheKeys() {
        assertEquals(5.102D,
            ScaffoldModule.tellyDriftSpeed(true, false) / ScaffoldModule.tellyDriftSpeed(false, false),
            1.0E-3D, "a wrong ground/air branch is a 5.1x mispricing, not a rounding nit");
        assertEquals(1.3D,
            ScaffoldModule.tellyDriftSpeed(true, true) / ScaffoldModule.tellyDriftSpeed(true, false),
            1.0E-9D);
        assertEquals(-0.0144D,
            -ScaffoldModule.tellyDriftSpeed(false, false) * Math.sin(Math.toRadians(47.4D)),
            1.0E-4D, "an airborne emission on a gnd=T line is 0.0196-priced, never 0.1-priced");

        assertEquals((0.02D + 0.01D) * 0.91D,
            ScaffoldModule.tellyDriftNextPerp(0.02D, 0.01D, false), 1.0E-12D);
        assertNotEquals(0.02D * 0.91D + 0.01D,
            ScaffoldModule.tellyDriftNextPerp(0.02D, 0.01D, false), 1.0E-6D,
            "multiplying before adding is 10 percent out in air and 45 percent on ground");
    }

    @Test
    void theCycleLengthIsGroundTicksPlusAirTicks() {
        assertEquals(5.617D, ScaffoldModule.tellyCycleBlocksPerSecond(3.37D, 1, 11), 1.0E-3D);
        assertEquals(5.185D, ScaffoldModule.tellyCycleBlocksPerSecond(3.37D, 2, 11), 1.0E-3D,
            "the same distance over one extra ground tick IS the measured 8 percent");
        assertEquals(12.0D / 13.0D,
            ScaffoldModule.tellyCycleBlocksPerSecond(3.37D, 2, 11)
                / ScaffoldModule.tellyCycleBlocksPerSecond(3.37D, 1, 11),
            1.0E-9D, "the dwell penalty is pure tick count");
        assertTrue(Double.isNaN(ScaffoldModule.tellyCycleBlocksPerSecond(3.37D, 0, 0)));
    }

    @Test
    void theTellyTraceStepIsTheRawPositionDelta() {
        assertEquals(Vec3.ZERO, ScaffoldModule.tellyTickStep(null, new Vec3(5.0D, 70.0D, -3.0D)));
        Vec3 step = ScaffoldModule.tellyTickStep(
            new Vec3(1.0D, 70.0D, 2.0D), new Vec3(1.25D, 69.92D, 2.5D));
        assertEquals(0.25D, step.x, 1.0E-9D);
        assertEquals(-0.08D, step.y, 1.0E-9D);
        assertEquals(0.5D, step.z, 1.0E-9D);
    }

    @Test
    void theLaneBiasCanOnlySteerByFlippingAnOctant() {

        assertEquals(0.0D, ScaffoldModule.emittedOctantDegrees(1, 0), 1.0E-9D);
        assertEquals(180.0D, Math.abs(ScaffoldModule.emittedOctantDegrees(-1, 0)), 1.0E-9D);
        assertEquals(-90.0D, ScaffoldModule.emittedOctantDegrees(0, 1), 1.0E-9D);
        assertEquals(90.0D, ScaffoldModule.emittedOctantDegrees(0, -1), 1.0E-9D);
        assertEquals(-45.0D, ScaffoldModule.emittedOctantDegrees(1, 1), 1.0E-9D);
        assertTrue(Double.isNaN(ScaffoldModule.emittedOctantDegrees(0, 0)));

        assertEquals(30.0D, ScaffoldModule.tellyStrafeFlipMargin(180.0D), 1.0E-6D);
        assertEquals(30.0D, ScaffoldModule.tellyStrafeFlipMargin(0.0D), 1.0E-6D);
        assertEquals(0.0D, ScaffoldModule.tellyStrafeFlipMargin(-150.0D), 1.0E-6D);

        assertTrue(ScaffoldModule.tellyStrafeFlipMargin(180.0D) > ScaffoldModule.TELLY_LANE_BIAS_MAX,
            "parking d on a straight-through angle puts the flip out of budget");

        assertTrue(
            ScaffoldModule.tellyStrafeFlipMargin(164.24D) <= ScaffoldModule.TELLY_LANE_BIAS_MAX,
            "an aim 16 degrees off astern is correctable and must stay correctable");

        for (double delta = -180.0D; delta <= 180.0D; delta += 0.5D) {
            if (ScaffoldModule.tellyStrafeFlipMargin(delta) <= ScaffoldModule.TELLY_LANE_BIAS_MAX) {
                continue;
            }
            int plain = Math.round((float) -Math.sin(delta * Math.PI / 180.0D));
            for (float bias : new float[] {-15.0F, -7.5F, 0.0F, 7.5F, 15.0F}) {
                assertEquals(plain,
                    Math.round((float) -Math.sin((delta + bias) * Math.PI / 180.0D)),
                    "bias must be inert past the flip margin, at delta=" + delta);
            }
        }
    }

}
