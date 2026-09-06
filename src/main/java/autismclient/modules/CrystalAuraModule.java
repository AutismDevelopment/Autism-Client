

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.util.AutismCombatClicker;
import autismclient.util.AutismExplosionDamage;
import autismclient.util.AutismFaceScan;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismPlacementTick;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class CrystalAuraModule extends Module implements AutismSilentAim.Owner {

    public static final String ID = "crystal-aura";

    private static final float POWER = AutismExplosionDamage.END_CRYSTAL_POWER;

    private static final double CELL_HEIGHT = 2.0D;

    private static final int PLACE_RAY_BUDGET = AutismFaceScan.DEFAULT_TICK_RAY_BUDGET;

    private static final double OBSIDIAN_UPGRADE_BAR = 8.0D;
    private static final double OBSIDIAN_MIN_GAIN = 5.0D;
    private static final double OBSIDIAN_MIN_RATIO = 2.0D;

    private static final int OBSIDIAN_COMMIT_TICKS = 12;
    private static final int PLACE_COMMIT_TICKS = 6;

    private static final int BUILDER_BAN_STALL_TICKS = 4;

    private static final int PRIME_STREAK_TICKS = 2;
    private static final int PRIME_SUPPORT_RADIUS = 1;

    private static final double PRIME_MAX_SPEED_SQR = 0.25D * 0.25D;

    private static final double SPAM_DAMAGE_MIN_BONUS = 2.0D;

    private static final int PRIME_STREAK_IDLE_TICKS = 1;

    private static final double PRIME_VULNERABLE_MIN_DROP = 1.0D;
    private static final double PRIME_VULNERABLE_MAX_DROP = 5.0D;

    private static final int PRIME_VULNERABLE_STREAK_TICKS = 2;

    private static final double DEFEND_MIN_DROP = 0.75D;

    private static final double DEFEND_THREAT_RANGE = 5.0D;

    private static final double DEFEND_MIN_REDUCTION = 2.0D;

    private static final int DEFEND_MAX_BLOCKS_PER_WINDOW = 3;
    private static final int DEFEND_WINDOW_TICKS = 20;

    private static final int DEFEND_COOLDOWN_TICKS = 2;

    private static final int DEFEND_MAX_THREATS = 4;

    private static final int ACTION_SYNC_TICKS = 3;

    private static final int RECENT_PLACED_MAX = 4;

    private static final double COMMIT_MAX_ENEMY_DRIFT_SQR = 1.5D * 1.5D;

    private static final int PRIME_BLAST_GRACE_TICKS = 2;

    private static final int DEFEND_OWN_TRACK_TICKS = 40;

    private static final int DEFEND_OWN_TRACK_MAX = 16;

    private static final double DEFEND_LOW_RING_DROP = 1.5D;

    private static final double DEFEND_DIRECT_BELOW_SQR = 0.25D;

    private AutismExplosionDamage.Options options = AutismExplosionDamage.Options.DEFAULT.withTerrain(true);

    private static final EnumSet<Direction> ALL_FACES = EnumSet.allOf(Direction.class);

    private static final double[] BOX_SAMPLES = {0.3D, 0.5D, 0.7D};

    private static final int MAX_AIM_CANDIDATES = 4;

    private static final int MAX_FULL_EVALUATIONS = 32;

    private static int[] sphereCache = new int[0];
    private static int sphereRadius = -1;

    private static volatile boolean viewOn;
    private static volatile float viewSize = 0.3F;
    private static volatile float viewY = -0.5F;
    private static volatile float viewSpin;
    private static volatile float viewBounce = 0.25F;

    private final Random random = new Random();

    private LivingEntity target;

    private int reservedTick = Integer.MIN_VALUE;

    private BlockPos reservedCell;

    private int lastActionTick = Integer.MIN_VALUE;
    private long lastActionNanos = Long.MIN_VALUE;
    private long actionFloorNanos;

    private String cachedEntityListSource;
    private Set<String> cachedEntityIds = Set.of();

    private int previousSlot = -1;

    private int switchedToSlot = -1;
    private int switchBackTicks;

    private int hotbarChangeTick = Integer.MIN_VALUE;

    private int placeScanCursor;

    private BlockPos committedObsidianCell;

    private int committedObsidianSlot = -1;
    private int committedObsidianTarget = -1;

    private int committedObsidianUntil = Integer.MIN_VALUE;
    private double committedObsidianDamage;

    private BlockPos committedPlaceSupport;
    private int committedPlaceTarget = -1;
    private int committedPlaceUntil = Integer.MIN_VALUE;

    private BlockPos primeSupport;
    private int primeStreak;

    private int primeIdleTicks;

    private int primeVulnerableStreak;

    private int primeVulnerableTarget = -1;

    private int primeVulnerableIdleTicks;

    private int lastDefendTick = Integer.MIN_VALUE;

    private int defendWindowStart = Integer.MIN_VALUE;

    private int defendWindowCount;

    private final Map<BlockPos, Integer> recentPlacedCells = new LinkedHashMap<>();

    private int lastBreakCrystalId = -1;

    private int lastBreakTick = Integer.MIN_VALUE;

    private BlockPos builderBanSupport;
    private int builderBanTarget = -1;

    private int builderBanStallTicks;

    private BlockPos supportScanPlayerPos;
    private BlockPos supportScanEnemyPos;

    private Vec3 committedObsidianEnemyPos;

    private final Map<BlockPos, Integer> ownObsidianCells = new LinkedHashMap<>();

    public CrystalAuraModule() {
        super(ID, "CrystalAura", ModuleCategory.COMBAT, "Places and detonates end crystals.");

        add(new BoolSetting("place", "Place", true)
            .description("Place on obsidian and bedrock.")
            .build());
        add(new BoolSetting("destroy", "Destroy", true)
            .description("Attack crystals in range.")
            .build());
        add(new BoolSetting("obsidian", "Place Obsidian", true)
            .description("Build a support when none exists.")
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
            .visibleWhen(() -> bool("place"))
            .build());
        add(new BoolSetting("only-above", "Only Above", false)
            .description("Place above target.")
            .group("Place")
            .visibleWhen(() -> bool("place"))
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

        add(new IntSetting("place-delay", "Place Delay", 75, 0, 1000, 10)
            .description("Floor between places.")
            .unit("ms")
            .group("Timing")
            .build());
        add(new IntSetting("destroy-delay", "Destroy Delay", 75, 0, 1000, 10)
            .description("Floor between breaks.")
            .unit("ms")
            .group("Timing")
            .build());
        add(new IntSetting("jitter", "Delay Jitter", 20, 0, 200, 5)
            .description("Random extra delay.")
            .unit("ms")
            .group("Timing")
            .build());

        add(new BoolSetting("spam", "Spam", true)
            .description("Run at the tick floor in a prime position.")
            .group("Timing")
            .build());
        add(new IntSetting("spam-delay", "Spam Delay", 0, 0, 200, 10)
            .description("Floor between actions while spamming.")
            .unit("ms")
            .group("Timing")
            .visibleWhen(() -> bool("spam"))
            .build());

        add(new BoolSetting("vulnerable-spam", "Vulnerable Spam", true)
            .description("Prime fast with the enemy trapped above; gates cadence and anchor priority.")
            .group("Timing")
            .build());

        add(new BoolSetting("defend", "Defend", true)
            .description("Block incoming crystals with obsidian.")
            .group("Defend")
            .build());

        add(new BoolSetting("crystal-view", "CrystalView", false)
            .description("Shrink and slow crystal models.")
            .group("CrystalView")
            .build());
        add(new DoubleSetting("view-size", "Size", 0.3D, 0.1D, 1.5D, 0.05D)
            .description("Crystal model scale.")
            .group("CrystalView")
            .visibleWhen(() -> bool("crystal-view"))
            .build());

        add(new DoubleSetting("view-y", "Y Translate", -0.5D, -2.0D, 2.0D, 0.05D)
            .description("Vertical offset in blocks.")
            .group("CrystalView")
            .visibleWhen(() -> bool("crystal-view"))
            .build());
        add(new DoubleSetting("view-spin", "Spin Speed", 0.0D, 0.0D, 5.0D, 0.05D)
            .description("Rotation speed. 0 freezes it.")
            .group("CrystalView")
            .visibleWhen(() -> bool("crystal-view"))
            .build());
        add(new DoubleSetting("view-bounce", "Bounce", 0.25D, -1.0D, 1.0D, 0.05D)
            .description("Bob height. Negative inverts it.")
            .group("CrystalView")
            .visibleWhen(() -> bool("crystal-view"))
            .build());
    }

    @Override
    public void onEnable() {
        resetRuntime();
        pushCrystalView();

    }

    @Override
    public void onDisable() {
        resetRuntime();

        pushCrystalView();

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
        reservedCell = null;
        lastActionTick = Integer.MIN_VALUE;
        lastActionNanos = Long.MIN_VALUE;
        actionFloorNanos = 0L;
        previousSlot = -1;
        switchedToSlot = -1;
        switchBackTicks = 0;
        hotbarChangeTick = Integer.MIN_VALUE;
        placeScanCursor = 0;
        primeSupport = null;
        primeStreak = 0;
        primeIdleTicks = 0;
        primeVulnerableStreak = 0;
        primeVulnerableTarget = -1;
        primeVulnerableIdleTicks = 0;
        lastDefendTick = Integer.MIN_VALUE;
        defendWindowStart = Integer.MIN_VALUE;
        defendWindowCount = 0;
        recentPlacedCells.clear();
        lastBreakCrystalId = -1;
        lastBreakTick = Integer.MIN_VALUE;
        builderBanSupport = null;
        builderBanTarget = -1;
        builderBanStallTicks = 0;
        defendIntendTick = Integer.MIN_VALUE;
        supportScanPlayerPos = null;
        supportScanEnemyPos = null;
        committedObsidianEnemyPos = null;
        ownObsidianCells.clear();
        clearPlanCommitments();
        AutismHandArbiter.releaseAll(ID);
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return viewOn
            || bool("crystal-view")
            || ID.equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    @Override
    public void tick() {
        pushCrystalView();

        if (isEnabled() || MC == null || MC.player == null) return;
        if (!ID.equals(AutismKillAuraRotation.currentOwner())) return;
        AutismKillAuraRotation.update(ID, MC.player);
    }

    @Override
    protected void onOptionValueChanged(String settingId) {

        pushCrystalView();

        if ("vulnerable-spam".equals(settingId)) {
            primeSupport = null;
            primeStreak = 0;
            primeIdleTicks = 0;
            primeVulnerableStreak = 0;
            primeVulnerableTarget = -1;
            primeVulnerableIdleTicks = 0;
        }
    }

    @Override
    public String info() {
        LivingEntity current = target;
        return current == null ? "" : current.getName().getString();
    }

    public static boolean reservesCombatTick() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;

        int age = AutismSharedState.get().getClientTickCounter() - aura.reservedTick;
        return age >= 0 && age <= AutismKillAuraRotation.TICKS_UNTIL_RESET;
    }

    public static boolean ownsSilentRotation() {
        return reservesCombatTick()
            && AutismKillAuraRotation.OWNER_CRYSTAL_AURA.equals(AutismKillAuraRotation.currentOwner());
    }

    public static boolean reservesPlacementCell(BlockPos cell) {
        if (cell == null) return false;
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;
        if (aura.reservedTick != AutismSharedState.get().getClientTickCounter()) return false;
        BlockPos reserved = aura.reservedCell;
        if (reserved == null) return false;
        return cell.getX() == reserved.getX()
            && cell.getZ() == reserved.getZ()
            && cell.getY() >= reserved.getY()
            && cell.getY() < reserved.getY() + (int) CELL_HEIGHT;
    }

    public static boolean holdsBorrowedSlot(int slot) {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;
        return aura.previousSlot >= 0 && aura.switchedToSlot == slot;
    }

    public static boolean hasLiveCommitment() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;
        return aura.committedObsidianCell != null || aura.committedPlaceSupport != null;
    }

    public static boolean inPrimePosition() {
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;
        return aura.primePosition();
    }

    private static volatile int defendIntendTick = Integer.MIN_VALUE;

    public static boolean defendIntends() {
        int age = AutismSharedState.get().getClientTickCounter() - defendIntendTick;
        return age >= 0 && age <= 1;
    }

    public static boolean reservesCommittedCell(BlockPos cell) {
        if (cell == null) return false;
        Module module = ModuleRegistry.get(ID);
        if (!(module instanceof CrystalAuraModule aura) || !aura.isEnabled()) return false;
        BlockPos obsidian = aura.committedObsidianCell;
        if (obsidian != null && cell.getX() == obsidian.getX() && cell.getZ() == obsidian.getZ()
            && cell.getY() >= obsidian.getY() && cell.getY() < obsidian.getY() + (int) CELL_HEIGHT) {
            return true;
        }
        BlockPos support = aura.committedPlaceSupport;
        return support != null && cell.getX() == support.getX() && cell.getZ() == support.getZ()
            && cell.getY() == support.getY() + 1;
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

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (MC == null || MC.player == null || MC.level == null) return;

        tickSwitchBack();

        if (!canRun()) {
            target = null;

            clearPlanCommitments();
            primeSupport = null;
            primeStreak = 0;
            primeIdleTicks = 0;
            primeVulnerableStreak = 0;
            primeVulnerableTarget = -1;
            primeVulnerableIdleTicks = 0;
            standDown();
            return;
        }

        LivingEntity enemy = selectTarget();
        target = enemy;
        if (enemy == null) {

            clearPlanCommitments();
            primeSupport = null;
            primeStreak = 0;
            primeIdleTicks = 0;
            primeVulnerableStreak = 0;
            primeVulnerableTarget = -1;
            primeVulnerableIdleTicks = 0;
            standDown();
            return;
        }

        options = AutismExplosionDamage.Options.DEFAULT.withTerrain(bool("terrain"));

        DestroyPlan destroy;
        PlacePlan place = null;
        ObsidianPlan obsidian = null;

        ObsidianPlan defend = null;

        try (AutismExplosionDamage.ScanPass pass = AutismExplosionDamage.beginScan()) {
            destroy = planDestroy(enemy);

            if (destroy == null) {

                AutismFaceScan.Budget budget = new AutismFaceScan.Budget(PLACE_RAY_BUDGET);

                if (revalidateObsidianCommit(enemy)) {
                    obsidian = aimCommittedObsidian(budget);
                } else if (revalidatePlaceCommit(enemy)) {
                    place = aimCommittedPlace(enemy, budget);
                } else {
                    place = planPlace(enemy, budget);

                    boolean builderSuppressed = false;
                    if (bool("place") && bool("obsidian") && hasCrystalAvailable()
                        && (place == null || place.targetDamage() < OBSIDIAN_UPGRADE_BAR)) {
                        if (builderBanned(enemy)) {
                            builderSuppressed = true;
                        } else if (!awaitingSupportSync()) {
                            obsidian = planObsidian(enemy, budget,
                                place == null ? -1.0D : place.targetDamage());

                            if (obsidian != null) {
                                place = null;
                                commitObsidian(enemy, obsidian);
                            }
                        }
                    }

                    if (obsidian == null && place != null) commitPlace(enemy, place);

                    if (builderSuppressed && place == null) {
                        if (++builderBanStallTicks >= BUILDER_BAN_STALL_TICKS) {
                            builderBanSupport = null;
                            builderBanStallTicks = 0;
                        }
                    } else {
                        builderBanStallTicks = 0;
                    }
                }

                if (place == null && obsidian == null
                    && committedObsidianCell == null && committedPlaceSupport == null) {
                    defend = planDefense(budget);

                    if (defend != null) {
                        defendIntendTick = AutismSharedState.get().getClientTickCounter();
                    }
                }
            }
        }

        trackPrimePosition(enemy, destroy, place, obsidian);

        if (destroy == null && place == null && obsidian == null && defend == null) {
            standDown();
            return;
        }

        if (!primePosition() && AnchorAuraModule.reservesCombatTick()
            && (destroy != null || place != null || obsidian != null || defend == null
                || AnchorAuraModule.worksThisTick())) {
            standDown();
            return;
        }

        reservedTick = AutismSharedState.get().getClientTickCounter();

        reservedCell = null;

        boolean borrowsHand = place != null && place.hand() != InteractionHand.OFF_HAND
            || obsidian != null && obsidian.hand() != InteractionHand.OFF_HAND
            || defend != null && defend.hand() != InteractionHand.OFF_HAND
            || previousSlot >= 0;
        if (borrowsHand) {
            AutismHandArbiter.holdHand(ID);
        } else {
            AutismHandArbiter.releaseHand(ID);
        }

        AutismRotationUtil.Rotation wire = wireRotation();

        AutismRotationUtil.Rotation goal = destroy != null ? destroy.goal()
            : place != null ? place.candidate().aim().goal()
            : obsidian != null ? obsidian.candidate().aim().goal()
            : defend.candidate().aim().goal();

        boolean ready = wire != null && cadenceReady();
        Vec3 destroyHit = ready && destroy != null ? destroyHit(destroy, wire) : null;
        BlockHitResult placeHit =
            ready && destroy == null && place != null ? placeLands(place, wire) : null;
        BlockHitResult obsidianHit =
            ready && destroy == null && place == null && obsidian != null
                ? obsidianLands(obsidian, wire) : null;

        BlockHitResult defendHit =
            ready && destroy == null && place == null && obsidian == null && defend != null
                ? obsidianLands(defend, wire) : null;
        boolean fire = ready && (destroy != null ? destroyHit != null
            : place != null ? placeHit != null
            : obsidian != null ? obsidianHit != null : defendHit != null);

        fire = fire && !BedDefenderModule.ownsSilentRotation() && !SurroundModule.ownsSilentRotation();

        AutismKillAuraRotation.setTarget(ID, AutismKillAuraRotation.PRIORITY_CRYSTAL_AURA,
            fire ? wire : goal);
        AutismKillAuraRotation.update(ID, MC.player, fire);

        if (!fire) return;

        AutismRotationUtil.Rotation outgoing = AutismSilentAim.activeOutgoingRotation(MC.player);
        if (outgoing == null || !sameRotation(outgoing, wire)) return;

        if (destroy != null) {

            if (executeDestroy(destroy, destroyHit)) clearPlanCommitments();
        } else if (place != null) {

            if (executePlace(place, placeHit)) clearPlanCommitments();
        } else if (obsidian != null) {

            if (executeObsidian(obsidian, obsidianHit)) clearPlanCommitments();
        } else {
            executeDefend(defend, defendHit);
        }
    }

    private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {
        return Math.abs(AutismRotationUtil.angleDifference(first.yaw(), second.yaw())) <= 0.05F
            && Math.abs(first.pitch() - second.pitch()) <= 0.05F;
    }

    private void standDown() {

        AutismHandArbiter.releaseHand(ID);

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

    private void bookAction(boolean destroy) {
        lastActionTick = AutismSharedState.get().getClientTickCounter();
        lastActionNanos = System.nanoTime();
        if (spamCadence()) {

            actionFloorNanos = integer("spam-delay") * 1_000_000L;
        } else {
            int jitter = integer("jitter");
            long extra = jitter > 0 ? random.nextInt(jitter + 1) : 0;

            actionFloorNanos =
                (integer(destroy ? "destroy-delay" : "place-delay") + extra) * 1_000_000L;
        }
        switchBackTicks = integer("switch-back-delay");
    }

    private void trackPrimePosition(LivingEntity enemy, DestroyPlan destroy, PlacePlan place,
                                    ObsidianPlan obsidian) {
        if (place == null) {
            boolean held = destroy != null || obsidian != null
                || committedObsidianCell != null || committedPlaceSupport != null;
            if (held) {

                primeIdleTicks = 0;
                primeVulnerableIdleTicks = 0;
                return;
            }
            if (++primeIdleTicks > PRIME_STREAK_IDLE_TICKS) {
                primeSupport = null;
                primeStreak = 0;
            }
            if (++primeVulnerableIdleTicks > PRIME_STREAK_IDLE_TICKS) {
                primeVulnerableStreak = 0;
                primeVulnerableTarget = -1;
            }
            return;
        }
        primeIdleTicks = 0;
        BlockPos support = place.support();
        boolean strong =
            place.targetDamage() >= decimal("min-target-damage") + SPAM_DAMAGE_MIN_BONUS;
        boolean sameArea = primeSupport != null
            && Math.abs(support.getX() - primeSupport.getX()) <= PRIME_SUPPORT_RADIUS
            && Math.abs(support.getY() - primeSupport.getY()) <= PRIME_SUPPORT_RADIUS
            && Math.abs(support.getZ() - primeSupport.getZ()) <= PRIME_SUPPORT_RADIUS;
        primeSupport = support;
        if (!strong) {
            primeStreak = 0;
        } else {
            primeStreak = sameArea ? primeStreak + 1 : 1;
        }

        if (vulnerableGeometry(enemy)) {
            primeVulnerableIdleTicks = 0;
            primeVulnerableStreak =
                enemy.getId() == primeVulnerableTarget ? primeVulnerableStreak + 1 : 1;
            primeVulnerableTarget = enemy.getId();
        } else if (++primeVulnerableIdleTicks > PRIME_STREAK_IDLE_TICKS) {
            primeVulnerableStreak = 0;
            primeVulnerableTarget = -1;
        }
    }

    private boolean primePosition() {
        LivingEntity enemy = target;
        if (enemy == null || MC.player == null) return false;

        int age = AutismSharedState.get().getClientTickCounter() - lastBreakTick;
        boolean blastGrace = age >= 0 && age <= PRIME_BLAST_GRACE_TICKS;

        if (bool("vulnerable-spam") && primeVulnerableStreak >= PRIME_VULNERABLE_STREAK_TICKS
            && (vulnerableGeometry(enemy) || primeVulnerableIdleTicks <= PRIME_STREAK_IDLE_TICKS)) {
            if (blastGrace) return true;
            return horizontalSpeedSqr(MC.player) <= PRIME_MAX_SPEED_SQR
                && horizontalSpeedSqr(enemy) <= PRIME_MAX_SPEED_SQR;
        }
        if (primeStreak < PRIME_STREAK_TICKS) return false;
        if (blastGrace) return true;
        return MC.player.getDeltaMovement().lengthSqr() <= PRIME_MAX_SPEED_SQR
            && enemy.getDeltaMovement().lengthSqr() <= PRIME_MAX_SPEED_SQR;
    }

    private boolean vulnerableGeometry(LivingEntity enemy) {
        double drop = enemy.getY() - MC.player.getY();
        if (drop < PRIME_VULNERABLE_MIN_DROP || drop > PRIME_VULNERABLE_MAX_DROP) return false;
        double dx = enemy.getX() - MC.player.getX();
        double dz = enemy.getZ() - MC.player.getZ();
        double range = MC.player.blockInteractionRange();
        return dx * dx + dz * dz <= range * range;
    }

    private static double horizontalSpeedSqr(Entity entity) {
        Vec3 delta = entity.getDeltaMovement();
        return delta.x * delta.x + delta.z * delta.z;
    }

    private boolean spamCadence() {
        return bool("spam") && primePosition();
    }

    private static final double TARGET_RANGE_GRACE = 0.5D;
    private static final double TARGET_DISTANCE_SWITCH_MARGIN = 1.5D;
    private static final double TARGET_HP_SWITCH_RATIO = 0.75D;
    private static final double TARGET_FOV_SWITCH_RATIO = 0.70D;

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

                && entity.hurtTime <= integer("hurt-time")
                && AutismExplosionDamage.effectiveHealth(entity) > 0.0D);
        if (found.isEmpty()) return null;

        Vec3 eye = player.getEyePosition();
        double rangeSq = range * range;

        String mode = choice("targeting");
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : found) {
            double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
            if (distanceSq > rangeSq) continue;
            double score = targetingScore(mode, entity, distanceSq, eye);
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }

        LivingEntity incumbent = target;
        if (incumbent != null && incumbent != best && found.contains(incumbent)) {
            double grace = range + TARGET_RANGE_GRACE;
            double incumbentDistanceSq = boxDistanceSqr(incumbent.getBoundingBox(), eye);
            if (incumbentDistanceSq <= grace * grace
                && (best == null || !clearlyBeats(mode, bestScore,
                    targetingScore(mode, incumbent, incumbentDistanceSq, eye)))) {
                return incumbent;
            }
        }
        return best;
    }

    private double targetingScore(String mode, LivingEntity entity, double distanceSq, Vec3 eye) {
        return switch (mode) {
            case "HP" -> AutismExplosionDamage.effectiveHealth(entity);
            case "FOV" -> AutismRotationUtil.rotationAngleTo(
                AutismRotationUtil.playerRotation(MC.player),
                AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eye));
            default -> distanceSq;
        };
    }

    private static final double TARGET_FOV_SWITCH_FLOOR = 1.5;

    private static boolean clearlyBeats(String mode, double challengerScore, double incumbentScore) {
        return switch (mode) {
            case "HP" -> challengerScore <= incumbentScore * TARGET_HP_SWITCH_RATIO;
            case "FOV" -> challengerScore <= incumbentScore * TARGET_FOV_SWITCH_RATIO
                && challengerScore <= incumbentScore - TARGET_FOV_SWITCH_FLOOR;
            default -> Math.sqrt(challengerScore)
                <= Math.sqrt(incumbentScore) - TARGET_DISTANCE_SWITCH_MARGIN;
        };
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

    private record DestroyPlan(EndCrystal crystal, AutismRotationUtil.Rotation goal, double range) {
    }

    private DestroyPlan planDestroy(LivingEntity enemy) {
        if (!bool("destroy")) return null;
        LocalPlayer player = MC.player;

        double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (range <= 0.0D) return null;

        Vec3 eye = player.getEyePosition();
        AABB search = player.getBoundingBox().inflate(range + CELL_HEIGHT);
        List<EndCrystal> crystals = MC.level.getEntitiesOfClass(EndCrystal.class, search, Entity::isAlive);
        if (crystals.isEmpty()) return null;

        double rangeSq = range * range;
        int tick = AutismSharedState.get().getClientTickCounter();

        if (lastBreakCrystalId >= 0) {
            Entity tracked = MC.level.getEntity(lastBreakCrystalId);
            if (tracked == null || !tracked.isAlive()) lastBreakCrystalId = -1;
        }

        if (!recentPlacedCells.isEmpty()) {
            for (EndCrystal crystal : crystals) {
                recentPlacedCells.remove(BlockPos.containing(crystal.position()));
            }
        }
        EndCrystal best = null;
        AutismExplosionDamage.Ranking bestRank = null;
        for (EndCrystal crystal : crystals) {

            if (crystal.getId() == lastBreakCrystalId
                && tick - lastBreakTick >= 0 && tick - lastBreakTick < ACTION_SYNC_TICKS) {
                continue;
            }
            if (boxDistanceSqr(crystal.getBoundingBox(), eye) > rangeSq) continue;
            AutismExplosionDamage.Ranking rank =
                AutismExplosionDamage.cachedRank(enemy, crystal.position(), POWER, options);
            if (!damagePasses(enemy, rank)) continue;
            if (bestRank == null || betterRank(rank, bestRank)) {
                best = crystal;
                bestRank = rank;
            }
        }
        if (best == null) return null;

        AutismRotationUtil.Rotation goal = destroyAim(best, range);
        return goal == null ? null : new DestroyPlan(best, goal, range);
    }

    private AutismRotationUtil.Rotation destroyAim(EndCrystal crystal, double range) {
        Vec3 eye = MC.player.getEyePosition();
        AutismRotationUtil.Rotation wire = aimReference();
        AABB box = crystal.getBoundingBox();
        AutismRotationUtil.Rotation best = null;
        float bestDelta = Float.MAX_VALUE;
        for (double fx : BOX_SAMPLES) {
            for (double fy : BOX_SAMPLES) {
                for (double fz : BOX_SAMPLES) {
                    Vec3 point = new Vec3(
                        Mth.lerp(fx, box.minX, box.maxX),
                        Mth.lerp(fy, box.minY, box.maxY),
                        Mth.lerp(fz, box.minZ, box.maxZ));
                    double distance = eye.distanceTo(point);
                    if (distance > range) continue;

                    if (blockedByTerrain(eye, point)) continue;
                    AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(point, eye);
                    float delta = AutismRotationUtil.rotationAngleTo(rotation, wire);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        best = rotation;
                    }
                }
            }
        }
        return best;
    }

    private Vec3 destroyHit(DestroyPlan plan, AutismRotationUtil.Rotation wire) {
        if (!plan.crystal().isAlive()) return null;

        if (((AutismMinecraftAccessor) MC).autism$getMissTime() > 0) return null;
        Vec3 eye = MC.player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(wire.pitch(), wire.yaw());
        Optional<Vec3> hit = plan.crystal().getBoundingBox().clip(eye, eye.add(look.scale(plan.range())));
        if (hit.isEmpty()) return null;
        if (eye.distanceTo(hit.get()) > plan.range()) return null;
        return blockedByTerrain(eye, hit.get()) ? null : hit.get();
    }

    private boolean executeDestroy(DestroyPlan plan, Vec3 hitPoint) {
        EndCrystal crystal = plan.crystal();
        if (!crystal.isAlive()) return false;

        if (!AutismHandArbiter.beginHandPacketGroup(ID)) return false;
        try {
            ((AutismMultiPlayerGameModeAccessor) MC.gameMode).autism$ensureHasSentCarriedItem();
        } finally {
            AutismHandArbiter.endHandPacketGroup(ID);
        }
        if (!AutismCombatClicker.queueAttack(new EntityHitResult(crystal, hitPoint))) return false;

        bookAction(true);

        lastBreakCrystalId = crystal.getId();
        lastBreakTick = AutismSharedState.get().getClientTickCounter();
        MC.player.resetAttackStrengthTicker();
        return true;
    }

    private record PlacePlan(BlockPos support, InteractionHand hand, double range,
                             AutismFaceScan.Candidate candidate, AutismFaceScan.Request request,
                             double targetDamage) {
    }

    private record ScanEntry(BlockPos support, AutismFaceScan.Request request,
                             AutismFaceScan.Option option) {
    }

    private PlacePlan planPlace(LivingEntity enemy, AutismFaceScan.Budget budget) {
        if (!bool("place")) return null;
        LocalPlayer player = MC.player;

        double range = player.blockInteractionRange();
        if (range <= 0.0D) return null;
        if (!hasCrystalAvailable()) return null;

        List<BlockPos> supports = rankSupports(enemy, range);
        if (supports.isEmpty()) return null;

        Vec3 eye = player.getEyePosition();
        AutismRotationUtil.Rotation wire = aimReference();

        AutismFaceScan.Refusal[] refusal = new AutismFaceScan.Refusal[1];

        List<ScanEntry> entries = new ArrayList<>();
        List<AutismFaceScan.Option> scan = new ArrayList<>();
        for (BlockPos support : supports) {
            BlockPos cell = support.above();
            BlockState state = MC.level.getBlockState(support);
            AutismFaceScan.Request request = new AutismFaceScan.Request(
                    cell, eye, range, AutismFaceScan.onSupport(support, ALL_FACES))
                .from(wire)

                .leadEye(eye.add(player.getDeltaMovement()))
                .budget(budget);
            scan.clear();
            supportOptions(support, state, cell, eye, wire, scan);
            for (AutismFaceScan.Option option : scan) entries.add(new ScanEntry(support, request, option));
        }
        if (entries.isEmpty()) return null;

        int size = entries.size();

        int start = placeScanCursor >= 0 && placeScanCursor < size ? placeScanCursor : 0;
        for (int step = 0; step < size; step++) {
            int index = (start + step) % size;
            ScanEntry entry = entries.get(index);
            AutismFaceScan.Aim aim = AutismFaceScan.solve(entry.option(), entry.request(), refusal);
            if (aim == null) continue;
            AutismFaceScan.Candidate candidate =
                AutismFaceScan.probe(entry.option(), aim, entry.request(), refusal);
            if (candidate == null) {

                if (refusal[0] == AutismFaceScan.Refusal.NO_BUDGET) {
                    placeScanCursor = index;
                    return null;
                }
                continue;
            }

            InteractionHand hand = ensureCrystalHand();
            placeScanCursor = 0;

            BlockPos chosen = entry.support();
            Vec3 chosenSource = new Vec3(chosen.getX() + 0.5D, chosen.getY() + 1.0D,
                chosen.getZ() + 0.5D);
            double chosenDamage =
                AutismExplosionDamage.cachedRank(enemy, chosenSource, POWER, options).targetDamage();
            return new PlacePlan(chosen, hand, range, candidate, entry.request(), chosenDamage);
        }

        placeScanCursor = 0;
        return null;
    }

    private void supportOptions(BlockPos support, BlockState state, BlockPos cell, Vec3 eye,
                                AutismRotationUtil.Rotation wire, List<AutismFaceScan.Option> out) {
        boolean requiresSneak = AutismFaceScan.sneakUnlocks(state, support);

        List<TurnKeyedOption> keyed = new ArrayList<>();
        for (Direction face : AutismFaceScan.FACE_ORDER_UP_FIRST) {
            AutismFaceScan.Intent intent = new AutismFaceScan.Intent(support, face);
            for (AABB rect : AutismFaceScan.faceRects(state, support, face,
                AutismFaceScan.DEFAULT_RECTS_PER_FACE)) {
                AutismFaceScan.Option option = new AutismFaceScan.Option(cell, support, face, 1,
                    state, rect, rectArea(rect, face),
                    AutismFaceScan.edgeConfidence(rect, face, eye), requiresSneak, intent);
                double turn = AutismRotationUtil.rotationAngleTo(
                    AutismRotationUtil.lookingAt(
                        AutismFaceScan.faceCentre(rect, face), eye), wire);
                keyed.add(new TurnKeyedOption(option, turn));
            }
        }
        keyed.sort(Comparator.comparingDouble(TurnKeyedOption::turn));
        for (TurnKeyedOption entry : keyed) out.add(entry.option());
    }

    private record TurnKeyedOption(AutismFaceScan.Option option, double turn) {
    }

    private static double rectArea(AABB rect, Direction face) {
        Direction.Axis normal = face.getAxis();
        Direction.Axis first = AutismFaceScan.inPlaneAxis(normal, true);
        Direction.Axis second = AutismFaceScan.inPlaneAxis(normal, false);
        return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
    }

    private List<BlockPos> rankSupports(LivingEntity enemy, double range) {
        LocalPlayer player = MC.player;
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));

        double floor = decimal("min-target-damage");
        double rangeSq = range * range;

        AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());

        List<Candidate> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (!supportsCrystal(cursor)) continue;
            if (!MC.level.isEmptyBlock(cursor.above())) continue;

            Vec3 source = new Vec3(cursor.getX() + 0.5D, cursor.getY() + 1.0D, cursor.getZ() + 0.5D);

            if (bool("only-above") && source.y < enemy.getY()) continue;
            double distanceSq = eye.distanceToSqr(source);
            if (distanceSq > rangeSq) continue;

            double bound = AutismExplosionDamage.maxDamageTo(enemy, source, POWER, options);
            if (bound < floor) continue;
            if (!cellIsFree(cursor, nextBox) || blockedByUnsyncedPlacement(cursor)) continue;
            candidates.add(new Candidate(cursor.immutable(), source, bound, distanceSq));
        }
        if (candidates.isEmpty()) return List.of();
        candidates.sort(Comparator.<Candidate>comparingDouble(Candidate::bound).reversed());

        List<Scored> accepted = new ArrayList<>();
        double bestDamage = -1.0D;
        int evaluated = 0;
        for (Candidate candidate : candidates) {
            if (accepted.size() >= MAX_AIM_CANDIDATES && candidate.bound() <= bestDamage) break;
            if (evaluated >= MAX_FULL_EVALUATIONS) break;

            if (!supportVisible(eye, candidate.pos(), candidate.source())) continue;
            evaluated++;
            AutismExplosionDamage.Ranking rank =
                AutismExplosionDamage.cachedRank(enemy, candidate.source(), POWER, options);
            if (!damagePasses(enemy, rank)) continue;
            accepted.add(new Scored(candidate.pos(), rank, candidate.distanceSq()));
            bestDamage = Math.max(bestDamage, rank.targetDamage());
        }
        if (accepted.isEmpty()) return List.of();

        accepted.sort((first, second) -> {
            int byBucket = Double.compare(damageBucket(second.rank().targetDamage()),
                damageBucket(first.rank().targetDamage()));
            if (byBucket != 0) return byBucket;
            int bySelf = Double.compare(first.rank().selfDamage(), second.rank().selfDamage());
            if (bySelf != 0) return bySelf;

            return Double.compare(second.distanceSq(), first.distanceSq());
        });

        List<BlockPos> result = new ArrayList<>(Math.min(MAX_AIM_CANDIDATES, accepted.size()));
        for (Scored scored : accepted) {
            if (result.size() >= MAX_AIM_CANDIDATES) break;
            result.add(scored.pos());
        }
        return result;
    }

    private record Candidate(BlockPos pos, Vec3 source, double bound, double distanceSq) {
    }

    private record Scored(BlockPos pos, AutismExplosionDamage.Ranking rank, double distanceSq) {
    }

    private static double damageBucket(double targetDamage) {
        return Math.floor(targetDamage * 2.0D);
    }

    private boolean supportsCrystal(BlockPos pos) {
        BlockState state = MC.level.getBlockState(pos);
        return state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK;
    }

    private boolean cellIsFree(BlockPos support, AABB nextBox) {
        BlockPos cell = support.above();
        if (!MC.level.isEmptyBlock(cell)) return false;
        AABB box = new AABB(cell.getX(), cell.getY(), cell.getZ(),
            cell.getX() + 1.0D, cell.getY() + CELL_HEIGHT, cell.getZ() + 1.0D);
        if (nextBox.intersects(box)) return false;
        List<Entity> occupants = MC.level.getEntities((Entity) null, box);
        if (!occupants.isEmpty()) retireSyncedPlacement(cell, occupants);
        return occupants.isEmpty();
    }

    private boolean blockedByUnsyncedPlacement(BlockPos support) {
        Integer until = recentPlacedCells.get(support.above());
        if (until == null) return false;

        if (AutismSharedState.get().getClientTickCounter() - until >= 0) {
            recentPlacedCells.remove(support.above());
            return false;
        }
        return true;
    }

    private void retireSyncedPlacement(BlockPos cell, List<Entity> occupants) {
        if (recentPlacedCells.isEmpty()) return;
        for (Entity occupant : occupants) {
            if (occupant instanceof EndCrystal) {
                recentPlacedCells.remove(cell);
                return;
            }
        }
    }

    private void notePlacedCell(BlockPos cell) {
        int tick = AutismSharedState.get().getClientTickCounter();
        recentPlacedCells.put(cell, tick + ACTION_SYNC_TICKS);
        recentPlacedCells.entrySet().removeIf(entry -> tick - entry.getValue() >= 0);
        while (recentPlacedCells.size() > RECENT_PLACED_MAX) {
            recentPlacedCells.remove(recentPlacedCells.keySet().iterator().next());
        }
    }

    private boolean awaitingSupportSync() {
        int tick = AutismSharedState.get().getClientTickCounter();
        int radius = Mth.clamp(Mth.ceil(MC.player.blockInteractionRange()), 0, 8);
        BlockPos origin = MC.player.blockPosition();
        if (lastBreakCrystalId >= 0
            && tick - lastBreakTick >= 0 && tick - lastBreakTick < ACTION_SYNC_TICKS) {
            Entity dying = MC.level.getEntity(lastBreakCrystalId);
            if (dying != null
                && inCandidateSphere(origin, BlockPos.containing(dying.position()).below(), radius)) {
                return true;
            }
        }
        if (!recentPlacedCells.isEmpty()) {
            for (Map.Entry<BlockPos, Integer> entry : recentPlacedCells.entrySet()) {
                if (tick - entry.getValue() >= 0) continue;
                if (inCandidateSphere(origin, entry.getKey().below(), radius)) return true;
            }
        }
        return false;
    }

    private static boolean inCandidateSphere(BlockPos origin, BlockPos support, int radius) {
        int dx = support.getX() - origin.getX();
        int dy = support.getY() - origin.getY();
        int dz = support.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private BlockHitResult placeLands(PlacePlan plan, AutismRotationUtil.Rotation wire) {
        if (plan.hand() == null) return null;

        if (plan.hand() == InteractionHand.OFF_HAND && AutismCombatClicker.mainHandWouldPreempt()) {
            return null;
        }
        BlockPos support = plan.support();
        if (!supportsCrystal(support)) return null;
        AABB nextBox = MC.player.getBoundingBox().move(MC.player.getDeltaMovement());
        if (!cellIsFree(support, nextBox) || blockedByUnsyncedPlacement(support)) return null;
        if (!isCrystal(MC.player.getItemInHand(plan.hand()))) return null;
        return AutismFaceScan.confirm(plan.candidate(), wire, MC.player.getEyePosition(),
            plan.range(), plan.request());
    }

    private boolean executePlace(PlacePlan plan, BlockHitResult hit) {
        if (hit == null) return false;
        InteractionHand hand = plan.hand();

        if (ModuleRegistry.shouldCancelUseExcept(hit, hand, ID)) return false;

        if (!AutismCombatClicker.queueUse(hit, hand)) return false;
        if (!AutismPlacementTick.claim(ID)) {

            AutismCombatClicker.cancel();
            return false;
        }

        reservedCell = plan.support().above();

        notePlacedCell(reservedCell);

        bookAction(false);
        return true;
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

    private record ObsidianPlan(BlockPos cell, InteractionHand hand, double range,
                                AutismFaceScan.Candidate candidate, double targetDamage, int slot) {
    }

    private ObsidianPlan planObsidian(LivingEntity enemy, AutismFaceScan.Budget budget,
                                      double rivalDamage) {
        LocalPlayer player = MC.player;
        double range = player.blockInteractionRange();
        if (range <= 0.0D) return null;

        boolean offhand = isObsidian(player.getOffhandItem())
            && !AutismHandArbiter.offhandClaimedByOther(ID)
            && !AutismCombatClicker.mainHandWouldPreempt();
        int slot = offhand ? -1 : findObsidianHotbarSlot();
        if (!offhand && slot < 0) return null;
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack material = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);

        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(range));
        double floor = decimal("min-target-damage");
        double rangeSq = range * range;
        AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());
        BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();

        List<Candidate> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            Vec3 source = new Vec3(cursor.getX() + 0.5D, cursor.getY() + 1.0D, cursor.getZ() + 0.5D);

            if (bool("only-above") && source.y < enemy.getY()) continue;
            if (supportsCrystal(cursor)) {

                double supportBound = AutismExplosionDamage.maxDamageTo(enemy, source, POWER, options);
                if (supportBound >= floor
                    && cellIsFree(cursor, nextBox)
                    && supportVisible(eye, cursor, source)) {
                    AutismExplosionDamage.Ranking supportRank =
                        AutismExplosionDamage.cachedRank(enemy, source, POWER, options);
                    if (damagePasses(enemy, supportRank)) {
                        rivalDamage = Math.max(rivalDamage, supportRank.targetDamage());
                    }
                }
                continue;
            }
            if (!MC.level.getBlockState(cursor).canBeReplaced()) continue;

            if (AnchorAuraModule.reservesCycleCell(cursor)) continue;

            if (!MC.level.isUnobstructed(obsidianState, cursor, CollisionContext.empty())) continue;
            double distanceSq = eye.distanceToSqr(source);
            if (distanceSq > rangeSq) continue;
            double bound = AutismExplosionDamage.maxDamageTo(enemy, source, POWER, options);
            if (bound < floor) continue;

            if (!cellIsFree(cursor, nextBox) || blockedByUnsyncedPlacement(cursor)) continue;
            candidates.add(new Candidate(cursor.immutable(), source, bound, distanceSq));
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.<Candidate>comparingDouble(Candidate::bound).reversed());

        List<Scored> accepted = new ArrayList<>();
        double bestDamage = -1.0D;
        int evaluated = 0;
        for (Candidate candidate : candidates) {
            if (accepted.size() >= MAX_AIM_CANDIDATES && candidate.bound() <= bestDamage) break;
            if (evaluated >= MAX_FULL_EVALUATIONS) break;
            evaluated++;
            AutismExplosionDamage.Ranking rank =
                AutismExplosionDamage.cachedRank(enemy, candidate.source(), POWER, options);
            if (!damagePasses(enemy, rank)) continue;
            accepted.add(new Scored(candidate.pos(), rank, candidate.distanceSq()));
            bestDamage = Math.max(bestDamage, rank.targetDamage());
        }
        if (accepted.isEmpty()) return null;

        accepted.sort((first, second) -> {
            int byBucket = Double.compare(damageBucket(second.rank().targetDamage()),
                damageBucket(first.rank().targetDamage()));
            if (byBucket != 0) return byBucket;
            int bySelf = Double.compare(first.rank().selfDamage(), second.rank().selfDamage());
            if (bySelf != 0) return bySelf;

            return Double.compare(second.distanceSq(), first.distanceSq());
        });

        double builderBest = accepted.get(0).rank().targetDamage();
        if (rivalDamage >= 0.0D && !worthUpgrade(rivalDamage, builderBest)) return null;

        AutismRotationUtil.Rotation wire = aimReference();
        AutismFaceScan.Refusal[] refusal = new AutismFaceScan.Refusal[1];
        int limit = Math.min(MAX_AIM_CANDIDATES, accepted.size());
        for (int i = 0; i < limit; i++) {
            BlockPos cell = accepted.get(i).pos();
            AutismFaceScan.Request request =
                obsidianRequest(cell, material, hand, range, eye, wire).budget(budget);
            AutismFaceScan.Candidate candidate = AutismFaceScan.best(request, refusal);
            if (candidate != null) {

                InteractionHand resolved =
                    offhand ? InteractionHand.OFF_HAND : ensureMainHandForObsidian(slot);
                return new ObsidianPlan(cell, resolved, range, candidate,
                    accepted.get(i).rank().targetDamage(), slot);
            }

            if (refusal[0] == AutismFaceScan.Refusal.NO_BUDGET) return null;
            refusal[0] = null;
        }
        return null;
    }

    private static boolean worthUpgrade(double rivalDamage, double builderDamage) {
        return builderDamage >= rivalDamage + OBSIDIAN_MIN_GAIN
            && builderDamage >= rivalDamage * OBSIDIAN_MIN_RATIO;
    }

    private AutismFaceScan.Request obsidianRequest(BlockPos cell, ItemStack stack,
                                                   InteractionHand hand, double range, Vec3 eye,
                                                   AutismRotationUtil.Rotation wire) {
        return new AutismFaceScan.Request(cell, eye, range,
            AutismFaceScan.blockItem(stack, MC.player, hand))
            .from(wire)
            .pitchLimit(AutismFaceScan.goalPitchLimit())

            .leadEye(eye.add(MC.player.getDeltaMovement()))
            .sneaking(MC.player.isSecondaryUseActive())
            .sneakAllowed(false);
    }

    private BlockHitResult obsidianLands(ObsidianPlan plan, AutismRotationUtil.Rotation wire) {
        if (plan.hand() == null) return null;
        if (plan.hand() == InteractionHand.OFF_HAND && AutismCombatClicker.mainHandWouldPreempt()) {
            return null;
        }
        BlockPos cell = plan.cell();
        if (!MC.level.getBlockState(cell).canBeReplaced()) return null;
        if (!MC.level.isUnobstructed(
            Blocks.OBSIDIAN.defaultBlockState(), cell, CollisionContext.empty())) return null;
        ItemStack held = MC.player.getItemInHand(plan.hand());
        if (!isObsidian(held)) return null;
        Vec3 eye = MC.player.getEyePosition();
        return AutismFaceScan.confirm(plan.candidate(), wire, eye, plan.range(),
            obsidianRequest(cell, held, plan.hand(), plan.range(), eye, aimReference()));
    }

    private boolean executeObsidian(ObsidianPlan plan, BlockHitResult hit) {
        if (hit == null) return false;
        InteractionHand hand = plan.hand();

        if (ModuleRegistry.shouldCancelUseExcept(hit, hand, ID)) return false;

        if (!AutismCombatClicker.queueUse(hit, hand)) return false;
        if (!AutismPlacementTick.claim(ID)) {
            AutismCombatClicker.cancel();
            return false;
        }

        reservedCell = plan.cell().above();

        ownObsidianCells.put(plan.cell().immutable(),
            AutismSharedState.get().getClientTickCounter() + DEFEND_OWN_TRACK_TICKS);
        while (ownObsidianCells.size() > DEFEND_OWN_TRACK_MAX) {
            ownObsidianCells.remove(ownObsidianCells.keySet().iterator().next());
        }

        bookAction(false);
        return true;
    }

    private boolean ownPlacedCell(BlockPos cell) {
        Integer until = ownObsidianCells.get(cell);
        if (until == null) return false;
        if (AutismSharedState.get().getClientTickCounter() - until >= 0) {
            ownObsidianCells.remove(cell);
            return false;
        }
        return true;
    }

    private static boolean isObsidian(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.OBSIDIAN);
    }

    private int findObsidianHotbarSlot() {
        LocalPlayer player = MC.player;
        int selected = player.getInventory().getSelectedSlot();
        int best = -1;
        int bestSteps = Integer.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            if (!isObsidian(player.getInventory().getItem(slot))) continue;

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

    private InteractionHand ensureMainHandForObsidian(int slot) {
        return ensureMainHandForObsidian(slot, false);
    }

    private InteractionHand ensureMainHandForObsidian(int slot, boolean defensive) {
        LocalPlayer player = MC.player;
        if (player.getInventory().getSelectedSlot() == slot) {
            switchBackTicks = integer("switch-back-delay");
            return InteractionHand.MAIN_HAND;
        }
        int held = player.getInventory().getSelectedSlot();
        if (changeHotbarSlot(slot, defensive)) {
            if (previousSlot < 0) previousSlot = held;
            switchedToSlot = slot;
        }
        switchBackTicks = integer("switch-back-delay");
        return null;
    }

    private record DefendThreat(Vec3 source, double selfDamage) {
    }

    private record DefendCell(BlockPos pos, double reduction) {
    }

    private ObsidianPlan planDefense(AutismFaceScan.Budget budget) {
        if (!bool("defend")) return null;
        LocalPlayer player = MC.player;
        if (SurroundModule.ownsSilentRotation() || BedDefenderModule.ownsSilentRotation()) {
            return null;
        }
        if (primePosition()) return null;
        int tick = AutismSharedState.get().getClientTickCounter();
        int cooldownAge = tick - lastDefendTick;
        if (cooldownAge >= 0 && cooldownAge < DEFEND_COOLDOWN_TICKS) return null;
        int windowAge = tick - defendWindowStart;
        if (windowAge < 0 || windowAge >= DEFEND_WINDOW_TICKS) {
            defendWindowStart = tick;
            defendWindowCount = 0;
        }
        if (defendWindowCount >= DEFEND_MAX_BLOCKS_PER_WINDOW) return null;

        LivingEntity enemy = defendThreat();
        if (enemy == null) return null;
        double range = player.blockInteractionRange();
        if (range <= 0.0D) return null;
        List<DefendThreat> threats = defendThreatSources(player);
        if (threats.isEmpty()) return null;

        boolean offhand = isObsidian(player.getOffhandItem())
            && !AutismHandArbiter.offhandClaimedByOther(ID)
            && !AutismCombatClicker.mainHandWouldPreempt();
        int slot = offhand ? -1 : findObsidianHotbarSlot();
        if (!offhand && slot < 0) return null;
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack material =
            offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);

        Vec3 eye = player.getEyePosition();
        double rangeSq = range * range;
        AABB playerBox = player.getBoundingBox();
        Vec3 delta = player.getDeltaMovement();
        BlockPos feet = player.blockPosition();
        AABB nextBox = playerBox.move(delta);
        BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();
        double enemyDx = enemy.getX() - player.getX();
        double enemyDz = enemy.getZ() - player.getZ();
        double worst = threats.get(0).selfDamage();

        boolean lowRing = player.getY() - enemy.getY() >= DEFEND_LOW_RING_DROP;
        boolean directBelow =
            enemyDx * enemyDx + enemyDz * enemyDz <= DEFEND_DIRECT_BELOW_SQR;
        List<DefendCell> cells = new ArrayList<>();
        for (int yOff = lowRing ? -1 : 0; yOff <= 0; yOff++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    if (!directBelow && dx * enemyDx + dz * enemyDz <= 0.0D) continue;
                    BlockPos cell = feet.offset(dx, yOff, dz);
                    if (!MC.level.getBlockState(cell).canBeReplaced()) continue;

                    if (AnchorAuraModule.reservesCycleCell(cell)) continue;

                    AABB cellBox = new AABB(cell);
                    if (cellBox.intersects(playerBox) || cellBox.intersects(nextBox)
                        || cellBox.intersects(playerBox.move(delta.scale(-1.0D)))) {
                        continue;
                    }
                    if (!MC.level.isUnobstructed(obsidianState, cell, CollisionContext.empty())) {
                        continue;
                    }
                    Vec3 centre =
                        new Vec3(cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D);
                    if (eye.distanceToSqr(centre) > rangeSq) continue;
                    double after = 0.0D;
                    for (DefendThreat threat : threats) {
                        after = Math.max(after, AutismExplosionDamage.cachedDamageTo(player,
                            threat.source(), POWER, options.withInclude(cell)));
                    }
                    double reduction = worst - after;
                    if (reduction >= DEFEND_MIN_REDUCTION) cells.add(new DefendCell(cell, reduction));
                }
            }
        }
        if (cells.isEmpty()) return null;
        cells.sort(Comparator.comparingDouble(DefendCell::reduction).reversed());

        AutismFaceScan.Refusal[] refusal = new AutismFaceScan.Refusal[1];
        int limit = Math.min(MAX_AIM_CANDIDATES, cells.size());
        for (int i = 0; i < limit; i++) {
            DefendCell cell = cells.get(i);
            AutismFaceScan.Request request =
                obsidianRequest(cell.pos(), material, hand, range, eye, aimReference())
                    .budget(budget);
            AutismFaceScan.Candidate candidate = AutismFaceScan.best(request, refusal);
            if (candidate != null) {

                InteractionHand resolved =
                    offhand ? InteractionHand.OFF_HAND : ensureMainHandForObsidian(slot, true);
                return new ObsidianPlan(cell.pos(), resolved, range, candidate, worst, slot);
            }

            if (refusal[0] == AutismFaceScan.Refusal.NO_BUDGET) return null;
            refusal[0] = null;
        }
        return null;
    }

    private LivingEntity defendThreat() {
        LocalPlayer player = MC.player;
        double range = decimal("target-range");
        AABB search = player.getBoundingBox().inflate(range);
        List<Player> found = MC.level.getEntitiesOfClass(Player.class, search,
            entity -> entity != player
                && entity.isAlive()
                && !entity.isSpectator()
                && !AutismAntiBot.suppress(entity)
                && !TeamsModule.combatExcluded(entity, "killaura")
                && entity.getY() <= player.getY() - DEFEND_MIN_DROP);
        Vec3 eye = player.getEyePosition();
        double rangeSq = range * range;
        Player best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Player entity : found) {
            double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
            if (distanceSq > rangeSq) continue;
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = entity;
            }
        }
        return best;
    }

    private List<DefendThreat> defendThreatSources(LocalPlayer player) {
        double maxSelf = decimal("max-self-damage");
        List<DefendThreat> threats = new ArrayList<>();
        AABB near = player.getBoundingBox().inflate(DEFEND_THREAT_RANGE);
        for (EndCrystal crystal : MC.level.getEntitiesOfClass(EndCrystal.class, near,
            Entity::isAlive)) {

            if (ownPlacedCell(BlockPos.containing(crystal.position()).below())) continue;
            double self = AutismExplosionDamage.cachedDamageTo(player, crystal.position(), POWER,
                options);
            if (self > maxSelf) threats.add(new DefendThreat(crystal.position(), self));
        }

        BlockPos origin = player.blockPosition();
        int[] offsets = sphere(Mth.ceil(DEFEND_THREAT_RANGE));
        AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1],
                origin.getZ() + offsets[i + 2]);
            if (!supportsCrystal(cursor)) continue;
            if (!MC.level.isEmptyBlock(cursor.above())) continue;

            if (ownPlacedCell(cursor)) continue;
            Vec3 source = new Vec3(cursor.getX() + 0.5D, cursor.getY() + 1.0D,
                cursor.getZ() + 0.5D);

            if (AutismExplosionDamage.maxSelfDamage(source, POWER, options) <= maxSelf) continue;
            if (!cellIsFree(cursor, nextBox)) continue;
            double self = AutismExplosionDamage.cachedDamageTo(player, source, POWER, options);
            if (self > maxSelf) threats.add(new DefendThreat(source, self));
        }
        threats.sort(Comparator.comparingDouble(DefendThreat::selfDamage).reversed());
        return threats.size() <= DEFEND_MAX_THREATS
            ? threats : new ArrayList<>(threats.subList(0, DEFEND_MAX_THREATS));
    }

    private boolean executeDefend(ObsidianPlan plan, BlockHitResult hit) {
        if (!executeObsidian(plan, hit)) return false;
        lastDefendTick = AutismSharedState.get().getClientTickCounter();
        defendWindowCount++;
        return true;
    }

    private void commitObsidian(LivingEntity enemy, ObsidianPlan plan) {
        committedObsidianCell = plan.cell();
        committedObsidianSlot = plan.slot();
        committedObsidianTarget = enemy.getId();
        committedObsidianDamage = plan.targetDamage();
        committedObsidianEnemyPos = enemy.position();
        committedObsidianUntil =
            AutismSharedState.get().getClientTickCounter() + OBSIDIAN_COMMIT_TICKS;

        committedPlaceSupport = null;
    }

    private void commitPlace(LivingEntity enemy, PlacePlan plan) {
        committedPlaceSupport = plan.support();
        committedPlaceTarget = enemy.getId();
        committedPlaceUntil = AutismSharedState.get().getClientTickCounter() + PLACE_COMMIT_TICKS;
        committedObsidianCell = null;
    }

    private void clearPlanCommitments() {
        committedObsidianCell = null;
        committedPlaceSupport = null;
    }

    private boolean revalidateObsidianCommit(LivingEntity enemy) {
        if (committedObsidianCell == null) return false;
        if (AutismSharedState.get().getClientTickCounter() > committedObsidianUntil
            || enemy.getId() != committedObsidianTarget
            || !bool("place") || !bool("obsidian") || !hasCrystalAvailable()
            || !MC.level.getBlockState(committedObsidianCell).canBeReplaced()
            || !MC.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), committedObsidianCell,
                CollisionContext.empty())
            || !cellIsFree(committedObsidianCell,
                MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))
            || !committedObsidianMaterial()) {
            committedObsidianCell = null;
            return false;
        }

        if (committedObsidianEnemyPos != null
            && enemy.position().distanceToSqr(committedObsidianEnemyPos)
                > COMMIT_MAX_ENEMY_DRIFT_SQR) {
            committedObsidianCell = null;
            return false;
        }

        BlockPos playerPos = MC.player.blockPosition();
        BlockPos enemyPos = enemy.blockPosition();
        if (!playerPos.equals(supportScanPlayerPos) || !enemyPos.equals(supportScanEnemyPos)) {
            supportScanPlayerPos = playerPos.immutable();
            supportScanEnemyPos = enemyPos.immutable();
            List<BlockPos> supports = rankSupports(enemy, MC.player.blockInteractionRange());
            if (!supports.isEmpty()) {
                builderBanTarget = enemy.getId();
                builderBanSupport = supports.get(0);

                builderBanStallTicks = 0;
                committedObsidianCell = null;
                return false;
            }
        }
        return true;
    }

    private boolean builderBanned(LivingEntity enemy) {
        if (builderBanSupport == null || enemy.getId() != builderBanTarget) return false;
        if (!supportsCrystal(builderBanSupport)
            || !cellIsFree(builderBanSupport,
                MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))
            || !damagePasses(enemy, AutismExplosionDamage.cachedRank(enemy,
                new Vec3(builderBanSupport.getX() + 0.5D, builderBanSupport.getY() + 1.0D,
                    builderBanSupport.getZ() + 0.5D), POWER, options))) {
            builderBanSupport = null;
            return false;
        }
        return true;
    }

    private boolean committedObsidianMaterial() {
        LocalPlayer player = MC.player;
        if (committedObsidianSlot < 0) {
            return isObsidian(player.getOffhandItem()) && !AutismHandArbiter.offhandClaimedByOther(ID);
        }

        return !AutismHandArbiter.slotReserved(committedObsidianSlot, ID)
            && isObsidian(player.getInventory().getItem(committedObsidianSlot));
    }

    private ObsidianPlan aimCommittedObsidian(AutismFaceScan.Budget budget) {
        LocalPlayer player = MC.player;
        double range = player.blockInteractionRange();
        if (range <= 0.0D) return null;
        Vec3 eye = player.getEyePosition();
        boolean offhand = committedObsidianSlot < 0;
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack material = offhand ? player.getOffhandItem()
            : player.getInventory().getItem(committedObsidianSlot);
        AutismFaceScan.Request request =
            obsidianRequest(committedObsidianCell, material, hand, range, eye, aimReference())
                .budget(budget);
        AutismFaceScan.Candidate candidate =
            AutismFaceScan.best(request, new AutismFaceScan.Refusal[1]);
        if (candidate == null) return null;

        InteractionHand resolved =
            offhand ? InteractionHand.OFF_HAND : ensureMainHandForObsidian(committedObsidianSlot);
        return new ObsidianPlan(committedObsidianCell, resolved, range, candidate,
            committedObsidianDamage, committedObsidianSlot);
    }

    private boolean revalidatePlaceCommit(LivingEntity enemy) {
        if (committedPlaceSupport == null) return false;
        if (AutismSharedState.get().getClientTickCounter() > committedPlaceUntil
            || enemy.getId() != committedPlaceTarget
            || !bool("place") || !hasCrystalAvailable()
            || !supportsCrystal(committedPlaceSupport)
            || !cellIsFree(committedPlaceSupport,
                MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))) {
            committedPlaceSupport = null;
            return false;
        }

        AutismExplosionDamage.Ranking rank = AutismExplosionDamage.cachedRank(enemy,
            new Vec3(committedPlaceSupport.getX() + 0.5D, committedPlaceSupport.getY() + 1.0D,
                committedPlaceSupport.getZ() + 0.5D), POWER, options);
        if (AutismExplosionDamage.killsSelf(rank.selfDamage())
            || rank.selfDamage() > decimal("max-self-damage")) {
            committedPlaceSupport = null;
            return false;
        }
        return true;
    }

    private PlacePlan aimCommittedPlace(LivingEntity enemy, AutismFaceScan.Budget budget) {
        LocalPlayer player = MC.player;
        double range = player.blockInteractionRange();
        if (range <= 0.0D) return null;
        Vec3 eye = player.getEyePosition();
        AutismRotationUtil.Rotation wire = aimReference();
        BlockPos support = committedPlaceSupport;
        BlockPos cell = support.above();
        BlockState state = MC.level.getBlockState(support);
        AutismFaceScan.Request request = new AutismFaceScan.Request(
                cell, eye, range, AutismFaceScan.onSupport(support, ALL_FACES))
            .from(wire)
            .leadEye(eye.add(player.getDeltaMovement()))
            .budget(budget);
        List<AutismFaceScan.Option> scan = new ArrayList<>();
        supportOptions(support, state, cell, eye, wire, scan);
        AutismFaceScan.Refusal[] refusal = new AutismFaceScan.Refusal[1];
        for (AutismFaceScan.Option option : scan) {
            AutismFaceScan.Aim aim = AutismFaceScan.solve(option, request, refusal);
            if (aim == null) continue;
            AutismFaceScan.Candidate candidate = AutismFaceScan.probe(option, aim, request, refusal);
            if (candidate == null) {

                if (refusal[0] == AutismFaceScan.Refusal.NO_BUDGET) return null;
                continue;
            }

            InteractionHand hand = ensureCrystalHand();
            Vec3 source = new Vec3(support.getX() + 0.5D, support.getY() + 1.0D,
                support.getZ() + 0.5D);
            double damage =
                AutismExplosionDamage.cachedRank(enemy, source, POWER, options).targetDamage();
            return new PlacePlan(support, hand, range, candidate, request, damage);
        }
        return null;
    }

    private static boolean isCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    private boolean hasCrystalAvailable() {
        LocalPlayer player = MC.player;
        if (isCrystal(player.getOffhandItem()) && !AutismHandArbiter.offhandClaimedByOther(ID)) {
            return true;
        }
        return findCrystalHotbarSlot() >= 0;
    }

    private InteractionHand ensureCrystalHand() {
        LocalPlayer player = MC.player;

        if (isCrystal(player.getOffhandItem()) && !AutismHandArbiter.offhandClaimedByOther(ID)
            && !AutismCombatClicker.mainHandWouldPreempt()) {

            return InteractionHand.OFF_HAND;
        }
        if (isCrystal(player.getMainHandItem())) {

            switchBackTicks = integer("switch-back-delay");
            return InteractionHand.MAIN_HAND;
        }

        int slot = findCrystalHotbarSlot();
        if (slot < 0) return null;
        int held = player.getInventory().getSelectedSlot();

        if (changeHotbarSlot(slot)) {
            if (previousSlot < 0) previousSlot = held;
            switchedToSlot = slot;
        }

        switchBackTicks = integer("switch-back-delay");
        return null;
    }

    private boolean changeHotbarSlot(int slot) {
        return changeHotbarSlot(slot, false);
    }

    private boolean changeHotbarSlot(int slot, boolean defensive) {

        if (BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()
            || !primePosition() && (defensive ? AnchorAuraModule.worksThisTick()
                                              : AnchorAuraModule.reservesCombatTick())) {
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

    private int findCrystalHotbarSlot() {
        LocalPlayer player = MC.player;
        int selected = player.getInventory().getSelectedSlot();
        int best = -1;
        int bestSteps = Integer.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            if (!isCrystal(player.getInventory().getItem(slot))) continue;

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
            if (AnchorAuraModule.holdsBorrowedSlot(selected)
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

        if (!changeHotbarSlot(previousSlot)) return;
        previousSlot = -1;
        switchedToSlot = -1;
    }

    private boolean damagePasses(LivingEntity enemy, AutismExplosionDamage.Ranking rank) {
        if (AutismExplosionDamage.killsSelf(rank.selfDamage())) return false;
        if (rank.targetDamage() < decimal("min-target-damage")) return false;
        if (rank.selfDamage() > decimal("max-self-damage")) return false;

        return !bool("efficient") || rank.isEfficient();
    }

    private boolean betterRank(AutismExplosionDamage.Ranking candidate,
                               AutismExplosionDamage.Ranking incumbent) {
        int byTarget = Double.compare(candidate.targetDamage(), incumbent.targetDamage());
        if (byTarget != 0) return byTarget > 0;
        return candidate.selfDamage() < incumbent.selfDamage();
    }

    private boolean supportVisible(Vec3 eye, BlockPos support, Vec3 source) {
        HitResult hit = MC.level.clip(new ClipContext(eye, source, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, MC.player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit instanceof BlockHitResult block && block.getBlockPos().equals(support);
    }

    private boolean blockedByTerrain(Vec3 eye, Vec3 point) {
        HitResult hit = MC.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, MC.player));
        return hit.getType() != HitResult.Type.MISS;
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

    private void pushCrystalView() {
        if (!bool("crystal-view")) {
            viewOn = false;
            return;
        }
        viewSize = (float) decimal("view-size");
        viewY = (float) decimal("view-y");
        viewSpin = (float) decimal("view-spin");
        viewBounce = (float) decimal("view-bounce");
        viewOn = true;
    }

    public static boolean crystalViewActive() {
        return viewOn;
    }

    public static float crystalViewSize() {
        return viewSize;
    }

    public static float crystalViewYTranslate() {
        return viewY;
    }

    public static float crystalViewSpin() {
        return viewSpin;
    }

    public static float crystalViewBounce() {
        return viewBounce;
    }
}
