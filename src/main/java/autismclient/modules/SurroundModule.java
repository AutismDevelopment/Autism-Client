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
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismPlacementTick;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.RegistryListCodec;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SurroundModule extends Module implements AutismSilentAim.Owner {

    private static final Direction[] DIRECTIONS_EXCLUDING_UP = {
        Direction.DOWN, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH
    };

    private static final Direction[] RING_CYCLE = {
        Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    };

    private static final float BLAST_RESISTANT_AT = 600.0F;

    private static final double XZ_SPEED_LIMIT_SQR = 0.25D;

    private static final int SCAN_RAY_BUDGET = AutismFaceScan.MAX_LADDER_RAYS;

    private static final double FEET_EPSILON = 1.0E-3D;

    private static final double COLUMN_INSET = 1.0E-3D;

    private static final int SWITCH_BACK_IDLE_TICKS = 6;

    private static final int OWNERSHIP_TAIL_TICKS = 5;

    private static final int MAX_TRACKED_BREAKERS = 256;

    private static final long BREAKING_EXPIRY_NANOS = 2_000_000_000L;

    private static final int MAX_PLACED_MEMORY = 64;

    private record Plan(BlockPos cell, AutismFaceScan.Candidate candidate) {

        AutismRotationUtil.Rotation goal() {
            return candidate.aim().goal();
        }
    }

    private record Breaking(long pos, int stage, long stampNanos) {
    }

    private final Map<Integer, Breaking> breaking = new ConcurrentHashMap<>();

    private final Set<BlockPos> broken = new HashSet<>();
    private final List<BlockPos> targets = new ArrayList<>();

    private final Set<BlockPos> placedByUs = new LinkedHashSet<>();

    private boolean armed;
    private double startY;
    private double centerX;
    private double centerZ;
    private double lastX;
    private double lastZ;
    private boolean lastPositionValid;

    private int lastPlaceTick = Integer.MIN_VALUE;

    private int aimTick = Integer.MIN_VALUE;

    private final AutismFaceScan.Budget scanBudget = new AutismFaceScan.Budget(SCAN_RAY_BUDGET);

    private int originalSlot = -1;

    private int switchedToSlot = -1;
    private int idleTicks;

    private final KillAuraModule.TickVerdict throwableVerdict = new KillAuraModule.TickVerdict();

    private String cachedFilterRaw;
    private Set<Block> cachedFilterBlocks = Set.of();

    public SurroundModule() {
        super("surround", "Surround", ModuleCategory.PLAYER, "Places blocks around your feet.");

        add(new BoolSetting("down", "Down", true)
            .group("Features")
            .description("Fill under the surround floor"));

        add(new ChoiceSetting("ring", "Ring", "Center", "Center", "Hitbox")
            .group("Features")
            .description("Ring around center or hitbox"));
        add(new BoolSetting("no-waste", "NoWaste", true)
            .group("Features")
            .visibleWhen(() -> "Hitbox".equals(choice("ring")))
            .description("Skip extra cells inside holes"));

        add(new BoolSetting("disable-on-y-change", "YChange", true)
            .group("DisableOn")
            .description("Turn off on Y change"));
        add(new BoolSetting("disable-on-xz-move", "XZMove", false)
            .group("DisableOn")
            .description("Turn off leaving start block"));
        add(new BoolSetting("disable-on-xz-speed", "XZSpeed", false)
            .group("DisableOn")
            .description("Turn off when moving fast"));

        add(new ChoiceSetting("filter-mode", "Filter", "Whitelist", "Whitelist", "Blacklist")
            .group("Blocks")
            .description("How the list is applied"));
        add(RegistryListSetting.blocks("blocks", "Blocks",
                "minecraft:obsidian|minecraft:crying_obsidian|minecraft:ender_chest")
            .group("Blocks")
            .description("Surround material"));

        add(new IntSetting("aim-speed", "Aim Speed", 3, 1, 5, 1)
            .group("Placing")
            .description("Turn and place speed"));
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("Placing")
            .description("Return to your old slot"));

        add(new BoolSetting("protect", "Protect", true)
            .group("Protect")
            .description("Refill blocks others are mining"));
        add(new IntSetting("min-destroy-progress", "Min Destroy Progress", 4, 0, 9, 1)
            .group("Protect")
            .unit("stage")
            .visibleWhen(() -> bool("protect"))
            .description("Stage to start reacting at"));
        add(new BoolSetting("extra-layer", "Extra Layer", true)
            .group("Protect")
            .visibleWhen(() -> bool("protect"))
            .description("Second ring around mined cells"));
        add(new BoolSetting("extra-layer-corners", "Corners", false)
            .group("Protect")
            .visibleWhen(() -> bool("protect") && bool("extra-layer"))
            .description("Also fill the diagonals"));
        add(new BoolSetting("force-extra-layer", "Force Extra Layer", false)
            .group("Protect")
            .description("Extra layer on every cell"));
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
        armed = false;
        lastPositionValid = false;
        targets.clear();
        placedByUs.clear();
        broken.clear();
        breaking.clear();
        lastPlaceTick = Integer.MIN_VALUE;
        originalSlot = -1;
        switchedToSlot = -1;
        idleTicks = 0;
        AutismHandArbiter.releaseAll(id());
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

        if (isEnabled() || MC == null || MC.player == null) return;
        if (!id().equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.update(id(), MC.player);
    }

    @Override
    public String info() {
        int remaining = targets.size();
        return remaining <= 0 ? "" : Integer.toString(remaining);
    }

    public static boolean ownsSilentRotation() {
        Module module = ModuleRegistry.get(AutismKillAuraRotation.OWNER_SURROUND);
        if (!(module instanceof SurroundModule surround)) return false;

        int age = AutismSharedState.get().getClientTickCounter() - surround.aimTick;
        return age >= 0 && age <= OWNERSHIP_TAIL_TICKS
            && AutismKillAuraRotation.OWNER_SURROUND.equals(AutismKillAuraRotation.currentOwner());
    }

    @Override
    public boolean silentCorrectionApplies() {
        boolean enabled = isEnabled();
        return !AutismSilentAim.scaffoldOwnsRotation()
            && (AutismKillAuraRotation.isWindingDown() || enabled && canRun());
    }

    private boolean canRun() {
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
    public boolean onPacketReceive(Packet<?> packet) {
        if (!(packet instanceof ClientboundBlockDestructionPacket destruction)) return false;
        if (!isEnabled() || !bool("protect")) return false;
        if (MC != null && MC.player != null && destruction.getId() == MC.player.getId()) return false;

        int progress = destruction.getProgress();
        if (progress < 0 || progress > 9) {
            breaking.remove(destruction.getId());
            return false;
        }
        if (breaking.size() >= MAX_TRACKED_BREAKERS && !breaking.containsKey(destruction.getId())) {
            return false;
        }
        breaking.put(destruction.getId(),
            new Breaking(destruction.getPos().asLong(), progress, System.nanoTime()));
        return false;
    }

    private void collectBrokenCells() {
        broken.clear();
        if (!bool("protect")) {
            breaking.clear();
            return;
        }
        long now = System.nanoTime();
        int minStage = integer("min-destroy-progress");
        breaking.values().removeIf(entry -> now - entry.stampNanos() > BREAKING_EXPIRY_NANOS);
        for (Breaking entry : breaking.values()) {
            if (entry.stage() >= minStage) broken.add(BlockPos.of(entry.pos()));
        }
    }

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (MC == null || MC.player == null || MC.level == null || MC.getConnection() == null) {
            targets.clear();
            standDown();
            return;
        }
        if (!armed) captureStartPose();

        tickPendingPlacement();

        double x = MC.player.getX();
        double y = MC.player.getY();
        double z = MC.player.getZ();
        String reason = disableReason(x, y, z);
        lastX = x;
        lastZ = z;
        lastPositionValid = true;
        if (reason != null) {
            disableWithToggleMessage("Surround disabled: " + reason + ".");
            return;
        }

        collectBrokenCells();
        buildTargets();
        runPlacement();
    }

    private void captureStartPose() {
        startY = MC.player.getY();
        BlockPos block = MC.player.blockPosition();
        centerX = block.getX() + 0.5D;
        centerZ = block.getZ() + 0.5D;
        lastPositionValid = false;
        armed = true;
    }

    private String disableReason(double x, double y, double z) {
        if (bool("disable-on-y-change") && y != startY) return "your Y changed";
        if (bool("disable-on-xz-move")
            && (Math.abs(x - centerX) > 0.5D || Math.abs(z - centerZ) > 0.5D)) {
            return "you left the block";
        }
        if (bool("disable-on-xz-speed") && lastPositionValid) {
            double dx = x - lastX;
            double dz = z - lastZ;
            if (dx * dx + dz * dz >= XZ_SPEED_LIMIT_SQR) return "you moved too fast";
        }
        return null;
    }

    private void buildTargets() {
        targets.clear();

        AABB box = MC.player.getBoundingBox();

        int feetY = Mth.floor(box.minY + FEET_EPSILON);
        List<BlockPos> hole = holeColumns(box, feetY);

        Set<BlockPos> seen = new HashSet<>(hole);
        List<BlockPos> ring = new ArrayList<>();
        List<BlockPos> down = new ArrayList<>();
        List<BlockPos> extra = new ArrayList<>();

        boolean addDown = bool("down");
        boolean forceExtra = bool("force-extra-layer");
        boolean extraLayer = bool("protect") && bool("extra-layer");
        boolean corners = extraLayer && bool("extra-layer-corners");

        Direction[] order = walkOrder();
        for (BlockPos holePos : hole) {
            for (Direction direction : order) {
                BlockPos pos = holePos.relative(direction);
                if (!seen.add(pos)) continue;
                if (direction == Direction.DOWN) {
                    down.add(pos);
                    if (addDown) addUnique(down, seen, holePos.below(2));
                    continue;
                }
                ring.add(pos);
                if (!forceExtra && !(extraLayer && broken.contains(pos))) continue;
                addUnique(extra, seen, pos.relative(direction));
                addUnique(extra, seen, pos.above());
                if (corners) addUnique(extra, seen, pos.relative(direction.getClockWise()));
            }
        }

        appendGroup(ring);
        appendGroup(down);
        appendGroup(extra);
    }

    private Direction[] walkOrder() {
        if (integer("aim-speed") <= 1) return DIRECTIONS_EXCLUDING_UP;

        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        float fromYaw = wire.initialized() ? wire.currentYaw() : MC.player.getYRot();
        int start = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < RING_CYCLE.length; i++) {
            float turn = Math.abs(AutismRotationUtil.angleDifference(RING_CYCLE[i].toYRot(), fromYaw));
            if (turn < best) {
                best = turn;
                start = i;
            }
        }
        Direction[] order = new Direction[RING_CYCLE.length + 1];
        order[0] = Direction.DOWN;
        for (int i = 0; i < RING_CYCLE.length; i++) {
            order[i + 1] = RING_CYCLE[(start + i) % RING_CYCLE.length];
        }
        return order;
    }

    private static void addUnique(List<BlockPos> target, Set<BlockPos> seen, BlockPos pos) {
        if (seen.add(pos)) target.add(pos);
    }

    private void appendGroup(List<BlockPos> group) {
        for (BlockPos pos : group) {
            if (placedByUs.contains(pos) && MC.level.getBlockState(pos).canBeReplaced()) {
                targets.add(pos);
            }
        }
        for (BlockPos pos : group) {
            if (!placedByUs.contains(pos) && MC.level.getBlockState(pos).canBeReplaced()) {
                targets.add(pos);
            }
        }
    }

    private List<BlockPos> holeColumns(AABB box, int feetY) {
        BlockPos feet = new BlockPos(
            Mth.floor((box.minX + box.maxX) * 0.5D), feetY, Mth.floor((box.minZ + box.maxZ) * 0.5D));
        if (!"Hitbox".equals(choice("ring"))) return List.of(feet);
        if (bool("no-waste") && isInHole(feet)) return List.of(feet);

        int minColX = Mth.floor(box.minX + COLUMN_INSET);
        int maxColX = Mth.floor(box.maxX - COLUMN_INSET);
        int minColZ = Mth.floor(box.minZ + COLUMN_INSET);
        int maxColZ = Mth.floor(box.maxZ - COLUMN_INSET);
        List<BlockPos> columns = new ArrayList<>(4);
        for (int columnX = minColX; columnX <= maxColX; columnX++) {
            for (int columnZ = minColZ; columnZ <= maxColZ; columnZ++) {
                columns.add(new BlockPos(columnX, feetY, columnZ));
            }
        }

        return columns.isEmpty() ? List.of(feet) : columns;
    }

    private boolean isInHole(BlockPos feet) {
        for (Direction direction : DIRECTIONS_EXCLUDING_UP) {
            float resistance = MC.level.getBlockState(feet.relative(direction))
                .getBlock().getExplosionResistance();
            if (resistance < BLAST_RESISTANT_AT) return false;
        }
        return true;
    }

    private void runPlacement() {

        if (!canRun()) {
            standDown();
            return;
        }
        int slot = resolveHotbarSlot();

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

        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        AutismRotationUtil.Rotation wireRotation = wire.initialized()
            ? new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
            : null;
        AutismRotationUtil.Rotation from = wireRotation != null
            ? wireRotation : AutismRotationUtil.playerRotation(MC.player);

        ItemStack material = MC.player.getInventory().getItem(slot);
        Plan plan = planNextTarget(from, material, null);
        if (plan == null) {

            standDown();
            maybeSwitchBack();
            return;
        }

        idleTicks = 0;

        boolean slotReady = slot == MC.player.getInventory().getSelectedSlot();
        BlockHitResult hit = wireRotation == null ? null : wireRay(plan, wireRotation);

        boolean fire = hit != null && slotReady
            && !BedDefenderModule.ownsSilentRotation()
            && cadenceHolds();

        boolean aimed;
        if (fire) {
            Plan next = planNextTarget(from, material, plan.cell());
            aimed = pumpAim(next != null ? next.goal() : plan.goal(), true);
        } else {
            aimed = pumpAim(plan.goal(), false);
        }

        if (!fire || !aimed) {
            if (!slotReady) selectSlot(slot);
            return;
        }

        AutismRotationUtil.Rotation outgoing = AutismSilentAim.activeOutgoingRotation(MC.player);
        if (outgoing == null || Math.abs(outgoing.pitch() - wireRotation.pitch()) > 0.05F) return;
        commit(plan, hit);
    }

    private boolean pumpAim(AutismRotationUtil.Rotation goal, boolean holdPitch) {
        AutismKillAuraRotation.setTarget(id(), AutismKillAuraRotation.PRIORITY_SURROUND, goal);

        aimTick = AutismSharedState.get().getClientTickCounter();
        if (!id().equals(AutismKillAuraRotation.currentOwner())) return false;
        int speed = integer("aim-speed");

        AutismKillAuraRotation.update(id(), MC.player, turnCap(speed),
            holdPitch ? 0.0F : turnCap(speed), aimProfile(speed));
        return true;
    }

    private static AutismHumanRotation.MotionProfile aimProfile(int speed) {
        return switch (speed) {
            case 2 -> AutismHumanRotation.MotionProfile.SURROUND_FAST_2;
            case 3 -> AutismHumanRotation.MotionProfile.SURROUND_FAST_3;
            case 4 -> AutismHumanRotation.MotionProfile.SURROUND_FAST_4;
            case 5 -> AutismHumanRotation.MotionProfile.SURROUND_FAST_5;
            default -> AutismHumanRotation.MotionProfile.STANDARD;
        };
    }

    private static float turnCap(int speed) {
        return switch (speed) {
            case 3 -> 90.0F;
            case 4 -> 135.0F;
            case 5 -> 180.0F;
            default -> AutismKillAuraRotation.TURN_SPEED;
        };
    }

    private Plan planNextTarget(AutismRotationUtil.Rotation from, ItemStack material, BlockPos skip) {
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        Vec3 eye = MC.player.getEyePosition();
        AABB playerBox = MC.player.getBoundingBox();
        Vec3 delta = MC.player.getDeltaMovement();
        BlockState placed = material.getItem() instanceof BlockItem blockItem
            ? blockItem.getBlock().defaultBlockState() : null;

        AutismFaceScan.Request request = new AutismFaceScan.Request(null, eye, reach,
            AutismFaceScan.blockItem(material, MC.player, InteractionHand.MAIN_HAND))
            .from(from)

            .sneaking(MC.player.isShiftKeyDown())

            .leadEye(eye.add(delta))
            .budget(scanBudget);
        for (BlockPos cell : targets) {
            if (cell.equals(skip)) continue;
            if (cellObstructed(cell, placed, playerBox, delta)) continue;
            scanBudget.reset(SCAN_RAY_BUDGET);
            AutismFaceScan.Candidate candidate = AutismFaceScan.best(request.cell(cell));
            if (candidate != null) return new Plan(cell, candidate);
        }
        return null;
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

    private BlockHitResult wireRay(Plan plan, AutismRotationUtil.Rotation rotation) {
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        Vec3 eye = MC.player.getEyePosition();
        AutismFaceScan.Request gate = new AutismFaceScan.Request(plan.cell(), eye, reach,
            AutismFaceScan.blockItem(MC.player.getItemInHand(InteractionHand.MAIN_HAND),
                MC.player, InteractionHand.MAIN_HAND))
            .sneaking(MC.player.isShiftKeyDown());
        return AutismFaceScan.confirm(plan.candidate(), rotation, eye, reach, gate);
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
            if (!isSurroundBlock(stack)) continue;
            if (slot == selected) return slot;
            if (best < 0 || stack.getCount() > inventory.getItem(best).getCount()) best = slot;
        }
        return best;
    }

    private void selectSlot(int slot) {

        if (BedDefenderModule.ownsSilentRotation()) return;
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

    private boolean isSurroundBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!stack.isItemEnabled(MC.level.enabledFeatures())) return false;
        boolean listed = filteredBlocks().contains(blockItem.getBlock());
        return "Whitelist".equals(choice("filter-mode")) == listed;
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
