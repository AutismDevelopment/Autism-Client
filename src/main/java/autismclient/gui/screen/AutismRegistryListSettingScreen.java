package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactSymbolButton;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.modules.Module;
import autismclient.api.module.Kind;
import autismclient.api.module.RegistryListSetting;
import autismclient.api.module.Setting;
import autismclient.modules.ScaffoldModule;
import autismclient.modules.ModuleStorageEsp;
import autismclient.util.AutismChatField;
import autismclient.util.AutismDisplayItemUtils;
import autismclient.util.AutismUiScale;
import autismclient.util.RegistryListCodec;
import autismclient.util.StringListCodec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class AutismRegistryListSettingScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 620;
    private static final int PANEL_H = 360;
    private static final int HEADER_H = 24;
    private static final int SEARCH_H = 20;
    private static final int ROW_H = 34;
    private static final int ICON_SIZE = 24;
    private static final int GROUP_H = 16;
    private static final Identifier EMPTY_SPAWN_EGG = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/autism/empty_spawn_egg.png");
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
    private final List<Entry> entries;
    private final Map<String, Entry> entriesById = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private AutismChatField searchField;
    private int availableScroll;
    private int selectedScroll;
    private boolean availableScrollbarDragging;
    private boolean selectedScrollbarDragging;
    private int scrollbarGrabOffset;
    private String cachedRawValue;
    private String cachedSearch;
    private Set<String> cachedSelectedIds = Set.of();
    private List<SelectedRow> cachedSelectedRows = List.of();
    private List<Entry> cachedAvailableRows = List.of();

    public AutismRegistryListSettingScreen(Screen parent, Module module, Setting<?, ?> option) {
        super(Component.literal(option == null ? "Select" : "Select " + option.label()));
        this.parent = parent;
        this.module = module;
        this.option = option;
        this.entries = buildEntries(option == null ? Kind.ITEM_LIST : option.kind());
        for (Entry entry : entries) entriesById.put(entry.id, entry);
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
            drawTopBar(graphics, x, y, panelW, panelH, titleText(), mx, my);
            if (panelW < 260 || panelH < 150) {
                drawText(graphics, "Window too small.", x + 8, y + HEADER_H + 10, muted(), Math.max(0, panelW - 16));
                return;
            }

            int searchX = x + 10;
            int searchY = y + HEADER_H + 9;
            ensureSearchField(searchX, searchY, panelW - 20, SEARCH_H);
            searchField.render(graphics, mx, my, delta);

            int paneY = searchY + SEARCH_H + 10;
            int paneH = y + panelH - paneY - 12;
            int paneW = (panelW - 30) / 2;
            int leftX = x + 10;
            int rightX = leftX + paneW + 10;

            drawPane(graphics, leftX, paneY, paneW, paneH, "Available", true, mx, my);
            drawPane(graphics, rightX, paneY, paneW, paneH, "Selected", false, mx, my);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawPane(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title, boolean available, int mx, int my) {
        frame(graphics, x, y, w, h, 0xAA09090B, THEME.borderSoft());
        drawText(graphics, title, x + 6, y + 6, TEXT, w - 12);
        int listTop = y + 24;
        int listH = h - 28;
        UiScissorStack.global().push(graphics, UiBounds.of(x + 1, listTop, Math.max(0, w - 2), Math.max(0, listH)));
        try {

            if (available) drawAvailableRows(graphics, x + 3, listTop, w - 12, listH, mx, my);
            else drawSelectedRows(graphics, x + 3, listTop, w - 12, listH, mx, my);
        } finally {
            UiScissorStack.global().pop(graphics);
        }
        CompactScrollbar.Metrics metrics = registryScrollbarMetrics(available, x, listTop, w, listH);
        CompactScrollbar.draw(graphics, metrics, metrics.contains(mx, my), available ? availableScrollbarDragging : selectedScrollbarDragging);
    }

    private void drawAvailableRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
        List<Entry> rows = filteredAvailable();
        int maxScroll = Math.max(0, availableContentHeight(rows) - h + 8);
        availableScroll = clamp(availableScroll, 0, maxScroll);
        int rowY = y - availableScroll;
        String lastGroup = "";
        for (Entry entry : rows) {
            if (showsGroupHeaders(option) && !entry.group.equals(lastGroup)) {
                drawGroup(graphics, entry.group, x, rowY, w);
                rowY += GROUP_H;
                lastGroup = entry.group;
            }
            if (rowY + ROW_H > y && rowY < y + h) drawEntryRow(graphics, entry, x, rowY, w, HitType.ADD, entry.id, mx, my, false);
            rowY += ROW_H;
        }
    }

    private void drawSelectedRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
        List<SelectedRow> rows = selectedRows();
        int maxScroll = Math.max(0, rows.size() * ROW_H - h + 8);
        selectedScroll = clamp(selectedScroll, 0, maxScroll);
        int rowY = y - selectedScroll;
        if (rows.isEmpty()) {
            drawText(graphics, "Nothing selected.", x + 4, y + 6, muted(), w - 8);
            return;
        }
        int first = clamp(selectedScroll / ROW_H, 0, Math.max(0, rows.size() - 1));
        int last = clamp((selectedScroll + h - 1) / ROW_H, 0, rows.size() - 1);
        for (int i = first; i <= last; i++) {
            SelectedRow row = rows.get(i);
            rowY = y - selectedScroll + i * ROW_H;
            if (rowY + ROW_H > y && rowY < y + h) {
                drawEntryRow(graphics, row.entry(), x, rowY, w, HitType.REMOVE, row.id, mx, my, row.invalid);
            }
        }
    }

    private void drawGroup(GuiGraphicsExtractor graphics, String label, int x, int y, int w) {
        CompactSurfaces.header(graphics, x, y + 2, w, 13);
        drawText(graphics, label, x + 5, y + 4, muted(), w - 10);
    }

    private void drawEntryRow(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int w, HitType type, String value, int mx, int my, boolean invalid) {
        String label = entry == null ? value : entry.label;
        String id = entry == null ? value : entry.id;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        CompactSurfaces.tintedRow(graphics, x, y, w, ROW_H - 1, over ? ROW_HOVER : ROW);
        int textX = x + 8;
        if (entry != null && !invalid) {
            drawEntryIcon(graphics, entry, x + 6, y + Math.max(2, (ROW_H - ICON_SIZE) / 2));
            textX = x + 6 + ICON_SIZE + 7;
        }
        int actionW = 14;
        int textMax = x + w - actionW - 6 - textX;
        drawText(graphics, label, textX, y + 5, invalid ? autismclient.util.AutismTheme.recolor(RED, autismclient.util.AutismTheme.Channel.DANGER) : TEXT, textMax);
        drawText(graphics, id, textX, y + 19, muted(), textMax);
        if (type == HitType.ADD) {
            CompactListRenderer.drawStructuralButton(graphics, x + w - actionW - 3, y + (ROW_H - 14) / 2, actionW, 14,
                CompactSymbolButton.EXPAND, over, false);
        } else {

            CompactListRenderer.drawDeleteButton(graphics, x + w - actionW - 3, y + (ROW_H - 14) / 2, actionW, 14, over);
        }
        hits.add(new Hit(type, x, y, w, ROW_H, value));
    }

    private void drawEntryIcon(GuiGraphicsExtractor graphics, Entry entry, int x, int y) {
        if (entry.iconFallback()) {
            graphics.blit(EMPTY_SPAWN_EGG, x, y, x + ICON_SIZE, y + ICON_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            return;
        }
        ItemStack stack = entry.icon();
        if (stack == null || stack.isEmpty()) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.5f, 1.5f);
        graphics.item(stack, Math.round(x / 1.5f), Math.round(y / 1.5f));
        graphics.pose().popMatrix();
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H, title,
            mx >= x && mx < x + width && my >= y && my < y + HEADER_H);
        UiBounds close = CompactScreenPanel.closeButton(bounds, HEADER_H);
        hits.add(new Hit(HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), ""));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (event.button() != 0) return true;
        if (searchField != null && searchField.mouseClicked(mx, my, event.button())) return true;
        CompactScrollbar.Metrics availableMetrics = registryScrollbarMetrics(true);
        if (availableMetrics.hasScroll() && availableMetrics.contains(mx, my)) {
            availableScrollbarDragging = true;
            scrollbarGrabOffset = availableMetrics.overThumb(mx, my) ? my - availableMetrics.thumbY() : availableMetrics.thumbHeight() / 2;
            availableScroll = CompactScrollbar.scrollFromThumb(availableMetrics, my, scrollbarGrabOffset);
            return true;
        }
        CompactScrollbar.Metrics selectedMetrics = registryScrollbarMetrics(false);
        if (selectedMetrics.hasScroll() && selectedMetrics.contains(mx, my)) {
            selectedScrollbarDragging = true;
            scrollbarGrabOffset = selectedMetrics.overThumb(mx, my) ? my - selectedMetrics.thumbY() : selectedMetrics.thumbHeight() / 2;
            selectedScroll = snapSelectedScroll(CompactScrollbar.scrollFromThumb(selectedMetrics, my, scrollbarGrabOffset));
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if (hit.type == HitType.ADD && !insideAvailablePane(mx, my)) continue;
            if (hit.type == HitType.REMOVE && !insideSelectedPane(mx, my)) continue;
            switch (hit.type) {
                case CLOSE -> onClose();
                case ADD -> add(hit.value);
                case REMOVE -> remove(hit.value);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        availableScrollbarDragging = false;
        selectedScrollbarDragging = false;
        if (searchField != null) {
            searchField.mouseReleased(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (availableScrollbarDragging) {
            availableScroll = CompactScrollbar.scrollFromThumb(registryScrollbarMetrics(true), my, scrollbarGrabOffset);
            return true;
        }
        if (selectedScrollbarDragging) {
            selectedScroll = snapSelectedScroll(CompactScrollbar.scrollFromThumb(registryScrollbarMetrics(false), my, scrollbarGrabOffset));
            return true;
        }
        if (searchField != null && searchField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        int x = panelX() + 10;
        int y = panelY() + HEADER_H + 9 + SEARCH_H + 10;
        int paneW = (panelW() - 30) / 2;
        int paneH = panelY() + panelH() - y - 12;
        int amount = scrollY < 0 ? ROW_H : -ROW_H;
        if (insideAvailablePane(mx, my)) availableScroll += amount;
        else if (insideSelectedPane(mx, my)) selectedScroll += amount;
        else return true;
        availableScroll = clamp(availableScroll, 0, registryScrollbarMetrics(true).maxScroll());
        selectedScroll = clamp(selectedScroll, 0, registryScrollbarMetrics(false).maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (searchField != null && searchField.keyPressed(input)) return true;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (searchField != null) searchField.charTyped(input);
        return true;
    }

    private void ensureSearchField(int x, int y, int w, int h) {
        if (searchField == null) {
            searchField = new AutismChatField(net.minecraft.client.Minecraft.getInstance(), font, x, y, w, h, false);
            searchField.setPlaceholder(net.minecraft.network.chat.Component.literal("Search"));
            searchField.setChangedListener(value -> clampPaneScrolls());
            searchField.setFocused(true);
        }
        searchField.setX(x);
        searchField.setY(y);
        searchField.setWidth(w);
        searchField.setHeight(h);
    }

    private String searchText() {
        return searchField == null ? "" : searchField.getText();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void add(String id) {
        List<String> tokens = StringListCodec.parse(module.value(option.id()));
        LinkedHashSet<String> valid = validIds(tokens);
        List<String> invalid = invalidTokens(tokens);
        valid.add(id);
        write(valid, invalid);
        clampPaneScrolls();
    }

    private void remove(String id) {
        List<String> tokens = StringListCodec.parse(module.value(option.id()));
        LinkedHashSet<String> valid = validIds(tokens);
        List<String> invalid = invalidTokens(tokens);
        String normalized = RegistryListCodec.normalizeId(id);
        valid.remove(normalized);
        invalid.removeIf(token -> token.equals(id)
            || RegistryListCodec.normalizeId(token).equals(normalized));
        write(valid, invalid);
        clampPaneScrolls();
    }

    private void write(Set<String> valid, List<String> invalid) {
        List<String> out = new ArrayList<>(valid);
        out.addAll(invalid);
        String encoded = StringListCodec.encode(out);
        if (parent instanceof AutismModuleScreen moduleScreen && moduleScreen.isTitleSetup()) {
            module.setConfiguredValue(option.id(), encoded);
        } else {
            module.setValue(option.id(), encoded);
        }
    }

    private Set<String> selectedValidIds() {
        ensureListCache();
        return cachedSelectedIds;
    }

    private LinkedHashSet<String> validIds(List<String> tokens) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = RegistryListCodec.normalizeId(token);
            if (RegistryListCodec.exists(option.kind(), normalized)
                && (!placeableBlocksOnly() || entriesById.containsKey(normalized))) {
                out.add(normalized);
            }
        }
        return out;
    }

    private List<String> invalidTokens(List<String> tokens) {
        if (!placeableBlocksOnly()) return RegistryListCodec.invalidTokens(option.kind(), tokens);
        List<String> invalid = new ArrayList<>();
        for (String token : tokens) {
            String normalized = RegistryListCodec.normalizeId(token);
            if (!RegistryListCodec.exists(option.kind(), normalized) || !entriesById.containsKey(normalized)) {
                invalid.add(token);
            }
        }
        return invalid;
    }

    private boolean placeableBlocksOnly() {
        return option instanceof RegistryListSetting registry && registry.placeableBlocksOnly();
    }

    private List<Entry> filteredAvailable() {
        ensureListCache();
        return cachedAvailableRows;
    }

    private List<SelectedRow> selectedRows() {
        ensureListCache();
        return cachedSelectedRows;
    }

    private void ensureListCache() {
        String rawValue = module.value(option.id());
        String needle = searchText().trim().toLowerCase(Locale.ROOT);
        if (rawValue.equals(cachedRawValue) && needle.equals(cachedSearch)) return;

        List<String> tokens = StringListCodec.parse(module.value(option.id()));
        LinkedHashSet<String> selected = validIds(tokens);
        List<SelectedRow> selectedRows = new ArrayList<>();
        for (String id : selected) {
            Entry entry = entry(id);
            selectedRows.add(new SelectedRow(id, entry == null ? fallbackEntry(id) : entry, false));
        }
        for (String invalid : invalidTokens(tokens)) {
            selectedRows.add(new SelectedRow(invalid, fallbackEntry("Invalid: " + invalid, invalid), true));
        }
        List<Entry> available = new ArrayList<>();
        for (Entry entry : entries) {
            if (selected.contains(entry.id)) continue;
            if (!needle.isEmpty() && !entry.labelLower().contains(needle) && !entry.id.contains(needle)) continue;
            available.add(entry);
        }

        if (!needle.isEmpty()) available.sort(Comparator.comparingInt(entry -> matchRank(entry, needle)));
        cachedRawValue = rawValue;
        cachedSearch = needle;
        cachedSelectedIds = Set.copyOf(selected);
        cachedSelectedRows = List.copyOf(selectedRows);
        cachedAvailableRows = List.copyOf(available);
    }

    private Entry entry(String id) {
        return entriesById.get(id);
    }

    private static int matchRank(Entry entry, String needle) {
        String path = entry.id();
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
        if (path.equals(needle)) return 0;
        if (path.startsWith(needle)) return 1;
        if (path.contains(needle)) return 2;
        String label = entry.labelLower();
        if (label.equals(needle)) return 3;
        if (label.startsWith(needle)) return 4;
        return 5;
    }

    private List<Entry> buildEntries(Kind type) {
        List<Entry> out = new ArrayList<>();
        switch (type) {
            case ITEM_LIST -> BuiltInRegistries.ITEM.forEach(item -> {
                if (item != Items.AIR) out.add(new Entry(id(BuiltInRegistries.ITEM.getKey(item)), item.getName(item.getDefaultInstance()).getString(), "", AutismDisplayItemUtils.toStack(item), false));
            });
            case BLOCK_LIST -> BuiltInRegistries.BLOCK.forEach(block -> {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);

                if (block != Blocks.AIR && block != Blocks.BARRIER && id != null
                    && !id.getPath().endsWith("_wall_banner")
                    && (!placeableBlocksOnly() || ScaffoldModule.isPlaceableBlockChoice(block))) {
                    out.add(new Entry(id(id), block.getName().getString(), "", AutismDisplayItemUtils.toStack(block), false));
                }
            });
            case ENTITY_TYPE_LIST -> {
                Map<EntityType<?>, ItemStack> spawnEggs = spawnEggIcons();
                BuiltInRegistries.ENTITY_TYPE.forEach(typeEntry -> {

                    if (typeEntry == EntityTypes.ITEM) return;
                    ItemStack icon = spawnEggs.getOrDefault(typeEntry, ItemStack.EMPTY);
                    out.add(new Entry(id(BuiltInRegistries.ENTITY_TYPE.getKey(typeEntry)), typeEntry.getDescription().getString(), entityGroup(typeEntry), icon, icon.isEmpty()));
                });
            }
            case SOUND_EVENT_LIST -> BuiltInRegistries.SOUND_EVENT.forEach(sound -> {
                Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
                if (soundId != null) out.add(new Entry(id(soundId), soundLabel(soundId), soundGroup(soundId), soundIcon(soundId, spawnEggIcons()), false));
            });
            case STORAGE_LIST -> {

                for (ModuleStorageEsp.Target target : ModuleStorageEsp.TARGETS) {
                    ItemStack icon = target.icon();
                    out.add(new Entry(target.id, target.label, target.group, icon, icon.isEmpty()));
                }
            }
            default -> {
            }
        }
        out.sort(Comparator.comparing((Entry entry) -> entry.group).thenComparing(Entry::labelLower));
        return out;
    }

    private Map<EntityType<?>, ItemStack> spawnEggIcons() {
        Map<EntityType<?>, ItemStack> out = new HashMap<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            var data = item.components().get(DataComponents.ENTITY_DATA);
            if (data != null) out.put(data.type(), AutismDisplayItemUtils.toStack(item));
        });
        return out;
    }

    private ItemStack soundIcon(Identifier soundId, Map<EntityType<?>, ItemStack> spawnEggs) {
        if (soundId == null) return AutismDisplayItemUtils.toStack(Items.NOTE_BLOCK);
        String path = soundId.getPath().toLowerCase(Locale.ROOT);
        String normalized = path.replace('.', '_');
        String[] parts = normalized.split("_+");

        ItemStack known = knownSoundIcon(normalized);
        if (!known.isEmpty()) return known;

        if (path.startsWith("entity.")) {
            ItemStack entity = resolveEntityIcon(parts, spawnEggs);
            if (!entity.isEmpty()) return entity;
        }
        if (path.startsWith("block.")) {
            ItemStack block = resolveBlockSoundIcon(parts);
            if (!block.isEmpty()) return block;
        }
        if (path.startsWith("item.")) {
            ItemStack item = resolveItemSoundIcon(parts);
            if (!item.isEmpty()) return item;
        }

        ItemStack registry = resolveAnyRegistryIcon(parts, spawnEggs);
        return registry.isEmpty() ? AutismDisplayItemUtils.toStack(Items.NOTE_BLOCK) : registry;
    }

    private ItemStack knownSoundIcon(String normalizedPath) {
        if (normalizedPath.contains("fishing_bobber")) return AutismDisplayItemUtils.toStack(Items.FISHING_ROD);
        if (normalizedPath.contains("lava")) return AutismDisplayItemUtils.toStack(Items.LAVA_BUCKET);
        if (normalizedPath.contains("water") || normalizedPath.contains("rain") || normalizedPath.contains("bubble")) return AutismDisplayItemUtils.toStack(Items.WATER_BUCKET);
        if (normalizedPath.contains("bucket_empty_lava") || normalizedPath.contains("bucket_fill_lava")) return AutismDisplayItemUtils.toStack(Items.LAVA_BUCKET);
        if (normalizedPath.contains("bucket_empty") || normalizedPath.contains("bucket_fill")) return AutismDisplayItemUtils.toStack(Items.BUCKET);
        if (normalizedPath.contains("firework")) return AutismDisplayItemUtils.toStack(Items.FIREWORK_ROCKET);
        if (normalizedPath.contains("experience_orb")) return AutismDisplayItemUtils.toStack(Items.EXPERIENCE_BOTTLE);
        if (normalizedPath.contains("lightning") || normalizedPath.contains("thunder")) return AutismDisplayItemUtils.toStack(Items.LIGHTNING_ROD.weathering().unaffected());
        if (normalizedPath.contains("music_disc_")) return resolveItemIcon(normalizedPath.substring(normalizedPath.indexOf("music_disc_")));
        if (normalizedPath.startsWith("music_") || normalizedPath.startsWith("record_")) return AutismDisplayItemUtils.toStack(Items.JUKEBOX);
        if (normalizedPath.contains("goat_horn")) return AutismDisplayItemUtils.toStack(Items.GOAT_HORN);
        if (normalizedPath.contains("note_block")) return AutismDisplayItemUtils.toStack(Items.NOTE_BLOCK);
        if (normalizedPath.contains("ui_") || normalizedPath.startsWith("ui")) return AutismDisplayItemUtils.toStack(Items.COMPASS);
        if (normalizedPath.contains("ambient_cave")) return AutismDisplayItemUtils.toStack(Items.TORCH);
        if (normalizedPath.contains("ambient_underwater")) return AutismDisplayItemUtils.toStack(Items.WATER_BUCKET);
        return ItemStack.EMPTY;
    }

    private ItemStack resolveEntityIcon(String[] parts, Map<EntityType<?>, ItemStack> spawnEggs) {
        for (String candidate : candidates(parts, 1)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(candidate)).orElse(null);
            if (type != null) {
                ItemStack icon = spawnEggs.getOrDefault(type, ItemStack.EMPTY);
                if (!icon.isEmpty()) return icon;
            }
        }
        if (containsPart(parts, "player")) return AutismDisplayItemUtils.toStack(Items.PLAYER_HEAD);
        if (containsPart(parts, "arrow")) return AutismDisplayItemUtils.toStack(Items.ARROW);
        if (containsPart(parts, "trident")) return AutismDisplayItemUtils.toStack(Items.TRIDENT);
        if (containsPart(parts, "boat")) return AutismDisplayItemUtils.toStack(Items.OAK_BOAT);
        if (containsPart(parts, "minecart")) return AutismDisplayItemUtils.toStack(Items.MINECART);
        if (containsPart(parts, "item")) return AutismDisplayItemUtils.toStack(Items.ITEM_FRAME);
        return ItemStack.EMPTY;
    }

    private ItemStack resolveBlockSoundIcon(String[] parts) {
        for (String candidate : candidates(parts, 1)) {
            ItemStack icon = resolveBlockIcon(candidate);
            if (!icon.isEmpty()) return icon;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack resolveItemSoundIcon(String[] parts) {
        for (String candidate : candidates(parts, 1)) {
            ItemStack icon = resolveItemIcon(candidate);
            if (!icon.isEmpty()) return icon;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack resolveAnyRegistryIcon(String[] parts, Map<EntityType<?>, ItemStack> spawnEggs) {
        for (String candidate : candidates(parts, 0)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(candidate)).orElse(null);
            if (type != null) {
                ItemStack icon = spawnEggs.getOrDefault(type, ItemStack.EMPTY);
                if (!icon.isEmpty()) return icon;
            }
            ItemStack item = resolveItemIcon(candidate);
            if (!item.isEmpty()) return item;
            ItemStack block = resolveBlockIcon(candidate);
            if (!block.isEmpty()) return block;
        }
        return ItemStack.EMPTY;
    }

    private List<String> candidates(String[] parts, int start) {
        List<String> out = new ArrayList<>();
        for (int len = Math.min(4, parts.length - start); len >= 1; len--) {
            for (int i = start; i + len <= parts.length; i++) {
                String candidate = joinParts(parts, i, len);
                if (!candidate.isBlank() && !isSoundEventWord(candidate)) out.add(candidate);
            }
        }
        return out;
    }

    private String joinParts(String[] parts, int start, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            String part = parts[start + i];
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append('_');
            builder.append(part);
        }
        return builder.toString();
    }

    private boolean isSoundEventWord(String candidate) {
        return switch (candidate) {
            case "ambient", "angry", "attack", "big", "break", "breathe", "burp", "charge", "click", "clicking",
                "close", "crack", "death", "dig", "drink", "eat", "empty", "fall", "fill", "flap", "fly", "growl",
                "hit", "hurt", "idle", "land", "loop", "open", "place", "pop", "ready", "shoot", "small", "splash",
                "step", "swim", "throw", "use", "walk" -> true;
            default -> false;
        };
    }

    private boolean containsPart(String[] parts, String part) {
        for (String value : parts) if (part.equals(value)) return true;
        return false;
    }

    private ItemStack resolveItemIcon(String path) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : AutismDisplayItemUtils.toStack(item);
    }

    private ItemStack resolveBlockIcon(String path) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElse(Blocks.AIR);
        if (block == Blocks.AIR || block.asItem() == Items.AIR) return ItemStack.EMPTY;
        return AutismDisplayItemUtils.toStack(block);
    }

    private Entry fallbackEntry(String id) {
        return fallbackEntry(id, id);
    }

    private Entry fallbackEntry(String label, String id) {
        return new Entry(id, label, "", ItemStack.EMPTY, false);
    }

    private String entityGroup(EntityType<?> type) {
        MobCategory category = type.getCategory();
        return switch (category) {
            case CREATURE -> "Animals";
            case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> "Water Animals";
            case MONSTER -> "Monsters";
            case AMBIENT -> "Ambient";
            default -> "Misc";
        };
    }

    private String soundLabel(Identifier id) {
        if (id == null) return "";
        String path = id.getPath().replace('.', ' ').replace('_', ' ');
        String[] parts = path.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? id.toString() : out.toString();
    }

    private String soundGroup(Identifier id) {
        if (id == null) return "";
        String path = id.getPath();
        if (path.startsWith("entity.")) return "Entities";
        if (path.startsWith("block.")) return "Blocks";
        if (path.startsWith("item.")) return "Items";
        if (path.startsWith("music.") || path.startsWith("music_disc.") || path.startsWith("record.")) return "Music";
        if (path.startsWith("weather.")) return "Weather";
        if (path.startsWith("ambient.")) return "Ambient";
        if (path.startsWith("ui.")) return "UI";
        return id.getNamespace();
    }

    private String id(Identifier id) {
        return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
    }

    private CompactScrollbar.Metrics registryScrollbarMetrics(boolean available) {
        int x = panelX() + 10;
        int y = panelY() + HEADER_H + 9 + SEARCH_H + 10;
        int paneW = (panelW() - 30) / 2;
        int paneH = panelY() + panelH() - y - 12;
        int paneX = available ? x : x + paneW + 10;
        int listTop = y + 24;
        int listH = paneH - 28;
        return registryScrollbarMetrics(available, paneX, listTop, paneW, listH);
    }

    private CompactScrollbar.Metrics registryScrollbarMetrics(boolean available, int paneX, int listTop, int paneW, int listH) {
        int contentH = available ? availableContentHeight(filteredAvailable()) : selectedRows().size() * ROW_H;
        int scroll = available ? availableScroll : selectedScroll;
        return CompactScrollbar.compute(contentH, Math.max(1, listH), paneX + paneW - 6, listTop, 4, Math.max(1, listH), scroll);
    }

    private int availableContentHeight(List<Entry> rows) {
        int height = 0;
        String lastGroup = "";
        for (Entry entry : rows) {
            if (option != null && showsGroupHeaders(option) && !entry.group.equals(lastGroup)) {
                height += GROUP_H;
                lastGroup = entry.group;
            }
            height += ROW_H;
        }
        return height;
    }

    private static boolean showsGroupHeaders(Setting<?, ?> option) {
        return option.kind() == Kind.ENTITY_TYPE_LIST
            || option.kind() == Kind.SOUND_EVENT_LIST
            || option.kind() == Kind.STORAGE_LIST;
    }

    private boolean insideAvailablePane(int mx, int my) {
        int x = panelX() + 10;
        int y = panelY() + HEADER_H + 9 + SEARCH_H + 10;
        int paneW = (panelW() - 30) / 2;
        int paneH = panelY() + panelH() - y - 12;
        int listTop = y + 24;
        int listH = paneH - 28;
        return mx >= x && mx < x + paneW && my >= listTop && my < listTop + listH;
    }

    private boolean insideSelectedPane(int mx, int my) {
        int x = panelX() + 10;
        int y = panelY() + HEADER_H + 9 + SEARCH_H + 10;
        int paneW = (panelW() - 30) / 2;
        int paneH = panelY() + panelH() - y - 12;
        int rightX = x + paneW + 10;
        int listTop = y + 24;
        int listH = paneH - 28;
        return mx >= rightX && mx < rightX + paneW && my >= listTop && my < listTop + listH;
    }

    private void clampPaneScrolls() {
        availableScroll = clamp(availableScroll, 0, registryScrollbarMetrics(true).maxScroll());
        selectedScroll = clamp(selectedScroll, 0, registryScrollbarMetrics(false).maxScroll());
    }

    private int snapSelectedScroll(int offset) {
        return Math.max(0, offset / ROW_H) * ROW_H;
    }

    private String titleText() {
        return option == null ? "Select" : "Select " + option.label();
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

    private record Entry(String id, String label, String group, ItemStack icon, boolean iconFallback) {
        private Entry {
            label = label == null || label.isBlank() ? id : label;
            group = group == null ? "" : group;
            icon = icon == null ? ItemStack.EMPTY : icon;
        }

        String labelLower() {
            return label.toLowerCase(Locale.ROOT);
        }
    }

    private record SelectedRow(String id, Entry entry, boolean invalid) {
    }

    private record Hit(HitType type, int x, int y, int w, int h, String value) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum HitType {
        ADD,
        REMOVE,
        CLOSE
    }
}
