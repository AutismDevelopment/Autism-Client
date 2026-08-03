package autismclient.modules;

import autismclient.AutismClientAddon;
import autismclient.gui.screen.AutismModuleScreen;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismInputGate;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPayloadChannelSubscriptionManager;
import autismclient.util.AutismPayloadFilterNotifier;
import autismclient.util.AutismPayloadStudySession;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismPerf;
import autismclient.util.AutismServerInfoOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class AutismModule {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final AutismModule INSTANCE = new AutismModule();
    private static final long PASSIVE_PAYLOAD_CAPTURE_MS = 20_000L;
    private static final int PASSIVE_PAYLOAD_RING_CAP = 96;
    private static final int PAYLOAD_FINGERPRINT_CAP = 256;
    private static final boolean PAYLOAD_TRACE = Boolean.getBoolean("autism.payload.trace");

    private static final Set<Class<?>> C2S_EXCLUDED_DEFAULTS = Set.of(
        net.minecraft.network.protocol.common.ServerboundKeepAlivePacket.class,
        net.minecraft.network.protocol.common.ServerboundPongPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.class,
        net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.class,
        net.minecraft.network.protocol.game.ServerboundContainerClosePacket.class,
        net.minecraft.network.protocol.game.ServerboundSwingPacket.class,
        net.minecraft.network.protocol.game.ServerboundPlayerInputPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos.class
    );

    private AutismConfig config;
    private boolean initialized;
    private boolean loadGuiKeyPressed;
    private boolean flushQueueKeyPressed;
    private boolean clearQueueKeyPressed;
    private boolean toggleLoggerKeyPressed;
    private boolean toggleSendKeyPressed;
    private boolean toggleDelayKeyPressed;
    private boolean moduleMenuKeyPressed;
    private final java.util.Map<String, Boolean> macroKeyStates = new java.util.HashMap<>();
    private List<AutismMacro> cachedKeyboundMacros = List.of();
    private long cachedMacroKeybindRevision = -1L;
    private int autoSendTickCounter;
    private int packetLoggerTickCounter;
    private AutismPacketLoggerOverlay packetLoggerOverlay;
    private autismclient.util.AutismPayloadChannelListeners passivePayloadListeners;
    private volatile boolean payloadListenerCacheValid;
    private volatile boolean payloadListenerEnabledCache;
    private AutismServerInfoOverlay serverInfoOverlay;
    private autismclient.util.AutismMatchmakingOverlay matchmakingOverlay;
    private autismclient.util.AutismProfilesOverlay profilesOverlay;
    private autismclient.util.AutismMultiOverlay multiOverlay;
    private final Deque<PassivePayloadCapture> passivePayloadRing = new ArrayDeque<>(PASSIVE_PAYLOAD_RING_CAP);
    private final Set<Packet<?>> capturedPayloadPackets = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Deque<String> capturedRawPayloadFingerprints = new ArrayDeque<>(PAYLOAD_FINGERPRINT_CAP);
    private final Set<String> capturedRawPayloadFingerprintSet = new HashSet<>();
    private volatile boolean joinedPlayConnection;
    private volatile boolean spawnedInWorld;
    private long lastTickErrorLogMs;

    private int stuckActiveTicks;
    private long lastWatchdogLogMs;
    private static final int ACTIVE_WATCHDOG_TICKS = 20;
    private volatile long passivePayloadCaptureUntilMs;

    private volatile boolean autoProbePending;
    private volatile long autoProbePendingSince;
    private static final long AUTO_PROBE_CMD_GRACE_MS = 2000L;
    private static final long AUTO_PROBE_GIVE_UP_MS = 25000L;

    private AutismModule() {
    }

    public static AutismModule get() {
        return INSTANCE;
    }

    public void initialize() {
        if (initialized) return;

        config = AutismConfig.load();
        initStep("applyRuntimeDefaults", () -> config.applyRuntimeDefaults());
        AutismConfig.setGlobal(config);

        initStep("themeWarm", () -> autismclient.util.AutismTheme.active());
        initStep("moduleRegistry", () -> ModuleRegistry.initialize(config));
        initStep("c2sPackets", () -> {
            if (config.c2sPackets.isEmpty()) config.c2sPackets = encodePackets(defaultC2SPackets());
        });
        initStep("sharedState", this::applyConfigToSharedState);
        initStep("packHide", () -> { if (PackHideState.isActive()) PackHideState.stopRuntimeWork(); });
        initStep("lanSync", () -> {
            if (config.lanSyncEnabled && !PackHideState.isActive()) AutismLANSync.getInstance().start();
        });

        initStep("profiles", () -> autismclient.util.AutismProfileManager.get());

        initialized = true;
        autismclient.util.AutismNetworkCaptureState.refresh(this);
    }

    private void initStep(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Autism] init step '{}' failed; isolated so the mod still activates", where, t);
        }
    }

    public void applyConfig(AutismConfig next) {
        if (next == null) return;
        next.applyRuntimeDefaults();

        AutismConfig prev = this.config != null ? this.config : AutismConfig.getGlobal();
        if (prev != null) {
            next.welcomeShown = prev.welcomeShown;
            next.welcomeInstallIdentity = prev.welcomeInstallIdentity;
            next.serverPluginScans = prev.serverPluginScans;

            next.lanSyncEnabled = prev.lanSyncEnabled;
        }

        boolean themeChanged = !AutismConfig.sameThemeColors(prev, next);
        boolean filtersChanged = !AutismConfig.samePayloadRules(prev, next);

        java.util.Map<String, Boolean> wasEnabled = new java.util.LinkedHashMap<>();
        for (Module m : ModuleRegistry.all()) wasEnabled.put(m.id(), m.isEnabled());
        boolean panicBefore = PackHideState.isActive();
        boolean inWorld = MC != null && MC.level != null;

        this.config = next;
        AutismConfig.setGlobal(next);
        next.save();

        for (Module m : ModuleRegistry.all()) {
            boolean now = m.isEnabled();
            boolean before = Boolean.TRUE.equals(wasEnabled.get(m.id()));
            if (now == before) continue;

            if (inWorld && !(now && PackHideState.blocksEnable(m))) {
                ModuleRegistry.fireEnableTransition(m, now);
            }
        }
        if (!inWorld) ModuleRegistry.clearOfflineDeferrals();

        ModuleRegistry.refreshEnabledModuleSettings();
        ModuleRegistry.markModuleEnabledChanged();

        applyConfigToSharedState();
        invalidatePayloadListenerCache(filtersChanged);
        autismclient.util.AutismNetworkCaptureState.refresh(this);
        autismclient.util.AutismHudManager.ensureDefaults();
        if (themeChanged) autismclient.util.AutismTheme.reload();

        boolean panicNow = PackHideState.isActive();
        if (panicNow && !panicBefore) PackHideState.stopRuntimeWork();

        if (!panicNow) {
            if (next.lanSyncEnabled && !AutismLANSync.getInstance().isRunning()) {
                AutismLANSync.getInstance().start();
            } else if (!next.lanSyncEnabled && AutismLANSync.getInstance().isRunning()) {
                AutismLANSync.getInstance().stop();
            }
        }
    }

    public void tick() {
        if (!initialized || MC == null) return;

        try {
            updateWorldSpawnState();
        } catch (Throwable t) {
            logTickError("worldSpawnState", t);
        }
        try {
            tickWork();
        } catch (Throwable t) {
            logTickError("tickWork", t);
        }
    }

    private void logTickError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastTickErrorLogMs < 5000L) return;
        lastTickErrorLogMs = now;
        AutismClientAddon.LOG.warn("[Autism] tick '{}' failed; isolated to protect the client", where, t);
    }

    private void logWatchdogRecovery() {
        long now = System.currentTimeMillis();
        if (now - lastWatchdogLogMs < 5000L) return;
        lastWatchdogLogMs = now;
        AutismClientAddon.LOG.warn("[Autism] active-state watchdog: a live world was present but joinedPlayConnection "
            + "was false; force-restored isActive() (HUD/overlay would otherwise stay hidden until reconnect)");
    }

    private void tickWork() {
        autismclient.util.AutismNetworkCaptureState.refreshIfDue(this);

        autismclient.util.AutismProfileManager.get().flushMirrorIfDue();

        autismclient.util.macro.PingSpoofController.flushDue();
        AutismBlinkManager.tick();

        autismclient.util.AutismClientWake.tick(MC);

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            autoProbePending = false;
            passivePayloadCaptureUntilMs = 0L;
            AutismPayloadStudySession.stop();
            AutismPayloadFilterNotifier.clear();
            AutismServerInfoOverlay hiddenServerInfo = getServerDataOverlayIfExists();
            if (hiddenServerInfo != null) hiddenServerInfo.stopPluginScanSilently();
        }

        AutismPerf.tickJoinWindow();
        AutismPayloadFilterNotifier.tick();
        if (!hidden) AutismPayloadChannelSubscriptionManager.tick(MC, isPacketLoggerCapturing());
        long perf;
        AutismLANSync lanSync = AutismLANSync.getInstance();
        if (!hidden && lanSync.hasTickWork()) {
            perf = AutismPerf.beginJoin();
            lanSync.tick();
            AutismPerf.endJoinSpike("join.lanSync.tick", perf);
        }
        if (!hidden && MacroConditionRegistry.hasPendingConditions()) {
            perf = AutismPerf.beginJoin();
            MacroConditionRegistry.onTick(MC);
            AutismPerf.endJoinSpike("join.macroConditions.tick", perf);
        }
        AutismSharedState shared = AutismSharedState.get();
        AutismJoinGrace.tick();
        if (!hidden && shared.hasStaggeredSendWork()) shared.tickStaggeredSend();
        if (!hidden
            && autismclient.util.AutismRuntimeActivity.has(autismclient.util.AutismRuntimeActivity.MODULE_TICK)) {
            perf = AutismPerf.beginJoin();
            ModuleRegistry.tick();
            AutismPerf.endJoinSpike("join.modules.tick", perf, 8_000_000L);
        }
        if (!hidden) PackAutoReconnectState.tickCurrentScreen();
        updatePassiveXCarryState();
        AutismServerInfoOverlay serverInfo = getServerDataOverlayIfExists();
        if (!hidden && serverInfo != null && (serverInfo.isVisible() || serverInfo.shouldRenderBackgroundProbeBanner())) {
            serverInfo.tickBackground();
        }

        if (shared.shouldDelayGuiPackets() && MC.getConnection() != null) {
            if (autoSendTickCounter++ >= 1) {
                autoSendTickCounter = 0;
            }
        } else {
            autoSendTickCounter = 0;
        }

        if (AutismInputGate.canRunAutismKeybinds()) {
            tickKeybinds();
        } else {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
        }

        packetLoggerTickCounter++;
        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (logger != null) {
            logger.setGameTick(packetLoggerTickCounter);
        }

        if (!hidden && autismclient.api.event.AddonEvents.hasTickListeners()) {
            autismclient.api.event.AddonEvents.fireTick(MC);
        }
        publishAuxiliaryHudActivity(hidden, shared, serverInfo);
    }

    private void publishAuxiliaryHudActivity(boolean hidden, AutismSharedState shared, AutismServerInfoOverlay serverInfo) {
        boolean active = !hidden && (
            autismclient.util.macro.MacroExecutor.isVisibleRunning()
                || shared.shouldDelayGuiPackets()
                || shared.hasDelayedPackets()
                || shared.hasStaggeredPackets()
                || shared.isCaptureMode()
                || shared.isGBreakCapturing()
                || shared.hasCaptureCancelCallback()
                || shared.hasAttackCaptureCallback()
                || shared.hasBlockCaptureCallback()
                || shared.hasEntityCaptureCallback()
                || AutismPayloadStudySession.isActive()
                || ModuleRenderUtil.has2dEspWork()
                || AntiVanishModule.shouldShowHud()
                || AutismNotifications.hasVisible()
                || serverInfo != null && serverInfo.shouldRenderBackgroundProbeBanner()
        );
        autismclient.util.AutismRuntimeActivity.publish(autismclient.util.AutismRuntimeActivity.HUD_AUX, active);
    }

    public void onGameJoin() {
        autismclient.util.SodiumTerrainPassGuard.armForTransition();
        AutismPerf.beginJoinWindow();
        long perf = AutismPerf.beginJoin();
        joinedPlayConnection = true;
        spawnedInWorld = false;
        if (PackHideState.isActive()) {
            autoProbePending = false;
            passivePayloadCaptureUntilMs = 0L;
        } else {
            autoProbePending = config != null && config.autoProbePlugins;
            if (autoProbePending) autoProbePendingSince = System.currentTimeMillis();
            beginPassivePayloadCapture();
            AutismPayloadChannelSubscriptionManager.requestRefresh();
        }
        applyRuntimePacketFlowDefaults();
        if (PackHideState.isActive()) PackHideState.stopRuntimeWork();

        if (!PackHideState.isActive() && config != null && config.packetLoggerCapturing) {
            getPacketLoggerOverlay();
        }

        if (config.lanSyncEnabled && !PackHideState.isActive() && !AutismLANSync.getInstance().isRunning()) {
            AutismLANSync.getInstance().start();
        }

        if (!PackHideState.isActive()) AutismLANSync.getInstance().onGameJoined();
        if (!PackHideState.isActive()) ModuleRegistry.onGameJoin();
        if (!PackHideState.isActive()) autismclient.api.event.AddonEvents.fireGameJoin();
        AutismPerf.endJoinSpike("join.onGameJoin", perf, 8_000_000L);
    }

    public void onGameLeft() {
        autismclient.util.SodiumTerrainPassGuard.armForTransition();
        joinedPlayConnection = false;
        spawnedInWorld = false;
        autoProbePending = false;
        autismclient.util.AutismPluginPayloadFingerprints.clearSession();
        AutismPayloadFilterNotifier.clear();
        AutismPayloadChannelSubscriptionManager.clear();
        AutismServerInfoOverlay leftOverlay = getServerDataOverlayIfExists();
        if (leftOverlay != null) leftOverlay.onConnectionClosed();
        passivePayloadCaptureUntilMs = 0L;
        autismclient.util.AutismNetworkCaptureState.refresh(this);
        AutismSharedState.get().clearRealServerVersion();
        loadGuiKeyPressed = false;
        flushQueueKeyPressed = false;
        clearQueueKeyPressed = false;
        toggleLoggerKeyPressed = false;
        toggleSendKeyPressed = false;
        toggleDelayKeyPressed = false;
        moduleMenuKeyPressed = false;
        if (!PackHideState.isActive()) ModuleRegistry.onGameLeft();
        if (!PackHideState.isActive()) autismclient.api.event.AddonEvents.fireGameLeft();
    }

    public boolean isActive() {
        return initialized && spawnedInWorld && isPlayerSpawnedInWorld();
    }

    public boolean isUsable() {
        return initialized && !PackHideState.isActive();
    }

    public boolean arePacketHooksActive() {
        if (!isActive() || PackHideState.isActive()) return false;
        AutismSharedState shared = AutismSharedState.get();
        return shared.hasPacketFlowWork()
            || isPacketLoggerCapturing()
            || isServerInfoPacketObservationActive()
            || ModuleRegistry.hasActivePacketEventModules()
            || autismclient.api.event.AddonEvents.hasPacketListeners()
            || autismclient.util.macro.PacketGateManager.hasActiveGates()
            || autismclient.util.macro.MacroExecutor.hasPacketObservationWork();
    }

    public PacketHookSnapshot packetHookSnapshot(boolean playConnection) {
        if (PackHideState.isActive()) return PacketHookSnapshot.inactive();
        long captureState = autismclient.util.AutismNetworkCaptureState.state();
        boolean loggerCapture = autismclient.util.AutismNetworkCaptureState.capturesPlaintext(captureState);
        boolean passivePayloadCapture = autismclient.util.AutismNetworkCaptureState.capturesPayloads(captureState);
        if (!isActive()) {

            return (loggerCapture || passivePayloadCapture)
                ? new PacketHookSnapshot(false, passivePayloadCapture, loggerCapture, false)
                : PacketHookSnapshot.inactive();
        }
        AutismSharedState shared = AutismSharedState.get();

        boolean serverInfoObservation = passivePayloadCapture && isServerInfoPacketObservationActive();
        boolean normalPath = playConnection && (shared.hasPacketFlowWork()
            || loggerCapture
            || serverInfoObservation
            || ModuleRegistry.hasActivePacketEventModules()
            || autismclient.api.event.AddonEvents.hasPacketListeners()
            || autismclient.util.macro.PacketGateManager.hasActiveGates()
            || autismclient.util.macro.MacroExecutor.hasPacketObservationWork());
        if (!normalPath && !passivePayloadCapture && !loggerCapture && !serverInfoObservation) {
            return PacketHookSnapshot.inactive();
        }
        return new PacketHookSnapshot(normalPath, passivePayloadCapture, loggerCapture, serverInfoObservation);
    }

    public boolean hasPassivePayloadCaptureWork() {
        return !PackHideState.isActive() && (isPassivePayloadCaptureActive() || payloadListenersEnabledCached() || AutismPayloadStudySession.isActive());
    }

    public boolean shouldCapturePacketPlaintext() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing();
    }

    public boolean shouldCapturePayloadBytes() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing() || hasPassivePayloadCaptureWork() || isServerInfoPacketObservationActive() || AutismPayloadStudySession.isActive();
    }

    private boolean isServerInfoPacketObservationActive() {
        if (config != null && config.autoProbePlugins && autoProbePending) return true;
        AutismServerInfoOverlay overlay = getServerDataOverlayIfExists();
        return overlay != null && overlay.isPacketObservationActive();
    }

    public boolean hasPluginDiscoveryObservationWork() {
        if (!isActive() || PackHideState.isActive()) return false;
        return isServerInfoPacketObservationActive();
    }

    private AutismServerInfoOverlay getPluginDiscoveryOverlay() {
        AutismServerInfoOverlay overlay = getServerDataOverlayIfExists();
        if (overlay != null) return overlay;
        if (config != null && config.autoProbePlugins && autoProbePending) {
            return getServerDataOverlay();
        }
        return null;
    }

    public void observePluginDiscoveryPacketSend(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        AutismServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        overlay.onCommandSuggestionRequest(packet);
        overlay.onOutgoingCommandPacket(packet);
    }

    public void observePluginDiscoveryPacketReceive(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        AutismServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket suggestions) {
            overlay.onCommandSuggestions(suggestions.id(), suggestions);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket) {
            overlay.onOpenScreenPacket(packet);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandsPacket) {
            if (spawnedInWorld && !autoProbePending && config != null && config.autoProbePlugins) {
                autoProbePending = true;
                autoProbePendingSince = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldObservePluginPayloadFingerprints() {
        return isPassivePayloadCaptureActive() || isServerInfoPacketObservationActive() || isPacketLoggerCapturing() || AutismPayloadStudySession.isActive();
    }

    public void invalidatePayloadListenerCache() {
        invalidatePayloadListenerCache(false);
    }

    public void invalidatePayloadListenerCache(boolean refreshSubscriptions) {
        payloadListenerCacheValid = false;
        if (passivePayloadListeners != null) passivePayloadListeners.load();
        if (refreshSubscriptions) AutismPayloadChannelSubscriptionManager.requestRefresh();
        autismclient.util.AutismNetworkCaptureState.refresh(this);
    }

    private boolean payloadListenersEnabledCached() {
        if (payloadListenerCacheValid) return payloadListenerEnabledCache;
        boolean enabled = computePayloadListenersEnabled();
        payloadListenerEnabledCache = enabled;
        payloadListenerCacheValid = true;
        return enabled;
    }

    private boolean computePayloadListenersEnabled() {
        AutismConfig cfg = AutismConfig.getGlobal();
        if (cfg == null || cfg.packetLoggerPayloadFilters == null) return false;
        for (AutismConfig.PayloadChannelFilterRule rule : cfg.packetLoggerPayloadFilters) {
            if (rule != null && rule.enabled) return true;
        }
        return false;
    }

    public void onConfigurationConnectionStarted() {
        beginPassivePayloadCapture();
        AutismPayloadChannelSubscriptionManager.requestRefresh();
    }

    public boolean isPassivePayloadCaptureActive() {
        return System.currentTimeMillis() <= passivePayloadCaptureUntilMs;
    }

    public long passivePayloadCaptureDeadlineMs() {
        return passivePayloadCaptureUntilMs;
    }

    public boolean isPacketLoggerCapturing() {
        if (PackHideState.isActive()) return false;
        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (logger != null) return !logger.isPaused();
        return config != null && config.packetLoggerCapturing;
    }

    public boolean capturePayloadPacketForLogger(Packet<?> packet, String direction, String protocolPhase) {
        return capturePayloadPacket(packet, direction, protocolPhase, "connection");
    }

    public boolean captureDecodedPayloadPacket(Packet<?> packet, String direction, String protocolPhase, String source) {
        return capturePayloadPacket(packet, direction, protocolPhase, source == null || source.isBlank() ? "codec" : source);
    }

    public boolean captureRawPayloadFrame(byte[] frameBytes, String direction, String protocolPhase, String source) {
        if (PackHideState.isActive() || frameBytes == null || frameBytes.length == 0) return false;
        autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot =
            autismclient.util.AutismPayloadSupport.snapshotFromEncodedPacketFrame(frameBytes, direction, protocolPhase);
        if (snapshot == null) return false;
        String fingerprint = rawPayloadFingerprint(snapshot);
        if (isRawPayloadCaptured(fingerprint)) return true;

        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(snapshot);
        }

        boolean loggerCapture = isPacketLoggerCapturing();
        boolean passiveWork = hasPassivePayloadCaptureWork();
        if (!loggerCapture && !passiveWork) return false;

        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        boolean captured = false;
        if (loggerCapture) {
            if (logger == null) {
                rememberPassivePayload(snapshot, direction, rawPayloadPacketClass(direction));
            } else {
                logger.logPayloadSnapshotSilently(System.currentTimeMillis(), packetLoggerTickCounter,
                    direction == null ? "" : direction, rawPayloadPacketClass(direction), snapshot);
            }
            captured = true;
        } else if (passiveWork) {
            autismclient.util.AutismPayloadChannelListeners.Match filterMatch = matchEnabledPayloadFilter(snapshot.channel(), direction);
            if (filterMatch != null) {
                if (logger != null) {
                    logger.logPayloadSnapshotSilently(System.currentTimeMillis(), packetLoggerTickCounter,
                        direction == null ? "" : direction, rawPayloadPacketClass(direction), snapshot);
                } else {
                    AutismPayloadFilterNotifier.onMatch(snapshot.channel(), direction, filterMatch);
                    rememberPassivePayload(snapshot, direction, rawPayloadPacketClass(direction));
                }
                captured = true;
            }
        }

        if (captured) {
            markRawPayloadCaptured(fingerprint);
            traceRawPayloadCapture(snapshot, source);
        }
        return captured;
    }

    private boolean capturePayloadPacket(Packet<?> packet, String direction, String protocolPhase, String source) {
        if (PackHideState.isActive() || packet == null) return false;
        if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
            boolean captured = false;
            for (Packet<?> child : bundledPackets(bundle)) {
                if (capturePayloadPacket(child, direction, protocolPhase, source)) captured = true;
            }
            return captured;
        }

        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            autismclient.util.AutismPayloadSupport.extractPayload(packet);
        if (payload == null) return false;

        if (isPayloadPacketCaptured(packet)) return true;

        String channel = autismclient.util.AutismPayloadSupport.payloadChannel(payload);
        autismclient.util.AutismPayloadSupport.rememberPayloadProtocol(packet, protocolPhase);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, direction);
        }

        boolean loggerCapture = isPacketLoggerCapturing();
        boolean passiveWork = hasPassivePayloadCaptureWork();
        if (!loggerCapture && !passiveWork) return false;

        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        boolean captured = false;
        if (loggerCapture) {
            if (logger == null) {
                rememberPassivePayload(packet, direction);
            } else {
                logger.logPayloadPacketSilently(packet, direction);
            }
            captured = true;
        } else if (passiveWork) {
            autismclient.util.AutismPayloadChannelListeners.Match filterMatch = matchEnabledPayloadFilter(channel, direction);
            if (filterMatch != null) {
                if (logger != null) {
                    logger.logPayloadPacketSilently(packet, direction);
                } else {
                    AutismPayloadFilterNotifier.onMatch(channel, direction, filterMatch);
                    rememberPassivePayload(packet, direction);
                }
                captured = true;
            }
        }

        if (captured) {
            markPayloadPacketCaptured(packet);
            tracePayloadCapture(packet, channel, direction, protocolPhase, source);
        }
        return captured;
    }

    private boolean isPayloadPacketCaptured(Packet<?> packet) {
        synchronized (capturedPayloadPackets) {
            return capturedPayloadPackets.contains(packet);
        }
    }

    private void markPayloadPacketCaptured(Packet<?> packet) {
        if (packet == null) return;
        synchronized (capturedPayloadPackets) {
            capturedPayloadPackets.add(packet);
        }
    }

    private void tracePayloadCapture(Packet<?> packet, String channel, String direction, String protocolPhase, String source) {
        if (!PAYLOAD_TRACE) return;
        int size = -1;
        try {
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
                autismclient.util.AutismPayloadSupport.extractPayload(packet);
            size = autismclient.util.AutismPayloadSupport.extractPayloadBytes(payload).length;
        } catch (Throwable ignored) {  }
        AutismClientAddon.LOG.info("[Autism Payload] {} {} {} {}B via {}",
            direction == null ? "" : direction,
            protocolPhase == null ? "" : protocolPhase,
            channel == null || channel.isBlank() ? "<unknown>" : channel,
            size,
            source == null ? "" : source);
    }

    private void traceRawPayloadCapture(autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot, String source) {
        if (!PAYLOAD_TRACE || snapshot == null) return;
        AutismClientAddon.LOG.info("[Autism Payload] {} {} {} {}B via {}",
            snapshot.direction() == null ? "" : snapshot.direction(),
            snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase(),
            snapshot.channel() == null || snapshot.channel().isBlank() ? "<unknown>" : snapshot.channel(),
            snapshot.sizeBytes(),
            source == null ? "" : source);
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction) {
        capturePassivePayloadPacket(packet, direction, "");
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction, String protocolPhase) {
        if (PackHideState.isActive()) return;
        if (!hasPassivePayloadCaptureWork()) return;
        capturePayloadPacket(packet, direction, protocolPhase, "passive");
    }

    private void rememberPassivePayload(Packet<?> packet, String direction) {
        long perf = AutismPerf.beginJoin();
        autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot =
            autismclient.util.AutismPayloadSupport.snapshot(packet, direction);
        if (snapshot == null) return;
        rememberPassivePayload(snapshot, direction, packet.getClass());
        AutismPerf.endJoinSpike("join.passivePayload.snapshot", perf);
    }

    private void rememberPassivePayload(autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot, String direction, Class<?> packetClass) {
        if (snapshot == null) return;
        synchronized (passivePayloadRing) {
            while (passivePayloadRing.size() >= PASSIVE_PAYLOAD_RING_CAP) {
                passivePayloadRing.removeFirst();
            }
            passivePayloadRing.addLast(new PassivePayloadCapture(
                System.currentTimeMillis(),
                packetLoggerTickCounter,
                direction == null ? "" : direction,
                packetClass,
                snapshot
            ));
        }
    }

    private autismclient.util.AutismPayloadChannelListeners.Match matchEnabledPayloadFilter(String channel, String direction) {
        if (channel == null || channel.isBlank()) return null;
        autismclient.util.AutismPayloadChannelListeners listeners = getPassivePayloadListeners();
        return listeners.hasEnabledRules() ? listeners.matchChannel(channel, direction) : null;
    }

    private autismclient.util.AutismPayloadChannelListeners getPassivePayloadListeners() {
        if (passivePayloadListeners == null) {
            passivePayloadListeners = new autismclient.util.AutismPayloadChannelListeners();
        }
        return passivePayloadListeners;
    }

    @SuppressWarnings("unchecked")
    private static List<Packet<?>> bundledPackets(net.minecraft.network.protocol.BundlePacket<?> bundle) {
        if (bundle == null) return List.of();
        List<Packet<?>> packets = new ArrayList<>();
        try {
            for (Object child : bundle.subPackets()) {
                if (child instanceof Packet<?> packet) packets.add(packet);
            }
        } catch (Throwable ignored) {  }
        return packets;
    }

    private static Class<?> rawPayloadPacketClass(String direction) {
        return direction != null && direction.equalsIgnoreCase("C2S")
            ? net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.class
            : net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class;
    }

    private String rawPayloadFingerprint(autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return "";
        return (snapshot.direction() == null ? "" : snapshot.direction()) + '|'
            + (snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase()) + '|'
            + snapshot.packetId() + '|'
            + (snapshot.channel() == null ? "" : snapshot.channel()) + '|'
            + snapshot.sizeBytes() + '|'
            + Arrays.hashCode(snapshot.rawBytes());
    }

    private boolean isRawPayloadCaptured(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return false;
        synchronized (capturedRawPayloadFingerprintSet) {
            return capturedRawPayloadFingerprintSet.contains(fingerprint);
        }
    }

    private void markRawPayloadCaptured(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return;
        synchronized (capturedRawPayloadFingerprintSet) {
            if (!capturedRawPayloadFingerprintSet.add(fingerprint)) return;
            capturedRawPayloadFingerprints.addLast(fingerprint);
            while (capturedRawPayloadFingerprints.size() > PAYLOAD_FINGERPRINT_CAP) {
                String removed = capturedRawPayloadFingerprints.removeFirst();
                capturedRawPayloadFingerprintSet.remove(removed);
            }
        }
    }

    public void toggle() {
        AutismClientMessaging.sendPrefixed("Autism is always enabled in standalone mode.");
    }

    private void beginPassivePayloadCapture() {
        if (PackHideState.isActive()) return;
        passivePayloadCaptureUntilMs = Math.max(passivePayloadCaptureUntilMs, System.currentTimeMillis() + PASSIVE_PAYLOAD_CAPTURE_MS);
        autismclient.util.AutismNetworkCaptureState.refresh(this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void appendTooltip(ItemStack stack, List<?> lines) {
        ModuleRegistry.appendTooltip(stack, lines);
    }

    public boolean handlePacketSend(Packet<?> packet) {
        return handlePacketSend(packet, false);
    }

    public boolean handlePacketSend(Packet<?> packet, boolean payloadLoggedEarly) {
        long perf = AutismPerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketSend(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "C2S");
        }
        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (ModuleRegistry.onPacketSend(packet)) return true;
        if (autismclient.api.event.AddonEvents.firePacketSend(packet)) return true;
        if (logger == null) return false;
        if (!payloadLoggedEarly && !logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "C2S");
        }
        return false;
        } finally {
            AutismPerf.endJoinSpike("join.packetSendHook", perf);
        }
    }

    public boolean handlePacketReceive(Packet<?> packet) {
        return handlePacketReceive(packet, false);
    }

    public boolean handlePacketReceive(Packet<?> packet, boolean payloadLoggedEarly) {
        long perf = AutismPerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketReceive(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "S2C");
        }

        AutismPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (ModuleRegistry.onPacketReceive(packet)) return true;
        autismclient.api.event.AddonEvents.firePacketReceive(packet);
        if (logger == null) return false;
        if (!payloadLoggedEarly && !logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "S2C");
        }
        return false;
        } finally {
            AutismPerf.endJoinSpike("join.packetReceiveHook", perf);
        }
    }

    private void observePluginPayloadFingerprint(Packet<?> packet, String direction) {
        if (packet == null || !AutismPacketLoggerOverlay.isPayloadPacket(packet)) return;
        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            autismclient.util.AutismPayloadSupport.extractPayload(packet);
        if (payload == null) return;
        String channel = autismclient.util.AutismPayloadSupport.payloadChannel(payload);
        boolean inbound = "S2C".equalsIgnoreCase(direction);
        if (inbound) {
            autismclient.util.AutismPayloadChannelSubscriptionManager.rememberObservedChannel(channel);
        }
        try {
            autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot =
                autismclient.util.AutismPayloadSupport.snapshot(packet, direction);
            AutismPayloadStudySession.recordPayload(snapshot);
            if (inbound) {
                autismclient.util.AutismPayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
            }
        } catch (Throwable ignored) {  }
        if (!inbound || !autismclient.util.AutismPluginPayloadFingerprints.shouldObserveChannel(channel)) return;

        try {
            autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot =
                autismclient.util.AutismPayloadSupport.snapshot(packet, direction);
            if (snapshot == null) return;
            boolean changed = autismclient.util.AutismPluginPayloadFingerprints.observe(
                currentPayloadFingerprintServerAddress(),
                currentPayloadFingerprintBrand(),
                snapshot
            );
            if (changed) {
                AutismServerInfoOverlay overlay = getServerDataOverlayIfExists();
                if (overlay != null) overlay.onPayloadFingerprintUpdated();
            }
        } catch (Throwable ignored) {  }
    }

    private void observePluginPayloadFingerprint(autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return;
        String channel = snapshot.channel();
        AutismPayloadStudySession.recordPayload(snapshot);
        boolean inbound = "S2C".equalsIgnoreCase(snapshot.direction());
        if (inbound) {
            autismclient.util.AutismPayloadChannelSubscriptionManager.rememberObservedChannel(channel);
            autismclient.util.AutismPayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
        }
        if (!inbound || !autismclient.util.AutismPluginPayloadFingerprints.shouldObserveChannel(channel)) return;

        try {
            boolean changed = autismclient.util.AutismPluginPayloadFingerprints.observe(
                currentPayloadFingerprintServerAddress(),
                currentPayloadFingerprintBrand(),
                snapshot
            );
            if (changed) {
                AutismServerInfoOverlay overlay = getServerDataOverlayIfExists();
                if (overlay != null) overlay.onPayloadFingerprintUpdated();
            }
        } catch (Throwable ignored) {  }
    }

    private String currentPayloadFingerprintBrand() {
        if (MC == null || MC.getConnection() == null) return "";
        String brand = MC.getConnection().serverBrand();
        return brand == null ? "" : brand;
    }

    private String currentPayloadFingerprintServerAddress() {
        if (MC == null) return "";
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return (host + ":" + inet.getPort()).trim().toLowerCase(java.util.Locale.ROOT);
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim().toLowerCase(java.util.Locale.ROOT);
                }
            }
        }
        return "";
    }

    public AutismPacketLoggerOverlay getPacketLoggerOverlay() {
        if (packetLoggerOverlay == null && MC != null && MC.font != null) {
            packetLoggerOverlay = new AutismPacketLoggerOverlay(MC.font);
            packetLoggerOverlay.restoreState();
            hydratePassivePayloads(packetLoggerOverlay);
        }
        return packetLoggerOverlay;
    }

    public AutismPacketLoggerOverlay getPacketLoggerOverlayIfExists() {
        return packetLoggerOverlay;
    }

    private void hydratePassivePayloads(AutismPacketLoggerOverlay logger) {
        if (logger == null) return;
        List<PassivePayloadCapture> captures;
        synchronized (passivePayloadRing) {
            if (passivePayloadRing.isEmpty()) return;
            captures = new ArrayList<>(passivePayloadRing);
            passivePayloadRing.clear();
        }
        for (PassivePayloadCapture capture : captures) {
            logger.logPayloadSnapshotSilently(
                capture.timestampMs(),
                capture.gameTick(),
                capture.direction(),
                capture.packetClass(),
                capture.snapshot()
            );
        }
    }

    private record PassivePayloadCapture(long timestampMs, int gameTick, String direction, Class<?> packetClass,
                                         autismclient.util.AutismPayloadSupport.PayloadSnapshot snapshot) {
    }

    public record PacketHookSnapshot(
        boolean normalPath,
        boolean passivePayloadCapture,
        boolean packetLoggerCapturing,
        boolean pluginDiscoveryObservation
    ) {
        private static final PacketHookSnapshot INACTIVE = new PacketHookSnapshot(false, false, false, false);

        public static PacketHookSnapshot inactive() {
            return INACTIVE;
        }
    }

    public AutismServerInfoOverlay getServerDataOverlay() {
        if (serverInfoOverlay == null && MC != null && MC.font != null) {
            serverInfoOverlay = new AutismServerInfoOverlay(MC.font);
            serverInfoOverlay.restoreState();
        }
        return serverInfoOverlay;
    }

    public AutismServerInfoOverlay getServerDataOverlayIfExists() {
        return serverInfoOverlay;
    }

    public boolean isNoPauseOnLostFocus() {
        return config.noPauseOnLostFocus;
    }

    public boolean isLANSyncEnabled() {
        return config.lanSyncEnabled;
    }

    public boolean isBypassResourcePack() {
        return config != null ? config.pretendPackAccepted : AutismConfig.getGlobal().pretendPackAccepted;
    }

    public boolean isSpoofClientVanilla() {
        return config != null ? config.spoofClientVanilla : AutismConfig.getGlobal().spoofClientVanilla;
    }

    public boolean isInventoryMoveEnabled() {
        Module module = ModuleRegistry.get("inv-move");
        return module != null ? module.isEnabled() : config != null && config.inventoryMove;
    }

    public void setInventoryMoveEnabled(boolean value) {
        if (config == null) return;
        config.inventoryMove = value;
        Module module = ModuleRegistry.get("inv-move");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public boolean isXCarryEnabled() {
        Module module = xcarryModule();
        return module != null ? module.isEnabled() : config != null && config.xCarry;
    }

    public void setXCarryEnabled(boolean value) {
        if (config == null) return;
        config.xCarry = value;
        Module module = ModuleRegistry.get("xcarry");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public void setBypassResourcePack(boolean value) {
        config.pretendPackAccepted = value;
        AutismSharedState.get().setBypassResourcePack(value);
        saveConfig();
    }

    public void setSpoofClientVanilla(boolean value) {
        if (config == null) return;
        config.spoofClientVanilla = value;
        saveConfig();
    }

    public boolean isForceDenyResourcePack() {
        return config != null ? config.autoDenyResourcePack : AutismConfig.getGlobal().autoDenyResourcePack;
    }

    public void setForceDenyResourcePack(boolean value) {
        config.autoDenyResourcePack = value;
        AutismSharedState.get().setResourcePackForceDeny(value);
        saveConfig();
    }

    public boolean useMsSleepMode() {
        return config.useMsSleepMode;
    }

    public int getMsSleepInterval() {
        return config.msSleepInterval;
    }

    public boolean useInstantExecutionMode() {
        return config.instantExecutionMode;
    }

    public int getActionDelayUs() {
        return config.actionDelayUs;
    }

    public boolean usePacketBurstMode() {
        return config.packetBurstMode;
    }

    public boolean shouldUseDirectFlush() {
        return config.useDirectFlush;
    }

    public boolean shouldForceChannelFlush() {
        return config.forceChannelFlush;
    }

    public boolean shouldFlushQueueOnDelayDisable() {
        return config != null && config.flushQueueOnDelayDisable;
    }

    public void setFlushQueueOnDelayDisable(boolean value) {
        if (config == null) return;
        config.flushQueueOnDelayDisable = value;
        saveConfig();
    }

    public boolean isCaptureAsExact() {
        return config != null && config.captureAsExact;
    }

    public void setCaptureAsExact(boolean value) {
        if (config == null) return;
        config.captureAsExact = value;
        saveConfig();
    }

    public boolean shouldUseCustomPackets() {
        return config.useCustomPackets;
    }

    public void setUseCustomPackets(boolean value) {
        config.useCustomPackets = value;
        AutismSharedState.get().setUseCustomPackets(value);
        saveConfig();
    }

    public void setSendGuiPackets(boolean value) {
        config.sendGuiPackets = value;
        AutismSharedState.get().setSendGuiPackets(value);
    }

    public boolean applySendGuiPacketsUiBehavior(boolean value) {
        setSendGuiPackets(value);
        saveConfig();
        return value;
    }

    public void setDelayGuiPackets(boolean value) {
        config.delayGuiPackets = value;
        AutismSharedState.get().setDelayGuiPackets(value);
    }

    public String getCommandPrefix() {
        return config == null ? "" : (config.commandPrefix == null ? "" : config.commandPrefix);
    }

    public void setCommandPrefix(String prefix) {
        if (config == null) return;
        config.commandPrefix = autismclient.util.AutismCompatManager.normalizeStoredCommandPrefix(prefix);
        saveConfig();
    }

    public java.util.Map<Integer, String> getCommandBinds() {
        if (config == null) return java.util.Collections.emptyMap();
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        return config.commandBinds;
    }

    public boolean hasCommandBinds() {
        return config != null && config.commandBinds != null && !config.commandBinds.isEmpty();
    }

    public void setCommandBind(int key, String command) {
        if (config == null) return;
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        if (command == null || command.isBlank()) config.commandBinds.remove(key);
        else config.commandBinds.put(key, command);
        saveConfig();
    }

    public void clearCommandBind(int key) {
        if (config == null || config.commandBinds == null) return;
        config.commandBinds.remove(key);
        saveConfig();
    }

    private autismclient.modules.Module xcarryCached;
    private int xcarryRevision = -1;

    private autismclient.modules.Module xcarryModule() {
        int revision = autismclient.modules.ModuleRegistry.revision();
        if (revision != xcarryRevision) {
            xcarryCached = autismclient.modules.ModuleRegistry.get("xcarry");
            xcarryRevision = revision;
        }
        return xcarryCached;
    }
    public boolean isXCarryUseCrafting() {
        autismclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-crafting");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseArmor() {
        autismclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-armor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseOffhand() {
        autismclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-offhand");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryUseCrafting(boolean v) {
        autismclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-crafting", Boolean.toString(v));
    }
    public void setXCarryUseArmor(boolean v) {
        autismclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-armor", Boolean.toString(v));
    }
    public void setXCarryUseOffhand(boolean v) {
        autismclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-offhand", Boolean.toString(v));
    }
    public boolean isXCarryCarryCursor() {
        autismclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("carry-cursor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryCarryCursor(boolean v) {
        autismclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("carry-cursor", Boolean.toString(v));
    }

    public java.util.Set<Integer> getXCarryModuleSlotMask() {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        if (isXCarryUseCrafting()) { s.add(1); s.add(2); s.add(3); s.add(4); s.add(0); }
        if (isXCarryUseArmor())    { s.add(5); s.add(6); s.add(7); s.add(8); }
        if (isXCarryUseOffhand())  { s.add(45); }
        if (s.isEmpty()) { s.add(1); s.add(2); s.add(3); s.add(4); s.add(0); }
        return s;
    }

    public int applyDelayGuiPacketsUiBehavior(boolean value) {
        setDelayGuiPackets(value);
        saveConfig();

        if (!value && shouldFlushQueueOnDelayDisable() && MC != null && MC.getConnection() != null) {
            return AutismSharedState.get().flushDelayedPackets(MC.getConnection());
        }

        return 0;
    }

    public int flushQueuedPacketsUiBehavior() {
        if (MC == null || MC.getConnection() == null) return 0;
        return AutismSharedState.get().flushDelayedPackets(MC.getConnection());
    }

    public int clearQueuedPacketsUiBehavior() {
        AutismSharedState shared = AutismSharedState.get();
        int count = shared.clearQueuedPackets();
        return count;
    }

    public void notifyDelayPacketsUiResult(boolean enabled, int flushed) {
        AutismSharedState shared = AutismSharedState.get();
        if (enabled) {
            AutismNotifications.show("Delay Packets on", 0xFF35D873);
            return;
        }

        if (flushed > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + flushed + " packet" + (flushed == 1 ? "" : "s") + ".");
                AutismNotifications.show("Delay Packets off - sending " + flushed, 0xFFFF5B5B);
            } else {
                AutismNotifications.show("Delay Packets off - sent " + flushed, 0xFFFF5B5B);
            }
            return;
        }

        if (!shouldFlushQueueOnDelayDisable()
            && (!shared.getDelayedPackets().isEmpty() || !shared.getStaggeredQueue().isEmpty())) {
            AutismNotifications.show("Delay Packets off - queue kept", 0xFFFF5B5B);
        } else {

            AutismNotifications.show("Delay Packets off", 0xFFFF5B5B);
        }
    }

    public void notifyFlushQueuedPacketsUiResult(int count) {
        AutismSharedState shared = AutismSharedState.get();
        if (count > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + count + " packet" + (count == 1 ? "" : "s") + ".");
                AutismNotifications.show("Sending " + count + " packet" + (count == 1 ? "" : "s"), 0xFFFFC857);
            } else {
                AutismNotifications.show("Sent " + count + " packet" + (count == 1 ? "" : "s"), 0xFF35D873);
            }
        } else {
            AutismNotifications.show("Queue empty", 0xFFFF5B5B);
        }
    }

    public void notifyClearQueuedPacketsUiResult(int count) {
        AutismNotifications.show(
            count > 0 ? "Cleared " + count + " packet" + (count == 1 ? "" : "s") : "Queue empty",
            count > 0 ? 0xFFFFC857 : 0xFFFF5B5B
        );
    }

    public boolean togglePacketLoggerUiBehavior() {
        AutismPacketLoggerOverlay overlay = getPacketLoggerOverlay();
        if (overlay == null) return false;
        AutismOverlayManager.get().register(overlay);
        overlay.toggle();
        return true;
    }

    public autismclient.util.AutismMatchmakingOverlay getMatchmakingOverlay() {
        if (matchmakingOverlay == null && MC != null && MC.font != null) {
            matchmakingOverlay = new autismclient.util.AutismMatchmakingOverlay(MC.font);
            matchmakingOverlay.restoreLayout();
        }
        return matchmakingOverlay;
    }

    public boolean toggleMatchmakingUiBehavior() {
        autismclient.util.AutismMatchmakingOverlay overlay = getMatchmakingOverlay();
        if (overlay == null) return false;
        if (overlay.isVisible()) { overlay.setVisible(false); return true; }
        AutismOverlayManager.get().register(overlay);
        overlay.openInGameInteractive();
        AutismOverlayManager.get().bringToFront(overlay);

        if (MC != null) MC.gui.setScreen(new autismclient.gui.screen.AutismOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public autismclient.util.AutismProfilesOverlay getProfilesOverlay() {
        if (profilesOverlay == null && MC != null && MC.font != null) {
            profilesOverlay = new autismclient.util.AutismProfilesOverlay(MC.font);
            profilesOverlay.restoreLayout();
        }
        return profilesOverlay;
    }

    public autismclient.util.AutismMultiOverlay getMultiOverlay() {
        if (multiOverlay == null && MC != null && MC.font != null) {
            multiOverlay = new autismclient.util.AutismMultiOverlay(MC.font);
            multiOverlay.restoreLayout();
        }
        return multiOverlay;
    }

    public autismclient.util.AutismMatchmakingOverlay getMatchmakingOverlayIfExists() { return matchmakingOverlay; }
    public autismclient.util.AutismProfilesOverlay getProfilesOverlayIfExists() { return profilesOverlay; }
    public autismclient.util.AutismMultiOverlay getMultiOverlayIfExists() { return multiOverlay; }

    public void hideMenuOverlays() {
        try { if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) matchmakingOverlay.setVisible(false); } catch (Throwable ignored) {  }
        try { if (profilesOverlay != null && profilesOverlay.isVisible()) profilesOverlay.setVisible(false); } catch (Throwable ignored) {  }

        try {
            autismclient.util.multi.MultiManager manager = autismclient.util.multi.MultiManager.getIfInitialized();
            if ((manager == null || !manager.isActive())
                && multiOverlay != null && multiOverlay.isVisible()) {
                multiOverlay.setVisible(false);
            }
        } catch (Throwable ignored) {  }
    }

    public boolean toggleMultiUiBehavior() {
        autismclient.util.AutismMultiOverlay overlay = getMultiOverlay();
        if (overlay == null) return false;
        AutismOverlayManager overlayManager = AutismOverlayManager.get();
        if (overlay.isVisible() && !overlayManager.isTemporarilyHidden(overlay)) {
            overlay.setVisible(false);
            return true;
        }

        autismclient.gui.screen.AutismMultiDisclaimerScreen.open(MC, MC == null ? null : MC.gui.screen(),
            this::showMultiOverlay);
        return true;
    }

    private void showMultiOverlay() {
        autismclient.util.AutismMultiOverlay overlay = getMultiOverlay();
        if (overlay == null) return;
        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.register(overlay);
        manager.setTemporarilyHidden(overlay, false);
        overlay.openInGameInteractive();
        manager.bringToFront(overlay);
    }

    public boolean openMultiUiInGame() {
        autismclient.util.AutismMultiOverlay overlay = getMultiOverlay();
        if (overlay == null) return false;
        showMultiOverlay();
        if (MC != null) MC.gui.setScreen(new autismclient.gui.screen.AutismOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public boolean toggleProfilesUiBehavior() {
        autismclient.util.AutismProfilesOverlay overlay = getProfilesOverlay();
        if (overlay == null) return false;
        if (overlay.isVisible()) { overlay.setVisible(false); return true; }
        AutismOverlayManager.get().register(overlay);
        overlay.openInGameInteractive();
        AutismOverlayManager.get().bringToFront(overlay);

        if (MC != null) MC.gui.setScreen(new autismclient.gui.screen.AutismOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public boolean restoreSavedScreenUiBehavior() {
        AutismSharedState shared = AutismSharedState.get();
        if (MC == null) return false;
        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            return false;
        }

        MC.execute(() -> {
            MC.gui.setScreen(shared.getStoredScreen());
            AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
            if (MC.player != null) MC.player.containerMenu = handler;
        });
        return true;
    }

    public Set<Class<? extends Packet<?>>> getC2SPackets() {
        return new LinkedHashSet<>(AutismSharedState.get().getC2SPackets());
    }

    public Set<Class<? extends Packet<?>>> getS2CPackets() {
        return new LinkedHashSet<>(AutismSharedState.get().getS2CPackets());
    }

    public void setC2SPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultC2SPackets() : new LinkedHashSet<>(packets);
        AutismSharedState.get().setC2SPackets(safe);
        config.c2sPackets = encodePackets(safe);
        saveConfig();
    }

    public void setS2CPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultS2CPackets() : new LinkedHashSet<>(packets);
        AutismSharedState.get().setS2CPackets(safe);
        config.s2cPackets = encodePackets(safe);
        saveConfig();
    }

    public void resetC2SPacketsToDefault() {
        setC2SPackets(defaultC2SPackets());
    }

    public void resetS2CPacketsToDefault() {
        setS2CPackets(defaultS2CPackets());
    }

    public Set<Class<? extends Packet<?>>> defaultC2SPackets() {
        Set<Class<? extends Packet<?>>> defaults = new LinkedHashSet<>();
        for (Class<? extends Packet<?>> packetClass : AutismPacketRegistry.getC2SPackets()) {
            if (!C2S_EXCLUDED_DEFAULTS.contains(packetClass)) defaults.add(packetClass);
        }

        defaults.add(net.minecraft.network.protocol.game.ServerboundChatPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundSignUpdatePacket.class);
        return defaults;
    }

    public Set<Class<? extends Packet<?>>> defaultS2CPackets() {
        return new LinkedHashSet<>();
    }

    private void applyConfigToSharedState() {
        AutismSharedState shared = AutismSharedState.get();
        applyRuntimePacketFlowDefaults();
        shared.setUseCustomPackets(config.useCustomPackets);
        shared.setC2SPackets(resolvePackets(config.c2sPackets, true));
        shared.setS2CPackets(resolvePackets(config.s2cPackets, false));
        shared.setAllowSignEditing(config.allowSignEditing);
        shared.setResourcePackForceDeny(config.autoDenyResourcePack);
        shared.setBypassResourcePack(config.pretendPackAccepted);
        shared.setStaggeredPacketSend(config.staggeredPacketSend);
        shared.setStaggeredSendDelay(config.staggeredSendDelay);
    }

    private void applyRuntimePacketFlowDefaults() {
        if (config != null) {
            config.applyRuntimeDefaults();
        }
        AutismSharedState shared = AutismSharedState.get();
        shared.setSendGuiPackets(true);
        shared.setDelayGuiPackets(false);
        shared.setStaggeredPacketSend(false);
        shared.setCaptureMode(false);
    }

    private void updatePassiveXCarryState() {
        AutismSharedState shared = AutismSharedState.get();
        if (shared.isXCarryForced()) return;
        if (!shared.isXCarryActive() && !isXCarryEnabled()) return;
        if (PackHideState.isActive()) {
            shared.setXCarryActive(false);
            return;
        }

        boolean active = false;
        if (isXCarryEnabled() && MC.player != null && MC.player.inventoryMenu != null) {
            active = autismclient.util.macro.XCarryAction.hasStoredItems(MC.player.inventoryMenu, true);
        }

        shared.setXCarryActive(active);
    }

    private void updateWorldSpawnState() {

        if (!joinedPlayConnection && isPlayerSpawnedInWorld()) {
            if (++stuckActiveTicks >= ACTIVE_WATCHDOG_TICKS) {
                joinedPlayConnection = true;
                spawnedInWorld = true;
                stuckActiveTicks = 0;
                logWatchdogRecovery();
                return;
            }
        } else {
            stuckActiveTicks = 0;
        }

        if (!joinedPlayConnection) {
            spawnedInWorld = false;
            autoProbePending = false;
            return;
        }

        boolean wasSpawned = spawnedInWorld;
        spawnedInWorld = isPlayerSpawnedInWorld();
        if (!wasSpawned && spawnedInWorld && !PackHideState.isActive()) {
            AutismJoinMacroController.onWorldReady();

            autoProbePending = true;
            autoProbePendingSince = System.currentTimeMillis();
        }
        tickAutoProbe();
    }

    private void tickAutoProbe() {
        if (!autoProbePending) return;
        long waited = System.currentTimeMillis() - autoProbePendingSince;
        if (waited > AUTO_PROBE_GIVE_UP_MS) { autoProbePending = false; return; }
        if (PackHideState.isActive() || config == null || !config.autoProbePlugins) {
            autoProbePending = false;
            return;
        }
        if (!spawnedInWorld) return;

        AutismServerInfoOverlay overlay = getServerDataOverlay();
        if (overlay == null || !overlay.isAutoProbeConnectionReady()) return;

        if (!overlay.isAutoProbeContextReady() && waited < AUTO_PROBE_CMD_GRACE_MS) return;

        if (overlay.autoProbeOnSpawn()) autoProbePending = false;
    }

    private boolean isPlayerSpawnedInWorld() {
        return MC != null && MC.getConnection() != null && MC.player != null && MC.level != null;
    }

    private void saveConfig() {
        if (config == null) return;
        config.save();
    }

    private Set<Class<? extends Packet<?>>> resolvePackets(List<String> names, boolean c2s) {
        Set<Class<? extends Packet<?>>> resolved = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                Class<? extends Packet<?>> packetClass = AutismPacketRegistry.getPacket(name);
                if (packetClass == null) {
                    try {
                        Class<?> direct = Class.forName(name);
                        if (Packet.class.isAssignableFrom(direct)) {
                            @SuppressWarnings("unchecked")
                            Class<? extends Packet<?>> typed = (Class<? extends Packet<?>>) direct;
                            packetClass = typed;
                        }
                    } catch (ClassNotFoundException ignored) {  }
                }
                if (packetClass != null) resolved.add(packetClass);
            }
        }

        if (resolved.isEmpty()) {
            return c2s ? defaultC2SPackets() : defaultS2CPackets();
        }

        return resolved;
    }

    private List<String> encodePackets(Set<Class<? extends Packet<?>>> packets) {
        List<String> names = new ArrayList<>();
        for (Class<? extends Packet<?>> packetClass : packets) {
            String name = AutismPacketRegistry.getName(packetClass);
            names.add(name != null ? name : packetClass.getName());
        }
        return names;
    }

    private void tickKeybinds() {
        AutismConfig cfg = config;
        if (cfg == null) return;
        AutismSharedState shared = AutismSharedState.get();
        refreshKeyboundMacroCache();

        if (cfg.keybindModuleMenu != -1) {
            boolean pressed = isBindPressed(cfg.keybindModuleMenu);
            if (pressed && !moduleMenuKeyPressed && MC.gui.screen() == null) {
                MC.gui.setScreen(new AutismModuleScreen(null));
            }
            moduleMenuKeyPressed = pressed;
        }

        if (PackHideState.isActive()) {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
            return;
        }

        if (cfg.keybindLoadGui != -1) {
            boolean pressed = isBindPressed(cfg.keybindLoadGui);
            if (pressed && !loadGuiKeyPressed) {
                if (restoreSavedScreenUiBehavior()) {
                    AutismNotifications.show("GUI restored.", 0xFF35D873);
                } else {
                    AutismNotifications.error("No stored GUI.");
                }
            }
            loadGuiKeyPressed = pressed;
        }

        if (cfg.keybindFlushQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindFlushQueue);
            if (pressed && !flushQueueKeyPressed) {
                int count = flushQueuedPacketsUiBehavior();
                notifyFlushQueuedPacketsUiResult(count);
            }
            flushQueueKeyPressed = pressed;
        }

        if (cfg.keybindClearQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindClearQueue);
            if (pressed && !clearQueueKeyPressed) {
                int count = clearQueuedPacketsUiBehavior();
                notifyClearQueuedPacketsUiResult(count);
            }
            clearQueueKeyPressed = pressed;
        }

        if (cfg.keybindToggleLogger != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleLogger);
            if (pressed && !toggleLoggerKeyPressed) {
                togglePacketLoggerUiBehavior();
            }
            toggleLoggerKeyPressed = pressed;
        }

        if (cfg.keybindToggleSend != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleSend);
            if (pressed && !toggleSendKeyPressed) {
                boolean newValue = !shared.shouldSendGuiPackets();
                applySendGuiPacketsUiBehavior(newValue);
                AutismNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            toggleSendKeyPressed = pressed;
        }

        if (cfg.keybindToggleDelay != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleDelay);
            if (pressed && !toggleDelayKeyPressed) {
                boolean newValue = !shared.shouldDelayGuiPackets();
                int sent = applyDelayGuiPacketsUiBehavior(newValue);
                notifyDelayPacketsUiResult(newValue, sent);
            }
            toggleDelayKeyPressed = pressed;
        }

        for (AutismMacro macro : cachedKeyboundMacros) {
            boolean pressed = isBindPressed(macro.keyCode);
            boolean wasPressed = macroKeyStates.getOrDefault(macro.name, false);
            if (pressed && !wasPressed) {
                if (autismclient.util.multi.MultiTakeoverState.isActive()) {
                    autismclient.util.multi.MultiManager multi = autismclient.util.multi.MultiManager.getIfInitialized();
                    if (multi != null && multi.isMacroPlayingOnInteractiveScope(macro.name, java.util.Set.of())) {
                        multi.stopMacroOnInteractiveScope(java.util.Set.of());
                    } else {
                        macro.execute();
                    }
                } else if (autismclient.util.macro.MacroExecutor.isMacroRunning(macro)) {
                    autismclient.util.macro.MacroExecutor.stopMacro(macro);
                } else {
                    macro.execute();
                }
            }
            macroKeyStates.put(macro.name, pressed);
        }
    }

    private void refreshKeyboundMacroCache() {
        AutismMacroManager macroManager = AutismMacroManager.get();
        long revision = macroManager.getRevision();
        if (revision == cachedMacroKeybindRevision) return;

        List<AutismMacro> keyboundMacros = new ArrayList<>();
        Set<String> activeMacroNames = new HashSet<>();
        for (AutismMacro macro : macroManager.getAll()) {
            if (macro == null || macro.keyCode == -1) continue;
            keyboundMacros.add(macro);
            activeMacroNames.add(macro.name);
        }

        macroKeyStates.keySet().removeIf(name -> !activeMacroNames.contains(name));
        cachedKeyboundMacros = keyboundMacros;
        cachedMacroKeybindRevision = revision;
    }

    private boolean isAnyTextFieldFocused() {
        return AutismOverlayManager.get().isAnyTextFieldFocused();
    }

    private boolean isBindPressed(int bindCode) {
        return autismclient.util.AutismBindUtil.isBindPressed(MC, bindCode);
    }

    private void restoreSavedScreen() {
        AutismSharedState shared = AutismSharedState.get();
        if (MC == null) return;

        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            AutismNotifications.error("No stored GUI.");
            return;
        }

        MC.gui.setScreen(shared.getStoredScreen());
        AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
        if (MC.player != null) MC.player.containerMenu = handler;
        AutismNotifications.show("GUI restored.", 0xFF35D873);
    }
}
