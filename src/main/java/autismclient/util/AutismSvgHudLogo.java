package autismclient.util;

import autismclient.AutismClientAddon;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class AutismSvgHudLogo {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Identifier SVG_RESOURCE = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/hud/autismclient.svg");
    private static final Identifier DYNAMIC_TEXTURE = Identifier.fromNamespaceAndPath("autismclient", "dynamic/hud/autismclient_svg");
    private static final int SVG_RENDER_WIDTH = 2400;
    private static final int SVG_RENDER_HEIGHT = 800;
    private static final int TEXTURE_SCALE = 4;

    private static Entry currentEntry;
    private static int currentTextureWidth = -1;
    private static int currentTextureHeight = -1;
    private static int lastRequestWidth = -1;
    private static int lastRequestHeight = -1;
    private static Entry lastRequestEntry;
    private static boolean disabled;

    private static final int MAX_FAILS = 3;
    private static int failCount;

    private AutismSvgHudLogo() {
    }

    public static boolean render(GuiGraphicsExtractor context, int x, int y, int width, int height, float alpha) {
        if (disabled || width <= 0 || height <= 0) return false;
        Entry entry;
        if (width == lastRequestWidth && height == lastRequestHeight && lastRequestEntry != null) {
            entry = lastRequestEntry;
        } else {
            entry = entry(width, height);
            lastRequestWidth = width;
            lastRequestHeight = height;
            lastRequestEntry = entry;
        }
        if (entry == null) return false;
        context.blit(RenderPipelines.GUI_TEXTURED, entry.id, x, y, 0.0F, 0.0F, width, height,
            entry.textureWidth, entry.textureHeight, entry.textureWidth, entry.textureHeight, ARGB.white(alpha));
        return true;
    }

    public static void clear() {
        releaseCurrent();
        lastRequestWidth = -1;
        lastRequestHeight = -1;
        lastRequestEntry = null;
        disabled = false;
        failCount = 0;
    }

    private static void releaseCurrent() {
        if (currentEntry != null) {
            try { MC.getTextureManager().release(currentEntry.id()); } catch (Throwable ignored) {  }
            currentEntry = null;
            currentTextureWidth = -1;
            currentTextureHeight = -1;
        }
    }

    private static Entry entry(int width, int height) {
        int textureWidth = Math.max(width, width * TEXTURE_SCALE);
        int textureHeight = Math.max(height, height * TEXTURE_SCALE);
        if (currentEntry != null && currentTextureWidth == textureWidth && currentTextureHeight == textureHeight) {
            return currentEntry;
        }
        String key = textureWidth + "x" + textureHeight;
        try {
            NativeImage image = rasterize(textureWidth, textureHeight);
            Identifier id = Identifier.fromNamespaceAndPath(DYNAMIC_TEXTURE.getNamespace(), DYNAMIC_TEXTURE.getPath() + "/" + key);
            MC.getTextureManager().register(id, new LinearDynamicTexture("AUTISM SVG HUD Logo " + key, image));
            releaseCurrent();
            Entry entry = new Entry(id, textureWidth, textureHeight);
            currentEntry = entry;
            currentTextureWidth = textureWidth;
            currentTextureHeight = textureHeight;
            failCount = 0;
            return entry;
        } catch (Throwable t) {
            if (++failCount >= MAX_FAILS) {
                disabled = true;
                AutismClientAddon.LOG.warn("SVG HUD logo disabled after {} failed loads", failCount, t);
            }
            return null;
        }
    }

    private static NativeImage rasterize(int textureWidth, int textureHeight) throws Exception {
        Optional<Resource> resource = MC.getResourceManager().getResource(SVG_RESOURCE);
        if (resource.isEmpty()) throw new IllegalStateException("Missing SVG resource " + SVG_RESOURCE);
        SVGDocument document;
        try (var in = resource.get().open()) {
            document = new SVGLoader().load(in, null, LoaderContext.createDefault());
        }
        if (document == null) throw new IllegalStateException("Unable to parse SVG resource " + SVG_RESOURCE);

        BufferedImage raw = new BufferedImage(SVG_RENDER_WIDTH, SVG_RENDER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = raw.createGraphics();
        configureGraphics(graphics);
        document.render(null, graphics, new ViewBox(SVG_RENDER_WIDTH, SVG_RENDER_HEIGHT));
        graphics.dispose();

        Bounds bounds = alphaBounds(raw);
        BufferedImage cropped = raw.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
        BufferedImage fitted = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fitGraphics = fitted.createGraphics();
        configureGraphics(fitGraphics);
        int targetW = textureWidth;
        int targetH = Math.max(1, Math.round(targetW * (bounds.height / (float) bounds.width)));
        if (targetH > textureHeight) {
            targetH = textureHeight;
            targetW = Math.max(1, Math.round(targetH * (bounds.width / (float) bounds.height)));
        }
        int dx = (textureWidth - targetW) / 2;
        int dy = (textureHeight - targetH) / 2;
        fitGraphics.drawImage(cropped, dx, dy, targetW, targetH, null);
        fitGraphics.dispose();
        return toNativeImage(fitted);
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Bounds alphaBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) <= 2) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX || maxY < minY) return new Bounds(0, 0, image.getWidth(), image.getHeight());
        int pad = 10;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(image.getWidth() - 1, maxX + pad);
        maxY = Math.min(image.getHeight() - 1, maxY + pad);
        return new Bounds(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        AutismTheme.State theme = AutismTheme.active();
        boolean recolor = theme.isActive(AutismTheme.Channel.ACCENT);
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (recolor) argb = AutismTheme.recolorImagePixel(argb, AutismTheme.Channel.ACCENT, theme);
                nativeImage.setPixel(x, y, argb);
            }
        }
        return nativeImage;
    }

    private record Entry(Identifier id, int textureWidth, int textureHeight) {
    }

    private record Bounds(int x, int y, int width, int height) {
    }

    private static final class LinearDynamicTexture extends AbstractTexture {
        private final NativeImage pixels;

        private LinearDynamicTexture(String label, NativeImage pixels) {
            this.pixels = pixels;
            this.texture = RenderSystem.getDevice().createTexture(label, 5, GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
        }

        @Override
        public void close() {
            this.pixels.close();
            super.close();
        }
    }
}
