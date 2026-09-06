

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.util.AutismCombatClicker;
import autismclient.util.AutismFaceScan;
import autismclient.util.AutismFarmBlocks;
import autismclient.util.AutismFarmPlanner;
import autismclient.util.AutismFarmPlanner.Kind;
import autismclient.util.AutismFarmReplantMemory;
import autismclient.util.AutismFarmActionWatchdog;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismKeyMappingBridge;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismPathWalker;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.RegistryListCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class AutoFarmModule extends Module implements AutismSilentAim.Owner {

    private static final int ARRIVAL_SETTLE_TICKS = 2;

    private static final int SWITCH_BACK_TICKS = 2;

    private static final float ROTATION_MATCH_EPSILON = 0.05F;

    private static final int RECENT_HARVEST_MAX = 4096;

    private static final int TARGET_STICKY_TICKS = 8;

    private static final int CELL_COOLDOWN_TICKS = 80;
    private static final int CELL_DRY_LIMIT = 6;

    private static final int DRY_AFTER_ACTION_LIMIT = 2;

    private static final float AIM_ARRIVED_DEGREES = 3.0F;

    private static final int PRESTAGE_REMAINING = 3;

    private static final int CLUSTER_RADIUS = 2;
    private static final int CLUSTER_MAX = 8;

    private static final int CLUSTER_PRESTAGED_EXTRA = 4;

    private static final int ROW_SAMPLE = 8;

    private static final int ROW_MIN_LINE = 3;

    private static final double ROW_LOOKAHEAD = 1.75D;

    private static final int ROW_MAX_WATER_GAP = 2;

    private static final int ROW_PIVOT_NEAR = 2;
    private static final int ROW_PIVOT_MAX_OFFSET = 3;

    private static final int WALK_Y_BAND = 12;

    private static final int WALK_RESCAN_TICKS = 20;

    private static final int WALK_BLACKLIST_TICKS = 200;

    private static final int CONTINUATION_MAX_RING = 12;

    private static final float CONTINUATION_CONE_DEG = 90.0F;

    private static final Set<Block> TILLABLE = Set.of(
        Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);

    private static final Set<Block> SHOVEL_FLATTENABLE = Set.of(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
        Blocks.MYCELIUM);

    private static final Set<Block> AXE_STRIPPABLE = Set.of(
        Blocks.OAK_WOOD, Blocks.OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LOG,
        Blocks.PALE_OAK_WOOD, Blocks.PALE_OAK_LOG, Blocks.ACACIA_WOOD, Blocks.ACACIA_LOG,
        Blocks.CHERRY_WOOD, Blocks.CHERRY_LOG, Blocks.BIRCH_WOOD, Blocks.BIRCH_LOG,
        Blocks.JUNGLE_WOOD, Blocks.JUNGLE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LOG,
        Blocks.WARPED_STEM, Blocks.WARPED_HYPHAE, Blocks.CRIMSON_STEM, Blocks.CRIMSON_HYPHAE,
        Blocks.MANGROVE_WOOD, Blocks.MANGROVE_LOG, Blocks.BAMBOO_BLOCK);

    private record Target(Kind kind, BlockPos pos, Block crop, Item seed, int hotbarSlot,
                          AutismRotationUtil.Rotation rotation) {
    }

    private record Harvested(BlockPos pos, Block crop, int tick) {
    }

    private record SeedChoice(Block crop, Item seed, int slot) {
    }

    private final Random random = new Random();

    private final AutismFarmReplantMemory<Harvested> recentHarvest =
        new AutismFarmReplantMemory<>(RECENT_HARVEST_MAX);

    private int lastActionTick = Integer.MIN_VALUE;

    private int holdTick = Integer.MIN_VALUE;

    private boolean holdKeyAttack;

    private int noHoldUntilTick = Integer.MIN_VALUE;
    private int previousSlot = -1;

    private int farmSlot = -1;
    private int switchBackTicks;

    private int hotbarChangeTick = Integer.MIN_VALUE;

    private int switchSettleTicks;

    private BlockPos walkTarget;
    private int walkScanTick = Integer.MIN_VALUE;

    private BlockPos rowContinuation;
    private int rowContinuationTick = Integer.MIN_VALUE;

    private BlockPos continuationTarget;
    private int continuationScanTick = Integer.MIN_VALUE;

    private BlockPos stickyPos;
    private Kind stickyKind;
    private int stickyTick = Integer.MIN_VALUE;

    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap cellCooldownStamp =
        new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Block> cellCooldownBase =
        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private BlockPos dryPos;
    private Kind dryKind;
    private int dryTicks;

    private BlockPos aimMemoPos;
    private Kind aimMemoKind;
    private AutismRotationUtil.Rotation aimMemoGoal;
    private Vec3 aimMemoEye;

    private static final int NO_PROGRESS_LIMIT = 20;
    private static final int PLAN_BACKOFF_TICKS = 10;

    private BlockPos noProgPos;
    private Kind noProgKind;
    private float noProgBest = Float.MAX_VALUE;
    private int noProgTicks;

    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap planBackoffStamp =
        new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    private static final int MICRO_GAP_TICKS = 3;
    private AutismRotationUtil.Rotation lastAimGoal;
    private int lastAimTick = Integer.MIN_VALUE;

    private int lastActionKindTick = Integer.MIN_VALUE;

    private Direction.Axis sweepAxis;
    private int sweepSign = 1;

    private final int[] rowYOut = new int[1];

    private int rowLastCropY;

    private ClientLevel lastLevel;

    private String cachedCropRaw = "";
    private Set<Block> cachedCropBlocks = Set.of();

    private String lastInfo = "";

    private static int[] sphereCache = new int[0];
    private static int sphereRadius = -1;

    AutoFarmModule() {
        super("auto-farm", "AutoFarm", ModuleCategory.PLAYER, "Harvests and replants crops.");
        add(new BoolSetting("movement", "Movement", false)
            .description("Walk to distant crops")
            .build());

        add(new IntSetting("search-range", "Search Range", 16, 4, 16, 1)
            .description("Pathfinding search radius")
            .build());
        add(RegistryListSetting.crops("crops", "Crops",
                "minecraft:wheat|minecraft:carrots|minecraft:potatoes|minecraft:beetroots"
                    + "|minecraft:nether_wart|minecraft:pumpkin|minecraft:melon")
            .description("Crop blocks to farm")
            .build());
        add(new BoolSetting("grown-only", "Fully Grown Only", true)
            .description("Harvest mature crops only")
            .build());
        add(new BoolSetting("replant", "Replant", true)
            .description("Replant after harvesting")
            .build());
        add(new BoolSetting("plant", "Plant", true)
            .description("Plant bare farmland")
            .build());
        add(new BoolSetting("bonemeal", "Bonemeal", true)
            .description("Fertilize crops to grow")
            .build());
        add(new BoolSetting("fortune", "Prefer Fortune", true)
            .description("Harvest with fortune tool")
            .build());
        add(new BoolSetting("till", "Till Dirt", false)
            .description("Hoe dirt into farmland")
            .build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .description("Return to previous slot")
            .build());
    }

    @Override
    public void onEnable() {
        resetRuntime();

    }

    @Override
    public void onDisable() {
        resetRuntime();

        AutismKillAuraRotation.beginWindDown(id());
        AutismHandArbiter.releaseAll(id());
        AutismPathWalker.reset();
    }

    @Override
    public void onGameLeft() {
        resetRuntime();

        if (ownsRotation()) AutismKillAuraRotation.reset();
        AutismHandArbiter.releaseAll(id());
        AutismPathWalker.reset();
    }

    private void resetRuntime() {
        lastActionTick = Integer.MIN_VALUE;
        hotbarTick = Integer.MIN_VALUE;
        sweepTick = Integer.MIN_VALUE;
        sweepCached = null;
        grownOnlyTick = Integer.MIN_VALUE;
        searchRangeTick = Integer.MIN_VALUE;
        planningSettings = null;
        lastLevel = null;
        previousSlot = -1;
        farmSlot = -1;
        switchBackTicks = 0;
        hotbarChangeTick = Integer.MIN_VALUE;
        recentHarvest.clear();
        actionWatchdog.clear();
        walkTarget = null;
        walkScanTick = Integer.MIN_VALUE;
        rowContinuation = null;
        rowContinuationTick = Integer.MIN_VALUE;
        continuationTarget = null;
        continuationScanTick = Integer.MIN_VALUE;
        stickyPos = null;
        stickyKind = null;
        stickyTick = Integer.MIN_VALUE;
        cellCooldownStamp.clear();
        cellCooldownBase.clear();
        dryPos = null;
        dryKind = null;
        dryTicks = 0;
        solveFailPos = null;
        solveFailTicks = 0;

        aimMemoPos = null;
        aimMemoKind = null;
        aimMemoGoal = null;
        aimMemoEye = null;
        noProgPos = null;
        noProgKind = null;
        noProgBest = Float.MAX_VALUE;
        noProgTicks = 0;
        planBackoffStamp.clear();

        lastAimGoal = null;
        lastAimTick = Integer.MIN_VALUE;
        lastActionKindTick = Integer.MIN_VALUE;
        sweepAxis = null;
        sweepSign = 1;
        noHoldUntilTick = Integer.MIN_VALUE;

        scanning = false;

        releaseHoldNow();
        lastInfo = "";
    }

    @Override
    public String info() {
        return lastInfo;
    }

    @Override
    public void tick() {
        if (!isEnabled() && MC != null && MC.player != null && ownsRotation()) {
            AutismKillAuraRotation.update(id(), MC.player);
        }
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return AutismKillAuraRotation.hasCurrentRotation() && ownsRotation();
    }

    @Override
    public boolean silentCorrectionApplies() {
        if (AutismSilentAim.scaffoldOwnsRotation()) return false;
        return AutismKillAuraRotation.isWindingDown() || isEnabled() && canRun();
    }

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        if (MC == null || MC.player == null || MC.level == null || MC.gameMode == null) {
            resetRuntime();

            AutismPathWalker.reset();
            return;
        }

        if (MC.level != lastLevel) {
            lastLevel = MC.level;
            hotbarTick = Integer.MIN_VALUE;
            sweepTick = Integer.MIN_VALUE;
            sweepCached = null;
            planningSettings = null;
            recentHarvest.clear();
        actionWatchdog.clear();
            walkTarget = null;
            rowContinuation = null;
            rowContinuationTick = Integer.MIN_VALUE;
            continuationTarget = null;
            continuationScanTick = Integer.MIN_VALUE;

            walkScanTick = Integer.MIN_VALUE;
            scanning = false;

            noHoldUntilTick = Integer.MIN_VALUE;
            stickyPos = null;
            sweepAxis = null;
            cellCooldownStamp.clear();
            cellCooldownBase.clear();
            dryPos = null;
            dryKind = null;
            dryTicks = 0;
            solveFailPos = null;
            solveFailTicks = 0;

            aimMemoPos = null;
            aimMemoKind = null;
            aimMemoGoal = null;
            aimMemoEye = null;
            noProgPos = null;
            noProgKind = null;
            noProgBest = Float.MAX_VALUE;
            noProgTicks = 0;
            planBackoffStamp.clear();

            lastAimGoal = null;
            lastAimTick = Integer.MIN_VALUE;
            AutismPathWalker.reset();
            releaseHoldNow();
        }
        refreshPlanningSettings();
        pruneHarvestMemory();

        if (!canRun()) {
            standDown();
            tickSwitchBack();
            return;
        }

        Target target = findTarget();
        if (target != null) {
            switchBackTicks = 0;

            boolean walkingThrough = bool("movement") && !replantBeforeMoving && walkThrough(target);
            if (!walkingThrough) {

                AutismPathWalker.stop();
            }

            walkTarget = null;
            AutismRotationUtil.Rotation acted = tryAct(target);
            if (acted != null) {
                lastActionKindTick = AutismSharedState.get().getClientTickCounter();

                dryPos = null;
                dryTicks = 0;

                noProgPos = null;
                noProgKind = null;
                noProgBest = Float.MAX_VALUE;
                noProgTicks = 0;
                lastInfo = switch (target.kind()) {
                    case HARVEST -> "Harvest";
                    case BONEMEAL -> "Bonemeal";
                    case TILL -> "Till";
                    default -> "Replant";
                };

                lastAimGoal = acted;
                lastAimTick = AutismSharedState.get().getClientTickCounter();

                pumpRotation(acted, true);
                return;
            }
            lastInfo = "";

            releaseHoldUnlessAffirmed();

            int tickNow = AutismSharedState.get().getClientTickCounter();
            int settleAge = tickNow - hotbarChangeTick;
            boolean deliberatePause = tickNow == lastActionTick
                || settleAge >= 0 && settleAge <= switchSettleTicks + 1
                || tickNow < noHoldUntilTick;
            if (!deliberatePause) {
                noteDryTick(target, tickNow);

                noteNoProgress(target, tickNow);
            }

            lastAimGoal = target.rotation();
            lastAimTick = tickNow;
            pumpRotation(target.rotation());
            return;
        }

        lastInfo = idleHint();

        releaseHoldUnlessAffirmed();

        dryPos = null;
        dryTicks = 0;

        AutismRotationUtil.Rotation graceGoal = bool("movement") ? null : microGapGoal();
        if (bool("movement")) {
            walkTick();
        } else {

            AutismPathWalker.stop();
            walkTarget = null;
            pumpRotation(graceGoal);
        }

        if (graceGoal == null) {
            armSwitchBack();
            tickSwitchBack();
        }
    }

    private String planningSettings;

    private void refreshPlanningSettings() {
        String settings = bool("movement") + ":" + searchRange() + ":" + cropBlocks()
            + ":" + bool("grown-only") + ":" + bool("replant") + ":" + bool("plant")
            + ":" + bool("bonemeal") + ":" + bool("fortune") + ":" + bool("till");
        if (settings.equals(planningSettings)) return;
        planningSettings = settings;
        releaseHoldNow();
        AutismPathWalker.reset();
        walkTarget = null;
        continuationTarget = null;
        rowContinuation = null;
        rowContinuationTick = Integer.MIN_VALUE;
        walkScanTick = Integer.MIN_VALUE;
        continuationScanTick = Integer.MIN_VALUE;
        scanning = false;
        stickyPos = null;
        aimMemoPos = null;
        lastAimGoal = null;
        cellCooldownStamp.clear();
        cellCooldownBase.clear();
        planBackoffStamp.clear();
        grownOnlyTick = Integer.MIN_VALUE;
        grownVerdict.clear();
    }

    private AutismRotationUtil.Rotation microGapGoal() {
        if (lastAimGoal == null) return null;
        int age = AutismSharedState.get().getClientTickCounter() - lastAimTick;
        return age >= 0 && age <= MICRO_GAP_TICKS ? lastAimGoal : null;
    }

    private String idleHint() {
        if (bool("till") && !hoeAvailable()) return "No hoe";
        if (bool("replant") || bool("plant")) {
            for (Block crop : cropBlocks()) {

                if (AutismFarmBlocks.isHarvestOnly(crop)) continue;
                if (AutismFarmBlocks.seedFor(crop) == null) return "No seed";
            }
        }

        if (bool("bonemeal") && !bonemealAvailable() && bonemealWorkInReach()) return "No bonemeal";
        return "";
    }

    private boolean bonemealWorkInReach() {
        Set<Block> crops = cropBlocks();
        if (crops.isEmpty()) return false;
        Vec3 eye = MC.player.getEyePosition();
        double cullSqr = reach() * reach();
        int[] offsets = sphere(Mth.ceil(reach()) + 3);
        BlockPos origin = MC.player.blockPosition();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < offsets.length; i += 3) {
            int cx = ox + offsets[i];
            int cy = oy + offsets[i + 1];
            int cz = oz + offsets[i + 2];
            if (cellDistanceSqr(eye, cx, cy, cz) > cullSqr) continue;
            BlockState state = MC.level.getBlockState(cursor.set(cx, cy, cz));
            if (crops.contains(state.getBlock()) && bonemealable(cursor, state)) return true;
        }
        return false;
    }

    private boolean canRun() {
        if (MC == null || MC.player == null || MC.level == null || MC.gameMode == null) return false;
        if (PackHideState.isActive() || PackFreecamState.isActive()) return false;
        if (!MC.player.isAlive() || MC.player.isSpectator()) return false;

        if (AutismSilentAim.scaffoldOwnsRotation() || ScaffoldModule.reservesRageInput()) return false;

        if (MC.player.isUsingItem() || MC.player.isHandsBusy()) return false;
        if (foreignStreamOwner()) return false;

        if (MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null) return false;

        if (AutoTotemModule.operationActive() || AutoArmorModule.operationActive()) return false;
        return true;
    }

    private void standDown() {

        AutismPathWalker.stop();
        releaseHoldNow();
        walkTarget = null;
        lastInfo = "";
        armSwitchBack();
        if (ownsRotation()) {
            AutismKillAuraRotation.beginWindDown(id());
            if (MC != null && MC.player != null) AutismKillAuraRotation.update(id(), MC.player);
        }
    }

    private boolean ownsRotation() {
        return id().equals(AutismKillAuraRotation.currentOwner());
    }

    private boolean foreignStreamOwner() {
        String owner = AutismKillAuraRotation.currentOwner();
        return owner != null && !id().equals(owner);
    }

    private void pumpRotation(AutismRotationUtil.Rotation goal) {
        pumpRotation(goal, false);
    }

    private void pumpRotation(AutismRotationUtil.Rotation goal, boolean pinQuiet) {
        if (goal != null) {

            if (!foreignStreamOwner()) {
                AutismKillAuraRotation.setTarget(id(), AutismKillAuraRotation.PRIORITY_AUTO_FARM, goal);
            }
        } else if (ownsRotation()) {
            AutismKillAuraRotation.beginWindDown(id());
        }

        if (ownsRotation()) AutismKillAuraRotation.update(id(), MC.player, pinQuiet);
    }

    private double reach() {
        return MC.player.blockInteractionRange();
    }

    private int searchRangeTick = Integer.MIN_VALUE;
    private int searchRange;

    private int searchRange() {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != searchRangeTick) {
            searchRangeTick = tick;
            searchRange = integer("search-range");
        }
        return searchRange;
    }

    private Target findTarget() {
        Set<Block> crops = cropBlocks();
        if (crops.isEmpty()) {
            return null;
        }
        double reach = reach();
        Vec3 eye = MC.player.getEyePosition();

        int[] offsets = sphere(Mth.ceil(reach) + 3);

        double cullSqr = reach * reach;
        int tick = AutismSharedState.get().getClientTickCounter();
        boolean wantReplant = bool("replant");
        boolean wantPlant = bool("plant");
        int hoeSlot = bool("till") ? hoeSlot() : -1;

        boolean bonemealOffer = bool("bonemeal") && bonemealAvailable();
        BlockPos origin = MC.player.blockPosition();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        AutismRotationUtil.Rotation sweep = sweepRotation();

        BlockPos[][] candidateCells = new BlockPos[Kind.values().length][3];
        double[][] candidateKeys = new double[Kind.values().length][3];
        double[][] candidateDistances = new double[Kind.values().length][3];
        for (double[] keys : candidateKeys) java.util.Arrays.fill(keys, Double.POSITIVE_INFINITY);
        replantBeforeMoving = false;

        for (int i = 0; i < offsets.length; i += 3) {
            int cx = ox + offsets[i];
            int cy = oy + offsets[i + 1];
            int cz = oz + offsets[i + 2];
            double distanceSqr = cellDistanceSqr(eye, cx, cy, cz);
            if (distanceSqr > cullSqr) continue;
            BlockState state = MC.level.getBlockState(cursor.set(cx, cy, cz));
            Block block = state.getBlock();
            if (crops.contains(block)) {

                if (grownOk(state) && harvestMemoryAvailable(cx, cy, cz, block)
                    && !cellCooled(cx, cy, cz, tick) && !planBackoff(cx, cy, cz, tick)
                    && columnBaseOk(cursor, cx, cy, cz, block)) {
                    consider(Kind.HARVEST, cx, cy, cz, distanceSqr, sweep, eye,
                        candidateCells, candidateKeys, candidateDistances);

                } else if (bonemealOffer && !cellCooled(cx, cy, cz, tick)
                    && !planBackoff(cx, cy, cz, tick)
                    && bonemealable(cursor.set(cx, cy, cz), state)) {
                    consider(Kind.BONEMEAL, cx, cy, cz, distanceSqr, sweep, eye,
                        candidateCells, candidateKeys, candidateDistances);
                }
            } else if ((wantReplant || wantPlant) && state.canBeReplaced()) {
                if (cellCooled(cx, cy, cz, tick) || planBackoff(cx, cy, cz, tick)) continue;
                BlockState below = MC.level.getBlockState(cursor.set(cx, cy - 1, cz));

                if (wantReplant && recentChoiceAt(cx, cy, cz, below, crops, tick) != null) {
                    consider(Kind.REPLANT_RECENT, cx, cy, cz, distanceSqr, sweep, eye,
                        candidateCells, candidateKeys, candidateDistances);
                }
                if (wantPlant && plainChoice(cx, cy, cz, below, crops) != null) {
                    consider(Kind.REPLANT, cx, cy, cz, distanceSqr, sweep, eye,
                        candidateCells, candidateKeys, candidateDistances);
                }
            } else if (bool("till") && hoeAvailable() && TILLABLE.contains(block)

                && (block == Blocks.ROOTED_DIRT
                    || MC.level.getBlockState(cursor.set(cx, cy + 1, cz)).isAir())
                && !cellCooled(cx, cy, cz, tick) && !planBackoff(cx, cy, cz, tick)) {
                consider(Kind.TILL, cx, cy, cz, distanceSqr, sweep, eye,
                    candidateCells, candidateKeys, candidateDistances);
            }
        }
        java.util.List<AutismFarmPlanner.Option<Target>> options = new java.util.ArrayList<>();
        for (Kind kind : Kind.values()) {
            for (BlockPos cell : candidateCells[kind.ordinal()]) {
                if (cell == null) break;
                Target candidate = rebuildTarget(cell, kind, crops, hoeSlot, tick);
                if (candidate == null) {

                    stampPlanBackoff(cell, tick);
                    continue;
                }
                options.add(planningOption(candidate, eye));
                break;
            }
        }
        Target previous = null;
        int stickyAge = tick - stickyTick;
        if (stickyPos != null && stickyAge >= 0 && stickyAge <= TARGET_STICKY_TICKS
            && cellDistanceSqr(eye, stickyPos.getX(), stickyPos.getY(), stickyPos.getZ()) <= cullSqr
            && !cellCooled(stickyPos.getX(), stickyPos.getY(), stickyPos.getZ(), tick)
            && !planBackoff(stickyPos.getX(), stickyPos.getY(), stickyPos.getZ(), tick)) {
            previous = rebuildTarget(stickyPos, stickyKind, crops, hoeSlot, tick);
            if (previous != null) {
                Target rebuilt = previous;
                Target existing = options.stream().map(AutismFarmPlanner.Option::target)
                    .filter(t -> t.kind() == rebuilt.kind() && t.pos().equals(rebuilt.pos()))
                    .findFirst().orElse(null);
                if (existing != null) previous = existing;
                else options.add(planningOption(previous, eye));
            }
        }
        Target chosen = AutismFarmPlanner.choose(options, sweep.yaw(), sweep.pitch(),
            MC.player.getInventory().getSelectedSlot(), previous);
        if (chosen == null) {
            stickyPos = null;
            return null;
        }
        if (!chosen.pos().equals(stickyPos) || chosen.kind() != stickyKind) stickyTick = tick;
        stickyPos = chosen.pos();
        stickyKind = chosen.kind();

        rowContinuationTick = Integer.MIN_VALUE;
        if (chosen.kind() == Kind.HARVEST) {
            Target solved = buildHarvest(chosen.pos());
            if (solved != null) chosen = solved;
        }
        return chosen;
    }

    private boolean replantBeforeMoving;

    private AutismFarmPlanner.Option<Target> planningOption(Target target, Vec3 eye) {
        int slot = target.hotbarSlot();
        if (target.seed() != null) {
            BlockHitResult hit = ScaffoldModule.grimClickRay(eye, target.rotation(), reach(), MC.level, MC.player);
            if (hit != null && offhandUsable(target.seed(), hit)) slot = -1;
        }
        boolean urgent = target.kind() == Kind.REPLANT_RECENT && bool("movement")
            && replantWouldLeaveReach(target.pos(), eye);
        replantBeforeMoving |= urgent;
        return new AutismFarmPlanner.Option<>(target, target.kind(), target.rotation().yaw(),
            target.rotation().pitch(), slot, urgent);
    }

    private boolean replantWouldLeaveReach(BlockPos cell, Vec3 eye) {
        Vec3 motion = MC.player.getDeltaMovement();
        double dx = motion.x * 8.0D;
        double dz = motion.z * 8.0D;
        if (dx * dx + dz * dz < 0.04D) {
            double yaw = Math.toRadians(sweepRotation().yaw());
            dx = -Math.sin(yaw) * 1.6D;
            dz = Math.cos(yaw) * 1.6D;
        }
        return cellDistanceSqr(eye.add(dx, 0, dz), cell.getX(), cell.getY(), cell.getZ())
            > reach() * reach() - 0.5D;
    }

    private Target rebuildTarget(BlockPos pos, Kind kind, Set<Block> crops, int hoeSlot,
                                 int tick) {
        switch (kind) {
            case HARVEST: {
                BlockState state = MC.level.getBlockState(pos);
                if (!crops.contains(state.getBlock()) || !grownOk(state)
                    || !harvestMemoryAvailable(pos.getX(), pos.getY(), pos.getZ(), state.getBlock())) return null;

                if (AutismFarmBlocks.isColumnCrop(state.getBlock())
                    && !MC.level.getBlockState(pos.below()).is(state.getBlock())) return null;
                return buildHarvest(pos);
            }
            case BONEMEAL: {

                if (!bool("bonemeal")) return null;
                return bonemealOfferAt(pos) ? buildBonemeal(pos) : null;
            }
            case REPLANT_RECENT:
            case REPLANT: {

                if (kind == Kind.REPLANT_RECENT ? !bool("replant") : !bool("plant")) return null;
                if (!MC.level.getBlockState(pos).canBeReplaced()) return null;

                return buildReplant(pos, kind == Kind.REPLANT_RECENT, tick);
            }
            case TILL:
                return tillOfferAt(pos) ? buildTill(pos, hoeSlot) : null;
            default:
                return null;
        }
    }

    private boolean cellCooled(int x, int y, int z, int tick) {
        long key = BlockPos.asLong(x, y, z);
        if (!cellCooldownStamp.containsKey(key)) return false;
        int stamp = cellCooldownStamp.get(key);
        int age = tick - stamp;
        if (age < 0 || age > CELL_COOLDOWN_TICKS) {
            cellCooldownStamp.remove(key);
            cellCooldownBase.remove(key);
            return false;
        }

        Block base = cellCooldownBase.get(key);
        if (base != null
            && MC.level.getBlockState(new BlockPos(x, y - 1, z)).getBlock() != base) {
            cellCooldownStamp.remove(key);
            cellCooldownBase.remove(key);
            return false;
        }
        return true;
    }

    private static final int SOLVE_FAIL_LIMIT = 3;
    private BlockPos solveFailPos;
    private int solveFailTicks;

    private void coolCellAfterSolveFail(BlockPos pos, int tick) {
        if (pos.equals(solveFailPos)) {
            solveFailTicks++;
        } else {
            solveFailPos = pos;
            solveFailTicks = 1;
        }
        if (solveFailTicks >= SOLVE_FAIL_LIMIT) {
            coolCell(pos, tick);
            solveFailPos = null;
            solveFailTicks = 0;
        }
    }

    private void coolCell(BlockPos pos, int tick) {
        if (cellCooldownStamp.size() >= 64) {

            cellCooldownStamp.entrySet().removeIf(entry -> {
                int age = tick - entry.getValue();
                if (age >= 0 && age <= CELL_COOLDOWN_TICKS) return false;
                cellCooldownBase.remove(entry.getKey());
                return true;
            });
        }
        cellCooldownStamp.put(pos.asLong(), tick);

        cellCooldownBase.put(pos.asLong(), MC.level.getBlockState(pos.below()).getBlock());
    }

    private void noteDryTick(Target target, int tick) {

        if (foreignStreamOwner()
            || BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()
            || CrystalAuraModule.reservesCombatTick() || AnchorAuraModule.reservesCombatTick()
            || AutismBlinkManager.holdsActionsWithoutMovement()) {
            return;
        }

        if (!AutismServerRotationView.snapshot().initialized()) return;
        AutismRotationUtil.Rotation goal = target.rotation();
        AutismRotationUtil.Rotation wire = sweepRotation();
        boolean arrived =
            Math.abs(AutismRotationUtil.angleDifference(wire.yaw(), goal.yaw())) <= AIM_ARRIVED_DEGREES
                && Math.abs(wire.pitch() - goal.pitch()) <= AIM_ARRIVED_DEGREES;
        if (!arrived) {
            dryPos = null;
            dryTicks = 0;
            return;
        }
        if (target.pos().equals(dryPos) && target.kind() == dryKind) {
            dryTicks++;
        } else {
            dryPos = target.pos();
            dryKind = target.kind();
            dryTicks = 1;
        }
        if (dryTicks >= dryLimit(tick)) {
            coolCell(target.pos(), tick);
            dryPos = null;
            dryTicks = 0;
        }
    }

    private int dryLimit(int tick) {
        int actionAge = tick - lastActionKindTick;
        return actionAge >= 0 && actionAge <= DRY_AFTER_ACTION_LIMIT
            ? DRY_AFTER_ACTION_LIMIT : CELL_DRY_LIMIT;
    }

    private void noteNoProgress(Target target, int tick) {

        if (foreignStreamOwner()
            || BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()
            || CrystalAuraModule.reservesCombatTick() || AnchorAuraModule.reservesCombatTick()
            || AutismBlinkManager.holdsActionsWithoutMovement()) {
            return;
        }
        if (!AutismServerRotationView.snapshot().initialized()) return;
        AutismRotationUtil.Rotation goal = target.rotation();
        AutismRotationUtil.Rotation wire = sweepRotation();
        float distance = Math.abs(AutismRotationUtil.angleDifference(wire.yaw(), goal.yaw()))
            + Math.abs(wire.pitch() - goal.pitch());
        if (target.pos().equals(noProgPos) && target.kind() == noProgKind) {
            if (distance < noProgBest - 0.5F) {

                noProgBest = distance;
                noProgTicks = 0;
            } else {
                noProgTicks++;
            }
        } else {

            noProgPos = target.pos();
            noProgKind = target.kind();
            noProgBest = distance;
            noProgTicks = 0;
        }
        if (noProgTicks >= NO_PROGRESS_LIMIT) {
            stampPlanBackoff(target.pos(), tick);
            noProgPos = null;
            noProgKind = null;
            noProgBest = Float.MAX_VALUE;
            noProgTicks = 0;
        }
    }

    private void stampPlanBackoff(BlockPos pos, int tick) {
        planBackoffStamp.put(pos.asLong(), tick);
    }

    private boolean planBackoff(int x, int y, int z, int tick) {
        long key = BlockPos.asLong(x, y, z);
        int stamp = planBackoffStamp.getOrDefault(key, Integer.MIN_VALUE);
        if (stamp == Integer.MIN_VALUE) return false;
        int age = tick - stamp;
        if (age < 0 || age > PLAN_BACKOFF_TICKS) {
            planBackoffStamp.remove(key);
            return false;
        }
        return true;
    }

    private boolean columnBaseOk(BlockPos.MutableBlockPos cursor, int x, int y, int z, Block block) {
        return !AutismFarmBlocks.isColumnCrop(block)
            || MC.level.getBlockState(cursor.set(x, y - 1, z)).is(block);
    }

    private void consider(Kind kind, int x, int y, int z, double distanceSqr,
                                 AutismRotationUtil.Rotation sweep, Vec3 eye,
                                 BlockPos[][] cells, double[][] keys, double[][] distances) {
        int group = kind.ordinal();
        double dx = x + 0.5D - eye.x;
        double dy = y + 0.5D - eye.y;
        double dz = z + 0.5D - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        double key = Math.hypot(AutismRotationUtil.angleDifference(sweep.yaw(), yaw), pitch - sweep.pitch());
        if (kind == Kind.REPLANT_RECENT && bool("movement")
            && replantWouldLeaveReach(new BlockPos(x, y, z), eye)) key -= 360.0D;
        for (int i = 0; i < cells[group].length; i++) {
            if (key > keys[group][i] || key == keys[group][i] && distanceSqr >= distances[group][i]) continue;
            for (int j = cells[group].length - 1; j > i; j--) {
                cells[group][j] = cells[group][j - 1];
                keys[group][j] = keys[group][j - 1];
                distances[group][j] = distances[group][j - 1];
            }
            cells[group][i] = new BlockPos(x, y, z);
            keys[group][i] = key;
            distances[group][i] = distanceSqr;
            break;
        }
    }

    private AutismRotationUtil.Rotation sweepRotation() {

        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == sweepTick && sweepCached != null) return sweepCached;
        sweepTick = tick;
        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        sweepCached = wire.initialized()
            ? new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
            : AutismRotationUtil.playerRotation(MC.player);
        return sweepCached;
    }

    private boolean grownOk(BlockState state) {
        if (!grownOnly()) return true;
        return atMaxAge(state);
    }

    private boolean atMaxAge(BlockState state) {

        Boolean cached = grownVerdict.get(state);
        if (cached != null) return cached;
        boolean verdict = true;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(age.getName())) {
                verdict = state.getValue(age) >= maxAge(age);
                break;
            }
        }
        grownVerdict.put(state, verdict);
        return verdict;
    }

    private int grownOnlyTick = Integer.MIN_VALUE;
    private boolean grownOnly;
    private final Map<IntegerProperty, Integer> maxAgeByProperty = new java.util.IdentityHashMap<>();

    private final Map<BlockState, Boolean> grownVerdict = new java.util.IdentityHashMap<>();

    private boolean grownOnly() {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != grownOnlyTick) {
            grownOnlyTick = tick;
            grownOnly = bool("grown-only");
        }
        return grownOnly;
    }

    private int maxAge(IntegerProperty age) {
        return maxAgeByProperty.computeIfAbsent(age, key -> Collections.max(key.getPossibleValues()));
    }

    private Set<Block> cropBlocks() {
        String raw = value("crops");
        if (raw.equals(cachedCropRaw)) return cachedCropBlocks;
        Set<Block> blocks = new java.util.LinkedHashSet<>();
        for (String entry : list("crops")) {
            Identifier identifier = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (identifier == null) continue;
            BuiltInRegistries.BLOCK.getOptional(identifier)
                .filter(AutismFarmBlocks::isFarmable)
                .ifPresent(blocks::add);
        }
        cachedCropRaw = raw;
        cachedCropBlocks = Collections.unmodifiableSet(blocks);
        return cachedCropBlocks;
    }

    private SeedChoice plainChoice(int x, int y, int z, BlockState below, Set<Block> crops) {
        Harvested ownCell = recentHarvest.get(BlockPos.asLong(x, y, z));

        if (ownCell != null) return seedChoiceFor(ownCell.crop(), below, crops, x, y, z);

        Block preferred = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int distance = 1; distance <= 4 && preferred == null; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = x + direction.getStepX() * distance;
                int nz = z + direction.getStepZ() * distance;
                Harvested entry = recentHarvest.get(BlockPos.asLong(nx, y, nz));
                Block neighbor = entry != null ? entry.crop()
                    : MC.level.getBlockState(new BlockPos(nx, y, nz)).getBlock();
                if (crops.contains(neighbor)
                    && cropBaseMatches(neighbor, below, x, y, z)) {
                    bestDistance = distance;
                    preferred = neighbor;
                    break;
                }
            }
        }
        if (preferred != null && bestDistance <= 4) {
            return seedChoiceFor(preferred, below, crops, x, y, z);
        }

        for (Block crop : crops) {
            SeedChoice choice = seedChoiceFor(crop, below, crops, x, y, z);
            if (choice != null) return choice;
        }
        return null;
    }

    private SeedChoice seedChoiceFor(Block crop, BlockState below, Set<Block> crops, int x, int y,
                                     int z) {
        if (!crops.contains(crop) || !cropBaseMatches(crop, below, x, y, z)) return null;
        if (crop instanceof net.minecraft.world.level.block.CropBlock
            && MC.level.getRawBrightness(new BlockPos(x, y, z), 0) < 8) {
            return null;
        }
        Item seed = AutismFarmBlocks.seedFor(crop);
        return seed == null ? null : seedChoiceFrom(crop, seed);
    }

    private boolean cropBaseMatches(Block crop, BlockState below, int x, int y, int z) {
        if (crop != Blocks.COCOA) return AutismFarmBlocks.baseMatches(crop, below);
        BlockPos cell = new BlockPos(x, y, z);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (AutismFarmBlocks.baseMatches(crop, MC.level.getBlockState(cell.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    private SeedChoice seedChoiceFrom(Block crop, Item seed) {
        int slot = findItemSlot(seed);
        if (slot >= 0) return new SeedChoice(crop, seed, slot);
        return offhandHolds(seed) ? new SeedChoice(crop, seed, -1) : null;
    }

    private SeedChoice recentChoiceAt(int x, int y, int z, BlockState below, Set<Block> crops,
                                      int tick) {
        Harvested entry = recentHarvest.get(BlockPos.asLong(x, y, z));
        return entry == null ? null : seedChoiceFor(entry.crop(), below, crops, x, y, z);
    }

    private Target buildHarvest(BlockPos cell) {

        int slot = bool("fortune") ? fortuneSlot() : -1;
        AutismRotationUtil.Rotation rotation = solveHarvest(cell);

        if (rotation == null) return null;
        return new Target(Kind.HARVEST, cell, MC.level.getBlockState(cell).getBlock(), null, slot,
            rotation);
    }

    private boolean arrivedRayWorks(AutismRotationUtil.Rotation goal, Vec3 eye) {
        BlockHitResult ray = ScaffoldModule.grimClickRay(eye, goal, reach(), MC.level, MC.player);
        if (ray == null) return false;
        BlockPos cell = ray.getBlockPos();
        BlockState state = MC.level.getBlockState(cell);
        if (!cropBlocks().contains(state.getBlock()) || !grownOk(state)) return false;
        return !AutismFarmBlocks.isColumnCrop(state.getBlock())
            || MC.level.getBlockState(cell.below()).is(state.getBlock());
    }

    private Target buildReplant(BlockPos cell, boolean recent, int tick) {
        Set<Block> crops = cropBlocks();
        BlockState below = MC.level.getBlockState(cell.below());
        SeedChoice choice = recent
            ? recentChoiceAt(cell.getX(), cell.getY(), cell.getZ(), below, crops, tick)
            : plainChoice(cell.getX(), cell.getY(), cell.getZ(), below, crops);
        if (choice == null) return null;

        ItemStack stack = choice.slot() >= 0
            ? MC.player.getInventory().getItem(choice.slot()) : MC.player.getOffhandItem();
        Kind kind = recent ? Kind.REPLANT_RECENT : Kind.REPLANT;

        Vec3 eye = MC.player.getEyePosition();
        AutismRotationUtil.Rotation rotation;
        if (aimMemoPos != null && aimMemoPos.equals(cell) && aimMemoKind == kind
            && aimMemoEye.distanceTo(eye) <= 1.0D) {
            rotation = aimMemoGoal;
        } else {
            rotation = solveUse(cell, stack);
            if (rotation != null) {
                aimMemoPos = cell.immutable();
                aimMemoKind = kind;
                aimMemoGoal = rotation;
                aimMemoEye = eye;
            }
        }
        if (rotation == null) return null;
        return new Target(kind, cell, choice.crop(), choice.seed(), choice.slot(), rotation);
    }

    private Target buildTill(BlockPos cell, int hoeSlot) {
        Item hoe = hoeSlot >= 0 ? MC.player.getInventory().getItem(hoeSlot).getItem()
            : offhandHoe() ? MC.player.getOffhandItem().getItem() : null;
        if (hoe == null) return null;
        AutismRotationUtil.Rotation rotation = solveTill(cell);
        if (rotation == null) return null;
        return new Target(Kind.TILL, cell, null, hoe, hoeSlot, rotation);
    }

    private Target buildBonemeal(BlockPos cell) {
        int slot = bonemealSlot();
        if (slot < 0 && !offhandHolds(Items.BONE_MEAL)) return null;
        AutismRotationUtil.Rotation rotation = solveBonemeal(cell);

        if (rotation == null) return null;
        return new Target(Kind.BONEMEAL, cell, MC.level.getBlockState(cell).getBlock(),
            Items.BONE_MEAL, slot, rotation);
    }

    private AutismRotationUtil.Rotation solveBonemeal(BlockPos cell) {
        Vec3 eye = MC.player.getEyePosition();
        VoxelShape shape = MC.level.getBlockState(cell).getShape(MC.level, cell);
        AABB box = shape.isEmpty() ? new AABB(cell) : shape.bounds().move(cell);
        AutismRotationUtil.Rotation goal = AutismRotationUtil.lookingAt(box.getCenter(), eye);
        if (!placementPitchLegal(goal.pitch())) return null;
        return arrivedRayBonemealable(goal, eye) ? goal : null;
    }

    private boolean arrivedRayBonemealable(AutismRotationUtil.Rotation goal, Vec3 eye) {
        BlockHitResult ray = ScaffoldModule.grimClickRay(eye, goal, reach(), MC.level, MC.player);
        return ray != null && bonemealOfferAt(ray.getBlockPos());
    }

    private boolean bonemealOfferAt(BlockPos pos) {
        BlockState state = MC.level.getBlockState(pos);
        return cropBlocks().contains(state.getBlock()) && bonemealable(pos, state);
    }

    private boolean bonemealable(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (AutismFarmBlocks.isColumnCrop(block) || atMaxAge(state)) return false;
        return block instanceof BonemealableBlock b
            && b.isValidBonemealTarget(MC.level, pos, state);
    }

    private AutismRotationUtil.Rotation solveHarvest(BlockPos cell) {
        Vec3 eye = MC.player.getEyePosition();
        AutismRotationUtil.Rotation row = solveRowSweep(cell, eye);
        if (row != null) {
            return row;
        }
        Set<Block> crops = cropBlocks();
        double cullSqr = reach() * reach();
        java.util.List<BlockPos> members = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -CLUSTER_RADIUS; dx <= CLUSTER_RADIUS; dx++) {
            for (int dy = -CLUSTER_RADIUS; dy <= CLUSTER_RADIUS; dy++) {
                for (int dz = -CLUSTER_RADIUS; dz <= CLUSTER_RADIUS; dz++) {
                    int x = cell.getX() + dx;
                    int y = cell.getY() + dy;
                    int z = cell.getZ() + dz;
                    if (cellDistanceSqr(eye, x, y, z) > cullSqr) continue;
                    BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
                    if (!crops.contains(state.getBlock()) || !grownOk(state)) continue;

                    if (!columnBaseOk(cursor, x, y, z, state.getBlock())) continue;
                    members.add(new BlockPos(x, y, z));
                }
            }
        }
        members.sort(java.util.Comparator.comparingInt(member -> {
            int dx = member.getX() - cell.getX();
            int dy = member.getY() - cell.getY();
            int dz = member.getZ() - cell.getZ();
            return dx * dx + dy * dy + dz * dz;
        }));

        int bandCount = members.size();
        if (bandCount <= PRESTAGE_REMAINING) {
            foldNextPatch(cell, eye, crops, cullSqr, members);
        }
        int count = Math.min(members.size(), CLUSTER_MAX);
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;
        for (int i = 0; i < count; i++) {
            BlockPos member = members.get(i);
            sumX += member.getX() + 0.5D;
            sumY += member.getY() + 0.5D;
            sumZ += member.getZ() + 0.5D;
        }
        Vec3 centroid = new Vec3(sumX / count, sumY / count, sumZ / count);

        if (eye.distanceToSqr(centroid) <= cullSqr) {
            AutismRotationUtil.Rotation goal = AutismRotationUtil.lookingAt(centroid, eye);
            if (arrivedRayWorks(goal, eye)) {
                return goal;
            }
        }
        if (count > bandCount) {

            count = bandCount;
            sumX = 0.0D;
            sumY = 0.0D;
            sumZ = 0.0D;
            for (int i = 0; i < count; i++) {
                BlockPos member = members.get(i);
                sumX += member.getX() + 0.5D;
                sumY += member.getY() + 0.5D;
                sumZ += member.getZ() + 0.5D;
            }
            centroid = new Vec3(sumX / count, sumY / count, sumZ / count);
            if (eye.distanceToSqr(centroid) <= cullSqr) {
                AutismRotationUtil.Rotation goal = AutismRotationUtil.lookingAt(centroid, eye);
                if (arrivedRayWorks(goal, eye)) {
                    return goal;
                }
            }
        }
        VoxelShape shape = MC.level.getBlockState(cell).getShape(MC.level, cell);
        AABB box = shape.isEmpty() ? new AABB(cell) : shape.bounds().move(cell);

        AutismRotationUtil.Rotation boxGoal = AutismRotationUtil.lookingAt(box.getCenter(), eye);
        if (!arrivedRayWorks(boxGoal, eye)) return null;
        return boxGoal;
    }

    private void foldNextPatch(BlockPos cell, Vec3 eye, Set<Block> crops, double cullSqr,
                               java.util.List<BlockPos> members) {
        AutismRotationUtil.Rotation sweep = sweepRotation();
        int[] offsets = sphere(Mth.ceil(reach()) + 3);
        BlockPos origin = MC.player.blockPosition();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> beyond = new java.util.ArrayList<>();
        for (int i = 0; i < offsets.length; i += 3) {
            int x = ox + offsets[i];
            int y = oy + offsets[i + 1];
            int z = oz + offsets[i + 2];

            if (Math.abs(x - cell.getX()) <= CLUSTER_RADIUS
                && Math.abs(y - cell.getY()) <= CLUSTER_RADIUS
                && Math.abs(z - cell.getZ()) <= CLUSTER_RADIUS) continue;
            if (cellDistanceSqr(eye, x, y, z) > cullSqr) continue;
            BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
            if (!crops.contains(state.getBlock()) || !grownOk(state)) continue;
            if (!columnBaseOk(cursor, x, y, z, state.getBlock())) continue;
            beyond.add(new BlockPos(x, y, z));
        }
        beyond.sort(java.util.Comparator.comparingDouble(pos -> {
            double dx = pos.getX() + 0.5D - eye.x;
            double dy = pos.getY() + 0.5D - eye.y;
            double dz = pos.getZ() + 0.5D - eye.z;
            float toYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float toPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            return Math.abs(AutismRotationUtil.angleDifference(sweep.yaw(), toYaw))
                + Math.abs(toPitch - sweep.pitch());
        }));
        for (int i = 0; i < Math.min(CLUSTER_PRESTAGED_EXTRA, beyond.size()); i++) {
            members.add(beyond.get(i));
        }
    }

    private AutismRotationUtil.Rotation solveRowSweep(BlockPos pick, Vec3 eye) {
        Set<Block> crops = cropBlocks();
        int px = pick.getX();
        int py = pick.getY();
        int pz = pick.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int countX = 0;
        int countZ = 0;
        for (int d = -ROW_SAMPLE; d <= ROW_SAMPLE; d++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (crops.contains(MC.level.getBlockState(cursor.set(px + d, py + dy, pz)).getBlock())) countX++;
                if (crops.contains(MC.level.getBlockState(cursor.set(px, py + dy, pz + d)).getBlock())) countZ++;
            }
        }
        if (Math.max(countX, countZ) < ROW_MIN_LINE) return null;
        Direction.Axis axis;
        if (countX == countZ) {
            axis = sweepAxis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X;
        } else {
            axis = countX > countZ ? Direction.Axis.X : Direction.Axis.Z;
        }
        int along = axis == Direction.Axis.X ? px : pz;
        int perp = axis == Direction.Axis.X ? pz : px;

        int sign;
        if (sweepAxis == axis) {

            sign = sweepSign;
        } else {

            sign = wireSignAlong(axis);
            if (sign == 0) sign = 1;
        }
        int end = rowEnd(cursor, along, perp, py, axis, sign, crops, rowYOut);
        if (end == Integer.MIN_VALUE && sweepAxis != axis) {

            sign = -sign;
            end = rowEnd(cursor, along, perp, py, axis, sign, crops, rowYOut);
        }
        if (end == Integer.MIN_VALUE) {
            return solveRowPivot(cursor, along, perp, py, axis, sign, crops, eye);
        }

        if ((end - along) * sign <= PRESTAGE_REMAINING) {
            AutismRotationUtil.Rotation pivot =
                solveRowPivot(cursor, along, perp, py, axis, sign, crops, eye);
            if (pivot != null) return pivot;

            rowEnd(cursor, along, perp, py, axis, sign, crops, rowYOut);
        }
        sweepAxis = axis;
        sweepSign = sign;

        AutismRotationUtil.Rotation goal = rowGoal(along, perp, rowYOut[0], axis, sign, end, eye);

        if (goal != null && !arrivedRayWorks(goal, eye)) goal = null;

        if (goal != null) stampRowContinuation(end, perp, axis);
        return goal;
    }

    private AutismRotationUtil.Rotation solveRowPivot(BlockPos.MutableBlockPos cursor, int along,
                                                      int perp, int py, Direction.Axis axis,
                                                      int sign, Set<Block> crops, Vec3 eye) {
        for (int offset = 1; offset <= ROW_PIVOT_MAX_OFFSET; offset++) {
            for (int side = -1; side <= 1; side += 2) {
                int rowPerp = perp + side * offset;
                int anchor = Integer.MIN_VALUE;
                int anchorDist = Integer.MAX_VALUE;
                for (int da = -ROW_PIVOT_NEAR; da <= ROW_PIVOT_NEAR; da++) {
                    if (rowCropY(cursor, along + da, rowPerp, py, axis, crops) == Integer.MIN_VALUE) {
                        continue;
                    }
                    if (Math.abs(da) < anchorDist) {
                        anchorDist = Math.abs(da);
                        anchor = along + da;
                    }
                }
                if (anchor == Integer.MIN_VALUE) continue;
                int newSign = -sign;
                int end = rowEnd(cursor, anchor, rowPerp, py, axis, newSign, crops, rowYOut);
                if (end == Integer.MIN_VALUE) continue;

                AutismRotationUtil.Rotation goal = rowGoal(anchor, rowPerp, rowYOut[0], axis, newSign, end, eye);

                if (goal != null && !arrivedRayWorks(goal, eye)) goal = null;
                if (goal == null) {

                    return null;
                }
                sweepAxis = axis;
                sweepSign = newSign;

                stampRowContinuation(end, rowPerp, axis);
                return goal;
            }
        }
        return null;
    }

    private void stampRowContinuation(int end, int perp, Direction.Axis axis) {
        rowContinuation = axis == Direction.Axis.X
            ? new BlockPos(end, rowLastCropY, perp) : new BlockPos(perp, rowLastCropY, end);
        rowContinuationTick = AutismSharedState.get().getClientTickCounter();
    }

    private int rowEnd(BlockPos.MutableBlockPos cursor, int along, int perp, int py,
                       Direction.Axis axis, int sign, Set<Block> crops, int[] nextY) {
        int end = Integer.MIN_VALUE;
        int gap = 0;
        boolean gapAllWater = true;
        for (int step = 1; step <= ROW_SAMPLE; step++) {
            int a = along + sign * step;
            int cropY = rowCropY(cursor, a, perp, py, axis, crops);
            if (cropY != Integer.MIN_VALUE) {

                if (end == Integer.MIN_VALUE) nextY[0] = cropY;
                end = a;

                rowLastCropY = cropY;
                gap = 0;
                gapAllWater = true;
                continue;
            }
            gapAllWater = gapAllWater && rowWaterAt(cursor, a, perp, py, axis);
            if (++gap > (gapAllWater ? ROW_MAX_WATER_GAP : 1)) break;
        }
        return end;
    }

    private int rowCropY(BlockPos.MutableBlockPos cursor, int a, int perp, int py,
                         Direction.Axis axis, Set<Block> crops) {
        for (int dy = -1; dy <= 1; dy++) {
            if (crops.contains(MC.level.getBlockState(rowSet(cursor, a, perp, py + dy, axis)).getBlock())) {
                return py + dy;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean rowWaterAt(BlockPos.MutableBlockPos cursor, int a, int perp, int py,
                               Direction.Axis axis) {
        for (int dy = -1; dy <= 1; dy++) {
            Block block = MC.level.getBlockState(rowSet(cursor, a, perp, py + dy, axis)).getBlock();
            if (block == Blocks.WATER || block == Blocks.LILY_PAD) return true;
        }
        return false;
    }

    private static BlockPos.MutableBlockPos rowSet(BlockPos.MutableBlockPos cursor, int a,
                                                   int perp, int y, Direction.Axis axis) {
        return axis == Direction.Axis.X ? cursor.set(a, y, perp) : cursor.set(perp, y, a);
    }

    private AutismRotationUtil.Rotation rowGoal(int along, int perp, int y, Direction.Axis axis,
                                                int sign, int end, Vec3 eye) {
        double goal = along + sign * ROW_LOOKAHEAD;
        goal = sign > 0 ? Math.min(goal, end + 1.0D) : Math.max(goal, end - 1.0D);
        Vec3 point = axis == Direction.Axis.X
            ? new Vec3(goal + 0.5D, y + 0.5D, perp + 0.5D)
            : new Vec3(perp + 0.5D, y + 0.5D, goal + 0.5D);
        if (eye.distanceToSqr(point) > reach() * reach()) return null;
        return AutismRotationUtil.lookingAt(point, eye);
    }

    private int wireSignAlong(Direction.Axis axis) {
        float yaw = sweepRotation().yaw();

        double component = axis == Direction.Axis.X
            ? -Math.sin(Math.toRadians(yaw)) : Math.cos(Math.toRadians(yaw));
        return component > 0.05D ? 1 : component < -0.05D ? -1 : 0;
    }

    private AutismRotationUtil.Rotation solveUse(BlockPos cell, ItemStack seedStack) {
        AutismFaceScan.Candidate candidate = AutismFaceScan.best(
            new AutismFaceScan.Request(cell, MC.player.getEyePosition(), reach(),
                AutismFaceScan.blockItem(seedStack, MC.player, InteractionHand.MAIN_HAND))

                .from(sweepRotation())
                .pitchLimit(AutismFaceScan.goalPitchLimit())

                .sneaking(MC.player.isSecondaryUseActive())
                .sneakAllowed(false)
                .budget(new AutismFaceScan.Budget(AutismFaceScan.DEFAULT_TICK_RAY_BUDGET)));
        return candidate == null ? null : candidate.aim().goal();
    }

    private AutismRotationUtil.Rotation solveTill(BlockPos dirt) {
        AutismRotationUtil.Rotation rotation = solveTillAt(dirt, dirt.above());
        if (rotation != null) return rotation;
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        sortTillSides(sides, dirt);
        for (Direction side : sides) {
            rotation = solveTillAt(dirt, dirt.relative(side));
            if (rotation != null) return rotation;
        }
        return null;
    }

    private AutismRotationUtil.Rotation solveTillAt(BlockPos dirt, BlockPos cell) {
        AutismFaceScan.Candidate candidate = AutismFaceScan.best(
            new AutismFaceScan.Request(cell, MC.player.getEyePosition(), reach(),
                (hit, target) -> hit.getBlockPos().equals(dirt)
                    && hit.getDirection() != Direction.DOWN)
                .from(sweepRotation())
                .pitchLimit(AutismFaceScan.goalPitchLimit())
                .sneaking(MC.player.isSecondaryUseActive())
                .sneakAllowed(false)
                .budget(new AutismFaceScan.Budget(AutismFaceScan.DEFAULT_TICK_RAY_BUDGET)));
        return candidate == null ? null : candidate.aim().goal();
    }

    private void sortTillSides(Direction[] sides, BlockPos dirt) {
        Vec3 eye = MC.player.getEyePosition();
        float wire = sweepRotation().yaw();
        float[] keys = new float[sides.length];
        for (int i = 0; i < sides.length; i++) {
            keys[i] = Math.abs(AutismRotationUtil.angleDifference(wire,
                AutismRotationUtil.lookingAt(Vec3.atCenterOf(dirt.relative(sides[i])), eye).yaw()));
        }
        for (int i = 1; i < sides.length; i++) {
            Direction side = sides[i];
            float key = keys[i];
            int j = i - 1;
            while (j >= 0 && keys[j] > key) {
                sides[j + 1] = sides[j];
                keys[j + 1] = keys[j];
                j--;
            }
            sides[j + 1] = side;
            keys[j + 1] = key;
        }
    }

    private boolean tillOfferAt(BlockPos pos) {
        Block block = MC.level.getBlockState(pos).getBlock();
        if (!TILLABLE.contains(block)) return false;
        return block == Blocks.ROOTED_DIRT || MC.level.getBlockState(pos.above()).isAir();
    }

    private AutismRotationUtil.Rotation tryAct(Target target) {
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == lastActionTick) return null;

        if (BedDefenderModule.ownsSilentRotation()) return null;
        if (SurroundModule.ownsSilentRotation()) return null;
        if (CrystalAuraModule.reservesCombatTick()) return null;
        if (AnchorAuraModule.reservesCombatTick()) return null;

        if (foreignStreamOwner()) return null;

        int switchAge = tick - hotbarChangeTick;
        if (switchAge >= 0 && switchAge <= switchSettleTicks) return null;

        if (tick < noHoldUntilTick) return null;

        if (AutismBlinkManager.holdsActionsWithoutMovement()) return null;

        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        if (!wire.initialized()) return null;
        AutismRotationUtil.Rotation wireRotation =
            new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch());

        if (target.kind() != Kind.HARVEST && !placementPitchLegal(wireRotation.pitch())) return null;

        AutismRotationUtil.Rotation silent = AutismSilentAim.activeOutgoingRotation(MC.player);
        if (silent != null && !sameRotation(silent, wireRotation)) return null;

        BlockHitResult ray = ScaffoldModule.grimClickRay(
            MC.player.getEyePosition(), wireRotation, reach(), MC.level, MC.player);
        if (ray == null) return null;

        return switch (target.kind()) {
            case HARVEST -> tryHarvest(target, ray, wireRotation, tick);
            case BONEMEAL -> tryBonemeal(target, ray, wireRotation, tick);
            case REPLANT, REPLANT_RECENT -> tryReplant(target, ray, wireRotation, tick);
            case TILL -> tryTill(target, ray, wireRotation, tick);
        };
    }

    private AutismRotationUtil.Rotation tryHarvest(Target target, BlockHitResult ray,
                                                   AutismRotationUtil.Rotation wireRotation,
                                                   int tick) {
        BlockPos cell = ray.getBlockPos();
        BlockState state = MC.level.getBlockState(cell);
        if (!cropBlocks().contains(state.getBlock()) || !grownOk(state)) return null;

        if (AutismFarmBlocks.isColumnCrop(state.getBlock())
            && !MC.level.getBlockState(cell.below()).is(state.getBlock())) return null;
        if ((bool("replant") || bool("plant")) && AutismFarmBlocks.seedFor(state.getBlock()) != null
            && !recentHarvest.hasRoomFor(cell.asLong())) return null;
        if (target.hotbarSlot() >= 0) {

            if (!ensureHand(target.hotbarSlot())) return null;

            if (fortuneLevel(MC.player.getMainHandItem()) <= 0) return null;
        }

        if (!actionProgressAllowed(target, cell, state, tick)) return null;
        releaseOppositeHold(true);

        if (!AutismCombatClicker.holdAttack(ray)) return null;

        book(tick);
        holdTick = tick;
        holdKeyAttack = true;

        rememberHarvest(cell, state.getBlock(), tick);
        return wireRotation;
    }

    private AutismRotationUtil.Rotation tryReplant(Target target, BlockHitResult ray,
                                                   AutismRotationUtil.Rotation wireRotation,
                                                   int tick) {

        boolean offhand = offhandUsable(target.seed(), ray);
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!offhand && target.hotbarSlot() < 0) {

            if (MC.player.getOffhandItem().is(target.seed())
                && !AutismHandArbiter.offhandClaimedByOther(id())) {
                clearPreemptingHand(ray);
            }
            return null;
        }

        ItemStack planStack = offhand ? MC.player.getOffhandItem()
            : target.hotbarSlot() >= 0 ? MC.player.getInventory().getItem(target.hotbarSlot())
            : ItemStack.EMPTY;
        if (!planStack.is(target.seed())) return null;
        BlockPlaceContext context = new BlockPlaceContext(MC.player, hand, planStack, ray);
        if (!context.canPlace()) return null;

        BlockPos cell = context.getClickedPos();
        if (!MC.level.getBlockState(cell).canBeReplaced()) return null;
        BlockState below = MC.level.getBlockState(cell.below());
        SeedChoice actualChoice = bool("replant")
            ? recentChoiceAt(cell.getX(), cell.getY(), cell.getZ(), below, cropBlocks(), tick) : null;
        if (actualChoice == null && bool("plant")) {
            actualChoice = plainChoice(cell.getX(), cell.getY(), cell.getZ(), below, cropBlocks());
        }
        if (actualChoice == null || actualChoice.crop() != target.crop()
            || actualChoice.seed() != target.seed()) return null;

        BlockState placed = target.crop().getStateForPlacement(context);
        if (placed == null || !placed.canSurvive(MC.level, cell)
            || !MC.level.isUnobstructed(placed, cell, CollisionContext.placementContext(MC.player))) {
            return null;
        }

        BlockPos clicked = ray.getBlockPos();
        if (!AutismFaceScan.isPlaceableSupport(
                MC.level.getBlockState(clicked), clicked, MC.player.isSecondaryUseActive())) {
            return null;
        }
        if (!offhand) {
            if (!ensureHand(target.hotbarSlot())) return null;
            if (!MC.player.getMainHandItem().is(target.seed())) return null;
        }
        if (ModuleRegistry.shouldCancelUseExcept(ray, hand, id())) return null;
        if (!usePressWindowOpen()) return null;

        releaseOppositeHold(false);

        if (offhand && !offhandUsable(target.seed(), ray)) return null;

        if (!actionProgressAllowed(target, cell, MC.level.getBlockState(cell), tick)) return null;
        if (!AutismCombatClicker.holdUse(ray, hand)) return null;
        book(tick);
        holdTick = tick;
        holdKeyAttack = false;
        return wireRotation;
    }

    private boolean mainHandWouldPreempt(BlockHitResult ray) {
        return wouldPreempt(MC.player.getMainHandItem(), ray);
    }

    private boolean wouldPreempt(ItemStack main, BlockHitResult ray) {

        BlockState clickedState = MC.level.getBlockState(ray.getBlockPos());
        if (!MC.player.isSecondaryUseActive()
            && clickedState.is(Blocks.SWEET_BERRY_BUSH)
            && clickedState.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) > 1
            && !main.is(Items.BONE_MEAL)) return true;
        if (main.isEmpty()) return false;
        if (MC.player.getCooldowns().isOnCooldown(main)) return false;
        Item item = main.getItem();

        if (item instanceof net.minecraft.world.item.BlockItem) return true;
        if (main.getUseAnimation() != net.minecraft.world.item.ItemUseAnimation.NONE) return true;

        if (main.has(net.minecraft.core.component.DataComponents.CONSUMABLE)
            || main.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)
            || main.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
            || main.has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
            return true;
        }

        if (main.is(Items.EXPERIENCE_BOTTLE) || main.is(Items.ENDER_PEARL)
            || main.is(Items.SNOWBALL) || main.is(Items.EGG) || main.is(Items.FIREWORK_ROCKET)
            || main.is(Items.FISHING_ROD)
            || item instanceof net.minecraft.world.item.BucketItem
            || main.is(Items.WIND_CHARGE) || main.is(Items.FIRE_CHARGE)
            || main.is(Items.ENDER_EYE) || main.is(Items.SPLASH_POTION)
            || main.is(Items.LINGERING_POTION) || main.is(Items.FLINT_AND_STEEL)
            || main.is(Items.BONE_MEAL) || main.is(Items.GLASS_BOTTLE)
            || main.is(Items.CARROT_ON_A_STICK)) {
            return true;
        }

        if (item instanceof net.minecraft.world.item.SpawnEggItem
            || item instanceof net.minecraft.world.item.BoatItem
            || item instanceof net.minecraft.world.item.MinecartItem
            || item instanceof net.minecraft.world.item.HangingEntityItem
            || item instanceof net.minecraft.world.item.ArmorStandItem
            || item instanceof net.minecraft.world.item.EndCrystalItem
            || item instanceof net.minecraft.world.item.PotionItem
            || item instanceof net.minecraft.world.item.MapItem
            || item instanceof net.minecraft.world.item.EmptyMapItem
            || item instanceof net.minecraft.world.item.CompassItem
            || item instanceof net.minecraft.world.item.HoneycombItem
            || item instanceof net.minecraft.world.item.LeadItem
            || item instanceof net.minecraft.world.item.BundleItem
            || item instanceof net.minecraft.world.item.KnowledgeBookItem
            || item instanceof net.minecraft.world.item.WritableBookItem
            || item instanceof net.minecraft.world.item.WrittenBookItem
            || item instanceof net.minecraft.world.item.DebugStickItem
            || item instanceof net.minecraft.world.item.FoodOnAStickItem) {
            return true;
        }

        if (item instanceof HoeItem) return hoeWouldFire(ray);
        if (item instanceof AxeItem) return axeWouldFire(ray.getBlockPos());
        if (item instanceof ShovelItem) return shovelWouldFire(ray);
        if (item instanceof ShearsItem) return shearsWouldFire(ray.getBlockPos());

        return item.getClass() != Item.class && !(item instanceof net.minecraft.world.item.MaceItem);
    }

    private boolean hoeWouldFire(BlockHitResult ray) {
        if (!tillOfferAt(ray.getBlockPos())) return false;
        return MC.level.getBlockState(ray.getBlockPos()).is(Blocks.ROOTED_DIRT)
            || ray.getDirection() != Direction.DOWN;
    }

    private boolean axeWouldFire(BlockPos pos) {
        BlockState state = MC.level.getBlockState(pos);
        return AXE_STRIPPABLE.contains(state.getBlock())
            || WeatheringCopper.getPrevious(state).isPresent()
            || net.minecraft.world.item.HoneycombItem.WAX_OFF_BY_BLOCK.get()
                .containsKey(state.getBlock());
    }

    private boolean shovelWouldFire(BlockHitResult ray) {
        if (ray.getDirection() == Direction.DOWN) return false;
        BlockPos pos = ray.getBlockPos();
        BlockState state = MC.level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            return true;
        }
        return SHOVEL_FLATTENABLE.contains(state.getBlock())
            && MC.level.getBlockState(pos.above()).isAir();
    }

    private boolean shearsWouldFire(BlockPos pos) {
        BlockState state = MC.level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof PumpkinBlock) return true;
        if (block instanceof BeehiveBlock
            && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
            return true;
        }
        return block instanceof GrowingPlantHeadBlock plant && !plant.isMaxAge(state);
    }

    private boolean clearPreemptingHand(BlockHitResult ray) {
        int selected = MC.player.getInventory().getSelectedSlot();
        int fortune = bool("fortune") ? fortuneSlot() : -1;
        if (fortune >= 0 && fortune != selected
            && !wouldPreempt(MC.player.getInventory().getItem(fortune), ray)) {
            if (!changeHotbarSlot(fortune, selected)) return false;
            farmSlot = fortune;
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected || AutismHandArbiter.slotReserved(slot, id())) continue;
            if (!wouldPreempt(MC.player.getInventory().getItem(slot), ray)) {
                if (!changeHotbarSlot(slot, selected)) return false;
                farmSlot = slot;
                return true;
            }
        }
        return false;
    }

    private AutismRotationUtil.Rotation tryTill(Target target, BlockHitResult ray,
                                                AutismRotationUtil.Rotation wireRotation,
                                                int tick) {
        if (!hoeWouldFire(ray)) return null;
        ItemStack offStack = MC.player.getOffhandItem();
        boolean offhand = isHoe(offStack) && offhandUsable(offStack.getItem(), ray);
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!offhand) {
            if (target.hotbarSlot() < 0) {
                if (offhandHoe()) clearPreemptingHand(ray);
                return null;
            }
            if (!ensureHand(target.hotbarSlot())) return null;
            if (!isHoe(MC.player.getMainHandItem())) return null;
        }
        if (MC.player.getCooldowns().isOnCooldown(MC.player.getItemInHand(hand))) return null;
        if (ModuleRegistry.shouldCancelUseExcept(ray, hand, id())) return null;
        if (!usePressWindowOpen()) return null;

        releaseOppositeHold(false);
        if (offhand && !offhandUsable(offStack.getItem(), ray)) return null;
        if (!actionProgressAllowed(target, ray.getBlockPos(), MC.level.getBlockState(ray.getBlockPos()), tick)) {
            return null;
        }
        if (!AutismCombatClicker.holdUse(ray, hand)) return null;
        book(tick);
        holdTick = tick;
        holdKeyAttack = false;
        return wireRotation;
    }

    private AutismRotationUtil.Rotation tryBonemeal(Target target, BlockHitResult ray,
                                                    AutismRotationUtil.Rotation wireRotation,
                                                    int tick) {

        if (!bonemealOfferAt(ray.getBlockPos())) return null;

        boolean offhand = offhandUsable(Items.BONE_MEAL, ray);
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!offhand) {
            if (target.hotbarSlot() < 0) {

                if (MC.player.getOffhandItem().is(Items.BONE_MEAL)
                    && !AutismHandArbiter.offhandClaimedByOther(id())) {
                    clearPreemptingHand(ray);
                }
                return null;
            }
            if (!ensureHand(target.hotbarSlot())) return null;

            if (!MC.player.getMainHandItem().is(Items.BONE_MEAL)) return null;
        }
        if (ModuleRegistry.shouldCancelUseExcept(ray, hand, id())) return null;
        if (!usePressWindowOpen()) return null;

        releaseOppositeHold(false);

        if (offhand && !offhandUsable(Items.BONE_MEAL, ray)) return null;
        if (MC.player.getCooldowns().isOnCooldown(MC.player.getItemInHand(hand))) return null;
        if (!actionProgressAllowed(target, ray.getBlockPos(), MC.level.getBlockState(ray.getBlockPos()), tick)) {
            return null;
        }
        if (!AutismCombatClicker.holdUse(ray, hand)) return null;
        book(tick);
        holdTick = tick;
        holdKeyAttack = false;
        return wireRotation;
    }

    private boolean usePressWindowOpen() {
        boolean useKeyDown = AutismCombatClicker.holding() && !holdKeyAttack;
        return useKeyDown || ((AutismMinecraftAccessor) MC).autism$getRightClickDelay() <= 0;
    }

    private void book(int tick) {
        lastActionTick = tick;
    }

    private void releaseOppositeHold(boolean attack) {
        if (AutismCombatClicker.holding() && holdKeyAttack != attack) {
            AutismCombatClicker.releaseHold();
        }
    }

    private void releaseHoldNow() {
        AutismCombatClicker.releaseHold();
        holdTick = Integer.MIN_VALUE;
    }

    private void releaseHoldUnlessAffirmed() {
        if (holdTick != AutismSharedState.get().getClientTickCounter()) releaseHoldNow();
    }

    private boolean harvestMemoryAvailable(int x, int y, int z, Block crop) {
        return !(bool("replant") || bool("plant")) || AutismFarmBlocks.seedFor(crop) == null
            || recentHarvest.hasRoomFor(BlockPos.asLong(x, y, z));
    }

    private void rememberHarvest(BlockPos pos, Block crop, int tick) {
        uncoolAround(pos);
        if ((bool("replant") || bool("plant")) && AutismFarmBlocks.seedFor(crop) != null) {
            recentHarvest.remember(pos.asLong(), new Harvested(pos.immutable(), crop, tick));
        }
    }

    private void pruneHarvestMemory() {
        int tick = AutismSharedState.get().getClientTickCounter();
        recentHarvest.values().removeIf(entry -> {
            BlockPos pos = entry.pos();
            if (!MC.level.hasChunkAt(pos) || tick == entry.tick()) return false;
            BlockState state = MC.level.getBlockState(pos);
            if (state.is(entry.crop())) return !atMaxAge(state);
            if (!state.canBeReplaced()) return true;
            return !cropBaseMatches(entry.crop(), MC.level.getBlockState(pos.below()),
                pos.getX(), pos.getY(), pos.getZ());
        });
    }

    private record AttemptState(Kind kind, BlockState state) {}
    private final AutismFarmActionWatchdog<AttemptState> actionWatchdog = new AutismFarmActionWatchdog<>();

    private boolean actionProgressAllowed(Target target, BlockPos cell, BlockState state, int tick) {
        int limit = 40;
        if (target.kind() == Kind.HARVEST) {
            float progress = state.getDestroyProgress(MC.player, MC.level, cell);
            if (progress > 0 && Float.isFinite(progress)) {
                limit += (int) Math.min(1200, Math.ceil(1.0D / progress));
            }
        }
        if (actionWatchdog.allow(cell.asLong(), new AttemptState(target.kind(), state), tick, limit)) {
            return true;
        }
        coolCell(cell, tick);
        coolCell(target.pos(), tick);
        releaseHoldNow();
        return false;
    }

    private void uncoolAround(BlockPos pos) {
        if (cellCooldownStamp.isEmpty()) return;
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();
        cellCooldownStamp.keySet().removeIf(key -> {
            if (Math.abs(BlockPos.getX(key) - px) > CLUSTER_RADIUS
                || Math.abs(BlockPos.getY(key) - py) > CLUSTER_RADIUS
                || Math.abs(BlockPos.getZ(key) - pz) > CLUSTER_RADIUS) {
                return false;
            }

            cellCooldownBase.remove(key);
            return true;
        });
    }

    @Override
    public boolean shouldCancelUse(net.minecraft.world.phys.HitResult hitResult, InteractionHand hand) {
        return holdOwnsKey();
    }

    @Override
    public boolean shouldCancelAttack(net.minecraft.world.phys.HitResult hitResult) {

        if (holdOwnsKey()) return true;
        return hitResult instanceof net.minecraft.world.phys.EntityHitResult
            && id().equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    private boolean holdOwnsKey() {
        if (holdTick == Integer.MIN_VALUE || !AutismCombatClicker.holding()) return false;
        int tick = AutismSharedState.get().getClientTickCounter();

        int age = tick - holdTick;
        return age >= 0 && age <= 1;
    }

    private static boolean placementPitchLegal(float pitch) {
        return ScaffoldModule.grimPlacementPitchLegal(Math.abs(pitch));
    }

    private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {

        float epsilon = (float) Math.max(ROTATION_MATCH_EPSILON,
            AutismRotationUtil.sensitivityGcd() + ROTATION_MATCH_EPSILON);
        return Math.abs(AutismRotationUtil.angleDifference(first.yaw(), second.yaw())) <= epsilon
            && Math.abs(first.pitch() - second.pitch()) <= epsilon;
    }

    private boolean ensureHand(int slot) {
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected == slot) return true;
        if (!changeHotbarSlot(slot, selected)) return false;
        farmSlot = slot;
        return false;
    }

    private boolean changeHotbarSlot(int slot, int selected) {

        if (BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()
            || CrystalAuraModule.reservesCombatTick() || AnchorAuraModule.reservesCombatTick()) {
            return false;
        }

        if (foreignStreamOwner()) return false;
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == hotbarChangeTick) return false;
        if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
        try {

            if (previousSlot < 0) previousSlot = selected;
            if (slot < 0 || slot >= MC.options.keyHotbarSlots.length
                || AutismHandArbiter.slotReserved(slot, id())) return false;

            var key = AutismKeyMappingBridge.of(MC.options.keyHotbarSlots[slot]);
            key.autism$simulatePress(true);
            key.autism$simulatePress(false);
            key.autism$resetPressedState();
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
        hotbarChangeTick = tick;

        switchSettleTicks = random.nextInt(3);
        return true;
    }

    private int findItemSlot(Item item) {
        refreshHotbar();
        return cachedItemSlots.getOrDefault(item, -1);
    }

    private int hotbarTick = Integer.MIN_VALUE;

    private int sweepTick = Integer.MIN_VALUE;
    private AutismRotationUtil.Rotation sweepCached;
    private int cachedHoeSlot = -1;
    private int cachedFortuneSlot = -1;

    private Item cachedOffhandItem;
    private final Map<Item, Integer> cachedItemSlots = new HashMap<>();

    private void refreshHotbar() {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == hotbarTick) return;
        hotbarTick = tick;
        cachedHoeSlot = -1;
        cachedFortuneSlot = -1;
        cachedItemSlots.clear();
        int selected = MC.player.getInventory().getSelectedSlot();
        int bestFortuneDistance = Integer.MAX_VALUE;
        boolean bestFortuneHoe = false;
        for (int slot = 0; slot < 9; slot++) {
            if (AutismHandArbiter.slotReserved(slot, id())) continue;
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int distance = Math.abs(slot - selected);
            cachedItemSlots.merge(stack.getItem(), slot,
                (oldSlot, newSlot) -> Math.abs(newSlot - selected) < Math.abs(oldSlot - selected)
                    ? newSlot : oldSlot);
            boolean hoe = isHoe(stack);
            if (hoe && (cachedHoeSlot < 0 || distance < Math.abs(cachedHoeSlot - selected))) {
                cachedHoeSlot = slot;
            }

            if (fortuneLevel(stack) > 0
                && (cachedFortuneSlot < 0 || hoe && !bestFortuneHoe
                    || hoe == bestFortuneHoe && distance < bestFortuneDistance)) {
                cachedFortuneSlot = slot;
                bestFortuneHoe = hoe;
                bestFortuneDistance = distance;
            }
        }

        ItemStack offhand = MC.player.getOffhandItem();
        cachedOffhandItem = offhand.isEmpty() || AutismHandArbiter.offhandClaimedByOther(id())
            ? null : offhand.getItem();
    }

    private boolean offhandHolds(Item item) {
        refreshHotbar();
        return cachedOffhandItem == item;
    }

    private boolean offhandUsable(Item item, BlockHitResult ray) {
        return MC.player.getOffhandItem().is(item)
            && !AutismHandArbiter.offhandClaimedByOther(id())
            && !mainHandWouldPreempt(ray);
    }

    private boolean offhandHoe() {
        return !AutismHandArbiter.offhandClaimedByOther(id()) && isHoe(MC.player.getOffhandItem());
    }

    private boolean hoeAvailable() {
        return hoeSlot() >= 0 || offhandHoe();
    }

    private int hoeSlot() {
        refreshHotbar();
        return cachedHoeSlot;
    }

    private int bonemealSlot() {
        return findItemSlot(Items.BONE_MEAL);
    }

    private boolean bonemealAvailable() {
        return offhandHolds(Items.BONE_MEAL) || bonemealSlot() >= 0;
    }

    private int fortuneSlot() {
        refreshHotbar();
        return cachedFortuneSlot;
    }

    private static boolean isHoe(ItemStack stack) {
        return stack.getItem() instanceof HoeItem;
    }

    private net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> fortuneHolder;
    private net.minecraft.client.multiplayer.ClientLevel fortuneHolderLevel;

    private int fortuneLevel(ItemStack stack) {
        try {
            if (MC == null || MC.level == null || stack == null || stack.isEmpty()) return 0;
            if (fortuneHolderLevel != MC.level) {
                fortuneHolderLevel = MC.level;
                fortuneHolder = MC.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            }
            return EnchantmentHelper.getItemEnchantmentLevel(fortuneHolder, stack);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void armSwitchBack() {
        if (previousSlot >= 0 && switchBackTicks <= 0) {

            switchBackTicks = SWITCH_BACK_TICKS + random.nextInt(3);
        }
    }

    private void tickSwitchBack() {
        if (previousSlot < 0) return;
        if (!bool("switch-back")) {

            previousSlot = -1;
            farmSlot = -1;
            switchBackTicks = 0;
            return;
        }

        if (MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null) return;

        if (farmSlot >= 0 && MC.player.getInventory().getSelectedSlot() != farmSlot) {
            previousSlot = -1;
            farmSlot = -1;
            switchBackTicks = 0;
            return;
        }
        if (switchBackTicks <= 0 || --switchBackTicks > 0) return;

        if (AutismHandArbiter.slotReserved(previousSlot, id())
            || !changeHotbarSlot(previousSlot, MC.player.getInventory().getSelectedSlot())) {
            switchBackTicks = 1;
            return;
        }

        switchSettleTicks = 0;
        previousSlot = -1;
        farmSlot = -1;
    }

    private void walkTick() {
        int tick = AutismSharedState.get().getClientTickCounter();
        BlockPos goal = walkTarget;
        if (goal != null
            && (AutismPathWalker.isBlacklisted(goal) || classifyGoal(goal, tick) == null)) {

            goal = null;
            walkTarget = null;
            walkScanTick = tick - WALK_RESCAN_TICKS;
        }

        if (goal == null && (walkScanTick == Integer.MIN_VALUE
            || tick - walkScanTick >= WALK_RESCAN_TICKS)) {
            goal = scanWalkTarget();

            if (!scanInProgress()) walkScanTick = tick;
            walkTarget = goal;
        }
        if (goal == null) {

            AutismPathWalker.stop();
            pumpRotation(null);
            return;
        }
        if (!AutismPathWalker.tick(goal)) {
            if (AutismPathWalker.hasArrived(goal)) {

                walkTarget = null;
                AutismPathWalker.stop();

                noHoldUntilTick = tick + ARRIVAL_SETTLE_TICKS + random.nextInt(3);

                walkScanTick = tick - WALK_RESCAN_TICKS;
                pumpRotation(null);
                return;
            }

            AutismPathWalker.stop();
            AutismPathWalker.blacklist(goal, WALK_BLACKLIST_TICKS);
            walkTarget = null;
            walkScanTick = tick - WALK_RESCAN_TICKS;
            pumpRotation(null);
            return;
        }
        lastInfo = "Walk";

        BlockPos walkNode = AutismPathWalker.currentNode();

        pumpRotation(AutismRotationUtil.lookingAt(
            Vec3.atCenterOf(walkNode != null ? walkNode : goal), MC.player.getEyePosition()));
    }

    private boolean walkThrough(Target target) {
        int tick = AutismSharedState.get().getClientTickCounter();
        Vec3 eye = MC.player.getEyePosition();
        double reachSqr = reach() * reach();
        BlockPos goal = continuation(target, eye, reachSqr, tick);
        if (goal == null) return false;
        if (AutismPathWalker.tick(goal)) {

            walkScanTick = tick - WALK_RESCAN_TICKS;
            return true;
        }

        continuationTarget = null;
        if (AutismPathWalker.hasArrived(goal)) {
            AutismPathWalker.stop();
            return false;
        }

        AutismPathWalker.stop();
        AutismPathWalker.blacklist(goal, WALK_BLACKLIST_TICKS);
        continuationScanTick = tick - WALK_RESCAN_TICKS;
        return false;
    }

    private BlockPos continuation(Target target, Vec3 eye, double reachSqr, int tick) {
        if (target.kind() == Kind.HARVEST && rowContinuationTick == tick && rowContinuation != null) {
            if (cellDistanceSqr(eye, rowContinuation.getX(), rowContinuation.getY(),
                    rowContinuation.getZ()) > reachSqr
                && !AutismPathWalker.isBlacklisted(rowContinuation)
                && classifyGoal(rowContinuation, tick) != null) {
                return rowContinuation;
            }
            return null;
        }
        BlockPos goal = continuationTarget;
        if (goal != null
            && (AutismPathWalker.isBlacklisted(goal) || classifyGoal(goal, tick) == null
                || cellDistanceSqr(eye, goal.getX(), goal.getY(), goal.getZ()) <= reachSqr)) {

            goal = null;
            continuationTarget = null;
            continuationScanTick = tick - WALK_RESCAN_TICKS;
        }

        if (goal == null
            && (continuationScanTick == Integer.MIN_VALUE
                || tick - continuationScanTick >= WALK_RESCAN_TICKS)) {
            goal = scanAhead(reachSqr, CONTINUATION_CONE_DEG,
                Math.min(CONTINUATION_MAX_RING, searchRange()));

            if (!scanInProgress()) continuationScanTick = tick;
            continuationTarget = goal;
        }
        return goal;
    }

    private Kind classifyGoal(BlockPos goal, int tick) {
        Set<Block> crops = cropBlocks();
        if (crops.isEmpty()) return null;
        boolean wantReplant = bool("replant");
        boolean wantPlant = bool("plant");
        boolean wantTill = bool("till") && hoeAvailable();
        boolean wantBonemeal = bool("bonemeal") && bonemealAvailable();
        return classifyAt(new BlockPos.MutableBlockPos(goal.getX(), goal.getY(), goal.getZ()),
            goal.getX(), goal.getY(), goal.getZ(), crops, tick, wantReplant, wantPlant, wantTill,
            wantBonemeal);
    }

    private BlockPos scanWalkTarget() {

        return scanAhead(0.0D, 360.0F, searchRange());
    }

    private static final int SCAN_BUDGET_CELLS = 8000;
    private boolean scanning;
    private double scanMinDistSqr;
    private float scanMaxCorrection;
    private int scanMaxRing;
    private int scanManhattan;
    private long scanMaxSqr;
    private int scanOx, scanOy, scanOz;
    private double scanPx, scanPz;
    private Vec3 scanEye;
    private float scanLookYaw;
    private int scanRing, scanEdge, scanAy, scanSy;
    private record WalkChoice(BlockPos pos, double estimate) {}
    private final java.util.List<WalkChoice> walkChoices = new java.util.ArrayList<>();

    private boolean scanInProgress() {
        return scanning;
    }

    private BlockPos scanAhead(double minDistSqr, float maxCorrection, int maxRing) {
        Set<Block> crops = cropBlocks();
        if (crops.isEmpty()) {
            scanning = false;
            return null;
        }
        BlockPos origin = MC.player.blockPosition();

        int manhattan = searchRange();
        if (scanning && (scanMinDistSqr != minDistSqr || scanMaxCorrection != maxCorrection
            || scanMaxRing != maxRing || scanManhattan != manhattan
            || origin.getX() != scanOx || origin.getY() != scanOy || origin.getZ() != scanOz)) {
            scanning = false;
        }
        if (!scanning) {
            scanning = true;
            scanMinDistSqr = minDistSqr;
            scanMaxCorrection = maxCorrection;
            scanMaxRing = maxRing;
            scanManhattan = manhattan;
            scanMaxSqr = (long) maxRing * maxRing;
            scanOx = origin.getX();
            scanOy = origin.getY();
            scanOz = origin.getZ();
            scanPx = MC.player.getX();
            scanPz = MC.player.getZ();
            scanEye = MC.player.getEyePosition();
            scanLookYaw = sweepRotation().yaw();
            scanRing = 0;
            scanEdge = 0;
            scanAy = 0;
            scanSy = 0;
            walkChoices.clear();
        }
        int tick = AutismSharedState.get().getClientTickCounter();
        boolean wantReplant = bool("replant");
        boolean wantPlant = bool("plant");
        boolean wantTill = bool("till") && hoeAvailable();
        boolean wantBonemeal = bool("bonemeal") && bonemealAvailable();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int budget = SCAN_BUDGET_CELLS;

        while (scanning) {

            if (budget <= 0) return null;
            if (scanRing > scanMaxRing) {
                scanning = false;
                break;
            }
            int edgeCount = scanRing == 0 ? 1 : scanRing * 8;
            if (scanEdge >= edgeCount) {
                scanRing++;
                scanEdge = 0;
                scanAy = 0;
                scanSy = 0;
                continue;
            }
            int dx;
            int dz;
            if (scanRing == 0) {
                dx = 0;
                dz = 0;
            } else {

                int side = scanEdge / (scanRing * 2);
                int offset = scanEdge % (scanRing * 2) - scanRing;
                switch (side) {
                    case 0 -> { dx = offset; dz = -scanRing; }
                    case 1 -> { dx = offset + 1; dz = scanRing; }
                    case 2 -> { dx = -scanRing; dz = offset + 1; }
                    default -> { dx = scanRing; dz = offset; }
                }
            }
            long horizontalSqr = (long) dx * dx + (long) dz * dz;

            if (Math.abs(dx) + Math.abs(dz) > scanManhattan) {
                scanEdge++;
                continue;
            }
            if (scanAy > WALK_Y_BAND) {
                scanEdge++;
                scanAy = 0;
                scanSy = 0;
                continue;
            }
            int syLimit = scanAy == 0 ? 1 : 2;
            if (scanSy >= syLimit) {
                scanAy++;
                scanSy = 0;
                continue;
            }
            int dy = scanSy == 0 ? scanAy : -scanAy;
            scanSy++;
            if (horizontalSqr + (long) dy * dy > scanMaxSqr) continue;
            budget--;
            int cx = scanOx + dx;
            int cy = scanOy + dy;
            int cz = scanOz + dz;
            cursor.set(cx, cy, cz);
            if (!MC.level.hasChunkAt(cursor) || AutismPathWalker.isBlacklisted(cursor)) continue;
            if (classifyAt(cursor, cx, cy, cz, crops, tick, wantReplant, wantPlant, wantTill,
                wantBonemeal) == null) {
                continue;
            }

            if (scanMinDistSqr > 0.0D && cellDistanceSqr(scanEye, cx, cy, cz) <= scanMinDistSqr) {
                continue;
            }

            float correction = Math.abs(AutismRotationUtil.angleDifference(
                scanLookYaw, yawTowards(scanPx, scanPz, cx, cz)));

            if (correction > scanMaxCorrection) continue;
            offerWalkChoice(new BlockPos(cx, cy, cz),
                Math.sqrt(cellDistanceSqr(scanEye, cx, cy, cz)) * 10.0D + correction / 18.0D);
        }
        return chooseWalkRoute(tick);
    }

    private void offerWalkChoice(BlockPos cell, double estimate) {

        for (int i = 0; i < walkChoices.size(); i++) {
            WalkChoice old = walkChoices.get(i);
            if (old.pos().distSqr(cell) <= 9.0D) {
                if (old.estimate() > estimate) walkChoices.set(i, new WalkChoice(cell, estimate));
                return;
            }
        }
        walkChoices.add(new WalkChoice(cell, estimate));
        walkChoices.sort(java.util.Comparator.comparingDouble(WalkChoice::estimate));
        if (walkChoices.size() > 6) walkChoices.removeLast();
    }

    private BlockPos chooseWalkRoute(int tick) {
        BlockPos best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (WalkChoice choice : walkChoices) {
            BlockPos cell = choice.pos();
            if (classifyGoal(cell, tick) == null || AutismPathWalker.isBlacklisted(cell)) continue;
            double cost = AutismPathWalker.estimateTravelCost(cell);
            if (!Double.isFinite(cost)) continue;

            int neighbours = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (classifyGoal(cell.relative(direction), tick) != null) neighbours++;
            }
            float correction = Math.abs(AutismRotationUtil.angleDifference(sweepRotation().yaw(),
                yawTowards(MC.player.getX(), MC.player.getZ(), cell.getX(), cell.getZ())));
            cost += correction / 18.0D - neighbours;
            if (cost < bestCost) {
                bestCost = cost;
                best = cell;
            }
        }
        walkChoices.clear();
        return best;
    }

    private static float yawTowards(double px, double pz, int x, int z) {
        return (float) (Math.toDegrees(Math.atan2(z + 0.5D - pz, x + 0.5D - px)) - 90.0);
    }

    private Kind classifyAt(BlockPos.MutableBlockPos cursor, int cx, int cy, int cz,
                            Set<Block> crops, int tick, boolean wantReplant, boolean wantPlant,
                            boolean wantTill, boolean wantBonemeal) {
        BlockState state = MC.level.getBlockState(cursor);
        Block block = state.getBlock();
        if (crops.contains(block)) {

            if (grownOk(state) && harvestMemoryAvailable(cx, cy, cz, block)
                && columnBaseOk(cursor, cx, cy, cz, block)) return Kind.HARVEST;

            if (wantBonemeal && bonemealable(cursor.set(cx, cy, cz), state)) return Kind.BONEMEAL;
            return null;
        }
        if ((wantReplant || wantPlant) && state.canBeReplaced()) {
            BlockState below = MC.level.getBlockState(cursor.set(cx, cy - 1, cz));

            if (wantReplant && recentChoiceAt(cx, cy, cz, below, crops, tick) != null) return Kind.REPLANT_RECENT;
            if (wantPlant && plainChoice(cx, cy, cz, below, crops) != null) return Kind.REPLANT;
            return null;
        }
        if (wantTill && TILLABLE.contains(block)

            && (block == Blocks.ROOTED_DIRT
                || MC.level.getBlockState(cursor.set(cx, cy + 1, cz)).isAir())) {
            return Kind.TILL;
        }
        return null;
    }

    private static double cellDistanceSqr(Vec3 eye, int x, int y, int z) {
        double dx = eye.x < x ? x - eye.x : eye.x > x + 1 ? eye.x - (x + 1) : 0.0D;
        double dy = eye.y < y ? y - eye.y : eye.y > y + 1 ? eye.y - (y + 1) : 0.0D;
        double dz = eye.z < z ? z - eye.z : eye.z > z + 1 ? eye.z - (z + 1) : 0.0D;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int[] sphere(int radius) {
        int clamped = Mth.clamp(radius, 0, 8);
        if (clamped == sphereRadius) return sphereCache;
        int limit = clamped * clamped;
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int x = -clamped; x <= clamped; x++) {
            for (int y = -clamped; y <= clamped; y++) {
                for (int z = -clamped; z <= clamped; z++) {
                    if (x * x + y * y + z * z <= limit) cells.add(new int[] {x, y, z});
                }
            }
        }
        cells.sort(java.util.Comparator.comparingInt(cell -> cell[0] * cell[0] + cell[1] * cell[1] + cell[2] * cell[2]));
        int[] flat = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            flat[i * 3] = cell[0];
            flat[i * 3 + 1] = cell[1];
            flat[i * 3 + 2] = cell[2];
        }
        sphereRadius = clamped;
        sphereCache = flat;
        return flat;
    }
}
