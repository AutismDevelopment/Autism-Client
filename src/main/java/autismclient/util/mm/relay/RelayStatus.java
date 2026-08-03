package autismclient.util.mm.relay;

import java.util.concurrent.atomic.AtomicLong;

public final class RelayStatus {
    public final String name;
    public volatile boolean connected;
    public volatile String lastError = "";
    public final AtomicLong published = new AtomicLong();
    public final AtomicLong received = new AtomicLong();

    public final AtomicLong queueDropped = new AtomicLong();
    public volatile long lastActivityMs;

    public RelayStatus(String name) { this.name = name; }

    public void markActivity() { lastActivityMs = System.currentTimeMillis(); }

    public String describe() {
        return name + (connected ? " ●" : " ○") + "  ↑" + published.get() + " ↓" + received.get()
            + (lastError.isEmpty() ? "" : "  (" + lastError + ")");
    }
}
