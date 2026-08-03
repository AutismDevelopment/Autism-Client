package autismclient.util.macro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FpsLimitControllerTest {
    @AfterEach
    void clearOverrides() {
        FpsLimitController.clearAll();
    }

    @Test
    void keepsOtherRunOverrideWhenOneStops() {
        FpsLimitController.applyUntilCleared(11L, 30);
        FpsLimitController.applyUntilCleared(22L, 60);

        assertEquals(30, FpsLimitController.activeLimit());
        FpsLimitController.clear(11L);
        assertEquals(60, FpsLimitController.activeLimit());
        assertTrue(FpsLimitController.isActive());

        FpsLimitController.clear(22L);
        assertFalse(FpsLimitController.isActive());
    }

    @Test
    void ownerReplacesOnlyItsOwnOverride() {
        FpsLimitController.applyUntilCleared(11L, 30);
        FpsLimitController.applyUntilCleared(22L, 45);
        FpsLimitController.applyUntilCleared(11L, 90);

        assertEquals(45, FpsLimitController.activeLimit());
    }
}
