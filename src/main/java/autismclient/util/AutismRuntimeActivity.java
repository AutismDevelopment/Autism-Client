package autismclient.util;

import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;

import java.util.concurrent.atomic.AtomicLong;

public final class AutismRuntimeActivity {
    public static final long MODULE_TICK = 1L;
    public static final long LEVEL_RENDER = 1L << 1;
    public static final long PRE_MOVEMENT = 1L << 2;
    public static final long MOVEMENT = 1L << 3;
    public static final long MOUSE_ROTATION = 1L << 4;
    public static final long SOUND = 1L << 5;
    public static final long HUD_MODULE = 1L << 6;
    public static final long NAMETAGS = 1L << 7;
    public static final long OVERLAY = 1L << 8;
    public static final long MULTI = 1L << 9;
    public static final long PACKET_CAPTURE = 1L << 10;
    public static final long HUD_AUX = 1L << 11;

    public record Snapshot(int moduleRevision, long bits) {
        public boolean has(long mask) {
            return (bits & mask) != 0L;
        }
    }

    private static final AtomicLong EXTERNAL_BITS = new AtomicLong();
    private static volatile Snapshot published = new Snapshot(Integer.MIN_VALUE, 0L);

    private AutismRuntimeActivity() {}

    public static Snapshot current() {
        return published;
    }

    public static boolean has(long mask) {
        return (published.bits & mask) != 0L;
    }

    public static void publish(long bit, boolean active) {
        if ((bit & externalMask()) == 0L) throw new IllegalArgumentException("Not an external activity bit: " + bit);
        while (true) {
            long current = EXTERNAL_BITS.get();
            long next = active ? current | bit : current & ~bit;
            if (next == current) return;
            if (EXTERNAL_BITS.compareAndSet(current, next)) {
                refresh(ModuleRegistry.revision(), next);
                return;
            }
        }
    }

    public static void publishModuleRevision(int revision) {
        refresh(revision, EXTERNAL_BITS.get());
    }

    private static synchronized Snapshot refresh(int revision, long external) {
        Snapshot current = published;
        if (current.moduleRevision == revision && (current.bits & externalMask()) == external) return current;
        long bits = external;
        if (ModuleRegistry.hasTickWork()) bits |= MODULE_TICK;
        if (ModuleRegistry.hasRenderLevelHooks()) bits |= LEVEL_RENDER;
        if (ModuleRegistry.hasPreMovementHooks()) bits |= PRE_MOVEMENT;
        if (ModuleRegistry.hasMovementHooks()) bits |= MOVEMENT;
        if (ModuleRegistry.hasMouseRotationHooks()) bits |= MOUSE_ROTATION;
        if (ModuleRegistry.hasSoundHooks()) bits |= SOUND;
        Module hud = ModuleRegistry.get("hud");
        if (hud != null && hud.isEnabled()) bits |= HUD_MODULE;
        Module nametags = ModuleRegistry.get("nametags");
        if (nametags != null && nametags.isEnabled()) bits |= NAMETAGS;
        Snapshot next = new Snapshot(revision, bits);
        published = next;
        return next;
    }

    private static long externalMask() {
        return OVERLAY | MULTI | PACKET_CAPTURE | HUD_AUX;
    }
}
