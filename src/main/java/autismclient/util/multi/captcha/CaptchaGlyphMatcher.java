package autismclient.util.multi.captcha;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CaptchaGlyphMatcher {

    static final int GRID_W = 32;
    static final int GRID_H = 48;

    private static final double HB_HOLE = 0.12;
    private static final int MIN_HOLE_AREA = 22;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private record Template(char ch, float[] cc, double norm, double aspect, boolean hole) {
    }

    private record Rendered(float[] cov, boolean[] gridMask, double aspect) {
    }

    private final List<Template> templates = new ArrayList<>();
    private final boolean heightAware;
    private final String charset;

    private CaptchaNet net;

    public CaptchaGlyphMatcher(Font font, String charset, double[] shears, boolean heightAware) {
        this.heightAware = heightAware;
        this.charset = charset;
        for (int i = 0; i < charset.length(); i++) {
            char ch = charset.charAt(i);
            for (double shear : shears) {
                Rendered r = renderNormalized(font, ch, shear);
                if (r != null && r.cov() != null) {
                    float[] cc = center(r.cov());
                    double norm = l2(cc);
                    if (norm > 1e-6) templates.add(new Template(ch, cc, norm, r.aspect(), hasEnclosedHole(r.gridMask())));
                }
            }
        }
    }

    public boolean heightAware() { return heightAware; }

    public boolean hasNet() { return net != null; }

    public float[] classify(float[] cov) {
        return net == null ? null : net.probs(cov);
    }

    public void setNet(CaptchaNet net) {
        if (net != null && (net.outputs() == charset.length() || net.outputs() == charset.length() + 1)) this.net = net;
    }

    private Rendered renderNormalized(Font font, char ch, double shear) {
        GlyphVector gv = font.createGlyphVector(FRC, String.valueOf(ch));
        java.awt.Shape outline = gv.getOutline();
        if (Math.abs(shear) > 1e-6) {
            AffineTransform sh = AffineTransform.getShearInstance(shear, shear);
            outline = sh.createTransformedShape(outline);
        }
        Rectangle2D b = outline.getBounds2D();
        if (b.getWidth() < 1 || b.getHeight() < 1) return null;
        double aspect = b.getWidth() / b.getHeight();

        int rw = (int) Math.ceil(b.getWidth()) + 2;
        int rh = (int) Math.ceil(b.getHeight()) + 2;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(rw, rh, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, rw, rh);
        g.translate(1 - b.getX(), 1 - b.getY());
        g.setColor(java.awt.Color.WHITE);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(outline);
        g.dispose();
        int[] gray = new int[rw * rh];
        boolean[] src = new boolean[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int v = img.getRGB(x, y) & 0xFF;
                gray[y * rw + x] = v;
                src[y * rw + x] = v > 96;
            }
        }
        return new Rendered(coverageFromGray(gray, rw, rh), maskToGrid(src, rw, rh), aspect);
    }

    static float[] coverageFromGray(int[] gray, int w, int h) {
        float[] out = new float[GRID_W * GRID_H];
        if (w <= 0 || h <= 0) return out;
        for (int gy = 0; gy < GRID_H; gy++) {
            int ya = gy * h / GRID_H, yb = Math.max(ya + 1, (gy + 1) * h / GRID_H);
            for (int gx = 0; gx < GRID_W; gx++) {
                int xa = gx * w / GRID_W, xb = Math.max(xa + 1, (gx + 1) * w / GRID_W);
                double sum = 0;
                int tot = 0;
                for (int y = ya; y < yb && y < h; y++) {
                    for (int x = xa; x < xb && x < w; x++) { sum += gray[y * w + x]; tot++; }
                }
                out[gy * GRID_W + gx] = tot == 0 ? 0f : (float) (sum / (tot * 255.0));
            }
        }
        return out;
    }

    static float[] coverageFromBinary(boolean[] src, int w, int h) {
        float[] out = new float[GRID_W * GRID_H];
        if (w <= 0 || h <= 0) return out;
        for (int gy = 0; gy < GRID_H; gy++) {
            int ya = gy * h / GRID_H, yb = Math.max(ya + 1, (gy + 1) * h / GRID_H);
            for (int gx = 0; gx < GRID_W; gx++) {
                int xa = gx * w / GRID_W, xb = Math.max(xa + 1, (gx + 1) * w / GRID_W);
                int cnt = 0, tot = 0;
                for (int y = ya; y < yb && y < h; y++) {
                    for (int x = xa; x < xb && x < w; x++) { tot++; if (src[y * w + x]) cnt++; }
                }
                out[gy * GRID_W + gx] = tot == 0 ? 0f : (float) cnt / tot;
            }
        }
        return out;
    }

    static boolean[] maskToGrid(boolean[] src, int w, int h) {
        boolean[] out = new boolean[GRID_W * GRID_H];
        if (w <= 0 || h <= 0) return out;
        for (int gy = 0; gy < GRID_H; gy++) {
            int sy = (int) ((gy + 0.5) * h / GRID_H);
            if (sy >= h) sy = h - 1;
            for (int gx = 0; gx < GRID_W; gx++) {
                int sx = (int) ((gx + 0.5) * w / GRID_W);
                if (sx >= w) sx = w - 1;
                out[gy * GRID_W + gx] = src[sy * w + sx];
            }
        }
        return out;
    }

    private static float[] center(float[] v) {
        double mean = 0;
        for (float f : v) mean += f;
        mean /= v.length;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] - mean);
        return out;
    }

    private static double l2(float[] v) {
        double s = 0;
        for (float f : v) s += (double) f * f;
        return Math.sqrt(s);
    }

    public Result match(boolean[] cellMask, int w, int h) {
        List<Result> ranked = matchTopK(cellMask, w, h, 1);
        return ranked.isEmpty() ? new Result('\0', 0.0) : ranked.get(0);
    }

    public List<Result> matchTopK(boolean[] cellMask, int w, int h, int k) {
        double cellAspect = h > 0 ? (double) w / h : 1.0;
        float[] cov = coverageFromBinary(cellMask, w, h);
        if (net != null) {
            float[] probs = net.probs(cov);
            if (probs != null) {

                List<Result> out = new ArrayList<>(charset.length());
                for (int i = 0; i < charset.length(); i++) out.add(new Result(charset.charAt(i), probs[i]));
                out.sort((a, b) -> Double.compare(b.score(), a.score()));
                return out.size() > k ? out.subList(0, k) : out;
            }
        }
        float[] cc = center(cov);
        double cellNorm = l2(cc);
        if (cellNorm < 1e-6) return List.of();

        boolean cellHole = heightAware && hasEnclosedHoleSealed(cellMask, w, h, Math.max(14, (int) (0.03 * w * h)));
        Map<Character, Double> bestPerChar = new HashMap<>();
        for (Template t : templates) {
            double dot = 0;
            for (int i = 0; i < cc.length; i++) dot += (double) cc[i] * t.cc[i];
            double corr = dot / (cellNorm * t.norm);
            double aspectSim = aspectSimilarity(cellAspect, t.aspect);
            double score = Math.max(0, corr) * (0.65 + 0.35 * aspectSim);
            if (heightAware) {
                score += (t.hole == cellHole) ? HB_HOLE : -HB_HOLE;
            }
            Double prev = bestPerChar.get(t.ch);
            if (prev == null || score > prev) bestPerChar.put(t.ch, score);
        }
        List<Result> out = new ArrayList<>();
        for (Map.Entry<Character, Double> e : bestPerChar.entrySet()) out.add(new Result(e.getKey(), e.getValue()));
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out.size() > k ? out.subList(0, k) : out;
    }

    static boolean hasEnclosedHole(boolean[] mask) {
        return hasEnclosedHole(mask, GRID_W, GRID_H, MIN_HOLE_AREA);
    }

    static boolean hasEnclosedHoleSealed(boolean[] mask, int w, int h, int minArea) {

        boolean[] sealed = CaptchaMapImage.dilate(mask, w, h);
        return hasEnclosedHole(sealed, w, h, minArea);
    }

    static boolean hasEnclosedHole(boolean[] mask, int w, int h, int minArea) {
        int n = w * h;
        boolean[] reached = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;
        for (int x = 0; x < w; x++) {
            int top = x, bot = (h - 1) * w + x;
            if (!mask[top] && !reached[top]) { reached[top] = true; stack[sp++] = top; }
            if (!mask[bot] && !reached[bot]) { reached[bot] = true; stack[sp++] = bot; }
        }
        for (int y = 0; y < h; y++) {
            int left = y * w, right = y * w + w - 1;
            if (!mask[left] && !reached[left]) { reached[left] = true; stack[sp++] = left; }
            if (!mask[right] && !reached[right]) { reached[right] = true; stack[sp++] = right; }
        }
        while (sp > 0) {
            int p = stack[--sp];
            int px = p % w, py = p / w;
            if (px > 0) sp = pushBg(mask, reached, stack, sp, p - 1);
            if (px < w - 1) sp = pushBg(mask, reached, stack, sp, p + 1);
            if (py > 0) sp = pushBg(mask, reached, stack, sp, p - w);
            if (py < h - 1) sp = pushBg(mask, reached, stack, sp, p + w);
        }
        int enclosed = 0;
        for (int i = 0; i < n; i++) if (!mask[i] && !reached[i]) enclosed++;
        return enclosed >= minArea;
    }

    private static int pushBg(boolean[] mask, boolean[] reached, int[] stack, int sp, int idx) {
        if (!mask[idx] && !reached[idx]) { reached[idx] = true; stack[sp++] = idx; }
        return sp;
    }

    private static double aspectSimilarity(double a, double b) {
        if (a <= 0 || b <= 0) return 1.0;
        return Math.min(a, b) / Math.max(a, b);
    }

    public record Result(char ch, double score) {
    }
}
