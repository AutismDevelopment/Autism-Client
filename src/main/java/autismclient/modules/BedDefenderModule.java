package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismCombatClicker;
import autismclient.util.AutismFaceScan;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismHumanRotation;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismPlacementTick;
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.RegistryListCodec;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BedDefenderModule extends Module implements AutismSilentAim.Owner {

    private static final float TURN_CAP = 55.0F;

    private static final double CELL_REACH_SLACK = 1.0D;

    private static final int SWITCH_BACK_IDLE_TICKS = 6;

    private static final int OWNERSHIP_TAIL_TICKS = 5;

    private static final int HOTBAR_COUNT_MARGIN = 16;

    private static final int MAX_PLACED_MEMORY = 64;

    private static final int MAX_TRACE_CHARS = 96;

    private static final int MAX_TICK_PROBE_CLIPS = 16;

    private static final int MAX_CANDIDATES = 4096;

    private static final int SHELL_COMPLETE_TICKS = 20;

    private static final int GATE_STALL_TICKS = 8;
    private static final int GATE_STALL_COOLDOWN_TICKS = 60;

    private static final int MAX_GATE_STALLS = 16;

    private static final int MAX_BANNED_CLICKS = 12;

    private static final double CAPPED_CELL_HEIGHT = 0.5D;

    private static final int MAX_COMPLETED_BEDS = 16;

    private record Plan(BlockPos cell, AutismFaceScan.Intent intent,
                        AutismFaceScan.Candidate candidate) {

        Direction face() {
            return candidate.option().face();
        }

        AutismRotationUtil.Rotation goal() {
            return candidate.aim().goal();
        }

        boolean needsSneak() {
            return candidate.option().requiresSneak();
        }
    }

    private record Step(BlockPos pos, int layer) {
    }

    private record ShellCell(BlockPos pos, int layer, boolean elevated, boolean supported,
                             int unlocks, boolean axisEnd, double distSq) {
    }

    private static final class GateStall {

        private int charges;

        private int cooldown;

        private int idle;

        private final List<AutismFaceScan.Intent> banned = new ArrayList<>();
    }

    private final List<BlockPos> targets = new ArrayList<>();

    private final Set<BlockPos> placedByUs = new LinkedHashSet<>();

    private BlockPos cachedBed;
    private int sinceSweep;

    private final Set<BlockPos> completedBeds = new LinkedHashSet<>();

    private BlockPos progressBed;

    private BlockPos progressPartner;
    private int sinceProgressTicks;

    private final Map<BlockPos, GateStall> gateStalls = new LinkedHashMap<>();

    private int lastPlaceTick = Integer.MIN_VALUE;

    private int aimTick = Integer.MIN_VALUE;

    private boolean sneakRequested;

    private int sneakRequestTick = Integer.MIN_VALUE;

    private int originalSlot = -1;

    private int switchedToSlot = -1;
    private int idleTicks;

    private final KillAuraModule.TickVerdict throwableVerdict = new KillAuraModule.TickVerdict();

    private final List<AutismFaceScan.Option> options = new ArrayList<>();

    private int[] optionRank = new int[0];

    private int cursor;

    private long shellKey;
    private long rankKey;
    private boolean shellKeyKnown;

    private Plan[] answers = new Plan[0];
    private int[] answerTick = new int[0];

    private final AutismFaceScan.Budget rayBudget = new AutismFaceScan.Budget(MAX_TICK_PROBE_CLIPS);

    private int planTick = Integer.MIN_VALUE;

    private boolean visitStarved;

    private boolean visitOutranked;

    private final Map<BlockPos, Boolean> obstructedCells = new LinkedHashMap<>();

    private String traceDetail;

    private String cachedFilterRaw;
    private Set<Block> cachedFilterBlocks = Set.of();

    public BedDefenderModule() {
        super("bed-defender", "BedDefender", ModuleCategory.PLAYER,
            "Walls in a bed with your hardest blocks.");

        add(new IntSetting("max-layers", "Max Layers", 1, 1, 5, 1)
            .group("Bed")
            .description("Shell depth around the bed"));

        add(new BoolSetting("prefer-sides", "Prefer Sides", true)
            .group("Bed")
            .description("Sides before the top"));

        add(new BoolSetting("defend-under", "Defend Under", true)
            .group("Bed")
            .description("Also fill under the bed"));

        add(new BoolSetting("top-riser", "Top Riser", true)
            .group("Bed")
            .visibleWhen(() -> integer("max-layers") == 1)
            .description("Riser to reach the top"));

        add(new BoolSetting("require-sneak", "Require Sneak", false)
            .group("Bed")
            .description("Only while holding shift"));

        add(new IntSetting("rescan-interval", "Rescan Interval", 10, 1, 40, 1)
            .group("Bed")
            .unit("ticks")
            .description("Ticks between full bed sweeps"));

        add(new ChoiceSetting("filter-mode", "Filter", "Whitelist", "Whitelist", "Blacklist")
            .group("Blocks")
            .description("How the list is applied"));
        add(RegistryListSetting.blocks("blocks", "Blocks",
                "minecraft:obsidian|minecraft:end_stone|minecraft:ender_chest")
            .group("Blocks")
            .description("Defence material"));

        add(new BoolSetting("allow-chests", "Allow Chests", false)
            .group("Blocks")
            .description("Also allow chests as material"));

        add(new BoolSetting("sneak-for-bed", "Sneak For Bed", true)
            .group("Placing")
            .description("Crouch to place on bed"));
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("Placing")
            .description("Return to your old slot"));

        add(new BoolSetting("trace-faces", "Trace Faces", false)
            .group("Placing")
            .description("Show why faces were refused"));
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();

        AutismKillAuraRotation.beginWindDown(id());
    }

    @Override
    public void onGameLeft() {
        reset();

        if (id().equals(AutismKillAuraRotation.currentOwner())) AutismKillAuraRotation.reset();
    }

    private void reset() {
        dropTargets();
        placedByUs.clear();
        cachedBed = null;
        completedBeds.clear();
        forgetProgress();
        forgetPlans();
        gateStalls.clear();
        sinceSweep = 0;
        lastPlaceTick = Integer.MIN_VALUE;

        sneakRequested = false;
        originalSlot = -1;
        switchedToSlot = -1;
        idleTicks = 0;
        AutismHandArbiter.releaseAll(id());
    }

    public static Input modifyMovementInput(ClientInput source, Input original) {
        if (original == null || MC == null || MC.player == null || MC.player.input != source) {
            return original;
        }
        Module module = ModuleRegistry.get("bed-defender");
        if (!(module instanceof BedDefenderModule bed) || !bed.isEnabled()) return original;
        if (!bed.sneakLive() || original.shift()) return original;
        return new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), true, original.sprint());
    }

    private boolean sneakLive() {
        if (!sneakRequested) return false;
        if (sneakRequestTick != AutismSharedState.get().getClientTickCounter()) {
            sneakRequested = false;
            return false;
        }
        return sneakPressIsOnlyACrouch();
    }

    private boolean sneakPressIsOnlyACrouch() {
        if (MC == null || MC.player == null) return false;
        if (MC.player.isPassenger() || MC.player.getAbilities().flying) return false;
        return !PacketTeleportController.ownsMainMovement() && !MultiPilot.isActive();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return AutismKillAuraRotation.hasCurrentRotation()
            && id().equals(AutismKillAuraRotation.currentOwner());
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null) return;
        if (isEnabled()) return;

        if (!id().equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.update(id(), MC.player);
    }

    @Override
    public String info() {
        if (!targets.isEmpty()) {
            String count = Integer.toString(targets.size());
            return traceDetail == null ? count : count + " " + traceDetail;
        }
        if (cachedBed != null) return "bed";
        return "";
    }

    public static boolean ownsSilentRotation() {
        Module module = ModuleRegistry.get(AutismKillAuraRotation.OWNER_BED_DEFENDER);
        if (!(module instanceof BedDefenderModule bed)) return false;

        int age = AutismSharedState.get().getClientTickCounter() - bed.aimTick;
        return age >= 0 && age <= OWNERSHIP_TAIL_TICKS
            && AutismKillAuraRotation.OWNER_BED_DEFENDER.equals(AutismKillAuraRotation.currentOwner());
    }

    @Override
    public boolean silentCorrectionApplies() {
        boolean enabled = isEnabled();
        return !AutismSilentAim.scaffoldOwnsRotation()
            && (AutismKillAuraRotation.isWindingDown() || enabled && canRun());
    }

    private boolean canRun() {

        return canPlan() && !throwableHeldThisTick();
    }

    private boolean canPlan() {
        return MC != null && MC.player != null && MC.level != null && MC.getConnection() != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !PackHideState.isActive()

            && !PackFreecamState.isActive()
            && !AutismRemoteView.isActive()
            && !MultiPilot.isActive()
            && !PacketTeleportController.ownsMainMovement()
            && !MacroExecutor.isRunning()
            && !MC.player.isDeadOrDying()
            && !MC.player.isSpectator()

            && !MC.player.isUsingItem()
            && !MC.player.isHandsBusy()
            && !AutismSilentAim.scaffoldOwnsRotation()

            && !ScaffoldModule.reservesRageInput()

            && !AutoTotemModule.operationActive()
            && !AutoArmorModule.operationActive();
    }

    private boolean throwableHeldThisTick() {
        return throwableVerdict.resolve(
            AutismSharedState.get().getClientTickCounter(), this::holdsInstantThrowable);
    }

    private boolean holdsInstantThrowable() {
        if (MC == null || MC.player == null) return false;
        ItemStack mainHand = MC.player.getMainHandItem();
        if (KillAuraModule.isInstantThrowable(mainHand)) return true;
        return mainHand.isEmpty() && KillAuraModule.isInstantThrowable(MC.player.getOffhandItem());
    }

    private void standDown() {
        if (MC == null || MC.player == null) return;

        if (!id().equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.beginWindDown(id());
        AutismKillAuraRotation.update(id(), MC.player);
    }

    @Override
    public void preMovementTick() {

        sneakRequested = false;
        if (MC == null || MC.player == null || MC.level == null || MC.getConnection() == null) {
            dropTargets();
            standDown();
            return;
        }

        tickPendingPlacement();

        if (bool("require-sneak") && !MC.player.isShiftKeyDown()) {
            dropTargets();
            standDown();
            return;
        }

        int slot = resolveHotbarSlot();
        ItemStack material = slot < 0 ? ItemStack.EMPTY : MC.player.getInventory().getItem(slot);

        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        AutismRotationUtil.Rotation wireRotation = wire.initialized()
            ? new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
            : null;
        AutismRotationUtil.Rotation from = wireRotation != null
            ? wireRotation : AutismRotationUtil.playerRotation(MC.player);
        beginPlanTick();
        buildTargets(material);

        refreshOptions(from, material);
        runPlacement(slot, material, wireRotation, from);
    }

    private void beginPlanTick() {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == planTick) return;
        planTick = tick;
        rayBudget.reset(MAX_TICK_PROBE_CLIPS);
        forgetPlans();
    }

    private void forgetPlans() {
        obstructedCells.clear();
        traceDetail = null;
    }

    private void dropTargets() {
        targets.clear();
        options.clear();
        optionRank = new int[0];
        answers = new Plan[0];
        answerTick = new int[0];
        shellKeyKnown = false;
        cursor = 0;
    }

    private void buildTargets(ItemStack material) {
        targets.clear();

        Vec3 eye = MC.player.getEyePosition();
        double reach = MC.player.blockInteractionRange();
        int layers = integer("max-layers");

        BlockPos bedPos = resolveBed(eye, reach + layers + 1.0D);
        if (bedPos == null) {
            forgetProgress();
            return;
        }

        BlockState bedState = MC.level.getBlockState(bedPos);
        if (!(bedState.getBlock() instanceof BedBlock)) {
            cachedBed = null;
            forgetProgress();
            return;
        }

        Direction other = BedBlock.getConnectedDirection(bedState);
        Direction outward = other.getOpposite();
        Direction[] perps = other.getAxis() == Direction.Axis.X
            ? new Direction[]{Direction.NORTH, Direction.SOUTH}
            : new Direction[]{Direction.WEST, Direction.EAST};

        BlockPos partner = bedPos.relative(other);

        Set<BlockPos> visited = new HashSet<>();
        List<Step> steps = new ArrayList<>();
        collectCells(bedPos, layers, new Direction[]{outward, Direction.UP, perps[0], perps[1]},
            visited, steps);
        collectCells(partner, layers, new Direction[]{other, Direction.UP, perps[0], perps[1]},
            visited, steps);
        if (bool("defend-under")) {
            if (visited.add(bedPos.below())) steps.add(new Step(bedPos.below(), 1));
            if (visited.add(partner.below())) steps.add(new Step(partner.below(), 1));
        }

        double limit = reach + CELL_REACH_SLACK;
        double limitSq = limit * limit;
        int bedY = bedPos.getY();

        boolean sneaking = serverSeesSneak() || sneakForBed();
        List<ShellCell> pool = new ArrayList<>(steps.size());
        for (Step step : steps) {
            ShellCell candidate = admit(step.pos(), step.layer(), bedPos, partner, bedY, eye,
                limitSq, material, sneaking);
            if (candidate != null) {
                pool.add(candidate);
                continue;
            }
            BlockPos capped = cappedPromotion(step.pos(), bedY, material, visited);
            if (capped == null) continue;
            ShellCell above = admit(capped, step.layer(), bedPos, partner, bedY, eye, limitSq,
                material, sneaking);
            if (above != null) pool.add(above);
        }
        promoteRisers(pool, visited, bedPos, partner, outward, other, layers, bedY, eye, limitSq,
            material, sneaking);

        Set<BlockPos> blocked = new HashSet<>();
        for (ShellCell candidate : pool) {
            if (!candidate.supported()) blocked.add(candidate.pos());
        }
        Direction.Axis bedAxis = other.getAxis();
        List<ShellCell> candidates = new ArrayList<>(pool.size());
        for (ShellCell candidate : pool) {
            int unlocks = 0;
            for (Direction direction : Direction.values()) {
                if (blocked.contains(candidate.pos().relative(direction))) unlocks++;
            }
            candidates.add(new ShellCell(candidate.pos(), candidate.layer(), candidate.elevated(),
                candidate.supported(), unlocks, onBedAxis(candidate.pos(), bedPos, bedAxis, bedY),
                candidate.distSq()));
        }

        boolean preferSides = bool("prefer-sides");

        boolean topFirst = sneaking;
        candidates.sort((left, right) -> {

            if (left.layer() != right.layer()) return Integer.compare(left.layer(), right.layer());

            boolean leftRefill = placedByUs.contains(left.pos());
            if (leftRefill != placedByUs.contains(right.pos())) return leftRefill ? -1 : 1;

            if (left.supported() != right.supported()) return left.supported() ? -1 : 1;
            if (left.unlocks() != right.unlocks()) {
                return Integer.compare(right.unlocks(), left.unlocks());
            }
            if (left.elevated() != right.elevated()) {

                if (topFirst) return left.elevated() ? -1 : 1;
                if (preferSides) return left.elevated() ? 1 : -1;
            }

            if (topFirst && left.axisEnd() != right.axisEnd()) return left.axisEnd() ? -1 : 1;
            return Double.compare(left.distSq(), right.distSq());
        });
        for (ShellCell candidate : candidates) targets.add(candidate.pos());

        if (!bedPos.equals(progressBed)) {
            progressBed = bedPos.immutable();
            sinceProgressTicks = 0;

            completedBeds.remove(bedPos);
            completedBeds.remove(partner);
        }

        progressPartner = partner.immutable();
    }

    private void forgetProgress() {
        progressBed = null;
        progressPartner = null;
        sinceProgressTicks = 0;
    }

    private boolean chargeProgress() {
        if (progressBed == null || progressPartner == null) return false;
        if (++sinceProgressTicks < shellCompleteTicks()) return false;
        markShellComplete(progressBed, progressPartner);
        return true;
    }

    private int shellCompleteTicks() {
        return SHELL_COMPLETE_TICKS;
    }

    private static boolean onBedAxis(BlockPos pos, BlockPos bedPos, Direction.Axis axis, int bedY) {
        if (pos.getY() != bedY) return false;
        return axis == Direction.Axis.X ? pos.getZ() == bedPos.getZ() : pos.getX() == bedPos.getX();
    }

    private ShellCell admit(BlockPos pos, int layer, BlockPos bedPos, BlockPos partner, int bedY,
                            Vec3 eye, double limitSq, ItemStack material, boolean sneaking) {

        if (pos.equals(bedPos) || pos.equals(partner)) return null;
        if (MC.level.isOutsideBuildHeight(pos) || !MC.level.isLoaded(pos)) return null;
        if (!fillable(pos, material)) return null;
        double distSq = pos.distToCenterSqr(eye);
        if (distSq > limitSq) return null;

        return new ShellCell(pos.immutable(), layer, pos.getY() > bedY,
            supportedNow(pos, eye, material, sneaking), 0, false, distSq);
    }

    private BlockPos cappedPromotion(BlockPos pos, int bedY, ItemStack material,
                                     Set<BlockPos> visited) {
        if (pos.getY() != bedY) return null;
        BlockState state = MC.level.getBlockState(pos);

        if (state.isAir() || replacedInPlace(state, pos, material)) return null;

        AABB[] tops = AutismFaceScan.faceRects(state, pos, Direction.UP, Integer.MAX_VALUE);
        if (tops.length == 0) return null;
        for (AABB top : tops) {
            if (top.maxY - pos.getY() >= CAPPED_CELL_HEIGHT) return null;
        }
        BlockPos above = pos.above();
        return visited.add(above) ? above : null;
    }

    private void promoteRisers(List<ShellCell> pool, Set<BlockPos> visited, BlockPos bedPos,
                               BlockPos partner, Direction outward, Direction other, int layers,
                               int bedY, Vec3 eye, double limitSq, ItemStack material,
                               boolean sneaking) {
        if (layers != 1 || !bool("top-riser")) return;
        addRiser(pool, visited, bedPos.above(), bedPos.relative(outward).above(), bedPos, partner,
            bedY, eye, limitSq, material, sneaking);
        addRiser(pool, visited, partner.above(), partner.relative(other).above(), bedPos, partner,
            bedY, eye, limitSq, material, sneaking);
    }

    private void addRiser(List<ShellCell> pool, Set<BlockPos> visited, BlockPos top, BlockPos riser,
                          BlockPos bedPos, BlockPos partner, int bedY, Vec3 eye, double limitSq,
                          ItemStack material, boolean sneaking) {

        for (ShellCell candidate : pool) {
            if (!candidate.pos().equals(top)) continue;
            if (candidate.supported() || !visited.add(riser)) return;

            ShellCell promoted =
                admit(riser, 2, bedPos, partner, bedY, eye, limitSq, material, sneaking);
            if (promoted != null) pool.add(promoted);
            return;
        }
    }

    private boolean fillable(BlockPos pos, ItemStack material) {
        return replacedInPlace(MC.level.getBlockState(pos), pos, material);
    }

    private boolean replacedInPlace(BlockState state, BlockPos pos, ItemStack material) {
        if (!state.canBeReplaced()) return false;

        if (state.isAir() || !(material.getItem() instanceof BlockItem)) return true;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, material, hit)
            .replacingClickedOnBlock();
    }

    private boolean supportedNow(BlockPos cell, Vec3 eye, ItemStack material, boolean sneaking) {

        if (AutismFaceScan.replaceableInPlace(cell, MC.level)) return true;
        for (Direction face : AutismFaceScan.FACE_ORDER_UP_FIRST) {

            BlockPos support = cell.relative(face.getOpposite());
            BlockState state = MC.level.getBlockState(support);
            if (!isPlaceableSupport(state, support, material, sneaking)) continue;

            if (AutismFaceScan.selfOccluded(eye, support, face, MC.level)) continue;
            if (eyeOutsideFace(state, support, face, eye)) return true;
        }
        return false;
    }

    private boolean eyeOutsideFace(BlockState state, BlockPos support, Direction face, Vec3 eye) {
        for (AABB rect : AutismFaceScan.faceRects(state, support, face, Integer.MAX_VALUE)) {
            if (AutismFaceScan.eyePastPlane(eye, rect, face) > AutismFaceScan.FACE_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private void markShellComplete(BlockPos bedPos, BlockPos partner) {
        dropTargets();
        forgetProgress();
        cachedBed = null;

        sinceSweep = integer("rescan-interval");
        completedBeds.add(bedPos.immutable());
        completedBeds.add(partner.immutable());
        while (completedBeds.size() > MAX_COMPLETED_BEDS) {
            completedBeds.remove(completedBeds.iterator().next());
        }
    }

    private static void collectCells(BlockPos seed, int layers, Direction[] directions,
                                     Set<BlockPos> visited, List<Step> out) {
        ArrayDeque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(seed, 0));
        visited.add(seed);
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (step.layer() > 0) out.add(step);
            if (step.layer() >= layers) continue;
            for (Direction direction : directions) {
                BlockPos next = step.pos().relative(direction);
                if (visited.add(next)) queue.add(new Step(next, step.layer() + 1));
            }
        }
    }

    private BlockPos resolveBed(Vec3 eye, double radius) {
        if (sinceSweep < Integer.MAX_VALUE) sinceSweep++;
        BlockPos cached = cachedBed;
        if (cached != null && isDefendableBed(cached, eye, radius)) return cached;

        boolean lost = cached != null;
        cachedBed = null;
        if (!lost && sinceSweep < integer("rescan-interval")) return null;
        sinceSweep = 0;
        cachedBed = nearestBed(eye, radius, true);
        if (cachedBed == null && !completedBeds.isEmpty()) {

            completedBeds.clear();
            cachedBed = nearestBed(eye, radius, false);
        }
        return cachedBed;
    }

    private boolean isDefendableBed(BlockPos pos, Vec3 eye, double radius) {
        if (pos.distToCenterSqr(eye) > radius * radius) return false;
        return MC.level.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private BlockPos nearestBed(Vec3 eye, double radius, boolean skipCompleted) {
        int minX = Mth.floor(eye.x - radius);
        int maxX = Mth.ceil(eye.x + radius);
        int minZ = Mth.floor(eye.z - radius);
        int maxZ = Mth.ceil(eye.z + radius);
        int minY = Math.max(Mth.floor(eye.y - radius), MC.level.getMinY());
        int maxY = Math.min(Mth.ceil(eye.y + radius), MC.level.getMaxY());
        if (minY > maxY) return null;

        BlockPos best = null;
        double bestSq = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            double distSq = pos.distToCenterSqr(eye);
            if (distSq > bestSq) continue;
            if (skipCompleted && completedBeds.contains(pos)) continue;
            if (!(MC.level.getBlockState(pos).getBlock() instanceof BedBlock)) continue;

            best = pos.immutable();
            bestSq = distSq;
        }
        return best;
    }

    private void runPlacement(int slot, ItemStack material,
                              AutismRotationUtil.Rotation wireRotation,
                              AutismRotationUtil.Rotation from) {

        if (!canPlan()) {
            standDown();
            return;
        }

        if (throwableHeldThisTick()) {
            standDown();
            if (slot >= 0 && slot != MC.player.getInventory().getSelectedSlot()) selectSlot(slot);
            return;
        }

        if (AutismBlinkManager.holdsActionsWithoutMovement()) {
            standDown();
            return;
        }
        if (targets.isEmpty() || slot < 0) {
            standDown();
            maybeSwitchBack();
            return;
        }

        if (chargeProgress()) {
            standDown();
            maybeSwitchBack();
            return;
        }

        boolean sneaking = serverSeesSneak();
        ageGateStalls();
        Plan plan = planNextTarget(from, material, null, sneaking);
        if (plan == null) {

            standDown();
            maybeSwitchBack();
            return;
        }
        boolean slotReady = slot == MC.player.getInventory().getSelectedSlot();

        BlockHitResult hit = null;
        if (wireRotation != null) {
            hit = wireRay(plan, wireRotation, sneaking);

            boolean sneakPending = plan.needsSneak() && !sneaking;
            if (hit == null && slotReady && !sneakPending
                && chargeGateStall(plan, wireRotation)) {
                plan = planNextTarget(from, material, null, sneaking);
                hit = plan == null ? null : wireRay(plan, wireRotation, sneaking);
            }
        }
        if (plan == null) {
            standDown();
            maybeSwitchBack();
            return;
        }

        idleTicks = 0;

        boolean fire = hit != null && slotReady && cadenceHolds();

        boolean aimed;
        Plan next = null;
        if (fire) {
            next = planNextTarget(from, material, plan.cell(), sneaking);
            aimed = pumpAim(next != null ? next.goal() : plan.goal(), true);
        } else {
            aimed = pumpAim(plan.goal(), false);
        }

        sneakRequested = sneakForBed()
            && (plan.needsSneak() || next != null && next.needsSneak());

        sneakRequestTick = AutismSharedState.get().getClientTickCounter();

        if (!fire || !aimed) {
            if (!slotReady) selectSlot(slot);
            return;
        }
        commit(plan, hit);
    }

    private boolean pumpAim(AutismRotationUtil.Rotation goal, boolean holdPitch) {
        AutismKillAuraRotation.setTarget(id(), AutismKillAuraRotation.PRIORITY_BED_DEFENDER, goal);

        aimTick = AutismSharedState.get().getClientTickCounter();
        if (!id().equals(AutismKillAuraRotation.currentOwner())) return false;

        AutismKillAuraRotation.update(id(), MC.player, TURN_CAP, holdPitch ? 0.0F : TURN_CAP,
            AutismHumanRotation.MotionProfile.BED_SHELL);
        return true;
    }

    private boolean chargeGateStall(Plan plan, AutismRotationUtil.Rotation wire) {
        double band = AutismHumanRotation.settleBandDegrees(AutismRotationUtil.sensitivityGcd());
        if (AutismRotationUtil.angleTo(wire, plan.goal()) > band * 2.0D) return false;
        BlockPos key = plan.cell().immutable();

        while (!gateStalls.containsKey(key) && gateStalls.size() >= MAX_GATE_STALLS) {
            gateStalls.remove(gateStalls.keySet().iterator().next());
        }
        GateStall stall = gateStalls.computeIfAbsent(key, k -> new GateStall());

        if (stall.idle > 1) stall.charges = 0;
        stall.idle = 0;
        if (++stall.charges < GATE_STALL_TICKS) return false;
        stall.charges = 0;
        stall.cooldown = GATE_STALL_COOLDOWN_TICKS;

        AutismFaceScan.Intent refusal = plan.intent();
        if (!stall.banned.contains(refusal)) stall.banned.add(refusal);
        while (stall.banned.size() > MAX_BANNED_CLICKS) stall.banned.remove(0);
        return true;
    }

    private void ageGateStalls() {
        Iterator<Map.Entry<BlockPos, GateStall>> entries = gateStalls.entrySet().iterator();
        while (entries.hasNext()) {
            GateStall stall = entries.next().getValue();
            if (stall.cooldown > 0) stall.cooldown--;
            stall.idle++;

            if (stall.idle > 1) stall.charges = 0;
            if (stall.cooldown == 0) stall.banned.clear();
            if (stall.charges == 0 && stall.cooldown == 0) entries.remove();
        }
    }

    private void refreshOptions(AutismRotationUtil.Rotation from, ItemStack material) {
        long key = AutismFaceScan.shellKey(targets, MC.level);
        if (shellKeyKnown && key == shellKey) {

            long ranks = rankKey();
            if (ranks != rankKey) {
                rankKey = ranks;
                refreshRanks();
            }
            return;
        }
        shellKey = key;
        rankKey = rankKey();
        shellKeyKnown = true;
        options.clear();
        cursor = 0;
        AutismFaceScan.Request template = scanRequest(from, material, serverSeesSneak(), null);
        int[] ranks = new int[Math.min(MAX_CANDIDATES, Math.max(16, targets.size() * 8))];
        int count = 0;
        for (int rank = 0; rank < targets.size() && options.size() < MAX_CANDIDATES; rank++) {
            AutismFaceScan.options(template.cell(targets.get(rank)), options);
            if (options.size() > MAX_CANDIDATES) {

                options.subList(MAX_CANDIDATES, options.size()).clear();
            }
            if (ranks.length < options.size()) {
                ranks = Arrays.copyOf(ranks, Math.max(options.size(), ranks.length * 2));
            }
            while (count < options.size()) ranks[count++] = rank;
        }
        optionRank = Arrays.copyOf(ranks, count);
        answers = new Plan[options.size()];
        answerTick = new int[options.size()];
        Arrays.fill(answerTick, Integer.MIN_VALUE);
    }

    private long rankKey() {
        long key = 1125899906842597L;
        for (BlockPos cell : targets) key = key * 31L + cell.asLong();
        return key;
    }

    private void refreshRanks() {
        Map<BlockPos, Integer> rank = new HashMap<>(targets.size() * 2);
        for (int i = 0; i < targets.size(); i++) rank.put(targets.get(i), i);
        for (int i = 0; i < options.size() && i < optionRank.length; i++) {
            Integer now = rank.get(options.get(i).cell());

            if (now != null) optionRank[i] = now;
        }
    }

    private Plan planNextTarget(AutismRotationUtil.Rotation from, ItemStack material, BlockPos skip,
                                boolean sneaking) {
        int size = options.size();
        if (size == 0) return null;

        if (cursor >= size) cursor = 0;
        AABB playerBox = MC.player.getBoundingBox();
        Vec3 delta = MC.player.getDeltaMovement();
        BlockState placed = material.getItem() instanceof BlockItem blockItem
            ? blockItem.getBlock().defaultBlockState() : null;
        int tick = AutismSharedState.get().getClientTickCounter();
        StringBuilder trace = bool("trace-faces") ? new StringBuilder() : null;
        AutismFaceScan.Request request = scanRequest(from, material, sneaking, trace);
        AutismFaceScan.Refusal[] refusal = new AutismFaceScan.Refusal[1];

        Plan best = null;
        float bestAngle = Float.MAX_VALUE;
        int bestRank = Integer.MAX_VALUE;
        int start = cursor;
        for (int step = 0; step < size; step++) {
            int index = (start + step) % size;
            AutismFaceScan.Option option = options.get(index);
            BlockPos cell = option.cell();
            int rank = index < optionRank.length ? optionRank[index] : Integer.MAX_VALUE;

            if (cell.equals(skip) || rank > bestRank
                || cellBlocked(cell, placed, playerBox, delta)) {
                cursor = index + 1 == size ? 0 : index + 1;
                continue;
            }
            GateStall stall = gateStalls.get(cell);
            if (stall != null && stall.cooldown > 0 && stall.banned.contains(option.intent())) {
                note(trace, option.face(), AutismFaceScan.Refusal.BANNED);
                cursor = index + 1 == size ? 0 : index + 1;
                continue;
            }

            Plan rival = rank == bestRank ? best : null;
            Plan plan;
            if (answerTick[index] == tick) {
                plan = answers[index];
            } else {
                plan = examine(option, request, refusal, from, rival, bestAngle, trace);

                if (visitStarved) {
                    cursor = index;
                    break;
                }

                if (!visitOutranked) {
                    answers[index] = plan;
                    answerTick[index] = tick;
                }
            }
            cursor = index + 1 == size ? 0 : index + 1;
            if (plan == null) continue;

            float angle = AutismRotationUtil.angleTo(from, plan.goal());
            if (best != null && rank == bestRank
                && !beatsIncumbent(plan.face() == Direction.UP, angle, best, bestAngle)) {
                continue;
            }
            best = plan;
            bestAngle = angle;
            bestRank = rank;
        }
        if (trace != null && traceDetail == null && trace.length() > 0) {
            traceDetail = trace.toString();
        }
        return best;
    }

    private Plan examine(AutismFaceScan.Option option, AutismFaceScan.Request request,
                         AutismFaceScan.Refusal[] refusal, AutismRotationUtil.Rotation from,
                         Plan rival, float rivalAngle, StringBuilder trace) {
        visitStarved = false;
        visitOutranked = false;
        boolean up = option.face() == Direction.UP;
        if (rival != null && rival.face() == Direction.UP && !up) {
            visitOutranked = true;
            note(trace, option.face(), AutismFaceScan.Refusal.OUTRANKED);
            return null;
        }
        AutismFaceScan.Aim aim = AutismFaceScan.solve(option, request.cell(option.cell()), refusal);
        if (aim == null) return null;
        if (rival != null
            && !beatsIncumbent(up, AutismRotationUtil.angleTo(from, aim.goal()), rival, rivalAngle)) {
            visitOutranked = true;
            note(trace, option.face(), AutismFaceScan.Refusal.OUTRANKED);
            return null;
        }
        AutismFaceScan.Candidate candidate = AutismFaceScan.probe(option, aim, request, refusal);
        if (candidate == null) {

            visitStarved = refusal[0] == AutismFaceScan.Refusal.NO_BUDGET;
            return null;
        }
        return new Plan(option.cell(), option.intent(), candidate);
    }

    private AutismFaceScan.Request scanRequest(AutismRotationUtil.Rotation from, ItemStack material,
                                               boolean sneaking, StringBuilder trace) {
        Vec3 eye = MC.player.getEyePosition();
        return new AutismFaceScan.Request(null, eye, MC.player.blockInteractionRange(),
            AutismFaceScan.blockItem(material, MC.player, InteractionHand.MAIN_HAND))
            .from(from)
            .sneaking(sneaking)
            .sneakAllowed(sneakForBed())
            .leadEye(eye.add(MC.player.getDeltaMovement()))
            .budget(rayBudget)
            .trace(trace);
    }

    private boolean sneakForBed() {
        return bool("sneak-for-bed") && sneakPressIsOnlyACrouch();
    }

    private boolean cellBlocked(BlockPos cell, BlockState placed, AABB playerBox, Vec3 delta) {
        Boolean known = obstructedCells.get(cell);
        if (known != null) return known;
        boolean blocked = cellObstructed(cell, placed, playerBox, delta);
        obstructedCells.put(cell, blocked);
        return blocked;
    }

    private boolean cellObstructed(BlockPos cell, BlockState placed, AABB playerBox, Vec3 delta) {
        AABB box = new AABB(cell);
        if (box.intersects(playerBox)
            || box.intersects(playerBox.move(delta))
            || box.intersects(playerBox.move(delta.scale(-1.0D)))) {
            return true;
        }
        if (placed != null) return !MC.level.isUnobstructed(placed, cell, CollisionContext.empty());
        return !MC.level.getEntities(MC.player, box, EntitySelector.NO_SPECTATORS).isEmpty();
    }

    private static boolean beatsIncumbent(boolean up, float angle, Plan best, float bestAngle) {
        boolean bestUp = best.face() == Direction.UP;
        if (up != bestUp) return up;
        return angle < bestAngle;
    }

    private static void note(StringBuilder trace, Direction face, AutismFaceScan.Refusal reason) {
        if (trace == null || trace.length() >= MAX_TRACE_CHARS) return;
        if (trace.length() > 0) trace.append(' ');
        trace.append(face.getSerializedName().charAt(0)).append(':').append(reason.code());
    }

    private boolean isPlaceableSupport(BlockState state, BlockPos support, ItemStack material,
                                       boolean sneaking) {
        if (state.isAir() || replacedInPlace(state, support, material)) return false;
        return AutismFaceScan.isPlaceableSupport(state, support, sneaking);
    }

    private boolean serverSeesSneak() {
        if (!MC.player.isShiftKeyDown() || !MC.player.getLastSentInput().shift()) return false;
        return !MC.player.getMainHandItem().isEmpty() || !MC.player.getOffhandItem().isEmpty();
    }

    private BlockHitResult wireRay(Plan plan, AutismRotationUtil.Rotation rotation,
                                   boolean sneaking) {
        ItemStack held = MC.player.getItemInHand(InteractionHand.MAIN_HAND);
        AutismFaceScan.Request gate = scanRequest(rotation, held, sneaking, null)
            .cell(plan.cell());
        return AutismFaceScan.confirm(plan.candidate(), rotation, MC.player.getEyePosition(),
            MC.player.blockInteractionRange(), gate);
    }

    private boolean cadenceHolds() {
        return AutismSharedState.get().getClientTickCounter() != lastPlaceTick;
    }

    private void commit(Plan plan, BlockHitResult hit) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (ModuleRegistry.shouldCancelUseExcept(hit, hand, id())) return;

        if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
        try {

            if (!AutismCombatClicker.queueUse(hit, hand)) return;
            if (!AutismPlacementTick.claim(id())) {

                AutismCombatClicker.cancel();
                return;
            }

            bookCadence();

            pendingPlacementCell = plan.cell();
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
    }

    private BlockPos pendingPlacementCell;

    private void tickPendingPlacement() {
        if (pendingPlacementCell == null) return;
        BlockPos cell = pendingPlacementCell;
        pendingPlacementCell = null;

        if (!MC.level.getBlockState(cell).canBeReplaced()) bookPlacement(cell);
    }

    private void bookCadence() {
        lastPlaceTick = AutismSharedState.get().getClientTickCounter();
    }

    @Override
    public boolean shouldCancelUse(net.minecraft.world.phys.HitResult hitResult, InteractionHand hand) {
        return lastPlaceTick == AutismSharedState.get().getClientTickCounter();
    }

    @Override
    public boolean shouldCancelAttack(net.minecraft.world.phys.HitResult hitResult) {
        return hitResult instanceof net.minecraft.world.phys.EntityHitResult
            && id().equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    private void bookPlacement(BlockPos cell) {
        targets.remove(cell);
        sinceProgressTicks = 0;

        BlockPos immutable = cell.immutable();
        placedByUs.remove(immutable);
        placedByUs.add(immutable);
        while (placedByUs.size() > MAX_PLACED_MEMORY) {
            placedByUs.remove(placedByUs.iterator().next());
        }
    }

    private int resolveHotbarSlot() {
        Inventory inventory = MC.player.getInventory();
        int selected = inventory.getSelectedSlot();
        int best = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (AutismHandArbiter.slotReserved(slot, id())) continue;
            ItemStack stack = inventory.getItem(slot);
            if (!isDefenceBlock(stack)) continue;
            if (best < 0
                || outranks(stack, inventory.getItem(best), slot == selected, best == selected)) {
                best = slot;
            }
        }
        return best;
    }

    private static boolean outranks(ItemStack candidate, ItemStack incumbent,
                                    boolean candidateHeld, boolean incumbentHeld) {
        float candidateHardness = hardnessOf(candidate);
        float incumbentHardness = hardnessOf(incumbent);
        boolean candidateUnbreakable = candidateHardness < 0.0F;
        boolean incumbentUnbreakable = incumbentHardness < 0.0F;
        if (candidateUnbreakable != incumbentUnbreakable) return candidateUnbreakable;
        if (candidateHardness != incumbentHardness) return candidateHardness > incumbentHardness;
        if (incumbentHeld) return candidate.getCount() >= incumbent.getCount() + HOTBAR_COUNT_MARGIN;
        if (candidateHeld) return candidate.getCount() + HOTBAR_COUNT_MARGIN > incumbent.getCount();
        return candidate.getCount() > incumbent.getCount();
    }

    private static float hardnessOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
            ? blockItem.getBlock().defaultDestroyTime() : 0.0F;
    }

    private void selectSlot(int slot) {
        if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
        try {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (originalSlot < 0 && bool("switch-back")) originalSlot = selected;
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            switchedToSlot = slot;
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
    }

    private void maybeSwitchBack() {
        if (originalSlot < 0) return;
        if (!bool("switch-back")) {
            originalSlot = -1;
            switchedToSlot = -1;
            return;
        }

        if (switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {
            originalSlot = -1;
            switchedToSlot = -1;
            return;
        }
        if (++idleTicks < SWITCH_BACK_IDLE_TICKS) return;
        if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
        try {
            AutismInventoryHelper.selectHotbarSlot(MC, originalSlot);
            originalSlot = -1;
            switchedToSlot = -1;
            idleTicks = 0;
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
    }

    private boolean isDefenceBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!stack.isItemEnabled(MC.level.enabledFeatures())) return false;
        Block block = blockItem.getBlock();
        boolean listed = filteredBlocks().contains(block);
        if ("Whitelist".equals(choice("filter-mode")) != listed) return false;

        if (block.defaultBlockState().isCollisionShapeFullBlock(MC.level, BlockPos.ZERO)) return true;

        return bool("allow-chests") && block instanceof AbstractChestBlock<?>;
    }

    private Set<Block> filteredBlocks() {
        String raw = value("blocks");
        if (raw.equals(cachedFilterRaw)) return cachedFilterBlocks;
        Set<Block> blocks = new HashSet<>();
        for (String entry : list("blocks")) {
            Identifier identifier = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (identifier == null) continue;
            BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(blocks::add);
        }
        cachedFilterRaw = raw;
        cachedFilterBlocks = Set.copyOf(blocks);
        return cachedFilterBlocks;
    }
}
