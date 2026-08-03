package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RawMouseAccumulatorTest {
    @Test
    void carriesFractionsIntoWholeCounts() {
        RawMouseAccumulator accumulator = new RawMouseAccumulator();
        accumulator.queue(0.4, -0.4);
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume());

        accumulator.queue(0.7, -0.7);
        assertEquals(new RawMouseAccumulator.Counts(1, -1), accumulator.consume());
    }

    @Test
    void neverRoundsPastRequestedMotion() {
        RawMouseAccumulator accumulator = new RawMouseAccumulator();
        accumulator.queue(2.9, -2.9);
        assertEquals(new RawMouseAccumulator.Counts(2, -2), accumulator.consume());
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume());
    }

    @Test
    void clearingDropsOldTargetResidual() {
        RawMouseAccumulator accumulator = new RawMouseAccumulator();
        accumulator.queue(0.9, 0.0);
        accumulator.consume();
        accumulator.clear();
        accumulator.queue(-0.2, 0.0);
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume());
    }

    @Test
    void replacingQueuedMotionPreservesFractionalHardwareCounts() {
        RawMouseAccumulator accumulator = new RawMouseAccumulator();
        accumulator.queue(0.6, -0.6);
        assertEquals(new RawMouseAccumulator.Counts(0, 0), accumulator.consume());

        accumulator.replaceQueued(0.6, -0.6);
        assertEquals(new RawMouseAccumulator.Counts(1, -1), accumulator.consume());
    }

    @Test
    void replacingQueuedMotionSupersedesAnOlderFrame() {
        RawMouseAccumulator accumulator = new RawMouseAccumulator();
        accumulator.queue(3.0, -3.0);
        accumulator.replaceQueued(1.0, -1.0);
        assertEquals(new RawMouseAccumulator.Counts(1, -1), accumulator.consume());
    }
}
