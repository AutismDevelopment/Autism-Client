package autismclient.util.multi;

import autismclient.util.macro.PacketRoutePlanner;
import autismclient.util.macro.PacketClipSafety;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PacketTeleportControllerTest {
    @Test
    void parsesAbsoluteRelativeAndRunOverrides() {
        PacketTeleportController.CommandRequest request = PacketTeleportController.parse(
            "~10 70 ~-2 35 900", new Vec3(5.0D, 64.0D, 9.0D), 20, 500);
        assertEquals(PacketTeleportController.CommandKind.START, request.kind());
        assertEquals(new Vec3(15.0D, 70.0D, 7.0D), request.destination());
        assertEquals(35, request.maxPackets());
        assertEquals(900, request.pauseMs());
    }

    @Test
    void removesSmoothModeAndKeepsLegacyFastAlias() {
        Vec3 origin = new Vec3(1.0D, 64.0D, 2.0D);
        assertEquals(PacketTeleportController.CommandKind.START,
            PacketTeleportController.parse("10 70 20", origin, 20, 500).kind());
        assertEquals(PacketTeleportController.CommandKind.START,
            PacketTeleportController.parse("fast 10 70 20", origin, 20, 500).kind());
        assertEquals(PacketTeleportController.CommandKind.ERROR,
            PacketTeleportController.parse("smooth 10 70 20", origin, 20, 500).kind());
    }

    @Test
    void parsesControlAndPersistentConfigForms() {
        Vec3 origin = Vec3.ZERO;
        assertEquals(PacketTeleportController.CommandKind.STOP,
            PacketTeleportController.parse("stop", origin, 20, 500).kind());
        assertEquals(PacketTeleportController.CommandKind.STATUS,
            PacketTeleportController.parse("status", origin, 20, 500).kind());
        PacketTeleportController.CommandRequest config = PacketTeleportController.parse(
            "config 44 1250", origin, 20, 500);
        assertEquals(PacketTeleportController.CommandKind.CONFIG, config.kind());
        assertEquals(44, config.maxPackets());
        assertEquals(1250, config.pauseMs());
        PacketTeleportController.CommandRequest reset = PacketTeleportController.parse("reset", origin, 44, 1250);
        assertEquals(20, reset.maxPackets());
        assertEquals(500, reset.pauseMs());
    }

    @Test
    void rejectsOutOfRangeAndUnsupportedCoordinates() {
        assertEquals(PacketTeleportController.CommandKind.ERROR,
            PacketTeleportController.parse("0 0 0 101", Vec3.ZERO, 20, 500).kind());
        assertEquals(PacketTeleportController.CommandKind.ERROR,
            PacketTeleportController.parse("0 0 0 20 49", Vec3.ZERO, 20, 500).kind());
        assertEquals(PacketTeleportController.CommandKind.ERROR,
            PacketTeleportController.parse("^1 0 0", Vec3.ZERO, 20, 500).kind());
    }

    @Test
    void sendsOncePerTickAndRestsAfterWindow() {
        PacketTeleportController.Pacer pacer = new PacketTeleportController.Pacer(20, 500);
        long now = 1_000L;
        for (long tick = 1; tick <= 20; tick++) {
            assertTrue(pacer.canSend(tick, now));
            assertFalse(pacer.canSend(tick, now), "same client tick must not send twice");
            pacer.markSent(now);
            now += 50L;
        }
        assertEquals(2_450L, pacer.resumeAt());
        assertFalse(pacer.canSend(21L, 2_449L));
        assertTrue(pacer.canSend(21L, 2_450L));
        pacer.markSent(2_450L);
        assertEquals(1, pacer.packetsInWindow(), "a new unlimited window starts after the rest");
        assertEquals(19, pacer.remainingInWindow());
    }

    @Test
    void rejectedFastBurstFallsBackToOneRealMicroStep() {
        PacketTeleportController.FastClipPlan normal = PacketTeleportController.planFastClip(
            500.0D, 8.0D, 20, false);
        assertEquals(500.0D, normal.amount(), 1.0E-9D);
        assertEquals(20, normal.packets());
        assertTrue(normal.banked());

        PacketTeleportController.FastClipPlan recovery = PacketTeleportController.planFastClip(
            500.0D, 4.0D, 20, true);
        assertEquals(4.0D, recovery.amount(), 1.0E-9D);
        assertEquals(1, recovery.packets());
        assertFalse(recovery.banked());

        PacketTeleportController.FastClipPlan minimum = PacketTeleportController.planFastClip(
            10.0D, 0.001D, 20, true);
        assertEquals(0.0625D, minimum.amount(), 1.0E-9D);
    }

    @Test
    void correctionRebaseClearsAnOldFullWindowPause() {
        PacketTeleportController.Pacer pacer = new PacketTeleportController.Pacer(1, 500);
        assertTrue(pacer.canSend(1L, 1_000L));
        pacer.markSent(1_000L);
        assertTrue(pacer.waiting(1_200L));
        pacer.rebase(1_200L, 200L);
        assertEquals(0, pacer.packetsInWindow());
        assertFalse(pacer.canSend(2L, 1_399L));
        assertTrue(pacer.canSend(2L, 1_400L));
    }

    @Test
    void halfBlockRollbackIsNeverMistakenForAcceptance() {
        assertFalse(PacketTeleportController.correctionAccepted(
            100.0D, 99.5D, 100.0D, 0.5D));
        assertTrue(PacketTeleportController.correctionAccepted(
            100.0D, 99.5D, 99.5D, 0.0D), "an exact predicted correction confirms the clip");
        assertTrue(PacketTeleportController.correctionAccepted(
            100.0D, 99.5D, 99.7D, 0.2D), "authoritative net progress is retained");
    }

    @Test
    void breathableWaterAnchorsNeverSpoofProtocolGround() {
        PacketRoutePlanner.CollisionView water = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public boolean breathableWater(Vec3 position) { return true; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        assertTrue(water.traversable(Vec3.ZERO), "water is a stable planner anchor");
        assertFalse(PacketTeleportController.protocolGround(water, Vec3.ZERO),
            "water must remain airborne on the wire");
    }

    @Test
    void collisionBlindFallbackIsShortAndAxisPureForEitherOwner() {
        Vec3 start = new Vec3(0.0D, 64.0D, 0.0D);
        PacketRoutePlanner.CollisionView knownSafe = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        java.util.List<Vec3> far = PacketTeleportController.unrestrictedFallbackWaypoints(
            knownSafe, start, new Vec3(100.0D, 80.0D, 100.0D));
        assertEquals(1, far.size());
        assertEquals(32.0D, Math.hypot(far.getFirst().x, far.getFirst().z), 1.0E-9D);
        assertEquals(start.y, far.getFirst().y, "the blind frontier is a pure HClip");

        Vec3 nearTarget = new Vec3(10.0D, 80.0D, 0.0D);
        java.util.List<Vec3> near = PacketTeleportController.unrestrictedFallbackWaypoints(
            knownSafe, start, nearTarget);
        assertEquals(List.of(new Vec3(10.0D, 64.0D, 0.0D), nearTarget), near,
            "the final unrestricted segment is HClip then VClip");
        assertTrue(PacketTeleportController.unrestrictedFallbackWaypoints(start, nearTarget).isEmpty(),
            "unknown hazard data must wait instead of guessing");
    }

    @Test
    void fifthSpatialLegForcesAGroundingAttemptButNeverBlocks() {
        int counter = 0;
        for (int leg = 1; leg <= 4; leg++) {
            counter = PacketTeleportController.nextGroundingCounter(counter, false);
            assertEquals(leg, counter);
        }
        assertTrue(PacketTeleportController.groundingDue(counter),
            "the route before leg five must try a stable VClip");
        assertEquals(5, PacketTeleportController.nextGroundingCounter(counter, false),
            "missing ground saturates instead of stopping movement");
        assertEquals(0, PacketTeleportController.nextGroundingCounter(5, true),
            "solid ground or breathable water resets the cadence");
    }

    @Test
    void preparatoryVclipTopIsAcceptedWithoutHorizontalProgress() {
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        PacketRoutePlanner.Route escape = new PacketRoutePlanner.Route(
            PacketRoutePlanner.State.ESCAPE,
            List.of(new Vec3(0.0D, 80.5D, 0.0D)),
            "VClip Top preparatory escape");

        assertTrue(PacketTeleportController.acceptsPlannedRoute(
            escape, start, new Vec3(100.0D, 1.0D, 0.0D)));
        PacketRoutePlanner.Route ordinaryDetour = new PacketRoutePlanner.Route(
            PacketRoutePlanner.State.PARTIAL,
            escape.waypoints(),
            "ordinary detour");
        assertFalse(PacketTeleportController.acceptsPlannedRoute(
            ordinaryDetour, start, new Vec3(100.0D, 1.0D, 0.0D)),
            "only an explicit escape may temporarily keep the same X/Z");
    }

    @Test
    void unrestrictedFallbackRoutesOverLavaInsteadOfThroughIt() {
        PacketRoutePlanner.CollisionView lavaBand = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean lavaSafe(Vec3 position) {
                return !(position.x >= 8.0D && position.x <= 40.0D && position.y <= 3.0D);
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        List<Vec3> route = PacketTeleportController.unrestrictedFallbackWaypoints(
            lavaBand, start, new Vec3(100.0D, 1.0D, 0.0D));

        assertEquals(List.of(new Vec3(0.0D, 4.0D, 0.0D), new Vec3(32.0D, 4.0D, 0.0D)), route);
        Vec3 previous = start;
        for (Vec3 waypoint : route) {
            assertTrue(PacketTeleportController.lavaSegmentSafe(lavaBand, previous, waypoint));
            previous = waypoint;
        }
    }

    @Test
    void newlyUnsafePacketStepIsRejectedAtSendTimeGate() {
        java.util.concurrent.atomic.AtomicBoolean flowing = new java.util.concurrent.atomic.AtomicBoolean(false);
        PacketRoutePlanner.CollisionView dynamic = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean lavaSafe(Vec3 position) {
                return !flowing.get() || position.x < 5.0D || position.x > 6.0D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        List<PacketClipSafety.Step> steps = List.of(
            new PacketClipSafety.Step(new Vec3(5.5D, 0.0D, 0.0D), false));
        assertTrue(PacketTeleportController.packetStepsLavaSafe(dynamic, steps));
        flowing.set(true);
        assertFalse(PacketTeleportController.packetStepsLavaSafe(dynamic, steps));
    }

    @Test
    void loadedLavaDestinationNeverFallsBackToRequestedCoordinates() {
        Vec3 requested = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 shore = new Vec3(-3.0D, 64.0D, 0.0D);
        PacketRoutePlanner.CollisionView withShore = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                return position.equals(requested) || position.equals(shore);
            }
            @Override public boolean supported(Vec3 position) { return position.equals(shore); }
            @Override public boolean lavaSafe(Vec3 position) { return position.equals(shore); }
            @Override public double lavaClearance(Vec3 position) { return position.equals(shore) ? 8.0D : 0.0D; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        assertEquals(shore, PacketTeleportController.resolveLoadedDestination(withShore, requested, 8.0D));

        PacketRoutePlanner.CollisionView noShore = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return true; }
            @Override public boolean lavaSafe(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        assertNull(PacketTeleportController.resolveLoadedDestination(noShore, requested, 8.0D),
            "no safe shore means wait, never use the lava request");
    }
}
