

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismCombatClicker;
import autismclient.util.AutismFaceScan;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismPlacementTick;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class AutoTrapModule extends Module implements AutismSilentAim.Owner {

    private static final double SIMULATION_DISTANCE = 10.0D;
    private static final int SIMULATION_TICKS = 25;
    private static final int SIMULATION_HISTORY = 10;
    private static final int MIN_EVIDENCE = 5;
    private static final double MAX_LANDING_SPREAD = 1.5D;

    private static final int LEAD_TICKS = 4;

    private static final double STATIONARY_SPEED_SQR = 1.0E-3D;

    private static final int LOOK_AHEAD_TICKS = 5;
    private static final double MAX_SWEEP_SIZE = 30.0D;

    private static final int GROUND_SNAP_BLOCKS = 3;

    private static final double VERTICAL_DRAG = 0.98D;

    private static final int MAX_COMBAT_WAIT_TICKS = 40;

    private static final int PLAN_DECAY_TICKS = 10;

    private static final int GATE_PATIENCE_TICKS = 10;

    private static final int COMBO_MAX_WAIT_TICKS = 10;

    private static final int LAVA_PICKUP_TICKS = 30;
    private static final int LAVA_PICKUP_GIVE_UP_TICKS = 200;

    private static final long PLACE_FLOOR_MS = 100L;
    private static final int PLACE_JITTER_MS = 25;
    private static final long PLACE_CLOCK_SLACK_MS = 3L;

    private static final int SWITCH_BACK_TICKS = 2;

    private static final float ROTATION_MATCH_EPSILON = 0.05F;

    private static final double MIN_DISTANCE_SQR = 1.0D * 1.0D;

    private static final Set<Item> WEB_ITEMS = Set.of(Items.COBWEB);
    private static final Set<Block> WEB_BLOCKS = Set.of(Blocks.COBWEB);
    private static final Set<Item> IGNITE_ITEMS = Set.of(Items.LAVA_BUCKET, Items.FLINT_AND_STEEL);
    private static final Set<Block> IGNITE_BLOCKS = Set.of(Blocks.LAVA, Blocks.FIRE);

    private record Landing(Vec3 landing, int ticksToGround, Vec3 current, boolean stationary, Vec3[] leadPath) {

        Vec3 leadAt(int ticks) {
            int index = ticks < 1 ? 0 : ticks > leadPath.length ? leadPath.length - 1 : ticks - 1;
            return leadPath[index];
        }
    }

    private record TrapSlot(int hotbarSlot, InteractionHand hand, ItemStack stack) {
    }

    private record Plan(
        boolean web,
        int targetId,
        AABB targetBox,
        int hotbarSlot,
        InteractionHand hand,
        AutismRotationUtil.Rotation rotation,
        boolean pickup
    ) {
    }

    private record LavaSource(BlockPos cell, int targetId, int hotbarSlot, InteractionHand hand,
                              int placedTick) {
    }

    private final Map<Integer, ArrayDeque<Landing>> landings = new LinkedHashMap<>();

    private final Map<Integer, Integer> gateMisses = new LinkedHashMap<>();
    private final Random random = new Random();

    private volatile Plan currentPlan;
    private int planDecayTicks;
    private int lockedTargetId = -1;

    private LavaSource lavaSource;

    private int comboTargetId = -1;
    private int comboWaitTicks;
    private int combatWaitTicks;
    private int delayTicks;
    private int lastPlaceTick = Integer.MIN_VALUE;
    private long lastPlaceNanos = Long.MIN_VALUE;
    private int placeJitterMs;
    private int previousSlot = -1;

    private int trapSlot = -1;
    private int switchBackTicks;

    private int hotbarChangeTick = Integer.MIN_VALUE;

    private String cachedEntityListSource;
    private Set<String> cachedEntityIds = Set.of();

    AutoTrapModule() {
        super("auto-trap", "AutoTrap", ModuleCategory.COMBAT, "Traps enemies in blocks.");
        add(new IntSetting("delay", "Delay", 20, 0, 400, 5)
            .unit("ticks")
            .description("Cooldown after a placed trap")
            .build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .description("Return to previous hotbar slot")
            .build());
        add(new BoolSetting("web", "AutoWeb", true)
            .group("AutoWeb")
            .description("Place cobwebs; tried before Ignite")
            .build());
        add(new BoolSetting("ignite", "Ignite", true)
            .group("Ignite")
            .description("Place lava or set fire")
            .build());
        add(new BoolSetting("predict", "Predict Landing", true)
            .group("Target")
            .description("Trap the predicted landing spot")
            .build());
        add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
            .group("Target")
            .description("Entity types to trap")
            .build());
        add(new IntSetting("fov", "FOV", 180, 0, 180, 5)
            .group("Target")
            .unit("degrees")
            .description("Cone width around crosshair")
            .build());
        add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1)
            .group("Target")
            .unit("ticks")
            .description("Maximum target hurt time")
            .build());
        add(new ChoiceSetting("priority", "Priority", "Type", "Type", "Health", "Distance")
            .group("Target")
            .description("Main target sort key")
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
    }

    @Override
    public void onGameLeft() {
        resetRuntime();

        if (ownsRotation()) AutismKillAuraRotation.reset();
        AutismHandArbiter.releaseAll(id());
    }

    private void resetRuntime() {
        currentPlan = null;
        planDecayTicks = 0;
        lockedTargetId = -1;
        lavaSource = null;
        comboTargetId = -1;
        comboWaitTicks = 0;
        combatWaitTicks = 0;
        delayTicks = 0;
        previousSlot = -1;
        trapSlot = -1;
        switchBackTicks = 0;
        hotbarChangeTick = Integer.MIN_VALUE;
        landings.clear();
        gateMisses.clear();
    }

    @Override
    public String info() {
        Plan plan = currentPlan;
        return plan == null ? "" : plan.pickup() ? "Pickup" : plan.web() ? "Web" : "Ignite";
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
        if (MC == null || MC.player == null || MC.level == null || MC.gameMode == null) {
            resetRuntime();
            return;
        }
        tickSwitchBack();

        runSimulations();

        if (!canRun()) {
            standDown();
            return;
        }

        if (comboTargetId >= 0 && ++comboWaitTicks > COMBO_MAX_WAIT_TICKS) {
            comboTargetId = -1;
            delayTicks = integer("delay");
        }

        if (delayTicks > 0) {

            delayTicks--;
            currentPlan = null;
            planDecayTicks = 0;
            pumpRotation(null);
            armSwitchBack();
            return;
        }

        Plan plan = currentPlan;

        boolean holdingForAura = plan != null && waitForCombatTiming();
        if (plan != null && !holdingForAura) {
            AutismRotationUtil.Rotation placed = tryPlace(plan);
            if (placed != null) {
                combatWaitTicks = 0;
                planDecayTicks = 0;

                currentPlan = null;

                if (plan.pickup()) {

                    comboTargetId = -1;
                } else if (plan.web() && bool("ignite") && findSlot(IGNITE_ITEMS) != null
                    && !targetOnFire(plan.targetId())) {

                    comboTargetId = plan.targetId();
                    comboWaitTicks = 0;
                } else {
                    comboTargetId = -1;
                    delayTicks = integer("delay");
                }
                pumpRotation(placed, true);
                armSwitchBack();
                return;
            }
        }

        Plan planned = plan();
        if (planned != null) {
            currentPlan = planned;
            planDecayTicks = 0;
        } else if (currentPlan != null && (targetHandled(currentPlan) || ++planDecayTicks >= PLAN_DECAY_TICKS)) {

            currentPlan = null;
            lockedTargetId = -1;
            planDecayTicks = 0;
        }

        Plan live = currentPlan;

        pumpRotation(holdingForAura || live == null ? null : live.rotation());
        armSwitchBack();
    }

    private boolean canRun() {
        if (MC == null || MC.player == null || MC.level == null || MC.gameMode == null) return false;
        if (PackHideState.isActive() || PackFreecamState.isActive()) return false;
        if (!MC.player.isAlive() || MC.player.isSpectator()) return false;

        if (AutismSilentAim.scaffoldOwnsRotation() || ScaffoldModule.reservesRageInput()) return false;

        if (MC.player.isUsingItem() || MC.player.isHandsBusy()) return false;

        if (MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null) return false;

        if (AutoTotemModule.operationActive() || AutoArmorModule.operationActive()) return false;
        return true;
    }

    private void standDown() {
        currentPlan = null;
        planDecayTicks = 0;
        combatWaitTicks = 0;
        lockedTargetId = -1;
        comboTargetId = -1;

        armSwitchBack();
        if (ownsRotation()) {
            AutismKillAuraRotation.beginWindDown(id());
            if (MC != null && MC.player != null) AutismKillAuraRotation.update(id(), MC.player);
        }
    }

    private boolean ownsRotation() {
        return id().equals(AutismKillAuraRotation.currentOwner());
    }

    private void pumpRotation(AutismRotationUtil.Rotation goal) {
        pumpRotation(goal, false);
    }

    private void pumpRotation(AutismRotationUtil.Rotation goal, boolean pinQuiet) {
        if (goal != null) {
            AutismKillAuraRotation.setTarget(id(), AutismKillAuraRotation.PRIORITY_AUTO_TRAP, goal);
        } else if (ownsRotation()) {
            AutismKillAuraRotation.beginWindDown(id());
        }

        if (ownsRotation()) AutismKillAuraRotation.update(id(), MC.player, pinQuiet);
    }

    private boolean waitForCombatTiming() {
        boolean wait = killAuraHasTarget()
            && (MC.player.getAttackStrengthScale(0.5F) > 0.9F || wouldDoCriticalHit());
        combatWaitTicks = wait ? combatWaitTicks + 1 : 0;
        return wait && combatWaitTicks < MAX_COMBAT_WAIT_TICKS;
    }

    private boolean killAuraHasTarget() {
        Module module = ModuleRegistry.get("kill-aura");
        return module instanceof KillAuraModule aura && aura.isEnabled() && aura.currentTarget() != null;
    }

    private boolean wouldDoCriticalHit() {
        return MC.player.fallDistance > 0.0D
            && !MC.player.onGround()
            && !MC.player.onClimbable()
            && !MC.player.isInWater()
            && !MC.player.hasEffect(MobEffects.BLINDNESS)
            && !MC.player.isPassenger();
    }

    private Plan plan() {
        List<LivingEntity> enemies = targets();

        if (lockedTargetId >= 0) {
            boolean stillValid = false;
            for (LivingEntity enemy : enemies) {
                if (enemy.getId() == lockedTargetId) {
                    stillValid = true;
                    break;
                }
            }
            if (!stillValid) lockedTargetId = -1;
        }

        Plan pickup = planLavaPickup();
        if (pickup != null) return pickup;
        if (enemies.isEmpty()) return null;

        if (comboTargetId >= 0) {
            LivingEntity combo = null;
            for (LivingEntity enemy : enemies) {
                if (enemy.getId() == comboTargetId) {
                    combo = enemy;
                    break;
                }
            }

            if (combo == null || combo.isOnFire() || !bool("ignite")) {
                comboTargetId = -1;
                delayTicks = integer("delay");
                return null;
            }
            return planTrap(List.of(combo), false);
        }
        Plan plan = bool("web") ? planTrap(enemies, true) : null;
        if (plan == null && bool("ignite")) plan = planTrap(enemies, false);
        return plan;
    }

    private boolean targetOnFire(int targetId) {
        return MC.level.getEntity(targetId) instanceof LivingEntity living && living.isOnFire();
    }

    private Plan planLavaPickup() {
        LavaSource source = lavaSource;
        if (source == null) return null;
        BlockPos cell = source.cell();
        if (!isLavaSource(MC.level.getBlockState(cell))) {

            lavaSource = null;
            return null;
        }
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick - source.placedTick() >= LAVA_PICKUP_GIVE_UP_TICKS) {
            lavaSource = null;
            return null;
        }
        if (!targetOnFire(source.targetId())
            && MC.level.getEntity(source.targetId()) != null
            && tick - source.placedTick() < LAVA_PICKUP_TICKS) {
            return null;
        }
        ItemStack stack = source.hand() == InteractionHand.OFF_HAND
            ? MC.player.getOffhandItem() : MC.player.getInventory().getItem(source.hotbarSlot());
        if (!stack.is(Items.BUCKET)) {
            lavaSource = null;
            return null;
        }

        if (source.hand() == InteractionHand.OFF_HAND) {
            if (AutismHandArbiter.offhandClaimedByOther(id())
                || AutismCombatClicker.mainHandWouldPreempt()) {
                return null;
            }
        } else if (AutismHandArbiter.slotReserved(source.hotbarSlot(), id())) {
            return null;
        }
        Vec3 eye = MC.player.getEyePosition();

        if (Vec3.atCenterOf(cell).distanceToSqr(eye) > reach() * reach()) return null;
        AutismRotationUtil.Rotation rotation =
            AutismRotationUtil.lookingAt(Vec3.atCenterOf(cell), eye);

        if (Math.abs(rotation.pitch()) > AutismFaceScan.goalPitchLimit()) return null;
        return new Plan(false, source.targetId(), new AABB(cell), source.hotbarSlot(),
            source.hand(), rotation, true);
    }

    private static boolean isLavaSource(BlockState state) {
        return state.is(Blocks.LAVA) && state.getFluidState().isSource();
    }

    private boolean targetHandled(Plan plan) {

        if (plan.pickup()) return lavaSource == null;
        Entity entity = MC.level.getEntity(plan.targetId());
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return true;
        boolean webDone = !bool("web") || findSlot(WEB_ITEMS) == null
            || boxTouchesWeb(living.getBoundingBox());
        boolean igniteDone = !bool("ignite") || findSlot(IGNITE_ITEMS) == null || living.isOnFire();
        return webDone && igniteDone;
    }

    private boolean boxTouchesWeb(AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (WEB_BLOCKS.contains(MC.level.getBlockState(pos).getBlock())) return true;
        }
        return false;
    }

    private Plan planTrap(List<LivingEntity> enemies, boolean web) {
        TrapSlot slot = findSlot(web ? WEB_ITEMS : IGNITE_ITEMS);
        if (slot == null) return null;
        Set<Block> trapBlocks = web ? WEB_BLOCKS : IGNITE_BLOCKS;
        BlockState placedState = placedState(web, slot.stack());
        Vec3 eye = MC.player.getEyePosition();
        double reach = reach();

        for (LivingEntity target : enemies) {

            if (!web && target.isOnFire()) continue;

            Vec3 trapPos = findPosForTrap(target, target.getId() == lockedTargetId);
            if (trapPos == null) continue;

            trapPos = grounded(trapPos);

            BlockPos origin = BlockPos.containing(trapPos);
            if (trapBlocks.contains(MC.level.getBlockState(origin).getBlock())) continue;

            EntityDimensions dimensions =
                target.getDimensions(web ? Pose.STANDING : target.getPose());

            boolean mustBeOnGround = slot.stack().is(web ? Items.COBWEB : Items.FLINT_AND_STEEL);
            Vec3 velocity = target.position().subtract(target.oldPosition());

            List<BlockPos> offsets = findOffsets(
                trapPos, dimensions, velocity, mustBeOnGround, web, trapBlocks, eye);
            for (BlockPos offset : offsets) {
                AutismRotationUtil.Rotation rotation =
                    solvePlacement(origin.offset(offset), placedState, eye, reach,
                        slot.stack(), slot.hand());
                if (rotation == null) continue;
                lockedTargetId = target.getId();
                return new Plan(web, target.getId(), dimensions.makeBoundingBox(trapPos),
                    slot.hotbarSlot(), slot.hand(), rotation, false);
            }
        }
        return null;
    }

    private double reach() {
        return MC.player.blockInteractionRange();
    }

    private BlockState placedState(boolean web, ItemStack stack) {
        if (web) return Blocks.COBWEB.defaultBlockState();
        return stack.is(Items.LAVA_BUCKET)
            ? Blocks.LAVA.defaultBlockState() : Blocks.FIRE.defaultBlockState();
    }

    private TrapSlot findSlot(Set<Item> items) {
        ItemStack offhand = MC.player.getOffhandItem();

        if (items.contains(offhand.getItem())
            && !(lavaSource != null && offhand.is(Items.LAVA_BUCKET))
            && !AutismHandArbiter.offhandClaimedByOther(id())
            && !AutismCombatClicker.mainHandWouldPreempt()) {
            return new TrapSlot(-1, InteractionHand.OFF_HAND, offhand);
        }
        int selected = MC.player.getInventory().getSelectedSlot();
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {

            if (AutismHandArbiter.slotReserved(slot, id())) continue;
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!items.contains(stack.getItem())) continue;

            if (lavaSource != null && stack.is(Items.LAVA_BUCKET)) continue;
            int distance = Math.abs(slot - selected);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = slot;
        }
        if (best < 0) return null;
        return new TrapSlot(best, InteractionHand.MAIN_HAND, MC.player.getInventory().getItem(best));
    }

    private List<LivingEntity> targets() {

        double minimum = MIN_DISTANCE_SQR;
        double reach = reach();
        double maximum = reach * reach;
        Vec3 eyes = MC.player.getEyePosition();

        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && validate(living, eyes, minimum, maximum)) {
                found.add(living);
            }
        }
        if (found.size() > 1) found.sort(targetComparator());
        return found;
    }

    private boolean validate(LivingEntity entity, Vec3 eyes, double minimumSqr, double maximumSqr) {
        if (entity == MC.player || entity.isRemoved() || !entity.isAlive()) return false;
        if (!(entity instanceof Attackable)) return false;
        if (!EntitySelector.CAN_BE_PICKED.test(entity)) return false;
        if (entity.hasPassenger(MC.player)) return false;
        if (entity.hurtTime > integer("hurt-time")) return false;

        if (AutismAntiBot.suppress(entity)) return false;

        if (TeamsModule.combatExcluded(entity, "killaura")) return false;

        if (entity instanceof Player player && player.isSleeping()) return false;
        if (!matchesEntity(entity)) return false;
        double distanceSqr = boxedDistanceToPlayerSqr(entity);

        if (distanceSqr < minimumSqr || distanceSqr > maximumSqr) return false;

        return integer("fov") >= 180 || crosshairAngleTo(entity, eyes) <= integer("fov") * 0.5F;
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

    private Comparator<LivingEntity> targetComparator() {
        Comparator<LivingEntity> byType = Comparator.comparingInt(
            entity -> entity instanceof Player ? 0 : 1);
        Comparator<LivingEntity> byHealth = Comparator.comparingDouble(
            entity -> entity.getHealth() + entity.getAbsorptionAmount());
        Comparator<LivingEntity> byDistance = Comparator.comparingDouble(
            this::boxedDistanceToPlayerSqr);
        return switch (choice("priority")) {
            case "Health" -> byHealth.thenComparing(byType).thenComparing(byDistance);
            case "Distance" -> byDistance.thenComparing(byType).thenComparing(byHealth);
            default -> byType.thenComparing(byHealth).thenComparing(byDistance);
        };
    }

    private double boxedDistanceToPlayerSqr(Entity entity) {
        return entity.getBoundingBox().inflate(entity.getPickRadius())
            .distanceToSqr(MC.player.getEyePosition());
    }

    private float crosshairAngleTo(Entity entity, Vec3 eyes) {
        AutismRotationUtil.Rotation toCenter =
            AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
        return AutismRotationUtil.rotationAngleTo(
            AutismRotationUtil.playerRotation(MC.player), toCenter);
    }

    private void runSimulations() {
        if (!bool("predict")) {
            landings.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        double maximumSqr = SIMULATION_DISTANCE * SIMULATION_DISTANCE;
        for (Player player : MC.level.players()) {
            if (player == MC.player || player.isRemoved() || !player.isAlive()) continue;
            if (player.distanceToSqr(MC.player) > maximumSqr) continue;
            seen.add(player.getId());
            ArrayDeque<Landing> history =
                landings.computeIfAbsent(player.getId(), key -> new ArrayDeque<>());
            history.addLast(simulate(player));
            while (history.size() > SIMULATION_HISTORY) history.removeFirst();
        }
        landings.keySet().retainAll(seen);
        gateMisses.keySet().retainAll(seen);
    }

    private Landing simulate(Player player) {
        Vec3 current = player.position();

        Vec3 velocity = current.subtract(player.oldPosition());
        boolean stationary = velocity.lengthSqr() < STATIONARY_SPEED_SQR;
        if (player.onGround() && stationary) {
            Vec3[] still = new Vec3[LEAD_TICKS];
            Arrays.fill(still, current);
            return new Landing(null, 0, current, true, still);
        }

        AABB box = player.getBoundingBox();
        Vec3 position = current;
        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        boolean wasAirborne = !player.onGround();

        boolean freeFalling = !player.onClimbable() && !player.isInWater();

        Vec3[] leadPath = new Vec3[LEAD_TICKS];
        Vec3 landing = null;
        int landingTick = 0;

        for (int tick = 1; tick <= SIMULATION_TICKS; tick++) {
            List<VoxelShape> shapes = collisionShapes(player, box, velocity);
            double dy = Shapes.collide(Direction.Axis.Y, box, shapes, velocity.y);
            box = box.move(0.0D, dy, 0.0D);
            double dx = Shapes.collide(Direction.Axis.X, box, shapes, velocity.x);
            box = box.move(dx, 0.0D, 0.0D);
            double dz = Shapes.collide(Direction.Axis.Z, box, shapes, velocity.z);
            box = box.move(0.0D, 0.0D, dz);
            position = new Vec3(position.x + dx, position.y + dy, position.z + dz);
            if (tick <= LEAD_TICKS) leadPath[tick - 1] = position;

            boolean onGround = velocity.y <= 0.0D && dy != velocity.y;
            if (wasAirborne && onGround && landing == null) {
                landing = position;
                landingTick = tick;

                if (tick >= LEAD_TICKS) return new Landing(landing, landingTick, current, false, leadPath);
            }
            wasAirborne = !onGround;

            double verticalSpeed =
                onGround ? 0.0D : freeFalling ? (velocity.y - gravity) * VERTICAL_DRAG : velocity.y;
            velocity = new Vec3(
                dx == velocity.x ? velocity.x : 0.0D,
                verticalSpeed,
                dz == velocity.z ? velocity.z : 0.0D);

            if (onGround && velocity.horizontalDistanceSqr() < 1.0E-6D) break;
        }

        for (int i = 0; i < leadPath.length; i++) {
            if (leadPath[i] == null) leadPath[i] = position;
        }
        return new Landing(landing, landingTick, current, stationary, leadPath);
    }

    private List<VoxelShape> collisionShapes(Entity entity, AABB box, Vec3 velocity) {
        List<VoxelShape> shapes = new ArrayList<>();
        for (VoxelShape shape : MC.level.getBlockCollisions(entity, box.expandTowards(velocity))) {
            shapes.add(shape);
        }
        return shapes;
    }

    private Vec3 findPosForTrap(LivingEntity target, boolean locked) {
        if (!(target instanceof Player) || !bool("predict")) return target.position();

        ArrayDeque<Landing> history = landings.get(target.getId());
        if (history == null || history.isEmpty()) {
            return locked ? target.position() : gateFallback(target, null, LEAD_TICKS);
        }
        Landing last = history.peekLast();
        if (stationaryOverWindow(history)) {
            gateMisses.remove(target.getId());
            return last.current();
        }

        int leadTicks = adaptiveLeadTicks(history);

        boolean longFall = last.landing() != null && last.ticksToGround() > LEAD_TICKS;
        List<Vec3> positions = new ArrayList<>();
        for (Landing entry : history) {
            Vec3 future = longFall ? entry.landing() : entry.leadAt(leadTicks);
            if (future != null) positions.add(future);
        }
        if (positions.size() < MIN_EVIDENCE) {
            return locked ? last.current() : gateFallback(target, last, leadTicks);
        }

        Vec3 average = Vec3.ZERO;
        for (Vec3 position : positions) average = average.add(position);
        average = average.scale(1.0D / positions.size());
        double squared = 0.0D;
        for (Vec3 position : positions) squared += position.subtract(average).lengthSqr();
        double spread = Math.sqrt(squared / positions.size());

        if (spread < MAX_LANDING_SPREAD) {
            gateMisses.remove(target.getId());
            return positions.get(positions.size() - 1);
        }
        return gateFallback(target, last, leadTicks);
    }

    private static boolean stationaryOverWindow(ArrayDeque<Landing> history) {
        if (history.peekLast().stationary()) return true;
        int elapsed = history.size() - 1;
        if (elapsed <= 0) return false;
        double perTickSqr =
            history.peekFirst().current().distanceToSqr(history.peekLast().current())
                / (elapsed * elapsed);
        return perTickSqr < STATIONARY_SPEED_SQR;
    }

    private static int adaptiveLeadTicks(ArrayDeque<Landing> history) {
        Vec3 previous = null;
        double walked = 0.0D;
        double displacementX = 0.0D;
        double displacementZ = 0.0D;
        for (Landing entry : history) {
            Vec3 current = entry.current();
            if (previous != null) {
                double dx = current.x - previous.x;
                double dz = current.z - previous.z;
                walked += Math.sqrt(dx * dx + dz * dz);
                displacementX += dx;
                displacementZ += dz;
            }
            previous = current;
        }
        if (walked < 1.0E-6D) return LEAD_TICKS;
        double straight =
            Math.sqrt(displacementX * displacementX + displacementZ * displacementZ) / walked;
        return 1 + (int) Math.round(straight * (LEAD_TICKS - 1));
    }

    private Vec3 gateFallback(LivingEntity target, Landing last, int leadTicks) {
        int misses = gateMisses.getOrDefault(target.getId(), 0) + 1;
        gateMisses.put(target.getId(), misses);
        if (misses < GATE_PATIENCE_TICKS) return null;
        if (last != null) {
            if (last.landing() != null && last.ticksToGround() > LEAD_TICKS) return last.landing();
            return last.leadAt(leadTicks);
        }
        return target.position();
    }

    private List<BlockPos> findOffsets(Vec3 position, EntityDimensions dimensions, Vec3 velocity,
                                       boolean mustBeOnGround, boolean web, Set<Block> trapBlocks,
                                       Vec3 eye) {
        BlockPos origin = BlockPos.containing(position);
        AABB start = dimensions.makeBoundingBox(position)
            .move(-origin.getX(), -origin.getY(), -origin.getZ());
        AABB end = start.move(velocity.x * LOOK_AHEAD_TICKS, 0.0D, velocity.z * LOOK_AHEAD_TICKS);

        if (velocity.horizontalDistance() * LOOK_AHEAD_TICKS > MAX_SWEEP_SIZE) {
            return placeable(origin, BlockPos.ZERO, mustBeOnGround, web, trapBlocks)
                ? List.of(BlockPos.ZERO) : List.<BlockPos>of();
        }

        record Ranked(BlockPos offset, double rank, double eyeDistanceSqr) {
        }
        List<Ranked> ranked = new ArrayList<>();
        int minX = Mth.floor(start.minX);
        int maxX = Mth.ceil(start.maxX) - 1;
        int minY = Mth.floor(start.minY);
        int maxY = Mth.ceil(start.maxY) - 1;
        int minZ = Mth.floor(start.minZ);
        int maxZ = Mth.ceil(start.maxZ) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos offset = new BlockPos(x, y, z);
                    AABB unit = new AABB(offset);
                    if (!start.intersects(unit) && !end.intersects(unit)) continue;
                    if (!placeable(origin, offset, mustBeOnGround, web, trapBlocks)) continue;
                    double rank = overlap(start, unit) + overlap(end, unit) * 0.5D;
                    ranked.add(new Ranked(offset, rank,
                        Vec3.atCenterOf(origin.offset(offset)).distanceToSqr(eye)));
                }
            }
        }
        ranked.sort(Comparator.<Ranked>comparingDouble(Ranked::rank).reversed()
            .thenComparingDouble(Ranked::eyeDistanceSqr));

        List<BlockPos> offsets = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) offsets.add(entry.offset());
        return offsets;
    }

    private Vec3 grounded(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        int drop = 0;
        while (drop < GROUND_SNAP_BLOCKS && !MC.level.isOutsideBuildHeight(pos.below())
            && MC.level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
            drop++;
        }
        return drop == 0 ? position : position.subtract(0.0D, drop, 0.0D);
    }

    private boolean placeable(BlockPos origin, BlockPos offset, boolean mustBeOnGround,
                              boolean web, Set<Block> trapBlocks) {
        BlockPos cell = origin.offset(offset);
        if (MC.level.isOutsideBuildHeight(cell)) return false;
        BlockState state = MC.level.getBlockState(cell);
        if (trapBlocks.contains(state.getBlock()) || !state.canBeReplaced()) return false;

        if (web && !state.getFluidState().isEmpty()) return false;
        return !mustBeOnGround || !MC.level.getBlockState(cell.below()).isAir();
    }

    private static double overlap(AABB box, AABB unit) {
        return box.intersects(unit) ? box.intersect(unit).getSize() : 0.0D;
    }

    private AutismRotationUtil.Rotation solvePlacement(BlockPos cell, BlockState placedState,
                                                       Vec3 eye, double reach, ItemStack material,
                                                       InteractionHand hand) {
        if (MC.level.isOutsideBuildHeight(cell)) return null;
        BlockState cellState = MC.level.getBlockState(cell);
        if (!cellState.canBeReplaced()) return null;

        if (placedState.is(Blocks.COBWEB) && !cellState.getFluidState().isEmpty()) return null;

        if (Vec3.atCenterOf(cell).distanceToSqr(eye) < MIN_DISTANCE_SQR) return null;

        if (!MC.level.isUnobstructed(placedState, cell, CollisionContext.empty())) return null;

        AutismFaceScan.Candidate candidate = AutismFaceScan.best(
            new AutismFaceScan.Request(cell, eye, reach, placementFor(material, hand))

                .from(AutismRotationUtil.playerRotation(MC.player))
                .pitchLimit(AutismFaceScan.goalPitchLimit())

                .sneaking(MC.player.isSecondaryUseActive())
                .sneakAllowed(false)
                .budget(new AutismFaceScan.Budget(AutismFaceScan.DEFAULT_TICK_RAY_BUDGET)));
        return candidate == null ? null : candidate.aim().goal();
    }

    private AutismFaceScan.Placement placementFor(ItemStack material, InteractionHand hand) {
        if (material.getItem() instanceof BlockItem) {
            return AutismFaceScan.blockItem(material, MC.player, hand);
        }
        return (hit, target) -> hit.getBlockPos().relative(hit.getDirection()).equals(target);
    }

    private ItemStack planStack(Plan plan) {
        return plan.hand() == InteractionHand.OFF_HAND
            ? MC.player.getOffhandItem()
            : MC.player.getInventory().getItem(plan.hotbarSlot());
    }

    private BlockPos placementCell(BlockHitResult ray, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) {
            return new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, ray)
                .getClickedPos();
        }
        return ray.getBlockPos().relative(ray.getDirection());
    }

    private boolean landsInCell(BlockHitResult hit, BlockPos cell, ItemStack stack) {

        if (!(stack.getItem() instanceof BlockItem)) return true;
        BlockPlaceContext context =
            new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, hit);
        return context.canPlace() && context.getClickedPos().equals(cell);
    }

    private static boolean placementPitchLegal(float pitch) {
        return ScaffoldModule.grimPlacementPitchLegal(Math.abs(pitch));
    }

    private AutismRotationUtil.Rotation tryPlace(Plan plan) {
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == lastPlaceTick) return null;

        if (BedDefenderModule.ownsSilentRotation()) return null;
        if (SurroundModule.ownsSilentRotation()) return null;
        if (CrystalAuraModule.reservesCombatTick()) return null;
        if (AnchorAuraModule.reservesCombatTick()) return null;
        if (!paceHolds()) return null;

        if (AutismBlinkManager.holdsActionsWithoutMovement()) return null;

        AutismServerRotationView.WireSnapshot wire = AutismServerRotationView.snapshot();
        if (!wire.initialized()) return null;
        AutismRotationUtil.Rotation wireRotation =
            new AutismRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch());

        if (!placementPitchLegal(wireRotation.pitch())) return null;

        AutismRotationUtil.Rotation silent = AutismSilentAim.activeOutgoingRotation(MC.player);
        if (silent != null && !sameRotation(silent, wireRotation)) return null;

        if (plan.pickup()) return tryPickup(plan, tick, wireRotation);

        double reach = reach();
        BlockHitResult ray = ScaffoldModule.grimClickRay(
            MC.player.getEyePosition(), wireRotation, reach, MC.level, MC.player);
        if (!landsInTarget(plan, ray)) return null;

        BlockPos clicked = ray.getBlockPos();
        if (!AutismFaceScan.isPlaceableSupport(
                MC.level.getBlockState(clicked), clicked, MC.player.isSecondaryUseActive())) {
            return null;
        }

        if (!ensureHand(plan)) return null;

        if (plan.hand() == InteractionHand.OFF_HAND && AutismCombatClicker.mainHandWouldPreempt()) {
            return null;
        }
        ItemStack stack = MC.player.getItemInHand(plan.hand());
        if (!trapItems(plan.web()).contains(stack.getItem())) return null;

        BlockPos placed = placementCell(ray, stack);

        if (plan.web() && !MC.level.getBlockState(placed).getFluidState().isEmpty()) return null;

        if (Vec3.atCenterOf(placed).distanceToSqr(MC.player.getEyePosition()) < MIN_DISTANCE_SQR) {
            return null;
        }
        if (!MC.level.isUnobstructed(
            placedState(plan.web(), stack), placed, CollisionContext.empty())) return null;
        if (ModuleRegistry.shouldCancelUseExcept(ray, plan.hand(), id())) return null;

        lastPlaceTick = tick;
        lastPlaceNanos = System.nanoTime();
        placeJitterMs = random.nextInt(PLACE_JITTER_MS + 1);

        if (!dispatch(plan, ray, stack)) return null;
        if (!plan.web() && stack.is(Items.LAVA_BUCKET) && !MC.player.hasInfiniteMaterials()) {

            lavaSource =
                new LavaSource(placed, plan.targetId(), plan.hotbarSlot(), plan.hand(), tick);
        }
        lockedTargetId = plan.targetId();
        return wireRotation;
    }

    private AutismRotationUtil.Rotation tryPickup(Plan plan, int tick,
                                                  AutismRotationUtil.Rotation wireRotation) {
        LavaSource source = lavaSource;

        if (source == null) return null;
        BlockHitResult ray = sourceRay(wireRotation);

        if (ray == null || !ray.getBlockPos().equals(source.cell())) return null;

        if (!isLavaSource(MC.level.getBlockState(source.cell()))) {
            lavaSource = null;
            return null;
        }

        if (!ensureHand(plan)) return null;

        if (plan.hand() == InteractionHand.OFF_HAND && AutismCombatClicker.mainHandWouldPreempt()) {
            return null;
        }
        ItemStack stack = MC.player.getItemInHand(plan.hand());

        if (!stack.is(Items.BUCKET)) return null;
        if (ModuleRegistry.shouldCancelUseExcept(ray, plan.hand(), id())) return null;

        lastPlaceTick = tick;
        lastPlaceNanos = System.nanoTime();
        placeJitterMs = random.nextInt(PLACE_JITTER_MS + 1);

        if (!dispatch(plan, ray, stack)) return null;

        return wireRotation;
    }

    private BlockHitResult sourceRay(AutismRotationUtil.Rotation rotation) {
        Vec3 eye = MC.player.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(rotation.pitch(), rotation.yaw())
            .scale(reach()));
        HitResult result = MC.level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY,
            CollisionContext.of(MC.player)));
        return result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK
            ? hit : null;
    }

    private boolean landsInTarget(Plan plan, BlockHitResult ray) {
        if (ray == null) return false;

        BlockPos placed = placementCell(ray, planStack(plan));
        if (!new AABB(placed).intersects(plan.targetBox())) return false;
        if (MC.level.isOutsideBuildHeight(placed)) return false;
        BlockState state = MC.level.getBlockState(placed);

        if (plan.web() && !state.getFluidState().isEmpty()) return false;
        return state.canBeReplaced() && !trapBlocks(plan.web()).contains(state.getBlock());
    }

    private Set<Item> trapItems(boolean web) {
        return web ? WEB_ITEMS : IGNITE_ITEMS;
    }

    private Set<Block> trapBlocks(boolean web) {
        return web ? WEB_BLOCKS : IGNITE_BLOCKS;
    }

    private boolean paceHolds() {
        if (lastPlaceNanos == Long.MIN_VALUE) return true;
        long elapsedMs = (System.nanoTime() - lastPlaceNanos) / 1_000_000L;
        return elapsedMs >= PLACE_FLOOR_MS + placeJitterMs - PLACE_CLOCK_SLACK_MS;
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

    private boolean ensureHand(Plan plan) {
        if (plan.hand() == InteractionHand.OFF_HAND) return true;
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected == plan.hotbarSlot()) return true;
        if (!changeHotbarSlot(plan.hotbarSlot(), selected)) return false;
        trapSlot = plan.hotbarSlot();
        return false;
    }

    private boolean changeHotbarSlot(int slot, int selected) {

        if (BedDefenderModule.ownsSilentRotation() || SurroundModule.ownsSilentRotation()
            || CrystalAuraModule.reservesCombatTick() || AnchorAuraModule.reservesCombatTick()) {
            return false;
        }
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == hotbarChangeTick) return false;
        if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
        try {

            if (previousSlot < 0) previousSlot = selected;
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
        hotbarChangeTick = tick;
        return true;
    }

    private boolean dispatch(Plan plan, BlockHitResult ray, ItemStack stack) {
        if (stack.isEmpty() || !stack.isItemEnabled(MC.level.enabledFeatures())) return false;

        if (!AutismPlacementTick.claim(id())) return false;
        if (!AutismCombatClicker.queueUse(ray, plan.hand())) return false;
        return true;
    }

    private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {

        return Math.abs(AutismRotationUtil.angleDifference(first.yaw(), second.yaw()))
            <= ROTATION_MATCH_EPSILON
            && Math.abs(first.pitch() - second.pitch()) <= ROTATION_MATCH_EPSILON;
    }

    private void armSwitchBack() {
        if (previousSlot >= 0 && currentPlan == null && switchBackTicks <= 0) {
            switchBackTicks = SWITCH_BACK_TICKS;
        }
    }

    private void tickSwitchBack() {
        if (previousSlot < 0) return;
        if (!bool("switch-back")) {

            previousSlot = -1;
            trapSlot = -1;
            switchBackTicks = 0;
            return;
        }

        if (MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null) return;

        if (trapSlot >= 0 && MC.player.getInventory().getSelectedSlot() != trapSlot) {
            previousSlot = -1;
            trapSlot = -1;
            switchBackTicks = 0;
            return;
        }
        if (switchBackTicks <= 0 || --switchBackTicks > 0) return;

        if (AutismHandArbiter.slotReserved(previousSlot, id())
            || !changeHotbarSlot(previousSlot, MC.player.getInventory().getSelectedSlot())) {
            switchBackTicks = 1;
            return;
        }
        previousSlot = -1;
        trapSlot = -1;
    }
}
