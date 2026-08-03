package autismclient.security;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismNumericSanityTest {
    @Test
    void legitimateValuesAreInRange() {
        assertFalse(AutismNumericSanity.outOfRange(0.0));
        assertFalse(AutismNumericSanity.outOfRange(3.0e7));
        assertFalse(AutismNumericSanity.outOfRange(-3.0e7));
        assertFalse(AutismNumericSanity.outOfRange(new Vec3(100.5, 64.0, -2000.0)));
        assertFalse(AutismNumericSanity.motionOutOfRange(100.0));
        assertFalse(AutismNumericSanity.motionOutOfRange(-99.9));
    }

    @Test
    void boundaryValuesAreInRange() {
        assertFalse(AutismNumericSanity.outOfRange(AutismNumericSanity.SANE_LIMIT));
        assertFalse(AutismNumericSanity.outOfRange(-AutismNumericSanity.SANE_LIMIT));
        assertFalse(AutismNumericSanity.motionOutOfRange(AutismNumericSanity.MAX_MOTION_PER_AXIS));
        assertFalse(AutismNumericSanity.motionOutOfRange(-AutismNumericSanity.MAX_MOTION_PER_AXIS));
    }

    @Test
    void absurdValuesAreOutOfRange() {
        assertTrue(AutismNumericSanity.outOfRange(Double.NaN));
        assertTrue(AutismNumericSanity.outOfRange(Double.POSITIVE_INFINITY));
        assertTrue(AutismNumericSanity.outOfRange(Double.NEGATIVE_INFINITY));
        assertTrue(AutismNumericSanity.outOfRange(1.8e38));
        assertTrue(AutismNumericSanity.outOfRange(-2.8e38));
        assertTrue(AutismNumericSanity.outOfRange(AutismNumericSanity.SANE_LIMIT * 1.0001));
        assertTrue(AutismNumericSanity.motionOutOfRange(1.0e6));
        assertTrue(AutismNumericSanity.motionOutOfRange(Double.NaN));
        assertTrue(AutismNumericSanity.motionOutOfRange(Double.POSITIVE_INFINITY));
    }

    @Test
    void vectorsAndChangesAreCheckedComponentWise() {
        assertTrue(AutismNumericSanity.outOfRange(null));
        assertTrue(AutismNumericSanity.outOfRange(new Vec3(0.0, 1.0e38, 0.0)));
        assertTrue(AutismNumericSanity.motionOutOfRange(null));
        assertTrue(AutismNumericSanity.motionOutOfRange(new Vec3(0.0, 0.0, 1.0e38)));

        PositionMoveRotation fine = new PositionMoveRotation(new Vec3(64.0, 70.0, -12.0), new Vec3(0.0, 0.4, 0.0), 0.0f, 0.0f);
        assertFalse(AutismNumericSanity.positionMoveOutOfRange(fine));

        PositionMoveRotation badPosition = new PositionMoveRotation(new Vec3(1.0e38, 0.0, 0.0), Vec3.ZERO, 0.0f, 0.0f);
        assertTrue(AutismNumericSanity.positionMoveOutOfRange(badPosition));

        PositionMoveRotation badDelta = new PositionMoveRotation(Vec3.ZERO, new Vec3(0.0, 1.0e38, 0.0), 0.0f, 0.0f);
        assertTrue(AutismNumericSanity.positionMoveOutOfRange(badDelta));

        assertTrue(AutismNumericSanity.positionMoveOutOfRange(null));
    }
}
