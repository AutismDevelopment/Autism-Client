

package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.mixin.accessor.AutismLivingEntityAccessor;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.mixin.accessor.AutismPlayerAccessor;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKillAuraRenderer;
import autismclient.util.AutismKillAuraRotation;
import autismclient.util.AutismRemoteView;
import autismclient.util.AutismRotationUtil;
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
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
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
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class KillAuraModule extends Module {

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

    static final int POST_USE_SUPPRESS_TICKS = 3;

    static final int HIT_CONFIRM_TICKS = 8;

    private static final double[] ITERATION_PROPORTIONS = {
        0.05D, 0.15D, 0.25D, 0.35D, 0.45D, 0.55D, 0.65D, 0.75D, 0.85D, 0.95D
    };
    private static final int POINT_TRACKER_SAMPLES = 128;
    private static final int RAYTRACE_SAMPLES = 256;

    private static long lastClickTime;

    private static final SoundEvent HITSOUND =
        SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("autismclient", "hitsound"));

    private final Random random = new Random();
    private final Clicker clicker = new Clicker();

    private LivingEntity currentTarget;

    private double closestSquaredEnemyDistance;

    private double scanAddition = nextScanAddition();

    private int previousSlot = -1;
    private int switchBackTicks;

    private int postUseSuppressTicks;

    private int pendingHitEntityId = -1;
    private int pendingHitPrevHurtTime;
    private int pendingHitTicks;
    private String cachedEntityListSource = "";
    private Set<String> cachedEntityIds = Set.of();

    public KillAuraModule() {
        super("kill-aura", "KillAura", ModuleCategory.COMBAT, "Automatically attacks configured entities.");
        add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").build());
        add(new ChoiceSetting("targeting", "Targeting", "FOV",
            "Type", "HP", "Distance", "FOV", "Hurt Time", "Age").build());
        add(new IntSetting("fov", "FOV", 180, 10, 360, 10)
            .description("Attack cone in degrees")
            .build());
        add(new BoolSetting("criticals", "Criticals", true)
            .description("Smart critical hits")
            .build());
        add(new BoolSetting("auto-sword", "Auto Sword", false).build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .visibleWhen(() -> bool("auto-sword"))
            .build());
        add(new BoolSetting("keep-sprint", "Keep Sprint", false)
            .description("Grim can flag this.")
            .build());
        add(new BoolSetting("hit-marker", "Render", true).build());
        add(new BoolSetting("hitsound", "Hitsound", true).build());
    }

    @Override
    public void onEnable() {
        resetRuntime(false);
    }

    @Override
    public void onDisable() {
        resetRuntime(true);
    }

    @Override
    public void onGameLeft() {
        resetRuntime(false);
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("entities".equals(settingId)) cachedEntityListSource = null;
        if ("auto-sword".equals(settingId) && !bool("auto-sword")) {

            if (previousSlot >= 0 && MC != null && MC.player != null) {
                AutismInventoryHelper.selectHotbarSlot(MC, previousSlot);
            }
            previousSlot = -1;
            switchBackTicks = 0;
        }
        if ("switch-back".equals(settingId) && !bool("switch-back")) {
            previousSlot = -1;
            switchBackTicks = 0;
        }
    }

    @Override
    public void preMovementTick() {
        if (MC == null || MC.player == null || MC.level == null) return;
        if (ScaffoldModule.reservesTellyInput()) {
            currentTarget = null;
            AutismKillAuraRotation.reset();
            return;
        }

        boolean usingItem = isUsingHeldItem();
        if (usingItem) postUseSuppressTicks = POST_USE_SUPPRESS_TICKS;
        else if (postUseSuppressTicks > 0) postUseSuppressTicks--;

        if (isBreakingBlock() || usingItem) {

            currentTarget = null;
            clicker.tick();
            confirmHitFeedback();
            AutismKillAuraRotation.update(MC.player);
            return;
        }

        tickAutoSwordReset();
        clicker.tick();
        confirmHitFeedback();

        if (canRun()) {
            updateTargetRotation();
        } else {
            currentTarget = null;
        }

        AutismKillAuraRotation.update(MC.player);

        if (canRun()) {
            attackPhase();
        }
    }

    private boolean isBreakingBlock() {
        return MC.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor
            && accessor.autism$isDestroying();
    }

    private boolean isUsingHeldItem() {
        return MC.player != null && MC.player.isUsingItem();
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
            && !PackHideState.isActive()
            && !PackFreecamState.isActive()
            && !AutismRemoteView.isActive()
            && !MultiPilot.isActive()
            && !MacroExecutor.isRunning()
            && !PacketTeleportController.ownsMainMovement()
            && !BuiltinModules.ownsManualFastExp()
            && !ScaffoldModule.reservesTellyInput()
            && !ScaffoldModule.hasActiveSilentMovementRotation();
    }

    public static Input modifyMovementInput(ClientInput source, Input input) {
        if (input == null || MC == null || MC.player == null || MC.player.input != source) return input;
        KillAuraModule aura = activeInstance();
        if (aura == null) return input;
        Input result = input;
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation != null && aura.canRun() && !ScaffoldModule.hasActiveSilentMovementRotation()) {
            result = ScaffoldModule.transformSilentMovementInput(input, MC.player.getYRot(), rotation.yaw());
        }

        return result;
    }

    public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
        if (entity == null || MC == null || entity != MC.player) return vanillaYaw;
        KillAuraModule aura = activeInstance();
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        return aura == null || rotation == null || !aura.canRun()
            || ScaffoldModule.hasActiveSilentMovementRotation() ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
        if (player == null || MC == null || player != MC.player) return vanillaYaw;
        return correctedMovementYaw(player, vanillaYaw);
    }

    public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
        if (player == null || MC == null || player != MC.player) return vanillaPitch;
        KillAuraModule aura = activeInstance();
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        return aura == null || rotation == null || !aura.canRun()
            || ScaffoldModule.hasActiveSilentMovementRotation() ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 silentViewVector(LocalPlayer player, Vec3 vanillaVector) {
        if (player == null || MC == null || player != MC.player) return vanillaVector;
        KillAuraModule aura = activeInstance();
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        return aura == null || rotation == null || !aura.canRun()
            ? vanillaVector : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        if (rotation == null) return vanillaImpulse;
        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, vanillaImpulse.y, Mth.cos(yaw) * 0.2F);
    }

    public static float correctedFallFlyingPitch(LivingEntity entity, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 correctedFallFlyingLook(LivingEntity entity, Vec3 vanillaLook) {
        AutismRotationUtil.Rotation rotation = activeMovementRotation(entity);
        return rotation == null ? vanillaLook
            : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    private static AutismRotationUtil.Rotation activeMovementRotation(Entity entity) {
        if (entity == null || MC == null || entity != MC.player) return null;
        KillAuraModule aura = activeInstance();
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        return aura == null || rotation == null || !aura.canRun() ? null : rotation;
    }

    public static AutismRotationUtil.Rotation activeUseItemRotation(LocalPlayer player) {
        if (player == null || MC == null || player != MC.player) return null;
        AutismRotationUtil.Rotation scaffold = ScaffoldModule.activeOutgoingRotation();
        if (scaffold != null) return scaffold;
        KillAuraModule aura = activeInstance();
        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        return aura == null || rotation == null || !aura.canRun() ? null : rotation;
    }

    private static KillAuraModule activeInstance() {
        Module module = ModuleRegistry.get("kill-aura");
        return module instanceof KillAuraModule aura && aura.isEnabled() ? aura : null;
    }

    public LivingEntity currentTarget() {
        return currentTarget;
    }

    private void updateTargetRotation() {
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
        for (LivingEntity entity : filtered) {

            AutismRotationUtil.Rotation rotation = findRotation(entity, maximumRange);
            if (rotation != null) {

                AutismKillAuraRotation.setTarget(rotation);
                chosen = entity;
                break;
            }
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

    private AutismRotationUtil.Rotation findRotation(Entity entity, double range) {
        Vec3 eyes = MC.player.getEyePosition();
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());

        Vec3 preferredPoint = closestProjectedPoint(eyes, box, POINT_TRACKER_SAMPLES);
        if (preferredPoint == null) preferredPoint = nearestPointOnBox(eyes, box);

        preferredPoint = nearestPointOnBox(preferredPoint, box);

        AutismRotationUtil.Rotation preference = AutismRotationUtil.lookingAt(preferredPoint, eyes);
        return raytraceBox(eyes, box, range, THROUGH_WALLS_RANGE, preference, preferredPoint);
    }

    private AutismRotationUtil.Rotation raytraceBox(Vec3 eyes, AABB box, double range, double wallsRange,
                                                    AutismRotationUtil.Rotation preference, Vec3 preferredSpot) {
        double rangeSq = range * range;
        double wallsRangeSq = wallsRange * wallsRange;

        Vec3 preferredSpotOnBox = firstHit(box, eyes, preferredSpot);
        if (preferredSpotOnBox != null) {
            double distanceSq = eyes.distanceToSqr(preferredSpotOnBox);
            boolean visible = aimVisibility(eyes, preferredSpotOnBox);
            if (distanceSq < (visible ? rangeSq : wallsRangeSq)) {
                return AutismRotationUtil.lookingAt(preferredSpotOnBox, eyes);
            }
        }

        RotationAccumulator accumulator = new RotationAccumulator();
        considerSpot(accumulator, eyes, box, preferredSpot, rangeSq, wallsRangeSq, preference);
        considerSpot(accumulator, eyes, box, nearestPointOnBox(eyes, box), rangeSq, wallsRangeSq, preference);
        scanBoxPoints(eyes, box, spot ->
            considerSpot(accumulator, eyes, box, spot, rangeSq, wallsRangeSq, preference));
        return accumulator.result();
    }

    private void considerSpot(RotationAccumulator accumulator, Vec3 eyes, AABB box, Vec3 spot,
                              double rangeSq, double wallsRangeSq, AutismRotationUtil.Rotation preference) {

        Vec3 raycastTarget = fma(eyes, 2.0D, spot.subtract(eyes));
        Vec3 spotOnBox = firstHit(box, eyes, raycastTarget);
        if (spotOnBox == null) return;

        double distanceSq = eyes.distanceToSqr(spotOnBox);
        boolean visible = aimVisibility(eyes, spotOnBox);

        if (!(distanceSq < (visible ? rangeSq : wallsRangeSq))) return;

        AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(spot, eyes);
        accumulator.consider(rotation, visible, AutismRotationUtil.rotationAngleTo(preference, rotation));
    }

    private void scanBoxPoints(Vec3 eyes, AABB box, java.util.function.Consumer<Vec3> consumer) {
        boolean outsideBox = projectBoxPoints(eyes, box, RAYTRACE_SAMPLES, consumer);
        if (!outsideBox) {
            for (double x : ITERATION_PROPORTIONS) for (double y : ITERATION_PROPORTIONS) {
                for (double z : ITERATION_PROPORTIONS) {
                    consumer.accept(new Vec3(
                        Math.fma(box.getXsize(), x, box.minX),
                        Math.fma(box.getYsize(), y, box.minY),
                        Math.fma(box.getZsize(), z, box.minZ)));
                }
            }
        }
    }

    static Vec3 closestProjectedPoint(Vec3 eyes, AABB box, int maxPoints) {
        Vec3[] best = new Vec3[1];
        double[] bestDistance = {Double.POSITIVE_INFINITY};
        boolean projected = projectBoxPoints(eyes, box, maxPoints, point -> {
            double distance = point.distanceToSqr(eyes);
            if (distance < bestDistance[0]) {
                bestDistance[0] = distance;
                best[0] = point;
            }
        });
        return projected ? best[0] : null;
    }

    static boolean projectBoxPoints(Vec3 eyes, AABB box, int maxPoints,
                                    java.util.function.Consumer<Vec3> consumer) {
        if (box.contains(eyes)) return false;

        Vec3 centerDirection = box.getCenter().subtract(eyes);
        double directionLengthSq = centerDirection.lengthSqr();

        if (Mth.equal(directionLengthSq, 0.0D)) return false;
        Vec3 normal = centerDirection.normalize();

        Vec3[] vertices = boxVertices(box);
        Vec3 frameProjection = null;
        double frameDistance = Double.POSITIVE_INFINITY;
        for (Vec3 vertex : vertices) {
            double parameter = vertex.subtract(eyes).dot(centerDirection) / directionLengthSq;
            Vec3 projected = eyes.add(centerDirection.scale(parameter));
            double distance = projected.distanceToSqr(eyes);
            if (distance < frameDistance) {
                frameDistance = distance;
                frameProjection = projected;
            }
        }
        if (frameProjection == null) return false;
        Vec3 frameOrigin = frameProjection.lerp(eyes, 0.1D);

        float yaw = (float) Math.atan2(normal.z, normal.x);
        float pitch = (float) Math.atan2(normal.y, normal.horizontalDistance());
        Matrix3f toMatrix = new Matrix3f().rotateY(-yaw).mul(new Matrix3f().rotateZ(pitch));
        Matrix3f backMatrix = new Matrix3f().rotateZ(-pitch).mul(new Matrix3f().rotateY(yaw));

        float minZ = 0.0F;
        float maxZ = 0.0F;
        float minY = 0.0F;
        float maxY = 0.0F;
        double planeDistance = frameOrigin.dot(normal);
        for (Vec3 vertex : vertices) {
            Vec3 direction = vertex.subtract(eyes);
            double divisor = direction.dot(normal);

            if (Mth.equal(divisor, 0.0D)) continue;
            double parameter = (planeDistance - eyes.dot(normal)) / divisor;
            Vec3 intersection = eyes.add(direction.scale(parameter));
            Vector3f local = intersection.subtract(frameOrigin).toVector3f().mul(backMatrix);
            minZ = Math.min(minZ, local.z);
            maxZ = Math.max(maxZ, local.z);
            minY = Math.min(minY, local.y);
            maxY = Math.max(maxY, local.y);
        }

        Vector3f originF = frameOrigin.toVector3f();
        Vector3f positionF = new Vector3f(0.0F, minY, minZ).mul(toMatrix).add(originF);
        Vector3f dirYF = new Vector3f(0.0F, maxY - minY, 0.0F).mul(toMatrix);
        Vector3f dirZF = new Vector3f(0.0F, 0.0F, maxZ - minZ).mul(toMatrix);
        Vec3 position = new Vec3(positionF.x, positionF.y, positionF.z);
        Vec3 dirY = new Vec3(dirYF.x, dirYF.y, dirYF.z);
        Vec3 dirZ = new Vec3(dirZF.x, dirZF.y, dirZF.z);

        double[] steps = fairPlaneSteps(dirY, dirZ, maxPoints);
        int yCount = (int) Math.floor((1.0D + 1.0E-10D) / steps[1]) + 1;
        int zCount = (int) Math.floor((1.0D + 1.0E-10D) / steps[0]) + 1;
        for (int yi = 0; yi < yCount; yi++) {
            double y = yi * steps[1];
            for (int zi = 0; zi < zCount; zi++) {
                double z = zi * steps[0];
                Vec3 point = fma(fma(position, y, dirY), z, dirZ);
                Vec3 extended = point.lerp(eyes, -100.0D);
                box.clip(eyes, extended).ifPresent(consumer);
            }
        }
        return true;
    }

    private static double[] fairPlaneSteps(Vec3 dirY, Vec3 dirZ, int maxPoints) {
        boolean yZero = Mth.equal(dirY.lengthSqr(), 0.0D);
        boolean zZero = Mth.equal(dirZ.lengthSqr(), 0.0D);
        if (!yZero && !zZero) {
            double aspectRatio = dirZ.length() / dirY.length();
            return new double[]{
                Math.sqrt(1.0D / (aspectRatio * maxPoints)),
                Math.sqrt(aspectRatio / maxPoints)
            };
        }
        if (yZero && zZero) return new double[]{1.0D, 1.0D};
        if (yZero) return new double[]{1.0D, 2.0D / maxPoints};
        return new double[]{2.0D / maxPoints, 1.0D};
    }

    private static Vec3[] boxVertices(AABB box) {
        return new Vec3[]{
            new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.minX, box.minY, box.maxZ),
            new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.minX, box.maxY, box.maxZ),
            new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ),
            new Vec3(box.maxX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ)
        };
    }

    private static Vec3 nearestPointOnBox(Vec3 point, AABB box) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ));
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
            camera, eyes, end, search, EntitySelector.CAN_BE_PICKED.or(predicate), range * range);
    }

    private EntityHitResult isLookingAtEntity(Entity target, AutismRotationUtil.Rotation rotation,
                                              double range, double wallsRange) {
        Entity camera = MC.getCameraEntity();
        if (camera == null) return null;
        EntityHitResult hit = findEntityInCrosshair(range, rotation, entity -> entity == target);
        if (hit == null) return null;
        Vec3 eyes = camera.getEyePosition();
        double distanceSq = eyes.distanceToSqr(hit.getLocation());
        return distanceSq <= wallsRange * wallsRange
            || distanceSq <= range * range && hasLineOfSight(eyes, hit.getLocation(), camera)
            ? hit : null;
    }

    private void attackPhase() {
        LivingEntity target = currentTarget;
        if (target == null) return;

        AutismRotationUtil.Rotation rotation = AutismKillAuraRotation.getCurrentRotation();
        if (rotation == null) rotation = AutismRotationUtil.playerRotation(MC.player);

        EntityHitResult crosshairHit = findEntityInCrosshair(interactionRange(), rotation, entity -> true);
        Entity crosshairTarget = crosshairHit != null ? crosshairHit.getEntity() : target;
        if (crosshairTarget instanceof LivingEntity living && living != target && shouldBeAttacked(living)) {
            currentTarget = living;
        }

        attackTarget(crosshairTarget, rotation);
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
        if (!clicker.isClickTick() || !canAttackNow(target, mainHandStack)) return;

        clickerPrepareForAttack(() -> {

            if (!canAttackNow(target, mainHandStack)) return false;
            attackEntity(target);

            if (bool("auto-sword") && bool("switch-back") && previousSlot >= 0) {
                switchBackTicks = AUTO_SWORD_SWITCH_BACK_TICKS;
            }
            scanAddition = nextScanAddition();
            return true;
        });
    }

    private boolean prepareWeaponSwitchTick(Entity target) {
        if (!bool("auto-sword")) return false;
        if (!(target instanceof LivingEntity living)) return false;
        Integer slot = determineWeaponSlot(living, false);
        if (slot == null || isAutoWeaponBusy()) return false;
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected == slot) return false;

        if (bool("switch-back")) {
            if (previousSlot < 0) previousSlot = selected;
            switchBackTicks = AUTO_SWORD_SWITCH_BACK_TICKS;
        }

        AutismInventoryHelper.selectHotbarSlot(MC, slot);
        return true;
    }

    private boolean performDueSwitchBack() {
        if (!bool("auto-sword") || !bool("switch-back") || previousSlot < 0 || switchBackTicks > 0) return false;
        int back = previousSlot;
        previousSlot = -1;
        if (MC.player.getInventory().getSelectedSlot() == back) return false;

        AutismInventoryHelper.selectHotbarSlot(MC, back);
        return true;
    }

    private boolean canAttackNow(Entity target, ItemStack stack) {
        if (!stack.isItemEnabled(MC.level.enabledFeatures())) return false;
        if (MC.player.cannotAttackWithItem(stack, 0)) return false;

        if (postUseSuppressTicks > 0) return false;

        return !(bool("criticals") && target instanceof LivingEntity
            && !MC.player.isFallFlying()
            && !(bool("keep-sprint") && MC.player.isSprinting())
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
        return bool("criticals") && !bool("keep-sprint")
            && MC.player != null && !MC.player.onGround()
            && currentTarget != null && clicker.willClickAt(1);
    }

    public static boolean blocksSprintForCrit() {
        KillAuraModule aura = activeInstance();
        return aura != null && aura.shouldStopSprintingForCrit();
    }

    private void clickerPrepareForAttack(BooleanSupplier attack) {
        if (!clicker.canExecuteClickNow()) return;
        if (MC.player.isBlocking()) return;
        if (MC.player.isUsingItem()) return;
        clicker.click(attack);
    }

    private void attackEntity(Entity target) {
        ItemStack stack = MC.player.getMainHandItem();
        var piercing = stack.get(DataComponents.PIERCING_WEAPON);

        if (piercing != null && !MC.gameMode.isSpectator()) {
            MC.gameMode.piercingAttack(piercing);
            MC.player.swing(InteractionHand.MAIN_HAND);
            autismclient.util.AutismCpsTracker.recordLeft();
            queueHitFeedback(target);
            return;
        }

        if (!canBeAttackedWithVanillaPacket(target)) return;

        ((AutismMultiPlayerGameModeAccessor) MC.gameMode).autism$ensureHasSentCarriedItem();
        MC.getConnection().send(new ServerboundAttackPacket(target.getId()));
        autismclient.util.AutismCpsTracker.recordLeft();
        queueHitFeedback(target);

        if (bool("keep-sprint")) {
            float genericDamage = MC.player.isAutoSpinAttack()
                ? ((AutismLivingEntityAccessor) MC.player).autism$getAutoSpinAttackDmg()
                : (float) MC.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DamageSource damageSource = MC.player.damageSources().playerAttack(MC.player);
            float enchantDamage = ((AutismPlayerAccessor) MC.player)
                .autism$getEnchantedDamage(target, genericDamage, damageSource) - genericDamage;
            float attackCooldown = MC.player.getAttackStrengthScale(0.5F);
            genericDamage *= 0.2F + attackCooldown * attackCooldown * 0.8F;
            enchantDamage *= attackCooldown;

            if (genericDamage > 0.0F || enchantDamage > 0.0F) {
                if (enchantDamage > 0.0F) {
                    MC.player.magicCrit(target);
                }
                if (wouldDoCriticalHit()) {
                    MC.level.playSound(null, MC.player.getX(), MC.player.getY(), MC.player.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, MC.player.getSoundSource(), 1.0F, 1.0F);
                    MC.player.crit(target);
                }
            }
        } else if (!MC.gameMode.isSpectator()) {
            MC.player.attack(target);
        }

        MC.player.resetAttackStrengthTicker();
        MC.player.swing(InteractionHand.MAIN_HAND);
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
        if (bool("auto-sword") && hasCooldown()) {
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
        int clicks = CPS_MIN + random.nextInt(CPS_MAX - CPS_MIN + 1);
        int interval = clicks > 0 ? cycle.length / clicks : 0;
        int remainder = clicks > 0 ? cycle.length % clicks : 0;
        int index = 0;
        for (int i = 0; i < clicks; i++) {
            cycle[index % cycle.length]++;
            index += Math.max(interval, 1);
            if (remainder > 0) {
                index++;
                remainder--;
            }
        }
    }

    private final class Clicker {
        private final RollingClickArray clickArray = new RollingClickArray(CLICK_CYCLE, CLICK_ITERATIONS);
        private int ticksSinceLastClick;

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
            if (getClickAmount(0) <= 0) return false;

            if (MC.missTime > 0) return false;
            return isCooldownPassed(0);
        }

        void click(BooleanSupplier attack) {
            int amount = getClickAmount(0);
            for (int i = 0; i < amount; i++) {
                if (MC.missTime > 0) continue;
                if (isCooldownPassed(0) && attack.getAsBoolean()) {
                    newCooldown();
                    lastClickTime = System.currentTimeMillis();
                    ticksSinceLastClick = 0;
                }
            }
        }
    }

    private Integer determineWeaponSlot(LivingEntity target, boolean enforceShield) {

        boolean requiresShield = enforceShield || target != null && wouldBlockHit(target);

        boolean requiresMace = canMaceSmash();

        Integer bestSlot = null;
        ItemStack bestStack = null;
        for (int slot = 0; slot < 9; slot++) {
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

    private boolean canMaceSmash() {
        return MaceItem.canSmashAttack(MC.player);
    }

    private boolean isAutoWeaponBusy() {
        return MC.player.isUsingItem()
            && MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND
            && MC.player.getUseItem().has(DataComponents.CONSUMABLE);
    }

    private void tickAutoSwordReset() {
        if (!bool("auto-sword") || !bool("switch-back")) return;
        if (previousSlot < 0 || switchBackTicks <= 0) return;
        switchBackTicks--;
        if (switchBackTicks == 0 && (currentTarget == null || !canRun())) {
            int back = previousSlot;
            previousSlot = -1;
            AutismInventoryHelper.selectHotbarSlot(MC, back);
        }
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

    private void resetRuntime(boolean restoreSlot) {
        if (restoreSlot && previousSlot >= 0 && MC != null && MC.player != null
            && bool("switch-back")) {
            AutismInventoryHelper.selectHotbarSlot(MC, previousSlot);
        }
        currentTarget = null;
        previousSlot = -1;
        switchBackTicks = 0;
        postUseSuppressTicks = 0;
        pendingHitEntityId = -1;
        AutismKillAuraRotation.reset();
        AutismKillAuraRenderer.clear();
    }

    private static final class RotationAccumulator {
        private AutismRotationUtil.Rotation bestVisible;
        private double bestVisibleAngle;
        private AutismRotationUtil.Rotation bestInvisible;
        private double bestInvisibleAngle;

        private void consider(AutismRotationUtil.Rotation rotation, boolean visible, double preferenceAngle) {
            if (visible) {
                if (bestVisible == null || preferenceAngle < bestVisibleAngle) {
                    bestVisible = rotation;
                    bestVisibleAngle = preferenceAngle;
                }
            } else if (bestInvisible == null || preferenceAngle < bestInvisibleAngle) {
                bestInvisible = rotation;
                bestInvisibleAngle = preferenceAngle;
            }
        }

        private AutismRotationUtil.Rotation result() {
            return bestVisible != null ? bestVisible : bestInvisible;
        }
    }
}
