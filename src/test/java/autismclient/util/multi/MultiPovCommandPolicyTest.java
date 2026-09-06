package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiPovCommandPolicyTest {
    @Test
    void playerBoundCommandsRouteToTheBotSession() {
        assertTrue(MultiPovCommandRouter.routesToSession("drop"));
        assertTrue(MultiPovCommandRouter.routesToSession("say"));
        assertTrue(MultiPovCommandRouter.routesToSession("send"));
        assertTrue(MultiPovCommandRouter.routesToSession("change-slot"));
        assertTrue(MultiPovCommandRouter.routesToSession("click-slot"));
        assertTrue(MultiPovCommandRouter.routesToSession("hclip"));
        assertTrue(MultiPovCommandRouter.routesToSession("vclip"));
        assertTrue(MultiPovCommandRouter.routesToSession("tp"));
        assertFalse(MultiPovCommandRouter.routesToSession("toggle"));
    }

    @Test
    void unsafeMainOnlyCommandsFailClosedDuringPov() {
        assertTrue(MultiPovCommandRouter.blocksMainFallback("gamemode"));
        assertTrue(MultiPovCommandRouter.blocksMainFallback("server"));
        assertTrue(MultiPovCommandRouter.blocksMainFallback("dismount"));
        assertFalse(MultiPovCommandRouter.blocksMainFallback("help"));
    }

    @Test
    void fullInventoryDropAcceptsCompactAndSpacedForms() {
        assertTrue(MultiSession.isFullInventoryDropRequest("fullinventory", ""));
        assertTrue(MultiSession.isFullInventoryDropRequest("full", "inventory"));
        assertTrue(MultiSession.isFullInventoryDropRequest("inventory", ""));
        assertFalse(MultiSession.isFullInventoryDropRequest("full", "enderchest"));
    }
}
