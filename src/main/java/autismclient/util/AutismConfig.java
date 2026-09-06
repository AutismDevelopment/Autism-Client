package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.PackHideState;
import autismclient.security.AutismProtector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AutismConfig implements Cloneable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutismConfig globalInstance;

    public static Runnable afterSave;

    static Consumer<AutismConfig> afterPersistenceSnapshot;

    public static AutismConfig getGlobal() {
        if (globalInstance == null) {
            globalInstance = load();
            AutismPerf.publishConfigState(globalInstance);
            PackHideState.publishRuntimeState(globalInstance);
            AutismProtector.publishRuntimeState(globalInstance);
        }
        return globalInstance;
    }

    public static void setGlobal(AutismConfig config) {
        globalInstance = config;
        AutismPerf.publishConfigState(config);
        PackHideState.publishRuntimeState(config);
        AutismProtector.publishRuntimeState(config);
    }

    public transient boolean sendGuiPackets = true;
    public transient boolean delayGuiPackets = false;
    public boolean useCustomPackets = false;
    public List<String> c2sPackets = new ArrayList<>();
    public List<String> s2cPackets = new ArrayList<>();

    public List<String> packetLoggerBlocked = new ArrayList<>();
    public boolean packetLoggerBlockedInit = false;
    public int packetLoggerBlockedDefaultsVersion = 0;
    public List<PayloadChannelFilterRule> packetLoggerPayloadFilters = new ArrayList<>();
    public List<PayloadChannelRegistrationRule> packetLoggerPayloadRegistrations = new ArrayList<>();
    @Deprecated
    public List<PayloadChannelListenerRule> packetLoggerPayloadListeners = new ArrayList<>();
    public boolean payloadRegistrationUnlocked = false;
    public int payloadRegistrationWarningAcceptedVersion = 0;
    public boolean packetLoggerCapturing = false;
    public boolean allowSignEditing = true;
    public boolean autoDenyResourcePack = false;
    public boolean pretendPackAccepted = false;

    public int packResponseDelayMs = 20000;
    public boolean resourcePackChoiceInitialized = false;
    public boolean spoofClientVanilla = true;

    public boolean protectorEnabled = true;
    public boolean protectorSpoofBrand = true;
    public boolean protectorFilterChannels = true;
    public boolean protectorTranslationProtection = true;
    public boolean protectorDisableTelemetry = true;

    public boolean protectorBlockLocalUrls = true;

    public boolean protectorIsolatePackCache = true;

    public boolean protectorStripServerPacks = false;

    public boolean protectorChatSigningOff = false;
    public boolean performanceDebug = false;
    public boolean inventoryMove = false;
    public boolean xCarry = true;
    public boolean noPauseOnLostFocus = true;
    public boolean showItemIds = true;

    public boolean autoProbePlugins = false;

    public transient Map<String, PluginScanCacheEntry> serverPluginScans = new LinkedHashMap<>();
    public boolean lanSyncEnabled = true;
    public boolean staggeredPacketSend = false;
    public int staggeredSendDelay = 1;
    public String executionPreset = "DEFAULT";
    public boolean packetBurstMode = true;
    public boolean useMsSleepMode = false;
    public int msSleepInterval = 5;
    public boolean instantExecutionMode = true;
    public int actionDelayUs = 0;
    public boolean useDirectFlush = true;
    public boolean forceChannelFlush = true;
    public boolean flushQueueOnDelayDisable = true;
    public boolean captureAsExact = false;
    public String commandPrefix = "";
    public boolean joinMacroEnabled = false;
    public String joinMacroName = "";
    public String joinMacroTiming = "WORLD";
    public String joinMacroTriggerJoin = "FIRST";
    public boolean joinMacroKeepEnabled = false;

    public boolean stopMacroOnLeave = true;

    public Map<String, String> joinMacroFormValues = new LinkedHashMap<>();
    public Map<Integer, String> commandBinds = new LinkedHashMap<>();

    public Map<String, ModuleState> modules = new LinkedHashMap<>();
    public List<String> hideRestoreModules = new ArrayList<>();
    public Map<String, ModuleCategoryLayout> moduleCategoryLayouts = new LinkedHashMap<>();
    public Map<String, List<String>> moduleCategoryOrder = new LinkedHashMap<>();
    public Map<String, HudElementState> hudElements = new LinkedHashMap<>();
    public boolean hudLayoutMigrated = false;

    public boolean hudLayoutNormalizedV2 = false;
    public int hudSnapRange = 10;
    public int hudEdgePadding = 4;
    public boolean hudEditorGrid = true;

    public int keybindLoadGui = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
    public int keybindModuleMenu = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public int keybindFlushQueue = -1;
    public int keybindClearQueue = -1;
    public int keybindToggleLogger = -1;
    public int keybindToggleSend = -1;
    public int keybindToggleDelay = -1;

    public boolean keybindInsideGui = false;

    public boolean customMainMenu = true;

    public boolean infiniChat = true;
    public double overlayScale = 1.0;

    public int tpMaxPackets = 20;
    public int tpPauseMs = 500;

    public boolean welcomeShown = false;
    public String welcomeInstallIdentity = "";

    public boolean voiceChatModdedPromptShown = false;

    public boolean multiDisclaimerAccepted = false;

    public boolean multiShowTooltips = true;

    public boolean multiAutoSolveCaptcha = true;

    public int multiMacroStartDelayMs = 0;

    public boolean accountGenSetPassword = true;

    public String accountGenPasswordMode = "Generate";

    public String accountGenSharedPassword = "";

    public String activeProfileId = "";

    public List<String> hideRestoreMeteorModules = new ArrayList<>();

    public boolean hideMeteorHudActive = true;

    public boolean essentialHiddenByPanic = false;

    public boolean essentialSavedEnabled = true;

    public List<String> disabledAddonIds = new ArrayList<>();

    public ThemeColors themeColors = new ThemeColors();

    public static final class ThemeColors {

        public boolean advanced = false;

        public int master = 0xFFFF3B3B;
        public int accent = 0xFFFF3B3B;
        public int outline = 0xFFB32B2B;
        public int text = 0xFFF3ECE7;
        public int toggle = 0xFFFF3B3B;
        public int backdrop = 0xFFB24848;
        public int success = 0xFF35D873;
        public int danger = 0xFFE26A6A;
        public int button = 0xFF8F1F24;
        public int header = 0xFFFF3B3B;
        public int hover = 0xFFFF6464;
    }

    public static final class ModuleState {
        public boolean enabled = false;
        public int keybind = -1;
        public Map<String, String> settings = new LinkedHashMap<>();
    }

    public static final class ModuleCategoryLayout {
        public int x = -1;
        public int y = -1;
        public boolean collapsed = false;

        public int visibleRows = 0;
    }

    public static final class HudElementState {
        public boolean enabled = true;
        public int x = 8;
        public int y = 8;
        public double scale = 1.0;
        public String anchor = "TOP_LEFT";
        public Map<String, String> settings = new LinkedHashMap<>();
    }

    public static class PayloadChannelFilterRule {
        public String label = "";
        public String pattern = "";
        public boolean enabled = true;
        public boolean preset = false;
    }

    public static class PayloadChannelRegistrationRule {
        public String label = "";
        public String channel = "";
        public boolean enabled = true;
        public String source = "custom";
    }

    @Deprecated
    public static final class PayloadChannelListenerRule extends PayloadChannelFilterRule {
        public String direction = "ANY";
    }

    public static AutismConfig load() {
        File file = configFile();
        AutismConfig config = null;
        if (file.exists()) {
            config = tryRead(file);
            if (config == null) {

                backupCorruptConfig();
                File backup = backupFile();
                if (backup.exists()) {
                    config = tryRead(backup);
                    if (config != null) {
                        AutismClientAddon.LOG.warn("Autism config was corrupt; recovered from backup {}", backup.getName());
                    }
                }
            }
        }
        if (config == null) {
            config = new AutismConfig();
        }

        Map<String, PluginScanCacheEntry> legacyScans = config.serverPluginScans;
        ServerPluginScanCache cache = ServerPluginScanCache.get();
        boolean migrated = cache.mergeLegacy(legacyScans);
        config.serverPluginScans = cache.sharedView();
        config.applyRuntimeDefaults();
        if (migrated) config.save();
        return config;
    }

    private static AutismConfig tryRead(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            AutismConfig loaded = GSON.fromJson(root, AutismConfig.class);
            if (loaded != null && root instanceof JsonObject object && object.has("serverPluginScans")) {
                Map<String, PluginScanCacheEntry> legacy = GSON.fromJson(object.get("serverPluginScans"),
                    new TypeToken<Map<String, PluginScanCacheEntry>>() { }.getType());
                loaded.serverPluginScans = legacy == null ? new LinkedHashMap<>() : legacy;
            }
            return loaded != null ? loaded : new AutismConfig();
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to read Autism config from {}", file.getName(), t);
            return null;
        }
    }

    private static File backupFile() {
        return new File(configFile().getParentFile(), "config.json.bak");
    }

    static File configFile() {
        return new File(AutismClientAddon.FOLDER, "config.json");
    }

    private static void backupCorruptConfig() {
        File file = configFile();
        try {
            File corrupt = new File(file.getParentFile(), "config.json.corrupt-" + System.currentTimeMillis());
            java.nio.file.Files.move(file.toPath(), corrupt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            AutismClientAddon.LOG.error("Autism config was corrupt; moved it to {}", corrupt.getName());
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to set aside corrupt Autism config", t);
        }
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        AutismPerf.publishConfigState(this);
        PackHideState.publishRuntimeState(this);
        AutismProtector.publishRuntimeState(this);
        AutismConfigWriter.request(this);

        Runnable hook = afterSave;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Autism config afterSave hook failed", t);
            }
        }
    }

    public static void enqueuePendingSaveNow() {
        AutismConfigWriter.capturePendingNow();

        if (!AutismLiteVariant.enabled()) AutismProfileManager.flushPendingMirrorIfInitialized();
    }

    public static void flushPendingSaves(long timeoutMs) {
        enqueuePendingSaveNow();
        AutismConfigWriter.flushBlocking(timeoutMs);
    }

    static String toJson(AutismConfig config) {
        return toJson(config, ServerPluginScanCache.get());
    }

    static String toJson(AutismConfig config, ServerPluginScanCache pluginScanCache) {
        Map<String, PluginScanCacheEntry> fallback = pluginScanCache.pendingLegacyFallback();
        if (fallback.isEmpty()) return GSON.toJson(config);
        JsonObject root = GSON.toJsonTree(config).getAsJsonObject();
        root.add("serverPluginScans", GSON.toJsonTree(fallback));
        return GSON.toJson(root);
    }

    static void onPersistenceSnapshot(AutismConfig snapshot) {
        Consumer<AutismConfig> hook = afterPersistenceSnapshot;
        if (hook == null) return;
        try {
            hook.accept(snapshot);
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Autism config snapshot hook failed", t);
        }
    }

    static void writeToDisk(String json) {
        synchronized (SAVE_LOCK) {
            long perfStart = AutismPerf.begin();
            File file = configFile();
            file.getParentFile().mkdirs();

            File tmp = new File(file.getParentFile(), "config.json.tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(json);
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Failed to write Autism config", t);
                tmp.delete();
                return;
            }

            try {
                if (file.exists()) {
                    java.nio.file.Files.copy(file.toPath(), backupFile().toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to back up Autism config before save", t);
            }

            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomicFailed) {

                try {
                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable t) {
                    AutismClientAddon.LOG.error("Failed to swap in Autism config", t);
                }
            }
            AutismPerf.endSpike("config.write", perfStart, 100_000_000L);
        }
    }

    public AutismConfig deepCopy() {
        return AutismConfigSnapshot.copyOf(this);
    }

    public AutismConfig snapshotForProfile() {
        return stripMachineStateForProfile(deepCopy());
    }

    static AutismConfig profileSnapshotFromDetached(AutismConfig detached) {
        if (detached == null) return stripMachineStateForProfile(new AutismConfig());
        return stripMachineStateForProfile(detached.shallowDetachedCopy());
    }

    private AutismConfig shallowDetachedCopy() {
        try {
            return (AutismConfig) super.clone();
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static AutismConfig stripMachineStateForProfile(AutismConfig copy) {
        copy.activeProfileId = "";
        copy.packetLoggerCapturing = false;
        copy.serverPluginScans = new LinkedHashMap<>();
        copy.welcomeShown = false;
        copy.welcomeInstallIdentity = "";
        copy.essentialHiddenByPanic = false;
        return copy;
    }

    public static boolean sameThemeColors(AutismConfig a, AutismConfig b) {
        if (a == null || b == null) return a == b;
        return GSON.toJson(a.themeColors).equals(GSON.toJson(b.themeColors));
    }

    public static boolean samePayloadRules(AutismConfig a, AutismConfig b) {
        if (a == null || b == null) return a == b;
        return GSON.toJson(a.packetLoggerPayloadFilters).equals(GSON.toJson(b.packetLoggerPayloadFilters))
            && GSON.toJson(a.packetLoggerPayloadRegistrations).equals(GSON.toJson(b.packetLoggerPayloadRegistrations));
    }

    public void applyRuntimeDefaults() {
        sendGuiPackets = true;
        delayGuiPackets = false;
        staggeredPacketSend = false;
        if (c2sPackets == null) c2sPackets = new ArrayList<>();
        if (s2cPackets == null) s2cPackets = new ArrayList<>();
        if (packetLoggerPayloadFilters == null) packetLoggerPayloadFilters = new ArrayList<>();
        if (packetLoggerPayloadRegistrations == null) packetLoggerPayloadRegistrations = new ArrayList<>();
        if (packetLoggerPayloadListeners == null) packetLoggerPayloadListeners = new ArrayList<>();
        if (packetLoggerPayloadFilters.isEmpty() && !packetLoggerPayloadListeners.isEmpty()) {
            for (PayloadChannelListenerRule oldRule : packetLoggerPayloadListeners) {
                if (oldRule == null) continue;
                PayloadChannelFilterRule rule = new PayloadChannelFilterRule();
                rule.label = oldRule.label;
                rule.pattern = oldRule.pattern;
                rule.enabled = oldRule.enabled;
                rule.preset = oldRule.preset;
                packetLoggerPayloadFilters.add(rule);
            }
        }
        if (!packetLoggerPayloadListeners.isEmpty()) {
            packetLoggerPayloadListeners = new ArrayList<>();
        }
        if (modules == null) modules = new LinkedHashMap<>();
        if (hideRestoreModules == null) hideRestoreModules = new ArrayList<>();
        if (hideRestoreMeteorModules == null) hideRestoreMeteorModules = new ArrayList<>();
        if (moduleCategoryLayouts == null) moduleCategoryLayouts = new LinkedHashMap<>();
        if (moduleCategoryOrder == null) moduleCategoryOrder = new LinkedHashMap<>();
        if (hudElements == null) hudElements = new LinkedHashMap<>();
        if (serverPluginScans == null) serverPluginScans = new LinkedHashMap<>();
        if (disabledAddonIds == null) disabledAddonIds = new ArrayList<>();
        if (themeColors == null) themeColors = new ThemeColors();
        overlayScale = AutismUiScale.nearestAllowedOverlayScale(overlayScale);
        tpMaxPackets = Math.max(1, Math.min(100, tpMaxPackets));
        tpPauseMs = Math.max(50, Math.min(10_000, tpPauseMs));
        if (hudSnapRange <= 0) hudSnapRange = 10;
        if (hudEdgePadding < 0) hudEdgePadding = 4;
        commandPrefix = AutismCompatManager.normalizeStoredCommandPrefix(commandPrefix);
        if (!resourcePackChoiceInitialized) {
            pretendPackAccepted = false;
            resourcePackChoiceInitialized = true;
        }
        for (HudElementState state : hudElements.values()) {
            if (state != null && state.settings == null) state.settings = new LinkedHashMap<>();
        }
    }

    private static String pluginScanKey(String address, String contextSignature) {
        String a = address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT);
        String c = contextSignature == null ? "" : contextSignature.trim().toLowerCase(java.util.Locale.ROOT);
        return c.isEmpty() ? a : a + "|" + Integer.toHexString(c.hashCode());
    }

    public PluginScanCacheEntry getPluginScan(String address, String contextSignature) {
        if (address == null || address.isBlank()) return null;
        return ServerPluginScanCache.get().get(pluginScanKey(address, contextSignature));
    }

    public long getPluginScanTimestamp(String address, String contextSignature) {
        PluginScanCacheEntry e = getPluginScan(address, contextSignature);
        return e == null ? 0L : e.scannedAtMs;
    }

    public PluginScanCacheEntry getPluginScanByAddress(String address) {
        if (address == null || address.isBlank()) return null;
        return ServerPluginScanCache.get().newestForAddress(
            address.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public long getPluginScanTimestampByAddress(String address) {
        PluginScanCacheEntry e = getPluginScanByAddress(address);
        return e == null ? 0L : e.scannedAtMs;
    }

    public Map<String, PluginScanCacheEntry> allPluginScans() {
        return ServerPluginScanCache.get().snapshot();
    }

    public long pluginScanRevision() {
        return ServerPluginScanCache.get().revision();
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, Map.of(), Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, guis, confidence, Map.of(), Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence, Map<String, String> copyEvidence,
                              Map<String, List<String>> copyCommands) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, guis, confidence,
            copyEvidence, copyCommands, "COMPLETE", 0, 0, 0, 0, "", "");
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence, Map<String, String> copyEvidence,
                              Map<String, List<String>> copyCommands, String scanStatus,
                              int totalProbes, int answeredProbes, int retriedProbes, int failedProbes,
                              String serverName, String serverAddress) {
        if (address == null || address.isBlank()) return;
        PluginScanCacheEntry e = new PluginScanCacheEntry();
        e.contextSignature = contextSignature == null ? "" : contextSignature;
        e.serverName = serverName == null ? "" : serverName;
        e.serverAddress = serverAddress == null || serverAddress.isBlank() ? address : serverAddress;
        e.plugins = plugins == null ? new ArrayList<>() : new ArrayList<>(plugins);
        e.commands = commands == null ? new LinkedHashMap<>() : new LinkedHashMap<>(commands);
        e.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
        e.channels = channels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(channels);
        e.guis = guis == null ? new LinkedHashMap<>() : new LinkedHashMap<>(guis);
        e.confidence = confidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(confidence);
        e.copyEvidence = copyEvidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(copyEvidence);
        e.copyCommands = copyCommands == null ? new LinkedHashMap<>() : new LinkedHashMap<>(copyCommands);
        e.scanStatus = scanStatus == null || scanStatus.isBlank() ? "COMPLETE" : scanStatus;
        e.totalProbes = Math.max(0, totalProbes);
        e.answeredProbes = Math.max(0, answeredProbes);
        e.retriedProbes = Math.max(0, retriedProbes);
        e.failedProbes = Math.max(0, failedProbes);
        e.scannedAtMs = System.currentTimeMillis();
        ServerPluginScanCache.get().put(pluginScanKey(address, contextSignature), e);
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public void removePluginScan(String address, String contextSignature) {
        if (address == null) return;
        ServerPluginScanCache.get().remove(pluginScanKey(address, contextSignature));
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public void removePluginScan(String address) {
        if (address == null) return;
        String a = pluginScanKey(address, "");
        ServerPluginScanCache.get().removeAddress(a);
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public static final class PluginScanCacheEntry {
        public String contextSignature = "";

        public String serverName = "";

        public String serverAddress = "";
        public List<String> plugins = new ArrayList<>();
        public Map<String, List<String>> commands = new LinkedHashMap<>();
        public Map<String, String> evidence = new LinkedHashMap<>();
        public Map<String, List<String>> channels = new LinkedHashMap<>();
        public Map<String, List<String>> guis = new LinkedHashMap<>();
        public Map<String, String> confidence = new LinkedHashMap<>();
        public Map<String, String> copyEvidence = new LinkedHashMap<>();
        public Map<String, List<String>> copyCommands = new LinkedHashMap<>();
        public String scanStatus = "COMPLETE";
        public int totalProbes = 0;
        public int answeredProbes = 0;
        public int retriedProbes = 0;
        public int failedProbes = 0;
        public long scannedAtMs = 0L;
    }
}
