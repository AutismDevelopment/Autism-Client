package autismclient.gui.vanillaui.module;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.UiTextRenderer;
import autismclient.gui.vanillaui.UiTheme;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.AnimatedToggleButton;
import autismclient.gui.vanillaui.components.CollapsibleSection;
import autismclient.gui.vanillaui.components.ColorPicker;
import autismclient.gui.vanillaui.components.CompactKeybindButton;
import autismclient.gui.vanillaui.components.CompactWindow;
import autismclient.gui.vanillaui.components.ConnectedButton;
import autismclient.gui.vanillaui.components.Dropdown;
import autismclient.gui.vanillaui.components.ScrollContainer;
import autismclient.gui.vanillaui.components.Scrollbar;
import autismclient.gui.vanillaui.components.Slider;
import autismclient.gui.vanillaui.components.TextField;
import autismclient.gui.vanillaui.components.Toggle;
import autismclient.gui.vanillaui.components.Tooltip;
import autismclient.gui.vanillaui.components.TopBar;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.modules.PackHideState;
import autismclient.modules.Module;
import autismclient.modules.ModuleCategory;
import autismclient.api.module.ActionSetting;
import autismclient.api.module.DisplayMode;
import autismclient.api.module.Kind;
import autismclient.api.module.Setting;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismConfig;
import autismclient.util.AutoFishStopMacroFactory;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPacketNamer;
import autismclient.util.PacketListCodec;
import autismclient.util.AutismSharedState;
import autismclient.util.StringListCodec;
import autismclient.util.macro.MacroConditionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VanillaModuleMenuController {
    public interface Host {
        default boolean offlineSetup() {
            return false;
        }

        Screen screen();

        Font font();

        void closeMenu();

        void saveConfig();

        void openPacketSelector(Module module, Setting<?, ?> option);

        void openStringListEditor(Module module, Setting<?, ?> option);

        void openRegistryListEditor(Module module, Setting<?, ?> option);

        void openMacroCreator(Module module, Setting<?, ?> option);

        default void openMacroCreator(Module module, Setting<?, ?> option, AutismMacro macro) {
            openMacroCreator(module, option);
        }

        void runUtility(String id);

        void addToggleMacro(Module module);
    }

    private static final int CATEGORY_W = 150;
    private static final int CATEGORY_MIN_W = 100;
    private static final int SETTINGS_W = 360;
    private static final int UTILITY_W = 170;
    private static final int MIN_VIEW_H = 42;
    private static final int MAX_CATEGORY_VISIBLE_ROWS = 9;
    private static final String MAIN_MENU = "MAIN_MENU";
    private static final String LEGACY_UTILITIES = "UTILITIES";
    private static final String SEARCH_WINDOW = "SEARCH";
    private static final List<UtilityAction> UTILITY_ACTIONS = List.of(
        UtilityAction.button("macros", "Macros", UiAssets.ICON_MACROS, Button.Tone.NORMAL),
        UtilityAction.button("admin", "Admin Tools", UiAssets.ICON_FABRICATOR, Button.Tone.NORMAL),
        UtilityAction.button("lan", "LAN Sync", UiAssets.ICON_LANSYNC, Button.Tone.NORMAL),
        UtilityAction.button("queue", "Packet Q", UiAssets.ICON_PACKET_Q_EDITOR, Button.Tone.NORMAL),
        UtilityAction.button("logger", "Logger", UiAssets.ICON_PACKET_LOGGER, Button.Tone.NORMAL),
        UtilityAction.button("packets", "Packets", UiAssets.ICON_FILTER, Button.Tone.NORMAL),
        UtilityAction.button("server", "Server", UiAssets.ICON_SERVER_INFO, Button.Tone.NORMAL),
        UtilityAction.button("keys", "Settings", UiAssets.ICON_KEYBINDS, Button.Tone.NORMAL),
        UtilityAction.button("matchmaking", "Matchmaking", UiAssets.ICON_MATCHMAKING, Button.Tone.NORMAL),
        UtilityAction.button("multi", "Multi", UiAssets.ICON_MULTI, Button.Tone.NORMAL),
        UtilityAction.category("Packet", UiAssets.ICON_PACKET_CATEGORY),
        UtilityAction.toggle("send", "Send", null, Button.Tone.SUCCESS),
        UtilityAction.toggle("delay", "Delay", null, Button.Tone.PRIMARY),
        UtilityAction.button("flush", "Flush", null, Button.Tone.NORMAL),
        UtilityAction.button("clear", "Clear", null, Button.Tone.NORMAL)
    );
    private static final List<UtilityAction> OFFLINE_UTILITY_ACTIONS = List.of(
        UtilityAction.button("macros", "Macros", UiAssets.ICON_MACROS, Button.Tone.NORMAL),
        UtilityAction.button("lan", "LAN Sync", UiAssets.ICON_LANSYNC, Button.Tone.NORMAL),
        UtilityAction.button("queue", "Packet Q", UiAssets.ICON_PACKET_Q_EDITOR, Button.Tone.NORMAL),
        UtilityAction.button("logger", "Logger", UiAssets.ICON_PACKET_LOGGER, Button.Tone.NORMAL),
        UtilityAction.button("packets", "Filters", UiAssets.ICON_FILTER, Button.Tone.NORMAL),
        UtilityAction.button("server", "Server", UiAssets.ICON_SERVER_INFO, Button.Tone.NORMAL),
        UtilityAction.button("keys", "Settings", UiAssets.ICON_KEYBINDS, Button.Tone.NORMAL),
        UtilityAction.button("matchmaking", "Matchmaking", UiAssets.ICON_MATCHMAKING, Button.Tone.NORMAL),
        UtilityAction.button("multi", "Multi", UiAssets.ICON_MULTI, Button.Tone.NORMAL)
    );

    private final Host host;
    private final UiTheme theme = new UiTheme();
    private final UiScissorStack scissors = new UiScissorStack();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> recycledHits = new ArrayList<>();

    private static final Map<String, Integer> windowScroll = new HashMap<>();
    private static final Map<Module, Integer> settingsScroll = new IdentityHashMap<>();
    private static final Map<String, Boolean> groupCollapsed = new HashMap<>();
    private static final List<String> windowZOrder = new ArrayList<>();
    private final Map<ModuleCategory, CachedOrderedModules> orderedModulesCache = new HashMap<>();
    private final Map<String, CachedSettingRows> settingRowsCache = new HashMap<>();
    private final Map<String, AnimatedValue> enabledAnimations = new HashMap<>();
    private final Map<String, String> enabledSeparatorOwners = new HashMap<>();
    private UiTextRenderer text;
    private Module selectedModule;
    private Module bindingModule;
    private Module bindingOptionModule;
    private Setting<?, ?> bindingOption;

    private long bindConsumedAtNanos;
    private Editing editing;
    private Dropdown dropdown;
    private Module dropdownModule;
    private Setting<?, ?> dropdownOption;
    private ColorPicker colorPicker;
    private MacroPicker macroPicker;
    private String draggingWindowId;
    private String resizingCategoryId;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragStartX;
    private int dragStartY;
    private boolean draggingMoved;
    private DragScrollbar scrollbarDrag;
    private Module sliderModule;
    private Setting<?, ?> sliderOption;
    private int screenWidth;
    private int screenHeight;
    private String tooltip;

    public VanillaModuleMenuController(Host host) {
        this.host = Objects.requireNonNull(host);
    }

    public void init() {
        text = UiContexts.textRenderer(host.font());
    }

    public boolean blocksGlobalKeybinds() {
        return editing != null || bindingModule != null || bindingOption != null || dropdown != null || colorPicker != null || macroPicker != null;
    }

    public boolean hasTopLayer() {
        return dropdown != null || colorPicker != null || macroPicker != null || editing != null || selectedModule != null;
    }

    public boolean hasSelectedModule() {
        return selectedModule != null;
    }

    public void openSettingsByModuleId(String moduleId) {
        Module module = ModuleRegistry.get(moduleId);
        if (module != null) openSettings(module);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, int width, int height) {
        if (text == null || text.font() != host.font()) text = UiContexts.textRenderer(host.font());
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        tooltip = "";
        recycleHits();
        initializeWindowDefaults();
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth, screenHeight), theme.colors().screenScrim);
        boolean topPopup = dropdown != null || colorPicker != null || macroPicker != null;
        int baseMouseX = topPopup ? -10000 : mouseX;
        int baseMouseY = topPopup ? -10000 : mouseY;
        UiContext context = new UiContext(graphics, theme, text, screenWidth, screenHeight, baseMouseX, baseMouseY, delta);
        if (selectedModule == null) {
            renderCategoryWindows(context);
        } else {
            if (PackHideState.isActive() && !PackHideState.isHideModule(selectedModule)) selectedModule = null;
            if (selectedModule != null) renderSettingsWindow(context, selectedModule);
        }
        UiContext overlayContext = topPopup ? new UiContext(graphics, theme, text, screenWidth, screenHeight, mouseX, mouseY, delta) : context;
        renderDropdown(overlayContext, mouseX, mouseY);
        renderColorPicker(overlayContext, mouseX, mouseY);
        renderMacroPicker(overlayContext, mouseX, mouseY);
        if (tooltip != null && !tooltip.isBlank() && !topPopup) Tooltip.render(context, tooltip, mouseX, mouseY);
    }

    private void renderCategoryWindows(UiContext context) {
        String topHoverWindowId = topWindowIdAt(context.mouseX(), context.mouseY());
        for (String id : windowRenderOrder()) {
            UiContext windowContext = hoverContextFor(context, topHoverWindowId, id);
            if (MAIN_MENU.equals(id)) renderUtilityWindow(windowContext);
            else if (SEARCH_WINDOW.equals(id)) renderSearchWindow(windowContext);
            else {
                ModuleCategory category = categoryById(id);
                if (category != null) renderCategoryWindow(windowContext, category);
            }
        }
    }

    private UiContext hoverContextFor(UiContext context, String topHoverWindowId, String windowId) {
        if (topHoverWindowId == null || topHoverWindowId.equals(windowId)) return context;
        return new UiContext(context.graphics(), context.theme(), context.text(), context.screenWidth(), context.screenHeight(), -10000, -10000, context.delta());
    }

    private String topWindowIdAt(int mouseX, int mouseY) {
        List<String> order = windowRenderOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            String id = order.get(i);
            UiBounds bounds;
            if (MAIN_MENU.equals(id)) {
                int contentH = utilityContentHeight();
                bounds = utilityWindowBounds(contentH);
            } else if (SEARCH_WINDOW.equals(id)) {
                bounds = searchWindowBounds(searchContentHeight());
            } else {
                ModuleCategory category = categoryById(id);
                if (category == null) continue;
                List<Module> modules = orderedModules(category);
                if (modules.isEmpty()) continue;
                bounds = categoryWindowBounds(category, modules.size() * theme.spacing().rowHeight);
            }
            if (bounds.contains(mouseX, mouseY)) return id;
        }
        return null;
    }

    private UiBounds categoryWindowBounds(ModuleCategory category, int contentH) {
        AutismConfig.ModuleCategoryLayout layout = layout(category.name());
        int w = categoryWidth(category);
        int maxBodyH = maxCategoryBodyHeight(category);
        int bodyH = layout.collapsed ? 0 : Math.min(contentH, maxBodyH);
        return UiBounds.of(layout.x, layout.y, w, theme.spacing().topBarHeight + bodyH).clampInside(screenWidth - 4, screenHeight - 4);
    }

    private int maxCategoryBodyHeight(ModuleCategory category) {
        int screenCap = screenHeight - 8 - theme.spacing().topBarHeight;
        int rows = layout(category.name()).visibleRows;
        int rowCap = (rows > 0 ? Math.max(1, rows) : MAX_CATEGORY_VISIBLE_ROWS) * theme.spacing().rowHeight;
        return Math.max(MIN_VIEW_H, Math.min(screenCap, rowCap));
    }

    private final Map<ModuleCategory, Integer> categoryWidthCache = new HashMap<>();
    private int categoryWidthStampRevision = Integer.MIN_VALUE;
    private boolean categoryWidthStampHidden;
    private int categoryWidthStampFont;
    private int categoryWidthStampScreenW;
    private int categoryWidthStampScreenH;

    private int categoryWidth(ModuleCategory category) {
        int revision = ModuleRegistry.revision();
        boolean hidden = PackHideState.isActive();
        int fontId = text == null ? 0 : System.identityHashCode(text);
        if (revision != categoryWidthStampRevision || hidden != categoryWidthStampHidden
            || fontId != categoryWidthStampFont || screenWidth != categoryWidthStampScreenW
            || screenHeight != categoryWidthStampScreenH) {
            categoryWidthCache.clear();
            categoryWidthStampRevision = revision;
            categoryWidthStampHidden = hidden;
            categoryWidthStampFont = fontId;
            categoryWidthStampScreenW = screenWidth;
            categoryWidthStampScreenH = screenHeight;
        }
        Integer cached = categoryWidthCache.get(category);
        if (cached != null) return cached;
        int width = computeCategoryWidth(category);
        categoryWidthCache.put(category, width);
        return width;
    }

    private int computeCategoryWidth(ModuleCategory category) {
        List<Module> modules = orderedModules(category);
        int maxNameW = 0;
        for (Module module : modules) maxNameW = Math.max(maxNameW, text.width(module.name()));
        int contentH = modules.size() * theme.spacing().rowHeight;
        int maxBodyH = maxCategoryBodyHeight(category);
        int scrollbar = contentH > maxBodyH ? theme.spacing().scrollbarWidth : 0;

        int rowNeeded = 5 + maxNameW + 9 + CompactKeybindButton.WIDTH + 3 + 2 + scrollbar;
        int headerNeeded = text.width(category.label()) + 26;
        int w = Math.max(72, Math.max(rowNeeded, headerNeeded));
        return Math.min(w, Math.min(CATEGORY_W, Math.max(1, screenWidth - 8)));
    }

    private int windowWidth(String id) {
        if (MAIN_MENU.equals(id)) return Math.max(112, Math.min(UTILITY_W, screenWidth - 8));
        ModuleCategory category = categoryById(id);
        if (category != null) return categoryWidth(category);
        return Math.max(CATEGORY_MIN_W, Math.min(CATEGORY_W, screenWidth - 8));
    }

    private UiBounds utilityWindowBounds(int contentH) {
        AutismConfig.ModuleCategoryLayout layout = layout(MAIN_MENU);
        int w = Math.max(112, Math.min(UTILITY_W, screenWidth - 8));
        int maxBodyH = Math.max(MIN_VIEW_H, screenHeight - 8 - theme.spacing().topBarHeight);
        int bodyH = layout.collapsed ? 0 : Math.min(contentH, maxBodyH);
        return UiBounds.of(layout.x, layout.y, w, theme.spacing().topBarHeight + bodyH + (layout.collapsed ? 0 : 1)).clampInside(screenWidth - 4, screenHeight - 4);
    }

    private void renderCategoryWindow(UiContext context, ModuleCategory category) {
        List<Module> modules = orderedModules(category);
        if (modules.isEmpty()) return;
        AutismConfig.ModuleCategoryLayout layout = layout(category.name());
        int contentH = modules.size() * theme.spacing().rowHeight;
        UiBounds bounds = categoryWindowBounds(category, contentH);
        syncLayout(layout, bounds);
        UiBounds header = UiBounds.of(bounds.x(), bounds.y(), bounds.width(), theme.spacing().topBarHeight);
        String headerTitle = category.label();
        CompactWindow.renderFrame(context, bounds, headerTitle, layout.collapsed, false, header.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.WINDOW_HEADER, header, null, null, category, category.name(), -1);
        if (layout.collapsed) return;

        int bodyH = Math.max(0, bounds.height() - theme.spacing().topBarHeight);
        UiBounds viewport = UiBounds.of(bounds.x() + 1, bounds.y() + theme.spacing().topBarHeight - 1, bounds.width() - 2, bodyH);
        int scroll = clampWindowScroll(category.name(), contentH, viewport.height());
        if (contentH > viewport.height()) {
            viewport = UiBounds.of(viewport.x(), viewport.y(), viewport.width() - theme.spacing().scrollbarWidth, viewport.height());
        }
        addHit(HitType.SCROLL_AREA, viewport, null, null, category, category.name(), -1);
        scissors.push(context.graphics(), viewport);
        try {
            int first = ScrollContainer.firstVisibleRow(scroll, theme.spacing().rowHeight, modules.size());
            int last = ScrollContainer.lastVisibleRow(scroll, viewport.height(), theme.spacing().rowHeight, modules.size());
            for (int i = first; i <= last; i++) {
                Module module = modules.get(i);
                int y = viewport.y() - scroll + i * theme.spacing().rowHeight;
                renderModuleRow(context, module, UiBounds.of(viewport.x(), y, viewport.width(), theme.spacing().rowHeight), category, i);
            }
        } finally {
            scissors.pop(context.graphics());
        }
        if (contentH > viewport.height()) {
            UiBounds track = UiBounds.of(bounds.right() - theme.spacing().scrollbarWidth - 1, viewport.y(), theme.spacing().scrollbarWidth, viewport.height());
            Scrollbar.Metrics metrics = Scrollbar.metrics(track, contentH, viewport.height(), scroll);
            Scrollbar.render(context, metrics, metrics.track().contains(context.mouseX(), context.mouseY()),
                scrollbarDrag != null && category.name().equals(scrollbarDrag.id));
            addHit(HitType.SCROLLBAR, scrollbarHitBounds(track), null, null, category, category.name(), -1);
        }

        boolean gripHot = resizingCategoryId != null && resizingCategoryId.equals(category.name())
            || (context.mouseY() >= bounds.bottom() - 6 && context.mouseY() < bounds.bottom()
                && context.mouseX() >= bounds.x() && context.mouseX() < bounds.right());
        if (gripHot) {
            autismclient.gui.vanillaui.UiRenderer.rect(context.graphics(),
                UiBounds.of(bounds.x() + 1, bounds.bottom() - 2, bounds.width() - 2, 2),
                context.theme().colors().accent);
        }
        addHit(HitType.CATEGORY_RESIZE, UiBounds.of(bounds.x(), bounds.bottom() - 6, bounds.width(), 6),
            null, null, category, category.name(), -1);
    }

    private String searchQuery = "";
    private String cachedSearchQuery = "";
    private int cachedSearchModuleRevision = Integer.MIN_VALUE;
    private List<SearchMatch> cachedSearchMatches = List.of();

    private String settingsSearchQuery = "";

    private UiBounds editingFieldBounds;
    private boolean draggingTextSelection;

    private boolean isSearchEditing() {
        return editing != null && editing.module == null && editing.option == null;
    }

    private boolean isSettingsSearchEditing() {
        return editing != null && editing.module != null && editing.option == null;
    }

    private String currentSearchQuery() {
        return isSearchEditing() ? editing.text : searchQuery;
    }

    private String currentSettingsSearch() {
        return isSettingsSearchEditing() ? editing.text : settingsSearchQuery;
    }

    private int cursorAtX(String value, int mx, UiBounds bounds) {
        int local = Math.max(0, mx - bounds.x() - 4);
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            if (text.width(value.substring(0, middle)) < local) low = middle + 1;
            else high = middle;
        }
        int cursor = low;
        if (cursor > 0 && cursor < value.length()
                && Character.isHighSurrogate(value.charAt(cursor - 1))
                && Character.isLowSurrogate(value.charAt(cursor))) {
            cursor--;
        }
        return cursor;
    }

    private void beginTextDrag(UiBounds bounds) {
        editingFieldBounds = bounds;
        draggingTextSelection = true;
    }

    private void beginSettingsSearchEditing(int mx, UiBounds bounds) {
        editing = new Editing(selectedModule, null, settingsSearchQuery, cursorAtX(settingsSearchQuery, mx, bounds));
        beginTextDrag(bounds);
    }

    private void beginSearchEditing(int mx, UiBounds bounds) {
        editing = new Editing(null, null, searchQuery, cursorAtX(searchQuery, mx, bounds));
        beginTextDrag(bounds);
    }

    private void focusSearchWindow() {
        finishEditing(true);
        selectedModule = null;
        AutismConfig.ModuleCategoryLayout layout = layout(SEARCH_WINDOW);
        layout.collapsed = false;
        host.saveConfig();
        bringWindowToFront(SEARCH_WINDOW);
        editing = new Editing(null, null, searchQuery, searchQuery.length());
        if (!searchQuery.isEmpty()) editing.selectAll();
    }

    private record SearchMatch(Module module, ModuleCategory category, int index) {}

    private List<SearchMatch> searchMatches() {
        String query = currentSearchQuery().trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) return List.of();
        String needle = fuzzyNormalize(query);
        if (needle.isEmpty()) return List.of();
        int moduleRevision = ModuleRegistry.revision();
        if (moduleRevision == cachedSearchModuleRevision && query.equals(cachedSearchQuery)) {
            return cachedSearchMatches;
        }
        List<SearchMatch> matches = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<Module> modules = orderedModules(category);
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                String name = fuzzyNormalize(module.name());
                if (name.contains(needle) || fuzzyNormalize(module.id()).contains(needle)
                        || isSubsequence(needle, name)) {
                    matches.add(new SearchMatch(module, category, i));
                }
            }
        }
        cachedSearchQuery = query;
        cachedSearchModuleRevision = moduleRevision;
        cachedSearchMatches = List.copyOf(matches);
        return cachedSearchMatches;
    }

    private static String fuzzyNormalize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '-' || c == '_' || c == '.') continue;
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static boolean isSubsequence(String needle, String haystack) {
        if (needle.isEmpty()) return false;
        int i = 0;
        for (int j = 0; j < haystack.length() && i < needle.length(); j++) {
            if (haystack.charAt(j) == needle.charAt(i)) i++;
        }
        return i == needle.length();
    }

    private int searchFieldAreaHeight() {
        return theme.spacing().buttonHeight + 6;
    }

    private int searchContentHeight() {
        int resultsH = currentSearchQuery().trim().isEmpty() ? 0
            : Math.max(theme.spacing().rowHeight, searchMatches().size() * theme.spacing().rowHeight);
        return searchFieldAreaHeight() + resultsH;
    }

    private UiBounds searchWindowBounds(int contentH) {
        AutismConfig.ModuleCategoryLayout layout = layout(SEARCH_WINDOW);
        int w = windowWidth(SEARCH_WINDOW);
        int maxBodyH = Math.max(MIN_VIEW_H, screenHeight - 8 - theme.spacing().topBarHeight);
        int bodyH = layout.collapsed ? 0 : Math.min(contentH, maxBodyH);
        return UiBounds.of(layout.x, layout.y, w, theme.spacing().topBarHeight + bodyH).clampInside(screenWidth - 4, screenHeight - 4);
    }

    private void renderSearchWindow(UiContext context) {
        AutismConfig.ModuleCategoryLayout layout = layout(SEARCH_WINDOW);
        List<SearchMatch> matches = searchMatches();
        boolean hasQuery = !currentSearchQuery().trim().isEmpty();
        UiBounds bounds = searchWindowBounds(searchContentHeight());
        syncLayout(layout, bounds);
        UiBounds header = UiBounds.of(bounds.x(), bounds.y(), bounds.width(), theme.spacing().topBarHeight);
        CompactWindow.renderFrame(context, bounds, "Search", layout.collapsed, false, header.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.WINDOW_HEADER, header, null, null, null, SEARCH_WINDOW, -1);
        if (layout.collapsed) return;

        UiBounds field = UiBounds.of(bounds.x() + 4, bounds.y() + theme.spacing().topBarHeight + 3,
            Math.max(1, bounds.width() - 8), theme.spacing().buttonHeight);
        if (isSearchEditing()) {
            TextField.render(context, field, editing.text, "Search modules...", true,
                editing.cursor, editing.selectionStart(), editing.selectionEnd());
        } else {
            TextField.render(context, field, searchQuery, "Search modules...", false, searchQuery.length());
        }
        addHit(HitType.SEARCH_FIELD, field, null, null, null, SEARCH_WINDOW, -1);
        if (!hasQuery) return;

        int resultsTop = bounds.y() + theme.spacing().topBarHeight + searchFieldAreaHeight() - 1;
        int bodyH = Math.max(0, bounds.bottom() - resultsTop);
        UiBounds viewport = UiBounds.of(bounds.x() + 1, resultsTop, bounds.width() - 2, bodyH);
        if (matches.isEmpty()) {
            context.text().drawFitted(context.graphics(), "No matches", viewport.x() + 4, viewport.y() + 3,
                Math.max(1, viewport.width() - 8), theme.colors().muted);
            return;
        }
        int listContentH = matches.size() * theme.spacing().rowHeight;
        int scroll = clampWindowScroll(SEARCH_WINDOW, listContentH, viewport.height());
        if (listContentH > viewport.height()) {
            viewport = UiBounds.of(viewport.x(), viewport.y(), viewport.width() - theme.spacing().scrollbarWidth, viewport.height());
        }
        addHit(HitType.SCROLL_AREA, viewport, null, null, null, SEARCH_WINDOW, -1);
        scissors.push(context.graphics(), viewport);
        try {
            int first = ScrollContainer.firstVisibleRow(scroll, theme.spacing().rowHeight, matches.size());
            int last = ScrollContainer.lastVisibleRow(scroll, viewport.height(), theme.spacing().rowHeight, matches.size());
            for (int i = first; i <= last; i++) {
                SearchMatch match = matches.get(i);
                int y = viewport.y() - scroll + i * theme.spacing().rowHeight;
                renderSearchModuleRow(context, match.module(), UiBounds.of(viewport.x(), y, viewport.width(), theme.spacing().rowHeight));
            }
        } finally {
            scissors.pop(context.graphics());
        }
        if (listContentH > viewport.height()) {
            UiBounds track = UiBounds.of(bounds.right() - theme.spacing().scrollbarWidth - 1, viewport.y(), theme.spacing().scrollbarWidth, viewport.height());
            Scrollbar.Metrics metrics = Scrollbar.metrics(track, listContentH, viewport.height(), scroll);
            Scrollbar.render(context, metrics, metrics.track().contains(context.mouseX(), context.mouseY()),
                scrollbarDrag != null && SEARCH_WINDOW.equals(scrollbarDrag.id));
            addHit(HitType.SCROLLBAR, scrollbarHitBounds(track), null, null, null, SEARCH_WINDOW, -1);
        }
    }

    private void renderSearchModuleRow(UiContext context, Module module, UiBounds row) {
        boolean hovered = row.contains(context.mouseX(), context.mouseY());
        float enabledProgress = animatedEnabledProgress(module);
        float hoverT = hoverFade("mod:search:" + module.id(), hovered);
        UiRenderer.rect(context.graphics(), row, lerpColor(theme.colors().row, theme.colors().rowHover, hoverT));
        if (enabledProgress > 0.001f) {
            int fillW = Math.max(1, Math.round(row.width() * enabledProgress));
            UiRenderer.rect(context.graphics(), UiBounds.of(row.x(), row.y(), fillW, row.height()),
                hovered ? alphaColor(theme.colors().accent, 0.34f) : alphaColor(theme.colors().accent, 0.22f));
            int color = alphaColor(theme.colors().accent, enabledProgress);
            int animatedW = Math.max(1, Math.round(row.width() * enabledProgress));
            UiRenderer.horizontalEdge(context.graphics(), row.x(), row.y(), animatedW, color);
            UiRenderer.horizontalEdge(context.graphics(), row.x(), row.bottom() - 1, animatedW, color);
            UiRenderer.verticalEdge(context.graphics(), row.x(), row.y(), row.height(), color);
            UiRenderer.verticalEdge(context.graphics(), row.right() - 1, row.y(), row.height(), color);
            UiRenderer.rect(context.graphics(), UiBounds.of(row.x(), row.y(), 2, row.height()), color);
        }
        String label = bindingModule == module ? "Press key" : module.name();
        UiBounds bindButton = CompactKeybindButton.atRowEnd(row, 3, 2);
        int labelMax = Math.max(1, bindButton.x() - row.x() - 9);
        drawHighlighted(context, label, row.x() + 5, row.y() + 3, labelMax, module.isEnabled() ? theme.colors().text : theme.colors().muted,
            currentSearchQuery().trim().toLowerCase(Locale.ROOT));
        addHit(HitType.MODULE, row, module, null, null, SEARCH_WINDOW, -1);
        CompactKeybindButton.render(context, bindButton, module.keybind(), bindingModule == module, bindButton.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.MODULE_BIND, bindButton, module, null, null, SEARCH_WINDOW, -1);
        if (hovered && module.description() != null && !module.description().isBlank()) tooltip = module.description();
    }

    private void renderUtilityWindow(UiContext context) {
        if (PackHideState.isActive()) return;
        AutismConfig.ModuleCategoryLayout layout = layout(MAIN_MENU);
        int contentH = utilityContentHeight();
        UiBounds bounds = utilityWindowBounds(contentH);
        syncLayout(layout, bounds);
        UiBounds header = UiBounds.of(bounds.x(), bounds.y(), bounds.width(), theme.spacing().topBarHeight);
        CompactWindow.renderFrame(context, bounds, "Main Menu",
            layout.collapsed, false, header.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.WINDOW_HEADER, header, null, null, null, MAIN_MENU, -1);
        if (layout.collapsed) return;
        int bodyH = Math.max(0, bounds.height() - theme.spacing().topBarHeight - 1);
        UiBounds viewport = UiBounds.of(bounds.x() + 1, bounds.y() + theme.spacing().topBarHeight, bounds.width() - 2, bodyH);
        int scroll = clampWindowScroll(MAIN_MENU, contentH, viewport.height());
        if (contentH > viewport.height()) {
            viewport = UiBounds.of(viewport.x(), viewport.y(), viewport.width() - theme.spacing().scrollbarWidth, viewport.height());
        }
        addHit(HitType.SCROLL_AREA, viewport, null, null, null, MAIN_MENU, -1);
        int buttonW = Math.max(44, viewport.width() / 2);
        int y = viewport.y() - scroll;
        int col = 0;
        UtilityAction leftAction = null;
        UiBounds leftButton = null;
        float leftProgress = 0.0f;
        scissors.push(context.graphics(), viewport);
        try {
            List<UtilityAction> utilityActions = utilityActions();
            for (int i = 0; i < utilityActions.size(); i++) {
                UtilityAction action = utilityActions.get(i);
                if (action.category()) {
                    if (col != 0) {
                        y += theme.spacing().buttonHeight;
                        col = 0;
                        leftAction = null;
                        leftButton = null;
                        leftProgress = 0.0f;
                    }
                    UiBounds label = UiBounds.of(viewport.x(), y, viewport.width(), theme.spacing().buttonHeight);
                    if (label.bottom() >= viewport.y() && label.y() <= viewport.bottom()) renderUtilityCategory(context, label, action);
                    y += theme.spacing().buttonHeight;
                    continue;
                }
                boolean fullWidth = utilityFullWidth(action);
                if (fullWidth && col != 0) {
                    y += theme.spacing().buttonHeight;
                    col = 0;
                    leftAction = null;
                    leftButton = null;
                    leftProgress = 0.0f;
                }
                UiBounds button = fullWidth
                    ? UiBounds.of(viewport.x(), y, viewport.width(), theme.spacing().buttonHeight)
                    : UiBounds.of(viewport.x() + col * buttonW, y, col == 0 ? buttonW : viewport.width() - buttonW, theme.spacing().buttonHeight);
                boolean active = utilityActive(action.id);
                float activeProgress = action.toggle() ? animatedUtilityProgress(action.id, active, button.width()) : 0.0f;
                if (button.bottom() >= viewport.y() && button.y() <= viewport.bottom()) {
                    boolean hover = button.contains(context.mouseX(), context.mouseY());

                    if (action.toggle()) ConnectedButton.renderToggle(context, button, utilityLabel(action, active), action.icon, hover, activeProgress, ConnectedButton.FULL);
                    else ConnectedButton.renderAction(context, button, action.label, action.icon, action.tone, hover, ConnectedButton.FULL);
                    addHit(HitType.UTILITY, button, null, null, null, action.id, i);
                }
                if (fullWidth) {
                    y += theme.spacing().buttonHeight;
                    col = 0;
                    leftAction = null;
                    leftButton = null;
                    leftProgress = 0.0f;
                    continue;
                }
                if (col == 0) {
                    leftAction = action;
                    leftButton = button;
                    leftProgress = activeProgress;
                }
                col++;
                if (col >= 2) {
                    col = 0;
                    y += theme.spacing().buttonHeight;
                    leftAction = null;
                    leftButton = null;
                    leftProgress = 0.0f;
                }
            }
        } finally {
            scissors.pop(context.graphics());
        }
        if (contentH > viewport.height()) {
            UiBounds track = UiBounds.of(bounds.right() - theme.spacing().scrollbarWidth - 1, viewport.y(), theme.spacing().scrollbarWidth, viewport.height());
            Scrollbar.Metrics metrics = Scrollbar.metrics(track, contentH, viewport.height(), scroll);
            Scrollbar.render(context, metrics, metrics.track().contains(context.mouseX(), context.mouseY()),
                scrollbarDrag != null && MAIN_MENU.equals(scrollbarDrag.id));
            addHit(HitType.SCROLLBAR, scrollbarHitBounds(track), null, null, null, MAIN_MENU, -1);
        }
    }

    private void renderUtilityCategory(UiContext context, UiBounds bounds, UtilityAction action) {
        ConnectedButton.renderCategory(context, bounds, action.label, action.icon);
    }

    private void drawUtilitySeam(UiContext context, UtilityAction leftAction, float leftProgress,
                                 UtilityAction rightAction, float rightProgress, UiBounds rightButton) {
        int leftColor = leftAction.toggle()
            ? ConnectedButton.toggleBorderColor(context, leftProgress)
            : ConnectedButton.toneBorderColor(context, leftAction.tone);
        int rightColor = rightAction.toggle()
            ? ConnectedButton.toggleBorderColor(context, rightProgress)
            : ConnectedButton.toneBorderColor(context, rightAction.tone);
        float leftWeight = ConnectedButton.seamWeight(leftAction.tone, leftAction.toggle(), leftProgress);
        float rightWeight = ConnectedButton.seamWeight(rightAction.tone, rightAction.toggle(), rightProgress);
        ConnectedButton.drawVerticalSeam(context, rightButton.x(), rightButton.y(), rightButton.height(),
            ConnectedButton.chooseSeamColor(leftColor, leftWeight, rightColor, rightWeight));
    }

    private int utilityContentHeight() {
        int rows = 0;
        int col = 0;
        for (UtilityAction action : utilityActions()) {
            if (action.category()) {
                if (col != 0) {
                    rows++;
                    col = 0;
                }
                rows++;
                continue;
            }
            if (utilityFullWidth(action)) {
                if (col != 0) {
                    rows++;
                    col = 0;
                }
                rows++;
                continue;
            }
            col++;
            if (col >= 2) {
                rows++;
                col = 0;
            }
        }
        if (col != 0) rows++;
        return Math.max(theme.spacing().buttonHeight, rows * theme.spacing().buttonHeight);
    }

    private boolean utilityFullWidth(UtilityAction action) {
        return host.offlineSetup() && action != null && "keys".equals(action.id());
    }

    private List<UtilityAction> utilityActions() {
        return host.offlineSetup() ? OFFLINE_UTILITY_ACTIONS : UTILITY_ACTIONS;
    }

    private boolean utilityActive(String id) {
        AutismSharedState shared = AutismSharedState.get();
        return switch (id) {
            case "send" -> shared.shouldSendGuiPackets();
            case "delay" -> shared.shouldDelayGuiPackets();
            default -> false;
        };
    }

    private String utilityLabel(UtilityAction action, boolean active) {
        return action.label;
    }

    private float animatedUtilityProgress(String id, boolean active, int width) {
        String key = "utility:" + id;
        float target = active ? 1.0f : 0.0f;
        AnimatedValue value = enabledAnimations.get(key);
        long now = System.nanoTime();
        if (value == null) {
            value = new AnimatedValue(target, now);
            enabledAnimations.put(key, value);
            return target;
        }
        float delta = Math.max(0.0f, Math.min(0.05f, (now - value.lastNanos) / 1_000_000_000.0f));
        value.lastNanos = now;
        float step = delta / (AnimatedToggleButton.durationNanos(width) / 1_000_000_000.0f);
        if (value.value < target) value.value = Math.min(target, value.value + step);
        else if (value.value > target) value.value = Math.max(target, value.value - step);
        return value.value;
    }

    private void renderModuleRow(UiContext context, Module module, UiBounds row, ModuleCategory category, int index) {
        boolean hovered = row.contains(context.mouseX(), context.mouseY());
        float enabledProgress = animatedEnabledProgress(module);
        float hoverT = hoverFade("mod:" + (category == null ? "search" : category.name()) + ":" + module.id(), hovered);
        UiRenderer.rect(context.graphics(), row, lerpColor(theme.colors().row, theme.colors().rowHover, hoverT));
        if (enabledProgress > 0.001f) {
            int fillW = Math.max(1, Math.round(row.width() * enabledProgress));
            UiRenderer.rect(context.graphics(), UiBounds.of(row.x(), row.y(), fillW, row.height()), hovered ? alphaColor(theme.colors().accent, 0.34f) : alphaColor(theme.colors().accent, 0.22f));
            drawAnimatedEnabledOutline(context, row, category, index, enabledProgress);
        }
        String label = bindingModule == module ? "Press key" : module.name();
        UiBounds bindButton = CompactKeybindButton.atRowEnd(row, 3, 2);
        int labelMax = Math.max(1, bindButton.x() - row.x() - 9);
        context.text().drawFitted(context.graphics(), label, row.x() + 5, row.y() + 3, labelMax, module.isEnabled() ? theme.colors().text : theme.colors().muted);
        addHit(HitType.MODULE, row, module, null, category, "", index);
        CompactKeybindButton.render(context, bindButton, module.keybind(), bindingModule == module, bindButton.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.MODULE_BIND, bindButton, module, null, category, "", index);
        if (hovered && module.description() != null && !module.description().isBlank()) tooltip = module.description();
    }

    private void drawAnimatedEnabledOutline(UiContext context, UiBounds row, ModuleCategory category, int index, float progress) {
        int color = alphaColor(theme.colors().accent, progress);
        List<Module> modules = orderedModules(category);
        Module module = modules.get(index);
        int animatedW = Math.max(1, Math.round(row.width() * progress));
        if (shouldDrawTopEnabledEdge(category, modules, module, index, progress)) {
            UiRenderer.horizontalEdge(context.graphics(), row.x(), row.y(), animatedW, color);
        }
        if (shouldDrawBottomEnabledEdge(category, modules, module, index, progress)) {
            UiRenderer.horizontalEdge(context.graphics(), row.x(), row.bottom() - 1, animatedW, color);
        }
        UiRenderer.verticalEdge(context.graphics(), row.x(), row.y(), row.height(), color);
        UiRenderer.verticalEdge(context.graphics(), row.right() - 1, row.y(), row.height(), color);
        UiRenderer.rect(context.graphics(), UiBounds.of(row.x(), row.y(), 2, row.height()), color);
    }

    private boolean shouldDrawTopEnabledEdge(ModuleCategory category, List<Module> modules, Module module, int index, float progress) {
        if (index == 0) return false;
        if (index < 0) return true;
        Module previous = modules.get(index - 1);
        float previousProgress = enabledProgressSnapshot(previous);
        if (previousProgress <= 0.001f) return true;
        String owner = enabledSeparatorOwner(category, previous, module, previousProgress, progress);
        return module.id().equals(owner);
    }

    private boolean shouldDrawBottomEnabledEdge(ModuleCategory category, List<Module> modules, Module module, int index, float progress) {
        if (index + 1 >= modules.size()) return true;
        Module next = modules.get(index + 1);
        float nextProgress = enabledProgressSnapshot(next);
        if (nextProgress <= 0.001f) return true;
        String owner = enabledSeparatorOwner(category, module, next, progress, nextProgress);
        return module.id().equals(owner);
    }

    private String enabledSeparatorOwner(ModuleCategory category, Module upper, Module lower, float upperProgress, float lowerProgress) {
        String key = category.name() + "|" + upper.id() + "|" + lower.id();
        String owner = enabledSeparatorOwners.get(key);
        if (upperProgress <= 0.001f && lowerProgress <= 0.001f) {
            enabledSeparatorOwners.remove(key);
            return "";
        }
        if (owner != null) {
            if (owner.equals(upper.id()) && upperProgress > 0.001f) return owner;
            if (owner.equals(lower.id()) && lowerProgress > 0.001f) return owner;
        }
        String nextOwner = upperProgress >= lowerProgress ? upper.id() : lower.id();
        enabledSeparatorOwners.put(key, nextOwner);
        return nextOwner;
    }

    private void renderSettingsWindow(UiContext context, Module module) {
        int availableWidth = Math.max(1, screenWidth - 8);
        int availableHeight = Math.max(1, screenHeight - 12);
        int w = Math.min(SETTINGS_W, availableWidth);
        int buttonH = theme.spacing().buttonHeight;
        int buttonGap = 4;
        int toolbarW = Math.max(1, w - 10);
        boolean wrapToolbarActions = toolbarW < 88 + buttonGap + 50 + buttonGap + (52 + buttonGap + 52);
        boolean stackToolbarButtons = toolbarW < 92;
        int toolbarRows = stackToolbarButtons ? 4 : wrapToolbarActions ? 2 : 1;

        int chrome = theme.spacing().topBarHeight + 5 + toolbarRows * buttonH + (toolbarRows - 1) * buttonGap + 5 + buttonH + 10;
        CachedSettingRows fitRows = cachedRows(module, toolbarW);
        int bodyH = Math.min(fitRows.contentHeight, Math.max(42, availableHeight - chrome));
        int h = Math.min(availableHeight, chrome + Math.max(42, bodyH));
        int x = Math.max(4, (screenWidth - w) / 2);
        int y = Math.max(4, (screenHeight - h) / 2);
        UiBounds bounds = UiBounds.of(x, y, w, h).clampInside(screenWidth - 4, screenHeight - 4);
        UiBounds header = UiBounds.of(bounds.x(), bounds.y(), bounds.width(), theme.spacing().topBarHeight);
        CompactWindow.renderFrame(context, bounds, module.name(), false, true, header.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.SETTINGS_HEADER, header, module, null, null, "", -1);
        addHit(HitType.BACK, TopBar.closeButton(header), module, null, null, "", -1);
        int toolbarX = bounds.x() + 5;
        int toolbarY = bounds.y() + theme.spacing().topBarHeight + 5;
        int toolbarRight = toolbarX + toolbarW;
        int actionGroupW = 52 + buttonGap + 52;

        int activeW = stackToolbarButtons ? toolbarW : Math.min(88, Math.max(1, wrapToolbarActions ? (toolbarW - buttonGap) / 2 : 88));
        UiBounds activeButton = UiBounds.of(toolbarX, toolbarY, activeW, buttonH);

        Toggle.renderLabeled(context, activeButton, "Enabled", module.isEnabled(),
            activeButton.contains(context.mouseX(), context.mouseY()), "module:" + module.id());
        addHit(HitType.TOGGLE_MODULE, activeButton, module, null, null, "", -1);

        int bindX = stackToolbarButtons ? toolbarX : activeButton.right() + buttonGap;
        int bindY = stackToolbarButtons ? activeButton.bottom() + buttonGap : activeButton.y();
        int bindRight = stackToolbarButtons || wrapToolbarActions ? toolbarRight : toolbarRight - actionGroupW - buttonGap;
        UiBounds bindButton = UiBounds.of(bindX, bindY, Math.max(1, Math.min(88, bindRight - bindX)), buttonH);
        Button.render(context, bindButton, bindingModule == module ? "Press key" : "Bind " + AutismBindUtil.getBindName(module.keybind()), Button.Tone.NORMAL, bindButton.contains(context.mouseX(), context.mouseY()), bindingModule == module);
        addHit(HitType.BIND_MODULE, bindButton, module, null, null, "", -1);

        int actionY = stackToolbarButtons ? bindButton.bottom() + buttonGap : wrapToolbarActions ? activeButton.bottom() + buttonGap : toolbarY;
        int actionButtonW = stackToolbarButtons ? toolbarW : wrapToolbarActions ? Math.max(1, (toolbarW - buttonGap) / 2) : 52;
        UiBounds macroButton = UiBounds.of(toolbarRight - actionButtonW, actionY, actionButtonW, buttonH);
        UiBounds resetButton = stackToolbarButtons
            ? UiBounds.of(toolbarX, macroButton.bottom() + buttonGap, toolbarW, buttonH)
            : UiBounds.of(Math.max(toolbarX, macroButton.x() - buttonGap - actionButtonW), actionY, actionButtonW, buttonH);
        Button.render(context, resetButton, "Reset", Button.Tone.DANGER, resetButton.contains(context.mouseX(), context.mouseY()), false);
        addHit(HitType.RESET_MODULE_SETTINGS, resetButton, module, null, null, "", -1);
        Button.render(context, macroButton, "Macro", Button.Tone.NORMAL, macroButton.contains(context.mouseX(), context.mouseY()), false);
        addHit(HitType.MACRO, macroButton, module, null, null, "", -1);

        int toolbarBottom = Math.max(Math.max(activeButton.bottom(), bindButton.bottom()), Math.max(macroButton.bottom(), resetButton.bottom()));

        UiBounds searchField = UiBounds.of(bounds.x() + 5, toolbarBottom + 5, Math.max(1, bounds.width() - 10), buttonH);
        boolean searchFocused = isSettingsSearchEditing();
        String searchText = searchFocused ? editing.text : settingsSearchQuery;
        TextField.render(context, searchField, searchText, "Search settings...", searchFocused,
            searchFocused ? editing.cursor : searchText.length(),
            searchFocused ? editing.selectionStart() : -1,
            searchFocused ? editing.selectionEnd() : -1);
        addHit(HitType.SETTINGS_SEARCH_FIELD, searchField, module, null, null, "", -1);

        UiBounds body = UiBounds.of(bounds.x() + 5, searchField.bottom() + 5, Math.max(1, bounds.width() - 10), Math.max(1, bounds.bottom() - searchField.bottom() - 10));
        CachedSettingRows rows = cachedRows(module, body.width());
        int scroll = clampSettingsScroll(module, rows.contentHeight, body.height());
        UiBounds viewport = rows.contentHeight > body.height()
            ? UiBounds.of(body.x(), body.y(), body.width() - theme.spacing().scrollbarWidth - 2, body.height())
            : body;
        addHit(HitType.SCROLL_AREA, body, module, null, null, "settings", -1);
        scissors.push(context.graphics(), viewport);
        try {
            for (SettingRow row : rows.rows) {
                int drawY = viewport.y() - scroll + row.y;
                if (drawY + row.height <= viewport.y() || drawY >= viewport.bottom()) continue;
                if (row.group != null) renderGroupHeader(context, module, row.group, UiBounds.of(viewport.x(), drawY, viewport.width(), row.height));
                else renderSettingRow(context, module, row.option, UiBounds.of(viewport.x(), drawY, viewport.width(), row.height));
            }
        } finally {
            scissors.pop(context.graphics());
        }
        if (rows.contentHeight > body.height()) {
            UiBounds track = UiBounds.of(body.right() - theme.spacing().scrollbarWidth, body.y(), theme.spacing().scrollbarWidth, body.height());
            Scrollbar.Metrics metrics = Scrollbar.metrics(track, rows.contentHeight, body.height(), scroll);
            Scrollbar.render(context, metrics, metrics.track().contains(context.mouseX(), context.mouseY()),
                scrollbarDrag != null && "settings".equals(scrollbarDrag.id));
            addHit(HitType.SCROLLBAR, scrollbarHitBounds(track), module, null, null, "settings", -1);
        }
    }

    private void renderGroupHeader(UiContext context, Module module, String group, UiBounds bounds) {
        boolean collapsed = isGroupCollapsed(module, group);
        CollapsibleSection.renderHeader(context, bounds, group, collapsed, bounds.contains(context.mouseX(), context.mouseY()));
        addHit(HitType.GROUP, bounds, module, null, null, group, -1);
    }

    private void renderSettingRow(UiContext context, Module module, Setting<?, ?> option, UiBounds bounds) {
        boolean hovered = bounds.contains(context.mouseX(), context.mouseY());
        float hoverT = hoverFade("set:" + module.id() + ":" + option.id(), hovered);

        UiRenderer.rect(context.graphics(), bounds, lerpColor(theme.colors().row, theme.colors().rowHover, hoverT));
        boolean macroDisplay = option.displayMode() == DisplayMode.MACRO_PICKER
            || option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER;
        int controlW = macroDisplay
            ? Math.min(190, Math.max(132, bounds.width() / 2))
            : Math.min(132, Math.max(96, bounds.width() / 3));
        int labelW = Math.max(40, bounds.width() - controlW - 8);
        int labelY = bounds.y() + 4;
        String settingQuery = module == selectedModule ? currentSettingsSearch().trim().toLowerCase(Locale.ROOT) : "";
        drawHighlighted(context, option.label(), bounds.x() + 5, labelY, labelW, theme.colors().text, settingQuery);
        boolean descriptionShownInline = !option.description().isBlank() && bounds.height() > 24;
        if (descriptionShownInline) {
            drawHighlighted(context, option.description(), bounds.x() + 5, bounds.y() + 15, labelW, theme.colors().muted, settingQuery);
        }

        if (hovered && !descriptionShownInline && option.description() != null && !option.description().isBlank()) {
            tooltip = option.description();
        }
        UiBounds control = UiBounds.of(bounds.right() - controlW - 4, bounds.y() + Math.max(3, (bounds.height() - theme.spacing().buttonHeight) / 2), controlW, theme.spacing().buttonHeight);
        if (option.displayMode() == DisplayMode.FILE_PICKER) {
            renderSummaryActionRow(context, module, option, control, "Pick", Button.Tone.PRIMARY, HitType.FILE_PICKER);
            return;
        }
        if (option.displayMode() == DisplayMode.READONLY_SUMMARY) {
            renderSummaryOnly(context, module, option, control);
            return;
        }
        if (option.displayMode() == DisplayMode.MACRO_PICKER
            || option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER) {
            renderSummaryActionRow(context, module, option, control, "Select", Button.Tone.NORMAL, HitType.MACRO_PICKER);
            return;
        }
        switch (option.kind()) {
            case BOOLEAN -> {
                UiBounds toggle = UiBounds.of(control.right() - 34, control.y(), 34, control.height());
                Toggle.render(context, toggle, Boolean.parseBoolean(module.value(option.id())),
                    hovered || toggle.contains(context.mouseX(), context.mouseY()),
                    module.id() + "/" + option.id());
                addHit(HitType.OPTION, bounds, module, option, null, "", -1);
            }
            case INTEGER, DOUBLE -> renderNumericOption(context, module, option, control);
            case ENUM -> {
                Dropdown.renderControl(context, control, module.displayValue(option), control.contains(context.mouseX(), context.mouseY()), dropdownModule == module && dropdownOption == option);
                addHit(HitType.DROPDOWN, control, module, option, null, "", -1);
            }
            case STRING -> {
                boolean focused = editing != null && editing.module == module && editing.option == option;
                TextField.render(context, control, focused ? editing.text : module.value(option.id()), "<empty>", focused,
                    focused ? editing.cursor : module.value(option.id()).length(),
                    focused ? editing.selectionStart() : -1,
                    focused ? editing.selectionEnd() : -1);
                addHit(HitType.TEXT_FIELD, control, module, option, null, "", -1);
            }
            case COLOR -> renderColorOption(context, module, option, control);
            case STRING_LIST, ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST -> {
                UiBounds edit = UiBounds.of(control.right() - 38, control.y(), 38, control.height());
                UiBounds value = UiBounds.of(control.x(), control.y(), Math.max(1, control.width() - 42), control.height());
                drawSummaryValue(context, module, option, value);
                Button.render(context, edit, "Edit", Button.Tone.NORMAL, edit.contains(context.mouseX(), context.mouseY()), false);
                addHit(HitType.LIST_EDITOR, edit, module, option, null, "", -1);
            }
            case PACKET_LIST -> {
                UiBounds edit = UiBounds.of(control.right() - 44, control.y(), 44, control.height());
                UiBounds value = UiBounds.of(control.x(), control.y(), Math.max(1, control.width() - 48), control.height());
                drawSummaryValue(context, module, option, value);
                Button.render(context, edit, "Pick", Button.Tone.PRIMARY, edit.contains(context.mouseX(), context.mouseY()), false);
                addHit(HitType.PACKET_EDITOR, edit, module, option, null, "", -1);
            }
            case KEYBIND -> {
                UiBounds bind = UiBounds.of(control.x(), control.y(), Math.max(46, control.width() - 42), control.height());
                UiBounds clear = UiBounds.of(bind.right() + 4, control.y(), 38, control.height());
                boolean focused = bindingOptionModule == module && bindingOption == option;
                Button.render(context, bind, focused ? "Press" : AutismBindUtil.getBindName(parseInt(module.value(option.id()), -1)), Button.Tone.NORMAL, bind.contains(context.mouseX(), context.mouseY()), focused);
                Button.render(context, clear, "Clear", Button.Tone.NORMAL, clear.contains(context.mouseX(), context.mouseY()), false);
                addHit(HitType.BIND_OPTION, bind, module, option, null, "", -1);
                addHit(HitType.RESET_OPTION_BIND, clear, module, option, null, "", -1);
            }
            case ACTION -> {
                String buttonLabel = option instanceof ActionSetting action ? action.buttonLabel() : "Run";
                Button.render(context, control, buttonLabel, Button.Tone.PRIMARY, control.contains(context.mouseX(), context.mouseY()), false);
                addHit(HitType.OPTION, control, module, option, null, "", -1);
            }
        }
    }

    private void renderNumericOption(UiContext context, Module module, Setting<?, ?> option, UiBounds control) {
        if (option.displayMode() == DisplayMode.NUMERIC_TEXT_FIELD) {
            boolean focused = editing != null && editing.module == module && editing.option == option;
            TextField.render(context, control, focused ? editing.text : module.value(option.id()), "", focused,
                focused ? editing.cursor : module.value(option.id()).length(),
                focused ? editing.selectionStart() : -1,
                focused ? editing.selectionEnd() : -1);
            addHit(HitType.TEXT_FIELD, control, module, option, null, "", -1);
            return;
        }
        String unit = option.unit() == null ? "" : option.unit();
        int unitW = unit.isEmpty() ? 0 : context.text().width(unit) + 4;
        int valueW = Math.min(48, Math.max(38, control.width() / 3));
        UiBounds slider = UiBounds.of(control.x(), control.y(), Math.max(36, control.width() - valueW - 4 - unitW), control.height());
        UiBounds value = UiBounds.of(slider.right() + 4, control.y(), valueW, control.height());
        double current = option.kind() == Kind.INTEGER ? parseInt(module.value(option.id()), parseInt(option.defaultValue(), 0)) : parseDouble(module.value(option.id()), parseDouble(option.defaultValue(), 0.0));
        double ratio = (current - option.sliderMin()) / Math.max(0.0001, option.sliderMax() - option.sliderMin());
        Slider.render(context, slider, ratio, slider.contains(context.mouseX(), context.mouseY()));
        boolean focused = editing != null && editing.module == module && editing.option == option;
        TextField.render(context, value, focused ? editing.text : module.value(option.id()), "", focused,
            focused ? editing.cursor : module.value(option.id()).length(),
            focused ? editing.selectionStart() : -1,
            focused ? editing.selectionEnd() : -1);
        if (!unit.isEmpty()) {
            context.text().drawTrimmed(context.graphics(), unit, value.right() + 3, context.text().centeredY(value), unitW, context.theme().colors().text);
        }
        addHit(HitType.SLIDER, slider, module, option, null, "", -1);
        addHit(HitType.TEXT_FIELD, value, module, option, null, "", -1);
    }

    private void renderColorOption(UiContext context, Module module, Setting<?, ?> option, UiBounds control) {
        int pickW = 38;
        UiBounds pick = UiBounds.of(control.right() - pickW, control.y(), pickW, control.height());
        UiBounds value = UiBounds.of(control.x(), control.y(), Math.max(1, control.width() - pickW - 4), control.height());
        int color = parseColor(module.value(option.id()), parseColor(option.defaultValue(), 0xFFFFFFFF));
        UiRenderer.frame(context.graphics(), value, context.theme().colors().field, context.theme().colors().borderSoft);
        UiBounds swatch = UiBounds.of(value.x() + 3, value.y() + 3, 14, Math.max(1, value.height() - 6));
        UiRenderer.frame(context.graphics(), swatch, color | 0xFF000000, context.theme().colors().borderSoft);
        String rgb = String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
        context.text().drawFitted(context.graphics(), rgb, swatch.right() + 5, context.text().centeredY(value), Math.max(1, value.right() - swatch.right() - 8), context.theme().colors().text);
        Button.render(context, pick, "Pick", Button.Tone.NORMAL, pick.contains(context.mouseX(), context.mouseY()), colorPicker != null);
        addHit(HitType.COLOR_PICKER, pick, module, option, null, "", -1);
        addHit(HitType.COLOR_PICKER, value, module, option, null, "", -1);
    }

    private void renderDropdown(UiContext context, int mouseX, int mouseY) {
        if (dropdown == null) return;
        context.graphics().nextStratum();
        dropdown.render(context);
    }

    private void renderColorPicker(UiContext context, int mouseX, int mouseY) {
        if (colorPicker == null) return;
        context.graphics().nextStratum();
        colorPicker.render(context);
        if (!colorPicker.isOpen()) clearColorPicker();
    }

    private void renderMacroPicker(UiContext context, int mouseX, int mouseY) {
        if (macroPicker == null) return;
        context.graphics().nextStratum();
        macroPicker.render(context, mouseX, mouseY);
    }

    private void clearDropdown() {
        if (dropdown != null) dropdown.close();
        dropdown = null;
        dropdownModule = null;
        dropdownOption = null;
    }

    private void openColorPicker(Module module, Setting<?, ?> option, UiBounds anchor) {
        if (module == null || option == null) return;
        clearDropdown();
        clearMacroPicker();
        finishEditing(true);
        int fallback = parseColor(option.defaultValue(), 0xFFFFFFFF);
        int initial = parseColor(module.value(option.id()), fallback);
        colorPicker = new ColorPicker(anchor, initial, fallback, screenWidth, screenHeight, argb -> {
            setOptionValue(module, option, String.format(Locale.ROOT, "%08X", argb));
            invalidateSettings(module);
        });
    }

    private void clearColorPicker() {
        if (colorPicker != null) colorPicker.closeCancel();
        colorPicker = null;
    }

    private void openMacroPicker(Module module, Setting<?, ?> option, UiBounds anchor) {
        if (module == null || option == null) return;
        clearDropdown();
        clearColorPicker();
        finishEditing(true);
        macroPicker = new MacroPicker(module, option, anchor);
    }

    private void clearMacroPicker() {
        macroPicker = null;
    }

    private void handleMacroPickerKey(int key) {
        if (macroPicker == null) return;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> clearMacroPicker();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!macroPicker.query.isEmpty()) {
                    macroPicker.setQuery(macroPicker.query.substring(0, macroPicker.query.length() - 1));
                }
            }
            default -> {
            }
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (handleBindMouse(button)) return true;
        if (dropdown != null) {
            dropdown.mouseClicked(mx, my, button);
            if (!dropdown.isOpen()) clearDropdown();
            return true;
        }
        if (colorPicker != null) {
            colorPicker.mouseClicked(mx, my, button);
            if (!colorPicker.isOpen()) colorPicker = null;
            return true;
        }
        if (macroPicker != null) {
            if (!macroPicker.mouseClicked(mx, my, button)) clearMacroPicker();
            return true;
        }
        finishEditing(true);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds.contains(mx, my)) continue;
            switch (hit.type) {
                case SEARCH_FIELD -> {
                    bringWindowToFront(SEARCH_WINDOW);
                    beginSearchEditing(mx, hit.bounds);
                    return true;
                }
                case SETTINGS_SEARCH_FIELD -> {
                    beginSettingsSearchEditing(mx, hit.bounds);
                    return true;
                }
                case CATEGORY_RESIZE -> {
                    bringWindowToFront(hit.id);
                    resizingCategoryId = hit.id;
                    return true;
                }
                case WINDOW_HEADER -> {
                    bringWindowToFront(hit.id);
                    draggingWindowId = hit.id;
                    AutismConfig.ModuleCategoryLayout layout = layout(hit.id);
                    dragOffsetX = mx - layout.x;
                    dragOffsetY = my - layout.y;
                    dragStartX = mx;
                    dragStartY = my;
                    draggingMoved = false;
                    return true;
                }
                case MODULE -> {
                    bringWindowToFront(hit.category != null ? hit.category.name() : SEARCH_WINDOW);
                    if (button == 1) openSettings(hit.module);
                    else if (button == 2) bindingModule = hit.module;
                    else if (button == 0) {

                        if (hasVisibleSettings(hit.module) && (hit.module.opensSettingsOnClick() || !hit.module.hasActivationToggle())) openSettings(hit.module);
                        else toggleModule(hit.module);
                    }
                    return true;
                }
                case MODULE_BIND -> {
                    if (button == 0) bindingModule = hit.module;
                    return true;
                }
                case UTILITY -> {
                    if (button == 0) host.runUtility(hit.id);
                    return true;
                }
                case BACK -> {
                    selectedModule = null;
                    return true;
                }
                case TOGGLE_MODULE -> {
                    if (hit.module.hasActivationToggle()) toggleModule(hit.module);
                    return true;
                }
                case BIND_MODULE -> {
                    bindingModule = hit.module;
                    return true;
                }
                case MACRO -> {
                    Module module = hit.module;
                    selectedModule = null;
                    host.addToggleMacro(module);
                    return true;
                }
                case RESET_MODULE_SETTINGS -> {
                    if (host.offlineSetup()) hit.module.resetConfiguredSettings();
                    else hit.module.resetSettings();
                    invalidateSettings(hit.module);
                    settingsScroll.put(hit.module, 0);
                    if (bindingOptionModule == hit.module) {
                        bindingOptionModule = null;
                        bindingOption = null;
                    }
                    AutismClientMessaging.sendPrefixed(hit.module.name() + " settings reset to defaults.");
                    return true;
                }
                case GROUP -> {
                    setGroupCollapsed(hit.module, hit.id, !isGroupCollapsed(hit.module, hit.id));
                    invalidateSettings(hit.module);
                    return true;
                }
                case OPTION -> {
                    activateOption(hit.module, hit.option, button);
                    return true;
                }
                case FILE_PICKER -> {
                    if (hit.option != null && hit.option.action() != null) {
                        hit.option.action().run();
                        invalidateSettings(hit.module);
                    }
                    return true;
                }
                case SLIDER -> {
                    sliderModule = hit.module;
                    sliderOption = hit.option;
                    updateSlider(mx);
                    return true;
                }
                case TEXT_FIELD -> {
                    beginEditing(hit.module, hit.option, mx, hit.bounds);
                    return true;
                }
                case DROPDOWN -> {
                    Module module = hit.module;
                    Setting<?, ?> option = hit.option;
                    if (module == null || option == null) {
                        clearDropdown();
                        return true;
                    }
                    scrollbarDrag = null;
                    sliderModule = null;
                    sliderOption = null;
                    draggingWindowId = null;
                    draggingMoved = false;
                    clearColorPicker();
                    clearMacroPicker();
                    dropdownModule = module;
                    dropdownOption = option;
                    dropdown = new Dropdown(hit.bounds, option.choices(), module.value(option.id()), value -> {
                        if (dropdownModule != module || dropdownOption != option) return;
                        setOptionValue(module, option, value);
                        invalidateSettings(module);
                    });
                    dropdown.open();
                    return true;
                }
                case COLOR_PICKER -> {
                    openColorPicker(hit.module, hit.option, hit.bounds);
                    return true;
                }
                case LIST_EDITOR -> {
                    openListEditor(hit.module, hit.option);
                    return true;
                }
                case PACKET_EDITOR -> {
                    host.openPacketSelector(hit.module, hit.option);
                    return true;
                }
                case MACRO_PICKER -> {
                    openMacroPicker(hit.module, hit.option, hit.bounds);
                    return true;
                }
                case BIND_OPTION -> {
                    bindingOptionModule = hit.module;
                    bindingOption = hit.option;
                    return true;
                }
                case RESET_OPTION_BIND -> {
                    setOptionValue(hit.module, hit.option, "-1");
                    invalidateSettings(hit.module);
                    return true;
                }
                case SCROLLBAR -> {
                    Scrollbar.Metrics metrics = metricsForHit(hit);
                    if (metrics != null && metrics.maxScroll() > 0) {
                        int grab = metrics.overThumb(mx, my) ? my - metrics.thumb().y() : metrics.thumb().height() / 2;
                        scrollbarDrag = new DragScrollbar(hit.id, grab);
                        setScroll(hit, Scrollbar.scrollFromMouse(metrics, my, grab));
                    }
                    return true;
                }
                default -> {
                }
            }
        }
        return true;
    }

    public boolean mouseReleased(int mx, int my, int button) {
        Module releasedSliderModule = sliderModule;
        draggingTextSelection = false;
        scrollbarDrag = null;
        sliderModule = null;
        sliderOption = null;
        if (releasedSliderModule != null) releasedSliderModule.persistConfiguredState();
        if (colorPicker != null) {
            colorPicker.mouseReleased(mx, my, button);
            if (!colorPicker.isOpen()) colorPicker = null;
            return true;
        }
        if (dropdown != null) {
            dropdown.mouseReleased(mx, my, button);
            if (!dropdown.isOpen()) clearDropdown();
            return true;
        }
        if (macroPicker != null) {
            macroPicker.mouseReleased(mx, my, button);
            return true;
        }
        if (button == 0 && resizingCategoryId != null) {
            resizingCategoryId = null;
            host.saveConfig();
        }
        if (button == 0 && draggingWindowId != null) {
            if (!draggingMoved) {
                AutismConfig.ModuleCategoryLayout layout = layout(draggingWindowId);
                layout.collapsed = !layout.collapsed;
            }
            draggingWindowId = null;
            draggingMoved = false;
            host.saveConfig();
        }
        return true;
    }

    public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
        if (colorPicker != null) {
            colorPicker.mouseDragged(mx, my, button, dx, dy);
            if (!colorPicker.isOpen()) colorPicker = null;
            return true;
        }
        if (dropdown != null) {
            dropdown.mouseDragged(mx, my, button, dx, dy);
            return true;
        }
        if (macroPicker != null) {
            macroPicker.mouseDragged(mx, my, button, dx, dy);
            return true;
        }

        if (draggingTextSelection && button == 0 && editing != null && editingFieldBounds != null) {
            moveEditingCursor(cursorAtX(editing.text, mx, editingFieldBounds), true);
            return true;
        }
        if (scrollbarDrag != null) {
            Hit hit = findScrollbarHit(scrollbarDrag.id);
            if (hit != null) {
                Scrollbar.Metrics metrics = metricsForHit(hit);
                if (metrics != null) setScroll(hit, Scrollbar.scrollFromMouse(metrics, my, scrollbarDrag.grabOffset));
            }
            return true;
        }
        if (resizingCategoryId != null) {
            AutismConfig.ModuleCategoryLayout layout = layout(resizingCategoryId);
            int rowH = Math.max(1, theme.spacing().rowHeight);
            int bodyTop = layout.y + theme.spacing().topBarHeight;
            int maxRows = Math.max(1, (screenHeight - 8 - theme.spacing().topBarHeight) / rowH);
            layout.visibleRows = clamp((my - bodyTop + rowH / 2) / rowH, 1, maxRows);
            return true;
        }
        if (draggingWindowId != null) {
            AutismConfig.ModuleCategoryLayout layout = layout(draggingWindowId);
            if (!draggingMoved && (Math.abs(mx - dragStartX) >= 3 || Math.abs(my - dragStartY) >= 3)) draggingMoved = true;
            int w = windowWidth(draggingWindowId);
            layout.x = clamp(mx - dragOffsetX, 0, Math.max(0, screenWidth - Math.min(w, screenWidth)));
            layout.y = clamp(my - dragOffsetY, 0, Math.max(0, screenHeight - theme.spacing().topBarHeight));
            return true;
        }
        if (sliderModule != null && sliderOption != null) {
            updateSlider(mx);
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(int mx, int my, double amount) {
        if (colorPicker != null) {
            colorPicker.mouseScrolled(mx, my, amount);
            return true;
        }
        if (dropdown != null) {
            dropdown.mouseScrolled(mx, my, amount);
            return true;
        }
        if (macroPicker != null) {
            macroPicker.mouseScrolled(mx, my, amount);
            return true;
        }
        Hit target = scrollTargetAt(mx, my);
        if (target != null) {
            String scrollId = selectedModule != null ? "settings" : (target.category != null ? target.category.name() : target.id);
            if (scrollId != null && !scrollId.isBlank()) {
                scrollBy(scrollId, selectedModule, amount < 0 ? theme.spacing().rowHeight * 2 : -theme.spacing().rowHeight * 2);
            }
            return true;
        }
        return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (bindingModule != null) {
            bindingModule.setKeybind(CompactKeybindButton.keyOrClear(key));
            bindingModule = null;
            bindConsumedAtNanos = System.nanoTime();
            return true;
        }
        if (bindingOptionModule != null && bindingOption != null) {
            setOptionValue(bindingOptionModule, bindingOption, Integer.toString(CompactKeybindButton.keyOrClear(key)));
            invalidateSettings(bindingOptionModule);
            bindingOptionModule = null;
            bindingOption = null;
            bindConsumedAtNanos = System.nanoTime();
            return true;
        }

        if (key == GLFW.GLFW_KEY_F && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            focusSearchWindow();
            return true;
        }
        if (editing != null) {
            handleEditingKey(key, modifiers);
            return true;
        }
        if (colorPicker != null) {
            colorPicker.keyPressed(key, scanCode, modifiers);
            if (!colorPicker.isOpen()) colorPicker = null;
            return true;
        }
        if (macroPicker != null) {
            handleMacroPickerKey(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (dropdown != null) clearDropdown();
            else if (macroPicker != null) clearMacroPicker();
            else if (selectedModule != null) selectedModule = null;
            else host.closeMenu();
            return true;
        }
        if (shouldReserveModifierForMacroEditor(key)) {
            return true;
        }
        if (key == AutismConfig.getGlobal().keybindModuleMenu || key == GLFW.GLFW_KEY_E) {

            if (dropdown == null && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return false;
            if (selectedModule != null) selectedModule = null;
            else host.closeMenu();
            return true;
        }
        return false;
    }

    private boolean shouldReserveModifierForMacroEditor(int key) {
        int menuBind = AutismConfig.getGlobal().keybindModuleMenu;
        if (key != menuBind) return false;
        if (key != GLFW.GLFW_KEY_LEFT_CONTROL
            && key != GLFW.GLFW_KEY_RIGHT_CONTROL
            && key != GLFW.GLFW_KEY_LEFT_SHIFT
            && key != GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return false;
        }
        return AutismSharedState.get().isMacroEditorVisible();
    }

    public boolean charTyped(char chr) {

        if (bindConsumedAtNanos != 0L && System.nanoTime() - bindConsumedAtNanos < 250_000_000L) {
            bindConsumedAtNanos = 0L;
            return true;
        }
        if (colorPicker != null) {
            colorPicker.charTyped(chr);
            if (!colorPicker.isOpen()) colorPicker = null;
            return true;
        }
        if (macroPicker != null) {
            if (chr >= 32 && chr != 127) macroPicker.setQuery(macroPicker.query + chr);
            return true;
        }
        if (editing == null) {

            if (dropdown == null && !autismclient.util.AutismOverlayManager.get().hasVisibleOverlay()
                    && (chr >= 'a' && chr <= 'z' || chr >= 'A' && chr <= 'Z')) {
                if (selectedModule != null) {
                    editing = new Editing(selectedModule, null, settingsSearchQuery, settingsSearchQuery.length());
                } else {
                    focusSearchWindow();
                    editing = new Editing(null, null, searchQuery, searchQuery.length());
                }
                replaceEditingSelection(Character.toString(chr));
                return true;
            }
            return false;
        }
        if (chr >= 32 && chr != 127) {
            replaceEditingSelection(Character.toString(chr));
        }
        return true;
    }

    private boolean handleBindMouse(int button) {
        if (!AutismBindUtil.isAllowedMouseButton(button)) return false;
        if (bindingModule != null) {
            bindingModule.setKeybind(AutismBindUtil.encodeMouseButton(button));
            bindingModule = null;
            return true;
        }
        if (bindingOptionModule != null && bindingOption != null) {
            setOptionValue(bindingOptionModule, bindingOption, Integer.toString(AutismBindUtil.encodeMouseButton(button)));
            invalidateSettings(bindingOptionModule);
            bindingOptionModule = null;
            bindingOption = null;
            return true;
        }
        return false;
    }

    private void activateOption(Module module, Setting<?, ?> option, int button) {
        if (module == null || option == null) return;
        switch (option.kind()) {
            case BOOLEAN, ACTION -> {
                if (host.offlineSetup()) module.adjustConfiguredOption(option, button == 1 ? -1 : 1);
                else module.adjustOption(option, button == 1 ? -1 : 1);
            }
            default -> {
            }
        }
        invalidateSettings(module);
    }

    private void beginEditing(Module module, Setting<?, ?> option, int mx, UiBounds bounds) {
        if (module == null || option == null
            || option.kind() == Kind.KEYBIND
            || option.kind() == Kind.ACTION
            || option.kind() == Kind.COLOR) return;
        if (option.displayMode() == DisplayMode.FILE_PICKER
            || option.displayMode() == DisplayMode.READONLY_SUMMARY
            || option.displayMode() == DisplayMode.MACRO_PICKER
            || option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER) return;
        String value = module.value(option.id());
        editing = new Editing(module, option, value, cursorAtX(value, mx, bounds));
        beginTextDrag(bounds);
    }

    private void handleEditingKey(int key, int modifiers) {
        if (editing == null) return;
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finishEditing(true);
            case GLFW.GLFW_KEY_ESCAPE -> finishEditing(true);
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) editing.selectAll();
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) copyEditingSelection();
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl) {
                    copyEditingSelection();
                    deleteEditingSelection();
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) pasteEditingClipboard();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteEditingSelection() && editing.cursor > 0) {
                    int from = ctrl ? previousWordBoundary(editing.text, editing.cursor) : editing.cursor - 1;
                    replaceEditingRange(from, editing.cursor, "");
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteEditingSelection() && editing.cursor < editing.text.length()) {
                    int to = ctrl ? nextWordBoundary(editing.text, editing.cursor) : editing.cursor + 1;
                    replaceEditingRange(editing.cursor, to, "");
                }
            }
            case GLFW.GLFW_KEY_LEFT -> moveEditingCursor(ctrl ? previousWordBoundary(editing.text, editing.cursor) : editing.cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> moveEditingCursor(ctrl ? nextWordBoundary(editing.text, editing.cursor) : editing.cursor + 1, shift);
            case GLFW.GLFW_KEY_HOME -> moveEditingCursor(0, shift);
            case GLFW.GLFW_KEY_END -> moveEditingCursor(editing.text.length(), shift);
            default -> {
            }
        }
    }

    private void moveEditingCursor(int cursor, boolean extendSelection) {
        if (editing == null) return;
        if (extendSelection && !editing.hasSelection()) editing.selectionAnchor = editing.cursor;
        editing.cursor = clamp(cursor, 0, editing.text.length());
        if (!extendSelection) editing.clearSelection();
    }

    private boolean deleteEditingSelection() {
        if (editing == null || !editing.hasSelection()) return false;
        replaceEditingRange(editing.selectionStart(), editing.selectionEnd(), "");
        return true;
    }

    private void replaceEditingSelection(String replacement) {
        if (editing == null) return;
        int start = editing.hasSelection() ? editing.selectionStart() : editing.cursor;
        int end = editing.hasSelection() ? editing.selectionEnd() : editing.cursor;
        replaceEditingRange(start, end, sanitizeSingleLineClipboard(replacement));
    }

    private void replaceEditingRange(int start, int end, String replacement) {
        if (editing == null) return;
        int safeStart = clamp(start, 0, editing.text.length());
        int safeEnd = clamp(Math.max(safeStart, end), safeStart, editing.text.length());
        String safe = replacement == null ? "" : replacement;
        editing.text = editing.text.substring(0, safeStart) + safe + editing.text.substring(safeEnd);
        editing.cursor = safeStart + safe.length();
        editing.clearSelection();
    }

    private void copyEditingSelection() {
        if (editing == null || !editing.hasSelection()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.keyboardHandler == null) return;
        mc.keyboardHandler.setClipboard(editing.text.substring(editing.selectionStart(), editing.selectionEnd()));
    }

    private void pasteEditingClipboard() {
        if (editing == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.keyboardHandler == null) return;
        String clipboard = mc.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) return;
        replaceEditingSelection(clipboard);
    }

    private String sanitizeSingleLineClipboard(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') out.append(' ');
            else if (c >= 32 && c != 127) out.append(c);
        }
        return out.toString();
    }

    private int previousWordBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int i = clamp(index, 0, value.length());
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
        if (i > 0 && isWordChar(value.charAt(i - 1))) {
            while (i > 0 && isWordChar(value.charAt(i - 1))) i--;
        } else {
            while (i > 0 && !Character.isWhitespace(value.charAt(i - 1)) && !isWordChar(value.charAt(i - 1))) i--;
        }
        return i;
    }

    private int nextWordBoundary(String text, int index) {
        String value = text == null ? "" : text;
        int i = clamp(index, 0, value.length());
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        if (i < value.length() && isWordChar(value.charAt(i))) {
            while (i < value.length() && isWordChar(value.charAt(i))) i++;
        } else {
            while (i < value.length() && !Character.isWhitespace(value.charAt(i)) && !isWordChar(value.charAt(i))) i++;
        }
        return i;
    }

    private boolean isWordChar(char chr) {
        return Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ':' || chr == '.';
    }

    private void finishEditing(boolean save) {
        if (editing == null) return;
        Editing current = editing;
        editing = null;
        editingFieldBounds = null;
        draggingTextSelection = false;
        if (current.option == null) {

            if (current.module == null) searchQuery = save ? current.text : "";
            else settingsSearchQuery = save ? current.text : "";
            return;
        }
        if (!save) return;
        Setting<?, ?> option = current.option;
        String next = current.text;
        if (option.kind() == Kind.INTEGER) {
            try {
                Integer.parseInt(next);
            } catch (NumberFormatException ex) {
                AutismClientMessaging.sendPrefixed("Invalid number for " + option.label() + ".");
                return;
            }
        } else if (option.kind() == Kind.DOUBLE) {
            try {
                Double.parseDouble(next);
            } catch (NumberFormatException ex) {
                AutismClientMessaging.sendPrefixed("Invalid number for " + option.label() + ".");
                return;
            }
        } else if (option.kind() == Kind.COLOR && parseColor(next, -1) == -1) {
            AutismClientMessaging.sendPrefixed("Invalid color: use RRGGBB or AARRGGBB.");
            return;
        }
        setOptionValue(current.module, option, next);
        invalidateSettings(current.module);
    }

    private void updateSlider(int mx) {
        if (sliderModule == null || sliderOption == null) return;
        Hit hit = findHit(HitType.SLIDER, sliderModule, sliderOption);
        if (hit == null) return;
        double ratio = (mx - hit.bounds.x()) / (double) Math.max(1, hit.bounds.width());
        ratio = Math.max(0, Math.min(1, ratio));
        double value = sliderOption.sliderMin() + ratio * (sliderOption.sliderMax() - sliderOption.sliderMin());
        double step = Math.max(0.0001, sliderOption.step());
        value = sliderOption.sliderMin() + Math.round((value - sliderOption.sliderMin()) / step) * step;
        if (sliderOption.kind() == Kind.INTEGER) setOptionValueTransient(sliderModule, sliderOption, Integer.toString((int) Math.round(value)));
        else setOptionValueTransient(sliderModule, sliderOption, String.format(Locale.ROOT, "%.2f", value));
        invalidateSettings(sliderModule);
    }

    private void openListEditor(Module module, Setting<?, ?> option) {
        if (module == null || option == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        switch (option.kind()) {
            case STRING_LIST -> host.openStringListEditor(module, option);
            case ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST -> host.openRegistryListEditor(module, option);
            default -> {
            }
        }
    }

    private void openSettings(Module module) {
        if (module == null) return;

        if (!hasVisibleSettings(module)) {
            autismclient.util.AutismNotifications.show(module.name() + " has no settings", 0xFFFFC857);
            return;
        }

        settingsSearchQuery = "";
        if (isSettingsSearchEditing()) {
            editing = null;
            editingFieldBounds = null;
            draggingTextSelection = false;
        }
        selectedModule = module;
    }

    private boolean hasVisibleSettings(Module module) {
        for (Setting<?, ?> option : module.settings()) {
            if (option.isVisible() && optionAvailable(option)) return true;
        }
        return false;
    }

    private final java.util.Map<String, HoverFade> hoverFades = new java.util.HashMap<>();

    private float hoverFade(String key, boolean hovered) {
        long now = System.nanoTime();
        HoverFade fade = hoverFades.get(key);
        if (fade == null) {
            fade = new HoverFade();
            fade.value = hovered ? 1.0f : 0.0f;
            fade.target = hovered;
            fade.since = now;
            hoverFades.put(key, fade);
            return fade.value;
        }
        if (fade.target != hovered) {
            fade.start = fade.value(now);
            fade.target = hovered;
            fade.since = now;
        }
        return fade.value(now);
    }

    private static final class HoverFade {
        float start;
        float value;
        boolean target;
        long since;

        float value(long now) {
            float elapsed = Math.min(1.0f, (now - since) / 90_000_000.0f);
            float eased = elapsed * elapsed * (3.0f - 2.0f * elapsed);
            return start + ((target ? 1.0f : 0.0f) - start) * eased;
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF;
        return 0xFF000000
            | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8)
            | Math.round(ab + (bb - ab) * t);
    }

    private void drawHighlighted(UiContext context, String text, int x, int y, int maxWidth, int baseColor, String queryLower) {
        String value = text == null ? "" : text;
        int index = queryLower == null || queryLower.isEmpty() ? -1 : value.toLowerCase(Locale.ROOT).indexOf(queryLower);
        if (index < 0 || context.text().width(value) > maxWidth) {
            context.text().drawFitted(context.graphics(), value, x, y, maxWidth, baseColor);
            return;
        }
        String pre = value.substring(0, index);
        String mid = value.substring(index, index + queryLower.length());
        String post = value.substring(index + queryLower.length());
        context.text().draw(context.graphics(), pre, x, y, baseColor);
        int midX = x + context.text().width(pre);
        context.text().draw(context.graphics(), mid, midX, y, theme.colors().accent);
        context.text().draw(context.graphics(), post, midX + context.text().width(mid), y, baseColor);
    }

    private CachedSettingRows cachedRows(Module module, int width) {
        long signature = settingSignature(module, width);
        CachedSettingRows cached = settingRowsCache.get(module.id());
        if (cached != null && cached.signature == signature) return cached;
        List<SettingRow> rows = new ArrayList<>();
        int y = 0;

        boolean searching = module == selectedModule && !currentSettingsSearch().trim().isEmpty();
        Map<String, List<Setting<?, ?>>> grouped = groupedOptions(module);
        for (Map.Entry<String, List<Setting<?, ?>>> entry : grouped.entrySet()) {
            rows.add(SettingRow.group(entry.getKey(), y, theme.spacing().topBarHeight));
            y += theme.spacing().topBarHeight + 2;
            if (!searching && isGroupCollapsed(module, entry.getKey())) continue;
            for (Setting<?, ?> option : entry.getValue()) {
                int h = option.description().isBlank() ? theme.spacing().settingRowHeight : theme.spacing().descriptionRowHeight;
                rows.add(SettingRow.option(option, y, h));
                y += h + theme.spacing().rowGap;
            }
            y += theme.spacing().sectionGap;
        }
        CachedSettingRows next = new CachedSettingRows(signature, List.copyOf(rows), y);
        settingRowsCache.put(module.id(), next);
        return next;
    }

    private Map<String, List<Setting<?, ?>>> groupedOptions(Module module) {

        String query = module == selectedModule ? currentSettingsSearch().trim().toLowerCase(Locale.ROOT) : "";
        Map<String, List<Setting<?, ?>>> grouped = new LinkedHashMap<>();
        for (Setting<?, ?> option : module.settings()) {
            if (option.isVisible() && optionAvailable(option)) {
                if (!query.isEmpty() && !matchesSettingSearch(option, query)) continue;
                grouped.computeIfAbsent(option.group(), ignored -> new ArrayList<>()).add(option);
            }
        }

        for (List<Setting<?, ?>> options : grouped.values()) {
            options.sort(Comparator.comparingInt(VanillaModuleMenuController::settingOrder));
        }
        return grouped;
    }

    private static int settingOrder(Setting<?, ?> option) {
        if (option.isListModeSelector()) return 0;
        if (option.isListSelection()) return 1;
        return 2;
    }

    private static boolean matchesSettingSearch(Setting<?, ?> option, String queryLower) {
        return containsIgnoreCase(option.label(), queryLower)
            || containsIgnoreCase(option.group(), queryLower)
            || containsIgnoreCase(option.description(), queryLower)
            || containsIgnoreCase(option.id(), queryLower);
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private boolean optionAvailable(Setting<?, ?> option) {
        if (option == null || !host.offlineSetup()) return option != null;
        return option.isAvailable(false, false);
    }

    private void toggleModule(Module module) {
        if (module == null) return;
        if (host.offlineSetup()) module.setConfiguredEnabled(!module.isEnabled());
        else module.toggle();
    }

    private void setOptionValue(Module module, Setting<?, ?> option, String value) {
        if (module == null || option == null) return;
        if (host.offlineSetup()) module.setConfiguredValue(option.id(), value);
        else module.setValue(option.id(), value);
    }

    private void setOptionValueTransient(Module module, Setting<?, ?> option, String value) {
        if (module == null || option == null) return;
        if (host.offlineSetup()) module.setConfiguredValueTransient(option.id(), value);
        else module.setValueTransient(option.id(), value);
    }

    private long settingSignature(Module module, int width) {

        long h = ModuleRegistry.revision();
        h = h * 31 + width;
        h = h * 31 + (module.isEnabled() ? 1 : 0);
        if (module == selectedModule) h = h * 31 + currentSettingsSearch().hashCode();
        return h;
    }

    private void invalidateSettings(Module module) {
        if (module != null) {
            settingRowsCache.remove(module.id());
            if (!summaryCache.isEmpty()) summaryCache.clear();
        }
    }

    private int clampSettingsScroll(Module module, int contentH, int viewH) {
        int scroll = ScrollContainer.clampScroll(settingsScroll.getOrDefault(module, 0), contentH, viewH);
        settingsScroll.put(module, scroll);
        return scroll;
    }

    private int clampWindowScroll(String id, int contentH, int viewH) {
        int scroll = ScrollContainer.clampScroll(windowScroll.getOrDefault(id, 0), contentH, viewH);
        windowScroll.put(id, scroll);
        return scroll;
    }

    private Scrollbar.Metrics metricsForHit(Hit hit) {
        if (hit == null || hit.type != HitType.SCROLLBAR) return null;
        if ("settings".equals(hit.id) && selectedModule != null) {

            int settingsBodyWidth = Math.min(SETTINGS_W, Math.max(1, screenWidth - 8)) - 10;
            CachedSettingRows rows = cachedRows(selectedModule, settingsBodyWidth);
            int scroll = settingsScroll.getOrDefault(selectedModule, 0);
            return Scrollbar.metrics(hit.bounds, rows.contentHeight, hit.bounds.height(), scroll);
        }
        ModuleCategory category = hit.category;
        int contentH;
        if (category != null) contentH = orderedModules(category).size() * theme.spacing().rowHeight;
        else if (SEARCH_WINDOW.equals(hit.id)) contentH = searchMatches().size() * theme.spacing().rowHeight;
        else contentH = utilityContentHeight();
        int scroll = windowScroll.getOrDefault(hit.id, 0);
        return Scrollbar.metrics(hit.bounds, contentH, hit.bounds.height(), scroll);
    }

    private UiBounds scrollbarHitBounds(UiBounds track) {
        return UiBounds.of(track.x() - 1, track.y(), track.width() + 2, track.height());
    }

    private Hit findScrollbarHit(String id) {
        for (Hit hit : hits) {
            if (hit.type == HitType.SCROLLBAR && hit.id.equals(id)) return hit;
        }
        return null;
    }

    private Hit scrollTargetAt(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds.contains(mx, my)) continue;
            if (hit.type == HitType.WINDOW_HEADER || hit.type == HitType.SETTINGS_HEADER || hit.type == HitType.BACK) {
                return null;
            }
            if (selectedModule != null) {
                return new Hit(HitType.SCROLL_AREA, hit.bounds, selectedModule, null, null, "settings", -1);
            }
            if (SEARCH_WINDOW.equals(hit.id)) {
                return new Hit(HitType.SCROLL_AREA, hit.bounds, null, null, null, SEARCH_WINDOW, -1);
            }
            if (hit.category != null) {
                return new Hit(HitType.SCROLL_AREA, hit.bounds, null, null, hit.category, hit.category.name(), -1);
            }
            if (hit.type == HitType.SCROLL_AREA || hit.type == HitType.SCROLLBAR) return hit;
            return null;
        }
        return null;
    }

    private void setScroll(Hit hit, int scroll) {
        if ("settings".equals(hit.id) && selectedModule != null) settingsScroll.put(selectedModule, scroll);
        else windowScroll.put(hit.id, scroll);
    }

    private void scrollBy(String id, Module module, int delta) {
        if ("settings".equals(id) && module != null) settingsScroll.put(module, Math.max(0, settingsScroll.getOrDefault(module, 0) + delta));
        else windowScroll.put(id, Math.max(0, windowScroll.getOrDefault(id, 0) + delta));
    }

    private boolean isGroupCollapsed(Module module, String group) {
        return groupCollapsed.getOrDefault(module.id() + ":" + group, false);
    }

    private void setGroupCollapsed(Module module, String group, boolean collapsed) {
        groupCollapsed.put(module.id() + ":" + group, collapsed);
    }

    private void initializeWindowDefaults() {

        AutismConfig.ModuleCategoryLayout utility = layout(MAIN_MENU);
        if (utility.x < 0 || utility.y < 0) {
            utility.x = Math.max(4, screenWidth - UTILITY_W - 8);
            utility.y = 4;
        }

        boolean anyMissing = false;
        for (ModuleCategory category : ModuleCategory.values()) {
            AutismConfig.ModuleCategoryLayout layout = layout(category.name());
            if (layout.x < 0 || layout.y < 0) anyMissing = true;
        }
        if (anyMissing) {

            int utilX = utility.x < 0 ? Math.max(4, screenWidth - UTILITY_W - 8) : utility.x;
            int utilW = Math.max(112, Math.min(UTILITY_W, screenWidth - 8));
            boolean utilityOnRight = utilX >= screenWidth / 2;
            int gridMin = utilityOnRight ? 4 : Math.min(screenWidth - 4, utilX + utilW + 8);
            int rightLimit = utilityOnRight
                ? Math.max(CATEGORY_MIN_W + 8, utilX - 8)
                : Math.max(gridMin + CATEGORY_MIN_W + 8, screenWidth - 4);
            int x = gridMin;
            int y = 4;
            int rowMaxH = 0;
            for (ModuleCategory category : ModuleCategory.values()) {
                AutismConfig.ModuleCategoryLayout layout = layout(category.name());
                int w = windowWidth(category.name());
                if (x + w + 4 > rightLimit && x > gridMin) {
                    x = 4;
                    y += rowMaxH + 4;
                    rowMaxH = 0;
                }
                layout.x = x;
                layout.y = y;
                int contentH = orderedModules(category).size() * theme.spacing().rowHeight;
                int windowH = theme.spacing().topBarHeight + Math.min(contentH, maxCategoryBodyHeight(category));
                rowMaxH = Math.max(rowMaxH, windowH);
                x += w + 4;
            }
            AutismConfig.ModuleCategoryLayout search = layout(SEARCH_WINDOW);
            int searchW = windowWidth(SEARCH_WINDOW);
            if (x + searchW + 4 > rightLimit && x > gridMin) {
                x = 4;
                y += rowMaxH + 4;
            }
            search.x = x;
            search.y = y;
        } else {

            AutismConfig.ModuleCategoryLayout search = layout(SEARCH_WINDOW);
            if (search.x < 0 || search.y < 0) {
                int maxBottom = 4;
                for (ModuleCategory category : ModuleCategory.values()) {
                    AutismConfig.ModuleCategoryLayout layout = layout(category.name());
                    int contentH = orderedModules(category).size() * theme.spacing().rowHeight;
                    int bottom = layout.y + theme.spacing().topBarHeight + Math.min(contentH, maxCategoryBodyHeight(category));
                    maxBottom = Math.max(maxBottom, bottom);
                }
                search.x = 4;
                search.y = maxBottom + 4;
            }
        }
        ensureWindowZOrder();
    }

    private void syncLayout(AutismConfig.ModuleCategoryLayout layout, UiBounds bounds) {
        layout.x = bounds.x();
        layout.y = bounds.y();
    }

    private AutismConfig.ModuleCategoryLayout layout(String id) {
        Map<String, AutismConfig.ModuleCategoryLayout> layouts = AutismConfig.getGlobal().moduleCategoryLayouts;
        if (MAIN_MENU.equals(id) && !layouts.containsKey(MAIN_MENU) && layouts.containsKey(LEGACY_UTILITIES)) {
            AutismConfig.ModuleCategoryLayout old = layouts.get(LEGACY_UTILITIES);
            AutismConfig.ModuleCategoryLayout migrated = new AutismConfig.ModuleCategoryLayout();
            migrated.x = old.x;
            migrated.y = old.y;
            migrated.collapsed = old.collapsed;
            layouts.put(MAIN_MENU, migrated);
        }
        return layouts.computeIfAbsent(id, ignored -> new AutismConfig.ModuleCategoryLayout());
    }

    private List<String> windowRenderOrder() {
        ensureWindowZOrder();
        return List.copyOf(windowZOrder);
    }

    private static final Set<String> VALID_WINDOW_IDS = buildValidWindowIds();

    private static Set<String> buildValidWindowIds() {
        Set<String> valid = new LinkedHashSet<>();
        for (ModuleCategory category : ModuleCategory.values()) valid.add(category.name());
        valid.add(SEARCH_WINDOW);
        valid.add(MAIN_MENU);
        return valid;
    }

    private void ensureWindowZOrder() {
        if (windowZOrder.size() == VALID_WINDOW_IDS.size()) return;
        windowZOrder.removeIf(id -> !VALID_WINDOW_IDS.contains(id));
        for (String id : VALID_WINDOW_IDS) {
            if (!windowZOrder.contains(id)) windowZOrder.add(id);
        }
    }

    private void bringWindowToFront(String id) {
        if (id == null || id.isBlank()) return;
        ensureWindowZOrder();
        windowZOrder.remove(id);
        windowZOrder.add(id);
    }

    private ModuleCategory categoryById(String id) {
        if (id == null) return null;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category.name().equals(id)) return category;
        }
        return null;
    }

    private List<Module> orderedModules(ModuleCategory category) {
        List<String> order = AutismConfig.getGlobal().moduleCategoryOrder.computeIfAbsent(category.name(), ignored -> new ArrayList<>());
        int revision = ModuleRegistry.revision();
        boolean hide = PackHideState.isActive();
        String orderSignature = String.join("|", order);
        CachedOrderedModules cached = orderedModulesCache.get(category);
        if (cached != null && cached.revision == revision && cached.hide == hide && cached.orderSignature.equals(orderSignature)) return cached.modules;
        List<Module> modules = new ArrayList<>(ModuleRegistry.byCategory(category));
        if (hide) modules.removeIf(module -> !PackHideState.isHideModule(module));
        if (host.offlineSetup()) {
            modules.removeIf(module -> !module.hasActivationToggle()
                && module.settings().stream().noneMatch(option -> option.isVisible() && optionAvailable(option)));
        }
        Set<String> known = new HashSet<>(order);
        boolean changed = false;
        for (Module module : modules) {
            if (!known.contains(module.id())) {
                order.add(module.id());
                changed = true;
            }
        }
        if (changed) orderSignature = String.join("|", order);
        Map<String, Integer> position = new HashMap<>();
        for (int i = 0; i < order.size(); i++) position.put(order.get(i), i);
        modules.sort(Comparator.comparingInt(module -> position.getOrDefault(module.id(), Integer.MAX_VALUE)));
        List<Module> immutable = List.copyOf(modules);
        orderedModulesCache.put(category, new CachedOrderedModules(revision, hide, orderSignature, immutable));
        return immutable;
    }

    private float animatedEnabledProgress(Module module) {
        if (module == null) return 0.0f;
        float target = module.isEnabled() ? 1.0f : 0.0f;
        AnimatedValue value = enabledAnimations.get(module.id());
        long now = System.nanoTime();
        if (value == null) {
            value = new AnimatedValue(target, now);
            enabledAnimations.put(module.id(), value);
            return target;
        }
        float delta = Math.max(0.0f, Math.min(0.05f, (now - value.lastNanos) / 1_000_000_000.0f));
        value.lastNanos = now;
        float step = delta / 0.16f;
        if (value.value < target) value.value = Math.min(target, value.value + step);
        else if (value.value > target) value.value = Math.max(target, value.value - step);
        return value.value;
    }

    private float enabledProgressSnapshot(Module module) {
        if (module == null) return 0.0f;
        AnimatedValue value = enabledAnimations.get(module.id());
        if (value != null) return value.value;
        return module.isEnabled() ? 1.0f : 0.0f;
    }

    private static int alphaColor(int color, float alphaScale) {
        int alpha = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alphaScale)))));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private Hit findHit(HitType type, Module module, Setting<?, ?> option) {
        for (Hit hit : hits) {
            if (hit.type == type && hit.module == module && hit.option == option) return hit;
        }
        return null;
    }

    private void recycleHits() {
        recycledHits.addAll(hits);
        hits.clear();
    }

    private void addHit(HitType type, UiBounds bounds, Module module, Setting<?, ?> option,
                        ModuleCategory category, String id, int index) {
        Hit hit = recycledHits.isEmpty() ? new Hit() : recycledHits.remove(recycledHits.size() - 1);
        hit.set(type, bounds, module, option, category, id, index);
        hits.add(hit);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        String text = value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        try {
            long parsed = Long.parseUnsignedLong(text, 16);
            if (text.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String listSummary(String value) {
        List<String> tokens = StringListCodec.parse(value);
        int count = tokens.size();
        if (count <= 0) return "Empty";
        return count == 1 ? tokens.get(0) : count + " entries";
    }

    private final Map<String, String> summaryCache = new HashMap<>();

    private String optionSummary(Module module, Setting<?, ?> option) {
        if (module == null || option == null) return "";
        String value = module.value(option.id());
        if (option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER) return conditionalMacroSummary(value);
        if (option.displayMode() == DisplayMode.MACRO_PICKER) return macroSummary(value);
        return switch (option.kind()) {
            case PACKET_LIST -> cachedSummary(module, option, value, () -> packetListSummary(option, value));
            case SOUND_EVENT_LIST -> cachedSummary(module, option, value, () -> soundListSummary(value));
            case STRING_LIST, ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, STORAGE_LIST -> cachedSummary(module, option, value, () -> listSummary(value));
            default -> option.format(value);
        };
    }

    private String cachedSummary(Module module, Setting<?, ?> option, String value, java.util.function.Supplier<String> compute) {
        if (summaryCache.size() > 512) summaryCache.clear();
        return summaryCache.computeIfAbsent(module.id() + ' ' + option.id() + ' ' + value, ignored -> compute.get());
    }

    private static String macroSummary(String value) {
        String macroName = value == null ? "" : value.trim();
        if (macroName.isBlank()) return "No macro";
        return AutismMacroManager.get().get(macroName) == null ? "Missing: " + macroName : macroName;
    }

    private static String conditionalMacroSummary(String value) {
        String macroName = value == null ? "" : value.trim();
        if (macroName.isBlank()) return "No macro";
        String lower = macroName.toLowerCase(Locale.ROOT);
        if (lower.equals(AutoFishStopMacroFactory.FREE_SLOTS_NAME.toLowerCase(Locale.ROOT))
            || lower.startsWith(AutoFishStopMacroFactory.FREE_SLOTS_NAME.toLowerCase(Locale.ROOT) + " (")) return "Free Slots";
        if (lower.equals(AutoFishStopMacroFactory.DURABILITY_NAME.toLowerCase(Locale.ROOT))
            || lower.startsWith(AutoFishStopMacroFactory.DURABILITY_NAME.toLowerCase(Locale.ROOT) + " (")) return "Durability";
        if (lower.equals(AutoFishStopMacroFactory.CUSTOM_NAME.toLowerCase(Locale.ROOT))
            || lower.startsWith(AutoFishStopMacroFactory.CUSTOM_NAME.toLowerCase(Locale.ROOT) + " (")) return "Custom";
        return AutismMacroManager.get().get(macroName) == null ? "Missing: " + macroName : macroName;
    }

    private static String packetListSummary(Setting<?, ?> option, String value) {
        boolean c2s = PacketListCodec.isC2SOption(option == null ? "" : option.id());
        Set<Class<? extends Packet<?>>> packets = PacketListCodec.resolvePackets(value, c2s);
        List<String> invalid = PacketListCodec.invalidTokens(value, c2s);
        int count = packets.size() + invalid.size();
        if (count <= 0) return "Empty";
        if (count > 1) return count + " packets";
        if (!packets.isEmpty()) return AutismPacketNamer.getFriendlyName(packets.iterator().next());
        return invalid.get(0);
    }

    private static String soundListSummary(String value) {
        List<String> tokens = StringListCodec.parse(value);
        if (tokens.isEmpty()) return "Empty";
        if (tokens.size() > 1) return tokens.size() + " sounds";
        return compactSoundId(tokens.get(0));
    }

    private static String compactSoundId(String value) {
        if (value == null || value.isBlank()) return "Empty";
        String cleaned = value.trim();
        int colon = cleaned.indexOf(':');
        String path = colon >= 0 ? cleaned.substring(colon + 1) : cleaned;
        String[] parts = path.split("[._/]+");
        List<String> meaningful = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) meaningful.add(part);
        }
        if (meaningful.size() >= 2) {
            return meaningful.get(meaningful.size() - 2) + "." + meaningful.get(meaningful.size() - 1);
        }
        return path.isBlank() ? cleaned : path;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum HitType {
        WINDOW_HEADER,
        SETTINGS_HEADER,
        MODULE,
        MODULE_BIND,
        UTILITY,
        BACK,
        TOGGLE_MODULE,
        BIND_MODULE,
        MACRO,
        RESET_MODULE_SETTINGS,
        GROUP,
        OPTION,
        FILE_PICKER,
        SLIDER,
        TEXT_FIELD,
        DROPDOWN,
        COLOR_PICKER,
        LIST_EDITOR,
        PACKET_EDITOR,
        MACRO_PICKER,
        SEARCH_FIELD,
        SETTINGS_SEARCH_FIELD,
        BIND_OPTION,
        RESET_OPTION_BIND,
        SCROLL_AREA,
        SCROLLBAR,
        CATEGORY_RESIZE
    }

    private static final class Hit {
        private HitType type;
        private UiBounds bounds;
        private Module module;
        private Setting<?, ?> option;
        private ModuleCategory category;
        private String id;
        private int index;

        private Hit() {
        }

        private Hit(HitType type, UiBounds bounds, Module module, Setting<?, ?> option,
                    ModuleCategory category, String id, int index) {
            set(type, bounds, module, option, category, id, index);
        }

        private void set(HitType type, UiBounds bounds, Module module, Setting<?, ?> option,
                         ModuleCategory category, String id, int index) {
            this.type = type;
            this.bounds = bounds;
            this.module = module;
            this.option = option;
            this.category = category;
            this.id = id == null ? "" : id;
            this.index = index;
        }
    }

    private void renderSummaryActionRow(UiContext context, Module module, Setting<?, ?> option, UiBounds control, String buttonText, Button.Tone tone, HitType hitType) {
        UiBounds button = UiBounds.of(control.right() - 40, control.y(), 40, control.height());
        UiBounds value = UiBounds.of(control.x(), control.y(), Math.max(1, control.width() - 44), control.height());
        drawSummaryValue(context, module, option, value, hitType == HitType.MACRO_PICKER);
        Button.render(context, button, buttonText, tone, button.contains(context.mouseX(), context.mouseY()), false);
        Setting<?, ?> linked = option.linkedActionId().isBlank() ? option : module.setting(option.linkedActionId());
        addHit(hitType, button, module, linked == null ? option : linked, null, "", -1);
    }

    private void renderSummaryOnly(UiContext context, Module module, Setting<?, ?> option, UiBounds control) {
        drawSummaryValue(context, module, option, control);
    }

    private void drawSummaryValue(UiContext context, Module module, Setting<?, ?> option, UiBounds bounds) {
        drawSummaryValue(context, module, option, bounds, false);
    }

    private void drawSummaryValue(UiContext context, Module module, Setting<?, ?> option, UiBounds bounds, boolean alignRight) {
        String summary = optionSummary(module, option);
        boolean empty = summary == null || summary.isBlank() || "Empty".equals(summary) || "No file selected".equals(summary);
        String shown = empty ? (summary == null || summary.isBlank() ? "Empty" : summary) : summary;
        int maxWidth = Math.max(1, bounds.width() - 4);
        int textX = bounds.x() + 2;
        if (alignRight) {
            int textWidth = context.text().width(shown);
            if (textWidth < maxWidth) textX = Math.max(bounds.x() + 2, bounds.right() - 2 - textWidth);
        }
        context.text().drawFitted(
            context.graphics(),
            shown,
            textX,
            context.text().centeredY(bounds),
            Math.max(1, bounds.right() - 2 - textX),
            empty ? theme.colors().muted : theme.colors().text
        );
        if (bounds.contains(context.mouseX(), context.mouseY()) && summary != null && !summary.isBlank()) {
            tooltip = summary;
        }
    }

    private record UtilityAction(String id, String label, Identifier icon, Button.Tone tone, boolean toggle, boolean category) {
        static UtilityAction button(String id, String label, Identifier icon, Button.Tone tone) {
            return new UtilityAction(id, label, icon, tone, false, false);
        }

        static UtilityAction toggle(String id, String label, Identifier icon, Button.Tone tone) {
            return new UtilityAction(id, label, icon, tone, true, false);
        }

        static UtilityAction category(String label, Identifier icon) {
            return new UtilityAction("", label, icon, Button.Tone.NORMAL, false, true);
        }
    }

    private final class MacroPicker {
        private static final int ROW_H = 18;
        private static final int MAX_ROWS = 8;
        private static final String PRESET_FREE_SLOTS = "Free Slots";
        private static final String PRESET_DURABILITY = "Durability";

        private final Module module;
        private final Setting<?, ?> option;
        private final UiBounds anchor;
        private String query = "";
        private int scroll;
        private UiBounds menuBounds = UiBounds.of(0, 0, 0, 0);
        private UiBounds searchBounds = UiBounds.of(0, 0, 0, 0);
        private UiBounds clearBounds = UiBounds.of(0, 0, 0, 0);
        private UiBounds createBounds = UiBounds.of(0, 0, 0, 0);
        private UiBounds listBounds = UiBounds.of(0, 0, 0, 0);
        private List<String> visible = List.of();
        private String filterQuery = null;
        private long filterMacroRevision = Long.MIN_VALUE;

        private MacroPicker(Module module, Setting<?, ?> option, UiBounds anchor) {
            this.module = module;
            this.option = option;
            this.anchor = anchor == null ? UiBounds.of(0, 0, 120, 18) : anchor;
        }

        private void setQuery(String query) {
            String next = query == null ? "" : query;
            if (this.query.equals(next)) return;
            this.query = next;
            this.scroll = 0;
        }

        private void render(UiContext context, int mouseX, int mouseY) {

            long macroRevision = AutismMacroManager.get().getRevision();
            if (!query.equals(filterQuery) || macroRevision != filterMacroRevision) {
                visible = filteredMacros();
                filterQuery = query;
                filterMacroRevision = macroRevision;
            }
            int width = Math.min(Math.max(anchor.width(), 196), Math.max(120, context.screenWidth() - 8));
            int rows = Math.max(1, Math.min(MAX_ROWS, Math.max(visible.size(), 1)));
            int listH = rows * ROW_H;
            int height = 2 + ROW_H + ROW_H + listH + 2;
            int x = clamp(anchor.x(), 4, Math.max(4, context.screenWidth() - width - 4));
            int below = anchor.bottom() + 2;
            int above = anchor.y() - height - 2;
            int y = below + height <= context.screenHeight() - 4 || above < 4 ? below : above;
            y = clamp(y, 4, Math.max(4, context.screenHeight() - height - 4));
            menuBounds = UiBounds.of(x, y, width, height);
            searchBounds = UiBounds.of(x + 2, y + 2, width - 4, ROW_H);
            int half = (width - 6) / 2;
            clearBounds = UiBounds.of(x + 2, searchBounds.bottom(), half, ROW_H);
            createBounds = UiBounds.of(clearBounds.right() + 2, searchBounds.bottom(), width - 4 - half - 2, ROW_H);
            listBounds = UiBounds.of(x + 2, createBounds.bottom(), width - 4, listH);

            UiRenderer.frame(context.graphics(), menuBounds, theme.colors().windowStrong, theme.colors().border);
            TextField.render(context, searchBounds, query, "Search macros...", true, query.length());
            Button.render(context, clearBounds, "Clear", Button.Tone.NORMAL, clearBounds.contains(mouseX, mouseY), false);
            Button.render(context, createBounds, isConditionalPicker() ? "Custom" : "Create New", Button.Tone.SUCCESS, createBounds.contains(mouseX, mouseY), false);

            int maxScroll = Math.max(0, visible.size() - rows);
            scroll = clamp(scroll, 0, maxScroll);
            if (visible.isEmpty()) {
                context.text().drawCentered(context.graphics(), "No saved macros", listBounds, theme.colors().muted);
            } else {
                int contentW = visible.size() > rows ? listBounds.width() - theme.spacing().scrollbarWidth - 2 : listBounds.width();
                for (int row = 0; row < rows; row++) {
                    int index = scroll + row;
                    if (index >= visible.size()) break;
                    String macro = visible.get(index);
                    UiBounds rowBounds = UiBounds.of(listBounds.x(), listBounds.y() + row * ROW_H, contentW, ROW_H);
                    boolean hovered = rowBounds.contains(mouseX, mouseY);
                    boolean selected = macro.equalsIgnoreCase(module.value(option.id()));
                    boolean preset = isPresetChoice(macro);
                    UiRenderer.rect(context.graphics(), rowBounds, selected ? theme.colors().accentSoft : (hovered ? theme.colors().rowHover : theme.colors().row));
                    context.text().drawFitted(context.graphics(), macro, rowBounds.x() + 4, context.text().centeredY(rowBounds),
                        Math.max(1, rowBounds.width() - 8), preset ? theme.colors().success : theme.colors().text);
                }
                if (visible.size() > rows) {
                    UiBounds track = UiBounds.of(listBounds.right() - theme.spacing().scrollbarWidth, listBounds.y(), theme.spacing().scrollbarWidth, listBounds.height());
                    Scrollbar.Metrics metrics = Scrollbar.metrics(track, visible.size() * ROW_H, rows * ROW_H, scroll * ROW_H);
                    Scrollbar.render(context, metrics, false);
                }
            }
        }

        private boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) return menuBounds.contains(mouseX, mouseY);
            if (!menuBounds.contains(mouseX, mouseY)) return false;
            if (clearBounds.contains(mouseX, mouseY)) {
                setOptionValue(module, option, "");
                invalidateSettings(module);
                clearMacroPicker();
                return true;
            }
            if (createBounds.contains(mouseX, mouseY)) {
                clearMacroPicker();
                selectedModule = null;
                if (isConditionalPicker()) {
                    AutismMacro macro = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.CUSTOM);
                    if (macro != null) {
                        setOptionValue(module, option, macro.name);
                        invalidateSettings(module);
                    }
                    host.openMacroCreator(module, option, macro);
                } else {
                    host.openMacroCreator(module, option);
                }
                return true;
            }
            if (listBounds.contains(mouseX, mouseY) && !visible.isEmpty()) {
                int index = scroll + Math.max(0, (mouseY - listBounds.y()) / ROW_H);
                if (index >= 0 && index < visible.size()) {
                    String choice = visible.get(index);
                    AutismMacro preset = createPresetChoice(choice);
                    if (preset != null) choice = preset.name;
                    if (!isConditionalPicker()
                        || preset != null
                        || MacroConditionUtil.startsWithWaitCondition(AutismMacroManager.get().get(choice))) {
                        setOptionValue(module, option, choice);
                        invalidateSettings(module);
                    } else {
                        AutismNotifications.warning("Auto stop rule must start with a conditional.");
                    }
                    clearMacroPicker();
                }
                return true;
            }
            return true;
        }

        private void mouseReleased(int mouseX, int mouseY, int button) {
        }

        private void mouseDragged(int mouseX, int mouseY, int button, double dx, double dy) {
        }

        private void mouseScrolled(int mouseX, int mouseY, double amount) {
            if (!menuBounds.contains(mouseX, mouseY)) return;
            int rows = Math.max(1, Math.min(MAX_ROWS, Math.max(visible.size(), 1)));
            scroll = clamp(scroll + (amount < 0 ? 1 : -1), 0, Math.max(0, visible.size() - rows));
        }

        private List<String> filteredMacros() {
            List<String> presets = new ArrayList<>();
            List<String> names = new ArrayList<>();
            String needle = query.trim().toLowerCase(Locale.ROOT);
            if (isConditionalPicker()) {
                addPresetIfVisible(presets, PRESET_FREE_SLOTS, needle);
                addPresetIfVisible(presets, PRESET_DURABILITY, needle);
            }
            for (AutismMacro macro : AutismMacroManager.get().getAll()) {
                if (macro == null || macro.name == null || macro.name.isBlank()) continue;
                if (isConditionalPicker() && isGeneratedAutoFishPresetName(macro.name)) continue;
                if (isConditionalPicker() && !MacroConditionUtil.startsWithWaitCondition(macro)) continue;
                if (needle.isEmpty() || macro.name.toLowerCase(Locale.ROOT).contains(needle)) names.add(macro.name);
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            if (!presets.isEmpty()) names.addAll(0, presets);
            return names;
        }

        private boolean isConditionalPicker() {
            return option != null && option.displayMode() == DisplayMode.CONDITIONAL_MACRO_PICKER;
        }

        private boolean isPresetChoice(String value) {
            return PRESET_FREE_SLOTS.equals(value) || PRESET_DURABILITY.equals(value);
        }

        private boolean isGeneratedAutoFishPresetName(String value) {
            return AutoFishStopMacroFactory.isGeneratedStopMacroName(value);
        }

        private void addPresetIfVisible(List<String> names, String label, String needle) {
            if (needle == null || needle.isBlank() || label.toLowerCase(Locale.ROOT).contains(needle)) names.add(label);
        }

        private AutismMacro createPresetChoice(String choice) {
            if (PRESET_FREE_SLOTS.equals(choice)) return AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.FREE_SLOTS);
            if (PRESET_DURABILITY.equals(choice)) return AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.DURABILITY);
            return null;
        }
    }

    private static final class AnimatedValue {
        private float value;
        private long lastNanos;

        private AnimatedValue(float value, long lastNanos) {
            this.value = value;
            this.lastNanos = lastNanos;
        }
    }

    private record CachedOrderedModules(int revision, boolean hide, String orderSignature, List<Module> modules) {
    }

    private record CachedSettingRows(long signature, List<SettingRow> rows, int contentHeight) {
    }

    private record SettingRow(String group, Setting<?, ?> option, int y, int height) {
        static SettingRow group(String group, int y, int height) {
            return new SettingRow(group, null, y, height);
        }

        static SettingRow option(Setting<?, ?> option, int y, int height) {
            return new SettingRow(null, option, y, height);
        }
    }

    private static final class Editing {
        private final Module module;
        private final Setting<?, ?> option;
        private String text;
        private int cursor;
        private int selectionAnchor = -1;

        private Editing(Module module, Setting<?, ?> option, String text, int cursor) {
            this.module = module;
            this.option = option;
            this.text = text == null ? "" : text;
            this.cursor = clamp(cursor, 0, this.text.length());
        }

        private boolean hasSelection() {
            return selectionAnchor >= 0 && selectionAnchor != cursor;
        }

        private int selectionStart() {
            return hasSelection() ? Math.min(selectionAnchor, cursor) : cursor;
        }

        private int selectionEnd() {
            return hasSelection() ? Math.max(selectionAnchor, cursor) : cursor;
        }

        private void clearSelection() {
            selectionAnchor = -1;
        }

        private void selectAll() {
            selectionAnchor = 0;
            cursor = text.length();
        }
    }

    private record DragScrollbar(String id, int grabOffset) {
    }
}
