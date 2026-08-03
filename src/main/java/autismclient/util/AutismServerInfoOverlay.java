package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiLabel;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.ProgressBar;
import autismclient.gui.vanillaui.direct.DirectInfoRow;
import autismclient.gui.vanillaui.direct.DirectRow;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.components.Slider;
import autismclient.gui.vanillaui.direct.DirectSpacer;
import autismclient.gui.vanillaui.components.TabStrip;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.macro.editor.ActionEditorOverlay;
import autismclient.modules.PackHideState;
import autismclient.util.macro.PayloadAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class AutismServerInfoOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OVERLAY_ID = "autism-serverinfo";
    private static final int HEADER_CONTROL = 12;
    private static final int HEADER_ARROW_WIDTH = 10;
    private static final int HEADER_ARROW_GAP = 3;
    private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;

    private int panelX = 350;
    private int panelY = 30;
    private int panelW = 236;
    private int panelH = 246;
    private static final int ROW_H = 16;
    private static final int PAD = 8;

    private boolean visible = false;
    private boolean collapsed = false;
    private boolean configurationOnly = false;
    private boolean isDragging = false;
    private double dragOffsetX, dragOffsetY;
    private float pressStartUiX, pressStartUiY;
    private int pressStartPanelX, pressStartPanelY;
    private boolean dragMoved = false;
    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow("Server Info");
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final TabStripNode tabStrip = new TabStripNode();
    private final CompactTextInput searchField = new CompactTextInput();
    private final CompactTextInput radarSearchField = new CompactTextInput();
    private final SliderNode probeDelaySlider = new SliderNode();
    private final ProgressBarNode scanProgressBar = new ProgressBarNode();
    private final DirectViewportSlot pluginListSlot = new DirectViewportSlot();
    private final DirectViewportSlot radarListSlot = new DirectViewportSlot();
    private boolean pluginScrollbarDragging = false;
    private int pluginScrollbarGrabOffset = 0;
    private boolean radarScrollbarDragging = false;
    private int radarScrollbarGrabOffset = 0;
    private long lastUiRebuildMs = 0L;

    private static final String[] TAB_NAMES = {"Info", "Plugins", "DupeDB"};
    private int activeTab = 0;

    private final List<ClickRegion> clickRegions = new ArrayList<>();

    private volatile String resolvedIp = null;
    private String lastResolvedAddress = null;
    private volatile boolean resolvingIp = false;

    private final List<String> detectedPlugins = new ArrayList<>();
    private final Map<String, List<String>> pluginCommands = new LinkedHashMap<>();
    private final Map<String, List<String>> pluginChannels = new LinkedHashMap<>();
    private final Map<String, List<String>> pluginGuis = new LinkedHashMap<>();
    private List<PluginListRow> cachedPluginRows = List.of();
    private String cachedPluginRowsQuery = null;
    private String cachedPluginRowsSelectedKey = null;
    private int cachedPluginRowsWidth = -1;
    private int[] cachedPluginRowOffsets = new int[0];
    private int cachedPluginRowsHeight = 0;
    private int pluginDataRevision = 0;
    private int pluginRowsRevision = 0;
    private int cachedPluginRowsRevision = -1;
    private Map<String, PluginDetail> cachedPluginDetails = Map.of();
    private int cachedPluginDetailsRevision = -1;
    private final ScrollState pluginScrollState = new ScrollState();
    private final ScrollState radarScrollState = new ScrollState();
    private int pluginContentHeight = 0;
    private int radarContentHeight = 0;
    private String selectedPlugin = null;
    private final Set<String> expandedRadarPlugins = new HashSet<>();
    private String lastRadarAutoCheckSignature = "";
    private long lastRadarAutoCheckAt = 0L;
    private Boolean lastRadarAuthenticated = null;
    private int pluginProbeDelayMs = 50;
    private boolean pluginScanDone = false;
    private boolean pluginScanInProgress = false;
    private PluginScanSession activePluginScan;
    private long nextPluginScanGeneration = 1L;
    private final AutismPluginProbeIdAllocator pluginProbeIdAllocator = new AutismPluginProbeIdAllocator();
    private PluginScanOutcome pluginScanOutcome = PluginScanOutcome.NONE;
    private String pluginScanIssue = "";
    private int lastPluginScanTotal;
    private int lastPluginScanAnswered;
    private int lastPluginScanRetried;
    private int lastPluginScanFailed;
    private final Set<String> observedPluginCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginEvidence> pluginEvidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginEvidence> pluginCopyEvidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginConfidence> pluginConfidence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginResultKind> pluginResultKinds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, List<String>> pluginCopyCommands = new LinkedHashMap<>();
    private final Map<String, PluginScanEntry> scanWorkingEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, PluginScanEntry> legacyScanEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Integer, PluginProbeSpec> observedSuggestionRequests = new LinkedHashMap<>();
    private final Deque<ObservedCommand> recentCommands = new ArrayDeque<>();
    private boolean pluginScanCompletionAnnounced = false;
    private boolean uiRebuildRequested = true;
    private boolean pluginManualSetupOpen = false;
    private int activeProbeDelayMs = DEFAULT_PLUGIN_PROBE_DELAY_MS;
    private String lastAutoScanContext = null;

    private String scannedServerAddress = null;
    private String lastRadarServerAddress = "";
    private String scannedPluginContextSignature = "";
    private String cachedPluginContextServerAddress = "";
    private String cachedPluginContextBrand = "";
    private String cachedPluginContextSignature = "";
    private boolean pluginContextSignatureDirty = true;
    private long lastPayloadFingerprintRevision = -1L;
    private static final long PLUGIN_SCAN_IDLE_MS = 700L;
    private static final long PLUGIN_SCAN_TIMEOUT_MS = 12000L;
    private static final int DEFAULT_PLUGIN_PROBE_DELAY_MS = 50;
    private static final int MIN_PLUGIN_PROBE_DELAY_MS = 10;
    private static final int MAX_PLUGIN_PROBE_DELAY_MS = 500;

    private static final long SAVED_PLUGIN_CACHE_MAX_AGE_MS = 60L * 24L * 60L * 60L * 1000L;
    private static final long PLUGIN_SCAN_SETTLE_MS = 450L;
    private static final long PLUGIN_SCAN_FINAL_SETTLE_MS = 1400L;
    private static final String PLUGIN_SCAN_MODEL_VERSION = "hybrid-plugin-scan-v12";
    private static final boolean PLUGIN_SCAN_TRACE = Boolean.getBoolean("autism.plugin.scan.trace");
    private static final long COMMAND_GUI_LINK_MS = 5000L;
    private static final int MAX_TRACKED_SUGGESTION_REQUESTS = 256;
    private static final int MAX_RECENT_COMMANDS = 24;
    private static final int PLUGIN_HEADER_H = 14;
    private static final int SHARED_PANEL_WIDTH = 200;
    private static final int PLUGIN_SETUP_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_SETUP_HEIGHT = 126;
    private static final int PLUGIN_SCANNING_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_SCANNING_HEIGHT = 92;
    private static final int INFO_MIN_WIDTH = SHARED_PANEL_WIDTH;
    private static final int INFO_MIN_HEIGHT = 258;
    private static final int PLUGIN_RESULTS_MIN_WIDTH = SHARED_PANEL_WIDTH;
    private static final int PLUGIN_RESULTS_MIN_HEIGHT = 280;
    private static final int RADAR_RESULTS_MIN_WIDTH = SHARED_PANEL_WIDTH;
    private static final int RADAR_RESULTS_MIN_HEIGHT = 286;
    private static final int RADAR_HEADER_H = 14;
    private static final int RADAR_CARD_H = 22;
    private static final int RADAR_FINDING_H = 18;
    private int infoPreferredWidth = INFO_MIN_WIDTH;
    private int infoPreferredHeight = INFO_MIN_HEIGHT;
    private int pluginPreferredWidth = PLUGIN_RESULTS_MIN_WIDTH;
    private int pluginPreferredHeight = PLUGIN_RESULTS_MIN_HEIGHT;
    private static final String[] COMMON_PLUGIN_NAMESPACES = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "citizens", "cmi", "cmilib", "multiverse-core", "multiverse", "viaversion",
        "viabackwards", "viarewind", "geysermc", "geyser", "floodgate", "protocollib",
        "coreprotect", "griefprevention", "shopkeepers", "dynmap", "placeholderapi",
        "skinsrestorer", "skript", "advancedanticheat", "vulcan", "grimac", "matrix",
        "spartan", "aac", "karhu", "verus", "nocheatplus", "authme", "authmereloaded",
        "librelogin", "nlogin", "openlogin", "limboauth", "loginsecurity", "fastlogin",
        "bungeeguard", "deluxemenus",
        "plotsquared", "supervanish", "packetevents", "oraxen", "itemsadder",
        "fawe", "fastasyncworldedit", "luckpermsbukkit", "essentialsgeoip", "essentialsprotect",
        "essentialsspawn", "essentialsxspawn", "multiverse-inventories", "multiverse-netherportals",
        "worldborder", "votifier", "nuVotifier", "votingplugin", "excellentcrates", "crazycrates",
        "goldencrates", "goldencrate", "cratereloaded", "magiccrates", "simplelootcrates",
        "cratesplus", "cratekeys", "advancedcrates", "phoenixcrates", "mysterycrates",
        "deluxecrates", "crazyenvoys", "crazyrewards", "jobs", "jobsreborn", "mcmmo", "towny", "factions", "factionsuuid",
        "lands", "residence", "claimchunk", "quickshop", "quickshop-hikari", "chestshop",
        "shopgui", "auctionhouse", "combatlogx", "litebans", "advancedban", "libertybans",
        "luckpermsgui", "tab", "tablist", "scoreboard", "animatedscoreboard", "ajleaderboards",
        "ajqueue", "spark", "sparkbukkit", "plan", "minimotd", "protocolsupport", "viaversion",
        "excellentenchants", "eco", "ecoenchants", "mythicmobs", "mythiclib", "modelengine",
        "mmoitems", "mmocore", "denizen", "citizenscmd", "sentinel", "npcs", "proantitab",
        "pat", "papiproxybridge", "groupmanager", "vulcanbungee",
        "grimacbukkit", "negativity", "intave", "polar", "horizon", "themis", "libreforge",
        "casinoslots", "slotmachine", "slotmachineplus", "roulette", "casino", "crazycasino",
        "coinflip", "deluxecoinflip", "crazycoinflip", "jackpot", "lottery", "lotteryplus",
        "blackjack", "crash", "plinko", "casebattle"
    };
    private static final String[] PLUGIN_LIST_PROBE_COMMANDS = {
        "/plugins ", "/pl ", "/bukkit:plugins ", "/bukkit:pl "
    };
    private static final String[] VERSION_PROBE_COMMANDS = {
        "/ver ", "/version ", "/about ", "/icanhasbukkit ", "/bukkit:ver ", "/bukkit:version "
    };
    private static final String[] HELP_PROBE_COMMANDS = {
        "/help ", "/? ", "/bukkit:help ", "/minecraft:help "
    };
    private static final String[] HIGH_VALUE_PLUGIN_HINTS = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "cmi", "citizens", "multiverse", "viaversion", "geyser", "floodgate",
        "protocollib", "coreprotect", "placeholderapi", "skinsrestorer", "skript",
        "vulcan", "grimac", "matrix", "spartan", "authme", "librelogin", "nlogin",
        "limboauth", "loginsecurity", "crazycrates", "excellentcrates", "goldencrates",
        "cratereloaded", "deluxemenus", "plotsquared", "tab", "spark", "proantitab"
    };
    private static final Set<String> SCANNER_AUTOCOMPLETE_BLOCKLIST = Set.of(
        "aliases", "bukkit:aliases", "icanhasbukkit", "plugins", "plugin", "pl",
        "version", "ver", "about", "help", "?", "bukkit:plugins", "bukkit:pl",
        "bukkit:version", "bukkit:ver", "bukkit:help", "minecraft:help"
    );
    private static final String ROOT_PROBE_PREFIXES = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Map<String, String> ROOT_COMMAND_PLUGIN_ALIASES = Map.ofEntries(
        Map.entry("lp", "luckperms"),
        Map.entry("we", "worldedit"),
        Map.entry("rg", "worldguard"),
        Map.entry("mv", "multiverse-core"),
        Map.entry("npc", "citizens"),
        Map.entry("papi", "placeholderapi"),
        Map.entry("cmi", "cmi"),
        Map.entry("co", "coreprotect"),
        Map.entry("grim", "grimac"),
        Map.entry("geyser", "geysermc"),
        Map.entry("floodgate", "floodgate"),
        Map.entry("viaver", "viaversion"),
        Map.entry("sr", "skinsrestorer"),
        Map.entry("authme", "authme"),
        Map.entry("authmereloaded", "authme"),
        Map.entry("librelogin", "librelogin"),
        Map.entry("nlogin", "nlogin"),
        Map.entry("openlogin", "openlogin"),
        Map.entry("limboauth", "limboauth"),
        Map.entry("loginsecurity", "loginsecurity"),
        Map.entry("fastlogin", "fastlogin"),
        Map.entry("bungeeguard", "bungeeguard"),
        Map.entry("dm", "deluxemenus"),
        Map.entry("plots", "plotsquared"),
        Map.entry("sv", "supervanish"),
        Map.entry("spawn", "essentialsx"),
        Map.entry("home", "essentialsx"),
        Map.entry("homes", "essentialsx"),
        Map.entry("warp", "essentialsx"),
        Map.entry("warps", "essentialsx"),
        Map.entry("tpa", "essentialsx"),
        Map.entry("tpahere", "essentialsx"),
        Map.entry("bal", "essentialsx"),
        Map.entry("balance", "essentialsx"),
        Map.entry("money", "essentialsx"),
        Map.entry("eco", "essentialsx"),
        Map.entry("ban", "essentialsx"),
        Map.entry("kick", "essentialsx"),
        Map.entry("mute", "essentialsx"),
        Map.entry("jail", "essentialsx"),
        Map.entry("seen", "essentialsx"),
        Map.entry("ptime", "essentialsx"),
        Map.entry("pweather", "essentialsx"),
        Map.entry("tppos", "essentialsx"),
        Map.entry("near", "essentialsx"),
        Map.entry("back", "essentialsx"),
        Map.entry("afk", "essentialsx"),
        Map.entry("msg", "essentialsx"),
        Map.entry("r", "essentialsx"),
        Map.entry("reply", "essentialsx"),
        Map.entry("mail", "essentialsx"),
        Map.entry("pay", "essentialsx"),
        Map.entry("sell", "essentialsx"),
        Map.entry("worth", "essentialsx"),
        Map.entry("kit", "essentialsx"),
        Map.entry("kits", "essentialsx"),
        Map.entry("lb", "litebans"),
        Map.entry("litebans", "litebans"),
        Map.entry("ab", "advancedban"),
        Map.entry("advancedban", "advancedban"),
        Map.entry("tab", "tab"),
        Map.entry("pat", "proantitab"),
        Map.entry("proantitab", "proantitab"),
        Map.entry("spark", "spark"),
        Map.entry("plan", "plan"),
        Map.entry("votingplugin", "votingplugin"),
        Map.entry("vote", "votingplugin"),
        Map.entry("votes", "votingplugin"),
        Map.entry("jobs", "jobsreborn"),
        Map.entry("mcmmo", "mcmmo"),
        Map.entry("towny", "towny"),
        Map.entry("f", "factions"),
        Map.entry("factions", "factions"),
        Map.entry("lands", "lands"),
        Map.entry("res", "residence"),
        Map.entry("residence", "residence"),
        Map.entry("qs", "quickshop"),
        Map.entry("quickshop", "quickshop"),
        Map.entry("auctionhouse", "auctionhouse"),
        Map.entry("crazycrates", "crazycrates"),
        Map.entry("excellentcrates", "excellentcrates"),
        Map.entry("goldencrates", "goldencrates"),
        Map.entry("cratereloaded", "cratereloaded"),
        Map.entry("magiccrates", "magiccrates"),
        Map.entry("simplelootcrates", "simplelootcrates"),
        Map.entry("cratesplus", "cratesplus"),
        Map.entry("cratekeys", "cratekeys"),
        Map.entry("casino", "casino"),
        Map.entry("casinoslots", "casinoslots"),
        Map.entry("slotmachine", "slotmachine"),
        Map.entry("coinflip", "coinflip"),
        Map.entry("jackpot", "jackpot"),
        Map.entry("lottery", "lottery"),
        Map.entry("mythicmobs", "mythicmobs"),
        Map.entry("mm", "mythicmobs"),
        Map.entry("mmoitems", "mmoitems"),
        Map.entry("denizen", "denizen"),
        Map.entry("sentinel", "sentinel"),
        Map.entry("vulcan", "vulcan"),
        Map.entry("matrix", "matrix"),
        Map.entry("karhu", "karhu"),
        Map.entry("verus", "verus"),
        Map.entry("ncp", "nocheatplus")
    );

    private static final Map<String, String> COMMAND_FEATURE_LABELS = Map.ofEntries(
        Map.entry("shop", "Shop"),
        Map.entry("shops", "Shop"),
        Map.entry("store", "Shop"),
        Map.entry("stores", "Shop"),
        Map.entry("market", "Market"),
        Map.entry("marketplace", "Market"),
        Map.entry("ah", "Auction"),
        Map.entry("auction", "Auction"),
        Map.entry("auctions", "Auction"),
        Map.entry("crate", "Crates"),
        Map.entry("crates", "Crates"),
        Map.entry("key", "Crates"),
        Map.entry("keys", "Crates"),
        Map.entry("case", "Crates"),
        Map.entry("cases", "Crates"),
        Map.entry("box", "Crates"),
        Map.entry("boxes", "Crates"),
        Map.entry("lootbox", "Crates"),
        Map.entry("lootboxes", "Crates"),
        Map.entry("cratekey", "Crates"),
        Map.entry("cratekeys", "Crates"),
        Map.entry("votekey", "Crates"),
        Map.entry("votekeys", "Crates"),
        Map.entry("casino", "Gambling"),
        Map.entry("slot", "Gambling"),
        Map.entry("slots", "Gambling"),
        Map.entry("slotmachine", "Gambling"),
        Map.entry("roulette", "Gambling"),
        Map.entry("coinflip", "Gambling"),
        Map.entry("cf", "Gambling"),
        Map.entry("jackpot", "Gambling"),
        Map.entry("lottery", "Gambling"),
        Map.entry("lotto", "Gambling"),
        Map.entry("raffle", "Gambling"),
        Map.entry("dice", "Gambling"),
        Map.entry("blackjack", "Gambling"),
        Map.entry("poker", "Gambling"),
        Map.entry("crash", "Gambling"),
        Map.entry("plinko", "Gambling"),
        Map.entry("casebattle", "Gambling"),
        Map.entry("casebattles", "Gambling"),
        Map.entry("wheel", "Gambling"),
        Map.entry("spin", "Gambling"),
        Map.entry("login", "Auth"),
        Map.entry("register", "Auth"),
        Map.entry("reg", "Auth"),
        Map.entry("logout", "Auth"),
        Map.entry("unregister", "Auth"),
        Map.entry("changepassword", "Auth"),
        Map.entry("changepw", "Auth"),
        Map.entry("captcha", "Auth"),
        Map.entry("2fa", "Auth"),
        Map.entry("totp", "Auth"),
        Map.entry("email", "Auth"),
        Map.entry("verify", "Auth"),
        Map.entry("verification", "Auth"),
        Map.entry("premium", "Auth"),
        Map.entry("cracked", "Auth"),
        Map.entry("backpack", "Backpacks"),
        Map.entry("backpacks", "Backpacks"),
        Map.entry("bp", "Backpacks"),
        Map.entry("pv", "Player Vaults"),
        Map.entry("playervault", "Player Vaults"),
        Map.entry("playervaults", "Player Vaults"),
        Map.entry("vaults", "Player Vaults"),
        Map.entry("enderchest", "Ender Chest"),
        Map.entry("echest", "Ender Chest"),
        Map.entry("ec", "Ender Chest"),
        Map.entry("shulker", "Shulker"),
        Map.entry("shulkers", "Shulker"),
        Map.entry("minion", "Minions"),
        Map.entry("minions", "Minions"),
        Map.entry("mine", "Mines"),
        Map.entry("mines", "Mines"),
        Map.entry("kit", "Kits"),
        Map.entry("kits", "Kits")
    );

    private static final Set<String> ANTICHEATS = Set.of(
        "nocheatplus", "aac", "spartan", "matrix", "vulcan", "grim",
        "grimac", "intave", "karhu", "verus", "polar", "negativity",
        "themis", "fairfight", "wraith", "horizon", "reflex", "antiaura",
        "guardian", "hac", "thotpatrol", "alice"
    );

    private static final Set<String> VANILLA_NAMESPACES = Set.of(
        "minecraft", "brigadier", "bukkit", "spigot", "paper", "purpur",
        "velocity", "bungeecord", "waterfall"
    );

    private static final Set<String> VANILLA_COMMAND_ROOTS = Set.of(
        "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear",
        "clone", "damage", "data", "datapack", "debug", "defaultgamemode",
        "deop", "difficulty", "effect", "enchant", "execute", "experience", "xp",
        "fill", "fillbiome", "forceload", "function", "gamemode", "gamerule",
        "give", "help", "item", "jfr", "kick", "kill", "list", "locate", "loot",
        "me", "msg", "op", "pardon", "pardon-ip", "particle", "perf", "place",
        "playsound", "publish", "random", "recipe", "reload", "return", "ride",
        "rotate", "save-all", "save-off", "save-on", "say", "schedule", "scoreboard",
        "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint", "spectate",
        "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg",
        "tm", "teleport", "tell", "tellraw", "tick", "time", "title", "tp",
        "transfer", "trigger", "w", "weather", "whitelist", "worldborder"
    );

    private static final class TabStripNode extends DirectUiNode {
        private static final int GAP = 2;
        private String[] tabs = new String[0];
        private int activeIndex = 0;
        private IntConsumer onSelect;

        TabStripNode setTabs(String... tabs) {
            this.tabs = tabs == null ? new String[0] : tabs.clone();
            setActiveIndex(activeIndex);
            markLayoutDirty();
            return this;
        }

        TabStripNode setActiveIndex(int activeIndex) {
            this.activeIndex = Math.max(0, Math.min(Math.max(0, tabs.length - 1), activeIndex));
            return this;
        }

        TabStripNode setOnSelect(IntConsumer onSelect) {
            this.onSelect = onSelect;
            return this;
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return Math.max(14, context.theme().buttonHeight());
        }

        @Override
        public void render(DirectRenderContext context) {
            if (!visible || tabs.length == 0) return;
            UiContext uiContext = UiContexts.overlay(context.drawContext(), context.textRenderer(),
                Math.round(context.mouseX()), Math.round(context.mouseY()));
            for (int i = 0; i < tabs.length; i++) {
                UiBounds bounds = tabBounds(i);
                boolean active = i == activeIndex;
                TabStrip.renderTab(uiContext, bounds, tabs[i], active);
            }
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if (!visible || !enabled || button != 0 || tabs.length == 0) return false;
            for (int i = 0; i < tabs.length; i++) {
                if (tabBounds(i).contains(Math.round(mouseX), Math.round(mouseY))) {
                    if (i != activeIndex) {
                        setActiveIndex(i);
                        if (onSelect != null) onSelect.accept(i);
                    }
                    return true;
                }
            }
            return false;
        }

        private UiBounds tabBounds(int index) {
            return TabStrip.tabBounds(UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height)), tabs.length, index, GAP);
        }
    }

    private static final class SliderNode extends DirectUiNode {
        private float min = 0.0f;
        private float max = 1.0f;
        private float value = 0.0f;
        private float step = 0.0f;
        private boolean dragging = false;
        private Consumer<Float> onChange;

        SliderNode() {
            this.height = 12;
        }

        SliderNode setRange(float min, float max) {
            this.min = min;
            this.max = Math.max(min, max);
            return setValue(value);
        }

        SliderNode setStep(float step) {
            this.step = Math.max(0.0f, step);
            return setValue(value);
        }

        SliderNode setValue(float value) {
            float snapped = snap(clamp(value));
            if (Math.abs(this.value - snapped) > 0.0001f) {
                this.value = snapped;
                if (onChange != null) onChange.accept(this.value);
            }
            return this;
        }

        SliderNode setOnChange(Consumer<Float> onChange) {
            this.onChange = onChange;
            return this;
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return 12.0f;
        }

        @Override
        public void render(DirectRenderContext context) {
            if (!visible) return;
            UiBounds bounds = UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
            UiContext uiContext = UiContexts.overlay(context.drawContext(), context.textRenderer(),
                Math.round(context.mouseX()), Math.round(context.mouseY()));
            Slider.render(uiContext, bounds, Slider.ratio(value, min, max), contains(context.mouseX(), context.mouseY()) || dragging);
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if (button != 0 || !contains(mouseX, mouseY)) return false;
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }

        @Override
        public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if (button == 0 && dragging) {
                dragging = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
            if (!dragging || button != 0) return false;
            updateFromMouse(mouseX);
            return true;
        }

        private void updateFromMouse(float mouseX) {
            setValue((float) Slider.valueFromMouse(mouseX, Math.round(x), Math.round(width), min, max, step <= 0.0f ? 0.0001f : step));
        }

        private float clamp(float value) {
            return Math.max(min, Math.min(max, value));
        }

        private float snap(float value) {
            if (step <= 0.0f) return value;
            return min + Math.round((value - min) / step) * step;
        }
    }

    private static final class ProgressBarNode extends DirectUiNode {
        private float progress = 0.0f;

        ProgressBarNode() {
            this.height = 12;
        }

        ProgressBarNode setProgress(float progress) {
            this.progress = Math.max(0.0f, Math.min(1.0f, progress));
            return this;
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return 12.0f;
        }

        @Override
        public void render(DirectRenderContext context) {
            if (!visible) return;
            ProgressBar.render(UiContexts.overlay(context.drawContext(), context.textRenderer(),
                    Math.round(context.mouseX()), Math.round(context.mouseY())),
                UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height)), progress);
        }
    }

    private static final class PluginScanEntry {
        String displayName;
        boolean commandBacked;
        PluginResultKind resultKind;
        PluginEvidence evidence = PluginEvidence.UNKNOWN;
        PluginEvidence copyEvidence = PluginEvidence.UNKNOWN;
        PluginConfidence confidence = PluginConfidence.FEATURE;
        final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> copyCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> channels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private enum PluginResultKind {
        PLUGIN,
        FEATURE
    }

    private enum PluginConfidence {
        EXACT,
        STRONG,
        FEATURE,
        UNKNOWN
    }

    private enum PluginEvidence {
        PAYLOAD_CHANNEL,
        COMMAND_GUI,
        COMMAND_TREE,
        USER_AUTOCOMPLETE,
        SCANNER_AUTOCOMPLETE,
        NAMESPACE,
        ROOT_HINT,
        HELP_HINT,
        PLUGIN_LIST,
        VERSION_HINT,
        FEATURE,
        UNKNOWN
    }

    private enum PluginProbeKind {
        ROOT,
        HELP,
        PLUGIN_LIST,
        VERSION,
        NAMESPACE,
        USER
    }

    private enum PluginScanOutcome {
        NONE,
        COMPLETE,
        PARTIAL,
        FAILED
    }

    private static final class PluginProbeSpec {
        final String query;
        final PluginProbeKind kind;
        final String hint;

        PluginProbeSpec(String query, PluginProbeKind kind, String hint) {
            this.query = query;
            this.kind = kind;
            this.hint = hint;
        }
    }

    private static final class PluginProbeRequest {
        final int id;
        final PluginProbeSpec spec;
        final int attempt;

        PluginProbeRequest(int id, PluginProbeSpec spec) {
            this(id, spec, 0);
        }

        PluginProbeRequest(int id, PluginProbeSpec spec, int attempt) {
            this.id = id;
            this.spec = spec;
            this.attempt = attempt;
        }
    }

    private static final class PluginScanSession {
        final long generation;
        final String serverAddress;
        final AutismPluginScanJobState lifecycle;
        String contextSignature;
        final long startedAt;
        final Map<String, PluginScanEntry> previousEntries;
        long lastResponseAt;
        long nextSendAt;
        final Set<Integer> pendingProbeIds = new HashSet<>();
        final Map<Integer, PluginProbeSpec> probes = new HashMap<>();
        final Deque<PluginProbeRequest> queuedProbes = new ArrayDeque<>();
        int totalProbes;
        int sentProbes;
        int answeredProbes;
        int retriedProbes;
        int failedProbes;
        int runtimeErrors;
        int commandTreeRoots;
        int suggestionReplies;
        int suggestionEntries;

        PluginScanSession(long generation, String serverAddress, String contextSignature, long startedAt,
                          Map<String, PluginScanEntry> previousEntries) {
            this.generation = generation;
            this.serverAddress = serverAddress == null ? "" : serverAddress;
            this.lifecycle = new AutismPluginScanJobState(generation, this.serverAddress);
            this.contextSignature = contextSignature == null ? "" : contextSignature;
            this.startedAt = startedAt;
            this.previousEntries = previousEntries == null
                ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(previousEntries));
            this.lastResponseAt = startedAt;
            this.nextSendAt = startedAt;
        }

        boolean matches(long expectedGeneration, String address) {
            return lifecycle.matches(expectedGeneration, address);
        }
    }

    private record ObservedCommand(String root, String fullCommand, long timestampMs) {
    }

    private enum PluginRowType {
        HEADER,
        PLUGIN,
        COMMAND,
        GUI,
        CHANNEL,
        WHY
    }

    private enum RadarRowType {
        HEADER,
        MATCH,
        FINDING,
        EMPTY
    }

    private static final class RadarListRow {
        final RadarRowType type;
        final String title;
        final AutismDupeRadar.RadarMatch match;
        final AutismDupeRadar.RadarFinding finding;
        final int height;
        final String searchText;

        private RadarListRow(RadarRowType type, String title, AutismDupeRadar.RadarMatch match,
                             AutismDupeRadar.RadarFinding finding, int height, String searchText) {
            this.type = type == null ? RadarRowType.EMPTY : type;
            this.title = title == null ? "" : title;
            this.match = match;
            this.finding = finding;
            this.height = Math.max(1, height);
            this.searchText = searchText == null ? "" : searchText;
        }

        static RadarListRow header(String title, int height) {
            return new RadarListRow(RadarRowType.HEADER, title, null, null, height, lower(title));
        }

        static RadarListRow match(AutismDupeRadar.RadarMatch match, int height, String searchText) {
            return new RadarListRow(RadarRowType.MATCH, "", match, null, height, searchText);
        }

        static RadarListRow finding(AutismDupeRadar.RadarMatch match, AutismDupeRadar.RadarFinding finding, int height, String searchText) {
            return new RadarListRow(RadarRowType.FINDING, "", match, finding, height, searchText);
        }

        static RadarListRow empty(String text, int height) {
            return new RadarListRow(RadarRowType.EMPTY, text, null, null, height, lower(text));
        }

        private static String lower(String text) {
            return text == null ? "" : text.toLowerCase(Locale.ROOT);
        }
    }

    private static final class PluginListRow {
        final PluginRowType type;
        final String title;
        final String plugin;
        final String label;
        final String actionCommand;
        final PluginResultKind resultKind;
        final PluginEvidence evidence;
        final PluginConfidence confidence;
        final int height;
        final String countLabel;
        final String searchText;

        private PluginListRow(PluginRowType type, String title, String plugin, String label, String actionCommand,
                              PluginResultKind resultKind, PluginEvidence evidence, PluginConfidence confidence,
                              int height, String countLabel, String searchText) {
            this.type = type;
            this.title = title;
            this.plugin = plugin;
            this.label = label;
            this.actionCommand = actionCommand;
            this.resultKind = resultKind == null ? PluginResultKind.PLUGIN : resultKind;
            this.evidence = evidence == null ? PluginEvidence.UNKNOWN : evidence;
            this.confidence = confidence == null ? PluginConfidence.UNKNOWN : confidence;
            this.height = Math.max(1, height);
            this.countLabel = countLabel == null ? "" : countLabel;
            this.searchText = searchText == null ? "" : searchText;
        }

        static PluginListRow header(String title, int height) {
            return new PluginListRow(PluginRowType.HEADER, title, null, title, null,
                PluginResultKind.PLUGIN, PluginEvidence.UNKNOWN, PluginConfidence.UNKNOWN, height, "", lower(title));
        }

        static PluginListRow plugin(String plugin, PluginResultKind resultKind, PluginEvidence evidence,
                                    PluginConfidence confidence, int height, String countLabel, String searchText) {
            return new PluginListRow(PluginRowType.PLUGIN, null, plugin, plugin, null,
                resultKind, evidence, confidence, height, countLabel, searchText);
        }

        static PluginListRow detail(PluginRowType type, String plugin, String label, String actionCommand,
                                    PluginEvidence evidence, PluginConfidence confidence, int height, String searchText) {
            return new PluginListRow(type, null, plugin, label, actionCommand,
                PluginResultKind.PLUGIN, evidence, confidence, height, "", searchText);
        }

        boolean header() {
            return type == PluginRowType.HEADER;
        }

        private static String lower(String text) {
            return text == null ? "" : text.toLowerCase(Locale.ROOT);
        }
    }

    private static final class PluginDetail {
        final String displayName;
        final String key;
        final List<String> commands;
        final List<String> guis;
        final List<String> channels;
        final PluginResultKind resultKind;
        final PluginEvidence evidence;
        final PluginEvidence copyEvidence;
        final PluginConfidence confidence;
        final String sourceLabel;
        final String countLabel;
        final List<String> copyCommands;
        final String searchText;

        PluginDetail(String displayName, String key, List<String> commands, List<String> guis, List<String> channels,
                     PluginResultKind resultKind, PluginEvidence evidence, PluginEvidence copyEvidence,
                     PluginConfidence confidence, String sourceLabel, String countLabel,
                     List<String> copyCommands, String searchText) {
            this.displayName = displayName == null ? "" : displayName;
            this.key = key == null ? "" : key;
            this.commands = commands == null ? List.of() : commands;
            this.guis = guis == null ? List.of() : guis;
            this.channels = channels == null ? List.of() : channels;
            this.resultKind = resultKind == null ? PluginResultKind.PLUGIN : resultKind;
            this.evidence = evidence == null ? PluginEvidence.UNKNOWN : evidence;
            this.copyEvidence = copyEvidence == null ? PluginEvidence.UNKNOWN : copyEvidence;
            this.confidence = confidence == null ? PluginConfidence.UNKNOWN : confidence;
            this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
            this.countLabel = countLabel == null ? "" : countLabel;
            this.copyCommands = copyCommands == null ? List.of() : copyCommands;
            this.searchText = searchText == null ? "" : searchText;
        }
    }

    private static final class PluginDetailBuilder {
        String displayName;
        final Set<String> commands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> copyCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> guis = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final List<String> channels = new ArrayList<>();
    }

    private static class ClickRegion {
        final int x, y, width, height;
        final Runnable action;
        ClickRegion(int x, int y, int width, int height, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.action = action;
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    public AutismServerInfoOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        buildUi();
    }

    public void setConfigurationOnly(boolean configurationOnly) {
        if (this.configurationOnly == configurationOnly) return;
        this.configurationOnly = configurationOnly;
        if (configurationOnly) cancelActivePluginScan("main menu");
        requestUiRebuild();
    }

    private void buildUi() {
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(panelPadding() + 1, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 12);
        windowNode.content().setGap(contentGap()).setPadding(DirectUiInsets.all(panelPadding()));

        tabStrip.setTabs(TAB_NAMES).setActiveIndex(activeTab).setOnSelect(this::selectTab);
        searchField
            .setPlaceholder("Search plugins...")
            .setPreferredWidth(pluginSearchWidth())
            .setFieldHeight(searchFieldHeight())
            .setOnChange(text -> {
                pluginScrollState.jumpTo(0, 0);
                requestUiRebuild();
            });
        radarSearchField
            .setPlaceholder("Search radar...")
            .setPreferredWidth(pluginSearchWidth())
            .setFieldHeight(searchFieldHeight())
            .setOnChange(text -> {
                radarScrollState.jumpTo(0, 0);
                requestUiRebuild();
            });
        probeDelaySlider
            .setRange(MIN_PLUGIN_PROBE_DELAY_MS, MAX_PLUGIN_PROBE_DELAY_MS)
            .setStep(10.0f)
            .setValue(pluginProbeDelayMs)
            .setOnChange(value -> setPluginProbeDelayMs(Math.round(value)));
        scanProgressBar.setProgress(0.0f);
    }

    public void saveState() {
        AutismSharedState shared = AutismSharedState.get();
        rememberCurrentTabSize();
        String stateAddress = currentServerAddress();
        if (stateAddress.isEmpty() && scannedServerAddress != null) stateAddress = scannedServerAddress;
        shared.setServerDataOverlayActiveTab(activeTab);
        shared.setServerDataOverlayPluginScrollOffset(pluginScrollState.targetOffset());
        shared.setServerDataOverlaySelectedPlugin(selectedPlugin);
        shared.setServerDataOverlayStateAddress(stateAddress);
        shared.setServerDataOverlayProbeDelayMs(pluginProbeDelayMs);
        shared.setServerDataOverlayInfoWidth(infoPreferredWidth);
        shared.setServerDataOverlayInfoHeight(infoPreferredHeight);
        shared.setServerDataOverlayPluginWidth(pluginPreferredWidth);
        shared.setServerDataOverlayPluginHeight(pluginPreferredHeight);
        cacheCurrentScan();
        saveLayout();
    }

    public void restoreState() {
        AutismSharedState shared = AutismSharedState.get();
        restoreLayout();
        activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, shared.getServerDataOverlayActiveTab()));
        pluginProbeDelayMs = Math.max(MIN_PLUGIN_PROBE_DELAY_MS, Math.min(MAX_PLUGIN_PROBE_DELAY_MS, shared.getServerDataOverlayProbeDelayMs()));
        infoPreferredWidth = Math.max(infoMinWidth(), shared.getServerDataOverlayInfoWidth());
        infoPreferredHeight = Math.max(infoMinHeight(), shared.getServerDataOverlayInfoHeight());
        pluginPreferredWidth = Math.max(pluginResultsMinWidth(), shared.getServerDataOverlayPluginWidth());
        pluginPreferredHeight = Math.max(pluginResultsMinHeightPreset(), shared.getServerDataOverlayPluginHeight());
        syncScanStateForCurrentServer();
        String stateAddress = currentServerAddress();
        if (stateAddress.isEmpty() && scannedServerAddress != null) stateAddress = scannedServerAddress;
        if (!stateAddress.isEmpty() && stateAddress.equals(shared.getServerDataOverlayStateAddress())) {
            pluginScrollState.restore(shared.getServerDataOverlayPluginScrollOffset());
            selectedPlugin = shared.getServerDataOverlaySelectedPlugin();
            if (selectedPlugin != null && selectedPlugin.isBlank()) selectedPlugin = null;
            if (selectedPlugin != null && !hasDetectedPlugin(selectedPlugin)) selectedPlugin = null;
        } else {
            pluginScrollState.jumpTo(0, 0);
            selectedPlugin = null;
        }
        if (activeTab == 0) {
            applyInfoLayout();
        } else if (activeTab == 2) {
            applyRadarLayout();
        } else {
            if (pluginScanInProgress) applyPluginScanningLayout();
            else if (!pluginScanDone) applyPluginSetupLayout();
        }
        tabStrip.setActiveIndex(activeTab);
        probeDelaySlider.setValue(pluginProbeDelayMs);
    }

    public static boolean shouldRestoreSavedVisible() {
        AutismWindowLayout layout = AutismSharedState.get().getWindowLayout(OVERLAY_ID);
        return layout != null && layout.visible;
    }

    private void selectTab(int tabIdx) {
        rememberCurrentTabSize();
        activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, tabIdx));
        tabStrip.setActiveIndex(activeTab);
        pluginScrollState.jumpTo(0, 0);
        radarScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        if (activeTab == 0) {
            applyInfoLayout();
        } else if (activeTab == 2) {
            applyRadarLayout();
        } else if (pluginScanInProgress) {
            applyPluginScanningLayout();
        } else if (!pluginScanDone) {
            applyPluginSetupLayout();
        } else {
            applyPluginResultsLayout();
        }
        saveState();
    }

    private void rebuildUi() {
        uiRebuildRequested = false;
        windowNode.content().clearChildren();
        tabStrip.setActiveIndex(activeTab);
        probeDelaySlider.setValue(pluginProbeDelayMs);
        scanProgressBar.setProgress(getPluginScanProgress());

        if (!pluginScanInProgress || activeTab == 2) {
            windowNode.content().add(tabStrip);
        }

        if (activeTab == 0) {
            buildInfoUi();
            return;
        }

        if (activeTab == 2) {
            buildRadarUi();
            return;
        }

        if (!pluginScanDone && !pluginScanInProgress) {
            buildPluginSetupUi();
            return;
        }

        if (pluginScanInProgress) {
            buildPluginScanningUi();
            return;
        }

        buildPluginResultsUi();
    }

    private void requestUiRebuild() {
        uiRebuildRequested = true;
    }

    private void buildInfoUi() {
        ServerData entry = MC.getCurrentServer();
        String displayedAddress = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(displayedAddress);
        String software = getSoftwareGuess(entry);
        String versionNote = getVersionNote(entry);
        String ping = entry != null ? entry.ping + " ms" : "--";
        String players = "--";
        if (MC.getConnection() != null) {
            int online = MC.getConnection().getListedOnlinePlayers().size();
            players = entry != null && entry.players != null ? online + " / " + entry.players.max() : String.valueOf(online);
        }
        String proto = entry != null ? String.valueOf(entry.protocol) : "--";
        String diff = MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--";
        String time = "--";
        if (MC.level != null) {
            long dayCount = MC.level.getOverworldClockTime() / 24000L;
            long timeOfDay = MC.level.getOverworldClockTime() % 24000L;
            int hours = (int) ((timeOfDay / 1000 + 6) % 24);
            int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
            time = "Day " + dayCount + " (" + String.format("%02d:%02d", hours, minutes) + ")";
        }
        double estimatedTps = AutismSharedState.get().getEstimatedTps();
        String tps = estimatedTps > 0 ? String.format("%.1f", Math.min(20.0, estimatedTps)) : "--";

        addInfoRow("IP:", displayedAddress, null, () -> copyClipboardValue(displayedAddress, "Server address copied.", "Server address unavailable."));
        String host = extractLookupHost(displayedAddress);
        if (!host.isBlank() && (!host.equals(realIp) || resolvingIp)) {
            addInfoRow("Real IP:", realIp, null, this::copyResolvedServerIp);
        }
        addInfoRow("Version:", getReportedVersion(entry), null);
        addInfoRow("Real Version:", getRealServerVersion(), null);
        addInfoRow("Brand:", getLiveBrand(), null);
        if (!"--".equals(software)) addInfoRow("Software:", software, null);
        addInfoRow("Ping:", ping, null);
        addInfoRow("Players:", players, null);
        addInfoRow("Protocol:", proto, null);
        if (!"--".equals(versionNote)) addInfoRow("Version Note:", versionNote, AutismColors.packetYellow());
        addInfoRow("Difficulty:", diff, null);
        addInfoRow("World:", getCurrentWorldName(), null);
        addInfoRow("Time:", time, null);
        addInfoRow("TPS:", tps, null);

        List<String> detectedAcs = getDetectedAnticheats();
        if (pluginScanInProgress) addInfoRow("AntiCheats:", "Scanning...", AutismTheme.recolor(0xFFFF4444, AutismTheme.Channel.DANGER));
        else if (!pluginScanDone) addInfoRow("AntiCheats:", "Probe Plugins First", AutismTheme.recolor(0xFFFF4444, AutismTheme.Channel.DANGER));
        else if (detectedAcs.isEmpty()) addInfoRow("AntiCheats:", "None detected", null);
        else addInfoRow("AntiCheats:", String.join(", ", detectedAcs), AutismTheme.recolor(0xFFFF4444, AutismTheme.Channel.DANGER));

        windowNode.content().add(new DirectSpacer(0, 2));
        windowNode.content().add(new DirectUiButton("Copy Report", DirectUiButton.Variant.SECONDARY, this::copyServerData).setGrowX(true).setButtonHeight(actionButtonHeight()));
    }

    private void addInfoRow(String label, String value, Integer valueColor) {
        addInfoRow(label, value, valueColor, null);
    }

    private void addInfoRow(String label, String value, Integer valueColor, Runnable onPress) {
        windowNode.content().add(
            new DirectInfoRow(label, value)
                .setLabelWidth(infoLabelWidth())
                .setValueColorOverride(valueColor)
                .setOnPress(onPress)
        );
    }

    private void buildPluginSetupUi() {
        windowNode.content().add(autoProbeToggleButton());
        windowNode.content().add(new DirectUiLabel("Auto-Scans on join.", UiTone.MUTED));
        windowNode.content().add(new DirectSpacer(0, 2));
        windowNode.content().add(new DirectUiLabel("Start probing manually", UiTone.MUTED));
        windowNode.content().add(new DirectInfoRow("Delay:", pluginProbeDelayMs + " ms").setLabelWidth(pluginSetupLabelWidth()));
        windowNode.content().add(probeDelaySlider);
        windowNode.content().add(new DirectUiLabel("Default: " + DEFAULT_PLUGIN_PROBE_DELAY_MS + " ms", UiTone.MUTED));
        windowNode.content().add(new DirectUiLabel("If you get kicked increase the delay.", UiTone.MUTED));
        DirectUiButton startProbe = new DirectUiButton(configurationOnly ? "Join a Server to Probe" : "Start Probing",
            DirectUiButton.Variant.SECONDARY, this::startManualPluginScan);
        startProbe.setGrowX(true).setButtonHeight(actionButtonHeight());
        startProbe.setEnabled(!configurationOnly);
        windowNode.content().add(startProbe);
        if (!detectedPlugins.isEmpty()) {
            windowNode.content().add(new DirectUiButton("Back to Results", DirectUiButton.Variant.SECONDARY, () -> {
                pluginManualSetupOpen = false;
                pluginScanDone = true;
                pluginScanInProgress = false;
                applyPluginResultsLayout();
                saveState();
            }).setGrowX(true).setButtonHeight(actionButtonHeight()));
        }
    }

    private void buildPluginScanningUi() {
        int progressPercent = Math.max(0, Math.min(100, Math.round(getPluginScanProgress() * 100.0f)));
        PluginScanSession session = activePluginScan;
        windowNode.content().add(new DirectInfoRow("State:", getPluginScanPhaseLabel()).setLabelWidth(pluginScanLabelWidth()).setValueColorOverride(getPluginScanPhaseColor()));
        windowNode.content().add(new DirectInfoRow("Sent:", getSentPluginProbeCount() + "/" + Math.max(1, getTotalPluginProbeCount())).setLabelWidth(pluginScanLabelWidth()));
        windowNode.content().add(new DirectInfoRow("Replies:", getAnsweredPluginProbeCount() + "/" + Math.max(1, getTotalPluginProbeCount())).setLabelWidth(pluginScanLabelWidth()));
        windowNode.content().add(new DirectInfoRow("Retries:", Integer.toString(session == null ? 0 : session.retriedProbes)).setLabelWidth(pluginScanLabelWidth()));
        windowNode.content().add(new DirectInfoRow("Failed:", Integer.toString(session == null ? 0 : session.failedProbes)).setLabelWidth(pluginScanLabelWidth()));
        int foundCount = getDisplayedPluginCount();
        windowNode.content().add(new DirectInfoRow("Found:", foundCount + " plugin" + (foundCount == 1 ? "" : "s")).setLabelWidth(pluginScanLabelWidth()));
        windowNode.content().add(new DirectInfoRow("Progress:", progressPercent + "%").setLabelWidth(pluginScanLabelWidth()).setValueColorOverride(getPluginScanPhaseColor()));
        windowNode.content().add(scanProgressBar);
    }

    private void buildPluginResultsUi() {
        windowNode.content().add(autoProbeToggleButton());
        searchField.setPreferredWidth(Math.max(pluginSearchWidth(), panelW - pluginSearchReserveWidth()));
        windowNode.content().add(searchField);

        DirectRow buttons = new DirectRow().setGap(buttonRowGap());
        buttons.add(new DirectUiButton("Rescan", DirectUiButton.Variant.SECONDARY, () -> {
            openPluginProbeSetup();
        }).setGrowX(true).setButtonHeight(actionButtonHeight()));
        buttons.add(new DirectUiButton("Copy Plugins", DirectUiButton.Variant.SECONDARY, this::copyPluginList).setGrowX(true).setButtonHeight(actionButtonHeight()));
        windowNode.content().add(buttons);

        String query = searchField.text().toLowerCase(Locale.ROOT);
        int filteredCount = 0;
        for (PluginDetail detail : getCachedPluginDetails()) {
            if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) filteredCount++;
        }
        String header = detectedPlugins.isEmpty() ? "No plugins detected" : "Detected: " + filteredCount + " plugin" + (filteredCount == 1 ? "" : "s");
        windowNode.content().add(new DirectUiLabel(header, UiTone.MUTED));

        float viewportHeight = Math.max(pluginViewportMinHeight(), panelH - getPluginResultsReservedHeight());
        pluginListSlot.setPreferredHeight(viewportHeight);
        windowNode.content().add(pluginListSlot);
    }

    private void buildRadarUi() {
        AutismDupeRadar.RadarState radarState = AutismDupeRadar.state();
        syncRadarAuthState(radarState);
        int pluginCount = radarPluginSnapshots().size();
        if (!configurationOnly) maybeAutoCheckRadar(radarState, pluginCount);
        radarState = AutismDupeRadar.state();
        syncRadarAuthState(radarState);

        if (!radarState.authenticated()) {
            windowNode.content().add(new DirectUiLabel("Login to continue.", UiTone.MUTED).setTrimToBounds(true));
            if (radarState.error() != null && !radarState.error().isBlank()) {
                windowNode.content().add(new DirectInfoRow("Error:", radarState.error())
                    .setLabelWidth(38)
                    .setValueColorOverride(AutismColors.dangerText()));
            }
            DirectRow loginButtons = new DirectRow().setGap(buttonRowGap());
            loginButtons.add(new DirectUiButton(radarState.authenticating() ? "Login..." : "Login DupeDB", DirectUiButton.Variant.SUCCESS, AutismDupeRadar::login)
                .setGrowX(true)
                .setButtonHeight(actionButtonHeight()));
            windowNode.content().add(loginButtons);
            return;
        }

        windowNode.content().add(new DirectInfoRow("Server:", radarServerLabel()).setLabelWidth(42));
        if (!pluginScanDone) {
            windowNode.content().add(new DirectInfoRow("Alert:", "Plugins not scanned")
                .setLabelWidth(42)
                .setValueColorOverride(AutismColors.dangerText()));
        }

        DirectRow authButtons = new DirectRow().setGap(buttonRowGap());
        authButtons.add(new DirectUiButton("Logout", DirectUiButton.Variant.DANGER, AutismDupeRadar::logout).setGrowX(true).setButtonHeight(actionButtonHeight()));
        windowNode.content().add(authButtons);

        radarSearchField.setPreferredWidth(Math.max(pluginSearchWidth(), panelW - pluginSearchReserveWidth()));
        windowNode.content().add(radarSearchField);

        float viewportHeight = Math.max(pluginViewportMinHeight(), panelH - getRadarResultsReservedHeight(!pluginScanDone));
        radarListSlot.setPreferredHeight(viewportHeight);
        windowNode.content().add(radarListSlot);
    }

    private void syncRadarAuthState(AutismDupeRadar.RadarState state) {
        boolean authenticated = state != null && state.authenticated();
        if (lastRadarAuthenticated != null && lastRadarAuthenticated != authenticated) {
            lastRadarAutoCheckSignature = "";
            lastRadarAutoCheckAt = 0L;
            expandedRadarPlugins.clear();
            radarScrollState.jumpTo(0, 0);
        }
        lastRadarAuthenticated = authenticated;
    }

    private DirectUiButton autoProbeToggleButton() {
        boolean autoOn = AutismConfig.getGlobal().autoProbePlugins;
        return new DirectUiButton(
            "Auto Probe: " + (autoOn ? "ON" : "OFF"),
            autoOn ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.SECONDARY,
            this::toggleAutoProbePlugins
        ).setGrowX(true).setButtonHeight(actionButtonHeight());
    }

    private void toggleAutoProbePlugins() {
        AutismConfig cfg = AutismConfig.getGlobal();
        cfg.autoProbePlugins = !cfg.autoProbePlugins;
        cfg.save();
        lastAutoScanContext = null;
        requestUiRebuild();
        saveState();
    }

    private void openPluginProbeSetup() {
        cancelActivePluginScan("setup");
        pluginScanDone = false;
        pluginManualSetupOpen = true;
        pluginScanCompletionAnnounced = false;
        selectedPlugin = null;
        searchField.setText("");
        searchField.setFocused(false);
        pluginScrollState.jumpTo(0, 0);
        activeTab = 1;
        applyPluginSetupLayout();
        saveState();
    }

    private String currentServerAddress() {
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return (host + ":" + inet.getPort()).trim().toLowerCase(Locale.ROOT);
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private String normalizePluginContextSignature(String signature) {
        return signature == null ? "" : signature.trim().toLowerCase(Locale.ROOT);
    }

    private String currentPluginContextSignature() {
        if (MC.getConnection() == null) return "";

        String currentAddress = currentServerAddress();
        String brand = MC.getConnection().serverBrand();
        String normalizedBrand = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        if (!pluginContextSignatureDirty
            && currentAddress.equals(cachedPluginContextServerAddress)
            && normalizedBrand.equals(cachedPluginContextBrand)) {
            return cachedPluginContextSignature;
        }

        List<String> parts = new ArrayList<>();
        parts.add("scan=" + PLUGIN_SCAN_MODEL_VERSION);
        if (brand != null && !brand.isBlank()) {
            parts.add("brand=" + normalizedBrand);
        }

        try {
            com.mojang.brigadier.CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
            if (dispatcher != null) {
                com.mojang.brigadier.tree.RootCommandNode<?> root = dispatcher.getRoot();
                if (root != null && !root.getChildren().isEmpty()) {
                    List<String> rootCommands = new ArrayList<>();
                    for (com.mojang.brigadier.tree.CommandNode<?> child : root.getChildren()) {
                        String name = child.getName();
                        if (name != null && !name.isBlank()) {
                            rootCommands.add(name.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    rootCommands.sort(String.CASE_INSENSITIVE_ORDER);
                    if (!rootCommands.isEmpty()) {
                        parts.add("cmd=" + String.join(",", rootCommands));
                    }
                }
            }
        } catch (Exception ignored) {  }

        cachedPluginContextServerAddress = currentAddress;
        cachedPluginContextBrand = normalizedBrand;
        cachedPluginContextSignature = normalizePluginContextSignature(String.join("|", parts));
        pluginContextSignatureDirty = false;
        return cachedPluginContextSignature;
    }

    private void invalidatePluginContextSignature() {
        pluginContextSignatureDirty = true;
    }

    private void setPluginProbeDelayMs(int delayMs) {
        pluginProbeDelayMs = Math.max(MIN_PLUGIN_PROBE_DELAY_MS, Math.min(MAX_PLUGIN_PROBE_DELAY_MS, delayMs));
        AutismSharedState.get().setServerDataOverlayProbeDelayMs(pluginProbeDelayMs);
    }

    private void applyClampedBounds() {
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;
    }

    private void applyPluginSetupLayout() {
        panelW = getSharedPanelWidth();
        panelH = getPluginSetupPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyPluginScanningLayout() {
        panelW = getSharedPanelWidth();
        panelH = getPluginScanningPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyPluginResultsLayout() {
        panelW = getSharedPanelWidth();
        panelH = Math.max(getPluginResultsMinHeight(), Math.min(getSharedPanelHeight(), getPluginResultsMaxHeight()));
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyRadarLayout() {
        panelW = getSharedPanelWidth();
        panelH = getRadarPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private void applyInfoLayout() {
        panelW = getSharedPanelWidth();
        panelH = getSharedPanelHeight();
        applyClampedBounds();
        requestUiRebuild();
    }

    private int getSharedPanelWidth() {
        DirectViewport viewport = surface.viewport();
        return Math.max(infoMinWidth(), Math.min(sharedPanelWidth(), Math.round(viewport.uiWidth())));
    }

    private int getPluginResultsMinHeight() {
        return getSharedPanelHeight();
    }

    private int getPluginResultsMaxHeight() {
        DirectViewport viewport = surface.viewport();
        int viewportHeight = Math.round(viewport.uiHeight());
        return Math.max(getPluginResultsMinHeight(), viewportHeight - 28);
    }

    private int getRadarResultsMinHeight() {
        return Math.max(RADAR_RESULTS_MIN_HEIGHT, getSharedPanelHeight());
    }

    private int getRadarResultsMaxHeight() {
        DirectViewport viewport = surface.viewport();
        int viewportHeight = Math.round(viewport.uiHeight());
        return Math.max(getRadarResultsMinHeight(), viewportHeight - 28);
    }

    private boolean isRadarAuthenticated() {
        AutismDupeRadar.RadarState state = AutismDupeRadar.state();
        return state != null && state.authenticated();
    }

    private int getRadarPanelHeight() {
        if (isRadarAuthenticated()) {
            return Math.max(getRadarResultsMinHeight(), Math.min(getSharedPanelHeight(), getRadarResultsMaxHeight()));
        }
        DirectViewport viewport = surface.viewport();
        int viewportLimit = Math.max(getRadarLoginPanelHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin());
        return Math.min(getRadarLoginPanelHeight(), viewportLimit);
    }

    private int getRadarLoginPanelHeight() {
        AutismDupeRadar.RadarState state = AutismDupeRadar.state();
        boolean hasError = state != null && state.error() != null && !state.error().isBlank();
        int tabHeight = Math.max(14, theme.buttonHeight());
        int labelHeight = theme.lineHeight(UiTone.MUTED, 3);
        int errorHeight = hasError ? 12 : 0;
        int nodeCount = hasError ? 4 : 3;
        return theme.headerHeight()
            + panelPadding() * 2
            + contentGap() * Math.max(0, nodeCount - 1)
            + tabHeight
            + labelHeight
            + errorHeight
            + actionButtonHeight();
    }

    private int getSharedPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(infoMinHeight(), Math.max(infoPreferredHeight, measurePreferredInfoPanelHeight()));
        return Math.max(infoMinHeight(), Math.min(measured, Math.max(infoMinHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin())));
    }

    private int getPluginSetupPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(pluginSetupHeight(), measurePreferredHeightForState(1, false, false, Math.max(panelW, pluginSetupWidth())));
        return Math.min(measured, Math.max(pluginSetupHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin()));
    }

    private int getPluginScanningPanelHeight() {
        DirectViewport viewport = surface.viewport();
        int measured = Math.max(pluginScanningHeight(), measurePreferredHeightForState(1, false, true, Math.max(panelW, pluginScanningWidth())));
        return Math.min(measured, Math.max(pluginScanningHeight(), Math.round(viewport.uiHeight()) - viewportHeightMargin()));
    }

    private int getPluginResultsReservedHeight() {
        int contentPadding = panelPadding() * 2;
        int contentGap = contentGap() * 4;
        int autoToggleHeight = actionButtonHeight();
        int searchHeight = searchFieldHeight();
        int buttonRowHeight = actionButtonHeight();
        int headerLabelHeight = 13;
        return theme.headerHeight() + contentPadding + contentGap + autoToggleHeight + searchHeight + buttonRowHeight + headerLabelHeight;
    }

    private int getRadarResultsReservedHeight(boolean includePluginAlert) {
        int contentPadding = panelPadding() * 2;
        int rowCount = includePluginAlert ? 2 : 1;
        int contentGap = contentGap() * (3 + rowCount);
        int infoRows = 12 * rowCount;
        int actionRows = actionButtonHeight();
        int searchHeight = searchFieldHeight();
        return theme.headerHeight() + contentPadding + contentGap + infoRows + actionRows + searchHeight;
    }

    private int measurePreferredInfoPanelHeight() {
        return Math.max(infoMinHeight(), measurePreferredHeightForState(0, pluginScanDone, pluginScanInProgress, Math.max(infoMinWidth(), panelW)));
    }

    private int measurePreferredHeightForState(int tab, boolean scanDone, boolean scanInProgress, int measureWidth) {
        int previousTab = activeTab;
        boolean previousScanDone = pluginScanDone;
        boolean previousScanInProgress = pluginScanInProgress;
        activeTab = tab;
        pluginScanDone = scanDone;
        pluginScanInProgress = scanInProgress;
        rebuildUi();
        int measured = Math.round(surface.measurePreferredHeight(measureWidth));
        activeTab = previousTab;
        pluginScanDone = previousScanDone;
        pluginScanInProgress = previousScanInProgress;
        rebuildUi();
        return measured;
    }

    private int getInfoRequiredHeight() {
        int rowCount = 11;
        String displayedAddress = getDisplayedServerAddress();
        String host = extractLookupHost(displayedAddress);
        String realIp = getDisplayedRealIp(displayedAddress);
        if (!host.isBlank() && (!host.equals(realIp) || resolvingIp)) rowCount++;

        ServerData entry = MC.getCurrentServer();
        String software = getSoftwareGuess(entry);
        if (!"--".equals(software)) rowCount++;

        String versionNote = getVersionNote(entry);
        if (!"--".equals(versionNote)) rowCount++;

        int contentHeight = 5 + (rowCount * 13) + 11 + actionButtonHeight();
        return theme.headerHeight() + (panelPadding() * 2) + (contentGap() * 2) + contentHeight;
    }

    private void rememberCurrentTabSize() {
        if (activeTab == 0) {
            infoPreferredWidth = Math.max(infoMinWidth(), panelW);
            infoPreferredHeight = Math.max(getInfoRequiredHeight(), panelH);
            pluginPreferredWidth = Math.max(pluginPreferredWidth, infoPreferredWidth);
            return;
        }

        if (activeTab == 2) {
            pluginPreferredWidth = Math.max(pluginPreferredWidth, panelW);
            pluginPreferredHeight = Math.max(pluginPreferredHeight, Math.min(getRadarResultsMaxHeight(), panelH));
            infoPreferredWidth = Math.max(infoPreferredWidth, pluginPreferredWidth);
            return;
        }

        if (pluginScanDone && !pluginScanInProgress) {
            pluginPreferredWidth = Math.max(pluginResultsMinWidth(), panelW);
            pluginPreferredHeight = Math.max(getPluginResultsMinHeight(), Math.min(getPluginResultsMaxHeight(), panelH));
            infoPreferredWidth = Math.max(infoPreferredWidth, pluginPreferredWidth);
        }
    }

    private void clearLocalScanState(String address, String contextSignature) {
        pluginScanDone = false;
        pluginManualSetupOpen = false;
        cancelActivePluginScan("clear");
        pluginScanOutcome = PluginScanOutcome.NONE;
        pluginScanIssue = "";
        lastPluginScanTotal = 0;
        lastPluginScanAnswered = 0;
        lastPluginScanRetried = 0;
        lastPluginScanFailed = 0;
        pluginScanCompletionAnnounced = false;
        observedPluginCommands.clear();
        pluginEvidence.clear();
        pluginCopyEvidence.clear();
        pluginCopyCommands.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        scannedServerAddress = address;
        scannedPluginContextSignature = normalizePluginContextSignature(contextSignature);
        detectedPlugins.clear();
        pluginCommands.clear();
        pluginChannels.clear();
        pluginGuis.clear();
        invalidatePluginRows();
        pluginScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        lastPayloadFingerprintRevision = -1L;
        observedSuggestionRequests.clear();
        recentCommands.clear();
        invalidatePluginContextSignature();
    }

    private boolean loadCachedScan(String address, String contextSignature) {
        AutismConfig.PluginScanCacheEntry cached = AutismConfig.getGlobal().getPluginScan(address, contextSignature);

        if (cached == null) {
            cached = AutismConfig.getGlobal().getPluginScanByAddress(address);
        }
        if (cached == null) return false;

        detectedPlugins.clear();
        if (cached.plugins != null) detectedPlugins.addAll(dedupePluginNames(cached.plugins));
        pluginCommands.clear();
        if (cached.commands != null) pluginCommands.putAll(cached.commands);
        pluginChannels.clear();
        if (cached.channels != null) pluginChannels.putAll(cached.channels);
        pluginGuis.clear();
        if (cached.guis != null) pluginGuis.putAll(cached.guis);
        pluginCopyCommands.clear();
        if (cached.copyCommands != null) pluginCopyCommands.putAll(cached.copyCommands);
        pluginScrollState.jumpTo(0, 0);
        selectedPlugin = null;
        pluginScanDone = true;
        pluginManualSetupOpen = false;
        cancelActivePluginScan("cache-load");
        pluginScanOutcome = parsePluginScanOutcome(cached.scanStatus);
        if (pluginScanOutcome == PluginScanOutcome.NONE) pluginScanOutcome = PluginScanOutcome.COMPLETE;
        pluginScanIssue = pluginScanOutcome == PluginScanOutcome.PARTIAL ? "Cached partial scan" : "";
        lastPluginScanTotal = Math.max(0, cached.totalProbes);
        lastPluginScanAnswered = Math.max(0, cached.answeredProbes);
        lastPluginScanRetried = Math.max(0, cached.retriedProbes);
        lastPluginScanFailed = Math.max(0, cached.failedProbes);
        observedPluginCommands.clear();
        pluginEvidence.clear();
        pluginCopyEvidence.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        observedSuggestionRequests.clear();
        recentCommands.clear();
        scannedServerAddress = address;
        scannedPluginContextSignature = normalizePluginContextSignature(cached.contextSignature);
        Map<String, String> cachedEvidence = cached.evidence == null ? Map.of() : cached.evidence;
        Map<String, String> cachedCopyEvidence = cached.copyEvidence == null ? Map.of() : cached.copyEvidence;
        Map<String, String> cachedConfidence = cached.confidence == null ? Map.of() : cached.confidence;
        for (String plugin : detectedPlugins) {
            String key = normalizePluginKey(plugin);
            pluginEvidence.put(key, parseEvidenceName(cachedEvidence.get(key)));
            PluginEvidence copyEvidence = parseEvidenceName(cachedCopyEvidence.get(key));
            if (copyEvidence == PluginEvidence.UNKNOWN) copyEvidence = fallbackCopyEvidence(plugin, pluginEvidence.get(key));
            pluginCopyEvidence.put(key, copyEvidence);
            pluginConfidence.put(key, parseConfidenceName(cachedConfidence.get(key), confidenceForEvidence(pluginEvidence.get(key))));
            pluginResultKinds.put(key, resultKindForStoredPlugin(plugin, pluginEvidence.get(key), pluginConfidence.get(key)));
        }
        lastPayloadFingerprintRevision = -1L;
        syncPayloadFingerprintsIntoCurrentScan();
        invalidatePluginRows();
        return true;
    }

    private void cacheCurrentScan() {
        if (scannedServerAddress == null || scannedServerAddress.isBlank()) return;
        if (!pluginScanDone || pluginScanInProgress) return;

        if (detectedPlugins.isEmpty()) return;
        Map<String, String> evidenceSnapshot = new LinkedHashMap<>();
        Map<String, String> copyEvidenceSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, PluginEvidence> entry : pluginEvidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            evidenceSnapshot.put(entry.getKey(), entry.getValue().name());
        }
        for (Map.Entry<String, PluginEvidence> entry : pluginCopyEvidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            copyEvidenceSnapshot.put(entry.getKey(), entry.getValue().name());
        }
        Map<String, String> confidenceSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, PluginConfidence> entry : pluginConfidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            confidenceSnapshot.put(entry.getKey(), entry.getValue().name());
        }

        net.minecraft.client.multiplayer.ServerData current = MC.getCurrentServer();
        String serverName = current == null || current.name == null ? "" : current.name;
        AutismConfig.getGlobal().putPluginScan(scannedServerAddress, "",
            detectedPlugins, pluginCommands, evidenceSnapshot, pluginChannels, pluginGuis, confidenceSnapshot,
            copyEvidenceSnapshot, pluginCopyCommands, pluginScanOutcome.name(), lastPluginScanTotal,
            lastPluginScanAnswered, lastPluginScanRetried, lastPluginScanFailed, serverName, scannedServerAddress);
    }

    private void syncScanStateForCurrentServer() {
        String currentAddress = currentServerAddress();
        if (currentAddress.isEmpty()) return;
        if (!currentAddress.equals(lastRadarServerAddress)) {
            lastRadarServerAddress = currentAddress;
            lastRadarAutoCheckSignature = "";
            lastRadarAutoCheckAt = 0L;
            expandedRadarPlugins.clear();
            radarScrollState.jumpTo(0, 0);
            AutismDupeRadar.clearServerResults();
        }

        String currentContextSignature = currentPluginContextSignature();
        boolean sameAddress = currentAddress.equals(scannedServerAddress);
        boolean hasCurrentSignature = !currentContextSignature.isEmpty();
        boolean sameContext = sameAddress && currentContextSignature.equals(scannedPluginContextSignature);

        PluginScanSession session = activePluginScan;
        if (session != null && pluginScanInProgress) {
            if (currentAddress.equals(session.serverAddress)) {
                session.contextSignature = currentContextSignature;
                scannedPluginContextSignature = currentContextSignature;
                return;
            }
            cancelActivePluginScan("server-changed");
        }

        if (sameAddress && pluginScanDone && hasCurrentSignature && !sameContext) {
            scannedPluginContextSignature = currentContextSignature;
            cacheCurrentScan();
            return;
        }

        if (pluginManualSetupOpen && sameAddress && (sameContext || !hasCurrentSignature)) return;
        if (!sameAddress || (hasCurrentSignature && !sameContext)) pluginManualSetupOpen = false;

        if (sameContext && (pluginScanDone || pluginScanInProgress)) return;
        if (sameAddress && !hasCurrentSignature && (pluginScanDone || pluginScanInProgress)) return;

        if (!loadCachedScan(currentAddress, currentContextSignature)) {
            clearLocalScanState(currentAddress, currentContextSignature);
        }
    }

    private String normalizePluginKey(String plugin) {
        return AutismPluginPayloadFingerprints.canonicalPluginKey(plugin);
    }

    private int evidenceRank(PluginEvidence evidence) {
        return switch (evidence) {
            case PAYLOAD_CHANNEL -> 0;
            case COMMAND_TREE -> 1;
            case NAMESPACE -> 2;
            case ROOT_HINT -> 3;
            case COMMAND_GUI -> 4;
            case VERSION_HINT -> 5;
            case PLUGIN_LIST -> 6;
            case SCANNER_AUTOCOMPLETE -> 7;
            case USER_AUTOCOMPLETE -> 8;
            case FEATURE -> 9;
            case HELP_HINT -> 10;
            case UNKNOWN -> 11;
        };
    }

    private int confidenceRank(PluginConfidence confidence) {
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> 0;
            case STRONG -> 1;
            case FEATURE -> 2;
            case UNKNOWN -> 3;
        };
    }

    private PluginEvidence mergeEvidence(PluginEvidence current, PluginEvidence candidate) {
        if (current == null) return candidate == null ? PluginEvidence.UNKNOWN : candidate;
        if (candidate == null) return current;
        return evidenceRank(candidate) < evidenceRank(current) ? candidate : current;
    }

    private int copyEvidenceRank(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case COMMAND_TREE -> 0;
            case NAMESPACE, ROOT_HINT -> 1;
            case PLUGIN_LIST, SCANNER_AUTOCOMPLETE -> 2;
            case VERSION_HINT -> 3;
            case PAYLOAD_CHANNEL -> 4;
            case COMMAND_GUI -> 5;
            case USER_AUTOCOMPLETE, FEATURE -> 6;
            case HELP_HINT, UNKNOWN -> 7;
        };
    }

    private PluginEvidence mergeCopyEvidence(PluginEvidence current, PluginEvidence candidate) {
        if (candidate == null) return current == null ? PluginEvidence.UNKNOWN : current;
        if (current == null) return candidate;
        return copyEvidenceRank(candidate) < copyEvidenceRank(current) ? candidate : current;
    }

    private boolean isCopyCommandEvidence(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case COMMAND_TREE, NAMESPACE, ROOT_HINT, PLUGIN_LIST -> true;
            default -> false;
        };
    }

    private boolean isCompactCopyEvidence(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case COMMAND_TREE, NAMESPACE, ROOT_HINT, PLUGIN_LIST, VERSION_HINT, PAYLOAD_CHANNEL, COMMAND_GUI -> true;
            default -> false;
        };
    }

    private PluginConfidence mergeConfidence(PluginConfidence current, PluginConfidence candidate) {
        if (current == null) return candidate == null ? PluginConfidence.UNKNOWN : candidate;
        if (candidate == null) return current;
        return confidenceRank(candidate) < confidenceRank(current) ? candidate : current;
    }

    private PluginConfidence confidenceForEvidence(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case PAYLOAD_CHANNEL, VERSION_HINT, NAMESPACE -> PluginConfidence.EXACT;
            case ROOT_HINT -> PluginConfidence.STRONG;
            case COMMAND_TREE, COMMAND_GUI, USER_AUTOCOMPLETE, SCANNER_AUTOCOMPLETE, PLUGIN_LIST, FEATURE, HELP_HINT -> PluginConfidence.FEATURE;
            case UNKNOWN -> PluginConfidence.UNKNOWN;
        };
    }

    private boolean isWeakCommandEvidence(PluginEvidence evidence) {
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case USER_AUTOCOMPLETE, SCANNER_AUTOCOMPLETE, FEATURE, HELP_HINT, UNKNOWN -> true;
            default -> false;
        };
    }

    private boolean isFallbackCommandEntry(PluginScanEntry entry) {
        if (entry == null) return false;
        return entry.resultKind == PluginResultKind.FEATURE || isWeakCommandEvidence(entry.evidence);
    }

    private boolean isStrongCommandOwner(PluginScanEntry entry) {
        return entry != null
            && entry.resultKind == PluginResultKind.PLUGIN
            && !isWeakCommandEvidence(entry.evidence)
            && entry.evidence != PluginEvidence.COMMAND_GUI;
    }

    private int commandOwnerScore(PluginScanEntry entry) {
        if (entry == null) return Integer.MAX_VALUE;
        int score = evidenceRank(entry.evidence) * 100 + confidenceRank(entry.confidence) * 10;
        if (entry.resultKind == PluginResultKind.FEATURE) score += 1000;
        if (isWeakCommandEvidence(entry.evidence)) score += 500;
        if (!entry.commandBacked) score += 25;
        String name = entry.displayName == null ? "" : entry.displayName.trim();
        if (name.startsWith("/")) score += 75;
        return score;
    }

    private PluginEvidence parseEvidenceName(String value) {
        if (value == null || value.isBlank()) return PluginEvidence.UNKNOWN;
        try {
            return PluginEvidence.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PluginEvidence.UNKNOWN;
        }
    }

    private PluginConfidence parseConfidenceName(String value, PluginConfidence fallback) {
        if (value == null || value.isBlank()) return fallback == null ? PluginConfidence.UNKNOWN : fallback;
        try {
            return PluginConfidence.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? PluginConfidence.UNKNOWN : fallback;
        }
    }

    private int getConfidenceColor(PluginConfidence confidence, boolean hovered) {
        if (hovered) return AutismColors.packetWhite();
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> AutismColors.packetGreen();
            case STRONG -> AutismColors.packetCyan();
            case FEATURE -> AutismColors.packetYellow();
            case UNKNOWN -> theme.color(UiTone.MUTED);
        };
    }

    private String confidenceLabel(PluginConfidence confidence) {
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> "Exact";
            case STRONG -> "Strong";
            case FEATURE -> "Feature";
            case UNKNOWN -> "Unknown";
        };
    }

    private Map<String, PluginScanEntry> buildCurrentPluginEntries() {
        Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String plugin : detectedPlugins) {
            if (plugin == null || plugin.isBlank()) continue;
            List<String> existing = getPluginCommands(plugin);
            PluginEvidence evidence = pluginEvidence.getOrDefault(normalizePluginKey(plugin), existing != null && !existing.isEmpty() ? PluginEvidence.ROOT_HINT : PluginEvidence.UNKNOWN);
            List<String> existingChannels = getPluginChannels(plugin);
            List<String> existingGuis = getPluginGuis(plugin);
            PluginConfidence confidence = pluginConfidence.getOrDefault(normalizePluginKey(plugin), confidenceForEvidence(evidence));
            PluginResultKind resultKind = resultKindForStoredPlugin(plugin, evidence, confidence);
            mergePluginEntry(entries, null, plugin, existing, existingChannels, existingGuis, existing != null && !existing.isEmpty(), evidence, confidence, resultKind);
            String key = normalizePluginKey(plugin);
            PluginScanEntry entry = entries.get(key);
            if (entry != null) {
                PluginEvidence copyEvidence = pluginCopyEvidence.getOrDefault(key, fallbackCopyEvidence(plugin, evidence));
                if (copyEvidenceRank(copyEvidence) < copyEvidenceRank(entry.copyEvidence)) entry.copyCommands.clear();
                entry.copyEvidence = mergeCopyEvidence(entry.copyEvidence, copyEvidence);
                List<String> copyCommands = listForPluginKey(pluginCopyCommands, plugin);
                if (copyCommands != null) {
                    for (String command : copyCommands) {
                        if (command != null && !command.isBlank()) entry.copyCommands.add(command.trim());
                    }
                }
            }
        }
        return entries;
    }

    private PluginScanOutcome parsePluginScanOutcome(String value) {
        if (value == null || value.isBlank()) return PluginScanOutcome.NONE;
        try {
            return PluginScanOutcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PluginScanOutcome.NONE;
        }
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String pluginName, Collection<String> commands, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, null, pluginName, commands, List.of(), List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String pluginName, Collection<String> commands, Collection<String> channels, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, null, pluginName, commands, channels, List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName, Collection<String> commands, Collection<String> channels, boolean commandBacked, PluginEvidence evidence) {
        return mergePluginEntry(entries, explicitKey, pluginName, commands, channels, List.of(), commandBacked, evidence, confidenceForEvidence(evidence));
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName,
                                     Collection<String> commands, Collection<String> channels, Collection<String> guis,
                                     boolean commandBacked, PluginEvidence evidence, PluginConfidence confidence) {
        return mergePluginEntry(entries, explicitKey, pluginName, commands, channels, guis,
            commandBacked, evidence, confidence, PluginResultKind.PLUGIN);
    }

    private boolean mergePluginEntry(Map<String, PluginScanEntry> entries, String explicitKey, String pluginName,
                                     Collection<String> commands, Collection<String> channels, Collection<String> guis,
                                     boolean commandBacked, PluginEvidence evidence, PluginConfidence confidence,
                                     PluginResultKind resultKind) {
        if (pluginName == null || pluginName.isBlank()) return false;

        String cleanName = pluginName.trim();
        String key = explicitKey == null || explicitKey.isBlank() ? normalizePluginKey(cleanName) : normalizePluginKey(explicitKey);
        if (key.isEmpty() || (VANILLA_NAMESPACES.contains(key) && evidence != PluginEvidence.PAYLOAD_CHANNEL)) return false;

        PluginScanEntry entry = entries.computeIfAbsent(key, unused -> new PluginScanEntry());
        String beforeName = entry.displayName;
        boolean beforeBacked = entry.commandBacked;
        PluginEvidence beforeEvidence = entry.evidence;
        PluginEvidence beforeCopyEvidence = entry.copyEvidence;
        PluginConfidence beforeConfidence = entry.confidence;
        PluginResultKind beforeKind = entry.resultKind;
        int beforeCommandCount = entry.commands.size();
        int beforeCopyCommandCount = entry.copyCommands.size();
        int beforeChannelCount = entry.channels.size();
        int beforeGuiCount = entry.guis.size();
        boolean candidateHasBetterCopySource = copyEvidenceRank(evidence) < copyEvidenceRank(entry.copyEvidence);
        boolean allowReadableRename = !isCompactCopyEvidence(entry.copyEvidence) || candidateHasBetterCopySource;
        if (candidateHasBetterCopySource) entry.copyCommands.clear();
        if (entry.displayName == null || entry.displayName.isBlank() || (commandBacked && !entry.commandBacked) || (allowReadableRename && isMoreReadablePluginName(cleanName, entry.displayName))) {
            entry.displayName = cleanName;
        }
        entry.resultKind = mergeResultKind(entry.resultKind, resultKind);
        if (commandBacked) entry.commandBacked = true;
        entry.evidence = mergeEvidence(entry.evidence, evidence);
        entry.copyEvidence = mergeCopyEvidence(entry.copyEvidence, evidence);
        entry.confidence = mergeConfidence(entry.confidence, confidence);
        pluginEvidence.put(key, mergeEvidence(pluginEvidence.get(key), evidence));
        pluginCopyEvidence.put(key, mergeCopyEvidence(pluginCopyEvidence.get(key), evidence));
        pluginConfidence.put(key, mergeConfidence(pluginConfidence.get(key), confidence));
        pluginResultKinds.put(key, mergeResultKind(pluginResultKinds.get(key), resultKind));

        if (commands != null) {
            for (String command : commands) {
                if (command == null) continue;
                String cleanCommand = command.trim();
                if (!cleanCommand.isEmpty()) entry.commands.add(cleanCommand);
                if (!cleanCommand.isEmpty() && isCopyCommandEvidence(evidence)
                    && copyEvidenceRank(evidence) <= copyEvidenceRank(entry.copyEvidence)) {
                    entry.copyCommands.add(cleanCommand);
                }
            }
        }
        if (channels != null) {
            for (String channel : channels) {
                if (channel == null) continue;
                String cleanChannel = channel.trim();
                if (!cleanChannel.isEmpty()) entry.channels.add(cleanChannel);
            }
        }
        if (guis != null) {
            for (String gui : guis) {
                if (gui == null) continue;
                String cleanGui = gui.trim();
                if (!cleanGui.isEmpty()) entry.guis.add(cleanGui);
            }
        }

        return !Objects.equals(beforeName, entry.displayName)
            || beforeBacked != entry.commandBacked
            || beforeEvidence != entry.evidence
            || beforeCopyEvidence != entry.copyEvidence
            || beforeConfidence != entry.confidence
            || beforeKind != entry.resultKind
            || beforeCommandCount != entry.commands.size()
            || beforeCopyCommandCount != entry.copyCommands.size()
            || beforeChannelCount != entry.channels.size()
            || beforeGuiCount != entry.guis.size();
    }

    private boolean mergeScanEntry(Map<String, PluginScanEntry> target, String explicitKey, PluginScanEntry source) {
        if (target == null || source == null || source.displayName == null || source.displayName.isBlank()) return false;
        boolean changed = mergePluginEntry(target, explicitKey, source.displayName, source.commands, source.channels, source.guis,
            source.commandBacked, source.evidence, source.confidence, source.resultKind);
        String key = explicitKey == null || explicitKey.isBlank() ? normalizePluginKey(source.displayName) : normalizePluginKey(explicitKey);
        PluginScanEntry entry = target.get(key);
        if (entry != null) {
            PluginEvidence beforeCopyEvidence = entry.copyEvidence;
            int beforeCopyCommandCount = entry.copyCommands.size();
            boolean betterCopySource = copyEvidenceRank(source.copyEvidence) < copyEvidenceRank(entry.copyEvidence);
            if (betterCopySource) entry.copyCommands.clear();
            entry.copyEvidence = mergeCopyEvidence(entry.copyEvidence, source.copyEvidence);
            Collection<String> sourceCopyCommands = source.copyCommands.isEmpty() && isCopyCommandEvidence(source.copyEvidence)
                ? source.commands
                : source.copyCommands;
            if (sourceCopyCommands != null && copyEvidenceRank(source.copyEvidence) <= copyEvidenceRank(entry.copyEvidence)) {
                for (String command : sourceCopyCommands) {
                    if (command != null && !command.isBlank()) entry.copyCommands.add(command.trim());
                }
            }
            changed |= beforeCopyEvidence != entry.copyEvidence || beforeCopyCommandCount != entry.copyCommands.size();
        }
        return changed;
    }

    private boolean mergeScanEntries(Map<String, PluginScanEntry> target, Map<String, PluginScanEntry> source) {
        if (target == null || source == null || source.isEmpty()) return false;
        boolean changed = false;
        for (Map.Entry<String, PluginScanEntry> entry : source.entrySet()) {
            changed |= mergeScanEntry(target, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    private boolean normalizeCommandSet(Set<String> commands) {
        if (commands == null || commands.isEmpty()) return false;
        TreeSet<String> normalized = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String command : commands) {
            String token = normalizeCommandToken(command);
            if (!token.isBlank()) normalized.add(token);
        }
        if (normalized.size() == commands.size() && normalized.containsAll(commands)) return false;
        commands.clear();
        commands.addAll(normalized);
        return true;
    }

    private String bestCommandOwnerKey(Map<String, PluginScanEntry> entries, String command) {
        if (entries == null || entries.isEmpty()) return null;
        String token = normalizeCommandToken(command);
        if (token.isBlank()) return null;

        String bestKey = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map.Entry<String, PluginScanEntry> mapEntry : new ArrayList<>(entries.entrySet())) {
            PluginScanEntry entry = mapEntry.getValue();
            if (entry == null || entry.commands == null || !entry.commands.contains(token)) continue;
            int score = commandOwnerScore(entry);
            if (score < bestScore) {
                bestScore = score;
                bestKey = mapEntry.getKey();
            }
        }

        String aliasedPlugin = ROOT_COMMAND_PLUGIN_ALIASES.get(token);
        if (aliasedPlugin != null && !aliasedPlugin.isBlank()) {
            String aliasedKey = normalizePluginKey(aliasedPlugin);
            PluginScanEntry aliasedEntry = entries.get(aliasedKey);
            if (isStrongCommandOwner(aliasedEntry)) {
                int score = commandOwnerScore(aliasedEntry);
                if (bestKey == null || score <= bestScore || !isStrongCommandOwner(entries.get(bestKey))) {
                    bestKey = aliasedKey;
                }
            }
        }

        return bestKey;
    }

    private PluginScanEntry commandHintEntry(String command) {
        String token = normalizeCommandToken(command);
        PluginScanEntry entry = new PluginScanEntry();
        entry.displayName = "/" + token;
        entry.commandBacked = true;
        entry.resultKind = PluginResultKind.PLUGIN;
        entry.evidence = PluginEvidence.SCANNER_AUTOCOMPLETE;
        entry.copyEvidence = PluginEvidence.UNKNOWN;
        entry.confidence = PluginConfidence.FEATURE;
        entry.commands.add(token);
        return entry;
    }

    private boolean cleanupPluginCommandOwnership(Map<String, PluginScanEntry> entries) {
        if (entries == null || entries.isEmpty()) return false;
        boolean changed = false;

        for (PluginScanEntry entry : entries.values()) {
            if (entry == null) continue;
            changed |= normalizeCommandSet(entry.commands);
            changed |= normalizeCommandSet(entry.copyCommands);
        }

        TreeSet<String> allCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PluginScanEntry entry : entries.values()) {
            if (entry != null && entry.commands != null) allCommands.addAll(entry.commands);
        }

        Map<String, String> ownerByCommand = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String command : allCommands) {
            String ownerKey = bestCommandOwnerKey(entries, command);
            if (ownerKey != null && !ownerKey.isBlank()) ownerByCommand.put(command, ownerKey);
        }

        for (Map.Entry<String, PluginScanEntry> mapEntry : new ArrayList<>(entries.entrySet())) {
            String key = mapEntry.getKey();
            PluginScanEntry entry = mapEntry.getValue();
            if (key == null || entry == null || entry.commands.isEmpty()) continue;
            for (String command : new ArrayList<>(entry.commands)) {
                String ownerKey = ownerByCommand.get(command);
                if (ownerKey == null || ownerKey.equals(key)) continue;
                PluginScanEntry owner = entries.get(ownerKey);
                if (owner == null) continue;
                if (owner.commands.add(command)) changed = true;
                if (isCopyCommandEvidence(owner.copyEvidence) && owner.copyCommands.add(command)) changed = true;
                if (!entry.guis.isEmpty()) {
                    int beforeGuiCount = owner.guis.size();
                    owner.guis.addAll(entry.guis);
                    changed |= beforeGuiCount != owner.guis.size();
                }
                entry.commands.remove(command);
                entry.copyCommands.remove(command);
                changed = true;
            }
        }

        List<String> removeKeys = new ArrayList<>();
        Map<String, PluginScanEntry> addEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, PluginScanEntry> mapEntry : new ArrayList<>(entries.entrySet())) {
            String key = mapEntry.getKey();
            PluginScanEntry entry = mapEntry.getValue();
            if (key == null || entry == null) {
                removeKeys.add(key);
                continue;
            }

            if (!isFallbackCommandEntry(entry)) continue;
            if (entry.commands.isEmpty()) {
                removeKeys.add(key);
                continue;
            }

            if (entry.commands.size() == 1) {
                String command = entry.commands.iterator().next();
                String expectedName = "/" + command;
                if (!expectedName.equals(entry.displayName)) {
                    entry.displayName = expectedName;
                    changed = true;
                }
                if (entry.resultKind != PluginResultKind.PLUGIN) {
                    entry.resultKind = PluginResultKind.PLUGIN;
                    changed = true;
                }
                if (entry.evidence != PluginEvidence.SCANNER_AUTOCOMPLETE) {
                    entry.evidence = PluginEvidence.SCANNER_AUTOCOMPLETE;
                    changed = true;
                }
                if (entry.copyEvidence != PluginEvidence.UNKNOWN) {
                    entry.copyEvidence = PluginEvidence.UNKNOWN;
                    entry.copyCommands.clear();
                    changed = true;
                }
                entry.confidence = PluginConfidence.FEATURE;
                continue;
            }

            for (String command : entry.commands) {
                String hintKey = normalizePluginKey("command_" + command);
                if (!entries.containsKey(hintKey) && !addEntries.containsKey(hintKey)) {
                    addEntries.put(hintKey, commandHintEntry(command));
                }
            }
            removeKeys.add(key);
            changed = true;
        }

        for (String key : removeKeys) {
            if (key != null && entries.remove(key) != null) changed = true;
        }
        if (!addEntries.isEmpty()) {
            entries.putAll(addEntries);
            changed = true;
        }
        return changed;
    }

    private int countPluginEntries(Map<String, PluginScanEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        int count = 0;
        for (PluginScanEntry entry : entries.values()) {
            if (entry != null && entry.displayName != null && !entry.displayName.isBlank()) count++;
        }
        return count;
    }

    private void tracePluginScan(PluginScanSession session, String message) {
        if (!PLUGIN_SCAN_TRACE) return;
        String generation = session == null ? "-" : Long.toString(session.generation);
        System.out.println("[AUTISM][PluginScan][gen=" + generation + "] " + message);
    }

    private PluginResultKind mergeResultKind(PluginResultKind current, PluginResultKind candidate) {
        if (candidate == null) return current == null ? PluginResultKind.PLUGIN : current;
        if (current == null) return candidate;
        if (current == PluginResultKind.PLUGIN || candidate == PluginResultKind.PLUGIN) return PluginResultKind.PLUGIN;
        return PluginResultKind.FEATURE;
    }

    private PluginResultKind resultKindForStoredPlugin(String plugin, PluginEvidence evidence, PluginConfidence confidence) {
        String key = normalizePluginKey(plugin);
        PluginResultKind stored = pluginResultKinds.get(key);
        if (stored != null) return stored;
        if (confidence == PluginConfidence.EXACT || confidence == PluginConfidence.STRONG) return PluginResultKind.PLUGIN;
        if (evidence == PluginEvidence.PAYLOAD_CHANNEL || evidence == PluginEvidence.VERSION_HINT
            || evidence == PluginEvidence.NAMESPACE) {
            return PluginResultKind.PLUGIN;
        }
        String lower = plugin == null ? "" : plugin.trim().toLowerCase(Locale.ROOT);
        for (String featureLabel : COMMAND_FEATURE_LABELS.values()) {
            if (lower.equals(featureLabel.toLowerCase(Locale.ROOT))) return PluginResultKind.FEATURE;
        }
        return PluginResultKind.PLUGIN;
    }

    private PluginEvidence fallbackCopyEvidence(String plugin, PluginEvidence evidence) {
        if (evidence == PluginEvidence.UNKNOWN || evidence == null) {
            List<String> commands = listForPluginKey(pluginCommands, plugin);
            if (commands != null && !commands.isEmpty()) {
                return PluginEvidence.ROOT_HINT;
            }
        }
        return isCompactCopyEvidence(evidence) ? evidence : PluginEvidence.UNKNOWN;
    }

    private List<String> listForPluginKey(Map<String, List<String>> map, String plugin) {
        if (map == null || map.isEmpty() || plugin == null || plugin.isBlank()) return null;
        List<String> direct = map.get(plugin);
        if (direct != null) return direct;
        String key = normalizePluginKey(plugin);
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if (entry.getKey() != null && normalizePluginKey(entry.getKey()).equals(key)) return entry.getValue();
        }
        return null;
    }

    private boolean isMoreReadablePluginName(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) return false;
        if (current == null || current.isBlank()) return true;
        String c = candidate.trim();
        String old = current.trim();
        if (c.equalsIgnoreCase(old)) return false;
        String cKey = normalizePluginKey(c);
        String oldKey = normalizePluginKey(old);
        if (!cKey.equals(oldKey)) return false;
        boolean candidateHasCase = !c.equals(c.toLowerCase(Locale.ROOT));
        boolean currentHasCase = !old.equals(old.toLowerCase(Locale.ROOT));
        boolean candidateHasSpace = c.indexOf(' ') >= 0;
        boolean currentHasSpace = old.indexOf(' ') >= 0;
        return (candidateHasCase && !currentHasCase) || (candidateHasSpace && !currentHasSpace);
    }

    private void applyPluginEntries(Map<String, PluginScanEntry> entries, boolean resetSelection) {
        Map<String, PluginScanEntry> normalizedEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (entries != null) normalizedEntries.putAll(entries);
        cleanupPluginCommandOwnership(normalizedEntries);

        detectedPlugins.clear();
        pluginCommands.clear();
        pluginChannels.clear();
        pluginGuis.clear();
        pluginEvidence.clear();
        pluginCopyEvidence.clear();
        pluginCopyCommands.clear();
        pluginConfidence.clear();
        pluginResultKinds.clear();

        List<PluginScanEntry> sortedEntries = new ArrayList<>(normalizedEntries.values());
        sortedEntries.removeIf(entry -> entry == null || entry.displayName == null || entry.displayName.isBlank());
        sortedEntries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));

        for (PluginScanEntry entry : sortedEntries) {
            String plugin = entry.displayName;
            List<String> commands = new ArrayList<>(entry.commands);
            commands.removeIf(cmd -> cmd == null || cmd.isBlank());
            commands.sort(String.CASE_INSENSITIVE_ORDER);

            detectedPlugins.add(plugin);
            pluginCommands.put(plugin, commands);

            pluginChannels.put(plugin, dedupeChannelRows(entry.channels));
            List<String> guis = new ArrayList<>(entry.guis);
            guis.removeIf(gui -> gui == null || gui.isBlank());
            guis.sort(String.CASE_INSENSITIVE_ORDER);
            pluginGuis.put(plugin, List.copyOf(guis));
            pluginEvidence.put(normalizePluginKey(plugin), entry.evidence == null ? PluginEvidence.UNKNOWN : entry.evidence);
            pluginCopyEvidence.put(normalizePluginKey(plugin), entry.copyEvidence == null ? PluginEvidence.UNKNOWN : entry.copyEvidence);
            List<String> copyCommands = new ArrayList<>(entry.copyCommands);
            copyCommands.removeIf(cmd -> cmd == null || cmd.isBlank());
            copyCommands.sort(String.CASE_INSENSITIVE_ORDER);
            pluginCopyCommands.put(plugin, List.copyOf(copyCommands));
            pluginConfidence.put(normalizePluginKey(plugin), entry.confidence == null ? confidenceForEvidence(entry.evidence) : entry.confidence);
            pluginResultKinds.put(normalizePluginKey(plugin), entry.resultKind == null ? PluginResultKind.PLUGIN : entry.resultKind);
        }

        if (resetSelection) {
            pluginScrollState.jumpTo(0, 0);
            selectedPlugin = null;
        } else if (selectedPlugin != null && !hasDetectedPlugin(selectedPlugin)) {
            selectedPlugin = null;
        }
        invalidatePluginRows();
    }

    private boolean isKnownPluginNamespace(String key) {
        for (String namespace : COMMON_PLUGIN_NAMESPACES) {
            if (namespace.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private String normalizeCommandToken(String raw) {
        if (raw == null) return "";
        String token = raw.trim();
        if (token.isEmpty()) return "";
        while (token.startsWith("/")) token = token.substring(1).trim();
        int spaceIndex = token.indexOf(' ');
        if (spaceIndex >= 0) token = token.substring(0, spaceIndex).trim();
        while (token.endsWith(":")) token = token.substring(0, token.length() - 1).trim();
        return token.toLowerCase(Locale.ROOT);
    }

    private boolean isTrackableCommandToken(String token) {
        if (token == null || token.isBlank()) return false;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 2) return false;
        if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) return false;
        if (getOnlinePlayerNames().contains(clean)) return false;
        return clean.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.' || ch == ':');
    }

    private boolean mergeCommandEvidence(Map<String, PluginScanEntry> entries, String rawCommand, PluginEvidence source) {
        String token = normalizeCommandToken(rawCommand);
        if (!isTrackableCommandToken(token)) return false;

        observedPluginCommands.add(token);
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            String namespace = parts[0].trim().toLowerCase(Locale.ROOT);
            String command = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
            if (isTrackableCommandToken(namespace) && !VANILLA_NAMESPACES.contains(namespace)) {
                return mergePluginEntry(entries, namespace, namespace, command.isBlank() ? List.of() : List.of(command), List.of(), List.of(),
                    !command.isBlank(), PluginEvidence.NAMESPACE, PluginConfidence.EXACT);
            }
            return false;
        }

        String plugin = null;
        PluginConfidence confidence = PluginConfidence.FEATURE;
        PluginEvidence evidence = source == null ? PluginEvidence.FEATURE : source;

        if (isKnownPluginNamespace(token)) {
            plugin = token;
            confidence = PluginConfidence.EXACT;
            evidence = PluginEvidence.NAMESPACE;
        } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(token)) {
            plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(token);
            confidence = PluginConfidence.STRONG;
            evidence = PluginEvidence.ROOT_HINT;
        }

        if (plugin != null && !plugin.isBlank()) {
            return mergePluginEntry(entries, null, plugin, List.of(token), List.of(), List.of(), true, evidence, confidence, PluginResultKind.PLUGIN);
        }

        String ownerKey = bestCommandOwnerKey(entries, token);
        if (ownerKey != null) {
            PluginScanEntry owner = entries.get(ownerKey);
            if (owner == null) return false;
            boolean changed = owner.commands.add(token);
            if (isCopyCommandEvidence(owner.copyEvidence)) changed |= owner.copyCommands.add(token);
            return changed;
        }

        if (source == PluginEvidence.USER_AUTOCOMPLETE
            || source == PluginEvidence.COMMAND_GUI
            || source == PluginEvidence.SCANNER_AUTOCOMPLETE
            || COMMAND_FEATURE_LABELS.containsKey(token)) {
            return mergePluginEntry(entries, "command_" + token, "/" + token, List.of(token), List.of(), List.of(),
                true, PluginEvidence.SCANNER_AUTOCOMPLETE, PluginConfidence.FEATURE, PluginResultKind.PLUGIN);
        }

        return false;
    }

    private Set<String> getOnlinePlayerNames() {
        if (MC.getConnection() == null) return Set.of();
        Set<String> names = new HashSet<>();
        for (var player : MC.getConnection().getListedOnlinePlayers()) {
            if (player == null || player.getProfile() == null) continue;
            String name = player.getProfile().name();
            if (name != null && !name.isBlank()) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private boolean isLikelyPluginNameCandidate(String candidate, PluginProbeKind kind) {
        if (candidate == null) return false;
        String clean = candidate.trim();
        String key = normalizePluginKey(clean);
        if (key.isEmpty()) return false;
        if (key.length() < 2) return false;
        if (clean.contains(" ") || clean.contains("/") || clean.contains(":")) return false;
        if (VANILLA_NAMESPACES.contains(key)) return false;
        if (getOnlinePlayerNames().contains(key)) return false;

        if (kind == PluginProbeKind.PLUGIN_LIST || kind == PluginProbeKind.VERSION) {
            if (key.equals("plugins") || key.equals("plugin") || key.equals("version") || key.equals("about")) return false;
        }

        return key.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.');
    }

    private void addObservedPluginCommand(String raw) {
        String token = normalizeCommandToken(raw);
        if (!isTrackableCommandToken(token)) return;
        observedPluginCommands.add(token);
    }

    private void inferPluginsFromObservedCommands(Map<String, PluginScanEntry> entries) {
        inferLegacyPluginsFromObservedCommands(entries);
    }

    private void inferLegacyPluginsFromObservedCommands(Map<String, PluginScanEntry> entries) {
        if (observedPluginCommands.isEmpty()) return;

        Map<String, Set<String>> inferredMatches = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String command : observedPluginCommands) {
            String plugin = null;
            if (isKnownPluginNamespace(command)) {
                plugin = command;
            } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(command)) {
                plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(command);
            }
            if (plugin == null || plugin.isBlank()) continue;
            inferredMatches.computeIfAbsent(plugin, unused -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(command);
        }

        for (Map.Entry<String, Set<String>> entry : inferredMatches.entrySet()) {
            mergePluginEntry(entries, entry.getKey(), entry.getValue(), true, PluginEvidence.ROOT_HINT);
        }
    }

    private boolean mergeScannerAutocompleteHint(Map<String, PluginScanEntry> entries, String rawCommand) {
        String token = normalizeCommandToken(rawCommand);
        if (!isScannerAutocompleteFallbackCandidate(token)) return false;
        if (token.contains(":")) return false;
        String ownerKey = bestCommandOwnerKey(entries, token);
        if (ownerKey != null) {
            PluginScanEntry owner = entries.get(ownerKey);
            if (owner == null || owner.commands.contains(token)) return false;
            owner.commands.add(token);
            if (isCopyCommandEvidence(owner.copyEvidence)) owner.copyCommands.add(token);
            return true;
        }

        return mergePluginEntry(entries, "command_" + token, "/" + token, List.of(token), List.of(), List.of(),
            true, PluginEvidence.SCANNER_AUTOCOMPLETE, PluginConfidence.FEATURE, PluginResultKind.PLUGIN);
    }

    private boolean isScannerAutocompleteFallbackCandidate(String token) {
        if (!isTrackableCommandToken(token)) return false;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 3) return false;
        if (SCANNER_AUTOCOMPLETE_BLOCKLIST.contains(clean)) return false;
        if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) return false;
        if (COMMAND_FEATURE_LABELS.containsKey(clean)) return false;
        if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(clean) || isKnownPluginNamespace(clean)) return false;
        return true;
    }

    private boolean mergePayloadFingerprintEntries(Map<String, PluginScanEntry> entries) {
        String address = currentServerAddress();
        if (address == null || address.isBlank()) return false;
        boolean changed = false;
        for (AutismPluginPayloadFingerprints.PluginFingerprint fingerprint : AutismPluginPayloadFingerprints.pluginsFor(address, getLiveBrand())) {
            if (fingerprint == null || fingerprint.plugin() == null || fingerprint.plugin().isBlank()) continue;
            changed |= promoteMatchingFeatureEntries(entries, fingerprint);
            PluginConfidence confidence = payloadFingerprintConfidence(fingerprint);
            changed |= mergePluginEntry(entries, fingerprint.key(), fingerprint.plugin(), List.of(), fingerprint.channels(), List.of(), false,
                PluginEvidence.PAYLOAD_CHANNEL, confidence);
        }
        return changed;
    }

    private PluginConfidence payloadFingerprintConfidence(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        String basis = fingerprint == null ? "" : fingerprint.basis();
        if ("embedded".equalsIgnoreCase(basis)) return PluginConfidence.STRONG;
        return PluginConfidence.EXACT;
    }

    private boolean promoteMatchingFeatureEntries(Map<String, PluginScanEntry> entries,
                                                  AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        if (entries == null || entries.isEmpty() || fingerprint == null) return false;
        Set<String> keywords = fingerprintKeywords(fingerprint);
        if (keywords.isEmpty()) return false;

        boolean changed = false;
        List<String> removeKeys = new ArrayList<>();
        for (Map.Entry<String, PluginScanEntry> mapEntry : new ArrayList<>(entries.entrySet())) {
            String key = mapEntry.getKey();
            PluginScanEntry entry = mapEntry.getValue();
            if (key == null || !key.startsWith("feature")) continue;
            if (entry == null || !featureEntryMatches(entry, keywords)) continue;
            changed |= mergePluginEntry(entries, fingerprint.key(), fingerprint.plugin(), entry.commands, fingerprint.channels(), entry.guis,
                entry.commandBacked, PluginEvidence.PAYLOAD_CHANNEL, payloadFingerprintConfidence(fingerprint));
            removeKeys.add(key);
        }
        for (String key : removeKeys) {
            entries.remove(key);
            pluginEvidence.remove(key);
            pluginConfidence.remove(key);
            pluginResultKinds.remove(key);
            changed = true;
        }
        return changed;
    }

    private Set<String> fingerprintKeywords(AutismPluginPayloadFingerprints.PluginFingerprint fingerprint) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addFingerprintKeywords(keywords, fingerprint.plugin());
        addFingerprintKeywords(keywords, fingerprint.key());
        if (fingerprint.channels() != null) {
            for (String channel : fingerprint.channels()) addFingerprintKeywords(keywords, channel);
        }
        keywords.removeIf(keyword -> keyword.length() < 3);
        return keywords;
    }

    private void addFingerprintKeywords(Set<String> out, String text) {
        if (out == null || text == null || text.isBlank()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String label = entry.getValue().toLowerCase(Locale.ROOT);
            if (lower.contains(key) || lower.contains(label.replace(" ", "")) || lower.contains(label)) {
                out.add(key);
                out.add(label);
            }
        }
    }

    private boolean featureEntryMatches(PluginScanEntry entry, Set<String> keywords) {
        if (entry == null || keywords == null || keywords.isEmpty()) return false;
        List<String> evidenceText = new ArrayList<>();
        evidenceText.add(entry.displayName);
        evidenceText.addAll(entry.commands);
        evidenceText.addAll(entry.guis);
        for (String text : evidenceText) {
            if (text == null) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private void syncPayloadFingerprintsIntoCurrentScan() {
        if (pluginScanInProgress) {
            if (mergePayloadFingerprintEntries(scanWorkingEntries)) {
                cleanupPluginCommandOwnership(scanWorkingEntries);
                PluginScanSession session = activePluginScan;
                if (session != null) session.lastResponseAt = System.currentTimeMillis();
            }
            return;
        }
        if (!pluginScanDone) return;

        String address = currentServerAddress();
        long revision = AutismPluginPayloadFingerprints.revisionFor(address, getLiveBrand());
        if (revision == lastPayloadFingerprintRevision) return;
        lastPayloadFingerprintRevision = revision;
        if (revision <= 0L) return;

        Map<String, PluginScanEntry> entries = buildCurrentPluginEntries();
        if (mergePayloadFingerprintEntries(entries)) {
            applyPluginEntries(entries, false);
            if (pluginScanDone) cacheCurrentScan();
        }
    }

    private int addProbeVariants(Map<Integer, PluginProbeSpec> probes, Set<String> seen, int nextId,
                                 PluginProbeKind kind, String... baseCommands) {
        for (String base : baseCommands) {
            if (base == null || base.isBlank()) continue;
            String trimmed = base.trim();
            nextId = addPluginProbe(probes, seen, nextId, trimmed, kind, null);
            nextId = addPluginProbe(probes, seen, nextId, trimmed + " ", kind, null);
        }
        return nextId;
    }

    private Map<Integer, PluginProbeSpec> buildPluginProbes() {
        Map<Integer, PluginProbeSpec> probes = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int nextId = allocatePluginProbeIdBase();

        nextId = addPluginProbe(probes, seen, nextId, "/", PluginProbeKind.ROOT, null);
        nextId = addPluginProbe(probes, seen, nextId, "/ ", PluginProbeKind.ROOT, null);

        nextId = addProbeVariants(probes, seen, nextId, PluginProbeKind.PLUGIN_LIST, "/plugins", "/pl", "/bukkit:plugins", "/bukkit:pl");
        nextId = addProbeVariants(probes, seen, nextId, PluginProbeKind.VERSION, "/ver", "/version", "/about", "/icanhasbukkit", "/bukkit:ver", "/bukkit:version");
        nextId = addProbeVariants(probes, seen, nextId, PluginProbeKind.HELP, "/help", "/?", "/bukkit:help", "/minecraft:help");

        for (int i = 0; i < ROOT_PROBE_PREFIXES.length(); i++) {
            char prefix = ROOT_PROBE_PREFIXES.charAt(i);
            String rootProbe = "/" + prefix;
            nextId = addPluginProbe(probes, seen, nextId, rootProbe, PluginProbeKind.ROOT, String.valueOf(prefix));
            nextId = addPluginProbe(probes, seen, nextId, "/help " + prefix, PluginProbeKind.HELP, String.valueOf(prefix));
            nextId = addPluginProbe(probes, seen, nextId, "/? " + prefix, PluginProbeKind.HELP, String.valueOf(prefix));
        }

        for (String namespace : COMMON_PLUGIN_NAMESPACES) {
            nextId = addPluginProbe(probes, seen, nextId, "/" + namespace + ":", PluginProbeKind.NAMESPACE, namespace);
        }

        for (String plugin : HIGH_VALUE_PLUGIN_HINTS) {
            nextId = addPluginProbe(probes, seen, nextId, "/version " + plugin, PluginProbeKind.VERSION, plugin);
            nextId = addPluginProbe(probes, seen, nextId, "/ver " + plugin, PluginProbeKind.VERSION, plugin);
            nextId = addPluginProbe(probes, seen, nextId, "/help " + plugin, PluginProbeKind.HELP, plugin);
        }

        return probes;
    }

    private int allocatePluginProbeIdBase() {
        return pluginProbeIdAllocator.allocateBlock();
    }

    public boolean isAutoProbeConnectionReady() {
        return MC != null && MC.getConnection() != null && !currentServerAddress().isBlank();
    }

    public boolean isAutoProbeContextReady() {
        try {
            if (MC == null || MC.getConnection() == null) return false;
            com.mojang.brigadier.CommandDispatcher<?> dispatcher = MC.getConnection().getCommands();
            return dispatcher != null && dispatcher.getRoot() != null && !dispatcher.getRoot().getChildren().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean autoProbeOnSpawn() {
        if (!isAutoProbeConnectionReady()) return false;

        String address = currentServerAddress();
        String context = currentPluginContextSignature();
        syncScanStateForCurrentServer();
        if (pluginScanInProgress) return true;

        if (context.equals(lastAutoScanContext) && pluginScanDone && pluginScanOutcome == PluginScanOutcome.COMPLETE) return true;

        long cachedAt = AutismConfig.getGlobal().getPluginScanTimestampByAddress(address);
        boolean fresh = cachedAt > 0L && (System.currentTimeMillis() - cachedAt) < SAVED_PLUGIN_CACHE_MAX_AGE_MS;
        if (fresh && pluginScanDone && pluginScanOutcome == PluginScanOutcome.COMPLETE) {
            lastAutoScanContext = context;
            return true;
        }

        lastAutoScanContext = context;

        AutismConfig.getGlobal().removePluginScan(address);
        preparePluginRescan();
        scanPlugins();
        return pluginScanInProgress;
    }

    public void resetAutoProbeContext() {
        lastAutoScanContext = null;
    }

    public void onConnectionClosed() {
        cacheCurrentScan();
        lastAutoScanContext = null;
        clearLocalScanState(null, "");
        cachedPluginContextServerAddress = "";
        cachedPluginContextBrand = "";
        cachedPluginContextSignature = "";
        lastRadarServerAddress = "";
        lastRadarAutoCheckSignature = "";
        lastRadarAutoCheckAt = 0L;
        expandedRadarPlugins.clear();
        AutismDupeRadar.clearServerResults();
        requestUiRebuild();
    }

    private void cancelActivePluginScan(String reason) {
        PluginScanSession session = activePluginScan;
        if (session != null) {
            session.lifecycle.beginFinalize();
            tracePluginScan(session, "cancel reason=" + reason);
        }
        activePluginScan = null;
        pluginScanInProgress = false;
    }

    private void recordPluginScanError(PluginScanSession session, String stage, Exception error) {
        if (session == null || session.lifecycle.isFinalized()) return;
        session.runtimeErrors++;
        String detail = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        pluginScanIssue = stage + " failed";
        tracePluginScan(session, "error stage=" + stage + " detail=" + detail);
        LOGGER.warn("Plugin scan {} failed during {}", session.generation, stage, error);
    }

    private void finalizePluginScan() {
        finalizePluginScan(null, "");
    }

    private void finalizePluginScan(PluginScanOutcome requestedOutcome, String issue) {
        PluginScanSession session = activePluginScan;
        if (session == null || !session.lifecycle.beginFinalize()) return;

        Map<String, PluginScanEntry> finalEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        boolean processingFailed = false;
        try {
            mergeScanEntries(finalEntries, legacyScanEntries);
            mergeScanEntries(finalEntries, scanWorkingEntries);
            mergePayloadFingerprintEntries(finalEntries);
            cleanupPluginCommandOwnership(finalEntries);
        } catch (RuntimeException error) {
            processingFailed = true;
            session.runtimeErrors++;
            pluginScanIssue = "Final processing failed";
            LOGGER.warn("Plugin scan {} failed during final processing", session.generation, error);
            tracePluginScan(session, "error stage=final-processing detail=" + error.getClass().getSimpleName());
        }

        PluginScanOutcome outcome = requestedOutcome;
        if (outcome == null) {
            outcome = processingFailed || session.lifecycle.requiresPartialResult(
                session.runtimeErrors, session.failedProbes, session.pendingProbeIds.size())
                ? PluginScanOutcome.PARTIAL
                : PluginScanOutcome.COMPLETE;
        }
        if (processingFailed && finalEntries.isEmpty()) outcome = PluginScanOutcome.FAILED;
        boolean infrastructureFailure = processingFailed || session.runtimeErrors > 0
            || session.failedProbes > 0 || !session.pendingProbeIds.isEmpty();
        boolean restorePrevious = finalEntries.isEmpty() && infrastructureFailure && !session.previousEntries.isEmpty();
        if (restorePrevious) outcome = PluginScanOutcome.FAILED;

        tracePluginScan(session, "final roots=" + session.commandTreeRoots
            + " suggestionReplies=" + session.suggestionReplies
            + " suggestionEntries=" + session.suggestionEntries
            + " sent=" + session.sentProbes
            + " answered=" + session.answeredProbes
            + " retried=" + session.retriedProbes
            + " failed=" + session.failedProbes
            + " legacy=" + countPluginEntries(legacyScanEntries)
            + " working=" + countPluginEntries(scanWorkingEntries)
            + " final=" + countPluginEntries(finalEntries)
            + " outcome=" + outcome);

        try {
            if (restorePrevious) {
                applyPluginEntries(session.previousEntries, false);
            } else if (outcome != PluginScanOutcome.FAILED || !finalEntries.isEmpty()) {
                applyPluginEntries(finalEntries, true);
            }
        } catch (RuntimeException error) {
            outcome = PluginScanOutcome.FAILED;
            pluginScanIssue = "Could not publish results";
            LOGGER.warn("Plugin scan {} could not publish results", session.generation, error);
            if (!session.previousEntries.isEmpty()) {
                try {
                    applyPluginEntries(session.previousEntries, false);
                } catch (RuntimeException restoreError) {
                    LOGGER.warn("Plugin scan {} could not restore previous results", session.generation, restoreError);
                }
            }
        }

        session.contextSignature = currentPluginContextSignature();
        scannedServerAddress = session.serverAddress;
        scannedPluginContextSignature = session.contextSignature;
        lastPluginScanTotal = session.totalProbes;
        lastPluginScanAnswered = session.answeredProbes;
        lastPluginScanRetried = session.retriedProbes;
        lastPluginScanFailed = session.failedProbes;
        activePluginScan = null;
        pluginScanInProgress = false;
        pluginScanDone = true;
        pluginScanOutcome = outcome;
        if (issue != null && !issue.isBlank()) pluginScanIssue = issue;
        else if (outcome == PluginScanOutcome.COMPLETE) pluginScanIssue = "";
        else if (pluginScanIssue.isBlank()) pluginScanIssue = outcome == PluginScanOutcome.PARTIAL ? "Some probes did not reply" : "Scan failed";

        observedPluginCommands.clear();
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        applyPluginResultsLayout();
        if (outcome != PluginScanOutcome.FAILED || !detectedPlugins.isEmpty()) cacheCurrentScan();
        announcePluginScanComplete();
        activeProbeDelayMs = pluginProbeDelayMs;
    }

    private void dispatchQueuedPluginProbes() {
        if (PackHideState.isHardLocked()) {
            stopPluginScanSilently();
            return;
        }
        PluginScanSession session = activePluginScan;
        if (!pluginScanInProgress || session == null || session.lifecycle.isFinalized() || MC.getConnection() == null) return;

        long now = System.currentTimeMillis();
        if (now < session.nextSendAt) return;

        PluginProbeRequest request = session.queuedProbes.pollFirst();
        if (request == null) return;

        try {
            net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket packet =
                new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(request.id, request.spec.query);
            MC.getConnection().send(packet);
            session.sentProbes++;
            session.nextSendAt = now + activeProbeDelayMs;
        } catch (Exception error) {
            session.nextSendAt = now + activeProbeDelayMs;
            if (session.lifecycle.canRetry(request.attempt)) {
                session.retriedProbes++;
                tracePluginScan(session, "retry probe=" + request.id + " reason=" + error.getClass().getSimpleName());
                LOGGER.warn("Plugin scan {} will retry probe {}", session.generation, request.id, error);
                session.queuedProbes.addLast(new PluginProbeRequest(request.id, request.spec, request.attempt + 1));
            } else {
                session.failedProbes++;
                session.pendingProbeIds.remove(request.id);
                session.probes.remove(request.id);
                recordPluginScanError(session, "probe send", error);
            }
        }
    }

    private int getTotalPluginProbeCount() {
        PluginScanSession session = activePluginScan;
        return session == null ? 0 : Math.max(0, session.totalProbes);
    }

    private long getPluginScanSendWindowMs() {
        PluginScanSession session = activePluginScan;
        int retryCount = session == null ? 0 : session.retriedProbes;
        return Math.max(activeProbeDelayMs, (long) (getTotalPluginProbeCount() + retryCount) * activeProbeDelayMs);
    }

    private long getPluginScanFinishedSendingAt() {
        PluginScanSession session = activePluginScan;
        if (session == null || session.startedAt <= 0L) return 0L;
        if (!session.queuedProbes.isEmpty()) return 0L;
        long candidate = session.nextSendAt - activeProbeDelayMs;
        return Math.max(session.startedAt, candidate);
    }

    private long getPluginScanHardTimeoutMs() {
        return Math.max(PLUGIN_SCAN_TIMEOUT_MS, getPluginScanSendWindowMs() + PLUGIN_SCAN_FINAL_SETTLE_MS + PLUGIN_SCAN_IDLE_MS);
    }

    private int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * clamped);
        int r = Math.round(r1 + (r2 - r1) * clamped);
        int g = Math.round(g1 + (g2 - g1) * clamped);
        int b = Math.round(b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getSentPluginProbeCount() {
        PluginScanSession session = activePluginScan;
        int total = getTotalPluginProbeCount();
        return session == null ? 0 : Math.max(0, Math.min(total, session.sentProbes));
    }

    private int getAnsweredPluginProbeCount() {
        PluginScanSession session = activePluginScan;
        int total = getTotalPluginProbeCount();
        return session == null ? 0 : Math.max(0, Math.min(total, session.answeredProbes));
    }

    private float getPluginScanProgress() {
        if (pluginScanDone) return 1.0f;
        int total = getTotalPluginProbeCount();
        if (total <= 0) return 0.0f;

        float sentRatio = getSentPluginProbeCount() / (float) total;
        float answeredRatio = getAnsweredPluginProbeCount() / (float) total;
        PluginScanSession session = activePluginScan;
        if (session != null && !session.queuedProbes.isEmpty()) {
            float progress = 0.80f * sentRatio;
            progress += 0.05f * answeredRatio;
            return Math.max(0.0f, Math.min(0.85f, progress));
        }

        float progress = 0.85f;
        long finishedSendingAt = getPluginScanFinishedSendingAt();
        long waitWindow = PLUGIN_SCAN_FINAL_SETTLE_MS;
        if (finishedSendingAt > 0L && waitWindow > 0L) {
            long now = System.currentTimeMillis();
            float settle = Math.max(0.0f, Math.min(1.0f, (now - finishedSendingAt) / (float) waitWindow));
            progress += 0.14f * settle;
        }

        return Math.max(0.0f, Math.min(0.99f, progress));
    }

    private int getWorkingPluginCount() {
        return (int) scanWorkingEntries.values().stream()
            .filter(entry -> entry != null && entry.displayName != null && !entry.displayName.isBlank())
            .count();
    }

    private int getDisplayedPluginCount() {
        if (pluginScanInProgress) {
            return getWorkingPluginCount();
        }
        return detectedPlugins.size();
    }

    private String getPluginScanStatusLabel() {
        int foundCount = getDisplayedPluginCount();
        if (foundCount > 0) {
            return "Scanning plugins | found " + foundCount;
        }
        return "Scanning plugins";
    }

    private String getPluginScanPhaseLabel() {
        PluginScanSession session = activePluginScan;
        return session == null || session.queuedProbes.isEmpty() ? "Analyzing replies" : "Sending probes";
    }

    private int getPluginScanPhaseColor() {
        PluginScanSession session = activePluginScan;
        return session == null || session.queuedProbes.isEmpty() ? AutismColors.packetCyan() : AutismColors.packetYellow();
    }

    private String getPluginScanPhaseDetail() {
        PluginScanSession session = activePluginScan;
        int total = Math.max(1, getTotalPluginProbeCount());
        return session == null || session.queuedProbes.isEmpty()
            ? getAnsweredPluginProbeCount() + "/" + total
            : getSentPluginProbeCount() + "/" + total;
    }

    private void announcePluginScanComplete() {
        if (pluginScanCompletionAnnounced) return;
        pluginScanCompletionAnnounced = true;

        int count = detectedPlugins.size();
        if (count <= 0) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: no plugins found.");
        } else if (count == 1) {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found 1 plugin.");
        } else {
            AutismClientMessaging.sendPrefixed("Plugin probing finished: found " + count + " plugins.");
        }
    }

    public boolean shouldRenderBackgroundProbeBanner() {
        return pluginScanInProgress;
    }

    public boolean isPacketObservationActive() {
        return visible || pluginScanInProgress || pluginScanDone || !detectedPlugins.isEmpty();
    }

    public void renderBackgroundProbeBanner(GuiGraphicsExtractor ctx) {
        if (!shouldRenderBackgroundProbeBanner() || MC == null || MC.getWindow() == null || textRenderer == null) return;

        DirectViewport viewport = surface.viewport();
        String status = getPluginScanStatusLabel();
        String phase = getPluginScanPhaseLabel();
        String detail = getPluginScanPhaseDetail();
        int progressPercent = Math.max(0, Math.min(100, Math.round(getPluginScanProgress() * 100.0f)));
        String percentComponent = progressPercent + "%";
        int screenW = Math.round(viewport.uiWidth());
        int statusWidth = UiText.width(textRenderer, status, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED));
        int phaseWidth = UiText.width(textRenderer, phase, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY));
        int detailWidth = UiText.width(textRenderer, detail, theme.fontFor(UiTone.MUTED), getPluginScanPhaseColor());
        int percentWidth = UiText.width(textRenderer, percentComponent, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY));
        int contentW = Math.max(180, Math.max(statusWidth, Math.max(phaseWidth + 12 + detailWidth, percentWidth + 150)));

        int boxW = Math.max(1, Math.min(Math.max(1, screenW - 16), Math.max(236, contentW + 24)));
        int boxH = 42;
        int boxX = Math.max(8, (screenW - boxW) / 2);
        int boxY = 8;
        int innerX = boxX + 8;
        int barY = boxY + 17;
        int barW = boxW - 16;
        int barH = 10;

        viewport.push(ctx);
        try {
            DirectRenderContext bannerContext = new DirectRenderContext(ctx, textRenderer, viewport, theme, 0, 0, 0.0f);
            int border = theme.borderColor();
            int headerAccent = theme.headerAccent();
            UiRenderer.window(
                ctx,
                UiBounds.of(boxX, boxY, boxW, boxH),
                14,
                bannerContext.applyAlpha(theme.windowFill()),
                bannerContext.applyAlpha(theme.headerFill()),
                bannerContext.applyAlpha(theme.windowFill()),
                bannerContext.applyAlpha(border),
                bannerContext.applyAlpha(headerAccent),
                1.0f
            );

            UiText.draw(ctx, textRenderer, status, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), innerX, boxY + 3, false);
            UiText.draw(ctx, textRenderer, percentComponent, theme.fontFor(UiTone.BODY), getPluginScanPhaseColor(), boxX + boxW - 8 - percentWidth, boxY + 3, false);

            autismclient.gui.vanillaui.components.ProgressBar.render(
                autismclient.gui.vanillaui.UiContexts.overlay(ctx, textRenderer, -1, -1),
                UiBounds.of(innerX, barY, barW, barH), getPluginScanProgress());

            UiText.draw(ctx, textRenderer, phase, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY), innerX, barY + 13, false);
            UiText.draw(ctx, textRenderer, detail, theme.fontFor(UiTone.MUTED), getPluginScanPhaseColor(), boxX + boxW - 8 - detailWidth, barY + 13, false);
        } finally {
            viewport.pop(ctx);
        }
    }

    private void updatePluginScanLifecycle() {
        PluginScanSession session = activePluginScan;
        if (!pluginScanInProgress || session == null || session.lifecycle.isFinalized()) return;

        try {
            dispatchQueuedPluginProbes();

            long now = System.currentTimeMillis();
            boolean allProbesSent = session.queuedProbes.isEmpty();
            boolean allResponsesReceived = session.pendingProbeIds.isEmpty();
            long finishedSendingAt = getPluginScanFinishedSendingAt();
            long settleWindow = allResponsesReceived ? PLUGIN_SCAN_FINAL_SETTLE_MS : Math.max(PLUGIN_SCAN_FINAL_SETTLE_MS, PLUGIN_SCAN_SETTLE_MS);
            boolean settledAfterSend = allProbesSent
                && finishedSendingAt > 0L
                && now - finishedSendingAt >= settleWindow;
            boolean settledAfterLastResponse = session.lastResponseAt > 0L
                && now - session.lastResponseAt >= PLUGIN_SCAN_IDLE_MS;
            boolean timedOut = now - session.startedAt >= getPluginScanHardTimeoutMs();

            if ((allProbesSent && settledAfterSend && (!allResponsesReceived || settledAfterLastResponse)) || timedOut) {
                PluginScanOutcome outcome = !session.lifecycle.requiresPartialResult(
                    session.runtimeErrors, session.failedProbes, session.pendingProbeIds.size())
                    ? PluginScanOutcome.COMPLETE : PluginScanOutcome.PARTIAL;
                finalizePluginScan(outcome, timedOut && !allResponsesReceived ? "Some probes timed out" : "");
            }
        } catch (RuntimeException error) {
            recordPluginScanError(session, "lifecycle", error);
            finalizePluginScan(PluginScanOutcome.PARTIAL, "Scan recovered from an error");
        }
    }

    public void tickBackground() {
        try {
            syncScanStateForCurrentServer();
            syncPayloadFingerprintsIntoCurrentScan();
            if (pluginScanInProgress) updatePluginScanLifecycle();
        } catch (RuntimeException error) {
            PluginScanSession session = activePluginScan;
            if (session != null) {
                recordPluginScanError(session, "background tick", error);
                finalizePluginScan(PluginScanOutcome.PARTIAL, "Scan recovered from an error");
            } else {
                LOGGER.warn("Plugin scanner background update failed", error);
            }
        }
    }

    private void parseCommandTree(com.mojang.brigadier.tree.RootCommandNode<?> root) {
        Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        entries.putAll(scanWorkingEntries);
        Map<String, PluginScanEntry> legacyEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        legacyEntries.putAll(legacyScanEntries);
        for (var child : root.getChildren()) {
            String name = child.getName();
            PluginScanSession session = activePluginScan;
            if (session != null) session.commandTreeRoots++;
            if (name.contains(":")) {
                String[] parts = name.split(":", 2);
                String ns = parts[0].toLowerCase();
                String cmd = parts[1];
                if (!VANILLA_NAMESPACES.contains(ns)) {
                    mergePluginEntry(legacyEntries, ns, List.of(cmd), true, PluginEvidence.COMMAND_TREE);
                    mergeCommandEvidence(entries, ns + ":" + cmd, PluginEvidence.COMMAND_TREE);
                    addObservedPluginCommand(cmd);
                }
            } else {
                addObservedPluginCommand(name);
                String token = normalizeCommandToken(name);
                if (COMMAND_FEATURE_LABELS.containsKey(token)) {
                    mergeCommandEvidence(entries, name, PluginEvidence.COMMAND_TREE);
                }
            }
        }
        inferLegacyPluginsFromObservedCommands(legacyEntries);
        inferPluginsFromObservedCommands(entries);
        mergeScanEntries(entries, legacyEntries);
        cleanupPluginCommandOwnership(entries);
        legacyScanEntries.clear();
        legacyScanEntries.putAll(legacyEntries);
        scanWorkingEntries.clear();
        scanWorkingEntries.putAll(entries);
        PluginScanSession session = activePluginScan;
        tracePluginScan(session, "command tree roots=" + (session == null ? 0 : session.commandTreeRoots)
            + " legacy=" + countPluginEntries(legacyScanEntries)
            + " working=" + countPluginEntries(scanWorkingEntries));
    }

    public void resetScan() {
        clearLocalScanState(null, "");
        searchField.setText("");
        searchField.setFocused(false);
        resolvedIp = null;
        lastResolvedAddress = null;
        invalidatePluginContextSignature();
    }

    public void stopPluginScanSilently() {
        cancelActivePluginScan("hard-lock");
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        observedSuggestionRequests.clear();
        if (!detectedPlugins.isEmpty()) pluginScanDone = true;
        uiRebuildRequested = true;
    }

    private void preparePluginRescan() {
        cancelActivePluginScan("restart");
        pluginScanDone = false;
        pluginManualSetupOpen = false;
        pluginScanCompletionAnnounced = false;
        pluginScanOutcome = PluginScanOutcome.NONE;
        pluginScanIssue = "";
        observedPluginCommands.clear();
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        selectedPlugin = null;
        searchField.setText("");
        searchField.setFocused(false);
        pluginScrollState.jumpTo(0, 0);
    }

    private void startManualPluginScan() {
        if (configurationOnly || MC.getConnection() == null) return;
        String address = currentServerAddress();
        AutismConfig.getGlobal().removePluginScan(address);
        preparePluginRescan();
        scanPlugins(true);
        saveState();
    }

    private int addPluginProbe(Map<Integer, PluginProbeSpec> probes, Set<String> seen, int nextId,
                               String query, PluginProbeKind kind, String hint) {
        if (query == null || query.isBlank()) return nextId;
        String key = kind.name() + "|" + query + "|" + (hint == null ? "" : hint);
        if (!seen.add(key)) return nextId;
        probes.put(nextId++, new PluginProbeSpec(query, kind, hint));
        return nextId;
    }

    private void scanPlugins() {
        scanPlugins(false);
    }

    private void scanPlugins(boolean forceLive) {
        if (configurationOnly || PackHideState.isHardLocked()) return;
        if (!forceLive) {
            syncScanStateForCurrentServer();
        }

        if ((!forceLive && pluginScanDone) || pluginScanInProgress) return;
        if (MC.getConnection() == null) {
            pluginScanDone = true;
            pluginScanOutcome = PluginScanOutcome.FAILED;
            pluginScanIssue = "Not connected";
            return;
        }

        activeProbeDelayMs = pluginProbeDelayMs;
        scannedServerAddress = currentServerAddress();
        scannedPluginContextSignature = currentPluginContextSignature();
        long startedAt = System.currentTimeMillis();
        PluginScanSession session = new PluginScanSession(
            nextPluginScanGeneration++, scannedServerAddress, scannedPluginContextSignature, startedAt,
            buildCurrentPluginEntries()
        );
        session.nextSendAt = startedAt + activeProbeDelayMs;
        activePluginScan = session;
        activeTab = 1;
        pluginScanInProgress = true;
        pluginScanDone = false;
        observedPluginCommands.clear();
        scanWorkingEntries.clear();
        legacyScanEntries.clear();
        pluginScanOutcome = PluginScanOutcome.NONE;
        pluginScanIssue = "";
        applyPluginScanningLayout();
        try {
            com.mojang.brigadier.CommandDispatcher<?> dispatcher =
                MC.getConnection().getCommands();
            if (dispatcher != null) {
                com.mojang.brigadier.tree.RootCommandNode<?> root = dispatcher.getRoot();
                if (root != null && !root.getChildren().isEmpty()) {
                    parseCommandTree(root);
                }
            }
            mergePayloadFingerprintEntries(scanWorkingEntries);

            Map<Integer, PluginProbeSpec> probes = buildPluginProbes();
            session.totalProbes = probes.size();
            for (Map.Entry<Integer, PluginProbeSpec> probe : probes.entrySet()) {
                session.pendingProbeIds.add(probe.getKey());
                session.probes.put(probe.getKey(), probe.getValue());
                session.queuedProbes.addLast(new PluginProbeRequest(probe.getKey(), probe.getValue()));
            }
            tracePluginScan(session, "start probes=" + session.totalProbes + " delay=" + activeProbeDelayMs + "ms");
            updatePluginScanLifecycle();
        } catch (Exception error) {
            recordPluginScanError(session, "startup", error);
            finalizePluginScan(PluginScanOutcome.PARTIAL, "Scan recovered from a startup error");
        }
    }

    public void onCommandSuggestionRequest(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        if (!"ServerboundCommandSuggestionPacket".equals(packet.getClass().getSimpleName())) return;
        int id = intValue(packet, -1, "getId", "id");
        String command = stringValue(packet, "getCommand", "command");
        if (id < 0 || command == null || command.isBlank()) return;

        if (AutismCommandSuggestionIds.isMacroId(id)) return;

        MC.execute(() -> {
            PluginScanSession session = activePluginScan;
            if (session != null && session.probes.containsKey(id)) return;
            observedSuggestionRequests.put(id, new PluginProbeSpec(command, PluginProbeKind.USER, null));
            while (observedSuggestionRequests.size() > MAX_TRACKED_SUGGESTION_REQUESTS) {
                Integer first = observedSuggestionRequests.keySet().iterator().next();
                observedSuggestionRequests.remove(first);
            }
        });
    }

    public void onOutgoingCommandPacket(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        String simple = packet.getClass().getSimpleName();
        String command = null;
        if ("ServerboundChatCommandPacket".equals(simple) || "ServerboundChatCommandSignedPacket".equals(simple)) {
            command = stringValue(packet, "command", "getCommand");
        } else if ("ServerboundChatPacket".equals(simple)) {
            String message = stringValue(packet, "message", "getMessage");
            if (message != null && message.trim().startsWith("/")) {
                command = message.trim().substring(1);
            }
        }
        if (command == null || command.isBlank()) return;
        String root = normalizeCommandToken(command);
        if (!isTrackableCommandToken(root)) return;
        String fullCommand = command;

        MC.execute(() -> {
            registerRecentCommand(root, fullCommand);
            mergeLiveCommandEvidence(fullCommand, PluginEvidence.USER_AUTOCOMPLETE, null);
        });
    }

    public void onOpenScreenPacket(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet == null || PackHideState.isActive()) return;
        if (!"ClientboundOpenScreenPacket".equals(packet.getClass().getSimpleName())) return;
        String title = componentText(invokeFirstNoArg(packet, "getTitle", "title"));
        if (title == null || title.isBlank()) return;

        MC.execute(() -> {
            ObservedCommand command = findRecentCommand(System.currentTimeMillis());
            if (command != null) {
                mergeLiveCommandEvidence(command.fullCommand, PluginEvidence.COMMAND_GUI, title);
                return;
            }

            String feature = featureLabelFromGuiTitle(title);
            if (feature == null || feature.isBlank()) return;
            mergeLiveGuiFeature(feature, title);
        });
    }

    private void registerRecentCommand(String root, String fullCommand) {
        if (root == null || root.isBlank()) return;
        recentCommands.addLast(new ObservedCommand(root, fullCommand == null ? root : fullCommand, System.currentTimeMillis()));
        while (recentCommands.size() > MAX_RECENT_COMMANDS) recentCommands.removeFirst();
    }

    private ObservedCommand findRecentCommand(long now) {
        ObservedCommand best = null;
        while (!recentCommands.isEmpty() && now - recentCommands.peekFirst().timestampMs > COMMAND_GUI_LINK_MS) {
            recentCommands.removeFirst();
        }
        for (ObservedCommand command : recentCommands) {
            if (command == null) continue;
            if (now - command.timestampMs <= COMMAND_GUI_LINK_MS) best = command;
        }
        return best;
    }

    private void mergeLiveCommandEvidence(String rawCommand, PluginEvidence evidence, String guiTitle) {
        if (rawCommand == null || rawCommand.isBlank()) return;
        MC.execute(() -> {
            if (MC.getConnection() == null) return;
            syncScanStateForCurrentServer();
            registerRecentCommand(normalizeCommandToken(rawCommand), rawCommand);
            Map<String, PluginScanEntry> entries = pluginScanInProgress
                ? new TreeMap<>(scanWorkingEntries)
                : buildCurrentPluginEntries();
            boolean changed = mergeCommandEvidence(entries, rawCommand, evidence);
            if (guiTitle != null && !guiTitle.isBlank()) {
                changed |= attachGuiToCommandEntry(entries, rawCommand, guiTitle);
            }
            if (!changed) return;
            applyLivePluginEntries(entries);
        });
    }

    private void mergeLiveGuiFeature(String feature, String guiTitle) {

    }

    private boolean attachGuiToCommandEntry(Map<String, PluginScanEntry> entries, String rawCommand, String guiTitle) {
        String token = normalizeCommandToken(rawCommand);
        if (token.isBlank() || guiTitle == null || guiTitle.isBlank()) return false;
        String ownerKey = bestCommandOwnerKey(entries, token);
        if (ownerKey == null) return false;
        PluginScanEntry entry = entries.get(ownerKey);
        if (entry == null) return false;
        int before = entry.guis.size();
        entry.guis.add(guiTitle.trim());
        if (!isWeakCommandEvidence(entry.evidence)) {
            entry.evidence = mergeEvidence(entry.evidence, PluginEvidence.COMMAND_GUI);
        }
        entry.confidence = mergeConfidence(entry.confidence, PluginConfidence.FEATURE);
        pluginEvidence.put(ownerKey, mergeEvidence(pluginEvidence.get(ownerKey), entry.evidence));
        pluginConfidence.put(ownerKey, mergeConfidence(pluginConfidence.get(ownerKey), PluginConfidence.FEATURE));
        boolean changed = before != entry.guis.size();
        return changed;
    }

    private void applyLivePluginEntries(Map<String, PluginScanEntry> entries) {
        mergePayloadFingerprintEntries(entries);
        cleanupPluginCommandOwnership(entries);
        if (pluginScanInProgress) {
            scanWorkingEntries.clear();
            scanWorkingEntries.putAll(entries);
            PluginScanSession session = activePluginScan;
            if (session != null) session.lastResponseAt = System.currentTimeMillis();
            invalidatePluginRows();
            return;
        }
        if (!pluginScanDone) return;

        scannedServerAddress = currentServerAddress();
        scannedPluginContextSignature = currentPluginContextSignature();
        pluginScanDone = true;
        pluginScanInProgress = false;
        applyPluginEntries(entries, false);
        cacheCurrentScan();
    }

    private String featureLabelFromGuiTitle(String title) {
        if (title == null || title.isBlank()) return null;
        String clean = title.toLowerCase(Locale.ROOT).replaceAll("\u00a7.", "");
        for (Map.Entry<String, String> entry : COMMAND_FEATURE_LABELS.entrySet()) {
            String key = entry.getKey();
            if (key.length() >= 3 && clean.contains(key)) return entry.getValue();
        }
        return null;
    }

    public void onCommandSuggestions(int id, net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket packet) {
        if (AutismCommandSuggestionIds.isMacroId(id)) return;

        MC.execute(() -> {
            PluginScanSession session = activePluginScan;
            PluginProbeSpec probe = session == null ? null : session.probes.get(id);
            PluginProbeSpec observed = observedSuggestionRequests.remove(id);
            boolean scanProbe = probe != null || session != null && session.pendingProbeIds.contains(id);
            if (!scanProbe && observed == null) return;
            long capturedGeneration = scanProbe ? session.generation : -1L;
            boolean liveObservation = !scanProbe;
            if (!liveObservation) {
                String address = currentServerAddress();
                if (!pluginScanInProgress || !session.matches(capturedGeneration, address)) return;
            }

            try {
                var list = packet == null || packet.suggestions() == null ? List.<net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry>of() : packet.suggestions();
                Map<String, PluginScanEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                if (pluginScanInProgress) entries.putAll(scanWorkingEntries);
                else entries.putAll(buildCurrentPluginEntries());
                Map<String, PluginScanEntry> legacyEntries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                legacyEntries.putAll(legacyScanEntries);
                if (scanProbe && session != null) {
                    if (session.pendingProbeIds.remove(id)) session.answeredProbes++;
                    session.probes.remove(id);
                    session.lastResponseAt = System.currentTimeMillis();
                    session.suggestionReplies++;
                    session.suggestionEntries += list.size();
                }

                for (var suggestion : list) {
                    if (suggestion == null || suggestion.text() == null) continue;
                    String text = suggestion.text();
                    String normalizedToken = normalizeCommandToken(text);
                    if (!normalizedToken.isEmpty()) addObservedPluginCommand(normalizedToken);

                    if (text.contains(":")) {
                        String[] parts = text.split(":", 2);
                        String ns = parts[0].toLowerCase(Locale.ROOT);
                        String cmd = parts.length > 1 ? parts[1] : "";
                        if (!VANILLA_NAMESPACES.contains(ns)) {
                            mergePluginEntry(legacyEntries, ns, List.of(cmd), true, PluginEvidence.COMMAND_TREE);
                            addObservedPluginCommand(cmd);
                        }
                        mergeCommandEvidence(entries, text, scanProbe ? PluginEvidence.COMMAND_TREE : PluginEvidence.USER_AUTOCOMPLETE);
                        continue;
                    }

                    if (probe != null) {
                        if (probe.kind == PluginProbeKind.NAMESPACE && probe.hint != null && !normalizedToken.isEmpty()) {
                            mergePluginEntry(legacyEntries, probe.hint, List.of(normalizedToken), true, PluginEvidence.NAMESPACE);
                            mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, PluginEvidence.NAMESPACE);
                        } else if ((probe.kind == PluginProbeKind.PLUGIN_LIST || probe.kind == PluginProbeKind.VERSION)) {
                            String pluginCandidate = text.trim();
                            String pluginKey = normalizePluginKey(pluginCandidate);
                            if (isLikelyPluginNameCandidate(pluginCandidate, probe.kind)
                                && !ROOT_COMMAND_PLUGIN_ALIASES.containsKey(pluginKey)) {
                                PluginEvidence candidateEvidence = probe.kind == PluginProbeKind.PLUGIN_LIST
                                    ? PluginEvidence.PLUGIN_LIST : PluginEvidence.VERSION_HINT;
                                PluginConfidence candidateConfidence = confidenceForEvidence(candidateEvidence);
                                mergePluginEntry(legacyEntries, null, pluginCandidate, List.of(), List.of(), List.of(), false,
                                    candidateEvidence, candidateConfidence, PluginResultKind.PLUGIN);
                                mergePluginEntry(entries, null, pluginCandidate, List.of(), List.of(), List.of(), false,
                                    candidateEvidence, candidateConfidence, PluginResultKind.PLUGIN);
                            }
                        } else if (probe.kind == PluginProbeKind.HELP && probe.hint != null && !normalizedToken.isEmpty()) {
                            mergePluginEntry(legacyEntries, probe.hint, List.of(normalizedToken), true, PluginEvidence.HELP_HINT);
                            mergePluginEntry(entries, probe.hint, List.of(normalizedToken), true, PluginEvidence.HELP_HINT);
                        } else if (probe.kind == PluginProbeKind.ROOT && !normalizedToken.isEmpty()) {
                            mergeScannerAutocompleteHint(entries, normalizedToken);
                        }
                    } else if (observed != null && !normalizedToken.isEmpty()) {
                        mergeCommandEvidence(entries, normalizedToken, PluginEvidence.USER_AUTOCOMPLETE);
                    }
                }

                inferLegacyPluginsFromObservedCommands(legacyEntries);
                inferPluginsFromObservedCommands(entries);
                mergeScanEntries(entries, legacyEntries);
                cleanupPluginCommandOwnership(entries);
                if (scanProbe || pluginScanInProgress) {
                    legacyScanEntries.clear();
                    legacyScanEntries.putAll(legacyEntries);
                    scanWorkingEntries.clear();
                    scanWorkingEntries.putAll(entries);
                    tracePluginScan(session, "suggestions replies=" + (session == null ? 0 : session.suggestionReplies)
                        + " suggestions=" + (session == null ? 0 : session.suggestionEntries)
                        + " legacy=" + countPluginEntries(legacyScanEntries)
                        + " working=" + countPluginEntries(scanWorkingEntries));
                    updatePluginScanLifecycle();
                } else {
                    applyLivePluginEntries(entries);
                }
            } catch (RuntimeException error) {
                if (scanProbe && session != null && session.matches(capturedGeneration, currentServerAddress())) {
                    session.pendingProbeIds.remove(id);
                    session.probes.remove(id);
                    session.failedProbes++;
                    recordPluginScanError(session, "suggestion reply", error);
                    updatePluginScanLifecycle();
                } else {
                    LOGGER.warn("Plugin autocomplete observation failed", error);
                }
            }
        });
    }

    public void onCommandTreeChanged() {
        invalidatePluginContextSignature();
        MC.execute(() -> {
            String currentAddress = currentServerAddress();
            if (currentAddress.isEmpty()) return;

            String currentContextSignature = currentPluginContextSignature();
            PluginScanSession session = activePluginScan;
            if (session != null && pluginScanInProgress && currentAddress.equals(session.serverAddress)) {
                session.contextSignature = currentContextSignature;
                scannedPluginContextSignature = currentContextSignature;
                try {
                    var dispatcher = MC.getConnection() == null ? null : MC.getConnection().getCommands();
                    if (dispatcher != null && dispatcher.getRoot() != null) parseCommandTree(dispatcher.getRoot());
                } catch (RuntimeException error) {
                    recordPluginScanError(session, "command tree update", error);
                }
                return;
            }

            AutismPluginPayloadFingerprints.clearSession();
            lastPayloadFingerprintRevision = -1L;
            if (currentAddress.equals(scannedServerAddress) && pluginScanDone
                && AutismConfig.getGlobal().autoProbePlugins) {
                preparePluginRescan();
                scanPlugins(true);
            } else if (currentAddress.equals(scannedServerAddress) && pluginScanDone) {
                clearLocalScanState(currentAddress, currentContextSignature);
            } else {
                syncScanStateForCurrentServer();
            }
        });
    }

    public void onPayloadFingerprintUpdated() {
        lastPayloadFingerprintRevision = -1L;
        invalidatePluginRows();
        invalidatePluginContextSignature();
    }

    public void openInfoTab() {
        setVisible(true);
        selectTab(0);
    }

    public void openPluginsTab() {
        setVisible(true);
        selectTab(1);
    }

    public void toggle() { setVisible(!visible); }

    @Override
    public void setVisible(boolean v) {
        this.visible = v;
        if (v) {
            windowNode.syncShowBody(!collapsed);
            AutismOverlayManager.get().bringToFront(this);
            syncScanStateForCurrentServer();
            if (activeTab == 0) {
                applyInfoLayout();
            } else {
                if (pluginScanInProgress) applyPluginScanningLayout();
                else if (!pluginScanDone) applyPluginSetupLayout();
                else applyPluginResultsLayout();
            }
        }
        saveState();
    }

    @Override public boolean isVisible() { return visible; }
    @Override public boolean isCollapsed() { return collapsed; }
    @Override public void setCollapsed(boolean c) {
        if (collapsed == c) return;
        this.collapsed = c;
        windowNode.syncShowBody(!collapsed);
        if (c) clearHiddenInteractionState();
        saveState();
    }
    @Override public String getOverlayId() { return OVERLAY_ID; }

    @Override
    public boolean isMouseOver(double mx, double my) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mx);
        float uiY = viewport.toUiY(my);
        return uiX >= panelX && uiX <= panelX + panelW && uiY >= panelY && uiY <= panelY + panelH;
    }

    @Override
    public boolean isOverDragBar(double mx, double my) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        return isOverHeaderUi(viewport.toUiX(mx), viewport.toUiY(my));
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout b) {
        if (b == null) return;
        AutismWindowLayout clamped = clampToViewport(b);
        panelX = clamped.x; panelY = clamped.y; panelW = clamped.width; panelH = clamped.height;
        visible = clamped.visible; collapsed = clamped.collapsed;
        windowNode.syncShowBody(!collapsed);
        rememberCurrentTabSize();
    }

    @Override
    public int getMinWidth() {
        return getSharedPanelWidth();
    }

    @Override
    public int getMinHeight() {
        if (activeTab == 2) {
            return getRadarPanelHeight();
        }
        if (activeTab == 1) {
            if (pluginScanInProgress) return getPluginScanningPanelHeight();
            if (!pluginScanDone) return getPluginSetupPanelHeight();
            return getPluginResultsMinHeight();
        }
        return measurePreferredInfoPanelHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;

        syncScanStateForCurrentServer();
        syncPayloadFingerprintsIntoCurrentScan();
        updatePluginScanLifecycle();
        long nowMs = System.currentTimeMillis();
        if (uiRebuildRequested
            || pluginScanInProgress && nowMs - lastUiRebuildMs >= 100L
            || activeTab == 2 && nowMs - lastUiRebuildMs >= 500L) {
            clickRegions.clear();
            rebuildUi();
            lastUiRebuildMs = nowMs;
        }

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mx);
        float uiMouseY = viewport.toUiY(my);
        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = isOverHeaderUi(uiMouseX, uiMouseY);
        DirectRenderContext metrics = new DirectRenderContext(ctx, textRenderer, viewport, theme, uiMouseX, uiMouseY, delta);

        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);

        int preferredHeight = Math.round(windowNode.preferredHeight(metrics, panelW));
        if (collapsed) {
            panelH = preferredHeight;
        } else {
            if (activeTab == 0) {
                panelH = getSharedPanelHeight();
            } else if (activeTab == 2) {
                panelH = getRadarPanelHeight();
            } else if (pluginScanInProgress) {
                panelH = getPluginScanningPanelHeight();
            } else if (!pluginScanDone) {
                panelH = getPluginSetupPanelHeight();
            } else {
                panelH = Math.max(getPluginResultsMinHeight(), Math.min(getPluginResultsMaxHeight(), getSharedPanelHeight()));
            }
        }
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;

        windowNode.setBounds(panelX, panelY, panelW, panelH);
        surface.render(ctx, mx, my, delta);

        if (!collapsed && activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            clickRegions.clear();
            renderPluginResultsViewport(ctx, viewport, uiMouseX, uiMouseY, delta);
        } else if (!collapsed && activeTab == 2 && isRadarAuthenticated()) {
            clickRegions.clear();
            renderRadarViewport(ctx, viewport, uiMouseX, uiMouseY, delta);
        }
    }

    private String getReportedVersion(ServerData entry) {
        return entry != null && entry.version != null ? entry.version.getString() : "--";
    }

    private String getRealServerVersion() {
        String version = AutismSharedState.get().getRealServerVersion(getDisplayedServerAddress());
        if (version != null && !version.isBlank()) return version;
        String brand = getLiveBrand();
        if (brand != null && !brand.isBlank() && !"--".equals(brand)) {
            String extracted = extractVersionFromBrand(brand);
            if (extracted != null) return extracted;
        }
        return "--";
    }

    private String extractVersionFromBrand(String brand) {
        if (brand == null || brand.isBlank()) return null;
        String lower = brand.toLowerCase(Locale.ROOT);
        int dashIdx = lower.indexOf('-');
        if (dashIdx >= 0 && dashIdx + 1 < brand.length()) {
            String afterDash = brand.substring(dashIdx + 1);
            int spaceIdx = afterDash.indexOf(' ');
            if (spaceIdx >= 0) {
                return afterDash.substring(0, spaceIdx);
            }
            return afterDash;
        }
        return null;
    }

    private void renderPluginResultsViewport(GuiGraphicsExtractor ctx, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
        int x = Math.round(pluginListSlot.x());
        int y = Math.round(pluginListSlot.y());
        int rowW = Math.round(pluginListSlot.width());
        int bodyBottom = panelY + panelH - 10;
        int rawViewH = Math.max(0, Math.min(Math.round(pluginListSlot.height()), bodyBottom - y));
        int viewH = getPluginlistViewportHeight(rawViewH);
        int listTop = y;
        int listBottom = listTop + viewH;
        if (rowW <= 0 || viewH <= 0) return;
        int clipLeft = x + 1;
        int clipTop = listTop + 1;
        int clipRight = x + rowW - 1;
        int clipBottom = listBottom - 1;
        int innerViewH = getPluginListInnerHeight(viewH);
        if (innerViewH <= 0) return;

        int rowContentW = Math.max(48, clipRight - clipLeft);
        List<PluginListRow> rows = getCachedPluginRows(searchField.text(), rowContentW);
        int estimatedContentHeight = estimatePluginContentHeight(rows);
        int maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
        int quantizedTarget = quantizeScrollOffset(pluginScrollState.targetOffset(), pluginListRowStep(), maxScroll);
        pluginScrollState.setTarget(quantizedTarget, maxScroll);
        int visualScroll = quantizeScrollOffset(pluginScrollState.tick(delta, maxScroll), pluginListRowStep(), maxScroll);
        pluginContentHeight = estimatedContentHeight;

        CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(x, listTop, rowW, viewH);
        int scrolledRowContentW = Math.max(48, (clipRight - clipLeft) - (scrollbarMetrics.hasScroll() ? 10 : 0));
        if (scrolledRowContentW != rowContentW) {
            rowContentW = scrolledRowContentW;
            rows = getCachedPluginRows(searchField.text(), rowContentW);
            estimatedContentHeight = estimatePluginContentHeight(rows);
            maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
            quantizedTarget = quantizeScrollOffset(pluginScrollState.targetOffset(), pluginListRowStep(), maxScroll);
            pluginScrollState.setTarget(quantizedTarget, maxScroll);
            visualScroll = quantizeScrollOffset(pluginScrollState.tick(0.0f, maxScroll), pluginListRowStep(), maxScroll);
            pluginContentHeight = estimatedContentHeight;
            scrollbarMetrics = getPluginScrollbarMetrics(x, listTop, rowW, viewH);
        }

        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);

        viewport.push(ctx);
        try {
            CompactListRenderer.drawFrame(ctx, x, listTop, rowW, viewH, active);
            viewport.enableScissor(ctx, clipLeft, clipTop, clipRight, clipBottom);
            try {
                int startIndex = firstVisiblePluginRowIndex(rows, visualScroll);
                int cy = clipTop - visualScroll + pluginRowOffset(startIndex);
                for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
                    PluginListRow row = rows.get(rowIndex);
                    int rowY = cy;
                    int rowTop = rowY;
                    int rowBottom = rowY + row.height;
                    boolean rowVisible = rowBottom > clipTop && rowTop < clipBottom;

                    if (row.header()) {
                        if (rowVisible) {
                            CompactSurfaces.header(ctx, clipLeft, rowTop, clipRight - clipLeft, rowBottom - rowTop);
                            int headerTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            UiText.draw(ctx, textRenderer, row.title, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), clipLeft + rowTextInset(), headerTextY, false);
                        }
                    } else if (row.type == PluginRowType.PLUGIN) {
                        boolean standaloneCommandHint = isStandaloneCommandHintRow(row);
                        boolean selected = !standaloneCommandHint && isSamePlugin(row.plugin, selectedPlugin);
                        boolean hovered = rowVisible && uiMouseX >= clipLeft && uiMouseX < clipLeft + rowContentW && uiMouseY >= rowTop && uiMouseY < rowBottom;
                        if (rowVisible) {
                            CompactSurfaces.row(ctx, clipLeft, rowTop, rowContentW, rowBottom - rowTop, hovered, selected);
                        }

                        int rowTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
                        if (rowVisible && !standaloneCommandHint) {
                            int arrowColor = hovered || selected ? theme.color(UiTone.BODY) : theme.color(UiTone.MUTED);
                            UiRenderer.chevron(ctx, UiBounds.of(clipLeft + 2, rowTextY, 9, theme.fontHeight(UiTone.BODY)), selected, arrowColor);
                        }

                        boolean isAnticheat = row.plugin != null && ANTICHEATS.contains(row.plugin.toLowerCase(Locale.ROOT));
                        int nameColor = isAnticheat ? AutismTheme.recolor(0xFFFF5555, AutismTheme.Channel.DANGER) : getConfidenceColor(row.confidence, hovered);
                        String label = isAnticheat ? "! " + row.plugin : row.plugin;
                        int labelX = clipLeft + (standaloneCommandHint ? 5 : 13);
                        int labelMaxWidth = Math.max(40, rowContentW - 18);
                        if (row.countLabel != null && !row.countLabel.isBlank()) {
                            int cw = UiText.width(textRenderer, row.countLabel, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED));
                            int countX = clipLeft + rowContentW - cw - 4;
                            labelMaxWidth = Math.max(36, countX - labelX - 4);
                            int countTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            if (rowVisible) {
                                UiText.draw(ctx, textRenderer, row.countLabel, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), countX, countTextY, false);
                            }
                        }
                        if (rowVisible) {
                            String displayLabel = UiText.trimToWidth(textRenderer, label, labelMaxWidth, theme.fontFor(UiTone.BODY), nameColor);
                            UiText.draw(ctx, textRenderer, displayLabel, theme.fontFor(UiTone.BODY), nameColor, labelX, rowTextY, false);
                            if (!standaloneCommandHint) {
                                final String clickedPlugin = row.plugin;
                                int hitTop = Math.max(rowTop, clipTop);
                                int hitBottom = Math.min(rowBottom, clipBottom);
                                clickRegions.add(new ClickRegion(clipLeft, hitTop, rowContentW, Math.max(1, hitBottom - hitTop), () -> {
                                    selectedPlugin = isSamePlugin(clickedPlugin, selectedPlugin) ? null : clickedPlugin;
                                    invalidatePluginRowView();
                                    saveState();
                                }));
                            } else {

                                final String hintCommand = row.plugin;
                                int hitTop = Math.max(rowTop, clipTop);
                                int hitBottom = Math.min(rowBottom, clipBottom);
                                clickRegions.add(new ClickRegion(clipLeft, hitTop, rowContentW, Math.max(1, hitBottom - hitTop), () -> {
                                    clearTextFieldFocus();
                                    clickRegions.clear();
                                    String fullCmd = hintCommand.startsWith("/") ? hintCommand : "/" + hintCommand;
                                    AutismOverlayManager.get().setTemporarilyHidden(this, true);
                                    MC.gui.setScreen(new ChatScreen(fullCmd, false));
                                }));
                            }
                        }
                    } else {
                        boolean commandRow = row.type == PluginRowType.COMMAND;
                        boolean channelRow = row.type == PluginRowType.CHANNEL && row.actionCommand != null && !row.actionCommand.isBlank();
                        boolean clickableRow = commandRow || channelRow;
                        boolean hovered = clickableRow && rowVisible && uiMouseX >= clipLeft + 9 && uiMouseX < clipLeft + rowContentW - 6 && uiMouseY >= rowTop && uiMouseY < rowBottom;
                        if (rowVisible) {
                            int detailColor = pluginDetailColor(row);
                            CompactSurfaces.row(ctx, clipLeft + 5, rowTop, rowContentW - 9, rowBottom - rowTop, hovered, false);
                            CompactSurfaces.indicator(ctx, clipLeft + 5, rowTop, 1, rowBottom - rowTop, detailColor);
                            int textColor = hovered ? theme.color(UiTone.BODY) : detailColor;
                            String detailLabel = UiText.trimToWidth(textRenderer, row.label, Math.max(20, rowContentW - 20), theme.fontFor(UiTone.MUTED), textColor);
                            int detailTextY = UiSizing.alignTextY(rowY, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
                            UiText.draw(ctx, textRenderer, detailLabel, theme.fontFor(UiTone.MUTED), textColor, clipLeft + 11, detailTextY, false);
                        }
                        if (rowVisible && clickableRow && row.actionCommand != null && !row.actionCommand.isBlank()) {
                            final String actionValue = row.actionCommand;
                            int hitTop = Math.max(rowTop, clipTop);
                            int hitBottom = Math.min(rowBottom, clipBottom);
                            clickRegions.add(new ClickRegion(clipLeft + 9, hitTop, Math.max(1, rowContentW - 15), Math.max(1, hitBottom - hitTop), () -> {
                                clearTextFieldFocus();
                                clickRegions.clear();
                                if (commandRow) {
                                    String fullCmd = actionValue.startsWith("/") ? actionValue : "/" + actionValue;
                                    AutismOverlayManager.get().setTemporarilyHidden(this, true);
                                    MC.gui.setScreen(new ChatScreen(fullCmd, false));
                                } else {
                                    openPayloadSenderForChannel(actionValue);
                                }
                            }));
                        }
                    }

                    cy += row.height;
                    if (cy > clipBottom + pluginListRowStep()) break;
                }
            } finally {
                viewport.disableScissor(ctx);
            }
            CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(uiMouseX, uiMouseY), pluginScrollbarDragging);
        } finally {
            viewport.pop(ctx);
        }
    }

    private int pluginDetailColor(PluginListRow row) {
        if (row == null) return theme.color(UiTone.MUTED);
        return switch (row.type) {
            case COMMAND -> 0xFFE5D0D0;
            case GUI -> AutismColors.packetYellow();
            case CHANNEL -> channelDetailColor(row.label == null ? null : row.label.replaceFirst("^ch\\s+", ""));
            case WHY -> getConfidenceColor(row.confidence, false);
            case HEADER, PLUGIN -> theme.color(UiTone.MUTED);
        };
    }

    private boolean isStandaloneCommandHintRow(PluginListRow row) {
        return row != null
            && row.type == PluginRowType.PLUGIN
            && row.evidence == PluginEvidence.SCANNER_AUTOCOMPLETE
            && row.plugin != null
            && row.plugin.startsWith("/");
    }

    private void renderRadarViewport(GuiGraphicsExtractor ctx, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
        int x = Math.round(radarListSlot.x());
        int y = Math.round(radarListSlot.y());
        int rowW = Math.round(radarListSlot.width());
        int bodyBottom = panelY + panelH - 10;
        int rawViewH = Math.max(0, Math.min(Math.round(radarListSlot.height()), bodyBottom - y));
        int viewH = getPluginlistViewportHeight(rawViewH);
        if (rowW <= 0 || viewH <= 0) return;

        int clipLeft = x + 1;
        int clipTop = y + 1;
        int clipRight = x + rowW - 1;
        int clipBottom = y + viewH - 1;
        int innerViewH = getPluginListInnerHeight(viewH);
        if (innerViewH <= 0) return;

        int rowContentW = Math.max(48, clipRight - clipLeft);
        List<RadarListRow> rows = buildRadarRows(rowContentW);
        int estimatedContentHeight = estimateRadarContentHeight(rows);
        int maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
        radarScrollState.setTarget(radarScrollState.targetOffset(), maxScroll);
        int visualScroll = radarScrollState.tick(delta, maxScroll);
        radarContentHeight = estimatedContentHeight;

        CompactScrollbar.Metrics scrollbarMetrics = getRadarScrollbarMetrics(x, y, rowW, viewH);
        if (scrollbarMetrics.hasScroll()) {
            rowContentW = Math.max(48, (clipRight - clipLeft) - 10);
            rows = buildRadarRows(rowContentW);
            estimatedContentHeight = estimateRadarContentHeight(rows);
            maxScroll = Math.max(0, estimatedContentHeight - innerViewH);
            radarScrollState.setTarget(radarScrollState.targetOffset(), maxScroll);
            visualScroll = radarScrollState.tick(0.0f, maxScroll);
            radarContentHeight = estimatedContentHeight;
            scrollbarMetrics = getRadarScrollbarMetrics(x, y, rowW, viewH);
        }

        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        viewport.push(ctx);
        try {
            CompactListRenderer.drawFrame(ctx, x, y, rowW, viewH, active);
            viewport.enableScissor(ctx, clipLeft, clipTop, clipRight, clipBottom);
            try {
                int startIndex = firstVisibleRadarRowIndex(rows, visualScroll);
                int cy = clipTop - visualScroll + radarRowOffset(rows, startIndex);
                for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
                    RadarListRow row = rows.get(rowIndex);
                    int rowY = cy;
                    int rowBottom = rowY + row.height;
                    boolean rowVisible = rowBottom > clipTop && rowY < clipBottom;
                    if (rowVisible) {
                        renderRadarRow(ctx, row, clipLeft, rowY, rowContentW, uiMouseX, uiMouseY, clipTop, clipBottom);
                    }
                    cy += row.height;
                    if (cy > clipBottom + pluginListRowStep()) break;
                }
            } finally {
                viewport.disableScissor(ctx);
            }
            CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(uiMouseX, uiMouseY), radarScrollbarDragging);
        } finally {
            viewport.pop(ctx);
        }
    }

    private void renderRadarRow(GuiGraphicsExtractor ctx, RadarListRow row, int x, int y, int width,
                                float uiMouseX, float uiMouseY, int clipTop, int clipBottom) {
        if (row.type == RadarRowType.HEADER) {
            CompactSurfaces.header(ctx, x, y, width, row.height);
            int textY = UiSizing.alignTextY(y, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
            UiText.draw(ctx, textRenderer, row.title, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), x + rowTextInset(), textY, false);
            return;
        }
        if (row.type == RadarRowType.EMPTY) {
            CompactSurfaces.row(ctx, x, y, width, row.height, false, false);
            int textY = UiSizing.alignTextY(y, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
            String text = UiText.trimToWidth(textRenderer, row.title, Math.max(20, width - 10), theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED));
            UiText.draw(ctx, textRenderer, text, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), x + 5, textY, false);
            return;
        }
        if (row.type == RadarRowType.MATCH && row.match != null) {
            AutismDupeRadar.RadarMatch match = row.match;
            String key = radarMatchKey(match);
            boolean expanded = expandedRadarPlugins.contains(key);
            boolean hovered = uiMouseX >= x && uiMouseX < x + width && uiMouseY >= y && uiMouseY < y + row.height;
            CompactSurfaces.row(ctx, x, y, width, row.height, hovered, expanded);
            int textY = UiSizing.alignTextY(y, row.height, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
            UiRenderer.chevron(ctx, UiBounds.of(x + 2, textY, 9, theme.fontHeight(UiTone.BODY)), expanded, hovered ? theme.color(UiTone.BODY) : theme.color(UiTone.MUTED));

            int rowColor = radarMatchStatusColor(match);
            String count = match.findings().size() + "x";
            int countW = UiText.width(textRenderer, count, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED)) + 4;
            int countX = x + width - countW - 5;
            String label = match.displayLabel();
            int labelMax = Math.max(30, countX - (x + 13) - 4);
            UiText.draw(ctx, textRenderer, UiText.trimToWidth(textRenderer, label, labelMax, theme.fontFor(UiTone.BODY), rowColor),
                theme.fontFor(UiTone.BODY), rowColor, x + 13, textY, false);
            UiText.draw(ctx, textRenderer, count, theme.fontFor(UiTone.MUTED), theme.color(UiTone.MUTED), countX, textY, false);

            int hitTop = Math.max(y, clipTop);
            int hitBottom = Math.min(y + row.height, clipBottom);
            clickRegions.add(new ClickRegion(x, hitTop, width, Math.max(1, hitBottom - hitTop), () -> {
                if (expandedRadarPlugins.contains(key)) expandedRadarPlugins.remove(key);
                else expandedRadarPlugins.add(key);
                requestUiRebuild();
            }));
            return;
        }
        if (row.type == RadarRowType.FINDING && row.finding != null && row.match != null) {
            AutismDupeRadar.RadarFinding finding = row.finding;
            boolean hovered = uiMouseX >= x + 5 && uiMouseX < x + width - 5 && uiMouseY >= y && uiMouseY < y + row.height;
            CompactSurfaces.row(ctx, x + 5, y, width - 9, row.height, hovered, false);
            int color = radarStatusColor(finding.status());
            CompactSurfaces.indicator(ctx, x + 5, y, 1, row.height, color);
            int textY = UiSizing.alignTextY(y, row.height, theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
            int openW = 28;
            int openX = x + width - openW - 5;
            String title = finding.title();
            if (finding.summary() != null && !finding.summary().isBlank()) {
                title += " - " + finding.summary();
            }
            String label = UiText.trimToWidth(textRenderer, title, Math.max(20, openX - x - 20), theme.fontFor(UiTone.MUTED), hovered ? theme.color(UiTone.BODY) : color);
            UiText.draw(ctx, textRenderer, label, theme.fontFor(UiTone.MUTED), hovered ? theme.color(UiTone.BODY) : color, x + 11, textY, false);
            drawRadarChip(ctx, openX, y + 3, openW, row.height - 6, "Open", AutismColors.packetCyan());
            int hitTop = Math.max(y, clipTop);
            int hitBottom = Math.min(y + row.height, clipBottom);
            clickRegions.add(new ClickRegion(openX, hitTop, openW, Math.max(1, hitBottom - hitTop), () -> AutismDupeRadar.open(finding.sourceUrl())));
        }
    }

    private void drawRadarChip(GuiGraphicsExtractor ctx, int x, int y, int width, int height, String label, int color) {

        autismclient.gui.vanillaui.components.Chip.render(
            autismclient.gui.vanillaui.UiContexts.overlay(ctx, textRenderer, -1, -1),
            UiBounds.of(x, y, width, height), label, color, false, false);
    }

    private static final int RADAR_PATCHED_RED = 0xFFFF4B4B;

    private int radarStatusColor(String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (s.contains("unpatch") || s.contains("not patch")) return AutismColors.packetGreen();
        if (s.contains("patch")) return RADAR_PATCHED_RED;
        if (s.contains("work")) return AutismColors.packetGreen();
        if (s.contains("unverif") || s.contains("pending")) return AutismColors.packetYellow();
        if (s.contains("verif")) return AutismColors.packetGreen();
        return theme.color(UiTone.MUTED);
    }

    private int radarMatchStatusColor(AutismDupeRadar.RadarMatch match) {
        if (match == null) return theme.color(UiTone.MUTED);
        boolean usable = false;
        boolean unverified = false;
        boolean patched = false;
        for (AutismDupeRadar.RadarFinding finding : match.findings()) {
            if (finding == null) continue;
            String s = finding.status() == null ? "" : finding.status().toLowerCase(Locale.ROOT);
            if (s.contains("unpatch") || s.contains("not patch")) usable = true;
            else if (s.contains("patch")) patched = true;
            else if (s.contains("work")) usable = true;
            else if (s.contains("unverif") || s.contains("pending")) unverified = true;
            else if (s.contains("verif")) usable = true;
        }
        if (usable) return AutismColors.packetGreen();
        if (unverified) return AutismColors.packetYellow();
        if (patched) return RADAR_PATCHED_RED;
        return theme.color(UiTone.MUTED);
    }

    private int radarStatusRank(String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (s.contains("unpatch") || s.contains("not patch")) return 0;
        if (s.contains("patch")) return 2;
        if (s.contains("work")) return 0;
        if (s.contains("unverif") || s.contains("pending")) return 1;
        if (s.contains("verif")) return 0;
        return 3;
    }

    private int radarMatchStatusRank(AutismDupeRadar.RadarMatch match) {
        int best = 3;
        if (match != null) {
            for (AutismDupeRadar.RadarFinding finding : match.findings()) {
                if (finding != null) best = Math.min(best, radarStatusRank(finding.status()));
            }
        }
        return best;
    }

    private CompactScrollbar.Metrics getRadarScrollbarMetrics(int x, int y, int width, int height) {
        int innerHeight = getPluginListInnerHeight(height);
        int maxScroll = Math.max(0, radarContentHeight - innerHeight);
        return CompactScrollbar.compute(
            radarContentHeight,
            Math.max(1, innerHeight),
            x + width - 5,
            y + 1,
            3,
            Math.max(1, innerHeight),
            radarScrollState.tick(0.0f, maxScroll)
        );
    }

    private List<RadarListRow> buildRadarRows(int rowContentW) {
        AutismDupeRadar.RadarState state = AutismDupeRadar.state();
        String query = radarSearchField.text() == null ? "" : radarSearchField.text().trim().toLowerCase(Locale.ROOT);
        List<RadarListRow> rows = new ArrayList<>();
        if (state.checking() || state.authenticating()) {
            rows.add(RadarListRow.empty(state.status(), RADAR_CARD_H));
        }
        List<AutismDupeRadar.RadarMatch> matches = new ArrayList<>();
        for (AutismDupeRadar.RadarMatch match : state.matches()) {
            if (match == null || !radarMatchPassesFilter(match)) continue;
            String search = radarSearchBlob(match);
            if (!query.isEmpty() && !search.contains(query)) continue;
            matches.add(match);
        }
        if (matches.isEmpty()) {
            String emptyText = state.matches().isEmpty()
                ? "No server matches"
                : "No search matches.";
            rows.add(RadarListRow.empty(emptyText, RADAR_CARD_H));
            return rows;
        }

        matches.sort(Comparator.comparingInt((AutismDupeRadar.RadarMatch m) -> radarMatchStatusRank(m))
            .thenComparing(Comparator.comparingLong(AutismDupeRadar.RadarMatch::sortTimestampMs).reversed())
            .thenComparing(AutismDupeRadar.RadarMatch::displayLabel, String.CASE_INSENSITIVE_ORDER));
        for (AutismDupeRadar.RadarMatch match : matches) {
            rows.add(RadarListRow.match(match, RADAR_CARD_H, radarSearchBlob(match)));
            if (expandedRadarPlugins.contains(radarMatchKey(match))) {
                List<AutismDupeRadar.RadarFinding> findings = new ArrayList<>(match.findings());
                findings.sort(Comparator.comparingInt((AutismDupeRadar.RadarFinding f) -> radarStatusRank(f.status()))
                    .thenComparing(Comparator.comparingLong(AutismDupeRadar.RadarFinding::sortTimestampMs).reversed()));
                for (AutismDupeRadar.RadarFinding finding : findings) {
                    if (finding == null) continue;
                    if (!query.isEmpty() && !radarFindingSearchBlob(match, finding).contains(query)) continue;
                    rows.add(RadarListRow.finding(match, finding, RADAR_FINDING_H, radarFindingSearchBlob(match, finding)));
                }
            }
        }
        return rows;
    }

    private boolean radarMatchPassesFilter(AutismDupeRadar.RadarMatch match) {
        return match != null;
    }

    private String radarSearchBlob(AutismDupeRadar.RadarMatch match) {
        if (match == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(match.detectedPlugin()).append(' ')
            .append(match.providerPlugin()).append(' ')
            .append(match.matchConfidence()).append(' ')
            .append(match.matchSource().label()).append(' ')
            .append(match.matchedInput()).append(' ')
            .append(match.provider());
        for (AutismDupeRadar.RadarFinding finding : match.findings()) {
            sb.append(' ').append(radarFindingSearchBlob(match, finding));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String radarFindingSearchBlob(AutismDupeRadar.RadarMatch match, AutismDupeRadar.RadarFinding finding) {
        if (finding == null) return "";
        return lowerSearch(match == null ? "" : match.detectedPlugin(), finding.title(), finding.summary(), finding.provider());
    }

    private String radarMatchKey(AutismDupeRadar.RadarMatch match) {
        if (match == null) return "";
        return (match.matchSource().name() + "|" + match.detectedPlugin() + "|" + match.providerPlugin()
            + "|" + match.matchedInput() + "|" + match.sourceUrl()).toLowerCase(Locale.ROOT);
    }

    private int estimateRadarContentHeight(List<RadarListRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        int total = 0;
        for (RadarListRow row : rows) total += row == null ? 0 : row.height;
        return total;
    }

    private int radarRowOffset(List<RadarListRow> rows, int index) {
        if (rows == null || rows.isEmpty() || index <= 0) return 0;
        int total = 0;
        for (int i = 0; i < Math.min(index, rows.size()); i++) total += rows.get(i) == null ? 0 : rows.get(i).height;
        return total;
    }

    private int firstVisibleRadarRowIndex(List<RadarListRow> rows, int scrollOffset) {
        if (rows == null || rows.isEmpty() || scrollOffset <= 0) return 0;
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            RadarListRow row = rows.get(i);
            total += row == null ? 0 : row.height;
            if (total > scrollOffset) return i;
        }
        return Math.max(0, rows.size() - 1);
    }

    private CompactScrollbar.Metrics getPluginScrollbarMetrics(int x, int y, int width, int height) {
        int innerHeight = getPluginListInnerHeight(height);
        int maxScroll = Math.max(0, pluginContentHeight - innerHeight);
        return CompactScrollbar.compute(
            pluginContentHeight,
            Math.max(1, innerHeight),
            x + width - 5,
            y + 1,
            3,
            Math.max(1, innerHeight),
            quantizeScrollOffset(pluginScrollState.tick(0.0f, maxScroll), pluginListRowStep(), maxScroll)
        );
    }

    private String detailCountLabel(List<String> commands, List<String> guis, List<String> channels) {
        int commandCount = commands == null ? 0 : commands.size();
        int guiCount = guis == null ? 0 : guis.size();
        int observedChannelCount = 0;
        int probableChannelCount = 0;
        boolean noChannelObserved = false;
        if (channels != null) {
            for (String channel : channels) {
                if (channel == null || channel.isBlank()) continue;
                if (isNoChannelRow(channel)) noChannelObserved = true;
                else if (isProbableChannelRow(channel)) probableChannelCount++;
                else observedChannelCount++;
            }
        }
        int channelCount = observedChannelCount + probableChannelCount;
        String channelLabel;
        if (observedChannelCount > 0) channelLabel = observedChannelCount + " ch";
        else if (probableChannelCount > 0) channelLabel = probableChannelCount + "? ch";
        else if (noChannelObserved) channelLabel = "no ch";
        else channelLabel = "0 ch";

        List<String> parts = new ArrayList<>();
        if (commandCount > 0) parts.add(commandCount + "c");
        if (guiCount > 0) parts.add(guiCount + "g");
        if (channelCount > 0 || noChannelObserved) parts.add(channelLabel);
        if (!parts.isEmpty()) return String.join("/", parts);
        return channelLabel;
    }

    private String detailCountLabel(List<String> commands, List<String> channels) {
        return detailCountLabel(commands, List.of(), channels);
    }

    private List<String> getPluginChannelDetailRows(String plugin) {
        List<String> observed = getPluginChannels(plugin);
        if (observed != null && !observed.isEmpty()) return observed;

        List<String> probableNamespaces = AutismPluginPayloadFingerprints.probableNamespacesForPlugin(plugin);
        if (!probableNamespaces.isEmpty()) {
            List<String> rows = new ArrayList<>();
            for (String namespace : probableNamespaces) {
                if (namespace == null || namespace.isBlank()) continue;
                rows.add("? " + namespace.trim().toLowerCase(Locale.ROOT) + ":*");
            }
            rows = dedupeChannelRows(rows);
            if (!rows.isEmpty()) return rows;
        }

        return List.of("no channel");
    }

    private int channelDetailColor(String detail) {
        if (detail == null) return theme.color(UiTone.MUTED);
        String lower = detail.toLowerCase(Locale.ROOT);
        if (isNoChannelRow(lower)) return 0xFF8F8585;
        if (isProbableChannelRow(lower)) return 0xFF8F8585;
        if (lower.contains("[registered")) return AutismColors.packetGreen();
        if (lower.contains("[live")) return AutismColors.packetCyan();
        return AutismColors.packetCyan();
    }

    private String channelDetailLabel(String detail) {
        if (detail == null || detail.isBlank()) return "--";
        if (isNoChannelRow(detail)) return "no channel";
        String base = channelDetailBase(detail);
        if (base.isBlank()) return "--";
        return isProbableChannelRow(detail) ? "? " + base : base;
    }

    private List<String> dedupeChannelRows(Collection<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String row : source) {
            if (row == null || row.isBlank()) continue;
            String clean = row.trim();
            String key = channelDetailKey(clean);
            if (key.isBlank()) continue;
            String existing = unique.get(key);
            if (existing == null || channelRowRank(clean) > channelRowRank(existing)) {
                unique.put(key, clean);
            }
        }
        List<String> out = new ArrayList<>(unique.values());
        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(channelDetailLabel(a), channelDetailLabel(b)));
        return out;
    }

    private String channelDetailKey(String detail) {
        if (isNoChannelRow(detail)) return "no-channel";
        return channelDetailBase(detail).toLowerCase(Locale.ROOT);
    }

    private String channelDetailBase(String detail) {
        if (detail == null) return "";
        String text = detail.trim();
        if (text.regionMatches(true, 0, "ch ", 0, 3)) text = text.substring(3).trim();
        if (text.startsWith("?")) text = text.substring(1).trim();
        int bracket = text.indexOf(" [");
        if (bracket >= 0) text = text.substring(0, bracket).trim();
        return text;
    }

    private String payloadChannelActionTarget(String detail) {
        if (detail == null || isNoChannelRow(detail)) return null;
        String base = channelDetailBase(detail);
        String channel = bestGuessPayloadChannel(base);
        return channel.isBlank() ? null : channel;
    }

    private String bestGuessPayloadChannel(String rawChannel) {
        if (rawChannel == null) return "";
        String channel = rawChannel.trim();
        if (channel.isBlank()) return "";
        String legacy = channel.replace(" ", "");
        if ("REGISTER".equalsIgnoreCase(legacy) || "minecraft:register".equalsIgnoreCase(legacy)) return "minecraft:register";
        if ("UNREGISTER".equalsIgnoreCase(legacy) || "minecraft:unregister".equalsIgnoreCase(legacy)) return "minecraft:unregister";
        if ("BungeeCord".equalsIgnoreCase(legacy) || "bungeecord".equalsIgnoreCase(legacy)) return "bungeecord:main";

        int wildcard = channel.indexOf('*');
        if (wildcard >= 0) {
            channel = channel.substring(0, wildcard);
            while (channel.endsWith(":") || channel.endsWith("/") || channel.endsWith(".")) {
                channel = channel.substring(0, channel.length() - 1);
            }
            if (channel.isBlank()) return "";
            if (!channel.contains(":")) channel = channel + ":main";
            else if (channel.endsWith(":")) channel = channel + "main";
        }

        channel = channel.toLowerCase(Locale.ROOT)
            .replace('\\', '/')
            .replaceAll("[^a-z0-9_./:-]", "_");
        int colon = channel.indexOf(':');
        if (colon < 0) channel = channel + ":main";
        else if (colon == 0) channel = "minecraft" + channel;
        else if (colon == channel.length() - 1) channel = channel + "main";
        try {
            AutismPayloadSupport.parseChannel(channel);
            return channel;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private void openPayloadSenderForChannel(String channel) {
        String target = channel == null ? "" : channel.trim();
        if (target.isBlank()) return;
        ActionEditorOverlay existing = ActionEditorOverlay.getSharedOverlayIfExists();
        if (existing != null && existing.updateOpenPayloadChannel(target)) return;

        PayloadAction action = new PayloadAction();
        action.channel = target;
        action.payloadDirection = "C2S";
        action.sourceDirection = "C2S";
        action.payloadPhase = "PLAY";
        action.sourceProtocol = "play";
        action.payloadProvenance = "serverInfoChannel";

        ActionEditorOverlay editor = ActionEditorOverlay.getSharedOverlay();
        editor.openStandalonePayloadEditor(action);
        AutismOverlayManager.get().bringToFront(editor);
    }

    private boolean isNoChannelRow(String detail) {
        if (detail == null) return false;
        String lower = detail.trim().toLowerCase(Locale.ROOT);
        return lower.equals("no channel") || lower.startsWith("no client payload");
    }

    private boolean isProbableChannelRow(String detail) {
        if (detail == null) return false;
        String lower = detail.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("? ") || lower.contains("not observed") || lower.contains("[probable");
    }

    private int channelRowRank(String detail) {
        if (detail == null) return 0;
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("[live")) return 4;
        if (lower.contains("[registered")) return 3;
        if (lower.contains("[embedded")) return 2;
        if (isProbableChannelRow(lower)) return 1;
        return isNoChannelRow(lower) ? 0 : 3;
    }

    private String getLiveBrand() {
        if (MC.getConnection() == null) return "--";
        String brand = MC.getConnection().serverBrand();
        return brand != null && !brand.isBlank() ? brand : "--";
    }

    private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelW
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverClose(UiBounds.of(panelX, panelY, panelW, Math.max(theme.headerHeight(), panelH)),
            theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private int panelPadding() {
        return 5;
    }

    private int contentGap() {
        return 3;
    }

    private int searchFieldHeight() {
        return 15;
    }

    private int actionButtonHeight() {
        return 15;
    }

    private int buttonRowGap() {
        return 2;
    }

    private int infoLabelWidth() {
        return 82;
    }

    private int pluginSetupLabelWidth() {
        return 46;
    }

    private int pluginScanLabelWidth() {
        return 40;
    }

    private int pluginSearchWidth() {
        return 128;
    }

    private int pluginSearchReserveWidth() {
        return 28;
    }

    private float pluginViewportMinHeight() {
        return 66.0f;
    }

    private int sharedPanelWidth() {
        return 204;
    }

    private int pluginSetupWidth() {
        return sharedPanelWidth();
    }

    private int pluginScanningWidth() {
        return sharedPanelWidth();
    }

    private int infoMinWidth() {
        return sharedPanelWidth();
    }

    private int infoMinHeight() {
        return 246;
    }

    private int pluginResultsMinWidth() {
        return sharedPanelWidth();
    }

    private int pluginResultsMinHeightPreset() {
        return 266;
    }

    private int pluginSetupHeight() {
        return 120;
    }

    private int pluginScanningHeight() {
        return 92;
    }

    private int viewportHeightMargin() {
        return 24;
    }

    private int pluginHeaderHeight() {
        return pluginListRowStep();
    }

    private int pluginRowHeight() {
        return 15;
    }

    private int pluginDetailRowHeight() {
        return pluginListRowStep();
    }

    private int pluginDetailPadding() {
        return 0;
    }

    private int headerControlSize() {
        return 12;
    }

    private int headerArrowWidth() {
        return 10;
    }

    private int headerArrowGap() {
        return 3;
    }

    private int rowTextInset() {
        return 3;
    }

    private int pluginListRowStep() {
        return pluginRowHeight();
    }

    private int getPluginlistViewportHeight(int rawHeight) {
        if (rawHeight <= 0) return 0;
        return Math.min(rawHeight, getPluginListInnerHeight(rawHeight) + 2);
    }

    private int getPluginListInnerHeight(int viewHeight) {
        return Math.max(0, alignViewportHeight(Math.max(0, viewHeight - 2), pluginListRowStep()));
    }

    private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int viewportWidth = Math.round(viewport.uiWidth());
        int viewportHeight = Math.round(viewport.uiHeight());
        int margin = 4;
        int availableWidth = Math.max(1, viewportWidth - margin * 2);
        int availableHeight = Math.max(theme.headerHeight(), viewportHeight - margin * 2);
        int minWidth = Math.min(getMinWidth(), availableWidth);
        int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
        int minHeight = bounds.collapsed ? theme.headerHeight() : Math.min(getMinHeight(), availableHeight);
        int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
        int renderedHeight = bounds.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportWidth - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportHeight - margin - renderedHeight)));
        return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private String getSoftwareGuess(ServerData entry) {
        LinkedHashSet<String> guesses = new LinkedHashSet<>();
        String brand = getLiveBrand().toLowerCase(Locale.ROOT);
        if (brand.contains("purpur")) guesses.add("Purpur");
        else if (brand.contains("pufferfish")) guesses.add("Pufferfish");
        else if (brand.contains("folia")) guesses.add("Folia");
        else if (brand.contains("paper")) guesses.add("Paper");
        else if (brand.contains("spigot")) guesses.add("Spigot");
        else if (brand.contains("craftbukkit") || brand.contains("bukkit")) guesses.add("Bukkit");
        else if (brand.contains("waterfall")) guesses.add("Waterfall");
        else if (brand.contains("velocity")) guesses.add("Velocity");
        else if (brand.contains("bungeecord") || brand.contains("bungee")) guesses.add("Bungee");

        if (detectedPlugins.stream().anyMatch(plugin -> "viaversion".equalsIgnoreCase(plugin) || "viabackwards".equalsIgnoreCase(plugin) || "viarewind".equalsIgnoreCase(plugin))) {
            guesses.add("ViaVersion");
        }
        if (detectedPlugins.stream().anyMatch(plugin -> "geysermc".equalsIgnoreCase(plugin) || "floodgate".equalsIgnoreCase(plugin))) {
            guesses.add("Bedrock Bridge");
        }

        return guesses.isEmpty() ? "--" : String.join(" + ", guesses);
    }

    private String getVersionNote(ServerData entry) {
        String brand = getLiveBrand();
        String reportedVersion = getReportedVersion(entry);
        String brandLower = brand.toLowerCase(Locale.ROOT);
        String versionLower = reportedVersion.toLowerCase(Locale.ROOT);
        if (entry != null && entry.protocol == 0) {
            return "Ping Spoof";
        }
        if (!"--".equals(brand)
            && (versionLower.contains("paper")
            || versionLower.contains("spigot")
            || versionLower.contains("purpur")
            || versionLower.contains("velocity")
            || versionLower.contains("bungee"))
            && ((brandLower.contains("paper") && !versionLower.contains("paper"))
            || (brandLower.contains("spigot") && !versionLower.contains("spigot"))
            || (brandLower.contains("purpur") && !versionLower.contains("purpur"))
            || (brandLower.contains("velocity") && !versionLower.contains("velocity"))
            || (brandLower.contains("bungee") && !versionLower.contains("bungee")))) {
            return "Brand Mismatch";
        }
        if (detectedPlugins.stream().anyMatch(plugin -> "viaversion".equalsIgnoreCase(plugin) || "viabackwards".equalsIgnoreCase(plugin) || "viarewind".equalsIgnoreCase(plugin) || "protocolsupport".equalsIgnoreCase(plugin))) {
            return "Protocol Bridge";
        }
        return "--";
    }

    private String getDisplayedServerAddress() {
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim();
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return host + ":" + inet.getPort();
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim();
                }
            }
        }
        return "--";
    }

    private String extractLookupHost(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || "--".equals(rawAddress)) return "";
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            return trimmed.substring(1, trimmed.indexOf(']'));
        }
        int colonCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == ':') colonCount++;
        }
        if (colonCount == 1 && trimmed.contains(":")) {
            return trimmed.substring(0, trimmed.lastIndexOf(':'));
        }
        return trimmed;
    }

    private String getLiveSocketIp() {
        if (MC.getConnection() == null || MC.getConnection().getConnection() == null) return "";
        SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            String hostAddress = inet.getAddress().getHostAddress();
            return hostAddress == null ? "" : hostAddress.trim();
        }
        return "";
    }

    private Integer extractPort(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || "--".equals(rawAddress)) return null;
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int closing = trimmed.indexOf(']');
            if (closing >= 0 && closing + 1 < trimmed.length() && trimmed.charAt(closing + 1) == ':') {
                try {
                    return Integer.parseInt(trimmed.substring(closing + 2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        int firstColon = trimmed.indexOf(':');
        int lastColon = trimmed.lastIndexOf(':');
        if (firstColon >= 0 && firstColon == lastColon) {
            try {
                return Integer.parseInt(trimmed.substring(lastColon + 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String appendPortIfPresent(String host, Integer port) {
        if (host == null || host.isBlank() || port == null) return host;
        if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private Object invokeFirstNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {  }
        }
        return null;
    }

    private String stringValue(Object target, String... names) {
        Object value = invokeFirstNoArg(target, names);
        if (value == null) return "";
        if (value instanceof Component component) return component.getString();
        return String.valueOf(value);
    }

    private int intValue(Object target, int fallback, String... names) {
        Object value = invokeFirstNoArg(target, names);
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {  }
        }
        return fallback;
    }

    private String componentText(Object value) {
        if (value instanceof Component component) return component.getString();
        return value == null ? "" : String.valueOf(value);
    }

    private void copyClipboardValue(String value, String successMessage, String unavailableMessage) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || "--".equals(trimmed) || "Resolving...".equalsIgnoreCase(trimmed) || "Failed".equalsIgnoreCase(trimmed)) {
            AutismNotifications.error(unavailableMessage);
            return;
        }
        MC.keyboardHandler.setClipboard(trimmed);
        AutismNotifications.copied(successMessage);
    }

    private void copyResolvedServerIp() {
        String displayedAddress = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(displayedAddress);
        String resolvedWithPort = appendPortIfPresent(realIp, extractPort(displayedAddress));
        copyClipboardValue(resolvedWithPort, "Real IP copied.", "Real IP unavailable.");
    }

    private void ensureResolvedIpLookup(String rawAddress) {
        String host = extractLookupHost(rawAddress);
        if (host.isEmpty()) return;
        if (!host.equals(lastResolvedAddress)) {
            resolvedIp = null;
            lastResolvedAddress = host;
            resolvingIp = false;
        }
        if (resolvedIp == null && !resolvingIp) {
            resolvingIp = true;
            Thread t = new Thread(() -> {
                try { resolvedIp = java.net.InetAddress.getByName(host).getHostAddress(); }
                catch (Exception e) { resolvedIp = "Failed"; }
                finally { resolvingIp = false; }
            }, "Autism-DNS");
            t.setDaemon(true);
            t.start();
        }
    }

    private String getDisplayedRealIp(String rawAddress) {
        ensureResolvedIpLookup(rawAddress);
        String socketIp = getLiveSocketIp();
        if (!socketIp.isBlank()) return socketIp;
        if (resolvedIp != null) return resolvedIp;
        if (resolvingIp) return "Resolving...";
        return "--";
    }

    private List<String> getDetectedAnticheats() {
        return detectedPlugins.stream()
            .filter(Objects::nonNull)
            .filter(p -> ANTICHEATS.contains(p.toLowerCase(Locale.ROOT)))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    private String getCurrentWorldName() {
        if (MC.level == null) return "--";

        net.minecraft.resources.Identifier worldId = MC.level.dimension().identifier();
        if (worldId == null) return "--";

        String namespace = worldId.getNamespace();
        String path = worldId.getPath();
        if ("minecraft".equals(namespace)) {
            return switch (path) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "The Nether";
                case "the_end" -> "The End";
                default -> path;
            };
        }

        return namespace + ":" + path;
    }

    private List<PluginListRow> buildPluginRows(List<PluginDetail> filteredPlugins, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean searchActive = !normalizedQuery.isBlank();
        Map<String, List<PluginListRow>> grouped = new LinkedHashMap<>();
        for (String title : List.of("Exact Plugins", "Strong Matches", "Command Tree", "Command Root Hints",
            "Plugin Command Hints", "Version Hints", "Payload Channels", "Command Hints", "Other")) {
            grouped.put(title, new ArrayList<>());
        }

        for (PluginDetail detail : filteredPlugins) {
            if (detail == null || detail.displayName.isBlank()) continue;
            String plugin = detail.displayName;
            String groupTitle = pluginGroupTitle(detail.resultKind, detail.confidence, detail.evidence);
            boolean standaloneCommandHint = isStandaloneAutocompleteCommand(detail);

            grouped.computeIfAbsent(groupTitle, unused -> new ArrayList<>()).add(
                PluginListRow.plugin(plugin, detail.resultKind, detail.evidence, detail.confidence, pluginRowHeight(),
                    standaloneCommandHint ? "" : detail.countLabel, detail.searchText)
            );

            boolean selected = isSamePlugin(plugin, selectedPlugin);
            boolean showDetails = !standaloneCommandHint && (selected || searchActive);
            if (showDetails) {
                for (String command : detail.commands) {
                    if (searchActive && !pluginDetailMatchesQuery(normalizedQuery, plugin, "/" + command, detail.sourceLabel)) continue;
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.COMMAND, plugin, "cmd /" + command, "/" + command,
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, command, detail.sourceLabel)));
                }
                for (String gui : detail.guis) {
                    if (searchActive && !pluginDetailMatchesQuery(normalizedQuery, plugin, gui, detail.sourceLabel)) continue;
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.GUI, plugin, "gui " + gui, null,
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, gui, detail.sourceLabel)));
                }
                List<String> channels = detail.channels.isEmpty() ? getPluginChannelDetailRows(plugin) : detail.channels;
                for (String channel : channels) {
                    String label = channelDetailLabel(channel);
                    if (searchActive && !pluginDetailMatchesQuery(normalizedQuery, plugin, label, detail.sourceLabel)) continue;
                    grouped.get(groupTitle).add(PluginListRow.detail(PluginRowType.CHANNEL, plugin, "ch " + label, payloadChannelActionTarget(channel),
                        detail.evidence, detail.confidence, pluginDetailRowHeight(), lowerSearch(plugin, label, detail.sourceLabel)));
                }
            }
        }

        List<PluginListRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<PluginListRow>> group : grouped.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            group.getValue().sort((a, b) -> {
                int byPlugin = String.CASE_INSENSITIVE_ORDER.compare(
                    a.plugin == null ? "" : a.plugin,
                    b.plugin == null ? "" : b.plugin
                );
                if (byPlugin != 0) return byPlugin;
                return Integer.compare(pluginRowTypeOrder(a.type), pluginRowTypeOrder(b.type));
            });
            rows.add(PluginListRow.header(group.getKey(), pluginHeaderHeight()));
            rows.addAll(group.getValue());
        }
        return rows;
    }

    private int pluginRowTypeOrder(PluginRowType type) {
        return switch (type == null ? PluginRowType.WHY : type) {
            case HEADER -> 0;
            case PLUGIN -> 1;
            case COMMAND -> 2;
            case GUI -> 3;
            case CHANNEL -> 4;
            case WHY -> 5;
        };
    }

    private boolean isStandaloneAutocompleteCommand(PluginDetail detail) {
        if (detail == null || detail.evidence != PluginEvidence.SCANNER_AUTOCOMPLETE || detail.commands.size() != 1) return false;
        String command = detail.commands.get(0);
        return command != null && detail.displayName.equalsIgnoreCase("/" + command);
    }

    private boolean pluginDetailMatchesQuery(String query, String plugin, String detail, String sourceLabel) {
        if (query == null || query.isBlank()) return true;
        return lowerSearch(plugin, detail, sourceLabel).contains(query);
    }

    private String pluginGroupTitle(PluginResultKind resultKind, PluginConfidence confidence, PluginEvidence evidence) {
        if (evidence == PluginEvidence.SCANNER_AUTOCOMPLETE) return "Command Hints";
        if (evidence == PluginEvidence.COMMAND_TREE) return "Command Tree";
        if (evidence == PluginEvidence.ROOT_HINT) return "Command Root Hints";
        if (evidence == PluginEvidence.PLUGIN_LIST) return "Plugin Command Hints";
        if (evidence == PluginEvidence.VERSION_HINT) return "Version Hints";
        if (evidence == PluginEvidence.PAYLOAD_CHANNEL) return "Payload Channels";
        if (resultKind == PluginResultKind.FEATURE || isWeakCommandEvidence(evidence)) return "Command Hints";
        return switch (confidence == null ? PluginConfidence.UNKNOWN : confidence) {
            case EXACT -> "Exact Plugins";
            case STRONG -> "Strong Matches";
            case FEATURE -> "Other";
            case UNKNOWN -> "Other";
        };
    }

    private String sourceLabelFor(PluginResultKind resultKind, PluginEvidence evidence, PluginConfidence confidence) {
        if (resultKind == PluginResultKind.FEATURE) return "feature command";
        return switch (evidence == null ? PluginEvidence.UNKNOWN : evidence) {
            case PAYLOAD_CHANNEL -> "payload channel";
            case PLUGIN_LIST -> "plugin command hint";
            case VERSION_HINT -> "version hint";
            case COMMAND_TREE -> "command tree";
            case NAMESPACE -> "namespace";
            case ROOT_HINT -> "root hint";
            case COMMAND_GUI -> "command and gui";
            case USER_AUTOCOMPLETE -> "user autocomplete";
            case SCANNER_AUTOCOMPLETE -> "command hint";
            case HELP_HINT -> "help hint";
            case FEATURE -> "feature command";
            case UNKNOWN -> confidence == PluginConfidence.UNKNOWN ? "unknown" : "weak hint";
        };
    }

    private List<String> copyPluginGroupOrder() {
        return List.of(
            "Command Tree",
            "Command Root Hint",
            "Plugin Command Hint",
            "Version Command Hint",
            "Payload Channel"
        );
    }

    private String copyPluginGroupTitle(PluginDetail detail) {
        if (detail == null) return "Other";
        PluginEvidence evidence = detail.copyEvidence == null ? PluginEvidence.UNKNOWN : detail.copyEvidence;
        return switch (evidence) {
            case COMMAND_TREE -> "Command Tree";
            case NAMESPACE, ROOT_HINT -> "Command Root Hint";
            case PLUGIN_LIST -> "Plugin Command Hint";
            case VERSION_HINT -> "Version Command Hint";
            case PAYLOAD_CHANNEL -> "Payload Channel";
            case COMMAND_GUI, USER_AUTOCOMPLETE, FEATURE -> "Other";
            case SCANNER_AUTOCOMPLETE, HELP_HINT, UNKNOWN -> "Other";
        };
    }

    private int copyPluginGroupRank(String title) {
        List<String> order = copyPluginGroupOrder();
        int index = order.indexOf(title);
        return index < 0 ? order.size() : index;
    }

    private String pluginSearchBlob(String plugin, List<String> commands, List<String> guis, List<String> channels,
                                    String sourceLabel, PluginConfidence confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append(plugin == null ? "" : plugin).append(' ')
            .append(confidenceLabel(confidence)).append(' ')
            .append(sourceLabel == null ? "" : sourceLabel);
        for (String command : commands) sb.append(' ').append(command);
        for (String gui : guis) sb.append(' ').append(gui);
        for (String channel : channels) sb.append(' ').append(channelDetailLabel(channel));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String lowerSearch(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (part == null || part.isBlank()) continue;
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(part);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private List<String> dedupePluginNames(Collection<String> plugins) {
        if (plugins == null || plugins.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String plugin : plugins) {
            if (plugin == null || plugin.isBlank()) continue;
            String clean = plugin.trim();
            String key = normalizePluginKey(clean);
            if (key.isBlank()) continue;
            String existing = unique.get(key);
            if (existing == null || isMoreReadablePluginName(clean, existing)) {
                unique.put(key, clean);
            }
        }
        List<String> out = new ArrayList<>(unique.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private boolean isSamePlugin(String a, String b) {
        if (a == null || b == null) return false;
        return normalizePluginKey(a).equals(normalizePluginKey(b));
    }

    private boolean hasDetectedPlugin(String plugin) {
        if (plugin == null || plugin.isBlank()) return false;
        String key = normalizePluginKey(plugin);
        for (String detected : detectedPlugins) {
            if (detected != null && normalizePluginKey(detected).equals(key)) return true;
        }
        return false;
    }

    private Collection<PluginDetail> getCachedPluginDetails() {
        if (cachedPluginDetailsRevision == pluginDataRevision) return cachedPluginDetails.values();

        Map<String, PluginDetailBuilder> builders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String plugin : dedupePluginNames(detectedPlugins)) {
            String key = normalizePluginKey(plugin);
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if (builder.displayName == null || builder.displayName.isBlank() || isMoreReadablePluginName(plugin, builder.displayName)) {
                builder.displayName = plugin.trim();
            }
        }

        mergePluginDetailCommands(builders, pluginCommands);
        mergePluginDetailCopyCommands(builders, pluginCopyCommands);
        mergePluginDetailGuis(builders, pluginGuis);
        mergePluginDetailChannels(builders, pluginChannels);

        Map<String, PluginDetail> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, PluginDetailBuilder> entry : builders.entrySet()) {
            String key = entry.getKey();
            PluginDetailBuilder builder = entry.getValue();
            if (builder == null) continue;
            String displayName = builder.displayName == null || builder.displayName.isBlank() ? key : builder.displayName.trim();
            List<String> commands = List.copyOf(builder.commands);
            List<String> copyCommands = builder.copyCommands.isEmpty() ? commands : List.copyOf(builder.copyCommands);
            List<String> guis = List.copyOf(builder.guis);
            List<String> channels = dedupeChannelRows(builder.channels);
            PluginEvidence evidence = pluginEvidence.getOrDefault(key, commands.isEmpty() ? PluginEvidence.UNKNOWN : PluginEvidence.ROOT_HINT);
            PluginEvidence copyEvidence = pluginCopyEvidence.getOrDefault(key, fallbackCopyEvidence(displayName, evidence));
            PluginConfidence confidence = pluginConfidence.getOrDefault(key, confidenceForEvidence(evidence));
            PluginResultKind resultKind = resultKindForStoredPlugin(displayName, evidence, confidence);
            String sourceLabel = sourceLabelFor(resultKind, evidence, confidence);
            String countLabel = detailCountLabel(commands, guis, channels);
            String searchText = pluginSearchBlob(displayName, commands, guis, channels, sourceLabel, confidence);
            details.put(key, new PluginDetail(displayName, key, commands, guis, channels, resultKind, evidence, copyEvidence, confidence, sourceLabel, countLabel, copyCommands, searchText));
        }
        cachedPluginDetails = Collections.unmodifiableMap(new TreeMap<>(details));
        cachedPluginDetailsRevision = pluginDataRevision;
        return cachedPluginDetails.values();
    }

    private PluginDetail getPluginDetail(String plugin) {
        if (plugin == null || plugin.isBlank()) return null;
        getCachedPluginDetails();
        return cachedPluginDetails.get(normalizePluginKey(plugin));
    }

    private void mergePluginDetailCommands(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() == null) continue;
            for (String command : entry.getValue()) {
                if (command != null && !command.isBlank()) builder.commands.add(command.trim());
            }
        }
    }

    private void mergePluginDetailCopyCommands(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() == null) continue;
            for (String command : entry.getValue()) {
                if (command != null && !command.isBlank()) builder.copyCommands.add(command.trim());
            }
        }
    }

    private void mergePluginDetailGuis(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() == null) continue;
            for (String gui : entry.getValue()) {
                if (gui != null && !gui.isBlank()) builder.guis.add(gui.trim());
            }
        }
    }

    private void mergePluginDetailChannels(Map<String, PluginDetailBuilder> builders, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizePluginKey(entry.getKey());
            if (key.isBlank()) continue;
            PluginDetailBuilder builder = builders.computeIfAbsent(key, unused -> new PluginDetailBuilder());
            if ((builder.displayName == null || builder.displayName.isBlank()) && entry.getKey() != null) builder.displayName = entry.getKey().trim();
            if (entry.getValue() != null) builder.channels.addAll(entry.getValue());
        }
    }

    private List<String> getPluginCommands(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.commands;
    }

    private List<String> getPluginGuis(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.guis;
    }

    private List<String> getPluginChannels(String plugin) {
        PluginDetail detail = getPluginDetail(plugin);
        return detail == null ? List.of() : detail.channels;
    }

    private List<PluginListRow> getCachedPluginRows(String queryText, int rowContentWidth) {
        String query = queryText == null ? "" : queryText.trim().toLowerCase(Locale.ROOT);
        String selectedKey = selectedPlugin == null ? "" : normalizePluginKey(selectedPlugin);
        if (cachedPluginRowsRevision == pluginRowsRevision
            && Objects.equals(cachedPluginRowsQuery, query)
            && Objects.equals(cachedPluginRowsSelectedKey, selectedKey)
            && cachedPluginRowsWidth == rowContentWidth) {
            return cachedPluginRows;
        }
        List<PluginDetail> filtered = new ArrayList<>();
        for (PluginDetail detail : getCachedPluginDetails()) {
            if (detail != null && (query.isEmpty() || detail.searchText.contains(query))) {
                filtered.add(detail);
            }
        }
        setCachedPluginRows(buildPluginRows(filtered, query));
        cachedPluginRowsQuery = query;
        cachedPluginRowsSelectedKey = selectedKey;
        cachedPluginRowsWidth = rowContentWidth;
        cachedPluginRowsRevision = pluginRowsRevision;
        return cachedPluginRows;
    }

    private boolean pluginDetailsMatchQuery(String plugin, String query) {
        if (plugin == null || query == null || query.isBlank()) return false;
        PluginDetail detail = getPluginDetail(plugin);
        return detail != null && detail.searchText.contains(query);
    }

    private void invalidatePluginRows() {
        pluginDataRevision++;
        pluginRowsRevision++;
        cachedPluginRowsRevision = -1;
        cachedPluginRowsQuery = null;
        cachedPluginRowsSelectedKey = null;
        cachedPluginRowsWidth = -1;
        cachedPluginRows = List.of();
        cachedPluginRowOffsets = new int[0];
        cachedPluginRowsHeight = 0;
        cachedPluginDetailsRevision = -1;
        cachedPluginDetails = Map.of();
        requestUiRebuild();
    }

    private void invalidatePluginRowView() {
        pluginRowsRevision++;
        cachedPluginRowsRevision = -1;
        cachedPluginRowsQuery = null;
        cachedPluginRowsSelectedKey = null;
        cachedPluginRowsWidth = -1;
        cachedPluginRows = List.of();
        cachedPluginRowOffsets = new int[0];
        cachedPluginRowsHeight = 0;
        requestUiRebuild();
    }

    private int estimatePluginContentHeight(List<PluginListRow> rows) {
        if (rows == cachedPluginRows) return cachedPluginRowsHeight;
        int total = 0;
        for (PluginListRow row : rows) {
            if (row != null) total += row.height;
        }
        return total;
    }

    private void setCachedPluginRows(List<PluginListRow> rows) {
        List<PluginListRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
        int[] offsets = new int[safeRows.size()];
        int cursor = 0;
        for (int i = 0; i < safeRows.size(); i++) {
            offsets[i] = cursor;
            PluginListRow row = safeRows.get(i);
            cursor += row == null ? 0 : row.height;
        }
        cachedPluginRows = safeRows;
        cachedPluginRowOffsets = offsets;
        cachedPluginRowsHeight = cursor;
    }

    private int pluginRowOffset(int index) {
        if (index < 0 || index >= cachedPluginRowOffsets.length) return 0;
        return cachedPluginRowOffsets[index];
    }

    private int firstVisiblePluginRowIndex(List<PluginListRow> rows, int scrollOffset) {
        if (rows == null || rows.isEmpty() || cachedPluginRowOffsets.length == 0 || scrollOffset <= 0) return 0;
        int idx = Arrays.binarySearch(cachedPluginRowOffsets, scrollOffset);
        if (idx < 0) idx = Math.max(0, -idx - 2);
        while (idx > 0) {
            PluginListRow row = rows.get(idx);
            int rowBottom = cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom <= scrollOffset) break;
            idx--;
        }
        while (idx < rows.size()) {
            PluginListRow row = rows.get(idx);
            int rowBottom = cachedPluginRowOffsets[idx] + (row == null ? 0 : row.height);
            if (rowBottom > scrollOffset) break;
            idx++;
        }
        return Math.max(0, Math.min(idx, rows.size() - 1));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mx);
        float uiMouseY = viewport.toUiY(my);

        if (isOverCloseButton(uiMouseX, uiMouseY)) {
            setVisible(false);
            isDragging = false;
            pluginScrollbarDragging = false;
            radarScrollbarDragging = false;
            dragMoved = false;
            return true;
        }
        if (button == 0 && isOverHeaderUi(uiMouseX, uiMouseY)) {
            isDragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (collapsed) return false;
        if (button != 0) return isMouseOver(mx, my);

        if (activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(
                Math.round(pluginListSlot.x()),
                Math.round(pluginListSlot.y()),
                Math.round(pluginListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(pluginListSlot.height()), panelY + panelH - 10 - Math.round(pluginListSlot.y()))))
            );
            if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(uiMouseX, uiMouseY)) {
                pluginScrollbarDragging = true;
                pluginScrollbarGrabOffset = Math.max(0, Math.round(uiMouseY) - scrollbarMetrics.thumbY());
                pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), pluginScrollbarGrabOffset, pluginListRowStep());
                saveState();
                return true;
            }
        }

        if (activeTab == 2 && isRadarAuthenticated()) {
            CompactScrollbar.Metrics scrollbarMetrics = getRadarScrollbarMetrics(
                Math.round(radarListSlot.x()),
                Math.round(radarListSlot.y()),
                Math.round(radarListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(radarListSlot.height()), panelY + panelH - 10 - Math.round(radarListSlot.y()))))
            );
            if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(uiMouseX, uiMouseY)) {
                radarScrollbarDragging = true;
                radarScrollbarGrabOffset = Math.max(0, Math.round(uiMouseY) - scrollbarMetrics.thumbY());
                radarScrollState.setFromThumb(scrollbarMetrics, Math.round(uiMouseY), radarScrollbarGrabOffset);
                saveState();
                return true;
            }
        }

        if ((activeTab == 1 && pluginScanDone && !pluginScanInProgress) || activeTab == 2 && isRadarAuthenticated()) {
            for (ClickRegion region : clickRegions) {
                if (region.contains(uiMouseX, uiMouseY)) {
                    surface.clearFocusedTextInputs();
                    region.action.run();
                    return true;
                }
            }
        }

        if (surface.mouseClicked(mx, my, button)) {
            return true;
        }

        surface.clearFocusedTextInputs();

        return isMouseOver(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pluginScrollbarDragging) {
            pluginScrollbarDragging = false;
            saveState();
            return true;
        }
        if (button == 0 && radarScrollbarDragging) {
            radarScrollbarDragging = false;
            saveState();
            return true;
        }
        if (isDragging) {
            boolean shouldCollapse = !dragMoved;
            isDragging = false;
            if (shouldCollapse) {
                setCollapsed(!collapsed);
                if (!collapsed) {
                    if (activeTab == 0) applyInfoLayout();
                    else if (activeTab == 2) applyRadarLayout();
                    else if (pluginScanInProgress) applyPluginScanningLayout();
                    else if (!pluginScanDone) applyPluginSetupLayout();
                    else applyPluginResultsLayout();
                }
            }
            saveState();
            return true;
        }
        return surface.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pluginScrollbarDragging && button == 0) {
            DirectViewport viewport = surface.viewport();
            float uiMouseY = viewport.toUiY(my);
            CompactScrollbar.Metrics scrollbarMetrics = getPluginScrollbarMetrics(
                Math.round(pluginListSlot.x()),
                Math.round(pluginListSlot.y()),
                Math.round(pluginListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(pluginListSlot.height()), panelY + panelH - 10 - Math.round(pluginListSlot.y()))))
            );
            pluginScrollState.setFromThumbStepped(scrollbarMetrics, Math.round(uiMouseY), pluginScrollbarGrabOffset, pluginListRowStep());
            return true;
        }
        if (radarScrollbarDragging && button == 0) {
            DirectViewport viewport = surface.viewport();
            float uiMouseY = viewport.toUiY(my);
            CompactScrollbar.Metrics scrollbarMetrics = getRadarScrollbarMetrics(
                Math.round(radarListSlot.x()),
                Math.round(radarListSlot.y()),
                Math.round(radarListSlot.width()),
                getPluginlistViewportHeight(Math.max(0, Math.min(Math.round(radarListSlot.height()), panelY + panelH - 10 - Math.round(radarListSlot.y()))))
            );
            radarScrollState.setFromThumb(scrollbarMetrics, Math.round(uiMouseY), radarScrollbarGrabOffset);
            return true;
        }
        if (isDragging) {
            DirectViewport viewport = surface.viewport();
            float uiMouseX = viewport.toUiX(mx);
            float uiMouseY = viewport.toUiY(my);
            AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(
                Math.round(uiMouseX - (float) dragOffsetX),
                Math.round(uiMouseY - (float) dragOffsetY),
                panelW,
                panelH,
                visible,
                collapsed
            ));
            panelX = clamped.x;
            panelY = clamped.y;
            dragMoved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            return true;
        }
        return surface.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        if (activeTab == 1 && pluginScanDone && !pluginScanInProgress) {
            int slotY = Math.round(pluginListSlot.y());
            int slotH = Math.round(pluginListSlot.height());
            int rawViewH = Math.max(0, Math.min(slotH, panelY + panelH - 10 - slotY));
            int viewH = getPluginlistViewportHeight(rawViewH);
            int maxScroll = Math.max(0, pluginContentHeight - getPluginListInnerHeight(viewH));
            pluginScrollState.nudge(amount, pluginListRowStep(), maxScroll);
            return true;
        }
        if (activeTab == 2 && isRadarAuthenticated()) {
            int slotY = Math.round(radarListSlot.y());
            int slotH = Math.round(radarListSlot.height());
            int rawViewH = Math.max(0, Math.min(slotH, panelY + panelH - 10 - slotY));
            int viewH = getPluginlistViewportHeight(rawViewH);
            int maxScroll = Math.max(0, radarContentHeight - getPluginListInnerHeight(viewH));
            radarScrollState.nudge(amount, pluginListRowStep(), maxScroll);
            return true;
        }
        return surface.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return visible && !collapsed && surface.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && surface.charTyped(chr, modifiers);
    }

    private List<AutismDupeRadar.RadarPluginSnapshot> radarPluginSnapshots() {
        if (!pluginScanDone) return List.of();
        List<AutismDupeRadar.RadarPluginSnapshot> snapshots = new ArrayList<>();
        for (PluginDetail detail : getCachedPluginDetails()) {
            if (detail == null || detail.displayName.isBlank()) continue;
            boolean feature = detail.resultKind == PluginResultKind.FEATURE
                || detail.confidence == PluginConfidence.FEATURE
                || detail.evidence == PluginEvidence.SCANNER_AUTOCOMPLETE
                || detail.evidence == PluginEvidence.USER_AUTOCOMPLETE
                || detail.evidence == PluginEvidence.FEATURE
                || detail.evidence == PluginEvidence.HELP_HINT
                || detail.evidence == PluginEvidence.UNKNOWN;
            if (feature) continue;
            if (detail.confidence != PluginConfidence.EXACT && detail.confidence != PluginConfidence.STRONG) continue;
            snapshots.add(new AutismDupeRadar.RadarPluginSnapshot(
                detail.displayName,
                detail.key,
                confidenceLabel(detail.confidence),
                detail.commands.size(),
                detail.channels,
                detail.guis,
                false
            ));
        }
        snapshots.sort(Comparator.comparing(AutismDupeRadar.RadarPluginSnapshot::displayName, String.CASE_INSENSITIVE_ORDER));
        return snapshots;
    }

    private void maybeAutoCheckRadar(AutismDupeRadar.RadarState state, int pluginCount) {
        if (state == null || !state.authenticated() || state.authenticating() || state.checking()) return;
        AutismDupeRadar.RadarServerSnapshot serverSnapshot = radarServerSnapshot();
        boolean hasServerIdentity = serverSnapshot != null && !serverSnapshot.identities().isEmpty();
        if (pluginCount <= 0 && !hasServerIdentity) return;
        long now = System.currentTimeMillis();
        String signature = radarCheckSignature();
        boolean noResults = state.updatedAtMs() <= 0L;
        boolean stale = state.updatedAtMs() > 0L && now - state.updatedAtMs() > 30L * 60L * 1000L;
        boolean changed = !signature.equals(lastRadarAutoCheckSignature);
        if (!noResults && !stale && !changed) return;
        if (!changed && now - lastRadarAutoCheckAt < 5000L) return;
        lastRadarAutoCheckSignature = signature;
        lastRadarAutoCheckAt = now;
        AutismDupeRadar.checkServer(radarPluginSnapshots(), serverSnapshot, stale);
    }

    private String radarCheckSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(currentServerAddress()).append('|');
        for (AutismDupeRadar.RadarPluginSnapshot snapshot : radarPluginSnapshots()) {
            sb.append(snapshot.canonicalKey()).append(',');
        }
        for (AutismDupeRadar.RadarServerIdentity identity : radarServerSnapshot().identities()) {
            sb.append(identity.ip() ? "ip:" : "domain:").append(identity.value()).append(',');
        }
        return sb.toString();
    }

    private AutismDupeRadar.RadarServerSnapshot radarServerSnapshot() {
        List<AutismDupeRadar.RadarServerIdentity> identities = new ArrayList<>();
        addRadarServerIdentity(identities, getDisplayedServerAddress());
        addRadarServerIdentity(identities, currentServerAddress());
        addRadarServerIdentity(identities, extractLookupHost(getDisplayedServerAddress()));
        String liveIp = getLiveSocketIp();
        if (!liveIp.isBlank()) addRadarServerIdentity(identities, liveIp);
        String displayedIp = getDisplayedRealIp(getDisplayedServerAddress());
        if (!displayedIp.isBlank() && !"--".equals(displayedIp) && !"Resolving...".equals(displayedIp) && !"Failed".equals(displayedIp)) {
            addRadarServerIdentity(identities, displayedIp);
        }
        return new AutismDupeRadar.RadarServerSnapshot(identities);
    }

    private String radarServerLabel() {
        String displayed = extractLookupHost(getDisplayedServerAddress());
        if (displayed != null && !displayed.isBlank() && !"--".equals(displayed)) return displayed;
        String current = extractLookupHost(currentServerAddress());
        if (current != null && !current.isBlank() && !"--".equals(current)) return current;
        return "--";
    }

    private void addRadarServerIdentity(List<AutismDupeRadar.RadarServerIdentity> identities, String raw) {
        if (identities == null || raw == null || raw.isBlank() || "--".equals(raw)) return;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("/")) clean = clean.substring(1);
        String host = extractLookupHost(clean);
        if (host == null || host.isBlank() || "--".equals(host)) host = clean;
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.isBlank() || "localhost".equals(host)) return;
        boolean ip = host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || host.contains(":") && !host.contains(".");
        identities.add(new AutismDupeRadar.RadarServerIdentity(host, ip));
    }

    private void copyRadarReport() {
        MC.keyboardHandler.setClipboard(AutismDupeRadar.copyReport());
        AutismNotifications.copied("Radar report copied.");
    }

    private void copyServerData() {
        StringBuilder sb = new StringBuilder();
        ServerData entry = MC.getCurrentServer();
        String ip = getDisplayedServerAddress();
        String realIp = getDisplayedRealIp(ip);
        List<String> detectedAcs = getDetectedAnticheats();
        String software = getSoftwareGuess(entry);
        String versionNote = getVersionNote(entry);
        sb.append("=== Server Info ===\n");
        sb.append("IP:         ").append(ip).append("\n");
        if (!realIp.equals("--")) {
            sb.append("Real IP:    ").append(realIp).append("\n");
        }
        sb.append("Version:    ").append(getReportedVersion(entry)).append("\n");
        sb.append("RealVersion: ").append(getRealServerVersion()).append("\n");
        sb.append("Brand:      ").append(getLiveBrand()).append("\n");
        if (!"--".equals(software)) {
            sb.append("Software:   ").append(software).append("\n");
        }
        if (MC.getConnection() != null) {
            sb.append("Players:    ").append(MC.getConnection().getListedOnlinePlayers().size());
            if (entry != null && entry.players != null) sb.append(" / ").append(entry.players.max());
            sb.append("\n");
        } else {
            sb.append("Players:    --\n");
        }
        sb.append("Protocol:   ").append(entry != null ? entry.protocol : "--").append("\n");
        if (!"--".equals(versionNote)) {
            sb.append("VersionNote: ").append(versionNote).append("\n");
        }
        sb.append("Difficulty: ").append(MC.level != null ? MC.level.getDifficulty().getDisplayName().getString() : "--").append("\n");
        sb.append("World:      ").append(getCurrentWorldName()).append("\n");
        if (MC.level != null) {
            long dayCount = MC.level.getOverworldClockTime() / 24000L;
            long timeOfDay = MC.level.getOverworldClockTime() % 24000L;
            int hours = (int) ((timeOfDay / 1000 + 6) % 24);
            int minutes = (int) ((timeOfDay % 1000) * 60 / 1000);
            sb.append("Time:       Day ").append(dayCount).append(" (").append(String.format("%02d:%02d", hours, minutes)).append(")\n");
        } else {
            sb.append("Time:       --\n");
        }
        if (pluginScanInProgress) {
            sb.append("AntiCheats: Scanning...\n");
        } else if (!pluginScanDone) {
            sb.append("AntiCheats: Probe Plugins First\n");
        } else if (detectedAcs.isEmpty()) {
            sb.append("AntiCheats: None detected\n");
        } else {
            sb.append("AntiCheats: ").append(String.join(", ", detectedAcs)).append("\n");
        }
        MC.keyboardHandler.setClipboard(sb.toString());
        AutismNotifications.copied("Server info copied.");
    }

    private void copyPluginList() {
        if (detectedPlugins.isEmpty()) {
            AutismNotifications.error("No plugins detected.");
            return;
        }
        Map<String, List<PluginDetail>> grouped = new TreeMap<>((a, b) -> {
            int rank = Integer.compare(copyPluginGroupRank(a), copyPluginGroupRank(b));
            return rank != 0 ? rank : String.CASE_INSENSITIVE_ORDER.compare(a, b);
        });
        int copyCount = 0;
        for (String plugin : dedupePluginNames(detectedPlugins)) {
            if (plugin == null || plugin.isBlank()) continue;
            PluginDetail detail = getPluginDetail(plugin);
            if (detail == null || detail.copyEvidence == PluginEvidence.HELP_HINT || detail.copyEvidence == PluginEvidence.UNKNOWN) continue;
            String uiGroupTitle = pluginGroupTitle(detail.resultKind, detail.confidence, detail.evidence);
            if ("Command Hints".equals(uiGroupTitle) || "Other".equals(uiGroupTitle)) continue;
            if (detail.resultKind == PluginResultKind.FEATURE && detail.copyEvidence != PluginEvidence.COMMAND_GUI) continue;
            grouped.computeIfAbsent(copyPluginGroupTitle(detail), unused -> new ArrayList<>()).add(detail);
            copyCount++;
        }
        if (copyCount <= 0) {
            AutismNotifications.error("No non-hint plugins to copy.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Plugins (").append(copyCount).append("):\n");
        for (Map.Entry<String, List<PluginDetail>> group : grouped.entrySet()) {
            List<PluginDetail> details = group.getValue();
            if (details == null || details.isEmpty()) continue;
            details.sort(Comparator.comparing(detail -> detail.displayName, String.CASE_INSENSITIVE_ORDER));
            sb.append("\n[").append(group.getKey()).append("]\n");
            for (PluginDetail detail : details) {
                boolean ac = ANTICHEATS.contains(detail.key.toLowerCase(Locale.ROOT));
                String groupTitle = copyPluginGroupTitle(detail);
                sb.append(ac ? "[AC] " : "- ").append(detail.displayName);
                if (!detail.copyCommands.isEmpty()
                    && !"Plugin Command Hint".equals(groupTitle)
                    && !"Version Command Hint".equals(groupTitle)
                    && !"Payload Channel".equals(groupTitle)) {
                    sb.append(" (").append(detail.copyCommands.size()).append(" cmds)");
                }
                sb.append("\n");
            }
        }
        MC.keyboardHandler.setClipboard(sb.toString());
        AutismNotifications.copied("Plugin list copied.");
    }
}
