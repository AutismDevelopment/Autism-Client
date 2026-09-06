package autismclient.util;

public final class AutismCpsTracker {
    private static final long WINDOW_NS = 1_000_000_000L;
    private static final int CAP = 48;

    private static final long[] LEFT = new long[CAP];
    private static final long[] RIGHT = new long[CAP];
    private static int leftIdx;
    private static int rightIdx;

    private AutismCpsTracker() {}

    public static void recordLeft() {
        LEFT[leftIdx] = System.nanoTime();
        leftIdx = (leftIdx + 1) % CAP;
    }

    public static void recordRight() {
        RIGHT[rightIdx] = System.nanoTime();
        rightIdx = (rightIdx + 1) % CAP;
    }

    public static int leftCps() {
        return count(LEFT);
    }

    public static int rightCps() {
        return count(RIGHT);
    }

    public static int totalCps() {
        return leftCps() + rightCps();
    }

    public static boolean leftActiveRecently(long ms) {
        return activeRecently(LEFT, leftIdx, ms);
    }

    public static boolean rightActiveRecently(long ms) {
        return activeRecently(RIGHT, rightIdx, ms);
    }

    private static boolean activeRecently(long[] ring, int idx, long ms) {
        long newest = ring[(idx + CAP - 1) % CAP];
        return newest != 0L && newest > System.nanoTime() - ms * 1_000_000L;
    }

    private static int count(long[] ring) {
        long cutoff = System.nanoTime() - WINDOW_NS;
        int c = 0;
        for (int i = 0; i < CAP; i++) {
            if (ring[i] > cutoff) c++;
        }
        return c;
    }
}
