package autismclient.gui.screen;

import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.util.AutismBackgroundTasks;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismProxyType;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static autismclient.gui.screen.AutismScreenPalette.*;

public class AutismProxiesScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int DEFAULT_COLOR = 0xFF8EA0FF;
    private static final int PANEL_WIDTH = 530;
    private static final int PANEL_MARGIN = 12;
    private static final int ROW_HEIGHT = 32;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_HEIGHT = 156;
    private static final int LIST_TOP = 178;
    private static final int LIST_HEADER_HEIGHT = 22;
    private static final int LIST_BOTTOM_MARGIN = 12;
    private static final int LIST_SCROLLBAR_WIDTH = 4;
    private static final int LIST_SCROLLBAR_GUTTER = 12;
    private static final int ROW_TOGGLE_W = 44;
    private static final int ROW_CHECK_W = 56;
    private static final int ROW_DELETE_W = 22;
    private static final int ROW_ACTION_GAP = 6;
    private static final int ROW_ACTION_RIGHT_PAD = 8;

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<ProxyRow> proxyRows = new ArrayList<>();
    private final ScrollState proxyListScroll = new ScrollState();
    private EditBox nameField;
    private EditBox addressField;
    private EditBox portField;
    private EditBox usernameField;
    private EditBox passwordField;
    private EditBox searchField;
    private AutismProxyType type = AutismProxyType.Socks5;
    private ProxyFilter filter = ProxyFilter.ALL;
    private AutismProxy selectedProxy;
    private String searchQuery = "";
    private int proxyListScrollOffset;
    private boolean proxyScrollbarDragging;
    private int proxyScrollbarGrabOffset;
    private long lastRefreshRevision = Long.MIN_VALUE;
    private long lastImportRevision = Long.MIN_VALUE;
    private boolean importChooserOpen;
    private CompactOverlayButton importFileButton;
    private CompactOverlayButton importClipboardButton;
    private long cachedListRevision = Long.MIN_VALUE;
    private long cachedRefreshRevision = Long.MIN_VALUE;
    private String cachedSearchQuery = null;
    private ProxyFilter cachedFilter = null;
    private List<AutismProxy> cachedFilteredProxies = List.of();
    private long cachedActiveRevision = Long.MIN_VALUE;
    private AutismProxy cachedActiveProxy;
    private List<AutismProxy> cachedDisplaySource;
    private AutismProxy cachedDisplayActive;
    private List<AutismProxy> cachedDisplayProxies = List.of();

    public AutismProxiesScreen(Screen parent) {
        super(Component.literal("Proxies"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int formX = panelX() + 10;
        int inputX = formX + 8;
        int inputW = Math.max(1, panelWidth() - 36);
        int gap = 8;
        int portW = Math.max(42, Math.min(72, inputW / 5));
        int remaining = Math.max(2, inputW - portW - gap * 2);
        int nameW = Math.max(1, remaining * 2 / 5);
        int addressW = Math.max(1, remaining - nameW);
        int halfW = Math.max(1, (inputW - gap) / 2);
        this.nameField = addField(inputX, 48, nameW, "Name");
        this.addressField = addField(inputX + nameW + gap, 48, addressW, "Address");
        this.portField = addField(inputX + nameW + gap + addressW + gap, 48, portW, "Port");
        this.usernameField = addField(inputX, 72, halfW, "Username");
        this.passwordField = addField(inputX + halfW + gap, 72, Math.max(1, inputW - halfW - gap), "Password");
        this.searchField = addField(inputX, 122, inputW, "Search proxies");
        this.searchField.setHint(Component.literal("Search name/address..."));
        this.searchField.setResponder(value -> {
            searchQuery = safeTrim(value);
            proxyListScrollOffset = 0;
            proxyListScroll.jumpTo(0, 0);
            rebuildButtons();
        });
        setFormFieldsVisible(!compactLayout());
        AutismProxyManager.get().requestGeoLookup(false);
        rebuildButtons();
    }

    private EditBox addField(int x, int y, int w, String hint) {
        EditBox field = new EditBox(this.font, x, y, w, 18, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setMaxLength(256);
        this.addRenderableWidget(field);
        return field;
    }

    private void rebuildButtons() {
        buttons.clear();
        proxyRows.clear();
        int formX = panelX() + 10;

        buttons.add(CompactOverlayButton.create(10, 10, 76, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent)).setVariant(CompactOverlayButton.Variant.SECONDARY));
        if (compactLayout()) return;
        int actionX = formX + 8;
        int actionGap = 6;
        int usableW = Math.max(1, panelWidth() - 36);
        AutismProxyManager manager = AutismProxyManager.get();
        AutismProxyManager.RefreshStatus refreshStatus = manager.refreshStatus();
        AutismProxyManager.ImportStatus importStatus = manager.importStatus();
        boolean refreshRunning = refreshStatus.running();
        boolean importRunning = importStatus.running();
        int[] actionWidths = scaledWidths(usableW, actionGap, 112, 64, 64, 72, 72, 64, 64);
        buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[0], 18, Component.literal("Type: " + type), b -> {
            type = type == AutismProxyType.Socks5 ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
            rebuildButtons();
        }).setVariant(CompactOverlayButton.Variant.SECONDARY));
        actionX += actionWidths[0] + actionGap;
        buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[1], 18, Component.literal(selectedProxy == null ? "Add" : "Update"), b -> {
            if (selectedProxy == null) addProxy();
            else updateProxy();
        }).setVariant(CompactOverlayButton.Variant.PRIMARY));
        actionX += actionWidths[1] + actionGap;
        buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[2], 18, Component.literal(selectedProxy == null ? "Clear" : "Cancel"), b -> clearProxySelection()).setVariant(selectedProxy == null ? CompactOverlayButton.Variant.SECONDARY : CompactOverlayButton.Variant.DANGER));
        actionX += actionWidths[2] + actionGap;
        CompactOverlayButton refresh = CompactOverlayButton.create(actionX, 96, actionWidths[3], 18, Component.literal(refreshRunning ? "Cancel" : "Refresh"), b -> refreshProxies()).setVariant(refreshRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SECONDARY);
        refresh.active = !importRunning;
        buttons.add(refresh);
        actionX += actionWidths[3] + actionGap;
        CompactOverlayButton cleanup = CompactOverlayButton.create(actionX, 96, actionWidths[4], 18, Component.literal("Cleanup"), b -> cleanupProxies()).setVariant(CompactOverlayButton.Variant.SECONDARY);
        cleanup.active = !refreshRunning && !importRunning;
        buttons.add(cleanup);
        actionX += actionWidths[4] + actionGap;
        buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[5], 18, Component.literal(importRunning ? "Cancel" : "Import"), b -> importProxies()).setVariant(importRunning ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SECONDARY));
        actionX += actionWidths[5] + actionGap;
        buttons.add(CompactOverlayButton.create(actionX, 96, actionWidths[6], 18, Component.literal("Config"), b -> this.minecraft.gui.setScreen(new AutismProxyConfigScreen(this))).setVariant(CompactOverlayButton.Variant.SECONDARY));

        int filterX = formX + 8;
        int filterGap = 6;
        int[] filterWidths = scaledWidths(usableW, filterGap, 52, 56, 56, 78, 68, 48, 70);
        addFilterButton(filterX, 148, filterWidths[0], ProxyFilter.ALL, "All");
        filterX += filterWidths[0] + filterGap;
        addFilterButton(filterX, 148, filterWidths[1], ProxyFilter.ALIVE, "Alive");
        filterX += filterWidths[1] + filterGap;
        addFilterButton(filterX, 148, filterWidths[2], ProxyFilter.DEAD, "Dead");
        filterX += filterWidths[2] + filterGap;
        addFilterButton(filterX, 148, filterWidths[3], ProxyFilter.UNCHECKED, "Unchecked");
        filterX += filterWidths[3] + filterGap;
        addFilterButton(filterX, 148, filterWidths[4], ProxyFilter.ENABLED, "Enabled");
        filterX += filterWidths[4] + filterGap;
        CompactOverlayButton sort = CompactOverlayButton.create(filterX, 148, filterWidths[5], 18, Component.literal("Sort"), b -> sortProxies()).setVariant(CompactOverlayButton.Variant.SECONDARY);
        sort.active = !refreshRunning && !importRunning && manager.size() > 1;
        buttons.add(sort);
        filterX += filterWidths[5] + filterGap;
        CompactOverlayButton clearAll = CompactOverlayButton.create(filterX, 148, filterWidths[6], 18, Component.literal("Clear All"), b -> clearAllProxies()).setVariant(CompactOverlayButton.Variant.DANGER);
        clearAll.active = !refreshRunning && !importRunning && manager.size() > 0;
        buttons.add(clearAll);

        List<AutismProxy> proxies = displayProxies();
        boolean hasActiveSection = proxies.size() > filteredProxies().size();
        int maxScroll = proxyMaxScroll(proxies.size());
        proxyListScrollOffset = clampScrollOffset(proxyListScrollOffset, maxScroll);
        proxyListScroll.jumpTo(proxyListScrollOffset, maxScroll);
        int firstVisible = proxyListScrollOffset / ROW_HEIGHT;
        int rowY = proxyRowsTop() - (proxyListScrollOffset % ROW_HEIGHT);
        for (int i = firstVisible; i < proxies.size() && rowY + ROW_HEIGHT - 3 <= proxyRowsBottom(); i++) {
            AutismProxy proxy = proxies.get(i);
            if (rowY + ROW_HEIGHT - 3 <= proxyRowsTop()) {
                rowY += ROW_HEIGHT;
                continue;
            }
            proxyRows.add(buildProxyRow(proxy, rowY, hasActiveSection && i == 0));
            rowY += ROW_HEIGHT;
        }
    }

    private ProxyRow buildProxyRow(AutismProxy proxy, int rowY, boolean activeSection) {
        CompactOverlayButton toggle = CompactOverlayButton.create(proxyRowX() + 8, rowY + 7, ROW_TOGGLE_W, 16, Component.literal(proxy.enabled ? "ON" : "OFF"), b -> toggleProxy(proxy));
        toggle.setVariant(proxy.enabled ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER)
            .setToggleState(proxy.enabled)
            .setAnimationKey("proxy-enabled:" + proxy.address + ':' + proxy.port);
        int deleteX = proxyRowRight() - ROW_ACTION_RIGHT_PAD - ROW_DELETE_W;
        int checkX = deleteX - ROW_ACTION_GAP - ROW_CHECK_W;
        CompactOverlayButton check = CompactOverlayButton.create(checkX, rowY + 7, ROW_CHECK_W, 16, Component.literal(proxy.status == AutismProxy.Status.CHECKING ? "..." : "Check"), b -> checkProxy(proxy));
        check.setVariant(CompactOverlayButton.Variant.PRIMARY);
        check.active = proxy.status != AutismProxy.Status.CHECKING;
        CompactOverlayButton delete = CompactOverlayButton.create(deleteX, rowY + 7, ROW_DELETE_W, 16, Component.empty(), b -> deleteProxy(proxy));
        delete.setVariant(CompactOverlayButton.Variant.DANGER).setIcon(autismclient.util.AutismUiIcons.TRASH);
        return new ProxyRow(proxy, rowY, activeSection, toggle, check, delete);
    }

    private void addFilterButton(int x, int y, int width, ProxyFilter value, String label) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, width, 18, Component.literal(label), b -> {
            filter = value;
            proxyListScrollOffset = 0;
            proxyListScroll.jumpTo(0, 0);
            rebuildButtons();
        });
        button.setVariant(filter == value ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.FILTER_OFF);
        buttons.add(button);
    }

    @Override
    public void tick() {
        super.tick();
        int maxScroll = proxyMaxScroll(displayProxies().size());
        proxyListScrollOffset = clampScrollOffset(proxyListScrollOffset, maxScroll);
        proxyListScroll.jumpTo(proxyListScrollOffset, maxScroll);
        AutismProxyManager.RefreshStatus status = AutismProxyManager.get().refreshStatus();
        if (status.revision() != lastRefreshRevision) {
            lastRefreshRevision = status.revision();
            rebuildButtons();
        }
        AutismProxyManager.ImportStatus importStatus = AutismProxyManager.get().importStatus();
        if (importStatus.revision() != lastImportRevision) {
            lastImportRevision = importStatus.revision();
            rebuildButtons();
        }
    }

    private void addProxy() {
        AutismProxy proxy = proxyFromFields();
        if (proxy == null) return;
        if (AutismProxyManager.get().add(proxy)) {
            selectedProxy = null;
            clearProxyFields();
            toast("Added proxy " + proxy.address + ":" + proxy.port + ".", SUCCESS);
            checkProxy(proxy);
        } else {
            toast("Proxy already exists or is invalid.", ERROR);
        }
        rebuildButtons();
    }

    private void updateProxy() {
        if (selectedProxy == null) return;
        AutismProxy updated = proxyFromFields();
        if (updated == null) return;
        if (AutismProxyManager.get().update(selectedProxy, updated)) {
            toast("Updated " + selectedProxy.displayName() + ".", SUCCESS);
            clearProxySelection();
        } else {
            toast("Proxy already exists or is invalid.", ERROR);
            rebuildButtons();
        }
    }

    private void toggleProxy(AutismProxy proxy) {
        if (proxy == null) return;
        int sectionsBefore = displayProxies().size() - filteredProxies().size();
        AutismProxyManager.get().setEnabled(proxy, !proxy.enabled);
        anchorScrollForSectionChange(sectionsBefore);
        toast(proxy.enabled ? "Enabled " + proxy.displayName() + "." : "Disabled proxy.", proxy.enabled ? SUCCESS : MUTED);
        rebuildButtons();
    }

    private void anchorScrollForSectionChange(int sectionsBefore) {
        int sectionsAfter = displayProxies().size() - filteredProxies().size();
        if (sectionsAfter == sectionsBefore) return;
        int maxScroll = proxyMaxScroll(displayProxies().size());
        proxyListScrollOffset = clampScrollOffset(proxyListScrollOffset + (sectionsAfter - sectionsBefore) * ROW_HEIGHT, maxScroll);
        proxyListScroll.jumpTo(proxyListScrollOffset, maxScroll);
    }

    private void deleteProxy(AutismProxy proxy) {
        if (proxy == null) return;
        AutismProxyManager.get().remove(proxy);
        if (proxy.equals(selectedProxy)) selectedProxy = null;
        toast("Deleted " + proxy.displayName() + ".", MUTED);
        rebuildButtons();
    }

    private void checkProxy(AutismProxy proxy) {
        if (proxy == null || proxy.status == AutismProxy.Status.CHECKING) return;
        proxy.status = AutismProxy.Status.CHECKING;
        proxy.latency = 0L;
        rebuildButtons();
        AutismBackgroundTasks.runTracked("Autism-Proxy-Check", () -> {
            proxy.checkStatus(AutismProxyManager.get().getTimeoutMs());
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    toast(proxy.displayName() + " is " + statusText(proxy) + ".", proxy.status.color());
                    rebuildButtons();
                });
            }
        });
    }

    private void refreshProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        if (manager.importStatus().running()) {
            toast("Finish import before refreshing.", WARN);
            return;
        }
        AutismProxyManager.RefreshStatus currentStatus = manager.refreshStatus();
        if (currentStatus.running()) {
            if (manager.cancelRefresh()) {
                toast("Proxy refresh canceled.", WARN);
                rebuildButtons();
            }
            return;
        }
        if (manager.size() == 0) {
            toast("No proxies to refresh.", WARN);
            return;
        }
        if (!manager.startRefresh(true)) {
            toast("Proxy refresh could not start.", WARN);
            return;
        }
        long generation = manager.refreshStatus().generation();
        toast("Refreshing proxies...", DEFAULT_COLOR);
        rebuildButtons();
        AutismBackgroundTasks.watchUntil("Autism-Proxy-Refresh-Watch", () -> !manager.refreshStatus().running(), () -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    AutismProxyManager.RefreshStatus finished = manager.refreshStatus();
                    if (finished.generation() == generation && finished.checked() >= finished.total()) {
                        toast("Proxy refresh finished.", SUCCESS);
                    }
                    rebuildButtons();
                });
            }
        }, 100L);
    }

    private void cleanupProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        if (manager.refreshStatus().running()) {
            toast("Cancel refresh before cleanup.", WARN);
            return;
        }
        if (manager.importStatus().running()) {
            toast("Finish import before cleanup.", WARN);
            return;
        }
        int before = manager.size();
        manager.clean();
        int removed = Math.max(0, before - manager.size());
        toast(removed == 0 ? "Cleanup finished. Nothing removed." : "Cleanup removed " + removed + " proxies.", removed == 0 ? MUTED : SUCCESS);
        rebuildButtons();
    }

    private void sortProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        if (manager.refreshStatus().running()) {
            toast("Cancel refresh before sorting proxies.", WARN);
            return;
        }
        if (manager.importStatus().running()) {
            toast("Finish import before sorting proxies.", WARN);
            return;
        }
        if (manager.sortByLatencyNow()) {
            toast("Sorted proxies by latency.", SUCCESS);
        } else {
            toast("Nothing to sort.", MUTED);
        }
        rebuildButtons();
    }

    private void clearAllProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        if (manager.refreshStatus().running()) {
            toast("Cancel refresh before clearing proxies.", WARN);
            return;
        }
        if (manager.importStatus().running()) {
            toast("Finish import before clearing proxies.", WARN);
            return;
        }
        int removed = manager.clearAll();
        selectedProxy = null;
        clearProxyFields();
        proxyListScrollOffset = 0;
        proxyListScroll.jumpTo(0, 0);
        toast(removed == 0 ? "Proxy list is already empty." : "Cleared " + removed + " proxies.", removed == 0 ? MUTED : SUCCESS);
        rebuildButtons();
    }

    private void importProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        AutismProxyManager.ImportStatus status = manager.importStatus();
        if (status.running()) {
            if (manager.cancelImport()) {
                toast("Proxy import canceled.", WARN);
                rebuildButtons();
            }
            return;
        }
        if (manager.refreshStatus().running()) {
            toast("Cancel refresh before importing.", WARN);
            return;
        }

        importChooserOpen = true;
    }

    private void importFromFile() {
        importChooserOpen = false;
        AutismProxyManager manager = AutismProxyManager.get();
        PointerBuffer filters = BufferUtils.createPointerBuffer(1);
        ByteBuffer txtFilter = MemoryUtil.memASCII("*.txt");
        filters.put(txtFilter);
        filters.rewind();
        String selectedFile = TinyFileDialogs.tinyfd_openFileDialog("Import Proxies", null, filters, null, false);
        if (selectedFile != null) {
            File file = new File(selectedFile);
            if (!manager.startImport(file)) {
                toast("Proxy import could not start.", WARN);
            } else {
                watchImport(manager, manager.importStatus().generation());
            }
        }
        MemoryUtil.memFree(txtFilter);
    }

    private void importFromClipboard() {
        importChooserOpen = false;
        AutismProxyManager manager = AutismProxyManager.get();
        String clipboard = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : null;
        if (clipboard == null || clipboard.isBlank()) {
            toast("Clipboard is empty.", WARN);
            return;
        }
        if (!manager.startImportFromText(clipboard)) {
            toast("Proxy import could not start.", WARN);
            return;
        }
        watchImport(manager, manager.importStatus().generation());
    }

    private void watchImport(AutismProxyManager manager, long generation) {
        toast("Importing proxies...", DEFAULT_COLOR);
        rebuildButtons();
        AutismBackgroundTasks.watchUntil("Autism-Proxy-Import-Watch", () -> !manager.importStatus().running(), () -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    AutismProxyManager.ImportStatus finished = manager.importStatus();
                    if (finished.generation() == generation && !finished.canceled()) {
                        toast("Imported " + finished.added() + " proxies.", finished.added() > 0 ? SUCCESS : WARN);
                        proxyListScrollOffset = 0;
                        proxyListScroll.jumpTo(0, 0);
                    }
                    rebuildButtons();
                });
            }
        }, 100L);
    }

    private UiBounds[] importChooserRects() {
        int cardW = 200;
        int cardH = 86;
        int cardX = (screenWidth() - cardW) / 2;
        int cardY = (screenHeight() - cardH) / 2;
        int btnW = cardW - 24;
        int btnH = 22;
        int btnX = cardX + 12;
        int fileY = cardY + 30;
        int clipY = fileY + btnH + 8;
        return new UiBounds[]{
            UiBounds.of(cardX, cardY, cardW, cardH),
            UiBounds.of(btnX, fileY, btnW, btnH),
            UiBounds.of(btnX, clipY, btnW, btnH),
        };
    }

    private void ensureImportChooserButtons() {
        if (importFileButton == null) {
            importFileButton = CompactOverlayButton.create(0, 0, 176, 22, Component.literal("From File"), b -> importFromFile())
                .setVariant(CompactOverlayButton.Variant.SECONDARY);
        }
        if (importClipboardButton == null) {
            importClipboardButton = CompactOverlayButton.create(0, 0, 176, 22, Component.literal("From Clipboard"), b -> importFromClipboard())
                .setVariant(CompactOverlayButton.Variant.SECONDARY);
        }
    }

    private void positionImportChooserButtons(UiBounds[] rects) {
        importFileButton.setX(rects[1].x());
        importFileButton.setY(rects[1].y());
        importClipboardButton.setX(rects[2].x());
        importClipboardButton.setY(rects[2].y());
    }

    private void renderImportChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ensureImportChooserButtons();
        UiBounds[] rects = importChooserRects();
        positionImportChooserButtons(rects);
        UiBounds card = rects[0];
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xC8000000);
        UiRenderer.frame(graphics, card, PANEL_BG, outlineColor(BORDER));
        drawText(graphics, "Import proxies from", card.x() + card.width() / 2, card.y() + 10, TEXT, true);
        CompactOverlayButton.renderStyled(graphics, this.font, importFileButton, mouseX, mouseY);
        CompactOverlayButton.renderStyled(graphics, this.font, importClipboardButton, mouseX, mouseY);
    }

    private void clearProxyFields() {
        nameField.setValue("");
        addressField.setValue("");
        portField.setValue("");
        usernameField.setValue("");
        passwordField.setValue("");
    }

    private void clearProxySelection() {
        selectedProxy = null;
        clearProxyFields();
        clearInputFocus();
        rebuildButtons();
    }

    private AutismProxy proxyFromFields() {
        AutismProxy proxy = new AutismProxy();
        proxy.name = safeTrim(nameField.getValue());
        proxy.address = safeTrim(addressField.getValue());
        proxy.username = safeTrim(usernameField.getValue());
        proxy.password = passwordField.getValue();
        proxy.type = type;
        if (proxy.address.isBlank()) {
            toast("Enter a proxy address first.", WARN);
            return null;
        }
        try {
            proxy.port = Integer.parseInt(safeTrim(portField.getValue()));
        } catch (NumberFormatException e) {
            toast("Invalid proxy port.", WARN);
            return null;
        }
        if (!proxy.isValid()) {
            toast("Proxy must have a valid address and port.", WARN);
            return null;
        }
        return proxy;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
        int panelX = panelX();
        drawPanel(graphics, panelX + 10, TOP_PANEL_Y, panelWidth() - 20, TOP_PANEL_HEIGHT, PANEL_BG);
        drawPanel(graphics, listX(), LIST_TOP, listWidth(), listPanelHeight(), PANEL_BG_SOFT);

        try {
            drawText(graphics, "Proxies", panelX + 22, TOP_PANEL_Y + 10, TEXT, false);
            if (compactLayout()) {
                drawText(graphics, "Window too small.", panelX + 22, TOP_PANEL_Y + 34, MUTED, false, Math.max(0, panelWidth() - 44));
            }
            List<AutismProxy> proxies = filteredProxies();
            AutismProxyManager manager = AutismProxyManager.get();
            int total = manager.size();
            int enabled = manager.enabledCount();
            AutismProxyManager.RefreshStatus refreshStatus = manager.refreshStatus();
            AutismProxyManager.ImportStatus importStatus = manager.importStatus();
            String summary = proxies.size() == total
                ? total + " proxies  enabled " + enabled
                : proxies.size() + " / " + total + " proxies  enabled " + enabled;
            String workLabel = importStatusText(importStatus);
            int workColor = importStatus.running() ? DEFAULT_COLOR : WARN;
            if (workLabel.isBlank()) {
                workLabel = refreshStatusText(refreshStatus);
                workColor = refreshStatus.canceling() ? WARN : DEFAULT_COLOR;
            }
            int workLabelW = workLabel.isBlank() ? 0 : 160;
            drawText(graphics, summary, listX() + 12, LIST_TOP + 10, TEXT, false, listWidth() - 24 - workLabelW);
            if (!workLabel.isBlank()) drawText(graphics, workLabel, listX() + listWidth() - workLabelW - 12, LIST_TOP + 10, workColor, false, workLabelW);
            renderWorkProgress(graphics, refreshStatus, importStatus);
            UiScissorStack.global().push(graphics, UiBounds.of(proxyRowX(), proxyRowsTop(), Math.max(0, proxyRowRight() - proxyRowX()), Math.max(0, proxyRowsBottom() - proxyRowsTop())));
            try {
                for (ProxyRow row : proxyRows) renderProxyRow(graphics, row, virtualMouseX, virtualMouseY);
            } finally {
                UiScissorStack.global().pop(graphics);
            }
            if (total == 0) {
                drawText(graphics, "No proxies saved yet. Add one manually or import a .txt list.", listX() + 12, proxyRowsTop() + 12, MUTED, false, listWidth() - 24);
            } else if (proxies.isEmpty()) {
                drawText(graphics, "No proxies match the current search or filter.", listX() + 12, proxyRowsTop() + 12, MUTED, false, listWidth() - 24);
            }
            for (CompactOverlayButton button : buttons) {
                CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
            }
            if (!compactLayout()) {
                CompactScrollbar.Metrics scrollbar = proxyScrollbarMetrics(displayProxies().size());
                CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), proxyScrollbarDragging);
            }
        } finally {
        }

        super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        if (importChooserOpen) {
            renderImportChooser(graphics, virtualMouseX, virtualMouseY);
        }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    private void renderProxyRow(GuiGraphicsExtractor graphics, ProxyRow row, int mouseX, int mouseY) {
        AutismProxy proxy = row.proxy;
        boolean selected = proxy.equals(selectedProxy);
        int x = proxyRowX();
        int y = row.y;
        int w = proxyRowWidth();
        int fill = proxy.enabled ? successColor(0x3324D86A) : selected ? outlineColor(0x242B1A1D) : 0x18111113;

        UiRenderer.rect(graphics, UiBounds.of(x, y, w, ROW_HEIGHT - 4), fill);
        if (proxy.enabled) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_HEIGHT - 4), successColor(BORDER_ACTIVE));
        else if (selected) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_HEIGHT - 4), outlineColor(0xFF5A3038));
        if (row.activeSection()) {

            UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_HEIGHT - 4), successColor(BORDER_ACTIVE));
            UiRenderer.rect(graphics, UiBounds.of(x, y + ROW_HEIGHT - 2, w, 1), outlineColor(BORDER));
        }

        String name = proxy.displayName().isBlank() ? "(unnamed)" : proxy.displayName();
        boolean hasAuth = proxy.username != null && !proxy.username.isBlank();
        String address = proxy.type + "  " + proxy.address + ":" + proxy.port + (hasAuth ? "  Auth" : "");
        String region = proxy.geoLabel();
        String status = statusText(proxy);
        int nameX = row.toggleButton.getX() + row.toggleButton.getWidth() + 12;
        int metaRight = row.checkButton.getX() - 8;
        int metaW = Math.min(96, Math.max(48, metaRight - nameX - 44));
        int metaX = Math.max(nameX + 36, metaRight - metaW);
        int textRight = Math.max(nameX + 24, metaX - 8);
        int textW = Math.max(20, textRight - nameX);
        int metaTextW = Math.max(20, metaRight - metaX);
        drawText(graphics, name, nameX, y + 6, proxy.enabled ? SUCCESS : TEXT, false, textW);
        drawText(graphics, address, nameX, y + 18, MUTED, false, textW);
        drawText(graphics, region, metaX, y + 6, proxy.geoColor(), false, metaTextW);
        drawText(graphics, status, metaX, y + 18, proxy.status.color(), false, metaTextW);
        CompactOverlayButton.renderStyled(graphics, this.font, row.toggleButton, mouseX, mouseY);
        CompactOverlayButton.renderStyled(graphics, this.font, row.checkButton, mouseX, mouseY);
        CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (importChooserOpen && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            importChooserOpen = false;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (importChooserOpen) {
            if (virtualEvent.button() == 0) {
                ensureImportChooserButtons();
                positionImportChooserButtons(importChooserRects());
                if (CompactOverlayButton.fireIfHit(importFileButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
                if (CompactOverlayButton.fireIfHit(importClipboardButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
                UiBounds card = importChooserRects()[0];
                if (!card.contains((int) virtualEvent.x(), (int) virtualEvent.y())) importChooserOpen = false;
            }
            return true;
        }
        if (virtualEvent.button() != 0) return super.mouseClicked(virtualEvent, doubleClick);
        if (compactLayout()) return super.mouseClicked(virtualEvent, doubleClick);
        CompactScrollbar.Metrics scrollbar = proxyScrollbarMetrics(displayProxies().size());
        if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            proxyScrollbarDragging = true;
            proxyScrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y()) ? Math.max(0, (int) Math.round(virtualEvent.y()) - scrollbar.thumbY()) : scrollbar.thumbHeight() / 2;
            proxyListScrollOffset = clampScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), proxyScrollbarGrabOffset), scrollbar.maxScroll());
            proxyListScroll.jumpTo(proxyListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            clearInputFocus();
            return true;
        }
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        }
        for (ProxyRow row : proxyRows) {
            if (handleProxyRowClick(row, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        }
        clearInputFocus();
        return super.mouseClicked(virtualEvent, doubleClick);
    }

    private boolean handleProxyRowClick(ProxyRow row, double x, double y, int button) {
        if (row == null) return false;
        if (overButton(row.toggleButton, x, y) || overButton(row.checkButton, x, y) || overButton(row.deleteButton, x, y)) {
            CompactOverlayButton.fireIfHit(row.toggleButton, x, y, button);
            CompactOverlayButton.fireIfHit(row.checkButton, x, y, button);
            CompactOverlayButton.fireIfHit(row.deleteButton, x, y, button);
            return true;
        }
        if (x >= proxyRowX() && x < proxyRowRight() && y >= row.y && y < row.y + ROW_HEIGHT - 3) {
            selectProxy(row.proxy);
            return true;
        }
        return false;
    }

    private boolean overButton(CompactOverlayButton button, double mouseX, double mouseY) {
        return button != null
            && button.visible
            && mouseX >= button.getX()
            && mouseX < button.getX() + button.getWidth()
            && mouseY >= button.getY()
            && mouseY < button.getY() + button.getHeight();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (proxyScrollbarDragging) {
            proxyScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (proxyScrollbarDragging) {
            CompactScrollbar.Metrics scrollbar = proxyScrollbarMetrics(displayProxies().size());
            proxyListScrollOffset = clampScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), proxyScrollbarGrabOffset), scrollbar.maxScroll());
            proxyListScroll.jumpTo(proxyListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            return true;
        }
        return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dx), AutismUiScale.toVirtual(dy));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        x = AutismUiScale.toVirtual(x);
        y = AutismUiScale.toVirtual(y);
        if (compactLayout()) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (x < listX() || x >= listX() + listWidth() || y < LIST_TOP || y >= LIST_TOP + listPanelHeight()) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        int maxScroll = proxyMaxScroll(displayProxies().size());
        if (maxScroll <= 0) return true;
        int next = proxyListScrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT;
        proxyListScrollOffset = clampScrollOffset(next, maxScroll);
        proxyListScroll.setTarget(proxyListScrollOffset, maxScroll);
        rebuildButtons();
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private void selectProxy(AutismProxy proxy) {
        selectedProxy = proxy;
        if (proxy != null) {
            nameField.setValue(safeTrim(proxy.name));
            addressField.setValue(safeTrim(proxy.address));
            portField.setValue(proxy.port <= 0 ? "" : Integer.toString(proxy.port));
            usernameField.setValue(safeTrim(proxy.username));
            passwordField.setValue(proxy.password == null ? "" : proxy.password);
            type = proxy.type == null ? AutismProxyType.Socks5 : proxy.type;
        }
        rebuildButtons();
    }

    private List<AutismProxy> filteredProxies() {
        AutismProxyManager manager = AutismProxyManager.get();
        long listRevision = manager.listRevision();
        long refreshRevision = manager.refreshStatus().revision();
        String query = normalize(searchQuery);
        boolean statusSensitive = filter != ProxyFilter.ALL || !query.isEmpty();
        boolean cacheValid = cachedFilter == filter
            && query.equals(cachedSearchQuery)
            && cachedListRevision == listRevision
            && (!statusSensitive || cachedRefreshRevision == refreshRevision);
        if (cacheValid) return cachedFilteredProxies;

        List<AutismProxy> proxies = manager.all();
        List<AutismProxy> filtered;
        if (query.isEmpty() && filter == ProxyFilter.ALL) {
            filtered = proxies;
        } else {
            filtered = new ArrayList<>();
            for (AutismProxy proxy : proxies) {
                if (proxy == null || !matchesFilter(proxy)) continue;
                if (!query.isEmpty() && !matchesSearch(proxy, query)) continue;
                filtered.add(proxy);
            }
        }
        cachedFilteredProxies = filtered;
        cachedSearchQuery = query;
        cachedFilter = filter;
        cachedListRevision = listRevision;
        cachedRefreshRevision = refreshRevision;
        return cachedFilteredProxies;
    }

    private AutismProxy activeProxy() {
        AutismProxyManager manager = AutismProxyManager.get();
        long revision = manager.listRevision();
        if (revision != cachedActiveRevision) {
            cachedActiveProxy = manager.getEnabled();
            cachedActiveRevision = revision;
        }
        return cachedActiveProxy;
    }

    private List<AutismProxy> displayProxies() {
        List<AutismProxy> filtered = filteredProxies();
        AutismProxy active = activeProxy();
        if (filtered == cachedDisplaySource && active == cachedDisplayActive) return cachedDisplayProxies;
        List<AutismProxy> display = filtered;
        if (active != null && filtered.indexOf(active) > 0) {
            display = new ArrayList<>(filtered.size() + 1);
            display.add(active);
            display.addAll(filtered);
        }
        cachedDisplaySource = filtered;
        cachedDisplayActive = active;
        cachedDisplayProxies = display;
        return display;
    }

    private boolean matchesFilter(AutismProxy proxy) {
        return switch (filter) {
            case ALL -> true;
            case ALIVE -> proxy.status == AutismProxy.Status.ALIVE;
            case DEAD -> proxy.status == AutismProxy.Status.DEAD;
            case UNCHECKED -> proxy.status == AutismProxy.Status.UNCHECKED || proxy.status == AutismProxy.Status.CHECKING;
            case ENABLED -> proxy.enabled;
        };
    }

    private boolean matchesSearch(AutismProxy proxy, String query) {
        String haystack = normalize(proxy.displayName() + " " + proxy.address + " " + proxy.port + " " + proxy.type + " " + proxy.status + " " + safeTrim(proxy.username) + " " + proxy.geoSearchText());
        return haystack.contains(query);
    }

    private String statusText(AutismProxy proxy) {
        if (proxy == null) return "";
        return switch (proxy.status) {
            case ALIVE -> proxy.latency + "ms";
            case DEAD -> "Dead";
            case CHECKING -> "Checking";
            case UNCHECKED -> "Unchecked";
        };
    }

    private String refreshStatusText(AutismProxyManager.RefreshStatus status) {
        if (status == null) return "";
        if (status.running()) return "Refreshing " + status.checked() + "/" + status.total();
        if (status.canceling()) return "Canceling";
        return "";
    }

    private String importStatusText(AutismProxyManager.ImportStatus status) {
        if (status == null) return "";
        if (status.running()) return "Importing " + status.linesRead() + " +" + status.candidates();
        if (status.canceling()) return "Canceling import";
        return "";
    }

    private void renderWorkProgress(GuiGraphicsExtractor graphics, AutismProxyManager.RefreshStatus refreshStatus, AutismProxyManager.ImportStatus importStatus) {
        boolean importing = importStatus != null && importStatus.running();
        boolean refreshing = refreshStatus != null && refreshStatus.running();
        if (!importing && !refreshing) return;
        int trackX = listX() + 12;
        int trackY = LIST_TOP + LIST_HEADER_HEIGHT - 5;
        int trackW = Math.max(1, listWidth() - 24);
        UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, trackW, 2), outlineColor(0x55332428));
        if (refreshing) {
            int total = Math.max(1, refreshStatus.total());
            int fillW = Math.max(1, Math.min(trackW, Math.round(trackW * Math.max(0, refreshStatus.checked()) / (float) total)));
            UiRenderer.rect(graphics, UiBounds.of(trackX, trackY, fillW, 2), DEFAULT_COLOR);
            return;
        }
        int sweepW = Math.max(24, trackW / 4);
        int range = Math.max(1, trackW + sweepW);
        int offset = (int) ((System.currentTimeMillis() / 8L) % range) - sweepW;
        int fillX = Math.max(trackX, trackX + offset);
        int fillRight = Math.min(trackX + trackW, trackX + offset + sweepW);
        if (fillRight > fillX) UiRenderer.rect(graphics, UiBounds.of(fillX, trackY, fillRight - fillX, 2), DEFAULT_COLOR);
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelWidth(), PANEL_MARGIN);
    }

    private int panelWidth() {
        return DirectLayout.fitPanelDimension(screenWidth(), PANEL_MARGIN, PANEL_WIDTH);
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listWidth() {
        return Math.max(1, panelWidth() - 20);
    }

    private int listPanelHeight() {
        return Math.max(1, screenHeight() - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    private int proxyRowsTop() {
        return LIST_TOP + LIST_HEADER_HEIGHT;
    }

    private int proxyRowsBottom() {
        return LIST_TOP + listPanelHeight() - 6;
    }

    private int proxyViewportHeight() {
        return Math.max(ROW_HEIGHT, Math.max(1, proxyRowsBottom() - proxyRowsTop()));
    }

    private int proxyMaxScroll(int rows) {
        return Math.max(0, rows * ROW_HEIGHT - proxyViewportHeight());
    }

    private CompactScrollbar.Metrics proxyScrollbarMetrics(int rows) {
        int contentPixels = Math.max(0, rows) * ROW_HEIGHT;
        int trackX = listX() + listWidth() - 8;
        int trackY = proxyRowsTop();
        int trackHeight = proxyViewportHeight();
        int maxScroll = proxyMaxScroll(rows);
        proxyListScrollOffset = clampScrollOffset(proxyListScrollOffset, maxScroll);
        proxyListScroll.jumpTo(proxyListScrollOffset, maxScroll);
        return CompactScrollbar.compute(contentPixels, proxyViewportHeight(), trackX, trackY, LIST_SCROLLBAR_WIDTH, trackHeight, proxyListScrollOffset);
    }

    private int proxyRowX() {
        return listX() + 8;
    }

    private int proxyRowRight() {
        return listX() + listWidth() - 8 - LIST_SCROLLBAR_GUTTER;
    }

    private int proxyRowWidth() {
        return Math.max(1, proxyRowRight() - proxyRowX());
    }

    private boolean compactLayout() {
        return panelWidth() < 360 || screenHeight() < LIST_TOP + 44;
    }

    private void setFormFieldsVisible(boolean visible) {
        if (nameField != null) nameField.visible = visible;
        if (addressField != null) addressField.visible = visible;
        if (portField != null) portField.visible = visible;
        if (usernameField != null) usernameField.visible = visible;
        if (passwordField != null) passwordField.visible = visible;
        if (searchField != null) searchField.visible = visible;
    }

    private int[] scaledWidths(int availableWidth, int gap, int... preferredWidths) {
        int[] widths = new int[preferredWidths.length];
        int gaps = Math.max(0, preferredWidths.length - 1) * Math.max(0, gap);
        int preferredTotal = 0;
        for (int preferred : preferredWidths) preferredTotal += Math.max(1, preferred);
        int usable = Math.max(preferredWidths.length, availableWidth - gaps);
        int assigned = 0;
        for (int i = 0; i < preferredWidths.length; i++) {
            widths[i] = i == preferredWidths.length - 1
                ? Math.max(1, usable - assigned)
                : Math.max(1, Math.round(Math.max(1, preferredWidths[i]) * usable / (float) preferredTotal));
            assigned += widths[i];
        }
        return widths;
    }

    private void clearInputFocus() {
        if (nameField != null) nameField.setFocused(false);
        if (addressField != null) addressField.setFocused(false);
        if (portField != null) portField.setFocused(false);
        if (usernameField != null) usernameField.setFocused(false);
        if (passwordField != null) passwordField.setFocused(false);
        if (searchField != null) searchField.setFocused(false);
        this.setFocused(null);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
        drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
        Font renderer = this.font;
        Identifier font = THEME.fontFor(UiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE && !center) {
            UiText.drawFitted(graphics, renderer, value, font, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        if (maxWidth != Integer.MAX_VALUE) value = UiText.trimToWidth(renderer, value, maxWidth, font, color);
        int w = UiText.width(renderer, value, font, color);
        int drawX = center ? x - w / 2 : x;
        UiText.draw(graphics, renderer, value, font, color, drawX, y, false);
    }

    private static int clampScrollOffset(int value, int maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    private static String normalize(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private static int outlineColor(int argb) {
        return autismclient.util.AutismTheme.recolor(argb, autismclient.util.AutismTheme.Channel.OUTLINE);
    }

    private static int successColor(int argb) {
        return autismclient.util.AutismTheme.recolor(argb, autismclient.util.AutismTheme.Channel.SUCCESS);
    }

    private enum ProxyFilter {
        ALL,
        ALIVE,
        DEAD,
        UNCHECKED,
        ENABLED
    }

    private record ProxyRow(AutismProxy proxy, int y, boolean activeSection, CompactOverlayButton toggleButton, CompactOverlayButton checkButton, CompactOverlayButton deleteButton) {
    }
}
