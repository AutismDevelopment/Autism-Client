package autismclient.gui.macro.editor;

import autismclient.gui.macro.editor.FieldType;
import autismclient.gui.macro.editor.components.MacroCaptureButton;
import autismclient.gui.macro.editor.components.MacroCaptureSession;
import autismclient.gui.macro.editor.components.MacroTypedListControl;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.direct.DirectScrollViewport;
import autismclient.util.macro.CraftAction;
import autismclient.util.AutismCraftingHelper;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.ToastStack;
import autismclient.util.IAutismOverlay;
import autismclient.util.AutismChatField;
import autismclient.util.AutismClipboardHelper;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismColors;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismOverlayBase;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismPayloadJsonSupport;
import autismclient.util.AutismPayloadEditorModel;
import autismclient.util.AutismPayloadSupport;
import autismclient.util.AutismPayloadTemplate;
import autismclient.util.AutismRegistryLabels;
import autismclient.util.AutismPacketSelectorOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismUiScale;
import autismclient.util.AutismWindowLayout;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;
import autismclient.util.macro.PayloadAction;
import autismclient.util.macro.SendPacketAction;
import autismclient.util.macro.WaitForPacketAction;
import autismclient.util.macro.WaitForSlotChangeAction;
import autismclient.util.macro.WaitPacketMatchAction;
import autismclient.util.macro.WaitsForGui;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class ActionEditorOverlay extends AutismOverlayBase {

    private static final Minecraft MC = Minecraft.getInstance();

    private static final int DEFAULT_W          = 280;
    private static final int COMPACT_W          = 184;
    private static final int MIN_W              = 168;
    private static final int MIN_H              = 96;
    private static final int COMPACT_MIN_H      = 88;
    private static final int PAD                = 4;
    private static final int ROW_H              = 15;
    private static final int ROW_GAP            = 2;
    private static final int FOOTER_H           = 32;
    private static final int LABEL_MIN_W        = 44;
    private static final int LABEL_MAX_W        = 68;
    private static final int FIELD_GAP          = 3;
    private static final int CATALOG_LIST_H     = 60;
    private static final int CATALOG_ITEM_H     = 13;
    private static final int CAPTURE_BTN_W      = MacroCaptureButton.WIDTH;
    private static final int SEL_LIST_MAX_VIS   = 4;
    private static final int SEL_ITEM_H         = 15;
    private static final int CONTAINER_LIST_VISIBLE_ROWS = 2;
    private static final int CRAFT_LIST_ROWS    = 4;
    private static final int CRAFT_LIST_H       = CATALOG_ITEM_H * CRAFT_LIST_ROWS;
    private static final int SCROLLBAR_W        = 5;
    private static final int WAIT_CHAT_ROW_H    = 34;
    private static final int WAIT_CHAT_VISIBLE_ROWS = 4;
    private static final int CAPTURE_CHAT_VISIBLE_ROWS = 3;
    private static final int CAPTURE_CHAT_HISTORY_LIMIT = 6;
    private static final int CAPTURE_SCOREBOARD_VISIBLE_ROWS = 5;

    private static final java.util.regex.Pattern VARIABLE_NAME =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,63}");
    private static final int CAPTURE_SCOREBOARD_ROW_H = 22;
    private static final int CAPTURE_TABLIST_VISIBLE_ROWS = 5;
    private static final int CAPTURE_TABLIST_ROW_H = 14;
    private static final long CAPTURE_TABLIST_SCAN_TTL_MS = 500L;
    private static final int WAIT_CHAT_PATTERN_H = 40;
    private static final double WAIT_ENTITY_NEARBY_LIST_RADIUS = 16.0;
    private static final int EDITOR_HINT_ROW_H = 11;
    private static final int EDITOR_GHOST_MAX_CHARS = 16;
    private static final int PAYLOAD_CONTENT_H = 74;
    private static final int PAYLOAD_JSON_H = PAYLOAD_CONTENT_H;
    private static final int PAYLOAD_TEXT_H = 30;
    private static final int PAYLOAD_RAW_H = 42;
    private static final int PAYLOAD_JAVA_H = 48;
    private static final int PAYLOAD_FIELD_ROW_H = 18;
    private static final int PAYLOAD_SCRIPT_H = 126;
    private static final int PAYLOAD_VIEW_H = 138;
    private static final long PAYLOAD_EDIT_DEBOUNCE_NANOS = 125_000_000L;
    private static final Gson PAYLOAD_TEXT_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int CAPTURE_TOAST_SUCCESS = 0xFF66E08A;
    private static final int CAPTURE_TOAST_ERROR = 0xFFFF6B6B;
    private static final int MACRO_SELECT_VISIBLE_ROWS = 5;

    private static List<String> ALL_BLOCK_IDS;
    private static List<String> ALL_SOUND_IDS;
    private static List<String> ALL_ENTITY_IDS;
    private static List<String> ALL_ITEM_IDS;

    private final Font textRenderer;
    private final CompactTheme  theme = new CompactTheme();
    private final AutismPacketSelectorOverlay packetSelectorOverlay;
    private final RaceStepSelectorOverlay raceStepSelectorOverlay;

    private int     panelX = 320, panelY = 60;
    private int     panelW = DEFAULT_W, panelH = MIN_H;
    private boolean visible    = false;
    private boolean dragging   = false;
    private double  dragOffX, dragOffY;
    private boolean restoreVisibleAfterCapture = false;
    private boolean worldCaptureAllowed = true;
    private boolean configurationOnly = false;
    private Screen  screenBeforeGBreak;
    private Screen  screenBeforeCapture;
    private boolean autoOpenedInventoryForCapture = false;
    private boolean entitySpecificCaptureMode = false;
    private boolean suppressCaptureValueAutoPattern = false;

    private String lastGeneratedCapturePattern = "";

    private String lastCaptureSource = "";
    private boolean captureWaitTouched;

    private MacroAction      targetAction;
    private int              multiEditCount = 1;
    private CompoundTag      workingTag;
    private ActionFieldSchema schema;
    private Runnable          onSaveCallback;

    private final Map<String, AutismChatField> textFields   = new LinkedHashMap<>();

    private final Map<String, Boolean>           toggleStates = new LinkedHashMap<>();

    private final Map<String, Integer>           enumIndices  = new LinkedHashMap<>();

    private final Map<String, List<String>>      stringLists  = new LinkedHashMap<>();

    private final Map<String, ItemTarget>        editorItemFields = new HashMap<>();
    private final Map<String, List<ItemTarget>>  editorItemLists  = new HashMap<>();

    private final Map<String, AutismChatField> addFields    = new LinkedHashMap<>();

    private final Map<String, DirectScrollViewport> catalogScrollViewports = new HashMap<>();

    private final Map<String, Integer>           stringListEditIndex = new HashMap<>();

    private final Map<String, String>            stringListEditPendingText = new HashMap<>();

    private final Map<String, List<Integer>>     stringListFilteredIndices = new HashMap<>();

    private final Map<String, List<String>>      catalogFilteredValues = new HashMap<>();

    private final Map<String, DirectScrollViewport> selectedScrollViewports = new HashMap<>();

    private final List<autismclient.util.macro.MacroExecutor.RecentChatMessage> waitChatFilteredHistory = new ArrayList<>();

    private final List<String> cachedMacroNames = new ArrayList<>();
    private final List<String> macroNamesWithCurrent = new ArrayList<>();
    private long cachedMacroNamesRevision = -1L;

    private int scrollOffset = 0;

    private autismclient.util.macro.ItemAction itemAction;

    private final MacroCaptureSession captureSession = new MacroCaptureSession();
    private String packetClickCapturePendingKey;

    private final java.util.List<CompactDropdown> enumDropdowns = new ArrayList<>();
    private final java.util.Map<String, CompactDropdown> enumDropdownCache = new HashMap<>();
    private final List<String> variableTokens = new ArrayList<>();
    private AutismMacro variableMacro;
    private int variableActionIndex = -1;
    private static final List<String> RACE_CONDITION_CHOICES = List.of(
            "Timing / DELAY",
            "GUI / WAIT_GUI",
            "Chat / WAIT_CHAT",
            "Packets / WAIT_PACKET",
            "Packets / WAIT_PACKET_MATCH",
            "Inventory / WAIT_SLOT_CHANGE",
            "Inventory / WAIT_INVENTORY_PREDICATE",
            "Inventory / WAIT_DURABILITY",
            "Inventory / WAIT_FREE_SLOTS",
            "Player / WAIT_HEALTH",
            "Player / WAIT_GAMEMODE_CHANGE",
            "World / WAIT_BLOCK",
            "World / WAIT_ENTITY",
            "World / WAIT_MOVEMENT",
            "World / WAIT_SOUND",
            "State / WAIT_COOLDOWN",
            "Sync / WAIT_LAN_STEP",
            "Sync / WAIT_MACRO_STEP",
            "Sync / TICK_SYNC",
            "Sync / REVISION_SYNC",
            "Sync / SERVER_TICK_SYNC"
    );
    private static final List<String> RACE_ACTION_CHOICES = List.of(
            "Inventory / PACKET_CLICK",
            "Inventory / ITEM",
            "Inventory / CLICK",
            "Inventory / CLOSE_GUI",
            "Inventory / INVENTORY",
            "Inventory / SELECT_SLOT",
            "Inventory / SWAP_SLOTS",
            "Inventory / DROP",
            "Inventory / XCARRY",
            "Inventory / CONTAINER_CLICK_SEQUENCE",
            "Packets / SEND_PACKET",
            "Packets / PACKET_GATE",
            "Packets / PACKET_BURST",
            "Packets / PAYLOAD",
            "Chat / SEND_CHAT",
            "Chat / SEND_COMMAND_PACKET",
            "Items / USE_ITEM",
            "Items / USE_ITEM_PHASE",
            "Items / NBT_BOOK",
            "World / MINE",
            "World / INSTA_BREAK",
            "World / BREAK",
            "Movement / VCLIP",
            "Movement / HCLIP",
            "Movement / TP",
            "Movement / SNEAK",
            "Movement / JUMP",
            "Movement / SPRINT",
            "Macro / MACRO_VARIABLES",
            "Macro / CAPTURE_VALUE",
            "Macro / FAKE_GAMEMODE",
            "Macro / ASSERT",
            "Macro / TOGGLE_MODULE",
            "Macro / START_MACRO",
            "Macro / STOP_MACRO",
            "Misc / PAY",
            "Misc / SIGN_EDIT",
            "Misc / RESTORE_GUI",
            "Misc / SAVE_GUI",
            "Misc / DESYNC"
    );

    private List<IAutismOverlay> captureHiddenOverlays;
    private final ToastStack captureToasts = new ToastStack(700L, 140.0f, 180.0f, 4, 4, 18);

    private List<CraftAction.CraftEntry>                     craftEntries;
    private List<AutismCraftingHelper.CraftableRecipeOption> craftAllRecipes;
    private List<AutismCraftingHelper.CraftableRecipeOption> craftFilteredRecipes;
    private AutismCraftingHelper.CraftableRecipeOption     craftSelectedRecipe;
    private int      craftRecipeScrollOffset;
    private boolean  craftUseMax;
    private String   craftLastQuery;
    private int[]    craftRecipeListBounds;

    private autismclient.util.macro.DropAction dropAction;

    private PayloadAction payloadAction;
    private AutismPayloadEditorModel payloadEditorModel;
    private boolean standalonePayloadEditor = false;
    private PayloadContentMode payloadContentMode = PayloadContentMode.BINARY_REPLAY;
    private int payloadFieldCount = 0;
    private int payloadTabIndex = 0;
    private boolean payloadContentEdited = false;
    private boolean payloadRawEdited = false;
    private boolean payloadChannelEdited = false;
    private boolean payloadJsonEdited = false;
    private boolean payloadModeManuallyChanged = false;
    private boolean payloadAddTypeManuallyChanged = false;
    private boolean suppressPayloadEditorChange = false;
    private PendingPayloadEdit pendingPayloadEdit;
    private long pendingPayloadEditAtNanos;
    private long payloadPreviewSyncedRevision = Long.MIN_VALUE;
    private PayloadBuildCache payloadBuildCache;
    private int itemEditIndex = -1;
    private int dropEditIndex = -1;
    private int wscEditIndex  = -1;

    private List<WaitForSlotChangeAction.WaitEntry> wscEntries;

    private WaitForSlotChangeAction.WaitMode wscAddMode  = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
    private int                              wscAddCount = 1;
    private boolean suppressItemEntryLiveUpdate = false;
    private boolean suppressDropEntryLiveUpdate = false;
    private boolean suppressWscLiveUpdate       = false;
    private boolean suppressDropCountEditorUpdate = false;
    private boolean waitChatFuzzySliderDragging = false;
    private int waitChatFuzzySliderX = -1;
    private int waitChatFuzzySliderY = -1;
    private int waitChatFuzzySliderW = 0;
    private int waitChatFuzzySliderH = 0;
    private boolean rotateSmoothnessSliderDragging = false;
    private int rotateSmoothnessSliderX = -1;
    private int rotateSmoothnessSliderY = -1;
    private int rotateSmoothnessSliderW = 0;
    private int rotateSmoothnessSliderH = 0;
    private boolean xcarrySafeDelaySliderDragging = false;
    private int xcarrySafeDelaySliderX = -1;
    private int xcarrySafeDelaySliderY = -1;
    private int xcarrySafeDelaySliderW = 0;
    private int xcarrySafeDelaySliderH = 0;
    private boolean suppressWaitChatPatternSync = false;

    private enum PayloadContentMode {
        UTF8_TEXT,
        BRAND_STRING,
        COMMAND_INT,
        BINARY_REPLAY
    }

    private enum PayloadEditKind {
        SCRIPT, HEX, UTF8, LOGICAL, PREVIEW_ONLY
    }

    private record PendingPayloadEdit(PayloadEditKind kind, String text) {}

    private record PayloadBuildCache(long revision, AutismPayloadTemplate.Template template,
                                     AutismPayloadTemplate.BuildResult result) {}

    private static final List<AutismPayloadTemplate.FieldType> PAYLOAD_FIELD_TYPES = List.of(
        AutismPayloadTemplate.FieldType.BYTE,
        AutismPayloadTemplate.FieldType.UNSIGNED_BYTE,
        AutismPayloadTemplate.FieldType.BOOLEAN,
        AutismPayloadTemplate.FieldType.SHORT,
        AutismPayloadTemplate.FieldType.UNSIGNED_SHORT,
        AutismPayloadTemplate.FieldType.CHAR,
        AutismPayloadTemplate.FieldType.INT,
        AutismPayloadTemplate.FieldType.LONG,
        AutismPayloadTemplate.FieldType.FLOAT,
        AutismPayloadTemplate.FieldType.DOUBLE,
        AutismPayloadTemplate.FieldType.JAVA_WRITE_UTF,
        AutismPayloadTemplate.FieldType.RAW_BYTES,
        AutismPayloadTemplate.FieldType.BYTE_ARRAY,
        AutismPayloadTemplate.FieldType.STRING_BYTES,
        AutismPayloadTemplate.FieldType.VAR_INT,
        AutismPayloadTemplate.FieldType.VAR_LONG,
        AutismPayloadTemplate.FieldType.MINECRAFT_STRING,
        AutismPayloadTemplate.FieldType.IDENTIFIER,
        AutismPayloadTemplate.FieldType.UUID_FIELD,
        AutismPayloadTemplate.FieldType.BLOCK_POS,
        AutismPayloadTemplate.FieldType.ENUM_VAR_INT,
        AutismPayloadTemplate.FieldType.OPTIONAL_VALUE,
        AutismPayloadTemplate.FieldType.NBT,
        AutismPayloadTemplate.FieldType.ITEM_STACK,
        AutismPayloadTemplate.FieldType.TEXT_COMPONENT
    );

    private static final List<String> PAYLOAD_FIELD_TYPE_LABELS = payloadFieldTypeLabels();
    private static final List<String> PAYLOAD_MODE_LABELS = List.of(
        "Raw UTF-8",
        "Hex",
        "DataOutput",
        "ByteBuf",
        "JSON",
        "Mixed"
    );
    private static final List<AutismPayloadTemplate.EncodingMode> PAYLOAD_MODES = List.of(
        AutismPayloadTemplate.EncodingMode.RAW_UTF8,
        AutismPayloadTemplate.EncodingMode.MANUAL_HEX,
        AutismPayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT,
        AutismPayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF,
        AutismPayloadTemplate.EncodingMode.JSON_TEXT,
        AutismPayloadTemplate.EncodingMode.ADVANCED_MIXED
    );

    private static List<String> payloadFieldTypeLabels() {
        List<String> labels = new ArrayList<>();
        for (AutismPayloadTemplate.FieldType type : PAYLOAD_FIELD_TYPES) labels.add(type.label());
        return List.copyOf(labels);
    }

    private List<autismclient.util.macro.WaitForLanStepAction.LanStepEntry> lanStepEntries;
    private List<WaitPacketMatchAction.Rule> waitPacketMatchRules;
    private int waitPacketMatchEditIndex = -1;
    private final List<String> payScannedPlayers = new ArrayList<>();
    private final Map<String, String> payPlayerPrefixes = new HashMap<>();

    private final Map<String, Component> payPrefixComponents = new HashMap<>();
    private boolean payPlayerScanPerformed = false;
    private final List<String> autismModuleNames = new ArrayList<>();
    private final List<String> meteorModuleNames = new ArrayList<>();
    private List<autismclient.util.macro.ToggleModuleAction.ModuleEntry> toggleModuleEntries;

    private Runnable onPreSave;

    private final List<HitRegion> hitRegions = new ArrayList<>();
    private UiBounds bodyInputClip;
    private float frameDelta = 0.0f;

    @FunctionalInterface
    private interface HitRegionAction {
        boolean fire(double mx, double my, int mouseButton);
    }

    private static final class HitRegion {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final HitRegionAction action;

        private HitRegion(int x, int y, int w, int h, Runnable action) {
            this(x, y, w, h, (mx, my, mouseButton) -> {
                if (mouseButton != 0) return false;
                action.run();
                return true;
            });
        }

        private HitRegion(CompactOverlayButton button, Runnable action) {
            this(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                    (mx, my, mouseButton) -> CompactOverlayButton.fireIfHit(button, mx, my, mouseButton));
        }

        private HitRegion(int x, int y, int w, int h, HitRegionAction action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean fire(double mx, double my, int mouseButton) {
            return action != null && action.fire(mx, my, mouseButton);
        }
    }

    private record CaptureListAddResult(boolean added, String message, int accentColor) {
    }

    private record ScrollDragRegion(int x, int y, int w, int h, java.util.function.IntConsumer handler) {
        boolean contains(int mx, int my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }
    private final List<ScrollDragRegion> scrollDragRegions = new ArrayList<>();

    private java.util.function.IntConsumer activeScrollDragHandler = null;

    private static ActionEditorOverlay sharedInstance;

    public static ActionEditorOverlay getSharedOverlay() {
        if (sharedInstance == null) {
            sharedInstance = new ActionEditorOverlay(MC.font);
            AutismOverlayManager.get().register(sharedInstance);
        }
        return sharedInstance;
    }

    private static boolean isCompactEditorType(MacroActionType type) {
        if (type == null) return false;
        return switch (type) {
            case INVENTORY, RESTORE_GUI, SAVE_GUI, SEND_TOGGLE, DESYNC -> true;
            default -> false;
        };
    }

    private int preferredPanelWidthFor(MacroAction action) {
        return isCompactEditorType(action == null ? null : action.getType()) ? COMPACT_W : DEFAULT_W;
    }

    private int currentMinPanelHeight() {
        return isCompactEditorType(targetAction == null ? null : targetAction.getType()) ? COMPACT_MIN_H : MIN_H;
    }

    public static boolean supportsActionEditor(MacroAction action) {
        if (action == null) return false;

        if (action instanceof autismclient.util.macro.MissingAddonAction) return false;
        MacroActionType type = action.getType();
        if (type == null) {

            return !ActionFieldRegistry.get(action).fields().isEmpty();
        }
        return switch (type) {
            case CRAFT, DROP, ITEM, PAYLOAD, WAIT_LAN_STEP, WAIT_PACKET, WAIT_PACKET_MATCH, WAIT_SLOT_CHANGE -> true;
            default -> !ActionFieldRegistry.get(type).fields().isEmpty();
        };
    }

    public static ActionEditorOverlay getSharedOverlayIfExists() {
        return sharedInstance;
    }

    public boolean isEditingAction(MacroAction action) {
        return visible && !standalonePayloadEditor && targetAction == action;
    }

    public void setWorldCaptureAllowed(boolean allowed) {
        this.worldCaptureAllowed = allowed;
        if (!allowed) cancelCaptureIfActive();
    }

    public void setConfigurationOnly(boolean configurationOnly) {
        this.configurationOnly = configurationOnly;
        if (configurationOnly) setWorldCaptureAllowed(false);
    }

    private boolean guardWorldCaptureAction() {
        if (worldCaptureUsable()) return true;
        AutismNotifications.warning("Pick/Capture only works in-game.");
        return false;
    }

    private boolean worldCaptureUsable() {
        if (worldCaptureAllowed) return true;
        boolean inWorld = MC != null && MC.player != null && MC.level != null;
        if (!inWorld) return false;
        configurationOnly = false;
        worldCaptureAllowed = true;
        return true;
    }

    private boolean captureControlAvailable(CaptureMode mode) {

        if (mode == CaptureMode.CUSTOM_MENU_BUTTON) return true;
        return mode == CaptureMode.PACKET_NAME || worldCaptureUsable();
    }

    public void closeIfEditingAny(java.util.List<MacroAction> actions) {
        if (!visible || standalonePayloadEditor || targetAction == null || actions == null) return;
        if (!actions.contains(targetAction)) closeEditor(false);
    }

    public void closeIfEditingAction(MacroAction action) {
        if (isEditingAction(action)) closeEditor(false);
    }

    public ActionEditorOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.packetSelectorOverlay = new AutismPacketSelectorOverlay(textRenderer);
        this.raceStepSelectorOverlay = new RaceStepSelectorOverlay(textRenderer);
    }

    public void setMacroContext(AutismMacro macro, int actionIndex) {
        variableMacro = macro;
        variableActionIndex = actionIndex;
        rebuildVariableTokens();
    }

    public void open(MacroAction action, Runnable onPreSave, Runnable onSave) {
        open(action, onPreSave, onSave, 1);
    }

    public void open(MacroAction action, Runnable onPreSave, Runnable onSave, int editCount) {
        this.standalonePayloadEditor = false;
        this.targetAction   = action;
        this.multiEditCount = Math.max(1, editCount);
        this.onPreSave      = onPreSave;
        this.onSaveCallback = onSave;
        this.workingTag     = AutismMacro.serializeAction(action);
        this.schema         = ActionFieldRegistry.get(action);
        this.scrollOffset   = 0;
        rebuildVariableTokens();

        textFields.clear();
        toggleStates.clear();
        enumIndices.clear();
        stringLists.clear();
        editorItemFields.clear();
        editorItemLists.clear();
        addFields.clear();
        catalogScrollViewports.clear();
        stringListEditIndex.clear();
        stringListEditPendingText.clear();
        stringListFilteredIndices.clear();
        catalogFilteredValues.clear();
        selectedScrollViewports.clear();
        packetSelectorOverlay.close();
        prepareWorkingTagForEditor(action);

        for (FieldDef field : schema.fields()) {
            String key = field.key();
            switch (field.type()) {

                case TOGGLE ->
                    toggleStates.put(key, workingTag.contains(key)
                            ? workingTag.getBooleanOr(key, false) : false);

                case NUMBER -> {
                    int v = workingTag.contains(key) ? workingTag.getIntOr(key, 0) : 0;
                    AutismChatField f = makeField(80);
                    if (!field.dynamic()) f.setNumericOnly(true);
                    String binding = autismclient.util.macro.MacroDynamicBindings.get(action, key);
                    f.setText(binding.isBlank() ? String.valueOf(v) : binding);
                    textFields.put(key, f);
                }

                case DECIMAL -> {
                    double v = workingTag.contains(key) ? workingTag.getDoubleOr(key, 0.0) : 0.0;
                    AutismChatField f = makeField(80);
                    if (!field.dynamic()) f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*\\.?\\d*"));
                    String binding = autismclient.util.macro.MacroDynamicBindings.get(action, key);
                    f.setText(binding.isBlank() ? fmtDouble(v) : binding);
                    textFields.put(key, f);
                }

                case TEXT, MACRO_SELECT -> {
                    String v = workingTag.contains(key) ? workingTag.getStringOr(key, "") : "";
                    AutismChatField f = makeField(80);
                    f.setText(v);

                    if ("saveAs".equals(key) && (v.isEmpty() || VARIABLE_NAME.matcher(v).matches())) {
                        f.setFilter(s -> s.isEmpty() || VARIABLE_NAME.matcher(s).matches());
                    }
                    textFields.put(key, f);
                }

                case ENUM -> {
                    String v = workingTag.contains(key) ? workingTag.getStringOr(key, "") : "";
                    List<String> opts = field.enumOptions();
                    int idx = opts.indexOf(v);
                    if (idx < 0) idx = opts.indexOf(v.toUpperCase());
                    enumIndices.put(key, Math.max(0, idx));
                    if (field.dynamic()) {
                        AutismChatField bindingField = makeField(80);
                        bindingField.setText(autismclient.util.macro.MacroDynamicBindings.get(action, key));
                        textFields.put(key, bindingField);
                    }
                }

                case SLOT -> {
                    int v = workingTag.contains(key) ? workingTag.getIntOr(key, 0) : 0;
                    AutismChatField f = makeField(50);
                    if (!field.dynamic()) f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*"));
                    String binding = autismclient.util.macro.MacroDynamicBindings.get(action, key);
                    f.setText(binding.isBlank() ? String.valueOf(v) : binding);
                    textFields.put(key, f);
                }

                case BLOCK_POS -> {
                    String[] xyzKeys = field.xyzKeys();
                    boolean dbl      = field.xyzDouble();
                    for (int i = 0; i < 3; i++) {
                        AutismChatField f = makeField(50);
                        if (dbl) {
                            if (!field.dynamic()) f.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d*\\.?\\d*"));
                            double v = workingTag.contains(xyzKeys[i]) ? workingTag.getDoubleOr(xyzKeys[i], 0.0) : 0.0;
                            String binding = autismclient.util.macro.MacroDynamicBindings.get(action, xyzKeys[i]);
                            f.setText(binding.isBlank() ? fmtDouble(v) : binding);
                        } else {
                            if (!field.dynamic()) f.setNumericOnly(true);
                            int v = workingTag.contains(xyzKeys[i]) ? workingTag.getIntOr(xyzKeys[i], 0) : 0;
                            String binding = autismclient.util.macro.MacroDynamicBindings.get(action, xyzKeys[i]);
                            f.setText(binding.isBlank() ? String.valueOf(v) : binding);
                        }
                        textFields.put(key + "_" + i, f);
                    }
                }

                case STRING_LIST -> {
                    List<String> list = new ArrayList<>();
                    if (workingTag.contains(key)) {
                        ListTag nl = workingTag.getList(key).orElse(new ListTag());
                        for (Tag el : nl) {
                            String s = el.asString().orElse("");
                            if (!s.isEmpty()) list.add(s);
                        }
                    }
                    stringLists.put(key, list);

                    AutismChatField af = makeField(80);
                    if (field.captureMode() == CaptureMode.BLOCK_CATALOG) {
                        af.setPlaceholder(Component.literal("Search blocks..."));
                    } else {
                        af.setPlaceholder(Component.literal("Search / " + field.addLabel()));
                        af.setSubmitHandler(text -> {
                            if (!text.isBlank()) {
                                List<String> entries = stringLists.get(key);
                                if (entries != null && addStringListEntry(field, entries, text.strip())) {
                                    af.setText("");
                                }
                            }
                            return true;
                        });
                    }
                    addFields.put(key, af);
                }
            }
        }

        if (action instanceof autismclient.util.macro.UseItemAction useItemAction) {
            AutismChatField slotField = textFields.get("slot");
            if (slotField != null && useItemAction.slot < 0 && !workingTag.contains("slot")) {
                slotField.setText("");
            }
        }

        itemAction              = null;
        captureSession.clearItemSlotCapture();
        itemEditIndex = -1;
        dropEditIndex = -1;
        if (action instanceof autismclient.util.macro.ItemAction ia) {
            itemAction = new autismclient.util.macro.ItemAction();
            itemAction.itemNames     = new ArrayList<>(ia.itemNames);
            itemAction.itemTargets   = copyEditorTargets(ia.itemTargets, ia.itemNames);
            itemAction.itemTimes     = new ArrayList<>(ia.itemTimes);
            itemAction.itemActionIdx = new ArrayList<>(ia.itemActionIdx);
            itemAction.itemButtons   = new ArrayList<>(ia.itemButtons);
            itemAction.itemSearchScopes = new ArrayList<>(ia.itemSearchScopes);
            itemAction.stackAmountModes = new ArrayList<>(ia.stackAmountModes);
            while (itemAction.itemTimes.size()     < itemAction.itemNames.size()) itemAction.itemTimes.add(1);
            while (itemAction.itemActionIdx.size() < itemAction.itemNames.size()) itemAction.itemActionIdx.add(0);
            while (itemAction.itemButtons.size()   < itemAction.itemNames.size()) itemAction.itemButtons.add(0);
            while (itemAction.itemSearchScopes.size() < itemAction.itemNames.size()) {
                int index = itemAction.itemSearchScopes.size();
                itemAction.itemSearchScopes.add(ia.getItemSearchScope(index).ordinal());
            }
            while (itemAction.stackAmountModes.size() < itemAction.itemNames.size()) itemAction.stackAmountModes.add(0);
            itemAction.targetSlot  = ia.targetSlot;
            itemAction.useSlot     = ia.useSlot;
            itemAction.actionIndex = ia.actionIndex;
            itemAction.button      = ia.button;
            itemAction.times       = ia.times;
            itemAction.waitForGuiBefore = ia.waitForGuiBefore;
            itemAction.waitForGuiAfter  = ia.waitForGuiAfter;
            itemAction.guiName     = ia.guiName != null ? ia.guiName : "";
            itemAction.waitForItem = ia.waitForItem;
            itemAction.useCursorItemForPickupAll = ia.useCursorItemForPickupAll;

            for (int i = 0; i < itemAction.itemNames.size(); i++) {
                AutismChatField f = makeField(28);
                f.setNumericOnly(true);
                f.setText(String.valueOf(itemAction.getItemTime(i)));
                textFields.put("item_times_" + i, f);
            }

            AutismChatField addF = makeField(120);
            addF.setPlaceholder(Component.literal("Item name"));
            addF.setChangedListener(text -> handleItemEntryEditorChanged());
            addFields.put("_item_add", addF);
            AutismChatField addSlotF = makeField(52);
            addSlotF.setNumericOnly(true);
            addSlotF.setPlaceholder(Component.literal("Slot"));
            addSlotF.setChangedListener(text -> handleItemEntryEditorChanged());
            textFields.put("item_entrySlot", addSlotF);

            toggleStates.put("item_waitForGuiBefore", ia.waitForGuiBefore);
            toggleStates.put("item_waitForGuiAfter",  ia.waitForGuiAfter);
            toggleStates.put("item_waitForItem", ia.waitForItem);
            toggleStates.put("item_useCursorItemForPickupAll", ia.useCursorItemForPickupAll);
            toggleStates.put("item_useClickDelay", ia.useClickDelay);
            AutismChatField guiF = makeField(80);
            guiF.setText(ia.guiName != null ? ia.guiName : "");
            textFields.put("item_guiName", guiF);
            AutismChatField delayF = makeField(60);
            delayF.setNumericOnly(true);
            delayF.setText(String.valueOf(Math.max(0, ia.clickDelayMs)));
            textFields.put("item_clickDelayMs", delayF);

        }

        wscEditIndex = -1;
        wscEntries   = new ArrayList<>();
        wscAddMode   = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
        wscAddCount  = 1;
        if (action instanceof WaitForSlotChangeAction wsc) {
            for (WaitForSlotChangeAction.WaitEntry e : wsc.entries) wscEntries.add(e.copy());
        }
        if (action.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
            toggleStates.put(MacroAction.LISTEN_DURING_PREVIOUS_KEY,
                    action.listensDuringPreviousAction());
            AutismChatField wscAddF = makeField(120);
            wscAddF.setPlaceholder(Component.literal("Item name (optional)"));
            wscAddF.setChangedListener(text -> handleWscEntryEditorChanged());
            addFields.put("_wsc_add", wscAddF);
            AutismChatField wscSlotF = makeField(52);
            wscSlotF.setNumericOnly(true);
            wscSlotF.setPlaceholder(Component.literal("Slot #"));
            wscSlotF.setChangedListener(text -> handleWscEntryEditorChanged());
            textFields.put("wsc_slot", wscSlotF);
            AutismChatField wscCountF = makeField(36);
            wscCountF.setNumericOnly(true);
            wscCountF.setPlaceholder(Component.literal("Count"));
            wscCountF.setChangedListener(text -> handleWscCountChanged());
            textFields.put("wsc_count", wscCountF);
        }

        craftEntries            = null;
        craftAllRecipes         = null;
        craftFilteredRecipes    = null;
        craftSelectedRecipe     = null;
        craftRecipeScrollOffset = 0;
        craftUseMax             = false;
        craftLastQuery          = null;
        craftRecipeListBounds   = null;
        if (action instanceof CraftAction craftAction) {
            craftEntries = craftAction.copyEntries();
            for (int i = 0; i < craftEntries.size(); i++) {
                CraftAction.CraftEntry entry = craftEntries.get(i);
                AutismChatField f = makeField(44);
                f.setNumericOnly(true);
                f.setText(String.valueOf(entry.amount));
                textFields.put("craft_amount_" + i, f);
                toggleStates.put("craft_useMax_" + i, entry.useMaxAmount);
            }

            AutismChatField amtF = makeField(44);
            amtF.setNumericOnly(true);
            amtF.setText("1");
            textFields.put("_craft_amount", amtF);

            AutismChatField srchF = makeField(180);
            srchF.setPlaceholder(Component.literal("Search recipes..."));
            addFields.put("_craft_search", srchF);

            craftAllRecipes      = AutismCraftingHelper.getCraftableRecipes(MC);
            craftFilteredRecipes = AutismCraftingHelper.filterRecipes(craftAllRecipes, "");
        }

        if (action instanceof WaitForPacketAction waitForPacketAction) {
            stringLists.put("packetNames", sanitizeWaitPacketTargets(waitForPacketAction.effectiveList()));
            toggleStates.put(MacroAction.LISTEN_DURING_PREVIOUS_KEY, waitForPacketAction.listenDuringPreviousAction);
        }

        waitPacketMatchRules = null;
        waitPacketMatchEditIndex = -1;
        if (action instanceof WaitPacketMatchAction packetMatchAction) {
            waitPacketMatchRules = new ArrayList<>();
            for (WaitPacketMatchAction.Rule rule : packetMatchAction.effectiveRules()) {
                waitPacketMatchRules.add(rule.copy());
            }
            if (waitPacketMatchRules.isEmpty()) waitPacketMatchRules.add(new WaitPacketMatchAction.Rule());
            waitPacketMatchEditIndex = 0;
            toggleStates.put(MacroAction.LISTEN_DURING_PREVIOUS_KEY, packetMatchAction.listenDuringPreviousAction);
            AutismChatField valueField = makeField(120);
            valueField.setPlaceholder(Component.literal("Value"));
            textFields.put("_wpm_value", valueField);
            syncWaitPacketMatchValueField();
        }

        dropAction = null;
        if (action instanceof autismclient.util.macro.DropAction da) {
            dropAction = new autismclient.util.macro.DropAction();
            dropAction.mode            = da.mode;
            dropAction.dropCount       = da.dropCount;
            dropAction.itemNames       = new ArrayList<>(da.itemNames);
            dropAction.itemTargets     = copyEditorTargets(da.itemTargets, da.itemNames);
            dropAction.itemCounts      = new ArrayList<>(da.itemCounts);
            dropAction.waitForGuiBefore = da.waitForGuiBefore;
            dropAction.waitForGuiAfter  = da.waitForGuiAfter;
            dropAction.guiName         = da.guiName != null ? da.guiName : "";
            dropAction.useHandlerSlots = da.useHandlerSlots;
            while (dropAction.itemCounts.size() < dropAction.itemNames.size()) {
                dropAction.itemCounts.add(dropAction.getItemCount(dropAction.itemCounts.size()));
            }

            for (int i = 0; i < dropAction.itemNames.size(); i++) {
                AutismChatField f = makeField(32);
                f.setNumericOnly(true);
                f.setText(String.valueOf(dropAction.itemCounts.get(i)));
                textFields.put("drop_count_" + i, f);
            }

            AutismChatField cntF = makeField(60);
            cntF.setNumericOnly(true);
            cntF.setText(String.valueOf(da.dropCount));
            cntF.setChangedListener(text -> handleDropCountEditorChanged());
            textFields.put("drop_globalCount", cntF);
            AutismChatField addDropF = makeField(120);
            addDropF.setPlaceholder(Component.literal("Item name..."));
            addDropF.setChangedListener(text -> handleDropEntryEditorChanged());
            addFields.put("_drop_add", addDropF);
            AutismChatField addDropSlotF = makeField(52);
            addDropSlotF.setNumericOnly(true);
            addDropSlotF.setPlaceholder(Component.literal("Slot"));
            addDropSlotF.setChangedListener(text -> handleDropEntryEditorChanged());
            textFields.put("drop_entrySlot", addDropSlotF);
            AutismChatField dropGuiF = makeField(80);
            dropGuiF.setText(da.guiName != null ? da.guiName : "");
            textFields.put("drop_guiName", dropGuiF);

            toggleStates.put("drop_waitForGuiBefore", da.waitForGuiBefore);
            toggleStates.put("drop_waitForGuiAfter",  da.waitForGuiAfter);
            toggleStates.put("drop_useHandlerSlots", da.useHandlerSlots);
            autismclient.util.macro.DropAction.DropMode[] modes =
                    autismclient.util.macro.DropAction.DropMode.values();
            int mi = 0;
            for (int i = 0; i < modes.length; i++) if (modes[i] == da.mode) { mi = i; break; }
            enumIndices.put("drop_mode", mi);
            syncDropCountEditorField();
        }

        payloadAction = null;
        payloadEditorModel = null;
        if (action instanceof PayloadAction pa) {
            payloadAction = new PayloadAction();
            payloadAction.fromTag(pa.toTag());
            initializePayloadEditorFields(payloadAction);
        }

        lanStepEntries = null;
        if (action instanceof autismclient.util.macro.WaitForLanStepAction wls) {
            lanStepEntries = new ArrayList<>();
            for (autismclient.util.macro.WaitForLanStepAction.LanStepEntry e : wls.entries)
                lanStepEntries.add(new autismclient.util.macro.WaitForLanStepAction.LanStepEntry(e.username, e.step));

            for (int i = 0; i < lanStepEntries.size(); i++) {
                autismclient.util.macro.WaitForLanStepAction.LanStepEntry e = lanStepEntries.get(i);
                AutismChatField uf = makeField(80); uf.setText(e.username);
                textFields.put("lan_user_" + i, uf);
                AutismChatField sf = makeField(40); sf.setNumericOnly(true);
                sf.setText(String.valueOf(e.step));
                textFields.put("lan_step_" + i, sf);
            }
            AutismChatField newUF = makeField(80);
            newUF.setPlaceholder(Component.literal("Peer name..."));
            addFields.put("_lan_user_add", newUF);
            AutismChatField newSF = makeField(40);
            newSF.setNumericOnly(true);
            newSF.setText("1");
            addFields.put("_lan_step_add", newSF);
            AutismChatField dsF = makeField(60);
            dsF.setNumericOnly(true);
            dsF.setText(String.valueOf(wls.defaultStep));
            textFields.put("lan_defaultStep", dsF);
            toggleStates.put("lan_filterByUser", wls.filterByUser);
            toggleStates.put(MacroAction.LISTEN_DURING_PREVIOUS_KEY, wls.listenDuringPreviousAction);

        }

        entitySpecificCaptureMode = false;
        if (action instanceof autismclient.util.macro.WaitForSoundAction) {
            AutismChatField search = addFields.get("soundIds");
            if (search != null) search.setPlaceholder(Component.literal("Search sound id..."));
        }
        if (action instanceof autismclient.util.macro.WaitForEntityAction) {
            AutismChatField search = addFields.get("entityIds");
            if (search != null) search.setPlaceholder(Component.literal("Search entity type..."));
        }
        if (action instanceof autismclient.util.macro.LookAtBlockAction) {
            AutismChatField search = addFields.get("entityIds");
            if (search != null) search.setPlaceholder(Component.literal("Search entity type..."));
        }
        if (action instanceof autismclient.util.macro.StoreItemAction) {
            AutismChatField search = addFields.get("targetItems");
            if (search != null) search.setPlaceholder(Component.literal("Search item id..."));
        }
        if (action instanceof autismclient.util.macro.InventoryAuditAction) {
            AutismChatField search = addFields.get("targetItems");
            if (search != null) search.setPlaceholder(Component.literal("Item name"));
        }
        payScannedPlayers.clear();
        payPlayerPrefixes.clear();
        payPrefixComponents.clear();
        payPlayerScanPerformed = false;
        if (action instanceof autismclient.util.macro.PayAction) {
            AutismChatField search = addFields.get("players");
            if (search != null) search.setPlaceholder(Component.literal("Search or add player..."));
        }
        autismModuleNames.clear();
        meteorModuleNames.clear();
        toggleModuleEntries = null;
        if (action instanceof autismclient.util.macro.ToggleModuleAction) {
            AutismChatField search = makeField(180);
            search.setPlaceholder(Component.literal("Search module..."));
            addFields.put("_toggle_module_search", search);
            refreshMeteorModuleNames();
            autismclient.util.macro.ToggleModuleAction toggleModuleAction = (autismclient.util.macro.ToggleModuleAction) action;
            toggleModuleEntries = new ArrayList<>();
            for (autismclient.util.macro.ToggleModuleAction.ModuleEntry entry : toggleModuleAction.entries) {
                if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
                    toggleModuleEntries.add(new autismclient.util.macro.ToggleModuleAction.ModuleEntry(entry.moduleName, entry.toggleMode));
                }
            }
        }
        if (action instanceof autismclient.util.macro.WaitForChatAction) {
            AutismChatField search = makeField(180);
            search.setPlaceholder(Component.literal("Search recent chat..."));
            addFields.put("_wait_chat_search", search);
            AutismChatField captureName = makeField(100);
            captureName.setPlaceholder(Component.literal("Variable name"));
            addFields.put("_capture_name", captureName);
            waitChatFuzzySliderDragging = false;
            clearWaitChatFuzzySliderBounds();
            AutismChatField patternField = textFields.get("pattern");
            if (patternField != null) {
                patternField.setMultiline(true);
                patternField.setHeight(WAIT_CHAT_PATTERN_H);
                String existingPatternJson = workingTag.getStringOr("patternJson", "");
                Component initialPattern = existingPatternJson == null || existingPatternJson.isBlank()
                        ? Component.literal(autismclient.util.macro.MacroExecutor.normalizeManualText(workingTag.getStringOr("pattern", "")))
                        : getWaitChatPatternComponent(workingTag.getStringOr("pattern", ""));
                String visiblePattern = initialPattern.getString();
                workingTag.putString("pattern", visiblePattern);
                if (existingPatternJson == null || existingPatternJson.isBlank()) workingTag.putString("patternJson", "");
                else workingTag.putString("patternJson", autismclient.util.macro.MacroExecutor.serializeTextComponent(initialPattern));
                patternField.setDisplayTextProvider(this::getWaitChatPatternComponent);
                suppressWaitChatPatternSync = true;
                patternField.setText(visiblePattern);
                suppressWaitChatPatternSync = false;
                patternField.setChangedListener(value -> {
                    if (suppressWaitChatPatternSync) return;
                    String visibleValue = autismclient.util.macro.MacroExecutor.normalizeManualText(value);
                    workingTag.putString("pattern", visibleValue);
                    workingTag.putString("patternJson", "");
                });
            }
        } else {
            suppressWaitChatPatternSync = false;
            waitChatFuzzySliderDragging = false;
            clearWaitChatFuzzySliderBounds();
        }
        if (action instanceof autismclient.util.macro.WaitForGuiAction) {
            AutismChatField captureName = makeField(100);
            captureName.setPlaceholder(Component.literal("Variable name"));
            addFields.put("_capture_name", captureName);
        }
        if (action instanceof autismclient.util.macro.CaptureValueAction) {
            AutismChatField token = makeField(180);
            token.setEditable(false);
            token.setFocusEffectsEnabled(false);
            token.setPlaceholder(Component.literal("Set a variable name"));
            addFields.put("_capture_token", token);

            AutismChatField preview = makeField(180);
            preview.setEditable(false);
            preview.setFocusEffectsEnabled(false);
            preview.setPlaceholder(Component.literal("Nothing available now"));
            addFields.put("_capture_preview", preview);

            lastGeneratedCapturePattern = autismclient.util.macro.CaptureValueAction.buildCapturePattern(
                editorFieldText("exampleText", ""), editorFieldText("selectedText", ""),
                editorFieldText("saveAs", "value"));

            lastCaptureSource = "";
            captureWaitTouched = false;

            for (String key : List.of("saveAs", "exampleText", "selectedText")) {
                AutismChatField field = textFields.get(key);
                if (field != null) field.setChangedListener(ignored -> refreshCaptureValueAutoPattern());
            }
        } else {
            suppressCaptureValueAutoPattern = false;
            lastGeneratedCapturePattern = "";
        }

        if (action instanceof autismclient.util.macro.RotateAction) {
            rotateSmoothnessSliderDragging = false;
            clearRotateSmoothnessSliderBounds();
            autismclient.util.macro.RotateAction rotateAction = (autismclient.util.macro.RotateAction) action;
            AutismChatField smoothnessField = textFields.get("smoothness");
            int smoothness = autismclient.util.macro.RotateAction.clampSmoothness(rotateAction.smoothness);
            workingTag.putInt("smoothness", smoothness);
            if (smoothnessField != null) smoothnessField.setText(String.valueOf(smoothness));
        } else if (action instanceof autismclient.util.macro.LookAtBlockAction lookAtAction) {
            rotateSmoothnessSliderDragging = false;
            clearRotateSmoothnessSliderBounds();
            AutismChatField smoothnessField = textFields.get("smoothness");
            int smoothness = autismclient.util.macro.RotateAction.clampSmoothness(lookAtAction.smoothness);
            workingTag.putInt("smoothness", smoothness);
            if (smoothnessField != null) smoothnessField.setText(String.valueOf(smoothness));
        } else {
            rotateSmoothnessSliderDragging = false;
            clearRotateSmoothnessSliderBounds();
        }

        if (action instanceof autismclient.util.macro.XCarryAction xCarryAction) {
            xcarrySafeDelaySliderDragging = false;
            clearXCarrySafeDelaySliderBounds();
            AutismChatField delayField = textFields.get("safeClickDelayTicks");
            int safeDelay = autismclient.util.macro.XCarryAction.clampSafeClickDelayTicks(xCarryAction.safeClickDelayTicks);
            workingTag.putInt("safeClickDelayTicks", safeDelay);
            workingTag.putBoolean("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
            workingTag.putBoolean("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
            toggleStates.put("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
            toggleStates.put("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
            if (delayField != null) delayField.setText(String.valueOf(safeDelay));
        } else {
            xcarrySafeDelaySliderDragging = false;
            clearXCarrySafeDelaySliderBounds();
        }

        if (action.getType() == MacroActionType.INVENTORY
                || action.getType() == MacroActionType.CLOSE_GUI
                || action.getType() == MacroActionType.SAVE_GUI) {
            toggleStates.put("sendPacket", !workingTag.getBooleanOr("sendPacket", true));
        }
        if (action.getType() == MacroActionType.STORE_ITEM) {
            toggleStates.put("closeSendPkt", !workingTag.getBooleanOr("closeSendPkt", true));
        }

        refreshItemTextDisplayProviders();
        applyEditorPlaceholders();

        if (action.getType() == MacroActionType.PLACE) {
            AutismChatField slotF = makeField(52);
            slotF.setNumericOnly(true);
            slotF.setPlaceholder(Component.literal("Slot"));
            slotF.setChangedListener(text -> syncPlaceItemEditorFields());
            textFields.put("place_itemSlot", slotF);

            if (action instanceof autismclient.util.macro.PlaceAction placeAction) {
                toggleStates.put("place_waitForItem", placeAction.waitForItem);
                toggleStates.put("place_silentSwitch", placeAction.silentSwitch);
            }

            ItemTarget placeTarget = editorItemFields.get("itemName");
            if (placeTarget == null && action instanceof autismclient.util.macro.PlaceAction placeAction) {
                placeTarget = resolveEditorTarget(placeAction.itemTarget, placeAction.itemName);
            }
            if (placeTarget != null) {
                slotF.setText(placeTarget.hasSlot() ? String.valueOf(placeTarget.slot) : "");
            }

            AutismChatField nameF = textFields.get("itemName");
            if (nameF != null) {
                nameF.setChangedListener(text -> syncPlaceItemEditorFields());
            }

            if (action instanceof autismclient.util.macro.PlaceAction placeAction) {
                if (placeAction.blockPos != null) {
                    AutismInstaBreakRenderer.setTarget(placeAction.blockPos, 0xFFFF3B3B);
                }
            }
        }

        panelW = preferredPanelWidthFor(action);
        int contentH = computeContentH();
        int desiredH = HEADER_HEIGHT + PAD + contentH + FOOTER_H + PAD;
        int minH     = currentMinPanelHeight();
        int maxH     = Math.max(minH, AutismUiScale.getVirtualScreenHeight() * 4 / 5);
        panelH = Math.max(minH, Math.min(maxH, desiredH));

        this.visible   = true;
        AutismOverlayManager.get().bringToFront(this);
    }

    public void openStandalonePayloadEditor(PayloadAction action) {
        open(action, null, null);
        this.standalonePayloadEditor = true;
    }

    public boolean updateOpenPayloadChannel(String channel) {
        String target = channel == null ? "" : channel.trim();
        if (!visible || target.isBlank()) return false;
        if (!(standalonePayloadEditor || payloadAction != null || targetAction instanceof PayloadAction)) return false;
        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField == null) return false;

        suppressPayloadEditorChange = true;
        try {
            channelField.setText(target);
            if (payloadEditorModel != null) {
                payloadEditorModel.channel = target;
                payloadEditorModel.touch();
                payloadBuildCache = null;
            }
            if (payloadAction != null) payloadAction.channel = target;
            if (targetAction instanceof PayloadAction targetPayload) targetPayload.channel = target;
            AutismChatField contentField = textFields.get("payload_content");
            if (contentField != null) {
                contentField.setText(replacePayloadScriptChannelLine(contentField.getText(), target));
            }
            payloadChannelEdited = true;
        } finally {
            suppressPayloadEditorChange = false;
        }
        refreshInteractiveLayout();
        AutismOverlayManager.get().bringToFront(this);
        return true;
    }

    private String replacePayloadScriptChannelLine(String script, String channel) {
        String safeScript = script == null ? "" : script.replace("\r\n", "\n").replace('\r', '\n');
        String safeChannel = channel == null || channel.isBlank() ? "minecraft:brand" : channel.trim();
        String[] lines = safeScript.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i] == null ? "" : lines[i];
            String stripped = raw.stripLeading();
            if (stripped.startsWith("#")) continue;
            int split = firstPayloadDelimiter(raw);
            if (split < 0) continue;
            String key = raw.substring(0, split).strip();
            if (!"channel".equalsIgnoreCase(key)) continue;
            String indent = raw.substring(0, raw.length() - stripped.length());
            lines[i] = indent + "channel = " + safeChannel;
            return String.join("\n", lines);
        }
        if (safeScript.isBlank()) return "channel = " + safeChannel;
        return "channel = " + safeChannel + "\n" + safeScript;
    }

    @Override public String getOverlayId()  { return "autism-action-editor"; }
    @Override public int    getMinWidth()   { return MIN_W; }
    @Override public int    getMinHeight()  { return currentMinPanelHeight(); }
    @Override public boolean isVisible()    { return visible; }
    @Override public void   setVisible(boolean v) {
        visible = v;

        if (v) {
            hitRegions.clear();
            scrollDragRegions.clear();
        } else {
            flushPendingPayloadEdit(true);
            CompactDropdown.closeOpenMenu(enumDropdowns);
            packetSelectorOverlay.setVisible(false);
            raceStepSelectorOverlay.setVisible(false);
            clearTextFieldFocus();
        }
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean value) {
        if (collapsed == value) return;
        super.setCollapsed(value);
        if (value) {
            CompactDropdown.closeOpenMenu(enumDropdowns);
            packetSelectorOverlay.setVisible(false);
            raceStepSelectorOverlay.setVisible(false);
            hitRegions.clear();
            scrollDragRegions.clear();
        }
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout b) {
        AutismWindowLayout c = clampToScreen(this, b);
        panelX    = c.x;
        panelY    = c.y;
        panelW    = c.width;
        panelH    = c.height;
        visible   = c.visible;
        collapsed = c.collapsed;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible
            && (packetSelectorOverlay.isVisible()
            || raceStepSelectorOverlay.isVisible()
            || CompactDropdown.isMenuOpen(enumDropdowns)
            || mx >= panelX && mx < panelX + panelW
            && my >= panelY && my < panelY + (collapsed ? HEADER_HEIGHT : panelH));
    }

    @Override
    public boolean isOverDragBar(double mx, double my) {
        return visible
            && mx >= panelX && mx < panelX + panelW
            && my >= panelY && my < panelY + HEADER_HEIGHT
            && !isOverWindowControl(mx, my, getBounds());
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean hasTextFieldFocused() {
        if (packetSelectorOverlay.hasTextFieldFocused()) return true;
        if (raceStepSelectorOverlay.hasTextFieldFocused()) return true;
        for (AutismChatField f : textFields.values()) if (f.isFocused()) return true;
        for (AutismChatField f : addFields.values())  if (f.isFocused()) return true;
        return false;
    }

    @Override
    public void clearTextFieldFocus() {
        textFields.values().forEach(f -> f.setFocused(false));
        addFields.values().forEach(f  -> f.setFocused(false));
    }

    public boolean wantsItemSlotCapture() {
        return captureSession.hasItemSlotCapture();
    }

    public boolean shouldRenderAbstractContainerScreenCaptureBanner() {
        return captureSession.hasItemSlotCapture();
    }

    public String getAbstractContainerScreenCaptureTitle() {
        return "Capturing " + getAbstractContainerScreenCaptureTargetLabel() + " - " + getCaptureActionLabel();
    }

    public String getAbstractContainerScreenCaptureInstruction() {
        String activeCaptureKey = captureSession.itemSlotKey();
        if ("_item_entries".equals(activeCaptureKey) || "_drop_entries".equals(activeCaptureKey)) {
            return "Right-click a slot to set the item. Esc = cancel";
        }
        if ("_wsc_entries".equals(activeCaptureKey)) {
            return "Right-click slots to add items or exact slots. Esc = done";
        }
        if (stringLists.containsKey(activeCaptureKey)) {
            if (isXCarryListKey(activeCaptureKey)) {
                return "Right-click slots to add items, or empty slots for exact slots. Esc = done";
            }
            if (isStoreItemTargetListKey(activeCaptureKey)) {
                return "Right-click items anywhere to add by name. Ctrl+right-click for exact slots. Esc = done";
            }
            return "Right-click slots to add item names. Esc = done";
        }
        return "Right-click a slot in this screen. Esc = cancel";
    }

    public String getAbstractContainerScreenCaptureHoverText(net.minecraft.world.inventory.Slot slot, String itemName, String registryId) {
        if (slot == null) return "";
        int handlerSlot = autismclient.util.AutismInventoryHelper.toMenuSlotId(MC, slot);
        int visibleSlot = handlerSlot >= 0
                ? autismclient.util.AutismInventoryHelper.toUserVisibleSlot(MC, handlerSlot)
                : -1;
        String slotText = visibleSlot >= 0 ? String.valueOf(visibleSlot) : "Handler " + handlerSlot;
        String slotDetail = "Handler " + handlerSlot;
        String itemText = !registryId.isEmpty() ? registryId : (!itemName.isEmpty() ? itemName : "Empty slot");
        return slotText.equals(slotDetail)
                ? "Hover: " + slotText + " | " + itemText
                : "Hover: " + slotText + " | " + slotDetail + " | " + itemText;
    }

    public boolean cancelCaptureIfActive() {
        if (cancelPacketClickCaptureIfActive()) return true;
        return captureSession.stopItemSlotCapture(() -> exitCaptureMode(false, false));
    }

    public boolean hasActiveCaptureSession() {
        AutismSharedState state = AutismSharedState.get();
        return captureSession.hasItemSlotCapture()
            || packetClickCapturePendingKey != null
            || captureHiddenOverlays != null
            || restoreVisibleAfterCapture
            || screenBeforeCapture != null
            || screenBeforeGBreak != null
            || state.hasCaptureCancelCallback()
            || state.hasBlockCaptureCallback()
            || state.hasEntityCaptureCallback()
            || state.hasAttackCaptureCallback()
            || state.isGBreakCapturing();
    }

    public boolean onInventorySlotCapture(net.minecraft.world.inventory.Slot slot,
                                          String itemName, String registryId) {
        return onInventorySlotCapture(slot, itemName, registryId, false);
    }

    public boolean onInventorySlotCapture(net.minecraft.world.inventory.Slot slot,
                                          String itemName, String registryId, boolean slotMode) {
        int visibleSlot = slot != null
                ? autismclient.util.AutismInventoryHelper.toUserVisibleSlot(MC, slot)
                : -1;
        ItemTarget capturedTarget = captureItemTarget(slot, itemName, registryId, visibleSlot);
        if (!captureSession.hasItemSlotCapture() || (itemName.isEmpty() && visibleSlot < 0)) return false;

        boolean useSlot = slotMode && visibleSlot >= 0;
        if (useSlot) capturedTarget = ItemTarget.slotOnly(Math.max(0, visibleSlot));
        String key = captureSession.itemSlotKey();

        if ("_item_entries".equals(key) && itemAction != null) {
            applyCapturedItemEntry(capturedTarget);
            captureSession.clearItemSlotCapture();
            exitCaptureMode(false, false);
            return true;
        }

        if ("_drop_entries".equals(key) && dropAction != null) {
            applyCapturedDropEntry(capturedTarget);
            captureSession.clearItemSlotCapture();
            exitCaptureMode(false, false);
            return true;
        }

        if ("_wsc_entries".equals(key)) {
            String rawTarget = capturedTarget.toLegacyEntry();
            if (rawTarget == null) {
                showCaptureToast("Nothing to add from that slot", CAPTURE_TOAST_ERROR);
                return true;
            }
            String norm = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(rawTarget);
            if (norm == null || norm.isBlank()) {
                showCaptureToast("Nothing to add from that slot", CAPTURE_TOAST_ERROR);
                return true;
            }
            String disp = autismclient.util.macro.StoreItemAction.formatTargetEntry(norm);
            if (wscEditIndex >= 0 && wscEditIndex < wscEntries.size()) {

                if (!wscTargetExistsOtherThan(norm, wscEditIndex)) {
                    WaitForSlotChangeAction.WaitEntry entry = wscEntries.get(wscEditIndex);
                    entry.target = norm;
                    entry.itemTarget = capturedTarget.copy();
                    syncWscEditorFromEntry(entry);
                    showCaptureToast("Updated: " + disp, CAPTURE_TOAST_SUCCESS);
                } else {
                    showCaptureToast("Already in list: " + disp, CAPTURE_TOAST_ERROR);
                }
            } else {
                if (wscTargetExists(norm)) {
                    showCaptureToast("Already added: " + disp, CAPTURE_TOAST_ERROR);
                } else {
                    WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry(norm, wscAddMode, wscAddCount);
                    entry.itemTarget = capturedTarget.copy();
                    entry.target = norm;
                    wscEntries.add(entry);
                    showCaptureToast("Added: " + disp, CAPTURE_TOAST_SUCCESS);
                }
            }
            return true;
        }

        if ("wait_inventory_slot".equals(key)) {
            setWaitInventorySlot(Math.max(0, visibleSlot));
            captureSession.clearItemSlotCapture();
            exitCaptureMode(false, false);
            showCaptureToast("Slot set: " + Math.max(0, visibleSlot), CAPTURE_TOAST_SUCCESS);
            return true;
        }

        if ("itemName".equals(key)
                && targetAction != null
                && targetAction.getType() == MacroActionType.USE_ITEM) {
            AutismChatField itemField = textFields.get("itemName");
            AutismChatField slotField = textFields.get("slot");
            if (capturedTarget.hasIdentity()) {
                ItemTarget nameOnly = stripSlotFromTarget(capturedTarget);
                if (itemField != null) itemField.setText(nameOnly.editorText());
                editorItemFields.put("itemName", nameOnly.copy());
                showCaptureToast("Item set: " + nameOnly.displayLabel(), CAPTURE_TOAST_SUCCESS);
            } else if (capturedTarget.hasSlot()) {
                if (itemField != null) itemField.setText("");
                editorItemFields.remove("itemName");
                if (slotField != null) slotField.setText(String.valueOf(Math.max(0, capturedTarget.slot)));
                showCaptureToast("Slot set: " + Math.max(0, capturedTarget.slot), CAPTURE_TOAST_SUCCESS);
            } else {
                showCaptureToast("Nothing to capture from that slot", CAPTURE_TOAST_ERROR);
            }
            captureSession.clearItemSlotCapture();
            exitCaptureMode(false, false);
            return true;
        }

        if (stringLists.containsKey(key)) {
            List<String> list = stringLists.get(key);
            if (list == null) return false;
            CaptureListAddResult result = isStoreItemTargetListKey(key)
                    ? tryAddCapturedStoreItemEntry(slot, itemName, registryId, visibleSlot, useSlot, list)
                    : tryAddCapturedStringListEntry(
                            findField(key),
                            key,
                            list,
                            useSlot
                                    ? capturedTarget.toLegacyEntry()
                                    : (isXCarryListKey(key)
                                    ? capturedTarget.toLegacyEntry()
                                    : (isInventoryAuditTargetListKey(key)
                                            ? stripSlotFromTarget(capturedTarget).toLegacyEntry()
                                    : (usesStoreTargetFormatting(key)
                                            ? capturedTarget.toLegacyEntry()
                                            : itemName)))
                    );
        if (result != null && result.added() && (usesStoreTargetFormatting(key) || isXCarryListKey(key) || isInventoryAuditTargetListKey(key))) {
                ItemTarget preservedTarget = isStoreItemTargetListKey(key)
                        ? storeCaptureTarget(capturedTarget)
                        : isInventoryAuditTargetListKey(key)
                        ? (useSlot ? capturedTarget : stripSlotFromTarget(capturedTarget))
                        : capturedTarget;
                preserveCapturedListTarget(key, list, preservedTarget);
            }
            if (result != null && result.message() != null && !result.message().isBlank()) {
                showCaptureToast(result.message(), result.accentColor());
            }
            return true;
        }

        AutismChatField tf = textFields.get(key);
        if (tf != null) {
            FieldDef field = findField(key);
            if (field != null && field.type() == FieldType.SLOT) {

                tf.setText(String.valueOf(Math.max(0, visibleSlot)));
            } else if (useSlot) {

                tf.setText(capturedTarget.toLegacyEntry());
                if ("itemName".equals(key) || "fromItemName".equals(key) || "toItemName".equals(key) || editorItemFields.containsKey(key)) {
                    editorItemFields.put(key, capturedTarget.copy());
                }
            } else {

                ItemTarget nameOnly = stripSlotFromTarget(capturedTarget);
                tf.setText(nameOnly.editorText());
                if ("itemName".equals(key) || "fromItemName".equals(key) || "toItemName".equals(key) || editorItemFields.containsKey(key)) {
                    editorItemFields.put(key, nameOnly.copy());
                }
            }
            captureSession.clearItemSlotCapture();
            exitCaptureMode(false, false);
            return true;
        }

        return false;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        flushPendingPayloadEdit(false);
        frameDelta = delta;

        hitRegions.clear();
        scrollDragRegions.clear();

        int rawMouseX = mouseX;
        int rawMouseY = mouseY;
        if (!enumDropdowns.isEmpty() && CompactDropdown.isMenuOpen(enumDropdowns)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        enumDropdowns.clear();
        bodyInputClip = null;

        if (!collapsed) {
            int neededH = HEADER_HEIGHT + PAD + computeContentH() + FOOTER_H + PAD;
            int minH    = currentMinPanelHeight();
            int maxH    = Math.max(minH, AutismUiScale.getVirtualScreenHeight() * 4 / 5);
            panelH = Math.max(minH, Math.min(maxH, neededH));
        }
        AutismWindowLayout clamped = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;

        String title = targetActionTitle();

        AutismWindowLayout bounds = getBounds();
        renderWindowFrame(context, mouseX, mouseY, bounds, title, collapsed, dragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (clipBody) {
            try {
                int frameH = getRenderedFrameHeight(bounds, false);
                int bodyTop = panelY + HEADER_HEIGHT;
                int bodyBtm = panelY + frameH;
                if (bodyBtm > bodyTop + 1) {
                    renderBody(context, mouseX, mouseY, delta, bodyTop, bodyBtm);
                }
                if (!enumDropdowns.isEmpty()) {
                    CompactDropdown.renderButtons(context, textRenderer, enumDropdowns, rawMouseX, rawMouseY);
                }
            } finally {
                endWindowBodyClip(context, true);
            }
        }
        renderWindowInactiveOverlay(context, bounds, collapsed, dragging);

        if (!enumDropdowns.isEmpty() && CompactDropdown.isMenuOpen(enumDropdowns)) {
            context.nextStratum();
            CompactDropdown.renderOpenMenu(context, textRenderer, enumDropdowns, rawMouseX, rawMouseY);
        }

        if (packetSelectorOverlay.isVisible()) {
            packetSelectorOverlay.render(context, mouseX, mouseY, delta);
        }
        if (raceStepSelectorOverlay.isVisible()) {
            raceStepSelectorOverlay.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderBody(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                            int bodyTop, int bodyBtm) {

        if (itemAction != null) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (payloadAction != null) {
            int contentBtm = bodyBtm - FOOTER_H;
            int contentAreaH = contentBtm - bodyTop;
            int totalContentH = computeContentH();
            final int maxScroll = Math.max(0, totalContentH - contentAreaH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? SCROLLBAR_W + 1 : 0;
            int x = panelX + PAD;
            int w = panelW - PAD * 2 - sbReserve;

            UiScissorStack.global().push(context, UiBounds.of(
                    panelX + 1, bodyTop, Math.max(0, panelW - 2), Math.max(0, contentBtm - bodyTop)));
            try {
                renderPayloadPanel(context, x, bodyTop - scrollOffset, w, mouseX, mouseY, delta);
            } finally {
                UiScissorStack.global().pop(context);
            }

            if (needsScroll) {
                int sbX = panelX + panelW - SCROLLBAR_W - 1;
                drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, scrollOffset, mouseX, mouseY);
                final int capSbY = bodyTop, capSbH = contentAreaH;
                scrollDragRegions.add(new ScrollDragRegion(sbX, bodyTop, SCROLLBAR_W, contentAreaH, my -> {
                    int rel = Math.max(0, Math.min(capSbH, my - capSbY));
                    scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
                }));
            }
        } else if (targetAction != null && targetAction.getType() == MacroActionType.SEND_PACKET) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderSendPacketPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitPacketPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitPacketMatchPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_SOUND) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitSoundPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_ENTITY) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitEntityPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.USE_ITEM) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderUseItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.LOOK_AT_BLOCK) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderLookAtPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.ROTATE) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderRotatePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.GO_TO) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderGoToPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.SWAP_SLOTS) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderSwapSlotsPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.CLICK) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderClickPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.DISCONNECT) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderDisconnectPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_GUI) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitGuiPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_CHAT) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitChatPanel(context, x, bodyTop, bodyBtm - FOOTER_H, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.SELECT_SLOT) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderSelectSlotPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_COOLDOWN) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitCooldownPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && (targetAction.getType() == MacroActionType.OPEN_CONTAINER
                                          || targetAction.getType() == MacroActionType.INTERACT_ENTITY)) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderOpenContainerPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitSlotChangePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_INVENTORY_PREDICATE) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderWaitInventoryPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
            int contentBtm = bodyBtm - FOOTER_H;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = computeContentH();
            final int maxScroll = Math.max(0, totalContentH - contentAreaH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? SCROLLBAR_W + 1 : 0;
            int x = panelX + PAD;
            int w = panelW - PAD * 2 - sbReserve;
            UiBounds clip = UiBounds.of(panelX + 1, bodyTop,
                Math.max(0, panelW - 2), contentAreaH);
            bodyInputClip = clip;
            int firstBodyHit = hitRegions.size();

            UiScissorStack.global().push(context, clip);
            try {
                renderCaptureValuePanel(context, x, bodyTop - scrollOffset, w, mouseX, mouseY, delta);
            } finally {
                UiScissorStack.global().pop(context);
            }
            clipHitRegions(firstBodyHit, clip);

            if (needsScroll) {
                int sbX = panelX + panelW - SCROLLBAR_W - 1;
                drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, scrollOffset, mouseX, mouseY);
                final int capSbY = bodyTop;
                final int capSbH = contentAreaH;
                scrollDragRegions.add(new ScrollDragRegion(sbX, bodyTop, SCROLLBAR_W, contentAreaH, my -> {
                    int rel = Math.max(0, Math.min(capSbH, my - capSbY));
                    scrollOffset = Math.max(0, Math.min(maxScroll,
                        rel * maxScroll / Math.max(1, capSbH)));
                }));
            }
        } else if (targetAction != null && targetAction.getType() == MacroActionType.DELAY_PACKETS) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderDelayPacketsPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.MINE) {
            int contentBtm = bodyBtm - FOOTER_H;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = computeContentH();
            final int maxScroll = Math.max(0, totalContentH - contentAreaH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? SCROLLBAR_W + 1 : 0;
            int x = panelX + PAD;
            int w = panelW - PAD * 2 - sbReserve;
            UiBounds clip = UiBounds.of(panelX + 1, bodyTop, Math.max(0, panelW - 2), contentAreaH);
            bodyInputClip = clip;
            int firstBodyHit = hitRegions.size();

            UiScissorStack.global().push(context, clip);
            try {
                renderMinePanel(context, x, bodyTop - scrollOffset, w, mouseX, mouseY, delta);
            } finally {
                UiScissorStack.global().pop(context);
            }
            clipHitRegions(firstBodyHit, clip);

            if (needsScroll) {
                int sbX = panelX + panelW - SCROLLBAR_W - 1;
                drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, scrollOffset, mouseX, mouseY);
                final int capSbY = bodyTop;
                final int capSbH = contentAreaH;
                scrollDragRegions.add(new ScrollDragRegion(sbX, bodyTop, SCROLLBAR_W, contentAreaH, my -> {
                    int rel = Math.max(0, Math.min(capSbH, my - capSbY));
                    scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
                }));
            }
        } else if (targetAction != null && targetAction.getType() == MacroActionType.PAY) {
            int contentBtm = bodyBtm - FOOTER_H;
            int contentAreaH = Math.max(0, contentBtm - bodyTop);
            int totalContentH = computeContentH();
            final int maxScroll = Math.max(0, totalContentH - contentAreaH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? SCROLLBAR_W + 1 : 0;
            int x = panelX + PAD;
            int w = panelW - PAD * 2 - sbReserve;
            UiBounds clip = UiBounds.of(panelX + 1, bodyTop, Math.max(0, panelW - 2), contentAreaH);
            bodyInputClip = clip;
            int firstBodyHit = hitRegions.size();

            UiScissorStack.global().push(context, clip);
            try {
                renderPayPanel(context, x, bodyTop - scrollOffset, w, mouseX, mouseY, delta);
            } finally {
                UiScissorStack.global().pop(context);
            }
            clipHitRegions(firstBodyHit, clip);

            if (needsScroll) {
                int sbX = panelX + panelW - SCROLLBAR_W - 1;
                drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, scrollOffset, mouseX, mouseY);
                final int capSbY = bodyTop;
                final int capSbH = contentAreaH;
                scrollDragRegions.add(new ScrollDragRegion(sbX, bodyTop, SCROLLBAR_W, contentAreaH, my -> {
                    int rel = Math.max(0, Math.min(capSbH, my - capSbY));
                    scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
                }));
            }
        } else if (targetAction != null && targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderInventoryAuditPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.STORE_ITEM) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderStoreItemPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (targetAction != null && targetAction.getType() == MacroActionType.TOGGLE_MODULE) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderToggleModulePanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (craftEntries != null) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderCraftPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (dropAction != null) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderDropPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (lanStepEntries != null) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            renderLanStepPanel(context, x, bodyTop, w, mouseX, mouseY, delta);
        } else if (schema != null && !schema.fields().isEmpty()) {
            int contentBtm   = bodyBtm - FOOTER_H;
            int contentAreaH = contentBtm - bodyTop;
            int totalContentH = computeContentH();
            final int maxScroll = Math.max(0, totalContentH - contentAreaH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            boolean needsScroll = maxScroll > 0;
            int sbReserve = needsScroll ? SCROLLBAR_W + 1 : 0;
            int x = panelX + PAD;
            int w = panelW - PAD * 2 - sbReserve;

            UiScissorStack.global().push(context, UiBounds.of(
                    panelX + 1, bodyTop, Math.max(0, panelW - 2), Math.max(0, contentBtm - bodyTop)));
            try {
            int y = bodyTop + PAD - scrollOffset;

            if (targetAction != null && targetAction.getType() == MacroActionType.PLACE) {
                y = renderFieldByKey(context, "packetOrder", x, y, w, mouseX, mouseY, delta);

                BlockPos previewPos = null;
                try {
                    AutismChatField fx = textFields.get("blockPos_0");
                    AutismChatField fy = textFields.get("blockPos_1");
                    AutismChatField fz = textFields.get("blockPos_2");
                    if (fx != null && fy != null && fz != null) {
                        String sx = fx.getText().strip();
                        String sy = fy.getText().strip();
                        String sz = fz.getText().strip();
                        if (!sx.isEmpty() && !sy.isEmpty() && !sz.isEmpty()) {
                            int xPos = Integer.parseInt(sx);
                            int yPos = Integer.parseInt(sy);
                            int zPos = Integer.parseInt(sz);
                            previewPos = new BlockPos(xPos, yPos, zPos);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    previewPos = null;
                }
                if (previewPos != null) {
                    AutismInstaBreakRenderer.setTarget(previewPos, 0xFFFF3B3B);
                }

                Identifier placeFont = theme.fontFor(UiTone.BODY);
                UiText.draw(context, textRenderer, "Item", placeFont,
                        AutismColors.textSecondary(), x, y + 2, false);
                y += 13;
                renderPlaceItemRow(context, x, y, w, mouseX, mouseY, delta);
                y += 16 + 4;
                y = renderEditorHint(context, x, y, w,
                        "Name:any  Slot:exact  None:held");
                y += ROW_GAP;

                renderInlineToggle(context, placeFont, "place_waitForItem", "Wait for Item", x, y, w, mouseX, mouseY);
                y += 18;
                renderInlineToggle(context, placeFont, "place_silentSwitch", "Silent Switch", x, y, w, mouseX, mouseY);
                y += 18;
            }

            for (FieldDef field : schema.fields()) {
                if (field.type() == FieldType.STRING_LIST) continue;
                if (isGuiWaitAfterKey(field.key())) continue;
                if (!isFieldVisible(field)) continue;
                if (targetAction != null && targetAction.getType() == MacroActionType.PLACE
                        && ("itemName".equals(field.key()) || "packetOrder".equals(field.key()))) continue;
                renderRow(context, field, x, y, w, mouseX, mouseY, delta);
                y += rowH(field) + ROW_GAP;
            }

            for (FieldDef field : schema.fields()) {
                if (field.type() != FieldType.STRING_LIST) continue;
                if (isGuiWaitAfterKey(field.key())) continue;
                if (!isFieldVisible(field)) continue;
                if (targetAction != null && targetAction.getType() == MacroActionType.PLACE
                        && ("itemName".equals(field.key()) || "packetOrder".equals(field.key()))) continue;
                renderRow(context, field, x, y, w, mouseX, mouseY, delta);
                y += rowH(field) + ROW_GAP;
            }

            if (targetAction != null && targetAction.getType() == MacroActionType.PACKET) {
                renderPacketActionButtons(context, x, y, w, mouseX, mouseY);
                y += 52;
            }
            if (targetAction != null && targetAction.getType() == MacroActionType.SEND_PACKET) {
                renderSendPacketButtons(context, x, y, w, mouseX, mouseY);
                y += 52;
            }
            if (targetAction != null && targetAction.getType() == MacroActionType.DELAY_PACKETS) {
                renderDelayPacketsPresetButtons(context, x, y, w, mouseX, mouseY);
            }

            } finally {
                UiScissorStack.global().pop(context);
            }

            if (needsScroll) {
                int sbX = panelX + panelW - SCROLLBAR_W - 1;
                drawScrollbar(context, sbX, bodyTop, contentAreaH, totalContentH, contentAreaH, 1, scrollOffset, mouseX, mouseY);
                final int capSbY = bodyTop, capSbH = contentAreaH;
                scrollDragRegions.add(new ScrollDragRegion(sbX, bodyTop, SCROLLBAR_W, contentAreaH, my -> {
                    int rel = Math.max(0, Math.min(capSbH, my - capSbY));
                    scrollOffset = Math.max(0, Math.min(maxScroll, rel * maxScroll / Math.max(1, capSbH)));
                }));
            }
        }

        renderFooter(context, bodyBtm - FOOTER_H, mouseX, mouseY);
    }

    private void renderItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font   = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        int headerBtnW = 44;

        UiText.draw(ctx, textRenderer,
                "Items / Exact Slots (" + itemAction.itemNames.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        boolean canClearAll = !itemAction.itemNames.isEmpty();
        int clearX = x + w - headerBtnW;
        renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClearAll, mx, my, () -> {
            itemAction.itemNames.clear();
            itemAction.itemTimes.clear();
            itemAction.itemActionIdx.clear();
            itemAction.itemButtons.clear();
            itemAction.itemSearchScopes.clear();
            itemAction.stackAmountModes.clear();
            clearItemEditSelection();
            rebuildItemFields();
        });
        cy += 14;

        int selAreaH = SEL_LIST_MAX_VIS * SEL_ITEM_H;
        int sbX = x + w - SCROLLBAR_W;

        DirectScrollViewport itemViewport = getOrCreateViewport(selectedScrollViewports, "_item_entries",
            x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        itemViewport.setContentHeight(itemAction.itemNames.size() * SEL_ITEM_H);

        itemViewport.renderScrollbar(ctx, mx, my);

        autismclient.util.AutismDropAction[] ACTIONS = autismclient.util.AutismDropAction.values();
        int sbGap   = SCROLLBAR_W + 2;
        int delW    = 13;
        int itemW   = w - sbGap - delW - 2;

        if (!itemAction.itemNames.isEmpty()) {
            itemViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstVis = itemViewport.getFirstVisibleRow();
            int iy0 = cy - (itemViewport.getScrollOffset() % SEL_ITEM_H);
            for (int i = firstVis; i < itemAction.itemNames.size() && i <= itemViewport.getLastVisibleRow(); i++) {
                int iy = itemViewport.getRowScreenY(i);
                if (iy == Integer.MIN_VALUE) continue;
                String entry      = itemAction.itemNames.get(i);
                int eiActIdx      = itemAction.getItemActionIdx(i);
                autismclient.util.AutismDropAction eiAct = ACTIONS[eiActIdx];
                int eiBtn         = itemAction.getItemButton(i);
                boolean selected  = i == itemEditIndex;

                ItemTarget entryTarget = targetAt(itemAction.itemTargets, itemAction.itemNames, i);
                Component displayName = formatItemTargetText(entryTarget, entry);
                boolean rowHovered = mx >= x && mx < x + itemW && my >= iy && my < iy + 13;

                String summaryAct = eiAct.shortName;
                String summaryBtn = eiAct == autismclient.util.AutismDropAction.SWAP
                        ? "H" + Math.max(1, Math.min(9, eiBtn + 1))
                        : eiAct == autismclient.util.AutismDropAction.PICKUP_ALL
                        ? ""
                        : switch (eiBtn) { case 1 -> "R"; case 2 -> "M"; default -> "L"; };
                int times = itemAction.getItemTime(i);
                String summary = " \u2022 " + summaryAct
                        + (summaryBtn.isBlank() ? "" : " " + summaryBtn)
                        + (times != 1 ? " \u00d7" + times : "");
                Component rowLabel = Component.empty().append(displayName).append(
                        Component.literal(summary).withStyle(s -> s.withColor(0xFF888888)));

                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        rowLabel,
                        UiBounds.of(x, iy, itemW, 13),
                        rowHovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        true
                );

                if (iy + 13 > cy && iy < cy + selAreaH) {
                    final int fi = i;
                    hitRegions.add(new HitRegion(x, Math.max(cy, iy), itemW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> toggleItemEditSelection(fi)));
                }

                if (iy + 13 > cy && iy < cy + selAreaH) {
                    int delX = x + itemW + 2;
                    final int fi = i;
                    renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                        itemAction.itemNames.remove(fi);
                        if (fi < itemAction.itemTargets.size()) itemAction.itemTargets.remove(fi);
                        if (fi < itemAction.itemTimes.size()) itemAction.itemTimes.remove(fi);
                        if (fi < itemAction.itemActionIdx.size()) itemAction.itemActionIdx.remove(fi);
                        if (fi < itemAction.itemButtons.size()) itemAction.itemButtons.remove(fi);
                        if (fi < itemAction.itemSearchScopes.size()) itemAction.itemSearchScopes.remove(fi);
                        if (fi < itemAction.stackAmountModes.size()) itemAction.stackAmountModes.remove(fi);
                        if (itemEditIndex == fi) clearItemEditSelection();
                        else if (itemEditIndex > fi) itemEditIndex--;
                        rebuildItemFields();
                        DirectScrollViewport vp = selectedScrollViewports.get("_item_entries");
                        if (vp != null) vp.scrollBy(-1);
                    });
                }
            }
            itemViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No rows yet. Add an item or exact slot.", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        int addPickW = 32;
        int addBtnW  = 34;
        int slotW    = 26;
        AutismChatField addF = addFields.get("_item_add");
        AutismChatField entrySlotF = textFields.get("item_entrySlot");
        if (addF != null && entrySlotF != null) {
            int pickX = x + w - addBtnW - 3 - addPickW;
            int plusX = x + w - addBtnW;
            int slotX = pickX - 3 - slotW;
            addF.setX(x); addF.setY(cy + 1); addF.setWidth(slotX - x - 2);
            addF.render(ctx, mx, my, delta);
            entrySlotF.setX(slotX); entrySlotF.setY(cy + 1); entrySlotF.setWidth(slotW);
            entrySlotF.render(ctx, mx, my, delta);

            boolean capturing = captureSession.isItemSlotCapture("_item_entries");
            renderFieldCaptureButton(
                    ctx,
                    pickX,
                    cy,
                    addPickW,
                    14,
                    CaptureMode.ITEM_SLOT,
                    capturing,
                    true,
                    mx,
                    my,
                    () -> {
                toggleItemSlotCapture("_item_entries");
            });

            String addLbl = itemEditIndex >= 0 ? "New" : "+Add";
            renderOverlayButton(ctx, plusX, cy, addBtnW, 14, addLbl, CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                if (itemEditIndex >= 0) clearItemEditSelection();
                else applyItemEntryEditor();
            });
        }
        cy += 16;
        cy += 4;

        if (itemEditIndex >= 0 && itemEditIndex < itemAction.itemNames.size()) {
            int eiActIdx = itemAction.getItemActionIdx(itemEditIndex);
            autismclient.util.AutismDropAction eiAct = ACTIONS[eiActIdx];
            int eiBtn = itemAction.getItemButton(itemEditIndex);
            boolean btnActive = eiAct == autismclient.util.AutismDropAction.PICKUP
                    || eiAct == autismclient.util.AutismDropAction.QUICK_MOVE
                    || eiAct == autismclient.util.AutismDropAction.SWAP;
            final int editIdx = itemEditIndex;

            int editActW = 54;
            int editBtnW = 28;
            int editTimesW = 28;
            int editGap = 3;
            java.util.List<String> actOptions = new java.util.ArrayList<>();
            for (autismclient.util.AutismDropAction a : autismclient.util.macro.ItemAction.itemClickActions()) actOptions.add(a.shortName);
            renderInlineDropdown("item_action", actOptions, itemAction.getItemClickActionOptionIndex(editIdx), x, cy, editActW, 14,
                    idx -> itemAction.setItemActionByOptionIndex(editIdx, idx));
            String btnLbl = eiAct == autismclient.util.AutismDropAction.SWAP
                    ? "H" + Math.max(1, Math.min(9, eiBtn + 1))
                    : eiAct == autismclient.util.AutismDropAction.PICKUP_ALL
                    ? "All"
                    : switch (eiBtn) { case 1 -> "R"; case 2 -> "M"; default -> "L"; };

            if (itemAction.itemButtonOptionCount(editIdx) > 2) {
                renderInlineDropdown("item_button", itemAction.itemButtonOptions(editIdx), itemAction.getItemButton(editIdx),
                        x + editActW + editGap, cy, editBtnW, 14, idx -> itemAction.setItemButton(editIdx, idx));
            } else {
                renderOverlayButton(ctx, x + editActW + editGap, cy, editBtnW, 14, btnLbl,
                        eiAct == autismclient.util.AutismDropAction.SWAP ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
                        btnActive, mx, my,
                        () -> itemAction.cycleItemButton(editIdx),
                        () -> itemAction.cycleItemButtonBackwards(editIdx));
            }
            AutismChatField tf = textFields.get("item_times_" + itemEditIndex);
            if (tf != null) { tf.setX(x + editActW + editGap + editBtnW + editGap); tf.setY(cy + 1); tf.setWidth(editTimesW); tf.render(ctx, mx, my, delta); }
            cy += 16;

            if (isEditingNameOnlyItemTargetingAction()) {
                renderItemTargetingControls(ctx, x, cy, w, mx, my);
                cy += ROW_H + ROW_GAP;
            }
        }

        renderGuiWaitRow(ctx, font, "item_waitForGuiBefore", "item_waitForGuiAfter", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        if (toggleStates.getOrDefault("item_waitForGuiBefore", false)
                || toggleStates.getOrDefault("item_waitForGuiAfter", false)) {
            AutismChatField guiF = textFields.get("item_guiName");
            if (guiF != null) {
                int lw = labelWidth(w, "GUI Name", font);
                drawLabel(ctx, "GUI Name", x, cy, lw, font);
                guiF.setX(controlX(x, lw)); guiF.setY(cy + 2); guiF.setWidth(controlWidth(w, lw));
                guiF.render(ctx, mx, my, delta);
            }
            cy += ROW_H + ROW_GAP;
        } else {
            AutismChatField guiF = textFields.get("item_guiName");
            if (guiF != null) guiF.setX(-1000);
        }

        renderInlineToggle(ctx, font, "item_waitForItem", "Wait for Item", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        renderInlineToggle(ctx, font, "item_useClickDelay", "Delay Clicks", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        if (toggleStates.getOrDefault("item_useClickDelay", false)) {
            AutismChatField delayF = textFields.get("item_clickDelayMs");
            if (delayF != null) {
                int lw = labelWidth(w, "Delay (ms)", font);
                drawLabel(ctx, "Delay (ms)", x, cy, lw, font);
                delayF.setX(controlX(x, lw)); delayF.setY(cy + 2); delayF.setWidth(controlWidth(w, lw));
                delayF.render(ctx, mx, my, delta);
            }
            cy += ROW_H + ROW_GAP;
        } else {
            AutismChatField delayF = textFields.get("item_clickDelayMs");
            if (delayF != null) delayF.setX(-1000);
        }

    }

    private void renderItemTargetingControls(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        if (itemAction == null || itemEditIndex < 0 || itemEditIndex >= itemAction.itemNames.size()) return;

        int gap = 4;
        int invW = Math.max(68, (w - gap) / 2);
        int stackW = Math.max(72, w - invW - gap);
        int stackX = x + invW + gap;
        autismclient.util.macro.ItemAction.ItemSearchScope scope = itemAction.getItemSearchScope(itemEditIndex);
        autismclient.util.macro.ItemAction.ItemSearchScope[] scopes = autismclient.util.macro.ItemAction.ItemSearchScope.values();
        java.util.List<String> scopeOptions = new java.util.ArrayList<>();
        for (autismclient.util.macro.ItemAction.ItemSearchScope s : scopes) scopeOptions.add(s.label);
        renderInlineDropdown("item_scope", scopeOptions, scope.ordinal(), x, y + 2, invW, 14,
                idx -> itemAction.setItemSearchScope(itemEditIndex, scopes[Math.max(0, Math.min(idx, scopes.length - 1))]));

        autismclient.util.macro.ItemAction.StackAmountMode mode = itemAction.getStackAmountMode(itemEditIndex);
        autismclient.util.macro.ItemAction.StackAmountMode[] stackModes = autismclient.util.macro.ItemAction.StackAmountMode.values();
        java.util.List<String> stackOptions = new java.util.ArrayList<>();
        for (autismclient.util.macro.ItemAction.StackAmountMode m : stackModes) stackOptions.add("Stack: " + m.label);
        renderInlineDropdown("item_stack", stackOptions, mode.ordinal(), stackX, y + 2, stackW, 14, idx -> {
            while (itemAction.stackAmountModes.size() <= itemEditIndex) itemAction.stackAmountModes.add(autismclient.util.macro.ItemAction.StackAmountMode.DEFAULT.ordinal());
            itemAction.stackAmountModes.set(itemEditIndex, stackModes[Math.max(0, Math.min(idx, stackModes.length - 1))].ordinal());
        });
    }

    private boolean isEditingNameOnlyItemTargetingAction() {
        if (itemAction == null || itemEditIndex < 0 || itemEditIndex >= itemAction.itemNames.size()) return false;
        ItemTarget target = targetAt(itemAction.itemTargets, itemAction.itemNames, itemEditIndex);
        if (!target.hasIdentity() || target.hasSlot()) return false;
        return itemAction.getItemAction(itemEditIndex) != autismclient.util.AutismDropAction.PICKUP_ALL
                || !toggleStates.getOrDefault("item_useCursorItemForPickupAll", itemAction.useCursorItemForPickupAll);
    }

    private void addItemEntry(ItemTarget target) {
        addTargetEntry(itemAction.itemTargets, itemAction.itemNames, target);
        while (itemAction.itemTimes.size()     < itemAction.itemNames.size()) itemAction.itemTimes.add(1);
        while (itemAction.itemActionIdx.size() < itemAction.itemNames.size()) itemAction.itemActionIdx.add(0);
        while (itemAction.itemButtons.size()   < itemAction.itemNames.size()) itemAction.itemButtons.add(0);
        while (itemAction.itemSearchScopes.size() < itemAction.itemNames.size()) itemAction.itemSearchScopes.add(0);
        while (itemAction.stackAmountModes.size() < itemAction.itemNames.size()) itemAction.stackAmountModes.add(0);
        int idx = itemAction.itemNames.size() - 1;
        AutismChatField f = makeField(28);
        f.setNumericOnly(true);
        f.setText("1");
        textFields.put("item_times_" + idx, f);
    }

    private void rebuildItemFields() {
        textFields.keySet().removeIf(k -> k.startsWith("item_times_"));

        trimTargetEntries(itemAction.itemTargets, itemAction.itemNames.size());
        while (itemAction.itemTimes.size()     > itemAction.itemNames.size()) itemAction.itemTimes.remove(itemAction.itemTimes.size() - 1);
        while (itemAction.itemActionIdx.size() > itemAction.itemNames.size()) itemAction.itemActionIdx.remove(itemAction.itemActionIdx.size() - 1);
        while (itemAction.itemButtons.size()   > itemAction.itemNames.size()) itemAction.itemButtons.remove(itemAction.itemButtons.size() - 1);
        while (itemAction.itemSearchScopes.size() > itemAction.itemNames.size()) itemAction.itemSearchScopes.remove(itemAction.itemSearchScopes.size() - 1);
        while (itemAction.stackAmountModes.size() > itemAction.itemNames.size()) itemAction.stackAmountModes.remove(itemAction.stackAmountModes.size() - 1);
        while (itemAction.itemSearchScopes.size() < itemAction.itemNames.size()) itemAction.itemSearchScopes.add(0);
        while (itemAction.stackAmountModes.size() < itemAction.itemNames.size()) itemAction.stackAmountModes.add(0);
        for (int i = 0; i < itemAction.itemNames.size(); i++) {
            AutismChatField f = makeField(28);
            f.setNumericOnly(true);
            f.setText(String.valueOf(itemAction.getItemTime(i)));
            textFields.put("item_times_" + i, f);
        }
    }

    private void toggleItemEditSelection(int index) {
        if (itemEditIndex == index) {
            clearItemEditSelection();
            return;
        }
        itemEditIndex = index;
        syncItemEntryEditorFromSelection();
    }

    private void clearItemEditSelection() {
        itemEditIndex = -1;
        AutismChatField addF = addFields.get("_item_add");
        suppressItemEntryLiveUpdate = true;
        if (addF != null) addF.setText("");
        AutismChatField slotF = textFields.get("item_entrySlot");
        if (slotF != null) slotF.setText("");
        suppressItemEntryLiveUpdate = false;
    }

    private void syncItemEntryEditorFromSelection() {
        AutismChatField addF = addFields.get("_item_add");
        AutismChatField slotF = textFields.get("item_entrySlot");
        if (addF == null || slotF == null) return;
        suppressItemEntryLiveUpdate = true;
        if (itemEditIndex < 0 || itemEditIndex >= itemAction.itemNames.size()) {
            addF.setText("");
            slotF.setText("");
            suppressItemEntryLiveUpdate = false;
            return;
        }
        ItemTarget target = targetAt(itemAction.itemTargets, itemAction.itemNames, itemEditIndex);
        addF.setText(target.editorText());
        slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
        suppressItemEntryLiveUpdate = false;
    }

    private void applyItemEntryEditor() {
        applyItemEntryEditor(false);
    }

    private void handleItemEntryEditorChanged() {
        if (suppressItemEntryLiveUpdate || itemAction == null) return;
        if (itemEditIndex < 0 || itemEditIndex >= itemAction.itemNames.size()) return;
        applyItemEntryEditor(true);
    }

    private void applyItemEntryEditor(boolean preserveSelection) {
        if (itemAction == null) return;
        AutismChatField addF = addFields.get("_item_add");
        AutismChatField slotF = textFields.get("item_entrySlot");
        if (addF == null || slotF == null) return;
        String nameText = addF.getText().strip();
        String slotText = slotF.getText().strip();
        ItemTarget target = buildEntryTargetFromEditor(nameText, slotText, targetAt(itemAction.itemTargets, itemAction.itemNames, itemEditIndex));
        String entry = target.toLegacyEntry();
        if (entry == null || entry.isBlank()) return;

        if (itemEditIndex >= 0 && itemEditIndex < itemAction.itemNames.size()) {
            if (!containsEntryOtherThan(itemAction.itemNames, entry, itemEditIndex)) {
                setTargetAt(itemAction.itemTargets, itemAction.itemNames, itemEditIndex, target);
            }
        } else if (!itemAction.itemNames.contains(entry)) {
            addItemEntry(target);
        }

        if (!preserveSelection) {
            clearItemEditSelection();
        }
    }

    private void applyCapturedItemEntry(ItemTarget rawTarget) {
        if (itemAction == null) return;

        ItemTarget target = storeCaptureTarget(rawTarget);
        String entry = target == null ? "" : target.toLegacyEntry();
        if (entry.isBlank()) return;

        int targetIndex = itemEditIndex;
        if (targetIndex >= 0 && targetIndex < itemAction.itemNames.size()) {
            if (!containsEntryOtherThan(itemAction.itemNames, entry, targetIndex)) {
                setTargetAt(itemAction.itemTargets, itemAction.itemNames, targetIndex, target.copy());
            }
        } else {
            targetIndex = itemAction.itemNames.indexOf(entry);
            if (targetIndex < 0) {
                addItemEntry(target.copy());
                targetIndex = itemAction.itemNames.size() - 1;
            }
        }

        itemEditIndex = targetIndex;
        AutismChatField addF = addFields.get("_item_add");
        if (addF != null) addF.setText(target.editorText());
    }

    private void renderInlineToggle(GuiGraphicsExtractor ctx, Identifier font, String stateKey, String label,
                                    int x, int y, int w, int mx, int my) {
        boolean val = toggleStates.getOrDefault(stateKey, false);
        int lw = labelWidth(w, label, font, 34);
        drawLabel(ctx, label, x, y, lw, font);
        int btnW = 34, btnH = 14;
        int btnX = x + w - btnW;
        renderOverlayToggleButton(
                ctx,
                btnX,
                y + 2,
                btnW,
                btnH,
                val ? "ON" : "OFF",
                val,
                "macro-inline:" + stateKey,
                mx,
                my,
                () -> toggleStates.put(stateKey, !toggleStates.getOrDefault(stateKey, false))
        );
    }

    private void renderGuiWaitRow(GuiGraphicsExtractor ctx, Identifier font, String beforeKey, String afterKey,
                                  int x, int y, int w, int mx, int my) {
        drawLabel(ctx, "GUI Wait", x, y, labelWidth(w, "GUI Wait", font, 90), font);
        int btnW = 42;
        int gap = 4;
        int afterX = x + w - btnW;
        int beforeX = afterX - gap - btnW;
        renderGuiWaitButton(ctx, beforeKey, "Before", beforeX, y + 2, btnW, mx, my);
        renderGuiWaitButton(ctx, afterKey, "After", afterX, y + 2, btnW, mx, my);
    }

    private void renderGuiWaitButton(GuiGraphicsExtractor ctx, String key, String label,
                                     int x, int y, int w, int mx, int my) {
        boolean val = toggleStates.getOrDefault(key, false);
        renderOverlayToggleButton(
                ctx,
                x,
                y,
                w,
                14,
                label,
                val,
                "macro-gui-wait:" + key,
                mx,
                my,
                () -> toggleStates.put(key, !toggleStates.getOrDefault(key, false))
        );
    }

    private void renderCraftPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w,
                                  int mx, int my, float delta) {
        Identifier font   = theme.fontFor(UiTone.BODY);
        int removeW       = 13;
        int maxTogW       = 42;
        int amountW       = 44;
        int headerBtnW    = 44;

        int cy = bodyTop + PAD;

        UiText.draw(ctx, textRenderer,
                "Craft Entries (" + craftEntries.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        boolean canClearEntries = !craftEntries.isEmpty();
        renderOverlayButton(ctx, x + w - headerBtnW, cy, headerBtnW, 14, "Clear",
                CompactOverlayButton.Variant.DANGER, canClearEntries, mx, my, () -> {
                    craftEntries.clear();
                    rebuildCraftFields();
                    DirectScrollViewport craftVp = selectedScrollViewports.get("_craft_entries");
                if (craftVp != null) craftVp.jumpTo(0);
                });
        cy += 14;

        int selAreaH = SEL_LIST_MAX_VIS * SEL_ITEM_H;

        DirectScrollViewport craftViewport = getOrCreateViewport(selectedScrollViewports, "_craft_entries",
            x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        craftViewport.setContentHeight(craftEntries.size() * SEL_ITEM_H);

        craftViewport.renderScrollbar(ctx, mx, my);

        int sbGap = SCROLLBAR_W + 2;
        if (!craftEntries.isEmpty()) {
            craftViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstSel = craftViewport.getFirstVisibleRow();
            for (int i = firstSel; i < craftEntries.size() && i <= craftViewport.getLastVisibleRow(); i++) {
                int siy = craftViewport.getRowScreenY(i);
                if (siy == Integer.MIN_VALUE) continue;
                CraftAction.CraftEntry entry = craftEntries.get(i);
                boolean useMax = toggleStates.getOrDefault("craft_useMax_" + i, false);

                int removeX = x + w - sbGap - removeW;
                final int fi = i;
                renderIconDeleteButton(ctx, removeX, siy + 1, removeW, mx, my, () -> {
                    craftEntries.remove(fi);
                    rebuildCraftFields();
                    DirectScrollViewport vp = selectedScrollViewports.get("_craft_entries");
                    if (vp != null) vp.scrollBy(-1);
                });

                int maxX = removeX - 3 - maxTogW;
                String mLbl = useMax ? "Max: On" : "Max: Off";
                final int fii = i;
                renderOverlayToggleButton(
                        ctx,
                        maxX,
                        siy + 1,
                        maxTogW,
                        13,
                        mLbl,
                        useMax,
                        "macro-craft-use-max:" + fii,
                        mx,
                        my,
                        () -> toggleStates.put("craft_useMax_" + fii,
                                !toggleStates.getOrDefault("craft_useMax_" + fii, false))
                );

                if (!useMax) {
                    int amtX = maxX - 3 - amountW;
                    AutismChatField af = textFields.get("craft_amount_" + i);
                    if (af != null) { af.setX(amtX); af.setY(siy + 1); af.setWidth(amountW); af.render(ctx, mx, my, delta); }
                }

                int nameW = useMax ? (maxX - 3 - x) : (maxX - 3 - amountW - 3 - x);
                boolean rowHovered = mx >= x && mx < x + nameW && my >= siy && my < siy + 13;
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        entry.resultNameComponent(),
                        UiBounds.of(x, siy, Math.max(1, nameW), 13),
                        rowHovered,
                        false,
                        useMax ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                        true
                );
            }
            craftViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No craft entries yet.", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        int refreshW = 44;
        AutismChatField srchF = addFields.get("_craft_search");
        if (srchF != null) {
            srchF.setX(x); srchF.setY(cy); srchF.setWidth(w - refreshW - 3);
            srchF.render(ctx, mx, my, delta);
        }
        int rfX = x + w - refreshW;
        renderOverlayButton(ctx, rfX, cy, refreshW, 14, "Reload",
                CompactOverlayButton.Variant.SECONDARY, true, mx, my, this::refreshCraftRecipes);
        cy += 16;

        String query = srchF != null ? srchF.getText() : "";
        if (!query.equals(craftLastQuery)) {
            craftFilteredRecipes = AutismCraftingHelper.filterRecipes(
                    craftAllRecipes != null ? craftAllRecipes : List.of(), query);
            craftLastQuery = query;
            craftRecipeScrollOffset = 0;
            if (craftSelectedRecipe != null
                    && (craftFilteredRecipes == null || !craftFilteredRecipes.contains(craftSelectedRecipe)))
                craftSelectedRecipe = null;
        }
        List<AutismCraftingHelper.CraftableRecipeOption> filtered =
                craftFilteredRecipes != null ? craftFilteredRecipes : List.of();

        int recipeListH  = CRAFT_LIST_H;

        DirectScrollViewport recipeViewport = getOrCreateViewport(catalogScrollViewports, "_craft_recipe_browser",
            x, cy, w, recipeListH, CATALOG_ITEM_H, SCROLLBAR_W);
        recipeViewport.setContentHeight(filtered.size() * CATALOG_ITEM_H);
        craftRecipeListBounds = new int[]{cy, recipeListH};

        recipeViewport.renderScrollbar(ctx, mx, my);

        int recW = w - SCROLLBAR_W - 1;
        if (filtered.isEmpty()) {
            CompactListRenderer.drawEmptyState(
                    ctx,
                    textRenderer,
                    craftAllRecipes == null || craftAllRecipes.isEmpty()
                            ? "No crafting recipes loaded. Use Reload."
                            : "No recipes match the search.",
                    x,
                    cy,
                    recW
            );
        } else {
            recipeViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstRec = recipeViewport.getFirstVisibleRow();
            for (int i = firstRec; i < filtered.size() && i <= recipeViewport.getLastVisibleRow(); i++) {
                int ry = recipeViewport.getRowScreenY(i);
                if (ry == Integer.MIN_VALUE) continue;
                AutismCraftingHelper.CraftableRecipeOption opt = filtered.get(i);
                boolean inList = craftEntries.stream().anyMatch(e ->
                        (opt.recipeKey != null && opt.recipeKey.equals(e.recipeKey))
                        || (opt.recipeId >= 0 && opt.recipeId == e.recipeId));
                boolean hov = !inList && mx >= x && mx < x + recW && my >= ry && my < ry + CATALOG_ITEM_H;
                CompactListRenderer.RowTone tone = inList
                        ? CompactListRenderer.RowTone.READY
                        : (opt.craftableNow ? CompactListRenderer.RowTone.NORMAL : CompactListRenderer.RowTone.MISSING);
                MacroTypedListControl.renderRow(ctx, textRenderer, opt.labelComponent,
                        UiBounds.of(x, ry, recW, CATALOG_ITEM_H), hov, inList, tone, true);
                if (opt.result != null && opt.result.getCount() > 1) {
                    String cnt = "\u00d7" + opt.result.getCount();
                    UiText.draw(ctx, textRenderer, cnt, font, AutismColors.textDim(),
                            x + recW - uiWidth(font, cnt) - 2, ry + 2, false);
                }
                final AutismCraftingHelper.CraftableRecipeOption fOpt = opt;
                final boolean fInList = inList;
                hitRegions.add(new HitRegion(x, ry, recW, CATALOG_ITEM_H, () -> {
                    if (fInList) {
                        craftEntries.removeIf(e ->
                                (fOpt.recipeKey != null && fOpt.recipeKey.equals(e.recipeKey))
                                || (fOpt.recipeId >= 0 && fOpt.recipeId == e.recipeId));
                        rebuildCraftFields();
                    } else {
                        CraftAction.CraftEntry newEntry = CraftAction.CraftEntry.fromOption(fOpt, 1, false);
                        craftEntries.add(newEntry);
                        int idx = craftEntries.size() - 1;
                        AutismChatField f = makeField(44);
                        f.setNumericOnly(true);
                        f.setText("1");
                        textFields.put("craft_amount_" + idx, f);
                        toggleStates.put("craft_useMax_" + idx, false);
                    }
                }));
            }
            recipeViewport.endRender(ctx);
        }
    }

    private void refreshCraftRecipes() {
        craftAllRecipes      = AutismCraftingHelper.getCraftableRecipes(MC);
        craftLastQuery       = null;
        craftSelectedRecipe  = null;
        craftRecipeScrollOffset = 0;
    }

    private void addCraftEntry() {
        if (craftSelectedRecipe == null) return;
        AutismChatField amtF = textFields.get("_craft_amount");
        int amount = 1;
        if (amtF != null && !craftUseMax) {
            try { amount = Math.max(1, Integer.parseInt(amtF.getText().strip())); }
            catch (NumberFormatException ignored) {  }
        }
        CraftAction.CraftEntry newEntry = CraftAction.CraftEntry.fromOption(craftSelectedRecipe, amount, craftUseMax);

        for (int i = 0; i < craftEntries.size(); i++) {
            CraftAction.CraftEntry existing = craftEntries.get(i);
            if ((newEntry.recipeKey != null && newEntry.recipeKey.equals(existing.recipeKey))
                    || (newEntry.recipeId >= 0 && newEntry.recipeId == existing.recipeId)) {
                existing.amount       = newEntry.amount;
                existing.useMaxAmount = newEntry.useMaxAmount;
                toggleStates.put("craft_useMax_" + i, newEntry.useMaxAmount);
                AutismChatField f = textFields.get("craft_amount_" + i);
                if (f != null) f.setText(String.valueOf(newEntry.amount));
                return;
            }
        }

        craftEntries.add(newEntry);
        int idx = craftEntries.size() - 1;
        AutismChatField f = makeField(44);
        f.setNumericOnly(true);
        f.setText(String.valueOf(newEntry.amount));
        textFields.put("craft_amount_" + idx, f);
        toggleStates.put("craft_useMax_" + idx, newEntry.useMaxAmount);
    }

    private void rebuildCraftFields() {
        textFields.keySet().removeIf(k -> k.startsWith("craft_amount_"));
        for (int i = 0; i < craftEntries.size(); i++) {
            CraftAction.CraftEntry entry = craftEntries.get(i);
            AutismChatField f = makeField(44);
            f.setNumericOnly(true);
            f.setText(String.valueOf(entry.amount));
            textFields.put("craft_amount_" + i, f);
            if (!toggleStates.containsKey("craft_useMax_" + i))
                toggleStates.put("craft_useMax_" + i, entry.useMaxAmount);
        }
        toggleStates.keySet().removeIf(k -> {
            if (!k.startsWith("craft_useMax_")) return false;
            try { return Integer.parseInt(k.substring("craft_useMax_".length())) >= craftEntries.size(); }
            catch (NumberFormatException e) { return true; }
        });
    }

    private void renderDropPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        int headerBtnW = 44;

        UiText.draw(ctx, textRenderer,
                "Drop Entries (" + dropAction.itemNames.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        boolean canClearAll = !dropAction.itemNames.isEmpty();
        int clearX = x + w - headerBtnW;
        renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear",
                CompactOverlayButton.Variant.DANGER, canClearAll, mx, my, () -> {
                    dropAction.itemNames.clear();
                    dropAction.itemTargets.clear();
                    dropAction.itemCounts.clear();
                    clearDropEditSelection();
                    rebuildDropFields();
                });
        cy += 14;

        int selAreaH = SEL_LIST_MAX_VIS * SEL_ITEM_H;

        DirectScrollViewport dropViewport = getOrCreateViewport(selectedScrollViewports, "_drop_entries",
            x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        dropViewport.setContentHeight(dropAction.itemNames.size() * SEL_ITEM_H);

        dropViewport.renderScrollbar(ctx, mx, my);

        int sbGap   = SCROLLBAR_W + 2;
        int delW    = 13;
        int dropItemW = w - sbGap - delW - 2;

        if (!dropAction.itemNames.isEmpty()) {
            dropViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstVis = dropViewport.getFirstVisibleRow();
            for (int i = firstVis; i < dropAction.itemNames.size() && i <= dropViewport.getLastVisibleRow(); i++) {
                int iy = dropViewport.getRowScreenY(i);
                if (iy == Integer.MIN_VALUE) continue;
                String entry = dropAction.itemNames.get(i);
                boolean selected = i == dropEditIndex;

                ItemTarget entryTarget = targetAt(dropAction.itemTargets, dropAction.itemNames, i);
                Component displayName = formatItemTargetText(entryTarget, entry);

                int cnt = i < dropAction.itemCounts.size() ? dropAction.itemCounts.get(i) : 0;
                String summary = " \u2022 " + (cnt == 0 ? "all" : "\u00d7" + cnt);
                Component rowLabel = Component.empty().append(displayName).append(
                        Component.literal(summary).withStyle(s -> s.withColor(0xFF888888)));

                boolean rowHovered = mx >= x && mx < x + dropItemW && my >= iy && my < iy + 13;
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        rowLabel,
                        UiBounds.of(x, iy, dropItemW, 13),
                        rowHovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        true
                );

                if (iy + 13 > cy && iy < cy + selAreaH) {
                    final int fi = i;
                    hitRegions.add(new HitRegion(x, Math.max(cy, iy), dropItemW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> toggleDropEditSelection(fi)));
                }

                if (iy + 13 > cy && iy < cy + selAreaH) {
                    int delX = x + dropItemW + 2;
                    final int fi = i;
                    renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                        dropAction.itemNames.remove(fi);
                        if (fi < dropAction.itemTargets.size()) dropAction.itemTargets.remove(fi);
                        if (fi < dropAction.itemCounts.size()) dropAction.itemCounts.remove(fi);
                        if (dropEditIndex == fi) clearDropEditSelection();
                        else if (dropEditIndex > fi) dropEditIndex--;
                        rebuildDropFields();
                        DirectScrollViewport vp = selectedScrollViewports.get("_drop_entries");
                        if (vp != null) vp.scrollBy(-1);
                    });
                }
            }
            dropViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer,
                    "Optional: add items/slots, or use the fallback below.", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        int addPickW = 32;
        int addBtnW  = 34;
        int slotW    = 44;
        AutismChatField addF = addFields.get("_drop_add");
        AutismChatField entrySlotF = textFields.get("drop_entrySlot");
        if (addF != null && entrySlotF != null) {
            int pickX = x + w - addBtnW - 3 - addPickW;
            int plusX = x + w - addBtnW;
            int slotX = pickX - 3 - slotW;
            addF.setX(x); addF.setY(cy + 1); addF.setWidth(slotX - x - 2);
            addF.render(ctx, mx, my, delta);
            entrySlotF.setX(slotX); entrySlotF.setY(cy + 1); entrySlotF.setWidth(slotW);
            entrySlotF.render(ctx, mx, my, delta);

            boolean capturing = captureSession.isItemSlotCapture("_drop_entries");
            renderFieldCaptureButton(
                    ctx,
                    pickX,
                    cy,
                    addPickW,
                    14,
                    CaptureMode.ITEM_SLOT,
                    capturing,
                    true,
                    mx,
                    my,
                    () -> {
                toggleItemSlotCapture("_drop_entries");
            });

            String addLbl = dropEditIndex >= 0 ? "New" : "+Add";
            renderOverlayButton(ctx, plusX, cy, addBtnW, 14, addLbl,
                    CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                if (dropEditIndex >= 0) clearDropEditSelection();
                else applyDropEntryEditor();
            });
        }
        cy += 16;
        cy += 4;
        boolean editingSelectedDrop = dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size();

        boolean noDropTargets = dropAction.itemNames.isEmpty();
        String dropHint = editingSelectedDrop
                ? "Editing this row. 0 means drop the full stack from that slot or item."
                : noDropTargets
                ? (dropAction.mode == autismclient.util.macro.DropAction.DropMode.ALL
                    ? "No items selected: Drop All drops the entire main inventory and hotbar."
                    : "No items selected: Times drops the currently held item by the count below.")
                : "No row selected. These controls set defaults for newly added rows.";
        cy = renderEditorHint(ctx, x, cy, w, dropHint);

        {
            boolean dropAllSelected = editingSelectedDrop
                    ? getDropEntryCount(dropEditIndex) == 0
                    : dropAction.mode == autismclient.util.macro.DropAction.DropMode.ALL;
            String modeLabel = editingSelectedDrop ? "Selected Row" : noDropTargets ? "No Items" : "Default Mode";
            int lw = labelWidth(w, modeLabel, font);
            drawLabel(ctx, modeLabel, x, cy, lw, font);
            int ctrlX = controlX(x, lw);
            int ctrlW = controlWidth(w, lw);
            int leftW = (ctrlW - 2) / 2;
            int rightX = ctrlX + leftW + 2;
            int rightW = ctrlW - leftW - 2;
            renderOverlayButton(ctx, ctrlX, cy + 2, leftW, 14, "Drop All",
                    dropAllSelected ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
                    true, mx, my, () -> {
                if (dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size()) {
                    setDropEntryCount(dropEditIndex, 0);
                } else {
                    enumIndices.put("drop_mode", 0);
                    dropAction.mode = autismclient.util.macro.DropAction.DropMode.ALL;
                }
                syncDropCountEditorField();
            });
            renderOverlayButton(ctx, rightX, cy + 2, rightW, 14, "Times",
                    !dropAllSelected ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.GHOST,
                    true, mx, my, () -> {
                if (dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size()) {
                    setDropEntryCount(dropEditIndex, Math.max(1, getDropEntryCount(dropEditIndex)));
                } else {
                    enumIndices.put("drop_mode", 1);
                    dropAction.mode = autismclient.util.macro.DropAction.DropMode.TIMES;
                }
                syncDropCountEditorField();
            });
        }
        cy += ROW_H + ROW_GAP;

        AutismChatField cntF = textFields.get("drop_globalCount");
        syncDropCountEditorField();
        if (cntF != null) {
            String countLabel = editingSelectedDrop ? "Selected Count" : noDropTargets ? "Held Count" : "Default Count";
            int lw = labelWidth(w, countLabel, font);
            drawLabel(ctx, countLabel, x, cy, lw, font);
            cntF.setX(controlX(x, lw)); cntF.setY(cy + 2); cntF.setWidth(controlWidth(w, lw));
            cntF.render(ctx, mx, my, delta);
            cy += ROW_H + ROW_GAP;
        }

        renderGuiWaitRow(ctx, font, "drop_waitForGuiBefore", "drop_waitForGuiAfter", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        if (toggleStates.getOrDefault("drop_waitForGuiBefore", false)
                || toggleStates.getOrDefault("drop_waitForGuiAfter", false)) {
            AutismChatField guiF = textFields.get("drop_guiName");
            if (guiF != null) {
                int lw = labelWidth(w, "GUI Name", font);
                drawLabel(ctx, "GUI Name", x, cy, lw, font);
                guiF.setX(controlX(x, lw)); guiF.setY(cy + 2); guiF.setWidth(controlWidth(w, lw));
                guiF.render(ctx, mx, my, delta);
            }
            cy += ROW_H + ROW_GAP;
        } else {
            AutismChatField guiF = textFields.get("drop_guiName");
            if (guiF != null) guiF.setX(-1000);
        }

    }

    private void addDropEntry(ItemTarget target, int count) {
        if (target == null) return;
        String entry = target.toLegacyEntry();
        if (entry.isEmpty()) return;
        addTargetEntry(dropAction.itemTargets, dropAction.itemNames, target);
        int safeCount = Math.max(0, count);
        dropAction.itemCounts.add(safeCount);
        int idx = dropAction.itemNames.size() - 1;
        AutismChatField f = makeField(32);
        f.setNumericOnly(true);
        f.setText(String.valueOf(safeCount));
        final int fieldIndex = idx;
        f.setChangedListener(text -> {
            try {
                setDropEntryCount(fieldIndex, Math.max(0, Integer.parseInt(text.strip())));
                if (fieldIndex == dropEditIndex) syncDropCountEditorField();
            } catch (NumberFormatException ignored) {  }
        });
        textFields.put("drop_count_" + idx, f);
    }

    private void rebuildDropFields() {
        textFields.keySet().removeIf(k -> k.startsWith("drop_count_"));
        trimTargetEntries(dropAction.itemTargets, dropAction.itemNames.size());
        while (dropAction.itemCounts.size() > dropAction.itemNames.size())
            dropAction.itemCounts.remove(dropAction.itemCounts.size() - 1);
        for (int i = 0; i < dropAction.itemNames.size(); i++) {
            AutismChatField f = makeField(32);
            f.setNumericOnly(true);
            f.setText(String.valueOf(dropAction.itemCounts.get(i)));
            final int fieldIndex = i;
            f.setChangedListener(text -> {
                try {
                    setDropEntryCount(fieldIndex, Math.max(0, Integer.parseInt(text.strip())));
                    if (fieldIndex == dropEditIndex) syncDropCountEditorField();
                } catch (NumberFormatException ignored) {  }
            });
            textFields.put("drop_count_" + i, f);
        }
    }

    private void toggleDropEditSelection(int index) {
        if (dropEditIndex == index) {
            clearDropEditSelection();
            return;
        }
        dropEditIndex = index;
        syncDropEntryEditorFromSelection();
        syncDropCountEditorField();
    }

    private void clearDropEditSelection() {
        dropEditIndex = -1;
        AutismChatField addF = addFields.get("_drop_add");
        suppressDropEntryLiveUpdate = true;
        if (addF != null) addF.setText("");
        AutismChatField slotF = textFields.get("drop_entrySlot");
        if (slotF != null) slotF.setText("");
        suppressDropEntryLiveUpdate = false;
        syncDropCountEditorField();
    }

    private void syncDropEntryEditorFromSelection() {
        AutismChatField addF = addFields.get("_drop_add");
        AutismChatField slotF = textFields.get("drop_entrySlot");
        if (addF == null || slotF == null) return;
        suppressDropEntryLiveUpdate = true;
        if (dropEditIndex < 0 || dropEditIndex >= dropAction.itemNames.size()) {
            addF.setText("");
            slotF.setText("");
            suppressDropEntryLiveUpdate = false;
            return;
        }
        ItemTarget target = targetAt(dropAction.itemTargets, dropAction.itemNames, dropEditIndex);
        addF.setText(target.editorText());
        slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
        suppressDropEntryLiveUpdate = false;
        syncDropCountEditorField();
    }

    private void applyDropEntryEditor() {
        applyDropEntryEditor(false);
    }

    private void handleDropEntryEditorChanged() {
        if (suppressDropEntryLiveUpdate || dropAction == null) return;
        if (dropEditIndex < 0 || dropEditIndex >= dropAction.itemNames.size()) return;
        applyDropEntryEditor(true);
    }

    private void applyDropEntryEditor(boolean preserveSelection) {
        if (dropAction == null) return;
        AutismChatField addF = addFields.get("_drop_add");
        AutismChatField slotF = textFields.get("drop_entrySlot");
        if (addF == null || slotF == null) return;
        String nameText = addF.getText().strip();
        String slotText = slotF.getText().strip();
        ItemTarget target = buildEntryTargetFromEditor(nameText, slotText, targetAt(dropAction.itemTargets, dropAction.itemNames, dropEditIndex));
        String entry = target.toLegacyEntry();
        if (entry.isBlank()) return;

        if (dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size()) {
            if (!containsEntryOtherThan(dropAction.itemNames, entry, dropEditIndex)) {
                setTargetAt(dropAction.itemTargets, dropAction.itemNames, dropEditIndex, target);
            }
        } else if (!dropAction.itemNames.contains(entry)) {
            addDropEntry(target, currentDropEditorCount());
        }

        if (!preserveSelection) {
            clearDropEditSelection();
        }
    }

    private void applyCapturedDropEntry(ItemTarget rawTarget) {
        if (dropAction == null) return;

        ItemTarget target = storeCaptureTarget(rawTarget);
        String entry = target == null ? "" : target.toLegacyEntry();
        if (entry.isBlank()) return;

        int targetIndex = dropEditIndex;
        if (targetIndex >= 0 && targetIndex < dropAction.itemNames.size()) {
            if (!containsEntryOtherThan(dropAction.itemNames, entry, targetIndex)) {
                setTargetAt(dropAction.itemTargets, dropAction.itemNames, targetIndex, target.copy());
            }
        } else {
            targetIndex = dropAction.itemNames.indexOf(entry);
            if (targetIndex < 0) {
                addDropEntry(target.copy(), currentDropEditorCount());
                targetIndex = dropAction.itemNames.size() - 1;
            }
        }

        dropEditIndex = targetIndex;
        syncDropEntryEditorFromSelection();
        syncDropCountEditorField();
    }

    private int currentDropEditorCount() {
        AutismChatField globalCountField = textFields.get("drop_globalCount");
        if (dropEditIndex >= 0 && dropEditIndex < dropAction.itemCounts.size()) {
            return Math.max(0, dropAction.itemCounts.get(dropEditIndex));
        }
        if (enumIndices.getOrDefault("drop_mode", 0) == 0) {
            return 0;
        }
        if (globalCountField != null) {
            try {
                return Math.max(0, Integer.parseInt(globalCountField.getText().strip()));
            } catch (NumberFormatException ignored) {  }
        }
        return Math.max(0, dropAction.dropCount);
    }

    private int getDropEntryCount(int index) {
        if (dropAction == null || index < 0 || index >= dropAction.itemNames.size()) return 1;
        while (dropAction.itemCounts.size() <= index) dropAction.itemCounts.add(1);
        return Math.max(0, dropAction.itemCounts.get(index));
    }

    private void setDropEntryCount(int index, int count) {
        if (dropAction == null || index < 0 || index >= dropAction.itemNames.size()) return;
        while (dropAction.itemCounts.size() <= index) dropAction.itemCounts.add(1);
        int safeCount = Math.max(0, count);
        dropAction.itemCounts.set(index, safeCount);
        AutismChatField rowField = textFields.get("drop_count_" + index);
        if (rowField != null) rowField.setText(String.valueOf(safeCount));
    }

    private void syncDropCountEditorField() {
        if (dropAction == null) return;
        AutismChatField countField = textFields.get("drop_globalCount");
        if (countField == null) return;

        boolean editingSelectedDrop = dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size();
        boolean dropAll = editingSelectedDrop
                ? getDropEntryCount(dropEditIndex) == 0
                : dropAction.mode == autismclient.util.macro.DropAction.DropMode.ALL;
        int displayCount = editingSelectedDrop
                ? Math.max(1, getDropEntryCount(dropEditIndex))
                : Math.max(1, dropAction.dropCount);

        suppressDropCountEditorUpdate = true;
        countField.setText(String.valueOf(displayCount));
        countField.setEditable(!dropAll);
        suppressDropCountEditorUpdate = false;
    }

    private void handleDropCountEditorChanged() {
        if (suppressDropCountEditorUpdate || dropAction == null) return;
        AutismChatField countField = textFields.get("drop_globalCount");
        if (countField == null) return;

        boolean editingSelectedDrop = dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size();
        boolean dropAll = editingSelectedDrop
                ? getDropEntryCount(dropEditIndex) == 0
                : dropAction.mode == autismclient.util.macro.DropAction.DropMode.ALL;
        if (dropAll) return;

        try {
            int value = Math.max(1, Integer.parseInt(countField.getText().strip()));
            if (editingSelectedDrop) {
                setDropEntryCount(dropEditIndex, value);
            } else {
                dropAction.dropCount = value;
            }
        } catch (NumberFormatException ignored) {  }
    }

    private void renderLanStepPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        boolean filterByUser = toggleStates.getOrDefault("lan_filterByUser", false);

        renderInlineToggle(ctx, font, MacroAction.LISTEN_DURING_PREVIOUS_KEY,
                "Listen During Previous", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        renderInlineToggle(ctx, font, "lan_filterByUser", "Specific Peers", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        AutismChatField dsF = textFields.get("lan_defaultStep");
        if (dsF != null && (!filterByUser || lanStepEntries.isEmpty())) {
            int lw = labelWidth(w, filterByUser ? "Fallback Step" : "Any Peer Step", font);
            drawLabel(ctx, filterByUser ? "Fallback Step" : "Any Peer Step", x, cy, lw, font);
            dsF.setX(controlX(x, lw)); dsF.setY(cy + 2); dsF.setWidth(controlWidth(w, lw));
            dsF.render(ctx, mx, my, delta);
            cy += ROW_H + ROW_GAP;
        }

        String summary = !filterByUser
                ? "Continue when any LAN peer reaches the target step."
                : lanStepEntries.isEmpty()
                    ? "Add peers below to narrow it down. Until then, it waits for any peer at the fallback step."
                    : "Each peer below must reach its step before this continues.";
        cy = renderEditorHint(ctx, x, cy, w, summary);

        if (!filterByUser) return;

        if (!lanStepEntries.isEmpty()) {
            UiText.draw(ctx, textRenderer,
                    "Peers (" + lanStepEntries.size() + ")", font,
                    AutismColors.textSecondary(), x, cy + 2, false);
            cy += 13;
        }

        boolean hasEntries = !lanStepEntries.isEmpty();
        int visibleRows  = hasEntries ? Math.min(3, Math.max(1, lanStepEntries.size())) : 1;
        int selAreaH     = hasEntries ? visibleRows * SEL_ITEM_H : 12;

        DirectScrollViewport lanViewport = null;
        if (hasEntries) {
            lanViewport = getOrCreateViewport(selectedScrollViewports, "_lan_entries",
                x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
            lanViewport.setContentHeight(lanStepEntries.size() * SEL_ITEM_H);
        }

        if (hasEntries && lanViewport != null) {
            lanViewport.renderScrollbar(ctx, mx, my);
        }

        int sbGap   = SCROLLBAR_W + 2;
        int removeW = 13;
        int stepW   = 40;
        int gapPx   = 2;
        int removeX = x + w - sbGap - removeW;
        int stepX   = removeX - gapPx - stepW;
        int userW   = stepX - x - gapPx;

        if (hasEntries && lanViewport != null) {
            lanViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstVis = lanViewport.getFirstVisibleRow();
            for (int i = firstVis; i < lanStepEntries.size() && i <= lanViewport.getLastVisibleRow(); i++) {
                int iy = lanViewport.getRowScreenY(i);
                if (iy == Integer.MIN_VALUE) continue;

                CompactSurfaces.valueField(ctx, x, iy, stepX - gapPx - x, 13);

                AutismChatField uf = textFields.get("lan_user_" + i);
                if (uf != null) { uf.setX(x); uf.setY(iy + 1); uf.setWidth(userW); uf.render(ctx, mx, my, delta); }

                AutismChatField sf = textFields.get("lan_step_" + i);
                if (sf != null) { sf.setX(stepX); sf.setY(iy + 1); sf.setWidth(stepW); sf.render(ctx, mx, my, delta); }

                final int fi = i;
                renderIconDeleteButton(ctx, removeX, iy + 1, removeW, mx, my, () -> {
                    lanStepEntries.remove(fi);
                    rebuildLanStepFields();
                    DirectScrollViewport vp = selectedScrollViewports.get("_lan_entries");
                    if (vp != null) vp.scrollBy(-1);
                });
            }
            lanViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No peer filters yet.", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        int addBtnW = 36;
        AutismChatField newUF = addFields.get("_lan_user_add");
        AutismChatField newSF = addFields.get("_lan_step_add");
        if (newUF != null && newSF != null) {
            int plusX  = x + w - addBtnW;
            int stepAX = plusX - 3 - stepW;
            int userAW = stepAX - x - 2;
            newUF.setX(x); newUF.setY(cy + 1); newUF.setWidth(userAW);
            newUF.render(ctx, mx, my, delta);
            newSF.setX(stepAX); newSF.setY(cy + 1); newSF.setWidth(stepW);
            newSF.render(ctx, mx, my, delta);

            renderOverlayButton(ctx, plusX, cy, addBtnW, 14, "+Add",
                    CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                String uname = newUF.getText().strip();
                int step = 1;
                try { step = Math.max(1, Integer.parseInt(newSF.getText().strip())); } catch (NumberFormatException ignored) {  }
                lanStepEntries.add(new autismclient.util.macro.WaitForLanStepAction.LanStepEntry(uname, step));
                int idx = lanStepEntries.size() - 1;
                AutismChatField uf2 = makeField(80); uf2.setText(uname);
                textFields.put("lan_user_" + idx, uf2);
                AutismChatField sf2 = makeField(40); sf2.setNumericOnly(true); sf2.setText(String.valueOf(step));
                textFields.put("lan_step_" + idx, sf2);
                newUF.setText(""); newSF.setText("1");
            });
        }
    }

    private void rebuildLanStepFields() {
        textFields.keySet().removeIf(k -> k.startsWith("lan_user_") || k.startsWith("lan_step_"));
        for (int i = 0; i < lanStepEntries.size(); i++) {
            autismclient.util.macro.WaitForLanStepAction.LanStepEntry e = lanStepEntries.get(i);
            AutismChatField uf = makeField(80); uf.setText(e.username);
            textFields.put("lan_user_" + i, uf);
            AutismChatField sf = makeField(40); sf.setNumericOnly(true); sf.setText(String.valueOf(e.step));
            textFields.put("lan_step_" + i, sf);
        }
    }

    private void renderPacketActionButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int btnH = 14;
        int halfW = (w - 4) / 2;
        int topY = y + 2;
        int bottomY = y + 20;

        String info = buildPacketActionInfo();
        UiText.draw(ctx, textRenderer,
                UiText.trimToWidth(textRenderer, info, w, font, -1),
                font, AutismColors.textDim(), x, y - 10, false);

        renderActionButton(ctx, x, topY, halfW, btnH, "Queue First", mx, my, () -> {
            List<AutismSharedState.QueuedPacket> queue = AutismSharedState.get().getDelayedPackets();
            if (queue == null || queue.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Queue is empty");
                return;
            }
            setRawPacketActionData(queue.get(0));
            AutismClientMessaging.sendPrefixed("Loaded first queued packet");
        });
        renderActionButton(ctx, x + halfW + 4, topY, halfW, btnH, "Paste Base64", mx, my, () -> {
            List<AutismSharedState.QueuedPacket> pasted = AutismClipboardHelper.pasteFromClipboard();
            if (pasted == null || pasted.isEmpty()) {
                AutismClientMessaging.sendPrefixed("Failed to paste packet data");
                return;
            }
            setRawPacketActionData(pasted.get(0));
            AutismClientMessaging.sendPrefixed("Loaded first pasted packet");
        });
        renderActionButton(ctx, x, bottomY, w, btnH, "Clear Raw Packet", mx, my, () -> {
            workingTag.putString("packetData", "");
            AutismClientMessaging.sendPrefixed("Cleared raw packet data");
        });
    }

    private void renderWaitPacketPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        int halfW = (w - 4) / 2;
        List<String> c2sTargets = getWaitPacketTargets("C2S");
        List<String> s2cTargets = getWaitPacketTargets("S2C");

        String summary = c2sTargets.isEmpty() && s2cTargets.isEmpty()
            ? "No packets selected. This step will continue on the next packet in either direction."
            : "This step continues as soon as any selected C2S or S2C packet arrives.";
        cy = renderEditorHint(ctx, x, cy, w, summary);

        renderInlineToggle(ctx, font, MacroAction.LISTEN_DURING_PREVIOUS_KEY, "Listen During Previous", x, cy, w, mx, my);
        cy += 18;
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);

        renderActionButton(ctx, x, cy, halfW, 14, "Add C2S", mx, my, () -> openWaitPacketSelector(true));
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Add S2C", mx, my, () -> openWaitPacketSelector(false));
        cy += 18;

        renderActionButton(ctx, x, cy, halfW, 14, "Load Queue", mx, my, this::loadWaitPacketTargetsFromQueue);
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Clear All", mx, my, () -> {
            getOrCreateWaitPacketTargets().clear();
            DirectScrollViewport vpC2s = selectedScrollViewports.get("wait_packet_c2s");
            if (vpC2s != null) vpC2s.jumpTo(0);
            DirectScrollViewport vpS2c = selectedScrollViewports.get("wait_packet_s2c");
            if (vpS2c != null) vpS2c.jumpTo(0);
        });
        cy += 20;

        cy = renderSimpleSelectedList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "wait_packet_c2s",
            "C2S Packets",
            c2sTargets,
            this::removeWaitPacketTarget,
            this::formatWaitPacketTarget,
            ignored -> null,
            () -> clearWaitPacketTargets("C2S"),
            "No C2S packets selected"
        );
        cy += 4;

        renderSimpleSelectedList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "wait_packet_s2c",
            "S2C Packets",
            s2cTargets,
            this::removeWaitPacketTarget,
            this::formatWaitPacketTarget,
            ignored -> null,
            () -> clearWaitPacketTargets("S2C"),
            "No S2C packets selected"
        );
    }

    private void renderWaitPacketMatchPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        if (waitPacketMatchRules == null) waitPacketMatchRules = new ArrayList<>();
        if (waitPacketMatchEditIndex >= waitPacketMatchRules.size()) waitPacketMatchEditIndex = waitPacketMatchRules.isEmpty() ? -1 : 0;

        cy = renderEditorHint(ctx, x, cy, w, "Add a packet, then set field + rule.");
        renderInlineToggle(ctx, font, MacroAction.LISTEN_DURING_PREVIOUS_KEY, "Listen During Previous", x, cy, w, mx, my);
        cy += 18;
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);

        int halfW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, halfW, 14, "Add C2S", mx, my, () -> openWaitPacketMatchSelector(true));
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Add S2C", mx, my, () -> openWaitPacketMatchSelector(false));
        cy += 18;

        renderWaitPacketMatchRuleList(ctx, x, cy, w, mx, my);
        cy += 13 + Math.max(1, Math.min(4, Math.max(1, waitPacketMatchRules.size()))) * SEL_ITEM_H + 5;

        if (waitPacketMatchRules.isEmpty()) {
            renderEditorHint(ctx, x, cy, w, "No packet rules yet. Add C2S or S2C.");
            return;
        }

        final WaitPacketMatchAction.Rule rule = waitPacketMatchRules.get(Math.max(0, waitPacketMatchEditIndex));

        java.util.List<String> dirOpts = new ArrayList<>();
        for (WaitPacketMatchAction.Direction d : WaitPacketMatchAction.Direction.values()) dirOpts.add(d.name());
        cy = renderInlineEnumDropdown(ctx, font, "Direction", "_wpm_dir", dirOpts, rule.direction.ordinal(), x, cy, w,
                i -> rule.direction = WaitPacketMatchAction.Direction.values()[i]);

        final Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolveWaitPacketMatchClass(rule);
        final java.util.List<String> fieldNames = new ArrayList<>(WaitPacketMatchAction.packetFieldNames(packetClass));
        java.util.List<String> fieldDisplay = new ArrayList<>();
        fieldDisplay.add("Packet Only");
        fieldDisplay.addAll(fieldNames);
        int fieldIdx = (rule.fieldName == null || rule.fieldName.isBlank()) ? 0 : Math.max(0, fieldNames.indexOf(rule.fieldName) + 1);
        cy = renderInlineEnumDropdown(ctx, font, "Field", "_wpm_field", fieldDisplay, fieldIdx, x, cy, w, i -> {
            rule.fieldName = i <= 0 ? "" : fieldNames.get(i - 1);
            if (rule.fieldName.isBlank()) rule.operator = WaitPacketMatchAction.Operator.EXISTS;
            java.util.List<String> vo = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
            if (!vo.isEmpty() && (rule.value == null || rule.value.isBlank() || !vo.contains(rule.value))) rule.value = vo.get(0);
            syncWaitPacketMatchValueField();
        });

        if (rule.fieldName != null && !rule.fieldName.isBlank()) {
            java.util.List<String> opOpts = new ArrayList<>();
            for (WaitPacketMatchAction.Operator o : WaitPacketMatchAction.Operator.values()) opOpts.add(o.name());
            cy = renderInlineEnumDropdown(ctx, font, "Operator", "_wpm_op", opOpts, rule.operator.ordinal(), x, cy, w,
                    i -> rule.operator = WaitPacketMatchAction.Operator.values()[i]);

            if (rule.operator != WaitPacketMatchAction.Operator.EXISTS) {
                java.util.List<String> valueOptions = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
                if (!valueOptions.isEmpty()) {
                    int valIdx = Math.max(0, valueOptions.indexOf(rule.value));
                    cy = renderInlineEnumDropdown(ctx, font, "Value", "_wpm_value_dd", valueOptions, valIdx, x, cy, w, i -> {
                        rule.value = valueOptions.get(i);
                        syncWaitPacketMatchValueField();
                    });
                } else {
                    AutismChatField valueField = textFields.get("_wpm_value");
                    if (valueField != null) {
                        int lw = labelWidth(w, "Value", font, 72);
                        drawLabel(ctx, "Value", x, cy, lw, font);
                        valueField.setX(controlX(x, lw));
                        valueField.setY(cy + 1);
                        valueField.setWidth(controlWidth(w, lw));
                        valueField.render(ctx, mx, my, delta);
                        cy += ROW_H + ROW_GAP;
                    }
                }
            }
        }
    }

    private int renderInlineEnumDropdown(GuiGraphicsExtractor ctx, Identifier font, String label, String cacheKey,
                                         java.util.List<String> options, int selectedIdx, int x, int cy, int w,
                                         java.util.function.IntConsumer onSelect) {
        if (options.isEmpty()) return cy;
        int lw = labelWidth(w, label, font, 72);
        drawLabel(ctx, label, x, cy, lw, font);
        int ctrlX = controlX(x, lw);
        int ctrlW = controlWidth(w, lw);
        int idx = Math.max(0, Math.min(selectedIdx, options.size() - 1));
        final java.util.List<String> opts = options;
        final java.util.function.IntConsumer cb = onSelect;
        CompactDropdown dd = enumDropdownCache.computeIfAbsent(cacheKey, k ->
                new CompactDropdown(ctrlX, cy + 1, ctrlW, 16, opts, idx, cb));
        dd.setBounds(ctrlX, cy + 1, ctrlW, 16).setOptions(opts).setSelectedIndex(idx).setOnSelect(cb);
        enumDropdowns.add(dd);
        return cy + ROW_H + ROW_GAP;
    }

    private void renderInlineDropdown(String cacheKey, java.util.List<String> options, int selectedIdx,
                                      int x, int y, int w, int h, java.util.function.IntConsumer onSelect) {
        if (options.isEmpty()) return;
        int idx = Math.max(0, Math.min(selectedIdx, options.size() - 1));
        final java.util.List<String> opts = options;
        final java.util.function.IntConsumer cb = onSelect;
        CompactDropdown dd = enumDropdownCache.computeIfAbsent(cacheKey, k -> new CompactDropdown(x, y, w, h, opts, idx, cb));
        dd.setBounds(x, y, w, h).setOptions(opts).setSelectedIndex(idx).setOnSelect(cb);
        enumDropdowns.add(dd);
    }

    private void renderWaitPacketMatchRuleList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, "Packet Rules (" + waitPacketMatchRules.size() + ")", font, AutismColors.textSecondary(), x, y + 2, false);
        renderOverlayButton(ctx, x + w - 44, y, 44, 14, "Clear", CompactOverlayButton.Variant.DANGER, !waitPacketMatchRules.isEmpty(), mx, my, () -> {
            waitPacketMatchRules.clear();
            waitPacketMatchEditIndex = -1;
        });
        y += 14;

        int rows = Math.max(1, Math.min(4, Math.max(1, waitPacketMatchRules.size())));
        int listH = rows * SEL_ITEM_H;
        int delW = 13;
        if (waitPacketMatchRules.isEmpty()) {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No packet rules selected.", x, y, w - SCROLLBAR_W - 1);
            return;
        }

        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, "_wpm_rules",
            x, y, w, listH, SEL_ITEM_H, SCROLLBAR_W);
        viewport.setContentHeight(waitPacketMatchRules.size() * SEL_ITEM_H);
        viewport.renderScrollbar(ctx, mx, my);
        int rowW = w - SCROLLBAR_W - 1 - delW - 2;
        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        for (int i = viewport.getFirstVisibleRow(); i < waitPacketMatchRules.size() && i <= viewport.getLastVisibleRow(); i++) {
            int iy = viewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            WaitPacketMatchAction.Rule rule = waitPacketMatchRules.get(i);
            boolean selected = i == waitPacketMatchEditIndex;
            boolean hovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
            MacroTypedListControl.renderRow(ctx, textRenderer, formatWaitPacketMatchRule(rule),
                    UiBounds.of(x, iy, rowW, 13), hovered, selected, CompactListRenderer.RowTone.NORMAL, true);
            final int fi = i;
            hitRegions.add(new HitRegion(x, iy, rowW, 13, () -> {
                syncWaitPacketMatchEditedRule();
                waitPacketMatchEditIndex = fi;
                syncWaitPacketMatchValueField();
            }));
            renderIconDeleteButton(ctx, x + rowW + 2, iy, delW, mx, my, () -> {
                waitPacketMatchRules.remove(fi);
                if (waitPacketMatchEditIndex >= waitPacketMatchRules.size()) waitPacketMatchEditIndex = waitPacketMatchRules.size() - 1;
                syncWaitPacketMatchValueField();
            });
        }
        viewport.endRender(ctx);
    }

    private void renderSendPacketPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        List<AutismSharedState.QueuedPacket> actionPackets = getWorkingQueuedPackets();
        List<AutismSharedState.QueuedPacket> queuePackets = AutismSharedState.get().getDelayedPackets();
        if (queuePackets == null) queuePackets = Collections.emptyList();
        final List<AutismSharedState.QueuedPacket> finalQueuePackets = queuePackets;

        UiText.draw(ctx, textRenderer,
                "Action Packets (" + actionPackets.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        renderOverlayButton(ctx, x + w - 44, cy, 44, 14, "Clear",
                CompactOverlayButton.Variant.DANGER, !actionPackets.isEmpty(), mx, my, () -> {
                    setWorkingQueuedPackets(Collections.emptyList());
                });
        cy += 14;

        cy = renderQueuedPacketList(ctx, x, cy, w, mx, my, delta,
                "_send_packet_action", actionPackets, true, packetIndex -> {
                    List<AutismSharedState.QueuedPacket> updated = getWorkingQueuedPackets();
                    if (packetIndex < 0 || packetIndex >= updated.size()) return;
                    updated.remove(packetIndex);
                    setWorkingQueuedPackets(updated);
                });

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        AutismChatField nameField = textFields.get("customName");
        if (nameField != null) {
            int lw = labelWidth(w, "Name", font);
            drawLabel(ctx, "Name", x, cy, lw, font);
            nameField.setX(controlX(x, lw)); nameField.setY(cy + 2); nameField.setWidth(controlWidth(w, lw));
            nameField.render(ctx, mx, my, delta);
            cy += ROW_H + ROW_GAP;
        }

        renderGuiWaitRow(ctx, font, "waitForGuiBefore", "waitForGuiAfter", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;

        if (toggleStates.getOrDefault("waitForGuiBefore", false)
                || toggleStates.getOrDefault("waitForGuiAfter", false)) {
            AutismChatField guiField = textFields.get("guiName");
            if (guiField != null) {
                int lw = labelWidth(w, "GUI Name", font);
                drawLabel(ctx, "GUI Name", x, cy, lw, font);
                guiField.setX(controlX(x, lw)); guiField.setY(cy + 2); guiField.setWidth(controlWidth(w, lw));
                guiField.render(ctx, mx, my, delta);
                cy += ROW_H + ROW_GAP;
            }
        } else {
            AutismChatField guiField = textFields.get("guiName");
            if (guiField != null) guiField.setX(-1000);
        }

        cy += 2;
        int halfW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, halfW, 14, "From Queue", mx, my, () -> {
            setWorkingQueuedPackets(finalQueuePackets);
            AutismClientMessaging.sendPrefixed("Loaded " + finalQueuePackets.size() + " packets from queue");
        });
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Paste Base64", mx, my, () -> {
            List<AutismSharedState.QueuedPacket> pasted = AutismClipboardHelper.pasteFromClipboard();
            if (pasted == null || pasted.isEmpty()) {
                AutismNotifications.error("Clipboard does not contain packets.");
                return;
            }
            setWorkingQueuedPackets(pasted);
            AutismClientMessaging.sendPrefixed("Pasted " + pasted.size() + " packets");
        });
        cy += 18;
        renderActionButton(ctx, x, cy, halfW, 14, "Clear", mx, my, () -> {
            setWorkingQueuedPackets(Collections.emptyList());
            AutismClientMessaging.sendPrefixed("Cleared packet list");
        });
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "GBreak", mx, my, this::startGBreakCaptureForEditor);
        cy += 20;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        UiText.draw(ctx, textRenderer,
                "Current Queue (" + finalQueuePackets.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        cy += 14;

        renderQueuedPacketList(ctx, x, cy, w, mx, my, delta,
                "_send_packet_queue", finalQueuePackets, false, packetIndex -> {
                    if (packetIndex < 0 || packetIndex >= finalQueuePackets.size()) return;
                    List<AutismSharedState.QueuedPacket> updated = getWorkingQueuedPackets();
                    updated.add(finalQueuePackets.get(packetIndex));
                    setWorkingQueuedPackets(updated);
                });
    }

    private int renderQueuedPacketList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta,
                                       String listKey, List<AutismSharedState.QueuedPacket> packets,
                                       boolean removable, java.util.function.IntConsumer rowAction) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int listH = SEL_LIST_MAX_VIS * SEL_ITEM_H;

        DirectScrollViewport packetViewport = getOrCreateViewport(selectedScrollViewports, listKey,
            x, y, w, listH, SEL_ITEM_H, SCROLLBAR_W);
        packetViewport.setContentHeight(packets.size() * SEL_ITEM_H);

        packetViewport.renderScrollbar(ctx, mx, my);

        int removeW = removable ? 13 : 0;
        int textW = w - SCROLLBAR_W - 2 - (removable ? (removeW + 2) : 0);
        if (packets.isEmpty()) {
            UiText.draw(ctx, textRenderer, removable ? "(none - use GBreak, queue, or Paste)" : "(queue empty)",
                    font, AutismColors.textDim(), x, y + 2, false);
            return y + listH;
        }

        packetViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int firstVis = packetViewport.getFirstVisibleRow();
        for (int i = firstVis; i < packets.size() && i <= packetViewport.getLastVisibleRow(); i++) {
            int iy = packetViewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            AutismSharedState.QueuedPacket qp = packets.get(i);
            String pname = qp != null && qp.packet != null ? AutismPacketNamer.getFriendlyName(qp.packet) : "???";
            String rowText = (i + 1) + ". " + pname + " d=" + (qp != null ? qp.getDelay() : 0);
            boolean hovered = mx >= x && mx < x + textW && my >= iy && my < iy + 13;
            MacroTypedListControl.renderRow(
                    ctx,
                    textRenderer,
                    Component.literal(rowText),
                    UiBounds.of(x, iy, textW, 13),
                    hovered,
                    false,
                    CompactListRenderer.RowTone.NORMAL,
                    false
            );
            final int rowIndex = i;
            if (!removable) {
                hitRegions.add(new HitRegion(x, iy, textW, 13, () -> rowAction.accept(rowIndex)));
            }

            if (removable) {
                int removeX = x + textW + 2;
                renderIconDeleteButton(ctx, removeX, iy, removeW, mx, my, () -> rowAction.accept(rowIndex));
            }
        }
        packetViewport.endRender(ctx);
        return y + listH;
    }

    private void renderSendPacketButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int btnH = 14;
        int halfW = (w - 4) / 2;
        int topY = y + 2;
        int bottomY = y + 20;

        String info = buildSendPacketInfo();
        UiText.draw(ctx, textRenderer,
                UiText.trimToWidth(textRenderer, info, w, font, -1),
                font, AutismColors.textDim(), x, y - 10, false);

        renderActionButton(ctx, x, topY, halfW, btnH, "From Queue", mx, my, () -> {
            List<AutismSharedState.QueuedPacket> queue = AutismSharedState.get().getDelayedPackets();
            setWorkingQueuedPackets(queue);
            AutismClientMessaging.sendPrefixed("Loaded " + getWorkingQueuedPackets().size() + " packets from queue");
        });
        renderActionButton(ctx, x + halfW + 4, topY, halfW, btnH, "Paste Base64", mx, my, () -> {
            List<AutismSharedState.QueuedPacket> pasted = AutismClipboardHelper.pasteFromClipboard();
            if (pasted == null || pasted.isEmpty()) {
                AutismNotifications.error("Clipboard does not contain packets.");
                return;
            }
            setWorkingQueuedPackets(pasted);
            AutismClientMessaging.sendPrefixed("Pasted " + pasted.size() + " packets");
        });
        renderActionButton(ctx, x, bottomY, halfW, btnH, "Clear", mx, my, () -> {
            setWorkingQueuedPackets(Collections.emptyList());
            AutismClientMessaging.sendPrefixed("Cleared packet list");
        });
        renderOverlayButton(ctx, x + halfW + 4, bottomY, halfW, btnH, configurationOnly ? "Capture: In Game" : "GBreak",
            CompactOverlayButton.Variant.SECONDARY, !configurationOnly, mx, my, () -> {
            if (configurationOnly) return;
            AutismSharedState.get().startGBreakCapture(() -> MC.execute(() -> {
                List<AutismSharedState.QueuedPacket> captured = AutismSharedState.get().getGBreakCapturedPackets();
                setWorkingQueuedPackets(captured);
                AutismClientMessaging.sendPrefixed(captured.isEmpty()
                        ? "GBreak capture finished with no packet"
                        : "GBreak packet captured");
            }));
            AutismClientMessaging.sendPrefixed("Break a block now to capture the GBreak packet");
        });
    }

    private record PayloadEditorState(String channel, byte[] rawBytes, boolean commandApiRecognized,
                                      int commandApiValue, PayloadContentMode contentMode, String contentText) {
        private PayloadEditorState {
            rawBytes = rawBytes == null ? new byte[0] : rawBytes.clone();
            contentMode = contentMode == null ? PayloadContentMode.BINARY_REPLAY : contentMode;
            contentText = contentText == null ? "" : contentText;
        }

        @Override
        public byte[] rawBytes() {
            return rawBytes.clone();
        }

    }

    private void initializePayloadEditorFields(PayloadAction seedAction) {
        payloadEditorModel = AutismPayloadEditorModel.fromAction(seedAction);
        pendingPayloadEdit = null;
        payloadPreviewSyncedRevision = Long.MIN_VALUE;
        payloadBuildCache = null;
        PayloadEditorState state = resolvePayloadEditorState(seedAction);
        payloadContentMode = state.contentMode();
        payloadContentEdited = false;
        payloadRawEdited = false;
        payloadChannelEdited = false;
        payloadJsonEdited = false;
        payloadModeManuallyChanged = false;
        payloadAddTypeManuallyChanged = false;
        suppressPayloadEditorChange = false;

        AutismChatField channelField = makeField(120);
        channelField.setText(state.channel());
        channelField.setPlaceholder(Component.literal("namespace:channel"));
        channelField.setChangedListener(text -> {
            if (!suppressPayloadEditorChange) {
                payloadChannelEdited = true;
                if (payloadEditorModel != null) {
                    payloadEditorModel.channel = text == null || text.isBlank() ? "minecraft:brand" : text.strip();
                    payloadEditorModel.touch();
                    queuePayloadEdit(PayloadEditKind.PREVIEW_ONLY, "");
                }
                autoSelectPayloadControls();
            }
        });
        textFields.put("payload_channel", channelField);

        if (seedAction != null) {
            seedAction.payloadPhase = payloadEditorModel.phase;
        }
        AutismChatField contentField = makeField(PAYLOAD_SCRIPT_H);
        contentField.setMultiline(true);
        contentField.setEnterInsertsNewline(true);
        contentField.setSpaceKeyInsertsSpace(true);
        contentField.setHoverEffectsEnabled(false);
        contentField.setFocusEffectsEnabled(false);
        contentField.setBackgroundColorOverride(0x520A090C);
        contentField.setMaxLength(32767);
        contentField.setPlaceholder(Component.literal("direction = C2S\nphase = PLAY\nchannel = minecraft:brand\n\nwriteMcString = vanilla"));
        contentField.setDisplayTextProvider(this::payloadScriptComponent);
        contentField.setDisplayTextRevisionProvider(this::payloadEditorRevision);
        contentField.setChangedListener(text -> {
            if (!suppressPayloadEditorChange) {
                payloadContentEdited = true;
                queuePayloadEdit(PayloadEditKind.SCRIPT, text);
            }
        });
        textFields.put("payload_content", contentField);

        AutismChatField hexView = makeField(PAYLOAD_VIEW_H);
        hexView.setMultiline(true);
        hexView.setEnterInsertsNewline(true);
        hexView.setSpaceKeyInsertsSpace(true);
        hexView.setHoverEffectsEnabled(false);
        hexView.setFocusEffectsEnabled(false);
        hexView.setBackgroundColorOverride(0x520A090C);
        hexView.setEditable(true);
        hexView.setMaxLength(32767);
        hexView.setPlaceholder(Component.literal("00 01 02 FF"));
        hexView.setDisplayTextProvider(this::payloadHexComponent);
        hexView.setDisplayTextRevisionProvider(this::payloadEditorRevision);
        hexView.setChangedListener(text -> {
            if (!suppressPayloadEditorChange) queuePayloadEdit(PayloadEditKind.HEX, text);
        });
        textFields.put("payload_hex_view", hexView);

        AutismChatField utf8View = makeField(PAYLOAD_VIEW_H);
        utf8View.setMultiline(true);
        utf8View.setEnterInsertsNewline(true);
        utf8View.setSpaceKeyInsertsSpace(true);
        utf8View.setHoverEffectsEnabled(false);
        utf8View.setFocusEffectsEnabled(false);
        utf8View.setBackgroundColorOverride(0x520A090C);
        utf8View.setEditable(true);
        utf8View.setMaxLength(32767);
        utf8View.setPlaceholder(Component.literal("Raw UTF-8 text"));
        utf8View.setDisplayTextProvider(this::payloadUtf8Component);
        utf8View.setDisplayTextRevisionProvider(this::payloadEditorRevision);
        utf8View.setChangedListener(text -> {
            if (!suppressPayloadEditorChange) queuePayloadEdit(PayloadEditKind.UTF8, text);
        });
        textFields.put("payload_utf8_view", utf8View);

        AutismChatField logicalView = makeField(PAYLOAD_VIEW_H);
        logicalView.setMultiline(true);
        logicalView.setEnterInsertsNewline(true);
        logicalView.setSpaceKeyInsertsSpace(true);
        logicalView.setHoverEffectsEnabled(false);
        logicalView.setFocusEffectsEnabled(false);
        logicalView.setBackgroundColorOverride(0x520A090C);
        logicalView.setEditable(true);
        logicalView.setMaxLength(32767);
        logicalView.setPlaceholder(Component.literal("direction = C2S\nphase = PLAY\nchannel = minecraft:brand\nbodyHex = ..."));
        logicalView.setDisplayTextProvider(this::payloadLogicalComponent);
        logicalView.setDisplayTextRevisionProvider(this::payloadEditorRevision);
        logicalView.setChangedListener(text -> {
            if (!suppressPayloadEditorChange) queuePayloadEdit(PayloadEditKind.LOGICAL, text);
        });
        textFields.put("payload_logical_view", logicalView);

        AutismChatField commandField = makeField(66);
        commandField.setNumericOnly(true);
        commandField.setText(String.valueOf(payloadEditorModel.commandApiValue));
        commandField.setPlaceholder(Component.literal("value"));
        commandField.setChangedListener(text -> {
            if (!suppressPayloadEditorChange && payloadEditorModel != null) {
                try {
                    payloadEditorModel.commandApiValue = Integer.parseInt((text == null ? "" : text).strip());
                    payloadEditorModel.commandApiRecognized = true;
                    payloadEditorModel.commandApiOverride = true;
                    payloadEditorModel.touch();
                    payloadBuildCache = null;
                    toggleStates.put("payload_command_api", true);
                    payloadContentEdited = true;
                } catch (NumberFormatException ignored) {  }
            }
        });
        textFields.put("payload_command_value", commandField);
        toggleStates.put("payload_command_api", payloadEditorModel.commandApiOverride);

        syncPayloadAllFieldsFromModel(true);
        AutismPayloadTemplate.Template template = currentPayloadTemplateFromModel();
        AutismPayloadTemplate.EncodingMode suggestedMode = suggestPayloadMode(template.channel(), template.fields(), template.mode());
        enumIndices.put("payload_mode", payloadModeIndex(suggestedMode));
        enumIndices.put("payload_add_type", payloadTypeIndex(suggestPayloadAddType(template.channel(), suggestedMode, template.fields())));
    }

    private PayloadEditorState resolvePayloadEditorState(PayloadAction seedAction) {
        PayloadAction action = new PayloadAction();
        if (seedAction != null) {
            action.fromTag(seedAction.toTag());
        }

        String channel = action.channel == null ? "" : action.channel.strip();
        byte[] bytes = new byte[0];
        boolean haveBytes = false;

        if (action.payloadData != null && !action.payloadData.isBlank()) {
            try {
                bytes = AutismPayloadSupport.parsePayloadBytes(action.payloadData);
                haveBytes = true;
            } catch (Exception ignored) {
                bytes = new byte[0];
            }
        }

        if (action.payloadJson != null && !action.payloadJson.isBlank()) {
            try {
                action.payloadJson = AutismPayloadJsonSupport.normalizeJson(action.payloadJson);
                AutismPayloadJsonSupport.EncodedPayload encoded = AutismPayloadJsonSupport.encodeAction(action);
                if (channel.isBlank() && encoded.channel() != null && !encoded.channel().isBlank()) {
                    channel = encoded.channel();
                }
                if (!haveBytes) {
                    bytes = encoded.bytes();
                    haveBytes = bytes.length > 0 || action.payloadData == null || action.payloadData.isBlank();
                }
            } catch (Exception ignored) {  }
        }

        if (channel.isBlank()) {
            channel = "minecraft:brand";
        }

        if (AutismPayloadSupport.isBrandChannel(channel)) {
            String brandText = AutismPayloadSupport.decodeMinecraftStringPayload(bytes);
            if (brandText != null || bytes.length == 0) {
                return new PayloadEditorState(channel, bytes, false, action.commandApiValue,
                    PayloadContentMode.BRAND_STRING,
                    brandText != null ? brandText : AutismPayloadSupport.defaultBrandPayloadString());
            }
        }

        Integer commandValue = AutismPayloadSupport.tryParseCommandApiValue(null, channel, bytes);
        boolean commandRecognized = action.commandApiRecognized || commandValue != null;
        int resolvedCommandValue = commandValue != null ? commandValue : action.commandApiValue;
        PayloadContentMode mode = PayloadContentMode.BINARY_REPLAY;
        String contentText = "";
        if (commandRecognized) {
            mode = PayloadContentMode.COMMAND_INT;
            contentText = String.valueOf(resolvedCommandValue);
        } else {
            String readableText = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
            if (!readableText.isBlank() || bytes.length == 0) {
                mode = PayloadContentMode.UTF8_TEXT;
                contentText = prettifyPayloadText(readableText);
            } else {
                contentText = "Binary payload (" + bytes.length + " bytes). Safe replay keeps the captured bytes.";
            }
        }
        return new PayloadEditorState(channel, bytes, commandRecognized, resolvedCommandValue, mode, contentText);
    }

    private String prettifyPayloadText(String text) {
        if (text == null || text.isBlank()) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String stripped = normalized.strip();
        if (!(stripped.startsWith("{") || stripped.startsWith("["))) {
            return normalized;
        }
        try {
            return PAYLOAD_TEXT_GSON.toJson(JsonParser.parseString(stripped));
        } catch (Throwable ignored) {
            return normalized;
        }
    }

    private void formatPayloadJsonField(boolean syncPreview) {
        AutismChatField jsonField = textFields.get("payload_json");
        if (jsonField == null) return;
        jsonField.setText(AutismPayloadJsonSupport.normalizeJson(jsonField.getText()));
        if (syncPreview) {
            syncPayloadPreviewFromJson(true);
        }
    }

    private void syncPayloadPreviewFromJson(boolean notifyOnError) {
        AutismChatField jsonField = textFields.get("payload_json");
        if (jsonField == null) return;

        PayloadAction preview = new PayloadAction();
        if (payloadAction != null) {
            preview.fromTag(payloadAction.toTag());
        }

        AutismChatField channelField = textFields.get("payload_channel");
        AutismChatField rawField = textFields.get("payload_data");
        AutismChatField textField = textFields.get("payload_text");
        AutismChatField commandField = textFields.get("payload_command_value");

        preview.payloadJson = jsonField.getText();
        preview.channel = channelField == null ? preview.channel : channelField.getText().strip();
        preview.payloadData = rawField == null ? preview.payloadData : rawField.getText();
        preview.commandApiRecognized = toggleStates.getOrDefault("payload_command_api", preview.commandApiRecognized);

        if (commandField != null) {
            try {
                preview.commandApiValue = Integer.parseInt(commandField.getText().strip());
            } catch (NumberFormatException ignored) {  }
        }

        try {
            preview.payloadJson = AutismPayloadJsonSupport.normalizeJson(preview.payloadJson);
            AutismPayloadJsonSupport.EncodedPayload encoded = AutismPayloadJsonSupport.encodeAction(preview);
            suppressPayloadEditorChange = true;
            try {
                jsonField.setText(preview.payloadJson);
                if (channelField != null) {
                    channelField.setText(encoded.channel());
                }
                if (rawField != null) {
                    rawField.setText(AutismPayloadSupport.toHex(encoded.bytes()));
                }
                if (textField != null) {
                    textField.setText(AutismPayloadSupport.decodeLikelyUtf8Text(encoded.bytes()));
                }
                payloadChannelEdited = true;
                payloadRawEdited = true;
                payloadJsonEdited = false;
            } finally {
                suppressPayloadEditorChange = false;
            }

            Integer commandValue = AutismPayloadSupport.tryParseCommandApiValue(null, encoded.channel(), encoded.bytes());
            if (commandValue != null) {
                toggleStates.put("payload_command_api", true);
                if (commandField != null) {
                    commandField.setText(String.valueOf(commandValue));
                }
            }
        } catch (Exception e) {
            if (notifyOnError) {
                AutismClientMessaging.sendPrefixed("§cCould not rebuild payload from JSON: " + AutismPayloadSupport.safeMessage(e));
            }
        }
    }

    private void setPayloadFieldsInEditor(List<AutismPayloadTemplate.Field> fields) {
        removePayloadRowFields();
        List<AutismPayloadTemplate.Field> safeFields = fields == null ? List.of() : fields;
        payloadFieldCount = safeFields.size();
        if (payloadEditorModel != null) {
            payloadEditorModel.bodyFields = new ArrayList<>(safeFields);
            payloadEditorModel.touch();
            payloadEditorModel.applyPacketScript(payloadEditorModel.packetScript());
            payloadBuildCache = null;
            syncPayloadAllFieldsFromModel(true);
            return;
        }
        AutismChatField contentField = textFields.get("payload_content");
        if (contentField != null) {
            contentField.setText(AutismPayloadTemplate.serializeFields(safeFields));
        }
    }

    private void removePayloadRowFields() {
        textFields.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_value_"));
        enumIndices.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_type_"));
        enumDropdownCache.entrySet().removeIf(entry -> entry.getKey().startsWith("payload_type_"));
        payloadFieldCount = 0;
    }

    private List<AutismPayloadTemplate.Field> payloadFieldsFromEditor() {
        if (payloadEditorModel != null) return payloadEditorModel.bodyFields == null ? List.of() : payloadEditorModel.bodyFields;
        AutismChatField contentField = textFields.get("payload_content");
        return AutismPayloadTemplate.parseFields(contentField == null ? "" : contentField.getText());
    }

    private void autoSelectPayloadControls() {
        if (suppressPayloadEditorChange) return;
        String channel = payloadEditorChannel();
        List<AutismPayloadTemplate.Field> fields = payloadFieldsFromEditor();
        if (!payloadModeManuallyChanged) {
            AutismPayloadTemplate.EncodingMode current = PAYLOAD_MODES.get(Math.max(0,
                Math.min(enumIndices.getOrDefault("payload_mode", payloadModeIndex(AutismPayloadTemplate.EncodingMode.ADVANCED_MIXED)), PAYLOAD_MODES.size() - 1)));
            enumIndices.put("payload_mode", payloadModeIndex(suggestPayloadMode(channel, fields, current)));
        }
        if (!payloadAddTypeManuallyChanged) {
            AutismPayloadTemplate.EncodingMode mode = payloadModeFromEditor(channel);
            enumIndices.put("payload_add_type", payloadTypeIndex(suggestPayloadAddType(channel, mode, fields)));
        }
    }

    private String payloadEditorChannel() {
        if (payloadEditorModel != null && payloadEditorModel.channel != null && !payloadEditorModel.channel.isBlank()) {
            return payloadEditorModel.channel.strip();
        }
        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null && !channelField.getText().isBlank()) return channelField.getText().strip();
        return payloadAction != null && payloadAction.channel != null && !payloadAction.channel.isBlank()
            ? payloadAction.channel.strip()
            : "minecraft:brand";
    }

    private AutismPayloadTemplate.EncodingMode suggestPayloadMode(String channel, List<AutismPayloadTemplate.Field> fields,
                                                                 AutismPayloadTemplate.EncodingMode fallback) {
        List<AutismPayloadTemplate.Field> safeFields = fields == null ? List.of() : fields;
        if (AutismPayloadSupport.isBrandChannel(channel)) return AutismPayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF;
        if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip())) return AutismPayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT;
        for (AutismPayloadTemplate.Field field : safeFields) {
            if (field == null) continue;
            switch (field.type()) {
                case HEX_BYTES, RAW_BYTES, BYTE_ARRAY -> {
                    return AutismPayloadTemplate.EncodingMode.MANUAL_HEX;
                }
                case JAVA_WRITE_UTF, BYTE, UNSIGNED_BYTE, BOOLEAN, SHORT, UNSIGNED_SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> {
                    return AutismPayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT;
                }
                case VAR_INT, VAR_LONG, MINECRAFT_STRING, IDENTIFIER, UUID_FIELD, BLOCK_POS, ENUM_VAR_INT, OPTIONAL_VALUE -> {
                    return AutismPayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF;
                }
                case JSON_STRING, TEXT_COMPONENT, NBT, ITEM_STACK -> {
                    return AutismPayloadTemplate.EncodingMode.JSON_TEXT;
                }
                case STRING_BYTES, RAW_UTF8_STRING -> {
                    return AutismPayloadTemplate.EncodingMode.RAW_UTF8;
                }
            }
        }
        return fallback == null ? AutismPayloadTemplate.EncodingMode.ADVANCED_MIXED : fallback;
    }

    private AutismPayloadTemplate.FieldType suggestPayloadAddType(String channel, AutismPayloadTemplate.EncodingMode mode,
                                                                 List<AutismPayloadTemplate.Field> fields) {
        if (AutismPayloadSupport.isBrandChannel(channel)) return AutismPayloadTemplate.FieldType.MINECRAFT_STRING;
        if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip())) return AutismPayloadTemplate.FieldType.JAVA_WRITE_UTF;
        if (mode == AutismPayloadTemplate.EncodingMode.MANUAL_HEX) return AutismPayloadTemplate.FieldType.HEX_BYTES;
        if (mode == AutismPayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT) return AutismPayloadTemplate.FieldType.JAVA_WRITE_UTF;
        if (mode == AutismPayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF) return AutismPayloadTemplate.FieldType.MINECRAFT_STRING;
        if (mode == AutismPayloadTemplate.EncodingMode.JSON_TEXT) return AutismPayloadTemplate.FieldType.TEXT_COMPONENT;
        return AutismPayloadTemplate.FieldType.STRING_BYTES;
    }

    private void queuePayloadEdit(PayloadEditKind kind, String text) {
        if (kind == null) return;
        if (pendingPayloadEdit != null && pendingPayloadEdit.kind() != kind) flushPendingPayloadEdit(true);
        pendingPayloadEdit = new PendingPayloadEdit(kind, text == null ? "" : text);
        pendingPayloadEditAtNanos = System.nanoTime();
    }

    private void flushPendingPayloadEdit(boolean force) {
        PendingPayloadEdit edit = pendingPayloadEdit;
        if (edit == null) return;
        if (!force && System.nanoTime() - pendingPayloadEditAtNanos < PAYLOAD_EDIT_DEBOUNCE_NANOS) return;
        pendingPayloadEdit = null;
        if (payloadEditorModel == null) {
            autoSelectPayloadControls();
            return;
        }

        switch (edit.kind()) {
            case SCRIPT -> {
                if (payloadEditorModel.applyPacketScript(edit.text())) {
                    syncPayloadQuickFieldsFromModel(false);
                    syncPayloadPreviewFieldsFromModel(false);
                }
                autoSelectPayloadControls();
            }
            case HEX -> syncPayloadModelFromHex(edit.text());
            case UTF8 -> syncPayloadModelFromUtf8(edit.text());
            case LOGICAL -> syncPayloadModelFromLogical(edit.text());
            case PREVIEW_ONLY -> syncPayloadPreviewFieldsFromModel(false);
        }
        payloadBuildCache = null;
    }

    private void syncPayloadModelFromHex(String text) {
        if (payloadEditorModel == null) return;
        if (payloadEditorModel.applyBodyHex(text)) {
            payloadContentEdited = true;
            syncPayloadAllFieldsFromModel(false);
        }
    }

    private void syncPayloadModelFromUtf8(String text) {
        if (payloadEditorModel == null) return;
        payloadEditorModel.applyUtf8(text);
        payloadContentEdited = true;
        syncPayloadAllFieldsFromModel(false);
    }

    private void syncPayloadModelFromLogical(String text) {
        if (payloadEditorModel == null) return;
        if (payloadEditorModel.applyLogicalText(text)) {
            payloadContentEdited = true;
            syncPayloadAllFieldsFromModel(false);
        }
    }

    private void syncPayloadAllFieldsFromModel(boolean force) {
        syncPayloadQuickFieldsFromModel(force);
        syncPayloadPreviewFieldsFromModel(force);
    }

    private void syncPayloadQuickFieldsFromModel(boolean force) {
        if (payloadEditorModel == null) return;
        suppressPayloadEditorChange = true;
        try {
            AutismChatField channelField = textFields.get("payload_channel");
            if (channelField != null && (force || !channelField.isFocused())) {
                channelField.setText(payloadEditorModel.channel == null ? "" : payloadEditorModel.channel);
            }
            enumIndices.put("payload_mode", payloadModeIndex(AutismPayloadTemplate.EncodingMode.parse(payloadEditorModel.encodingMode, payloadEditorModel.channel)));
            AutismChatField commandField = textFields.get("payload_command_value");
            if (commandField != null && (force || !commandField.isFocused())) {
                commandField.setText(String.valueOf(payloadEditorModel.commandApiValue));
            }
            toggleStates.put("payload_command_api", payloadEditorModel.commandApiOverride);
        } finally {
            suppressPayloadEditorChange = false;
        }
    }

    private void syncPayloadPreviewFieldsFromModel(boolean force) {
        if (payloadEditorModel == null) return;
        long revision = payloadEditorModel.revision();
        if (!force && payloadPreviewSyncedRevision == revision) return;
        suppressPayloadEditorChange = true;
        try {
            AutismChatField contentField = textFields.get("payload_content");
            if (contentField != null && (force || !contentField.isFocused())) {
                contentField.setText(payloadEditorModel.packetScript());
            }
            AutismChatField hexView = textFields.get("payload_hex_view");
            if (hexView != null && (force || !hexView.isFocused())) {
                hexView.setText(payloadEditorModel.bodyHexMultiline());
            }
            AutismChatField utf8View = textFields.get("payload_utf8_view");
            if (utf8View != null && (force || !utf8View.isFocused())) {
                utf8View.setText(payloadEditorModel.utf8View());
            }
            AutismChatField logicalView = textFields.get("payload_logical_view");
            if (logicalView != null && (force || !logicalView.isFocused())) {
                logicalView.setText(payloadEditorModel.logicalText());
            }
        } finally {
            suppressPayloadEditorChange = false;
        }
        payloadPreviewSyncedRevision = revision;
    }

    private String escapePayloadScriptValue(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n");
    }

    private int payloadTypeIndex(AutismPayloadTemplate.FieldType type) {
        int index = PAYLOAD_FIELD_TYPES.indexOf(type);
        return index < 0 ? PAYLOAD_FIELD_TYPES.indexOf(AutismPayloadTemplate.FieldType.STRING_BYTES) : index;
    }

    private AutismPayloadTemplate.FieldType payloadTypeAt(int row) {
        int index = Math.max(0, Math.min(enumIndices.getOrDefault(payloadTypeKey(row), payloadTypeIndex(AutismPayloadTemplate.FieldType.STRING_BYTES)),
            PAYLOAD_FIELD_TYPES.size() - 1));
        return PAYLOAD_FIELD_TYPES.get(index);
    }

    private String payloadTypeKey(int row) {
        return "payload_type_" + row;
    }

    private String payloadValueKey(int row) {
        return "payload_value_" + row;
    }

    private int payloadModeIndex(AutismPayloadTemplate.EncodingMode mode) {
        int index = PAYLOAD_MODES.indexOf(mode);
        return index < 0 ? PAYLOAD_MODES.indexOf(AutismPayloadTemplate.EncodingMode.ADVANCED_MIXED) : index;
    }

    private AutismPayloadTemplate.EncodingMode payloadModeFromEditor(String channel) {
        int fallback = payloadModeIndex(AutismPayloadTemplate.EncodingMode.parse(payloadAction == null ? "" : payloadAction.payloadEncodingMode, channel));
        int index = Math.max(0, Math.min(enumIndices.getOrDefault("payload_mode", fallback), PAYLOAD_MODES.size() - 1));
        return PAYLOAD_MODES.get(index);
    }

    private void renderCleanPayloadPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        PayloadBuildCache preview = payloadBuildPreview();
        AutismPayloadTemplate.Template template = preview.template();
        AutismPayloadTemplate.BuildResult built = preview.result();

        UiText.draw(ctx, textRenderer, "Custom Payload", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 14;

        cy = renderPayloadStatus(ctx, x, cy, w);
        cy = renderPayloadTabs(ctx, x, cy, w, mx, my);

        int dirLabelW = 18;
        int dirW = 38;
        int modeLabelW = 28;
        UiText.draw(ctx, textRenderer, "Dir", font, AutismColors.textDim(), x, cy + 3, false);
        renderOverlayButton(ctx, x + dirLabelW, cy, dirW, 14, template.direction().name(), CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
            flushPendingPayloadEdit(true);
            if (payloadEditorModel != null) {
                payloadEditorModel.direction = AutismPayloadTemplate.nextDirectionName(payloadEditorModel.direction);
                payloadEditorModel.touch();
                payloadBuildCache = null;
                syncPayloadAllFieldsFromModel(false);
            } else if (payloadAction != null) {
                payloadAction.payloadDirection = AutismPayloadTemplate.nextDirectionName(payloadAction.payloadDirection);
            }
            payloadContentEdited = true;
            refreshInteractiveLayout();
        });
        int modeLabelX = x + dirLabelW + dirW + 5;
        UiText.draw(ctx, textRenderer, "Mode", font, AutismColors.textDim(), modeLabelX, cy + 3, false);
        int modeX = modeLabelX + modeLabelW;
        renderPayloadModeDropdown(modeX, cy, Math.max(54, x + w - modeX), mx, my, template);
        cy += 18;

        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null) {
            int lw = labelWidth(w, "Channel", font, 64);
            drawLabel(ctx, "Channel", x, cy, lw, font);
            int ctrlX = controlX(x, lw);
            int ctrlW = controlWidth(w, lw);
            channelField.setX(ctrlX);
            channelField.setY(cy + 2);
            channelField.setWidth(ctrlW);
            channelField.render(ctx, mx, my, delta);
            cy += ROW_H + ROW_GAP;
        }

        if (payloadEditorModel != null && payloadEditorModel.commandApiRecognized) {
            AutismChatField commandField = textFields.get("payload_command_value");
            if (commandField != null) {
                int lw = labelWidth(w, "CommandApi", font, 64);
                drawLabel(ctx, "CommandApi", x, cy, lw, font);
                int ctrlX = controlX(x, lw);
                int ctrlW = controlWidth(w, lw);
                int btnW = 58;
                int fieldW = Math.max(38, ctrlW - btnW - 3);
                commandField.setX(ctrlX);
                commandField.setY(cy + 2);
                commandField.setWidth(fieldW);
                commandField.render(ctx, mx, my, delta);
                boolean override = toggleStates.getOrDefault("payload_command_api", payloadEditorModel.commandApiOverride);
                renderOverlayButton(ctx, ctrlX + fieldW + 3, cy + 2, btnW, 14, override ? "Override" : "Exact",
                    override ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY,
                    true, mx, my, () -> {
                        if (payloadEditorModel != null) {
                            payloadEditorModel.commandApiOverride = !payloadEditorModel.commandApiOverride;
                            payloadEditorModel.touch();
                            payloadBuildCache = null;
                            toggleStates.put("payload_command_api", payloadEditorModel.commandApiOverride);
                            payloadContentEdited = true;
                        }
                        refreshInteractiveLayout();
                    });
                cy += ROW_H + ROW_GAP;
            }
        }

        if (payloadTabIndex == 0) {
            UiText.draw(ctx, textRenderer, "Packet Script", font,
                AutismColors.textSecondary(), x, cy + 2, false);
            renderOverlayButton(ctx, x + w - 42, cy, 42, 14, "Clear", CompactOverlayButton.Variant.DANGER,
                !payloadFieldsFromEditor().isEmpty(), mx, my, () -> {
                    AutismChatField contentField = textFields.get("payload_content");
                    if (contentField != null) contentField.setText("");
                    payloadContentEdited = true;
                    refreshInteractiveLayout();
                });
            cy += 15;
            int addButtonW = 62;
            renderPayloadAddDropdown(x, cy, Math.max(54, w - addButtonW - 4), mx, my);
            renderOverlayButton(ctx, x + w - addButtonW, cy, addButtonW, 14, "Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                appendPayloadFieldType(enumIndices.getOrDefault("payload_add_type", payloadTypeIndex(AutismPayloadTemplate.FieldType.STRING_BYTES)));
            });
            cy += 17;

            AutismChatField contentField = textFields.get("payload_content");
            if (contentField != null) {
                contentField.setX(x);
                contentField.setY(cy);
                contentField.setWidth(w);
                contentField.setHeight(PAYLOAD_SCRIPT_H);
                contentField.render(ctx, mx, my, delta);
            }
            cy += PAYLOAD_SCRIPT_H + 4;
        } else if (payloadTabIndex == 1) {
            syncPayloadPreviewFieldsFromModel(false);
            AutismChatField hexView = textFields.get("payload_hex_view");
            if (hexView != null) {
                int bytes = payloadEditorModel == null ? built.bytes().length : payloadEditorModel.bodyBytes.length;
                UiText.draw(ctx, textRenderer, "Body Hex - payload body only (" + bytes + " bytes)", font,
                    AutismColors.textSecondary(), x, cy + 2, false);
                cy += 14;
                hexView.setX(x);
                hexView.setY(cy);
                hexView.setWidth(w);
                hexView.setHeight(PAYLOAD_VIEW_H);
                hexView.render(ctx, mx, my, delta);
                cy += PAYLOAD_VIEW_H + 4;
            }
        } else if (payloadTabIndex == 2) {
            syncPayloadPreviewFieldsFromModel(false);
            AutismChatField utf8View = textFields.get("payload_utf8_view");
            if (utf8View != null) {
                UiText.draw(ctx, textRenderer, "UTF-8 / Raw Text Body", font,
                    AutismColors.textSecondary(), x, cy + 2, false);
                cy += 14;
                utf8View.setX(x);
                utf8View.setY(cy);
                utf8View.setWidth(w);
                utf8View.setHeight(PAYLOAD_VIEW_H);
                utf8View.render(ctx, mx, my, delta);
                cy += PAYLOAD_VIEW_H + 4;
            }
        } else {
            syncPayloadPreviewFieldsFromModel(false);
            AutismChatField logicalView = textFields.get("payload_logical_view");
            if (logicalView != null) {
                UiText.draw(ctx, textRenderer, "Logical Packet - envelope + body", font,
                    AutismColors.textSecondary(), x, cy + 2, false);
                cy += 14;
                logicalView.setX(x);
                logicalView.setY(cy);
                logicalView.setWidth(w);
                logicalView.setHeight(PAYLOAD_VIEW_H);
                logicalView.render(ctx, mx, my, delta);
                cy += PAYLOAD_VIEW_H + 4;
            }
        }

        int halfW = (w - 4) / 2;
        String notice = payloadValidationText(built);
        boolean modelBad = payloadEditorModel != null && payloadEditorModel.validationError != null && !payloadEditorModel.validationError.isBlank();
        int noticeColor = modelBad || !built.errors().isEmpty()
            ? AutismColors.dangerText()
            : (!built.warnings().isEmpty() ? 0xFFFFC857 : AutismColors.textDim());
        UiText.draw(ctx, textRenderer, UiText.trimToWidth(textRenderer, notice, Math.max(10, w), font, noticeColor),
            font, noticeColor, x, cy + 2, false);
        cy += 14;

        renderOverlayButton(ctx, x, cy, halfW, 16, configurationOnly ? "Send: In Game" : "Send",
            CompactOverlayButton.Variant.SUCCESS, !configurationOnly && built.ok() && !modelBad, mx, my, () -> {
            if (configurationOnly) return;
            try {
                PayloadAction action = buildPayloadActionFromEditor();
                action.execute(MC);
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("§cPayload send failed: " + AutismPayloadSupport.safeMessage(e));
            }
        });
        renderOverlayButton(ctx, x + halfW + 4, cy, halfW, 16, "Reset", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
            resetPayloadEditorFields();
            refreshInteractiveLayout();
        });
        cy += 20;

        if (isPayloadConfigPhase()) {
            cy = renderEditorHint(ctx, x, cy, w, "Config-phase payload — sending may disconnect.", 0xFFE6B450);
        }
        renderEditorHint(ctx, x, cy, w, payloadEditorHint());
    }

    private int renderPayloadTabs(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        String[] tabs = {"Packet", "Body Hex", "UTF-8", "Logical"};
        int gap = 3;
        int tabW = Math.max(42, (w - gap * (tabs.length - 1)) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * (tabW + gap);
            int tw = i == tabs.length - 1 ? Math.max(30, x + w - tx) : tabW;
            CompactOverlayButton.Variant variant = payloadTabIndex == i
                ? CompactOverlayButton.Variant.SUCCESS
                : CompactOverlayButton.Variant.SECONDARY;
            final int index = i;
            renderOverlayButton(ctx, tx, y, tw, 14, tabs[i], variant, true, mx, my, () -> {
                flushPendingPayloadEdit(true);
                payloadTabIndex = index;
                clearPayloadFieldFocus();
                refreshInteractiveLayout();
            });
        }
        return y + 18;
    }

    private void clearPayloadFieldFocus() {
        for (String key : List.of("payload_content", "payload_hex_view", "payload_utf8_view", "payload_logical_view")) {
            AutismChatField field = textFields.get(key);
            if (field != null) field.setFocused(false);
        }
    }

    private void renderPayloadAddDropdown(int x, int y, int w, int mx, int my) {
        int idx = Math.max(0, Math.min(enumIndices.getOrDefault("payload_add_type",
            payloadTypeIndex(suggestPayloadAddType(payloadEditorChannel(), payloadModeFromEditor(payloadEditorChannel()), payloadFieldsFromEditor()))),
            PAYLOAD_FIELD_TYPE_LABELS.size() - 1));
        CompactDropdown dd = enumDropdownCache.computeIfAbsent("payload_add_type", key ->
            new CompactDropdown(x, y, w, 14, PAYLOAD_FIELD_TYPE_LABELS, idx, newIdx -> {
                payloadAddTypeManuallyChanged = true;
                enumIndices.put("payload_add_type", newIdx);
            }));
        dd.setBounds(x, y, w, 14)
            .setOptions(PAYLOAD_FIELD_TYPE_LABELS)
            .setSelectedIndex(idx)
            .setOnSelect(newIdx -> {
                payloadAddTypeManuallyChanged = true;
                enumIndices.put("payload_add_type", newIdx);
            })
            .setButtonLabelOverride("");
        enumDropdowns.add(dd);
    }

    private void appendPayloadFieldType(int index) {
        if (index < 0 || index >= PAYLOAD_FIELD_TYPES.size()) return;
        AutismPayloadTemplate.FieldType type = PAYLOAD_FIELD_TYPES.get(index);
        appendPayloadField(type.label() + " = " + type.defaultValue());
        if (!payloadModeManuallyChanged) {
            enumIndices.put("payload_mode", payloadModeIndex(suggestPayloadMode(payloadEditorChannel(),
                List.of(new AutismPayloadTemplate.Field(type, type.defaultValue(), true)), payloadModeFromEditor(payloadEditorChannel()))));
        }
        refreshInteractiveLayout();
    }

    private void updatePayloadPreviewFields(AutismPayloadTemplate.BuildResult built) {
        AutismChatField hexView = textFields.get("payload_hex_view");
        if (hexView != null && !hexView.isFocused()) {
            suppressPayloadEditorChange = true;
            try {
                hexView.setText(formatPayloadHex(built.bytes()));
            } finally {
                suppressPayloadEditorChange = false;
            }
        }
        AutismChatField utf8View = textFields.get("payload_utf8_view");
        if (utf8View != null && !utf8View.isFocused()) {
            suppressPayloadEditorChange = true;
            try {
                utf8View.setText(formatPayloadUtf8View(built));
            } finally {
                suppressPayloadEditorChange = false;
            }
        }
    }

    private String formatPayloadHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3 + bytes.length / 16 * 10);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(i % 16 == 0 ? '\n' : ' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private String formatPayloadUtf8View(AutismPayloadTemplate.BuildResult built) {
        AutismPayloadTemplate.Preview preview = built.preview();
        String fullUtf8 = AutismPayloadSupport.decodeLikelyUtf8Text(built.bytes());
        if (fullUtf8 != null && !fullUtf8.isBlank()) return fullUtf8;
        if (preview.utf8() != null && !preview.utf8().isBlank()) return preview.utf8();
        if (preview.minecraftString() != null && !preview.minecraftString().isBlank()) return preview.minecraftString();
        if (preview.javaWriteUtf() != null && !preview.javaWriteUtf().isBlank()) return preview.javaWriteUtf();
        return "";
    }

    private String payloadValidationText(AutismPayloadTemplate.BuildResult built) {
        if (payloadEditorModel != null && payloadEditorModel.validationError != null && !payloadEditorModel.validationError.isBlank()) {
            return "Fix: " + shortPayloadMessage(payloadEditorModel.validationError);
        }
        String scriptIssue = payloadScriptIssue();
        if (scriptIssue != null) return "Fix: " + shortPayloadMessage(scriptIssue);
        if (payloadTabIndex == 1) {
            AutismChatField hexView = textFields.get("payload_hex_view");
            if (hexView != null && hexView.isFocused()) {
                try {
                    parsePayloadHexView(hexView.getText());
                } catch (Exception e) {
                    return "Bad hex: " + shortPayloadMessage(AutismPayloadSupport.safeMessage(e));
                }
            }
        }
        if (!built.errors().isEmpty()) return "Fix: " + shortPayloadMessage(built.errors().get(0));
        if (!built.warnings().isEmpty()) return "Warn: " + shortPayloadMessage(built.warnings().get(0));
        return "Ready: " + built.bytes().length + " bytes";
    }

    private String shortPayloadMessage(String message) {
        String text = message == null ? "invalid payload" : message.replace('\n', ' ').strip();
        return text.length() <= 34 ? text : text.substring(0, 31) + "...";
    }

    private byte[] parsePayloadHexView(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) return new byte[0];
        String compact = value.replaceAll("(?i)0x", "").replaceAll("\\s+", "");
        if (compact.isEmpty()) return new byte[0];
        if ((compact.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex needs byte pairs");
        }
        if (!compact.matches("(?i)[0-9a-f]+")) {
            throw new IllegalArgumentException("Only hex digits are allowed here");
        }
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < compact.length(); i += 2) {
            out[i / 2] = (byte) Integer.parseInt(compact.substring(i, i + 2), 16);
        }
        return out;
    }

    private String readablePayloadUtf8(byte[] bytes) {
        String text = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
        if (text == null || text.isBlank()) return null;
        for (int i = 0; i < text.length(); i++) {
            char chr = text.charAt(i);
            if (chr == '\n' || chr == '\r' || chr == '\t') continue;
            if (Character.isISOControl(chr) || chr == '\uFFFD') return null;
        }
        return text;
    }

    private String payloadScriptIssue() {
        AutismChatField contentField = textFields.get("payload_content");
        if (contentField == null) return null;
        String text = contentField.getText();
        if (text == null || text.isBlank()) return null;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i] == null ? "" : lines[i];
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = firstPayloadDelimiter(raw);
            if (split < 0) return "Line " + (i + 1) + " needs writer = value";
            String typeText = raw.substring(0, split).strip();
            if (isPayloadEnvelopeKey(typeText)) continue;
            if (payloadFieldTypeFromToken(typeText) == null) return "Line " + (i + 1) + " unknown writer";
        }
        return null;
    }

    private long payloadEditorRevision() {
        return payloadEditorModel == null ? 0L : payloadEditorModel.revision();
    }

    private Component payloadScriptComponent(String source) {
        MutableComponent out = Component.empty();
        String safe = source == null ? "" : source;
        String[] lines = safe.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) appendPayloadStyled(out, "\n", 0x777777);
            String line = lines[i];
            if (line.isEmpty()) continue;
            String stripped = line.stripLeading();
            if (stripped.startsWith("#")) {
                appendPayloadStyled(out, line, 0x777777);
                continue;
            }
            int split = firstPayloadDelimiter(line);
            if (split < 0) {
                appendPayloadStyled(out, line, 0xFF7777);
                continue;
            }
            String typePart = line.substring(0, split);
            String sep = line.substring(split, split + 1);
            String valuePart = line.substring(split + 1);
            AutismPayloadTemplate.FieldType type = payloadFieldTypeFromToken(typePart.strip());
            appendPayloadStyled(out, typePart, isPayloadEnvelopeKey(typePart.strip()) ? 0xF92672 : (type == null ? 0xFF7777 : 0x66D9EF));
            appendPayloadStyled(out, sep, 0xAAAAAA);
            appendPayloadValueStyled(out, valuePart);
        }
        return out;
    }

    private boolean isPayloadEnvelopeKey(String key) {
        if (key == null) return false;
        return switch (key.strip().toLowerCase(Locale.ROOT)) {
            case "direction", "phase", "channel", "packetid", "packet_id", "packetclass", "packet_class",
                 "sourceprotocol", "source_protocol", "provenance" -> true;
            default -> false;
        };
    }

    private Component payloadHexComponent(String source) {
        MutableComponent out = Component.empty();
        String safe = source == null ? "" : source;
        int hexCount = 0;
        for (int i = 0; i < safe.length(); i++) {
            char chr = safe.charAt(i);
            if (chr == '\n' || Character.isWhitespace(chr)) {
                appendPayloadStyled(out, Character.toString(chr), 0x777777);
            } else if (isPayloadHexDigit(chr)) {
                int color = ((hexCount / 2) % 2 == 0) ? 0xA6E22E : 0x66D9EF;
                appendPayloadStyled(out, Character.toString(chr), color);
                hexCount++;
            } else {
                appendPayloadStyled(out, Character.toString(chr), 0xFF7777);
            }
        }
        return out;
    }

    private Component payloadUtf8Component(String source) {
        MutableComponent out = Component.empty();
        String safe = source == null ? "" : source;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < safe.length(); i++) {
            char chr = safe.charAt(i);
            int color;
            if (chr == '\n') color = 0x777777;
            else if (Character.isWhitespace(chr)) color = 0x777777;
            else if (escaped) {
                color = 0xAE81FF;
                escaped = false;
            } else if (chr == '\\') {
                color = 0xAE81FF;
                escaped = true;
            } else if (chr == '"' || chr == '\'') {
                color = 0xE6DB74;
                inString = !inString;
            } else if (inString) color = 0xE6DB74;
            else if ("{}[],:=".indexOf(chr) >= 0) color = 0x66D9EF;
            else if (Character.isDigit(chr) || chr == '-' || chr == '+') color = 0xAE81FF;
            else if (Character.isLetter(chr)) color = 0xA6E22E;
            else color = 0xF8F8F2;
            appendPayloadStyled(out, Character.toString(chr), color);
        }
        return out;
    }

    private Component payloadLogicalComponent(String source) {
        MutableComponent out = Component.empty();
        String safe = source == null ? "" : source;
        String[] lines = safe.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) appendPayloadStyled(out, "\n", 0x777777);
            String line = lines[i];
            int split = firstPayloadDelimiter(line);
            if (split < 0) {
                appendPayloadStyled(out, line, line.strip().startsWith("#") ? 0x777777 : 0xF8F8F2);
                continue;
            }
            String key = line.substring(0, split);
            String sep = line.substring(split, split + 1);
            String value = line.substring(split + 1);
            appendPayloadStyled(out, key, isPayloadEnvelopeKey(key) || key.toLowerCase(Locale.ROOT).contains("hex") ? 0x66D9EF : 0xA6E22E);
            appendPayloadStyled(out, sep, 0xAAAAAA);
            if (key.toLowerCase(Locale.ROOT).contains("hex")) {
                appendPayloadStyled(out, value, 0xE6DB74);
            } else {
                appendPayloadValueStyled(out, value);
            }
        }
        return out;
    }

    private void appendPayloadValueStyled(MutableComponent out, String valuePart) {
        if (valuePart == null || valuePart.isEmpty()) return;
        int leading = 0;
        while (leading < valuePart.length() && Character.isWhitespace(valuePart.charAt(leading))) leading++;
        if (leading > 0) appendPayloadStyled(out, valuePart.substring(0, leading), 0xAAAAAA);
        String value = valuePart.substring(leading);
        int color = looksNumericPayloadValue(value) ? 0xAE81FF : looksBooleanPayloadValue(value) ? 0xF92672 : 0xE6DB74;
        appendPayloadStyled(out, value, color);
    }

    private void appendPayloadStyled(MutableComponent out, String text, int color) {
        if (text == null || text.isEmpty()) return;
        out.append(UiText.literal(text, theme.fontFor(UiTone.BODY), color));
    }

    private int firstPayloadDelimiter(String line) {
        if (line == null) return -1;
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }

    private AutismPayloadTemplate.FieldType payloadFieldTypeFromToken(String token) {
        if (token == null || token.isBlank()) return null;
        String normalized = token.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (AutismPayloadTemplate.FieldType type : AutismPayloadTemplate.FieldType.values()) {
            if (type.name().equals(normalized) || type.label().equalsIgnoreCase(token.strip())) return type;
        }
        if ("UUID".equals(normalized)) return AutismPayloadTemplate.FieldType.UUID_FIELD;
        if ("UTF".equals(normalized) || "WRITEUTF".equals(normalized)) return AutismPayloadTemplate.FieldType.JAVA_WRITE_UTF;
        if ("MC_STRING".equals(normalized) || "MINECRAFT_STRING".equals(normalized)) return AutismPayloadTemplate.FieldType.MINECRAFT_STRING;
        if ("COMPONENT".equals(normalized) || "TEXT_COMPONENT".equals(normalized)) return AutismPayloadTemplate.FieldType.TEXT_COMPONENT;
        return null;
    }

    private boolean isPayloadHexDigit(char chr) {
        return (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
    }

    private boolean looksNumericPayloadValue(String value) {
        String text = value == null ? "" : value.strip();
        return text.matches("-?(0x[0-9a-fA-F]+|\\d+)(\\.\\d+)?");
    }

    private boolean looksBooleanPayloadValue(String value) {
        String text = value == null ? "" : value.strip();
        return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
    }

    private void renderPayloadModeDropdown(int x, int y, int w, int mx, int my, AutismPayloadTemplate.Template template) {
        int idx = payloadModeIndex(template == null ? AutismPayloadTemplate.EncodingMode.ADVANCED_MIXED : template.mode());
        CompactDropdown dd = enumDropdownCache.computeIfAbsent("payload_mode", key ->
            new CompactDropdown(x, y, w, 14, PAYLOAD_MODE_LABELS, idx, newIdx -> {
                if (newIdx >= 0 && newIdx < PAYLOAD_MODES.size()) {
                    flushPendingPayloadEdit(true);
                    payloadModeManuallyChanged = true;
                    enumIndices.put("payload_mode", newIdx);
                    if (payloadAction != null) payloadAction.payloadEncodingMode = PAYLOAD_MODES.get(newIdx).name();
                    if (payloadEditorModel != null) {
                        payloadEditorModel.encodingMode = PAYLOAD_MODES.get(newIdx).name();
                        payloadEditorModel.touch();
                        payloadBuildCache = null;
                        syncPayloadPreviewFieldsFromModel(false);
                    }
                    payloadContentEdited = true;
                }
            }));
        dd.setBounds(x, y, w, 14)
            .setOptions(PAYLOAD_MODE_LABELS)
            .setSelectedIndex(idx)
            .setOnSelect(newIdx -> {
                if (newIdx >= 0 && newIdx < PAYLOAD_MODES.size()) {
                    flushPendingPayloadEdit(true);
                    payloadModeManuallyChanged = true;
                    enumIndices.put("payload_mode", newIdx);
                    if (payloadAction != null) payloadAction.payloadEncodingMode = PAYLOAD_MODES.get(newIdx).name();
                    if (payloadEditorModel != null) {
                        payloadEditorModel.encodingMode = PAYLOAD_MODES.get(newIdx).name();
                        payloadEditorModel.touch();
                        payloadBuildCache = null;
                        syncPayloadPreviewFieldsFromModel(false);
                    }
                    payloadContentEdited = true;
                }
            });
        enumDropdowns.add(dd);
    }

    private int renderPayloadFieldRow(GuiGraphicsExtractor ctx, int x, int y, int w, int row, int mx, int my, float delta) {
        int btnH = 14;
        int typeW = Math.min(116, Math.max(88, (w - 70) / 2));
        int gap = 3;
        int actionW = 19;
        int actionsW = actionW * 3 + gap * 2;
        int valueX = x + typeW + gap;
        int valueW = Math.max(36, w - typeW - gap - actionsW - gap);
        renderPayloadTypeDropdown(row, x, y + 2, typeW, mx, my);

        AutismChatField valueField = textFields.get(payloadValueKey(row));
        if (valueField != null) {
            AutismPayloadTemplate.FieldType type = payloadTypeAt(row);
            valueField.setPlaceholder(Component.literal(type.defaultValue()));
            valueField.setX(valueX);
            valueField.setY(y + 3);
            valueField.setWidth(valueW);
            valueField.render(ctx, mx, my, delta);
        }

        int ax = valueX + valueW + gap;
        renderOverlayButton(ctx, ax, y + 2, actionW, btnH, "Up", CompactOverlayButton.Variant.SECONDARY, row > 0, mx, my, () -> movePayloadField(row, -1));
        renderOverlayButton(ctx, ax + actionW + gap, y + 2, actionW, btnH, "Dn", CompactOverlayButton.Variant.SECONDARY, row < payloadFieldCount - 1, mx, my, () -> movePayloadField(row, 1));
        renderOverlayButton(ctx, ax + (actionW + gap) * 2, y + 2, actionW, btnH, "X", CompactOverlayButton.Variant.DANGER, payloadFieldCount > 1, mx, my, () -> removePayloadField(row));
        return y + PAYLOAD_FIELD_ROW_H;
    }

    private void renderPayloadTypeDropdown(int row, int x, int y, int w, int mx, int my) {
        String key = payloadTypeKey(row);
        int idx = Math.max(0, Math.min(enumIndices.getOrDefault(key, payloadTypeIndex(AutismPayloadTemplate.FieldType.STRING_BYTES)), PAYLOAD_FIELD_TYPE_LABELS.size() - 1));
        CompactDropdown dd = enumDropdownCache.computeIfAbsent(key, cacheKey ->
            new CompactDropdown(x, y, w, 14, PAYLOAD_FIELD_TYPE_LABELS, idx, newIdx -> selectPayloadType(row, newIdx)));
        dd.setBounds(x, y, w, 14)
            .setOptions(PAYLOAD_FIELD_TYPE_LABELS)
            .setSelectedIndex(idx)
            .setOnSelect(newIdx -> selectPayloadType(row, newIdx));
        enumDropdowns.add(dd);
    }

    private void selectPayloadType(int row, int newIdx) {
        if (newIdx < 0 || newIdx >= PAYLOAD_FIELD_TYPES.size()) return;
        String key = payloadTypeKey(row);
        int previous = enumIndices.getOrDefault(key, -1);
        enumIndices.put(key, newIdx);
        if (previous != newIdx) {
            AutismChatField valueField = textFields.get(payloadValueKey(row));
            if (valueField != null && valueField.getText().isBlank()) {
                valueField.setText(PAYLOAD_FIELD_TYPES.get(newIdx).defaultValue());
            }
            payloadContentEdited = true;
        }
    }

    private void movePayloadField(int row, int direction) {
        List<AutismPayloadTemplate.Field> fields = new ArrayList<>(payloadFieldsFromEditor());
        int target = row + direction;
        if (row < 0 || row >= fields.size() || target < 0 || target >= fields.size()) return;
        Collections.swap(fields, row, target);
        setPayloadFieldsInEditor(fields);
        payloadContentEdited = true;
        refreshInteractiveLayout();
    }

    private void removePayloadField(int row) {
        List<AutismPayloadTemplate.Field> fields = new ArrayList<>(payloadFieldsFromEditor());
        if (fields.size() <= 1 || row < 0 || row >= fields.size()) return;
        fields.remove(row);
        setPayloadFieldsInEditor(fields);
        payloadContentEdited = true;
        refreshInteractiveLayout();
    }

    private AutismPayloadTemplate.Template currentPayloadTemplateFromEditor() {
        if (payloadEditorModel != null) return currentPayloadTemplateFromModel();
        PayloadAction action = new PayloadAction();
        if (payloadAction != null) {
            action.fromTag(payloadAction.toTag());
        }
        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null && !channelField.getText().isBlank()) {
            action.channel = channelField.getText().strip();
        }
        action.payloadPhase = "PLAY";
        action.payloadEncodingMode = payloadModeFromEditor(action.channel).name();
        action.payloadFields = AutismPayloadTemplate.serializeFields(payloadFieldsFromEditor());
        return AutismPayloadTemplate.fromAction(action);
    }

    private PayloadBuildCache payloadBuildPreview() {
        if (payloadEditorModel == null) {
            AutismPayloadTemplate.Template template = currentPayloadTemplateFromEditor();
            return new PayloadBuildCache(Long.MIN_VALUE, template, template.build());
        }
        long revision = payloadEditorModel.revision();
        PayloadBuildCache hit = payloadBuildCache;
        if (hit != null && hit.revision() == revision) return hit;
        AutismPayloadTemplate.Template template = currentPayloadTemplateFromModel();
        hit = new PayloadBuildCache(revision, template, template.build());
        payloadBuildCache = hit;
        return hit;
    }

    private AutismPayloadTemplate.Template currentPayloadTemplateFromModel() {
        return payloadEditorModel == null ? AutismPayloadTemplate.fromAction(new PayloadAction()) : payloadEditorModel.toTemplate();
    }

    private void appendPayloadField(String line) {
        AutismChatField contentField = textFields.get("payload_content");
        if (contentField == null || line == null || line.isBlank()) return;
        String current = contentField.getText() == null ? "" : contentField.getText().stripTrailing();
        contentField.setText(current.isBlank() ? line : current + "\n" + line);
        payloadContentEdited = true;
    }

    private void applyPayloadTemplateToEditor(AutismPayloadTemplate.Template template) {
        if (template == null) return;
        if (payloadAction != null) {
            payloadAction.channel = template.channel();
            payloadAction.payloadDirection = template.direction().name();
            payloadAction.payloadPhase = template.phase().name();
            payloadAction.payloadEncodingMode = template.mode().name();
            payloadAction.payloadFields = AutismPayloadTemplate.serializeFields(template.fields());
        }
        suppressPayloadEditorChange = true;
        try {
            if (payloadEditorModel != null) {
                payloadEditorModel.channel = template.channel();
                payloadEditorModel.direction = template.direction().name();
                payloadEditorModel.phase = template.phase().name();
                payloadEditorModel.encodingMode = template.mode().name();
                payloadEditorModel.bodyFields = new ArrayList<>(template.fields());
                payloadEditorModel.touch();
                payloadEditorModel.applyPacketScript(payloadEditorModel.packetScript());
                payloadBuildCache = null;
            }
            AutismChatField channelField = textFields.get("payload_channel");
            if (channelField != null) channelField.setText(template.channel());
            if (payloadEditorModel == null) setPayloadFieldsInEditor(template.fields());
            else syncPayloadAllFieldsFromModel(true);
        } finally {
            suppressPayloadEditorChange = false;
        }
        payloadChannelEdited = true;
        payloadContentEdited = true;
        refreshInteractiveLayout();
    }

    private int renderPayloadStatus(GuiGraphicsExtractor ctx, int x, int y, int w) {
        Identifier font = theme.fontFor(UiTone.BODY);
        String status = payloadReplayStatus();
        String transport = payloadEditorModel == null
            ? "Encoder: vanilla"
            : (payloadEditorModel.direction + " / " + payloadEditorModel.phase
                + (payloadEditorModel.packetId >= 0 ? " / id " + payloadEditorModel.packetId : " / id ?"));
        UiText.draw(ctx, textRenderer,
            UiText.trimToWidth(textRenderer, status, Math.max(10, w), font, AutismColors.textPrimary()),
            font, AutismColors.textPrimary(), x, y + 2, false);
        y += 11;
        UiText.draw(ctx, textRenderer,
            UiText.trimToWidth(textRenderer, transport, Math.max(10, w), font, AutismColors.textDim()),
            font, AutismColors.textDim(), x, y + 2, false);
        return y + 13;
    }

    private String payloadReplayStatus() {
        if (payloadEditorModel != null) {
            String decoded = payloadEditorModel.decodedKind == null ? "payload" : payloadEditorModel.decodedKind;
            int bytes = payloadEditorModel.bodyBytes == null ? 0 : payloadEditorModel.bodyBytes.length;
            if (payloadContentEdited || payloadChannelEdited) return "Edited: " + bytes + "B " + decoded;
            return "Ready: " + bytes + "B " + decoded;
        }
        if (payloadContentEdited) {
            return "Edited: script rebuilds bytes";
        }
        if (payloadChannelEdited) {
            return "Edited: channel changed";
        }
        return "Payload builder ready";
    }

    private String payloadContentLabel() {
        return switch (payloadContentMode) {
            case COMMAND_INT -> "Payload Value";
            case BRAND_STRING -> "Brand";
            case UTF8_TEXT -> "Payload Component";
            case BINARY_REPLAY -> "Payload";
        };
    }

    private String payloadEditorHint() {
        return "Rows run top to bottom.";
    }

    private boolean isPayloadConfigPhase() {
        if (payloadEditorModel != null && payloadEditorModel.phase != null) {
            return payloadEditorModel.phase.toLowerCase(java.util.Locale.ROOT).contains("config");
        }
        return payloadAction != null && payloadAction.payloadPhase != null
            && payloadAction.payloadPhase.toLowerCase(java.util.Locale.ROOT).contains("config");
    }

    private void resetPayloadEditorFields() {
        pendingPayloadEdit = null;
        payloadEditorModel = AutismPayloadEditorModel.fromAction(payloadAction);
        payloadPreviewSyncedRevision = Long.MIN_VALUE;
        payloadBuildCache = null;
        PayloadEditorState state = resolvePayloadEditorState(payloadAction);
        payloadContentMode = state.contentMode();
        suppressPayloadEditorChange = true;
        try {
            AutismChatField channelField = textFields.get("payload_channel");
            if (channelField != null) {
                channelField.setText(state.channel());
            }
            syncPayloadAllFieldsFromModel(true);
            payloadChannelEdited = false;
            payloadContentEdited = false;
            payloadRawEdited = false;
            payloadJsonEdited = false;
            payloadModeManuallyChanged = false;
            payloadAddTypeManuallyChanged = false;
        } finally {
            suppressPayloadEditorChange = false;
        }
        autoSelectPayloadControls();
    }

    private PayloadAction buildCleanPayloadActionFromEditor() {
        flushPendingPayloadEdit(true);
        if (payloadEditorModel != null) {
            if (payloadEditorModel.validationError != null && !payloadEditorModel.validationError.isBlank()) {
                throw new IllegalArgumentException(payloadEditorModel.validationError);
            }
            PayloadAction action = payloadEditorModel.toAction(payloadAction);
            if (action.channel == null || action.channel.isBlank()) action.channel = "minecraft:brand";
            AutismPayloadTemplate.BuildResult built = payloadEditorModel.toTemplate().build();
            if (!built.ok()) throw new IllegalArgumentException(String.join("; ", built.errors()));
            action.payloadData = AutismPayloadSupport.toHex(built.bytes());
            return action;
        }
        PayloadAction action = new PayloadAction();
        if (payloadAction != null) {
            action.fromTag(payloadAction.toTag());
        }
        action.payloadClassName = "";
        action.commandApiOverride = false;
        action.javaSource = "";
        action.payloadScriptEnabled = false;

        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null) {
            String editorChannel = channelField.getText().strip();
            if (!editorChannel.isBlank()) {
                action.channel = editorChannel;
            }
        }

        action.payloadFields = AutismPayloadTemplate.serializeFields(payloadFieldsFromEditor());
        if (action.payloadDirection == null || action.payloadDirection.isBlank()) action.payloadDirection = "C2S";
        action.payloadPhase = "PLAY";
        action.payloadEncodingMode = payloadModeFromEditor(action.channel).name();

        AutismPayloadTemplate.Template template = AutismPayloadTemplate.fromAction(action);
        AutismPayloadTemplate.BuildResult built = template.build();
        if (!built.ok()) throw new IllegalArgumentException(String.join("; ", built.errors()));
        action.channel = template.channel();
        action.payloadDirection = template.direction().name();
        action.payloadPhase = "PLAY";
        action.payloadEncodingMode = template.mode().name();
        action.payloadFields = AutismPayloadTemplate.serializeFields(template.fields());
        action.sourceDirection = action.payloadDirection;
        action.sourceProtocol = "";
        action.payloadData = AutismPayloadSupport.toHex(built.bytes());
        action.payloadJson = "";
        action.commandApiRecognized = false;
        action.commandApiOverride = false;

        if (action.channel == null || action.channel.isBlank()) {
            action.channel = payloadAction != null && payloadAction.channel != null && !payloadAction.channel.isBlank()
                ? payloadAction.channel
                : "minecraft:brand";
        }
        return action;
    }

    private void renderPayloadPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        if (payloadAction != null || targetAction != null) {
            renderCleanPayloadPanel(ctx, x, bodyTop, w, mx, my, delta);
            return;
        }
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        UiText.draw(ctx, textRenderer, "Custom Payload Editor", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        UiText.draw(ctx, textRenderer, "Packet JSON", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        AutismChatField jsonField = textFields.get("payload_json");
        if (jsonField != null) {
            jsonField.setX(x);
            jsonField.setY(cy);
            jsonField.setWidth(w);
            jsonField.setHeight(PAYLOAD_JSON_H);
            jsonField.render(ctx, mx, my, delta);
            cy += PAYLOAD_JSON_H + 4;
        }

        int halfW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, halfW, 14, "Pretty JSON", mx, my, () -> {
            formatPayloadJsonField(false);
            refreshInteractiveLayout();
        });
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Apply JSON", mx, my, () -> {
            syncPayloadPreviewFromJson(true);
            refreshInteractiveLayout();
        });
        cy += 18;

        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null) {
            int lw = labelWidth(w, "Channel", font, 92);
            drawLabel(ctx, "Channel", x, cy, lw, font);
            int ctrlX = controlX(x, lw);
            int ctrlW = controlWidth(w, lw);
            channelField.setX(ctrlX);
            channelField.setY(cy + 2);
            channelField.setWidth(ctrlW);
            channelField.render(ctx, mx, my, delta);
            cy += ROW_H + ROW_GAP;
        }

        if (toggleStates.getOrDefault("payload_command_api", false)) {
            AutismChatField commandField = textFields.get("payload_command_value");
            if (commandField != null) {
                int lw = labelWidth(w, "CommandApi", font, 92);
                drawLabel(ctx, "CommandApi", x, cy, lw, font);
                int ctrlX = controlX(x, lw);
                int ctrlW = controlWidth(w, lw);
                int btnW = 30;
                int fieldW = Math.max(32, ctrlW - btnW - 2);
                commandField.setX(ctrlX);
                commandField.setY(cy + 2);
                commandField.setWidth(fieldW);
                commandField.render(ctx, mx, my, delta);
                renderOverlayButton(ctx, ctrlX + fieldW + 2, cy + 2, btnW, 14, "Sync",
                    CompactOverlayButton.Variant.GHOST, true, mx, my, () -> {
                        syncPayloadRawFieldFromCommand();
                        refreshInteractiveLayout();
                    });
                cy += ROW_H + ROW_GAP;
            }
        }

        UiText.draw(ctx, textRenderer, "UTF-8 Component", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        AutismChatField textField = textFields.get("payload_text");
        if (textField != null) {
            textField.setX(x);
            textField.setY(cy);
            textField.setWidth(w);
            textField.setHeight(PAYLOAD_TEXT_H);
            textField.render(ctx, mx, my, delta);
            cy += PAYLOAD_TEXT_H + 4;
        }

        int legacyHalfW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, legacyHalfW, 14, "Component -> Hex", mx, my, () -> {
            syncPayloadRawFieldFromText();
            refreshInteractiveLayout();
        });
        renderActionButton(ctx, x + legacyHalfW + 4, cy, legacyHalfW, 14, "Hex -> Component", mx, my, () -> {
            syncPayloadTextFieldFromRaw();
            refreshInteractiveLayout();
        });
        cy += 18;

        UiText.draw(ctx, textRenderer, "Raw Payload", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        AutismChatField rawField = textFields.get("payload_data");
        if (rawField != null) {
            rawField.setX(x);
            rawField.setY(cy);
            rawField.setWidth(w);
            rawField.setHeight(PAYLOAD_RAW_H);
            rawField.render(ctx, mx, my, delta);
            cy += PAYLOAD_RAW_H + 4;
        }

        renderActionButton(ctx, x, cy, halfW, 14, "Parse Int", mx, my, () -> {
            syncPayloadCommandFieldFromRaw();
            refreshInteractiveLayout();
        });
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Normalize", mx, my, () -> {
            AutismChatField field = textFields.get("payload_data");
            if (field == null) return;
            try {
                field.setText(AutismPayloadSupport.toHex(AutismPayloadSupport.parsePayloadBytes(field.getText())));
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("§cInvalid payload data: " + AutismPayloadSupport.safeMessage(e));
            }
        });
        cy += 18;

        renderActionButton(ctx, x, cy, halfW, 14, "Seed From Int", mx, my, () -> {
            syncPayloadRawFieldFromCommand();
            refreshInteractiveLayout();
        });
        renderOverlayButton(ctx, x + halfW + 4, cy, halfW, 14, configurationOnly ? "Send: In Game" : "Send Now",
            CompactOverlayButton.Variant.SUCCESS, !configurationOnly, mx, my, () -> {
            if (configurationOnly) return;
            try {
                PayloadAction action = buildPayloadActionFromEditor();
                action.execute(MC);
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("§cPayload send failed: " + AutismPayloadSupport.safeMessage(e));
            }
        });
        cy += 20;

        UiText.draw(ctx, textRenderer, "Runtime Java", font,
            AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        AutismChatField javaField = textFields.get("payload_java");
        if (javaField != null) {
            javaField.setX(x);
            javaField.setY(cy);
            javaField.setWidth(w);
            javaField.setHeight(PAYLOAD_JAVA_H);
            javaField.render(ctx, mx, my, delta);
            cy += PAYLOAD_JAVA_H + 4;
        }

        renderEditorHint(ctx, x, cy, w,
            "Raw replay is exact by default. Use Apply JSON only when you want the JSON view to rebuild the raw bytes.");
    }

    private PayloadAction buildPayloadActionFromEditor() {
        if (payloadAction != null || targetAction != null) {
            return buildCleanPayloadActionFromEditor();
        }
        PayloadAction action = new PayloadAction();
        if (payloadAction != null) {
            action.fromTag(payloadAction.toTag());
        }
        action.payloadClassName = "";

        AutismChatField jsonField = textFields.get("payload_json");
        if (jsonField != null) {
            action.payloadJson = jsonField.getText();
        }

        boolean jsonUsable = false;
        AutismPayloadJsonSupport.EncodedPayload jsonEncoded = null;
        if (action.payloadJson != null && !action.payloadJson.isBlank()) {
            action.payloadJson = AutismPayloadJsonSupport.normalizeJson(action.payloadJson);
            try {
                jsonEncoded = AutismPayloadJsonSupport.encodeAction(action);
                jsonUsable = true;
            } catch (Exception ignored) {  }
        }

        AutismChatField channelField = textFields.get("payload_channel");
        if (channelField != null) {
            String editorChannel = channelField.getText().strip();
            if (!editorChannel.isBlank()) {
                action.channel = editorChannel;
            }
        }

        AutismChatField rawField = textFields.get("payload_data");
        if (rawField != null) {
            action.payloadData = rawField.getText();
        }
        if (action.payloadData == null || action.payloadData.isBlank()) {
            AutismChatField textField = textFields.get("payload_text");
            if (textField != null && !textField.getText().isBlank()) {
                action.payloadData = AutismPayloadSupport.toHex(textField.getText().getBytes(StandardCharsets.UTF_8));
            }
        }

        AutismChatField javaField = textFields.get("payload_java");
        if (javaField != null) {
            action.javaSource = javaField.getText();
        }

        action.commandApiRecognized = toggleStates.getOrDefault("payload_command_api", false);
        AutismChatField commandField = textFields.get("payload_command_value");
        if (commandField != null) {
            try {
                action.commandApiValue = Integer.parseInt(commandField.getText().strip());
            } catch (NumberFormatException ignored) {  }
        }

        if (payloadJsonEdited && jsonUsable && jsonEncoded != null && !payloadRawEdited && !payloadChannelEdited) {
            try {
                jsonEncoded = AutismPayloadJsonSupport.encodeAction(action);
                action.channel = jsonEncoded.channel();
                action.payloadData = AutismPayloadSupport.toHex(jsonEncoded.bytes());
            } catch (Exception ignored) {  }
        }

        if (action.commandApiRecognized) {
            try {
                byte[] bytes = AutismPayloadSupport.parsePayloadBytes(action.payloadData);
                action.payloadData = AutismPayloadSupport.toHex(AutismPayloadSupport.withCommandApiValue(bytes, action.commandApiValue));
            } catch (Exception ignored) {  }
        }

        action.payloadClassName = "";

        if (action.channel == null || action.channel.isBlank()) {
            action.channel = payloadAction != null && payloadAction.channel != null && !payloadAction.channel.isBlank()
                ? payloadAction.channel
                : "minecraft:brand";
        }
        return action;
    }

    private void syncPayloadCommandFieldFromRaw() {
        AutismChatField rawField = textFields.get("payload_data");
        AutismChatField commandField = textFields.get("payload_command_value");
        AutismChatField channelField = textFields.get("payload_channel");
        if (rawField == null || commandField == null) return;
        try {
            byte[] bytes = AutismPayloadSupport.parsePayloadBytes(rawField.getText());
            Integer value = AutismPayloadSupport.tryParseCommandApiValue(null,
                channelField == null ? "" : channelField.getText(), bytes);
            if (value == null) {
                AutismClientMessaging.sendPrefixed("§cCould not parse a CommandApi integer from the current payload bytes.");
                return;
            }
            toggleStates.put("payload_command_api", true);
            commandField.setText(String.valueOf(value));
        } catch (Exception e) {
            AutismClientMessaging.sendPrefixed("§cInvalid payload data: " + AutismPayloadSupport.safeMessage(e));
        }
    }

    private void syncPayloadRawFieldFromCommand() {
        AutismChatField rawField = textFields.get("payload_data");
        AutismChatField commandField = textFields.get("payload_command_value");
        if (rawField == null || commandField == null) return;
        try {
            byte[] bytes = AutismPayloadSupport.parsePayloadBytes(rawField.getText());
            int value = Integer.parseInt(commandField.getText().strip());
            toggleStates.put("payload_command_api", true);
            rawField.setText(AutismPayloadSupport.toHex(AutismPayloadSupport.withCommandApiValue(bytes, value)));
        } catch (Exception e) {
            AutismClientMessaging.sendPrefixed("§cFailed to sync CommandApi value: " + AutismPayloadSupport.safeMessage(e));
        }
    }

    private void syncPayloadTextFieldFromRaw() {
        AutismChatField rawField = textFields.get("payload_data");
        AutismChatField textField = textFields.get("payload_text");
        if (rawField == null || textField == null) return;
        try {
            byte[] bytes = AutismPayloadSupport.parsePayloadBytes(rawField.getText());
            String text = AutismPayloadSupport.decodeLikelyUtf8Text(bytes);
            if (text.isBlank()) {
                AutismClientMessaging.sendPrefixed("§cThe current payload bytes do not look like readable UTF-8 text.");
                return;
            }
            textField.setText(text);
        } catch (Exception e) {
            AutismClientMessaging.sendPrefixed("§cInvalid payload data: " + AutismPayloadSupport.safeMessage(e));
        }
    }

    private void syncPayloadRawFieldFromText() {
        AutismChatField rawField = textFields.get("payload_data");
        AutismChatField textField = textFields.get("payload_text");
        if (rawField == null || textField == null) return;
        rawField.setText(AutismPayloadSupport.toHex(textField.getText().getBytes(StandardCharsets.UTF_8)));
    }

    private void renderDelayPacketsPresetButtons(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        int btnH = 14;
        int halfW = (w - 4) / 2;
        int x2 = x + halfW + 4;
        renderOverlayButton(ctx, x, y + 2, halfW, btnH, "Default", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
            autismclient.util.macro.DelayPacketsAction tmp = new autismclient.util.macro.DelayPacketsAction();
            tmp.applyDefaultPreset();
            stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
            stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
        });
        renderOverlayButton(ctx, x2, y + 2, halfW, btnH, "Module", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> {
            autismclient.util.macro.DelayPacketsAction tmp = new autismclient.util.macro.DelayPacketsAction();
            tmp.applyModulePreset();
            stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
            stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
        });
    }

    private CompactOverlayButton renderOverlayButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            String label,
            CompactOverlayButton.Variant variant,
            boolean active,
            int mx,
            int my,
            Runnable action
    ) {
        return renderOverlayButton(ctx, x, y, w, h, label, variant, active, mx, my, action, null, null, null);
    }

    private CompactOverlayButton renderOverlayButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            String label,
            CompactOverlayButton.Variant variant,
            boolean active,
            int mx,
            int my,
            Runnable action,
            Runnable secondaryAction
    ) {
        return renderOverlayButton(ctx, x, y, w, h, label, variant, active, mx, my, action, secondaryAction, null, null);
    }

    private CompactOverlayButton renderOverlayToggleButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean toggleState,
            String animationKey,
            int mx,
            int my,
            Runnable action
    ) {
        return renderOverlayButton(
                ctx, x, y, w, h, label,
                toggleState ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER,
                true, mx, my, action, null, toggleState, animationKey
        );
    }

    private CompactOverlayButton renderOverlayButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            String label,
            CompactOverlayButton.Variant variant,
            boolean active,
            int mx,
            int my,
            Runnable action,
            Runnable secondaryAction,
            Boolean toggleState,
            String animationKey
    ) {
        CompactOverlayButton button = CompactOverlayButton.create(
                x,
                y,
                w,
                h,
                Component.literal(label),
                ignored -> action.run(),
                secondaryAction == null ? null : ignored -> secondaryAction.run()
        );
        button.setVariant(variant);
        if (toggleState != null) {
            button.setToggleState(toggleState).setAnimationKey(animationKey);
        }
        button.active = active;
        CompactOverlayButton.renderStyled(ctx, textRenderer, button, mx, my);
        hitRegions.add(new HitRegion(button, action));
        return button;
    }

    private static int macroEditorButtonOutline(CompactOverlayButton.Variant variant, boolean active) {
        if (!active) return 0x88402A30;
        return switch (variant == null ? CompactOverlayButton.Variant.SECONDARY : variant) {
            case SUCCESS -> autismclient.util.AutismTheme.recolor(0xFF35D873, autismclient.util.AutismTheme.Channel.SUCCESS);
            case DANGER -> autismclient.util.AutismTheme.recolor(0xFFFF3B3B, autismclient.util.AutismTheme.Channel.DANGER);
            case PRIMARY, FILTER_ON -> autismclient.util.AutismTheme.recolor(0xFFFF5B5B, autismclient.util.AutismTheme.Channel.BUTTON);
            case GHOST, FILTER_OFF, SECONDARY -> autismclient.util.AutismTheme.recolor(0xFFFF3B3B, autismclient.util.AutismTheme.Channel.OUTLINE);
        };
    }

    private CompactOverlayButton renderFieldCaptureButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            CaptureMode mode,
            boolean capturing,
            boolean active,
            int mx,
            int my,
            Runnable action
    ) {
        return renderFieldCaptureButton(ctx, x, y, w, h, mode, capturing, active, mx, my, null, null, action);
    }

    private CompactOverlayButton renderFieldCaptureButton(
            GuiGraphicsExtractor ctx,
            int x,
            int y,
            int w,
            int h,
            CaptureMode mode,
            boolean capturing,
            boolean active,
            int mx,
            int my,
            String idleLabel,
            String capturingLabel,
            Runnable action
    ) {
        if (!captureControlAvailable(mode)) return null;
        CompactOverlayButton button = MacroCaptureButton.render(
                ctx,
                textRenderer,
                UiBounds.of(x, y, w, h),
                mode,
                capturing,
                active,
                mx,
                my,
                idleLabel,
                capturingLabel,
                action
        );
        hitRegions.add(new HitRegion(button, action));
        return button;
    }

    private void renderIconDeleteButton(GuiGraphicsExtractor ctx, int x, int y, int size, int mx, int my, Runnable action) {
        boolean hovered = mx >= x && mx < x + size && my >= y && my < y + size;
        MacroTypedListControl.renderDelete(ctx, UiBounds.of(x, y, size, size), hovered);
        hitRegions.add(new HitRegion(x, y, size, size, (mouseX, mouseY, mouseButton) -> {
            if (mouseButton != 0) return false;
            action.run();
            return true;
        }));
    }

    private void renderActionButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                    String label, int mx, int my, Runnable action) {
        renderOverlayButton(ctx, x, y, w, h, label, CompactOverlayButton.Variant.SECONDARY, true, mx, my, action);
    }

    private int renderEditorHint(GuiGraphicsExtractor ctx, int x, int y, int w, String text) {
        return renderEditorHint(ctx, x, y, w, text, AutismColors.textDim());
    }

    private int renderEditorHint(GuiGraphicsExtractor ctx, int x, int y, int w, String text, int color) {
        String hint = formatEditorHint(text);
        if (hint.isEmpty()) return y;
        Identifier font = theme.fontFor(UiTone.BODY);
        List<String> lines = wrapEditorHint(hint, Math.max(10, w), font, color);
        int cy = y;
        for (String line : lines) {
            UiText.draw(ctx, textRenderer, line, font, color, x, cy + 2, false);
            cy += EDITOR_HINT_ROW_H;
        }
        return cy;
    }

    private List<String> wrapEditorHint(String hint, int maxWidth, Identifier font, int color) {
        if (hint == null || hint.isBlank()) return List.of();
        String safe = hint.trim().replaceAll("\\s+", " ");
        if (UiText.width(textRenderer, safe, font, color) <= maxWidth) return List.of(safe);
        String[] words = safe.split(" ");
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        for (String word : words) {
            String candidate = first.length() == 0 ? word : first + " " + word;
            if (second.length() == 0 && UiText.width(textRenderer, candidate, font, color) <= maxWidth) {
                first.setLength(0);
                first.append(candidate);
            } else {
                if (second.length() > 0) second.append(' ');
                second.append(word);
            }
        }
        if (second.length() == 0 || first.length() == 0) {
            return List.of(UiText.trimToWidth(textRenderer, safe, maxWidth, font, color));
        }
        String secondLine = UiText.width(textRenderer, second.toString(), font, color) <= maxWidth
            ? second.toString()
            : UiText.trimToWidth(textRenderer, second.toString(), maxWidth, font, color);
        return List.of(first.toString(), secondLine);
    }

    private String formatEditorHint(String text) {
        if (text == null) return "";
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "";
        if (normalized.startsWith("Config-phase payload")) return "Config payload may disconnect.";

        String exact = switch (normalized) {
            case "Editing this row. 0 means drop the full stack from that slot or item." -> "Editing row. 0 = full stack.";
            case "No row selected. The controls below set defaults for new rows you add." -> "No row picked. Controls set new-row defaults.";
            case "Continue when any LAN peer reaches the target step." -> "Waits for any LAN peer step.";
            case "Add peers below to narrow it down. Until then, it waits for any peer at the fallback step." -> "Add peers below or use fallback.";
            case "Each peer below must reach its step before this continues." -> "All listed peers must reach their step.";
            case "No packets selected. This step will continue on the next packet in either direction." -> "No filters: next packet continues.";
            case "This step continues as soon as any selected C2S or S2C packet arrives." -> "Waits for any chosen packet.";
            case "Capture only marks the block. It will not open it." -> "Capture only marks the block.";
            case "0,0,0 uses the current container when open, otherwise the closest nearby one." -> "0,0,0 = current or nearest container.";
            case "Pick on an item adds it by name, from any slot on the screen. Ctrl+pick or an empty slot adds that exact slot." -> "Item = name (any slot). Ctrl/empty = exact slot.";
            case "Exact slots must be chest/custom GUI slots." -> "Exact slots: GUI side only.";
            case "Exact slots must be player inventory slots." -> "Exact slots: your inventory only.";
            case "No payment targets selected yet." -> "No players selected.";
            case "Click a scanned name to toggle it, or use All to add the filtered scan results." -> "Click names to toggle, or use All.";
            case "The search box also lets you manually add a player by pressing Enter." -> "Press Enter to add a typed player.";
            case "Pick modules below, then click the mode chip on each row." -> "Pick modules, then set each mode.";
            case "Each row runs with its own Toggle / Enable / Disable setting." -> "Each row keeps its own mode.";
            case "Capture View fills in your current yaw and pitch." -> "Capture View fills yaw and pitch.";
            case "Wait for Arrival continues only after Baritone finishes." -> "Waits until Baritone arrives.";
            case "Simple disconnect. Delay only controls how long to wait before closing the connection." -> "Simple disconnect after the delay.";
            case "Sends lag packets, then the selected kick packet. Packet Count controls how hard it pushes." -> "Sends lag, then the kick packet.";
            case "Kick Dupe will run the next eligible macro actions inside the lag sandwich, then kick." -> "Runs next actions inside the dupe kick.";
            case "Kick Dupe will try to use a bundle, then kick." -> "Tries bundle dupe, then kicks.";
            case "Automatically detects teleport/world change and disconnects at the perfect timing for dupe exploits." -> "Auto-disconnects at the perfect dupe timing.";
            case "Waits until the current GUI closes." -> "Waits for the GUI to close.";
            case "Waits until a GUI opens." -> "Waits for any GUI to open.";
            case "Blank pattern waits for any incoming server or plugin message." -> "Blank pattern = any server message.";
            case "Blank pattern waits for any incoming chat line." -> "Blank pattern = any chat line.";
            case "Regex is on, so the pattern must match with Java regex rules." -> "Regex mode uses Java regex rules.";
            case "No modules found right now." -> "No modules found.";
            case "Press Scan to load the current server players." -> "Press Scan for players.";
            case "Click a row to use that message." -> "Click a row to use it.";
            case "Pick target blocks, then choose the stop rule you want." -> "Pick blocks, then choose a stop rule.";
            default -> "";
        };
        if (!exact.isEmpty()) return fitEditorHintText(exact);

        normalized = normalized
                .replace("Waits until", "Waits for")
                .replace("Continue when", "Continues when")
                .replace("This step continues as soon as", "Continues when")
                .replace("the selected", "chosen")
                .replace("the current", "this")
                .replace("current ", "")
                .replace("number of times", "times")
                .replace("the chosen number of times", "times")
                .replace("the chosen ticks", "set ticks")
                .replace("only after", "after")
                .replace("It will not", "Won't")
                .replace("selected kick packet", "kick packet")
                .replace("Packet Count controls how hard it pushes", "Packet Count controls force")
                .replace("the open handler", "the open GUI")
                .replace("player inventory", "inventory")
                .replace("inventory slot", "slot")
                .replace("the matched item into", "it into")
                .replace("first matching visible slot", "first visible match")
                .replace("contains an item that matches the optional name", "matches the optional item")
                .replace("becomes empty", "turns empty")
                .replace("reaches the target count", "hits the target count")
                .replace("drops below the target count", "drops below the target")
                .replace("on the first content or count change in that exact slot", "on the first exact slot change")
                .replace("the block under your crosshair", "your target block")
                .replace("the entity under your crosshair", "your target entity")
                .replace("whatever is under your crosshair", "your target")
                .replace("will attack or break", "acts on")
                .replace("will interact with", "uses")
                .replace("the currently selected hotbar item", "the held item");

        return fitEditorHintText(normalized);
    }

    private void applyEditorPlaceholders() {
        if (schema != null) {
            for (FieldDef field : schema.fields()) {
                switch (field.type()) {
                    case TEXT, MACRO_SELECT, NUMBER, DECIMAL, SLOT -> applyTextFieldPlaceholder(field.key(), resolveFieldPlaceholder(field));
                    case BLOCK_POS -> applyBlockPosPlaceholders(field.key());
                    default -> {
                    }
                }
            }
        }

        applyTextFieldPlaceholder("item_guiName", "Any GUI");
        applyTextFieldPlaceholder("drop_guiName", "Any GUI");
        applyTextFieldPlaceholder("lan_defaultStep", "Any step");
        applyTextFieldPlaceholder("item_entrySlot", "Slot");
        applyTextFieldPlaceholder("place_itemSlot", "Slot");
        applyTextFieldPlaceholder("drop_entrySlot", "Slot #");
        applyTextFieldPlaceholder("_craft_amount", "1");
        applyTextFieldPlaceholder("_lan_step_add", "1");
        applyTextFieldPlaceholder("drop_globalCount", "1");
        applyTextFieldPlaceholder("amountInput", "1k");

        applyAddFieldPlaceholder("_item_add", "Any item");
        applyAddFieldPlaceholder("_drop_add", "Any item");
        applyAddFieldPlaceholder("_craft_search", "Find recipe");
        applyAddFieldPlaceholder("_lan_user_add", "Peer name");
        applyAddFieldPlaceholder("soundIds", "Find sound");
        applyAddFieldPlaceholder("entityIds", "Find entity");
        applyAddFieldPlaceholder("targetItems", "Item name");
        applyAddFieldPlaceholder("players", "Find player");
        applyAddFieldPlaceholder("_toggle_module_search", "Find module");
        applyAddFieldPlaceholder("_wait_chat_search", "Find chat");
    }

    private void applyTextFieldPlaceholder(String key, String placeholder) {
        AutismChatField field = textFields.get(key);
        setEditorPlaceholder(field, placeholder);
    }

    private void applyAddFieldPlaceholder(String key, String placeholder) {
        AutismChatField field = addFields.get(key);
        setEditorPlaceholder(field, placeholder);
    }

    private void applyBlockPosPlaceholders(String key) {
        applyTextFieldPlaceholder(key + "_0", "X");
        applyTextFieldPlaceholder(key + "_1", "Y");
        applyTextFieldPlaceholder(key + "_2", "Z");
    }

    private void setEditorPlaceholder(AutismChatField field, String placeholder) {
        if (field == null || placeholder == null || placeholder.isBlank()) return;
        field.setPlaceholder(Component.literal(fitEditorGhostText(placeholder)));
    }

    private String resolveFieldPlaceholder(FieldDef field) {
        if (field == null) return "";
        return switch (field.key()) {
            case "guiName", "waitGuiName" -> "Any GUI";
            case "listFilterText" -> "text to match";
            case "listExcludeText" -> "name1, name2";
            case "pattern" -> "Any chat";
            case "message" -> "Type message";
            case "description" -> "Quick note";
            case "customText" -> "Optional text";
            case "customFilePath" -> "File in book-sources/";
            case "characters" -> "1024";
            case "title" -> targetAction != null && targetAction.getType() == MacroActionType.NBT_BOOK ? "Book title" : "Optional title";
            case "guiTitle" -> "Optional title";
            case "customName" -> "Optional name";
            case "commandTemplate" -> "/pay <p> <amt>";
            case "amountInput" -> "1k";
            case "itemName", "fromItemName", "toItemName" -> "Any item";
            case "moduleName" -> "Pick below";
            case "slot", "fromSlot", "toSlot", "slotNumber", "targetSlot" -> "Slot #";
            case "healthThreshold" -> "0-20";
            case "yaw", "pitch" -> "0";
            case "delayMs" -> "0";
            case "delayTicks" -> "0";
            case "clickCount", "useCount", "holdTicks", "packetCount", "maxDistance", "stopSlotsUsed",
                 "slotsUsedThreshold", "minedCountTarget", "timeoutSeconds", "durationTicks",
                 "tickOffset", "preGenCount", "revisionOffset", "maxWaitMs", "bufferMs" -> "1";
            default -> switch (field.type()) {
                case TEXT, MACRO_SELECT -> fitEditorGhostText(field.label());
                case SLOT -> "Slot #";
                case NUMBER, DECIMAL -> "0";
                default -> "";
            };
        };
    }

    private String fitEditorGhostText(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= EDITOR_GHOST_MAX_CHARS) return normalized;
        int cutoff = normalized.lastIndexOf(' ', EDITOR_GHOST_MAX_CHARS - 3);
        if (cutoff < 5) cutoff = EDITOR_GHOST_MAX_CHARS - 3;
        return normalized.substring(0, Math.max(1, cutoff)).trim() + "...";
    }

    private String fitEditorHintText(String text) {

        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private void renderRow(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                           int mx, int my, float delta) {
        if (isGuiWaitBeforeKey(field.key())) {
            renderGuiWaitRow(ctx, theme.fontFor(UiTone.BODY), field.key(), "waitForGuiAfter", x, y, w, mx, my);
            return;
        }
        if ("safeClickDelayTicks".equals(field.key())) {
            renderXCarrySafeDelaySlider(ctx, x, y, w, mx, my);
            return;
        }
        switch (field.type()) {
            case TOGGLE      -> renderToggle   (ctx, field, x, y, w, mx, my);
            case NUMBER,
                 DECIMAL,
                 TEXT,
                 MACRO_SELECT,
                 SLOT        -> renderTextField(ctx, field, x, y, w, mx, my, delta);
            case ENUM        -> renderEnum     (ctx, field, x, y, w, mx, my);
            case BLOCK_POS   -> renderBlockPos (ctx, field, x, y, w, mx, my, delta);
            case STRING_LIST -> {
                if (isRacePickerList(field))
                    renderRacePickerList(ctx, field, x, y, w, mx, my);
                else if (isMultiAccountList(field))
                    renderMultiAccountList(ctx, field, x, y, w, mx, my);
                else if (field.captureMode() == CaptureMode.BLOCK_CATALOG)
                    renderStringListCatalog(ctx, field, x, y, w, mx, my, delta);
                else
                    renderStringList(ctx, field, x, y, w, mx, my, delta);
            }
            case TARGET_SUMMARY -> renderTargetSummary(ctx, field, x, y, w);
            case CAPTURE_BUTTON -> renderCaptureButton(ctx, field, x, y, w, mx, my);
            case CONDITION      -> renderConditionSummary(ctx, field, x, y, w);
        }
    }

    private void renderConditionSummary(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int lw = labelWidth(w, field.label(), font, 56);
        drawLabel(ctx, field.label(), x, y, lw, font);

        String summary = liveConditionSummary(field.key());
        int valX = controlX(x, lw);
        int valW = controlWidth(w, lw);
        int color = AutismColors.textPrimary();
        String trimmed = UiText.width(textRenderer, summary, font, color) > valW
                ? UiText.trimToWidth(textRenderer, summary, Math.max(8, valW - 4), font, color)
                : summary;
        UiText.draw(ctx, textRenderer, trimmed, font, color, valX, y + 2, false);
    }

    private String liveConditionSummary(String conditionKey) {
        CompoundTag stored = (workingTag != null && workingTag.get(conditionKey) instanceof CompoundTag c) ? c : null;
        boolean hasFlatEditor = getField("cond_kind") != null;
        boolean flatEditable = hasFlatEditor;
        if (hasFlatEditor && stored != null) {
            try {
                flatEditable = autismclient.util.macro.MacroCondition.fromTag(stored).simpleLeafOrNull() != null;
            } catch (Throwable ignored) {
                flatEditable = true;
            }
        }
        if (flatEditable && hasFlatEditor) {
            CompoundTag flat = new CompoundTag();
            flat.putString("cond_kind", currentEnumValue("cond_kind"));
            flat.putBoolean("cond_negate", toggleStates.getOrDefault("cond_negate", false));
            flat.putString("cond_item", conditionFieldText("cond_item"));
            flat.putString("cond_op", currentEnumValue("cond_op"));
            flat.putString("cond_value", conditionFieldText("cond_value"));
            flat.putString("cond_cmp", currentEnumValue("cond_cmp"));
            flat.putInt("cond_amount", conditionFieldInt("cond_amount", 0));
            flat.putInt("cond_slot", conditionFieldInt("cond_slot", 0));
            try {
                return autismclient.util.macro.MacroCondition.readFlatLeaf(flat, "cond_").summary();
            } catch (Throwable ignored) {  }
        }
        if (stored != null) {
            try { return autismclient.util.macro.MacroCondition.fromTag(stored).summary(); }
            catch (Throwable ignored) {  }
        }
        return "always";
    }

    private String conditionFieldText(String key) {
        AutismChatField f = textFields.get(key);
        return f == null ? "" : f.getText();
    }

    private int conditionFieldInt(String key, int fallback) {
        AutismChatField f = textFields.get(key);
        if (f == null) return fallback;
        try { return Integer.parseInt(f.getText().strip()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void renderPlaceItemRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                                    int mx, int my, float delta) {
        AutismChatField nameF = textFields.get("itemName");
        AutismChatField slotF = textFields.get("place_itemSlot");
        if (nameF == null || slotF == null) return;

        int pickW = 32;
        int slotW = 26;
        int gap = 3;
        int pickX = x + w - pickW;
        int slotX = pickX - gap - slotW;
        int nameW = Math.max(1, slotX - x - 2);

        nameF.setX(x);
        nameF.setY(y + 1);
        nameF.setWidth(nameW);
        nameF.render(ctx, mx, my, delta);

        slotF.setX(slotX);
        slotF.setY(y + 1);
        slotF.setWidth(slotW);
        slotF.render(ctx, mx, my, delta);

        boolean capt = captureSession.isItemSlotCapture("itemName");
        renderFieldCaptureButton(
                ctx,
                pickX,
                y,
                pickW,
                14,
                CaptureMode.ITEM_SLOT,
                capt,
                true,
                mx,
                my,
                () -> toggleItemSlotCapture("itemName")
        );
    }

    private void renderTargetSummary(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int lw = labelWidth(w, field.label(), font, 56);
        drawLabel(ctx, field.label(), x, y, lw, font);

        String summary = resolvePacketClickTargetSummary();
        int valX = controlX(x, lw);
        int valW = controlWidth(w, lw);
        int color = summary == null || summary.isEmpty()
                ? AutismColors.textSecondary()
                : AutismColors.textPrimary();
        String display = (summary == null || summary.isEmpty()) ? "Not captured" : summary;
        String trimmed = UiText.width(textRenderer, display, font, color) > valW
                ? UiText.trimToWidth(textRenderer, display, Math.max(8, valW - 4), font, color)
                : display;
        UiText.draw(ctx, textRenderer, trimmed, font, color, valX, y + 2, false);
    }

    private void renderCaptureButton(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int btnW = 60;
        int btnH = 14;
        int lw = labelWidth(w, field.label(), font, btnW);
        drawLabel(ctx, field.label(), x, y, lw, font);

        boolean capturing = field.key().equals(packetClickCapturePendingKey);
        int btnX = x + w - btnW;
        renderFieldCaptureButton(
                ctx,
                btnX,
                y + 2,
                btnW,
                btnH,
                CaptureMode.PACKET_CLICK_TARGET,
                capturing,
                true,
                mx,
                my,
                "Re-capture",
                "Click slot...",
                () -> beginPacketClickCapture(field.key())
        );
    }

    private String resolvePacketClickTargetSummary() {
        if (!(targetAction instanceof autismclient.util.macro.PacketClickAction pca)) return null;
        if (pca.target == null) return null;
        return pca.target.withMode(currentPacketClickMode()).summary();
    }

    private boolean isRacePickerList(FieldDef field) {
        return targetAction != null
                && targetAction.getType() == MacroActionType.RACE
                && field != null
                && "raceSteps".equals(field.key());
    }

    private boolean isMultiAccountList(FieldDef field) {

        if (autismclient.util.AutismLiteVariant.enabled()) return false;
        return targetAction != null
                && targetAction.getType() == MacroActionType.MULTI
                && field != null
                && "accounts".equals(field.key());
    }

    private void renderRacePickerList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
        String key = field.key();
        List<String> values = stringLists.computeIfAbsent(key, ignored -> new ArrayList<>());
        Identifier font = theme.fontFor(UiTone.BODY);

        UiText.draw(ctx, textRenderer, field.label() + " (" + values.size() + ")", font,
                AutismColors.textSecondary(), x, y + 2, false);

        int addY = y + 13;
        int gap = 4;
        int btnW = Math.max(60, (w - gap) / 2);
        renderOverlayButton(ctx, x, addY, btnW, 14, "+ Condition", CompactOverlayButton.Variant.PRIMARY,
                true, mx, my, () -> openRaceStepSelector(values, true));
        renderOverlayButton(ctx, x + btnW + gap, addY, Math.max(60, w - btnW - gap), 14, "+ Action", CompactOverlayButton.Variant.SUCCESS,
                true, mx, my, () -> openRaceStepSelector(values, false));
        int listY = addY + 18;
        int rowH = 18;
        int visibleRows = SEL_LIST_MAX_VIS;
        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, key + "_race",
                x, listY, w, visibleRows * rowH, rowH, SCROLLBAR_W);
        viewport.setContentHeight(values.size() * rowH);
        viewport.renderScrollbar(ctx, mx, my);

        if (values.isEmpty()) {
            String hint = "Use +Cond for waits or +Act for batch actions.";
            CompactListRenderer.drawEmptyState(ctx, textRenderer, hint, x, listY, w - SCROLLBAR_W - 1);
            return;
        }

        int buttonW = 14;
        int rowGap = 2;
        int buttonArea = (buttonW * 3) + (rowGap * 2);
        int rowTextW = Math.max(20, w - SCROLLBAR_W - buttonArea - 7);
        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = viewport.getFirstVisibleRow();
        for (int vi = first; vi < values.size() && vi <= viewport.getLastVisibleRow(); vi++) {
            int rowY = viewport.getRowScreenY(vi);
            if (rowY == Integer.MIN_VALUE) continue;
            String value = values.get(vi);
            boolean hovered = mx >= x && mx < x + rowTextW && my >= rowY && my < rowY + rowH;
            renderRaceStepRow(ctx, x, rowY, rowTextW, rowH, vi, value, hovered);
            int bx = x + rowTextW + 3;
            int rowIndex = vi;
            renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "^",
                    false, rowIndex > 0, () -> Collections.swap(values, rowIndex, rowIndex - 1));
            bx += buttonW + rowGap;
            renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "v",
                    false, rowIndex + 1 < values.size(), () -> Collections.swap(values, rowIndex, rowIndex + 1));
            bx += buttonW + rowGap;
            boolean canDelete = raceActionCount(values) > 1 || !isRaceActionStep(value);
            renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "X",
                    true, canDelete, () -> {
                        if (rowIndex >= 0 && rowIndex < values.size()
                                && (raceActionCount(values) > 1 || !isRaceActionStep(values.get(rowIndex)))) {
                            values.remove(rowIndex);
                        }
                    });
        }
        viewport.endRender(ctx);
    }

    private void renderRaceStepRow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int index, String step, boolean hovered) {
        String canonical = autismclient.util.macro.RaceAction.canonicalRaceStep(step);
        boolean condition = autismclient.util.macro.RaceAction.isConditionStep(canonical);
        int bgColor = hovered ? AutismColors.rowHover() : AutismColors.rowNormal();
        CompactSurfaces.tintedRow(ctx, x, y + 1, w, h - 2, bgColor);
        int badgeColor = condition ? -30720 : -12268476;
        CompactSurfaces.indicator(ctx, x + 1, y + 3, 3, h - 6, badgeColor);
        int numberColor = AutismColors.textDim();
        UiText.draw(ctx, textRenderer, String.valueOf(index + 1), theme.fontFor(UiTone.BODY), numberColor,
                x + 8, UiSizing.alignTextY(y, h, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge()), false);
        String role = condition ? "COND" : "ACT";
        int roleColor = condition ? AutismColors.packetCyan() : AutismColors.textSecondary();
        int roleX = x + 24;
        UiText.draw(ctx, textRenderer, role, theme.fontFor(UiTone.LABEL), roleColor,
                roleX, UiSizing.alignTextY(y, h, theme.fontHeight(UiTone.LABEL), theme.bodyTextNudge()), false);
        String label = raceStepDisplay(step).replaceFirst("^(COND|ACT)\\s+", "");
        int labelX = roleX + 34;
        String trimmed = UiText.trimToWidth(textRenderer, label, Math.max(1, x + w - labelX - 4),
                theme.fontFor(UiTone.BODY), -1);
        UiText.draw(ctx, textRenderer, trimmed, theme.fontFor(UiTone.BODY), -1,
                labelX, UiSizing.alignTextY(y, h, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge()), false);
    }

    private void renderRaceListSymbolButton(GuiGraphicsExtractor ctx, int x, int y, int size, int mx, int my,
                                          String symbol, boolean danger, boolean active, Runnable action) {
        boolean hovered = active && mx >= x && mx < x + size && my >= y && my < y + size;
        MacroTypedListControl.renderSymbol(ctx, UiBounds.of(x, y, size, size), symbol, hovered, danger);
        hitRegions.add(new HitRegion(x, y, size, size, (mouseX, mouseY, mouseButton) -> {
            if (!active || mouseButton != 0) return false;
            action.run();
            return true;
        }));
    }

    private void openRaceStepSelector(List<String> values, boolean condition) {
        List<RaceStepSelectorOverlay.Option> options = condition
                ? raceSelectorOptions(RACE_CONDITION_CHOICES, "CONDITION")
                : raceSelectorOptions(RACE_ACTION_CHOICES, "ACTION");
        raceStepSelectorOverlay.open(condition ? "Condition Selector" : "Action Selector", options, option -> {
            if (option == null || option.id() == null || option.id().isBlank()) return;
            values.add((condition ? "CONDITION:" : "ACTION:") + option.id());
            refreshInteractiveLayout();
        });
    }

    private List<RaceStepSelectorOverlay.Option> raceSelectorOptions(List<String> choices, String role) {
        ArrayList<RaceStepSelectorOverlay.Option> out = new ArrayList<>();
        for (String choice : choices) {
            String[] parts = choice == null ? new String[0] : choice.split("/", 2);
            String category = parts.length > 1 ? parts[0].trim() : role;
            String id = raceChoiceValue(choice);
            if (id.isBlank()) continue;
            String label = id.replace('_', ' ');
            out.add(new RaceStepSelectorOverlay.Option(category, id, label, role));
        }
        return out;
    }

    private String raceStepDisplay(String step) {
        String canonical = autismclient.util.macro.RaceAction.canonicalRaceStep(step);
        String type = autismclient.util.macro.RaceAction.stepTypeName(canonical);
        return (autismclient.util.macro.RaceAction.isConditionStep(canonical) ? "COND  " : "ACT   ") + type;
    }

    private boolean isRaceActionStep(String step) {
        return autismclient.util.macro.RaceAction.isActionStep(autismclient.util.macro.RaceAction.canonicalRaceStep(step));
    }

    private int raceActionCount(List<String> values) {
        int count = 0;
        if (values != null) {
            for (String value : values) {
                if (isRaceActionStep(value)) count++;
            }
        }
        return count;
    }

    private String raceChoiceValue(String choice) {
        if (choice == null) return "";
        int slash = choice.lastIndexOf('/');
        return (slash >= 0 ? choice.substring(slash + 1) : choice).trim();
    }

    private void renderMultiAccountList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w, int mx, int my) {
        String key = field.key();
        List<String> values = stringLists.computeIfAbsent(key, ignored -> new ArrayList<>());
        Identifier font = theme.fontFor(UiTone.BODY);

        UiText.draw(ctx, textRenderer, field.label() + " (" + values.size() + ")", font,
                AutismColors.textSecondary(), x, y + 2, false);

        int addY = y + 13;
        int gap = 4;
        int smallW = 34;
        renderOverlayButton(ctx, x, addY, Math.max(60, w - (smallW + gap) * 2), 14, "+ Accounts", CompactOverlayButton.Variant.PRIMARY,
                true, mx, my, () -> openMultiAccountSelector(values));
        renderOverlayButton(ctx, x + Math.max(60, w - (smallW + gap) * 2) + gap, addY, smallW, 14, "All",
                CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
                    java.util.List<autismclient.util.multi.MultiProfile.SessionSpec> specs = multiSessionSpecs();
                    if (specs.isEmpty()) return;
                    values.clear();
                    for (autismclient.util.multi.MultiProfile.SessionSpec spec : specs) {
                        String accountId = spec.accountId();
                        if (accountId != null && !accountId.isBlank() && !values.contains(accountId)) values.add(accountId);
                    }
                });
        renderOverlayButton(ctx, x + w - smallW, addY, smallW, 14, "None",
                CompactOverlayButton.Variant.SECONDARY, !values.isEmpty(), mx, my, values::clear);

        int listY = addY + 18;
        int rowH = 18;
        int visibleRows = SEL_LIST_MAX_VIS;
        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, key + "_multi",
                x, listY, w, visibleRows * rowH, rowH, SCROLLBAR_W);
        viewport.setContentHeight(values.size() * rowH);
        viewport.renderScrollbar(ctx, mx, my);

        if (values.isEmpty()) {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No accounts selected. Use + Accounts.", x, listY, w - SCROLLBAR_W - 1);
            return;
        }

        int buttonW = 14;
        int rowTextW = Math.max(20, w - SCROLLBAR_W - buttonW - 7);
        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = viewport.getFirstVisibleRow();
        for (int vi = first; vi < values.size() && vi <= viewport.getLastVisibleRow(); vi++) {
            int rowY = viewport.getRowScreenY(vi);
            if (rowY == Integer.MIN_VALUE) continue;
            boolean hovered = mx >= x && mx < x + rowTextW && my >= rowY && my < rowY + rowH;
            renderMultiAccountRow(ctx, x, rowY, rowTextW, rowH, vi, values.get(vi), hovered);
            int bx = x + rowTextW + 3;
            int rowIndex = vi;
            renderRaceListSymbolButton(ctx, bx, rowY + 2, buttonW, mx, my, "X",
                    true, true, () -> {
                        if (rowIndex >= 0 && rowIndex < values.size()) values.remove(rowIndex);
                    });
        }
        viewport.endRender(ctx);
    }

    private void renderMultiAccountRow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int index, String accountId, boolean hovered) {
        int bgColor = hovered ? AutismColors.rowHover() : AutismColors.rowNormal();
        CompactSurfaces.tintedRow(ctx, x, y + 1, w, h - 2, bgColor);
        CompactSurfaces.indicator(ctx, x + 1, y + 3, 3, h - 6, -12268476);
        UiText.draw(ctx, textRenderer, String.valueOf(index + 1), theme.fontFor(UiTone.BODY), AutismColors.textDim(),
                x + 8, UiSizing.alignTextY(y, h, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge()), false);

        String label = multiAccountDisplayName(accountId);
        int labelX = x + 24;
        String trimmed = UiText.trimToWidth(textRenderer, label, Math.max(1, x + w - labelX - 4),
                theme.fontFor(UiTone.BODY), -1);
        UiText.draw(ctx, textRenderer, trimmed, theme.fontFor(UiTone.BODY), -1,
                labelX, UiSizing.alignTextY(y, h, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge()), false);
    }

    private List<autismclient.util.multi.MultiProfile.SessionSpec> multiSessionSpecs() {
        autismclient.util.multi.MultiManager manager = autismclient.util.multi.MultiManager.getIfInitialized();
        autismclient.util.multi.MultiProfile profile = manager != null ? manager.activeProfile() : null;
        if (profile == null) return List.of();
        return profile.sessions;
    }

    private String multiAccountDisplayName(String accountId) {
        autismclient.util.multi.MultiManager manager = autismclient.util.multi.MultiManager.getIfInitialized();
        if (manager != null && accountId != null) {
            for (autismclient.util.multi.MultiSession.Snapshot snapshot : manager.snapshots()) {
                if (snapshot != null && accountId.equals(snapshot.accountId())
                        && snapshot.accountName() != null && !snapshot.accountName().isBlank()) {
                    return snapshot.accountName();
                }
            }
        }
        return accountId == null ? "" : accountId;
    }

    private void openMultiAccountSelector(List<String> values) {
        List<RaceStepSelectorOverlay.Option> options = new ArrayList<>();
        for (autismclient.util.multi.MultiProfile.SessionSpec spec : multiSessionSpecs()) {
            String accountId = spec.accountId();
            if (accountId == null || accountId.isBlank() || values.contains(accountId)) continue;
            StringBuilder description = new StringBuilder();
            if (spec.proxyId() != null && !spec.proxyId().isBlank()) description.append("proxy: ").append(spec.proxyId());
            if (spec.macroName() != null && !spec.macroName().isBlank()) {
                if (description.length() > 0) description.append("  ");
                description.append("macro: ").append(spec.macroName());
            }
            options.add(new RaceStepSelectorOverlay.Option("Accounts", accountId, multiAccountDisplayName(accountId), description.toString()));
        }
        raceStepSelectorOverlay.open("Account Selector", options, option -> {
            if (option == null || option.id() == null || option.id().isBlank()) return;
            values.add(option.id());
            refreshInteractiveLayout();
        });
    }

    private void beginPacketClickCapture(String fieldKey) {
        if (!(targetAction instanceof autismclient.util.macro.PacketClickAction)) return;
        packetClickCapturePendingKey = fieldKey;
        autismclient.util.AutismContainerHold.setPendingCapture(target -> applyPacketClickCapture(target));
        enterCaptureMode();
    }

    private void applyPacketClickCapture(autismclient.util.AutismPacketClick.Target target) {
        if (target == null) return;
        if (targetAction instanceof autismclient.util.macro.PacketClickAction pca) {
            autismclient.util.AutismPacketClick.Mode mode = currentPacketClickMode();
            pca.mode = mode.name();
            pca.setTarget(target.withMode(mode));
            if (workingTag != null) {
                workingTag.putString("mode", mode.name());
                workingTag.put("target", target.withMode(mode).toTag());
            }
        }
        packetClickCapturePendingKey = null;
        exitCaptureMode(false, false);
    }

    private autismclient.util.AutismPacketClick.Mode currentPacketClickMode() {
        FieldDef modeField = fieldByKey("mode");
        if (modeField != null && modeField.type() == FieldType.ENUM && !modeField.enumOptions().isEmpty()) {
            int idx = Math.max(0, Math.min(enumIndices.getOrDefault("mode", 0), modeField.enumOptions().size() - 1));
            return autismclient.util.AutismPacketClick.Mode.fromName(modeField.enumOptions().get(idx));
        }
        return autismclient.util.AutismPacketClick.Mode.fromName(workingTag == null ? "" : workingTag.getStringOr("mode", ""));
    }

    private FieldDef fieldByKey(String key) {
        if (schema == null || key == null) return null;
        for (FieldDef field : schema.fields()) {
            if (key.equals(field.key())) return field;
        }
        return null;
    }

    public boolean wantsPacketClickCapture() {
        return packetClickCapturePendingKey != null
                && autismclient.util.AutismContainerHold.hasPendingCapture();
    }

    public boolean cancelPacketClickCaptureIfActive() {
        if (packetClickCapturePendingKey == null) return false;
        autismclient.util.AutismContainerHold.clearPendingCapture();
        packetClickCapturePendingKey = null;
        exitCaptureMode(false, false);
        return true;
    }

    private boolean isGuiWaitBeforeKey(String key) {
        return "waitForGuiBefore".equals(key);
    }

    private boolean isGuiWaitAfterKey(String key) {
        return "waitForGuiAfter".equals(key);
    }

    private void renderToggle(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                              int mx, int my) {
        String key = field.key();
        boolean val = toggleStates.getOrDefault(key, false);
        Identifier font = theme.fontFor(UiTone.BODY);

        int btnW = 34;
        int btnH = 14;
        String label = labelFor(field);
        int lw = labelWidth(w, label, font, btnW);
        drawLabel(ctx, label, x, y, lw, font);

        int btnX = x + w - btnW;
        renderOverlayToggleButton(
                ctx,
                btnX,
                y + 2,
                btnW,
                btnH,
                val ? "ON" : "OFF",
                val,
                "macro-field:" + key,
                mx,
                my,
                () -> {
            boolean nowOn = !toggleStates.getOrDefault(key, false);
            toggleStates.put(key, nowOn);
            if ("waitForTrigger".equals(key)) captureWaitTouched = true;
            if (workingTag != null) {
                workingTag.putBoolean(key, nowOn);
            }
            if (nowOn && field.hasMutualExclusion()) {
                for (String other : field.mutuallyExclusiveWith()) {
                    toggleStates.put(other, false);
                    if (workingTag != null) {
                        workingTag.putBoolean(other, false);
                    }
                }
            }
        });
    }

    private void renderTextField(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                                 int mx, int my, float delta) {
        String key = field.key();
        AutismChatField tf = textFields.get(key);
        if (tf == null) return;
        Identifier font = theme.fontFor(UiTone.BODY);

        if (field.type() == FieldType.MACRO_SELECT) {
            renderMacroSelector(ctx, field, tf, x, y, w, mx, my);
            return;
        }

        if (isWaitChatPatternField(field)) {
            UiText.draw(ctx, textRenderer, field.label(), font,
                    AutismColors.textSecondary(), x, y + 2, false);
            tf.setX(x);
            tf.setY(y + 13);
            int variableW = shouldShowVariableButton(field.key()) ? 24 : 0;
            tf.setWidth(Math.max(1, w - (variableW > 0 ? variableW + 2 : 0)));
            tf.setHeight(WAIT_CHAT_PATTERN_H);
            tf.render(ctx, mx, my, delta);
            if (variableW > 0) renderVariableDropdown(field, tf, x + w - variableW, y + 13, variableW);
            return;
        }

        boolean packetPick = field.captureMode() == CaptureMode.PACKET_NAME;
        boolean slotPick = field.type() == FieldType.SLOT && worldCaptureUsable();
        boolean itemSlotPick = field.captureMode() == CaptureMode.ITEM_SLOT && worldCaptureUsable();
        boolean menuButtonPick = field.captureMode() == CaptureMode.CUSTOM_MENU_BUTTON;
        boolean nbtBookFilePick = targetAction != null
                && targetAction.getType() == MacroActionType.NBT_BOOK
                && "customFilePath".equals(key);
        int lw = labelWidth(w, field.label(), font, itemSlotPick || packetPick || slotPick || menuButtonPick ? 88 : 56);
        drawLabel(ctx, field.label(), x, y, lw, font);

        int tfX = controlX(x, lw);
        int variableW = shouldShowVariableButton(key) ? 24 : 0;

        if (menuButtonPick) {

            int pickW = 34;
            int availableW = Math.max(1, controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
            int tfW = Math.max(1, availableW - pickW - 2);
            tf.setX(tfX); tf.setY(y + 2); tf.setWidth(tfW);
            tf.render(ctx, mx, my, delta);
            final String fKey = key;
            renderFieldCaptureButton(ctx, tfX + tfW + 2, y + 2, pickW, 14, CaptureMode.CUSTOM_MENU_BUTTON,
                    false, true, mx, my, () -> pickCustomMenuButtonInto(fKey));
            if (variableW > 0) renderVariableDropdown(field, tf, tfX + tfW + pickW + 4, y + 2, variableW);
        } else if (nbtBookFilePick) {
            int pickW = 30;
            int availableW = Math.max(1, controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
            int tfW = Math.max(1, availableW - pickW - 2);
            tf.setX(tfX);
            tf.setY(y + 2);
            tf.setWidth(tfW);
            tf.render(ctx, mx, my, delta);
            renderOverlayButton(ctx, tfX + tfW + 2, y + 2, pickW, 14, "Pick",
                    CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> pickNbtBookTextFile(tf));
            if (variableW > 0) renderVariableDropdown(field, tf, tfX + tfW + pickW + 4, y + 2, variableW);
        } else if (itemSlotPick || packetPick || slotPick) {

            int pickW  = 30;
            int availableW = Math.max(1, controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
            int tfW = slotPick
                    ? Math.max(26, availableW - pickW - 2)
                    : Math.max(1, availableW - pickW - 2);
            tf.setX(tfX); tf.setY(y + 2); tf.setWidth(tfW);
            tf.render(ctx, mx, my, delta);

            int pkX       = tfX + tfW + 2;
            boolean capt  = !packetPick && captureSession.isItemSlotCapture(key);
            final String fKey = key;
            renderFieldCaptureButton(
                    ctx,
                    pkX,
                    y + 2,
                    pickW,
                    14,
                    packetPick ? CaptureMode.PACKET_NAME : CaptureMode.ITEM_SLOT,
                    capt,
                    true,
                    mx,
                    my,
                    () -> {
                        if (packetPick) {
                            openPacketNameFieldSelector(fKey);
                            return;
                        }
                        toggleItemSlotCapture(fKey);
                    }
            );
            if (variableW > 0) renderVariableDropdown(field, tf, pkX + pickW + 2, y + 2, variableW);
        } else {
            int tfW = Math.max(1, controlWidth(w, lw) - (variableW > 0 ? variableW + 2 : 0));
            tf.setX(tfX); tf.setY(y + 2); tf.setWidth(tfW);
            tf.render(ctx, mx, my, delta);
            if (variableW > 0) renderVariableDropdown(field, tf, tfX + tfW + 2, y + 2, variableW);
        }
    }

    private void pickCustomMenuButtonInto(String key) {
        autismclient.api.custommenu.CustomMenuSnapshot menu = autismclient.util.custommenu.CustomMenuTracker.current();
        if (menu == null) {
            AutismNotifications.warning("No custom screen is open to pick a button from.");
            return;
        }
        java.util.List<autismclient.api.custommenu.CustomMenuButton> buttons = new ArrayList<>();
        for (autismclient.api.custommenu.CustomMenuButton button : menu.buttons()) {
            if (button.serverRelevant() && !button.label().isBlank()) buttons.add(button);
        }
        if (buttons.isEmpty()) {
            AutismNotifications.warning("This screen has no pressable buttons.");
            return;
        }
        AutismChatField tf = textFields.get(key);
        String current = tf == null ? "" : tf.getText().trim();
        int next = 0;
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).label().equalsIgnoreCase(current)) { next = (i + 1) % buttons.size(); break; }
        }
        String label = buttons.get(next).label();
        if (tf != null) tf.setText(label);
        AutismNotifications.show("Button set to \"" + label + "\""
                + (buttons.size() > 1 ? " (Pick again to cycle)" : ""), 0xFF57F287);
    }

    private void renderVariableDropdown(FieldDef field, AutismChatField textField, int x, int y, int width) {
        if (field == null) return;
        renderVariableDropdown(field.key(), textField, x, y, width);
    }

    private void renderVariableDropdown(String key, AutismChatField textField, int x, int y, int width) {
        if (!shouldShowVariableButton(key) || textField == null) return;
        String cacheKey = "__vars_" + key;
        CompactDropdown dropdown = enumDropdownCache.computeIfAbsent(cacheKey,
            ignored -> new CompactDropdown(x, y, width, 14, variableTokens, 0, index -> {}));
        dropdown.setBounds(x, y, width, 14)
            .setOptions(variableTokens)
            .setButtonLabelOverride("{}")
            .setOnSelect(index -> {
                if (index >= 0 && index < variableTokens.size()) textField.insertText(variableTokens.get(index));
                dropdown.close();
            });
        enumDropdowns.add(dropdown);
    }

    private boolean shouldShowVariableButton(String key) {
        return targetAction != null
            && targetAction.getType() == MacroActionType.SEND_CHAT
            && "message".equals(key)
            && !variableTokens.isEmpty();
    }

    private static final List<String> DEFAULT_VARIABLE_TOKENS = List.of(
        "{user}", "{pos}", "{rot}", "{x}", "{y}", "{z}",
        "{uuid}", "{dimension}", "{facing}", "{server}", "{selected_slot}", "{timestamp}"
    );

    private void rebuildVariableTokens() {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        if (variableMacro != null && variableMacro.actions != null) {
            int limit = variableActionIndex < 0 ? variableMacro.actions.size() : Math.min(variableActionIndex, variableMacro.actions.size());
            for (int i = limit - 1; i >= 0 && tokens.size() < 4; i--) {
                addCaptureValueTokens(tokens, variableMacro.actions.get(i), 4);
            }
        }
        tokens.addAll(DEFAULT_VARIABLE_TOKENS);
        variableTokens.clear();
        variableTokens.addAll(tokens);
    }

    private static void addCaptureValueTokens(java.util.Set<String> tokens, MacroAction action, int limit) {
        if (!(action instanceof autismclient.util.macro.CaptureValueAction capture) || tokens.size() >= limit) return;
        java.util.Set<String> names = autismclient.util.macro.MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern);
        for (String name : names) {
            if (tokens.size() >= limit) return;
            tokens.add("{" + name + "}");
        }
        if (tokens.size() < limit) {
            String root = autismclient.util.macro.MacroVariableContext.cleanRootName(capture.saveAs);
            if (!root.isBlank()) tokens.add("{" + root + "}");
        }
    }

    private static void addActionVariableTokens(java.util.Set<String> tokens, MacroAction action) {
        if (action instanceof autismclient.util.macro.WaitForChatAction chat) {
            for (String name : autismclient.util.macro.MacroCapturePattern.declaredNames(chat.effectiveMatchMode(), chat.pattern)) tokens.add("{" + name + "}");
            addStructuredTokens(tokens, chat.saveAs, "sender", "message", "display", "source");
        } else if (action instanceof autismclient.util.macro.WaitForGuiAction gui) {
            for (String name : autismclient.util.macro.MacroCapturePattern.declaredNames(gui.matchMode, gui.guiTitle)) tokens.add("{" + name + "}");
            addStructuredTokens(tokens, gui.saveAs, "title", "type");
        } else if (action instanceof autismclient.util.macro.CaptureValueAction capture) {
            for (String name : autismclient.util.macro.MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) tokens.add("{" + name + "}");
            switch (capture.source) {
                case GUI_TITLE -> addStructuredTokens(tokens, capture.saveAs, "title", "type");
                case RECENT_CHAT -> addStructuredTokens(tokens, capture.saveAs, "sender", "message", "display", "source");
                case SCOREBOARD -> addStructuredTokens(tokens, capture.saveAs, "line", "name", "score", "row", "title", "objective");
                case GUI_ITEM, PLAYER_ITEM, CURSOR_ITEM, HELD_ITEM -> addStructuredTokens(tokens, capture.saveAs, "name", "id", "count", "slot", "lore", "lore1");
            }
        } else if (action instanceof autismclient.util.macro.SelectSlotAction select && select.outputVariable != null && !select.outputVariable.isBlank()) {
            tokens.add("{" + select.outputVariable.trim() + "}");
        } else if (action instanceof autismclient.util.macro.MacroVariablesAction variables) {
            for (String name : variables.names) if (name != null && !name.isBlank()) tokens.add("{" + name.trim() + "}");
        } else if (action instanceof autismclient.util.macro.MacroCaptureOutput output) {
            String root = output.getSaveAs();
            if (action instanceof autismclient.util.macro.WaitForHealthAction) {
                addStructuredTokens(tokens, root, "health", "max", "absorption");
            } else if (action instanceof autismclient.util.macro.WaitForEntityAction) {
                addStructuredTokens(tokens, root, "name", "id", "uuid", "x", "y", "z", "distance");
            } else if (action instanceof autismclient.util.macro.WaitForSoundAction) {
                addStructuredTokens(tokens, root, "id", "x", "y", "z");
            } else if (action instanceof autismclient.util.macro.WaitForPacketAction
                    || action instanceof autismclient.util.macro.WaitPacketMatchAction) {
                addStructuredTokens(tokens, root, "name", "class", "direction");
                if (action instanceof autismclient.util.macro.WaitPacketMatchAction packetMatch) {
                    String cleanRoot = autismclient.util.macro.MacroVariableContext.cleanRootName(root);
                    if (!cleanRoot.isBlank()) {
                        for (autismclient.util.macro.WaitPacketMatchAction.Rule rule : packetMatch.effectiveRules()) {
                            if (rule.fieldName != null && !rule.fieldName.isBlank()) {
                                tokens.add("{" + cleanRoot + "." + rule.fieldName.trim().toLowerCase(Locale.ROOT) + "}");
                            }
                        }
                    }
                }
            } else if (action instanceof autismclient.util.macro.WaitGuiTypeAction gui) {
                for (String name : autismclient.util.macro.MacroCapturePattern.declaredNames(gui.matchMode, gui.title)) {
                    tokens.add("{" + name + "}");
                }
                addStructuredTokens(tokens, root, "title", "type");
            } else if (action instanceof autismclient.util.macro.WaitPosAction
                    || action instanceof autismclient.util.macro.WaitForPositionDeltaAction
                    || action instanceof autismclient.util.macro.WaitForTeleportAction) {
                addStructuredTokens(tokens, root, "x", "y", "z", "yaw", "pitch");
            } else if (action instanceof autismclient.util.macro.WaitForWorldChangeAction) {
                addStructuredTokens(tokens, root, "id", "name");
            } else if (action instanceof autismclient.util.macro.WaitGamemodeChangeAction) {
                addStructuredTokens(tokens, root, "name");
            } else {
                addStructuredTokens(tokens, root, "name", "id", "count", "slot", "condition");
            }
        }
    }

    private static void addStructuredTokens(java.util.Set<String> tokens, String root, String... properties) {
        if (root == null || root.isBlank()) return;
        String clean = autismclient.util.macro.MacroVariableContext.cleanRootName(root);
        if (clean.isBlank()) return;
        tokens.add("{" + clean + "}");
        for (String property : properties) tokens.add("{" + clean + "." + property + "}");
    }

    private void renderMacroSelector(GuiGraphicsExtractor ctx, FieldDef field, AutismChatField backingField,
                                     int x, int y, int w, int mx, int my) {
        List<String> macros = availableMacroNames(backingField.getText());
        Identifier font = theme.fontFor(UiTone.BODY);
        String current = backingField.getText() == null ? "" : backingField.getText();
        if (current.isBlank() && !macros.isEmpty()) {
            backingField.setText(macros.get(0));
            current = backingField.getText();
        }

        UiText.draw(ctx, textRenderer, field.label(), font, AutismColors.textSecondary(), x, y + 2, false);
        int badgeW = Math.min(150, Math.max(60, w / 2));
        int badgeX = x + w - badgeW;
        CompactSurfaces.valueField(ctx, badgeX, y + 1, badgeW, 14);
        String selectedLabel = current.isBlank() ? "No macro selected" : current;
        String selectedTrimmed = UiText.trimToWidth(textRenderer, selectedLabel, badgeW - 6, font, -1);
        UiText.draw(ctx, textRenderer, selectedTrimmed, font, current.isBlank() ? AutismColors.textMuted() : AutismColors.textPrimary(), badgeX + 3, y + 3, false);

        int listY = y + 17;
        int listH = MACRO_SELECT_VISIBLE_ROWS * SEL_ITEM_H;
        int itemW = w - SCROLLBAR_W - 1;
        String viewportKey = "macro_select_" + field.key();
        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, viewportKey, x, listY, w, listH, SEL_ITEM_H, SCROLLBAR_W);
        viewport.setContentHeight(macros.size() * SEL_ITEM_H);
        viewport.renderScrollbar(ctx, mx, my);

        if (macros.isEmpty()) {
            CompactSurfaces.row(ctx, x, listY, itemW, 13, false, false);
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No saved macros", x, listY, itemW);
            return;
        }

        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = viewport.getFirstVisibleRow();
        for (int i = first; i < macros.size() && i <= viewport.getLastVisibleRow(); i++) {
            String macroName = macros.get(i);
            int iy = viewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            boolean selected = macroName.equals(current);
            boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + SEL_ITEM_H;
            MacroTypedListControl.renderRow(ctx, textRenderer, Component.literal(macroName),
                    UiBounds.of(x, iy, itemW, SEL_ITEM_H), hovered, selected, CompactListRenderer.RowTone.NORMAL, false);
            final String selectedMacro = macroName;
            hitRegions.add(new HitRegion(x, iy, itemW, SEL_ITEM_H, () -> backingField.setText(selectedMacro)));
        }
        viewport.endRender(ctx);
    }

    private List<String> availableMacroNames(String currentValue) {
        AutismMacroManager manager = AutismMacroManager.get();
        long revision = manager.getRevision();
        if (revision != cachedMacroNamesRevision) {
            cachedMacroNames.clear();
            for (AutismMacro macro : manager.getAll()) {
                if (macro != null && macro.name != null && !macro.name.isBlank()) {
                    cachedMacroNames.add(macro.name);
                }
            }
            cachedMacroNamesRevision = revision;
        }

        if (currentValue == null || currentValue.isBlank() || cachedMacroNames.contains(currentValue)) {
            return cachedMacroNames;
        }

        macroNamesWithCurrent.clear();
        macroNamesWithCurrent.add(currentValue);
        macroNamesWithCurrent.addAll(cachedMacroNames);
        return macroNamesWithCurrent;
    }

    private boolean isWaitChatPatternField(FieldDef field) {
        return field != null
                && "pattern".equals(field.key())
                && targetAction != null
                && targetAction.getType() == MacroActionType.WAIT_CHAT;
    }

    private void renderEnum(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                            int mx, int my) {
        String key = field.key();
        List<String> opts = field.enumOptions();
        if (opts.isEmpty()) return;
        int idx = Math.min(enumIndices.getOrDefault(key, 0), opts.size() - 1);
        Identifier font = theme.fontFor(UiTone.BODY);

        int lw = labelWidth(w, field.label(), font, 72);
        drawLabel(ctx, field.label(), x, y, lw, font);

        int ctrlX = controlX(x, lw);
        int ctrlW = controlWidth(w, lw);
        int ddH   = 16;
        int variableW = shouldShowVariableButton(key) ? 20 : 0;
        int valueW = Math.max(1, ctrlW - (variableW > 0 ? variableW + 2 : 0));
        AutismChatField bindingField = field.dynamic() ? textFields.get(key) : null;

        if (bindingField != null && !bindingField.getText().isBlank()) {
            bindingField.setX(ctrlX);
            bindingField.setY(y + 2);
            bindingField.setWidth(valueW);
            bindingField.render(ctx, mx, my, 0.0f);
            if (variableW > 0) renderDynamicEnumDropdown(field, bindingField, ctrlX + valueW + 2, y + 2, variableW);
            return;
        }

        final List<String> capturedOpts = opts;
        final String capturedKey = key;
        CompactDropdown dd = enumDropdownCache.computeIfAbsent(key, k ->
            new CompactDropdown(ctrlX, y + 1, valueW, ddH, capturedOpts, idx, newIdx -> {
                if (newIdx >= 0 && newIdx < capturedOpts.size()) enumIndices.put(capturedKey, newIdx);
            }));
        dd.setBounds(ctrlX, y + 1, valueW, ddH)
          .setOptions(opts)
          .setSelectedIndex(idx)
          .setOnSelect(newIdx -> {
              if (newIdx >= 0 && newIdx < capturedOpts.size()) enumIndices.put(capturedKey, newIdx);
          });
        enumDropdowns.add(dd);
        if (variableW > 0 && bindingField != null) renderDynamicEnumDropdown(field, bindingField, ctrlX + valueW + 2, y + 2, variableW);
    }

    private void renderDynamicEnumDropdown(FieldDef field, AutismChatField bindingField, int x, int y, int width) {
        List<String> options = new ArrayList<>();
        options.add("Static");
        options.addAll(variableTokens);
        String cacheKey = "__enum_vars_" + field.key();
        CompactDropdown dropdown = enumDropdownCache.computeIfAbsent(cacheKey,
                ignored -> new CompactDropdown(x, y, width, 14, options, 0, index -> {}));
        dropdown.setBounds(x, y, width, 14)
                .setOptions(options)
                .setButtonLabelOverride("{}")
                .setOnSelect(index -> {
                    if (index <= 0) bindingField.setText("");
                    else if (index < options.size()) bindingField.setText(options.get(index));
                    dropdown.close();
                });
        enumDropdowns.add(dropdown);
    }

    private void renderBlockPos(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                                int mx, int my, float delta) {
        String key  = field.key();
        Identifier font = theme.fontFor(UiTone.BODY);
        String[] axis = {"X", "Y", "Z"};
        boolean hasCapture = field.captureMode() != CaptureMode.NONE
                && captureControlAvailable(field.captureMode());

        int capBtnW = CAPTURE_BTN_W, capBtnH = 14;
        int labelW  = hasCapture ? w - capBtnW - 4 : w;
        UiText.draw(ctx, textRenderer, field.label(), font,
                AutismColors.textSecondary(), x, y + 3, false);

        if (hasCapture) {
            int cbX = x + labelW + 4;
            renderFieldCaptureButton(
                    ctx,
                    cbX,
                    y,
                    capBtnW,
                    capBtnH,
                    field.captureMode(),
                    false,
                    true,
                    mx,
                    my,
                    () -> startBlockPosCapture(field)
            );
        }

        int fieldW = (w - 4) / 3;
        for (int i = 0; i < 3; i++) {
            AutismChatField tf = textFields.get(key + "_" + i);
            if (tf == null) continue;
            int fx = x + i * (fieldW + 2);
            int variableW = shouldShowVariableButton(field.key()) ? 18 : 0;
            UiText.draw(ctx, textRenderer, axis[i], font,
                    AutismColors.textDim(), fx, y + ROW_H + 3, false);
            tf.setX(fx + 9); tf.setY(y + ROW_H + 1);
            tf.setWidth(Math.max(1, fieldW - 9 - (variableW > 0 ? variableW + 2 : 0)));
            tf.render(ctx, mx, my, delta);
            if (variableW > 0) renderVariableDropdown(field.key() + "_" + axis[i].toLowerCase(Locale.ROOT), tf,
                    fx + fieldW - variableW, y + ROW_H + 2, variableW);
        }
    }

    private void pickNbtBookTextFile(AutismChatField field) {
        if (field == null) return;
        String current = field.getText() == null ? "" : field.getText().trim();
        String selected = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                "NBT Book Text File",
                current.isBlank() ? null : current,
                null,
                null,
                false
        );
        if (selected != null && !selected.isBlank()) field.setText(selected);
    }

    private void renderStringList(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                                  int mx, int my, float delta) {
        String key        = field.key();
        List<String> lst  = stringLists.getOrDefault(key, Collections.emptyList());
        Identifier font   = theme.fontFor(UiTone.BODY);
        AutismChatField af = addFields.get(key);
        boolean editable  = isEditableStringList(key);
        boolean xcarryList = isXCarryListKey(key);
        int editIdx       = editable ? stringListEditIndex.getOrDefault(key, -1) : -1;

        if (editIdx >= lst.size()) { editIdx = -1; stringListEditIndex.put(key, -1); }
        String filter     = (!editable && af != null) ? af.getText().toLowerCase() : "";
        String emptyHint  = getStringListEmptyHint(key);

        List<Integer> filtered = MacroTypedListControl.refilter(
                lst,
                stringListFilteredIndices.computeIfAbsent(key, ignored -> new ArrayList<>()),
                value -> filter.isEmpty() || value.toLowerCase().contains(filter)
        );

        String sectionLabel = filter.isEmpty()
                ? field.label() + " (" + lst.size() + ")"
                : field.label() + " (" + filtered.size() + "/" + lst.size() + ")";
        UiText.draw(ctx, textRenderer, sectionLabel, font,
                AutismColors.textSecondary(), x, y + 2, false);

        boolean hasCapture = field.captureMode() != CaptureMode.NONE
                && captureControlAvailable(field.captureMode());
        boolean hasSlotField = editable && usesMinecraftTextRendering(key);
        int btnH      = 14;
        int addY      = y + 13;
        MacroTypedListControl.ToolbarLayout toolbar = MacroTypedListControl.toolbar(
                UiBounds.of(x, addY, w, MacroCaptureButton.HEIGHT),
                hasSlotField,
                hasCapture
        );
        int slotW     = toolbar.slot() == null ? 0 : toolbar.slot().width();

        AutismChatField slotField = hasSlotField ? textFields.get(key + "_slot") : null;
        if (hasSlotField && slotField == null) {
            slotField = makeField(slotW);
            slotField.setNumericOnly(true);
            slotField.setPlaceholder(Component.literal("Slot#"));
            textFields.put(key + "_slot", slotField);
        }
        if (af != null) {
            af.setX(toolbar.text().x()); af.setY(toolbar.text().y() + 1); af.setWidth(toolbar.text().width());
            af.render(ctx, mx, my, delta);
        }
        if (hasSlotField && slotField != null) {
            slotField.setX(toolbar.slot().x()); slotField.setY(toolbar.slot().y() + 1); slotField.setWidth(toolbar.slot().width());
            slotField.render(ctx, mx, my, delta);
        }

        if (editable && editIdx >= 0 && editIdx < lst.size() && af != null) {
            String nameText = af.getText().strip();
            String slotText = hasSlotField && slotField != null ? slotField.getText().strip() : "";
            String entry = buildEntryFromNameAndSlot(nameText, slotText);
            if (!entry.isEmpty()) {
                String normalized = isXCarryListKey(key) ? normalizeXCarryEntry(entry)
                        : usesStoreTargetFormatting(key)
                            ? autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry) : entry;
                if (normalized != null && !normalized.isBlank() && !normalized.equals(lst.get(editIdx))) {
                    lst.set(editIdx, normalized);
                    if (usesMinecraftTextRendering(key)) {
                        editorItemLists.put(key, buildStructuredListTargets(key));
                    }
                }
            }
        }

        final AutismChatField fSlotField = slotField;
        if (af != null) {
            renderOverlayButton(ctx, toolbar.add().x(), toolbar.add().y(), toolbar.add().width(), btnH, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                String nameText = af.getText().strip();
                String slotText = hasSlotField && fSlotField != null ? fSlotField.getText().strip() : "";
                String entry = buildEntryFromNameAndSlot(nameText, slotText);
                if (!entry.isEmpty() && addStringListEntry(field, lst, entry)) {
                    af.setText("");
                    if (fSlotField != null) fSlotField.setText("");
                    stringListEditIndex.put(key, -1);
                }
            });
        }

        if (hasCapture) {
            boolean isItemSlot = field.captureMode() == CaptureMode.ITEM_SLOT;
            boolean capturing  = isItemSlot && captureSession.isItemSlotCapture(key);
            final String fKey = key;
            final FieldDef fField = field;
            final List<String> fLst = lst;
            renderFieldCaptureButton(
                    ctx,
                    toolbar.capture().x(),
                    toolbar.capture().y(),
                    toolbar.capture().width(),
                    btnH,
                    field.captureMode(),
                    capturing,
                    true,
                    mx,
                    my,
                    () -> {
                if (isItemSlot) {
                    toggleItemSlotCapture(fKey);
                } else {
                    startCapture(fField, fLst);
                }
            });
        }

        int itemsY       = addY + btnH + 2;
        int selAreaH     = SEL_LIST_MAX_VIS * SEL_ITEM_H;
        int delW         = 13;
        int destW        = xcarryList ? 62 : 0;
        int amountW      = xcarryList ? 54 : 0;
        if (xcarryList) syncXCarryDestinationCount(lst.size());
        if (xcarryList) {
            syncXCarryAmountCount(lst.size());
            if (editIdx >= 0 && editIdx < lst.size()) {
                final int selectedXCarryIndex = editIdx;
                AutismChatField countField = textFields.get(key + "_xcarry_count");
                if (countField == null) {
                    countField = makeField(38);
                    countField.setNumericOnly(true);
                    countField.setPlaceholder(Component.literal("Count"));
                    textFields.put(key + "_xcarry_count", countField);
                }

                int amountY = itemsY;
                UiText.draw(ctx, textRenderer, "Amount", theme.fontFor(UiTone.BODY),
                        AutismColors.textSecondary(), x, amountY + 3, false);
                int modeX = x + 52;
                boolean custom = getXCarryAmountMode(editIdx) == autismclient.util.macro.XCarryAction.AmountMode.CUSTOM;
                renderOverlayButton(ctx, modeX, amountY, 58, btnH, custom ? "Count" : "FullStk",
                        CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> {
                            boolean nowCustom = getXCarryAmountMode(selectedXCarryIndex) == autismclient.util.macro.XCarryAction.AmountMode.CUSTOM;
                            setXCarryAmountMode(selectedXCarryIndex, nowCustom
                                    ? autismclient.util.macro.XCarryAction.AmountMode.FULL_STK
                                    : autismclient.util.macro.XCarryAction.AmountMode.CUSTOM);
                            AutismChatField cf = textFields.get(key + "_xcarry_count");
                            if (cf != null) cf.setText(String.valueOf(getXCarryAmount(selectedXCarryIndex)));
                        });
                if (custom) {
                    countField.setX(modeX + 62);
                    countField.setY(amountY + 1);
                    countField.setWidth(42);
                    if (countField.getText().isBlank()) countField.setText(String.valueOf(getXCarryAmount(editIdx)));
                    countField.render(ctx, mx, my, delta);
                    try {
                        int parsed = Integer.parseInt(countField.getText().strip());
                        setXCarryAmount(selectedXCarryIndex, Math.max(1, parsed));
                    } catch (NumberFormatException ignored) {  }
                }
                itemsY = amountY + btnH + 2;
            }
        }

        DirectScrollViewport strViewport = getOrCreateViewport(selectedScrollViewports, key,
            x, itemsY, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        strViewport.setContentHeight(filtered.size() * SEL_ITEM_H);

        strViewport.renderScrollbar(ctx, mx, my);

        if (!filtered.isEmpty()) {
            int rowAreaW = Math.max(24, w - SCROLLBAR_W - 1 - delW - 2
                    - (destW > 0 ? destW + 2 : 0)
                    - (amountW > 0 ? amountW + 2 : 0));
            strViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstVi = strViewport.getFirstVisibleRow();
            for (int vi = firstVi; vi < filtered.size() && vi <= strViewport.getLastVisibleRow(); vi++) {
                int ri   = filtered.get(vi);
                int iy   = strViewport.getRowScreenY(vi);
                if (iy == Integer.MIN_VALUE) continue;
                Component displayValue = formatStringListEntryText(key, lst.get(ri), ri);
                boolean selected = editable && ri == editIdx;
                boolean hovered = mx >= x && mx < x + rowAreaW && my >= iy && my < iy + SEL_ITEM_H;
                if (field.captureMode() == CaptureMode.PACKET_NAME) {
                    int textColor = getPacketRowColor(lst.get(ri), ri, selected);
                    MacroTypedListControl.renderRowWithColor(
                            ctx,
                            textRenderer,
                            displayValue,
                            UiBounds.of(x, iy, rowAreaW, SEL_ITEM_H),
                            hovered,
                            selected,
                            textColor,
                            usesMinecraftTextRendering(key)
                    );
                } else {
                    MacroTypedListControl.renderRow(
                            ctx,
                            textRenderer,
                            displayValue,
                            UiBounds.of(x, iy, rowAreaW, SEL_ITEM_H),
                            hovered,
                            selected,
                            CompactListRenderer.RowTone.NORMAL,
                            usesMinecraftTextRendering(key)
                    );
                }

                if (editable) {
                    final int fri = ri;
                    final AutismChatField fSlotF2 = hasSlotField ? textFields.get(key + "_slot") : null;
                    hitRegions.add(new HitRegion(x, iy, rowAreaW, SEL_ITEM_H, () -> {
                        int curIdx = stringListEditIndex.getOrDefault(key, -1);
                        if (curIdx == fri) {
                            stringListEditIndex.put(key, -1);
                            if (af != null) af.setText("");
                            if (fSlotF2 != null) fSlotF2.setText("");
                            AutismChatField countField = textFields.get(key + "_xcarry_count");
                            if (countField != null) countField.setText("");
                        } else {
                            stringListEditIndex.put(key, fri);
                            String raw = lst.get(fri);
                            if (af != null) af.setText(parseHandlerEntryName(raw));
                            if (fSlotF2 != null) {
                                ItemTarget parsed = ItemTarget.fromLegacyEntry(raw);
                                fSlotF2.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
                            }
                            AutismChatField countField = textFields.get(key + "_xcarry_count");
                            if (countField != null) countField.setText(String.valueOf(getXCarryAmount(fri)));
                        }
                    }));
                }

                int actionX = x + rowAreaW + 2;
                if (xcarryList) {
                    final int fDestIndex = ri;
                    int dest = getXCarryDestination(fDestIndex);
                    int[] destValues = autismclient.util.macro.XCarryAction.destinationValues();
                    List<String> destOptions = autismclient.util.macro.XCarryAction.destinationLabels();
                    int selectedDestIndex = autismclient.util.macro.XCarryAction.destinationIndex(dest);
                    String ddKey = key + "_xcarry_dest_" + fDestIndex;
                    final int ddX = actionX;
                    final int ddY = iy + 1;
                    CompactDropdown dd = enumDropdownCache.computeIfAbsent(ddKey, k ->
                            new CompactDropdown(ddX, ddY, destW, 13, destOptions, selectedDestIndex, newIdx -> {
                                if (newIdx >= 0 && newIdx < destValues.length) {
                                    setXCarryDestination(fDestIndex, destValues[newIdx]);
                                }
                            }));
                    dd.setBounds(ddX, ddY, destW, 13)
                      .setOptions(destOptions)
                      .setSelectedIndex(selectedDestIndex)
                      .setOnSelect(newIdx -> {
                          if (newIdx >= 0 && newIdx < destValues.length) {
                              setXCarryDestination(fDestIndex, destValues[newIdx]);
                          }
                      });
                    enumDropdowns.add(dd);
                    actionX += destW + 2;

                    final int fAmountIndex = ri;
                    boolean custom = getXCarryAmountMode(fAmountIndex) == autismclient.util.macro.XCarryAction.AmountMode.CUSTOM;
                    String amountLabel = custom ? "x" + getXCarryAmount(fAmountIndex) : "FullStk";
                    renderOverlayButton(ctx, actionX, iy + 1, amountW, 13, amountLabel,
                            custom ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY,
                            true, mx, my, () -> {
                                boolean nowCustom = getXCarryAmountMode(fAmountIndex) == autismclient.util.macro.XCarryAction.AmountMode.CUSTOM;
                                setXCarryAmountMode(fAmountIndex, nowCustom
                                        ? autismclient.util.macro.XCarryAction.AmountMode.FULL_STK
                                        : autismclient.util.macro.XCarryAction.AmountMode.CUSTOM);
                                stringListEditIndex.put(key, fAmountIndex);
                                AutismChatField cf = textFields.get(key + "_xcarry_count");
                                if (cf != null) cf.setText(String.valueOf(getXCarryAmount(fAmountIndex)));
                            });
                    actionX += amountW + 2;
                }

                int delX = actionX;
                final int fRi = ri;
                renderIconDeleteButton(ctx, delX, iy + 1, delW, mx, my, () -> {
                    lst.remove(fRi);
                    if (xcarryList) {
                        removeXCarryDestination(fRi);
                        removeXCarryAmount(fRi);
                    }
                    if (editable && stringListEditIndex.getOrDefault(key, -1) == fRi) {
                        stringListEditIndex.put(key, -1);
                        if (af != null) af.setText("");
                    } else if (editable && stringListEditIndex.getOrDefault(key, -1) > fRi) {
                        stringListEditIndex.put(key, stringListEditIndex.get(key) - 1);
                    }
                    DirectScrollViewport vp = selectedScrollViewports.get(key);
                    if (vp != null) vp.scrollBy(-1);
                    if (usesMinecraftTextRendering(key)) {
                        editorItemLists.put(key, buildStructuredListTargets(key));
                    }
                });
            }
            strViewport.endRender(ctx);
        } else if (emptyHint != null && !emptyHint.isBlank()) {
            int itemW = w - SCROLLBAR_W - 1;
            CompactSurfaces.row(ctx, x, itemsY, itemW, 12, false, false);
            CompactListRenderer.drawEmptyState(ctx, textRenderer, emptyHint, x, itemsY, itemW);
        }
    }

    private String getStringListEmptyHint(String key) {
        if (isInventoryAuditTargetListKey(key)) {
            List<String> selected = stringLists.getOrDefault(key, Collections.emptyList());
            if (selected.isEmpty()) {
                return "Add at least one target before running this audit.";
            }
        }
        return null;
    }

    private void renderStringListCatalog(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                                         int mx, int my, float delta) {
        String key        = field.key();
        List<String> sel  = stringLists.getOrDefault(key, Collections.emptyList());
        Identifier font   = theme.fontFor(UiTone.BODY);
        AutismChatField af = addFields.get(key);
        String filter     = (af != null) ? af.getText().toLowerCase() : "";

        int cy = y;

        UiText.draw(ctx, textRenderer, field.label() + " (" + sel.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;

        int delW        = 13;
        int selAreaH    = SEL_LIST_MAX_VIS * SEL_ITEM_H;

        DirectScrollViewport catSelViewport = getOrCreateViewport(selectedScrollViewports, key,
            x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        catSelViewport.setContentHeight(sel.size() * SEL_ITEM_H);

        catSelViewport.renderScrollbar(ctx, mx, my);

        if (!sel.isEmpty()) {
            int delX = x + w - SCROLLBAR_W - 2 - delW;
            catSelViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstSel = catSelViewport.getFirstVisibleRow();
            for (int i = firstSel; i < sel.size() && i <= catSelViewport.getLastVisibleRow(); i++) {
                int siy = catSelViewport.getRowScreenY(i);
                if (siy == Integer.MIN_VALUE) continue;
                String display = AutismRegistryLabels.block(sel.get(i));
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        Component.literal(display),
                        UiBounds.of(x, siy, delX - x - 1, 13),
                        mx >= x && mx < delX - 1 && my >= siy && my < siy + 13,
                        false,
                        CompactListRenderer.RowTone.NORMAL,
                        false
                );
                final int fi = i;
                renderIconDeleteButton(ctx, delX, siy, delW, mx, my, () -> {
                    sel.remove(fi);
                    DirectScrollViewport vp = selectedScrollViewports.get(key);
                    if (vp != null) vp.scrollBy(-1);
                });
            }
            catSelViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "(none selected)", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 1, w, AutismColors.subPanelBorder());
        cy += 5;

        if (af != null) {
            af.setX(x); af.setY(cy); af.setWidth(w);
            af.render(ctx, mx, my, delta);
        }
        cy += 14;

        int headerH = 15;
        int capBtnH2 = 14;
        int cbX = x + w - CAPTURE_BTN_W;
        int headerTextW = Math.max(24, cbX - x - 4);
        String headerLabel = UiText.trimToWidth(
            textRenderer,
            "Available Blocks",
            headerTextW,
            font,
            AutismColors.textSecondary()
        );
        int headerTextY = UiSizing.alignTextY(cy, headerH, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
        renderFieldCaptureButton(ctx, cbX, cy, CAPTURE_BTN_W, capBtnH2, field.captureMode(), false, true, mx, my, () -> startCapture(field, sel));
        UiText.draw(ctx, textRenderer, headerLabel, font, AutismColors.textSecondary(), x, headerTextY, false);
        cy += headerH + 2;

        List<String> filtered = MacroTypedListControl.refilterValues(
                getAllBlockIds(),
                catalogFilteredValues.computeIfAbsent(key + "_catalog", ignored -> new ArrayList<>()),
                id -> matchesListFilter(filter, id, trimMinecraftPrefix(id), AutismRegistryLabels.block(id))
        );

        int catItemW  = w - SCROLLBAR_W - 1;

        DirectScrollViewport catViewport = getOrCreateViewport(catalogScrollViewports, key,
            x, cy, w, CATALOG_LIST_H, CATALOG_ITEM_H, SCROLLBAR_W);
        catViewport.setContentHeight(filtered.size() * CATALOG_ITEM_H);

        catViewport.renderScrollbar(ctx, mx, my);

        catViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int firstItem = catViewport.getFirstVisibleRow();
        for (int i = firstItem; i < filtered.size() && i <= catViewport.getLastVisibleRow(); i++) {
            int iy = catViewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            String id      = filtered.get(i);
            String display = AutismRegistryLabels.block(id);
            boolean already = sel.contains(id);
            boolean hov = mx >= x && mx < x + catItemW && my >= iy && my < iy + CATALOG_ITEM_H;
            MacroTypedListControl.renderRow(
                ctx,
                textRenderer,
                Component.literal(display),
                UiBounds.of(x, iy, catItemW, CATALOG_ITEM_H),
                hov,
                already,
                already ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                false
            );
            if (!already) {
                hitRegions.add(new HitRegion(x, iy, catItemW, CATALOG_ITEM_H, () -> {
                    if (!sel.contains(id)) sel.add(id);
                }));
            } else {

                hitRegions.add(new HitRegion(x, iy, catItemW, CATALOG_ITEM_H, () -> sel.remove(id)));
            }
        }
        catViewport.endRender(ctx);
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int footerY, int mx, int my) {
        int btnH  = 16;
        String variableWarning = variableAvailabilityWarning();
        int btnY  = variableWarning.isBlank() ? footerY + (FOOTER_H - btnH) / 2 : footerY + FOOTER_H - btnH - 2;
        if (standalonePayloadEditor) {
            int closeW = 72;
            int closeX = panelX + (panelW - closeW) / 2;
            CompactSurfaces.divider(ctx, panelX + PAD, footerY, panelW - (PAD * 2), AutismColors.subPanelBorder());
            renderOverlayButton(ctx, closeX, btnY, closeW, btnH, "Close", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> closeEditor(false));
            return;
        }
        int btnW  = payloadAction != null ? 72 : 58;
        int gap   = 6;
        int total = btnW * 2 + gap;
        int sx    = panelX + (panelW - total) / 2;

        CompactSurfaces.divider(ctx, panelX + PAD, footerY, panelW - (PAD * 2), AutismColors.subPanelBorder());

        if (!variableWarning.isBlank()) {
            Identifier font = theme.fontFor(UiTone.BODY);
            String text = UiText.trimToWidth(textRenderer, variableWarning, panelW - PAD * 2, font, -1);
            UiText.draw(ctx, textRenderer, text, font, 0xFFFF6B6B,
                    panelX + Math.max(PAD, (panelW - uiWidth(font, text)) / 2), footerY + 2, false);
        }

        renderOverlayButton(ctx, sx, btnY, btnW, btnH, "Cancel", CompactOverlayButton.Variant.SECONDARY, true, mx, my, () -> closeEditor(false));

        int saveX = sx + btnW + gap;
        renderOverlayButton(ctx, saveX, btnY, btnW, btnH, payloadAction != null ? "Save Macro" : "Save", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> closeEditor(true));
    }

    private String variableAvailabilityWarning() {
        if (variableMacro == null || variableMacro.actions == null || variableActionIndex < 0) return "";
        java.util.Set<String> used = new java.util.LinkedHashSet<>();
        for (AutismChatField field : textFields.values()) collectTemplateRoots(used, field == null ? "" : field.getText());
        for (List<String> values : stringLists.values()) for (String value : values) collectTemplateRoots(used, value);
        used.removeAll(currentActionCaptureRoots());
        if (used.isEmpty()) return "";
        used.removeAll(autismclient.util.macro.MacroTemplate.BUILT_IN_NAMES);
        if (used.isEmpty()) return "";

        java.util.Map<String, Integer> providerIndex = new java.util.LinkedHashMap<>();
        for (int i = 0; i < variableMacro.actions.size(); i++) {
            java.util.LinkedHashSet<String> supplied = new java.util.LinkedHashSet<>();
            addActionVariableTokens(supplied, variableMacro.actions.get(i));
            for (String token : supplied) {
                java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<>();
                collectTemplateRoots(roots, token);
                for (String root : roots) providerIndex.putIfAbsent(root, i);
            }
        }
        for (String root : used) {
            Integer provider = providerIndex.get(root);
            if (provider == null) return "Value may be missing";
            if (provider >= variableActionIndex) return "Captured later";
            if (isRaceBodyIndex(provider) && !sameRaceBody(provider, variableActionIndex)) return "Race-only value";
        }
        return "";
    }

    private java.util.Set<String> currentActionCaptureRoots() {
        java.util.LinkedHashSet<String> outputs = new java.util.LinkedHashSet<>();
        if (targetAction instanceof autismclient.util.macro.MacroCaptureOutput captureOutput) {
            String root = autismclient.util.macro.MacroVariableContext.cleanRootName(captureOutput.getSaveAs());
            if (!root.isBlank()) outputs.add(root);
        }
        String pattern = "";
        autismclient.util.macro.MacroCapturePattern.Mode mode = autismclient.util.macro.MacroCapturePattern.Mode.MATCH;
        if (targetAction instanceof autismclient.util.macro.WaitForChatAction) {
            AutismChatField field = textFields.get("pattern");
            pattern = field == null ? "" : field.getText();
            mode = editorCaptureMode();
        } else if (targetAction instanceof autismclient.util.macro.WaitForGuiAction) {
            AutismChatField field = textFields.get("guiTitle");
            pattern = field == null ? "" : field.getText();
            mode = editorCaptureMode();
        } else if (targetAction instanceof autismclient.util.macro.WaitGuiTypeAction) {
            AutismChatField field = textFields.get("title");
            pattern = field == null ? "" : field.getText();
            mode = editorCaptureMode();
        } else if (targetAction instanceof autismclient.util.macro.CaptureValueAction) {
            AutismChatField field = textFields.get("pattern");
            pattern = field == null ? "" : field.getText();
            mode = editorCaptureMode();
        }
        if (mode != autismclient.util.macro.MacroCapturePattern.Mode.MATCH) {
            outputs.addAll(autismclient.util.macro.MacroCapturePattern.declaredNames(mode, pattern));
        }
        return outputs;
    }

    private autismclient.util.macro.MacroCapturePattern.Mode editorCaptureMode() {
        try {
            return autismclient.util.macro.MacroCapturePattern.Mode.valueOf(currentEnumValue("matchMode"));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return autismclient.util.macro.MacroCapturePattern.Mode.MATCH;
        }
    }

    private static void collectTemplateRoots(java.util.Set<String> out, String text) {
        if (out == null || text == null || text.isBlank()) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_.-]{0,63})(?:\\|[^{}]*)?}(?!})")
                .matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            int dot = name.indexOf('.');
            out.add(dot < 0 ? name : name.substring(0, dot));
        }
    }

    private boolean isRaceBodyIndex(int index) {
        return raceHeaderFor(index) >= 0;
    }

    private boolean sameRaceBody(int left, int right) {
        int leftHeader = raceHeaderFor(left);
        return leftHeader >= 0 && leftHeader == raceHeaderFor(right);
    }

    private int raceHeaderFor(int index) {
        if (variableMacro == null || variableMacro.actions == null || index < 0) return -1;
        for (int i = 0; i < Math.min(index, variableMacro.actions.size()); i++) {
            MacroAction action = variableMacro.actions.get(i);
            if (action instanceof autismclient.util.macro.RaceAction race) {
                int end = i + Math.max(0, race.bodyCount);
                if (index > i && index <= end) return i;
            }
        }
        return -1;
    }

    private void enterCaptureMode() {
        clearCaptureToasts();
        captureHiddenOverlays = new ArrayList<>();
        for (IAutismOverlay o : AutismOverlayManager.get().getOverlays()) {
            if (o != this && o.isVisible()) {
                o.saveLayout();
                captureHiddenOverlays.add(o);
                o.setVisible(false);
            }
        }
        saveLayout();
        clearTextFieldFocus();
        restoreVisibleAfterCapture = visible;
        visible = false;
        AutismSharedState.get().setCaptureMode(true);
    }

    private void toggleItemSlotCapture(String key) {
        if (!guardWorldCaptureAction()) return;
        captureSession.toggleItemSlotCapture(
                key,
                this::enterItemSlotCaptureMode,
                () -> exitCaptureMode(false, false)
        );
    }

    private void enterItemSlotCaptureMode() {
        Screen current = MC.gui.screen();
        boolean hasSlotScreen = current instanceof AbstractContainerScreen<?>;
        autoOpenedInventoryForCapture = false;
        enterCaptureMode();
        if (!hasSlotScreen) {

            screenBeforeCapture = current;
            autoOpenedInventoryForCapture = true;
            MC.execute(() -> MC.gui.setScreen(new InventoryScreen(MC.player)));
        }
    }

    private void restoreCaptureHiddenOverlays() {
        if (captureHiddenOverlays == null) return;
        AutismOverlayManager manager = AutismOverlayManager.get();
        for (IAutismOverlay overlay : captureHiddenOverlays) {
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.saveLayout();
        }
        captureHiddenOverlays = null;
    }

    private void clearCaptureCallbacks(boolean cancelGBreak) {
        AutismSharedState state = AutismSharedState.get();
        state.setCaptureCancelCallback(null);
        state.setBlockCaptureCallback(null);
        state.setDirectionalBlockCaptureCallback(null);
        state.setEntityCaptureCallback(null);
        state.setAttackCaptureCallback(null);
        state.setEntityCaptureSpecific(false);
        if (cancelGBreak && state.isGBreakCapturing()) {
            state.cancelGBreakCapture();
        }
    }

    private void exitCaptureMode(boolean reopenInventory, boolean closeCurrentScreen) {
        restoreCaptureHiddenOverlays();
        clearCaptureCallbacks(false);
        AutismSharedState.get().setPlaceCaptureActive(false);
        AutismSharedState.get().setCaptureMode(false);
        visible = restoreVisibleAfterCapture;
        restoreVisibleAfterCapture = false;

        hitRegions.clear();
        scrollDragRegions.clear();
        AutismOverlayManager.get().bringToFront(this);
        if (autoOpenedInventoryForCapture) {

            Screen restore = screenBeforeCapture;
            MC.execute(() -> {
                if (MC.gui.screen() instanceof InventoryScreen) {
                    if (restore != null) MC.gui.setScreen(restore);
                } else if (restore == null && MC.gui.screen() == null && MC.player != null) {
                    MC.gui.setScreen(new InventoryScreen(MC.player));
                } else if (restore != null && MC.gui.screen() == null) {
                    MC.gui.setScreen(restore);
                }
            });
            autoOpenedInventoryForCapture = false;
        } else if (reopenInventory && screenBeforeCapture != null) {
            MC.execute(() -> { if (MC.gui.screen() == null) MC.gui.setScreen(screenBeforeCapture); });
        } else if (reopenInventory && MC.player != null) {
            MC.execute(() -> { if (MC.gui.screen() == null) MC.gui.setScreen(new InventoryScreen(MC.player)); });
        }
        if (closeCurrentScreen) {
            MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });
        }
        screenBeforeCapture = null;
        refreshInteractiveLayout();
    }

    private void closeEditor(boolean save) {
        restoreCaptureHiddenOverlays();
        restoreVisibleAfterCapture = false;
        clearCaptureCallbacks(true);
        AutismSharedState.get().setCaptureMode(false);
        clearCaptureToasts();

        if (targetAction != null && targetAction.getType() == MacroActionType.PLACE) {
            AutismInstaBreakRenderer.clear();
        }

        if (save && targetAction != null) {
            if (onPreSave != null) onPreSave.run();
            flushToWorkingTag();
            targetAction.fromTag(workingTag);
            autismclient.util.macro.MacroDynamicBindings.load(targetAction, workingTag);
            if (onSaveCallback != null) onSaveCallback.run();
        }
        packetSelectorOverlay.close();
        raceStepSelectorOverlay.close();
        visible                   = false;
        targetAction              = null;
        multiEditCount            = 1;
        standalonePayloadEditor   = false;
        payloadAction             = null;
        pendingPayloadEdit        = null;
        payloadBuildCache         = null;
        workingTag                = null;
        schema                    = null;
        itemAction                = null;
        captureSession.clearItemSlotCapture();
        if (packetClickCapturePendingKey != null) {
            autismclient.util.AutismContainerHold.clearPendingCapture();
            packetClickCapturePendingKey = null;
        }
        enumDropdowns.clear();
        enumDropdownCache.clear();
        craftEntries              = null;
        craftAllRecipes         = null;
        craftFilteredRecipes    = null;
        craftSelectedRecipe     = null;
        craftRecipeListBounds   = null;
        dropAction              = null;
        editorItemFields.clear();
        editorItemLists.clear();
        lanStepEntries          = null;
        toggleModuleEntries     = null;
        onPreSave               = null;
        onSaveCallback          = null;
        screenBeforeGBreak      = null;
        screenBeforeCapture     = null;
    }

    private void clearCaptureToasts() {
        captureToasts.clear();
    }

    private void showCaptureToast(String message, int accentColor) {
        captureToasts.show(message, accentColor);
    }

    public boolean hasAbstractContainerScreenCaptureToasts() {
        return captureToasts.hasVisibleToasts();
    }

    public void renderAbstractContainerScreenCaptureToasts(GuiGraphicsExtractor context, int anchorX, int anchorY, int anchorWidth) {
        captureToasts.render(UiContexts.overlay(context, textRenderer, anchorX, anchorY), anchorX, anchorY, anchorWidth);
    }

    private void flushToWorkingTag() {
        if (workingTag == null) return;

        if (itemAction != null) {
            if (itemEditIndex >= 0 && itemEditIndex < itemAction.itemNames.size()) {
                applyItemEntryEditor();
            }
            for (int i = 0; i < itemAction.itemNames.size(); i++) {
                AutismChatField tf = textFields.get("item_times_" + i);
                if (tf != null) {
                    try {
                        while (itemAction.itemTimes.size() <= i) itemAction.itemTimes.add(1);
                        itemAction.itemTimes.set(i, Math.max(1, Integer.parseInt(tf.getText().strip())));
                    } catch (NumberFormatException ignored) {  }
                }
            }
            itemAction.waitForGuiBefore = toggleStates.getOrDefault("item_waitForGuiBefore", false);
            itemAction.waitForGuiAfter  = toggleStates.getOrDefault("item_waitForGuiAfter",  false);
            itemAction.waitForItem = toggleStates.getOrDefault("item_waitForItem", false);
            itemAction.useCursorItemForPickupAll = toggleStates.getOrDefault("item_useCursorItemForPickupAll", true);
            itemAction.useClickDelay = toggleStates.getOrDefault("item_useClickDelay", false);
            AutismChatField delayF = textFields.get("item_clickDelayMs");
            if (delayF != null) {
                try { itemAction.clickDelayMs = Math.max(0, Integer.parseInt(delayF.getText().strip())); }
                catch (NumberFormatException ignored) {  }
            }
            AutismChatField guiF = textFields.get("item_guiName");
            if (guiF != null) itemAction.guiName = guiF.getText();
            if (!itemAction.waitForGuiBefore && !itemAction.waitForGuiAfter) itemAction.guiName = "";
            if (!itemAction.itemNames.isEmpty()) {
                itemAction.useSlot = false;
                itemAction.targetSlot = -1;
                itemAction.actionIndex = 0;
                itemAction.button = 0;
                itemAction.times = 1;
            }
            workingTag = itemAction.toTag();
            return;
        }

        if (craftEntries != null) {
            for (int i = 0; i < craftEntries.size(); i++) {
                CraftAction.CraftEntry entry = craftEntries.get(i);
                AutismChatField af = textFields.get("craft_amount_" + i);
                if (af != null) {
                    try { entry.amount = Math.max(1, Integer.parseInt(af.getText().strip())); }
                    catch (NumberFormatException ignored) {  }
                }
                entry.useMaxAmount = toggleStates.getOrDefault("craft_useMax_" + i, false);
            }
            ListTag entryTags = new ListTag();
            for (CraftAction.CraftEntry entry : craftEntries) {
                if (entry.hasRecipe()) entryTags.add(entry.toTag());
            }
            workingTag.put("entries", entryTags);
            return;
        }

        if (dropAction != null) {
            if (dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size()) {
                applyDropEntryEditor();
            }
            for (int i = 0; i < dropAction.itemNames.size(); i++) {
                AutismChatField f = textFields.get("drop_count_" + i);
                if (f != null) {
                    try { dropAction.itemCounts.set(i, Math.max(0, Integer.parseInt(f.getText().strip()))); }
                    catch (NumberFormatException ignored) {  }
                }
            }
            while (dropAction.itemCounts.size() < dropAction.itemNames.size()) {
                dropAction.itemCounts.add(dropAction.getItemCount(dropAction.itemCounts.size()));
            }
            AutismChatField cntF = textFields.get("drop_globalCount");
            if (cntF != null) { try { dropAction.dropCount = Math.max(1, Integer.parseInt(cntF.getText().strip())); } catch (NumberFormatException ignored) {  } }
            autismclient.util.macro.DropAction.DropMode[] modes = autismclient.util.macro.DropAction.DropMode.values();
            dropAction.mode = modes[Math.min(enumIndices.getOrDefault("drop_mode", 0), modes.length - 1)];
            dropAction.waitForGuiBefore = toggleStates.getOrDefault("drop_waitForGuiBefore", false);
            dropAction.waitForGuiAfter  = toggleStates.getOrDefault("drop_waitForGuiAfter",  false);
            dropAction.useHandlerSlots = true;
            AutismChatField guiF = textFields.get("drop_guiName");
            if (guiF != null) dropAction.guiName = guiF.getText();
            if (!dropAction.waitForGuiBefore && !dropAction.waitForGuiAfter) dropAction.guiName = "";
            workingTag = dropAction.toTag();
            workingTag.putString("packetOrder", currentEnumValue("packetOrder"));
            return;
        }

        if (payloadAction != null) {
            payloadAction = buildPayloadActionFromEditor();
            workingTag = payloadAction.toTag();
            return;
        }

        if (lanStepEntries != null) {
            for (int i = 0; i < lanStepEntries.size(); i++) {
                autismclient.util.macro.WaitForLanStepAction.LanStepEntry e = lanStepEntries.get(i);
                AutismChatField uf = textFields.get("lan_user_" + i);
                if (uf != null) e.username = uf.getText();
                AutismChatField sf = textFields.get("lan_step_" + i);
                if (sf != null) { try { e.step = Math.max(1, Integer.parseInt(sf.getText().strip())); } catch (NumberFormatException ignored) {  } }
            }
            ListTag entryList = new ListTag();
            for (autismclient.util.macro.WaitForLanStepAction.LanStepEntry e : lanStepEntries)
                entryList.add(e.toTag());
            workingTag.put("entries", entryList);
            workingTag.putBoolean("filterByUser", toggleStates.getOrDefault("lan_filterByUser", false));
            workingTag.putBoolean(MacroAction.LISTEN_DURING_PREVIOUS_KEY,
                    toggleStates.getOrDefault(MacroAction.LISTEN_DURING_PREVIOUS_KEY, false));
            AutismChatField dsF = textFields.get("lan_defaultStep");
            if (dsF != null) { try { workingTag.putInt("defaultStep", Math.max(1, Integer.parseInt(dsF.getText().strip()))); } catch (NumberFormatException ignored) {  } }

        }

        if (toggleModuleEntries != null) {
            ListTag entriesTag = new ListTag();
            for (autismclient.util.macro.ToggleModuleAction.ModuleEntry entry : toggleModuleEntries) {
                if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
                    entriesTag.add(entry.toTag());
                }
            }
            workingTag.put("entries", entriesTag);
            workingTag.putString("moduleName", "");
            workingTag.putString("toggleMode", autismclient.util.macro.ToggleModuleAction.ToggleMode.TOGGLE.name());
            return;
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET) {
            List<String> targets = sanitizeWaitPacketTargets(getOrCreateWaitPacketTargets());
            ListTag packetList = new ListTag();
            for (String target : targets) packetList.add(StringTag.valueOf(target));
            workingTag.put("packetNames", packetList);
            workingTag.putString("packetName", targets.isEmpty() ? "" : targets.get(0));
            workingTag.putBoolean(MacroAction.LISTEN_DURING_PREVIOUS_KEY, toggleStates.getOrDefault(MacroAction.LISTEN_DURING_PREVIOUS_KEY, false));
            AutismChatField saveAs = textFields.get("saveAs");
            if (saveAs != null) workingTag.putString("saveAs", saveAs.getText());
            return;
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
            syncWaitPacketMatchEditedRule();
            ListTag rules = new ListTag();
            if (waitPacketMatchRules != null) {
                for (WaitPacketMatchAction.Rule rule : waitPacketMatchRules) {
                    if (rule == null) continue;
                    if ((rule.packetName == null || rule.packetName.isBlank()) && (rule.fieldName == null || rule.fieldName.isBlank())) continue;
                    rules.add(rule.toTag());
                }
            }
            workingTag.put("rules", rules);
            workingTag.putBoolean(MacroAction.LISTEN_DURING_PREVIOUS_KEY, toggleStates.getOrDefault(MacroAction.LISTEN_DURING_PREVIOUS_KEY, false));
            AutismChatField saveAs = textFields.get("saveAs");
            if (saveAs != null) workingTag.putString("saveAs", saveAs.getText());
            return;
        }

        if (schema == null) return;
        for (FieldDef field : schema.fields()) {
            String key = field.key();
            switch (field.type()) {

                case TOGGLE ->
                    workingTag.putBoolean(key, toggleStates.getOrDefault(key, false));

                case NUMBER, SLOT -> {
                    AutismChatField f = textFields.get(key);
                    if (f != null) {
                        String raw = f.getText().strip();
                        if (field.dynamic() && raw.contains("{")) putDynamicBinding(key, raw);
                        else {
                            removeDynamicBinding(key);
                            try { workingTag.putInt(key, Integer.parseInt(raw)); }
                            catch (NumberFormatException ignored) {  }
                        }
                    }
                }

                case DECIMAL -> {
                    AutismChatField f = textFields.get(key);
                    if (f != null) {
                        String raw = f.getText().strip();
                        if (field.dynamic() && raw.contains("{")) putDynamicBinding(key, raw);
                        else {
                            removeDynamicBinding(key);
                            try { workingTag.putDouble(key, Double.parseDouble(raw)); }
                            catch (NumberFormatException ignored) {  }
                        }
                    }
                }

                case TEXT, MACRO_SELECT -> {
                    AutismChatField f = textFields.get(key);
                    if (f != null) workingTag.putString(key, f.getText());
                }

                case ENUM -> {
                    AutismChatField bindingField = field.dynamic() ? textFields.get(key) : null;
                    String binding = bindingField == null ? "" : bindingField.getText().strip();
                    if (field.dynamic() && !binding.isBlank()) {
                        putDynamicBinding(key, binding);
                    } else {
                        removeDynamicBinding(key);
                        List<String> opts = field.enumOptions();
                        if (!opts.isEmpty()) {
                            int idx = Math.min(enumIndices.getOrDefault(key, 0), opts.size() - 1);
                            workingTag.putString(key, opts.get(idx));
                        }
                    }
                }

                case BLOCK_POS -> {
                    String[] xyzKeys = field.xyzKeys();
                    boolean  dbl     = field.xyzDouble();
                    for (int i = 0; i < 3; i++) {
                        AutismChatField f = textFields.get(key + "_" + i);
                        if (f == null) continue;
                        String t = f.getText().strip();
                        if (field.dynamic() && t.contains("{")) {
                            putDynamicBinding(xyzKeys[i], t);
                            continue;
                        }
                        removeDynamicBinding(xyzKeys[i]);
                        if (dbl) {
                            try { workingTag.putDouble(xyzKeys[i], Double.parseDouble(t)); }
                            catch (NumberFormatException ignored) {  }
                        } else {
                            try { workingTag.putInt(xyzKeys[i], Integer.parseInt(t)); }
                            catch (NumberFormatException ignored) {  }
                        }
                    }
                }

                case STRING_LIST -> {
                    List<String> items = stringLists.getOrDefault(key, Collections.emptyList());
                    ListTag nbt = new ListTag();
                    for (String s : items) nbt.add(StringTag.valueOf(s));
                    workingTag.put(key, nbt);
                }
            }
        }

        rewriteStructuredEditorTargets();

        if (targetAction instanceof WaitsForGui && !hasGuiWaitEnabled()) {
            workingTag.putString("guiName", "");
            workingTag.putString("waitGuiName", "");
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.INVENTORY) {
            String mode = currentEnumValue("mode");
            if ("CLOSE".equals(mode)) {
                workingTag.putBoolean("waitForGuiBefore", false);
                workingTag.putBoolean("waitForGuiAfter", false);
            }
            workingTag.putString("guiName", "");
            workingTag.putBoolean("sendPacket", !toggleStates.getOrDefault("sendPacket", false));
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {

            net.minecraft.nbt.ListTag nbtEntries = new net.minecraft.nbt.ListTag();
            if (wscEntries != null) {
                for (WaitForSlotChangeAction.WaitEntry e : wscEntries) {
                    net.minecraft.nbt.CompoundTag ec = new net.minecraft.nbt.CompoundTag();
                    ItemTarget target = e.resolvedTarget();
                    if (target.hasSlot() || target.hasIdentity()) ec.put("target", target.toTag());
                    ec.putString("mode",   e.waitMode.name());
                    ec.putInt   ("count",  Math.max(1, e.targetCount));
                    nbtEntries.add(ec);
                }
            }
            workingTag.put("entries", nbtEntries);
            workingTag.putBoolean(MacroAction.LISTEN_DURING_PREVIOUS_KEY,
                    toggleStates.getOrDefault(MacroAction.LISTEN_DURING_PREVIOUS_KEY, false));
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.USE_ITEM) {
            AutismChatField slotField = textFields.get("slot");
            if (slotField == null || slotField.getText() == null || slotField.getText().strip().isEmpty()) {
                workingTag.remove("slot");
            }
            String mode = workingTag.getStringOr("useMode", "AUTOMATIC");
            if ("CUSTOM_HOLD".equals(mode)) {
                workingTag.putInt("useCount", 1);
                if (workingTag.getIntOr("holdTicks", 0) <= 0) workingTag.putInt("holdTicks", 20);
            } else if (workingTag.getIntOr("useCount", 0) <= 0) {
                workingTag.putInt("useCount", 1);
            }
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.NBT_BOOK) {
            normalizeNbtBookWorkingTag();
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.SWAP_SLOTS) {
            boolean fromUseItemName = workingTag.getBooleanOr("fromUseItemName", false);
            boolean toUseItemName = workingTag.getBooleanOr("toUseItemName", false);
            if (fromUseItemName) workingTag.putInt("fromSlot", -1);
            else workingTag.putString("fromItemName", "");
            if (toUseItemName) workingTag.putInt("toSlot", -1);
            else workingTag.putString("toItemName", "");
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.GO_TO && !workingTag.getBooleanOr("waitForArrival", false)) {
            workingTag.putDouble("arrivalRadius", 2.0);
            workingTag.putInt("timeoutMs", 60000);
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.CLICK) {
            if (!hasGuiWaitEnabled()) {
                workingTag.putString("guiName", "");
            }
            if ("MIDDLE".equals(workingTag.getStringOr("clickType", "RIGHT"))) {
                workingTag.putString("clickType", "RIGHT");
            }
            if (workingTag.getIntOr("clickCount", 0) <= 0) workingTag.putInt("clickCount", 1);
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.XCARRY) {
            workingTag.putInt("safeClickDelayTicks",
                    autismclient.util.macro.XCarryAction.clampSafeClickDelayTicks(
                            workingTag.getIntOr("safeClickDelayTicks", 1)));
            workingTag.putBoolean("safeClickDelayAfterPickup",
                    toggleStates.getOrDefault("safeClickDelayAfterPickup",
                            workingTag.getBooleanOr("safeClickDelayAfterPickup", true)));
            workingTag.putBoolean("safeClickDelayBeforeReturn",
                    toggleStates.getOrDefault("safeClickDelayBeforeReturn",
                            workingTag.getBooleanOr("safeClickDelayBeforeReturn", true)));
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.DISCONNECT) {
            String mode = workingTag.getStringOr("mode", "DISCONNECT");
            if ("DISCONNECT".equals(mode)) {
                workingTag.putBoolean("useNextAction", false);
            } else if (workingTag.getIntOr("packetCount", 0) <= 0) {
                workingTag.putInt("packetCount", 200);
            }
            if (!"KICK_DUPE".equals(mode)) {
                workingTag.putBoolean("useNextAction", false);
            }
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.BREAK
                && !workingTag.getBooleanOr("interact", false)) {
            workingTag.putBoolean("runNextSteps", false);
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.CLOSE_GUI) {
            workingTag.putBoolean("sendPacket", !toggleStates.getOrDefault("sendPacket", false));
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.SAVE_GUI) {
            workingTag.putBoolean("sendPacket", !toggleStates.getOrDefault("sendPacket", false));
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.STORE_ITEM) {
            workingTag.putBoolean("closeSendPkt", !toggleStates.getOrDefault("closeSendPkt", false));
        }
        if (targetAction != null && (targetAction.getType() == MacroActionType.OPEN_CONTAINER
                                  || targetAction.getType() == MacroActionType.INTERACT_ENTITY)) {
            List<String> selectedTargets = stringLists.getOrDefault("entityTargets", Collections.emptyList());
            String entityTarget = selectedTargets.isEmpty() ? "" : selectedTargets.get(0);
            workingTag.putString("entityTarget", entityTarget);
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_CHAT) {
            workingTag.putInt("fuzzyPercent",
                    autismclient.util.macro.WaitForChatAction.clampFuzzyPercent(getWaitChatFuzzyPercent()));
            if (!hasGuiWaitEnabled()) {
                workingTag.putString("waitGuiName", "");
            }
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.PLACE) {
            if (!workingTag.getBooleanOr("manualDirection", false)) {
                workingTag.remove("direction");
            }
            workingTag.putBoolean("waitForItem", toggleStates.getOrDefault("place_waitForItem", true));
            workingTag.putBoolean("silentSwitch", toggleStates.getOrDefault("place_silentSwitch", false));
        }
    }

    private void putDynamicBinding(String key, String template) {
        if (workingTag == null || key == null || key.isBlank()) return;
        CompoundTag bindings = workingTag.get(autismclient.util.macro.MacroDynamicBindings.TAG_KEY) instanceof CompoundTag existing
            ? existing : new CompoundTag();
        bindings.putString(key, template == null ? "" : template);
        workingTag.put(autismclient.util.macro.MacroDynamicBindings.TAG_KEY, bindings);
    }

    private void removeDynamicBinding(String key) {
        if (workingTag == null || key == null) return;
        if (!(workingTag.get(autismclient.util.macro.MacroDynamicBindings.TAG_KEY) instanceof CompoundTag bindings)) return;
        bindings.remove(key);
        if (bindings.isEmpty()) workingTag.remove(autismclient.util.macro.MacroDynamicBindings.TAG_KEY);
    }

    private void normalizeNbtBookWorkingTag() {
        if (workingTag == null) return;
        int pages = workingTag.getIntOr("pages", 100);
        if (pages <= 0) workingTag.putInt("pages", 100);
        int chars = workingTag.getIntOr("characters", 1024);
        if (chars <= 0) workingTag.putInt("characters", 1024);
        if (!workingTag.contains("sign")) workingTag.putBoolean("sign", true);
        if (!workingTag.contains("appendCount")) workingTag.putBoolean("appendCount", true);
        String source = currentEnumValue("dataSource");
        if (source == null || source.isBlank()) source = "Random";
        workingTag.putString("dataSource", source);
        if ("Random".equals(source)) {
            workingTag.putString("customText", "");
            workingTag.putString("customFilePath", "");
        } else if ("Pasted".equals(source)) {
            workingTag.putString("customFilePath", "");
        } else if ("File".equals(source)) {
            workingTag.putString("customText", "");
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseClicked(mx, my, button);
        if (raceStepSelectorOverlay.isVisible()) return raceStepSelectorOverlay.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;

        if (!enumDropdowns.isEmpty()) {
            if (CompactDropdown.mouseClicked(enumDropdowns, mx, my, button)) {
                AutismOverlayManager.get().bringToFront(this);
                return true;
            }
            if (CompactDropdown.isMenuOpen(enumDropdowns)) {
                return true;
            }
        }

        if (isOverCloseButton(mx, my, getBounds())) {
            closeEditor(false);
            return true;
        }
        if (isOverDragBar(mx, my)) {
            dragging   = true;
            dragOffX   = mx - panelX;
            dragOffY   = my - panelY;
            AutismOverlayManager.get().bringToFront(this);
            return true;
        }
        if (!isMouseOver(mx, my)) return false;
        if (collapsed) return true;

        AutismOverlayManager.get().bringToFront(this);

        MouseButtonEvent click = new MouseButtonEvent(imx, imy, new MouseButtonInfo(button, 0));
        boolean bodyInputAllowed = bodyInputClip == null || bodyInputClip.contains(imx, imy);
        AutismChatField focused = null;
        if (bodyInputAllowed) {
            for (Map.Entry<String, AutismChatField> entry : textFields.entrySet()) {
                if (!isPayloadTextFieldVisible(entry.getKey())) continue;

                String baseKey = entry.getKey().replaceAll("_\\d+$", "");
                FieldDef fld = getField(baseKey);
                if (fld != null && !isFieldVisible(fld)) continue;
                if (entry.getValue().mouseClicked(click, false)) { focused = entry.getValue(); break; }
            }
        }
        if (bodyInputAllowed && focused == null) {
            for (Map.Entry<String, AutismChatField> entry : addFields.entrySet()) {
                FieldDef fld = getField(entry.getKey());
                if (fld != null && !isFieldVisible(fld)) continue;
                if (entry.getValue().mouseClicked(click, false)) { focused = entry.getValue(); break; }
            }
        }
        if (focused != null) {
            AutismChatField ff = focused;
            textFields.values().forEach(f -> f.setFocused(f == ff));
            addFields.values().forEach(f  -> f.setFocused(f == ff));
            return true;
        }

        clearTextFieldFocus();

        if (button == 0 && isOverWaitChatFuzzySlider(imx, imy)) {
            waitChatFuzzySliderDragging = true;
            updateWaitChatFuzzyPercentFromMouse(imx);
            return true;
        }
        if (button == 0 && isOverRotateSmoothnessSlider(imx, imy)) {
            rotateSmoothnessSliderDragging = true;
            updateRotateSmoothnessFromMouse(imx);
            return true;
        }
        if (button == 0 && isOverXCarrySafeDelaySlider(imx, imy)) {
            xcarrySafeDelaySliderDragging = true;
            updateXCarrySafeDelayFromMouse(imx);
            return true;
        }

        if (bodyInputAllowed) {
            for (DirectScrollViewport vp : selectedScrollViewports.values()) {
                if (vp.isScrollbarHovered(mx, my)) {
                    vp.mouseClicked(mx, my, button);
                    return true;
                }
            }
            for (DirectScrollViewport vp : catalogScrollViewports.values()) {
                if (vp.isScrollbarHovered(mx, my)) {
                    vp.mouseClicked(mx, my, button);
                    return true;
                }
            }
        }

        for (ScrollDragRegion r : scrollDragRegions) {
            if (r.contains(imx, imy)) {
                activeScrollDragHandler = r.handler();
                activeScrollDragHandler.accept(imy);
                return true;
            }
        }

        for (HitRegion r : hitRegions) {
            if (r.contains(imx, imy) && r.fire(mx, my, button)) {
                refreshInteractiveLayout();
                return true;
            }
        }
        return true;
    }

    private boolean isPayloadTextFieldVisible(String key) {
        if ("payload_content".equals(key)) return payloadTabIndex == 0;
        if ("payload_hex_view".equals(key)) return payloadTabIndex == 1;
        if ("payload_utf8_view".equals(key)) return payloadTabIndex == 2;
        if ("payload_logical_view".equals(key)) return payloadTabIndex == 3;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseReleased(mx, my, button)) return true;
        if (raceStepSelectorOverlay.isVisible() && raceStepSelectorOverlay.mouseReleased(mx, my, button)) return true;
        boolean consumed = false;

        for (DirectScrollViewport vp : selectedScrollViewports.values()) {
            if (vp.isScrollbarDragging()) { vp.mouseReleased(); consumed = true; }
        }
        for (DirectScrollViewport vp : catalogScrollViewports.values()) {
            if (vp.isScrollbarDragging()) { vp.mouseReleased(); consumed = true; }
        }

        if (activeScrollDragHandler != null) { activeScrollDragHandler = null; consumed = true; }
        if (waitChatFuzzySliderDragging) { waitChatFuzzySliderDragging = false; consumed = true; }
        if (rotateSmoothnessSliderDragging) { rotateSmoothnessSliderDragging = false; consumed = true; }
        if (xcarrySafeDelaySliderDragging) { xcarrySafeDelaySliderDragging = false; consumed = true; }
        if (dragging) { dragging = false; consumed = true; }
        if (CompactDropdown.mouseReleased(enumDropdowns)) return true;
        for (Map.Entry<String, AutismChatField> entry : textFields.entrySet()) {
            if (!isPayloadTextFieldVisible(entry.getKey())) continue;
            if (entry.getValue().mouseReleased(mx, my, button)) consumed = true;
        }
        for (AutismChatField field : addFields.values()) {
            if (field.mouseReleased(mx, my, button)) consumed = true;
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseDragged(mx, my, button, dx, dy)) return true;
        if (raceStepSelectorOverlay.isVisible() && raceStepSelectorOverlay.mouseDragged(mx, my, button, dx, dy)) return true;
        if (CompactDropdown.mouseDragged(enumDropdowns, mx, my, button)) return true;
        if (waitChatFuzzySliderDragging) {
            updateWaitChatFuzzyPercentFromMouse((int) mx);
            return true;
        }
        if (rotateSmoothnessSliderDragging) {
            updateRotateSmoothnessFromMouse((int) mx);
            return true;
        }
        if (xcarrySafeDelaySliderDragging) {
            updateXCarrySafeDelayFromMouse((int) mx);
            return true;
        }
        if (dragging) {
            panelX = (int)(mx - dragOffX);
            panelY = (int)(my - dragOffY);
            AutismWindowLayout c = clampToScreen(this);
            panelX = c.x; panelY = c.y;
            return true;
        }

        for (Map.Entry<String, AutismChatField> entry : textFields.entrySet()) {
            if (!isPayloadTextFieldVisible(entry.getKey())) continue;
            if (entry.getValue().mouseDragged(mx, my, button, dx, dy)) return true;
        }
        for (AutismChatField field : addFields.values()) {
            if (field.mouseDragged(mx, my, button, dx, dy)) return true;
        }

        for (DirectScrollViewport vp : selectedScrollViewports.values()) {
            if (vp.isScrollbarDragging()) { vp.mouseDragged(mx, my); return true; }
        }
        for (DirectScrollViewport vp : catalogScrollViewports.values()) {
            if (vp.isScrollbarDragging()) { vp.mouseDragged(mx, my); return true; }
        }

        if (activeScrollDragHandler != null) {
            activeScrollDragHandler.accept((int) my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseScrolled(mx, my, amount);
        if (raceStepSelectorOverlay.isVisible()) return raceStepSelectorOverlay.mouseScrolled(mx, my, amount);
        if (!visible || !isMouseOver(mx, my)) return false;

        if (!enumDropdowns.isEmpty()
                && (CompactDropdown.isMenuOpen(enumDropdowns)
                    || CompactDropdown.isInsideOpenMenu(enumDropdowns, mx, my))) {
            if (CompactDropdown.mouseScrolled(enumDropdowns, mx, my, amount)) return true;
        }

        boolean bodyScrollAllowed = bodyInputClip == null || bodyInputClip.contains((int) mx, (int) my);
        if (bodyScrollAllowed) {
            for (Map.Entry<String, AutismChatField> entry : textFields.entrySet()) {
                if (!isPayloadTextFieldVisible(entry.getKey())) continue;
                if (entry.getValue().mouseScrolled(mx, my, amount)) return true;
            }
            for (AutismChatField field : addFields.values()) {
                if (field.mouseScrolled(mx, my, amount)) return true;
            }

            for (Map.Entry<String, DirectScrollViewport> entry : selectedScrollViewports.entrySet()) {
                DirectScrollViewport vp = entry.getValue();
                if (vp.contains(mx, my)) {
                    vp.mouseScrolled(mx, my, amount);
                    return true;
                }
            }

            for (Map.Entry<String, DirectScrollViewport> entry : catalogScrollViewports.entrySet()) {
                DirectScrollViewport vp = entry.getValue();
                if (vp.contains(mx, my)) {
                    vp.mouseScrolled(mx, my, amount);
                    return true;
                }
            }
        }

        if (craftRecipeListBounds != null
                && my >= craftRecipeListBounds[0]
                && my < craftRecipeListBounds[0] + craftRecipeListBounds[1]) {
            craftRecipeScrollOffset = Math.max(0, craftRecipeScrollOffset - (int)(amount * CATALOG_ITEM_H));
            return true;
        }

        scrollOffset = Math.max(0, scrollOffset - (int)(amount * 12));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
        if (raceStepSelectorOverlay.isVisible()) return raceStepSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
        if (CompactDropdown.isMenuOpen(enumDropdowns)) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                CompactDropdown.closeOpenMenu(enumDropdowns);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (cancelCaptureIfActive()) return true;
            if (hasTextFieldFocused()) { clearTextFieldFocus(); return true; }
            closeEditor(false);
            return true;
        }
        KeyEvent ki = new KeyEvent(keyCode, scanCode, modifiers);

        for (AutismChatField f : textFields.values()) {
            if (f.isFocused()) return f.keyPressed(ki);
        }
        for (AutismChatField f : addFields.values()) {
            if (f.isFocused()) return f.keyPressed(ki);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.charTyped(chr, modifiers);
        if (raceStepSelectorOverlay.isVisible()) return raceStepSelectorOverlay.charTyped(chr, modifiers);
        CharacterEvent ci = new CharacterEvent(chr);
        for (AutismChatField f : textFields.values()) {
            if (f.isFocused() && f.charTyped(ci)) return true;
        }
        for (AutismChatField f : addFields.values()) {
            if (f.isFocused() && f.charTyped(ci)) return true;
        }
        return false;
    }

    private boolean isFieldVisible(FieldDef field) {
        if (field == null) return false;
        if ("safeClickDelayTicks".equals(field.key())) {
            return isXCarrySafeClickTimingVisible();
        }
        if ("safeClickDelayAfterPickup".equals(field.key()) || "safeClickDelayBeforeReturn".equals(field.key())) {
            return isXCarrySafeClickTimingVisible();
        }
        if (isGuiNameField(field.key()) && targetAction instanceof WaitsForGui) {
            return hasGuiWaitEnabled();
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.INVENTORY) {
            String mode = currentEnumValue("mode");
            if ("waitForGuiBefore".equals(field.key()) || "waitForGuiAfter".equals(field.key())) {
                return "OPEN".equals(mode);
            }
            if ("sendPacket".equals(field.key())) {
                return "CLOSE".equals(mode);
            }
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_ENTITY) {
            String checkMode = currentEnumValue("checkMode");
            if ("centerOnPlayer".equals(field.key())
                    || "radius".equals(field.key())
                    || "mustBeLookingAt".equals(field.key())) {
                return "RADIUS".equals(checkMode) || ("NEARBY".equals(checkMode) && !"mustBeLookingAt".equals(field.key()));
            }
            if ("pos".equals(field.key())) {
                return ("RADIUS".equals(checkMode) || "NEARBY".equals(checkMode)) && !toggleStates.getOrDefault("centerOnPlayer", false);
            }
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
            String mode = currentEnumValue("mode");
            String openMode = currentEnumValue("openMode");
            boolean isDupeMode = "DUPE".equals(mode) || "DUPE_SPAM".equals(mode);
            boolean isDupeSpam = "DUPE_SPAM".equals(mode);

            if ("targetItems".equals(field.key())) {
                return isDupeMode;
            }

            if ("openCommand".equals(field.key())) {
                return isDupeMode && "COMMAND".equals(openMode);
            }

            if ("containerPos".equals(field.key())) {
                return isDupeMode && "CONTAINER".equals(openMode);
            }

            if ("spamCount".equals(field.key()) || "spamDelayMs".equals(field.key())) {
                return isDupeSpam;
            }

            if ("openMode".equals(field.key()) ||
                "dupeVector".equals(field.key()) ||
                "iterations".equals(field.key()) ||
                "maxTransferAttempts".equals(field.key()) ||
                "transferRetryDelayMs".equals(field.key())) {
                return isDupeMode;
            }
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.PACKET_GATE
                && "DISABLE_GATE".equals(currentEnumValue("mode"))) {
            String key = field.key();
            return "mode".equals(key) || "gateId".equals(key) || "flushOnDisable".equals(key);
        }

        if (targetAction != null && (targetAction.getType() == MacroActionType.FPS
                || targetAction.getType() == MacroActionType.SPING)) {
            String endMode = currentEnumValue("endMode");
            boolean timeout = endMode.isEmpty() || "TIMEOUT".equals(endMode);
            boolean steps = "STEPS".equals(endMode);
            boolean useTicks = toggleStates.getOrDefault("useTicks", false);
            String key = field.key();
            if ("stepCount".equals(key)) return steps;
            if ("useTicks".equals(key)) return timeout;
            if ("durationMs".equals(key)) return timeout && !useTicks;
            if ("durationTicks".equals(key)) return timeout && useTicks;
            if ("continueNextActions".equals(key)) return timeout;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
            String key = field.key();
            if ("listFilterText".equals(key) || "listStripPrefix".equals(key) || "listPickPosition".equals(key)) {
                String source = currentEnumValue("source");
                if (!"COMMAND_AUTOFILL".equals(source) && !"TABLIST".equals(source)) return false;
                String filter = currentEnumValue("listFilter");
                if ("listFilterText".equals(key)) return !"NONE".equals(filter);
                if ("listStripPrefix".equals(key)) return "PREFIX".equals(filter) || "SUFFIX".equals(filter);
                return "POSITION".equals(currentEnumValue("listSelection"));
            }
        }

        if (!field.hasShowWhen()) return true;
        boolean cond;
        if (field.showWhenValue() != null && !field.showWhenValue().isEmpty()) {
            String currentValue = currentEnumValue(field.showWhenKey());
            cond = false;
            for (String allowed : field.showWhenValue().split("\\|")) {
                if (allowed.equals(currentValue)) {
                    cond = true;
                    break;
                }
            }
        } else {
            cond = toggleStates.getOrDefault(field.showWhenKey(), false);
        }
        boolean visible = field.showWhenInverted() ? !cond : cond;

        if (visible && !field.showWhenInverted()) {
            FieldDef dep = getField(field.showWhenKey());
            if (dep != null && !isFieldVisible(dep)) visible = false;
        }
        return visible;
    }

    private boolean isXCarrySafeClickTimingVisible() {
        return targetAction != null
                && targetAction.getType() == MacroActionType.XCARRY
                && "PUT_IN".equals(currentEnumValue("mode"))
                && "SAFE_CLICK".equals(currentEnumValue("transferMode"));
    }

    private boolean isGuiNameField(String key) {
        return "guiName".equals(key) || "waitGuiName".equals(key);
    }

    private boolean hasGuiWaitEnabled() {
        return toggleStates.getOrDefault("waitForGuiBefore", false)
                || toggleStates.getOrDefault("waitForGuiAfter", false)
                || toggleStates.getOrDefault("waitForGui", false);
    }

    private String currentEnumValue(String key) {
        if (schema == null) return "";
        for (FieldDef field : schema.fields()) {
            if (!field.key().equals(key) || field.type() != FieldType.ENUM) continue;
            List<String> opts = field.enumOptions();
            if (opts.isEmpty()) return "";
            int idx = Math.min(enumIndices.getOrDefault(key, 0), opts.size() - 1);
            return opts.get(idx);
        }
        return "";
    }

    private void prepareWorkingTagForEditor(MacroAction action) {
        if (action == null || workingTag == null) return;

        if (action instanceof autismclient.util.macro.SelectSlotAction selectSlotAction) {
            primeStructuredField("itemName", selectSlotAction.itemTarget, selectSlotAction.itemName, null);
        }
        if (action instanceof autismclient.util.macro.UseItemAction useItemAction) {
            primeStructuredField("itemName", useItemAction.itemTarget, useItemAction.itemName, "slot");
            if (useItemAction.slot >= 0) workingTag.putInt("slot", useItemAction.slot);
        }
        if (action instanceof autismclient.util.macro.WaitForCooldownAction waitForCooldownAction) {
            primeStructuredField("itemName", waitForCooldownAction.itemTarget, waitForCooldownAction.itemName, null);
        }
        if (action instanceof autismclient.util.macro.CloseGuiAction closeGuiAction) {
            primeStructuredField("itemName", closeGuiAction.itemTarget, closeGuiAction.itemName, "targetSlot");
        }
        if (action instanceof autismclient.util.macro.SwapSlotsAction swapSlotsAction) {
            primeStructuredField("fromItemName", swapSlotsAction.fromItemTarget, swapSlotsAction.fromItemName, null);
            primeStructuredField("toItemName", swapSlotsAction.toItemTarget, swapSlotsAction.toItemName, null);
        }
        if (action instanceof autismclient.util.macro.StoreItemAction storeItemAction) {
            primeStructuredList("targetItems", storeItemAction.itemTargets, storeItemAction.targetItems);
        }
        if (action instanceof autismclient.util.macro.XCarryAction xCarryAction) {
            primeStructuredList("entries", xCarryAction.entryTargets, xCarryAction.entries);
            workingTag.putInt("safeClickDelayTicks",
                    autismclient.util.macro.XCarryAction.clampSafeClickDelayTicks(xCarryAction.safeClickDelayTicks));
            workingTag.putBoolean("safeClickDelayAfterPickup", xCarryAction.safeClickDelayAfterPickup);
            workingTag.putBoolean("safeClickDelayBeforeReturn", xCarryAction.safeClickDelayBeforeReturn);
            ListTag destinations = new ListTag();
            xCarryAction.resizeDestinations(xCarryAction.entries.size());
            xCarryAction.resizeAmountSettings(xCarryAction.entries.size());
            ListTag amountModes = new ListTag();
            ListTag amounts = new ListTag();
            for (int i = 0; i < xCarryAction.entries.size(); i++) {
                destinations.add(StringTag.valueOf(String.valueOf(xCarryAction.destinationFor(i))));
                amountModes.add(StringTag.valueOf(xCarryAction.amountModeFor(i).name()));
                amounts.add(StringTag.valueOf(String.valueOf(xCarryAction.amountFor(i))));
            }
            workingTag.put("entryDestinations", destinations);
            workingTag.put("entryAmountModes", amountModes);
            workingTag.put("entryAmounts", amounts);
        }
        if (action instanceof autismclient.util.macro.InventoryAuditAction inventoryAuditAction) {
            primeStructuredList("targetItems", inventoryAuditAction.itemTargets, inventoryAuditAction.targetItems);
        }
        if (action instanceof autismclient.util.macro.PlaceAction placeAction) {
            primeStructuredField("itemName", placeAction.itemTarget, placeAction.itemName, null);
            normalizePlaceWorkingTag();
        }
    }

    private void normalizePlaceWorkingTag() {
        if (workingTag == null) return;
        workingTag.remove("slot");
        if (!workingTag.getBooleanOr("manualDirection", false)) {
            workingTag.remove("direction");
        }
    }

    private ItemTarget buildPlaceItemTarget() {
        AutismChatField nameF = textFields.get("itemName");
        AutismChatField slotF = textFields.get("place_itemSlot");
        return buildEntryTargetFromEditor(
                nameF == null ? "" : nameF.getText(),
                slotF == null ? "" : slotF.getText(),
                editorItemFields.get("itemName")
        );
    }

    private void syncPlaceItemEditorFields() {
        if (targetAction == null || targetAction.getType() != MacroActionType.PLACE) return;
        ItemTarget target = buildPlaceItemTarget();
        if (target.hasSlot() || target.hasIdentity()) {
            editorItemFields.put("itemName", target.copy());
        } else {
            editorItemFields.remove("itemName");
        }
    }

    private void primeStructuredField(String key, ItemTarget source, String legacyValue, String slotKey) {
        ItemTarget resolved = resolveEditorTarget(source, legacyValue);
        if (!resolved.hasSlot() && !resolved.hasIdentity()) return;

        editorItemFields.put(key, resolved.copy());
        workingTag.putString(key, resolved.editorText());
        if (slotKey != null && resolved.hasSlot()) {
            workingTag.putInt(slotKey, resolved.slot);
        }
    }

    private void refreshItemTextDisplayProviders() {
        bindStructuredItemFieldDisplay("itemName");
        bindStructuredItemFieldDisplay("fromItemName");
        bindStructuredItemFieldDisplay("toItemName");
        bindTransientItemEditorDisplay("_item_add");
        bindTransientItemEditorDisplay("_drop_add");
        bindTransientItemEditorDisplay("_wsc_add");
        bindTransientItemEditorDisplay("targetItems");
    }

    private void bindStructuredItemFieldDisplay(String key) {
        AutismChatField field = textFields.get(key);
        if (field == null) return;
        field.setDisplayTextProvider(value -> resolveStructuredItemFieldDisplay(key, value));
    }

    private void bindTransientItemEditorDisplay(String key) {
        AutismChatField field = addFields.get(key);
        if (field == null) return;
        field.setDisplayTextProvider(value -> resolveTransientItemEditorDisplay(key, value));
    }

    private Component resolveStructuredItemFieldDisplay(String key, String value) {
        return richItemFieldDisplay(editorItemFields.get(key), value);
    }

    private Component resolveTransientItemEditorDisplay(String key, String value) {
        return switch (key) {
            case "_item_add" -> richItemFieldDisplay(
                    itemAction != null && itemEditIndex >= 0 && itemEditIndex < itemAction.itemNames.size()
                            ? targetAt(itemAction.itemTargets, itemAction.itemNames, itemEditIndex)
                            : null,
                    value
            );
            case "_drop_add" -> richItemFieldDisplay(
                    dropAction != null && dropEditIndex >= 0 && dropEditIndex < dropAction.itemNames.size()
                            ? targetAt(dropAction.itemTargets, dropAction.itemNames, dropEditIndex)
                            : null,
                    value
            );
            case "_wsc_add" -> richItemFieldDisplay(
                    wscEntries != null && wscEditIndex >= 0 && wscEditIndex < wscEntries.size()
                            ? wscEntries.get(wscEditIndex).resolvedTarget()
                            : null,
                    value
            );
            case "targetItems" -> richItemFieldDisplay(resolveSelectedTargetItemsEditorTarget(), value);
            default -> null;
        };
    }

    private ItemTarget resolveSelectedTargetItemsEditorTarget() {
        int storeIndex = stringListEditIndex.getOrDefault("store_items_selected", -1);
        int auditIndex = stringListEditIndex.getOrDefault("audit_items_selected", -1);
        int index = storeIndex >= 0 ? storeIndex : auditIndex;
        if (index < 0) return null;
        List<ItemTarget> targets = buildStructuredListTargets("targetItems");
        if (index >= 0 && index < targets.size()) {
            ItemTarget target = targets.get(index);
            if (target != null && (target.hasSlot() || target.hasIdentity())) return target;
        }
        return null;
    }

    private Component richItemFieldDisplay(ItemTarget target, String value) {
        if (target == null) return null;
        Component rich = target.editorComponent(value);
        if (rich == null) return null;
        String safeValue = value == null ? "" : value;
        return safeValue.equals(rich.getString()) ? rich.copy() : null;
    }

    private void primeStructuredList(String key, List<ItemTarget> source, List<String> legacyValues) {
        List<ItemTarget> resolved = copyEditorTargets(source, legacyValues);
        if (resolved.isEmpty()) return;

        editorItemLists.put(key, ItemTarget.copyList(resolved));
        ListTag listTag = new ListTag();
        for (ItemTarget target : resolved) {
            if (target == null) continue;
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) listTag.add(StringTag.valueOf(entry));
        }
        workingTag.put(key, listTag);
    }

    private ItemTarget resolveEditorTarget(ItemTarget source, String legacyValue) {
        if (source != null && (source.hasSlot() || source.hasIdentity())) return source.copy();
        return ItemTarget.fromLegacyEntry(legacyValue);
    }

    private List<ItemTarget> copyEditorTargets(List<ItemTarget> source, List<String> legacyValues) {
        List<ItemTarget> resolved = ItemTarget.copyList(source);
        if (!resolved.isEmpty()) return resolved;

        List<ItemTarget> parsed = new ArrayList<>();
        if (legacyValues == null) return parsed;
        for (String legacyValue : legacyValues) {
            ItemTarget target = ItemTarget.fromLegacyEntry(legacyValue);
            if (target.hasSlot() || target.hasIdentity()) parsed.add(target);
        }
        return parsed;
    }

    private ItemTarget buildStructuredFieldTarget(String key, String slotKey) {
        AutismChatField valueField = textFields.get(key);
        String text = valueField == null ? "" : valueField.getText().strip();
        ItemTarget previous = editorItemFields.get(key);
        int slot = -1;
        boolean slotDriven = false;
        if (slotKey != null) {
            AutismChatField slotField = textFields.get(slotKey);
            slot = slotField == null ? -1 : parseHandlerSlotField(slotField.getText(), -1);
            slotDriven = true;
        } else if (previous != null && previous.hasSlot()) {
            slot = previous.slot;
        }

        if (text.isEmpty() && (!slotDriven || slot < 0)) {
            return new ItemTarget();
        }

        if (previous != null && text.equals(previous.editorText())) {
            ItemTarget preserved = previous.copy();
            if (slotDriven) preserved.slot = slot;
            return preserved;
        }

        if (previous != null && previous.hasRichText() && !text.isEmpty()) {
            ItemTarget preserved = previous.withEditedRichDisplay(text);
            if (slot >= 0) preserved.slot = slot;
            else if (slotDriven) preserved.slot = -1;
            return preserved;
        }

        String raw;
        if (slot >= 0 && !text.isEmpty()) raw = "#" + slot + "|" + text;
        else if (slot >= 0) raw = "#" + slot;
        else raw = text;
        return ItemTarget.fromLegacyEntry(raw);
    }

    private List<ItemTarget> buildStructuredListTargets(String key) {
        List<String> values = stringLists.getOrDefault(key, Collections.emptyList());
        List<ItemTarget> previous = editorItemLists.getOrDefault(key, Collections.emptyList());
        boolean[] used = new boolean[previous.size()];
        List<ItemTarget> rebuilt = new ArrayList<>();
        for (String value : values) {
            String entry = value == null ? "" : value.strip();
            if (entry.isEmpty()) continue;

            ItemTarget preserved = null;
            for (int i = 0; i < previous.size(); i++) {
                ItemTarget candidate = previous.get(i);
                if (used[i] || candidate == null) continue;
                if (entry.equals(candidate.toLegacyEntry())) {
                    preserved = candidate.copy();
                    used[i] = true;
                    break;
                }
            }
            if (preserved == null) preserved = ItemTarget.fromLegacyEntry(entry);
            if (isXCarryListKey(key) && preserved.hasIdentity()) preserved.slot = -1;
            if (preserved.hasSlot() || preserved.hasIdentity()) rebuilt.add(preserved);
        }
        editorItemLists.put(key, ItemTarget.copyList(rebuilt));
        return rebuilt;
    }

    private void rewriteStructuredEditorTargets() {
        if (targetAction == null || workingTag == null) return;

        if (targetAction instanceof autismclient.util.macro.SelectSlotAction) {
            writeStructuredField("itemName", buildStructuredFieldTarget("itemName", null));
        }
        if (targetAction instanceof autismclient.util.macro.UseItemAction) {
            writeStructuredField("itemName", buildStructuredFieldTarget("itemName", "slot"));
        }
        if (targetAction instanceof autismclient.util.macro.WaitForCooldownAction) {
            writeStructuredField("itemName", buildStructuredFieldTarget("itemName", null));
        }
        if (targetAction instanceof autismclient.util.macro.CloseGuiAction) {
            writeStructuredField("itemName", buildStructuredFieldTarget("itemName", "targetSlot"));
        }
        if (targetAction instanceof autismclient.util.macro.SwapSlotsAction) {
            writeStructuredField("fromItemName", buildStructuredFieldTarget("fromItemName", null));
            writeStructuredField("toItemName", buildStructuredFieldTarget("toItemName", null));
        }
        if (targetAction instanceof autismclient.util.macro.PlaceAction) {
            writeStructuredField("itemName", buildPlaceItemTarget());
            workingTag.remove("slot");
        }
        if (targetAction instanceof autismclient.util.macro.StoreItemAction) {
            writeStructuredList("targetItems", buildStructuredListTargets("targetItems"));
        }
        if (targetAction instanceof autismclient.util.macro.XCarryAction) {
            writeStructuredList("entries", buildStructuredListTargets("entries"));
            int count = stringLists.getOrDefault("entries", Collections.emptyList()).size();
            syncXCarryDestinationCount(count);
            syncXCarryAmountCount(count);
        }
        if (targetAction instanceof autismclient.util.macro.InventoryAuditAction) {
            writeStructuredList("targetItems", buildStructuredListTargets("targetItems"));
        }
    }

    private void writeStructuredField(String key, ItemTarget target) {
        if (target == null || (!target.hasSlot() && !target.hasIdentity())) {
            workingTag.remove(key);
            editorItemFields.remove(key);
            return;
        }
        workingTag.put(key, target.toTag());
        editorItemFields.put(key, target.copy());
    }

    private void writeStructuredList(String key, List<ItemTarget> targets) {
        workingTag.put(key, ItemTarget.toTagList(targets));
    }

    private void refreshInteractiveLayout() {
        hitRegions.clear();
        scrollDragRegions.clear();
        int neededH = HEADER_HEIGHT + PAD + computeContentH() + FOOTER_H + PAD;
        int minH = currentMinPanelHeight();
        int maxH = Math.max(minH, AutismUiScale.getVirtualScreenHeight() * 4 / 5);
        panelH = Math.max(minH, Math.min(maxH, neededH));
        AutismWindowLayout clamped = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelW, panelH, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelW = clamped.width;
        panelH = clamped.height;
    }

    private void toggleWscEditSelection(int index) {
        if (wscEditIndex == index) {
            wscEditIndex = -1;
            clearWscEditorFields();
        } else {
            wscEditIndex = index;
            if (wscEntries != null && index < wscEntries.size()) {
                syncWscEditorFromEntry(wscEntries.get(index));
            }
        }
    }

    private void syncWscEditorFromEntry(WaitForSlotChangeAction.WaitEntry e) {
        AutismChatField addF   = addFields.get("_wsc_add");
        AutismChatField slotF  = textFields.get("wsc_slot");
        AutismChatField countF = textFields.get("wsc_count");
        if (addF == null || slotF == null) return;
        suppressWscLiveUpdate = true;
        ItemTarget target = e.resolvedTarget();
        addF.setText(target.editorText());
        slotF.setText(target.hasSlot() ? String.valueOf(target.slot) : "");
        if (countF != null) countF.setText(String.valueOf(Math.max(1, e.targetCount)));
        suppressWscLiveUpdate = false;
    }

    private void clearWscEditorFields() {
        AutismChatField addF   = addFields.get("_wsc_add");
        AutismChatField slotF  = textFields.get("wsc_slot");
        AutismChatField countF = textFields.get("wsc_count");
        suppressWscLiveUpdate = true;
        if (addF   != null) addF.setText("");
        if (slotF  != null) slotF.setText("");
        if (countF != null) countF.setText("");
        suppressWscLiveUpdate = false;
    }

    private void handleWscEntryEditorChanged() {
        if (suppressWscLiveUpdate || wscEntries == null) return;
        if (wscEditIndex < 0 || wscEditIndex >= wscEntries.size()) return;
        AutismChatField addF  = addFields.get("_wsc_add");
        AutismChatField slotF = textFields.get("wsc_slot");
        if (addF == null || slotF == null) return;
        String nameText = addF.getText().strip();
        String slotText = slotF.getText().strip();
        WaitForSlotChangeAction.WaitEntry entry = wscEntries.get(wscEditIndex);
        ItemTarget target = buildEntryTargetFromEditor(nameText, slotText, entry.resolvedTarget());
        String rawTarget = target.toLegacyEntry();
        String norm = rawTarget.isEmpty() ? "" :
                autismclient.util.macro.StoreItemAction.normalizeTargetEntry(rawTarget);
        if (norm == null) norm = "";
        if (!wscTargetExistsOtherThan(norm, wscEditIndex)) {
            entry.target = norm;
            entry.itemTarget = target;
        }
    }

    private void handleWscCountChanged() {
        if (suppressWscLiveUpdate || wscEntries == null) return;
        AutismChatField countF = textFields.get("wsc_count");
        if (countF == null) return;
        int count;
        try { count = Integer.parseInt(countF.getText().strip()); }
        catch (NumberFormatException ignored) { return; }
        count = Math.max(1, count);
        if (wscEditIndex >= 0 && wscEditIndex < wscEntries.size()) {
            wscEntries.get(wscEditIndex).targetCount = count;
        } else {
            wscAddCount = count;
        }
    }

    private void applyWscAddEntry(AutismChatField addF, AutismChatField slotF) {
        if (wscEntries == null) return;
        String nameText = addF.getText().strip();
        String slotText = slotF.getText().strip();
        ItemTarget target = buildEntryTargetFromEditor(nameText, slotText, null);
        String rawTarget = target.toLegacyEntry();
        String norm = rawTarget.isEmpty() ? "" :
                autismclient.util.macro.StoreItemAction.normalizeTargetEntry(rawTarget);
        if (norm == null) norm = "";
        if (!wscTargetExists(norm)) {
            WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry(norm, wscAddMode, wscAddCount);
            entry.itemTarget = target;
            entry.target = norm;
            wscEntries.add(entry);
            addF.setText("");
            slotF.setText("");
        }
    }

    private boolean wscTargetExists(String normTarget) {
        if (wscEntries == null) return false;
        for (WaitForSlotChangeAction.WaitEntry e : wscEntries) {
            if (e.target.equals(normTarget)) return true;
        }
        return false;
    }

    private boolean wscTargetExistsOtherThan(String normTarget, int ignoreIdx) {
        if (wscEntries == null) return false;
        for (int i = 0; i < wscEntries.size(); i++) {
            if (i != ignoreIdx && wscEntries.get(i).target.equals(normTarget)) return true;
        }
        return false;
    }

    private boolean isWaitSlotChangeEntryKey(String key) {
        return targetAction != null
                && targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE
                && "targetEntries".equals(key);
    }

    private boolean isStoreItemTargetListKey(String key) {
        return targetAction != null
                && targetAction.getType() == MacroActionType.STORE_ITEM
                && "targetItems".equals(key);
    }

    private boolean isInventoryAuditTargetListKey(String key) {
        return targetAction != null
                && targetAction.getType() == MacroActionType.INVENTORY_AUDIT
                && "targetItems".equals(key);
    }

    private boolean usesStoreTargetFormatting(String key) {
        return isStoreItemTargetListKey(key) || isInventoryAuditTargetListKey(key);
    }

    private String buildHandlerEntryFromEditor(String nameText, String slotText) {
        String trimmedName = nameText == null ? "" : nameText.trim();
        int slot = parseHandlerSlotField(slotText, -1);
        if (!trimmedName.isEmpty() && slot >= 0) return "#" + slot + "|" + trimmedName;
        if (!trimmedName.isEmpty()) return trimmedName;
        return slot >= 0 ? "#" + slot : null;
    }

    private ItemTarget captureItemTarget(net.minecraft.world.inventory.Slot slot, String itemName, String registryId, int visibleSlot) {
        if (slot != null && slot.getItem() != null && !slot.getItem().isEmpty()) {
            return ItemTarget.capture(slot.getItem(), visibleSlot);
        }
        if (visibleSlot >= 0) return ItemTarget.slotOnly(visibleSlot);
        String fallback = registryId != null && !registryId.isBlank()
                ? registryId.trim()
                : (itemName == null ? "" : itemName.trim());
        return ItemTarget.fromLegacyEntry(fallback);
    }

    private static ItemTarget stripSlotFromTarget(ItemTarget target) {
        if (target == null) return new ItemTarget();
        if (!target.hasSlot()) return target;
        ItemTarget copy = target.copy();
        copy.slot = -1;
        return copy;
    }

    private static ItemTarget storeCaptureTarget(ItemTarget target) {
        if (target == null) return new ItemTarget();
        if (target.hasIdentity()) return stripSlotFromTarget(target);
        return target.copy();
    }

    private ItemTarget buildEntryTargetFromEditor(String nameText, String slotText, ItemTarget previous) {
        String trimmedName = nameText == null ? "" : nameText.strip();
        int slot = parseHandlerSlotField(slotText, -1);
        if (trimmedName.isEmpty() && slot < 0) return new ItemTarget();

        if (previous != null && trimmedName.equals(previous.editorText())) {
            ItemTarget kept = previous.copy();
            kept.slot = slot;
            return kept;
        }

        if (previous != null && previous.hasRichText() && !trimmedName.isEmpty()) {
            ItemTarget preserved = previous.withEditedRichDisplay(trimmedName);
            preserved.slot = slot;
            return preserved;
        }

        String raw;
        if (slot >= 0 && !trimmedName.isEmpty()) raw = "#" + slot + "|" + trimmedName;
        else if (slot >= 0) raw = "#" + slot;
        else raw = trimmedName;
        return ItemTarget.fromLegacyEntry(raw);
    }

    private ItemTarget targetAt(List<ItemTarget> targets, List<String> legacyEntries, int index) {
        if (targets != null && index >= 0 && index < targets.size()) {
            ItemTarget target = targets.get(index);
            if (target != null && (target.hasSlot() || target.hasIdentity())) return target;
        }
        if (legacyEntries != null && index >= 0 && index < legacyEntries.size()) {
            return ItemTarget.fromLegacyEntry(legacyEntries.get(index));
        }
        return new ItemTarget();
    }

    private void preserveCapturedListTarget(String key, List<String> entries, ItemTarget capturedTarget) {
        if (key == null || entries == null || capturedTarget == null) return;
        String normalized = capturedTarget.toLegacyEntry();
        if (usesStoreTargetFormatting(key) || isWaitSlotChangeEntryKey(key)) {
            normalized = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(normalized);
        } else if (isXCarryListKey(key)) {
            normalized = normalizeXCarryEntry(normalized);
        }
        if (normalized == null || normalized.isBlank()) return;

        int index = entries.indexOf(normalized);
        if (index < 0) return;

        List<ItemTarget> targets = buildStructuredListTargets(key);
        while (targets.size() < entries.size()) {
            targets.add(new ItemTarget());
        }
        ItemTarget stored = isXCarryListKey(key) ? stripSlotFromTarget(capturedTarget) : capturedTarget.copy();
        if (!normalized.equals(stored.toLegacyEntry())) {
            ItemTarget normalizedTarget = ItemTarget.fromLegacyEntry(normalized);
            stored.slot = normalizedTarget.slot;
            if (!normalizedTarget.hasSlot() && !stored.hasIdentity()) {
                stored = normalizedTarget;
            }
        }
        targets.set(index, stored);
        editorItemLists.put(key, ItemTarget.copyList(targets));
    }

    private void setTargetAt(List<ItemTarget> targets, List<String> legacyEntries, int index, ItemTarget target) {
        if (targets == null || legacyEntries == null || index < 0) return;
        while (targets.size() <= index) targets.add(new ItemTarget());
        while (legacyEntries.size() <= index) legacyEntries.add("");
        ItemTarget stored = target == null ? new ItemTarget() : target;
        targets.set(index, stored);
        legacyEntries.set(index, stored.toLegacyEntry());
    }

    private void addTargetEntry(List<ItemTarget> targets, List<String> legacyEntries, ItemTarget target) {
        if (targets == null || legacyEntries == null || target == null) return;
        targets.add(target);
        legacyEntries.add(target.toLegacyEntry());
    }

    private void trimTargetEntries(List<ItemTarget> targets, int size) {
        if (targets == null) return;
        while (targets.size() > size) targets.remove(targets.size() - 1);
        while (targets.size() < size) targets.add(new ItemTarget());
    }

    private boolean isEditableStringList(String key) {
        return isXCarryListKey(key) || isStoreItemTargetListKey(key)
                || isInventoryAuditTargetListKey(key) || isWaitSlotChangeEntryKey(key);
    }

    private boolean isXCarryListKey(String key) {
        return targetAction != null
                && targetAction.getType() == MacroActionType.XCARRY
                && "entries".equals(key);
    }

    private boolean isOpenContainerEntityListKey(String key) {
        return targetAction != null
                && (targetAction.getType() == MacroActionType.OPEN_CONTAINER
                 || targetAction.getType() == MacroActionType.INTERACT_ENTITY)
                && "entityTargets".equals(key);
    }

    private boolean isReplaceOnCaptureEntityKey(String key) {
        return false;
    }

    private CaptureListAddResult tryAddCapturedStoreItemEntry(net.minecraft.world.inventory.Slot slot,
                                                              String itemName,
                                                              String registryId,
                                                              int visibleSlot,
                                                              boolean slotMode,
                                                              List<String> entries) {
        if (slot == null || visibleSlot < 0) {
            return new CaptureListAddResult(false, "Could not read that slot", CAPTURE_TOAST_ERROR);
        }

        ItemTarget captured = slotMode
                ? ItemTarget.slotOnly(visibleSlot)
                : storeCaptureTarget(captureItemTarget(slot, itemName, registryId, visibleSlot));

        if (!captured.hasIdentity()) {
            String mode = currentEnumValue("mode");
            boolean playerInventorySlot = autismclient.util.AutismInventoryHelper.isInventorySlot(MC, slot);
            if ("LOOT".equals(mode) && playerInventorySlot) {
                return new CaptureListAddResult(false, "Loot slot numbers must be chest/GUI slots - pick an item to add it by name", CAPTURE_TOAST_ERROR);
            }
            if ("STORE".equals(mode) && !playerInventorySlot) {
                return new CaptureListAddResult(false, "Store slot numbers must be inventory slots - pick an item to add it by name", CAPTURE_TOAST_ERROR);
            }
        }

        return tryAddCapturedStringListEntry(findField("targetItems"), "targetItems", entries, captured.toLegacyEntry());
    }

    private String normalizeXCarryEntry(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        ItemTarget parsed = ItemTarget.fromLegacyEntry(trimmed);
        if (parsed.hasIdentity()) {
            parsed.slot = -1;
            String entry = parsed.toLegacyEntry();
            return entry == null || entry.isBlank() ? null : entry;
        }
        if (!trimmed.startsWith("#") && trimmed.matches("\\d+")) {
            trimmed = "#" + trimmed;
        }
        return autismclient.util.macro.XCarryAction.normalizeEntry(trimmed);
    }

    private boolean addStringListEntry(FieldDef field, List<String> entries, String rawEntry) {
        if (entries == null || rawEntry == null) return false;
        String entry = rawEntry.strip();
        if (entry.isEmpty()) return false;

        if (field != null && isXCarryListKey(field.key())) {
            entry = normalizeXCarryEntry(entry);
            if (entry == null) return false;
            int limit = currentXCarryEntryLimit();
            if (entries.size() >= limit) {
                AutismClientMessaging.sendPrefixed(xCarryLimitMessage(limit));
                return false;
            }
            entries.add(entry);
            syncXCarryDestinationCount(entries.size());
            syncXCarryAmountCount(entries.size());
            return true;
        }

        if (field != null && isOpenContainerEntityListKey(field.key())) {
            if (entry.isBlank()) return false;
            if (!entries.contains(entry)) entries.add(entry);
            return true;
        }

        if (field != null && (usesStoreTargetFormatting(field.key()) || isWaitSlotChangeEntryKey(field.key()))) {
            entry = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
            if (entry == null || entry.isBlank() || entries.contains(entry)) return false;
            entries.add(entry);
            return true;
        }

        if (field != null && "players".equals(field.key())) {
            if (containsIgnoreCase(entries, entry)) return false;
            entries.add(entry);
            return true;
        }

        entries.add(entry);
        return true;
    }

    private CaptureListAddResult tryAddCapturedStringListEntry(FieldDef field, String key, List<String> entries, String rawEntry) {
        if (entries == null) return null;
        String entry = rawEntry == null ? "" : rawEntry.strip();
        if (entry.isEmpty()) {
            return new CaptureListAddResult(false, "Nothing to add from that slot", CAPTURE_TOAST_ERROR);
        }

        if (field != null && isXCarryListKey(field.key())) {
            entry = normalizeXCarryEntry(entry);
            if (entry == null || entry.isBlank()) {
                return new CaptureListAddResult(false, "Could not read that XCarry entry", CAPTURE_TOAST_ERROR);
            }
            String formatted = formatStringListEntry(key, entry);
            int limit = currentXCarryEntryLimit();
            if (entries.size() >= limit) {
                return new CaptureListAddResult(false, xCarryLimitMessage(limit), CAPTURE_TOAST_ERROR);
            }
            entries.add(entry);
            syncXCarryDestinationCount(entries.size());
            syncXCarryAmountCount(entries.size());
            return new CaptureListAddResult(true, "Added " + formatted, CAPTURE_TOAST_SUCCESS);
        }

        if (field != null && (usesStoreTargetFormatting(field.key()) || isWaitSlotChangeEntryKey(field.key()))) {
            entry = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
            if (entry == null || entry.isBlank()) {
                return new CaptureListAddResult(false, "Could not read that slot target", CAPTURE_TOAST_ERROR);
            }
        }

        String formatted = formatStringListEntry(key, entry);
        if (entries.contains(entry)) {
            return new CaptureListAddResult(false, "Already added " + formatted, CAPTURE_TOAST_ERROR);
        }
        entries.add(entry);
        return new CaptureListAddResult(true, "Added " + formatted, CAPTURE_TOAST_SUCCESS);
    }

    private String formatStringListEntry(String key, String entry) {
        if (isXCarryListKey(key)) {
            String formatted = autismclient.util.macro.XCarryAction.formatEntry(entry);
            if (!formatted.isEmpty()) return formatted;
        }
        if (usesStoreTargetFormatting(key) || isWaitSlotChangeEntryKey(key)) {
            return autismclient.util.macro.StoreItemAction.formatTargetEntry(entry);
        }
        if (isOpenContainerEntityListKey(key)) {
            return formatEntityEntry(entry);
        }
        return entry == null ? "" : entry;
    }

    private int currentXCarryEntryLimit() {
        return autismclient.util.macro.XCarryAction.MAX_ENTRIES;
    }

    private String xCarryLimitMessage(int limit) {
        return "XCarry limit reached: " + limit + " entries max (5 craft + 4 armor + offhand + cursor)";
    }

    private void syncXCarryDestinationCount(int count) {
        if (workingTag == null) return;
        ListTag list = workingTag.getList("entryDestinations").orElse(new ListTag());
        while (list.size() < count) list.add(StringTag.valueOf(String.valueOf(autismclient.util.macro.XCarryAction.DEST_AUTO)));
        while (list.size() > count) list.remove(list.size() - 1);
        workingTag.put("entryDestinations", list);
    }

    private void syncXCarryAmountCount(int count) {
        if (workingTag == null) return;
        ListTag modes = workingTag.getList("entryAmountModes").orElse(new ListTag());
        while (modes.size() < count) modes.add(StringTag.valueOf(autismclient.util.macro.XCarryAction.AmountMode.FULL_STK.name()));
        while (modes.size() > count) modes.remove(modes.size() - 1);
        workingTag.put("entryAmountModes", modes);

        ListTag amounts = workingTag.getList("entryAmounts").orElse(new ListTag());
        while (amounts.size() < count) amounts.add(StringTag.valueOf("1"));
        while (amounts.size() > count) amounts.remove(amounts.size() - 1);
        workingTag.put("entryAmounts", amounts);
    }

    private int getXCarryDestination(int index) {
        if (workingTag == null || index < 0) return autismclient.util.macro.XCarryAction.DEST_AUTO;
        ListTag list = workingTag.getList("entryDestinations").orElse(new ListTag());
        if (index >= list.size()) return autismclient.util.macro.XCarryAction.DEST_AUTO;
        try {
            return Integer.parseInt(list.get(index).asString().orElse(String.valueOf(autismclient.util.macro.XCarryAction.DEST_AUTO)));
        } catch (Exception ignored) {
            return autismclient.util.macro.XCarryAction.DEST_AUTO;
        }
    }

    private void setXCarryDestination(int index, int destination) {
        if (workingTag == null || index < 0) return;
        syncXCarryDestinationCount(Math.max(index + 1, stringLists.getOrDefault("entries", Collections.emptyList()).size()));
        ListTag list = workingTag.getList("entryDestinations").orElse(new ListTag());
        if (index >= list.size()) return;
        list.set(index, StringTag.valueOf(String.valueOf(destination)));
        workingTag.put("entryDestinations", list);
    }

    private void removeXCarryDestination(int index) {
        if (workingTag == null || index < 0) return;
        ListTag list = workingTag.getList("entryDestinations").orElse(new ListTag());
        if (index >= 0 && index < list.size()) list.remove(index);
        workingTag.put("entryDestinations", list);
    }

    private autismclient.util.macro.XCarryAction.AmountMode getXCarryAmountMode(int index) {
        if (workingTag == null || index < 0) return autismclient.util.macro.XCarryAction.AmountMode.FULL_STK;
        ListTag list = workingTag.getList("entryAmountModes").orElse(new ListTag());
        if (index >= list.size()) return autismclient.util.macro.XCarryAction.AmountMode.FULL_STK;
        try {
            return autismclient.util.macro.XCarryAction.AmountMode.valueOf(
                    list.get(index).asString().orElse(autismclient.util.macro.XCarryAction.AmountMode.FULL_STK.name()));
        } catch (IllegalArgumentException ignored) {
            return autismclient.util.macro.XCarryAction.AmountMode.FULL_STK;
        }
    }

    private void setXCarryAmountMode(int index, autismclient.util.macro.XCarryAction.AmountMode mode) {
        if (workingTag == null || index < 0) return;
        syncXCarryAmountCount(Math.max(index + 1, stringLists.getOrDefault("entries", Collections.emptyList()).size()));
        ListTag list = workingTag.getList("entryAmountModes").orElse(new ListTag());
        list.set(index, StringTag.valueOf((mode == null ? autismclient.util.macro.XCarryAction.AmountMode.FULL_STK : mode).name()));
        workingTag.put("entryAmountModes", list);
    }

    private int getXCarryAmount(int index) {
        if (workingTag == null || index < 0) return 1;
        ListTag list = workingTag.getList("entryAmounts").orElse(new ListTag());
        if (index >= list.size()) return 1;
        try {
            return Math.max(1, Integer.parseInt(list.get(index).asString().orElse("1")));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void setXCarryAmount(int index, int amount) {
        if (workingTag == null || index < 0) return;
        syncXCarryAmountCount(Math.max(index + 1, stringLists.getOrDefault("entries", Collections.emptyList()).size()));
        ListTag list = workingTag.getList("entryAmounts").orElse(new ListTag());
        list.set(index, StringTag.valueOf(String.valueOf(Math.max(1, amount))));
        workingTag.put("entryAmounts", list);
    }

    private void removeXCarryAmount(int index) {
        if (workingTag == null || index < 0) return;
        ListTag modes = workingTag.getList("entryAmountModes").orElse(new ListTag());
        if (index >= 0 && index < modes.size()) modes.remove(index);
        workingTag.put("entryAmountModes", modes);
        ListTag amounts = workingTag.getList("entryAmounts").orElse(new ListTag());
        if (index >= 0 && index < amounts.size()) amounts.remove(index);
        workingTag.put("entryAmounts", amounts);
    }

    private Component formatStringListEntryText(String key, String entry, int index) {
        if (usesMinecraftTextRendering(key)) {
            ItemTarget target = resolveStructuredListTarget(key, index, entry);
            return formatItemTargetText(target, formatStringListEntry(key, entry));
        }
        return Component.literal(formatStringListEntry(key, entry));
    }

    private int getPacketRowColor(String entry, int index, boolean selected) {
        if (selected) return autismclient.util.AutismColors.packetRowSelectedText();
        Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = autismclient.util.AutismPacketRegistry.getPacket(entry);
        if (packetClass == null) return theme.color(autismclient.gui.vanillaui.components.UiTone.BODY);
        boolean c2s = autismclient.util.AutismPacketRegistry.getC2SPackets().contains(packetClass);
        return autismclient.util.AutismColors.packetRowText(c2s, index);
    }

    private ItemTarget resolveStructuredListTarget(String key, int index, String entry) {
        List<ItemTarget> targets = buildStructuredListTargets(key);
        if (index >= 0 && index < targets.size()) {
            ItemTarget target = targets.get(index);
            if (target != null && (target.hasSlot() || target.hasIdentity())) return target.copy();
        }
        ItemTarget parsed = ItemTarget.fromLegacyEntry(entry);
        return (parsed.hasSlot() || parsed.hasIdentity()) ? parsed : null;
    }

    private boolean usesMinecraftTextRendering(String key) {
        return usesStoreTargetFormatting(key) || isXCarryListKey(key);
    }

    private Component formatItemTargetText(ItemTarget target, String fallback) {
        String safeFallback = fallback == null ? "" : fallback;
        if (target == null) return Component.literal(safeFallback);

        boolean hasSlot = target.hasSlot();
        boolean hasIdentity = target.hasIdentity();
        if (hasSlot && hasIdentity) {
            return Component.literal(target.slot + ": ").append(target.listComponent().copy());
        }
        if (hasSlot) {
            return Component.literal("#" + target.slot);
        }
        if (hasIdentity) {
            Component display = target.listComponent();
            if (display != null && !display.getString().isBlank()) return display.copy();
        }
        return Component.literal(safeFallback);
    }

    private static String buildEntryFromNameAndSlot(String name, String slotText) {
        String n = name == null ? "" : name.strip();
        int slot = -1;
        if (slotText != null && !slotText.isBlank()) {
            try { slot = Integer.parseInt(slotText.replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) {  }
        }
        if (slot >= 0 && !n.isEmpty()) return "#" + slot + "|" + n;
        if (slot >= 0) return "#" + slot;
        return n;
    }

    private String parseHandlerEntryName(String entry) {
        if (entry == null || entry.isBlank()) return "";
        if (!entry.startsWith("#")) return entry;
        int separator = entry.indexOf('|');
        return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
    }

    private int parseHandlerSlotField(String text, int fallback) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) return fallback;
        String cleaned = raw.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return fallback;
        try {
            int slot = Integer.parseInt(cleaned);
            return slot < 0 ? fallback : slot;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseSlotEntryValue(String entry) {
        if (entry == null || !entry.startsWith("#")) return -1;
        try {
            int separator = entry.indexOf('|');
            String raw = separator >= 0 ? entry.substring(1, separator) : entry.substring(1);
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean containsEntryOtherThan(List<String> entries, String entry, int ignoreIndex) {
        for (int i = 0; i < entries.size(); i++) {
            if (i != ignoreIndex && entries.get(i).equals(entry)) return true;
        }
        return false;
    }

    private List<AutismSharedState.QueuedPacket> getWorkingQueuedPackets() {
        if (workingTag == null) return new ArrayList<>();
        SendPacketAction action = new SendPacketAction();
        action.fromTag(workingTag);
        return new ArrayList<>(action.packets);
    }

    private void setWorkingQueuedPackets(List<AutismSharedState.QueuedPacket> packets) {
        if (workingTag == null) return;
        net.minecraft.nbt.ListTag packetList = new net.minecraft.nbt.ListTag();
        if (packets != null) {
            for (AutismSharedState.QueuedPacket qp : packets) {
                CompoundTag packetTag = AutismClipboardHelper.serializeQueuedPacket(qp);
                if (packetTag != null) packetList.add(packetTag);
            }
        }
        workingTag.put("packets", packetList);
    }

    private String buildSendPacketInfo() {
        List<AutismSharedState.QueuedPacket> packets = getWorkingQueuedPackets();
        if (packets.isEmpty()) return "Packets: 0";

        String autoName = "";
        for (AutismSharedState.QueuedPacket qp : packets) {
            if (qp != null && qp.packet != null) {
                autoName = AutismPacketNamer.getFriendlyName(qp.packet);
                break;
            }
        }
        if (autoName.isEmpty()) return "Packets: " + packets.size();
        return packets.size() == 1 ? "Packets: 1 - " + autoName : "Packets: " + packets.size() + " - " + autoName;
    }

    private void setRawPacketActionData(AutismSharedState.QueuedPacket packet) {
        if (workingTag == null || packet == null) return;
        List<AutismSharedState.QueuedPacket> single = java.util.Collections.singletonList(packet);
        String base64 = AutismClipboardHelper.serializeQueueToBase64(single);
        if (base64 == null || base64.isBlank()) return;
        workingTag.putString("packetData", base64);
        if (!workingTag.contains("description") || workingTag.getStringOr("description", "").isBlank()) {
            String name = packet.packet != null ? AutismPacketNamer.getFriendlyName(packet.packet) : "Packet";
            workingTag.putString("description", name);
        }
    }

    private String buildPacketActionInfo() {
        if (workingTag == null) return "Raw Packet: empty";
        String packetData = workingTag.getStringOr("packetData", "");
        if (packetData == null || packetData.isBlank()) return "Raw Packet: empty";
        String description = workingTag.getStringOr("description", "");
        return description == null || description.isBlank() ? "Raw Packet: loaded" : "Raw Packet: " + description;
    }

    private void startGBreakCaptureForEditor() {
        if (!guardWorldCaptureAction()) return;
        AutismSharedState state = AutismSharedState.get();
        screenBeforeGBreak = MC.gui.screen();
        enterCaptureMode();
        state.setCaptureCancelCallback(() -> {
            state.cancelGBreakCapture();
            if (screenBeforeGBreak != null) {
                MC.gui.setScreen(screenBeforeGBreak);
                screenBeforeGBreak = null;
            }
            exitCaptureMode(false, false);
        });
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });
        AutismClientMessaging.sendPrefixed("GBreak: Break a block to capture the insta-break packet");
        state.startGBreakCapture(() -> MC.execute(() -> {
            List<AutismSharedState.QueuedPacket> captured = AutismSharedState.get().getGBreakCapturedPackets();
            if (!captured.isEmpty()) {
                setWorkingQueuedPackets(java.util.Collections.singletonList(captured.get(0)));
                workingTag.putString("customName", "GBreak");
                AutismChatField customNameField = textFields.get("customName");
                if (customNameField != null) customNameField.setText("GBreak");
                AutismClientMessaging.sendPrefixed("GBreak packet captured");
            } else {
                AutismClientMessaging.sendPrefixed("GBreak capture finished with no packet");
            }

            if (screenBeforeGBreak != null) {
                MC.gui.setScreen(screenBeforeGBreak);
                screenBeforeGBreak = null;
            }
            exitCaptureMode(false, false);
            AutismOverlayManager.get().bringToFront(this);
        }));
    }

    private void startLookAtEntityCapture() {
        if (!guardWorldCaptureAction()) return;
        if (!(targetAction instanceof autismclient.util.macro.LookAtBlockAction)) return;
        Screen previousScreen = MC.gui.screen();
        enterCaptureMode();
        AutismSharedState state = AutismSharedState.get();
        state.setCaptureCancelCallback(() -> {
            if (previousScreen != null) MC.gui.setScreen(previousScreen);
            state.setEntityCaptureCallback(null);
            exitCaptureMode(false, false);
        });
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });
        state.setEntityCaptureSpecific(entitySpecificCaptureMode);
        state.setEntityCaptureCallback(payload -> MC.execute(() -> {
            List<String> selected = stringLists.get("entityIds");
            if (selected != null && payload != null && !payload.isBlank()) {
                if (entitySpecificCaptureMode) {
                    selected.removeIf(existing -> existing.startsWith(entitySpecificEntryPrefix(payload)));
                }
                if (!selected.contains(payload)) selected.add(payload);
            }
            if (previousScreen != null) MC.gui.setScreen(previousScreen);
            exitCaptureMode(false, false);
            AutismOverlayManager.get().bringToFront(this);
        }));
    }

    private void renderWaitSoundPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitGuiName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "checkDistance", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "maxDistance", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);

        List<String> selected = stringLists.getOrDefault("soundIds", Collections.emptyList());
        cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "wait_sound_selected", "Sounds", selected, value -> {
            selected.remove(value);
            DirectScrollViewport vp = selectedScrollViewports.get("wait_sound_selected");
            if (vp != null) vp.scrollBy(-1);
        }, AutismRegistryLabels::sound, "(any sound - add to filter)");

        AutismChatField search = addFields.get("soundIds");
        if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(w);
            search.render(ctx, mx, my, delta);
        }
        cy += 18;

        String filter = search != null ? search.getText().trim().toLowerCase() : "";
        List<String> filtered = filterRegistryValues("wait_sound_search", getAllSoundIds(), filter, AutismRegistryLabels::sound);
        renderSearchRegistryList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "wait_sound_search",
            filtered,
            6,
            value -> {
                if (selected.contains(value)) selected.remove(value);
                else selected.add(value);
            },
            selected::contains,
            AutismRegistryLabels::sound
        );
    }

    private void renderWaitEntityPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "checkMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "radius", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "centerOnPlayer", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "pos", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "mustBeLookingAt", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);

        List<String> selected = stringLists.getOrDefault("entityIds", Collections.emptyList());
        cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "wait_entity_selected", "Entities", selected, selected::remove,
                this::formatEntityEntry, "(any entity - add type or specific)");

        AutismChatField search = addFields.get("entityIds");
        int searchW = w - 66;
        if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(searchW);
            search.render(ctx, mx, my, delta);
        }
        int modeX = x + searchW + 2;
        String modeLabel = entitySpecificCaptureMode ? "[Spec]" : "[Type]";
        renderActionButton(ctx, modeX, cy, 30, 14, modeLabel, mx, my, () -> entitySpecificCaptureMode = !entitySpecificCaptureMode);
        renderActionButton(ctx, modeX + 34, cy, 32, 14, "CAP", mx, my, this::startWaitEntityCapture);
        cy += 18;

        String filter = search != null ? search.getText().trim().toLowerCase() : "";
        List<String> filtered = filterRegistryValues("wait_entity_search", getAllEntityIds(), filter, AutismRegistryLabels::entity);
        renderSearchRegistryList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "wait_entity_search",
            filtered,
            4,
            value -> {
                if (selected.contains(value)) selected.remove(value);
                else selected.add(value);
            },
            selected::contains,
            AutismRegistryLabels::entity
        );
        cy += 4 + 4 * CATALOG_ITEM_H + 10;

        UiText.draw(ctx, textRenderer, "Nearby Entities", font, AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;
        List<String> nearbyEntries = getNearbyEntityEntries();
        renderSearchRegistryList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "wait_entity_nearby",
            nearbyEntries,
            3,
            value -> {
                String stored = entitySpecificCaptureMode ? value : extractEntityTypeFromNearbyEntry(value);
                if (stored.isBlank()) return;
                if (entitySpecificCaptureMode) {
                    boolean removed = selected.removeIf(existing -> existing.startsWith(entitySpecificEntryPrefix(stored)));
                    if (!removed) selected.add(stored);
                } else if (selected.contains(stored)) {
                    selected.remove(stored);
                } else {
                    selected.add(stored);
                }
            },
            value -> {
                String stored = entitySpecificCaptureMode ? value : extractEntityTypeFromNearbyEntry(value);
                return entitySpecificCaptureMode
                    ? selected.stream().anyMatch(existing -> existing.startsWith(entitySpecificEntryPrefix(stored)))
                    : selected.contains(stored);
            },
            this::formatNearbyEntityEntry
        );
    }

    private void renderOpenContainerPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "targetMode", x, cy, w, mx, my, delta);
        String targetMode = currentEnumValue("targetMode");
        if ("ENTITY".equals(targetMode)) {
            cy = renderFieldByKey(ctx, "entityTargets", x, cy, w, mx, my, delta);
        } else if ("BLOCK".equals(targetMode)) {
            cy = renderFieldByKey(ctx, "pos", x, cy, w, mx, my, delta);
        }
        cy = renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);
        if ("ENTITY".equals(targetMode)) {
            String hint = "Pick captures exact entities. Last Target reuses the most recent interaction target.";
            cy = renderEditorHint(ctx, x, cy, w, hint);
            List<String> selected = stringLists.getOrDefault("entityTargets", Collections.emptyList());
            cy += 13;
            List<String> nearbyEntries = getNearbyEntityEntries();
            renderSearchRegistryList(
                ctx,
                x,
                cy,
                w,
                mx,
                my,
                "open_container_entity_nearby",
                nearbyEntries,
                3,
                value -> {
                    if (selected.contains(value)) selected.remove(value);
                    else selected.add(value);
                },
                selected::contains,
                this::formatNearbyEntityEntry
            );
        } else if ("LAST_TARGET".equals(targetMode)) {
            renderEditorHint(ctx, x, cy, w, "Uses the last block or entity container you actually opened.");
        } else {
            cy = renderEditorHint(ctx, x, cy, w, "Capture only marks the block. It will not open it.");
            renderNearbyContainerList(ctx, x, cy, w, mx, my, "open_container_nearby", "pos");
        }
    }

    private void renderNearbyContainerList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                           String listKey, String fieldKey) {
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, "Nearby Containers", font, AutismColors.textSecondary(), x, y + 2, false);
        y += 14;
        List<BlockPos> nearbyContainers = getNearbyContainerPositions();
        renderSearchRegistryList(
            ctx,
            x,
            y,
            w,
            mx,
            my,
            listKey,
            nearbyContainers,
            CONTAINER_LIST_VISIBLE_ROWS,
            pos -> fillBlockPosField(fieldKey, pos),
            pos -> isCurrentBlockPosField(fieldKey, pos),
            this::formatContainerEntry
        );
    }

    private void renderStoreItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "allItems", x, cy, w, mx, my, delta);

        List<String> selected = stringLists.getOrDefault("targetItems", Collections.emptyList());
        if (!toggleStates.getOrDefault("allItems", false)) {
            cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "store_items_selected", "Target Items", selected,
                    selected::remove, autismclient.util.macro.StoreItemAction::formatTargetEntry,
                    value -> formatStringListEntryText("targetItems", value, selected.indexOf(value)),
                    selected::clear,
                    "(nothing selected - search, type #slot, or capture)", true);

            int storeEditIdx = stringListEditIndex.getOrDefault("store_items_selected", -1);
            boolean storeEditing = storeEditIdx >= 0 && storeEditIdx < selected.size();
            AutismChatField search = addFields.get("targetItems");
            int pickW   = 32;
            int addBtnW = 34;
            int slotW   = 44;
            int capBtnX = x + w - pickW;
            int addBtnX = capBtnX - 3 - addBtnW;
            int slotX   = addBtnX - 2 - slotW;
            int nameW   = slotX - x - 2;

            AutismChatField storeSlotF = textFields.get("store_slot");
            if (storeSlotF == null) {
                storeSlotF = makeField(slotW);
                storeSlotF.setNumericOnly(true);
                storeSlotF.setPlaceholder(Component.literal("Slot#"));
                textFields.put("store_slot", storeSlotF);
            }

            String pendingText = stringListEditPendingText.remove("store_items_selected");
            if (pendingText != null) {
                if (search != null) search.setText(parseHandlerEntryName(pendingText));
                ItemTarget parsed = ItemTarget.fromLegacyEntry(pendingText);
                storeSlotF.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
            }

            if (search != null) {
                search.setX(x); search.setY(cy + 1); search.setWidth(nameW);
                search.render(ctx, mx, my, delta);
            }
            storeSlotF.setX(slotX); storeSlotF.setY(cy + 1); storeSlotF.setWidth(slotW);
            storeSlotF.render(ctx, mx, my, delta);

            if (storeEditing && search != null) {
                String nameText = search.getText().strip();
                String slotText = storeSlotF.getText().strip();
                String entry = buildEntryFromNameAndSlot(nameText, slotText);
                if (!entry.isEmpty()) {
                    String normalized = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
                    if (normalized != null && !normalized.isBlank() && !normalized.equals(selected.get(storeEditIdx))) {
                        selected.set(storeEditIdx, normalized);
                        editorItemLists.put("targetItems", buildStructuredListTargets("targetItems"));
                    }
                }
            }

            final AutismChatField fStoreSlotF = storeSlotF;
            renderOverlayButton(ctx, addBtnX, cy, addBtnW, 14, "+Add", CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                if (search == null) return;
                String nameText = search.getText().strip();
                String slotText = fStoreSlotF.getText().strip();
                String entry = buildEntryFromNameAndSlot(nameText, slotText);
                if (!entry.isEmpty()) {
                    String normalized = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
                    if (normalized != null && !normalized.isBlank() && !selected.contains(normalized)) {
                        selected.add(normalized);
                        editorItemLists.put("targetItems", buildStructuredListTargets("targetItems"));
                    }
                    search.setText("");
                    fStoreSlotF.setText("");
                    stringListEditIndex.put("store_items_selected", -1);
                }
            });
            boolean capturing = captureSession.isItemSlotCapture("targetItems");
            renderFieldCaptureButton(ctx, capBtnX, cy, pickW, 14, CaptureMode.ITEM_SLOT, capturing,
                    true, mx, my, () -> toggleItemSlotCapture("targetItems"));
            cy += 18;

            String filter = search != null ? search.getText().trim().toLowerCase() : "";
            List<String> filtered = filterRegistryValues("store_items_search", getAllItemIds(), filter, AutismRegistryLabels::item);
            renderSearchRegistryList(
                ctx,
                x,
                cy,
                w,
                mx,
                my,
                "store_items_search",
                filtered,
                6,
                value -> {
                    if (selected.contains(value)) selected.remove(value);
                    else selected.add(value);
                    stringListEditIndex.put("store_items_selected", -1);
                    editorItemLists.put("targetItems", buildStructuredListTargets("targetItems"));
                },
                selected::contains,
                AutismRegistryLabels::item
            );
            cy += 6 * CATALOG_ITEM_H + 4;

            cy = renderEditorHint(ctx, x, cy, w, "Pick on an item adds it by name, from any slot on the screen. Ctrl+pick or an empty slot adds that exact slot.");

            String modeHint = "LOOT".equals(currentEnumValue("mode"))
                    ? "Exact slots must be chest/custom GUI slots."
                    : "Exact slots must be player inventory slots.";
            cy = renderEditorHint(ctx, x, cy, w, modeHint);
        }

        cy = renderFieldByKey(ctx, "persistent", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "delayTicks", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "closeAfter", x, cy, w, mx, my, delta);
        renderFieldByKey(ctx, "closeSendPkt", x, cy, w, mx, my, delta);
    }

    private void renderInventoryAuditPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);

        List<String> selected = stringLists.getOrDefault("targetItems", Collections.emptyList());
        FieldDef targetItemsField = getField("targetItems");
        if (targetItemsField != null && isFieldVisible(targetItemsField)) {
            String emptyLabel = getStringListEmptyHint("targetItems");
            if (emptyLabel == null || emptyLabel.isBlank()) emptyLabel = "(nothing selected)";
            boolean multipleStacks = toggleStates.getOrDefault("multipleStacks", false);
            int listTop = cy;

            cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "audit_items_selected", "Targets", selected,
                    selected::remove, autismclient.util.macro.StoreItemAction::formatTargetEntry,
                    value -> formatStringListEntryText("targetItems", value, selected.indexOf(value)),
                    selected::clear,
                    emptyLabel,
                    true);

            int multiBtnW = 96;
            int clearBtnW = 44;
            int multiBtnX = x + w - clearBtnW - 3 - multiBtnW;
            renderOverlayToggleButton(ctx, multiBtnX, listTop, multiBtnW, 14, "Multiple Stacks",
                    multipleStacks, "macro-audit:multiple-stacks",
                    mx, my, () -> toggleStates.put("multipleStacks", !toggleStates.getOrDefault("multipleStacks", false)));

            int auditEditIdx = stringListEditIndex.getOrDefault("audit_items_selected", -1);
            boolean auditEditing = auditEditIdx >= 0 && auditEditIdx < selected.size();
            AutismChatField search = addFields.get("targetItems");
            int pickW = 32;
            int addBtnW = 34;
            int slotW = 26;
            int pickX = x + w - addBtnW - 3 - pickW;
            int addBtnX = x + w - addBtnW;
            int slotX = pickX - 3 - slotW;
            int nameW = slotX - x - 2;

            AutismChatField auditSlotF = textFields.get("audit_slot");
            if (auditSlotF == null) {
                auditSlotF = makeField(slotW);
                auditSlotF.setNumericOnly(true);
                auditSlotF.setPlaceholder(Component.literal("Slot"));
                textFields.put("audit_slot", auditSlotF);
            }

            String pendingText = stringListEditPendingText.remove("audit_items_selected");
            if (pendingText != null) {
                if (search != null) search.setText(parseHandlerEntryName(pendingText));
                ItemTarget parsed = ItemTarget.fromLegacyEntry(pendingText);
                auditSlotF.setText(parsed.hasSlot() ? String.valueOf(parsed.slot) : "");
            }

            if (search != null) {
                search.setX(x); search.setY(cy + 1); search.setWidth(nameW);
                search.render(ctx, mx, my, delta);
            }
            auditSlotF.setX(slotX); auditSlotF.setY(cy + 1); auditSlotF.setWidth(slotW);
            auditSlotF.render(ctx, mx, my, delta);

            if (auditEditing && search != null) {
                String nameText = search.getText().strip();
                String slotText = auditSlotF.getText().strip();
                String entry = buildEntryFromNameAndSlot(nameText, slotText);
                if (!entry.isEmpty()) {
                    String normalized = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
                    if (normalized != null && !normalized.isBlank() && !normalized.equals(selected.get(auditEditIdx))) {
                        selected.set(auditEditIdx, normalized);
                        editorItemLists.put("targetItems", buildStructuredListTargets("targetItems"));
                    }
                }
            }

            final AutismChatField fAuditSlotF = auditSlotF;
            boolean capturing = captureSession.isItemSlotCapture("targetItems");
            renderFieldCaptureButton(ctx, pickX, cy, pickW, 14, CaptureMode.ITEM_SLOT, capturing,
                    true, mx, my, () -> toggleItemSlotCapture("targetItems"));

            String addLabel = auditEditing ? "New" : "+Add";
            renderOverlayButton(ctx, addBtnX, cy, addBtnW, 14, addLabel, CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                if (search == null) return;
                if (stringListEditIndex.getOrDefault("audit_items_selected", -1) >= 0) {
                    stringListEditIndex.put("audit_items_selected", -1);
                    stringListEditPendingText.put("audit_items_selected", "");
                    search.setText("");
                    fAuditSlotF.setText("");
                    return;
                }

                String nameText = search.getText().strip();
                String slotText = fAuditSlotF.getText().strip();
                String entry = buildEntryFromNameAndSlot(nameText, slotText);
                if (!entry.isEmpty()) {
                    String normalized = autismclient.util.macro.StoreItemAction.normalizeTargetEntry(entry);
                    if (normalized != null && !normalized.isBlank() && !selected.contains(normalized)) {
                        selected.add(normalized);
                        editorItemLists.put("targetItems", buildStructuredListTargets("targetItems"));
                    }
                    search.setText("");
                    fAuditSlotF.setText("");
                    stringListEditIndex.put("audit_items_selected", -1);
                    stringListEditPendingText.put("audit_items_selected", "");
                }
            });
            cy += 18;
        }

        cy = renderFieldByKey(ctx, "openMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "openCommand", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "containerPos", x, cy, w, mx, my, delta);
        if ("CONTAINER".equals(currentEnumValue("openMode"))) {
            cy = renderEditorHint(ctx, x, cy, w, "Capture stores the clicked block position. Nearby also includes likely server-handled GUI blocks.");
            renderNearbyContainerList(ctx, x, cy, w, mx, my, "inventory_audit_nearby", "containerPos");
            cy += CONTAINER_LIST_VISIBLE_ROWS * CATALOG_ITEM_H + 17;
        }
        cy = renderFieldByKey(ctx, "dupeVector", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "iterations", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "maxTransferAttempts", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "transferRetryDelayMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "spamCount", x, cy, w, mx, my, delta);
        renderFieldByKey(ctx, "spamDelayMs", x, cy, w, mx, my, delta);
    }

    private void renderPayPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "commandTemplate", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "amountInput", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "divideEnabled", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "probeHidden", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "delayEnabled", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "delayMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "confirmMacro", x, cy, w, mx, my, delta);

        List<String> selected = stringLists.getOrDefault("players", Collections.emptyList());
        cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "pay_players_selected", "Players", selected,
                value -> removeStringIgnoreCase(selected, value), value -> value, ignored -> null, selected::clear,
                "(scan the server or add one manually)");

        AutismChatField search = addFields.get("players");
        int scanW = 34;
        int allW = 28;
        int searchW = Math.max(1, w - scanW - allW - 4);
        if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(searchW);
            search.render(ctx, mx, my, delta);
        }
        int scanX = x + searchW + 2;
        int allX = scanX + scanW + 2;
        renderActionButton(ctx, scanX, cy, scanW, 14, "Scan", mx, my, this::refreshPayScannedPlayers);
        renderActionButton(ctx, allX, cy, allW, 14, "All", mx, my, () -> addFilteredPayPlayers(
                selected, search != null ? search.getText() : ""
        ));
        cy += 18;

        String query = search != null ? search.getText() : "";
        if (!payPlayerScanPerformed) {
            renderRegistryPlaceholder(ctx, x, cy, w, "pay_players_scan", 1, "Press Scan to load the current server players.");
            cy += CATALOG_ITEM_H + 4;
        } else {
            cy = renderPayGroupedList(ctx, x, cy, w, mx, my, selected, query) + 4;
        }

        AutismChatField amountField = textFields.get("amountInput");
        long amount = autismclient.util.macro.PayAction.parseAmount(amountField != null ? amountField.getText() : "");
        boolean divide = toggleStates.getOrDefault("divideEnabled", false);
        int count = selected.size();
        String summary;
        if (count == 0) {
            summary = "Empty list = pays EVERYONE (tab + probed hidden).";
        } else if (divide) {
            long[] dist = autismclient.util.macro.PayAction.distribute(amount, count);
            String per = dist[0] == dist[count - 1]
                    ? autismclient.util.macro.PayAction.formatAmount(dist[0])
                    : autismclient.util.macro.PayAction.formatAmount(dist[count - 1]) + "-" + autismclient.util.macro.PayAction.formatAmount(dist[0]);
            summary = "Splits " + autismclient.util.macro.PayAction.formatAmount(amount) + " among " + count + " (" + per + " each).";
        } else {
            summary = "Pays " + count + " x " + autismclient.util.macro.PayAction.formatAmount(amount)
                    + " (" + autismclient.util.macro.PayAction.formatAmount(amount * (long) count) + " total).";
        }
        cy = renderEditorHint(ctx, x, cy, w, summary);

        String hint = payPlayerScanPerformed
                ? "Click a name to add it. Click a rank header to add/remove everyone in that rank; All adds all."
                : "Scan to list players by rank. Type a name then Enter to add it manually.";
        renderEditorHint(ctx, x, cy, w, hint);
    }

    private record PayRow(boolean header, String prefix, String name, List<String> members) {
        static PayRow header(String prefix, List<String> members) { return new PayRow(true, prefix, null, members); }
        static PayRow player(String prefix, String name) { return new PayRow(false, prefix, name, null); }
    }

    private List<PayRow> buildPayRows(String query) {
        Map<String, List<String>> byPrefix = new LinkedHashMap<>();
        for (String name : filterEntries(payScannedPlayers, query)) {
            byPrefix.computeIfAbsent(payPrefixFor(name), ignored -> new ArrayList<>()).add(name);
        }
        List<String> order = new ArrayList<>(byPrefix.keySet());
        order.sort((a, b) -> {
            boolean ab = a.isBlank();
            boolean bb = b.isBlank();
            if (ab != bb) return ab ? 1 : -1;
            return a.compareToIgnoreCase(b);
        });
        List<PayRow> rows = new ArrayList<>();
        for (String prefix : order) {
            List<String> names = byPrefix.get(prefix);
            names.sort(String.CASE_INSENSITIVE_ORDER);
            rows.add(PayRow.header(prefix, names));
            for (String name : names) rows.add(PayRow.player(prefix, name));
        }
        return rows;
    }

    private int renderPayGroupedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, List<String> selected, String query) {
        int rowH = CATALOG_ITEM_H;
        List<PayRow> entries = buildPayRows(query);
        if (entries.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, w, 12, false, false);
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No players found", x, y, w);
            return y + rowH;
        }

        int clipTop = bodyInputClip != null ? bodyInputClip.y() : Integer.MIN_VALUE;
        int clipBottom = bodyInputClip != null ? bodyInputClip.bottom() : Integer.MAX_VALUE;
        Identifier font = theme.fontFor(UiTone.BODY);
        int iy = y;
        for (PayRow row : entries) {
            if (iy + rowH > clipTop && iy < clipBottom) {
                if (row.header()) {
                    List<String> members = row.members();
                    int selCount = (int) members.stream().filter(m -> containsIgnoreCase(selected, m)).count();
                    boolean allSel = selCount == members.size() && selCount > 0;
                    UiText.fill(ctx, x, iy, x + w, iy + rowH - 1, 0x44000000);
                    UiText.fill(ctx, x, iy, x + 2, iy + rowH - 1, allSel ? 0xFF4E8E5A : 0xFF8E2E39);
                    int headerColor = allSel ? 0xFF9CE0A8 : 0xFFE7B0B8;
                    int baseY = iy + Math.max(1, (rowH - 8) / 2);
                    String marker = allSel ? "✓ " : "+ ";
                    String counter = "  (" + selCount + "/" + members.size() + ")";
                    int markerW = textRenderer.width(marker);
                    int counterW = textRenderer.width(counter);
                    int rankMaxW = Math.max(8, w - 12 - markerW - counterW);
                    int cx = x + 6;
                    UiText.draw(ctx, textRenderer, marker, font, headerColor, cx, baseY, false);
                    cx += markerW;

                    Component styled = row.prefix().isBlank() ? null : payPrefixComponents.get(row.prefix());
                    if (styled != null && textRenderer.width(styled) <= rankMaxW) {
                        ctx.text(textRenderer, styled, cx, baseY, 0xFFFFFFFF, false);
                        cx += textRenderer.width(styled);
                    } else {
                        String fallback = row.prefix().isBlank() ? "No rank" : row.prefix();
                        int fallbackColor = row.prefix().isBlank() ? headerColor : 0xFFFFFFFF;
                        fallback = UiText.trimToWidth(textRenderer, fallback, rankMaxW, font, fallbackColor);
                        UiText.draw(ctx, textRenderer, fallback, font, fallbackColor, cx, baseY, false);
                        cx += textRenderer.width(fallback);
                    }
                    UiText.draw(ctx, textRenderer, counter, font, headerColor, cx, baseY, false);
                    final List<String> targetMembers = members;
                    hitRegions.add(new HitRegion(x, iy, w, rowH, () -> togglePayRankSelection(selected, targetMembers)));
                } else {
                    boolean sel = containsIgnoreCase(selected, row.name());
                    boolean hovered = mx >= x && mx < x + w && my >= iy && my < iy + rowH;
                    String label = UiText.trimToWidth(textRenderer, "  " + row.name(), w - 6, font, AutismColors.textSecondary());
                    MacroTypedListControl.renderRow(ctx, textRenderer, Component.literal(label),
                            UiBounds.of(x, iy, w, rowH), hovered, sel,
                            sel ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL, false);
                    final String name = row.name();
                    hitRegions.add(new HitRegion(x, iy, w, rowH, () -> togglePayPlayerSelection(selected, name)));
                }
            }
            iy += rowH;
        }
        return iy;
    }

    private void renderToggleModulePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        List<autismclient.util.macro.ToggleModuleAction.ModuleEntry> selected =
                toggleModuleEntries != null ? toggleModuleEntries : Collections.emptyList();

        UiText.draw(ctx, textRenderer, "Module Actions (" + selected.size() + ")", font, AutismColors.textSecondary(), x, cy + 2, false);
        boolean canClear = !selected.isEmpty();
        int clearX = x + w - 44;
        renderOverlayButton(ctx, clearX, cy, 44, 14, "Clear",
                CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
                    selected.clear();
                    DirectScrollViewport vp = selectedScrollViewports.get("toggle_module_selected");
                    if (vp != null) vp.jumpTo(0);
                });
        cy += 14;

        int listH = 4 * SEL_ITEM_H;
        int itemW = w - SCROLLBAR_W - 1;

        DirectScrollViewport toggleViewport = getOrCreateViewport(selectedScrollViewports, "toggle_module_selected",
            x, cy, w, listH, SEL_ITEM_H, SCROLLBAR_W);
        toggleViewport.setContentHeight(selected.size() * SEL_ITEM_H);

        toggleViewport.renderScrollbar(ctx, mx, my);

        if (selected.isEmpty()) {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "Pick modules below.", x, cy, itemW);
        } else {
            toggleViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int first = toggleViewport.getFirstVisibleRow();
            for (int i = first; i < selected.size() && i <= toggleViewport.getLastVisibleRow(); i++) {
                int iy = toggleViewport.getRowScreenY(i);
                if (iy == Integer.MIN_VALUE) continue;
                autismclient.util.macro.ToggleModuleAction.ModuleEntry entry = selected.get(i);
                int removeW = 13;
                int modeW = 52;
                int rowW = itemW - removeW - modeW - 4;
                boolean hovered = mx >= x && mx < x + rowW && my >= iy + 1 && my < iy + 14;
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        Component.literal(entry.moduleName),
                        UiBounds.of(x, iy + 1, rowW, 13),
                        hovered,
                        false,
                        CompactListRenderer.RowTone.NORMAL,
                        false
                );

                int modeX = x + rowW + 2;
                final int entryIndex = i;
                autismclient.util.macro.ToggleModuleAction.ToggleMode[] tmModes = autismclient.util.macro.ToggleModuleAction.ToggleMode.values();
                java.util.List<String> tmOptions = new java.util.ArrayList<>();
                for (autismclient.util.macro.ToggleModuleAction.ToggleMode m : tmModes) tmOptions.add(formatToggleModeShort(m));
                renderInlineDropdown("toggle_mode_" + entryIndex, tmOptions, entry.toggleMode.ordinal(), modeX, iy + 1, modeW, 13, idx -> {
                    if (entryIndex >= 0 && entryIndex < selected.size()) {
                        selected.get(entryIndex).toggleMode = tmModes[Math.max(0, Math.min(idx, tmModes.length - 1))];
                    }
                });

                int removeX = modeX + modeW + 2;
                renderIconDeleteButton(ctx, removeX, iy + 1, removeW, mx, my, () -> {
                    if (entryIndex >= 0 && entryIndex < selected.size()) {
                        selected.remove(entryIndex);
                        DirectScrollViewport vp = selectedScrollViewports.get("toggle_module_selected");
                        if (vp != null) vp.scrollBy(-1);
                    }
                });
            }
            toggleViewport.endRender(ctx);
        }
        cy += listH + 4;

        AutismChatField search = addFields.get("_toggle_module_search");
        int refreshW = 52;
        int addW = 40;
        int searchW = Math.max(1, w - refreshW - addW - 4);
        if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(searchW);
            search.render(ctx, mx, my, delta);
        }
        int refreshX = x + searchW + 2;
        int addX = refreshX + refreshW + 2;
        renderActionButton(ctx, refreshX, cy, refreshW, 14, "Refresh", mx, my, this::refreshMeteorModuleNames);
        renderActionButton(ctx, addX, cy, addW, 14, "+Add", mx, my, () -> addFirstFilteredModule(search != null ? search.getText() : ""));
        cy += 18;

        String query = search != null ? search.getText() : "";
        List<String> filteredAutism = filterEntries("toggle_module_autism_registry", autismModuleNames, query);
        List<String> filteredMeteor = filterEntries("toggle_module_meteor_registry", meteorModuleNames, query);
        boolean hasExternalModules = !meteorModuleNames.isEmpty();
        if (autismModuleNames.isEmpty() && meteorModuleNames.isEmpty()) {
            renderRegistryPlaceholder(ctx, x, cy, w, "toggle_module_registry_empty", 6, "No modules found right now.");
            cy += 6 * CATALOG_ITEM_H + 4;
        } else {
            cy = renderToggleModuleCatalogSection(ctx, x, cy, w, mx, my,
                    "AUTISM Modules", "toggle_module_autism_registry",
                    autismModuleNames, filteredAutism, hasExternalModules ? 3 : 6, selected);
            if (hasExternalModules) {
                cy = renderToggleModuleCatalogSection(ctx, x, cy, w, mx, my,
                        "Meteor Modules", "toggle_module_meteor_registry",
                        meteorModuleNames, filteredMeteor, 3, selected);
            }
        }

        String hint = selected.isEmpty()
                ? "Pick modules below, then click the mode chip on each row."
                : "Each row runs with its own Toggle / Enable / Disable setting.";
        renderEditorHint(ctx, x, cy, w, hint);
    }

    private int renderToggleModuleCatalogSection(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                                  String title, String listKey, List<String> source,
                                                  List<String> filtered,
                                                  int visibleRows,
                                                  List<autismclient.util.macro.ToggleModuleAction.ModuleEntry> selected) {
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, title, font, AutismColors.textSecondary(), x, y + 1, false);
        y += 12;
        if (source == null || source.isEmpty()) {
            renderRegistryPlaceholder(ctx, x, y, w, listKey, visibleRows, "No modules found right now.");
        } else {
            renderSearchRegistryList(ctx, x, y, w, mx, my, listKey, filtered, visibleRows,
                    this::addToggleModuleEntry,
                    value -> containsModuleEntry(selected, value),
                    value -> value);
        }
        return y + visibleRows * CATALOG_ITEM_H + 4;
    }

    private void renderRegistryPlaceholder(GuiGraphicsExtractor ctx, int x, int y, int w, String listKey, int visibleRows, String message) {
        int listH = visibleRows * CATALOG_ITEM_H;
        int itemW = w - SCROLLBAR_W - 1;
        CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
        CompactListRenderer.drawEmptyState(ctx, textRenderer, formatEditorHint(message), x, y, itemW);
    }

    private void refreshPayScannedPlayers() {
        payScannedPlayers.clear();
        payPlayerPrefixes.clear();
        payPrefixComponents.clear();
        payPlayerScanPerformed = true;
        DirectScrollViewport vp = catalogScrollViewports.get("pay_players_scan");
        if (vp != null) vp.jumpTo(0);

        for (autismclient.util.AutismPlayerScanner.ScannedPlayer player : autismclient.util.AutismPlayerScanner.scan(MC)) {
            if (player == null || player.name() == null || player.name().isBlank()) continue;
            if (containsIgnoreCase(payScannedPlayers, player.name())) continue;
            payScannedPlayers.add(player.name());
            if (player.hasPrefix()) {
                payPlayerPrefixes.put(player.name().toLowerCase(Locale.ROOT), player.prefix());

                if (player.prefixComponent() != null) {
                    payPrefixComponents.putIfAbsent(player.prefix(), player.prefixComponent());
                }
            }
        }

        autismclient.util.AutismBackgroundTasks.runTracked("pay-scan-probe", () -> {
            java.util.List<String> probed = autismclient.util.AutismPlayerProbe.everyone(MC, true);
            if (probed.isEmpty()) return;
            MC.execute(() -> {
                boolean changed = false;
                for (String name : probed) {
                    if (!containsIgnoreCase(payScannedPlayers, name)) {
                        payScannedPlayers.add(name);
                        payPlayerPrefixes.putIfAbsent(name.toLowerCase(Locale.ROOT), "unknown");
                        changed = true;
                    }
                }
                if (changed) payScannedPlayers.sort(String::compareToIgnoreCase);
            });
        });
        payScannedPlayers.sort(String::compareToIgnoreCase);
    }

    private String payPrefixFor(String name) {
        return name == null ? "" : payPlayerPrefixes.getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    private void togglePayRankSelection(List<String> selected, List<String> members) {
        if (selected == null || members == null || members.isEmpty()) return;
        boolean allSelected = members.stream().allMatch(m -> containsIgnoreCase(selected, m));
        if (allSelected) {
            for (String m : members) removeStringIgnoreCase(selected, m);
        } else {
            for (String m : members) {
                if (!containsIgnoreCase(selected, m)) selected.add(m);
            }
        }
    }

    private void refreshMeteorModuleNames() {
        autismModuleNames.clear();
        meteorModuleNames.clear();
        DirectScrollViewport autismVp = catalogScrollViewports.get("toggle_module_autism_registry");
        if (autismVp != null) autismVp.jumpTo(0);
        DirectScrollViewport meteorVp = catalogScrollViewports.get("toggle_module_meteor_registry");
        if (meteorVp != null) meteorVp.jumpTo(0);
        DirectScrollViewport emptyVp = catalogScrollViewports.get("toggle_module_registry_empty");
        if (emptyVp != null) emptyVp.jumpTo(0);
        for (String moduleName : autismclient.util.AutismCompatManager.getAutismModuleNames()) {
            if (moduleName != null && !moduleName.isBlank() && !containsIgnoreCase(autismModuleNames, moduleName)) {
                autismModuleNames.add(moduleName);
            }
        }
        for (String moduleName : autismclient.util.AutismCompatManager.getMeteorOnlyModuleNames()) {
            if (moduleName != null && !moduleName.isBlank() && !containsIgnoreCase(meteorModuleNames, moduleName)) {
                meteorModuleNames.add(moduleName);
            }
        }
        autismModuleNames.sort(String::compareToIgnoreCase);
        meteorModuleNames.sort(String::compareToIgnoreCase);
    }

    private List<String> filterEntries(List<String> source, String query) {
        return filterEntries(null, source, query);
    }

    private List<String> filterRegistryValues(String cacheKey, List<String> source, String query,
                                              Function<String, String> labeler) {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> target = catalogFilteredValues.computeIfAbsent(cacheKey, ignored -> new ArrayList<>());
        return MacroTypedListControl.refilterValues(source, target,
                id -> id != null && matchesListFilter(filter, id, trimMinecraftPrefix(id), labeler.apply(id)));
    }

    private List<String> filterEntries(String cacheKey, List<String> source, String query) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> target = cacheKey == null
                ? new ArrayList<>()
                : catalogFilteredValues.computeIfAbsent(cacheKey, ignored -> new ArrayList<>());
        return MacroTypedListControl.refilterValues(source, target,
                value -> value != null && (filter.isEmpty() || value.toLowerCase(Locale.ROOT).contains(filter)));
    }

    private void addFilteredPayPlayers(List<String> selected, String query) {
        if (selected == null) return;
        for (String player : filterEntries(payScannedPlayers, query)) {
            if (!containsIgnoreCase(selected, player)) selected.add(player);
        }
    }

    private void togglePayPlayerSelection(List<String> selected, String player) {
        if (selected == null || player == null || player.isBlank()) return;
        if (containsIgnoreCase(selected, player)) removeStringIgnoreCase(selected, player);
        else selected.add(player);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private void removeStringIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return;
        values.removeIf(value -> value != null && value.equalsIgnoreCase(target));
    }

    private void addFirstFilteredModule(String query) {
        List<String> filtered = filterEntries(autismModuleNames, query);
        if (filtered.isEmpty()) filtered = filterEntries(meteorModuleNames, query);
        if (!filtered.isEmpty()) addToggleModuleEntry(filtered.get(0));
    }

    private void addToggleModuleEntry(String moduleName) {
        if (toggleModuleEntries == null || moduleName == null || moduleName.isBlank() || containsModuleEntry(toggleModuleEntries, moduleName)) return;
        toggleModuleEntries.add(new autismclient.util.macro.ToggleModuleAction.ModuleEntry(moduleName, autismclient.util.macro.ToggleModuleAction.ToggleMode.TOGGLE));
    }

    private boolean containsModuleEntry(List<autismclient.util.macro.ToggleModuleAction.ModuleEntry> entries, String moduleName) {
        if (entries == null || moduleName == null) return false;
        for (autismclient.util.macro.ToggleModuleAction.ModuleEntry entry : entries) {
            if (entry != null && entry.moduleName != null && entry.moduleName.equalsIgnoreCase(moduleName)) return true;
        }
        return false;
    }

    private String formatToggleModeShort(autismclient.util.macro.ToggleModuleAction.ToggleMode mode) {
        return switch (mode) {
            case ENABLE -> "Enable";
            case DISABLE -> "Disable";
            default -> "Toggle";
        };
    }

    private void renderWaitSlotChangePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        int headerBtnW = 44;

        renderInlineToggle(ctx, font, MacroAction.LISTEN_DURING_PREVIOUS_KEY,
                "Listen During Previous", x, cy, w, mx, my);
        cy += ROW_H + ROW_GAP;
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);

        List<WaitForSlotChangeAction.WaitEntry> entries =
                wscEntries != null ? wscEntries : new ArrayList<>();
        if (wscEditIndex >= entries.size()) wscEditIndex = -1;

        UiText.draw(ctx, textRenderer,
                "Items / Slots (" + entries.size() + ")", font,
                AutismColors.textSecondary(), x, cy + 2, false);
        boolean canClear = !entries.isEmpty();
        int clearX = x + w - headerBtnW;
        renderOverlayButton(ctx, clearX, cy, headerBtnW, 14, "Clear",
                CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
            entries.clear(); wscEditIndex = -1; clearWscEditorFields();
        });
        cy += 14;

        int delW    = 13;
        int rowW    = w - SCROLLBAR_W - 1 - delW - 2;

        int selAreaH  = SEL_LIST_MAX_VIS * SEL_ITEM_H;

        DirectScrollViewport wscViewport = getOrCreateViewport(selectedScrollViewports, "_wsc_entries",
            x, cy, w, selAreaH, SEL_ITEM_H, SCROLLBAR_W);
        wscViewport.setContentHeight(entries.size() * SEL_ITEM_H);

        wscViewport.renderScrollbar(ctx, mx, my);

        if (!entries.isEmpty()) {
            wscViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
            int firstVis = wscViewport.getFirstVisibleRow();
            for (int i = firstVis; i < entries.size() && i <= wscViewport.getLastVisibleRow(); i++) {
                int iy = wscViewport.getRowScreenY(i);
                if (iy == Integer.MIN_VALUE) continue;
                boolean selected = (i == wscEditIndex);
                WaitForSlotChangeAction.WaitEntry e = entries.get(i);

                Component targetDisp = formatItemTargetText(e.resolvedTarget(), "(any slot)");
                String modeSummary = " \u2022 " + e.modeLabel();
                Component rowLabel = Component.empty().append(targetDisp).append(
                        Component.literal(modeSummary).withStyle(s -> s.withColor(0xFF888888)));
                boolean rowHovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        rowLabel,
                        UiBounds.of(x, iy, rowW, 13),
                        rowHovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        true
                );

                final int fi = i;
                if (iy + 13 > cy && iy < cy + selAreaH) {
                    hitRegions.add(new HitRegion(x, Math.max(cy, iy), rowW, Math.min(iy + 13, cy + selAreaH) - Math.max(cy, iy), () -> toggleWscEditSelection(fi)));
                }

                int delX = x + rowW + 2;
                renderIconDeleteButton(ctx, delX, iy, delW, mx, my, () -> {
                    if (wscEditIndex == fi) { wscEditIndex = -1; clearWscEditorFields(); }
                    else if (wscEditIndex > fi) wscEditIndex--;
                    entries.remove(fi);
                    DirectScrollViewport vp = selectedScrollViewports.get("_wsc_entries");
                    if (vp != null) vp.scrollBy(-1);
                });
            }
            wscViewport.endRender(ctx);
        } else {
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No entries yet. Add an item or slot below.", x, cy, w - SCROLLBAR_W - 1);
        }
        cy += selAreaH;

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 6;

        AutismChatField wscAddF  = addFields.get("_wsc_add");
        AutismChatField wscSlotF = textFields.get("wsc_slot");
        if (wscAddF != null && wscSlotF != null) {
            int pickW = 32;
            int slotW = 44;
            int pickX = x + w - pickW;
            int slotX = pickX - 2 - slotW;
            wscAddF.setX(x);      wscAddF.setY(cy + 1);  wscAddF.setWidth(slotX - x - 2);
            wscSlotF.setX(slotX); wscSlotF.setY(cy + 1); wscSlotF.setWidth(slotW);
            wscAddF.render(ctx, mx, my, delta);
            wscSlotF.render(ctx, mx, my, delta);

            boolean capturing = captureSession.isItemSlotCapture("_wsc_entries");
            renderFieldCaptureButton(ctx, pickX, cy, pickW, 14, CaptureMode.ITEM_SLOT, capturing,
                    true, mx, my, () -> toggleItemSlotCapture("_wsc_entries"));
        }
        cy += 16;

        {
            boolean editing = wscEditIndex >= 0 && wscEditIndex < entries.size();
            WaitForSlotChangeAction.WaitMode curMode  =
                    editing ? entries.get(wscEditIndex).waitMode  : wscAddMode;
            int curCount = editing ? entries.get(wscEditIndex).targetCount : wscAddCount;

            int addBtnW = 34;
            int cntW    = 36;
            int modBtnW = w - addBtnW - 2 - cntW - 2;

            WaitForSlotChangeAction.WaitMode[] modes = WaitForSlotChangeAction.WaitMode.values();
            java.util.List<String> modeOptions = new java.util.ArrayList<>();
            for (WaitForSlotChangeAction.WaitMode m : modes) modeOptions.add(m.name().replace("_", " "));
            renderInlineDropdown("wsc_mode", modeOptions, curMode.ordinal(), x, cy, modBtnW, 14, idx -> {
                WaitForSlotChangeAction.WaitMode chosen = modes[Math.max(0, Math.min(idx, modes.length - 1))];
                if (wscEditIndex >= 0 && wscEditIndex < entries.size()) {
                    entries.get(wscEditIndex).waitMode = chosen;
                } else {
                    wscAddMode = chosen;
                }
            });

            int cntX = x + modBtnW + 2;
            AutismChatField countF = textFields.get("wsc_count");
            boolean countRelevant = curMode == WaitForSlotChangeAction.WaitMode.COUNT_AT_LEAST
                                 || curMode == WaitForSlotChangeAction.WaitMode.COUNT_BELOW;
            if (countF != null) {
                if (countRelevant) {
                    countF.setX(cntX); countF.setY(cy + 1); countF.setWidth(cntW);
                    if (!countF.isFocused() && !suppressWscLiveUpdate) {
                        suppressWscLiveUpdate = true;
                        countF.setText(String.valueOf(curCount));
                        suppressWscLiveUpdate = false;
                    }
                    countF.render(ctx, mx, my, delta);
                } else {
                    CompactSurfaces.valueField(ctx, cntX, cy, cntW, 14);
                    fillBorder(ctx, cntX, cy, cntW, 14, 0xFF2A1A1A);
                    UiText.draw(ctx, textRenderer, "-", font, AutismColors.textDim(),
                            cntX + (cntW - uiWidth(font, "-")) / 2, cy + 3, false);
                }
            }

            int plusX = cntX + cntW + 2;
            renderOverlayButton(ctx, plusX, cy, addBtnW, 14, "+Add",
                    CompactOverlayButton.Variant.SUCCESS, true, mx, my, () -> {
                if (wscAddF != null && wscSlotF != null) {
                    applyWscAddEntry(wscAddF, wscSlotF);
                    wscEditIndex = -1;
                    clearWscEditorFields();
                }
            });
        }
        cy += 16;

        renderEditorHint(ctx, x, cy, w,
                wscEditIndex >= 0 ? "Editing row - all entries must match."
                        : "Click a row to edit - all must match.");
    }

    private void renderUseItemPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        cy = renderUseItemTargetRow(ctx, x, cy, w, mx, my, delta);
        int btnW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, btnW, 14, "Use Held", mx, my, this::fillUseItemFromHeld);
        renderActionButton(ctx, x + btnW + 4, cy, btnW, 14, "Use Current", mx, my, () -> {
            AutismChatField field = textFields.get("itemName");
            if (field != null) field.setText("");
            AutismChatField slotField = textFields.get("slot");
            if (slotField != null) slotField.setText("");
            editorItemFields.remove("itemName");
        });
        cy += 18;

        cy = renderFieldByKey(ctx, "useMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForFinish", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "sneak", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "holdTicks", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "useCount", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGui", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);

        String itemTarget = currentUseItemTargetLabel();
        String mode = currentEnumValue("useMode");
        String hint = "CUSTOM_HOLD".equals(mode)
                ? "Hold-uses " + itemTarget + " for the set ticks."
                : "Uses " + itemTarget + " for the set count.";
        renderEditorHint(ctx, x, cy, w, hint);
    }

    private int renderUseItemTargetRow(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
        FieldDef itemFieldDef = getField("itemName");
        AutismChatField itemField = textFields.get("itemName");
        AutismChatField slotField = textFields.get("slot");
        if (itemFieldDef == null || itemField == null || slotField == null) {
            int cy = renderFieldByKey(ctx, "itemName", x, y, w, mx, my, delta);
            return renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
        }

        Identifier font = theme.fontFor(UiTone.BODY);
        int labelW = labelWidth(w, itemFieldDef.label(), font, 88);
        drawLabel(ctx, itemFieldDef.label(), x, y, labelW, font);

        int controlX = controlX(x, labelW);
        int controlW = Math.max(1, controlWidth(w, labelW));
        int pickW = 30;
        int slotW = 34;
        int gap = 2;
        int itemW = Math.max(1, controlW - pickW - slotW - gap * 2);

        itemField.setX(controlX);
        itemField.setY(y + 2);
        itemField.setWidth(itemW);
        itemField.render(ctx, mx, my, delta);

        int slotX = controlX + itemW + gap;
        slotField.setX(slotX);
        slotField.setY(y + 2);
        slotField.setWidth(slotW);
        slotField.render(ctx, mx, my, delta);

        int pickX = slotX + slotW + gap;
        boolean capturing = captureSession.isItemSlotCapture("itemName");
        renderFieldCaptureButton(
                ctx,
                pickX,
                y + 2,
                pickW,
                14,
                CaptureMode.ITEM_SLOT,
                capturing,
                true,
                mx,
                my,
                () -> toggleItemSlotCapture("itemName")
        );

        return y + ROW_H + ROW_GAP;
    }

    private void renderRotatePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "yaw", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "pitch", x, cy, w, mx, my, delta);
        renderActionButton(ctx, x, cy, w, 14, "Capture View", mx, my, this::fillRotateFromCurrentView);
        cy += 18;
        cy = renderFieldByKey(ctx, "smooth", x, cy, w, mx, my, delta);
        if (toggleStates.getOrDefault("smooth", false)) {
            cy = renderRotateSmoothnessSlider(ctx, x, cy, w, mx, my);
        } else {
            rotateSmoothnessSliderDragging = false;
            clearRotateSmoothnessSliderBounds();
        }
        cy = renderFieldByKey(ctx, "waitForCompletion", x, cy, w, mx, my, delta);
        renderEditorHint(ctx, x, cy, w,
                toggleStates.getOrDefault("smooth", false)
                        ? "Capture View fills in your current yaw and pitch. Smoothness 1 is faster, 10 is slower."
                        : "Capture View fills in your current yaw and pitch.");
    }

    private void renderLookAtPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "targetMode", x, cy, w, mx, my, delta);
        String mode = currentEnumValue("targetMode");
        if ("BLOCK".equals(mode)) {
            cy = renderFieldByKey(ctx, "searchRadius", x, cy, w, mx, my, delta);
            cy = renderFieldByKey(ctx, "blockIds", x, cy, w, mx, my, delta);
        } else if ("ENTITY".equals(mode)) {
            cy = renderFieldByKey(ctx, "searchRadius", x, cy, w, mx, my, delta);
            cy = renderLookAtEntitySelector(ctx, x, cy, w, mx, my, delta);
        } else {
            cy = renderFieldByKey(ctx, "blockPos", x, cy, w, mx, my, delta);
        }

        cy = renderFieldByKey(ctx, "smooth", x, cy, w, mx, my, delta);
        if (toggleStates.getOrDefault("smooth", false)) {
            cy = renderRotateSmoothnessSlider(ctx, x, cy, w, mx, my);
        } else {
            rotateSmoothnessSliderDragging = false;
            clearRotateSmoothnessSliderBounds();
        }
        cy = renderFieldByKey(ctx, "waitForCompletion", x, cy, w, mx, my, delta);

        String hint = switch (mode) {
            case "BLOCK" -> "Search blocks and it faces the nearest matching block in range.";
            case "ENTITY" -> "Pick entity types or specific captures and it faces the nearest match in range.";
            default -> "Pick a specific block position to face.";
        };
        if (toggleStates.getOrDefault("smooth", false)) hint += " Smoothness 1 is faster, 10 is slower.";
        renderEditorHint(ctx, x, cy, w, hint);
    }

    private int renderLookAtEntitySelector(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = y;

        List<String> selected = stringLists.getOrDefault("entityIds", Collections.emptyList());
        cy = renderSimpleSelectedList(ctx, x, cy, w, mx, my, "look_at_entity_selected", "Entities", selected, selected::remove,
                this::formatEntityEntry, "(add entity types or specific captures)");

        AutismChatField search = addFields.get("entityIds");
        int searchW = w - 66;
        if (search != null) {
            search.setX(x);
            search.setY(cy + 1);
            search.setWidth(searchW);
            search.render(ctx, mx, my, delta);
        }
        int modeX = x + searchW + 2;
        String modeLabel = entitySpecificCaptureMode ? "[Spec]" : "[Type]";
        renderActionButton(ctx, modeX, cy, 30, 14, modeLabel, mx, my, () -> entitySpecificCaptureMode = !entitySpecificCaptureMode);
        renderActionButton(ctx, modeX + 34, cy, 32, 14, "CAP", mx, my, this::startLookAtEntityCapture);
        cy += 18;

        String filter = search != null ? search.getText().trim().toLowerCase() : "";
        List<String> filtered = filterRegistryValues("look_at_entity_search", getAllEntityIds(), filter, AutismRegistryLabels::entity);
        renderSearchRegistryList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "look_at_entity_search",
            filtered,
            4,
            value -> {
                if (selected.contains(value)) selected.remove(value);
                else selected.add(value);
            },
            selected::contains,
            AutismRegistryLabels::entity
        );
        cy += 4 + 4 * CATALOG_ITEM_H + 10;

        UiText.draw(ctx, textRenderer, "Nearby Entities", font, AutismColors.textSecondary(), x, cy + 2, false);
        cy += 13;
        List<String> nearbyEntries = getNearbyEntityEntries();
        renderSearchRegistryList(
            ctx,
            x,
            cy,
            w,
            mx,
            my,
            "look_at_entity_nearby",
            nearbyEntries,
            3,
            value -> {
                String stored = entitySpecificCaptureMode ? value : extractEntityTypeFromNearbyEntry(value);
                if (stored.isBlank()) return;
                if (entitySpecificCaptureMode) {
                    boolean removed = selected.removeIf(existing -> existing.startsWith(entitySpecificEntryPrefix(stored)));
                    if (!removed) selected.add(stored);
                } else if (selected.contains(stored)) {
                    selected.remove(stored);
                } else {
                    selected.add(stored);
                }
            },
            value -> {
                String stored = entitySpecificCaptureMode ? value : extractEntityTypeFromNearbyEntry(value);
                return entitySpecificCaptureMode
                    ? selected.stream().anyMatch(existing -> existing.startsWith(entitySpecificEntryPrefix(stored)))
                    : selected.contains(stored);
            },
            this::formatNearbyEntityEntry
        );
        return cy + 3 * CATALOG_ITEM_H + 4;
    }

    private void renderGoToPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        FieldDef posField = getField("pos");
        if (posField != null) {
            cy = renderBlockPosWithoutCapture(ctx, posField, x, cy, w, mx, my, delta);
        }
        renderActionButton(ctx, x, cy, w, 14, "Capture Here", mx, my, this::fillGoToFromPlayer);
        cy += 18;
        cy = renderFieldByKey(ctx, "waitForArrival", x, cy, w, mx, my, delta);
        renderEditorHint(ctx, x, cy, w, "Wait for Arrival continues only after Baritone finishes.");
    }

    private void renderSwapSlotsPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        UiText.draw(ctx, textRenderer, "From", font, AutismColors.textSecondary(), x, cy + 2, false);
        renderActionButton(ctx, x + w - 68, cy, 68, 14, "Flip Ends", mx, my, this::swapSwapSlotEndpoints);
        cy += 16;
        cy = renderFieldByKey(ctx, "fromUseItemName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "fromItemName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "fromSlot", x, cy, w, mx, my, delta);

        CompactSurfaces.divider(ctx, x, cy + 2, w, AutismColors.subPanelBorder());
        cy += 8;

        UiText.draw(ctx, textRenderer, "To", font, AutismColors.textSecondary(), x, cy + 2, false);
        cy += 16;
        cy = renderFieldByKey(ctx, "toUseItemName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "toItemName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "toSlot", x, cy, w, mx, my, delta);

        renderEditorHint(ctx, x, cy, w, buildSwapSlotsSummary());
    }

    private void renderClickPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "clickType", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "clickCount", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "guiName", x, cy, w, mx, my, delta);

        renderEditorHint(ctx, x, cy, w, buildClickSummary());
    }

    private void renderDisconnectPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "delayMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "lagMethod", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "kickMethod", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "packetCount", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "useNextAction", x, cy, w, mx, my, delta);

        cy = renderFieldByKey(ctx, "trigger", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "tolerance", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "bufferMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "timeoutSec", x, cy, w, mx, my, delta);

        String mode = currentEnumValue("mode");
        String hint = switch (mode) {
            case "DISCONNECT" -> "Simple disconnect. Delay only controls how long to wait before closing the connection.";
            case "KICK" -> "Sends lag packets, then the selected kick packet. Packet Count controls how hard it pushes.";
            case "AUTO_DISCONNECT" -> "Auto-disconnects at the perfect dupe timing.";
            case "KICK_DUPE" -> toggleStates.getOrDefault("useNextAction", false)
                    ? "Kick Dupe will run the next eligible macro actions inside the lag sandwich, then kick."
                    : "Kick Dupe will try to use a bundle, then kick.";
            default -> "Unknown mode";
        };
        renderEditorHint(ctx, x, cy, w, hint, "DISCONNECT".equals(mode) ? AutismColors.textDim() : 0xFFFFB36B);
    }

    private void renderWaitGuiPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "waitMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "guiType", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "matchMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "guiTitle", x, cy, w, mx, my, delta);
        cy = renderCapturePatternBuilder(ctx, textFields.get("guiTitle"), x, cy, w, mx, my);
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);

        String mode = currentEnumValue("waitMode");
        String guiType = currentEnumValue("guiType");
        String hint = "CLOSE".equals(mode)
                ? "Waits until the matching " + guiType + " GUI closes."
                : "Waits until a matching " + guiType + " GUI opens.";
        renderEditorHint(ctx, x, cy, w, hint);
    }

    private void renderSelectSlotPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);

        int btnW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, btnW, 14, "Held", mx, my, this::fillSelectSlotFromHeld);
        renderActionButton(ctx, x + btnW + 4, cy, btnW, 14, "Use Slot Only", mx, my, this::clearSelectSlotItemName);
        cy += 18;

        UiText.draw(ctx, textRenderer, "Fallback Hotbar Slot", font, AutismColors.textSecondary(), x, cy + 2, false);
        cy += 12;

        cy = renderSelectSlotHotbarPicker(ctx, x, cy, w, mx, my);
        cy = renderFieldByKey(ctx, "strategy", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "outputVariable", x, cy, w, mx, my, delta);

        renderEditorHint(ctx, x, cy, w, buildSelectSlotSummary());
    }

    private int renderSelectSlotHotbarPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int selectedSlot = getSelectSlotHotbarIndex();
        int slotGap = 2;
        int cellW = Math.max(16, Math.min(24, (w - slotGap * 8) / 9));
        int totalW = cellW * 9 + slotGap * 8;
        int startX = x + Math.max(0, (w - totalW) / 2);

        for (int slot = 0; slot < 9; slot++) {
            int sx = startX + slot * (cellW + slotGap);
            boolean selected = selectedSlot == slot;
            String label = String.valueOf(slot + 1);
            final int clickedSlot = slot;
            renderOverlayButton(ctx, sx, y, cellW, 14, label,
                    selected ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.GHOST,
                    true, mx, my, () -> setSelectSlotHotbarIndex(clickedSlot));
        }

        return y + 18;
    }

    private void renderWaitCooldownPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);

        int halfW = (w - 4) / 2;
        renderActionButton(ctx, x, cy, halfW, 14, currentWaitCooldownHandLabel(), mx, my, this::toggleWaitCooldownHand);
        renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Capture Held", mx, my, this::fillWaitCooldownFromHeld);
        cy += 18;

        renderActionButton(ctx, x, cy, Math.min(130, w), 14, "Use InteractionHand Item", mx, my, this::clearWaitCooldownItemName);
        cy += 18;

        renderEditorHint(ctx, x, cy, w, buildWaitCooldownSummary());
    }

    private void renderWaitInventoryPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;
        cy = renderFieldByKey(ctx, MacroAction.LISTEN_DURING_PREVIOUS_KEY, x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "condition", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "itemName", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "count", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
        if (isFieldVisible(getField("slot"))) {
            int third = (w - 4) / 3;
            renderActionButton(ctx, x, cy, third, 14, "Selected", mx, my, this::fillWaitInventorySelectedSlot);
            boolean capturing = captureSession.isItemSlotCapture("wait_inventory_slot");
            renderFieldCaptureButton(ctx, x + third + 2, cy, third, 14, CaptureMode.ITEM_SLOT, capturing,
                    true, mx, my, "Pick Slot", "Done", this::toggleWaitInventorySlotCapture);
            renderActionButton(ctx, x + (third + 2) * 2, cy, w - (third + 2) * 2, 14, "Slot 0", mx, my, () -> setWaitInventorySlot(0));
            cy += 18;
        }
        cy = renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
    }

    private void renderCaptureValuePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;
        applyCaptureSourceWaitDefault();
        cy = renderFieldByKey(ctx, "source", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "autofillCommand", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "autofillTimeoutMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "autofillCacheList", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "itemFilter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "slot", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "itemText", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listSelection", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listPickPosition", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listFilter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listFilterText", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listExcludeText", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "listStripPrefix", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "excludeSelf", x, cy, w, mx, my, delta);
        if ("RECENT_CHAT".equals(currentEnumValue("source"))) {
            cy = renderCaptureRecentChatPicker(ctx, x, cy, w, mx, my);
        } else if ("SCOREBOARD".equals(currentEnumValue("source"))) {
            cy = renderCaptureScoreboardPicker(ctx, x, cy, w, mx, my);
        } else if ("TABLIST".equals(currentEnumValue("source"))) {
            cy = renderCaptureTablistMatches(ctx, x, cy, w, mx, my);
        }
        cy = renderFieldByKey(ctx, "exampleText", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "selectedText", x, cy, w, mx, my, delta);

        autismclient.util.macro.CaptureValueAction draft = captureValueDraft();
        String primaryName = captureValuePrimaryName(draft);
        String token = primaryName.isBlank() ? "" : "{" + primaryName + "}";
        boolean exampleCapture = draft.source == autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT
            || draft.source == autismclient.util.macro.CaptureValueAction.Source.GUI_TITLE
            || draft.source == autismclient.util.macro.CaptureValueAction.Source.SCOREBOARD;

        autismclient.util.macro.CaptureValueAction.Preview preview =
            exampleCapture && !draft.exampleText.isBlank() ? draft.previewExample() : draft.preview(MC);
        String previewText = preview.success() ? preview.value(primaryName) : preview.message();
        if (preview.success() && previewText.isBlank()) previewText = preview.sourceText();

        boolean derivingPattern = exampleCapture && !draft.exampleText.isBlank()
            && draft.matchMode != autismclient.util.macro.MacroCapturePattern.Mode.MATCH;
        if (derivingPattern && draft.selectedText.isBlank()) {
            previewText = "Set Capture This";
        } else if (derivingPattern && draft.pattern.isBlank()) {
            previewText = "Not in example";
        }

        cy = renderCaptureCopyField(ctx, "Use Later", "_capture_token", token, x, cy, w, mx, my,
            "Variable copied.");
        cy = renderCaptureCopyField(ctx, "Preview Now", "_capture_preview", previewText, x, cy, w, mx, my,
            "Preview copied.");
        cy = renderFieldByKey(ctx, "numberMode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "numberModifier", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "numberModifierAmount", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForTrigger", x, cy, w, mx, my, delta);
        String behaviorHint;
        if (draft.source == autismclient.util.macro.CaptureValueAction.Source.COMMAND_AUTOFILL) {
            behaviorHint = "Asks server when run";
        } else if (draft.source == autismclient.util.macro.CaptureValueAction.Source.TABLIST) {
            behaviorHint = draft.waitForTrigger ? "Waits for player" : "Picks online player";
        } else {
            behaviorHint = draft.waitForTrigger
                ? draft.source == autismclient.util.macro.CaptureValueAction.Source.SCOREBOARD
                    ? "Waits for row change"
                    : "Waits for next match"
                : draft.source == autismclient.util.macro.CaptureValueAction.Source.RECENT_CHAT
                    || draft.source == autismclient.util.macro.CaptureValueAction.Source.GUI_TITLE
                    ? "Uses newest match"
                    : "Reads value now";
        }
        if (!draft.saveAs.isBlank()
                && autismclient.util.macro.MacroVariableContext.cleanRootName(draft.saveAs).isBlank()) {
            behaviorHint = "Bad variable name";
        }
        renderEditorHint(ctx, x, cy, w, behaviorHint);
    }

    private int renderCaptureRecentChatPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        List<autismclient.util.macro.MacroExecutor.RecentChatMessage> history =
            autismclient.util.macro.MacroExecutor.getRecentChatMessages().stream()
                .limit(CAPTURE_CHAT_HISTORY_LIMIT)
                .toList();
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, "Recent Chat", font,
            AutismColors.textSecondary(), x, y + 2, false);
        y += 13;
        int listH = captureChatHistoryListHeight(history.size());
        renderRecentChatHistoryList(ctx, x, y, w, listH, mx, my, history,
            "capture_value_recent", CAPTURE_CHAT_VISIBLE_ROWS, this::applyCaptureValueChatHistoryEntry);
        return y + listH + ROW_GAP;
    }

    private void applyCaptureValueChatHistoryEntry(autismclient.util.macro.MacroExecutor.RecentChatMessage entry) {
        if (entry == null) return;
        String text = formatWaitChatHistoryEntry(entry);
        String suggested = autismclient.util.macro.CaptureValueAction.suggestDynamicPart(text);
        AutismChatField exampleField = textFields.get("exampleText");
        AutismChatField selectedField = textFields.get("selectedText");
        suppressCaptureValueAutoPattern = true;
        if (exampleField != null) exampleField.setText(text);
        if (selectedField != null) selectedField.setText(suggested);
        suppressCaptureValueAutoPattern = false;

        lastGeneratedCapturePattern = editorFieldText("pattern", "");
        refreshCaptureValueAutoPattern();
    }

    private int renderCaptureScoreboardPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        List<autismclient.util.macro.CaptureValueAction.ScoreboardLine> lines =
            autismclient.util.macro.CaptureValueAction.scoreboardLines(MC);
        Identifier font = theme.fontFor(UiTone.BODY);
        String title = lines.isEmpty() || lines.get(0).objectiveTitle().isBlank()
            ? "Scoreboard"
            : "Scoreboard - " + lines.get(0).objectiveTitle();
        UiText.draw(ctx, textRenderer,
            UiText.trimToWidth(textRenderer, title, w, font, -1),
            font, AutismColors.textSecondary(), x, y + 2, false);
        y += 13;
        int listH = lines.isEmpty() ? 24
            : Math.min(CAPTURE_SCOREBOARD_VISIBLE_ROWS, lines.size()) * CAPTURE_SCOREBOARD_ROW_H;
        int itemW = Math.max(1, w - SCROLLBAR_W - 1);
        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, "capture_value_scoreboard",
            x, y, w, listH, CAPTURE_SCOREBOARD_ROW_H, SCROLLBAR_W);
        viewport.setContentHeight(lines.size() * CAPTURE_SCOREBOARD_ROW_H);
        viewport.renderScrollbar(ctx, mx, my);
        if (lines.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
            UiText.draw(ctx, textRenderer, "No scoreboard", font,
                AutismColors.textDim(), x + 3, y + 7, false);
            return y + 24 + ROW_GAP;
        }

        String selectedKey = editorFieldText("scoreboardRow", "");
        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = viewport.getFirstVisibleRow();
        for (int i = first; i < lines.size() && i <= viewport.getLastVisibleRow(); i++) {
            int rowY = viewport.getRowScreenY(i);
            if (rowY == Integer.MIN_VALUE) continue;
            var line = lines.get(i);
            boolean selected = line.key().equals(selectedKey);
            boolean hovered = mx >= x && mx < x + itemW && my >= rowY && my < rowY + CAPTURE_SCOREBOARD_ROW_H;
            CompactSurfaces.row(ctx, x, rowY, itemW, CAPTURE_SCOREBOARD_ROW_H, hovered, selected);
            CompactSurfaces.indicator(ctx, x, rowY + 2, 3, CAPTURE_SCOREBOARD_ROW_H - 4,
                selected ? 0xFF35D873 : 0xFFCC7A22);
            String rowLabel = "Row " + (line.row() + 1);
            UiText.draw(ctx, textRenderer, rowLabel, font, AutismColors.textDim(), x + 6, rowY + 2, false);
            UiText.draw(ctx, textRenderer,
                UiText.trimToWidth(textRenderer, line.text(), Math.max(20, itemW - 10), font, -1),
                font, AutismColors.textPrimary(), x + 6, rowY + 11, false);
            final var selectedLine = line;
            hitRegions.add(new HitRegion(x, rowY, itemW, CAPTURE_SCOREBOARD_ROW_H,
                () -> applyCaptureValueScoreboardLine(selectedLine)));
        }
        viewport.endRender(ctx);
        return y + listH + ROW_GAP;
    }

    private java.util.List<String> captureTablistNamesCache;
    private long captureTablistNamesAtMs;

    private java.util.List<String> captureTablistNames() {
        long now = System.currentTimeMillis();
        if (captureTablistNamesCache == null || now - captureTablistNamesAtMs > CAPTURE_TABLIST_SCAN_TTL_MS) {
            java.util.List<autismclient.util.AutismPlayerScanner.ScannedPlayer> players =
                autismclient.util.AutismPlayerScanner.scan(MC);
            java.util.List<String> names = new java.util.ArrayList<>(players.size());
            for (autismclient.util.AutismPlayerScanner.ScannedPlayer player : players) names.add(player.name());
            captureTablistNamesCache = names;
            captureTablistNamesAtMs = now;
        }
        return captureTablistNamesCache;
    }

    private java.util.List<String> captureTablistAllNames(autismclient.util.macro.CaptureValueAction draft) {
        java.util.List<String> names = new java.util.ArrayList<>(captureTablistNames());
        if (!draft.excludeSelf && MC.player != null && MC.player.getGameProfile() != null) {
            names.add(MC.player.getGameProfile().name());
            names.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return names;
    }

    private int captureTablistMatchCount() {
        autismclient.util.macro.CaptureValueAction draft = captureValueDraft();
        return draft.filterCandidates(captureTablistAllNames(draft)).size();
    }

    private int renderCaptureTablistMatches(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        autismclient.util.macro.CaptureValueAction draft = captureValueDraft();
        java.util.List<String> names = captureTablistAllNames(draft);
        java.util.List<String> pool = draft.filterCandidates(names);
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, "Matches (" + pool.size() + " of " + names.size() + ")", font,
            AutismColors.textSecondary(), x, y + 2, false);
        y += 13;
        int listH = pool.isEmpty() ? 24
            : Math.min(CAPTURE_TABLIST_VISIBLE_ROWS, pool.size()) * CAPTURE_TABLIST_ROW_H;
        int itemW = Math.max(1, w - SCROLLBAR_W - 1);
        DirectScrollViewport viewport = getOrCreateViewport(selectedScrollViewports, "capture_value_tablist",
            x, y, w, listH, CAPTURE_TABLIST_ROW_H, SCROLLBAR_W);
        viewport.setContentHeight(pool.size() * CAPTURE_TABLIST_ROW_H);
        viewport.renderScrollbar(ctx, mx, my);
        if (pool.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
            UiText.draw(ctx, textRenderer, "No matches", font,
                AutismColors.textDim(), x + 3, y + 7, false);
            return y + 24 + ROW_GAP;
        }
        viewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = viewport.getFirstVisibleRow();
        for (int i = first; i < pool.size() && i <= viewport.getLastVisibleRow(); i++) {
            int rowY = viewport.getRowScreenY(i);
            if (rowY == Integer.MIN_VALUE) continue;
            String name = pool.get(i);
            boolean hovered = mx >= x && mx < x + itemW && my >= rowY && my < rowY + CAPTURE_TABLIST_ROW_H;
            CompactSurfaces.row(ctx, x, rowY, itemW, CAPTURE_TABLIST_ROW_H, hovered, false);
            UiText.draw(ctx, textRenderer,
                UiText.trimToWidth(textRenderer, name, Math.max(20, itemW - 10), font, -1),
                font, AutismColors.textPrimary(), x + 6, rowY + 3, false);
            hitRegions.add(new HitRegion(x, rowY, itemW, CAPTURE_TABLIST_ROW_H,
                () -> copyCaptureEditorValue(name, "Name copied.")));
        }
        viewport.endRender(ctx);
        return y + listH + ROW_GAP;
    }

    private void applyCaptureValueScoreboardLine(autismclient.util.macro.CaptureValueAction.ScoreboardLine line) {
        if (line == null) return;
        AutismChatField rowField = textFields.get("scoreboardRow");
        AutismChatField rowIndexField = textFields.get("scoreboardRowIndex");
        AutismChatField objectiveField = textFields.get("scoreboardObjective");
        AutismChatField exampleField = textFields.get("exampleText");
        AutismChatField selectedField = textFields.get("selectedText");
        String suggested = autismclient.util.macro.CaptureValueAction.suggestDynamicPart(line.text());
        suppressCaptureValueAutoPattern = true;
        if (rowField != null) rowField.setText(line.key());
        if (rowIndexField != null) rowIndexField.setText(Integer.toString(line.row()));
        if (objectiveField != null) objectiveField.setText(line.objective());
        if (exampleField != null) exampleField.setText(line.text());
        if (selectedField != null) selectedField.setText(suggested);
        suppressCaptureValueAutoPattern = false;

        lastGeneratedCapturePattern = editorFieldText("pattern", "");
        refreshCaptureValueAutoPattern();
    }

    private String labelFor(FieldDef field) {
        if ("waitForTrigger".equals(field.key())
                && targetAction instanceof autismclient.util.macro.CaptureValueAction) {
            return switch (currentEnumValue("source") == null ? "" : currentEnumValue("source")) {
                case "RECENT_CHAT", "GUI_TITLE" -> "Wait for New";
                case "SCOREBOARD" -> "Wait for Change";
                default -> "Wait for Match";
            };
        }
        return field.label();
    }

    private void applyCaptureSourceWaitDefault() {
        String source = currentEnumValue("source");
        if (source == null || source.equals(lastCaptureSource)) return;
        boolean firstFrame = lastCaptureSource.isEmpty();
        lastCaptureSource = source;
        if (firstFrame || captureWaitTouched) return;
        autismclient.util.macro.CaptureValueAction.Source parsed;
        try {
            parsed = autismclient.util.macro.CaptureValueAction.Source.valueOf(source);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        boolean waits = autismclient.util.macro.CaptureValueAction.defaultWaitForTrigger(parsed);
        toggleStates.put("waitForTrigger", waits);
        if (workingTag != null) workingTag.putBoolean("waitForTrigger", waits);
    }

    private void refreshCaptureValueAutoPattern() {
        if (suppressCaptureValueAutoPattern || !(targetAction instanceof autismclient.util.macro.CaptureValueAction)) return;
        String source = currentEnumValue("source");

        if (!"GUI_TITLE".equals(source) && !"RECENT_CHAT".equals(source) && !"SCOREBOARD".equals(source)) return;
        String example = editorFieldText("exampleText", "");
        String selected = editorFieldText("selectedText", "");
        String variable = editorFieldText("saveAs", "value");
        if (workingTag != null) {
            workingTag.putString("exampleText", example);
            workingTag.putString("selectedText", selected);
        }
        AutismChatField patternField = textFields.get("pattern");
        String current = patternField != null ? patternField.getText()
            : workingTag == null ? "" : workingTag.getStringOr("pattern", "");

        if (!current.isBlank() && !current.equals(lastGeneratedCapturePattern)) return;
        if (example.isBlank() || selected.isBlank()) {
            if (patternField != null) patternField.setText("");
            if (workingTag != null) workingTag.putString("pattern", "");
            lastGeneratedCapturePattern = "";
            return;
        }
        String generated = autismclient.util.macro.CaptureValueAction.buildCapturePattern(example, selected, variable);

        if (generated.isBlank()) return;
        if (patternField != null) patternField.setText(generated);
        if (workingTag != null) {
            workingTag.putString("pattern", generated);
        }
        lastGeneratedCapturePattern = generated;
        if (!generated.isBlank()) {
            enumIndices.put("matchMode", 1);
            if (workingTag != null) workingTag.putString("matchMode", "CAPTURE");
        }
    }

    private int captureChatHistoryListHeight(int count) {
        if (count <= 0) return 24;
        return Math.min(CAPTURE_CHAT_VISIBLE_ROWS, Math.min(CAPTURE_CHAT_HISTORY_LIMIT, count)) * WAIT_CHAT_ROW_H;
    }

    private void clipHitRegions(int fromIndex, UiBounds clip) {
        if (clip == null) return;
        for (int i = hitRegions.size() - 1; i >= Math.max(0, fromIndex); i--) {
            HitRegion region = hitRegions.get(i);
            int left = Math.max(region.x, clip.x());
            int top = Math.max(region.y, clip.y());
            int right = Math.min(region.x + region.w, clip.right());
            int bottom = Math.min(region.y + region.h, clip.bottom());
            if (right <= left || bottom <= top) {
                hitRegions.remove(i);
            } else if (left != region.x || top != region.y || right != region.x + region.w || bottom != region.y + region.h) {
                hitRegions.set(i, new HitRegion(left, top, right - left, bottom - top, region.action));
            }
        }
    }

    private int renderCaptureCopyField(GuiGraphicsExtractor ctx, String label, String fieldKey, String value,
                                       int x, int y, int w, int mx, int my, String copiedMessage) {
        Identifier font = theme.fontFor(UiTone.BODY);
        UiText.draw(ctx, textRenderer, label, font, AutismColors.textSecondary(), x, y + 2, false);
        y += 12;
        int buttonW = Math.min(52, Math.max(40, w / 4));
        AutismChatField field = addFields.get(fieldKey);
        if (field != null) {
            String safe = value == null ? "" : value;
            if (!safe.equals(field.getText())) field.setText(safe);
            field.setX(x);
            field.setY(y);
            field.setWidth(Math.max(1, w - buttonW - 4));
            field.setHeight(14);
            field.render(ctx, mx, my, frameDelta);
        }
        String copied = value == null ? "" : value;
        renderActionButton(ctx, x + w - buttonW, y, buttonW, 14, "Copy", mx, my,
            () -> copyCaptureEditorValue(copied, copiedMessage));
        return y + 18;
    }

    private void copyCaptureEditorValue(String value, String message) {
        if (value == null || value.isBlank()) {
            AutismNotifications.warning("Nothing to copy.");
            return;
        }
        MC.keyboardHandler.setClipboard(value);
        AutismNotifications.copied(message);
    }

    private autismclient.util.macro.CaptureValueAction captureValueDraft() {
        autismclient.util.macro.CaptureValueAction draft = new autismclient.util.macro.CaptureValueAction();
        try {
            draft.source = autismclient.util.macro.CaptureValueAction.Source.valueOf(currentEnumValue("source"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        try {
            draft.matchMode = autismclient.util.macro.MacroCapturePattern.Mode.valueOf(currentEnumValue("matchMode"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        try {
            draft.itemText = autismclient.util.macro.CaptureValueAction.ItemText.valueOf(currentEnumValue("itemText"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        draft.saveAs = editorFieldText("saveAs", "value");
        draft.pattern = editorFieldText("pattern", "");
        draft.exampleText = editorFieldText("exampleText", "");
        draft.selectedText = editorFieldText("selectedText", "");
        draft.itemFilter = editorFieldText("itemFilter", "");
        draft.scoreboardRow = editorFieldText("scoreboardRow", "");
        draft.scoreboardObjective = editorFieldText("scoreboardObjective", "");
        try {
            draft.scoreboardRowIndex = Integer.parseInt(editorFieldText("scoreboardRowIndex", "-1").trim());
        } catch (NumberFormatException ignored) {
            draft.scoreboardRowIndex = -1;
        }
        try {
            draft.numberMode = autismclient.util.macro.CaptureValueAction.NumberMode.valueOf(
                currentEnumValue("numberMode"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        try {
            draft.numberModifier = autismclient.util.macro.CaptureValueAction.NumberModifier.valueOf(
                currentEnumValue("numberModifier"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        try {
            draft.numberModifierAmount = Double.parseDouble(editorFieldText("numberModifierAmount", "1").trim());
        } catch (NumberFormatException ignored) {

            draft.numberModifierAmount = workingTag == null ? 1.0 : workingTag.getDoubleOr("numberModifierAmount", 1.0);
        }
        draft.waitForTrigger = toggleStates.getOrDefault("waitForTrigger",
            workingTag == null || workingTag.getBooleanOr("waitForTrigger", true));
        try {
            draft.slot = Integer.parseInt(editorFieldText("slot", "-1").trim());
        } catch (NumberFormatException ignored) {
            draft.slot = -1;
        }
        draft.autofillCommand = editorFieldText("autofillCommand", "");
        try {
            draft.autofillTimeoutMs = Integer.parseInt(editorFieldText("autofillTimeoutMs", "5000").trim());
        } catch (NumberFormatException ignored) {
            draft.autofillTimeoutMs = 5000;
        }
        draft.autofillCacheList = toggleStates.getOrDefault("autofillCacheList",
            workingTag != null && workingTag.getBooleanOr("autofillCacheList", false));
        try {
            draft.listSelection = autismclient.util.macro.CaptureListSelector.Selection.valueOf(currentEnumValue("listSelection"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        try {
            draft.listFilter = autismclient.util.macro.CaptureListSelector.Filter.valueOf(currentEnumValue("listFilter"));
        } catch (IllegalArgumentException | NullPointerException ignored) {  }
        draft.listFilterText = editorFieldText("listFilterText", "");
        draft.listExcludeText = editorFieldText("listExcludeText", "");
        try {
            draft.listPickPosition = Integer.parseInt(editorFieldText("listPickPosition", "1").trim());
        } catch (NumberFormatException ignored) {
            draft.listPickPosition = 1;
        }
        draft.listStripPrefix = toggleStates.getOrDefault("listStripPrefix",
            workingTag != null && workingTag.getBooleanOr("listStripPrefix", false));
        draft.excludeSelf = toggleStates.getOrDefault("excludeSelf",
            workingTag == null || workingTag.getBooleanOr("excludeSelf", true));
        return draft;
    }

    private String captureValuePrimaryName(autismclient.util.macro.CaptureValueAction draft) {
        if (draft == null) return "";

        boolean listSource = draft.source == autismclient.util.macro.CaptureValueAction.Source.COMMAND_AUTOFILL
            || draft.source == autismclient.util.macro.CaptureValueAction.Source.TABLIST;
        if (!listSource) {
            java.util.Set<String> captured = autismclient.util.macro.MacroCapturePattern.declaredNames(draft.matchMode, draft.pattern);
            if (!captured.isEmpty()) return captured.iterator().next();
        }
        return autismclient.util.macro.MacroVariableContext.cleanRootName(draft.saveAs);
    }

    private String editorFieldText(String key, String fallback) {
        AutismChatField field = textFields.get(key);
        return field == null ? fallback : field.getText();
    }

    private void fillWaitInventorySelectedSlot() {
        if (MC.player == null) return;
        setWaitInventorySlot(MC.player.getInventory().getSelectedSlot());
    }

    private void setWaitInventorySlot(int slot) {
        AutismChatField field = textFields.get("slot");
        if (field != null) field.setText(String.valueOf(Math.max(0, slot)));
    }

    private void toggleWaitInventorySlotCapture() {
        toggleItemSlotCapture("wait_inventory_slot");
    }

    private void renderWaitChatPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int contentBottom, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;

        cy = renderFieldByKey(ctx, "pattern", x, cy, w, mx, my, delta);
        cy = renderCapturePatternBuilder(ctx, textFields.get("pattern"), x, cy, w, mx, my);
        cy = renderFieldByKey(ctx, "matchMode", x, cy, w, mx, my, delta);
        String matchMode = currentEnumValue("matchMode");
        if ("MATCH".equals(matchMode)) {
            cy = renderWaitChatFuzzySlider(ctx, x, cy, w, mx, my);
        } else {
            waitChatFuzzySliderDragging = false;
            clearWaitChatFuzzySliderBounds();
        }
        cy = renderFieldByKey(ctx, "saveAs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "timeoutMs", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiBefore", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitForGuiAfter", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "waitGuiName", x, cy, w, mx, my, delta);

        String pattern = textFields.containsKey("pattern") ? textFields.get("pattern").getText().trim() : "";
        int fuzzyPercent = getWaitChatFuzzyPercent();
        String hint = pattern.isEmpty()
                ? "Blank pattern = any chat line."
                : ("REGEX".equals(matchMode) ? "Named groups save values."
                   : "CAPTURE".equals(matchMode) ? "Use {name} to capture."
                   : "Fuzzy match: " + fuzzyPercent + "%.");
        cy = renderEditorHint(ctx, x, cy, w, hint);

        AutismChatField searchField = addFields.get("_wait_chat_search");
        if (searchField != null) {
            searchField.setX(x);
            searchField.setY(cy);
            searchField.setWidth(w);
            searchField.render(ctx, mx, my, delta);
            cy += 18;
        }

        List<autismclient.util.macro.MacroExecutor.RecentChatMessage> history = filterWaitChatHistory();
        UiText.draw(ctx, textRenderer, "Recent Messages (" + history.size() + ")",
                font, AutismColors.textSecondary(), x, cy + 2, false);
        UiText.draw(ctx, textRenderer,
                UiText.trimToWidth(textRenderer, formatEditorHint("Click a row to use that message."), Math.max(20, w - 122), font, -1),
                font, AutismColors.textDim(), x + 118, cy + 2, false);
        cy += 13;
        int availableListHeight = Math.max(24, contentBottom - cy - PAD);
        renderWaitChatHistoryList(ctx, x, cy, w, availableListHeight, mx, my, history);
    }

    private int renderCapturePatternBuilder(GuiGraphicsExtractor ctx, AutismChatField patternField,
                                            int x, int y, int w, int mx, int my) {
        AutismChatField nameField = addFields.get("_capture_name");
        if (nameField == null || patternField == null) return y;
        int buttonW = 48;
        nameField.setX(x);
        nameField.setY(y + 1);
        nameField.setWidth(Math.max(1, w - buttonW - 3));
        nameField.setHeight(14);
        nameField.render(ctx, mx, my, 0.0f);
        renderActionButton(ctx, x + w - buttonW, y + 1, buttonW, 14, "Capture", mx, my, () -> {
            String name = autismclient.util.macro.MacroVariableContext.cleanRootName(nameField.getText());
            if (name.isBlank()) return;
            patternField.insertText("{" + name + "}");
            enumIndices.put("matchMode", 1);
            workingTag.putString("matchMode", "CAPTURE");
            workingTag.putString("patternJson", "");
        });
        return y + 18;
    }

    private int renderWaitChatFuzzySlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int percent = getWaitChatFuzzyPercent();
        UiText.draw(ctx, textRenderer, "Match Strength", font, AutismColors.textSecondary(), x, y + 2, false);
        String label = percent + "%";
        UiText.draw(ctx, textRenderer, label, font, AutismColors.textDim(),
                x + w - uiWidth(font, label), y + 2, false);
        y += 13;

        int sliderW = w;
        int sliderH = 14;
        int trackH = 4;
        int trackY = y + (sliderH - trackH) / 2;
        waitChatFuzzySliderX = x;
        waitChatFuzzySliderY = y;
        waitChatFuzzySliderW = sliderW;
        waitChatFuzzySliderH = sliderH;

        UiRenderer.frame(ctx, UiBounds.of(x, y, sliderW, sliderH), 0xFF0D0D18, AutismColors.subPanelBorder());
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, sliderW - 2, trackH), 0xFF150C0C);

        int[] steps = {40, 50, 60, 70, 80, 90, 100};
        int knobIndex = Math.max(0, Math.min(6, (percent - 40) / 10));
        int knobCenterX = x + Math.round((sliderW - 1) * (knobIndex / 6.0f));
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), 0xFF345C86);

        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            int cx = x + Math.round((sliderW - 1) * (i / 6.0f));
            int tickColor = step <= percent ? 0xFF9DCEFF : 0xFF5A4040;
            UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
        }

        int knobW = 16;
        int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
        boolean hovered = isOverWaitChatFuzzySlider(mx, my);
        int knobFill = waitChatFuzzySliderDragging ? 0xFF274A6D : (hovered ? 0xFF2A4462 : 0xFF223B56);
        int knobBorder = waitChatFuzzySliderDragging ? 0xFFA5D5FF : 0xFF88BBFF;
        UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
        return y + sliderH + ROW_GAP;
    }

    private int getWaitChatFuzzyPercent() {
        AutismChatField field = textFields.get("fuzzyPercent");
        if (field == null) return 100;
        try {
            return autismclient.util.macro.WaitForChatAction.clampFuzzyPercent(Integer.parseInt(field.getText().trim()));
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    private void setWaitChatFuzzyPercent(int percent) {
        AutismChatField field = textFields.get("fuzzyPercent");
        if (field != null) field.setText(String.valueOf(autismclient.util.macro.WaitForChatAction.clampFuzzyPercent(percent)));
    }

    private void clearWaitChatFuzzySliderBounds() {
        waitChatFuzzySliderX = -1;
        waitChatFuzzySliderY = -1;
        waitChatFuzzySliderW = 0;
        waitChatFuzzySliderH = 0;
    }

    private boolean isOverWaitChatFuzzySlider(int mx, int my) {
        return waitChatFuzzySliderW > 0
                && waitChatFuzzySliderH > 0
                && mx >= waitChatFuzzySliderX
                && mx < waitChatFuzzySliderX + waitChatFuzzySliderW
                && my >= waitChatFuzzySliderY
                && my < waitChatFuzzySliderY + waitChatFuzzySliderH;
    }

    private void updateWaitChatFuzzyPercentFromMouse(int mouseX) {
        if (waitChatFuzzySliderW <= 1) return;
        float normalized = (mouseX - waitChatFuzzySliderX) / (float) (waitChatFuzzySliderW - 1);
        normalized = Math.max(0.0f, Math.min(1.0f, normalized));
        int stepIndex = Math.round(normalized * 6.0f);
        setWaitChatFuzzyPercent(40 + stepIndex * 10);
    }

    private int renderRotateSmoothnessSlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int smoothness = getRotateSmoothness();
        UiText.draw(ctx, textRenderer, "Smoothness", font, AutismColors.textSecondary(), x, y + 2, false);
        String label = smoothness + " / 10";
        UiText.draw(ctx, textRenderer, label, font, AutismColors.textDim(),
                x + w - uiWidth(font, label), y + 2, false);
        y += 13;

        int sliderW = w;
        int sliderH = 14;
        int trackH = 4;
        int trackY = y + (sliderH - trackH) / 2;
        rotateSmoothnessSliderX = x;
        rotateSmoothnessSliderY = y;
        rotateSmoothnessSliderW = sliderW;
        rotateSmoothnessSliderH = sliderH;

        UiRenderer.frame(ctx, UiBounds.of(x, y, sliderW, sliderH), 0xFF0D0D18, AutismColors.subPanelBorder());
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, sliderW - 2, trackH), 0xFF150C0C);

        int knobIndex = Math.max(0, Math.min(9, smoothness - 1));
        int knobCenterX = x + Math.round((sliderW - 1) * (knobIndex / 9.0f));
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), 0xFF345C86);

        for (int i = 0; i < 10; i++) {
            int cx = x + Math.round((sliderW - 1) * (i / 9.0f));
            int tickColor = i <= knobIndex ? 0xFF9DCEFF : 0xFF5A4040;
            UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
        }

        int knobW = 16;
        int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
        boolean hovered = isOverRotateSmoothnessSlider(mx, my);
        int knobFill = rotateSmoothnessSliderDragging ? 0xFF274A6D : (hovered ? 0xFF2A4462 : 0xFF223B56);
        int knobBorder = rotateSmoothnessSliderDragging ? 0xFFA5D5FF : 0xFF88BBFF;
        UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
        return y + sliderH + ROW_GAP;
    }

    private int getRotateSmoothness() {
        AutismChatField field = textFields.get("smoothness");
        if (field == null) return 9;
        try {
            return autismclient.util.macro.RotateAction.clampSmoothness(Integer.parseInt(field.getText().trim()));
        } catch (NumberFormatException ignored) {
            return 9;
        }
    }

    private void setRotateSmoothness(int smoothness) {
        AutismChatField field = textFields.get("smoothness");
        if (field != null) field.setText(String.valueOf(autismclient.util.macro.RotateAction.clampSmoothness(smoothness)));
    }

    private void clearRotateSmoothnessSliderBounds() {
        rotateSmoothnessSliderX = -1;
        rotateSmoothnessSliderY = -1;
        rotateSmoothnessSliderW = 0;
        rotateSmoothnessSliderH = 0;
    }

    private boolean isOverRotateSmoothnessSlider(int mx, int my) {
        return rotateSmoothnessSliderW > 0
                && rotateSmoothnessSliderH > 0
                && mx >= rotateSmoothnessSliderX
                && mx < rotateSmoothnessSliderX + rotateSmoothnessSliderW
                && my >= rotateSmoothnessSliderY
                && my < rotateSmoothnessSliderY + rotateSmoothnessSliderH;
    }

    private void updateRotateSmoothnessFromMouse(int mouseX) {
        if (rotateSmoothnessSliderW <= 1) return;
        float normalized = (mouseX - rotateSmoothnessSliderX) / (float) (rotateSmoothnessSliderW - 1);
        normalized = Math.max(0.0f, Math.min(1.0f, normalized));
        int stepIndex = Math.round(normalized * 9.0f);
        setRotateSmoothness(1 + stepIndex);
    }

    private int renderXCarrySafeDelaySlider(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int delay = getXCarrySafeDelayTicks();
        UiText.draw(ctx, textRenderer, "Safe Delay", font, AutismColors.textSecondary(), x, y + 2, false);
        String label = delay + "t";
        UiText.draw(ctx, textRenderer, label, font, AutismColors.textDim(),
                x + w - uiWidth(font, label), y + 2, false);
        y += 13;

        int sliderW = w;
        int sliderH = 14;
        int trackH = 4;
        int trackY = y + (sliderH - trackH) / 2;
        xcarrySafeDelaySliderX = x;
        xcarrySafeDelaySliderY = y;
        xcarrySafeDelaySliderW = sliderW;
        xcarrySafeDelaySliderH = sliderH;

        UiRenderer.frame(ctx, UiBounds.of(x, y, sliderW, sliderH), 0xFF0D0D18, AutismColors.subPanelBorder());
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, sliderW - 2, trackH), 0xFF150C0C);

        int knobIndex = Math.max(0, Math.min(10, delay));
        int knobCenterX = x + Math.round((sliderW - 1) * (knobIndex / 10.0f));
        UiRenderer.rect(ctx, UiBounds.of(x + 1, trackY, knobCenterX - x - 1, trackH), 0xFF6A2D2D);

        for (int i = 0; i <= 10; i++) {
            int cx = x + Math.round((sliderW - 1) * (i / 10.0f));
            int tickColor = i <= knobIndex ? 0xFFFF8A8A : 0xFF5A4040;
            UiRenderer.rect(ctx, UiBounds.of(cx, y + 2, 1, sliderH - 4), tickColor);
        }

        int knobW = 16;
        int knobX = Math.max(x, Math.min(x + sliderW - knobW, knobCenterX - knobW / 2));
        boolean hovered = isOverXCarrySafeDelaySlider(mx, my);
        int knobFill = xcarrySafeDelaySliderDragging ? 0xFF5C2424 : (hovered ? 0xFF4C2424 : 0xFF351B1B);
        int knobBorder = xcarrySafeDelaySliderDragging ? 0xFFFFB0B0 : 0xFFFF7777;
        UiRenderer.frame(ctx, UiBounds.of(knobX, y, knobW, sliderH), knobFill, knobBorder);
        return y + sliderH + ROW_GAP;
    }

    private int getXCarrySafeDelayTicks() {
        AutismChatField field = textFields.get("safeClickDelayTicks");
        if (field == null) return 1;
        try {
            return autismclient.util.macro.XCarryAction.clampSafeClickDelayTicks(Integer.parseInt(field.getText().trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void setXCarrySafeDelayTicks(int ticks) {
        AutismChatField field = textFields.get("safeClickDelayTicks");
        if (field != null) {
            field.setText(String.valueOf(autismclient.util.macro.XCarryAction.clampSafeClickDelayTicks(ticks)));
        }
    }

    private void clearXCarrySafeDelaySliderBounds() {
        xcarrySafeDelaySliderX = -1;
        xcarrySafeDelaySliderY = -1;
        xcarrySafeDelaySliderW = 0;
        xcarrySafeDelaySliderH = 0;
    }

    private boolean isOverXCarrySafeDelaySlider(int mx, int my) {
        return xcarrySafeDelaySliderW > 0
                && xcarrySafeDelaySliderH > 0
                && mx >= xcarrySafeDelaySliderX
                && mx < xcarrySafeDelaySliderX + xcarrySafeDelaySliderW
                && my >= xcarrySafeDelaySliderY
                && my < xcarrySafeDelaySliderY + xcarrySafeDelaySliderH;
    }

    private void updateXCarrySafeDelayFromMouse(int mouseX) {
        if (xcarrySafeDelaySliderW <= 1) return;
        float normalized = (mouseX - xcarrySafeDelaySliderX) / (float) (xcarrySafeDelaySliderW - 1);
        normalized = Math.max(0.0f, Math.min(1.0f, normalized));
        int stepIndex = Math.round(normalized * 10.0f);
        setXCarrySafeDelayTicks(stepIndex);
    }

    private void applyWaitChatHistoryEntry(autismclient.util.macro.MacroExecutor.RecentChatMessage entry) {
        AutismChatField field = textFields.get("pattern");
        if (field != null && entry != null) {
            Component patternComponent = entry.displayComponent() != null
                    ? entry.displayComponent().copy()
                    : Component.literal(formatWaitChatHistoryEntry(entry));
            setWaitChatPatternField(field, patternComponent);
            enumIndices.put("matchMode", 0);
            workingTag.putString("matchMode", "MATCH");
        }
    }

    private void setWaitChatPatternField(AutismChatField field, Component component) {
        if (field == null) return;
        Component safeComponent = component != null ? component.copy() : Component.empty();
        String visibleText = safeComponent.getString();
        workingTag.putString("pattern", visibleText);
        workingTag.putString("patternJson",
                autismclient.util.macro.MacroExecutor.serializeTextComponent(safeComponent));
        suppressWaitChatPatternSync = true;
        field.setText(visibleText);
        suppressWaitChatPatternSync = false;
    }

    private Component getWaitChatPatternComponent(String fallbackValue) {
        Component exact = autismclient.util.macro.MacroExecutor.deserializeTextComponent(
                workingTag != null ? workingTag.getStringOr("patternJson", "") : "");
        if (exact != null) return exact.copy();
        return Component.literal(fallbackValue == null ? "" : fallbackValue);
    }

    private Component rebuildWaitChatPatternComponent(Component previousComponent, String editedValue) {
        String safeValue = editedValue == null ? "" : editedValue;
        if (previousComponent == null) return Component.literal(safeValue);

        String previousValue = previousComponent.getString();
        if (previousValue.equals(safeValue)) return previousComponent.copy();
        if (safeValue.isEmpty()) return Component.empty();
        if (previousValue.isEmpty()) return Component.literal(safeValue);

        List<Style> previousStyles = flattenWaitChatStyles(previousComponent, previousValue.length());
        if (previousStyles.isEmpty()) return Component.literal(safeValue);

        int prefix = longestCommonPrefix(previousValue, safeValue);
        int suffix = longestCommonSuffix(previousValue, safeValue, prefix);
        int previousLength = previousValue.length();
        int nextLength = safeValue.length();

        MutableComponent rebuilt = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style segmentStyle = null;
        for (int i = 0; i < nextLength; i++) {
            Style style = styleForEditedWaitChatIndex(previousStyles, previousLength, nextLength, prefix, suffix, i);
            if (segmentStyle == null) {
                segmentStyle = style;
            } else if (!segmentStyle.equals(style)) {
                rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle));
                segment.setLength(0);
                segmentStyle = style;
            }
            segment.append(safeValue.charAt(i));
        }
        if (!segment.isEmpty()) {
            rebuilt.append(Component.literal(segment.toString()).setStyle(segmentStyle == null ? Style.EMPTY : segmentStyle));
        }
        return rebuilt;
    }

    private List<Style> flattenWaitChatStyles(Component text, int expectedLength) {
        if (text == null || expectedLength <= 0) return Collections.emptyList();
        List<Style> rawStyles = new ArrayList<>(expectedLength);
        text.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
                Style safeStyle = style == null ? Style.EMPTY : style;
                for (int i = 0; i < part.length(); i++) rawStyles.add(safeStyle);
            }
            return Optional.empty();
        }, Style.EMPTY);
        List<Style> styles = new ArrayList<>(rawStyles);
        if (styles.isEmpty()) {
            for (int i = 0; i < expectedLength; i++) styles.add(Style.EMPTY);
        } else if (styles.size() < expectedLength) {
            Style fill = styles.get(styles.size() - 1);
            while (styles.size() < expectedLength) styles.add(fill);
        } else if (styles.size() > expectedLength) {
            styles = new ArrayList<>(styles.subList(0, expectedLength));
        }
        return styles;
    }

    private int longestCommonPrefix(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int index = 0;
        while (index < max && left.charAt(index) == right.charAt(index)) index++;
        return index;
    }

    private int longestCommonSuffix(String previous, String next, int prefixLength) {
        int previousRemaining = previous.length() - prefixLength;
        int nextRemaining = next.length() - prefixLength;
        int max = Math.min(previousRemaining, nextRemaining);
        int suffix = 0;
        while (suffix < max
                && previous.charAt(previous.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
            suffix++;
        }
        return suffix;
    }

    private Style styleForEditedWaitChatIndex(List<Style> previousStyles, int previousLength, int nextLength,
                                              int prefixLength, int suffixLength, int nextIndex) {
        if (previousStyles.isEmpty()) return Style.EMPTY;
        if (nextIndex < prefixLength) {
            return previousStyles.get(Math.min(nextIndex, previousStyles.size() - 1));
        }

        int nextSuffixStart = nextLength - suffixLength;
        int previousSuffixStart = previousLength - suffixLength;
        if (suffixLength > 0 && nextIndex >= nextSuffixStart) {
            int mappedIndex = previousSuffixStart + (nextIndex - nextSuffixStart);
            return previousStyles.get(Math.max(0, Math.min(mappedIndex, previousStyles.size() - 1)));
        }

        int anchorIndex;
        if (prefixLength > 0) {
            anchorIndex = prefixLength - 1;
        } else if (suffixLength > 0) {
            anchorIndex = previousSuffixStart;
        } else {
            anchorIndex = 0;
        }
        return previousStyles.get(Math.max(0, Math.min(anchorIndex, previousStyles.size() - 1)));
    }

    private List<autismclient.util.macro.MacroExecutor.RecentChatMessage> filterWaitChatHistory() {
        List<autismclient.util.macro.MacroExecutor.RecentChatMessage> history =
                autismclient.util.macro.MacroExecutor.getRecentChatMessages();
        AutismChatField searchField = addFields.get("_wait_chat_search");
        String query = searchField != null ? searchField.getText().trim() : "";
        waitChatFilteredHistory.clear();
        String normalizedQuery = query.isEmpty() ? "" : normalizeWaitChatSearch(query);
        for (autismclient.util.macro.MacroExecutor.RecentChatMessage entry : history) {
            if (normalizedQuery.isEmpty()) {
                waitChatFilteredHistory.add(entry);
                continue;
            }
            String haystack = normalizeWaitChatSearch(formatWaitChatHistoryEntry(entry));
            if (haystack.contains(normalizedQuery)) waitChatFilteredHistory.add(entry);
        }
        return waitChatFilteredHistory;
    }

    private String normalizeWaitChatSearch(String text) {
        return autismclient.util.macro.MacroExecutor.normalizeChatText(text);
    }

    private String formatWaitChatHistoryEntry(autismclient.util.macro.MacroExecutor.RecentChatMessage entry) {
        if (entry == null) return "";
        if (entry.displayComponent() != null) {
            String rendered = entry.displayComponent().getString();
            if (rendered != null && !rendered.isBlank()) return rendered;
        }
        String sender = entry.sender() == null ? "" : entry.sender().trim();
        String message = entry.message() == null ? "" : entry.message().trim();
        if (!sender.isEmpty() && !message.isEmpty()) return sender + ": " + message;
        if (!message.isEmpty()) return (entry.source() == autismclient.util.macro.MacroExecutor.ChatSource.SERVER ? "[Server] " : "[Player] ") + message;
        return entry.displayText() == null ? "" : entry.displayText();
    }

    private void renderWaitChatHistoryList(GuiGraphicsExtractor ctx, int x, int y, int w, int listH, int mx, int my,
                                           List<autismclient.util.macro.MacroExecutor.RecentChatMessage> values) {
        renderRecentChatHistoryList(ctx, x, y, w, listH, mx, my, values,
            "wait_chat_recent", WAIT_CHAT_VISIBLE_ROWS, this::applyWaitChatHistoryEntry);
    }

    private void renderRecentChatHistoryList(GuiGraphicsExtractor ctx, int x, int y, int w, int listH, int mx, int my,
                                             List<autismclient.util.macro.MacroExecutor.RecentChatMessage> values,
                                             String viewportKey, int visibleRows,
                                             java.util.function.Consumer<autismclient.util.macro.MacroExecutor.RecentChatMessage> onSelect) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int desiredListH = values.isEmpty()
                ? 24
                : Math.min(Math.max(1, visibleRows), values.size()) * WAIT_CHAT_ROW_H;
        listH = Math.max(24, Math.min(listH, desiredListH));
        int itemW = w - SCROLLBAR_W - 1;

        DirectScrollViewport chatViewport = getOrCreateViewport(selectedScrollViewports, viewportKey,
            x, y, w, listH, WAIT_CHAT_ROW_H, SCROLLBAR_W);
        chatViewport.setContentHeight(values.size() * WAIT_CHAT_ROW_H);

        chatViewport.renderScrollbar(ctx, mx, my);

        if (values.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, itemW, 24, false, false);
            String firstLine = "capture_value_recent".equals(viewportKey)
                ? "No recent chat yet."
                : "No recent messages matched your search.";
            String secondLine = "capture_value_recent".equals(viewportKey)
                ? "Send or receive a message first."
                : "Send or receive chat first, then pick it here.";
            UiText.draw(ctx, textRenderer, firstLine,
                    font, AutismColors.textDim(), x + 3, y + 3, false);
            UiText.draw(ctx, textRenderer, secondLine,
                    font, AutismColors.textDim(), x + 3, y + 13, false);
            return;
        }

        chatViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = chatViewport.getFirstVisibleRow();
        for (int i = first; i < values.size() && i <= chatViewport.getLastVisibleRow(); i++) {
            int iy = chatViewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            int itemH = WAIT_CHAT_ROW_H;

            if (iy + itemH > y + listH) {
                itemH = Math.max(0, y + listH - iy);
            }
            if (itemH <= 0) continue;
            var value = values.get(i);
            boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + itemH;
            CompactSurfaces.row(ctx, x, iy, itemW, itemH, hovered, false);
            int border = value.source() == autismclient.util.macro.MacroExecutor.ChatSource.SERVER
                    ? (hovered ? 0xFFFFB15A : 0xFFCC7A22)
                    : (hovered ? 0xFF7CCEFF : 0xFF4E95C8);
            fillBorder(ctx, x, iy, itemW, itemH, border);

            List<FormattedCharSequence> wrapped = wrapWaitChatDisplayLines(value, Math.max(20, itemW - 6), 3);
            int textY = iy + 3;
            if (wrapped.isEmpty()) {
                UiText.draw(ctx, textRenderer, "(empty)", font, AutismColors.textDim(), x + 3, textY, false);
            } else {
                for (FormattedCharSequence line : wrapped) {
                    if (textY + 8 > iy + itemH) break;
                    ctx.text(textRenderer, line, x + 3, textY, 0xFFFFFFFF, false);
                    textY += 9;
                }
            }
            final var selected = value;
            hitRegions.add(new HitRegion(x, iy, itemW, itemH, () -> {
                if (onSelect != null) onSelect.accept(selected);
            }));
        }
        chatViewport.endRender(ctx);
    }

    private int waitChatHistoryListHeight() {
        int count = filterWaitChatHistory().size();
        if (count <= 0) return 24;
        return Math.min(WAIT_CHAT_VISIBLE_ROWS, count) * WAIT_CHAT_ROW_H;
    }

    private List<FormattedCharSequence> wrapWaitChatDisplayLines(autismclient.util.macro.MacroExecutor.RecentChatMessage entry, int maxWidth, int maxLines) {
        Component display = entry != null && entry.displayComponent() != null
                ? entry.displayComponent()
                : Component.literal(formatWaitChatHistoryEntry(entry));
        List<FormattedCharSequence> wrapped = textRenderer.split(display, Math.max(20, maxWidth));
        if (wrapped.size() <= maxLines) return wrapped;
        return new ArrayList<>(wrapped.subList(0, Math.max(0, maxLines)));
    }

    private List<String> wrapWaitChatLines(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxLines <= 0) return lines;
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (uiWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                if (lines.size() == maxLines - 1) {
                    lines.add(trimWaitChatLine(current + " " + joinWaitChatWords(words, wordIndex), maxWidth));
                    return lines;
                }
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (lines.size() == maxLines - 1) {
                    lines.add(trimWaitChatLine(joinWaitChatWords(words, wordIndex), maxWidth));
                    return lines;
                }
                lines.add(trimWaitChatLine(word, maxWidth));
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) lines.add(current.toString());
        return lines;
    }

    private String joinWaitChatWords(String[] words, int startIndex) {
        StringBuilder out = new StringBuilder();
        for (int i = startIndex; i < words.length; i++) {
            if (out.length() > 0) out.append(' ');
            out.append(words[i]);
        }
        return out.toString();
    }

    private String trimWaitChatLine(String text, int maxWidth) {
        String trimmed = text == null ? "" : text;
        while (!trimmed.isEmpty() && uiWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.length() < (text == null ? 0 : text.length()) ? trimmed + "..." : trimmed;
    }

    private void renderDelayPacketsPanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        int cy = bodyTop + PAD;
        int halfW = (w - 4) / 2;
        List<String> c2sTargets = getDelayPacketTargets(true);
        List<String> s2cTargets = getDelayPacketTargets(false);

        cy = renderFieldByKey(ctx, "mode", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "flushOnDisable", x, cy, w, mx, my, delta);

        String mode = currentEnumValue("mode");
        if ("ENABLE".equals(mode)) {
            renderOverlayButton(ctx, x, cy, halfW, 14, "Add C2S (" + c2sTargets.size() + ")", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> openDelayPacketSelector(true));
            renderOverlayButton(ctx, x + halfW + 4, cy, halfW, 14, "Add S2C (" + s2cTargets.size() + ")", CompactOverlayButton.Variant.PRIMARY, true, mx, my, () -> openDelayPacketSelector(false));
            cy += 18;

            renderActionButton(ctx, x, cy, halfW, 14, "Load Queue", mx, my, this::loadDelayPacketTargetsFromQueue);
            renderActionButton(ctx, x + halfW + 4, cy, halfW, 14, "Clear All", mx, my, this::clearAllDelayPacketTargets);
            cy += 18;

            renderOverlayButton(ctx, x, cy, halfW, 14, "Default Preset", CompactOverlayButton.Variant.PRIMARY, true, mx, my, this::applyDefaultDelayPacketPreset);
            renderOverlayButton(ctx, x + halfW + 4, cy, halfW, 14, "Module Preset", CompactOverlayButton.Variant.PRIMARY, true, mx, my, this::applyModuleDelayPacketPreset);
        }
    }

    private List<String> getDelayPacketTargets(boolean c2s) {
        return stringLists.computeIfAbsent(c2s ? "c2sPackets" : "s2cPackets", ignored -> new ArrayList<>());
    }

    private void openDelayPacketSelector(boolean c2s) {
        if (c2s) {
            packetSelectorOverlay.openToggleC2S(
                (packetClass, selected) -> setDelayPacketSelected(true, packetClass, selected),
                getSelectedDelayPacketClasses(true)
            );
        } else {
            packetSelectorOverlay.openToggleS2C(
                (packetClass, selected) -> setDelayPacketSelected(false, packetClass, selected),
                getSelectedDelayPacketClasses(false)
            );
        }
    }

    private List<Class<? extends net.minecraft.network.protocol.Packet<?>>> getSelectedDelayPacketClasses(boolean c2s) {
        List<Class<? extends net.minecraft.network.protocol.Packet<?>>> selected = new ArrayList<>();
        for (String target : getDelayPacketTargets(c2s)) {
            Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolveDelayPacketClass(target, c2s);
            if (packetClass != null && !selected.contains(packetClass)) {
                selected.add(packetClass);
            }
        }
        return selected;
    }

    private Class<? extends net.minecraft.network.protocol.Packet<?>> resolveDelayPacketClass(String target, boolean c2s) {
        if (target == null || target.isBlank()) return null;
        List<Class<? extends net.minecraft.network.protocol.Packet<?>>> pool = c2s
            ? new ArrayList<>(AutismPacketRegistry.getC2SPackets())
            : new ArrayList<>(AutismPacketRegistry.getS2CPackets());
        for (Class<? extends net.minecraft.network.protocol.Packet<?>> candidate : pool) {
            String registryName = AutismPacketRegistry.getName(candidate);
            if (packetNameMatches(target, registryName)) return candidate;
            if (packetNameMatches(target, AutismPacketNamer.getFriendlyName(candidate))) return candidate;
            if (packetNameMatches(target, candidate.getSimpleName())) return candidate;
        }
        return null;
    }

    private void setDelayPacketSelected(boolean c2s, Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass, boolean selected) {
        if (packetClass == null) return;
        List<String> targets = getDelayPacketTargets(c2s);
        if (selected) {
            if (!containsDelayPacketTarget(targets, packetClass, c2s)) {
                targets.add(AutismPacketNamer.getFriendlyName(packetClass));
            }
            return;
        }

        targets.removeIf(existing -> packetClass.equals(resolveDelayPacketClass(existing, c2s)));
    }

    private boolean containsDelayPacketTarget(List<String> targets, Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass, boolean c2s) {
        if (targets == null || packetClass == null) return false;
        for (String target : targets) {
            if (packetClass.equals(resolveDelayPacketClass(target, c2s))) {
                return true;
            }
        }
        return false;
    }

    private void loadDelayPacketTargetsFromQueue() {
        List<AutismSharedState.QueuedPacket> queue = AutismSharedState.get().getDelayedPackets();
        if (queue == null || queue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("Queue is empty");
            return;
        }

        int before = getDelayPacketTargets(true).size() + getDelayPacketTargets(false).size();
        for (AutismSharedState.QueuedPacket queuedPacket : queue) {
            if (queuedPacket == null || queuedPacket.packet == null) continue;
            @SuppressWarnings("unchecked")
            Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass =
                (Class<? extends net.minecraft.network.protocol.Packet<?>>) queuedPacket.packet.getClass();
            if (AutismPacketRegistry.getC2SPackets().contains(packetClass)) {
                setDelayPacketSelected(true, packetClass, true);
            } else if (AutismPacketRegistry.getS2CPackets().contains(packetClass)) {
                setDelayPacketSelected(false, packetClass, true);
            }
        }

        int after = getDelayPacketTargets(true).size() + getDelayPacketTargets(false).size();
        int added = Math.max(0, after - before);
        AutismClientMessaging.sendPrefixed(added == 0
            ? "Queue did not add any new packet filters"
            : "Added " + added + " packet filter" + (added == 1 ? "" : "s") + " from queue");
    }

    private void clearAllDelayPacketTargets() {
        getDelayPacketTargets(true).clear();
        getDelayPacketTargets(false).clear();
    }

    private void applyDefaultDelayPacketPreset() {
        autismclient.util.macro.DelayPacketsAction tmp = new autismclient.util.macro.DelayPacketsAction();
        tmp.applyDefaultPreset();
        stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
        stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
    }

    private void applyModuleDelayPacketPreset() {
        autismclient.util.macro.DelayPacketsAction tmp = new autismclient.util.macro.DelayPacketsAction();
        tmp.applyModulePreset();
        stringLists.put("c2sPackets", new ArrayList<>(tmp.c2sPacketNames));
        stringLists.put("s2cPackets", new ArrayList<>(tmp.s2cPacketNames));
    }

    private String buildDelayPacketDirectionSummary(boolean c2s) {
        String direction = c2s ? "C2S" : "S2C";
        List<String> targets = getDelayPacketTargets(c2s);
        if (targets.isEmpty()) {
            return direction + ": none";
        }

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < targets.size() && i < 3; i++) {
            String target = targets.get(i);
            Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolveDelayPacketClass(target, c2s);
            labels.add(packetClass != null ? AutismPacketNamer.getFriendlyName(packetClass) : target);
        }

        String summary = direction + ": " + targets.size();
        if (!labels.isEmpty()) {
            summary += " - " + String.join(", ", labels);
        }
        if (targets.size() > labels.size()) {
            summary += ", +" + (targets.size() - labels.size());
        }
        return summary;
    }

    private int renderDelayPacketPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, float delta, boolean c2s) {
        String key = c2s ? "c2sPackets" : "s2cPackets";
        String searchKey = c2s ? "_delay_packets_c2s_search" : "_delay_packets_s2c_search";
        String title = c2s ? "C2S Packets" : "S2C Packets";
        List<String> selected = stringLists.getOrDefault(key, Collections.emptyList());
        y = renderSimpleSelectedList(ctx, x, y, w, mx, my, key + "_selected", title, selected,
                selected::remove, value -> value, "(none selected)");

        AutismChatField search = addFields.get(searchKey);
        if (search != null) {
            search.setX(x);
            search.setY(y + 1);
            search.setWidth(w);
            search.render(ctx, mx, my, delta);
        }
        y += 18;

        String filter = search != null ? search.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<String> options = new ArrayList<>();
        for (String packetName : getPacketNames(c2s)) {
            if (filter.isEmpty() || packetName.toLowerCase(Locale.ROOT).contains(filter)) {
                options.add(packetName);
            }
        }
        renderSearchRegistryList(
            ctx,
            x,
            y,
            w,
            mx,
            my,
            key + "_search",
            options,
            5,
            value -> {
                if (selected.contains(value)) selected.remove(value);
                else selected.add(value);
            },
            selected::contains,
            value -> value
        );
        return y + 5 * CATALOG_ITEM_H + 4;
    }

    private void renderMinePanel(GuiGraphicsExtractor ctx, int x, int bodyTop, int w, int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int cy = bodyTop + PAD;
        cy = renderFieldByKey(ctx, "packetOrder", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "targetBlocks", x, cy, w, mx, my, delta);
        cy += 4;
        cy = renderFieldByKey(ctx, "stopInventoryFull", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "stopSlotsUsed", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "slotsUsedThreshold", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "stopMinedCount", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "minedCountTarget", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "stopAfterTime", x, cy, w, mx, my, delta);
        cy = renderFieldByKey(ctx, "timeoutSeconds", x, cy, w, mx, my, delta);
        renderEditorHint(ctx, x, cy, w, "Pick target blocks, then choose the stop rule you want.");
    }

    private void fillUseItemFromHeld() {
        if (MC.player == null) return;
        AutismChatField field = textFields.get("itemName");
        if (field == null) return;
        if (MC.player.getMainHandItem().isEmpty()) {
            field.setText("");
            editorItemFields.remove("itemName");
            return;
        }
        ItemTarget target = stripSlotFromTarget(ItemTarget.capture(MC.player.getMainHandItem(), -1));
        editorItemFields.put("itemName", target);
        field.setText(target.editorText());
    }

    private void fillSelectSlotFromHeld() {
        if (MC.player == null) return;
        AutismChatField field = textFields.get("itemName");
        if (field == null) return;
        if (MC.player.getMainHandItem().isEmpty()) {
            field.setText("");
            editorItemFields.remove("itemName");
            return;
        }
        ItemTarget target = ItemTarget.capture(MC.player.getMainHandItem(), MC.player.getInventory().getSelectedSlot());
        editorItemFields.put("itemName", target);
        field.setText(target.editorText());
    }

    private void clearSelectSlotItemName() {
        AutismChatField field = textFields.get("itemName");
        if (field != null) field.setText("");
        editorItemFields.remove("itemName");
    }

    private int getSelectSlotHotbarIndex() {
        AutismChatField field = textFields.get("slot");
        if (field == null) return 0;
        try {
            return Math.max(0, Math.min(8, Integer.parseInt(field.getText().trim())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void setSelectSlotHotbarIndex(int slot) {
        AutismChatField field = textFields.get("slot");
        if (field != null) field.setText(String.valueOf(Math.max(0, Math.min(8, slot))));
    }

    private String buildSelectSlotSummary() {
        int fallbackSlot = getSelectSlotHotbarIndex() + 1;
        AutismChatField itemField = textFields.get("itemName");
        String itemName = itemField != null ? itemField.getText().trim() : "";
        return itemName.isEmpty()
                ? "Uses hotbar slot " + fallbackSlot + "."
                : "Uses the named item, else slot " + fallbackSlot + ".";
    }

    private String currentUseItemTargetLabel() {
        AutismChatField field = textFields.get("itemName");
        String item = field != null ? field.getText().trim() : "";
        AutismChatField slotField = textFields.get("slot");
        String slot = slotField != null ? slotField.getText().trim() : "";
        if (!slot.isEmpty() && !item.isEmpty()) return "slot #" + slot + " / named item";
        if (!slot.isEmpty()) return "slot #" + slot;
        return item.isEmpty() ? "the held item" : "the named item";
    }

    private void toggleWaitCooldownHand() {
        toggleStates.put("checkMainHand", !toggleStates.getOrDefault("checkMainHand", true));
    }

    private String currentWaitCooldownHandLabel() {
        return toggleStates.getOrDefault("checkMainHand", true) ? "Hand: Main" : "Hand: Off";
    }

    private void fillWaitCooldownFromHeld() {
        if (MC.player == null) return;
        AutismChatField field = textFields.get("itemName");
        if (field == null) return;
        if (MC.player.getMainHandItem().isEmpty()) {
            field.setText("");
            editorItemFields.remove("itemName");
            return;
        }
        ItemTarget target = ItemTarget.capture(MC.player.getMainHandItem(), MC.player.getInventory().getSelectedSlot());
        editorItemFields.put("itemName", target);
        field.setText(target.editorText());
    }

    private void clearWaitCooldownItemName() {
        AutismChatField field = textFields.get("itemName");
        if (field != null) field.setText("");
        editorItemFields.remove("itemName");
    }

    private void fillRotateFromCurrentView() {
        if (!guardWorldCaptureAction()) return;
        if (MC.player == null) return;
        AutismChatField yawField = textFields.get("yaw");
        AutismChatField pitchField = textFields.get("pitch");
        if (yawField != null) yawField.setText(fmtDouble(MC.player.getYRot()));
        if (pitchField != null) pitchField.setText(fmtDouble(MC.player.getXRot()));
    }

    private void fillGoToFromPlayer() {
        if (!guardWorldCaptureAction()) return;
        if (MC.player == null) return;
        AutismChatField xField = textFields.get("pos_0");
        AutismChatField yField = textFields.get("pos_1");
        AutismChatField zField = textFields.get("pos_2");
        if (xField != null) xField.setText(fmtDouble(MC.player.getX()));
        if (yField != null) yField.setText(fmtDouble(MC.player.getY()));
        if (zField != null) zField.setText(fmtDouble(MC.player.getZ()));
    }

    private String buildWaitCooldownSummary() {
        AutismChatField itemField = textFields.get("itemName");
        String itemName = itemField != null ? itemField.getText().trim() : "";
        String handLabel = toggleStates.getOrDefault("checkMainHand", true) ? "main hand" : "off hand";
        if (itemName.isEmpty()) {
            return "Waits for the " + handLabel + " cooldown.";
        }
        return "Waits for the named item cooldown.";
    }

    private void swapSwapSlotEndpoints() {
        boolean fromUseName = toggleStates.getOrDefault("fromUseItemName", false);
        boolean toUseName = toggleStates.getOrDefault("toUseItemName", false);
        String fromItem = textFields.containsKey("fromItemName") ? textFields.get("fromItemName").getText() : "";
        String toItem = textFields.containsKey("toItemName") ? textFields.get("toItemName").getText() : "";
        String fromSlot = textFields.containsKey("fromSlot") ? textFields.get("fromSlot").getText() : "";
        String toSlot = textFields.containsKey("toSlot") ? textFields.get("toSlot").getText() : "";

        toggleStates.put("fromUseItemName", toUseName);
        toggleStates.put("toUseItemName", fromUseName);
        AutismChatField fromItemField = textFields.get("fromItemName");
        AutismChatField toItemField = textFields.get("toItemName");
        AutismChatField fromSlotField = textFields.get("fromSlot");
        AutismChatField toSlotField = textFields.get("toSlot");
        if (fromItemField != null) fromItemField.setText(toItem);
        if (toItemField != null) toItemField.setText(fromItem);
        if (fromSlotField != null) fromSlotField.setText(toSlot);
        if (toSlotField != null) toSlotField.setText(fromSlot);
    }

    private String buildSwapSlotsSummary() {
        return "Swaps the two targets. Name mode uses the first visible match.";
    }

    private String buildClickSummary() {
        String clickType = currentEnumValue("clickType");
        return switch (clickType) {
            case "LEFT" -> "Left click acts on your target.";
            default -> "Right click uses your target.";
        };
    }

    private String buildWaitSlotSummary(String waitMode) {
        return switch (waitMode) {
            case "IS_EMPTY"       -> "Waits until the slot (or any slot) is empty.";
            case "COUNT_AT_LEAST" -> "Waits until the slot/item hits the target count.";
            case "COUNT_BELOW"    -> "Waits until the slot/item drops below the count.";
            case "ANY_CHANGE"     -> "Waits for the first change in the specified slot.";
            default               -> "Waits until the slot or item is present. -1 scans all slots.";
        };
    }

    private int renderFieldByKey(GuiGraphicsExtractor ctx, String key, int x, int y, int w, int mx, int my, float delta) {
        if (isGuiWaitAfterKey(key)) return y;
        FieldDef field = getField(key);
        if (field == null || !isFieldVisible(field)) {
            if ("safeClickDelayTicks".equals(key)) clearXCarrySafeDelaySliderBounds();
            return y;
        }
        renderRow(ctx, field, x, y, w, mx, my, delta);
        return y + rowH(field) + ROW_GAP;
    }

    private FieldDef getField(String key) {
        if (schema == null) return null;
        for (FieldDef field : schema.fields()) {
            if (field.key().equals(key)) return field;
        }
        return null;
    }

    private <T> int renderSimpleSelectedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                             String listKey, String label, List<T> values,
                                             java.util.function.Consumer<T> removeAction,
                                             java.util.function.Function<T, String> formatter,
                                             String emptyLabel) {
        return renderSimpleSelectedList(ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, ignored -> null, values::clear, emptyLabel, false);
    }

    private <T> int renderSimpleSelectedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                             String listKey, String label, List<T> values,
                                             java.util.function.Consumer<T> removeAction,
                                             java.util.function.Function<T, String> formatter,
                                             java.util.function.Function<T, Component> richFormatter,
                                             String emptyLabel) {
        return renderSimpleSelectedList(ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, richFormatter, values::clear, emptyLabel, false);
    }

    private <T> int renderSimpleSelectedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                             String listKey, String label, List<T> values,
                                             java.util.function.Consumer<T> removeAction,
                                             java.util.function.Function<T, String> formatter,
                                             java.util.function.Function<T, Component> richFormatter,
                                             Runnable clearAction,
                                             String emptyLabel) {
        return renderSimpleSelectedList(ctx, x, y, w, mx, my, listKey, label, values, removeAction, formatter, richFormatter, clearAction, emptyLabel, false);
    }

    private <T> int renderSimpleSelectedList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my,
                                             String listKey, String label, List<T> values,
                                             java.util.function.Consumer<T> removeAction,
                                             java.util.function.Function<T, String> formatter,
                                             java.util.function.Function<T, Component> richFormatter,
                                             Runnable clearAction,
                                             String emptyLabel,
                                             boolean editable) {
        Identifier font = theme.fontFor(UiTone.BODY);
        int headerBtnW = 44;
        int editIdx = editable ? stringListEditIndex.getOrDefault(listKey, -1) : -1;
        if (editIdx >= values.size()) { editIdx = -1; stringListEditIndex.put(listKey, -1); }
        UiText.draw(ctx, textRenderer, label + " (" + values.size() + ")", font, AutismColors.textSecondary(), x, y + 2, false);
        boolean canClear = !values.isEmpty();
        int clearX = x + w - headerBtnW;
        renderOverlayButton(ctx, clearX, y, headerBtnW, 14, "Clear", CompactOverlayButton.Variant.DANGER, canClear, mx, my, () -> {
            clearAction.run();
            stringListEditIndex.put(listKey, -1);
            DirectScrollViewport vp = selectedScrollViewports.get(listKey);
            if (vp != null) vp.jumpTo(0);
        });
        y += 14;

        int listH = SEL_ITEM_H * 4;
        int itemW = w - SCROLLBAR_W - 1;

        DirectScrollViewport simpleViewport = getOrCreateViewport(selectedScrollViewports, listKey,
            x, y, w, listH, SEL_ITEM_H, SCROLLBAR_W);
        simpleViewport.setContentHeight(values.size() * SEL_ITEM_H);

        simpleViewport.renderScrollbar(ctx, mx, my);

        if (values.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
            CompactListRenderer.drawEmptyState(ctx, textRenderer, emptyLabel, x, y, itemW);
            return y + listH + 4;
        }

        simpleViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = simpleViewport.getFirstVisibleRow();
        for (int i = first; i < values.size() && i <= simpleViewport.getLastVisibleRow(); i++) {
            int iy = simpleViewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            T value = values.get(i);
            int removeW = 13;
            int rowW = itemW - removeW - 2;
            boolean selected = editable && i == editIdx;
            boolean hovered = mx >= x && mx < x + rowW && my >= iy && my < iy + 13;
            Component richLabel = richFormatter == null ? null : richFormatter.apply(value);
            if (richLabel != null) {
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        richLabel,
                        UiBounds.of(x, iy, rowW, 13),
                        hovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        true
                );
            } else {
                MacroTypedListControl.renderRow(
                        ctx,
                        textRenderer,
                        Component.literal(formatter.apply(value)),
                        UiBounds.of(x, iy, rowW, 13),
                        hovered,
                        selected,
                        CompactListRenderer.RowTone.NORMAL,
                        false
                );
            }

            if (editable) {
                final int fi = i;
                final String rawText = value instanceof String rawValue ? rawValue : formatter.apply(value);
                hitRegions.add(new HitRegion(x, iy, rowW, 13, () -> {
                    int curIdx = stringListEditIndex.getOrDefault(listKey, -1);
                    if (curIdx == fi) {
                        stringListEditIndex.put(listKey, -1);
                        stringListEditPendingText.put(listKey, "");
                    } else {
                        stringListEditIndex.put(listKey, fi);
                        stringListEditPendingText.put(listKey, rawText != null ? rawText : "");
                    }
                }));
            }

            {
                int removeX = x + rowW + 2;
                final int fi2 = i;
                renderIconDeleteButton(ctx, removeX, iy, removeW, mx, my, () -> {
                    removeAction.accept(values.get(fi2));
                    if (editable) {
                        int curSel = stringListEditIndex.getOrDefault(listKey, -1);
                        if (curSel == fi2) { stringListEditIndex.put(listKey, -1); stringListEditPendingText.put(listKey, ""); }
                        else if (curSel > fi2) stringListEditIndex.put(listKey, curSel - 1);
                    }
                    DirectScrollViewport vp = selectedScrollViewports.get(listKey);
                    if (vp != null) vp.scrollBy(-1);
                });
            }
        }
        simpleViewport.endRender(ctx);

        return y + listH + 4;
    }

    private <T> void renderSearchRegistryList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, String listKey,
                                              List<T> values, int visibleRows,
                                              java.util.function.Consumer<T> clickAction,
                                              java.util.function.Function<T, String> formatter) {
        renderSearchRegistryList(ctx, x, y, w, mx, my, listKey, values, visibleRows, clickAction, ignored -> false, formatter);
    }

    private <T> void renderSearchRegistryList(GuiGraphicsExtractor ctx, int x, int y, int w, int mx, int my, String listKey,
                                              List<T> values, int visibleRows,
                                              java.util.function.Consumer<T> clickAction,
                                              java.util.function.Predicate<T> selectedPredicate,
                                              java.util.function.Function<T, String> formatter) {
        int rowH = CATALOG_ITEM_H;
        int listH = visibleRows * rowH;
        int itemW = w - SCROLLBAR_W - 1;

        DirectScrollViewport regViewport = getOrCreateViewport(catalogScrollViewports, listKey,
            x, y, w, listH, rowH, SCROLLBAR_W);
        regViewport.setContentHeight(values.size() * rowH);

        regViewport.renderScrollbar(ctx, mx, my);

        if (values.isEmpty()) {
            CompactSurfaces.row(ctx, x, y, itemW, 12, false, false);
            CompactListRenderer.drawEmptyState(ctx, textRenderer, "No matches", x, y, itemW);
            return;
        }

        regViewport.beginRender(ctx, theme.borderSoft(), 0x36000000);
        int first = regViewport.getFirstVisibleRow();
        for (int i = first; i < values.size() && i <= regViewport.getLastVisibleRow(); i++) {
            int iy = regViewport.getRowScreenY(i);
            if (iy == Integer.MIN_VALUE) continue;
            T value = values.get(i);
            String display = formatter.apply(value);
            boolean selected = selectedPredicate != null && selectedPredicate.test(value);
            boolean hovered = mx >= x && mx < x + itemW && my >= iy && my < iy + rowH;
            net.minecraft.world.item.ItemStack icon = iconForRegistryListValue(listKey, value);
            MacroTypedListControl.renderRow(
                    ctx,
                    textRenderer,
                    Component.literal(display),
                    icon,
                    UiBounds.of(x, iy, itemW, rowH),
                    hovered,
                    selected,
                    selected ? CompactListRenderer.RowTone.READY : CompactListRenderer.RowTone.NORMAL,
                    false
            );
            final T clickedValue = value;
            hitRegions.add(new HitRegion(x, iy, itemW, rowH, () -> clickAction.accept(clickedValue)));
        }
        regViewport.endRender(ctx);
    }

    private net.minecraft.world.item.ItemStack iconForRegistryListValue(String listKey, Object value) {
        if (!(value instanceof String raw) || raw.isBlank()) return net.minecraft.world.item.ItemStack.EMPTY;
        String id = raw;
        if (raw.startsWith("~")) id = extractEntityTypeFromNearbyEntry(raw);
        if (id == null || id.isBlank()) return net.minecraft.world.item.ItemStack.EMPTY;
        Identifier parsed = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsed == null) return net.minecraft.world.item.ItemStack.EMPTY;

        String key = listKey == null ? "" : listKey.toLowerCase(Locale.ROOT);
        if (key.contains("item") || key.contains("store")) {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(net.minecraft.world.item.Items.AIR);
            return item == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY : item.getDefaultInstance();
        }
        if (key.contains("block") || key.contains("container")) {
            net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(net.minecraft.world.level.block.Blocks.AIR);
            return block == net.minecraft.world.level.block.Blocks.AIR || block.asItem() == net.minecraft.world.item.Items.AIR
                ? net.minecraft.world.item.ItemStack.EMPTY
                : block.asItem().getDefaultInstance();
        }
        if (key.contains("entity")) {
            return entityTypeIcon(parsed);
        }
        if (key.contains("sound")) {
            return soundListIcon(parsed);
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    private net.minecraft.world.item.ItemStack entityTypeIcon(Identifier entityId) {
        net.minecraft.world.entity.EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) return net.minecraft.world.item.ItemStack.EMPTY;
        for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
            var data = item.components().get(net.minecraft.core.component.DataComponents.ENTITY_DATA);
            if (data != null && data.type() == entityType) return item.getDefaultInstance();
        }
        if ("player".equals(entityId.getPath())) return net.minecraft.world.item.Items.PLAYER_HEAD.getDefaultInstance();
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    private net.minecraft.world.item.ItemStack soundListIcon(Identifier soundId) {
        String path = soundId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("fishing_bobber")) return net.minecraft.world.item.Items.FISHING_ROD.getDefaultInstance();
        if (path.contains("lava")) return net.minecraft.world.item.Items.LAVA_BUCKET.getDefaultInstance();
        if (path.contains("water") || path.contains("rain") || path.contains("bubble")) return net.minecraft.world.item.Items.WATER_BUCKET.getDefaultInstance();
        if (path.contains("firework")) return net.minecraft.world.item.Items.FIREWORK_ROCKET.getDefaultInstance();
        if (path.contains("experience_orb")) return net.minecraft.world.item.Items.EXPERIENCE_BOTTLE.getDefaultInstance();
        if (path.contains("lightning") || path.contains("thunder")) return net.minecraft.world.item.Items.LIGHTNING_ROD.weathering().unaffected().getDefaultInstance();
        if (path.contains("note_block")) return net.minecraft.world.item.Items.NOTE_BLOCK.getDefaultInstance();
        if (path.contains("ui_") || path.startsWith("ui.")) return net.minecraft.world.item.Items.COMPASS.getDefaultInstance();
        if (path.contains("ambient_cave")) return net.minecraft.world.item.Items.TORCH.getDefaultInstance();
        return net.minecraft.world.item.Items.NOTE_BLOCK.getDefaultInstance();
    }

    private void startWaitEntityCapture() {
        if (!guardWorldCaptureAction()) return;
        if (!(targetAction instanceof autismclient.util.macro.WaitForEntityAction)) return;
        Screen previousScreen = MC.gui.screen();
        enterCaptureMode();
        AutismSharedState state = AutismSharedState.get();
        state.setCaptureCancelCallback(() -> {
            if (previousScreen != null) MC.gui.setScreen(previousScreen);
            state.setEntityCaptureCallback(null);
            exitCaptureMode(false, false);
        });
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });
        state.setEntityCaptureSpecific(entitySpecificCaptureMode);
        state.setEntityCaptureCallback(payload -> MC.execute(() -> {
            List<String> selected = stringLists.get("entityIds");
            if (selected != null && payload != null && !payload.isBlank()) {
                if (entitySpecificCaptureMode) {
                    selected.removeIf(existing -> existing.startsWith(entitySpecificEntryPrefix(payload)));
                }
                if (!selected.contains(payload)) selected.add(payload);
            }
            if (previousScreen != null) MC.gui.setScreen(previousScreen);
            exitCaptureMode(false, false);
            AutismOverlayManager.get().bringToFront(this);
        }));
    }

    private static String trimMinecraftPrefix(String value) {
        return AutismRegistryLabels.stripNamespace(value);
    }

    private String formatEntityEntry(String entry) {
        if (entry == null || entry.isBlank()) return "(unknown)";
        if (entry.startsWith("~")) {
            String[] parts = entry.split("~", 4);
            String rawName = parts.length >= 4 ? parts[3] : "";
            String type = parts.length >= 3 ? AutismRegistryLabels.entity(parts[2]) : "?";
            String uuid = parts.length >= 2 ? parts[1] : "";
            String suffix = uuid.length() >= 4 ? uuid.substring(uuid.length() - 4) : uuid;
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty() || name.equalsIgnoreCase(type) || name.equalsIgnoreCase(trimMinecraftPrefix(type))) {
                return type + " #" + suffix;
            }
            return name + " (" + type + " #" + suffix + ")";
        }
        return AutismRegistryLabels.entity(entry);
    }

    private List<String> getNearbyEntityEntries() {
        boolean supportedAction = targetAction instanceof autismclient.util.macro.WaitForEntityAction
                || targetAction instanceof autismclient.util.macro.LookAtBlockAction
                || targetAction instanceof autismclient.util.macro.OpenContainerAction
                || targetAction instanceof autismclient.util.macro.InteractEntityAction;
        if (!supportedAction || MC.player == null || MC.level == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : MC.level.entitiesForRendering()) {
            if (entity == MC.player) continue;
            if (MC.player.distanceTo(entity) > WAIT_ENTITY_NEARBY_LIST_RADIUS) continue;
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            String uuid = entity.getStringUUID();
            String displayName = entity.getDisplayName().getString().replaceAll("§.", "").trim();
            result.add("~" + uuid + "~" + typeId + "~" + displayName);
        }
        result.sort(java.util.Comparator.comparingDouble(this::distanceForNearbyEntityEntry));
        return result;
    }

    private boolean canOpenContainerEntity(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return false;
        return entity instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer
                || entity instanceof net.minecraft.world.entity.vehicle.boat.ChestBoat
                || entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse;
    }

    private double distanceForNearbyEntityEntry(String entry) {
        if (MC.player == null || entry == null || !entry.startsWith("~")) return Double.MAX_VALUE;
        String[] parts = entry.split("~", 4);
        if (parts.length < 2 || MC.level == null) return Double.MAX_VALUE;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(parts[1]);
            net.minecraft.world.entity.Entity entity = null;
            for (net.minecraft.world.entity.Entity candidate : MC.level.entitiesForRendering()) {
                if (uuid.equals(candidate.getUUID())) {
                    entity = candidate;
                    break;
                }
            }
            return entity != null ? MC.player.distanceTo(entity) : Double.MAX_VALUE;
        } catch (Exception ignored) {
            return Double.MAX_VALUE;
        }
    }

    private String extractEntityTypeFromNearbyEntry(String entry) {
        if (entry == null || !entry.startsWith("~")) return "";
        String[] parts = entry.split("~", 4);
        return parts.length >= 3 ? parts[2] : "";
    }

    private String entitySpecificEntryPrefix(String entry) {
        if (entry == null || !entry.startsWith("~")) return "";
        String[] parts = entry.split("~", 4);
        return parts.length >= 2 ? "~" + parts[1] + "~" : "";
    }

    private String formatNearbyEntityEntry(String entry) {
        String label = formatEntityEntry(entry);
        double dist = distanceForNearbyEntityEntry(entry);
        if (dist == Double.MAX_VALUE) return label;
        return label + " (" + String.format(java.util.Locale.ROOT, "%.1fm", dist) + ")";
    }

    private List<BlockPos> getNearbyContainerPositions() {
        if (MC.player == null || MC.level == null) return Collections.emptyList();
        BlockPos center = MC.player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        int range = 10;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!isLikelyContainerCandidate(pos)) continue;
                    found.add(pos);
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(pos -> Vec3.atCenterOf(center).distanceToSqr(Vec3.atCenterOf(pos))));
        return found;
    }

    private boolean isLikelyContainerCandidate(BlockPos pos) {
        if (MC.level == null || pos == null) return false;
        net.minecraft.world.level.block.state.BlockState state = MC.level.getBlockState(pos);
        if (state == null || state.isAir()) return false;
        if (state.getMenuProvider(MC.level, pos) != null) return true;

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        if (path.contains("chest")
                || path.contains("barrel")
                || path.contains("shulker")
                || path.contains("hopper")
                || path.contains("dispenser")
                || path.contains("dropper")
                || path.contains("furnace")
                || path.contains("smoker")
                || path.contains("blast_furnace")
                || path.contains("brewing")
                || path.contains("anvil")
                || path.contains("beacon")
                || path.contains("crafting")
                || path.contains("loom")
                || path.contains("smithing")
                || path.contains("stonecutter")
                || path.contains("cartography")
                || path.contains("grindstone")
                || path.contains("enchant")
                || path.contains("ender_chest")
                || path.contains("spawner")) {
            return true;
        }

        return blockId.contains("container") || blockId.contains("gui") || blockId.contains("menu");
    }

    private String formatContainerEntry(BlockPos pos) {
        if (pos == null) return "(unknown)";
        String blockName = MC.level != null ? MC.level.getBlockState(pos).getBlock().getName().getString() : "Container";
        return blockName + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private void fillBlockPosField(String fieldKey, BlockPos pos) {
        if (fieldKey == null || pos == null) return;
        AutismChatField fx = textFields.get(fieldKey + "_0");
        AutismChatField fy = textFields.get(fieldKey + "_1");
        AutismChatField fz = textFields.get(fieldKey + "_2");
        if (fx != null) fx.setText(String.valueOf(pos.getX()));
        if (fy != null) fy.setText(String.valueOf(pos.getY()));
        if (fz != null) fz.setText(String.valueOf(pos.getZ()));
    }

    private boolean isCurrentBlockPosField(String fieldKey, BlockPos pos) {
        if (fieldKey == null || pos == null) return false;
        AutismChatField fx = textFields.get(fieldKey + "_0");
        AutismChatField fy = textFields.get(fieldKey + "_1");
        AutismChatField fz = textFields.get(fieldKey + "_2");
        if (fx == null || fy == null || fz == null) return false;
        try {
            int x = (int) Math.round(Double.parseDouble(fx.getText().trim()));
            int y = (int) Math.round(Double.parseDouble(fy.getText().trim()));
            int z = (int) Math.round(Double.parseDouble(fz.getText().trim()));
            return x == pos.getX() && y == pos.getY() && z == pos.getZ();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private List<String> getOrCreateWaitPacketTargets() {
        return stringLists.computeIfAbsent("packetNames", ignored -> new ArrayList<>());
    }

    private void openWaitPacketMatchSelector(boolean c2s) {
        java.util.function.Consumer<Class<? extends net.minecraft.network.protocol.Packet<?>>> add = packetClass -> {
            if (packetClass == null) return;
            if (waitPacketMatchRules == null) waitPacketMatchRules = new ArrayList<>();
            WaitPacketMatchAction.Rule rule = new WaitPacketMatchAction.Rule();
            rule.direction = c2s ? WaitPacketMatchAction.Direction.C2S : WaitPacketMatchAction.Direction.S2C;
            String name = AutismPacketRegistry.getName(packetClass);
            rule.packetName = name == null || name.isBlank() ? AutismPacketNamer.getFriendlyName(packetClass) : name;
            waitPacketMatchRules.add(rule);
            waitPacketMatchEditIndex = waitPacketMatchRules.size() - 1;
            syncWaitPacketMatchValueField();
        };
        if (c2s) packetSelectorOverlay.openC2S(add);
        else packetSelectorOverlay.openS2C(add);
    }

    private void openPacketNameFieldSelector(String fieldKey) {
        AutismChatField field = textFields.get(fieldKey);
        java.util.function.Consumer<Class<? extends net.minecraft.network.protocol.Packet<?>>> set = packetClass -> {
            if (packetClass == null || field == null) return;
            String name = AutismPacketRegistry.getName(packetClass);
            field.setText(name == null || name.isBlank() ? AutismPacketNamer.getFriendlyName(packetClass) : name);
        };
        String direction = currentEnumValue("direction");
        if ("S2C".equals(direction)) packetSelectorOverlay.openS2C(set);
        else if ("ANY".equals(direction)) packetSelectorOverlay.open(set);
        else packetSelectorOverlay.openC2S(set);
    }

    private void syncWaitPacketMatchEditedRule() {
        if (waitPacketMatchRules == null || waitPacketMatchEditIndex < 0 || waitPacketMatchEditIndex >= waitPacketMatchRules.size()) return;
        AutismChatField valueField = textFields.get("_wpm_value");
        if (valueField != null) waitPacketMatchRules.get(waitPacketMatchEditIndex).value = valueField.getText();
    }

    private void syncWaitPacketMatchValueField() {
        AutismChatField valueField = textFields.get("_wpm_value");
        if (valueField == null) return;
        if (waitPacketMatchRules == null || waitPacketMatchEditIndex < 0 || waitPacketMatchEditIndex >= waitPacketMatchRules.size()) {
            valueField.setText("");
            return;
        }
        valueField.setText(waitPacketMatchRules.get(waitPacketMatchEditIndex).value == null ? "" : waitPacketMatchRules.get(waitPacketMatchEditIndex).value);
    }

    private String waitPacketMatchFieldLabel(WaitPacketMatchAction.Rule rule) {
        return rule == null || rule.fieldName == null || rule.fieldName.isBlank() ? "Packet Only" : rule.fieldName;
    }

    private Component formatWaitPacketMatchRule(WaitPacketMatchAction.Rule rule) {
        if (rule == null) return Component.literal("(empty)");
        String label = rule.direction + " " + (rule.packetName == null || rule.packetName.isBlank() ? "Any Packet" : AutismPacketNamer.getFriendlyName(rule.packetName));
        if (rule.fieldName != null && !rule.fieldName.isBlank()) label += " / " + rule.fieldName;
        return Component.literal(label);
    }

    private void cycleWaitPacketMatchField(WaitPacketMatchAction.Rule rule) {
        if (rule == null) return;
        Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolveWaitPacketMatchClass(rule);
        List<String> fields = new ArrayList<>(WaitPacketMatchAction.packetFieldNames(packetClass));
        fields.add(0, "");
        int idx = fields.indexOf(rule.fieldName == null ? "" : rule.fieldName);
        rule.fieldName = fields.get((idx + 1 + fields.size()) % fields.size());
        rule.operator = rule.fieldName.isBlank() ? WaitPacketMatchAction.Operator.EXISTS : rule.operator;
        List<String> options = WaitPacketMatchAction.valueOptions(packetClass, rule.fieldName);
        if (!options.isEmpty() && (rule.value == null || rule.value.isBlank() || !options.contains(rule.value))) rule.value = options.get(0);
        syncWaitPacketMatchValueField();
    }

    private Class<? extends net.minecraft.network.protocol.Packet<?>> resolveWaitPacketMatchClass(WaitPacketMatchAction.Rule rule) {
        if (rule == null) return null;
        String direction = rule.direction == WaitPacketMatchAction.Direction.ANY ? "" : rule.direction.name();
        return resolvePacketClassForTarget(direction, rule.packetName == null ? "" : rule.packetName);
    }

    private static <E extends Enum<E>> E nextEnum(E current, E[] values) {
        if (values == null || values.length == 0) return current;
        int idx = current == null ? -1 : current.ordinal();
        return values[(idx + 1 + values.length) % values.length];
    }

    private List<String> sanitizeWaitPacketTargets(List<String> rawTargets) {
        List<String> sanitized = new ArrayList<>();
        for (String rawTarget : rawTargets == null ? Collections.<String>emptyList() : rawTargets) {
            String normalized = normalizeWaitPacketTarget(rawTarget);
            if (!normalized.isEmpty() && !sanitized.contains(normalized)) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private String normalizeWaitPacketTarget(String target) {
        String normalized = WaitForPacketAction.normalizeTarget(target);
        if (normalized.isEmpty()) return "";
        if (!WaitForPacketAction.getDirection(normalized).isEmpty()) return normalized;

        Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolvePacketClassForTarget("", normalized);
        if (packetClass == null) return normalized;
        if (AutismPacketRegistry.getC2SPackets().contains(packetClass)) return buildWaitPacketTarget("C2S", packetClass);
        if (AutismPacketRegistry.getS2CPackets().contains(packetClass)) return buildWaitPacketTarget("S2C", packetClass);
        return normalized;
    }

    private List<String> getWaitPacketTargets(String direction) {
        List<String> filtered = catalogFilteredValues.computeIfAbsent(
                "wait_packet_targets_" + direction.toUpperCase(Locale.ROOT),
                ignored -> new ArrayList<>()
        );
        filtered.clear();
        for (String target : getOrCreateWaitPacketTargets()) {
            String normalized = normalizeWaitPacketTarget(target);
            if (!normalized.isEmpty() && direction.equalsIgnoreCase(WaitForPacketAction.getDirection(normalized))) {
                filtered.add(normalized);
            }
        }
        return filtered;
    }

    private String formatWaitPacketTarget(String target) {
        return WaitForPacketAction.getDisplayLabel(target);
    }

    private void clearWaitPacketTargets(String direction) {
        getOrCreateWaitPacketTargets().removeIf(target ->
            direction.equalsIgnoreCase(WaitForPacketAction.getDirection(normalizeWaitPacketTarget(target))));
        DirectScrollViewport vpC2s = selectedScrollViewports.get("wait_packet_c2s");
        if (vpC2s != null) vpC2s.jumpTo(0);
        DirectScrollViewport vpS2c = selectedScrollViewports.get("wait_packet_s2c");
        if (vpS2c != null) vpS2c.jumpTo(0);
    }

    private void removeWaitPacketTarget(String target) {
        String normalized = normalizeWaitPacketTarget(target);
        if (normalized.isEmpty()) return;
        getOrCreateWaitPacketTargets().removeIf(existing -> normalized.equals(normalizeWaitPacketTarget(existing)));
    }

    private void openWaitPacketSelector(boolean c2s) {
        if (c2s) {
            packetSelectorOverlay.openToggleC2S(
                (packetClass, selected) -> setWaitPacketTargetSelected("C2S", packetClass, selected),
                getSelectedWaitPacketClasses("C2S")
            );
        } else {
            packetSelectorOverlay.openToggleS2C(
                (packetClass, selected) -> setWaitPacketTargetSelected("S2C", packetClass, selected),
                getSelectedWaitPacketClasses("S2C")
            );
        }
    }

    private List<Class<? extends net.minecraft.network.protocol.Packet<?>>> getSelectedWaitPacketClasses(String direction) {
        List<Class<? extends net.minecraft.network.protocol.Packet<?>>> selected = new ArrayList<>();
        for (String target : getWaitPacketTargets(direction)) {
            Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass = resolvePacketClassForTarget(direction, target);
            if (packetClass != null && !selected.contains(packetClass)) {
                selected.add(packetClass);
            }
        }
        return selected;
    }

    private void setWaitPacketTargetSelected(String direction, Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass, boolean selected) {
        String target = buildWaitPacketTarget(direction, packetClass);
        if (target.isEmpty()) return;

        List<String> targets = getOrCreateWaitPacketTargets();
        if (selected) {
            if (!targets.stream().map(this::normalizeWaitPacketTarget).anyMatch(target::equals)) {
                targets.add(target);
            }
            return;
        }

        targets.removeIf(existing -> target.equals(normalizeWaitPacketTarget(existing)));
    }

    private void loadWaitPacketTargetsFromQueue() {
        List<AutismSharedState.QueuedPacket> queue = AutismSharedState.get().getDelayedPackets();
        if (queue == null || queue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("Queue is empty");
            return;
        }

        List<String> targets = getOrCreateWaitPacketTargets();
        List<String> merged = sanitizeWaitPacketTargets(targets);
        int before = merged.size();
        for (AutismSharedState.QueuedPacket queuedPacket : queue) {
            String target = buildWaitPacketTarget(queuedPacket);
            if (!target.isEmpty() && !merged.contains(target)) {
                merged.add(target);
            }
        }
        targets.clear();
        targets.addAll(merged);
        int added = Math.max(0, merged.size() - before);
        AutismClientMessaging.sendPrefixed(added == 0
            ? "Queue did not add any new packet targets"
            : "Added " + added + " packet target" + (added == 1 ? "" : "s") + " from queue");
    }

    private String buildWaitPacketTarget(AutismSharedState.QueuedPacket queuedPacket) {
        if (queuedPacket == null || queuedPacket.packet == null) return "";
        @SuppressWarnings("unchecked")
        Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass =
            (Class<? extends net.minecraft.network.protocol.Packet<?>>) queuedPacket.packet.getClass();
        if (AutismPacketRegistry.getC2SPackets().contains(packetClass)) return buildWaitPacketTarget("C2S", packetClass);
        if (AutismPacketRegistry.getS2CPackets().contains(packetClass)) return buildWaitPacketTarget("S2C", packetClass);
        return "";
    }

    private String buildWaitPacketTarget(String direction, Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass) {
        if (packetClass == null) return "";
        String name = AutismPacketRegistry.getName(packetClass);
        if (name == null || name.isBlank()) {
            name = AutismPacketNamer.getFriendlyName(packetClass);
        }
        return WaitForPacketAction.withDirection(direction, name);
    }

    private Class<? extends net.minecraft.network.protocol.Packet<?>> resolvePacketClassForTarget(String direction, String target) {
        String packetName = WaitForPacketAction.getPacketName(target);
        if (packetName.isBlank()) return null;

        Class<? extends net.minecraft.network.protocol.Packet<?>> direct = AutismPacketRegistry.getPacket(packetName);
        if (direct != null) {
            if (direction.isBlank()) return direct;
            if ("C2S".equalsIgnoreCase(direction) && AutismPacketRegistry.getC2SPackets().contains(direct)) return direct;
            if ("S2C".equalsIgnoreCase(direction) && AutismPacketRegistry.getS2CPackets().contains(direct)) return direct;
        }

        List<Class<? extends net.minecraft.network.protocol.Packet<?>>> pool = new ArrayList<>();
        if ("C2S".equalsIgnoreCase(direction)) {
            pool.addAll(AutismPacketRegistry.getC2SPackets());
        } else if ("S2C".equalsIgnoreCase(direction)) {
            pool.addAll(AutismPacketRegistry.getS2CPackets());
        } else {
            pool.addAll(AutismPacketRegistry.getC2SPackets());
            pool.addAll(AutismPacketRegistry.getS2CPackets());
        }

        for (Class<? extends net.minecraft.network.protocol.Packet<?>> candidate : pool) {
            String registryName = AutismPacketRegistry.getName(candidate);
            if (packetNameMatches(packetName, registryName)) return candidate;
            if (packetNameMatches(packetName, AutismPacketNamer.getFriendlyName(candidate))) return candidate;
            if (packetNameMatches(packetName, candidate.getSimpleName())) return candidate;
        }
        return null;
    }

    private boolean packetNameMatches(String expected, String candidate) {
        if (expected == null || expected.isBlank() || candidate == null || candidate.isBlank()) return false;
        String normalizedExpected = expected.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalizedExpected.isEmpty() || normalizedCandidate.isEmpty()) return false;
        if (normalizedExpected.equals(normalizedCandidate)) return true;
        if (normalizedCandidate.endsWith("packet")) {
            String stripped = normalizedCandidate.substring(0, normalizedCandidate.length() - "packet".length());
            if (normalizedExpected.equals(stripped)) return true;
        }
        return normalizedExpected.endsWith(normalizedCandidate) || normalizedCandidate.endsWith(normalizedExpected);
    }

    private List<String> getPacketNames(boolean c2s) {
        List<String> names = new ArrayList<>();
        for (Class<? extends net.minecraft.network.protocol.Packet<?>> packetClass : (c2s ? AutismPacketRegistry.getC2SPackets() : AutismPacketRegistry.getS2CPackets())) {
            String name = AutismPacketRegistry.getName(packetClass);
            if (name != null && !name.isBlank()) names.add(name);
        }
        Collections.sort(names);
        return names;
    }

    private int renderBlockPosWithoutCapture(GuiGraphicsExtractor ctx, FieldDef field, int x, int y, int w,
                                             int mx, int my, float delta) {
        Identifier font = theme.fontFor(UiTone.BODY);
        drawLabel(ctx, field.label(), x, y, w, font);
        y += 13;
        int bw = (w - 4) / 3;
        for (int i = 0; i < 3; i++) {
            AutismChatField f = textFields.get(field.key() + "_" + i);
            if (f == null) continue;
            int fx = x + i * (bw + 2);
            f.setX(fx);
            f.setY(y + 1);
            f.setWidth(bw);
            f.render(ctx, mx, my, delta);
        }
        return y + ROW_H + ROW_GAP;
    }

    private static List<String> getAllSoundIds() {
        if (ALL_SOUND_IDS == null) {
            ALL_SOUND_IDS = new ArrayList<>();
            for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
                Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent);
                if (id != null) ALL_SOUND_IDS.add(id.toString());
            }
            Collections.sort(ALL_SOUND_IDS);
        }
        return ALL_SOUND_IDS;
    }

    private static List<String> getAllEntityIds() {
        if (ALL_ENTITY_IDS == null) {
            ALL_ENTITY_IDS = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                ALL_ENTITY_IDS.add(id.toString());
            }
            Collections.sort(ALL_ENTITY_IDS);
        }
        return ALL_ENTITY_IDS;
    }

    private static List<String> getAllItemIds() {
        if (ALL_ITEM_IDS == null) {
            ALL_ITEM_IDS = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                ALL_ITEM_IDS.add(id.toString());
            }
            Collections.sort(ALL_ITEM_IDS);
        }
        return ALL_ITEM_IDS;
    }

    private int rowH(FieldDef field) {
        if (field != null && "safeClickDelayTicks".equals(field.key())) {
            return 29;
        }
        if (isWaitChatPatternField(field)) {
            return 13 + WAIT_CHAT_PATTERN_H;
        }
        return switch (field.type()) {
            case MACRO_SELECT -> 17 + MACRO_SELECT_VISIBLE_ROWS * SEL_ITEM_H;
            case BLOCK_POS    -> 13 + ROW_H + ROW_GAP;
            case STRING_LIST  -> {
                if (isRacePickerList(field)) {
                    yield 13 + 18 + SEL_LIST_MAX_VIS * 18 + 2;
                }
                if (isMultiAccountList(field)) {
                    yield 13 + 18 + SEL_LIST_MAX_VIS * 18 + 2;
                }
                if (field.captureMode() == CaptureMode.BLOCK_CATALOG) {

                    yield 13 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 5 + 14 + 13 + CATALOG_LIST_H;
                }

                int extraEditorRow = isXCarryListKey(field.key())
                        && stringListEditIndex.getOrDefault(field.key(), -1) >= 0 ? 16 : 0;
                yield 13 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 16 + extraEditorRow;
            }
            default           -> ROW_H;
        };
    }

    private int computeContentH() {
        if (itemAction != null) {
            int h = PAD + 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 6 + 20;
            h += 16;
            if (isEditingNameOnlyItemTargetingAction()) h += ROW_H + ROW_GAP;
            h += (ROW_H + ROW_GAP);
            if (toggleStates.getOrDefault("item_waitForGuiBefore", false)
                    || toggleStates.getOrDefault("item_waitForGuiAfter", false))
                h += ROW_H + ROW_GAP;
            h += (ROW_H + ROW_GAP);
            h += (ROW_H + ROW_GAP);
            if (toggleStates.getOrDefault("item_useClickDelay", false))
                h += ROW_H + ROW_GAP;
            return h;
        }
        if (payloadAction != null) {
            int h = PAD;
            h += 13;
            h += 24;
            h += 18;
            h += 18;
            h += ROW_H + ROW_GAP;
            h += 13;
            h += (payloadTabIndex == 0 ? PAYLOAD_SCRIPT_H + 17 : PAYLOAD_VIEW_H + 14) + 4;
            h += 14;
            h += 20;
            h += EDITOR_HINT_ROW_H * 2;
            if (isPayloadConfigPhase()) h += EDITOR_HINT_ROW_H * 2;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.SEND_PACKET) {
            int h = PAD + 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 6;
            h += ROW_H + ROW_GAP;
            h += ROW_H + ROW_GAP;
            if (hasGuiWaitEnabled()) h += ROW_H + ROW_GAP;
            h += 20 + 20 + 6 + 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_SOUND) {
            int h = PAD;
            h += isFieldVisible(getField("waitForGuiBefore")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiAfter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitGuiName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("checkDistance")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("maxDistance")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            h += 14 + (4 * SEL_ITEM_H) + 4;
            h += 18;
            h += 6 * CATALOG_ITEM_H;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_ENTITY) {
            int h = PAD;
            h += isFieldVisible(getField("checkMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("radius")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("centerOnPlayer")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("pos")) ? rowH(getField("pos")) + ROW_GAP : 0;
            h += isFieldVisible(getField("mustBeLookingAt")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            h += 14 + (4 * SEL_ITEM_H) + 4;
            h += 18;
            h += 4 * CATALOG_ITEM_H + 10;
            h += 13 + 3 * CATALOG_ITEM_H;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.LOOK_AT_BLOCK) {
            int h = PAD;
            h += isFieldVisible(getField("targetMode")) ? ROW_H + ROW_GAP : 0;
            String mode = currentEnumValue("targetMode");
            if ("BLOCK".equals(mode)) {
                h += isFieldVisible(getField("searchRadius")) ? ROW_H + ROW_GAP : 0;
                h += rowH(getField("blockIds")) + ROW_GAP;
            } else if ("ENTITY".equals(mode)) {
                h += isFieldVisible(getField("searchRadius")) ? ROW_H + ROW_GAP : 0;
                h += 14 + (4 * SEL_ITEM_H) + 4;
                h += 18;
                h += 4 * CATALOG_ITEM_H + 14;
                h += 13 + (3 * CATALOG_ITEM_H) + 4;
            } else {
                h += rowH(getField("blockPos")) + ROW_GAP;
            }
            h += isFieldVisible(getField("smooth")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("smoothness")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForCompletion")) ? ROW_H + ROW_GAP : 0;
            h += 14;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.ROTATE) {
            int h = PAD;
            h += isFieldVisible(getField("yaw")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("pitch")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += isFieldVisible(getField("smooth")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("smoothness")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForCompletion")) ? ROW_H + ROW_GAP : 0;
            h += 14;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.GO_TO) {
            int h = PAD;
            h += rowH(getField("pos")) + ROW_GAP;
            h += 18;
            h += isFieldVisible(getField("waitForArrival")) ? ROW_H + ROW_GAP : 0;
            h += 14;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.USE_ITEM) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("itemName")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += isFieldVisible(getField("useMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForFinish")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("sneak")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("holdTicks")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("useCount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGui")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiName")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.INVENTORY_AUDIT) {
            int h = PAD;
            h += isFieldVisible(getField("mode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("targetItems")) ? 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 4 + 18 : 0;
            h += isFieldVisible(getField("openMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("openCommand")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("containerPos")) ? rowH(getField("containerPos")) + ROW_GAP : 0;
            if ("CONTAINER".equals(currentEnumValue("openMode"))) {
                h += 13 + CONTAINER_LIST_VISIBLE_ROWS * CATALOG_ITEM_H + 17;
            }
            h += isFieldVisible(getField("dupeVector")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("iterations")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("maxTransferAttempts")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("transferRetryDelayMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("spamCount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("spamDelayMs")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.SWAP_SLOTS) {
            int h = PAD + 16;
            h += isFieldVisible(getField("fromUseItemName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("fromItemName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("fromSlot")) ? ROW_H + ROW_GAP : 0;
            h += 8 + 16;
            h += isFieldVisible(getField("toUseItemName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("toItemName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("toSlot")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.CLICK) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("clickType")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("clickCount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiBefore")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiName")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.DISCONNECT) {
            int h = PAD;
            h += isFieldVisible(getField("mode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("delayMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("lagMethod")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("kickMethod")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("packetCount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("useNextAction")) ? ROW_H + ROW_GAP : 0;

            h += isFieldVisible(getField("trigger")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("tolerance")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("bufferMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("timeoutSec")) ? ROW_H + ROW_GAP : 0;
            h += EDITOR_HINT_ROW_H;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_GUI) {
            int h = PAD;
            h += isFieldVisible(getField("waitMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiType")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("matchMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiTitle")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("timeoutMs")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.SELECT_SLOT) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("itemName")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += 12 + 18;
            h += isFieldVisible(getField("strategy")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("outputVariable")) ? ROW_H + ROW_GAP : 0;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_COOLDOWN) {
            int h = PAD;
            h += isFieldVisible(getField("itemName")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += 18;
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_CHAT) {
            int h = PAD;
            h += isFieldVisible(getField("pattern")) ? rowH(getField("pattern")) + ROW_GAP : 0;
            h += 18;
            h += isFieldVisible(getField("matchMode")) ? ROW_H + ROW_GAP : 0;
            h += "MATCH".equals(currentEnumValue("matchMode")) ? 29 : 0;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("timeoutMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiBefore")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiAfter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitGuiName")) ? ROW_H + ROW_GAP : 0;
            h += 18;
            h += 18;
            h += 13 + waitChatHistoryListHeight();
            h += 16;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.CAPTURE_VALUE) {
            int h = PAD;
            h += isFieldVisible(getField("source")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("autofillCommand")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("autofillTimeoutMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("autofillCacheList")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listSelection")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listPickPosition")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listFilter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listFilterText")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listExcludeText")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("listStripPrefix")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("excludeSelf")) ? ROW_H + ROW_GAP : 0;
            if ("RECENT_CHAT".equals(currentEnumValue("source"))) {
                h += 13 + captureChatHistoryListHeight(
                    autismclient.util.macro.MacroExecutor.getRecentChatMessages().size()) + ROW_GAP;
            } else if ("SCOREBOARD".equals(currentEnumValue("source"))) {
                int lineCount = autismclient.util.macro.CaptureValueAction.scoreboardLines(MC).size();
                h += 13 + (lineCount <= 0 ? 24
                    : Math.min(CAPTURE_SCOREBOARD_VISIBLE_ROWS, lineCount) * CAPTURE_SCOREBOARD_ROW_H) + ROW_GAP;
            } else if ("TABLIST".equals(currentEnumValue("source"))) {
                int matchCount = captureTablistMatchCount();
                h += 13 + (matchCount <= 0 ? 24
                    : Math.min(CAPTURE_TABLIST_VISIBLE_ROWS, matchCount) * CAPTURE_TABLIST_ROW_H) + ROW_GAP;
            }
            h += isFieldVisible(getField("exampleText")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("selectedText")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("itemFilter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("slot")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("itemText")) ? ROW_H + ROW_GAP : 0;
            h += 60;
            h += isFieldVisible(getField("numberMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("numberModifier")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("numberModifierAmount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForTrigger")) ? ROW_H + ROW_GAP : 0;
            h += EDITOR_HINT_ROW_H * 2;
            return h;
        }
        if (targetAction != null && (targetAction.getType() == MacroActionType.OPEN_CONTAINER
                                  || targetAction.getType() == MacroActionType.INTERACT_ENTITY)) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("targetMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("pos")) ? rowH(getField("pos")) + ROW_GAP : 0;
            h += isFieldVisible(getField("entityTargets")) ? rowH(getField("entityTargets")) + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiBefore")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiAfter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiName")) ? ROW_H + ROW_GAP : 0;
            String targetMode = currentEnumValue("targetMode");
            h += 12;
            if ("ENTITY".equals(targetMode)) {
                h += 13 + 3 * CATALOG_ITEM_H;
            } else if ("BLOCK".equals(targetMode)) {
                h += 13 + CONTAINER_LIST_VISIBLE_ROWS * CATALOG_ITEM_H;
            }
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.PLACE) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += 13 + 16 + 4 + EDITOR_HINT_ROW_H + ROW_GAP;
            h += isFieldVisible(getField("blockPos")) ? rowH(getField("blockPos")) + ROW_GAP : 0;
            h += isFieldVisible(getField("manualDirection")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("direction")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("sneak")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("sneakMode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("interact")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("interactTiming")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("interactCustomMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiBefore")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("waitForGuiAfter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("guiName")) ? ROW_H + ROW_GAP : 0;

            h += 18 + 18;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_SLOT_CHANGE) {
            int h = PAD;
            h += ROW_H + ROW_GAP;
            h += ROW_H + ROW_GAP;
            h += 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 6;
            h += 16;
            h += 16;
            h += EDITOR_HINT_ROW_H;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_INVENTORY_PREDICATE) {
            int h = PAD;
            h += isFieldVisible(getField(MacroAction.LISTEN_DURING_PREVIOUS_KEY)) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("condition")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("itemName")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("count")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("slot")) ? ROW_H + ROW_GAP + 18 : 0;
            h += isFieldVisible(getField("timeoutMs")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("saveAs")) ? ROW_H + ROW_GAP : 0;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.DELAY_PACKETS) {
            int h = PAD;
            h += isFieldVisible(getField("mode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("flushOnDisable")) ? ROW_H + ROW_GAP : 0;
            if ("ENABLE".equals(currentEnumValue("mode"))) {
                h += 18 + 18 + 18;
            }
            h += 8;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.MINE) {
            int h = PAD;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;
            h += rowH(getField("targetBlocks")) + ROW_GAP + 4;
            h += isFieldVisible(getField("stopInventoryFull")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("stopSlotsUsed")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("slotsUsedThreshold")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("stopMinedCount")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("minedCountTarget")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("stopAfterTime")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("timeoutSeconds")) ? ROW_H + ROW_GAP : 0;
            h += 14;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.PAY) {
            int h = PAD;
            h += isFieldVisible(getField("commandTemplate")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("amountInput")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("divideEnabled")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("probeHidden")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("delayEnabled")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("delayMs")) ? ROW_H + ROW_GAP : 0;
            h += 14 + (4 * SEL_ITEM_H) + 4;
            h += 18;
            AutismChatField paySearch = addFields.get("players");
            int payRows = payPlayerScanPerformed ? Math.max(1, buildPayRows(paySearch != null ? paySearch.getText() : "").size()) : 1;
            h += payRows * CATALOG_ITEM_H + 4;
            h += EDITOR_HINT_ROW_H * 4;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.STORE_ITEM) {
            int h = PAD;
            h += isFieldVisible(getField("mode")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("allItems")) ? ROW_H + ROW_GAP : 0;
            if (!toggleStates.getOrDefault("allItems", false)) {
                h += 14 + (4 * SEL_ITEM_H) + 4;
                h += 18;
                h += 6 * CATALOG_ITEM_H + 4;
                h += EDITOR_HINT_ROW_H * 2;
            }
            h += isFieldVisible(getField("persistent")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("delayTicks")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("closeAfter")) ? ROW_H + ROW_GAP : 0;
            h += isFieldVisible(getField("closeSendPkt")) ? ROW_H + ROW_GAP : 0;
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.TOGGLE_MODULE) {
            int h = PAD;
            h += 14 + (4 * SEL_ITEM_H) + 4;
            h += 18;
            h += meteorModuleNames.isEmpty()
                    ? 12 + 6 * CATALOG_ITEM_H + 4
                    : 24 + 6 * CATALOG_ITEM_H + 8;
            h += 14;
            return h;
        }
        if (craftEntries != null) {

            return PAD + 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 6 + 16 + CRAFT_LIST_H;
        }
        if (dropAction != null) {
            int h = PAD + 14 + SEL_LIST_MAX_VIS * SEL_ITEM_H + 6 + 16 + 4;
            h += isFieldVisible(getField("packetOrder")) ? ROW_H + ROW_GAP : 0;

            h += EDITOR_HINT_ROW_H * 2;
            h += (ROW_H + ROW_GAP);
            h += ROW_H + ROW_GAP;
            h += (ROW_H + ROW_GAP);
            if (toggleStates.getOrDefault("drop_waitForGuiBefore", false)
                    || toggleStates.getOrDefault("drop_waitForGuiAfter", false)) h += ROW_H + ROW_GAP;
            return h;
        }
        if (lanStepEntries != null) {
            boolean filterByUser = toggleStates.getOrDefault("lan_filterByUser", false);
            int h = PAD + (ROW_H + ROW_GAP) * 2;
            if (!filterByUser || lanStepEntries.isEmpty()) h += ROW_H + ROW_GAP;
            h += 12;
            if (filterByUser) {
                if (!lanStepEntries.isEmpty()) {
                    int visibleRows = Math.min(3, Math.max(1, lanStepEntries.size()));
                    h += 13 + visibleRows * SEL_ITEM_H;
                } else {
                    h += 12;
                }
                h += 6 + 16;
            }
            return h;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET) {
            return PAD + 14 + 18 + (ROW_H + ROW_GAP) + 18 + 20 + (14 + SEL_ITEM_H * 4 + 4) * 2 + 4;
        }
        if (targetAction != null && targetAction.getType() == MacroActionType.WAIT_PACKET_MATCH) {
            int rows = waitPacketMatchRules == null ? 1 : Math.max(1, Math.min(4, Math.max(1, waitPacketMatchRules.size())));

            int h = PAD + EDITOR_HINT_ROW_H + 18 + (ROW_H + ROW_GAP) + 18 + 14 + rows * SEL_ITEM_H + 5;
            if (waitPacketMatchRules == null || waitPacketMatchRules.isEmpty()) {
                h += EDITOR_HINT_ROW_H;
            } else {
                int idx = Math.max(0, Math.min(waitPacketMatchEditIndex, waitPacketMatchRules.size() - 1));
                WaitPacketMatchAction.Rule rule = waitPacketMatchRules.get(idx);
                h += (ROW_H + ROW_GAP) * 2;
                if (rule.fieldName != null && !rule.fieldName.isBlank()) {
                    h += ROW_H + ROW_GAP;
                    if (rule.operator != WaitPacketMatchAction.Operator.EXISTS) {
                        h += ROW_H + ROW_GAP;
                    }
                }
            }
            return h;
        }
        if (schema == null) return 0;
        int h = PAD;
        for (FieldDef field : schema.fields()) {
            if (field.type() == FieldType.STRING_LIST) continue;
            if (isGuiWaitAfterKey(field.key())) continue;
            if (!isFieldVisible(field)) continue;
            h += rowH(field) + ROW_GAP;
        }
        for (FieldDef field : schema.fields()) {
            if (field.type() != FieldType.STRING_LIST) continue;
            if (isGuiWaitAfterKey(field.key())) continue;
            if (!isFieldVisible(field)) continue;
            h += rowH(field) + ROW_GAP;
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.PACKET) {
            h += 52;
        }

        if (targetAction != null && targetAction.getType() == MacroActionType.DELAY_PACKETS) {
            h += 4 + 14;
        }
        return h;
    }

    private boolean isEditingPickupAllItemAction() {
        return itemAction != null
                && itemEditIndex >= 0
                && itemEditIndex < itemAction.itemNames.size()
                && itemAction.getItemAction(itemEditIndex) == autismclient.util.AutismDropAction.PICKUP_ALL;
    }

    private int computeDisconnectMaxH() {
        int h = PAD;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;

        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += ROW_H + ROW_GAP;
        h += EDITOR_HINT_ROW_H;
        return h;
    }

    private void drawLabel(GuiGraphicsExtractor ctx, String text, int x, int y, int maxW,
                           Identifier font) {
        String trimmed = UiText.trimToWidth(textRenderer, text, maxW - 4, font, -1);
        UiText.draw(
            ctx,
            textRenderer,
            trimmed,
            font,
            AutismColors.textPrimary(),
            x,
            UiSizing.alignTextY(y, ROW_H, fontHeight(font), theme.bodyTextNudge()),
            false
        );
    }

    private int fontHeight(Identifier font) {
        if (UiAssets.FONT_TITLE.equals(font)) return theme.fontHeight(UiTone.TITLE);
        if (UiAssets.FONT_LABEL.equals(font)) return theme.fontHeight(UiTone.LABEL);
        return theme.fontHeight(UiTone.BODY);
    }

    private int uiWidth(Identifier font, String text) {
        return UiText.width(textRenderer, text == null ? "" : text, font, AutismColors.textPrimary());
    }

    private int uiWidth(String text) {
        return uiWidth(theme.fontFor(UiTone.BODY), text);
    }

    private int labelWidth(int totalWidth, String label, Identifier font) {
        return labelWidth(totalWidth, label, font, 56);
    }

    private int labelWidth(int totalWidth, String label, Identifier font, int minControlWidth) {
        int measured = uiWidth(font, label) + 8;
        int availableMax = Math.max(LABEL_MIN_W, totalWidth - Math.max(28, minControlWidth) - FIELD_GAP);
        return Math.max(LABEL_MIN_W, Math.min(measured, availableMax));
    }

    private int controlX(int x, int labelWidth) {
        return x + labelWidth + FIELD_GAP;
    }

    private int controlWidth(int totalWidth, int labelWidth) {
        return Math.max(28, totalWidth - labelWidth - FIELD_GAP);
    }

    private void fillBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        UiRenderer.outline(ctx, UiBounds.of(x, y, w, h), color);
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int trackX, int trackY, int trackH,
                               int totalCount, int visibleCount, int itemH, int scrollOffset,
                               int mouseX, int mouseY) {
        if (totalCount <= visibleCount) return;
        int viewPixels = Math.max(1, Math.min(trackH, visibleCount * itemH));
        int contentPixels = Math.max(0, totalCount * itemH);
        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, SCROLLBAR_W, trackH, scrollOffset);

        boolean sbDragging = activeScrollDragHandler != null;
        CompactScrollbar.draw(ctx, metrics, metrics.contains(mouseX, mouseY), sbDragging);
    }

    private DirectScrollViewport getOrCreateViewport(Map<String, DirectScrollViewport> viewports, String key,
                                                      int x, int y, int width, int height, int rowHeight, int scrollbarWidth) {
        DirectScrollViewport vp = viewports.get(key);
        if (vp == null || vp.getX() != x || vp.getY() != y || vp.getWidth() != width || vp.getHeight() != height) {
            vp = new DirectScrollViewport(x, y, width, height, rowHeight, scrollbarWidth);
            viewports.put(key, vp);
        }
        return vp;
    }

    private AutismChatField makeField(int w) {
        return new AutismChatField(MC, textRenderer, 0, 0, w, 13, false);
    }

    private static String fmtDouble(double v) {
        long lv = (long) v;
        return (v == lv) ? String.valueOf(lv) : String.valueOf(v);
    }

    private static boolean matchesListFilter(String filter, String... candidates) {
        String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void startCapture(FieldDef field, List<String> lst) {
        if (field.captureMode() == CaptureMode.PACKET_NAME) {

            boolean both = targetAction != null
                    && targetAction.getType() == autismclient.util.macro.MacroActionType.PACKET_GATE;
            if (both) {
                java.util.function.BiConsumer<Class<? extends net.minecraft.network.protocol.Packet<?>>, Boolean> toggle =
                        (packetClass, selected) -> {
                            if (packetClass == null || lst == null) return;
                            String name = AutismPacketRegistry.getName(packetClass);
                            if (name == null || name.isBlank()) name = AutismPacketNamer.getFriendlyName(packetClass);
                            if (selected) {
                                if (!lst.contains(name)) lst.add(name);
                            } else {
                                lst.remove(name);
                            }
                        };
                java.util.List<Class<? extends net.minecraft.network.protocol.Packet<?>>> selected = new java.util.ArrayList<>();
                if (lst != null) {
                    for (String name : lst) {
                        Class<? extends net.minecraft.network.protocol.Packet<?>> c = AutismPacketRegistry.getPacket(name);
                        if (c != null && !selected.contains(c)) selected.add(c);
                    }
                }
                packetSelectorOverlay.openToggleAny(toggle, selected);
                return;
            }

            java.util.function.Consumer<Class<? extends net.minecraft.network.protocol.Packet<?>>> add = packetClass -> {
                if (packetClass == null || lst == null) return;
                String name = AutismPacketRegistry.getName(packetClass);
                if (name == null || name.isBlank()) name = AutismPacketNamer.getFriendlyName(packetClass);
                if (!lst.contains(name)) lst.add(name);
            };
            String direction = currentEnumValue("direction");
            if ("ANY".equals(direction)) packetSelectorOverlay.open(add);
            else if ("S2C".equals(direction)) packetSelectorOverlay.openS2C(add);
            else packetSelectorOverlay.openC2S(add);
            return;
        }
        if (!guardWorldCaptureAction()) return;
        screenBeforeCapture = MC.gui.screen();
        enterCaptureMode();
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });

        AutismSharedState state = AutismSharedState.get();
        state.setCaptureCancelCallback(() -> {
            exitCaptureMode(true, false);
        });
        if (field.captureMode() == CaptureMode.BLOCK_ID || field.captureMode() == CaptureMode.BLOCK_CATALOG) {
            state.setBlockCaptureCallback(pos -> {
                if (MC.level != null) {
                    String id = BuiltInRegistries.BLOCK.getKey(
                            MC.level.getBlockState(pos).getBlock()).toString();
                    if (!lst.contains(id)) lst.add(id);
                }
                exitCaptureMode(true, false);
            });
        } else if (field.captureMode() == CaptureMode.ENTITY_ID) {
            boolean specificCapture = isOpenContainerEntityListKey(field.key());
            boolean replaceOnCapture = isReplaceOnCaptureEntityKey(field.key());
            state.setEntityCaptureSpecific(specificCapture);
            state.setEntityCaptureCallback(payload -> {
                String value = payload == null ? "" : payload.strip();
                if (!value.isBlank()) {
                    if (replaceOnCapture) lst.clear();
                    if (!lst.contains(value)) lst.add(value);
                }
                exitCaptureMode(true, false);
            });
        }
    }

    private void startBlockPosCapture(FieldDef field) {
        if (!guardWorldCaptureAction()) return;
        if (targetAction != null
            && (targetAction.getType() == MacroActionType.INSTA_BREAK || targetAction.getType() == MacroActionType.BREAK)
            && "blockPos".equals(field.key())) {
            startInstaBreakCapture();
            return;
        }
        screenBeforeCapture = MC.gui.screen();
        enterCaptureMode();
        String key   = field.key();
        boolean dbl  = field.xyzDouble();
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });

        AutismSharedState state = AutismSharedState.get();
        state.setPlaceCaptureActive(targetAction != null && targetAction.getType() == MacroActionType.PLACE);
        state.setCaptureCancelCallback(() -> {
            state.setPlaceCaptureActive(false);
            exitCaptureMode(true, false);
        });

        if (targetAction != null && targetAction.getType() == MacroActionType.PLACE) {
            state.setDirectionalBlockCaptureCallback((pos, face) -> {

                Direction useFace = face == null ? Direction.UP : face;
                BlockPos targetPos = pos.relative(useFace);
                AutismChatField fx = textFields.get(key + "_0");
                AutismChatField fy = textFields.get(key + "_1");
                AutismChatField fz = textFields.get(key + "_2");
                if (fx != null) fx.setText(dbl ? fmtDouble(targetPos.getX()) : String.valueOf(targetPos.getX()));
                if (fy != null) fy.setText(dbl ? fmtDouble(targetPos.getY()) : String.valueOf(targetPos.getY()));
                if (fz != null) fz.setText(dbl ? fmtDouble(targetPos.getZ()) : String.valueOf(targetPos.getZ()));

                state.setPlaceCaptureActive(false);
                exitCaptureMode(true, false);
            });
        } else {
            state.setBlockCaptureCallback(pos -> {
                AutismChatField fx = textFields.get(key + "_0");
                AutismChatField fy = textFields.get(key + "_1");
                AutismChatField fz = textFields.get(key + "_2");
                if (fx != null) fx.setText(dbl ? fmtDouble(pos.getX()) : String.valueOf(pos.getX()));
                if (fy != null) fy.setText(dbl ? fmtDouble(pos.getY()) : String.valueOf(pos.getY()));
                if (fz != null) fz.setText(dbl ? fmtDouble(pos.getZ()) : String.valueOf(pos.getZ()));
                exitCaptureMode(true, false);
            });
        }
    }

    private void startInstaBreakCapture() {
        if (!guardWorldCaptureAction()) return;
        screenBeforeCapture = MC.gui.screen();
        enterCaptureMode();
        MC.execute(() -> { if (MC.gui.screen() != null) MC.gui.setScreen(null); });

        AutismSharedState state = AutismSharedState.get();
        state.setCaptureCancelCallback(() -> exitCaptureMode(true, false));
        state.setDirectionalBlockCaptureCallback((pos, direction) -> {
            fillBlockPosField("blockPos", pos);
            enumIndices.put("direction", direction == null ? 1 : direction.ordinal());
            AutismClientMessaging.sendPrefixed("InstaBreak target captured");
            exitCaptureMode(true, false);
        });
        AutismClientMessaging.sendPrefixed("InstaBreak: right-click the target block to capture it");
    }

    private static List<String> getAllBlockIds() {
        if (ALL_BLOCK_IDS == null) {
            ALL_BLOCK_IDS = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                String path = id.getPath();

                if (path.endsWith("_wall_head") || path.endsWith("_wall_sign") || path.endsWith("_wall_banner") || path.endsWith("_wall_torch") || path.endsWith("_wall_skull")) {
                    continue;
                }
                ALL_BLOCK_IDS.add(id.toString());
            }
            Collections.sort(ALL_BLOCK_IDS);
        }
        return ALL_BLOCK_IDS;
    }

    private static String formatTypeName(MacroActionType type) {
        if (type == null) return "Action";
        String[] parts = type.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String getAbstractContainerScreenCaptureTargetLabel() {
        String activeCaptureKey = captureSession.itemSlotKey();
        if ("_item_entries".equals(activeCaptureKey) || "_drop_entries".equals(activeCaptureKey)
                || "_wsc_entries".equals(activeCaptureKey)) {
            return "Slot + Item";
        }
        FieldDef field = findField(activeCaptureKey);
        return field != null ? field.label() : "Slot";
    }

    private String getCaptureActionLabel() {
        return targetAction != null ? targetActionTitle() : "Action";
    }

    private String targetActionTitle() {
        if (targetAction == null) return "Edit Action";
        String title = targetAction.getType() != null ? formatTypeName(targetAction.getType()) : targetAction.getDisplayName();
        return multiEditCount > 1 ? title + " x" + multiEditCount : title;
    }

    private FieldDef findField(String key) {
        if (schema == null || key == null) return null;
        for (FieldDef field : schema.fields()) {
            if (key.equals(field.key())) return field;
        }
        return null;
    }
}
