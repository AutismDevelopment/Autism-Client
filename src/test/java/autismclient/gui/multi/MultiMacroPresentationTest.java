package autismclient.gui.multi;

import autismclient.util.multi.MultiSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiMacroPresentationTest {
    @Test
    void assignmentLabelsAreStableAndExplicit() {
        assertEquals("Assigned: none", MultiMacroPresentation.assignedLabel(""));
        assertEquals("Assigned: Build", MultiMacroPresentation.assignedLabel("Build"));
        assertEquals("Playing: Runtime", MultiMacroPresentation.playingLabel("Runtime"));
    }

    @Test
    void liveInterpreterIsGreenEligible() {
        MultiSession.Snapshot snapshot = snapshot(true,
            new MultiSession.MacroProgress("Runtime", true, 2, 5, 1, "Move"));
        assertTrue(MultiMacroPresentation.playing(snapshot));
        assertEquals("Runtime", MultiMacroPresentation.playingName(snapshot));
    }

    @Test
    void disconnectedResumeIntentCannotRenderAsPlaying() {
        MultiSession.Snapshot snapshot = snapshot(false,
            new MultiSession.MacroProgress("Runtime", true, 2, 5, 1, "Disconnected; waiting for retry"));
        assertFalse(MultiMacroPresentation.playing(snapshot));
        assertEquals("", MultiMacroPresentation.playingName(snapshot));
    }

    @Test
    void queuedStartCountsDownAndOutranksAStaleDetail() {
        MultiSession.MacroQueue queue = new MultiSession.MacroQueue(1_000L, 6_000L);
        MultiSession.Snapshot snapshot = snapshot(true,
            new MultiSession.MacroProgress("Runtime", false, 4, 4, 1, "done"), queue);
        assertTrue(MultiMacroPresentation.queued(snapshot, 3_500L));
        assertEquals("Starts in 2.5s", MultiMacroPresentation.queuedLabel(snapshot, 3_500L));
        assertEquals(0.5D, queue.elapsedRatio(3_500L), 1.0E-9D);

        assertFalse(MultiMacroPresentation.queued(snapshot, 6_000L));
        assertEquals("", MultiMacroPresentation.queuedLabel(snapshot, 6_000L));
    }

    @Test
    void unstaggeredRunIsNeverQueued() {
        MultiSession.Snapshot snapshot = snapshot(true,
            new MultiSession.MacroProgress("Runtime", true, 1, 4, 1, "Move"), MultiSession.MacroQueue.NONE);
        assertFalse(MultiMacroPresentation.queued(snapshot, System.currentTimeMillis()));
    }

    private static MultiSession.Snapshot snapshot(boolean connected, MultiSession.MacroProgress progress) {
        return snapshot(connected, progress, MultiSession.MacroQueue.NONE);
    }

    private static MultiSession.Snapshot snapshot(boolean connected, MultiSession.MacroProgress progress,
                                                  MultiSession.MacroQueue queue) {
        return new MultiSession.Snapshot(
            "id", "Bot", "Proxy Off", "Native", MultiSession.Status.READY, "Ready", 20,
            connected, connected, 1L, "", false, "", 1, "minecraft:overworld", true,
            0.0D, 64.0D, 0.0D, 20.0F, 20.0F, 20, 1L, progress.running() ? "running" : "", progress, queue
        );
    }
}
