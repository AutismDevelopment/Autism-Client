package autismclient.gui.vanillaui;

public record UiBounds(int x, int y, int width, int height) {
    public static UiBounds of(int x, int y, int width, int height) {
        return new UiBounds(x, y, Math.max(0, width), Math.max(0, height));
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public UiBounds inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    public UiBounds inset(int left, int top, int right, int bottom) {
        int nx = x + left;
        int ny = y + top;
        return of(nx, ny, width - left - right, height - top - bottom);
    }

    public UiBounds clampInside(int screenWidth, int screenHeight) {
        int w = Math.min(width, Math.max(0, screenWidth));
        int h = Math.min(height, Math.max(0, screenHeight));
        int nx = clamp(x, 0, Math.max(0, screenWidth - w));
        int ny = clamp(y, 0, Math.max(0, screenHeight - h));
        return of(nx, ny, w, h);
    }

    public UiBounds union(UiBounds other) {
        if (other == null) return this;
        int nx = Math.min(x, other.x);
        int ny = Math.min(y, other.y);
        return of(nx, ny, Math.max(right(), other.right()) - nx, Math.max(bottom(), other.bottom()) - ny);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
