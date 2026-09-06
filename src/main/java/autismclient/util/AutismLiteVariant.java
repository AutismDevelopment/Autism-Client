package autismclient.util;

import java.io.InputStream;

public final class AutismLiteVariant {
    private static final boolean ENABLED = compute();

    private AutismLiteVariant() {
    }

    private static boolean compute() {
        try (InputStream in = AutismLiteVariant.class.getResourceAsStream("/autism-lite.marker")) {
            return in != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean enabled() {
        return ENABLED;
    }
}
