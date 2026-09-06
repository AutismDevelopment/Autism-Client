package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.components.CompactSymbolButton;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.util.AutismChatField;
import autismclient.util.AutismDisplayItemUtils;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class AutismItemPickerScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 330;
    private static final int HEADER_H = 24;
    private static final int SEARCH_H = 20;
    private static final int ROW_H = 28;
    private static final int ICON_SIZE = 20;
    private static final int BG = 0xCC050507;
    private static final int ROW = 0xAA111114;
    private static final int ROW_HOVER = 0xAA1C1214;
    private static final int ROW_SELECTED = 0xAA261114;
    private static final int TEXT = 0xFFF3ECE7;
    private static final int MUTED = 0xFFB79E9E;

    private static int muted() { return autismclient.util.AutismTheme.recolor(MUTED, autismclient.util.AutismTheme.Channel.TEXT); }
    private static final int RED = 0xFFFF3B3B;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<Entry> entries;
    private final List<Hit> hits = new ArrayList<>();
    private String selectedId;
    private AutismChatField searchField;
    private int scroll;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private String cachedSearch;
    private List<Entry> cachedRows = List.of();

    public AutismItemPickerScreen(Screen parent, String selectedId, Consumer<String> onPick) {
        super(Component.literal("Pick Item"));
        this.parent = parent;
        this.selectedId = normalize(selectedId);
        this.onPick = onPick == null ? ignored -> { } : onPick;
        this.entries = buildEntries();
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
            drawTopBar(graphics, x, y, panelW, panelH, mx, my);
            if (panelW < 180 || panelH < 140) {
                drawText(graphics, "Window too small.", x + 8, y + HEADER_H + 10, muted(), Math.max(0, panelW - 16));
                return;
            }

            int searchX = x + 10;
            int searchY = y + HEADER_H + 9;
            ensureSearchField(searchX, searchY, panelW - 20, SEARCH_H);
            searchField.render(graphics, mx, my, delta);

            int listX = x + 10;
            int listY = searchY + SEARCH_H + 9;
            int listW = panelW - 20;
            int listH = y + panelH - listY - 10;
            frame(graphics, listX, listY, listW, listH, 0xAA09090B, THEME.borderSoft());
            UiBounds clip = UiBounds.of(listX + 1, listY + 1, Math.max(1, listW - 8), Math.max(1, listH - 2));
            UiScissorStack.global().push(graphics, clip);
            try {
                drawRows(graphics, listX + 3, listY + 3, listW - 9, Math.max(1, listH - 6), mx, my);
            } finally {
                UiScissorStack.global().pop(graphics);
            }
            CompactScrollbar.Metrics metrics = scrollbarMetrics(listX, listY, listW, listH);
            CompactScrollbar.draw(graphics, metrics, metrics.contains(mx, my), scrollbarDragging);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
        List<Entry> rows = filteredRows();
        int maxScroll = Math.max(0, rows.size() * ROW_H - h + 4);
        scroll = clamp(scroll, 0, maxScroll);
        if (rows.isEmpty()) {
            drawText(graphics, "No items matched.", x + 4, y + 6, muted(), w - 8);
            return;
        }
        int first = clamp(scroll / ROW_H, 0, Math.max(0, rows.size() - 1));
        int last = clamp((scroll + h - 1) / ROW_H + 1, 0, rows.size() - 1);
        for (int i = first; i <= last; i++) {
            Entry entry = rows.get(i);
            int rowY = y - scroll + i * ROW_H;
            if (rowY + ROW_H <= y || rowY >= y + h) continue;
            drawRow(graphics, entry, x, rowY, w, mx, my);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int w, int mx, int my) {
        boolean selected = entry.id.equals(selectedId);
        boolean over = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        CompactSurfaces.tintedRow(graphics, x, y, w, ROW_H - 1, selected ? ROW_SELECTED : over ? ROW_HOVER : ROW);
        drawIcon(graphics, entry.icon, x + 6, y + Math.max(2, (ROW_H - ICON_SIZE) / 2));
        int textX = x + 6 + ICON_SIZE + 7;
        int textMax = Math.max(1, w - (textX - x) - 8);
        drawText(graphics, entry.label, textX, y + 4, selected ? RED : TEXT, textMax);
        drawText(graphics, entry.id, textX, y + 17, muted(), textMax);
        hits.add(new Hit(HitType.ITEM, x, y, w, ROW_H, entry.id));
    }

    private void drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.25f, 1.25f);
        graphics.item(stack, Math.round(x / 1.25f), Math.round(y / 1.25f));
        graphics.pose().popMatrix();
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H, "Pick Item",
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
        CompactScrollbar.Metrics metrics = scrollbarMetrics();
        if (metrics.hasScroll() && metrics.contains(mx, my)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            scroll = CompactScrollbar.scrollFromThumb(metrics, my, scrollbarGrabOffset);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            switch (hit.type) {
                case CLOSE -> onClose();
                case ITEM -> pick(hit.value);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollbarDragging = false;
        if (searchField != null) {
            searchField.mouseReleased(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (scrollbarDragging) {
            scroll = CompactScrollbar.scrollFromThumb(scrollbarMetrics(), my, scrollbarGrabOffset);
            return true;
        }
        if (searchField != null && searchField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int amount = scrollY < 0 ? ROW_H : -ROW_H;
        scroll = clamp(scroll + amount, 0, scrollbarMetrics().maxScroll());
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
            searchField = new AutismChatField(net.minecraft.client.Minecraft.getInstance(), font, x, y, w, h, true);
            searchField.setPlaceholder(Component.literal("Search item id or name"));
            searchField.setChangedListener(value -> scroll = 0);
            searchField.setSubmitHandler(value -> {
                List<Entry> rows = filteredRows();
                if (!rows.isEmpty()) pick(rows.get(0).id);
                return true;
            });
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

    private void pick(String id) {
        selectedId = normalize(id);
        onPick.accept(selectedId);
        onClose();
    }

    private List<Entry> filteredRows() {
        String needle = searchText().trim().toLowerCase(Locale.ROOT);
        if (needle.equals(cachedSearch)) return cachedRows;
        cachedSearch = needle;
        if (needle.isEmpty()) {
            cachedRows = entries;
            return cachedRows;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.id.contains(needle) || entry.labelLower.contains(needle)) out.add(entry);
        }
        cachedRows = List.copyOf(out);
        return cachedRows;
    }

    private List<Entry> buildEntries() {
        List<Entry> out = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item == Items.AIR) return;
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) return;
            ItemStack stack = AutismDisplayItemUtils.toStack(item);
            String itemId = id.toString().toLowerCase(Locale.ROOT);
            out.add(new Entry(itemId, item.getName(item.getDefaultInstance()).getString(), stack));
        });
        out.sort(Comparator.comparing(Entry::labelLower).thenComparing(entry -> entry.id));
        return List.copyOf(out);
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        int x = panelX() + 10;
        int y = panelY() + HEADER_H + 9 + SEARCH_H + 9;
        int w = panelW() - 20;
        int h = panelY() + panelH() - y - 10;
        return scrollbarMetrics(x, y, w, h);
    }

    private CompactScrollbar.Metrics scrollbarMetrics(int listX, int listY, int listW, int listH) {
        int viewH = Math.max(1, listH - 6);
        int contentH = filteredRows().size() * ROW_H;
        return CompactScrollbar.compute(contentH, viewH, listX + listW - 6, listY + 1, 4, Math.max(1, listH - 2), scroll);
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

    private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
        UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        String display = UiText.trimToWidth(font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Entry(String id, String label, String labelLower, ItemStack icon) {
        private Entry(String id, String label, ItemStack icon) {
            this(id, label == null || label.isBlank() ? id : label, (label == null ? id : label).toLowerCase(Locale.ROOT), icon == null ? ItemStack.EMPTY : icon);
        }
    }

    private record Hit(HitType type, int x, int y, int w, int h, String value) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum HitType {
        CLOSE,
        ITEM
    }
}
