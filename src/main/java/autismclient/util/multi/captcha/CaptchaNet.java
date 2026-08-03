package autismclient.util.multi.captcha;

import java.io.DataInputStream;
import java.io.InputStream;

public final class CaptchaNet {

    private static final int MAGIC_MLP = 0x43474E31;
    private static final int MAGIC_CNN = 0x43474E32;
    static final int GRID_W = CaptchaGlyphMatcher.GRID_W;
    static final int GRID_H = CaptchaGlyphMatcher.GRID_H;

    private final boolean cnn;
    private final int outputs;

    private int dim, hid;
    private float[] w1, b1, w2, b2;

    private static final int C1 = 24, C2 = 48, K = 3, FC1 = 96;
    private static final int PH = GRID_H / 4, PW = GRID_W / 4;
    private float[] c1w, c1b, c2w, c2b, f1w, f1b, f2w, f2b;

    private CaptchaNet(boolean cnn, int outputs) {
        this.cnn = cnn;
        this.outputs = outputs;
    }

    public int outputs() { return outputs; }

    public int inputDim() { return GRID_W * GRID_H; }

    public static CaptchaNet loadBundled() {
        try (InputStream is = CaptchaNet.class.getResourceAsStream("/assets/autismclient/captcha/glyphnet.bin")) {
            if (is == null) return null;
            DataInputStream in = new DataInputStream(is);
            int magic = in.readInt();
            if (magic == MAGIC_CNN) return loadCnn(in);
            if (magic == MAGIC_MLP) return loadMlp(in);
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CaptchaNet loadCnn(DataInputStream in) throws java.io.IOException {
        int classes = in.readInt();
        if (classes <= 0 || classes > 256) return null;
        CaptchaNet n = new CaptchaNet(true, classes);
        n.c1w = read(in, C1 * 1 * K * K); n.c1b = read(in, C1);
        n.c2w = read(in, C2 * C1 * K * K); n.c2b = read(in, C2);
        n.f1w = read(in, FC1 * C2 * PH * PW); n.f1b = read(in, FC1);
        n.f2w = read(in, classes * FC1); n.f2b = read(in, classes);
        return n;
    }

    private static CaptchaNet loadMlp(DataInputStream in) throws java.io.IOException {
        int dim = in.readInt(), hid = in.readInt(), out = in.readInt();
        if (dim <= 0 || hid <= 0 || out <= 0 || dim > 1 << 20 || hid > 1 << 16 || out > 256) return null;
        CaptchaNet n = new CaptchaNet(false, out);
        n.dim = dim; n.hid = hid;
        n.w1 = read(in, dim * hid); n.b1 = read(in, hid);
        n.w2 = read(in, hid * out); n.b2 = read(in, out);
        return n;
    }

    private static float[] read(DataInputStream in, int n) throws java.io.IOException {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = in.readFloat();
        return a;
    }

    public float[] probs(float[] x) {
        if (x == null || x.length != GRID_W * GRID_H) return null;
        return cnn ? forwardCnn(x) : forwardMlp(x);
    }

    private float[] forwardCnn(float[] x) {

        float[] a1 = new float[C1 * GRID_H * GRID_W];
        conv(x, 1, GRID_H, GRID_W, c1w, c1b, C1, a1);
        relu(a1);

        int h1 = GRID_H / 2, w1c = GRID_W / 2;
        float[] p1 = new float[C1 * h1 * w1c];
        pool(a1, C1, GRID_H, GRID_W, p1);

        float[] a2 = new float[C2 * h1 * w1c];
        conv(p1, C1, h1, w1c, c2w, c2b, C2, a2);
        relu(a2);

        float[] p2 = new float[C2 * PH * PW];
        pool(a2, C2, h1, w1c, p2);

        float[] h = new float[FC1];
        for (int o = 0; o < FC1; o++) {
            float s = f1b[o];
            int base = o * p2.length;
            for (int i = 0; i < p2.length; i++) s += f1w[base + i] * p2[i];
            h[o] = s > 0 ? s : 0;
        }

        float[] logit = new float[outputs];
        for (int o = 0; o < outputs; o++) {
            float s = f2b[o];
            int base = o * FC1;
            for (int j = 0; j < FC1; j++) s += f2w[base + j] * h[j];
            logit[o] = s;
        }
        return softmax(logit);
    }

    private static void conv(float[] in, int inC, int H, int W, float[] w, float[] b, int outC, float[] out) {
        for (int oc = 0; oc < outC; oc++) {
            int obase = oc * H * W;
            float bias = b[oc];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float s = bias;
                    for (int ic = 0; ic < inC; ic++) {
                        int ibase = ic * H * W;
                        int wbase = ((oc * inC) + ic) * K * K;
                        for (int ky = 0; ky < K; ky++) {
                            int iy = y + ky - 1;
                            if (iy < 0 || iy >= H) continue;
                            for (int kx = 0; kx < K; kx++) {
                                int ix = x + kx - 1;
                                if (ix < 0 || ix >= W) continue;
                                s += in[ibase + iy * W + ix] * w[wbase + ky * K + kx];
                            }
                        }
                    }
                    out[obase + y * W + x] = s;
                }
            }
        }
    }

    private static void pool(float[] in, int C, int H, int W, float[] out) {
        int oh = H / 2, ow = W / 2;
        for (int c = 0; c < C; c++) {
            int ibase = c * H * W, obase = c * oh * ow;
            for (int y = 0; y < oh; y++) {
                for (int x = 0; x < ow; x++) {
                    int iy = y * 2, ix = x * 2;
                    float m = in[ibase + iy * W + ix];
                    m = Math.max(m, in[ibase + iy * W + ix + 1]);
                    m = Math.max(m, in[ibase + (iy + 1) * W + ix]);
                    m = Math.max(m, in[ibase + (iy + 1) * W + ix + 1]);
                    out[obase + y * ow + x] = m;
                }
            }
        }
    }

    private static void relu(float[] a) {
        for (int i = 0; i < a.length; i++) if (a[i] < 0) a[i] = 0;
    }

    private float[] forwardMlp(float[] x) {
        float[] h = new float[hid];
        for (int j = 0; j < hid; j++) h[j] = b1[j];
        for (int i = 0; i < dim; i++) {
            float xi = x[i];
            if (xi == 0f) continue;
            int base = i * hid;
            for (int j = 0; j < hid; j++) h[j] += xi * w1[base + j];
        }
        for (int j = 0; j < hid; j++) if (h[j] < 0f) h[j] = 0f;
        float[] logit = new float[outputs];
        for (int o = 0; o < outputs; o++) logit[o] = b2[o];
        for (int j = 0; j < hid; j++) {
            float hj = h[j];
            if (hj == 0f) continue;
            int base = j * outputs;
            for (int o = 0; o < outputs; o++) logit[o] += hj * w2[base + o];
        }
        return softmax(logit);
    }

    private static float[] softmax(float[] logit) {
        float max = Float.NEGATIVE_INFINITY;
        for (float l : logit) if (l > max) max = l;
        float sum = 0f;
        for (int o = 0; o < logit.length; o++) { logit[o] = (float) Math.exp(logit[o] - max); sum += logit[o]; }
        if (sum <= 0f) return null;
        for (int o = 0; o < logit.length; o++) logit[o] /= sum;
        return logit;
    }
}
