package autismclient.compat;

import autismclient.gui.screen.AutismAddonsScreen;
import autismclient.gui.screen.AutismThemeColorScreen;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.Dropdown;
import autismclient.gui.vanillaui.components.Scrollbar;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.modules.AutismModule;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismConfig;
import autismclient.util.AutismLinks;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class AutismModMenuConfigScreen extends Screen {
    private static final int TEXT_COLOR = 0xFFF3ECE7;
    private static final int MUTED_COLOR = 0xFFB79E9E;
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 3;
    private static final int ROW_STEP = ROW_H + ROW_GAP;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 30;

    private final Screen parent;
    private final List<Keybind> keybinds = new ArrayList<>();
    private int capturing = -1;
    private int scroll;
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;

    private final List<Hit> hits = new ArrayList<>();
    private UiBounds contentViewport = UiBounds.of(0, 0, 0, 0);
    private int contentHeight;
    private UiContext ctx;
    private Dropdown prefixDropdown;

    private record Keybind(String label, IntSupplier getter, IntConsumer setter) {}
    private record Hit(UiBounds bounds, Runnable onLeft, Runnable onRight) {}

    public AutismModMenuConfigScreen(Screen parent) {
        super(Component.literal("AUTISM Client Settings"));
        this.parent = parent;
        AutismConfig cfg = AutismConfig.getGlobal();
        keybinds.add(new Keybind("Module Menu", () -> cfg.keybindModuleMenu, v -> { cfg.keybindModuleMenu = v; cfg.save(); }));
        keybinds.add(new Keybind("Load GUI", () -> cfg.keybindLoadGui, v -> { cfg.keybindLoadGui = v; cfg.save(); }));
        keybinds.add(new Keybind("Flush Queue", () -> cfg.keybindFlushQueue, v -> { cfg.keybindFlushQueue = v; cfg.save(); }));
        keybinds.add(new Keybind("Clear Queue", () -> cfg.keybindClearQueue, v -> { cfg.keybindClearQueue = v; cfg.save(); }));
        keybinds.add(new Keybind("Toggle Logger", () -> cfg.keybindToggleLogger, v -> { cfg.keybindToggleLogger = v; cfg.save(); }));
        keybinds.add(new Keybind("Toggle Send", () -> cfg.keybindToggleSend, v -> { cfg.keybindToggleSend = v; cfg.save(); }));
        keybinds.add(new Keybind("Toggle Delay", () -> cfg.keybindToggleDelay, v -> { cfg.keybindToggleDelay = v; cfg.save(); }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            hits.clear();
            ctx = UiContexts.overlay(graphics, font, mx, my);
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0xF0090709);

            int panelW = Math.min(320, sw - 20);
            int panelH = Math.min(sh - 20, HEADER_H + FOOTER_H + rows().size() * ROW_STEP + 8);
            panelH = Math.max(panelH, HEADER_H + FOOTER_H + ROW_STEP);
            int px = (sw - panelW) / 2;
            int py = (sh - panelH) / 2;
            UiBounds panel = UiBounds.of(px, py, panelW, panelH);
            UiRenderer.frame(graphics, panel, 0xE60A0A0C, border());

            UiBounds header = UiBounds.of(px, py, panelW, HEADER_H);
            UiRenderer.rect(graphics, UiBounds.of(px + 1, py + 1, panelW - 2, HEADER_H - 1), p(0x33241A1D, Channel.HEADER));
            drawCentered(graphics, "AUTISM Client Settings", UiAssets.FONT_TITLE, p(TEXT_COLOR, Channel.TEXT), header);

            UiBounds footer = UiBounds.of(px, panel.bottom() - FOOTER_H, panelW, FOOTER_H);
            UiBounds done = UiBounds.of(px + 8, footer.y() + 6, panelW - 16, ROW_H);
            button(graphics, done, "Done", done.contains(mx, my), true);
            addHit(done, this::onClose, null);

            int contentTop = py + HEADER_H + 4;
            int contentBottom = footer.y() - 4;
            contentViewport = UiBounds.of(px + 4, contentTop, panelW - 8, Math.max(0, contentBottom - contentTop));
            List<Row> rows = rows();
            contentHeight = rows.size() * ROW_STEP;
            scroll = clampScroll(scroll);

            boolean needsBar = contentHeight > contentViewport.height();
            int rowW = contentViewport.width() - (needsBar ? 6 : 0);

            UiScissorStack.global().push(graphics, contentViewport);
            try {
                for (int i = 0; i < rows.size(); i++) {
                    int ry = contentViewport.y() - scroll + i * ROW_STEP;
                    if (ry + ROW_H < contentViewport.y() || ry > contentViewport.bottom()) continue;
                    UiBounds rb = UiBounds.of(contentViewport.x(), ry, rowW, ROW_H);
                    boolean visible = ry >= contentViewport.y() - 1 && ry + ROW_H <= contentViewport.bottom() + 1;
                    rows.get(i).render(graphics, rb, mx, my, visible);
                }
            } finally {
                UiScissorStack.global().pop(graphics);
            }

            if (needsBar) {

                Scrollbar.Metrics metrics = scrollbarMetrics();
                Scrollbar.render(ctx, metrics, metrics.overTrack(mx, my), scrollbarDragging);
                addHit(metrics.track(), () -> startScrollbarDrag(metrics, my), null);
            }

            if (prefixDropdown != null && prefixDropdown.isOpen()) prefixDropdown.render(ctx);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private interface Row {
        void render(GuiGraphicsExtractor g, UiBounds rb, int mx, int my, boolean clickable);
    }

    private List<Row> rows() {
        AutismConfig cfg = AutismConfig.getGlobal();
        AutismModule module = AutismModule.get();
        List<Row> out = new ArrayList<>();

        out.add(toggleRow("Open Inside GUI", () -> cfg.keybindInsideGui, v -> { cfg.keybindInsideGui = v; cfg.save(); }));
        out.add(toggleRow("Custom Main Menu", () -> cfg.customMainMenu, v -> { cfg.customMainMenu = v; cfg.save(); }));
        out.add(toggleRow("Auto Probe Plugins", () -> cfg.autoProbePlugins, v -> { cfg.autoProbePlugins = v; cfg.save(); }));
        out.add(toggleRow("InfiniChat", () -> cfg.infiniChat, v -> { cfg.infiniChat = v; cfg.save(); }));
        out.add(toggleRow("Stop On Leave", () -> cfg.stopMacroOnLeave, v -> { cfg.stopMacroOnLeave = v; cfg.save(); }));

        out.add(cycleRow("Overlay Scale", AutismUiScale.getOverlayScaleLabel(),
            () -> AutismUiScale.setOverlayScaleMultiplier(AutismUiScale.nextOverlayScaleMultiplier()),
            () -> AutismUiScale.setOverlayScaleMultiplier(AutismUiScale.previousOverlayScaleMultiplier())));

        List<String> prefixes = new ArrayList<>(AutismCompatManager.COMMAND_PREFIX_CHOICES);
        if (AutismCompatManager.isMeteorAvailable()) prefixes.remove(".");
        String cur = AutismCompatManager.effectiveCommandPrefix();
        if (!prefixes.contains(cur)) cur = prefixes.isEmpty() ? "%" : prefixes.get(0);
        out.add(prefixRow(module, prefixes, cur));

        for (int i = 0; i < keybinds.size(); i++) out.add(keybindRow(i));

        out.add(actionRow("Theme Color", () -> this.minecraft.gui.setScreen(new AutismThemeColorScreen(this))));
        out.add(actionRow("Addons", () -> this.minecraft.gui.setScreen(new AutismAddonsScreen(this))));
        out.add(splitRow("Donate", () -> AutismLinks.open(AutismLinks.KOFI),
            "Discord", () -> AutismLinks.open(AutismLinks.DISCORD)));
        return out;
    }

    private Row toggleRow(String label, IntSupplierBool getter, java.util.function.Consumer<Boolean> setter) {
        return (g, rb, mx, my, clickable) -> {
            boolean on = getter.get();
            boolean hover = clickable && rb.contains(mx, my);
            UiRenderer.frame(g, rb, hover ? p(0x40201A1D, Channel.BUTTON) : 0x26181A1E, p(0x66662C2C, Channel.OUTLINE));
            draw(g, label, UiAssets.FONT_LABEL, p(TEXT_COLOR, Channel.TEXT), rb.x() + 8, rb.y() + 5);
            UiBounds tog = UiBounds.of(rb.right() - 34, rb.y() + 3, 30, rb.height() - 6);
            UiRenderer.frame(g, tog, on ? p(0xA8231A1D, Channel.TOGGLE) : 0x40121316, on ? p(0xFFFF6464, Channel.TOGGLE) : 0x66565D66);
            int knobX = on ? tog.right() - 12 : tog.x() + 2;
            UiRenderer.rect(g, UiBounds.of(knobX, tog.y() + 2, 10, tog.height() - 4), on ? p(0xFFFF3B3B, Channel.TOGGLE) : 0xFF8A8A8A);
            if (clickable) addHit(rb, () -> setter.accept(!getter.get()), null);
        };
    }

    private Row cycleRow(String label, String value, Runnable onLeft, Runnable onRight) {
        return (g, rb, mx, my, clickable) -> {
            boolean hover = clickable && rb.contains(mx, my);
            UiRenderer.frame(g, rb, hover ? p(0x40201A1D, Channel.BUTTON) : 0x26181A1E, p(0x66662C2C, Channel.OUTLINE));
            draw(g, label, UiAssets.FONT_LABEL, p(TEXT_COLOR, Channel.TEXT), rb.x() + 8, rb.y() + 5);
            int vw = UiText.width(font, value, UiAssets.FONT_BODY, MUTED_COLOR);
            draw(g, value, UiAssets.FONT_BODY, p(0xFFFF6464, Channel.ACCENT), rb.right() - vw - 8, rb.y() + 5);
            if (clickable) addHit(rb, onLeft, onRight);
        };
    }

    private Row prefixRow(AutismModule module, List<String> prefixes, String current) {
        if (prefixDropdown == null) {
            prefixDropdown = new Dropdown(UiBounds.of(0, 0, 0, 0), prefixes, current, module::setCommandPrefix);
        } else {
            prefixDropdown.setOptions(prefixes);
            prefixDropdown.setSelected(current);
        }
        return (g, rb, mx, my, clickable) -> {

            UiRenderer.frame(g, rb, 0x26181A1E, p(0x66662C2C, Channel.OUTLINE));
            draw(g, "Command Prefix", UiAssets.FONT_LABEL, p(TEXT_COLOR, Channel.TEXT), rb.x() + 8, rb.y() + 5);
            int ctrlW = Math.min(110, rb.width() / 2);
            UiBounds ctrl = UiBounds.of(rb.right() - ctrlW - 2, rb.y() + 1, ctrlW, rb.height() - 2);
            prefixDropdown.setBounds(ctrl);
            boolean hover = clickable && ctrl.contains(mx, my);
            Dropdown.renderControl(ctx, ctrl, current, hover, prefixDropdown.isOpen());
            if (clickable) {
                addHit(ctrl, () -> { if (!prefixDropdown.isOpen()) prefixDropdown.open(); }, null);
            } else if (prefixDropdown.isOpen()) {
                prefixDropdown.close();
            }
        };
    }

    private Row keybindRow(int idx) {
        return (g, rb, mx, my, clickable) -> {
            Keybind k = keybinds.get(idx);
            boolean hover = clickable && rb.contains(mx, my);
            boolean active = capturing == idx;
            UiRenderer.frame(g, rb, active ? p(0x55241A1D, Channel.ACCENT) : (hover ? p(0x40201A1D, Channel.BUTTON) : 0x26181A1E),
                active ? p(0xFFFF6464, Channel.ACCENT) : p(0x66662C2C, Channel.OUTLINE));
            draw(g, k.label(), UiAssets.FONT_LABEL, p(TEXT_COLOR, Channel.TEXT), rb.x() + 8, rb.y() + 5);
            String v = active ? "press a key..." : AutismBindUtil.getBindName(k.getter().getAsInt());
            int vw = UiText.width(font, v, UiAssets.FONT_BODY, MUTED_COLOR);
            draw(g, v, UiAssets.FONT_BODY, p(MUTED_COLOR, Channel.TEXT), rb.right() - vw - 8, rb.y() + 5);
            if (clickable) addHit(rb, () -> startCapture(idx), null);
        };
    }

    private Row actionRow(String label, Runnable onClick) {
        return (g, rb, mx, my, clickable) -> {
            button(g, rb, label, clickable && rb.contains(mx, my), false);
            if (clickable) addHit(rb, onClick, null);
        };
    }

    private Row splitRow(String labelA, Runnable onA, String labelB, Runnable onB) {
        return (g, rb, mx, my, clickable) -> {
            int half = (rb.width() - ROW_GAP) / 2;
            UiBounds a = UiBounds.of(rb.x(), rb.y(), half, rb.height());
            UiBounds b = UiBounds.of(rb.x() + half + ROW_GAP, rb.y(), rb.width() - half - ROW_GAP, rb.height());
            button(g, a, labelA, clickable && a.contains(mx, my), false);
            button(g, b, labelB, clickable && b.contains(mx, my), false);
            if (clickable) {
                addHit(a, onA, null);
                addHit(b, onB, null);
            }
        };
    }

    private interface IntSupplierBool { boolean get(); }

    private void addHit(UiBounds bounds, Runnable onLeft, Runnable onRight) {
        hits.add(new Hit(bounds, onLeft, onRight));
    }

    private int clampScroll(int value) {
        int max = Math.max(0, contentHeight - contentViewport.height());
        return Math.max(0, Math.min(value, max));
    }

    private Scrollbar.Metrics scrollbarMetrics() {
        UiBounds track = UiBounds.of(contentViewport.right() - 6, contentViewport.y(), 6, contentViewport.height());
        return Scrollbar.metrics(track, contentHeight, contentViewport.height(), scroll);
    }

    private void startScrollbarDrag(Scrollbar.Metrics metrics, int my) {
        scrollbarDragging = true;
        scrollbarGrabOffset = metrics.overThumb(metrics.track().x() + 1, my) ? my - metrics.thumb().y() : metrics.thumb().height() / 2;
        scroll = clampScroll(Scrollbar.scrollFromMouse(metrics, my, scrollbarGrabOffset));
    }

    private int p(int argb, Channel ch) {
        return AutismTheme.recolor(argb, ch);
    }

    private int border() {
        return p(0xFFB32B2B, Channel.OUTLINE);
    }

    private void button(GuiGraphicsExtractor g, UiBounds b, String label, boolean hover, boolean primary) {
        Button.render(ctx, b, label, primary ? Button.Tone.PRIMARY : Button.Tone.NORMAL, hover, false);
    }

    private void drawCentered(GuiGraphicsExtractor g, String text, Identifier fontId, int color, UiBounds area) {
        int tw = UiText.width(font, text, fontId, color);
        draw(g, text, fontId, color, area.x() + (area.width() - tw) / 2, area.y() + (area.height() - 8) / 2);
    }

    private void draw(GuiGraphicsExtractor g, String text, Identifier fontId, int color, int x, int y) {
        UiText.draw(g, this.font, text, fontId, color, x, y, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        boolean right = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (!right && event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (capturing >= 0) {
            applyCapture(AutismBindUtil.encodeMouseButton(event.button()));
            return true;
        }

        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            prefixDropdown.mouseClicked(mx, my, event.button());
            return true;
        }

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds().contains(mx, my)) continue;
            Runnable action = right ? hit.onRight() : hit.onLeft();
            if (action != null) action.run();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollbarDragging = false;
        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            prefixDropdown.mouseReleased(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrollbarDragging) {
            scroll = clampScroll(Scrollbar.scrollFromMouse(scrollbarMetrics(), AutismUiScale.toVirtualInt(event.y()), scrollbarGrabOffset));
            return true;
        }
        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            prefixDropdown.mouseDragged(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button(), dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {

        if (prefixDropdown != null && prefixDropdown.isOpen()) {
            prefixDropdown.mouseScrolled(AutismUiScale.toVirtualInt(mouseX), AutismUiScale.toVirtualInt(mouseY), scrollY);
            return true;
        }
        if (scrollY != 0) scroll = clampScroll(scroll - (int) Math.signum(scrollY) * ROW_STEP * 2);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing >= 0) {
            applyCapture(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (prefixDropdown != null && prefixDropdown.isOpen()) {
                prefixDropdown.close();
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void startCapture(int idx) {
        capturing = idx;
    }

    private void applyCapture(int code) {
        if (capturing < 0 || capturing >= keybinds.size()) return;
        keybinds.get(capturing).setter().accept(code);
        capturing = -1;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
