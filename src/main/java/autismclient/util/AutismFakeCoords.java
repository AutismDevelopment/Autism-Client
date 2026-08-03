package autismclient.util;

import java.security.SecureRandom;

public final class AutismFakeCoords {
    private AutismFakeCoords() {}

    public enum Mode { OFFSET, SCALED, ROTATED, SCRAMBLED, FROZEN, CUSTOM }

    private static final SecureRandom RNG = new SecureRandom();
    private static final long REGION = 1024;
    private static final long SPAN = 20_000_000L;

    private static volatile boolean enabled;
    private static volatile Mode mode = Mode.OFFSET;
    private static volatile boolean fakeY = false;

    private static double offX, offY, offZ;
    private static double sclX, sclZ, pivX, pivZ;
    private static double rotAngle, rotPivX, rotPivZ;
    private static long scrambleKey;
    private static double frozenX, frozenY, frozenZ;

    private static volatile double customX, customY, customZ;
    private static double anchorX, anchorY, anchorZ;
    private static volatile double custOffX, custOffY, custOffZ;

    public static boolean active() { return enabled; }

    public static void enable(Mode m, boolean spoofY) {
        mode = m == null ? Mode.OFFSET : m;
        fakeY = spoofY;
        regenerate();
        enabled = true;
    }

    public static void disable() { enabled = false; }

    public static void setMode(Mode m) { if (m != null) mode = m; }

    public static void setFakeY(boolean spoofY) { fakeY = spoofY; }

    public static void regenerate() {
        offX = randSpan(); offY = randRange(-120, 200); offZ = randSpan();
        sclX = randScale(); sclZ = randScale(); pivX = randSpan(); pivZ = randSpan();
        rotAngle = RNG.nextDouble() * Math.PI * 2.0; rotPivX = randSpan(); rotPivZ = randSpan();
        scrambleKey = RNG.nextLong();
        frozenX = randSpan(); frozenY = randRange(-50, 320); frozenZ = randSpan();
    }

    public static void setCustom(double cx, double cy, double cz) {
        customX = cx; customY = cy; customZ = cz;
        custOffX = customX - anchorX; custOffY = customY - anchorY; custOffZ = customZ - anchorZ;
    }

    public static void anchorTo(double realX, double realY, double realZ) {
        anchorX = realX; anchorY = realY; anchorZ = realZ;
        custOffX = customX - anchorX; custOffY = customY - anchorY; custOffZ = customZ - anchorZ;
    }

    public static double[] apply(double x, double y, double z) {
        if (!enabled) return new double[]{x, y, z};
        double fx, fy, fz;
        switch (mode) {
            case SCALED -> { fx = (x - pivX) * sclX + offX; fy = y + offY; fz = (z - pivZ) * sclZ + offZ; }
            case ROTATED -> {
                double dx = x - rotPivX, dz = z - rotPivZ, c = Math.cos(rotAngle), s = Math.sin(rotAngle);
                fx = dx * c - dz * s + offX; fz = dx * s + dz * c + offZ; fy = y + offY;
            }
            case SCRAMBLED -> {
                long rx = Math.floorDiv((long) Math.floor(x), REGION);
                long ry = Math.floorDiv((long) Math.floor(y), REGION);
                long rz = Math.floorDiv((long) Math.floor(z), REGION);
                fx = x + regionOffset(scrambleKey, rx, rz, 0);
                fz = z + regionOffset(scrambleKey, rx, rz, 1);
                fy = y + regionOffset(scrambleKey ^ 0x9E3779B97F4A7C15L, ry, 0, 2);
            }
            case FROZEN -> { fx = frozenX; fy = frozenY; fz = frozenZ; }
            case CUSTOM -> { fx = x + custOffX; fy = y + custOffY; fz = z + custOffZ; }
            default  -> { fx = x + offX; fy = y + offY; fz = z + offZ; }
        }
        if (!fakeY && mode != Mode.FROZEN) fy = y;
        return new double[]{fx, fy, fz};
    }

    private static double regionOffset(long key, long a, long b, int axis) {
        long h = key + 0x9E3779B97F4A7C15L * (a + 1);
        h ^= h >>> 29; h = h * 0xBF58476D1CE4E5B9L + (b + 1);
        h ^= h >>> 27; h = h * 0x94D049BB133111EBL + (axis + 1);
        h ^= h >>> 31;
        return Math.floorMod(h, SPAN) - SPAN / 2.0;
    }

    private static double randSpan() { return RNG.nextDouble() * SPAN - SPAN / 2.0; }
    private static double randRange(int lo, int hi) { return lo + RNG.nextInt(hi - lo); }
    private static double randScale() {
        double s = 0.5 + RNG.nextDouble() * 1.5;
        return RNG.nextBoolean() ? s : -s;
    }
}
