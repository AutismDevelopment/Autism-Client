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
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.WaypointsModule;
import autismclient.util.AutismChatField;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismUiScale;
import autismclient.util.AutismWaypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AutismWaypointsScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 460;
    private static final int PANEL_H = 330;
    private static final int HEADER_H = 24;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 22;
    private static final int SWATCH_W = 22;
    private static final int SWATCH_H = 14;
    private static final int NAME_FIELD_W = 140;
    private static final int PICKER_H = 48;
    private static final int BG = 0xCC050507;
    private static final int ROW = 0xAA111114;
    private static final int ROW_HOVER = 0xAA1C1214;
    private static final int TEXT = 0xFFF3ECE7;
    private static final int MUTED = 0xFFB79E9E;

    private static int muted() { return autismclient.util.AutismTheme.recolor(MUTED, autismclient.util.AutismTheme.Channel.TEXT); }
    private static final int RED = 0xFFFF3B3B;
    private static final int FALLBACK_DEFAULT_COLOR = 0xFF3BD7FF;

    private static final int[] PRESETS = {
        0xFFFF3B3B, 0xFFFF9F1A, 0xFFFFE93B, 0xFF35D873, 0xFF3BD7FF,
        0xFF4A7CFF, 0xFFB45BFF, 0xFFFF6EC7, 0xFFFFFFFF, 0xFF8C8C8C
    };

    private final Screen parent;
    private final String scope;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, AutismChatField> rowFields = new LinkedHashMap<>();
    private final List<String> visibleRowNames = new ArrayList<>();
    private AutismChatField entryField;
    private AutismChatField hexField;
    private int scroll;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private String pickerTarget;
    private String focusedName;
    private boolean syncingHex;
    private UiBounds pickerBounds;
    private long cachedRevision = Long.MIN_VALUE;
    private List<AutismWaypoints.Waypoint> cachedRows = List.of();

    public AutismWaypointsScreen(Screen parent) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
        this.scope = AutismWaypoints.scopeKey(net.minecraft.client.Minecraft.getInstance());
    }

    @Override
    protected void init() {
        ensureFields();
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
            pickerBounds = null;
            visibleRowNames.clear();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            int x = panelX();
            int y = panelY();
            int panelW = panelW();
            int panelH = panelH();
            ensureFields();
            syncFieldBounds();
            syncRowFields();
            drawTopBar(graphics, x, y, panelW, panelH, mx, my);
            if (panelW < 180 || panelH < 160) {
                drawText(graphics, "Window too small.", x + 8, y + HEADER_H + 10, muted(), Math.max(0, panelW - 16));
                return;
            }

            int fieldX = x + 10;
            int fieldY = y + HEADER_H + 10;
            entryField.render(graphics, mx, my, delta);
            button(graphics, "Add", x + panelW - 70, fieldY, 60, FIELD_H, CompactOverlayButton.Variant.SUCCESS, HitType.ADD, "", mx, my);

            int scopeY = fieldY + FIELD_H + 8;
            String scopeLabel = "unknown".equals(scope) ? "Scope: unknown (join a server or world)" : "Scope: " + scope;
            drawText(graphics, scopeLabel, fieldX, scopeY + 2, muted(), panelW - 20);

            int listY = scopeY + 16;
            if (pickerTarget != null) {
                drawPicker(graphics, fieldX, listY, panelW - 20, delta, mx, my);
                listY += PICKER_H + 6;
            }

            int listH = y + panelH - listY - 10;
            CompactListViewport.Layout listLayout = listLayout(listY, listH);
            frame(graphics, fieldX, listY, panelW - 20, listH, 0xAA09090B, THEME.borderSoft());
            listLayout.beginRows(graphics);
            try {
                drawRows(graphics, listLayout, delta, mx, my);
            } finally {
                listLayout.endRows(graphics);
            }
            listLayout.drawScrollbar(graphics, mx, my, scrollbarDragging);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, CompactListViewport.Layout layout, float delta, int mx, int my) {
        List<AutismWaypoints.Waypoint> rows = rows();
        scroll = clamp(scroll, 0, layout.maxScroll());
        int x = layout.x() + layout.contentInset();
        int w = layout.contentWidth();
        if (rows.isEmpty()) {
            drawText(graphics, "No waypoints for this scope.", x + 4, layout.y() + layout.contentInset() + 6, muted(), w - 8);
            return;
        }
        layout.forEachVisibleRow(rows.size(), (i, rowY) -> {
            AutismWaypoints.Waypoint waypoint = rows.get(i);
            visibleRowNames.add(waypoint.name());
            boolean over = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H;
            CompactSurfaces.tintedRow(graphics, x, rowY, w, ROW_H - 1, over ? ROW_HOVER : ROW);
            int swatchY = rowY + (ROW_H - SWATCH_H) / 2;
            boolean swatchOver = mx >= x + 4 && mx < x + 4 + SWATCH_W && my >= swatchY && my < swatchY + SWATCH_H;
            UiRenderer.rect(graphics, UiBounds.of(x + 4, swatchY, SWATCH_W, SWATCH_H), waypoint.color());
            UiRenderer.outline(graphics, UiBounds.of(x + 4, swatchY, SWATCH_W, SWATCH_H),
                swatchOver || waypoint.name().equals(pickerTarget)
                    ? autismclient.util.AutismTheme.recolor(RED, autismclient.util.AutismTheme.Channel.OUTLINE)
                    : THEME.borderSoft());
            int nameX = x + 4 + SWATCH_W + 7;
            AutismChatField nameField = rowFields.get(waypoint.name());
            if (nameField != null) {
                nameField.setX(nameX);
                nameField.setY(rowY + 3);
                nameField.setWidth(NAME_FIELD_W);
                nameField.setHeight(ROW_H - 8);
                nameField.render(graphics, mx, my, delta);
            }
            drawText(graphics, waypoint.x() + " " + waypoint.y() + " " + waypoint.z(),
                nameX + NAME_FIELD_W + 8, rowY + 7, muted(), w - (nameX - x) - NAME_FIELD_W - 40);
            CompactListRenderer.drawDeleteButton(graphics, x + w - 22, rowY + 4, 18, 14, over);

            hits.add(new Hit(HitType.ROW, x, rowY, w, ROW_H - 1, waypoint.name()));
            hits.add(new Hit(HitType.SWATCH, x + 4, swatchY, SWATCH_W, SWATCH_H, waypoint.name()));
            hits.add(new Hit(HitType.REMOVE, x + w - 22, rowY + 4, 18, 14, waypoint.name()));
        });
    }

    private void drawPicker(GuiGraphicsExtractor graphics, int x, int y, int w, float delta, int mx, int my) {
        pickerBounds = UiBounds.of(x, y, w, PICKER_H);
        frame(graphics, x, y, w, PICKER_H, 0xEE0B0B0E, THEME.borderSoft());
        int current = AutismWaypoints.get().colorOf(scope, pickerTarget, defaultColor());
        int swatchY = y + 6;
        int swatchX = x + 8;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = swatchX + i * (SWATCH_W + 4);
            if (px + SWATCH_W > x + w - 8) break;
            UiRenderer.rect(graphics, UiBounds.of(px, swatchY, SWATCH_W, SWATCH_H), PRESETS[i]);
            boolean over = mx >= px && mx < px + SWATCH_W && my >= swatchY && my < swatchY + SWATCH_H;
            UiRenderer.outline(graphics, UiBounds.of(px, swatchY, SWATCH_W, SWATCH_H),
                over || PRESETS[i] == current ? 0xFFFFFFFF : THEME.borderSoft());
            hits.add(new Hit(HitType.PRESET, px, swatchY, SWATCH_W, SWATCH_H, Integer.toHexString(i)));
        }
        int hexY = swatchY + SWATCH_H + 6;
        hexField.setX(x + 8);
        hexField.setY(hexY);
        hexField.setWidth(96);
        hexField.setHeight(FIELD_H - 2);
        hexField.render(graphics, mx, my, delta);
        UiRenderer.rect(graphics, UiBounds.of(x + 112, hexY, SWATCH_W, FIELD_H - 2), current);
        UiRenderer.outline(graphics, UiBounds.of(x + 112, hexY, SWATCH_W, FIELD_H - 2), THEME.borderSoft());
        drawText(graphics, "# hex applies live", x + 142, hexY + 5, muted(), x + w - 142 - 8);
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, CompactOverlayButton.Variant variant, HitType type, String value, int mx, int my) {
        CompactOverlayControls.action(graphics, font, x, y, w, h, label, variant, true, mx, my);
        hits.add(new Hit(type, x, y, w, h, value));
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H, "Waypoints",
            mx >= x && mx < x + width && my >= y && my < y + HEADER_H);
        UiBounds close = CompactScreenPanel.closeButton(bounds, HEADER_H);
        hits.add(new Hit(HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), ""));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (event.button() != 0) return true;

        AutismChatField focused = focusedName == null ? null : rowFields.get(focusedName);
        if (focused != null && !contains(focused, mx, my)) commitRename();
        CompactScrollbar.Metrics metrics = listScrollbarMetrics();
        if (metrics.hasScroll() && metrics.contains(mx, my)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            scroll = snapScrollToRows(CompactScrollbar.scrollFromThumb(metrics, my, scrollbarGrabOffset));
            return true;
        }
        ensureFields();
        if (pickerTarget != null && hexField.mouseClicked(mx, my, event.button())) {
            entryField.setFocused(false);
            return true;
        }
        if (entryField.mouseClicked(mx, my, event.button())) {
            hexField.setFocused(false);
            return true;
        }
        for (String name : visibleRowNames) {
            AutismChatField rowField = rowFields.get(name);
            if (rowField != null && rowField.mouseClicked(mx, my, event.button())) {
                focusRename(name);
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if ((hit.type == HitType.REMOVE || hit.type == HitType.SWATCH || hit.type == HitType.ROW) && !insideListBody(mx, my)) continue;
            switch (hit.type) {
                case CLOSE -> onClose();
                case ADD -> addEntry();
                case SWATCH -> openPicker(hit.value);
                case PRESET -> applyPreset(hit.value);
                case REMOVE -> removeEntry(hit.value);
                case ROW -> copyCoords(hit.value);
            }
            return true;
        }
        if (pickerTarget != null && (pickerBounds == null || !pickerBounds.contains(mx, my))) closePicker();
        entryField.setFocused(false);
        hexField.setFocused(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        if (!insideListBody(mx, my)) return true;
        scroll += scrollY < 0 ? ROW_H : -ROW_H;
        scroll = clamp(scroll, 0, listScrollbarMetrics().maxScroll());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        scrollbarDragging = false;
        AutismChatField focused = focusedName == null ? null : rowFields.get(focusedName);
        if (focused != null && focused.mouseReleased(mx, my, event.button())) return true;
        if (entryField != null && entryField.mouseReleased(mx, my, event.button())) return true;
        if (hexField != null && hexField.mouseReleased(mx, my, event.button())) return true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        AutismChatField focused = focusedName == null ? null : rowFields.get(focusedName);
        if (focused != null && focused.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (entryField != null && entryField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (hexField != null && hexField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (!scrollbarDragging) return true;
        scroll = snapScrollToRows(CompactScrollbar.scrollFromThumb(listScrollbarMetrics(), my, scrollbarGrabOffset));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        ensureFields();
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (focusedName != null) cancelRename();
            else if (pickerTarget != null) closePicker();
            else onClose();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (focusedName != null) commitRename();
            else if (entryField.isFocused()) addEntry();
            return true;
        }
        AutismChatField focused = focusedName == null ? null : rowFields.get(focusedName);
        if (focused != null && focused.keyPressed(input)) return true;
        if (entryField.keyPressed(input)) return true;
        if (pickerTarget != null && hexField.keyPressed(input)) return true;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        ensureFields();
        AutismChatField focused = focusedName == null ? null : rowFields.get(focusedName);
        if (focused != null && focused.charTyped(input)) return true;
        if (entryField.charTyped(input)) return true;
        if (pickerTarget != null && hexField.charTyped(input)) return true;
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void addEntry() {
        if (minecraft == null || minecraft.player == null) {
            AutismNotifications.warning("Join a world to add a waypoint here.");
            return;
        }
        AutismWaypoints store = AutismWaypoints.get();
        String name = entryText().trim();
        if (name.isEmpty()) name = store.nextName(scope, "Waypoint");
        int x = (int) Math.floor(minecraft.player.getX());
        int y = (int) Math.floor(minecraft.player.getY());
        int z = (int) Math.floor(minecraft.player.getZ());
        store.add(scope, new AutismWaypoints.Waypoint(name, x, y, z, defaultColor(), System.currentTimeMillis()));
        setEntryText("");
        clampScrollToList();
    }

    private void removeEntry(String name) {
        AutismWaypoints.get().remove(scope, name);
        if (name != null && name.equals(pickerTarget)) closePicker();
        if (name != null && name.equals(focusedName)) focusedName = null;
        clampScrollToList();
    }

    private void copyCoords(String name) {
        for (AutismWaypoints.Waypoint waypoint : rows()) {
            if (!waypoint.name().equals(name)) continue;
            String coords = waypoint.x() + " " + waypoint.y() + " " + waypoint.z();
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(coords);
            AutismNotifications.copied("Copied coordinates: " + coords + ".");
            return;
        }
    }

    private void syncRowFields() {
        List<AutismWaypoints.Waypoint> rows = rows();
        Map<String, AutismChatField> alive = new LinkedHashMap<>();
        for (AutismWaypoints.Waypoint waypoint : rows) {
            AutismChatField field = rowFields.get(waypoint.name());
            if (field == null) {
                field = new AutismChatField(minecraft, font, 0, 0, 1, ROW_H - 8, false);
                field.setMaxLength(64);
                field.setText(waypoint.name());
            } else if (!waypoint.name().equals(focusedName) && !waypoint.name().equals(field.getText())) {
                field.setText(waypoint.name());
            }
            alive.put(waypoint.name(), field);
        }
        if (focusedName != null && !alive.containsKey(focusedName)) focusedName = null;
        rowFields.clear();
        rowFields.putAll(alive);
    }

    private void focusRename(String name) {
        if (focusedName != null && !focusedName.equals(name)) commitRename();
        focusedName = name;
        AutismChatField field = rowFields.get(name);
        if (field != null) {
            field.setFocused(true);
            field.setSelectionEnd(field.getText().length());
        }
        if (entryField != null) entryField.setFocused(false);
        if (hexField != null) hexField.setFocused(false);
    }

    private void commitRename() {
        if (focusedName == null) return;
        String original = focusedName;
        AutismChatField field = rowFields.get(original);
        focusedName = null;
        if (field == null) return;
        field.setFocused(false);
        String renamed = field.getText().trim();
        if (renamed.equals(original)) {
            field.setText(original);
            return;
        }
        if (AutismWaypoints.get().rename(scope, original, renamed)) {
            rowFields.remove(original);
            rowFields.put(renamed, field);
            field.setText(renamed);
            if (original.equals(pickerTarget)) pickerTarget = renamed;
        } else {
            field.setText(original);
        }
    }

    private void cancelRename() {
        if (focusedName == null) return;
        AutismChatField field = rowFields.get(focusedName);
        if (field != null) {
            field.setText(focusedName);
            field.setFocused(false);
        }
        focusedName = null;
    }

    private static boolean contains(AutismChatField field, int mx, int my) {
        return mx >= field.getX() && mx < field.getX() + field.getWidth()
            && my >= field.getY() && my < field.getY() + field.getHeight();
    }

    private void openPicker(String name) {
        pickerTarget = name;
        syncingHex = true;
        try {
            hexField.setText(String.format(Locale.ROOT, "%06X",
                AutismWaypoints.get().colorOf(scope, name, defaultColor()) & 0xFFFFFF));
        } finally {
            syncingHex = false;
        }
        hexField.setFocused(true);
        entryField.setFocused(false);
    }

    private void closePicker() {
        pickerTarget = null;
        if (hexField != null) hexField.setFocused(false);
    }

    private void applyPreset(String hexIndex) {
        if (pickerTarget == null) return;
        int index;
        try {
            index = Integer.parseInt(hexIndex, 16);
        } catch (NumberFormatException e) {
            return;
        }
        if (index < 0 || index >= PRESETS.length) return;
        AutismWaypoints.get().setColor(scope, pickerTarget, PRESETS[index]);
        syncingHex = true;
        try {
            hexField.setText(String.format(Locale.ROOT, "%06X", PRESETS[index] & 0xFFFFFF));
        } finally {
            syncingHex = false;
        }
    }

    private void applyHex(String raw) {
        if (syncingHex || pickerTarget == null) return;
        Integer color = parseHex(raw);
        if (color != null) AutismWaypoints.get().setColor(scope, pickerTarget, color);
    }

    private static Integer parseHex(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() != 6) return null;
        try {
            return 0xFF000000 | Integer.parseInt(text, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int defaultColor() {
        Module module = ModuleRegistry.get("waypoints");
        return module instanceof WaypointsModule waypoints ? waypoints.defaultColor() : FALLBACK_DEFAULT_COLOR;
    }

    private List<AutismWaypoints.Waypoint> rows() {
        AutismWaypoints store = AutismWaypoints.get();
        long revision = store.revision();
        if (revision != cachedRevision) {
            cachedRevision = revision;
            cachedRows = store.list(scope);
        }
        return cachedRows;
    }

    private void ensureFields() {
        if (entryField == null) {
            entryField = new AutismChatField(minecraft, font, 0, 0, 1, FIELD_H, false);
            entryField.setMaxLength(64);
            entryField.setPlaceholder(Component.literal("Waypoint name"));
        }
        if (hexField == null) {
            hexField = new AutismChatField(minecraft, font, 0, 0, 1, FIELD_H - 2, false);
            hexField.setMaxLength(7);
            hexField.setPlaceholder(Component.literal("RRGGBB"));
            hexField.setFilter(text -> {
                if (text == null || text.isEmpty()) return true;
                int start = text.startsWith("#") ? 1 : 0;
                if (text.length() - start > 6) return false;
                for (int i = start; i < text.length(); i++) {
                    char chr = text.charAt(i);
                    boolean hex = (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
                    if (!hex) return false;
                }
                return true;
            });
            hexField.setChangedListener(this::applyHex);
        }
    }

    private void syncFieldBounds() {
        if (entryField == null) return;
        int x = panelX();
        int y = panelY();
        entryField.setX(x + 10);
        entryField.setY(y + HEADER_H + 10);
        entryField.setWidth(panelW() - 88);
        entryField.setHeight(FIELD_H);
    }

    private String entryText() {
        return entryField == null ? "" : entryField.getText();
    }

    private void setEntryText(String value) {
        ensureFields();
        entryField.setText(value == null ? "" : value);
        entryField.setSelectionEnd(entryField.getText().length());
    }

    private int listY() {
        int y = panelY() + HEADER_H + 10 + FIELD_H + 8 + 16;
        return pickerTarget != null ? y + PICKER_H + 6 : y;
    }

    private CompactListViewport.Layout listLayout(int listY, int listH) {
        return CompactListViewport.layout(
            panelX() + 10,
            listY,
            panelW() - 20,
            listH,
            rows().size(),
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

    private CompactScrollbar.Metrics listScrollbarMetrics() {
        int listY = listY();
        int listH = panelY() + panelH() - listY - 10;
        return listLayout(listY, listH).scrollbar();
    }

    private boolean insideListBody(int mx, int my) {
        int listY = listY();
        int listH = panelY() + panelH() - listY - 10;
        CompactListViewport.Layout listLayout = listLayout(listY, listH);
        return mx >= listLayout.x() + listLayout.contentInset()
            && mx < listLayout.x() + listLayout.contentInset() + listLayout.contentWidth()
            && my >= listLayout.y() + listLayout.contentInset()
            && my < listLayout.y() + listLayout.contentInset() + listLayout.viewHeight();
    }

    private void clampScrollToList() {
        scroll = clamp(scroll, 0, listScrollbarMetrics().maxScroll());
    }

    private int snapScrollToRows(int offset) {
        return Math.max(0, offset / ROW_H) * ROW_H;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

        UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill,
            autismclient.util.AutismTheme.recolor(0x99662C2C, autismclient.util.AutismTheme.Channel.OUTLINE));
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        String display = UiText.trimToWidth(font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private record Hit(HitType type, int x, int y, int w, int h, String value) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum HitType {
        ADD,
        SWATCH,
        PRESET,
        REMOVE,
        ROW,
        CLOSE
    }
}
