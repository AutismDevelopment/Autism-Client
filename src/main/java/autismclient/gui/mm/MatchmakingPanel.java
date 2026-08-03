package autismclient.gui.mm;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.TextWrapLayout;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.commands.AutismCommands;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismClipboardHelper;
import autismclient.util.AutismDiscordLogin;
import autismclient.util.AutismLinks;
import autismclient.util.AutismFilterViewOverlay;
import autismclient.util.AutismGuiViewOverlay;
import autismclient.util.AutismModuleViewOverlay;
import autismclient.util.AutismSharePickerOverlay;
import autismclient.util.AutismUiScale;
import autismclient.util.AutismItemNbtInspectOverlay;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPacketInspectOverlay;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.IAutismOverlay;
import autismclient.util.mm.Lobby;
import autismclient.util.mm.LobbyListing;
import autismclient.util.mm.LobbySettings;
import autismclient.util.mm.MatchmakingManager;
import autismclient.util.mm.MmBlobs;
import autismclient.util.mm.MmCardActions;
import autismclient.util.mm.MmText;
import autismclient.util.mm.MmChatLine;
import autismclient.util.mm.MmPeer;
import autismclient.util.mm.MmPrefs;
import autismclient.util.mm.MmShare;
import autismclient.util.mm.msg.MmMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public final class MatchmakingPanel {

    public interface Host {

        Screen returnScreen();
    }

    private static final int BG = 0xF40E0E10;
    private static final int PANEL_SOFT = 0xC8141417;
    private static final int CARD_BG = 0x2A121214;
    private static final int DISABLED_FILL = 0x18111113;
    private static final int DISABLED_BORDER = 0xFF2A2A2E;
    private static final int WARN = 0xFFFFC857;
    private static final int INFO = 0xFF8EA0FF;

    private static final int BORDER_STOCK = 0xFF332428;
    private static final int BORDER_BRIGHT_STOCK = 0xFF7A5560;
    private static final int ACCENT_STOCK = 0xFF35D873;
    private static final int TEXT_STOCK = 0xFFF2F2F2;
    private static final int MUTED_STOCK = 0xFF9A9A9A;
    private static final int ERROR_STOCK = 0xFFFF5B5B;

    private int BORDER = BORDER_STOCK, BORDER_BRIGHT = BORDER_BRIGHT_STOCK, ACCENT = ACCENT_STOCK,
        TEXT = TEXT_STOCK, MUTED = MUTED_STOCK, ERROR = ERROR_STOCK;

    private enum Tab { LOBBIES("Lobbies"), CHAT("Chat"), MEMBERS("Members"), SETTINGS("Settings");
        final String label; Tab(String l) { this.label = l; } }
    private enum LobbyPane { BROWSE, CREATE, JOIN }

    private final Host host;
    private final Font font;
    private final CompactTheme theme = new CompactTheme();
    private final MatchmakingManager mm = MatchmakingManager.get();
    private final MmPrefs prefs = MmPrefs.get();
    private final List<Hotspot> hotspots = new ArrayList<>();

    private Tab activeTab = Tab.LOBBIES;
    private LobbyPane lobbyPane = LobbyPane.BROWSE;
    private boolean joinPublicMode = true;
    private boolean createPublic = true;
    private boolean createAnnouncement;
    private final int[] scroll = new int[Tab.values().length];

    private final int[] contentHeight = new int[Tab.values().length];
    private int chromeHeight;
    private int lastContentTop;
    private int availableContentH;

    private static final int CHAT_CHROME = 60;
    private static final int MIN_CHAT_LOG = 14;
    private int chatViewPx, chatMaxScrollPx;
    private CompactScrollbar.Metrics chatScrollbar;
    private boolean chatScrollbarDragging;
    private int chatScrollGrab;

    private ItemStack chatTooltipItem = ItemStack.EMPTY;
    private int chatTooltipMx, chatTooltipMy;
    private int chatLogTop, chatLogBottom;

    private int hitGateTop = Integer.MIN_VALUE, hitGateBottom = Integer.MAX_VALUE;

    private final CompactTextInput chatInput = field("Message, /command, or paste a shared hash…", 100_000, false);
    private final CompactTextInput lobbyName = field("Lobby name", 32, false);
    private final CompactTextInput server = field("Server (optional)", 48, false);
    private final CompactTextInput maxPlayers = field("40", 4, true);
    private final CompactTextInput passphrase = field("Paste the Room Key", 64, false);
    private final CompactTextInput joinCode = field("Paste the Room Code", 24, false);
    private final List<CompactTextInput> activeInputs = new ArrayList<>();

    private AutismPacketInspectOverlay inspectOverlay;
    private AutismGuiViewOverlay guiViewOverlay;
    private AutismFilterViewOverlay filterViewOverlay;
    private AutismModuleViewOverlay moduleViewOverlay;
    private AutismSharePickerOverlay sharePicker;

    private static final int ICON_CACHE_MAX = 64;
    private final Map<String, ItemStack> itemIconCache = new LinkedHashMap<>();

    private AutismTheme.State cachedThemeState;
    private int cachedChatPx, cachedChatPxVersion = -1, cachedChatPxWidth = -1;
    private long cachedNamesRosterVersion = Long.MIN_VALUE;
    private int cachedNamesMemberCount = -1;
    private String cachedNamesSelf = "";
    private Map<String, String> cachedNames = Map.of();

    private static final int CHAT_ROW_STEP = 10;
    private static final int CHAT_MAX_ROWS = 3;
    private int lastChatLogFullW;
    private float delta;
    private int lastMx, lastMy;
    private int bx, by, bw, bh;

    private final List<MemberRow> memberRows = new ArrayList<>();
    private final List<CtxItem> ctxItems = new ArrayList<>();
    private boolean ctxOpen;
    private int ctxX, ctxY;
    private String ctxFp = "";
    private boolean ctxBanConfirm;

    private record MemberRow(int x, int y, int w, int h, String fp, int gateTop, int gateBottom) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= gateTop && my < gateBottom;
        }
    }
    private static final class CtxItem {
        final int x, y, w, h; final String label; final int color; final Runnable action; final boolean closes;
        CtxItem(int x, int y, int w, int h, String label, int color, Runnable action, boolean closes) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; this.color = color; this.action = action; this.closes = closes;
        }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    public MatchmakingPanel(Host host, Font font) {
        this.host = host;
        this.font = font;
        this.createPublic = prefs.defaultPublic();
        lobbyName.setText(prefs.defaultLobbyName());
        server.setText(prefs.defaultServer());
        maxPlayers.setText(prefs.defaultMaxPlayers() <= 0 ? "" : Integer.toString(prefs.defaultMaxPlayers()));

        lobbyName.setOnChange(prefs::setDefaultLobbyName);
        server.setOnChange(prefs::setDefaultServer);
        maxPlayers.setOnChange(v -> prefs.setDefaultMaxPlayers(parseMax(v)));

        chatInput.setMultiline(true).setSubmitOnEnter(true);
        chatInput.setOnSubmit(v -> doSend());
        joinCode.setOnSubmit(v -> doJoinPublic());
    }

    private CompactTextInput field(String placeholder, int maxLen, boolean numeric) {
        CompactTextInput f = new CompactTextInput();
        f.setPlaceholder(placeholder).setMaxLength(maxLen).setFieldHeight(16);
        if (numeric) f.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        return f;
    }

    public void openDirectory() { mm.openDirectory(); }
    public void closeDirectory() { mm.closeDirectory(); }

    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float partial) {
        refreshTheme();
        this.delta = partial;
        this.lastMx = mouseX; this.lastMy = mouseY;
        this.bx = x; this.by = y; this.bw = w; this.bh = h;
        hotspots.clear();
        activeInputs.clear();
        memberRows.clear();
        if (!mm.inLobby() && (activeTab == Tab.CHAT || activeTab == Tab.MEMBERS)) activeTab = Tab.LOBBIES;
        if (mm.inLobby() && activeTab == Tab.LOBBIES) activeTab = Tab.CHAT;
        if (activeTab != Tab.MEMBERS && ctxOpen) closeContextMenu();

        UiRenderer.rect(g, UiBounds.of(x, y, w, h), BG);

        if (!autismclient.util.AutismDiscordLogin.hasSession()) { renderSignInGate(g, x, y, w, h, mouseX, mouseY); return; }

        MatchmakingManager.JoinPhase jp = mm.joinPhase();
        if (jp != MatchmakingManager.JoinPhase.NONE) { renderJoiningGate(g, x, y, w, h, mouseX, mouseY, jp); return; }

        if (!mm.matchmakingReady()) { renderConnectingGate(g, x, y, w, h, mouseX, mouseY); return; }

        Lobby lb = mm.currentLobby();
        String status = mm.inLobby()
            ? "In lobby: " + lb.name + (lb.announcement ? "  [Announcement]" : "")
                + "   (" + (mm.currentLobbyOfficial() ? mm.memberCount() + " users" : mm.memberCount() + "/" + maxLabel(lb.maxPlayers)) + ")"
            : "Not in a lobby";
        drawText(g, status, x + 8, y + 6, mm.inLobby() ? ACCENT : MUTED, false, w - 16);
        String subline = !mm.inLobby() ? "Browse public lobbies or create your own below."
            : lb.announcement && !mm.canSend() ? "Announcement lobby  •  Read-only"
            : lb.announcement ? "Announcement lobby  •  You can send"
            : lb.isPublic ? "Invite with the Room Code in the Members tab."
                          : "Invite with the Room Key in the Members tab.";
        drawText(g, subline, x + 8, y + 17, MUTED, false, w - 16);

        int tabY = y + 30;
        int tabH = 18;
        int n = Tab.values().length;
        int tabW = (w - 16 - (n - 1) * 4) / n;
        int tx = x + 8;
        for (Tab tab : Tab.values()) {
            if (tab == Tab.LOBBIES && mm.inLobby()) {

                boolean hover = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + tabH;
                UiRenderer.frame(g, UiBounds.of(tx, tabY, tabW, tabH), tint(ERROR, hover ? 0xEE : 0xCC), ERROR);
                drawText(g, "Leave", tx + tabW / 2, tabY + (tabH - 8) / 2, 0xFFFFFFFF, true, tabW - 4);
                addHotspot(tx, tabY, tabW, tabH, this::doLeave);
            } else {
                boolean active = tab == activeTab;
                boolean usable = tabUsable(tab);
                chipColored(g, tx, tabY, tabW, tabH, tab.label, ACCENT, active, usable, mouseX, mouseY,
                    usable ? () -> { activeTab = tab; clearFocus(); } : null);
            }
            tx += tabW + 4;
        }

        int contentTop = renderLobbyServerBar(g, x, tabY + tabH + 6, w, mouseX, mouseY);
        int contentBottom = y + h - 6;
        int cx = x + 8;
        int cw = w - 16;
        this.chromeHeight = contentTop - y;
        this.lastContentTop = contentTop;
        this.availableContentH = Math.max(0, contentBottom - contentTop);
        if (activeTab != Tab.CHAT) {
            scroll[activeTab.ordinal()] = Math.max(0, Math.min(scroll[activeTab.ordinal()], maxScrollFor(activeTab)));
        }

        DirectRenderContext ctx = renderCtx(g, mouseX, mouseY);
        switch (activeTab) {
            case LOBBIES -> renderLobbies(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
            case CHAT -> renderChat(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
            case MEMBERS -> renderMembers(g, cx, contentTop, cw, contentBottom, mouseX, mouseY);
            case SETTINGS -> renderSettings(g, ctx, cx, contentTop, cw, contentBottom, mouseX, mouseY);
        }

        renderContextMenu(g, mouseX, mouseY);
    }

    private void renderSignInGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        int cx = x + w / 2;
        int ty = y + Math.max(14, h / 2 - 64);
        drawText(g, "Matchmaking", cx, ty, ACCENT, true, w - 16);
        ty += 18;
        String notice = autismclient.util.AutismDiscordLogin.authGateNotice();
        boolean gated = autismclient.util.AutismDiscordLogin.isGateCode(notice);
        String[] lines;
        if (gated) {

            lines = new String[]{
                "Your shit's out of date.",
                "",
                "Grab the latest from autismclient.com,",
                "then restart Minecraft and sign back in.",
            };
        } else {
            lines = new String[]{
                "Find players, share macros, and TPA across",
                "servers, all end to end encrypted.",
                "",
                "You must be a member of our Discord to use it.",
                "Sign in with Discord to continue.",
            };
        }
        for (int i = 0; i < lines.length; i++) {
            int col = lines[i].isEmpty() ? MUTED : (gated && i == 0 ? ERROR : TEXT);
            drawText(g, lines[i], cx, ty, col, true, w - 24);
            ty += 11;
        }
        ty += 10;
        int bw2 = Math.min(190, w - 32);
        int bxc = cx - bw2 / 2;
        if (gated) {
            button(g, bxc, ty, bw2, 18, "Get the latest version", ACCENT, TEXT, mx, my,
                () -> AutismLinks.open(AutismLinks.WEBSITE));
            ty += 24;
        }
        button(g, bxc, ty, bw2, 18, "Sign in with Discord", gated ? BORDER : ACCENT, TEXT, mx, my, this::signInDiscord);
        ty += 24;
        button(g, bxc, ty, bw2, 16, "Join our Discord", BORDER, TEXT, mx, my, () -> AutismLinks.open(AutismLinks.DISCORD));
    }

    private void renderConnectingGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        int cx = x + w / 2;
        int ty = y + Math.max(14, h / 2 - 56);
        drawText(g, "Matchmaking", cx, ty, ACCENT, true, w - 16);
        ty += 20;
        if (mm.connState() == MatchmakingManager.ConnState.OFFLINE) {
            if (AutismDiscordLogin.isGateCode(AutismDiscordLogin.authGateNotice())) {

                drawText(g, AutismDiscordLogin.errorMessage(AutismDiscordLogin.authGateNotice()), cx, ty, ERROR, true, w - 24); ty += 13;
                String min = AutismDiscordLogin.requiredMinVersion();
                if (!min.isEmpty()) { drawText(g, "Minimum version: " + min, cx, ty, MUTED, true, w - 24); ty += 11; }
                drawText(g, "Get the latest download at autismclient.com", cx, ty, MUTED, true, w - 24); ty += 11;
            } else {
                drawText(g, "Can't reach the matchmaking server.", cx, ty, ERROR, true, w - 24); ty += 13;
                drawText(g, "It may be down or restarting.", cx, ty, MUTED, true, w - 24); ty += 11;
                drawText(g, "Check your connection, then try again.", cx, ty, MUTED, true, w - 24); ty += 11;
                String err = mm.connError();
                if (!err.isEmpty()) { drawText(g, err, cx, ty, tint(MUTED, 0xAA), true, w - 24); ty += 12; }
            }
            ty += 10;
            int bw2 = Math.min(190, w - 32);
            int bxc = cx - bw2 / 2;
            button(g, bxc, ty, bw2, 18, "Try again", ACCENT, TEXT, mx, my, mm::reconnectNow);
            ty += 24;
            button(g, bxc, ty, bw2, 16, "Sign out", BORDER, TEXT, mx, my, this::signOutDiscord);
        } else {
            long t = System.currentTimeMillis();
            String dots = ".".repeat((int) ((t / 400) % 4));
            drawText(g, "Connecting to matchmaking server" + dots, cx, ty, TEXT, true, w - 24); ty += 16;
            char spin = "|/-\\".charAt((int) ((t / 120) % 4));
            drawText(g, String.valueOf(spin), cx, ty, ACCENT, true, w - 24); ty += 16;
            drawText(g, "This only takes a moment.", cx, ty, MUTED, true, w - 24);
        }
    }

    private void renderJoiningGate(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, MatchmakingManager.JoinPhase jp) {
        int cx = x + w / 2;
        int ty = y + Math.max(14, h / 2 - 70);
        drawText(g, "Joining lobby", cx, ty, ACCENT, true, w - 16);
        ty += 20;
        if (jp == MatchmakingManager.JoinPhase.FAILED) {
            drawText(g, joinFailMessage(mm.joinError()), cx, ty, ERROR, true, w - 24); ty += 13;
            drawText(g, "Nothing was joined.", cx, ty, MUTED, true, w - 24); ty += 18;
            int bw2 = Math.min(190, w - 32);
            button(g, cx - bw2 / 2, ty, bw2, 18, "Back", ACCENT, TEXT, mx, my, mm::cancelJoin);
            return;
        }
        boolean reserving = jp == MatchmakingManager.JoinPhase.RESERVING;
        char spin = "|/-\\".charAt((int) ((System.currentTimeMillis() / 120) % 4));
        String[] steps = { "Checking membership", "Checking lobby ban", "Reserving a slot", "Connecting" };
        for (int i = 0; i < steps.length; i++) {
            boolean done = !reserving && i < 3;
            boolean active = reserving ? i < 3 : i == 3;
            String mark = done ? "[x] " : (active ? "[" + spin + "] " : "[ ] ");
            drawText(g, mark + steps[i], cx, ty, done ? ACCENT : (active ? TEXT : MUTED), true, w - 24);
            ty += 12;
        }
        ty += 8;
        int bw2 = Math.min(160, w - 32);
        button(g, cx - bw2 / 2, ty, bw2, 16, "Cancel", BORDER, TEXT, mx, my, mm::cancelJoin);
    }

    private String joinFailMessage(String err) {
        return switch (err == null ? "" : err) {
            case "no_such_lobby" -> "No lobby found for that code.";
            case "full" -> "That lobby is full.";
            case "lobby_banned" -> "You're banned from that lobby.";
            case "too_many" -> "You're in too many lobbies already.";
            case "busy" -> "Server busy. Try again.";
            case "unauthorized" -> "Sign in again.";
            case "old_version" -> "Update the mod.";
            case "network" -> "Can't reach matchmaking. Try again.";
            default -> "Couldn't join. Try again.";
        };
    }

    private void refreshTheme() {

        AutismTheme.State st = AutismTheme.active();
        if (st == cachedThemeState) return;
        cachedThemeState = st;
        BORDER = AutismTheme.recolor(BORDER_STOCK, Channel.OUTLINE);
        BORDER_BRIGHT = AutismTheme.recolor(BORDER_BRIGHT_STOCK, Channel.OUTLINE);
        ACCENT = AutismTheme.recolor(ACCENT_STOCK, Channel.ACCENT);
        TEXT = AutismTheme.recolor(TEXT_STOCK, Channel.TEXT);
        MUTED = AutismTheme.recolor(MUTED_STOCK, Channel.TEXT);
        ERROR = AutismTheme.recolor(ERROR_STOCK, Channel.DANGER);
    }

    private boolean tabUsable(Tab tab) {
        return switch (tab) { case CHAT, MEMBERS -> mm.inLobby(); default -> true; };
    }

    private int renderLobbyServerBar(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        Lobby lb = mm.currentLobby();
        if (lb == null) return y;
        final String ip = lb.server == null ? "" : lb.server.trim();
        if (ip.isEmpty() || MatchmakingManager.alreadyOn(ip)) return y;
        int rowH = 16, joinW = 44;

        autismclient.gui.vanillaui.components.Card.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x + 8, y, w - 16, rowH));
        drawText(g, "Server: " + MmBlobs.displayAddr(ip), x + 14, y + 4, INFO, false, w - 16 - joinW - 18);
        final String label = lb.name;
        button(g, x + w - 8 - joinW, y + 1, joinW, rowH - 2, "Join", INFO, TEXT, mx, my,
            () -> MmCardActions.confirmJoinServer(ip, label, host.returnScreen()));
        return y + rowH + 4;
    }

    private void renderLobbies(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {

        UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
        hitGateTop = top; hitGateBottom = bottom;
        try {
            int top0 = top - scrollOf(Tab.LOBBIES);
            int segW = (w - 8) / 3;
            chipColored(g, x, top0, segW, 18, "Browse", ACCENT, lobbyPane == LobbyPane.BROWSE, true, mx, my, () -> setPane(LobbyPane.BROWSE));
            chipColored(g, x + segW + 4, top0, segW, 18, "Create", ACCENT, lobbyPane == LobbyPane.CREATE, true, mx, my, () -> setPane(LobbyPane.CREATE));
            chipColored(g, x + (segW + 4) * 2, top0, w - (segW + 4) * 2, 18, "Join", ACCENT, lobbyPane == LobbyPane.JOIN, true, mx, my, () -> setPane(LobbyPane.JOIN));
            int paneTop = top0 + 26;
            int paneBottom = switch (lobbyPane) {
                case BROWSE -> renderBrowse(g, x, paneTop, w, top, bottom, mx, my);
                case CREATE -> renderCreate(g, ctx, x, paneTop, w, bottom, mx, my);
                case JOIN -> renderJoin(g, ctx, x, paneTop, w, bottom, mx, my);
            };
            setContentBottom(Tab.LOBBIES, paneBottom);
        } finally {
            hitGateTop = Integer.MIN_VALUE; hitGateBottom = Integer.MAX_VALUE;
            UiScissorStack.global().pop(g);
        }
    }

    private void setPane(LobbyPane pane) {
        lobbyPane = pane;
        clearFocus();
        scroll[Tab.LOBBIES.ordinal()] = 0;
    }

    private int renderBrowse(GuiGraphicsExtractor g, int x, int y, int w, int top, int bottom, int mx, int my) {
        drawText(g, "Public lobbies", x, y, TEXT, false, w - 60);
        button(g, x + w - 56, y - 2, 56, 13, "Refresh", BORDER, TEXT, mx, my, this::doRefresh);

        boolean refreshing = mm.directoryRefreshing();
        boolean hasRefreshFeedback = refreshing || mm.lastDirectoryPullMs() > 0;
        if (refreshing) {
            int barY = y + 14;
            UiRenderer.rect(g, UiBounds.of(x, barY, w, 2), tint(MUTED, 0x40));
            long t = System.currentTimeMillis();
            int segW = Math.max(24, w / 4);
            int off = (int) ((t / 5) % (w + segW)) - segW;
            int sx = Math.max(x, x + off), ex = Math.min(x + w, x + off + segW);
            if (ex > sx) UiRenderer.rect(g, UiBounds.of(sx, barY, ex - sx, 2), ACCENT);
        } else if (hasRefreshFeedback) {
            long ago = Math.max(0, (System.currentTimeMillis() - mm.lastDirectoryPullMs()) / 1000);
            drawText(g, "Refreshed " + ago + "s ago", x, y + 13, MUTED, false, w - 64);
        }
        y += hasRefreshFeedback ? 27 : 16;
        List<LobbyListing> listings = mm.directoryListings();
        if (listings.isEmpty()) {
            drawText(g, "No public lobbies announced yet.", x + 2, y + 2, MUTED, false, w - 4); y += 14;
            drawText(g, "Use Create to host one, or Join to enter a code/passphrase.", x + 2, y + 2, MUTED, false, w - 4);
            return y + 12;
        }
        for (LobbyListing l : listings) {
            boolean visible = y + 32 > top && y < bottom;
            if (visible) {

                autismclient.gui.vanillaui.components.Card.render(
                    autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, 30));

                drawText(g, l.name, x + 8, y + 4, l.official ? ERROR : TEXT, false, w - 96);
                String tags = l.official ? l.members + " users" : l.members + "/" + maxLabel(l.maxMembers);
                if (l.announcement) tags += "  •  Announcement";
                if (l.hostDupe == 0) tags += "  •  dupe hunting"; else if (l.hostDupe == 1) tags += "  •  has dupe";
                if (!l.server.isBlank()) tags += "  •  " + l.server;
                drawText(g, tags, x + 8, y + 16, MUTED, false, w - 96);
                boolean full = l.isFull();
                button(g, x + w - 80, y + 6, 72, 18, full ? "Full" : "Join", full ? BORDER : ACCENT, full ? MUTED : TEXT, mx, my,
                    full ? null : () -> { mm.joinPublic(l.lobbyId); afterJoin(); });
            }
            y += 34;
        }
        return y;
    }

    private int renderCreate(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int y, int w, int bottom, int mx, int my) {
        boolean pub = createPublic;
        drawText(g, "Visibility", x, y + 4, MUTED, false, 70);
        chipColored(g, x + 72, y, 70, 16, "Public", ACCENT, pub, true, mx, my, () -> createPublic = true);
        chipColored(g, x + 146, y, 70, 16, "Private", WARN, !pub, true, mx, my, () -> createPublic = false);
        y += 22;

        drawText(g, "Name *", x, y - 1, MUTED, false, 56);
        placeField(g, ctx, lobbyName, x + 72, y - 3, w - 72); y += 20;

        if (pub) {
            drawText(g, "Server", x, y - 1, MUTED, false, 56);
            placeField(g, ctx, server, x + 72, y - 3, w - 72); y += 20;
        }

        if (AutismDiscordLogin.isAdmin()) {
            drawText(g, "Messaging", x, y + 4, MUTED, false, 70);
            int mw = (w - 72 - 4) / 2;
            chipColored(g, x + 72, y, mw, 16, "Everyone", ACCENT, !createAnnouncement, true, mx, my,
                () -> createAnnouncement = false);
            chipColored(g, x + 72 + mw + 4, y, mw, 16, "Announcement", WARN, createAnnouncement, true, mx, my,
                () -> createAnnouncement = true);
            y += 22;
        } else {
            createAnnouncement = false;
        }

        drawText(g, "Max", x, y - 1, MUTED, false, 56);
        placeField(g, ctx, maxPlayers, x + 72, y - 3, 44);
        int capLimit = capLimit();
        drawText(g, "(blank = " + capLimit + ", max " + capLimit + ")", x + 122, y, MUTED, false, w - 130); y += 22;

        if (pub) {
            drawText(g, "Dupe *", x, y + 3, MUTED, false, 56);
            int dw = (w - 72 - 4) / 2;
            chipColored(g, x + 72, y, dw, 16, "Dupe Hunting", WARN, prefs.myDupeStatus() == 0, true, mx, my, () -> prefs.setMyDupeStatus(0));
            chipColored(g, x + 72 + dw + 4, y, dw, 16, "Has dupe", ACCENT, prefs.myDupeStatus() == 1, true, mx, my, () -> prefs.setMyDupeStatus(1));
            y += 22;
        }

        button(g, x, y, w, 20, pub ? "Create public lobby" : "Create private lobby", ACCENT, TEXT, mx, my, this::doCreate);
        drawText(g, pub ? "Name and dupe status are required." : "A secure Room Key is generated for you to share.",
            x, y + 24, MUTED, false, w);
        return y + 36;
    }

    private int renderJoin(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int y, int w, int bottom, int mx, int my) {
        drawText(g, "Join by", x, y + 4, MUTED, false, 56);
        chipColored(g, x + 60, y, 104, 16, "Public", ACCENT, joinPublicMode, true, mx, my, () -> { joinPublicMode = true; clearFocus(); });
        chipColored(g, x + 168, y, 130, 16, "Private", WARN, !joinPublicMode, true, mx, my, () -> { joinPublicMode = false; clearFocus(); });
        y += 24;
        if (joinPublicMode) {
            drawText(g, "Room Code", x, y - 1, MUTED, false, 70);
            placeField(g, ctx, joinCode, x + 72, y - 3, w - 72); y += 22;
            button(g, x, y, w, 20, "Join", ACCENT, TEXT, mx, my, this::doJoinPublic);
            drawText(g, "Paste the public Room Code someone shared with you.", x, y + 24, MUTED, false, w);
        } else {
            drawText(g, "Room Key", x, y - 1, MUTED, false, 70);
            placeField(g, ctx, passphrase, x + 72, y - 3, w - 72); y += 22;
            button(g, x, y, w, 20, "Join", ACCENT, TEXT, mx, my, this::doJoinPrivate);
            drawText(g, "Paste the private Room Key someone shared with you.", x, y + 24, MUTED, false, w);
        }
        return y + 36;
    }

    private void afterJoin() { activeTab = Tab.CHAT; clearFocus(); }

    private void doLeave() { mm.leave(); activeTab = Tab.LOBBIES; lobbyPane = LobbyPane.BROWSE; scroll[Tab.LOBBIES.ordinal()] = 0; mm.refreshDirectory(); clearFocus(); }

    private void doRefresh() { mm.refreshDirectory(); }

    private void doCreate() {
        String name = lobbyName.text().trim();
        if (name.isEmpty()) { AutismNotifications.show("Enter a lobby name.", WARN); return; }
        int max = parseMax(maxPlayers.text());
        int capLimit = capLimit();
        if (max <= 0) max = capLimit;
        if (max > capLimit) {
            max = capLimit;
            AutismNotifications.show("Max players is capped at " + capLimit + ".", WARN);
        }
        if (createPublic) {
            if (prefs.myDupeStatus() < 0) { AutismNotifications.show("Pick a dupe status (needs or has).", WARN); return; }
            mm.createLobby(new LobbySettings(name, true, max, null, server.text().trim(), "", createAnnouncement));
        } else {
            mm.createLobby(new LobbySettings(name, false, max, null, "", "", createAnnouncement));
        }
        prefs.setDefaultPublic(createPublic);
        afterJoin();
    }

    private void doJoinPublic() {
        String code = joinCode.text().trim();
        if (code.isEmpty()) { AutismNotifications.show("Enter a lobby code.", WARN); return; }
        mm.joinPublic(code); afterJoin();
    }

    private void doJoinPrivate() {
        String key = passphrase.text().trim();
        if (key.isEmpty()) { AutismNotifications.show("Paste the room key.", WARN); return; }
        mm.joinPrivate(key.toCharArray()); afterJoin();
    }

    private static int parseMax(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static int capLimit() {
        return switch (AutismDiscordLogin.role()) {
            case "god", "admin" -> LobbySettings.ADMIN_MAX_PLAYERS;
            case "gold" -> LobbySettings.GOLD_MAX_PLAYERS;
            case "aqua" -> LobbySettings.AQUA_MAX_PLAYERS;
            case "notbroke" -> LobbySettings.NOTBROKE_MAX_PLAYERS;
            case "blue" -> LobbySettings.BLUE_MAX_PLAYERS;
            default -> LobbySettings.USER_MAX_PLAYERS;
        };
    }

    private void renderChat(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {
        boolean canWrite = mm.canSend();
        chatInput.setEditable(canWrite).setPlaceholder(canWrite
            ? "Message, /command, or paste a shared hash…" : "Announcement is read-only");

        int composerFieldW = w - 56;
        int composerRows = Math.max(1, Math.min(3, chatInput.wrappedRowCount(ctx, composerFieldW)));
        int composerH = Math.max(16, chatInput.rowsToHeight(ctx, composerRows));
        int composerY = bottom - 3 - composerH;
        int toolR1 = composerY - 38;
        int chatBottom = toolR1 - 4;

        Map<String, String> names = disambiguate(mm.members());
        List<MmChatLine> lines = mm.chatSnapshot();
        this.lastChatLogFullW = w;
        if (lines.isEmpty()) {
            drawText(g, "No messages yet. Say hi, type a /command, or use the Share buttons below.", x, top + 2, MUTED, false, w);
        }

        int viewPx = Math.max(0, chatBottom - top);
        int[] heights = chatLineHeights(lines, w, names);
        boolean reserveBar = sum(heights) > viewPx;
        int logW = reserveBar ? w - 5 : w;
        if (reserveBar) heights = chatLineHeights(lines, logW, names);
        int logPx = sum(heights);
        this.chatViewPx = viewPx;
        this.chatMaxScrollPx = Math.max(0, logPx - chatViewPx);
        int sc = Math.max(0, Math.min(chatMaxScrollPx, scroll[Tab.CHAT.ordinal()]));
        scroll[Tab.CHAT.ordinal()] = sc;

        chatTooltipItem = ItemStack.EMPTY;
        chatLogTop = top; chatLogBottom = chatBottom;
        UiScissorStack.global().push(g, UiBounds.of(x, top, w, chatViewPx));
        try {
            int yPos = chatBottom + sc;
            for (int i = lines.size() - 1; i >= 0; i--) {
                MmChatLine line = lines.get(i);
                int lh = heights[i];
                yPos -= lh;
                if (yPos >= chatBottom) continue;
                if (yPos + lh <= top) break;
                if (line.isCard()) renderCard(g, line, names, x, yPos, logW, mx, my);
                else renderTextLine(g, line, names, x, yPos, logW, mx, my);
            }
        } finally {

            UiScissorStack.global().pop(g);
        }

        if (reserveBar) {
            int trackX = x + w - 3;
            chatScrollbar = CompactScrollbar.compute(logPx, chatViewPx, trackX, top, 3, chatViewPx, chatMaxScrollPx - sc);
            CompactScrollbar.draw(g, chatScrollbar, chatScrollbar.contains(mx, my), chatScrollbarDragging);
        } else {
            chatScrollbar = null;
        }

        renderShareToolbar(g, x, toolR1, w, mx, my);

        placeField(g, ctx, chatInput, x, composerY, composerFieldW, composerH);
        button(g, x + w - 50, bottom - 3 - 16, 50, 16, "Send", ACCENT, TEXT, mx, my,
            mm.inLobby() && canWrite ? this::doSend : null);

        if (chatTooltipItem != null && !chatTooltipItem.isEmpty())
            drawItemTooltip(g, chatTooltipItem, chatTooltipMx, chatTooltipMy);
    }

    private void drawItemTooltip(GuiGraphicsExtractor g, ItemStack stack, int mx, int my) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            List<Component> lines = Screen.getTooltipFromItem(mc, stack);
            if (lines == null || lines.isEmpty()) return;
            int tw = 0;
            for (Component c : lines) tw = Math.max(tw, font.width(c));
            int th = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            int tx = mx + 12, ty = my - 12;
            if (tx + tw + 4 > sw) tx = Math.max(4, mx - tw - 16);
            if (ty + th + 4 > sh) ty = sh - th - 4;
            if (ty < 4) ty = 4;

            g.nextStratum();
            UiRenderer.rect(g, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0xF0100010);
            UiRenderer.frame(g, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0, 0x505000A0);
            int ly = ty;
            for (Component c : lines) {
                g.text(font, c.getVisualOrderText(), tx, ly, 0xFFFFFFFF, true);
                ly += 10;
            }
        } catch (Throwable ignored) {

        }
    }

    private void renderShareToolbar(GuiGraphicsExtractor g, int x, int r1, int w, int mx, int my) {
        boolean in = mm.inLobby();
        boolean canShare = in && mm.canSend();
        Minecraft mc = Minecraft.getInstance();
        int gap = 4;
        int colW = (w - gap * 4) / 5;
        int r2 = r1 + 18;
        int qn = AutismSharedState.get().getDelayedPackets().size();
        boolean hasMacros = !AutismMacroManager.get().getAll().isEmpty();
        boolean onServer = MatchmakingManager.currentServerIp() != null;
        shareBtn(g, x, r1, colW, qn > 0 ? "Queue " + qn : "Queue", canShare && qn > 0, this::shareQueue, mx, my);
        shareBtn(g, x + (colW + gap), r1, colW, "Macro", canShare && hasMacros, this::toggleMacroPicker, mx, my);
        shareBtn(g, x + 2 * (colW + gap), r1, colW, "GUI", canShare && guiOpen(), this::shareGui, mx, my);
        shareBtn(g, x + 3 * (colW + gap), r1, colW, "Item", canShare && holdingItem(), this::shareItem, mx, my);
        shareBtn(g, x + 4 * (colW + gap), r1, colW, "Server", canShare && onServer, this::shareServer, mx, my);
        shareBtn(g, x, r2, colW, "Pos", canShare && mc.player != null, this::sharePosition, mx, my);
        shareBtn(g, x + (colW + gap), r2, colW, "TPA inv", canShare && onServer, this::shareTpaInvite, mx, my);
        shareBtn(g, x + 2 * (colW + gap), r2, colW, "Filter", canShare, this::shareFilter, mx, my);
        shareBtn(g, x + 3 * (colW + gap), r2, colW, "Module", canShare, this::toggleModulePicker, mx, my);
        shareBtn(g, x + 4 * (colW + gap), r2, colW, "Clear", mm.hasChat(), this::clearChat, mx, my);
    }

    private void toggleMacroPicker() {
        if (sharePicker == null) sharePicker = new AutismSharePickerOverlay(font);
        sharePicker.open("Share Macro", "Search macros...", filter -> {
            List<AutismSharePickerOverlay.Row> out = new ArrayList<>();
            String f = filter.toLowerCase(Locale.ROOT);
            for (AutismMacro m : AutismMacroManager.get().getAll()) {
                if (!f.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(f)) continue;
                AutismMacro macro = m;
                out.add(AutismSharePickerOverlay.Row.item(m.name, m.actions.size() + " steps", () -> shareMacro(macro)));
            }
            return out;
        });
    }

    private void toggleModulePicker() {
        if (sharePicker == null) sharePicker = new AutismSharePickerOverlay(font);
        sharePicker.open("Share Module", "Search modules...", filter -> {
            List<AutismSharePickerOverlay.Row> out = new ArrayList<>();
            String f = filter.toLowerCase(Locale.ROOT);
            List<Module> mods = new ArrayList<>();
            for (Module m : ModuleRegistry.all()) if (m.showInModuleMenu()) mods.add(m);
            mods.sort(Comparator.comparing((Module m) -> categoryLabel(m) + " " + m.name(), String.CASE_INSENSITIVE_ORDER));
            String lastCat = null;
            for (Module m : mods) {
                if (!f.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(f)) continue;
                String cat = categoryLabel(m);
                if (!cat.equals(lastCat)) { out.add(AutismSharePickerOverlay.Row.header(cat)); lastCat = cat; }
                Module module = m;
                out.add(AutismSharePickerOverlay.Row.item(m.name(), null, () -> shareModule(module)));
            }
            return out;
        });
    }

    private static String categoryLabel(Module m) {
        autismclient.modules.ModuleCategory c = m.category();
        return c == null ? "Other" : c.label();
    }

    private void shareBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean enabled, Runnable action, int mx, int my) {
        button(g, x, y, w, 16, label, enabled ? INFO : BORDER, TEXT, mx, my, enabled ? action : null);
    }

    private boolean guiOpen() { return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen; }
    private boolean holdingItem() { var p = Minecraft.getInstance().player; return p != null && !p.getMainHandItem().isEmpty(); }

    private void shareGui() {
        MmMessages.BlobOffer b = MmBlobs.captureGui();
        if (b == null) { AutismNotifications.show("Open a container to share its GUI.", WARN); return; }
        mm.offerBlob(b);
    }
    private void shareItem() {
        MmMessages.BlobOffer b = MmBlobs.captureHeldItem();
        if (b == null) { AutismNotifications.show("Hold an item to share it.", WARN); return; }
        mm.offerBlob(b);
    }
    private void sharePosition() {
        MmMessages.BlobOffer b = MmBlobs.capturePosition();
        if (b != null) mm.offerBlob(b);
    }
    private void shareFilter() {
        MmMessages.BlobOffer b = MmBlobs.captureFilter();
        if (b != null) mm.offerBlob(b);
    }
    private void shareServer() {
        MmMessages.BlobOffer b = MmBlobs.captureServer();
        if (b == null) { AutismNotifications.show("Join a server first.", WARN); return; }
        mm.offerBlob(b);
    }
    private void clearChat() { mm.clearChat(); }
    private void signInDiscord() {
        AutismNotifications.show("Opening Discord sign-in in your browser…", 0xFF35D873);
        autismclient.util.AutismDiscordLogin.signIn(err -> {
            if (err.isEmpty()) {
                mm.openDirectory();
                AutismNotifications.show("Signed in to matchmaking.", 0xFF35D873);
            }

            else if (!autismclient.util.AutismDiscordLogin.isGateCode(err))
                AutismNotifications.show(autismclient.util.AutismDiscordLogin.errorMessage(err), 0xFFFF5B5B);
        });
    }

    private void toggleKillSwitch() {
        boolean on = !prefs.killSwitch();
        prefs.setKillSwitch(on);
        AutismNotifications.show(on ? "Kill switch on. Sharing off." : "Kill switch off.", on ? 0xFFFFC857 : 0xFF35D873);
    }

    private void signOutDiscord() {

        mm.leave();
        autismclient.util.AutismDiscordLogin.signOut();
        AutismNotifications.show("Signed out of matchmaking.", 0xFFFFC857);
    }

    private void shareTpaInvite() {
        String name = mm.selfTpaName();
        if (name == null || name.isBlank()) { AutismNotifications.show("Can't read your username.", WARN); return; }
        mm.offerCommand("/tpa " + name);
    }

    private void renderTextLine(GuiGraphicsExtractor g, MmChatLine line, Map<String, String> names, int x, int y, int w, int mx, int my) {
        Identifier fontId = theme.fontFor(UiTone.BODY);
        if (line.system) {
            drawWrapped(g, "• " + line.text, x, y, w, w, MUTED, fontId);
            return;
        }
        String who = chatName(line, names);
        int nameColor = MatchmakingManager.nameColor(line.senderFpHex, line.self);
        int endX = drawRoleName(g, who, x, y, line.senderFpHex, line.self);
        UiText.draw(g, font, ": ", fontId, nameColor, endX, y, false);
        int nameW = (endX - x) + UiText.width(font, ": ", fontId, nameColor);

        int rows = drawWrapped(g, line.text, x, y, Math.max(1, w - nameW), w, TEXT, fontId, x + nameW);

        int blockH = rows * CHAT_ROW_STEP + 1;
        if (!line.text.isBlank() && my >= chatLogTop && my < chatLogBottom
                && mx >= x && mx < x + w && my >= y && my < y + blockH) {
            int bw = 34;
            button(g, x + w - bw, y, bw, 11, "Copy", BORDER, TEXT, mx, my, () -> copyText(line.text));
        }
    }

    private int drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int firstW, int restW, int color, Identifier fontId) {
        return drawWrapped(g, text, x, y, firstW, restW, color, fontId, x);
    }

    private int drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int firstW, int restW, int color, Identifier fontId, int firstX) {
        List<int[]> segs = wrapChatRows(text, firstW, restW);
        for (int r = 0; r < segs.size(); r++) {
            int[] seg = segs.get(r);
            boolean first = r == 0;
            boolean overflow = seg[2] == 1;
            int rx = first ? firstX : x;
            int rw = first ? firstW : restW;
            int ry = y + r * CHAT_ROW_STEP;
            if (overflow) drawText(g, text.substring(seg[0]), rx, ry, color, false, rw);
            else UiText.draw(g, font, text.substring(seg[0], seg[1]), fontId, color, rx, ry, false);
        }
        return segs.size();
    }

    private List<int[]> wrapChatRows(String text, int firstW, int restW) {
        Identifier fontId = theme.fontFor(UiTone.BODY);
        String src = text == null ? "" : text;
        TextWrapLayout.RangeWidth rw = (s, e) -> UiText.width(font, src.substring(s, e), fontId, TEXT);
        List<int[]> rows = new ArrayList<>();
        int cursor = 0, len = src.length();
        while (cursor < len && rows.size() < CHAT_MAX_ROWS) {
            int width = Math.max(1, rows.isEmpty() ? firstW : restW);
            int nl = src.indexOf('\n', cursor);
            int limit = nl >= 0 ? nl : len;
            int end; boolean hard = false;
            if (cursor >= limit) {
                end = cursor; hard = true;
            } else {
                end = TextWrapLayout.nextLineEnd(src, cursor, limit, width, rw);
                if (end <= cursor) end = Math.min(limit, cursor + 1);
                if (end >= limit && nl >= 0) hard = true;
            }
            int renderEnd = end;
            while (renderEnd > cursor && Character.isWhitespace(src.charAt(renderEnd - 1))) renderEnd--;
            rows.add(new int[]{cursor, renderEnd, 0});
            if (hard) cursor = nl + 1;
            else { cursor = end; while (cursor < len && src.charAt(cursor) == ' ') cursor++; }
        }
        if (rows.isEmpty()) rows.add(new int[]{0, 0, 0});
        if (cursor < len) rows.get(rows.size() - 1)[2] = 1;
        return rows;
    }

    private int[] chatLineHeights(List<MmChatLine> lines, int logW, Map<String, String> names) {
        int[] h = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) h[i] = chatLineHeight(lines.get(i), logW, names);
        return h;
    }

    private int chatLineHeight(MmChatLine line, int logW, Map<String, String> names) {
        if (line.isCard()) return 30;
        return chatTextRows(line, logW, names) * CHAT_ROW_STEP + 1;
    }

    private int chatTextRows(MmChatLine line, int logW, Map<String, String> names) {
        if (logW <= 0) return 1;
        if (line.system) {
            String t = "• " + line.text;
            return t.isEmpty() ? 1 : wrapChatRows(t, logW, logW).size();
        }
        if (line.text.isEmpty()) return 1;
        String who = chatName(line, names);
        int nameW = UiText.width(font, who + ": ", theme.fontFor(UiTone.BODY),
            MatchmakingManager.nameColor(line.senderFpHex, line.self));
        return wrapChatRows(line.text, Math.max(1, logW - nameW), logW).size();
    }

    private static int sum(int[] a) { int s = 0; for (int v : a) s += v; return s; }

    private void copyText(String text) {
        copyToClipboard(text);
        AutismNotifications.copied("Copied to clipboard.");
    }

    private String chatName(MmChatLine line, Map<String, String> names) {
        if (line.self) return names.getOrDefault(mm.selfFpHex(), mm.selfDisplayName()) + " (you)";
        return names.getOrDefault(line.senderFpHex, mm.displayNameFor(line.senderFpHex));
    }

    private void renderCard(GuiGraphicsExtractor g, MmChatLine line, Map<String, String> names, int x, int y, int w, int mx, int my) {
        boolean tpa = isTpaInvite(line);
        int accent = cardAccent(line);

        autismclient.gui.vanillaui.components.Card.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, 28));
        ItemStack icon = line.kind == MmChatLine.Kind.BLOB_CARD && "item".equals(line.blob.kind) ? itemIcon(line.blob) : ItemStack.EMPTY;
        if (icon != null && !icon.isEmpty()) {
            g.item(icon, x + 5, y + 6);

            if (mx >= x + 5 && mx < x + 21 && my >= y + 6 && my < y + 22 && my >= chatLogTop && my < chatLogBottom) {
                chatTooltipItem = icon; chatTooltipMx = mx; chatTooltipMy = my;
            }
        } else drawText(g, cardTag(line, tpa), x + 6, y + 4, accent, false, 52);
        if (line.kind == MmChatLine.Kind.BLOB_CARD && "server".equals(line.blob.kind)) {
            drawText(g, MmBlobs.displayAddr(MmBlobs.serverIp(line.blob)), x + 54, y + 4, TEXT, false, w - 150);
            drawText(g, serverCardOnline(line.blob), x + 54, y + 15, MUTED, false, w - 150);
        } else {
            drawText(g, cardHead(line, tpa), x + 54, y + 4, TEXT, false, w - 64);
            drawText(g, (line.self ? "you" : "from " + names.getOrDefault(line.senderFpHex, mm.displayNameFor(line.senderFpHex))),
                x + 54, y + 15, MUTED, false, w - 160);
        }

        int rightX = x + w - 6;
        rightX = cardBtn(g, rightX, y, "Copy", BORDER, mx, my, () -> copyCard(line));
        switch (line.kind) {
            case MACRO_CARD -> cardBtn(g, rightX, y, "Inspect", INFO, mx, my,
                () -> openMacroEditor(AutismClipboardHelper.deserializeMacroFromBase64(line.macro.hash)));
            case COMMAND_CARD -> {
                if (tpa) {
                    cardBtn(g, rightX, y, "Accept", ACCENT, mx, my, () -> mm.runCommandOffer(line.command));
                } else {
                    rightX = cardBtn(g, rightX, y, "Fill", BORDER, mx, my, () -> fillCommand(line.command));
                    cardBtn(g, rightX, y, "Execute", ACCENT, mx, my, () -> mm.runCommandOffer(line.command));
                }
            }
            case PACKET_CARD -> {
                rightX = cardBtn(g, rightX, y, "Inspect", INFO, mx, my, () -> openInspector(MmShare.inspectableEntry(line.packet)));
                cardBtn(g, rightX, y, "Add to queue", ACCENT, mx, my, () -> addToQueue(line.packet));
            }
            case BLOB_CARD -> renderBlobButtons(g, line.blob, rightX, y, mx, my);
            default -> {}
        }
    }

    private boolean isTpaInvite(MmChatLine line) {
        return line.kind == MmChatLine.Kind.COMMAND_CARD && line.command != null
            && line.command.kind == MmMessages.CommandOffer.KIND_VANILLA
            && line.command.body != null && line.command.body.toLowerCase(Locale.ROOT).startsWith("tpa ");
    }

    private int cardAccent(MmChatLine line) {
        if (line.kind == MmChatLine.Kind.BLOB_CARD) {
            return switch (line.blob.kind) { case "item", "gui", "steps" -> ACCENT; case "filter" -> WARN; default -> INFO; };
        }
        return switch (line.kind) { case MACRO_CARD -> ACCENT; case COMMAND_CARD -> WARN; default -> INFO; };
    }

    private String cardTag(MmChatLine line, boolean tpa) {
        if (line.kind == MmChatLine.Kind.BLOB_CARD) {
            return switch (line.blob.kind) {
                case "gui" -> "GUI"; case "item" -> "ITEM"; case "filter" -> "FILTER";
                case "position" -> "POS"; case "server" -> "SERVER";
                case "steps" -> "STEPS"; case "module" -> "MODULE"; default -> "SHARE";
            };
        }
        return switch (line.kind) {
            case MACRO_CARD -> "MACRO"; case PACKET_CARD -> "QUEUE";
            case COMMAND_CARD -> tpa ? "TPA" : "CMD"; default -> "SHARE";
        };
    }

    private String cardHead(MmChatLine line, boolean tpa) {
        if (line.kind == MmChatLine.Kind.COMMAND_CARD) {
            String rendered = MatchmakingManager.renderCommandOffer(line.command);
            return tpa ? "TPA invite → " + rendered : rendered;
        }
        if (line.kind == MmChatLine.Kind.BLOB_CARD) {
            return switch (line.blob.kind) {
                case "gui" -> line.blob.friendlyName + "  (" + line.blob.count + " slots)";
                case "filter" -> "Packet Filter  (" + line.blob.count + " types)";
                case "module" -> line.blob.friendlyName + "  (" + line.blob.count + " settings)";
                default -> line.blob.friendlyName;
            };
        }
        return line.cardHeadline();
    }

    private String serverCardOnline(MmMessages.BlobOffer b) {
        int players = MmBlobs.serverPlayers(b);
        int max = MmBlobs.serverPlayersMax(b);
        int ping = MmBlobs.serverPing(b);
        StringBuilder sb = new StringBuilder();
        sb.append(players);
        if (max > 0) sb.append("/").append(max);
        sb.append(" online");
        if (ping >= 0) sb.append("  ·  ").append(ping).append("ms");
        return sb.toString();
    }

    private void renderBlobButtons(GuiGraphicsExtractor g, MmMessages.BlobOffer b, int rightX, int y, int mx, int my) {
        switch (b.kind) {
            case "gui" -> cardBtn(g, rightX, y, "View", ACCENT, mx, my, () -> openGuiView(b));
            case "item" -> cardBtn(g, rightX, y, "Inspect", ACCENT, mx, my, () -> openItemInspect(MmBlobs.decodeItem(b)));
            case "filter" -> {
                rightX = cardBtn(g, rightX, y, "Inspect", INFO, mx, my, () -> openFilterView(b));
                cardBtn(g, rightX, y, "Import", ACCENT, mx, my, () -> {
                    int n = MmBlobs.importFilter(b);
                    AutismNotifications.show("Imported " + n + " filtered packet(s).", ACCENT);
                });
            }
            case "server" -> cardBtn(g, rightX, y, "Join", ACCENT, mx, my, () -> joinSharedServer(b));
            case "steps" -> {
                rightX = cardBtn(g, rightX, y, "To editor", INFO, mx, my,
                    () -> openMacroEditor(AutismClipboardHelper.deserializeMacroFromBase64(b.data)));
                cardBtn(g, rightX, y, "Import", ACCENT, mx, my, () -> importSteps(b));
            }
            case "module" -> {
                rightX = cardBtn(g, rightX, y, "Preview", INFO, mx, my, () -> openModuleView(b));
                cardBtn(g, rightX, y, "Apply", ACCENT, mx, my, () -> applyModuleBlob(b));
            }
            default -> {}
        }
    }

    private int cardBtn(GuiGraphicsExtractor g, int rightX, int rowY, String label, int border, int mx, int my, Runnable action) {
        int bw = Math.max(34, UiText.width(font, label, theme.fontFor(UiTone.BODY), TEXT) + 10);
        int bx = rightX - bw;
        button(g, bx, rowY + 11, bw, 14, label, border, TEXT, mx, my, action);
        return bx - 4;
    }

    private void doSend() {
        if (!mm.inLobby()) { AutismNotifications.show("Join a lobby first.", WARN); return; }
        String text = chatInput.text().trim();
        if (text.isEmpty()) return;

        String type = text.length() >= 24 ? AutismClipboardHelper.detectShareType(text) : null;
        if ("autism_macro_steps".equals(type)) {

            MmMessages.BlobOffer steps = buildStepsOffer(text);
            if (steps != null) { mm.offerBlob(steps); chatInput.setText(""); return; }
        } else if (type != null && type.startsWith("autism_macro")) {
            AutismMacro macro = AutismClipboardHelper.deserializeMacroFromBase64(text);
            MmMessages.MacroOffer offer = MmShare.buildMacroOffer(macro);
            if (offer != null) { mm.offerMacro(offer); chatInput.setText(""); return; }
        } else if ("packets".equals(type)) {

            List<AutismSharedState.QueuedPacket> q = AutismClipboardHelper.deserializeQueueFromBase64(text);
            int count = q == null ? 0 : q.size();
            MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
            offer.friendlyName = count <= 0 ? "shared packets" : count + (count == 1 ? " packet" : " packets");
            offer.data = text;
            mm.offerPacket(offer); chatInput.setText(""); return;
        }

        MmMessages.BlobOffer blob = text.length() >= 24 ? MmBlobs.decodeOffer(text) : null;
        if (blob != null) { mm.offerBlob(blob); chatInput.setText(""); return; }

        if (text.startsWith("/") || AutismCommands.isAutismCommandMessage(text)) {
            mm.offerCommand(text); chatInput.setText(""); return;
        }
        mm.sendChat(text);
        chatInput.setText("");
    }

    private void shareQueue() {
        List<AutismSharedState.QueuedPacket> q = AutismSharedState.get().getDelayedPackets();
        MmMessages.PacketOffer offer = MmShare.buildPacketOffer(q, q.size() + (q.size() == 1 ? " packet" : " packets"));
        if (offer == null) { AutismNotifications.show("Your packet queue is empty.", WARN); return; }
        mm.offerPacket(offer);
    }

    private void shareMacro(AutismMacro macro) {
        MmMessages.MacroOffer offer = MmShare.buildMacroOffer(macro);
        if (offer != null) mm.offerMacro(offer);
    }

    private MmMessages.BlobOffer buildStepsOffer(String stepsHash) {
        AutismMacro macro = AutismClipboardHelper.deserializeMacroFromBase64(stepsHash);
        if (macro == null || macro.actions == null || macro.actions.isEmpty()) return null;
        int count = macro.actions.size();
        String label = count == 1
            ? (macro.actions.get(0) == null ? "1 step" : macro.actions.get(0).getDisplayName())
            : count + " macro steps";
        return new MmMessages.BlobOffer("steps", label, count, stepsHash);
    }

    private void shareModule(Module m) {
        MmMessages.BlobOffer b = MmBlobs.captureModule(m);
        if (b == null) { AutismNotifications.show("Could not capture module settings.", ERROR); return; }
        mm.offerBlob(b);
    }

    private void importSteps(MmMessages.BlobOffer b) {
        MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
        offer.hash = b.data;
        offer.macroName = b.friendlyName;
        MmShare.importMacro(offer);
    }

    private void openModuleView(MmMessages.BlobOffer b) {
        if (moduleViewOverlay == null) moduleViewOverlay = new AutismModuleViewOverlay(font);
        if (moduleViewOverlay.open(b)) presentOverlay(moduleViewOverlay, true);
    }

    private void applyModuleBlob(MmMessages.BlobOffer b) {
        int n = MmBlobs.applyModule(b);
        if (n < 0) { AutismNotifications.show("Can't apply. Bad data.", ERROR); return; }
        AutismNotifications.show("Applied " + n + " setting(s) to " + MmBlobs.moduleName(b) + ".", ACCENT);
    }

    private void addToQueue(MmMessages.PacketOffer offer) {
        int n = MmShare.addToQueue(offer);
        if (n < 0) { AutismNotifications.show("Could not read shared queue.", ERROR); return; }
        AutismNotifications.show("Added " + n + " packet(s) to your queue.", ACCENT);
    }

    private void copyCard(MmChatLine line) {
        copyToClipboard(MmCardActions.clipboardTextFor(line));
        AutismNotifications.copied("Copied to clipboard.");
    }

    private void fillCommand(MmMessages.CommandOffer c) {
        String text = MatchmakingManager.renderCommandOffer(c);
        Minecraft.getInstance().gui.setScreen(new ChatScreen(text, false));
    }

    private void joinSharedServer(MmMessages.BlobOffer b) {
        MmCardActions.confirmJoinServer(MmBlobs.serverIp(b), MmBlobs.serverName(b), host.returnScreen());
    }

    private void renderMembers(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {

        UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
        hitGateTop = top; hitGateBottom = bottom;
        int y;
        try {
            y = renderMembersList(g, x, top, w, bottom, mx, my);
        } finally {
            hitGateTop = Integer.MIN_VALUE; hitGateBottom = Integer.MAX_VALUE;
            UiScissorStack.global().pop(g);
        }
        setContentBottom(Tab.MEMBERS, y);
    }

    private int renderMembersList(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {
        int y = top - scrollOf(Tab.MEMBERS);

        String code = mm.currentShareCode();
        if (!code.isBlank()) {
            Lobby clb = mm.currentLobby();
            boolean pub = clb != null && clb.isPublic;

            autismclient.gui.vanillaui.components.Card.render(
                autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, 20));
            drawText(g, pub ? "Room Code:" : "Room Key:", x + 8, y + 6, MUTED, false, 70);
            drawText(g, code, x + 78, y + 6, ACCENT, false, w - 78 - 60);
            button(g, x + w - 54, y + 2, 48, 16, "Copy", ACCENT, TEXT, mx, my,
                () -> { copyToClipboard(code); AutismNotifications.copied(pub ? "Room code copied." : "Room key copied."); });
            y += 24;
        }

        drawText(g, "Members", x, y, TEXT, false, w);
        y += 16;

        List<MmPeer> peers = mm.members();
        Map<String, String> names = disambiguate(peers);
        Lobby lb = mm.currentLobby();
        long now = System.currentTimeMillis();

        String selfLabel = names.getOrDefault(mm.selfFpHex(), mm.selfDisplayName());
        boolean selfHost = mm.isHost();

        String selfServerLine = selfServerLine();
        String selfWorldLine = selfWorldLine();
        String selfPosLine = selfPosLine();
        int selfExtra = (selfServerLine.isEmpty() ? 0 : 1) + (selfWorldLine.isEmpty() ? 0 : 1) + (selfPosLine.isEmpty() ? 0 : 1);
        int selfH = 22 + selfExtra * 10;

        autismclient.gui.vanillaui.components.Card.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, selfH));
        String selfRole = lb != null && lb.announcement
            ? (AutismDiscordLogin.isAdmin() ? "  [Admin]" : mm.canSend() ? "  [Speaker]" : "") : "";
        int selfEndX = drawRoleName(g, selfLabel, x + 8, y + 3, mm.selfFpHex(), true);
        drawText(g, "  (you)" + (selfHost ? "  (host)" : "") + selfRole,
            selfEndX, y + 3, MUTED, false, Math.max(1, x + w - 8 - selfEndX));
        int sly = y + 13;
        if (!selfServerLine.isEmpty()) { drawText(g, selfServerLine, x + 8, sly, INFO, false, w - 16); sly += 10; }
        if (!selfWorldLine.isEmpty()) { drawText(g, selfWorldLine, x + 8, sly, INFO, false, w - 16); sly += 10; }
        if (!selfPosLine.isEmpty()) drawText(g, selfPosLine, x + 8, sly, INFO, false, w - 16);
        y += selfH + 3;

        if (peers.isEmpty()) { drawText(g, "No other members yet.", x + 4, y + 2, MUTED, false, w); y += 14; }
        for (MmPeer p : peers) {

            String serverLine = (p.serverShared && !p.serverIp.isBlank()) ? "Server: " + MmBlobs.displayAddr(p.serverIp) : "";
            String worldLine = p.hasLocation ? "world: " + shortDim(p.dimension) : "";
            String posLine = p.hasLocation ? "position: " + (int) p.x + " " + (int) p.y + " " + (int) p.z : "";
            int extraLines = (serverLine.isEmpty() ? 0 : 1) + (worldLine.isEmpty() ? 0 : 1) + (posLine.isEmpty() ? 0 : 1);
            int rowH = 22 + extraLines * 10;
            boolean visible = y + rowH > top && y < bottom;
            if (visible) {
                boolean isHost = p.fpHex.equals(mm.actingHostFp());

                autismclient.gui.vanillaui.components.Card.render(
                    autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, rowH));
                int dot = p.isOnline(now) ? ACCENT : MUTED;
                UiRenderer.rect(g, UiBounds.of(x + 6, y + 5, 6, 6), dot);
                String label = names.getOrDefault(p.fpHex, p.displayName());
                String suffix = (isHost ? "  (host)" : "")
                    + (lb != null && lb.announcement ? (p.admin ? "  [Admin]" : p.speaker ? "  [Speaker]" : "") : "")
                    + (p.muted ? "  (muted)" : "");
                int endX = drawRoleName(g, label, x + 18, y + 3, p.fpHex, false);
                if (!suffix.isEmpty()) drawText(g, suffix, endX, y + 3, MUTED, false, Math.max(1, x + w - 6 - endX));
                int ly = y + 13;
                if (!serverLine.isEmpty()) { drawText(g, serverLine, x + 18, ly, INFO, false, w - 16); ly += 10; }
                if (!worldLine.isEmpty()) { drawText(g, worldLine, x + 18, ly, INFO, false, w - 16); ly += 10; }
                if (!posLine.isEmpty()) drawText(g, posLine, x + 18, ly, INFO, false, w - 16);

                final String fpHex = p.fpHex;
                memberRows.add(new MemberRow(x, y, w, rowH, fpHex, hitGateTop, hitGateBottom));
                final int kx = x + w - 16;
                final int menuY = y + 17;
                boolean kHover = mx >= kx - 2 && mx < x + w && my >= y && my < y + 18;
                drawText(g, "⋮", kx + 3, y + 3, kHover ? TEXT : MUTED, false);
                addHotspot(kx - 2, y, 18, 18, () -> openContextMenu(fpHex, kx - 2, menuY));
            }
            y += rowH + 3;
        }
        return y;
    }

    private String selfServerLine() {
        if (!mm.effectiveShareServer()) return "";
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        if (sd == null || sd.ip == null || sd.ip.isBlank()) return "";
        return "Server: " + MmBlobs.displayAddr(sd.ip);
    }

    private String selfWorldLine() {
        if (!mm.effectiveShareLocation()) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.level() == null) return "";
        return "world: " + shortDim(mc.player.level().dimension().identifier().toString());
    }

    private String selfPosLine() {
        if (!mm.effectiveShareLocation()) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        return "position: " + (int) mc.player.getX() + " " + (int) mc.player.getY() + " " + (int) mc.player.getZ();
    }

    private Map<String, String> disambiguate(List<MmPeer> peers) {
        String self = mm.selfDisplayName();
        long rosterVersion = mm.rosterUiVersion();
        if (rosterVersion == cachedNamesRosterVersion && peers.size() == cachedNamesMemberCount
                && self.equals(cachedNamesSelf)) return cachedNames;
        List<String[]> order = new ArrayList<>();
        order.add(new String[]{mm.selfFpHex(), self});
        for (MmPeer p : peers) order.add(new String[]{p.fpHex, p.displayName()});
        Map<String, Integer> seen = new HashMap<>();
        Map<String, String> out = new HashMap<>();
        for (String[] e : order) {
            String key = e[1].toLowerCase(Locale.ROOT);
            int count = seen.merge(key, 1, Integer::sum);
            out.put(e[0], count == 1 ? e[1] : e[1] + " (" + (count - 1) + ")");
        }
        cachedNamesRosterVersion = rosterVersion;
        cachedNamesMemberCount = peers.size();
        cachedNamesSelf = self;
        cachedNames = Map.copyOf(out);
        return cachedNames;
    }

    private void confirmJoinServer(MmPeer peer) {
        String name = peer.serverName == null || peer.serverName.isBlank() ? peer.displayName() : peer.serverName;
        MmCardActions.confirmJoinServer(peer.serverIp, name, host.returnScreen());
    }

    private void payPeer(String name) {
        Minecraft.getInstance().gui.setScreen(new ChatScreen("/pay " + name + " ", false));
    }

    private static String shortDim(String dim) {
        if (dim == null) return "?";
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    private void renderSettings(GuiGraphicsExtractor g, DirectRenderContext ctx, int x, int top, int w, int bottom, int mx, int my) {

        UiScissorStack.global().push(g, UiBounds.of(x, top, w, Math.max(0, bottom - top)));
        hitGateTop = top; hitGateBottom = bottom;
        try {
            int y = top - scrollOf(Tab.SETTINGS);

            y = toggle(g, x, y, w, "Share my server", prefs.shareServer(), mx, my, () -> prefs.setShareServer(!prefs.shareServer()));
            y = toggle(g, x, y, w, "Share my position", prefs.shareLocation(), mx, my, () -> prefs.setShareLocation(!prefs.shareLocation()));
            y = toggle(g, x, y, w, "Kill switch (block sharing)", prefs.killSwitch(), mx, my, this::toggleKillSwitch);
            y = toggle(g, x, y, w, "Debug logging (console)", prefs.debugLog(), mx, my,
                () -> prefs.setDebugLog(!prefs.debugLog()));

            y += 4;
            drawText(g, "Public Settings", x, y, TEXT, false, w);
            y += 14;
            y = toggle(g, x, y, w, "Auto-join public lobby", prefs.autoJoinPublic(), mx, my,
                () -> prefs.setAutoJoinPublic(!prefs.autoJoinPublic()));

            Lobby olb = mm.currentLobbyOfficial() ? mm.currentLobby() : null;
            if (olb != null) {
                final String olId = olb.lobbyId;
                y = toggle(g, x, y, w, "Share my server here", prefs.lobbyShareServer(olId), mx, my,
                    () -> prefs.setLobbyShareServer(olId, !prefs.lobbyShareServer(olId)));
                y = toggle(g, x, y, w, "Share my position here", prefs.lobbyShareLocation(olId), mx, my,
                    () -> prefs.setLobbyShareLocation(olId, !prefs.lobbyShareLocation(olId)));
            }

            autismclient.gui.vanillaui.components.Card.render(
                autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, 18));
            drawText(g, "Signed in: " + mm.selfDisplayName(), x + 8, y + 5, MUTED, false, w - 72);
            button(g, x + w - 62, y + 1, 56, 16, "Sign out", ERROR, TEXT, mx, my, this::signOutDiscord);
            y += 22;
            y = renderModerationList(g, x, y, w, mx, my, "Blocked", prefs.blockedDiscordSnapshot(), true);
            y = renderModerationList(g, x, y, w, mx, my, "Banned (your lobbies)", prefs.bannedDiscordSnapshot(), false);
            setContentBottom(Tab.SETTINGS, y);
        } finally {
            hitGateTop = Integer.MIN_VALUE; hitGateBottom = Integer.MAX_VALUE;
            UiScissorStack.global().pop(g);
        }
    }

    private int renderModerationList(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, String title,
                                     java.util.Map<String, String> entries, boolean block) {
        y += 4;
        drawText(g, title, x, y, TEXT, false, w);
        y += 14;
        if (entries.isEmpty()) { drawText(g, "None.", x + 6, y + 2, MUTED, false, w); return y + 14; }
        for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
            String id = e.getKey();
            String label = e.getValue() == null || e.getValue().isBlank() ? "Discord member" : e.getValue();

            autismclient.gui.vanillaui.components.Card.render(
                autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my), UiBounds.of(x, y, w, 18));
            drawText(g, label, x + 8, y + 5, TEXT, false, w - 76);
            button(g, x + w - 70, y + 1, 64, 16, block ? "Unblock" : "Unban", ACCENT, TEXT, mx, my,
                () -> { if (block) mm.unblockDiscord(id); else mm.unbanDiscord(id); });
            y += 21;
        }
        return y;
    }

    private void openInspector(AutismPacketLoggerOverlay.LogEntry entry) {
        if (entry == null) { AutismNotifications.show("Could not rebuild packet to inspect.", ERROR); return; }
        if (inspectOverlay == null) inspectOverlay = new AutismPacketInspectOverlay(font);
        inspectOverlay.open(entry, 60, 60);
        presentOverlay(inspectOverlay, true);
    }

    private void openItemInspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { AutismNotifications.show("Could not read shared item.", ERROR); return; }
        AutismItemNbtInspectOverlay ov = AutismItemNbtInspectOverlay.getSharedOverlay(font);
        if (ov == null) return;
        ov.open(stack, 60, 60);
        presentOverlay(ov, true);
    }

    private void openGuiView(MmMessages.BlobOffer blob) {
        if (guiViewOverlay == null) guiViewOverlay = new AutismGuiViewOverlay(font);
        if (guiViewOverlay.open(blob)) presentOverlay(guiViewOverlay, true);
    }

    private void openFilterView(MmMessages.BlobOffer blob) {
        if (filterViewOverlay == null) filterViewOverlay = new AutismFilterViewOverlay(font);
        if (filterViewOverlay.open(blob)) presentOverlay(filterViewOverlay, true);
    }

    private void openMacroEditor(AutismMacro macro) {
        if (macro == null) { AutismNotifications.show("Could not read shared macro.", ERROR); return; }
        AutismMacroEditorOverlay ed = AutismMacroEditorOverlay.getSharedOverlay();
        if (ed == null) return;
        ed.openForImport(macro);
        presentOverlay(ed, false);
    }

    private void presentOverlay(IAutismOverlay ov, boolean background) {
        if (ov == null) return;
        if (background) AutismOverlayManager.get().register(ov, IAutismOverlay.OverlayScope.BACKGROUND_STATUS);
        else AutismOverlayManager.get().register(ov);
        AutismOverlayManager.get().bringToFront(ov);
    }

    private void copyToClipboard(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s == null ? "" : s);
    }

    public boolean mouseClicked(int mx, int my, int button) {
        this.lastMx = mx; this.lastMy = my;

        if (ctxOpen && button == 0) {
            for (CtxItem it : ctxItems) {
                if (it.hit(mx, my)) {
                    try { it.action.run(); } catch (Throwable t) { AutismNotifications.show("Action failed: " + t.getClass().getSimpleName(), ERROR); }
                    if (it.closes) closeContextMenu();
                    return true;
                }
            }
            closeContextMenu();
            return true;
        }

        if (button == 1) {
            for (MemberRow r : memberRows) if (r.hit(mx, my)) { openContextMenu(r.fp(), mx, my); return true; }
            if (ctxOpen) { closeContextMenu(); return true; }
            return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        }
        if (button == 0 && activeTab == Tab.CHAT && chatScrollbar != null && chatScrollbar.contains(mx, my)) {
            chatScrollbarDragging = true;
            chatScrollGrab = my - chatScrollbar.thumbY();
            clearFocus();
            return true;
        }

        DirectRenderContext ctx = renderCtx(null, mx, my);
        for (CompactTextInput in : activeInputs) {
            if (in.mouseClicked(ctx, mx, my, button)) {
                for (CompactTextInput other : activeInputs) if (other != in) other.setFocused(false);
                return true;
            }
        }
        clearFocus();
        if (button == 0) {
            for (Hotspot h : hotspots) {
                if (h.hit(mx, my)) {

                    try { h.action.run(); }
                    catch (Throwable t) { AutismNotifications.show("Action failed: " + t.getClass().getSimpleName(), ERROR); }
                    return true;
                }
            }
        }

        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    public boolean mouseReleased(int mx, int my, int button) {
        if (chatScrollbarDragging) { chatScrollbarDragging = false; return true; }
        DirectRenderContext ctx = renderCtx(null, mx, my);
        boolean any = false;
        for (CompactTextInput in : activeInputs) any |= in.mouseReleased(ctx, mx, my, button);
        return any;
    }

    public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
        if (chatScrollbarDragging && chatScrollbar != null) {

            int topVal = CompactScrollbar.scrollFromThumb(chatScrollbar, my, chatScrollGrab);
            scroll[Tab.CHAT.ordinal()] = Math.max(0, Math.min(chatMaxScrollPx, chatMaxScrollPx - topVal));
            return true;
        }
        DirectRenderContext ctx = renderCtx(null, mx, my);
        boolean any = false;
        for (CompactTextInput in : activeInputs) any |= in.mouseDragged(ctx, mx, my, button, (float) dx, (float) dy);
        return any;
    }

    public boolean mouseScrolled(int mx, int my, double amount) {
        if (ctxOpen) closeContextMenu();
        if (activeTab == Tab.CHAT) {

            int max = chatMaxScrollPx;
            scroll[Tab.CHAT.ordinal()] = Math.max(0, Math.min(max, scroll[Tab.CHAT.ordinal()] + (int) Math.signum(amount) * 18));
            return true;
        }
        int max = maxScrollFor(activeTab);
        if (max <= 0) return false;
        scroll[activeTab.ordinal()] = Math.max(0, Math.min(max, scroll[activeTab.ordinal()] - (int) Math.signum(amount) * 16));
        return true;
    }

    private int scrollOf(Tab tab) { return tab == Tab.CHAT ? 0 : scroll[tab.ordinal()]; }

    private void setContentBottom(Tab tab, int renderedBottomY) {
        if (tab == activeTab) contentHeight[tab.ordinal()] = Math.max(0, (renderedBottomY + scrollOf(tab)) - lastContentTop);
    }

    private int maxScrollFor(Tab tab) {
        return tab == Tab.CHAT ? 0 : Math.max(0, contentHeight[tab.ordinal()] - availableContentH);
    }

    public int desiredHeight() {

        if (!autismclient.util.AutismDiscordLogin.hasSession()) return 175;
        if (activeTab == Tab.CHAT) {

            return chromeHeight + CHAT_CHROME + Math.max(MIN_CHAT_LOG, chatContentPx(lastChatLogFullW, disambiguate(mm.members()))) + 8;
        }
        return chromeHeight + Math.max(24, contentHeight[activeTab.ordinal()]) + 8;
    }

    private int chatContentPx(int logW, Map<String, String> names) {
        int v = mm.chatVersion();
        if (v != cachedChatPxVersion || logW != cachedChatPxWidth) {
            int px = 0;
            for (MmChatLine line : mm.chatSnapshot()) px += chatLineHeight(line, logW, names);
            cachedChatPx = px;
            cachedChatPxVersion = v;
            cachedChatPxWidth = logW;
        }
        return cachedChatPx;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DirectRenderContext ctx = renderCtx(null, lastMx, lastMy);
        for (CompactTextInput in : activeInputs) {
            if (in.isFocused() && in.keyPressed(ctx, keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        DirectRenderContext ctx = renderCtx(null, lastMx, lastMy);
        for (CompactTextInput in : activeInputs) {
            if (in.isFocused() && in.charTyped(ctx, chr, modifiers)) return true;
        }
        return false;
    }

    public boolean hasFocusedTextInput() {
        for (CompactTextInput in : activeInputs) if (in.isFocused()) return true;
        return false;
    }

    public void clearFocus() {
        chatInput.setFocused(false); lobbyName.setFocused(false); server.setFocused(false);
        maxPlayers.setFocused(false); passphrase.setFocused(false);
        joinCode.setFocused(false);
        closeContextMenu();
    }

    private DirectRenderContext renderCtx(GuiGraphicsExtractor g, int mx, int my) {
        return new DirectRenderContext(g, font, DirectViewport.current(1.0f), theme, mx, my, delta);
    }

    private void placeField(GuiGraphicsExtractor g, DirectRenderContext ctx, CompactTextInput f, int x, int y, int w) {
        placeField(g, ctx, f, x, y, w, 16);
    }

    private void placeField(GuiGraphicsExtractor g, DirectRenderContext ctx, CompactTextInput f, int x, int y, int w, int h) {
        f.setBounds(x, y, w, h);
        f.render(ctx);

        if (y + h > hitGateTop && y < hitGateBottom) activeInputs.add(f);
    }

    private void openContextMenu(String fp, int atX, int atY) {
        ctxOpen = true; ctxFp = fp; ctxX = atX; ctxY = atY; ctxBanConfirm = false;
    }

    private void closeContextMenu() { ctxOpen = false; ctxBanConfirm = false; ctxItems.clear(); }

    private void renderContextMenu(GuiGraphicsExtractor g, int mx, int my) {
        ctxItems.clear();
        if (!ctxOpen || activeTab != Tab.MEMBERS) return;
        MmPeer p = mm.peer(ctxFp);
        if (p == null) { ctxOpen = false; return; }

        record Act(String label, int color, Runnable run, boolean closes) {}
        List<Act> acts = new ArrayList<>();
        String tpaName = p.commandName();

        boolean coLocated = tpaName.matches("[A-Za-z0-9_]{1,16}") && MatchmakingManager.sameServerAs(p);
        if (coLocated) {
            acts.add(new Act("TPA", INFO, () -> mm.tpaPeer(tpaName), true));
            acts.add(new Act("Trade", INFO, () -> mm.tradePeer(tpaName), true));
            acts.add(new Act("Pay", INFO, () -> payPeer(tpaName), true));
        }
        if (p.serverShared && !p.serverIp.isBlank() && !MatchmakingManager.alreadyOn(p.serverIp))
            acts.add(new Act("Join server", INFO, () -> confirmJoinServer(p), true));
        if (p.hasLocation) acts.add(new Act("Copy coords", TEXT,
            () -> { copyToClipboard((int) p.x + " " + (int) p.y + " " + (int) p.z); AutismNotifications.copied("Coords copied."); }, true));
        if (mm.canManageSpeakers() && !p.admin) {
            if (p.speaker) acts.add(new Act("Revoke sending", WARN, () -> mm.setSpeaker(p.fpHex, false), true));
            else acts.add(new Act("Allow sending", ACCENT, () -> mm.setSpeaker(p.fpHex, true), true));
        }
        if (mm.isHost() && !p.fpHex.equals(mm.selfFpHex()) && !p.fpHex.equals(mm.actingHostFp()))
            acts.add(new Act("Make host", ACCENT, () -> mm.makeHost(p.fpHex), true));
        if (p.muted) acts.add(new Act("Unblock", ACCENT, () -> mm.unblockPeer(p.fpHex), true));
        else acts.add(new Act("Block", ERROR, () -> mm.blockPeer(p.fpHex), true));
        if (mm.isHost() && !p.fpHex.equals(mm.selfFpHex())) {
            if (ctxBanConfirm) acts.add(new Act("Confirm ban", WARN, () -> mm.banPeer(p.fpHex), true));
            else acts.add(new Act("Ban", ERROR, () -> ctxBanConfirm = true, false));
        }

        int itemH = 15, pad = 8, wMenu = 96;
        for (Act a : acts) wMenu = Math.max(wMenu, font.width(a.label()) + pad * 2);
        int hMenu = acts.size() * itemH + 4;
        int mxv = Math.max(bx + 2, Math.min(ctxX, bx + bw - wMenu - 2));
        int myv = Math.max(by + 2, Math.min(ctxY, by + bh - hMenu - 2));

        UiRenderer.frame(g, UiBounds.of(mxv, myv, wMenu, hMenu), 0xF21A1A1E, BORDER_BRIGHT);
        int iy = myv + 2;
        for (Act a : acts) {
            boolean hover = mx >= mxv && mx < mxv + wMenu && my >= iy && my < iy + itemH;
            if (hover) UiRenderer.rect(g, UiBounds.of(mxv + 1, iy, wMenu - 2, itemH), tint(a.color(), 0x33));
            drawText(g, a.label(), mxv + pad, iy + (itemH - 8) / 2, a.color(), false, wMenu - pad);
            ctxItems.add(new CtxItem(mxv, iy, wMenu, itemH, a.label(), a.color(), a.run(), a.closes()));
            iy += itemH;
        }
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int border, int textColor,
                        int mx, int my, Runnable action) {
        boolean enabled = action != null;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;

        autismclient.gui.vanillaui.components.Button.Tone tone = border == ACCENT || textColor == ACCENT
            ? autismclient.gui.vanillaui.components.Button.Tone.PRIMARY
            : autismclient.gui.vanillaui.components.Button.Tone.NORMAL;
        autismclient.gui.vanillaui.components.Button.render(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            UiBounds.of(x, y, w, h), label, tone, hover, false);
        if (!enabled) UiRenderer.rect(g, UiBounds.of(x, y, w, h), 0x66000000);
        if (enabled) addHotspot(x, y, w, h, action);
    }

    private void chipColored(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int accentColor,
                             boolean selected, boolean enabled, int mx, int my, Runnable action) {
        boolean clickable = enabled && action != null;
        boolean hover = clickable && !selected && mx >= x && mx < x + w && my >= y && my < y + h;

        if (clickable || selected) {
            autismclient.gui.vanillaui.components.Chip.render(
                autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
                UiBounds.of(x, y, w, h), label, accentColor, selected, hover);
        } else {
            autismclient.gui.vanillaui.components.Chip.renderDisabled(
                autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
                UiBounds.of(x, y, w, h), label);
        }
        if (clickable) addHotspot(x, y, w, h, action);
    }

    private int toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean value, int mx, int my, Runnable onToggle) {
        int h = 18;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        UiRenderer.frame(g, UiBounds.of(x, y, w, h), hover ? tint(BORDER, 0x33) : CARD_BG, BORDER);

        autismclient.gui.vanillaui.components.Toggle.renderLabeled(
            autismclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            UiBounds.of(x, y, w, h), label, value, hover, "mm:" + label);
        addHotspot(x, y, w, h, onToggle);
        return y + h + 3;
    }

    private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center) {
        drawText(g, text, x, y, color, center, Integer.MAX_VALUE);
    }

    private int drawRoleName(GuiGraphicsExtractor g, String name, int x, int y, String fpHex, boolean self) {
        Identifier fontId = theme.fontFor(UiTone.BODY);
        String value = name == null ? "" : name;
        if (!MatchmakingManager.isGradientName(fpHex, self)) {
            int color = MatchmakingManager.nameColor(fpHex, self);
            UiText.draw(g, font, value, fontId, color, x, y, false);
            return x + UiText.width(font, value, fontId, color);
        }
        int cx = x;
        int len = value.length();
        for (int i = 0; i < len; i++) {
            String ch = String.valueOf(value.charAt(i));
            int color = MatchmakingManager.gradientNameColor(fpHex, self, i, len);
            UiText.draw(g, font, ch, fontId, color, cx, y, false);
            cx += UiText.width(font, ch, fontId, color);
        }
        return cx;
    }

    private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center, int maxWidth) {
        Identifier fontId = theme.fontFor(UiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE && !center) {
            UiText.drawEllipsized(g, font, value, fontId, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        if (maxWidth != Integer.MAX_VALUE) value = UiText.trimToWidthEllipsis(font, value, maxWidth, fontId, color);
        int wpx = UiText.width(font, value, fontId, color);
        UiText.draw(g, font, value, fontId, color, center ? x - wpx / 2 : x, y, false);
    }

    private ItemStack itemIcon(MmMessages.BlobOffer b) {
        ItemStack cached = itemIconCache.get(b.data);
        if (cached != null) return cached;
        ItemStack st = MmBlobs.decodeItem(b);
        if (st == null) st = ItemStack.EMPTY;
        while (itemIconCache.size() >= ICON_CACHE_MAX) {
            Iterator<String> it = itemIconCache.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
        itemIconCache.put(b.data, st);
        return st;
    }

    private static int tint(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }
    private static String maxLabel(int max) { return max <= 0 ? "∞" : Integer.toString(max); }

    private void addHotspot(int x, int y, int w, int h, Runnable action) {
        hotspots.add(new Hotspot(x, y, w, h, hitGateTop, hitGateBottom, action));
    }

    private static final class Hotspot {
        final int x, y, w, h; final int gateTop, gateBottom; final Runnable action;
        Hotspot(int x, int y, int w, int h, int gateTop, int gateBottom, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.gateTop = gateTop; this.gateBottom = gateBottom;
            this.action = action;
        }
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= gateTop && my < gateBottom;
        }
    }
}
