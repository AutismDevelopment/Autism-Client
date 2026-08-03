package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismPacketSelectorOverlay;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiManualPackets;
import autismclient.util.multi.MultiPacketPolicy;
import autismclient.util.multi.MultiManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiPacketPolicyScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 236;

    private final Screen parent;
    private final Consumer<MultiPacketPolicy> save;
    private final boolean live;
    private final List<ToggleRow> toggleRows = new ArrayList<>();
    private MultiPacketPolicy policy;
    private autismclient.util.multi.MultiAutoAccept autoAccept;
    private final Consumer<autismclient.util.multi.MultiAutoAccept> saveAutoAccept;
    private AutismPacketSelectorOverlay selector;

    public AutismMultiPacketPolicyScreen(Screen parent, MultiPacketPolicy policy, Consumer<MultiPacketPolicy> save,
                                         autismclient.util.multi.MultiAutoAccept autoAccept,
                                         Consumer<autismclient.util.multi.MultiAutoAccept> saveAutoAccept, boolean live) {
        super(Component.literal("Multi Packet Policy"));
        this.parent = parent;
        this.policy = new MultiPacketPolicy(policy);
        this.save = save;
        this.autoAccept = new autismclient.util.multi.MultiAutoAccept(autoAccept);
        this.saveAutoAccept = saveAutoAccept;
        this.live = live;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        toggleRows.clear();
        int x = panelX();
        int y = 46;
        addToggle(x, y, "Gravity", "Bot falls to and rests on the ground. Off: it holds where the server puts it.",
            policy.gravity(), policy::setGravity);
        y += 30;
        addToggle(x, y, "Auto look", "Send a look packet every second (anti-AFK).",
            policy.autoLook(), policy::setAutoLook);
        y += 30;
        addToggle(x, y, "Auto swing", "Swing the arm every second (anti-AFK).",
            policy.autoSwing(), policy::setAutoSwing);
        y += 36;
        int inner = panelWidth() - 28;
        int gap = 4;
        int each = Math.max(1, (inner - gap * 2) / 3);
        addStyled(x + 14, y, each, 20, "Block C2S", Button.Tone.DANGER,
            button -> openBlocklist(MultiPacketPolicy.Direction.C2S));
        addStyled(x + 14 + each + gap, y, each, 20, "Block S2C", Button.Tone.DANGER,
            button -> openBlocklist(MultiPacketPolicy.Direction.S2C));
        addStyled(x + 14 + (each + gap) * 2, y, inner - (each + gap) * 2, 20, "Clear", Button.Tone.NORMAL,
            button -> {
                policy.setBlocklist(List.of());
                applyLive();
                rebuild();
            });
        y += 28;

        addStyled(x + 14, y, inner, 20, "Auto-Accept: TPA & Trade...", Button.Tone.PRIMARY, button ->
            minecraft.gui.setScreen(new AutismMultiAutoAcceptScreen(this, autoAccept,
                cfg -> {
                    autoAccept = new autismclient.util.multi.MultiAutoAccept(cfg);
                    if (saveAutoAccept != null) saveAutoAccept.accept(cfg);
                }, live)));

        int botY = 18 + panelHeight() - 28;
        boolean tips = autismclient.util.AutismConfig.getGlobal().multiShowTooltips;
        addStyled(x + 14, botY, 118, 20, tips ? "Hover tips: On" : "Hover tips: Off",
            tips ? Button.Tone.SUCCESS : Button.Tone.NORMAL, button -> {
                autismclient.util.AutismConfig config = autismclient.util.AutismConfig.getGlobal();
                config.multiShowTooltips = !config.multiShowTooltips;
                config.save();
                rebuild();
            });
        addStyled(x + panelWidth() - 14 - 96, 18 + panelHeight() - 28, 96, 20, "Done", Button.Tone.PRIMARY,
            button -> finish());
    }

    private void addToggle(int x, int y, String label, String description, boolean on, Consumer<Boolean> setter) {
        toggleRows.add(new ToggleRow(label, description, y));
        addStyled(x + panelWidth() - 14 - 60, y + 3, 60, 18, on ? "On" : "Off",
            on ? Button.Tone.SUCCESS : Button.Tone.NORMAL,
            button -> {
                setter.accept(!on);
                applyLive();
                rebuild();
            });
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

    private void openBlocklist(MultiPacketPolicy.Direction direction) {
        ensureSelector();
        Set<Class<? extends Packet<?>>> selected = new LinkedHashSet<>();
        for (MultiPacketPolicy.Rule rule : policy.blocklist()) {
            if (rule.direction() != direction) continue;
            Class<? extends Packet<?>> packet = AutismPacketRegistry.getPacket(rule.packetClass());
            if (packet != null) selected.add(packet);
        }
        java.util.function.BiConsumer<Class<? extends Packet<?>>, Boolean> callback = (packetClass, enabled) -> {
            java.util.List<MultiPacketPolicy.Rule> rules = new java.util.ArrayList<>(policy.blocklist());
            rules.removeIf(rule -> rule.direction() == direction && rule.packetClass().equals(packetClass.getName()));
            if (enabled) rules.add(new MultiPacketPolicy.Rule(direction, packetClass.getName()));
            policy.setBlocklist(rules);
            applyLive();
        };
        if (direction == MultiPacketPolicy.Direction.S2C) selector.openToggleS2C(callback, selected);
        else selector.openToggleC2S(callback, selected, MultiManualPackets.unsafeC2S());
    }

    private void ensureSelector() {
        if (selector == null) selector = new AutismPacketSelectorOverlay(font);
    }

    private void applyLive() {
        if (live && save != null) save.accept(new MultiPacketPolicy(policy));
    }

    private void finish() {

        if (!live && save != null) save.accept(new MultiPacketPolicy(policy));
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
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
            drawFitted(graphics, "Advanced - gravity & blocklist", x + 14, 26, panelW - 28, text);
            for (ToggleRow row : toggleRows) {
                drawFitted(graphics, row.label(), x + 14, row.y() + 2, panelW - 94, text);
                drawFitted(graphics, row.description(), x + 14, row.y() + 13, panelW - 28, muted);
            }
            drawFitted(graphics, "Empty blocklist = everything allowed.", x + 14, 18 + panelH - 44, panelW - 28, muted);
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
            if (selector != null && selector.isVisible()) selector.render(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private record ToggleRow(String label, String description, int y) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseClicked((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button());
            return true;
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseReleased((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button());
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseDragged((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button(), (float) AutismUiScale.toVirtual(dragX), (float) AutismUiScale.toVirtual(dragY));
            return true;
        }
        return super.mouseDragged(virtualEvent, AutismUiScale.toVirtual(dragX), AutismUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double vx = AutismUiScale.toVirtual(mouseX);
        double vy = AutismUiScale.toVirtual(mouseY);
        if (selector != null && selector.isVisible()) {
            selector.mouseScrolled((float) vx, (float) vy, vertical);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selector != null && selector.isVisible()) {
            selector.keyPressed(event.key(), event.scancode(), event.modifiers());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (selector != null && selector.isVisible()) {
            selector.charTyped((char) event.codepoint(), 0);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        finish();
    }

    private AutismStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        String label = UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 64), Math.max(1, w - 8),
            THEME.fontFor(UiTone.BODY), AutismTheme.recolor(TEXT, Channel.TEXT));
        Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        AutismStyledButton button = new AutismStyledButton(x, y, w, h, Component.literal(label), interactiveTone, press);
        addRenderableWidget(button);
        return button;
    }

    private static String shortPacket(String className) {
        Class<? extends Packet<?>> packet = AutismPacketRegistry.getPacket(className);
        if (packet != null) return AutismPacketNamer.getFriendlyName(packet);
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        String safe = UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 96), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), color);
        graphics.text(font, Component.literal(safe), x, y, color, false);
    }
}
