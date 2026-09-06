package autismclient.gui.multi;

public final class MultiMenuGeometry {
    private MultiMenuGeometry() {
    }

    public static final int ENCH_ROW_X = 54;
    public static final int ENCH_ROW_W = 120;
    public static final int ENCH_ROW_H = 22;
    public static final int MERCHANT_TRADE_X = 0;
    public static final int MERCHANT_TRADE_W = 86;
    public static final int MERCHANT_TRADE_TOP = 8;
    public static final int MERCHANT_TRADE_H = 20;

    public record Layout(int[] x, int[] y, int invTop) {
    }

    public static Layout layout(String typeId, int containerCount, int auxCount) {
        if (containerCount < 0) containerCount = 0;
        Layout typed = typed(strip(typeId), containerCount, Math.max(0, auxCount));
        return typed != null ? typed : generic(containerCount);
    }

    private static Layout typed(String key, int n, int aux) {
        return switch (key) {

            case "furnace", "blast_furnace", "smoker" -> n == 3 ? of(62, 0, 0, 0, 36, 72, 18) : null;

            case "anvil" -> n == 3 ? of(52, 0, 18, 42, 18, 96, 18) : null;

            case "enchantment" -> n == 2 ? of(68, 0, 18, 18, 18) : null;

            case "brewing_stand" -> n == 5 ? of(86, 24, 54, 48, 60, 72, 54, 48, 0, 0, 54) : null;
            case "beacon" -> n == 1 ? of(108, 0, 0) : null;

            case "merchant" -> n == 3
                ? of(Math.max(60, MERCHANT_TRADE_TOP + aux * MERCHANT_TRADE_H + 6), 90, 0, 108, 0, 156, 0) : null;
            case "grindstone" -> n == 3 ? of(62, 0, 0, 0, 36, 72, 18) : null;

            case "stonecutter" -> n == 2 ? of(80, 0, 18, 126, 18) : null;

            case "loom" -> n == 4 ? of(80, 0, 0, 18, 0, 0, 18, 126, 36) : null;
            case "cartography_table" -> n == 3 ? of(62, 0, 0, 0, 36, 126, 18) : null;
            case "smithing" -> n == 4 ? of(44, 0, 0, 18, 0, 36, 0, 90, 0) : null;
            case "lectern" -> n == 1 ? of(44, 0, 0) : null;
            case "crafting" -> n == 10 ? crafting() : null;
            case "hopper" -> n == 5 ? of(26, 0, 0, 18, 0, 36, 0, 54, 0, 72, 0) : null;
            case "generic_3x3", "crafter_3x3" -> n == 9 ? grid3x3() : null;
            default -> null;
        };
    }

    private static Layout crafting() {
        int[] x = new int[10];
        int[] y = new int[10];
        x[0] = 108;
        y[0] = 18;
        for (int i = 1; i <= 9; i++) {
            x[i] = ((i - 1) % 3) * 18;
            y[i] = ((i - 1) / 3) * 18;
        }
        return new Layout(x, y, 62);
    }

    private static Layout grid3x3() {
        int[] x = new int[9];
        int[] y = new int[9];
        for (int i = 0; i < 9; i++) {
            x[i] = (i % 3) * 18;
            y[i] = (i / 3) * 18;
        }
        return new Layout(x, y, 62);
    }

    private static Layout generic(int n) {
        int[] x = new int[n];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = (i % 9) * 18;
            y[i] = (i / 9) * 18;
        }
        int invTop = ((n + 8) / 9) * 18 + 8;
        return new Layout(x, y, invTop);
    }

    private static Layout of(int invTop, int... xy) {
        int n = xy.length / 2;
        int[] x = new int[n];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = xy[i * 2];
            y[i] = xy[i * 2 + 1];
        }
        return new Layout(x, y, invTop);
    }

    private static String strip(String typeId) {
        if (typeId == null) return "";
        int c = typeId.indexOf(':');
        return c >= 0 ? typeId.substring(c + 1) : typeId;
    }
}
