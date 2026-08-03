package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismBackgroundTasks;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiProxyVerifier;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.BORDER_ACTIVE;
import static autismclient.gui.screen.AutismScreenPalette.ERROR;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;
import static autismclient.gui.screen.AutismScreenPalette.WARN;

public final class AutismMultiProxyPickerScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 14;
    private static final int LIST_TOP = 72;
    private static final int LIST_HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_FRAME_HEIGHT = ROW_HEIGHT - 4;
    private static final int CHECK_WIDTH = 52;
    private static final int USED_YELLOW = 0xFFFFC857;

    private final Screen parent;
    private final String contextTitle;
    private final String serverHost;
    private final int serverPort;
    private final String selectedProxyId;
    private final Map<String, Integer> profileUsage;
    private final Consumer<String> onPick;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private EditBox searchField;
    private String search = "";
    private int scrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;
    private long cachedListRevision = Long.MIN_VALUE;
    private long cachedStatusRevision = Long.MIN_VALUE;
    private String cachedSearch = null;
    private List<Choice> cachedChoices = List.of();
    private boolean refreshWasRunning;
    private long watchedRefreshGeneration = Long.MIN_VALUE;

    public AutismMultiProxyPickerScreen(Screen parent, String contextTitle, String serverAddress,
                                        String selectedProxyId, Map<String, Integer> profileUsage,
                                        Consumer<String> onPick) {
        super(Component.literal("Choose Proxy"));
        this.parent = parent;
        this.contextTitle = safeTrim(contextTitle).isBlank() ? "Choose a manual proxy" : safeTrim(contextTitle);

        this.selectedProxyId = selectedProxyId;
        this.profileUsage = profileUsage == null ? Map.of() : Map.copyOf(profileUsage);
        this.onPick = onPick;
        ServerAddress parsed = null;
        try {
            if (serverAddress != null && !serverAddress.isBlank()) parsed = ServerAddress.parseString(serverAddress.trim());
        } catch (RuntimeException ignored) {

        }
        this.serverHost = parsed == null ? "" : parsed.getHost();
        this.serverPort = parsed == null ? 0 : parsed.getPort();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int searchX = rowX();
        searchField = new EditBox(font, searchX, 46, Math.max(40, rowRight() - searchX), 18,
            Component.literal("Search proxies"));
        searchField.setHint(Component.literal("Search name, address, type, or region..."));
        searchField.setMaxLength(160);
        searchField.setValue(search);
        searchField.setResponder(value -> {
            search = safeTrim(value);
            scrollOffset = 0;
            rebuild();
        });
        addRenderableWidget(searchField);
        AutismProxyManager manager = AutismProxyManager.get();
        manager.requestGeoLookup(false);
        AutismProxyManager.RefreshStatus refresh = manager.refreshStatus();
        refreshWasRunning = refresh.running();
        watchedRefreshGeneration = refresh.generation();
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        AutismProxyManager manager = AutismProxyManager.get();
        long listRevision = manager.listRevision();
        AutismProxyManager.RefreshStatus refresh = manager.refreshStatus();
        long statusRevision = refresh.revision();
        if (refreshWasRunning && !refresh.running() && refresh.generation() == watchedRefreshGeneration) {
            if (refresh.checked() >= refresh.total()) {
                manager.sortByLatencyNow();
                toast("Proxy refresh finished and sorted best to worst.", SUCCESS);
            }
            scrollOffset = 0;
            invalidateChoices();
        }
        refreshWasRunning = refresh.running();
        watchedRefreshGeneration = refresh.generation();
        if (listRevision != cachedListRevision || statusRevision != cachedStatusRevision) rebuild();
    }

    private void rebuild() {
        buttons.clear();
        visibleRows.clear();
        int right = screenWidth() - MARGIN - 10;
        CompactOverlayButton back = CompactOverlayButton.create(right - 60, 22, 60, 18,
            Component.literal("Back"), button -> onClose()).setVariant(CompactOverlayButton.Variant.SECONDARY);
        CompactOverlayButton manage = CompactOverlayButton.create(right - 60 - 6 - 104, 22, 104, 18,
            Component.literal("Manage Proxies"), button -> openProxyManager())
            .setVariant(CompactOverlayButton.Variant.PRIMARY);
        AutismProxyManager.RefreshStatus refreshStatus = AutismProxyManager.get().refreshStatus();
        CompactOverlayButton refresh = CompactOverlayButton.create(right - 60 - 6 - 104 - 6 - 78, 22, 78, 18,
            Component.literal(refreshStatus.running() ? "Cancel" : "Refresh"), button -> refreshAll())
            .setVariant(refreshStatus.running()
                ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS);
        buttons.add(back);
        buttons.add(manage);
        buttons.add(refresh);

        List<Choice> choices = choices();
        int visibleCount = visibleRowCount();
        scrollOffset = clampScroll(scrollOffset, choices.size(), visibleCount);
        int y = rowsTop();
        int end = Math.min(choices.size(), scrollOffset + visibleCount);
        for (int i = scrollOffset; i < end; i++) {
            Choice choice = choices.get(i);
            CompactOverlayButton check = null;
            if (choice.proxy() != null) {
                AutismProxy proxy = choice.proxy();
                check = CompactOverlayButton.create(rowRight() - CHECK_WIDTH - 6, y + 6,
                    CHECK_WIDTH, 18, Component.literal(proxy.status == AutismProxy.Status.CHECKING ? "..." : "Check"),
                    button -> checkOne(proxy)).setVariant(CompactOverlayButton.Variant.PRIMARY);
                check.active = !refreshStatus.running() && proxy.status != AutismProxy.Status.CHECKING;
            }
            visibleRows.add(new Row(choice, y, check));
            y += ROW_HEIGHT;
        }
    }

    private void refreshAll() {
        AutismProxyManager manager = AutismProxyManager.get();
        AutismProxyManager.RefreshStatus current = manager.refreshStatus();
        if (current.running()) {
            if (manager.cancelRefresh()) toast("Proxy refresh canceled.", WARN);
            invalidateChoices();
            rebuild();
            return;
        }
        if (serverHost.isBlank() || serverPort <= 0) {
            toast("Set a valid profile server before checking proxies.", WARN);
            return;
        }
        if (!manager.startRefreshToServer(true, serverHost, serverPort)) {
            toast(manager.size() == 0 ? "No proxies to refresh." : "Proxy refresh could not start.", WARN);
            return;
        }
        AutismProxyManager.RefreshStatus started = manager.refreshStatus();
        refreshWasRunning = true;
        watchedRefreshGeneration = started.generation();
        invalidateChoices();
        rebuild();
        toast("Checking proxies against " + serverHost + ':' + serverPort + "...", SUCCESS);
    }

    private void checkOne(AutismProxy proxy) {
        if (proxy == null || AutismProxyManager.get().refreshStatus().running()) return;
        if (serverHost.isBlank() || serverPort <= 0) {
            toast("Set a valid profile server before checking this proxy.", WARN);
            return;
        }
        proxy.status = AutismProxy.Status.CHECKING;
        proxy.latency = 0L;
        invalidateChoices();
        rebuild();
        AutismBackgroundTasks.runTracked("Autism-Multi-Proxy-Check", () -> {
            int attempts = Math.max(1, AutismProxyManager.get().getRetries() + 1);
            MultiProxyVerifier.Result verified = new MultiProxyVerifier.Result(false, 0L);
            for (int attempt = 0; attempt < attempts && !verified.ok(); attempt++) {
                verified = MultiProxyVerifier.verify(proxy, serverHost, serverPort,
                    AutismProxyManager.get().getTimeoutMs());
            }
            AutismProxy.CheckResult result = verified.ok()
                ? new AutismProxy.CheckResult(AutismProxy.Status.ALIVE, verified.latencyMs(), 1)
                : new AutismProxy.CheckResult(AutismProxy.Status.DEAD, 0L, 2);
            MultiProxyVerifier.Result completed = verified;
            if (minecraft != null) minecraft.execute(() -> {
                if (AutismProxyManager.get().applySingleCheck(proxy, result, completed.workingType())) {
                    toast(proxy.displayName() + " is " + statusText(proxy) + '.', statusColor(proxy));
                }
                invalidateChoices();
                rebuild();
            });
        });
    }

    private void invalidateChoices() {
        cachedListRevision = Long.MIN_VALUE;
        cachedStatusRevision = Long.MIN_VALUE;
        cachedSearch = null;
    }

    private void openProxyManager() {
        if (minecraft != null) minecraft.gui.setScreen(new AutismProxiesScreen(this));
    }

    private void pick(String proxyId) {
        if (onPick != null) onPick.accept(proxyId == null ? "" : proxyId);
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private List<Choice> choices() {
        AutismProxyManager manager = AutismProxyManager.get();
        long listRevision = manager.listRevision();
        long statusRevision = manager.refreshStatus().revision();
        String query = search.toLowerCase(Locale.ROOT);
        if (listRevision == cachedListRevision && statusRevision == cachedStatusRevision
            && query.equals(cachedSearch)) return cachedChoices;

        List<AutismProxy> proxies = manager.all().stream()
            .filter(proxy -> proxy != null && proxy.isValid())
            .sorted(Comparator.comparingInt(AutismMultiProxyPickerScreen::proxyRank)
                .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L
                    ? proxy.latency : Long.MAX_VALUE)
                .thenComparing(AutismProxy::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(proxy -> safeTrim(proxy.address), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(proxy -> proxy.port))
            .toList();
        List<Choice> choices = new ArrayList<>(proxies.size() + 2);
        addIfMatches(choices, new Choice("", "Proxy Off", "Connect directly without a proxy", null), query);
        if (proxies.stream().anyMatch(proxy -> proxy.status != AutismProxy.Status.DEAD)) {
            addIfMatches(choices, new Choice(MultiProfile.BEST_PROXY_ID, "Best Proxy",
                "Balance usable proxies across Best Proxy accounts", null), query);
        }
        for (AutismProxy proxy : proxies) {
            String auth = proxy.username == null || proxy.username.isBlank() ? "" : "  Auth";
            Choice choice = new Choice(proxy.stableId(), proxy.displayName(),
                proxy.type + "  " + proxy.address + ':' + proxy.port + auth, proxy);
            addIfMatches(choices, choice, query);
        }
        cachedListRevision = listRevision;
        cachedStatusRevision = statusRevision;
        cachedSearch = query;
        cachedChoices = List.copyOf(choices);
        return cachedChoices;
    }

    private static void addIfMatches(List<Choice> choices, Choice choice, String query) {
        if (query.isBlank() || choice.searchText().contains(query)) choices.add(choice);
    }

    private static int proxyRank(AutismProxy proxy) {
        if (proxy == null || proxy.status == null) return 2;
        return switch (proxy.status) {
            case ALIVE -> 0;
            case UNCHECKED -> 1;
            case CHECKING -> 2;
            case DEAD -> 3;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0) {
            CompactScrollbar.Metrics scrollbar = scrollbarMetrics();
            if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
                scrollbarDragging = true;
                scrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
                    ? Math.max(0, (int) Math.round(virtualEvent.y()) - scrollbar.thumbY())
                    : scrollbar.thumbHeight() / 2;
                setScrollFromPixels(CompactScrollbar.scrollFromThumb(
                    scrollbar, virtualEvent.y(), scrollbarGrabOffset));
                return true;
            }
            for (CompactOverlayButton button : buttons) {
                if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
                    return true;
                }
            }
            for (Row row : visibleRows) {
                CompactOverlayButton check = row.check();
                if (check != null && virtualEvent.x() >= check.getX()
                    && virtualEvent.x() < check.getX() + check.getWidth()
                    && virtualEvent.y() >= check.getY()
                    && virtualEvent.y() < check.getY() + check.getHeight()) {
                    CompactOverlayButton.fireIfHit(check, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                    return true;
                }
            }
            for (Row row : visibleRows) {
                if (virtualEvent.x() >= rowX() && virtualEvent.x() < rowRight()
                    && virtualEvent.y() >= row.y() && virtualEvent.y() < row.y() + ROW_FRAME_HEIGHT) {
                    pick(row.choice().id());
                    return true;
                }
            }
        }
        return super.mouseClicked(virtualEvent, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (scrollbarDragging) {
            CompactScrollbar.Metrics scrollbar = scrollbarMetrics();
            setScrollFromPixels(CompactScrollbar.scrollFromThumb(
                scrollbar, virtualEvent.y(), scrollbarGrabOffset));
            return true;
        }
        return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dragX), AutismUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int x = AutismUiScale.toVirtualInt(mouseX);
        int y = AutismUiScale.toVirtualInt(mouseY);
        if (x < rowX() || x >= rowRight() + 8 || y < rowsTop() || y >= rowsBottom()) {
            return super.mouseScrolled(x, y, horizontal, vertical);
        }
        if (vertical == 0.0) return true;
        scrollOffset = clampScroll(scrollOffset + (vertical < 0.0 ? 2 : -2), choices().size(), visibleRowCount());
        rebuild();
        return true;
    }

    private void setScrollFromPixels(int pixels) {
        scrollOffset = clampScroll(Math.round(pixels / (float) ROW_HEIGHT), choices().size(), visibleRowCount());
        rebuild();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), themeBg());
            UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, panelWidth(), screenHeight() - 28),
                themePanel(), themeBorder());
            UiRenderer.frame(graphics, UiBounds.of(rowX() - 4, LIST_TOP, rowRight() - rowX() + 12,
                Math.max(24, screenHeight() - LIST_TOP - 20)),
                AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON), themeBorder());

            int firstButtonX = buttons.stream().mapToInt(CompactOverlayButton::getX).min()
                .orElse(screenWidth() - MARGIN - 10);
            int titleMaxWidth = Math.max(1, firstButtonX - (MARGIN + 10) - 6);
            drawFitted(graphics, contextTitle, MARGIN + 10, 25, titleMaxWidth, themeText());
            renderListHeader(graphics);
            for (Row row : visibleRows) renderRow(graphics, row, virtualMouseX, virtualMouseY);
            for (CompactOverlayButton button : buttons) {
                CompactOverlayButton.renderStyled(graphics, font, button, virtualMouseX, virtualMouseY);
            }
            CompactScrollbar.Metrics scrollbar = scrollbarMetrics();
            if (scrollbar.hasScroll()) {
                CompactScrollbar.draw(graphics, scrollbar,
                    scrollbar.contains(virtualMouseX, virtualMouseY), scrollbarDragging);
            }
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderListHeader(GuiGraphicsExtractor graphics) {
        List<Choice> choices = choices();
        AutismProxyManager.RefreshStatus refresh = AutismProxyManager.get().refreshStatus();
        int totalUsable = (int) AutismProxyManager.get().all().stream()
            .filter(proxy -> proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD)
            .count();
        AutismProxy selectedProxy = selectedProxyId == null || selectedProxyId.isBlank()
            || MultiProfile.BEST_PROXY_ID.equals(selectedProxyId)
            ? null : AutismProxyManager.get().findById(selectedProxyId);
        boolean selectedVisible = selectedProxyId == null
            || selectedProxyId.isBlank()
            || (MultiProfile.BEST_PROXY_ID.equals(selectedProxyId) && totalUsable > 0)
            || (selectedProxy != null && selectedProxy.isValid() && selectedProxy.status != AutismProxy.Status.DEAD);
        if (refresh.running()) {
            String progress = "Refreshing " + refresh.checked() + '/' + refresh.total()
                + " against " + serverHost + ':' + serverPort;
            drawFitted(graphics, progress, rowX(), LIST_TOP + 7,
                Math.max(20, rowRight() - rowX()), themeText());
        } else if (!selectedVisible) {
            drawFitted(graphics, "Current proxy is missing; choose a replacement", rowX(), LIST_TOP + 8,
                Math.max(20, rowRight() - rowX()), themeWarn());
        } else if (selectedProxy != null && selectedProxy.status == AutismProxy.Status.DEAD) {
            drawFitted(graphics, "Selected proxy failed its last check; manual selection is still preserved",
                rowX(), LIST_TOP + 8, Math.max(20, rowRight() - rowX()), themeWarn());
        } else {
            String summary = choices.size() + " choices  " + totalUsable + " usable proxies";
            drawFitted(graphics, summary, rowX(), LIST_TOP + 8,
                Math.max(20, rowRight() - rowX()), themeText());
        }
        if (choices.isEmpty()) {
            drawText(graphics, "No proxy choices match this search.", rowX() + 4, rowsTop() + 8, themeMuted());
        }
        if (refresh.running()) {
            int trackX = rowX();
            int trackY = LIST_TOP + LIST_HEADER_HEIGHT - 4;
            int trackW = Math.max(1, rowRight() - rowX());
            UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, trackW, 2), themeBorder());
            int total = Math.max(1, refresh.total());
            int fillW = refresh.checked() <= 0 ? 0
                : Math.max(1, Math.min(trackW, Math.round(trackW * refresh.checked() / (float) total)));
            if (fillW > 0) UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, fillW, 2), themeSuccess());
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY) {
        Choice choice = row.choice();
        boolean selected = java.util.Objects.equals(selectedProxyId, choice.id());
        int usedCount = profileUsage.getOrDefault(choice.id(), 0);
        boolean used = usedCount > 0;
        boolean hovered = mouseX >= rowX() && mouseX < rowRight()
            && mouseY >= row.y() && mouseY < row.y() + ROW_FRAME_HEIGHT;
        int fill = used ? 0x33332208
            : selected ? AutismTheme.recolor(0x3324D86A, Channel.SUCCESS)
            : hovered ? AutismTheme.recolor(0x2A2B1A1D, Channel.ACCENT)
            : AutismTheme.recolor(0x18111113, Channel.BUTTON);
        UiRenderer.rect(graphics, UiBounds.of(rowX(), row.y(), rowRight() - rowX(), ROW_FRAME_HEIGHT), fill);
        if (selected) UiRenderer.rect(graphics, UiBounds.of(rowX(), row.y(), 2, ROW_FRAME_HEIGHT), themeSuccess());
        if (used) UiRenderer.rect(graphics, UiBounds.of(rowX(), row.y(), 2, ROW_FRAME_HEIGHT), USED_YELLOW);

        AutismProxy proxy = choice.proxy();
        int checkX = row.check() == null ? rowRight() - 6 : row.check().getX();
        int metaWidth = proxy == null ? 84 : Math.min(86, Math.max(48, (rowRight() - rowX()) / 4));
        int metaX = checkX - metaWidth - 7;
        int textWidth = Math.max(24, metaX - rowX() - 18);
        drawFitted(graphics, choice.label(), rowX() + 9, row.y() + 6, textWidth,
            used ? USED_YELLOW : selected ? themeSuccess() : themeText());
        drawFitted(graphics, choice.description(), rowX() + 9, row.y() + 18, textWidth, themeMuted());

        if (proxy == null) {
            String status = choice.id().isBlank() ? "Direct" : usableProxyCount() + " available";
            drawRightFitted(graphics, status, metaX, row.y() + 12, metaWidth,
                choice.id().isBlank() ? themeMuted() : themeSuccess());
        } else {
            drawRightFitted(graphics, used ? "Used" + (usedCount > 1 ? " x" + usedCount : "") : proxy.geoLabel(),
                metaX, row.y() + 6, metaWidth, used ? USED_YELLOW : proxy.geoColor());
            drawRightFitted(graphics, statusText(proxy), metaX, row.y() + 18, metaWidth, statusColor(proxy));
            if (row.check() != null) {
                CompactOverlayButton.renderStyled(graphics, font, row.check(), mouseX, mouseY);
            }
        }
    }

    private int usableProxyCount() {
        int count = 0;
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD) count++;
        }
        return count;
    }

    private static String statusText(AutismProxy proxy) {
        if (proxy == null || proxy.status == null) return "Unchecked";
        return switch (proxy.status) {
            case ALIVE -> proxy.latency > 0L ? proxy.latency + "ms" : "Alive";
            case CHECKING -> "Checking";
            case UNCHECKED -> "Unchecked";
            case DEAD -> "Dead";
        };
    }

    private static int statusColor(AutismProxy proxy) {
        if (proxy == null || proxy.status == null) return MUTED;
        return switch (proxy.status) {
            case ALIVE -> SUCCESS;
            case CHECKING, UNCHECKED -> MUTED;
            case DEAD -> ERROR;
        };
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        int viewport = Math.max(1, rowsBottom() - rowsTop());
        return CompactScrollbar.compute(choices().size() * ROW_HEIGHT, viewport,
            rowRight() + 5, rowsTop(), 4, viewport, scrollOffset * ROW_HEIGHT);
    }

    private int visibleRowCount() {
        return Math.max(1, Math.max(1, rowsBottom() - rowsTop()) / ROW_HEIGHT);
    }

    private static int clampScroll(int offset, int total, int visible) {
        return Math.max(0, Math.min(offset, Math.max(0, total - visible)));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private int panelWidth() {
        return Math.max(1, screenWidth() - MARGIN * 2);
    }

    private int rowX() {
        return MARGIN + 10;
    }

    private int rowRight() {
        return Math.max(rowX() + 40, screenWidth() - MARGIN - 14);
    }

    private int rowsTop() {
        return LIST_TOP + LIST_HEADER_HEIGHT;
    }

    private int rowsBottom() {
        return Math.max(rowsTop() + ROW_HEIGHT, screenHeight() - 22);
    }

    private void drawText(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        UiText.draw(graphics, font, value == null ? "" : value, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String fitted = UiText.trimToWidthEllipsis(font, value == null ? "" : value,
            Math.max(1, maxWidth), fontId, color);
        UiText.draw(graphics, font, fitted, fontId, color, x, y, false);
    }

    private void drawRightFitted(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String fitted = UiText.trimToWidthEllipsis(font, value == null ? "" : value,
            Math.max(1, maxWidth), fontId, color);
        UiText.draw(graphics, font, fitted, fontId, color, x + maxWidth - font.width(fitted), y, false);
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themeBorder() {
        return AutismTheme.recolor(BORDER, Channel.OUTLINE);
    }

    private static int themeText() {
        return AutismTheme.recolor(TEXT, Channel.TEXT);
    }

    private static int themeMuted() {
        return MUTED;
    }

    private static int themeSuccess() {
        return AutismTheme.recolor(BORDER_ACTIVE, Channel.SUCCESS);
    }

    private static int themeWarn() {
        return WARN;
    }

    private record Choice(String id, String label, String description, AutismProxy proxy) {
        private String searchText() {
            String proxyText = proxy == null ? "" : proxy.geoSearchText() + ' ' + proxy.status;
            return (safeTrim(label) + ' ' + safeTrim(description) + ' ' + proxyText).toLowerCase(Locale.ROOT);
        }
    }

    private record Row(Choice choice, int y, CompactOverlayButton check) {
    }
}
