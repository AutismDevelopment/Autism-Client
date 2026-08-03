package autismclient.util;

import autismclient.gui.vanillaui.direct.DirectUiLabel;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectCompactRow;
import autismclient.gui.vanillaui.direct.DirectFormRow;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.CompactFieldFactory;
import autismclient.gui.vanillaui.components.Dropdown;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.vanillaui.components.CompactKeybindButton;
import autismclient.gui.vanillaui.components.ConnectedButton;
import autismclient.gui.vanillaui.components.Tooltip;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.modules.AutismModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class AutismKeybindOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final String WINDOW_TITLE = "Settings";

    private static final String INSIDE_GUI_TOOLTIP = "Fire keybinds while a container screen is open. Blocked in chat/sign/book editors and focused text fields.";
    private static final String CUSTOM_MENU_TOOLTIP = "Use our custom main menu. Turn off for the normal vanilla/Fabric title screen (mods stay visible).";
    private static final int MIN_PANEL_WIDTH = 170;
    private static final int ROW_HEIGHT = 16;
    private static final int PAD = 3;
    private static final int HEADER_CONTROL = 10;
    private static final int ROW_LABEL_GAP = 3;
    private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;

    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow(WINDOW_TITLE);
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final List<KeybindEntry> entries = new ArrayList<>();
    private final List<KeybindRowNode> rowNodes = new ArrayList<>();

    private boolean dragging = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private float pressStartUiX;
    private float pressStartUiY;
    private int pressStartPanelX;
    private int pressStartPanelY;
    private boolean dragMoved = false;
    private int capturingIndex = -1;
    private String hoveredTooltip = null;
    private float tooltipUiX;
    private float tooltipUiY;
    private int cachedLabelColumnWidth = -1;
    private int cachedPanelWidth = -1;
    private Dropdown prefixDropdown;

    public AutismKeybindOverlay() {
        super("autism-keybinds", MIN_PANEL_WIDTH, 142);
        this.panelX = 160;
        this.panelY = 46;
        entries.add(new KeybindEntry("Module Menu", "Opens the Autism module click GUI", () -> getConfig().keybindModuleMenu, v -> getConfig().keybindModuleMenu = v));
        entries.add(new KeybindEntry("Load GUI", "Restores the last saved GUI screen and handler", () -> getConfig().keybindLoadGui, v -> getConfig().keybindLoadGui = v));
        entries.add(new KeybindEntry("Flush Queue", "Sends all queued packets to the server", () -> getConfig().keybindFlushQueue, v -> getConfig().keybindFlushQueue = v));
        entries.add(new KeybindEntry("Clear Queue", "Discards all queued packets", () -> getConfig().keybindClearQueue, v -> getConfig().keybindClearQueue = v));
        entries.add(new KeybindEntry("Toggle Logger", "Shows or hides the packet logger overlay", () -> getConfig().keybindToggleLogger, v -> getConfig().keybindToggleLogger = v));
        entries.add(new KeybindEntry("Toggle Send", "Turns packet sending on or off", () -> getConfig().keybindToggleSend, v -> getConfig().keybindToggleSend = v));
        entries.add(new KeybindEntry("Toggle Delay", "Turns packet delay on or off", () -> getConfig().keybindToggleDelay, v -> getConfig().keybindToggleDelay = v));

        buildUi();
    }

    private void buildUi() {
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(panelPadding() + 2, panelPadding() + headerControlSize() * 2 + 8);
        windowNode.content().clearChildren();
        windowNode.content().setGap(windowContentGap()).setPadding(DirectUiInsets.all(panelPadding()));

        windowNode.content().add(new ToggleRowNode(
            "Inside GUI",
            INSIDE_GUI_TOOLTIP,
            () -> getConfig().keybindInsideGui,
            v -> getConfig().keybindInsideGui = v
        ));

        windowNode.content().add(new ToggleRowNode(
            "Custom Menu",
            CUSTOM_MENU_TOOLTIP,
            () -> getConfig().customMainMenu,
            v -> {
                AutismConfig config = getConfig();
                config.customMainMenu = v;
                config.save();
            }
        ));

        windowNode.content().add(new ScaleRowNode());

        windowNode.content().add(new ToggleRowNode(
            "Auto Probe",
            "Auto-Scans on join.",
            () -> getConfig().autoProbePlugins,
            v -> {
                AutismConfig config = getConfig();
                config.autoProbePlugins = v;
                config.save();
            }
        ));

        windowNode.content().add(new ToggleRowNode(
            "InfiniChat",
            "Remove chat length limit.",
            () -> getConfig().infiniChat,
            v -> {
                AutismConfig config = getConfig();
                config.infiniChat = v;
                config.save();
            }
        ));

        windowNode.content().add(new ToggleRowNode(
            "Stop On Leave",
            "Stop macros on disconnect.",
            () -> getConfig().stopMacroOnLeave,
            v -> {
                AutismConfig config = getConfig();
                config.stopMacroOnLeave = v;
                config.save();
            }
        ));

        windowNode.content().add(new PrefixRowNode());

        rowNodes.clear();
        for (int i = 0; i < entries.size(); i++) {
            KeybindRowNode row = new KeybindRowNode(i, entries.get(i));
            rowNodes.add(row);
            windowNode.content().add(row);
        }

        DirectCompactRow settingsPages = new DirectCompactRow()
            .setGap(0)
            .setPadding(DirectUiInsets.NONE)
            .setUnderlineOnHover(false);
        settingsPages.setGrowX(true);
        settingsPages.add(new DirectUiButton("Theme Color", DirectUiButton.Variant.PRIMARY, this::openThemeColor)
            .setGrowX(true)
            .setConnectedStyle(true)
            .setConnectedEdges(ConnectedButton.LEFT_CELL)
            .setButtonHeight(chooserButtonHeight()));
        settingsPages.add(new DirectUiButton("Profiles", DirectUiButton.Variant.PRIMARY, this::openProfiles)
            .setGrowX(true)
            .setConnectedStyle(true)
            .setConnectedEdges(ConnectedButton.RIGHT_CELL)
            .setButtonHeight(chooserButtonHeight()));
        windowNode.content().add(settingsPages);
    }

    private void openThemeColor() {
        MC.gui.setScreen(new autismclient.gui.screen.AutismThemeColorScreen(MC.gui.screen()));
    }

    private void openProfiles() {
        AutismModule module = AutismModule.get();
        AutismProfilesOverlay profiles = module == null ? null : module.getProfilesOverlay();
        if (profiles == null) return;
        profiles.setMainMenuMode(false);
        profiles.setVisible(true);
        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.register(profiles, OverlayScope.BACKGROUND_STATUS);
        manager.bringToFront(profiles);
    }

    private AutismConfig getConfig() {
        return AutismConfig.getGlobal();
    }

    @Override
    public int getMinWidth() {
        return computePanelWidth();
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + bodyMinimumHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        AutismWindowLayout clamped = clampToViewport(bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
        windowNode.syncShowBody(!collapsed);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        hoveredTooltip = null;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        panelWidth = computePanelWidth();
        boolean active = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
        DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);

        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);
        panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        panelHeight = Math.max(theme.headerHeight(), panelHeight);
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        if (!collapsed && prefixDropdown != null && prefixDropdown.isOpen()) {
            context.nextStratum();
            viewport.push(context);
            try {
                prefixDropdown.render(UiContexts.overlay(context, MC.font, Math.round(uiMouseX), Math.round(uiMouseY)));
            } finally {
                viewport.pop(context);
            }
        }
        if (!collapsed && hoveredTooltip != null) {
            context.nextStratum();
            renderTooltip(context, viewport, hoveredTooltip, tooltipUiX, tooltipUiY);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor context, DirectViewport viewport, String text, float uiX, float uiY) {
        viewport.push(context);
        try {
            Tooltip.render(UiContexts.overlay(context, MC.font, Math.round(uiX), Math.round(uiY)),
                text, Math.round(uiX), Math.round(uiY), tooltipMaxWidth());
        } finally {
            viewport.pop(context);
        }
    }

    public static String getKeyName(int keyCode) {
        return AutismBindUtil.getBindName(keyCode);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            UiInputResult result = prefixDropdown.mouseClicked(Math.round(uiMouseX), Math.round(uiMouseY), button);
            if (!prefixDropdown.isOpen()) prefixDropdown = null;
            return result == UiInputResult.HANDLED;
        }

        if (isOverCloseButton(uiMouseX, uiMouseY)) {
            visible = false;
            capturingIndex = -1;
            dragging = false;
            dragMoved = false;
            return true;
        }

        if (button == 0 && isOverCollapseButton(uiMouseX, uiMouseY)) {
            setCollapsed(!collapsed);
            return true;
        }

        if (button == 0 && isOverDragBarUi(uiMouseX, uiMouseY)) {
            dragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (collapsed) return isMouseOver(mouseX, mouseY);

        if (capturingIndex >= 0 && AutismBindUtil.isAllowedMouseButton(button)) {
            captureBind(AutismBindUtil.encodeMouseButton(button));
            return true;
        }

        if (surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || collapsed) return false;
        if (prefixDropdown != null && prefixDropdown.isOpen() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            prefixDropdown.close();
            prefixDropdown = null;
            return true;
        }
        if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!visible || capturingIndex < 0) return false;

        captureBind(CompactKeybindButton.keyOrClear(keyCode));
        return true;
    }

    private void captureBind(int bindCode) {
        for (int i = 0; i < entries.size(); i++) {
            if (i != capturingIndex && entries.get(i).getter.getAsInt() == bindCode) {
                entries.get(i).setter.accept(-1);
            }
        }

        entries.get(capturingIndex).setter.accept(bindCode);
        getConfig().save();
        capturingIndex = -1;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            DirectViewport viewport = surface.viewport();
            return prefixDropdown.mouseReleased(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), button) == UiInputResult.HANDLED;
        }
        if (dragging) {
            dragging = false;
            saveLayout();
            return true;
        }
        if (collapsed) return false;
        return surface.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            DirectViewport viewport = surface.viewport();
            return prefixDropdown.mouseDragged(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), button, deltaX, deltaY) == UiInputResult.HANDLED;
        }
        if (dragging) {
            DirectViewport viewport = surface.viewport();
            float uiMouseX = viewport.toUiX(mouseX);
            float uiMouseY = viewport.toUiY(mouseY);
            AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(
                Math.round(uiMouseX - dragOffsetX),
                Math.round(uiMouseY - dragOffsetY),
                panelWidth,
                panelHeight,
                visible,
                collapsed
            ));
            panelX = clamped.x;
            panelY = clamped.y;
            dragMoved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            return true;
        }
        if (collapsed) return false;
        return surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (collapsed) return false;
        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            DirectViewport viewport = surface.viewport();
            return prefixDropdown.mouseScrolled(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), amount) == UiInputResult.HANDLED;
        }
        return surface.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || collapsed) return false;
        return surface.charTyped(chr, modifiers);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean v) {
        visible = v;
        if (!v) {
            capturingIndex = -1;
            dragging = false;
            dragMoved = false;
            closePrefixDropdown();
        } else {
            windowNode.syncShowBody(!collapsed);
            AutismOverlayManager.get().bringToFront(this);
        }
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return uiX >= panelX && uiX < panelX + panelWidth
            && uiY >= panelY && uiY < panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        return isOverDragBarUi(viewport.toUiX(mouseX), viewport.toUiY(mouseY));
    }

    private boolean isOverDragBarUi(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverDragArea(currentWindowBounds(), theme.headerHeight(), true, true, uiMouseX, uiMouseY);
    }

    private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverHeader(currentWindowBounds(), theme.headerHeight(), uiMouseX, uiMouseY);
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public void setCollapsed(boolean c) {
        if (collapsed == c) return;
        collapsed = c;
        windowNode.syncShowBody(!collapsed);
        if (c) {
            capturingIndex = -1;
            closePrefixDropdown();
            clearHiddenInteractionState();
        }
        saveLayout();
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public boolean wantsKeyboardCapture() {
        return visible && capturingIndex >= 0;
    }

    @Override
    public void clearTextFieldFocus() {
        closePrefixDropdown();
        surface.clearFocusedTextInputs();
    }

    private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverClose(currentWindowBounds(), theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private boolean isOverCollapseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverCollapse(currentWindowBounds(), theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private UiBounds currentWindowBounds() {
        return UiBounds.of(panelX, panelY, panelWidth, Math.max(theme.headerHeight(), panelHeight));
    }

    private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int margin = 4;
        int viewportW = Math.round(viewport.uiWidth());
        int viewportH = Math.round(viewport.uiHeight());
        int availableW = Math.max(1, viewportW - margin * 2);
        int availableH = Math.max(theme.headerHeight(), viewportH - margin * 2);
        int width = Math.max(Math.min(getMinWidth(), availableW), Math.min(bounds.width, availableW));
        int minHeight = bounds.collapsed ? theme.headerHeight() : getMinHeight();
        int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
        int renderedHeight = bounds.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
        return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private int computeLabelColumnWidth() {
        if (MC.font == null) return labelColumnFallbackWidth();
        if (cachedLabelColumnWidth > 0) return cachedLabelColumnWidth;
        List<String> labels = new ArrayList<>(entries.size() + 3);
        labels.add("Inside GUI");
        labels.add("Custom Menu");
        labels.add("Overlay Scale");
        labels.add("Auto Probe");
        labels.add("Command Prefix");
        for (KeybindEntry entry : entries) labels.add(entry.label);
        cachedLabelColumnWidth = Math.max(labelColumnMinimumWidth(), UiSizing.measureWidestText(MC.font, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY), labels) + labelColumnExtraWidth());
        return cachedLabelColumnWidth;
    }

    private int computeBodyWidth() {
        if (MC.font == null) return MIN_PANEL_WIDTH - (PAD * 2);
        int keybindWidth = computeLabelColumnWidth() + rowLabelGap() + bindButtonWidth();
        return keybindWidth;
    }

    private int computePanelWidth() {
        if (MC.font == null) return MIN_PANEL_WIDTH;
        if (cachedPanelWidth > 0) return cachedPanelWidth;
        int headerReserve = headerControlSize() * 2 + (panelPadding() * 2) + 14;
        int titleWidth = UiText.width(MC.font, WINDOW_TITLE, theme.fontFor(UiTone.TITLE), theme.color(UiTone.TITLE));
        cachedPanelWidth = Math.max(panelMinimumWidth(), Math.max(computeBodyWidth() + (panelPadding() * 2), titleWidth + headerReserve + panelPadding()));
        return cachedPanelWidth;
    }

    private final class ToggleRowNode extends DirectFormRow {
        private final java.util.function.BooleanSupplier getter;
        private final java.util.function.Consumer<Boolean> setter;
        private final String tooltip;
        private final DirectUiLabel labelNode;
        private final DirectUiButton toggleButton;

        private ToggleRowNode(String label, String tooltip,
                              java.util.function.BooleanSupplier getter,
                              java.util.function.Consumer<Boolean> setter) {
            super(
                new DirectUiLabel(label, UiTone.BODY).setTrimToBounds(true),
                new DirectUiButton("OFF", DirectUiButton.Variant.SECONDARY, null)
                    .setConnectedToggle(true)
                    .setPreferredWidth(bindButtonWidth())
                    .setMinWidth(bindButtonWidth())
                    .setMaxWidth(bindButtonWidth())
                    .setHorizontalPadding(bindButtonPadding())
                    .setButtonHeight(chooserButtonHeight())
                    .setConnectedEdges(autismclient.gui.vanillaui.components.ConnectedButton.FULL)
                    .setTextYOffset(0)
            );
            this.getter = getter;
            this.setter = setter;
            this.tooltip = tooltip;
            this.labelNode = (DirectUiLabel) labelNode();
            this.toggleButton = (DirectUiButton) controlNode();
            this.toggleButton.setOnPress(() -> {
                setter.accept(!getter.getAsBoolean());
                getConfig().save();
            });
            this.height = ROW_HEIGHT;
            this.growX = true;
            setLabelWidth(computeLabelColumnWidth());
            setGap(rowLabelGap());
            setAlignControlEnd(true);
            setPadding(DirectUiInsets.NONE);
        }

        @Override
        public float preferredWidth(DirectRenderContext context) {
            return panelWidth - theme.scale(PAD * 2);
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(DirectRenderContext context) {
            boolean on = getter.getAsBoolean();

            labelNode.render(context);
            autismclient.gui.vanillaui.UiContext ui = autismclient.gui.vanillaui.UiContexts.overlay(
                context.drawContext(), context.textRenderer(),
                Math.round(context.mouseX()), Math.round(context.mouseY()));
            autismclient.gui.vanillaui.UiBounds pillBounds = autismclient.gui.vanillaui.UiBounds.of(
                Math.round(toggleButton.x()), Math.round(toggleButton.y()),
                Math.round(toggleButton.width()), Math.round(toggleButton.height()));
            autismclient.gui.vanillaui.components.Toggle.render(ui, pillBounds, on,
                pillBounds.contains(ui.mouseX(), ui.mouseY()), "keybind:" + tooltip);
            if (labelNode.contains(context.mouseX(), context.mouseY())) {
                hoveredTooltip = tooltip;
                tooltipUiX = context.mouseX() + tooltipOffsetX();
                tooltipUiY = context.mouseY() - tooltipOffsetY();
            }
        }
    }

    private final class KeybindRowNode extends DirectFormRow {
        private final int index;
        private final KeybindEntry entry;
        private final DirectUiLabel labelNode;
        private KeybindRowNode(int index, KeybindEntry entry) {
            super(new DirectUiLabel(entry.label, UiTone.BODY).setTrimToBounds(true),
                new KeybindButtonNode(entry.getter, () -> capturingIndex == index, () -> {
                    capturingIndex = capturingIndex == index ? -1 : index;
                }));
            this.index = index;
            this.entry = entry;
            this.height = ROW_HEIGHT;
            this.growX = true;
            this.labelNode = (DirectUiLabel) labelNode();
            setLabelWidth(computeLabelColumnWidth());
            setGap(rowLabelGap());
            setAlignControlEnd(true);
            setPadding(DirectUiInsets.NONE);
        }

        @Override
        public float preferredWidth(DirectRenderContext context) {
            return panelWidth - (panelPadding() * 2);
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(DirectRenderContext context) {
            labelNode.setTone(UiTone.BODY);

            super.render(context);

            if (labelNode.contains(context.mouseX(), context.mouseY())) {
                hoveredTooltip = entry.tooltip;
                tooltipUiX = context.mouseX() + tooltipOffsetX();
                tooltipUiY = context.mouseY() - tooltipOffsetY();
            }
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            return super.mouseClicked(context, mouseX, mouseY, button);
        }

    }

    private static final class KeybindButtonNode extends DirectUiNode {
        private final java.util.function.IntSupplier bindCode;
        private final java.util.function.BooleanSupplier capturing;
        private final Runnable onPress;

        private KeybindButtonNode(java.util.function.IntSupplier bindCode, java.util.function.BooleanSupplier capturing, Runnable onPress) {
            this.bindCode = bindCode;
            this.capturing = capturing;
            this.onPress = onPress;
            this.width = CompactKeybindButton.WIDTH;
            this.height = CompactKeybindButton.HEIGHT;
        }

        @Override
        public float preferredWidth(DirectRenderContext context) {
            return CompactKeybindButton.WIDTH;
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return CompactKeybindButton.HEIGHT;
        }

        @Override
        public void render(DirectRenderContext context) {
            if (!visible) return;
            UiBounds bounds = UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
            boolean hovered = enabled && contains(context.mouseX(), context.mouseY());
            CompactKeybindButton.render(
                UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
                bounds,
                bindCode.getAsInt(),
                capturing.getAsBoolean(),
                hovered
            );
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if (!enabled || !contains(mouseX, mouseY) || button != 0) return false;
            if (onPress != null) onPress.run();
            return true;
        }
    }

    private final class ScaleRowNode extends DirectFormRow {
        private final DirectUiLabel labelNode;
        private final DirectUiButton scaleButton;
        private final String tooltip = "Scales every Autism overlay/window. Left-click increases, right-click decreases. 1x is default; 1.5x/2x are for 4K/high-DPI screens.";

        private ScaleRowNode() {
            super(
                new DirectUiLabel("Overlay Scale", UiTone.BODY).setTrimToBounds(true),
                new DirectUiButton("1x", DirectUiButton.Variant.SECONDARY, null)
                    .setPreferredWidth(bindButtonWidth())
                    .setMinWidth(bindButtonWidth())
                    .setMaxWidth(bindButtonWidth())
                    .setHorizontalPadding(bindButtonPadding())
                    .setButtonHeight(chooserButtonHeight())
                    .setConnectedEdges(autismclient.gui.vanillaui.components.ConnectedButton.FULL)
                    .setTextYOffset(0)
            );
            this.labelNode = (DirectUiLabel) labelNode();
            this.scaleButton = (DirectUiButton) controlNode();
            this.scaleButton.setOnPress(() -> {
                AutismUiScale.setOverlayScaleMultiplier(AutismUiScale.nextOverlayScaleMultiplier());
                surface.invalidateLayout();
            });
            this.height = ROW_HEIGHT;
            this.growX = true;
            setLabelWidth(computeLabelColumnWidth());
            setGap(rowLabelGap());
            setAlignControlEnd(true);
            setPadding(DirectUiInsets.NONE);
        }

        @Override
        public float preferredWidth(DirectRenderContext context) {
            return panelWidth - (panelPadding() * 2);
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(DirectRenderContext context) {
            double scale = AutismUiScale.getOverlayScaleMultiplier();
            scaleButton.setText(AutismUiScale.formatOverlayScale(scale));
            scaleButton.setVariant(scale > 1.0 ? DirectUiButton.Variant.PRIMARY : DirectUiButton.Variant.SECONDARY);
            super.render(context);
            if (labelNode.contains(context.mouseX(), context.mouseY())) {
                hoveredTooltip = tooltip;
                tooltipUiX = context.mouseX() + tooltipOffsetX();
                tooltipUiY = context.mouseY() - tooltipOffsetY();
            }
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if ((button == 0 || button == 1) && scaleButton.contains(mouseX, mouseY)) {
                AutismUiScale.setOverlayScaleMultiplier(button == 1
                    ? AutismUiScale.previousOverlayScaleMultiplier()
                    : AutismUiScale.nextOverlayScaleMultiplier());
                surface.invalidateLayout();
                return true;
            }
            return super.mouseClicked(context, mouseX, mouseY, button);
        }
    }

    private final class PrefixRowNode extends DirectUiNode {
        private UiBounds controlBounds = UiBounds.of(0, 0, 1, 1);

        private PrefixRowNode() {
            this.height = ROW_HEIGHT;
            this.growX = true;
        }

        @Override
        public float preferredWidth(DirectRenderContext context) {
            return panelWidth - (panelPadding() * 2);
        }

        @Override
        public float preferredHeight(DirectRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(DirectRenderContext context) {
            int rowX = Math.round(x);
            int rowY = Math.round(y);
            int rowW = Math.round(width);
            int rowH = Math.round(height);
            int controlW = bindButtonWidth();
            controlBounds = UiBounds.of(rowX + Math.max(0, rowW - controlW), rowY + 1, controlW, Math.max(12, rowH - 2));
            UiText.drawFitted(context.drawContext(), context.textRenderer(), "Command Prefix",
                theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY), rowX,
                rowY + Math.max(0, (rowH - UiText.fontHeight(theme.fontFor(UiTone.BODY))) / 2),
                Math.max(1, rowW - controlW - rowLabelGap()), false);
            String value = AutismCompatManager.effectiveCommandPrefix();

            CompactFieldFactory.dropdown(
                UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
                controlBounds, value, controlBounds.contains(Math.round(context.mouseX()), Math.round(context.mouseY())),
                prefixDropdown != null && prefixDropdown.isOpen()
            );
        }

        @Override
        public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
            if (button != 0 || !controlBounds.contains(Math.round(mouseX), Math.round(mouseY))) return false;
            prefixDropdown = new Dropdown(controlBounds, AutismCompatManager.COMMAND_PREFIX_CHOICES,
                AutismCompatManager.effectiveCommandPrefix(), AutismKeybindOverlay.this::selectCommandPrefix);
            prefixDropdown.open();
            return true;
        }
    }

    private void selectCommandPrefix(String requested) {
        String selected = AutismCompatManager.normalizeStoredCommandPrefix(requested);
        AutismModule module = AutismModule.get();
        if (module != null) module.setCommandPrefix(selected);
        else {
            getConfig().commandPrefix = selected;
            getConfig().save();
        }
        if (!selected.equals(requested)) {
            AutismNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
        }
    }

    private void closePrefixDropdown() {
        if (prefixDropdown != null) prefixDropdown.close();
        prefixDropdown = null;
    }

    private static class KeybindEntry {
        final String label;
        final String tooltip;
        final java.util.function.IntSupplier getter;
        final java.util.function.IntConsumer setter;

        KeybindEntry(String label, String tooltip, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
            this.label = label;
            this.tooltip = tooltip;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private int panelMinimumWidth() {
        return 170;
    }

    private int panelPadding() {
        return 3;
    }

    private int windowContentGap() {
        return 0;
    }

    private int bodyMinimumHeight() {
        return 20;
    }

    private int headerControlSize() {
        return HEADER_CONTROL;
    }

    private int headerControlInset() {
        return 2;
    }

    private int tooltipMaxWidth() {
        return 170;
    }

    private int tooltipPadding() {
        return 3;
    }

    private int tooltipOffsetX() {
        return 8;
    }

    private int tooltipOffsetY() {
        return 2;
    }

    private int labelColumnFallbackWidth() {
        return 82;
    }

    private int labelColumnMinimumWidth() {
        return 68;
    }

    private int labelColumnExtraWidth() {
        return 0;
    }

    private int rowLabelGap() {
        return ROW_LABEL_GAP;
    }

    private int bindButtonWidth() {
        return CompactKeybindButton.WIDTH;
    }

    private int bindButtonPadding() {
        return 2;
    }

    private int chooserButtonHeight() {
        return 14;
    }

    private int rowHeight() {
        return 16;
    }
}
