

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.mixin.accessor.AutismLivingEntityAccessor;
import autismclient.mixin.accessor.AutismMinecraftAccessor;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.util.AutismHandArbiter;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRenderer;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSilentAim;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class KillAuraModule extends Module implements AutismSilentAim.Owner {

    static final int HURT_TIME = 10;

    static final double SCAN_ADDITION_MIN = 2.0D;
    static final double SCAN_ADDITION_MAX = 3.0D;

    static final double THROUGH_WALLS_RANGE = 0.0D;

    static final int CPS_MIN = 5;
    static final int CPS_MAX = 8;
    static final int CLICK_CYCLE = 20;
    static final int CLICK_ITERATIONS = 2;
    static final long ENFORCED_CLICK_INTERVAL_MS = 1_000L;

    static final int AUTO_SWORD_SWITCH_BACK_TICKS = 20;

    static final int SHIELD_BREAK_HOLD_TICKS = 10;

    static final int SHIELD_DISABLE_TICKS = 100;

    static final int POST_USE_SUPPRESS_TICKS = 3;

    static final int HIT_CONFIRM_TICKS = 8;

    static final double MISS_LATERAL_MIN = 0.10D;
    static final double MISS_LATERAL_MAX = 0.20D;

    static final double MISS_DEPTH_JITTER = 0.10D;
    static final double MISS_VERTICAL_JITTER = 0.02D;

    static final long ATTACK_ACCURACY_WINDOW_MS = 60L;

    static final float WINDOW_MAX_YAW_STEP = 12.0f;
    static final float WINDOW_MAX_PITCH_STEP = 6.0f;
    static final float THROTTLED_MAX_YAW_STEP = 5.5f;
    static final float THROTTLED_MAX_PITCH_STEP = 1.2f;

    static final float WHIFF_CONVERGENCE_DEG = 2.0F;

    static final double AIM_HORIZONTAL_INSET = 0.12D;
    static final double AIM_VERTICAL_INSET = 0.20D;
    private static final double[] AIM_SCAN_HORIZONTAL = {0.12D, 0.31D, 0.50D, 0.69D, 0.88D};
    private static final double[] AIM_SCAN_VERTICAL = {0.20D, 0.35D, 0.50D, 0.65D, 0.80D};

    private static long lastClickTime;

    private static final SoundEvent HITSOUND =
        SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("autismclient", "hitsound"));

    private final Random random = new Random();
    private final AimPointTracker aimPointTracker = new AimPointTracker(new Random());
    private final Clicker clicker = new Clicker();

    private final Random missRandom = new Random();
    private final MissState missState = new MissState();
    private final AccuracyGovernor accuracyGovernor = new AccuracyGovernor();

    private LivingEntity currentTarget;

    private double closestSquaredEnemyDistance;

    private double scanAddition = nextScanAddition();

    private int previousSlot = -1;

    private int switchedToSlot = -1;
    private int switchBackTicks;

    private int hotbarChangeTick = Integer.MIN_VALUE;

    private int lastShieldSeenTick = Integer.MIN_VALUE;

    private int shieldHoldEntityId = -1;

    private int shieldBreakLandedTick = Integer.MIN_VALUE;
    private int shieldBreakLandedEntityId = -1;

    private int postUseSuppressTicks;

    private int pendingHitEntityId = -1;
    private int pendingHitPrevHurtTime;
    private int pendingHitTicks;
    private String cachedEntityListSource = "";
    private Set<String> cachedEntityIds = Set.of();

    public KillAuraModule() {
        super("kill-aura", "KillAura", ModuleCategory.COMBAT, "Automatically attacks configured entities.");
        add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").build());
        add(new ChoiceSetting("attack-mode", "Attack Mode", "Real Input", "Real Input", "Packet")
            .description("How attacks are sent")
            .build());
        add(new ChoiceSetting("targeting", "Targeting", "FOV",
            "Type", "HP", "Distance", "FOV", "Hurt Time", "Age").build());
        add(new IntSetting("fov", "FOV", 180, 10, 360, 10)
            .description("Attack cone in degrees")
            .build());
        add(new BoolSetting("miss-injection", "Miss Injection", false)
            .description("Adds human-like misses")
            .build());
        add(new IntSetting("miss-chance", "Miss Chance", 6, 1, 20, 1)
            .description("Miss rate percent")
            .visibleWhen(() -> bool("miss-injection"))
            .build());
        add(new BoolSetting("criticals", "Criticals", false)
            .description("Smart critical hits")
            .build());
        add(new BoolSetting("auto-sword", "Auto Sword", true).build());
        add(new BoolSetting("shield-break", "Shield Break", true).build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .visibleWhen(() -> bool("auto-sword"))
            .build());
        add(new ChoiceSetting("throwables", "Throwables", "MainHand", "MainHand", "BothHands")
            .description("Hands checked for throwables")
            .build());
        add(new BoolSetting("hit-marker", "Render", true).build());
        add(new BoolSetting("hitsound", "Hitsound", true).build());
    }

    @Override
    public void onEnable() {
        resetRuntime();

    }

    @Override
    public void onDisable() {

        resetRuntime();

        AutismKillAuraRotation.beginWindDown(AutismKillAuraRotation.OWNER_KILL_AURA);
    }

    @Override
    public void onGameLeft() {
        resetRuntime();

        if (AutismKillAuraRotation.OWNER_KILL_AURA.equals(AutismKillAuraRotation.currentOwner())) {
            AutismKillAuraRotation.reset();
        }

        accuracyGovernor.reset();
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("entities".equals(settingId)) cachedEntityListSource = null;
        if ("auto-sword".equals(settingId) && !bool("auto-sword")) {

            if (previousSlot >= 0 && MC != null && MC.player != null
                && MC.gui.screen() == null && MC.gui.overlay() == null
                && !AutismBlinkManager.holdsActionsWithoutMovement()
                && AutismHandArbiter.beginHandPacketGroup(id())) {
                try {
                    AutismInventoryHelper.selectHotbarSlot(MC, previousSlot);
                } finally {
                    AutismHandArbiter.endHandPacketGroup(id());
                }
            }
            previousSlot = -1;
            switchBackTicks = 0;
        }
        if ("switch-back".equals(settingId) && !bool("switch-back")) {
            previousSlot = -1;
            switchBackTicks = 0;
        }
        if ("shield-break".equals(settingId) && !bool("shield-break")) {

            if (previousSlot >= 0 && MC != null && MC.player != null
                && MC.gui.screen() == null && MC.gui.overlay() == null
                && !AutismBlinkManager.holdsActionsWithoutMovement()
                && AutismHandArbiter.beginHandPacketGroup(id())) {
                try {
                    AutismInventoryHelper.selectHotbarSlot(MC, previousSlot);
                } finally {
                    AutismHandArbiter.endHandPacketGroup(id());
                }
            }
            previousSlot = -1;
            switchedToSlot = -1;
            switchBackTicks = 0;
            shieldHoldEntityId = -1;
            lastShieldSeenTick = Integer.MIN_VALUE;
            shieldBreakLandedTick = Integer.MIN_VALUE;
            shieldBreakLandedEntityId = -1;
        }
    }

    @Override
    public void tick() {

        if (!isEnabled() && MC != null && MC.player != null) {
            AutismKillAuraRotation.update(AutismKillAuraRotation.OWNER_KILL_AURA, MC.player);
        }
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return AutismKillAuraRotation.hasCurrentRotation();
    }

    @Override
    public void preMovementTick() {
        if (MC == null || MC.player == null || MC.level == null) return;

        if (AutismSilentAim.scaffoldOwnsRotation() || ScaffoldModule.reservesRageInput()) {
            currentTarget = null;
            missState.clear();

            if (AutismKillAuraRotation.OWNER_KILL_AURA.equals(AutismKillAuraRotation.currentOwner())) {
                AutismKillAuraRotation.reset();
            }

            accuracyGovernor.forgetPacketHistory();
            return;
        }

        boolean usingItem = isUsingHeldItem();
        if (usingItem) postUseSuppressTicks = POST_USE_SUPPRESS_TICKS;
        else if (postUseSuppressTicks > 0) postUseSuppressTicks--;

        boolean throwable = throwableHeldThisTick();
        if (isBreakingBlock() || usingItem || throwable) {

            currentTarget = null;
            missState.clear();

            AutismKillAuraRotation.beginWindDown(AutismKillAuraRotation.OWNER_KILL_AURA);
            clicker.tick();
            confirmHitFeedback();
            AutismKillAuraRotation.update(AutismKillAuraRotation.OWNER_KILL_AURA, MC.player);
            recordOutgoingRotation();
            return;
        }

        clicker.tick();
        confirmHitFeedback();

        boolean postAttackWindow = accuracyGovernor.attackedRecently();

        if (canRun()) {
            updateTargetRotation(postAttackWindow);
        } else {
            currentTarget = null;
            missState.clear();
        }

        tickAutoSwordReset();

        boolean inAttackWindow = postAttackWindow || attackImminentThisTick();
        boolean throttle = inAttackWindow && accuracyGovernor.speedAtRisk();
        AutismKillAuraRotation.update(AutismKillAuraRotation.OWNER_KILL_AURA, MC.player,
            !inAttackWindow ? AutismKillAuraRotation.TURN_SPEED
                : throttle ? THROTTLED_MAX_YAW_STEP : WINDOW_MAX_YAW_STEP,
            !inAttackWindow ? AutismKillAuraRotation.TURN_SPEED
                : throttle ? THROTTLED_MAX_PITCH_STEP : WINDOW_MAX_PITCH_STEP);

        if (canRun()) {
            attackPhase();
        }

        recordOutgoingRotation();
    }

    private void recordOutgoingRotation() {
        AutismRotationUtil.Rotation sent = AutismKillAuraRotation.getCurrentRotation();
        if (sent == null) sent = AutismRotationUtil.playerRotation(MC.player);
        accuracyGovernor.onOutgoingRotation(sent.yaw(), sent.pitch(), perfectYaw(currentTarget));
    }

    private float perfectYaw(LivingEntity target) {
        if (target == null || MC.player == null) return Float.NaN;
        double diffX = target.getX() - MC.player.getX();
        double diffZ = target.getZ() - MC.player.getZ();
        return (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D);
    }

    private boolean isBreakingBlock() {
        return MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor
            && accessor.autism$isDestroying();
    }

    private boolean isUsingHeldItem() {
        return MC.player != null && MC.player.isUsingItem();
    }

    static boolean isInstantThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof EnderpearlItem
            || item instanceof SnowballItem
            || item instanceof EggItem
            || item instanceof ExperienceBottleItem
            || item instanceof ThrowablePotionItem
            || item instanceof WindChargeItem;
    }

    private boolean holdsInstantThrowable() {
        if (MC.player == null) return false;
        ItemStack mainHand = MC.player.getMainHandItem();
        if (isInstantThrowable(mainHand)) return true;
        if (!"BothHands".equals(choice("throwables")) && !mainHand.isEmpty()) return false;
        return isInstantThrowable(MC.player.getOffhandItem());
    }

    static final class TickVerdict {
        private int tick = Integer.MIN_VALUE;
        private boolean value;

        boolean resolve(int clientTick, BooleanSupplier live) {
            if (clientTick != tick) {
                tick = clientTick;
                value = live.getAsBoolean();
            }
            return value;
        }
    }

    private final TickVerdict throwableVerdict = new TickVerdict();

    private boolean throwableHeldThisTick() {
        return throwableVerdict.resolve(
            AutismSharedState.get().getClientTickCounter(), this::holdsInstantThrowable);
    }

    private boolean canRun() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.gameMode != null
            && MC.getConnection() != null
            && !MC.player.isDeadOrDying()
            && !MC.player.isSpectator()
            && !MC.player.isUsingItem()
            && !throwableHeldThisTick()
            && !PackHideState.isActive()
            && !PackFreecamState.isActive()
            && !AutismRemoteView.isActive()
            && !MultiPilot.isActive()
            && !MacroExecutor.isRunning()
            && !PacketTeleportController.ownsMainMovement()
            && !BuiltinModules.ownsManualFastExp()
            && !ScaffoldModule.reservesTellyInput()
            && !ScaffoldModule.hasActiveSilentMovementRotation()

            && !ScaffoldModule.reservesRageInput()

            && !BedDefenderModule.ownsSilentRotation()
            && !SurroundModule.ownsSilentRotation()
            && !CrystalAuraModule.reservesCombatTick()
            && !AnchorAuraModule.reservesCombatTick()
            && !autoTrapOwnsSilentRotation()

            && !AutismBlinkManager.holdsActionsWithoutMovement()

            && !AutoTotemModule.operationActive()
            && !AutoArmorModule.operationActive();
    }

    private static boolean missTimeActive() {
        return ((AutismMinecraftAccessor) MC).autism$getMissTime() > 0;
    }

    private static boolean autoTrapOwnsSilentRotation() {
        return AutismKillAuraRotation.OWNER_AUTO_TRAP.equals(AutismKillAuraRotation.currentOwner())
            && AutismKillAuraRotation.hasCurrentRotation();
    }

    static boolean silentCorrectionApplies(boolean windingDown, boolean enabled, boolean canRun,
                                           boolean scaffoldOwnsRotation) {
        return !scaffoldOwnsRotation && (windingDown || enabled && canRun);
    }

    @Override
    public boolean silentCorrectionApplies() {
        boolean enabled = isEnabled();
        return silentCorrectionApplies(AutismKillAuraRotation.isWindingDown(), enabled,
            enabled && canRun(), AutismSilentAim.scaffoldOwnsRotation());
    }

    private static KillAuraModule activeInstance() {
        Module module = ModuleRegistry.get("kill-aura");
        return module instanceof KillAuraModule aura && aura.isEnabled() ? aura : null;
    }

    public LivingEntity currentTarget() {
        return currentTarget;
    }

    @Override
    public boolean shouldCancelAttack(HitResult hitResult) {
        if (!isEnabled() || currentTarget == null || !canRun()) return false;
        return hitResult == null || hitResult.getType() != HitResult.Type.BLOCK;
    }

    private void updateTargetRotation(boolean inAttackWindow) {

        boolean allowExcursion = !accuracyGovernor.errorAtRisk();
        double interactionRange = interactionRange();
        double normalRangeSq = interactionRange * interactionRange;

        double maximumRange = closestSquaredEnemyDistance > normalRangeSq ? scanRange() : interactionRange;
        double maximumRangeSq = maximumRange * maximumRange;

        List<LivingEntity> targets = collectTargets();

        List<LivingEntity> filtered = new ArrayList<>();
        for (LivingEntity entity : targets) {
            if (boxedDistanceToPlayerSqr(entity) <= maximumRangeSq) filtered.add(entity);
        }

        filtered.sort(Comparator.<LivingEntity>comparingInt(entity ->
            boxedDistanceToPlayerSqr(entity) <= normalRangeSq ? 0 : 1));

        LivingEntity chosen = null;
        AutismRotationUtil.Rotation chosenRotation = null;

        int bestRangeBucket = filtered.isEmpty() ? Integer.MAX_VALUE
            : boxedDistanceToPlayerSqr(filtered.get(0)) <= normalRangeSq ? 0 : 1;
        if (currentTarget != null) {
            for (LivingEntity entity : filtered) {
                if (entity != currentTarget) continue;
                int currentRangeBucket = boxedDistanceToPlayerSqr(entity) <= normalRangeSq ? 0 : 1;

                if (currentRangeBucket != bestRangeBucket) break;
                Vec3 preferred = aimPointTracker.advance(entity.getId(), entity.getBoundingBox(),
                    MC.player.getEyePosition(), inAttackWindow, allowExcursion);
                chosenRotation = rotationForAimPoint(entity, maximumRange, preferred);
                if (chosenRotation != null) chosen = entity;
                break;
            }
        }

        if (chosen == null) {
            for (LivingEntity entity : filtered) {
                if (entity == currentTarget) continue;
                AABB box = entity.getBoundingBox();

                Vec3 acquisition = safeAimPoint(box, 0.50D, 0.56D, 0.50D);
                AutismRotationUtil.Rotation rotation = findRotation(entity, maximumRange, acquisition);
                if (rotation == null) continue;

                Vec3 preferred = aimPointTracker.begin(entity.getId(), box,
                    MC.player.getEyePosition(), inAttackWindow, allowExcursion);
                AutismRotationUtil.Rotation tracked = rotationForAimPoint(entity, maximumRange, preferred);
                chosen = entity;
                chosenRotation = tracked != null ? tracked : rotation;
                break;
            }
        }

        if (chosenRotation != null) {

            chosenRotation = applyMissOverride(chosen, chosenRotation);

            AutismKillAuraRotation.setTarget(chosenRotation);
        } else {
            aimPointTracker.clear();
            missState.clear();
        }
        currentTarget = chosen;
    }

    private List<LivingEntity> collectTargets() {
        List<LivingEntity> entities = new ArrayList<>();
        Vec3 eyes = MC.player.getEyePosition();
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && validate(living, eyes)) {
                entities.add(living);
            }
        }
        if (entities.isEmpty()) {

            return entities;
        }

        entities.sort(targetComparator());

        double closest = Double.MAX_VALUE;
        for (LivingEntity entity : entities) {
            closest = Math.min(closest, boxedDistanceToPlayerSqr(entity));
        }
        closestSquaredEnemyDistance = closest;
        return entities;
    }

    private boolean validate(LivingEntity entity, Vec3 eyes) {
        if (entity == MC.player) return false;
        if (entity.isRemoved()) return false;
        if (entity.hurtTime > HURT_TIME) return false;
        if (!shouldBeAttacked(entity)) return false;

        return crosshairAngleToEntity(entity, eyes) <= integer("fov") * 0.5F;
    }

    private boolean shouldBeAttacked(LivingEntity entity) {
        if (!(entity instanceof Attackable)) return false;
        if (!EntitySelector.CAN_BE_PICKED.test(entity)) return false;
        if (entity == MC.player || entity.hasPassenger(MC.player)) return false;

        if (AutismAntiBot.suppress(entity)) return false;

        if (TeamsModule.combatExcluded(entity, "killaura")) return false;

        if (!entity.isAlive()) return false;

        if (entity instanceof Player player && player.isSleeping()) return false;
        return matchesEntity(entity);
    }

    private float crosshairAngleToEntity(Entity entity, Vec3 eyes) {
        AutismRotationUtil.Rotation toCenter =
            AutismRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
        return AutismRotationUtil.rotationAngleTo(AutismRotationUtil.playerRotation(MC.player), toCenter);
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

    private int typeWeight(LivingEntity entity) {
        if (entity instanceof Player) return 0;
        if (entity instanceof Enemy) return 1;
        if (entity instanceof NeutralMob neutral) {
            EntityReference<LivingEntity> angerTarget = neutral.getPersistentAngerTarget();
            if (angerTarget != null && angerTarget.matches(MC.player)) return 2;
        }
        return Integer.MAX_VALUE;
    }

    private Comparator<LivingEntity> targetComparator() {
        return switch (choice("targeting")) {
            case "HP" -> Comparator.comparingDouble(this::actualHealth);
            case "Distance" -> Comparator.comparingDouble(this::boxedDistanceToPlayerSqr);
            case "FOV" -> Comparator.comparingDouble(entity ->
                crosshairAngleToEntity(entity, MC.player.getEyePosition()));
            case "Hurt Time" -> Comparator.comparingInt(entity -> entity.hurtTime);
            case "Age" -> Comparator.comparingInt(entity -> -entity.tickCount);
            default -> Comparator.comparingInt(this::typeWeight);
        };
    }

    private float actualHealth(LivingEntity entity) {
        try {
            var scoreboard = entity.level().getScoreboard();
            var objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
            if (objective != null) {
                String displayName = objective.getDisplayName().getString();
                if (displayName.contains("❤") || displayName.contains("HP")
                    || displayName.contains("Health") || displayName.contains("Здоровья")
                    || displayName.contains("Здоровье")) {
                    var score = scoreboard.getPlayerScoreInfo(entity, objective);
                    if (score != null) return score.value();
                }
            }
        } catch (Throwable ignored) {

        }
        return entity.getHealth();
    }

    private double boxedDistanceToPlayerSqr(Entity entity) {
        return entity.getBoundingBox().inflate(entity.getPickRadius())
            .distanceToSqr(MC.player.getEyePosition());
    }

    private AutismRotationUtil.Rotation findRotation(Entity entity, double range, Vec3 preferredPoint) {
        Vec3 eyes = MC.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double rangeSq = range * range;
        Vec3 preferred = clampToSafeAimPoint(box, preferredPoint);

        AutismRotationUtil.Rotation direct = visibleInteriorRotation(eyes, box, preferred, rangeSq);
        if (direct != null) return direct;

        AutismRotationUtil.Rotation reference = AutismKillAuraRotation.getCurrentRotation();
        if (reference == null) reference = AutismRotationUtil.playerRotation(MC.player);
        AutismRotationUtil.Rotation best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double diagonal = Math.sqrt(box.getXsize() * box.getXsize()
            + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize());
        diagonal = Math.max(diagonal, 1.0E-6D);

        for (double x : AIM_SCAN_HORIZONTAL) for (double y : AIM_SCAN_VERTICAL) {
            for (double z : AIM_SCAN_HORIZONTAL) {
                Vec3 point = safeAimPoint(box, x, y, z);
                AutismRotationUtil.Rotation candidate = visibleInteriorRotation(eyes, box, point, rangeSq);
                if (candidate == null) continue;
                double pointDistance = Math.sqrt(point.distanceToSqr(preferred)) / diagonal;
                double score = AutismRotationUtil.rotationAngleTo(reference, candidate) + pointDistance * 8.0D;
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private AutismRotationUtil.Rotation rotationForAimPoint(Entity entity, double range, Vec3 point) {
        if (!entity.getBoundingBox().contains(point)) {
            return AutismRotationUtil.lookingAt(point, MC.player.getEyePosition());
        }
        return findRotation(entity, range, point);
    }

    private AutismRotationUtil.Rotation visibleInteriorRotation(Vec3 eyes, AABB box, Vec3 point,
                                                                 double rangeSq) {
        if (eyes.distanceToSqr(point) <= 1.0E-12D) return null;
        Vec3 hit = box.contains(eyes)
            ? eyes
            : firstHit(box, eyes, fma(eyes, 1.25D, point.subtract(eyes)));
        if (hit == null || !(eyes.distanceToSqr(hit) < rangeSq)) return null;
        if (!aimVisibility(eyes, hit)) return null;
        return AutismRotationUtil.lookingAt(point, eyes);
    }

    static Vec3 safeAimPoint(AABB box, double x, double y, double z) {
        double safeX = Mth.clamp(x, AIM_HORIZONTAL_INSET, 1.0D - AIM_HORIZONTAL_INSET);
        double safeY = Mth.clamp(y, AIM_VERTICAL_INSET, 1.0D - AIM_VERTICAL_INSET);
        double safeZ = Mth.clamp(z, AIM_HORIZONTAL_INSET, 1.0D - AIM_HORIZONTAL_INSET);
        return new Vec3(
            Math.fma(box.getXsize(), safeX, box.minX),
            Math.fma(box.getYsize(), safeY, box.minY),
            Math.fma(box.getZsize(), safeZ, box.minZ));
    }

    private static Vec3 clampToSafeAimPoint(AABB box, Vec3 point) {
        if (point == null) return safeAimPoint(box, 0.50D, 0.56D, 0.50D);
        return safeAimPoint(box,
            normalized(point.x, box.minX, box.maxX),
            normalized(point.y, box.minY, box.maxY),
            normalized(point.z, box.minZ, box.maxZ));
    }

    private static double normalized(double value, double min, double max) {
        double size = max - min;
        return size > 1.0E-9D ? (value - min) / size : 0.5D;
    }

    private static Vec3 firstHit(AABB box, Vec3 from, Vec3 to) {
        return (box.contains(from) ? box.clip(to, from) : box.clip(from, to)).orElse(null);
    }

    private static Vec3 fma(Vec3 base, double scale, Vec3 other) {
        return new Vec3(
            Math.fma(scale, other.x, base.x),
            Math.fma(scale, other.y, base.y),
            Math.fma(scale, other.z, base.z));
    }

    private boolean aimVisibility(Vec3 eyes, Vec3 point) {
        return hasLineOfSight(eyes, point, MC.player);
    }

    private boolean hasLineOfSight(Vec3 eyes, Vec3 point, Entity entity) {
        return MC.level.clip(new ClipContext(
            eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
            .getType() == HitResult.Type.MISS;
    }

    private EntityHitResult findEntityInCrosshair(double range, AutismRotationUtil.Rotation rotation,
                                                  java.util.function.Predicate<Entity> predicate) {
        Entity camera = MC.getCameraEntity();
        if (camera == null) return null;
        Vec3 eyes = camera.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
        Vec3 end = eyes.add(direction.x * range, direction.y * range, direction.z * range);
        AABB search = camera.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0D, 1.0D, 1.0D);
        return ProjectileUtil.getEntityHitResult(
            camera, eyes, end, search, EntitySelector.CAN_BE_PICKED.and(predicate), range * range);
    }

    private EntityHitResult isLookingAtEntity(Entity target, AutismRotationUtil.Rotation rotation,
                                              double range, double wallsRange) {
        Entity camera = MC.getCameraEntity();
        if (camera == null) return null;
        EntityHitResult hit = findEntityInCrosshair(range, rotation, entity -> entity == target);
        if (hit == null || hit.getEntity() != target) return null;
        Vec3 eyes = camera.getEyePosition();
        double distanceSq = eyes.distanceToSqr(hit.getLocation());
        return distanceSq <= wallsRange * wallsRange
            || distanceSq <= range * range && hasLineOfSight(eyes, hit.getLocation(), camera)
            ? hit : null;
    }

    private void attackPhase() {
        LivingEntity target = currentTarget;
        if (target == null) return;

        if (ScaffoldModule.hasActiveSilentMovementRotation()) {
            missState.clear();
            return;
        }

        if (missState.isPending()) {

            if (missState.isFireTick()) {

                AutismRotationUtil.Rotation current = AutismKillAuraRotation.getCurrentRotation();
                AutismRotationUtil.Rotation toMiss = AutismRotationUtil.lookingAt(
                    missState.point(), MC.player.getEyePosition());
                if (current == null
                    || AutismRotationUtil.rotationAngleTo(current, toMiss) > WHIFF_CONVERGENCE_DEG) {
                    return;
                }

                if (autismclient.util.AutismCombatClicker.queueAttackMiss(
                    net.minecraft.world.phys.BlockHitResult.miss(
                        MC.player.getEyePosition(), net.minecraft.core.Direction.DOWN,
                        MC.player.blockPosition()))) {
                    missState.advance();
                }
            } else {

                missState.advance();
            }
            return;
        }

        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation == null) rotation = AutismRotationUtil.playerRotation(MC.player);

        attackTarget(target, rotation);
    }

    private boolean attackImminentThisTick() {
        LivingEntity target = currentTarget;
        if (target == null || missState.isPending() || !clicker.isClickTick()) return false;
        ItemStack stack = MC.player.getMainHandItem();
        if (!canAttackNow(target, stack)) return false;

        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation == null) rotation = AutismRotationUtil.playerRotation(MC.player);
        EntityHitResult hit = isLookingAtEntity(target, rotation, interactionRange(), THROUGH_WALLS_RANGE);
        return hit != null && attackRangeIsInRange(stack, hit.getLocation());
    }

    private void attackTarget(Entity target, AutismRotationUtil.Rotation rotation) {
        EntityHitResult attackHit =
            isLookingAtEntity(target, rotation, interactionRange(), THROUGH_WALLS_RANGE);

        boolean isInRange = attackHit != null
            && attackRangeIsInRange(MC.player.getMainHandItem(), attackHit.getLocation());
        if (!isInRange) return;

        if (prepareWeaponSwitchTick(target)) return;
        if (performDueSwitchBack()) return;

        ItemStack mainHandStack = MC.player.getMainHandItem();

        boolean shieldBreakHit = target instanceof LivingEntity living && shieldBreakHitPending(living);
        if ((!clicker.isClickTick() && !shieldBreakHit) || !canAttackNow(target, mainHandStack)) return;

        clicker.shieldBreakBypass = shieldBreakHit;
        clickerPrepareForAttack(() -> {

            if (!canAttackNow(target, mainHandStack)) return false;
            if (!attackEntity(target, attackHit)) return false;

            boolean brokeShield = shieldBreakHit && mainHandStack.is(ItemTags.AXES);
            if (brokeShield) {
                shieldBreakLandedTick = AutismSharedState.get().getClientTickCounter();
                shieldBreakLandedEntityId = target.getId();
            }

            accuracyGovernor.onAttackSent();
            missState.onAttackFired();

            if (bool("switch-back") && previousSlot >= 0) {
                switchBackTicks = brokeShield ? 0 : AUTO_SWORD_SWITCH_BACK_TICKS;
            }
            scanAddition = nextScanAddition();
            return true;
        });
    }

    private AutismRotationUtil.Rotation applyMissOverride(LivingEntity target,
                                                          AutismRotationUtil.Rotation rotation) {
        if (missState.isPending()) {
            if (missState.matches(target.getId())) {
                return AutismRotationUtil.lookingAt(missState.point(), MC.player.getEyePosition());
            }
            missState.clear();
            return rotation;
        }
        if (!missState.mayRoll() || !bool("miss-injection") || !clicker.isClickTick()) return rotation;
        if (!canAttackNow(target, MC.player.getMainHandItem())) return rotation;
        if (wouldBeLethal(target)) return rotation;

        if (shieldBreakHitPending(target)) return rotation;
        if (missRandom.nextDouble() >= integer("miss-chance") * 0.01D) return rotation;
        missState.begin(target.getId(),
            missAimPoint(target.getBoundingBox(), MC.player.getEyePosition(), missRandom));
        return AutismRotationUtil.lookingAt(missState.point(), MC.player.getEyePosition());
    }

    private boolean wouldBeLethal(LivingEntity target) {

        return target.getHealth() <= MC.player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5D;
    }

    static Vec3 missAimPoint(AABB box, Vec3 eyes, Random random) {
        Vec3 center = box.getCenter();
        double halfWidth = Math.min(box.getXsize(), box.getZsize()) * 0.5D;
        double lateral = halfWidth + MISS_LATERAL_MIN
            + random.nextDouble() * (MISS_LATERAL_MAX - MISS_LATERAL_MIN);
        if (random.nextBoolean()) lateral = -lateral;
        double depth = (random.nextDouble() * 2.0D - 1.0D) * MISS_DEPTH_JITTER;
        double dy = (random.nextDouble() * 2.0D - 1.0D) * MISS_VERTICAL_JITTER;
        if (dy == 0.0D) dy = 0.01D;
        double dx = center.x - eyes.x;
        double dz = center.z - eyes.z;
        double horizontal = Math.max(Math.sqrt(dx * dx + dz * dz), 0.3D);
        double dirX = dx / horizontal;
        double dirZ = dz / horizontal;
        return new Vec3(
            center.x + dirX * depth - dirZ * lateral,
            center.y + dy,
            center.z + dirZ * depth + dirX * lateral);
    }

    private boolean prepareWeaponSwitchTick(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        boolean shieldBreak = shieldBreakEngaged(living);
        if (!bool("auto-sword") && !shieldBreak) return false;
        Integer slot = determineWeaponSlot(living, shieldBreak);
        if (slot == null || isAutoWeaponBusy()) return false;
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected == slot) return false;

        if (bool("switch-back")) {
            if (previousSlot < 0) previousSlot = selected;
            switchBackTicks = AUTO_SWORD_SWITCH_BACK_TICKS;
        }

        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == hotbarChangeTick) return true;

        if (!AutismHandArbiter.beginHandPacketGroup(id())) return true;
        try {
            AutismInventoryHelper.selectHotbarSlot(MC, slot);
            switchedToSlot = slot;
            hotbarChangeTick = tick;
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
        return true;
    }

    private boolean shieldBreakEngaged(LivingEntity target) {
        if (!bool("shield-break")) return false;
        int tick = AutismSharedState.get().getClientTickCounter();
        if (wouldBlockHit(target)) {
            lastShieldSeenTick = tick;
            shieldHoldEntityId = target.getId();
            return true;
        }

        if (target instanceof Player && target.getId() == shieldBreakLandedEntityId
            && tick - shieldBreakLandedTick <= SHIELD_DISABLE_TICKS) {
            return false;
        }

        return target.getId() == shieldHoldEntityId
            && tick - lastShieldSeenTick <= SHIELD_BREAK_HOLD_TICKS;
    }

    private boolean shieldBreakHitPending(LivingEntity target) {
        return bool("shield-break") && wouldBlockHit(target);
    }

    private boolean performDueSwitchBack() {

        if (!bool("switch-back") || previousSlot < 0 || switchBackTicks > 0) return false;

        if (switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {

            int selected = MC.player.getInventory().getSelectedSlot();
            if (CrystalAuraModule.holdsBorrowedSlot(selected)
                || AnchorAuraModule.holdsBorrowedSlot(selected)) {
                return false;
            }
            previousSlot = -1;
            switchedToSlot = -1;
            return false;
        }
        int back = previousSlot;
        if (MC.player.getInventory().getSelectedSlot() == back) {
            previousSlot = -1;
            switchedToSlot = -1;
            return false;
        }

        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == hotbarChangeTick) return false;

        if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
        try {
            AutismInventoryHelper.selectHotbarSlot(MC, back);
            hotbarChangeTick = tick;
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }
        previousSlot = -1;
        switchedToSlot = -1;
        return true;
    }

    private boolean canAttackNow(Entity target, ItemStack stack) {
        if (!stack.isItemEnabled(MC.level.enabledFeatures())) return false;
        if (MC.player.cannotAttackWithItem(stack, 0)) return false;

        if (ScaffoldModule.hasActiveSilentMovementRotation()) return false;

        if (postUseSuppressTicks > 0) return false;

        return !(bool("criticals") && target instanceof LivingEntity living
            && !MC.player.isFallFlying()
            && !shieldBreakHitPending(living)
            && shouldWaitForCrit());
    }

    private boolean shouldWaitForCrit() {
        double motionY = MC.player.getDeltaMovement().y;
        if (!allowsCriticalHit() || motionY < -0.08D) return false;
        float ticksTillCrit = Math.max(ticksUntilNextCrit(), (float) (motionY / 0.08D));
        float damageOnCrit = 0.5F * 0.75F;
        if (damageOnCrit <= cooldownDamageFactor(ticksTillCrit)) return false;
        return willStayAirborne((int) (ticksTillCrit * 1.3F));
    }

    private float ticksUntilNextCrit() {
        return Math.max(currentItemAttackStrengthDelay() * 0.9F - 0.5F - attackStrengthTicker(), 0.0F);
    }

    private float cooldownDamageFactor(float ticks) {
        float base = (ticks + 0.5F) / currentItemAttackStrengthDelay();
        return Math.min(0.2F + base * base * 0.8F, 1.0F);
    }

    private boolean willStayAirborne(int ticks) {
        double motionY = MC.player.getDeltaMovement().y;
        AABB box = MC.player.getBoundingBox();
        for (int i = 0; i < ticks; i++) {
            motionY = (motionY - 0.08D) * 0.98D;
            box = box.move(0.0D, motionY, 0.0D);
            if (!MC.level.noCollision(MC.player, box)) return false;
        }
        return true;
    }

    private boolean shouldStopSprintingForCrit() {

        return bool("criticals")
            && MC.player != null && !MC.player.onGround()
            && currentTarget != null && clicker.willClickAt(1)
            && !(currentTarget instanceof LivingEntity living && shieldBreakHitPending(living));
    }

    public static boolean blocksSprintForCrit() {
        KillAuraModule aura = activeInstance();
        return aura != null && aura.shouldStopSprintingForCrit();
    }

    public static boolean holdsBorrowedSlot(int slot) {
        Module module = ModuleRegistry.get("kill-aura");
        if (!(module instanceof KillAuraModule aura) || !aura.isEnabled()) return false;
        return aura.previousSlot >= 0 && aura.switchedToSlot == slot;
    }

    private void clickerPrepareForAttack(BooleanSupplier attack) {
        if (!clicker.canExecuteClickNow()) return;
        if (MC.player.isBlocking()) return;
        if (MC.player.isUsingItem()) return;
        clicker.click(attack);
    }

    private boolean attackEntity(Entity target, EntityHitResult hit) {
        ItemStack stack = MC.player.getMainHandItem();
        var piercing = stack.get(DataComponents.PIERCING_WEAPON);

        if (piercing != null && !MC.gameMode.isSpectator()) {
            MC.gameMode.piercingAttack(piercing);
            MC.player.swing(InteractionHand.MAIN_HAND);
            autismclient.util.AutismCpsTracker.recordLeft();
            queueHitFeedback(target);
            return true;
        }

        if (!canBeAttackedWithVanillaPacket(target)) return false;

        if (!AutismHandArbiter.beginHandPacketGroup(id())) return false;
        try {
            ((AutismMultiPlayerGameModeAccessor) MC.gameMode).autism$ensureHasSentCarriedItem();
        } finally {
            AutismHandArbiter.endHandPacketGroup(id());
        }

        if ("Packet".equals(choice("attack-mode"))) {

            MC.getConnection().send(new ServerboundAttackPacket(target.getId()));
            if (!MC.gameMode.isSpectator()) {
                MC.player.attack(target);
                MC.player.resetAttackStrengthTicker();
            }
            MC.player.swing(InteractionHand.MAIN_HAND);
            autismclient.util.AutismCpsTracker.recordLeft();
            queueHitFeedback(target);
            return true;
        }

        if (!autismclient.util.AutismCombatClicker.queueAttack(hit)) return false;
        queueHitFeedback(target);
        MC.player.resetAttackStrengthTicker();
        return true;
    }

    private boolean canBeAttackedWithVanillaPacket(Entity target) {
        return target != null
            && target != MC.player
            && !(target instanceof ItemEntity)
            && !(target instanceof ExperienceOrb)
            && (!(target instanceof AbstractArrow) || target.isAttackable());
    }

    private void queueHitFeedback(Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        if (!bool("hitsound") && !bool("hit-marker")) return;
        pendingHitEntityId = living.getId();
        pendingHitPrevHurtTime = living.hurtTime;
        pendingHitTicks = HIT_CONFIRM_TICKS;
    }

    private void confirmHitFeedback() {
        if (pendingHitEntityId < 0) return;
        Entity entity = MC.level.getEntity(pendingHitEntityId);
        boolean landed = entity instanceof LivingEntity living
            && (living.hurtTime > pendingHitPrevHurtTime
                || pendingHitPrevHurtTime >= HURT_TIME && living.hurtTime >= HURT_TIME);
        if (landed) {
            showHitMarker(entity);
            playHitsound();
            autismclient.util.AutismChamsHit.mark(entity);
        }
        if (landed || entity == null || --pendingHitTicks <= 0) {
            pendingHitEntityId = -1;
        }
    }

    private void showHitMarker(Entity target) {
        if (!bool("hit-marker")) return;
        AutismKillAuraRenderer.show(target.getBoundingBox().inflate(target.getPickRadius()));
    }

    private void playHitsound() {
        if (!bool("hitsound")) return;
        MC.getSoundManager().play(SimpleSoundInstance.forUI(HITSOUND, 1.0F, 0.7F));
    }

    private boolean wouldDoCriticalHit() {
        return canDoCriticalHit() && MC.player.fallDistance > 0.0F;
    }

    private boolean canDoCriticalHit() {
        return allowsCriticalHit() && MC.player.getAttackStrengthScale(0.5F) > 0.9F;
    }

    private boolean allowsCriticalHit() {
        Module flight = ModuleRegistry.get("flight");
        boolean flyRunning = flight != null && flight.isEnabled();
        return !flyRunning
            && !MC.player.isInLiquid()
            && !MC.player.isPassenger()
            && !insideWebBlock()
            && !MC.player.hasEffect(MobEffects.LEVITATION)
            && !MC.player.hasEffect(MobEffects.BLINDNESS)
            && !MC.player.hasEffect(MobEffects.SLOW_FALLING)
            && !MC.player.onClimbable()
            && !MC.player.isNoGravity()
            && !MC.player.isHandsBusy()
            && !MC.player.getAbilities().flying
            && !MC.player.onGround();
    }

    private boolean insideWebBlock() {
        return MC.level.getBlockStates(MC.player.getBoundingBox())
            .anyMatch(state -> state.getBlock() instanceof WebBlock);
    }

    private double interactionRange() {
        return MC.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    private double scanRange() {
        return Math.max(interactionRange(), THROUGH_WALLS_RANGE) + scanAddition;
    }

    private boolean attackRangeIsInRange(ItemStack stack, Vec3 pos) {
        AttackRange attackRange = stack.get(DataComponents.ATTACK_RANGE);
        if (attackRange == null) attackRange = AttackRange.defaultFor(MC.player);
        return attackRange.isInRange(MC.player, pos);
    }

    private double nextScanAddition() {
        return SCAN_ADDITION_MIN + random.nextDouble() * (SCAN_ADDITION_MAX - SCAN_ADDITION_MIN);
    }

    private boolean hasCooldown() {
        return MC.player.getAttributeValue(Attributes.ATTACK_SPEED) < 20.0D;
    }

    private float currentItemAttackStrengthDelay() {
        double attackSpeed = MC.player.getAttributeValue(Attributes.ATTACK_SPEED);

        if (bool("auto-sword") && hasCooldown() && switchedToSlot < 0) {
            Integer slot = determineWeaponSlot(null, false);
            if (slot != null) {
                attackSpeed = attributeValue(MC.player.getInventory().getItem(slot),
                    Attributes.ATTACK_SPEED, MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
            }
        }
        return (float) (1.0D / attackSpeed * 20.0D);
    }

    private int attackStrengthTicker() {
        return ((AutismLivingEntityAccessor) MC.player).autism$getAttackStrengthTicker();
    }

    private boolean isCooldownPassed(int ticks) {
        float delay = currentItemAttackStrengthDelay();
        return (attackStrengthTicker() + ticks) / delay >= nextCooldown + clickOffsetTicks / delay;
    }

    private float nextCooldown = 1.0F;

    private float clickOffsetTicks = rollClickOffsetTicks();

    private void newCooldown() {
        nextCooldown = 1.0F;
        clickOffsetTicks = rollClickOffsetTicks();
    }

    private float rollClickOffsetTicks() {

        double magnitude = (random.nextDouble() + random.nextDouble()) * 0.5D;
        return (float) (random.nextDouble() < 0.2D ? -magnitude : magnitude);
    }

    private boolean wouldBlockHit(LivingEntity target) {
        DamageSource source = target.level().damageSources().playerAttack(MC.player);
        return getBlockedDamage(target, source, 1.0F) > 0.0F;
    }

    private float getBlockedDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0F) return 0.0F;
        ItemStack blockingStack = target.getItemBlockingWith();
        if (blockingStack == null) return 0.0F;
        var blocksAttacks = blockingStack.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks == null) return 0.0F;
        if (blocksAttacks.bypassedBy().map(tag -> tag.contains(source.typeHolder())).orElse(false)) return 0.0F;
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) return 0.0F;

        double horizontalAngle = Math.PI;
        Vec3 sourcePosition = source.getSourcePosition();
        if (sourcePosition != null) {
            Vec3 view = target.calculateViewVector(0.0F, target.getYHeadRot());
            Vec3 to = sourcePosition.subtract(target.position());
            Vec3 sourceDirection = new Vec3(to.x, 0.0D, to.z).normalize();
            horizontalAngle = Math.acos(sourceDirection.dot(view));
        }
        return blocksAttacks.resolveBlockedDamage(source, amount, horizontalAngle);
    }

    static final class RollingClickArray {
        private final int cycleLength;
        final int iterations;
        private final int[] array;
        private int head;

        RollingClickArray(int cycleLength, int iterations) {
            this.cycleLength = cycleLength;
            this.iterations = iterations;
            this.array = new int[cycleLength * iterations];
        }

        int get(int relativeIndex) {
            return array[(head + relativeIndex) % array.length];
        }

        boolean advance(int amount) {
            head = (head + amount) % array.length;
            return head % cycleLength == 0;
        }

        void clear() {
            Arrays.fill(array, 0);
            head = 0;
        }

        void push(int[] cycle) {
            if (cycle.length != cycleLength) {
                throw new IllegalArgumentException("Array size must match cycle length");
            }
            if (head == 0) {
                System.arraycopy(cycle, 0, array, cycleLength, cycleLength);
            } else if (head == cycleLength) {
                System.arraycopy(cycle, 0, array, 0, cycleLength);
            } else {
                throw new IllegalStateException("Head must be at 0 or cycle length");
            }
        }

        int cycleClickCount(int offset) {
            int total = 0;
            for (int index = offset; index < offset + cycleLength; index++) total += array[index];
            return total;
        }
    }

    static void stabilizedFill(int[] cycle, Random random) {
        if (cycle.length == 0) return;
        int clicks = Math.min(cycle.length, CPS_MIN + random.nextInt(CPS_MAX - CPS_MIN + 1));
        int[] gaps = new int[clicks];
        int baseGap = cycle.length / clicks;
        int remainder = cycle.length % clicks;
        Arrays.fill(gaps, baseGap);
        for (int i = 0; i < remainder; i++) gaps[i]++;
        shuffle(gaps, random);

        boolean allEqual = true;
        for (int i = 1; i < gaps.length; i++) {
            if (gaps[i] != gaps[0]) {
                allEqual = false;
                break;
            }
        }
        if (allEqual && gaps.length > 1 && gaps[0] > 1) {
            int donor = random.nextInt(gaps.length);
            int receiver = (donor + 1 + random.nextInt(gaps.length - 1)) % gaps.length;
            gaps[donor]--;
            gaps[receiver]++;
        }

        int transfers = random.nextInt(gaps.length + 1) + random.nextInt(gaps.length + 1);
        for (int i = 0; i < transfers; i++) {
            int donor = random.nextInt(gaps.length);
            int receiver = random.nextInt(gaps.length);
            if (donor == receiver || gaps[donor] <= 1) continue;
            gaps[donor]--;
            gaps[receiver]++;
        }
        shuffle(gaps, random);

        int position = random.nextInt(cycle.length);
        for (int gap : gaps) {
            cycle[position]++;
            position = (position + gap) % cycle.length;
        }
    }

    private static void shuffle(int[] values, Random random) {
        for (int i = values.length - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            int value = values[i];
            values[i] = values[other];
            values[other] = value;
        }
    }

    private final class Clicker {
        private final RollingClickArray clickArray = new RollingClickArray(CLICK_CYCLE, CLICK_ITERATIONS);
        private int ticksSinceLastClick;

        private boolean shieldBreakBypass;

        Clicker() {
            fill();
        }

        void tick() {
            ticksSinceLastClick++;
            if (clickArray.advance(1)) {
                int[] cycle = new int[CLICK_CYCLE];
                stabilizedFill(cycle, random);
                clickArray.push(cycle);
            }
        }

        private void fill() {
            clickArray.clear();
            int[] cycle = new int[CLICK_CYCLE];
            for (int i = 0; i < clickArray.iterations; i++) {
                Arrays.fill(cycle, 0);
                stabilizedFill(cycle, random);
                clickArray.push(cycle);
                clickArray.advance(CLICK_CYCLE);
            }
        }

        int getClickAmount(int tick) {
            if (isEnforcedClick()) return 1;
            return clickArray.get(tick);
        }

        private boolean isEnforcedClick() {
            if (hasCooldown() && isCooldownPassed(0)) return true;
            return System.currentTimeMillis() - lastClickTime >= ENFORCED_CLICK_INTERVAL_MS;
        }

        boolean willClickAt(int tick) {
            return getClickAmount(tick) > 0;
        }

        boolean isClickTick() {
            return willClickAt(0);
        }

        boolean canExecuteClickNow() {
            if (!shieldBreakBypass && getClickAmount(0) <= 0) return false;

            if (missTimeActive()) return false;
            return shieldBreakBypass || isCooldownPassed(0);
        }

        void click(BooleanSupplier attack) {

            int amount = shieldBreakBypass ? Math.max(1, getClickAmount(0)) : getClickAmount(0);
            for (int i = 0; i < amount; i++) {
                if (missTimeActive()) continue;
                if ((shieldBreakBypass || isCooldownPassed(0)) && attack.getAsBoolean()) {
                    newCooldown();
                    lastClickTime = System.currentTimeMillis();
                    ticksSinceLastClick = 0;
                }
            }
        }
    }

    private Integer determineWeaponSlot(LivingEntity target, boolean enforceShield) {

        boolean requiresShield = enforceShield || target != null && wouldBlockHit(target);

        boolean requiresMace = canMaceSmash() && (hotbarContainsMace() || !requiresShield);

        Integer bestSlot = null;
        ItemStack bestStack = null;
        for (int slot = 0; slot < 9; slot++) {

            if (AutismHandArbiter.slotReserved(slot, id())) continue;
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            boolean eligible = requiresMace ? stack.getItem() instanceof MaceItem
                : requiresShield ? stack.is(ItemTags.AXES)
                : stack.is(ItemTags.SWORDS);
            if (!eligible) continue;
            if (bestStack == null || (requiresMace
                ? compareMaces(stack, bestStack) > 0
                : compareWeapons(stack, bestStack) > 0)) {
                bestSlot = slot;
                bestStack = stack;
            }
        }
        return bestSlot;
    }

    private boolean hotbarContainsMace() {
        for (int slot = 0; slot < 9; slot++) {

            if (AutismHandArbiter.slotReserved(slot, id())) continue;
            if (MC.player.getInventory().getItem(slot).getItem() instanceof MaceItem) return true;
        }
        return false;
    }

    private boolean canMaceSmash() {
        return MaceItem.canSmashAttack(MC.player);
    }

    private boolean isAutoWeaponBusy() {
        return MC.player.isUsingItem()
            && MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND
            && MC.player.getUseItem().has(DataComponents.CONSUMABLE);
    }

    private void tickAutoSwordReset() {

        if (!bool("switch-back")) return;
        if (previousSlot < 0 || switchBackTicks <= 0) return;

        if (switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {

            int selected = MC.player.getInventory().getSelectedSlot();
            if (CrystalAuraModule.holdsBorrowedSlot(selected)
                || AnchorAuraModule.holdsBorrowedSlot(selected)) {
                return;
            }
            previousSlot = -1;
            switchedToSlot = -1;
            switchBackTicks = 0;
            return;
        }
        switchBackTicks--;

        if (switchBackTicks == 0 && (!canRun() || targetOutOfRange(currentTarget))) {

            if (AutismBlinkManager.holdsActionsWithoutMovement()) return;

            if (CrystalAuraModule.reservesCombatTick() || CrystalAuraModule.hasLiveCommitment()
                || AnchorAuraModule.reservesCombatTick()) {
                return;
            }

            if (AutismHandArbiter.slotReserved(previousSlot, id())) return;

            int tick = AutismSharedState.get().getClientTickCounter();
            if (tick == hotbarChangeTick) return;

            if (!AutismHandArbiter.beginHandPacketGroup(id())) return;
            try {
                int back = previousSlot;
                previousSlot = -1;
                switchedToSlot = -1;
                AutismInventoryHelper.selectHotbarSlot(MC, back);
                hotbarChangeTick = tick;
            } finally {
                AutismHandArbiter.endHandPacketGroup(id());
            }
        }
    }

    private boolean targetOutOfRange(LivingEntity target) {
        if (target == null) return true;
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation == null) rotation = AutismRotationUtil.playerRotation(MC.player);
        EntityHitResult hit = isLookingAtEntity(target, rotation, interactionRange(), THROUGH_WALLS_RANGE);
        return hit == null || !attackRangeIsInRange(MC.player.getMainHandItem(), hit.getLocation());
    }

    private int compareWeapons(ItemStack first, ItemStack second) {
        int result = Double.compare(estimatedWeaponDamage(first), estimatedWeaponDamage(second));
        if (result != 0) return result;
        result = Double.compare(secondaryWeaponValue(first), secondaryWeaponValue(second));
        if (result != 0) return result;
        result = Boolean.compare(first.is(ItemTags.SWORDS), second.is(ItemTags.SWORDS));
        if (result != 0) return result;
        result = Integer.compare(durability(first), durability(second));
        if (result != 0) return result;
        result = Integer.compare(enchantableValue(first), enchantableValue(second));
        if (result != 0) return result;
        return Integer.compare(first.hashCode(), second.hashCode());
    }

    private int compareMaces(ItemStack first, ItemStack second) {
        int result = Double.compare(estimatedMaceDamage(first), estimatedMaceDamage(second));
        if (result != 0) return result;
        result = Integer.compare(durability(first), durability(second));
        if (result != 0) return result;
        result = Integer.compare(enchantableValue(first), enchantableValue(second));
        if (result != 0) return result;
        return Integer.compare(first.hashCode(), second.hashCode());
    }

    private double estimatedWeaponDamage(ItemStack stack) {
        double damage = attributeValue(stack, Attributes.ATTACK_DAMAGE,
            MC.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
        int sharpness = enchantmentLevel(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) damage += 0.5D * sharpness + 0.5D;
        double speed = attributeValue(stack, Attributes.ATTACK_SPEED,
            MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
        double probability = Math.pow(0.85D, 1.0D / 20.0D);
        double adjusted = Math.pow(probability, Math.ceil((20.0D / speed) * 0.9D));
        double fire = Math.max(0.0D, enchantmentLevel(stack, Enchantments.FIRE_ASPECT) * 4.0D - 1.0D) * 0.33D;
        double factor = enchantmentLevel(stack, Enchantments.SMITE) * 0.2D
            + enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 0.2D
            + enchantmentLevel(stack, Enchantments.KNOCKBACK) * 0.2D;
        return damage * speed * adjusted * (1.0D + factor) + fire;
    }

    private double estimatedMaceDamage(ItemStack stack) {
        double damage = attributeValue(stack, Attributes.ATTACK_DAMAGE,
            MC.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
        int sharpness = enchantmentLevel(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) damage += 0.5D * sharpness + 0.5D;
        double speed = attributeValue(stack, Attributes.ATTACK_SPEED,
            MC.player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
        double probability = Math.pow(0.85D, 1.0D / 20.0D);
        double adjusted = Math.pow(probability, Math.ceil((20.0D / speed) * 0.9D));
        double factor = enchantmentLevel(stack, Enchantments.DENSITY) * 0.5D
            + enchantmentLevel(stack, Enchantments.BREACH) * 0.15D
            + enchantmentLevel(stack, Enchantments.SMITE) * 0.2D
            + enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 0.2D
            + enchantmentLevel(stack, Enchantments.WIND_BURST) * 0.2D;

        return damage * speed * adjusted + factor + 29.0D;
    }

    private double secondaryWeaponValue(ItemStack stack) {
        return enchantmentLevel(stack, Enchantments.LOOTING) * 0.05D
            + enchantmentLevel(stack, Enchantments.UNBREAKING) * 0.05D
            + enchantmentLevel(stack, Enchantments.MENDING) * 0.1D
            - enchantmentLevel(stack, Enchantments.VANISHING_CURSE) * 0.1D
            + enchantmentLevel(stack, Enchantments.SWEEPING_EDGE) * 0.2D
            + enchantmentLevel(stack, Enchantments.KNOCKBACK) * 0.25D;
    }

    private static int durability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private static int enchantableValue(ItemStack stack) {
        return stack.has(DataComponents.ENCHANTABLE) ? stack.get(DataComponents.ENCHANTABLE).value() : 0;
    }

    private double attributeValue(ItemStack stack, Holder<Attribute> attribute, double base) {
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers == null ? attribute.value().sanitizeValue(base)
            : attribute.value().sanitizeValue(modifiers.compute(attribute, base, EquipmentSlot.MAINHAND));
    }

    private int enchantmentLevel(ItemStack stack,
                                 net.minecraft.resources.ResourceKey<Enchantment> enchantment) {
        try {
            Holder<Enchantment> holder = MC.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantment);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void resetRuntime() {
        currentTarget = null;
        previousSlot = -1;
        switchedToSlot = -1;
        switchBackTicks = 0;
        lastShieldSeenTick = Integer.MIN_VALUE;
        shieldHoldEntityId = -1;
        shieldBreakLandedTick = Integer.MIN_VALUE;
        shieldBreakLandedEntityId = -1;
        postUseSuppressTicks = 0;
        pendingHitEntityId = -1;
        aimPointTracker.clear();
        missState.clear();
        AutismKillAuraRenderer.clear();
    }

    static final class AimPointTracker {

        private static final double BAND_FLOOR_DEGREES = 2.2D;
        private static final double BAND_LO_HWA_FRACTION = 0.55D;

        private static final double BAND_HI_MIN_WIDTH = 3.6D;
        private static final double BAND_HI_HWA_FRACTION = 0.85D;

        private static final double HWA_INSET_BLOCKS = 0.04D;
        private static final int SIDE_FLIP_TICKS_MIN = 2;
        private static final int SIDE_FLIP_TICKS_SPAN = 3;
        private static final int EXCURSION_INTERVAL_MIN = 30;
        private static final int EXCURSION_INTERVAL_SPAN = 9;
        private static final int EXCURSION_TICKS = 2;

        private static final int EXCURSION_MAX_HOLD_TICKS = 8;
        private static final double EXCURSION_DEGREES_MIN = 13.0D;
        private static final double EXCURSION_DEGREES_SPAN = 3.0D;
        private static final double VERTICAL_OFFSET_MIN = 0.5D;
        private static final double VERTICAL_OFFSET_SPAN = 1.5D;
        private static final int VERTICAL_HOLD_TICKS_MIN = 50;
        private static final int VERTICAL_HOLD_TICKS_SPAN = 50;
        private static final double VERTICAL_MAX_STEP_DEGREES = 0.5D;

        static final double FREE_MAX_STEP_DEGREES = 30.0D;
        static final double WINDOW_MAX_STEP_DEGREES = 4.0D;

        private final Random random;
        private int entityId = Integer.MIN_VALUE;
        private int side = 1;
        private int sideFlipTicks;
        private boolean sideChangePending;
        private int excursionCountdown;
        private int excursionTicksLeft;
        private int excursionHoldTicks;
        private double excursionDegrees;

        private double errorDegrees = Double.NaN;
        private double verticalOffsetDegrees;
        private double verticalTargetDegrees;
        private int verticalHoldTicks;

        AimPointTracker(Random random) {
            this.random = random;
        }

        Vec3 begin(int targetEntityId, AABB box, Vec3 eyes,
                   boolean inAttackWindow, boolean allowExcursion) {
            if (entityId != targetEntityId) resetTarget(targetEntityId);
            return aimPoint(box, eyes, inAttackWindow, allowExcursion);
        }

        Vec3 advance(int targetEntityId, AABB box, Vec3 eyes,
                     boolean inAttackWindow, boolean allowExcursion) {
            if (entityId != targetEntityId) {
                return begin(targetEntityId, box, eyes, inAttackWindow, allowExcursion);
            }
            tickState(inAttackWindow, allowExcursion);
            return aimPoint(box, eyes, inAttackWindow, allowExcursion);
        }

        void clear() {
            entityId = Integer.MIN_VALUE;
        }

        private void resetTarget(int targetEntityId) {
            entityId = targetEntityId;
            side = random.nextBoolean() ? 1 : -1;
            sideFlipTicks = rollSideFlipTicks(random);
            sideChangePending = false;

            excursionCountdown = rollExcursionInterval(random);
            excursionTicksLeft = 0;
            excursionHoldTicks = 0;
            errorDegrees = Double.NaN;
            verticalOffsetDegrees = rollVerticalOffset(random);
            verticalTargetDegrees = verticalOffsetDegrees;
            verticalHoldTicks = VERTICAL_HOLD_TICKS_MIN + random.nextInt(VERTICAL_HOLD_TICKS_SPAN);
        }

        private void tickState(boolean inAttackWindow, boolean allowExcursion) {

            if (excursionTicksLeft > 0 && Math.abs(errorDegrees) >= excursionDegrees - 1.0E-9D) {
                excursionTicksLeft--;
            }
            if (--excursionCountdown <= 0) {

                if (!allowExcursion
                    || (inAttackWindow && excursionHoldTicks < EXCURSION_MAX_HOLD_TICKS)) {
                    excursionCountdown = 1;
                    excursionHoldTicks++;
                } else {
                    excursionTicksLeft = EXCURSION_TICKS;
                    excursionDegrees = rollExcursionDegrees(random);
                    excursionCountdown = rollExcursionInterval(random);
                    excursionHoldTicks = 0;
                }
            }

            if (excursionTicksLeft <= 0 && !sideChangePending && --sideFlipTicks <= 0) {
                sideChangePending = true;
            }
            if (--verticalHoldTicks <= 0) {

                double magnitude = VERTICAL_OFFSET_MIN + random.nextDouble() * VERTICAL_OFFSET_SPAN;
                verticalTargetDegrees = Math.copySign(magnitude, verticalOffsetDegrees);
                verticalHoldTicks = VERTICAL_HOLD_TICKS_MIN + random.nextInt(VERTICAL_HOLD_TICKS_SPAN);
            }

            double verticalDelta = verticalTargetDegrees - verticalOffsetDegrees;
            if (Math.abs(verticalDelta) > VERTICAL_MAX_STEP_DEGREES) {
                verticalOffsetDegrees += Math.copySign(VERTICAL_MAX_STEP_DEGREES, verticalDelta);
            } else {
                verticalOffsetDegrees = verticalTargetDegrees;
            }

            if (Math.abs(verticalOffsetDegrees) < VERTICAL_OFFSET_MIN) {
                verticalOffsetDegrees = Math.copySign(VERTICAL_OFFSET_MIN, verticalDelta);
            }
        }

        private Vec3 aimPoint(AABB box, Vec3 eyes, boolean inAttackWindow, boolean allowExcursion) {
            Vec3 center = box.getCenter();
            double dx = center.x - eyes.x;
            double dz = center.z - eyes.z;
            double horizontal = Math.max(Math.sqrt(dx * dx + dz * dz), 0.3D);
            double halfWidth = Math.min(box.getXsize(), box.getZsize()) * 0.5D;
            double hwa = horizontalHalfWidthAngle(halfWidth, horizontal);
            double signed = advanceErrorAngle(hwa, inAttackWindow, allowExcursion);

            double perpX = -dz / horizontal;
            double perpZ = dx / horizontal;
            double lateral = horizontal * Math.tan(Math.toRadians(signed));
            double dy = horizontal * Math.tan(Math.toRadians(verticalOffsetDegrees));
            return new Vec3(center.x + perpX * lateral, center.y + dy, center.z + perpZ * lateral);
        }

        private double advanceErrorAngle(double hwa, boolean inAttackWindow, boolean allowExcursion) {
            double bandLo = aimBandLow(hwa);
            if (Double.isNaN(errorDegrees)) {

                errorDegrees = side * rollBandMagnitude(random, hwa);
                return errorDegrees;
            }

            boolean excursion = allowExcursion && excursionTicksLeft > 0;

            if (sideChangePending && !excursion && Math.abs(errorDegrees) <= bandLo + 1.0E-9D) {
                side = -side;
                errorDegrees = -errorDegrees;
                sideChangePending = false;
                sideFlipTicks = rollSideFlipTicks(random);
                return errorDegrees;
            }

            double goal;
            if (excursion) {
                goal = side * excursionDegrees;
            } else if (sideChangePending) {
                goal = side * bandLo;
            } else {
                goal = side * rollBandMagnitude(random, hwa);
            }

            double cap = inAttackWindow ? WINDOW_MAX_STEP_DEGREES : FREE_MAX_STEP_DEGREES;
            double delta = goal - errorDegrees;
            errorDegrees = Math.abs(delta) > cap
                ? errorDegrees + Math.copySign(cap, delta)
                : goal;

            if (Math.abs(errorDegrees) < bandLo) {
                errorDegrees = Math.copySign(bandLo, errorDegrees == 0.0D ? side : errorDegrees);
            }
            return errorDegrees;
        }

        static double horizontalHalfWidthAngle(double halfWidth, double distance) {
            return Math.toDegrees(Math.atan(Math.max(halfWidth - HWA_INSET_BLOCKS, 0.01D)
                / Math.max(distance, 0.3D)));
        }

        static double aimBandLow(double hwa) {
            return Math.min(BAND_FLOOR_DEGREES, BAND_LO_HWA_FRACTION * hwa);
        }

        static double aimBandHigh(double hwa) {
            return Math.max(aimBandLow(hwa) + BAND_HI_MIN_WIDTH, BAND_HI_HWA_FRACTION * hwa);
        }

        static double rollBandMagnitude(Random random, double hwa) {
            double low = aimBandLow(hwa);
            return low + random.nextDouble() * (aimBandHigh(hwa) - low);
        }

        static int rollSideFlipTicks(Random random) {
            return SIDE_FLIP_TICKS_MIN + random.nextInt(SIDE_FLIP_TICKS_SPAN + 1);
        }

        static int rollExcursionInterval(Random random) {
            return EXCURSION_INTERVAL_MIN + random.nextInt(EXCURSION_INTERVAL_SPAN);
        }

        static double rollExcursionDegrees(Random random) {
            return EXCURSION_DEGREES_MIN + random.nextDouble() * EXCURSION_DEGREES_SPAN;
        }

        private static double rollVerticalOffset(Random random) {
            double magnitude = VERTICAL_OFFSET_MIN + random.nextDouble() * VERTICAL_OFFSET_SPAN;
            return random.nextBoolean() ? magnitude : -magnitude;
        }
    }

    static final class AccuracyGovernor {

        static final double SAMPLE_YAW_SPEED = 5.0D;

        static final int WINDOW_SAMPLES = 21;

        static final int STALE_TICKS = 30;

        static final double SAFE_MEAN = 8.5D;

        static final double THROTTLED_SAMPLE = THROTTLED_MAX_YAW_STEP + THROTTLED_MAX_PITCH_STEP;

        static final double BAND_SAMPLE = 6.5D;

        private long lastAttackMs = Long.MIN_VALUE;
        private int samples;
        private double speedSum;
        private double errorSum;
        private float lastYaw;
        private float lastPitch;
        private boolean seeded;

        private int ticksSinceSample;

        void onAttackSent() {
            lastAttackMs = System.currentTimeMillis();
        }

        boolean attackedRecently() {
            return lastAttackMs != Long.MIN_VALUE
                && System.currentTimeMillis() - lastAttackMs <= ATTACK_ACCURACY_WINDOW_MS;
        }

        void onOutgoingRotation(float yaw, float pitch, float perfectYaw) {
            if (!seeded) {
                lastYaw = yaw;
                lastPitch = pitch;
                seeded = true;
                return;
            }
            double yawSpeed = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
            double pitchSpeed = Math.abs(Mth.wrapDegrees(pitch - lastPitch));
            lastYaw = yaw;
            lastPitch = pitch;
            if (yawSpeed <= SAMPLE_YAW_SPEED || Float.isNaN(perfectYaw) || !attackedRecently()) {
                ticksSinceSample++;
                return;
            }
            ticksSinceSample = 0;
            if (samples >= WINDOW_SAMPLES) {
                samples = 0;
                speedSum = 0.0D;
                errorSum = 0.0D;
            }
            samples++;
            speedSum += yawSpeed + pitchSpeed;
            errorSum += Math.abs(Mth.wrapDegrees(yaw - perfectYaw));
        }

        void forgetPacketHistory() {
            seeded = false;
        }

        boolean speedAtRisk() {
            return !discardIfStale() && projectedMean(speedSum, THROTTLED_SAMPLE) > SAFE_MEAN;
        }

        boolean errorAtRisk() {
            return !discardIfStale() && projectedMean(errorSum, BAND_SAMPLE) > SAFE_MEAN;
        }

        private boolean discardIfStale() {
            if (samples <= 0 || ticksSinceSample <= STALE_TICKS) return false;
            samples = 0;
            speedSum = 0.0D;
            errorSum = 0.0D;
            return true;
        }

        private double projectedMean(double sum, double remainingSample) {
            if (samples <= 0) return 0.0D;
            return (sum + (WINDOW_SAMPLES - samples) * remainingSample) / WINDOW_SAMPLES;
        }

        void reset() {
            lastAttackMs = Long.MIN_VALUE;
            samples = 0;
            speedSum = 0.0D;
            errorSum = 0.0D;
            seeded = false;
            ticksSinceSample = 0;
        }
    }

    static final class MissState {
        private int pendingTicks;
        private boolean lastWasMiss;
        private int targetId = -1;
        private Vec3 point;

        boolean isPending() {
            return pendingTicks > 0;
        }

        boolean isFireTick() {
            return pendingTicks == 1;
        }

        boolean mayRoll() {
            return pendingTicks == 0 && !lastWasMiss;
        }

        void begin(int entityId, Vec3 missPoint) {
            pendingTicks = 2;
            targetId = entityId;
            point = missPoint;
        }

        boolean matches(int entityId) {
            return targetId == entityId;
        }

        Vec3 point() {
            return point;
        }

        void advance() {
            if (pendingTicks == 1) lastWasMiss = true;
            if (pendingTicks > 0) pendingTicks--;
            if (pendingTicks == 0) {
                targetId = -1;
                point = null;
            }
        }

        void onAttackFired() {
            lastWasMiss = false;
        }

        void clear() {
            pendingTicks = 0;
            lastWasMiss = false;
            targetId = -1;
            point = null;
        }
    }

}
