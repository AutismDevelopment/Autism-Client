package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.gui.vanillaui.components.ColorPicker;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Slider;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.Toggle;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismChatField;
import autismclient.util.AutismHudManager;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class AutismHudElementSettingsScreen extends Screen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int TEXT = 0xFFF3ECE7;
    private static final int MUTED = 0xFFB79E9E;

    private static int muted() { return autismclient.util.AutismTheme.recolor(MUTED, autismclient.util.AutismTheme.Channel.TEXT); }

    private static int themed(int argb) { return autismclient.util.AutismTheme.recolor(argb, autismclient.util.AutismTheme.Channel.ACCENT); }
    private static final int RED = 0xFFFF3B3B;
    private static final int GREEN = 0xFF5CFF9A;
    private static final int PANEL_W = 368;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 36;
    private static final int ROW_H = 26;
    private static final int FIELD_H = 18;

    private final Screen parent;
    private final String id;
    private int scroll;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private String focusedKey;
    private AutismChatField editField;
    private String colorPickerKey;
    private ColorPicker colorPicker;
    private final List<CompactDropdown> enumDropdowns = new ArrayList<>();
    private final Map<String, CompactDropdown> enumDropdownCache = new HashMap<>();
    private boolean modulePickerOpen;
    private int modulePickerScroll;

    public AutismHudElementSettingsScreen(Screen parent, String id) {
        super(Component.literal("HUD Element Settings"));
        this.parent = parent;
        this.id = id;
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
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            int[] panel = panelBounds();
            int x = panel[0];
            int y = panel[1];
            int w = panel[2];
            int h = panel[3];
            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0x66000000);
            drawTopBar(graphics, x, y, w, h, HEADER_H, AutismHudManager.label(id), mx, my);
            if (compactLayout(w, h)) return;

            int bodyTop = y + HEADER_H + 7;
            int bodyBottom = y + h - FOOTER_H - 5;
            int viewH = Math.max(30, bodyBottom - bodyTop);
            List<Row> rows = rows();
            int contentH = contentHeight(rows);
            scroll = clamp(scroll, 0, Math.max(0, contentH - viewH));

            boolean menuOpen = CompactDropdown.isMenuOpen(enumDropdowns);
            int hoverX = menuOpen ? Integer.MIN_VALUE : mx;
            int hoverY = menuOpen ? Integer.MIN_VALUE : my;
            enumDropdowns.clear();
            autismclient.gui.vanillaui.UiScissorStack.global().push(graphics,
                autismclient.gui.vanillaui.UiBounds.of(x + 2, bodyTop, Math.max(0, w - 9), Math.max(0, bodyBottom - bodyTop)));
            try {
                renderRows(graphics, rows, x + 8, bodyTop - scroll, w - 20, hoverX, hoverY, bodyTop, bodyBottom);
                CompactDropdown.renderButtons(graphics, this.font, enumDropdowns, mx, my);
            } finally {
                autismclient.gui.vanillaui.UiScissorStack.global().pop(graphics);
            }
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, viewH, x + w - 6, bodyTop, 4, viewH, scroll);
            CompactScrollbar.draw(graphics, metrics, metrics.contains(hoverX, hoverY), scrollbarDragging);

            int footerY = y + h - FOOTER_H + 7;
            button(graphics, "Back", x + 8, footerY, 78, 20, hoverX, hoverY, CompactOverlayButton.Variant.PRIMARY);
            button(graphics, "Reset", x + 94, footerY, 78, 20, hoverX, hoverY, CompactOverlayButton.Variant.PRIMARY);
            boolean enabled = AutismHudManager.state(id).enabled;
            int switchX = x + w - 32;
            int labelW = UiText.width(this.font, "Enabled", THEME.fontFor(UiTone.BODY), TEXT);
            draw(graphics, "Enabled", switchX - 6 - labelW, footerY + 6, enabled ? TEXT : muted(), labelW + 2);
            renderSwitch(graphics, enabled, switchX, footerY + 2, "hud-elem-settings:" + id + ":enabled");

            CompactDropdown.renderOpenMenu(graphics, this.font, enumDropdowns, mx, my);

            if (modulePickerOpen) renderModulePicker(graphics, sw, sh, mx, my);
            if (colorPicker != null) {
                graphics.nextStratum();
                colorPicker.render(UiContexts.overlay(graphics, font, mx, my));
                if (!colorPicker.isOpen()) clearColorPicker();
            }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderRows(GuiGraphicsExtractor graphics, List<Row> rows, int x, int y, int w, int mx, int my, int clipTop, int clipBottom) {
        int cy = y;
        for (Row row : rows) {
            if (row.section()) {
                if (visible(cy, 22, clipTop, clipBottom)) section(graphics, row.label, x, cy, w);
                cy += 22;
                continue;
            }
            if (visible(cy, ROW_H, clipTop, clipBottom)) row(graphics, row, x, cy, w, mx, my);
            cy += ROW_H;
        }
    }

    private void section(GuiGraphicsExtractor graphics, String label, int x, int y, int w) {
        CompactSurfaces.tintedRow(graphics, x, y + 3, w, 16, themed(0x44190C10));
        CompactSurfaces.divider(graphics, x, y + 3, w, themed(0x668F1F24));
        CompactSurfaces.divider(graphics, x, y + 18, w, themed(0x668F1F24));
        draw(graphics, label, x + 6, y + 7, muted(), w - 12);
    }

    private void row(GuiGraphicsExtractor graphics, Row row, int x, int y, int w, int mx, int my) {
        boolean over = hover(mx, my, x, y, w, ROW_H - 2);
        CompactSurfaces.tintedRow(graphics, x, y, w, ROW_H - 2, over ? themed(0x66351B1F) : 0x33131418);
        draw(graphics, row.label, x + 6, y + 7, TEXT, w / 2 - 8);
        int valueX = x + w / 2;
        int valueW = w / 2 - 8;
        if (row.type == RowType.BOOL) {
            boolean on = bool(row.key);
            renderSwitch(graphics, on, x + w - 34, y + 4, "hud-elem-settings:" + id + ":" + row.key);
        } else if (row.type == RowType.ENUM) {
            renderDropdown(row, valueX, y + 4, valueW, 18);
        } else if (row.type == RowType.COLOR) {
            int pickW = 44;
            int color = AutismHudManager.parseColor(AutismHudManager.setting(id, row.key), defaultColor(row.key));
            int swatchX = valueX;
            int swatchY = y + 5;
            UiRenderer.rect(graphics, UiBounds.of(swatchX, swatchY, 28, 12), color | 0xFF000000);
            frame(graphics, swatchX, swatchY, 28, 12, 0x00000000, THEME.borderSoft());
            draw(graphics, "#" + String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF),
                swatchX + 34, y + 7, TEXT, Math.max(1, valueW - pickW - 40));
            button(graphics, "Pick", valueX + valueW - pickW, y + 3, pickW, 18, mx, my, CompactOverlayButton.Variant.PRIMARY);
        } else if (row.type == RowType.MODULE_LIST) {
            button(graphics, "Edit (" + hiddenModuleIds().size() + ")", valueX + valueW - 86, y + 3, 86, 18, mx, my, CompactOverlayButton.Variant.PRIMARY);
        } else if (row.type == RowType.NUMBER) {
            double value = AutismHudManager.doubleSetting(id, row.key, row.min);
            renderSlider(graphics, row, valueX, y + 5, valueW, 12, value,
                (row.key != null && row.key.equals(focusedKey)) || row == draggingSlider, mx, my);
        } else if (row.type == RowType.ITEM) {
            int pickW = 44;
            String raw = AutismHudManager.setting(id, row.key);
            String itemName = raw == null || raw.isBlank() ? "None"
                : autismclient.util.AutismRegistryLabels.item(raw.contains(":") ? raw : "minecraft:" + raw);
            draw(graphics, itemName, valueX, y + 7, TEXT, Math.max(1, valueW - pickW - 8));
            button(graphics, "Pick", valueX + valueW - pickW, y + 3, pickW, 18, mx, my, CompactOverlayButton.Variant.PRIMARY);
        }
    }

    private void renderDropdown(Row row, int x, int y, int w, int h) {
        List<String> choices = row.choices;
        int selected = Math.max(0, choices.indexOf(AutismHudManager.setting(id, row.key)));
        CompactDropdown dropdown = enumDropdownCache.computeIfAbsent(row.key, ignored -> new CompactDropdown(x, y, w, h, choices, selected, index -> {}));
        dropdown.setBounds(x, y, w, h)
            .setOptions(choices)
            .setSelectedIndex(selected)
            .setOnSelect(index -> {
                AutismHudManager.setSetting(id, row.key, choices.get(index));
                clearHudFocus();
            });
        enumDropdowns.add(dropdown);
    }

    private void renderSlider(GuiGraphicsExtractor graphics, Row row, int x, int y, int w, int h, double value, boolean editing, int mx, int my) {
        int fieldW = 50;
        int sliderW = Math.max(20, w - fieldW - 7);
        double ratio = Slider.ratio(value, row.min, row.max);
        Slider.render(UiContexts.overlay(graphics, this.font, x, y), UiBounds.of(x, y, sliderW, h), ratio, editing);
        int fieldY = y - 3;
        if (editing) {
            positionEditField(x + sliderW + 7, fieldY, fieldW, FIELD_H);
            editField.render(graphics, mx, my, 0f);
        } else {
            frame(graphics, x + sliderW + 7, fieldY, fieldW, FIELD_H, 0xFF120D10, THEME.borderSoft());
            drawCentered(graphics, formatNumber(value, row.step), x + sliderW + 7, fieldY, fieldW, FIELD_H, TEXT);
        }
    }

    private void positionEditField(int x, int y, int w, int h) {
        if (editField == null) editField = new AutismChatField(net.minecraft.client.Minecraft.getInstance(), font, x, y, w, h, false);
        editField.setX(x);
        editField.setY(y);
        editField.setWidth(w);
        editField.setHeight(h);
    }

    private void startEditing(String key, String value) {
        focusedKey = key;
        if (editField == null) editField = new AutismChatField(net.minecraft.client.Minecraft.getInstance(), font, 0, 0, 20, FIELD_H, false);
        editField.setText(value == null ? "" : value);
        editField.setFocused(true);
        editField.setSelectionEnd(editField.getText().length());
    }

    private void renderSwitch(GuiGraphicsExtractor graphics, boolean checked, int x, int y, String animationKey) {
        int w = 24;
        int h = 16;

        Toggle.render(UiContexts.overlay(graphics, this.font, x, y), UiBounds.of(x, y + 2, w, h - 4), checked, false, animationKey);
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, int mx, int my, CompactOverlayButton.Variant variant) {
        CompactOverlayControls.action(graphics, font, x, y, w, h, label, variant, true, mx, my);
    }

    private void renderModulePicker(GuiGraphicsExtractor graphics, int sw, int sh, int mx, int my) {
        int w = DirectLayout.fitPanelDimension(sw, 12, 260);
        List<Module> modules = moduleRows();
        int rowH = 20;
        int contentH = 28 + modules.size() * rowH;
        int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
        int x = DirectLayout.centerPanel(sw, w, 12);
        int y = DirectLayout.centerPanel(sh, h, 12);
        int viewTop = y + 28;
        modulePickerScroll = clamp(modulePickerScroll, 0, Math.max(0, contentH - h));
        UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0x44000000);
        drawTopBar(graphics, x, y, w, h, HEADER_H, "Hidden Modules", mx, my);
        autismclient.gui.vanillaui.UiScissorStack.global().push(graphics,
            autismclient.gui.vanillaui.UiBounds.of(x + 2, viewTop, Math.max(0, w - 4), Math.max(0, y + h - 4 - viewTop)));
        try {
            List<String> hidden = hiddenModuleIds();
            int cy = viewTop - modulePickerScroll;
            for (Module module : modules) {
                if (visible(cy, rowH, viewTop, y + h - 4)) {
                    boolean selected = hidden.contains(module.id());
                    boolean over = hover(mx, my, x + 5, cy, w - 10, rowH - 2);
                    CompactSurfaces.tintedRow(graphics, x + 5, cy, w - 10, rowH - 2, over ? themed(0x66351B1F) : 0x33131418);
                    draw(graphics, module.name(), x + 10, cy + 5, selected ? TEXT : muted(), w - 52);
                    renderSwitch(graphics, selected, x + w - 34, cy + 2, "hud-hidden-modules:" + module.id());
                }
                cy += rowH;
            }
        } finally {
            autismclient.gui.vanillaui.UiScissorStack.global().pop(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        int[] panel = panelBounds();
        int x = panel[0], y = panel[1], w = panel[2], h = panel[3];
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return true;
        if (colorPicker != null) {
            colorPicker.mouseClicked(mx, my, event.button());
            if (!colorPicker.isOpen()) clearColorPicker();
            return true;
        }
        if (modulePickerOpen) {
            handleModulePickerClick(mx, my);
            return true;
        }
        if (isOverTopBarClose(mx, my, x, y, w, h, HEADER_H)) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        if (CompactDropdown.mouseClicked(enumDropdowns, mx, my, event.button())) {
            clearHudFocus();
            return true;
        }
        if (CompactDropdown.isMenuOpen(enumDropdowns)) return true;
        if (focusedKey != null && editField != null && editField.mouseClicked(mx, my, event.button())) return true;

        int bodyTop = y + HEADER_H + 7;
        int bodyBottom = y + h - FOOTER_H - 8;
        int viewH = Math.max(30, bodyBottom - bodyTop);
        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentHeight(rows()), viewH, x + w - 6, bodyTop, 4, viewH, scroll);
        if (metrics.hasScroll() && metrics.contains(mx, my)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            scroll = CompactScrollbar.scrollFromThumb(metrics, my, scrollbarGrabOffset);
            return true;
        }
        int footerY = y + h - FOOTER_H + 7;
        if (hover(mx, my, x + 8, footerY, 78, 20)) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        if (hover(mx, my, x + 94, footerY, 78, 20)) {
            AutismHudManager.resetElement(id);
            clearHudFocus();
            return true;
        }
        if (hover(mx, my, x + w - 88, footerY, 82, 20)) {
            AutismHudManager.toggle(id);
            return true;
        }

        if (my < bodyTop || my >= bodyBottom) {
            clearHudFocus();
            return true;
        }
        Row row = rowAt(mx, my, x + 8, bodyTop - scroll, w - 20, bodyTop, bodyBottom);
        if (row == null || row.section()) {
            clearHudFocus();
            return true;
        }
        if (row.type != RowType.ENUM) handleRowClick(row, event.button(), mx, my, x + 8 + (w - 20) / 2, (w - 20) / 2 - 8);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollbarDragging = false;
        draggingSlider = null;
        if (colorPicker != null) {
            int mx = AutismUiScale.toVirtualInt(event.x());
            int my = AutismUiScale.toVirtualInt(event.y());
            colorPicker.mouseReleased(mx, my, event.button());
            if (!colorPicker.isOpen()) clearColorPicker();
            return true;
        }
        if (CompactDropdown.mouseReleased(enumDropdowns)) return true;
        if (editField != null) {
            editField.mouseReleased(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
        }
        return true;
    }

    private Row draggingSlider;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (draggingSlider != null) {

            int[] panel = panelBounds();
            int valueX = panel[0] + 8 + (panel[2] - 20) / 2;
            int valueW = (panel[2] - 20) / 2 - 8;
            setNumeric(draggingSlider, Slider.valueFromMouse(mx, valueX, Math.max(20, valueW - 57),
                draggingSlider.min, draggingSlider.max, draggingSlider.step));
            return true;
        }
        if (colorPicker != null) {
            colorPicker.mouseDragged(mx, my, event.button(), dx, dy);
            if (!colorPicker.isOpen()) clearColorPicker();
            return true;
        }
        if (CompactDropdown.mouseDragged(enumDropdowns, mx, my, event.button())) return true;
        if (focusedKey != null && editField != null && editField.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (!scrollbarDragging) return true;
        int[] panel = panelBounds();
        int bodyTop = panel[1] + HEADER_H + 7;
        int viewH = Math.max(30, panel[1] + panel[3] - FOOTER_H - 8 - bodyTop);
        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentHeight(rows()), viewH, panel[0] + panel[2] - 6, bodyTop, 4, viewH, scroll);
        scroll = CompactScrollbar.scrollFromThumb(metrics, my, scrollbarGrabOffset);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double virtualX = AutismUiScale.toVirtual(mouseX);
        double virtualY = AutismUiScale.toVirtual(mouseY);
        if (colorPicker != null) {
            colorPicker.mouseScrolled((int) Math.round(virtualX), (int) Math.round(virtualY), scrollY);
            return true;
        }
        if (CompactDropdown.mouseScrolled(enumDropdowns, virtualX, virtualY, scrollY)) return true;
        if (modulePickerOpen) {
            modulePickerScroll = clamp(modulePickerScroll + (scrollY < 0 ? ROW_H : -ROW_H), 0, modulePickerContentOverflow());
            return true;
        }
        int[] panel = panelBounds();
        int bodyTop = panel[1] + HEADER_H + 7;
        int bodyBottom = panel[1] + panel[3] - FOOTER_H - 8;
        int contentH = contentHeight(rows());
        scroll = clamp(scroll + (scrollY < 0 ? ROW_H : -ROW_H), 0, Math.max(0, contentH - Math.max(30, bodyBottom - bodyTop)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (colorPicker != null) {
            colorPicker.keyPressed(input.key(), input.scancode(), input.modifiers());
            if (!colorPicker.isOpen()) clearColorPicker();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (modulePickerOpen) modulePickerOpen = false;
            else if (focusedKey != null) clearHudFocus();
            else minecraft.gui.setScreen(parent);
            return true;
        }
        if (focusedKey != null) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitFocus();
                return true;
            }
            if (editField != null && editField.keyPressed(input)) return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (colorPicker != null) {
            colorPicker.charTyped((char) input.codepoint());
            if (!colorPicker.isOpen()) clearColorPicker();
            return true;
        }
        if (focusedKey != null && editField != null) editField.charTyped(input);
        return true;
    }

    private void handleRowClick(Row row, int button, int mx, int my, int valueX, int valueW) {
        int dir = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
        if (row.type == RowType.BOOL) {
            AutismHudManager.setSetting(id, row.key, Boolean.toString(!bool(row.key)));
            clearHudFocus();
        } else if (row.type == RowType.COLOR) {
            openColorPicker(row.key, UiBounds.of(valueX, Math.max(0, my - ROW_H / 2), valueW, ROW_H));
            clearHudFocus();
        } else if (row.type == RowType.MODULE_LIST) {
            modulePickerOpen = true;
            modulePickerScroll = 0;
            clearHudFocus();
        } else if (row.type == RowType.NUMBER) {
            int fieldX = valueX + valueW - 50;
            if (mx >= fieldX && mx < fieldX + 50) {
                startEditing(row.key, AutismHudManager.setting(id, row.key));
                return;
            }
            double value = Slider.valueFromMouse(mx, valueX, Math.max(20, valueW - 57), row.min, row.max, row.step);
            setNumeric(row, value);

            draggingSlider = row;
            clearHudFocus();
        } else if (row.type == RowType.ITEM) {
            clearHudFocus();
            minecraft.gui.setScreen(new AutismItemPickerScreen(this, AutismHudManager.setting(id, row.key),
                value -> AutismHudManager.setSetting(id, row.key, value)));
        }
    }

    private void openColorPicker(String key, UiBounds anchor) {
        if (key == null || key.isBlank()) return;
        colorPickerKey = key;
        modulePickerOpen = false;
        CompactDropdown.closeOpenMenu(enumDropdowns);
        clearHudFocus();
        int fallback = defaultColor(key);
        int initial = AutismHudManager.parseColor(AutismHudManager.setting(id, key), fallback);
        colorPicker = new ColorPicker(anchor, initial, fallback,
            AutismUiScale.getVirtualScreenWidth(), AutismUiScale.getVirtualScreenHeight(), argb -> {
                AutismHudManager.setSetting(id, key, String.format(Locale.ROOT, "%08X", argb));
                clearColorPicker();
            });
    }

    private void clearColorPicker() {
        if (colorPicker != null) colorPicker.closeCancel();
        colorPicker = null;
        colorPickerKey = null;
    }

    private int defaultColor(String key) {
        return AutismHudManager.parseColor(AutismHudManager.defaultSetting(id, key), 0xFFFFFFFF);
    }

    private void setNumeric(Row row, double value) {
        double clamped = Math.max(row.min, Math.min(row.max, value));
        AutismHudManager.setSetting(id, row.key, formatNumber(clamped, row.step));
    }

    private void commitFocus() {
        Row row = findRow(focusedKey);
        if (row == null) {
            clearHudFocus();
            return;
        }
        String editingText = editField == null ? "" : editField.getText();
        if (row.type == RowType.NUMBER) {
            try {
                setNumeric(row, Double.parseDouble(editingText.trim()));
            } catch (NumberFormatException ignored) {  }
        }
        clearHudFocus();
    }

    private void clearHudFocus() {
        focusedKey = null;
        if (editField != null) {
            editField.setFocused(false);
            editField.setText("");
        }
    }

    private Row rowAt(int mx, int my, int x, int y, int w, int clipTop, int clipBottom) {
        int cy = y;
        for (Row row : rows()) {
            int h = row.section() ? 22 : ROW_H;
            if (!row.section() && my >= clipTop && my < clipBottom && hover(mx, my, x, cy, w, h - 2)) return row;
            cy += h;
        }
        return null;
    }

    private Row findRow(String key) {
        if (key == null) return null;
        for (Row row : rows()) if (key.equals(row.key)) return row;
        return null;
    }

    private static final Map<String, CachedRows> cachedRowLists = new HashMap<>();
    private record CachedRows(long revision, List<Row> rows) {}

    private List<Row> rows() {
        long rev = hudSettingsRevision();
        CachedRows cached = cachedRowLists.get(id);
        if (cached != null && cached.revision == rev) return cached.rows;

        List<Row> rows = new ArrayList<>();

        boolean isArrayList = AutismHudManager.ACTIVE_MODULES.equals(id);
        boolean customColors = isArrayList || AutismHudManager.boolSetting(id, "use-custom-colors");

        rows.add(Row.section("Visibility"));
        if (!visualOnlyElement()) rows.add(Row.enumRow("alignment", "Alignment", "Left", "Center", "Right"));
        if (!isArrayList) rows.add(Row.bool("use-custom-colors", "Custom Colors"));

        if (AutismHudManager.WATERMARK.equals(id)) {
            rows.add(Row.section("Logo"));
            rows.add(Row.number("logo-width", "Width", RowType.NUMBER, 48, 420, 1));
        } else if (customColors && !AutismHudManager.ARMOR.equals(id) && !AutismHudManager.INVENTORY.equals(id)) {
            rows.add(Row.section("Text"));
            if (!AutismHudManager.COMPASS.equals(id)) {
                rows.add(Row.color("label-color", "Label Color"));
                rows.add(Row.color("value-color", "Value Color"));
            }
            rows.add(Row.color("accent-color", "Accent Color"));
        }

        rows.add(Row.section("Background"));
        rows.add(Row.bool("background", "Background"));
        if (customColors && AutismHudManager.boolSetting(id, "background")) rows.add(Row.color("background-color", "Background Color"));

        rows.add(Row.section("Outline"));
        rows.add(Row.bool("outline", "Outline"));
        if (AutismHudManager.boolSetting(id, "outline")) {
            if (customColors) rows.add(Row.color("outline-color", "Outline Color"));
            rows.add(Row.number("outline-width", "Outline Width", RowType.NUMBER, 1, 6, 1));
        }

        if (AutismHudManager.ARMOR.equals(id) || AutismHudManager.INVENTORY.equals(id)) {
            rows.add(Row.section("Slots"));
            rows.add(Row.enumRow("slot-style", "Slot Style", "Textured", "Flat"));
        }

        if (AutismHudManager.ACTIVE_MODULES.equals(id)) {
            rows.add(Row.section("Active Modules"));
            rows.add(Row.bool("module-info", "Module Info"));
            rows.add(Row.bool("show-keybind", "Show Keybind"));
            rows.add(Row.enumRow("sort", "Sort", "Width", "Name", "Category"));
            rows.add(Row.enumRow("color-mode", "Color Mode", "Flat", "Random", "Rainbow", "Gradient"));
            String mode = AutismHudManager.setting(id, "color-mode");
            if ("Flat".equals(mode)) rows.add(Row.color("flat-color", "Flat Color"));
            if ("Gradient".equals(mode)) {
                rows.add(Row.color("gradient-start-color", "Gradient Start"));
                rows.add(Row.color("gradient-end-color", "Gradient End"));
            }
            if (AutismHudManager.boolSetting(id, "module-info") || AutismHudManager.boolSetting(id, "show-keybind")) rows.add(Row.color("module-info-color", "Info Color"));
            if ("Rainbow".equals(mode) || "Gradient".equals(mode)) {
                rows.add(Row.number("rainbow-speed", "Animation Speed", RowType.NUMBER, 0.1, 2.0, 0.05));
                rows.add(Row.number("rainbow-spread", "Row Spread", RowType.NUMBER, 0.001, 0.12, 0.001));
            }
            if ("Rainbow".equals(mode)) {
                rows.add(Row.number("rainbow-saturation", "Saturation", RowType.NUMBER, 0.0, 1.0, 0.05));
                rows.add(Row.number("rainbow-brightness", "Brightness", RowType.NUMBER, 0.0, 1.0, 0.05));
                rows.add(Row.enumRow("rainbow-direction", "Direction", "Forward", "Reverse"));
            }
            rows.add(Row.number("stair-snap", "Stair Snap", RowType.NUMBER, 0, 24, 1));
            rows.add(Row.moduleList("hidden-modules", "Hidden Modules"));
        }
        if (AutismHudManager.TPS.equals(id)) {
            rows.add(Row.section("TPS"));
            rows.add(Row.bool("tps-precise", "Precise"));
            rows.add(Row.bool("tps-color-threshold", "Color Thresholds"));
            rows.add(Row.bool("tps-show-jitter", "Show Min"));
        }
        if (AutismHudManager.PING.equals(id)) {
            rows.add(Row.section("Ping"));
            rows.add(Row.bool("ping-color-threshold", "Color Thresholds"));
            rows.add(Row.bool("ping-show-jitter", "Show Jitter"));
        }
        if (AutismHudManager.CPS.equals(id)) {
            rows.add(Row.section("CPS"));
            rows.add(Row.bool("show-total", "Show Total"));
        }
        if (AutismHudManager.DURABILITY.equals(id)) {
            rows.add(Row.section("Durability"));
            rows.add(Row.bool("show-item-name", "Show Item Name"));
            rows.add(Row.bool("low-durability-warn", "Low Durability Warning"));
        }
        if (AutismHudManager.LOOKING_AT.equals(id)) {
            rows.add(Row.section("Looking At"));
            rows.add(Row.bool("show-distance", "Show Distance"));
            rows.add(Row.bool("show-block-id", "Show Block ID"));
        }
        if (AutismHudManager.BREAKING_PROGRESS.equals(id)) {
            rows.add(Row.section("Breaking Progress"));
            rows.add(Row.bool("show-block-name", "Show Block Name"));
        }
        if (AutismHudManager.WEATHER.equals(id)) {
            rows.add(Row.section("Weather"));
            rows.add(Row.bool("show-temperature", "Show Temperature"));
        }
        if (AutismHudManager.WORLD_TIME.equals(id)) {
            rows.add(Row.section("World Time"));
            rows.add(Row.enumRow("world-time-format", "Format", "24h", "ticks"));
            rows.add(Row.bool("show-day", "Show Day"));
        }
        if (AutismHudManager.REAL_TIME.equals(id)) {
            rows.add(Row.section("Real Time"));
            rows.add(Row.enumRow("real-time-format", "Format", "12h", "24h"));
            rows.add(Row.bool("show-date", "Show Date"));
        }
        if (AutismHudManager.MEMORY.equals(id)) {
            rows.add(Row.section("Memory"));
            rows.add(Row.enumRow("memory-format", "Format", "used/max", "used%"));
            rows.add(Row.bool("show-bar", "Show Bar"));
            rows.add(Row.bool("show-percent", "Show Percentage"));
        }
        if (AutismHudManager.SERVER_IP.equals(id)) {
            rows.add(Row.section("Server IP"));
            rows.add(Row.bool("show-port", "Show Port"));
        }
        if (AutismHudManager.FPS_GRAPH.equals(id)) {
            rows.add(Row.section("FPS Graph"));
            rows.add(Row.number("graph-samples", "Samples", RowType.NUMBER, 50, 200, 10));
            rows.add(Row.bool("show-current-fps", "Show Current FPS"));
        }
        if (AutismHudManager.ITEM_COUNTER.equals(id)) {
            rows.add(Row.section("Item Counter"));
            rows.add(Row.bool("item-count-held", "Count Held Item"));
            rows.add(Row.bool("item-show-name", "Show Item Name"));
            if (!AutismHudManager.boolSetting(id, "item-count-held")) rows.add(Row.item("item-id", "Item"));
        }
        if (AutismHudManager.KEYSTROKES.equals(id)) {
            rows.add(Row.section("Keystrokes"));
            rows.add(Row.number("keystroke-size", "Key Size", RowType.NUMBER, 12, 40, 1));
            if (customColors) {
                rows.add(Row.color("keystroke-active-color", "Fill Color"));
                rows.add(Row.color("keystroke-idle-color", "Key Color"));
                rows.add(Row.color("keystroke-text-color", "Text Color"));
            }
            rows.add(Row.bool("keystroke-show-space", "Show Space"));
            rows.add(Row.bool("keystroke-show-mouse", "Show Mouse"));
        }
        if (AutismHudManager.SPOTIFY.equals(id)) {
            rows.add(Row.section("Spotify"));
            rows.add(Row.number("spotify-width", "Width", RowType.NUMBER, 140, 260, 1));
            rows.add(Row.number("spotify-scroll-speed", "Scroll Speed", RowType.NUMBER, 10, 60, 1));
            rows.add(Row.enumRow("spotify-source", "Source", "Spotify", "Any Media"));
            rows.add(Row.bool("spotify-menu-strip", "Menu Strip"));
            rows.add(Row.section("Colors"));
            rows.add(Row.enumRow("spotify-color-mode", "Color Mode", "Theme", "Custom", "Rainbow"));
            String spotifyMode = AutismHudManager.setting(id, "spotify-color-mode");
            if ("Custom".equals(spotifyMode) || "Flat".equals(spotifyMode)) {
                rows.add(Row.color("spotify-title-color", "Title Color"));
                rows.add(Row.color("spotify-artist-color", "Artist Color"));
                rows.add(Row.color("spotify-time-color", "Time Color"));
                rows.add(Row.color("spotify-progress-color", "Progress Color"));
            } else if ("Rainbow".equals(spotifyMode)) {
                rows.add(Row.bool("spotify-rainbow-artist", "Rainbow Artist"));
                rows.add(Row.bool("spotify-rainbow-title", "Rainbow Title"));
                rows.add(Row.bool("spotify-rainbow-time", "Rainbow Time"));
                rows.add(Row.bool("spotify-rainbow-progress", "Rainbow Timeline"));
                rows.add(Row.number("rainbow-speed", "Animation Speed", RowType.NUMBER, 0.1, 2.0, 0.05));
                rows.add(Row.number("rainbow-spread", "Two-Tone Spread", RowType.NUMBER, 0.001, 0.12, 0.001));
                rows.add(Row.number("rainbow-saturation", "Saturation", RowType.NUMBER, 0.0, 1.0, 0.05));
                rows.add(Row.number("rainbow-brightness", "Brightness", RowType.NUMBER, 0.0, 1.0, 0.05));
                rows.add(Row.enumRow("spotify-rainbow-direction", "Direction", "Forward", "Reverse"));
                rows.add(Row.color("spotify-title-color", "Title Color"));
                rows.add(Row.color("spotify-artist-color", "Artist Color"));
                rows.add(Row.color("spotify-time-color", "Time Color"));
                rows.add(Row.color("spotify-progress-color", "Progress Color"));
            }

            rows.add(Row.section("Parts"));
            rows.add(Row.bool("spotify-part-art", "Album Art"));
            rows.add(Row.bool("spotify-part-artist", "Artist"));
            rows.add(Row.bool("spotify-part-time", "Time"));
            rows.add(Row.enumRow("spotify-time-position", "Time Position", "Top", "Bottom"));
            rows.add(Row.bool("spotify-part-progress", "Progress Bar"));
        }
        List<Row> frozen = List.copyOf(rows);
        cachedRowLists.put(id, new CachedRows(rev, frozen));
        return frozen;
    }

    private static long hudSettingsRevision() {

        return ((long) AutismHudManager.settingsRevision() << 32)
            | (autismclient.modules.ModuleRegistry.revision() & 0xFFFFFFFFL);
    }

    private List<Module> moduleRows() {
        return new ArrayList<>(ModuleRegistry.all());
    }

    private List<String> hiddenModuleIds() {
        List<String> ids = new ArrayList<>();
        String raw = AutismHudManager.setting(AutismHudManager.ACTIVE_MODULES, "hidden-modules");
        if (raw == null || raw.isBlank()) return ids;
        for (String token : raw.split("\\|")) {
            String parsed = token.trim().toLowerCase(Locale.ROOT);
            if (!parsed.isBlank() && !ids.contains(parsed)) ids.add(parsed);
        }
        return ids;
    }

    private void toggleHiddenModule(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return;
        String normalized = moduleId.trim().toLowerCase(Locale.ROOT);
        List<String> ids = hiddenModuleIds();
        if (ids.contains(normalized)) ids.remove(normalized);
        else ids.add(normalized);
        AutismHudManager.setSetting(AutismHudManager.ACTIVE_MODULES, "hidden-modules", String.join("|", ids));
    }

    private int modulePickerContentOverflow() {
        int sh = AutismUiScale.getVirtualScreenHeight();
        int rowH = 20;
        int contentH = 28 + moduleRows().size() * rowH;
        int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
        return Math.max(0, contentH - h);
    }

    private void handleModulePickerClick(int mx, int my) {
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        int w = DirectLayout.fitPanelDimension(sw, 12, 260);
        List<Module> modules = moduleRows();
        int rowH = 20;
        int contentH = 28 + modules.size() * rowH;
        int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
        int x = DirectLayout.centerPanel(sw, w, 12);
        int y = DirectLayout.centerPanel(sh, h, 12);
        if (isOverTopBarClose(mx, my, x, y, w, h, HEADER_H) || !hover(mx, my, x, y, w, h)) {
            modulePickerOpen = false;
            return;
        }
        int viewTop = y + 28;
        if (!hover(mx, my, x + 2, viewTop, w - 4, h - 32)) return;
        int index = (my - viewTop + modulePickerScroll) / rowH;
        if (index >= 0 && index < modules.size()) toggleHiddenModule(modules.get(index).id());
    }

    private boolean visualOnlyElement() {
        return AutismHudManager.ARMOR.equals(id) || AutismHudManager.INVENTORY.equals(id) || AutismHudManager.COMPASS.equals(id)
            || AutismHudManager.WATERMARK.equals(id) || AutismHudManager.KEYSTROKES.equals(id);
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int headerHeight, String title, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, headerHeight, title,
            mx >= x && mx < x + width && my >= y && my < y + headerHeight);
    }

    private boolean isOverTopBarClose(int mx, int my, int x, int y, int width, int height, int headerHeight) {
        return CompactScreenPanel.isOverClose(UiBounds.of(x, y, width, height), headerHeight, mx, my);
    }

    private int preferredHeight() {
        return HEADER_H + FOOTER_H + Math.min(430, contentHeight(rows()) + 18);
    }

    private int contentHeight(List<Row> rows) {
        int h = 0;
        for (Row row : rows) h += row.section() ? 22 : ROW_H;
        return h;
    }

    private int[] panelBounds() {
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        int w = DirectLayout.fitPanelDimension(sw, 8, PANEL_W);
        int h = DirectLayout.fitPanelDimension(sh, 8, Math.max(180, preferredHeight()));
        return new int[] {DirectLayout.centerPanel(sw, w, 8), DirectLayout.centerPanel(sh, h, 8), w, h};
    }

    private boolean compactLayout(int width, int height) {
        return width < 220 || height < HEADER_H + FOOTER_H + 32;
    }

    private boolean bool(String key) {
        return AutismHudManager.boolSetting(id, key);
    }

    private String formatNumber(double value, double step) {
        if (step >= 1.0) return Integer.toString((int) Math.round(value));
        return String.format(Locale.ROOT, step < 0.01 ? "%.3f" : "%.2f", value);
    }

    private boolean visible(int y, int h, int top, int bottom) {
        return y + h >= top && y <= bottom;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
        UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
    }

    private void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxW) {
        String trimmed = UiText.trimToWidth(font, text, maxW, THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, trimmed, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private int textYInset(int h) {
        return Math.max(3, (h - THEME.fontHeight(UiTone.BODY) + 1) / 2 + 1);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
        String trimmed = UiText.trimToWidth(font, text, w - 8, THEME.fontFor(UiTone.BODY), color);
        int tw = UiText.width(font, trimmed, THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, trimmed, THEME.fontFor(UiTone.BODY), color, x + Math.max(4, (w - tw) / 2), y + textYInset(h), false);
    }

    private enum RowType {
        BOOL, ENUM, COLOR, NUMBER, ITEM, MODULE_LIST, SECTION
    }

    private record Row(String key, String label, RowType type, double min, double max, double step, List<String> choices) {
        static Row section(String label) {
            return new Row("", label, RowType.SECTION, 0, 0, 1, List.of());
        }

        static Row bool(String key, String label) {
            return new Row(key, label, RowType.BOOL, 0, 1, 1, List.of());
        }

        static Row enumRow(String key, String label, String... choices) {
            return new Row(key, label, RowType.ENUM, 0, 1, 1, List.of(choices));
        }

        static Row color(String key, String label) {
            return new Row(key, label, RowType.COLOR, 0, 1, 1, List.of());
        }

        static Row number(String key, String label, RowType type, double min, double max, double step) {
            return new Row(key, label, type, min, max, step, List.of());
        }

        static Row item(String key, String label) {
            return new Row(key, label, RowType.ITEM, 0, 1, 1, List.of());
        }

        static Row moduleList(String key, String label) {
            return new Row(key, label, RowType.MODULE_LIST, 0, 1, 1, List.of());
        }

        boolean section() {
            return type == RowType.SECTION;
        }
    }
}
