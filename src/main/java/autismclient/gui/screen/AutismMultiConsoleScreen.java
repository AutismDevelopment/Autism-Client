package autismclient.gui.screen;

import autismclient.commands.AutismCommands;
import autismclient.gui.multi.MultiChatPresentation;
import autismclient.gui.multi.MultiMacroPresentation;
import autismclient.gui.multi.MultiMenuInput;
import autismclient.gui.multi.MultiMenuRenderer;
import autismclient.gui.multi.MultiTooltip;
import autismclient.util.AutismItemNbtInspectOverlay;
import autismclient.util.multi.MultiClientCommands;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiQuickAction;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiSession;
import autismclient.util.multi.MultiSharedGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.ERROR;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiConsoleScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 12;
    private static final int MAX_SESSION_WIDTH = 300;
    private static final int ROW_HEIGHT = 25;
    private static final int DETAIL_ROW_H = 72;
    private static final int GUI_SUB_H = 11;
    private static final int MACRO_SUB_H = 11;
    private static final int GUI_W = 40;
    private static final int POV_W = 40;
    private static final int CHAT_MIN_H = 66;
    private static final Identifier HEART_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier FOOD_SPRITE = Identifier.withDefaultNamespace("hud/food_full");

    private static final int STATUS_GREEN = 0xFF57F287;
    private static final int STATUS_YELLOW = 0xFFF2C54B;
    private static final int STATUS_RED = 0xFFFF5A5A;
    private static final int CHAT_LINE_HEIGHT = 11;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Screen parent;
    private final List<SessionRow> sessionRows = new ArrayList<>();
    private final List<int[]> presetRects = new ArrayList<>();

    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private String anchorId;
    private final List<ChatRow> chatRows = new ArrayList<>();
    private List<MultiChatPresentation.VisualRow> cachedVisualRows = List.of();
    private long cachedChatRevision = Long.MIN_VALUE;
    private String cachedChatScope = null;
    private int cachedChatWidth = -1;
    private int cachedChatTotal = -1;
    private int chatScroll;
    private int chatLeft;
    private int chatRight;
    private int chatTop;
    private int chatBottom;
    private int chatAvail;

    private final autismclient.gui.multi.MultiChatSelection chatSel = new autismclient.gui.multi.MultiChatSelection();
    private boolean chatSelectingPress;

    private CompactScrollbar.Metrics chatScrollbar;
    private boolean chatScrollbarDragging;
    private int chatScrollbarGrab;
    private int chatMaxScrollRows;
    private boolean detailsOpen;

    private String viewingId;

    private boolean sharedView;
    private String sharedKey = "";
    private List<MultiSharedGui.Group> sharedGroups = List.of();
    private final List<int[]> sharedTabRects = new ArrayList<>();
    private int hoveredViewHotbar = -1;
    private int[] closeSilentRect;
    private int[] detailsRect;
    private int[] sharedGuiRect;
    private String macroRowTooltip;
    private int viewScroll;
    private MultiSession.MenuView cachedView;
    private String cachedViewId;
    private long cachedViewRev = Long.MIN_VALUE;
    private final List<int[]> viewSlotRects = new ArrayList<>();
    private final List<MultiMenuRenderer.MenuHit> viewWidgetHits = new ArrayList<>();
    private final MultiMenuInput menuInput = new MultiMenuInput();
    private final Map<String, MultiSession.Snapshot> frameSnapshots = new java.util.HashMap<>();
    private ItemStack hoveredViewStack = ItemStack.EMPTY;
    private int hoveredViewHandler = -1;
    private int hoveredViewX;
    private int hoveredViewY;
    private int viewGridX;
    private int viewGridY;
    private int viewGridW;
    private int viewGridH;
    private int infoColumnTop;
    private EditBox chatInput;
    private int sessionScroll;
    private long sessionLayoutSignature = Long.MIN_VALUE;

    private int historyIndex = -1;
    private String historyDraft = "";

    private final List<String> suggestions = new ArrayList<>();
    private int suggestionIndex;
    private int suggestStart;
    private int suggestLength;
    private String suggestForText;
    private String suggestOriginal;
    private boolean appliedOnce;
    private boolean frozen;
    private String expectedValue;
    private String lastRequestedCmd;
    private long lastRequestAt;
    private final List<int[]> suggestRects = new ArrayList<>();
    private long lastUiRevision = Long.MIN_VALUE;
    private long lastSessionRevision = Long.MIN_VALUE;
    private String resultText = "";
    private int resultColor = MUTED;
    private String consoleMacro = "";

    public AutismMultiConsoleScreen(Screen parent) {
        super(Component.literal("Multi Console"));
        this.parent = parent;

        this.consoleMacro = MultiManager.get().allMacroName();
    }

    @Override
    protected void init() {
        rebuildControls();
    }

    private void rebuildControls() {
        String chatValue = chatInput == null ? "" : chatInput.getValue();
        boolean restoreChatFocus = chatInput != null && chatInput.isFocused();
        int chatCursor = chatInput == null ? chatValue.length() : chatInput.getCursorPosition();
        clearWidgets();
        int sessionW = sessionWidth();
        int consoleX = consoleX();
        int consoleW = consoleWidth();
        int sendW = Math.min(68, Math.max(36, consoleW / 3));
        chatInput = new EditBox(font, consoleX + 10, screenHeight() - 36, Math.max(10, consoleW - sendW - 24), 18, Component.literal("Chat or command"));
        chatInput.setMaxLength(256);
        chatInput.setHint(Component.literal("Chat or /command"));
        chatInput.setResponder(value -> updateGhostSuggestion());
        chatInput.setValue(chatValue);
        chatInput.setCursorPosition(Math.max(0, Math.min(chatCursor, chatValue.length())));
        addRenderableWidget(chatInput);
        if (restoreChatFocus) {
            chatInput.setFocused(true);
            setFocused(chatInput);
        }
        addStyled(consoleX + consoleW - sendW - 10, screenHeight() - 36, sendW, 18, "Send", Button.Tone.SUCCESS, button -> sendChat());

        addQuickActionButtons(consoleX + 10, 46, consoleW - 20);
        addQuickManagementButtons(consoleX + 10, 72, consoleW - 20);
        addActionButtons(consoleX + 10, 94, consoleW - 20);
        if (!isViewing()) addMacroButtons(consoleX + 10, 116, consoleW - 20);
        if (isViewing()) {
            addStyled(consoleX + consoleW - 68, 22, 60, 14, "Close GUI", Button.Tone.NORMAL, button -> exitView());
        }

        int detailsW = Math.min(62, Math.max(34, sessionW - 8));
        detailsRect = new int[]{MARGIN + sessionW - detailsW - 4, 22, detailsW, 14};
        addStyled(MARGIN + sessionW - detailsW - 4, 22, detailsW, 14, detailsOpen ? "Details On" : "Details",
            detailsOpen ? Button.Tone.SUCCESS : Button.Tone.NORMAL, button -> toggleDetails());

        sharedGuiRect = new int[]{MARGIN + 4, 44, sessionW - 8, 16};
        addStyled(MARGIN + 4, 44, sessionW - 8, 16, sharedView ? "Shared GUI: On" : "Shared GUI",
            sharedView ? Button.Tone.SUCCESS : Button.Tone.PRIMARY, button -> toggleSharedView());
        int footerGap = 3;
        int footerEach = Math.max(1, (sessionW - footerGap * 2) / 3);
        addStyled(MARGIN, screenHeight() - 34, footerEach, 18, "Disconnect", Button.Tone.DANGER,
            button -> {
                MultiManager.get().disconnectAll("Disconnected by user");
                openProfiles();
            });
        addStyled(MARGIN + footerEach + footerGap, screenHeight() - 34, footerEach, 18, "Retry All", Button.Tone.PRIMARY, button -> retryAll());
        addStyled(MARGIN + (footerEach + footerGap) * 2, screenHeight() - 34,
            sessionW - (footerEach + footerGap) * 2, 18, "Profiles", Button.Tone.NORMAL, button -> openProfiles());
        addSessionButtons();
        lastUiRevision = MultiManager.get().uiRevision();
        lastSessionRevision = MultiManager.get().sessionRevision();
    }

    private void addQuickActionButtons(int x, int y, int width) {
        MultiProfile profile = MultiManager.get().activeProfile();
        if (profile == null) return;
        presetRects.clear();
        int gap = 6;
        int count = MultiProfile.QUICK_ACTIONS + 1;
        String[] labels = new String[count];
        labels[0] = "Move";
        for (int i = 0; i < MultiProfile.QUICK_ACTIONS; i++) {
            MultiQuickAction action = profile.quickAction(i);
            labels[i + 1] = action.empty() ? "Empty" : action.label(i);
        }
        int avail = width - gap * (count - 1);
        int each = Math.max(1, avail / count);
        int cx = x;
        addStyled(cx, y, each, 18, "Move", Button.Tone.SUCCESS, button -> sendMovement());
        cx += each + gap;
        for (int i = 0; i < MultiProfile.QUICK_ACTIONS; i++) {
            final int index = i;
            MultiQuickAction action = profile.quickAction(i);
            Button.Tone tone = action.empty() ? Button.Tone.NORMAL : Button.Tone.PRIMARY;
            int cw = i == MultiProfile.QUICK_ACTIONS - 1 ? x + width - cx : each;
            addStyled(cx, y, cw, 18, labels[i + 1], tone, button -> sendQuickAction(index));
            presetRects.add(new int[]{cx, y, cw, 18, index});
            cx += cw + gap;
        }
    }

    private void addQuickManagementButtons(int x, int y, int width) {
        int gap = 6;
        int half = Math.max(1, (width - gap) / 2);
        addStyled(x, y, half, 16, "Reset presets", Button.Tone.NORMAL, button -> resetQuickActions());
        addStyled(x + half + gap, y, width - half - gap, 16, "Advanced", Button.Tone.NORMAL, button -> openPolicy());
    }

    private void addActionButtons(int x, int y, int width) {
        String[] labels = {"Use", "GUI", "Close", "Close W/O Pkt"};
        int gap = 6;
        int count = labels.length;
        int avail = width - gap * (count - 1);
        int each = Math.max(1, avail / count);
        int cx = x;
        Runnable[] actions = {this::doUse, this::doOpenInventory, this::doClose, this::doCloseSilent};
        for (int i = 0; i < count; i++) {
            Runnable action = actions[i];
            int cw = i == count - 1 ? x + width - cx : each;
            addStyled(cx, y, cw, 16, labels[i], Button.Tone.NORMAL, button -> action.run());
            if (i == count - 1) closeSilentRect = new int[]{cx, y, cw, 16};
            cx += cw + gap;
        }
    }

    private void addMacroButtons(int x, int y, int width) {
        int gap = 6;
        int sideW = Math.max(24, Math.min(60, Math.max(1, (width - 3 * gap) / 5)));
        int macroX = x + 3 * (sideW + gap);
        int macroW = Math.max(1, x + width - macroX);
        addStyled(x, y, sideW, 16, "Run", Button.Tone.SUCCESS, button -> runMacroScope());
        addStyled(x + sideW + gap, y, sideW, 16, "Stop", Button.Tone.DANGER, button -> stopMacroScope());
        addStyled(x + 2 * (sideW + gap), y, sideW, 16, "Assign", Button.Tone.PRIMARY, button -> openAssign());
        AutismStyledButton picker = new AutismStyledButton(macroX, y, macroW, 16,
            Component.literal("Macro (" + currentMacroLabel() + ")"), Button.Tone.NORMAL,
            () -> fitLabel("Macro (" + currentMacroLabel() + ")", macroW - 8), button -> chooseMacro());
        addRenderableWidget(picker);
    }

    private String currentMacroLabel() {
        return orNone(consoleMacro);
    }

    private static String orNone(String name) {
        return name == null || name.isBlank() ? "none" : name;
    }

    private void chooseMacro() {
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, consoleMacro, name -> {
            consoleMacro = name == null ? "" : name;
            resultText = consoleMacro.isBlank() ? "No macro chosen" : "Chose \"" + consoleMacro + "\"";
            resultColor = SUCCESS;
        }));
    }

    private void openAssign() {
        if (consoleMacro.isBlank()) {
            resultText = "Choose a macro first (Macro button)";
            resultColor = MUTED;
            return;
        }
        minecraft.gui.setScreen(new AutismMultiAssignScreen(this, consoleMacro, selectedIds));
    }

    private void runMacroScope() {
        MultiManager m = MultiManager.get();
        if (m.hasAssignedMacroOnInteractiveScope(actionScope())) {
            applyResult(m.runMacroOnInteractiveScope(actionScope()));
        } else if (!consoleMacro.isBlank()) {
            autismclient.util.AutismMacro macro = autismclient.util.AutismMacroManager.get().get(consoleMacro);
            if (macro != null) applyResult(m.runMacroDirectInteractive(macro, actionScope()));
            else { resultText = "Macro not found"; resultColor = ERROR; }
        } else {
            resultText = "Choose or assign a macro first";
            resultColor = MUTED;
        }
    }

    private void stopMacroScope() {
        applyResult(MultiManager.get().stopMacroOnInteractiveScope(actionScope()));
    }

    private void doUse() {
        applyResult(MultiManager.get().useOnScope(actionScope()));
    }

    private void doClose() {
        applyResult(MultiManager.get().closeOnScope(actionScope()));
    }

    private void doCloseSilent() {
        applyResult(MultiManager.get().closeSilentOnScope(actionScope()));
    }

    private void doOpenInventory() {
        if (selectedIds.size() != 1) {
            resultText = "Select one bot";
            resultColor = MUTED;
            return;
        }
        enterView(selectedIds.iterator().next());
    }

    private void applyResult(MultiManager.BroadcastResult result) {
        resultText = shortResult(result);
        resultColor = result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
    }

    private void enterView(String accountId) {
        viewingId = accountId;
        sharedView = false;
        viewScroll = 0;
        chatScroll = 0;
        cachedViewId = null;
        rebuildControls();
    }

    private void exitView() {
        viewingId = null;
        sharedView = false;
        chatScroll = 0;
        cachedView = null;
        cachedViewId = null;
        rebuildControls();
    }

    private void toggleSharedView() {
        sharedView = !sharedView;
        viewingId = null;
        viewScroll = 0;
        chatScroll = 0;
        cachedView = null;
        cachedViewId = null;
        rebuildControls();
    }

    private boolean isViewing() {
        return viewingId != null || sharedView;
    }

    private String viewTargetId() {
        if (!sharedView) return viewingId;
        MultiSharedGui.Group group = MultiSharedGui.pick(sharedGroups, sharedKey);
        return group == null ? null : group.representativeId();
    }

    private List<String> sharedIds() {
        if (!sharedView) return List.of();
        MultiSharedGui.Group group = MultiSharedGui.pick(sharedGroups, sharedKey);
        return group == null ? List.of() : group.accountIds();
    }

    private void applyFanout(MultiManager.BroadcastResult result) {
        if (result == null) return;
        int missed = result.failed() + result.skipped();
        if (result.sent() > 0 && missed == 0) return;
        resultText = result.sent() == 0 ? "No bot took the click" : "Sent " + result.sent() + ", missed " + missed;
        resultColor = result.sent() == 0 ? ERROR : MUTED;
    }

    private void toggleDetails() {
        detailsOpen = !detailsOpen;
        sessionScroll = 0;
        rebuildControls();
    }

    private void addSessionButtons() {
        sessionRows.clear();
        List<MultiSession.Snapshot> sessions = MultiManager.get().snapshots();
        sessionLayoutSignature = sessionLayoutSignature(sessions);

        Set<String> present = new java.util.HashSet<>();
        for (MultiSession.Snapshot s : sessions) present.add(s.accountId());
        selectedIds.retainAll(present);
        if (anchorId != null && !present.contains(anchorId)) anchorId = null;
        int top = 64;
        int bottom = screenHeight() - 46;
        int rowPitch = (detailsOpen ? DETAIL_ROW_H : ROW_HEIGHT) + MACRO_SUB_H;

        int viewH = Math.max(1, bottom - top);
        int maxScroll = 0;
        int tail = 0;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            tail += rowPitch + guiExtra(sessions.get(i));
            if (tail >= viewH) {
                maxScroll = i;
                break;
            }
        }
        sessionScroll = Math.max(0, Math.min(sessionScroll, maxScroll));
        int y = top;
        for (int i = sessionScroll; i < sessions.size(); i++) {

            int extra = guiExtra(sessions.get(i));
            if (!sessionRows.isEmpty() && y + rowPitch + extra > bottom) break;
            sessionRows.add(new SessionRow(sessions.get(i).accountId(), y, extra));
            y += rowPitch + extra;
        }
    }

    private static long sessionLayoutSignature(List<MultiSession.Snapshot> sessions) {
        long hash = sessions.size();
        for (MultiSession.Snapshot snapshot : sessions) {
            hash = 31L * hash + snapshot.accountId().hashCode();
            hash = 31L * hash + (guiExtra(snapshot) > 0 ? 1 : 0);
        }
        return hash;
    }

    private void refreshSessionLayoutIfNeeded() {
        List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();
        if (sessionLayoutSignature(snapshots) != sessionLayoutSignature) addSessionButtons();
    }

    private static int guiExtra(MultiSession.Snapshot snapshot) {
        String gui = snapshot.openScreen();
        return snapshot.customMenuOpen() || (gui != null && !gui.isBlank()) ? GUI_SUB_H : 0;
    }

    private int rowFrameHeight() {
        return detailsOpen ? DETAIL_ROW_H - 5 : 20;
    }

    private void triggerSessionAction(String id) {
        MultiSession.Snapshot current = findSnapshot(id);
        if (current != null && !MultiManager.isRetryable(current.status())) {
            MultiManager.get().disconnectSession(id);
            resultText = "Stopped";
            resultColor = ERROR;
        } else {
            MultiManager.RetryResult result = MultiManager.get().retry(id);
            resultText = MultiManager.singleLine(result.message(), 40);
            resultColor = result.ok() ? SUCCESS : ERROR;
        }
    }

    private void retryAll() {
        MultiManager.RetryResult result = MultiManager.get().retryAllDisconnected();
        resultText = MultiManager.singleLine(result.message(), 40);
        resultColor = result.ok() ? SUCCESS : MUTED;
    }

    private void openProfiles() {
        if (minecraft != null) minecraft.gui.setScreen(new AutismMultiScreen(parent, "", true));
    }

    private int actionX() {
        return MARGIN + sessionWidth() - actionWidth() - 4;
    }

    private static final int ACTION_H = 16;

    private int actionWidth() {
        return sessionWidth() < 180 ? 44 : 60;
    }

    private void renderSessionRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        macroRowTooltip = null;
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int sessionW = sessionWidth();
        int actionW = actionWidth();
        for (SessionRow row : sessionRows) {
            MultiSession.Snapshot snapshot = findSnapshot(row.id());
            if (snapshot == null) continue;
            int color = statusColor(snapshot);
            boolean selected = selectedIds.contains(row.id());
            int selectionColor = 0xFFFF66CC;
            int frameH = rowFrameHeight() + MACRO_SUB_H + row.guiExtra();

            int frameColor = selected ? selectionColor : color;
            int fill = (frameColor & 0x00FFFFFF) | (selected ? 0x44000000 : 0x1F000000);

            UiRenderer.rect(graphics, UiBounds.of(MARGIN + 4, row.y(), sessionW - 8, frameH), fill);
            UiRenderer.rect(graphics, UiBounds.of(MARGIN + 4, row.y(), 3, frameH), frameColor);
            int actionX = actionX();

            boolean showView = sessionW >= 220;
            int viewX = showView ? actionX - 6 - GUI_W : actionX;
            boolean viewingThis = row.id().equals(viewingId);
            int viewBorder = autismclient.util.AutismTheme.recolor(0x99662C2C, autismclient.util.AutismTheme.Channel.OUTLINE);
            int viewFill = viewingThis ? 0x555AD16A : 0x33000000;
            if (showView) {

                UiRenderer.frame(graphics, UiBounds.of(viewX, row.y() + 2, GUI_W, ACTION_H), viewFill, viewBorder);
                if (viewingThis) UiRenderer.rect(graphics, UiBounds.of(viewX + 1, row.y() + 2 + ACTION_H - 2, GUI_W - 2, 1), 0xFF5AD16A);
                int labelColor = viewingThis ? 0xFF5AD16A : 0xFF9AA6B2;
                String viewLabelFit = UiText.trimToWidthEllipsis(font, "GUI", GUI_W - 4, fontId, labelColor);
                int viewLabelW = UiText.width(font, viewLabelFit, fontId, labelColor);
                UiText.draw(graphics, font, viewLabelFit, fontId, labelColor,
                    viewX + Math.max(2, (GUI_W - viewLabelW) / 2), row.y() + 6, false);
            }

            boolean povThis = autismclient.util.multi.MultiTakeoverState.isActive(row.id());
            boolean povOk = povThis || autismclient.util.multi.MultiTakeoverState.available(row.id());
            boolean showPov = showView && povOk;
            int povX = showPov ? viewX - 6 - POV_W : viewX;
            if (showPov) {
                int povFill = povThis ? 0x555AD16A : 0x33000000;
                UiRenderer.frame(graphics, UiBounds.of(povX, row.y() + 2, POV_W, ACTION_H), povFill, viewBorder);
                if (povThis) UiRenderer.rect(graphics, UiBounds.of(povX + 1, row.y() + 2 + ACTION_H - 2, POV_W - 2, 1), 0xFF5AD16A);
                int labelColor = povThis ? 0xFF5AD16A : 0xFF9AA6B2;
                String povFit = UiText.trimToWidthEllipsis(font, "POV", POV_W - 4, fontId, labelColor);
                int povLabelW = UiText.width(font, povFit, fontId, labelColor);
                UiText.draw(graphics, font, povFit, fontId, labelColor,
                    povX + Math.max(2, (POV_W - povLabelW) / 2), row.y() + 6, false);
            }
            String ping = snapshot.ping() >= 0 ? snapshot.ping() + "ms" : "--";
            int pingWidth = UiText.width(font, ping, fontId, color);
            int pingX = (showView ? povX : actionX) - 6 - pingWidth;
            UiText.draw(graphics, font, ping, fontId, color, pingX, row.y() + 6, false);
            int rightEdge = pingX;

            int macroY = row.y() + 17;
            drawMacroRow(graphics, fontId, snapshot, MARGIN + 12, macroY, sessionW - 24);
            if (mouseX >= MARGIN + 4 && mouseX < MARGIN + sessionW - 4
                && mouseY >= macroY - 1 && mouseY < macroY + MACRO_SUB_H) {
                macroRowTooltip = MultiMacroPresentation.tooltip(MultiManager.get(), snapshot);
            }
            if (row.guiExtra() > 0) {
                String gui = snapshot.openScreen();
                String guiLabel = snapshot.customMenuOpen()
                    ? "GUI: CustomScreen"
                    : "GUI: " + MultiManager.singleLine(gui == null ? "" : gui, 48);
                UiText.draw(graphics, font,
                    UiText.trimToWidthEllipsis(font, guiLabel, sessionW - 24, fontId, themeMuted()),
                    fontId, themeMuted(), MARGIN + 12, row.y() + 17 + MACRO_SUB_H, false);
            }
            int nameX = MARGIN + 12;
            int nameMax = Math.max(1, rightEdge - nameX - 6);

            int nameColor = color == STATUS_RED ? STATUS_RED : themeText();
            String name = UiText.trimToWidthEllipsis(font, MultiManager.singleLine(snapshot.accountName(), 48), nameMax, fontId, nameColor);
            UiText.draw(graphics, font, name, fontId, nameColor, nameX, row.y() + 6, false);

            String actionLabel = MultiManager.isRetryable(snapshot.status()) ? "Retry" : "Stop";
            int actionFill = (color & 0x00FFFFFF) | 0x59000000;
            UiRenderer.frame(graphics, UiBounds.of(actionX, row.y() + 2, actionW, ACTION_H), actionFill, viewBorder);
            UiRenderer.rect(graphics, UiBounds.of(actionX + 1, row.y() + 2 + ACTION_H - 2, actionW - 2, 1), color);
            int labelWidth = UiText.width(font, actionLabel, fontId, color);
            UiText.draw(graphics, font, actionLabel, fontId, color,
                actionX + Math.max(2, (actionW - labelWidth) / 2), row.y() + 6, false);
            if (detailsOpen) {
                int dx = MARGIN + 12;
                int dw = sessionW - 24;
                int dy = row.y() + 22 + MACRO_SUB_H + row.guiExtra();
                drawStatsLine(graphics, fontId, snapshot, dx, dy);
                String held = snapshot.heldItem() == null || snapshot.heldItem().isBlank() ? "empty" : snapshot.heldItem();
                drawDetailLine(graphics, fontId, "Held: " + held + "   Slot " + snapshot.hotbarSlot(), dx, dy + 12, dw);
                String dim = snapshot.dimension() == null || snapshot.dimension().isBlank() ? "?" : snapshot.dimension();
                drawDetailLine(graphics, fontId, "World: " + dim, dx, dy + 23, dw);
                String pos = snapshot.hasPosition()
                    ? String.format(java.util.Locale.ROOT, "Pos: %.1f  %.1f  %.1f", snapshot.x(), snapshot.y(), snapshot.z())
                    : "Pos: --";
                drawDetailLine(graphics, fontId, pos, dx, dy + 34, dw);
            }
        }
    }

    private void drawMacroRow(GuiGraphicsExtractor graphics, Identifier fontId, MultiSession.Snapshot snapshot,
                              int x, int y, int width) {
        MultiManager manager = MultiManager.get();
        String assignedName = MultiMacroPresentation.assignedName(manager, snapshot);
        String assigned = MultiMacroPresentation.assignedLabel(assignedName);
        String playingName = MultiMacroPresentation.playingName(snapshot);
        if (playingName.isBlank()) {
            UiText.draw(graphics, font,
                UiText.trimToWidthEllipsis(font, assigned, width, fontId, MultiMacroPresentation.ASSIGNED_GRAY),
                fontId, MultiMacroPresentation.ASSIGNED_GRAY, x, y, false);
            return;
        }
        String playing = MultiMacroPresentation.playingLabel(playingName);
        int playingW = Math.min(width, UiText.width(font, playing, fontId, MultiMacroPresentation.PLAYING_GREEN));
        int gap = 7;
        int assignedW = Math.max(0, width - playingW - gap);
        int cx = x;
        if (assignedW > 12) {
            String fit = UiText.trimToWidthEllipsis(font, assigned, assignedW, fontId, MultiMacroPresentation.ASSIGNED_GRAY);
            UiText.draw(graphics, font, fit, fontId, MultiMacroPresentation.ASSIGNED_GRAY, cx, y, false);
            cx += assignedW + gap;
        }
        String fitPlaying = UiText.trimToWidthEllipsis(font, playing, Math.max(1, x + width - cx), fontId,
            MultiMacroPresentation.PLAYING_GREEN);
        UiText.draw(graphics, font, fitPlaying, fontId, MultiMacroPresentation.PLAYING_GREEN, cx, y, false);
    }

    private static boolean rectHover(int[] rect, int mouseX, int mouseY) {
        return rect != null && MultiTooltip.hovered(rect[0], rect[1], rect[2], rect[3], mouseX, mouseY);
    }

    private void drawDetailLine(GuiGraphicsExtractor graphics, Identifier fontId, String text, int x, int y, int width) {
        UiText.draw(graphics, font, UiText.trimToWidthEllipsis(font, text, width, fontId, themeMuted()), fontId, themeMuted(), x, y, false);
    }

    private void drawStatsLine(GuiGraphicsExtractor graphics, Identifier fontId, MultiSession.Snapshot snapshot, int x, int y) {
        int cx = x;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_SPRITE, cx, y - 1, 9, 9);
        cx += 11;
        String hp = trimNumber(snapshot.health()) + "/" + trimNumber(snapshot.maxHealth());
        UiText.draw(graphics, font, hp, fontId, themeText(), cx, y, false);
        cx += UiText.width(font, hp, fontId, themeText()) + 10;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_SPRITE, cx, y - 1, 9, 9);
        cx += 11;
        UiText.draw(graphics, font, snapshot.food() + "/20", fontId, themeText(), cx, y, false);
    }

    private static String trimNumber(float value) {
        return value == Math.rint(value)
            ? Integer.toString((int) value)
            : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int statusColor(MultiSession.Snapshot snapshot) {
        return switch (snapshot.displayState(System.currentTimeMillis())) {
            case GREEN -> STATUS_GREEN;
            case RED -> STATUS_RED;
            case YELLOW -> STATUS_YELLOW;
        };
    }

    private void sendChat() {
        String value = chatInput.getValue();
        if (value == null || value.isBlank()) {

            String last = MultiManager.get().lastHistoryEntry();
            if (last == null || last.isBlank()) {
                resultText = "Empty input";
                resultColor = MUTED;
                return;
            }
            value = last;
        }

        Set<String> targets = !selectedIds.isEmpty() ? selectedIds
            : viewingId != null ? java.util.Set.of(viewingId) : selectedIds;
        MultiManager.BroadcastResult result = MultiManager.get().broadcastConsole(value, targets);
        resultText = shortResult(result);
        resultColor = result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
        MultiManager.get().pushHistory(value);
        if (result.sent() > 0) chatInput.setValue("");
        historyIndex = -1;
        updateGhostSuggestion();
        clearSuggestions();
    }

    private void updateGhostSuggestion() {
        if (chatInput == null) return;
        String last = chatInput.getValue().isEmpty() ? MultiManager.get().lastHistoryEntry() : null;
        chatInput.setSuggestion(last);
    }

    private void sendMovement() {
        MultiManager.BroadcastResult result = MultiManager.get().broadcastMovementNow(actionScope());
        resultText = shortResult(result);
        resultColor = result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
    }

    private void sendQuickAction(int index) {
        MultiProfile profile = MultiManager.get().activeProfile();
        if (profile == null) return;
        MultiQuickAction action = profile.quickAction(index);
        if (action.empty()) {
            openQuickEditor(index);
            return;
        }
        MultiManager.BroadcastResult result = MultiManager.get().broadcastQuickAction(action, actionScope());
        resultText = shortResult(result);
        resultColor = result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
    }

    private void openQuickEditor(int index) {
        MultiProfile profile = MultiManager.get().activeProfile();
        if (profile == null || minecraft == null) return;
        minecraft.gui.setScreen(new AutismMultiQuickActionScreen(
            this,
            index,
            profile.quickAction(index),
            action -> {
                MultiManager.get().updateQuickAction(index, action);
                resultText = "Saved";
                resultColor = SUCCESS;
            },
            action -> {
                MultiManager.BroadcastResult result = MultiManager.get().broadcastQuickAction(action, actionScope());
                resultText = shortResult(result);
                resultColor = result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
                return result;
            }
        ));
    }

    private void resetQuickActions() {
        MultiManager.get().resetQuickActions();
        resultText = "Reset";
        resultColor = SUCCESS;
        rebuildControls();
    }

    private void openPolicy() {
        MultiProfile profile = MultiManager.get().activeProfile();
        if (profile == null) return;
        minecraft.gui.setScreen(new AutismMultiPacketPolicyScreen(
            this,
            profile.packetPolicy,
            MultiManager.get()::updatePolicy,
            profile.autoAccept,
            MultiManager.get()::updateAutoAccept,
            true
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (!MultiManager.get().isActive()) {
            if (minecraft != null) minecraft.gui.setScreen(parent);
            return;
        }
        if (viewingId != null && findSnapshot(viewingId) == null) exitView();
        long uiRevision = MultiManager.get().uiRevision();
        if (uiRevision != lastUiRevision) {
            rebuildControls();
        } else {
            long sessionRevision = MultiManager.get().sessionRevision();
            if (sessionRevision != lastSessionRevision) {
                lastSessionRevision = sessionRevision;
                addSessionButtons();
            }
        }
        refreshSessionLayoutIfNeeded();
        refreshSuggestions();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {

        if (menuInput.rename.focused()) {
            if (menuInput.rename.keyPressed(event.key())) {
                sendRename();
                return true;
            }
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_C && event.hasControlDown()
            && chatSel.hasSelection() && (chatInput == null || !chatInput.isFocused())) {
            copyChatSelection();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && chatSel.hasSelection()) {
            chatSel.clear();
            return true;
        }
        if (isViewing() && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            exitView();
            return true;
        }
        boolean chatFocused = chatInput != null && chatInput.isFocused();
        if (isViewing() && !chatFocused && hoveredViewHandler >= 0 && minecraft != null
            && minecraft.options.keyDrop.matches(event)) {
            if (cachedView == null || !cachedView.interactive()) {
                resultText = cachedView != null && cachedView.synchronizationBlocked()
                    ? "Inventory is waiting for a server update" : "Inventory is synchronizing";
                resultColor = MUTED;
                return true;
            }
            MultiClientCommands.ClickSpec spec = MultiClientCommands.dropSpec(event.hasControlDown());
            if (sharedView) applyFanout(MultiManager.get().clickBotSlots(sharedIds(), hoveredViewHandler, spec));
            else MultiManager.get().clickBotSlot(viewingId, hoveredViewHandler, spec);
            return true;
        }

        if (isViewing() && !chatFocused && hoveredViewHotbar >= 0
            && (event.key() == GLFW.GLFW_KEY_U || event.key() == GLFW.GLFW_KEY_I)) {
            if (cachedView == null || !cachedView.interactive()) {
                resultText = cachedView != null && cachedView.synchronizationBlocked()
                    ? "Inventory is waiting for a server update" : "Inventory is synchronizing";
                resultColor = MUTED;
                return true;
            }
            boolean use = event.key() == GLFW.GLFW_KEY_I;
            if (sharedView) {
                applyFanout(use ? MultiManager.get().useBotHotbars(sharedIds(), hoveredViewHotbar)
                    : MultiManager.get().selectBotHotbars(sharedIds(), hoveredViewHotbar));
            } else {
                String result = use ? MultiManager.get().useBotHotbar(viewingId, hoveredViewHotbar)
                    : MultiManager.get().selectBotHotbar(viewingId, hoveredViewHotbar);
                if (!"Sent".equals(result)) {
                    resultText = result;
                    resultColor = MUTED;
                }
            }
            return true;
        }
        if (chatInput != null && chatInput.isFocused()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    sendChat();
                    return true;
                }
                case GLFW.GLFW_KEY_TAB -> {

                    cycleSuggestion((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    recallHistory(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    recallHistory(1);
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    if (!suggestions.isEmpty()) {
                        clearSuggestions();
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {

        if (menuInput.rename.focused()) {
            if (menuInput.rename.charTyped((char) input.codepoint())) sendRename();
            return true;
        }
        return super.charTyped(input);
    }

    private void recallHistory(int direction) {
        List<String> history = MultiManager.get().commandHistory();
        if (history.isEmpty() || chatInput == null) return;
        if (direction < 0) {
            if (historyIndex == -1) {
                historyDraft = chatInput.getValue();
                historyIndex = history.size() - 1;
            } else if (historyIndex > 0) {
                historyIndex--;
            }
        } else {
            if (historyIndex == -1) return;
            historyIndex++;
            if (historyIndex >= history.size()) {
                historyIndex = -1;
                setInput(historyDraft);
                return;
            }
        }
        setInput(history.get(historyIndex));
    }

    private void setInput(String value) {
        chatInput.setValue(value);
        chatInput.moveCursorToEnd(false);
        clearSuggestions();
    }

    private boolean cycleSuggestion(boolean backwards) {
        if (!suggestionsFresh()) return false;
        if (appliedOnce) suggestionIndex = Math.floorMod(suggestionIndex + (backwards ? -1 : 1), suggestions.size());
        appliedOnce = true;
        frozen = true;
        applySuggestion(suggestionIndex);
        return true;
    }

    private boolean suggestionsFresh() {
        if (suggestions.isEmpty() || chatInput == null) return false;
        String value = chatInput.getValue();
        return value.equals(suggestOriginal) || value.equals(expectedValue);
    }

    private void applySuggestion(int index) {
        if (chatInput == null || suggestOriginal == null || index < 0 || index >= suggestions.size()) return;
        int start = Math.max(0, Math.min(suggestStart, suggestOriginal.length()));
        int end = Math.max(start, Math.min(suggestStart + suggestLength, suggestOriginal.length()));
        String entry = suggestions.get(index);
        String full = suggestOriginal.substring(0, start) + entry + suggestOriginal.substring(end);
        chatInput.setValue(full);
        int caret = Math.min(start + entry.length(), full.length());
        chatInput.setCursorPosition(caret);
        chatInput.setHighlightPos(caret);
        expectedValue = full;
    }

    private void clearSuggestions() {
        suggestions.clear();
        suggestRects.clear();
        suggestionIndex = 0;
        suggestForText = null;
        suggestOriginal = null;
        expectedValue = null;
        appliedOnce = false;
        frozen = false;
    }

    private void refreshSuggestions() {
        if (chatInput == null) return;
        String value = chatInput.getValue();
        if (!chatInput.isFocused()) {
            if (!suggestions.isEmpty() || suggestForText != null) clearSuggestions();
            lastRequestedCmd = null;
            return;
        }
        if (frozen) {
            if (value.equals(expectedValue)) return;
            frozen = false;
        }
        if (value.equals(suggestForText)) return;
        if (value.startsWith("/")) {
            refreshServerSuggestions(value);
        } else if (AutismCommands.isAutismCommandMessage(value)) {
            refreshClientSuggestions(value);
        } else {
            if (!suggestions.isEmpty() || suggestForText != null) clearSuggestions();
            lastRequestedCmd = null;
        }
    }

    private void refreshServerSuggestions(String value) {
        String stripped = value.substring(1);
        long now = System.currentTimeMillis();
        if (!value.equals(lastRequestedCmd) && now - lastRequestAt >= 60) {
            MultiManager.get().requestSuggestions(stripped, actionScope());
            lastRequestedCmd = value;
            lastRequestAt = now;
        }
        MultiManager.SuggestionResult result = MultiManager.get().suggestions(stripped);
        if (result == null) return;
        applyResult(value, 1 + result.start(), result.length(), result.entries());
    }

    private void refreshClientSuggestions(String value) {
        lastRequestedCmd = null;
        autismclient.gui.multi.MultiChatCompletion.Result r = autismclient.gui.multi.MultiChatCompletion.clientSuggestions(value);
        if (r == null) return;
        applyResult(value, r.start(), r.length(), r.entries());
    }

    private void applyResult(String value, int absStart, int length, List<String> entries) {
        if (!value.equals(suggestForText)) {
            suggestionIndex = 0;
            appliedOnce = false;
        }
        suggestForText = value;
        suggestOriginal = value;
        suggestStart = absStart;
        suggestLength = length;
        suggestions.clear();
        suggestions.addAll(entries);
        if (suggestionIndex >= suggestions.size()) suggestionIndex = Math.max(0, suggestions.size() - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0 && suggestionsFresh()) {
            for (int[] rect : suggestRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2] && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]) {
                    suggestionIndex = rect[4];
                    appliedOnce = true;
                    frozen = true;
                    applySuggestion(suggestionIndex);
                    if (chatInput != null) chatInput.setFocused(true);
                    return true;
                }
            }
        }
        if (sharedView && virtualEvent.button() == 0) {
            for (int[] rect : sharedTabRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2]
                    && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]
                    && rect[4] < sharedGroups.size()) {
                    sharedKey = sharedGroups.get(rect[4]).key();
                    viewScroll = 0;
                    cachedViewId = null;
                    return true;
                }
            }
        }
        if (isViewing()) {
            if (virtualEvent.button() == 0) {
                for (MultiMenuRenderer.MenuHit hit : viewWidgetHits) {
                    if (virtualEvent.x() < hit.x() || virtualEvent.x() >= hit.x() + hit.w()
                        || virtualEvent.y() < hit.y() || virtualEvent.y() >= hit.y() + hit.h()) continue;
                    clearInputFocus();
                    dispatchViewWidget(hit.action());
                    return true;
                }
            }
            for (int[] rect : viewSlotRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + 18 && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + 18) {

                    clearInputFocus();
                    handleViewClick(rect[2], virtualEvent.button(), virtualEvent.hasShiftDown(), virtualEvent.hasControlDown());
                    return true;
                }
            }

            if (virtualEvent.x() >= viewGridX && virtualEvent.x() < viewGridX + viewGridW
                && virtualEvent.y() >= viewGridY && virtualEvent.y() < viewGridY + viewGridH) {
                clearInputFocus();
                return true;
            }
        }
        if (virtualEvent.button() == 1) {
            for (int[] rect : presetRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2] && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3]) {
                    openQuickEditor(rect[4]);
                    return true;
                }
            }
        }
        if (virtualEvent.button() == 0) {
            for (SessionRow row : sessionRows) {
                int ax = actionX();
                int ay = row.y() + 2;
                if (virtualEvent.x() >= ax && virtualEvent.x() < ax + actionWidth() && virtualEvent.y() >= ay && virtualEvent.y() < ay + ACTION_H) {
                    triggerSessionAction(row.id());
                    return true;
                }
                int viewX = ax - 6 - GUI_W;
                if (sessionWidth() >= 220 && virtualEvent.x() >= viewX && virtualEvent.x() < viewX + GUI_W
                    && virtualEvent.y() >= ay && virtualEvent.y() < ay + ACTION_H) {
                    if (row.id().equals(viewingId)) exitView(); else enterView(row.id());
                    return true;
                }

                boolean povShown = sessionWidth() >= 220
                    && (autismclient.util.multi.MultiTakeoverState.isActive(row.id())
                        || autismclient.util.multi.MultiTakeoverState.available(row.id()));
                int povX = viewX - 6 - POV_W;
                if (povShown && virtualEvent.x() >= povX && virtualEvent.x() < povX + POV_W
                    && virtualEvent.y() >= ay && virtualEvent.y() < ay + ACTION_H) {
                    autismclient.util.multi.MultiTakeoverState.toggle(row.id(), this);
                    return true;
                }
                if (virtualEvent.x() >= MARGIN + 4 && virtualEvent.x() < MARGIN + sessionWidth() - 4
                    && virtualEvent.y() >= row.y()
                    && virtualEvent.y() < row.y() + rowFrameHeight() + MACRO_SUB_H + row.guiExtra()) {
                    selectRow(row.id(), virtualEvent.hasControlDown(), virtualEvent.hasShiftDown());
                    return true;
                }
            }
            if (chatScrollbar != null && chatScrollbar.hasScroll() && chatScrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
                chatScrollbarDragging = true;
                chatScrollbarGrab = chatScrollbar.overThumb(virtualEvent.x(), virtualEvent.y())
                    ? (int) Math.round(virtualEvent.y()) - chatScrollbar.thumbY() : chatScrollbar.thumbHeight() / 2;
                chatScrollbarDrag(virtualEvent.y());
                return true;
            }

            if (handleChatClick(virtualEvent.x(), virtualEvent.y())) return true;
            if (beginChatSelection(virtualEvent.x(), virtualEvent.y())) return true;
        }
        boolean handled = super.mouseClicked(virtualEvent, doubled);
        if (!handled && virtualEvent.button() == 0) {
            clearInputFocus();
            if (!selectedIds.isEmpty()) chatScroll = 0;
            selectedIds.clear();
            anchorId = null;
            return true;
        }
        return handled;
    }

    private void selectRow(String id, boolean ctrl, boolean shift) {
        LinkedHashSet<String> before = new LinkedHashSet<>(selectedIds);
        List<String> ordered = orderedIds();
        if (shift && anchorId != null && ordered.contains(anchorId) && ordered.contains(id)) {
            int a = ordered.indexOf(anchorId);
            int b = ordered.indexOf(id);
            selectedIds.clear();
            for (int i = Math.min(a, b); i <= Math.max(a, b); i++) selectedIds.add(ordered.get(i));
        } else if (ctrl) {
            if (!selectedIds.remove(id)) selectedIds.add(id);
            anchorId = id;
        } else {
            boolean only = selectedIds.size() == 1 && selectedIds.contains(id);
            selectedIds.clear();
            if (!only) {
                selectedIds.add(id);
                anchorId = id;
            } else {
                anchorId = null;
            }
        }
        if (!before.equals(selectedIds)) chatScroll = 0;
    }

    private List<String> orderedIds() {
        List<String> ids = new ArrayList<>();
        for (MultiSession.Snapshot s : MultiManager.get().snapshots()) ids.add(s.accountId());
        return ids;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (chatScrollbarDragging) {
            chatScrollbarDragging = false;
            return true;
        }
        if (chatSelectingPress) {
            chatSelectingPress = false;
            chatSel.finishDrag();
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (chatScrollbarDragging) {
            chatScrollbarDrag(virtualEvent.y());
            return true;
        }
        if (chatSelectingPress) {
            chatSelectAt(virtualEvent.x(), virtualEvent.y(), false);
            return true;
        }
        return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dragX), AutismUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double vx = AutismUiScale.toVirtual(mouseX);
        double vy = AutismUiScale.toVirtual(mouseY);
        if (vx < MARGIN + sessionWidth()) {
            sessionScroll = Math.max(0, sessionScroll + (vertical < 0 ? 1 : -1));
            addSessionButtons();
            return true;
        }

        if (isViewing() && vy < chatTop) {
            viewScroll = Math.max(0, viewScroll + (vertical < 0 ? 18 : -18));
            return true;
        }

        chatScroll = Math.max(0, chatScroll + (vertical > 0 ? 1 : -1));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), themeBg());
        int sessionW = sessionWidth();
        UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, sessionW, screenHeight() - 58), themePanelSoft(), themeBorder());
        int consoleX = consoleX();
        int consoleW = consoleWidth();
        UiRenderer.frame(graphics, UiBounds.of(consoleX, 14, consoleW, screenHeight() - 58), themePanel(), themeBorder());
        List<MultiSession.Snapshot> snapshots = MultiManager.get().snapshots();

        frameSnapshots.clear();
        int ready = 0;
        for (MultiSession.Snapshot s : snapshots) {
            frameSnapshots.put(s.accountId(), s);
            if (s.ready()) ready++;
        }
        drawFitted(graphics, "Sessions  " + ready + "/" + snapshots.size() + " ready",
            MARGIN + 8, 24, Math.max(1, sessionW - 84), themeText());
        MultiSession.Snapshot viewed = selectedIds.size() == 1 ? findSnapshot(selectedIds.iterator().next()) : null;
        if (viewed != null) {
            String detail = UiText.trimToWidthEllipsis(font, detailLabel(viewed), Math.max(1, sessionW - 16),
                THEME.fontFor(UiTone.BODY), themeMuted());
            boolean up = viewed.displayState(System.currentTimeMillis()) == MultiSession.DisplayState.GREEN;
            drawText(graphics, detail, MARGIN + 8, 35, up ? themeMuted() : statusColor(viewed));
        } else if (!selectedIds.isEmpty()) {
            drawText(graphics, selectedIds.size() + " selected", MARGIN + 8, 35, themeMuted());
        } else if (snapshots.isEmpty()) {
            drawText(graphics, "Starting sessions...", MARGIN + 8, 66, themeMuted());
        }
        if (sharedView) sharedGroups = MultiSharedGui.groups();
        String viewTarget = viewTargetId();
        boolean viewing = viewTarget != null && findSnapshot(viewTarget) != null;
        if (viewing) refreshView(viewTarget);
        if (viewing && cachedView != null) {

            AutismUiScale.enableOverlayScissor(graphics, consoleX + 10, 22, consoleX + consoleW - 72, 36);

            Component viewTitle = cachedView.title();
            graphics.text(font, viewTitle.getVisualOrderText(), consoleX + 10, 24, themeText(), false);
            graphics.disableScissor();
        } else {
            drawFitted(graphics, "Console", consoleX + 10, 24, consoleW - 20, themeText());
        }
        if (viewing) {
            int gridTop = 118;
            if (sharedView) {
                renderSharedTabs(graphics, consoleX + 10, gridTop, consoleW - 20, virtualMouseX, virtualMouseY);
                gridTop += 20;
            }

            int bottomLimit = screenHeight() - 48;
            int available = bottomLimit - gridTop;
            int gridMaxH = Math.max(36, available - CHAT_MIN_H - 12);
            renderGuiView(graphics, consoleX + 10, gridTop, consoleW - 20, gridMaxH, virtualMouseX, virtualMouseY);
            infoColumnTop = gridTop + 2;
            int chatTop = gridTop + Math.max(12, viewGridH) + 12;
            renderChat(graphics, consoleX + 10, chatTop, consoleW - 20, Math.max(0, bottomLimit - chatTop));
        } else {
            sharedTabRects.clear();
            if (sharedView) {
                drawFitted(graphics, "No bot has a GUI open yet.", consoleX + 10, 120, consoleW - 20, themeMuted());
            }

            int chatTop = 138;
            renderChat(graphics, consoleX + 10, chatTop, consoleW - 20, Math.max(0, (screenHeight() - 44) - chatTop));
        }
        if (!resultText.isBlank()) drawFitted(graphics, resultText, consoleX + 10, screenHeight() - 50, consoleW - 20, themeStatusColor(resultColor));

        renderSessionRows(graphics, virtualMouseX, virtualMouseY);
        super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);

        renderSuggestions(graphics, virtualMouseX, virtualMouseY);

        if (viewing) {
            ItemStack carried = cachedView != null ? cachedView.carried() : ItemStack.EMPTY;
            if (carried != null && !carried.isEmpty()) {
                graphics.nextStratum();
                graphics.item(carried, virtualMouseX - 8, virtualMouseY - 8);
                graphics.itemDecorations(font, carried, virtualMouseX - 8, virtualMouseY - 8);
            } else if (!hoveredViewStack.isEmpty()) {
                drawItemTooltip(graphics, hoveredViewStack, hoveredViewX, hoveredViewY);
            }

            if (cachedView != null && viewGridW > 0) {
                graphics.nextStratum();
                int colW = font.width("SyncID: 000000") + 6;
                int infoX = consoleX + consoleW - 10 - colW;
                if (infoX >= consoleX + 10 + viewGridW + 8) {
                    drawMetric(graphics, infoX, infoColumnTop, colW, "Rev: ", Integer.toString(cachedView.stateId()),
                        autismclient.util.AutismTheme.recolor(0xFFFF4A4A, autismclient.util.AutismTheme.Channel.ACCENT));
                    drawMetric(graphics, infoX, infoColumnTop + 11, colW, "SyncID: ", Integer.toString(cachedView.syncId()),
                        autismclient.util.AutismTheme.recolor(0xFFF3ECE7, autismclient.util.AutismTheme.Channel.TEXT));
                    int visibleSlot = hoveredViewHandler >= 0
                        ? MultiManager.get().visibleSlotForHandler(viewTargetId(), hoveredViewHandler) : -1;
                    drawMetric(graphics, infoX, infoColumnTop + 22, colW, "Slot: ",
                        visibleSlot >= 0 ? Integer.toString(visibleSlot) : "--",
                        autismclient.util.AutismTheme.recolor(0xFF8FD7FF, autismclient.util.AutismTheme.Channel.ACCENT));
                }
            }
        }

        if (autismclient.util.AutismConfig.getGlobal().multiShowTooltips) {
            String tip = null;
            if (rectHover(closeSilentRect, virtualMouseX, virtualMouseY)) {
                tip = "Hide GUI locally, keep it open";
            } else if (rectHover(sharedGuiRect, virtualMouseX, virtualMouseY)) {
                tip = "Control every bot's GUI at once";
            } else if (rectHover(detailsRect, virtualMouseX, virtualMouseY)) {
                tip = "Show HP, food, and position";
            } else if (macroRowTooltip != null) {
                tip = macroRowTooltip;
            }
            if (tip != null) MultiTooltip.render(graphics, font, tip, virtualMouseX, virtualMouseY);
        }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        suggestRects.clear();
        if (suggestions.isEmpty() || chatInput == null || !chatInput.isFocused()) return;
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int rowH = 12;
        int visible = Math.min(8, suggestions.size());
        int start = Math.max(0, Math.min(suggestionIndex - visible + 1, suggestions.size() - visible));
        int textW = 40;
        for (int i = 0; i < visible; i++) textW = Math.max(textW, font.width(suggestions.get(start + i)));
        int popupW = Math.min(240, textW + 10);
        int popupH = visible * rowH + 2;
        int caretIndex = Math.min(suggestStart, chatInput.getValue().length());
        int anchorX = chatInput.getScreenX(caretIndex) - 3;
        int minX = consoleX() + 10;
        int maxX = Math.max(minX, screenWidth() - MARGIN - popupW);
        int popupX = Math.max(minX, Math.min(anchorX, maxX));
        int popupY = Math.max(20, chatInput.getY() - popupH - 2);
        UiRenderer.frame(graphics, UiBounds.of(popupX, popupY, popupW, popupH), 0xEE0B0B0F, themeBorder());
        for (int i = 0; i < visible; i++) {
            int idx = start + i;
            int ry = popupY + 1 + i * rowH;
            boolean hover = mouseX >= popupX && mouseX < popupX + popupW && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = idx == suggestionIndex;
            if (sel || hover) UiRenderer.rect(graphics, UiBounds.of(popupX + 1, ry, popupW - 2, rowH), sel ? 0x662E7DFF : 0x33FFFFFF);
            int color = sel ? 0xFFFFFFFF : themeMuted();
            String label = UiText.trimToWidthEllipsis(font, suggestions.get(idx), popupW - 8, fontId, color);
            UiText.draw(graphics, font, label, fontId, color, popupX + 4, ry + 2, false);
            suggestRects.add(new int[]{popupX + 1, ry, popupW - 2, rowH, idx});
        }
    }

    private void refreshView(String target) {
        MultiSession.Snapshot snap = frameSnapshots.get(target);
        long rev = snap != null ? snap.menuRevision() : -1;
        if (!target.equals(cachedViewId) || rev != cachedViewRev) {
            cachedView = MultiManager.get().menuView(target);
            cachedViewId = target;
            cachedViewRev = rev;
        }
    }

    private void renderSharedTabs(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
        sharedTabRects.clear();
        MultiSharedGui.Group current = MultiSharedGui.pick(sharedGroups, sharedKey);
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int cx = x;
        for (int i = 0; i < sharedGroups.size(); i++) {
            MultiSharedGui.Group group = sharedGroups.get(i);
            int room = x + w - cx;
            if (room < 40) {
                UiText.draw(graphics, font, "+" + (sharedGroups.size() - i), fontId, themeMuted(), cx, y + 4, false);
                break;
            }
            boolean selected = current != null && group.key().equals(current.key());
            String label = UiText.trimToWidthEllipsis(font, group.label(), Math.min(150, room - 10), fontId, themeText());
            int bw = UiText.width(font, label, fontId, themeText()) + 10;
            boolean hover = mouseX >= cx && mouseX < cx + bw && mouseY >= y && mouseY < y + 16;
            UiRenderer.frame(graphics, UiBounds.of(cx, y, bw, 16),
                selected ? 0x3335D873 : hover ? 0x33FFFFFF : 0x2A121214,
                selected ? themeSuccess() : themeBorder());
            UiText.draw(graphics, font, label, fontId, selected ? themeText() : themeMuted(), cx + 5, y + 4, false);
            sharedTabRects.add(new int[]{cx, y, bw, 16, i});
            cx += bw + 4;
        }
    }

    private void renderGuiView(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        viewSlotRects.clear();
        MultiSession.MenuView view = cachedView;
        if (view == null) {
            viewGridX = x;
            viewGridY = y;
            viewGridW = 0;
            viewGridH = 0;
            hoveredViewStack = ItemStack.EMPTY;
            hoveredViewHandler = -1;
            hoveredViewHotbar = -1;
            drawFitted(graphics, "Loading...", x, y, w, themeMuted());
            return;
        }
        viewWidgetHits.clear();
        menuInput.sync(view.extras() == null ? "" : view.extras().typeId());

        int[] cs = MultiMenuRenderer.contentSize(view);
        int contentW = Math.max(18, cs[0]);
        int contentH = Math.max(18, cs[1]);

        int panelW = Math.min(w, contentW);
        int panelH = Math.min(h, contentH);
        viewScroll = Math.max(0, Math.min(viewScroll, Math.max(0, contentH - panelH)));
        viewGridX = x;
        viewGridY = y;
        viewGridW = panelW;
        viewGridH = panelH;

        AutismUiScale.enableOverlayScissor(graphics, x - 3, y - 3, x + panelW + 3, y + panelH + 3);

        UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, panelW + 6, panelH + 6), 0xFFC6C6C6);

        int selectedHandler = MultiManager.get().selectedHotbarHandler(viewTargetId());
        MultiSession.ViewSlot hovered = null;
        for (MultiSession.ViewSlot slot : view.slots()) {
            int sx = x + slot.x();
            int sy = y + slot.y() - viewScroll;
            if (sy + 18 < y || sy > y + panelH) continue;
            drawSlotCell(graphics, sx, sy);
            if (selectedHandler >= 0 && slot.handler() == selectedHandler) {
                UiRenderer.frame(graphics, UiBounds.of(sx, sy, 18, 18), 0x1AFF4040, 0xB0FF4040);
            }
            ItemStack stack = slot.item();
            if (stack != null && !stack.isEmpty()) {
                try {
                    graphics.item(stack, sx + 1, sy + 1);
                    graphics.itemDecorations(font, stack, sx + 1, sy + 1);
                } catch (Throwable ignored) {

                }
            }
            viewSlotRects.add(new int[]{sx, sy, slot.handler()});
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18
                && mouseX >= x && mouseX < x + panelW && mouseY >= y && mouseY < y + panelH) {
                hovered = slot;
            }
        }

        MultiMenuRenderer.render(graphics, font, view, x, y, viewScroll, mouseX, mouseY, viewWidgetHits, menuInput);
        graphics.disableScissor();
        hoveredViewStack = hovered != null && hovered.item() != null ? hovered.item() : ItemStack.EMPTY;
        hoveredViewHandler = hovered != null ? hovered.handler() : -1;
        hoveredViewHotbar = hovered != null
            ? MultiManager.get().hotbarIndexForHandler(viewTargetId(), hovered.handler()) : -1;
        hoveredViewX = mouseX;
        hoveredViewY = mouseY;
    }

    private void drawMetric(GuiGraphicsExtractor graphics, int x, int y, int w, String key, String value, int valueColor) {
        int keyColor = autismclient.util.AutismTheme.recolor(0xFFB79E9E, autismclient.util.AutismTheme.Channel.TEXT);
        graphics.text(font, Component.literal(key).getVisualOrderText(), x, y, keyColor, false);
        int vx = x + font.width(key);
        String fit = UiText.trimToWidthEllipsis(font, value, Math.max(1, x + w - vx), THEME.fontFor(UiTone.BODY), valueColor);
        graphics.text(font, Component.literal(fit).getVisualOrderText(), vx, y, valueColor, false);
    }

    private void drawSlotCell(GuiGraphicsExtractor graphics, int cx, int cy) {
        UiRenderer.rect(graphics, UiBounds.of(cx, cy, 18, 18), 0xFF8B8B8B);
        UiRenderer.rect(graphics, UiBounds.of(cx, cy, 18, 1), 0xFF373737);
        UiRenderer.rect(graphics, UiBounds.of(cx, cy, 1, 18), 0xFF373737);
        UiRenderer.rect(graphics, UiBounds.of(cx, cy + 17, 18, 1), 0xFFFFFFFF);
        UiRenderer.rect(graphics, UiBounds.of(cx + 17, cy, 1, 18), 0xFFFFFFFF);
    }

    private void drawItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mx, int my) {
        try {
            List<Component> base = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack);
            if (base == null || base.isEmpty()) return;
            List<Component> lines = base;
            if (hoveredViewHotbar >= 0) {

                lines = new ArrayList<>(base);
                lines.add(Component.literal("[U] switch to slot"));
                lines.add(Component.literal("[I] switch + use item"));
            }
            int tw = 0;
            for (Component c : lines) tw = Math.max(tw, font.width(c));
            int th = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
            int tx = mx + 12;
            int ty = my - 12;
            if (tx + tw + 4 > screenWidth()) tx = Math.max(4, mx - tw - 16);
            if (ty + th + 4 > screenHeight()) ty = screenHeight() - th - 4;
            if (ty < 4) ty = 4;
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0xF0100010);
            UiRenderer.frame(graphics, UiBounds.of(tx - 3, ty - 3, tw + 6, th + 6), 0, 0x505000A0);
            int yy = ty;
            for (Component c : lines) {
                graphics.text(font, c.getVisualOrderText(), tx, yy, 0xFFFFFFFF, true);
                yy += 10;
            }
        } catch (Throwable ignored) {

        }
    }

    private ItemStack viewStackAt(int handler) {
        if (cachedView == null) return ItemStack.EMPTY;
        for (MultiSession.ViewSlot slot : cachedView.slots()) {
            if (slot.handler() == handler) return slot.item();
        }
        return ItemStack.EMPTY;
    }

    private void handleViewClick(int handler, int button, boolean shift, boolean ctrl) {
        if (handler < 0) return;
        ItemStack stack = viewStackAt(handler);
        if (button == 1 && ctrl && shift) {
            if (stack != null && !stack.isEmpty()) openNbt(stack);
            return;
        }
        if (cachedView == null || !cachedView.interactive()) {
            resultText = cachedView != null && cachedView.synchronizationBlocked()
                ? "Inventory is waiting for a server update" : "Inventory is synchronizing";
            resultColor = MUTED;
            return;
        }
        MultiClientCommands.ClickSpec spec = MultiClientCommands.fromMouse(button, shift, ctrl);
        if (sharedView) {
            applyFanout(MultiManager.get().clickBotSlots(sharedIds(), handler, spec));
            return;
        }
        String result = MultiManager.get().clickBotSlot(viewingId, handler, spec);
        if (!"Sent".equals(result)) {
            resultText = result;
            resultColor = MUTED;
        }
    }

    private void dispatchViewWidget(MultiMenuRenderer.MenuAction action) {
        if (action instanceof MultiMenuRenderer.RenameFocusAct) {
            menuInput.rename.focus();
            menuInput.rename.set("");
            return;
        }
        if (action instanceof MultiMenuRenderer.BeaconPick p) {
            if (p.secondary()) menuInput.beaconSecondary = p.effectId();
            else menuInput.beaconPrimary = p.effectId();
            return;
        }
        if (cachedView == null || !cachedView.interactive()) {
            resultText = "Inventory is synchronizing";
            resultColor = MUTED;
            return;
        }
        MultiManager mgr = MultiManager.get();
        String type = viewTypeId();
        if (action instanceof MultiMenuRenderer.ButtonAct b) {
            if (sharedView) applyFanout(mgr.buttonClickBots(sharedIds(), b.id(), type));
            else viewActionResult(mgr.buttonClickBot(viewingId, b.id()));
        } else if (action instanceof MultiMenuRenderer.TradeAct t) {
            if (sharedView) applyFanout(mgr.selectTradeBots(sharedIds(), t.index(), type));
            else viewActionResult(mgr.selectTradeBot(viewingId, t.index()));
        } else if (action instanceof MultiMenuRenderer.BeaconAct be) {
            if (sharedView) applyFanout(mgr.setBeaconBots(sharedIds(), be.primary(), be.secondary(), type));
            else viewActionResult(mgr.setBeaconBot(viewingId, be.primary(), be.secondary()));
        } else if (action instanceof MultiMenuRenderer.RecipeStep rs) {
            menuInput.recipeIndex = Math.max(0, menuInput.recipeIndex + rs.delta());
            if (sharedView) applyFanout(mgr.buttonClickBots(sharedIds(), menuInput.recipeIndex, type));
            else viewActionResult(mgr.buttonClickBot(viewingId, menuInput.recipeIndex));
        }
    }

    private void viewActionResult(String result) {
        if (!"Sent".equals(result)) {
            resultText = result;
            resultColor = MUTED;
        }
    }

    private String viewTypeId() {
        return cachedView != null && cachedView.extras() != null ? cachedView.extras().typeId() : "";
    }

    private void sendRename() {
        if (sharedView) applyFanout(MultiManager.get().renameBotItems(sharedIds(), menuInput.rename.text(), viewTypeId()));
        else MultiManager.get().renameBotItem(viewingId, menuInput.rename.text());
    }

    private void openNbt(ItemStack stack) {
        if (minecraft == null || stack == null || stack.isEmpty()) return;
        if (AutismItemNbtInspectOverlay.openGlobal(stack)) {
            AutismItemNbtInspectOverlay overlay = AutismItemNbtInspectOverlay.getSharedOverlay(font);
            if (overlay != null) minecraft.gui.setScreen(new AutismOverlayHostScreen(overlay, this, false, true));
        }
    }

    private void renderChat(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        chatRows.clear();
        chatLeft = x;
        chatRight = x + w;
        chatTop = y;
        chatBottom = y + h;
        int gutter = 6;
        int tsWidth = font.width("[00:00:00] ");
        int x0 = x + tsWidth;
        int avail = Math.max(1, (x + w - gutter) - x0);
        chatAvail = avail;
        refreshChatCache(avail);
        List<MultiChatPresentation.VisualRow> rows = cachedVisualRows;

        List<autismclient.gui.multi.MultiChatSelection.Row> selRows = new ArrayList<>(rows.size());
        for (MultiChatPresentation.VisualRow row : rows) {
            selRows.add(new autismclient.gui.multi.MultiChatSelection.Row(row.line().seq(), row.lineIndex(),
                autismclient.gui.multi.MultiChatSelection.plain(row.hit())));
        }
        chatSel.setRows(selRows);
        int visible = Math.max(1, h / CHAT_LINE_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visible);
        chatScroll = Math.max(0, Math.min(chatScroll, maxScroll));
        int end = rows.size() - chatScroll;
        int start = Math.max(0, end - visible);
        int yy = y;
        for (int i = start; i < end; i++) {
            MultiChatPresentation.VisualRow row = rows.get(i);
            String rowText = autismclient.gui.multi.MultiChatSelection.plain(row.hit());

            int[] range = chatSel.rangeFor(row.line().seq(), row.lineIndex(), rowText.length());
            if (range != null && range[1] > range[0]) {

                int sx = x0 + autismclient.gui.multi.MultiChatSelection.widthOfStyled(font, row.hit(), range[0]);
                int ex = x0 + autismclient.gui.multi.MultiChatSelection.widthOfStyled(font, row.hit(), range[1]);
                UiRenderer.rect(graphics, UiBounds.of(sx, yy - 1, Math.max(1, ex - sx), CHAT_LINE_HEIGHT), 0x553C6EF5);
            }
            if (row.lineIndex() == 0) {
                graphics.text(font, Component.literal(timestamp(row.line().time())), x, yy, themeMuted(), false);
            }
            graphics.text(font, row.render(), x0, yy, themeText(), false);
            underlineClickableLine(graphics, row.hit(), x0, yy);
            chatRows.add(new ChatRow(row.line(), row.lineIndex(), yy, x0, row.hit(), rowText));
            yy += CHAT_LINE_HEIGHT;
        }

        chatMaxScrollRows = maxScroll;
        int lineH = CHAT_LINE_HEIGHT;
        int offsetFromTop = Math.max(0, maxScroll - chatScroll) * lineH;
        chatScrollbar = CompactScrollbar.compute(rows.size() * lineH, visible * lineH,
            x + w - 4, y, 3, Math.max(1, h), offsetFromTop);
        CompactScrollbar.draw(graphics, chatScrollbar, false, chatScrollbarDragging);
    }

    private void chatScrollbarDrag(double vy) {
        if (chatScrollbar == null) return;
        int px = CompactScrollbar.scrollFromThumb(chatScrollbar, vy, chatScrollbarGrab);
        int rowsFromTop = Math.round(px / (float) CHAT_LINE_HEIGHT);
        chatScroll = Math.max(0, Math.min(chatMaxScrollRows, chatMaxScrollRows - rowsFromTop));
    }

    private void chatSelectAt(double mx, double my, boolean begin) {
        if (chatRows.isEmpty()) return;
        ChatRow target = chatRows.getFirst();
        for (ChatRow row : chatRows) {
            if (my >= row.y()) target = row;
        }
        int ch = autismclient.gui.multi.MultiChatSelection.charIndexAtStyled(font, target.hit(), (int) Math.round(mx - target.x0()));
        if (begin) chatSel.begin(target.line().seq(), target.lineIndex(), ch);
        else chatSel.extend(target.line().seq(), target.lineIndex(), ch);
    }

    private boolean beginChatSelection(double mx, double my) {
        if (mx < chatLeft || mx > chatRight || my < chatTop || my > chatBottom || chatRows.isEmpty()) {
            chatSel.clear();
            return false;
        }
        chatSelectingPress = true;
        chatSelectAt(mx, my, true);
        return true;
    }

    private void copyChatSelection() {
        String text = chatSel.selectedText();
        if (text.isBlank() || minecraft == null || minecraft.keyboardHandler == null) return;
        minecraft.keyboardHandler.setClipboard(text);
        resultText = "Copied " + text.length() + " chars";
        resultColor = SUCCESS;
    }

    private Set<String> chatScope() {
        return scopeForView();
    }

    private Set<String> actionScope() {
        return scopeForView();
    }

    private Set<String> scopeForView() {

        if (!selectedIds.isEmpty()) return selectedIds;
        if (viewingId != null) return java.util.Set.of(viewingId);
        if (sharedView) {
            List<String> ids = sharedIds();
            if (!ids.isEmpty()) return new LinkedHashSet<>(ids);
        }
        return selectedIds;
    }

    private void refreshChatCache(int avail) {
        Set<String> scope = chatScope();
        String scopeKey = String.join(",", scope);
        long rev = MultiManager.get().chatRevision();
        int total = currentChatTotal();
        if (rev == cachedChatRevision && scopeKey.equals(cachedChatScope) && avail == cachedChatWidth && total == cachedChatTotal) {
            return;
        }
        int previousRows = cachedVisualRows.size();
        boolean preserveScrolledPosition = chatScroll > 0 && scopeKey.equals(cachedChatScope)
            && avail == cachedChatWidth && total == cachedChatTotal;
        cachedChatRevision = rev;
        cachedChatScope = scopeKey;
        cachedChatWidth = avail;
        cachedChatTotal = total;
        List<MultiChatPresentation.VisualRow> out = MultiChatPresentation.wrap(font,
            MultiManager.get().chatView(scope), avail, total, this::chatAccountLabel, themeMuted());
        cachedVisualRows = out;
        if (preserveScrolledPosition && out.size() > previousRows) chatScroll += out.size() - previousRows;
    }

    private int currentChatTotal() {
        Set<String> scope = chatScope();
        return scope.isEmpty() ? Math.max(1, frameSnapshots.size()) : scope.size();
    }

    private String chatAccountLabel(String id) {
        MultiSession.Snapshot snapshot = findSnapshot(id);
        return snapshot == null ? id : snapshot.accountName();
    }

    private void underlineClickableLine(GuiGraphicsExtractor graphics, net.minecraft.network.chat.FormattedText line, int x0, int y) {
        MultiChatPresentation.underlineClickableLine(graphics, font, line, x0, y);
    }

    private boolean handleChatClick(double mx, double my) {
        if (mx < chatLeft || mx > chatRight) return false;
        for (ChatRow row : chatRows) {
            if (my >= row.y() && my < row.y() + CHAT_LINE_HEIGHT) {
                int relativeX = (int) (mx - row.x0());
                ClickEvent kind = relativeX >= 0 ? resolveClickInLine(row.hit(), relativeX) : null;
                if (kind == null) return false;
                executeChatClick(row.line(), row.lineIndex(), relativeX, kind);
                return true;
            }
        }
        return false;
    }

    private ClickEvent resolveClickInLine(net.minecraft.network.chat.FormattedText line, int relativeX) {
        return MultiChatPresentation.resolveClick(font, line, relativeX);
    }

    private void executeChatClick(MultiManager.ChatLine line, int lineIndex, int relativeX, ClickEvent kind) {
        switch (kind) {
            case ClickEvent.RunCommand command -> {
                int ran = MultiChatPresentation.runCommands(font, line, lineIndex, relativeX, chatAvail,
                    currentChatTotal(), this::chatAccountLabel, themeMuted(), command);
                resultText = ran > 0 ? "Clicked " + ran : "Clicked";
                resultColor = SUCCESS;
            }
            case ClickEvent.SuggestCommand suggest -> {
                if (chatInput != null) chatInput.setValue(suggest.command());
            }
            case ClickEvent.CopyToClipboard copy -> {
                if (minecraft != null) minecraft.keyboardHandler.setClipboard(copy.value());
                resultText = "Copied";
                resultColor = MUTED;
            }
            case ClickEvent.OpenUrl url -> {
                MultiChatPresentation.openLinkSafely(url.uri());
                resultText = "Opening link";
                resultColor = SUCCESS;
            }
            default -> {
            }
        }
    }

    private String timestamp(long time) {
        return "[" + LocalTime.ofInstant(java.time.Instant.ofEpochMilli(time), java.time.ZoneId.systemDefault()).format(CHAT_TIME) + "] ";
    }

    private record ChatRow(MultiManager.ChatLine line, int lineIndex, int y, int x0,
                           net.minecraft.network.chat.FormattedText hit, String text) {
    }

    @Override
    public void onClose() {

        minecraft.gui.setScreen(parent);
    }

    private AutismStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        String label = fitLabel(text, w - 8);
        Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        AutismStyledButton button = new AutismStyledButton(x, y, Math.max(1, w), h,
            Component.literal(label), interactiveTone, press);
        addRenderableWidget(button);
        return button;
    }

    private String fitLabel(String text, int width) {
        return UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 64), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), themeText());
    }

    private int sessionWidth() {
        int room = screenWidth() - MARGIN * 2 - 8 - 160;
        return Math.max(120, Math.min(MAX_SESSION_WIDTH, room));
    }

    private int consoleX() {
        return MARGIN + sessionWidth() + 8;
    }

    private int consoleWidth() {
        return Math.max(1, screenWidth() - consoleX() - MARGIN);
    }

    private void clearInputFocus() {
        if (chatInput != null) chatInput.setFocused(false);
        this.setFocused(null);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, MultiManager.singleLine(text, 180), fontId, color, x, y, false);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String line = UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 160), Math.max(1, width), fontId, color);
        UiText.draw(graphics, font, line, fontId, color, x, y, false);
    }

    private static String detailLabel(MultiSession.Snapshot snapshot) {
        if (snapshot == null) return "";

        String proxy = MultiManager.singleLine(snapshot.proxyName(), 24);
        String status = statusWord(snapshot);
        return proxy.isBlank() ? status : proxy + " - " + status;
    }

    private static String statusWord(MultiSession.Snapshot snapshot) {
        return switch (snapshot.status()) {
            case QUEUED -> "Queued";
            case AUTHENTICATING -> "Auth";
            case CONNECTING -> "Connecting";
            case LOGIN -> "Login";
            case CONFIGURING -> "Configuring";
            case JOINED -> "Joining";
            case READY -> "Ready";
            case DISCONNECTED -> "Disconnected";
            case FAILED -> "Failed";
        };
    }

    private static String shortResult(MultiManager.BroadcastResult result) {
        if (result == null) return "Failed";
        if (result.failed() > 0) return "Failed";
        if (result.sent() > 0 && result.skipped() == 0) return "Sent";
        if (result.sent() > 0) return "Sent " + result.sent();
        if (result.skipped() > 0) return "Skipped";
        return "Failed";
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themePanelSoft() {
        return AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON);
    }

    private static int themeBorder() {
        return AutismTheme.recolor(BORDER, Channel.OUTLINE);
    }

    private static int themeText() {
        return AutismTheme.recolor(TEXT, Channel.TEXT);
    }

    private static int themeMuted() {
        return AutismTheme.recolor(MUTED, Channel.TEXT);
    }

    private static int themeSuccess() {
        return AutismTheme.recolor(SUCCESS, Channel.SUCCESS);
    }

    private static int themeError() {
        return AutismTheme.recolor(ERROR, Channel.DANGER);
    }

    private static int themeStatusColor(int color) {
        if (color == SUCCESS) return themeSuccess();
        if (color == ERROR) return themeError();
        if (color == TEXT) return themeText();
        return themeMuted();
    }

    private MultiSession.Snapshot findSnapshot(String accountId) {
        MultiSession.Snapshot cached = frameSnapshots.get(accountId);
        if (cached != null) return cached;
        for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (snapshot.accountId().equals(accountId)) return snapshot;
        }
        return null;
    }

    private record SessionRow(String id, int y, int guiExtra) {
    }
}
