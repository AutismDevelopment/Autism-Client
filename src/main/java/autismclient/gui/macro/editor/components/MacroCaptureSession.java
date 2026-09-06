package autismclient.gui.macro.editor.components;

public final class MacroCaptureSession {
    private String itemSlotKey;

    public String itemSlotKey() {
        return itemSlotKey;
    }

    public boolean hasItemSlotCapture() {
        return itemSlotKey != null;
    }

    public boolean isItemSlotCapture(String key) {
        return key != null && key.equals(itemSlotKey);
    }

    public void startItemSlotCapture(String key, Runnable beginCapture) {
        if (key == null || key.isBlank()) return;
        itemSlotKey = key;
        if (beginCapture != null) beginCapture.run();
    }

    public boolean stopItemSlotCapture(Runnable endCapture) {
        if (itemSlotKey == null) return false;
        itemSlotKey = null;
        if (endCapture != null) endCapture.run();
        return true;
    }

    public void toggleItemSlotCapture(String key, Runnable beginCapture, Runnable endCapture) {
        if (isItemSlotCapture(key)) {
            stopItemSlotCapture(endCapture);
        } else {
            startItemSlotCapture(key, beginCapture);
        }
    }

    public void clearItemSlotCapture() {
        itemSlotKey = null;
    }
}
