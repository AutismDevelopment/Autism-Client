package autismclient.util.macro;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PacketRoutePlanner {
    public enum State { COMPLETE, PARTIAL, ESCAPE, BLOCKED }

    public record Route(State state, List<Vec3> waypoints, String detail) {
        public Route {
            state = state == null ? State.BLOCKED : state;
            waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
            detail = detail == null ? "" : detail;
        }

        public boolean complete() { return state == State.COMPLETE; }
        public boolean madeProgress() { return !waypoints.isEmpty(); }
    }

    public interface CollisionView {
        boolean loaded(Vec3 position);
        boolean clear(Vec3 position);

        default Entity entity() { return null; }

        default boolean supported(Vec3 position) { return clear(position); }

        default boolean breathableWater(Vec3 position) { return false; }

        default boolean traversable(Vec3 position) { return supported(position) || breathableWater(position); }

        default boolean lavaSafe(Vec3 position) { return true; }

        default double lavaClearance(Vec3 position) {
            return lavaSafe(position) ? Double.POSITIVE_INFINITY : 0.0D;
        }
        double minY();
        double maxFeetY();
    }

    private static final double PATH_SAMPLE = 0.25D;
    private static final double ADVANCE_SAMPLE = 0.5D;
    private static final double HYBRID_PATH_SAMPLE = 0.5D;
    private static final double HYBRID_ANCHOR_SAMPLE = 0.25D;
    private static final double HYBRID_MIN_PROGRESS = 0.5D;
    private static final int HYBRID_MAX_LEGS = 3;
    private static final int HYBRID_PROBE_BUDGET = 1_800;
    private static final int HYBRID_TOP_PROBE_BUDGET = 1_500;
    private static final int HYBRID_MAX_FRONTIER = 128;
    private static final double HYBRID_MAX_ANCHOR_DOWN = 128.0D;
    private static final double HYBRID_MAX_ANCHOR_UP = 64.0D;
    private static final double LAVA_HARD_HORIZONTAL = 1.5D;
    private static final double LAVA_HARD_VERTICAL = 1.0D;
    private static final double LAVA_SOFT_SCAN = 6.0D;

    private PacketRoutePlanner() {
    }

    public static CollisionView forEntity(Entity entity) {
        if (entity == null || entity.level() == null) return null;
        Vec3 origin = entity.position();
        AABB bounds = entity.getBoundingBox();
        double height = bounds.getYsize();
        return new CollisionView() {
            private final Map<Long, Boolean> lavaBlocks = new HashMap<>();

            @Override
            public Entity entity() {
                return entity;
            }

            private boolean lava(BlockPos position) {
                long key = position.asLong();
                Boolean cached = lavaBlocks.get(key);
                if (cached != null) return cached;
                boolean lava = entity.level().getFluidState(position).is(FluidTags.LAVA);
                lavaBlocks.put(key, lava);
                return lava;
            }

            @Override
            public boolean loaded(Vec3 position) {
                return position != null && entity.level().isLoaded(BlockPos.containing(position));
            }

            @Override
            public boolean clear(Vec3 position) {
                if (position == null || position.y < minY() || position.y > maxFeetY()) return false;
                AABB moved = bounds.move(position.subtract(origin));
                return entity.level().noCollision(entity, moved);
            }

            @Override
            public boolean supported(Vec3 position) {
                if (!clear(position)) return false;
                AABB moved = bounds.move(position.subtract(origin));
                return !entity.level().noCollision(entity, moved.move(0.0D, -0.0625D, 0.0D));
            }

            @Override
            public boolean breathableWater(Vec3 position) {
                if (!clear(position)) return false;
                double feetY = position.y;
                double eyeY = feetY + entity.getEyeHeight();
                double surface = waterSurface(entity, position.x, position.z, feetY, eyeY);
                if (!Double.isFinite(surface)) return false;
                double depth = surface - feetY;
                boolean eyeSubmerged = pointInWater(entity, position.x, eyeY - 0.05D, position.z);
                return breathableWaterDepth(bounds.getYsize(), entity.getEyeHeight(), depth, eyeSubmerged)
                    && pointInWater(entity, position.x, feetY + 0.10D, position.z)
                    && pointInWater(entity, position.x,
                        feetY + Math.min(0.80D, bounds.getYsize() * 0.48D), position.z);
            }

            @Override
            public boolean lavaSafe(Vec3 position) {
                return position != null && lavaSafeAt(entity, bounds.move(position.subtract(origin)), this::lava);
            }

            @Override
            public double lavaClearance(Vec3 position) {
                return position == null ? 0.0D
                    : lavaClearanceAt(entity, bounds.move(position.subtract(origin)), LAVA_SOFT_SCAN, this::lava);
            }

            @Override
            public double minY() {
                return entity.level().getMinY();
            }

            @Override
            public double maxFeetY() {
                return entity.level().getMaxY() + 1.0D - height;
            }
        };
    }

    static boolean breathableWaterDepth(double entityHeight, double eyeHeight,
                                         double waterDepth, boolean eyeSubmerged) {
        if (eyeSubmerged || !Double.isFinite(waterDepth)) return false;
        double minDepth = Math.min(0.85D, Math.max(0.45D, entityHeight * 0.45D));
        double maxDepth = Math.max(minDepth + 0.10D,
            Math.min(Math.max(0.0D, eyeHeight - 0.30D), entityHeight * 0.68D));
        return waterDepth >= minDepth && waterDepth <= maxDepth;
    }

    private static double waterSurface(Entity entity, double x, double z, double feetY, double eyeY) {
        if (entity == null || entity.level() == null) return Double.NaN;
        double highest = Double.NaN;
        int min = (int) Math.floor(feetY);
        int max = (int) Math.floor(eyeY);
        for (int y = min; y <= max; y++) {
            BlockPos block = BlockPos.containing(x, y, z);
            FluidState fluid = entity.level().getFluidState(block);
            if (!fluid.is(FluidTags.WATER)) continue;
            double surface = block.getY() + fluid.getHeight(entity.level(), block);
            if (!Double.isFinite(highest) || surface > highest) highest = surface;
        }
        return highest;
    }

    private static boolean pointInWater(Entity entity, double x, double y, double z) {
        if (entity == null || entity.level() == null) return false;
        BlockPos block = BlockPos.containing(x, y, z);
        FluidState fluid = entity.level().getFluidState(block);
        return fluid.is(FluidTags.WATER)
            && y < block.getY() + fluid.getHeight(entity.level(), block) - 1.0E-4D;
    }

    private static boolean lavaSafeAt(Entity entity, AABB body,
                                      java.util.function.Predicate<BlockPos> lavaAt) {
        if (entity == null || entity.level() == null || body == null) return false;
        AABB shell = new AABB(
            body.minX - LAVA_HARD_HORIZONTAL, body.minY - LAVA_HARD_VERTICAL,
            body.minZ - LAVA_HARD_HORIZONTAL, body.maxX + LAVA_HARD_HORIZONTAL,
            body.maxY + LAVA_HARD_VERTICAL, body.maxZ + LAVA_HARD_HORIZONTAL);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(floor(shell.minY), entity.level().getMinY());
        int maxY = Math.min(ceil(shell.maxY), entity.level().getMaxY() + 1);
        for (int x = floor(shell.minX); x < ceil(shell.maxX); x++) {
            for (int z = floor(shell.minZ); z < ceil(shell.maxZ); z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    if (!entity.level().isLoaded(cursor)) return false;
                    if (lavaAt.test(cursor)) return false;
                }
            }
        }
        return true;
    }

    private static double lavaClearanceAt(Entity entity, AABB body, double scanRadius,
                                          java.util.function.Predicate<BlockPos> lavaAt) {
        if (entity == null || entity.level() == null || body == null) return 0.0D;
        double radius = Math.max(0.0D, scanRadius);
        AABB scan = new AABB(body.minX - radius, body.minY - radius, body.minZ - radius,
            body.maxX + radius, body.maxY + radius, body.maxZ + radius);
        double bestSqr = Double.POSITIVE_INFINITY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(floor(scan.minY), entity.level().getMinY());
        int maxY = Math.min(ceil(scan.maxY), entity.level().getMaxY() + 1);
        for (int x = floor(scan.minX); x < ceil(scan.maxX); x++) {
            for (int z = floor(scan.minZ); z < ceil(scan.maxZ); z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    if (!entity.level().isLoaded(cursor)) return 0.0D;
                    if (!lavaAt.test(cursor)) continue;
                    double dx = axisGap(body.minX, body.maxX, x, x + 1.0D);
                    double dy = axisGap(body.minY, body.maxY, y, y + 1.0D);
                    double dz = axisGap(body.minZ, body.maxZ, z, z + 1.0D);
                    bestSqr = Math.min(bestSqr, dx * dx + dy * dy + dz * dz);
                }
            }
        }
        return Double.isFinite(bestSqr) ? Math.sqrt(bestSqr) : Double.POSITIVE_INFINITY;
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) return secondMin - firstMax;
        if (secondMax < firstMin) return firstMin - secondMax;
        return 0.0D;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    public static Route planHorizontal(CollisionView view, Vec3 start, Vec3 target, int searchRadius,
                                       int verticalRange, int maxWaypoints, boolean allowPartial) {
        if (view == null || start == null || target == null) return blocked("no collision view");
        int radius = Math.max(1, Math.min(128, searchRadius));
        int escapeRange = Math.max(0, Math.min(128, verticalRange));
        int routeCap = Math.max(1, Math.min(512, maxWaypoints));
        Vec3 horizontalTarget = new Vec3(target.x, start.y, target.z);

        if (safeClear(view, horizontalTarget)
            && clearHorizontalPath(view, start, horizontalTarget)) {
            return complete(clean(start, List.of(horizontalTarget)), "direct");
        }

        Route layered = layered(view, start, horizontalTarget, escapeRange, routeCap);
        if (layered.complete()) return layered;

        List<Vec3> route = new ArrayList<>();
        Vec3 current = start;
        boolean unloadedFrontier = false;
        int guard = 0;
        while (horizontalDistance(current, horizontalTarget) > 0.5D && guard++ < routeCap) {
            Vec3 advance = maxClearAdvance(view, current, horizontalTarget, radius);
            if (horizontalDistance(advance, current) > 0.5D) {
                route.add(advance);
                current = advance;
                continue;
            }

            Vec3 next = toward(current, horizontalTarget, ADVANCE_SAMPLE);
            if (!view.loaded(next)) {
                unloadedFrontier = true;
                break;
            }

            boolean escaped = false;
            for (int dy : verticalOffsets(escapeRange)) {
                if (dy == 0) continue;
                Vec3 layer = new Vec3(current.x, clampY(view, current.y + dy), current.z);
                if (!safeClear(view, layer)) continue;
                if (!safeLavaPath(view, current, layer)) continue;
                Vec3 layerAdvance = maxClearAdvance(view, layer, horizontalTarget, radius);
                if (horizontalDistance(layerAdvance, layer) <= 1.0D) continue;
                route.add(layer);
                route.add(layerAdvance);
                current = layerAdvance;
                escaped = true;
                break;
            }
            if (!escaped) break;
        }

        boolean reached = horizontalDistance(current, horizontalTarget) <= 0.5D;
        if (reached) {
            Vec3 landing = nearestClearAtColumn(view, current.x, current.z, start.y, 128.0D);
            if (landing != null && !same(landing, current) && safeLavaPath(view, current, landing)) {
                route.add(landing);
            }
        }
        List<Vec3> cleaned = clean(start, route);
        if (reached && !cleaned.isEmpty()) return complete(cleaned, "segmented");
        if (allowPartial && !cleaned.isEmpty()) return new Route(State.PARTIAL, cleaned,
            unloadedFrontier ? "loaded frontier" : "route stopped before target");
        if (unloadedFrontier) return blocked("loaded frontier unavailable");
        return blocked(cleaned.isEmpty() ? "no clear route" : "route stopped before target");
    }

    public static Route planToward(CollisionView view, Vec3 start, Vec3 target, int frontierRadius,
                                   int verticalRange, int maxWaypoints) {
        if (view == null || start == null || target == null) return blocked("no collision view");
        if (start.distanceTo(target) <= 0.05D) return new Route(State.COMPLETE, List.of(), "arrived");

        if (Math.hypot(target.x - start.x, target.z - start.z) <= 0.05D) {
            if (!safeClear(view, target)) return blocked("target unsafe or unavailable");
            return complete(List.of(target), "vertical");
        }

        Route horizontal = planHorizontal(view, start, new Vec3(target.x, start.y, target.z),
            frontierRadius, verticalRange, maxWaypoints, true);
        if (horizontal.state() == State.BLOCKED) return horizontal;
        List<Vec3> route = new ArrayList<>(horizontal.waypoints());
        Vec3 end = route.isEmpty() ? start : route.getLast();
        boolean atTargetColumn = Math.hypot(end.x - target.x, end.z - target.z) <= 0.5D;
        if (atTargetColumn && safeClear(view, target)) {
            if (!safeLavaPath(view, end, target)) {
                return new Route(State.PARTIAL, clean(start, route), "lava-safe target approach unavailable");
            }
            if (!same(end, target)) route.add(target);
            return complete(clean(start, route), "target column");
        }
        return new Route(State.PARTIAL, clean(start, route), horizontal.detail());
    }

    public static Route planGroundToward(CollisionView view, Vec3 start, Vec3 target, int maxWaypoints) {
        return planHybridToward(view, start, target, HYBRID_MAX_FRONTIER, 128,
            Math.min(HYBRID_MAX_LEGS, Math.max(1, maxWaypoints)));
    }

    public static Route planGroundFirst(CollisionView view, Vec3 start, Vec3 target,
                                        int frontierRadius, int verticalRange, int maxWaypoints) {
        return planHybridToward(view, start, target, frontierRadius, verticalRange, maxWaypoints);
    }

    public static Route planHybridToward(CollisionView view, Vec3 start, Vec3 target,
                                         int frontierRadius, int verticalRange, int maxWaypoints) {
        if (view == null || start == null || target == null) return blocked("no collision view");
        if (start.distanceTo(target) <= 0.05D) return new Route(State.COMPLETE, List.of(), "arrived");

        HybridProbe probe = new HybridProbe(view, HYBRID_PROBE_BUDGET);
        if (horizontalDistance(start, target) <= 0.05D) {
            if (!probe.safeClear(target)) return blocked("lava-safe vertical target unavailable");
            return complete(List.of(target), "VClip target");
        }

        double radius = Math.max(1.0D, Math.min(HYBRID_MAX_FRONTIER, frontierRadius));
        Vec3 frontier = farthestLoadedFrontier(probe, start, target,
            Math.min(horizontalDistance(start, target), radius));
        if (frontier == null || horizontalDistance(start, frontier) < HYBRID_MIN_PROGRESS) {
            return unrestrictedClipRoute(view, start, target, radius, "unloaded safety wait");
        }
        boolean targetColumn = horizontalDistance(frontier, target) <= 0.05D;
        double preferredY = targetColumn ? target.y : start.y;
        double range = Math.max(0.0D, Math.min(128.0D, verticalRange));

        Vec3 anchor = findHybridAnchor(probe, frontier.x, frontier.z, preferredY,
            Math.min(HYBRID_MAX_ANCHOR_DOWN, range), Math.min(HYBRID_MAX_ANCHOR_UP, range));

        if (anchor != null) {
            List<Vec3> anchored = routeToStableAnchor(probe, start, anchor, target.y, range, false);
            Route route = finishHybridRoute(probe, start, target, anchored, targetColumn,
                "hybrid stable anchor");
            if (route.madeProgress()) return route;
        }

        HybridProbe topProbe = new HybridProbe(view, HYBRID_TOP_PROBE_BUDGET);
        Vec3 topExit = findTopLanding(topProbe, start, range);
        List<Vec3> topAdvance = topAdvance(topProbe, start, frontier, topExit);
        if (!topAdvance.isEmpty()) {
            List<Vec3> topLanding = new ArrayList<>(topAdvance);
            if (anchor != null && topLanding.size() < HYBRID_MAX_LEGS && !same(topLanding.getLast(), anchor)) {
                topLanding.add(anchor);
            }
            Route topRoute = finishHybridRoute(topProbe, start, target, topLanding, targetColumn, "VClip Top escape");
            if (topRoute.madeProgress()) return topRoute;
        }
        if (topExit != null && safeLavaPath(topProbe, start, topExit)) {
            return new Route(State.ESCAPE, clean(start, List.of(topExit)), "VClip Top preparatory escape");
        }

        if (anchor != null) {
            List<Vec3> layeredAnchor = routeToStableAnchor(probe, start, anchor, target.y, range, true);
            Route layeredRoute = finishHybridRoute(probe, start, target, layeredAnchor, targetColumn,
                "extended V/H/V anchor");
            if (layeredRoute.madeProgress()) return layeredRoute;
        }

        List<Vec3> airborne = routeToAirFrontier(probe, start, frontier, target, targetColumn, range);
        Route airRoute = finishHybridRoute(probe, start, target, airborne, targetColumn,
            "air fallback");
        if (airRoute.madeProgress()) return airRoute;

        Vec3 advance = farthestClearAdvance(probe, start, frontier);
        if (advance != null && horizontalDistance(start, advance) >= HYBRID_MIN_PROGRESS) {
            Vec3 localAnchor = findHybridAnchor(probe, advance.x, advance.z, start.y,
                Math.min(16.0D, range), Math.min(8.0D, range));
            if (localAnchor != null) {
                List<Vec3> localRoute = routeToStableAnchor(probe, start, localAnchor, target.y,
                    Math.min(16.0D, range));
                if (!localRoute.isEmpty()) {
                    return new Route(State.PARTIAL, localRoute, "stable obstacle frontier");
                }
            }
            return new Route(State.PARTIAL, clean(start, List.of(advance)), "air obstacle frontier");
        }
        return unrestrictedClipRoute(view, start, target, radius,
            probe.exhausted() ? "probe-budget unrestricted fallback" : "unrestricted clip fallback");
    }

    public static Vec3 nearestClear(CollisionView view, Vec3 target, double radius) {
        if (view == null || target == null || !view.loaded(target)) return null;
        if (safeClear(view, target)) return target;
        double limit = Math.max(0.0D, radius);
        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double dy = -limit; dy <= limit + 1.0E-9D; dy += 0.5D) {
            for (double dx = -limit; dx <= limit + 1.0E-9D; dx += 0.5D) {
                for (double dz = -limit; dz <= limit + 1.0E-9D; dz += 0.5D) {
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance > limit * limit + 1.0E-9D || distance >= bestDistance) continue;
                    Vec3 candidate = new Vec3(target.x + dx, clampY(view, target.y + dy), target.z + dz);
                    if (!safeClear(view, candidate)) continue;
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public static Vec3 nearestGrounded(CollisionView view, Vec3 target, double radius) {
        if (view == null || target == null || !view.loaded(target)) return null;
        if (safeClear(view, target) && view.traversable(target)) return target;
        double limit = Math.max(0.0D, radius);
        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double dy = -limit; dy <= limit + 1.0E-9D; dy += 0.0625D) {
            for (double dx = -limit; dx <= limit + 1.0E-9D; dx += 0.5D) {
                for (double dz = -limit; dz <= limit + 1.0E-9D; dz += 0.5D) {
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance > limit * limit + 1.0E-9D || distance >= bestDistance) continue;
                    Vec3 candidate = new Vec3(target.x + dx, clampY(view, target.y + dy), target.z + dz);
                    if (!safeClear(view, candidate) || !view.traversable(candidate)) continue;
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public static Vec3 safestLavaLanding(CollisionView view, Vec3 target, double radius) {
        if (view == null || target == null) return null;
        if (safeClear(view, target) && view.traversable(target)) return target;
        int maxRing = Math.max(0, Math.min(64, (int) Math.ceil(radius)));
        double[] yOffsets = {0.0D, 0.5D, -0.5D, 1.0D, -1.0D, 2.0D, -2.0D,
            3.0D, -3.0D, 4.0D, -4.0D};
        Vec3 best = null;
        double bestClearance = Double.NEGATIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestVertical = Double.POSITIVE_INFINITY;
        int firstSafeRing = -1;
        for (int ring = 0; ring <= maxRing; ring++) {
            if (firstSafeRing >= 0 && ring > Math.min(maxRing, firstSafeRing + 6)) break;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (ring > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (double dy : yOffsets) {
                        Vec3 candidate = new Vec3(target.x + dx,
                            clampY(view, target.y + dy), target.z + dz);
                        if (!safeClear(view, candidate) || !view.traversable(candidate)) continue;
                        if (firstSafeRing < 0) firstSafeRing = ring;
                        double clearance = view.lavaClearance(candidate);
                        if (Double.isNaN(clearance)) clearance = 0.0D;
                        if (clearance >= LAVA_SOFT_SCAN) return candidate;
                        double distance = candidate.distanceToSqr(target);
                        double vertical = Math.abs(candidate.y - target.y);
                        if (betterLavaLanding(candidate, clearance, distance, vertical,
                            best, bestClearance, bestDistance, bestVertical)) {
                            best = candidate;
                            bestClearance = clearance;
                            bestDistance = distance;
                            bestVertical = vertical;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean betterLavaLanding(Vec3 candidate, double clearance, double distance, double vertical,
                                              Vec3 best, double bestClearance,
                                              double bestDistance, double bestVertical) {
        if (best == null) return true;
        int clearanceOrder = Double.compare(clearance, bestClearance);
        if (clearanceOrder != 0) return clearanceOrder > 0;
        int distanceOrder = Double.compare(distance, bestDistance);
        if (distanceOrder != 0) return distanceOrder < 0;
        int verticalOrder = Double.compare(vertical, bestVertical);
        if (verticalOrder != 0) return verticalOrder < 0;
        int xOrder = Double.compare(candidate.x, best.x);
        if (xOrder != 0) return xOrder < 0;
        int yOrder = Double.compare(candidate.y, best.y);
        if (yOrder != 0) return yOrder < 0;
        return candidate.z < best.z;
    }

    public static Vec3 nearestGroundedAtColumn(CollisionView view, Vec3 target, double verticalRange) {
        if (view == null || target == null || !view.loaded(target)) return null;
        double range = Math.max(0.0D, Math.min(192.0D, verticalRange));
        return findHybridAnchor(new HybridProbe(view, HYBRID_PROBE_BUDGET), target.x, target.z, target.y,
            range, range);
    }

    public static Vec3 findTopLanding(CollisionView view, Vec3 start, double verticalRange) {
        if (view == null || start == null) return null;
        return findTopLanding(new HybridProbe(view, HYBRID_PROBE_BUDGET), start,
            Math.max(0.0D, Math.min(128.0D, verticalRange)));
    }

    private static Vec3 findTopLanding(HybridProbe probe, Vec3 start, double verticalRange) {
        if (probe == null || start == null) return null;
        boolean seenBlocked = false;
        double range = Math.max(0.0D, Math.min(128.0D, verticalRange));
        for (double offset = 0.5D; offset <= range + 1.0E-9D && !probe.exhausted(); offset += 0.5D) {
            Vec3 candidate = new Vec3(start.x, clampY(probe.view, start.y + offset), start.z);
            if (sameY(candidate.y, start.y)) break;
            if (!probe.loaded(candidate)) continue;
            if (!probe.clear(candidate)) {
                seenBlocked = true;
                continue;
            }
            if (seenBlocked && probe.lavaSafe(candidate) && probe.supported(candidate)) return candidate;
        }
        return null;
    }

    private static List<Vec3> topAdvance(HybridProbe probe, Vec3 start, Vec3 frontier, Vec3 topExit) {
        if (topExit == null || frontier == null) return List.of();
        Vec3 end = new Vec3(frontier.x, topExit.y, frontier.z);
        if (!safeLavaPath(probe, start, topExit)) return List.of();
        if (!probe.safeClear(end)) return List.of();
        if (!clearHybridHorizontalPath(probe, topExit, end)) return List.of();
        return clean(start, List.of(topExit, end));
    }

    private static Route unrestrictedClipRoute(CollisionView view, Vec3 start, Vec3 target,
                                               double frontierRadius, String detail) {
        List<Vec3> route = safeFallbackWaypoints(view, start, target, frontierRadius);
        if (route.isEmpty()) return blocked("lava safety wait: " + detail);
        return new Route(same(route.getLast(), target) ? State.COMPLETE : State.PARTIAL, route, detail);
    }

    public static List<Vec3> safeFallbackWaypoints(CollisionView view, Vec3 start, Vec3 target,
                                                    double frontierRadius) {
        if (view == null || start == null || target == null) return List.of();
        double horizontal = horizontalDistance(start, target);
        if (horizontal <= 0.05D) {
            return safeLavaPath(view, start, target) && knownLavaSafe(view, target)
                ? clean(start, List.of(target)) : List.of();
        }
        double amount = Math.min(horizontal, Math.max(1.0D, Math.min(HYBRID_MAX_FRONTIER, frontierRadius)));
        Vec3 horizontalEnd = toward(start, target, amount);
        boolean targetColumn = amount + 0.05D >= horizontal;
        List<Vec3> direct = finishSafeFallback(view, start, horizontalEnd, target, targetColumn, List.of());
        if (!direct.isEmpty()) return direct;

        double[] offsets = {3.0D, -3.0D, 4.0D, -4.0D, 8.0D, -8.0D, 16.0D, -16.0D,
            32.0D, -32.0D, 48.0D, -48.0D, 64.0D, -64.0D, 80.0D, -80.0D,
            96.0D, -96.0D, 112.0D, -112.0D, 128.0D, -128.0D};
        for (double offset : offsets) {
            double layerY = clampY(view, start.y + offset);
            if (sameY(layerY, start.y)) continue;
            Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
            Vec3 escapeEnd = new Vec3(horizontalEnd.x, layerY, horizontalEnd.z);
            if (!safeLavaPath(view, start, escapeStart)) continue;
            List<Vec3> layered = finishSafeFallback(
                view, escapeStart, escapeEnd, target, targetColumn, List.of(escapeStart));
            if (!layered.isEmpty()) return clean(start, layered);
        }
        return List.of();
    }

    private static List<Vec3> finishSafeFallback(CollisionView view, Vec3 segmentStart, Vec3 horizontalEnd,
                                                  Vec3 target, boolean targetColumn, List<Vec3> prefix) {
        if (!safeLavaPath(view, segmentStart, horizontalEnd)) return List.of();
        List<Vec3> route = new ArrayList<>(prefix);
        route.add(horizontalEnd);
        if (targetColumn && !same(horizontalEnd, target)) {
            if (!safeLavaPath(view, horizontalEnd, target) || !knownLavaSafe(view, target)) return List.of();
            route.add(target);
        }
        return List.copyOf(route);
    }

    private static boolean safeLavaPath(CollisionView view, Vec3 from, Vec3 to) {
        if (view == null || from == null || to == null) return false;
        double distance = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(distance / HYBRID_PATH_SAMPLE));
        for (int i = 1; i <= samples; i++) {
            Vec3 sample = from.lerp(to, i / (double) samples);
            if (!view.loaded(sample) || !view.lavaSafe(sample)) return false;
        }
        return true;
    }

    private static boolean safeLavaPath(HybridProbe probe, Vec3 from, Vec3 to) {
        if (probe == null || from == null || to == null) return false;
        double distance = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(distance / HYBRID_PATH_SAMPLE));
        for (int i = 1; i <= samples && !probe.exhausted(); i++) {
            Vec3 sample = from.lerp(to, i / (double) samples);
            if (!probe.loaded(sample) || !probe.lavaSafe(sample)) return false;
        }
        return !probe.exhausted();
    }

    private static Route finishHybridRoute(HybridProbe probe, Vec3 start, Vec3 target, List<Vec3> route,
                                           boolean targetColumn, String detail) {
        List<Vec3> cleaned = clean(start, route);
        if (cleaned.size() > HYBRID_MAX_LEGS) cleaned = List.copyOf(cleaned.subList(0, HYBRID_MAX_LEGS));
        if (cleaned.isEmpty()) return blocked(probe.exhausted() ? "collision probe budget exhausted" : detail);
        Vec3 end = cleaned.getLast();
        if (same(end, target)) return complete(cleaned, detail);
        if (targetColumn && horizontalDistance(end, target) <= 0.05D && cleaned.size() < HYBRID_MAX_LEGS
            && probe.safeClear(target) && safeLavaPath(probe, end, target)) {
            List<Vec3> completed = new ArrayList<>(cleaned);
            completed.add(target);
            return complete(clean(start, completed), detail + " then VClip target");
        }
        return new Route(State.PARTIAL, cleaned, detail);
    }

    private static List<Vec3> routeToStableAnchor(HybridProbe probe, Vec3 start, Vec3 anchor,
                                                   double targetY, double verticalRange) {
        return routeToStableAnchor(probe, start, anchor, targetY, verticalRange, true);
    }

    private static List<Vec3> routeToStableAnchor(HybridProbe probe, Vec3 start, Vec3 anchor,
                                                   double targetY, double verticalRange,
                                                   boolean includeLayerScan) {
        if (horizontalDistance(start, anchor) <= 0.05D) return clean(start, List.of(anchor));

        Vec3 startAtAnchorY = new Vec3(start.x, anchor.y, start.z);
        if (probe.safeClear(startAtAnchorY)
            && probe.safeClear(anchor)
            && safeLavaPath(probe, start, startAtAnchorY)
            && clearHybridHorizontalPath(probe, startAtAnchorY, anchor)) {
            return clean(start, List.of(startAtAnchorY, anchor));
        }

        Vec3 endAtStartY = new Vec3(anchor.x, start.y, anchor.z);
        if (probe.safeClear(endAtStartY)
            && clearHybridHorizontalPath(probe, start, endAtStartY)
            && safeLavaPath(probe, endAtStartY, anchor)) {
            return clean(start, List.of(endAtStartY, anchor));
        }

        if (!includeLayerScan) return List.of();

        for (double layerY : hybridLayerHeights(probe, start.y, anchor.y, targetY, verticalRange)) {
            if (sameY(layerY, start.y) || sameY(layerY, anchor.y)) continue;
            Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
            Vec3 escapeEnd = new Vec3(anchor.x, layerY, anchor.z);
            if (!probe.safeClear(escapeStart)) continue;
            if (!probe.safeClear(escapeEnd)) continue;
            if (!safeLavaPath(probe, start, escapeStart)) continue;
            if (!clearHybridHorizontalPath(probe, escapeStart, escapeEnd)) continue;
            if (!safeLavaPath(probe, escapeEnd, anchor)) continue;
            return clean(start, List.of(escapeStart, escapeEnd, anchor));
        }
        return List.of();
    }

    private static List<Vec3> routeToAirFrontier(HybridProbe probe, Vec3 start, Vec3 frontier, Vec3 target,
                                                  boolean targetColumn, double verticalRange) {
        Vec3 endAtStartY = new Vec3(frontier.x, start.y, frontier.z);
        if (probe.safeClear(endAtStartY)
            && clearHybridHorizontalPath(probe, start, endAtStartY)) {
            List<Vec3> route = new ArrayList<>(2);
            route.add(endAtStartY);
            if (targetColumn && !same(endAtStartY, target) && probe.safeClear(target)
                && safeLavaPath(probe, endAtStartY, target)) {
                route.add(target);
            }
            return clean(start, route);
        }

        for (double layerY : hybridLayerHeights(probe, start.y, target.y, target.y, verticalRange)) {
            if (sameY(layerY, start.y)) continue;
            Vec3 escapeStart = new Vec3(start.x, layerY, start.z);
            Vec3 escapeEnd = new Vec3(frontier.x, layerY, frontier.z);
            if (!probe.safeClear(escapeStart)) continue;
            if (!probe.safeClear(escapeEnd)) continue;
            if (!safeLavaPath(probe, start, escapeStart)) continue;
            if (!clearHybridHorizontalPath(probe, escapeStart, escapeEnd)) continue;
            List<Vec3> route = new ArrayList<>(3);
            route.add(escapeStart);
            route.add(escapeEnd);
            if (targetColumn && !same(escapeEnd, target) && probe.safeClear(target)
                && safeLavaPath(probe, escapeEnd, target)) {
                route.add(target);
            }
            return clean(start, route);
        }
        return List.of();
    }

    private static Vec3 farthestLoadedFrontier(HybridProbe probe, Vec3 start, Vec3 target, double maxDistance) {
        double distance = Math.max(0.0D, maxDistance);
        while (distance >= HYBRID_MIN_PROGRESS - 1.0E-9D && !probe.exhausted()) {
            Vec3 candidate = toward(start, target, distance);
            if (probe.loaded(candidate)) return candidate;
            distance -= 2.0D;
        }
        return null;
    }

    private static Vec3 farthestClearAdvance(HybridProbe probe, Vec3 start, Vec3 target) {
        double distance = horizontalDistance(start, target);
        if (distance < HYBRID_MIN_PROGRESS) return null;
        Vec3 best = null;
        for (double step = HYBRID_PATH_SAMPLE; step <= distance + 1.0E-9D && !probe.exhausted();
             step += HYBRID_PATH_SAMPLE) {
            Vec3 candidate = toward(start, target, Math.min(step, distance));
            if (!probe.safeClear(candidate)) break;
            best = candidate;
        }
        return best;
    }

    private static boolean clearHybridHorizontalPath(HybridProbe probe, Vec3 from, Vec3 to) {
        if (!sameY(from.y, to.y)) return false;
        double distance = horizontalDistance(from, to);
        int samples = Math.max(1, (int) Math.ceil(distance / HYBRID_PATH_SAMPLE));
        for (int i = 1; i <= samples && !probe.exhausted(); i++) {
            Vec3 candidate = from.lerp(to, i / (double) samples);
            if (!probe.safeClear(candidate)) return false;
        }
        if (probe.exhausted()) return false;
        Entity entity = probe.view.entity();
        if (entity != null) {

            int segments = Math.max(1, (int) Math.ceil(distance / 8.0D));
            Vec3 prev = from;
            for (int i = 1; i <= segments; i++) {
                Vec3 next = from.lerp(to, i / (double) segments);
                if (!PacketClipSafety.sweptClear(entity, prev, next)) return false;
                prev = next;
            }
        }
        return true;
    }

    private static Vec3 findHybridAnchor(HybridProbe probe, double x, double z, double preferredY,
                                         double downRange, double upRange) {
        double preferred = clampY(probe.view, preferredY);
        Vec3 exact = stableCandidate(probe, x, z, preferred);
        if (exact != null) return exact;
        Vec3 down = scanHybridAnchor(probe, x, z, preferred, -1.0D,
            Math.max(0.0D, downRange));
        if (down != null) return down;
        return scanHybridAnchor(probe, x, z, preferred, 1.0D, Math.max(0.0D, upRange));
    }

    private static Vec3 scanHybridAnchor(HybridProbe probe, double x, double z, double preferredY,
                                         double direction, double range) {
        double previousY = preferredY;
        Vec3 previous = new Vec3(x, previousY, z);
        boolean previousClear = probe.loaded(previous) && probe.clear(previous);
        for (double offset = HYBRID_ANCHOR_SAMPLE;
             offset <= range + 1.0E-9D && !probe.exhausted(); offset += HYBRID_ANCHOR_SAMPLE) {
            double y = clampY(probe.view, preferredY + direction * offset);
            if (sameY(y, previousY)) break;
            Vec3 candidate = new Vec3(x, y, z);
            AnchorCheck check = probe.anchor(candidate);
            if (check.stable()) return candidate;
            boolean clear = check.clear();
            if (clear != previousClear) {
                Vec3 boundary = refineStableBoundary(probe, x, z, previousY, previousClear, y, clear);
                if (boundary != null) return boundary;
            }
            previousY = y;
            previousClear = clear;
        }
        return null;
    }

    private static Vec3 refineStableBoundary(HybridProbe probe, double x, double z,
                                              double firstY, boolean firstClear,
                                              double secondY, boolean secondClear) {
        if (firstClear == secondClear) return null;
        double clearY = firstClear ? firstY : secondY;
        double blockedY = firstClear ? secondY : firstY;
        for (int i = 0; i < 7 && !probe.exhausted(); i++) {
            double middleY = (clearY + blockedY) * 0.5D;
            Vec3 middle = new Vec3(x, middleY, z);
            if (probe.loaded(middle) && probe.clear(middle)) clearY = middleY;
            else blockedY = middleY;
        }
        return stableCandidate(probe, x, z, clearY);
    }

    private static Vec3 stableCandidate(HybridProbe probe, double x, double z, double y) {
        double clamped = clampY(probe.view, y);
        Vec3 candidate = new Vec3(x, clamped, z);
        if (probe.anchor(candidate).stable()) return candidate;
        double snapped = clampY(probe.view, Math.rint(clamped * 16.0D) / 16.0D);
        if (!sameY(snapped, clamped)) {
            Vec3 snappedCandidate = new Vec3(x, snapped, z);
            if (probe.anchor(snappedCandidate).stable()) return snappedCandidate;
        }
        return null;
    }

    private static List<Double> hybridLayerHeights(HybridProbe probe, double startY, double anchorY,
                                                    double targetY, double verticalRange) {
        List<Double> heights = new ArrayList<>(32);
        addHybridLayer(heights, clampY(probe.view, startY));
        addHybridLayer(heights, clampY(probe.view, anchorY));
        addHybridLayer(heights, clampY(probe.view, targetY));
        int[] offsets = {1, -1, 2, -2, 3, 4, -3, -4, 6, -6, 8, -8,
            12, -12, 16, -16, 24, -24, 32, -32, 48, -48, 64, -64,
            80, -80, 96, -96, 112, -112, 128, -128};
        double range = Math.max(0.0D, Math.min(128.0D, verticalRange));
        for (int offset : offsets) {
            if (heights.size() >= 32) break;
            if (Math.abs(offset) > range + 1.0E-9D) continue;
            addHybridLayer(heights, clampY(probe.view, startY + offset));
        }
        return List.copyOf(heights);
    }

    private static void addHybridLayer(List<Double> heights, double y) {
        for (double existing : heights) {
            if (sameY(existing, y)) return;
        }
        heights.add(y);
    }

    private static boolean sameY(double a, double b) {
        return Math.abs(a - b) <= 1.0E-5D;
    }

    private record AnchorCheck(boolean clear, boolean stable) {}

    private static final class HybridProbe {
        final CollisionView view;
        private int remaining;

        HybridProbe(CollisionView view, int budget) {
            this.view = view;
            this.remaining = Math.max(1, budget);
        }

        boolean loaded(Vec3 position) {
            return claim() && view.loaded(position);
        }

        boolean clear(Vec3 position) {
            return claim() && view.clear(position);
        }

        boolean lavaSafe(Vec3 position) {
            return claim() && view.lavaSafe(position);
        }

        boolean safeClear(Vec3 position) {
            return loaded(position) && lavaSafe(position) && clear(position);
        }

        boolean supported(Vec3 position) {
            return claim() && view.supported(position);
        }

        AnchorCheck anchor(Vec3 position) {
            if (!loaded(position)) return new AnchorCheck(false, false);
            if (!lavaSafe(position)) return new AnchorCheck(false, false);
            boolean clear = clear(position);
            if (!clear || !claim()) return new AnchorCheck(clear, false);
            return new AnchorCheck(true, view.traversable(position));
        }

        boolean exhausted() {
            return remaining <= 0;
        }

        private boolean claim() {
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }
    }

    static boolean reached(Vec3 actual, Vec3 target, double tolerance) {
        return actual != null && target != null && actual.distanceTo(target) <= Math.max(0.0D, tolerance);
    }

    private static Route layered(CollisionView view, Vec3 start, Vec3 target, int escapeRange, int routeCap) {
        if (!view.loaded(target)) return blocked("target chunk unavailable");
        for (int dy : verticalOffsets(escapeRange)) {
            if (dy == 0) continue;
            double y = clampY(view, start.y + dy);
            Vec3 escapeStart = new Vec3(start.x, y, start.z);
            Vec3 escapeEnd = new Vec3(target.x, y, target.z);
            if (!safeClear(view, escapeStart)) continue;
            if (!safeClear(view, escapeEnd)) continue;
            if (!safeLavaPath(view, start, escapeStart)) continue;
            if (!clearHorizontalPath(view, escapeStart, escapeEnd)) continue;
            List<Vec3> route = new ArrayList<>(3);
            route.add(escapeStart);
            route.add(escapeEnd);
            Vec3 landing = nearestClearAtColumn(view, target.x, target.z, start.y, 128.0D);
            if (landing != null && !same(landing, escapeEnd)
                && safeLavaPath(view, escapeEnd, landing)) route.add(landing);
            List<Vec3> cleaned = clean(start, route);
            if (!cleaned.isEmpty() && cleaned.size() <= routeCap) return complete(cleaned, "layered dy=" + dy);
        }
        return blocked("no clear layer");
    }

    private static Vec3 nearestClearAtColumn(CollisionView view, double x, double z, double preferredY, double range) {
        for (double offset = 0.0D; offset <= range; offset += 0.5D) {
            int[] signs = offset == 0.0D ? new int[]{1} : new int[]{1, -1};
            for (int sign : signs) {
                Vec3 candidate = new Vec3(x, clampY(view, preferredY + sign * offset), z);
                if (safeClear(view, candidate)) return candidate;
            }
        }
        return null;
    }

    private static Vec3 maxClearAdvance(CollisionView view, Vec3 from, Vec3 target, int searchRadius) {
        double distance = horizontalDistance(from, target);
        if (distance < 1.0E-5D) return from;
        double maxDistance = Math.min(distance, Math.max(1, searchRadius));
        Entity entity = view.entity();
        Vec3 best = from;
        for (double step = ADVANCE_SAMPLE; step <= maxDistance + 1.0E-9D; step += ADVANCE_SAMPLE) {
            Vec3 candidate = new Vec3(
                from.x + (target.x - from.x) / distance * step,
                from.y,
                from.z + (target.z - from.z) / distance * step);

            if (entity != null ? !PacketClipSafety.sweptClear(entity, best, candidate) : !safeClear(view, candidate)) break;
            best = candidate;
        }
        return best;
    }

    private static boolean clearHorizontalPath(CollisionView view, Vec3 from, Vec3 to) {
        if (Math.abs(from.y - to.y) > 1.0E-5D) return false;
        Entity entity = view.entity();
        if (entity != null) {

            double distance = from.distanceTo(to);
            int segments = Math.max(1, (int) Math.ceil(distance / 8.0D));
            Vec3 prev = from;
            for (int i = 1; i <= segments; i++) {
                Vec3 next = from.lerp(to, i / (double) segments);
                if (!PacketClipSafety.sweptClear(entity, prev, next)) return false;
                prev = next;
            }
            return true;
        }
        double distance = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(distance / PATH_SAMPLE));
        for (int i = 1; i <= samples; i++) {
            Vec3 candidate = from.lerp(to, i / (double) samples);
            if (!safeClear(view, candidate)) return false;
        }
        return true;
    }

    private static List<Integer> verticalOffsets(int range) {
        List<Integer> offsets = new ArrayList<>(range * 2 + 1);
        offsets.add(0);
        for (int i = 1; i <= range; i++) {
            offsets.add(i);
            offsets.add(-i);
        }
        return offsets;
    }

    private static List<Vec3> clean(Vec3 start, List<Vec3> route) {
        List<Vec3> cleaned = new ArrayList<>();
        Vec3 previous = start;
        for (Vec3 waypoint : route) {
            if (waypoint == null || same(previous, waypoint)) continue;
            cleaned.add(waypoint);
            previous = waypoint;
        }
        return List.copyOf(cleaned);
    }

    private static boolean safeClear(CollisionView view, Vec3 position) {
        return knownLavaSafe(view, position) && view.clear(position);
    }

    private static boolean knownLavaSafe(CollisionView view, Vec3 position) {
        return view != null && position != null && view.loaded(position) && view.lavaSafe(position);
    }

    private static Vec3 toward(Vec3 from, Vec3 target, double distance) {
        double horizontal = horizontalDistance(from, target);
        if (horizontal <= distance) return new Vec3(target.x, from.y, target.z);
        return new Vec3(from.x + (target.x - from.x) / horizontal * distance, from.y,
            from.z + (target.z - from.z) / horizontal * distance);
    }

    private static double clampY(CollisionView view, double y) {
        return Math.max(view.minY(), Math.min(view.maxFeetY(), y));
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static boolean same(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) < 1.0E-10D;
    }

    private static Route complete(List<Vec3> waypoints, String detail) {
        return new Route(State.COMPLETE, waypoints, detail);
    }

    private static Route blocked(String detail) {
        return new Route(State.BLOCKED, List.of(), detail);
    }
}
