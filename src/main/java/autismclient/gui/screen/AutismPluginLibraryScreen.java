package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.util.AutismColors;
import autismclient.util.AutismConfig;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static autismclient.gui.screen.AutismScreenPalette.*;

public class AutismPluginLibraryScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();

    private static final int PANEL_WIDTH = 472;
    private static final int PANEL_MARGIN = 12;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_HEIGHT = 86;
    private static final int LIST_TOP = 116;
    private static final int LIST_HEADER_HEIGHT = 20;
    private static final int LIST_BOTTOM_MARGIN = 12;
    private static final int LIST_SCROLLBAR_WIDTH = 4;
    private static final int LIST_SCROLLBAR_GUTTER = 12;
    private static final int CHILD_INDENT = 16;
    private static final int ROW_BTN_H = 16;
    private static final int ROW_BTN_GAP = 5;
    private static final int COPY_W = 42;
    private static final int DELETE_W = 18;

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();

    private EditBox searchField;
    private String searchQuery = "";
    private Mode mode = Mode.BY_SERVER;
    private List<Row> allRows = List.of();
    private int parentCount;
    private int listScrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private long lastRevision = Long.MIN_VALUE;

    public AutismPluginLibraryScreen(Screen parent) {
        super(Component.literal("Plugin Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldX = panelX() + 18;
        int fieldW = Math.max(40, panelWidth() - 36);
        this.searchField = new EditBox(this.font, fieldX, TOP_PANEL_Y + 50, fieldW, 18, Component.literal("Search"));
        this.searchField.setHint(Component.literal(searchHint()));
        this.searchField.setMaxLength(128);
        this.searchField.setValue(searchQuery);
        this.searchField.setResponder(value -> {
            searchQuery = safeTrim(value);
            listScrollOffset = 0;
            rebuild();
        });
        this.addRenderableWidget(this.searchField);
        lastRevision = AutismConfig.getGlobal().pluginScanRevision();
        rebuild();
    }

    @Override
    public void tick() {
        long rev = AutismConfig.getGlobal().pluginScanRevision();
        if (rev != lastRevision) {
            lastRevision = rev;
            rebuild();
        }
    }

    private String searchHint() {
        return mode == Mode.BY_SERVER ? "Search servers or plugins..." : "Search plugins...";
    }

    private void setMode(Mode next) {
        if (mode == next) return;
        mode = next;
        expanded.clear();
        listScrollOffset = 0;
        if (searchField != null) searchField.setHint(Component.literal(searchHint()));
        rebuild();
    }

    private void toggleExpand(String key) {
        if (!expanded.remove(key)) expanded.add(key);
        rebuild();
    }

    private List<ServerRecord> loadServers() {
        Map<String, ServerRecord> byAddress = new LinkedHashMap<>();
        Map<String, AutismConfig.PluginScanCacheEntry> all = AutismConfig.getGlobal().allPluginScans();
        for (Map.Entry<String, AutismConfig.PluginScanCacheEntry> item : all.entrySet()) {
            AutismConfig.PluginScanCacheEntry entry = item.getValue();
            if (entry == null || entry.plugins == null || entry.plugins.isEmpty()) continue;
            String address = entry.serverAddress != null && !entry.serverAddress.isBlank()
                ? entry.serverAddress : addressFromKey(item.getKey());
            if (address == null || address.isBlank()) continue;
            String lower = address.toLowerCase(Locale.ROOT);
            ServerRecord existing = byAddress.get(lower);
            if (existing != null && existing.scannedAtMs >= entry.scannedAtMs) continue;
            String name = entry.serverName != null && !entry.serverName.isBlank() ? entry.serverName : address;
            byAddress.put(lower, new ServerRecord(address, name, dedupeSorted(entry.plugins), entry.scannedAtMs));
        }
        List<ServerRecord> servers = new ArrayList<>(byAddress.values());
        servers.sort(Comparator.comparingLong((ServerRecord r) -> r.scannedAtMs).reversed());
        return servers;
    }

    private static String addressFromKey(String key) {
        if (key == null) return "";
        int cut = key.lastIndexOf('|');
        return cut > 0 ? key.substring(0, cut) : key;
    }

    private static List<String> dedupeSorted(List<String> plugins) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String p : plugins) {
            if (p != null && !p.isBlank()) set.add(p.trim());
        }
        return new ArrayList<>(set);
    }

    private List<Row> buildRows() {
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<ServerRecord> servers = loadServers();
        return mode == Mode.BY_SERVER ? buildServerRows(servers, query) : buildPluginRows(servers, query);
    }

    private List<Row> buildServerRows(List<ServerRecord> servers, String query) {
        List<Row> rows = new ArrayList<>();
        for (ServerRecord server : servers) {
            boolean nameHit = query.isEmpty() || fuzzy(server.name, query) || fuzzy(server.address, query);
            boolean pluginHit = false;
            if (!query.isEmpty()) {
                for (String p : server.plugins) {
                    if (fuzzy(p, query)) { pluginHit = true; break; }
                }
            }
            if (!query.isEmpty() && !nameHit && !pluginHit) continue;

            String key = "srv:" + server.address.toLowerCase(Locale.ROOT);
            boolean open = expanded.contains(key);
            Row row = Row.parent(RowType.SERVER, key, server.label,
                server.plugins.size() + (server.plugins.size() == 1 ? " plugin  " : " plugins  ") + relativeTime(server.scannedAtMs), open);
            row.address = server.address;
            row.plugins = server.plugins;
            rows.add(row);
            if (open) {

                List<String> shown = server.plugins;
                if (!query.isEmpty()) {
                    List<String> matched = new ArrayList<>();
                    for (String plugin : server.plugins) {
                        if (fuzzy(plugin, query)) matched.add(plugin);
                    }
                    if (!matched.isEmpty()) shown = matched;
                }
                for (String plugin : shown) rows.add(Row.child(RowType.SERVER_PLUGIN, plugin, ""));
            }
        }
        parentCount = rows.isEmpty() ? 0 : (int) rows.stream().filter(r -> r.type == RowType.SERVER).count();
        return rows;
    }

    private List<Row> buildPluginRows(List<ServerRecord> servers, String query) {
        Map<String, List<ServerRecord>> byPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ServerRecord server : servers) {
            for (String plugin : server.plugins) {
                if (!query.isEmpty() && !fuzzy(plugin, query)) continue;
                byPlugin.computeIfAbsent(plugin, k -> new ArrayList<>()).add(server);
            }
        }
        List<Map.Entry<String, List<ServerRecord>>> ordered = new ArrayList<>(byPlugin.entrySet());
        ordered.sort(Comparator
            .<Map.Entry<String, List<ServerRecord>>>comparingInt(e -> e.getValue().size()).reversed()
            .thenComparing(e -> e.getKey().toLowerCase(Locale.ROOT)));

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<ServerRecord>> entry : ordered) {
            int count = entry.getValue().size();
            String key = "plg:" + entry.getKey().toLowerCase(Locale.ROOT);
            boolean open = expanded.contains(key);
            Row row = Row.parent(RowType.PLUGIN, key, entry.getKey(),
                "on " + count + (count == 1 ? " server" : " servers"), open);
            row.servers = entry.getValue();
            rows.add(row);
            if (open) {
                for (ServerRecord server : entry.getValue()) {
                    rows.add(Row.child(RowType.PLUGIN_SERVER, server.label, relativeTime(server.scannedAtMs)));
                }
            }
        }
        parentCount = ordered.size();
        return rows;
    }

    private static boolean fuzzy(String value, String queryLower) {
        if (queryLower.isEmpty()) return true;
        if (value == null) return false;
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains(queryLower)) return true;
        int vi = 0, qi = 0, misses = 0, maxMisses = Math.max(1, queryLower.length() / 3);
        while (vi < v.length() && qi < queryLower.length()) {
            if (v.charAt(vi) == queryLower.charAt(qi)) qi++;
            else if (qi > 0) misses++;
            if (misses > maxMisses) return false;
            vi++;
        }
        return qi == queryLower.length();
    }

    private static String relativeTime(long ms) {
        if (ms <= 0L) return "unknown";
        long d = System.currentTimeMillis() - ms;
        if (d < 60_000L) return "just now";
        long mins = d / 60_000L;
        if (mins < 60) return mins + "m ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        long days = hrs / 24;
        if (days < 30) return days + "d ago";
        long months = days / 30;
        if (months < 12) return months + "mo ago";
        return (months / 12) + "y ago";
    }

    private void rebuild() {
        buttons.clear();
        rowHits.clear();
        visibleRows.clear();

        buttons.add(CompactOverlayButton.create(10, 10, 56, 20, Component.literal("Back"), b -> onClose()));

        int toggleY = TOP_PANEL_Y + 26;
        int toggleW = Math.max(70, Math.min(110, (panelWidth() - 36 - 6) / 2));
        int toggleX = panelX() + 18;
        buttons.add(CompactOverlayButton.create(toggleX, toggleY, toggleW, 18, Component.literal("By Server"), b -> setMode(Mode.BY_SERVER))
            .setVariant(mode == Mode.BY_SERVER ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(toggleX + toggleW + 6, toggleY, toggleW, 18, Component.literal("By Plugin"), b -> setMode(Mode.BY_PLUGIN))
            .setVariant(mode == Mode.BY_PLUGIN ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY));

        allRows = buildRows();
        int maxScroll = maxScroll(allRows.size());
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        int firstVisible = ROW_HEIGHT <= 0 ? 0 : listScrollOffset / ROW_HEIGHT;
        int rowY = rowsTop() - (listScrollOffset % ROW_HEIGHT);
        for (int i = firstVisible; i < allRows.size() && rowY < rowsBottom(); i++, rowY += ROW_HEIGHT) {
            if (rowY + ROW_HEIGHT <= rowsTop()) continue;
            Row row = allRows.get(i);
            row.renderY = rowY;
            visibleRows.add(row);

            boolean fullyVisible = rowY >= rowsTop() && rowY + ROW_HEIGHT <= rowsBottom();
            boolean parent = row.type == RowType.SERVER || row.type == RowType.PLUGIN;
            if (parent && fullyVisible) {
                int btnY = rowY + (ROW_HEIGHT - ROW_BTN_H) / 2;
                int cursor = rowRight();
                if (row.type == RowType.SERVER) {
                    cursor -= DELETE_W;
                    buttons.add(CompactOverlayButton.create(cursor, btnY, DELETE_W, ROW_BTN_H, Component.empty(), b -> deleteServer(row))
                        .setVariant(CompactOverlayButton.Variant.DANGER)
                        .setIcon(autismclient.util.AutismUiIcons.TRASH));
                    cursor -= ROW_BTN_GAP;
                }
                cursor -= COPY_W;
                buttons.add(CompactOverlayButton.create(cursor, btnY, COPY_W, ROW_BTN_H, Component.literal("Copy"), b -> copyRow(row))
                    .setVariant(CompactOverlayButton.Variant.SECONDARY));
                rowHits.add(new RowHit(rowX(), rowY, Math.max(1, cursor - 4 - rowX()), ROW_HEIGHT, row.key));
            }
        }
    }

    private void deleteServer(Row row) {
        if (row.type != RowType.SERVER || row.address == null) return;
        AutismConfig.getGlobal().removePluginScan(row.address);
        expanded.remove(row.key);
        lastRevision = AutismConfig.getGlobal().pluginScanRevision();
        rebuild();
    }

    private void copyRow(Row row) {
        String text;
        String note;
        if (row.type == RowType.SERVER && row.plugins != null) {
            text = String.join("\n", row.plugins);
            note = "Copied " + row.plugins.size() + " plugins from " + row.primary;
        } else if (row.type == RowType.PLUGIN && row.servers != null) {

            List<String> names = new ArrayList<>();
            for (ServerRecord s : row.servers) names.add(s.label);
            text = String.join("\n", names);
            note = "Copied " + names.size() + " servers running " + row.primary;
        } else {
            return;
        }
        if (text.isBlank() || this.minecraft == null) return;
        this.minecraft.keyboardHandler.setClipboard(text);
        toast(note, SUCCESS);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            drawPanel(graphics, panelX() + 10, TOP_PANEL_Y, panelWidth() - 20, TOP_PANEL_HEIGHT, PANEL_BG);
            drawPanel(graphics, listX(), LIST_TOP, listWidth(), listPanelHeight(), PANEL_BG_SOFT);

            drawText(graphics, "Plugin Library", panelX() + 18, TOP_PANEL_Y + 9, TEXT, panelWidth() - 200);
            String count = parentCount + (mode == Mode.BY_SERVER
                ? (parentCount == 1 ? " server" : " servers")
                : (parentCount == 1 ? " plugin" : " plugins"));
            int countW = UiText.width(this.font, count, bodyFont(), MUTED);
            drawText(graphics, count, panelX() + panelWidth() - 18 - countW, TOP_PANEL_Y + 9, MUTED, countW + 4);

            String header = mode == Mode.BY_SERVER ? "Servers you have scanned" : "Plugin -> servers running it";
            drawText(graphics, header, listX() + 8, LIST_TOP + 6, MUTED, listWidth() - 16);

            String queryLower = searchQuery.toLowerCase(Locale.ROOT);
            UiScissorStack.global().push(graphics, UiBounds.of(rowX(), rowsTop(),
                Math.max(0, rowRight() + LIST_SCROLLBAR_GUTTER - rowX()), Math.max(0, rowsBottom() - rowsTop())));
            try {
                if (allRows.isEmpty()) {
                    drawText(graphics, emptyMessage(), rowX() + 4, rowsTop() + 6, MUTED, rowRight() - rowX());
                } else {
                    for (Row row : visibleRows) renderRow(graphics, row, mx, my, queryLower);
                }
            } finally {
                UiScissorStack.global().pop(graphics);
            }

            for (CompactOverlayButton b : buttons) CompactOverlayButton.renderStyled(graphics, this.font, b, mx, my);

            if (!compactLayout()) {
                CompactScrollbar.Metrics sb = scrollbarMetrics(allRows.size());
                CompactScrollbar.draw(graphics, sb, sb.contains(mx, my), scrollbarDragging);
            }

            super.extractRenderState(graphics, mx, my, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private String emptyMessage() {
        if (searchQuery.isEmpty()) return "No saved scans yet. Join a server with Auto Probe on to record its plugins.";
        return "Nothing matches \"" + searchQuery + "\".";
    }

    private void renderRow(GuiGraphicsExtractor graphics, Row row, int mx, int my, String queryLower) {
        boolean child = row.type == RowType.SERVER_PLUGIN || row.type == RowType.PLUGIN_SERVER;
        int x = rowX();
        int y = row.renderY;
        int w = rowRight() - rowX();
        boolean hovered = my >= y && my < y + ROW_HEIGHT && mx >= x && mx < rowRight();

        if (!child) {
            int fill = hovered ? 0x24FFFFFF : 0x14FFFFFF;
            UiRenderer.rect(graphics, UiBounds.of(x, y + 1, w, ROW_HEIGHT - 2), fill);
            UiRenderer.rect(graphics, UiBounds.of(x, y + 1, 2, ROW_HEIGHT - 2), row.expanded ? BORDER_ACTIVE : 0x33FFFFFF);
        }

        int textY = y + (ROW_HEIGHT - 8) / 2;
        int textLeft = x + 8 + (child ? CHILD_INDENT : 0);
        if (!child) {
            drawText(graphics, row.expanded ? "v" : ">", x + 6, textY, MUTED, 8);
            textLeft += 8;
        } else {
            drawText(graphics, "-", x + 8 + CHILD_INDENT - 8, textY, MUTED, 8);
        }

        int baseColor = child ? 0xFFCFCFCF : TEXT;
        int secondaryColor = MUTED;
        int secondaryW = row.secondary.isEmpty() ? 0 : UiText.width(this.font, row.secondary, bodyFont(), secondaryColor);

        int rowActionReserve = child ? 0 : (COPY_W + ROW_BTN_GAP + (row.type == RowType.SERVER ? DELETE_W + ROW_BTN_GAP : 0));
        int rightLimit = rowRight() - rowActionReserve - 4;
        int secondaryX = Math.max(textLeft + 20, rightLimit - secondaryW);
        int primaryMax = Math.max(20, (row.secondary.isEmpty() ? rightLimit : secondaryX - 8) - textLeft);

        drawHighlighted(graphics, row.primary, textLeft, textY, primaryMax, baseColor, queryLower);
        if (!row.secondary.isEmpty() && secondaryX > textLeft) {
            drawText(graphics, row.secondary, secondaryX, textY, secondaryColor, secondaryW + 2);
        }
    }

    private void drawHighlighted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int baseColor, String queryLower) {
        Identifier font = bodyFont();
        int idx = queryLower.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(queryLower);
        if (idx < 0 || UiText.width(this.font, text, font, baseColor) > maxWidth) {
            UiText.drawFitted(graphics, this.font, text, font, baseColor, x, y, maxWidth, false);
            return;
        }
        String pre = text.substring(0, idx);
        String mid = text.substring(idx, idx + queryLower.length());
        String post = text.substring(idx + queryLower.length());
        int accent = AutismColors.accent();
        UiText.draw(graphics, this.font, pre, font, baseColor, x, y, false);
        int midX = x + UiText.width(this.font, pre, font, baseColor);
        UiText.draw(graphics, this.font, mid, font, accent, midX, y, false);
        UiText.draw(graphics, this.font, post, font, baseColor, midX + UiText.width(this.font, mid, font, accent), y, false);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        UiText.drawFitted(graphics, this.font, text, bodyFont(), color, x, y, Math.max(1, maxWidth), false);
    }

    private static Identifier bodyFont() {
        return THEME.fontFor(UiTone.BODY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (compactLayout()) return super.mouseClicked(event, doubleClick);
        MouseButtonEvent ve = virtualEvent(event);
        double mx = ve.x();
        double my = ve.y();

        if (ve.button() == 0 && !compactLayout()) {
            CompactScrollbar.Metrics sb = scrollbarMetrics(allRows.size());
            if (sb.hasScroll() && sb.contains(mx, my)) {
                scrollbarDragging = true;
                scrollbarGrabOffset = sb.overThumb(mx, my) ? (int) Math.round(my) - sb.thumbY() : sb.thumbHeight() / 2;
                listScrollOffset = CompactScrollbar.scrollFromThumb(sb, my, scrollbarGrabOffset);
                rebuild();
                return true;
            }
        }

        for (CompactOverlayButton b : buttons) {
            if (CompactOverlayButton.fireIfHit(b, mx, my, ve.button())) return true;
        }

        if (ve.button() == 0) {
            for (RowHit hit : rowHits) {
                if (hit.contains(mx, my)) {
                    toggleExpand(hit.key);
                    return true;
                }
            }
        }
        return super.mouseClicked(ve, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent ve = virtualEvent(event);
        if (scrollbarDragging) {
            CompactScrollbar.Metrics sb = scrollbarMetrics(allRows.size());
            listScrollOffset = CompactScrollbar.scrollFromThumb(sb, ve.y(), scrollbarGrabOffset);
            rebuild();
            return true;
        }
        return super.mouseDragged(ve, AutismUiScale.toVirtual(deltaX), AutismUiScale.toVirtual(deltaY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (compactLayout()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        double vy = AutismUiScale.toVirtual(mouseY);
        double vx = AutismUiScale.toVirtual(mouseX);
        if (vx < listX() || vx > listX() + listWidth() || vy < rowsTop() || vy > rowsBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int maxScroll = maxScroll(allRows.size());
        int next = listScrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT;
        listScrollOffset = Math.max(0, Math.min(next, maxScroll));
        rebuild();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private int panelWidth() {
        return DirectLayout.fitPanelDimension(screenWidth(), PANEL_MARGIN, PANEL_WIDTH);
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelWidth(), PANEL_MARGIN);
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listWidth() {
        return panelWidth() - 20;
    }

    private int listPanelHeight() {
        return Math.max(ROW_HEIGHT + LIST_HEADER_HEIGHT, screenHeight() - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    private int rowsTop() {
        return LIST_TOP + LIST_HEADER_HEIGHT;
    }

    private int rowsBottom() {
        return LIST_TOP + listPanelHeight() - 6;
    }

    private int viewportHeight() {
        return Math.max(ROW_HEIGHT, rowsBottom() - rowsTop());
    }

    private int maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - viewportHeight());
    }

    private int rowX() {
        return listX() + 8;
    }

    private int rowRight() {
        return listX() + listWidth() - 8 - LIST_SCROLLBAR_GUTTER;
    }

    private CompactScrollbar.Metrics scrollbarMetrics(int rowCount) {
        int contentPixels = Math.max(0, rowCount) * ROW_HEIGHT;
        int trackX = listX() + listWidth() - 8;
        return CompactScrollbar.compute(contentPixels, viewportHeight(), trackX, rowsTop(),
            LIST_SCROLLBAR_WIDTH, viewportHeight(), listScrollOffset);
    }

    private boolean compactLayout() {
        return panelWidth() < 360 || screenHeight() < LIST_TOP + 44;
    }

    private enum Mode { BY_SERVER, BY_PLUGIN }

    private enum RowType { SERVER, SERVER_PLUGIN, PLUGIN, PLUGIN_SERVER }

    private static final class ServerRecord {
        final String address;
        final String name;

        final String label;
        final List<String> plugins;
        final long scannedAtMs;

        ServerRecord(String address, String name, List<String> plugins, long scannedAtMs) {
            this.address = address;
            this.name = name;
            this.label = address;
            this.plugins = plugins;
            this.scannedAtMs = scannedAtMs;
        }
    }

    private static final class Row {
        RowType type;
        String key = "";
        String primary = "";
        String secondary = "";
        boolean expanded;
        String address;
        List<String> plugins;
        List<ServerRecord> servers;
        int renderY;

        static Row parent(RowType type, String key, String primary, String secondary, boolean expanded) {
            Row row = new Row();
            row.type = type;
            row.key = key;
            row.primary = primary;
            row.secondary = secondary;
            row.expanded = expanded;
            return row;
        }

        static Row child(RowType type, String primary, String secondary) {
            Row row = new Row();
            row.type = type;
            row.primary = primary;
            row.secondary = secondary;
            return row;
        }
    }

    private static final class RowHit {
        final int x;
        final int y;
        final int width;
        final int height;
        final String key;

        RowHit(int x, int y, int width, int height, String key) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.key = key;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
}
