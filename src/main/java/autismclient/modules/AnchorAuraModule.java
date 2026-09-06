

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismCombatClicker;
import autismclient.util.AutismExplosionDamage;
import autismclient.util.AutismFaceScan;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismPlacementTick;
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public final class AnchorAuraModule extends Module implements AutismSilentAim.Owner {

    public static final String ID = AutismKillAuraRotation.OWNER_ANCHOR_AURA;

    private static final float POWER = AutismExplosionDamage.RESPAWN_ANCHOR_POWER;

    private static final int MAX_CHARGES = RespawnAnchorBlock.MAX_CHARGES;

    private static final int MAX_AIM_CANDIDATES = 4;

    private static final int MAX_FULL_EVALUATIONS = 32;

    private static final int TARGET_LEAD_TICKS = 6;
    private static final double TARGET_LEAD_MAX = 2.0D;

    private static final int RAY_BUDGET = AutismFaceScan.DEFAULT_TICK_RAY_BUDGET;

    private static final int DETONATE_RETRY_TICKS = 10;

    private static final int CHARGE_RETRY_TICKS = 10;

    private static final int BAN_TICKS = 40;

    private static final int ADOPTION_NO_BUDGET_PARKS = 3;

    private static final int CYCLE_TIMEOUT_TICKS = 20;

    private static final int MAX_PLACE_ATTEMPTS = 3;

    private static final int PENDING_COMMIT_TICKS = 25;

    private static final int PENDING_BAN_TICKS = 10;

    private static final int PENDING_COMMIT_STRIKES = 3;

    private static final int FORCE_ENGAGE_TICKS = 3;

    private static final int SHIELD_FUEL_MINIMUM = 2;

    private static final int SHIELD_ROUTE_CELLS = 4;

    private static final int SHIELD_CELLS_PER_ANCHOR = 4;

    private static int[] sphereCache = new int[0];
    private static int sphereRadius = -1;

    private final Random random = new Random();

    private final Map<BlockPos, Integer> banned = new HashMap<>();

    private LivingEntity target;

    private int reservedTick = Integer.MIN_VALUE;

    private int activityTick = Integer.MIN_VALUE;

    private int lastActionTick = Integer.MIN_VALUE;
    private long lastActionNanos = Long.MIN_VALUE;
    private long actionFloorNanos;

    private String cachedEntityListSource;
    private Set<String> cachedEntityIds = Set.of();

    private int previousSlot = -1;

    private int switchedToSlot = -1;
    private int switchBackTicks;

    private int hotbarChangeTick = Integer.MIN_VALUE;

    private BlockPos cycleCell;
    private int cycleDeadline;
    private int abandonDeadline;
    private boolean abandoning;
    private boolean detonateSent;
    private int detonateTick = Integer.MIN_VALUE;

    private boolean chargeSent;
    private int chargeTick = Integer.MIN_VALUE;

    private int placeAttempts;

    private BlockPos placeCursor;

    private BlockPos budgetParkedCell;
    private int budgetParkStreak;

    private BlockPos pendingCell;
    private int pendingSlot = -1;
    private LivingEntity pendingTarget;
    private int pendingExpiry;

    private int pendingStreak;

    private BlockPos pendingShield;
    private boolean pendingShieldObsidian;

    private BlockPos cycleShield;
    private boolean cycleShieldObsidian;

    private int shieldAttempts;

    private int targetSeenTicks;

    private AutismExplosionDamage.Options damageOptions = AutismExplosionDamage.Options.DEFAULT;

    private final Map<BlockPos, AutismExplosionDamage.Options> cellOptions = new HashMap<>();

    private String status = "";

    public AnchorAuraModule() {
        super(ID, "AnchorAura", ModuleCategory.COMBAT, "Blows targets up with respawn anchors.");

        add(new BoolSetting("place", "Place", true)
            .description("Place new respawn anchors.")
            .build());
        add(new BoolSetting("charge", "Charge", true)
            .description("Charge anchors with glowstone.")
            .build());
        add(new BoolSetting("detonate", "Detonate", true)
            .description("Detonate charged anchors.")
            .build());
        add(new DoubleSetting("target-range", "Target Range", 8.0D, 1.0D, 16.0D, 0.5D)
            .description("Enemy search radius.")
            .build());
        add(new ChoiceSetting("targeting", "Targeting", "Distance", "Distance", "HP", "FOV")
            .description("How the enemy is picked.")
            .build());
        add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
            .description("Entity types to blast.")
            .build());
        add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1)
            .description("Maximum hurt time.")
            .build());

        add(new IntSetting("switch-back-delay", "Switch Back Delay", 20, 1, 100, 1)
            .description("Idle ticks before switching back.")
            .group("Place")
            .build());
        add(new BoolSetting("only-above", "Only Above", false)
            .description("Place above target.")
            .group("Place")
            .build());
        add(new BoolSetting("force-engage", "Force Engage", true)
            .description("Engage anyway when nothing passes the damage gates.")
            .group("Place")
            .build());

        add(new DoubleSetting("min-target-damage", "Min Target Damage", 5.0D, 0.0D, 20.0D, 0.5D)
            .description("Minimum blast damage to target.")
            .group("Damage")
            .build());
        add(new DoubleSetting("max-self-damage", "Max Self Damage", 4.0D, 0.0D, 20.0D, 0.5D)
            .description("Maximum blast damage to self.")
            .group("Damage")
            .build());
        add(new BoolSetting("efficient", "Efficient", true)
            .description("Require efficient trades.")
            .group("Damage")
            .build());
        add(new BoolSetting("terrain", "Terrain", true)
            .description("Model terrain damage.")
            .group("Damage")
            .build());
        add(new BoolSetting("glowstone-shield", "Glowstone Shield", true)
            .description("Shield yourself with glowstone when self damage is too high.")
            .group("Damage")
            .build());

        add(new IntSetting("step-delay", "Step Delay", 75, 0, 1000, 10)
            .description("Floor between two anchor clicks.")
            .unit("ms")
            .group("Timing")
            .build());
        add(new IntSetting("step-jitter", "Step Jitter", 20, 0, 200, 5)
            .description("Random extra delay per click.")
            .unit("ms")
            .group("Timing")
            .build());

    }

    @Override
    public void onEnable() {
        resetRuntime();

    }

    @Override
    public void onDisable() {
        resetRuntime();

        AutismKillAuraRotation.beginWindDown(ID);
    }

    @Override
    public void onGameLeft() {
        resetRuntime();
        if (ID.equals(AutismKillAuraRotation.currentOwner())) AutismKillAuraRotation.reset();
    }

    private void resetRuntime() {
        target = null;
        reservedTick = Integer.MIN_VALUE;
        activityTick = Integer.MIN_VALUE;
        lastActionTick = Integer.MIN_VALUE;
        lastActionNanos = Long.MIN_VALUE;
        actionFloorNanos = 0L;
        previousSlot = -1;
        switchedToSlot = -1;
        switchBackTicks = 0;
        hotbarChangeTick = Integer.MIN_VALUE;
        cycleCell = null;
        abandoning = false;
        detonateSent = false;
        detonateTick = Integer.MIN_VALUE;
        chargeSent = false;
        chargeTick = Integer.MIN_VALUE;
        placeCursor = null;
        budgetParkedCell = null;
        budgetParkStreak = 0;
        cycleShield = null;
        cycleShieldObsidian = false;
        shieldAttempts = 0;
        targetSeenTicks = 0;
        clearPending();
        banned.clear();
        cellOptions.clear();
        status = "";
        AutismHandArbiter.releaseAll(ID);
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return ID.equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    @Override
    public void tick() {

        if (isEnabled() || MC == null || MC.player == null) return;
        if (!ID.equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.update(ID, MC.player);
    }

    @Override
    public String info() {
        if (!status.isEmpty()) return status;
        LivingEntity current = target;
        return current == null ? "" : current.getName().getString();
    }

    public static boolean reservesCombatTick() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AnchorAuraModule aura) || !aura.isEnabled()) return false;

        int age = AutismSharedState.get().getClientTickCounter() - aura.reservedTick;
        return age >= 0 && age <= AutismKillAuraRotation.TICKS_UNTIL_RESET;
    }

    public static boolean worksThisTick() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AnchorAuraModule aura) || !aura.isEnabled()) return false;
        return aura.activityTick == AutismSharedState.get().getClientTickCounter();
    }

    public static boolean holdsBorrowedSlot(int slot) {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AnchorAuraModule aura) || !aura.isEnabled()) return false;
        return aura.previousSlot >= 0 && aura.switchedToSlot == slot;
    }

    public static boolean reservesCycleCell(BlockPos cell) {
        if (cell == null) return false;
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof AnchorAuraModule aura) || !aura.isEnabled()) return false;
        return cell.equals(aura.pendingCell) || cell.equals(aura.cycleCell)
            || cell.equals(aura.pendingShield) || cell.equals(aura.cycleShield);
    }

    @Override
    public boolean silentCorrectionApplies() {
        boolean enabled = isEnabled();
        return !AutismSilentAim.scaffoldOwnsRotation()
            && (AutismKillAuraRotation.isWindingDown() || enabled && canRun());
    }

    private boolean canRun() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.gameMode != null
            && MC.getConnection() != null

            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !MC.player.isDeadOrDying()
            && !MC.player.isSpectator()

            && !MC.player.isSecondaryUseActive()

            && !MC.player.isUsingItem()
            && !MC.player.isHandsBusy()
            && !PackHideState.isActive()
            && !PackFreecamState.isActive()
            && !AutismRemoteView.isActive()
            && !MultiPilot.isActive()
            && !MacroExecutor.isRunning()
            && !PacketTeleportController.ownsMainMovement()

            && !AutismBlinkManager.holdsActionsWithoutMovement()
            && !AutismSilentAim.scaffoldOwnsRotation()

            && !ScaffoldModule.reservesRageInput()

            && !AutoTotemModule.operationActive()
            && !AutoArmorModule.operationActive();

    }

    private static boolean higherRungAiming() {

        return BedDefenderModule.ownsSilentRotation()
            || SurroundModule.ownsSilentRotation()
            || CrystalAuraModule.inPrimePosition() && CrystalAuraModule.reservesCombatTick();
    }

    private boolean finishesCommittedWork(Plan plan) {

        if (plan.step() == Step.DETONATE) return true;

        return plan.step() == Step.CHARGE && abandoning && plan.cell().equals(cycleCell);
    }

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (MC == null || MC.player == null || MC.level == null) return;
        int tick = AutismSharedState.get().getClientTickCounter();

        pruneBans(tick);
        tickSwitchBack();

        tickDeadline(tick);

        if (!canRun()) {
            target = null;

            clearPending();
            standDown();
            return;
        }

        LivingEntity enemy = selectTarget();

        if (enemy == null || enemy != target) targetSeenTicks = 0;
        target = enemy;

        if (enemy == null) clearPending();

        double range = MC.player.blockInteractionRange();
        AutismFaceScan.Budget budget = new AutismFaceScan.Budget(RAY_BUDGET);
        rebuildDamageOptions();

        boolean higherRung = higherRungAiming();

        boolean caPrime = CrystalAuraModule.inPrimePosition();
        boolean caCommitted = caPrime && CrystalAuraModule.hasLiveCommitment();

        if (caPrime) clearPending();

        Plan plan;

        try (AutismExplosionDamage.ScanPass pass = AutismExplosionDamage.beginScan()) {
            plan = planCycle(tick, range, budget);

            if (plan == null && cycleCell == null && enemy != null && !caPrime) {
                plan = planCommittedPlacement(enemy, tick, range, budget, higherRung || caCommitted);
            }
        }

        if (plan == null) {
            standDown();
            return;
        }
        status = plan.step().name().toLowerCase(Locale.ROOT);

        boolean finishing = finishesCommittedWork(plan);
        boolean mayAct = !higherRung && !caCommitted || finishing;
        if (mayAct) reservedTick = tick;

        boolean releasedToDefend = mayAct && !finishing && !cadenceReady()
            && CrystalAuraModule.defendIntends();
        if (mayAct && !releasedToDefend) activityTick = tick;

        AutismHandArbiter.holdHand(ID);

        AutismRotationUtil.Rotation wire = wireRotation();

        boolean allowed = wire != null && cadenceReady() && mayAct;
        boolean slotReady = allowed && ensureSlot(plan.slot(), finishing);

        boolean finishSwitchRefused = finishing && allowed && !slotReady
            && MC.player.getInventory().getSelectedSlot() != plan.slot();
        BlockHitResult hit = slotReady ? gateHit(plan, wire) : null;

        if (mayAct && !releasedToDefend && !finishSwitchRefused) {
            boolean pinCommittedFire = hit != null && (higherRung || caCommitted);
            AutismKillAuraRotation.setTarget(ID,
                pinCommittedFire ? AutismKillAuraRotation.PRIORITY_BED_DEFENDER
                    : AutismKillAuraRotation.PRIORITY_ANCHOR_AURA,
                hit != null ? wire : plan.candidate().aim().goal());

            AutismKillAuraRotation.update(ID, MC.player, hit != null);
        }

        if (hit == null) return;

        AutismRotationUtil.Rotation outgoing = AutismSilentAim.activeOutgoingRotation(MC.player);
        if (outgoing == null || !sameRotation(outgoing, wire)) return;
        execute(plan, hit, tick);
    }

    private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {
        return Math.abs(AutismRotationUtil.angleDifference(first.yaw(), second.yaw())) <= 0.05F
            && Math.abs(first.pitch() - second.pitch()) <= 0.05F;
    }

    private void standDown() {
        status = "";

        if (cycleCell == null) {
            AutismHandArbiter.releaseHand(ID);
            AutismHandArbiter.releaseSlots(ID);
        }

        if (!ID.equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.beginWindDown(ID);
        AutismKillAuraRotation.update(ID, MC.player);
    }

    private AutismRotationUtil.Rotation wireRotation() {
        AutismServerRotationView.WireSnapshot snapshot = AutismServerRotationView.snapshot();
        if (!snapshot.initialized()) return null;
        return new AutismRotationUtil.Rotation(snapshot.currentYaw(), snapshot.currentPitch());
    }

    private AutismRotationUtil.Rotation aimReference() {
        AutismRotationUtil.Rotation wire = wireRotation();
        return wire != null ? wire : AutismRotationUtil.playerRotation(MC.player);
    }

    private boolean cadenceReady() {
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == lastActionTick) return false;
        if (lastActionNanos == Long.MIN_VALUE) return true;
        return System.nanoTime() - lastActionNanos >= actionFloorNanos;
    }

    private void bookAction() {
        lastActionTick = AutismSharedState.get().getClientTickCounter();
        lastActionNanos = System.nanoTime();
        int jitter = integer("step-jitter");
        long extra = jitter > 0 ? random.nextInt(jitter + 1) : 0;
        actionFloorNanos = (integer("step-delay") + extra) * 1_000_000L;
        switchBackTicks = integer("switch-back-delay");
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
        return lastActionTick == AutismSharedState.get().getClientTickCounter();
    }

    @Override
    public boolean shouldCancelAttack(HitResult hitResult) {
        return hitResult instanceof net.minecraft.world.phys.EntityHitResult
            && ID.equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    private enum Step { PLACE, SHIELD, CHARGE, DETONATE }

    private record Plan(BlockPos cell, Step step, int slot, AutismFaceScan.Candidate candidate,
                        double range, BlockPos shield, boolean shieldObsidian) {
    }

    private Plan planCycle(int tick, double range, AutismFaceScan.Budget budget) {
        BlockPos cell = cycleCell;
        if (cell == null) return null;
        BlockState state = MC.level.getBlockState(cell);
        boolean anchor = state.getBlock() instanceof RespawnAnchorBlock;

        if (!anchor) {

            if (detonateSent) {
                finishCycle(false);
                return null;
            }

            if (!bool("place") || abandoning || placeAttempts >= MAX_PLACE_ATTEMPTS) {
                finishCycle(true);
                return null;
            }

            chargeSent = false;
            chargeTick = Integer.MIN_VALUE;
            return placePlan(cell, range, budget);
        }
        int charge = state.getValue(RespawnAnchorBlock.CHARGE);

        if (cycleShield != null && MC.level.getBlockState(cycleShield).canBeReplaced()) {

            if (abandoning || shieldAttempts >= MAX_PLACE_ATTEMPTS
                || !shieldMaterialsPresent(cycleShieldObsidian)) {
                cycleShield = null;
                cycleShieldObsidian = false;
            } else {
                Plan shield = shieldPlan(cycleShield, range, budget);

                if (charge <= 0 || shield != null && !selfSafe(cell)) {
                    return shield;
                }
                cycleShield = null;
                cycleShieldObsidian = false;
            }
        }
        if (charge <= 0) {

            if (!bool("charge")) return null;

            if (chargeSent && tick - chargeTick < CHARGE_RETRY_TICKS) return null;
            return usePlan(cell, Step.CHARGE, range, budget);
        }
        if (!bool("detonate") || !detonateReady(cell, state)) {

            return null;
        }

        if (detonateSent && tick - detonateTick < DETONATE_RETRY_TICKS) return null;
        return usePlan(cell, Step.DETONATE, range, budget);
    }

    private Plan planNewCycle(LivingEntity enemy, int tick, double range,
                              AutismFaceScan.Budget budget) {

        BlockPos adopted = findAdoptable(enemy, range);
        if (adopted != null) {
            BlockState state = MC.level.getBlockState(adopted);
            Step step = state.getValue(RespawnAnchorBlock.CHARGE) > 0 ? Step.DETONATE : Step.CHARGE;
            boolean usable = step == Step.DETONATE
                ? bool("detonate") && detonateReady(adopted, state)

                : chargedCycleCanFire();
            boolean refused = true;
            if (usable) {
                AutismFaceScan.Refusal[] outcome = new AutismFaceScan.Refusal[1];
                Plan plan = usePlan(adopted, step, range, budget, outcome);
                if (plan != null) {
                    beginCycle(adopted, tick, null, false);
                    return plan;
                }

                refused = outcome[0] != AutismFaceScan.Refusal.NO_BUDGET || budgetParkBans(adopted);
            }

            if (refused) banned.put(adopted, tick + BAN_TICKS);
        }
        if (!bool("place")) return null;

        if (!chargedCycleCanFire()) return null;
        List<BlockPos> cells = rankCells(enemy, range);
        if (cells.isEmpty()) {

            if (bool("glowstone-shield")) {

                Plan shielded = planShieldRoute(enemy, range, budget);
                if (shielded != null) return shielded;
            }

            if (targetSeenTicks < FORCE_ENGAGE_TICKS) targetSeenTicks++;
            if (bool("force-engage") && targetSeenTicks >= FORCE_ENGAGE_TICKS) {
                cells = rankForceEngageCells(enemy, range);
            }
        } else {

            targetSeenTicks = 0;
        }
        if (cells.isEmpty()) return null;

        AutismFaceScan.Refusal[] outcome = new AutismFaceScan.Refusal[1];
        int start = placeCursorIndex(cells);
        for (int step = 0; step < cells.size(); step++) {
            int index = start + step;
            if (index >= cells.size()) index -= cells.size();
            BlockPos cell = cells.get(index);

            outcome[0] = null;
            Plan plan = placePlan(cell, null, false, range, budget, outcome);

            if (plan != null) {

                placeCursor = cell;
                return plan;
            }
            if (outcome[0] == AutismFaceScan.Refusal.NO_BUDGET) {

                placeCursor = cell;
                return null;
            }
            placeCursor = cells.get(index + 1 == cells.size() ? 0 : index + 1);
        }
        return null;
    }

    private int placeCursorIndex(List<BlockPos> cells) {
        if (placeCursor == null) return 0;

        int index = cells.indexOf(placeCursor);
        return index < 0 ? 0 : index;
    }

    private boolean budgetParkBans(BlockPos cell) {
        if (cell.equals(budgetParkedCell)) {
            budgetParkStreak++;
        } else {
            budgetParkedCell = cell.immutable();
            budgetParkStreak = 1;
        }
        if (budgetParkStreak < ADOPTION_NO_BUDGET_PARKS) return false;
        budgetParkedCell = null;
        budgetParkStreak = 0;
        return true;
    }

    private Plan planCommittedPlacement(LivingEntity enemy, int tick, double range,
                                        AutismFaceScan.Budget budget, boolean hotbarOwnedAbove) {
        if (pendingCell != null) {
            boolean valid = bool("place")
                && enemy == pendingTarget
                && isAnchorItem(MC.player.getInventory().getItem(pendingSlot))
                && !AutismHandArbiter.slotReserved(pendingSlot, ID)
                && placeable(Blocks.RESPAWN_ANCHOR.defaultBlockState(), pendingCell, nextTickBox())

                && (pendingShield == null
                    || shieldMaterialsPresent(pendingShieldObsidian)
                        && placeable(shieldBlock(pendingShieldObsidian), pendingShield, nextTickBox()));
            if (valid) {

                if (hotbarOwnedAbove) pendingExpiry = tick + PENDING_COMMIT_TICKS;
                if (tick - pendingExpiry < 0) {

                    Plan held = placePlan(pendingCell, pendingShield, pendingShieldObsidian, range,
                        budget, null);
                    if (held != null) {
                        pendingStreak = 0;
                        return held;
                    }
                    if (++pendingStreak < PENDING_COMMIT_STRIKES) return null;
                }

                banned.put(pendingCell, tick + PENDING_BAN_TICKS);
            }
            clearPending();
        }
        Plan plan = planNewCycle(enemy, tick, range, budget);
        if (plan != null && plan.step() == Step.PLACE) {
            pendingCell = plan.cell();
            pendingShield = plan.shield();
            pendingShieldObsidian = plan.shieldObsidian();
            pendingSlot = plan.slot();
            pendingTarget = enemy;
            pendingExpiry = tick + PENDING_COMMIT_TICKS;
        }
        return plan;
    }

    private void clearPending() {
        pendingCell = null;
        pendingSlot = -1;
        pendingTarget = null;
        pendingExpiry = 0;
        pendingStreak = 0;
        pendingShield = null;
        pendingShieldObsidian = false;
    }

    private boolean chargedCycleCanFire() {

        if (!bool("charge") || !bool("detonate")) return false;

        if (findHotbarSlot(AnchorAuraModule::isFuel) < 0) return false;

        if (isFuel(MC.player.getOffhandItem())) return false;

        return triggerSlot() >= 0;
    }

    private void beginCycle(BlockPos cell, int tick, BlockPos shield, boolean shieldObsidian) {

        clearPending();
        cycleCell = cell.immutable();
        cycleShield = shield;
        cycleShieldObsidian = shieldObsidian;
        shieldAttempts = 0;
        cycleDeadline = tick + CYCLE_TIMEOUT_TICKS;
        abandoning = false;
        detonateSent = false;
        detonateTick = Integer.MIN_VALUE;
        chargeSent = false;
        chargeTick = Integer.MIN_VALUE;
        placeAttempts = 0;

        targetSeenTicks = 0;

        placeCursor = null;
    }

    private void tickDeadline(int tick) {
        if (cycleCell == null) return;
        if (!abandoning) {

            if (tick - cycleDeadline <= 0) return;
            abandoning = true;
            abandonDeadline = tick + CYCLE_TIMEOUT_TICKS;

            return;
        }
        if (tick - abandonDeadline > 0) finishCycle(true);
    }

    private void finishCycle(boolean ban) {
        BlockPos cell = cycleCell;
        if (cell != null && ban) {
            banned.put(cell, AutismSharedState.get().getClientTickCounter() + BAN_TICKS);
        }
        cycleCell = null;
        cycleShield = null;
        cycleShieldObsidian = false;
        shieldAttempts = 0;
        abandoning = false;
        detonateSent = false;
        detonateTick = Integer.MIN_VALUE;
        chargeSent = false;
        chargeTick = Integer.MIN_VALUE;
        AutismHandArbiter.releaseSlots(ID);
        AutismHandArbiter.releaseHand(ID);
    }

    private void pruneBans(int tick) {
        if (banned.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Integer>> entries = banned.entrySet().iterator();
        while (entries.hasNext()) {

            if (tick - entries.next().getValue() >= 0) entries.remove();
        }
    }

    private Plan placePlan(BlockPos cell, double range, AutismFaceScan.Budget budget) {
        return placePlan(cell, null, false, range, budget, null);
    }

    private Plan placePlan(BlockPos cell, BlockPos shield, boolean shieldObsidian, double range,
                           AutismFaceScan.Budget budget, AutismFaceScan.Refusal[] outcome) {

        if (!placeable(Blocks.RESPAWN_ANCHOR.defaultBlockState(), cell, nextTickBox())) return null;
        int slot = findHotbarSlot(AnchorAuraModule::isAnchorItem);
        if (slot < 0) return null;

        ItemStack material = MC.player.getInventory().getItem(slot);
        AutismFaceScan.Candidate candidate =
            AutismFaceScan.best(placeRequest(cell, material, range).budget(budget), outcome);
        return candidate == null ? null : new Plan(cell, Step.PLACE, slot, candidate, range, shield,
            shieldObsidian);
    }

    private Plan usePlan(BlockPos cell, Step step, double range, AutismFaceScan.Budget budget) {
        return usePlan(cell, step, range, budget, null);
    }

    private Plan usePlan(BlockPos cell, Step step, double range, AutismFaceScan.Budget budget,
                         AutismFaceScan.Refusal[] outcome) {
        int slot = step == Step.CHARGE ? findHotbarSlot(AnchorAuraModule::isFuel) : triggerSlot();
        if (slot < 0) return null;
        AutismFaceScan.Candidate candidate = useCandidate(cell, range, budget, outcome);
        return candidate == null ? null : new Plan(cell, step, slot, candidate, range, null, false);
    }

    private Plan shieldPlan(BlockPos cell, double range, AutismFaceScan.Budget budget) {
        boolean obsidian = cycleShieldObsidian;
        if (!placeable(shieldBlock(obsidian), cell, nextTickBox())) return null;
        Predicate<ItemStack> material = obsidian ? AnchorAuraModule::isObsidian
            : AnchorAuraModule::isFuel;
        int slot = findHotbarSlot(material);
        if (slot < 0) return null;
        ItemStack stack = MC.player.getInventory().getItem(slot);
        AutismFaceScan.Candidate candidate = AutismFaceScan.best(
            placeRequest(cell, stack, range, shieldPlacement(cycleCell, stack)).budget(budget));
        return candidate == null ? null
            : new Plan(cell, Step.SHIELD, slot, candidate, range, null, obsidian);
    }

    private AutismFaceScan.Request placeRequest(BlockPos cell, ItemStack stack, double range) {
        return placeRequest(cell, stack, range,
            AutismFaceScan.blockItem(stack, MC.player, InteractionHand.MAIN_HAND));
    }

    private AutismFaceScan.Request placeRequest(BlockPos cell, ItemStack stack, double range,
                                                AutismFaceScan.Placement placement) {
        LocalPlayer player = MC.player;
        AutismFaceScan.Request request = new AutismFaceScan.Request(cell, player.getEyePosition(),
            range, placement)
            .from(aimReference())
            .pitchLimit(AutismFaceScan.goalPitchLimit())

            .leadEye(player.getEyePosition().add(player.getDeltaMovement()))

            .sneaking(player.isSecondaryUseActive())
            .sneakAllowed(false);

        return request;
    }

    private AutismFaceScan.Request useRequest(BlockPos anchor, double range) {
        LocalPlayer player = MC.player;
        return new AutismFaceScan.Request(anchor, player.getEyePosition(), range, new AnchorClick(anchor))
            .from(aimReference())
            .pitchLimit(AutismFaceScan.goalPitchLimit())

            .leadEye(player.getEyePosition().add(player.getDeltaMovement()))
            .sneaking(player.isSecondaryUseActive())
            .sneakAllowed(false);
    }

    private record AnchorClick(BlockPos anchor) implements AutismFaceScan.Placement {
        @Override
        public boolean lands(BlockHitResult hit, BlockPos cell) {
            return hit != null && anchor.equals(hit.getBlockPos());
        }

        @Override
        public boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
            return anchor.equals(pos) && state.getBlock() instanceof RespawnAnchorBlock;
        }
    }

    private AutismFaceScan.Placement shieldPlacement(BlockPos anchor, ItemStack held) {
        LocalPlayer player = MC.player;
        return new AutismFaceScan.Placement() {
            @Override
            public boolean lands(BlockHitResult hit, BlockPos cell) {
                if (hit == null || cell == null || !(held.getItem() instanceof BlockItem)) {
                    return false;
                }

                BlockPlaceContext context =
                    new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
                return context.canPlace() && context.getClickedPos().equals(cell);
            }

            @Override
            public boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
                if (state.getBlock() instanceof RespawnAnchorBlock) {
                    return anchor != null && anchor.equals(pos) && isObsidian(held)
                        && state.getValue(RespawnAnchorBlock.CHARGE) <= 0;
                }
                return !AutismFaceScan.useActionEatsClick(state, pos, sneaking);
            }
        };
    }

    private AutismFaceScan.Candidate useCandidate(BlockPos anchor, double range,
                                                  AutismFaceScan.Budget budget,
                                                  AutismFaceScan.Refusal[] outcome) {
        BlockState state = MC.level.getBlockState(anchor);
        if (!(state.getBlock() instanceof RespawnAnchorBlock)) return null;
        AutismFaceScan.Request request = useRequest(anchor, range).budget(budget);
        Vec3 eye = MC.player.getEyePosition();

        AutismFaceScan.Refusal[] refusal = outcome != null ? outcome : new AutismFaceScan.Refusal[1];
        for (Direction face : AutismFaceScan.FACE_ORDER_UP_FIRST) {
            for (AABB rect : AutismFaceScan.faceRects(state, anchor, face,
                AutismFaceScan.DEFAULT_RECTS_PER_FACE)) {
                AutismFaceScan.Option option = new AutismFaceScan.Option(anchor, anchor, face, 1, state,
                    rect, faceArea(rect, face), AutismFaceScan.edgeConfidence(rect, face, eye), false,
                    new AutismFaceScan.Intent(anchor, face));
                AutismFaceScan.Aim aim = AutismFaceScan.solve(option, request, refusal);
                if (aim == null) continue;
                AutismFaceScan.Candidate candidate = AutismFaceScan.probe(option, aim, request, refusal);
                if (candidate != null) return candidate;

                if (refusal[0] == AutismFaceScan.Refusal.NO_BUDGET) return null;
            }
        }
        return null;
    }

    private static double faceArea(AABB rect, Direction face) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis first = AutismFaceScan.inPlaneAxis(normal, true);
        Direction.Axis second = AutismFaceScan.inPlaneAxis(normal, false);
        return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
    }

    private BlockHitResult gateHit(Plan plan, AutismRotationUtil.Rotation wire) {
        LocalPlayer player = MC.player;
        AutismFaceScan.Request gate = switch (plan.step()) {

            case PLACE -> placeRequest(plan.cell(), player.getMainHandItem(), plan.range());

            case SHIELD -> placeRequest(plan.cell(), player.getMainHandItem(), plan.range(),
                shieldPlacement(cycleCell, player.getMainHandItem()));
            case CHARGE, DETONATE -> useRequest(plan.cell(), plan.range());
        };
        return AutismFaceScan.confirm(plan.candidate(), wire, player.getEyePosition(), plan.range(), gate);
    }

    private void execute(Plan plan, BlockHitResult hit, int tick) {

        if (!stepStillLegal(plan)) return;

        if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, ID)) return;
        if (!AutismCombatClicker.queueUse(hit, InteractionHand.MAIN_HAND)) return;

        if (!AutismPlacementTick.claim(ID)) {

            AutismCombatClicker.cancel();
            return;
        }

        bookAction();
        if (plan.step() == Step.PLACE) {

            if (cycleCell == null || !cycleCell.equals(plan.cell())) {
                beginCycle(plan.cell(), tick, plan.shield(), plan.shieldObsidian());
            }
            placeAttempts++;
        }

        if (plan.step() == Step.SHIELD) shieldAttempts++;

        if (plan.step() == Step.CHARGE) {
            chargeSent = true;
            chargeTick = tick;
        }
        if (plan.step() == Step.DETONATE) {
            detonateSent = true;
            detonateTick = tick;
        }
    }

    private boolean stepStillLegal(Plan plan) {
        BlockState state = MC.level.getBlockState(plan.cell());
        boolean anchor = state.getBlock() instanceof RespawnAnchorBlock;
        return switch (plan.step()) {
            case PLACE -> !anchor && isAnchorItem(MC.player.getMainHandItem());

            case SHIELD -> state.canBeReplaced()
                && (plan.shieldObsidian() ? isObsidian(MC.player.getMainHandItem())
                    : isFuel(MC.player.getMainHandItem()));

            case CHARGE -> anchor && state.getValue(RespawnAnchorBlock.CHARGE) < MAX_CHARGES
                && isFuel(MC.player.getMainHandItem());

            case DETONATE -> anchor && !isFuel(MC.player.getMainHandItem())
                && detonateReady(plan.cell(), state);
        };
    }

    private boolean detonateReady(BlockPos cell, BlockState state) {
        if (!(state.getBlock() instanceof RespawnAnchorBlock)) return false;
        int charge = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charge <= 0) return false;
        if (isFuel(MC.player.getOffhandItem()) && charge < MAX_CHARGES) return false;
        if (isFuel(MC.player.getMainHandItem()) && triggerSlot() < 0) return false;
        if (!anchorExplodes(cell)) return false;
        return selfSafe(cell);
    }

    private boolean anchorExplodes(BlockPos pos) {
        Boolean works = MC.level.environmentAttributes()
            .getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
        return works == null || !works;
    }

    private LivingEntity selectTarget() {
        LocalPlayer player = MC.player;
        double range = decimal("target-range");
        AABB search = player.getBoundingBox().inflate(range);
        List<LivingEntity> found = MC.level.getEntitiesOfClass(LivingEntity.class, search,
            entity -> entity != player
                && entity.isAlive()
                && !entity.isSpectator()
                && matchesEntity(entity)
                && !AutismAntiBot.suppress(entity)

                && !TeamsModule.combatExcluded(entity, "killaura")
                && AutismExplosionDamage.effectiveHealth(entity) > 0.0D

                && entity.hurtTime <= integer("hurt-time"));
        if (found.isEmpty()) return null;

        Vec3 eye = player.getEyePosition();
        double rangeSq = range * range;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : found) {
            double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
            if (distanceSq > rangeSq) continue;
            double score = switch (choice("targeting")) {
                case "HP" -> AutismExplosionDamage.effectiveHealth(entity);
                case "FOV" -> AutismRotationUtil.rotationAngleTo(
                    AutismRotationUtil.playerRotation(player),
                    AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eye));
                default -> distanceSq;
            };
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    private boolean matchesEntity(Entity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
        Set<String> ids = cachedEntityIds();
        int separator = id.indexOf(':');
        return ids.contains(id) || separator >= 0 && ids.contains(id.substring(separator + 1));
    }

    private Set<String> cachedEntityIds() {
        List<String> entries = list("entities");
        String source = String.join("|", entries);
        if (source.equals(cachedEntityListSource)) return cachedEntityIds;
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            normalized.add(value);
            int separator = value.indexOf(':');
            if (separator >= 0 && separator + 1 < value.length()) normalized.add(value.substring(separator + 1));
        }
        cachedEntityListSource = source;
        cachedEntityIds = Set.copyOf(normalized);
        return cachedEntityIds;
    }

    private List<BlockPos> rankCells(LivingEntity enemy, double range) {
        LocalPlayer player = MC.player;
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));
        BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();

        double damageFloor = decimal("min-target-damage");
        double rangeSq = range * range;
        AABB nextBox = nextTickBox();

        AABB lead = leadBox(enemy);

        List<Candidate> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (banned.containsKey(cursor)) continue;
            if (!anchorExplodes(cursor)) continue;
            if (!placeable(anchor, cursor, nextBox)) continue;

            Vec3 centre = centreOf(cursor);

            if (bool("only-above") && centre.y < enemy.getY()) continue;
            double distanceSq = eye.distanceToSqr(centre);
            if (distanceSq > rangeSq) continue;

            double bound = AutismExplosionDamage.maxDamageTo(enemy, centre, POWER,
                optionsFor(cursor).withOverrideBox(lead));
            if (bound < damageFloor) continue;
            candidates.add(new Candidate(cursor.immutable(), centre, bound, distanceSq));
        }
        if (candidates.isEmpty()) return List.of();
        candidates.sort(Comparator.<Candidate>comparingDouble(Candidate::bound).reversed());

        List<Scored> accepted = new ArrayList<>();
        double bestDamage = -1.0D;
        int evaluated = 0;
        for (Candidate candidate : candidates) {
            if (accepted.size() >= MAX_AIM_CANDIDATES && candidate.bound() <= bestDamage) break;
            if (evaluated >= MAX_FULL_EVALUATIONS) break;
            evaluated++;

            AutismExplosionDamage.Ranking rank = rankAtLead(enemy, candidate.centre(),
                optionsFor(candidate.pos()), lead);
            if (!damagePasses(rank)) continue;
            accepted.add(new Scored(candidate.pos(), rank, candidate.distanceSq()));
            bestDamage = Math.max(bestDamage, rank.targetDamage());
        }
        if (accepted.isEmpty()) return List.of();

        accepted.sort((first, second) -> {
            int byPreference = comparePreference(first.rank(), second.rank());
            if (byPreference != 0) return byPreference;

            return Double.compare(second.distanceSq(), first.distanceSq());
        });

        List<BlockPos> result = new ArrayList<>(Math.min(MAX_AIM_CANDIDATES, accepted.size()));
        for (Scored scored : accepted) {
            if (result.size() >= MAX_AIM_CANDIDATES) break;
            result.add(scored.pos());
        }
        return result;
    }

    private List<BlockPos> rankForceEngageCells(LivingEntity enemy, double range) {
        LocalPlayer player = MC.player;
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));
        BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
        double rangeSq = range * range;
        AABB nextBox = nextTickBox();

        AABB enemyRing = enemy.getBoundingBox().inflate(1.0D);
        int feetY = Mth.floor(enemy.getY());

        List<Scored> accepted = new ArrayList<>();
        int evaluated = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (banned.containsKey(cursor)) continue;
            if (!anchorExplodes(cursor)) continue;
            if (!placeable(anchor, cursor, nextBox)) continue;
            Vec3 centre = centreOf(cursor);
            double distanceSq = eye.distanceToSqr(centre);
            if (distanceSq > rangeSq) continue;
            if (!new AABB(cursor).intersects(enemyRing)) continue;
            if (evaluated >= MAX_FULL_EVALUATIONS) break;
            evaluated++;
            AutismExplosionDamage.Ranking rank = AutismExplosionDamage.cachedRank(enemy, centre,
                POWER, optionsFor(cursor));

            if (!damagePasses(rank)) continue;
            accepted.add(new Scored(cursor.immutable(), rank, distanceSq));
        }
        if (accepted.isEmpty()) return List.of();

        accepted.sort((first, second) -> {

            boolean firstLow = first.pos().getY() <= feetY;
            boolean secondLow = second.pos().getY() <= feetY;
            if (firstLow != secondLow) return firstLow ? -1 : 1;
            int byPreference = comparePreference(first.rank(), second.rank());
            if (byPreference != 0) return byPreference;
            return Double.compare(second.distanceSq(), first.distanceSq());
        });

        List<BlockPos> result = new ArrayList<>(Math.min(MAX_AIM_CANDIDATES, accepted.size()));
        for (Scored scored : accepted) {
            if (result.size() >= MAX_AIM_CANDIDATES) break;
            result.add(scored.pos());
        }
        return result;
    }

    private Plan planShieldRoute(LivingEntity enemy, double range, AutismFaceScan.Budget budget) {

        if (!shieldMaterialsPresent(false) && !shieldMaterialsPresent(true)) return null;
        LocalPlayer player = MC.player;
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));
        BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
        double damageFloor = decimal("min-target-damage");
        double selfCeiling = decimal("max-self-damage");
        double rangeSq = range * range;
        AABB nextBox = nextTickBox();

        List<Scored> needsShield = new ArrayList<>();
        int evaluated = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (banned.containsKey(cursor)) continue;
            if (!anchorExplodes(cursor)) continue;
            if (!placeable(anchor, cursor, nextBox)) continue;
            Vec3 centre = centreOf(cursor);
            if (bool("only-above") && centre.y < enemy.getY()) continue;
            double distanceSq = eye.distanceToSqr(centre);
            if (distanceSq > rangeSq) continue;

            if (AutismExplosionDamage.maxDamageTo(enemy, centre, POWER, optionsFor(cursor))
                < damageFloor) continue;
            if (evaluated >= MAX_FULL_EVALUATIONS) break;
            evaluated++;
            AutismExplosionDamage.Ranking rank = AutismExplosionDamage.cachedRank(enemy, centre,
                POWER, optionsFor(cursor));

            if (rank.targetDamage() < damageFloor) continue;
            if (rank.selfDamage() <= selfCeiling) continue;
            if (bool("efficient") && !rank.isEfficient()) continue;
            needsShield.add(new Scored(cursor.immutable(), rank, distanceSq));
        }
        if (needsShield.isEmpty()) return null;

        needsShield.sort((first, second) -> {
            int byPreference = comparePreference(first.rank(), second.rank());
            if (byPreference != 0) return byPreference;
            return Double.compare(second.distanceSq(), first.distanceSq());
        });

        int tried = 0;
        for (Scored scored : needsShield) {
            if (tried >= SHIELD_ROUTE_CELLS) break;
            tried++;
            BlockPos shield = verifyShield(enemy, scored.pos(), damageFloor, selfCeiling);
            if (shield == null) continue;

            boolean obsidian;
            if (shieldMaterialsPresent(false) && glowstoneSafeSupport(shield)) {
                obsidian = false;
            } else if (shieldMaterialsPresent(true) && obsidianSupport(shield, scored.pos())) {
                obsidian = true;
            } else {
                continue;
            }
            return placePlan(scored.pos(), shield, obsidian, range, budget, null);
        }
        return null;
    }

    private BlockPos verifyShield(LivingEntity enemy, BlockPos anchorCell, double damageFloor,
                                  double selfCeiling) {
        Vec3 centre = centreOf(anchorCell);
        for (BlockPos shieldCell : shieldCandidates(anchorCell)) {
            AutismExplosionDamage.Options verify = optionsFor(anchorCell).withInclude(shieldCell);
            AutismExplosionDamage.Ranking rank = AutismExplosionDamage.cachedRank(enemy, centre,
                POWER, verify);

            if (AutismExplosionDamage.killsSelf(rank.selfDamage())) continue;
            if (rank.selfDamage() > selfCeiling) continue;
            if (rank.targetDamage() < damageFloor) continue;
            return shieldCell;
        }
        return null;
    }

    private List<BlockPos> shieldCandidates(BlockPos anchorCell) {
        LocalPlayer player = MC.player;
        AABB box = player.getBoundingBox();
        AABB nextBox = nextTickBox();
        AABB ring = box.inflate(1.0D);
        Vec3 playerCentre = box.getCenter();
        Vec3 toAnchor = centreOf(anchorCell).subtract(playerCentre).normalize();

        List<BlockPos> cells = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(ring.minX); x <= Mth.floor(ring.maxX); x++) {
            for (int y = Mth.floor(ring.minY); y <= Mth.floor(ring.maxY); y++) {
                for (int z = Mth.floor(ring.minZ); z <= Mth.floor(ring.maxZ); z++) {
                    cursor.set(x, y, z);
                    if (cursor.equals(anchorCell)) continue;
                    AABB cellBox = new AABB(cursor);
                    if (!cellBox.intersects(ring)) continue;

                    if (cellBox.intersects(box) || cellBox.intersects(nextBox)) continue;

                    if (centreOf(cursor).subtract(playerCentre).dot(toAnchor) <= 0.0D) continue;
                    if (!placeable(Blocks.GLOWSTONE.defaultBlockState(), cursor, nextBox)) continue;
                    cells.add(cursor.immutable());
                }
            }
        }

        cells.sort((first, second) -> {
            boolean firstGround = groundSupported(first);
            boolean secondGround = groundSupported(second);
            if (firstGround != secondGround) return firstGround ? -1 : 1;
            return Double.compare(
                centreOf(second).subtract(playerCentre).normalize().dot(toAnchor),
                centreOf(first).subtract(playerCentre).normalize().dot(toAnchor));
        });
        return cells.size() <= SHIELD_CELLS_PER_ANCHOR ? cells
            : new ArrayList<>(cells.subList(0, SHIELD_CELLS_PER_ANCHOR));
    }

    private boolean groundSupported(BlockPos cell) {
        BlockPos below = cell.below();
        return AutismFaceScan.isPlaceableSupport(MC.level.getBlockState(below), below, false);
    }

    private boolean glowstoneSafeSupport(BlockPos cell) {
        for (Direction face : Direction.values()) {
            BlockPos support = cell.relative(face);
            if (AutismFaceScan.isPlaceableSupport(MC.level.getBlockState(support), support, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean obsidianSupport(BlockPos cell, BlockPos anchorCell) {
        return glowstoneSafeSupport(cell) || cell.distManhattan(anchorCell) == 1;
    }

    private record Candidate(BlockPos pos, Vec3 centre, double bound, double distanceSq) {
    }

    private record Scored(BlockPos pos, AutismExplosionDamage.Ranking rank, double distanceSq) {
    }

    private AABB nextTickBox() {
        return MC.player.getBoundingBox().move(MC.player.getDeltaMovement());
    }

    private boolean placeable(BlockState anchor, BlockPos cell, AABB nextBox) {

        if (CrystalAuraModule.reservesPlacementCell(cell)) return false;

        if (CrystalAuraModule.reservesCommittedCell(cell)) return false;
        if (!MC.level.getBlockState(cell).canBeReplaced()) return false;
        if (!anchor.canSurvive(MC.level, cell)) return false;
        if (!MC.level.isUnobstructed(anchor, cell, CollisionContext.placementContext(MC.player))) return false;

        return !nextBox.intersects(new AABB(cell));
    }

    private BlockPos findAdoptable(LivingEntity enemy, double range) {
        LocalPlayer player = MC.player;
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));
        double rangeSq = range * range;

        BlockPos best = null;
        AutismExplosionDamage.Ranking bestRank = null;
        double bestDistanceSq = 0.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (banned.containsKey(cursor)) continue;
            BlockState state = MC.level.getBlockState(cursor);
            if (!(state.getBlock() instanceof RespawnAnchorBlock)) continue;
            if (!anchorExplodes(cursor)) continue;
            Vec3 centre = centreOf(cursor);
            double distanceSq = eye.distanceToSqr(centre);
            if (distanceSq > rangeSq) continue;
            AutismExplosionDamage.Ranking rank = AutismExplosionDamage.cachedRank(enemy, centre, POWER,
                optionsFor(cursor));
            if (!damagePasses(rank)) continue;

            int preference = bestRank == null ? -1 : comparePreference(rank, bestRank);
            if (preference < 0 || preference == 0 && distanceSq > bestDistanceSq) {
                best = cursor.immutable();
                bestRank = rank;
                bestDistanceSq = distanceSq;
            }
        }
        return best;
    }

    private static boolean isAnchorItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.RESPAWN_ANCHOR;
    }

    private static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.GLOWSTONE;
    }

    private static boolean isObsidian(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.OBSIDIAN;
    }

    private static BlockState shieldBlock(boolean obsidian) {
        return (obsidian ? Blocks.OBSIDIAN : Blocks.GLOWSTONE).defaultBlockState();
    }

    private boolean shieldMaterialsPresent(boolean obsidian) {
        if (!obsidian) return hotbarFuelCount() >= SHIELD_FUEL_MINIMUM;
        return findHotbarSlot(AnchorAuraModule::isFuel) >= 0
            && findHotbarSlot(AnchorAuraModule::isObsidian) >= 0;
    }

    private int hotbarFuelCount() {
        LocalPlayer player = MC.player;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isFuel(stack)) count += stack.getCount();
        }
        return count;
    }

    private int triggerSlot() {
        LocalPlayer player = MC.player;
        int selected = player.getInventory().getSelectedSlot();
        if (!isFuel(player.getMainHandItem()) && !AutismHandArbiter.slotReserved(selected, ID)) {
            return selected;
        }
        int anchor = findHotbarSlot(AnchorAuraModule::isAnchorItem);
        return anchor >= 0 ? anchor : findHotbarSlot(stack -> !isFuel(stack));
    }

    private boolean ensureSlot(int slot, boolean finishingWork) {
        if (slot < 0) return true;
        LocalPlayer player = MC.player;

        if (!AutismHandArbiter.reserveSlot(ID, slot)) return false;
        int held = player.getInventory().getSelectedSlot();
        if (held == slot) {

            switchBackTicks = integer("switch-back-delay");
            return true;
        }
        if (changeHotbarSlot(slot, finishingWork)) {
            if (previousSlot < 0) previousSlot = held;
            switchedToSlot = slot;
        }
        switchBackTicks = integer("switch-back-delay");
        return false;
    }

    private boolean changeHotbarSlot(int slot, boolean finishingWork) {

        if (BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()) {
            return false;
        }

        if (!finishingWork && CrystalAuraModule.inPrimePosition()
            && (CrystalAuraModule.reservesCombatTick() || CrystalAuraModule.hasLiveCommitment())) {
            return false;
        }
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == hotbarChangeTick) return false;
        if (!AutismHandArbiter.beginHandPacketGroup(ID)) return false;
        try {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
        } finally {
            AutismHandArbiter.endHandPacketGroup(ID);
        }
        hotbarChangeTick = tick;
        return true;
    }

    private int findHotbarSlot(Predicate<ItemStack> match) {
        LocalPlayer player = MC.player;
        int selected = player.getInventory().getSelectedSlot();
        int best = -1;
        int bestSteps = Integer.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            if (!match.test(player.getInventory().getItem(slot))) continue;

            if (AutismHandArbiter.slotReserved(slot, ID)) continue;
            int steps = Math.abs(slot - selected);
            steps = Math.min(steps, 9 - steps);
            if (steps < bestSteps) {
                bestSteps = steps;
                best = slot;
            }
        }
        return best;
    }

    private void tickSwitchBack() {
        if (previousSlot < 0) return;

        if (MC.gui.screen() != null || MC.gui.overlay() != null) return;

        if (switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {

            int selected = MC.player.getInventory().getSelectedSlot();
            if (CrystalAuraModule.holdsBorrowedSlot(selected)
                || KillAuraModule.holdsBorrowedSlot(selected)) {
                return;
            }
            previousSlot = -1;
            switchedToSlot = -1;
            switchBackTicks = 0;
            return;
        }
        if (switchBackTicks > 0) {
            switchBackTicks--;
            return;
        }
        if (AutismHandArbiter.slotReserved(previousSlot, ID)) return;

        if (!changeHotbarSlot(previousSlot, false)) return;
        previousSlot = -1;
        switchedToSlot = -1;
    }

    private void rebuildDamageOptions() {
        damageOptions = AutismExplosionDamage.Options.DEFAULT

            .withTerrain(bool("terrain"))
            .withEstimateProtection(true);

        cellOptions.clear();
    }

    private AutismExplosionDamage.Options optionsFor(BlockPos cell) {
        BlockPos key = cell.immutable();
        AutismExplosionDamage.Options cached = cellOptions.get(key);
        if (cached != null) return cached;
        AutismExplosionDamage.Options options = damageOptions

            .withExclude(List.of(key))

            .withDamageSource(AutismExplosionDamage.badRespawnPointSource(MC.level, centreOf(key)));
        cellOptions.put(key, options);
        return options;
    }

    private AABB leadBox(LivingEntity enemy) {
        double dx = (enemy.getX() - enemy.xo) * TARGET_LEAD_TICKS;
        double dz = (enemy.getZ() - enemy.zo) * TARGET_LEAD_TICKS;
        double length = Math.hypot(dx, dz);
        AABB live = enemy.getBoundingBox();
        if (length < 1.0E-3D) return live;
        if (length > TARGET_LEAD_MAX) {
            dx *= TARGET_LEAD_MAX / length;
            dz *= TARGET_LEAD_MAX / length;
            length = TARGET_LEAD_MAX;
        }
        Vec3 feet = enemy.position();
        BlockHitResult wall = MC.level.clip(new ClipContext(feet, feet.add(dx, 0.0D, dz),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, enemy));
        if (wall.getType() != HitResult.Type.MISS) {

            double allowed = (wall.getLocation().distanceTo(feet) - 0.35D) / length;
            if (allowed <= 0.0D) return live;
            if (allowed < 1.0D) {
                dx *= allowed;
                dz *= allowed;
            }
        }
        return live.move(dx, 0.0D, dz);
    }

    private AutismExplosionDamage.Ranking rankAtLead(LivingEntity enemy, Vec3 centre,
                                                     AutismExplosionDamage.Options options,
                                                     AABB lead) {
        double target = AutismExplosionDamage.cachedDamageTo(enemy, centre, POWER,
            options.withOverrideBox(lead));
        LocalPlayer player = MC.player;

        double self = enemy == player ? target
            : AutismExplosionDamage.cachedSelfDamage(centre, POWER, options);
        return new AutismExplosionDamage.Ranking(target, self);
    }

    private boolean damagePasses(AutismExplosionDamage.Ranking rank) {
        if (AutismExplosionDamage.killsSelf(rank.selfDamage())) return false;
        if (rank.targetDamage() < decimal("min-target-damage")) return false;
        if (rank.selfDamage() > decimal("max-self-damage")) return false;

        return !bool("efficient") || rank.isEfficient();
    }

    private boolean selfSafe(BlockPos cell) {
        return !AutismExplosionDamage.killsSelf(selfDamageAt(cell));
    }

    private double selfDamageAt(BlockPos cell) {
        AutismExplosionDamage.Options options = optionsFor(cell);
        if (cycleShield != null && cell.equals(cycleCell)
            && MC.level.getBlockState(cycleShield).is(shieldBlock(cycleShieldObsidian).getBlock())) {
            options = options.withInclude(cycleShield);
        }
        return AutismExplosionDamage.cachedSelfDamage(centreOf(cell), POWER, options);
    }

    private static int comparePreference(AutismExplosionDamage.Ranking first,
                                         AutismExplosionDamage.Ranking second) {
        int byBucket = Double.compare(damageBucket(second.targetDamage()),
            damageBucket(first.targetDamage()));
        if (byBucket != 0) return byBucket;
        return Double.compare(first.selfDamage(), second.selfDamage());
    }

    private static double damageBucket(double targetDamage) {
        return Math.floor(targetDamage * 2.0D);
    }

    private static Vec3 centreOf(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    private static double boxDistanceSqr(AABB box, Vec3 point) {
        double dx = Math.max(Math.max(box.minX - point.x, point.x - box.maxX), 0.0D);
        double dy = Math.max(Math.max(box.minY - point.y, point.y - box.maxY), 0.0D);
        double dz = Math.max(Math.max(box.minZ - point.z, point.z - box.maxZ), 0.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static int[] sphere(int radius) {
        int clamped = Mth.clamp(radius, 0, 8);
        if (clamped == sphereRadius) return sphereCache;
        int limit = clamped * clamped;
        List<int[]> cells = new ArrayList<>();
        for (int x = -clamped; x <= clamped; x++) {
            for (int y = -clamped; y <= clamped; y++) {
                for (int z = -clamped; z <= clamped; z++) {
                    int distance = x * x + y * y + z * z;
                    if (distance <= limit) cells.add(new int[] {x, y, z, distance});
                }
            }
        }
        cells.sort(Comparator.comparingInt(cell -> cell[3]));
        int[] flat = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            flat[i * 3] = cell[0];
            flat[i * 3 + 1] = cell[1];
            flat[i * 3 + 2] = cell[2];
        }
        sphereCache = flat;
        sphereRadius = clamped;
        return flat;
    }
}
