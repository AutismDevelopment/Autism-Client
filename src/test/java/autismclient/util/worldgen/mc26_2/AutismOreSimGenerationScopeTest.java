package autismclient.util.worldgen.mc26_2;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismOreSimGenerationScopeTest {

    @Test
    void nestedScopesRestoreThePreviousMarkerState() {
        assertFalse(AutismOreSimGenerationScope.isActive());

        int value = AutismOreSimGenerationScope.call(() -> {
            assertTrue(AutismOreSimGenerationScope.isActive());
            return AutismOreSimGenerationScope.call(() -> {
                assertTrue(AutismOreSimGenerationScope.isActive());
                return 26;
            });
        });

        assertEquals(26, value);
        assertFalse(AutismOreSimGenerationScope.isActive());
    }

    @Test
    void exceptionRestoresTheMarkerAndDoesNotLeakToAnotherThread() {
        RuntimeException failure = new RuntimeException("expected");
        ExecutorService otherThread = Executors.newSingleThreadExecutor();
        RuntimeException thrown;
        try {
            thrown = assertThrows(RuntimeException.class, () ->
                AutismOreSimGenerationScope.call(() -> {
                    assertTrue(AutismOreSimGenerationScope.isActive());
                    assertFalse(CompletableFuture.supplyAsync(
                        AutismOreSimGenerationScope::isActive, otherThread).join());
                    throw failure;
                }));
        } finally {
            otherThread.shutdownNow();
        }

        assertSame(failure, thrown);
        assertFalse(AutismOreSimGenerationScope.isActive());
    }
}
