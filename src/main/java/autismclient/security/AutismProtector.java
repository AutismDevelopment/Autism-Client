package autismclient.security;

import autismclient.modules.AutismModule;
import autismclient.util.AutismConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public final class AutismProtector {
    private static final String OPSEC_MOD_ID = "opsec";
    private static final String EXPLOIT_PREVENTER_MOD_ID = "exploitpreventer";
    private static final boolean OPSEC_PRESENT;
    private static final boolean EXPLOIT_PREVENTER_PRESENT;
    private static final long STATE_REFRESH_NANOS = 250_000_000L;
    private static volatile RuntimeState runtimeState;
    private static volatile long runtimeStateExpiresAt;

    static {
        boolean opsec = false;
        boolean exploitPreventer = false;
        try {
            FabricLoader loader = FabricLoader.getInstance();
            opsec = loader.isModLoaded(OPSEC_MOD_ID);
            exploitPreventer = loader.isModLoaded(EXPLOIT_PREVENTER_MOD_ID);
        } catch (Throwable ignored) {  }
        OPSEC_PRESENT = opsec;
        EXPLOIT_PREVENTER_PRESENT = exploitPreventer;
    }

    private AutismProtector() {
    }

    public static boolean isExternalProtectorPresent() {
        return isFullExternalProtectorPresent();
    }

    public static boolean isFullExternalProtectorPresent() {
        return OPSEC_PRESENT;
    }

    public static boolean isOverlapExternalProtectorPresent() {
        return OPSEC_PRESENT || EXPLOIT_PREVENTER_PRESENT;
    }

    public static boolean isExploitPreventerPresent() {
        return EXPLOIT_PREVENTER_PRESENT && !OPSEC_PRESENT;
    }

    public static boolean isActive() {
        return state().active;
    }

    public static boolean shouldSpoofBrand() {
        return state().spoofBrand;
    }

    public static String getEffectiveBrand() {
        return isVanillaMode() ? "vanilla" : "fabric";
    }

    public static boolean isVanillaMode() {
        if (!isActive()) return false;
        AutismModule module = AutismModule.get();
        return module != null && module.isSpoofClientVanilla();
    }

    public static boolean isFabricMode() {
        return isActive() && !isVanillaMode();
    }

    public static boolean shouldFilterChannels() {
        return state().filterChannels;
    }

    public static boolean shouldProtectTranslationKeys() {
        return state().protectTranslationKeys;
    }

    public static boolean shouldTagPacketComponents() {
        return state().protectTranslationKeys;
    }

    public static boolean shouldDisableTelemetry() {
        return state().disableTelemetry;
    }

    public static boolean shouldBlockLocalUrls() {
        return state().blockLocalUrls;
    }

    public static boolean shouldIsolatePackCache() {
        return state().isolatePackCache;
    }

    public static boolean shouldStripServerPacks() {
        return state().stripServerPacks;
    }

    public static boolean shouldSkipChatSigning() {
        return state().skipChatSigning;
    }

    public static boolean shouldGuardCrashPackets() {
        return state().guardCrashPackets;
    }

    public static void refreshRuntimeState() {
        runtimeState = buildRuntimeState();
        runtimeStateExpiresAt = System.nanoTime() + STATE_REFRESH_NANOS;
    }

    private static RuntimeState state() {
        long now = System.nanoTime();
        RuntimeState state = runtimeState;
        if (state != null && now < runtimeStateExpiresAt) return state;
        synchronized (AutismProtector.class) {
            state = runtimeState;
            if (state != null && now < runtimeStateExpiresAt) return state;
            state = buildRuntimeState();
            runtimeState = state;
            runtimeStateExpiresAt = now + STATE_REFRESH_NANOS;
            return state;
        }
    }

    private static RuntimeState buildRuntimeState() {
        if (OPSEC_PRESENT) return RuntimeState.inactive();
        AutismConfig config = AutismConfig.getGlobal();
        boolean active = config != null && config.protectorEnabled;
        if (!active) return RuntimeState.inactive();
        boolean overlap = isOverlapExternalProtectorPresent();
        return new RuntimeState(
            true,
            config.protectorSpoofBrand,
            config.protectorFilterChannels,
            !overlap && config.protectorTranslationProtection,
            config.protectorDisableTelemetry,
            !overlap && config.protectorBlockLocalUrls,
            !overlap && config.protectorIsolatePackCache,
            config.protectorStripServerPacks,
            config.protectorChatSigningOff,
            // Not overlap-gated: a crash guard is cheap and worth keeping even alongside another protector, and
            // AutismMixinPlugin likewise keeps non-overlap security mixins applied under exploitpreventer.
            config.protectorCrashGuard
        );
    }

    private record RuntimeState(
        boolean active,
        boolean spoofBrand,
        boolean filterChannels,
        boolean protectTranslationKeys,
        boolean disableTelemetry,
        boolean blockLocalUrls,
        boolean isolatePackCache,
        boolean stripServerPacks,
        boolean skipChatSigning,
        boolean guardCrashPackets
    ) {
        static RuntimeState inactive() {
            return new RuntimeState(false, false, false, false, false, false, false, false, false, false);
        }
    }

    private static final int MAX_BYPASS_ENTRIES = 1024;

    private static final Map<Object, Boolean> USER_BYPASS_PACKETS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    public static void markUserBypass(Packet<?> packet) {
        if (packet == null) return;
        synchronized (USER_BYPASS_PACKETS) {
            if (USER_BYPASS_PACKETS.size() >= MAX_BYPASS_ENTRIES) {
                Iterator<Map.Entry<Object, Boolean>> it = USER_BYPASS_PACKETS.entrySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            USER_BYPASS_PACKETS.put(packet, Boolean.TRUE);
        }
    }

    public static boolean isUserBypass(Packet<?> packet) {
        if (packet == null) return false;
        return USER_BYPASS_PACKETS.containsKey(packet);
    }

    public static void consumeUserBypass(Packet<?> packet) {
        if (packet == null) return;
        USER_BYPASS_PACKETS.remove(packet);
    }
}
