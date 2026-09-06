package autismclient.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AutismPathWalker {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final double ARRIVE_DIST_SQ = 1.2 * 1.2;

    private static final double NODE_REACH_SQ = 0.5 * 0.5;

    private static final double DEVIATE_DIST_SQ = 1.5 * 1.5;

    private static final int FALL_BELOW = 2;

    private static final int GOAL_DRIFT_KEEP_SQ = 3 * 3;

    private static final int MAX_EXPAND = 6000;
    private static final int PREVIEW_MAX_EXPAND = 1200;

    private static final int REPLAN_INTERVAL_TICKS = 10;

    private static final int MAX_RANGE = 40;

    private static final int MAX_DROP = 3;

    private static final int SMOOTH_LOOKAHEAD = 12;

    private static final int HAZARD_STEP_COST = 30;

    private static final int FIELD_STEP_COST = 4;

    private static final int TRAMPLE_STEP_COST = 26;

    private static final int GAP_JUMP_COST = 18;

    private static final double STEP_HEIGHT = 0.6;
    private static final double BODY_HALF_WIDTH = 0.3;
    private static final double BODY_HEIGHT = 1.8;

    private static final float DEADZONE_DEG = 12.0F;

    private static final double JUMP_RANGE_SQ = 1.6 * 1.6;

    private static final double STEPUP_JUMP_RANGE_SQ = 1.9 * 1.9;

    private static final int STUCK_TICKS = 15;

    private static final double STUCK_PROGRESS_SQ = 0.3 * 0.3;

    private static final int FAIL_BLACKLIST_TICKS = 100;

    private static final int MAX_FAIL_BLACKLIST_TICKS = 400;

    private static final int UNSTICK_JUMP_TICKS = 5;

    private static final int WALL_FAIL_TICKS = 8;

    private static final int BIT_W = 1;
    private static final int BIT_A = 2;
    private static final int BIT_S = 4;
    private static final int BIT_D = 8;
    private static final int BIT_JUMP = 16;

    private static final int BIT_SPRINT = 32;

    private static final float[] COMBO_ANGLE = {0, -45, 45, -90, 90, -135, 135, 180};
    private static final int[] COMBO_MASK = {
        BIT_W,
        BIT_W | BIT_A,
        BIT_W | BIT_D,
        BIT_A,
        BIT_D,
        BIT_S | BIT_A,
        BIT_S | BIT_D,
        BIT_S
    };

    private static final int[] DIR_X = {1, -1, 0, 0};
    private static final int[] DIR_Z = {0, 0, 1, -1};

    private static final class PathNode {
        final int x, y, z;
        final boolean jump;

        PathNode(int x, int y, int z, boolean jump) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.jump = jump;
        }
    }

    private static final class OpenEntry implements Comparable<OpenEntry> {
        final long key;
        final int f;

        OpenEntry(long key, int f) {
            this.key = key;
            this.f = f;
        }

        @Override
        public int compareTo(OpenEntry other) {
            return Integer.compare(f, other.f);
        }
    }

    private static final ArrayList<PathNode> path = new ArrayList<>();
    private static int pathIndex;
    private static BlockPos activeTarget;

    private static int prevNodeX, prevNodeY, prevNodeZ;

    private static final HashMap<Long, Integer> gScore = new HashMap<>();
    private static final HashMap<Long, Long> cameFrom = new HashMap<>();

    private static final HashSet<Long> jumpCells = new HashSet<>();
    private static final HashSet<Long> closed = new HashSet<>();
    private static final PriorityQueue<OpenEntry> open = new PriorityQueue<>();
    private static final ArrayList<Long> rawChain = new ArrayList<>();
    private static final ArrayList<PathNode> smoothed = new ArrayList<>();
    private static final HashSet<Long> goalCells = new HashSet<>();
    private static final HashMap<Long, Boolean> walkableCache = new HashMap<>();
    private static boolean cacheWalkability;
    private static final HashMap<BlockPos, Double> previewCosts = new HashMap<>();
    private static int previewTick = Integer.MIN_VALUE;
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static int goalX, goalY, goalZ;

    private static int rangeAnchorX, rangeAnchorZ;

    private static BlockPos lastPlanTarget;
    private static int lastPlanTick;

    private static double progressX;
    private static double progressZ;
    private static int progressTicks;

    private static int jumpHeldTicks;

    private static int heldMask;

    private static boolean steeringAtGoal;

    private static final HashMap<BlockPos, Integer> blacklistTick = new HashMap<>();

    private static final HashMap<BlockPos, Integer> blacklistWindow = new HashMap<>();

    private static final HashMap<BlockPos, Integer> failCounts = new HashMap<>();

    private AutismPathWalker() {
    }

    public static boolean isStandable(BlockPos pos) {
        return pos != null && MC != null && MC.level != null
            && walkable(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean hasArrived(BlockPos target) {
        if (target == null || MC == null || MC.level == null || MC.player == null) return false;
        double dx = target.getX() + 0.5 - MC.player.getX();
        double dz = target.getZ() + 0.5 - MC.player.getZ();
        return dx * dx + dz * dz <= ARRIVE_DIST_SQ
            && targetVisible(MC.player.getEyePosition(), target);
    }

    public static double estimateTravelCost(BlockPos target) {
        if (target == null || MC == null || MC.level == null || MC.player == null
            || isBlacklisted(target)) return Double.POSITIVE_INFINITY;
        int now = AutismSharedState.get().getClientTickCounter();
        if (previewTick != now) {
            previewTick = now;
            previewCosts.clear();
        }
        Double cached = previewCosts.get(target);
        if (cached != null) return cached;
        double cost = hasArrived(target) ? 0.0 : search(MC.player.getX(), MC.player.getY(),
            MC.player.getZ(), target, false, PREVIEW_MAX_EXPAND);
        previewCosts.put(target.immutable(), cost);
        return cost;
    }

    private static boolean targetVisible(Vec3 eye, BlockPos target) {
        Vec3 aim = Vec3.atCenterOf(target);
        double reach = MC.player.blockInteractionRange();
        if (eye.distanceToSqr(aim) > reach * reach) return false;
        BlockHitResult hit = MC.level.clip(new ClipContext(eye, aim,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    public static void reset() {
        stop();
        blacklistTick.clear();
        blacklistWindow.clear();
        failCounts.clear();
    }

    public static void stop() {
        releaseKeys();
        path.clear();
        pathIndex = 0;
        activeTarget = null;
        lastPlanTarget = null;
        lastPlanTick = 0;
        progressTicks = 0;
        jumpHeldTicks = 0;

        gScore.clear();
        cameFrom.clear();
        jumpCells.clear();
        closed.clear();
        open.clear();
        rawChain.clear();
        smoothed.clear();
        goalCells.clear();
        walkableCache.clear();
        previewCosts.clear();
        previewTick = Integer.MIN_VALUE;
        steeringAtGoal = false;
    }

    public static boolean tick(BlockPos target) {
        if (MC == null || MC.player == null || MC.level == null || MC.options == null) {

            releaseKeys();
            clearRoute();
            activeTarget = null;
            return false;
        }
        if (target == null) {
            releaseKeys();
            clearRoute();
            return false;
        }
        int now = AutismSharedState.get().getClientTickCounter();
        if (isBlacklisted(target)) {
            releaseKeys();
            clearRoute();
            activeTarget = null;
            return false;
        }

        LocalPlayer player = MC.player;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        if (activeTarget == null || !activeTarget.equals(target)) {
            if (activeTarget != null && withinPlanDrift(target)) {
                activeTarget = target.immutable();
                if (pathIndex >= path.size() && heldMask == 0) {

                    progressTicks = 0;
                    jumpHeldTicks = 0;
                    progressX = px;
                    progressZ = pz;
                }
            } else {
                releaseKeys();
                clearRoute();
                activeTarget = target.immutable();
                progressTicks = 0;
                jumpHeldTicks = 0;
                progressX = px;
                progressZ = pz;
            }
        }

        if (hasArrived(activeTarget)) {

            failCounts.remove(activeTarget);

            progressTicks = 0;
            jumpHeldTicks = 0;
            progressX = px;
            progressZ = pz;
            releaseKeys();
            clearRoute();
            return false;
        }

        int feetY = Mth.floor(py);
        advanceReachedNodes(px, py, pz, feetY);

        boolean followGoal = pathIndex >= path.size() && !path.isEmpty() && goalMovedWithinDrift()
            && clearWalkLine(px, py, pz, activeTarget.getX() + 0.5, activeTarget.getZ() + 0.5);

        boolean needPlan = pathIndex >= path.size() && !followGoal;
        if (pathIndex < path.size()) {
            PathNode node = path.get(pathIndex);
            double segX = node.x - prevNodeX;
            double segZ = node.z - prevNodeZ;
            double fromX = prevNodeX + 0.5;
            double fromZ = prevNodeZ + 0.5;
            double lenSq = segX * segX + segZ * segZ;
            double t = lenSq < 1.0E-6 ? 0.0
                : Mth.clamp(((px - fromX) * segX + (pz - fromZ) * segZ) / lenSq, 0.0, 1.0);
            double offX = px - (fromX + segX * t);
            double offZ = pz - (fromZ + segZ * t);
            if (offX * offX + offZ * offZ > DEVIATE_DIST_SQ || feetY < node.y - FALL_BELOW) {
                needPlan = true;
            }

            if (!needPlan && !node.jump && node.y == prevNodeY && player.onGround()
                && !hardHazard(Mth.floor(px), feetY, Mth.floor(pz))
                && !hardHazard(Mth.floor(px), feetY + 1, Mth.floor(pz))
                && AutismPathGeometry.safeRise(floorTop(prevNodeX, prevNodeY, prevNodeZ),
                    floorTop(node.x, node.y, node.z), STEP_HEIGHT)) {
                double nx = node.x + 0.5 - px;
                double nz = node.z + 0.5 - pz;
                double fraction = Math.min(1.0, 0.8 / Math.max(0.001, Math.hypot(nx, nz)));
                needPlan = !clearWalkLine(px, py, pz, px + nx * fraction, pz + nz * fraction, false);
            }
        }

        if (needPlan && canPlan(activeTarget, now)) {
            notePlan(activeTarget, now);
            if (!plan(px, py, pz)) {

                failActiveTarget();
                releaseKeys();
                clearRoute();
                return false;
            }
            pathIndex = 0;
            advanceReachedNodes(px, py, pz, feetY);
        } else if (needPlan) {

            releaseKeys();

            if (autismclient.modules.AutoTotemModule.movementInputPaused()
                || autismclient.modules.AutoArmorModule.movementInputPaused()) {
                progressX = px;
                progressZ = pz;
                progressTicks = 0;
                return true;
            }
            double movedX = px - progressX;
            double movedZ = pz - progressZ;
            if (movedX * movedX + movedZ * movedZ >= STUCK_PROGRESS_SQ) {
                progressX = px;
                progressZ = pz;
                progressTicks = 0;
            } else if (++progressTicks >= STUCK_TICKS) {
                failActiveTarget();
                releaseKeys();
                clearRoute();
                return false;
            }
            return true;
        }

        if (pathIndex >= path.size() && !followGoal) {

            releaseKeys();
            steeringAtGoal = false;
            if (!path.isEmpty()) clearRoute();
            return false;
        }

        int nodeX, nodeY, nodeZ;
        boolean nodeJump;
        if (pathIndex < path.size()) {
            PathNode node = path.get(pathIndex);
            nodeX = node.x;
            nodeY = node.y;
            nodeZ = node.z;
            nodeJump = node.jump;
            steeringAtGoal = false;
        } else {
            nodeX = activeTarget.getX();
            nodeY = activeTarget.getY();
            nodeZ = activeTarget.getZ();
            nodeJump = false;
            steeringAtGoal = true;
        }
        double dx = nodeX + 0.5 - px;
        double dz = nodeZ + 0.5 - pz;
        double distSq = dx * dx + dz * dz;

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = Mth.wrapDegrees(desiredYaw - player.getYRot());
        int mask = chooseComboMask(delta);

        double frac = py - feetY;
        int standCellY = frac > 0.5 ? feetY + 1 : feetY;
        boolean headroom = freeHead(Mth.floor(px), standCellY + 2, Mth.floor(pz));

        double jumpRangeSq = floorTop(nodeX, nodeY, nodeZ) - py > STEP_HEIGHT
            ? STEPUP_JUMP_RANGE_SQ : JUMP_RANGE_SQ;
        boolean jump = headroom && nodeJump && distSq <= jumpRangeSq;
        if (!jump && headroom && !steeringAtGoal && progressTicks >= UNSTICK_JUMP_TICKS) {

            jump = !onFarmland(px, feetY, pz) && !farmlandAt(nodeX, nodeY - 1, nodeZ);
        }

        int sprint = (mask & BIT_W) != 0 && !jump ? BIT_SPRINT : 0;
        applyHeld(mask | (jump ? BIT_JUMP : 0) | sprint);
        jumpHeldTicks = jump ? jumpHeldTicks + 1 : 0;

        if (autismclient.modules.AutoTotemModule.movementInputPaused()
            || autismclient.modules.AutoArmorModule.movementInputPaused()) {
            progressX = px;
            progressZ = pz;
            progressTicks = 0;
            return true;
        }

        double movedX = px - progressX;
        double movedZ = pz - progressZ;
        if (movedX * movedX + movedZ * movedZ >= STUCK_PROGRESS_SQ) {
            progressX = px;
            progressZ = pz;
            progressTicks = 0;
        } else {
            progressTicks++;

            if (progressTicks >= WALL_FAIL_TICKS
                && jumpHeldTicks >= WALL_FAIL_TICKS
                && unjumpableAhead(px, py, pz, standCellY, dx, dz, distSq)) {
                failActiveTarget();
                releaseKeys();
                clearRoute();
                return false;
            }
            if (progressTicks >= STUCK_TICKS) {
                failActiveTarget();
                releaseKeys();
                clearRoute();
                return false;
            }
        }
        return true;
    }

    public static BlockPos currentNode() {
        if (activeTarget == null) return null;
        if (pathIndex < path.size()) {
            PathNode node = path.get(pathIndex);
            return new BlockPos(node.x, node.y, node.z);
        }

        return steeringAtGoal ? activeTarget : null;
    }

    public static void blacklist(BlockPos target, int ticks) {
        if (target == null || ticks <= 0) return;
        BlockPos key = target.immutable();
        blacklistTick.put(key, AutismSharedState.get().getClientTickCounter());
        blacklistWindow.put(key, ticks);
    }

    public static boolean isBlacklisted(BlockPos target) {
        if (target == null) return false;
        Integer stamp = blacklistTick.get(target);
        if (stamp == null) return false;

        int age = AutismSharedState.get().getClientTickCounter() - stamp;
        if (age < 0 || age > blacklistWindow.getOrDefault(target, 0)) {

            blacklistTick.remove(target);
            blacklistWindow.remove(target);
            return false;
        }
        return true;
    }

    private static void failActiveTarget() {
        int fails = failCounts.merge(activeTarget, 1, Integer::sum);
        int ticks = Math.min(FAIL_BLACKLIST_TICKS << Math.min(fails - 1, 2), MAX_FAIL_BLACKLIST_TICKS);
        blacklist(activeTarget, ticks);
    }

    private static int chooseComboMask(float delta) {

        if (Math.abs(delta) <= DEADZONE_DEG) return BIT_W;
        int best = 0;
        float bestError = Float.MAX_VALUE;
        for (int i = 0; i < COMBO_MASK.length; i++) {
            float error = Math.abs(Mth.wrapDegrees(delta - COMBO_ANGLE[i]));
            if (error < bestError) {
                bestError = error;
                best = i;
            }
        }
        return COMBO_MASK[best];
    }

    private static void applyHeld(int mask) {
        if (MC.options == null) {
            heldMask = 0;
            return;
        }
        int changed = mask ^ heldMask;
        if (changed == 0) return;
        if ((changed & BIT_W) != 0) setKey(MC.options.keyUp, (mask & BIT_W) != 0);
        if ((changed & BIT_A) != 0) setKey(MC.options.keyLeft, (mask & BIT_A) != 0);
        if ((changed & BIT_S) != 0) setKey(MC.options.keyDown, (mask & BIT_S) != 0);
        if ((changed & BIT_D) != 0) setKey(MC.options.keyRight, (mask & BIT_D) != 0);
        if ((changed & BIT_JUMP) != 0) setKey(MC.options.keyJump, (mask & BIT_JUMP) != 0);
        if ((changed & BIT_SPRINT) != 0) setKey(MC.options.keySprint, (mask & BIT_SPRINT) != 0);
        heldMask = mask;
    }

    private static void setKey(KeyMapping key, boolean down) {
        if (down) {
            key.setDown(true);
        } else {
            AutismKeyMappingBridge.of(key).autism$resetPressedState();
        }
    }

    private static void releaseKeys() {
        applyHeld(0);
    }

    public static void onExternalKeyRelease() {
        heldMask = 0;
    }

    private static void clearRoute() {
        path.clear();
        pathIndex = 0;
        steeringAtGoal = false;
    }

    private static void advanceReachedNodes(double px, double py, double pz, int feetY) {
        while (pathIndex < path.size()) {

            if (pathIndex == path.size() - 1 && !goalMovedWithinDrift()) break;
            PathNode node = path.get(pathIndex);
            double dx = node.x + 0.5 - px;
            double dz = node.z + 0.5 - pz;
            if (dx * dx + dz * dz < NODE_REACH_SQ
                && Math.abs(feetY - node.y) <= 1
                && (!node.jump || reachedLedgeTop(node, py))) {
                prevNodeX = node.x;
                prevNodeY = node.y;
                prevNodeZ = node.z;
                pathIndex++;
            } else {
                break;
            }
        }
    }

    private static boolean reachedLedgeTop(PathNode node, double py) {
        double ledgeTop = floorTop(node.x, node.y, node.z);
        return py >= ledgeTop - 0.0625;
    }

    private static boolean unjumpableAhead(double px, double py, double pz, int standCellY,
        double dx, double dz, double distSq) {
        if (distSq < 1.0E-6) return false;
        double len = Math.sqrt(distSq);

        int aheadX = Mth.floor(px + dx / len * 0.9);
        int aheadZ = Mth.floor(pz + dz / len * 0.9);
        double rise = standCellY + collisionTop(aheadX, standCellY, aheadZ) - py;
        if (rise > 1.25) return true;
        return rise > STEP_HEIGHT && collisionTop(aheadX, standCellY + 1, aheadZ) > 0.0;
    }

    private static boolean withinPlanDrift(BlockPos target) {
        if (lastPlanTarget == null) return false;
        if (target.getY() != lastPlanTarget.getY()) return false;
        int dx = target.getX() - lastPlanTarget.getX();
        int dz = target.getZ() - lastPlanTarget.getZ();
        return dx * dx + dz * dz <= GOAL_DRIFT_KEEP_SQ;
    }

    private static boolean goalMovedWithinDrift() {
        return lastPlanTarget != null && !lastPlanTarget.equals(activeTarget)
            && withinPlanDrift(activeTarget);
    }

    private static boolean canPlan(BlockPos target, int now) {

        if (lastPlanTarget == null || !lastPlanTarget.equals(target)) return true;
        return now - lastPlanTick >= REPLAN_INTERVAL_TICKS;
    }

    private static void notePlan(BlockPos target, int now) {
        lastPlanTarget = target;
        lastPlanTick = now;
    }

    private static boolean plan(double px, double py, double pz) {
        clearRoute();
        return Double.isFinite(search(px, py, pz, activeTarget, true, MAX_EXPAND));
    }

    private static double search(double px, double py, double pz, BlockPos target,
                                 boolean materialize, int expansionLimit) {
        gScore.clear();
        cameFrom.clear();
        jumpCells.clear();
        closed.clear();
        open.clear();
        rawChain.clear();
        smoothed.clear();
        goalCells.clear();
        walkableCache.clear();
        cacheWalkability = true;
        try {
            return searchCached(px, py, pz, target, materialize, expansionLimit);
        } finally {
            cacheWalkability = false;
        }
    }

    private static double searchCached(double px, double py, double pz, BlockPos target,
                                       boolean materialize, int expansionLimit) {

        int startX = Mth.floor(px);
        int startZ = Mth.floor(pz);

        int startY = standingCellY(startX, Mth.floor(py), startZ);
        if (startY == Integer.MIN_VALUE) return Double.POSITIVE_INFINITY;
        rangeAnchorX = startX;
        rangeAnchorZ = startZ;

        goalX = target.getX();
        goalZ = target.getZ();
        goalY = target.getY();
        addApproachCells(goalX, goalZ, target);
        for (int d = 0; d < 4; d++) {
            addApproachCells(goalX + DIR_X[d], goalZ + DIR_Z[d], target);
        }
        if (goalCells.isEmpty()) return Double.POSITIVE_INFINITY;

        if (Math.abs(goalX - startX) + Math.abs(goalZ - startZ) > MAX_RANGE + 1)
            return Double.POSITIVE_INFINITY;

        long startKey = BlockPos.asLong(startX, startY, startZ);

        if (materialize) {
            prevNodeX = startX;
            prevNodeY = startY;
            prevNodeZ = startZ;
        }

        gScore.put(startKey, 0);
        open.add(new OpenEntry(startKey, heuristic(startX, startY, startZ)));

        int expanded = 0;
        while (!open.isEmpty() && expanded < expansionLimit) {
            OpenEntry entry = open.poll();
            if (!closed.add(entry.key)) continue;
            expanded++;
            if (goalCells.contains(entry.key)) {
                if (materialize) {
                    buildPath(startKey, entry.key, startX, startY, startZ);

                    if (path.isEmpty()) path.add(new PathNode(startX, startY, startZ, false));
                }
                return gScore.get(entry.key);
            }
            expandNeighbors(entry.key);
        }
        return Double.POSITIVE_INFINITY;
    }

    private static void addApproachCells(int x, int z, BlockPos target) {
        double eyeHeight = MC.player.getEyeY() - MC.player.getY();
        for (int y = target.getY() + 1; y >= target.getY() - 3; y--) {
            if (walkable(x, y, z) && targetVisible(new Vec3(x + 0.5,
                floorTop(x, y, z) + eyeHeight, z + 0.5), target)) {
                goalCells.add(BlockPos.asLong(x, y, z));
            }
        }
    }

    private static void expandNeighbors(long currentKey) {
        int cx = BlockPos.getX(currentKey);
        int cy = BlockPos.getY(currentKey);
        int cz = BlockPos.getZ(currentKey);
        int gCurrent = gScore.get(currentKey);

        for (int d = 0; d < 4; d++) {
            int nx = cx + DIR_X[d];
            int nz = cz + DIR_Z[d];

            if (Math.abs(nx - rangeAnchorX) + Math.abs(nz - rangeAnchorZ) > MAX_RANGE) continue;

            double fromFloor = floorTop(cx, cy, cz);
            double rise = floorTop(nx, cy + 1, nz) - fromFloor;
            boolean hop = rise > STEP_HEIGHT;
            if (walkable(nx, cy + 1, nz)
                && (!hop || freeHead(cx, cy + 2, cz))
                && rise <= 1.25) {
                offer(currentKey, gCurrent, nx, cy + 1, nz,
                    (hop ? 14 : 10) + (hop && farmlandAt(nx, cy, nz) ? TRAMPLE_STEP_COST : 0), hop);
            }

            for (int ny = cy; ny >= cy - MAX_DROP; ny--) {
                if (!freeFeet(nx, ny, nz) || !freeHead(nx, ny + 1, nz)
                    || hardHazard(nx, ny, nz) || hardHazard(nx, ny + 1, nz)) break;
                if (hasFloor(nx, ny, nz)) {
                    double toFloor = floorTop(nx, ny, nz);
                    double floorRise = toFloor - fromFloor;
                    boolean needsHop = floorRise > STEP_HEIGHT;

                    if (walkable(nx, ny, nz) && floorRise <= 1.25 && floorRise >= -MAX_DROP
                        && (!needsHop || freeHead(cx, cy + 2, cz))) {
                        boolean trample = (needsHop || floorRise <= -0.5)
                            && farmlandAt(nx, ny - 1, nz);
                        offer(currentKey, gCurrent, nx, ny, nz,
                            10 + (needsHop ? 4 : (int) Math.ceil(Math.max(0, -floorRise) * 2))
                                + (trample ? TRAMPLE_STEP_COST : 0), needsHop);
                    }
                    break;
                }
            }

            int jx = cx + DIR_X[d] * 2;
            int jz = cz + DIR_Z[d] * 2;
            if (Math.abs(jx - rangeAnchorX) + Math.abs(jz - rangeAnchorZ) <= MAX_RANGE
                && !walkable(nx, cy, nz)
                && walkable(jx, cy, jz)
                && freeHead(cx, cy + 2, cz)
                && freeHead(jx, cy + 2, jz)
                && freeHead(nx, cy + 1, nz)
                && freeHead(nx, cy + 2, nz)
                && !hardHazard(nx, cy + 1, nz)
                && !hardHazard(nx, cy + 2, nz)
                && Math.abs(floorTop(jx, cy, jz) - fromFloor) < 0.0625
                && safeGapFall(nx, cy, nz)) {
                offer(currentKey, gCurrent, jx, cy, jz,
                    GAP_JUMP_COST + (farmlandAt(jx, cy - 1, jz) ? TRAMPLE_STEP_COST : 0), true);
            }
        }
    }

    private static void offer(long fromKey, int gFrom, int nx, int ny, int nz, int stepCost,
        boolean jump) {
        long key = BlockPos.asLong(nx, ny, nz);

        if (closed.contains(key)) return;
        int g = gFrom + stepCost + terrainCost(nx, ny, nz);
        Integer old = gScore.get(key);
        if (old != null && old <= g) return;
        gScore.put(key, g);
        cameFrom.put(key, fromKey);
        if (jump) {
            jumpCells.add(key);
        } else {
            jumpCells.remove(key);
        }
        open.add(new OpenEntry(key, g + heuristic(nx, ny, nz)));
    }

    private static int terrainCost(int x, int y, int z) {
        int cost = 0;
        if (softHazard(x, y, z)) cost += HAZARD_STEP_COST;
        if (farmlandAt(x, y - 1, z)) cost += FIELD_STEP_COST;
        return cost;
    }

    private static int heuristic(int x, int y, int z) {
        int best = Integer.MAX_VALUE;
        for (long key : goalCells) {
            best = Math.min(best, AutismPathGeometry.lowerBound(x, z,
                BlockPos.getX(key), BlockPos.getZ(key)));
        }
        return best;
    }

    private static void buildPath(long startKey, long goalKey, int startX, int startY, int startZ) {
        rawChain.clear();
        long key = goalKey;
        while (key != startKey) {
            rawChain.add(key);
            key = cameFrom.get(key);
        }
        path.clear();

        for (int i = rawChain.size() - 1; i >= 0; i--) {
            long nodeKey = rawChain.get(i);
            path.add(new PathNode(BlockPos.getX(nodeKey), BlockPos.getY(nodeKey),
                BlockPos.getZ(nodeKey), jumpCells.contains(nodeKey)));
        }
        smooth(startX, startY, startZ);
    }

    private static void smooth(int startX, int startY, int startZ) {
        if (path.size() < 3) return;
        smoothed.clear();
        int anchorX = startX;
        int anchorY = startY;
        int anchorZ = startZ;
        int i = 0;
        while (i < path.size()) {
            int far = i;
            int limit = Math.min(path.size() - 1, i + SMOOTH_LOOKAHEAD);
            for (int j = limit; j > i; j--) {
                PathNode candidate = path.get(j);
                if (candidate.jump || candidate.y != anchorY) continue;
                if (clearLine(anchorX, anchorY, anchorZ, candidate.x, candidate.z)) {
                    far = j;
                    break;
                }
            }
            PathNode next = path.get(far);
            smoothed.add(next);
            anchorX = next.x;
            anchorY = next.y;
            anchorZ = next.z;
            i = far + 1;
        }
        path.clear();
        path.addAll(smoothed);
        smoothed.clear();
    }

    private static boolean clearLine(int ax, int ay, int az, int bx, int bz) {
        return clearWalkLine(ax + 0.5, floorTop(ax, ay, az), az + 0.5, bx + 0.5, bz + 0.5);
    }

    private static boolean clearWalkLine(double ax, double floorY, double az, double bx, double bz) {
        return clearWalkLine(ax, floorY, az, bx, bz, true);
    }

    private static boolean clearWalkLine(double ax, double floorY, double az, double bx, double bz,
                                         boolean avoidHazards) {
        double dx = bx - ax;
        double dz = bz - az;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) * 8.0));
        double lastX = ax;
        double lastZ = az;
        double lastFloor = floorY;
        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            double x = ax + dx * t;
            double z = az + dz * t;
            int cx = Mth.floor(x);
            int cz = Mth.floor(z);
            int cellY = lineStandingCell(cx, Mth.floor(lastFloor), cz, lastFloor);
            if (cellY == Integer.MIN_VALUE || avoidHazards && softHazard(cx, cellY, cz)) return false;
            double nextFloor = floorTop(cx, cellY, cz);
            if (!AutismPathGeometry.safeRise(lastFloor, nextFloor, STEP_HEIGHT)) return false;
            if (!clearBodySegment(lastX, lastZ, x, z, Math.max(lastFloor, nextFloor))) return false;
            lastX = x;
            lastZ = z;
            lastFloor = nextFloor;
        }
        return true;
    }

    private static int lineStandingCell(int x, int feetY, int z, double fromFloor) {
        for (int y = feetY + 1; y >= feetY - 1; y--) {
            if (walkable(x, y, z)
                && AutismPathGeometry.safeRise(fromFloor, floorTop(x, y, z), STEP_HEIGHT)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean clearBodySegment(double ax, double az, double bx, double bz, double floor) {
        double half = BODY_HALF_WIDTH - 1.0E-5;
        AABB bounds = new AABB(Math.min(ax, bx) - half, floor + 1.0E-5,
            Math.min(az, bz) - half, Math.max(ax, bx) + half,
            floor + BODY_HEIGHT - 1.0E-5, Math.max(az, bz) + half);
        for (VoxelShape shape : MC.level.getBlockCollisions(MC.player, bounds)) {
            for (AABB box : shape.toAabbs()) {
                if (box.maxY <= bounds.minY || box.minY >= bounds.maxY) continue;
                if (AutismPathGeometry.crossesBox(ax, az, bx, bz,
                    box.minX - half, box.minZ - half, box.maxX + half, box.maxZ + half)) return false;
            }
        }
        return true;
    }

    private static boolean freeFeet(int x, int y, int z) {
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
        if (!state.getFluidState().isEmpty()) return false;
        VoxelShape shape = state.getCollisionShape(MC.level, cursor);
        if (!shape.isEmpty()
            && (shape.min(Direction.Axis.Y) > 1.0E-4 || shape.max(Direction.Axis.Y) > STEP_HEIGHT)) {
            return false;
        }

        VoxelShape below = MC.level.getBlockState(cursor.set(x, y - 1, z)).getCollisionShape(MC.level, cursor);
        return below.isEmpty() || below.max(Direction.Axis.Y) <= 1.0 + 1.0E-4;
    }

    private static boolean freeHead(int x, int y, int z) {
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
        if (!state.getFluidState().isEmpty()) return false;
        return state.getCollisionShape(MC.level, cursor).isEmpty();
    }

    private static boolean standable(int x, int y, int z) {
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
        if (state.isFaceSturdy(MC.level, cursor, Direction.UP)) return true;
        VoxelShape shape = state.getCollisionShape(MC.level, cursor);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5;
    }

    private static boolean walkable(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (cacheWalkability) {
            Boolean cached = walkableCache.get(key);
            if (cached != null) return cached;
        }
        boolean result = y >= MC.level.getMinY() && y < MC.level.getMaxY()
            && MC.level.hasChunkAt(cursor.set(x, y, z))
            && freeFeet(x, y, z) && freeHead(x, y + 1, z) && hasFloor(x, y, z)
            && !hardHazard(x, y, z) && !hardHazard(x, y + 1, z)
            && clearBodySegment(x + 0.5, z + 0.5, x + 0.5, z + 0.5, floorTop(x, y, z));
        if (cacheWalkability) walkableCache.put(key, result);
        return result;
    }

    private static boolean hardHazard(int x, int y, int z) {
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW);
    }

    private static boolean softHazard(int x, int y, int z) {
        if (MC.level.getBlockState(cursor.set(x, y, z)).is(Blocks.SWEET_BERRY_BUSH)) return true;
        BlockState floor = MC.level.getBlockState(cursor.set(x, y - 1, z));
        return floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS);
    }

    private static boolean farmlandAt(int x, int y, int z) {
        return MC.level.getBlockState(cursor.set(x, y, z)).is(Blocks.FARMLAND);
    }

    private static boolean onFarmland(double px, int feetY, double pz) {
        int x = Mth.floor(px);
        int z = Mth.floor(pz);
        return farmlandAt(x, feetY - 1, z) || farmlandAt(x, feetY, z);
    }

    private static boolean hasFloor(int x, int y, int z) {
        if (standable(x, y - 1, z)) return true;
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
        VoxelShape shape = state.getCollisionShape(MC.level, cursor);
        return !shape.isEmpty()
            && shape.min(Direction.Axis.Y) <= 1.0E-4
            && shape.max(Direction.Axis.Y) <= STEP_HEIGHT;
    }

    private static double collisionTop(int x, int y, int z) {
        VoxelShape shape = MC.level.getBlockState(cursor.set(x, y, z)).getCollisionShape(MC.level, cursor);
        return shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
    }

    private static double floorTop(int x, int y, int z) {
        BlockState state = MC.level.getBlockState(cursor.set(x, y, z));

        if (!state.getFluidState().isEmpty()) return y;
        VoxelShape shape = state.getCollisionShape(MC.level, cursor);
        if (!shape.isEmpty()
            && shape.min(Direction.Axis.Y) <= 1.0E-4
            && shape.max(Direction.Axis.Y) <= STEP_HEIGHT) {
            return y + shape.max(Direction.Axis.Y);
        }
        return (y - 1) + collisionTop(x, y - 1, z);
    }

    private static boolean safeGapFall(int x, int y, int z) {
        for (int gy = y; gy >= y - MAX_DROP; gy--) {
            BlockState state = MC.level.getBlockState(cursor.set(x, gy, z));
            if (!state.getFluidState().isEmpty()) {
                return !state.getFluidState().is(FluidTags.LAVA);
            }
            if (hardHazard(x, gy, z)) return false;
            if (freeFeet(x, gy, z) && hasFloor(x, gy, z)) return true;
            if (!state.getCollisionShape(MC.level, cursor).isEmpty()) return false;
        }
        return false;
    }

    private static int standingCellY(int x, int feetY, int z) {
        for (int y = feetY + 1; y >= feetY - MAX_DROP; y--) {
            if (walkable(x, y, z)) return y;
        }

        for (int y = feetY + 1; y >= feetY - MAX_DROP; y--) {
            if (freeFeet(x, y, z) && freeHead(x, y + 1, z) && hasFloor(x, y, z)) return y;
        }

        for (int y = feetY + 1; y >= feetY - MAX_DROP; y--) {
            BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
            if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.LAVA)
                && freeHead(x, y + 1, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int goalCellY(int x, int ty, int z) {
        if (walkable(x, ty, z)) return ty;
        if (walkable(x, ty + 1, z)) return ty + 1;
        if (walkable(x, ty - 1, z)) return ty - 1;
        return Integer.MIN_VALUE;
    }
}
