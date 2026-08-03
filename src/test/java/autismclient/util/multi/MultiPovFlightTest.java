package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiPovFlightTest {
    @Test
    void abilitiesFlightUsesVanillaVerticalImpulse() {
        assertEquals(0.30D, MultiPovModuleController.abilitiesVerticalImpulse(0.10D, true, false), 1.0E-9D);
        assertEquals(-0.30D, MultiPovModuleController.abilitiesVerticalImpulse(0.10D, false, true), 1.0E-9D);
        assertEquals(0.0D, MultiPovModuleController.abilitiesVerticalImpulse(0.10D, true, true), 1.0E-9D);
        assertEquals(0.0D, MultiPovModuleController.abilitiesVerticalImpulse(0.10D, false, false), 1.0E-9D);
    }
}
