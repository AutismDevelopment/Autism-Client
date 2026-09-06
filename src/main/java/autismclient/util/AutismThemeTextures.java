package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.util.AutismTheme.Channel;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AutismThemeTextures {
    private static final Minecraft MC = Minecraft.getInstance();

    public static final Identifier PANORAMA_LOCATION =
        Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama");
    private static final Identifier PANORAMA_OVERLAY_SRC =
        Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama_overlay.png");
    private static final Identifier PANORAMA_OVERLAY_DYN =
        Identifier.fromNamespaceAndPath("autismclient", "dynamic/theme/panorama_overlay");

    private record Recolored(Identifier id, int generation) {}

    private record ColorRecolored(Identifier id, int color) {}

    private static final Map<Identifier, Recolored> UI_IDS = new HashMap<>();

    private static final Map<Identifier, ColorRecolored> COLOR_IDS = new HashMap<>();
    private static final Set<Identifier> COLOR_REBUILDING = new HashSet<>();
    private static final Set<Identifier> REBUILDING = new HashSet<>();

    private static final Map<Identifier, Integer> FAILED = new HashMap<>();
    private static final Map<Identifier, Identifier> WHITE_IDS = new HashMap<>();
    private static int themeGeneration;
    private static RecoloredCubeMapTexture panorama;
    private static volatile boolean panoramaAvailable;
    private static AbstractTexture overlayTexture;
    private static boolean overlayBuilt;

    private AutismThemeTextures() {}

    public static Identifier recolored(Identifier source, Channel ch) {
        if (source == null) return null;
        AutismTheme.State st = AutismTheme.active();
        if (!st.isActive(ch)) return source;
        Recolored cached = UI_IDS.get(source);
        if (cached != null && cached.generation == themeGeneration) return cached.id;
        Integer failedGen = FAILED.get(source);
        if (failedGen != null && failedGen.intValue() == themeGeneration) {

            return cached != null ? cached.id : source;
        }

        if (cached == null && !REBUILDING.contains(source)) {
            Identifier built = buildRecolorNow(source, ch, st);
            if (built != null) return built;

        }
        kickRecolorRebuild(source, ch, st);
        return cached != null ? cached.id : source;
    }

    public static Identifier recoloredTo(Identifier source, int targetColor) {
        if (source == null) return null;
        ColorRecolored cached = COLOR_IDS.get(source);
        if (cached != null && cached.color == targetColor) return cached.id;
        kickColorRebuild(source, targetColor);
        return cached != null ? cached.id : source;
    }

    private static void kickColorRebuild(Identifier source, int targetColor) {
        if (!COLOR_REBUILDING.add(source)) return;
        byte[] png = readResourceBytes(source);
        if (png == null) {
            COLOR_REBUILDING.remove(source);
            return;
        }
        float[] hsb = java.awt.Color.RGBtoHSB((targetColor >> 16) & 0xFF, (targetColor >> 8) & 0xFF, targetColor & 0xFF, null);
        float targetHue = hsb[0];
        float targetSat = hsb[1];
        AutismBackgroundTasks.runTracked("skybox-recolor", () -> {
            NativeImage recolored = null;
            try (NativeImage src = NativeImage.read(png)) {
                recolored = src.mappedCopy(argb -> AutismTheme.recolorImagePixelTo(argb, targetHue, targetSat));
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Skybox recolor failed for {}", source, t);
            }
            NativeImage result = recolored;
            MC.execute(() -> {
                COLOR_REBUILDING.remove(source);
                if (result == null) return;
                Identifier id = colorDerivedId(source);
                MC.getTextureManager().register(id, new DynamicTexture(source.toString(), result, FilterMode.LINEAR));
                COLOR_IDS.put(source, new ColorRecolored(id, targetColor));

            });
        });
    }

    private static Identifier colorDerivedId(Identifier source) {
        return Identifier.fromNamespaceAndPath("autismclient",
            "dynamic/skybox/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", ""));
    }

    private static Identifier buildRecolorNow(Identifier source, Channel ch, AutismTheme.State st) {
        byte[] png = readResourceBytes(source);
        if (png == null) return null;
        int generation = themeGeneration;
        try (NativeImage src = NativeImage.read(png)) {
            NativeImage recolored = src.mappedCopy(argb -> AutismTheme.recolorImagePixel(argb, ch, st));
            Identifier id = derivedId(source);
            MC.getTextureManager().register(id, new DynamicTexture(source.toString(), recolored, FilterMode.LINEAR));
            UI_IDS.put(source, new Recolored(id, generation));
            return id;
        } catch (Throwable t) {
            FAILED.put(source, generation);
            AutismClientAddon.LOG.warn("Theme recolor (sync) failed for {} (backing off this theme generation)", source, t);
            return null;
        }
    }

    private static void kickRecolorRebuild(Identifier source, Channel ch, AutismTheme.State st) {
        if (!REBUILDING.add(source)) return;
        int generation = themeGeneration;

        byte[] png = readResourceBytes(source);
        if (png == null) {

            REBUILDING.remove(source);
            return;
        }
        AutismBackgroundTasks.runTracked("theme-recolor", () -> {
            NativeImage recolored = null;
            try (NativeImage src = NativeImage.read(png)) {
                recolored = src.mappedCopy(argb -> AutismTheme.recolorImagePixel(argb, ch, st));
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", source, t);
            }
            NativeImage result = recolored;
            MC.execute(() -> {
                REBUILDING.remove(source);
                if (generation != themeGeneration) {

                    if (result != null) result.close();
                    return;
                }
                if (result == null) {
                    FAILED.put(source, generation);
                    return;
                }
                Identifier id = derivedId(source);
                MC.getTextureManager().register(id, new DynamicTexture(source.toString(), result, FilterMode.LINEAR));
                UI_IDS.put(source, new Recolored(id, generation));
            });
        });
    }

    private static byte[] readResourceBytes(Identifier source) {
        try {
            Optional<Resource> res = MC.getResourceManager().getResource(source);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                return in.readAllBytes();
            }
        } catch (Throwable t) {
            FAILED.put(source, themeGeneration);
            AutismClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", source, t);
            return null;
        }
    }

    public static Identifier whitened(Identifier source) {
        if (source == null) return null;
        Identifier cached = WHITE_IDS.get(source);
        if (cached != null) return cached;
        try {
            TextureContents contents = TextureContents.load(MC.getResourceManager(), source);
            NativeImage src = contents.image();
            NativeImage white = src.mappedCopy(argb -> (argb & 0xFF000000) | 0x00FFFFFF);
            src.close();
            Identifier id = Identifier.fromNamespaceAndPath("autismclient",
                "dynamic/white/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", ""));
            MC.getTextureManager().register(id, new DynamicTexture(source.toString(), white, FilterMode.LINEAR));
            WHITE_IDS.put(source, id);
            return id;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Theme whiten failed for {}", source, t);
            return source;
        }
    }

    private static Identifier derivedId(Identifier source) {
        return Identifier.fromNamespaceAndPath("autismclient",
            "dynamic/theme/" + source.getNamespace() + "/" + source.getPath().replace('/', '_').replace(".png", ""));
    }

    public static void registerPanorama(TextureManager textureManager) {

        try {
            panorama = new RecoloredCubeMapTexture(PANORAMA_LOCATION);
            textureManager.register(PANORAMA_LOCATION, panorama);
        } catch (Throwable t) {
            panoramaAvailable = false;
            AutismClientAddon.LOG.warn("Failed to register themed panorama", t);
        }
    }

    public static boolean isPanoramaAvailable() {
        return panoramaAvailable;
    }

    public static void reloadPanorama() {
        if (panorama == null || !panoramaAvailable) return;
        int generation = themeGeneration;
        Runnable jobDone = autismclient.gui.AutismThemeApplyOverlay.beginJob("Recoloring panorama");

        MC.execute(() -> {
            try {
                if (generation != themeGeneration) return;
                TextureContents contents = panorama.loadContents(MC.getResourceManager());
                try {
                    panorama.apply(contents);
                } catch (Throwable t) {
                    AutismClientAddon.LOG.warn("Failed to upload the recolored panorama (keeping the current one)", t);
                    try { contents.image().close(); } catch (Throwable ignored) {  }
                }
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to recolor themed panorama (keeping the current one)", t);
            } finally {
                jobDone.run();
            }
        });
    }

    public static Identifier panoramaOverlay(Identifier original) {
        AutismTheme.State st = AutismTheme.active();
        if (!st.isActive(Channel.BACKDROP)) return original;
        if (overlayTexture != null) return PANORAMA_OVERLAY_DYN;
        if (!overlayBuilt) {
            overlayBuilt = true;
            kickOverlayBuild(st, () -> {});
        }
        return original;
    }

    private static void kickOverlayBuild(AutismTheme.State st, Runnable jobDone) {
        int generation = themeGeneration;

        byte[] png;
        try {
            Optional<Resource> res = MC.getResourceManager().getResource(PANORAMA_OVERLAY_SRC);
            if (res.isEmpty()) {
                overlayBuilt = false;
                jobDone.run();
                return;
            }
            try (InputStream in = res.get().open()) {
                png = in.readAllBytes();
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Failed to build themed panorama overlay", t);
            jobDone.run();
            return;
        }
        AutismBackgroundTasks.runTracked("theme-overlay", () -> {
            NativeImage recolored = null;
            try (NativeImage src = NativeImage.read(png)) {
                recolored = src.mappedCopy(argb -> AutismTheme.recolorImagePixel(argb, Channel.BACKDROP, st));
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to build themed panorama overlay", t);
            }
            NativeImage result = recolored;
            MC.execute(() -> {
                try {
                    if (generation != themeGeneration) {
                        if (result != null) result.close();
                        return;
                    }
                    if (result == null) return;
                    overlayTexture = new DynamicTexture("panorama_overlay", result, FilterMode.LINEAR);
                    MC.getTextureManager().register(PANORAMA_OVERLAY_DYN, overlayTexture);
                } finally {
                    jobDone.run();
                }
            });
        });
    }

    public static void invalidate() {
        themeGeneration++;
        FAILED.clear();
        overlayBuilt = false;
        overlayTexture = null;
        AutismSvgHudLogo.clear();
        AutismTheme.State st = AutismTheme.active();
        if (st.isActive(Channel.BACKDROP)) {

            overlayBuilt = true;
            kickOverlayBuild(st, autismclient.gui.AutismThemeApplyOverlay.beginJob("Recoloring backdrop"));
        }
        reloadPanorama();
    }

    public static final class Preview implements AutoCloseable {
        private final Channel channel;
        private final NativeImage source;
        private final DynamicTexture texture;
        private final Identifier id;
        private int signature = Integer.MIN_VALUE;

        private final Object lock = new Object();
        private boolean closed;
        private boolean jobRunning;
        private boolean resourcesFreed;
        private AutismTheme.State pendingState;

        private Preview(Channel channel, NativeImage source, DynamicTexture texture, Identifier id) {
            this.channel = channel;
            this.source = source;
            this.texture = texture;
            this.id = id;
        }

        public Identifier id() { return id; }
        public int width() { return source.getWidth(); }
        public int height() { return source.getHeight(); }

        private static int backdropSignature(AutismTheme.State st) {
            int sig = Float.floatToIntBits(st.hueOf(Channel.BACKDROP)) * 31
                + Float.floatToIntBits(st.satOf(Channel.BACKDROP));
            return sig == 0 ? 1 : sig;
        }

        public void update(AutismTheme.State st) {

            int sig = channel == Channel.BACKDROP ? backdropSignature(st) : st.previewSignature(channel);
            if (sig == signature) return;
            signature = sig;
            synchronized (lock) {
                if (closed) return;
                pendingState = st;
                if (jobRunning) return;
                jobRunning = true;
            }
            AutismBackgroundTasks.runTracked("theme-preview", this::recolorPending);
        }

        private void recolorPending() {
            try {
                while (true) {
                    AutismTheme.State st;
                    synchronized (lock) {
                        if (closed || pendingState == null) {
                            jobRunning = false;
                            break;
                        }
                        st = pendingState;
                        pendingState = null;
                    }
                    int w = source.getWidth();
                    int h = source.getHeight();
                    int[] out = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            out[y * w + x] = channel == Channel.BACKDROP
                                ? AutismTheme.recolorImagePixelTo(source.getPixel(x, y),
                                    st.hueOf(Channel.BACKDROP), st.satOf(Channel.BACKDROP))
                                : AutismTheme.recolorImagePixel(source.getPixel(x, y), channel, st);
                        }
                    }
                    MC.execute(() -> {
                        synchronized (lock) {
                            if (closed) return;
                            for (int y = 0; y < h; y++) {
                                for (int x = 0; x < w; x++) texture.pixels.setPixel(x, y, out[y * w + x]);
                            }
                            texture.upload();
                        }
                    });
                }
            } catch (Throwable t) {
                synchronized (lock) {
                    jobRunning = false;
                }
                AutismClientAddon.LOG.error("Theme preview recolor failed", t);
            }
            boolean freeNow;
            synchronized (lock) {
                freeNow = closed;
            }
            if (freeNow) closeResources();
        }

        @Override public void close() {
            synchronized (lock) {
                closed = true;
                pendingState = null;
                if (jobRunning) return;
            }
            closeResources();
        }

        private void closeResources() {
            synchronized (lock) {
                if (resourcesFreed) return;
                resourcesFreed = true;
            }
            try { source.close(); } catch (Throwable ignored) {  }
            try { texture.close(); } catch (Throwable ignored) {  }
        }
    }

    public static Preview preview(Identifier source, Channel channel, int maxDim) {
        try {
            TextureContents contents = TextureContents.load(MC.getResourceManager(), source);
            NativeImage full = contents.image();
            NativeImage scaled = downscale(full, maxDim);
            if (scaled != full) full.close();
            NativeImage work = new NativeImage(scaled.getWidth(), scaled.getHeight(), false);
            for (int y = 0; y < scaled.getHeight(); y++) {
                for (int x = 0; x < scaled.getWidth(); x++) work.setPixel(x, y, scaled.getPixel(x, y));
            }
            Identifier id = Identifier.fromNamespaceAndPath("autismclient",
                "dynamic/theme/preview/" + source.getPath().replace('/', '_').replace(".png", ""));
            DynamicTexture tex = new DynamicTexture("preview " + source, work, FilterMode.LINEAR);
            MC.getTextureManager().register(id, tex);
            return new Preview(channel, scaled, tex, id);
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Failed to build theme preview for {}", source, t);
            return null;
        }
    }

    private static NativeImage downscale(NativeImage src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min(maxDim / (float) w, maxDim / (float) h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        NativeImage out = new NativeImage(nw, nh, false);
        for (int y = 0; y < nh; y++) {
            int sy0 = (int) Math.floor(y * (h / (double) nh));
            int sy1 = Math.min(h, Math.max(sy0 + 1, (int) Math.ceil((y + 1) * (h / (double) nh))));
            for (int x = 0; x < nw; x++) {
                int sx0 = (int) Math.floor(x * (w / (double) nw));
                int sx1 = Math.min(w, Math.max(sx0 + 1, (int) Math.ceil((x + 1) * (w / (double) nw))));
                long aSum = 0, rSum = 0, gSum = 0, bSum = 0;
                int n = 0;
                for (int yy = sy0; yy < sy1; yy++) {
                    for (int xx = sx0; xx < sx1; xx++) {
                        int px = src.getPixel(xx, yy);
                        int pa = (px >>> 24) & 0xFF;
                        aSum += pa;
                        rSum += (long) ((px >>> 16) & 0xFF) * pa;
                        gSum += (long) ((px >>> 8) & 0xFF) * pa;
                        bSum += (long) (px & 0xFF) * pa;
                        n++;
                    }
                }
                if (n == 0) { out.setPixel(x, y, 0); continue; }
                int outA = (int) (aSum / n);
                int outR = aSum == 0 ? 0 : (int) (rSum / aSum);
                int outG = aSum == 0 ? 0 : (int) (gSum / aSum);
                int outB = aSum == 0 ? 0 : (int) (bSum / aSum);
                out.setPixel(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
        return out;
    }

    private static final class DynamicTexture extends AbstractTexture {
        private final NativeImage pixels;

        private DynamicTexture(String label, NativeImage pixels, FilterMode filter) {
            this.pixels = pixels;
            this.texture = RenderSystem.getDevice().createTexture(label, 5, GpuFormat.RGBA8_UNORM,
                pixels.getWidth(), pixels.getHeight(), 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(filter);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            upload();
        }

        private void upload() {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
        }

        @Override public void close() {
            try { pixels.close(); } catch (Throwable ignored) {  }
            super.close();
        }
    }

    private static final class RecoloredCubeMapTexture extends CubeMapTexture {
        private RecoloredCubeMapTexture(Identifier id) { super(id); }

        @Override
        public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
            TextureContents contents = super.loadContents(resourceManager);

            panoramaAvailable = true;
            AutismTheme.State st = AutismTheme.active();

            float hue = st.hueOf(Channel.BACKDROP);
            float sat = st.satOf(Channel.BACKDROP);
            NativeImage img = contents.image();
            int changed = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int before = img.getPixel(x, y);
                    int after = AutismTheme.recolorImagePixelTo(before, hue, sat);
                    if (before != after) {
                        img.setPixel(x, y, after);
                        changed++;
                    }
                }
            }

            AutismClientAddon.LOG.info("Panorama recolored to hue {} sat {}: {} of {} pixels changed",
                String.format(java.util.Locale.ROOT, "%.0f", hue * 360.0f),
                String.format(java.util.Locale.ROOT, "%.2f", sat),
                changed, img.getWidth() * img.getHeight());
            return contents;
        }
    }
}
