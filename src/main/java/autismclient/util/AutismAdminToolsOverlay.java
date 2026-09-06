package autismclient.util;

import autismclient.gui.screen.AutismForceOpPreviewScreen;
import autismclient.gui.screen.AutismAdminItemOptionScreen;
import autismclient.gui.screen.AutismAdminItemStructuredScreen;
import autismclient.gui.screen.AutismItemPickerScreen;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactFieldFactory;
import autismclient.gui.vanillaui.components.CompactForm;
import autismclient.gui.vanillaui.components.CompactToolbar;
import autismclient.gui.vanillaui.components.Dropdown;
import autismclient.gui.vanillaui.components.Scrollbar;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.gui.vanillaui.components.Slider;
import autismclient.gui.vanillaui.components.TabStrip;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.modules.BuiltinModules;
import autismclient.modules.Module;
import autismclient.api.module.Kind;
import autismclient.api.module.Setting;
import autismclient.modules.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutismAdminToolsOverlay extends AutismOverlayBase {
    private static volatile AutismAdminToolsOverlay sharedOverlay;
    private static final int DEFAULT_W = 374;
    private static final int DEFAULT_H = 264;
    private static final int TAB_H = 16;
    private static final int FIELD_H = 16;
    private static final int SCROLLBAR_W = 6;
    private static final int CARD_PAD_X = 10;
    private static final int CARD_PAD_BOTTOM = 10;
    private static final int FLOW_GAP = 5;
    private static final int LABEL_H = 11;
    private static final int FIELD_ROW_H = LABEL_H + FIELD_H + 2;
    private static final int MUTED_ROW_H = 13;
    private static final String[] TABS = {"Fireballs", "Item Editor", "ForceOP"};
    private static final String[] ITEM_TABS = {"Item", "Visual", "Stats", "Raw"};
    private static final int RAW_PREVIEW_H = 104;
    private static final String[] FIREBALL_PRESETS = {"Balanced", "Strong", "Max", "Beam"};

    private final List<Hit> hits = new ArrayList<>();
    private int activeTab;
    private int itemTab;
    private int fireballPreset;
    private int scroll;
    private int contentHeight;
    private Dropdown dropdown;
    private Setting<?, ?> dropdownOption;
    private Editing editing;
    private Setting<?, ?> bindingOption;
    private Setting<?, ?> sliderOption;
    private UiBounds lastBodyBounds;
    private UiBounds lastViewport;
    private UiBounds editingTextFieldBounds;
    private boolean draggingTextSelection;
    private boolean scrollbarDragging;
    private boolean headerDragging;
    private int scrollbarGrabOffset;
    private boolean itemStackCaptureActive;
    private boolean restoreVisibleAfterItemStackCapture;
    private boolean autoOpenedInventoryForItemStackCapture;
    private Screen screenBeforeItemStackCapture;

    private AutismAdminToolsOverlay() {
        super("admin-tools-panel", DEFAULT_W, DEFAULT_H);
        panelX = 88;
        panelY = 32;
    }

    public static synchronized AutismAdminToolsOverlay getSharedOverlay() {
        if (sharedOverlay == null) sharedOverlay = new AutismAdminToolsOverlay();
        return sharedOverlay;
    }

    public static AutismAdminToolsOverlay getSharedOverlayIfExists() {
        return sharedOverlay;
    }

    public void showItemEditor() {
        this.activeTab = 1;
        this.itemTab = 0;
        this.scroll = 0;
        this.setVisible(true);
    }

    public void showRawItemEditor() {
        this.activeTab = 1;
        this.itemTab = 3;
        this.scroll = 0;
        this.setVisible(true);
    }

    @Override
    public int getMinWidth() {
        return 336;
    }

    @Override
    public int getMinHeight() {
        return 144;
    }

    @Override
    public boolean isOverResizeHandle(double mouseX, double mouseY) {
        if (!visible || collapsed) return false;
        return mouseX >= panelX + panelWidth - RESIZE_HANDLE && mouseX <= panelX + panelWidth
            && mouseY >= panelY + panelHeight - RESIZE_HANDLE && mouseY <= panelY + panelHeight;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        recordMouse(mouseX, mouseY);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        BuiltinModules.AdminToolsModule module = adminTools();
        if (module == null) return;

        applyAutoHeight();
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x;
        panelY = bounds.y;
        panelWidth = bounds.width;
        panelHeight = bounds.height;

        boolean topPopup = dropdown != null;
        int baseMouseX = topPopup ? -10000 : mouseX;
        int baseMouseY = topPopup ? -10000 : mouseY;
        UiContext context = UiContexts.overlay(graphics, mc.font, baseMouseX, baseMouseY);
        renderWindowFrame(graphics, baseMouseX, baseMouseY, bounds, "Admin Tools", collapsed, false);
        hits.clear();
        if (collapsed) return;

        boolean clipped = beginWindowBodyClip(graphics, bounds, collapsed);
        try {
            renderBody(context, module, bounds, baseMouseX, baseMouseY);
        } finally {
            endWindowBodyClip(graphics, clipped);
            renderWindowInactiveOverlay(graphics, bounds, collapsed, false);
        }
        if (dropdown != null) {
            graphics.nextStratum();
            dropdown.render(UiContexts.overlay(graphics, mc.font, mouseX, mouseY));
        }
    }

    private void renderBody(UiContext context, BuiltinModules.AdminToolsModule module, AutismWindowLayout bounds, int mouseX, int mouseY) {
        int x = bounds.x + 5;
        int y = bounds.y + HEADER_HEIGHT + 5;
        int w = Math.max(1, bounds.width - 10);
        UiBounds strip = UiBounds.of(x, y, w, TAB_H);
        for (int i = 0; i < TABS.length; i++) {
            UiBounds tab = TabStrip.tabBounds(strip, TABS.length, i, context.theme().spacing().controlGap);
            boolean active = i == activeTab;
            TabStrip.renderTab(context, tab, TABS[i], active);
            hits.add(new Hit(HitType.TAB, tab, null, Integer.toString(i)));
        }

        UiBounds body = UiBounds.of(x, y + TAB_H + 5, w, Math.max(1, bounds.y + bounds.height - y - TAB_H - 10));
        lastBodyBounds = body;
        contentHeight = tabContentHeight(body.width());
        scroll = clamp(scroll, 0, maxScroll(body.height()));
        UiBounds viewport = contentHeight > body.height()
            ? UiBounds.of(body.x(), body.y(), body.width() - SCROLLBAR_W - 3, body.height())
            : body;
        lastViewport = viewport;
        UiScissorStack.global().push(context.graphics(), viewport);
        try {
            int contentY = viewport.y() - scroll;
            switch (activeTab) {
                case 1 -> renderItemEditor(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
                case 2 -> renderForceOp(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
                default -> renderFireballs(context, module, viewport.x(), contentY, viewport.width(), mouseX, mouseY);
            }
        } finally {
            UiScissorStack.global().pop(context.graphics());
        }
        if (contentHeight > body.height()) {
            UiBounds track = UiBounds.of(body.right() - SCROLLBAR_W, body.y(), SCROLLBAR_W, body.height());
            Scrollbar.Metrics metrics = Scrollbar.metrics(track, contentHeight, body.height(), scroll);
            Scrollbar.render(context, metrics, scrollbarDragging);
            hits.add(new Hit(HitType.SCROLLBAR, track, null, ""));
        }
    }

    private int tabContentHeight(int availableWidth) {
        return switch (activeTab) {
            case 1 -> itemEditorHeight(Math.max(1, availableWidth - SCROLLBAR_W - 3));
            case 2 -> forceOpCardHeight(availableWidth);
            default -> fireballsCardHeight();
        };
    }

    private void applyAutoHeight() {
        if (collapsed) return;
        int bodyWidth = Math.max(1, panelWidth - 10);
        int desired = HEADER_HEIGHT + 5 + TAB_H + 5 + tabContentHeight(bodyWidth) + 10;
        int screenHeight = AutismUiScale.getVirtualScreenHeight();
        int maxHeight = screenHeight > 0 ? Math.max(getMinHeight(), screenHeight - 8) : Math.max(DEFAULT_H, desired);
        panelHeight = clamp(desired, getMinHeight(), maxHeight);
    }

    private int fireballsCardHeight() {
        return 75 + FIELD_H + 8;
    }

    private int forceOpCardHeight(int width) {
        int innerW = Math.max(1, width - 20);
        int cursor = 20;
        cursor += 22 * 3;
        cursor += 26;
        cursor += actionRowHeight(innerW, 3) + 6;
        cursor += actionRowHeight(innerW, 1) + 5;
        cursor += MUTED_ROW_H;
        return cursor + 7;
    }

    private int itemEditorHeight(int width) {
        int innerW = Math.max(1, width - CARD_PAD_X * 2);
        int tabHeight = TAB_H + FLOW_GAP;
        int content = switch (itemTab) {
            case 1 -> itemVisualHeight(innerW);
            case 2 -> itemStatsHeight(innerW);
            case 3 -> itemRawHeight(innerW);
            default -> itemBasicHeight(innerW);
        };
        return 18 + tabHeight + content + CARD_PAD_BOTTOM;
    }

    private int itemBasicHeight(int width) {
        FlowSizer sizer = new FlowSizer();
        sizer.add(MUTED_ROW_H);
        if (width >= 270) sizer.add(FIELD_ROW_H);
        else {
            sizer.add(FIELD_ROW_H);
            sizer.add(FIELD_ROW_H);
        }
        sizer.add(FIELD_ROW_H);
        sizer.add(FIELD_ROW_H);
        sizer.add(FIELD_ROW_H);
        sizer.add(actionRowHeight(width, 3));
        sizer.add(FIELD_H);
        return sizer.height();
    }

    private int itemVisualHeight(int width) {
        FlowSizer sizer = new FlowSizer();
        sizer.add(FIELD_ROW_H);
        sizer.add(FIELD_ROW_H);
        sizer.add(width >= 280 ? FIELD_ROW_H : FIELD_ROW_H * 2 + FLOW_GAP);
        sizer.add(actionRowHeight(width, 2));
        sizer.add(FIELD_H);
        return sizer.height();
    }

    private int itemStatsHeight(int width) {
        FlowSizer sizer = new FlowSizer();
        sizer.add(FIELD_ROW_H);
        sizer.add(actionRowHeight(width, 2));
        sizer.add(FIELD_ROW_H);
        sizer.add(FIELD_ROW_H);
        sizer.add(MUTED_ROW_H);
        sizer.add(FIELD_H);
        return sizer.height();
    }

    private int itemRawHeight(int width) {
        FlowSizer sizer = new FlowSizer();
        sizer.add(MUTED_ROW_H);
        sizer.add(RAW_PREVIEW_H);
        sizer.add(actionRowHeight(width, 3));
        sizer.add(FIELD_H);
        return sizer.height();
    }

    private void renderFireballs(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
        renderCard(context, "Fireballs", UiBounds.of(x, y, w, fireballsCardHeight()));
        int innerX = x + 8;
        int innerY = y + 18;
        int innerW = Math.max(1, w - 16);
        int presetW = Math.min(118, Math.max(84, innerW / 3));
        drawLabel(context, "Strength", innerX, innerY, presetW);
        renderDropdown(context, null, UiBounds.of(innerX, innerY + 11, presetW, FIELD_H), FIREBALL_PRESETS[fireballPreset], List.of(FIREBALL_PRESETS), mouseX, mouseY);
        renderSliderRow(context, module, "fireball-delay", innerX + presetW + 8, innerY + 7, Math.max(90, innerW - presetW - 8), mouseX, mouseY);
        renderBindRow(context, module, innerX, y + 54, innerW, "Stream Bind", "fireball-stream-bind", mouseX, mouseY);
        renderBindRow(context, module, innerX, y + 75, innerW, "Firestorm Bind", "firestorm-bind", mouseX, mouseY);
    }

    private void renderItemEditor(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
        int cardH = itemEditorHeight(w);
        renderCard(context, "Held Item Editor", UiBounds.of(x, y, w, cardH));
        int tx = x + CARD_PAD_X;
        int ty = y + 18;
        int tw = Math.max(1, w - CARD_PAD_X * 2);
        Flow flow = new Flow(tx, ty, tw);
        UiBounds strip = flow.take(TAB_H);
        for (int i = 0; i < ITEM_TABS.length; i++) {
            UiBounds tab = TabStrip.tabBounds(strip, ITEM_TABS.length, i, context.theme().spacing().controlGap);
            boolean active = i == itemTab;
            TabStrip.renderTab(context, tab, ITEM_TABS[i], active);
            addViewportHit(new Hit(HitType.ITEM_TAB, tab, null, Integer.toString(i)));
        }
        flow.gap(FLOW_GAP);
        switch (itemTab) {
            case 1 -> renderItemVisual(context, module, flow, mouseX, mouseY);
            case 2 -> renderItemStats(context, module, flow, mouseX, mouseY);
            case 3 -> renderItemRaw(context, module, flow, mouseX, mouseY);
            default -> renderItemBasic(context, module, flow, mouseX, mouseY);
        }
    }

    private void renderItemBasic(UiContext context, BuiltinModules.AdminToolsModule module, Flow flow, int mouseX, int mouseY) {
        UiBounds info = flow.take(MUTED_ROW_H);
        drawMuted(context, "Fill held, copy a targeted held item, or type an item id.", info.x(), info.y(), info.width());
        int gap = 5;
        if (flow.w >= 270) {
            UiBounds row = flow.take(FIELD_ROW_H);
            int pickW = 52;
            int numericW = 54;
            int idW = Math.max(80, row.width() - pickW - gap - (numericW + gap) * 2);
            renderTextField(context, module, option(module, "nbt-item-id"), row.x(), row.y(), idW, "Item ID", mouseX, mouseY);
            renderActionButton(context, module, UiBounds.of(row.x() + idW + gap, row.y() + LABEL_H + 2, pickW, FIELD_H), custom("List", "pick-item"), mouseX, mouseY);
            renderTextField(context, module, option(module, "nbt-item-count"), row.x() + idW + gap + pickW + gap, row.y(), numericW, "Count", mouseX, mouseY);
            renderTextField(context, module, option(module, "nbt-max-stack"), row.x() + idW + gap + pickW + gap + numericW + gap, row.y(), numericW, "Max", mouseX, mouseY);
        } else {
            UiBounds itemRow = flow.take(FIELD_ROW_H);
            int pickW = 52;
            int idW = Math.max(60, itemRow.width() - pickW - gap);
            renderTextField(context, module, option(module, "nbt-item-id"), itemRow.x(), itemRow.y(), idW, "Item ID", mouseX, mouseY);
            renderActionButton(context, module, UiBounds.of(itemRow.x() + idW + gap, itemRow.y() + LABEL_H + 2, pickW, FIELD_H), custom("List", "pick-item"), mouseX, mouseY);
            renderInlineFields(context, module, flow.take(FIELD_ROW_H), mouseX, mouseY, "nbt-item-count", "nbt-max-stack");
        }
        renderTextField(context, module, option(module, "nbt-custom-data"), flow.take(FIELD_ROW_H), "Custom data SNBT", mouseX, mouseY);
        renderGeneratedField(context, module, option(module, "nbt-item-components"), flow.take(FIELD_ROW_H), "Generated component patch");
        renderTextField(context, module, option(module, "nbt-command"), flow.take(FIELD_ROW_H), "Optional embedded command", mouseX, mouseY);
        renderActionRow(context, module, flow.take(actionRowHeight(flow.w, 3)), mouseX, mouseY,
            custom("Pick Item", "pick-inventory-item"), action("Copy Target", "copy-target-item"), action("Apply to Held", "apply-held-item"));
        renderRawEditorActionRow(context, module, flow.take(FIELD_H), mouseX, mouseY);
    }

    private void renderItemVisual(UiContext context, BuiltinModules.AdminToolsModule module, Flow flow, int mouseX, int mouseY) {
        renderTextField(context, module, option(module, "nbt-item-name"), flow.take(FIELD_ROW_H), "Display name", mouseX, mouseY);
        renderTextField(context, module, option(module, "nbt-item-lore"), flow.take(FIELD_ROW_H), "Lore lines separated with |", mouseX, mouseY);
        Setting<?, ?> glint = option(module, "nbt-glint");
        Setting<?, ?> rarity = option(module, "nbt-rarity");
        Setting<?, ?> unbreakable = option(module, "nbt-unbreakable");
        if (flow.w >= 280) {
            UiBounds row = flow.take(FIELD_ROW_H);
            int glintW = Math.max(76, (row.width() - 8) / 3);
            int rarityW = Math.max(86, (row.width() - 8) / 3);
            drawLabel(context, "Glint", row.x() + 2, row.y(), glintW - 4);
            renderDropdown(context, glint, UiBounds.of(row.x(), row.y() + LABEL_H + 2, glintW, FIELD_H), module.value(glint.id()), glint.choices(), mouseX, mouseY);
            drawLabel(context, "Rarity", row.x() + glintW + 4, row.y(), rarityW - 4);
            renderDropdown(context, rarity, UiBounds.of(row.x() + glintW + 4, row.y() + LABEL_H + 2, rarityW, FIELD_H), module.value(rarity.id()), rarity.choices(), mouseX, mouseY);
            renderBooleanInline(context, module, unbreakable, UiBounds.of(row.x() + glintW + rarityW + 8, row.y() + LABEL_H + 2, Math.max(40, row.width() - glintW - rarityW - 8), FIELD_H), "Unbreakable", mouseX, mouseY);
        } else {
            UiBounds row = flow.take(FIELD_ROW_H);
            renderDropdownField(context, module, glint, UiBounds.of(row.x(), row.y(), Math.max(1, (row.width() - 4) / 2), FIELD_ROW_H), "Glint", mouseX, mouseY);
            renderDropdownField(context, module, rarity, UiBounds.of(row.x() + (row.width() + 4) / 2, row.y(), Math.max(1, (row.width() - 4) / 2), FIELD_ROW_H), "Rarity", mouseX, mouseY);
            renderBooleanInline(context, module, unbreakable, flow.take(FIELD_ROW_H), "Unbreakable", mouseX, mouseY);
        }
        renderActionRow(context, module, flow.take(actionRowHeight(flow.w, 2)), mouseX, mouseY,
            custom("Edit Lore", "edit-lore"),
            custom("Edit Full NBT", "edit-item-stack"));
        renderFittedActionRow(context, module, flow.take(FIELD_H), mouseX, mouseY,
            custom("Clear", "item-clear"), custom("Pick Item", "pick-inventory-item"), action("Fill Held", "fill-held-item"),
            action("Copy Target", "copy-target-item"), action("Apply to Held", "apply-held-item"), custom("Give", "give-item"));
    }

    private void renderItemStats(UiContext context, BuiltinModules.AdminToolsModule module, Flow flow, int mouseX, int mouseY) {
        renderInlineFields(context, module, flow.take(FIELD_ROW_H), mouseX, mouseY, "nbt-max-damage", "nbt-damage");
        renderActionRow(context, module, flow.take(actionRowHeight(flow.w, 2)), mouseX, mouseY,
            custom("Edit Enchants", "edit-enchants"), custom("Edit Attributes", "edit-attributes"));
        renderTextField(context, module, option(module, "nbt-enchants"), flow.take(FIELD_ROW_H), "Enchantments raw list", mouseX, mouseY);
        renderTextField(context, module, option(module, "nbt-attributes"), flow.take(FIELD_ROW_H), "Attributes raw list", mouseX, mouseY);
        UiBounds info = flow.take(MUTED_ROW_H);
        drawMuted(context, "Raw lists stay editable for exact component control.", info.x(), info.y(), info.width());
        renderFittedActionRow(context, module, flow.take(FIELD_H), mouseX, mouseY,
            custom("Clear", "item-clear"), custom("Pick Item", "pick-inventory-item"), action("Fill Held", "fill-held-item"),
            action("Copy Target", "copy-target-item"), action("Apply to Held", "apply-held-item"), custom("Give", "give-item"));
    }

    private void renderItemRaw(UiContext context, BuiltinModules.AdminToolsModule module, Flow flow, int mouseX, int mouseY) {
        UiBounds info = flow.take(MUTED_ROW_H);
        drawMuted(context, "Complete ItemStack SNBT. Click the preview to edit; raw edits are authoritative.", info.x(), info.y(), info.width());

        UiBounds preview = flow.take(RAW_PREVIEW_H);
        renderCard(context, "Full ItemStack SNBT", preview);
        addViewportHit(new Hit(HitType.ACTION, preview, null, "edit-item-stack"));
        List<String> lines = AutismItemNbtInspector.prettySnbtLines(module.value("nbt-item-stack"));
        int x = preview.x() + 7;
        int y = preview.y() + 18;
        int width = Math.max(1, preview.width() - 14);
        int maxLines = Math.max(1, (preview.height() - 23) / 10);
        int shown = Math.min(maxLines, lines.size());
        for (int i = 0; i < shown; i++) {
            String line = i == shown - 1 && lines.size() > shown
                ? lines.get(i) + "  ... (" + (lines.size() - shown) + " more lines)"
                : lines.get(i);
            drawRawPreviewLine(context, line, x, y + i * 10, width);
        }

        renderActionRow(context, module, flow.take(actionRowHeight(flow.w, 3)), mouseX, mouseY,
            custom("Edit Raw NBT", "edit-item-stack"), action("Fill Held", "fill-held-item"),
            action("Apply to Held", "apply-held-item"));
        renderFittedActionRow(context, module, flow.take(FIELD_H), mouseX, mouseY,
            custom("Copy Raw", "copy-item-stack-snbt"), custom("Clear", "item-clear"), custom("Give", "give-item"));
    }

    private void drawRawPreviewLine(UiContext context, String line, int x, int y, int maxWidth) {
        int cursor = x;
        int remaining = maxWidth;
        for (AutismItemNbtInspector.TextToken token
            : AutismItemNbtInspector.tokenizeStructuredText(line, context.theme().colors().text)) {
            if (token == null || token.text() == null || token.text().isEmpty() || remaining <= 0) continue;
            String text = context.text().trimEllipsis(token.text(), remaining);
            context.text().draw(context.graphics(), text, cursor, y, token.color());
            int width = context.text().width(text);
            cursor += width;
            remaining -= width;
            if (!text.equals(token.text())) break;
        }
    }

    private void renderForceOp(UiContext context, BuiltinModules.AdminToolsModule module, int x, int y, int w, int mouseX, int mouseY) {
        int innerX = x + 10;
        int innerW = Math.max(1, w - 20);
        int cursor = y + 20;
        renderCard(context, "AuthMe ForceOP", UiBounds.of(x, y, w, forceOpCardHeight(w)));
        renderSliderRow(context, module, "forceop-delay", innerX, cursor, innerW, mouseX, mouseY);
        cursor += 22;
        renderSliderRow(context, module, "forceop-min-length", innerX, cursor, innerW, mouseX, mouseY);
        cursor += 22;
        renderSliderRow(context, module, "forceop-max-length", innerX, cursor, innerW, mouseX, mouseY);
        cursor += 22;
        Setting<?, ?> wait = option(module, "forceop-wait");
        UiBounds toggle = UiBounds.of(innerX, cursor + 2, 34, FIELD_H);
        CompactFieldFactory.toggle(context, toggle, Boolean.parseBoolean(module.value(wait.id())), toggle.contains(mouseX, mouseY), "admintools:" + module.id() + "/" + wait.id());
        addViewportHit(new Hit(HitType.BOOLEAN, UiBounds.of(innerX, cursor, innerW, 20), wait, ""));
        drawLabel(context, wait.label(), innerX + 40, cursor + 6, Math.max(1, innerW - 46));
        cursor += 26;
        int actionH = actionRowHeight(innerW, 3);
        renderActionRow(context, module, UiBounds.of(innerX, cursor, innerW, actionH), mouseX, mouseY,
            custom("Load TXT", "forceop-load"), custom("Unload", "forceop-unload"), custom("Preview", "forceop-preview"));
        cursor += actionH + 6;
        int runH = actionRowHeight(innerW, 1);
        renderActionRow(context, module, UiBounds.of(innerX, cursor, innerW, runH), mouseX, mouseY,
            custom(module.isForceOpRunning() ? "Stop" : "Start", module.isForceOpRunning() ? "forceop-stop" : "forceop-start"));
        cursor += runH + 5;
        drawText(context, "Passwords: " + module.getForceOpTotal() + "   Attempt: " + module.getForceOpIndex() + " / " + module.getForceOpTotal(),
            innerX, cursor, innerW, module.isForceOpRunning() ? context.theme().colors().accent : context.theme().colors().muted);
    }

    private void renderCard(UiContext context, String title, UiBounds bounds) {
        SectionPanel.render(context, bounds, title);
    }

    private void renderActionRow(UiContext context, Module module, int x, int y, int w, int mouseX, int mouseY, AdminAction... actions) {
        renderActionRow(context, module, UiBounds.of(x, y, w, actionRowHeight(w, actions == null ? 0 : actions.length)), mouseX, mouseY, actions);
    }

    private void renderActionRow(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, AdminAction... actions) {
        if (actions == null || actions.length == 0) return;
        int gap = 3;
        int minCellW = 54;
        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();
        int columns = Math.max(1, Math.min(actions.length, (w + gap) / Math.max(1, minCellW + gap)));
        for (int index = 0; index < actions.length; index++) {
            AdminAction action = actions[index];
            int rowIndex = index / columns;
            int colIndex = index % columns;
            int yOffset = rowIndex * (FIELD_H + 3);
            UiBounds row = UiBounds.of(x, y + yOffset, w, FIELD_H);
            int countInRow = Math.min(columns, actions.length - rowIndex * columns);
            UiBounds cell = CompactToolbar.cell(row, countInRow, colIndex, gap);
            Button.Tone tone = action.label.toLowerCase(Locale.ROOT).contains("run") || action.label.equals("Start")
                ? Button.Tone.PRIMARY : action.label.equals("Stop") || action.label.equals("Clear") ? Button.Tone.DANGER : Button.Tone.NORMAL;
            Button.render(context, cell, action.label, tone, cell.contains(mouseX, mouseY), false);
            addViewportHit(new Hit(HitType.ACTION, cell, action.optionId == null ? null : option(module, action.optionId), action.command));
        }
    }

    private void renderFittedActionRow(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, AdminAction... actions) {
        if (actions == null || actions.length == 0) return;
        int gap = 3;
        int pad = 6;
        int n = actions.length;
        int[] want = new int[n];
        long total = (long) gap * (n - 1);
        long labelTotal = 0;
        for (int i = 0; i < n; i++) {
            int label = context.text().width(actions[i].label);
            labelTotal += label;
            want[i] = Math.max(1, label + pad);
            total += want[i];
        }
        int x = bounds.x();
        int usable = Math.max(1, bounds.width() - gap * (n - 1));
        for (int i = 0; i < n; i++) {
            int w;
            if (i == n - 1) {
                w = Math.max(1, bounds.right() - x);
            } else if (total <= bounds.width()) {

                int leftover = (int) (bounds.width() - total);
                w = want[i] + leftover / n;
            } else {

                w = (int) Math.max(1, usable * (long) context.text().width(actions[i].label) / Math.max(1, labelTotal));
            }
            renderActionButton(context, module, UiBounds.of(x, bounds.y(), w, FIELD_H), actions[i], mouseX, mouseY);
            x += w + gap;
        }
    }

    private void renderRawEditorActionRow(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY) {
        renderFittedActionRow(context, module, bounds, mouseX, mouseY,
            custom("Clear", "item-clear"), action("Fill", "fill-held-item"),
            custom("Edit Custom Data", "edit-custom-data"), custom("Edit Full NBT", "edit-item-stack"),
            custom("Give", "give-item"));
    }

    private int actionRowHeight(int width, int actionCount) {
        if (actionCount <= 0) return 0;
        int gap = 3;
        int minCellW = 54;
        int columns = Math.max(1, Math.min(actionCount, (Math.max(1, width) + gap) / Math.max(1, minCellW + gap)));
        int rows = (int) Math.ceil(actionCount / (double) columns);
        return rows * FIELD_H + Math.max(0, rows - 1) * gap;
    }

    private void renderActionButton(UiContext context, Module module, UiBounds bounds, AdminAction action, int mouseX, int mouseY) {
        if (action == null) return;
        Button.render(context, bounds, action.label, Button.Tone.NORMAL, bounds.contains(mouseX, mouseY), false);
        addViewportHit(new Hit(HitType.ACTION, bounds, action.optionId == null ? null : option(module, action.optionId), action.command));
    }

    private void renderBindRow(UiContext context, Module module, int x, int y, int w, String label, String optionId, int mouseX, int mouseY) {
        Setting<?, ?> option = option(module, optionId);
        drawLabel(context, label, x + 2, y + 5, w - 160);
        UiBounds bind = UiBounds.of(x + w - 126, y, 78, FIELD_H);
        UiBounds clear = UiBounds.of(x + w - 44, y, 44, FIELD_H);
        boolean focused = bindingOption == option;
        Button.render(context, bind, focused ? "Press key" : AutismBindUtil.getBindName(parseInt(module.value(option.id()), -1)), Button.Tone.NORMAL, bind.contains(mouseX, mouseY), focused);
        Button.render(context, clear, "Clear", Button.Tone.NORMAL, clear.contains(mouseX, mouseY), false);
        addViewportHit(new Hit(HitType.KEYBIND, bind, option, ""));
        addViewportHit(new Hit(HitType.CLEAR_KEYBIND, clear, option, ""));
    }

    private void renderInlineFields(UiContext context, Module module, int x, int y, int w, int mouseX, int mouseY, String... ids) {
        renderInlineFields(context, module, UiBounds.of(x, y, w, FIELD_ROW_H), mouseX, mouseY, ids);
    }

    private void renderInlineFields(UiContext context, Module module, UiBounds bounds, int mouseX, int mouseY, String... ids) {
        int gap = 4;
        for (int index = 0; index < ids.length; index++) {
            String id = ids[index];
            UiBounds field = CompactForm.cell(bounds, ids.length, index, gap);
            renderTextField(context, module, option(module, id), field.x(), field.y(), field.width(), option(module, id).label(), mouseX, mouseY);
        }
    }

    private void renderTextField(UiContext context, Module module, Setting<?, ?> option, int x, int y, int w, String label, int mouseX, int mouseY) {
        renderTextField(context, module, option, UiBounds.of(x, y, w, FIELD_ROW_H), label, mouseX, mouseY);
    }

    private void renderTextField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label, int mouseX, int mouseY) {
        if (option == null) return;
        drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
        UiBounds bounds = UiBounds.of(row.x(), row.y() + LABEL_H + 2, row.width(), FIELD_H);
        boolean focused = editing != null && editing.option == option;
        CompactFieldFactory.text(context, bounds, focused ? editing.text : module.value(option.id()), "", focused,
            focused ? editing.cursor : module.value(option.id()).length(),
            focused ? editing.selectionStart() : -1,
            focused ? editing.selectionEnd() : -1);
        addViewportHit(new Hit(HitType.TEXT, bounds, option, ""));
    }

    private void renderGeneratedField(UiContext context, Module module, Setting<?, ?> option, int x, int y, int w, String label) {
        renderGeneratedField(context, module, option, UiBounds.of(x, y, w, FIELD_ROW_H), label);
    }

    private void renderGeneratedField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label) {
        drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
        UiBounds bounds = UiBounds.of(row.x(), row.y() + LABEL_H + 2, row.width(), FIELD_H);
        CompactFieldFactory.text(context, bounds, module.displayValue(option), "", false, 0);
    }

    private void renderDropdownField(UiContext context, Module module, Setting<?, ?> option, UiBounds row, String label, int mouseX, int mouseY) {
        if (option == null) return;
        drawLabel(context, label, row.x() + 2, row.y(), row.width() - 4);
        renderDropdown(context, option, UiBounds.of(row.x(), row.y() + LABEL_H + 2, row.width(), FIELD_H), module.value(option.id()), option.choices(), mouseX, mouseY);
    }

    private void renderBooleanInline(UiContext context, Module module, Setting<?, ?> option, UiBounds bounds, String label, int mouseX, int mouseY) {
        if (option == null) return;
        UiBounds toggle = UiBounds.of(bounds.x(), bounds.y(), 34, FIELD_H);
        CompactFieldFactory.toggle(context, toggle, Boolean.parseBoolean(module.value(option.id())), bounds.contains(mouseX, mouseY), "admintools:" + module.id() + "/" + option.id());
        addViewportHit(new Hit(HitType.BOOLEAN, bounds, option, ""));
        drawLabel(context, label, toggle.right() + 6, bounds.y() + 4, Math.max(1, bounds.right() - toggle.right() - 8));
    }

    private void renderSliderRow(UiContext context, Module module, String optionId, int x, int y, int w, int mouseX, int mouseY) {
        Setting<?, ?> option = option(module, optionId);
        drawLabel(context, option.label(), x, y + 5, 82);
        UiBounds control = UiBounds.of(x + 84, y + 2, Math.max(60, w - 84), FIELD_H);
        renderNumber(context, module, option, control, mouseX, mouseY);
    }

    private void renderNumber(UiContext context, Module module, Setting<?, ?> option, UiBounds control, int mouseX, int mouseY) {
        int valueW = Math.min(54, Math.max(42, control.width() / 4));
        UiBounds slider = UiBounds.of(control.x(), control.y(), Math.max(40, control.width() - valueW - 4), control.height());
        UiBounds value = UiBounds.of(slider.right() + 4, control.y(), valueW, control.height());
        double current = option.kind() == Kind.INTEGER
            ? parseInt(module.value(option.id()), parseInt(option.defaultValue(), 0))
            : parseDouble(module.value(option.id()), parseDouble(option.defaultValue(), 0.0));
        Slider.render(context, slider, Slider.ratio(current, option.sliderMin(), option.sliderMax()), slider.contains(mouseX, mouseY));
        boolean focused = editing != null && editing.option == option;
        CompactFieldFactory.text(context, value, focused ? editing.text : module.value(option.id()), "", focused,
            focused ? editing.cursor : module.value(option.id()).length(),
            focused ? editing.selectionStart() : -1,
            focused ? editing.selectionEnd() : -1);
        addViewportHit(new Hit(HitType.SLIDER, slider, option, ""));
        addViewportHit(new Hit(HitType.TEXT, value, option, ""));
    }

    private void renderDropdown(UiContext context, Setting<?, ?> option, UiBounds bounds, String label, List<String> choices, int mouseX, int mouseY) {
        boolean open = dropdown != null && dropdownOption == option && dropdown.bounds().equals(bounds);
        CompactFieldFactory.dropdown(context, bounds, label, bounds.contains(mouseX, mouseY), open);
        addViewportHit(new Hit(HitType.DROPDOWN, bounds, option, String.join("\u0000", choices)));
        if (open) return;
        if (dropdown != null && dropdown.bounds().equals(bounds)) dropdownOption = option;
    }

    private void drawLabel(UiContext context, String text, int x, int y, int width) {
        drawText(context, text, x, y, width, context.theme().colors().muted);
    }

    private void drawMuted(UiContext context, String text, int x, int y, int width) {
        drawText(context, text, x, y, width, context.theme().colors().muted);
    }

    private void drawText(UiContext context, String text, int x, int y, int width, int color) {
        context.text().drawFitted(context.graphics(), text == null ? "" : text, x, y, Math.max(1, width), color);
    }

    private void addViewportHit(Hit hit) {
        if (lastViewport == null || intersects(lastViewport, hit.bounds)) hits.add(hit);
    }

    private boolean intersects(UiBounds a, UiBounds b) {
        return b.right() > a.x() && b.x() < a.right() && b.bottom() > a.y() && b.y() < a.bottom();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (button == 0) {
            AutismWindowLayout bounds = getBounds();
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            if (isOverDragBar(mouseX, mouseY)) {
                headerDragging = true;
                return true;
            }
        }
        if (collapsed) return true;
        BuiltinModules.AdminToolsModule module = adminTools();
        if (module == null) return true;
        if (dropdown != null) {
            dropdown.mouseClicked((int) mouseX, (int) mouseY, button);
            if (!dropdown.isOpen()) clearDropdown();
            return true;
        }
        finishEditing(true);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds.contains((int) mouseX, (int) mouseY)) continue;
            switch (hit.type) {
                case TAB -> {
                    activeTab = clamp(parseInt(hit.value, 0), 0, TABS.length - 1);
                    scroll = 0;
                    return true;
                }
                case ITEM_TAB -> {
                    itemTab = clamp(parseInt(hit.value, 0), 0, ITEM_TABS.length - 1);
                    if (itemTab == 3) module.prepareRawItemStackEditor();
                    scroll = 0;
                    return true;
                }
                case BOOLEAN -> {
                    module.setValue(hit.option.id(), Boolean.toString(!Boolean.parseBoolean(module.value(hit.option.id()))));
                    if (hit.option.id().startsWith("nbt-")) module.prepareRawItemStackEditor();
                    return true;
                }
                case ACTION -> {
                    handleAction(module, hit);
                    return true;
                }
                case TEXT -> {
                    beginEditing(module, hit.option, (int) mouseX, hit.bounds);
                    return true;
                }
                case DROPDOWN -> {
                    List<String> values = hit.value.isEmpty() ? List.of() : List.of(hit.value.split("\u0000", -1));
                    String selected = hit.option == null ? FIREBALL_PRESETS[fireballPreset] : module.value(hit.option.id());
                    dropdownOption = hit.option;
                    dropdown = new Dropdown(hit.bounds, values, selected, value -> {
                        if (hit.option == null) {
                            fireballPreset = Math.max(0, values.indexOf(value));
                            applyFireballPreset(module, fireballPreset);
                        } else {
                            module.setValue(hit.option.id(), value);
                            if (hit.option.id().startsWith("nbt-")) module.prepareRawItemStackEditor();
                        }
                    });
                    dropdown.open();
                    return true;
                }
                case KEYBIND -> {
                    bindingOption = hit.option;
                    return true;
                }
                case CLEAR_KEYBIND -> {
                    module.setValue(hit.option.id(), "-1");
                    return true;
                }
                case SLIDER -> {
                    sliderOption = hit.option;
                    updateSlider(module, (int) mouseX);
                    return true;
                }
                case SCROLLBAR -> {
                    Scrollbar.Metrics metrics = scrollbarMetrics();
                    if (metrics != null && metrics.maxScroll() > 0) {
                        scrollbarGrabOffset = metrics.thumb().contains((int) mouseX, (int) mouseY) ? (int) mouseY - metrics.thumb().y() : metrics.thumb().height() / 2;
                        scrollbarDragging = true;
                        scroll = Scrollbar.scrollFromMouse(metrics, (int) mouseY, scrollbarGrabOffset);
                    }
                    return true;
                }
            }
        }
        return isMouseOver(mouseX, mouseY);
    }

    private void handleAction(BuiltinModules.AdminToolsModule module, Hit hit) {
        if (hit.option != null && hit.option.action() != null) {
            hit.option.action().run();
            return;
        }
        switch (hit.value) {
            case "give-item" -> module.giveEditorItem();
            case "item-clear" -> clearItemEditor(module);
            case "pick-item" -> openItemPicker(module, hit.bounds);
            case "pick-inventory-item" -> startInventoryItemPicker(module);
            case "edit-lore" -> openItemOptionEditor(module, "nbt-item-lore", AutismAdminItemOptionScreen.Mode.LORE);
            case "edit-enchants" -> openItemStructuredEditor(module, "nbt-enchants", AutismAdminItemStructuredScreen.Mode.ENCHANTMENTS);
            case "edit-attributes" -> openItemStructuredEditor(module, "nbt-attributes", AutismAdminItemStructuredScreen.Mode.ATTRIBUTES);
            case "edit-custom-data" -> openItemOptionEditor(module, "nbt-custom-data", AutismAdminItemOptionScreen.Mode.RAW);
            case "edit-item-stack" -> {
                module.prepareRawItemStackEditor();
                openItemOptionEditor(module, "nbt-item-stack", AutismAdminItemOptionScreen.Mode.FULL_NBT);
            }
            case "copy-item-stack-snbt" -> {
                Minecraft mc = Minecraft.getInstance();
                module.prepareRawItemStackEditor();
                String raw = module.value("nbt-item-stack");
                if (mc == null || mc.keyboardHandler == null || raw == null || raw.isBlank()) {
                    AutismNotifications.error("No full ItemStack NBT to copy.");
                } else {
                    mc.keyboardHandler.setClipboard(raw);
                    AutismNotifications.copied("Copied full ItemStack NBT.");
                }
            }
            case "forceop-load" -> module.loadForceOpPasswords();
            case "forceop-unload" -> module.unloadForceOpPasswords();
            case "forceop-start" -> module.startForceOp();
            case "forceop-stop" -> module.stopForceOp();
            case "forceop-preview" -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) mc.gui.setScreen(new AutismForceOpPreviewScreen(mc.gui.screen(), module.getForceOpPasswords()));
            }
            default -> {
            }
        }
    }

    private void openItemOptionEditor(Module module, String optionId, AutismAdminItemOptionScreen.Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        Setting<?, ?> option = option(module, optionId);
        if (mc != null && option != null) mc.gui.setScreen(new AutismAdminItemOptionScreen(mc.gui.screen(), module, option, mode));
    }

    private void openItemStructuredEditor(Module module, String optionId, AutismAdminItemStructuredScreen.Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        Setting<?, ?> option = option(module, optionId);
        if (mc != null && option != null) mc.gui.setScreen(new AutismAdminItemStructuredScreen(mc.gui.screen(), module, option, mode));
    }

    private void openItemPicker(Module module, UiBounds anchor) {
        clearDropdown();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.gui.setScreen(new AutismItemPickerScreen(mc.gui.screen(), module.value("nbt-item-id"), value -> {
                if (module instanceof BuiltinModules.AdminToolsModule adminTools) {
                    adminTools.selectEditorItemId(value);
                    adminTools.prepareRawItemStackEditor();
                } else {
                    module.setValue("nbt-item-id", value);
                }
            }));
        }
    }

    private void startInventoryItemPicker(BuiltinModules.AdminToolsModule module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            AutismNotifications.warning("Join a world first.");
            return;
        }
        finishEditing(true);
        clearDropdown();
        itemStackCaptureActive = true;
        restoreVisibleAfterItemStackCapture = visible;
        visible = false;
        autoOpenedInventoryForItemStackCapture = !(mc.gui.screen() instanceof AbstractContainerScreen<?>);
        screenBeforeItemStackCapture = autoOpenedInventoryForItemStackCapture ? mc.gui.screen() : null;
        AutismSharedState.get().setCaptureMode(true);
        AutismSharedState.get().setCaptureCancelCallback(this::cancelInventoryItemPicker);
        AutismNotifications.show("Right-click an inventory item to pick it.", 0xFF35D873);
        if (autoOpenedInventoryForItemStackCapture) {
            mc.execute(() -> {
                if (mc.player != null) mc.gui.setScreen(new InventoryScreen(mc.player));
            });
        }
    }

    public boolean wantsItemStackCapture() {
        return itemStackCaptureActive;
    }

    public boolean shouldRenderAbstractContainerScreenCaptureBanner() {
        return itemStackCaptureActive;
    }

    public String getAbstractContainerScreenCaptureTitle() {
        return "Capturing NBT item";
    }

    public String getAbstractContainerScreenCaptureInstruction() {
        return "Right-click a slot to fill the NBT item editor. Esc = cancel";
    }

    public String getAbstractContainerScreenCaptureHoverText(Slot slot, String itemName, String registryId) {
        if (slot == null) return "";
        String itemText = registryId != null && !registryId.isBlank() ? registryId : itemName;
        if (itemText == null || itemText.isBlank()) itemText = "Empty slot";
        int visibleSlot = AutismInventoryHelper.toUserVisibleSlot(Minecraft.getInstance(), slot);
        return "Hover: " + visibleSlot + " | " + itemText;
    }

    public boolean onInventoryItemStackCapture(Slot slot) {
        if (!itemStackCaptureActive) return false;
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        BuiltinModules.AdminToolsModule module = adminTools();
        if (module == null || stack.isEmpty()) {
            AutismNotifications.warning("Pick a non-empty item slot.");
            return true;
        }
        module.fillItemEditorFromStack(stack, false);
        AutismNotifications.show("NBT editor filled from " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ".", 0xFF35D873);
        finishInventoryItemPicker();
        return true;
    }

    public void cancelInventoryItemPicker() {
        if (!itemStackCaptureActive) return;
        AutismNotifications.warning("Item pick cancelled.");
        finishInventoryItemPicker();
    }

    private void finishInventoryItemPicker() {
        Minecraft mc = Minecraft.getInstance();
        itemStackCaptureActive = false;
        visible = restoreVisibleAfterItemStackCapture;
        restoreVisibleAfterItemStackCapture = false;
        AutismSharedState.get().setCaptureMode(false);
        AutismSharedState.get().setCaptureCancelCallback(null);
        AutismOverlayManager.get().bringToFront(this);
        if (autoOpenedInventoryForItemStackCapture) {
            Screen restore = screenBeforeItemStackCapture;
            mc.execute(() -> {
                if (mc.gui.screen() instanceof InventoryScreen) {
                    if (restore != null) mc.gui.setScreen(restore);
                } else if (restore != null && mc.gui.screen() == null) {
                    mc.gui.setScreen(restore);
                }
            });
        }
        autoOpenedInventoryForItemStackCapture = false;
        screenBeforeItemStackCapture = null;
    }

    private void clearItemEditor(Module module) {
        module.setValue("nbt-item-id", "");
        module.setValue("nbt-item-count", "1");
        module.setValue("nbt-max-stack", "64");
        module.setValue("nbt-custom-data", "");
        module.setValue("nbt-item-name", "");
        module.setValue("nbt-item-lore", "");
        module.setValue("nbt-glint", "Default");
        module.setValue("nbt-rarity", "Default");
        module.setValue("nbt-unbreakable", "false");
        module.setValue("nbt-max-damage", "0");
        module.setValue("nbt-damage", "0");
        module.setValue("nbt-enchants", "");
        module.setValue("nbt-attributes", "");
        module.setValue("nbt-command", "");
        module.setValue("nbt-item-components", "");
        module.setValue("nbt-imported-components", "");
        module.setValue("nbt-imported-signature", "");
        module.setValue("nbt-item-stack", "");
        module.setValue("nbt-item-stack-signature", "");
    }

    private void applyFireballPreset(Module module, int preset) {
        String delay = module.value("fireball-delay");
        if (delay == null || delay.isBlank()) delay = "1";
        switch (preset) {
            case 1 -> setFireball(module, "48", "10.0", "2.4", "5", delay, "2.5", "Cone", "true");
            case 2 -> setFireball(module, "128", "16.0", "3.2", "8", delay, "3.0", "Cone", "true");
            case 3 -> setFireball(module, "16", "0.0", "5.0", "6", delay, "2.5", "Look", "false");
            default -> setFireball(module, "12", "8.0", "1.8", "3", delay, "2.0", "Cone", "true");
        }
    }

    private void setFireball(Module module, String count, String spread, String speed, String power, String delay, String distance, String aim, String randomize) {
        module.setValue("fireball-count", count);
        module.setValue("fireball-spread", spread);
        module.setValue("fireball-speed", speed);
        module.setValue("fireball-power", power);
        module.setValue("fireball-delay", delay);
        module.setValue("fireball-distance", distance);
        module.setValue("fireball-aim", aim);
        module.setValue("fireball-randomize", randomize);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dropdown != null) dropdown.mouseReleased((int) mouseX, (int) mouseY, button);
        headerDragging = false;
        scrollbarDragging = false;
        draggingTextSelection = false;
        sliderOption = null;
        return visible && isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && headerDragging) {
            AutismWindowLayout current = getBounds();
            setBounds(new AutismWindowLayout(
                current.x + (int) Math.round(deltaX),
                current.y + (int) Math.round(deltaY),
                current.width,
                current.height,
                current.visible,
                current.collapsed
            ));
            return true;
        }
        BuiltinModules.AdminToolsModule module = adminTools();
        if (module == null) return false;
        if (dropdown != null) {
            dropdown.mouseDragged((int) mouseX, (int) mouseY, button, deltaX, deltaY);
            return true;
        }
        if (scrollbarDragging) {
            Scrollbar.Metrics metrics = scrollbarMetrics();
            if (metrics != null) scroll = Scrollbar.scrollFromMouse(metrics, (int) mouseY, scrollbarGrabOffset);
            return true;
        }
        if (sliderOption != null) {
            updateSlider(module, (int) mouseX);
            return true;
        }

        if (draggingTextSelection && button == 0 && editing != null && editingTextFieldBounds != null) {
            moveEditingCursor(cursorAtX(editing.text, (int) mouseX, editingTextFieldBounds), true);
            return true;
        }
        return false;
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed) return false;
        if (dropdown != null) {
            dropdown.mouseScrolled((int) mouseX, (int) mouseY, amount);
            return true;
        }
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll = clamp(scroll + (amount < 0 ? 36 : -36), 0, maxScroll(bodyBounds().height()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        BuiltinModules.AdminToolsModule module = adminTools();
        if (module == null) return false;
        if (bindingOption != null) {
            module.setValue(bindingOption.id(), Integer.toString(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode));
            bindingOption = null;
            return true;
        }
        if (editing != null) {
            boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishEditing(true);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                editing.selectAll();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                copyEditingSelection();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
                copyEditingSelection();
                deleteEditingSelection();
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                pasteEditingClipboard();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishEditing(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && deleteEditingSelection()) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && editing.cursor > 0) {
                editing.text = editing.text.substring(0, editing.cursor - 1) + editing.text.substring(editing.cursor);
                editing.cursor--;
                editing.clearSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE && deleteEditingSelection()) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE && editing.cursor < editing.text.length()) {
                editing.text = editing.text.substring(0, editing.cursor) + editing.text.substring(editing.cursor + 1);
                editing.clearSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                moveEditingCursor(Math.max(0, editing.cursor - 1), shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                moveEditingCursor(Math.min(editing.text.length(), editing.cursor + 1), shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                moveEditingCursor(0, shift);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                moveEditingCursor(editing.text.length(), shift);
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && dropdown != null) {
            clearDropdown();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editing == null) return false;
        if (chr >= 32 && chr != 127) {
            replaceEditingSelection(Character.toString(chr));
        }
        return true;
    }

    @Override
    public boolean hasTextFieldFocused() {
        return editing != null || bindingOption != null;
    }

    @Override
    public void clearTextFieldFocus() {
        finishEditing(true);
        bindingOption = null;
        clearDropdown();
    }

    private void beginEditing(Module module, Setting<?, ?> option, int mouseX, UiBounds bounds) {
        String value = module.value(option.id());
        editing = new Editing(option, value, cursorAtX(value, mouseX, bounds));
        editingTextFieldBounds = bounds;
        draggingTextSelection = true;
    }

    private int cursorAtX(String value, int mouseX, UiBounds bounds) {
        int local = mouseX - bounds.x() - 4;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return value.length();
        for (int i = 0; i <= value.length(); i++) {
            if (mc.font.width(value.substring(0, i)) >= local) return i;
        }
        return value.length();
    }

    private void beginEditing(Module module, Setting<?, ?> option) {
        if (module == null || option == null) return;
        String value = module.value(option.id());
        editing = new Editing(option, value, value.length());
    }

    private void moveEditingCursor(int cursor, boolean extendSelection) {
        if (editing == null) return;
        if (extendSelection && !editing.hasSelection()) editing.selectionAnchor = editing.cursor;
        editing.cursor = clamp(cursor, 0, editing.text.length());
        if (!extendSelection) editing.clearSelection();
    }

    private boolean deleteEditingSelection() {
        if (editing == null || !editing.hasSelection()) return false;
        int start = editing.selectionStart();
        int end = editing.selectionEnd();
        editing.text = editing.text.substring(0, start) + editing.text.substring(end);
        editing.cursor = start;
        editing.clearSelection();
        return true;
    }

    private void replaceEditingSelection(String replacement) {
        if (editing == null) return;
        String safe = replacement == null ? "" : replacement;
        int start = editing.hasSelection() ? editing.selectionStart() : editing.cursor;
        int end = editing.hasSelection() ? editing.selectionEnd() : editing.cursor;
        editing.text = editing.text.substring(0, start) + safe + editing.text.substring(end);
        editing.cursor = start + safe.length();
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
        replaceEditingSelection(sanitizeSingleLineClipboard(clipboard));
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

    private void finishEditing(boolean save) {
        if (editing == null) return;
        BuiltinModules.AdminToolsModule module = adminTools();
        String optionId = editing.option.id();
        if (save && module != null) {
            module.setValue(optionId, editing.text);
            if ("nbt-item-id".equals(optionId)) module.syncEditorMaxStackForItemId();
            if (optionId.startsWith("nbt-")) module.prepareRawItemStackEditor();
        }
        editing = null;
        editingTextFieldBounds = null;
        draggingTextSelection = false;
    }

    private void updateSlider(Module module, int mouseX) {
        if (module == null || sliderOption == null) return;
        for (Hit hit : hits) {
            if (hit.type != HitType.SLIDER || hit.option != sliderOption) continue;
            double value = Slider.valueFromMouse(mouseX, hit.bounds.x(), hit.bounds.width(), sliderOption.sliderMin(), sliderOption.sliderMax(), sliderOption.step());
            if (sliderOption.kind() == Kind.INTEGER) module.setValue(sliderOption.id(), Integer.toString((int) Math.round(value)));
            else module.setValue(sliderOption.id(), String.format(Locale.ROOT, "%.2f", value));
            return;
        }
    }

    private Scrollbar.Metrics scrollbarMetrics() {
        UiBounds body = bodyBounds();
        if (contentHeight <= body.height()) return null;
        UiBounds track = UiBounds.of(body.right() - SCROLLBAR_W, body.y(), SCROLLBAR_W, body.height());
        return Scrollbar.metrics(track, contentHeight, body.height(), scroll);
    }

    private UiBounds bodyBounds() {
        if (lastBodyBounds != null) return lastBodyBounds;
        int y = panelY + HEADER_HEIGHT + 5 + TAB_H + 5;
        return UiBounds.of(panelX + 5, y, panelWidth - 10, Math.max(1, panelY + panelHeight - y - 10));
    }

    private int maxScroll(int viewHeight) {
        return Math.max(0, contentHeight - Math.max(1, viewHeight));
    }

    private void clearDropdown() {
        if (dropdown != null) dropdown.close();
        dropdown = null;
        dropdownOption = null;
    }

    private BuiltinModules.AdminToolsModule adminTools() {
        Module module = ModuleRegistry.get("admin-tools");
        return module instanceof BuiltinModules.AdminToolsModule adminTools ? adminTools : null;
    }

    private Setting<?, ?> option(Module module, String id) {
        if (module == null || id == null) return null;
        for (Setting<?, ?> option : module.settings()) {
            if (id.equals(option.id())) return option;
        }
        return null;
    }

    private AdminAction action(String label, String optionId) {
        return new AdminAction(label, optionId, "");
    }

    private AdminAction custom(String label, String command) {
        return new AdminAction(label, null, command);
    }

    private String activeText(boolean active) {
        return active ? "Active" : "Idle";
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum HitType {
        TAB,
        ITEM_TAB,
        BOOLEAN,
        TEXT,
        SLIDER,
        DROPDOWN,
        KEYBIND,
        CLEAR_KEYBIND,
        ACTION,
        SCROLLBAR
    }

    private record Hit(HitType type, UiBounds bounds, Setting<?, ?> option, String value) {
    }

    private record AdminAction(String label, String optionId, String command) {
    }

    private static final class Flow {
        private final int x;
        private final int w;
        private int cursor;

        private Flow(int x, int y, int w) {
            this.x = x;
            this.cursor = y;
            this.w = Math.max(1, w);
        }

        private UiBounds take(int height) {
            int h = Math.max(0, height);
            UiBounds bounds = UiBounds.of(x, cursor, w, h);
            cursor += h + FLOW_GAP;
            return bounds;
        }

        private void gap(int gap) {
            cursor += Math.max(0, gap);
        }
    }

    private static final class FlowSizer {
        private int height;
        private boolean empty = true;

        private void add(int rowHeight) {
            if (rowHeight <= 0) return;
            if (!empty) height += FLOW_GAP;
            height += rowHeight;
            empty = false;
        }

        private int height() {
            return height;
        }
    }

    private static final class Editing {
        private final Setting<?, ?> option;
        private String text;
        private int cursor;
        private int selectionAnchor = -1;

        private Editing(Setting<?, ?> option, String text, int cursor) {
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
}
