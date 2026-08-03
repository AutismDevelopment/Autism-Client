package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactListViewport;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.modules.AutismModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AutismCustomFilterPresetOverlay extends AutismOverlayBase {
    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final CompactTextInput nameField;
    private final List<ActionButton> buttons = new ArrayList<>();
    private final PresetListState presetListState = new PresetListState();
    private List<AutismPresetManager.PresetEntry> cachedBuiltInEntries = List.of();
    private List<AutismPresetManager.PresetEntry> cachedUserEntries = List.of();
    private List<PresetRow> cachedPresetRows = List.of();

    private int panelX = 570;
    private int panelY = 48;
    private int panelWidth;
    private int panelHeight;
    private boolean visible = false;
    private boolean collapsed = false;

    private boolean dragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean presetScrollbarDragging = false;
    private int presetScrollbarGrabOffset = 0;

    private String selectedPresetName;

    public AutismCustomFilterPresetOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.panelWidth = defaultPanelWidth();
        this.panelHeight = defaultPanelHeight();
        this.nameField = new CompactTextInput()
            .setPlaceholder("Preset name")
            .setFieldHeight(inputHeight())
            .setMinWidth(120)
            .setPreferredWidth(120)
            .setTextTone(UiTone.BODY)
            .setPlaceholderTone(UiTone.MUTED);
    }

    @Override
    public String getOverlayId() {
        return "autism-custom-filter-presets";
    }

    @Override
    public int getMinWidth() {
        return defaultPanelWidth();
    }

    @Override
    public int getMinHeight() {
        return minimumPanelHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            AutismOverlayManager.get().bringToFront(this);
        } else {
            clearFocus();
        }
        saveLayout();
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + panelWidth
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, bounds);
    }

    @Override
    public boolean hasTextFieldFocused() {
        return nameField.isFocused();
    }

    @Override
    public void clearTextFieldFocus() {
        clearFocus();
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        buttons.clear();
        AutismWindowLayout clamped = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, "Preset Manager", collapsed, dragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
            return;
        }

        try {
            int x = panelX + panelInset();
            int y = panelY + HEADER_HEIGHT + topInset();
            int width = panelWidth - (panelInset() * 2);
            AutismModule module = AutismModule.get();

            AutismText.draw(context, textRenderer,
                "C2S " + module.getC2SPackets().size() + " | S2C " + module.getS2CPackets().size(),
                AutismText.Tone.MUTED, x, y, false);
            y += infoLineHeight();

            CompactListRenderer.drawHeader(context, textRenderer, "Preset Name", x, y);
            y += sectionHeaderHeight();

            nameField.setBounds(x, y, width, inputHeight());
            nameField.render(renderContext(context, mouseX, mouseY, delta));
            y += inputHeight() + actionGap();

            int halfWidth = (width - actionGap()) / 2;
            y = drawActionRow(context, mouseX, mouseY, x, y, halfWidth,
                "Save", this::saveNamedPreset, canSaveNamed(),
                "Load", this::loadSelectedPreset, canLoadSelected());
            y = drawActionRow(context, mouseX, mouseY, x, y, halfWidth,
                "Overwrite", this::overwriteSelectedPreset, canOverwriteSelected(),
                "Delete", this::deleteSelectedPreset, canDeleteSelected());
            y = drawAction(context, mouseX, mouseY, x, y, width, "Reset To Default", this::resetDefaults, true);
            y += sectionGap() - actionGap();

            y = renderPresetSection(context, mouseX, mouseY, delta, x, y, width, Math.max(listMinimumHeight(), panelHeight - (y - panelY) - contentBottomPadding()));
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
        }
    }

    private int renderPresetSection(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, int x, int y, int width, int sectionHeight) {
        CompactListRenderer.drawHeader(context, textRenderer, "Saved Presets", x, y);
        y += sectionHeaderHeight();

        List<PresetRow> rows = getCachedPresetRows();
        presetListState.rows = rows;
        int listHeight = Math.max(listMinimumHeight(), sectionHeight - sectionHeaderHeight() - footerHeight() - footerGap());
        CompactListViewport.Layout baseLayout = presetListLayout(x, y, width, listHeight, rows.size(), 0);
        int visualScroll = presetListState.scroll.tick(delta, baseLayout.maxScroll());
        CompactListViewport.Layout listLayout = presetListLayout(x, y, width, listHeight, rows.size(), visualScroll);
        int contentWidth = Math.max(40, listLayout.contentWidth());
        presetListState.setBounds(x, y, width, listHeight, listLayout.scrollbar());

        boolean focused = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        listLayout.drawFrame(context, focused);

        if (rows.isEmpty()) {
            CompactListRenderer.drawEmptyState(context, textRenderer, "No presets", x, y, contentWidth);
        } else {
            listLayout.beginRows(context);
            try {
                listLayout.forEachVisibleRow(rows.size(), (index, rowY) -> {
                    PresetRow row = rows.get(index);
                    int rowTextY = UiSizing.alignTextY(rowY, presetRowHeight(), theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
                    if (row.header) {
                        CompactListRenderer.drawRow(context, textRenderer, row.label, x + 2, rowY, contentWidth, presetRowHeight(), false, false, CompactListRenderer.RowTone.WARNING);
                    } else if (row.note) {
                        CompactListRenderer.drawRow(context, textRenderer, row.label, x + 2, rowY, contentWidth, presetRowHeight(), false, false, CompactListRenderer.RowTone.NORMAL);
                    } else if (row.entry != null) {
                        boolean selected = row.entry.name().equals(selectedPresetName);
                        boolean hovered = mouseX >= x + 2 && mouseX < x + 2 + contentWidth && mouseY >= rowY && mouseY < rowY + presetRowHeight();
                        CompactListRenderer.drawRow(
                            context,
                            textRenderer,
                            "",
                            x + 2,
                            rowY,
                            contentWidth,
                            presetRowHeight(),
                            hovered,
                            selected,
                            row.entry.builtIn() ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                        );
                        int textColor = selected
                            ? AutismColors.rowSelectedText()
                            : (row.entry.builtIn() ? 0xFFB7D4FF : AutismColors.textLight());
                        String prefix = row.entry.builtIn() ? "[DEV] " : "[USR] ";
                        String name = AutismText.trimToWidth(textRenderer, prefix + row.entry.name(), contentWidth - 70, AutismText.Tone.BODY);
                        AutismText.draw(context, textRenderer, name, textColor, x + 5, rowTextY, false);

                        String counts = row.entry.c2sCount() + " / " + row.entry.s2cCount();
                        int countsWidth = AutismText.width(textRenderer, counts, AutismText.Tone.BODY);
                        AutismText.draw(context, textRenderer, counts, textColor, x + contentWidth - countsWidth - 1, rowTextY, false);
                    }
                    CompactListRenderer.drawDivider(context, x + 2, rowY + presetRowHeight(), contentWidth);
                });
            } finally {
                listLayout.endRows(context);
            }
        }

        listLayout.drawScrollbar(context, mouseX, mouseY, presetScrollbarDragging);

        String footer = listLayout.maxScroll() > 0
            ? "Scroll: " + (Math.min(rows.size(), Math.round(presetListState.scroll.targetOffset() / (float) presetRowStep()) + 1))
                + "/" + (Math.max(1, Math.round(listLayout.maxScroll() / (float) presetRowStep()) + 1))
            : (selectedPresetName == null ? "Select a preset row" : "Selected: " + selectedPresetName);
        AutismText.draw(context, textRenderer, footer, AutismText.Tone.MUTED, x + 4, y + listHeight + footerGap(), false);
        return y + listHeight + footerHeight() + footerGap();
    }

    private List<PresetRow> getCachedPresetRows() {
        AutismPresetManager manager = AutismPresetManager.get();
        List<AutismPresetManager.PresetEntry> builtInEntries = manager.getBuiltInPresetEntries();
        List<AutismPresetManager.PresetEntry> userEntries = manager.getUserPresetEntries();
        if (builtInEntries == cachedBuiltInEntries && userEntries == cachedUserEntries) {
            return cachedPresetRows;
        }

        List<PresetRow> rows = new ArrayList<>();
        rows.add(PresetRow.header("Developer Templates"));
        for (AutismPresetManager.PresetEntry entry : builtInEntries) {
            rows.add(PresetRow.entry(entry));
        }
        rows.add(PresetRow.header("User Presets"));
        if (userEntries.isEmpty()) {
            rows.add(PresetRow.note("No user presets yet"));
        } else {
            for (AutismPresetManager.PresetEntry entry : userEntries) {
                rows.add(PresetRow.entry(entry));
            }
        }
        cachedBuiltInEntries = builtInEntries;
        cachedUserEntries = userEntries;
        cachedPresetRows = List.copyOf(rows);
        return cachedPresetRows;
    }

    private void saveNamedPreset() {
        String name = sanitizeNameField();
        if (name == null) {
            AutismClientMessaging.sendPrefixed("Enter a preset name first.");
            return;
        }
        if (AutismPresetManager.get().savePreset(name)) {
            selectedPresetName = name;
            nameField.setText(name);
        }
    }

    private void loadSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            AutismClientMessaging.sendPrefixed("Select a preset first.");
            return;
        }
        if (AutismPresetManager.get().loadPreset(presetName)) {
            selectedPresetName = AutismPresetManager.get().getPresetEntry(presetName) != null
                ? AutismPresetManager.get().getPresetEntry(presetName).name()
                : presetName;
            nameField.setText(selectedPresetName);
        }
    }

    private void overwriteSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            AutismClientMessaging.sendPrefixed("Select a user preset first.");
            return;
        }
        AutismPresetManager.PresetEntry entry = AutismPresetManager.get().getPresetEntry(presetName);
        if (entry == null || entry.builtIn()) {
            AutismClientMessaging.sendPrefixed("Select a user preset to overwrite.");
            return;
        }
        if (AutismPresetManager.get().overwriteUserPreset(entry.name())) {
            selectedPresetName = entry.name();
            nameField.setText(entry.name());
        }
    }

    private void deleteSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            AutismClientMessaging.sendPrefixed("Select a user preset first.");
            return;
        }
        AutismPresetManager.PresetEntry entry = AutismPresetManager.get().getPresetEntry(presetName);
        if (entry == null || entry.builtIn()) {
            AutismClientMessaging.sendPrefixed("Developer presets cannot be deleted.");
            return;
        }
        if (AutismPresetManager.get().deleteUserPreset(entry.name())) {
            if (entry.name().equals(selectedPresetName)) {
                selectedPresetName = null;
            }
            nameField.setText("");
        }
    }

    private void resetDefaults() {
        AutismModule module = AutismModule.get();
        module.resetC2SPacketsToDefault();
        module.resetS2CPacketsToDefault();
        AutismClientMessaging.sendPrefixed("Reset current filter to default packet lists.");
    }

    private String sanitizeNameField() {
        String value = nameField.text();
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private String resolveTargetPresetName() {
        if (selectedPresetName != null) {
            AutismPresetManager.PresetEntry selectedEntry = AutismPresetManager.get().getPresetEntry(selectedPresetName);
            if (selectedEntry != null) return selectedEntry.name();
        }

        String typedName = sanitizeNameField();
        if (typedName == null) return null;
        AutismPresetManager.PresetEntry typedEntry = AutismPresetManager.get().getPresetEntry(typedName);
        return typedEntry != null ? typedEntry.name() : null;
    }

    private AutismPresetManager.PresetEntry getSelectedPresetEntry() {
        if (selectedPresetName == null) return null;
        return AutismPresetManager.get().getPresetEntry(selectedPresetName);
    }

    private boolean canSaveNamed() {
        String name = sanitizeNameField();
        return name != null && !AutismPresetManager.get().isReservedPresetName(name);
    }

    private boolean canLoadSelected() {
        return resolveTargetPresetName() != null;
    }

    private boolean canOverwriteSelected() {
        AutismPresetManager.PresetEntry entry = getSelectedPresetEntry();
        return entry != null && !entry.builtIn();
    }

    private boolean canDeleteSelected() {
        return canOverwriteSelected();
    }

    private int drawAction(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action, boolean enabled) {
        drawActionButton(context, mouseX, mouseY, x, y, width, label, enabled);
        buttons.add(new ActionButton(x, y, width, actionHeight(), action, enabled));
        return y + actionHeight() + actionGap();
    }

    private int drawActionRow(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int buttonWidth,
                              String leftLabel, Runnable leftAction, boolean leftEnabled,
                              String rightLabel, Runnable rightAction, boolean rightEnabled) {
        drawActionButton(context, mouseX, mouseY, x, y, buttonWidth, leftLabel, leftEnabled);
        buttons.add(new ActionButton(x, y, buttonWidth, actionHeight(), leftAction, leftEnabled));

        int rightX = x + buttonWidth + actionGap();
        int rightWidth = panelWidth - (panelInset() * 2) - buttonWidth - actionGap();
        drawActionButton(context, mouseX, mouseY, rightX, y, rightWidth, rightLabel, rightEnabled);
        buttons.add(new ActionButton(rightX, y, rightWidth, actionHeight(), rightAction, rightEnabled));
        return y + actionHeight() + actionGap();
    }

    private void drawActionButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, boolean enabled) {
        CompactOverlayControls.action(context, textRenderer, x, y, width, actionHeight(), label,
            enabled ? CompactOverlayButton.Variant.SECONDARY : CompactOverlayButton.Variant.GHOST, enabled, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (button == 0 && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT) {
            AutismWindowLayout bounds = getBounds();
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            clearFocus();
            return true;
        }

        if (collapsed) return false;

        if (button == 0) {
            CompactScrollbar.Metrics scrollbar = presetListState.scrollbarMetrics;
            if (scrollbar != null && scrollbar.hasScroll() && scrollbar.contains(mouseX, mouseY)) {
                presetScrollbarDragging = true;
                presetScrollbarGrabOffset = Math.max(0, (int) mouseY - scrollbar.thumbY());
                presetListState.scroll.setFromThumbStepped(scrollbar, mouseY, presetScrollbarGrabOffset, presetRowStep());
                return true;
            }
        }

        if (handleTextFieldClick(nameField, mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && presetListState.contains(mouseX, mouseY)) {
            PresetRow row = presetListState.getRowAt(mouseY);
            if (row != null && row.entry != null) {
                selectedPresetName = row.entry.name();
                nameField.setText(selectedPresetName);
                clearFocus();
                return true;
            }
        }

        clearFocus();
        if (button == 0) {
            for (ActionButton actionButton : buttons) {
                if (actionButton.contains(mouseX, mouseY)) {
                    if (!actionButton.enabled()) {
                        return true;
                    }
                    actionButton.action.run();
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    private boolean handleTextFieldClick(CompactTextInput field, double mouseX, double mouseY, int button) {
        boolean clicked = field.mouseClicked(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button);
        if (clicked) {
            clearFocus();
            field.setFocused(true);
        }
        return clicked;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (nameField.mouseReleased(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button)) return true;
        if (button == 0 && presetScrollbarDragging) {
            presetScrollbarDragging = false;
            return true;
        }
        if (button == 0) {
            if (dragging) saveLayout();
            dragging = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (nameField.mouseDragged(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button, (float) deltaX, (float) deltaY)) return true;
        if (presetScrollbarDragging) {
            presetListState.scroll.setFromThumbStepped(presetListState.scrollbarMetrics, mouseY, presetScrollbarGrabOffset, presetRowStep());
            return true;
        }
        if (dragging && button == 0) {
            AutismWindowLayout nextBounds = clampToScreen(this,
                new AutismWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    panelWidth, panelHeight, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (presetListState.contains(mouseX, mouseY)) {
            int maxScroll = presetListLayout(presetListState.x, presetListState.y, presetListState.width, presetListState.height, presetListState.rows.size(), 0).maxScroll();
            presetListState.scroll.nudge(amount, presetRowStep(), maxScroll);
            return true;
        }
        return false;
    }

    private CompactListViewport.Layout presetListLayout(int x, int y, int width, int listHeight, int rowCount, int scrollOffset) {
        return CompactListViewport.layout(
            x, y, width, listHeight, rowCount, presetRowHeight(), presetRowStep(),
            scrollOffset, scrollbarTrackWidth(), scrollbarGutter()
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (nameField.isFocused()) {
                clearFocus();
                return true;
            }
            setVisible(false);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameField.isFocused() && canSaveNamed()) {
                saveNamedPreset();
                return true;
            }
        }

        if (!nameField.isFocused()) return false;
        return nameField.keyPressed(inputContext(0, 0), keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || !nameField.isFocused()) return false;
        return nameField.charTyped(inputContext(0, 0), chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        int frameHeight = collapsed ? HEADER_HEIGHT : panelHeight;
        if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + frameHeight) return true;
        return mouseX >= nameField.x() && mouseX <= nameField.x() + nameField.width()
            && mouseY >= nameField.y() && mouseY <= nameField.y() + nameField.height();
    }

    private void clearFocus() {
        nameField.setFocused(false);
    }

    private DirectRenderContext renderContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        DirectViewport viewport = DirectViewport.current(1.0f);
        return new DirectRenderContext(context, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta);
    }

    private DirectRenderContext inputContext(double mouseX, double mouseY) {
        DirectViewport viewport = DirectViewport.current(1.0f);
        return new DirectRenderContext(null, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0f);
    }

        private int defaultPanelWidth() {
        return 236;
    }

    private int defaultPanelHeight() {
        return 318;
    }

    private int minimumPanelHeight() {
        return 250;
    }

    private int panelInset() {
        return 10;
    }

    private int topInset() {
        return 6;
    }

    private int contentBottomPadding() {
        return 12;
    }

    private int infoLineHeight() {
        return theme.lineHeight(UiTone.MUTED, 1);
    }

    private int sectionHeaderHeight() {
        return theme.lineHeight(UiTone.LABEL, 0);
    }

    private int actionHeight() {
        return 13;
    }

    private int actionGap() {
        return 2;
    }

    private int sectionGap() {
        return 6;
    }

    private int inputHeight() {
        return 16;
    }

    private int presetRowHeight() {
        return 14;
    }

    private int presetRowStep() {
        return presetRowHeight() + 1;
    }

    private int footerGap() {
        return 4;
    }

    private int footerHeight() {
        return theme.lineHeight(UiTone.MUTED, 1);
    }

    private int scrollbarGutter() {
        return 8;
    }

    private int scrollbarTrackWidth() {
        return 3;
    }

    private int listMinimumHeight() {
        return 52;
    }

    private record ActionButton(int x, int y, int width, int height, Runnable action, boolean enabled) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final class PresetListState {
        private int x;
        private int y;
        private int width;
        private int height;
        private final ScrollState scroll = new ScrollState();
        private CompactScrollbar.Metrics scrollbarMetrics = null;
        private List<PresetRow> rows = List.of();

        private void setBounds(int x, int y, int width, int height, CompactScrollbar.Metrics scrollbarMetrics) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scrollbarMetrics = scrollbarMetrics;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private PresetRow getRowAt(double mouseY) {
            int idx = (int) (((mouseY - y - 2) + scroll.visualOffsetInt()) / AutismCustomFilterPresetOverlay.this.presetRowStep());
            if (idx < 0 || idx >= rows.size()) return null;
            return rows.get(idx);
        }
    }

    private static final class PresetRow {
        private final boolean header;
        private final boolean note;
        private final String label;
        private final AutismPresetManager.PresetEntry entry;

        private PresetRow(boolean header, boolean note, String label, AutismPresetManager.PresetEntry entry) {
            this.header = header;
            this.note = note;
            this.label = label;
            this.entry = entry;
        }

        private static PresetRow header(String label) {
            return new PresetRow(true, false, label, null);
        }

        private static PresetRow note(String label) {
            return new PresetRow(false, true, label, null);
        }

        private static PresetRow entry(AutismPresetManager.PresetEntry entry) {
            return new PresetRow(false, false, null, entry);
        }
    }
}
