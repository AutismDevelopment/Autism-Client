package autismclient.util.mm;

public final class MmRoleColors {
    public static final int USER_COLOR = 0xFF3498DB;
    public static final int BLUE_COLOR = 0xFF3498DB;

    private static final int[] GOD_GRADIENT      = {0xFFFF5C5C, 0xFFFF0000};
    private static final int[] ADMIN_GRADIENT    = {0xFF4CADD0, 0xFFB2F9FF};
    private static final int[] GOLD_GRADIENT     = {0xFFD4843D, 0xFFFFDE90};
    private static final int[] AQUA_GRADIENT     = {0xFF00E5FF, 0xFF3AFF00};
    private static final int[] NOTBROKE_GRADIENT = {0xFFC91AEC, 0xFF1540BE};

    private MmRoleColors() {
    }

    private static int[] gradient(String role) {
        return switch (role == null ? "" : role) {
            case "god" -> GOD_GRADIENT;
            case "admin" -> ADMIN_GRADIENT;
            case "gold" -> GOLD_GRADIENT;
            case "aqua" -> AQUA_GRADIENT;
            case "notbroke" -> NOTBROKE_GRADIENT;
            default -> null;
        };
    }

    public static boolean isGradient(String role) {
        return gradient(role) != null;
    }

    public static int roleColor(String role) {
        int[] g = gradient(role);
        if (g != null) return g[0];
        return "blue".equals(role) ? BLUE_COLOR : USER_COLOR;
    }

    public static int gradientNameColor(String role, int index, int length) {
        int[] g = gradient(role);
        if (g == null) return roleColor(role);
        if (length <= 1) return g[0];
        float t = Math.max(0.0F, Math.min(1.0F, index / (float) (length - 1)));
        return lerpRgb(g[0], g[1], t);
    }

    static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
