package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.Chip;
import autismclient.gui.vanillaui.components.CompactControlGlyphs;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AutismPacketLoggerOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int HEADER_H = HEADER_HEIGHT;
    private static final int GROUP_THRESHOLD = 10;
    private static final int DEFAULT_PANEL_WIDTH = 360;
    private static final int DEFAULT_PANEL_HEIGHT = 250;

    private static final int CAP_ALL = 800;
    private static final int CAP_INVENTORY = 400;
    private static final int CAP_MOVEMENT = 200;
    private static final int CAP_PAYLOAD = 400;
    private static final String OVERLAY_ID = "AutismPacketLoggerOverlay";
    private static final long UI_FLUSH_INTERVAL_MS = 500L;
    private static final int BLOCKED_DEFAULTS_VERSION = 2;
    private static final int METADATA_TIME_COLOR = 0xFFB69EA4;
    private static final int METADATA_TICK_COLOR = 0xFFA2B1C0;

    private static final Set<String> DEFAULT_BLOCKED_NAMES_V2_ADDITIONS = createIgnoredPacketKeyAdditionsV2();
    private static final Set<String> DEFAULT_BLOCKED_NAMES = createIgnoredPacketKeys();

    public enum Category {
        ALL("All"), INVENTORY("INV"), MOVEMENT("Move"), PAYLOAD("Payload");
        public final String label;
        Category(String l) { this.label = l; }
    }

    private static final Set<String> INVENTORY_NAMES = new HashSet<>(Arrays.asList(
        "ClickSlot", "CloseAbstractContainerScreen", "OpenScreen", "AbstractContainerMenu",
        "CreativeInventoryAction", "PickFromInventory", "PlayerAction",
        "InventoryS2C", "AbstractContainerMenuSlotUpdate", "AbstractContainerMenuProperty",
        "SetTradeOffers", "OpenHorseScreen", "CraftRequest",
        "ButtonClick", "RecipeBookData", "UpdateSelectedSlot",
        "HandSwing", "PlayerInteractBlock", "PlayerInteractItem",
        "PlayerInteractEntity", "ItemPickupAnimation",
        "ContainerClick", "ContainerClose", "ContainerSetContent", "ContainerSetData",
        "ContainerSetSlot", "ContainerButtonClick", "SetCreativeModeSlot",
        "SetCarriedItem", "Swing", "UseItem", "UseItemOn", "Interact",
        "SetCursorItem", "SetPlayerInventory", "TakeItemEntity", "MerchantOffers"
    ));

    private static final Set<String> MOVEMENT_NAMES = new HashSet<>(Arrays.asList(
        "PlayerMove", "PlayerMoveFull", "PlayerMovePositionAndOnGround",
        "PlayerMoveLookAndOnGround", "PlayerMoveOnGroundOnly",
        "EntityPosition", "EntityPositionSync", "EntitySetHead",
        "EntityVelocityUpdate", "VehicleMove",
        "MoveRelative", "PacketMoveRelative", "RotateRelative",
        "PacketRotateRelative", "EntityPacketRotate",
        "EntityMoveRelative", "EntityRotate", "TeleportConfirm",
        "ClientTickEnd",
        "ServerboundMovePlayer", "ServerboundMoveVehicle", "ServerboundPlayerInput",
        "ServerboundAcceptTeleportation", "ClientboundMoveEntity", "ClientboundMoveVehicle",
        "ClientboundMoveMinecart", "ClientboundPlayerPosition", "ClientboundPlayerRotation",
        "ClientboundEntityPositionSync", "ClientboundSetEntityMotion", "ClientboundRotateHead",
        "ClientboundTeleportEntity"
    ));

    private static final Set<String> PAYLOAD_NAMES = new HashSet<>(Arrays.asList(
        "CustomPacketPayload",
        "CustomPacketPayloadC2S",
        "CustomPacketPayloadS2C",
        "BrandPayload",
        "PluginMessage",
        "CustomPayload",
        "S2CPayload",
        "DiscardedPayload"
    ));

    private final Font textRenderer;
    private final AutismPacketInspectOverlay inspectOverlay;
    private final BlockedPacketListOverlay blockedListOverlay;
    private final PayloadChannelListenerOverlay payloadListenerOverlay;
    private final AutismPayloadChannelListeners payloadListeners;
    private final AutismPayloadChannelRegistrations payloadRegistrations;
    private final CompactTheme theme = new CompactTheme();
    private final AutismContextMenu<LogEntry> ctxMenu;
    private int PANEL_WIDTH = DEFAULT_PANEL_WIDTH;
    private int PANEL_HEIGHT = DEFAULT_PANEL_HEIGHT;
    private int currentPanelHeight = DEFAULT_PANEL_HEIGHT;
    private volatile boolean paused = true;
    private boolean configurationOnly = false;
    private boolean isDragging;
    private double dragOffX, dragOffY;
    private boolean headerDragMoved;
    private int scrollOffset;
    private final ScrollState contentScrollState = new ScrollState();
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;

    private String searchFilter = "";
    private final AutismChatField searchField;
    private Category activeTab = Category.ALL;
    private boolean groupingEnabled = true;
    private int dirFilter = 0;
    private boolean payloadFilteredOnly;

    private final Set<String> blockedNames = new LinkedHashSet<>();
    private final Set<String> blockedNormalized = new HashSet<>();
    private final Map<Class<?>, Boolean> blockedClassCache = new HashMap<>();
    private boolean blockedExpanded;

    private final Deque<LogEntry> bufAll = new ArrayDeque<>();
    private final Deque<LogEntry> bufInventory = new ArrayDeque<>();
    private final Deque<LogEntry> bufMovement = new ArrayDeque<>();
    private final Deque<LogEntry> bufPayload = new ArrayDeque<>();
    private final List<LogEntry> pendingEntries = new ArrayList<>();
    private final AutismPacketContextTracker packetContextTracker = new AutismPacketContextTracker();

    private volatile int gameTick;
    private long lastUiFlushMs;

    private List<DisplayRow> displayRows = new ArrayList<>();
    private boolean dirty = true;

    private final Set<String> expandedGroups = new HashSet<>();

    public AutismPacketLoggerOverlay(Font textRenderer) {
        super(OVERLAY_ID, DEFAULT_PANEL_WIDTH, DEFAULT_PANEL_HEIGHT);
        this.textRenderer = textRenderer;
        this.PANEL_WIDTH = defaultPanelWidth();
        this.PANEL_HEIGHT = defaultPanelHeight();
        this.currentPanelHeight = this.PANEL_HEIGHT;
        this.panelX = 200;
        this.panelY = 40;
        this.searchField = new AutismChatField(MC, textRenderer, 0, 0, searchFieldWidth(), filterRowHeight(), false);
        this.searchField.setPlaceholder(Component.literal("Search..."));
        this.searchField.setMaxLength(160);
        this.searchField.setChangedListener(value -> {
            searchFilter = value == null ? "" : value;
            dirty = true;
        });
        this.inspectOverlay = new AutismPacketInspectOverlay(textRenderer);
        this.blockedListOverlay = new BlockedPacketListOverlay();
        this.payloadListeners = new AutismPayloadChannelListeners();
        this.payloadRegistrations = new AutismPayloadChannelRegistrations();
        this.payloadListenerOverlay = new PayloadChannelListenerOverlay();
        this.ctxMenu = new AutismContextMenu<>(theme, textRenderer, this::getCtxItems, lineHeight());
        setContextMenu(ctxMenu);
        AutismOverlayManager.get().register(this.inspectOverlay);
        AutismOverlayManager.get().register(this.blockedListOverlay);
        AutismOverlayManager.get().register(this.payloadListenerOverlay);
        loadBlockedFromConfig();

        this.paused = !AutismConfig.getGlobal().packetLoggerCapturing;
    }

    public void saveState() {
        saveLayout();
    }

    public synchronized String censusSummary() {
        return "capturing=" + !paused
            + " all=" + bufAll.size()
            + " inv=" + bufInventory.size()
            + " move=" + bufMovement.size()
            + " payload=" + bufPayload.size()
            + " pending=" + pendingEntries.size();
    }

    public void restoreState() {
        restoreLayout();
        payloadListeners.load();
        payloadRegistrations.load();
        dirty = true;
    }

    public static boolean shouldRestoreSavedVisible() {
        AutismWindowLayout layout = AutismSharedState.get().getWindowLayout(OVERLAY_ID);
        return layout != null && layout.visible;
    }

    @Override
    public int getMinWidth() {
        return defaultPanelWidth();
    }

    @Override
    public int getMinHeight() {
        return defaultPanelHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        PANEL_HEIGHT = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    public void setGameTick(int t) { gameTick = t; }
    public boolean isPaused() { return paused; }
    public void setConfigurationOnly(boolean configurationOnly) {
        boolean leavingOfflineSetup = this.configurationOnly && !configurationOnly;
        this.configurationOnly = configurationOnly;
        if (configurationOnly) ctxMenu.close();
        if (leavingOfflineSetup) {
            inspectOverlay.close();
            blockedListOverlay.setVisible(false);
            payloadListenerOverlay.setVisible(false);
        }
    }
    public synchronized void setPaused(boolean paused) {
        if (this.paused == paused) return;
        if (paused) {
            flushPendingLocked();
        } else {

            lastUiFlushMs = System.currentTimeMillis();
        }
        this.paused = paused;
        dirty = true;
        AutismNetworkCaptureState.refreshCurrent();

        AutismConfig config = AutismConfig.getGlobal();
        if (config.packetLoggerCapturing != !paused) {
            config.packetLoggerCapturing = !paused;
            config.save();
        }
    }

    public void logPacket(Packet<?> packet, String direction) {
        logPacket(packet, direction, false, false);
    }

    public void logPayloadPacketSilently(Packet<?> packet, String direction) {
        logPacket(packet, direction, true, true);
    }

    public void logPayloadSnapshotSilently(long timestampMs, int tick, String direction, Class<?> packetClass,
                                           AutismPayloadSupport.PayloadSnapshot payloadSnapshot) {
        if (payloadSnapshot == null) return;
        synchronized (this) {
            String name = packetClass != null && Packet.class.isAssignableFrom(packetClass)
                ? friendlyNameForClass(packetClass)
                : "Custom Payload";
            boolean isInventory = matchesAny(name, INVENTORY_NAMES);
            boolean isMovement = matchesAny(name, MOVEMENT_NAMES);
            LogEntry e = new LogEntry(timestampMs, tick, direction, name, packetClass, null, isInventory, isMovement, true,
                null, null, payloadSnapshot, AutismPacketContextTracker.EMPTY_CAPTURE);
            pendingEntries.add(e);
            maybeFlushPendingLocked(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static String friendlyNameForClass(Class<?> packetClass) {
        try {
            return AutismPacketNamer.getFriendlyName((Class<? extends Packet<?>>) packetClass);
        } catch (Throwable ignored) {
            return packetClass == null ? "Custom Payload" : packetClass.getSimpleName();
        }
    }

    private void logPacket(Packet<?> packet, String direction, boolean ignorePaused, boolean payloadOnly) {
        if (packet == null) return;
        if (!ignorePaused && paused) return;
        synchronized (this) {

            if (!ignorePaused && paused) return;
            Class<?> cls = packet.getClass();
            String name = AutismPacketNamer.getFriendlyName(packet, direction);
            boolean isInventory = matchesAny(name, INVENTORY_NAMES);
            boolean isMovement = matchesAny(name, MOVEMENT_NAMES);
            boolean isPayload = isPayloadPacket(cls, name);
            if (!(payloadOnly && isPayload) && isBlockedName(cls, name)) return;
            if (payloadOnly && !isPayload) return;
            AutismPacketContextTracker.Capture packetContext = packetContextTracker.capture(packet, direction);
            LogCaptureContext captureContext = captureLogContext(packet);
            AutismPayloadSupport.PayloadSnapshot payloadSnapshot = isPayload ? AutismPayloadSupport.snapshot(packet, direction) : null;
            if (payloadSnapshot != null) {
                AutismPayloadChannelListeners.Match match = payloadListeners.match(payloadSnapshot, direction);
                if (match != null) {
                    AutismPayloadFilterNotifier.onMatch(payloadSnapshot.channel(), direction, match);
                }
            }

            LogEntry e = new LogEntry(System.currentTimeMillis(), gameTick, direction, name, cls, packet, isInventory, isMovement, isPayload,
                captureContext.blockStateSummary(), captureContext.screenSummary(), payloadSnapshot, packetContext);
            pendingEntries.add(e);
            maybeFlushPendingLocked(false);
        }
    }

    private static LogCaptureContext captureLogContext(Packet<?> packet) {
        if (packet == null || MC == null || !MC.isSameThread()) return LogCaptureContext.EMPTY;
        try {
            if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
                return new LogCaptureContext(snapshotBlockState(actionPacket.getPos()), null);
            }
            if (packet instanceof ServerboundUseItemOnPacket interactBlockPacket) {
                return new LogCaptureContext(snapshotBlockState(interactBlockPacket.getHitResult().getBlockPos()), null);
            }
            if (packet instanceof ServerboundContainerClosePacket) {
                return new LogCaptureContext(null, snapshotCurrentScreen());
            }
        } catch (Throwable ignored) {  }
        return LogCaptureContext.EMPTY;
    }

    private static String snapshotBlockState(BlockPos pos) {
        if (pos == null || MC.level == null) return null;
        try {
            BlockState state = MC.level.getBlockState(pos);
            if (state == null) return null;
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String snapshotCurrentScreen() {
        if (MC.gui.screen() == null) return null;
        try {
            String title = MC.gui.screen().getTitle() == null ? "" : MC.gui.screen().getTitle().getString().trim();
            String className = MC.gui.screen().getClass().getSimpleName();
            if (title.isEmpty()) return className;
            if (className == null || className.isBlank()) return title;
            return title + " [" + className + "]";
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isPacketBlocked(Class<?> cls) { return isBlockedClass(cls); }

    private static boolean matchesAny(String name, Set<String> set) {
        for (String s : set) { if (name.contains(s)) return true; }
        return false;
    }

    private static boolean isPayloadPacket(Class<?> cls, String name) {
        if (matchesAny(name, PAYLOAD_NAMES)) return true;
        if (cls == null) return false;
        String simpleName = cls.getSimpleName();
        if (matchesAny(simpleName, PAYLOAD_NAMES)) return true;
        String className = cls.getName();
        return matchesAny(className, PAYLOAD_NAMES);
    }

    private static Set<String> createIgnoredPacketKeys() {
        Set<String> keys = new LinkedHashSet<>();

        registerIgnoredPackets(keys,

            "ServerboundMovePlayerPacket.Pos",
            "ServerboundMovePlayerPacket.PosRot",
            "ServerboundMovePlayerPacket.Rot",
            "ServerboundMovePlayerPacket.StatusOnly",
            "ServerboundClientTickEndPacket",
            "ServerboundPlayerInputPacket",
            "ServerboundAcceptTeleportationPacket",
            "ServerboundChunkBatchReceivedPacket",
            "ServerboundPlayerCommandPacket",
            "ServerboundKeepAlivePacket",
            "ServerboundPongPacket",

            "ClientboundBundlePacket",
            "ClientboundBundleDelimiterPacket",
            "ClientboundKeepAlivePacket",
            "ClientboundPingPacket",

            "ClientboundMoveEntityPacket.Pos",
            "ClientboundMoveEntityPacket.PosRot",
            "ClientboundMoveEntityPacket.Rot",
            "ClientboundEntityPositionSyncPacket",
            "ClientboundSetEntityMotionPacket",
            "ClientboundRotateHeadPacket",
            "ClientboundTeleportEntityPacket",
            "ClientboundMoveMinecartPacket",
            "ClientboundSetEntityDataPacket",
            "ClientboundUpdateAttributesPacket",
            "ClientboundEntityEventPacket",

            "ClientboundLightUpdatePacket",
            "ClientboundLevelChunkWithLightPacket",
            "ClientboundForgetLevelChunkPacket",
            "ClientboundChunkBatchStartPacket",
            "ClientboundChunkBatchFinishedPacket",
            "ClientboundSetChunkCacheCenterPacket",
            "ClientboundChunksBiomesPacket",
            "ClientboundSectionBlocksUpdatePacket",
            "ClientboundBlockUpdatePacket",
            "ClientboundBlockEntityDataPacket",

            "ClientboundSetTimePacket",
            "ClientboundLevelParticlesPacket",
            "ClientboundLevelEventPacket",
            "ClientboundSoundPacket",
            "ClientboundSoundEntityPacket",

            "ClientboundPlayerInfoUpdatePacket",
            "ClientboundSetObjectivePacket",
            "ClientboundTickingStatePacket"
        );
        keys.addAll(DEFAULT_BLOCKED_NAMES_V2_ADDITIONS);
        return Collections.unmodifiableSet(keys);
    }

    private static Set<String> createIgnoredPacketKeyAdditionsV2() {
        Set<String> keys = new LinkedHashSet<>();
        registerIgnoredPackets(keys,
            "ClientboundSetScorePacket",
            "ClientboundSetEquipmentPacket",
            "ClientboundAnimatePacket",
            "ClientboundTabListPacket",
            "ClientboundBlockDestructionPacket",
            "ClientboundPlayerPositionPacket",
            "ClientboundExplodePacket",
            "ClientboundRemoveEntitiesPacket",
            "ClientboundBlockEventPacket",
            "ClientboundSetPlayerTeamPacket",
            "ClientboundSystemChatPacket"
        );
        return Collections.unmodifiableSet(keys);
    }

    private static void registerIgnoredPackets(Set<String> keys, String... names) {
        for (String name : names) {
            registerIgnoredPacket(keys, name);
        }
    }

    private static void registerIgnoredPacket(Set<String> keys, String name) {
        if (name == null || name.isBlank()) return;
        keys.add(name.trim());
    }

    private static String normalizePacketKey(String value) {
        if (value == null || value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private void recomputeBlockedNormalized() {
        blockedNormalized.clear();
        blockedClassCache.clear();
        for (String name : blockedNames) {
            if (name == null || name.isBlank()) continue;
            blockedNormalized.add(normalizePacketKey(name));
            if (name.endsWith("Packet")) {
                blockedNormalized.add(normalizePacketKey(name.substring(0, name.length() - 6)));
            }
        }
    }

    private boolean isBlockedName(Class<?> cls, String friendlyName) {
        if (blockedNormalized.isEmpty()) return false;
        if (blockedNormalized.contains(normalizePacketKey(friendlyName))) return true;
        return isBlockedClass(cls);
    }

    private boolean isBlockedClass(Class<?> cls) {
        if (cls == null || blockedNormalized.isEmpty()) return false;
        Boolean cached = blockedClassCache.get(cls);
        if (cached != null) return cached;
        boolean blocked = computeBlockedClass(cls);
        blockedClassCache.put(cls, blocked);
        return blocked;
    }

    private boolean computeBlockedClass(Class<?> cls) {
        if (blockedNormalized.contains(normalizePacketKey(cls.getSimpleName()))) return true;
        if (blockedNormalized.contains(normalizePacketKey(cls.getName()))) return true;
        if (Packet.class.isAssignableFrom(cls)) {
            @SuppressWarnings("unchecked")
            Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) cls;
            if (blockedNormalized.contains(normalizePacketKey(AutismPacketNamer.getFriendlyName(packetClass)))) return true;
        }
        return false;
    }

    private void loadBlockedFromConfig() {
        AutismConfig config = AutismConfig.getGlobal();
        blockedNames.clear();
        if (config.packetLoggerBlockedInit) {
            if (config.packetLoggerBlocked != null) blockedNames.addAll(config.packetLoggerBlocked);
            if (config.packetLoggerBlockedDefaultsVersion < BLOCKED_DEFAULTS_VERSION) {
                blockedNames.addAll(DEFAULT_BLOCKED_NAMES_V2_ADDITIONS);
                config.packetLoggerBlocked = new ArrayList<>(blockedNames);
                config.packetLoggerBlockedDefaultsVersion = BLOCKED_DEFAULTS_VERSION;
                config.save();
            }
        } else {
            blockedNames.addAll(DEFAULT_BLOCKED_NAMES);
            config.packetLoggerBlocked = new ArrayList<>(blockedNames);
            config.packetLoggerBlockedInit = true;
            config.packetLoggerBlockedDefaultsVersion = BLOCKED_DEFAULTS_VERSION;
            config.save();
        }
        recomputeBlockedNormalized();
    }

    private void saveBlocked() {
        AutismConfig config = AutismConfig.getGlobal();
        config.packetLoggerBlocked = new ArrayList<>(blockedNames);
        config.packetLoggerBlockedInit = true;
        config.packetLoggerBlockedDefaultsVersion = BLOCKED_DEFAULTS_VERSION;
        config.save();
    }

    public synchronized void blockPacketName(String name) {
        if (name == null || name.isBlank()) return;
        if (blockedNames.add(name.trim())) {
            recomputeBlockedNormalized();
            saveBlocked();
        }
        purgeBlockedEntries();
    }

    private synchronized void unblockName(String name) {
        if (blockedNames.remove(name)) {
            recomputeBlockedNormalized();
            saveBlocked();
        }
    }

    private synchronized void unblockAllMatching(Class<?> cls, String friendlyName) {
        Set<String> targets = new HashSet<>();
        if (friendlyName != null && !friendlyName.isBlank()) targets.add(normalizePacketKey(friendlyName));
        if (cls != null) {
            targets.add(normalizePacketKey(cls.getSimpleName()));
            targets.add(normalizePacketKey(cls.getName()));
        }
        for (String t : new ArrayList<>(targets)) {
            if (t.endsWith("packet")) targets.add(t.substring(0, t.length() - 6));
        }
        targets.remove("");
        if (targets.isEmpty()) return;
        boolean changed = blockedNames.removeIf(raw -> {
            if (raw == null) return false;
            if (targets.contains(normalizePacketKey(raw))) return true;
            return raw.endsWith("Packet")
                && targets.contains(normalizePacketKey(raw.substring(0, raw.length() - 6)));
        });
        if (changed) {
            recomputeBlockedNormalized();
            saveBlocked();
        }
    }

    private synchronized void resetBlockedToDefault() {
        blockedNames.clear();
        blockedNames.addAll(DEFAULT_BLOCKED_NAMES);
        recomputeBlockedNormalized();
        saveBlocked();
        purgeBlockedEntries();
    }

    private synchronized void clearBlocked() {
        blockedNames.clear();
        recomputeBlockedNormalized();
        saveBlocked();
    }

    private synchronized void purgeBlockedEntries() {
        bufAll.removeIf(e -> isBlockedName(e.packetClass, e.shortName));
        bufInventory.removeIf(e -> isBlockedName(e.packetClass, e.shortName));
        bufMovement.removeIf(e -> isBlockedName(e.packetClass, e.shortName));
        bufPayload.removeIf(e -> isBlockedName(e.packetClass, e.shortName));
        dirty = true;
    }

    public static boolean isPayloadPacket(Packet<?> packet) {
        if (packet == null) return false;
        if (AutismPayloadSupport.extractPayload(packet) != null) return true;
        Class<?> cls = packet.getClass();
        return isPayloadPacket(cls, AutismPacketNamer.getFriendlyName(packet, ""));
    }

    private static void addCapped(Deque<LogEntry> buf, LogEntry e, int cap) {
        buf.addLast(e);
        while (buf.size() > cap) buf.removeFirst();
    }

    private void maybeFlushPending() {
        synchronized (this) {
            maybeFlushPendingLocked(false);
        }
    }

    private void maybeFlushPendingLocked(boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            if (pendingEntries.isEmpty()) return;
            if (lastUiFlushMs != 0L && now - lastUiFlushMs < UI_FLUSH_INTERVAL_MS) return;
        }
        flushPendingLocked();
        lastUiFlushMs = now;
    }

    private void flushPending() {
        synchronized (this) {
            flushPendingLocked();
            lastUiFlushMs = System.currentTimeMillis();
        }
    }

    private void flushPendingLocked() {
        if (pendingEntries.isEmpty()) return;

        for (LogEntry e : pendingEntries) {
            addCapped(bufAll, e, CAP_ALL);
            if (e.isInventory) addCapped(bufInventory, e, CAP_INVENTORY);
            if (e.isMovement) addCapped(bufMovement, e, CAP_MOVEMENT);
            if (e.isPayload) addCapped(bufPayload, e, CAP_PAYLOAD);
        }

        pendingEntries.clear();
        dirty = true;
    }

    @Override public void setVisible(boolean v) {
        visible = v;
        if (v) { scrollOffset = 0; contentScrollState.jumpTo(0, 0); dirty = true; ctxMenu.close(); AutismOverlayManager.get().bringToFront(this); }
        else inspectOverlay.close();
        saveLayout();
    }
    public void toggle() { setVisible(!visible); }
    @Override public boolean isVisible() { return visible; }
    @Override public boolean isCollapsed() { return collapsed; }
    @Override public void setCollapsed(boolean c) {
        if (collapsed == c) return;
        collapsed = c;
        isDragging = false;
        headerDragMoved = false;
        scrollbarDragging = false;
        if (c) {
            clearHiddenInteractionState();
            ctxMenu.close();
        }
        saveLayout();
    }
    @Override public boolean usesSharedHeaderClickCollapse() { return true; }
    @Override public boolean hasTextFieldFocused() { return searchField != null && searchField.isFocused(); }
    @Override public void clearTextFieldFocus() { if (searchField != null) searchField.setFocused(false); }
    @Override public int getZLevel() { return 10; }

    private void drawUiText(GuiGraphicsExtractor context, String text, UiTone tone, int color, int x, int y) {
        UiText.draw(context, textRenderer, text, theme.fontFor(tone), color, x, y, false);
    }

    @Override public boolean isMouseOver(double mx, double my) {
        if (!visible) return false;
        int panelHeight = collapsed ? HEADER_H : currentPanelHeight;
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
        int h = getRenderedFrameHeight(bounds, collapsed);
        boolean overPanel = mx >= panelX && mx <= panelX + PANEL_WIDTH && my >= panelY && my <= panelY + h;
        overPanel |= ctxMenu.isMouseOver(mx, my);
        return overPanel;
    }

    @Override public boolean isOverDragBar(double mx, double my) {
        if (!visible) return false;
        return mx >= panelX && mx <= panelX + PANEL_WIDTH
            && my >= panelY && my <= panelY + HEADER_H
            && !isOverWindowControl(mx, my, getBounds());
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;
        maybeFlushPending();
        if (dirty) { rebuildDisplay(); dirty = false; }

        AutismWindowLayout clamped = clampToScreen(this, new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, calcPanelH(), visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        currentPanelHeight = clamped.height;
        int ph = currentPanelHeight;

        int total = 0;
        for (DisplayRow r : displayRows) total += (r.type == RowType.GROUP) ? r.groupCount : 1;

        int bodyMx = mx;
        int bodyMy = my;
        if (ctxMenu.isMouseOver(mx, my)) {
            bodyMx = AutismOverlayManager.HOVER_BLOCKED_MOUSE;
            bodyMy = AutismOverlayManager.HOVER_BLOCKED_MOUSE;
        }

        String title = "Packet Logger";
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, ph, visible, collapsed);
        renderWindowFrame(ctx, bodyMx, bodyMy, bounds, title, collapsed, isDragging);
        boolean clipBody = beginWindowBodyClip(ctx, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(ctx, bounds, collapsed, isDragging);
            return;
        }

        try {

            int tabY = panelY + HEADER_H + 2;
            renderTabs(ctx, bodyMx, bodyMy, tabY, total);

            int filterY = tabY + tabHeight() + 2;
            renderFilterBar(ctx, bodyMx, bodyMy, filterY);

            int contentY = filterY + filterHeight() + 2;
            int contentEndY = contentY + contentAreaHeight();

            if (displayRows.isEmpty()) {
                drawUiText(ctx, "No packets matching filters", UiTone.MUTED, AutismColors.textDim(), panelX + 10, contentY + 6);
            } else {
                int contentHeight = displayRows.size() * lineHeight();
                int viewHeight = contentAreaHeight();
                int maxScroll = Math.max(0, contentHeight - viewHeight);
                scrollOffset = quantizeScrollOffset(scrollOffset, lineHeight(), maxScroll);
                contentScrollState.setTarget(scrollOffset, maxScroll);
                int drawScroll = contentScrollState.tick(delta, maxScroll);
                autismclient.gui.vanillaui.UiScissorStack.global().push(ctx,
                    autismclient.gui.vanillaui.UiBounds.of(panelX, contentY, PANEL_WIDTH, contentEndY - contentY));
                int drawBase = contentY - drawScroll;
                for (int i = 0; i < displayRows.size(); i++) {
                    int ey = drawBase + i * lineHeight();
                    if (ey + lineHeight() > contentY && ey < contentEndY) {
                        DisplayRow row = displayRows.get(i);
                        if (row.type == RowType.GROUP) renderGroup(ctx, row, ey, bodyMx, bodyMy);
                        else renderEntry(ctx, row.entry, ey, bodyMx, bodyMy);
                    }
                }
                autismclient.gui.vanillaui.UiScissorStack.global().pop(ctx);
                CompactScrollbar.Metrics scrollbarMetrics = getContentScrollbarMetrics();
                CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(bodyMx, bodyMy), scrollbarDragging);
            }

        } finally {
            endWindowBodyClip(ctx, clipBody);
            renderWindowInactiveOverlay(ctx, bounds, collapsed, isDragging);
        }

        if (ctxMenu.isOpen()) {

            ctx.nextStratum();
            ctxMenu.render(ctx, mx, my);
        }
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mx, int my, int y, int total) {
        int x = panelX + 4;
        for (Category cat : Category.values()) {
            int w = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, cat.label, 5, 32, 64);
            CompactOverlayControls.tab(ctx, textRenderer, x, y, w, tabHeight(), cat.label, activeTab == cat, mx, my);
            x += w + 2;
        }

        String summary = paused ? ("Paused  " + total) : Integer.toString(total);
        if (activeTab == Category.PAYLOAD) {
            AutismPayloadChannelSubscriptionManager.Status subscriptionStatus =
                AutismPayloadChannelSubscriptionManager.status();
            String channelStatus = subscriptionStatus == null ? "" : subscriptionStatus.shortLabel();
            if (!channelStatus.isBlank()) {
                summary = summary + "  " + channelStatus;
            }
        }
        int summaryColor = paused ? theme.color(UiTone.MUTED) : AutismColors.textSecondary();
        int summaryWidth = UiText.width(textRenderer, summary, theme.fontFor(UiTone.MUTED), summaryColor);
        int summaryX = panelX + PANEL_WIDTH - 6 - summaryWidth;
        int minSummaryX = x + 4;
        if (summaryX >= minSummaryX) {
            int summaryY = UiSizing.alignTextY(y, tabHeight(), theme.fontHeight(UiTone.MUTED), theme.bodyTextNudge());
            drawUiText(ctx, summary, UiTone.MUTED, summaryColor, summaryX, summaryY);
        }
    }

    private int contentAreaY() {
        return panelY + HEADER_H + 2 + tabHeight() + 2 + filterHeight() + 2;
    }

    private int contentAreaHeight() {
        int rawHeight = Math.max(0, currentPanelHeight - HEADER_H - tabHeight() - filterHeight() - 8 - blockedH());
        return alignViewportHeight(rawHeight, lineHeight());
    }

    private CompactScrollbar.Metrics getContentScrollbarMetrics() {
        int contentHeight = displayRows.size() * lineHeight();
        int viewHeight = contentAreaHeight();
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        return CompactScrollbar.compute(contentHeight, viewHeight, panelX + PANEL_WIDTH - 5, contentAreaY(), 3, viewHeight, contentScrollState.tick(0.0f, maxScroll));
    }

    private void renderFilterBar(GuiGraphicsExtractor ctx, int mx, int my, int y) {
        int gap = 2;
        int row1Y = y;
        int row2Y = y + filterRowHeight() + filterRowGap();

        int x = panelX + 4;
        String captureLabel = configurationOnly ? (paused ? "Auto OFF" : "Auto ON") : paused ? "Start" : "Stop";
        int captureW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, captureLabel, 5, 38, configurationOnly ? 66 : 54);
        if (paused) {
            drawOverlayToggleButton(ctx, x, row1Y, captureW, filterRowHeight(), captureLabel, false, "packet-logger:capture", mx, my);
        } else {
            drawOverlayToggleButton(ctx, x, row1Y, captureW, filterRowHeight(), captureLabel, true, "packet-logger:capture", mx, my);
        }
        x += captureW + gap;

        int clearW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Clear", 5, 34, 54);
        drawOverlayButton(ctx, x, row1Y, clearW, filterRowHeight(), "Clear", CompactOverlayButton.Variant.GHOST, true, mx, my);
        x += clearW + gap;
        int copyW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Copy", 5, 34, 52);
        drawOverlayButton(ctx, x, row1Y, copyW, filterRowHeight(), "Copy", CompactOverlayButton.Variant.GHOST, true, mx, my);
        x += copyW + gap;
        String grpLabel = groupingEnabled ? "Group" : "Ungrp";
        int groupW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, grpLabel, 5, 38, 58);
        drawOverlayToggleButton(ctx, x, row1Y, groupW, filterRowHeight(), grpLabel, groupingEnabled, "packet-logger:grouping", mx, my);
        x += groupW + gap;
        String bt = "Blocked";
        int blockedW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, bt, 5, 54, 72);
        drawOverlayButton(ctx, x, row1Y, blockedW, filterRowHeight(), bt, CompactOverlayButton.Variant.GHOST, true, mx, my);
        x += blockedW + gap;
        String channelsLabel = "Channels";
        int channelsW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, channelsLabel, 5, 58, 78);
        drawOverlayButton(ctx, x, row1Y, channelsW, filterRowHeight(), channelsLabel, CompactOverlayButton.Variant.GHOST, true, mx, my);
        x += channelsW + gap;

        int payloadW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Payload", 5, 44, 64);
        drawOverlayButton(ctx, x, row1Y, payloadW, filterRowHeight(), "Payload", CompactOverlayButton.Variant.GHOST, true, mx, my);

        x = panelX + 4;
        int sw = filterSearchFieldWidth();
        searchField.setX(x);
        searchField.setY(row2Y);
        searchField.setWidth(sw);
        searchField.setHeight(filterRowHeight());
        if (!Objects.equals(searchField.getText(), searchFilter)) {
            searchField.setText(searchFilter);
        }
        searchField.render(ctx, mx, my, 0.0f);
        x += sw + 3;

        String[] dirLabels = {"Both", "C2S", "S2C"};
        for (int d = 0; d < 3; d++) {
            int bw = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, dirLabels[d], 4, 30, 48);
            CompactOverlayControls.tab(ctx, textRenderer, x, row2Y, bw, filterRowHeight(), dirLabels[d], dirFilter == d, mx, my);
            x += bw + gap;
        }
        if (activeTab == Category.PAYLOAD) {
            String listenLabel = payloadFilteredOnly ? "Filtered" : "All";
            int listenW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, listenLabel, 4, 42, 62);
            CompactOverlayControls.tab(ctx, textRenderer, x, row2Y, listenW, filterRowHeight(), listenLabel, payloadFilteredOnly, mx, my);
        }
    }

    private void drawOverlayButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean active, int mx, int my) {
        CompactOverlayControls.action(ctx, textRenderer, x, y, w, h, label, variant, active, mx, my);
    }

    private void drawOverlayToggleButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean enabled, String animationKey, int mx, int my) {
        CompactOverlayControls.toggle(ctx, textRenderer, x, y, w, h, label, enabled, animationKey, mx, my);
    }

    private void renderGroup(GuiGraphicsExtractor ctx, DisplayRow row, int y, int mx, int my) {
        int x = panelX + 4;
        boolean exp = expandedGroups.contains(row.groupKey);
        boolean hov = mx >= panelX && mx <= panelX + PANEL_WIDTH && my >= y && my < y + lineHeight();
        AutismPayloadChannelListeners.Match listenerMatch = row.payloadSnapshot == null ? null : payloadListeners.match(row.payloadSnapshot, row.direction);
        if (listenerMatch != null) {
            ctx.fill(panelX + 2, y, panelX + PANEL_WIDTH - 4, y + lineHeight(), AutismColors.packetRowSelectedBg(hov));
            ctx.fill(panelX + 2, y, panelX + 4, y + lineHeight(), AutismColors.packetRowSelectedAccent());
        } else if (hov) {
            CompactSurfaces.row(ctx, panelX + 2, y, PANEL_WIDTH - 4, lineHeight(), true, false);
        }

        CompactControlGlyphs.drawChevron(
            ctx,
            x,
            y + 2,
            8,
            exp ? CompactControlGlyphs.ChevronDirection.DOWN : CompactControlGlyphs.ChevronDirection.RIGHT,
            hov ? 0xFFF5EEEE : 0xFFE7DADA,
            0xB83A1418,
            1.0f
        );
        x += 10;
        String arrow = row.direction.equals("C2S") ? ">" : "<";

        int color = row.direction.equals("C2S") ? 0xFF44CCFF : 0xFFFFAA44;
        drawUiText(ctx, arrow, UiTone.BODY, color, x, y + 1);
        x += 12;
        String displayName = row.groupKey.contains(":") ? row.groupKey.substring(row.groupKey.indexOf(':') + 1) : row.groupKey;
        String summary = row.payloadSnapshot == null ? "" : AutismPayloadSupport.summarizeForLogger(row.payloadSnapshot, true);
        String listenText = listenerMatch == null ? "" : " [" + listenerMatch.label() + "]";
        String line = displayName + " (x" + row.groupCount + ")" + listenText + (summary.isBlank() ? "" : " " + summary);
        int maxW = PANEL_WIDTH - (x - panelX) - 32;
        if (UiText.width(textRenderer, line, theme.fontFor(UiTone.BODY), color) > maxW) {
            line = UiText.trimToWidth(textRenderer, line, Math.max(1, maxW), theme.fontFor(UiTone.BODY), color);
        }
        drawUiText(ctx, line, UiTone.BODY, color, x, y + 1);

        int bx = panelX + PANEL_WIDTH - 28;
        boolean hb = mx >= bx && mx <= bx + 24 && my >= y && my < y + lineHeight();
        drawUiText(ctx, "BLK", UiTone.MUTED, hb ? 0xFFFF4444 : AutismColors.textDim(), bx, y + 1);
    }

    private void renderEntry(GuiGraphicsExtractor ctx, LogEntry e, int y, int mx, int my) {
        int x = panelX + 4;
        boolean hov = mx >= panelX && mx <= panelX + PANEL_WIDTH && my >= y && my < y + lineHeight();
        AutismPayloadChannelListeners.Match listenerMatch = payloadListenerMatch(e);
        if (listenerMatch != null) {
            ctx.fill(panelX + 2, y, panelX + PANEL_WIDTH - 4, y + lineHeight(), AutismColors.packetRowSelectedBg(hov));
            ctx.fill(panelX + 2, y, panelX + 4, y + lineHeight(), AutismColors.packetRowSelectedAccent());
        } else if (hov) {
            CompactSurfaces.row(ctx, panelX + 2, y, PANEL_WIDTH - 4, lineHeight(), true, false);
        }

        int color = e.direction.equals("C2S") ? 0xFF44CCFF : 0xFFFFAA44;
        drawUiText(ctx, e.direction.equals("C2S") ? ">" : "<", UiTone.BODY, color, x, y + 1);
        x += 12;
        String time = TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs));
        drawUiText(ctx, time, UiTone.MUTED, METADATA_TIME_COLOR, x, y + 1);
        x += UiText.width(textRenderer, time, theme.fontFor(UiTone.MUTED), METADATA_TIME_COLOR) + 4;
        String tick = "T" + e.gameTick;
        drawUiText(ctx, tick, UiTone.MUTED, METADATA_TICK_COLOR, x, y + 1);
        x += UiText.width(textRenderer, tick, theme.fontFor(UiTone.MUTED), METADATA_TICK_COLOR) + 4;

        int maxW = PANEL_WIDTH - (x - panelX) - 30;
        String name = e.shortName;
        if (e.payloadSnapshot != null) {
            String summary = AutismPayloadSupport.summarizeForLogger(e.payloadSnapshot, true);
            if (!summary.isBlank()) {
                name = name + " " + summary;
            }
        }
        if (listenerMatch != null) {
            name = name + " [" + listenerMatch.label() + "]";
        }
        if (UiText.width(textRenderer, name, theme.fontFor(UiTone.BODY), AutismColors.textPrimary()) > maxW) {
            name = UiText.trimToWidth(textRenderer, name, Math.max(1, maxW), theme.fontFor(UiTone.BODY), AutismColors.textPrimary());
        }
        drawUiText(ctx, name, UiTone.BODY, color, x, y + 1);

        int bx = panelX + PANEL_WIDTH - 16;
        boolean hb = mx >= bx && mx <= bx + 12 && my >= y && my < y + lineHeight();
        drawUiText(ctx, "=", UiTone.MUTED, hb ? 0xFFFFDD55 : AutismColors.textDim(), bx, y + 1);
    }

    private void renderBlocked(GuiGraphicsExtractor ctx, int mx, int my, int startY, int endY) {
        CompactSurfaces.divider(ctx, panelX + 4, startY, PANEL_WIDTH - 8);
        int y = startY + 3;
        boolean hh = mx >= panelX + 4 && mx <= panelX + 150 && my >= y && my < y + lineHeight();
        int blockedColor = hh ? AutismTheme.recolor(0xFFFF8888, AutismTheme.Channel.DANGER) : AutismTheme.recolor(0xFFFF6666, AutismTheme.Channel.DANGER);
        UiRenderer.chevron(ctx, UiBounds.of(panelX + 6, y + 1, 9, 9), blockedExpanded, blockedColor);
        drawUiText(ctx, "Blocked (" + blockedNames.size() + ")", UiTone.LABEL, blockedColor, panelX + 17, y + 1);
        int ubX = panelX + PANEL_WIDTH - 58;
        boolean hua = mx >= ubX && mx <= ubX + 54 && my >= y && my < y + lineHeight();
        drawUiText(ctx, "CLR ALL", UiTone.MUTED, hua ? 0xFF44FF44 : AutismColors.textSecondary(), ubX, y + 1);
        int rstX = ubX - 44;
        boolean hRst = mx >= rstX && mx <= rstX + 40 && my >= y && my < y + lineHeight();
        drawUiText(ctx, "RESET", UiTone.MUTED, hRst ? 0xFFFFD555 : AutismColors.textSecondary(), rstX, y + 1);

        if (!blockedExpanded) return;
        y += lineHeight();
        for (String name : blockedNames) {
            if (y + lineHeight() > endY) break;
            drawUiText(ctx, "  " + UiText.trimToWidth(textRenderer, name, Math.max(1, PANEL_WIDTH - 50), theme.fontFor(UiTone.BODY), 0xFFAA6666), UiTone.BODY, 0xFFAA6666, panelX + 8, y + 1);
            int ux = panelX + PANEL_WIDTH - 28;
            boolean hu = mx >= ux && mx <= ux + 24 && my >= y && my < y + lineHeight();
            drawUiText(ctx, "UB", UiTone.MUTED, hu ? 0xFF44FF44 : AutismColors.textSecondary(), ux, y + 1);
            y += lineHeight();
        }
    }

    private String[] getCtxItems(LogEntry e) {
        boolean isC2S = e != null && "C2S".equalsIgnoreCase(e.direction);
        boolean isPayload = e != null && e.isPayload;
        String[] base;
        if (configurationOnly) {
            if (isC2S && isPayload) {
                base = new String[]{"Block", "Queue", "Edit Payload", "+PAYLOAD", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
            } else {
                base = isC2S
                    ? new String[]{"Block", "Queue", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"}
                    : new String[]{"Block", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
            }
        } else if (isC2S && isPayload) {
            base = new String[]{"Block", "Queue", "Replay", "Send", "Edit Payload", "+PAYLOAD", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
        } else {
            base = isC2S
                ? new String[]{"Block", "Queue", "Replay", "Send", "+SEND", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"}
                : new String[]{"Block", "+WAIT", "+ Filter", "- Filter", "Copy", "Inspect"};
        }

        if (e != null && e.packetRef != null && !autismclient.util.AutismLiteVariant.enabled()) {
            String[] withShare = java.util.Arrays.copyOf(base, base.length + 1);
            withShare[base.length] = "Share to Lobby";
            return withShare;
        }
        return base;
    }

    private void openCtxMenu(LogEntry entry, int mouseX, int mouseY) {
        ctxMenu.open(mouseX, mouseY, entry);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (ctxMenu.handleClick(mouseX, mouseY, button, (entry, action, index) -> executeCtxAction(action, entry))) return true;

        if (button != 0 && button != 1) return false;

        if (mouseY >= panelY && mouseY <= panelY + HEADER_H && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, calcPanelH(), visible, collapsed);
            if (isOverCloseButton(mouseX, mouseY, bounds)) { setVisible(false); return true; }
            if (button == 0) {
                isDragging = true;
                headerDragMoved = false;
                dragOffX = mouseX - panelX;
                dragOffY = mouseY - panelY;
            }
            return true;
        }
        if (collapsed) return false;

        int tabY = panelY + HEADER_H + 2;
        int filterY = tabY + tabHeight() + 2;
        int contentY = filterY + filterHeight() + 2;

        if (mouseY >= tabY && mouseY < tabY + tabHeight() && button == 0) {
            int x = panelX + 4;
            for (Category cat : Category.values()) {
                int w = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, cat.label, 5, 32, 64);
                if (mouseX >= x && mouseX < x + w) { activeTab = cat; dirty = true; scrollOffset = 0; return true; }
                x += w + 2;
            }
            return true;
        }

        if (mouseY >= filterY && mouseY < filterY + filterHeight() && button == 0) {
            return handleFilterClick(mouseX, mouseY, filterY);
        }

        if (searchField.isFocused()) searchField.setFocused(false);

        int contentEndY = contentY + contentAreaHeight();
        if (mouseY >= contentY && mouseY < contentEndY) {
            if (button == 0) {
                CompactScrollbar.Metrics scrollbarMetrics = getContentScrollbarMetrics();
                if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains((int) mouseX, (int) mouseY)) {
                    scrollbarDragging = true;
                    scrollbarGrabOffset = Math.max(0, (int) mouseY - scrollbarMetrics.thumbY());
                    scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int) mouseY, scrollbarGrabOffset), lineHeight(), scrollbarMetrics.maxScroll());
                    contentScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
                    return true;
                }
            }
            return handleContentClick(mouseX, mouseY, contentY, button);
        }

        return false;
    }

    private boolean handleFilterClick(double mouseX, double mouseY, int y) {
        int gap = 2;
        int row1Y = y;
        int row2Y = y + filterRowHeight() + filterRowGap();
        int x = panelX + 4;
        String captureLabel = configurationOnly ? (paused ? "Auto OFF" : "Auto ON") : paused ? "Start" : "Stop";
        int captureW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, captureLabel, 5, 38, configurationOnly ? 66 : 54);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + captureW) {
            setPaused(!paused);
            return true;
        }
        x += captureW + gap;

        int clrW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Clear", 5, 34, 54);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + clrW) { clearAll(); return true; }
        x += clrW + gap;
        int cpW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Copy", 5, 34, 52);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + cpW) { copyToClipboard(); return true; }
        x += cpW + gap;
        int grpW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, groupingEnabled ? "Group" : "Ungrp", 5, 38, 58);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + grpW) { groupingEnabled = !groupingEnabled; dirty = true; return true; }
        x += grpW + gap;
        String bt = "Blocked";
        int bw = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, bt, 5, 54, 72);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + bw) { openBlockedListOverlay(); return true; }
        x += bw + gap;
        String channelsLabel = "Channels";
        int channelsW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, channelsLabel, 5, 58, 78);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + channelsW) { openPayloadListenerOverlay(); return true; }
        x += channelsW + gap;
        int payloadW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, "Payload", 5, 44, 64);
        if (mouseY >= row1Y && mouseY < row1Y + filterRowHeight() && mouseX >= x && mouseX < x + payloadW) { openCleanPayloadEditor(); return true; }

        x = panelX + 4;
        int sw = filterSearchFieldWidth();
        if (mouseY >= row2Y && mouseY < row2Y + filterRowHeight() && mouseX >= x && mouseX < x + sw) {
            searchField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        x += sw + 3;

        String[] dirLabels = {"Both", "C2S", "S2C"};
        for (int d = 0; d < 3; d++) {
            int dirW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, dirLabels[d], 4, 30, 48);
            if (mouseY >= row2Y && mouseY < row2Y + filterRowHeight() && mouseX >= x && mouseX < x + dirW) { dirFilter = d; dirty = true; return true; }
            x += dirW + gap;
        }
        if (activeTab == Category.PAYLOAD) {
            String listenLabel = payloadFilteredOnly ? "Filtered" : "All";
            int listenW = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, listenLabel, 4, 42, 62);
            if (mouseY >= row2Y && mouseY < row2Y + filterRowHeight() && mouseX >= x && mouseX < x + listenW) {
                payloadFilteredOnly = !payloadFilteredOnly;
                dirty = true;
                return true;
            }
        }
        searchField.setFocused(false);
        return true;
    }

    private boolean handleContentClick(double mouseX, double mouseY, int contentY, int button) {
        int idx = (int) ((mouseY - contentY + contentScrollState.tick(0.0f, Math.max(0, displayRows.size() * lineHeight() - contentAreaHeight()))) / lineHeight());
        if (idx < 0 || idx >= displayRows.size()) return false;
        DisplayRow row = displayRows.get(idx);

        if (row.type == RowType.GROUP) {
            int bx = panelX + PANEL_WIDTH - 28;
            if (button == 0 && mouseX >= bx && mouseX <= bx + 24 && row.packetClass != null) {
                @SuppressWarnings("unchecked")
                String n = AutismPacketNamer.getFriendlyName((Class<? extends Packet<?>>) row.packetClass);
                blockPacketName(n); return true;
            }
            if (button == 0) {
                if (expandedGroups.contains(row.groupKey)) expandedGroups.remove(row.groupKey);
                else expandedGroups.add(row.groupKey);
                dirty = true; return true;
            }
        }

        if (row.type == RowType.ENTRY && row.entry != null) {

            int menuIconX = panelX + PANEL_WIDTH - 16;
            if (button == 1 || (button == 0 && mouseX >= menuIconX)) {
                openCtxMenu(row.entry, (int) mouseX, (int) mouseY);
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private void executeCtxAction(String action, LogEntry e) {
        if (configurationOnly && ("Send".equals(action) || "Replay".equals(action))) return;
        switch (action) {
            case "Block":
                blockPacketName(e.shortName);
                AutismClientMessaging.sendPrefixed("Blocked + purged from logger: " + e.shortName);
                break;
            case "Queue":
                AutismPacketEntryActions.queue(e);
                break;
            case "Send":
                AutismPacketEntryActions.directSend(e);
                break;
            case "+SEND":
                AutismPacketEntryActions.addSendActionToVisibleMacro(e);
                break;
            case "+WAIT":
                AutismPacketEntryActions.addWaitActionToVisibleMacro(e);
                break;
            case "Edit Payload":
                AutismPacketEntryActions.openPayloadEditor(e);
                break;
            case "+PAYLOAD":
                AutismPacketEntryActions.addPayloadActionToVisibleMacro(e);
                break;
            case "+ Filter": {
                AutismSharedState shared = AutismSharedState.get();
                Class<? extends Packet<?>> pktCls = (Class<? extends Packet<?>>) e.packetClass;
                boolean added;
                if (e.direction.equals("C2S")) {
                    added = shared.getC2SPackets().add(pktCls);
                } else {
                    added = shared.getS2CPackets().add(pktCls);
                }
                shared.setUseCustomPackets(true);
                AutismClientMessaging.sendPrefixed(added
                    ? "Added to custom " + e.direction + " filter: " + e.shortName
                    : "Already present in custom " + e.direction + " filter: " + e.shortName);
                break;
            }
            case "- Filter": {
                AutismSharedState shared = AutismSharedState.get();
                Class<? extends Packet<?>> pktCls = (Class<? extends Packet<?>>) e.packetClass;
                boolean removed;
                if (e.direction.equals("C2S")) {
                    removed = shared.getC2SPackets().remove(pktCls);
                } else {
                    removed = shared.getS2CPackets().remove(pktCls);
                }
                AutismClientMessaging.sendPrefixed(removed
                    ? "Removed from custom " + e.direction + " filter: " + e.shortName
                    : "Not present in custom " + e.direction + " filter: " + e.shortName);
                break;
            }
            case "Replay":
                if (e.packetRef != null && e.direction.equals("C2S")) {
                    AutismPacketEntryActions.directSend(e);
                }
                break;
            case "Copy": {
                String line = e.direction + " " + TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs))
                        + " T" + e.gameTick + " " + e.shortName;
                MC.keyboardHandler.setClipboard(line);
                AutismNotifications.copied("Copied packet info.");
                break;
            }
            case "Inspect": {
                inspectOverlay.open(e, panelX + PANEL_WIDTH + 10, panelY + 8);
                break;
            }
            case "Share to Lobby": {
                shareEntryToLobby(e);
                break;
            }
        }
    }

    private void shareEntryToLobby(LogEntry e) {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        autismclient.util.mm.MatchmakingManager mm = autismclient.util.mm.MatchmakingManager.get();
        if (!mm.inLobby()) {
            AutismClientMessaging.sendPrefixed("§cNot in a Matchmaking lobby. Open Matchmaking and join one first.");
            return;
        }
        if (e == null || e.packetRef == null) {
            AutismClientMessaging.sendPrefixed("§cThis entry has no replayable packet to share.");
            return;
        }
        String data = AutismClipboardHelper.serializeQueueToBase64(
            java.util.List.of(new AutismSharedState.QueuedPacket(e.packetRef, 0)));
        if (data == null) {
            AutismClientMessaging.sendPrefixed("§cFailed to serialize packet for sharing.");
            return;
        }
        autismclient.util.mm.msg.MmMessages.PacketOffer offer = new autismclient.util.mm.msg.MmMessages.PacketOffer();
        offer.friendlyName = e.shortName;
        offer.direction = e.direction;
        offer.data = data;
        mm.offerPacket(offer);
        AutismNotifications.show("Shared packet to lobby: " + e.shortName, 0xFF35D873);
    }

    private boolean handleBlockedClick(double mouseX, double mouseY, int contentEndY) {
        int y = contentEndY + 3;
        if (mouseY >= y && mouseY < y + lineHeight()) {
            int ubX = panelX + PANEL_WIDTH - 58;
            if (mouseX >= ubX && mouseX <= ubX + 54) { clearBlocked(); return true; }
            int rstX = ubX - 44;
            if (mouseX >= rstX && mouseX <= rstX + 40) { resetBlockedToDefault(); return true; }
            blockedExpanded = !blockedExpanded; return true;
        }
        if (blockedExpanded) {
            int ey = y + lineHeight();
            int ux = panelX + PANEL_WIDTH - 28;
            for (String name : new ArrayList<>(blockedNames)) {
                if (mouseX >= ux && mouseX <= ux + 24 && mouseY >= ey && mouseY < ey + lineHeight()) {
                    unblockName(name); return true;
                }
                ey += lineHeight();
            }
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (scrollbarDragging && b == 0) {
            CompactScrollbar.Metrics scrollbarMetrics = getContentScrollbarMetrics();
            scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int) my, scrollbarGrabOffset), lineHeight(), scrollbarMetrics.maxScroll());
            contentScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
            return true;
        }
        if (isDragging && b == 0) {
            AutismWindowLayout nextBounds = clampToScreen(this,
                new AutismWindowLayout((int) (mx - dragOffX), (int) (my - dragOffY),
                    PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed));
            if (nextBounds.x != panelX || nextBounds.y != panelY) {
                headerDragMoved = true;
            }
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            return true;
        }
        if (searchField.mouseDragged(mx, my, b, dx, dy)) return true;
        return false;
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (b == 0 && scrollbarDragging) { scrollbarDragging = false; return true; }
        if (b == 0 && isDragging) {

            isDragging = false;
            headerDragMoved = false;
            saveLayout();
            return true;
        }
        if (searchField.mouseReleased(mx, my, b)) return true;
        return false;
    }
    @Override public boolean mouseScrolled(double mx, double my, double amt) {
        if (!visible || collapsed) return false;
        int totalH = displayRows.size() * lineHeight();
        int visH = contentAreaHeight();
        int maxScroll = Math.max(0, totalH - visH);
        scrollOffset = quantizeScrollOffset(scrollOffset - (int)Math.round(amt * lineHeight() * 3.0), lineHeight(), maxScroll);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (!visible || collapsed) return false;
        return searchField.isFocused() && searchField.keyPressed(new KeyEvent(key, scan, mods));
    }
    @Override public boolean charTyped(char c, int mods) {
        if (!visible || collapsed) return false;
        return searchField.isFocused() && searchField.charTyped(new CharacterEvent(c));
    }

    private synchronized List<LogEntry> getActiveBuffer() {
        switch (activeTab) {
            case INVENTORY: return new ArrayList<>(bufInventory);
            case MOVEMENT: return new ArrayList<>(bufMovement);
            case PAYLOAD: return new ArrayList<>(bufPayload);
            case ALL: default: return new ArrayList<>(bufAll);
        }
    }

    private AutismPayloadChannelListeners.Match payloadListenerMatch(LogEntry entry) {
        if (entry == null || !entry.isPayload || entry.payloadSnapshot == null) return null;
        return payloadListeners.match(entry.payloadSnapshot, entry.direction);
    }

    private String entrySearchKey(LogEntry entry) {
        if (entry == null) return "";
        AutismPayloadChannelListeners.Match match = payloadListenerMatch(entry);
        if (match == null) return entry.searchKey();
        return entry.searchKey() + " " + match.searchText();
    }

    private void rebuildDisplay() {
        List<LogEntry> source = getActiveBuffer();
        if (source == null) source = new ArrayList<>();

        String ls = searchFilter.toLowerCase(Locale.ROOT);
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry e : source) {
            if (isBlockedClass(e.packetClass)) continue;
            if (activeTab == Category.PAYLOAD && payloadFilteredOnly && payloadListenerMatch(e) == null) continue;
            if (!ls.isEmpty() && !entrySearchKey(e).contains(ls)) continue;

            if (dirFilter == 1 && !e.direction.equals("C2S")) continue;
            if (dirFilter == 2 && !e.direction.equals("S2C")) continue;
            filtered.add(e);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> dirs = new LinkedHashMap<>();
        Map<String, Class<?>> classes = new LinkedHashMap<>();
        for (LogEntry e : filtered) {
            String key = e.groupKey;
            counts.merge(key, 1, Integer::sum);
            dirs.put(key, e.direction);
            classes.put(key, e.packetClass);
        }

        Set<String> groupedKeys = new HashSet<>();
        if (groupingEnabled) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() >= GROUP_THRESHOLD) groupedKeys.add(entry.getKey());
            }
        }

        List<LogEntry> reversed = new ArrayList<>(filtered);
        java.util.Collections.reverse(reversed);

        List<DisplayRow> rows = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        for (LogEntry e : reversed) {
            String key = e.groupKey;
            if (groupedKeys.contains(key) && added.add(key)) {
                DisplayRow h = new DisplayRow();
                h.type = RowType.GROUP; h.groupKey = key; h.groupCount = counts.get(key);
                h.direction = dirs.get(key); h.packetClass = classes.get(key);
                h.payloadSnapshot = e.payloadSnapshot;
                rows.add(h);
                if (expandedGroups.contains(key)) {
                    for (LogEntry child : reversed) {
                        if (child.groupKey.equals(key)) {
                            DisplayRow r = new DisplayRow(); r.type = RowType.ENTRY; r.entry = child; rows.add(r);
                        }
                    }
                }
            }
        }

        for (LogEntry e : reversed) {
            if (!groupedKeys.contains(e.groupKey)) {
                DisplayRow r = new DisplayRow(); r.type = RowType.ENTRY; r.entry = e; rows.add(r);
            }
        }

        displayRows = rows;
        clampScrollOffset();
    }

    private void clampScrollOffset() {
        int totalH = displayRows.size() * lineHeight();
        int visH = currentPanelHeight - HEADER_H - tabHeight() - filterHeight() - 8 - blockedH();
        int maxScroll = Math.max(0, totalH - visH);
        scrollOffset = quantizeScrollOffset(scrollOffset, lineHeight(), maxScroll);
    }

    private int calcPanelH() {
        int rc = Math.min(displayRows.size(), maxVisibleRows());
        return Math.max(PANEL_HEIGHT, HEADER_H + tabHeight() + filterHeight() + 8 + Math.max(rc * lineHeight(), 24) + blockedH() + 4);
    }

    private int blockedH() {
        return 0;
    }

    private void openBlockedListOverlay() {

        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        blockedListOverlay.panelX = Math.max(4, (sw - blockedListOverlay.panelWidth) / 2);
        blockedListOverlay.panelY = Math.max(4, (sh - blockedListOverlay.panelHeight) / 2);
        blockedListOverlay.setVisible(true);
        blockedListOverlay.rebuildRows();
        AutismOverlayManager.get().bringToFront(blockedListOverlay);
    }

    private void openPayloadListenerOverlay() {
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        payloadListeners.load();
        payloadRegistrations.load();
        payloadListenerOverlay.panelX = Math.max(4, (sw - payloadListenerOverlay.panelWidth) / 2);
        payloadListenerOverlay.panelY = Math.max(4, (sh - payloadListenerOverlay.panelHeight) / 2);
        payloadListenerOverlay.setVisible(true);
        payloadListenerOverlay.rebuildRows();
        AutismOverlayManager.get().bringToFront(payloadListenerOverlay);
    }

    private void openCleanPayloadEditor() {
        autismclient.gui.macro.editor.ActionEditorOverlay editor =
            autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
        editor.openStandalonePayloadEditor(new autismclient.util.macro.PayloadAction());
        AutismOverlayManager.get().bringToFront(editor);
    }

    private final class PayloadChannelListenerOverlay extends AutismOverlayBase {
        private final AutismChatField search = new AutismChatField(MC, textRenderer, 0, 0, 120, 16, false);
        private final AutismChatField customPattern = new AutismChatField(MC, textRenderer, 0, 0, 112, 16, false);
        private final ScrollState rowsScroll = new ScrollState();
        private final List<ListenerRow> rows = new ArrayList<>();
        private int rowScroll;
        private boolean draggingScroll;
        private int scrollGrabOffset;
        private boolean draggingWindow;
        private double dragOffsetX;
        private double dragOffsetY;
        private boolean enabledOnly;
        private boolean channelConfigDirty;
        private final Set<String> expandedPayloadFamilies = new HashSet<>();
        private int editingRuleIndex = -1;
        private int editingRegistrationIndex = -1;
        private Map<String, PayloadChannelCategoryRow> payloadCategoryCache;
        private List<PayloadChannelCategoryRow> sortedPayloadCategoryCache;
        private final Set<String> allPresetKeysCache = new HashSet<>();
        private final Set<String> enabledPresetKeysCache = new HashSet<>();
        private final Set<String> enabledRegistrationChannelsCache = new LinkedHashSet<>();
        private final Map<String, Integer> groupPresetCountCache = new HashMap<>();
        private final Map<String, Integer> groupExactCountCache = new HashMap<>();
        private final Map<String, AutismPayloadChannelListeners.Preset> presetByExactChannelCache = new HashMap<>();
        private final Map<AutismPayloadChannelListeners.Preset, String> presetSearchTextCache = new IdentityHashMap<>();
        private final Map<String, AutismPayloadChannelSubscriptionManager.RegistrationImpact> impactByPatternCache = new HashMap<>();
        private int enabledHighlightCountCache;
        private String pendingRegistrationLabelCache = "";
        private int cachedRegistrationLimit = 96;
        private boolean registrationWarningOpen;
        private long registrationWarningOpenedAtMs;
        private boolean searchRowsDirty;

        private static final int ROW_H = 16;
        private static final int CUSTOM_ADD_W = 52;
        private static final int CUSTOM_CANCEL_W = 52;
        private static final int CUSTOM_GAP = 4;
        private static final int LIST_PAD_X = 6;
        private static final int LIST_FRAME_PAD = 2;
        private static final int SCROLLBAR_GUTTER = 7;
        private static final int CHANNEL_COL_W = 58;
        private static final int STUDY_COL_W = 36;
        private static final int STATUS_COL_W = 30;
        private static final int STATUS_BADGE_H = 12;
        private static final int REMOVE_COL_W = 14;
        private static final int DEFAULTS_W = 88;
        private static final int KNOWN_ON_W = 62;
        private static final int CAPTURE_W = 56;
        private static final int FILTER_W = 62;
        private static final int APPLY_W = 46;
        private static final int REVERT_W = 50;
        private static final int ALL_OFF_W = 50;
        private static final int REGISTRATION_LOCK_W = 122;
        private static final int REGISTRATION_WARNING_DELAY_MS = 5000;
        private static final int REGISTRATION_WARNING_PAD_X = 14;
        private static final int REGISTRATION_WARNING_PAD_Y = 10;
        private static final int CONTROL_GAP = 4;

        PayloadChannelListenerOverlay() {
            super("PacketLoggerPayloadChannels", 492, 336);
            this.panelX = 270;
            this.panelY = 58;
            this.visible = false;
            search.setPlaceholder(Component.literal("Search presets/channels..."));
            search.setMaxLength(120);
            search.setChangedListener(value -> searchRowsDirty = true);
            customPattern.setPlaceholder(Component.literal("channel or wildcard: shopgui:main / oraxen:*"));
            customPattern.setMaxLength(96);
        }

        private int searchY() { return panelY + HEADER_H + 5; }
        private int customY() { return searchY() + 18; }
        private int controlsY() { return customY() + 18; }
        private int statusY() { return controlsY() + 18; }
        private int registrationButtonY() { return statusY() - 1; }
        private int listTopY() { return statusY() + 18; }
        private int listLeft() { return panelX + LIST_PAD_X; }
        private int listRight() { return panelX + panelWidth - LIST_PAD_X - SCROLLBAR_GUTTER; }
        private int listWidth() { return Math.max(20, listRight() - listLeft()); }
        private int listFrameLeft() { return listLeft() - LIST_FRAME_PAD; }
        private int listFrameRight() { return panelX + panelWidth - 5; }
        private int listenerListHeight() {
            int raw = Math.max(ROW_H, panelY + panelHeight - listTopY() - 8);
            return Math.max(ROW_H, alignViewportHeight(raw, ROW_H));
        }

        private boolean isEditingCustomEntry() {
            return editingRuleIndex >= 0 || editingRegistrationIndex >= 0;
        }

        private int customPatternWidth() {
            int buttons = CUSTOM_ADD_W + (isEditingCustomEntry() ? CUSTOM_GAP + CUSTOM_CANCEL_W : 0);
            return Math.max(120, panelWidth - 12 - buttons - CUSTOM_GAP);
        }

        private void clearCustomEditState() {
            editingRuleIndex = -1;
            editingRegistrationIndex = -1;
            customPattern.setText("");
            customPattern.setPlaceholder(Component.literal("exact channel or highlight wildcard"));
            customPattern.setFocused(false);
        }

        private void commitPayloadChannelConfigInMemory() {
            payloadListeners.commit(false);
            payloadRegistrations.commit(false);
        }

        private void markPayloadChannelConfigDirty() {
            channelConfigDirty = true;
            dirty = true;
        }

        private void savePendingPayloadChannelConfig() {
            if (!channelConfigDirty) return;
            commitPayloadChannelConfigInMemory();
            AutismConfig.getGlobal().save();
            channelConfigDirty = false;
        }

        @Override
        public void setVisible(boolean v) {
            if (!v) {
                savePendingPayloadChannelConfig();
                clearCustomEditState();
                registrationWarningOpen = false;
            }
            super.setVisible(v);
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
            if (!visible) return;
            if (searchRowsDirty) {
                searchRowsDirty = false;
                rebuildRows();
            }
            AutismWindowLayout bounds = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
            panelX = bounds.x;
            panelY = bounds.y;
            renderWindowFrame(ctx, mx, my, bounds, "Payload Channels", collapsed, false);
            boolean clip = beginWindowBodyClip(ctx, bounds, collapsed);
            if (!clip) {
                renderWindowInactiveOverlay(ctx, bounds, collapsed, false);
                return;
            }
            try {
                int x = panelX + 6;
                int y = searchY();
                search.setX(x);
                search.setY(y);
                search.setWidth(Math.max(80, panelWidth - 12));
                search.setHeight(16);
                search.render(ctx, mx, my, delta);

                y = customY();
                int customW = customPatternWidth();
                customPattern.setX(x);
                customPattern.setY(y);
                customPattern.setWidth(customW);
                customPattern.setHeight(16);
                customPattern.render(ctx, mx, my, delta);
                int addX = x + customW + CUSTOM_GAP;
                CompactOverlayControls.action(ctx, textRenderer, addX, y, CUSTOM_ADD_W, 16,
                    isEditingCustomEntry() ? "Save" : "Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my);
                if (isEditingCustomEntry()) {
                    int cancelX = addX + CUSTOM_ADD_W + CUSTOM_GAP;
                    CompactOverlayControls.action(ctx, textRenderer, cancelX, y, CUSTOM_CANCEL_W, 16,
                        "Cancel", CompactOverlayButton.Variant.GHOST, true, mx, my);
                }

                y = controlsY();
                int defaultsX = x;
                int knownOnX = defaultsX + DEFAULTS_W + CONTROL_GAP;
                int captureX = knownOnX + KNOWN_ON_W + CONTROL_GAP;
                int filterX = captureX + CAPTURE_W + CONTROL_GAP;
                int applyX = filterX + FILTER_W + CONTROL_GAP;
                int revertX = applyX + APPLY_W + CONTROL_GAP;
                int allOffX = revertX + REVERT_W + CONTROL_GAP;
                CompactOverlayControls.action(ctx, textRenderer, defaultsX, y, DEFAULTS_W, 16, "Recommended",
                    payloadListeners.isDefaultRecommendedProfileActive() ? CompactOverlayButton.Variant.GHOST : CompactOverlayButton.Variant.SUCCESS,
                    true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, knownOnX, y, KNOWN_ON_W, 16, "Known On", CompactOverlayButton.Variant.SUCCESS, true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, captureX, y, CAPTURE_W, 16, "Capture", CompactOverlayButton.Variant.SUCCESS, true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, filterX, y, FILTER_W, 16, enabledOnly ? "Enabled" : "All",
                    enabledOnly ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST, true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, applyX, y, APPLY_W, 16, "Apply", CompactOverlayButton.Variant.SUCCESS, true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, revertX, y, REVERT_W, 16, "Revert", CompactOverlayButton.Variant.GHOST, true, mx, my);
                CompactOverlayControls.action(ctx, textRenderer, allOffX, y, ALL_OFF_W, 16, "All Off", CompactOverlayButton.Variant.GHOST, true, mx, my);
                AutismPayloadChannelSubscriptionManager.Status subscriptionStatus =
                    AutismPayloadChannelSubscriptionManager.status();
                String sub = subscriptionStatus == null ? "Registered 0/96" : subscriptionStatus.shortLabel();
                if (subscriptionStatus != null && !subscriptionStatus.locked()
                    && subscriptionStatus.registeredCount() > 0 && !subscriptionStatus.lastSendSucceeded()) {
                    sub += " pending";
                }
                boolean registrationUnlocked = AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked();
                int lockX = panelX + panelWidth - 8 - REGISTRATION_LOCK_W;
                CompactOverlayControls.action(ctx, textRenderer, lockX, registrationButtonY(), REGISTRATION_LOCK_W, 16,
                    registrationUnlocked ? "Registration : On" : "Registration : Off",
                    registrationUnlocked ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.GHOST,
                    true, mx, my);
                String count = enabledHighlightCountCache + " highlight  " + sub + pendingRegistrationLabelCache;
                int countX = x;
                int countMaxW = Math.max(1, lockX - countX - 5);
                drawUiText(ctx, UiText.trimToWidth(textRenderer, count, countMaxW, theme.fontFor(UiTone.MUTED), AutismColors.textSecondary()),
                    UiTone.MUTED, AutismColors.textSecondary(), countX, statusY() + 3);

                int listTop = listTopY();
                int listH = listenerListHeight();
                int contentH = rows.size() * ROW_H;
                int maxScroll = Math.max(0, contentH - listH);
                rowScroll = quantizeScrollOffset(rowScroll, ROW_H, maxScroll);
                rowsScroll.setTarget(rowScroll, maxScroll);
                int drawScroll = rowsScroll.tick(delta, maxScroll);
                UiRenderer.frame(ctx, UiBounds.of(listFrameLeft(), listTop - 2, Math.max(1, listFrameRight() - listFrameLeft()), listH + 4),
                    AutismColors.listBg(), AutismColors.subPanelBorder());
                autismclient.gui.vanillaui.UiScissorStack.global().push(ctx,
                    autismclient.gui.vanillaui.UiBounds.of(listLeft(), listTop, listWidth(), listH));
                int base = listTop - drawScroll;
                int firstVisible = Math.max(0, drawScroll / ROW_H);
                int lastVisible = Math.min(rows.size() - 1, (drawScroll + listH - 1) / ROW_H);
                for (int i = firstVisible; i <= lastVisible; i++) {
                    int ry = base + i * ROW_H;
                    renderListenerRow(ctx, rows.get(i), ry, mx, my, i);
                }
                autismclient.gui.vanillaui.UiScissorStack.global().pop(ctx);
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, listH, panelX + panelWidth - 7, listTop, 3, listH, drawScroll);
                CompactScrollbar.draw(ctx, metrics, metrics.contains(mx, my), draggingScroll);
                if (registrationWarningOpen) {
                    renderRegistrationWarning(ctx, mx, my);
                }
            } finally {
                endWindowBodyClip(ctx, clip);
                renderWindowInactiveOverlay(ctx, bounds, collapsed, false);
            }
        }

        private void renderRegistrationWarning(GuiGraphicsExtractor ctx, int mx, int my) {
            int bodyTop = panelY + HEADER_H;
            ctx.fill(panelX + 4, bodyTop + 2, panelX + panelWidth - 4, panelY + panelHeight - 5, 0xCC050507);
            RegistrationWarningBounds bounds = registrationWarningBounds();
            int x = bounds.x;
            int y = bounds.y;
            int w = bounds.w;
            int h = bounds.h;
            UiRenderer.popup(ctx, UiBounds.of(x, y, w, h), 0xFF120D0F, AutismTheme.recolor(0xFFFF5B5B, AutismTheme.Channel.OUTLINE), AutismTheme.recolor(0xFFFF5B5B, AutismTheme.Channel.OUTLINE));
            drawCenteredUiText(ctx, "Payload Registration Warning", UiTone.LABEL, AutismTheme.recolor(0xFFFFB4B4, AutismTheme.Channel.DANGER), x, w, y + REGISTRATION_WARNING_PAD_Y);
            drawCenteredUiText(ctx, "Servers can see every registered channel.", UiTone.BODY, AutismColors.textPrimary(), x, w, y + 30);
            drawCenteredUiText(ctx, "This can identify this client or selected plugins.", UiTone.BODY, AutismColors.textPrimary(), x, w, y + 43);
            drawCenteredUiText(ctx, "Passive logging and decoding still work while locked.", UiTone.MUTED, AutismColors.textSecondary(), x, w, y + 60);
            int cancelW = 72;
            int acceptW = 92;
            int buttonY = bounds.buttonY;
            int buttonsW = cancelW + 6 + acceptW;
            int cancelX = x + (w - buttonsW) / 2;
            int acceptX = cancelX + cancelW + 6;
            CompactOverlayControls.action(ctx, textRenderer, cancelX, buttonY, cancelW, 16,
                "Cancel", CompactOverlayButton.Variant.GHOST, true, mx, my);
            int remaining = registrationWarningRemainingSeconds();
            boolean ready = remaining <= 0;
            CompactOverlayControls.action(ctx, textRenderer, acceptX, buttonY, acceptW, 16,
                ready ? "Accept" : "Accept " + remaining + "s",
                CompactOverlayButton.Variant.DANGER, ready, mx, my);
        }

        private int registrationWarningRemainingSeconds() {
            long elapsed = Math.max(0L, System.currentTimeMillis() - registrationWarningOpenedAtMs);
            long remaining = Math.max(0L, REGISTRATION_WARNING_DELAY_MS - elapsed);
            return (int) Math.ceil(remaining / 1000.0);
        }

        private boolean handleRegistrationWarningClick(double mx, double my, int button) {
            if (button != 0) return true;
            RegistrationWarningBounds bounds = registrationWarningBounds();
            int cancelW = 72;
            int acceptW = 92;
            int buttonsW = cancelW + 6 + acceptW;
            int cancelX = bounds.x + (bounds.w - buttonsW) / 2;
            int acceptX = cancelX + cancelW + 6;
            int buttonY = bounds.buttonY;
            if (my >= buttonY && my < buttonY + 16 && mx >= cancelX && mx < cancelX + cancelW) {
                registrationWarningOpen = false;
                return true;
            }
            if (my >= buttonY && my < buttonY + 16 && mx >= acceptX && mx < acceptX + acceptW) {
                if (registrationWarningRemainingSeconds() > 0) return true;
                registrationWarningOpen = false;
                AutismPayloadChannelSubscriptionManager.unlockRegistration();
                rebuildRows();
                AutismNotifications.copied("Payload registration unlocked. Press Apply.");
                return true;
            }
            return true;
        }

        private RegistrationWarningBounds registrationWarningBounds() {
            int bodyTop = panelY + HEADER_H;
            int contentW = 0;
            contentW = Math.max(contentW, warningTextWidth("Payload Registration Warning", UiTone.LABEL, AutismTheme.recolor(0xFFFFB4B4, AutismTheme.Channel.DANGER)));
            contentW = Math.max(contentW, warningTextWidth("Servers can see every registered channel.", UiTone.BODY, AutismColors.textPrimary()));
            contentW = Math.max(contentW, warningTextWidth("This can identify this client or selected plugins.", UiTone.BODY, AutismColors.textPrimary()));
            contentW = Math.max(contentW, warningTextWidth("Passive logging and decoding still work while locked.", UiTone.MUTED, AutismColors.textSecondary()));
            int buttonsW = 72 + 6 + 92;
            int maxW = Math.max(180, panelWidth - 24);
            int w = Math.min(maxW, Math.max(buttonsW + REGISTRATION_WARNING_PAD_X * 2, contentW + REGISTRATION_WARNING_PAD_X * 2));
            int h = REGISTRATION_WARNING_PAD_Y + 65 + 16 + REGISTRATION_WARNING_PAD_Y;
            int x = panelX + (panelWidth - w) / 2;
            int y = bodyTop + Math.max(8, (panelHeight - HEADER_H - h) / 2);
            return new RegistrationWarningBounds(x, y, w, h, y + h - REGISTRATION_WARNING_PAD_Y - 16);
        }

        private int warningTextWidth(String text, UiTone tone, int color) {
            return UiText.width(textRenderer, text, theme.fontFor(tone), color);
        }

        private void drawCenteredUiText(GuiGraphicsExtractor ctx, String text, UiTone tone, int color, int x, int w, int y) {
            String fitted = UiText.trimToWidth(textRenderer, text, Math.max(1, w - REGISTRATION_WARNING_PAD_X * 2), theme.fontFor(tone), color);
            int textW = UiText.width(textRenderer, fitted, theme.fontFor(tone), color);
            drawUiText(ctx, fitted, tone, color, x + Math.max(0, (w - textW) / 2), y);
        }

        private record RegistrationWarningBounds(int x, int y, int w, int h, int buttonY) {}

        private void openRegistrationWarning() {
            registrationWarningOpen = true;
            registrationWarningOpenedAtMs = System.currentTimeMillis();
        }

        private void disablePayloadRegistration() {
            savePendingPayloadChannelConfig();
            AutismPayloadChannelSubscriptionManager.lockRegistrationAndUnregister(MC);
            rebuildRows();
            AutismNotifications.warning("Payload registration locked.");
        }

        private boolean ensurePayloadRegistrationUnlockedForActiveUse() {
            if (AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked()) return true;
            openRegistrationWarning();
            return false;
        }

        private void renderListenerRow(GuiGraphicsExtractor ctx, ListenerRow row, int y, int mx, int my, int index) {
            int left = listLeft();
            int right = listRight();
            int width = Math.max(1, right - left);
            int x = left + 4;
            if (row.kind == ListenerRowKind.HEADER) {
                boolean groupHeader = groupPresetCountCache.getOrDefault(cleanPresetGroup(row.header), 0) > 0;
                boolean groupOn = groupHeader && isPresetGroupFullyEnabled(row.header);
                ctx.fill(left, y, right, y + ROW_H, AutismColors.sectionHeaderBg());
                CompactSurfaces.divider(ctx, left + 2, y + ROW_H - 1, Math.max(1, width - 4));
                int statusW = groupHeader ? STATUS_COL_W : 0;
                int statusX = right - statusW - 4;
                int channelX = groupHeader ? statusX - CHANNEL_COL_W - 3 : right;
                int studyX = groupHeader ? channelX - STUDY_COL_W - 3 : right;
                int maxHeaderW = groupHeader ? Math.max(1, studyX - x - 6) : Math.max(1, width - 10);
                String header = UiText.trimToWidth(textRenderer, row.header, maxHeaderW, theme.fontFor(UiTone.MUTED), AutismColors.textSecondary());
                drawUiText(ctx, header, UiTone.MUTED, AutismColors.textSecondary(), x, y + 3);
                if (groupHeader) {
                    drawStudyPill(ctx, studyX, y, mx, my, "Study");
                    drawGroupChannelImpact(ctx, row.header, channelX, y);
                    drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), statusW, STATUS_BADGE_H, groupOn ? "ON" : "OFF", groupOn);
                }
                return;
            }
            if (row.kind == ListenerRowKind.EMPTY) {
                ctx.fill(left, y, right, y + ROW_H, AutismColors.rowNormal());
                drawUiText(ctx, UiText.trimToWidth(textRenderer, row.header == null ? "" : row.header,
                        Math.max(1, width - 12), theme.fontFor(UiTone.MUTED), AutismColors.textDim()),
                    UiTone.MUTED, AutismColors.textDim(), x + 4, rowTextY(y, UiTone.MUTED));
                return;
            }
            boolean hover = mx >= left && mx < right && my >= y && my < y + ROW_H;
            if (row.kind == ListenerRowKind.FAMILY) {
                PayloadChannelFamilyRow family = row.family;
                if (family == null) return;
                int total = family.exactChannelCount();
                int enabled = familyEnabledExactCount(family);
                boolean allEnabled = total > 0 && enabled == total;
                int fill = enabled > 0 ? AutismColors.packetRowSelectedBg(hover) : (hover ? AutismColors.rowHover() : AutismColors.rowNormal());
                ctx.fill(left, y, right, y + ROW_H, fill);
                if (enabled > 0) ctx.fill(left, y, left + 2, y + ROW_H, AutismColors.packetRowSelectedAccent());
                int statusX = right - STATUS_COL_W - 4;
                int channelX = statusX - CHANNEL_COL_W - 3;
                int studyX = channelX - STUDY_COL_W - 3;
                boolean familyExpanded = expandedPayloadFamilies.contains(family.stableKey());
                String text = family.label + "  " + enabled + "/" + total;
                int textColor = enabled > 0 ? AutismColors.packetRowSelectedText() : AutismColors.textPrimary();
                UiRenderer.chevron(ctx, UiBounds.of(x + 4, y + ((ROW_H - 9) / 2), 9, 9), familyExpanded, textColor);
                drawUiText(ctx, UiText.trimToWidth(textRenderer, text, Math.max(1, studyX - (x + 15) - 5),
                        theme.fontFor(UiTone.BODY), textColor),
                    UiTone.BODY, textColor, x + 15, rowTextY(y, UiTone.BODY));
                drawStudyPill(ctx, studyX, y, mx, my, "Study");
                drawImpactText(ctx, total + "ch", total > 0 ? 0xFF8FE8B0 : AutismColors.textDim(), channelX, y);
                drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), STATUS_COL_W, STATUS_BADGE_H,
                    allEnabled ? "ON" : "OFF", allEnabled);
                return;
            }
            if (row.kind == ListenerRowKind.REGISTRATION) {
                AutismConfig.PayloadChannelRegistrationRule rule = row.registration;
                boolean enabled = rule != null && rule.enabled;
                int fill = enabled ? AutismColors.packetRowSelectedBg(hover) : (hover ? AutismColors.rowHover() : AutismColors.rowNormal());
                ctx.fill(left, y, right, y + ROW_H, fill);
                if (enabled) ctx.fill(left, y, left + 2, y + ROW_H, AutismColors.packetRowSelectedAccent());
                String label = rule == null || rule.label == null || rule.label.isBlank() ? "" : rule.label.trim();
                String channel = rule == null ? "" : AutismPayloadChannelRegistrations.normalizeChannel(rule.channel);
                String text = label.isBlank() || label.equals(channel) ? channel : label + "  " + channel;
                boolean editable = isEditableRegistration(rule);
                int removeX = editable ? right - REMOVE_COL_W : right;
                int editX = editable ? removeX - REMOVE_COL_W : right;
                int statusRight = editable ? editX - 3 : right - 4;
                int statusX = statusRight - STATUS_COL_W;
                int sourceW = 52;
                int sourceX = statusX - sourceW - 3;
                int maxTextW = Math.max(1, sourceX - (x + 4) - 5);
                int color = enabled ? AutismColors.packetRowSelectedText() : AutismColors.textPrimary();
                drawUiText(ctx, UiText.trimToWidth(textRenderer, text, maxTextW, theme.fontFor(UiTone.BODY), color),
                    UiTone.BODY, color, x + 4, rowTextY(y, UiTone.BODY));
                drawUiText(ctx, UiText.trimToWidth(textRenderer, AutismPayloadChannelRegistrations.sourceLabel(rule == null ? "" : rule.source),
                        sourceW, theme.fontFor(UiTone.MUTED), AutismColors.textSecondary()),
                    UiTone.MUTED, AutismColors.textSecondary(), sourceX, rowTextY(y, UiTone.MUTED));
                drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), STATUS_COL_W, STATUS_BADGE_H, enabled ? "ON" : "OFF", enabled);
                if (editable) {
                    boolean eu = mx >= editX && mx <= editX + REMOVE_COL_W && my >= y && my < y + ROW_H;
                    boolean hu = mx >= removeX && mx <= removeX + REMOVE_COL_W && my >= y && my < y + ROW_H;
                    drawUiText(ctx, "E", UiTone.MUTED, eu ? 0xFF8FE8B0 : AutismColors.textSecondary(), editX + 3, rowTextY(y, UiTone.MUTED));
                    UiRenderer.cross(ctx, UiBounds.of(removeX + 3, y + ((ROW_H - 9) / 2), 9, 9), hu ? AutismTheme.recolor(0xFFFF6666, AutismTheme.Channel.DANGER) : AutismColors.textSecondary());
                }
                return;
            }
            if (row.kind == ListenerRowKind.LEARNED) {
                ctx.fill(left, y, right, y + ROW_H, hover ? AutismColors.rowHover() : AutismColors.rowNormal());
                int statusX = right - STATUS_COL_W - 4;
                int sourceX = statusX - 56;
                int maxTextW = Math.max(1, sourceX - (x + 4) - 5);
                drawUiText(ctx, UiText.trimToWidth(textRenderer, row.learnedChannel == null ? "" : row.learnedChannel,
                        maxTextW, theme.fontFor(UiTone.BODY), AutismColors.textPrimary()),
                    UiTone.BODY, AutismColors.textPrimary(), x + 4, rowTextY(y, UiTone.BODY));
                drawUiText(ctx, "Learned", UiTone.MUTED, AutismColors.textSecondary(), sourceX, rowTextY(y, UiTone.MUTED));
                drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), STATUS_COL_W, STATUS_BADGE_H, "OFF", false);
                return;
            }
            if (row.kind == ListenerRowKind.ACTIVE) {
                AutismConfig.PayloadChannelFilterRule rule = row.rule;
                int fill = rule.enabled ? AutismColors.packetRowSelectedBg(hover) : (hover ? AutismColors.rowHover() : AutismColors.rowNormal());
                ctx.fill(left, y, right, y + ROW_H, fill);
                if (rule.enabled) ctx.fill(left, y, left + 2, y + ROW_H, AutismColors.packetRowSelectedAccent());
                String label = rule.label == null || rule.label.isBlank() ? rule.pattern : rule.label;
                String text = label + "  " + rule.pattern;
                int color = rule.enabled ? AutismColors.packetRowSelectedText() : AutismColors.textSecondary();
                int removeX = right - REMOVE_COL_W;
                int editX = removeX - REMOVE_COL_W;
                int statusRight = editX - 3;
                int statusW = STATUS_COL_W;
                int statusX = statusRight - statusW;
                int channelX = statusX - CHANNEL_COL_W - 3;
                int studyX = channelX - STUDY_COL_W - 3;
                int maxTextW = Math.max(1, studyX - (x + 4) - 4);
                drawUiText(ctx, UiText.trimToWidth(textRenderer, text, maxTextW, theme.fontFor(UiTone.BODY), color), UiTone.BODY, color, x + 4, rowTextY(y, UiTone.BODY));
            drawStudyPill(ctx, studyX, y, mx, my, "Study");
            drawChannelImpact(ctx, rule.pattern, channelX, y);
                drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), statusW, STATUS_BADGE_H, rule.enabled ? "ON" : "OFF", rule.enabled);
                boolean eu = mx >= editX && mx <= editX + REMOVE_COL_W && my >= y && my < y + ROW_H;
                boolean hu = mx >= removeX && mx <= removeX + REMOVE_COL_W && my >= y && my < y + ROW_H;
                drawUiText(ctx, "E", UiTone.MUTED, eu ? 0xFF8FE8B0 : AutismColors.textSecondary(), editX + 3, rowTextY(y, UiTone.MUTED));
                UiRenderer.cross(ctx, UiBounds.of(removeX + 3, y + ((ROW_H - 9) / 2), 9, 9), hu ? AutismTheme.recolor(0xFFFF6666, AutismTheme.Channel.DANGER) : AutismColors.textSecondary());
                return;
            }

            AutismPayloadChannelListeners.Preset preset = row.preset;
            boolean enabled = isPresetEnabled(preset);
            int fill = enabled ? AutismColors.packetRowSelectedBg(hover) : (hover ? AutismColors.rowHover() : AutismColors.rowNormal());
            ctx.fill(left, y, right, y + ROW_H, fill);
            if (enabled) ctx.fill(left, y, left + 2, y + ROW_H, AutismColors.packetRowSelectedAccent());
            String status = enabled ? "ON" : "OFF";
            int statusW = STATUS_COL_W;
            int statusX = right - statusW - 4;
            String text = preset.label() + "  " + preset.pattern();
            int textColor = enabled ? AutismColors.packetRowSelectedText() : AutismColors.textPrimary();
            int channelX = statusX - CHANNEL_COL_W - 3;
            int studyX = channelX - STUDY_COL_W - 3;
            int maxTextW = Math.max(1, studyX - (x + 4) - 5);
            drawUiText(ctx, UiText.trimToWidth(textRenderer, text, maxTextW, theme.fontFor(UiTone.BODY), textColor),
                UiTone.BODY, textColor, x + 4, rowTextY(y, UiTone.BODY));
            drawStudyPill(ctx, studyX, y, mx, my, presetActionLabel(preset));
            drawChannelImpact(ctx, preset, channelX, y);
            drawStatusPill(ctx, statusX, y + ((ROW_H - STATUS_BADGE_H) / 2), statusW, STATUS_BADGE_H, status, enabled);
        }

        private String presetActionLabel(AutismPayloadChannelListeners.Preset preset) {
            if (preset != null && preset.kind().templateLike()) return "Use";
            return "Study";
        }

        private void drawStudyPill(GuiGraphicsExtractor ctx, int x, int y, int mx, int my, String label) {
            int h = STATUS_BADGE_H;
            int py = y + ((ROW_H - h) / 2);
            boolean disabled = configurationOnly && !"Use".equals(label);
            boolean hover = !disabled && mx >= x && mx < x + STUDY_COL_W && my >= py && my < py + h;

            if (disabled) {
                Chip.renderDisabled(UiContexts.overlay(ctx, textRenderer, mx, my),
                    UiBounds.of(x, py, STUDY_COL_W, h), label);
            } else {
                Chip.render(UiContexts.overlay(ctx, textRenderer, mx, my),
                    UiBounds.of(x, py, STUDY_COL_W, h), label, false, hover);
            }
        }

        private boolean studyHit(double mx, double my, int rowY) {
            int right = listRight();
            int statusX = right - STATUS_COL_W - 4;
            int channelX = statusX - CHANNEL_COL_W - 3;
            int studyX = channelX - STUDY_COL_W - 3;
            int studyY = rowY + ((ROW_H - STATUS_BADGE_H) / 2);
            return mx >= studyX && mx < studyX + STUDY_COL_W && my >= studyY && my < studyY + STATUS_BADGE_H;
        }

        private boolean familyExpandHit(double mx, double my, int rowY) {
            int x = listLeft() + 4;
            return mx >= x && mx < x + 16 && my >= rowY && my < rowY + ROW_H;
        }

        private void drawChannelImpact(GuiGraphicsExtractor ctx, String pattern, int x, int y) {
            AutismPayloadChannelSubscriptionManager.RegistrationImpact impact =
                impactForPatternCached(pattern);
            boolean core = impact.exactChannelCount() <= 0 && isCorePayloadPattern(pattern);
            String label = impact.exactChannelCount() > 0 ? impact.compactLabel() : (core ? "Core" : "Highlight");
            int color = impact.exactChannelCount() > 0 || core ? 0xFF8FE8B0 : AutismColors.textDim();
            drawImpactText(ctx, label, color, x, y);
        }

        private void drawChannelImpact(GuiGraphicsExtractor ctx, AutismPayloadChannelListeners.Preset preset, int x, int y) {
            if (preset == null) {
                drawChannelImpact(ctx, "", x, y);
                return;
            }
            AutismPayloadChannelSubscriptionManager.RegistrationImpact impact =
                impactForPatternCached(preset.pattern());
            String label;
            int color;
            if (preset.kind() == AutismPayloadChannelListeners.PresetKind.EXACT) {
                label = impact.exactChannelCount() > 0 ? impact.compactLabel() : "Exact";
                color = 0xFF8FE8B0;
            } else if (preset.kind() == AutismPayloadChannelListeners.PresetKind.CORE) {
                label = preset.kind().badge();
                color = 0xFF8FE8B0;
            } else if (preset.kind() == AutismPayloadChannelListeners.PresetKind.UNVERIFIED) {
                label = preset.kind().badge();
                color = AutismColors.packetYellow();
            } else {
                label = preset.kind().badge();
                color = AutismColors.textDim();
            }
            drawImpactText(ctx, label, color, x, y);
        }

        private void drawImpactText(GuiGraphicsExtractor ctx, String label, int color, int x, int y) {
            label = UiText.trimToWidth(textRenderer, label, Math.max(1, CHANNEL_COL_W - 2), theme.fontFor(UiTone.MUTED), color);
            drawUiText(ctx, label, UiTone.MUTED,
                color,
                x + Math.max(0, CHANNEL_COL_W - UiText.width(textRenderer, label, theme.fontFor(UiTone.MUTED), color)) / 2,
                rowTextY(y, UiTone.MUTED));
        }

        private boolean isCorePayloadPattern(String pattern) {
            String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
            return switch (normalized) {
                case "minecraft:register", "minecraft:unregister", "register", "unregister", "bungeecord" -> true;
                default -> false;
            };
        }

        private void drawGroupChannelImpact(GuiGraphicsExtractor ctx, String group, int x, int y) {
            int count = groupExactChannelCount(group);
            String label = count > 0 ? count + "ch" : "Tpl";
            int color = count > 0 ? 0xFF8FE8B0 : AutismColors.textDim();
            drawUiText(ctx, label, UiTone.MUTED,
                color,
                x + Math.max(0, CHANNEL_COL_W - UiText.width(textRenderer, label, theme.fontFor(UiTone.MUTED), color)) / 2,
                rowTextY(y, UiTone.MUTED));
        }

        private int groupExactChannelCount(String group) {
            return group == null ? 0 : groupExactCountCache.getOrDefault(cleanPresetGroup(group), 0);
        }

        private boolean isPresetEnabled(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null) return false;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            return enabledPresetKeysCache.contains(preset.key())
                || (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)
                    && enabledRegistrationChannelsCache.contains(pattern));
        }

        private int familyEnabledExactCount(PayloadChannelFamilyRow family) {
            if (family == null) return 0;
            int enabled = 0;
            for (String channel : family.exactChannels()) {
                if (enabledRegistrationChannelsCache.contains(channel)) enabled++;
            }
            return enabled;
        }

        private boolean isFamilyFullyEnabled(PayloadChannelFamilyRow family) {
            return family != null
                && family.exactChannelCount() > 0
                && familyEnabledExactCount(family) == family.exactChannelCount();
        }

        private boolean isPresetGroupFullyEnabled(String group) {
            if (group == null || group.isBlank()) return false;
            String cleanGroup = cleanPresetGroup(group);
            int total = groupPresetCountCache.getOrDefault(cleanGroup, 0);
            if (total <= 0) return false;
            int enabled = 0;
            PayloadChannelCategoryRow category = buildPayloadChannelCategories().get(cleanGroup);
            if (category == null) return false;
            for (PayloadChannelFamilyRow family : category.families.values()) {
                for (AutismPayloadChannelListeners.Preset preset : family.presets) {
                    if (isPresetEnabled(preset)) enabled++;
                }
            }
            for (AutismPayloadChannelListeners.Preset preset : category.standalonePresets) {
                if (isPresetEnabled(preset)) enabled++;
            }
            return enabled == total;
        }

        private void drawStatusPill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean enabled) {

            Chip.render(UiContexts.overlay(ctx, textRenderer, -1, -1),
                UiBounds.of(x, y, w, h), label, enabled, false);
        }

        private int rowTextY(int y, UiTone tone) {
            return UiSizing.alignTextY(y, ROW_H, theme.fontHeight(tone), theme.bodyTextNudge());
        }

        private void refreshPayloadChannelUiCaches() {
            enabledRegistrationChannelsCache.clear();
            for (AutismConfig.PayloadChannelRegistrationRule rule : payloadRegistrations.rules()) {
                if (rule == null || !rule.enabled) continue;
                String channel = AutismPayloadChannelRegistrations.normalizeChannel(rule.channel);
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(channel)) {
                    enabledRegistrationChannelsCache.add(channel);
                }
            }

            if (allPresetKeysCache.isEmpty()) {
                for (AutismPayloadChannelListeners.Preset preset : payloadListeners.presets()) {
                    if (preset != null) allPresetKeysCache.add(preset.key());
                }
            }
            enabledPresetKeysCache.clear();
            enabledHighlightCountCache = 0;
            for (AutismConfig.PayloadChannelFilterRule rule : payloadListeners.rules()) {
                if (rule == null) continue;
                if (rule.enabled) enabledHighlightCountCache++;
                if (rule.enabled) enabledPresetKeysCache.add(AutismPayloadChannelListeners.normalizePattern(rule.pattern));
            }

            if (groupPresetCountCache.isEmpty() && groupExactCountCache.isEmpty()) {
                buildPayloadChannelGroupCaches();
            }

            cachedRegistrationLimit = payloadRegistrationLimitFromStatus();
            pendingRegistrationLabelCache = computePendingRegistrationLabel();
        }

        private void buildPayloadChannelGroupCaches() {
            for (PayloadChannelCategoryRow category : buildPayloadChannelCategories().values()) {
                int presetCount = category.standalonePresets.size();
                LinkedHashSet<String> exact = new LinkedHashSet<>();
                for (PayloadChannelFamilyRow family : category.families.values()) {
                    presetCount += family.presets.size();
                    exact.addAll(family.exactChannels());
                }
                for (AutismPayloadChannelListeners.Preset preset : category.standalonePresets) {
                    if (preset == null) continue;
                    String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
                    if (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) exact.add(pattern);
                }
                groupPresetCountCache.put(category.group, presetCount);
                groupExactCountCache.put(category.group, exact.size());
            }
        }

        private void rebuildRows() {
            refreshPayloadChannelUiCaches();
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            rows.clear();
            boolean registrationUnlocked = AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked();
            String registeredHeader = registrationUnlocked ? "Registered Channels" : "Saved Channels (Locked)";

            List<AutismConfig.PayloadChannelRegistrationRule> registrations = payloadRegistrations.rules();
            Map<String, List<ListenerRow>> registeredGroups = new LinkedHashMap<>();
            for (int i = 0; i < registrations.size(); i++) {
                AutismConfig.PayloadChannelRegistrationRule rule = registrations.get(i);
                if (rule == null || !rule.enabled) continue;
                if (!registrationMatchesSearch(rule, query)) continue;
                String group = registeredGroupTitle(rule);
                registeredGroups.computeIfAbsent(group, unused -> new ArrayList<>()).add(ListenerRow.registration(i, rule));
            }
            int registeredCount = registeredGroups.values().stream().mapToInt(List::size).sum();
            if (!registeredGroups.isEmpty()) {
                rows.add(ListenerRow.header(registeredHeader));
                for (Map.Entry<String, List<ListenerRow>> group : sortedRegisteredGroups(registeredGroups)) {
                    rows.add(ListenerRow.header(group.getKey()));
                    group.getValue().sort(this::compareRegistrationRows);
                    rows.addAll(group.getValue());
                }
            } else if (query.isEmpty()) {
                rows.add(ListenerRow.header(registeredHeader));
                rows.add(ListenerRow.empty(registrationUnlocked
                    ? "No channels registered. Enable a preset or add an exact channel."
                    : "No channels saved. Enable a preset or add an exact channel."));
            } else if (registeredCount == 0) {
                rows.add(ListenerRow.empty(registrationUnlocked ? "No matching registered channels." : "No matching saved channels."));
            }

            Set<String> knownRegistrationChannels = new LinkedHashSet<>();
            for (AutismConfig.PayloadChannelRegistrationRule rule : registrations) {
                if (rule != null) knownRegistrationChannels.add(AutismPayloadChannelRegistrations.normalizeChannel(rule.channel));
            }

            boolean customHeaderAdded = false;
            List<AutismConfig.PayloadChannelFilterRule> active = payloadListeners.rules();
            for (int i = 0; i < active.size(); i++) {
                AutismConfig.PayloadChannelFilterRule rule = active.get(i);
                if (rule == null || rule.preset || allPresetKeysCache.contains(AutismPayloadChannelListeners.normalizePattern(rule.pattern))) {
                    continue;
                }
                if (enabledOnly && !rule.enabled) continue;
                String normalizedPattern = AutismPayloadChannelListeners.normalizePattern(rule.pattern);
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalizedPattern)
                    && knownRegistrationChannels.contains(normalizedPattern)) {
                    continue;
                }
                String hay = ((rule.label == null ? "" : rule.label) + " " + rule.pattern).toLowerCase(Locale.ROOT);
                if (!query.isEmpty() && !hay.contains(query)) continue;
                if (!customHeaderAdded) {
                    rows.add(ListenerRow.header("Custom Highlights"));
                    customHeaderAdded = true;
                }
                rows.add(ListenerRow.active(i, rule));
            }

            boolean availableHeader = false;
            if (!enabledOnly) {
                for (String learned : AutismPayloadChannelSubscriptionManager.learnedChannels()) {
                    if (learned == null || learned.isBlank() || knownRegistrationChannels.contains(learned)) continue;
                    if (!query.isEmpty() && !learned.toLowerCase(Locale.ROOT).contains(query)) continue;
                    if (!availableHeader) {
                        rows.add(ListenerRow.header("Learned Exact Channels"));
                        availableHeader = true;
                    }
                    rows.add(ListenerRow.learned(learned));
                }
            }
            for (int i = 0; i < registrations.size(); i++) {
                AutismConfig.PayloadChannelRegistrationRule rule = registrations.get(i);
                if (rule == null || rule.enabled) continue;
                if (enabledOnly) continue;
                if (AutismPayloadChannelRegistrations.SOURCE_PRESET.equals(rule.source)) continue;
                if (!registrationMatchesSearch(rule, query)) continue;
                if (!availableHeader) {
                    rows.add(ListenerRow.header("Available Exact Channels"));
                    availableHeader = true;
                }
                rows.add(ListenerRow.registration(i, rule));
            }

            for (PayloadChannelCategoryRow category : sortedPayloadCategories()) {
                List<PayloadChannelFamilyRow> visibleFamilies = new ArrayList<>();
                for (PayloadChannelFamilyRow family : category.sortedFamilies) {
                    if (!familyMatchesSearch(family, query)) continue;
                    if (enabledOnly && familyEnabledExactCount(family) <= 0) continue;
                    visibleFamilies.add(family);
                }

                List<AutismPayloadChannelListeners.Preset> standalone = new ArrayList<>();
                for (AutismPayloadChannelListeners.Preset preset : category.sortedStandalonePresets) {
                    if (!presetMatchesSearch(preset, query)) continue;
                    if (enabledOnly && !isPresetEnabled(preset)) continue;
                    standalone.add(preset);
                }

                if (visibleFamilies.isEmpty() && standalone.isEmpty()) continue;
                rows.add(ListenerRow.header(category.group));
                for (PayloadChannelFamilyRow family : visibleFamilies) {
                    rows.add(ListenerRow.family(family));
                    if (isFamilyExpandedForRender(family, query)) {
                        for (AutismPayloadChannelListeners.Preset preset : family.sortedPresets()) {
                            if (!presetVisibleInExpandedFamily(family, preset, query)) continue;
                            if (enabledOnly && !isPresetEnabled(preset)) continue;
                            rows.add(ListenerRow.preset(preset));
                        }
                    }
                }
                for (AutismPayloadChannelListeners.Preset preset : standalone) {
                    rows.add(ListenerRow.preset(preset));
                }
            }
            if (rows.isEmpty()) {
                rows.add(ListenerRow.empty(enabledOnly ? "No enabled payload channels or highlights." : "No payload channels match."));
            }
            clampRowScroll();
        }

        private Map<String, PayloadChannelCategoryRow> buildPayloadChannelCategories() {
            if (payloadCategoryCache != null) return payloadCategoryCache;
            Map<String, PayloadChannelCategoryRow> categories = new LinkedHashMap<>();
            presetByExactChannelCache.clear();
            presetSearchTextCache.clear();
            for (AutismPayloadChannelListeners.Preset preset : payloadListeners.presets()) {
                if (preset == null) continue;
                presetSearchTextCache.put(preset, (preset.group() + " " + preset.label() + " " + preset.pattern()).toLowerCase(Locale.ROOT));
                if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) {
                    String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
                    if (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                        AutismPayloadChannelListeners.Preset existing = presetByExactChannelCache.get(pattern);
                        if (existing == null || presetKindPriority(preset) < presetKindPriority(existing)) {
                            presetByExactChannelCache.put(pattern, preset);
                        }
                    }
                }
                String group = cleanPresetGroup(preset.group());
                PayloadChannelCategoryRow category = categories.computeIfAbsent(group, PayloadChannelCategoryRow::new);
                PayloadChannelFamilyKey familyKey = familyKeyForPreset(preset);
                if (familyKey != null) {
                    category.family(familyKey).presets.add(preset);
                } else {
                    category.standalonePresets.add(preset);
                }
            }
            payloadCategoryCache = categories;
            sortedPayloadCategoryCache = new ArrayList<>(categories.values());
            sortedPayloadCategoryCache.sort((a, b) -> {
                int priority = Integer.compare(groupPriority(a.group), groupPriority(b.group));
                return priority != 0 ? priority : String.CASE_INSENSITIVE_ORDER.compare(a.group, b.group);
            });
            for (PayloadChannelCategoryRow category : sortedPayloadCategoryCache) {
                category.sortedFamilies = new ArrayList<>(category.families.values());
                category.sortedFamilies.sort(this::compareFamilies);
                category.sortedStandalonePresets = new ArrayList<>(category.standalonePresets);
                sortPresetRows(category.sortedStandalonePresets);
                for (PayloadChannelFamilyRow family : category.sortedFamilies) {
                    family.sortedPresets = new ArrayList<>(family.presets);
                    sortPresetRows(family.sortedPresets);
                }
            }
            return payloadCategoryCache;
        }

        private List<PayloadChannelCategoryRow> sortedPayloadCategories() {
            buildPayloadChannelCategories();
            return sortedPayloadCategoryCache == null ? List.of() : sortedPayloadCategoryCache;
        }

        private String cleanPresetGroup(String group) {
            return group == null || group.isBlank() ? "Other" : group.trim();
        }

        private PayloadChannelFamilyKey familyKeyForPreset(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null || !AutismPayloadChannelListeners.isRegisterablePreset(preset)) return null;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            if (!AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) return null;
            String family = derivePayloadFamily(pattern);
            if (family.isBlank()) return null;
            return new PayloadChannelFamilyKey(cleanPresetGroup(preset.group()), family, family);
        }

        private String derivePayloadFamily(String channel) {
            String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
            int colon = normalized.indexOf(':');
            if (colon <= 0) return "";
            String namespace = normalized.substring(0, colon);
            String path = normalized.substring(colon + 1);
            if (namespace.equals("plasmo") && path.startsWith("voice/v2/")) return "plasmo:voice/v2/*";
            if (namespace.equals("ftbchunks")) return "ftbchunks:*";
            if (namespace.equals("ftbteams")) return "ftbteams:*";
            return namespace + ":*";
        }

        private boolean familyMatchesSearch(PayloadChannelFamilyRow family, String query) {
            if (family == null) return false;
            if (query == null || query.isBlank()) return true;
            String needle = query.toLowerCase(Locale.ROOT);
            if ((family.group + " " + family.label).toLowerCase(Locale.ROOT).contains(needle)) return true;
            for (AutismPayloadChannelListeners.Preset preset : family.presets) {
                if (presetMatchesSearch(preset, needle)) return true;
            }
            return false;
        }

        private boolean presetVisibleInExpandedFamily(PayloadChannelFamilyRow family,
                                                       AutismPayloadChannelListeners.Preset preset,
                                                       String query) {
            if (query == null || query.isBlank()) return true;
            String needle = query.toLowerCase(Locale.ROOT);
            if (family != null && (family.group + " " + family.label).toLowerCase(Locale.ROOT).contains(needle)) return true;
            return presetMatchesSearch(preset, needle);
        }

        private boolean isFamilyExpandedForRender(PayloadChannelFamilyRow family, String query) {
            return family != null && (expandedPayloadFamilies.contains(family.stableKey()) || (query != null && !query.isBlank()));
        }

        private int compareFamilies(PayloadChannelFamilyRow a, PayloadChannelFamilyRow b) {
            int exact = Integer.compare(b.exactChannelCount(), a.exactChannelCount());
            if (exact != 0) return exact;
            return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
        }

        private List<Map.Entry<String, List<ListenerRow>>> sortedRegisteredGroups(Map<String, List<ListenerRow>> groups) {
            List<Map.Entry<String, List<ListenerRow>>> out = new ArrayList<>(groups.entrySet());
            out.sort((a, b) -> {
                int priority = Integer.compare(registeredGroupPriority(a.getKey()), registeredGroupPriority(b.getKey()));
                return priority != 0 ? priority : String.CASE_INSENSITIVE_ORDER.compare(a.getKey(), b.getKey());
            });
            return out;
        }

        private int compareRegistrationRows(ListenerRow a, ListenerRow b) {
            String ac = a == null || a.registration == null ? "" : AutismPayloadChannelRegistrations.normalizeChannel(a.registration.channel);
            String bc = b == null || b.registration == null ? "" : AutismPayloadChannelRegistrations.normalizeChannel(b.registration.channel);
            return String.CASE_INSENSITIVE_ORDER.compare(ac, bc);
        }

        private String registeredGroupTitle(AutismConfig.PayloadChannelRegistrationRule rule) {
            if (rule == null) return "Registered: Other";
            String source = rule.source == null ? "" : rule.source.trim().toLowerCase(Locale.ROOT);
            if (AutismPayloadChannelRegistrations.SOURCE_CUSTOM.equals(source)) return "Registered: Custom";
            if (AutismPayloadChannelRegistrations.SOURCE_LEARNED.equals(source)) return "Registered: Learned";
            AutismPayloadChannelListeners.Preset preset = presetForRegisteredChannel(rule.channel);
            if (preset != null && preset.group() != null && !preset.group().isBlank()) {
                PayloadChannelFamilyKey family = familyKeyForPreset(preset);
                if (family != null) return "Registered: " + preset.group().trim() + " - " + family.label();
                return "Registered: " + preset.group().trim();
            }
            return "Registered: Other";
        }

        private int registeredGroupPriority(String group) {
            String clean = group == null ? "" : group.trim();
            if (clean.startsWith("Registered: ")) clean = clean.substring("Registered: ".length()).trim();
            int familySep = clean.indexOf(" - ");
            if (familySep > 0) clean = clean.substring(0, familySep).trim();
            if ("Custom".equalsIgnoreCase(clean)) return 100;
            if ("Learned".equalsIgnoreCase(clean)) return 101;
            if ("Other".equalsIgnoreCase(clean)) return 102;
            return groupPriority(clean);
        }

        private AutismPayloadChannelListeners.Preset presetForRegisteredChannel(String channel) {
            String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
            if (normalized.isBlank()) return null;
            buildPayloadChannelCategories();
            return presetByExactChannelCache.get(normalized);
        }

        private boolean presetMatchesSearch(AutismPayloadChannelListeners.Preset preset, String query) {
            if (preset == null) return false;
            if (query == null || query.isBlank()) return true;
            String hay = presetSearchTextCache.get(preset);
            if (hay == null) {
                hay = (preset.group() + " " + preset.label() + " " + preset.pattern()).toLowerCase(Locale.ROOT);
                presetSearchTextCache.put(preset, hay);
            }
            return hay.contains(query);
        }

        private void sortPresetRows(List<AutismPayloadChannelListeners.Preset> presets) {
            presets.sort((a, b) -> {
                int kind = Integer.compare(presetKindPriority(a), presetKindPriority(b));
                if (kind != 0) return kind;
                int exact = Integer.compare(presetExactCount(b), presetExactCount(a));
                if (exact != 0) return exact;
                boolean aDefault = AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(a);
                boolean bDefault = AutismPayloadChannelListeners.isDefaultRecommendedPresetPublic(b);
                if (aDefault != bDefault) return aDefault ? -1 : 1;
                int labelCompare = String.CASE_INSENSITIVE_ORDER.compare(a.label(), b.label());
                return labelCompare != 0 ? labelCompare : String.CASE_INSENSITIVE_ORDER.compare(a.pattern(), b.pattern());
            });
        }

        private int presetExactCount(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null) return 0;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            return AutismPayloadChannelRegistrations.isRegisterableChannel(pattern) ? 1 : 0;
        }

        private AutismPayloadChannelSubscriptionManager.RegistrationImpact impactForPatternCached(String pattern) {
            String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
            if (normalized.isBlank()) return new AutismPayloadChannelSubscriptionManager.RegistrationImpact(false, false, 0, List.of());
            return impactByPatternCache.computeIfAbsent(normalized, AutismPayloadChannelSubscriptionManager::impactForPattern);
        }

        private int presetKindPriority(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null || preset.kind() == null) return 4;
            return switch (preset.kind()) {
                case EXACT -> 0;
                case CORE -> 1;
                case TEMPLATE -> 2;
                case UNVERIFIED -> 3;
            };
        }

        private int groupPriority(String group) {
            return switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
                case "minecraft" -> 0;
                case "proxy" -> 1;
                case "mod api" -> 2;
                case "voice" -> 3;
                case "protection" -> 4;
                case "shops" -> 5;
                case "auctions" -> 6;
                case "crates" -> 7;
                case "gambling" -> 8;
                case "containers" -> 9;
                case "storage" -> 10;
                case "backpacks" -> 11;
                case "economy" -> 12;
                case "items" -> 13;
                default -> 50;
            };
        }

        private boolean registrationMatchesSearch(AutismConfig.PayloadChannelRegistrationRule rule, String query) {
            if (query == null || query.isBlank()) return true;
            String hay = ((rule.label == null ? "" : rule.label) + " "
                + (rule.channel == null ? "" : rule.channel) + " "
                + AutismPayloadChannelRegistrations.sourceLabel(rule.source)).toLowerCase(Locale.ROOT);
            return hay.contains(query);
        }

        private void mutateRowsKeepingAnchor(int clickedIndex, Runnable mutation) {
            String anchor = rowStableKey(clickedIndex);
            int oldIndex = clickedIndex;
            mutation.run();
            rebuildRows();
            if (anchor != null) {
                int newIndex = findRowByStableKey(anchor);
                if (newIndex >= 0) {
                    rowScroll += (newIndex - oldIndex) * ROW_H;
                }
            }
            clampRowScroll();
            int listH = listenerListHeight();
            int maxScroll = Math.max(0, rows.size() * ROW_H - listH);
            rowsScroll.jumpTo(rowScroll, maxScroll);
        }

        private String rowStableKey(int index) {
            if (index < 0 || index >= rows.size()) return null;
            ListenerRow row = rows.get(index);
            if (row.kind == ListenerRowKind.ACTIVE && row.rule != null) {
                return "custom:" + AutismPayloadChannelListeners.normalizePattern(row.rule.pattern);
            }
            if (row.kind == ListenerRowKind.REGISTRATION && row.registration != null) {
                return "reg:" + AutismPayloadChannelRegistrations.normalizeChannel(row.registration.channel);
            }
            if (row.kind == ListenerRowKind.LEARNED && row.learnedChannel != null) {
                return "learned:" + AutismPayloadChannelRegistrations.normalizeChannel(row.learnedChannel);
            }
            if (row.kind == ListenerRowKind.PRESET && row.preset != null) {
                return "preset:" + row.preset.key();
            }
            if (row.kind == ListenerRowKind.FAMILY && row.family != null) {
                return "family:" + row.family.stableKey();
            }
            if (row.kind == ListenerRowKind.HEADER && row.header != null) {
                return "header:" + row.header;
            }
            return null;
        }

        private int findRowByStableKey(String key) {
            if (key == null) return -1;
            for (int i = 0; i < rows.size(); i++) {
                if (key.equals(rowStableKey(i))) return i;
            }
            return -1;
        }

        private void clampRowScroll() {
            int listH = listenerListHeight();
            int maxScroll = Math.max(0, rows.size() * ROW_H - listH);
            rowScroll = quantizeScrollOffset(rowScroll, ROW_H, maxScroll);
        }

        @Override
        public boolean usesSharedHeaderClickCollapse() {
            return true;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!visible) return false;
            AutismWindowLayout bounds = getBounds();
            if (button == 0 && isOverCloseButton(mx, my, bounds)) {
                setVisible(false);
                return true;
            }
            if (registrationWarningOpen) {
                return handleRegistrationWarningClick(mx, my, button);
            }
            if (button == 0 && isOverDragBar(mx, my)) {
                draggingWindow = true;
                dragOffsetX = mx - panelX;
                dragOffsetY = my - panelY;
                return true;
            }
            if (collapsed) return true;
            if (search.mouseClicked(mx, my, button)) return true;
            if (customPattern.mouseClicked(mx, my, button)) return true;

            int y = customY();
            int customW = customPatternWidth();
            int addX = panelX + 6 + customW + CUSTOM_GAP;
            if (button == 0 && my >= y && my < y + 16) {
                if (mx >= addX && mx < addX + CUSTOM_ADD_W) {
                    saveCustomRuleField();
                    return true;
                }
                if (isEditingCustomEntry()) {
                    int cancelX = addX + CUSTOM_ADD_W + CUSTOM_GAP;
                    if (mx >= cancelX && mx < cancelX + CUSTOM_CANCEL_W) {
                        clearCustomEditState();
                        return true;
                    }
                }
            }

            y = controlsY();
            int defaultsX = panelX + 6;
            int knownOnX = defaultsX + DEFAULTS_W + CONTROL_GAP;
            int captureX = knownOnX + KNOWN_ON_W + CONTROL_GAP;
            int filterX = captureX + CAPTURE_W + CONTROL_GAP;
            int applyX = filterX + FILTER_W + CONTROL_GAP;
            int revertX = applyX + APPLY_W + CONTROL_GAP;
            int allOffX = revertX + REVERT_W + CONTROL_GAP;
            if (button == 0 && my >= y && my < y + 16 && mx >= defaultsX && mx < defaultsX + DEFAULTS_W) {
                payloadListeners.applyDefaultRecommendedOnlyInMemory();
                payloadRegistrations.applyRecommendedOnlyInMemory();
                markPayloadChannelConfigDirty();
                rebuildRows();
                dirty = true;
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= knownOnX && mx < knownOnX + KNOWN_ON_W) {
                enableAllKnownExactChannels();
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= captureX && mx < captureX + CAPTURE_W) {
                captureLearnedChannels(false);
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= filterX && mx < filterX + FILTER_W) {
                enabledOnly = !enabledOnly;
                rebuildRows();
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= applyX && mx < applyX + APPLY_W) {
                applyPayloadChannelRegistration();
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= revertX && mx < revertX + REVERT_W) {
                revertPayloadChannelRegistration();
                return true;
            }
            if (button == 0 && my >= y && my < y + 16 && mx >= allOffX && mx < allOffX + ALL_OFF_W) {
                payloadListeners.disableAllInMemory();
                payloadRegistrations.disableAllInMemory();
                markPayloadChannelConfigDirty();
                rebuildRows();
                dirty = true;
                return true;
            }

            int lockX = panelX + panelWidth - 8 - REGISTRATION_LOCK_W;
            int lockY = registrationButtonY();
            if (button == 0 && my >= lockY && my < lockY + 16
                && mx >= lockX && mx < lockX + REGISTRATION_LOCK_W) {
                if (AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
                    disablePayloadRegistration();
                } else {
                    openRegistrationWarning();
                }
                return true;
            }

            int listTop = listTopY();
            int listH = listenerListHeight();
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(rows.size() * ROW_H, listH, panelX + panelWidth - 7, listTop, 3, listH, rowScroll);
            if (button == 0 && metrics.hasScroll() && metrics.contains((int) mx, (int) my)) {
                draggingScroll = true;
                scrollGrabOffset = (int) my - metrics.thumbY();
                return true;
            }
            if (button == 0 && my >= listTop && my < listTop + listH && mx >= listLeft() && mx < listRight()) {
                int index = (int) ((my - listTop + rowScroll) / ROW_H);
                if (index >= 0 && index < rows.size()) {
                    ListenerRow row = rows.get(index);
                    if (row.kind == ListenerRowKind.REGISTRATION) {
                        boolean editable = isEditableRegistration(row.registration);
                        int removeX = editable ? listRight() - REMOVE_COL_W : listRight();
                        int editX = editable ? removeX - REMOVE_COL_W : listRight();
                        if (editable && mx >= removeX && mx <= removeX + REMOVE_COL_W) {
                            mutateRowsKeepingAnchor(index, () -> {
                                removeRegistrationAndLinkedHighlight(row.registrationIndex, row.registration);
                                markPayloadChannelConfigDirty();
                            });
                        } else if (editable && mx >= editX && mx <= editX + REMOVE_COL_W) {
                            beginEditRegistration(row.registrationIndex, row.registration);
                        } else {
                            mutateRowsKeepingAnchor(index, () -> {
                                payloadRegistrations.toggleInMemory(row.registrationIndex);
                                markPayloadChannelConfigDirty();
                            });
                        }
                        dirty = true;
                        return true;
                    }
                    if (row.kind == ListenerRowKind.LEARNED) {
                        if (!canEnablePatterns(List.of(row.learnedChannel))) {
                            showPayloadChannelCapToast();
                            return true;
                        }
                        mutateRowsKeepingAnchor(index, () -> {
                            payloadRegistrations.addOrEnableInMemory(row.learnedChannel, row.learnedChannel, AutismPayloadChannelRegistrations.SOURCE_LEARNED);
                            markPayloadChannelConfigDirty();
                        });
                        dirty = true;
                        return true;
                    }
                    if (row.kind == ListenerRowKind.ACTIVE) {
                        if (studyHit(mx, my, listTop + index * ROW_H - rowScroll)) {
                            startStudyCustom(row.ruleIndex, row.rule);
                            return true;
                        }
                        int ux = listRight() - REMOVE_COL_W;
                        int editX = ux - REMOVE_COL_W;
                        if (mx >= ux && mx <= ux + REMOVE_COL_W) {
                            mutateRowsKeepingAnchor(index, () -> removeCustomWatch(row.ruleIndex, row.rule));
                        } else if (mx >= editX && mx <= editX + REMOVE_COL_W) {
                            beginEditFilter(row.ruleIndex, row.rule);
                        } else {
                            mutateRowsKeepingAnchor(index, () -> toggleCustomWatch(row.ruleIndex, row.rule));
                        }
                        dirty = true;
                        return true;
                    }
                    if (row.kind == ListenerRowKind.FAMILY) {
                        int rowY = listTop + index * ROW_H - rowScroll;
                        if (studyHit(mx, my, rowY)) {
                            startStudyFamily(row.family);
                            return true;
                        }
                        if (familyExpandHit(mx, my, rowY)) {
                            if (row.family != null) {
                                String key = row.family.stableKey();
                                if (!expandedPayloadFamilies.remove(key)) expandedPayloadFamilies.add(key);
                                rebuildRows();
                            }
                            return true;
                        }
                        mutateRowsKeepingAnchor(index, () -> togglePresetFamily(row.family));
                        dirty = true;
                        return true;
                    }
                    if (row.kind == ListenerRowKind.PRESET) {
                        if (studyHit(mx, my, listTop + index * ROW_H - rowScroll)) {
                            if (row.preset != null && row.preset.kind().templateLike()) {
                                beginUsePresetTemplate(row.preset);
                            } else {
                                startStudyPreset(row.preset);
                            }
                            return true;
                        }
                        if (row.preset != null
                            && row.preset.kind() == AutismPayloadChannelListeners.PresetKind.EXACT
                            && !isPresetEnabled(row.preset)
                            && !canEnablePatterns(List.of(row.preset.pattern()))) {
                            showPayloadChannelCapToast();
                            return true;
                        }
                        mutateRowsKeepingAnchor(index, () -> togglePreset(row.preset));
                        dirty = true;
                        return true;
                    }
                    if (row.kind == ListenerRowKind.HEADER && groupPresetCountCache.getOrDefault(cleanPresetGroup(row.header), 0) > 0) {
                        if (studyHit(mx, my, listTop + index * ROW_H - rowScroll)) {
                            startStudyGroup(row.header);
                            return true;
                        }
                        mutateRowsKeepingAnchor(index, () -> togglePresetGroup(row.header));
                        dirty = true;
                        return true;
                    }
                }
            }
            return isMouseOver(mx, my);
        }

        private boolean isEditableRegistration(AutismConfig.PayloadChannelRegistrationRule rule) {
            if (rule == null) return false;
            String source = rule.source == null ? "" : rule.source.trim().toLowerCase(Locale.ROOT);
            return AutismPayloadChannelRegistrations.SOURCE_CUSTOM.equals(source)
                || AutismPayloadChannelRegistrations.SOURCE_LEARNED.equals(source);
        }

        private void beginEditRegistration(int index, AutismConfig.PayloadChannelRegistrationRule rule) {
            if (!isEditableRegistration(rule)) {
                AutismNotifications.warning("Presets can be toggled, not edited.");
                return;
            }
            editingRegistrationIndex = index;
            editingRuleIndex = -1;
            customPattern.setText(AutismPayloadChannelRegistrations.normalizeChannel(rule.channel));
            customPattern.setPlaceholder(Component.literal("edit exact channel"));
            customPattern.setFocused(true);
        }

        private void beginEditFilter(int index, AutismConfig.PayloadChannelFilterRule rule) {
            if (rule == null || rule.preset) return;
            editingRuleIndex = index;
            editingRegistrationIndex = -1;
            customPattern.setText(AutismPayloadChannelListeners.normalizePattern(rule.pattern));
            customPattern.setPlaceholder(Component.literal("edit highlight pattern"));
            customPattern.setFocused(true);
        }

        private void saveCustomRuleField() {
            String pattern = customPattern.getText() == null ? "" : customPattern.getText().trim();
            if (pattern.isBlank()) {
                AutismNotifications.warning("Enter a payload channel first.");
                return;
            }
            if (!isEditingCustomEntry() && !canEnablePatterns(List.of(pattern))) {
                showPayloadChannelCapToast();
                return;
            }
            String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
            String label = normalized;
            if (editingRegistrationIndex >= 0) {
                saveEditedRegistration(normalized);
                return;
            }
            if (editingRuleIndex >= 0) {
                saveEditedFilter(normalized);
                return;
            }
            boolean filterChanged = payloadListeners.addCustomInMemory(label, normalized, AutismPayloadChannelListeners.Direction.ANY);
            boolean registrationChanged = false;
            if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                registrationChanged = payloadRegistrations.addOrEnableInMemory(label, normalized, AutismPayloadChannelRegistrations.SOURCE_CUSTOM);
            }
            if (filterChanged || registrationChanged) {
                customPattern.setText("");
                markPayloadChannelConfigDirty();
                rebuildRows();
                rowScroll = 0;
                rowsScroll.jumpTo(0, Math.max(0, rows.size() * ROW_H - listenerListHeight()));
                dirty = true;
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                    AutismNotifications.copied("Payload channel added. Press Apply to register.");
                } else {
                    AutismNotifications.copied("Payload highlight added.");
                }
            } else {
                AutismNotifications.warning("Payload channel already exists.");
            }
        }

        private void saveEditedRegistration(String normalized) {
            if (!AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                AutismNotifications.warning("Exact registered channels need namespace:path.");
                return;
            }
            List<AutismConfig.PayloadChannelRegistrationRule> regs = payloadRegistrations.mutableRules();
            if (editingRegistrationIndex < 0 || editingRegistrationIndex >= regs.size()) {
                clearCustomEditState();
                rebuildRows();
                return;
            }
            int existing = payloadRegistrations.indexOf(normalized);
            String oldChannel = "";
            AutismConfig.PayloadChannelRegistrationRule currentRule =
                editingRegistrationIndex >= 0 && editingRegistrationIndex < regs.size() ? regs.get(editingRegistrationIndex) : null;
            if (currentRule != null) oldChannel = AutismPayloadChannelRegistrations.normalizeChannel(currentRule.channel);
            if (existing >= 0 && existing != editingRegistrationIndex) {
                payloadRegistrations.removeInMemory(editingRegistrationIndex);
                payloadRegistrations.addOrEnableInMemory(normalized, normalized, AutismPayloadChannelRegistrations.SOURCE_CUSTOM);
            } else {
                AutismConfig.PayloadChannelRegistrationRule rule = regs.get(editingRegistrationIndex);
                if (rule != null && isEditableRegistration(rule)) {
                    rule.channel = normalized;
                    rule.label = normalized;
                    rule.source = AutismPayloadChannelRegistrations.SOURCE_CUSTOM;
                    rule.enabled = true;
                }
            }
            if (!oldChannel.isBlank() && !oldChannel.equals(normalized)) removeCustomHighlightByPattern(oldChannel);
            payloadListeners.addCustomInMemory(normalized, normalized, AutismPayloadChannelListeners.Direction.ANY);
            clearCustomEditState();
            markPayloadChannelConfigDirty();
            rebuildRows();
            AutismNotifications.copied("Payload channel edited. Press Apply to register.");
        }

        private void saveEditedFilter(String normalized) {
            List<AutismConfig.PayloadChannelFilterRule> filters = payloadListeners.mutableRules();
            if (editingRuleIndex < 0 || editingRuleIndex >= filters.size()) {
                clearCustomEditState();
                rebuildRows();
                return;
            }
            AutismConfig.PayloadChannelFilterRule rule = filters.get(editingRuleIndex);
            if (rule != null && !rule.preset) {
                String oldPattern = AutismPayloadChannelListeners.normalizePattern(rule.pattern);
                rule.pattern = normalized;
                rule.label = normalized;
                rule.enabled = true;
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(oldPattern)) {
                    removeCustomRegistration(oldPattern);
                }
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                    payloadRegistrations.addOrEnableInMemory(normalized, normalized, AutismPayloadChannelRegistrations.SOURCE_CUSTOM);
                }
            }
            clearCustomEditState();
            markPayloadChannelConfigDirty();
            rebuildRows();
            AutismNotifications.copied("Payload highlight edited.");
        }

        private void togglePreset(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null) return;
            String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
            boolean enabled = isPresetEnabled(preset);
            if (!enabled) {
                payloadListeners.addOrEnablePresetInMemory(preset);
                if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) {
                    payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, AutismPayloadChannelRegistrations.SOURCE_PRESET);
                }
                markPayloadChannelConfigDirty();
                return;
            }
            if (payloadListeners.isRuleEnabledFor(preset)) {
                payloadListeners.toggleOrAddPresetInMemory(preset);
            }
            if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) {
                removePresetRegistration(pattern);
            }
            markPayloadChannelConfigDirty();
        }

        private void toggleCustomWatch(int ruleIndex, AutismConfig.PayloadChannelFilterRule rule) {
            if (rule == null) return;
            String pattern = AutismPayloadChannelListeners.normalizePattern(rule.pattern);
            boolean enabling = !rule.enabled;
            payloadListeners.toggleInMemory(ruleIndex);
            if (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) {
                if (enabling) {
                    payloadRegistrations.addOrEnableInMemory(rule.label, pattern, AutismPayloadChannelRegistrations.SOURCE_CUSTOM);
                } else {
                    removeCustomRegistration(pattern);
                }
            }
            markPayloadChannelConfigDirty();
        }

        private void removeCustomWatch(int ruleIndex, AutismConfig.PayloadChannelFilterRule rule) {
            String pattern = rule == null ? "" : AutismPayloadChannelListeners.normalizePattern(rule.pattern);
            payloadListeners.removeInMemory(ruleIndex);
            if (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) removeCustomRegistration(pattern);
            markPayloadChannelConfigDirty();
        }

        private void removeRegistrationAndLinkedHighlight(int registrationIndex, AutismConfig.PayloadChannelRegistrationRule rule) {
            String channel = rule == null ? "" : AutismPayloadChannelRegistrations.normalizeChannel(rule.channel);
            payloadRegistrations.removeInMemory(registrationIndex);
            removeCustomHighlightByPattern(channel);
        }

        private void removeCustomHighlightByPattern(String pattern) {
            String normalized = AutismPayloadChannelListeners.normalizePattern(pattern);
            if (normalized.isBlank()) return;
            List<AutismConfig.PayloadChannelFilterRule> filters = payloadListeners.mutableRules();
            for (int i = filters.size() - 1; i >= 0; i--) {
                AutismConfig.PayloadChannelFilterRule rule = filters.get(i);
                if (rule == null || rule.preset) continue;
                if (normalized.equals(AutismPayloadChannelListeners.normalizePattern(rule.pattern))) {
                    filters.remove(i);
                }
            }
        }

        private void beginUsePresetTemplate(AutismPayloadChannelListeners.Preset preset) {
            if (preset == null) return;
            editingRuleIndex = -1;
            editingRegistrationIndex = -1;
            String prefix = AutismPayloadChannelListeners.templatePrefix(preset);
            customPattern.setText(prefix);
            customPattern.setPlaceholder(Component.literal("finish exact channel from " + preset.label()));
            customPattern.setFocused(true);
            AutismNotifications.copied("Finish the exact payload channel, then Add.");
        }

        private void startStudyPreset(AutismPayloadChannelListeners.Preset preset) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            if (preset == null) return;
            startStudy("Preset: " + preset.label(), List.of(preset), List.of(preset.pattern()));
        }

        private void startStudyGroup(String group) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            if (group == null || group.isBlank()) return;
            List<AutismPayloadChannelListeners.Preset> presets = new ArrayList<>();
            List<String> patterns = new ArrayList<>();
            PayloadChannelCategoryRow category = buildPayloadChannelCategories().get(cleanPresetGroup(group));
            if (category == null) return;
            for (PayloadChannelFamilyRow family : category.sortedFamilies) {
                for (AutismPayloadChannelListeners.Preset preset : family.sortedPresets()) {
                    presets.add(preset);
                    patterns.add(preset.pattern());
                }
            }
            for (AutismPayloadChannelListeners.Preset preset : category.sortedStandalonePresets) {
                presets.add(preset);
                patterns.add(preset.pattern());
            }
            startStudy("Category: " + group, presets, patterns);
        }

        private void startStudyFamily(PayloadChannelFamilyRow family) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            if (family == null || family.presets.isEmpty()) return;
            List<String> patterns = new ArrayList<>();
            for (AutismPayloadChannelListeners.Preset preset : family.presets) {
                patterns.add(preset.pattern());
            }
            startStudy("Family: " + family.label, new ArrayList<>(family.presets), patterns);
        }

        private void startStudyCustom(int ruleIndex, AutismConfig.PayloadChannelFilterRule rule) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            if (rule == null) return;
            if (!rule.enabled) {
                payloadListeners.toggleInMemory(ruleIndex);
                markPayloadChannelConfigDirty();
            }
            String pattern = AutismPayloadChannelListeners.normalizePattern(rule.pattern);
            String label = rule.label == null || rule.label.isBlank() ? pattern : rule.label.trim();
            int skipped = enableExactChannelsForPattern(label, pattern, AutismPayloadChannelRegistrations.SOURCE_CUSTOM);
            markPayloadChannelConfigDirty();
            rebuildRows();
            beginPayloadStudy(label, List.of(pattern), skipped);
        }

        private void startStudy(String label, List<AutismPayloadChannelListeners.Preset> presets, List<String> patterns) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            if (presets == null || presets.isEmpty()) return;
            int skipped = 0;
            for (AutismPayloadChannelListeners.Preset preset : presets) {
                if (preset == null) continue;
                payloadListeners.addOrEnablePresetInMemory(preset);
                skipped += enableExactChannelsForPattern(preset.label(), preset.pattern(), AutismPayloadChannelRegistrations.SOURCE_PRESET);
            }
            markPayloadChannelConfigDirty();
            rebuildRows();
            beginPayloadStudy(label, patterns, skipped);
        }

        private int enableExactChannelsForPattern(String label, String pattern, String source) {
            AutismPayloadChannelSubscriptionManager.RegistrationImpact impact =
                impactForPatternCached(pattern);
            int skipped = 0;
            for (String channel : impact.exactChannels()) {
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) continue;
                if (!payloadRegistrations.hasEnabled(normalized) && !canEnablePatterns(List.of(normalized))) {
                    skipped++;
                    continue;
                }
                payloadRegistrations.addOrEnableInMemory(label, normalized, source);
            }
            return skipped;
        }

        private void beginPayloadStudy(String label, List<String> patterns, int skipped) {
            if (configurationOnly) return;
            if (!ensurePayloadRegistrationUnlockedForActiveUse()) return;
            savePendingPayloadChannelConfig();
            AutismPayloadChannelSubscriptionManager.requestRefresh();
            AutismPayloadChannelSubscriptionManager.tick(MC, false);
            AutismPayloadChannelSubscriptionManager.Status status =
                AutismPayloadChannelSubscriptionManager.status();
            if (skipped > 0 || (status != null && status.skippedCount() > 0)) {
                int count = status == null ? 0 : status.registeredCount();
                int limit = status == null ? 96 : status.limit();
                AutismNotifications.error("Payload channel cap: registered " + count + "/" + limit + ".");
            }
            List<String> applied = status == null ? List.of() : status.channels();
            AutismPayloadStudySession.start(label, patterns == null ? List.of() : patterns, applied);
            setVisible(false);
            if (MC != null) MC.gui.setScreen(null);
        }

        private void togglePresetGroup(String group) {
            if (group == null || group.isBlank()) return;
            boolean enable = !isPresetGroupFullyEnabled(group);
            boolean capHit = false;
            PayloadChannelCategoryRow category = buildPayloadChannelCategories().get(cleanPresetGroup(group));
            if (category == null) return;
            List<AutismPayloadChannelListeners.Preset> presets = new ArrayList<>();
            for (PayloadChannelFamilyRow family : category.sortedFamilies) presets.addAll(family.sortedPresets());
            presets.addAll(category.sortedStandalonePresets);
            for (AutismPayloadChannelListeners.Preset preset : presets) {
                String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
                if (enable) {
                    if (AutismPayloadChannelListeners.isRegisterablePreset(preset) && !canEnablePatterns(List.of(pattern))) {
                        capHit = true;
                        continue;
                    }
                    payloadListeners.addOrEnablePresetInMemory(preset);
                    if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) {
                        payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, AutismPayloadChannelRegistrations.SOURCE_PRESET);
                    }
                } else {
                    if (payloadListeners.isRuleEnabledFor(preset)) payloadListeners.toggleOrAddPresetInMemory(preset);
                    if (AutismPayloadChannelListeners.isRegisterablePreset(preset)) removePresetRegistration(pattern);
                }
            }
            if (capHit) showPayloadChannelCapToast();
            markPayloadChannelConfigDirty();
        }

        private void togglePresetFamily(PayloadChannelFamilyRow family) {
            if (family == null || family.presets.isEmpty()) return;
            boolean enable = !isFamilyFullyEnabled(family);
            boolean capHit = false;
            List<AutismPayloadChannelListeners.Preset> presets = new ArrayList<>(family.presets);
            sortPresetRows(presets);
            for (AutismPayloadChannelListeners.Preset preset : presets) {
                String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) continue;
                if (enable) {
                    if (!payloadRegistrations.hasEnabled(pattern) && !canEnablePatterns(List.of(pattern))) {
                        capHit = true;
                        continue;
                    }
                    payloadListeners.addOrEnablePresetInMemory(preset);
                    payloadRegistrations.addOrEnableInMemory(preset.label(), pattern, AutismPayloadChannelRegistrations.SOURCE_PRESET);
                } else {
                    if (payloadListeners.isRuleEnabledFor(preset)) payloadListeners.toggleOrAddPresetInMemory(preset);
                    removePresetRegistration(pattern);
                }
            }
            if (capHit) showPayloadChannelCapToast();
            markPayloadChannelConfigDirty();
        }

        private void removePresetRegistration(String channel) {
            int index = payloadRegistrations.indexOf(channel);
            if (index < 0) return;
            List<AutismConfig.PayloadChannelRegistrationRule> rules = payloadRegistrations.rules();
            if (index >= rules.size()) return;
            AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            if (rule != null && AutismPayloadChannelRegistrations.SOURCE_PRESET.equals(rule.source)) {
                payloadRegistrations.removeInMemory(index);
            }
        }

        private void removeCustomRegistration(String channel) {
            int index = payloadRegistrations.indexOf(channel);
            if (index < 0) return;
            List<AutismConfig.PayloadChannelRegistrationRule> rules = payloadRegistrations.rules();
            if (index >= rules.size()) return;
            AutismConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            if (rule == null) return;
            String source = rule.source == null ? "" : rule.source;
            if (AutismPayloadChannelRegistrations.SOURCE_CUSTOM.equals(source)
                || AutismPayloadChannelRegistrations.SOURCE_LEARNED.equals(source)) {
                payloadRegistrations.removeInMemory(index);
            }
        }

        private void captureLearnedChannels(boolean replaceExisting) {
            List<String> learned = AutismPayloadChannelSubscriptionManager.learnedChannels();
            if (learned.isEmpty()) {
                AutismNotifications.warning("No captured payload channels yet.");
                return;
            }
            if (replaceExisting) {
                payloadRegistrations.disableAllInMemory();
            }
            int added = 0;
            int skipped = 0;
            for (String channel : learned) {
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) continue;
                if (!payloadRegistrations.hasEnabled(normalized) && !canEnablePatterns(List.of(normalized))) {
                    skipped++;
                    continue;
                }
                boolean regChanged = payloadRegistrations.addOrEnableInMemory(normalized, normalized, AutismPayloadChannelRegistrations.SOURCE_LEARNED);
                payloadListeners.addCustomInMemory(normalized, normalized, AutismPayloadChannelListeners.Direction.ANY);
                if (regChanged) added++;
            }
            markPayloadChannelConfigDirty();
            rebuildRows();
            dirty = true;
            if (skipped > 0) {
                AutismNotifications.error("Captured " + added + ", skipped " + skipped + " at channel cap.");
            } else {
                AutismNotifications.copied((replaceExisting ? "Reset to " : "Captured ") + added + " payload channels.");
            }
        }

        private void enableAllKnownExactChannels() {
            int added = 0;
            int skipped = 0;
            for (String channel : AutismPayloadChannelSubscriptionManager.learnedChannels()) {
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) continue;
                if (!payloadRegistrations.hasEnabled(normalized) && !canEnablePatterns(List.of(normalized))) {
                    skipped++;
                    continue;
                }
                boolean regChanged = payloadRegistrations.addOrEnableInMemory(normalized, normalized, AutismPayloadChannelRegistrations.SOURCE_LEARNED);
                payloadListeners.addCustomInMemory(normalized, normalized, AutismPayloadChannelListeners.Direction.ANY);
                if (regChanged) added++;
            }
            buildPayloadChannelCategories();
            for (AutismPayloadChannelListeners.Preset preset : presetByExactChannelCache.values()) {
                if (preset.kind() != AutismPayloadChannelListeners.PresetKind.EXACT) continue;
                payloadListeners.addOrEnablePresetInMemory(preset);
                String normalized = AutismPayloadChannelRegistrations.normalizeChannel(preset.pattern());
                if (!AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) continue;
                if (!payloadRegistrations.hasEnabled(normalized) && !canEnablePatterns(List.of(normalized))) {
                    skipped++;
                    continue;
                }
                boolean regChanged = payloadRegistrations.addOrEnableInMemory(preset.label(), normalized, AutismPayloadChannelRegistrations.SOURCE_PRESET);
                if (regChanged) added++;
            }
            markPayloadChannelConfigDirty();
            rebuildRows();
            rowScroll = 0;
            rowsScroll.jumpTo(0, Math.max(0, rows.size() * ROW_H - listenerListHeight()));
            dirty = true;
            if (skipped > 0) {
                AutismNotifications.error("Known channels enabled with " + skipped + " skipped at cap.");
            } else {
                AutismNotifications.copied("Known exact channels enabled. Press Apply.");
            }
        }

        private boolean canEnablePatterns(Collection<String> patterns) {
            return localProjectedChannels(patterns).size() <= cachedRegistrationLimit;
        }

        private LinkedHashSet<String> localProjectedChannels(Collection<String> patterns) {
            LinkedHashSet<String> projected = new LinkedHashSet<>();
            for (AutismConfig.PayloadChannelRegistrationRule rule : payloadRegistrations.rules()) {
                if (rule == null || !rule.enabled) continue;
                String channel = AutismPayloadChannelRegistrations.normalizeChannel(rule.channel);
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(channel)) projected.add(channel);
            }
            if (patterns != null) {
                for (String pattern : patterns) {
                    AutismPayloadChannelSubscriptionManager.RegistrationImpact impact =
                        impactForPatternCached(pattern);
                    for (String channel : impact.exactChannels()) {
                        String normalized = AutismPayloadChannelRegistrations.normalizeChannel(channel);
                        if (AutismPayloadChannelRegistrations.isRegisterableChannel(normalized)) projected.add(normalized);
                    }
                }
            }
            return projected;
        }

        private int payloadRegistrationLimit() {
            return cachedRegistrationLimit;
        }

        private int payloadRegistrationLimitFromStatus() {
            AutismPayloadChannelSubscriptionManager.Status status =
                AutismPayloadChannelSubscriptionManager.status();
            return status == null || status.limit() <= 0 ? 96 : status.limit();
        }

        private List<String> groupPatterns(String group) {
            if (group == null || group.isBlank()) return List.of();
            List<String> patterns = new ArrayList<>();
            for (AutismPayloadChannelListeners.Preset preset : payloadListeners.presets()) {
                if (group.equals(preset.group())) patterns.add(preset.pattern());
            }
            return patterns;
        }

        private void showPayloadChannelCapToast() {
            int limit = payloadRegistrationLimit();
            int count = Math.min(enabledRegistrationChannelsCache.size(), limit);
            AutismNotifications.error("Payload channel cap reached: " + count + "/" + limit);
        }

        private String computePendingRegistrationLabel() {
            AutismPayloadChannelSubscriptionManager.Status status =
                AutismPayloadChannelSubscriptionManager.status();
            Set<String> applied = new LinkedHashSet<>(status == null ? List.of() : status.channels());
            Set<String> wanted = enabledRegistrationChannelsCache;
            int added = 0;
            for (String channel : wanted) {
                if (!applied.contains(channel)) added++;
            }
            int removed = 0;
            for (String channel : applied) {
                if (!wanted.contains(channel)) removed++;
            }
            if (added == 0 && removed == 0) return "";
            return "  Pending +" + added + " / -" + removed;
        }

        private void applyPayloadChannelRegistration() {
            savePendingPayloadChannelConfig();
            if (!AutismPayloadChannelSubscriptionManager.isRegistrationUnlocked()) {
                AutismPayloadChannelSubscriptionManager.requestRefresh();
                AutismPayloadChannelSubscriptionManager.tick(MC, false);
                AutismNotifications.warning("Registration locked. Saved, nothing sent.");
                rebuildRows();
                return;
            }
            AutismPayloadChannelSubscriptionManager.requestRefresh();
            AutismPayloadChannelSubscriptionManager.tick(MC, false);
            AutismPayloadChannelSubscriptionManager.Status status =
                AutismPayloadChannelSubscriptionManager.status();
            if (status != null && status.skippedCount() > 0) {
                AutismNotifications.error("Payload channel cap reached: " + status.registeredCount() + "/" + status.limit());
            } else {
                AutismNotifications.copied("Payload channels applied.");
            }
            rebuildRows();
        }

        private void revertPayloadChannelRegistration() {
            AutismPayloadChannelSubscriptionManager.Status status =
                AutismPayloadChannelSubscriptionManager.status();
            payloadRegistrations.replaceWithApplied(status == null ? List.of() : status.channels());
            payloadRegistrations.load();
            rebuildRows();
            AutismNotifications.copied("Payload channels reverted.");
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (draggingWindow && button == 0) {
                setBounds(new AutismWindowLayout((int) Math.round(mx - dragOffsetX), (int) Math.round(my - dragOffsetY),
                    panelWidth, panelHeight, visible, collapsed));
                return true;
            }
            if (draggingScroll && button == 0) {
                int listTop = listTopY();
                int listH = listenerListHeight();
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(rows.size() * ROW_H, listH, panelX + panelWidth - 7, listTop, 3, listH, rowScroll);
                rowScroll = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(metrics, (int) my, scrollGrabOffset), ROW_H, metrics.maxScroll());
                return true;
            }
            if (search.mouseDragged(mx, my, button, dx, dy)) return true;
            if (customPattern.mouseDragged(mx, my, button, dx, dy)) return true;
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (button == 0 && draggingWindow) {
                draggingWindow = false;
                saveLayout();
                return true;
            }
            if (button == 0 && draggingScroll) {
                draggingScroll = false;
                return true;
            }
            if (search.mouseReleased(mx, my, button)) return true;
            if (customPattern.mouseReleased(mx, my, button)) return true;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            if (!visible || collapsed || !isMouseOver(mx, my)) return false;
            int listTop = listTopY();
            int listH = listenerListHeight();
            int maxScroll = Math.max(0, rows.size() * ROW_H - listH);
            rowScroll = quantizeScrollOffset(rowScroll - (int) Math.signum(amount) * ROW_H, ROW_H, maxScroll);
            rowsScroll.jumpTo(rowScroll, maxScroll);
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!visible) return false;
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (search.isFocused()) return search.keyPressed(event);
            if (customPattern.isFocused()) return customPattern.keyPressed(event);
            return false;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (!visible) return false;
            CharacterEvent event = new CharacterEvent(chr);
            if (search.isFocused()) return search.charTyped(event);
            if (customPattern.isFocused()) return customPattern.charTyped(event);
            return false;
        }

        @Override
        public boolean hasTextFieldFocused() {
            return visible && (search.isFocused() || customPattern.isFocused());
        }

        @Override
        public void clearTextFieldFocus() {
            search.setFocused(false);
            customPattern.setFocused(false);
        }
    }

    private record PayloadChannelFamilyKey(String group, String key, String label) {
        String stableKey() {
            return group + "|" + key;
        }
    }

    private static final class PayloadChannelFamilyRow {
        final String group;
        final String key;
        final String label;
        final List<AutismPayloadChannelListeners.Preset> presets = new ArrayList<>();
        List<AutismPayloadChannelListeners.Preset> sortedPresets = List.of();
        private Set<String> exactChannelsCache;

        PayloadChannelFamilyRow(PayloadChannelFamilyKey familyKey) {
            this.group = familyKey.group();
            this.key = familyKey.key();
            this.label = familyKey.label();
        }

        String stableKey() {
            return group + "|" + key;
        }

        int exactChannelCount() {
            return exactChannels().size();
        }

        Set<String> exactChannels() {
            if (exactChannelsCache != null) return exactChannelsCache;
            LinkedHashSet<String> channels = new LinkedHashSet<>();
            for (AutismPayloadChannelListeners.Preset preset : presets) {
                if (preset == null) continue;
                String pattern = AutismPayloadChannelListeners.normalizePattern(preset.pattern());
                if (AutismPayloadChannelRegistrations.isRegisterableChannel(pattern)) channels.add(pattern);
            }
            exactChannelsCache = Collections.unmodifiableSet(channels);
            return exactChannelsCache;
        }

        List<AutismPayloadChannelListeners.Preset> sortedPresets() {
            return sortedPresets == null || sortedPresets.isEmpty() ? presets : sortedPresets;
        }
    }

    private static final class PayloadChannelCategoryRow {
        final String group;
        final Map<String, PayloadChannelFamilyRow> families = new LinkedHashMap<>();
        final List<AutismPayloadChannelListeners.Preset> standalonePresets = new ArrayList<>();
        List<PayloadChannelFamilyRow> sortedFamilies = List.of();
        List<AutismPayloadChannelListeners.Preset> sortedStandalonePresets = List.of();

        PayloadChannelCategoryRow(String group) {
            this.group = group;
        }

        PayloadChannelFamilyRow family(PayloadChannelFamilyKey familyKey) {
            return families.computeIfAbsent(familyKey.stableKey(), unused -> new PayloadChannelFamilyRow(familyKey));
        }
    }

    private enum ListenerRowKind { HEADER, REGISTRATION, LEARNED, ACTIVE, FAMILY, PRESET, EMPTY }

    private static final class ListenerRow {
        final ListenerRowKind kind;
        final String header;
        final int ruleIndex;
        final AutismConfig.PayloadChannelFilterRule rule;
        final int registrationIndex;
        final AutismConfig.PayloadChannelRegistrationRule registration;
        final String learnedChannel;
        final PayloadChannelFamilyRow family;
        final AutismPayloadChannelListeners.Preset preset;

        private ListenerRow(ListenerRowKind kind, String header, int ruleIndex,
                            AutismConfig.PayloadChannelFilterRule rule,
                            int registrationIndex,
                            AutismConfig.PayloadChannelRegistrationRule registration,
                            String learnedChannel,
                            PayloadChannelFamilyRow family,
                            AutismPayloadChannelListeners.Preset preset) {
            this.kind = kind;
            this.header = header;
            this.ruleIndex = ruleIndex;
            this.rule = rule;
            this.registrationIndex = registrationIndex;
            this.registration = registration;
            this.learnedChannel = learnedChannel;
            this.family = family;
            this.preset = preset;
        }

        static ListenerRow header(String label) {
            return new ListenerRow(ListenerRowKind.HEADER, label, -1, null, -1, null, null, null, null);
        }

        static ListenerRow empty(String label) {
            return new ListenerRow(ListenerRowKind.EMPTY, label, -1, null, -1, null, null, null, null);
        }

        static ListenerRow registration(int index, AutismConfig.PayloadChannelRegistrationRule rule) {
            return new ListenerRow(ListenerRowKind.REGISTRATION, null, -1, null, index, rule, null, null, null);
        }

        static ListenerRow learned(String channel) {
            return new ListenerRow(ListenerRowKind.LEARNED, null, -1, null, -1, null, channel, null, null);
        }

        static ListenerRow active(int index, AutismConfig.PayloadChannelFilterRule rule) {
            return new ListenerRow(ListenerRowKind.ACTIVE, null, index, rule, -1, null, null, null, null);
        }

        static ListenerRow family(PayloadChannelFamilyRow family) {
            return new ListenerRow(ListenerRowKind.FAMILY, null, -1, null, -1, null, null, family, null);
        }

        static ListenerRow preset(AutismPayloadChannelListeners.Preset preset) {
            return new ListenerRow(ListenerRowKind.PRESET, null, -1, null, -1, null, null, null, preset);
        }
    }

    private final class BlockedPacketListOverlay extends AutismOverlayBase {
        private final AutismChatField search = new AutismChatField(MC, textRenderer, 0, 0, 120, 16, false);
        private final ScrollState rowsScroll = new ScrollState();
        private final List<Class<? extends Packet<?>>> allPackets = new ArrayList<>();
        private final Set<Class<? extends Packet<?>>> c2sSet = new HashSet<>();
        private final List<Class<? extends Packet<?>>> rows = new ArrayList<>();
        private int rowScroll;
        private boolean draggingScroll;
        private int scrollGrabOffset;
        private boolean showBlockedOnly;
        private boolean draggingWindow;
        private double dragOffsetX;
        private double dragOffsetY;
        private boolean searchRowsDirty;

        private static final int BLOCKED_W = 54;
        private static final int CLEAR_W = 44;
        private static final int RESET_W = 44;

        BlockedPacketListOverlay() {
            super("PacketLoggerBlockedList", 250, 248);
            this.panelX = 260;
            this.panelY = 56;
            this.visible = false;
            search.setPlaceholder(Component.literal("Search packets..."));
            search.setMaxLength(120);
            search.setChangedListener(value -> searchRowsDirty = true);

            List<Class<? extends Packet<?>>> c2s = new ArrayList<>(AutismPacketRegistry.getC2SPackets());
            List<Class<? extends Packet<?>>> s2c = new ArrayList<>(AutismPacketRegistry.getS2CPackets());
            Comparator<Class<? extends Packet<?>>> byName =
                Comparator.comparing(AutismPacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER);
            c2s.sort(byName);
            s2c.sort(byName);
            c2sSet.addAll(c2s);
            allPackets.addAll(c2s);
            allPackets.addAll(s2c);
        }

        private int controlsRowY() { return panelY + HEADER_H + 5 + 20; }
        private int listTopY() { return controlsRowY() + 21; }

        private String blockKey(Class<? extends Packet<?>> cls) {
            String name = AutismPacketRegistry.getName(cls);
            return name != null ? name : AutismPacketNamer.getFriendlyName(cls);
        }

        @Override
        public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
            if (!visible) return;
            if (searchRowsDirty) {
                searchRowsDirty = false;
                rebuildRows();
            }
            AutismWindowLayout bounds = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
            panelX = bounds.x;
            panelY = bounds.y;
            renderWindowFrame(ctx, mx, my, bounds, "Block List", collapsed, false);
            boolean clip = beginWindowBodyClip(ctx, bounds, collapsed);
            if (!clip) {
                renderWindowInactiveOverlay(ctx, bounds, collapsed, false);
                return;
            }
            try {
                int x = panelX + 6;
                int y = panelY + HEADER_H + 5;
                int rowH = 16;
                search.setX(x);
                search.setY(y);
                search.setWidth(Math.max(80, panelWidth - 12));
                search.setHeight(rowH);
                search.render(ctx, mx, my, delta);

                y = controlsRowY();
                int bx = x;
                CompactOverlayControls.tab(ctx, textRenderer, bx, y, BLOCKED_W, rowH, "Blocked", showBlockedOnly, mx, my);
                int clearX = bx + BLOCKED_W + 4;
                CompactOverlayControls.action(ctx, textRenderer, clearX, y, CLEAR_W, rowH, "Clear", CompactOverlayButton.Variant.DANGER, true, mx, my);
                int resetX = clearX + CLEAR_W + 4;
                CompactOverlayControls.action(ctx, textRenderer, resetX, y, RESET_W, rowH, "Reset", CompactOverlayButton.Variant.GHOST, true, mx, my);
                int countX = resetX + RESET_W + 8;
                drawUiText(ctx, blockedNames.size() + " blocked", UiTone.MUTED, AutismColors.textSecondary(), countX, y + 4);

                int listTop = listTopY();
                int listH = Math.max(20, panelY + panelHeight - listTop - 6);
                int contentH = rows.size() * lineHeight();
                int maxScroll = Math.max(0, contentH - listH);
                rowScroll = quantizeScrollOffset(rowScroll, lineHeight(), maxScroll);
                rowsScroll.setTarget(rowScroll, maxScroll);
                int drawScroll = rowsScroll.tick(delta, maxScroll);
                autismclient.gui.vanillaui.UiScissorStack.global().push(ctx,
                    autismclient.gui.vanillaui.UiBounds.of(panelX + 4, listTop, panelWidth - 8, listH));
                int base = listTop - drawScroll;
                int rowHeight = lineHeight();
                int firstVisible = Math.max(0, drawScroll / rowHeight);
                int lastVisible = Math.min(rows.size() - 1, (drawScroll + listH - 1) / rowHeight);
                for (int i = firstVisible; i <= lastVisible; i++) {
                    int ry = base + i * rowHeight;
                    Class<? extends Packet<?>> cls = rows.get(i);
                    boolean c2s = c2sSet.contains(cls);
                    boolean blocked = isBlockedName(cls, AutismPacketNamer.getFriendlyName(cls));
                    boolean hover = mx >= panelX + 4 && mx < panelX + panelWidth - 8 && my >= ry && my < ry + lineHeight();
                    int fill = blocked ? AutismColors.packetRowBlockedBg(hover) : AutismColors.packetRowBg(c2s, i, hover);
                    ctx.fill(panelX + 4, ry, panelX + panelWidth - 8, ry + lineHeight(), fill);
                    if (blocked) {
                        ctx.fill(panelX + 4, ry, panelX + 6, ry + lineHeight(), AutismColors.packetRowBlockedAccent());
                    }
                    int color = AutismColors.packetRowText(c2s, i);
                    String name = AutismPacketNamer.getFriendlyName(cls);
                    String label = UiText.trimToWidth(textRenderer, name, Math.max(1, panelWidth - 24), theme.fontFor(UiTone.BODY), color);
                    drawUiText(ctx, label, UiTone.BODY, color, panelX + 9, ry + 1);
                }
                autismclient.gui.vanillaui.UiScissorStack.global().pop(ctx);
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, listH, panelX + panelWidth - 5, listTop, 3, listH, drawScroll);
                CompactScrollbar.draw(ctx, metrics, metrics.contains(mx, my), draggingScroll);
            } finally {
                endWindowBodyClip(ctx, clip);
                renderWindowInactiveOverlay(ctx, bounds, collapsed, false);
            }
        }

        private void rebuildRows() {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            rows.clear();
            for (Class<? extends Packet<?>> cls : allPackets) {
                if (showBlockedOnly && !isBlockedName(cls, AutismPacketNamer.getFriendlyName(cls))) continue;
                if (!query.isEmpty()) {
                    String hay = (AutismPacketNamer.getFriendlyName(cls) + ' ' + cls.getSimpleName()).toLowerCase(Locale.ROOT);
                    if (!hay.contains(query)) continue;
                }
                rows.add(cls);
            }
            rowScroll = Math.min(rowScroll, Math.max(0, rows.size() * lineHeight()));
        }

        @Override
        public boolean usesSharedHeaderClickCollapse() {
            return true;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!visible) return false;
            AutismWindowLayout bounds = getBounds();
            if (button == 0 && isOverCloseButton(mx, my, bounds)) {
                setVisible(false);
                return true;
            }
            if (button == 0 && isOverDragBar(mx, my)) {
                draggingWindow = true;
                dragOffsetX = mx - panelX;
                dragOffsetY = my - panelY;
                return true;
            }
            if (collapsed) return true;
            int x = panelX + 6;
            if (search.mouseClicked(mx, my, button)) return true;

            int y = controlsRowY();
            if (button == 0 && my >= y && my < y + 16) {
                int clearX = x + BLOCKED_W + 4;
                int resetX = clearX + CLEAR_W + 4;
                if (mx >= x && mx < x + BLOCKED_W) {
                    showBlockedOnly = !showBlockedOnly;
                    rowScroll = 0;
                    rebuildRows();
                    return true;
                }
                if (mx >= clearX && mx < clearX + CLEAR_W) {
                    clearBlocked();
                    rebuildRows();
                    return true;
                }
                if (mx >= resetX && mx < resetX + RESET_W) {
                    resetBlockedToDefault();
                    rebuildRows();
                    return true;
                }
            }

            int listTop = listTopY();
            int listH = Math.max(20, panelY + panelHeight - listTop - 6);
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(rows.size() * lineHeight(), listH, panelX + panelWidth - 5, listTop, 3, listH, rowScroll);
            if (button == 0 && metrics.hasScroll() && metrics.contains((int) mx, (int) my)) {
                draggingScroll = true;
                scrollGrabOffset = (int) my - metrics.thumbY();
                return true;
            }
            if (button == 0 && my >= listTop) {
                int index = (int) ((my - listTop + rowScroll) / lineHeight());
                if (index >= 0 && index < rows.size()) {
                    Class<? extends Packet<?>> cls = rows.get(index);
                    String key = blockKey(cls);
                    if (isBlockedName(cls, AutismPacketNamer.getFriendlyName(cls))) {
                        unblockAllMatching(cls, AutismPacketNamer.getFriendlyName(cls));
                    } else {
                        blockPacketName(key);
                    }
                    if (showBlockedOnly) rebuildRows();
                    return true;
                }
            }
            return isMouseOver(mx, my);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (draggingWindow && button == 0) {
                setBounds(new AutismWindowLayout((int) Math.round(mx - dragOffsetX), (int) Math.round(my - dragOffsetY),
                    panelWidth, panelHeight, visible, collapsed));
                return true;
            }
            if (draggingScroll && button == 0) {
                int listTop = listTopY();
                int listH = Math.max(20, panelY + panelHeight - listTop - 6);
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(rows.size() * lineHeight(), listH, panelX + panelWidth - 5, listTop, 3, listH, rowScroll);
                rowScroll = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(metrics, (int) my, scrollGrabOffset), lineHeight(), metrics.maxScroll());
                return true;
            }
            if (search.mouseDragged(mx, my, button, dx, dy)) return true;
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (button == 0 && draggingWindow) {
                draggingWindow = false;
                saveLayout();
                return true;
            }
            if (button == 0 && draggingScroll) {
                draggingScroll = false;
                return true;
            }
            if (search.mouseReleased(mx, my, button)) return true;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            if (!visible || collapsed || !isMouseOver(mx, my)) return false;
            int listTop = listTopY();
            int listH = Math.max(20, panelY + panelHeight - listTop - 6);
            int maxScroll = Math.max(0, rows.size() * lineHeight() - listH);
            rowScroll = quantizeScrollOffset(rowScroll - (int) Math.signum(amount) * lineHeight(), lineHeight(), maxScroll);
            rowsScroll.jumpTo(rowScroll, maxScroll);
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return visible && search.isFocused() && search.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return visible && search.isFocused() && search.charTyped(new CharacterEvent(chr));
        }

        @Override
        public boolean hasTextFieldFocused() {
            return visible && search.isFocused();
        }

        @Override
        public void clearTextFieldFocus() {
            search.setFocused(false);
        }
    }

    private synchronized void clearAll() {
        pendingEntries.clear();
        bufAll.clear(); bufInventory.clear(); bufMovement.clear(); bufPayload.clear();
        packetContextTracker.reset();
        displayRows.clear(); scrollOffset = 0; contentScrollState.jumpTo(0, 0); expandedGroups.clear(); dirty = true;
    }

    private void copyToClipboard() {
        flushPending();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Packet Logger [").append(activeTab.label).append("] ===\n");
        if (!searchFilter.isEmpty()) sb.append("Search: \"").append(searchFilter).append("\"\n");
        sb.append(String.format("%-5s %-14s %-8s %s%n", "DIR", "TIME", "TICK", "PACKET"));
        sb.append("----------------------------------------------\n");

        List<LogEntry> source = getActiveBuffer();
        if (source == null) source = new ArrayList<>();
        String ls = searchFilter.toLowerCase(Locale.ROOT);
        int count = 0;
        for (LogEntry e : source) {
            if (isBlockedClass(e.packetClass)) continue;
            if (activeTab == Category.PAYLOAD && payloadFilteredOnly && payloadListenerMatch(e) == null) continue;
            if (!ls.isEmpty() && !entrySearchKey(e).contains(ls)) continue;
            if (dirFilter == 1 && !e.direction.equals("C2S")) continue;
            if (dirFilter == 2 && !e.direction.equals("S2C")) continue;
            String copiedName = e.shortName;
            if (e.payloadSnapshot != null) {
                String summary = AutismPayloadSupport.summarizeForLogger(e.payloadSnapshot, true);
                if (!summary.isBlank()) copiedName += " " + summary;
            }
            AutismPayloadChannelListeners.Match match = payloadListenerMatch(e);
            if (match != null) copiedName += " [" + match.label() + "]";
            sb.append(String.format("%-5s %-14s %-8s %s%n",
                    e.direction.equals("C2S") ? "->" : "<-",
                    TIME_FMT.format(Instant.ofEpochMilli(e.timestampMs)),
                    "T" + e.gameTick, copiedName));
            count++;
        }
        sb.append("----------------------------------------------\n");
        sb.append("Total: ").append(count).append(" packets\n");

        MC.keyboardHandler.setClipboard(sb.toString());
        AutismNotifications.copied("Copied " + count + " packets.");
    }

        private int defaultPanelWidth() {

        return 360;
    }

    private int defaultPanelHeight() {
        return 250;
    }

    private int lineHeight() {
        return 12;
    }

    private int tabHeight() {
        return 16;
    }

    private int filterRowHeight() {
        return 16;
    }

    private int filterRowGap() {
        return 2;
    }

    private int filterHeight() {
        return filterRowHeight() * 2 + filterRowGap();
    }

    private int searchFieldWidth() {
        return 148;
    }

    private int filterSearchFieldWidth() {
        return activeTab == Category.PAYLOAD ? 108 : searchFieldWidth();
    }

    private int maxVisibleRows() {
        return 22;
    }

    enum RowType { ENTRY, GROUP }

    static class DisplayRow {
        RowType type;
        LogEntry entry;
        String groupKey;
        int groupCount;
        String direction;
        Class<?> packetClass;
        AutismPayloadSupport.PayloadSnapshot payloadSnapshot;
    }

    private static long idCounter = 0;

    public static class LogEntry {
        public final long id;
        public final long timestampMs;
        public final int gameTick;
        public final String direction;
        public final String shortName;
        private volatile String searchKey;
        public final String groupKey;
        public final Class<?> packetClass;
        public final Packet<?> packetRef;
        public final boolean isInventory;
        public final boolean isMovement;
        public final boolean isPayload;
        public final String capturedBlockState;
        public final String capturedScreen;
        public final AutismPayloadSupport.PayloadSnapshot payloadSnapshot;
        public final AutismPacketContextTracker.Capture packetContext;

        public LogEntry(long ts, int tick, String dir, String name, Class<?> cls, Packet<?> ref,
                        boolean inv, boolean mov, boolean payload, String capturedBlockState, String capturedScreen,
                        AutismPayloadSupport.PayloadSnapshot payloadSnapshot,
                        AutismPacketContextTracker.Capture packetContext) {
            this.id = idCounter++;
            this.timestampMs = ts;
            this.gameTick = tick;
            this.direction = dir;
            this.shortName = name;
            this.payloadSnapshot = payloadSnapshot;
            this.groupKey = dir + ":" + name;
            this.packetClass = cls;
            this.packetRef = ref;
            this.isInventory = inv;
            this.isMovement = mov;
            this.isPayload = payload;
            this.capturedBlockState = capturedBlockState;
            this.capturedScreen = capturedScreen;
            this.packetContext = packetContext == null ? AutismPacketContextTracker.EMPTY_CAPTURE : packetContext;
        }

        public String searchKey() {
            String key = searchKey;
            if (key == null) {
                String payloadSearch = payloadSnapshot == null ? "" : (" " + payloadSnapshot.channel() + " " + payloadSnapshot.rawDump());
                key = (shortName == null ? "" : shortName).concat(payloadSearch).toLowerCase(Locale.ROOT);
                searchKey = key;
            }
            return key;
        }
    }

    private record LogCaptureContext(String blockStateSummary, String screenSummary) {
        private static final LogCaptureContext EMPTY = new LogCaptureContext(null, null);
    }
}
