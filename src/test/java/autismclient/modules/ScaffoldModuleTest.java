package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismRotationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScaffoldModuleTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void fixedProfileConstantsRemainHidden() {
        assertEquals(5, ScaffoldModule.SLOT_RESET_TICKS);
        assertEquals(5, ScaffoldModule.ROTATION_RESET_TICKS);
        assertEquals(2.0F, ScaffoldModule.ROTATION_RESET_THRESHOLD);
        assertEquals(180.0F, ScaffoldModule.MAX_TURN_SPEED);
        assertEquals(0.0D, ScaffoldModule.MIN_FACE_DISTANCE);
        assertEquals(1.0F, ScaffoldModule.TIMER_MULTIPLIER);
    }

    @Test
    void grimIsDefaultAndAllModesAreAvailable() {
        ChoiceSetting mode = new ChoiceSetting(
            "mode", "Mode", "Grim", "Grim", "Vulcan", "Fast", "Telly");

        assertEquals("Grim", mode.defaultValue());
        assertEquals(List.of("Grim", "Vulcan", "Fast", "Telly"), mode.choices());
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

}
