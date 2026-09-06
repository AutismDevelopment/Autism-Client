package autismclient.modules;

import autismclient.util.AutismHumanRotation;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismRotationUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraModuleTest {
    @Test
    void boxedDistanceUsesHitboxInsteadOfEntityOrigin() {
        AABB box = new AABB(4.0, 1.0, -0.5, 5.0, 3.0, 0.5);

        assertEquals(9.0, box.distanceToSqr(new Vec3(1.0, 2.0, 0.0)), 1.0E-9);
        assertEquals(0.0, box.distanceToSqr(new Vec3(4.5, 2.0, 0.0)), 1.0E-9);
    }

    @Test
    void stabilizedCyclesStayWithinAUTISMDefaults() {
        KillAuraModule.RollingClickArray array =
            new KillAuraModule.RollingClickArray(KillAuraModule.CLICK_CYCLE, KillAuraModule.CLICK_ITERATIONS);
        int[] cycle = new int[KillAuraModule.CLICK_CYCLE];
        Random random = new Random(0xC0FFEE);

        for (int i = 0; i < KillAuraModule.CLICK_ITERATIONS; i++) {
            Arrays.fill(cycle, 0);
            KillAuraModule.stabilizedFill(cycle, random);
            array.push(cycle);
            array.advance(KillAuraModule.CLICK_CYCLE);
        }

        assertTrue(array.cycleClickCount(0) >= KillAuraModule.CPS_MIN);
        assertTrue(array.cycleClickCount(0) <= KillAuraModule.CPS_MAX);
        assertTrue(array.cycleClickCount(KillAuraModule.CLICK_CYCLE) >= KillAuraModule.CPS_MIN);
        assertTrue(array.cycleClickCount(KillAuraModule.CLICK_CYCLE) <= KillAuraModule.CPS_MAX);

        for (int tick = 0; tick < KillAuraModule.CLICK_CYCLE; tick++) {
            if (array.advance(1)) {
                int[] refill = new int[KillAuraModule.CLICK_CYCLE];
                KillAuraModule.stabilizedFill(refill, random);
                array.push(refill);
            }
        }
        assertTrue(array.cycleClickCount(0) >= KillAuraModule.CPS_MIN);
        assertTrue(array.cycleClickCount(0) <= KillAuraModule.CPS_MAX);
    }

    @Test
    void attackCycleSpreadsIntervalsWiderThanAnEvenSplit() {

        int distinctTotal = 0;
        int cycles = 400;
        for (long seed = 0; seed < cycles; seed++) {
            int[] cycle = new int[KillAuraModule.CLICK_CYCLE];
            KillAuraModule.stabilizedFill(cycle, new Random(seed * 31L + 7L));
            List<Integer> clicks = new ArrayList<>();
            for (int tick = 0; tick < cycle.length; tick++) {
                assertTrue(cycle[tick] <= 1, "more than one attack was scheduled in a tick");
                if (cycle[tick] == 1) clicks.add(tick);
            }
            Set<Integer> gaps = new HashSet<>();
            for (int i = 0; i < clicks.size(); i++) {
                int next = clicks.get((i + 1) % clicks.size());
                if (i + 1 == clicks.size()) next += cycle.length;
                gaps.add(next - clicks.get(i));
            }
            distinctTotal += gaps.size();
        }
        assertTrue(distinctTotal / (double) cycles > 2.5,
            "attack intervals are no more varied than an even split of the cycle");
    }

    @Test
    void attackCycleNeverUsesOneRepeatedCircularGap() {
        for (long seed = 0; seed < 200; seed++) {
            int[] cycle = new int[KillAuraModule.CLICK_CYCLE];
            KillAuraModule.stabilizedFill(cycle, new Random(seed));
            List<Integer> clicks = new ArrayList<>();
            for (int tick = 0; tick < cycle.length; tick++) {
                assertTrue(cycle[tick] <= 1, "more than one attack was scheduled in a tick");
                if (cycle[tick] == 1) clicks.add(tick);
            }

            assertTrue(clicks.size() >= KillAuraModule.CPS_MIN);
            assertTrue(clicks.size() <= KillAuraModule.CPS_MAX);
            int minimumGap = Integer.MAX_VALUE;
            int maximumGap = Integer.MIN_VALUE;
            for (int i = 0; i < clicks.size(); i++) {
                int next = clicks.get((i + 1) % clicks.size());
                if (i + 1 == clicks.size()) next += cycle.length;
                int gap = next - clicks.get(i);
                minimumGap = Math.min(minimumGap, gap);
                maximumGap = Math.max(maximumGap, gap);
            }
            assertTrue(maximumGap > minimumGap, "attack cycle became metronomic for seed " + seed);
        }
    }

    @Test
    void combatAimPointIsClampedInsideTheRealHitbox() {
        AABB box = new AABB(-0.3, 10.0, 2.7, 0.3, 11.8, 3.3);
        Vec3 low = KillAuraModule.safeAimPoint(box, -10.0, -10.0, -10.0);
        Vec3 high = KillAuraModule.safeAimPoint(box, 10.0, 10.0, 10.0);

        assertEquals(box.minX + box.getXsize() * KillAuraModule.AIM_HORIZONTAL_INSET, low.x, 1.0E-9);
        assertEquals(box.minY + box.getYsize() * KillAuraModule.AIM_VERTICAL_INSET, low.y, 1.0E-9);
        assertEquals(box.minZ + box.getZsize() * KillAuraModule.AIM_HORIZONTAL_INSET, low.z, 1.0E-9);
        assertEquals(box.maxX - box.getXsize() * KillAuraModule.AIM_HORIZONTAL_INSET, high.x, 1.0E-9);
        assertEquals(box.maxY - box.getYsize() * KillAuraModule.AIM_VERTICAL_INSET, high.y, 1.0E-9);
        assertEquals(box.maxZ - box.getZsize() * KillAuraModule.AIM_HORIZONTAL_INSET, high.z, 1.0E-9);
    }
    @Test
    void accuracyGovernorDiscardsBufferNoLiveFightCouldFeed() {
        KillAuraModule.AccuracyGovernor governor = new KillAuraModule.AccuracyGovernor();
        governor.onOutgoingRotation(0.0F, 0.0F, 0.0F);

        for (int i = 1; i <= 15; i++) {
            governor.onAttackSent();
            governor.onOutgoingRotation(10.0F * i, 0.0F, 0.0F);
        }
        assertTrue(governor.speedAtRisk(), "fast tracked packets inside the window must be at risk");
        assertTrue(governor.errorAtRisk());

        for (int i = 0; i <= KillAuraModule.AccuracyGovernor.STALE_TICKS; i++) {
            governor.onOutgoingRotation(150.0F, 0.0F, Float.NaN);
        }
        assertFalse(governor.speedAtRisk(), "a buffer no live fight could feed must be discarded");
        assertFalse(governor.errorAtRisk());
    }

    @Test
    void accuracyGovernorKeepsLiveBufferAcrossNormalAttackGaps() {
        KillAuraModule.AccuracyGovernor governor = new KillAuraModule.AccuracyGovernor();
        governor.onOutgoingRotation(0.0F, 0.0F, 0.0F);
        for (int i = 1; i <= 15; i++) {
            governor.onAttackSent();
            governor.onOutgoingRotation(10.0F * i, 0.0F, 0.0F);
        }
        assertTrue(governor.speedAtRisk());

        for (int i = 0; i < KillAuraModule.AccuracyGovernor.STALE_TICKS - 1; i++) {
            governor.onOutgoingRotation(150.0F, 0.0F, Float.NaN);
        }
        assertTrue(governor.speedAtRisk(), "a live window must survive ordinary inter-attack gaps");
    }

    @Test
    void theReturnToCameraSweepKeepsRidingThePacketsAfterADisable() {

        assertTrue(KillAuraModule.silentCorrectionApplies(true, false, false, false),
            "a live wind-down owns the packets with the module disabled");
        assertFalse(KillAuraModule.silentCorrectionApplies(false, false, false, false),
            "no sweep and no module means the real camera owns the packets");

        assertFalse(KillAuraModule.silentCorrectionApplies(true, false, false, true),
            "Scaffold's rotation must win over our release sweep");
        assertFalse(KillAuraModule.silentCorrectionApplies(false, true, true, true));

        assertTrue(KillAuraModule.silentCorrectionApplies(false, true, true, false));
        assertFalse(KillAuraModule.silentCorrectionApplies(false, true, false, false));
    }

    @Test
    void theThrowableVerdictIsFrozenForTheWholeTickItWasResolvedOn() {

        KillAuraModule.TickVerdict verdict = new KillAuraModule.TickVerdict();
        boolean[] live = { false };

        assertFalse(verdict.resolve(100, () -> live[0]), "HEAD of the tick: no throwable held");
        live[0] = true;
        assertFalse(verdict.resolve(100, () -> live[0]),
            "the packet must read the verdict this tick opened with, not the swapped hand");
        assertTrue(verdict.resolve(101, () -> live[0]),
            "the next tick resolves afresh - that is where the wind-down latches");

        live[0] = false;
        assertTrue(verdict.resolve(101, () -> live[0]));
        assertFalse(verdict.resolve(102, () -> live[0]));
    }

    @Test
    void theWindDownCapIsTheEmittedCeilingMinusTheShapersBudget() {

        assertTrue(AutismKillAuraRotation.WIND_DOWN_MAX_YAW_STEP < 40.0F,
            "the wind-down yaw cap IS the emitted delta on this arm and must clear 40");
        assertTrue(AutismKillAuraRotation.WIND_DOWN_MAX_PITCH_STEP < 40.0F,
            "the wind-down pitch cap IS the emitted delta on this arm and must clear 40");
        assertEquals(ScaffoldModule.GRIM_WIND_DOWN_MAX_YAW_STEP,
            AutismKillAuraRotation.WIND_DOWN_MAX_YAW_STEP, 1.0E-4F,
            "one number for both arms - move it in one place and it stops being priced");
        assertEquals(ScaffoldModule.GRIM_WIND_DOWN_MAX_PITCH_STEP,
            AutismKillAuraRotation.WIND_DOWN_MAX_PITCH_STEP, 1.0E-4F);

        double coarsest = 0.6144D;
        assertTrue(Math.floor(AutismKillAuraRotation.WIND_DOWN_MAX_YAW_STEP / coarsest) * coarsest
            + 3.0D * coarsest + 0.20D < 40.0D, "the shared cap must survive the worse shaper");

        AutismHumanRotation.Stream stream = new AutismHumanRotation.Stream(new Random(7L));
        AutismRotationUtil.Rotation camera = new AutismRotationUtil.Rotation(0.0F, 0.0F);
        AutismHumanRotation.seed(stream, new AutismRotationUtil.Rotation(180.0F, 40.0F));
        AutismRotationUtil.Rotation previous = AutismHumanRotation.current(stream);
        int ticks = 0;
        while (ticks < 64
            && AutismRotationUtil.rotationAngleTo(previous, camera)
                > AutismKillAuraRotation.RESET_THRESHOLD) {
            AutismRotationUtil.Rotation next = AutismHumanRotation.step(stream, camera,
                AutismKillAuraRotation.WIND_DOWN_MAX_YAW_STEP,
                AutismKillAuraRotation.WIND_DOWN_MAX_PITCH_STEP, coarsest, false);
            float yawDelta = Math.abs(Mth.wrapDegrees(next.yaw() - previous.yaw()));
            float pitchDelta = Math.abs(next.pitch() - previous.pitch());
            assertTrue(yawDelta < 40.0F, "tick " + ticks + " emitted a " + yawDelta + " yaw delta");
            assertTrue(pitchDelta < 40.0F,
                "tick " + ticks + " emitted a " + pitchDelta + " pitch delta");
            previous = next;
            ticks++;
        }
        assertTrue(ticks < 64, "the hand-back must converge");
        assertTrue(ticks >= 4, "and it must not hand 180 degrees back in a 2-3 tick snap");
    }

}
