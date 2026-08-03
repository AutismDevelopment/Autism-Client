package autismclient.security;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutismProtectorPackStrip {

    private static final Set<UUID> WRAPPED = ConcurrentHashMap.newKeySet();

    private AutismProtectorPackStrip() {
    }

    public static void onPackPush(UUID id) {
        if (id == null) return;
        if (!AutismProtector.shouldStripServerPacks()) return;
        WRAPPED.add(id);
    }

    public static void onPackFinalResponse(UUID id, Object action) {
        if (id == null || action == null) return;
        String name = action.toString();
        if ("DECLINED".equals(name)
            || "FAILED_DOWNLOAD".equals(name)
            || "INVALID_URL".equals(name)
            || "FAILED_RELOAD".equals(name)
            || "DISCARDED".equals(name)) {
            WRAPPED.remove(id);
        }
    }

    public static void onPop(UUID id) {
        if (id == null) {
            WRAPPED.clear();
            return;
        }
        WRAPPED.remove(id);
    }

    public static boolean isWrapped(UUID id) {
        return id != null && WRAPPED.contains(id);
    }

    public static void clearAll() {
        WRAPPED.clear();
    }
}
