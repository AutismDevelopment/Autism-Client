package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public final class AutismFaceScan {
    private AutismFaceScan() {
    }

    public static final Direction[] FACE_ORDER_UP_FIRST = {
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    };

    public static final double FACE_EPSILON = 1.0E-4D;

    public static final double AIM_CENTRE_WINDOW = 0.2D;

    public static final int MAX_LADDER_RAYS = 4;

    public static final int DEFAULT_RECTS_PER_FACE = 3;

    public static final int DEFAULT_TICK_RAY_BUDGET = 48;

    public static final float MAX_PLACEMENT_PITCH = 89.5F;

    private static final double FACE_INSET = 0.12D;

    private static final double PITCH_EMISSION_SLACK = 0.01D;

    private static final int MAX_SHAPE_BOXES = 16;

    private static final double CROSSING_SPAN_MARGIN = 0.06D;

    private static final AABB[] NO_RECTS = new AABB[0];

    public record Intent(BlockPos support, Direction face) {
        public Intent {
            support = support == null ? null : support.immutable();
        }
    }

    public record Option(
        BlockPos cell, BlockPos support, Direction face, int form,
        BlockState state, AABB rect, double faceArea, double edgeConfidence,
        boolean requiresSneak, Intent intent) {
    }

    public record Landing(AABB rect, Direction face) {
    }

    public record Aim(
        Vec3 point, AutismRotationUtil.Rotation goal, AutismRotationUtil.Rotation emitted,
        double distance, float turn, Tier tier) {
    }

    public enum Tier { CENTRE, NEAREST, FLATTEST, ESCAPE }

    public record Candidate(
        Option option, Aim aim, BlockHitResult hit, boolean substituted, double score) {
    }

    public enum Refusal {
        NONE("ok"),
        NOT_A_SUPPORT("ns"),
        SELF_OCCLUDED("so"),
        BEHIND_PLANE("sd"),
        OUT_OF_REACH("rc"),
        PAST_PITCH_CAP("pc"),
        BANNED("st"),
        OUTRANKED("pr"),
        RAY_MISSED("rm"),
        RAY_WRONG_CELL("rw"),
        NO_BUDGET("bg");

        private final String code;

        Refusal(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final class Budget {
        private int rays;

        public Budget(int rays) {
            this.rays = Math.max(0, rays);
        }

        public int remaining() {
            return rays;
        }

        public boolean spend() {
            if (rays <= 0) return false;
            rays--;
            return true;
        }

        public void reset(int rays) {
            this.rays = Math.max(0, rays);
        }
    }

    public interface Placement {

        boolean lands(BlockHitResult hit, BlockPos cell);

        default boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
            return !useActionEatsClick(state, pos, sneaking);
        }
    }

    public static Placement blockItem(ItemStack stack, Player player, InteractionHand hand) {
        return (hit, cell) -> {
            if (hit == null || cell == null || player == null) return false;
            if (stack == null || !(stack.getItem() instanceof BlockItem)) return false;
            BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
            return context.canPlace() && context.getClickedPos().equals(cell);
        };
    }

    public static Placement onSupport(BlockPos support, EnumSet<Direction> allowedFaces) {
        BlockPos anchor = support == null ? null : support.immutable();
        return (hit, cell) -> {
            if (hit == null || anchor == null || cell == null) return false;
            if (!hit.getBlockPos().equals(anchor)) return false;
            if (allowedFaces != null && !allowedFaces.contains(hit.getDirection())) return false;
            return cell.equals(anchor) || cell.equals(anchor.above());
        };
    }

    public static final class Request {
        private BlockPos cell;
        private final Vec3 eye;
        private final double reach;
        private Placement placement;
        private AutismRotationUtil.Rotation from;
        private double pitchLimit = goalPitchLimit();
        private boolean sneaking;
        private boolean sneakAllowed;
        private Direction[] faceOrder = FACE_ORDER_UP_FIRST;
        private EnumSet<Direction> allowedFaces;
        private boolean allowInPlace = true;
        private Vec3 leadEye;
        private Predicate<Intent> banned;
        private Budget budget;
        private int maxRectsPerFace = DEFAULT_RECTS_PER_FACE;
        private boolean quantize = true;
        private StringBuilder trace;

        public Request(BlockPos cell, Vec3 eye, double reach, Placement placement) {
            this.cell = cell == null ? null : cell.immutable();
            this.eye = eye;
            this.reach = reach;
            this.placement = placement;
        }

        public Request cell(BlockPos cell) {
            this.cell = cell == null ? null : cell.immutable();
            return this;
        }

        public Request placement(Placement placement) {
            this.placement = placement;
            return this;
        }

        public Request from(AutismRotationUtil.Rotation from) {
            this.from = from;
            return this;
        }

        public Request pitchLimit(double degrees) {
            this.pitchLimit = degrees;
            return this;
        }

        public Request sneaking(boolean sneaking) {
            this.sneaking = sneaking;
            return this;
        }

        public Request sneakAllowed(boolean allowed) {
            this.sneakAllowed = allowed;
            return this;
        }

        public Request faceOrder(Direction[] order) {
            this.faceOrder = order == null || order.length == 0 ? FACE_ORDER_UP_FIRST : order;
            return this;
        }

        public Request allowedFaces(EnumSet<Direction> faces) {
            this.allowedFaces = faces;
            return this;
        }

        public Request allowInPlace(boolean allow) {
            this.allowInPlace = allow;
            return this;
        }

        public Request leadEye(Vec3 leadEye) {
            this.leadEye = leadEye;
            return this;
        }

        public Request banned(Predicate<Intent> banned) {
            this.banned = banned;
            return this;
        }

        public Request budget(Budget budget) {
            this.budget = budget;
            return this;
        }

        public Request maxRectsPerFace(int max) {
            this.maxRectsPerFace = Math.max(1, max);
            return this;
        }

        public Request quantize(boolean quantize) {
            this.quantize = quantize;
            return this;
        }

        public Request trace(StringBuilder trace) {
            this.trace = trace;
            return this;
        }

        public BlockPos cell() {
            return cell;
        }

        public Vec3 eye() {
            return eye;
        }

        public double reach() {
            return reach;
        }

        public Placement placement() {
            return placement;
        }

        public AutismRotationUtil.Rotation from() {
            return from;
        }

        public double pitchLimit() {
            return pitchLimit;
        }

        public boolean sneaking() {
            return sneaking;
        }

        public boolean sneakAllowed() {
            return sneakAllowed;
        }

        public Direction[] faceOrder() {
            return faceOrder;
        }

        public EnumSet<Direction> allowedFaces() {
            return allowedFaces;
        }

        public boolean allowInPlace() {
            return allowInPlace;
        }

        public Vec3 leadEye() {
            return leadEye;
        }

        public Predicate<Intent> banned() {
            return banned;
        }

        public Budget budget() {
            return budget;
        }

        public int maxRectsPerFace() {
            return maxRectsPerFace;
        }

        public boolean quantize() {
            return quantize;
        }

        public StringBuilder trace() {
            return trace;
        }
    }

    public static double goalPitchLimit() {
        return MAX_PLACEMENT_PITCH
            - AutismHumanRotation.settleBandDegrees(AutismRotationUtil.sensitivityGcd())
            - PITCH_EMISSION_SLACK;
    }

    public static void options(Request request, List<Option> out) {
        if (request == null || out == null || request.cell() == null) return;
        Level level = level();
        if (level == null) return;
        BlockPos cell = request.cell();
        int start = out.size();
        boolean inPlace = request.allowInPlace() && replaceableInPlace(cell, level);
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (Direction face : request.faceOrder()) {
            if (request.allowedFaces() != null && !request.allowedFaces().contains(face)) continue;
            for (int form = 0; form < 2; form++) {
                boolean own = form == 0;
                if (own && !inPlace) continue;
                BlockPos support = own ? cell : scan.setWithOffset(cell, face.getOpposite()).immutable();
                BlockState state = level.getBlockState(support);
                if (state.isAir()) continue;

                boolean requiresSneak = sneakUnlocks(state, support);
                boolean sneaking = request.sneaking() || (requiresSneak && request.sneakAllowed());
                if (!own && !isPlaceableSupport(state, support, sneaking)) continue;
                if (own && useActionEatsClick(state, support, sneaking)) continue;
                Intent intent = new Intent(support, face);
                if (request.banned() != null && request.banned().test(intent)) continue;
                for (AABB rect : faceRects(state, support, face, request.maxRectsPerFace())) {
                    out.add(new Option(cell, support, face, form, state, rect,
                        faceArea(rect, face), edgeConfidence(rect, face, request.eye()),
                        requiresSneak, intent));
                }
            }
        }
        sortSegment(out, start, request);
    }

    public static void options(List<BlockPos> cells, Request template, List<Option> out) {
        if (cells == null || template == null || out == null) return;
        BlockPos held = template.cell();
        for (BlockPos cell : cells) options(template.cell(cell), out);
        template.cell(held);
    }

    public static long shellKey(List<BlockPos> cells, Level level) {
        if (cells == null || level == null) return 0L;
        long key = 0L;
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (BlockPos cell : cells) key += cellKey(cell, level, scan);
        return key;
    }

    private static long cellKey(BlockPos cell, Level level, BlockPos.MutableBlockPos scan) {
        long key = cell.asLong() * 31L + System.identityHashCode(level.getBlockState(cell));
        for (Direction direction : Direction.values()) {
            key = key * 31L
                + System.identityHashCode(level.getBlockState(scan.setWithOffset(cell, direction)));
        }
        key ^= key >>> 33;
        key *= 0xFF51AFD7ED558CCDL;
        key ^= key >>> 33;
        return key;
    }

    private static void sortSegment(List<Option> out, int start, Request request) {
        int size = out.size();
        if (size - start < 2) return;
        List<Option> segment = new ArrayList<>(out.subList(start, size));
        Vec3 eye = request.eye();
        AutismRotationUtil.Rotation from = request.from();
        Direction[] order = request.faceOrder();
        segment.sort((left, right) -> {
            boolean leftUp = left.face() == Direction.UP;
            boolean rightUp = right.face() == Direction.UP;
            if (leftUp != rightUp) return leftUp ? -1 : 1;
            int area = Double.compare(right.faceArea(), left.faceArea());
            if (area != 0) return area;
            int edge = Double.compare(right.edgeConfidence(), left.edgeConfidence());
            if (edge != 0) return edge;
            if (from != null) {
                int turn = Float.compare(turnTo(left, eye, from), turnTo(right, eye, from));
                if (turn != 0) return turn;
            }
            return Integer.compare(faceIndex(order, left.face()), faceIndex(order, right.face()));
        });
        for (int i = 0; i < segment.size(); i++) out.set(start + i, segment.get(i));
    }

    private static float turnTo(Option option, Vec3 eye, AutismRotationUtil.Rotation from) {
        Vec3 centre = faceCentre(option.rect(), option.face());
        return AutismRotationUtil.angleTo(from, AutismRotationUtil.lookingAt(centre, eye));
    }

    private static int faceIndex(Direction[] order, Direction face) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == face) return i;
        }
        return order.length;
    }

    public static Aim solve(Option option, Request request, Refusal[] refusal) {
        set(refusal, Refusal.NONE);
        if (option == null || request == null) return refuse(refusal, Refusal.NOT_A_SUPPORT, request, null);
        Level level = level();
        Direction face = option.face();
        if (level == null) return refuse(refusal, Refusal.NOT_A_SUPPORT, request, face);
        BlockPos support = option.support();
        BlockState state = level.getBlockState(support);
        if (state != option.state()) return refuse(refusal, Refusal.NOT_A_SUPPORT, request, face);
        if (request.banned() != null && request.banned().test(option.intent())) {
            return refuse(refusal, Refusal.BANNED, request, face);
        }
        boolean sneaking = request.sneaking()
            || (option.requiresSneak() && request.sneakAllowed());
        if (request.placement() == null || !request.placement().clickable(state, support, sneaking)) {
            return refuse(refusal, Refusal.NOT_A_SUPPORT, request, face);
        }
        Vec3 eye = request.eye();
        if (selfOccluded(eye, support, face, level)) {
            return refuse(refusal, Refusal.SELF_OCCLUDED, request, face);
        }
        AABB rect = option.rect();
        Vec3 point = aimPoint(rect, face, eye, aimFrom(request));
        if (point == null) return refuse(refusal, Refusal.BEHIND_PLANE, request, face);
        Tier tier = Tier.CENTRE;
        double reachSq = request.reach() * request.reach();
        if (eye.distanceToSqr(point) > reachSq) {

            point = windowPoint(rect, face, eye, Tier.NEAREST);
            if (point == null || eye.distanceToSqr(point) > reachSq) {
                return refuse(refusal, Refusal.OUT_OF_REACH, request, face);
            }
            tier = Tier.NEAREST;
        }
        AutismRotationUtil.Rotation goal = AutismRotationUtil.lookingAt(point, eye);
        if (Math.abs(goal.pitch()) > request.pitchLimit()) {

            Vec3 flat = windowPoint(rect, face, eye, Tier.FLATTEST);
            if (flat == null || eye.distanceToSqr(flat) > reachSq) {
                return refuse(refusal, Refusal.PAST_PITCH_CAP, request, face);
            }
            goal = AutismRotationUtil.lookingAt(flat, eye);
            if (Math.abs(goal.pitch()) > request.pitchLimit()) {
                return refuse(refusal, Refusal.PAST_PITCH_CAP, request, face);
            }
            point = flat;
            tier = Tier.FLATTEST;
        }

        if (face.getAxis().isHorizontal() && request.leadEye() != null) {
            double[] window = crossingWindow(eye, request.leadEye(), rect, face, goal.yaw());
            if (window != null) {
                float pitch = (float) Mth.clamp(goal.pitch(), window[0], window[1]);
                if (Math.abs(pitch) > request.pitchLimit()) {
                    return refuse(refusal, Refusal.PAST_PITCH_CAP, request, face);
                }
                goal = new AutismRotationUtil.Rotation(goal.yaw(), pitch);
            }
        }

        if (!request.placement().lands(
            new BlockHitResult(point, face, support, false), request.cell())) {
            return refuse(refusal, Refusal.NOT_A_SUPPORT, request, face);
        }
        AutismRotationUtil.Rotation from = aimFrom(request);
        AutismRotationUtil.Rotation emitted = request.quantize()
            ? AutismRotationUtil.normalizeToSensitivity(goal, from == null ? goal : from) : goal;
        float turn = from == null ? 0.0F : AutismRotationUtil.angleTo(from, goal);
        return new Aim(point, goal, emitted, eye.distanceTo(point), turn, tier);
    }

    public static Vec3 aimPoint(AABB rect, Direction face, Vec3 eye, AutismRotationUtil.Rotation from) {
        if (rect == null || face == null || eye == null) return null;
        Direction.Axis normal = face.getAxis();
        double plane = planeOf(rect, face);
        if (eyePastPlane(eye, rect, face) <= FACE_EPSILON) return null;

        Direction.Axis first = inPlaneAxis(normal, true);
        Direction.Axis second = inPlaneAxis(normal, false);
        double margin = faceMargin(eye.distanceTo(faceCentre(rect, face)));
        double[] windowFirst = aimWindow(rect, first, margin, true);
        double[] windowSecond = aimWindow(rect, second, margin, true);

        double a;
        double b;
        double travel = -1.0D;
        Vec3 look = from == null ? null : lookVector(from);
        if (look != null) {
            double along = coordinate(look, normal);
            travel = Math.abs(along) > FACE_EPSILON ? (plane - coordinate(eye, normal)) / along : -1.0D;
        }
        if (travel > 0.0D) {
            Vec3 crossing = eye.add(look.scale(travel));
            a = coordinate(crossing, first);
            b = coordinate(crossing, second);
        } else {
            a = coordinate(eye, first);
            b = coordinate(eye, second);
        }
        return onPlane(normal, plane,
            Mth.clamp(a, windowFirst[0], windowFirst[1]),
            Mth.clamp(b, windowSecond[0], windowSecond[1]));
    }

    public static Vec3 windowPoint(AABB rect, Direction face, Vec3 eye, Tier tier) {
        if (rect == null || face == null || eye == null) return null;
        Direction.Axis normal = face.getAxis();
        double plane = planeOf(rect, face);
        if (eyePastPlane(eye, rect, face) <= FACE_EPSILON) return null;
        Direction.Axis first = inPlaneAxis(normal, true);
        Direction.Axis second = inPlaneAxis(normal, false);
        double margin = faceMargin(eye.distanceTo(faceCentre(rect, face)));
        boolean centred = tier == Tier.CENTRE;
        boolean away = tier == Tier.FLATTEST || tier == Tier.ESCAPE;
        return onPlane(normal, plane,
            windowEnd(aimWindow(rect, first, margin, centred), coordinate(eye, first), first, away),
            windowEnd(aimWindow(rect, second, margin, centred), coordinate(eye, second), second, away));
    }

    private static double windowEnd(double[] window, double at, Direction.Axis axis, boolean away) {
        if (!away || axis == Direction.Axis.Y) return Mth.clamp(at, window[0], window[1]);
        return Math.abs(window[0] - at) >= Math.abs(window[1] - at) ? window[0] : window[1];
    }

    public static double axisInset(AABB rect, Direction.Axis axis, double margin) {
        double extent = rect.max(axis) - rect.min(axis);
        return Math.min(Math.max(0.0D, margin), Math.max(0.0D, extent * 0.5D - FACE_EPSILON));
    }

    public static double[] aimWindow(AABB rect, Direction.Axis axis, double margin, boolean centred) {
        double inset = axisInset(rect, axis, margin);
        double low = rect.min(axis) + inset;
        double high = rect.max(axis) - inset;
        if (high < low) {
            double mid = (low + high) * 0.5D;
            low = mid;
            high = mid;
        }
        if (!centred) return new double[]{low, high};
        double centre = (rect.min(axis) + rect.max(axis)) * 0.5D;
        double half = Math.min(AIM_CENTRE_WINDOW, (high - low) * 0.5D);
        return new double[]{centre - half, centre + half};
    }

    public static double faceMargin(double distance) {
        double band = AutismHumanRotation.settleBandDegrees(AutismRotationUtil.sensitivityGcd());
        return Math.max(FACE_INSET, Math.abs(distance) * Math.tan(Math.toRadians(band)));
    }

    public static double edgeConfidence(AABB rect, Direction face, Vec3 eye) {
        if (rect == null || face == null || eye == null) return 0.0D;
        double margin = faceMargin(eye.distanceTo(faceCentre(rect, face))) * 2.0D;
        if (margin <= 0.0D) return 1.0D;
        Direction.Axis normal = face.getAxis();
        double first = rect.max(inPlaneAxis(normal, true)) - rect.min(inPlaneAxis(normal, true));
        double second = rect.max(inPlaneAxis(normal, false)) - rect.min(inPlaneAxis(normal, false));
        return Mth.clamp(Math.min(first, second) / margin, 0.0D, 1.0D);
    }

    public static double[] crossingWindow(Vec3 eye, AABB rect, Direction face, float yaw) {
        if (eye == null || rect == null || face == null) return null;
        double past = eyePastPlane(eye, rect, face);
        if (past <= 0.0D) return null;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return null;
        double run = past / toward;
        double margin = CROSSING_SPAN_MARGIN * rect.getYsize();
        double shallowest = Math.toDegrees(Math.atan2(eye.y - (rect.maxY - margin), run));
        double steepest = Math.toDegrees(Math.atan2(eye.y - (rect.minY + margin), run));
        return new double[]{shallowest, steepest, run};
    }

    public static double[] crossingWindow(Vec3 eye, Vec3 leadEye, AABB rect, Direction face, float yaw) {
        double[] primary = crossingWindow(eye, rect, face, yaw);
        if (primary == null) return null;
        double low = primary[0];
        double high = primary[1];
        double[] secondary = crossingWindow(leadEye, rect, face, yaw);
        if (secondary != null) {
            double bothLow = Math.max(low, secondary[0]);
            double bothHigh = Math.min(high, secondary[1]);
            if (bothLow <= bothHigh) {
                low = bothLow;
                high = bothHigh;
            }
        }
        return new double[]{low, high, primary[2]};
    }

    public static Candidate probe(Option option, Aim aim, Request request, Refusal[] refusal) {
        set(refusal, Refusal.NONE);
        if (option == null || aim == null || request == null) {
            return refuseCandidate(refusal, Refusal.RAY_MISSED, request, null);
        }
        Level level = level();
        Player player = player();
        Direction face = option.face();
        if (level == null || player == null) {
            return refuseCandidate(refusal, Refusal.RAY_MISSED, request, face);
        }
        Vec3 eye = request.eye();
        double reach = request.reach();
        double reachSq = reach * reach;
        boolean sneaking = request.sneaking() || (option.requiresSneak() && request.sneakAllowed());
        AutismRotationUtil.Rotation cast = request.quantize() ? aim.emitted() : aim.goal();
        boolean escaped = false;
        for (int rung = 0; rung < MAX_LADDER_RAYS; rung++) {
            if (request.budget() != null && !request.budget().spend()) {
                return refuseCandidate(refusal, Refusal.NO_BUDGET, request, face);
            }
            BlockHitResult hit = ray(eye, cast, reach, level, player);
            BlockPos hitPos = hit == null ? null : hit.getBlockPos();
            BlockState hitState = hit == null ? null : level.getBlockState(hitPos);

            boolean builds = hit != null
                && request.placement().clickable(hitState, hitPos, sneaking)
                && request.placement().lands(hit, request.cell());
            if (!builds) {

                if (escaped) {
                    return refuseCandidate(refusal,
                        hit == null ? Refusal.RAY_MISSED : Refusal.RAY_WRONG_CELL, request, face);
                }
                escaped = true;
                cast = retarget(windowPoint(option.rect(), face, eye, Tier.ESCAPE), cast, eye,
                    reachSq, request);
                if (cast == null) {
                    return refuseCandidate(refusal,
                        hit == null ? Refusal.RAY_MISSED : Refusal.RAY_WRONG_CELL, request, face);
                }
                continue;
            }
            Landing landing = landingUnderHit(hitState, hitPos, hit);
            if (landing == null) return refuseCandidate(refusal, Refusal.RAY_MISSED, request, face);
            AABB hitRect = landing.rect();

            Direction hitFace = landing.face();

            if (onFaceRect(hitRect, hitFace, hit.getLocation(),
                faceMargin(eye.distanceTo(hit.getLocation())))) {
                boolean substituted = !(hitPos.equals(option.support()) && hitFace == face);
                Option found = substituted
                    ? substitute(option, hitPos, hitFace, hitState, hitRect, eye, request)
                    : option;
                Aim landed = new Aim(hit.getLocation(), cast, cast,
                    eye.distanceTo(hit.getLocation()),
                    aimFrom(request) == null ? 0.0F
                        : AutismRotationUtil.angleTo(aimFrom(request), cast),
                    aim.tier());
                return new Candidate(found, landed, hit, substituted, score(found, landed));
            }
            cast = retarget(aimPoint(hitRect, hitFace, eye, cast), cast, eye, reachSq, request);
            if (cast == null) return refuseCandidate(refusal, Refusal.RAY_MISSED, request, face);
        }
        return refuseCandidate(refusal, Refusal.RAY_MISSED, request, face);
    }

    private static AutismRotationUtil.Rotation retarget(Vec3 point, AutismRotationUtil.Rotation cast,
                                                        Vec3 eye, double reachSq, Request request) {
        if (point == null || eye.distanceToSqr(point) > reachSq) return null;
        AutismRotationUtil.Rotation next = AutismRotationUtil.lookingAt(point, eye);
        if (Math.abs(next.pitch()) > request.pitchLimit()) return null;
        if (request.quantize()) {
            AutismRotationUtil.Rotation from = aimFrom(request);
            next = AutismRotationUtil.normalizeToSensitivity(next, from == null ? next : from);
            if (Math.abs(next.pitch()) > request.pitchLimit()) return null;
        }
        if (Float.compare(next.yaw(), cast.yaw()) == 0
            && Float.compare(next.pitch(), cast.pitch()) == 0) {
            return null;
        }
        return next;
    }

    private static Option substitute(Option option, BlockPos support, Direction face, BlockState state,
                                     AABB rect, Vec3 eye, Request request) {
        AABB flat = flatten(rect, face);
        return new Option(option.cell(), support.immutable(), face,
            support.equals(option.cell()) ? 0 : 1, state, flat,
            faceArea(flat, face), edgeConfidence(flat, face, eye),
            sneakUnlocks(state, support), new Intent(support, face));
    }

    private static double score(Option option, Aim aim) {
        return (option.face() == Direction.UP ? 4.0D : 0.0D)
            + option.faceArea() + option.edgeConfidence() - aim.turn() / 180.0D;
    }

    public static Candidate best(Request request) {
        return best(request, null);
    }

    public static Candidate best(Request request, Refusal[] outcome) {
        set(outcome, Refusal.NONE);
        if (request == null || request.cell() == null) return null;
        Level level = level();
        if (level == null) return null;
        ScanSlot slot = scanSlot(request, level);
        List<Option> found = slot.options;
        int size = found.size();
        if (size == 0) {
            set(outcome, Refusal.NOT_A_SUPPORT);
            return null;
        }

        if (slot.cursor >= size) slot.cursor = 0;
        Refusal[] refusal = new Refusal[1];
        Refusal last = Refusal.NOT_A_SUPPORT;
        int start = slot.cursor;
        for (int step = 0; step < size; step++) {
            int index = start + step;
            if (index >= size) index -= size;
            Option option = found.get(index);
            Aim aim = solve(option, request, refusal);
            if (aim == null) {
                last = refusal[0];
                slot.cursor = index + 1 == size ? 0 : index + 1;
                continue;
            }
            Candidate candidate = probe(option, aim, request, refusal);
            if (refusal[0] == Refusal.NO_BUDGET) {
                slot.cursor = index;
                set(outcome, Refusal.NO_BUDGET);
                return null;
            }
            if (candidate != null) {

                slot.cursor = index;
                return candidate;
            }
            slot.cursor = index + 1 == size ? 0 : index + 1;
            last = refusal[0];
        }
        set(outcome, last);
        return null;
    }

    public static BlockHitResult confirm(Candidate candidate, AutismRotationUtil.Rotation wire,
                                         Vec3 eye, double reach, Request request) {
        if (candidate == null || wire == null || eye == null || request == null) return null;

        if (Math.abs(wire.pitch()) > MAX_PLACEMENT_PITCH) return null;
        Level level = level();
        Player player = player();
        if (level == null || player == null) return null;
        BlockHitResult hit = ray(eye, wire, reach, level, player);
        if (hit == null) return null;
        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);
        if (!request.placement().clickable(hitState, hitPos, request.sneaking())) return null;
        if (!request.placement().lands(hit, candidate.option().cell())) return null;
        Option plan = candidate.option();
        if (hitPos.equals(plan.support()) && hit.getDirection() == plan.face()
            && onFaceRect(plan.rect(), plan.face(), hit.getLocation(), 0.0D)) {
            return hit;
        }
        return rectUnderHit(hitState, hitPos, hit) != null ? hit : null;
    }

    public static AABB[] faceRects(BlockState state, BlockPos support, Direction face, int max) {
        AABB[] boxes = shapeBoxes(state, support);
        if (boxes.length == 0) return NO_RECTS;
        int count = Math.min(boxes.length, MAX_SHAPE_BOXES);
        AABB[] rects = new AABB[count];
        for (int i = 0; i < count; i++) rects[i] = flatten(boxes[i], face);
        count = mergeCoplanar(rects, count, face);

        for (int i = 1; i < count; i++) {
            AABB key = rects[i];
            double area = faceArea(key, face);
            int j = i - 1;
            while (j >= 0 && faceArea(rects[j], face) < area) {
                rects[j + 1] = rects[j];
                j--;
            }
            rects[j + 1] = key;
        }
        int kept = Math.min(count, Math.max(1, max));
        if (kept == rects.length) return rects;
        AABB[] out = new AABB[kept];
        System.arraycopy(rects, 0, out, 0, kept);
        return out;
    }

    public static AABB rectUnderHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        Landing landing = landingUnderHit(state, pos, hit);
        return landing == null ? null : landing.rect();
    }

    public static Landing landingUnderHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (state == null || pos == null || hit == null) return null;
        Vec3 point = hit.getLocation();
        Direction reported = hit.getDirection();
        AABB[] boxes = shapeBoxes(state, pos);
        for (AABB box : boxes) {
            if (onFaceRect(box, reported, point, 0.0D)) return new Landing(box, reported);
        }
        for (AABB box : boxes) {
            for (Direction face : FACE_ORDER_UP_FIRST) {
                if (face != reported && onFaceRect(box, face, point, 0.0D)) {
                    return new Landing(box, face);
                }
            }
        }
        return null;
    }

    public static boolean onFaceRect(AABB rect, Direction face, Vec3 point, double margin) {
        if (rect == null || face == null || point == null) return false;
        Direction.Axis normal = face.getAxis();
        if (Math.abs(coordinate(point, normal) - planeOf(rect, face)) > FACE_EPSILON) return false;
        return within(rect, inPlaneAxis(normal, true), point, margin)
            && within(rect, inPlaneAxis(normal, false), point, margin);
    }

    private static boolean within(AABB rect, Direction.Axis axis, Vec3 point, double margin) {
        double inset = acceptInset(rect, axis, margin);
        double at = coordinate(point, axis);
        return at >= rect.min(axis) + inset - FACE_EPSILON
            && at <= rect.max(axis) - inset + FACE_EPSILON;
    }

    private static double acceptInset(AABB rect, Direction.Axis axis, double margin) {
        double extent = rect.max(axis) - rect.min(axis);
        return Math.max(0.0D, Math.min(margin, extent * 0.5D - margin));
    }

    public static double eyePastPlane(Vec3 eye, AABB rect, Direction face) {
        if (eye == null || rect == null || face == null) return 0.0D;
        double plane = planeOf(rect, face);
        double along = coordinate(eye, face.getAxis());
        return (along - plane)
            * (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D);
    }

    public static Vec3 faceCentre(AABB rect, Direction face) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis first = inPlaneAxis(normal, true);
        Direction.Axis second = inPlaneAxis(normal, false);
        return onPlane(normal, planeOf(rect, face),
            (rect.min(first) + rect.max(first)) * 0.5D,
            (rect.min(second) + rect.max(second)) * 0.5D);
    }

    public static boolean selfOccluded(Vec3 eye, BlockPos support, Direction face, Level level) {
        if (eye == null || support == null || face == null || level == null) return false;
        if (!face.getAxis().isHorizontal()) return false;
        if (Mth.floor(eye.x) != support.getX() || Mth.floor(eye.z) != support.getZ()) return false;
        BlockPos lid = support.above();
        return eye.y >= lid.getY() + 1.0D
            && level.getBlockState(lid).isCollisionShapeFullBlock(level, lid);
    }

    public static boolean useActionEatsClick(BlockState state, BlockPos pos, boolean sneaking) {
        if (state == null || pos == null || !hasUseAction(state, pos)) return false;
        return !sneaking || !(state.getBlock() instanceof BedBlock);
    }

    public static boolean sneakUnlocks(BlockState state, BlockPos pos) {
        return useActionEatsClick(state, pos, false) && !useActionEatsClick(state, pos, true);
    }

    public static boolean isPlaceableSupport(BlockState state, BlockPos support, boolean sneaking) {
        if (state == null || support == null || state.isAir()) return false;

        if (shapeBoxes(state, support).length == 0) return false;
        return !useActionEatsClick(state, support, sneaking);
    }

    public static boolean replaceableInPlace(BlockPos cell, Level level) {
        if (cell == null || level == null) return false;
        BlockState state = level.getBlockState(cell);
        if (state.isAir() || !state.canBeReplaced()) return false;
        if (hasUseAction(state, cell)) return false;
        return shapeBoxes(state, cell).length > 0;
    }

    public static BlockHitResult ray(Vec3 eye, AutismRotationUtil.Rotation rotation, double reach,
                                     Level level, Entity entity) {
        if (eye == null || rotation == null || level == null) return null;
        Vec3 end = eye.add(lookVector(rotation).scale(reach));
        HitResult result = level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
            entity == null ? CollisionContext.empty() : CollisionContext.of(entity)));
        return result instanceof BlockHitResult blockHit && result.getType() == HitResult.Type.BLOCK
            ? blockHit : null;
    }

    public static Direction.Axis inPlaneAxis(Direction.Axis normal, boolean first) {
        return switch (normal) {
            case X -> first ? Direction.Axis.Y : Direction.Axis.Z;
            case Y -> first ? Direction.Axis.X : Direction.Axis.Z;
            case Z -> first ? Direction.Axis.X : Direction.Axis.Y;
        };
    }

    public static Vec3 onPlane(Direction.Axis normal, double plane, double a, double b) {
        return switch (normal) {
            case X -> new Vec3(plane, a, b);
            case Y -> new Vec3(a, plane, b);
            case Z -> new Vec3(a, b, plane);
        };
    }

    public static double coordinate(Vec3 point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x;
            case Y -> point.y;
            case Z -> point.z;
        };
    }

    public static void invalidateShapeCache() {
        for (int i = 0; i < SHAPE_SLOTS; i++) {
            shapePos[i] = null;
            shapeState[i] = null;
            shapeBoxes[i] = null;
        }
        for (int i = 0; i < SCAN_SLOTS; i++) {
            if (scanSlots[i] != null) scanSlots[i].keyKnown = false;
        }
    }

    private static final class ScanSlot {
        private final List<Option> options = new ArrayList<>();
        private long key;
        private boolean keyKnown;
        private int cursor;
    }

    private static final int SCAN_SLOTS = 48;
    private static final ScanSlot[] scanSlots = new ScanSlot[SCAN_SLOTS];
    private static int scanSlotCursor;

    private static ScanSlot scanSlot(Request request, Level level) {
        long key = optionsKey(request, level);
        for (int i = 0; i < SCAN_SLOTS; i++) {
            ScanSlot slot = scanSlots[i];
            if (slot != null && slot.keyKnown && slot.key == key) return slot;
        }
        ScanSlot slot = scanSlots[scanSlotCursor];
        if (slot == null) slot = scanSlots[scanSlotCursor] = new ScanSlot();
        scanSlotCursor = (scanSlotCursor + 1) % SCAN_SLOTS;
        slot.options.clear();
        slot.cursor = 0;

        Predicate<Intent> held = request.banned();
        request.banned(null);
        options(request, slot.options);
        request.banned(held);
        slot.key = key;
        slot.keyKnown = true;
        return slot;
    }

    private static long optionsKey(Request request, Level level) {
        long key = cellKey(request.cell(), level, new BlockPos.MutableBlockPos());
        key = key * 31L + System.identityHashCode(level);
        for (Direction face : request.faceOrder()) key = key * 31L + face.ordinal();
        EnumSet<Direction> allowed = request.allowedFaces();
        key = key * 31L + (allowed == null ? -1L : allowed.hashCode());
        key = key * 31L + (request.allowInPlace() ? 1L : 0L);
        key = key * 31L + request.maxRectsPerFace();
        key = key * 31L + (request.sneaking() ? 2L : 0L);
        key = key * 31L + (request.sneakAllowed() ? 4L : 0L);
        return key;
    }

    private static final int SHAPE_SLOTS = 8;
    private static final BlockPos[] shapePos = new BlockPos[SHAPE_SLOTS];
    private static final BlockState[] shapeState = new BlockState[SHAPE_SLOTS];
    private static final AABB[][] shapeBoxes = new AABB[SHAPE_SLOTS][];
    private static int shapeCursor;

    private static AABB[] shapeBoxes(BlockState state, BlockPos pos) {
        if (state == null || pos == null) return NO_RECTS;
        for (int i = 0; i < SHAPE_SLOTS; i++) {
            if (shapeState[i] == state && pos.equals(shapePos[i])) return shapeBoxes[i];
        }
        Level level = level();
        if (level == null) return NO_RECTS;
        Player player = player();
        VoxelShape shape = state.getShape((BlockGetter) level, pos,
            player == null ? CollisionContext.empty() : CollisionContext.of(player));
        List<AABB> local = shape.isEmpty() ? List.of() : shape.toAabbs();
        AABB[] boxes = local.isEmpty() ? NO_RECTS : new AABB[local.size()];
        for (int i = 0; i < local.size(); i++) boxes[i] = local.get(i).move(pos);
        shapePos[shapeCursor] = pos.immutable();
        shapeState[shapeCursor] = state;
        shapeBoxes[shapeCursor] = boxes;
        shapeCursor = (shapeCursor + 1) % SHAPE_SLOTS;
        return boxes;
    }

    private static int mergeCoplanar(AABB[] rects, int count, Direction face) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis first = inPlaneAxis(normal, true);
        Direction.Axis second = inPlaneAxis(normal, false);
        boolean merged = true;
        while (merged && count > 1) {
            merged = false;
            for (int i = 0; i < count && !merged; i++) {
                for (int j = i + 1; j < count; j++) {
                    if (Math.abs(planeOf(rects[i], face) - planeOf(rects[j], face)) > FACE_EPSILON) {
                        continue;
                    }
                    AABB union = exactUnion(rects[i], rects[j], first, second);
                    if (union == null) continue;
                    rects[i] = union;
                    rects[j] = rects[count - 1];
                    count--;
                    merged = true;
                    break;
                }
            }
        }
        return count;
    }

    private static AABB exactUnion(AABB left, AABB right, Direction.Axis first, Direction.Axis second) {
        if (contains(left, right, first, second)) return left;
        if (contains(right, left, first, second)) return right;
        if (sameExtent(left, right, first) && touches(left, right, second)) return left.minmax(right);
        if (sameExtent(left, right, second) && touches(left, right, first)) return left.minmax(right);
        return null;
    }

    private static boolean contains(AABB outer, AABB inner, Direction.Axis first, Direction.Axis second) {
        return outer.min(first) - FACE_EPSILON <= inner.min(first)
            && outer.max(first) + FACE_EPSILON >= inner.max(first)
            && outer.min(second) - FACE_EPSILON <= inner.min(second)
            && outer.max(second) + FACE_EPSILON >= inner.max(second);
    }

    private static boolean sameExtent(AABB left, AABB right, Direction.Axis axis) {
        return Math.abs(left.min(axis) - right.min(axis)) <= FACE_EPSILON
            && Math.abs(left.max(axis) - right.max(axis)) <= FACE_EPSILON;
    }

    private static boolean touches(AABB left, AABB right, Direction.Axis axis) {
        return left.min(axis) <= right.max(axis) + FACE_EPSILON
            && right.min(axis) <= left.max(axis) + FACE_EPSILON;
    }

    private static AABB flatten(AABB box, Direction face) {
        double plane = planeOf(box, face);
        return switch (face.getAxis()) {
            case X -> new AABB(plane, box.minY, box.minZ, plane, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, plane, box.minZ, box.maxX, plane, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, plane, box.maxX, box.maxY, plane);
        };
    }

    private static double planeOf(AABB rect, Direction face) {
        Direction.Axis normal = face.getAxis();
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? rect.max(normal) : rect.min(normal);
    }

    private static double faceArea(AABB rect, Direction face) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis first = inPlaneAxis(normal, true);
        Direction.Axis second = inPlaneAxis(normal, false);
        return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
    }

    private static boolean hasUseAction(BlockState state, BlockPos pos) {
        Level level = level();
        if (level != null && state.getMenuProvider(level, pos) != null) return true;
        Block block = state.getBlock();
        if (block instanceof FenceBlock) return leadingLeashedMob();
        boolean safeBlockEntity = block instanceof AbstractSkullBlock || block instanceof ConduitBlock
            || block instanceof SculkSensorBlock || block instanceof SculkShriekerBlock
            || block instanceof SculkCatalystBlock || block instanceof SpawnerBlock
            || block instanceof BrushableBlock;
        if (!safeBlockEntity && block instanceof BaseEntityBlock) return true;
        return block instanceof BedBlock || block instanceof DoorBlock
            || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
            || block instanceof ButtonBlock || block instanceof LeverBlock
            || block instanceof NoteBlock || block instanceof CakeBlock
            || block instanceof CandleCakeBlock || block instanceof RespawnAnchorBlock
            || block instanceof DiodeBlock || block instanceof DragonEggBlock
            || block instanceof RedStoneWireBlock || block instanceof RedStoneOreBlock
            || block instanceof FlowerPotBlock || block instanceof AbstractCauldronBlock
            || block instanceof ComposterBlock || block instanceof CaveVines
            || block instanceof SweetBerryBushBlock;
    }

    private static int leashTick = Integer.MIN_VALUE;
    private static boolean leashLeading;

    private static boolean leadingLeashedMob() {
        Player player = player();
        if (player == null) return false;
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != leashTick) {
            leashTick = tick;
            leashLeading = !Leashable.leashableInArea(player, led -> led.getLeashHolder() == player)
                .isEmpty();
        }
        return leashLeading;
    }

    private static Vec3 lookVector(AutismRotationUtil.Rotation rotation) {
        float yaw = -rotation.yaw() * Mth.DEG_TO_RAD;
        float pitch = -rotation.pitch() * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitch);
        return new Vec3(Mth.sin(yaw) * cosPitch, Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
    }

    private static AutismRotationUtil.Rotation aimFrom(Request request) {
        if (request.from() != null) return request.from();
        Player player = player();
        return player == null ? null : new AutismRotationUtil.Rotation(player.getYRot(), player.getXRot());
    }

    private static Level level() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.level;
    }

    private static Player player() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    private static void set(Refusal[] refusal, Refusal value) {
        if (refusal != null && refusal.length > 0) refusal[0] = value;
    }

    private static Aim refuse(Refusal[] refusal, Refusal reason, Request request, Direction face) {
        set(refusal, reason);
        note(request, face, reason);
        return null;
    }

    private static Candidate refuseCandidate(Refusal[] refusal, Refusal reason, Request request,
                                             Direction face) {
        set(refusal, reason);
        note(request, face, reason);
        return null;
    }

    private static void note(Request request, Direction face, Refusal reason) {
        if (request == null || face == null) return;
        StringBuilder trace = request.trace();
        if (trace == null || trace.length() >= 96) return;
        if (trace.length() > 0) trace.append(' ');
        trace.append(face.getSerializedName().charAt(0)).append(':').append(reason.code());
    }
}
