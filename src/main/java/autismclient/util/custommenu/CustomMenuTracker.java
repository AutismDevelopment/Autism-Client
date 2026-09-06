package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuSnapshot;
import net.minecraft.network.protocol.Packet;

public final class CustomMenuTracker {
    private static final CustomMenuSession SESSION = new CustomMenuSession();

    private CustomMenuTracker() {}

    public static void accept(Packet<?> packet, String phase) { SESSION.accept(packet, phase); }
    public static void acceptInterested(Packet<?> packet, String phase) { SESSION.acceptInterested(packet, phase); }
    public static CustomMenuSnapshot current() { return SESSION.current(); }
    public static long generation() { return SESSION.generation(); }
    public static void consume(CustomMenuSnapshot expected, CustomMenuSnapshot replacement) { SESSION.consume(expected, replacement); }
    public static void consumeAt(long seenGeneration, CustomMenuSnapshot replacement, String phase) {
        SESSION.consumeAt(seenGeneration, replacement, phase);
    }
    public static void clear() { SESSION.clear(); }
}
