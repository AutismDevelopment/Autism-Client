package autismclient.security;

import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AutismResourcePackTruthGuard {
    private static final ConcurrentMap<UUID, GuardedPack> GUARDED_PACKS = new ConcurrentHashMap<>();

    private AutismResourcePackTruthGuard() {
    }

    public enum ResponseKind {
        PASS,
        BYPASS_SUCCESS,
        DECLINE,
        FAILED_DOWNLOAD,
        INVALID_URL
    }

    public static final class Verdict {
        private static final Verdict PASS = new Verdict(ResponseKind.PASS, "");

        private final ResponseKind kind;
        private final String reason;

        private Verdict(ResponseKind kind, String reason) {
            this.kind = kind;
            this.reason = reason == null ? "" : reason;
        }

        public static Verdict pass() {
            return PASS;
        }

        public static Verdict of(ResponseKind kind, String reason) {
            return kind == ResponseKind.PASS ? PASS : new Verdict(kind, reason);
        }

        public ResponseKind kind() {
            return kind;
        }

        public String reason() {
            return reason;
        }

        public boolean shouldCancelVanilla() {
            return kind != ResponseKind.PASS;
        }
    }

    public static Verdict classify(ClientboundResourcePackPushPacket packet, boolean forceDeny, boolean bypass) {
        if (packet == null || packet.id() == null) return Verdict.pass();

        UrlVerdict url = inspectUrl(packet.url());
        Verdict verdict;
        if (forceDeny) {
            verdict = Verdict.of(ResponseKind.DECLINE, "resource pack auto-deny");
        } else if (bypass) {
            if (url.invalid) {
                verdict = Verdict.of(ResponseKind.INVALID_URL, url.reason);
            } else if (url.impossible || url.blockedLocal) {
                verdict = Verdict.of(ResponseKind.FAILED_DOWNLOAD, url.reason);
            } else {
                verdict = Verdict.of(ResponseKind.BYPASS_SUCCESS, "resource pack bypass compatibility response");
            }
        } else if (url.invalid) {
            verdict = Verdict.of(ResponseKind.INVALID_URL, url.reason);
        } else if (url.impossible || url.blockedLocal) {
            verdict = Verdict.of(ResponseKind.FAILED_DOWNLOAD, url.reason);
        } else {
            GUARDED_PACKS.remove(packet.id());
            return Verdict.pass();
        }

        if (verdict.kind() == ResponseKind.PASS) {
            GUARDED_PACKS.remove(packet.id());
        } else {
            GUARDED_PACKS.put(packet.id(), new GuardedPack(verdict.kind(), verdict.reason()));
        }
        return verdict;
    }

    public static boolean shouldCancelOutboundStatus(ServerboundResourcePackPacket packet) {
        if (packet == null || packet.id() == null || packet.action() == null) return false;
        GuardedPack guarded = GUARDED_PACKS.get(packet.id());
        if (guarded == null) return false;
        if (guarded.kind() == ResponseKind.BYPASS_SUCCESS) {
            if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) {
                GUARDED_PACKS.remove(packet.id(), guarded);
            }
            return false;
        }
        return isImpossibleSuccess(packet.action());
    }

    public static void onPop(UUID id) {
        if (id == null) {
            GUARDED_PACKS.clear();
            return;
        }
        GUARDED_PACKS.remove(id);
    }

    public static void clearAll() {
        GUARDED_PACKS.clear();
    }

    private static boolean isImpossibleSuccess(ServerboundResourcePackPacket.Action action) {
        return action == ServerboundResourcePackPacket.Action.DOWNLOADED
            || action == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED;
    }

    private static UrlVerdict inspectUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return UrlVerdict.invalid("empty resource pack URL");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            return UrlVerdict.invalid("malformed resource pack URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) return UrlVerdict.invalid("resource pack URL has no scheme");
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
            return UrlVerdict.invalid("unsupported resource pack URL scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return UrlVerdict.invalid("resource pack URL has no host");
        }

        int port = uri.getPort();
        if (port == 0) {
            return UrlVerdict.impossible("resource pack URL uses impossible port 0");
        }

        if (AutismProtector.shouldBlockLocalUrls() && AutismProtectorLocalAddressUtil.shouldBlock(host)) {
            return UrlVerdict.blockedLocal("resource pack URL points to a protected local/private address");
        }

        return UrlVerdict.ok();
    }

    private record GuardedPack(ResponseKind kind, String reason) {
    }

    private static final class UrlVerdict {
        private static final UrlVerdict OK = new UrlVerdict(false, false, false, "");

        private final boolean invalid;
        private final boolean impossible;
        private final boolean blockedLocal;
        private final String reason;

        private UrlVerdict(boolean invalid, boolean impossible, boolean blockedLocal, String reason) {
            this.invalid = invalid;
            this.impossible = impossible;
            this.blockedLocal = blockedLocal;
            this.reason = reason == null ? "" : reason;
        }

        private static UrlVerdict ok() {
            return OK;
        }

        private static UrlVerdict invalid(String reason) {
            return new UrlVerdict(true, false, false, reason);
        }

        private static UrlVerdict impossible(String reason) {
            return new UrlVerdict(false, true, false, reason);
        }

        private static UrlVerdict blockedLocal(String reason) {
            return new UrlVerdict(false, false, true, reason);
        }
    }
}
