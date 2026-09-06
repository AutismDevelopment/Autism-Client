package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

public final class ColorPicker {
    private static final int WIDTH = 244;
    private static final int HEIGHT = 188;
    private static final int HEADER_H = 16;
    private static final int SV_W = 102;
    private static final int SV_H = 82;
    private static final int HUE_W = 13;
    private static final int HUE_H = 82;
    private static final int HUE_BUCKETS = 360;
    private static final int BACKDROP = 0x99000000;
    private static final Minecraft MC = Minecraft.getInstance();
    private static GradientTexture hueTexture;
    private static GradientTexture saturationValueTexture;
    private static int saturationValueHueBucket = Integer.MIN_VALUE;

    private final Consumer<Integer> onSave;
    private final int initialArgb;
    private final int defaultArgb;
    private final int preservedAlpha;
    private UiBounds bounds;
    private boolean open = true;
    private float hue;
    private float saturation;
    private float brightness;
    private boolean hexFocused;
    private String hexText;
    private int hexCursor;
    private int hexSelectionAnchor = -1;
    private int draftRgb;
    private int draftArgb;
    private int draftRed;
    private int draftGreen;
    private int draftBlue;
    private DragTarget dragTarget = DragTarget.NONE;

    public ColorPicker(UiBounds anchor, int initialArgb, int defaultArgb, int screenWidth, int screenHeight, Consumer<Integer> onSave) {
        this.onSave = onSave;
        this.initialArgb = initialArgb;
        this.defaultArgb = defaultArgb;
        this.preservedAlpha = (initialArgb >>> 24) & 0xFF;
        setDraftRgb(initialArgb & 0x00FFFFFF);
        int x = anchor == null ? 8 : anchor.right() - WIDTH;
        int y = anchor == null ? 8 : anchor.bottom() + 4;
        if (anchor != null && y + HEIGHT > screenHeight - 4) y = anchor.y() - HEIGHT - 4;
        this.bounds = UiBounds.of(x, y, WIDTH, HEIGHT).clampInside(Math.max(1, screenWidth - 4), Math.max(1, screenHeight - 4));
    }

    public boolean isOpen() {
        return open;
    }

    public void closeCancel() {
        open = false;
    }

    public boolean contains(int mouseX, int mouseY) {
        return open && bounds.contains(mouseX, mouseY);
    }

