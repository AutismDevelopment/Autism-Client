package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.Toggle;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.commands.args.KeyArgumentType;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismChatField;
import autismclient.util.AutismConfig;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import autismclient.util.AutismUiScale;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class AutismThemeColorScreen extends Screen {
    private static final int FONT_BODY_COLOR = 0xFFF3ECE7;
    private static final int FONT_MUTED = 0xFFB79E9E;

    private final Screen parent;
    private final AutismConfig cfg;
    private final AutismConfig.ThemeColors pending = new AutismConfig.ThemeColors();
    private AutismTheme.State pendingState;

    private final Picker picker = new Picker();
    private int selected;

    private AutismThemeTextures.Preview logoPreview;
    private AutismThemeTextures.Preview panoramaPreview;
    private AutismThemeTextures.Preview iconPreview;

    private UiBounds simpleTab, advancedTab, resetBtn, cancelBtn, applyBtn;
    private final List<UiBounds> rowBounds = new ArrayList<>();
    private UiBounds pickerArea;

    private List<Chan> cachedRows;
    private boolean cachedRowsAdvanced;
    private List<Module> cachedPreview;
    private int cachedPreviewRev = Integer.MIN_VALUE;

    public AutismThemeColorScreen(Screen parent) {
        super(Component.literal("Theme Color"));
        this.parent = parent;
        this.cfg = AutismConfig.getGlobal();
        copy(cfg.themeColors, pending);
        rebuildState();
        loadSelectedIntoPicker();
    }

    @Override
    protected void init() {

        if (logoPreview == null) {
            logoPreview = AutismThemeTextures.preview(
                Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/autism_client_logo.png"), Channel.ACCENT, 2048);
        }

        if (panoramaPreview == null) {
            panoramaPreview = AutismThemeTextures.preview(
                Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama_0.png"), Channel.BACKDROP, 640);
        }

        if (iconPreview == null) {
            iconPreview = AutismThemeTextures.preview(UiAssets.ICON_PACKET_CATEGORY, Channel.ACCENT, 128);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Chan(String label, IntSupplier get, IntConsumer set, Channel previewChannel) {}

    private List<Chan> rows() {

        if (cachedRows != null && cachedRowsAdvanced == pending.advanced) return cachedRows;
        List<Chan> rows = new ArrayList<>();
        if (!pending.advanced) {
            rows.add(new Chan("Theme Color", () -> pending.master, v -> pending.master = v, Channel.ACCENT));
        } else {

            rows.add(new Chan("Accent", () -> pending.accent, v -> pending.accent = v, Channel.ACCENT));
            rows.add(new Chan("Button Fill", () -> pending.button, v -> pending.button = v, Channel.BUTTON));
            rows.add(new Chan("Outline / Border", () -> pending.outline, v -> pending.outline = v, Channel.OUTLINE));
            rows.add(new Chan("Header", () -> pending.header, v -> pending.header = v, Channel.HEADER));
            rows.add(new Chan("Toggle / Highlight", () -> pending.toggle, v -> pending.toggle = v, Channel.TOGGLE));
            rows.add(new Chan("Hover / Glow", () -> pending.hover, v -> pending.hover = v, Channel.HOVER));
            rows.add(new Chan("Text", () -> pending.text, v -> pending.text = v, Channel.TEXT));
            rows.add(new Chan("Background / Panorama", () -> pending.backdrop, v -> pending.backdrop = v, Channel.BACKDROP));
            rows.add(new Chan("Danger", () -> pending.danger, v -> pending.danger = v, Channel.DANGER));
            rows.add(new Chan("Success", () -> pending.success, v -> pending.success = v, Channel.SUCCESS));
        }
        cachedRows = rows;
        cachedRowsAdvanced = pending.advanced;
        return rows;
    }

    private void rebuildState() {
        pendingState = AutismTheme.State.from(pending);
    }

    private void loadSelectedIntoPicker() {
        List<Chan> rows = rows();
        if (selected >= rows.size()) selected = 0;
        picker.setColor(rows.get(selected).get().getAsInt());
        picker.onChange = argb -> {
            rows().get(selected).set().accept(argb);
            rebuildState();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            UiContext ctx = UiContexts.overlay(graphics, font, mx, my);

            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0xF0090709);

            int margin = 14;
            int contentW = sw - margin * 2;
            int contentH = sh - margin * 2;
            int leftW = Math.max(250, Math.round(contentW * 0.44f));
            int gap = 12;
            int rightW = contentW - leftW - gap;
            UiBounds left = UiBounds.of(margin, margin, leftW, contentH);
            UiBounds right = UiBounds.of(margin + leftW + gap, margin, Math.max(120, rightW), contentH);

            renderLeft(ctx, left, mx, my);
            renderPreview(ctx, right);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderLeft(UiContext ctx, UiBounds area, int mx, int my) {
        GuiGraphicsExtractor g = ctx.graphics();
        UiRenderer.frame(g, area, AutismColorsBg(), AutismThemeBorder());
        int x = area.x() + 12;
        int y = area.y() + 10;
        int innerW = area.width() - 24;

        draw(g, "THEME COLOR", UiAssets.FONT_TITLE, p(0xFFFF4D4D, Channel.ACCENT), x, y);
        y += 20;

        int tabW = (innerW - 6) / 2;
        simpleTab = UiBounds.of(x, y, tabW, 18);
        advancedTab = UiBounds.of(x + tabW + 6, y, innerW - tabW - 6, 18);
        tab(g, simpleTab, "Simple", !pending.advanced, simpleTab.contains(mx, my));
        tab(g, advancedTab, "Advanced", pending.advanced, advancedTab.contains(mx, my));
        y += 24;

        draw(g, pending.advanced ? "Channels" : "One color recolors the whole theme", UiAssets.FONT_LABEL, pText(FONT_MUTED), x, y);
        y += 14;

        rowBounds.clear();
        List<Chan> rows = rows();
        int rowH = pending.advanced ? 17 : 20;
        for (int i = 0; i < rows.size(); i++) {
            Chan c = rows.get(i);
            UiBounds rb = UiBounds.of(x, y, innerW, rowH - 2);
            rowBounds.add(rb);
            boolean sel = i == selected;
            boolean over = rb.contains(mx, my);
            UiRenderer.frame(g, rb, sel ? p(0x55241A1D, Channel.BUTTON) : (over ? p(0x40201A1D, Channel.BUTTON) : 0x26181A1E),
                sel ? p(0xFFFF6464, Channel.ACCENT) : p(0x66662C2C, Channel.OUTLINE));
            UiBounds swatch = UiBounds.of(rb.x() + 4, rb.y() + 3, 24, rb.height() - 6);
            UiRenderer.frame(g, swatch, 0xFF000000 | (c.get().getAsInt() & 0x00FFFFFF), 0xFF000000);
            draw(g, c.label(), UiAssets.FONT_BODY, sel ? pText(FONT_BODY_COLOR) : pText(FONT_MUTED), rb.x() + 34, rb.y() + (rb.height() - 8) / 2);
            String hex = hex6(c.get().getAsInt());
            int hexW = UiText.width(font, hex, UiAssets.FONT_BODY, FONT_MUTED);
            draw(g, hex, UiAssets.FONT_BODY, pText(FONT_MUTED), rb.right() - hexW - 6, rb.y() + (rb.height() - 8) / 2);
            y += rowH;
        }
        y += 6;

        picker.showAlpha = pending.advanced;
        picker.preview = pendingState;
        pickerArea = UiBounds.of(x, y, innerW, pending.advanced ? 156 : 142);
        picker.render(ctx, pickerArea);

        int footerY = area.bottom() - 28;
        int bw = (innerW - 12) / 3;
        resetBtn = UiBounds.of(x, footerY, bw, 20);
        cancelBtn = UiBounds.of(x + bw + 6, footerY, bw, 20);
        applyBtn = UiBounds.of(x + (bw + 6) * 2, footerY, innerW - (bw + 6) * 2, 20);
        button(g, resetBtn, "Reset", p(0x66662C2C, Channel.OUTLINE), resetBtn.contains(mx, my));
        button(g, cancelBtn, "Cancel", p(0x66662C2C, Channel.OUTLINE), cancelBtn.contains(mx, my));
        button(g, applyBtn, "Apply", p(0xFFFF6464, Channel.ACCENT), applyBtn.contains(mx, my));
    }

    private void renderPreview(UiContext ctx, UiBounds area) {
        GuiGraphicsExtractor g = ctx.graphics();
        UiRenderer.frame(g, area, AutismColorsBg(), AutismThemeBorder());
        int x = area.x() + 14;
        int y = area.y() + 12;
        int innerW = area.width() - 28;

        int logoW = logoPreview != null ? Math.min(innerW, 340) : 0;
        int logoH = logoPreview != null ? Math.max(1, Math.round(logoW * (logoPreview.height() / (float) logoPreview.width()))) : 0;
        int middleContentH = 197;

        draw(g, "LIVE PREVIEW", UiAssets.FONT_LABEL, p(0xFFFF4D4D, Channel.ACCENT), x, y);
        y += 18;

        UiRenderer.frame(g, UiBounds.of(x, y, innerW, 24), 0xA8111114, p(0xFFB32B2B, Channel.HEADER));
        if (iconPreview != null) {
            iconPreview.update(pendingState);
            g.blit(RenderPipelines.GUI_TEXTURED, iconPreview.id(), x + 6, y + 5, 0.0F, 0.0F, 14, 14,
                iconPreview.width(), iconPreview.height(), iconPreview.width(), iconPreview.height(), 0xFFFFFFFF);
        }
        draw(g, "Autism Client", UiAssets.FONT_LABEL, p(0xFFFFF4F4, Channel.TEXT), x + 26, y + 8);
        UiRenderer.rect(g, UiBounds.of(x, y + 24, innerW, 2), p(0xFFFF3B3B, Channel.HEADER));
        y += 34;

        if (panoramaPreview != null) {
            panoramaPreview.update(pendingState);
            int bw = innerW;

            int bannerMaxBottom = area.bottom() - 10 - logoH - middleContentH;
            int bh = Math.max(60, Math.min(Math.round(innerW * 0.56f), bannerMaxBottom - y));
            int regionH = Math.max(1, Math.min(panoramaPreview.height(), Math.round(panoramaPreview.height() * (bh / (float) bw))));
            float vOff = Math.max(0, (panoramaPreview.height() - regionH) / 2.0f);
            g.blit(RenderPipelines.GUI_TEXTURED, panoramaPreview.id(), x, y, 0.0F, vOff, bw, bh,
                panoramaPreview.width(), regionH, panoramaPreview.width(), panoramaPreview.height(), 0xFFFFFFFF);
            UiRenderer.outline(g, UiBounds.of(x, y, bw, bh), p(0xFF8F3131, Channel.OUTLINE));
            y += bh + 12;
        }

        draw(g, "MODULES", UiAssets.FONT_LABEL, pText(FONT_MUTED), x, y);
        y += 13;
        List<Module> preview = previewModules();
        for (int i = 0; i < preview.size(); i++) {
            Module m = preview.get(i);
            String key = m.keybind() >= 0 ? KeyArgumentType.keyName(m.keybind()) : "";

            y = previewModuleRow(g, x, y, innerW, m.name(), key, m.isEnabled(), i == 0, i == 1);
        }
        y += 10;

        previewToggle(g, x, y, true);
        previewToggle(g, x + 38, y, false);
        chip(g, x + 82, y - 1, 66, "Success", p(0xFF6FD38B, Channel.SUCCESS));
        chip(g, x + 154, y - 1, 66, "Danger", p(0xFFE26A6A, Channel.DANGER));
        y += 24;

        UiBounds btn = UiBounds.of(x, y, 84, 16);
        UiRenderer.frame(g, btn, p(0xA88F1F24, Channel.BUTTON), p(0xFFFF8787, Channel.OUTLINE));
        int btnTextW = UiText.width(font, "Button", UiAssets.FONT_BODY, FONT_BODY_COLOR);
        draw(g, "Button", UiAssets.FONT_BODY, p(0xFFFFF4F4, Channel.TEXT), btn.x() + (84 - btnTextW) / 2, btn.y() + 4);
        UiBounds field = UiBounds.of(x + 92, y, Math.max(60, innerW - 92), 16);
        UiRenderer.frame(g, field, 0xD9111217, p(0xFFFF3B3B, Channel.OUTLINE));
        draw(g, "search…", UiAssets.FONT_BODY, pText(FONT_MUTED), field.x() + 5, field.y() + 4);
        y += 24;

        draw(g, "Primary text sample", UiAssets.FONT_BODY, p(0xFFF3ECE7, Channel.TEXT), x, y); y += 12;
        draw(g, "Secondary / muted text", UiAssets.FONT_BODY, p(0xFFB79E9E, Channel.TEXT), x, y); y += 12;
        draw(g, "Accent text", UiAssets.FONT_BODY, p(0xFFFF4D4D, Channel.ACCENT), x, y); y += 18;

        if (logoPreview != null) {
            logoPreview.update(pendingState);
            int lx = x + (innerW - logoW) / 2;
            int ly = area.bottom() - 10 - logoH;
            g.blit(RenderPipelines.GUI_TEXTURED, logoPreview.id(), lx, ly, 0.0F, 0.0F, logoW, logoH,
                logoPreview.width(), logoPreview.height(), logoPreview.width(), logoPreview.height(), 0xFFFFFFFF);
        }
    }

    private int previewModuleRow(GuiGraphicsExtractor g, int x, int y, int w, String name, String keybind, boolean on, boolean selected, boolean hovered) {
        int h = 18;
        UiBounds row = UiBounds.of(x, y, w, h);
        int fill = selected ? p(0xA823382B, Channel.TOGGLE) : hovered ? p(0x4F241A1D, Channel.HOVER) : 0x26181A1E;
        UiRenderer.rect(g, row, fill);
        if (selected) UiRenderer.rect(g, UiBounds.of(x, y, 2, h), p(0xFF66E08A, Channel.TOGGLE));
        draw(g, name, UiAssets.FONT_BODY, on ? p(0xFFF3ECE7, Channel.TEXT) : pText(FONT_MUTED), x + 8, y + 5);
        if (keybind != null && !keybind.isEmpty()) {
            int nameW = UiText.width(font, name, UiAssets.FONT_BODY, FONT_BODY_COLOR);
            draw(g, "[" + keybind + "]", UiAssets.FONT_BODY, p(0xFFFF6464, Channel.ACCENT), x + 14 + nameW, y + 5);
        }
        previewToggle(g, x + w - 38, y + 2, on);
        return y + h + 3;
    }

    private static String hex6(int rgb) {
        rgb &= 0xFFFFFF;
        char[] out = new char[7];
        out[0] = '#';
        for (int i = 6; i >= 1; i--) {
            int nibble = rgb & 0xF;
            out[i] = (char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10));
            rgb >>= 4;
        }
        return new String(out);
    }

    private List<Module> previewModules() {

        int rev = ModuleRegistry.revision();
        if (cachedPreview != null && cachedPreviewRev == rev) return cachedPreview;
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleRegistry.all()) {
            if (m.showInModuleMenu()) {
                out.add(m);
                if (out.size() == 4) break;
            }
        }
        cachedPreview = out;
        cachedPreviewRev = rev;
        return out;
    }

    private void previewToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        UiBounds b = UiBounds.of(x, y, 30, 14);
        Toggle.renderLabeled(UiContexts.overlay(g, font, -1, -1), b, null, on, false, "themePreviewToggle:" + x + ":" + y);
    }

    private void chip(GuiGraphicsExtractor g, int x, int y, int w, String label, int color) {
        UiRenderer.frame(g, UiBounds.of(x, y, w, 16), (color & 0x00FFFFFF) | 0x33000000, color);
        draw(g, label, UiAssets.FONT_BODY, color, x + 8, y + 4);
    }

    private int p(int color, Channel ch) {
        return AutismTheme.recolor(color, ch, pendingState);
    }

    private int pText(int color) {
        return p(color, Channel.TEXT);
    }

    private int AutismColorsBg() {
        return 0xE60A0A0C;
    }

    private int AutismThemeBorder() {
        return p(0xFFB32B2B, Channel.OUTLINE);
    }

    private void tab(GuiGraphicsExtractor g, UiBounds b, String label, boolean active, boolean hover) {
        Button.render(UiContexts.overlay(g, font, -1, -1), b, label, active ? Button.Tone.PRIMARY : Button.Tone.NORMAL, hover, active);
    }

    private void button(GuiGraphicsExtractor g, UiBounds b, String label, int border, boolean hover) {

        Button.render(UiContexts.overlay(g, font, -1, -1), b, label,
            border == p(0xFFFF6464, Channel.ACCENT) ? Button.Tone.PRIMARY : Button.Tone.NORMAL, hover, false);
    }

    private void draw(GuiGraphicsExtractor g, String text, Identifier font, int color, int x, int y) {
        UiText.draw(g, this.font, text, font, color, x, y, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        if (picker.mouseClicked(mx, my, event.button())) return true;

        if (simpleTab != null && simpleTab.contains(mx, my)) { setMode(false); return true; }
        if (advancedTab != null && advancedTab.contains(mx, my)) { setMode(true); return true; }
        for (int i = 0; i < rowBounds.size(); i++) {
            if (rowBounds.get(i).contains(mx, my)) {
                selected = i;
                loadSelectedIntoPicker();
                return true;
            }
        }
        if (resetBtn != null && resetBtn.contains(mx, my)) { reset(); return true; }
        if (cancelBtn != null && cancelBtn.contains(mx, my)) { onClose(); return true; }
        if (applyBtn != null && applyBtn.contains(mx, my)) { apply(); return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        picker.mouseDragged(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button(), dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        picker.mouseReleased(AutismUiScale.toVirtualInt(event.x()), AutismUiScale.toVirtualInt(event.y()), event.button());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (picker.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (picker.charTyped(input)) return true;
        return super.charTyped(input);
    }

    private void setMode(boolean advanced) {
        if (pending.advanced == advanced) return;
        if (advanced) {
            int defaultMaster = AutismTheme.DEFAULTS[Channel.ACCENT.ordinal()];
            boolean masterChanged = (pending.master & 0x00FFFFFF) != (defaultMaster & 0x00FFFFFF)
                || ((pending.master >>> 24) & 0xFF) != 0xFF;
            if (masterChanged) {

                pending.accent = pending.master;
                pending.button = pending.master;
                pending.outline = pending.master;
                pending.header = pending.master;
                pending.toggle = pending.master;
                pending.hover = pending.master;
                pending.backdrop = pending.master;
                pending.danger = pending.master;
            } else {

                pending.accent = AutismTheme.DEFAULTS[Channel.ACCENT.ordinal()];
                pending.button = AutismTheme.DEFAULTS[Channel.BUTTON.ordinal()];
                pending.outline = AutismTheme.DEFAULTS[Channel.OUTLINE.ordinal()];
                pending.header = AutismTheme.DEFAULTS[Channel.HEADER.ordinal()];
                pending.toggle = AutismTheme.DEFAULTS[Channel.TOGGLE.ordinal()];
                pending.hover = AutismTheme.DEFAULTS[Channel.HOVER.ordinal()];
                pending.backdrop = AutismTheme.DEFAULTS[Channel.BACKDROP.ordinal()];
                pending.danger = AutismTheme.DEFAULTS[Channel.DANGER.ordinal()];
            }
        }
        pending.advanced = advanced;
        selected = 0;
        rebuildState();
        loadSelectedIntoPicker();
    }

    private void reset() {
        AutismConfig.ThemeColors def = new AutismConfig.ThemeColors();
        def.advanced = pending.advanced;
        copy(def, pending);
        rebuildState();
        loadSelectedIntoPicker();
        AutismNotifications.success("Theme reset to defaults");
    }

    private void apply() {
        copy(pending, cfg.themeColors);
        AutismTheme.reload();
        Runnable saved = autismclient.gui.AutismThemeApplyOverlay.beginJob("Saving config");
        autismclient.util.AutismBackgroundTasks.runTracked("theme-config-save", () -> {
            try {
                cfg.save();
            } finally {
                saved.run();
            }
        });
        AutismNotifications.success("Theme applied");
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (logoPreview != null) { logoPreview.close(); logoPreview = null; }
        if (panoramaPreview != null) { panoramaPreview.close(); panoramaPreview = null; }
        if (iconPreview != null) { iconPreview.close(); iconPreview = null; }
    }

    private static void copy(AutismConfig.ThemeColors from, AutismConfig.ThemeColors to) {
        to.advanced = from.advanced;
        to.master = from.master;
        to.accent = from.accent;
        to.outline = from.outline;
        to.text = from.text;
        to.toggle = from.toggle;
        to.backdrop = from.backdrop;
        to.success = from.success;
        to.danger = from.danger;
        to.button = from.button;
        to.header = from.header;
        to.hover = from.hover;
    }

    private static final class Picker {
        private static final int SV_W = 130, SV_H = 90, HUE_W = 14;
        private IntConsumer onChange;
        private boolean showAlpha;
        private AutismTheme.State preview;

        private int pp(int color, Channel ch) { return AutismTheme.recolor(color, ch, preview); }
        private float hue, sat, bri;
        private int r, g, b, a = 255;
        private UiBounds sv, hueStrip, rSlider, gSlider, bSlider, aSlider, hexField;
        private int drag;
        private AutismChatField hexInput;

        void setColor(int argb) {
            a = (argb >>> 24) & 0xFF;
            r = (argb >>> 16) & 0xFF; g = (argb >>> 8) & 0xFF; b = argb & 0xFF;
            float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
            if (hexInput != null) hexInput.setFocused(false);
            syncHex();
        }

        private int argb() { return ((showAlpha ? a : 255) << 24) | (r << 16) | (g << 8) | b; }

        private void ensureHexInput() {
            if (hexInput == null) {
                hexInput = new AutismChatField(mc(), mc().font, 0, 0, 20, 16, false);
                hexInput.setMaxLength(6);
                hexInput.setFilter(next -> next.chars().allMatch(ch -> isHex((char) ch)));
                hexInput.setChangedListener(value -> applyHex());
                hexInput.setText(String.format(Locale.ROOT, "%06X", (r << 16) | (g << 8) | b));
            }
        }

        private void syncHex() {
            if (hexInput != null && !hexInput.isFocused()) {
                hexInput.setText(String.format(Locale.ROOT, "%06X", (r << 16) | (g << 8) | b));
            }
        }

        private void fromHsb() {
            int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri) & 0x00FFFFFF;
            r = (rgb >> 16) & 0xFF; g = (rgb >> 8) & 0xFF; b = rgb & 0xFF;
            syncHex(); fire();
        }

        private void fromRgb() {
            float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
            syncHex(); fire();
        }

        private void fire() { if (onChange != null) onChange.accept(argb()); }

        void render(UiContext ctx, UiBounds area) {
            GuiGraphicsExtractor g2 = ctx.graphics();
            sv = UiBounds.of(area.x(), area.y(), SV_W, SV_H);
            hueStrip = UiBounds.of(sv.right() + 8, area.y(), HUE_W, SV_H);

            GradientTex.sv(hue).blit(g2, sv);
            UiRenderer.outline(g2, sv, pp(0x99662C2C, Channel.OUTLINE));
            int cx = sv.x() + Math.round(sat * (sv.width() - 1));
            int cy = sv.y() + Math.round((1 - bri) * (sv.height() - 1));
            UiRenderer.rect(g2, UiBounds.of(cx - 4, cy, 9, 1), 0xFFFFFFFF);
            UiRenderer.rect(g2, UiBounds.of(cx, cy - 4, 1, 9), 0xFFFFFFFF);

            GradientTex.hue().blit(g2, hueStrip);
            UiRenderer.outline(g2, hueStrip, pp(0x99662C2C, Channel.OUTLINE));
            int hy = hueStrip.y() + Math.round((1 - hue) * (hueStrip.height() - 1));
            UiRenderer.outline(g2, UiBounds.of(hueStrip.x() - 1, hy - 1, hueStrip.width() + 2, 3), 0xFFFFFFFF);

            int rx = hueStrip.right() + 10;
            int rw = Math.max(58, area.right() - rx);
            UiBounds swatch = UiBounds.of(rx, area.y(), rw, 20);
            UiRenderer.rect(g2, swatch, 0xFF3A3A3A);
            UiRenderer.rect(g2, swatch, argb());
            UiRenderer.outline(g2, swatch, 0xFF000000);
            hexField = UiBounds.of(rx, area.y() + 24, rw, 16);
            ensureHexInput();
            hexInput.setX(hexField.x());
            hexInput.setY(hexField.y());
            hexInput.setWidth(hexField.width());
            hexInput.setHeight(hexField.height());
            hexInput.render(g2, ctx.mouseX(), ctx.mouseY(), 0f);

            int sy = sv.bottom() + 8;
            rSlider = UiBounds.of(area.x() + 14, sy, area.width() - 52, 9);
            gSlider = UiBounds.of(area.x() + 14, sy + 13, area.width() - 52, 9);
            bSlider = UiBounds.of(area.x() + 14, sy + 26, area.width() - 52, 9);
            slider(g2, rSlider, "R", r, 0xFFFF5555);
            slider(g2, gSlider, "G", g, 0xFF55DD77);
            slider(g2, bSlider, "B", b, 0xFF6699FF);
            if (showAlpha) {
                aSlider = UiBounds.of(area.x() + 14, sy + 39, area.width() - 52, 9);
                slider(g2, aSlider, "A", a, 0xFFBFBFBF);
            } else {
                aSlider = null;
            }
        }

        private void slider(GuiGraphicsExtractor g2, UiBounds b2, String label, int value, int accent) {
            UiText.draw(g2, mc().font, label, UiAssets.FONT_BODY, pp(FONT_MUTED, Channel.TEXT), b2.x() - 12, b2.y() + 1, false);
            UiRenderer.frame(g2, b2, 0xD9111217, pp(0x99662C2C, Channel.OUTLINE));
            int fillW = Math.max(0, Math.round((b2.width() - 2) * (value / 255.0f)));
            if (fillW > 0) UiRenderer.rect(g2, UiBounds.of(b2.x() + 1, b2.y() + 1, fillW, b2.height() - 2), accent);
            int knobX = b2.x() + 1 + Math.round((b2.width() - 3) * (value / 255.0f));
            UiRenderer.rect(g2, UiBounds.of(knobX, b2.y(), 2, b2.height()), 0xFFFFFFFF);
            UiText.draw(g2, mc().font, Integer.toString(value), UiAssets.FONT_BODY, pp(FONT_BODY_COLOR, Channel.TEXT), b2.right() + 5, b2.y() + 1, false);
        }

        boolean mouseClicked(int mx, int my, int button) {
            if (sv == null) return false;
            ensureHexInput();
            if (hexInput.mouseClicked(mx, my, button)) return true;
            if (sv.contains(mx, my)) { drag = 1; updateSv(mx, my); return true; }
            if (hueStrip.contains(mx, my)) { drag = 2; updateHue(my); return true; }
            if (rSlider.contains(mx, my)) { drag = 3; updateChannel(mx, rSlider, 0); return true; }
            if (gSlider.contains(mx, my)) { drag = 4; updateChannel(mx, gSlider, 1); return true; }
            if (bSlider.contains(mx, my)) { drag = 5; updateChannel(mx, bSlider, 2); return true; }
            if (aSlider != null && aSlider.contains(mx, my)) { drag = 6; updateChannel(mx, aSlider, 3); return true; }
            return false;
        }

        void mouseDragged(int mx, int my, int button, double dx, double dy) {
            if (hexInput != null && hexInput.mouseDragged(mx, my, button, dx, dy)) return;
            switch (drag) {
                case 1 -> updateSv(mx, my);
                case 2 -> updateHue(my);
                case 3 -> updateChannel(mx, rSlider, 0);
                case 4 -> updateChannel(mx, gSlider, 1);
                case 5 -> updateChannel(mx, bSlider, 2);
                case 6 -> { if (aSlider != null) updateChannel(mx, aSlider, 3); }
                default -> { }
            }
        }

        void mouseReleased(int mx, int my, int button) {
            drag = 0;
            if (hexInput != null) hexInput.mouseReleased(mx, my, button);
        }

        boolean keyPressed(KeyEvent input) {
            if (hexInput == null || !hexInput.isFocused()) return false;
            int key = input.key();
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                hexInput.setFocused(false); syncHex();
                return true;
            }
            hexInput.keyPressed(input);
            return true;
        }

        boolean charTyped(CharacterEvent input) {
            if (hexInput == null || !hexInput.isFocused()) return false;
            hexInput.charTyped(input);
            return true;
        }

        private void applyHex() {
            String hexText = hexInput == null ? "" : hexInput.getText();
            if (hexText.length() != 6) return;
            try {
                int rgb = Integer.parseInt(hexText, 16) & 0xFFFFFF;
                r = (rgb >> 16) & 0xFF; g = (rgb >> 8) & 0xFF; b = rgb & 0xFF;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
                fire();
            } catch (NumberFormatException ignored) {  }
        }

        private void updateSv(int mx, int my) {
            sat = clamp01((mx - sv.x()) / (float) Math.max(1, sv.width() - 1));
            bri = clamp01(1 - (my - sv.y()) / (float) Math.max(1, sv.height() - 1));
            fromHsb();
        }

        private void updateHue(int my) {
            hue = clamp01(1 - (my - hueStrip.y()) / (float) Math.max(1, hueStrip.height() - 1));
            fromHsb();
        }

        private void updateChannel(int mx, UiBounds bounds, int channel) {
            int value = Math.round(clamp01((mx - bounds.x()) / (float) Math.max(1, bounds.width())) * 255.0f);
            if (channel == 0) r = value; else if (channel == 1) g = value; else if (channel == 2) b = value; else { a = value; fire(); return; }
            fromRgb();
        }

        private static boolean isHex(char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

        private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

        private static net.minecraft.client.Minecraft mc() { return net.minecraft.client.Minecraft.getInstance(); }
    }

    private static final class GradientTex extends AbstractTexture {
        private static final int HUE_BUCKETS = 360;
        private static GradientTex hueTex;
        private static GradientTex svTex;
        private static int svBucket = Integer.MIN_VALUE;

        private final Identifier id;
        private final NativeImage pixels;
        private final int w, h;

        private GradientTex(Identifier id, String label, int w, int h) {
            this.id = id; this.w = w; this.h = h;
            this.pixels = new NativeImage(w, h, true);
            this.texture = RenderSystem.getDevice().createTexture("AUTISM " + label, 5, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, this);
        }

        static GradientTex hue() {
            if (hueTex == null) {
                hueTex = new GradientTex(Identifier.fromNamespaceAndPath("autismclient", "dynamic/theme/picker_hue"), "theme picker hue", 14, 96);
                for (int y = 0; y < hueTex.h; y++) {
                    int color = 0xFF000000 | (java.awt.Color.HSBtoRGB(1.0f - (y / (float) (hueTex.h - 1)), 1, 1) & 0x00FFFFFF);
                    for (int x = 0; x < hueTex.w; x++) hueTex.pixels.setPixel(x, y, color);
                }
                hueTex.upload();
            }
            return hueTex;
        }

        static GradientTex sv(float hue) {
            int bucket = Math.round(Picker.clamp01(hue) * HUE_BUCKETS);
            if (svTex == null) svTex = new GradientTex(Identifier.fromNamespaceAndPath("autismclient", "dynamic/theme/picker_sv"), "theme picker sv", 130, 96);
            if (svBucket != bucket) {
                float hh = bucket / (float) HUE_BUCKETS;
                for (int x = 0; x < svTex.w; x++) {
                    float s = x / (float) (svTex.w - 1);
                    for (int y = 0; y < svTex.h; y++) {
                        float v = 1.0f - (y / (float) (svTex.h - 1));
                        svTex.pixels.setPixel(x, y, 0xFF000000 | (java.awt.Color.HSBtoRGB(hh, s, v) & 0x00FFFFFF));
                    }
                }
                svTex.upload();
                svBucket = bucket;
            }
            return svTex;
        }

        private void upload() {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
        }

        private void blit(GuiGraphicsExtractor g, UiBounds area) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, area.x(), area.y(), 0.0F, 0.0F, area.width(), area.height(), w, h, w, h, 0xFFFFFFFF);
        }
    }
}
