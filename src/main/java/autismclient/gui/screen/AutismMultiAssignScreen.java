package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismTheme;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class AutismMultiAssignScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int ROW_H = 22;

    private final Screen parent;
    private final String macroName;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private int scroll;
    private String status = "";
    private int statusColor = AutismScreenPalette.MUTED;

    public AutismMultiAssignScreen(Screen parent, String macroName, java.util.Set<String> preselect) {
        super(Component.literal("Assign Macro"));
        this.parent = parent;
        this.macroName = macroName == null ? "" : macroName;
        if (preselect != null) selected.addAll(preselect);
    }

    private record Account(String id, String name, String macro) {}

    private List<Account> accounts() {
        List<Account> out = new ArrayList<>();
        for (MultiSession.Snapshot s : MultiManager.get().snapshots()) {
            String m = MultiManager.get().effectiveMacroName(s.accountId());
            out.add(new Account(s.accountId(), s.accountName(), m == null ? "" : m));
        }
        return out;
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = panelW();
        int x = (screenWidth() - w) / 2;
        int gap = 6;
        int third = Math.max(1, (w - gap * 2) / 3);
        int toolY = 40;
        addButton(x, toolY, third, "Select All", Button.Tone.SECONDARY, b -> {
            selected.clear();
            for (Account a : accounts()) selected.add(a.id());
        });
        addButton(x + third + gap, toolY, third, "Select None", Button.Tone.SECONDARY, b -> selected.clear());
        addButton(x + (third + gap) * 2, toolY, w - (third + gap) * 2, "Only Without Macro", Button.Tone.SECONDARY, b -> {
            selected.clear();
            for (Account a : accounts()) if (a.macro().isBlank()) selected.add(a.id());
        });
        int half = Math.max(1, (w - gap) / 2);
        addButton(x, screenHeight() - 28, half, "Assign to Selected", Button.Tone.SUCCESS, b -> apply());
        addButton(x + half + gap, screenHeight() - 28, w - half - gap, "Back", Button.Tone.SECONDARY, b -> onClose());
    }

    private void apply() {
        if (selected.isEmpty()) { status("Tick at least one account.", AutismScreenPalette.ERROR); return; }
        int changed = MultiManager.get().assignMacroOnScope(new LinkedHashSet<>(selected), macroName);
        status("Assigned \"" + macroName + "\" to " + changed + " account(s).", AutismScreenPalette.SUCCESS);
    }

    private void status(String text, int color) {
        status = text == null ? "" : text;
        statusColor = color;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() == 0) {
            List<Account> list = accounts();
            for (int[] rect : rowRects) {
                if (virtualEvent.x() >= rect[0] && virtualEvent.x() < rect[0] + rect[2]
                    && virtualEvent.y() >= rect[1] && virtualEvent.y() < rect[1] + rect[3] && rect[4] < list.size()) {
                    String id = list.get(rect[4]).id();
                    if (!selected.remove(id)) selected.add(id);
                    return true;
                }
            }
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll + (vertical < 0 ? 1 : -1));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()),
                AutismTheme.recolor(AutismScreenPalette.BG, AutismTheme.Channel.BACKDROP));
            int w = panelW();
            int x = (screenWidth() - w) / 2;
            int border = AutismTheme.recolor(AutismScreenPalette.BORDER, AutismTheme.Channel.OUTLINE);
            int textColor = AutismTheme.recolor(AutismScreenPalette.TEXT, AutismTheme.Channel.TEXT);
            int muted = AutismTheme.recolor(AutismScreenPalette.MUTED, AutismTheme.Channel.TEXT);
            UiRenderer.frame(graphics, UiBounds.of(x - 6, 14, w + 12, Math.max(1, screenHeight() - 42)),
                AutismTheme.recolor(AutismScreenPalette.PANEL_BG, AutismTheme.Channel.BUTTON), border);
            Identifier fontId = THEME.fontFor(UiTone.BODY);
            UiText.draw(graphics, font, "Assign \"" + MultiManager.singleLine(macroName, 40) + "\" to accounts:",
                fontId, textColor, x, 26, false);

            rowRects.clear();
            List<Account> list = accounts();
            int top = 62;
            int bottom = screenHeight() - 34;
            int visible = Math.max(1, (bottom - top) / ROW_H);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
            int success = AutismTheme.recolor(AutismScreenPalette.SUCCESS, AutismTheme.Channel.SUCCESS);
            int y = top;
            for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
                Account a = list.get(i);
                boolean sel = selected.contains(a.id());
                UiRenderer.rect(graphics, UiBounds.of(x, y, w, ROW_H - 3),
                    sel ? (success & 0x00FFFFFF) | 0x33000000 : 0x18000000);
                if (sel) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, ROW_H - 3), success);
                String box = sel ? "[x] " : "[ ] ";
                UiText.draw(graphics, font, box + MultiManager.singleLine(a.name(), 40), fontId,
                    sel ? success : textColor, x + 6, y + 2, false);
                String cur = a.macro().isBlank() ? "no macro" : "-> " + a.macro();
                UiText.draw(graphics, font, cur, fontId, a.macro().isBlank() ? muted : textColor, x + 6, y + 11, false);
                rowRects.add(new int[]{x, y, w, ROW_H - 3, i});
                y += ROW_H;
            }
            if (!status.isBlank()) UiText.draw(graphics, font, status, fontId, statusColor, x, screenHeight() - 40, false);
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private int panelW() {
        return Math.max(200, Math.min(360, screenWidth() - 60));
    }

    private void addButton(int x, int y, int w, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new AutismStyledButton(x, y, Math.max(1, w), 18, Component.literal(label), tone, press));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
