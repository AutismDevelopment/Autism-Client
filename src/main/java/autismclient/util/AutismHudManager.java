package autismclient.util;

import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.modules.Module;
import autismclient.modules.AntiVanishModule;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AutismHudManager {
    public static final String ACTIVE_MODULES = "active_modules";
    public static final String TPS = "tps";
    public static final String COORDINATES = "coordinates";
    public static final String NETHER_COORDS = "nether_coords";
    public static final String FPS = "fps";
    public static final String PING = "ping";
    public static final String SPEED = "speed";
    public static final String GAME_MODE = "game_mode";
    public static final String DURABILITY = "durability";
    public static final String LOOKING_AT = "looking_at";
    public static final String BREAKING_PROGRESS = "breaking_progress";
    public static final String SERVER = "server";
    public static final String WEATHER = "weather";
    public static final String BIOME = "biome";
    public static final String WORLD_TIME = "world_time";
    public static final String REAL_TIME = "real_time";
    public static final String ROTATION = "rotation";
    public static final String WATERMARK = "watermark";
    public static final String ARMOR = "armor";
    public static final String INVENTORY = "inventory";
    public static final String ITEM_COUNTER = "item_counter";
    public static final String POTION_TIMERS = "potion_timers";
    public static final String COMPASS = "compass";
    public static final String ANTI_VANISH = "anti_vanish";
    public static final String CPS = "cps";
    public static final String KEYSTROKES = "keystrokes";

    public static final String MEMORY = "memory";
    public static final String SERVER_IP = "server_ip";
    public static final String SERVER_BRAND = "server_brand";
    public static final String FPS_GRAPH = "fps_graph";

    private static final String KEY_PADDING = "padding";
    private static final String KEY_VERTICAL_PADDING = "vertical-padding";
    private static final String KEY_OUTLINE = "outline";
    private static final String KEY_OUTLINE_COLOR = "outline-color";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_COMPASS_WIDTH = "compass-width";
    private static final String KEY_STAIR_SNAP = "stair-snap";
    private static final String KEY_LOGO_WIDTH = "logo-width";
    private static final String KEY_LOGO_RIGHT_PADDING = "logo-right-padding";

    private static final String KEY_KS_ACTIVE_COLOR = "keystroke-active-color";
    private static final String KEY_KS_IDLE_COLOR = "keystroke-idle-color";
    private static final String KEY_KS_TEXT_COLOR = "keystroke-text-color";
    private static final String KEY_KS_SHOW_SPACE = "keystroke-show-space";
    private static final String KEY_KS_SHOW_MOUSE = "keystroke-show-mouse";
    private static final String KEY_KS_SIZE = "keystroke-size";

    private static final String DEFAULT_PADDING = "1";
    private static final String DEFAULT_VERTICAL_PADDING = "0";
    private static final String DEFAULT_OUTLINE = "false";
    private static final String DEFAULT_OUTLINE_COLOR = "FF750000";
    private static final String DEFAULT_BACKGROUND = "true";
    private static final String DEFAULT_COMPASS_WIDTH = "112";
    private static final String DEFAULT_STAIR_SNAP = "2";

    private static final Minecraft MC = Minecraft.getInstance();
    private static long lastHudErrorLogMs;
    private static final CompactTheme THEME = new CompactTheme();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final Identifier HUD_LOGO = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/hud/autismclient_hud.png");
    private static final int HUD_LOGO_TEXTURE_WIDTH = 552;
    private static final int HUD_LOGO_TEXTURE_HEIGHT = 52;
    private static final int HUD_LOGO_DISPLAY_WIDTH = 552;
    private static final int HUD_LOGO_DISPLAY_HEIGHT = 52;
    private static final int HUD_OUTLINE_MERGE_TOLERANCE = 1;
    private static final int HUD_SAFE_ZONE_X = 1;
    private static final int HUD_SAFE_ZONE_Y = 2;
    private static final int HUD_OUTLINE_CHROME_PADDING = 2;
    private static final List<String> ORDER = List.of(
        ACTIVE_MODULES, TPS, COORDINATES, NETHER_COORDS,
        FPS, PING, SPEED, GAME_MODE, DURABILITY, LOOKING_AT, BREAKING_PROGRESS,
        SERVER, SERVER_IP, SERVER_BRAND, WEATHER, BIOME, WORLD_TIME, REAL_TIME, ROTATION, WATERMARK,
        ARMOR, INVENTORY, ITEM_COUNTER, POTION_TIMERS, COMPASS, ANTI_VANISH,
        CPS, KEYSTROKES, MEMORY, FPS_GRAPH
    );

    private static double lastX;
    private static double lastZ;
    private static long lastSpeedGameTime = Long.MIN_VALUE;
    private static double blocksPerSecond;
    private static double cachedRainbowSpeed = 1.0;
    private static int cachedRainbowSpeedRev = Integer.MIN_VALUE;
    private static final Map<String, CachedHudElement> HUD_CACHE = new HashMap<>();
    private static final Map<String, Map<String, String>> DEFAULT_SETTINGS_CACHE = new HashMap<>();
    private static long hudRenderFrame;
    private static int metricsStableWidth;
    private static long metricsWidthHoldUntil;

    private static List<ElementBounds> HUD_OCCLUDERS = List.of();
    private static boolean defaultsEnsured;

    private AutismHudManager() {
    }

    private static List<String> allIdsCache = ORDER;
    private static int allIdsCacheRevision = -1;

    private static List<String> allIds() {
        if (autismclient.api.hud.HudElements.isEmpty()) return ORDER;
        int revision = autismclient.api.hud.HudElements.revision();
        if (revision != allIdsCacheRevision) {
            List<String> ids = new ArrayList<>(ORDER);
            ids.addAll(autismclient.api.hud.HudElements.ids());
            allIdsCache = ids;
            allIdsCacheRevision = revision;
        }
        return allIdsCache;
    }

    public static int enabledElementCount() {
        ensureDefaults();
        int count = 0;
        for (String id : allIds()) {
            if (state(id).enabled) count++;
        }
        return count;
    }

    private static int lastHudElementsRevision = -1;

    public static void ensureDefaults() {
        AutismConfig config = AutismConfig.getGlobal();
        int hudRevision = autismclient.api.hud.HudElements.revision();

        if (defaultsEnsured && config.hudLayoutMigrated && hudRevision == lastHudElementsRevision) return;
        STATE_CACHE.clear();
        if (!config.hudLayoutMigrated) migrateOldHud(config);
        for (String id : allIds()) state(id);
        normalizeDefaultHudStack(config);
        defaultsEnsured = true;
        lastHudElementsRevision = hudRevision;
    }

    public static List<String> elementIds() {
        ensureDefaults();
        return allIds();
    }

    public static String label(String id) {
        return switch (id) {
            case ACTIVE_MODULES -> "Active Modules";
            case TPS -> "TPS";
            case COORDINATES -> "Coordinates";
            case NETHER_COORDS -> "Nether Coords";
            case FPS -> "FPS";
            case PING -> "Ping";
            case SPEED -> "Speed";
            case GAME_MODE -> "Game Mode";
            case DURABILITY -> "Durability";
            case LOOKING_AT -> "Looking At";
            case BREAKING_PROGRESS -> "Breaking Progress";
            case SERVER -> "Server";
            case WEATHER -> "Weather";
            case BIOME -> "Biome";
            case WORLD_TIME -> "World Time";
            case REAL_TIME -> "Real Time";
            case ROTATION -> "Rotation";
            case WATERMARK -> "Logo";
            case ARMOR -> "Armor";
            case INVENTORY -> "Inventory";
            case ITEM_COUNTER -> "Item Counter";
            case POTION_TIMERS -> "Potion Timers";
            case COMPASS -> "Compass";
            case ANTI_VANISH -> "Anti Vanish";
            case CPS -> "CPS";
            case KEYSTROKES -> "Keystrokes";
            case MEMORY -> "Memory";
            case SERVER_IP -> "Server IP";
            case SERVER_BRAND -> "Server Brand";
            case FPS_GRAPH -> "FPS Graph";
            default -> {
                autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
                yield provider != null ? provider.label() : id;
            }
        };
    }

    public static String description(String id) {
        return switch (id) {
            case ACTIVE_MODULES -> "Enabled Autism modules with optional info and keybinds.";
            case TPS -> "Estimated server TPS from Autism's tick tracker.";
            case COORDINATES -> "Current player coordinates in this dimension.";
            case NETHER_COORDS -> "Overworld/Nether converted coordinates using the 8:1 ratio.";
            case FPS -> "Current client FPS.";
            case PING -> "Current player latency.";
            case SPEED -> "Horizontal player speed in blocks per second.";
            case GAME_MODE -> "Current game mode.";
            case DURABILITY -> "Main hand durability.";
            case LOOKING_AT -> "Block or entity currently under the crosshair.";
            case BREAKING_PROGRESS -> "Current vanilla block-breaking progress.";
            case SERVER -> "Current server or world.";
            case WEATHER -> "Current world weather.";
            case BIOME -> "Current biome.";
            case WORLD_TIME -> "Current world day time.";
            case REAL_TIME -> "Local system time.";
            case ROTATION -> "Camera direction, yaw, and pitch.";
            case WATERMARK -> "Movable AUTISM Client logo image.";
            case ARMOR -> "Equipped armor with vanilla item overlays.";
            case INVENTORY -> "Main inventory grid.";
            case ITEM_COUNTER -> "Counts a picked or held item across the inventory.";
            case POTION_TIMERS -> "Active status effects and durations.";
            case COMPASS -> "Compact facing compass.";
            case ANTI_VANISH -> "Vanish detections.";
            case CPS -> "Left and right mouse clicks per second (counts Fast Use too).";
            case KEYSTROKES -> "Movement keys and mouse buttons with fill animation.";
            case MEMORY -> "JVM heap memory usage and max.";
            case SERVER_IP -> "Connected server IP and port.";
            case SERVER_BRAND -> "Server software brand and version.";
            case FPS_GRAPH -> "Recent FPS history as a graph.";
            default -> {
                autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
                yield provider != null ? provider.description() : "";
            }
        };
    }

    private static final Map<String, AutismConfig.HudElementState> STATE_CACHE = new HashMap<>();
    private static AutismConfig stateCacheConfig;

    public static AutismConfig.HudElementState state(String id) {
        AutismConfig config = AutismConfig.getGlobal();
        if (config != stateCacheConfig) {
            STATE_CACHE.clear();
            stateCacheConfig = config;
        }
        AutismConfig.HudElementState cachedState = STATE_CACHE.get(id);
        if (cachedState != null) return cachedState;
        ensureStateMap(config);
        AutismConfig.HudElementState state = config.hudElements.computeIfAbsent(id, key -> defaultState(key));
        if (state.settings == null) state.settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : defaultSettings(id).entrySet()) {
            state.settings.putIfAbsent(entry.getKey(), entry.getValue());
        }
        STATE_CACHE.put(id, state);
        return state;
    }

    public static void save() {
        AutismConfig.getGlobal().save();
    }

    public static boolean shouldRenderInGame(Screen screen, Module hud) {
        if (PackHideState.isActive()) return false;
        if (hud == null || !hud.isEnabled() || MC.player == null || MC.gui.hud.isHidden()) return false;
        if (screen == null) return true;
        if (screen instanceof autismclient.gui.screen.AutismHudEditorScreen) return false;
        if (screen instanceof ChatScreen) return bool(hud, "show-in-chat", false);
        if (screen.isPauseScreen()) return bool(hud, "show-in-pause", false);
        if (bool(hud, "hide-in-guis", true)) return false;
        return !(screen instanceof AbstractContainerScreen<?>) || !bool(hud, "hide-in-guis", true);
    }

    public static void tick() {
        if (MC.player == null) {
            lastSpeedGameTime = Long.MIN_VALUE;
            blocksPerSecond = 0.0;
            return;
        }
        if (MC.level == null) return;
        long gameTime = MC.level.getGameTime();
        if (gameTime == lastSpeedGameTime) return;
        double x = MC.player.getX();
        double z = MC.player.getZ();
        if (lastSpeedGameTime != Long.MIN_VALUE) {
            long ticks = Math.max(1L, gameTime - lastSpeedGameTime);
            double dx = x - lastX;
            double dz = z - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instant = distance * (20.0 / ticks);
            if (instant > 80.0) instant = horizontalVelocityBps();
            if (instant < 0.01) instant = 0.0;
            blocksPerSecond = (blocksPerSecond * 0.70) + (instant * 0.30);
            if (blocksPerSecond < 0.03) blocksPerSecond = 0.0;
        }
        lastX = x;
        lastZ = z;
        lastSpeedGameTime = gameTime;
    }

    private static double horizontalVelocityBps() {
        if (MC.player == null) return 0.0;
        double vx = MC.player.getDeltaMovement().x;
        double vz = MC.player.getDeltaMovement().z;
        return Math.sqrt(vx * vx + vz * vz) * 20.0;
    }

    public static void render(GuiGraphicsExtractor context, Font font, boolean editor, String selectedId, int mouseX, int mouseY) {
        render(context, font, editor, selectedId, mouseX, mouseY, List.of());
    }

    private static void logHudError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastHudErrorLogMs < 5000L) return;
        lastHudErrorLogMs = now;
        autismclient.AutismClientAddon.LOG.warn("[Autism] HUD '{}' render failed; skipped to protect the UI", where, t);
    }

    public static void render(GuiGraphicsExtractor context, Font font, boolean editor, String selectedId, int mouseX, int mouseY, List<ElementBounds> occluders) {
        long perf = AutismPerf.beginSampled();
        ensureDefaults();
        beginFramePass(font, editor);
        HUD_OCCLUDERS = editor || occluders == null || occluders.isEmpty() ? List.of() : occluders;
        try {
            hudRenderFrame++;
            tick();
            frameRects = collectFrameRects(font);
            for (String id : allIds()) {
                AutismConfig.HudElementState state = state(id);
                if (!state.enabled) continue;
                if (!editor && ANTI_VANISH.equals(id) && !AntiVanishModule.shouldShowHud()) continue;
                if (combinedMetricsRowOwns(id)) continue;
                CachedHudElement cached = cached(id, font);
                ElementBounds bounds = cached.layout().bounds();
                if (bounds.width <= 0 || bounds.height <= 0) continue;
                int dodgeY = 0;
                if (!editor && ANTI_VANISH.equals(id)) {

                    dodgeY = computeDodge(bounds);
                } else if (!ACTIVE_MODULES.equals(id) && occluded(bounds)) {
                    continue;
                }
                boolean hovered = hover(mouseX, mouseY, bounds);
                try {
                    renderElement(context, font, id, state, cached, editor, selectedId != null && selectedId.equals(id), hovered, dodgeY);
                } catch (Throwable t) {

                    logHudError("element:" + id, t);
                }
            }
        } finally {
            HUD_OCCLUDERS = List.of();
            frameRects = null;
            endFramePass();
            AutismPerf.end("hud.render", perf);
        }
    }

    public static HudLayout layout(String id, Font font) {
        return cached(id, font).layout();
    }

    public static void renderSingle(GuiGraphicsExtractor context, Font font, String id) {
        if (context == null || font == null || id == null || PackHideState.isActive()) return;
        ensureDefaults();
        AutismConfig.HudElementState state = state(id);
        if (!state.enabled) return;
        if (ANTI_VANISH.equals(id) && !AntiVanishModule.shouldShowHud()) return;
        beginFramePass(font, false);
        try {
            frameRects = collectFrameRects(font);
            CachedHudElement cached = cached(id, font);
            ElementBounds bounds = cached.layout().bounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            renderElement(context, font, id, state, cached, false, false, false, 0);
        } finally {
            frameRects = null;
            endFramePass();
        }
    }

    private static HudLayout computeLayout(String id, Font font, AutismConfig.HudElementState state, List<HudLine> lines, List<Integer> widths) {
        int pad = padding(id);
        int width;
        int height;
        if (WATERMARK.equals(id)) {
            int logoW = logoWidth(id);
            width = logoW + pad * 2 + logoRightPadding(id);
            height = logoHeight(logoW) + pad * 2;
        } else if (ARMOR.equals(id)) {
            width = pad * 2 + 4 * 18;
            height = pad * 2 + 18;
        } else if (INVENTORY.equals(id)) {
            width = pad * 2 + 9 * 18;
            height = pad * 2 + 3 * 18;
        } else if (COMPASS.equals(id)) {
            width = pad * 2 + compassWidth(id);
            height = pad * 2 + 18;
        } else if (KEYSTROKES.equals(id)) {
            int unit = keystrokeUnit(id);
            int keyH = keystrokeKeyHeight(unit);
            int gap = 2;
            width = pad * 2 + 3 * unit + 2 * gap;
            int rows = 2 * keyH + gap;
            if (keystrokesShowSpace(id)) rows += gap + keyH;
            if (keystrokesShowMouse(id)) rows += gap + keyH;
            height = pad * 2 + rows;
        } else if (autismclient.api.hud.HudElements.isAddon(id)) {
            autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
            int pw = 16, ph = 10;
            try { pw = Math.max(1, provider.width()); ph = Math.max(1, provider.height()); }
            catch (Throwable t) { autismclient.AutismClientAddon.LOG.warn("[Hud] Addon element '{}' sizing failed", id, t); }
            width = pad * 2 + pw;
            height = pad * 2 + ph;
        } else if (ACTIVE_MODULES.equals(id)) {
            int maxW = 0;
            for (Integer lineWidth : widths) maxW = Math.max(maxW, lineWidth);
            int rowH = activeModuleRowHeight(id);
            int gap = lineGap(id);
            width = Math.max(32, maxW + pad * 2);
            height = lines.isEmpty() ? 0 : lines.size() * rowH + Math.max(0, lines.size() - 1) * gap;
        } else if (FPS.equals(id)) {
            int maxW = 0;
            for (Integer lineWidth : widths) maxW = Math.max(maxW, lineWidth);
            width = stableMetricsWidth(maxW) + pad * 2;
            height = THEME.fontHeight(UiTone.BODY) + verticalPadding(id) * 2;
        } else {
            int lineH = lineHeight(id);
            int maxW = 0;
            for (Integer lineWidth : widths) maxW = Math.max(maxW, lineWidth);
            width = Math.max(32, maxW + pad * 2);
            int vpad = verticalPadding(id);
            height = Math.max(THEME.fontHeight(UiTone.BODY) + vpad * 2, lines.size() * lineH - lineGap(id) + vpad * 2);
        }
        int renderX = safeContentX(id, anchorX(state.anchor, state.x, width), width);
        int renderY = safeContentY(id, anchorY(state.anchor, state.y, height), height);
        return new HudLayout(id, renderX, renderY, width, height, 1.0);
    }

    public static ElementBounds bounds(String id, Font font) {
        return layout(id, font).bounds();
    }

    private static ElementBounds visualBounds(String id, Font font) {
        CachedHudElement cached = cached(id, font);
        if (ACTIVE_MODULES.equals(id)) {
            List<VisualRect> rects = activeModuleVisualRects(id, cached);
            if (rects.isEmpty()) return cached.layout().bounds();
            int left = Integer.MAX_VALUE;
            int top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int bottom = Integer.MIN_VALUE;
            for (VisualRect rect : rects) {
                left = Math.min(left, rect.x());
                top = Math.min(top, rect.y());
                right = Math.max(right, rect.right());
                bottom = Math.max(bottom, rect.bottom());
            }
            return new ElementBounds(id, left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
        ElementBounds bounds = cached.layout().bounds();
        VisualRect rect = visualChromeRect(new VisualRect(id, bounds.x(), bounds.y(), bounds.width(), bounds.height()), cached.style());
        return new ElementBounds(id, rect.x(), rect.y(), rect.width(), rect.height());
    }

    public static String hit(Font font, int mouseX, int mouseY) {
        ensureDefaults();
        List<String> ids = allIds();
        for (int i = ids.size() - 1; i >= 0; i--) {
            String id = ids.get(i);
            if (!state(id).enabled) continue;
            ElementBounds bounds = visualBounds(id, font);
            if (hover(mouseX, mouseY, bounds)) return id;
        }
        return null;
    }

    public static void move(String id, int x, int y, int screenW, int screenH) {
        AutismConfig.HudElementState state = state(id);
        HudLayout layout = layout(id, MC.font);
        int w = layout.scaledWidth();
        int h = layout.scaledHeight();
        int px = clamp(x, safeZoneXFor(id), maxSafeX(id, screenW, w));
        int py = clamp(y, safeZoneYFor(id), maxSafeY(id, screenH, h));

        boolean right = px > screenW / 2 || px + w >= screenW - 2 || (px + w / 2) >= screenW / 2;
        boolean bottom = py > screenH / 2 || py + h >= screenH - 2 || (py + h / 2) >= screenH / 2;
        state.anchor = (bottom ? "BOTTOM_" : "TOP_") + (right ? "RIGHT" : "LEFT");

        state.x = right ? (px + w - screenW) : px;
        state.y = bottom ? (py + h - screenH) : py;
        HUD_CACHE.remove(id);
        hudSettingsRevision++;
        save();
    }

    public static void setEnabled(String id, boolean enabled) {
        state(id).enabled = enabled;
        hudSettingsRevision++;
        save();
    }

    public static void toggle(String id) {
        AutismConfig.HudElementState state = state(id);
        state.enabled = !state.enabled;
        hudSettingsRevision++;
        save();
    }

    public static String setting(String id, String key) {
        return state(id).settings.getOrDefault(key, defaultSettings(id).getOrDefault(key, ""));
    }

    public static String defaultSetting(String id, String key) {
        return defaultSettings(id).getOrDefault(key, "");
    }

    public static void setSetting(String id, String key, String value) {
        state(id).settings.put(key, value == null ? "" : value);
        hudSettingsRevision++;
        save();
    }

    public static boolean boolSetting(String id, String key) {
        return Boolean.parseBoolean(setting(id, key));
    }

    public static int intSetting(String id, String key, int fallback) {
        try {
            return Integer.parseInt(setting(id, key));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static double doubleSetting(String id, String key, double fallback) {
        try {
            return Double.parseDouble(setting(id, key));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static void resetElement(String id) {
        AutismConfig config = AutismConfig.getGlobal();
        config.hudElements.put(id, defaultState(id));
        STATE_CACHE.remove(id);
        hudSettingsRevision++;
        save();
    }

    public static void resetAllElements() {
        AutismConfig config = AutismConfig.getGlobal();
        ensureStateMap(config);
        config.hudElements.clear();
        for (String id : allIds()) config.hudElements.put(id, defaultState(id));
        defaultsEnsured = false;
        HUD_CACHE.clear();
        STATE_CACHE.clear();
        hudSettingsRevision++;
        save();
    }

    public static List<String> lines(String id) {
        List<String> lines = new ArrayList<>();
        for (HudLine line : cached(id, MC.font).lines()) lines.add(line.plainText());
        if (lines.isEmpty()) lines.add(label(id));
        return lines;
    }

    public static int settingsRevision() {
        return hudSettingsRevision;
    }

    private static int hudSettingsRevision;
    private static boolean fastPassActive;
    private static boolean frameEnvChanged;
    private static long frameNowMs;
    private static int lastEnvScreenW, lastEnvScreenH, lastEnvPlayerId, lastEnvFontId,
                       lastEnvSettingsRev, lastEnvModuleRev, lastEnvHudElementsRev, lastEnvConfigId, lastEnvThemeRev;

    private static void beginFramePass(Font font, boolean editor) {
        frameNowMs = System.currentTimeMillis();
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int playerId = MC.player == null ? 0 : MC.player.getId();
        int fontId = System.identityHashCode(font);
        int moduleRev = ModuleRegistry.revision();
        int hudElsRev = autismclient.api.hud.HudElements.revision();
        int configId = System.identityHashCode(AutismConfig.getGlobal());
        int themeRev = AutismTheme.generation();
        frameEnvChanged = screenW != lastEnvScreenW || screenH != lastEnvScreenH || playerId != lastEnvPlayerId
            || fontId != lastEnvFontId || hudSettingsRevision != lastEnvSettingsRev || moduleRev != lastEnvModuleRev
            || hudElsRev != lastEnvHudElementsRev || configId != lastEnvConfigId || themeRev != lastEnvThemeRev;
        lastEnvScreenW = screenW;
        lastEnvScreenH = screenH;
        lastEnvPlayerId = playerId;
        lastEnvFontId = fontId;
        lastEnvSettingsRev = hudSettingsRevision;
        lastEnvModuleRev = moduleRev;
        lastEnvHudElementsRev = hudElsRev;
        lastEnvConfigId = configId;
        lastEnvThemeRev = themeRev;

        fastPassActive = !editor;
    }

    private static void endFramePass() {
        fastPassActive = false;
    }

    private static CachedHudElement cached(String id, Font font) {
        CachedHudElement fastCached = HUD_CACHE.get(id);
        if (fastPassActive && !frameEnvChanged && fastCached != null && frameNowMs < fastCached.nextSignatureCheckAtMs) {
            return fastCached;
        }
        AutismConfig.HudElementState state = state(id);
        long now = fastPassActive ? frameNowMs : System.currentTimeMillis();
        HudCacheKey signature = cacheSignature(id, state, font, now);
        long interval = cacheIntervalMillis(id);
        long nextCheck = (now / interval + 1) * interval;
        CachedHudElement cached = HUD_CACHE.get(id);
        if (cached != null && cached.signature().equals(signature)) {
            cached.nextSignatureCheckAtMs = nextCheck;
            return cached;
        }
        List<HudLine> lines = ARMOR.equals(id) || INVENTORY.equals(id) || COMPASS.equals(id) || WATERMARK.equals(id)
                || KEYSTROKES.equals(id)
                || autismclient.api.hud.HudElements.isAddon(id)
            ? List.of()
            : buildLines(id);
        List<Integer> widths = new ArrayList<>(lines.size());
        for (HudLine line : lines) widths.add(stableLineWidth(id, font, line));
        HudLayout layout = computeLayout(id, font, state, lines, widths);
        CachedHudElement next = new CachedHudElement(signature, lines, widths, layout, computeStyle(id));
        next.nextSignatureCheckAtMs = nextCheck;
        HUD_CACHE.put(id, next);
        return next;
    }

    private static int stableLineWidth(String id, Font font, HudLine line) {
        int width = lineWidth(font, line);
        if (BREAKING_PROGRESS.equals(id)) {
            width = Math.max(width, lineWidth(font, row(id, "Breaking", "100%")));
        }
        return width;
    }

    private static HudCacheKey cacheSignature(String id, AutismConfig.HudElementState state, Font font, long now) {
        long nowBucket = now / cacheIntervalMillis(id);
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int playerId = MC.player == null ? 0 : MC.player.getId();
        int activeRevision = ACTIVE_MODULES.equals(id) ? ModuleRegistry.activeRevision() : 0;
        int moduleRevision = ACTIVE_MODULES.equals(id) ? ModuleRegistry.revision() : 0;
        return new HudCacheKey(
            state.enabled,
            state.anchor,
            state.x,
            state.y,
            state.settings.hashCode(),
            nowBucket,
            screenW,
            screenH,
            playerId,
            activeRevision,
            moduleRevision,
            System.identityHashCode(font),
            AutismTheme.generation()
        );
    }

    private static long cacheIntervalMillis(String id) {
        return switch (id) {
            case ACTIVE_MODULES, COORDINATES, NETHER_COORDS, SPEED, LOOKING_AT, BREAKING_PROGRESS, ROTATION, COMPASS, ANTI_VANISH, CPS, KEYSTROKES -> 50L;
            case FPS, TPS, PING, REAL_TIME, WORLD_TIME, POTION_TIMERS, ITEM_COUNTER -> 250L;
            default -> 500L;
        };
    }

    private static List<HudLine> buildLines(String id) {
        List<HudLine> lines = new ArrayList<>();
        if (MC.player == null) {
            lines.add(row(id, label(id), "Preview"));
            return lines;
        }
        switch (id) {
            case ACTIVE_MODULES -> activeModuleLines(lines);
            case TPS -> lines.add(tpsLine());
            case COORDINATES -> { double[] p = AutismFakeCoords.apply(MC.player.getX(), MC.player.getY(), MC.player.getZ());
                                  lines.add(row(id, "Pos", blockPositionText(p[0], p[1], p[2]))); }
            case NETHER_COORDS -> lines.add(oppositeCoordsLine());
            case FPS -> lines.add(metricsLine());
            case PING -> lines.add(pingLine());
            case SPEED -> lines.add(row(id, "Speed", String.format(Locale.ROOT, "%.2f b/s", blocksPerSecond)));
            case GAME_MODE -> lines.add(row(id, "Game", gameMode()));
            case DURABILITY -> lines.add(durabilityLine());
            case LOOKING_AT -> lines.add(lookingAtLine());
            case BREAKING_PROGRESS -> lines.add(breakingLine());
            case SERVER -> lines.add(serverLine());
            case WEATHER -> lines.add(weatherLine());
            case BIOME -> lines.add(biomeLine());
            case WORLD_TIME -> lines.add(worldTimeLine());
            case REAL_TIME -> lines.add(realTimeLine());
            case ROTATION -> lines.add(row(id, "Rot", String.format(Locale.ROOT, "%.1f yaw, %.1f pitch", MC.player.getYRot(), MC.player.getXRot())));
            case WATERMARK -> {
            }
            case ITEM_COUNTER -> lines.add(itemCounterLine());
            case POTION_TIMERS -> potionLines(lines);
            case COMPASS -> lines.add(row(id, "Compass", directionName()));
            case ANTI_VANISH -> antiVanishLines(lines);
            case CPS -> cpsLines(lines);
            case MEMORY -> lines.add(memoryLine());
            case SERVER_IP -> lines.add(serverIpLine());
            case SERVER_BRAND -> lines.add(row(id, "Brand", serverBrand()));
            case FPS_GRAPH -> lines.add(fpsGraphLine());
            default -> lines.add(row(id, label(id), ""));
        }
        if (ACTIVE_MODULES.equals(id) && lines.isEmpty()) return lines;
        if (lines.isEmpty()) lines.add(row(id, label(id), ""));
        return lines;
    }

    private static void activeModuleLines(List<HudLine> lines) {
        boolean showInfo = boolSetting(ACTIVE_MODULES, "module-info");
        boolean showKeybind = boolSetting(ACTIVE_MODULES, "show-keybind");
        String hidden = "|" + setting(ACTIVE_MODULES, "hidden-modules").toLowerCase(Locale.ROOT) + "|";
        List<Module> modules = new ArrayList<>(ModuleRegistry.activeModules());
        modules.removeIf(module -> !module.showInArrayList());
        modules.removeIf(module -> hidden.contains("|" + module.id().toLowerCase(Locale.ROOT) + "|"));
        String sort = setting(ACTIVE_MODULES, "sort");
        if ("Name".equals(sort)) modules.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
        else if ("Category".equals(sort)) modules.sort(Comparator.comparing((Module m) -> m.category().label()).thenComparing(Module::name));
        else {

            Map<Module, Integer> plainWidths = new java.util.IdentityHashMap<>(modules.size() * 2);
            for (Module module : modules) plainWidths.put(module, modulePlainWidth(module, showInfo, showKeybind));
            modules.sort((a, b) -> {
                int widthCompare = Integer.compare(plainWidths.get(b), plainWidths.get(a));
                return widthCompare != 0 ? widthCompare : a.name().compareToIgnoreCase(b.name());
            });
        }

        String colorMode = setting(ACTIVE_MODULES, "color-mode");
        int moduleCount = modules.size();

        RainbowParams rainbow = buildRainbowParams(colorMode, moduleCount);
        int infoColor = color("module-info-color", 0xFFB79E9E);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int moduleColor = activeModuleColor(module, i, rainbow);
            HudLine line = new HudLine();
            line.add(module.name(), moduleColor);
            String info = module.info();
            if (showInfo && info != null && !info.isBlank()) line.add(" " + info, infoColor);
            if (showKeybind && module.keybind() != -1) line.add(" [" + AutismBindUtil.getBindName(module.keybind()) + "]", infoColor);
            lines.add(line);
        }
    }

    private static void antiVanishLines(List<HudLine> lines) {

        for (AntiVanishModule.HudEntry entry : AntiVanishModule.hudEntries()) {
            lines.add(row(ANTI_VANISH, AntiVanishModule.hudTag(entry), AntiVanishModule.hudValue(entry)));
        }

        if (lines.isEmpty()) lines.add(row(ANTI_VANISH, "Vanish", "Clear"));
    }

    private record RainbowParams(String mode, float basePhase, float spread, float saturation, float brightness,
                                 int flatColor, int gradientStart, int gradientEnd, int valueColor, double gradientRows) {}

    private static RainbowParams buildRainbowParams(String colorMode, int moduleCount) {

        return new RainbowParams(
            colorMode,
            rainbowPhase(cachedRainbowSpeed()),
            (float) doubleSetting(ACTIVE_MODULES, "rainbow-spread", 0.035),
            Math.min(0.35f, (float) doubleSetting(ACTIVE_MODULES, "rainbow-saturation", 0.35)),
            (float) doubleSetting(ACTIVE_MODULES, "rainbow-brightness", 1.0),
            color(ACTIVE_MODULES, "flat-color", 0xFFFF3B3B),
            color(ACTIVE_MODULES, "gradient-start-color", 0xFFFF3B3B),
            color(ACTIVE_MODULES, "gradient-end-color", 0xFFFFD6D6),
            color(ACTIVE_MODULES, "value-color", 0xFFF3ECE7),
            Math.max(1.0, moduleCount - 1.0));
    }

    private static double cachedRainbowSpeed() {
        if (cachedRainbowSpeedRev != hudSettingsRevision) {
            cachedRainbowSpeed = doubleSetting(ACTIVE_MODULES, "rainbow-speed", 1.0);
            cachedRainbowSpeedRev = hudSettingsRevision;
        }
        return cachedRainbowSpeed;
    }

    private static float rainbowPhase(double speed) {
        if (speed <= 0.0) return 0.0f;
        double periodMs = 1000.0 / (0.0525 * speed);
        long period = Math.max(1L, (long) periodMs);
        return (float) ((System.currentTimeMillis() % period) / (double) period);
    }

    private static int activeModuleColor(Module module, int index, RainbowParams rainbow) {
        if ("Flat".equals(rainbow.mode())) return rainbow.flatColor();
        if ("Random".equals(rainbow.mode())) return 0xFF000000 | (module.id().hashCode() & 0x00FFFFFF);
        if ("Gradient".equals(rainbow.mode())) {
            double spread = rainbow.spread() * 3.0;
            double phase = (rainbow.basePhase() + index * spread + index / rainbow.gradientRows()) % 1.0;
            double t = phase < 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
            return lerpColor(rainbow.gradientStart(), rainbow.gradientEnd(), t);
        }
        float hue = (rainbow.basePhase() + index * rainbow.spread()) % 1.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, rainbow.saturation(), rainbow.brightness());
        return softenColor(0xE8000000 | (rgb & 0x00FFFFFF), rainbow.valueColor(), 0.55);
    }

    private static String moduleLinePlain(Module module, boolean showInfo, boolean showKeybind) {
        StringBuilder line = new StringBuilder(module.name());
        String info = module.info();
        if (showInfo && info != null && !info.isBlank()) line.append(' ').append(info);
        if (showKeybind && module.keybind() != -1) line.append(" [").append(AutismBindUtil.getBindName(module.keybind())).append(']');
        return line.toString();
    }

    private static int modulePlainWidth(Module module, boolean showInfo, boolean showKeybind) {
        return UiText.width(MC.font, moduleLinePlain(module, showInfo, showKeybind),
            THEME.fontFor(UiTone.BODY), color(ACTIVE_MODULES, "text-color", 0xFFF3ECE7));
    }

    private static HudLine oppositeCoordsLine() {
        if (MC.level == null || MC.player == null) return row(NETHER_COORDS, "Opposite", "N/A");
        Identifier dimension = MC.level.dimension().identifier();
        String path = dimension.getPath();
        double[] p = AutismFakeCoords.apply(MC.player.getX(), MC.player.getY(), MC.player.getZ());
        double x = p[0];
        double y = p[1];
        double z = p[2];
        if ("overworld".equals(path)) return row(NETHER_COORDS, "Nether", blockPositionText(x / 8.0, y, z / 8.0));
        if ("the_nether".equals(path)) return row(NETHER_COORDS, "Overworld", blockPositionText(x * 8.0, y, z * 8.0));
        return row(NETHER_COORDS, "Opposite", "N/A");
    }

    private static void renderElement(GuiGraphicsExtractor context, Font font, String id, AutismConfig.HudElementState state, CachedHudElement cached, boolean editor, boolean selected, boolean hovered, int dodgeY) {
        float alpha = 1.0f;
        HudLayout layout = cached.layout();
        int x = layout.x();
        int y = layout.y() + dodgeY;
        int unscaledW = layout.unscaledWidth();
        int unscaledH = layout.unscaledHeight();

        if (ACTIVE_MODULES.equals(id)) {
            renderActiveModuleRows(context, font, id, cached, editor, selected, hovered, alpha);
            return;
        }
        HudStyle style = cached.style();

        boolean flatGrid = (ARMOR.equals(id) || INVENTORY.equals(id)) && flatSlots(id);
        boolean background = style.background() && !flatGrid;
        VisualRect contentRect = new VisualRect(id, x, y, unscaledW, unscaledH);
        VisualRect rect = visualChromeRect(contentRect, style);

        boolean inGameOutline = style.outline() && !flatGrid && !editor && !selected && !hovered;
        boolean needsBlockers = (background && !COMPASS.equals(id)) || inGameOutline;
        List<VisualRect> blockers = needsBlockers ? mergeBlockers(font, id, rect) : null;
        if (background) {
            if (COMPASS.equals(id)) drawCompassBackground(context, rect.x(), rect.y(), rect.width(), rect.height(), alpha, style.backgroundColor());
            else drawMergedBackground(context, font, id, rect, alphaColor(style.backgroundColor(), alpha), blockers);
        }
        else if (editor) UiText.fill(context, rect.x(), rect.y(), rect.right(), rect.bottom(), editorWash(selected));

        if ((style.outline() && !flatGrid) || editor || selected || hovered) {

            int border = selected ? color(id, "accent-color", 0xFFFF3B3B)
                : editor ? color(id, "outline-color", 0xAAFF3B3B)
                : style.outlineColor();
            int outlineColor = alphaColor(border, alpha);
            if (!editor && !selected && !hovered) outlineMergedRect(context, rect, blockers, outlineColor, style.outlineWidth());
            else outline(context, rect.x(), rect.y(), rect.width(), rect.height(), outlineColor, style.outlineWidth());
        }

        int pad = style.padding();
        if (WATERMARK.equals(id)) renderLogo(context, x + pad, y + pad, logoWidth(id), logoHeight(logoWidth(id)), alpha);
        else if (ARMOR.equals(id)) renderArmor(context, x + pad, y + pad, alpha, style);
        else if (INVENTORY.equals(id)) renderInventory(context, x + pad, y + pad, alpha, style);
        else if (COMPASS.equals(id)) renderCompass(context, font, id, x + pad, y + pad, unscaledW - pad * 2, unscaledH - pad * 2, alpha, style);
        else if (KEYSTROKES.equals(id)) renderKeystrokes(context, font, id, x + pad, y + pad, unscaledW - pad * 2, unscaledH - pad * 2, alpha);
        else if (autismclient.api.hud.HudElements.isAddon(id)) {
            autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
            try { provider.render(context, font, x + pad, y + pad, alpha); }
            catch (Exception e) { autismclient.AutismClientAddon.LOG.warn("[Hud] Addon element '{}' render failed", id, e); }
        }
        else renderTextLines(context, font, id, state, cached, x, y, unscaledW, alpha);
    }

    private static void renderActiveModuleRows(GuiGraphicsExtractor context, Font font, String id, CachedHudElement cached, boolean editor, boolean selected, boolean hovered, float alpha) {
        List<HudLine> lines = cached.lines();
        List<VisualRect> rowRects = activeModuleContentRects(id, cached);
        HudStyle style = cached.style();
        int pad = style.padding();
        boolean background = style.background();
        boolean drawOutline = style.outline() || editor || selected || hovered;
        boolean shadow = style.shadow();
        int backgroundColor = alphaColor(style.backgroundColor(), alpha);
        int editorColor = editorWash(selected);

        int border = selected ? color(id, "accent-color", 0xFFFF3B3B)
            : editor ? color(id, "outline-color", 0xAAFF3B3B)
            : style.outlineColor();
        int outlineColor = alphaColor(border, alpha);
        int outlineWidth = style.outlineWidth();
        int verticalPad = style.verticalPadding();

        for (int i = 0; i < lines.size() && i < rowRects.size(); i++) {
            VisualRect contentRect = rowRects.get(i);
            VisualRect rect = visualChromeRect(contentRect, style);
            if (occluded(rect)) continue;

            List<VisualRect> blockers = (background || drawOutline) ? mergeBlockers(font, id, rect) : null;
            if (background) drawMergedBackground(context, font, id, rect, backgroundColor, blockers);
            else if (editor) UiText.fill(context, rect.x(), rect.y(), rect.right(), rect.bottom(), editorColor);

            if (drawOutline) {
                outlineMergedRect(context, rect, blockers, outlineColor, outlineWidth);
            }

            int lineW = i < cached.widths().size() ? cached.widths().get(i) : -1;
            renderLineSegments(context, font, lines.get(i), contentRect.x() + pad, contentRect.y() + verticalPad, Math.max(1, contentRect.width() - pad * 2), alpha, shadow, lineW);
        }
    }

    private static void renderLogo(GuiGraphicsExtractor context, int x, int y, int width, int height, float alpha) {
        if (AutismSvgHudLogo.render(context, x, y, width, height, alpha)) return;
        context.blit(RenderPipelines.GUI_TEXTURED, AutismThemeTextures.recolored(HUD_LOGO, AutismTheme.Channel.ACCENT), x, y, 0.0F, 0.0F, width, height,
            HUD_LOGO_TEXTURE_WIDTH, HUD_LOGO_TEXTURE_HEIGHT, HUD_LOGO_TEXTURE_WIDTH, HUD_LOGO_TEXTURE_HEIGHT, ARGB.white(alpha));
    }

    private static void renderTextLines(GuiGraphicsExtractor context, Font font, String id, AutismConfig.HudElementState state, CachedHudElement cached, int renderX, int renderY, int unscaledW, float alpha) {
        List<HudLine> lines = cached.lines();
        HudStyle style = cached.style();
        int pad = style.padding();
        int y = renderY + style.verticalPadding();
        int lineH = style.lineHeight();
        String alignment = style.alignment();
        boolean shadow = style.shadow();
        int maxW = Math.max(1, unscaledW - pad * 2);
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            int lineW = i < cached.widths().size() ? cached.widths().get(i) : lineWidth(font, line);
            int tx = renderX + pad;
            if ("Center".equals(alignment)) tx = renderX + Math.max(pad, (unscaledW - lineW) / 2);
            else if ("Right".equals(alignment)) tx = renderX + Math.max(pad, unscaledW - pad - lineW);
            renderLineSegments(context, font, line, tx, y, maxW, alpha, shadow, lineW);
            y += lineH;
        }
    }

    private static void renderLineSegments(GuiGraphicsExtractor context, Font font, HudLine line, int x, int y, int maxW, float alpha, boolean shadow, int lineWidth) {
        int drawX = x;
        int used = 0;

        boolean fits = lineWidth >= 0 && lineWidth <= maxW;
        int[] cachedWidths = fits ? line.segmentWidths : null;
        boolean cachedWidthsValid = cachedWidths != null && cachedWidths.length == line.segments.size();
        for (int i = 0; i < line.segments.size(); i++) {
            HudSegment segment = line.segments.get(i);
            int remaining = Math.max(1, maxW - used);
            String text = fits ? segment.text : UiText.trimToWidth(font, segment.text, remaining, THEME.fontFor(UiTone.BODY), segment.color);
            UiText.draw(context, font, text, THEME.fontFor(UiTone.BODY), alphaColor(segment.color, alpha), drawX, y, shadow);
            int width = cachedWidthsValid ? cachedWidths[i] : UiText.width(font, text, THEME.fontFor(UiTone.BODY), segment.color);
            drawX += width;
            used += width;
            if (used >= maxW) break;
        }
    }

    private static void renderArmor(GuiGraphicsExtractor context, int x, int y, float alpha, HudStyle style) {
        if (MC.player == null) return;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        boolean flat = flatSlots(ARMOR);
        if (flat) drawFlatSlotGrid(context, x, y, slots.length, 1, alpha, style);
        for (int i = 0; i < slots.length; i++) {
            int sx = x + i * 18;
            if (!flat) drawHudSlot(context, sx, y);
            ItemStack stack = MC.player.getItemBySlot(slots[i]);
            renderHudItem(context, stack, sx + 1, y + 1);
        }
    }

    private static void renderInventory(GuiGraphicsExtractor context, int x, int y, float alpha, HudStyle style) {
        if (MC.player == null) return;
        boolean flat = flatSlots(INVENTORY);
        if (flat) drawFlatSlotGrid(context, x, y, 9, 3, alpha, style);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + col * 18;
                int sy = y + row * 18;
                if (!flat) drawHudSlot(context, sx, sy);
                ItemStack stack = MC.player.getInventory().getItem(9 + row * 9 + col);
                renderHudItem(context, stack, sx + 1, sy + 1);
            }
        }
    }

    private static void renderHudItem(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || MC.font == null) return;
        context.item(stack, x, y);
        context.itemDecorations(MC.font, stack, x, y);
    }

    private static boolean flatSlots(String id) {
        return "Flat".equals(setting(id, "slot-style"));
    }

    private static void drawFlatSlotGrid(GuiGraphicsExtractor context, int x, int y, int cols, int rows,
                                         float alpha, HudStyle style) {
        int w = cols * 18;
        int h = rows * 18;
        if (style.background()) {
            UiText.fill(context, x, y, x + w, y + h, alphaColor(style.backgroundColor(), alpha));
        }
        if (style.outline()) {
            int line = alphaColor(style.outlineColor(), alpha);
            for (int i = 0; i <= cols; i++) {

                int lx = i == cols ? x + w - 1 : x + i * 18;
                UiText.fill(context, lx, y, lx + 1, y + h, line);
            }
            for (int j = 0; j <= rows; j++) {
                int ly = j == rows ? y + h - 1 : y + j * 18;
                UiText.fill(context, x, ly, x + w, ly + 1, line);
            }
        }
    }

    private static void drawHudSlot(GuiGraphicsExtractor context, int x, int y) {

        int fill = AutismTheme.recolor(0x201A1215, AutismTheme.Channel.BACKDROP);
        int lit = AutismTheme.recolor(0x55912E35, AutismTheme.Channel.OUTLINE);
        int shade = AutismTheme.recolor(0x551B0B0E, AutismTheme.Channel.BACKDROP);
        UiText.fill(context, x, y, x + 18, y + 18, fill);
        UiText.fill(context, x, y, x + 18, y + 1, lit);
        UiText.fill(context, x, y + 17, x + 18, y + 18, shade);
        UiText.fill(context, x, y, x + 1, y + 18, lit);
        UiText.fill(context, x + 17, y, x + 18, y + 18, shade);
    }

    private static final String[] COMPASS_LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final int[] COMPASS_LABEL_WIDTHS = new int[COMPASS_LABELS.length];
    private static Font compassWidthsFont;
    private static int compassWidthsReloadGen = Integer.MIN_VALUE;

    private static void renderCompass(GuiGraphicsExtractor context, Font font, String id, int x, int y, int width, int height, float alpha, HudStyle style) {
        int stripH = Math.max(16, height);
        int centerX = x + width / 2;
        UiText.fill(context, centerX, y + 2, centerX + 1, y + stripH - 2, alphaColor(style.accentColor(), alpha * 0.88f));
        String[] labels = COMPASS_LABELS;

        int reloadGen = UiText.reloadGeneration();
        if (compassWidthsFont != font || compassWidthsReloadGen != reloadGen) {
            for (int i = 0; i < labels.length; i++) {
                COMPASS_LABEL_WIDTHS[i] = UiText.width(font, labels[i], THEME.fontFor(UiTone.BODY), 0xFFFFFFFF);
            }
            compassWidthsFont = font;
            compassWidthsReloadGen = reloadGen;
        }
        float yaw = MC.player == null ? 180.0f : MC.player.getYRot();
        double normalized = ((yaw % 360.0) + 360.0) % 360.0;
        int yText = y + Math.max(2, (stripH - THEME.fontHeight(UiTone.BODY)) / 2);
        double visibleDegrees = 118.0;
        boolean fullHeightHighlight = style.background();
        boolean shadow = style.shadow();
        for (int i = 0; i < labels.length; i++) {
            double centerAngle = i * 45.0;
            double delta = wrappedDegrees(centerAngle - normalized);
            double distance = Math.abs(delta);
            if (distance > visibleDegrees) continue;
            double presence = 1.0 - (distance / visibleDegrees);
            presence = presence * presence * (3.0 - 2.0 * presence);
            int tx = centerX + (int) Math.round(delta / visibleDegrees * (width / 2.0 - 8.0));
            double face = Math.max(0.0, 1.0 - distance / 24.0);
            int baseColor = lerpColor(style.labelColor(), style.accentColor(), face);
            int textColor = alphaColor(baseColor, alpha * (0.18f + (float) presence * 0.82f));
            int tw = COMPASS_LABEL_WIDTHS[i];
            int halo = alphaColor(AutismTheme.recolor(0x38280D12, AutismTheme.Channel.BACKDROP), alpha * (float) face);
            if (face > 0.0) {
                int haloTop = fullHeightHighlight ? y : y + 2;
                int haloBottom = fullHeightHighlight ? y + stripH : y + stripH - 2;
                UiText.fill(context, tx - tw / 2 - 3, haloTop, tx + tw / 2 + 3, haloBottom, halo);
            }
            UiText.draw(context, font, labels[i], THEME.fontFor(UiTone.BODY), alphaColor(0xCC000000, alpha * (0.24f + (float) presence * 0.36f)), tx - tw / 2 + 1, yText + 1, false);
            UiText.draw(context, font, labels[i], THEME.fontFor(UiTone.BODY), textColor, tx - tw / 2, yText, shadow);
        }
    }

    private static final float[] KS_FILL = new float[7];
    private static long ksAnimNanos;

    private static int keystrokeUnit(String id) {
        return clamp(intSetting(id, KEY_KS_SIZE, 18), 12, 40);
    }

    private static int keystrokeKeyHeight(int unit) {
        return Math.max(10, Math.round(unit * 0.86f));
    }

    private static boolean keystrokesShowSpace(String id) {
        return boolSetting(id, KEY_KS_SHOW_SPACE);
    }

    private static boolean keystrokesShowMouse(String id) {
        return boolSetting(id, KEY_KS_SHOW_MOUSE);
    }

    private static void renderKeystrokes(GuiGraphicsExtractor context, Font font, String id, int x, int y, int width, int height, float alpha) {
        int unit = keystrokeUnit(id);
        int gap = 2;

        int active = color(id, KEY_KS_ACTIVE_COLOR, 0xFFFF3B3B);
        int idle = color(id, KEY_KS_IDLE_COLOR, 0xB4101014);
        int text = color(id, KEY_KS_TEXT_COLOR, 0xFFFFFFFF);
        boolean showMouse = keystrokesShowMouse(id);

        long now = System.nanoTime();
        float dt = ksAnimNanos == 0L ? 0f : (float) Math.min(0.1, (now - ksAnimNanos) / 1_000_000_000.0);
        ksAnimNanos = now;
        float factor = 1f - (float) Math.exp(-dt * 16.0);
        boolean[] pressed = keystrokePressed();
        for (int i = 0; i < KS_FILL.length; i++) {
            float target = pressed[i] ? 1f : 0f;
            KS_FILL[i] += (target - KS_FILL[i]) * factor;
            if (Math.abs(target - KS_FILL[i]) < 0.003f) KS_FILL[i] = target;
        }

        int keyH = keystrokeKeyHeight(unit);
        int rowW = 3 * unit + 2 * gap;
        drawKey(context, font, x + (rowW - unit) / 2, y, unit, keyH, "W", KS_FILL[0], idle, active, text, alpha);
        int row2Y = y + keyH + gap;
        drawKey(context, font, x, row2Y, unit, keyH, "A", KS_FILL[1], idle, active, text, alpha);
        drawKey(context, font, x + unit + gap, row2Y, unit, keyH, "S", KS_FILL[2], idle, active, text, alpha);
        drawKey(context, font, x + 2 * (unit + gap), row2Y, unit, keyH, "D", KS_FILL[3], idle, active, text, alpha);
        int nextRowY = row2Y + keyH + gap;
        if (keystrokesShowSpace(id)) {
            drawSpaceKey(context, font, x, nextRowY, rowW, keyH, KS_FILL[6], idle, active, text, alpha);
            nextRowY += keyH + gap;
        }
        if (showMouse) {
            int leftW = (rowW - gap) / 2;
            drawKey(context, font, x, nextRowY, leftW, keyH, Integer.toString(autismclient.util.AutismCpsTracker.leftCps()),
                KS_FILL[4], idle, active, text, alpha);
            drawKey(context, font, x + leftW + gap, nextRowY, rowW - leftW - gap, keyH,
                Integer.toString(autismclient.util.AutismCpsTracker.rightCps()), KS_FILL[5], idle, active, text, alpha);
        }
    }

    private static boolean[] keystrokePressed() {
        net.minecraft.client.Options o = MC == null ? null : MC.options;
        return new boolean[] {
            keyHeld(o == null ? null : o.keyUp),
            keyHeld(o == null ? null : o.keyLeft),
            keyHeld(o == null ? null : o.keyDown),
            keyHeld(o == null ? null : o.keyRight),
            keyHeld(o == null ? null : o.keyAttack) || autismclient.util.AutismCpsTracker.leftActiveRecently(120),
            keyHeld(o == null ? null : o.keyUse) || autismclient.util.AutismCpsTracker.rightActiveRecently(120),
            keyHeld(o == null ? null : o.keyJump)
        };
    }

    private static boolean keyHeld(net.minecraft.client.KeyMapping mapping) {
        return mapping != null && autismclient.util.AutismKeyMappingBridge.of(mapping).autism$isActuallyDown();
    }

    private static void drawSpaceKey(GuiGraphicsExtractor context, Font font, int x, int y, int w, int h,
                                     float fill, int idle, int active, int text, float alpha) {
        drawKey(context, font, x, y, w, h, "", fill, idle, active, text, alpha);
        int lineW = Math.max(6, Math.round(w * 0.45f));
        int lineH = Math.max(2, h / 7);
        int lx = x + (w - lineW) / 2;
        int ly = y + (h - lineH) / 2;
        UiText.fill(context, lx, ly, lx + lineW, ly + lineH, alphaColor(text, alpha));
    }

    private static void drawKey(GuiGraphicsExtractor context, Font font, int x, int y, int w, int h, String label,
                                float fill, int idle, int active, int text, float alpha) {
        if (w <= 0 || h <= 0) return;
        UiText.fill(context, x, y, x + w, y + h, alphaColor(idle, alpha));

        if (fill > 0.003f) {
            float eased = fill * fill * (3f - 2f * fill);
            int fillColor = alphaColor(active, alpha * (0.6f + 0.4f * eased));
            if (eased >= 0.999f) {
                UiText.fill(context, x, y, x + w, y + h, fillColor);
            } else {
                double cx = x + w / 2.0;
                double cy = y + h / 2.0;
                double maxR = Math.sqrt((w / 2.0) * (w / 2.0) + (h / 2.0) * (h / 2.0));
                double r = eased * maxR;
                int top = Math.max(y, (int) Math.floor(cy - r));
                int bot = Math.min(y + h, (int) Math.ceil(cy + r));
                for (int row = top; row < bot; row++) {
                    double dy = (row + 0.5) - cy;
                    double span = r * r - dy * dy;
                    if (span <= 0) continue;
                    double hw = Math.sqrt(span);
                    int rx1 = Math.max(x, (int) Math.round(cx - hw));
                    int rx2 = Math.min(x + w, (int) Math.round(cx + hw));
                    if (rx2 > rx1) UiText.fill(context, rx1, row, rx2, row + 1, fillColor);
                }
            }
        }
        outline(context, x, y, w, h, alphaColor(0x66000000, alpha), 1);
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int tw = UiText.width(font, label, fontId, text);
        int fhgt = THEME.fontHeight(UiTone.BODY);
        int tx = x + (w - tw) / 2;

        int ty = y + (h - fhgt) / 2 + 1;
        UiText.draw(context, font, label, fontId, alphaColor(0xCC000000, alpha * 0.55f), tx + 1, ty + 1, false);
        UiText.draw(context, font, label, fontId, alphaColor(text, alpha), tx, ty, false);
    }

    private static void drawCompassBackground(GuiGraphicsExtractor context, int x, int y, int width, int height, float alpha, int base) {
        if (width <= 0 || height <= 0 || alpha <= 0.001f) return;
        int maxAlpha = (int) (((base >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        int rgb = base & 0x00FFFFFF;
        int center = Math.max(1, width / 2);
        for (int i = 0; i < width; i++) {
            double edgeDistance = Math.abs(i + 0.5 - (width / 2.0)) / center;
            double presence = 1.0 - Math.min(1.0, edgeDistance);
            presence = presence * presence * (3.0 - 2.0 * presence);
            int a = (int) Math.round(maxAlpha * presence);
            if (a <= 0) continue;
            UiText.fill(context, x + i, y, x + i + 1, y + height, (a << 24) | rgb);
        }
    }

    private static double wrappedDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static HudLine row(String elementId, String label, String value) {
        HudLine line = new HudLine();
        line.add(label + ": ", color(elementId, "label-color", 0xFFB79E9E));
        line.add(value == null || value.isBlank() ? "N/A" : value, color(elementId, "value-color", 0xFFF3ECE7));
        return line;
    }

    private static HudLine metricsLine() {
        HudLine line = new HudLine();
        int labelColor = color(FPS, "label-color", 0xFFB79E9E);
        int valueColor = color(FPS, "value-color", 0xFFF3ECE7);
        int muted = alphaColor(labelColor, 0.72f);
        line.add("FPS: ", labelColor);
        line.add(Integer.toString(MC.getFps()), valueColor);
        line.add(" ", muted);
        line.add("TPS: ", labelColor);
        line.add(String.format(Locale.ROOT, "%.1f", autismclient.util.macro.ServerTickTracker.getEstimatedTps()), valueColor);
        line.add(" ", muted);
        line.add("Ping: ", labelColor);
        line.add(autismclient.util.macro.ServerTickTracker.getPingMs() + " ms", valueColor);
        return line;
    }

    private static String gameMode() {
        if (MC.gameMode == null) return "N/A";
        try {
            String name = MC.gameMode.getPlayerMode().getName();
            return name == null || name.isBlank() ? "N/A" : AutismRegistryLabels.identifier(name);
        } catch (Throwable ignored) {
            return MC.gameMode.toString();
        }
    }

    private static String durability() {
        ItemStack stack = MC.player == null ? ItemStack.EMPTY : MC.player.getMainHandItem();
        if (stack.isEmpty()) return "Empty";
        if (!stack.isDamageableItem()) return "No durability";
        int left = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int max = Math.max(1, stack.getMaxDamage());
        return String.format(Locale.ROOT, "%d / %d (%d%%)", left, max, Math.round(left * 100.0f / max));
    }

    private static HudLine memoryLine() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long max = rt.maxMemory() >> 20;
        boolean showBar = boolSetting(MEMORY, "show-bar");
        boolean showPct = boolSetting(MEMORY, "show-percent");
        String fmt = setting(MEMORY, "memory-format");
        long pct = max > 0 ? used * 100 / max : 0;
        HudLine line = new HudLine();
        line.add("Memory: ", color(MEMORY, "label-color", 0xFFB79E9E));
        boolean isPctFormat = !"used/max".equals(fmt);
        if (isPctFormat) {
            line.add(pct + "%", color(MEMORY, "value-color", 0xFFF3ECE7));
        } else {
            line.add(used + " / " + max + " MB", color(MEMORY, "value-color", 0xFFF3ECE7));
        }

        if (showPct && !isPctFormat) line.add(" (" + pct + "%)", color(MEMORY, "value-color", 0xFFF3ECE7));
        if (showBar) {
            int barColor = pct > 85 ? 0xFFFF3B3B : pct > 60 ? 0xFFFFD600 : 0xFF3FE87E;
            int barW = Math.min(10, Math.max(1, (int) (pct * 10 / 100)));
            line.add(" [", alphaColor(color(MEMORY, "label-color", 0xFFB79E9E), 0.5f));
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barW; i++) bar.append('|');
            for (int i = barW; i < 10; i++) bar.append('.');
            line.add(bar.toString(), barColor);
            line.add("]", alphaColor(color(MEMORY, "label-color", 0xFFB79E9E), 0.5f));
        }
        return line;
    }

    private static HudLine serverIpLine() {
        HudLine line = new HudLine();
        line.add("IP: ", color(SERVER_IP, "label-color", 0xFFB79E9E));
        if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) {
            String ip = MC.getCurrentServer().ip;
            if (boolSetting(SERVER_IP, "show-port") && MC.getConnection() != null) {
                var addr = MC.getConnection().getConnection().getRemoteAddress();
                if (addr instanceof java.net.InetSocketAddress inet) {
                    ip = ip.contains(":") ? ip : ip + ":" + inet.getPort();
                }
            }
            line.add(ip, color(SERVER_IP, "value-color", 0xFFF3ECE7));
        } else {
            line.add(MC.hasSingleplayerServer() ? "Singleplayer" : "N/A", color(SERVER_IP, "value-color", 0xFFF3ECE7));
        }
        return line;
    }

    private static String serverBrand() {
        if (MC.getConnection() == null) return "N/A";
        try {
            String brand = MC.getConnection().serverBrand();
            return brand != null && !brand.isBlank() ? brand : "Unknown";
        } catch (Throwable ignored) {
            return "Unknown";
        }
    }

    private static final int FPS_BUF_SIZE = 200;
    private static final int[] fpsSamples = new int[FPS_BUF_SIZE];
    private static int fpsSampleIndex;
    private static int fpsSampleCount;

    private static HudLine fpsGraphLine() {

        int fps = MC.getFps();
        fpsSamples[fpsSampleIndex] = fps;
        fpsSampleIndex = (fpsSampleIndex + 1) % FPS_BUF_SIZE;
        if (fpsSampleCount < FPS_BUF_SIZE) fpsSampleCount++;

        int samples = intSetting(FPS_GRAPH, "graph-samples", 100);
        samples = Math.max(10, Math.min(FPS_BUF_SIZE, samples));
        int count = Math.min(samples, fpsSampleCount);
        if (count <= 0) return row(FPS_GRAPH, "FPS", Integer.toString(fps));

        int minFps = fps, maxFps = fps;
        int read = fpsSampleIndex;
        for (int i = 0; i < count; i++) {
            read = (read - 1 + FPS_BUF_SIZE) % FPS_BUF_SIZE;
            int v = fpsSamples[read];
            if (v < minFps) minFps = v;
            if (v > maxFps) maxFps = v;
        }
        int range = Math.max(1, maxFps - minFps);

        HudLine line = new HudLine();
        if (boolSetting(FPS_GRAPH, "show-current-fps")) {
            line.add(Integer.toString(fps), color(FPS_GRAPH, "value-color", 0xFFF3ECE7));
        }

        String[] bars = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        read = fpsSampleIndex;

        int step = Math.max(1, count / 20);
        int shown = 0;
        line.add(" ", alphaColor(color(FPS_GRAPH, "label-color", 0xFFB79E9E), 0.3f));
        for (int i = 0; i < count && shown < 20; i += step) {
            read = (read - 1 + FPS_BUF_SIZE) % FPS_BUF_SIZE;
            int v = fpsSamples[read];
            int barIndex = Math.min(7, (v - minFps) * 8 / range);
            float fraction = (v - minFps) / (float) range;
            int r = Math.round(255 * (1 - fraction));
            int g = Math.round(255 * fraction);
            int barColor = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8);
            line.add(bars[barIndex], barColor);
            shown++;
        }
        return line;
    }

    private static void cpsLines(List<HudLine> lines) {
        boolean showTotal = boolSetting(CPS, "show-total");
        int leftCps = autismclient.util.AutismCpsTracker.leftCps();
        int rightCps = autismclient.util.AutismCpsTracker.rightCps();
        lines.add(row(CPS, "Left", Integer.toString(leftCps)));
        lines.add(row(CPS, "Right", Integer.toString(rightCps)));
        if (showTotal) {
            lines.add(row(CPS, "Total", Integer.toString(leftCps + rightCps)));
        }
    }

    private static String lookingAt() {
        HitResult hit = MC.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return "Nothing";
        if (hit instanceof EntityHitResult entityHit) return entityHit.getEntity().getName().getString();
        if (hit instanceof BlockHitResult blockHit && MC.level != null) {
            BlockPos pos = blockHit.getBlockPos();
            Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
            return AutismRegistryLabels.block(id.toString()) + " " + blockPositionText(pos.getX(), pos.getY(), pos.getZ());
        }
        return AutismRegistryLabels.identifier(hit.getType().name());
    }

    private static String breakingProgress() {
        if (!(MC.hitResult instanceof BlockHitResult) || MC.gameMode == null) return "0%";
        try {
            AutismMultiPlayerGameModeAccessor accessor = (AutismMultiPlayerGameModeAccessor) MC.gameMode;
            if (!accessor.autism$isDestroying()) return "0%";
            return Math.round(clamp(accessor.autism$getDestroyProgress(), 0.0, 1.0) * 100.0) + "%";
        } catch (Throwable ignored) {
            return "0%";
        }
    }

    private static double tpsRollingMin = 20.0;
    private static long tpsMinSetTime;

    private static HudLine tpsLine() {
        double tps = autismclient.util.macro.ServerTickTracker.getEstimatedTps();

        if (tps < tpsRollingMin || System.currentTimeMillis() - tpsMinSetTime > 5000L) {
            tpsRollingMin = tps;
            tpsMinSetTime = System.currentTimeMillis();
        }

        String value = String.format(Locale.ROOT, boolSetting(TPS, "tps-precise") ? "%.2f" : "%.1f", tps);
        int valueColor = color(TPS, "value-color", 0xFFF3ECE7);
        if (boolSetting(TPS, "tps-color-threshold")) {
            valueColor = tps >= 19.0 ? 0xFF3FE87E : tps >= 15.0 ? 0xFFFFD600 : 0xFFFF3B3B;
        }
        HudLine line = new HudLine();
        line.add("TPS: ", color(TPS, "label-color", 0xFFB79E9E));
        line.add(value, valueColor);
        if (boolSetting(TPS, "tps-show-jitter")) {
            line.add(" (" + String.format(Locale.ROOT, "%.1f", tpsRollingMin) + ")", alphaColor(valueColor, 0.65f));
        }
        return line;
    }

    private static HudLine pingLine() {
        int ping = autismclient.util.macro.ServerTickTracker.getPingMs();
        int valueColor = color(PING, "value-color", 0xFFF3ECE7);
        if (boolSetting(PING, "ping-color-threshold")) {
            valueColor = ping <= 50 ? 0xFF3FE87E : ping <= 150 ? 0xFFFFD600 : 0xFFFF3B3B;
        }
        HudLine line = new HudLine();
        line.add("Ping: ", color(PING, "label-color", 0xFFB79E9E));
        line.add(ping + " ms", valueColor);
        if (boolSetting(PING, "ping-show-jitter")) {

            int old = lastPingValue;
            lastPingValue = ping;
            int jitter = old > 0 ? Math.abs(ping - old) : 0;
            if (jitter > 0) line.add(" ±" + jitter, alphaColor(valueColor, 0.65f));
        }
        return line;
    }

    private static int lastPingValue;

    private static HudLine durabilityLine() {
        ItemStack stack = MC.player == null ? ItemStack.EMPTY : MC.player.getMainHandItem();
        HudLine line = new HudLine();
        if (boolSetting(DURABILITY, "show-item-name") && !stack.isEmpty()) {
            line.add(stack.getHoverName().getString() + ": ", color(DURABILITY, "label-color", 0xFFB79E9E));
        } else {
            line.add("Durability: ", color(DURABILITY, "label-color", 0xFFB79E9E));
        }
        if (stack.isEmpty()) {
            line.add("Empty", color(DURABILITY, "value-color", 0xFFF3ECE7));
        } else if (!stack.isDamageableItem()) {
            line.add("No durability", color(DURABILITY, "value-color", 0xFFF3ECE7));
        } else {
            int left = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            int max = Math.max(1, stack.getMaxDamage());
            int pct = Math.round(left * 100.0f / max);
            int valColor = color(DURABILITY, "value-color", 0xFFF3ECE7);
            if (boolSetting(DURABILITY, "low-durability-warn")) {
                valColor = pct > 50 ? 0xFF3FE87E : pct > 20 ? 0xFFFFD600 : 0xFFFF3B3B;
            }
            line.add(String.format(Locale.ROOT, "%d / %d (%d%%)", left, max, pct), valColor);
        }
        return line;
    }

    private static HudLine lookingAtLine() {
        HudLine line = new HudLine();
        HitResult hit = MC.hitResult;
        line.add("Looking: ", color(LOOKING_AT, "label-color", 0xFFB79E9E));
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            line.add("Nothing", color(LOOKING_AT, "value-color", 0xFFF3ECE7));
            return line;
        }
        String name;
        if (hit instanceof EntityHitResult entityHit) {
            name = entityHit.getEntity().getName().getString();
        } else if (hit instanceof BlockHitResult blockHit && MC.level != null) {
            BlockState state = MC.level.getBlockState(blockHit.getBlockPos());
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (boolSetting(LOOKING_AT, "show-block-id")) {
                name = id.toString();
            } else {
                name = AutismRegistryLabels.block(id.toString());
            }
            if (boolSetting(LOOKING_AT, "show-distance")) {
                double dist = Math.sqrt(MC.player.distanceToSqr(blockHit.getLocation()));
                name += " " + String.format(Locale.ROOT, "%.1fm", dist);
            }
        } else {
            name = AutismRegistryLabels.identifier(hit.getType().name());
        }
        line.add(name, color(LOOKING_AT, "value-color", 0xFFF3ECE7));
        return line;
    }

    private static HudLine breakingLine() {
        HudLine line = new HudLine();
        if (!(MC.hitResult instanceof BlockHitResult) || MC.gameMode == null || MC.level == null) {
            line.add("Breaking: 0%", color(BREAKING_PROGRESS, "label-color", 0xFFB79E9E));
            return line;
        }
        double progress = 0.0;
        try {
            AutismMultiPlayerGameModeAccessor accessor = (AutismMultiPlayerGameModeAccessor) MC.gameMode;
            if (accessor.autism$isDestroying()) progress = accessor.autism$getDestroyProgress();
            progress = Math.max(0.0, Math.min(1.0, progress));
        } catch (Throwable ignored) {}
        int pct = (int) Math.round(progress * 100.0);
        if (boolSetting(BREAKING_PROGRESS, "show-block-name")) {
            BlockPos pos = ((BlockHitResult) MC.hitResult).getBlockPos();
            String blockName = AutismRegistryLabels.block(BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock()).toString());
            line.add(blockName + ": ", color(BREAKING_PROGRESS, "label-color", 0xFFB79E9E));
        } else {
            line.add("Breaking: ", color(BREAKING_PROGRESS, "label-color", 0xFFB79E9E));
        }
        line.add(pct + "%", pct >= 80 ? 0xFF3FE87E : pct >= 40 ? 0xFFFFD600 : color(BREAKING_PROGRESS, "value-color", 0xFFF3ECE7));
        return line;
    }

    private static HudLine serverLine() {
        HudLine line = new HudLine();
        line.add("Server: ", color(SERVER, "label-color", 0xFFB79E9E));
        if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) {
            line.add(MC.getCurrentServer().ip, color(SERVER, "value-color", 0xFFF3ECE7));
        } else if (MC.hasSingleplayerServer()) {
            line.add("Singleplayer", color(SERVER, "value-color", 0xFFF3ECE7));
        } else {
            line.add("N/A", color(SERVER, "value-color", 0xFFF3ECE7));
        }
        return line;
    }

    private static HudLine weatherLine() {
        String w = weather();
        HudLine line = new HudLine();
        line.add("Weather: ", color(WEATHER, "label-color", 0xFFB79E9E));
        int wColor = switch (w) {
            case "Thunder" -> 0xFFFF3B3B;
            case "Rain" -> 0xFF66D9FF;
            default -> color(WEATHER, "value-color", 0xFFF3ECE7);
        };
        line.add(w, wColor);
        if (boolSetting(WEATHER, "show-temperature") && MC.level != null) {
            var biome = MC.level.getBiome(MC.player.blockPosition());
            float temp = biome.value().getBaseTemperature();
            line.add(" " + String.format(Locale.ROOT, "%.1f°C", temp), alphaColor(wColor, 0.65f));
        }
        return line;
    }

    private static HudLine biomeLine() {
        String b = biome();
        HudLine line = new HudLine();
        line.add("Biome: ", color(BIOME, "label-color", 0xFFB79E9E));
        line.add(b, color(BIOME, "value-color", 0xFFF3ECE7));
        return line;
    }

    private static HudLine worldTimeLine() {
        String fmt = setting(WORLD_TIME, "world-time-format");
        HudLine line = new HudLine();
        line.add("Time: ", color(WORLD_TIME, "label-color", 0xFFB79E9E));
        if ("ticks".equals(fmt) && MC.level != null) {
            line.add(Long.toString(currentDayTime() % 24000L), color(WORLD_TIME, "value-color", 0xFFF3ECE7));
        } else {
            line.add(worldTime(), color(WORLD_TIME, "value-color", 0xFFF3ECE7));
        }
        if (boolSetting(WORLD_TIME, "show-day") && MC.level != null) {
            long day = currentDayTime() / 24000L;
            line.add(" Day " + day, alphaColor(color(WORLD_TIME, "value-color", 0xFFF3ECE7), 0.65f));
        }
        return line;
    }

    private static HudLine realTimeLine() {
        String fmt = setting(REAL_TIME, "real-time-format");
        java.time.format.DateTimeFormatter formatter = "24h".equals(fmt)
            ? java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            : java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        HudLine line = new HudLine();
        line.add("Time: ", color(REAL_TIME, "label-color", 0xFFB79E9E));
        line.add(LocalTime.now().format(formatter), color(REAL_TIME, "value-color", 0xFFF3ECE7));
        if (boolSetting(REAL_TIME, "show-date")) {
            line.add(" " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")),
                alphaColor(color(REAL_TIME, "value-color", 0xFFF3ECE7), 0.65f));
        }
        return line;
    }

    private static String serverName() {
        if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) return MC.getCurrentServer().ip;
        return MC.hasSingleplayerServer() ? "Singleplayer" : "N/A";
    }

    private static String weather() {
        if (MC.level == null) return "N/A";
        if (MC.level.isThundering()) return "Thunder";
        if (MC.level.isRaining()) return "Rain";
        return "Clear";
    }

    private static String biome() {
        if (MC.level == null || MC.player == null) return "N/A";
        try {
            return MC.level.getBiome(MC.player.blockPosition()).unwrapKey()
                .map(key -> AutismRegistryLabels.identifier(key.identifier().toString()))
                .orElse("Unknown");
        } catch (Throwable ignored) {
            return "Unknown";
        }
    }

    private static String worldTime() {
        if (MC.level == null) return "N/A";
        long time = currentDayTime() % 24000L;
        long hours = (time / 1000L + 6L) % 24L;
        long minutes = (time % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static volatile boolean dayTimeResolved;
    private static Method dayTimeGetter;
    private static Method dayTimeLevelData;

    private static long currentDayTime() {
        if (MC.level == null) return 0L;
        if (!dayTimeResolved) resolveDayTimeAccessor();
        Method getter = dayTimeGetter;
        if (getter != null) {
            try {
                Object target = dayTimeLevelData != null ? dayTimeLevelData.invoke(MC.level) : MC.level;
                Object value = target == null ? null : getter.invoke(target);
                if (value instanceof Number number) return number.longValue();
            } catch (Throwable ignored) {  }
        }
        return MC.level.getGameTime();
    }

    private static synchronized void resolveDayTimeAccessor() {
        if (dayTimeResolved) return;
        try {
            Method direct = findGetter(MC.level.getClass(), "getDayTime", "dayTime");
            if (direct != null) {
                dayTimeGetter = direct;
            } else {
                Method levelData = MC.level.getClass().getMethod("getLevelData");
                Object data = levelData.invoke(MC.level);
                Method fromData = data == null ? null : findGetter(data.getClass(), "getDayTime", "dayTime");
                if (fromData != null) {
                    dayTimeLevelData = levelData;
                    dayTimeGetter = fromData;
                }
            }
        } catch (Throwable ignored) {  }
        dayTimeResolved = true;
    }

    private static Method findGetter(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static String directionName() {
        if (MC.player == null) return "N";
        return switch (MC.player.getDirection()) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            case UP -> "Up";
            case DOWN -> "Down";
        };
    }

    private static HudLine itemCounterLine() {
        Item target = itemCounterTarget();
        HudLine line = new HudLine();
        if (boolSetting(ITEM_COUNTER, "item-show-name")) {
            line.add(itemCounterLabel(target) + ": ", color(ITEM_COUNTER, "label-color", 0xFFB79E9E));
        }
        line.add(Integer.toString(countItemInInventory(target)), color(ITEM_COUNTER, "value-color", 0xFFF3ECE7));
        return line;
    }

    private static Item itemCounterTarget() {
        if (MC.player == null) return null;
        if (boolSetting(ITEM_COUNTER, "item-count-held")) {
            ItemStack held = MC.player.getMainHandItem();
            return held.isEmpty() ? null : held.getItem();
        }
        String raw = setting(ITEM_COUNTER, "item-id");
        if (raw == null || raw.isBlank()) return null;
        try {
            Identifier id = Identifier.parse(raw.contains(":") ? raw : "minecraft:" + raw);
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String itemCounterLabel(Item target) {
        if (target == null) return boolSetting(ITEM_COUNTER, "item-count-held") ? "Hand" : "Items";
        return AutismRegistryLabels.item(BuiltInRegistries.ITEM.getKey(target).toString());
    }

    private static int countItemInInventory(Item target) {
        if (MC.player == null || target == null) return 0;
        int count = 0;
        for (int i = 0; i < MC.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == target) count += stack.getCount();
        }
        return count;
    }

    private static String blockPositionText(double x, double y, double z) {
        return String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static void potionLines(List<HudLine> lines) {
        if (MC.player == null || MC.player.getActiveEffects().isEmpty()) {
            lines.add(row(POTION_TIMERS, "Effects", "None"));
            return;
        }
        for (MobEffectInstance effect : MC.player.getActiveEffects()) {
            String name = Component.translatable(effect.getDescriptionId()).getString();
            int amplifier = effect.getAmplifier() + 1;
            if (amplifier > 1) name += " " + roman(amplifier);
            int seconds = Math.max(0, effect.getDuration() / 20);
            lines.add(row(POTION_TIMERS, name, String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
        }
    }

    private static String roman(int value) {
        return switch (Math.max(1, Math.min(10, value))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    private static int lineWidth(Font font, HudLine line) {

        int[] widths = new int[line.segments.size()];
        int width = 0;
        for (int i = 0; i < line.segments.size(); i++) {
            HudSegment segment = line.segments.get(i);
            int w = UiText.width(font, segment.text, THEME.fontFor(UiTone.BODY), segment.color);
            widths[i] = w;
            width += w;
        }
        line.segmentWidths = widths;
        return width;
    }

    private static int lineHeight(String id) {
        return THEME.fontHeight(UiTone.BODY) + lineGap(id);
    }

    private static int activeModuleRowHeight(String id) {
        return THEME.fontHeight(UiTone.BODY) + verticalPadding(id) * 2;
    }

    private static int activeModuleStairSnap(String id) {
        return clamp(intSetting(id, "stair-snap", 2), 0, 24);
    }

    private static int lineGap(String id) {
        return clamp(intSetting(id, "line-gap", 0), 0, 10);
    }

    private static void migrateOldHud(AutismConfig config) {
        ensureStateMap(config);
        AutismConfig.ModuleState old = config.modules == null ? null : config.modules.get("hud");
        Map<String, String> settings = old == null || old.settings == null ? Map.of() : old.settings;
        int x = parseInt(settings.get("x"), 8);
        int y = parseInt(settings.get("y"), 8);
        boolean modules = parseBool(settings.get("modules"), true);
        boolean metrics = parseBool(settings.get("metrics"), true);
        boolean coords = parseBool(settings.get("coords"), true);
        putMigrated(config, ACTIVE_MODULES, modules, x, y);
        putMigrated(config, TPS, metrics, x, y + 24);
        putMigrated(config, COORDINATES, coords, x, y + 44);
        putMigrated(config, NETHER_COORDS, coords, x, y + 64);
        config.hudLayoutMigrated = true;
        config.save();
    }

    private static void putMigrated(AutismConfig config, String id, boolean enabled, int x, int y) {
        AutismConfig.HudElementState state = config.hudElements.computeIfAbsent(id, ignored -> defaultState(id));
        state.enabled = enabled;
        state.x = x;
        state.y = y;
        if (state.settings == null) state.settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : defaultSettings(id).entrySet()) state.settings.putIfAbsent(entry.getKey(), entry.getValue());
    }

    private static void normalizeDefaultHudStack(AutismConfig config) {
        if (config == null || config.hudElements == null) return;
        int rowH = defaultHudRowStep();
        int oldRowH = THEME.fontHeight(UiTone.BODY) + 2;
        int oldLogoBottom = logoHeight(180) + 1;
        int oldSpacedLogoBottom = logoHeight(180) + 6;
        int newLogoBottom = defaultLogoElementHeight();
        boolean changed = false;
        AutismConfig.HudElementState fps = config.hudElements.get(FPS);
        AutismConfig.HudElementState tps = config.hudElements.get(TPS);
        AutismConfig.HudElementState ping = config.hudElements.get(PING);
        AutismConfig.HudElementState speed = config.hudElements.get(SPEED);
        AutismConfig.HudElementState compass = config.hudElements.get(COMPASS);
        AutismConfig.HudElementState coordinates = config.hudElements.get(COORDINATES);
        AutismConfig.HudElementState nether = config.hudElements.get(NETHER_COORDS);
        AutismConfig.HudElementState rotation = config.hudElements.get(ROTATION);
        AutismConfig.HudElementState antiVanish = config.hudElements.get(ANTI_VANISH);
        AutismConfig.HudElementState logo = config.hudElements.get(WATERMARK);
        if (isTopLeftAt(fps, 0, oldLogoBottom) || isTopLeftAt(fps, 0, oldSpacedLogoBottom)) {
            fps.y = newLogoBottom;
            changed = true;
        }
        if (isTopLeftAt(tps, 0, oldLogoBottom + rowH) || isTopLeftAt(tps, 0, oldSpacedLogoBottom + rowH)) {
            tps.enabled = false;
            tps.y = newLogoBottom;
            changed = true;
        }
        if (isTopLeftAt(ping, 0, oldLogoBottom + rowH * 2) || isTopLeftAt(ping, 0, oldSpacedLogoBottom + rowH * 2)) {
            ping.enabled = false;
            ping.y = newLogoBottom;
            changed = true;
        }
        if (isTopLeftAt(speed, 0, oldLogoBottom + rowH * 3) || isTopLeftAt(speed, 0, oldSpacedLogoBottom + rowH)) {
            speed.y = newLogoBottom + rowH;
            changed = true;
        }
        if (isTopLeftAt(compass, 0, oldLogoBottom + rowH * 5) || isTopLeftAt(compass, 0, oldSpacedLogoBottom + rowH * 2)) {
            compass.y = newLogoBottom + rowH * 2;
            changed = true;
        }
        if (isDefaultAntiVanishPlacement(antiVanish)) {
            changed |= snapHudState(antiVanish, "TOP_LEFT", 0, newLogoBottom + rowH * 2);
        }
        int rightStackX = -HUD_SAFE_ZONE_X;
        int rightStackBottom = -HUD_SAFE_ZONE_Y;
        if (isLegacyRightCornerStack(coordinates, nether, rotation)) {
            changed |= snapHudState(coordinates, "BOTTOM_RIGHT", rightStackX, rightStackBottom - rowH * 2);
            changed |= snapHudState(nether, "BOTTOM_RIGHT", rightStackX, rightStackBottom - rowH);
            changed |= snapHudState(rotation, "BOTTOM_RIGHT", rightStackX, rightStackBottom);
        }
        if (isBottomRightStackAt(coordinates, nether, rotation, 0, oldRowH)
            || isBottomRightStackAt(coordinates, nether, rotation, 0, rowH)) {
            changed |= snapHudState(coordinates, "BOTTOM_RIGHT", rightStackX, rightStackBottom - rowH * 2);
            changed |= snapHudState(nether, "BOTTOM_RIGHT", rightStackX, rightStackBottom - rowH);
            changed |= snapHudState(rotation, "BOTTOM_RIGHT", rightStackX, rightStackBottom);
        }
        if (isLegacyCompass(compass, oldLogoBottom, oldSpacedLogoBottom, newLogoBottom, rowH)) {
            changed |= snapHudState(compass, "TOP_CENTER", 0, 2);
        }
        if (compass != null && compass.settings != null && "3".equals(compass.settings.get(KEY_PADDING))) {
            compass.settings.put(KEY_PADDING, DEFAULT_PADDING);
            changed = true;
        }
        if (compass != null && compass.settings != null && "86".equals(compass.settings.get(KEY_COMPASS_WIDTH))) {
            compass.settings.put(KEY_COMPASS_WIDTH, DEFAULT_COMPASS_WIDTH);
            changed = true;
        }
        if (compass != null && compass.settings != null && !compass.settings.containsKey("compass-style-migrated")) {
            if ("true".equalsIgnoreCase(compass.settings.get(KEY_OUTLINE))) {
                compass.settings.put(KEY_OUTLINE, DEFAULT_OUTLINE);
                changed = true;
            }
            compass.settings.put("compass-style-migrated", "true");
            changed = true;
        }
        if (logo != null && logo.settings != null) {
            if ("false".equalsIgnoreCase(logo.settings.get(KEY_BACKGROUND))) {
                logo.settings.put(KEY_BACKGROUND, DEFAULT_BACKGROUND);
                changed = true;
            }
            if (!"1".equals(logo.settings.get(KEY_PADDING))) {
                logo.settings.put(KEY_PADDING, DEFAULT_PADDING);
                changed = true;
            }
        }
        for (AutismConfig.HudElementState element : config.hudElements.values()) {
            if (element == null || element.settings == null) continue;
            String outlineColor = element.settings.get(KEY_OUTLINE_COLOR);
            if ("FF8F1F24".equalsIgnoreCase(outlineColor) || "FFD3424D".equalsIgnoreCase(outlineColor)) {
                element.settings.put(KEY_OUTLINE_COLOR, DEFAULT_OUTLINE_COLOR);
                changed = true;
            }

            if ("5".equals(element.settings.get(KEY_STAIR_SNAP))) {
                element.settings.put(KEY_STAIR_SNAP, DEFAULT_STAIR_SNAP);
                changed = true;
            }
            if (!element.settings.containsKey(KEY_VERTICAL_PADDING)) {
                element.settings.put(KEY_VERTICAL_PADDING, DEFAULT_VERTICAL_PADDING);
                changed = true;
            }
        }
        if (changed) config.save();
    }

    private static boolean isTopLeftAt(AutismConfig.HudElementState state, int x, int y) {
        return state != null && "TOP_LEFT".equals(state.anchor) && state.x == x && state.y == y;
    }

    private static boolean isDefaultAntiVanishPlacement(AutismConfig.HudElementState state) {
        return isAnchoredAt(state, "TOP_RIGHT", 0, 72) || isAnchoredAt(state, "TOP_LEFT", 0, 72);
    }

    private static boolean isLegacyRightCornerStack(
        AutismConfig.HudElementState coordinates,
        AutismConfig.HudElementState nether,
        AutismConfig.HudElementState rotation
    ) {
        int legacy = 0;
        if (isLegacyRightCornerState(coordinates)) legacy++;
        if (isLegacyRightCornerState(nether)) legacy++;
        if (isLegacyRightCornerState(rotation)) legacy++;
        return legacy >= 2;
    }

    private static boolean isLegacyRightCornerState(AutismConfig.HudElementState state) {
        if (state == null) return false;
        String anchor = state.anchor == null ? "" : state.anchor.toUpperCase(Locale.ROOT);
        if (anchor.contains("RIGHT") && (state.x > 0 || state.y > 0)) return true;
        return "TOP_LEFT".equals(anchor) && state.x >= 0 && state.x <= 16 && state.y >= 0 && state.y <= 140;
    }

    private static boolean isBottomRightStackAt(
        AutismConfig.HudElementState coordinates,
        AutismConfig.HudElementState nether,
        AutismConfig.HudElementState rotation,
        int bottomOffset,
        int rowH
    ) {
        return isAnchoredAt(coordinates, "BOTTOM_RIGHT", bottomOffset, -rowH * 2)
            && isAnchoredAt(nether, "BOTTOM_RIGHT", bottomOffset, -rowH)
            && isAnchoredAt(rotation, "BOTTOM_RIGHT", bottomOffset, 0);
    }

    private static boolean isAnchoredAt(AutismConfig.HudElementState state, String anchor, int x, int y) {
        return state != null && anchor.equals(state.anchor) && state.x == x && state.y == y;
    }

    private static boolean isLegacyCompass(AutismConfig.HudElementState state, int oldLogoBottom, int oldSpacedLogoBottom, int newLogoBottom, int rowH) {
        if (state == null) return false;
        String anchor = state.anchor == null ? "" : state.anchor.toUpperCase(Locale.ROOT);
        if ("TOP_CENTER".equals(anchor) && state.x == 0 && state.y == 2) return false;
        if (state.x != 0 && Math.abs(state.x) > 16) return false;
        return "TOP_LEFT".equals(anchor)
            || "TOP_CENTER".equals(anchor) && (state.y == newLogoBottom + rowH * 2
                || state.y == oldLogoBottom + rowH * 5
                || state.y == oldSpacedLogoBottom + rowH * 2);
    }

    private static boolean snapHudState(AutismConfig.HudElementState state, String anchor, int x, int y) {
        if (state == null) return false;
        boolean changed = !anchor.equals(state.anchor) || state.x != x || state.y != y;
        if (!changed) return false;
        state.anchor = anchor;
        state.x = x;
        state.y = y;
        return true;
    }

    private static AutismConfig.HudElementState defaultState(String id) {
        AutismConfig.HudElementState state = new AutismConfig.HudElementState();
        state.enabled = defaultEnabled(id);
        state.scale = 1.0;
        state.anchor = defaultAnchor(id);
        int[] position = defaultPosition(id);
        state.x = position[0];
        state.y = position[1];
        state.settings = new LinkedHashMap<>(defaultSettings(id));
        return state;
    }

    private static String defaultAnchor(String id) {
        autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
        if (provider != null) return provider.defaultAnchor();
        return switch (id) {
            case ACTIVE_MODULES -> "TOP_RIGHT";
            case COORDINATES, NETHER_COORDS, ROTATION -> "BOTTOM_RIGHT";
            case COMPASS -> "TOP_CENTER";
            case KEYSTROKES -> "MIDDLE_LEFT";
            default -> "TOP_LEFT";
        };
    }

    private static int[] defaultPosition(String id) {
        autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
        if (provider != null) return new int[] {provider.defaultX(), provider.defaultY()};
        int rowH = defaultHudRowStep();
        int logoBottom = defaultLogoElementHeight();
        return switch (id) {
            case WATERMARK -> new int[] {0, 0};
            case FPS -> new int[] {0, logoBottom};
            case TPS, PING -> new int[] {0, logoBottom};
            case SPEED -> new int[] {0, logoBottom + rowH};
            case ACTIVE_MODULES -> new int[] {0, 0};
            case ANTI_VANISH -> new int[] {0, logoBottom + rowH * 2};

            case ROTATION -> new int[] {-HUD_SAFE_ZONE_X, -HUD_SAFE_ZONE_Y};
            case NETHER_COORDS -> new int[] {-HUD_SAFE_ZONE_X, -HUD_SAFE_ZONE_Y - rowH};
            case COORDINATES -> new int[] {-HUD_SAFE_ZONE_X, -HUD_SAFE_ZONE_Y - rowH * 2};
            case ARMOR -> new int[] {0, logoBottom + rowH * 2};
            case COMPASS -> new int[] {0, 2};
            case POTION_TIMERS -> new int[] {0, logoBottom + rowH * 4};
            case KEYSTROKES -> new int[] {4, 0};
            default -> new int[] {0, 0};
        };
    }

    private static boolean defaultEnabled(String id) {
        autismclient.api.hud.HudElementProvider provider = autismclient.api.hud.HudElements.get(id);
        if (provider != null) return provider.defaultEnabled();
        return ACTIVE_MODULES.equals(id)
            || WATERMARK.equals(id)
            || FPS.equals(id)
            || SPEED.equals(id)
            || COORDINATES.equals(id)
            || NETHER_COORDS.equals(id)
            || ROTATION.equals(id)
            || COMPASS.equals(id)
            || ANTI_VANISH.equals(id)
            || KEYSTROKES.equals(id);
    }

    private static Map<String, String> defaultSettings(String id) {
        Map<String, String> cached = DEFAULT_SETTINGS_CACHE.get(id);
        if (cached != null) return cached;
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("shadow", "true");
        settings.put("use-custom-colors", "false");
        settings.put(KEY_BACKGROUND, DEFAULT_BACKGROUND);
        settings.put("background-color", "32191919");
        settings.put("text-color", "FFF3ECE7");
        settings.put("label-color", "FFB79E9E");
        settings.put("value-color", "FFF3ECE7");
        settings.put("accent-color", "FFFF3B3B");
        settings.put("alignment", "Left");
        settings.put(KEY_OUTLINE, DEFAULT_OUTLINE);
        settings.put(KEY_OUTLINE_COLOR, DEFAULT_OUTLINE_COLOR);
        settings.put("outline-width", "1");
        settings.put(KEY_PADDING, DEFAULT_PADDING);
        settings.put(KEY_VERTICAL_PADDING, DEFAULT_VERTICAL_PADDING);
        settings.put("line-gap", "0");
        if (WATERMARK.equals(id)) {
            settings.put("shadow", "false");
            settings.put(KEY_BACKGROUND, DEFAULT_BACKGROUND);
            settings.put(KEY_OUTLINE, DEFAULT_OUTLINE);
            settings.put(KEY_PADDING, DEFAULT_PADDING);
            settings.put(KEY_LOGO_RIGHT_PADDING, "3");
            settings.put(KEY_LOGO_WIDTH, "180");
        }
        if (COMPASS.equals(id)) {
            settings.put(KEY_PADDING, DEFAULT_PADDING);
            settings.put(KEY_COMPASS_WIDTH, DEFAULT_COMPASS_WIDTH);
            settings.put(KEY_OUTLINE, DEFAULT_OUTLINE);
        }
        if (ARMOR.equals(id) || INVENTORY.equals(id)) {

            settings.put(KEY_OUTLINE, "true");
            settings.put("slot-style", "Flat");
        }
        if (TPS.equals(id)) {
            settings.put("tps-precise", "false");
            settings.put("tps-color-threshold", "true");
            settings.put("tps-show-jitter", "false");
        }
        if (PING.equals(id)) {
            settings.put("ping-color-threshold", "true");
            settings.put("ping-show-jitter", "true");
        }
        if (CPS.equals(id)) {
            settings.put("show-total", "false");
        }
        if (DURABILITY.equals(id)) {
            settings.put("show-item-name", "false");
            settings.put("low-durability-warn", "true");
        }
        if (LOOKING_AT.equals(id)) {
            settings.put("show-distance", "false");
            settings.put("show-block-id", "false");
        }
        if (BREAKING_PROGRESS.equals(id)) {
            settings.put("show-block-name", "false");
        }
        if (WEATHER.equals(id)) {
            settings.put("show-temperature", "false");
        }
        if (WORLD_TIME.equals(id)) {
            settings.put("world-time-format", "24h");
            settings.put("show-day", "false");
        }
        if (REAL_TIME.equals(id)) {
            settings.put("real-time-format", "12h");
            settings.put("show-date", "false");
        }
        if (KEYSTROKES.equals(id)) {
            settings.put(KEY_BACKGROUND, "false");
            settings.put(KEY_PADDING, "0");
            settings.put(KEY_KS_ACTIVE_COLOR, "FFFF3B3B");
            settings.put(KEY_KS_IDLE_COLOR, "B4101014");
            settings.put(KEY_KS_TEXT_COLOR, "FFFFFFFF");
            settings.put(KEY_KS_SHOW_SPACE, "true");
            settings.put(KEY_KS_SHOW_MOUSE, "true");
            settings.put(KEY_KS_SIZE, "18");
        }
        if (ACTIVE_MODULES.equals(id) || COORDINATES.equals(id) || NETHER_COORDS.equals(id) || ROTATION.equals(id)) {
            settings.put("alignment", "Right");
        }
        if (ACTIVE_MODULES.equals(id)) {

            settings.put("use-custom-colors", "true");
            settings.put("module-info", "true");
            settings.put("show-keybind", "false");
            settings.put("sort", "Width");
            settings.put("hidden-modules", "hud");
            settings.put("color-mode", "Rainbow");
            settings.put("flat-color", "FFFF3B3B");
            settings.put("gradient-start-color", "FFFF3B3B");
            settings.put("gradient-end-color", "FFFFD6D6");
            settings.put("module-info-color", "FFB79E9E");
            settings.put("rainbow-speed", "1.0");
            settings.put("rainbow-spread", "0.035");
            settings.put("rainbow-saturation", "0.35");
            settings.put("rainbow-brightness", "1.0");
            settings.put(KEY_STAIR_SNAP, DEFAULT_STAIR_SNAP);
        }
        if (ITEM_COUNTER.equals(id)) {
            settings.put("item-id", "minecraft:totem_of_undying");
            settings.put("item-show-name", "true");
            settings.put("item-count-held", "false");
        }
        if (MEMORY.equals(id)) {
            settings.put("show-bar", "true");
            settings.put("show-percent", "true");
            settings.put("memory-format", "used/max");
        }
        if (SERVER_IP.equals(id)) {
            settings.put("show-port", "true");
        }
        if (FPS_GRAPH.equals(id)) {
            settings.put("graph-samples", "100");
            settings.put("show-current-fps", "true");
        }
        Map<String, String> immutable = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        DEFAULT_SETTINGS_CACHE.put(id, immutable);
        return immutable;
    }

    private static int padding(String id) {
        return clamp(intSetting(id, KEY_PADDING, 0), 0, 16);
    }

    private static int verticalPadding(String id) {
        return clamp(intSetting(id, KEY_VERTICAL_PADDING, 0), 0, 16);
    }

    private static int defaultHudRowStep() {
        return THEME.fontHeight(UiTone.BODY);
    }

    private static int logoWidth(String id) {
        return clamp(intSetting(id, KEY_LOGO_WIDTH, 180), 48, 420);
    }

    private static int logoHeight(int width) {
        return Math.max(1, Math.round(width * (HUD_LOGO_DISPLAY_HEIGHT / (float) HUD_LOGO_DISPLAY_WIDTH)));
    }

    private static int defaultLogoElementHeight() {
        return logoHeight(180) + 2;
    }

    private static int logoRightPadding(String id) {
        return clamp(intSetting(id, KEY_LOGO_RIGHT_PADDING, 3), 0, 16);
    }

    private static int compassWidth(String id) {
        return clamp(intSetting(id, KEY_COMPASS_WIDTH, 112), 72, 200);
    }

    private static int anchorX(String anchor, int x, int width) {
        String normalized = anchor == null ? "" : anchor.toUpperCase(Locale.ROOT);
        if (normalized.contains("RIGHT")) {
            int sw = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
            return sw + x - width;
        }
        if (normalized.contains("CENTER")) {
            int sw = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
            return (sw - width) / 2 + x;
        }
        return x;
    }

    private static int anchorY(String anchor, int y, int height) {
        String normalized = anchor == null ? "" : anchor.toUpperCase(Locale.ROOT);
        if (normalized.contains("BOTTOM")) {
            int sh = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
            return sh + y - height;
        }
        if (normalized.contains("MIDDLE")) {
            int sh = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
            return (sh - height) / 2 + y;
        }
        return y;
    }

    private static int safeContentX(String id, int x, int width) {
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        return clamp(x, safeZoneXFor(id), maxSafeX(id, screenW, width));
    }

    private static int safeContentY(String id, int y, int height) {
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        return clamp(y, safeZoneYFor(id), maxSafeY(id, screenH, height));
    }

    private static int safeZoneXFor(String id) {
        if (WATERMARK.equals(id)) return 0;
        return HUD_SAFE_ZONE_X;
    }

    private static int safeZoneYFor(String id) {

        if (WATERMARK.equals(id) || ACTIVE_MODULES.equals(id)) return 0;
        return HUD_SAFE_ZONE_Y;
    }

    private static int maxSafeX(String id, int screenW, int width) {
        int safe = safeZoneXFor(id);
        return Math.max(safe, screenW - Math.max(0, width) - safe);
    }

    private static int maxSafeY(String id, int screenH, int height) {
        int safe = safeZoneYFor(id);
        return Math.max(safe, screenH - Math.max(0, height) - safe);
    }

    private static int color(String key, int fallback) {
        return color(ACTIVE_MODULES, key, fallback);
    }

    private static int color(String id, String key, int fallback) {

        if (!boolSetting(id, "use-custom-colors")) {
            int themed = parseColor(defaultSetting(id, key), fallback);
            return AutismTheme.recolor(themed, channelForColorKey(key));
        }
        return parseColor(setting(id, key), fallback);
    }

    private static AutismTheme.Channel channelForColorKey(String key) {
        if (key == null) return AutismTheme.Channel.ACCENT;
        return switch (key) {
            case "outline-color" -> AutismTheme.Channel.OUTLINE;
            case "label-color", "value-color", "text-color", "module-info-color", "keystroke-text-color" -> AutismTheme.Channel.TEXT;
            case "background-color", "keystroke-idle-color" -> AutismTheme.Channel.BACKDROP;
            default -> AutismTheme.Channel.ACCENT;
        };
    }

    private static int editorWash(boolean selected) {
        return AutismTheme.recolor(selected ? 0x382A1116 : 0x22131418, AutismTheme.Channel.BACKDROP);
    }

    private static int alphaColor(int color, float alpha) {
        if (alpha >= 0.999f) return color;
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int lerpColor(int from, int to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int a0 = (from >>> 24) & 255;
        int r0 = (from >>> 16) & 255;
        int g0 = (from >>> 8) & 255;
        int b0 = from & 255;
        int a = (int) Math.round(a0 + (((to >>> 24) & 255) - a0) * clamped);
        int r = (int) Math.round(r0 + (((to >>> 16) & 255) - r0) * clamped);
        int g = (int) Math.round(g0 + (((to >>> 8) & 255) - g0) * clamped);
        int b = (int) Math.round(b0 + ((to & 255) - b0) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int softenColor(int color, int base, double amount) {
        return lerpColor(color, base, amount);
    }

    private static void ensureStateMap(AutismConfig config) {
        if (config.hudElements == null) config.hudElements = new LinkedHashMap<>();
    }

    private static boolean bool(Module module, String id, boolean fallback) {
        ModuleOptionAccess option = new ModuleOptionAccess(module, id);
        if (!option.exists) return fallback;
        return Boolean.parseBoolean(option.value);
    }

    private static boolean parseBool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() == 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hover(int mouseX, int mouseY, ElementBounds bounds) {
        return hover(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private static boolean hover(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static boolean isRightAnchor(String anchor) {
        return anchor != null && anchor.toUpperCase(Locale.ROOT).contains("RIGHT");
    }

    private static boolean combinedMetricsRowOwns(String id) {
        return (TPS.equals(id) || PING.equals(id)) && state(FPS).enabled;
    }

    private static int stableMetricsWidth(int measuredWidth) {
        long now = System.currentTimeMillis();
        int width = Math.max(1, measuredWidth);
        if (width >= metricsStableWidth) {
            metricsStableWidth = width;
            metricsWidthHoldUntil = now + 350L;
            return metricsStableWidth;
        }
        if (now < metricsWidthHoldUntil) return metricsStableWidth;
        metricsStableWidth = Math.max(width, metricsStableWidth - 6);
        return metricsStableWidth;
    }

    private static void drawMergedBackground(GuiGraphicsExtractor context, Font font, String id, VisualRect rect, int color, List<VisualRect> blockers) {
        List<VisualRect> occluders = backgroundOccluders(font, id, rect);
        drawRectWithoutOverlaps(context, rect, occluders, color);
        drawBackgroundBridges(context, rect, blockers != null ? blockers : mergeBlockers(font, id, rect), color);
    }

    private static VisualRect bleedToScreenEdge(VisualRect rect) {
        if (rect == null) return null;
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int safeX = safeZoneXFor(rect.id());
        int safeY = safeZoneYFor(rect.id());
        int x = rect.x();
        int y = rect.y();
        int right = rect.right();
        int bottom = rect.bottom();
        if (x == safeX) x = 0;
        if (y == safeY) y = 0;
        if (right == screenW - safeX) right = screenW;
        if (bottom == screenH - safeY) bottom = screenH;
        x = clamp(x, 0, screenW);
        y = clamp(y, 0, screenH);
        right = clamp(right, x, screenW);
        bottom = clamp(bottom, y, screenH);
        return new VisualRect(rect.id(), x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private static VisualRect visualChromeRect(VisualRect contentRect, HudStyle style) {
        if (contentRect == null) return null;
        VisualRect rect = contentRect;
        if (style.outline()) {
            int pad = HUD_OUTLINE_CHROME_PADDING;
            rect = new VisualRect(
                contentRect.id(),
                contentRect.x() - pad,
                contentRect.y() - pad,
                contentRect.width() + pad * 2,
                contentRect.height() + pad * 2
            );
        }
        return bleedToScreenEdge(rect);
    }

    private static final Map<String, Integer> ORDER_INDEX = buildOrderIndex();
    private static List<HudRectEntry> frameRects;
    private static final CachedHudElement[] FRAME_RECT_CACHE_ENTRIES = new CachedHudElement[ORDER.size()];
    private static List<HudRectEntry> cachedFrameRects = List.of();
    private static long cachedFrameRectOccluders = Long.MIN_VALUE;

    private static Map<String, Integer> buildOrderIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < ORDER.size(); i++) index.put(ORDER.get(i), i);
        return index;
    }

    private record HudRectEntry(String id, int orderIndex, VisualRect rect, boolean background) {
    }

    private static List<HudRectEntry> collectFrameRects(Font font) {
        boolean changed = false;
        for (int i = 0; i < ORDER.size(); i++) {
            String id = ORDER.get(i);
            CachedHudElement current = combinedMetricsRowOwns(id) || !state(id).enabled
                ? null
                : cached(id, font);
            if (FRAME_RECT_CACHE_ENTRIES[i] != current) {
                FRAME_RECT_CACHE_ENTRIES[i] = current;
                changed = true;
            }
        }
        long occluderSignature = hudOccluderSignature();
        if (!changed && occluderSignature == cachedFrameRectOccluders) return cachedFrameRects;

        List<HudRectEntry> entries = new ArrayList<>();
        for (int i = 0; i < ORDER.size(); i++) {
            String other = ORDER.get(i);
            CachedHudElement cached = FRAME_RECT_CACHE_ENTRIES[i];
            if (cached == null) continue;
            HudStyle style = cached.style();
            if (!style.background() && !style.outline()) continue;
            for (VisualRect rect : visualRects(other, font)) {
                entries.add(new HudRectEntry(other, i, rect, style.background()));
            }
        }
        cachedFrameRectOccluders = occluderSignature;
        cachedFrameRects = List.copyOf(entries);
        return cachedFrameRects;
    }

    private static long hudOccluderSignature() {
        long signature = HUD_OCCLUDERS.size();
        for (ElementBounds bounds : HUD_OCCLUDERS) {
            if (bounds == null) continue;
            signature = signature * 31L + bounds.x();
            signature = signature * 31L + bounds.y();
            signature = signature * 31L + bounds.width();
            signature = signature * 31L + bounds.height();
        }
        return signature;
    }

    private static List<VisualRect> backgroundOccluders(Font font, String id, VisualRect self) {
        List<VisualRect> occluders = new ArrayList<>();
        List<HudRectEntry> snapshot = frameRects;
        if (snapshot != null) {
            int selfOrder = ORDER_INDEX.getOrDefault(id, -1);
            for (HudRectEntry entry : snapshot) {
                if (!entry.background() || entry.orderIndex() > selfOrder) continue;
                VisualRect rect = entry.rect();
                if (rect.sameBounds(self)) continue;
                if (entry.orderIndex() == selfOrder && !drawsBefore(rect, self)) continue;
                if (rect.intersects(self)) occluders.add(rect);
            }
            return occluders;
        }
        int selfOrder = ORDER.indexOf(id);
        for (String other : ORDER) {
            if (combinedMetricsRowOwns(other) || !state(other).enabled || !boolSetting(other, "background")) continue;
            int otherOrder = ORDER.indexOf(other);
            if (otherOrder > selfOrder) continue;
            for (VisualRect rect : visualRects(other, font)) {
                if (rect.sameBounds(self)) continue;
                if (otherOrder == selfOrder && !drawsBefore(rect, self)) continue;
                if (rect.intersects(self)) occluders.add(rect);
            }
        }
        return occluders;
    }

    private static boolean drawsBefore(VisualRect rect, VisualRect self) {
        if (rect.y() != self.y()) return rect.y() < self.y();
        if (rect.x() != self.x()) return rect.x() < self.x();
        if (rect.width() != self.width()) return rect.width() < self.width();
        return rect.height() < self.height();
    }

    private static void drawRectWithoutOverlaps(GuiGraphicsExtractor context, VisualRect rect, List<VisualRect> occluders, int color) {
        if (occluders.isEmpty()) {
            UiText.fill(context, rect.x(), rect.y(), rect.right(), rect.bottom(), color);
            return;
        }
        List<Integer> cuts = new ArrayList<>();
        cuts.add(rect.y());
        cuts.add(rect.bottom());
        for (VisualRect other : occluders) {
            int top = clamp(other.y(), rect.y(), rect.bottom());
            int bottom = clamp(other.bottom(), rect.y(), rect.bottom());
            if (top < bottom) {
                cuts.add(top);
                cuts.add(bottom);
            }
        }
        cuts.sort(Integer::compareTo);
        for (int i = 0; i < cuts.size() - 1; i++) {
            int bandY = cuts.get(i);
            int bandBottom = cuts.get(i + 1);
            if (bandY >= bandBottom) continue;
            List<int[]> blocked = new ArrayList<>();
            for (VisualRect other : occluders) {
                if (other.y() >= bandBottom || other.bottom() <= bandY) continue;
                int left = clamp(other.x(), rect.x(), rect.right());
                int right = clamp(other.right(), rect.x(), rect.right());
                if (left < right) blocked.add(new int[] { left, right });
            }
            drawLineSegments(context, rect.x(), rect.right(), blocked, 0, (a, b) -> UiText.fill(context, a, bandY, b, bandBottom, color));
        }
    }

    private static void drawBackgroundBridges(GuiGraphicsExtractor context, VisualRect rect, List<VisualRect> blockers, int color) {
        for (VisualRect other : blockers) {
            if (rect.right() <= other.x() && other.x() - rect.right() <= HUD_OUTLINE_MERGE_TOLERANCE) {
                int y0 = Math.max(rect.y(), other.y());
                int y1 = Math.min(rect.bottom(), other.bottom());
                if (y0 < y1 && rect.right() < other.x()) UiText.fill(context, rect.right(), y0, other.x(), y1, color);
            } else if (other.right() <= rect.x() && rect.x() - other.right() <= HUD_OUTLINE_MERGE_TOLERANCE) {
                int y0 = Math.max(rect.y(), other.y());
                int y1 = Math.min(rect.bottom(), other.bottom());
                if (y0 < y1 && other.right() < rect.x()) UiText.fill(context, other.right(), y0, rect.x(), y1, color);
            }
            if (rect.bottom() <= other.y() && other.y() - rect.bottom() <= HUD_OUTLINE_MERGE_TOLERANCE) {
                int x0 = Math.max(rect.x(), other.x());
                int x1 = Math.min(rect.right(), other.right());
                if (x0 < x1 && rect.bottom() < other.y()) UiText.fill(context, x0, rect.bottom(), x1, other.y(), color);
            } else if (other.bottom() <= rect.y() && rect.y() - other.bottom() <= HUD_OUTLINE_MERGE_TOLERANCE) {
                int x0 = Math.max(rect.x(), other.x());
                int x1 = Math.min(rect.right(), other.right());
                if (x0 < x1 && other.bottom() < rect.y()) UiText.fill(context, x0, other.bottom(), x1, rect.y(), color);
            }
        }
    }

    private static void outlineMerged(GuiGraphicsExtractor context, Font font, String id, int x, int y, int w, int h, int color, int width) {
        VisualRect rect = new VisualRect(id, x, y, w, h);
        outlineMergedRect(context, rect, mergeBlockers(font, id, rect), color, width);
    }

    private static void outlineMergedRect(GuiGraphicsExtractor context, VisualRect rect, List<VisualRect> blockers, int color, int width) {
        int size = Math.max(1, width);
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int x0 = clamp(rect.x(), 0, screenW);
        int x1 = clamp(rect.right(), 0, screenW);
        int y0 = clamp(rect.y(), 0, screenH);
        int y1 = clamp(rect.bottom(), 0, screenH);
        if (x0 >= x1 || y0 >= y1) return;
        if (y0 > 0) drawMergedHorizontalEdge(context, blockers, x0, x1, y0, y0, size, color, true);
        if (y1 < screenH) drawMergedHorizontalEdge(context, blockers, x0, x1, Math.max(y0, y1 - size), y1, size, color, false);
        if (x0 > 0) drawMergedVerticalEdge(context, blockers, y0, y1, x0, x0, size, color, true);
        if (x1 < screenW) drawMergedVerticalEdge(context, blockers, y0, y1, Math.max(x0, x1 - size), x1, size, color, false);
    }

    private static void drawMergedHorizontalEdge(GuiGraphicsExtractor context, List<VisualRect> blockers, int fromX, int toX, int drawY, int edgeY, int size, int color, boolean top) {
        List<int[]> blocked = new ArrayList<>();
        for (VisualRect other : blockers) {
            boolean connected = top
                ? other.y() < edgeY && other.bottom() >= edgeY - HUD_OUTLINE_MERGE_TOLERANCE
                : other.y() <= edgeY + HUD_OUTLINE_MERGE_TOLERANCE && other.bottom() > edgeY;
            if (!connected) continue;
            int overlapStart = Math.max(fromX, other.x());
            int overlapEnd = Math.min(toX, other.right());
            if (overlapStart < overlapEnd) blocked.add(new int[] { overlapStart, overlapEnd });
        }
        drawLineSegments(context, fromX, toX, blocked, size, (a, b) -> UiText.fill(context, a, drawY, b, drawY + size, color));
    }

    private static void drawMergedVerticalEdge(GuiGraphicsExtractor context, List<VisualRect> blockers, int fromY, int toY, int drawX, int edgeX, int size, int color, boolean left) {
        List<int[]> blocked = new ArrayList<>();
        for (VisualRect other : blockers) {
            boolean connected = left
                ? other.x() < edgeX && other.right() >= edgeX - HUD_OUTLINE_MERGE_TOLERANCE
                : other.x() <= edgeX + HUD_OUTLINE_MERGE_TOLERANCE && other.right() > edgeX;
            if (!connected) continue;
            int overlapStart = Math.max(fromY, other.y());
            int overlapEnd = Math.min(toY, other.bottom());
            if (overlapStart < overlapEnd) blocked.add(new int[] { overlapStart, overlapEnd });
        }
        drawLineSegments(context, fromY, toY, blocked, size, (a, b) -> UiText.fill(context, drawX, a, drawX + size, b, color));
    }

    private static List<VisualRect> mergeBlockers(Font font, String id, VisualRect self) {
        List<VisualRect> blockers = new ArrayList<>();
        List<HudRectEntry> snapshot = frameRects;
        if (snapshot != null) {
            for (HudRectEntry entry : snapshot) {
                if (entry.id().equals(id) && !ACTIVE_MODULES.equals(entry.id())) continue;
                VisualRect rect = entry.rect();
                if (self != null && rect.sameBounds(self)) continue;
                blockers.add(rect);
            }
            return blockers;
        }
        for (String other : ORDER) {
            if (combinedMetricsRowOwns(other) || !state(other).enabled || !mergesWithHudOutline(other)) continue;
            if (other.equals(id) && !ACTIVE_MODULES.equals(other)) continue;
            for (VisualRect rect : visualRects(other, font)) {
                if (self != null && rect.sameBounds(self)) continue;
                blockers.add(rect);
            }
        }
        return blockers;
    }

    private static List<VisualRect> visualRects(String id, Font font) {
        CachedHudElement cached = cached(id, font);
        if (ACTIVE_MODULES.equals(id)) {
            List<VisualRect> rects = activeModuleVisualRects(id, cached);
            if (HUD_OCCLUDERS.isEmpty() || rects.isEmpty()) return rects;
            List<VisualRect> visible = new ArrayList<>(rects.size());
            for (VisualRect rect : rects) {
                if (!occluded(rect)) visible.add(rect);
            }
            return visible;
        }
        ElementBounds bounds = cached.layout().bounds();
        if (occluded(bounds)) return List.of();
        return List.of(visualChromeRect(new VisualRect(id, bounds.x(), bounds.y(), bounds.width(), bounds.height()), cached.style()));
    }

    private static List<VisualRect> activeModuleVisualRects(String id, CachedHudElement cached) {
        List<VisualRect> content = activeModuleContentRects(id, cached);
        if (content.isEmpty()) return content;
        List<VisualRect> visual = new ArrayList<>(content.size());
        HudStyle style = cached.style();
        for (VisualRect rect : content) visual.add(visualChromeRect(rect, style));
        return visual;
    }

    private static List<VisualRect> activeModuleContentRects(String id, CachedHudElement cached) {

        if (cached.contentRects != null) return cached.contentRects;
        List<VisualRect> rects = new ArrayList<>();
        HudLayout layout = cached.layout();
        HudStyle style = cached.style();
        int rowH = style.activeRowHeight();
        int gap = style.lineGap();
        int stairSnap = style.stairSnap();
        int pad = style.padding();
        int groupWidth = -1;
        for (int i = 0; i < cached.lines().size(); i++) {
            int lineW = i < cached.widths().size() ? cached.widths().get(i) : lineWidth(MC.font, cached.lines().get(i));
            int rowW = Math.max(1, lineW + pad * 2);
            if (groupWidth < 0 || Math.abs(groupWidth - rowW) > stairSnap) groupWidth = rowW;
            else rowW = groupWidth;
            int rowX = alignedRowX(style.alignment(), layout.x(), layout.unscaledWidth(), rowW);
            int rowY = layout.y() + i * (rowH + gap);
            rects.add(new VisualRect(id, rowX, rowY, rowW, rowH));
        }
        cached.contentRects = java.util.List.copyOf(rects);
        return cached.contentRects;
    }

    private static int alignedRowX(String alignment, int renderX, int unscaledW, int rowW) {
        if ("Center".equals(alignment)) return renderX + Math.max(0, (unscaledW - rowW) / 2);
        if ("Right".equals(alignment)) return renderX + Math.max(0, unscaledW - rowW);
        return renderX;
    }

    private static boolean mergesWithHudOutline(String id) {
        HudStyle style = cached(id, MC.font).style();
        return style.background() || style.outline();
    }

    private static boolean occluded(ElementBounds bounds) {
        if (bounds == null || HUD_OCCLUDERS.isEmpty()) return false;
        for (ElementBounds occluder : HUD_OCCLUDERS) {
            if (bounds.intersects(occluder)) return true;
        }
        return false;
    }

    private static boolean occluded(VisualRect rect) {
        if (rect == null || HUD_OCCLUDERS.isEmpty()) return false;
        for (ElementBounds occluder : HUD_OCCLUDERS) {
            if (rect.x() < occluder.right() && rect.right() > occluder.x()
                && rect.y() < occluder.bottom() && rect.bottom() > occluder.y()) {
                return true;
            }
        }
        return false;
    }

    private static int computeDodge(ElementBounds bounds) {
        if (bounds == null || HUD_OCCLUDERS.isEmpty() || !occluded(bounds)) return 0;
        int sh = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int down = dodgeInDir(bounds, 1, sh);
        int up = dodgeInDir(bounds, -1, sh);
        boolean haveDown = down != Integer.MIN_VALUE;
        boolean haveUp = up != Integer.MIN_VALUE;
        if (haveDown && haveUp) return Math.abs(down) <= Math.abs(up) ? down : up;
        if (haveDown) return down;
        if (haveUp) return up;
        return 0;
    }

    private static int dodgeInDir(ElementBounds bounds, int dir, int sh) {
        int shift = 0;
        for (int iter = 0; iter < 32; iter++) {
            ElementBounds moved = new ElementBounds(bounds.id(), bounds.x(), bounds.y() + shift, bounds.width(), bounds.height());
            ElementBounds hit = null;
            for (ElementBounds occ : HUD_OCCLUDERS) {
                if (moved.intersects(occ)) { hit = occ; break; }
            }
            if (hit == null) {
                int top = bounds.y() + shift;
                return (top < 0 || top + bounds.height() > sh) ? Integer.MIN_VALUE : shift;
            }
            int next = dir > 0 ? (hit.bottom() - bounds.y() + 1) : (hit.y() - bounds.height() - bounds.y() - 1);
            if (dir > 0 ? next <= shift : next >= shift) next = shift + dir;
            shift = next;
            int top = bounds.y() + shift;
            if (top < 0 || top + bounds.height() > sh) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static void drawLineSegments(GuiGraphicsExtractor context, int from, int to, List<int[]> blocked, int capOverlap, SegmentDrawer drawer) {
        if (from >= to) return;
        if (blocked.isEmpty()) {
            drawer.draw(from, to);
            return;
        }
        blocked.sort(Comparator.comparingInt(a -> a[0]));
        int cursor = from;
        for (int[] block : blocked) {
            int start = clamp(block[0], from, to);
            int end = clamp(block[1], from, to);
            if (cursor < start) drawer.draw(cursor, Math.min(to, start + capOverlap));
            cursor = Math.max(cursor, end);
            if (cursor >= to) return;
        }
        if (cursor < to) drawer.draw(Math.max(from, cursor - capOverlap), to);
    }

    @FunctionalInterface
    private interface SegmentDrawer {
        void draw(int from, int to);
    }

    private static void outline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color, int width) {
        int size = Math.max(1, width);
        int screenW = MC.getWindow() == null ? 854 : AutismUiScale.getVirtualScreenWidth();
        int screenH = MC.getWindow() == null ? 480 : AutismUiScale.getVirtualScreenHeight();
        int x0 = clamp(x, 0, screenW);
        int y0 = clamp(y, 0, screenH);
        int x1 = clamp(x + w, x0, screenW);
        int y1 = clamp(y + h, y0, screenH);
        if (x0 >= x1 || y0 >= y1) return;
        int edge = Math.min(size, Math.max(1, Math.min(x1 - x0, y1 - y0)));
        UiText.fill(context, x0, y0, x1, Math.min(y1, y0 + edge), color);
        if (y1 - y0 > edge) UiText.fill(context, x0, Math.max(y0, y1 - edge), x1, y1, color);
        UiText.fill(context, x0, y0, Math.min(x1, x0 + edge), y1, color);
        if (x1 - x0 > edge) UiText.fill(context, Math.max(x0, x1 - edge), y0, x1, y1, color);
    }

    public record ElementBounds(String id, int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean intersects(ElementBounds other) {
            return other != null && x < other.right() && right() > other.x()
                && y < other.bottom() && bottom() > other.y();
        }
    }

    public record HudLayout(String id, int x, int y, int unscaledWidth, int unscaledHeight, double scale) {
        public int scaledWidth() {
            return (int) Math.ceil(unscaledWidth * scale);
        }

        public int scaledHeight() {
            return (int) Math.ceil(unscaledHeight * scale);
        }

        public ElementBounds bounds() {
            return new ElementBounds(id, x, y, scaledWidth(), scaledHeight());
        }
    }

    private record HudCacheKey(
        boolean enabled,
        String anchor,
        int x,
        int y,
        int settingsHash,
        long timeBucket,
        int screenWidth,
        int screenHeight,
        int playerId,
        int activeRevision,
        int moduleRevision,
        int fontIdentity,
        int themeRevision
    ) {
    }

    private static final class CachedHudElement {
        private final HudCacheKey signature;
        private final List<HudLine> lines;
        private final List<Integer> widths;
        private final HudLayout layout;
        private final HudStyle style;

        private long nextSignatureCheckAtMs;

        private List<VisualRect> contentRects;

        private CachedHudElement(HudCacheKey signature, List<HudLine> lines, List<Integer> widths, HudLayout layout, HudStyle style) {
            this.signature = signature;
            this.lines = lines;
            this.widths = widths;
            this.layout = layout;
            this.style = style;
        }

        HudCacheKey signature() { return signature; }
        List<HudLine> lines() { return lines; }
        List<Integer> widths() { return widths; }
        HudLayout layout() { return layout; }
        HudStyle style() { return style; }
    }

    private record HudStyle(
        boolean background, boolean outline, boolean shadow,
        int outlineWidth, int padding, int verticalPadding, int lineGap,
        int lineHeight, int activeRowHeight, int stairSnap,
        String alignment,
        int backgroundColor, int outlineColor, int accentColor, int labelColor
    ) {
    }

    private static HudStyle computeStyle(String id) {
        return new HudStyle(
            boolSetting(id, "background"), boolSetting(id, "outline"), boolSetting(id, "shadow"),
            Math.max(1, intSetting(id, "outline-width", 1)),
            padding(id), verticalPadding(id), lineGap(id), lineHeight(id),
            activeModuleRowHeight(id), activeModuleStairSnap(id),
            setting(id, "alignment"),
            color(id, "background-color", 0x32191919),
            color(id, "outline-color", ACTIVE_MODULES.equals(id) ? 0xFF8F1F24 : 0xFF750000),
            color(id, "accent-color", 0xFFFF3B3B),
            color(id, "label-color", 0xFFB79E9E));
    }

    private record VisualRect(String id, int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean sameBounds(VisualRect other) {
            return other != null && x == other.x && y == other.y && width == other.width && height == other.height;
        }

        boolean intersects(VisualRect other) {
            return other != null && x < other.right() && right() > other.x() && y < other.bottom() && bottom() > other.y();
        }
    }

    private static final class HudLine {
        private final List<HudSegment> segments = new ArrayList<>();

        private int[] segmentWidths;

        void add(String text, int color) {
            if (text != null && !text.isEmpty()) segments.add(new HudSegment(text, color));
        }

        String plainText() {
            StringBuilder builder = new StringBuilder();
            for (HudSegment segment : segments) builder.append(segment.text);
            return builder.toString();
        }
    }

    private record HudSegment(String text, int color) {
    }

    private record ModuleOptionAccess(boolean exists, String value) {
        ModuleOptionAccess(Module module, String id) {
            this(module != null && module.setting(id) != null, module != null && module.setting(id) != null ? module.value(id) : "");
        }
    }
}
