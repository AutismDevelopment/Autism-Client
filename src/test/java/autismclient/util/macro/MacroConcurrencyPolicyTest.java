package autismclient.util.macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MacroConcurrencyPolicyTest {
    @Test
    void allowsExactlyFourConcurrentRuns() {
        assertTrue(MacroExecutor.hasMacroCapacity(0));
        assertTrue(MacroExecutor.hasMacroCapacity(3));
        assertFalse(MacroExecutor.hasMacroCapacity(4));
        assertFalse(MacroExecutor.hasMacroCapacity(5));
    }
}
