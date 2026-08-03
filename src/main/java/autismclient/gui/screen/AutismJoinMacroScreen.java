package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.util.AutismChatField;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static autismclient.gui.screen.AutismScreenPalette.*;

public final class AutismJoinMacroScreen extends AutismScreen {
    private static final int PANEL_W = 382;
    private static final int PANEL_MARGIN = 12;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_H = 154;
    private static final int LIST_TOP = 180;
    private static final int LIST_BOTTOM_MARGIN = 12;
    private static final int LIST_HEADER_H = 22;
    private static final int ROW_H = 22;
    private static final int GAP = 6;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<HitButton> buttons = new ArrayList<>();
    private AutismChatField searchField;
    private int scroll;
    private String lastQuery = "";
    private long lastMacroRevision = Long.MIN_VALUE;
    private boolean rowsDirty = true;

    private final List<CompactDropdown> dropdowns = new ArrayList<>();
    private CompactDropdown methodDropdown;
    private CompactDropdown triggerDropdown;
    private boolean macroScrollbarDragging;
    private int macroScrollbarGrabOffset;

    public AutismJoinMacroScreen(Screen parent) {
        super(Component.literal("Join Macro"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = TOP_PANEL_Y;
        if (searchField == null) {
            searchField = new AutismChatField(minecraft, font, x + 22, y + 48, panelW() - 44, 18, false);
            searchField.setPlaceholder(Component.literal("Search macros..."));
            searchField.setMaxLength(64);
            searchField.setChangedListener(value -> {
                scroll = 0;
                rowsDirty = true;
            });
        }
        syncSearchBounds();
        rebuildRows();
    }

    @Override
    public void tick() {
        super.tick();
        rebuildRowsIfNeeded();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            syncSearchBounds();
            buttons.clear();
            dropdowns.clear();

            graphics.fill(0, 0, screenWidth(), screenHeight(), BG);
            int x = panelX();
            int panelW = panelW();
            addButton(10, 10, 76, 18, "Back", CompactOverlayButton.Variant.SECONDARY, true, () -> minecraft.gui.setScreen(parent));

            UiRenderer.frame(graphics, UiBounds.of(x + 10, TOP_PANEL_Y, panelW - 20, TOP_PANEL_H), PANEL_BG, AutismTheme.recolor(BORDER, Channel.OUTLINE));
            UiRenderer.frame(graphics, UiBounds.of(listX(), LIST_TOP, listW(), listPanelH()), PANEL_BG_SOFT, AutismTheme.recolor(BORDER, Channel.OUTLINE));

            graphics.text(font, "Join Macro", x + 22, TOP_PANEL_Y + 10, TEXT, false);
            renderStatus(graphics, x + 22, TOP_PANEL_Y + 24, panelW - 44);

            boolean menuOpen = CompactDropdown.isMenuOpen(dropdowns);
            int hoverX = menuOpen ? Integer.MIN_VALUE : virtualMouseX;
            int hoverY = menuOpen ? Integer.MIN_VALUE : virtualMouseY;
            searchField.render(graphics, virtualMouseX, virtualMouseY, delta);
            renderActionRow(hoverX, hoverY);
            renderMethodRow(graphics, hoverX, hoverY);
            renderRows(graphics, hoverX, hoverY);
            renderButtons(graphics, hoverX, hoverY);
            CompactDropdown.renderButtons(graphics, font, dropdowns, virtualMouseX, virtualMouseY);
            CompactDropdown.renderOpenMenu(graphics, font, dropdowns, virtualMouseX, virtualMouseY);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderActionRow(int mouseX, int mouseY) {
        int x = panelX() + 22;
        int y = TOP_PANEL_Y + 72;
        addButton(x, y, 58, 18, "Edit", CompactOverlayButton.Variant.SECONDARY, selectedMacro() != null, () -> openEditor(selectedMacro()));
        addButton(x + 64, y, 62, 18, "Create", CompactOverlayButton.Variant.SUCCESS, true, () -> openEditor(null));
        addButton(x + 132, y, 92, 18, "Passwords", CompactOverlayButton.Variant.SECONDARY, true,
            this::openFormValues);
    }

    private void openFormValues() {
        MultiProfile profile = new MultiProfile();
        profile.name = "Rendered client";
        profile.sessions.clear();
        profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        AutismJoinMacroController.openFormValues().forEach((name, value) ->
            profile.setFormValue(MultiProfile.DEFAULT_ACCOUNT_ID, name, value));
        minecraft.gui.setScreen(new AutismFormValuesScreen(this, profile,
            java.util.Set.of(MultiProfile.DEFAULT_ACCOUNT_ID), updated -> {
                if (!AutismJoinMacroController.setFormValues(
                    updated.openFormValues(MultiProfile.DEFAULT_ACCOUNT_ID))) {
                    AutismNotifications.error("Secure encryption is unavailable; values were not saved.");
                }
            }, false));
    }

    private void renderMethodRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = panelX() + 22;
        int y = TOP_PANEL_Y + 98;
        graphics.text(font, "Execution Method", x, y + 5, 0xFFEFE8E4, false);

        int dropdownX = x + 104;
        int dropdownW = 104;

        AutismJoinMacroController.Timing[] timings = AutismJoinMacroController.Timing.values();
        List<String> methodOptions = new ArrayList<>();
        for (AutismJoinMacroController.Timing t : timings) methodOptions.add(t.label());
        int methodSel = AutismJoinMacroController.timing().ordinal();
        methodDropdown = updateDropdown(methodDropdown, dropdownX, y, dropdownW, 18, methodOptions, methodSel,
            idx -> AutismJoinMacroController.setTiming(timings[Math.max(0, Math.min(idx, timings.length - 1))]));
        dropdowns.add(methodDropdown);

        boolean keep = AutismJoinMacroController.keepEnabled();
        addButton(dropdownX + dropdownW + GAP, y, 118, 18, keep ? "Stays Enabled" : "Clears After",
            keep ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY, true,
            () -> AutismJoinMacroController.setKeepEnabled(!AutismJoinMacroController.keepEnabled()));

        int triggerRowY = y + 24;
        graphics.text(font, keep ? "Repeat On" : "Run On", x, triggerRowY + 5, 0xFFEFE8E4, false);

        AutismJoinMacroController.TriggerJoin[] triggerOpts = triggerOptions();
        List<String> triggerLabels = new ArrayList<>();
        int triggerSel = 0;
        for (int i = 0; i < triggerOpts.length; i++) {
            triggerLabels.add(triggerLabel(triggerOpts[i]));
            if (triggerOpts[i] == AutismJoinMacroController.triggerJoin()) triggerSel = i;
        }
        triggerDropdown = updateDropdown(triggerDropdown, dropdownX, triggerRowY, 118, 18, triggerLabels, triggerSel,
            idx -> AutismJoinMacroController.setTriggerJoin(triggerOpts[Math.max(0, Math.min(idx, triggerOpts.length - 1))]));
        dropdowns.add(triggerDropdown);
    }

    private CompactDropdown updateDropdown(CompactDropdown existing, int x, int y, int w, int h,
                                           List<String> options, int selected, java.util.function.IntConsumer onSelect) {
        if (existing == null) return new CompactDropdown(x, y, w, h, options, selected, onSelect);
        existing.setBounds(x, y, w, h).setOptions(options).setSelectedIndex(selected).setOnSelect(onSelect);
        return existing;
    }

    private void renderStatus(GuiGraphicsExtractor graphics, int x, int y, int maxWidth) {
        String selected = AutismJoinMacroController.selectedMacroName();
        if (selected.isBlank()) {
            graphics.text(font, "Click a macro to select it.", x, y, MUTED, false);
            return;
        }

        String selectedLine = "Selected: " + selected;
        String modeLine = AutismJoinMacroController.modeSummary();
        String combined = selectedLine + "  " + modeLine;
        if (font.width(combined) <= maxWidth) {
            graphics.text(font, combined, x, y, SUCCESS, false);
            return;
        }

        graphics.text(font, fit(selectedLine, maxWidth), x, y, SUCCESS, false);
        graphics.text(font, fit(modeLine, maxWidth), x, y + 10, SUCCESS, false);
    }

    private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        CompactScrollbar.Metrics scrollbar = macroScrollbarMetrics();
        boolean hasScrollbar = scrollbar.hasScroll();
        int rowRightInset = hasScrollbar ? 18 : 8;

        int titleY = listY + 10;
        int visibleRows = visibleRows();
        String title = rows.size() <= visibleRows
            ? "Macros"
            : "Macros  showing " + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + " / " + rows.size();
        graphics.text(font, fit(title, listW - 24), listX + 12, titleY, TEXT, false);

        if (rows.isEmpty()) {
            graphics.text(font, "No macros found.", listX + 12, rowsTop() + 8, MUTED, false);
            return;
        }

        String selected = AutismJoinMacroController.selectedMacroName();
        int max = Math.min(rows.size(), scroll + visibleRows);
        for (int i = scroll; i < max; i++) {
            Row row = rows.get(i);
            int rowY = rowsTop() + (i - scroll) * ROW_H;
            boolean hovered = mouseX >= listX + 1 && mouseX < listX + listW - 1 && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean active = row.name.equalsIgnoreCase(selected);
            UiBounds rowBounds = UiBounds.of(listX + 8, rowY, listW - 8 - rowRightInset, ROW_H - 3);
            renderMacroSelectionRow(graphics, rowBounds, row.name, active, hovered);

            String stepsText = row.steps + (row.steps == 1 ? " step" : " steps");
            int stepsW = font.width(stepsText);
            int rightX = listX + listW - rowRightInset - 4 - stepsW;
            int nameW = Math.max(1, rightX - (listX + 20) - 6);
            graphics.text(font, fit(row.name, nameW), listX + 20, rowY + 5, 0xFFEFE8E4, false);
            graphics.text(font, stepsText, rightX, rowY + 5, active ? SUCCESS : MUTED, false);
        }

        CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mouseX, mouseY), macroScrollbarDragging);
    }

    private void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (HitButton button : buttons) {
            CompactOverlayControls.action(graphics, font, button.x, button.y, button.w, button.h, button.label, button.variant, button.enabled, mouseX, mouseY);
        }
    }

    private void addButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action) {
        buttons.add(new HitButton(x, y, w, h, label, variant, enabled, action));
    }

    private void renderMacroSelectionRow(GuiGraphicsExtractor graphics, UiBounds bounds, String key, boolean selected, boolean hovered) {
        UiRenderer.rect(graphics, bounds,
            selected ? AutismTheme.recolor(0x3A35D873, Channel.SUCCESS) : (hovered ? AutismTheme.recolor(0x242B1A1D, Channel.ACCENT) : 0x18111113));
        if (selected) {
            UiRenderer.rect(graphics, bounds.inset(1, 1, 1, 1), AutismTheme.recolor(hovered ? 0x5735D873 : 0x3A35D873, Channel.SUCCESS));
            UiRenderer.rect(graphics, UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()), SUCCESS);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        event = virtualEvent(event);
        if (CompactDropdown.mouseClicked(dropdowns, event.x(), event.y(), event.button())) return true;
        if (searchField != null && searchField.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        if (event.button() == 0) {
            CompactScrollbar.Metrics scrollbar = macroScrollbarMetrics();
            if (scrollbar.hasScroll() && scrollbar.contains(event.x(), event.y())) {
                macroScrollbarDragging = true;
                macroScrollbarGrabOffset = scrollbar.overThumb(event.x(), event.y())
                    ? Math.max(0, (int) Math.round(event.y()) - scrollbar.thumbY())
                    : scrollbar.thumbHeight() / 2;
                setScrollFromPixels(CompactScrollbar.scrollFromThumb(scrollbar, event.y(), macroScrollbarGrabOffset));
                if (searchField != null) searchField.setFocused(false);
                return true;
            }
            for (HitButton button : buttons) {
                if (button.contains(event.x(), event.y())) {
                    if (button.enabled && button.action != null) button.action.run();
                    return true;
                }
            }
            int rowIndex = rowAt(event.x(), event.y());
            if (rowIndex >= 0) {
                Row row = rows.get(rowIndex);
                String selected = AutismJoinMacroController.selectedMacroName();
                AutismJoinMacroController.setSelectedMacro(row.name.equalsIgnoreCase(selected) ? "" : row.name);
                return true;
            }
        }
        if (searchField != null) searchField.setFocused(false);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        event = virtualEvent(event);
        if (CompactDropdown.mouseReleased(dropdowns)) return true;
        if (macroScrollbarDragging) {
            macroScrollbarDragging = false;
            return true;
        }
        return searchField != null && searchField.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        event = virtualEvent(event);
        dx = AutismUiScale.toVirtual(dx);
        dy = AutismUiScale.toVirtual(dy);
        if (CompactDropdown.mouseDragged(dropdowns, event.x(), event.y(), event.button())) return true;
        if (macroScrollbarDragging) {
            setScrollFromPixels(CompactScrollbar.scrollFromThumb(macroScrollbarMetrics(), event.y(), macroScrollbarGrabOffset));
            return true;
        }
        return searchField != null && searchField.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        if (CompactDropdown.mouseScrolled(dropdowns, mouseX, mouseY, scrollY)) return true;
        if (searchField != null && searchField.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (mouseX >= listX() && mouseX < listX() + listW() && mouseY >= listY() && mouseY < listY() + listPanelH()) {
            int maxScroll = Math.max(0, rows.size() - visibleRows());
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (searchField != null && searchField.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (CompactDropdown.closeOpenMenu(dropdowns)) return true;
            minecraft.gui.setScreen(parent);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return searchField != null && searchField.charTyped(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void rebuildRowsIfNeeded() {
        String query = searchText();
        long revision = AutismMacroManager.get().getRevision();
        if (rowsDirty || !query.equals(lastQuery) || revision != lastMacroRevision) {
            rebuildRows();
        }
    }

    private void rebuildRows() {
        String query = searchText();
        lastQuery = query;
        lastMacroRevision = AutismMacroManager.get().getRevision();
        rowsDirty = false;
        rows.clear();
        for (AutismMacro macro : AutismMacroManager.get().getAll()) {
            if (macro == null || macro.name == null || macro.name.isBlank()) continue;
            if (!query.isEmpty() && !macro.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            rows.add(new Row(macro.name, macro.actions == null ? 0 : macro.actions.size()));
        }
        rows.sort(Comparator.comparing(row -> row.name.toLowerCase(Locale.ROOT)));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    private String searchText() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private AutismMacro selectedMacro() {
        String selected = AutismJoinMacroController.selectedMacroName();
        return selected.isBlank() ? null : AutismMacroManager.get().get(selected);
    }

    private void openEditor(AutismMacro macro) {
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        AutismOverlayManager.get().register(editor, autismclient.util.IAutismOverlay.OverlayScope.HOST_SCREEN);
        editor.openForJoinMacroMenu(macro, saved -> {
            if (saved != null && saved.name != null && !saved.name.isBlank()) {
                AutismJoinMacroController.setSelectedMacro(saved.name);
                AutismNotifications.show("Join macro selected: " + saved.name, 0xFF66E08A);
            }
        });
        if (minecraft != null) minecraft.gui.setScreen(new AutismOverlayHostScreen(editor, new AutismJoinMacroScreen(parent)));
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = listX();
        int y = rowsTop();
        int h = visibleRows() * ROW_H;
        if (mouseX < x + 8 || mouseX >= x + listW() - 8 || mouseY < y || mouseY >= y + h) return -1;
        int visibleIndex = ((int) mouseY - y) / ROW_H;
        int index = scroll + visibleIndex;
        return index >= 0 && index < rows.size() ? index : -1;
    }

    private void syncSearchBounds() {
        if (searchField == null) return;
        int x = panelX();
        searchField.setX(x + 22);
        searchField.setY(TOP_PANEL_Y + 48);
        searchField.setWidth(panelW() - 44);
        searchField.setHeight(18);
    }

    private int panelX() {
        return Math.max(PANEL_MARGIN, (screenWidth() - panelW()) / 2);
    }

    private int panelW() {
        return Math.max(1, Math.min(PANEL_W, screenWidth() - PANEL_MARGIN * 2));
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listY() {
        return LIST_TOP;
    }

    private int listW() {
        return panelW() - 20;
    }

    private int listPanelH() {

        return Math.max(ROW_H, screenHeight() - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    private int rowsTop() {
        return LIST_TOP + LIST_HEADER_H;
    }

    private int visibleRows() {
        return Math.max(1, (listPanelH() - LIST_HEADER_H - 8) / ROW_H);
    }

    private CompactScrollbar.Metrics macroScrollbarMetrics() {
        int trackX = listX() + listW() - 10;
        int trackY = rowsTop();
        int trackH = Math.max(1, visibleRows() * ROW_H - 3);
        int contentPixels = Math.max(0, rows.size() * ROW_H);
        int viewPixels = Math.max(1, visibleRows() * ROW_H);
        return CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, 4, trackH, scroll * ROW_H);
    }

    private void setScrollFromPixels(int scrollPixels) {
        int maxScrollRows = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(maxScrollRows, Math.round(scrollPixels / (float) ROW_H)));
    }

    private AutismJoinMacroController.TriggerJoin[] triggerOptions() {
        if (AutismJoinMacroController.keepEnabled()) {
            return new AutismJoinMacroController.TriggerJoin[] {
                AutismJoinMacroController.TriggerJoin.ANY,
                AutismJoinMacroController.TriggerJoin.SECOND,
                AutismJoinMacroController.TriggerJoin.THIRD,
                AutismJoinMacroController.TriggerJoin.FOURTH,
                AutismJoinMacroController.TriggerJoin.FIFTH,
                AutismJoinMacroController.TriggerJoin.SIXTH_PLUS
            };
        }

        return new AutismJoinMacroController.TriggerJoin[] {
            AutismJoinMacroController.TriggerJoin.FIRST,
            AutismJoinMacroController.TriggerJoin.SECOND,
            AutismJoinMacroController.TriggerJoin.THIRD,
            AutismJoinMacroController.TriggerJoin.FOURTH,
            AutismJoinMacroController.TriggerJoin.FIFTH,
            AutismJoinMacroController.TriggerJoin.SIXTH_PLUS
        };
    }

    private String triggerLabel(AutismJoinMacroController.TriggerJoin triggerJoin) {
        return triggerJoin.displayLabel(AutismJoinMacroController.keepEnabled());
    }

    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - 4));
    }

    private record Row(String name, int steps) {
    }

    private record HitButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant,
                             boolean enabled, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

}