    public void render(UiContext context) {
        if (!open) return;
        UiRenderer.rect(context.graphics(), UiBounds.of(0, 0, context.screenWidth(), context.screenHeight()), BACKDROP);
        CompactWindow.renderFrame(context, bounds, "Color", false, false, true, headerBounds().contains(context.mouseX(), context.mouseY()), true, 5, 5, HEADER_H);
        UiBounds close = TopBar.closeButton(headerBounds());
        boolean closeHover = close.contains(context.mouseX(), context.mouseY());
        if (closeHover) UiRenderer.rect(context.graphics(), close.inset(1), 0x22FFFFFF);

        UiBounds sv = svBounds();
        renderSaturationValue(context, sv);
        drawCrosshair(context, sv);

        UiBounds hueStrip = hueBounds();
        renderHueStrip(context, hueStrip);
        int hueY = hueStrip.y() + Math.round((1.0f - hue) * (hueStrip.height() - 1));
        UiRenderer.outline(context.graphics(), UiBounds.of(hueStrip.x() - 1, hueY - 1, hueStrip.width() + 2, 3), context.theme().colors().text);

        UiBounds current = UiBounds.of(bounds.x() + 144, bounds.y() + 25, 38, 20);
        UiBounds draft = UiBounds.of(bounds.x() + 190, bounds.y() + 25, 38, 20);
        drawSwatch(context, current, initialArgb);
        drawSwatch(context, draft, draftArgb());
        context.text().drawCentered(context.graphics(), "Old", UiBounds.of(current.x(), current.bottom() + 2, current.width(), 10), context.theme().colors().muted);
        context.text().drawCentered(context.graphics(), "New", UiBounds.of(draft.x(), draft.bottom() + 2, draft.width(), 10), context.theme().colors().muted);

        renderSlider(context, redBounds(), "R", red(), 0xFFFF5555);
        renderSlider(context, greenBounds(), "G", green(), 0xFF55DD77);
        renderSlider(context, blueBounds(), "B", blue(), 0xFF6699FF);

        UiBounds hex = hexBounds();
        TextField.render(context, hex, hexText, "RRGGBB", hexFocused, hexCursor, hexSelectionStart(), hexSelectionEnd());
        context.text().drawFitted(context.graphics(), "#", hex.x() - 9, context.text().centeredY(hex), 8, context.theme().colors().muted);

        Button.render(context, saveBounds(), "Save", Button.Tone.SUCCESS, saveBounds().contains(context.mouseX(), context.mouseY()), false);
        Button.render(context, resetBounds(), "Reset", Button.Tone.NORMAL, resetBounds().contains(context.mouseX(), context.mouseY()), false);
        Button.render(context, cancelBounds(), "Cancel", Button.Tone.DANGER, cancelBounds().contains(context.mouseX(), context.mouseY()), false);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) return false;
        if (button != 0) return true;
        if (!bounds.contains(mouseX, mouseY)) {
            closeCancel();
            return true;
        }
        if (TopBar.closeButton(headerBounds()).contains(mouseX, mouseY) || cancelBounds().contains(mouseX, mouseY)) {
            closeCancel();
            return true;
        }
        if (saveBounds().contains(mouseX, mouseY)) {
            if (onSave != null) onSave.accept(draftArgb());
            open = false;
            return true;
        }
        if (resetBounds().contains(mouseX, mouseY)) {
            setDraftRgb(defaultArgb & 0x00FFFFFF);
            return true;
        }
        UiBounds hex = hexBounds();
        hexFocused = hex.contains(mouseX, mouseY);
        if (hexFocused) {
            hexCursor = cursorFromHexMouse(mouseX, hex);
            hexSelectionAnchor = hexCursor;
            dragTarget = DragTarget.HEX;
            return true;
        }
        if (svBounds().contains(mouseX, mouseY)) {
            dragTarget = DragTarget.SV;
            updateSaturationValue(mouseX, mouseY);
        } else if (hueBounds().contains(mouseX, mouseY)) {
            dragTarget = DragTarget.HUE;
            updateHue(mouseY);
        } else if (redBounds().contains(mouseX, mouseY)) {
            dragTarget = DragTarget.RED;
            updateChannel(mouseX, redBounds(), 0);
        } else if (greenBounds().contains(mouseX, mouseY)) {
            dragTarget = DragTarget.GREEN;
            updateChannel(mouseX, greenBounds(), 1);
        } else if (blueBounds().contains(mouseX, mouseY)) {
            dragTarget = DragTarget.BLUE;
            updateChannel(mouseX, blueBounds(), 2);
        } else {
            dragTarget = DragTarget.NONE;
        }
        return true;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        dragTarget = DragTarget.NONE;
        return open;
    }

    public boolean mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        if (!open || button != 0) return false;
        switch (dragTarget) {
            case SV -> updateSaturationValue(mouseX, mouseY);
            case HUE -> updateHue(mouseY);
            case RED -> updateChannel(mouseX, redBounds(), 0);
            case GREEN -> updateChannel(mouseX, greenBounds(), 1);
            case BLUE -> updateChannel(mouseX, blueBounds(), 2);
            case HEX -> hexCursor = cursorFromHexMouse(mouseX, hexBounds());
            default -> {
            }
        }
        return true;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        return open;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!open) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeCancel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (hexFocused) applyHexText();
            if (onSave != null) onSave.accept(draftArgb());
            open = false;
            return true;
        }
        if (!hexFocused) return true;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedHexTextOrAll());
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            pasteHex(Minecraft.getInstance().keyboardHandler.getClipboard(), hasHexSelection());
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAllHex();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !hexText.isEmpty()) {
            if (hasHexSelection()) {
                replaceSelectedHex("");
            } else if (hexCursor > 0) {
                hexText = hexText.substring(0, hexCursor - 1) + hexText.substring(hexCursor);
                hexCursor--;
                applyHexText();
            }
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            if (hasHexSelection()) {
                replaceSelectedHex("");
            } else if (hexCursor < hexText.length()) {
                hexText = hexText.substring(0, hexCursor) + hexText.substring(hexCursor + 1);
                applyHexText();
            }
        } else if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            moveHexCursor(key, shift);
            return true;
        }
        return true;
    }

    public boolean charTyped(char chr) {
        if (!open || !hexFocused) return false;
        if (isHex(chr)) {
            if (hasHexSelection()) {
                replaceSelectedHex(Character.toString(Character.toUpperCase(chr)));
            } else if (hexText.length() < 6) {
                hexText = (hexText.substring(0, hexCursor) + Character.toUpperCase(chr) + hexText.substring(hexCursor)).toUpperCase(Locale.ROOT);
                hexCursor++;
                clearHexSelection();
                applyHexText();
            }
        }
        return true;
    }

    private int cursorFromHexMouse(int mouseX, UiBounds hex) {
        int local = Math.max(0, mouseX - hex.x() - 4);
        int cursor = hexText.length();
        for (int i = 0; i <= hexText.length(); i++) {
            if (MC.font.width(hexText.substring(0, i)) >= local) {
                cursor = i;
                break;
            }
        }
        return cursor;
    }

    private void moveHexCursor(int key, boolean shift) {
        if (shift && hexSelectionAnchor < 0) hexSelectionAnchor = hexCursor;
        if (!shift) clearHexSelection();
        switch (key) {
            case GLFW.GLFW_KEY_HOME -> hexCursor = 0;
            case GLFW.GLFW_KEY_END -> hexCursor = hexText.length();
            case GLFW.GLFW_KEY_LEFT -> hexCursor = Math.max(0, hexCursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> hexCursor = Math.min(hexText.length(), hexCursor + 1);
            default -> {
            }
        }
        if (shift && hexSelectionAnchor == hexCursor) clearHexSelection();
    }

    private void selectAllHex() {
        hexSelectionAnchor = 0;
        hexCursor = hexText.length();
    }

    private void clearHexSelection() {
        hexSelectionAnchor = -1;
    }

    private boolean hasHexSelection() {
        return hexSelectionAnchor >= 0 && hexSelectionAnchor != hexCursor;
    }

    private int hexSelectionStart() {
        return hasHexSelection() ? Math.min(hexSelectionAnchor, hexCursor) : -1;
    }

    private int hexSelectionEnd() {
        return hasHexSelection() ? Math.max(hexSelectionAnchor, hexCursor) : -1;
    }

    private String selectedHexTextOrAll() {
        if (!hasHexSelection()) return hexText;
        return hexText.substring(hexSelectionStart(), hexSelectionEnd());
    }

    private void replaceSelectedHex(String value) {
        int start = hasHexSelection() ? hexSelectionStart() : hexCursor;
        int end = hasHexSelection() ? hexSelectionEnd() : hexCursor;
        String cleaned = cleanHex(value, 6 - (hexText.length() - (end - start)));
        hexText = (hexText.substring(0, start) + cleaned + hexText.substring(end)).toUpperCase(Locale.ROOT);
        hexCursor = start + cleaned.length();
        clearHexSelection();
        applyHexText();
    }

    private static String cleanHex(String value, int maxLength) {
        if (value == null || maxLength <= 0) return "";
        String text = value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() >= 8) text = text.substring(text.length() - 6);
        StringBuilder cleaned = new StringBuilder(Math.min(6, maxLength));
        for (int i = 0; i < text.length() && cleaned.length() < maxLength; i++) {
            char chr = text.charAt(i);
            if (isHex(chr)) cleaned.append(Character.toUpperCase(chr));
        }
        return cleaned.toString();
    }

    private void normalizeHexCursor() {
        hexCursor = Math.max(0, Math.min(hexCursor, hexText == null ? 0 : hexText.length()));
        if (hexSelectionAnchor > (hexText == null ? 0 : hexText.length())) hexSelectionAnchor = hexText.length();
    }

    private void applyHexText() {
        normalizeHexCursor();
        if (hexText.length() != 6) return;
        try {
            setDraftRgb(Integer.parseInt(hexText, 16) & 0x00FFFFFF);
            hexCursor = hexText.length();
            clearHexSelection();
        } catch (Exception ignored) {  }
    }

    private void pasteHex(String clipboard, boolean replaceSelectionOnly) {
        String cleaned = cleanHex(clipboard, 6);
        if (cleaned.length() != 6) return;
        if (replaceSelectionOnly && hasHexSelection()) {
            replaceSelectedHex(cleaned);
        } else {
            hexText = cleaned.toUpperCase(Locale.ROOT);
            hexCursor = hexText.length();
            clearHexSelection();
            applyHexText();
        }
    }

    private void renderSaturationValue(UiContext context, UiBounds area) {
        GradientTexture texture = saturationValueTexture(hue);
        texture.blit(context, area);
        UiRenderer.outline(context.graphics(), area, context.theme().colors().borderSoft);
    }

    private void renderHueStrip(UiContext context, UiBounds area) {
        hueTexture().blit(context, area);
        UiRenderer.outline(context.graphics(), area, context.theme().colors().borderSoft);
    }

    private void renderSlider(UiContext context, UiBounds bounds, String label, int value, int accent) {
        context.text().drawCentered(context.graphics(), label, UiBounds.of(bounds.x() - 15, bounds.y(), 12, bounds.height()), context.theme().colors().muted);
        UiRenderer.frame(context.graphics(), bounds, context.theme().colors().field, context.theme().colors().borderSoft);
        int fillW = Math.max(0, Math.round((bounds.width() - 2) * (value / 255.0f)));
        if (fillW > 0) UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x() + 1, bounds.y() + 1, fillW, bounds.height() - 2), accent);
        int knobX = bounds.x() + 1 + Math.round((bounds.width() - 3) * (value / 255.0f));
        UiRenderer.rect(context.graphics(), UiBounds.of(knobX, bounds.y() + 1, 2, bounds.height() - 2), 0xFFFFFFFF);
        context.text().drawFitted(context.graphics(), Integer.toString(value), bounds.right() + 5, context.text().centeredY(bounds), 24, context.theme().colors().text);
    }

    private void drawSwatch(UiContext context, UiBounds bounds, int color) {
        UiRenderer.frame(context.graphics(), bounds, 0xFF000000 | (color & 0x00FFFFFF), context.theme().colors().borderSoft);
    }

    private void drawCrosshair(UiContext context, UiBounds area) {
        int x = area.x() + Math.round(saturation * (area.width() - 1));
        int y = area.y() + Math.round((1.0f - brightness) * (area.height() - 1));
        UiRenderer.rect(context.graphics(), UiBounds.of(x - 4, y, 9, 1), 0xFFFFFFFF);
        UiRenderer.rect(context.graphics(), UiBounds.of(x, y - 4, 1, 9), 0xFFFFFFFF);
        UiRenderer.rect(context.graphics(), UiBounds.of(x - 3, y, 7, 1), 0xFF000000);
        UiRenderer.rect(context.graphics(), UiBounds.of(x, y - 3, 1, 7), 0xFF000000);
    }

    private void updateSaturationValue(int mouseX, int mouseY) {
        UiBounds area = svBounds();
        saturation = clamp01((mouseX - area.x()) / (float) Math.max(1, area.width() - 1));
        brightness = clamp01(1.0f - ((mouseY - area.y()) / (float) Math.max(1, area.height() - 1)));
        updateDraftFromHsb();
    }

    private void updateHue(int mouseY) {
        UiBounds area = hueBounds();
        hue = clamp01(1.0f - ((mouseY - area.y()) / (float) Math.max(1, area.height() - 1)));
        updateDraftFromHsb();
    }

    private void updateChannel(int mouseX, UiBounds bounds, int channel) {
        int value = Math.round(clamp01((mouseX - bounds.x()) / (float) Math.max(1, bounds.width())) * 255.0f);
        int r = draftRed;
        int g = draftGreen;
        int b = draftBlue;
        if (channel == 0) r = value;
        else if (channel == 1) g = value;
        else b = value;
        setDraftRgb((r << 16) | (g << 8) | b);
    }

    private void setDraftRgb(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        setDraftRgbFields(rgb);
    }

    private void updateDraftFromHsb() {
        setDraftRgbFields(java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF);
    }

    private void setDraftRgbFields(int rgb) {
        draftRgb = rgb & 0x00FFFFFF;
        draftRed = (draftRgb >>> 16) & 0xFF;
        draftGreen = (draftRgb >>> 8) & 0xFF;
        draftBlue = draftRgb & 0xFF;
        draftArgb = ((preservedAlpha & 0xFF) << 24) | draftRgb;
        syncHex();
    }

    private void syncHex() {
        hexText = String.format(Locale.ROOT, "%06X", draftRgb);
        hexCursor = hexText.length();
        clearHexSelection();
    }

    private int draftArgb() {
        return draftArgb;
    }

    private int red() {
        return draftRed;
    }

    private int green() {
        return draftGreen;
    }

    private int blue() {
        return draftBlue;
    }

    private UiBounds headerBounds() {
        return UiBounds.of(bounds.x(), bounds.y(), bounds.width(), HEADER_H);
    }

    private UiBounds svBounds() {
        return UiBounds.of(bounds.x() + 8, bounds.y() + 24, SV_W, SV_H);
    }

    private UiBounds hueBounds() {
        return UiBounds.of(bounds.x() + 116, bounds.y() + 24, HUE_W, HUE_H);
    }

    private UiBounds redBounds() {
        return UiBounds.of(bounds.x() + 24, bounds.y() + 114, 156, 13);
    }

    private UiBounds greenBounds() {
        return UiBounds.of(bounds.x() + 24, bounds.y() + 132, 156, 13);
    }

    private UiBounds blueBounds() {
        return UiBounds.of(bounds.x() + 24, bounds.y() + 150, 156, 13);
    }

    private UiBounds hexBounds() {
        return UiBounds.of(bounds.x() + 158, bounds.y() + 61, 70, 17);
    }

    private UiBounds saveBounds() {
        return UiBounds.of(bounds.x() + 8, bounds.bottom() - 24, 68, 17);
    }

    private UiBounds resetBounds() {
        return UiBounds.of(bounds.x() + 82, bounds.bottom() - 24, 68, 17);
    }

    private UiBounds cancelBounds() {
        return UiBounds.of(bounds.x() + 156, bounds.bottom() - 24, 80, 17);
    }

    private static boolean isHex(char chr) {
        return (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static GradientTexture hueTexture() {
        if (hueTexture == null) {
            hueTexture = GradientTexture.create("color_picker_hue", HUE_W, HUE_H);
            hueTexture.writeHue();
        }
        return hueTexture;
    }

    private static GradientTexture saturationValueTexture(float hue) {
        int bucket = Math.round(clamp01(hue) * HUE_BUCKETS);
        if (saturationValueTexture == null) {
            saturationValueTexture = GradientTexture.create("color_picker_sv", SV_W, SV_H);
            saturationValueHueBucket = Integer.MIN_VALUE;
        }
        if (saturationValueHueBucket != bucket) {
            saturationValueTexture.writeSaturationValue(bucket / (float) HUE_BUCKETS);
            saturationValueHueBucket = bucket;
        }
        return saturationValueTexture;
    }

    private enum DragTarget {
        NONE,
        SV,
        HUE,
        RED,
        GREEN,
        BLUE,
        HEX
    }

    private static final class GradientTexture extends AbstractTexture {
        private final Identifier id;
        private final NativeImage pixels;
        private final int width;
        private final int height;

        private static GradientTexture create(String name, int width, int height) {
            Identifier id = Identifier.fromNamespaceAndPath("autismclient", "dynamic/ui/" + name);
            GradientTexture texture = new GradientTexture(id, name, width, height);
            MC.getTextureManager().register(id, texture);
            return texture;
        }

        private GradientTexture(Identifier id, String name, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.pixels = new NativeImage(width, height, true);
            this.texture = RenderSystem.getDevice().createTexture("AUTISM " + name, 5, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
        }

        private void writeHue() {
            for (int y = 0; y < height; y++) {
                float h = height <= 1 ? 0.0f : 1.0f - (y / (float) (height - 1));
                int color = 0xFF000000 | (java.awt.Color.HSBtoRGB(h, 1.0f, 1.0f) & 0x00FFFFFF);
                for (int x = 0; x < width; x++) {
                    pixels.setPixel(x, y, color);
                }
            }
            upload();
        }

        private void writeSaturationValue(float hue) {
            for (int x = 0; x < width; x++) {
                float s = width <= 1 ? 0.0f : x / (float) (width - 1);
                for (int y = 0; y < height; y++) {
                    float v = height <= 1 ? 0.0f : 1.0f - (y / (float) (height - 1));
                    pixels.setPixel(x, y, 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, s, v) & 0x00FFFFFF));
                }
            }
            upload();
        }

        private void upload() {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
        }

        private void blit(UiContext context, UiBounds area) {
            context.graphics().blit(RenderPipelines.GUI_TEXTURED, id, area.x(), area.y(), 0.0F, 0.0F, area.width(), area.height(),
                width, height, width, height, 0xFFFFFFFF);
        }

        @Override
        public void close() {
            pixels.close();
            super.close();
        }
    }
}
