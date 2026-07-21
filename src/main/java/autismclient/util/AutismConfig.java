package autismclient.util;

import autismclient.AutismClientAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutismConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(AutismClientAddon.FOLDER, "config.json");
    private static AutismConfig globalInstance;

    public static Runnable afterSave;

    public static AutismConfig getGlobal() {
        if (globalInstance == null) globalInstance = load();
        return globalInstance;
    }

    public static void setGlobal(AutismConfig config) {
        globalInstance = config;
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

    public boolean protectorCrashGuard = true;
    public boolean performanceDebug = false;
    public boolean inventoryMove = false;
    public boolean xCarry = true;
    public boolean noPauseOnLostFocus = true;
    public boolean showItemIds = true;

    public boolean autoProbePlugins = false;
    public Map<String, PluginScanCacheEntry> serverPluginScans = new LinkedHashMap<>();
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

    public Map<String, String> joinMacroFormValues = new LinkedHashMap<>();
    public Map<Integer, String> commandBinds = new LinkedHashMap<>();

    public Map<String, ModuleState> modules = new LinkedHashMap<>();
    public List<String> hideRestoreModules = new ArrayList<>();
    public Map<String, ModuleCategoryLayout> moduleCategoryLayouts = new LinkedHashMap<>();
    public Map<String, ModuleWindowLayout> moduleWindowLayouts = new LinkedHashMap<>();
    public Map<String, List<String>> moduleCategoryOrder = new LinkedHashMap<>();
    public Map<String, HudElementState> hudElements = new LinkedHashMap<>();
    public boolean hudLayoutMigrated = false;
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
    public double overlayScale = 1.0;

    public boolean welcomeShown = false;
    public String welcomeInstallIdentity = "";

    public boolean voiceChatModdedPromptShown = false;

    public boolean multiDisclaimerAccepted = false;

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
    }

    public static final class ModuleWindowLayout {
        public int x = -1;
        public int y = -1;
        public boolean pinned = false;
        public boolean open = false;
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
        AutismConfig config = null;
        if (FILE.exists()) {
            config = tryRead(FILE);
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
        config.applyRuntimeDefaults();
        return config;
    }

    private static AutismConfig tryRead(File file) {
        try (FileReader reader = new FileReader(file)) {
            AutismConfig loaded = GSON.fromJson(reader, AutismConfig.class);
            return loaded != null ? loaded : new AutismConfig();
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to read Autism config from {}", file.getName(), t);
            return null;
        }
    }

    private static File backupFile() {
        return new File(FILE.getParentFile(), "config.json.bak");
    }

    private static void backupCorruptConfig() {
        try {
            File corrupt = new File(FILE.getParentFile(), "config.json.corrupt-" + System.currentTimeMillis());
            java.nio.file.Files.move(FILE.toPath(), corrupt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            AutismClientAddon.LOG.error("Autism config was corrupt; moved it to {}", corrupt.getName());
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to set aside corrupt Autism config", t);
        }
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        String json;
        long perfStart = AutismPerf.begin();
        try {
            json = GSON.toJson(this);
        } catch (Throwable t) {
            AutismClientAddon.LOG.error("Failed to serialize Autism config", t);
            return;
        }
        AutismPerf.endSpike("config.serialize", perfStart, 20_000_000L);
        AutismConfigWriter.enqueue(json);

        Runnable hook = afterSave;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Autism config afterSave hook failed", t);
            }
        }
    }

    public static void flushPendingSaves(long timeoutMs) {
        AutismConfigWriter.flushBlocking(timeoutMs);
    }

    static void writeToDisk(String json) {
        synchronized (SAVE_LOCK) {
            long perfStart = AutismPerf.begin();
            FILE.getParentFile().mkdirs();

            File tmp = new File(FILE.getParentFile(), "config.json.tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(json);
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Failed to write Autism config", t);
                tmp.delete();
                return;
            }

            try {
                if (FILE.exists()) {
                    java.nio.file.Files.copy(FILE.toPath(), backupFile().toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to back up Autism config before save", t);
            }

            try {
                java.nio.file.Files.move(tmp.toPath(), FILE.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomicFailed) {

                try {
                    java.nio.file.Files.move(tmp.toPath(), FILE.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable t) {
                    AutismClientAddon.LOG.error("Failed to swap in Autism config", t);
                }
            }
            AutismPerf.endSpike("config.write", perfStart, 100_000_000L);
        }
    }

    public AutismConfig deepCopy() {
        AutismConfig copy = GSON.fromJson(GSON.toJson(this), AutismConfig.class);
        if (copy == null) copy = new AutismConfig();
        copy.applyRuntimeDefaults();
        return copy;
    }

    public AutismConfig snapshotForProfile() {
        AutismConfig copy = deepCopy();
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
        if (moduleWindowLayouts == null) moduleWindowLayouts = new LinkedHashMap<>();
        if (moduleCategoryOrder == null) moduleCategoryOrder = new LinkedHashMap<>();
        if (hudElements == null) hudElements = new LinkedHashMap<>();
        if (serverPluginScans == null) serverPluginScans = new LinkedHashMap<>();
        if (disabledAddonIds == null) disabledAddonIds = new ArrayList<>();
        if (themeColors == null) themeColors = new ThemeColors();
        overlayScale = AutismUiScale.nearestAllowedOverlayScale(overlayScale);
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
        if (serverPluginScans == null || address == null || address.isBlank()) return null;
        return serverPluginScans.get(pluginScanKey(address, contextSignature));
    }

    public long getPluginScanTimestamp(String address, String contextSignature) {
        PluginScanCacheEntry e = getPluginScan(address, contextSignature);
        return e == null ? 0L : e.scannedAtMs;
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
            copyEvidence, copyCommands, "COMPLETE", 0, 0, 0, 0);
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence, Map<String, String> copyEvidence,
                              Map<String, List<String>> copyCommands, String scanStatus,
                              int totalProbes, int answeredProbes, int retriedProbes, int failedProbes) {
        if (address == null || address.isBlank()) return;
        if (serverPluginScans == null) serverPluginScans = new LinkedHashMap<>();
        PluginScanCacheEntry e = new PluginScanCacheEntry();
        e.contextSignature = contextSignature == null ? "" : contextSignature;
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
        serverPluginScans.put(pluginScanKey(address, contextSignature), e);
        save();
    }

    public void removePluginScan(String address, String contextSignature) {
        if (serverPluginScans == null || address == null) return;
        if (serverPluginScans.remove(pluginScanKey(address, contextSignature)) != null) save();
    }

    public void removePluginScan(String address) {
        if (serverPluginScans == null || address == null) return;
        String a = pluginScanKey(address, "");
        boolean changed = serverPluginScans.keySet().removeIf(k -> k.equals(a) || k.startsWith(a + "|"));
        if (changed) save();
    }

    public static final class PluginScanCacheEntry {
        public String contextSignature = "";
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
