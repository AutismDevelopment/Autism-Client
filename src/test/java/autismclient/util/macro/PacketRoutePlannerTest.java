package autismclient.util.macro;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class PacketRoutePlannerTest {
    @Test
    void directClearRouteCompletesExactly() {
        PacketRoutePlanner.Route route = PacketRoutePlanner.planHorizontal(
            view(100.0D, position -> true), Vec3.ZERO, new Vec3(12.0D, 0.0D, 0.0D),
            32, 8, 20, false);
        assertEquals(PacketRoutePlanner.State.COMPLETE, route.state());
        assertEquals(new Vec3(12.0D, 0.0D, 0.0D), route.waypoints().getLast());
    }

    @Test
    void frontierIsPartialOnlyForPacedCaller() {
        PacketRoutePlanner.CollisionView frontier = view(4.0D, position -> true);
        PacketRoutePlanner.Route paced = PacketRoutePlanner.planHorizontal(
            frontier, Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D), 32, 8, 20, true);
        assertEquals(PacketRoutePlanner.State.PARTIAL, paced.state());
        assertFalse(paced.waypoints().isEmpty());

        PacketRoutePlanner.Route atomicHClip = PacketRoutePlanner.planHorizontal(
            frontier, Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D), 32, 8, 20, false);
        assertEquals(PacketRoutePlanner.State.BLOCKED, atomicHClip.state(),
            "HClip must not report frontier progress as completion");
    }

    @Test
    void obstructedLandingChoosesNearestFullClearPosition() {
        Vec3 target = new Vec3(4.0D, 4.0D, 4.0D);
        PacketRoutePlanner.CollisionView collision = view(100.0D,
            position -> position.distanceToSqr(target) >= 0.25D - 1.0E-9D);
        Vec3 result = PacketRoutePlanner.nearestClear(collision, target, 3.0D);
        assertNotNull(result);
        assertEquals(0.5D, result.distanceTo(target), 1.0E-9D);
    }

    @Test
    void wallUsesBoundedVclipHclipVclip() {
        PacketRoutePlanner.CollisionView terrain = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                boolean wall = position.x >= 2.0D && position.x <= 4.0D && position.y < 5.0D;
                return position.y >= 1.0D && !wall;
            }
            @Override public boolean supported(Vec3 position) {
                return clear(position) && Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 target = new Vec3(8.0D, 1.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            terrain, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(List.of(
            new Vec3(0.0D, 5.0D, 0.0D),
            new Vec3(8.0D, 5.0D, 0.0D),
            target), route.waypoints());
        assertTrue(terrain.traversable(route.waypoints().getLast()));
    }

    @Test
    void hybridUsesTheExactHalfBlockVclipTopExit() {
        PacketRoutePlanner.CollisionView tallStructure = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                boolean ceilingAboveStart = Math.abs(position.x) < 0.25D
                    && position.y >= 2.0D && position.y < 80.5D;
                boolean horizontalWall = position.x >= 1.0D && position.x <= 5.0D
                    && position.y < 80.5D;
                return !ceilingAboveStart && !horizontalWall;
            }
            @Override public boolean supported(Vec3 position) {
                boolean roofTop = Math.abs(position.x) < 0.25D
                    && Math.abs(position.y - 80.5D) < 1.0E-9D;
                boolean targetGround = position.x > 5.0D && Math.abs(position.y - 1.0D) < 1.0E-9D;
                return clear(position) && (roofTop || targetGround);
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 target = new Vec3(20.0D, 1.0D, 0.0D);

        assertEquals(new Vec3(0.0D, 80.5D, 0.0D),
            PacketRoutePlanner.findTopLanding(tallStructure, start, 128.0D));
        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            tallStructure, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals("VClip Top escape", route.detail());
        assertEquals(List.of(
            new Vec3(0.0D, 80.5D, 0.0D),
            new Vec3(20.0D, 80.5D, 0.0D),
            target), route.waypoints());
    }

    @Test
    void validTopExitCanBeAStandalonePreparatoryLeg() {
        PacketRoutePlanner.CollisionView structure = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                boolean ceiling = Math.abs(position.x) < 0.25D
                    && position.y >= 2.0D && position.y < 20.5D;
                boolean tallerWallAhead = position.x >= 1.0D && position.x <= 10.0D
                    && position.y < 50.0D;
                return !ceiling && !tallerWallAhead;
            }
            @Override public boolean supported(Vec3 position) {
                return clear(position) && (Math.abs(position.x) < 0.25D
                    && Math.abs(position.y - 20.5D) < 1.0E-9D
                    || position.x > 10.0D && Math.abs(position.y - 1.0D) < 1.0E-9D);
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            structure, start, new Vec3(20.0D, 1.0D, 0.0D), 32, 128, 96);

        assertEquals(PacketRoutePlanner.State.ESCAPE, route.state(), route.detail());
        assertEquals(List.of(new Vec3(0.0D, 20.5D, 0.0D)), route.waypoints());
    }

    @Test
    void unavailableCollisionDataWaitsInsteadOfGuessingAboutLava() {
        PacketRoutePlanner.CollisionView unavailable = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return false; }
            @Override public boolean clear(Vec3 position) { return false; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 target = new Vec3(500.0D, 90.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            unavailable, Vec3.ZERO, target, 32, 128, 96);

        assertEquals(PacketRoutePlanner.State.BLOCKED, route.state());
        assertTrue(route.waypoints().isEmpty());
        assertTrue(route.detail().contains("lava safety wait"));
    }

    @Test
    void unknownVerticalTargetWaitsForLavaSafetyData() {
        PacketRoutePlanner.CollisionView obstructed = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return false; }
            @Override public boolean clear(Vec3 position) { return false; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 target = new Vec3(0.0D, 120.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            obstructed, Vec3.ZERO, target, 32, 128, 96);

        assertEquals(PacketRoutePlanner.State.BLOCKED, route.state());
        assertTrue(route.waypoints().isEmpty());
        assertTrue(route.detail().contains("lava-safe"));
    }

    @Test
    void diagonalHoleUsesOneDirectHclip() {
        PacketRoutePlanner.CollisionView terrain = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) {
                boolean hole = position.x >= 3.0D && position.x <= 8.0D
                    && position.z >= 3.0D && position.z <= 8.0D;
                return !hole && Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 target = new Vec3(12.0D, 1.0D, 12.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            terrain, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(List.of(target), route.waypoints(), "unsupported space must not cause a long side detour");
    }

    @Test
    void eachLongDistanceCycleEndsAtAStableAnchor() {
        PacketRoutePlanner.CollisionView flat = flatGround();
        Vec3 current = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 target = new Vec3(96.0D, 1.0D, 0.0D);
        int cycles = 0;

        while (current.distanceTo(target) > 0.05D && cycles++ < 6) {
            PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
                flat, current, target, 32, 128, 96);
            assertTrue(route.madeProgress(), route.detail());
            assertTrue(route.waypoints().size() <= 3, "one cycle must be at most V/H/V");
            Vec3 end = route.waypoints().getLast();
            assertTrue(flat.traversable(end), "each available-surface cycle must land");
            assertTrue(end.x > current.x);
            current = end;
        }

        assertEquals(target, current);
        assertEquals(3, cycles);
    }

    @Test
    void descendingTerrainCanVclipDownBeforeHclip() {
        PacketRoutePlanner.CollisionView descending = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) {
                double expectedY = position.x < 2.0D ? 10.0D : 0.0D;
                return Math.abs(position.y - expectedY) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 10.0D, 0.0D);
        Vec3 target = new Vec3(6.0D, 0.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            descending, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(List.of(new Vec3(0.0D, 0.0D, 0.0D), target), route.waypoints());
        assertTrue(descending.traversable(target));
    }

    @Test
    void airborneTargetTouchesGroundBeforeFinalVclip() {
        PacketRoutePlanner.CollisionView ground = flatGround();
        Vec3 target = new Vec3(8.0D, 10.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            ground, new Vec3(0.0D, 1.0D, 0.0D), target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(new Vec3(8.0D, 1.0D, 0.0D), route.waypoints().get(route.waypoints().size() - 2));
        assertEquals(target, route.waypoints().getLast());
    }

    @Test
    void airOnlyWorldStillUsesBoundedFallback() {
        PacketRoutePlanner.CollisionView airOnly = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 target = new Vec3(6.0D, 8.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            airOnly, Vec3.ZERO, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(target, route.waypoints().getLast());
        assertTrue(route.waypoints().size() <= 2);
        assertTrue(route.detail().contains("fallback"));
    }

    @Test
    void farAirWorldKeepsEnoughBudgetForItsFrontier() {
        PacketRoutePlanner.CollisionView airOnly = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            airOnly, Vec3.ZERO, new Vec3(1_000.0D, 40.0D, 0.0D), 80, 128, 96);

        assertEquals(PacketRoutePlanner.State.PARTIAL, route.state(), route.detail());
        assertEquals(new Vec3(80.0D, 0.0D, 0.0D), route.waypoints().getLast());
        assertTrue(route.detail().contains("fallback"));
    }

    @Test
    void breathableWaterlineIsAStableCycleEndpoint() {
        PacketRoutePlanner.CollisionView ocean = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return false; }
            @Override public boolean breathableWater(Vec3 position) {
                return Math.abs(position.y - 64.125D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 64.125D, 0.0D);
        Vec3 target = new Vec3(24.0D, 64.125D, 17.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            ocean, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(List.of(target), route.waypoints());
        assertTrue(ocean.traversable(route.waypoints().getLast()));
    }

    @Test
    void farRouteHasAHardCollisionQueryCeiling() {
        AtomicInteger probes = new AtomicInteger();
        PacketRoutePlanner.CollisionView counted = new PacketRoutePlanner.CollisionView() {
            private void count() {
                assertTrue(probes.incrementAndGet() <= 4_096, "planner exceeded its per-cycle probe budget");
            }
            @Override public boolean loaded(Vec3 position) { count(); return true; }
            @Override public boolean clear(Vec3 position) { count(); return true; }
            @Override public boolean supported(Vec3 position) {
                count();
                return Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public boolean breathableWater(Vec3 position) { count(); return false; }
            @Override public boolean traversable(Vec3 position) {
                count();
                return Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            counted, new Vec3(0.0D, 1.0D, 0.0D), new Vec3(10_000.0D, 1.0D, 10_000.0D),
            32, 128, 96);

        assertEquals(PacketRoutePlanner.State.PARTIAL, route.state());
        assertFalse(route.waypoints().isEmpty());
        assertTrue(route.waypoints().size() <= 3);
        assertTrue(counted.traversable(route.waypoints().getLast()));
        assertTrue(probes.get() < 512, "a clear frontier should be extremely cheap");
    }

    @Test
    void fullyBlockedCorridorStillFallsBackWithinTheProbeCeiling() {
        AtomicInteger probes = new AtomicInteger();
        PacketRoutePlanner.CollisionView blocked = new PacketRoutePlanner.CollisionView() {
            private void count() {
                assertTrue(probes.incrementAndGet() <= 4_096, "blocked planner exceeded its total probe ceiling");
            }
            @Override public boolean loaded(Vec3 position) { count(); return true; }
            @Override public boolean clear(Vec3 position) {
                count();
                return !(position.x > 0.1D && position.x < 79.9D);
            }
            @Override public boolean supported(Vec3 position) {
                count();
                return clear(position) && Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public boolean traversable(Vec3 position) {
                count();
                return !(position.x > 0.1D && position.x < 79.9D)
                    && Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            blocked, new Vec3(0.0D, 1.0D, 0.0D), new Vec3(200.0D, 1.0D, 0.0D),
            80, 128, 96);

        assertEquals(PacketRoutePlanner.State.PARTIAL, route.state());
        assertEquals(new Vec3(80.0D, 1.0D, 0.0D), route.waypoints().getLast());
        assertTrue(route.detail().contains("unrestricted"), route.detail());
        assertTrue(probes.get() <= 4_096);
    }

    @Test
    void lavaBandUsesSafeVclipHclipVclipLayer() {
        PacketRoutePlanner.CollisionView lavaBand = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) {
                return Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public boolean lavaSafe(Vec3 position) {
                return !(position.x >= 3.0D && position.x <= 5.0D && position.y <= 3.0D);
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        Vec3 start = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 target = new Vec3(8.0D, 1.0D, 0.0D);

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            lavaBand, start, target, 32, 128, 96);

        assertTrue(route.complete(), route.detail());
        assertEquals(List.of(
            new Vec3(0.0D, 4.0D, 0.0D),
            new Vec3(8.0D, 4.0D, 0.0D),
            target), route.waypoints());
        assertLavaSafeRoute(lavaBand, start, route.waypoints());
    }

    @Test
    void lavaTargetChoosesFarthestDeterministicSafeLanding() {
        Vec3 target = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 near = new Vec3(1.0D, 64.0D, 0.0D);
        Vec3 west = new Vec3(-3.0D, 64.0D, 0.0D);
        Vec3 east = new Vec3(3.0D, 64.0D, 0.0D);
        PacketRoutePlanner.CollisionView lavaTarget = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                return position.equals(target) || position.equals(near)
                    || position.equals(west) || position.equals(east);
            }
            @Override public boolean supported(Vec3 position) { return clear(position); }
            @Override public boolean lavaSafe(Vec3 position) { return !position.equals(target) && clear(position); }
            @Override public double lavaClearance(Vec3 position) {
                if (position.equals(near)) return 4.0D;
                if (position.equals(west) || position.equals(east)) return 8.0D;
                return 0.0D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        for (int i = 0; i < 3; i++) {
            assertEquals(west, PacketRoutePlanner.safestLavaLanding(lavaTarget, target, 8.0D));
        }
    }

    @Test
    void noSafeLavaLandingReturnsNull() {
        PacketRoutePlanner.CollisionView lavaEverywhere = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return true; }
            @Override public boolean lavaSafe(Vec3 position) { return false; }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
        assertNull(PacketRoutePlanner.safestLavaLanding(lavaEverywhere, Vec3.ZERO, 8.0D));
        assertTrue(PacketRoutePlanner.safeFallbackWaypoints(
            lavaEverywhere, Vec3.ZERO, new Vec3(100.0D, 0.0D, 0.0D), 32).isEmpty());
    }

    @Test
    void verticalOnlyLavaTargetCannotBypassSafety() {
        Vec3 target = new Vec3(0.0D, 8.0D, 0.0D);
        PacketRoutePlanner.CollisionView lavaTarget = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return true; }
            @Override public boolean supported(Vec3 position) { return true; }
            @Override public boolean lavaSafe(Vec3 position) { return !position.equals(target); }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        PacketRoutePlanner.Route route = PacketRoutePlanner.planHybridToward(
            lavaTarget, Vec3.ZERO, target, 32, 128, 96);

        assertEquals(PacketRoutePlanner.State.BLOCKED, route.state());
        assertFalse(route.waypoints().contains(target));
    }

    @Test
    void vclipTopSkipsUnsafeSupportedLanding() {
        PacketRoutePlanner.CollisionView roof = new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) {
                return position.y < 2.0D || position.y >= 5.5D;
            }
            @Override public boolean supported(Vec3 position) {
                return Math.abs(position.y - 5.5D) < 1.0E-9D
                    || Math.abs(position.y - 6.0D) < 1.0E-9D;
            }
            @Override public boolean lavaSafe(Vec3 position) {
                return Math.abs(position.y - 5.5D) > 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };

        assertEquals(new Vec3(0.0D, 6.0D, 0.0D),
            PacketRoutePlanner.findTopLanding(roof, Vec3.ZERO, 128.0D));
    }

    @Test
    void waterlineDepthKeepsEyesSafelyDry() {
        assertTrue(PacketRoutePlanner.breathableWaterDepth(1.8D, 1.62D, 0.90D, false));
        assertFalse(PacketRoutePlanner.breathableWaterDepth(1.8D, 1.62D, 0.20D, false));
        assertFalse(PacketRoutePlanner.breathableWaterDepth(1.8D, 1.62D, 1.50D, false));
        assertFalse(PacketRoutePlanner.breathableWaterDepth(1.8D, 1.62D, 0.90D, true));
    }

    private static PacketRoutePlanner.CollisionView flatGround() {
        return new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return true; }
            @Override public boolean clear(Vec3 position) { return position.y >= 1.0D; }
            @Override public boolean supported(Vec3 position) {
                return Math.abs(position.y - 1.0D) < 1.0E-9D;
            }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
    }

    private static PacketRoutePlanner.CollisionView view(double loadedMaxX,
                                                           java.util.function.Predicate<Vec3> clear) {
        return new PacketRoutePlanner.CollisionView() {
            @Override public boolean loaded(Vec3 position) { return position.x <= loadedMaxX; }
            @Override public boolean clear(Vec3 position) { return clear.test(position); }
            @Override public double minY() { return -64.0D; }
            @Override public double maxFeetY() { return 318.0D; }
        };
    }

    private static void assertLavaSafeRoute(PacketRoutePlanner.CollisionView view,
                                            Vec3 start, List<Vec3> route) {
        Vec3 previous = start;
        for (Vec3 waypoint : route) {
            int samples = Math.max(1, (int) Math.ceil(previous.distanceTo(waypoint) / 0.25D));
            for (int i = 1; i <= samples; i++) {
                assertTrue(view.lavaSafe(previous.lerp(waypoint, i / (double) samples)),
                    "route sampled lava between " + previous + " and " + waypoint);
            }
            previous = waypoint;
        }
    }
}
