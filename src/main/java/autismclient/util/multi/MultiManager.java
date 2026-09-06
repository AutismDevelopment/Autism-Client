package autismclient.util.multi;

import autismclient.commands.AutismCommands;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyType;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MultiManager implements MultiSession.Sink {
    public record StartResult(boolean ok, String message) {
        static StartResult success() {
            return new StartResult(true, "");
        }

        static StartResult error(String message) {
            return new StartResult(false, message);
        }
    }

    public record BroadcastResult(int sent, int skipped, int failed, List<String> details) {
        public String summary() {
            return "Sent " + sent + ", skipped " + skipped + ", failed " + failed;
        }
    }

    public record RetryResult(boolean ok, String message) {
        static RetryResult ok(String message) {
            return new RetryResult(true, message);
        }

        static RetryResult error(String message) {
            return new RetryResult(false, message);
        }
    }

    public interface UiLifecycleListener {
        void batchEnded();

        void sessionDropped(String accountId);

        default void menuClosed(String accountId) {}
    }

    private static volatile UiLifecycleListener uiLifecycleListener;

    public static void setUiLifecycleListener(UiLifecycleListener listener) {
        uiLifecycleListener = listener;
    }

    private static void fireBatchEnded() {
        UiLifecycleListener listener = uiLifecycleListener;
        if (listener == null) return;
        try {
            listener.batchEnded();
        } catch (Throwable ignored) {

        }
    }

    private static void fireSessionDropped(String accountId) {
        UiLifecycleListener listener = uiLifecycleListener;
        if (listener == null) return;
        try {
            listener.sessionDropped(accountId);
        } catch (Throwable ignored) {

        }
    }

    private static void fireMenuClosed(String accountId) {
        UiLifecycleListener listener = uiLifecycleListener;
        if (listener == null) return;
        try {
            listener.menuClosed(accountId);
        } catch (Throwable ignored) {

        }
    }

    private record Pending(MultiSession session, InetSocketAddress address, String host, int port) {
    }

    private record ChatMsg(long seq, long time, Component component, String text, String source) {
    }

    private static final class UnifiedMsg {
        final long seq;
        final long time;
        long lastAt;
        int count = 1;
        final String text;
        final String source;
        final boolean system;
        final Component representative;
        final Map<String, Component> perAccount = new LinkedHashMap<>();

        UnifiedMsg(long seq, long time, String text, String source, boolean system, Component representative) {
            this.seq = seq;
            this.time = time;
            this.lastAt = time;
            this.text = text;
            this.source = source == null ? "" : source;
            this.system = system;
            this.representative = representative;
        }
    }

    public record ChatLine(long seq, long time, Component render, Map<String, Component> targets, boolean system,
                           int count, String source) {
    }

    private static String groupKey(String source, String text) {
        return (source == null ? "" : source) + '\0' + text;
    }

    private static String systemKey(String text) {
        return "\0sys\0" + text;
    }

    private static volatile MultiManager instance;
    private static final int CHAT_LIMIT = 100;

    private static final long CHAT_MERGE_MS = 2_000L;

    private static final long SYS_MERGE_MS = 20_000L;

    private static final int PROXY_SAMPLE_COUNT = 3;
    private static final int PROXY_CANDIDATE_CAP = 100;

    private static final long PROXY_SAMPLE_MAX_MS = 30_000L;

    private static final long SNAPSHOT_INTERVAL_MS = 100L;
    private static final long SNAPSHOT_DEMAND_NANOS = TimeUnit.SECONDS.toNanos(1L);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Autism-Multi-Scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService workers = Executors.newFixedThreadPool(
        Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
        runnable -> {
            Thread thread = new Thread(runnable, "Autism-Multi-Worker");
            thread.setDaemon(true);
            return thread;
        }
    );
    private final AtomicLong generations = new AtomicLong();
    private final Map<String, MultiSession> sessions = new LinkedHashMap<>();

    private volatile List<MultiSession> sessionList = List.of();
    private volatile List<MultiSession.Snapshot> snapshotList = List.of();

    private volatile long snapshotDemandUntilNanos;
    private volatile Map<String, MultiSession> sessionsById = Map.of();
    private volatile long sessionRevision;
    private volatile long uiRevision;
    private final Map<String, MultiProfile.SessionSpec> runtimeSpecs = new HashMap<>();
    private final Map<String, AutismProxy> runtimeProxies = new HashMap<>();

    private final java.util.Set<String> controllableIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int MAX_CONTROLLABLE = 1;
    private enum MacroIntentKind { ASSIGNED, DIRECT }

    private record MacroResumeIntent(MacroIntentKind kind, AutismMacro directMacro) {
        static MacroResumeIntent assigned() { return new MacroResumeIntent(MacroIntentKind.ASSIGNED, null); }
        static MacroResumeIntent direct(AutismMacro macro) { return new MacroResumeIntent(MacroIntentKind.DIRECT, macro); }
    }

    private final Map<String, MacroResumeIntent> macroIntents = new HashMap<>();

    private volatile Map<String, String> assignedMacroNames = Map.of();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Set<MultiSession> connecting = new HashSet<>();
    private final Map<java.util.UUID, String> resolvedIdentities = new HashMap<>();
    private final Map<String, String> lastFailedProxyIds = new HashMap<>();
    private final Map<String, MultiSession.Status> lastPostedStatus = new HashMap<>();
    private final Map<String, Long> retryTokens = new HashMap<>();
    private final Set<String> retryingAccounts = new HashSet<>();
    private final Map<String, ArrayDeque<ChatMsg>> accountChat = new HashMap<>();
    private final ArrayDeque<UnifiedMsg> unifiedChat = new ArrayDeque<>();
    private final Map<String, UnifiedMsg> recentByText = new HashMap<>();
    private long chatSeq;
    private volatile long chatRevision;

    private final ArrayDeque<String> commandHistory = new ArrayDeque<>();
    private String suggestSourceId = "";
    private int suggestId = -1;
    private String suggestText = "";

    private volatile long generation;
    private long retrySerial;
    private boolean passwordPromptShown;
    private MultiProfile activeProfile;

    private java.util.UUID renderedProfileIdAtStart;
    private long nextStartAt;
    private long lastSnapshotPublishAt;
    private boolean active;
    private MultiViaCompat.Target viaTarget = new MultiViaCompat.Target(false, null, "Native");
    private ScheduledFuture<?> tickTask;
    private String rememberedServerAddress = "";
    private MultiViaCompat.Target rememberedServerTarget;

    private MultiManager() {
    }

    public static MultiManager get() {
        MultiManager current = instance;
        if (current != null) return current;
        synchronized (MultiManager.class) {
            if (instance == null) instance = new MultiManager();
            return instance;
        }
    }

    public static MultiManager getIfInitialized() {
        return instance;
    }

    public synchronized StartResult start(MultiProfile source) {
        if (source == null) return StartResult.error("Profile is missing");
        if (active) return StartResult.error("Disconnect the active Multi batch first");
        MultiProfile profile = new MultiProfile(source);
        profile.normalize();
        java.util.UUID renderedProfileId = currentRenderedProfileId();

        boolean hadSessions = !profile.sessions.isEmpty();
        profile.sessions.removeIf(spec -> isCurrentRenderedAccount(spec.accountId()));
        if (hadSessions && profile.sessions.isEmpty()) {
            return StartResult.error("You're playing on this account. Pick another.");
        }
        StartResult validation = validate(profile, renderedProfileId);
        if (!validation.ok()) return validation;

        ServerAddress server = ServerAddress.parseString(profile.serverAddress);
        MultiViaCompat.Target selectedVia = MultiViaCompat.captureSelectedTarget();
        if (MultiViaCompat.isAutoDetect(selectedVia)
            && profile.serverAddress.equalsIgnoreCase(rememberedServerAddress)
            && rememberedServerTarget != null
            && rememberedServerTarget.version() != null) {
            selectedVia = rememberedServerTarget;
        }
        String viaError = MultiViaCompat.validateSelectedTarget(selectedVia);
        if (!viaError.isBlank()) return StartResult.error(viaError);

        generation = generations.incrementAndGet();
        activeProfile = profile;
        renderedProfileIdAtStart = renderedProfileId;
        viaTarget = selectedVia;
        active = true;
        autismclient.util.AutismRuntimeActivity.publish(autismclient.util.AutismRuntimeActivity.MULTI, true);
        ensureTicking();
        nextStartAt = 0L;
        lastSnapshotPublishAt = 0L;
        sessions.clear();
        republishSessions();
        runtimeSpecs.clear();
        runtimeProxies.clear();
        macroIntents.clear();
        rebuildAssignedMacroNames();
        pending.clear();
        connecting.clear();
        resolvedIdentities.clear();
        lastFailedProxyIds.clear();
        lastPostedStatus.clear();
        retryTokens.clear();
        retryingAccounts.clear();
        passwordPromptShown = false;
        clearChat();

        long startedGeneration = generation;
        boolean auto = profile.proxyMode == MultiProfile.ProxyMode.Auto;
        appendSystem(auto ? "Verifying proxies for " + profile.serverAddress : "Resolving " + profile.serverAddress);

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, AutismProxy> assignment = switch (profile.proxyMode) {
                    case Auto -> verifyAndAssign(startedGeneration, profile, server.getHost(), server.getPort());
                    case Manual -> assignManualProxies(profile, AutismProxyManager.get().all());
                    case Off -> Map.of();
                };
                if (!isCurrent(startedGeneration)) return;
                InetSocketAddress resolved = ServerNameResolver.DEFAULT.resolveAddress(server)
                    .map(ResolvedServerAddress::asInetSocketAddress)
                    .orElse(null);
                Minecraft.getInstance().execute(() ->
                    buildAndStartSessions(startedGeneration, profile, assignment, resolved, server));
            } catch (Throwable error) {
                String message = "Start failed: " + singleLine(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), 120);
                Minecraft.getInstance().execute(() -> buildFailedSessions(startedGeneration, profile, message));
            }
        }, workers);
        return StartResult.success();
    }

    private synchronized void buildAndStartSessions(long gen, MultiProfile profile, Map<String, AutismProxy> assignment,
                                                    InetSocketAddress resolved, ServerAddress server) {
        if (!active || generation != gen) return;
        boolean dnsOk = resolved != null;
        appendSystem(dnsOk ? "Starting " + profile.name + " on " + profile.serverAddress : "Unknown server address");
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            AutismProxy proxy = perModeProxy(profile, spec, assignment);
            boolean proxyRequired = profile.proxyMode != MultiProfile.ProxyMode.Off && !spec.direct();
            boolean missingProxy = proxyRequired && proxy == null;
            AutismProxy snapshot = copyProxy(proxy);
            String proxyName = snapshot != null ? snapshot.displayName() : proxyRequired ? "No proxy" : "Proxy Off";
            MultiSession session = new MultiSession(
                generation, spec, snapshot, proxyName, profile.packetPolicy, profile.loginMode,
                profile.name, profile.openFormValues(spec.accountId()), this, workers, viaTarget);
            session.setAutoAccept(profile.autoAccept);
            armCapture(session);
            sessions.put(spec.accountId(), session);
            runtimeSpecs.put(spec.accountId(), runtimeSpecFor(profile, spec, proxy));
            runtimeProxies.put(spec.accountId(), snapshot);
            if (!dnsOk) {
                session.failExternal("Unknown server address");
            } else if (missingProxy) {
                session.failExternal("No working proxy");
            } else {
                pending.addLast(new Pending(session, resolved, server.getHost(), server.getPort()));
            }
            armJoinMacro(profile, spec, session);
        }
        republishSessions();
        if (dnsOk) pump();

        if (profile.loginMode == MultiProfile.LoginMode.Custom) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (MultiProfile.SessionSpec spec : profile.sessions) {
                String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
                if (name != null && !name.isBlank()) names.add(name);
            }
            for (String name : names) {
                AutismMacro macro = AutismMacroManager.get().get(name);
                if (macro != null) warnMacroCompatibility(macro);
            }
        }
    }

    private boolean deferRepublish;

    private void republishSessions() {
        if (deferRepublish) return;
        sessionList = List.copyOf(sessions.values());
        sessionsById = Map.copyOf(sessions);
        publishSnapshots(true);
        sessionRevision++;
    }

    private synchronized void buildFailedSessions(long gen, MultiProfile profile, String reason) {
        if (!active || generation != gen || !sessions.isEmpty()) return;
        appendSystem(reason);
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            MultiSession session = new MultiSession(gen, spec, null, "Proxy Off", profile.packetPolicy,
                profile.loginMode, profile.name, profile.openFormValues(spec.accountId()),
                this, workers, viaTarget);
            sessions.put(spec.accountId(), session);
            runtimeSpecs.put(spec.accountId(), spec);
            runtimeProxies.put(spec.accountId(), null);
            armJoinMacro(profile, spec, session);
            session.failExternal(reason);
        }
        republishSessions();
    }

    private static AutismProxy perModeProxy(MultiProfile profile, MultiProfile.SessionSpec spec, Map<String, AutismProxy> assignment) {
        return switch (profile.proxyMode) {
            case Off -> null;
            case Auto -> assignment.get(spec.accountId());
            case Manual -> spec.direct() ? null : assignment.get(spec.accountId());
        };
    }

    private static MultiProfile.SessionSpec runtimeSpecFor(MultiProfile profile, MultiProfile.SessionSpec spec, AutismProxy proxy) {
        return switch (profile.proxyMode) {
            case Off -> new MultiProfile.SessionSpec(spec.accountId(), "");
            case Auto -> new MultiProfile.SessionSpec(spec.accountId(), proxy == null ? "" : proxy.stableId());
            case Manual -> spec;
        };
    }

    private StartResult validate(MultiProfile profile, java.util.UUID renderedProfileId) {
        if (profile.serverAddress.isBlank()) return StartResult.error("Server address is required");
        if (profile.sessions.isEmpty()) return StartResult.error("Select at least one account");
        if (profile.sessions.size() > MultiProfile.MAX_SESSIONS) {
            return StartResult.error("Maximum " + MultiProfile.MAX_SESSIONS + " sessions");
        }
        boolean manual = profile.proxyMode == MultiProfile.ProxyMode.Manual;
        Set<String> accountIds = new HashSet<>();
        Set<String> identities = new HashSet<>();
        int manualBestRows = 0;
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!accountIds.add(spec.accountId())) return StartResult.error("Duplicate account row");
            String identity;
            if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(spec.accountId())) {
                java.util.UUID defaultProfileId = AutismAccountSessionSwitcher.getOriginalUser().getProfileId();
                if (renderedProfileId != null && renderedProfileId.equals(defaultProfileId)) {
                    return StartResult.error("You're playing on this account.");
                }
                identity = defaultProfileId.toString();
            } else {
                AutismAccount account = AutismAccountManager.get().findById(spec.accountId());
                if (account == null) return StartResult.error("Missing account: " + spec.accountId());
                java.util.UUID knownProfileId = parseKnownProfileId(account.uuid);
                if (renderedProfileId != null && renderedProfileId.equals(knownProfileId)) {
                    return StartResult.error("You're playing on this account.");
                }
                identity = account.uuid == null || account.uuid.isBlank()
                    ? account.type.name() + ":" + account.displayName().toLowerCase(Locale.ROOT)
                    : account.uuid.toLowerCase(Locale.ROOT);
            }
            if (!identities.add(identity)) return StartResult.error("The same Minecraft identity is selected twice");

            if (manual) {
                if (spec.bestProxy()) {
                    manualBestRows++;
                } else if (!spec.direct()) {
                    AutismProxy proxy = AutismProxyManager.get().findById(spec.proxyId());
                    if (proxy == null || !proxy.isValid()) {
                        return StartResult.error("Missing proxy for " + accountLabel(spec.accountId()));
                    }
                }
            }
        }
        List<AutismProxy> usableProxies = distinctUsableProxies(AutismProxyManager.get().all());
        if (profile.proxyMode == MultiProfile.ProxyMode.Auto && usableProxies.isEmpty()) {
            return StartResult.error("Add proxies or set Proxy: Off");
        }
        if (manual && manualBestRows > 0 && usableProxies.isEmpty()) {
            return StartResult.error("No proxy is available for Manual Best Proxy rows");
        }
        return StartResult.success();
    }

    private static java.util.UUID currentRenderedProfileId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        net.minecraft.client.multiplayer.ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return null;

        com.mojang.authlib.GameProfile profile = connection.getLocalGameProfile();
        return profile == null ? null : profile.id();
    }

    public static String renderedServerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) return "";
        com.mojang.authlib.GameProfile profile = minecraft.getConnection().getLocalGameProfile();
        return profile == null ? "" : profile.name();
    }

    public boolean isRenderedOnActiveServer() {
        if (!active || activeProfile == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.getCurrentServer() == null) return false;
        return sameServer(activeProfile.serverAddress, minecraft.getCurrentServer().ip);
    }

    static boolean sameServer(String a, String b) {
        if (a == null || b == null) return false;
        try {
            ServerAddress sa = ServerAddress.parseString(a.trim());
            ServerAddress sb = ServerAddress.parseString(b.trim());
            return sa.getHost().equalsIgnoreCase(sb.getHost()) && sa.getPort() == sb.getPort();
        } catch (RuntimeException ignored) {
            return a.trim().equalsIgnoreCase(b.trim());
        }
    }

    static java.util.UUID parseKnownProfileId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.mojang.util.UndashedUuid.fromStringLenient(value.trim());
        } catch (RuntimeException ignored) {
            try {
                return java.util.UUID.fromString(value.trim());
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private static java.util.UUID accountProfileId(String accountId) {
        if (accountId == null) return null;
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            net.minecraft.client.User user = AutismAccountSessionSwitcher.getOriginalUser();
            return user == null ? null : user.getProfileId();
        }
        AutismAccount account = AutismAccountManager.get().findById(accountId);
        if (account == null) return null;
        java.util.UUID known = parseKnownProfileId(account.uuid);
        if (known != null) return known;
        String name = account.username != null && !account.username.isBlank() ? account.username : account.displayName();
        return name == null || name.isBlank() ? null : net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
    }

    public static boolean isCurrentRenderedAccount(String accountId) {
        if (accountId == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (connection == null) return false;
        com.mojang.authlib.GameProfile rendered = connection.getLocalGameProfile();
        if (rendered == null) return false;
        java.util.UUID target = accountProfileId(accountId);
        if (target != null && target.equals(rendered.id())) return true;
        String renderedName = rendered.name();
        String accountName = accountUsername(accountId);
        return renderedName != null && !renderedName.isBlank()
            && accountName != null && !accountName.isBlank()
            && renderedName.equalsIgnoreCase(accountName);
    }

    private static String accountUsername(String accountId) {
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            net.minecraft.client.User user = AutismAccountSessionSwitcher.getOriginalUser();
            return user == null ? null : user.getName();
        }
        AutismAccount account = AutismAccountManager.get().findById(accountId);
        if (account == null) return null;
        return account.username != null && !account.username.isBlank() ? account.username : account.displayName();
    }

    static Map<String, AutismProxy> assignManualProxies(MultiProfile profile, List<AutismProxy> available) {
        if (profile == null) return Map.of();
        List<AutismProxy> usable = distinctUsableProxies(available);
        List<AutismProxy> allValid = available == null ? List.of() : available.stream()
            .filter(proxy -> proxy != null && proxy.isValid()).toList();
        Map<String, AutismProxy> assignment = new LinkedHashMap<>();
        Set<String> explicitlyUsed = new HashSet<>();

        for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (spec.direct() || spec.bestProxy()) continue;
            AutismProxy selected = findProxyById(allValid, spec.proxyId());
            if (selected != null) {
                assignment.put(spec.accountId(), selected);
                explicitlyUsed.add(proxyLeaseKey(selected));
            }
        }
        List<AutismProxy> bestOrder = new ArrayList<>(usable.size());
        for (AutismProxy candidate : usable) {
            if (!explicitlyUsed.contains(proxyLeaseKey(candidate))) bestOrder.add(candidate);
        }
        for (AutismProxy candidate : usable) {
            if (explicitlyUsed.contains(proxyLeaseKey(candidate))) bestOrder.add(candidate);
        }
        int bestIndex = 0;
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!spec.bestProxy()) continue;
            AutismProxy selected = bestOrder.isEmpty() ? null : bestOrder.get(bestIndex++ % bestOrder.size());
            if (selected != null) assignment.put(spec.accountId(), selected);
        }
        return assignment;
    }

    private static AutismProxy findProxyById(List<AutismProxy> proxies, String id) {
        if (proxies == null || id == null || id.isBlank()) return null;
        for (AutismProxy proxy : proxies) if (id.equals(proxy.stableId())) return proxy;
        return null;
    }

    private synchronized void pump() {
        if (!active || activeProfile == null) return;
        int limit = activeProfile.concurrency();
        while (connecting.size() < limit && !pending.isEmpty()) {
            Pending next = pending.removeFirst();
            connecting.add(next.session());
            long now = System.currentTimeMillis();
            long at = Math.max(now, nextStartAt);
            nextStartAt = at + activeProfile.delayMs();
            scheduler.schedule(
                () -> {
                    synchronized (MultiManager.this) {
                        if (!active || next.session().generation() != generation
                            || sessions.get(next.session().accountId()) != next.session()) {
                            connecting.remove(next.session());
                            pump();
                            return;
                        }
                    }
                    next.session().start(next.address(), next.host(), next.port());
                },
                Math.max(0L, at - now),
                TimeUnit.MILLISECONDS
            );
        }
    }

    public synchronized RetryResult retry(String accountId) {
        if (!active || activeProfile == null || accountId == null) return RetryResult.error("No batch");
        MultiSession old = sessions.get(accountId);
        if (old != null && old.connected()) return RetryResult.error("Connected");
        if (old != null && !isRetryable(old.statusValue())) return RetryResult.error("Still connecting");
        if (!retryingAccounts.add(accountId)) return RetryResult.error("Already retrying");
        long retryToken = ++retrySerial;
        retryTokens.put(accountId, retryToken);
        MultiProfile.SessionSpec spec = runtimeSpecs.get(accountId);
        if (spec == null) {
            retryingAccounts.remove(accountId);
            return RetryResult.error("No session");
        }
        ServerAddress server = ServerAddress.parseString(activeProfile.serverAddress);

        if (activeProfile.proxyMode == MultiProfile.ProxyMode.Auto) {
            List<AutismProxy> candidates = orderedAutoRetryCandidates(accountId);
            if (candidates.isEmpty()) {
                retryingAccounts.remove(accountId);
                appendSystem(accountLabel(accountId) + ": No proxy");
                return RetryResult.error("No proxy");
            }
            long retryGeneration = generation;
            int target = activeProfile.autoMaxPingMs;
            CompletableFuture.runAsync(() -> {
                try {
                    retryAutoAsync(retryGeneration, retryToken, accountId, candidates, server, target);
                } catch (Throwable error) {
                    synchronized (MultiManager.this) {
                        if (!active || generation != retryGeneration
                            || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
                        retryingAccounts.remove(accountId);
                        appendSystem(accountLabel(accountId) + ": Retry failed");
                    }
                }
            }, workers);
            appendSystem(accountLabel(accountId) + ": Verifying proxy");
            return RetryResult.ok("Verifying proxy");
        }

        AutismProxy previous = runtimeProxies.get(accountId);
        String currentProxyId = previous == null ? spec.proxyId() : previous.stableId();
        boolean proxyEnabled = activeProfile.proxyMode == MultiProfile.ProxyMode.Manual && !spec.direct();
        if (!proxyEnabled) currentProxyId = "";
        String lastFailedProxyId = lastFailedProxyIds.getOrDefault(accountId, currentProxyId == null ? "" : currentProxyId);
        AutismProxy selectedProxy = null;
        if (proxyEnabled) {
            if (spec.bestProxy()) {
                selectedProxy = selectPreferredRetryProxy(accountId, currentProxyId, lastFailedProxyId);
            } else {
                AutismProxy configured = AutismProxyManager.get().findById(spec.proxyId());
                selectedProxy = configured != null ? configured : previous;
            }
        }
        if (selectedProxy == null && proxyEnabled) {
            retryingAccounts.remove(accountId);
            appendSystem(accountLabel(accountId) + ": No proxy");
            return RetryResult.error("No proxy");
        }

        MultiProfile.SessionSpec retrySpec = activeProfile.proxyMode == MultiProfile.ProxyMode.Manual
            ? spec : new MultiProfile.SessionSpec(accountId, "");
        AutismProxy proxy = copyProxy(selectedProxy);
        runtimeSpecs.put(accountId, retrySpec);
        runtimeProxies.put(accountId, proxy);
        MultiSession replacement = new MultiSession(
            generation,
            retrySpec,
            proxy,
            proxy == null ? "Proxy Off" : proxy.displayName(),
            activeProfile.packetPolicy,
            activeProfile.loginMode,
            activeProfile.name,
            activeProfile.openFormValues(accountId),
            this,
            workers,
            viaTarget
        );
        replacement.setAutoAccept(activeProfile.autoAccept);
        armCapture(replacement);
        pending.removeIf(next -> next.session() == old);
        connecting.remove(old);
        sessions.put(accountId, replacement);
        if (old != null) old.disconnect("Retrying");
        rearmMacroIfRunning(accountId, replacement);
        republishSessions();
        long retryGeneration = generation;
        CompletableFuture.supplyAsync(
            () -> ServerNameResolver.DEFAULT.resolveAddress(server)
                .map(ResolvedServerAddress::asInetSocketAddress)
                .orElse(null),
            workers
        ).whenComplete((resolved, error) -> {
            synchronized (MultiManager.this) {
                if (!active || generation != retryGeneration || sessions.get(accountId) != replacement
                    || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
                retryingAccounts.remove(accountId);
                if (error != null || resolved == null) {
                    replacement.failExternal("Unknown server address");
                    return;
                }
                pending.addLast(new Pending(replacement, resolved, server.getHost(), server.getPort()));
                pump();
            }
        });
        String label = proxy == null ? "Proxy Off" : proxy.displayName();
        String message = "Retry: " + singleLine(label, 32);
        appendSystem(accountLabel(accountId) + ": " + message);
        return RetryResult.ok(message);
    }

    private synchronized List<AutismProxy> orderedAutoRetryCandidates(String accountId) {
        String lastFailed = lastFailedProxyIds.getOrDefault(accountId, "");
        Set<String> inUseByOthers = new HashSet<>();
        for (Map.Entry<String, AutismProxy> entry : runtimeProxies.entrySet()) {
            if (!entry.getKey().equals(accountId) && entry.getValue() != null) {
                inUseByOthers.add(proxyLeaseKey(entry.getValue()));
            }
        }
        List<AutismProxy> all = distinctUsableProxies(AutismProxyManager.get().all());
        List<AutismProxy> primary = new ArrayList<>();
        List<AutismProxy> fallback = new ArrayList<>();
        for (AutismProxy proxy : all) {
            if (proxy.stableId().equals(lastFailed) || inUseByOthers.contains(proxyLeaseKey(proxy))) fallback.add(proxy);
            else primary.add(proxy);
        }
        primary.addAll(fallback);
        return primary;
    }

    private synchronized AutismProxy selectPreferredRetryProxy(String accountId, String currentProxyId,
                                                                String lastFailedProxyId) {
        Set<String> inUseByOthers = new HashSet<>();
        for (Map.Entry<String, AutismProxy> entry : runtimeProxies.entrySet()) {
            if (!entry.getKey().equals(accountId) && entry.getValue() != null) {
                inUseByOthers.add(proxyLeaseKey(entry.getValue()));
            }
        }
        List<AutismProxy> preferred = new ArrayList<>();
        List<AutismProxy> shared = new ArrayList<>();
        for (AutismProxy proxy : distinctUsableProxies(AutismProxyManager.get().all())) {
            if (inUseByOthers.contains(proxyLeaseKey(proxy))) shared.add(proxy);
            else preferred.add(proxy);
        }
        preferred.addAll(shared);
        return selectRetryProxy(preferred, currentProxyId, lastFailedProxyId, false);
    }

    private void retryAutoAsync(long gen, long retryToken, String accountId, List<AutismProxy> candidates,
                                ServerAddress server, int target) {
        int timeout = probeTimeoutMs(target);
        Sample best = null;
        int tries = 0;
        for (AutismProxy candidate : candidates) {
            if (!isCurrentRetry(gen, retryToken, accountId)) return;
            if (tries++ >= 6) break;
            Sample sample = sampleOne(gen, candidate, server.getHost(), server.getPort(), timeout);
            if (sample == null) return;
            if (!sample.ok()) continue;
            if (sample.pingMs() <= target) {
                best = sample;
                break;
            }
            if (best == null || sample.pingMs() < best.pingMs()) best = sample;
        }
        if (!isCurrentRetry(gen, retryToken, accountId)) return;
        InetSocketAddress resolved = ServerNameResolver.DEFAULT.resolveAddress(server)
            .map(ResolvedServerAddress::asInetSocketAddress)
            .orElse(null);
        AutismProxy chosenFinal = best == null ? null : best.proxy();
        Minecraft.getInstance().execute(() -> applyAutoRetry(gen, retryToken, accountId, chosenFinal, resolved, server));
    }

    private synchronized void applyAutoRetry(long gen, long retryToken, String accountId, AutismProxy chosen,
                                             InetSocketAddress resolved, ServerAddress server) {
        if (!active || generation != gen || activeProfile == null
            || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
        retryingAccounts.remove(accountId);
        MultiSession old = sessions.get(accountId);
        if (old != null && old.connected()) return;
        if (chosen == null) {
            appendSystem(accountLabel(accountId) + ": No working proxy");
            if (old != null) old.failExternal("No working proxy");
            return;
        }
        if (resolved == null) {
            appendSystem("Unknown server address");
            if (old != null) old.failExternal("Unknown server address");
            return;
        }
        pending.removeIf(next -> next.session() == old);
        connecting.remove(old);
        AutismProxy proxy = copyProxy(chosen);
        MultiProfile.SessionSpec retrySpec = new MultiProfile.SessionSpec(accountId, chosen.stableId());
        runtimeSpecs.put(accountId, retrySpec);
        runtimeProxies.put(accountId, proxy);
        MultiSession replacement = new MultiSession(
            generation, retrySpec, proxy, proxy.displayName(), activeProfile.packetPolicy,
            activeProfile.loginMode, activeProfile.name, activeProfile.openFormValues(accountId), this, workers, viaTarget);
        replacement.setAutoAccept(activeProfile.autoAccept);
        armCapture(replacement);
        sessions.put(accountId, replacement);
        if (old != null) old.disconnect("Retrying");
        rearmMacroIfRunning(accountId, replacement);
        republishSessions();
        pending.addLast(new Pending(replacement, resolved, server.getHost(), server.getPort()));
        pump();
        appendSystem(accountLabel(accountId) + ": Retry " + singleLine(proxy.displayName(), 32));
    }

    public synchronized RetryResult retryAllDisconnected() {
        if (!active || activeProfile == null) return RetryResult.error("No batch");
        int attempted = 0;
        deferRepublish = true;
        try {
            for (String accountId : new ArrayList<>(sessions.keySet())) {
                MultiSession session = sessions.get(accountId);
                if (session != null && isRetryable(session.statusValue()) && retry(accountId).ok()) attempted++;
            }
        } finally {
            deferRepublish = false;
        }
        republishSessions();
        return attempted == 0 ? RetryResult.error("Nothing to retry") : RetryResult.ok("Retrying " + attempted);
    }

    private Map<String, AutismProxy> verifyAndAssign(long gen, MultiProfile profile, String host, int port) {
        List<AutismProxy> candidates = new ArrayList<>();
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD) candidates.add(proxy);
        }
        reserveMainProxyEndpoint(candidates);
        candidates.sort(Comparator
            .comparingInt(MultiManager::retryRank)
            .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE));
        Map<String, AutismProxy> assignment = new LinkedHashMap<>();
        if (candidates.isEmpty()) {
            post(gen, "No proxies to verify");
            return assignment;
        }
        int needed = (int) profile.sessions.stream().filter(spec -> !spec.direct()).count();
        int target = profile.autoMaxPingMs;
        List<AutismProxy> poolList = new ArrayList<>(candidates.subList(0, Math.min(candidates.size(), PROXY_CANDIDATE_CAP)));
        int timeout = probeTimeoutMs(target);

        int wantUnderTarget = Math.min(poolList.size(), Math.max(needed + 3, 8));
        {

            ExecutorCompletionService<Sample> service = new ExecutorCompletionService<>(workers);
            List<Future<Sample>> probeTasks = new ArrayList<>(poolList.size());
            for (AutismProxy candidate : poolList) {
                probeTasks.add(service.submit(() -> sampleOne(gen, candidate, host, port, timeout)));
            }
            post(gen, "Testing " + poolList.size() + (poolList.size() == 1 ? " proxy" : " proxies") + " (target " + target + "ms)");
            long deadline = System.currentTimeMillis() + PROXY_SAMPLE_MAX_MS;
            List<Sample> working = new ArrayList<>();
            int underTarget = 0;
            for (int i = 0; i < poolList.size(); i++) {
                if (!isCurrent(gen)) break;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) break;
                Future<Sample> done;
                try {
                    done = service.poll(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (done == null) break;
                Sample sample;
                try {
                    sample = done.get();
                } catch (Exception ignored) {
                    continue;
                }
                if (sample == null || !sample.ok()) continue;
                working.add(sample);
                if (sample.pingMs() <= target && ++underTarget >= wantUnderTarget) break;
            }
            if (working.isEmpty()) {
                cancelProbeTasks(probeTasks);
                post(gen, "No working proxy found");
                return assignment;
            }
            working.sort(Comparator.comparingLong(Sample::pingMs));

            List<Sample> distinctWorking = new ArrayList<>(working.size());
            Set<String> workingLeases = new HashSet<>();
            for (Sample sample : working) {
                if (workingLeases.add(proxyLeaseKey(sample.proxy()))) distinctWorking.add(sample);
            }
            int distinct = distinctWorking.size();
            long best = distinctWorking.getFirst().pingMs();
            long underTargetCount = distinctWorking.stream().filter(sample -> sample.pingMs() <= target).count();
            post(gen, "Verified " + distinct + (distinct == 1 ? " proxy" : " proxies") + " (best " + best + "ms)");
            if (underTargetCount < distinct) {
                post(gen, "Only " + underTargetCount + " under " + target + "ms; slower proxies stay unique");
            }
            if (distinct < needed) {
                post(gen, "Only " + distinct + " distinct working " + (distinct == 1 ? "proxy" : "proxies")
                    + " for " + needed + " accounts; sharing as a last resort");
            }
            assignment.putAll(distributeProxies(profile.sessions,
                distinctWorking.stream().map(Sample::proxy).toList()));
            cancelProbeTasks(probeTasks);
            return assignment;
        }
    }

    private static void cancelProbeTasks(List<? extends Future<?>> tasks) {
        if (tasks == null) return;
        for (Future<?> task : tasks) if (task != null && !task.isDone()) task.cancel(true);
    }

    private Sample sampleOne(long gen, AutismProxy proxy, String host, int port, int timeout) {
        long[] pings = new long[PROXY_SAMPLE_COUNT];
        AutismProxyType workingType = proxy == null ? null : proxy.type;
        for (int i = 0; i < PROXY_SAMPLE_COUNT; i++) {
            if (!isCurrent(gen)) return null;
            MultiProxyVerifier.Result result = MultiProxyVerifier.verify(proxy, host, port, timeout);
            if (!result.ok()) return new Sample(proxy, false, 0L);
            if (result.workingType() != null) workingType = result.workingType();
            pings[i] = result.latencyMs();
        }
        Arrays.sort(pings);
        AutismProxy verified = copyProxy(proxy);
        if (workingType != null) verified.type = workingType;
        return new Sample(verified, true, pings[PROXY_SAMPLE_COUNT / 2]);
    }

    private static int probeTimeoutMs(int targetPingMs) {
        return Math.max(700, Math.min(1500, targetPingMs * 3));
    }

    private synchronized boolean isCurrent(long gen) {
        return active && generation == gen;
    }

    private synchronized boolean isCurrentRetry(long gen, long retryToken, String accountId) {
        return active && generation == gen && retryTokens.getOrDefault(accountId, -1L) == retryToken;
    }

    private void post(long gen, String message) {
        synchronized (this) {
            if (active && generation == gen) appendSystem(message);
        }
    }

    private record Sample(AutismProxy proxy, boolean ok, long pingMs) {
    }

    record MacroFinish(String macroName, String reason) {
    }

    public synchronized void disconnectSession(String accountId) {
        MultiSession session = sessions.get(accountId);
        if (session == null) return;

        retryTokens.put(accountId, ++retrySerial);
        retryingAccounts.remove(accountId);
        macroIntents.remove(accountId);
        pending.removeIf(next -> next.session() == session);
        connecting.remove(session);
        session.disconnect("Disconnected by user");
        pump();
    }

    public static boolean isRetryable(MultiSession.Status status) {
        return status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED;
    }

    public synchronized void disconnectAll(String reason) {
        if (!active && sessions.isEmpty()) return;
        generation = generations.incrementAndGet();
        active = false;
        autismclient.util.AutismRuntimeActivity.publish(autismclient.util.AutismRuntimeActivity.MULTI, false);
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        pending.clear();
        connecting.clear();
        resolvedIdentities.clear();
        lastFailedProxyIds.clear();
        lastPostedStatus.clear();
        for (MultiSession session : sessions.values()) session.disconnect(reason == null ? "Multi stopped" : reason);
        sessions.clear();
        republishSessions();
        runtimeSpecs.clear();
        runtimeProxies.clear();
        macroIntents.clear();
        assignedMacroNames = Map.of();
        retryTokens.clear();
        retryingAccounts.clear();
        controllableIds.clear();
        activeProfile = null;
        renderedProfileIdAtStart = null;
        clearChat();
        fireBatchEnded();
    }

    private void clearChat() {
        accountChat.clear();
        unifiedChat.clear();
        recentByText.clear();
        chatSeq = 0;
        chatRevision++;
        commandHistory.clear();
        suggestSourceId = "";
        suggestId = -1;
        suggestText = "";
    }

    private static final int HISTORY_LIMIT = 50;

    public synchronized void pushHistory(String line) {
        String value = line == null ? "" : line.trim();
        if (value.isEmpty()) return;
        commandHistory.remove(value);
        commandHistory.addLast(value);
        while (commandHistory.size() > HISTORY_LIMIT) commandHistory.removeFirst();
        AutismClientMessaging.rememberRecentChat(value);
    }

    public synchronized List<String> commandHistory() {
        return List.copyOf(commandHistory);
    }

    public synchronized String lastHistoryEntry() {
        return commandHistory.peekLast();
    }

    public synchronized void clearHistory() {
        commandHistory.clear();
    }

    public record SuggestionResult(int start, int length, List<String> entries) {
    }

    public synchronized void requestSuggestions(String command, Set<String> scope) {
        if (command == null) return;
        MultiSession source = representativeReady(scope);
        if (source == null) {
            suggestSourceId = "";
            suggestId = -1;
            suggestText = "";
            return;
        }
        int id = source.requestSuggestions(command);
        if (id < 0) {
            suggestSourceId = "";
            suggestId = -1;
            suggestText = "";
            return;
        }
        suggestSourceId = source.accountId();
        suggestId = id;
        suggestText = command;
    }

    public synchronized SuggestionResult suggestions(String command) {
        if (command == null || !command.equals(suggestText)) return null;
        MultiSession source = sessions.get(suggestSourceId);
        if (source == null) return null;
        MultiSession.Suggest suggest = source.suggestion(suggestId);
        if (suggest == null) return null;
        return new SuggestionResult(suggest.start(), suggest.length(), suggest.entries());
    }

    private MultiSession representativeReady(Set<String> scope) {
        if (scope != null && !scope.isEmpty()) {
            for (String id : scope) {
                MultiSession session = sessions.get(id);
                if (session != null && session.ready()) return session;
            }
        }
        for (MultiSession session : sessions.values()) {
            if (session.ready()) return session;
        }
        return null;
    }

    public void shutdown() {
        disconnectAll("Game closed");
        workers.shutdownNow();
        scheduler.shutdownNow();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void rememberSelectedServer(net.minecraft.client.multiplayer.ServerData serverData) {
        if (serverData == null) {
            rememberedServerAddress = "";
            rememberedServerTarget = null;
            return;
        }
        rememberedServerAddress = serverData.ip == null ? "" : serverData.ip.trim();
        rememberedServerTarget = MultiViaCompat.captureServerTarget(serverData);
    }

    public int connectedCount() {
        int count = 0;
        for (MultiSession session : sessionList) {
            if (session.connected()) count++;
        }
        return count;
    }

    public int readyCount() {
        int count = 0;
        for (MultiSession session : sessionList) {
            if (session.ready()) count++;
        }
        return count;
    }

    public int sessionCount() {
        return sessionList.size();
    }

    public String readyFraction() {
        return readyCount() + "/" + sessionCount();
    }

    public synchronized MultiProfile activeProfile() {
        return activeProfile == null ? null : new MultiProfile(activeProfile);
    }

    public synchronized void updatePolicy(MultiPacketPolicy policy) {
        if (!active || activeProfile == null || policy == null) return;
        activeProfile.packetPolicy = new MultiPacketPolicy(policy);
        for (MultiSession session : sessions.values()) session.applyPolicy(policy);
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void updateAutoAccept(MultiAutoAccept config) {
        if (!active || activeProfile == null || config == null) return;
        activeProfile.autoAccept = new MultiAutoAccept(config);
        for (MultiSession session : sessions.values()) session.setAutoAccept(activeProfile.autoAccept);
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized String tpaToMe(String accountId) {
        return startTeleport(accountId, autoAcceptConfig().tpaToMeCommand, "tpa");
    }

    public synchronized String tpaToBot(String accountId) {
        return startTeleport(accountId, autoAcceptConfig().tpaToBotCommand, "tpa");
    }

    public synchronized String tradeWith(String accountId) {
        return startTeleport(accountId, autoAcceptConfig().tradeCommand, "trade");
    }

    private MultiAutoAccept autoAcceptConfig() {
        return activeProfile == null ? new MultiAutoAccept() : activeProfile.autoAccept;
    }

    private String startTeleport(String accountId, String template, String kind) {
        if (!isRenderedOnActiveServer()) return "Join the bots' server first";
        MultiSession session = sessions.get(accountId);
        if (session == null) return "No session";
        if (session.statusValue() != MultiSession.Status.READY) return "Bot is not in the world yet";
        String me = renderedServerName();
        if (me.isBlank()) return "Can't read your name";
        String command = MultiAutoAccept.expand(template, session.username(), me);
        if (command.isBlank()) return "No command set";
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return "Not connected";
        if (command.startsWith("/")) mc.getConnection().sendCommand(command.substring(1));
        else mc.getConnection().sendChat(command);
        session.armAutoAccept(kind);
        return "Sent";
    }

    public synchronized void updateQuickAction(int index, MultiQuickAction action) {
        if (!active || activeProfile == null) return;
        activeProfile.setQuickAction(index, action);
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void clearQuickAction(int index) {
        updateQuickAction(index, new MultiQuickAction());
    }

    public synchronized void resetQuickActions() {
        if (!active || activeProfile == null) return;
        activeProfile.resetQuickActions();
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void assignAllMacro(String macroName) {
        if (activeProfile == null) return;
        String name = macroName == null ? "" : macroName.trim();
        if (name.equals(activeProfile.allMacroName)) return;
        activeProfile.allMacroName = name;
        MultiProfileManager.get().put(activeProfile);
        rebuildAssignedMacroNames();
        uiRevision++;
    }

    public synchronized void assignMacro(String accountId, String macroName) {
        if (accountId == null) return;
        assignMacroOnScope(java.util.Set.of(accountId), macroName);
    }

    public synchronized int assignMacroOnScope(Set<String> accountIds, String macroName) {
        if (activeProfile == null || accountIds == null || accountIds.isEmpty()) return 0;
        String name = macroName == null ? "" : macroName.trim();
        List<MultiProfile.SessionSpec> specs = activeProfile.sessions;
        int changed = 0;
        for (int i = 0; i < specs.size(); i++) {
            MultiProfile.SessionSpec spec = specs.get(i);
            if (!accountIds.contains(spec.accountId()) || spec.macroName().equals(name)) continue;
            specs.set(i, spec.withMacro(name));
            changed++;
        }
        if (changed > 0) {
            MultiProfileManager.get().put(activeProfile);
            rebuildAssignedMacroNames();
            uiRevision++;
        }
        return changed;
    }

    public synchronized String allMacroName() {
        return activeProfile == null ? "" : activeProfile.allMacroName;
    }

    public synchronized boolean hasAnyAssignedMacro() {
        if (activeProfile == null) return false;
        if (!activeProfile.allMacroName.isBlank()) return true;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (!spec.macroName().isBlank()) return true;
        }
        return false;
    }

    public synchronized boolean hasAssignedMacroOnInteractiveScope(Set<String> requestedScope) {
        Set<String> scope = interactiveMacroScope(requestedScope);
        if (scope == null) return true;
        if (scope.isEmpty()) return hasAnyAssignedMacro();
        for (String accountId : scope) {
            if (!assignedMacroName(accountId).isBlank()) return true;
        }
        return false;
    }

    public synchronized String effectiveMacroName(String accountId) {
        if (activeProfile == null) return "";
        String published = assignedMacroNames.get(accountId);
        return published == null ? activeProfile.allMacroName : published;
    }

    public String assignedMacroName(String accountId) {
        if (accountId == null) return "";
        return assignedMacroNames.getOrDefault(accountId, "");
    }

    private void rebuildAssignedMacroNames() {
        MultiProfile profile = activeProfile;
        if (profile == null || profile.sessions.isEmpty()) {
            assignedMacroNames = Map.of();
            return;
        }
        Map<String, String> next = new HashMap<>();
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            next.put(spec.accountId(), spec.macroName().isBlank() ? profile.allMacroName : spec.macroName());
        }
        assignedMacroNames = Map.copyOf(next);
    }

    public synchronized List<String> macroCompatibility(String macroName) {
        if (macroName == null || macroName.isBlank()) return List.of();
        AutismMacro macro = AutismMacroManager.get().get(macroName);
        if (macro == null) return List.of("Macro not found");
        return List.copyOf(MultiMacroSupport.analyze(macro));
    }

    public synchronized BroadcastResult runMacroOnScope(Set<String> scope) {
        return runMacroOnScope(scope, false);
    }

    public synchronized BroadcastResult runMacroOnScope(Set<String> scope, boolean idleOnly) {
        Map<String, AutismMacro> snapshots = new HashMap<>();
        Set<String> analyzed = new java.util.HashSet<>();
        MacroStagger stagger = new MacroStagger();
        BroadcastResult result = broadcastSessionAction("Run macro", scope, session -> {
            if (idleOnly && session.isMacroRunning()) return "Macro already running; not restarted";
            String name = effectiveMacroName(session.accountId());
            if (name == null || name.isBlank()) return "No macro assigned";
            AutismMacro copy = snapshots.computeIfAbsent(name, n -> {
                AutismMacro found = AutismMacroManager.get().get(n);
                return found == null ? null : found.deepCopy();
            });
            if (copy == null) return "Macro not found: " + name;
            if (analyzed.add(name)) warnMacroCompatibility(copy);
            macroIntents.put(session.accountId(), MacroResumeIntent.assigned());
            MultiSession.Status status = session.statusValue();
            if (status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED) {
                return "Session is not connected; queued for retry";
            }
            session.startAssignedMacro(copy, stagger.next());
            return "Sent";
        });
        stagger.report();
        return result;
    }

    private final class MacroStagger {
        private final long launchedAt = System.currentTimeMillis();
        private final int gapMs = MultiMacroDelay.currentMs();
        private int started;

        long next() {
            return MultiMacroDelay.startAt(launchedAt, started++, gapMs);
        }

        void report() {
            if (gapMs <= 0 || started <= 1) return;
            appendSystem("Starting " + started + " bots " + MultiMacroDelay.valueText(gapMs) + " apart.");
        }
    }

    private void warnMacroCompatibility(AutismMacro macro) {
        List<String> warnings = MultiMacroSupport.analyze(macro);
        if (warnings.isEmpty()) return;
        String name = singleLine(macro.name, 40);
        appendSystem("Warning: macro \"" + name + "\" - " + warnings.size()
            + " item(s) may not run as intended:");
        for (String line : warnings) appendSystem(line);
    }

    public synchronized BroadcastResult runMacroDirect(AutismMacro macro) {
        return runMacroDirect(macro, null);
    }

    public synchronized BroadcastResult runMacroDirect(AutismMacro macro, Set<String> scope) {
        if (macro == null || macro.actions.isEmpty()) {
            return new BroadcastResult(0, 0, 1, List.of("Macro has no actions"));
        }
        AutismMacro copy = macro.deepCopy();
        if (copy.name == null || copy.name.isBlank()) copy.name = "Editor macro";
        warnMacroCompatibility(copy);
        MacroResumeIntent intent = MacroResumeIntent.direct(copy);
        MacroStagger stagger = new MacroStagger();
        BroadcastResult result = broadcastSessionAction("Run for Multi", scope, session -> {
            macroIntents.put(session.accountId(), intent);
            MultiSession.Status status = session.statusValue();
            if (status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED) {
                return "Session is not connected; queued for retry";
            }
            session.startAssignedMacro(copy, stagger.next());
            return "Sent";
        });
        stagger.report();
        return result;
    }

    public synchronized boolean hasQueuedMacroIntent(String accountId, String macroName) {
        if (accountId == null || macroName == null || macroName.isBlank()) return false;
        MacroResumeIntent intent = macroIntents.get(accountId);
        if (intent == null || intent.kind() != MacroIntentKind.DIRECT) return false;
        AutismMacro direct = intent.directMacro();
        if (direct == null || !macroName.equals(direct.name)) return false;
        MultiSession session = sessions.get(accountId);
        return session == null || !session.connected();
    }

    public synchronized BroadcastResult stopMacroOnScope(Set<String> scope) {
        return broadcastSessionAction("Stop macro", scope, session -> {
            macroIntents.remove(session.accountId());
            session.stopMacro();
            return "Sent";
        });
    }

    public synchronized BroadcastResult runMacroOnInteractiveScope(Set<String> requestedScope) {
        Set<String> scope = interactiveMacroScope(requestedScope);
        if (scope == null) return stalePovResult();
        return runMacroOnScope(scope);
    }

    public synchronized BroadcastResult runMacroDirectInteractive(AutismMacro macro, Set<String> requestedScope) {
        Set<String> scope = interactiveMacroScope(requestedScope);
        if (scope == null) return stalePovResult();
        return runMacroDirect(macro, scope);
    }

    public synchronized BroadcastResult stopMacroOnInteractiveScope(Set<String> requestedScope) {
        Set<String> scope = interactiveMacroScope(requestedScope);
        if (scope == null) return stalePovResult();
        return stopMacroOnScope(scope);
    }

    public synchronized boolean isMacroPlayingOnInteractiveScope(String macroName, Set<String> requestedScope) {
        if (macroName == null || macroName.isBlank()) return false;
        Set<String> scope = interactiveMacroScope(requestedScope);
        if (scope == null) return false;
        return isMacroPlayingOnScope(macroName, scope);
    }

    public synchronized boolean isMacroPlayingOnScope(String macroName, Set<String> scope) {
        if (macroName == null || macroName.isBlank()) return false;
        boolean scoped = scope != null && !scope.isEmpty();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !scope.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            if (!session.connected() || !session.isMacroRunning()) continue;
            MacroResumeIntent intent = macroIntents.get(entry.getKey());
            String intended = intent == null ? "" : intent.kind() == MacroIntentKind.DIRECT
                ? intent.directMacro() == null ? "" : intent.directMacro().name
                : effectiveMacroName(entry.getKey());
            if (macroName.equalsIgnoreCase(intended) || macroName.equalsIgnoreCase(session.currentMacroName())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> interactiveMacroScope(Set<String> requestedScope) {
        String pov = MultiTakeoverState.activeAccountId();
        return resolveInteractiveMacroScope(pov, pov == null || sessions.containsKey(pov), requestedScope);
    }

    static Set<String> resolveInteractiveMacroScope(String povAccountId, boolean povSessionExists,
                                                     Set<String> requestedScope) {
        if (povAccountId == null) return requestedScope;
        return povSessionExists ? Set.of(povAccountId) : null;
    }

    private static BroadcastResult stalePovResult() {
        return new BroadcastResult(0, 0, 1, List.of("The POV bot is no longer available"));
    }

    private void rearmMacroIfRunning(String accountId, MultiSession session) {
        MacroResumeIntent intent = macroIntents.get(accountId);
        if (intent == null) return;
        if (intent.kind() == MacroIntentKind.DIRECT) {
            AutismMacro direct = intent.directMacro();
            if (direct != null && direct.actions != null && !direct.actions.isEmpty()) {
                session.startAssignedMacro(direct);
            }
            return;
        }
        String name = effectiveMacroName(accountId);
        if (name == null || name.isBlank()) return;
        AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) return;

        if (activeProfile != null && activeProfile.loginMode == MultiProfile.LoginMode.Custom) {
            session.startLoginMacro(macro.deepCopy());
        } else {
            session.startAssignedMacro(macro.deepCopy());
        }
    }

    private void armJoinMacro(MultiProfile profile, MultiProfile.SessionSpec spec, MultiSession session) {
        if (profile == null || spec == null || session == null) return;
        if (profile.loginMode != MultiProfile.LoginMode.Custom) return;
        String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
        if (name == null || name.isBlank()) return;
        AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) return;
        macroIntents.put(spec.accountId(), MacroResumeIntent.assigned());
        session.startLoginMacro(macro.deepCopy());
    }

    public List<MultiSession.Snapshot> snapshots() {
        snapshotDemandUntilNanos = System.nanoTime() + SNAPSHOT_DEMAND_NANOS;
        return snapshotList;
    }

    public record BotHandle(String accountId, String username) {}

    public List<BotHandle> liveBots() {
        List<MultiSession> live = sessionList;
        List<BotHandle> out = new ArrayList<>(live.size());
        for (MultiSession session : live) {
            if (session == null) continue;
            String name = session.username();
            out.add(new BotHandle(session.accountId(), name == null || name.isBlank() ? session.accountId() : name));
        }
        return List.copyOf(out);
    }

    public Set<String> botUsernamesLower() {
        List<MultiSession> live = sessionList;
        if (live.isEmpty()) return Set.of();
        Set<String> names = new java.util.HashSet<>(live.size() * 2);
        for (MultiSession session : live) {
            if (session == null) continue;
            String name = session.username();
            if (name != null && !name.isBlank()) names.add(name.toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    public static BotHandle resolveBot(java.util.Collection<BotHandle> bots, String botName) {
        if (bots == null || botName == null) return null;
        String want = botName.trim();
        if (want.isEmpty()) return null;
        for (BotHandle bot : bots) {
            if (bot != null && bot.username() != null && bot.username().equalsIgnoreCase(want)) return bot;
        }
        for (BotHandle bot : bots) {
            if (bot != null && bot.accountId() != null && bot.accountId().equalsIgnoreCase(want)) return bot;
        }
        return null;
    }

    public Set<String> scopeForBot(String botName) {
        if (botName == null || botName.isBlank()) return Set.of();
        BotHandle bot = resolveBot(liveBots(), botName);
        return bot == null ? null : Set.of(bot.accountId());
    }

    private void publishSnapshots(boolean force) {
        if (!force && System.nanoTime() - snapshotDemandUntilNanos >= 0L) return;
        List<MultiSession> live = sessionList;
        if (live.isEmpty()) {
            snapshotList = List.of();
            return;
        }
        List<MultiSession.Snapshot> previous = snapshotList;
        ArrayList<MultiSession.Snapshot> changed = null;
        for (int i = 0; i < live.size(); i++) {
            MultiSession.Snapshot next = live.get(i).snapshot();
            if (changed == null && i < previous.size() && previous.get(i) == next) continue;
            if (changed == null) {
                changed = new ArrayList<>(live.size());
                for (int j = 0; j < i; j++) changed.add(previous.get(j));
            }
            changed.add(next);
        }
        if (changed == null && previous.size() == live.size()) return;

        if (changed == null) changed = new ArrayList<>(previous.subList(0, live.size()));
        snapshotList = List.copyOf(changed);
    }

    public long sessionRevision() {
        return sessionRevision;
    }

    public long uiRevision() {
        return uiRevision;
    }

    public long chatRevision() {
        return chatRevision;
    }

    public synchronized List<ChatLine> chatView(Set<String> scope) {
        if (scope == null || scope.isEmpty()) {
            List<ChatLine> out = new ArrayList<>(unifiedChat.size());
            for (UnifiedMsg m : unifiedChat) {
                out.add(new ChatLine(m.seq, m.time, m.representative,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), m.system, m.count, m.source));
            }
            return out;
        }
        if (scope.size() == 1) {
            String id = scope.iterator().next();
            ArrayDeque<ChatMsg> acc = accountChat.get(id);
            if (acc == null) return List.of();
            List<ChatLine> out = new ArrayList<>(acc.size());
            for (ChatMsg m : acc) out.add(new ChatLine(m.seq, m.time, m.component, Map.of(id, m.component), false, 1, m.source()));
            return out;
        }

        record Owned(String id, ChatMsg msg) {
        }
        List<Owned> gathered = new ArrayList<>();
        for (String id : scope) {
            ArrayDeque<ChatMsg> acc = accountChat.get(id);
            if (acc == null) continue;
            for (ChatMsg m : acc) gathered.add(new Owned(id, m));
        }
        gathered.sort(Comparator.comparingLong(o -> o.msg().seq()));
        Map<String, UnifiedMsg> recent = new HashMap<>();
        List<UnifiedMsg> ordered = new ArrayList<>();
        for (Owned o : gathered) {
            String text = o.msg().text();
            String src = o.msg().source();
            String key = groupKey(src, text);
            UnifiedMsg prev = recent.get(key);
            if (prev != null && !text.isBlank() && withinChatMergeWindow(prev.time, o.msg().time())) {
                prev.count++;
                prev.perAccount.putIfAbsent(o.id(), o.msg().component());
            } else {
                UnifiedMsg m = new UnifiedMsg(o.msg().seq(), o.msg().time(), text, src, false, o.msg().component());
                m.perAccount.put(o.id(), o.msg().component());
                ordered.add(m);
                if (!text.isBlank()) recent.put(key, m);
            }
        }
        int startIndex = Math.max(0, ordered.size() - CHAT_LIMIT);
        List<ChatLine> out = new ArrayList<>(ordered.size() - startIndex);
        for (int i = startIndex; i < ordered.size(); i++) {
            UnifiedMsg m = ordered.get(i);
            out.add(new ChatLine(m.seq, m.time, m.representative,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), false, m.count, m.source));
        }
        return out;
    }

    public synchronized String sendCommandTo(String accountId, String command) {
        MultiSession session = sessions.get(accountId);
        if (session == null) return "No session";
        try {
            return session.sendConsoleLine(command);
        } catch (RuntimeException error) {
            return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }

    public synchronized BroadcastResult broadcastConsole(String line) {
        return broadcastConsole(line, null);
    }

    public synchronized BroadcastResult broadcastConsole(String line, Set<String> targets) {

        if (line != null && AutismCommands.isAutismCommandMessage(line.trim())) return runClientCommand(line, targets);
        if (sessions.isEmpty()) return sessionsStartingResult("Send");
        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = session.sendConsoleLine(line);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result) || result.startsWith("Queued ")) {
                sent++;
            } else if (result.contains("not ready") || result.contains("Blocked") || result.contains("key") || result.contains("Rate limited")) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));
        if (failed > 0) {
            appendSystem(result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    public synchronized BroadcastResult runClientCommand(String line, Set<String> targets) {
        String body = AutismCommands.commandBody(line).trim();
        if (body.isEmpty()) {
            appendSystem("Empty command");
            return new BroadcastResult(0, 0, 0, List.of());
        }
        if (sessions.isEmpty()) return sessionsStartingResult("Command");
        int space = body.indexOf(' ');
        String name = (space < 0 ? body : body.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
        String args = space < 0 ? "" : body.substring(space + 1).trim();

        String deny = MultiClientCommands.batchDenyReason(name, args);
        if (deny != null) {

            appendSystem(name + ": " + deny);
            return new BroadcastResult(0, 0, 0, List.of());
        }

        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = session.runClientAction(name, args);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result)) {
                sent++;
            } else if (result.contains("not ready") || result.contains("Blocked") || result.contains("Rate limited")) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));
        if (failed > 0) {
            appendSystem(name + " -> " + result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    public synchronized BroadcastResult broadcastMovementNow() {
        return broadcastMovementNow(null);
    }

    public synchronized BroadcastResult broadcastMovementNow(Set<String> targets) {
        return broadcastSessionAction("Move", targets, MultiSession::sendImmediateMoveLook);
    }

    private record PreparedStep(Class<? extends Packet<?>> packetClass, String arguments) {
    }

    public synchronized BroadcastResult broadcastQuickAction(MultiQuickAction action) {
        return broadcastQuickAction(action, null);
    }

    public synchronized BroadcastResult broadcastQuickAction(MultiQuickAction action, Set<String> targets) {
        if (action == null || action.empty()) {
            int skipped = targets == null || targets.isEmpty() ? sessions.size()
                : (int) targets.stream().filter(sessions::containsKey).count();
            BroadcastResult result = new BroadcastResult(0, skipped, 0, List.of("Empty slot"));
            appendSystem("Empty slot");
            return result;
        }
        MultiQuickAction sendAction = new MultiQuickAction(action);
        List<PreparedStep> prepared = new ArrayList<>();
        for (MultiQuickAction.Step step : sendAction.steps) {
            Class<? extends Packet<?>> packetClass = resolvePacket(step.packetClass());
            if (packetClass == null) {
                BroadcastResult result = new BroadcastResult(0, 0, sessions.size(), List.of("Missing packet"));
                appendSystem("Missing packet");
                return result;
            }
            prepared.add(new PreparedStep(packetClass, step.arguments()));
        }

        return broadcastSessionAction(sendAction.label(0), targets, session -> {
            for (PreparedStep step : prepared) {
                String result = session.sendManual(step.packetClass(), step.arguments());
                if (!"Sent".equals(result)) return result;
            }
            return "Sent";
        });
    }

    public synchronized BroadcastResult broadcastManual(Class<? extends Packet<?>> packetClass, String arguments) {
        return broadcastSessionAction("Packet", session -> session.sendManual(packetClass, arguments));
    }

    private BroadcastResult broadcastSessionAction(String label, java.util.function.Function<MultiSession, String> sender) {
        return broadcastSessionAction(label, null, sender);
    }

    private BroadcastResult broadcastSessionAction(String label, Set<String> targets,
                                                   java.util.function.Function<MultiSession, String> sender) {
        if (sessions.isEmpty()) return sessionsStartingResult(label);
        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = sender.apply(session);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result)) {
                sent++;
            } else if (isSkippedSendResult(result)) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));

        if (failed > 0) {
            appendSystem(singleLine(label, 24) + ": " + result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    private BroadcastResult sessionsStartingResult(String label) {
        int waiting = activeProfile == null ? 0 : activeProfile.sessions.size();
        BroadcastResult result = new BroadcastResult(0, waiting, 0, List.of("Sessions are still starting"));
        return result;
    }

    private void appendResultDetails(List<String> details) {
        if (details == null || details.isEmpty()) return;
        int shown = Math.min(5, details.size());
        for (int i = 0; i < shown; i++) appendSystem(details.get(i));
        if (details.size() > shown) appendSystem((details.size() - shown) + " more sessions omitted");
    }

    public synchronized BroadcastResult useOnScope(Set<String> scope) {
        return broadcastSessionAction("Use", scope, MultiSession::useItem);
    }

    public synchronized BroadcastResult closeOnScope(Set<String> scope) {
        return broadcastSessionAction("Close", scope, MultiSession::closeContainer);
    }

    public synchronized BroadcastResult closeSilentOnScope(Set<String> scope) {
        return broadcastSessionAction("Close (silent)", scope, MultiSession::closeSilent);
    }

    public MultiSession.MenuView menuView(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? null : session.menuView();
    }

    public MultiSession.MenuView inventoryView(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? null : session.inventoryView();
    }

    public long menuRevision(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.menuRevision();
    }

    public String clickBotSlot(String accountId, int handler, MultiClientCommands.ClickSpec spec) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null || spec == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.clickSlot(handler, spec.button(), spec.input());
    }

    private static boolean povMacroBlocksInput(String accountId, MultiSession session) {
        return session != null && MultiTakeoverState.isActive(accountId) && session.macroOwnsPilot();
    }

    public int hotbarIndexForHandler(String accountId, int handler) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.hotbarIndexOfHandler(handler);
    }

    public int visibleSlotForHandler(String accountId, int handler) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.handlerToVisibleSlot(handler);
    }

    public synchronized boolean setControllable(String accountId, boolean on) {
        if (accountId == null) return false;
        if (on) {
            if (!controllableIds.contains(accountId) && controllableIds.size() >= MAX_CONTROLLABLE) return false;
            controllableIds.add(accountId);
        } else {
            controllableIds.remove(accountId);
        }
        MultiSession session = sessions.get(accountId);
        if (session != null) session.setCaptureWorld(on);
        return true;
    }

    public boolean isControllable(String accountId) {
        return accountId != null && controllableIds.contains(accountId);
    }

    MultiSession session(String accountId) {
        return accountId == null ? null : sessionsById.get(accountId);
    }

    public java.util.UUID botServerUuid(String accountId) {
        MultiSession session = session(accountId);
        return session == null ? null : session.serverUuid();
    }

    public static com.mojang.authlib.GameProfile botProfileByServerUuid(java.util.UUID uuid) {
        MultiManager mgr = getIfInitialized();
        if (mgr == null || uuid == null || !mgr.isActive()) return null;
        for (MultiSession session : mgr.sessionsById.values()) {
            if (uuid.equals(session.serverUuid())) return session.takeoverProfile();
        }
        return null;
    }

    public int controllableCount() {
        return controllableIds.size();
    }

    public int maxControllable() {
        return MAX_CONTROLLABLE;
    }

    private void armCapture(MultiSession session) {
        if (session != null && controllableIds.contains(session.accountId())) session.setCaptureWorld(true);
    }

    public String botServerAddress(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        if (session != null) {
            String addr = session.serverAddress();
            if (addr != null && !addr.isBlank()) return addr;
        }
        MultiProfile profile = activeProfile();
        return profile == null || profile.serverAddress == null || profile.serverAddress.isBlank()
            ? null : profile.serverAddress;
    }

    public int selectedHotbarHandler(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.selectedHotbarHandler();
    }

    public String selectBotHotbar(String accountId, int index) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
    }

    public String useBotHotbar(String accountId, int index) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        String selected = session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
        if (!"Sent".equals(selected)) return selected;
        return session.runClientAction("use", "");
    }

    public BroadcastResult clickBotSlots(List<String> accountIds, int handler, MultiClientCommands.ClickSpec spec) {
        int sent = 0, skipped = 0, failed = 0;
        if (accountIds != null && spec != null) {
            for (String accountId : accountIds) {
                MultiSession session = sessionsById.get(accountId);
                if (session == null || povMacroBlocksInput(accountId, session)
                    || (handler >= 0 && handler >= session.clickHandlerLimit())) {
                    skipped++;
                    continue;
                }
                if ("Sent".equals(session.clickSlot(handler, spec.button(), spec.input()))) sent++;
                else failed++;
            }
        }
        return new BroadcastResult(sent, skipped, failed, List.of());
    }

    public String buttonClickBot(String accountId, int buttonId) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.buttonClick(buttonId);
    }

    public BroadcastResult buttonClickBots(List<String> accountIds, int buttonId, String typeId) {
        return fanoutMenuAction(accountIds, typeId, session -> session.buttonClick(buttonId));
    }

    public String selectTradeBot(String accountId, int index) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.selectTrade(index);
    }

    public BroadcastResult selectTradeBots(List<String> accountIds, int index, String typeId) {
        return fanoutMenuAction(accountIds, typeId, session -> session.selectTrade(index));
    }

    public String setBeaconBot(String accountId, int primaryId, int secondaryId) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.setBeacon(primaryId, secondaryId);
    }

    public BroadcastResult setBeaconBots(List<String> accountIds, int primaryId, int secondaryId, String typeId) {
        return fanoutMenuAction(accountIds, typeId, session -> session.setBeacon(primaryId, secondaryId));
    }

    public String placeRecipeBot(String accountId, net.minecraft.world.item.crafting.display.RecipeDisplayId id, boolean all) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.placeRecipe(id, all);
    }

    public BroadcastResult placeRecipeBots(List<String> accountIds, net.minecraft.world.item.crafting.display.RecipeDisplayId id,
                                           boolean all, String typeId) {
        return fanoutMenuAction(accountIds, typeId, session -> session.placeRecipe(id, all));
    }

    public String renameBotItem(String accountId, String name) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        if (povMacroBlocksInput(accountId, session)) return "Macro controls POV bot";
        return session.renameItem(name);
    }

    public BroadcastResult renameBotItems(List<String> accountIds, String name, String typeId) {
        return fanoutMenuAction(accountIds, typeId, session -> session.renameItem(name));
    }

    private BroadcastResult fanoutMenuAction(List<String> accountIds, String typeId,
                                             java.util.function.Function<MultiSession, String> action) {
        int sent = 0, skipped = 0, failed = 0;
        if (accountIds != null) {
            for (String accountId : accountIds) {
                MultiSession session = sessionsById.get(accountId);
                if (session == null || povMacroBlocksInput(accountId, session)
                    || (typeId != null && !typeId.isEmpty() && !typeId.equals(session.menuTypeId()))) {
                    skipped++;
                    continue;
                }
                if ("Sent".equals(action.apply(session))) sent++;
                else failed++;
            }
        }
        return new BroadcastResult(sent, skipped, failed, List.of());
    }

    public BroadcastResult selectBotHotbars(List<String> accountIds, int index) {
        return fanoutHotbar(accountIds, index, false);
    }

    public BroadcastResult useBotHotbars(List<String> accountIds, int index) {
        return fanoutHotbar(accountIds, index, true);
    }

    private BroadcastResult fanoutHotbar(List<String> accountIds, int index, boolean use) {
        int sent = 0, skipped = 0, failed = 0;
        if (accountIds != null) {
            for (String accountId : accountIds) {
                if (sessionsById.get(accountId) == null) {
                    skipped++;
                    continue;
                }
                String result = use ? useBotHotbar(accountId, index) : selectBotHotbar(accountId, index);
                if ("Sent".equals(result)) sent++;
                else failed++;
            }
        }
        return new BroadcastResult(sent, skipped, failed, List.of());
    }

    private static boolean isSkippedSendResult(String result) {
        if (result == null) return false;
        return result.contains("not ready")
            || result.contains("Blocked")
            || result.contains("headless-safe")
            || result.contains("Rate limited")
            || result.contains("already running")
            || result.contains("Macro controls POV bot")
            || result.contains("Position is not ready")
            || result.contains("Signing key");
    }

    @Override
    public synchronized String identityRejection(MultiSession session, java.util.UUID profileId) {
        if (session == null || profileId == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return "Stale Multi session";
        return identityRejection(renderedProfileIdAtStart, resolvedIdentities, session.accountId(), profileId);
    }

    static String identityRejection(java.util.UUID renderedProfileId, Map<java.util.UUID, String> resolved,
                                    String accountId, java.util.UUID profileId) {
        if (profileId == null || accountId == null || resolved == null) return "Stale Multi session";
        if (renderedProfileId != null && renderedProfileId.equals(profileId)) return "Account is already used by the rendered client";
        String existing = resolved.putIfAbsent(profileId, accountId);
        return existing == null || existing.equals(accountId)
            ? ""
            : "Duplicate Minecraft identity in this batch";
    }

    public synchronized void replaceMacroReference(String oldName, String newName) {
        if (activeProfile == null || oldName == null || oldName.isBlank()) return;
        String replacement = newName == null ? "" : newName.trim();
        List<String> affected = new ArrayList<>();
        for (String accountId : sessions.keySet()) {
            if (oldName.equals(effectiveMacroName(accountId))) affected.add(accountId);
        }
        if (activeProfile.replaceMacroReference(oldName, replacement)) {
            MultiProfileManager.get().put(activeProfile);
            rebuildAssignedMacroNames();
            uiRevision++;
        }
        if (replacement.isBlank()) {
            for (String accountId : affected) {
                macroIntents.remove(accountId);
                MultiSession session = sessions.get(accountId);
                if (session != null) session.stopMacro();
            }
        }
    }

    @Override
    public synchronized void stateChanged(MultiSession session) {
        if (session == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return;
        MultiSession.Status current = session.statusValue();
        String accountId = session.accountId();
        MultiSession.Status previous = lastPostedStatus.put(accountId, current);
        if (previous != current && (current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)) {
            AutismProxy proxy = runtimeProxies.get(accountId);
            lastFailedProxyIds.put(accountId, proxy == null ? "" : proxy.stableId());

            String reason = singleLine(session.detailText(), 160);
            if (!reason.isBlank()) reportConnectionIssue(accountLabel(accountId), reason);

            fireSessionDropped(accountId);
        }
        if ((current == MultiSession.Status.READY || current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)
            && connecting.remove(session)) {
            pump();
        }
        if (current == MultiSession.Status.READY || current == MultiSession.Status.FAILED
            || current == MultiSession.Status.DISCONNECTED) retryingAccounts.remove(accountId);
    }

    @Override
    public void note(MultiSession session, String text) {
        if (session == null || text == null || text.isBlank() || session.generation() != generation) return;

        recordNote(session, text);
    }

    @Override
    public void menuClosed(MultiSession session) {
        if (session == null || session.generation() != generation) return;
        synchronized (this) {
            if (sessions.get(session.accountId()) != session) return;
        }
        fireMenuClosed(session.accountId());
    }

    private synchronized void recordNote(MultiSession session, String text) {
        if (session.generation() != generation || sessions.get(session.accountId()) != session) return;
        appendSystem(text);
    }

    @Override
    public void chat(MultiSession session, Component component) {
        if (session == null || component == null || session.generation() != generation) return;

        String text = singleLine(component.getString(), 512);
        Component visible = sanitizeComponent(component, 512);
        String source = singleLine(session.currentMacroName(), 32);
        if (recordChat(session, text, visible, source)) {
            MultiPovChat.onBotChat(session, visible);
        }
    }

    private synchronized boolean recordChat(MultiSession session, String text, Component visible, String source) {
        if (session.generation() != generation || sessions.get(session.accountId()) != session) return false;
        String accountId = session.accountId();
        long now = System.currentTimeMillis();
        long seq = ++chatSeq;

        ArrayDeque<ChatMsg> account = accountChat.computeIfAbsent(accountId, key -> new ArrayDeque<>());
        account.addLast(new ChatMsg(seq, now, visible, text, source));
        while (account.size() > CHAT_LIMIT) account.removeFirst();

        if (!text.isBlank()) {
            pruneRecent(now);
            String key = groupKey(source, text);
            UnifiedMsg existing = recentByText.get(key);
            if (existing != null && !existing.system && withinChatMergeWindow(existing.time, now)) {
                existing.count++;
                existing.perAccount.putIfAbsent(accountId, visible);
            } else {
                UnifiedMsg msg = new UnifiedMsg(seq, now, text, source, false, visible);
                msg.perAccount.put(accountId, visible);
                pushUnified(msg);
                recentByText.put(key, msg);
            }
        }
        chatRevision++;
        return true;
    }

    synchronized List<MultiPovChat.HistoryLine> povChatHistory(String accountId) {
        ArrayDeque<ChatMsg> messages = accountChat.get(accountId);
        if (messages == null || messages.isEmpty()) return List.of();
        List<MultiPovChat.HistoryLine> result = new ArrayList<>(messages.size());
        for (ChatMsg message : messages) {
            result.add(new MultiPovChat.HistoryLine(message.time(), message.component()));
        }
        return List.copyOf(result);
    }

    @Override
    public void customMenuNeedsPassword(MultiSession session, String title) {
        boolean firstAlert;
        synchronized (this) {
            if (session == null || session.generation() != generation
                || sessions.get(session.accountId()) != session) return;
            firstAlert = !passwordPromptShown;
            passwordPromptShown = true;

            if (firstAlert) {
                appendSystem("Warning: " + accountLabel(session.accountId()) + " got a login screen (\""
                    + singleLine(title, 48) + "\") but no password is stored for this profile. Set one in the popup.");
            }
        }
        if (!firstAlert) return;
        Minecraft.getInstance().execute(() -> {
            autismclient.util.AutismNotifications.error("A bot hit a login screen. Set a password.");
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof autismclient.gui.screen.AutismMultiPasswordPromptScreen) return;
            mc.gui.setScreen(new autismclient.gui.screen.AutismMultiPasswordPromptScreen(mc.gui.screen()));
        });
    }

    public synchronized int applyPasswordToAllAccounts(String password) {
        if (activeProfile == null || password == null || password.isBlank()) return 0;
        int updated = 0;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (activeProfile.setFormValue(spec.accountId(), "password", password)) updated++;
        }
        persistAndPushFormValues();
        return updated;
    }

    public synchronized int applyGeneratedPasswords() {
        if (activeProfile == null) return 0;
        int updated = 0;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (activeProfile.setFormValue(spec.accountId(), "password", generatePassword())) updated++;
        }
        persistAndPushFormValues();
        return updated;
    }

    public static String generatePassword() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        int length = 9 + random.nextInt(8);
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return out.toString();
    }

    private void persistAndPushFormValues() {
        if (activeProfile == null) return;
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            entry.getValue().updateFormValues(activeProfile.openFormValues(entry.getKey()));
        }
    }

    public synchronized void updateActiveFormValues(MultiProfile source) {
        if (activeProfile == null || source == null) return;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            String password = source.openFormValues(spec.accountId()).get("password");
            if (password != null) activeProfile.setFormValue(spec.accountId(), "password", password);
        }
        persistAndPushFormValues();
    }

    @Override
    public boolean macroStepMet(MultiSession requester, autismclient.util.macro.WaitForMacroStepAction action) {
        List<MultiSession.MacroProgress> published = new ArrayList<>();
        for (MultiSession session : sessionList) {
            if (session == requester) continue;
            published.add(session.snapshot().macroProgress());
        }
        return publishedMacroStepMet(action, published);
    }

    @Override
    public synchronized void macroChained(MultiSession session, AutismMacro macro) {
        if (session == null || macro == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return;

        macroIntents.put(session.accountId(), MacroResumeIntent.direct(macro));
    }

    @Override
    public synchronized void macroDisconnected(MultiSession session) {
        if (session == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return;
        macroIntents.remove(session.accountId());
    }

    static boolean publishedMacroStepMet(
        autismclient.util.macro.WaitForMacroStepAction action,
        Iterable<MultiSession.MacroProgress> published
    ) {
        if (action == null) return true;
        String target = action.macroName == null ? "" : action.macroName.trim();
        if (target.isEmpty()) return true;
        int step = Math.max(1, action.step);
        boolean found = false;
        for (MultiSession.MacroProgress progress : published) {
            if (progress == null || !target.equalsIgnoreCase(progress.macroName())) continue;
            found = true;
            boolean satisfied = switch (action.mode == null
                ? autismclient.util.macro.WaitForMacroStepAction.WaitMode.COMPLETED_STEP : action.mode) {
                case STARTED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                case COMPLETED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                case FINISHED -> !progress.running();
            };
            if (satisfied) return true;
        }
        return !found && action.mode == autismclient.util.macro.WaitForMacroStepAction.WaitMode.FINISHED;
    }

    private void pushUnified(UnifiedMsg msg) {
        unifiedChat.addLast(msg);
        while (unifiedChat.size() > CHAT_LIMIT) {
            UnifiedMsg removed = unifiedChat.removeFirst();
            recentByText.remove(removed.system ? systemKey(removed.text) : groupKey(removed.source, removed.text),
                removed);
        }
    }

    private void pruneRecent(long now) {
        if (recentByText.size() < 256) return;

        recentByText.values().removeIf(m -> m.system
            ? now - m.lastAt > SYS_MERGE_MS
            : !withinChatMergeWindow(m.time, now));
    }

    static boolean withinChatMergeWindow(long firstCopyAt, long currentCopyAt) {
        long elapsed = currentCopyAt - firstCopyAt;
        return elapsed >= 0L && elapsed <= CHAT_MERGE_MS;
    }

    private void tick() {
        List<MultiSession> snapshot;
        long tickGeneration;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!active) return;
            snapshot = sessionList;
            tickGeneration = generation;
        }

        for (MultiSession session : snapshot) tickOne(session, now);
        synchronized (this) {

            if (!active || generation != tickGeneration) return;
            drainMacroFinishes(snapshot);
            if (now - lastSnapshotPublishAt >= SNAPSHOT_INTERVAL_MS) {
                publishSnapshots(false);
                lastSnapshotPublishAt = now;
            }
        }
    }

    private void drainMacroFinishes(List<MultiSession> snapshot) {
        java.util.LinkedHashMap<String, Integer> groups = null;
        for (MultiSession session : snapshot) {
            String note = session.pollMacroFinish();
            if (note == null) continue;
            MacroFinish finish = parseMacroFinish(note);
            String macroName = finish.macroName();
            String reason = finish.reason();
            if (!"chained".equals(reason)) macroIntents.remove(session.accountId());

            String key = macroFinishVerb(reason) + '\0' + macroName;
            if (groups == null) groups = new java.util.LinkedHashMap<>();
            groups.merge(key, 1, Integer::sum);
        }
        if (groups == null) return;
        for (var e : groups.entrySet()) {
            int sep = e.getKey().indexOf('\0');
            String verb = e.getKey().substring(0, sep);
            String macroName = e.getKey().substring(sep + 1);
            int count = e.getValue();
            appendSystem("Macro \"" + macroName + "\" " + verb + " on " + count + " bot" + (count == 1 ? "" : "s") + ".");
        }
    }

    static MacroFinish parseMacroFinish(String note) {
        String value = note == null ? "" : note;
        int separator = value.indexOf(0);
        return separator >= 0
            ? new MacroFinish(value.substring(0, separator), value.substring(separator + 1))
            : new MacroFinish(value, "done");
    }

    private static String macroFinishVerb(String reason) {
        return switch (reason) {
            case "chained" -> "handed off";
            case "error" -> "stopped on an error";
            case "stopped" -> "finished (stop action)";
            default -> "finished";
        };
    }

    private static void tickOne(MultiSession session, long now) {
        try {
            session.tick(now);
        } catch (RuntimeException error) {

            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            try {
                session.failExternal("Session tick failed: " + singleLine(message, 120));
            } catch (RuntimeException ignored) {

            }
        }
    }

    private synchronized void ensureTicking() {
        if (tickTask == null || tickTask.isCancelled() || tickTask.isDone()) {

            tickTask = scheduler.scheduleWithFixedDelay(this::tick, 50L, 50L, TimeUnit.MILLISECONDS);
        }
    }

    private static final long ISSUE_SUMMARY_MS = 15_000L;
    private final Map<String, long[]> connectionIssues = new HashMap<>();

    private void reportConnectionIssue(String account, String reason) {
        String key = singleLine(reason == null ? "" : reason.trim(), 160);
        if (key.isBlank()) return;
        long now = System.currentTimeMillis();
        long[] agg = connectionIssues.get(key);
        if (agg == null) {
            connectionIssues.put(key, new long[]{1, 0});
            postConnectionIssue(account + ": " + key);
            return;
        }
        agg[0]++;

        if (agg[0] == 4 || (agg[0] > 4 && now - agg[1] >= ISSUE_SUMMARY_MS)) {
            postConnectionIssue(key + " (x" + agg[0] + " accounts)");
            agg[1] = now;
        }
    }

    private void postConnectionIssue(String line) {
        appendSystem(line);
        AutismClientMessaging.sendPrefixed("Multi: " + line);
    }

    private synchronized void appendSystem(String text) {
        String safe = singleLine(text, 512);
        if (safe.isBlank()) return;
        long now = System.currentTimeMillis();
        pruneRecent(now);

        String key = systemKey(safe);
        UnifiedMsg existing = recentByText.get(key);
        if (existing != null && existing.system && now - existing.lastAt < SYS_MERGE_MS) {
            existing.count++;
            existing.lastAt = now;
            chatRevision++;
            return;
        }

        Component render = AutismClientMessaging.themedTag("Multi").append(AutismClientMessaging.themedBody(safe));
        UnifiedMsg msg = new UnifiedMsg(++chatSeq, now, safe, "", true, render);
        pushUnified(msg);
        recentByText.put(key, msg);
        chatRevision++;
    }

    public static String singleLine(String text, int maxChars) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(Math.min(text.length(), Math.max(16, maxChars)));
        boolean spaced = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean space = Character.isISOControl(ch) || Character.isWhitespace(ch);
            if (space) {
                if (!spaced && out.length() > 0) {
                    out.append(' ');
                    spaced = true;
                }
                continue;
            }
            out.append(ch);
            spaced = false;
            if (maxChars > 3 && out.length() >= maxChars) break;
        }
        String safe = out.toString().trim();
        if (maxChars > 3 && safe.length() > maxChars - 3) safe = safe.substring(0, maxChars - 3).trim() + "...";
        return safe;
    }

    static Component sanitizeComponent(Component component, int maxChars) {
        if (component == null || maxChars <= 0) return Component.empty();
        MutableComponent safe = Component.empty();
        int[] length = {0};
        boolean[] spaced = {true};
        component.visit((style, part) -> {
            if (part == null || part.isEmpty() || length[0] >= maxChars) return Optional.empty();
            StringBuilder run = new StringBuilder(Math.min(part.length(), maxChars - length[0]));
            for (int i = 0; i < part.length() && length[0] < maxChars; i++) {
                char ch = part.charAt(i);
                if (Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                    if (!spaced[0] && length[0] > 0) {
                        run.append(' ');
                        length[0]++;
                        spaced[0] = true;
                    }
                } else {
                    run.append(ch);
                    length[0]++;
                    spaced[0] = false;
                }
            }
            if (!run.isEmpty()) safe.append(Component.literal(run.toString()).withStyle(style == null ? Style.EMPTY : style));
            return Optional.empty();
        }, Style.EMPTY);
        return safe;
    }

    private static String accountLabel(String accountId) {
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) return AutismAccountSessionSwitcher.getOriginalUser().getName();
        AutismAccount account = AutismAccountManager.get().findById(accountId);
        return account == null ? accountId : account.displayName();
    }

    private static AutismProxy copyProxy(AutismProxy source) {
        if (source == null) return null;
        AutismProxy copy = new AutismProxy();
        copy.id = source.stableId();
        copy.name = source.name;
        copy.type = source.type;
        copy.address = source.address;
        copy.port = source.port;
        copy.username = source.username;
        copy.password = source.password;
        return copy;
    }

    static String proxyLeaseKey(AutismProxy proxy) {
        if (proxy == null || !proxy.isValid()) return "";
        return (proxy.address == null ? "" : proxy.address.trim().toLowerCase(Locale.ROOT)) + ':' + proxy.port;
    }

    static List<AutismProxy> distinctUsableProxies(List<AutismProxy> proxies) {
        if (proxies == null || proxies.isEmpty()) return List.of();
        List<AutismProxy> ordered = new ArrayList<>();
        for (AutismProxy proxy : proxies) if (isRetryCandidate(proxy)) ordered.add(proxy);
        ordered.sort(Comparator
            .comparingInt(MultiManager::retryRank)
            .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L
                ? proxy.latency : Long.MAX_VALUE)
            .thenComparing(proxy -> proxy.displayName().toLowerCase(Locale.ROOT)));
        reserveMainProxyEndpoint(ordered);
        Map<String, AutismProxy> distinct = new LinkedHashMap<>();
        for (AutismProxy proxy : ordered) distinct.putIfAbsent(proxyLeaseKey(proxy), proxy);
        return List.copyOf(distinct.values());
    }

    private static void reserveMainProxyEndpoint(List<AutismProxy> candidates) {
        if (candidates == null || candidates.size() < 2) return;

        if (Minecraft.getInstance() == null) return;
        AutismProxy mainProxy = AutismProxyManager.get().getEnabled();
        if (mainProxy == null) return;
        String reserved = proxyLeaseKey(mainProxy);
        if (reserved.isBlank()) return;
        long distinctEndpoints = candidates.stream().map(MultiManager::proxyLeaseKey).distinct().count();
        if (distinctEndpoints > 1) candidates.removeIf(proxy -> reserved.equals(proxyLeaseKey(proxy)));
    }

    static Map<String, AutismProxy> distributeProxies(List<MultiProfile.SessionSpec> sessions,
                                                       List<AutismProxy> candidates) {
        if (sessions == null || sessions.isEmpty()) return Map.of();
        if (candidates == null || candidates.isEmpty()) return Map.of();
        List<AutismProxy> distinct = new ArrayList<>();
        Set<String> leases = new HashSet<>();
        for (AutismProxy candidate : candidates) {
            String key = proxyLeaseKey(candidate);
            if (!key.isBlank() && leases.add(key)) distinct.add(candidate);
        }
        if (distinct.isEmpty()) return Map.of();
        Map<String, AutismProxy> assignment = new LinkedHashMap<>();
        int index = 0;
        for (MultiProfile.SessionSpec spec : sessions) {

            if (spec.direct()) continue;
            assignment.put(spec.accountId(), distinct.get(index++ % distinct.size()));
        }
        return assignment;
    }

    static AutismProxy selectRetryProxy(List<AutismProxy> proxies, String currentProxyId, String lastFailedProxyId, boolean currentDirect) {
        if (currentDirect) return null;
        if (proxies == null || proxies.isEmpty()) return null;
        String current = currentProxyId == null ? "" : currentProxyId;
        String failed = lastFailedProxyId == null ? "" : lastFailedProxyId;
        AutismProxy candidate = bestRetryProxy(proxies, current, failed);
        if (candidate != null) return candidate;
        candidate = bestRetryProxy(proxies, "", failed);
        if (candidate != null) return candidate;
        return bestRetryProxy(proxies, "", "");
    }

    private static AutismProxy bestRetryProxy(List<AutismProxy> proxies, String avoidCurrent, String avoidFailed) {
        if (proxies == null || proxies.isEmpty()) return null;
        String current = avoidCurrent == null ? "" : avoidCurrent;
        String failed = avoidFailed == null ? "" : avoidFailed;
        return proxies.stream()
            .filter(MultiManager::isRetryCandidate)
            .filter(proxy -> {
                String id = proxy.stableId();
                return (current.isBlank() || !current.equals(id)) && (failed.isBlank() || !failed.equals(id));
            })
            .min(Comparator
                .comparingInt(MultiManager::retryRank)
                .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE)
                .thenComparing(proxy -> proxy.displayName().toLowerCase(Locale.ROOT)))
            .orElse(null);
    }

    private static boolean isRetryCandidate(AutismProxy proxy) {
        return proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD;
    }

    private static int retryRank(AutismProxy proxy) {
        if (proxy == null || proxy.status == null) return 3;
        return switch (proxy.status) {
            case ALIVE -> 0;
            case UNCHECKED -> 1;
            case CHECKING -> 2;
            case DEAD -> 3;
        };
    }

    public static Class<? extends Packet<?>> resolvePacket(String name) {
        return AutismPacketRegistry.getPacket(name);
    }
}
