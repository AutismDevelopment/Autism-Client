package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PayActionTest {
    @Test
    void confirmMacroRoundTripsThroughNbt() {
        PayAction action = new PayAction();
        action.confirmMacro = "pay-confirm";

        PayAction restored = new PayAction();
        restored.fromTag(action.toTag());

        assertEquals("pay-confirm", restored.confirmMacro);
    }

    @Test
    void confirmMacroDefaultsToNothingAndOldSavesStayEmpty() {
        assertEquals("", new PayAction().confirmMacro);

        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("type", "PAY");
        legacy.putString("amountInput", "500");
        PayAction restored = new PayAction();
        restored.fromTag(legacy);

        assertEquals("", restored.confirmMacro);
        assertEquals(500L, restored.resolvedAmount());
    }

    @Test
    void aBlankOrUnknownConfirmMacroIsNotAnError() {

        assertTrue(PayAction.runConfirmMacro("", () -> false));
        assertTrue(PayAction.runConfirmMacro(null, () -> false));
        assertTrue(PayAction.runConfirmMacro("   ", () -> false));
    }

    @Test
    void splitStillSumsBackToTheTotal() {
        long[] amounts = PayAction.distribute(1000L, 3);
        assertEquals(3, amounts.length);
        assertEquals(1000L, amounts[0] + amounts[1] + amounts[2]);
    }
}
