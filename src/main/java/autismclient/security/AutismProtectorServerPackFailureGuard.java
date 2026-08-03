package autismclient.security;

public final class AutismProtectorServerPackFailureGuard {
    private static volatile long suppressServerPacksUntilMs;

    private AutismProtectorServerPackFailureGuard() {
    }

    public static void suppressServerPacksTemporarily() {
        suppressServerPacksUntilMs = Math.max(suppressServerPacksUntilMs, System.currentTimeMillis() + 15_000L);
    }

    public static boolean shouldSuppressServerPacks() {
        return System.currentTimeMillis() < suppressServerPacksUntilMs;
    }

    public static void clear() {
        suppressServerPacksUntilMs = 0L;
    }
}
