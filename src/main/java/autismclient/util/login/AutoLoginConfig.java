package autismclient.util.login;

public record AutoLoginConfig(
    long windowMs,
    long chatDelayMs,
    long chatResendMs,
    int maxChatAttempts,
    long screenRetryMs,
    long registerFollowUpMs
) {
    public AutoLoginConfig {
        windowMs = clamp(windowMs, 1_000L, 300_000L);
        chatDelayMs = clamp(chatDelayMs, 0L, 60_000L);
        chatResendMs = clamp(chatResendMs, 250L, 30_000L);
        maxChatAttempts = (int) clamp(maxChatAttempts, 1L, 20L);
        screenRetryMs = clamp(screenRetryMs, 100L, 10_000L);
        registerFollowUpMs = clamp(registerFollowUpMs, 250L, 30_000L);
    }

    public static AutoLoginConfig multiDefaults() {
        return new AutoLoginConfig(40_000L, 2_000L, 2_500L, 4, 1_000L, 3_000L);
    }

    public static AutoLoginConfig playerDefaults() {
        return new AutoLoginConfig(10_000L, 2_000L, 2_500L, 4, 1_000L, 3_000L);
    }

    private static long clamp(long value, long low, long high) {
        return Math.max(low, Math.min(high, value));
    }
}
