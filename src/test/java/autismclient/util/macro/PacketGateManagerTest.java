package autismclient.util.macro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PacketGateManagerTest {
    @AfterEach
    void clearGates() {
        PacketGateManager.clearAll();
    }

    @Test
    void sameGateIdIsIsolatedPerRun() {
        PacketGateAction first = new PacketGateAction();
        first.gateId = "auto";
        PacketGateAction second = new PacketGateAction();
        second.gateId = "auto";

        PacketGateManager.installOwned(first, 11L);
        PacketGateManager.installOwned(second, 22L);

        assertEquals(1, PacketGateManager.activeGateCountForOwner(11L));
        assertEquals(1, PacketGateManager.activeGateCountForOwner(22L));

        PacketGateManager.disable("all", 11L);
        assertEquals(0, PacketGateManager.activeGateCountForOwner(11L));
        assertEquals(1, PacketGateManager.activeGateCountForOwner(22L));
    }
}
