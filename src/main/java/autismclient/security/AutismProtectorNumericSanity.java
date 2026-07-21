package autismclient.security;

/**
 * Numeric sanity checks for incoming packet data — a single place that decides whether a raw {@code double} or
 * {@code float} a server sent is safe to feed into the game's own position/velocity/section math, or whether it
 * is malformed (NaN / infinite) or absurdly large enough to overflow that math and crash the client.
 *
 * <p>Rather than a one-off fix per exploit packet, any incoming packet handler can call these predicates on the
 * fields it is about to apply and drop a packet carrying a bad value. Observed overflow-crash reports carried
 * per-axis values around {@code 1.8e38 / 2.8e38 / 2.1e38} (near {@code Float.MAX_VALUE}); the ceiling here sits
 * many orders of magnitude below that and far above any legitimate value:
 *
 * <ul>
 *   <li>{@link #SANE_LIMIT} ({@code 1e9}) — the ceiling for a position or velocity component the client will
 *       apply. The world border caps coordinates near {@code 3.0e7} and real per-tick speeds stay well under
 *       ~100, so {@code 1e9} clears every legitimate value by a wide margin while still sitting far below the
 *       values that overflow the game's math.</li>
 * </ul>
 *
 * <p>Purely defensive: these never alter anything the client sends to the server and never fabricate
 * acknowledgements — they only let a handler avoid processing a value that would crash the local client. No
 * Minecraft types are referenced, so the whole decision surface is unit-testable without a client.
 *
 * <p>Adapted for AUTISM from BossCrashGuard (https://github.com/WaterBoss11/BossCrashGuard).
 */
public final class AutismProtectorNumericSanity {

    private AutismProtectorNumericSanity() {
    }

    /** Ceiling for a position/velocity component the client applies (blocks or blocks/tick). */
    public static final double SANE_LIMIT = 1.0e9;

    /** True when {@code v} is NaN or infinite — i.e. not a usable finite number. */
    public static boolean isNonFinite(double v) {
        return Double.isNaN(v) || Double.isInfinite(v);
    }

    /** Float overload of {@link #isNonFinite(double)}. */
    public static boolean isNonFinite(float v) {
        return Float.isNaN(v) || Float.isInfinite(v);
    }

    /** True when {@code v} is non-finite, or its magnitude exceeds {@code limit} — unsafe to feed to game math. */
    public static boolean isInsane(double v, double limit) {
        return isNonFinite(v) || Math.abs(v) > limit;
    }

    /** Float overload of {@link #isInsane(double, double)}. */
    public static boolean isInsane(float v, double limit) {
        return isNonFinite(v) || Math.abs((double) v) > limit;
    }

    /**
     * True when any of {@code x/y/z} is insane against {@link #SANE_LIMIT} — the check every position/velocity
     * packet path uses before letting the client apply the triple.
     */
    public static boolean isInsane(double x, double y, double z) {
        return isInsane(x, SANE_LIMIT) || isInsane(y, SANE_LIMIT) || isInsane(z, SANE_LIMIT);
    }
}
