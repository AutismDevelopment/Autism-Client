package autismclient.util.multi;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiPilotTruthTest {
    @Test
    void observerEchoOfRecentSendAcknowledgesWithoutRebasingPrediction() {
        MultiPilotTruth truth = new MultiPilotTruth();
        truth.reset(new Vec3(0.0, 64.0, 0.0), 0L);
        truth.recordSent(new Vec3(0.25, 64.0, 0.0), 100L);
        truth.recordSent(new Vec3(0.50, 64.0, 0.0), 150L);

        assertEquals(MultiPilotTruth.Result.ACKNOWLEDGED,
            truth.observe(new Vec3(0.25, 64.0, 0.0), 200L));
        assertEquals(MultiPilotTruth.Result.ACKNOWLEDGED,
            truth.observe(new Vec3(0.50, 64.0, 0.0), 250L));
    }

    @Test
    void unexpectedServerPositionRequiresImmediateRebase() {
        MultiPilotTruth truth = new MultiPilotTruth();
        truth.reset(new Vec3(0.0, 64.0, 0.0), 0L);
        truth.recordSent(new Vec3(1.0, 64.0, 0.0), 100L);

        assertEquals(MultiPilotTruth.Result.REBASE,
            truth.observe(new Vec3(8.0, 70.0, -3.0), 800L));
    }

    @Test
    void unchangedObserverTargetDoesNotRepeatedlyCorrect() {
        MultiPilotTruth truth = new MultiPilotTruth();
        Vec3 start = new Vec3(0.0, 64.0, 0.0);
        truth.reset(start, 0L);
        assertEquals(MultiPilotTruth.Result.UNCHANGED, truth.observe(start, 100L));
        assertEquals(MultiPilotTruth.Result.UNCHANGED, truth.observe(start, 200L));
    }

    @Test
    void transitionGraceIgnoresOldEchoButEventuallyRebasesIfItRemainsWrong() {
        MultiPilotTruth truth = new MultiPilotTruth();
        truth.reset(new Vec3(0.0, 64.0, 0.0), 0L);
        Vec3 oldEcho = new Vec3(-1.0, 64.0, 0.0);
        assertEquals(MultiPilotTruth.Result.UNCHANGED, truth.observe(oldEcho, 100L));
        assertEquals(MultiPilotTruth.Result.REBASE, truth.observe(oldEcho, 800L));
    }

    @Test
    void expiredPredictionCannotHideLaterDivergence() {
        MultiPilotTruth truth = new MultiPilotTruth();
        truth.reset(new Vec3(0.0, 64.0, 0.0), 0L);
        truth.recordSent(new Vec3(2.0, 64.0, 0.0), 0L);
        assertEquals(MultiPilotTruth.Result.REBASE,
            truth.observe(new Vec3(2.0, 64.0, 0.0), 5_000L));
    }
}
