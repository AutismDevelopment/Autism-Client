package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuEvent;
import autismclient.api.custommenu.CustomMenuSnapshot;
import net.minecraft.network.protocol.Packet;

public final class CustomMenuSession {
    private volatile long generation;
    private volatile CustomMenuSnapshot current;

    public boolean accept(Packet<?> packet, String phase) {
        if (!CustomMenuAdapterRegistry.acceptsInbound(packet)) return false;
        return acceptInterested(packet, phase);
    }

    public boolean acceptInterested(Packet<?> packet, String phase) {
        CustomMenuEvent event = CustomMenuAdapterRegistry.inspect(packet, phase);
        if (event.type() == CustomMenuEvent.Type.NONE) return false;
        synchronized (this) {
            if (event.type() == CustomMenuEvent.Type.CLEAR) {
                current = null;
                generation++;
            } else if (event.snapshot() != null) {
                current = event.snapshot().withConnectionState(phase, ++generation);
            }
        }
        return true;
    }

    public CustomMenuSnapshot current() { return current; }
    public long generation() { return generation; }

    public synchronized void consume(CustomMenuSnapshot expected, CustomMenuSnapshot replacement) {
        if (expected != null && current != null && current.generation() != expected.generation()) return;
        current = replacement == null ? null : replacement.withConnectionState(
            expected == null ? "" : expected.phase(), ++generation);
    }

    public synchronized void consumeAt(long seenGeneration, CustomMenuSnapshot replacement, String phase) {
        if (generation != seenGeneration) return;
        generation++;
        current = replacement == null ? null : replacement.withConnectionState(phase == null ? "" : phase, generation);
    }

    public void clear() {
        if (current == null) return;
        synchronized (this) {
            if (current != null) {
                current = null;
                generation++;
            }
        }
    }
}
