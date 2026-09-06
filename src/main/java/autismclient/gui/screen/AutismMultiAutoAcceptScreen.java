package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiAutoAccept;
import autismclient.util.multi.MultiManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiAutoAcceptScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MASK = 0xFF120B0F;

    private static final int PANEL_W = 474;
    private static final int PANEL_H = 300;
    private static final int PAD = 12;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 40;
    private static final int CARD_GAP = 8;
    private static final int CARD_PAD = 8;
    private static final int ROW_H = 22;
    private static final int HEADER_ROW_H = 22;
    private static final int FIELD_H = 18;
    private static final int LABEL_W = 92;
    private static final int TOGGLE_W = 48;
    private static final int SCROLLBAR_W = 4;

    private static final int TPA_H = HEADER_ROW_H + 5 * ROW_H + CARD_PAD;
    private static final int TRADE_H = HEADER_ROW_H + 4 * ROW_H + CARD_PAD;
    private static final int CUSTOM_H = HEADER_ROW_H + 3 * ROW_H + CARD_PAD;
    private static final int ADD_H = 26;

    private final Screen parent;
    private final Consumer<MultiAutoAccept> onSave;
    private final boolean live;
    private final MultiAutoAccept config;

    private final List<Draft> drafts = new ArrayList<>();

    private final List<Runnable> captureOps = new ArrayList<>();
    private final List<UiBounds> cardFrames = new ArrayList<>();
    private final List<TextDraw> texts = new ArrayList<>();

    private int scroll;
    private int maxScroll;
    private int vpTop, vpBottom, vpLeft, vpRight;
    private boolean scrollable;
    private int doneX, doneY, doneW, doneH;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;

    private static final class Draft {
        String trigger = "";
        String response = "";
        boolean useMacro = false;
        String macroName = "";
        int delayMs = 300;
    }

    private record TextDraw(String text, int x, int y, boolean muted, int width) {
    }

    public AutismMultiAutoAcceptScreen(Screen parent, MultiAutoAccept config, Consumer<MultiAutoAccept> onSave, boolean live) {
        super(Component.literal("Auto-Accept"));
        this.parent = parent;
        this.onSave = onSave;
        this.live = live;
        this.config = new MultiAutoAccept(config);
        for (MultiAutoAccept.Responder responder : this.config.responders) {
            Draft draft = new Draft();
            draft.trigger = responder.trigger();
            draft.response = responder.response();
            draft.useMacro = responder.useMacro();
            draft.macroName = responder.macroName();
            draft.delayMs = responder.delayMs();
            drafts.add(draft);
        }
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        captureOps.clear();
        cardFrames.clear();
        texts.clear();

        vpLeft = panelX() + PAD;
        vpRight = panelX() + panelWidth() - PAD;
        vpTop = 18 + HEADER_H;
        vpBottom = 18 + panelHeight() - FOOTER_H;
        int viewportH = Math.max(1, vpBottom - vpTop);

        List<Integer> heights = new ArrayList<>();
        heights.add(TPA_H);
        heights.add(TRADE_H);
        for (int i = 0; i < drafts.size(); i++) heights.add(CUSTOM_H);
        heights.add(ADD_H);
        int contentH = 0;
        for (int h : heights) contentH += h;
        contentH += CARD_GAP * Math.max(0, heights.size() - 1);

        scrollable = contentH > viewportH;
        maxScroll = Math.max(0, contentH - viewportH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int cardX = vpLeft;
        int cardW = vpRight - vpLeft - (scrollable ? SCROLLBAR_W + 4 : 0);

        List<Section> sections = new ArrayList<>();
        sections.add(new Section(TPA_H, y -> layoutTpa(cardX, y, cardW)));
        sections.add(new Section(TRADE_H, y -> layoutTrade(cardX, y, cardW)));
        for (int i = 0; i < drafts.size(); i++) {
            int index = i;
            sections.add(new Section(CUSTOM_H, y -> layoutCustom(cardX, y, cardW, index)));
        }
        sections.add(new Section(ADD_H, y -> layoutAdd(cardX, y, cardW)));

        int y = vpTop - scroll;
        for (Section section : sections) {
            if (y + section.height() > vpTop && y < vpBottom) section.layout().accept(y);
            y += section.height() + CARD_GAP;
        }

        doneW = 96;
        doneH = 20;
        doneX = panelX() + panelWidth() - PAD - doneW;
        doneY = 18 + panelHeight() - 28;
    }

    private record Section(int height, IntConsumer layout) {
    }

    private void layoutTpa(int cardX, int top, int cardW) {
        frame(cardX, top, cardW, TPA_H, "TPA");
        enableToggle(cardX, top, cardW, config.tpaEnabled, () -> config.tpaEnabled = !config.tpaEnabled);
        int innerX = cardX + CARD_PAD;
        int innerW = cardW - 2 * CARD_PAD;
        int ry = top + HEADER_ROW_H;
        acceptRow(innerX, ry, innerW, config.tpaUseMacro, config.tpaAcceptCommand, config.tpaMacroName,
            "/tpaccept {name}", v -> config.tpaAcceptCommand = v,
            () -> { config.tpaUseMacro = !config.tpaUseMacro; },
            () -> pickMacro(config.tpaMacroName, n -> config.tpaMacroName = n));
        ry += ROW_H;
        label("Arm window ms", innerX, ry);
        intField(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "15000", config.tpaArmWindowMs, v -> config.tpaArmWindowMs = v);
        ry += ROW_H;
        label("Accept delay ms", innerX, ry);
        intField(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "300", config.tpaAcceptDelayMs, v -> config.tpaAcceptDelayMs = v);
        ry += ROW_H;
        label("TPA to me", innerX, ry);
        field(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "/tpahere {bot}", config.tpaToMeCommand, v -> config.tpaToMeCommand = v);
        ry += ROW_H;
        label("TPA to bot", innerX, ry);
        field(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "/tpa {bot}", config.tpaToBotCommand, v -> config.tpaToBotCommand = v);
    }

    private void layoutTrade(int cardX, int top, int cardW) {
        frame(cardX, top, cardW, TRADE_H, "Trade");
        enableToggle(cardX, top, cardW, config.tradeEnabled, () -> config.tradeEnabled = !config.tradeEnabled);
        int innerX = cardX + CARD_PAD;
        int innerW = cardW - 2 * CARD_PAD;
        int ry = top + HEADER_ROW_H;
        acceptRow(innerX, ry, innerW, config.tradeUseMacro, config.tradeAcceptCommand, config.tradeMacroName,
            "/trade accept", v -> config.tradeAcceptCommand = v,
            () -> { config.tradeUseMacro = !config.tradeUseMacro; },
            () -> pickMacro(config.tradeMacroName, n -> config.tradeMacroName = n));
        ry += ROW_H;
        label("Arm window ms", innerX, ry);
        intField(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "15000", config.tradeArmWindowMs, v -> config.tradeArmWindowMs = v);
        ry += ROW_H;
        label("Accept delay ms", innerX, ry);
        intField(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "300", config.tradeAcceptDelayMs, v -> config.tradeAcceptDelayMs = v);
        ry += ROW_H;
        label("Trade command", innerX, ry);
        field(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "/trade {bot}", config.tradeCommand, v -> config.tradeCommand = v);
    }

    private void layoutCustom(int cardX, int top, int cardW, int index) {
        Draft draft = drafts.get(index);
        frame(cardX, top, cardW, CUSTOM_H, "Custom " + (index + 1));
        addStyled(cardX + cardW - CARD_PAD - 64, top + 2, 64, 18, "Remove", Button.Tone.NORMAL,
            b -> { capture(); if (index < drafts.size()) drafts.remove(index); applyLive(); rebuild(); });
        int innerX = cardX + CARD_PAD;
        int innerW = cardW - 2 * CARD_PAD;
        int ry = top + HEADER_ROW_H;
        label("Trigger text", innerX, ry);
        field(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "text to watch for", draft.trigger, v -> draft.trigger = v);
        ry += ROW_H;
        acceptRow(innerX, ry, innerW, draft.useMacro, draft.response, draft.macroName,
            "/reply", v -> draft.response = v,
            () -> { draft.useMacro = !draft.useMacro; },
            () -> pickMacro(draft.macroName, n -> draft.macroName = n));
        ry += ROW_H;
        label("Delay ms", innerX, ry);
        intField(innerX + LABEL_W + 4, ry, innerW - LABEL_W - 4, "300", draft.delayMs, v -> draft.delayMs = v);
    }

    private void layoutAdd(int cardX, int top, int cardW) {
        if (drafts.size() >= MultiAutoAccept.MAX_RESPONDERS) {
            texts.add(new TextDraw("Maximum of " + MultiAutoAccept.MAX_RESPONDERS + " custom responders reached.",
                cardX, top + 6, true, cardW));
            return;
        }
        addStyled(cardX, top, cardW, 20, "+ Add custom responder", Button.Tone.SECONDARY, b -> {
            capture();
            if (drafts.size() < MultiAutoAccept.MAX_RESPONDERS) drafts.add(new Draft());
            applyLive();
            rebuild();
        });
    }

    private void acceptRow(int innerX, int y, int innerW, boolean useMacro, String textValue, String macroName,
                           String hint, Consumer<String> textSink, Runnable toggleMode, Runnable pick) {
        label("Accept", innerX, y);
        int fieldX = innerX + LABEL_W + 4;
        int fieldW = innerW - LABEL_W - 4 - TOGGLE_W - 4;
        if (useMacro) {
            addStyled(fieldX, y, fieldW, FIELD_H, "Macro: " + orNone(macroName), Button.Tone.NORMAL, b -> pick.run());
        } else {
            field(fieldX, y, fieldW, hint, textValue, textSink);
        }
        addStyled(fieldX + fieldW + 4, y, TOGGLE_W, FIELD_H, useMacro ? "Macro" : "Text",
            useMacro ? Button.Tone.PRIMARY : Button.Tone.NORMAL,
            b -> { capture(); toggleMode.run(); applyLive(); rebuild(); });
    }

    private void enableToggle(int cardX, int top, int cardW, boolean on, Runnable toggle) {
        addStyled(cardX + cardW - CARD_PAD - 46, top + 2, 46, 18, on ? "On" : "Off",
            on ? Button.Tone.SUCCESS : Button.Tone.NORMAL,
            b -> { capture(); toggle.run(); applyLive(); rebuild(); });
    }

    private void frame(int x, int y, int w, int h, String title) {
        cardFrames.add(UiBounds.of(x, y, w, h));
        texts.add(new TextDraw(title, x + CARD_PAD, y + 7, false, w - 2 * CARD_PAD - 50));
    }

    private void label(String text, int x, int y) {
        texts.add(new TextDraw(text, x, y + 5, true, LABEL_W));
    }

    private void field(int x, int y, int w, String hint, String value, Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, Math.max(1, w), FIELD_H, Component.literal(hint));
        box.setMaxLength(96);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        captureOps.add(() -> sink.accept(box.getValue()));
    }

    private void intField(int x, int y, int w, String hint, int value, IntConsumer sink) {
        EditBox box = new EditBox(font, x, y, Math.max(1, w), FIELD_H, Component.literal(hint));
        box.setMaxLength(8);
        box.setValue(Integer.toString(value));
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        captureOps.add(() -> {
            try {
                sink.accept(Integer.parseInt(box.getValue().trim()));
            } catch (RuntimeException ignored) {  }
        });
    }

    private AutismStyledButton addStyled(int x, int y, int w, int h, String label, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        Button.Tone interactive = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        String fit = UiText.trimToWidthEllipsis(font, label, Math.max(1, w - 6), THEME.fontFor(UiTone.BODY),
            AutismTheme.recolor(TEXT, Channel.TEXT));
        AutismStyledButton button = new AutismStyledButton(x, y, Math.max(1, w), h, Component.literal(fit), interactive, press);
        addRenderableWidget(button);
        return button;
    }

    private void capture() {
        for (Runnable op : captureOps) op.run();
        config.responders.clear();
        for (Draft draft : drafts) {
            config.responders.add(new MultiAutoAccept.Responder(draft.trigger, draft.response, draft.useMacro,
                draft.macroName, draft.delayMs));
        }
        config.normalize();
    }

    private void pickMacro(String current, Consumer<String> setter) {
        capture();
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, current, name -> {
            setter.accept(name == null ? "" : name);
            applyLive();
        }));
    }

    private void applyLive() {
        if (live && onSave != null) onSave.accept(new MultiAutoAccept(config));
    }

    private void finish() {
        capture();
        if (onSave != null) onSave.accept(new MultiAutoAccept(config));
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        finish();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int vmx = AutismUiScale.toVirtualInt(mouseX);
        int vmy = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), AutismTheme.recolor(BG, Channel.BACKDROP));
            int x = panelX();
            int panelW = panelWidth();
            int panelH = panelHeight();
            int text = AutismTheme.recolor(TEXT, Channel.TEXT);
            int muted = AutismTheme.recolor(MUTED, Channel.TEXT);
            UiRenderer.frame(graphics, UiBounds.of(x, 18, panelW, panelH), AutismTheme.recolor(PANEL_BG, Channel.BUTTON),
                AutismTheme.recolor(BORDER, Channel.OUTLINE));
            drawText(graphics, "Auto-Accept - TPA & Trade", x + 14, 26, text, panelW - 28);

            AutismUiScale.enableOverlayScissor(graphics, vpLeft - 2, vpTop, vpRight + SCROLLBAR_W + 2, vpBottom);
            int cardFill = AutismTheme.recolor(BG, Channel.BUTTON);
            int cardBorder = AutismTheme.recolor(BORDER, Channel.OUTLINE);
            for (UiBounds card : cardFrames) UiRenderer.frame(graphics, card, cardFill, cardBorder);
            for (TextDraw td : texts) drawText(graphics, td.text(), td.x(), td.y(), td.muted() ? muted : text, td.width());
            super.extractRenderState(graphics, vmx, vmy, delta);
            graphics.disableScissor();

            CompactScrollbar.Metrics metrics = scrollbarMetrics();
            CompactScrollbar.draw(graphics, metrics, metrics.contains(vmx, vmy), scrollbarDragging);

            int tipX = x + 14;
            int tipW = Math.max(1, doneX - tipX - 8);
            List<String> tipLines = wrapToWidth(
                "{bot} = bot name. {name}/{me} = your on-server name. Bots watch chat for these.", tipW, muted);
            int tipY = tipLines.size() > 1 ? doneY + 1 : doneY + 6;
            for (int i = 0; i < tipLines.size(); i++) {
                drawText(graphics, tipLines.get(i), tipX, tipY + i * 10, muted, tipW);
            }
            UiBounds doneBounds = UiBounds.of(doneX, doneY, doneW, doneH);
            UiRenderer.rect(graphics, doneBounds, AutismTheme.recolor(MASK, Channel.BUTTON));
            boolean doneHover = vmx >= doneX && vmx < doneX + doneW && vmy >= doneY && vmy < doneY + doneH;
            Button.render(UiContexts.overlay(graphics, font, vmx, vmy), doneBounds, "Done", Button.Tone.PRIMARY, doneHover, false);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, String value, int x, int y, int color, int width) {
        String shown = UiText.trimToWidthEllipsis(font, MultiManager.singleLine(value, 96), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), color);
        graphics.text(font, Component.literal(shown), x, y, color, false);
    }

    private List<String> wrapToWidth(String value, int width, int color) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && UiText.width(font, candidate, THEME.fontFor(UiTone.BODY), color) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        while (lines.size() > 2) {
            String tail = lines.remove(2);
            lines.set(1, lines.get(1) + " " + tail);
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtual = virtualEvent(event);
        int vmx = (int) virtual.x();
        int vmy = (int) virtual.y();
        if (vmx >= doneX && vmx < doneX + doneW && vmy >= doneY && vmy < doneY + doneH) {
            finish();
            return true;
        }
        CompactScrollbar.Metrics metrics = scrollbarMetrics();
        if (metrics.hasScroll() && metrics.contains(vmx, vmy)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.overThumb(vmx, vmy) ? vmy - metrics.thumbY() : metrics.thumbHeight() / 2;
            setScroll(CompactScrollbar.scrollFromThumb(metrics, vmy, scrollbarGrabOffset));
            return true;
        }

        if (vmy >= vpTop && vmy < vpBottom) return super.mouseClicked(virtual, doubled);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollbarDragging = false;
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (scrollbarDragging) {
            setScroll(CompactScrollbar.scrollFromThumb(scrollbarMetrics(), virtual.y(), scrollbarGrabOffset));
            return true;
        }
        return super.mouseDragged(virtual, AutismUiScale.toVirtual(dragX), AutismUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int vmx = AutismUiScale.toVirtualInt(mouseX);
        int vmy = AutismUiScale.toVirtualInt(mouseY);
        if (vmx >= panelX() && vmx < panelX() + panelWidth() && vmy >= vpTop && vmy < vpBottom && maxScroll > 0) {
            capture();
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * ROW_H)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        int viewportH = Math.max(1, vpBottom - vpTop);
        return CompactScrollbar.compute(viewportH + maxScroll, viewportH, vpRight + 2, vpTop, SCROLLBAR_W, viewportH, scroll);
    }

    private void setScroll(int value) {
        capture();
        scroll = Math.max(0, Math.min(maxScroll, value));
        rebuild();
    }

    private int panelX() {
        return Math.max(1, (screenWidth() - panelWidth()) / 2);
    }

    private int panelWidth() {
        return Math.max(1, Math.min(PANEL_W, screenWidth() - 36));
    }

    private int panelHeight() {
        return Math.max(1, Math.min(PANEL_H, screenHeight() - 36));
    }

    private static String orNone(String name) {
        return name == null || name.isBlank() ? "none" : name;
    }
}
