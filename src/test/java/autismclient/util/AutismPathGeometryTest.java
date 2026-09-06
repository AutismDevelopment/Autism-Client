package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismPathGeometryTest {
    @Test
    void shoulderCannotClipAnObstacleWhenCenterAvoidsItsCell() {

        assertFalse(AutismPathGeometry.crossesBox(0.5, 0.5, 1.5, 2.0, 1, 0, 2, 1));
        assertTrue(AutismPathGeometry.crossesBox(0.5, 0.5, 1.5, 2.0, 0.7, -0.3, 2.3, 1.3));
    }

    @Test
    void corridorClearanceIncludesTheWholeBodyInBothDirections() {
        assertFalse(AutismPathGeometry.crossesBox(0.5, -3, 0.5, 3, -1.3, -2.3, 0.3, 2.3));
        assertTrue(AutismPathGeometry.crossesBox(0.2, -3, 0.2, 3, -1.3, -2.3, 0.3, 2.3));
        assertTrue(AutismPathGeometry.crossesBox(0.2, 3, 0.2, -3, -1.3, -2.3, 0.3, 2.3));
    }

    @Test
    void stationaryBodyAndBoundaryGrazingAreHandledWithoutDivisionByZero() {
        assertTrue(AutismPathGeometry.crossesBox(0, 0, 0, 0, -1, -1, 1, 1));
        assertFalse(AutismPathGeometry.crossesBox(1, -2, 1, 2, -1, -1, 1, 1));
        assertFalse(AutismPathGeometry.crossesBox(2, 2, 2, 2, -1, -1, 1, 1));
    }

    @Test
    void smoothingCannotTurnAPartialFloorClimbIntoAJumpOrTramplingDrop() {
        assertTrue(AutismPathGeometry.safeRise(0.9375, 1.5, 0.6));
        assertFalse(AutismPathGeometry.safeRise(0.5, 1.5, 0.6));
        assertTrue(AutismPathGeometry.safeRise(1.0, 0.9375, 0.6));
        assertFalse(AutismPathGeometry.safeRise(1.5, 0.9375, 0.6));
        assertFalse(AutismPathGeometry.safeRise(1.0, 0.5, 0.6));
    }

    @Test
    void heuristicIsConsistentForFlatEdgesDropsAndCheapTwoCellGaps() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                int h = AutismPathGeometry.lowerBound(x, z, 0, 0);
                for (int sign : new int[] {-1, 1}) {

                    assertTrue(h <= 10 + AutismPathGeometry.lowerBound(x + sign, z, 0, 0));
                    assertTrue(h <= 16 + AutismPathGeometry.lowerBound(x, z + sign, 0, 0));
                    assertTrue(h <= 18 + AutismPathGeometry.lowerBound(x + sign * 2, z, 0, 0));
                    assertTrue(h <= 18 + AutismPathGeometry.lowerBound(x, z + sign * 2, 0, 0));
                }
            }
        }
        assertEquals(0, AutismPathGeometry.lowerBound(4, -7, 4, -7));
    }
}
