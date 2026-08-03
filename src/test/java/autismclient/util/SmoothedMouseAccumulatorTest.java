package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmoothedMouseAccumulatorTest {
    @Test
    void pendingFlagTracksActiveDistribution() {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        assertFalse(accumulator.hasPending());
        accumulator.replace(8.0D, 0.0D, 0.05D);
        assertTrue(accumulator.hasPending());
        accumulator.consume(0.05D);
        assertFalse(accumulator.hasPending());
    }
    @Test
    void distributesCorrectionAcrossFrames() {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        accumulator.replace(120.0, -60.0, 0.05);

        RawMouseAccumulator.Counts first = accumulator.consume(1.0 / 60.0);
        RawMouseAccumulator.Counts second = accumulator.consume(1.0 / 60.0);
        RawMouseAccumulator.Counts third = accumulator.consume(1.0 / 60.0);

        assertEquals(new RawMouseAccumulator.Counts(120, -60), new RawMouseAccumulator.Counts(
            first.x() + second.x() + third.x(),
            first.y() + second.y() + third.y()
        ));
        assertNotEquals(first, second);
    }

    @Test
    void totalDoesNotDependOnFrameRate() {
        assertEquals(totalAtFramesPerSecond(60), totalAtFramesPerSecond(240));
        assertEquals(new RawMouseAccumulator.Counts(120, -60), totalAtFramesPerSecond(60));
    }

    @Test
    void replacementDropsStaleMotion() {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        accumulator.replace(100.0, 0.0, 0.05);
        assertEquals(new RawMouseAccumulator.Counts(20, 0), accumulator.consume(0.01));

        accumulator.replace(-10.0, 0.0, 0.05);
        RawMouseAccumulator.Counts total = sum(accumulator, 5, 0.01);
        assertEquals(new RawMouseAccumulator.Counts(-10, 0), total);
    }

    @Test
    void clearDropsPendingCorrection() {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        accumulator.replace(100.0, -50.0, 0.05);
        accumulator.consume(0.01);
        accumulator.clear();
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume(0.05));
    }

    @Test
    void zeroReplacementStopsPendingCorrection() {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        accumulator.replace(100.0, -50.0, 0.05);
        accumulator.consume(0.01);
        accumulator.replace(0.0, 0.0, 0.05);
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume(0.05));
    }

    private static RawMouseAccumulator.Counts totalAtFramesPerSecond(int fps) {
        SmoothedMouseAccumulator accumulator = new SmoothedMouseAccumulator();
        accumulator.replace(120.0, -60.0, 0.05);
        int frames = Math.round(fps * 0.05f);
        return sum(accumulator, frames, 1.0 / fps);
    }

    private static RawMouseAccumulator.Counts sum(SmoothedMouseAccumulator accumulator, int frames, double elapsed) {
        long x = 0;
        long y = 0;
        for (int i = 0; i < frames; i++) {
            RawMouseAccumulator.Counts counts = accumulator.consume(elapsed);
            x += counts.x();
            y += counts.y();
        }
        return new RawMouseAccumulator.Counts(x, y);
    }
}
