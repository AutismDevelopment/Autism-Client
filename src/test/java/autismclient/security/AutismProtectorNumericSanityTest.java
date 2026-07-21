package autismclient.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismProtectorNumericSanityTest {

    @Test
    void finiteInRangeValuesAreSane() {
        assertFalse(AutismProtectorNumericSanity.isNonFinite(0.0));
        assertFalse(AutismProtectorNumericSanity.isNonFinite(1.0f));
        assertFalse(AutismProtectorNumericSanity.isInsane(0.0, 0.0, 0.0));
        // A far-but-legitimate coordinate near the world border stays sane.
        assertFalse(AutismProtectorNumericSanity.isInsane(2.9e7, 320.0, -2.9e7));
        // Right at the ceiling is still allowed (strictly-greater is the reject test).
        assertFalse(AutismProtectorNumericSanity.isInsane(AutismProtectorNumericSanity.SANE_LIMIT,
            AutismProtectorNumericSanity.SANE_LIMIT));
    }

    @Test
    void nonFiniteValuesAreInsane() {
        assertTrue(AutismProtectorNumericSanity.isNonFinite(Double.NaN));
        assertTrue(AutismProtectorNumericSanity.isNonFinite(Double.POSITIVE_INFINITY));
        assertTrue(AutismProtectorNumericSanity.isNonFinite(Float.NaN));
        assertTrue(AutismProtectorNumericSanity.isNonFinite(Float.NEGATIVE_INFINITY));
        assertTrue(AutismProtectorNumericSanity.isInsane(Double.NaN, AutismProtectorNumericSanity.SANE_LIMIT));
        assertTrue(AutismProtectorNumericSanity.isInsane(Float.POSITIVE_INFINITY,
            AutismProtectorNumericSanity.SANE_LIMIT));
    }

    @Test
    void overflowMagnitudesAreInsane() {
        // The values from real crash reports.
        assertTrue(AutismProtectorNumericSanity.isInsane(1.8e38, 2.8e38, 2.1e38));
        assertTrue(AutismProtectorNumericSanity.isInsane(-2.8e38, AutismProtectorNumericSanity.SANE_LIMIT));
        // Just past the ceiling on any single axis trips the triple check.
        double justOver = Math.nextUp(AutismProtectorNumericSanity.SANE_LIMIT);
        assertTrue(AutismProtectorNumericSanity.isInsane(0.0, justOver, 0.0));
    }
}
