package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutismPluginScanJobStateTest {
    @Test
    void mutableServerContextDoesNotInvalidateSameGeneration() {
        AutismPluginScanJobState state = new AutismPluginScanJobState(7L, "play.example.net");
        assertTrue(state.matches(7L, "play.example.net"));
        assertTrue(state.matches(7L, "play.example.net"));
    }

    @Test
    void staleGenerationAndDifferentServerAreRejected() {
        AutismPluginScanJobState state = new AutismPluginScanJobState(8L, "one.example.net");
        assertFalse(state.matches(7L, "one.example.net"));
        assertFalse(state.matches(8L, "two.example.net"));
    }

    @Test
    void consecutiveScansCannotReuseSuggestionIds() {
        AutismPluginProbeIdAllocator allocator = new AutismPluginProbeIdAllocator();
        int first = allocator.allocateBlock();
        int second = allocator.allocateBlock();
        assertEquals(AutismPluginProbeIdAllocator.BLOCK_SIZE, second - first);
        assertTrue(first + AutismPluginProbeIdAllocator.BLOCK_SIZE <= second);
    }

    @Test
    void sendFailureRetriesExactlyThreeTimes() {
        AutismPluginScanJobState state = new AutismPluginScanJobState(1L, "server");
        assertTrue(state.canRetry(0));
        assertTrue(state.canRetry(1));
        assertTrue(state.canRetry(2));
        assertFalse(state.canRetry(3));
        assertFalse(state.canRetry(4));
    }

    @Test
    void missingRepliesAndMalformedProcessingProducePartialResults() {
        AutismPluginScanJobState state = new AutismPluginScanJobState(1L, "server");
        assertTrue(state.requiresPartialResult(0, 0, 4));
        assertTrue(state.requiresPartialResult(1, 0, 0));
        assertTrue(state.requiresPartialResult(0, 1, 0));
        assertFalse(state.requiresPartialResult(0, 0, 0));
    }

    @Test
    void finalizationIsIdempotentAndRejectsLateReplies() {
        AutismPluginScanJobState state = new AutismPluginScanJobState(3L, "server");
        assertTrue(state.beginFinalize());
        assertFalse(state.beginFinalize());
        assertFalse(state.matches(3L, "server"));
        assertFalse(state.canRetry(0));
    }

    @Test
    void disconnectOrPanicCancellationUsesTheSameTerminalGuard() {
        AutismPluginScanJobState disconnected = new AutismPluginScanJobState(4L, "server");
        AutismPluginScanJobState panic = new AutismPluginScanJobState(5L, "server");
        assertTrue(disconnected.beginFinalize());
        assertTrue(panic.beginFinalize());
        assertTrue(disconnected.isFinalized());
        assertTrue(panic.isFinalized());
    }
}
