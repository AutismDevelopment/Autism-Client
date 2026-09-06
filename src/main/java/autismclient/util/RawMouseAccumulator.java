package autismclient.util;

public final class RawMouseAccumulator {
    private double queuedX;
    private double queuedY;
    private double residualX;
    private double residualY;

    public void queue(double x, double y) {
        if (Double.isFinite(x)) queuedX += x;
        if (Double.isFinite(y)) queuedY += y;
    }

    public void replaceQueued(double x, double y) {
        queuedX = Double.isFinite(x) ? x : 0.0D;
        queuedY = Double.isFinite(y) ? y : 0.0D;
    }

    public Counts consume() {
        double totalX = queuedX + residualX;
        double totalY = queuedY + residualY;
        long wholeX = wholeCounts(totalX);
        long wholeY = wholeCounts(totalY);
        residualX = totalX - wholeX;
        residualY = totalY - wholeY;
        queuedX = 0.0;
        queuedY = 0.0;
        return new Counts(wholeX, wholeY);
    }

    public void clear() {
        queuedX = 0.0;
        queuedY = 0.0;
        residualX = 0.0;
        residualY = 0.0;
    }

    private static long wholeCounts(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < 1.0) return 0L;
        return value > 0.0 ? (long) Math.floor(value) : (long) Math.ceil(value);
    }

    public record Counts(long x, long y) {
    }
}
