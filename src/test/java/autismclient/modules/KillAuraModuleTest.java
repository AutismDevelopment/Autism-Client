package autismclient.modules;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraModuleTest {
    @Test
    void boxedDistanceUsesHitboxInsteadOfEntityOrigin() {
        AABB box = new AABB(4.0, 1.0, -0.5, 5.0, 3.0, 0.5);

        assertEquals(9.0, box.distanceToSqr(new Vec3(1.0, 2.0, 0.0)), 1.0E-9);
        assertEquals(0.0, box.distanceToSqr(new Vec3(4.5, 2.0, 0.0)), 1.0E-9);
    }

    @Test
    void stabilizedCyclesStayWithinLiquidBounceDefaults() {
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
    void perspectiveSamplesLandOnTargetSurface() {
        Vec3 eyes = new Vec3(0.25, 1.62, -4.0);
        AABB box = new AABB(-0.4, 0.0, -0.4, 0.4, 1.8, 0.4);
        List<Vec3> points = new ArrayList<>();

        assertTrue(KillAuraModule.projectBoxPoints(eyes, box, 128, points::add));
        assertFalse(points.isEmpty());
        for (Vec3 point : points) {
            assertTrue(onSurface(box, point), () -> "projected point not on box: " + point);
        }

        Vec3 closest = KillAuraModule.closestProjectedPoint(eyes, box, 128);
        assertNotNull(closest);
        assertTrue(onSurface(box, closest));
    }

    @Test
    void projectionFallsBackWhenEyesAreInside() {
        AABB box = new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);
        assertFalse(KillAuraModule.projectBoxPoints(Vec3.ZERO, box, 128, ignored -> {}));
    }

    private static boolean onSurface(AABB box, Vec3 point) {
        double epsilon = 1.0E-5;
        boolean inside = point.x >= box.minX - epsilon && point.x <= box.maxX + epsilon
            && point.y >= box.minY - epsilon && point.y <= box.maxY + epsilon
            && point.z >= box.minZ - epsilon && point.z <= box.maxZ + epsilon;
        boolean boundary = Math.abs(point.x - box.minX) <= epsilon || Math.abs(point.x - box.maxX) <= epsilon
            || Math.abs(point.y - box.minY) <= epsilon || Math.abs(point.y - box.maxY) <= epsilon
            || Math.abs(point.z - box.minZ) <= epsilon || Math.abs(point.z - box.maxZ) <= epsilon;
        return inside && boundary;
    }
}
