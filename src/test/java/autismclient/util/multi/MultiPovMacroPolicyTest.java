package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiPovMacroPolicyTest {
    @Test
    void activePovAlwaysWinsOverSelection() {
        Set<String> selected = new LinkedHashSet<>(Set.of("bot-b", "bot-c"));
        assertEquals(Set.of("bot-a"), MultiManager.resolveInteractiveMacroScope("bot-a", true, selected));
    }

    @Test
    void stalePovFailsClosedInsteadOfBroadcasting() {
        assertNull(MultiManager.resolveInteractiveMacroScope("gone", false, Set.of()));
    }

    @Test
    void selectionAndAllSemanticsRemainWhenPovIsInactive() {
        Set<String> selected = Set.of("bot-b");
        assertEquals(selected, MultiManager.resolveInteractiveMacroScope(null, true, selected));
        assertEquals(Set.of(), MultiManager.resolveInteractiveMacroScope(null, true, Set.of()));
    }

    @Test
    void macroRequestRunAndMovementTailEachOwnPilotAuthority() {
        assertTrue(MultiSession.macroOwnsPilot(true, false, false));
        assertTrue(MultiSession.macroOwnsPilot(false, true, false));
        assertTrue(MultiSession.macroOwnsPilot(false, false, true));
        assertFalse(MultiSession.macroOwnsPilot(false, false, false));
    }
}
