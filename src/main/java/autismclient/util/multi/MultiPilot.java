package autismclient.util.multi;

import autismclient.AutismClientAddon;
import autismclient.util.macro.PacketRoutePlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MultiPilot {
    private static final double EQUIVALENT_ENTITY_MAX_DISTANCE_SQ = 1.0D;
    private static final double POV_ENTITY_REACH_TOLERANCE = 0.75D;
    private static final double POV_ARMOR_STAND_AIM_TOLERANCE = 0.85D;
    private static final double POV_INTERACTION_AIM_TOLERANCE = 0.45D;
    private static final double POV_OTHER_ENTITY_AIM_TOLERANCE = 0.20D;
    private static volatile MultiSession session;
    private static volatile int pilotedEntityId = -1;
    private static volatile PovEntityTarget povEntityTarget;
    private static final ThreadLocal<VanillaInteractionRoute> VANILLA_INTERACTION_ROUTE = new ThreadLocal<>();

    private record PovEntityTarget(int renderedId, UUID renderedUuid, int wireId,
                                   Vec3 relativeHit, String trackedType) {
        boolean matches(Entity entity) {
            return renderedId >= 0 && entity != null && entity.getId() == renderedId
                && (renderedUuid == null || renderedUuid.equals(entity.getUUID()));
        }

        boolean botOnly() {
            return renderedId < 0;
        }
    }

    private static final class VanillaInteractionRoute {
        final MultiSession session;
        final int wireId;
        boolean captured;
        boolean sent;

        VanillaInteractionRoute(MultiSession session, int wireId) {
            this.session = session;
            this.wireId = wireId;
        }
    }

    private static boolean jumpRequested;
    private static boolean sneaking;
    private static boolean sprinting;
    private static boolean lastSentSprint;
    private static Input lastSentInput = Input.EMPTY;
    private static Input currentInput = Input.EMPTY;
    private static long lastTeleportSeq;
    private static long lastGuiSeq;
    private static long lastSignSeq;
    private static long lastBookSeq;
    private static boolean botGuiOpen;
    private static boolean pendingGuiRestore;
    private static boolean pendingSignRestore;
    private static boolean viewerWasOpenBeforePov;
    private static boolean physicsRanThisTick;
    private static boolean macroObserving;
    private static long lastMacroInputNoticeAt;
    private static long lastPilotActionNoticeAt;

    private static Vec3 simPosition = Vec3.ZERO;
    private static Vec3 simDelta = Vec3.ZERO;
    private static float simYaw;
    private static float simPitch;
    private static boolean simOnGround = true;
    private static boolean simHorizontalCollision;
    private static boolean simInitialized;
    private static boolean simulating;
    private static final MultiPilotTruth SERVER_TRUTH = new MultiPilotTruth();

    private static long lastMenuRevision = Long.MIN_VALUE;
    private static int lastSelectedHotbar = -1;

    private static int useItemDelay;

    private static boolean breaking;
    private static BlockPos breakPos;
    private static Direction breakDir;
    private static float destroyProgress;
    private static int destroyDelay;
    private static int lastCrackStage = -1;

    private MultiPilot() {
    }

    public static boolean isActive() {
        return session != null;
    }

    static MultiSession activeCommandSession() {
        return session;
    }

    static RemotePlayer activeBotEntity() {
        return botEntity(Minecraft.getInstance());
    }

    public static net.minecraft.world.entity.player.Player commandPlayer() {
        MultiSession active = session;
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return mc.player;
        return mc.level != null && mc.level.getEntity(pilotedEntityId) instanceof RemotePlayer bot ? bot : null;
    }

    public static long blockInspectionMenuGeneration() {
        MultiSession active = session;
        return active == null ? -1L : active.menuContext().generation();
    }

    public static BlockInspectionMenu blockInspectionMenuAfter(long generation) {
        MultiSession active = session;
        if (active == null) return null;
        MultiSession.MenuContext context = active.menuContext();
        if (context.generation() <= generation || context.containerId() <= 0 || !context.interactive()) return null;
        int containerSlots = Math.max(0, context.slots().size() - 36);
        java.util.List<ItemStack> items = new java.util.ArrayList<>(containerSlots);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = context.slots().get(i);
            items.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return new BlockInspectionMenu(context.generation(), context.title().getString(), context.typeId(),
            java.util.List.copyOf(items));
    }

    public static void finishBlockInspectionMenu() {
        MultiSession active = session;
        if (active == null) return;
        lastGuiSeq = active.guiOpenSeq();
        botGuiOpen = false;
        autismclient.util.AutismMultiOverlay.hideGuiViewer(active.accountId());
        active.pilotCloseContainer();
    }

    public record BlockInspectionMenu(long generation, String title, String typeId, java.util.List<ItemStack> items) {
    }

    public static String commandGiveCreative(ItemStack stack) {
        MultiSession active = session;
        return active == null ? null : active.giveCreative(stack);
    }

    public static String commandStashXCarry(int targetHandlerSlot) {
        MultiSession active = session;
        return active == null ? null : active.stashSelectedXCarry(targetHandlerSlot);
    }

    static String runPovHClip(String arguments) {
        MultiSession active = session;
        RemotePlayer bot = botEntity(Minecraft.getInstance());
        if (active == null || bot == null || !active.pilotPacketsReady()) return "POV session is not ready";
        if (active.macroOwnsPilot()) return "Macro owns the POV bot";
        String[] parts = commandParts(arguments);
        if (parts.length == 0) return "Usage: hclip <blocks>";
        String mode = parts[0].toLowerCase(java.util.Locale.ROOT);
        double blocks;
        int count;
        boolean forceGround;

        if (mode.equals("forward") || mode.equals("back")) {
            blocks = autoHorizontalClipDistance(bot, mode.equals("back") ? -1 : 1, 32);
            if (blocks == 0.0D) return mode.equals("back")
                ? "No blocking run behind you" : "No blocking run in front of you";
            count = Math.max(1, (int) Math.ceil(Math.abs(blocks) / 10));
            if (count > 20) count = 1;
            forceGround = false;
        } else {
            MultiClientCommands.ClipRequest request = MultiClientCommands.parseClip("hclip", arguments);
            if (request.spec() == null) return request.error();
            blocks = request.spec().blocks();
            count = request.spec().segments();
            forceGround = request.spec().forceGround();
        }
        double yaw = Math.toRadians(bot.getYRot());
        Vec3 delta = new Vec3(-Math.sin(yaw) * blocks, 0.0D, Math.cos(yaw) * blocks);
        applyPovCommandClip(active, bot, delta, count, forceGround || bot.onGround());
        return "Sent";
    }

    static String runPovTp(String arguments) {
        return PacketTeleportController.executePov(arguments);
    }

    static String runPovVClip(String arguments) {
        MultiSession active = session;
        RemotePlayer bot = botEntity(Minecraft.getInstance());
        if (active == null || bot == null || !active.pilotPacketsReady()) return "POV session is not ready";
        if (active.macroOwnsPilot()) return "Macro owns the POV bot";
        String[] parts = commandParts(arguments);
        if (parts.length == 0) return "Usage: vclip <blocks>";
        String mode = parts[0].toLowerCase(java.util.Locale.ROOT);
        double blocks;
        int count;
        boolean forceGround;

        if (mode.equals("top") || mode.equals("bottom")) {
            blocks = autoVerticalClipDistance(bot, mode.equals("top"));
            if (blocks == 0.0D) return "No safe " + mode + " target";
            count = Math.max(1, (int) Math.ceil(Math.abs(blocks) / 10));
            if (count > 20) count = 1;
            forceGround = true;
        } else {
            MultiClientCommands.ClipRequest request = MultiClientCommands.parseClip("vclip", arguments);
            if (request.spec() == null) return request.error();
            blocks = request.spec().blocks();
            count = request.spec().segments();
            forceGround = request.spec().forceGround();
        }
        applyPovCommandClip(active, bot, new Vec3(0.0D, blocks, 0.0D), count, forceGround);
        return "Sent";
    }

    private static void applyPovCommandClip(MultiSession active, RemotePlayer bot, Vec3 delta,
                                            int segments, boolean onGround) {
        active.clip(delta.x, delta.y, delta.z, segments, onGround);
        simPosition = simPosition.add(delta);
        simDelta = Vec3.ZERO;
        simOnGround = onGround;
        simHorizontalCollision = false;
        applySimulationPose(bot);
        SERVER_TRUTH.reset(simPosition, System.currentTimeMillis());
    }

    static void applyPacedTeleportStep(MultiSession active, Vec3 position, boolean onGround) {
        if (active == null || active != session || position == null) return;
        RemotePlayer bot = botEntity(Minecraft.getInstance());
        if (bot == null) return;
        simPosition = position;
        simDelta = Vec3.ZERO;
        simOnGround = onGround;
        simHorizontalCollision = false;
        simInitialized = true;
        applySimulationPose(bot);
        SERVER_TRUTH.reset(position, System.currentTimeMillis());
    }

    static void preparePacedTeleport(MultiSession active, RemotePlayer bot) {
        if (active == null || active != session || bot == null) return;
        currentInput = Input.EMPTY;
        lastSentInput = Input.EMPTY;
        jumpRequested = false;
        sneaking = false;
        simDelta = Vec3.ZERO;
        PacketRoutePlanner.CollisionView view = PacketRoutePlanner.forEntity(bot);
        simOnGround = view != null && view.loaded(bot.position()) && view.traversable(bot.position());
        bot.setDeltaMovement(Vec3.ZERO);
        bot.setShiftKeyDown(false);
        bot.stopFallFlying();
        bot.getAbilities().flying = false;
        if (!bot.getAbilities().instabuild) bot.getAbilities().mayfly = false;
        bot.setOnGround(simOnGround);
        active.pilotSend(new ServerboundPlayerInputPacket(Input.EMPTY));
        active.pilotAbilities(bot.getAbilities());
        if (sprinting || lastSentSprint) {
            active.pilotPlayerCommand(bot, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
        }
        sprinting = false;
        lastSentSprint = false;
        bot.setSprinting(false);
    }

    private static double autoHorizontalClipDistance(RemotePlayer bot, int direction, int radius) {
        double yaw = Math.toRadians(bot.getYRot());
        double dx = -Math.sin(yaw) * direction;
        double dz = Math.cos(yaw) * direction;
        Vec3 start = bot.position();
        boolean blocked = false;
        int lastBlocked = 0;
        for (int distance = 1; distance <= Math.max(1, radius); distance++) {
            Vec3 candidate = new Vec3(start.x + dx * distance, start.y, start.z + dz * distance);
            boolean clear = commandPositionClear(bot, candidate);
            if (!clear) {
                blocked = true;
                lastBlocked = distance;
            } else if (blocked) {
                return direction * distance;
            }
        }
        return blocked ? direction * Math.min(radius, lastBlocked + 1) : 0.0D;
    }

    private static double autoVerticalClipDistance(RemotePlayer bot, boolean upward) {
        Vec3 start = bot.position();
        boolean sawBlocked = false;
        for (double distance = 0.5D; distance <= 128.0D; distance += 0.5D) {
            double signed = upward ? distance : -distance;
            Vec3 candidate = start.add(0.0D, signed, 0.0D);
            boolean clear = commandPositionClear(bot, candidate);
            if (!clear) {
                sawBlocked = true;
                continue;
            }
            if (!upward || sawBlocked) {
                Vec3 below = candidate.add(0.0D, -0.0625D, 0.0D);
                Vec3 shift = below.subtract(bot.position());
                if (!bot.level().noCollision(bot, bot.getBoundingBox().move(shift))) return signed;
            }
        }
        return 0.0D;
    }

    private static boolean commandPositionClear(RemotePlayer bot, Vec3 candidate) {
        if (bot == null || bot.level() == null || !bot.level().isLoaded(BlockPos.containing(candidate))) return false;
        return bot.level().noCollision(bot, bot.getBoundingBox().move(candidate.subtract(bot.position())));
    }

    private static String[] commandParts(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    public static boolean isManualActive() {
        MultiSession active = session;
        return active != null && !active.macroOwnsPilot();
    }

    public static boolean isPilotedEntity(Entity entity) {
        return session != null && entity != null && entity.getId() == pilotedEntityId;
    }

    public static boolean isPilotedEntityId(int entityId) {
        return session != null && entityId == pilotedEntityId;
    }

    public static boolean isManualControlEntity(Entity entity) {
        MultiSession s = session;
        return s != null && entity != null && entity.getId() == pilotedEntityId && !s.macroOwnsPilot();
    }

    public static boolean isSimulatingEntity(Entity entity) {
        return simulating && entity != null && entity.getId() == pilotedEntityId;
    }

    public static net.minecraft.world.entity.player.Player pilotedBot() {
        if (session == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.getEntity(pilotedEntityId) instanceof RemotePlayer bot ? bot : null;
    }

    public static void applyEntityRenderTruth(Entity entity, EntityRenderState state, float partialTick) {
        MultiSession active = session;
        if (active == null || entity == null || state == null) return;
        Minecraft mc = Minecraft.getInstance();

        if (!shouldTranslateFromBotTruth(entity == mc.player, entity.getId(), pilotedEntityId)) return;
        MultiEntityTracker.State truth = truthForRenderedEntity(active, entity);
        if (truth == null) return;

        Vec3 oldRenderPosition = new Vec3(state.x, state.y, state.z);
        Vec3 truthPosition = truth.position();
        state.x = truthPosition.x;
        state.y = truthPosition.y;
        state.z = truthPosition.z;

        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null) state.distanceToCameraSq = truthPosition.distanceToSqr(camera.getPosition(partialTick));

        if (state instanceof FishingHookRenderState hookState && truth.ownerId() >= 0) {
            MultiEntityTracker.State ownerTruth = active.entityTruth(truth.ownerId());
            Entity renderedOwner = entityForTruth(mc, ownerTruth);
            if (renderedOwner == null && truth.ownerId() == active.takeoverPositionEntityId()) {
                renderedOwner = botEntity(mc);
            }
            if (renderedOwner != null && hookState.lineOriginOffset != null) {

                Vec3 ownerShift = ownerTruth == null ? Vec3.ZERO
                    : ownerTruth.position().subtract(renderedOwner.getPosition(partialTick));
                Vec3 hookShift = truthPosition.subtract(oldRenderPosition);
                hookState.lineOriginOffset = hookState.lineOriginOffset.add(ownerShift).subtract(hookShift);
            }
        }
    }

    public static Boolean isEntityVisibleFromTruth(Entity entity, Frustum frustum,
                                                   double cameraX, double cameraY, double cameraZ) {
        MultiSession active = session;
        Minecraft mc = Minecraft.getInstance();
        if (active == null || entity == null
            || !shouldTranslateFromBotTruth(entity == mc.player, entity.getId(), pilotedEntityId)) return null;
        MultiEntityTracker.State truth = truthForRenderedEntity(active, entity);
        if (truth == null) return null;
        Vec3 pos = truth.position();
        double dx = pos.x - cameraX;
        double dy = pos.y - cameraY;
        double dz = pos.z - cameraZ;
        if (!entity.shouldRenderAtSqrDistance(dx * dx + dy * dy + dz * dz)) return false;
        if (isFishingBobber(truth.type())) return true;
        AABB translated = entity.getBoundingBox().move(pos.subtract(entity.position())).inflate(0.5D);
        return frustum == null || frustum.isVisible(translated);
    }

    private static boolean isFishingBobber(String type) {
        return type != null && (type.equals("fishing_bobber") || type.endsWith(":fishing_bobber"));
    }

    private static MultiEntityTracker.State truthForRenderedEntity(MultiSession active, Entity entity) {
        if (active == null || entity == null) return null;
        MultiEntityTracker.State byId = active.entityTruth(entity.getId());
        if (byId != null && (byId.uuid() == null || byId.uuid().equals(entity.getUUID()))) return byId;
        MultiEntityTracker.State byUuid = active.entityTruth(entity.getUUID());
        return byUuid;
    }

    private static MultiEntityTracker.State interactionTruthForRenderedEntity(MultiSession active, Entity entity) {
        MultiEntityTracker.State exact = truthForRenderedEntity(active, entity);
        if (exact != null || active == null || entity == null) return exact;

        MultiEntityTracker.State byId = active.entityTruth(entity.getId());
        String renderedType = entityTypeKey(entity);
        Vec3 renderedPosition = entity.position();
        if (byId != null && sameEntityType(byId.type(), renderedType)
            && byId.position().distanceToSqr(renderedPosition) <= EQUIVALENT_ENTITY_MAX_DISTANCE_SQ) {
            return byId;
        }
        MultiEntityTracker.State closest = null;
        double closestDistanceSq = EQUIVALENT_ENTITY_MAX_DISTANCE_SQ;
        for (MultiEntityTracker.State truth : active.entityTruthStates()) {
            if (!sameEntityType(truth.type(), renderedType)) continue;
            double distanceSq = truth.position().distanceToSqr(renderedPosition);
            if (distanceSq <= closestDistanceSq) {
                closest = truth;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }

    private static String entityTypeKey(Entity entity) {
        if (entity == null) return "";
        try {
            return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean sameEntityType(String trackedType, String renderedType) {
        if (trackedType == null || renderedType == null || renderedType.isBlank()) return false;
        String normalized = trackedType.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) normalized = normalized.substring(colon + 1);
        return normalized.equals(renderedType);
    }

    private static Entity entityForTruth(Minecraft mc, MultiEntityTracker.State truth) {
        if (mc == null || mc.level == null || truth == null) return null;
        Entity byId = mc.level.getEntity(truth.id());
        if (byId != null && (truth.uuid() == null || truth.uuid().equals(byId.getUUID()))) return byId;
        if (truth.uuid() == null) return null;
        for (Entity candidate : mc.level.entitiesForRendering()) {
            if (truth.uuid().equals(candidate.getUUID())) return candidate;
        }
        return null;
    }

    public static HitResult authoritativePick(Minecraft mc, float partialTick) {
        return authoritativePick(mc, partialTick, null);
    }

    public static HitResult authoritativePick(Minecraft mc, float partialTick, HitResult vanillaHit) {
        MultiSession active = session;
        RemotePlayer bot = botEntity(mc);
        if (active == null || bot == null || mc.level == null) return mc.hitResult;

        double blockReach = active.takeoverBlockInteractionRange();
        double entityReach = active.takeoverEntityInteractionRange();
        float hitboxMargin = 0.0F;
        try {
            net.minecraft.world.item.component.AttackRange attackRange = bot.getActiveItem()
                .get(DataComponents.ATTACK_RANGE);
            if (attackRange != null) {
                entityReach = Math.max(entityReach, attackRange.effectiveMaxRange(bot));
                hitboxMargin = attackRange.hitboxMargin();
            }
        } catch (Throwable ignored) {

        }

        double targetingReach = entityReach + POV_ENTITY_REACH_TOLERANCE;
        double rayReach = Math.max(blockReach, targetingReach);
        Vec3 eye = bot.getEyePosition(partialTick);
        Vec3 look = bot.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(rayReach));
        HitResult blockHit = mc.level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, bot));
        double blockDistanceSq = blockHit != null && blockHit.getType() != HitResult.Type.MISS
            ? eye.distanceToSqr(blockHit.getLocation()) : rayReach * rayReach;
        double maxEntityDistanceSq = targetingReach * targetingReach;

        if (vanillaHit instanceof EntityHitResult vanillaEntityHit) {
            Entity vanillaTarget = vanillaEntityHit.getEntity();
            MultiEntityTracker.State vanillaTruth = interactionTruthForRenderedEntity(active, vanillaTarget);
            if (vanillaTarget != bot && vanillaTruth != null && isPovInteractable(vanillaTruth, vanillaTarget)) {
                Vec3 relativeHit = vanillaEntityHit.getLocation().subtract(vanillaTarget.position());
                Vec3 translatedHit = vanillaTarget == mc.player
                    ? vanillaEntityHit.getLocation() : vanillaTruth.position().add(relativeHit);
                double distanceSq = eye.distanceToSqr(translatedHit);
                if (distanceSq <= maxEntityDistanceSq && distanceSq < blockDistanceSq) {
                    povEntityTarget = new PovEntityTarget(
                        vanillaTarget.getId(), vanillaTarget.getUUID(), vanillaTruth.id(), relativeHit,
                        vanillaTruth.type());
                    return new EntityHitResult(vanillaTarget, translatedHit);
                }
            }
        }

        double bestDistanceSq = Double.POSITIVE_INFINITY;
        double bestAimErrorSq = Double.POSITIVE_INFINITY;
        Entity bestEntity = null;
        MultiEntityTracker.State bestTruth = null;
        Vec3 bestLocation = null;
        Map<UUID, Entity> renderedByUuid = null;
        List<Entity> renderedEntities = null;

        for (MultiEntityTracker.State truth : active.entityTruthStates()) {
            Entity sameIdCandidate = mc.level.getEntity(truth.id());
            Entity candidate = sameIdCandidate;
            if (candidate == null || (truth.uuid() != null && !truth.uuid().equals(candidate.getUUID()))) {

                if (renderedByUuid == null) {
                    renderedByUuid = new HashMap<>(Math.max(16, mc.level.getEntityCount()));
                    renderedEntities = new ArrayList<>(Math.max(16, mc.level.getEntityCount()));
                    for (Entity rendered : mc.level.entitiesForRendering()) {
                        renderedByUuid.put(rendered.getUUID(), rendered);
                        renderedEntities.add(rendered);
                    }
                }
                candidate = truth.uuid() == null ? null : renderedByUuid.get(truth.uuid());
                if (candidate == null && isEquivalentRenderedEntity(truth, sameIdCandidate)) {
                    candidate = sameIdCandidate;
                }
                if (candidate == null) candidate = closestEquivalentRenderedEntity(truth, renderedEntities);
            }
            if (candidate == bot || candidate != null && candidate.isRemoved()) continue;
            AABB box;
            float pickRadius;
            if (candidate == null) {
                if (!isPotentialBotOnlyTarget(truth.type())) continue;
                box = trackedEntityBox(truth);
                if (box == null) continue;
                pickRadius = 0.0F;
            } else {
                if (!isPovInteractable(truth, candidate)) continue;

                Vec3 shift = candidate == mc.player ? Vec3.ZERO : truth.position().subtract(candidate.position());
                box = povInteractionBox(truth, candidate, shift);
                pickRadius = candidate.getPickRadius();
            }
            box = box.inflate(pickRadius + hitboxMargin);
            Vec3 location;
            if (box.contains(eye)) {
                location = eye;
            } else {
                Optional<Vec3> clipped = box.clip(eye, end);
                if (clipped.isPresent()) {
                    location = clipped.get();
                } else {
                    double aimTolerance = povAimTolerance(truth.type());
                    location = povAimAssistLocation(box, eye, look, targetingReach, aimTolerance);
                    if (location == null) continue;
                }
            }
            double distanceSq = eye.distanceToSqr(location);
            double aimErrorSq = distanceToFiniteRaySq(location, eye, look, targetingReach);
            boolean betterAim = aimErrorSq + 1.0E-7D < bestAimErrorSq;
            boolean equalAimCloser = Math.abs(aimErrorSq - bestAimErrorSq) <= 1.0E-7D
                && distanceSq < bestDistanceSq;
            if (distanceSq <= maxEntityDistanceSq && distanceSq < blockDistanceSq
                && (betterAim || equalAimCloser)) {
                bestDistanceSq = distanceSq;
                bestAimErrorSq = aimErrorSq;
                bestEntity = candidate;
                bestTruth = truth;
                bestLocation = location;
            }
        }
        if (bestTruth == null || bestLocation == null) {
            povEntityTarget = null;
            return blockHit;
        }
        povEntityTarget = new PovEntityTarget(
            bestEntity == null ? -1 : bestEntity.getId(),
            bestEntity == null ? null : bestEntity.getUUID(),
            bestTruth.id(), bestLocation.subtract(bestEntity == mc.player ? bestEntity.position() : bestTruth.position()),
            bestTruth.type());
        return bestEntity == null ? blockHit : new EntityHitResult(bestEntity, bestLocation);
    }

    private static double povAimTolerance(String trackedType) {
        String type = normalizedEntityType(trackedType);
        if (type.equals("armor_stand")) return POV_ARMOR_STAND_AIM_TOLERANCE;
        if (type.equals("interaction")) return POV_INTERACTION_AIM_TOLERANCE;
        return POV_OTHER_ENTITY_AIM_TOLERANCE;
    }

    static Vec3 povAimAssistLocation(AABB box, Vec3 eye, Vec3 look, double reach, double tolerance) {
        if (box == null || eye == null || look == null || reach <= 0.0D || tolerance < 0.0D) return null;
        double lookLengthSq = look.lengthSqr();
        if (lookLengthSq < 1.0E-8D) return null;
        Vec3 direction = look.scale(1.0D / Math.sqrt(lookLengthSq));
        Vec3 center = box.getCenter();
        double projection = Mth.clamp(center.subtract(eye).dot(direction), 0.0D, reach);
        Vec3 rayPoint = eye.add(direction.scale(projection));
        Vec3 boxPoint = new Vec3(
            Mth.clamp(rayPoint.x, box.minX, box.maxX),
            Mth.clamp(rayPoint.y, box.minY, box.maxY),
            Mth.clamp(rayPoint.z, box.minZ, box.maxZ));
        return rayPoint.distanceToSqr(boxPoint) <= tolerance * tolerance ? boxPoint : null;
    }

    private static double distanceToFiniteRaySq(Vec3 point, Vec3 eye, Vec3 look, double reach) {
        double lookLengthSq = look.lengthSqr();
        if (lookLengthSq < 1.0E-8D) return Double.POSITIVE_INFINITY;
        Vec3 direction = look.scale(1.0D / Math.sqrt(lookLengthSq));
        double projection = Mth.clamp(point.subtract(eye).dot(direction), 0.0D, reach);
        return point.distanceToSqr(eye.add(direction.scale(projection)));
    }

    static AABB trackedEntityBox(MultiEntityTracker.State truth) {
        if (truth == null) return null;
        try {
            String path = normalizedEntityType(truth.type());
            if (path.isBlank()) return null;
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace(path);
            net.minecraft.world.entity.EntityType<?> type =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) return null;
            AABB box = type.getDimensions().makeBoundingBox(truth.position());

            if (box.getXsize() <= 0.0D && box.getYsize() <= 0.0D && path.equals("interaction")) {
                Vec3 pos = truth.position();
                return new AABB(pos.x - 0.5D, pos.y, pos.z - 0.5D,
                    pos.x + 0.5D, pos.y + 1.0D, pos.z + 0.5D);
            }
            return box;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean isPotentialBotOnlyTarget(String trackedType) {
        String type = normalizedEntityType(trackedType);
        if (type.isBlank()) return false;
        return !type.equals("item") && !type.equals("experience_orb")
            && !type.equals("area_effect_cloud") && !type.equals("falling_block")
            && !type.equals("firework_rocket") && !type.equals("fishing_bobber")
            && !type.equals("marker") && !type.endsWith("_display")
            && !type.contains("arrow") && !type.contains("projectile")
            && !type.equals("trident") && !type.equals("snowball") && !type.equals("egg")
            && !type.equals("potion") && !type.equals("experience_bottle")
            && !type.contains("wind_charge") && !type.contains("fireball")
            && !type.equals("wither_skull") && !type.equals("shulker_bullet")
            && !type.equals("llama_spit") && !type.equals("evoker_fangs");
    }

    private static String normalizedEntityType(String trackedType) {
        if (trackedType == null) return "";
        String normalized = trackedType.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = normalized.indexOf(':');
        return colon >= 0 && colon + 1 < normalized.length() ? normalized.substring(colon + 1) : normalized;
    }

    private static boolean isPovInteractable(MultiEntityTracker.State truth, Entity candidate) {

        return candidate != null && EntitySelector.CAN_BE_PICKED.test(candidate);
    }

    private static AABB povInteractionBox(MultiEntityTracker.State truth, Entity candidate, Vec3 shift) {
        return candidate.getBoundingBox().move(shift);
    }

    private static MultiEntityTracker.State pickedTruth(MultiSession active, Entity entity) {
        PovEntityTarget picked = povEntityTarget;
        if (picked != null && picked.matches(entity)) {
            MultiEntityTracker.State truth = active.entityTruth(picked.wireId());
            if (truth != null) return truth;
        }
        return interactionTruthForRenderedEntity(active, entity);
    }

    private static PovEntityTarget currentPovTarget(HitResult hit) {
        PovEntityTarget picked = povEntityTarget;
        if (picked == null) return null;
        if (hit instanceof EntityHitResult entityHit) {
            return picked.matches(entityHit.getEntity()) ? picked : null;
        }
        return picked.botOnly() ? picked : null;
    }

    private static boolean isEquivalentRenderedEntity(MultiEntityTracker.State truth, Entity candidate) {
        return truth != null && candidate != null
            && sameEntityType(truth.type(), entityTypeKey(candidate))
            && truth.position().distanceToSqr(candidate.position()) <= EQUIVALENT_ENTITY_MAX_DISTANCE_SQ;
    }

    private static Entity closestEquivalentRenderedEntity(MultiEntityTracker.State truth, List<Entity> rendered) {
        if (truth == null || rendered == null) return null;
        Entity closest = null;
        double closestDistanceSq = EQUIVALENT_ENTITY_MAX_DISTANCE_SQ;
        for (Entity candidate : rendered) {
            if (!sameEntityType(truth.type(), entityTypeKey(candidate))) continue;
            double distanceSq = truth.position().distanceToSqr(candidate.position());
            if (distanceSq <= closestDistanceSq) {
                closest = candidate;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }

    public static boolean jumpRequested() {
        return jumpRequested;
    }

    static void begin(MultiSession pilotSession, RemotePlayer bot) {
        PacketTeleportController.cancelAll("POV ownership changed");
        session = pilotSession;
        pilotedEntityId = bot.getId();
        povEntityTarget = null;
        MultiPovChat.enter(pilotSession.accountId());
        lastTeleportSeq = pilotSession.teleportSeq();
        lastGuiSeq = pilotSession.guiOpenSeq();
        lastSignSeq = pilotSession.pilotSignSeq();
        lastBookSeq = pilotSession.pilotBookSeq();
        lastSentInput = Input.EMPTY;
        currentInput = Input.EMPTY;
        jumpRequested = false;
        sneaking = false;
        sprinting = false;
        lastSentSprint = false;
        useItemDelay = 0;
        useKeyWasDown = false;
        botGuiOpen = false;

        pendingGuiRestore = pilotSession.containerOpen();
        pendingSignRestore = pilotSession.signEditorOpen();

        viewerWasOpenBeforePov = autismclient.util.AutismMultiOverlay.isGuiViewerOpen(pilotSession.accountId());
        macroObserving = false;
        clearBreaking();

        Vec3 truth = bot.getInterpolation().position();
        simPosition = truth == null ? bot.position() : truth;
        simDelta = Vec3.ZERO;
        simYaw = bot.getInterpolation().yRot();
        simPitch = bot.getInterpolation().xRot();
        simOnGround = bot.onGround();
        simHorizontalCollision = false;
        simInitialized = true;
        simulating = false;
        MultiPovModuleController.begin(pilotSession, bot);
        SERVER_TRUTH.reset(simPosition, System.currentTimeMillis());
        bot.getInterpolation().setInterpolationLength(1);
        bot.getInterpolation().interpolateTo(simPosition, simYaw, simPitch);
        applySimulationPose(bot);

        lastMenuRevision = Long.MIN_VALUE;
        lastSelectedHotbar = -1;
        syncHud(bot);
    }

    static void neutralizeMainPlayer(Minecraft mc) {
        if (mc == null || mc.player == null) return;
        net.minecraft.client.player.LocalPlayer self = mc.player;
        boolean wasSprinting = self.isSprinting();
        autismclient.util.AutismInventoryMoveHelper.releaseMovementKeysIfSafe();
        if (self.input != null) self.input.tick();
        self.xxa = 0.0F;
        self.zza = 0.0F;
        self.setJumping(false);
        self.setShiftKeyDown(false);
        self.setSprinting(false);
        self.setDeltaMovement(stoppedMainVelocity(self.getDeltaMovement()));
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
        if (wasSprinting) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                self, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
    }

    static Vec3 stoppedMainVelocity(Vec3 velocity) {
        return velocity == null ? Vec3.ZERO : new Vec3(0.0D, velocity.y, 0.0D);
    }

    static boolean shouldTranslateFromBotTruth(boolean mainPlayer, int entityId, int controlledEntityId) {
        return !mainPlayer && entityId != controlledEntityId;
    }

    static void end(RemotePlayer bot) {
        MultiSession s = session;
        if (s != null) PacketTeleportController.cancelPov(s, "POV exited");
        povEntityTarget = null;
        MultiPovChat.exit();
        if (s != null && breaking && breakPos != null) {
            try {
                s.pilotSend(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, breakPos,
                    breakDir == null ? Direction.DOWN : breakDir));
            } catch (Throwable ignored) {

            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && breakPos != null) mc.level.destroyBlockProgress(pilotedEntityId, breakPos, -1);
        clearBreaking();
        abortSimulation(bot);
        MultiPovModuleController.end(bot);

        if (s != null && !viewerWasOpenBeforePov) {
            try {
                autismclient.util.AutismMultiOverlay.hideGuiViewer(s.accountId());
            } catch (Throwable ignored) {

            }
        }
        botGuiOpen = false;
        pendingGuiRestore = false;
        pendingSignRestore = false;
        if (bot != null) {
            bot.noPhysics = true;
            bot.getInterpolation().setInterpolationLength(
                net.minecraft.world.entity.InterpolationHandler.DEFAULT_INTERPOLATION_STEPS);
            bot.setSprinting(false);
            bot.setShiftKeyDown(false);
        }
        session = null;
        pilotedEntityId = -1;
        macroObserving = false;
        simInitialized = false;
    }

    private static void clearBreaking() {
        breaking = false;
        breakPos = null;
        breakDir = null;
        destroyProgress = 0.0F;
        destroyDelay = 0;
        lastCrackStage = -1;
    }

    public static boolean prePhysics(RemotePlayer bot) {
        physicsRanThisTick = false;
        MultiSession s = session;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null) return false;
        if (!s.pilotPacketsReady()) {

            simulating = false;
            currentInput = Input.EMPTY;
            jumpRequested = false;
            sneaking = false;
            sprinting = false;
            povEntityTarget = null;
            if (breaking) {
                clearCrack(mc);
                clearBreaking();
            }
            return false;
        }
        if (PacketTeleportController.ownsPov(s)) {
            simulating = false;
            currentInput = Input.EMPTY;
            jumpRequested = false;
            sneaking = false;
            sprinting = false;
            bot.setDeltaMovement(Vec3.ZERO);
            applySimulationPose(bot);
            return false;
        }

        Vec3 observedTruth = bot.getInterpolation().position();
        if (observedTruth == null) observedTruth = bot.position();
        s.pilotObserveServerTruth(observedTruth);

        if (macroObserving) {
            macroObserving = false;
            PositionMoveRotation authoritative = s.takeoverPosition();
            if (authoritative != null && authoritative.position() != null) {
                simPosition = authoritative.position();
                simDelta = authoritative.deltaMovement() == null ? Vec3.ZERO : authoritative.deltaMovement();
                simYaw = authoritative.yRot();
                simPitch = authoritative.xRot();
            }
            simOnGround = bot.onGround();
            simHorizontalCollision = false;
            simInitialized = true;
            SERVER_TRUTH.reset(observedTruth, System.currentTimeMillis());
            lastSentInput = Input.EMPTY;
            currentInput = Input.EMPTY;
            lastSentSprint = false;
        }

        if (!simInitialized) {
            simPosition = observedTruth;
            simDelta = Vec3.ZERO;
            simYaw = bot.getInterpolation().yRot();
            simPitch = bot.getInterpolation().xRot();
            simOnGround = bot.onGround();
            simInitialized = true;
            SERVER_TRUTH.reset(observedTruth, System.currentTimeMillis());
        } else {

            SERVER_TRUTH.observe(observedTruth, System.currentTimeMillis());
        }

        long seq = s.teleportSeq();
        if (seq != lastTeleportSeq) {
            lastTeleportSeq = seq;
            PositionMoveRotation corrected = s.takeoverPosition();
            if (corrected != null && corrected.position() != null) {
                simPosition = corrected.position();
                simDelta = corrected.deltaMovement() == null ? Vec3.ZERO : corrected.deltaMovement();
                simYaw = corrected.yRot();
                simPitch = corrected.xRot();
                simOnGround = false;
                simHorizontalCollision = false;
            }
            SERVER_TRUTH.reset(observedTruth, System.currentTimeMillis());
            applySimulationPose(bot);
            return false;
        }

        if (!bot.level().hasChunk(net.minecraft.util.Mth.floor(simPosition.x) >> 4,
            net.minecraft.util.Mth.floor(simPosition.z) >> 4)) {
            simDelta = Vec3.ZERO;
            jumpRequested = false;

            currentInput = Input.EMPTY;
            if (sprinting) {
                sprinting = false;
            }
            return false;
        }

        Vec3 impulse = s.consumePilotImpulse();
        if (impulse != null) simDelta = impulse;

        simulating = true;
        applySimulationPose(bot);

        boolean allowed = pilotInputAllowed(mc);
        boolean kf = allowed && down(mc.options.keyUp);
        boolean kb = allowed && down(mc.options.keyDown);
        boolean kl = allowed && down(mc.options.keyLeft);
        boolean kr = allowed && down(mc.options.keyRight);
        boolean kjump = allowed && down(mc.options.keyJump);
        boolean kshift = allowed && down(mc.options.keyShift);
        boolean ksprint = allowed && down(mc.options.keySprint);

        float forwardImpulse = (kf ? 1.0F : 0.0F) - (kb ? 1.0F : 0.0F);
        float leftImpulse = (kl ? 1.0F : 0.0F) - (kr ? 1.0F : 0.0F);
        Vec2 move = new Vec2(leftImpulse, forwardImpulse);
        if (move.lengthSquared() > 1.0F) move = move.normalized();

        boolean flightNoSneak = MultiPovModuleController.flightNoSneak(bot);
        boolean shiftInput = kshift && !flightNoSneak || MultiPovModuleController.forceSneak(bot);

        sneaking = shiftInput && !MultiPovModuleController.usesAbilitiesFlight(bot);
        bot.setShiftKeyDown(sneaking);

        if (move.lengthSquared() > 0.0F) {
            if (bot.isUsingItem() && !bot.isPassenger()) {
                move = move.scale(itemUseSpeedMultiplier(bot));
            }
            if (sneaking) {
                move = move.scale((float) bot.getAttributeValue(Attributes.SNEAKING_SPEED));
            }
            move = modifyInputSpeedForSquareMovement(move);
        }

        boolean hungerOk = s.food() > 6;
        boolean wantSprint = MultiPovModuleController.wantsSprint(
            kf, kb, kl, kr, sneaking, bot.isUsingItem(), hungerOk, bot.horizontalCollision);
        if (wantSprint != sprinting) {
            sprinting = wantSprint;
            bot.setSprinting(sprinting);
        }

        currentInput = new Input(kf, kb, kl, kr, kjump, shiftInput, ksprint || sprinting);

        bot.xxa = move.x;
        bot.zza = move.y;
        jumpRequested = kjump;
        MultiPovModuleController.preparePhysics(bot, kjump, shiftInput);
        physicsRanThisTick = true;
        return true;
    }

    private static void applySimulationPose(RemotePlayer bot) {
        bot.setPosRaw(simPosition.x, simPosition.y, simPosition.z);
        bot.setDeltaMovement(simDelta);
        bot.setYRot(simYaw);
        bot.setXRot(simPitch);
        bot.setYHeadRot(simYaw);
        bot.setOnGround(simOnGround);
        bot.noPhysics = false;
        bot.horizontalCollision = simHorizontalCollision;
        bot.setSprinting(sprinting);
    }

    private static boolean down(net.minecraft.client.KeyMapping mapping) {
        return mapping != null && autismclient.util.AutismKeyMappingBridge.of(mapping).autism$isActuallyDown();
    }

    private static boolean pilotInputAllowed(Minecraft mc) {
        if (autismclient.modules.PackFreecamState.isActive()) return false;
        try {
            if (autismclient.util.AutismOverlayManager.get().isAnyTextFieldFocused()) return false;
        } catch (Throwable ignored) {

        }
        net.minecraft.client.gui.screens.Screen screen = mc.gui.screen();
        if (screen == null) return true;
        if (screen instanceof net.minecraft.client.gui.screens.ChatScreen) return false;

        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) return false;
        return !(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox);
    }

    public static void postPhysics(RemotePlayer bot) {
        MultiSession s = session;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null) {
            abortSimulation(bot);
            return;
        }
        try {
            if (!currentInput.equals(lastSentInput)) {
                lastSentInput = currentInput;
                s.pilotSend(new ServerboundPlayerInputPacket(currentInput));
            }

            if (sprinting != lastSentSprint) {
                lastSentSprint = sprinting;
                s.pilotPlayerCommand(bot, sprinting
                    ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                    : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
            }

            if (physicsRanThisTick && s.teleportSeq() == lastTeleportSeq) {
                simPosition = bot.position();
                simDelta = bot.getDeltaMovement();
                simYaw = bot.getYRot();
                simPitch = bot.getXRot();
                simOnGround = bot.onGround();
                simHorizontalCollision = bot.horizontalCollision;
                s.pilotMove(simPosition, simDelta, simYaw, simPitch, simOnGround, simHorizontalCollision);
                SERVER_TRUTH.recordSent(simPosition, System.currentTimeMillis());
            } else if (s.teleportSeq() != lastTeleportSeq) {
                PositionMoveRotation corrected = s.takeoverPosition();
                if (corrected != null && corrected.position() != null) {
                    simPosition = corrected.position();
                    simDelta = corrected.deltaMovement() == null ? Vec3.ZERO : corrected.deltaMovement();
                    simYaw = corrected.yRot();
                    simPitch = corrected.xRot();
                    simOnGround = false;
                }
            }
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot post-physics failed", error);
        } finally {
            simulating = false;
        }
        try {
            servicePovUpkeep(mc, bot, s);
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot upkeep failed", error);
        }
    }

    public static void passiveTick(RemotePlayer bot) {
        MultiSession s = session;
        if (s == null) return;
        try {
            servicePovUpkeep(Minecraft.getInstance(), bot, s);
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV passive upkeep failed", error);
        }
    }

    public static void abortSimulation(RemotePlayer bot) {
        simulating = false;
    }

    private static void servicePovUpkeep(Minecraft mc, RemotePlayer bot, MultiSession s) {
            if (useItemDelay > 0) useItemDelay--;
            if (s.pilotPacketsReady() && !macroBlocksInput()) {
                MultiPovModuleController.tickModules(mc, bot, breaking, breakPos, breakDir);
            }
            if (s.pilotPacketsReady() && !macroBlocksInput()) tickBreaking(mc, bot, s);
            syncHud(bot);

            if (pendingGuiRestore) {
                pendingGuiRestore = false;
                if (s.containerOpen()) openBotGui();
            }

            if (pendingSignRestore) {
                pendingSignRestore = false;
                if (s.signEditorOpen()) openBotSignEditor(mc, s);
            }

            long guiSeq = s.guiOpenSeq();
            if (guiSeq != lastGuiSeq) {
                lastGuiSeq = guiSeq;
                openBotGui();
            }

            if (botGuiOpen && !autismclient.util.AutismMultiOverlay.isGuiViewerOpen(s.accountId())) {
                botGuiOpen = false;

                if (!macroBlocksInput()) s.pilotCloseContainer();
            }

            long signSeq = s.pilotSignSeq();
            if (signSeq != lastSignSeq) {
                lastSignSeq = signSeq;
                openBotSignEditor(mc, s);
            }

            long bookSeq = s.pilotBookSeq();
            if (bookSeq != lastBookSeq) {
                lastBookSeq = bookSeq;
                openBotBookViewer(mc, bot, s);
            }
    }

    public static void observeMacro(RemotePlayer bot) {
        MultiSession s = session;
        if (s == null) return;
        Vec3 observed = bot.getInterpolation().position();
        s.pilotObserveServerTruth(observed == null ? bot.position() : observed);
        if (!macroObserving) {
            macroObserving = true;
            Minecraft mc = Minecraft.getInstance();
            if (breaking) abortBreaking(mc, s);
            MultiPovModuleController.suspendForMacro(bot);
            currentInput = Input.EMPTY;
            jumpRequested = false;
            sneaking = false;
            sprinting = false;
            lastSentSprint = false;
            physicsRanThisTick = false;
            bot.noPhysics = true;
        }
        currentInput = Input.EMPTY;
        jumpRequested = false;
        sprinting = false;
        physicsRanThisTick = false;
        try {
            servicePovUpkeep(Minecraft.getInstance(), bot, s);
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV macro observer upkeep failed", error);
        }
    }

    private static boolean macroBlocksInput() {
        MultiSession s = session;
        return s != null && s.macroOwnsPilot();
    }

    private static void noteMacroOwnsBot() {
        long now = System.currentTimeMillis();
        if (now - lastMacroInputNoticeAt < 2000L) return;
        lastMacroInputNoticeAt = now;
        autismclient.util.AutismNotifications.show(
            "Macro controls this bot. Stop the macro to use manual POV controls.", 0xFFF2C54B);
    }

    private static float itemUseSpeedMultiplier(RemotePlayer bot) {
        try {
            return bot.getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).speedMultiplier();
        } catch (Throwable ignored) {
            return 0.2F;
        }
    }

    private static Vec2 modifyInputSpeedForSquareMovement(Vec2 input) {
        float length = input.length();
        if (length <= 0.0F) return input;
        Vec2 direction = input.scale(1.0F / length);
        float dx = Math.abs(direction.x);
        float dy = Math.abs(direction.y);
        float tan = dy > dx ? dx / dy : dy / dx;
        float distanceToUnitSquare = Mth.sqrt(1.0F + Mth.square(tan));
        return direction.scale(Math.min(length * distanceToUnitSquare, 1.0F));
    }

    private static void syncHud(RemotePlayer bot) {
        MultiSession s = session;
        if (s == null) return;
        long revision = s.menuRevision();
        if (revision != lastMenuRevision) {
            lastMenuRevision = revision;
            List<ItemStack> inv = s.takeoverInventory();
            for (int handler = 0; handler < inv.size(); handler++) {
                int slot = handlerToInventorySlot(handler);
                if (slot < 0) continue;
                ItemStack want = inv.get(handler);

                if (!ItemStack.matches(bot.getInventory().getItem(slot), want)) {
                    bot.getInventory().setItem(slot, want);
                }
            }
        }
        int selected = s.selectedHotbar();
        if (selected != lastSelectedHotbar && selected >= 0 && selected <= 8) {
            lastSelectedHotbar = selected;
            bot.getInventory().setSelectedSlot(selected);
        }
        bot.getFoodData().setFoodLevel(s.food());
    }

    private static int handlerToInventorySlot(int handler) {
        if (handler >= 36 && handler <= 44) return handler - 36;
        if (handler >= 9 && handler <= 35) return handler;
        if (handler >= 5 && handler <= 8) return 39 - (handler - 5);
        if (handler == 45) return 40;
        return -1;
    }

    public static boolean handleStartAttack(Minecraft mc) {
        MultiSession s = session;
        if (s == null) return false;
        if (macroBlocksInput()) { noteMacroOwnsBot(); return true; }

        if (autismclient.modules.PackFreecamState.isActive()) return true;
        if (!s.pilotPacketsReady()) return true;
        if (mc.hitResult == null) return true;
        RemotePlayer bot = botEntity(mc);
        if (bot == null) return true;
        try {

            HitResult pilotHit = mc.hitResult;
            PovEntityTarget picked = currentPovTarget(pilotHit);
            if (picked != null) {
                if (!s.pilotAttackEntity(picked.wireId())) {
                    notePilotActionFailure("attack packet blocked or disconnected");
                }
                bot.swing(InteractionHand.MAIN_HAND);
                return true;
            }
            switch (pilotHit.getType()) {
                case BLOCK -> {
                    BlockHitResult hit = (BlockHitResult) pilotHit;
                    if (!MultiPovModuleController.shouldCancelBlockMine(mc, hit.getBlockPos())) {
                        startBreaking(mc, bot, s, hit.getBlockPos(), hit.getDirection());
                    }
                }
                case ENTITY -> {
                    Entity target = ((EntityHitResult) pilotHit).getEntity();
                    if (target != bot) {
                        MultiEntityTracker.State targetTruth = pickedTruth(s, target);
                        int wireId = targetTruth == null ? -1 : targetTruth.id();
                        if (!s.pilotAttackEntity(wireId)) {
                            notePilotActionFailure("attack packet blocked or disconnected");
                        }
                    }
                }
                default -> s.pilotSend(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
            bot.swing(InteractionHand.MAIN_HAND);
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot attack failed", error);
        }
        return true;
    }

    private static void notePilotActionFailure(String detail) {
        long now = System.currentTimeMillis();
        if (now - lastPilotActionNoticeAt < 2000L) return;
        lastPilotActionNoticeAt = now;
        String suffix = detail == null || detail.isBlank() ? "" : " (" + detail + ")";
        autismclient.util.AutismNotifications.show(
            "POV action was not sent. Check the Multi packet policy or connection" + suffix + ".",
            0xFFFF5A67);
    }

    public static void handleContinueAttack(Minecraft mc, boolean down) {
        MultiSession s = session;
        if (s == null) return;
        if (macroBlocksInput()) {
            if (breaking) abortBreaking(mc, s);
            if (down) noteMacroOwnsBot();
            return;
        }
        if (!s.pilotPacketsReady()) {
            if (breaking) {
                clearCrack(mc);
                clearBreaking();
            }
            return;
        }
        if (autismclient.modules.PackFreecamState.isActive()) down = false;
        RemotePlayer bot = botEntity(mc);
        if (bot == null) return;
        try {
            if (!down || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                abortBreaking(mc, s);
                return;
            }
            if (destroyDelay > 0) {
                destroyDelay--;
                return;
            }
            if (!breaking) {
                startBreaking(mc, bot, s, hit.getBlockPos(), hit.getDirection());
                if (breaking) swingWhileMining(bot, s);
                return;
            }
            if (!hit.getBlockPos().equals(breakPos)) {
                abortBreaking(mc, s);
                startBreaking(mc, bot, s, hit.getBlockPos(), hit.getDirection());
            }
            if (breaking) swingWhileMining(bot, s);
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot continue-attack failed", error);
        }
    }

    private static void swingWhileMining(RemotePlayer bot, MultiSession s) {
        bot.swing(InteractionHand.MAIN_HAND);
        s.pilotSend(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    private static void startBreaking(Minecraft mc, RemotePlayer bot, MultiSession s, BlockPos pos, Direction dir) {
        if (mc.level == null || mc.level.getBlockState(pos).isAir()
            || MultiPovModuleController.shouldCancelBlockMine(mc, pos)) return;
        BlockState state = mc.level.getBlockState(pos);
        MultiPovModuleController.onBlockStart(bot, pos, dir, state);
        for (net.minecraft.network.protocol.Packet<?> packet : initialBlockPressPackets(pos, dir, s.nextUseSeq())) {
            s.pilotSend(packet);
        }
        float baseProgress = state.getDestroyProgress(bot, mc.level, pos) * s.digSpeedMultiplier();
        if (MultiPovModuleController.fastBreakStartsInstant(bot, state, pos, baseProgress)) {
            s.pilotSend(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, s.nextUseSeq()));
            MultiPovModuleController.afterStop(pos, dir);
            destroyDelay = 5;
            MultiPovModuleController.onBlockEnd(true);
            clearCrack(mc);
            breaking = false;
            breakPos = null;
            breakDir = null;
            return;
        }
        boolean creative = s.takeoverGameModeId() == 1;
        if (creative || baseProgress >= 1.0F) {

            destroyDelay = 5;
            MultiPovModuleController.onBlockEnd(true);
            clearCrack(mc);
            breaking = false;
            breakPos = null;
            breakDir = null;
            return;
        }
        breaking = true;
        breakPos = pos;
        breakDir = dir;
        destroyProgress = 0.0F;
        lastCrackStage = -1;
    }

    static List<net.minecraft.network.protocol.Packet<?>> initialBlockPressPackets(BlockPos pos, Direction dir, int sequence) {
        return List.of(
            new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos, dir == null ? Direction.UP : dir, sequence),
            new ServerboundSwingPacket(InteractionHand.MAIN_HAND)
        );
    }

    private static void abortBreaking(Minecraft mc, MultiSession s) {
        if (!breaking || breakPos == null) return;
        s.pilotSend(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, breakPos,
            breakDir == null ? Direction.DOWN : breakDir));
        MultiPovModuleController.onBlockEnd(true);
        clearCrack(mc);
        clearBreaking();
    }

    private static void clearCrack(Minecraft mc) {
        if (mc.level != null && breakPos != null) mc.level.destroyBlockProgress(pilotedEntityId, breakPos, -1);
    }

    private static void tickBreaking(Minecraft mc, RemotePlayer bot, MultiSession s) {
        if (!breaking || breakPos == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(breakPos);
        if (state.isAir()) {
            MultiPovModuleController.onBlockEnd(true);
            clearCrack(mc);
            clearBreaking();
            return;
        }

        MultiPovModuleController.onBlockProgress(breakPos, breakDir);
        float delta = state.getDestroyProgress(bot, mc.level, breakPos) * s.digSpeedMultiplier();
        delta = MultiPovModuleController.modifyDestroyProgress(delta, state);
        destroyProgress = MultiPovModuleController.applyDamageFinishThreshold(destroyProgress, delta);
        int stage = destroyProgress > 0.0F ? (int) (destroyProgress * 10.0F) : -1;
        if (stage != lastCrackStage) {
            lastCrackStage = stage;
            mc.level.destroyBlockProgress(pilotedEntityId, breakPos, Math.min(stage, 9));
        }
        if (destroyProgress >= 1.0F) {
            s.pilotSend(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, breakPos, breakDir, s.nextUseSeq()));
            MultiPovModuleController.afterStop(breakPos, breakDir);
            MultiPovModuleController.onBlockEnd(true);
            clearCrack(mc);
            clearBreaking();
            destroyDelay = 5;
        }
    }

    public static boolean handleStartUseItem(Minecraft mc) {
        MultiSession s = session;
        if (s == null) return false;
        if (macroBlocksInput()) { noteMacroOwnsBot(); return true; }
        if (autismclient.modules.PackFreecamState.isActive()) {
            return true;
        }
        if (!s.pilotPacketsReady()) {
            return true;
        }
        if (useItemDelay > 0) {
            return true;
        }
        RemotePlayer bot = botEntity(mc);
        if (bot == null) {
            return true;
        }

        if (bot.isUsingItem()) {
            return true;
        }
        useItemDelay = 4;
        try {

            HitResult hit = mc.hitResult;
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ItemStack mainStack = bot.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offStack = bot.getItemInHand(InteractionHand.OFF_HAND);
            PovEntityTarget picked = currentPovTarget(hit);
            if (picked != null) {
                Entity renderedTarget = hit instanceof EntityHitResult entityHit && picked.matches(entityHit.getEntity())
                    ? entityHit.getEntity() : null;
                interactWithPovTarget(mc, s, bot, renderedTarget, picked);
                return true;
            }

            autismclient.modules.AirPlaceModule.Placement airPlacement =
                autismclient.modules.AirPlaceModule.activePlacement(bot, hit);
            if (airPlacement != null) {
                if (autismclient.modules.ModuleRegistry.shouldCancelUseExcept(
                    airPlacement.hit(), airPlacement.hand(), "air-place")) {
                    return true;
                }
                s.pilotSend(new ServerboundUseItemOnPacket(
                    airPlacement.hand(), airPlacement.hit(), s.nextUseSeq()));
                s.pilotSend(new ServerboundSwingPacket(airPlacement.hand()));
                bot.swing(airPlacement.hand());
                return true;
            }

            boolean fishingRod = mainStack != null && mainStack.is(net.minecraft.world.item.Items.FISHING_ROD);
            if (!fishingRod && offStack != null && offStack.is(net.minecraft.world.item.Items.FISHING_ROD)) {
                fishingRod = true;
                hand = InteractionHand.OFF_HAND;
            }

            if (fishingRod) {
                s.pilotSend(new ServerboundUseItemPacket(hand, s.nextUseSeq(), simYaw, simPitch));
            } else if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY) {
                Entity target = entityHit.getEntity();
                MultiEntityTracker.State targetTruth = pickedTruth(s, target);
                Vec3 targetPosition = targetTruth == null ? target.position() : targetTruth.position();
                Vec3 location = entityHit.getLocation().subtract(targetPosition);
                int wireId = targetTruth == null ? -1 : targetTruth.id();
                if (wireId >= 0) {
                    interactWithEntity(mc, s, bot, target, wireId, location);
                } else {
                    notePilotActionFailure("entity is not tracked on the bot connection");
                }
                return true;
            } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                s.pilotSend(new ServerboundUseItemOnPacket(hand, blockHit, s.nextUseSeq()));
            } else {

                net.minecraft.world.item.ItemStack held = mainStack;
                if (held == null || !held.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
                    net.minecraft.world.item.ItemStack off = bot.getItemInHand(InteractionHand.OFF_HAND);
                    if (off != null && off.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
                        hand = InteractionHand.OFF_HAND;
                        held = off;
                    }
                }
                s.pilotSend(new ServerboundUseItemPacket(hand, s.nextUseSeq(), simYaw, simPitch));
                if (held != null && held.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.BookEditScreen(bot, held, hand,
                        held.getOrDefault(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT,
                            net.minecraft.world.item.component.WritableBookContent.EMPTY)));
                }
            }
            s.pilotSend(new ServerboundSwingPacket(hand));
            bot.swing(hand);
        } catch (Throwable ignored) {
            notePilotActionFailure("interaction failed");
        }
        return true;
    }

    private static void interactWithEntity(Minecraft mc, MultiSession session, RemotePlayer bot, Entity target,
                                           int wireId, Vec3 location) {
        if (mc.gameMode == null) {
            notePilotActionFailure("game mode is unavailable");
            return;
        }
        boolean sentAny = false;
        EntityHitResult vanillaHit = new EntityHitResult(target, target.position().add(location));
        for (InteractionHand candidateHand : InteractionHand.values()) {
            VanillaInteractionRoute route = new VanillaInteractionRoute(session, wireId);
            VANILLA_INTERACTION_ROUTE.set(route);
            InteractionResult result;
            try {
                result = mc.gameMode.interact(bot, target, vanillaHit, candidateHand);
            } finally {
                VANILLA_INTERACTION_ROUTE.remove();
            }
            sentAny |= route.sent;
            if (!route.captured) {
                notePilotActionFailure("vanilla packet route was not captured");
                return;
            }
            if (!route.sent) continue;
            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    session.pilotSend(new ServerboundSwingPacket(candidateHand));
                    bot.swing(candidateHand);
                }
                return;
            }
        }
        if (!sentAny) notePilotActionFailure("entity interaction blocked or disconnected");
    }

    private static void interactWithPovTarget(Minecraft mc, MultiSession session, RemotePlayer bot,
                                              Entity renderedTarget,
                                              PovEntityTarget picked) {
        if (renderedTarget != null) {
            interactWithEntity(mc, session, bot, renderedTarget, picked.wireId(), picked.relativeHit());
            return;
        }

        if (!session.pilotInteractEntity(
            picked.wireId(), InteractionHand.MAIN_HAND, picked.relativeHit(), sneaking)) {
            notePilotActionFailure("entity interaction blocked or disconnected");
        }
    }

    public static boolean rerouteVanillaInteraction(ServerboundInteractPacket packet) {
        VanillaInteractionRoute route = VANILLA_INTERACTION_ROUTE.get();
        if (route == null || packet == null) return false;
        route.captured = true;
        route.sent = route.session.pilotInteractEntity(
            route.wireId, packet.hand(), packet.location(), packet.usingSecondaryAction());
        return true;
    }

    private static void openBotGui() {
        MultiSession s = session;
        if (s == null) return;
        autismclient.util.AutismMultiOverlay.openGuiViewerHosted(s.accountId());
        botGuiOpen = true;
    }

    private static void openBotSignEditor(Minecraft mc, MultiSession s) {
        try {
            BlockPos pos = s.pilotSignPos();
            if (pos == null || mc.level == null) return;
            if (!(mc.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) {
                return;
            }
            boolean front = s.pilotSignFront();
            net.minecraft.world.level.block.Block block = sign.getBlockState().getBlock();
            boolean hanging = block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
                || block instanceof net.minecraft.world.level.block.WallHangingSignBlock;
            mc.gui.setScreen(hanging
                ? new net.minecraft.client.gui.screens.inventory.HangingSignEditScreen(sign, front, mc.isTextFilteringEnabled())
                : new net.minecraft.client.gui.screens.inventory.SignEditScreen(sign, front, mc.isTextFilteringEnabled()));
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot sign editor open failed", error);
        }
    }

    private static void openBotBookViewer(Minecraft mc, RemotePlayer bot, MultiSession s) {
        try {
            if (bot == null) return;
            net.minecraft.world.item.ItemStack stack = bot.getItemInHand(s.pilotBookHand());
            if (stack == null || stack.isEmpty()) return;
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(stack)));
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot book open failed", error);
        }
    }

    public static boolean rerouteEditPacket(net.minecraft.network.protocol.Packet<?> packet) {
        MultiSession s = session;
        if (s == null) return false;
        if (macroBlocksInput()) {
            noteMacroOwnsBot();
            return true;
        }
        try {
            if (packet instanceof net.minecraft.network.protocol.game.ServerboundEditBookPacket book
                && book.slot() >= 0 && book.slot() <= 8) {
                packet = new net.minecraft.network.protocol.game.ServerboundEditBookPacket(
                    Math.max(0, Math.min(8, s.selectedHotbar())), book.pages(), book.title());
            }
            s.pilotSend(packet);
            if (packet instanceof net.minecraft.network.protocol.game.ServerboundSignUpdatePacket) {
                s.clearSignEditor();
            }
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot edit reroute failed", error);
        }
        return true;
    }

    public static void handleReleaseUseItem() {
        MultiSession s = session;
        if (s == null) return;
        if (macroBlocksInput()) return;
        s.pilotSend(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
    }

    private static boolean useKeyWasDown;

    public static boolean drainKeybinds(Minecraft mc) {
        MultiSession s = session;
        if (s == null || mc.options == null) return false;

        if (autismclient.modules.PackFreecamState.isActive()) return false;
        try {
            if (macroBlocksInput()) {
                boolean attempted = down(mc.options.keyUp) || down(mc.options.keyDown)
                    || down(mc.options.keyLeft) || down(mc.options.keyRight) || down(mc.options.keyJump)
                    || down(mc.options.keyShift) || down(mc.options.keySprint);
                for (net.minecraft.client.KeyMapping key : mc.options.keyHotbarSlots) {
                    while (key.consumeClick()) attempted = true;
                }
                while (mc.options.keyDrop.consumeClick()) attempted = true;
                while (mc.options.keySwapOffhand.consumeClick()) attempted = true;
                while (mc.options.keyPickItem.consumeClick()) attempted = true;
                boolean openViewer = false;
                while (mc.options.keyInventory.consumeClick()) openViewer = true;
                if (openViewer) openBotGui();
                if (attempted) noteMacroOwnsBot();
                useKeyWasDown = down(mc.options.keyUse);
                return false;
            }
            for (int i = 0; i < mc.options.keyHotbarSlots.length && i < 9; i++) {
                boolean pressed = false;
                while (mc.options.keyHotbarSlots[i].consumeClick()) pressed = true;
                if (pressed) s.selectHotbar(i);
            }
            while (mc.options.keyDrop.consumeClick()) {
                s.dropSelected(ctrlDown(mc));
            }
            while (mc.options.keySwapOffhand.consumeClick()) {
                s.pilotSend(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            }
            boolean openViewer = false;
            while (mc.options.keyInventory.consumeClick()) openViewer = true;
            if (openViewer) openBotGui();
            while (mc.options.keyPickItem.consumeClick()) {

            }

            boolean useDown = down(mc.options.keyUse);
            if (useKeyWasDown && !useDown) handleReleaseUseItem();
            useKeyWasDown = useDown;
        } catch (Throwable error) {
            AutismClientAddon.LOG.warn("POV pilot keybind drain failed", error);
        }
        return false;
    }

    public static boolean handleTurn(double deltaYaw, double deltaPitch) {
        MultiSession s = session;
        if (s == null) return false;
        if (macroBlocksInput()) { noteMacroOwnsBot(); return true; }
        RemotePlayer bot = botEntity(Minecraft.getInstance());
        if (bot == null) return true;

        simYaw += (float) (deltaYaw * 0.15D);
        simPitch = Mth.clamp(simPitch + (float) (deltaPitch * 0.15D), -90.0F, 90.0F);
        bot.setYRot(simYaw);
        bot.setXRot(simPitch);
        bot.setYHeadRot(simYaw);
        return true;
    }

    static void applyModuleRotation(RemotePlayer bot, float yaw, float pitch) {
        if (bot == null || !isManualControlEntity(bot)) return;
        simYaw = yaw;
        simPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        bot.setYRot(simYaw);
        bot.setXRot(simPitch);
        bot.setYHeadRot(simYaw);
    }

    public static boolean handleHotbarScroll(double scrollY) {
        MultiSession s = session;
        if (s == null || scrollY == 0.0D) return false;
        if (macroBlocksInput()) { noteMacroOwnsBot(); return true; }
        int selected = Math.max(0, Math.min(8, s.selectedHotbar()));
        int step = scrollY > 0.0D ? -1 : 1;
        s.selectHotbar((selected + step + 9) % 9);
        return true;
    }

    public static boolean rerouteChat(String line) {
        MultiSession s = session;
        if (s == null || line == null || line.isBlank()) return false;
        if (macroBlocksInput()) { noteMacroOwnsBot(); return true; }
        s.sendConsoleLine(line);
        return true;
    }

    static String activeAccountLabel() {
        MultiSession s = session;
        return s == null ? null : s.accountId();
    }

    private static boolean ctrlDown(Minecraft mc) {
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static RemotePlayer botEntity(Minecraft mc) {
        if (mc.level == null || pilotedEntityId < 0) return null;
        return mc.level.getEntity(pilotedEntityId) instanceof RemotePlayer bot ? bot : null;
    }
}
