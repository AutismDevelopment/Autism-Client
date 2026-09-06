package autismclient.gui.screen;

import autismclient.addons.AddonManager;
import autismclient.addons.AddonManager.AddonLoadStatus;
import autismclient.addons.AddonManager.AddonReport;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static autismclient.gui.screen.AutismScreenPalette.*;

public final class AutismAddonsScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int INFO = 0xFF8EA0FF;
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_MARGIN = 12;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_HEIGHT = 64;
    private static final int BODY_TOP = 92;
    private static final int BODY_BOTTOM_MARGIN = 12;
    private static final int LEFT_WIDTH = 278;
    private static final int GAP = 8;
    private static final int ROW_HEIGHT = 31;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_GUTTER = 10;

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final ScrollState listScroll = new ScrollState();
    private EditBox searchField;
    private String searchQuery = "";
    private String pendingSearchQuery = "";
    private boolean searchDirty;
    private String selectedAddonId = "";
    private int scrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private List<AddonReport> reportsSnapshot = List.of();
    private String cachedFilterQuery = "";
    private List<AddonReport> cachedFilteredReports = List.of();

    public AutismAddonsScreen(Screen parent) {
        super(Component.literal("Addons"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reportsSnapshot = List.copyOf(AddonManager.reports());
        cachedFilterQuery = "\u0000";
        cachedFilteredReports = List.of();
        this.searchField = new EditBox(this.font, listX() + 12, BODY_TOP + 26, LEFT_WIDTH - 24, 16, Component.literal("Search addons"));
        this.searchField.setHint(Component.literal("Search addons..."));
        this.searchField.setMaxLength(96);
        this.searchField.setResponder(value -> {
            pendingSearchQuery = safeTrim(value);
            searchDirty = true;
        });
        this.addRenderableWidget(searchField);
        ensureSelection();
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttons.clear();

        int backW = 70;
        int backX = panelX() + panelWidth() - 22 - backW;
        buttons.add(CompactOverlayButton.create(backX, TOP_PANEL_Y + 12, backW, 18, Component.literal("Back"),
            b -> this.minecraft.gui.setScreen(parent)).setVariant(CompactOverlayButton.Variant.SECONDARY));

        AddonReport selected = selectedReport();
        int pad = 12;
        int gap = 8;
        int btnLeft = detailX() + pad;
        int areaW = Math.max(80, detailWidth() - pad * 2);
        int halfW = (areaW - gap) / 2;
        int rightW = areaW - halfW - gap;
        int rightX = btnLeft + halfW + gap;
        int row2Y = bodyBottom() - 26;
        int row1Y = row2Y - 24;

        buttons.add(CompactOverlayButton.create(btnLeft, row1Y, halfW, 18, Component.literal("Copy ID"),
            b -> copyId()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(rightX, row1Y, rightW, 18, Component.literal("Copy Report"),
            b -> copyReport()).setVariant(CompactOverlayButton.Variant.SECONDARY));

        boolean disabledRestart = selected != null && AddonManager.isDisabledOnRestart(selected.modId());
        CompactOverlayButton toggle = CompactOverlayButton.create(btnLeft, row2Y, halfW, 18,
            Component.literal(disabledRestart ? "Enable Restart" : "Disable Restart"),
            b -> toggleRestartDisabled());
        toggle.setVariant(disabledRestart ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER);
        toggle.active = selected != null;
        buttons.add(toggle);
        buttons.add(CompactOverlayButton.create(rightX, row2Y, rightW, 18, Component.literal("Mods Folder"),
            b -> openModsFolder()).setVariant(CompactOverlayButton.Variant.PRIMARY));
    }

    private void ensureSelection() {
        List<AddonReport> reports = filteredReports();
        if (reports.isEmpty()) {
            selectedAddonId = "";
            return;
        }
        for (AddonReport report : reports) {
            if (report.modId().equals(selectedAddonId)) return;
        }
        selectedAddonId = reports.get(0).modId();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchDirty) {
            searchDirty = false;
            searchQuery = pendingSearchQuery;
            scrollOffset = 0;
            listScroll.jumpTo(0, 0);
            ensureSelection();
            rebuildButtons();
        }
        listScroll.tick(1.0F, 1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            drawPanel(graphics, panelX() + 10, TOP_PANEL_Y, panelWidth() - 20, TOP_PANEL_HEIGHT, PANEL_BG);
            drawPanel(graphics, listX(), BODY_TOP, LEFT_WIDTH, bodyHeight(), PANEL_BG_SOFT);
            drawPanel(graphics, detailX(), BODY_TOP, detailWidth(), bodyHeight(), PANEL_BG_SOFT);

            drawText(graphics, "Addons", panelX() + 22, TOP_PANEL_Y + 10, TEXT, false);
            drawText(graphics, summaryText(), panelX() + 22, TOP_PANEL_Y + 28, MUTED, false, panelWidth() - 44);
            drawText(graphics, "Addon Library", listX() + 12, BODY_TOP + 10, TEXT, false);

            renderRows(graphics, mx, my);
            renderDetails(graphics);
            for (CompactOverlayButton button : buttons) {
                CompactOverlayButton.renderStyled(graphics, this.font, button, mx, my);
            }
            CompactScrollbar.Metrics scrollbar = scrollbarMetrics(filteredReports().size());
            CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mx, my), scrollbarDragging);
            super.extractRenderState(graphics, mx, my, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<AddonReport> reports = filteredReports();
        int maxScroll = maxScroll(reports.size());
        scrollOffset = quantizeScrollOffset(scrollOffset, ROW_HEIGHT, maxScroll);
        listScroll.jumpTo(scrollOffset, maxScroll);
        int first = scrollOffset / ROW_HEIGHT;
        int y = rowsTop() - (scrollOffset % ROW_HEIGHT);
        for (int i = first; i < reports.size() && y + ROW_HEIGHT - 3 <= rowsBottom(); i++) {
            if (y + ROW_HEIGHT - 3 > rowsTop()) renderRow(graphics, reports.get(i), y, mouseX, mouseY);
            y += ROW_HEIGHT;
        }
        if (reports.isEmpty()) {
            drawText(graphics, reportsSnapshot.isEmpty() ? "No AUTISM addons discovered." : "No addons match the search.",
                listX() + 12, rowsTop() + 12, MUTED, false, LEFT_WIDTH - 24);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, AddonReport report, int y, int mouseX, int mouseY) {
        boolean selected = report.modId().equals(selectedAddonId);
        boolean hovered = mouseX >= rowX() && mouseX < rowRight() && mouseY >= y && mouseY < y + ROW_HEIGHT - 3;
        int fill = selected ? 0x3324D86A : hovered ? 0x242B1A1D : 0x18111113;
        int status = selected ? BORDER_ACTIVE : statusColor(report);
        UiRenderer.rect(graphics, UiBounds.of(rowX(), y, rowWidth(), ROW_HEIGHT - 3), fill);
        UiRenderer.rect(graphics, UiBounds.of(rowX(), y, 2, ROW_HEIGHT - 3), status);
        UiRenderer.rect(graphics, UiBounds.of(rowX() + 4, y + 5, 3, ROW_HEIGHT - 13), report.color());
        drawText(graphics, report.name(), rowX() + 12, y + 4, TEXT, false, rowWidth() - 92);
        drawText(graphics, report.modId() + (report.version().isBlank() ? "" : "  v" + report.version()),
            rowX() + 12, y + 16, MUTED, false, rowWidth() - 92);
        drawText(graphics, displayStatus(report), rowRight() - 78, y + 10, statusColor(report), false, 70);
    }

    private void renderDetails(GuiGraphicsExtractor graphics) {
        AddonReport report = selectedReport();
        int x = detailX() + 12;
        int y = BODY_TOP + 10;
        int width = detailWidth() - 24;
        drawText(graphics, "Details", x, y, TEXT, false, width);
        y += 18;
        if (report == null) {
            drawText(graphics, "Select an addon to inspect its status.", x, y, MUTED, false, width);
            return;
        }
        drawText(graphics, report.name(), x, y, TEXT, false, width);
        y += 12;
        drawText(graphics, report.modId(), x, y, MUTED, false, width);
        y += 16;
        y = detailLine(graphics, x, y, width, "Status", displayStatus(report), statusColor(report));
        y = detailLine(graphics, x, y, width, "API", apiText(report), INFO);
        y = detailLine(graphics, x, y, width, "Version", report.version().isBlank() ? "unknown" : report.version(), TEXT);
        y = detailLine(graphics, x, y, width, "Authors", report.authors().isBlank() ? "unknown" : report.authors(), TEXT);
        y = detailLine(graphics, x, y, width, "Extensions", report.summaryCounts(), SUCCESS);
        if (report.rejectedTotal() > 0) y = detailLine(graphics, x, y, width, "Rejected", Integer.toString(report.rejectedTotal()), WARN);
        if (report.runtimeErrors() > 0) y = detailLine(graphics, x, y, width, "Runtime Errors", Integer.toString(report.runtimeErrors()), ERROR);
        if (AddonManager.isDisabledOnRestart(report.modId())) {
            y += 4;
            drawText(graphics, "Will be disabled after restart.", x, y, WARN, false, width);
            y += 12;
        }
        if (!report.failureReason().isBlank()) {
            y += 4;
            drawText(graphics, report.failureReason(), x, y, ERROR, false, width);
            y += 14;
        }
        if (!report.rejectionDetails().isEmpty()) {
            y += 2;
            drawText(graphics, "Latest rejection:", x, y, MUTED, false, width);
            y += 12;
            drawText(graphics, report.rejectionDetails().get(report.rejectionDetails().size() - 1), x, y, WARN, false, width);
        } else if (!report.lastRuntimeError().isBlank()) {
            y += 2;
            drawText(graphics, report.lastRuntimeError(), x, y, WARN, false, width);
        }
    }

    private int detailLine(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value, int valueColor) {
        drawText(graphics, label + ":", x, y, MUTED, false, 92);
        drawText(graphics, value, x + 76, y, valueColor, false, Math.max(1, width - 76));
        return y + 12;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (virtual.button() != 0) return super.mouseClicked(virtual, doubleClick);
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtual.x(), virtual.y(), virtual.button())) return true;
        }
        CompactScrollbar.Metrics scrollbar = scrollbarMetrics(filteredReports().size());
        if (scrollbar.hasScroll() && scrollbar.contains(virtual.x(), virtual.y())) {
            scrollbarDragging = true;
            scrollbarGrabOffset = scrollbar.overThumb(virtual.x(), virtual.y())
                ? Math.max(0, (int) Math.round(virtual.y()) - scrollbar.thumbY())
                : scrollbar.thumbHeight() / 2;
            scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtual.y(), scrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            listScroll.jumpTo(scrollOffset, scrollbar.maxScroll());
            return true;
        }
        AddonReport row = rowAt(virtual.x(), virtual.y());
        if (row != null) {
            selectedAddonId = row.modId();
            rebuildButtons();
            clearInputFocus();
            return true;
        }
        return super.mouseClicked(virtual, doubleClick);
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
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (scrollbarDragging) {
            CompactScrollbar.Metrics scrollbar = scrollbarMetrics(filteredReports().size());
            scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtual.y(), scrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            listScroll.jumpTo(scrollOffset, scrollbar.maxScroll());
            return true;
        }
        return super.mouseDragged(virtual, AutismUiScale.toVirtual(dx), AutismUiScale.toVirtual(dy));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        x = AutismUiScale.toVirtual(x);
        y = AutismUiScale.toVirtual(y);
        if (x < listX() || x >= listX() + LEFT_WIDTH || y < BODY_TOP || y >= bodyBottom()) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        int maxScroll = maxScroll(filteredReports().size());
        if (maxScroll <= 0) return true;
        scrollOffset = quantizeScrollOffset(scrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT, ROW_HEIGHT, maxScroll);
        listScroll.setTarget(scrollOffset, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private AddonReport rowAt(double mouseX, double mouseY) {
        if (mouseX < rowX() || mouseX >= rowRight() || mouseY < rowsTop() || mouseY >= rowsBottom()) return null;
        List<AddonReport> reports = filteredReports();
        int index = ((int) mouseY - rowsTop() + scrollOffset) / ROW_HEIGHT;
        return index >= 0 && index < reports.size() ? reports.get(index) : null;
    }

    private void copyId() {
        AddonReport report = selectedReport();
        if (report == null || minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(report.modId());
        AutismNotifications.copied("Addon ID copied.");
    }

    private void copyReport() {
        AddonReport report = selectedReport();
        if (report == null || minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(report.copyReport());
        AutismNotifications.copied("Addon report copied.");
    }

    private void toggleRestartDisabled() {
        AddonReport report = selectedReport();
        if (report == null) return;
        boolean next = !AddonManager.isDisabledOnRestart(report.modId());
        AddonManager.setDisabledOnRestart(report.modId(), next);
        AutismNotifications.show(next ? "Addon disables after restart." : "Addon enables after restart.", next ? WARN : SUCCESS);
        rebuildButtons();
    }

    private void openModsFolder() {
        Util.getPlatform().openFile(AddonManager.modsFolder());
    }

    private List<AddonReport> filteredReports() {
        String query = normalize(searchQuery);
        if (query.equals(cachedFilterQuery)) return cachedFilteredReports;
        cachedFilterQuery = query;
        if (query.isEmpty()) {
            cachedFilteredReports = reportsSnapshot;
            return cachedFilteredReports;
        }
        List<AddonReport> out = new ArrayList<>();
        for (AddonReport report : reportsSnapshot) {
            if (normalize(report.name()).contains(query)
                || normalize(report.modId()).contains(query)
                || normalize(report.authors()).contains(query)) {
                out.add(report);
            }
        }
        cachedFilteredReports = List.copyOf(out);
        return cachedFilteredReports;
    }

    private AddonReport selectedReport() {
        for (AddonReport report : reportsSnapshot) {
            if (report.modId().equals(selectedAddonId)) return report;
        }
        return null;
    }

    private String summaryText() {
        int total = reportsSnapshot.size();
        int loaded = 0;
        int issues = 0;
        int disabled = 0;
        for (AddonReport report : reportsSnapshot) {
            if (report.status() == AddonLoadStatus.LOADED) loaded++;
            if (report.status().isIssue()) issues++;
            if (AddonManager.isDisabledOnRestart(report.modId()) || report.status() == AddonLoadStatus.DISABLED) disabled++;
        }
        return total + " discovered  " + loaded + " loaded  " + issues + " issue(s)  " + disabled + " disabled/restart";
    }

    private String displayStatus(AddonReport report) {
        if (report == null) return "";
        if (AddonManager.isDisabledOnRestart(report.modId()) && report.status() == AddonLoadStatus.LOADED) return "Disables";
        return report.status().label();
    }

    private int statusColor(AddonReport report) {
        if (report == null) return MUTED;
        if (report.status().isIssue()) return ERROR;
        if (AddonManager.isDisabledOnRestart(report.modId()) || report.status() == AddonLoadStatus.DISABLED) return WARN;
        return SUCCESS;
    }

    private String apiText(AddonReport report) {
        String addon = report.apiVersion() < 0 ? "unknown" : Integer.toString(report.apiVersion());
        return "addon v" + addon + " / host v" + report.hostApiVersion();
    }

    private CompactScrollbar.Metrics scrollbarMetrics(int rows) {
        int content = rows * ROW_HEIGHT;
        return CompactScrollbar.compute(content, Math.max(1, rowsBottom() - rowsTop()),
            listX() + LEFT_WIDTH - SCROLLBAR_GUTTER, rowsTop(), SCROLLBAR_WIDTH, rowsBottom() - rowsTop(), scrollOffset);
    }

    private int maxScroll(int rows) {
        return Math.max(0, rows * ROW_HEIGHT - Math.max(1, rowsBottom() - rowsTop()));
    }

    private void clearInputFocus() {
        if (searchField != null) searchField.setFocused(false);
        this.setFocused(null);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
        drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
        Font renderer = this.font;
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE && !center) {
            UiText.drawEllipsized(graphics, renderer, value, fontId, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        if (maxWidth != Integer.MAX_VALUE) value = UiText.trimToWidthEllipsis(renderer, value, maxWidth, fontId, color);
        int w = UiText.width(renderer, value, fontId, color);
        UiText.draw(graphics, renderer, value, fontId, color, center ? x - w / 2 : x, y, false);
    }

    private int panelX() { return (screenWidth() - panelWidth()) / 2; }
    private int panelWidth() { return Math.min(PANEL_WIDTH, Math.max(320, screenWidth() - PANEL_MARGIN * 2)); }
    private int listX() { return panelX() + 10; }
    private int detailX() { return listX() + LEFT_WIDTH + GAP; }
    private int detailWidth() { return Math.max(220, panelWidth() - 20 - LEFT_WIDTH - GAP); }
    private int bodyBottom() { return screenHeight() - BODY_BOTTOM_MARGIN; }
    private int bodyHeight() { return Math.max(120, bodyBottom() - BODY_TOP); }
    private int rowsTop() { return BODY_TOP + 48; }
    private int rowsBottom() { return Math.min(bodyBottom() - 8, rowsTop() + MAX_VISIBLE_ROWS * ROW_HEIGHT); }
    private int rowX() { return listX() + 8; }
    private int rowRight() { return listX() + LEFT_WIDTH - 16; }
    private int rowWidth() { return Math.max(1, rowRight() - rowX()); }

    private static int quantizeScrollOffset(int value, int step, int maxScroll) {
        int clamped = Math.max(0, Math.min(maxScroll, value));
        if (step <= 0) return clamped;
        int rounded = Math.round(clamped / (float) step) * step;
        return Math.max(0, Math.min(maxScroll, rounded));
    }

    private static String normalize(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }
}
