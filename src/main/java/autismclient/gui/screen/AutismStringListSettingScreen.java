package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactListViewport;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.util.StringListCodec;
import autismclient.util.AutismChatField;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPlayerScanner;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.modules.Module;
import autismclient.api.module.DisplayMode;
import autismclient.api.module.Setting;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutismStringListSettingScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 460;
    private static final int PANEL_H = 330;
    private static final int HEADER_H = 24;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 22;
    private static final int BG = 0xCC050507;
    private static final int ROW = 0xAA111114;
    private static final int ROW_HOVER = 0xAA1C1214;
    private static final int TEXT = 0xFFF3ECE7;
    private static final int MUTED = 0xFFB79E9E;

    private static int muted() { return autismclient.util.AutismTheme.recolor(MUTED, autismclient.util.AutismTheme.Channel.TEXT); }
    private static final int RED = 0xFFFF3B3B;

    private final Screen parent;
    private final Module module;
    private final Setting<?, ?> option;
    private final List<Hit> hits = new ArrayList<>();
    private AutismChatField entryField;
    private AutismChatField searchField;
    private int scroll;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private int editingIndex = -1;
    private int suggestionIndex = 0;
    private String cachedRawValue;
    private String cachedFilter;
    private List<String> cachedValues = List.of();
    private List<String> cachedFilteredValues = List.of();

    public AutismStringListSettingScreen(Screen parent, Module module, Setting<?, ?> option) {
        super(Component.literal(option == null ? "Edit List" : "Edit " + option.label()));
        this.parent = parent;
        this.module = module;
        this.option = option;
    }

    @Override
    protected void init() {
        ensureFields();
        syncFieldBounds();
        syncFieldBounds();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            hits.clear();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            int x = panelX();
            int y = panelY();
            int panelW = panelW();
            int panelH = panelH();
            ensureFields();
            syncFieldBounds();
            drawTopBar(graphics, x, y, panelW, panelH, titleText(), mx, my);
            if (panelW < 180 || panelH < 160) {
                drawText(graphics, "Window too small.", x + 8, y + HEADER_H + 10, muted(), Math.max(0, panelW - 16));
                return;
            }

            int fieldX = x + 10;
            int fieldY = y + HEADER_H + 10;
            entryField.render(graphics, mx, my, delta);
            button(graphics, editingIndex >= 0 ? "Save" : "Add", x + panelW - 70, fieldY, 60, FIELD_H, CompactOverlayButton.Variant.SUCCESS, HitType.ADD, -1, mx, my);
            drawPlayerSuggestions(graphics, fieldX, fieldY, panelW - 88, mx, my);

            int searchY = fieldY + FIELD_H + 7;
            searchField.render(graphics, mx, my, delta);

            int actionsY = searchY + FIELD_H + 7;
            int nextX = fieldX;
            button(graphics, "Clear", nextX, actionsY, 58, 18, CompactOverlayButton.Variant.DANGER, HitType.CLEAR, -1, mx, my);
            nextX += 64;
            String importLabel = importLabel();
            if (importLabel != null) {
                button(graphics, importLabel, nextX, actionsY, 88, 18, CompactOverlayButton.Variant.PRIMARY, HitType.IMPORT, -1, mx, my);
                nextX += 94;
            }
            if (supportsRankTagScan()) {
                button(graphics, "Scan", nextX, actionsY, 58, 18, CompactOverlayButton.Variant.PRIMARY, HitType.SCAN, -1, mx, my);
                nextX += 64;
            }
            int summaryX = nextX + 6;
            drawText(graphics, filteredValues().size() + " shown | " + snapshotValues().size() + " total",
                summaryX, actionsY + 5, muted(), x + panelW - 10 - summaryX);

            int listY = actionsY + 24;
            int listH = y + panelH - listY - 10;
            CompactListViewport.Layout listLayout = stringListLayout(listY, listH);
            frame(graphics, fieldX, listY, panelW - 20, listH, 0xAA09090B, THEME.borderSoft());
            listLayout.beginRows(graphics);
            try {
                drawRows(graphics, listLayout, mx, my);
            } finally {
                listLayout.endRows(graphics);
            }
            listLayout.drawScrollbar(graphics, mx, my, scrollbarDragging);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, CompactListViewport.Layout layout, int mx, int my) {
        List<String> rows = filteredValues();
        scroll = clamp(scroll, 0, layout.maxScroll());
        int x = layout.x() + layout.contentInset();
        int w = layout.contentWidth();
        if (rows.isEmpty()) {
            drawText(graphics, "No entries.", x + 4, layout.y() + layout.contentInset() + 6, muted(), w - 8);
            return;
        }
        List<String> allValues = snapshotValues();
        layout.forEachVisibleRow(rows.size(), (i, rowY) -> {
            String value = rows.get(i);
            boolean over = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H;
            CompactSurfaces.tintedRow(graphics, x, rowY, w, ROW_H - 1, over ? ROW_HOVER : ROW);
            drawText(graphics, value, x + 6, rowY + 7, TEXT, w - 30);
            CompactListRenderer.drawDeleteButton(graphics, x + w - 22, rowY + 4, 18, 14, over);
            int index = indexForFilteredRow(rows, allValues, i);
            hits.add(new Hit(HitType.EDIT_ENTRY, x, rowY, Math.max(1, w - 26), ROW_H, index));
            hits.add(new Hit(HitType.REMOVE, x + w - 22, rowY + 4, 18, 14, index));
        });
    }

    private void drawPlayerSuggestions(GuiGraphicsExtractor graphics, int fieldX, int fieldY, int fieldW, int mx, int my) {
        if (!supportsPlayerSuggestions() || entryField == null || !entryField.isFocused()) return;
        List<String> suggestions = playerSuggestions();
        if (suggestions.isEmpty()) return;
        int visible = Math.min(5, suggestions.size());
        int rowH = 14;
        int popupH = visible * rowH + 2;
        int popupY = Math.max(4, fieldY - popupH - 2);
        int popupW = Math.min(fieldW, 170);
        int popupX = fieldX;
        frame(graphics, popupX, popupY, popupW, popupH, 0xEE09090B, autismclient.util.AutismTheme.recolor(RED, autismclient.util.AutismTheme.Channel.OUTLINE));
        int start = Math.max(0, Math.min(suggestionIndex - visible + 1, suggestions.size() - visible));
        for (int i = 0; i < visible; i++) {
            int idx = start + i;
            String suggestion = suggestions.get(idx);
            int y = popupY + 1 + i * rowH;
            boolean selected = idx == suggestionIndex;
            boolean over = mx >= popupX && mx < popupX + popupW && my >= y && my < y + rowH;
            CompactSurfaces.tintedRow(graphics, popupX + 1, y, popupW - 2, rowH - 1, selected || over ? ROW_HOVER : ROW);
            drawText(graphics, suggestion, popupX + 5, y + 4, TEXT, popupW - 10);
            hits.add(new Hit(HitType.SUGGESTION, popupX + 1, y, popupW - 2, rowH, idx));
        }
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, CompactOverlayButton.Variant variant, HitType type, int index, int mx, int my) {
        CompactOverlayControls.action(graphics, font, x, y, w, h, label, variant, true, mx, my);
        hits.add(new Hit(type, x, y, w, h, index));
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H, title,
            mx >= x && mx < x + width && my >= y && my < y + HEADER_H);
        UiBounds close = CompactScreenPanel.closeButton(bounds, HEADER_H);
        hits.add(new Hit(HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), -1));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (event.button() != 0) return true;
        CompactScrollbar.Metrics metrics = stringScrollbarMetrics();
        if (metrics.hasScroll() && metrics.contains(mx, my)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            scroll = snapScrollToRows(CompactScrollbar.scrollFromThumb(metrics, my, scrollbarGrabOffset));
            return true;
        }
        ensureFields();
        if (entryField.mouseClicked(mx, my, event.button())) {
            searchField.setFocused(false);
            return true;
        }
        if (searchField.mouseClicked(mx, my, event.button())) {
            entryField.setFocused(false);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if (hit.type == HitType.REMOVE && !insideListBody(mx, my)) continue;
            switch (hit.type) {
                case CLOSE -> onClose();
                case ADD -> addEntry();
                case EDIT_ENTRY -> beginEditEntry(hit.index);
                case REMOVE -> removeEntry(hit.index);
                case SUGGESTION -> acceptSuggestion(hit.index);
                case CLEAR -> {
                    setOptionValue("");
                    cancelEdit();
                    clampScrollToList();
                }
                case SCAN -> scanRankTags();
                case IMPORT -> importFromLinkedList();
            }
            return true;
        }
        entryField.setFocused(false);
        searchField.setFocused(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int my = AutismUiScale.toVirtualInt(mouseY);
        int mx = AutismUiScale.toVirtualInt(mouseX);
        if (!insideListBody(mx, my)) return true;
        scroll += scrollY < 0 ? ROW_H : -ROW_H;
        scroll = clamp(scroll, 0, stringScrollbarMetrics().maxScroll());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        scrollbarDragging = false;
        if (entryField != null && entryField.mouseReleased(mx, my, event.button())) return true;
        if (searchField != null && searchField.mouseReleased(mx, my, event.button())) return true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int my = AutismUiScale.toVirtualInt(event.y());
        int mx = AutismUiScale.toVirtualInt(event.x());
        if (entryField != null && entryField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (searchField != null && searchField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (!scrollbarDragging) return true;
        scroll = snapScrollToRows(CompactScrollbar.scrollFromThumb(stringScrollbarMetrics(), my, scrollbarGrabOffset));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        ensureFields();
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editingIndex >= 0) {

                if (!entryText().trim().isEmpty()) addEntry();
                else cancelEdit();
                return true;
            }
            onClose();
            return true;
        }
        if (entryField.isFocused() && supportsPlayerSuggestions() && input.key() == GLFW.GLFW_KEY_TAB) {
            List<String> suggestions = playerSuggestions();
            if (!suggestions.isEmpty()) {
                suggestionIndex = Math.floorMod(suggestionIndex + ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), suggestions.size());
                setEntryText(applySuggestionToEntry(entryText(), suggestions.get(suggestionIndex)));
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (entryField.isFocused()) addEntry();
            return true;
        }
        if (entryField.keyPressed(input)) return true;
        if (searchField.keyPressed(input)) return true;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        ensureFields();
        if (entryField.charTyped(input)) return true;
        if (searchField.charTyped(input)) return true;
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void addEntry() {
        String value = entryText().trim();
        if (value.isEmpty()) return;
        List<String> values = values();
        if (editingIndex >= 0 && editingIndex < values.size()) {
            values.set(editingIndex, value);
        } else {
            values.add(value);
        }
        setOptionValue(StringListCodec.encode(values));
        setEntryText("");
        editingIndex = -1;
        suggestionIndex = 0;
        clampScrollToList();
    }

    private void beginEditEntry(int index) {
        List<String> values = snapshotValues();
        if (index < 0 || index >= values.size()) return;
        editingIndex = index;
        setEntryText(values.get(index));
        if (entryField != null) entryField.setFocused(true);
        if (searchField != null) searchField.setFocused(false);
        suggestionIndex = 0;
    }

    private void cancelEdit() {
        editingIndex = -1;
        setEntryText("");
        suggestionIndex = 0;
    }

    private void removeEntry(int index) {
        List<String> values = values();
        if (index < 0 || index >= values.size()) return;
        values.remove(index);
        setOptionValue(StringListCodec.encode(values));
        if (editingIndex == index) cancelEdit();
        else if (editingIndex > index) editingIndex--;
        clampScrollToList();
    }

    private boolean isTitleSetup() {
        return parent instanceof AutismModuleScreen moduleScreen && moduleScreen.isTitleSetup();
    }

    private void setOptionValue(String value) {
        if (isTitleSetup()) module.setConfiguredValue(option.id(), value);
        else module.setValue(option.id(), value);
    }

    private List<String> filteredValues() {
        String needle = searchText().trim().toLowerCase(Locale.ROOT);
        List<String> values = snapshotValues();
        if (needle.equals(cachedFilter)) return cachedFilteredValues;
        cachedFilter = needle;
        if (needle.isEmpty()) {
            cachedFilteredValues = values;
            return cachedFilteredValues;
        }
        List<String> out = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).contains(needle)) out.add(value);
        cachedFilteredValues = List.copyOf(out);
        return cachedFilteredValues;
    }

    private List<String> values() {
        return new ArrayList<>(snapshotValues());
    }

    private List<String> snapshotValues() {
        String rawValue = module.value(option.id());
        if (!rawValue.equals(cachedRawValue)) {
            cachedRawValue = rawValue;
            cachedValues = List.copyOf(StringListCodec.parse(rawValue));
            cachedFilter = null;
            cachedFilteredValues = List.of();
        }
        return cachedValues;
    }

    private int indexForFilteredRow(List<String> rows, List<String> allValues, int filteredIndex) {
        if (filteredIndex < 0 || filteredIndex >= rows.size()) return -1;
        String value = rows.get(filteredIndex);
        int occurrence = 0;
        for (int i = 0; i <= filteredIndex; i++) {
            if (value.equals(rows.get(i))) occurrence++;
        }
        for (int i = 0; i < allValues.size(); i++) {
            if (value.equals(allValues.get(i)) && --occurrence == 0) return i;
        }
        return allValues.indexOf(value);
    }

    private boolean supportsPlayerSuggestions() {
        return option != null && option.displayMode() == DisplayMode.PLAYER_NAME_LIST;
    }

    private boolean supportsRankTagScan() {
        return option != null && option.displayMode() == DisplayMode.RANK_TAG_LIST;
    }

    private void scanRankTags() {
        if (!supportsRankTagScan() || minecraft == null || minecraft.getConnection() == null) {
            AutismNotifications.warning("Join a server first.");
            return;
        }
        List<String> values = values();
        int added = 0;
        for (AutismPlayerScanner.ScannedPlayer player : AutismPlayerScanner.scan(minecraft)) {
            if (player == null || !player.hasPrefix()) continue;
            String tag = player.prefix().trim();
            if (tag.isEmpty() || containsIgnoreCase(values, tag)) continue;
            values.add(tag);
            added++;
        }
        if (added > 0) {
            setOptionValue(StringListCodec.encode(values));
            clampScrollToList();
            AutismNotifications.show("Rank scan added " + added + " tag" + (added == 1 ? "." : "s."), 0xFF35D873);
        } else {
            AutismNotifications.warning("Rank scan found nothing.");
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private String importLabel() {
        if (module == null || option == null) return null;
        if ("teams".equals(module.id()) && "friends".equals(option.id())) return "Import Names";
        if ("name-censor".equals(module.id()) && "names".equals(option.id())) return "Import Teams";
        return null;
    }

    private void importFromLinkedList() {
        String sourceModuleId = null;
        String sourceOptionId = null;
        if (module != null && option != null) {
            if ("teams".equals(module.id()) && "friends".equals(option.id())) {
                sourceModuleId = "name-censor";
                sourceOptionId = "names";
            } else if ("name-censor".equals(module.id()) && "names".equals(option.id())) {
                sourceModuleId = "teams";
                sourceOptionId = "friends";
            }
        }
        autismclient.modules.Module source = sourceModuleId == null
            ? null : autismclient.modules.ModuleRegistry.get(sourceModuleId);
        if (source == null) {
            AutismNotifications.warning("Nothing to import.");
            return;
        }
        List<String> values = values();
        int added = 0;
        for (String entry : StringListCodec.parse(source.value(sourceOptionId))) {
            if (entry == null || entry.isBlank() || containsIgnoreCase(values, entry)) continue;
            values.add(entry.trim());
            added++;
        }
        if (added > 0) {
            setOptionValue(StringListCodec.encode(values));
            clampScrollToList();
            AutismNotifications.show("Imported " + added + " name" + (added == 1 ? "." : "s."), 0xFF35D873);
        } else {
            AutismNotifications.warning("Nothing new to import.");
        }
    }

    private List<String> playerSuggestions() {
        if (!supportsPlayerSuggestions() || minecraft == null || minecraft.getConnection() == null) return List.of();
        String query = playerSuggestionQuery(entryText()).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (net.minecraft.client.multiplayer.PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            String name = info.getProfile().name();
            if (!isPlayerSuggestionName(name)) continue;
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(query)) continue;
            if (!names.contains(name)) names.add(name);
        }
        if (minecraft.level != null) {
            for (var player : minecraft.level.players()) {
                if (player == null || player.getGameProfile() == null || player.getGameProfile().name() == null) continue;
                String name = player.getGameProfile().name();
                if (!isPlayerSuggestionName(name)) continue;
                if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(query)) continue;
                if (!names.contains(name)) names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (suggestionIndex >= names.size()) suggestionIndex = Math.max(0, names.size() - 1);
        return names;
    }

    private boolean isPlayerSuggestionName(String name) {
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.length() < 4) return false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("slot_") && lower.length() > 5) {
            for (int i = 5; i < lower.length(); i++) {
                if (!Character.isDigit(lower.charAt(i))) return true;
            }
            return false;
        }
        return true;
    }

    private String playerSuggestionQuery(String entry) {
        String value = entry == null ? "" : entry;
        int eq = value.indexOf('=');
        if (eq >= 0) value = value.substring(0, eq);
        return value.trim();
    }

    private String applySuggestionToEntry(String entry, String suggestion) {
        String value = entry == null ? "" : entry;
        int eq = value.indexOf('=');
        if (eq >= 0) return suggestion + value.substring(eq);
        return suggestion;
    }

    private void acceptSuggestion(int index) {
        List<String> suggestions = playerSuggestions();
        if (index < 0 || index >= suggestions.size()) return;
        suggestionIndex = index;
        setEntryText(applySuggestionToEntry(entryText(), suggestions.get(index)));
        if (entryField != null) entryField.setFocused(true);
        if (searchField != null) searchField.setFocused(false);
    }

    private void ensureFields() {
        if (entryField == null) {
            entryField = new AutismChatField(minecraft, font, 0, 0, 1, FIELD_H, false);
            entryField.setMaxLength(512);
            entryField.setChangedListener(value -> suggestionIndex = 0);
        }
        if (searchField == null) {
            searchField = new AutismChatField(minecraft, font, 0, 0, 1, FIELD_H, false);
            searchField.setMaxLength(128);
            searchField.setPlaceholder(Component.literal("Search"));
            searchField.setChangedListener(value -> {
                cachedFilter = null;
                clampScrollToList();
            });
        }
    }

    private void syncFieldBounds() {
        if (entryField == null || searchField == null) return;
        int x = panelX();
        int y = panelY();
        int panelW = panelW();
        int fieldX = x + 10;
        int fieldY = y + HEADER_H + 10;
        entryField.setX(fieldX);
        entryField.setY(fieldY);
        entryField.setWidth(panelW - 88);
        entryField.setHeight(FIELD_H);
        entryField.setPlaceholder(Component.literal(editingIndex >= 0 ? "Edit entry" : "New entry"));
        int searchY = fieldY + FIELD_H + 7;
        searchField.setX(fieldX);
        searchField.setY(searchY);
        searchField.setWidth(panelW - 20);
        searchField.setHeight(FIELD_H);
    }

    private String entryText() {
        return entryField == null ? "" : entryField.getText();
    }

    private void setEntryText(String value) {
        ensureFields();
        entryField.setText(value == null ? "" : value);
        entryField.setSelectionEnd(entryField.getText().length());
    }

    private String searchText() {
        return searchField == null ? "" : searchField.getText();
    }

    private String titleText() {
        return option == null ? "Edit List" : "Edit " + option.label();
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelW(), 4);
    }

    private int panelY() {
        return DirectLayout.centerPanel(screenHeight(), panelH(), 4);
    }

    private int panelW() {
        return DirectLayout.fitPanelDimension(screenWidth(), 4, PANEL_W);
    }

    private int panelH() {
        return DirectLayout.fitPanelDimension(screenHeight(), 4, PANEL_H);
    }

    private CompactScrollbar.Metrics stringScrollbarMetrics() {
        int y = panelY();
        int fieldY = y + HEADER_H + 10;
        int searchY = fieldY + FIELD_H + 7;
        int actionsY = searchY + FIELD_H + 7;
        int listY = actionsY + 24;
        int listH = y + panelH() - listY - 10;
        return stringScrollbarMetrics(listY, listH);
    }

    private CompactScrollbar.Metrics stringScrollbarMetrics(int listY, int listH) {
        return stringListLayout(listY, listH).scrollbar();
    }

    private CompactListViewport.Layout stringListLayout(int listY, int listH) {
        return CompactListViewport.layout(
            panelX() + 10,
            listY,
            panelW() - 20,
            listH,
            filteredValues().size(),
            ROW_H,
            ROW_H,
            scroll,
            4,
            0,
            3,
            6,
            false
        );
    }

    private boolean insideListBody(int mx, int my) {
        int y = panelY();
        int fieldY = y + HEADER_H + 10;
        int searchY = fieldY + FIELD_H + 7;
        int actionsY = searchY + FIELD_H + 7;
        int listY = actionsY + 24;
        int listH = y + panelH() - listY - 10;
        CompactListViewport.Layout listLayout = stringListLayout(listY, listH);
        return mx >= listLayout.x() + listLayout.contentInset()
            && mx < listLayout.x() + listLayout.contentInset() + listLayout.contentWidth()
            && my >= listLayout.y() + listLayout.contentInset()
            && my < listLayout.y() + listLayout.contentInset() + listLayout.viewHeight();
    }

    private void clampScrollToList() {
        scroll = clamp(scroll, 0, stringScrollbarMetrics().maxScroll());
    }

    private int snapScrollToRows(int offset) {
        return Math.max(0, offset / ROW_H) * ROW_H;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
        UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        String display = UiText.trimToWidth(font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
        String display = UiText.trimToWidth(font, text == null ? "" : text, Math.max(0, w - 4), THEME.fontFor(UiTone.BODY), color);
        int tw = UiText.width(font, display, THEME.fontFor(UiTone.BODY), color);
        int th = THEME.fontHeight(UiTone.BODY);
        int drawX = x + Math.max(2, (w - tw) / 2);
        int drawY = y + Math.max(1, (h - th + 1) / 2 + 1);
        UiText.draw(graphics, font, display, THEME.fontFor(UiTone.BODY), color, drawX, drawY, false);
    }

    private record Hit(HitType type, int x, int y, int w, int h, int index) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum HitType {
        ADD,
        EDIT_ENTRY,
        REMOVE,
        SUGGESTION,
        CLEAR,
        SCAN,
        IMPORT,
        CLOSE
    }
}
