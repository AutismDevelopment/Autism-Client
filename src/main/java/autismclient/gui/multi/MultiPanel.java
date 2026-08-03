package autismclient.gui.multi;

import autismclient.gui.screen.AutismAccountsScreen;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismAccountType;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismTheme;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiManualPackets;
import autismclient.util.multi.MultiPacketPolicy;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import autismclient.util.multi.MultiQuickAction;
import autismclient.util.multi.MultiSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class MultiPanel {
    public interface Host {
        void manageAccounts();
        void pickManualProxy(String title, String serverAddress, String selectedProxyId,
                             Map<String, Integer> profileUsage, Consumer<String> callback);
        void openGui(String accountId);
        boolean isGuiOpen(String accountId);

        void toggleTakeover(String accountId);
        boolean isTakeoverActive(String accountId);

        boolean canTakeover(String accountId);
        void openSharedGui();
        boolean isSharedGuiOpen();
        void pickQuickPacket(Consumer<Class<? extends Packet<?>>> callback);
        void editBlocklist(MultiPacketPolicy.Direction direction,
                           Collection<Class<? extends Packet<?>>> selected,
                           BiConsumer<Class<? extends Packet<?>>, Boolean> callback);
        void editMacro(AutismMacro macro, Consumer<AutismMacro> onSaved);
        void editFormValues(MultiProfile profile, Set<String> selectedAccounts, Consumer<MultiProfile> onSaved);
        void editAutoAccept(autismclient.util.multi.MultiAutoAccept config,
                            Consumer<autismclient.util.multi.MultiAutoAccept> onSaved, boolean live);
    }

    private enum Tab { SETUP, ACCOUNTS, CONSOLE, ACTIONS, MACROS }

    private static final int CARD = 0x2A121214;
    private static final int DISABLED = 0x18111113;
    private static final int BORDER_STOCK = 0xFF463A40;
    private static final int ACCENT_STOCK = 0xFFFF4A4A;
    private static final int SUCCESS_STOCK = 0xFF35D873;
    private static final int DANGER_STOCK = 0xFFFF5B5B;
    private static final int TEXT_STOCK = 0xFFF2F2F2;
    private static final int MUTED_STOCK = 0xFF9A9A9A;
    private static final int WARN_STOCK = 0xFFFFC857;
    private static final int ROW = 20;
    private static final int DETAIL_ROW = 54;
    private static final int MACRO_SUB_H = 11;

    private static final int SELECTED_PINK = 0xFFFF66CC;
    private static final int DISCONNECTED_RED = 0xFFFF5A5A;
    private static final int CONNECTING_YELLOW = 0xFFF2C54B;
    private static final Identifier HEART_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier FOOD_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
    private static final int CHAT_LINE_HEIGHT = 11;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int[] PING_OPTIONS = {50, 100, 200, 300, 500, 1000};
    private static final List<String> PACING_OPTIONS = List.of("Gentle", "Balanced", "Fast", "Immediate", "Custom");
    private static final List<String> PROXY_MODE_OPTIONS = List.of("Off", "Auto", "Manual");

    private final Minecraft mc = Minecraft.getInstance();
    private final Host host;
    private final Font font;
    private final CompactTheme theme = new CompactTheme();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<CompactTextInput> activeInputs = new ArrayList<>();
    private final EnumSet<AutismAccountType> accountFilters = EnumSet.allOf(AutismAccountType.class);
    private final LinkedHashSet<String> selectedSessionIds = new LinkedHashSet<>();
    private final List<CompactDropdown> frameDropdowns = new ArrayList<>();
    private final CompactDropdown pacingDropdown;
    private final CompactDropdown proxyModeDropdown;
    private final CompactDropdown autoPingDropdown;

    private final CompactTextInput profileName = field("Profile name", 64);
    private final CompactTextInput serverAddress = field("server.example:25565", 160);
    private final CompactTextInput customConcurrency = field("Concurrency", 4);
    private final CompactTextInput customDelay = field("Delay ms", 5);
    private final CompactTextInput autoPing = field("Max ping", 4);
    private final CompactTextInput accountSearch = field("Search accounts", 48);
    private final CompactTextInput chatInput = field("Chat or /command", 256);

    private final List<String> chatSuggests = new ArrayList<>();
    private final List<int[]> chatSuggestRects = new ArrayList<>();
    private int chatSuggestIndex;
    private String chatSuggestFor;
    private String chatSuggestApplied;
    private int chatSuggestStart;
    private int chatSuggestLen;
    private String chatLastReqCmd;
    private long chatLastReqAt;
    private final CompactTextInput macroSearch = field("Search macros", 48);
    private final CompactTextInput quickName = field("Action name", 32);
    private final CompactTextInput quickArgs = field("Packet args", 2048);

    private Tab tab = Tab.SETUP;
    private MultiProfile draft;
    private long lastAutoSaveCheckAt;
    private String lastCommittedState;
    private String selectedProfileId = "";
    private String selectedMacroName = "";
    private String pendingProfileDelete = "";
    private String pendingMacroDelete = "";

    private boolean assignPopupOpen;
    private final LinkedHashSet<String> assignSelected = new LinkedHashSet<>();
    private int assignScroll;
    private String status = "";
    private String macroRowTooltip;
    private int statusColor = MUTED_STOCK;
    private long savedFlashAt;
    private int profileScroll;
    private int setupDetailScroll;
    private int accountScroll;
    private int sessionScroll;
    private boolean detailsOpen;
    private int chatScrollLines;
    private int macroScroll;
    private int macroProgressScroll;

    private String scrollbarDragId;
    private int scrollbarGrab;
    private int scrollbarTrackY;
    private int scrollbarTravel;
    private int scrollbarMax;
    private java.util.function.IntConsumer scrollbarSetter;
    private int quickEdit = -1;
    private int quickStep;
    private MultiQuickAction quickDraft;
    private float delta;
    private int lastMx, lastMy;
    private int bx, by, bw, bh;
    private int contentTop, contentBottom;
    private Viewport profileViewport = Viewport.NONE;
    private Viewport setupDetailViewport = Viewport.NONE;
    private Viewport accountViewport = Viewport.NONE;
    private Viewport sessionViewport = Viewport.NONE;
    private Viewport chatViewport = Viewport.NONE;
    private Viewport macroViewport = Viewport.NONE;
    private Viewport macroProgressViewport = Viewport.NONE;
    private Viewport interactionClip = Viewport.NONE;
    private final List<ChatHitRow> chatHitRows = new ArrayList<>();

    private final List<RowHit> sessionRowHits = new ArrayList<>();
    private String contextMenuId = "";
    private int contextMenuX;
    private int contextMenuY;
    private final List<int[]> contextItemRects = new ArrayList<>();

    private final MultiChatSelection chatSel = new MultiChatSelection();
    private boolean chatSelectingPress;
    private List<MultiSession.Snapshot> frameSnapshots = List.of();
    private MultiProfile frameProfile;
    private int frameReadyCount;
    private final Set<String> frameLiveIds = new java.util.HashSet<>(MultiProfile.MAX_SESSIONS * 2);
    private long cachedProfilesRevision = Long.MIN_VALUE;
    private List<MultiProfile> cachedProfiles = List.of();
    private long cachedActiveUiRevision = Long.MIN_VALUE;
    private long cachedActiveSessionRevision = Long.MIN_VALUE;
    private MultiProfile cachedActiveProfile;
    private long cachedChatRevision = Long.MIN_VALUE;
    private String cachedChatScope = "";
    private int cachedChatWidth = -1;
    private int cachedChatTotal = -1;
    private List<MultiChatPresentation.VisualRow> cachedChatRows = List.of();
    private int chatMessageX;
    private int chatMessageRight;
    private int chatWrapWidth;
    private long cachedMacroRevision = Long.MIN_VALUE;
    private String cachedMacroQuery = "";
    private List<AutismMacro> cachedMacros = List.of();
    private int cachedAccountSourceSize = -1;
    private long cachedAccountSourceRevision = Long.MIN_VALUE;
    private List<AutismAccount> cachedAccountSource = List.of();
    private String cachedAccountChoiceQuery = "";
    private int cachedAccountChoiceFilterMask = Integer.MIN_VALUE;
    private MultiProfile cachedAccountChoiceProfile;
    private int cachedAccountChoiceSessionCount = -1;
    private long cachedAccountChoiceUiRevision = Long.MIN_VALUE;
    private List<AccountChoice> cachedAccountChoices = List.of();
    private long cachedCompatibilityRevision = Long.MIN_VALUE;
    private String cachedCompatibilityName = "";
    private List<String> cachedCompatibility = List.of();

    public MultiPanel(Host host, Font font) {
        this.host = host;
        this.font = font;
        pacingDropdown = new CompactDropdown(0, 0, 1, 1, PACING_OPTIONS, 0, this::setPacing);
        proxyModeDropdown = new CompactDropdown(0, 0, 1, 1, PROXY_MODE_OPTIONS, 0, this::setProxyMode);
        autoPingDropdown = new CompactDropdown(0, 0, 1, 1, pingOptionLabels(), 1, this::setAutoPing);
        chatInput.setOnSubmit(this::sendChat);
        chatInput.setHistoryNavigationEnabled(true);
        chatInput.setHistoryProvider(() -> MultiManager.get().commandHistory());
        loadInitialDraft();
    }

    private CompactTextInput field(String placeholder, int maxLength) {
        return new CompactTextInput().setPlaceholder(placeholder).setMaxLength(maxLength).setFieldHeight(16);
    }

    public void opened() {

        cachedAccountSourceSize = -1;
        cachedAccountChoiceProfile = null;
        if (!MultiManager.get().isActive()) {

            MultiProfile shared = MultiProfileManager.get().find(MultiProfileManager.get().selectedId());
            if (shared != null && !shared.toTag().toString().equals(draft.toTag().toString())) {
                loadProfile(shared);
            }
        }
        if (!MultiManager.get().isActive() && draft.serverAddress.isBlank()) useCurrentServer();
        if (!MultiManager.get().isActive() && draft.sessions.isEmpty()) selectFirstEligibleAccount();
    }

    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float partial) {
        refreshTheme();
        autoSaveTick();
        hotspots.clear();
        activeInputs.clear();
        macroRowTooltip = null;
        boolean dropdownOpen = CompactDropdown.isMenuOpen(frameDropdowns);
        frameDropdowns.clear();
        delta = partial;
        int contentMx = dropdownOpen ? Integer.MIN_VALUE : mx;
        int contentMy = dropdownOpen ? Integer.MIN_VALUE : my;
        lastMx = contentMx;
        lastMy = contentMy;
        bx = x; by = y; bw = w; bh = h;
        contentTop = y + 25;
        contentBottom = y + h - 5;
        profileViewport = setupDetailViewport = accountViewport = sessionViewport = chatViewport = Viewport.NONE;
        macroViewport = macroProgressViewport = interactionClip = Viewport.NONE;
        MultiManager manager = MultiManager.get();
        if (manager.isActive()) {
            frameSnapshots = manager.snapshots();
            frameProfile = tab == Tab.CONSOLE ? null : activeProfileSnapshot(manager);
            frameLiveIds.clear();
            frameReadyCount = 0;
            for (MultiSession.Snapshot snapshot : frameSnapshots) {
                frameLiveIds.add(snapshot.accountId());
                if (snapshot.ready()) frameReadyCount++;
            }
            selectedSessionIds.retainAll(frameLiveIds);
        } else {
            frameSnapshots = List.of();
            frameProfile = null;
            frameReadyCount = 0;
            selectedSessionIds.clear();
            chatScrollLines = 0;
        }

        renderTabs(g, x + 5, y + 4, w - 10, contentMx, contentMy);
        switch (tab) {
            case SETUP -> renderSetup(g, contentMx, contentMy);
            case ACCOUNTS -> renderAccounts(g, contentMx, contentMy);
            case CONSOLE -> renderConsole(g, contentMx, contentMy);
            case ACTIONS -> renderActions(g, contentMx, contentMy);
            case MACROS -> renderMacros(g, contentMx, contentMy);
        }
        if (quickEdit >= 0) renderQuickEditor(g, contentMx, contentMy);
        else if (!pendingMacroDelete.isBlank()) renderMacroDelete(g, contentMx, contentMy);
        else if (assignPopupOpen) renderAssignPopup(g, contentMx, contentMy);
        CompactDropdown.renderOpenMenu(g, font, frameDropdowns, mx, my);
        if (macroRowTooltip != null && autismclient.util.AutismConfig.getGlobal().multiShowTooltips) {
            MultiTooltip.render(g, font, macroRowTooltip, mx, my);
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        Tab[] tabs = Tab.values();
        String[] labels = {"Setup", "Accounts", "Console", "Actions", "Macros"};
        int gap = 3;
        int each = (w - gap * (tabs.length - 1)) / tabs.length;
        int cx = x;
        for (int i = 0; i < tabs.length; i++) {
            int cw = i == tabs.length - 1 ? x + w - cx : each;
            Tab target = tabs[i];
            button(g, cx, y, cw, 17, labels[i], tab == target ? success() : border(), text(), mx, my,
                () -> { tab = target; clearFocus(); });
            cx += cw + gap;
        }
    }

    private void renderSetup(GuiGraphicsExtractor g, int mx, int my) {
        boolean active = MultiManager.get().isActive();
        int pad = 6;
        int leftW = Math.max(105, Math.min(142, bw / 3));
        int lx = bx + pad;
        int rx = lx + leftW + 7;
        int rw = bx + bw - pad - rx;
        int top = contentTop;

        draw(g, "Saved Profiles", lx, top, text(), false, leftW);
        int listTop = top + 12;
        int listBottom = contentBottom - 42;
        profileViewport = new Viewport(lx, listTop, leftW, Math.max(1, listBottom - listTop));
        List<MultiProfile> profiles = savedProfiles();
        int maxScroll = Math.max(0, profiles.size() * ROW - profileViewport.h());
        profileScroll = clamp(profileScroll, 0, maxScroll);
        UiScissorStack.global().push(g, profileViewport.bounds());
        interactionClip = profileViewport;
        int py = listTop - profileScroll;
        for (MultiProfile profile : profiles) {
            if (py + ROW > listTop && py < listBottom) {
                boolean selected = profile.id.equals(selectedProfileId);
                row(g, lx, py, leftW, ROW - 2, profile.name, selected ? success() : border(), mx, my,
                    active ? null : () -> selectProfileRow(profile));
            }
            py += ROW;
        }
        interactionClip = Viewport.NONE;
        UiScissorStack.global().pop(g);
        scrollbar(g, profileViewport, profileScroll, maxScroll, mx, my, "profile", v -> profileScroll = v);
        button(g, lx, contentBottom - 36, leftW, 16, "New", border(), text(), mx, my, active ? null : this::newProfile);
        button(g, lx, contentBottom - 17, leftW, 16, "Duplicate", border(), text(), mx, my, active ? null : this::duplicateProfile);

        draw(g, active ? "Active Batch" : "Profile Setup", rx, top, active ? success() : text(), false, rw);

        if (!active && System.currentTimeMillis() - savedFlashAt < 1500L) {
            draw(g, "Saved", rx + rw - 34, top, success(), false, 34);
        }
        int detailTop = top + 13;
        setupDetailViewport = new Viewport(rx, detailTop, rw, Math.max(1, contentBottom - detailTop));
        int detailHeight = setupDetailContentHeight(active);
        setupDetailScroll = clamp(setupDetailScroll, 0, Math.max(0, detailHeight - setupDetailViewport.h()));
        UiScissorStack.global().push(g, setupDetailViewport.bounds());
        interactionClip = setupDetailViewport;
        int cy = detailTop - setupDetailScroll;
        if (active) {
            MultiProfile current = frameProfile;
            draw(g, current == null ? "Multi" : current.name, rx, cy, text(), false, rw);
            cy += 13;
            draw(g, current == null ? "" : current.serverAddress, rx, cy, muted(), false, rw);
            cy += 18;
            draw(g, frameReadyCount + "/" + frameSnapshots.size() + " ready", rx, cy, success(), false, rw);
            cy += 22;
            button(g, rx, cy, rw, 18, "Disconnect All", danger(), text(), mx, my,
                () -> { MultiManager.get().disconnectAll("Disconnected by user"); status("Disconnected", MUTED_STOCK); });
            cy += 24;
            draw(g, "Disconnect to edit server, accounts, proxies, pacing, or mode.", rx, cy, muted(), false, rw);
            cy += 12;
        } else {
            place(g, profileName, rx, cy, rw); cy += 20;
            place(g, serverAddress, rx, cy, Math.max(10, rw - 82));
            button(g, rx + rw - 78, cy, 78, 16, "Use Current", border(), text(), mx, my, this::useCurrentServer);
            cy += 21;
            int half = (rw - 4) / 2;
            if (draft.proxyMode == MultiProfile.ProxyMode.Auto) {
                int third = Math.max(1, (rw - 8) / 3);
                configureSetupDropdown(pacingDropdown, rx, cy, third, draft.pacing.ordinal(),
                    "Join speed: " + draft.pacing.name());
                configureSetupDropdown(proxyModeDropdown, rx + third + 4, cy, third, draft.proxyMode.ordinal(),
                    "Proxy: " + proxyModeLabel());
                configureSetupDropdown(autoPingDropdown, rx + (third + 4) * 2, cy,
                    rw - (third + 4) * 2, pingIndex(draft.autoMaxPingMs), "<=" + draft.autoMaxPingMs + "ms");
            } else {
                configureSetupDropdown(pacingDropdown, rx, cy, half, draft.pacing.ordinal(),
                    "Join speed: " + draft.pacing.name());
                configureSetupDropdown(proxyModeDropdown, rx + half + 4, cy, rw - half - 4,
                    draft.proxyMode.ordinal(), "Proxy: " + proxyModeLabel());
            }
            cy += 21;
            CompactDropdown.renderButtons(g, font, frameDropdowns, mx, my);

            button(g, rx, cy, rw, 17, "Passwords (login)", border(), text(), mx, my, this::editFormValues);
            cy += 21;
            if (draft.pacing == MultiProfile.Pacing.Custom) {
                place(g, customConcurrency, rx, cy, half);
                place(g, customDelay, rx + half + 4, cy, rw - half - 4);
                cy += 21;
            }
            draw(g, draft.sessions.size() + " accounts selected", rx, cy + 3, draft.sessions.isEmpty() ? danger() : muted(), false, rw);
            cy += 20;

            int halfBtn = (rw - 4) / 2;
            button(g, rx, cy, halfBtn, 17, "Reset", border(), text(), mx, my, this::resetDraft);
            button(g, rx + halfBtn + 4, cy, rw - halfBtn - 4, 17,
                pendingProfileDelete.equals(selectedProfileId) ? "Confirm" : "Delete", danger(), text(), mx, my, this::deleteProfile);
            cy += 22;
            button(g, rx, cy, rw, 19, "Launch Multi", success(), text(), mx, my, this::launch);
            cy += 22;
        }
        if (!status.isBlank()) draw(g, status, rx, cy, themedStatus(), false, rw);
        interactionClip = Viewport.NONE;
        UiScissorStack.global().pop(g);
        scrollbar(g, setupDetailViewport, setupDetailScroll, Math.max(0, detailHeight - setupDetailViewport.h()), mx, my, "setupDetail", v -> setupDetailScroll = v);
    }

    private int setupDetailContentHeight(boolean active) {
        if (active) return 89 + (status.isBlank() ? 0 : 12);
        int height = 168;
        if (draft.pacing == MultiProfile.Pacing.Custom) height += 21;
        if (!status.isBlank()) height += 12;
        return height;
    }

    private void renderAccounts(GuiGraphicsExtractor g, int mx, int my) {
        boolean active = MultiManager.get().isActive();
        MultiProfile shownProfile = active && frameProfile != null ? frameProfile : draft;
        int x = bx + 6, w = bw - 12, y = contentTop;
        place(g, accountSearch, x, y, Math.max(10, w - 102));
        button(g, x + w - 98, y, 98, 16, "Manage Accounts", border(), text(), mx, my, host::manageAccounts);
        y += 20;
        AutismAccountType[] types = {AutismAccountType.Cracked, AutismAccountType.Session,
            AutismAccountType.Microsoft, AutismAccountType.TheAltening, AutismAccountType.Generated};
        String[] labels = {"Cracked", "Session", "Microsoft", "Altening", "Generated"};
        int gap = 3, each = (w - gap * 4) / 5, cx = x;
        for (int i = 0; i < types.length; i++) {
            AutismAccountType type = types[i];
            toggleButton(g, cx, y, i == types.length - 1 ? x + w - cx : each, 16, labels[i],
                accountFilters.contains(type), mx, my,
                () -> { if (!accountFilters.remove(type)) accountFilters.add(type); accountScroll = 0; });
            cx += each + gap;
        }
        y += 20;

        if (!active && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual) {
            String commonProxy = commonManualProxyId(shownProfile);
            Map<String, Integer> profileUsage = manualProxyUsage(shownProfile);
            String bulkLabel = commonProxy == null
                ? "Set All Proxies (Mixed)..."
                : "Set All Proxies: " + proxyLabel(shownProfile, commonProxy);
            button(g, x, y, w, 16, bulkLabel, border(), text(), mx, my,
                () -> host.pickManualProxy("Proxy for all selected accounts", shownProfile.serverAddress,
                    commonProxy, profileUsage, this::setAllProxies));
            y += 20;
        }
        int listH = Math.max(20, contentBottom - y);
        accountViewport = new Viewport(x, y, w, listH);
        List<AccountChoice> choices = filteredAccounts(shownProfile);
        int maxScroll = Math.max(0, choices.size() * ROW - listH);
        accountScroll = clamp(accountScroll, 0, maxScroll);
        UiScissorStack.global().push(g, accountViewport.bounds());
        interactionClip = accountViewport;
        int ry = y - accountScroll;
        Map<String, Integer> profileUsage = !active && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual
            ? manualProxyUsage(shownProfile) : Map.of();
        for (AccountChoice choice : choices) {
            if (ry + ROW > y && ry < y + listH) {
                MultiProfile.SessionSpec spec = selectedSpec(shownProfile, choice.id());
                boolean blocked = choice.current();
                boolean selected = spec != null;
                int proxyW = Math.min(116, Math.max(76, w / 3));
                int accountW = w - proxyW - 4;
                String label = choice.label() + (blocked ? " (Current)" : "");
                row(g, x, ry, accountW, ROW - 2, label,
                    blocked ? danger() : selected ? success() : border(), mx, my,
                    active || blocked ? null : () -> toggleAccount(choice.id()));
                String proxy = spec == null ? "-" : proxyLabel(shownProfile, spec.proxyId());
                boolean pickable = !active && spec != null && !choice.current()
                    && shownProfile.proxyMode == MultiProfile.ProxyMode.Manual;
                if (pickable) {
                    button(g, x + accountW + 4, ry, proxyW, ROW - 2, proxy, border(), text(), mx, my,
                        () -> host.pickManualProxy("Proxy for " + choice.label(), shownProfile.serverAddress,
                            spec.proxyId(), profileUsage, proxyId -> setAccountProxy(choice.id(), proxyId)));
                } else {
                    row(g, x + accountW + 4, ry, proxyW, ROW - 2, proxy, border(), mx, my, null);
                }
            }
            ry += ROW;
        }
        interactionClip = Viewport.NONE;
        CompactDropdown.renderButtons(g, font, frameDropdowns, mx, my);
        UiScissorStack.global().pop(g);
        scrollbar(g, accountViewport, accountScroll, maxScroll, mx, my, "account", v -> accountScroll = v);
    }

    private void renderConsole(GuiGraphicsExtractor g, int mx, int my) {
        int x = bx + 6, y = contentTop, w = bw - 12, h = contentBottom - y;
        if (!MultiManager.get().isActive()) {
            emptyState(g, "No active batch", "Launch a profile from Setup.", x, y, w, h, mx, my, () -> tab = Tab.SETUP);
            return;
        }
        List<MultiSession.Snapshot> snapshots = frameSnapshots;
        boolean twoPane = w >= 410;
        int listW = twoPane ? Math.max(128, Math.min(158, w / 3)) : w;
        int listH = twoPane ? h : Math.min(76, Math.max(40, h / 3));
        renderSessionList(g, snapshots, x, y, listW, listH, mx, my);
        int chatX = twoPane ? x + listW + 6 : x;
        int chatY = twoPane ? y : y + listH + 6;
        int chatW = twoPane ? w - listW - 6 : w;
        int chatH = twoPane ? h : h - listH - 6;
        renderChat(g, chatX, chatY, chatW, chatH, mx, my);
        renderContextMenu(g, mx, my);
    }

    private void renderContextMenu(GuiGraphicsExtractor g, int mx, int my) {
        contextItemRects.clear();
        if (contextMenuId.isBlank()) return;
        String[] items = {"TPA to Me", "TPA to Bot", "Trade"};
        int itemH = 14;
        int menuW = 84;
        int menuH = items.length * itemH + 2;
        int menuX = Math.min(contextMenuX, bx + bw - menuW - 2);
        int menuY = Math.min(contextMenuY, by + bh - menuH - 2);
        UiRenderer.frame(g, UiBounds.of(menuX, menuY, menuW, menuH), 0xF0121216, accent());
        for (int i = 0; i < items.length; i++) {
            int ry = menuY + 1 + i * itemH;
            boolean hover = mx >= menuX && mx < menuX + menuW && my >= ry && my < ry + itemH;
            if (hover) UiRenderer.rect(g, UiBounds.of(menuX + 1, ry, menuW - 2, itemH), 0x33FFFFFF);
            draw(g, items[i], menuX + 5, ry + 3, hover ? text() : muted(), false, menuW - 8);
            contextItemRects.add(new int[]{menuX, ry, menuW, itemH, i});
        }
    }

    private void runContextAction(int index) {
        String id = contextMenuId;
        contextMenuId = "";
        if (id.isBlank()) return;
        MultiManager mm = MultiManager.get();
        String result = switch (index) {
            case 0 -> mm.tpaToMe(id);
            case 1 -> mm.tpaToBot(id);
            case 2 -> mm.tradeWith(id);
            default -> "";
        };
        if (!result.isBlank()) status(result, "Sent".equals(result) ? SUCCESS_STOCK : MUTED_STOCK);
    }

    private void renderSessionList(GuiGraphicsExtractor g, List<MultiSession.Snapshot> snapshots,
                                   int x, int y, int w, int h, int mx, int my) {
        draw(g, "Sessions " + frameReadyCount + "/" + snapshots.size(), x, y, text(), false, w - 60);

        toggleButton(g, x + w - 58, y - 1, 58, 11, "Details", detailsOpen, mx, my,
            () -> { detailsOpen = !detailsOpen; sessionScroll = 0; });

        toggleButton(g, x, y + 12, w, 16, "Shared GUI", host.isSharedGuiOpen(), mx, my, host::openSharedGui);
        int pitch = detailsOpen ? DETAIL_ROW : ROW;
        int top = y + 31, footer = y + h - 19, listH = Math.max(16, footer - top);
        sessionViewport = new Viewport(x, top, w, listH);

        int totalH = 0;
        for (MultiSession.Snapshot snapshot : snapshots) totalH += pitch + MACRO_SUB_H + guiExtra(snapshot);
        int maxScroll = Math.max(0, totalH - listH);
        sessionScroll = clamp(sessionScroll, 0, maxScroll);
        UiScissorStack.global().push(g, sessionViewport.bounds());
        interactionClip = sessionViewport;
        sessionRowHits.clear();
        int ry = top - sessionScroll;
        for (MultiSession.Snapshot snapshot : snapshots) {
            int guiExtra = guiExtra(snapshot);
            int frameH = pitch + MACRO_SUB_H + guiExtra - 2;
            if (ry + frameH > top && ry < top + listH) {
                int state = sessionColor(snapshot);
                boolean selected = selectedSessionIds.contains(snapshot.accountId());
                UiRenderer.frame(g, UiBounds.of(x, ry, w, frameH), selected ? tint(SELECTED_PINK, 0x35) : CARD,
                    selected ? SELECTED_PINK : state);
                hotspots.add(new Hotspot(x, ry, w, frameH, () -> selectSession(snapshot.accountId())));
                if (interactionAllowed(x, ry, w, frameH)) sessionRowHits.add(new RowHit(x, ry, w, frameH, snapshot.accountId()));
                int guiW = 29;
                int guiX = x + w - guiW - 3;
                int povW = 29;

                boolean povActive = host.isTakeoverActive(snapshot.accountId());
                boolean povOk = povActive || host.canTakeover(snapshot.accountId());
                int povX = povOk ? guiX - povW - 3 : guiX;
                String ping = snapshot.ping() >= 0 ? snapshot.ping() + "ms" : "--ms";
                int pingW = font.width(ping) + 5;
                int pingX = (povOk ? povX : guiX) - pingW - 2;
                int nameColor = state == DISCONNECTED_RED ? DISCONNECTED_RED : text();
                draw(g, snapshot.accountName(), x + 5, ry + 5, nameColor, false, Math.max(20, pingX - x - 7));
                draw(g, ping, pingX, ry + 5, snapshot.ping() >= 0 ? muted() : border(), false, pingW);
                if (povOk) {
                    toggleButton(g, povX, ry + 2, povW, 14, "POV", povActive, mx, my,
                        () -> host.toggleTakeover(snapshot.accountId()));
                }
                toggleButton(g, guiX, ry + 2, guiW, 14, "GUI",
                    host.isGuiOpen(snapshot.accountId()), mx, my,
                    () -> host.openGui(snapshot.accountId()));
                int macroY = ry + 17;
                drawMacroRow(g, snapshot, x + 5, macroY, w - 10);
                if (mx >= x && mx < x + w && my >= macroY - 1 && my < macroY + MACRO_SUB_H) {
                    macroRowTooltip = MultiMacroPresentation.tooltip(MultiManager.get(), snapshot);
                }
                if (guiExtra > 0) {
                    String gui = snapshot.openScreen();
                    String guiLabel = snapshot.customMenuOpen()
                        ? "GUI: CustomScreen"
                        : "GUI: " + MultiManager.singleLine(gui == null ? "" : gui, 40);
                    draw(g, guiLabel, x + 5, ry + 17 + MACRO_SUB_H, muted(), false, w - 10);
                }
                if (detailsOpen) drawSessionDetails(g, snapshot, x + 6, ry + 17 + MACRO_SUB_H + guiExtra, w - 12);
            }
            ry += pitch + MACRO_SUB_H + guiExtra;
        }
        interactionClip = Viewport.NONE;
        UiScissorStack.global().pop(g);
        scrollbar(g, sessionViewport, sessionScroll, maxScroll, mx, my, "session", v -> sessionScroll = v);
        int half = (w - 3) / 2;
        button(g, x, footer, half, 17, "Retry", border(), text(), mx, my, this::retrySelected);
        button(g, x + half + 3, footer, w - half - 3, 17, "Stop", danger(), text(), mx, my, this::stopSelectedSessions);
    }

    private static int guiExtra(MultiSession.Snapshot snapshot) {
        String gui = snapshot.openScreen();
        return snapshot.customMenuOpen() || (gui != null && !gui.isBlank()) ? 11 : 0;
    }

    private void drawMacroRow(GuiGraphicsExtractor g, MultiSession.Snapshot snapshot, int x, int y, int width) {
        String assignedName = MultiMacroPresentation.assignedName(MultiManager.get(), snapshot);
        String assigned = MultiMacroPresentation.assignedLabel(assignedName);
        String playingName = MultiMacroPresentation.playingName(snapshot);
        if (playingName.isBlank()) {
            draw(g, assigned, x, y, MultiMacroPresentation.ASSIGNED_GRAY, false, width);
            return;
        }
        String playing = MultiMacroPresentation.playingLabel(playingName);
        int gap = 7;
        int playingNatural = font.width(playing);
        int playingW = Math.min(Math.max(42, width * 3 / 5), Math.max(1, playingNatural));
        int assignedW = Math.max(0, width - playingW - gap);
        int cx = x;
        if (assignedW > 12) {
            draw(g, assigned, cx, y, MultiMacroPresentation.ASSIGNED_GRAY, false, assignedW);
            cx += assignedW + gap;
        }
        draw(g, playing, cx, y, MultiMacroPresentation.PLAYING_GREEN, false, Math.max(1, x + width - cx));
    }

    private void drawSessionDetails(GuiGraphicsExtractor g, MultiSession.Snapshot snapshot, int x, int y, int w) {
        int cx = x;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_SPRITE, cx, y - 1, 9, 9);
        cx += 11;
        String hp = trimStat(snapshot.health()) + "/" + trimStat(snapshot.maxHealth());
        draw(g, hp, cx, y, muted(), false, w);
        cx += font.width(hp) + 10;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_SPRITE, cx, y - 1, 9, 9);
        cx += 11;
        draw(g, snapshot.food() + "/20", cx, y, muted(), false, Math.max(1, x + w - cx));
        String held = snapshot.heldItem() == null || snapshot.heldItem().isBlank() ? "empty" : snapshot.heldItem();
        draw(g, "Held " + held + "  (slot " + snapshot.hotbarSlot() + ")", x, y + 11, muted(), false, w);
        String dim = snapshot.dimension() == null || snapshot.dimension().isBlank() ? "?" : snapshot.dimension();
        draw(g, "World: " + dim, x, y + 22, muted(), false, w);
    }

    private static String trimStat(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    private void renderChat(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        String scope = selectedSessionIds.isEmpty() ? "All" : selectedSessionIds.size() + " selected";
        draw(g, "Console - " + scope, x, y, text(), false, w);
        int inputY = y + h - 17;
        int sendW = 44;
        place(g, chatInput, x, inputY, Math.max(50, w - sendW - 4));
        button(g, x + w - sendW, inputY, sendW, 16, "Send", success(), text(), mx, my, () -> sendChat(chatInput.text()));
        int top = y + 13, chatH = Math.max(10, inputY - top - 3);
        chatViewport = new Viewport(x, top, w, chatH);
        UiRenderer.frame(g, UiBounds.of(x, top, w, chatH), 0x30101012, border());
        chatHitRows.clear();
        int textX = x + 4;
        int timestampWidth = font.width("[00:00:00] ");
        chatMessageX = textX + timestampWidth;
        chatMessageRight = x + w - 4;
        chatWrapWidth = Math.max(1, chatMessageRight - chatMessageX);
        long chatRevision = MultiManager.get().chatRevision();
        String requestedScope = selectedSessionIds.isEmpty() ? "*" : String.join("|", selectedSessionIds);
        int total = currentChatTotal();
        if (chatRevision != cachedChatRevision || !requestedScope.equals(cachedChatScope)
            || chatWrapWidth != cachedChatWidth || total != cachedChatTotal) {
            boolean scopeChanged = !requestedScope.equals(cachedChatScope);
            int previousRows = cachedChatRows.size();
            boolean preserveScroll = chatScrollLines > 0 && requestedScope.equals(cachedChatScope)
                && chatWrapWidth == cachedChatWidth && total == cachedChatTotal;
            if (scopeChanged) chatScrollLines = 0;
            cachedChatRevision = chatRevision;
            cachedChatScope = requestedScope;
            cachedChatWidth = chatWrapWidth;
            cachedChatTotal = total;
            cachedChatRows = MultiChatPresentation.wrap(font, MultiManager.get().chatView(selectedSessionIds),
                chatWrapWidth, total, this::chatAccountLabel, muted());
            if (preserveScroll && cachedChatRows.size() > previousRows) {
                chatScrollLines += cachedChatRows.size() - previousRows;
            }
        }
        List<MultiChatPresentation.VisualRow> rows = cachedChatRows;

        List<MultiChatSelection.Row> selRows = new ArrayList<>(rows.size());
        for (MultiChatPresentation.VisualRow row : rows) {
            selRows.add(new MultiChatSelection.Row(row.line().seq(), row.lineIndex(),
                MultiChatSelection.plain(row.hit())));
        }
        chatSel.setRows(selRows);
        int visible = Math.max(1, (chatH - 4) / CHAT_LINE_HEIGHT);
        int chatMaxScroll = Math.max(0, rows.size() - visible);
        chatScrollLines = clamp(chatScrollLines, 0, chatMaxScroll);
        int end = rows.size() - chatScrollLines;
        int start = Math.max(0, end - visible);
        UiScissorStack.global().push(g, UiBounds.of(x + 2, top + 2, Math.max(1, w - 4), Math.max(1, chatH - 4)));
        int cy = top + 3;
        for (int i = start; i < end; i++) {
            MultiChatPresentation.VisualRow row = rows.get(i);
            String rowText = MultiChatSelection.plain(row.hit());
            int[] range = chatSel.rangeFor(row.line().seq(), row.lineIndex(), rowText.length());
            if (range != null && range[1] > range[0]) {

                int sx = chatMessageX + MultiChatSelection.widthOfStyled(font, row.hit(), range[0]);
                int ex = chatMessageX + MultiChatSelection.widthOfStyled(font, row.hit(), range[1]);
                UiRenderer.rect(g, UiBounds.of(sx, cy - 1, Math.max(1, ex - sx), CHAT_LINE_HEIGHT), 0x553C6EF5);
            }
            if (row.lineIndex() == 0) {
                g.text(font, net.minecraft.network.chat.Component.literal(timestamp(row.line().time())), textX, cy, muted(), false);
            }
            g.text(font, row.render(), chatMessageX, cy, text(), false);
            MultiChatPresentation.underlineClickableLine(g, font, row.hit(), chatMessageX, cy);
            chatHitRows.add(new ChatHitRow(row.line(), row.lineIndex(), cy, row.hit(), rowText));
            cy += CHAT_LINE_HEIGHT;
        }
        UiScissorStack.global().pop(g);
        scrollbar(g, chatViewport, chatScrollLines, chatMaxScroll, mx, my, "chat", v -> chatScrollLines = v);

        refreshChatSuggestions();
        chatSuggestRects.clear();
        boolean freshSuggests = !chatSuggests.isEmpty() && chatInput.isFocused()
            && (chatInput.text().equals(chatSuggestFor) || chatInput.text().equals(chatSuggestApplied));
        if (freshSuggests) {
            int rowH = CHAT_LINE_HEIGHT;
            int shown = Math.min(6, chatSuggests.size());
            int popH = shown * rowH + 2;
            int popY = inputY - popH - 1;
            UiRenderer.frame(g, UiBounds.of(x, popY, w, popH), 0xF0121216, border());
            int first = clamp(chatSuggestIndex - shown + 1, 0, Math.max(0, chatSuggests.size() - shown));
            for (int i = 0; i < shown; i++) {
                int idx = first + i;
                int ry = popY + 1 + i * rowH;
                boolean sel = idx == chatSuggestIndex;
                if (sel) UiRenderer.frame(g, UiBounds.of(x + 1, ry, w - 2, rowH), 0x40FFFFFF, 0);
                draw(g, chatSuggests.get(idx), x + 3, ry + 1, sel ? text() : muted(), false, w - 6);
                chatSuggestRects.add(new int[]{x, ry, w, rowH, idx});
            }
        }
    }

    private boolean handleChatClick(int mouseX, int mouseY) {
        if (!chatViewport.hit(mouseX, mouseY) || mouseX < chatMessageX || mouseX > chatMessageRight) return false;
        for (ChatHitRow row : chatHitRows) {
            if (mouseY < row.y() || mouseY >= row.y() + CHAT_LINE_HEIGHT) continue;
            int relativeX = mouseX - chatMessageX;
            ClickEvent click = MultiChatPresentation.resolveClick(font, row.hit(), relativeX);
            if (click == null) return false;
            executeChatClick(row.line(), row.lineIndex(), relativeX, click);
            return true;
        }
        return false;
    }

    private boolean beginChatSelection(int mouseX, int mouseY) {
        if (!chatViewport.hit(mouseX, mouseY) || chatHitRows.isEmpty()) {
            chatSel.clear();
            return false;
        }
        chatSelectingPress = true;
        chatSelectAt(mouseX, mouseY, true);
        return true;
    }

    private void chatSelectAt(int mouseX, int mouseY, boolean begin) {
        if (chatHitRows.isEmpty()) return;
        ChatHitRow target = chatHitRows.getFirst();
        for (ChatHitRow row : chatHitRows) {
            if (mouseY >= row.y()) target = row;
        }
        int ch = MultiChatSelection.charIndexAtStyled(font, target.hit(), mouseX - chatMessageX);
        if (begin) chatSel.begin(target.line().seq(), target.lineIndex(), ch);
        else chatSel.extend(target.line().seq(), target.lineIndex(), ch);
    }

    private void copyChatSelection() {
        String selected = chatSel.selectedText();
        if (selected.isBlank() || mc == null || mc.keyboardHandler == null) return;
        mc.keyboardHandler.setClipboard(selected);
        status("Copied " + selected.length() + " chars", SUCCESS_STOCK);
    }

    private void executeChatClick(MultiManager.ChatLine line, int lineIndex, int relativeX, ClickEvent click) {
        switch (click) {
            case ClickEvent.RunCommand command -> {
                int ran = MultiChatPresentation.runCommands(font, line, lineIndex, relativeX, chatWrapWidth,
                    currentChatTotal(), this::chatAccountLabel, muted(), command);
                status(ran > 0 ? "Clicked " + ran : "Clicked", SUCCESS_STOCK);
            }
            case ClickEvent.SuggestCommand suggest -> {
                chatInput.setText(suggest.command());
                chatInput.setFocused(true);
            }
            case ClickEvent.CopyToClipboard copy -> {
                mc.keyboardHandler.setClipboard(copy.value());
                status("Copied", MUTED_STOCK);
            }
            case ClickEvent.OpenUrl url -> {
                MultiChatPresentation.openLinkSafely(url.uri());
                status("Opening link", SUCCESS_STOCK);
            }
            default -> {
            }
        }
    }

    private int currentChatTotal() {
        return selectedSessionIds.isEmpty() ? Math.max(1, frameSnapshots.size()) : selectedSessionIds.size();
    }

    private String chatAccountLabel(String accountId) {
        for (MultiSession.Snapshot snapshot : frameSnapshots) {
            if (snapshot.accountId().equals(accountId)) return snapshot.accountName();
        }
        return accountId;
    }

    private static String timestamp(long time) {
        return "[" + LocalTime.ofInstant(java.time.Instant.ofEpochMilli(time), java.time.ZoneId.systemDefault())
            .format(CHAT_TIME) + "] ";
    }

    private void renderActions(GuiGraphicsExtractor g, int mx, int my) {
        int x = bx + 6, y = contentTop, w = bw - 12;
        boolean active = MultiManager.get().isActive();
        MultiProfile profile = active ? frameProfile : draft;
        if (profile == null) return;
        String scope = selectedSessionIds.isEmpty() ? "All sessions" : selectedSessionIds.size() + " selected";
        draw(g, "Quick Actions - " + scope, x, y, text(), false, w);
        y += 13;
        int count = MultiProfile.QUICK_ACTIONS + 1, gap = 3, each = (w - gap * (count - 1)) / count, cx = x;
        button(g, cx, y, each, 18, "Move", success(), text(), mx, my,
            MultiManager.get().isActive() ? () -> result(MultiManager.get().broadcastMovementNow(selectedSessionIds)) : null);
        cx += each + gap;
        for (int i = 0; i < MultiProfile.QUICK_ACTIONS; i++) {
            int index = i;
            MultiQuickAction action = profile.quickAction(i);
            int cw = i == MultiProfile.QUICK_ACTIONS - 1 ? x + w - cx : each;
            button(g, cx, y, cw, 18, action.label(i), action.empty() ? border() : accent(), text(), mx, my,
                () -> { if (!active || action.empty()) openQuickEditor(index);
                    else result(MultiManager.get().broadcastQuickAction(action, selectedSessionIds)); });
            cx += cw + gap;
        }
        y += 22;
        int slotW = (w - gap * 3) / 4;
        cx = x;
        for (int i = 0; i < 4; i++) {
            int index = i;
            int cw = i == 3 ? x + w - cx : slotW;
            button(g, cx, y, cw, 16, "Edit " + (i + 1), border(), text(), mx, my, () -> openQuickEditor(index));
            cx += cw + gap;
        }
        y += 21;
        int third = (w - 6) / 3;
        button(g, x, y, third, 17, "Use", border(), text(), mx, my,
            MultiManager.get().isActive() ? () -> result(MultiManager.get().useOnScope(selectedSessionIds)) : null);
        button(g, x + third + 3, y, third, 17, "Close GUI", border(), text(), mx, my,
            MultiManager.get().isActive() ? () -> result(MultiManager.get().closeOnScope(selectedSessionIds)) : null);
        String guiAccount = selectedSessionIds.size() == 1 ? selectedSessionIds.iterator().next() : "";
        toggleButton(g, x + (third + 3) * 2, y, w - (third + 3) * 2, 17, "GUI",
            host.isGuiOpen(guiAccount), mx, my,
            selectedSessionIds.size() == 1 ? this::openSelectedGui : null);
        y += 22;
        int half = (w - 4) / 2;
        MultiPacketPolicy policy = profile.packetPolicy;
        button(g, x, y, half, 17, "Gravity: " + (policy.gravity() ? "On" : "Off"),
            policy.gravity() ? success() : border(), text(), mx, my, () -> updatePolicy(p -> p.setGravity(!p.gravity())));
        button(g, x + half + 4, y, w - half - 4, 17, "Look: " + (policy.autoLook() ? "On" : "Off"),
            policy.autoLook() ? success() : border(), text(), mx, my, () -> updatePolicy(p -> p.setAutoLook(!p.autoLook())));
        y += 21;
        button(g, x, y, half, 17, "Swing: " + (policy.autoSwing() ? "On" : "Off"),
            policy.autoSwing() ? success() : border(), text(), mx, my, () -> updatePolicy(p -> p.setAutoSwing(!p.autoSwing())));
        button(g, x + half + 4, y, (w - half - 8) / 2, 17, "Block C2S", danger(), text(), mx, my,
            () -> openBlocklist(MultiPacketPolicy.Direction.C2S));
        button(g, x + half + 4 + (w - half - 8) / 2 + 4, y,
            w - (half + 4 + (w - half - 8) / 2 + 4), 17, "Block S2C", danger(), text(), mx, my,
            () -> openBlocklist(MultiPacketPolicy.Direction.S2C));
        y += 22;
        button(g, x, y, half, 17, "Reset Actions", border(), text(), mx, my, this::resetQuickActions);
        button(g, x + half + 4, y, w - half - 4, 17, "Clear Blocklist", border(), text(), mx, my,
            () -> updatePolicy(p -> p.setBlocklist(List.of())));
        y += 22;
        button(g, x, y, w, 17, "Auto-Accept: TPA & Trade", accent(), text(), mx, my, this::openAutoAccept);
        y += 22;
        if (!status.isBlank()) draw(g, status, x, y, themedStatus(), false, w);
    }

    private void renderMacros(GuiGraphicsExtractor g, int mx, int my) {
        int x = bx + 6, y = contentTop, w = bw - 12, h = contentBottom - y;
        int listW = Math.max(130, Math.min(180, w / 3));
        place(g, macroSearch, x, y, listW);
        y += 20;
        List<AutismMacro> macros = filteredMacros();
        int listH = Math.max(30, h - 20);
        macroViewport = new Viewport(x, y, listW, listH);
        int maxScroll = Math.max(0, macros.size() * ROW - listH);
        macroScroll = clamp(macroScroll, 0, maxScroll);
        UiScissorStack.global().push(g, macroViewport.bounds());
        interactionClip = macroViewport;
        int ry = y - macroScroll;
        for (AutismMacro macro : macros) {
            if (ry + ROW > y && ry < y + listH) {
                boolean selected = macro.name.equals(selectedMacroName);
                row(g, x, ry, listW, ROW - 2, macro.name, selected ? success() : border(), mx, my,
                    () -> { selectedMacroName = macro.name; pendingMacroDelete = ""; });
            }
            ry += ROW;
        }
        interactionClip = Viewport.NONE;
        UiScissorStack.global().pop(g);
        scrollbar(g, macroViewport, macroScroll, maxScroll, mx, my, "macro", v -> macroScroll = v);

        int dx = x + listW + 7, dw = w - listW - 7, cy = contentTop;
        String scope = selectedSessionIds.isEmpty() ? "All sessions" : selectedSessionIds.size() + " selected";
        draw(g, scope, dx, cy, muted(), false, dw); cy += 13;
        draw(g, selectedMacroName.isBlank() ? "No macro selected" : selectedMacroName, dx, cy, text(), false, dw); cy += 16;
        int third = (dw - 6) / 3;
        button(g, dx, cy, third, 17, "Assign", success(), text(), mx, my,
            selectedMacroName.isBlank() ? null : this::assignMacro);
        button(g, dx + third + 3, cy, third, 17, "Run", success(), text(), mx, my,
            MultiManager.get().isActive() ? this::runMacros : null, true);
        button(g, dx + (third + 3) * 2, cy, dw - (third + 3) * 2, 17, "Stop", danger(), text(), mx, my,
            MultiManager.get().isActive() ? () -> result(MultiManager.get().stopMacroOnInteractiveScope(selectedSessionIds)) : null);
        cy += 21;
        button(g, dx, cy, third, 17, "New", border(), text(), mx, my, () -> editMacro(null));
        button(g, dx + third + 3, cy, third, 17, "Edit", border(), text(), mx, my,
            selectedMacroName.isBlank() ? null : () -> editMacro(AutismMacroManager.get().get(selectedMacroName)));
        button(g, dx + (third + 3) * 2, cy, dw - (third + 3) * 2, 17, "Delete", danger(), text(), mx, my,
            selectedMacroName.isBlank() ? null : () -> pendingMacroDelete = selectedMacroName);
        cy += 21;
        button(g, dx, cy, dw, 17, "Clear Assignment", border(), text(), mx, my, this::clearMacroAssignment);
        cy += 22;
        List<String> compatibility = macroCompatibility(selectedMacroName);
        if (compatibility.isEmpty()) {
            draw(g, selectedMacroName.isBlank() ? "Select a macro." : "Fully compatible.", dx, cy, muted(), false, dw);
            cy += 15;
        } else {

            int warnBottom = cy + Math.min(72, Math.max(20, (contentBottom - 24 - cy) / 2));
            int shown = 0;
            boolean truncated = false;
            for (String warning : compatibility) {
                List<net.minecraft.util.FormattedCharSequence> lines =
                    font.split(net.minecraft.network.chat.FormattedText.of(warning), Math.max(40, dw - 4));
                if (cy + lines.size() * 10 > warnBottom) {
                    truncated = true;
                    break;
                }
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    g.text(font, line, dx, cy, warn(), false);
                    cy += 10;
                }
                shown++;
            }
            if (truncated) {
                draw(g, "+" + (compatibility.size() - shown) + " more warning(s) - shown in console chat on Run",
                    dx, cy, muted(), false, dw);
                cy += 10;
            }
            cy += 5;
        }
        draw(g, "Progress", dx, cy, text(), false, dw); cy += 12;
        int progressBottom = contentBottom - 12;
        macroProgressViewport = new Viewport(dx, cy, dw, Math.max(1, progressBottom - cy));
        int progressCount = 0;
        for (MultiSession.Snapshot snapshot : frameSnapshots) {
            if (selectedSessionIds.isEmpty() || selectedSessionIds.contains(snapshot.accountId())) progressCount++;
        }
        macroProgressScroll = clamp(macroProgressScroll, 0,
            Math.max(0, progressCount * 12 - macroProgressViewport.h()));
        UiScissorStack.global().push(g, macroProgressViewport.bounds());
        cy -= macroProgressScroll;
        for (MultiSession.Snapshot snapshot : frameSnapshots) {
            if (!selectedSessionIds.isEmpty() && !selectedSessionIds.contains(snapshot.accountId())) continue;
            MultiSession.MacroProgress progress = snapshot.macroProgress();
            String line;
            int color;
            if (progress != null && progress.running()) {
                line = snapshot.accountName() + "  " + progress.step() + "/" + progress.totalSteps()
                    + "  L" + progress.loop() + "  " + progress.detail();
                color = success();
            } else if (progress != null && !progress.macroName().isBlank() && !progress.detail().isBlank()) {
                line = snapshot.accountName() + "  " + progress.detail();
                color = progress.detail().toLowerCase(Locale.ROOT).contains("error") ? danger() : muted();
            } else {
                line = snapshot.accountName() + "  idle";
                color = muted();
            }
            draw(g, line, dx, cy, color, false, dw);
            cy += 12;
        }
        UiScissorStack.global().pop(g);
        scrollbar(g, macroProgressViewport, macroProgressScroll,
            Math.max(0, progressCount * 12 - macroProgressViewport.h()), mx, my, "macroProgress", v -> macroProgressScroll = v);
        if (!status.isBlank()) draw(g, status, dx, contentBottom - 10, themedStatus(), false, dw);
    }

    private void renderQuickEditor(GuiGraphicsExtractor g, int mx, int my) {
        hotspots.clear();
        activeInputs.clear();
        UiRenderer.rect(g, UiBounds.of(bx, by, bw, bh), 0xB0000000);
        int mw = Math.min(330, bw - 24), mh = Math.min(184, bh - 24);
        int x = bx + (bw - mw) / 2, y = by + (bh - mh) / 2;
        UiRenderer.frame(g, UiBounds.of(x, y, mw, mh), 0xF0121214, accent());
        draw(g, "Edit Action " + (quickEdit + 1), x + 8, y + 7, text(), false, mw - 16);
        place(g, quickName, x + 8, y + 22, mw - 16);
        ensureQuickStep();
        String packet = quickDraft.steps.get(quickStep).packetClass();
        draw(g, "Packet " + (quickStep + 1) + "/" + quickDraft.steps.size(), x + 8, y + 44, muted(), false, mw - 90);
        button(g, x + mw - 76, y + 42, 68, 17, "Choose", border(), text(), mx, my, this::pickQuickPacket);
        draw(g, packet.isBlank() ? "No packet" : MultiQuickAction.shortLabel(packet), x + 8, y + 57, text(), false, mw - 16);
        place(g, quickArgs, x + 8, y + 70, mw - 16);
        int q = (mw - 22) / 4, by1 = y + 91;
        button(g, x + 8, by1, q, 16, "Prev", border(), text(), mx, my, quickStep > 0 ? () -> changeQuickStep(-1) : null);
        button(g, x + 10 + q, by1, q, 16, "Next", border(), text(), mx, my,
            quickStep + 1 < quickDraft.steps.size() ? () -> changeQuickStep(1) : null);
        button(g, x + 12 + q * 2, by1, q, 16, "Add", border(), text(), mx, my,
            quickDraft.steps.size() < MultiQuickAction.MAX_STEPS ? this::addQuickStep : null);
        button(g, x + 14 + q * 3, by1, mw - 22 - q * 3, 16, "Remove", danger(), text(), mx, my, this::removeQuickStep);
        int half = (mw - 20) / 2, by2 = y + 113;
        button(g, x + 8, by2, half, 17, "Test Send", accent(), text(), mx, my, this::testQuickDraft);
        button(g, x + 12 + half, by2, mw - 20 - half, 17, "Clear", danger(), text(), mx, my, this::clearQuickEditor);
        int third = (mw - 22) / 3, by3 = y + mh - 24;
        button(g, x + 8, by3, third, 17, "Save", success(), text(), mx, my, this::saveQuickEditor);
        button(g, x + 11 + third, by3, third, 17, "Reset All", border(), text(), mx, my, this::resetQuickActions);
        button(g, x + 14 + third * 2, by3, mw - 22 - third * 2, 17, "Cancel", border(), text(), mx, my, this::closeQuickEditor);
    }

    private void renderMacroDelete(GuiGraphicsExtractor g, int mx, int my) {
        hotspots.clear();
        activeInputs.clear();
        UiRenderer.rect(g, UiBounds.of(bx, by, bw, bh), 0xB0000000);
        int w = Math.min(270, bw - 30), h = 76, x = bx + (bw - w) / 2, y = by + (bh - h) / 2;
        UiRenderer.frame(g, UiBounds.of(x, y, w, h), 0xF0121214, danger());
        draw(g, "Delete " + pendingMacroDelete + "?", x + 8, y + 10, text(), false, w - 16);
        int half = (w - 20) / 2;
        button(g, x + 8, y + 46, half, 18, "Delete", danger(), text(), mx, my, this::confirmMacroDelete);
        button(g, x + 12 + half, y + 46, w - 20 - half, 18, "Cancel", border(), text(), mx, my,
            () -> pendingMacroDelete = "");
    }

    private void loadInitialDraft() {
        List<MultiProfile> profiles = MultiProfileManager.get().all();
        if (profiles.isEmpty()) { newProfile(); return; }

        MultiProfile shared = MultiProfileManager.get().find(MultiProfileManager.get().selectedId());
        loadProfile(shared != null ? shared : profiles.getFirst());
    }

    private void newProfile() {
        draft = new MultiProfile();
        draft.name = MultiProfileManager.get().nextAvailableName("New profile", draft.id);
        draft.serverAddress = currentServer();
        selectFirstEligibleAccount();

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        syncFieldsFromDraft();
        pendingProfileDelete = "";
        lastCommittedState = draft.toTag().toString();
    }

    private void resetDraft() {
        String server = currentServer();
        newProfile();
        draft.serverAddress = server;
        syncFieldsFromDraft();
        status("Defaults restored", MUTED_STOCK);
    }

    private void loadProfile(MultiProfile profile) {
        if (profile == null) return;
        draft = new MultiProfile(profile);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        setupDetailScroll = 0;
        syncFieldsFromDraft();
        pendingProfileDelete = "";
        lastCommittedState = draft.toTag().toString();
        status("Loaded", MUTED_STOCK);
    }

    private void selectProfileRow(MultiProfile profile) {
        if (profile == null) return;
        if (profile.id.equals(selectedProfileId)) {

            return;
        }
        loadProfile(profile);
    }

    private void commit() {
        if (draft == null) return;
        captureDraftFields();
        draft.normalize();
        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        String serialized = draft.toTag().toString();
        if (!serialized.equals(lastCommittedState)) savedFlashAt = System.currentTimeMillis();
        lastCommittedState = serialized;
    }

    private boolean editingProfileText() {
        return profileName.isFocused() || serverAddress.isFocused()
            || customConcurrency.isFocused() || customDelay.isFocused() || autoPing.isFocused();
    }

    private void autoSaveTick() {
        long now = System.currentTimeMillis();
        if (now - lastAutoSaveCheckAt < 400L) return;
        lastAutoSaveCheckAt = now;
        if (draft == null || editingProfileText()) return;
        captureDraftFields();
        String state = draft.toTag().toString();
        if (lastCommittedState == null) {
            lastCommittedState = state;
        } else if (!state.equals(lastCommittedState)) {
            commit();
        }
    }

    public void flushPendingEdit() {
        if (draft == null) return;
        captureDraftFields();
        String state = draft.toTag().toString();
        if (lastCommittedState == null) {
            lastCommittedState = state;
        } else if (!state.equals(lastCommittedState)) {
            commit();
        }
    }

    private void duplicateProfile() {
        captureDraftFields();
        MultiProfile copy = new MultiProfile(draft);
        copy.id = UUID.randomUUID().toString();
        copy.name = MultiProfileManager.get().nextAvailableName(copy.name, copy.id);
        draft = copy;
        syncFieldsFromDraft();
        commit();
    }

    private void deleteProfile() {
        if (selectedProfileId.isBlank()) { status("Unsaved profile", MUTED_STOCK); return; }
        if (!pendingProfileDelete.equals(selectedProfileId)) {
            pendingProfileDelete = selectedProfileId;
            status("Press Confirm", WARN_STOCK);
            return;
        }
        MultiProfileManager.get().remove(selectedProfileId);
        pendingProfileDelete = "";
        List<MultiProfile> left = MultiProfileManager.get().all();
        if (left.isEmpty()) newProfile(); else loadProfile(left.getFirst());
        status("Deleted", MUTED_STOCK);
    }

    private void launch() {
        captureDraftFields();
        draft.normalize();
        draft.name = MultiProfileManager.get().nextAvailableName(draft.name, draft.id);
        profileName.setText(draft.name);
        MultiManager.StartResult result = MultiManager.get().start(draft);
        if (!result.ok()) { status(result.message(), DANGER_STOCK); return; }
        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        tab = Tab.CONSOLE;
        status("Launching", SUCCESS_STOCK);
    }

    private void setPacing(int index) {
        if (index >= 0 && index < MultiProfile.Pacing.values().length) draft.pacing = MultiProfile.Pacing.values()[index];
    }

    private void setProxyMode(int index) {
        if (index >= 0 && index < MultiProfile.ProxyMode.values().length) draft.proxyMode = MultiProfile.ProxyMode.values()[index];
    }

    private void openAutoAccept() {
        boolean active = MultiManager.get().isActive();
        MultiProfile profile = active ? MultiManager.get().activeProfile() : draft;
        if (profile == null) return;
        host.editAutoAccept(profile.autoAccept, cfg -> {
            if (active) {
                MultiManager.get().updateAutoAccept(cfg);
            } else {
                draft.autoAccept = cfg;
                commit();
            }
        }, active);
    }

    private void editFormValues() {
        Set<String> selected = selectedSessionIds.isEmpty()
            ? draft.sessions.stream().map(MultiProfile.SessionSpec::accountId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            : new LinkedHashSet<>(selectedSessionIds);
        host.editFormValues(new MultiProfile(draft), selected, updated -> {
            draft = updated;
            syncFieldsFromDraft();
            commit();
            status("Passwords saved", SUCCESS_STOCK);
        });
    }

    private void setAutoPing(int index) {
        if (index >= 0 && index < PING_OPTIONS.length) {
            draft.autoMaxPingMs = PING_OPTIONS[index];
            autoPing.setText(Integer.toString(draft.autoMaxPingMs));
        }
    }

    private void useCurrentServer() {
        draft.serverAddress = currentServer();
        serverAddress.setText(draft.serverAddress);
    }

    private void captureDraftFields() {
        draft.name = clean(profileName.text());
        draft.serverAddress = clean(serverAddress.text());
        draft.customConcurrency = parseInt(customConcurrency.text(), draft.customConcurrency);
        draft.customDelayMs = parseInt(customDelay.text(), draft.customDelayMs);
        draft.autoMaxPingMs = parseInt(autoPing.text(), draft.autoMaxPingMs);
    }

    private void syncFieldsFromDraft() {
        profileName.setText(draft.name);
        serverAddress.setText(draft.serverAddress);
        customConcurrency.setText(Integer.toString(draft.customConcurrency));
        customDelay.setText(Integer.toString(draft.customDelayMs));
        autoPing.setText(Integer.toString(draft.autoMaxPingMs));
    }

    private void selectFirstEligibleAccount() {
        if (draft == null || !draft.sessions.isEmpty()) return;
        if (!MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID)) {
            draft.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
            return;
        }
        for (AutismAccount account : AutismAccountManager.get().all()) {
            if (!MultiManager.isCurrentRenderedAccount(account.stableId())) {
                draft.sessions.add(new MultiProfile.SessionSpec(account.stableId(), ""));
                return;
            }
        }
    }

    private void toggleAccount(String id) {
        if (MultiManager.isCurrentRenderedAccount(id)) {

            status("Current account cannot join Multi", DANGER_STOCK);
            return;
        }
        MultiProfile.SessionSpec spec = selectedSpec(id);
        if (spec != null) draft.sessions.remove(spec);
        else if (draft.sessions.size() < MultiProfile.MAX_SESSIONS) draft.sessions.add(new MultiProfile.SessionSpec(id, ""));
    }

    private void setAccountProxy(String accountId, String proxyId) {
        MultiProfile.SessionSpec spec = selectedSpec(accountId);
        if (spec == null) return;
        int index = draft.sessions.indexOf(spec);
        draft.sessions.set(index, new MultiProfile.SessionSpec(spec.accountId(), proxyId, spec.macroName()));
    }

    private void setAllProxies(String proxyId) {
        String id = proxyId == null ? "" : proxyId;
        for (int i = 0; i < draft.sessions.size(); i++) {
            MultiProfile.SessionSpec spec = draft.sessions.get(i);
            draft.sessions.set(i, new MultiProfile.SessionSpec(spec.accountId(), id, spec.macroName()));
        }
    }

    private void selectSession(String id) {
        if (ctrlDown()) {
            if (!selectedSessionIds.remove(id)) selectedSessionIds.add(id);
            return;
        }
        if (shiftDown() && !selectedSessionIds.isEmpty()) {
            List<String> order = MultiManager.get().snapshots().stream().map(MultiSession.Snapshot::accountId).toList();
            int a = order.indexOf(selectedSessionIds.iterator().next()), b = order.indexOf(id);
            if (a >= 0 && b >= 0) {
                selectedSessionIds.clear();
                for (int i = Math.min(a, b); i <= Math.max(a, b); i++) selectedSessionIds.add(order.get(i));
                return;
            }
        }
        boolean only = selectedSessionIds.size() == 1 && selectedSessionIds.contains(id);
        selectedSessionIds.clear();
        if (!only) selectedSessionIds.add(id);
    }

    private void openSelectedGui() {
        if (selectedSessionIds.size() != 1) {
            status("Select one session", MUTED_STOCK);
            return;
        }
        host.openGui(selectedSessionIds.iterator().next());
    }

    private void retrySelected() {
        List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();
        int retried = 0;
        for (MultiSession.Snapshot snapshot : snapshots) {
            if (!selectedSessionIds.isEmpty() && !selectedSessionIds.contains(snapshot.accountId())) continue;
            if (!MultiManager.isRetryable(snapshot.status())) continue;
            if (MultiManager.get().retry(snapshot.accountId()).ok()) retried++;
        }
        status(retried > 0 ? "Retrying " + retried : "Nothing to retry", retried > 0 ? SUCCESS_STOCK : MUTED_STOCK);
    }

    private void stopSelectedSessions() {
        for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (selectedSessionIds.isEmpty() || selectedSessionIds.contains(snapshot.accountId())) {
                MultiManager.get().disconnectSession(snapshot.accountId());
            }
        }
        status("Stopped", DANGER_STOCK);
    }

    private void sendChat(String text) {
        String line = clean(text);
        if (line.isBlank() || !MultiManager.get().isActive()) return;
        result(MultiManager.get().broadcastConsole(line, selectedSessionIds));
        MultiManager.get().pushHistory(line);
        chatInput.setText("");
        clearChatSuggests();
    }

    private void openQuickEditor(int index) {
        MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : draft;
        if (profile == null) return;
        quickEdit = index;
        quickStep = 0;
        quickDraft = profile.quickAction(index);
        if (quickDraft.steps.isEmpty()) quickDraft.steps.add(new MultiQuickAction.Step("", ""));
        quickName.setText(quickDraft.name);
        quickArgs.setText(quickDraft.steps.getFirst().arguments());
    }

    private void ensureQuickStep() {
        if (quickDraft == null) quickDraft = new MultiQuickAction();
        if (quickDraft.steps.isEmpty()) quickDraft.steps.add(new MultiQuickAction.Step("", ""));
        quickStep = clamp(quickStep, 0, quickDraft.steps.size() - 1);
    }

    private void captureQuickFields() {
        ensureQuickStep();
        quickDraft.name = clean(quickName.text());
        MultiQuickAction.Step current = quickDraft.steps.get(quickStep);
        quickDraft.steps.set(quickStep, new MultiQuickAction.Step(current.packetClass(), quickArgs.text()));
    }

    private void changeQuickStep(int delta) {
        captureQuickFields();
        quickStep = clamp(quickStep + delta, 0, quickDraft.steps.size() - 1);
        quickArgs.setText(quickDraft.steps.get(quickStep).arguments());
    }

    private void addQuickStep() {
        captureQuickFields();
        if (quickDraft.steps.size() >= MultiQuickAction.MAX_STEPS) return;
        quickDraft.steps.add(new MultiQuickAction.Step("", ""));
        quickStep = quickDraft.steps.size() - 1;
        quickArgs.setText("");
    }

    private void removeQuickStep() {
        ensureQuickStep();
        quickDraft.steps.remove(quickStep);
        if (quickDraft.steps.isEmpty()) quickDraft.steps.add(new MultiQuickAction.Step("", ""));
        quickStep = clamp(quickStep, 0, quickDraft.steps.size() - 1);
        quickArgs.setText(quickDraft.steps.get(quickStep).arguments());
    }

    private void pickQuickPacket() {
        captureQuickFields();
        host.pickQuickPacket(packet -> {
            ensureQuickStep();
            MultiQuickAction.Step old = quickDraft.steps.get(quickStep);
            quickDraft.steps.set(quickStep, new MultiQuickAction.Step(packet.getName(), old.arguments()));
            if (quickDraft.name.isBlank()) {
                quickDraft.name = MultiQuickAction.shortLabel(packet.getName());
                quickName.setText(quickDraft.name);
            }
        });
    }

    private MultiQuickAction builtQuickDraft() {
        captureQuickFields();
        MultiQuickAction result = new MultiQuickAction(quickDraft);
        result.normalize();
        return result;
    }

    private void saveQuickEditor() {
        MultiQuickAction action = builtQuickDraft();
        if (MultiManager.get().isActive()) MultiManager.get().updateQuickAction(quickEdit, action);
        else draft.setQuickAction(quickEdit, action);
        status("Saved", SUCCESS_STOCK);
        closeQuickEditor();
    }

    private void testQuickDraft() {
        if (!MultiManager.get().isActive()) { status("Launch first", MUTED_STOCK); return; }
        result(MultiManager.get().broadcastQuickAction(builtQuickDraft(), selectedSessionIds));
    }

    private void clearQuickEditor() {
        if (MultiManager.get().isActive()) MultiManager.get().clearQuickAction(quickEdit);
        else draft.setQuickAction(quickEdit, new MultiQuickAction());
        status("Cleared", MUTED_STOCK);
        closeQuickEditor();
    }

    private void closeQuickEditor() {
        quickEdit = -1;
        quickDraft = null;
        quickStep = 0;
        quickName.setFocused(false);
        quickArgs.setFocused(false);
    }

    private void resetQuickActions() {
        if (MultiManager.get().isActive()) MultiManager.get().resetQuickActions();
        else draft.resetQuickActions();
        status("Actions reset", MUTED_STOCK);
        closeQuickEditor();
    }

    private void updatePolicy(Consumer<MultiPacketPolicy> change) {
        MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : draft;
        if (profile == null) return;
        MultiPacketPolicy policy = new MultiPacketPolicy(profile.packetPolicy);
        change.accept(policy);
        if (MultiManager.get().isActive()) MultiManager.get().updatePolicy(policy);
        else draft.packetPolicy = policy;
    }

    private void openBlocklist(MultiPacketPolicy.Direction direction) {
        MultiProfile profile = MultiManager.get().isActive() ? MultiManager.get().activeProfile() : draft;
        if (profile == null) return;
        Set<Class<? extends Packet<?>>> selected = new LinkedHashSet<>();
        for (MultiPacketPolicy.Rule rule : profile.packetPolicy.blocklist()) {
            if (rule.direction() != direction) continue;
            Class<? extends Packet<?>> packet = AutismPacketRegistry.getPacket(rule.packetClass());
            if (packet != null) selected.add(packet);
        }
        host.editBlocklist(direction, selected, (packet, enabled) -> updatePolicy(policy -> {
            List<MultiPacketPolicy.Rule> rules = new ArrayList<>(policy.blocklist());
            rules.removeIf(rule -> rule.direction() == direction && rule.packetClass().equals(packet.getName()));
            if (enabled) rules.add(new MultiPacketPolicy.Rule(direction, packet.getName()));
            policy.setBlocklist(rules);
        }));
    }

    private void runMacros() {
        MultiManager m = MultiManager.get();
        if (!m.isActive()) { status("Connect first", MUTED_STOCK); return; }
        if (m.hasAssignedMacroOnInteractiveScope(selectedSessionIds)) {
            result(m.runMacroOnInteractiveScope(selectedSessionIds));
        } else if (!selectedMacroName.isBlank()) {
            AutismMacro macro = AutismMacroManager.get().get(selectedMacroName);
            if (macro != null) result(m.runMacroDirectInteractive(macro, selectedSessionIds));
            else status("Macro not found", DANGER_STOCK);
        } else {
            status("Select or assign a macro first", MUTED_STOCK);
        }
    }

    private void assignMacro() {
        if (selectedMacroName.isBlank()) { status("Select a macro first", MUTED_STOCK); return; }
        List<String[]> accounts = assignAccounts();
        if (accounts.isEmpty()) { status("No accounts", MUTED_STOCK); return; }
        if (accounts.size() == 1) {
            applyAssign(java.util.List.of(accounts.get(0)[0]));
            status("Assigned to " + accounts.get(0)[1], SUCCESS_STOCK);
            return;
        }
        assignSelected.clear();
        assignSelected.addAll(selectedSessionIds);
        assignScroll = 0;
        assignPopupOpen = true;
    }

    private List<String[]> assignAccounts() {
        List<String[]> out = new ArrayList<>();
        if (MultiManager.get().isActive()) {
            for (MultiSession.Snapshot s : frameSnapshots) {
                out.add(new String[]{s.accountId(), s.accountName(),
                    orEmpty(MultiManager.get().effectiveMacroName(s.accountId()))});
            }
        } else if (draft != null) {
            for (MultiProfile.SessionSpec spec : draft.sessions) {
                String m = spec.macroName().isBlank() ? draft.allMacroName : spec.macroName();
                out.add(new String[]{spec.accountId(), assignAccountName(spec.accountId()), orEmpty(m)});
            }
        }
        return out;
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private String assignAccountName(String id) {
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(id)) return "Current account";
        AutismAccount account = AutismAccountManager.get().findById(id);
        return account == null ? id : clean(account.displayName());
    }

    private void applyAssign(java.util.Collection<String> ids) {
        if (MultiManager.get().isActive()) {
            MultiManager.get().assignMacroOnScope(new LinkedHashSet<>(ids), selectedMacroName);
        } else if (draft != null) {
            for (int i = 0; i < draft.sessions.size(); i++) {
                MultiProfile.SessionSpec spec = draft.sessions.get(i);
                if (ids.contains(spec.accountId())) draft.sessions.set(i, spec.withMacro(selectedMacroName));
            }
            commit();
        }
    }

    private static final int ASSIGN_ROW_H = 16;

    private void renderAssignPopup(GuiGraphicsExtractor g, int mx, int my) {
        hotspots.clear();
        activeInputs.clear();
        UiRenderer.rect(g, UiBounds.of(bx, by, bw, bh), 0xB0000000);
        int w = Math.min(300, bw - 24), h = Math.min(220, bh - 24);
        int x = bx + (bw - w) / 2, y = by + (bh - h) / 2;
        UiRenderer.frame(g, UiBounds.of(x, y, w, h), 0xF0121214, accent());
        draw(g, "Assign \"" + selectedMacroName + "\" to:", x + 8, y + 7, text(), false, w - 16);

        List<String[]> accounts = assignAccounts();
        int listTop = y + 22;
        int listBottom = y + h - 44;
        int listH = Math.max(ASSIGN_ROW_H, listBottom - listTop);
        int maxScroll = Math.max(0, accounts.size() * ASSIGN_ROW_H - listH);
        assignScroll = clamp(assignScroll, 0, maxScroll);
        UiScissorStack.global().push(g, UiBounds.of(x + 8, listTop, w - 16, listH));
        int ry = listTop - assignScroll;
        for (String[] acc : accounts) {
            if (ry + ASSIGN_ROW_H > listTop && ry < listBottom) {
                boolean sel = assignSelected.contains(acc[0]);
                int outline = sel ? success() : border();
                UiRenderer.frame(g, UiBounds.of(x + 8, ry, w - 16, ASSIGN_ROW_H - 2),
                    sel ? tint(success(), 0x30) : CARD, outline);
                String box = sel ? "[x] " : "[ ] ";
                String cur = acc[2].isBlank() ? "" : "  -> " + acc[2];
                draw(g, box + acc[1] + cur, x + 12, ry + 4, sel ? success() : text(), false, w - 24);
                final String id = acc[0];
                hotspots.add(new Hotspot(x + 8, ry, w - 16, ASSIGN_ROW_H - 2,
                    () -> { if (!assignSelected.remove(id)) assignSelected.add(id); }));
            }
            ry += ASSIGN_ROW_H;
        }
        UiScissorStack.global().pop(g);

        int by1 = y + h - 40;
        button(g, x + 8, by1, w - 16, 15, "Select all without a macro", border(), text(), mx, my, () -> {
            assignSelected.clear();
            for (String[] acc : assignAccounts()) if (acc[2].isBlank()) assignSelected.add(acc[0]);
        });
        int by2 = y + h - 22;
        int half = (w - 20) / 2;
        button(g, x + 8, by2, half, 16, "Save (" + assignSelected.size() + ")", success(), text(), mx, my, () -> {
            applyAssign(new LinkedHashSet<>(assignSelected));
            assignPopupOpen = false;
            status("Assigned to " + assignSelected.size() + " account(s)", SUCCESS_STOCK);
        });
        button(g, x + 12 + half, by2, w - 20 - half, 16, "Cancel", border(), text(), mx, my,
            () -> assignPopupOpen = false);
    }

    private void clearMacroAssignment() {
        if (selectedSessionIds.isEmpty()) MultiManager.get().assignAllMacro("");
        else MultiManager.get().assignMacroOnScope(selectedSessionIds, "");
        if (!MultiManager.get().isActive()) draft.allMacroName = "";
        status("Assignment cleared", MUTED_STOCK);
    }

    private void editMacro(AutismMacro macro) {
        String oldName = macro == null || macro.name == null ? "" : macro.name;
        host.editMacro(macro, saved -> {
            if (saved == null || saved.name == null) return;
            if (!oldName.isBlank() && !oldName.equals(saved.name)) {
                MultiProfileManager.get().replaceMacroReferences(oldName, saved.name);
                MultiManager.get().replaceMacroReference(oldName, saved.name);
            }
            selectedMacroName = saved.name;
            status("Macro saved", SUCCESS_STOCK);
        });
    }

    private void confirmMacroDelete() {
        AutismMacro macro = AutismMacroManager.get().get(pendingMacroDelete);
        String name = pendingMacroDelete;
        pendingMacroDelete = "";
        if (macro == null) return;
        AutismMacroManager.get().delete(macro);
        MultiProfileManager.get().replaceMacroReferences(name, "");
        MultiManager.get().replaceMacroReference(name, "");
        if (name.equals(selectedMacroName)) selectedMacroName = "";
        status("Macro deleted", MUTED_STOCK);
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (CompactDropdown.mouseClicked(frameDropdowns, mx, my, button)) {
            clearFocus();
            return true;
        }

        if (!contextMenuId.isBlank()) {
            if (button == 0) {
                for (int[] rect : contextItemRects) {
                    if (mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3]) {
                        runContextAction(rect[4]);
                        return true;
                    }
                }
            }
            contextMenuId = "";
            return true;
        }

        if (button == 1 && tab == Tab.CONSOLE) {
            for (RowHit row : sessionRowHits) {
                if (row.hit(mx, my)) {
                    contextMenuId = row.id();
                    contextMenuX = mx;
                    contextMenuY = my;
                    return true;
                }
            }
        }

        if (button == 0 && tab == Tab.CONSOLE) {
            for (int[] rect : chatSuggestRects) {
                if (mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3]) {
                    applyChatSuggestionAt(rect[4]);
                    chatInput.setFocused(true);
                    return true;
                }
            }
        }
        DirectRenderContext ctx = ctx(null, mx, my);
        for (CompactTextInput input : activeInputs) {
            if (input.mouseClicked(ctx, mx, my, button)) {
                for (CompactTextInput other : activeInputs) if (other != input) other.setFocused(false);
                return true;
            }
        }
        if (button == 0 && tab == Tab.CONSOLE) {

            if (handleChatClick(mx, my)) return true;
            if (beginChatSelection(mx, my)) return true;
        }
        clearFocus();
        if (button == 0) {
            for (int i = hotspots.size() - 1; i >= 0; i--) {
                Hotspot hit = hotspots.get(i);
                if (!hit.hit(mx, my)) continue;
                try { hit.action().run(); }
                catch (Throwable error) { AutismNotifications.show("Multi action failed", danger()); }
                return true;
            }

            if (assignPopupOpen || quickEdit >= 0 || !pendingMacroDelete.isBlank()) return true;
            if (tab == Tab.CONSOLE) selectedSessionIds.clear();

            else if (tab == Tab.MACROS && macroViewport.hit(mx, my)) {
                selectedMacroName = "";
                pendingMacroDelete = "";
            }
        }
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    public boolean mouseReleased(int mx, int my, int button) {
        scrollbarDragId = null;
        scrollbarSetter = null;
        if (CompactDropdown.mouseReleased(frameDropdowns)) return true;
        if (chatSelectingPress) {
            chatSelectingPress = false;
            chatSel.finishDrag();
            return true;
        }
        DirectRenderContext ctx = ctx(null, mx, my);
        boolean result = false;
        for (CompactTextInput input : activeInputs) result |= input.mouseReleased(ctx, mx, my, button);
        return result;
    }

    public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
        if (CompactDropdown.mouseDragged(frameDropdowns, mx, my, button)) return true;
        if (scrollbarDragId != null) {
            applyScrollbarDrag(my);
            return true;
        }
        if (chatSelectingPress) {
            chatSelectAt(mx, my, false);
            return true;
        }
        DirectRenderContext ctx = ctx(null, mx, my);
        boolean result = false;
        for (CompactTextInput input : activeInputs) result |= input.mouseDragged(ctx, mx, my, button, (float) dx, (float) dy);
        return result;
    }

    public boolean mouseScrolled(int mx, int my, double amount) {
        if (CompactDropdown.mouseScrolled(frameDropdowns, mx, my, amount)) return true;
        if (mx < bx || mx >= bx + bw || my < by || my >= by + bh) return false;
        if (assignPopupOpen) {
            assignScroll = Math.max(0, assignScroll - (int) Math.signum(amount) * ASSIGN_ROW_H * 2);
            return true;
        }
        if (quickEdit >= 0 || !pendingMacroDelete.isBlank()) return true;
        int direction = (int) Math.signum(amount);
        if (direction == 0) return true;
        int step = (int) Math.signum(amount) * ROW * 2;
        switch (tab) {
            case SETUP -> {
                if (profileViewport.hit(mx, my)) profileScroll = Math.max(0, profileScroll - step);
                else if (setupDetailViewport.hit(mx, my)) setupDetailScroll = Math.max(0, setupDetailScroll - step);
            }
            case ACCOUNTS -> {
                if (accountViewport.hit(mx, my)) accountScroll = Math.max(0, accountScroll - step);
            }
            case CONSOLE -> {
                if (chatViewport.hit(mx, my)) chatScrollLines = Math.max(0, chatScrollLines + direction * 3);
                else if (sessionViewport.hit(mx, my)) sessionScroll = Math.max(0, sessionScroll - step);
            }
            case ACTIONS -> {
            }
            case MACROS -> {
                if (macroProgressViewport.hit(mx, my)) macroProgressScroll = Math.max(0, macroProgressScroll - step);
                else if (macroViewport.hit(mx, my)) macroScroll = Math.max(0, macroScroll - step);
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
            && chatSel.hasSelection() && !chatInput.isFocused()) {
            copyChatSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (chatSel.hasSelection()) { chatSel.clear(); return true; }
            if (CompactDropdown.closeOpenMenu(frameDropdowns)) return true;
            if (quickEdit >= 0) { closeQuickEditor(); return true; }
            if (!pendingMacroDelete.isBlank()) { pendingMacroDelete = ""; return true; }
            if (assignPopupOpen) { assignPopupOpen = false; return true; }

            if (chatInput.isFocused() && !chatSuggests.isEmpty()) { clearChatSuggests(); return true; }
        }
        if (chatInput.isFocused() && keyCode == GLFW.GLFW_KEY_TAB) {

            applyChatTab((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        DirectRenderContext ctx = ctx(null, lastMx, lastMy);
        for (CompactTextInput input : activeInputs) {
            if (input.isFocused() && input.keyPressed(ctx, keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    private void refreshChatSuggestions() {
        if (!chatInput.isFocused()) {
            if (!chatSuggests.isEmpty() || chatSuggestFor != null || chatSuggestApplied != null) clearChatSuggests();
            return;
        }
        String value = chatInput.text();
        if (value.equals(chatSuggestApplied)) return;
        if (value.equals(chatSuggestFor)) return;
        if (value.startsWith("/")) {
            refreshServerSuggestions(value);
        } else if (MultiChatCompletion.isClientCommand(value)) {
            refreshClientSuggestions(value);
        } else {
            clearChatSuggests();
        }
    }

    private void refreshServerSuggestions(String value) {
        String stripped = value.substring(1);
        long now = System.currentTimeMillis();
        if (!value.equals(chatLastReqCmd) && now - chatLastReqAt >= 60) {
            MultiManager.get().requestSuggestions(stripped, selectedSessionIds);
            chatLastReqCmd = value;
            chatLastReqAt = now;
        }
        MultiManager.SuggestionResult r = MultiManager.get().suggestions(stripped);
        if (r == null) return;
        applyChatSuggestions(value, 1 + r.start(), r.length(), r.entries());
    }

    private void refreshClientSuggestions(String value) {
        chatLastReqCmd = null;
        MultiChatCompletion.Result r = MultiChatCompletion.clientSuggestions(value);
        if (r == null) return;
        applyChatSuggestions(value, r.start(), r.length(), r.entries());
    }

    private void applyChatSuggestions(String value, int absStart, int length, List<String> entries) {
        if (!value.equals(chatSuggestFor)) chatSuggestIndex = 0;
        chatSuggestFor = value;
        chatSuggestStart = absStart;
        chatSuggestLen = length;
        chatSuggests.clear();
        chatSuggests.addAll(entries);
        if (chatSuggestIndex >= chatSuggests.size()) chatSuggestIndex = Math.max(0, chatSuggests.size() - 1);
    }

    private void applyChatSuggestionAt(int index) {
        if (index < 0 || index >= chatSuggests.size()) return;
        chatSuggestIndex = index;
        String base = chatSuggestFor == null ? chatInput.text() : chatSuggestFor;
        int start = Math.max(0, Math.min(chatSuggestStart, base.length()));
        int end = Math.max(start, Math.min(chatSuggestStart + chatSuggestLen, base.length()));
        String full = base.substring(0, start) + chatSuggests.get(index) + base.substring(end);
        chatInput.setText(full);
        chatInput.moveCursorToEnd();
        chatSuggestApplied = full;
    }

    private boolean applyChatTab(boolean backwards) {
        String value = chatInput.text();
        boolean cycling = value.equals(chatSuggestApplied);
        if (chatSuggests.isEmpty() || (!value.equals(chatSuggestFor) && !cycling)) return false;
        if (cycling) chatSuggestIndex = Math.floorMod(chatSuggestIndex + (backwards ? -1 : 1), chatSuggests.size());
        String base = chatSuggestFor == null ? value : chatSuggestFor;
        int start = Math.max(0, Math.min(chatSuggestStart, base.length()));
        int end = Math.max(start, Math.min(chatSuggestStart + chatSuggestLen, base.length()));
        String full = base.substring(0, start) + chatSuggests.get(chatSuggestIndex) + base.substring(end);
        chatInput.setText(full);
        chatInput.moveCursorToEnd();
        chatInput.setFocused(true);
        chatSuggestApplied = full;
        return true;
    }

    private void clearChatSuggests() {
        chatSuggests.clear();
        chatSuggestRects.clear();
        chatSuggestFor = null;
        chatSuggestApplied = null;
        chatLastReqCmd = null;
        chatSuggestIndex = 0;
    }

    public boolean charTyped(char chr, int modifiers) {
        DirectRenderContext ctx = ctx(null, lastMx, lastMy);
        for (CompactTextInput input : activeInputs) {
            if (input.isFocused() && input.charTyped(ctx, chr, modifiers)) return true;
        }
        return false;
    }

    public boolean hasFocusedTextInput() {
        for (CompactTextInput input : activeInputs) if (input.isFocused()) return true;
        return false;
    }

    public void clearFocus() {
        profileName.setFocused(false); serverAddress.setFocused(false); customConcurrency.setFocused(false);
        customDelay.setFocused(false); autoPing.setFocused(false); accountSearch.setFocused(false);
        chatInput.setFocused(false); macroSearch.setFocused(false); quickName.setFocused(false); quickArgs.setFocused(false);
    }

    private DirectRenderContext ctx(GuiGraphicsExtractor g, int mx, int my) {
        return new DirectRenderContext(g, font, DirectViewport.current(1.0f), theme, mx, my, delta);
    }

    private void place(GuiGraphicsExtractor g, CompactTextInput input, int x, int y, int w) {
        input.setBounds(x, y, Math.max(10, w), 16);
        input.render(ctx(g, lastMx, lastMy));
        if (interactionAllowed(x, y, Math.max(10, w), 16)) activeInputs.add(input);
        else input.setFocused(false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline, int color,
                        int mx, int my, Runnable action) {
        button(g, x, y, w, h, label, outline, color, mx, my, action, false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline, int color,
                        int mx, int my, Runnable action, boolean playIcon) {
        if (w <= 0 || h <= 0) return;
        boolean enabled = action != null;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;

        autismclient.gui.vanillaui.components.Button.Tone tone = outline == success() ? autismclient.gui.vanillaui.components.Button.Tone.SUCCESS
            : outline == danger() ? autismclient.gui.vanillaui.components.Button.Tone.DANGER
            : autismclient.gui.vanillaui.components.Button.Tone.NORMAL;
        autismclient.gui.vanillaui.components.Button.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            UiBounds.of(x, y, w, h), label, tone, hover, false);
        if (!enabled) UiRenderer.rect(g, UiBounds.of(x, y, w, h), 0x66000000);
        if (playIcon) UiRenderer.play(g, x + 5, y + Math.max(1, (h - 8) / 2), 8, enabled ? color : muted());
        if (enabled && interactionAllowed(x, y, w, h)) hotspots.add(new Hotspot(x, y, w, h, action));
    }

    private void toggleButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active,
                              int mx, int my, Runnable action) {
        if (w <= 0 || h <= 0) return;
        boolean enabled = action != null;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;

        int fill = hover ? tint(accent(), 0x34) : active ? tint(success(), 0x16) : CARD;
        UiRenderer.frame(g, UiBounds.of(x, y, w, h), enabled ? fill : DISABLED,
            active ? success() : border());
        draw(g, label, x + w / 2, y + Math.max(2, (h - 8) / 2), enabled ? text() : muted(), true, w - 8);
        if (enabled && interactionAllowed(x, y, w, h)) hotspots.add(new Hotspot(x, y, w, h, action));
    }

    private void row(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int outline,
                     int mx, int my, Runnable action) {
        if (h <= 0 || w <= 0) return;
        boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;

        boolean emphasized = outline != border();
        int fill = hover ? tint(outline, 0x30) : emphasized ? tint(outline, 0x16) : CARD;
        if (emphasized) UiRenderer.frame(g, UiBounds.of(x, y, w, h), fill, outline);
        else UiRenderer.rect(g, UiBounds.of(x, y, w, h), fill);
        draw(g, label, x + 5, y + Math.max(2, (h - 8) / 2), action == null ? muted() : text(), false, w - 10);
        if (action != null && interactionAllowed(x, y, w, h)) hotspots.add(new Hotspot(x, y, w, h, action));
    }

    private void emptyState(GuiGraphicsExtractor g, String title, String sub, int x, int y, int w, int h,
                            int mx, int my, Runnable action) {
        draw(g, title, x + w / 2, y + h / 2 - 16, text(), true, w - 12);
        draw(g, sub, x + w / 2, y + h / 2 - 3, muted(), true, w - 12);
        button(g, x + w / 2 - 50, y + h / 2 + 14, 100, 18, "Open Setup", success(), text(), mx, my, action);
    }

    private void draw(GuiGraphicsExtractor g, String value, int x, int y, int color, boolean centered, int maxWidth) {
        String safe = clean(value);
        Identifier fontId = theme.fontFor(UiTone.BODY);
        safe = UiText.trimToWidthEllipsis(font, safe, Math.max(1, maxWidth), fontId, color);
        int dx = centered ? x - font.width(safe) / 2 : x;
        UiText.draw(g, font, safe, fontId, color, dx, y, false);
    }

    private void scrollbar(GuiGraphicsExtractor g, Viewport viewport, int offset, int maxOffset, int mx, int my,
                           String id, java.util.function.IntConsumer setOffset) {
        if (viewport == Viewport.NONE || viewport.h() < 12 || maxOffset <= 0) return;
        int trackH = viewport.h() - 4;
        int thumbH = Math.min(trackH, Math.max(8, trackH * trackH / Math.max(trackH + maxOffset, 1)));
        int travel = Math.max(0, trackH - thumbH);
        int trackX = viewport.x() + viewport.w() - 6;
        int trackY = viewport.y() + 2;
        int top = trackY + (int) ((long) travel * clamp(offset, 0, maxOffset) / maxOffset);

        boolean hovered = mx >= trackX && mx < trackX + 6 && my >= trackY && my < trackY + trackH;
        boolean dragging = id.equals(scrollbarDragId);
        autismclient.gui.vanillaui.components.Scrollbar.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            new autismclient.gui.vanillaui.components.Scrollbar.Metrics(
                UiBounds.of(trackX, trackY, 6, trackH),
                UiBounds.of(trackX, top, 6, thumbH),
                maxOffset),
            hovered, dragging);
        hotspots.add(new Hotspot(trackX, trackY, 6, trackH,
            () -> startScrollbarDrag(id, my, top, thumbH, trackY, travel, maxOffset, setOffset)));
    }

    private void startScrollbarDrag(String id, int my, int thumbTop, int thumbH, int trackY, int travel, int maxOffset,
                                    java.util.function.IntConsumer setOffset) {
        scrollbarGrab = (my >= thumbTop && my < thumbTop + thumbH) ? my - thumbTop : thumbH / 2;
        scrollbarDragId = id;
        scrollbarTrackY = trackY;
        scrollbarTravel = travel;
        scrollbarMax = maxOffset;
        scrollbarSetter = setOffset;
    }

    private void applyScrollbarDrag(int my) {
        if (scrollbarDragId == null || scrollbarSetter == null) return;
        int thumbTop = Math.max(scrollbarTrackY, Math.min(scrollbarTrackY + scrollbarTravel, my - scrollbarGrab));
        int value = scrollbarTravel <= 0 ? 0 : (thumbTop - scrollbarTrackY) * scrollbarMax / scrollbarTravel;
        scrollbarSetter.accept(Math.max(0, Math.min(scrollbarMax, value)));
    }

    private List<AccountChoice> filteredAccounts(MultiProfile profile) {
        String query = clean(accountSearch.text()).toLowerCase(Locale.ROOT);
        int filterMask = 0;
        for (AutismAccountType filter : accountFilters) filterMask |= 1 << filter.ordinal();

        long accountRevision = AutismAccountManager.get().changeRevision();
        if (cachedAccountSourceSize < 0 || accountRevision != cachedAccountSourceRevision) {
            AutismAccountManager accountManager = AutismAccountManager.get();
            cachedAccountSource = List.copyOf(accountManager.all());
            cachedAccountSourceSize = cachedAccountSource.size();
            cachedAccountSourceRevision = accountRevision;
            cachedAccountChoiceProfile = null;
        }
        int sessionCount = profile == null ? 0 : profile.sessions.size();
        long uiRevision = MultiManager.get().uiRevision();
        if (query.equals(cachedAccountChoiceQuery)
                && filterMask == cachedAccountChoiceFilterMask
                && profile == cachedAccountChoiceProfile
                && sessionCount == cachedAccountChoiceSessionCount
                && uiRevision == cachedAccountChoiceUiRevision) {
            return cachedAccountChoices;
        }

        List<AccountChoice> out = new ArrayList<>();
        String defaultName = AutismAccountSessionSwitcher.getOriginalUser().getName();
        AccountChoice current = new AccountChoice(MultiProfile.DEFAULT_ACCOUNT_ID, defaultName + " (Default)",
            AutismAccountSessionSwitcher.getOriginalUser().getAccessToken().isBlank() ? AutismAccountType.Cracked : AutismAccountType.Session,
            MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID));
        if (accountFilters.contains(current.type()) && current.label().toLowerCase(Locale.ROOT).contains(query)) out.add(current);
        java.util.Set<String> knownAccountIds = new java.util.HashSet<>(cachedAccountSource.size() * 2 + 1);
        for (AutismAccount account : cachedAccountSource) {
            AutismAccountType type = account.type == null ? AutismAccountType.Cracked : account.type;
            String label = clean(account.displayName());
            knownAccountIds.add(account.stableId());
            if (!accountFilters.contains(type) || !label.toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(new AccountChoice(account.stableId(), label, type, MultiManager.isCurrentRenderedAccount(account.stableId())));
        }
        List<MultiProfile.SessionSpec> shownSessions = profile == null ? List.of() : profile.sessions;
        for (MultiProfile.SessionSpec spec : shownSessions) {
            boolean known = spec.accountId().equals(MultiProfile.DEFAULT_ACCOUNT_ID)
                || knownAccountIds.contains(spec.accountId());
            if (!known && (query.isBlank() || spec.accountId().toLowerCase(Locale.ROOT).contains(query))) {
                out.add(new AccountChoice(spec.accountId(), "Missing: " + spec.accountId(), AutismAccountType.Cracked, false));
            }
        }
        cachedAccountChoiceQuery = query;
        cachedAccountChoiceFilterMask = filterMask;
        cachedAccountChoiceProfile = profile;
        cachedAccountChoiceSessionCount = sessionCount;
        cachedAccountChoiceUiRevision = uiRevision;
        cachedAccountChoices = List.copyOf(out);
        return cachedAccountChoices;
    }

    private List<AutismMacro> filteredMacros() {
        String query = clean(macroSearch.text()).toLowerCase(Locale.ROOT);
        long revision = AutismMacroManager.get().getRevision();
        if (revision == cachedMacroRevision && query.equals(cachedMacroQuery)) return cachedMacros;
        cachedMacroRevision = revision;
        cachedMacroQuery = query;

        cachedMacros = AutismMacroManager.get().getAll().stream()
            .filter(m -> m != null && m.name != null && m.name.toLowerCase(Locale.ROOT).contains(query))
            .toList();
        return cachedMacros;
    }

    private List<MultiProfile> savedProfiles() {
        MultiProfileManager manager = MultiProfileManager.get();
        long revision = manager.revision();
        if (revision != cachedProfilesRevision) {
            cachedProfilesRevision = revision;
            cachedProfiles = manager.all();
        }
        return cachedProfiles;
    }

    private MultiProfile activeProfileSnapshot(MultiManager manager) {
        long uiRevision = manager.uiRevision();
        long sessionRevision = manager.sessionRevision();
        if (uiRevision != cachedActiveUiRevision || sessionRevision != cachedActiveSessionRevision) {
            cachedActiveUiRevision = uiRevision;
            cachedActiveSessionRevision = sessionRevision;
            cachedActiveProfile = manager.activeProfile();
        }
        return cachedActiveProfile;
    }

    private List<String> macroCompatibility(String name) {
        long revision = AutismMacroManager.get().getRevision();
        String safeName = name == null ? "" : name;
        if (revision == cachedCompatibilityRevision && safeName.equals(cachedCompatibilityName)) return cachedCompatibility;
        cachedCompatibilityRevision = revision;
        cachedCompatibilityName = safeName;
        cachedCompatibility = MultiManager.get().macroCompatibility(safeName);
        return cachedCompatibility;
    }

    private MultiProfile.SessionSpec selectedSpec(String id) {
        return selectedSpec(draft, id);
    }

    private static MultiProfile.SessionSpec selectedSpec(MultiProfile profile, String id) {
        if (profile == null) return null;
        for (MultiProfile.SessionSpec spec : profile.sessions) if (spec.accountId().equals(id)) return spec;
        return null;
    }

    private String proxyLabel(String id) {
        return proxyLabel(draft, id);
    }

    private String proxyLabel(MultiProfile profile, String id) {
        MultiProfile.ProxyMode mode = profile == null ? MultiProfile.ProxyMode.Off : profile.proxyMode;
        if (mode == MultiProfile.ProxyMode.Off) return "Proxy Off";
        if (mode == MultiProfile.ProxyMode.Auto) return "Best Proxy";
        if (id == null || id.isBlank()) return "Proxy Off";
        if (MultiProfile.BEST_PROXY_ID.equals(id)) return "Best Proxy";
        AutismProxy proxy = AutismProxyManager.get().findById(id);
        return proxy == null ? "Missing" : proxy.displayName();
    }

    private String proxyModeLabel() {
        return switch (draft.proxyMode) {
            case Off -> "Off";
            case Auto -> "Auto";
            case Manual -> "Manual";
        };
    }

    private void configureSetupDropdown(CompactDropdown dropdown, int x, int y, int width, int selected, String label) {
        dropdown.setBounds(x, y, Math.max(1, width), 17).setSelectedIndex(selected).setButtonLabelOverride(label);
        dropdown.active = true;
        frameDropdowns.add(dropdown);
    }

    private static String commonManualProxyId(MultiProfile profile) {
        if (profile == null || profile.sessions.isEmpty()) return "";
        String common = profile.sessions.getFirst().proxyId();
        for (int i = 1; i < profile.sessions.size(); i++) {
            if (!java.util.Objects.equals(common, profile.sessions.get(i).proxyId())) return null;
        }
        return common == null ? "" : common;
    }

    private static Map<String, Integer> manualProxyUsage(MultiProfile profile) {
        if (profile == null || profile.sessions.isEmpty()) return Map.of();
        Map<String, Integer> usage = new java.util.LinkedHashMap<>();
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (spec.direct() || spec.bestProxy()) continue;
            usage.merge(spec.proxyId(), 1, Integer::sum);
        }
        return Map.copyOf(usage);
    }

    private static List<String> pingOptionLabels() {
        List<String> labels = new ArrayList<>(PING_OPTIONS.length);
        for (int option : PING_OPTIONS) labels.add(option + "ms");
        return labels;
    }

    private static int pingIndex(int value) {
        int index = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < PING_OPTIONS.length; i++) {
            int distance = Math.abs(PING_OPTIONS[i] - value);
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        return index;
    }

    private String currentServer() {
        return mc != null && mc.getCurrentServer() != null && mc.getCurrentServer().ip != null
            ? clean(mc.getCurrentServer().ip) : "";
    }

    private int sessionColor(MultiSession.Snapshot snapshot) {

        return switch (snapshot.displayState(System.currentTimeMillis())) {
            case GREEN -> success();
            case RED -> DISCONNECTED_RED;
            case YELLOW -> CONNECTING_YELLOW;
        };
    }

    private void result(MultiManager.BroadcastResult result) {
        if (result == null) { status("Failed", DANGER_STOCK); return; }
        if (result.failed() > 0) status("Failed " + result.failed(), DANGER_STOCK);
        else if (result.sent() > 0) status("Sent " + result.sent(), SUCCESS_STOCK);
        else if (result.skipped() > 0) status("Skipped", MUTED_STOCK);
        else status("Nothing sent", MUTED_STOCK);
    }

    private void status(String text, int color) {
        status = MultiManager.singleLine(text, 80);
        statusColor = color;
    }

    private int themedStatus() {
        if (statusColor == SUCCESS_STOCK) return success();
        if (statusColor == DANGER_STOCK) return danger();
        if (statusColor == WARN_STOCK) return warn();
        return muted();
    }

    private void refreshTheme() {  }
    private int border() { return AutismTheme.recolor(BORDER_STOCK, AutismTheme.Channel.OUTLINE); }
    private int accent() { return AutismTheme.recolor(ACCENT_STOCK, AutismTheme.Channel.ACCENT); }
    private int success() { return AutismTheme.recolor(SUCCESS_STOCK, AutismTheme.Channel.SUCCESS); }
    private int danger() { return AutismTheme.recolor(DANGER_STOCK, AutismTheme.Channel.DANGER); }
    private int text() { return AutismTheme.recolor(TEXT_STOCK, AutismTheme.Channel.TEXT); }
    private int muted() { return AutismTheme.recolor(MUTED_STOCK, AutismTheme.Channel.TEXT); }
    private int warn() { return AutismTheme.recolor(WARN_STOCK, AutismTheme.Channel.ACCENT); }

    private static int tint(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private boolean interactionAllowed(int x, int y, int w, int h) {
        return interactionClip == Viewport.NONE || interactionClip.contains(x, y, w, h);
    }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; } }
    private static String clean(String value) {
        return MultiManager.singleLine(value == null ? "" : value.replace('\u00A0', ' '), 512);
    }

    private boolean ctrlDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean shiftDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
        boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record ChatHitRow(MultiManager.ChatLine line, int lineIndex, int y,
                              net.minecraft.network.chat.FormattedText hit, String text) {
    }

    private record RowHit(int x, int y, int w, int h, String id) {
        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Viewport(int x, int y, int w, int h) {
        private static final Viewport NONE = new Viewport(0, 0, 0, 0);
        boolean hit(int mx, int my) { return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h; }
        boolean contains(int rx, int ry, int rw, int rh) {
            return rw > 0 && rh > 0 && rx >= x && ry >= y && rx + rw <= x + w && ry + rh <= y + h;
        }
        UiBounds bounds() { return UiBounds.of(x, y, Math.max(1, w), Math.max(1, h)); }
    }

    private record AccountChoice(String id, String label, AutismAccountType type, boolean current) { }
}
