package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;

public class AutismProxyConfigScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_WIDTH = 430;
    private static final int ROW_HEIGHT = 30;
    private static final int[] TIMEOUT_VALUES = {1000, 1500, 2000, 2500, 3000, 5000, 7500, 10000, 15000};
    private static final int[] THREAD_VALUES = {1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256};
    private static final int[] RETRY_VALUES = {0, 1, 2, 3, 5};
    private static final int[] PRUNE_LATENCY_VALUES = {0, 500, 1000, 1500, 2000, 3000, 5000, 10000};
    private static final int[] PRUNE_COUNT_VALUES = {0, 25, 50, 100, 250, 500, 1000};

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<CompactDropdown> dropdowns = new ArrayList<>();
    private final List<ConfigRow> rows = new ArrayList<>();
    private int scroll;
    private int layoutWidth;
    private int layoutHeight;

    public AutismProxyConfigScreen(Screen parent) {
        super(Component.literal("Proxy Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        buttons.clear();
        dropdowns.clear();
        rows.clear();
        int panelX = panelX();
        int panelW = panelW();
        int y = contentTop() - scroll;
        AutismProxyManager mgr = AutismProxyManager.get();

        addDropdownRow("Timeout", "How long each proxy check can take.", mgr.getTimeoutMs(), mgr::setTimeoutMs, TIMEOUT_VALUES, this::formatMs, panelX, panelW, y);
        y += ROW_HEIGHT;
        addDropdownRow("Threads", "How many proxies can be checked at once.", mgr.getThreads(), mgr::setThreads, THREAD_VALUES, Integer::toString, panelX, panelW, y);
        y += ROW_HEIGHT;
        addDropdownRow("Retries", "Extra attempts when a proxy check times out.", mgr.getRetries(), mgr::setRetries, RETRY_VALUES, Integer::toString, panelX, panelW, y);
        y += ROW_HEIGHT;
        addDropdownRow("Prune latency", "Cleanup removes alive proxies slower than this.", mgr.getPruneLatency(), mgr::setPruneLatency, PRUNE_LATENCY_VALUES, value -> value <= 0 ? "Off" : formatMs(value), panelX, panelW, y);
        y += ROW_HEIGHT;
        addDropdownRow("Prune limit", "Cleanup keeps only the fastest proxies when enabled.", mgr.getPruneToCount(), mgr::setPruneToCount, PRUNE_COUNT_VALUES, value -> value <= 0 ? "No limit" : Integer.toString(value), panelX, panelW, y);
        y += ROW_HEIGHT;
        addToggleRow("Sort by latency", mgr.isSortByLatency(), "Refresh/cancel sorts alive proxies from fastest to slowest.", value -> mgr.setSortByLatency(value), panelX, panelW, y);
        y += ROW_HEIGHT;
        addToggleRow("Prune dead", mgr.isPruneDead(), "Cleanup removes proxies marked as dead.", value -> mgr.setPruneDead(value), panelX, panelW, y);

        if (!compactLayout()) {
            buttons.add(CompactOverlayButton.create(panelX + 12, panelBottom() - 30, 92, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent)).setVariant(CompactOverlayButton.Variant.SECONDARY));
            buttons.add(CompactOverlayButton.create(panelX + panelW - 104, panelBottom() - 30, 92, 18, Component.literal("Defaults"), b -> resetDefaults()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        }
        layoutWidth = screenWidth();
        layoutHeight = screenHeight();
    }

    private void addDropdownRow(String label, String hint, int current, IntConsumer setter, int[] options, ValueFormatter formatter, int panelX, int panelW, int y) {
        rows.add(new ConfigRow(label, "", hint, y, false, false));
        if (!isRowVisible(y)) return;
        int controlW = controlWidth(panelW);
        dropdowns.add(new CompactDropdown(
            panelX + panelW - controlW - 14,
            y + 6,
            controlW,
            18,
            optionLabels(options, formatter),
            selectedOptionIndex(current, options),
            index -> {
                if (index >= 0 && index < options.length) {
                    setter.accept(options[index]);
                    rebuildRows();
                }
            }
        ));
    }

    private void addToggleRow(String label, boolean enabled, String hint, BooleanConsumer setter, int panelX, int panelW, int y) {
        rows.add(new ConfigRow(label, enabled ? "Enabled" : "Disabled", hint, y, true, enabled));
        if (!isRowVisible(y)) return;
        int controlW = controlWidth(panelW);
        buttons.add(CompactOverlayButton.create(panelX + panelW - controlW - 14, y + 6, controlW, 18, Component.literal(enabled ? "Enabled" : "Disabled"), b -> {
            setter.accept(!enabled);
            rebuildRows();
        }).setVariant(enabled ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER)
            .setToggleState(enabled)
            .setAnimationKey("proxy-config:" + label));
    }

    private void resetDefaults() {
        AutismProxyManager mgr = AutismProxyManager.get();
        mgr.setTimeoutMs(3000);
        mgr.setThreads(64);
        mgr.setRetries(0);
        mgr.setPruneLatency(2000);
        mgr.setPruneToCount(0);
        mgr.setSortByLatency(true);
        mgr.setPruneDead(true);
        rebuildRows();
    }

    private int selectedOptionIndex(int current, int[] options) {
        if (options.length == 0) return current;
        int index = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < options.length; i++) {
            int distance = Math.abs(options[i] - current);
            if (distance < bestDistance) {
                bestDistance = distance;
                index = i;
            }
        }
        return index;
    }

    private List<String> optionLabels(int[] options, ValueFormatter formatter) {
        List<String> labels = new ArrayList<>();
        for (int option : options) {
            labels.add(formatter.format(option));
        }
        return labels;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            if (layoutWidth != screenWidth() || layoutHeight != screenHeight()) rebuildRows();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            int panelX = panelX();
            int panelW = panelW();
            int panelTop = panelTop();
            UiBounds panelBounds = UiBounds.of(panelX, panelTop, panelW, panelBottom() - panelTop);
            CompactScreenPanel.render(UiContexts.overlay(graphics, font, virtualMouseX, virtualMouseY), panelBounds, 20,
                "Proxy Settings", panelBounds.contains(virtualMouseX, virtualMouseY));
            SectionPanel.renderBody(UiContexts.overlay(graphics, font, virtualMouseX, virtualMouseY),
                UiBounds.of(panelX + 10, panelTop + 28, Math.max(0, panelW - 20), Math.max(0, panelBottom() - panelTop - 64)), PANEL_BG_SOFT);

            try {
                boolean suppressUnderlyingPointer = CompactDropdown.shouldSuppressUnderlyingPointer();
                boolean dropdownMenuHovered = CompactDropdown.isInsideOpenMenu(dropdowns, virtualMouseX, virtualMouseY);
                int buttonMouseX = dropdownMenuHovered || suppressUnderlyingPointer ? Integer.MIN_VALUE : virtualMouseX;
                int buttonMouseY = dropdownMenuHovered || suppressUnderlyingPointer ? Integer.MIN_VALUE : virtualMouseY;
                if (compactLayout()) {
                    drawText(graphics, "Window too small.", panelX + 14, panelTop + 34, MUTED, false, Math.max(0, panelW - 28));
                    return;
                }
                for (ConfigRow row : rows) {
                    renderRow(graphics, row, panelX, panelW);
                }
                for (CompactOverlayButton button : buttons) {
                    CompactOverlayButton.renderStyled(graphics, this.font, button, buttonMouseX, buttonMouseY);
                }
                CompactDropdown.renderButtons(graphics, this.font, dropdowns, virtualMouseX, virtualMouseY);
            } finally {
            }
            if (CompactDropdown.isMenuOpen(dropdowns)) {
                try {
                    CompactDropdown.renderOpenMenu(graphics, this.font, dropdowns, virtualMouseX, virtualMouseY);
                } finally {
                }
            }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, ConfigRow row, int panelX, int panelW) {
        if (!isRowVisible(row.y())) return;
        int x = panelX + 12;
        int y = row.y();
        int w = panelW - 24;
        int fill = row.toggle() && row.enabled() ? 0x2210261A : 0x18111113;
        UiRenderer.rect(graphics, UiBounds.of(x, y, w, ROW_HEIGHT - 4), fill);
        if (row.toggle() && row.enabled()) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_HEIGHT - 4), SUCCESS);
        int textMaxWidth = Math.max(1, panelW - controlWidth(panelW) - 48);
        drawText(graphics, row.label(), x + 10, y + 5, TEXT, false, textMaxWidth);
        drawText(graphics, row.hint(), x + 10, y + 17, MUTED, false, textMaxWidth);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0 && CompactScreenPanel.isOverClose(
            UiBounds.of(panelX(), panelTop(), panelW(), panelBottom() - panelTop()), 20,
            (int) virtualEvent.x(), (int) virtualEvent.y())) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        if (CompactDropdown.mouseClicked(dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        if (virtualEvent.button() != 0) return super.mouseClicked(virtualEvent, doubleClick);
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        }
        return super.mouseClicked(virtualEvent, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double virtualX = AutismUiScale.toVirtual(mouseX);
        double virtualY = AutismUiScale.toVirtual(mouseY);
        if (CompactDropdown.mouseScrolled(dropdowns, virtualX, virtualY, scrollY)) return true;
        if (virtualX < panelX() || virtualX > panelX() + panelW() || virtualY < contentTop() || virtualY > contentBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY < 0 ? ROW_HEIGHT : -ROW_HEIGHT)));
        rebuildRows();
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelW(), 8);
    }

    private int panelW() {
        return DirectLayout.fitPanelDimension(screenWidth(), 8, PANEL_WIDTH);
    }

    private int panelTop() {
        return DirectLayout.centerPanel(screenHeight(), panelH(), 8);
    }

    private int panelH() {
        return DirectLayout.fitPanelDimension(screenHeight(), 8, 286);
    }

    private int panelBottom() {
        return panelTop() + panelH();
    }

    private int contentTop() {
        return panelTop() + 36;
    }

    private int contentBottom() {
        return panelBottom() - 40;
    }

    private int maxScroll() {
        return Math.max(0, ROW_HEIGHT * 7 - Math.max(0, contentBottom() - contentTop()));
    }

    private boolean isRowVisible(int y) {
        return y + ROW_HEIGHT > contentTop() && y < contentBottom();
    }

    private boolean compactLayout() {
        return panelW() < 220 || panelH() < 130;
    }

    private int controlWidth(int panelW) {
        return Math.max(72, Math.min(118, panelW / 3));
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean right) {
        drawText(graphics, text, x, y, color, right, Integer.MAX_VALUE);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean right, int maxWidth) {
        Font renderer = this.font;
        Identifier font = THEME.fontFor(UiTone.BODY);
        if (maxWidth != Integer.MAX_VALUE && !right) {
            UiText.drawFitted(graphics, renderer, text, font, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        String display = maxWidth == Integer.MAX_VALUE ? text : UiText.trimToWidth(renderer, text, maxWidth, font, color);
        int w = UiText.width(renderer, display, font, color);
        int drawX = right ? x - w : x;
        UiText.draw(graphics, renderer, display, font, color, drawX, y, false);
    }

    private String formatMs(int value) {
        if (value % 1000 == 0) return (value / 1000) + "s";
        return (value / 1000.0D) + "s";
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    @FunctionalInterface
    private interface ValueFormatter {
        String format(int value);
    }

    private record ConfigRow(String label, String value, String hint, int y, boolean toggle, boolean enabled) {
    }
}
