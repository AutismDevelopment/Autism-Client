package autismclient.util.oresim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismOreSimSeedInputTest {

    @Test
    void nullEmptyAndWhitespaceAreEmpty() {
        for (String input : new String[]{null, "", " ", "\t\r\n", "\u2003"}) {
            AutismOreSimSeedInput.Result result = AutismOreSimSeedInput.parse(input);
            assertEquals(AutismOreSimSeedInput.Status.EMPTY, result.status());
            assertNull(result.value());
        }
    }

    @Test
    void acceptsTheEntireSignedLongRangeWithoutChangingTheValue() {
        assertValid("0", 0L);
        assertValid("-0", 0L);
        assertValid("+0", 0L);
        assertValid("42", 42L);
        assertValid("  -81234918234129384\n", -81234918234129384L);
        assertValid(Long.toString(Long.MIN_VALUE), Long.MIN_VALUE);
        assertValid(Long.toString(Long.MAX_VALUE), Long.MAX_VALUE);
    }

    @Test
    void malformedAndOverflowingValuesAreInvalid() {
        for (String input : new String[]{
            "seed", "+", "-", "12 34", "1.0", "9,223,372,036,854,775,807",
            "9223372036854775808", "-9223372036854775809"
        }) {
            AutismOreSimSeedInput.Result result = AutismOreSimSeedInput.parse(input);
            assertEquals(AutismOreSimSeedInput.Status.INVALID, result.status(), input);
            assertNull(result.value(), input);
        }
    }

    private static void assertValid(String input, long expected) {
        AutismOreSimSeedInput.Result result = AutismOreSimSeedInput.parse(input);
        assertEquals(AutismOreSimSeedInput.Status.VALID, result.status());
        assertTrue(result.isValid());
        assertEquals(expected, result.value());
    }
}
