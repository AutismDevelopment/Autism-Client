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

    private static MultiSession.Snapshot snapshot(boolean connected, MultiSession.MacroProgress progress) {
        return new MultiSession.Snapshot(
            "id", "Bot", "Proxy Off", "Native", MultiSession.Status.READY, "Ready", 20,
            connected, connected, 1L, "", false, "", 1, "minecraft:overworld", true,
            0.0D, 64.0D, 0.0D, 20.0F, 20.0F, 20, 1L, progress.running() ? "running" : "", progress
        );
    }
}
