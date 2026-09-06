package autismclient.util.multi.captcha;

import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

public final class CaptchaMapImage {

    public static final int SIZE = 128;

    public final int[] rgb = new int[SIZE * SIZE];

    public static CaptchaMapImage fromMapColors(byte[] colors) {
        if (colors == null || colors.length != SIZE * SIZE) return null;
        CaptchaMapImage img = new CaptchaMapImage();
        for (int i = 0; i < colors.length; i++) {
            img.rgb[i] = MapColor.getColorFromPackedId(colors[i] & 0xFF) & 0xFFFFFF;
        }
        return img;
    }

    static int r(int c) { return (c >> 16) & 0xFF; }
    static int g(int c) { return (c >> 8) & 0xFF; }
    static int b(int c) { return c & 0xFF; }

    static int brightness(int c) { return (r(c) + g(c) + b(c)) / 3; }

    static int saturation(int c) {
        int mx = Math.max(r(c), Math.max(g(c), b(c)));
        int mn = Math.min(r(c), Math.min(g(c), b(c)));
        return mx - mn;
    }

    static boolean[] medianDenoise(boolean[] mask, int w, int h) {
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int on = 0, total = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        total++;
                        if (mask[ny * w + nx]) on++;
                    }
                }
                out[y * w + x] = on * 2 > total;
            }
        }
        return out;
    }

    static boolean[] erode(boolean[] mask, int w, int h) {
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[y * w + x]) continue;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) continue;
                if (mask[y * w + x - 1] && mask[y * w + x + 1] && mask[(y - 1) * w + x] && mask[(y + 1) * w + x]) {
                    out[y * w + x] = true;
                }
            }
        }
        return out;
    }

    static boolean[] dilate(boolean[] mask, int w, int h) {
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y * w + x]
                    || (x > 0 && mask[y * w + x - 1]) || (x < w - 1 && mask[y * w + x + 1])
                    || (y > 0 && mask[(y - 1) * w + x]) || (y < h - 1 && mask[(y + 1) * w + x])) {
                    out[y * w + x] = true;
                }
            }
        }
        return out;
    }

    static boolean[] open(boolean[] mask, int w, int h) {
        return dilate(erode(mask, w, h), w, h);
    }

    public static final class Blob {
        public int minX, minY, maxX, maxY, area;
        public boolean[] localMask;
        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
    }

    static List<Blob> connectedComponents(boolean[] mask, int w, int h) {
        List<Blob> blobs = new ArrayList<>();
        int[] label = new int[mask.length];
        int[] stack = new int[mask.length];
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || label[start] != 0) continue;
            int sp = 0;
            stack[sp++] = start;
            label[start] = 1;
            Blob blob = new Blob();
            blob.minX = Integer.MAX_VALUE; blob.minY = Integer.MAX_VALUE;
            blob.maxX = Integer.MIN_VALUE; blob.maxY = Integer.MIN_VALUE;
            List<Integer> pixels = new ArrayList<>();
            while (sp > 0) {
                int p = stack[--sp];
                int px = p % w, py = p / w;
                pixels.add(p);
                blob.area++;
                if (px < blob.minX) blob.minX = px;
                if (px > blob.maxX) blob.maxX = px;
                if (py < blob.minY) blob.minY = py;
                if (py > blob.maxY) blob.maxY = py;
                sp = tryPush(mask, label, stack, sp, w, h, px - 1, py);
                sp = tryPush(mask, label, stack, sp, w, h, px + 1, py);
                sp = tryPush(mask, label, stack, sp, w, h, px, py - 1);
                sp = tryPush(mask, label, stack, sp, w, h, px, py + 1);
            }
            int bw = blob.width(), bh = blob.height();
            blob.localMask = new boolean[bw * bh];
            for (int p : pixels) {
                int px = p % w - blob.minX, py = p / w - blob.minY;
                blob.localMask[py * bw + px] = true;
            }
            blobs.add(blob);
        }
        return blobs;
    }

    private static int tryPush(boolean[] mask, int[] label, int[] stack, int sp, int w, int h, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return sp;
        int idx = y * w + x;
        if (!mask[idx] || label[idx] != 0) return sp;
        label[idx] = 1;
        stack[sp++] = idx;
        return sp;
    }
}
