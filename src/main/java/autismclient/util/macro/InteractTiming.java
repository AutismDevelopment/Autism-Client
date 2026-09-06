package autismclient.util.macro;

public enum InteractTiming {
    BEFORE,
    WITH,
    AFTER,
    AFTER_PLUS,
    CUSTOM;

    public static InteractTiming parse(String raw, InteractTiming fallback) {
        if (raw == null) return fallback;
        switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "BEFORE", "TICK_BEFORE": return BEFORE;
            case "WITH", "SAME_TICK":     return WITH;
            case "AFTER":                 return AFTER;
            case "AFTER_PLUS", "AFTER+", "TICK_AFTER": return AFTER_PLUS;
            case "CUSTOM":                return CUSTOM;
            default:                       return fallback;
        }
    }
}
