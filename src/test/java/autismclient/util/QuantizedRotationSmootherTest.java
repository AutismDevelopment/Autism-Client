package autismclient.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuantizedRotationSmootherTest {
    private static final double STEP = 0.15;

    @Test
    void outputsOnlyWholeSensitivityCounts() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(42L);

        for (int i = 0; i < 30; i++) {
            QuantizedRotationSmoother.Step result = smoother.step(
                37.13f, -18.71f, STEP, 0.4f, 0.25f, 0.5f, 0.35f, true, true
            );
            AutismRotationUtil.Rotation delta = result.asDelta(STEP);
            assertEquals(result.yawCounts(), Math.round(delta.yaw() / STEP));
            assertEquals(result.pitchCounts(), Math.round(delta.pitch() / STEP));
        }
    }

    @Test
    void acceleratesWithoutOvershootingQuantizedError() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(7L);
        List<Long> yaw = new ArrayList<>();
        long remaining = 200L;

        for (int i = 0; i < 20 && remaining > 0; i++) {
            QuantizedRotationSmoother.Step result = smoother.step(
                (float) (remaining * STEP), 0.0f, STEP, 0.5f, 0.5f, 0.0f, 0.35f, true, false
            );
            assertTrue(result.yawCounts() >= 0L);
            assertTrue(result.yawCounts() <= remaining);
            yaw.add(result.yawCounts());
            remaining -= result.yawCounts();
        }

        assertTrue(yaw.size() >= 3);
        assertTrue(yaw.get(1) >= yaw.get(0));
        assertTrue(yaw.stream().distinct().count() > 2L);
    }

    @Test
    void changesRepeatedNonZeroStepsWhenThereIsRoom() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(99L);
        QuantizedRotationSmoother.Step previous = null;
        boolean changed = false;

        for (int i = 0; i < 16; i++) {
            QuantizedRotationSmoother.Step current = smoother.step(
                120.0f, 60.0f, STEP, 0.3f, 0.3f, 0.8f, 0.5f, true, true
            );
            if (previous != null) {
                changed |= current.yawCounts() != previous.yawCounts()
                    || current.pitchCounts() != previous.pitchCounts();
            }
            previous = current;
        }

        assertTrue(changed);
        assertNotEquals(new QuantizedRotationSmoother.Step(0L, 0L), previous);
    }

    @Test
    void disabledAxisAlwaysProducesZeroCounts() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(123L);
        for (int i = 0; i < 8; i++) {
            assertEquals(0L, smoother.step(
                45.0f, 45.0f, STEP, 0.5f, 0.5f, 1.0f, 0.35f, false, true
            ).yawCounts());
        }
    }

    @Test
    void haltDropsRetainedVelocity() {
        QuantizedRotationSmoother smoother = new QuantizedRotationSmoother();
        smoother.reset(456L);
        smoother.step(90.0f, 30.0f, STEP, 1.0f, 1.0f, 0.5f, 0.35f, true, true);
        smoother.halt();

        assertEquals(new QuantizedRotationSmoother.Step(0L, 0L), smoother.step(
            0.0f, 0.0f, STEP, 1.0f, 1.0f, 0.5f, 0.35f, true, true
        ));
    }
}
