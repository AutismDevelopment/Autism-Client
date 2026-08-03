

package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismInputClicker;
import autismclient.util.AutismKeyMappingBridge;
import autismclient.util.AutismRotationUtil;
import autismclient.util.RegistryListCodec;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class ScaffoldModule extends Module {
    static final int SLOT_RESET_TICKS = 5;
    static final int ROTATION_RESET_TICKS = 5;
    static final float ROTATION_RESET_THRESHOLD = 2.0F;
    static final float MAX_TURN_SPEED = 180.0F;
    static final double MIN_FACE_DISTANCE = 0.0D;
    static final float TIMER_MULTIPLIER = 1.0F;
    static final boolean SWITCH_BACK_DEFAULT = true;
    static final String SWITCH_BACK_TIP = "Restore previous hotbar slot.";

    private static final double FACE_INSET = 0.15D;
    private static final double GEOMETRY_EPSILON = 1.0E-9D;

    private static final int VULCAN_SNEAK_TICKS = 2;

    private static final long TOWER_INTERVAL_MS = 280L;

    private static final float VULCAN_MAX_YAW_STEP = 120.0F;
    private static final float VULCAN_MAX_PITCH_STEP = 8.0F;
    private static final double[] SUPPORT_SAMPLES = {0.301D, 0.0D, -0.301D};
    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final int MAX_PLACEMENT_OFFSETS = 4;
    private static final float DIRECTION_HYSTERESIS_DEGREES = 30.0F;
    private static final double SUPPORT_SURFACE_EPSILON = 1.0E-3D;
    private static final double SUPPORT_OVERLAP_HYSTERESIS = 0.02D;
    private static final double PREDICTION_BACKOFF = 0.2D;
    private static final double PREDICTION_CUTOFF_DISTANCE = 0.05D;
    private static final double PREDICTION_LINE_LENGTH = 3.0D;
    private static final int PREDICTION_WARMUP_PLACEMENTS = 2;
    private static final List<BlockPos> NORMAL_OFFSETS = normalOffsets();

    private int originalSlot = -1;
    private int requestedSlot = -1;
    private int slotResetTicks;
    private boolean selectionPending;
    private AutismRotationUtil.Rotation serverRotation;
    private MovementLine currentMovementLine;
    private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED_BLOCKS);
    private final ArrayDeque<Vec3> placementOffsets = new ArrayDeque<>(MAX_PLACEMENT_OFFSETS + 1);
    private BlockPos lastSupportPosition;
    private SupportReference lastSupportReference;
    private float lastDirectionAngle = Float.NaN;
    private String cachedFilterRaw = "";
    private Set<Block> cachedFilterBlocks = Set.of();
    private AutismRotationUtil.Rotation grimSilentRotation;
    private int grimRotationResetTicks;
    private int vulcanSneakTicks;
    private boolean vulcanSneakReleased = true;

    private long lastSuccessfulPlaceMs;

    private TellyPhase tellyPhase = TellyPhase.IDLE;
    private TellyMotion tellyMotion = TellyMotion.RELEASED;
    private boolean tellyOwnsInput;
    private boolean tellyStopRequested;
    private boolean tellyJumpThisTick;
    private boolean tellySneakThisTick;
    private boolean tellyPhysicalSpaceWasDown;
    private boolean tellyRiseQueued;
    private boolean tellySpaceHeld;
    private boolean tellyFinishing;
    private boolean tellyCycleRises;
    private boolean tellyRaisedBlockPlaced;
    private boolean tellyPlacementQueued;
    private boolean tellyWalkOffCatch;
    private int tellyWalkOffGraceTicks;
    private int tellyClickCooldown;
    private int tellyAirTicks;
    private int tellyFlatPlacements;
    private int tellyFailedClicks;
    private int tellyForwardDwellTicks;
    private int tellyBridgeY;
    private double tellyTakeoffY;
    private double tellyTakeoffProgress;
    private float tellyAnchorYaw;
    private float tellyForwardPitch;
    private double tellyLaneCenter;
    private int tellyRecoveryTicks;
    private boolean tellyCourseLatched;
    private boolean tellyGroundSteeringActive;
    private float tellyGroundSteerOffset;
    private int tellyCourseDeviationTicks;
    private int tellyEdgeHoldTicks;
    private boolean tellyRotationHeldForPlacement;
    private boolean tellyReturnFlickPending;
    private boolean tellySecureFootingQueued;
    private int tellyHoldWatchdogTicks;
    private boolean tellyGroundLaunchAllowed;

    private AutismRotationUtil.Rotation tellySmoothedRotation;
    private boolean tellyTurnSettling;
    private int tellySettleHoldTicks;
    private int tellySettleDwellTicks;
    private BlockPos tellyLastBridge;
    private BlockPos tellyRaisedCell;
    private BlockPos tellyQueuedBlock;
    private Vec3 tellyLineOrigin;
    private TellyPlacement tellyTarget;
    private long tellyCycleSerial;

    ScaffoldModule() {
        super("scaffold", "Scaffold", ModuleCategory.MOVEMENT, "Places blocks beneath you.");
        add(new ChoiceSetting("mode", "Mode", "Grim", "Grim", "Vulcan", "Fast", "Telly")
            .description("Choose scaffold mode.")
            .build());
        add(new BoolSetting("switch-back", "Switch Back", SWITCH_BACK_DEFAULT)
            .description(SWITCH_BACK_TIP)
            .build());
        add(new ChoiceSetting("filter-mode", "Filter", "Off", "Off", "Whitelist", "Blacklist")
            .description("Choose filter mode.")
            .build());
        add(new BoolSetting("stabilize-movement", "Stabilize Movement", false)
            .visibleWhen(() -> "Fast".equals(choice("mode")))
            .description("Keeps bridge line.")
            .build());
        add(RegistryListSetting.placeableBlocks("blocks", "Blocks")
            .visibleWhen(() -> !"Off".equals(choice("filter-mode")))
            .description("Choose filtered blocks.")
            .build());
        add(new BoolSetting("place-animation", "Place Animation", true)
            .description("Placement ripple effect").group("Animation").build());
        add(new BoolSetting("animation-custom", "Custom Color", false)
            .description("Custom ripple color").group("Animation")
            .visibleWhen(() -> bool("place-animation")).build());
        add(new ColorSetting("animation-color", "Color", 0xFFFF3B3B)
            .description("Ripple color").group("Animation")
            .visibleWhen(() -> bool("place-animation") && bool("animation-custom")).build());
    }

    @Override
    public void onEnable() {
        clearRuntime(true);
        pushAnimation();
    }

    @Override
    public String info() {
        return choice("mode");
    }

    @Override
    public void onDisable() {
        clearRuntime(true);
        autismclient.util.AutismScaffoldPlaceRenderer.disable();
    }

    @Override
    public void onGameLeft() {
        clearRuntime(false);
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("blocks".equals(settingId) || "filter-mode".equals(settingId)) {
            cachedFilterRaw = null;
        }
        if ("switch-back".equals(settingId) && !bool("switch-back")) {
            originalSlot = -1;
            slotResetTicks = 0;
        }
        if ("mode".equals(settingId)) clearRuntime(true);
        pushAnimation();
    }

    private void pushAnimation() {
        autismclient.util.AutismScaffoldPlaceRenderer.push(
            isEnabled() && bool("place-animation"),
            bool("animation-custom"),
            ModuleRenderUtil.color(this, "animation-color", 0xFFFF3B3B));
    }

    @Override
    public void preMovementTick() {
        if (isTellyMode()) {
            runTellyTick();
        } else if (isGrimFamily()) {
            runGrimPlacement();
        } else {
            runFastPlacement();
        }
    }

    private void runFastPlacement() {
        if (!canRun()) {
            currentMovementLine = null;
            tickSlotReset();
            return;
        }

        PlacementTarget target = findPlacementTarget(planningStack());
        if (target == null) {
            tickSlotReset();
            return;
        }

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) {

                refreshSelectionReset();
            } else {
                tickSlotReset();
            }
            return;
        }

        refreshSelectionReset();
        ItemStack stack = MC.player.getItemInHand(hand);
        target = findPlacementTarget(stack);
        if (target == null) {
            return;
        }
        AutismRotationUtil.Rotation rotation = AutismRotationUtil.normalizeToSensitivity(
            target.rotation(), AutismRotationUtil.playerRotation(MC.player));
        PlacementTarget verified = validateForHeldBlock(target, stack, hand, rotation);
        if (verified == null) {
            return;
        }

        place(verified, hand, stack);
    }

    private void runGrimPlacement() {
        boolean vulcan = isVulcanMode();
        if (!canRun()) {
            currentMovementLine = null;
            tickGrimRotationReset();
            tickSlotReset();
            return;
        }

        PlacementTarget target = findPlacementTarget(planningStack());
        if (target == null) {
            tickGrimRotationReset();
            tickSlotReset();
            return;
        }

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) refreshSelectionReset();
            else {
                tickSlotReset();
            }
            tickGrimRotationReset();
            return;
        }

        refreshSelectionReset();
        ItemStack stack = MC.player.getItemInHand(hand);
        target = findPlacementTarget(stack);
        if (target == null) {
            tickGrimRotationReset();
            return;
        }

        AutismRotationUtil.Rotation knownServerRotation = serverRotation();
        AutismRotationUtil.Rotation rotationBase = grimSilentRotation != null
            ? grimSilentRotation : knownServerRotation;

        AutismRotationUtil.Rotation rotation = AutismRotationUtil.normalizeToSensitivity(
            vulcan ? AutismRotationUtil.towardsLinear(rotationBase, target.rotation(),
                VULCAN_MAX_YAW_STEP, VULCAN_MAX_PITCH_STEP) : target.rotation(),
            rotationBase);
        if (vulcan) {
            grimSilentRotation = rotation;
            grimRotationResetTicks = ROTATION_RESET_TICKS;
        }
        PlacementTarget aimedTarget = validateForHeldBlock(target, stack, hand, rotation);
        if (aimedTarget == null) {
            if (!vulcan) tickGrimRotationReset();
            return;
        }

        if (vulcan && MC.player.onGround() && (vulcanSneakTicks > 0 || !vulcanSneakReleased)) {
            return;
        }

        grimSilentRotation = rotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;

        if (vulcan && isInColumnUpPlacement(aimedTarget)
            && System.currentTimeMillis() - lastSuccessfulPlaceMs < TOWER_INTERVAL_MS) {
            return;
        }
        place(aimedTarget, hand, stack, false, false);
        if (vulcan) {
            lastSuccessfulPlaceMs = System.currentTimeMillis();

            if (vulcanSneakTicks <= 0 && vulcanSneakReleased) {
                vulcanSneakTicks = VULCAN_SNEAK_TICKS;
                vulcanSneakReleased = false;
            }
        }
    }

    private boolean isInColumnUpPlacement(PlacementTarget target) {
        return target.face() == Direction.UP
            && target.placedBlock().getX() == MC.player.blockPosition().getX()
            && target.placedBlock().getZ() == MC.player.blockPosition().getZ();
    }

    private static final double TELLY_LANE_ENTER = 0.08D;
    private static final double TELLY_LANE_EXIT = 0.035D;
    private static final double TELLY_LANE_VELOCITY_EXIT = 0.012D;
    private static final double TELLY_LANE_PREDICT_TICKS = 3.0D;
    private static final double TELLY_LANE_PREDICT_ENTER = 0.10D;
    private static final double TELLY_LANE_VELOCITY_GAIN = 0.18D;
    private static final double TELLY_LANE_MAX_VELOCITY = 0.055D;
    private static final float TELLY_LANE_MIN_STEER = 8.0F;
    private static final float TELLY_LANE_MAX_STEER = 15.0F;
    private static final float TELLY_LANE_OUTWARD_SLEW = 5.0F;
    private static final float TELLY_LANE_RETURN_SLEW = 7.0F;
    private static final double TELLY_LANE_RETURN_MARGIN = 0.10D;
    private static final double TELLY_FACE_VISIBILITY_EPSILON = 0.0125D;
    private static final double TELLY_AIR_CONTROL = 0.0196D;
    private static final double TELLY_SAFE_OVERLAP = 0.12D;

    private static final int TELLY_FORWARD_DWELL_TICKS = 0;
    private static final double[] TELLY_FACE_OFFSETS = {0.0D, -0.16D, 0.16D, -0.28D, 0.28D};

    private static final float TELLY_ROTATION_STEP = 75.0F;
    private static final float TELLY_FLICK_STEP_CAP = 150.0F;
    private static final float TELLY_SETTLE_YAW_EPSILON = 1.0F;
    private static final double TELLY_SETTLE_SPEED_FLOOR = 0.08D;
    private static final float TELLY_SETTLE_VELOCITY_ANGLE = 25.0F;

    private static final double TELLY_SETTLE_LANE_EPSILON = 0.45D;
    private static final int TELLY_SETTLE_DWELL_TICKS = 1;
    private static final int TELLY_SETTLE_TIMEOUT_TICKS = 30;

    private void runTellyTick() {
        tellyJumpThisTick = false;
        tellySneakThisTick = false;
        tellyGroundLaunchAllowed = false;
        maintainTellyClickPipeline();

        if (!canRun()) {
            resetTellyState();
            tickSlotReset();
            return;
        }

        LocalPlayer player = MC.player;
        boolean physicalForward = physicallyDown(MC.options.keyUp);
        boolean physicalSpace = physicallyDown(MC.options.keyJump);

        boolean physicalMoving = physicalForward
            || physicallyDown(MC.options.keyDown)
            || physicallyDown(MC.options.keyLeft)
            || physicallyDown(MC.options.keyRight);
        tellySpaceHeld = physicalSpace;
        if (shouldQueueTellyRise(
            physicalForward, physicalSpace, tellyPhysicalSpaceWasDown, tellyOwnsInput
        )) {
            tellyRiseQueued = true;
        }
        tellyPhysicalSpaceWasDown = physicalSpace;

        if (!tellyOwnsInput) {
            if (!physicalMoving || !player.onGround()) {
                if (!physicalMoving) tellyCourseLatched = false;
                tickSlotReset();
                return;
            }
            BlockPos under = solidBlockUnder(player);
            if (under == null) {
                tickSlotReset();
                return;
            }

            if (!isValidBlock(planningStack())) {
                tickSlotReset();
                return;
            }
            beginTellyControl(player, under);
        }

        if (tellyCourseLatched) updateTellyCourseIntent(player);
        grimSilentRotation = advanceTellyRotationStream(player);
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        tellyStopRequested = !physicalMoving;

        if (player.onGround()) {
            runGroundedTelly(player);
        } else if (tellyPhase == TellyPhase.RECOVERING) {

            beginTellyWalkOffCatch(player);
            runAirborneTelly(player);
        } else {
            runAirborneTelly(player);
        }

        if (tellyOwnsInput && player.onGround() && tellyMotion == TellyMotion.HOLD
            && !tellyStopRequested && !tellyFinishing) {
            if (++tellyHoldWatchdogTicks >= 30) {
                tellyHoldWatchdogTicks = 0;
                tellyCourseDeviationTicks = 0;
                tellyTurnSettling = false;
                tellySettleHoldTicks = 0;
                tellySettleDwellTicks = 0;
                tellyEdgeHoldTicks = 0;
                if (tellyPhase == TellyPhase.RECOVERING) {
                    tellyPhase = TellyPhase.FORWARD_DWELL;
                    tellyRecoveryTicks = 0;
                }
            }
        } else {
            tellyHoldWatchdogTicks = 0;
        }
        tickSlotReset();
    }

    private void beginTellyControl(LocalPlayer player, BlockPos under) {
        tellyOwnsInput = true;
        tellyStopRequested = false;
        tellyFinishing = false;
        tellyPhase = TellyPhase.FORWARD_DWELL;
        tellyMotion = TellyMotion.FORWARD;

        tellySmoothedRotation = serverRotation();

        float lookYaw = tellyMovementYaw(player);
        boolean hadCourse = tellyCourseLatched && Float.isFinite(tellyAnchorYaw);
        float previousAnchor = tellyAnchorYaw;
        if (hadCourse) {

            float lookDeviation = Math.abs(AutismRotationUtil.angleDifference(tellyAnchorYaw, lookYaw));
            tellyAnchorYaw = lookDeviation > 45.0F
                ? snapTellyYaw(lookYaw)
                : Mth.wrapDegrees(tellyAnchorYaw);
        } else {
            tellyAnchorYaw = snapTellyYaw(lookYaw);
        }
        tellyCourseLatched = true;
        if (hadCourse && Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(tellyAnchorYaw)) != 0) {
            beginTellyTurnSettle();
        }
        tellyForwardPitch = Mth.clamp(player.getXRot(), -82.0F, 82.0F);
        tellyLineOrigin = laneOrigin(under, player.position(), tellyAnchorYaw);
        tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
        tellyCourseDeviationTicks = 0;
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
        tellyLastBridge = under.immutable();
        tellyBridgeY = under.getY();
        tellyForwardDwellTicks = 0;
        tellyAirTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyTarget = null;
        tellyQueuedBlock = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
    }

    private void updateTellyCourseIntent(LocalPlayer player) {
        float deviation = Math.abs(AutismRotationUtil.angleDifference(tellyAnchorYaw, tellyMovementYaw(player)));
        tellyCourseDeviationTicks = nextTellyCourseDeviationTicks(tellyCourseDeviationTicks, deviation);
    }

    static int nextTellyCourseDeviationTicks(int current, float deviation) {
        if (deviation > 45.0F) return current + 1;
        if (deviation < 35.0F) return 0;
        return Math.max(0, current - 1);
    }

    private void applyTellyCourseTurn(LocalPlayer player, BlockPos laneSupport) {
        if (laneSupport == null || tellyCourseDeviationTicks < 3) return;
        tellyCourseDeviationTicks = 0;
        float previousAnchor = tellyAnchorYaw;
        tellyAnchorYaw = snapTellyYaw(tellyMovementYaw(player));
        tellyCourseLatched = true;
        tellyLineOrigin = laneOrigin(laneSupport, player.position(), tellyAnchorYaw);
        tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
        clearTellyGroundSteering();
        if (Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(tellyAnchorYaw)) != 0) {
            beginTellyTurnSettle();
        }
    }

    private boolean tellyTurnIntentPending() {
        return tellyCourseDeviationTicks >= 2;
    }

    private void beginTellyTurnSettle() {

        if (tellyTurnSettling) return;
        tellyTurnSettling = true;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = TELLY_SETTLE_DWELL_TICKS;
        tellyTarget = null;
        clearTellyGroundSteering();
    }

    private void runGroundedTelly(LocalPlayer player) {
        BlockPos under = solidBlockUnder(player);
        if (tellyPhase == TellyPhase.RECOVERING) {
            runTellyRunupRecovery(player, under != null ? under : tellyLastBridge);
            return;
        }
        if (under != null) {

            applyTellyCourseTurn(player, under);

            double laneError = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
            if (Math.abs(laneError) > 0.55D) {
                tellyLineOrigin = laneOrigin(under, player.position(), tellyAnchorYaw);
                tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
                clearTellyGroundSteering();
            }
        }
        if (under == null) {

            if (tellyLastBridge == null) {
                releaseTellyControl();
                return;
            }

            if (tellyAirTicks > 0
                || tellyPhase == TellyPhase.LAUNCH
                || tellyPhase == TellyPhase.AIMING
                || tellyPhase == TellyPhase.RETURNING) {
                finishTellyCycleOnGround(tellyLastBridge);
            }
            if (tellyStopRequested || tellyFinishing) {
                tellyMotion = TellyMotion.HOLD;
                tellySneakThisTick = true;

                tellyEdgeHoldTicks++;
                if (tellyEdgeHoldTicks < 40) {

                    if (tellyOverhangDirection(player) != null && tryTellySecureFooting(player)) return;
                    if (tryTellyLipSecurePlacement(player)) return;
                }
                if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.02D) {

                    releaseTellyControl();
                }
                return;
            }

            applyTellyCourseTurn(player, tellyLastBridge);
            if (tellyTurnSettling && runTellySettleHold(player, tellyLastBridge)) return;

            if (tellyTarget == null) {
                ItemStack lipStack = planningStack();
                if (isValidBlock(lipStack)) tellyTarget = nextFlatTellyPlacement(player, lipStack);
            }
            if (tellyTarget != null
                && !tellyTurnIntentPending()
                && isTellyLaunchCatchable(player, tellyLastBridge)
                && tellyFlickBackForLaunch()) {
                startTellyLaunch(player, tellyTarget);
                return;
            }
            if (tellyTurnIntentPending()) {

                tellyEdgeHoldTicks++;
                if (tellyEdgeHoldTicks < 20) {
                    tellyMotion = TellyMotion.HOLD;
                    tellySneakThisTick = true;
                    if (tellyOverhangDirection(player) != null) tryTellySecureFooting(player);
                    return;
                }
                tellyCourseDeviationTicks = 0;
                tellyEdgeHoldTicks = 0;
            }
            if (!isValidBlock(planningStack())) {

                releaseTellyControl();
                return;
            }

            tellyMotion = TellyMotion.FORWARD;
            tellySneakThisTick = false;
            return;
        }

        if (tellyTurnSettling && runTellySettleHold(player, under)) return;

        if (tellyFinishing) {

            runTellyGroundedStopHold(player, under);
            return;
        }

        if (tellyAirTicks > 0
            || tellyPhase == TellyPhase.LAUNCH
            || tellyPhase == TellyPhase.AIMING
            || tellyPhase == TellyPhase.RETURNING) {
            finishTellyCycleOnGround(under);

            if (tellyLandingTransition(atTellyEdge(player, under)) == TellyLandingTransition.DWELL) {

                return;
            }

            tellyPhase = TellyPhase.RUNNING;
            tellyForwardDwellTicks = 0;
        }

        if (tellyPhase == TellyPhase.FORWARD_DWELL
            && runTellyForwardDwell(player, under)) {
            return;
        }

        boolean nearEdge = atTellyEdge(player, under);

        tellyGroundLaunchAllowed = !nearEdge && tellyRunwayRemaining(player, under) > 4.5D;
        if (tellyStopRequested) {
            runTellyGroundedStopHold(player, under);
            return;
        }

        tellyMotion = TellyMotion.FORWARD;
        updateTellyGroundSteering(player, under);
        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (!selectionPending) {

                releaseTellyControl();
                return;
            }
            tellyMotion = nearEdge ? TellyMotion.HOLD : TellyMotion.FORWARD;
            tellySneakThisTick = nearEdge;
            if (selectionPending) refreshSelectionReset();
            return;
        }
        refreshSelectionReset();

        ItemStack stack = player.getItemInHand(hand);
        if (!isValidBlock(stack)) {
            if (!selectionPending) {
                releaseTellyControl();
                return;
            }
            tellyMotion = nearEdge ? TellyMotion.HOLD : TellyMotion.FORWARD;
            tellySneakThisTick = nearEdge;
            return;
        }

        tellyLastBridge = under.immutable();
        tellyBridgeY = under.getY();
        TellyPlacement first = nextFlatTellyPlacement(player, stack);
        tellyTarget = first;
        if (tellyGroundLaunchAllowed && tellySpaceHeld && !tellyTurnIntentPending()) {

            startTellyLaunch(player, first);
            return;
        }
        if (!nearEdge) {
            tellyEdgeHoldTicks = 0;
            return;
        }

        if (first == null) {

            tellyEdgeHoldTicks++;
            if (tellyEdgeHoldTicks >= 40) {
                releaseTellyControl();
                return;
            }
            tellyMotion = TellyMotion.HOLD;
            tellySneakThisTick = true;
            return;
        }
        tellyEdgeHoldTicks = 0;
        if (tellyTurnIntentPending()) {

            tellyEdgeHoldTicks++;
            if (tellyEdgeHoldTicks < 20) {
                tellyMotion = TellyMotion.HOLD;
                tellySneakThisTick = true;
                if (tellyOverhangDirection(player) != null) tryTellySecureFooting(player);
                return;
            }
            tellyCourseDeviationTicks = 0;
            tellyEdgeHoldTicks = 0;
        }

        boolean launchCatchable = (tellyRiseQueued || tellySpaceHeld)
            ? isTellyRiseLaunchCatchable(player, under)
            : isTellyLaunchCatchable(player, under);
        if (requiresTellyRunupRecovery(launchCatchable)) {

            beginTellyRunupRecovery(player, under);
            return;
        }

        if (!tellyFlickBackForLaunch()) {
            tellyMotion = TellyMotion.HOLD;
            tellySneakThisTick = true;
            return;
        }

        startTellyLaunch(player, first);
    }

    private boolean runTellySettleHold(LocalPlayer player, BlockPos under) {
        if (tellyStopRequested) {
            tellyTurnSettling = false;
            return false;
        }
        if (tellyAirTicks > 0
            || tellyPhase == TellyPhase.LAUNCH
            || tellyPhase == TellyPhase.AIMING
            || tellyPhase == TellyPhase.RETURNING) {
            finishTellyCycleOnGround(under);
        }
        Vec3 velocity = player.getDeltaMovement();
        Vec3 forward = tellyForwardVector();
        Vec3 left = tellyLeftVector();
        double laneError = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
        if (Math.abs(laneError) > 0.55D) {

            tellyLineOrigin = laneOrigin(under, player.position(), tellyAnchorYaw);
            tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
            laneError = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
        }
        boolean settled = tellySmoothedRotation != null && tellyTurnSettled(
            tellySmoothedRotation.yaw(), tellyAnchorYaw,
            velocity.x * forward.x + velocity.z * forward.z,
            velocity.x * left.x + velocity.z * left.z,
            laneError,
            player.onGround());
        if (!settled || tellySettleDwellTicks > 0) {
            if (settled) tellySettleDwellTicks--;
            clearTellyGroundSteering();
            tellyMotion = TellyMotion.HOLD;

            double settleSpeed = velocity.horizontalDistance();
            Direction overhang = tellyOverhangDirection(player);

            tellySneakThisTick = settleSpeed >= TELLY_SETTLE_SPEED_FLOOR
                || overhang != null || atTellyEdge(player, under);

            if (overhang != null) tryTellySecureFooting(player);
            tellyHoldStrafe = tellyLaneStrafe(player);
            tellySettleHoldTicks++;
            if (tellySettleHoldTicks >= TELLY_SETTLE_TIMEOUT_TICKS) {

                releaseTellyControl();
            }
            return true;
        }
        tellyTurnSettling = false;
        tellyPhase = TellyPhase.FORWARD_DWELL;
        tellyMotion = TellyMotion.FORWARD;
        tellyRecoveryTicks = 0;
        tellyForwardDwellTicks = 0;
        return true;
    }

    private void runTellyGroundedStopHold(LocalPlayer player, BlockPos under) {
        clearTellyGroundSteering();
        tellyMotion = TellyMotion.HOLD;
        tellySneakThisTick = true;
        boolean atEdge = atTellyEdge(player, under);
        Direction overhang = tellyOverhangDirection(player);
        if (atEdge || overhang != null) {
            tellyEdgeHoldTicks++;
            if (tellyEdgeHoldTicks < 40) {

                if (overhang != null && tryTellySecureFooting(player)) return;
                if (atEdge && tryTellyLipSecurePlacement(player)) return;
            }
        }
        double releaseSpeed = atEdge || overhang != null ? 0.02D : 0.06D;
        if (player.onGround() && player.getDeltaMovement().horizontalDistance() < releaseSpeed) {
            releaseTellyControl();
        }
    }

    private boolean runTellyForwardDwell(LocalPlayer player, BlockPos under) {
        clearTellyGroundSteering();
        boolean nearEdge = atTellyEdge(player, under);
        if (tellyStopRequested) {
            tellyMotion = TellyMotion.HOLD;
            tellySneakThisTick = true;
            if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.06D) {
                releaseTellyControl();
            }
            return true;
        }

        tellyForwardDwellTicks = nextTellyForwardDwellTicks(tellyForwardDwellTicks, true);
        if (!tellyForwardDwellComplete(tellyForwardDwellTicks)) {
            tellyMotion = nearEdge ? TellyMotion.HOLD : TellyMotion.FORWARD;
            tellySneakThisTick = nearEdge;
            return true;
        }

        tellyPhase = TellyPhase.RUNNING;
        tellyMotion = TellyMotion.FORWARD;
        tellySneakThisTick = false;
        tellyForwardDwellTicks = 0;
        return false;
    }

    private void beginTellyRunupRecovery(LocalPlayer player, BlockPos support) {
        if (tellyPhase == TellyPhase.RECOVERING || player == null || support == null) return;
        tellyPhase = TellyPhase.RECOVERING;
        tellyMotion = TellyMotion.HOLD;
        tellySneakThisTick = true;
        tellyRecoveryTicks = 0;
        tellyTarget = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
        tellyQueuedBlock = null;
        tellyLastBridge = support.immutable();
        clearTellyGroundSteering();
    }

    private void runTellyRunupRecovery(LocalPlayer player, BlockPos support) {
        if (player == null || support == null) {
            releaseTellyControl();
            return;
        }
        if (tellyStopRequested) {
            tellyMotion = TellyMotion.HOLD;
            tellySneakThisTick = true;
            if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.06D) {
                releaseTellyControl();
            }
            return;
        }

        applyTellyCourseTurn(player, support);

        tellyMotion = TellyMotion.HOLD;
        tellySneakThisTick = true;

        tellyHoldStrafe = tellyLaneStrafe(player);
        tellyRecoveryTicks++;
        if (tellyRecoveryTicks >= 60) {

            releaseTellyControl();
            return;
        }
        if (isTellyLaunchCatchable(player, support)) {
            tellyPhase = TellyPhase.FORWARD_DWELL;
            tellyMotion = TellyMotion.FORWARD;
            tellySneakThisTick = false;
            tellyLineOrigin = laneOrigin(support, player.position(), tellyAnchorYaw);
            tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
            tellyLastBridge = support.immutable();
            tellyBridgeY = support.getY();
            tellyTarget = null;
            tellyRecoveryTicks = 0;
            tellyForwardDwellTicks = 0;
        }
    }

    private void startTellyLaunch(LocalPlayer player, TellyPlacement first) {
        tellyCycleSerial++;

        tellyCycleRises = tellyRiseQueued || tellySpaceHeld;
        tellyRiseQueued = false;
        tellyFinishing = false;
        tellyRaisedBlockPlaced = false;
        tellyRaisedCell = null;
        tellyFlatPlacements = 0;
        tellyWalkOffCatch = false;
        tellyWalkOffGraceTicks = 0;
        tellyTakeoffY = player.getY();
        tellyTakeoffProgress = player.position().dot(tellyForwardVector());
        tellyFailedClicks = 0;
        tellyAirTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyTarget = first;
        tellyPhase = TellyPhase.LAUNCH;
        tellyMotion = TellyMotion.FORWARD;
        clearTellyGroundSteering();
        tellyJumpThisTick = true;
        tellySneakThisTick = false;
    }

    private void runAirborneTelly(LocalPlayer player) {
        tellyAirTicks++;
        if (tellyLastBridge == null) {
            releaseTellyControl();
            return;
        }
        if (tellyFinishing) {

            tellyMotion = TellyMotion.FORWARD;
            tellyTarget = null;
            return;
        }
        if (tellyPhase == TellyPhase.RUNNING || tellyPhase == TellyPhase.FORWARD_DWELL) {
            if (isTellyLandingSupported(player, tellyBridgeY)) {

                tellyMotion = TellyMotion.FORWARD;
                return;
            }

            beginTellyWalkOffCatch(player);
        }

        tellyMotion = TellyMotion.FORWARD;
        ItemStack stack = planningStack();

        if (tellyRaisedBlockPlaced && tellyRaisedCell != null
            && !isSolidSupport(MC.level.getBlockState(tellyRaisedCell), tellyRaisedCell)) {

            tellyRaisedBlockPlaced = false;
            if (tellyRaisedCell.equals(tellyLastBridge)) tellyLastBridge = tellyRaisedCell.below();
            tellyRaisedCell = null;
        }

        int landingBlockY = tellyRaisedBlockPlaced ? tellyBridgeY + 1 : tellyBridgeY;
        boolean hasConfirmedCatchBlock = tellyFlatPlacements > 0;
        boolean descending = player.getDeltaMovement().y < -0.035D;

        boolean landingSecured = hasConfirmedCatchBlock
            && isTellyLandingSupported(player, landingBlockY);
        if (!landingSecured && tellyRaisedBlockPlaced && descending && tellyRiseOvershootCoast(player)) {
            tellyMotion = TellyMotion.HOLD;
        }

        boolean chainCovered = landingSecured
            && tellyLandingRunwayCovered(player, landingBlockY);
        boolean walkOffGrace = tellyWalkOffCatch && tellyWalkOffGraceTicks > 0;
        if (tellyWalkOffGraceTicks > 0) tellyWalkOffGraceTicks--;
        if (!landingSecured && !walkOffGrace && !tellyPlacementQueued && tellyClickCooldown <= 0
            && tellyLandingOffLane(player)) {

            rescueTellyPlacement(player, stack);
        }
        if (!landingSecured && tellyFailedClicks >= 3) {

            abortMissedTelly(player);
            return;
        }
        if (!landingSecured && !walkOffGrace && missedTellyCatchWindow(player, descending)) {

            if (!tellyPlacementQueued && tellyClickCooldown <= 0 && !rescueTellyPlacement(player, stack)) {
                abortMissedTelly(player);
            }
            return;
        }

        if (!isValidBlock(stack)) {
            tellyPhase = TellyPhase.RETURNING;
            return;
        }
        if (chainCovered && (!tellyCycleRises || tellyRaisedBlockPlaced)
            && tellyPhase != TellyPhase.RETURNING) {
            tellyTarget = null;
            tellyPhase = TellyPhase.RETURNING;
            return;
        }
        if (landingSecured && tellyCycleRises && !tellyRaisedBlockPlaced
            && player.getDeltaMovement().y < 0.0D && player.getY() < tellyBridgeY + 2.0D) {

            tellyTarget = null;
            tellyPhase = TellyPhase.RETURNING;
            return;
        }

        if (tellyPhase == TellyPhase.RETURNING && !landingSecured) {
            tellyPhase = TellyPhase.AIMING;
            tellyTarget = null;
        }

        if (tellyPhase == TellyPhase.RETURNING) {

            return;
        }

        if (tellyTarget == null) {
            if (chainCovered) {

                if (!tellyCycleRises || tellyRaisedBlockPlaced) {
                    tellyPhase = TellyPhase.RETURNING;
                }
                return;
            }
            if (tellyRiseStillPossible(player) && tellyRiseSupportCell(player) != null) {

                return;
            }
            tellyTarget = nextFlatTellyPlacement(player, stack);
        }

        if (tellyTarget == null) {
            return;
        }

        tellyPhase = TellyPhase.AIMING;

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) refreshSelectionReset();
            return;
        }
        refreshSelectionReset();
        ItemStack held = player.getItemInHand(hand);
        if (!isValidBlock(held)) return;

        landingBlockY = tellyRaisedBlockPlaced ? tellyBridgeY + 1 : tellyBridgeY;
        hasConfirmedCatchBlock = tellyFlatPlacements > 0;
        landingSecured = hasConfirmedCatchBlock
            && isTellyLandingSupported(player, landingBlockY);
        chainCovered = landingSecured
            && tellyLandingRunwayCovered(player, landingBlockY);
        if (chainCovered
            && (!tellyCycleRises || tellyRaisedBlockPlaced)) {
            tellyPhase = TellyPhase.RETURNING;
            tellyTarget = null;
        }
    }

    private void beginTellyWalkOffCatch(LocalPlayer player) {
        tellyCycleSerial++;
        tellyCycleRises = false;
        tellyRaisedBlockPlaced = false;
        tellyRaisedCell = null;
        tellyFlatPlacements = 0;
        tellyWalkOffCatch = true;
        tellyWalkOffGraceTicks = 3;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
        tellyQueuedBlock = null;
        tellyFailedClicks = 0;
        tellyAirTicks = Math.max(tellyAirTicks, 3);
        tellyBridgeY = tellyLastBridge.getY();
        tellyTakeoffY = tellyBridgeY + 1.0D;
        tellyTakeoffProgress = player.position().dot(tellyForwardVector()) - 1.45D;
        tellyPhase = TellyPhase.LAUNCH;
        tellyMotion = TellyMotion.FORWARD;
        clearTellyGroundSteering();
    }

    public static void beforeHandleKeybinds() {
        if (MC == null || MC.player == null || MC.level == null) return;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold)
            || !scaffold.isEnabled() || !scaffold.isTellyMode() || !scaffold.tellyOwnsInput
            || !scaffold.canRun() || MC.player.onGround()) return;
        scaffold.armTellySilentPlacement();
    }

    private void armTellySilentPlacement() {

        maintainTellyClickPipeline();
        if (tellyPlacementQueued || tellyClickCooldown > 0 || tellyFinishing) return;

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) refreshSelectionReset();
            return;
        }
        refreshSelectionReset();
        ItemStack held = MC.player.getItemInHand(hand);
        if (!isValidBlock(held)) return;

        if (tellyRiseStillPossible(MC.player) && tellyLastBridge != null) {

            BlockPos riseCell = tellyRiseSupportCell(MC.player);
            if (riseCell != null) {
                TellyPlacement rise = raisedTellyPlacement(MC.player, held, riseCell);
                if (rise != null) {
                    TellyPlacement live = liveTellyPlacement(MC.player, rise);
                    if (live != null && !ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) {

                        PlacementTarget aimed = tellyStreamAimedTarget(live.target());
                        if (aimed == null) aimed = tellyBoundedFlickTarget(live.target());
                        tellyTarget = live;
                        adoptTellyPlacementRotation(aimed.rotation());
                        place(aimed, hand, held, false, false);
                        tellyPlacementQueued = true;
                        tellyQueuedBlock = aimed.placedBlock().immutable();
                        tellyClickCooldown = 1;
                        return;
                    }
                }
            }
        }

        if (tellyTarget == null
            && (tellyPhase == TellyPhase.LAUNCH || tellyPhase == TellyPhase.AIMING)
            && !(tellyRiseStillPossible(MC.player) && tellyRiseSupportCell(MC.player) != null)) {
            tellyTarget = nextFlatTellyPlacement(MC.player, held);
        }
        if (tellyTarget == null) return;

        if (tellyShouldDelayFirstClick(MC.player)) return;
        TellyPlacement live = liveTellyPlacement(MC.player, tellyTarget);
        if (live == null) return;
        if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return;

        PlacementTarget aimed = tellyStreamAimedTarget(live.target());
        if (aimed == null) aimed = tellyBoundedFlickTarget(live.target());
        tellyTarget = live;
        adoptTellyPlacementRotation(aimed.rotation());
        place(aimed, hand, held, false, false);
        tellyPlacementQueued = true;
        tellyQueuedBlock = aimed.placedBlock().immutable();
        tellyClickCooldown = 1;
    }

    private boolean missedTellyCatchWindow(LocalPlayer player, boolean descending) {
        if (!descending) return false;

        double targetTop = tellyBridgeY + 1.0D;
        return player.getY() < targetTop + 0.05D
            || player.getY() < tellyTakeoffY - 1.15D
            || tellyAirTicks > 14;
    }

    private boolean tellyLandingOffLane(LocalPlayer player) {
        double catchTop = tellyRaisedBlockPlaced ? tellyBridgeY + 2.0D : tellyBridgeY + 1.0D;
        double feetY = Math.min(catchTop, player.getY());
        Vec3 landing = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        double lane = laneCoordinate(new Vec3(landing.x, player.getY(), landing.z), tellyAnchorYaw);
        return Math.abs(lane - tellyLaneCenter) > 0.4D;
    }

    private boolean rescueTellyPlacement(LocalPlayer player, ItemStack stack) {
        if (tellyPlacementQueued || stack == null) return false;

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) {
                refreshSelectionReset();
                return true;
            }
            return false;
        }
        refreshSelectionReset();
        ItemStack held = player.getItemInHand(hand);
        if (!isValidBlock(held)) return false;

        double catchTop = tellyRaisedBlockPlaced ? tellyBridgeY + 2.0D : tellyBridgeY + 1.0D;
        double feetY = Math.min(catchTop, player.getY());
        Vec3 landing = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        BlockPos cell = tellyLaneCell(BlockPos.containing(landing.x, feetY - 1.0D, landing.z));
        if (isSolidSupport(MC.level.getBlockState(cell), cell)) return false;
        TellyPlacement rescue = rescueTellyPlacementTarget(player, cell);
        if (rescue == null) return false;
        TellyPlacement live = liveTellyPlacement(player, rescue);
        if (live == null || ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return false;

        PlacementTarget aimed = tellyStreamAimedTarget(live.target());
        if (aimed == null) aimed = tellyBoundedFlickTarget(live.target());
        tellyTarget = live;
        adoptTellyPlacementRotation(aimed.rotation());
        place(aimed, hand, held, false, false);
        tellyPlacementQueued = true;
        tellyQueuedBlock = aimed.placedBlock().immutable();
        tellyClickCooldown = 1;
        return true;
    }

    private TellyPlacement rescueTellyPlacementTarget(LocalPlayer player, BlockPos cell) {
        BlockPos below = cell.below();
        if (isSolidSupport(MC.level.getBlockState(below), below)) {
            Vec3 aim = Vec3.atCenterOf(below).add(0.0D, 0.5D, 0.0D);
            PlacementTarget target = new PlacementTarget(
                below.immutable(), cell.immutable(), Direction.UP,
                new BlockHitResult(aim, Direction.UP, below, false),
                AutismRotationUtil.lookingAt(aim, player.getEyePosition()), below.getY() + 0.5D);
            return new TellyPlacement(target, false);
        }

        Direction course = tellyForwardDirection();
        for (Direction direction : new Direction[]{course.getOpposite(), course}) {
            BlockPos neighbor = cell.relative(direction);
            if (!isSolidSupport(MC.level.getBlockState(neighbor), neighbor)) continue;
            Direction face = direction.getOpposite();
            Vec3 aim = tellySideAim(neighbor, face);
            PlacementTarget target = new PlacementTarget(
                neighbor.immutable(), cell.immutable(), face,
                new BlockHitResult(aim, face, neighbor, false),
                AutismRotationUtil.lookingAt(aim, player.getEyePosition()), neighbor.getY());
            return new TellyPlacement(target, false);
        }
        return null;
    }

    private void abortMissedTelly(LocalPlayer player) {

        tellyFinishing = true;
        tellyTarget = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
        tellyQueuedBlock = null;
        tellyPhase = TellyPhase.RETURNING;
        tellyMotion = TellyMotion.FORWARD;
    }

    private void maintainTellyClickPipeline() {
        if (MC == null || MC.player == null || MC.level == null) return;
        int tick = MC.player.tickCount;
        if (tick == tellyPipelineTick) return;
        tellyPipelineTick = tick;
        if (tellyClickCooldown > 0) tellyClickCooldown--;
        confirmTellyPlacement();
    }

    private int tellyPipelineTick = Integer.MIN_VALUE;

    private void confirmTellyPlacement() {
        if (!tellyPlacementQueued || tellyQueuedBlock == null) return;
        tellyPlacementQueued = false;
        boolean secureFooting = tellySecureFootingQueued;
        tellySecureFootingQueued = false;
        BlockPos placed = tellyQueuedBlock;
        tellyQueuedBlock = null;
        if (!isSolidSupport(MC.level.getBlockState(placed), placed)) {
            tellyFailedClicks++;
            return;
        }
        if (secureFooting) {

            tellyFailedClicks = 0;
            return;
        }

        boolean raised = tellyTarget != null
            && tellyTarget.target().placedBlock().equals(placed)
            && tellyTarget.raised();
        tellyLastBridge = placed.immutable();
        if (raised) {
            tellyRaisedBlockPlaced = true;
            tellyRaisedCell = placed.immutable();
        } else {
            tellyFlatPlacements++;
        }
        tellyFailedClicks = 0;
        tellyTarget = null;
    }

    private void finishTellyCycleOnGround(BlockPos under) {
        tellyPhase = TellyPhase.FORWARD_DWELL;
        tellyMotion = TellyMotion.FORWARD;
        tellyAirTicks = 0;
        tellyFlatPlacements = 0;
        tellyFailedClicks = 0;
        tellyForwardDwellTicks = 0;
        tellyCycleRises = false;
        tellyRaisedBlockPlaced = false;
        tellyRaisedCell = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
        tellyWalkOffCatch = false;
        tellyWalkOffGraceTicks = 0;
        tellyQueuedBlock = null;
        tellyTarget = null;
        tellyLastBridge = under.immutable();
        tellyBridgeY = under.getY();
    }

    private boolean atTellyEdge(LocalPlayer player, BlockPos under) {
        Direction direction = tellyForwardDirection();
        BlockPos ahead = under.relative(direction);
        if (isSolidSupport(MC.level.getBlockState(ahead), ahead)) return false;

        double remaining = switch (direction) {
            case EAST -> under.getX() + 1.0D - player.getX();
            case WEST -> player.getX() - under.getX();
            case SOUTH -> under.getZ() + 1.0D - player.getZ();
            case NORTH -> player.getZ() - under.getZ();
            default -> Double.POSITIVE_INFINITY;
        };
        Vec3 forward = tellyForwardVector();
        Vec3 velocity = player.getDeltaMovement();
        double forwardSpeed = Math.max(0.0D, velocity.x * forward.x + velocity.z * forward.z);
        return shouldLaunchTelly(remaining, forwardSpeed);
    }

    static boolean shouldLaunchTelly(double supportRemaining, double forwardSpeed) {
        return supportRemaining <= tellyLaunchPoint(forwardSpeed);
    }

    private static double tellyLaunchPoint(double forwardSpeed) {
        return Mth.clamp(
            0.52D + Math.max(0.0D, forwardSpeed) * 0.12D, 0.52D, 0.64D);
    }

    private BlockPos solidBlockUnder(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int y = Mth.floor(player.getY() - 0.08D);
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int minX = Mth.floor(box.minX + 0.04D);
        int maxX = Mth.floor(box.maxX - 0.04D);
        int minZ = Mth.floor(box.minZ + 0.04D);
        int maxZ = Mth.floor(box.maxZ - 0.04D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isSolidSupport(MC.level.getBlockState(pos), pos)) continue;
                double distance = Vec3.atCenterOf(pos).distanceToSqr(player.position());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private TellyPlacement nextFlatTellyPlacement(LocalPlayer player, ItemStack stack) {
        if (tellyLastBridge == null || tellyLineOrigin == null || !isValidBlock(stack)) return null;
        BlockPos support = tellySolidChainRoot();
        if (support == null) return null;

        Direction direction = tellyForwardDirection();
        for (int i = 0; i < 8; i++) {
            BlockPos next = support.relative(direction);
            if (MC.level.isOutsideBuildHeight(next)) return null;
            BlockState state = MC.level.getBlockState(next);
            if (isSolidSupport(state, next)) {
                support = next;
                continue;
            }
            if (!state.isAir() && !state.canBeReplaced()) return null;

            Vec3 aim = tellySideAim(support, direction);
            PlacementTarget target = new PlacementTarget(
                support.immutable(), next.immutable(), direction,
                new BlockHitResult(aim, direction, support, false),
                AutismRotationUtil.lookingAt(aim, player.getEyePosition()), support.getY());
            tellyLastBridge = support.immutable();
            return new TellyPlacement(target, false);
        }
        return null;
    }

    private TellyPlacement raisedTellyPlacement(LocalPlayer player, ItemStack stack, BlockPos support) {
        if (support == null || !isValidBlock(stack)) return null;
        BlockPos raised = support.above();
        if (MC.level.isOutsideBuildHeight(raised)) return null;
        BlockState state = MC.level.getBlockState(raised);
        if (!state.isAir() && !state.canBeReplaced()) return null;

        Vec3 forward = tellyForwardVector();
        double cross = Math.sin(tellyCycleSerial * 1.731D) * 0.11D;
        Vec3 lateral = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 aim = Vec3.atCenterOf(support)
            .add(lateral.scale(cross))
            .add(0.0D, 0.5D, 0.0D);
        PlacementTarget target = new PlacementTarget(
            support.immutable(), raised.immutable(), Direction.UP,
            new BlockHitResult(aim, Direction.UP, support, false),
            AutismRotationUtil.lookingAt(aim, player.getEyePosition()),
            support.getY() + 0.9D);
        return new TellyPlacement(target, true);
    }

    private Vec3 tellySideAim(BlockPos support, Direction direction) {
        double faceY = Mth.clamp(0.78D - tellyFlatPlacements * 0.11D, 0.28D, 0.78D);
        return Vec3.atCenterOf(support).add(
            direction.getStepX() * 0.5D,
            faceY - 0.5D,
            direction.getStepZ() * 0.5D);
    }

    private TellyPlacement liveTellyPlacement(LocalPlayer player, TellyPlacement placement) {
        PlacementTarget target = placement.target();
        Vec3 eye = player.getEyePosition(1.0F);

        if (target.face().getAxis().isVertical()) {

            if (!tellyRiseCellClear(player.getBoundingBox(), player.getDeltaMovement(), target.placedBlock())) {
                return null;
            }
            AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(
                target.hit().getLocation(), eye);
            BlockHitResult ray = raytrace(rotation, player.blockInteractionRange());
            if (ray == null || !ray.getBlockPos().equals(target.supportBlock())
                || ray.getDirection() != target.face()) {
                return null;
            }
            return new TellyPlacement(new PlacementTarget(
                target.supportBlock(), target.placedBlock(), target.face(), ray, rotation,
                target.minPlacementY()), placement.raised());
        }

        BlockState supportState = MC.level.getBlockState(target.supportBlock());
        VoxelShape shape = supportState.getShape(
            MC.level, target.supportBlock(), CollisionContext.of(player));
        if (shape.isEmpty()) return null;

        Vec3 normal = new Vec3(
            target.face().getStepX(), target.face().getStepY(), target.face().getStepZ());
        Vec3 left = tellyLeftVector();
        double desiredY = Mth.clamp(
            target.supportBlock().getY() + 0.78D - tellyFlatPlacements * 0.11D
                - Math.max(0.0D, tellyTakeoffY - player.getY()) * 0.18D,
            target.supportBlock().getY() + 0.22D,
            target.supportBlock().getY() + 0.80D);
        double reach = player.blockInteractionRange();

        AutismRotationUtil.Rotation current = serverRotation();
        TellyFaceSample best = null;
        Vec3 blockOffset = new Vec3(
            target.supportBlock().getX(), target.supportBlock().getY(), target.supportBlock().getZ());

        for (AABB localBox : shape.toAabbs()) {
            FaceRect face = FaceRect.fromBox(localBox, target.face()).trim(0.12D).offset(blockOffset);
            if (face.area() <= GEOMETRY_EPSILON) continue;
            if (eye.subtract(face.center()).dot(normal) <= TELLY_FACE_VISIBILITY_EPSILON) continue;

            for (double offset : TELLY_FACE_OFFSETS) {
                Vec3 center = face.center()
                    .add(left.scale(offset));
                Vec3 point = new Vec3(
                    Mth.clamp(center.x, face.from().x, face.to().x),
                    Mth.clamp(desiredY, face.from().y, face.to().y),
                    Mth.clamp(center.z, face.from().z, face.to().z));
                if (eye.distanceToSqr(point) > reach * reach + 1.0E-7D) continue;

                AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(point, eye);
                BlockHitResult ray = raytrace(rotation, reach);
                if (ray == null || !ray.getBlockPos().equals(target.supportBlock())
                    || ray.getDirection() != target.face()) continue;

                double cost = rotationAngle(current, rotation)
                    + Math.abs(offset) * 4.0D
                    + eye.distanceTo(point) * 0.015D;
                if (best == null || cost < best.angularCost()) {
                    best = new TellyFaceSample(face, point, rotation, ray, cost);
                }
            }
        }
        if (best == null) return null;

        PlacementTarget live = new PlacementTarget(
            target.supportBlock(), target.placedBlock(), target.face(), best.verifiedHit(),
            best.rotation(), best.worldFace().from().y);
        return new TellyPlacement(live, placement.raised());
    }

    private PlacementTarget tellyStreamAimedTarget(PlacementTarget target) {
        if (tellySmoothedRotation == null) return null;
        BlockHitResult ray = raytrace(tellySmoothedRotation, MC.player.blockInteractionRange());
        if (ray == null || !ray.getBlockPos().equals(target.supportBlock())
            || ray.getDirection() != target.face()) return null;
        return new PlacementTarget(target.supportBlock(), target.placedBlock(), target.face(),
            ray, tellySmoothedRotation, target.minPlacementY());
    }

    private PlacementTarget tellyBoundedFlickTarget(PlacementTarget live) {
        AutismRotationUtil.Rotation from =
            tellySmoothedRotation != null ? tellySmoothedRotation : serverRotation();
        AutismRotationUtil.Rotation stepped = stepTellyRotation(
            from, live.rotation(), TELLY_FLICK_STEP_CAP, AutismRotationUtil.sensitivityGcd());
        BlockHitResult ray = raytrace(stepped, MC.player.blockInteractionRange());
        if (ray != null && ray.getBlockPos().equals(live.supportBlock())
            && ray.getDirection() == live.face()) {
            return new PlacementTarget(live.supportBlock(), live.placedBlock(), live.face(),
                ray, stepped, live.minPlacementY());
        }
        return live;
    }

    private boolean tryTellySecureFooting(LocalPlayer player) {
        if (tellyPlacementQueued) return true;
        if (tellyClickCooldown > 0) return true;
        Direction overhang = tellyOverhangDirection(player);
        if (overhang == null) return false;
        Vec3 pos = player.position();
        BlockPos center = BlockPos.containing(pos.x, pos.y - 0.5D, pos.z);
        BlockPos support = isSolidSupport(MC.level.getBlockState(center), center)
            ? center : center.relative(overhang.getOpposite());
        if (!isSolidSupport(MC.level.getBlockState(support), support)) return false;
        BlockPos targetCell = support.relative(overhang);
        BlockState targetState = MC.level.getBlockState(targetCell);
        if (!targetState.isAir() && !targetState.canBeReplaced()) return false;
        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) {
                refreshSelectionReset();
                return true;
            }
            return false;
        }
        refreshSelectionReset();
        ItemStack held = player.getItemInHand(hand);
        if (!isValidBlock(held)) return false;
        Vec3 aim = tellySideAim(support, overhang);
        AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(aim, player.getEyePosition());
        BlockHitResult ray = raytrace(rotation, player.blockInteractionRange());
        if (ray == null || !ray.getBlockPos().equals(support) || ray.getDirection() != overhang) return false;
        PlacementTarget target = new PlacementTarget(
            support.immutable(), targetCell.immutable(), overhang, ray, rotation, support.getY());
        if (ModuleRegistry.shouldCancelUseExcept(target.hit(), hand, id())) return false;
        PlacementTarget aimed = tellyStreamAimedTarget(target);
        if (aimed == null) aimed = tellyBoundedFlickTarget(target);
        adoptTellyPlacementRotation(aimed.rotation());
        place(aimed, hand, held, false, false);
        tellyPlacementQueued = true;
        tellySecureFootingQueued = true;
        tellyQueuedBlock = aimed.placedBlock().immutable();
        tellyClickCooldown = 1;
        return true;
    }

    private boolean tryTellyLipSecurePlacement(LocalPlayer player) {
        if (tellyPlacementQueued) return true;
        if (tellyClickCooldown > 0) return true;

        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) {
                refreshSelectionReset();
                return true;
            }
            return false;
        }
        refreshSelectionReset();
        ItemStack held = player.getItemInHand(hand);
        if (!isValidBlock(held)) return false;
        if (tellyTarget == null) tellyTarget = nextFlatTellyPlacement(player, held);
        if (tellyTarget == null) return false;
        TellyPlacement live = liveTellyPlacement(player, tellyTarget);
        if (live == null) return false;
        if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return false;
        PlacementTarget aimed = tellyStreamAimedTarget(live.target());
        if (aimed == null) aimed = tellyBoundedFlickTarget(live.target());
        tellyTarget = live;
        adoptTellyPlacementRotation(aimed.rotation());
        place(aimed, hand, held, false, false);
        tellyPlacementQueued = true;
        tellyQueuedBlock = aimed.placedBlock().immutable();
        tellyClickCooldown = 1;
        return true;
    }

    private boolean tellyRiseOvershootCoast(LocalPlayer player) {
        if (tellyRaisedCell == null) return false;
        double feetY = tellyRaisedCell.getY() + 1.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        Vec3 forward = tellyForwardVector();
        double landingProgress = projected.subtract(player.position()).dot(forward);
        double farEdge = Vec3.atCenterOf(tellyRaisedCell).subtract(player.position()).dot(forward)
            + 0.5D - TELLY_SAFE_OVERLAP;
        return landingProgress > farEdge;
    }

    private boolean tellyRiseStillPossible(LocalPlayer player) {
        if (!tellyCycleRises || tellyRaisedBlockPlaced) return false;
        return player.getDeltaMovement().y > 0.0D || player.getY() > tellyBridgeY + 2.0D;
    }

    private BlockPos tellyRiseSupportCell(LocalPlayer player) {
        if (tellyLastBridge == null) return null;
        double feetY = tellyBridgeY + 2.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        BlockPos cell = tellyLaneCell(BlockPos.containing(projected.x, tellyBridgeY, projected.z));
        Direction back = tellyForwardDirection().getOpposite();
        for (int step = 0; step <= 1; step++) {
            BlockPos candidate = step == 0 ? cell : cell.relative(back, step);
            if (MC.level.isOutsideBuildHeight(candidate)) continue;
            if (!isSolidSupport(MC.level.getBlockState(candidate), candidate)) continue;
            BlockState above = MC.level.getBlockState(candidate.above());
            if (above.isAir() || above.canBeReplaced()) return candidate;
        }
        return null;
    }

    private Vec3 tellyEffectiveForward(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        double hx = velocity.x;
        double hz = velocity.z;
        double length = Math.sqrt(hx * hx + hz * hz);
        if (length < 0.05D) return tellyForwardVector();
        return new Vec3(hx / length, 0.0D, hz / length);
    }

    private boolean isTellyLandingSupported(LocalPlayer player, int blockY) {
        double feetY = blockY + 1.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        return tellyLandingSupportedAt(player, blockY, projected, feetY);
    }

    private boolean tellyLandingRunwayCovered(LocalPlayer player, int blockY) {
        double feetY = blockY + 1.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        BlockPos runway = tellyLaneCell(BlockPos.containing(projected.x, blockY, projected.z))
            .relative(tellyForwardDirection());
        return isSolidSupport(MC.level.getBlockState(runway), runway);
    }

    private boolean tellyLandingSupportedAt(LocalPlayer player, int blockY, Vec3 projected, double feetY) {
        AABB moved = player.getBoundingBox().move(
            projected.x - player.getX(),
            feetY - player.getY(),
            projected.z - player.getZ());
        int minX = Mth.floor(moved.minX + 1.0E-4D);
        int maxX = Mth.floor(moved.maxX - 1.0E-4D);
        int minZ = Mth.floor(moved.minZ + 1.0E-4D);
        int maxZ = Mth.floor(moved.maxZ - 1.0E-4D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, blockY, z);
                if (!isSolidSupport(MC.level.getBlockState(pos), pos)) continue;
                if (tellyFootprintOverlaps(moved, pos)) return true;
            }
        }
        return false;
    }

    private boolean wouldTellyBlockCatch(LocalPlayer player, BlockPos block) {
        if (block == null) return false;
        double feetY = block.getY() + 1.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyForwardVector());
        AABB moved = player.getBoundingBox().move(
            projected.x - player.getX(), feetY - player.getY(), projected.z - player.getZ());
        return tellyFootprintOverlaps(moved, block);
    }

    static boolean tellyFootprintOverlaps(AABB footprint, BlockPos block) {
        return tellyFootprintOverlaps(footprint, block, TELLY_SAFE_OVERLAP);
    }

    static boolean tellyFootprintOverlaps(AABB footprint, BlockPos block, double requiredOverlap) {
        if (footprint == null || block == null) return false;
        double overlapX = Math.min(footprint.maxX, block.getX() + 1.0D)
            - Math.max(footprint.minX, block.getX());
        double overlapZ = Math.min(footprint.maxZ, block.getZ() + 1.0D)
            - Math.max(footprint.minZ, block.getZ());
        return overlapX >= requiredOverlap && overlapZ >= requiredOverlap;
    }

    static Vec3 projectTellyLanding(Vec3 position, Vec3 velocity, double feetY) {
        Vec3 projected = position;
        Vec3 motion = velocity;
        for (int tick = 0; tick < 14; tick++) {
            projected = projected.add(motion);
            if (motion.y <= 0.0D && projected.y <= feetY + 0.08D) {
                return new Vec3(projected.x, feetY, projected.z);
            }
            motion = new Vec3(
                motion.x * 0.91D,
                (motion.y - 0.08D) * 0.98D,
                motion.z * 0.91D);
        }
        return new Vec3(projected.x, feetY, projected.z);
    }

    static int tellyTicksUntilCatch(Vec3 position, Vec3 velocity, double feetY) {
        Vec3 projected = position;
        Vec3 motion = velocity;
        for (int tick = 1; tick <= 14; tick++) {
            projected = projected.add(motion);
            if (motion.y <= 0.0D && projected.y <= feetY + 0.08D) return tick;
            motion = new Vec3(
                motion.x * 0.91D,
                (motion.y - 0.08D) * 0.98D,
                motion.z * 0.91D);
        }
        return 14;
    }

    static boolean tellyLateFlickBudgetAllows(int ticksLeft, int blocksNeeded, double nextTickFaceDistance,
                                              double reach) {

        if (nextTickFaceDistance > reach - 0.30D) return false;
        return ticksLeft > blocksNeeded + 1;
    }

    static Vec3 projectTellyLandingWithInput(Vec3 position, Vec3 velocity, double feetY, Vec3 forward) {
        Vec3 projected = position;
        Vec3 motion = velocity;
        Vec3 airControl = forward == null || forward.horizontalDistanceSqr() <= 1.0E-10D
            ? Vec3.ZERO : new Vec3(forward.x, 0.0D, forward.z).normalize().scale(TELLY_AIR_CONTROL);
        for (int tick = 0; tick < 14; tick++) {
            motion = motion.add(airControl);
            projected = projected.add(motion);
            if (motion.y <= 0.0D && projected.y <= feetY + 0.08D) {
                return new Vec3(projected.x, feetY, projected.z);
            }
            motion = new Vec3(
                motion.x * 0.91D,
                (motion.y - 0.08D) * 0.98D,
                motion.z * 0.91D);
        }
        return new Vec3(projected.x, feetY, projected.z);
    }

    private Vec3 predictedTellyLaunchVelocity(LocalPlayer player) {

        double groundAcceleration = Math.max(0.0D, player.getSpeed()) * 0.98D;
        return predictedTellyGroundLaunch(
            player.getDeltaMovement(), tellyAnchorYaw, groundAcceleration,
            willTellySprintOnLaunch(player));
    }

    private boolean willTellySprintOnLaunch(LocalPlayer player) {

        return player.isSprinting()
            || player.canSprint()
            && !player.isMovingSlowly()
            && !player.isUsingItem()
            && !player.isFallFlying();
    }

    static Vec3 predictedTellyGroundLaunch(
        Vec3 velocity, float visibleYawDegrees, double groundAcceleration, boolean sprinting
    ) {
        if (velocity == null) velocity = Vec3.ZERO;
        float visibleYaw = visibleYawDegrees * Mth.DEG_TO_RAD;
        Vec3 jumpDirection = new Vec3(
            -Mth.sin(visibleYaw), 0.0D, Mth.cos(visibleYaw));
        double sprintJumpBoost = sprinting ? 0.2D : 0.0D;
        double visibleForwardImpulse = sprintJumpBoost + groundAcceleration;
        return new Vec3(
            velocity.x + jumpDirection.x * visibleForwardImpulse,
            Math.max(velocity.y, 0.42D),
            velocity.z + jumpDirection.z * visibleForwardImpulse);
    }

    private boolean isTellyLaunchCatchable(LocalPlayer player, BlockPos support) {
        if (player == null || support == null || MC.level == null) return false;
        Vec3 launchVelocity = predictedTellyLaunchVelocity(player);
        double launchDrag = MC.level.getBlockState(support).getBlock().getFriction() * 0.91D;
        Vec3 forward = tellyForwardVector();
        Vec3 landing = projectTellyLaunchLanding(
            player.position(), launchVelocity, player.getY(), forward, launchDrag);
        Direction direction = tellyForwardDirection();
        BlockPos catchBlock = BlockPos.containing(landing.x, support.getY(), landing.z);
        if (isSolidSupport(MC.level.getBlockState(catchBlock), catchBlock)) {

            return true;
        }
        int steps = (catchBlock.getX() - support.getX()) * direction.getStepX()
            + (catchBlock.getZ() - support.getZ()) * direction.getStepZ();
        if (steps < 1 || steps > 6) return false;

        for (int step = 1; step <= steps; step++) {
            BlockPos cell = support.relative(direction, step);
            if (MC.level.isOutsideBuildHeight(cell)) {
                return false;
            }
            BlockState state = MC.level.getBlockState(cell);
            if (!isSolidSupport(state, cell) && !state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }

        double feetY = support.getY() + 1.0D;
        AABB landingBox = player.getBoundingBox().move(
            landing.x - player.getX(),
            feetY - player.getY(),
            landing.z - player.getZ());

        return tellyFootprintOverlaps(landingBox, catchBlock, 0.05D);
    }

    private boolean isTellyRiseLaunchCatchable(LocalPlayer player, BlockPos support) {

        if (!isTellyLaunchCatchable(player, support)) return false;

        return apexReachesHeight(player.position(), predictedTellyLaunchVelocity(player),
            support.getY() + 2.0D);
    }

    private static boolean apexReachesHeight(Vec3 position, Vec3 launchVelocity, double targetFeetY) {
        double y = position.y + launchVelocity.y;
        if (y >= targetFeetY) return true;
        double motionY = (launchVelocity.y - 0.08D) * 0.98D;
        for (int tick = 1; tick < 14; tick++) {
            y += motionY;
            if (y >= targetFeetY) return true;
            if (motionY <= 0.0D) return false;
            motionY = (motionY - 0.08D) * 0.98D;
        }
        return false;
    }

    static boolean requiresTellyRunupRecovery(boolean projectedLandingCatchable) {
        return !projectedLandingCatchable;
    }

    static TellyLandingTransition tellyLandingTransition(boolean imminentEdge) {
        return imminentEdge ? TellyLandingTransition.CHAIN : TellyLandingTransition.DWELL;
    }

    private static int requiredTellyPlacements(Vec3 position, Vec3 velocity, double feetY,
                                               Vec3 forward, BlockPos support, double launchDrag) {
        if (position == null || velocity == null || support == null) return 1;
        Vec3 landing = projectTellyLaunchLanding(
            position, velocity, feetY, forward, launchDrag);
        return requiredTellyBlocksToLanding(landing, forward, support);
    }

    static int requiredTellyBlocksToLanding(Vec3 landing, Vec3 forward, BlockPos support) {
        if (landing == null || forward == null || support == null) return 1;
        int landingX = Mth.floor(landing.x);
        int landingZ = Mth.floor(landing.z);
        int gridDistance;
        if (Math.abs(forward.x) >= Math.abs(forward.z)) {
            gridDistance = (int) Math.round((landingX - support.getX()) * Math.signum(forward.x));
        } else {
            gridDistance = (int) Math.round((landingZ - support.getZ()) * Math.signum(forward.z));
        }
        return Mth.clamp(gridDistance, 1, 6);
    }

    static Vec3 projectTellyLaunchLanding(Vec3 position, Vec3 launchVelocity,
                                          double feetY, Vec3 forward) {
        return projectTellyLaunchLanding(position, launchVelocity, feetY, forward, 0.546D);
    }

    static Vec3 projectTellyLaunchLanding(Vec3 position, Vec3 launchVelocity,
                                          double feetY, Vec3 forward, double launchDrag) {
        if (position == null || launchVelocity == null) return position;
        Vec3 projected = position.add(launchVelocity);
        double horizontalDrag = Mth.clamp(launchDrag, 0.0D, 1.2D);
        Vec3 motion = new Vec3(
            launchVelocity.x * horizontalDrag,
            (launchVelocity.y - 0.08D) * 0.98D,
            launchVelocity.z * horizontalDrag);
        Vec3 airControl = forward == null || forward.horizontalDistanceSqr() <= 1.0E-10D
            ? Vec3.ZERO : new Vec3(forward.x, 0.0D, forward.z).normalize().scale(TELLY_AIR_CONTROL);
        for (int tick = 1; tick < 14; tick++) {
            motion = motion.add(airControl);
            projected = projected.add(motion);
            if (motion.y <= 0.0D && projected.y <= feetY + 0.08D) {
                return new Vec3(projected.x, feetY, projected.z);
            }
            motion = new Vec3(
                motion.x * 0.91D,
                (motion.y - 0.08D) * 0.98D,
                motion.z * 0.91D);
        }
        return new Vec3(projected.x, feetY, projected.z);
    }

    static float snapTellyYaw(float yaw) {
        return Mth.wrapDegrees(Math.round(Mth.wrapDegrees(yaw) / 90.0F) * 90.0F);
    }

    static Direction tellyOverhangDirection(double fracX, double fracZ,
                                            boolean northVoid, boolean southVoid,
                                            boolean westVoid, boolean eastVoid) {
        double half = 0.3D;
        double best = 0.05D;
        Direction result = null;
        double north = half - fracZ;
        if (northVoid && north > best) { best = north; result = Direction.NORTH; }
        double south = fracZ + half - 1.0D;
        if (southVoid && south > best) { best = south; result = Direction.SOUTH; }
        double west = half - fracX;
        if (westVoid && west > best) { best = west; result = Direction.WEST; }
        double east = fracX + half - 1.0D;
        if (eastVoid && east > best) { result = Direction.EAST; }
        return result;
    }

    private Direction tellyOverhangDirection(LocalPlayer player) {
        Vec3 pos = player.position();
        BlockPos cell = BlockPos.containing(pos.x, pos.y - 0.5D, pos.z);
        if (!isSolidSupport(MC.level.getBlockState(cell), cell)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = cell.relative(direction);
                if (isSolidSupport(MC.level.getBlockState(neighbor), neighbor)) {
                    return direction.getOpposite();
                }
            }
            return null;
        }
        double fracX = pos.x - Math.floor(pos.x);
        double fracZ = pos.z - Math.floor(pos.z);
        return tellyOverhangDirection(fracX, fracZ,
            !isSolidSupport(MC.level.getBlockState(cell.north()), cell.north()),
            !isSolidSupport(MC.level.getBlockState(cell.south()), cell.south()),
            !isSolidSupport(MC.level.getBlockState(cell.west()), cell.west()),
            !isSolidSupport(MC.level.getBlockState(cell.east()), cell.east()));
    }

    private BlockPos tellyLaneCell(BlockPos raw) {
        if (tellyLastBridge == null) return raw;
        return tellyForwardDirection().getAxis() == Direction.Axis.Z
            ? new BlockPos(tellyLastBridge.getX(), raw.getY(), raw.getZ())
            : new BlockPos(raw.getX(), raw.getY(), tellyLastBridge.getZ());
    }

    private BlockPos tellySolidChainRoot() {
        if (tellyLastBridge == null) return null;
        if (isSolidSupport(MC.level.getBlockState(tellyLastBridge), tellyLastBridge)) return tellyLastBridge;
        Direction back = tellyForwardDirection().getOpposite();
        for (int step = 1; step <= 3; step++) {
            BlockPos candidate = tellyLastBridge.relative(back, step);
            if (isSolidSupport(MC.level.getBlockState(candidate), candidate)) {
                tellyLastBridge = candidate.immutable();
                return tellyLastBridge;
            }
        }
        return null;
    }

    static boolean tellyRiseCellClear(AABB currentBox, Vec3 velocity, BlockPos cell) {
        AABB cellBox = new AABB(cell);
        return !currentBox.intersects(cellBox)
            && !currentBox.move(-velocity.x, -velocity.y, -velocity.z).intersects(cellBox);
    }

    static AutismRotationUtil.Rotation stepTellyRotation(
        AutismRotationUtil.Rotation current, AutismRotationUtil.Rotation goal,
        float stepCap, double gcd) {
        if (current == null) return goal;
        if (goal == null) return current;
        AutismRotationUtil.Rotation stepped = AutismRotationUtil.towardsLinear(current, goal, stepCap, stepCap);
        if (gcd <= 0.0D) return stepped;
        float yawDiff = AutismRotationUtil.angleDifference(stepped.yaw(), current.yaw());
        float pitchDiff = AutismRotationUtil.angleDifference(stepped.pitch(), current.pitch());
        float yaw = current.yaw() + (float) (Math.round(yawDiff / gcd) * gcd);
        float pitch = current.pitch() + (float) (Math.round(pitchDiff / gcd) * gcd);
        return new AutismRotationUtil.Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    static boolean tellyTurnSettled(float smoothedYaw, float anchorYaw,
                                    double velAlongCourse, double velCrossCourse,
                                    double laneError, boolean onGround) {
        if (!onGround) return false;
        if (Math.abs(AutismRotationUtil.angleDifference(anchorYaw, smoothedYaw)) > TELLY_SETTLE_YAW_EPSILON) {
            return false;
        }
        if (Math.abs(laneError) > TELLY_SETTLE_LANE_EPSILON) return false;
        double speed = Math.sqrt(velAlongCourse * velAlongCourse + velCrossCourse * velCrossCourse);
        if (speed < TELLY_SETTLE_SPEED_FLOOR) return true;
        if (velAlongCourse <= 0.0D) return false;
        double angle = Math.toDegrees(Math.atan2(Math.abs(velCrossCourse), velAlongCourse));
        return angle <= TELLY_SETTLE_VELOCITY_ANGLE;
    }

    static float retainTellyCourseYaw(boolean latched, float retainedYaw, float measuredTravelYaw) {
        if (latched && Float.isFinite(retainedYaw)) return Mth.wrapDegrees(retainedYaw);
        return snapTellyYaw(measuredTravelYaw);
    }

    static boolean shouldQueueTellyRise(
        boolean physicalForward,
        boolean physicalSpace,
        boolean physicalSpaceWasDown,
        boolean ownsInput
    ) {
        if (!physicalForward || !physicalSpace) return false;
        return !physicalSpaceWasDown || !ownsInput;
    }

    static boolean shouldRestoreTellyCourseOnGround(TellyPhase phase) {
        return phase != null && phase != TellyPhase.IDLE;
    }

    private Vec3 tellyForwardVector() {
        float radians = tellyAnchorYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
    }

    private Vec3 tellyLeftVector() {
        Vec3 forward = tellyForwardVector();
        return new Vec3(forward.z, 0.0D, -forward.x);
    }

    private Direction tellyForwardDirection() {
        int quadrant = Math.floorMod(Math.round(tellyAnchorYaw / 90.0F), 4);
        return switch (quadrant) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    static Vec3 laneOrigin(BlockPos support, Vec3 playerPosition, float yaw) {
        boolean alongZ = Math.floorMod(Math.round(yaw / 90.0F), 2) == 0;
        if (alongZ) {
            return new Vec3(support.getX() + 0.5D, playerPosition.y, playerPosition.z);
        }
        return new Vec3(playerPosition.x, playerPosition.y, support.getZ() + 0.5D);
    }

    private static double laneCoordinate(Vec3 position, float yaw) {
        float radians = yaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(radians), 0.0D, Mth.sin(radians));
        return position.dot(left);
    }

    private AutismRotationUtil.Rotation tellyForwardRotation() {
        return new AutismRotationUtil.Rotation(tellyAnchorYaw, tellyForwardPitch);
    }

    private AutismRotationUtil.Rotation tellyGroundSteeringRotation() {
        return new AutismRotationUtil.Rotation(
            Mth.wrapDegrees(tellyAnchorYaw + tellyGroundSteerOffset), tellyForwardPitch);
    }

    private AutismRotationUtil.Rotation advanceTellyRotationStream(LocalPlayer player) {
        if (tellyRotationHeldForPlacement) {

            tellyRotationHeldForPlacement = false;
            if (tellySmoothedRotation != null) return tellySmoothedRotation;
        }
        AutismRotationUtil.Rotation goal = selectTellyRotationGoal(player);
        AutismRotationUtil.Rotation from = tellySmoothedRotation != null ? tellySmoothedRotation : serverRotation();
        float cap = player.onGround() ? TELLY_ROTATION_STEP : TELLY_FLICK_STEP_CAP;
        if (tellyReturnFlickPending) {

            tellyReturnFlickPending = false;
            cap = TELLY_FLICK_STEP_CAP;
        }
        tellySmoothedRotation = stepTellyRotation(from, goal, cap, AutismRotationUtil.sensitivityGcd());
        return tellySmoothedRotation;
    }

    private void adoptTellyPlacementRotation(AutismRotationUtil.Rotation rotation) {
        tellySmoothedRotation = rotation;
        grimSilentRotation = rotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        tellyRotationHeldForPlacement = true;

        tellyReturnFlickPending = true;
    }

    private AutismRotationUtil.Rotation selectTellyRotationGoal(LocalPlayer player) {
        if (player.onGround()) {
            return tellyGroundSteeringActive ? tellyGroundSteeringRotation() : tellyForwardRotation();
        }
        boolean aimingPhase = !tellyFinishing
            && (tellyPhase == TellyPhase.LAUNCH || tellyPhase == TellyPhase.AIMING);
        if (aimingPhase && tellyTarget != null && !tellyShouldDelayFirstClick(player)) {
            TellyPlacement live = liveTellyPlacement(player, tellyTarget);
            if (live != null) return live.target().rotation();
        }

        if (aimingPhase && tellyCycleRises && !tellyRaisedBlockPlaced
            && tellySmoothedRotation != null) {
            return tellySmoothedRotation;
        }
        return tellyForwardRotation();
    }

    private boolean tellyStreamAlignedForLaunch() {
        return tellySmoothedRotation != null
            && Math.abs(AutismRotationUtil.angleDifference(
                tellyAnchorYaw, tellySmoothedRotation.yaw())) <= TELLY_SETTLE_YAW_EPSILON;
    }

    private boolean tellyShouldDelayFirstClick(LocalPlayer player) {
        if (tellyFlatPlacements > 0 || tellyWalkOffCatch || tellyFinishing || tellyTarget == null) {
            return false;
        }
        Vec3 velocity = player.getDeltaMovement();

        boolean risePending = tellyCycleRises && !tellyRaisedBlockPlaced;
        double catchFeetY = tellyBridgeY + (risePending ? 2.0D : 1.0D);
        int ticksLeft = tellyTicksUntilCatch(player.position(), velocity, catchFeetY);
        Vec3 landing = projectTellyLandingWithInput(
            player.position(), velocity, catchFeetY, tellyEffectiveForward(player));

        int blocksNeeded = requiredTellyBlocksToLanding(landing, tellyForwardVector(), tellyLastBridge) + 1;
        double nextTickFaceDistance = player.getEyePosition().add(velocity)
            .distanceTo(tellyTarget.target().hit().getLocation());
        return tellyLateFlickBudgetAllows(
            ticksLeft, blocksNeeded, nextTickFaceDistance, player.blockInteractionRange());
    }

    private boolean tellyFlickBackForLaunch() {
        if (tellyStreamAlignedForLaunch()) return true;
        tellySmoothedRotation = stepTellyRotation(
            tellySmoothedRotation != null ? tellySmoothedRotation : serverRotation(),
            tellyForwardRotation(), TELLY_FLICK_STEP_CAP, AutismRotationUtil.sensitivityGcd());
        grimSilentRotation = tellySmoothedRotation;
        return tellyStreamAlignedForLaunch();
    }

    static int nextTellyForwardDwellTicks(int currentTicks, boolean forwardAligned) {
        if (!forwardAligned) return 0;
        return Math.min(TELLY_FORWARD_DWELL_TICKS, Math.max(0, currentTicks) + 1);
    }

    static boolean tellyForwardDwellComplete(int alignedTicks) {
        return alignedTicks >= TELLY_FORWARD_DWELL_TICKS;
    }

    private static boolean physicallyDown(net.minecraft.client.KeyMapping mapping) {
        return mapping != null && AutismKeyMappingBridge.of(mapping).autism$isActuallyDown();
    }

    private void resetTellyState() {
        grimRotationResetTicks = 0;
        grimSilentRotation = null;
        tellyPhase = TellyPhase.IDLE;
        tellyMotion = TellyMotion.RELEASED;
        tellyOwnsInput = false;
        tellyStopRequested = false;
        tellyJumpThisTick = false;
        tellySneakThisTick = false;
        tellyPhysicalSpaceWasDown = false;
        tellyRiseQueued = false;
        tellySpaceHeld = false;
        tellyFinishing = false;
        tellyCycleRises = false;
        tellyRaisedBlockPlaced = false;
        tellyRaisedCell = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
        tellyWalkOffCatch = false;
        tellyWalkOffGraceTicks = 0;
        tellyClickCooldown = 0;
        tellyAirTicks = 0;
        tellyFlatPlacements = 0;
        tellyFailedClicks = 0;
        tellyForwardDwellTicks = 0;
        tellyBridgeY = 0;
        tellyTakeoffY = 0.0D;
        tellyTakeoffProgress = 0.0D;
        tellyAnchorYaw = 0.0F;
        tellyForwardPitch = 0.0F;
        tellyLaneCenter = 0.0D;
        tellyRecoveryTicks = 0;
        tellyCourseLatched = false;
        tellyCourseDeviationTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
        tellySmoothedRotation = null;
        tellyTurnSettling = false;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = 0;
        tellyRotationHeldForPlacement = false;
        tellyReturnFlickPending = false;
        tellyHoldWatchdogTicks = 0;
        tellyLastBridge = null;
        tellyQueuedBlock = null;
        tellyLineOrigin = null;
        tellyTarget = null;
    }

    private void releaseTellyControl() {
        grimRotationResetTicks = 0;
        grimSilentRotation = null;
        tellyPhase = TellyPhase.IDLE;
        tellyMotion = TellyMotion.RELEASED;
        tellyOwnsInput = false;
        tellyStopRequested = false;
        tellyJumpThisTick = false;
        tellySneakThisTick = false;
        tellyAirTicks = 0;
        tellyForwardDwellTicks = 0;
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
        tellyWalkOffCatch = false;
        tellyWalkOffGraceTicks = 0;
        tellyRecoveryTicks = 0;
        tellyRiseQueued = false;
        tellyFinishing = false;
        tellyCourseDeviationTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellySmoothedRotation = null;
        tellyTurnSettling = false;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = 0;
        tellyRotationHeldForPlacement = false;
        tellyReturnFlickPending = false;
        tellyHoldWatchdogTicks = 0;
        tellyTarget = null;
        tellyQueuedBlock = null;
        tellyPlacementQueued = false;
        tellySecureFootingQueued = false;
    }

    @Override
    public void tick() {

    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()) {
            AutismRotationUtil.Rotation base = serverRotation();
            serverRotation = new AutismRotationUtil.Rotation(
                movement.getYRot(base.yaw()), movement.getXRot(base.pitch()));
        }
        return false;
    }

    private boolean canRun() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.gameMode != null
            && MC.getConnection() != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !MC.player.isSpectator()
            && !MC.player.isHandsBusy()
            && !PackHideState.isActive()
            && !PackFreecamState.isActive()

            && !autismclient.util.AutismRemoteView.isActive()
            && !BuiltinModules.ownsManualFastExp()
            && !MultiPilot.isActive()
            && !PacketTeleportController.ownsMainMovement()
            && !MacroExecutor.isRunning();
    }

    public static boolean ownsTellyInput() {
        Module module = ModuleRegistry.get("scaffold");
        return module instanceof ScaffoldModule scaffold
            && scaffold.isEnabled()
            && scaffold.isTellyMode()
            && scaffold.tellyOwnsInput;
    }

    public static boolean reservesTellyInput() {
        Module module = ModuleRegistry.get("scaffold");
        return module instanceof ScaffoldModule scaffold
            && scaffold.isEnabled()
            && scaffold.isTellyMode()
            && (scaffold.tellyOwnsInput
                || MC != null && MC.options != null && physicallyDown(MC.options.keyUp));
    }

    public static Input modifyMovementInput(ClientInput source, Input original) {
        if (original == null || MC == null || MC.player == null || MC.player.input != source) return original;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()) return original;
        if (!scaffold.canRun()) return original;
        if (scaffold.isTellyMode()) {

            Input authored = scaffold.tellyMovementInput(original);
            return scaffold.tellyOwnsInput
                ? scaffold.transformTellyAuthoredInput(authored)
                : scaffold.transformSilentMovementInput(authored);
        }

        scaffold.currentMovementLine = hasDirectionalInput(original)
            ? scaffold.buildMovementLine(original) : null;
        boolean stabilize = scaffold.isGrimFamily() || scaffold.bool("stabilize-movement");
        Input adjusted = stabilize ? scaffold.stabilizeMovementInput(original) : original;

        boolean sneakOverride = false;
        boolean sneakValue = false;
        if (scaffold.isVulcanMode()) {
            if (scaffold.vulcanSneakTicks > 0) {
                scaffold.vulcanSneakTicks--;
                scaffold.vulcanSneakReleased = false;
                sneakOverride = true;
                sneakValue = true;
            } else if (!scaffold.vulcanSneakReleased) {
                scaffold.vulcanSneakReleased = true;
                sneakOverride = true;
                sneakValue = false;
            } else if (scaffold.shouldSneakAtEdge()) {
                sneakOverride = true;
                sneakValue = true;
            }
        }
        if (sneakOverride) {
            adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(), adjusted.right(),
                adjusted.jump(), sneakValue, adjusted.sprint());
        } else if (!scaffold.isVulcanMode() && scaffold.isGrimFamily() && !adjusted.shift()
            && scaffold.shouldSneakAtEdge()) {

            adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(), adjusted.right(),
                adjusted.jump(), true, adjusted.sprint());
        }

        if (scaffold.isVulcanMode() && scaffold.predictFallRisk() == FallRisk.IMMINENT && !adjusted.shift()) {
            adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(), adjusted.right(),
                adjusted.jump(), true, adjusted.sprint());
        }
        return scaffold.transformSilentMovementInput(adjusted);
    }

    private Input tellyMovementInput(Input original) {
        if (!tellyOwnsInput || tellyMotion == TellyMotion.RELEASED) return original;

        if (tellyMotion == TellyMotion.HOLD) {
            boolean[] strafe = tellyHoldStrafe != null ? tellyHoldStrafe : TELLY_NO_STRAFE;
            tellyHoldStrafe = null;
            return new Input(false, false, strafe[0], strafe[1],
                tellyJumpThisTick, tellySneakThisTick, false);
        }

        if (MC.player != null && MC.player.onGround()
            && usesTellyGroundWOnly(tellyPhase)) {

            return tellyGroundForwardInput(tellyJumpThisTick, tellySneakThisTick);
        }

        boolean[] strafe = tellyLaneStrafe(MC.player);
        return new Input(true, false, strafe[0], strafe[1],
            tellyJumpThisTick, tellySneakThisTick, true);
    }

    private static final boolean[] TELLY_NO_STRAFE = {false, false};

    private boolean[] tellyHoldStrafe;

    private boolean[] tellyLaneStrafe(LocalPlayer player) {
        if (player == null || tellyLastBridge == null) return TELLY_NO_STRAFE;
        double error = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);

        if (Math.abs(error) <= 0.15D || Math.abs(error) > 0.55D) return TELLY_NO_STRAFE;
        return error > 0 ? new boolean[]{true, false} : new boolean[]{false, true};
    }

    static Input tellyGroundForwardInput(boolean jump, boolean sneak) {
        return new Input(true, false, false, false, jump, sneak, true);
    }

    static boolean usesTellyGroundWOnly(TellyPhase phase) {
        return phase == TellyPhase.RUNNING;
    }

    private void updateTellyGroundSteering(LocalPlayer player, BlockPos support) {
        if (player == null || support == null || !player.onGround()
            || tellyPhase != TellyPhase.RUNNING) {
            clearTellyGroundSteering();
            return;
        }

        Vec3 left = tellyLeftVector();
        double error = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
        double lateralVelocity = player.getDeltaMovement().dot(left);
        BlockState supportState = MC.level.getBlockState(support);
        double drag = supportState.getBlock().getFriction() * 0.91D;
        double groundAcceleration = Math.max(0.025D, player.getSpeed() * 0.98D);
        double forwardSpeed = Math.max(0.0D, player.getDeltaMovement().dot(tellyForwardVector()));
        double runwayRemaining = tellyRunwayRemaining(player, support);
        double launchPoint = tellyLaunchPoint(forwardSpeed);
        boolean returnToCourse = runwayRemaining <= tellySteeringReturnDistance(
            launchPoint, forwardSpeed, tellyGroundSteerOffset);

        TellyGroundSteeringState next = nextTellyGroundSteering(
            tellyGroundSteeringActive,
            tellyGroundSteerOffset,
            error,
            lateralVelocity,
            groundAcceleration,
            drag,
            returnToCourse);
        tellyGroundSteeringActive = next.active();
        tellyGroundSteerOffset = next.offsetDegrees();
    }

    private double tellyRunwayRemaining(LocalPlayer player, BlockPos support) {
        Direction direction = tellyForwardDirection();
        double remaining = switch (direction) {
            case EAST -> support.getX() + 1.0D - player.getX();
            case WEST -> player.getX() - support.getX();
            case SOUTH -> support.getZ() + 1.0D - player.getZ();
            case NORTH -> player.getZ() - support.getZ();
            default -> Double.POSITIVE_INFINITY;
        };
        for (int step = 1; step <= 8; step++) {
            BlockPos ahead = support.relative(direction, step);
            if (!isSolidSupport(MC.level.getBlockState(ahead), ahead)) {
                return remaining + step - 1;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    static TellyGroundSteeringState nextTellyGroundSteering(
        boolean active,
        float currentOffset,
        double laneError,
        double lateralVelocity,
        double groundAcceleration,
        double drag,
        boolean returnToCourse
    ) {
        boolean nextActive = active;
        double predictedError = laneError - lateralVelocity * TELLY_LANE_PREDICT_TICKS;

        if (Math.abs(laneError) > 0.55D) {
            nextActive = false;
        } else if (returnToCourse) {
            nextActive = false;
        } else if (nextActive) {
            if (Math.abs(laneError) <= TELLY_LANE_EXIT
                && Math.abs(lateralVelocity) <= TELLY_LANE_VELOCITY_EXIT) {
                nextActive = false;
            }
        } else if (Math.abs(laneError) > TELLY_LANE_ENTER
            || Math.abs(predictedError) > TELLY_LANE_PREDICT_ENTER) {
            nextActive = true;
        }

        float targetOffset = 0.0F;
        if (nextActive) {
            double safeDrag = Mth.clamp(drag, 0.20D, 0.99D);
            double safeAcceleration = Math.max(0.025D, Math.abs(groundAcceleration));
            double desiredLateralVelocity = Mth.clamp(
                laneError * TELLY_LANE_VELOCITY_GAIN,
                -TELLY_LANE_MAX_VELOCITY,
                TELLY_LANE_MAX_VELOCITY);
            double requiredLateralAcceleration =
                desiredLateralVelocity / safeDrag - lateralVelocity;
            double steerProgress = Mth.clamp(
                (Math.abs(laneError) - TELLY_LANE_ENTER) / (0.24D - TELLY_LANE_ENTER),
                0.0D,
                1.0D);
            double maxSteer = Mth.lerp(
                steerProgress, TELLY_LANE_MIN_STEER, TELLY_LANE_MAX_STEER);
            double maximumRatio = Math.sin(maxSteer * Mth.DEG_TO_RAD);
            double steeringRatio = Mth.clamp(
                requiredLateralAcceleration / safeAcceleration,
                -maximumRatio,
                maximumRatio);

            targetOffset = (float) -Math.toDegrees(Math.asin(steeringRatio));
        }

        boolean movingOutward = targetOffset != 0.0F
            && (currentOffset == 0.0F
                || Math.signum(targetOffset) == Math.signum(currentOffset)
                && Math.abs(targetOffset) > Math.abs(currentOffset));
        float maximumStep = movingOutward
            ? TELLY_LANE_OUTWARD_SLEW
            : TELLY_LANE_RETURN_SLEW;
        float nextOffset = approachTellyAngle(currentOffset, targetOffset, maximumStep);
        if (!nextActive && Math.abs(nextOffset) < 0.05F) nextOffset = 0.0F;
        return new TellyGroundSteeringState(nextActive, nextOffset);
    }

    static double tellySteeringReturnDistance(
        double launchPoint, double forwardSpeed, float steeringOffset
    ) {
        int returnTicks = Math.max(
            1, Mth.ceil(Math.abs(steeringOffset) / TELLY_LANE_RETURN_SLEW));
        return launchPoint
            + Math.max(0.0D, forwardSpeed) * (returnTicks + 1)
            + TELLY_LANE_RETURN_MARGIN;
    }

    private static float approachTellyAngle(float current, float target, float maximumStep) {
        float difference = target - current;
        if (Math.abs(difference) <= maximumStep) return target;
        return current + Math.copySign(maximumStep, difference);
    }

    private void clearTellyGroundSteering() {
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
    }

    public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
        if (entity == null || MC == null || entity != MC.player) return vanillaYaw;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.usesSilentRotationPath()
            || !scaffold.canRun()) return vanillaYaw;
        AutismRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
        return rotation == null || scaffold.grimRotationResetTicks <= 0 ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
        AutismRotationUtil.Rotation rotation = activeGrimRotation(player);
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeGrimRotation(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
        AutismRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
        if (rotation == null) return vanillaImpulse;
        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, vanillaImpulse.y, Mth.cos(yaw) * 0.2F);
    }

    public static float correctedFallFlyingPitch(LivingEntity entity, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 correctedFallFlyingLook(LivingEntity entity, Vec3 vanillaLook) {
        AutismRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
        return rotation == null ? vanillaLook
            : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    private static AutismRotationUtil.Rotation activeGrimRotationValue(LivingEntity entity) {
        if (entity == null || MC == null || entity != MC.player) return null;
        if (BuiltinModules.ownsManualFastExp()) return null;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.usesSilentRotationPath() || !scaffold.canRun()) return null;
        AutismRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
        return rotation == null || scaffold.grimRotationResetTicks <= 0 ? null : rotation;
    }

    private static AutismRotationUtil.Rotation activeGrimRotation(LocalPlayer player) {
        if (player == null || MC == null || player != MC.player) return null;

        if (BuiltinModules.ownsManualFastExp()) return null;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.usesSilentRotationPath() || !scaffold.canRun()
            || scaffold.grimRotationResetTicks <= 0) return null;
        return scaffold.grimSilentRotation;
    }

    static boolean hasActiveSilentMovementRotation() {
        return MC != null && activeGrimRotation(MC.player) != null;
    }

    public static AutismRotationUtil.Rotation activeOutgoingRotation() {
        return MC == null ? null : activeGrimRotation(MC.player);
    }

    private boolean isGrimMode() {
        return "Grim".equals(choice("mode"));
    }

    private boolean isVulcanMode() {
        return "Vulcan".equals(choice("mode"));
    }

    private boolean isTellyMode() {
        return "Telly".equals(choice("mode"));
    }

    private boolean isGrimFamily() {
        return isGrimMode() || isVulcanMode();
    }

    private boolean usesSilentRotationPath() {
        return isGrimFamily() || (isTellyMode() && tellyOwnsInput);
    }

    private void tickGrimRotationReset() {
        if (grimRotationResetTicks <= 0) return;
        grimRotationResetTicks--;
        if (grimRotationResetTicks <= 0) grimSilentRotation = null;
    }

    private Input stabilizeMovementInput(Input input) {
        MovementLine optimalLine = currentMovementLine;
        if (optimalLine == null || input.jump() && MC.player.onGround()) return input;

        Vec3 nearestPoint = nearestPointOnLine(optimalLine, MC.player.position());
        Vec3 vectorToLine = nearestPoint.subtract(MC.player.position());
        Vec3 velocity = MC.player.getDeltaMovement();
        boolean movingTowardsLine = vectorToLine.dot(new Vec3(velocity.x, 0.0D, velocity.z)) > 0.0D;
        double maximumDeviation = movingTowardsLine ? 0.075D : 0.2D;
        if (nearestPoint.distanceToSqr(MC.player.position()) < maximumDeviation * maximumDeviation) return input;

        float desiredYaw = (float) (Math.toDegrees(Math.atan2(vectorToLine.z, vectorToLine.x)) - 90.0D);
        float degrees = Mth.wrapDegrees(desiredYaw - Mth.wrapDegrees(MC.player.getYRot()));
        Input correction = directionalInputForDegrees(degrees);
        boolean frontalAxisBlocked = input.forward() || input.backward();
        boolean sagittalAxisBlocked = input.right() || input.left();
        return new Input(
            frontalAxisBlocked ? input.forward() : correction.forward(),
            frontalAxisBlocked ? input.backward() : correction.backward(),
            sagittalAxisBlocked ? input.left() : correction.left(),
            sagittalAxisBlocked ? input.right() : correction.right(),
            input.jump(), input.shift(), input.sprint());
    }

    private Input transformSilentMovementInput(Input input) {
        if (!usesSilentRotationPath() || grimSilentRotation == null || grimRotationResetTicks <= 0) return input;

        return transformSilentMovementInput(input, MC.player.getYRot(), grimSilentRotation.yaw());
    }

    private Input transformTellyAuthoredInput(Input input) {
        if (!usesSilentRotationPath() || grimSilentRotation == null || grimRotationResetTicks <= 0) return input;
        float courseYaw = tellyGroundSteeringActive
            ? Mth.wrapDegrees(tellyAnchorYaw + tellyGroundSteerOffset)
            : tellyAnchorYaw;
        return transformSilentMovementInput(input, courseYaw, grimSilentRotation.yaw());
    }

    static Input transformSilentMovementInput(Input input, float playerYaw, float silentYaw) {
        if (input == null) return null;

        float forward = inputImpulse(input.forward(), input.backward());
        float sideways = inputImpulse(input.left(), input.right());
        float deltaYaw = (playerYaw - silentYaw) * Mth.DEG_TO_RAD;
        float cosine = Mth.cos(deltaYaw);
        float sine = Mth.sin(deltaYaw);
        float transformedSideways = sideways * cosine - forward * sine;
        float transformedForward = forward * cosine + sideways * sine;
        int roundedSideways = Math.round(transformedSideways);
        int roundedForward = Math.round(transformedForward);

        return new Input(
            roundedForward > 0,
            roundedForward < 0,
            roundedSideways > 0,
            roundedSideways < 0,
            input.jump(), input.shift(), input.sprint());
    }

    private static float inputImpulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    private static Input directionalInputForDegrees(float degrees) {
        return new Input(
            degrees > -90.0F && degrees < 90.0F,
            degrees < -90.0F || degrees > 90.0F,
            degrees > -180.0F && degrees < 0.0F,
            degrees > 0.0F && degrees < 180.0F,
            false, false, false);
    }

    private PlacementTarget findPlacementTarget(ItemStack stack) {
        if (!isValidBlock(stack)) return null;
        Vec3 predicted = predictedPlacementPosition(currentMovementLine);
        BlockPos predictedBase = targetedBase(predicted);
        return findFromBase(predictedBase, predicted, stack);
    }

    static BlockPos targetedBase(Vec3 position) {
        return BlockPos.containing(position).below();
    }

    private PlacementTarget findFromBase(BlockPos base, Vec3 plannedPosition, ItemStack stack) {
        if (base == null || MC.level.isOutsideBuildHeight(base)) return null;
        BlockState baseState = MC.level.getBlockState(base);
        if (isSolidSupport(baseState, base)) return null;

        for (BlockPos offset : orderedOffsetsForWorld(base, plannedPosition, currentMovementLine)) {
            BlockPos candidate = base.offset(offset);
            if (MC.level.isOutsideBuildHeight(candidate)) continue;
            BlockState candidateState = MC.level.getBlockState(candidate);
            if (isSolidSupport(candidateState, candidate)) continue;

            boolean replaceExisting = !candidateState.isAir() && candidateState.getFluidState().isEmpty();
            if (replaceExisting && !canBeReplacedWith(candidateState, candidate, stack)) continue;

            PlacementTarget target = planTargetForCandidate(candidate, plannedPosition, replaceExisting);
            if (target != null) return target;
        }
        return null;
    }

    private List<BlockPos> orderedOffsetsForWorld(BlockPos base, Vec3 predictedPosition,
                                                  MovementLine optimalLine) {
        List<BlockPos> ordered = new ArrayList<>(NORMAL_OFFSETS);
        ordered.sort(Comparator
            .comparingDouble((BlockPos offset) -> blockDistancePriority(
                base.offset(offset), predictedPosition, optimalLine))
            .thenComparingDouble(offset -> offset.distSqr(BlockPos.ZERO))
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
        return ordered;
    }

    private double blockDistancePriority(BlockPos pos, Vec3 predictedPosition, MovementLine optimalLine) {
        VoxelShape shape = MC.level.getBlockState(pos).getShape(MC.level, pos, CollisionContext.of(MC.player));
        if (shape.isEmpty()) {
            return optimalLine == null
                ? Vec3.atCenterOf(pos).distanceToSqr(predictedPosition)
                : distanceToLineSqr(optimalLine, Vec3.atCenterOf(pos));
        }

        double best = Double.POSITIVE_INFINITY;
        for (AABB local : shape.toAabbs()) {
            AABB box = local.move(pos);
            double distance = optimalLine == null
                ? distanceToBoxSqr(predictedPosition, box)
                : distanceToBoxSqr(optimalLine, box);
            best = Math.min(best, distance);
        }
        return best;
    }

    private PlacementTarget planTargetForCandidate(BlockPos candidate, Vec3 plannedPosition,
                                                    boolean replaceExisting) {
        TargetPlan bestPlan = null;
        double bestFaceAngle = Double.POSITIVE_INFINITY;
        Vec3 plannedEye = plannedPosition.add(0.0D, MC.player.getEyeHeight(), 0.0D);
        AutismRotationUtil.Rotation from = serverRotation();

        for (Direction face : Direction.values()) {
            BlockPos supportPos = replaceExisting ? candidate : candidate.relative(face.getOpposite());
            if (MC.level.isOutsideBuildHeight(supportPos)) continue;
            BlockState supportState = MC.level.getBlockState(supportPos);
            if (!replaceExisting && supportState.canBeReplaced()) continue;

            Vec3 faceCenter = Vec3.atCenterOf(supportPos).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
            );
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            if (plannedEye.subtract(faceCenter).dot(normal) < MIN_FACE_DISTANCE) continue;

            AutismRotationUtil.Rotation faceRotation = AutismRotationUtil.lookingAt(faceCenter, plannedEye);
            double faceAngle = rotationAngle(from, faceRotation);
            if (faceAngle < bestFaceAngle) {
                bestFaceAngle = faceAngle;
                bestPlan = new TargetPlan(supportPos.immutable(), face);
            }
        }

        if (bestPlan == null) return null;

        BlockState supportState = MC.level.getBlockState(bestPlan.supportBlock());
        VoxelShape shape = supportState.getShape(
            MC.level, bestPlan.supportBlock(), CollisionContext.of(MC.player));
        FaceSample bestSample = null;

        for (AABB localBox : shape.toAabbs()) {
            FaceRect face = FaceRect.fromBox(localBox, bestPlan.face());
            FaceRect searchFace = face;
            if (searchFace.to().y >= 0.9D) {
                FaceRect truncated = searchFace.truncateY(0.6D);
                if (truncated.area() > GEOMETRY_EPSILON) searchFace = truncated;
            }

            Vec3 point = stabilizedPointOnFace(
                searchFace, bestPlan.supportBlock(), plannedEye, from, currentMovementLine);
            if (point == null) continue;
            FaceSample sample = new FaceSample(face, point, bestPlan.face());
            if (bestSample == null || compareFaceSamples(sample, bestSample) > 0) bestSample = sample;
        }

        if (bestSample == null) return null;
        Vec3 worldPoint = bestSample.point().add(
            bestPlan.supportBlock().getX(), bestPlan.supportBlock().getY(), bestPlan.supportBlock().getZ());
        AutismRotationUtil.Rotation rotation = AutismRotationUtil.lookingAt(worldPoint, plannedEye);
        BlockHitResult plannedHit = new BlockHitResult(
            Vec3.atCenterOf(bestPlan.supportBlock()), bestPlan.face(), bestPlan.supportBlock(), false);
        return new PlacementTarget(
            bestPlan.supportBlock(), candidate.immutable(), bestPlan.face(), plannedHit,
            rotation, bestPlan.supportBlock().getY() + bestSample.face().from().y);
    }

    private PlacementTarget validateForHeldBlock(PlacementTarget target, ItemStack stack, InteractionHand hand,
                                                   AutismRotationUtil.Rotation rotation) {
        if (!isValidBlock(stack) || target == null) return null;
        BlockState candidateState = MC.level.getBlockState(target.placedBlock());
        if (isSolidSupport(candidateState, target.placedBlock())) return null;

        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        BlockHitResult ray = raytrace(rotation, reach);
        if (ray == null || !ray.getBlockPos().equals(target.supportBlock()) || ray.getDirection() != target.face()) {
            return null;
        }
        if (!target.supportBlock().equals(target.placedBlock())
            && !target.supportBlock().relative(target.face()).equals(target.placedBlock())) return null;
        if (ray.getLocation().y < target.minPlacementY()) return null;
        if (ModuleRegistry.shouldCancelUseExcept(ray, hand, id())) return null;

        return new PlacementTarget(target.supportBlock(), target.placedBlock(), target.face(), ray,
            rotation, target.minPlacementY());
    }

    private void place(PlacementTarget target, InteractionHand hand, ItemStack stack) {
        place(target, hand, stack, true, true);
    }

    private void place(PlacementTarget target, InteractionHand hand, ItemStack stack,
                       boolean restoreClientRotation, boolean sendPlacementRotation) {
        AutismRotationUtil.Rotation rotation = target.rotation();
        MovementLine placementLine = currentMovementLine;
        Vec3 previousFallOff = findFallOffPosition(placementLine);

        float clientYaw = MC.player.getYRot();
        float clientPitch = MC.player.getXRot();
        if (sendPlacementRotation && !sameRotation(rotation, serverRotation())) sendRotation(rotation);
        int oldCount = stack.getCount();
        try {
            InteractionResult result = MC.gameMode.useItemOn(MC.player, hand, target.hit());
            if (result instanceof InteractionResult.Fail) return;
            if (result instanceof InteractionResult.Pass) {
                if (stack.isEmpty()) return;
                InteractionResult itemResult = MC.gameMode.useItem(MC.player, hand);
                if (itemResult instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.CLIENT) MC.player.swing(hand);
                    MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
                }
                return;
            }
            if (!result.consumesAction()) return;
            if (result instanceof InteractionResult.Success success
                && success.swingSource() != InteractionResult.SwingSource.CLIENT) return;

            MC.player.swing(hand);
            autismclient.util.AutismCpsTracker.recordRight();
            autismclient.util.AutismScaffoldPlaceRenderer.recordPlacement(target.placedBlock());
            trackSuccessfulPlacement(target.placedBlock(), placementLine, previousFallOff);
            boolean wasStackUsed = !stack.isEmpty()
                && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials());
            if (wasStackUsed) {
                MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
        } finally {
            if (restoreClientRotation) {
                AutismRotationUtil.Rotation clientRotation = new AutismRotationUtil.Rotation(clientYaw, clientPitch);
                if (!sameRotation(serverRotation(), clientRotation)) sendRotation(clientRotation);
            }
        }
    }

    private void trackSuccessfulPlacement(BlockPos placed, MovementLine line, Vec3 previousFallOff) {
        BlockPos immutable = placed.immutable();
        if (!immutable.equals(lastPlacedBlocks.peekLast())) {
            while (lastPlacedBlocks.size() >= MAX_LAST_PLACED_BLOCKS) lastPlacedBlocks.removeFirst();
            lastPlacedBlocks.addLast(immutable);
        }
        if (line == null || previousFallOff == null) return;

        float angle = (float) Math.atan2(line.direction().z, line.direction().x);
        Vec3 unrotatedOffset = MC.player.position().subtract(previousFallOff).yRot(angle);
        placementOffsets.addLast(unrotatedOffset);
        while (placementOffsets.size() > MAX_PLACEMENT_OFFSETS) placementOffsets.removeFirst();
    }

    private void sendRotation(AutismRotationUtil.Rotation rotation) {
        if (rotation == null || MC.getConnection() == null || MC.player == null) return;
        serverRotation = rotation;
        MC.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
            MC.player.getX(), MC.player.getY(), MC.player.getZ(),
            rotation.yaw(), rotation.pitch(), MC.player.onGround(), MC.player.horizontalCollision
        ));
    }

    private static boolean sameRotation(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {
        return first != null && second != null
            && Float.compare(first.yaw(), second.yaw()) == 0
            && Float.compare(first.pitch(), second.pitch()) == 0;
    }

    private BlockHitResult raytrace(AutismRotationUtil.Rotation rotation, double reach) {
        if (rotation == null) return null;
        Vec3 eye = MC.player.getEyePosition();
        Vec3 look = lookVector(rotation);
        Vec3 end = eye.add(look.scale(reach));
        HitResult result = MC.level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player
        ));
        return result instanceof BlockHitResult blockHit && result.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    private ItemStack planningStack() {
        ItemStack main = MC.player.getMainHandItem();
        if (isValidBlock(main)) return main;

        int hotbar = findBestBlockSlot();
        if (hotbar >= 0) return MC.player.getInventory().getItem(hotbar);
        return ItemStack.EMPTY;
    }

    private boolean canBeReplacedWith(BlockState state, BlockPos pos, ItemStack stack) {
        BlockPlaceContext context = new BlockPlaceContext(
            MC.player,
            InteractionHand.MAIN_HAND,
            stack,
            new BlockHitResult(Vec3.atLowerCornerOf(pos), Direction.UP, pos, false)
        );
        return state.canBeReplaced(context);
    }

    private InteractionHand ensurePlacementHand() {
        ItemStack main = MC.player.getMainHandItem();
        if (isValidBlock(main)) {
            if (requestedSlot == MC.player.getInventory().getSelectedSlot()) requestedSlot = -1;
            return InteractionHand.MAIN_HAND;
        }

        int slot = findBestBlockSlot();
        if (slot >= 0) {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (selected == slot) return InteractionHand.MAIN_HAND;
            if (bool("switch-back") && originalSlot < 0) originalSlot = selected;
            requestedSlot = slot;
            selectionPending = true;
            AutismInputClicker.queueHotbarSlot(slot);
            return null;
        }
        return null;
    }

    private int findBestBlockSlot() {
        int best = findBestBlockSlot(true);
        return best >= 0 ? best : findBestBlockSlot(false);
    }

    private int findBestBlockSlot(boolean requireReserve) {
        int best = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (!isValidBlock(stack) || requireReserve && stack.getCount() <= 1) continue;
            if (best < 0 || compareBlockStacks(stack, MC.player.getInventory().getItem(best)) > 0) best = slot;
        }
        return best;
    }

    private int compareBlockStacks(ItemStack first, ItemStack second) {
        Block firstBlock = ((BlockItem) first.getItem()).getBlock();
        Block secondBlock = ((BlockItem) second.getItem()).getBlock();
        BlockState firstState = firstBlock.defaultBlockState();
        BlockState secondState = secondBlock.defaultBlockState();

        int result = Boolean.compare(!isUnfavorable(firstBlock, firstState),
            !isUnfavorable(secondBlock, secondState));
        if (result != 0) return result;
        result = Boolean.compare(firstState.isRedstoneConductor(MC.level, BlockPos.ZERO),
            secondState.isRedstoneConductor(MC.level, BlockPos.ZERO));
        if (result != 0) return result;
        result = Boolean.compare(firstState.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO),
            secondState.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO));
        if (result != 0) return result;
        result = Float.compare(firstBlock.getFriction(), secondBlock.getFriction());
        if (result != 0) return result;
        result = Float.compare(Math.abs(firstBlock.getJumpFactor() - 1.0F),
            Math.abs(secondBlock.getJumpFactor() - 1.0F));
        if (result != 0) return result;
        result = Float.compare(Math.abs(firstBlock.getSpeedFactor() - 1.0F),
            Math.abs(secondBlock.getSpeedFactor() - 1.0F));
        if (result != 0) return result;
        result = Double.compare(hardnessDistance(secondState, true), hardnessDistance(firstState, true));
        if (result != 0) return result;
        result = Integer.compare(second.getCount(), first.getCount());
        if (result != 0) return result;
        return Double.compare(hardnessDistance(secondState, false), hardnessDistance(firstState, false));
    }

    private boolean isUnfavorable(Block block, BlockState state) {
        return block.getFriction() > 0.6F
            || block.getSpeedFactor() < 1.0F
            || block.getJumpFactor() < 1.0F
            || block instanceof BaseEntityBlock
            || !state.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO)
            || block == Blocks.CRAFTING_TABLE
            || block == Blocks.JIGSAW
            || block == Blocks.SMITHING_TABLE
            || block == Blocks.FLETCHING_TABLE
            || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.CAULDRON
            || block == Blocks.MAGMA_BLOCK;
    }

    private double hardnessDistance(BlockState state, boolean neutralRange) {
        double hardness = state.getDestroySpeed(MC.level, BlockPos.ZERO);
        if (neutralRange && hardness >= 0.8D && hardness <= 2.0D) return 0.0D;
        return Math.abs(1.7D - hardness);
    }

    private boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!stack.isItemEnabled(MC.level.enabledFeatures())) return false;
        Block block = blockItem.getBlock();
        if (!isPlaceableBlockChoice(block) || !filterAllows(block)) return false;
        BlockState state = block.defaultBlockState();
        return state.entityCanStandOnFace(MC.level, BlockPos.ZERO, MC.player, Direction.UP);
    }

    public static boolean isPlaceableBlockChoice(Block block) {
        if (block == null || !(block.asItem() instanceof BlockItem blockItem) || blockItem.getBlock() != block) {
            return false;
        }
        if (block instanceof FallingBlock || block == Blocks.TNT || block == Blocks.COBWEB
            || block == Blocks.NETHER_PORTAL) return false;
        try {
            VoxelShape collision = block.defaultBlockState().getCollisionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            return Block.isFaceFull(collision, Direction.UP);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean filterAllows(Block block) {
        String mode = choice("filter-mode");
        if ("Off".equals(mode)) return true;
        boolean listed = filteredBlocks().contains(block);
        return "Whitelist".equals(mode) ? listed : !listed;
    }

    private Set<Block> filteredBlocks() {
        String raw = value("blocks");
        if (raw.equals(cachedFilterRaw)) return cachedFilterBlocks;
        Set<Block> blocks = new HashSet<>();
        for (String entry : list("blocks")) {
            Identifier id = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (id == null) continue;
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> {
                if (isPlaceableBlockChoice(block)) blocks.add(block);
            });
        }
        cachedFilterRaw = raw;
        cachedFilterBlocks = Set.copyOf(blocks);
        return cachedFilterBlocks;
    }

    private void refreshSlotReset() {
        if (bool("switch-back") && originalSlot >= 0) slotResetTicks = SLOT_RESET_TICKS;
    }

    private void refreshSelectionReset() {
        refreshSlotReset();
    }

    private void tickSlotReset() {
        if (!bool("switch-back")) {
            originalSlot = -1;
            slotResetTicks = 0;
            return;
        }
        if (originalSlot < 0 || MC == null || MC.player == null) return;
        if (slotResetTicks > 0) {
            slotResetTicks--;
            return;
        }
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected != originalSlot) {
            requestedSlot = originalSlot;
            AutismInputClicker.queueHotbarSlot(originalSlot);
            return;
        }
        originalSlot = -1;
        requestedSlot = -1;
    }

    private AutismRotationUtil.Rotation serverRotation() {
        if (serverRotation != null) return serverRotation;
        return MC != null && MC.player != null
            ? AutismRotationUtil.playerRotation(MC.player)
            : new AutismRotationUtil.Rotation(0.0F, 0.0F);
    }

    private static double rotationAngle(AutismRotationUtil.Rotation first,
                                        AutismRotationUtil.Rotation second) {
        double cosine = Mth.clamp(lookVector(first).dot(lookVector(second)), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(cosine));
    }

    private static Vec3 lookVector(AutismRotationUtil.Rotation rotation) {
        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        float pitch = rotation.pitch() * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitch);
        return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
    }

    private MovementLine buildMovementLine(Input input) {
        Vec3 direction = chooseDirection(movementYaw(input));

        SupportReference support = findSupportReferenceUnderPlayer();
        if (support == null) return null;
        lastSupportReference = support;

        MovementLine placedLine = fitLineThroughLastPlacements();
        Vec3 anchor;
        if (placedLine != null && placedLine.direction().dot(direction) >= 0.5D) {
            anchor = nearestPointOnLine(placedLine, MC.player.position());
        } else {
            anchor = new Vec3(
                support.blockPos().getX() + 0.5D + support.offsetX(),
                MC.player.getY(),
                support.blockPos().getZ() + 0.5D + support.offsetZ());
        }
        return new MovementLine(new Vec3(anchor.x, MC.player.getY(), anchor.z), direction);
    }

    private MovementLine fitLineThroughLastPlacements() {
        if (lastPlacedBlocks.size() < 2) return null;
        Iterator<BlockPos> iterator = lastPlacedBlocks.descendingIterator();
        BlockPos last = iterator.next();
        BlockPos previous = iterator.next();
        Vec3 lastCenter = new Vec3(last.getX() + 0.5D, last.getY(), last.getZ() + 0.5D);
        Vec3 previousCenter = new Vec3(previous.getX() + 0.5D, previous.getY(), previous.getZ() + 0.5D);
        Vec3 direction = lastCenter.subtract(previousCenter);
        if (direction.lengthSqr() <= 1.0E-8D) return null;
        direction = direction.normalize();
        return new MovementLine(previousCenter.add(lastCenter).scale(0.5D), direction);
    }

    private SupportReference findSupportReferenceUnderPlayer() {
        List<SupportCandidate> candidates = new ArrayList<>(SUPPORT_SAMPLES.length * SUPPORT_SAMPLES.length);
        Set<BlockPos> visited = new HashSet<>();
        Vec3 playerPosition = MC.player.position();
        for (double xOffset : SUPPORT_SAMPLES) {
            for (double zOffset : SUPPORT_SAMPLES) {
                BlockPos pos = BlockPos.containing(
                    playerPosition.x + xOffset,
                    playerPosition.y - 1.0D,
                    playerPosition.z + zOffset);
                if (!visited.add(pos)) continue;
                SupportCandidate candidate = createSupportCandidate(pos);
                if (candidate != null) candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            lastSupportPosition = null;
            lastSupportReference = null;
            return null;
        }

        SupportCandidate best = candidates.stream().min(ScaffoldModule::compareSupportCandidates).orElse(null);
        if (best == null) return null;
        SupportCandidate chosen = stableSupportCandidate(candidates, best);
        lastSupportPosition = chosen.blockPos();
        return new SupportReference(
            chosen.blockPos(),
            playerPosition.x - (chosen.blockPos().getX() + 0.5D),
            playerPosition.z - (chosen.blockPos().getZ() + 0.5D));
    }

    private SupportCandidate createSupportCandidate(BlockPos pos) {
        BlockState state = MC.level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(MC.level, pos, CollisionContext.of(MC.player));
        if (shape.isEmpty()) return null;

        AABB playerBox = MC.player.getBoundingBox();
        double bestSurfaceDelta = Double.POSITIVE_INFINITY;
        double overlapAtBestSurface = 0.0D;
        for (AABB local : shape.toAabbs()) {
            double minX = pos.getX() + local.minX;
            double maxX = pos.getX() + local.maxX;
            double maxY = pos.getY() + local.maxY;
            double minZ = pos.getZ() + local.minZ;
            double maxZ = pos.getZ() + local.maxZ;
            double overlapX = Math.min(playerBox.maxX, maxX) - Math.max(playerBox.minX, minX);
            double overlapZ = Math.min(playerBox.maxZ, maxZ) - Math.max(playerBox.minZ, minZ);
            if (overlapX <= 0.0D || overlapZ <= 0.0D) continue;

            double surfaceDelta = Math.abs(playerBox.minY - maxY);
            double overlap = overlapX * overlapZ;
            if (surfaceDelta + SUPPORT_SURFACE_EPSILON < bestSurfaceDelta) {
                bestSurfaceDelta = surfaceDelta;
                overlapAtBestSurface = overlap;
            } else if (Math.abs(surfaceDelta - bestSurfaceDelta) <= SUPPORT_SURFACE_EPSILON) {
                overlapAtBestSurface += overlap;
            }
        }
        if (!Double.isFinite(bestSurfaceDelta)) return null;
        double dx = MC.player.getX() - (pos.getX() + 0.5D);
        double dz = MC.player.getZ() - (pos.getZ() + 0.5D);
        return new SupportCandidate(pos.immutable(), overlapAtBestSurface, bestSurfaceDelta, dx * dx + dz * dz);
    }

    private SupportCandidate stableSupportCandidate(List<SupportCandidate> candidates, SupportCandidate best) {
        BlockPos lastPlaced = lastPlacedBlocks.peekLast();
        SupportCandidate preferred = candidateAt(candidates, lastPlaced);
        if (preferred != null && supportIsStable(preferred, best)) return preferred;
        preferred = candidateAt(candidates, lastSupportPosition);
        if (preferred != null && supportIsStable(preferred, best)) return preferred;
        return best;
    }

    private static SupportCandidate candidateAt(List<SupportCandidate> candidates, BlockPos position) {
        if (position == null) return null;
        for (SupportCandidate candidate : candidates) {
            if (candidate.blockPos().equals(position)) return candidate;
        }
        return null;
    }

    private static boolean supportIsStable(SupportCandidate candidate, SupportCandidate best) {
        return candidate.surfaceDelta() <= best.surfaceDelta() + SUPPORT_SURFACE_EPSILON
            && candidate.overlapArea() + SUPPORT_OVERLAP_HYSTERESIS >= best.overlapArea();
    }

    private static int compareSupportCandidates(SupportCandidate first, SupportCandidate second) {
        if (first.surfaceDelta() + SUPPORT_SURFACE_EPSILON < second.surfaceDelta()) return -1;
        if (second.surfaceDelta() + SUPPORT_SURFACE_EPSILON < first.surfaceDelta()) return 1;
        if (first.overlapArea() > second.overlapArea() + SUPPORT_OVERLAP_HYSTERESIS) return -1;
        if (first.overlapArea() + SUPPORT_OVERLAP_HYSTERESIS < second.overlapArea()) return 1;
        return Double.compare(first.horizontalDistanceSqr(), second.horizontalDistanceSqr());
    }

    private Vec3 predictedPlacementPosition(MovementLine line) {
        Vec3 playerPosition = MC.player.position();
        if (line == null) return playerPosition;
        if (isCloseToEdge(PREDICTION_CUTOFF_DISTANCE, playerPosition)) return playerPosition;
        Vec3 fallOff = findFallOffPosition(line);
        if (fallOff == null) return playerPosition;

        Vec3 delta = fallOff.subtract(playerPosition);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        Vec3 bootstrap = horizontalDistance <= 1.0E-8D
            ? fallOff
            : fallOff.subtract(new Vec3(delta.x, 0.0D, delta.z).scale(PREDICTION_BACKOFF / horizontalDistance));
        Vec3 average = averagePlacementOffset();
        if (average == null) {
            SupportReference support = lastSupportReference;
            return support == null
                ? bootstrap
                : bootstrap.add(support.offsetX(), 0.0D, support.offsetZ());
        }

        float angle = (float) Math.atan2(line.direction().z, line.direction().x);
        Vec3 historyPosition = fallOff.add(average.yRot(-angle));
        double blend = Math.min(1.0D, placementOffsets.size() / (double) PREDICTION_WARMUP_PLACEMENTS);
        return bootstrap.lerp(historyPosition, blend);
    }

    private Vec3 findFallOffPosition(MovementLine line) {
        if (line == null) return null;
        Vec3 nearest = nearestPointOnLine(line, MC.player.position());
        Vec3 from = nearest.add(0.0D, -0.1D, 0.0D);
        Vec3 to = from.add(line.direction().scale(PREDICTION_LINE_LENGTH));
        Vec3 collision = findEdgeCollision(from, to);
        return collision == null ? null : new Vec3(collision.x, MC.player.getY(), collision.z);
    }

    private boolean isCloseToEdge(double distance, Vec3 position) {
        Input input = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
        Vec3 nextVelocity = MC.player.getDeltaMovement();
        Vec3 direction;
        if (nextVelocity.horizontalDistanceSqr() > 0.003D * 0.003D) {
            direction = new Vec3(nextVelocity.x, 0.0D, nextVelocity.z).normalize();
        } else if (hasDirectionalInput(input)) {
            direction = Vec3.directionFromRotation(0.0F, movementYaw(input));
        } else {
            direction = Vec3.directionFromRotation(0.0F, MC.player.getYRot());
        }

        Vec3 from = position.add(0.0D, -0.1D, 0.0D);
        if (findEdgeCollision(from, from.add(direction.scale(distance))) != null) return true;

        Vec3 nextPosition = position.add(nextVelocity.x, nextVelocity.y, nextVelocity.z);
        Vec3 positionInTwoTicks = nextPosition.add(nextVelocity.x, 0.0D, nextVelocity.z);
        return wouldBeCloseToFallOff(position) || wouldBeCloseToFallOff(positionInTwoTicks);
    }

    private boolean shouldSneakAtEdge() {
        if (MC.player.onGround()) {
            double lookahead = Mth.clamp(0.1D + MC.player.getDeltaMovement().horizontalDistance() * 3.0D, 0.1D, 0.6D);
            return isCloseToEdge(lookahead, MC.player.position());
        }
        return MC.player.fallDistance > 0.0F && isCloseToEdge(0.1D, MC.player.position());
    }

    private enum FallRisk { NONE, IMMINENT }

    private FallRisk predictFallRisk() {
        if (MC.player.onGround() && !isCloseToEdge(0.35D, MC.player.position())) return FallRisk.NONE;
        Vec3 position = MC.player.position();
        Vec3 velocity = MC.player.getDeltaMovement();
        double x = position.x, y = position.y, z = position.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        double startY = y;
        for (int tick = 0; tick < 12; tick++) {
            vx *= 0.91D;
            vy = (vy - 0.08D) * 0.98D;
            vz *= 0.91D;
            x += vx;
            y += vy;
            z += vz;
            if (hasSupportBelow(x, y, z, 2)) return FallRisk.NONE;
            double drop = startY - y;
            if (drop > 2.0D) return FallRisk.IMMINENT;
            if (drop > 0.6D && tick < 4) return FallRisk.IMMINENT;
        }
        return FallRisk.NONE;
    }

    private boolean hasSupportBelow(double x, double y, double z, int blocks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
            Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int step = 0; step <= blocks; step++) {
            BlockState state = MC.level.getBlockState(pos);
            if (!state.isAir() && state.isSolidRender()) return true;
            pos.set(pos.getX(), pos.getY() - 1, pos.getZ());
        }
        return false;
    }

    private boolean wouldBeCloseToFallOff(Vec3 position) {
        AABB hitbox = MC.player.getDimensions(MC.player.getPose())
            .makeBoundingBox(position)
            .inflate(-0.05D, 0.0D, -0.05D)
            .move(0.0D, MC.player.fallDistance - MC.player.maxUpStep(), 0.0D);
        return MC.level.noCollision(MC.player, hitbox);
    }

    private Vec3 findEdgeCollision(Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        if (line.lengthSqr() <= 1.0E-12D) return null;
        List<AABB> boxes = collectSupportBoxes(from, to);
        Vec3 current = from;
        Vec3 extendedFrom = from.add(line.scale(-1000.0D));
        Vec3 extendedTo = to.add(line.scale(1000.0D));

        while (true) {
            List<AABB> containing = new ArrayList<>();
            for (AABB box : boxes) {
                if (box.contains(current)) containing.add(box);
            }
            if (containing.isEmpty()) return current;
            for (AABB box : containing) {
                if (box.contains(to)) return null;
            }

            Vec3 next = null;
            double nearestToDestination = Double.POSITIVE_INFINITY;
            for (AABB box : containing) {
                Vec3 clipped = box.clip(extendedTo, extendedFrom).orElse(null);
                if (clipped == null) continue;
                double distance = clipped.distanceToSqr(to);
                if (distance < nearestToDestination) {
                    nearestToDestination = distance;
                    next = clipped;
                }
            }
            if (next == null) return current;
            current = next;
            boxes.removeAll(containing);
        }
    }

    private List<AABB> collectSupportBoxes(Vec3 from, Vec3 to) {
        AABB fromBox = MC.player.getDimensions(Pose.STANDING).makeBoundingBox(from);
        AABB toBox = MC.player.getDimensions(Pose.STANDING).makeBoundingBox(to);
        AABB union = fromBox.minmax(toBox);
        int minX = Mth.floor(union.minX - 0.3D - 1.0E-7D);
        int maxX = Mth.floor(union.maxX + 0.3D + 1.0E-7D);
        int minY = Mth.floor(union.minY - 0.5D - 1.0E-7D);
        int maxY = Mth.floor(union.minY + 1.0E-7D);
        int minZ = Mth.floor(union.minZ - 0.3D - 1.0E-7D);
        int maxZ = Mth.floor(union.maxZ + 0.3D + 1.0E-7D);
        Vec3 line = to.subtract(from);
        Vec3 extendedFrom = from.add(line.scale(-1000.0D));
        Vec3 extendedTo = to.add(line.scale(1000.0D));
        List<AABB> boxes = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    VoxelShape shape = MC.level.getBlockState(pos).getCollisionShape(MC.level, pos);
                    for (AABB local : shape.toAabbs()) {
                        AABB adjusted = new AABB(
                            x + local.minX - 0.3D,
                            y + local.minY - 1.0D,
                            z + local.minZ - 0.3D,
                            x + local.maxX + 0.3D,
                            y + local.maxY + 0.55D,
                            z + local.maxZ + 0.3D);
                        if (adjusted.clip(extendedFrom, extendedTo).isPresent()) boxes.add(adjusted);
                    }
                }
            }
        }
        return boxes;
    }

    private Vec3 averagePlacementOffset() {
        if (placementOffsets.isEmpty()) return null;
        Vec3 sum = Vec3.ZERO;
        for (Vec3 offset : placementOffsets) sum = sum.add(offset);
        return sum.scale(1.0D / placementOffsets.size());
    }

    private static Vec3 nearestPointOnLine(MovementLine line, Vec3 point) {
        Vec3 delta = point.subtract(line.origin());
        double projection = delta.dot(line.direction());
        return line.origin().add(line.direction().scale(projection));
    }

    private static double distanceToLineSqr(MovementLine line, Vec3 point) {
        return nearestPointOnLine(line, point).distanceToSqr(point);
    }

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        double x = Mth.clamp(point.x, box.minX, box.maxX);
        double y = Mth.clamp(point.y, box.minY, box.maxY);
        double z = Mth.clamp(point.z, box.minZ, box.maxZ);
        return point.distanceToSqr(new Vec3(x, y, z));
    }

    private static double distanceToBoxSqr(MovementLine movementLine, AABB box) {
        InfiniteLine line = new InfiniteLine(movementLine.origin(), movementLine.direction());
        if (lineIntersectsBox(line, box)) return 0.0D;

        Vec3 p000 = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 p001 = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 p010 = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 p011 = new Vec3(box.minX, box.maxY, box.maxZ);
        Vec3 p100 = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 p101 = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 p110 = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 p111 = new Vec3(box.maxX, box.maxY, box.maxZ);
        LineSegment3[] edges = {
            new LineSegment3(p000, p001), new LineSegment3(p000, p010), new LineSegment3(p000, p100),
            new LineSegment3(p111, p110), new LineSegment3(p111, p101), new LineSegment3(p111, p011),
            new LineSegment3(p001, p011), new LineSegment3(p001, p101),
            new LineSegment3(p010, p011), new LineSegment3(p010, p110),
            new LineSegment3(p100, p101), new LineSegment3(p100, p110)
        };
        double best = Double.POSITIVE_INFINITY;
        for (LineSegment3 edge : edges) {
            NearestPair pair = nearestPoints(edge, line);
            if (pair != null) best = Math.min(best, pair.first().distanceToSqr(pair.second()));
        }
        return best;
    }

    private static boolean lineIntersectsBox(InfiniteLine line, AABB box) {
        double enter = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        double[] anchors = {line.anchor().x, line.anchor().y, line.anchor().z};
        double[] directions = {line.direction().x, line.direction().y, line.direction().z};
        double[] minimums = {box.minX, box.minY, box.minZ};
        double[] maximums = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Mth.equal(directions[axis], 0.0D)) {
                if (anchors[axis] < minimums[axis] || anchors[axis] > maximums[axis]) return false;
                continue;
            }
            double first = (minimums[axis] - anchors[axis]) / directions[axis];
            double second = (maximums[axis] - anchors[axis]) / directions[axis];
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            if (enter > exit + GEOMETRY_EPSILON) return false;
        }
        return true;
    }

    private static boolean hasDirectionalInput(Input input) {
        return input != null && (input.forward() != input.backward() || input.left() != input.right());
    }

    private float movementYaw(Input input) {
        float yaw = MC.player.getYRot();
        float forwardMultiplier;
        if (input.backward() && !input.forward()) {
            yaw += 180.0F;
            forwardMultiplier = -0.5F;
        } else if (input.forward() && !input.backward()) {
            forwardMultiplier = 0.5F;
        } else {
            forwardMultiplier = 1.0F;
        }
        if (input.left() && !input.right()) yaw -= 90.0F * forwardMultiplier;
        if (input.right() && !input.left()) yaw += 90.0F * forwardMultiplier;
        return yaw;
    }

    private float tellyMovementYaw(LocalPlayer player) {
        Input move = new Input(
            physicallyDown(MC.options.keyUp),
            physicallyDown(MC.options.keyDown),
            physicallyDown(MC.options.keyLeft),
            physicallyDown(MC.options.keyRight),
            false, false, false);
        if (!hasDirectionalInput(move)) return Float.isFinite(tellyAnchorYaw) ? tellyAnchorYaw : player.getYRot();
        return movementYaw(move);
    }

    private Vec3 chooseDirection(float currentAngle) {
        if (!Float.isNaN(lastDirectionAngle)
            && Math.abs(Mth.wrapDegrees(currentAngle - lastDirectionAngle)) <= DIRECTION_HYSTERESIS_DEGREES) {
            return Vec3.directionFromRotation(0.0F, lastDirectionAngle);
        }
        float directionNumber = currentAngle / 180.0F * 4.0F + 4.0F;
        float roundedDirection = Math.round(directionNumber);
        lastDirectionAngle = Mth.wrapDegrees((roundedDirection - 4.0F) / 4.0F * 180.0F);
        return Vec3.directionFromRotation(0.0F, lastDirectionAngle);
    }

    private Vec3 stabilizedPointOnFace(FaceRect face, BlockPos targetPos, Vec3 eye,
                                       AutismRotationUtil.Rotation currentRotation,
                                       MovementLine optimalLine) {
        Vec3 offset = new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        FaceRect trimmed = face.trim(FACE_INSET).offset(offset);
        FaceRect targetFace = stabilizedTargetFace(trimmed, eye, optimalLine);
        Vec3 point = nearestPointToFace(targetFace, new InfiniteLine(eye, lookVector(currentRotation)));
        return point.subtract(offset);
    }

    private FaceRect stabilizedTargetFace(FaceRect trimmedFace, Vec3 eye, MovementLine optimalLine) {
        if (optimalLine == null) return trimmedFace;

        Vec3 nearest = nearestPointOnLine(optimalLine, MC.player.position());
        Vec3 directionToLine = MC.player.position().subtract(nearest).normalize();
        Vec3 collision = planeIntersection(
            trimmedFace, new InfiniteLine(eye, optimalLine.direction()));
        if (collision == null) return trimmedFace;

        Vec3 b = MC.player.position().add(directionToLine.scale(2.0D));
        AABB crop = new AABB(
            collision.x, MC.player.getY() - 2.0D, collision.z,
            b.x, MC.player.getY() + 1.0D, b.z);
        FaceRect clamped = trimmedFace.clamp(crop);
        return clamped.area() < 0.0001D ? trimmedFace : clamped;
    }

    private static int compareFaceSamples(FaceSample first, FaceSample second) {
        int normal = Double.compare(faceNormalDistance(first), faceNormalDistance(second));
        return normal != 0 ? normal : Double.compare(first.point().y, second.point().y);
    }

    private static double faceNormalDistance(FaceSample sample) {
        Vec3 centered = sample.point().subtract(0.5D, 0.5D, 0.5D);
        double x = centered.x * sample.side().getStepX();
        double y = centered.y * sample.side().getStepY();
        double z = centered.z * sample.side().getStepZ();
        return x * x + y * y + z * z;
    }

    private static Vec3 nearestPointToFace(FaceRect face, InfiniteLine line) {
        Vec3 intersection = planeIntersection(face, line);
        List<LineSegment3> edges = face.edges();
        Vec3 center = face.center();
        if (intersection != null) {
            boolean inside = true;
            for (LineSegment3 edge : edges) {
                Vec3 edgeCenter = edge.pointAt(0.5D);
                if (edgeCenter.subtract(intersection).dot(edgeCenter.subtract(center)) <= 0.0D) {
                    inside = false;
                    break;
                }
            }
            if (edges.isEmpty() || inside) return intersection;
        }

        Vec3 bestPoint = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (LineSegment3 edge : edges) {
            NearestPair pair = nearestPoints(edge, line);
            if (pair == null) continue;
            double distance = pair.first().distanceToSqr(pair.second());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = pair.first();
            }
        }
        return bestPoint != null ? bestPoint : intersection != null ? intersection : center;
    }

    private static Vec3 planeIntersection(FaceRect face, InfiniteLine line) {
        Vec3 dimensions = face.dimensions();
        double plane;
        double anchor;
        double direction;
        if (Mth.equal(dimensions.x, 0.0D)) {
            plane = face.from().x;
            anchor = line.anchor().x;
            direction = line.direction().x;
        } else if (Mth.equal(dimensions.y, 0.0D)) {
            plane = face.from().y;
            anchor = line.anchor().y;
            direction = line.direction().y;
        } else if (Mth.equal(dimensions.z, 0.0D)) {
            plane = face.from().z;
            anchor = line.anchor().z;
            direction = line.direction().z;
        } else {
            return null;
        }
        if (Mth.equal(direction, 0.0D)) return null;
        double parameter = (plane - anchor) / direction;
        return Double.isFinite(parameter) ? line.pointAt(parameter) : null;
    }

    private static NearestPair nearestPoints(LineSegment3 segment, InfiniteLine line) {
        Vec3 firstDirection = segment.direction();
        Vec3 secondDirection = line.direction();
        Vec3 delta = segment.start().subtract(line.anchor());
        double a = firstDirection.dot(firstDirection);
        double b = firstDirection.dot(secondDirection);
        double c = secondDirection.dot(secondDirection);
        double d = firstDirection.dot(delta);
        double e = secondDirection.dot(delta);
        double determinant = a * c - b * b;

        NearestCandidate best = null;
        if (Math.abs(determinant) > GEOMETRY_EPSILON) {
            best = chooseNearest(best, segment, line,
                (b * e - c * d) / determinant,
                (a * e - b * d) / determinant);
        }
        best = chooseNearest(best, segment, line, 0.0D, e / c);
        best = chooseNearest(best, segment, line, 1.0D, (b + e) / c);
        best = chooseNearest(best, segment, line, Mth.clamp(-d / a, 0.0D, 1.0D), 0.0D);
        best = chooseNearest(best, segment, line, 0.0D, e / c);
        return best == null ? null : new NearestPair(best.first(), best.second());
    }

    private static NearestCandidate chooseNearest(NearestCandidate best, LineSegment3 segment,
                                                   InfiniteLine line, double firstParameter,
                                                   double secondParameter) {
        if (!Double.isFinite(firstParameter) || !Double.isFinite(secondParameter)
            || firstParameter < -GEOMETRY_EPSILON || firstParameter > 1.0D + GEOMETRY_EPSILON) return best;
        double first = Mth.clamp(firstParameter, 0.0D, 1.0D);
        Vec3 firstPoint = segment.pointAt(first);
        Vec3 secondPoint = line.pointAt(secondParameter);
        double distance = firstPoint.distanceToSqr(secondPoint);
        if (best == null || distance < best.distance() - GEOMETRY_EPSILON) {
            return new NearestCandidate(firstPoint, secondPoint, distance);
        }
        return best;
    }

    private boolean isSolidSupport(BlockState state, BlockPos pos) {
        return state.isFaceSturdy(MC.level, pos, Direction.UP, SupportType.CENTER);
    }

    private void clearRuntime(boolean restoreSlot) {
        if (restoreSlot && bool("switch-back") && originalSlot >= 0 && MC != null && MC.player != null
            && !MultiPilot.isActive() && !MacroExecutor.isRunning()
            && MC.player.getInventory().getSelectedSlot() != originalSlot) {
            AutismInputClicker.queueHotbarSlot(originalSlot);
        }
        originalSlot = -1;
        requestedSlot = -1;
        slotResetTicks = 0;
        selectionPending = false;
        serverRotation = MC != null && MC.player != null
            ? AutismRotationUtil.playerRotation(MC.player)
            : null;
        grimSilentRotation = null;
        grimRotationResetTicks = 0;
        resetTellyState();
        vulcanSneakTicks = 0;
        vulcanSneakReleased = true;
        lastSuccessfulPlaceMs = 0L;
        currentMovementLine = null;
        lastPlacedBlocks.clear();
        placementOffsets.clear();
        lastSupportPosition = null;
        lastSupportReference = null;
        lastDirectionAngle = Float.NaN;
    }

    private static List<BlockPos> normalOffsets() {
        List<BlockPos> offsets = new ArrayList<>(18);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                offsets.add(new BlockPos(x, 0, z));
                offsets.add(new BlockPos(x, -1, z));
            }
        }
        offsets.sort(Comparator
            .comparingDouble((BlockPos pos) -> pos.distSqr(BlockPos.ZERO))
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    enum TellyPhase {
        IDLE,
        RUNNING,
        FORWARD_DWELL,
        RECOVERING,
        LAUNCH,
        AIMING,
        RETURNING
    }

    enum TellyLandingTransition {
        DWELL,
        CHAIN
    }

    private enum TellyMotion {
        RELEASED,
        FORWARD,
        HOLD
    }

    private record TellyPlacement(PlacementTarget target, boolean raised) {
    }

    private record TellyFaceSample(
        FaceRect worldFace,
        Vec3 point,
        AutismRotationUtil.Rotation rotation,
        BlockHitResult verifiedHit,
        double angularCost
    ) {
    }

    record TellyGroundSteeringState(boolean active, float offsetDegrees) {
    }

    private record PlacementTarget(
        BlockPos supportBlock,
        BlockPos placedBlock,
        Direction face,
        BlockHitResult hit,
        AutismRotationUtil.Rotation rotation,
        double minPlacementY
    ) {
    }

    private record TargetPlan(BlockPos supportBlock, Direction face) {
    }

    private record FaceSample(FaceRect face, Vec3 point, Direction side) {
    }

    private record FaceRect(Vec3 from, Vec3 to) {
        private FaceRect {
            Vec3 minimum = new Vec3(
                Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z));
            Vec3 maximum = new Vec3(
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
            from = minimum;
            to = maximum;
        }

        static FaceRect fromBox(AABB box, Direction side) {
            return switch (side) {
                case DOWN -> new FaceRect(
                    new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ));
                case UP -> new FaceRect(
                    new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
                case NORTH -> new FaceRect(
                    new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ));
                case SOUTH -> new FaceRect(
                    new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ));
                case WEST -> new FaceRect(
                    new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.minX, box.maxY, box.maxZ));
                case EAST -> new FaceRect(
                    new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
            };
        }

        Vec3 dimensions() {
            return to.subtract(from);
        }

        Vec3 center() {
            return from.lerp(to, 0.5D);
        }

        double area() {
            Vec3 dimensions = dimensions();
            return dimensions.x * dimensions.y
                + dimensions.y * dimensions.z
                + dimensions.x * dimensions.z;
        }

        FaceRect truncateY(double minimumY) {
            return new FaceRect(
                new Vec3(from.x, Math.max(from.y, minimumY), from.z),
                new Vec3(to.x, Math.max(to.y, minimumY), to.z));
        }

        FaceRect trim(double amount) {
            Vec3 inset = dimensions().scale(amount);
            return new FaceRect(from.add(inset), to.subtract(inset));
        }

        FaceRect offset(Vec3 offset) {
            return new FaceRect(from.add(offset), to.add(offset));
        }

        FaceRect clamp(AABB box) {
            return new FaceRect(clampPoint(from, box), clampPoint(to, box));
        }

        List<LineSegment3> edges() {
            Vec3 dimensions = dimensions();
            Vec3 first;
            Vec3 second;
            if (Mth.equal(dimensions.x, 0.0D)) {
                first = new Vec3(0.0D, dimensions.y, 0.0D);
                second = new Vec3(0.0D, 0.0D, dimensions.z);
            } else if (Mth.equal(dimensions.y, 0.0D)) {
                first = new Vec3(dimensions.x, 0.0D, 0.0D);
                second = new Vec3(0.0D, 0.0D, dimensions.z);
            } else if (Mth.equal(dimensions.z, 0.0D)) {
                first = new Vec3(0.0D, dimensions.y, 0.0D);
                second = new Vec3(dimensions.x, 0.0D, 0.0D);
            } else {
                return List.of();
            }

            List<LineSegment3> edges = new ArrayList<>(4);
            if (first.lengthSqr() > GEOMETRY_EPSILON) {
                edges.add(new LineSegment3(from, from.add(first)));
                edges.add(new LineSegment3(to, to.subtract(first)));
            }
            if (second.lengthSqr() > GEOMETRY_EPSILON) {
                edges.add(new LineSegment3(from, from.add(second)));
                edges.add(new LineSegment3(to, to.subtract(second)));
            }
            return edges;
        }

        private static Vec3 clampPoint(Vec3 point, AABB box) {
            return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
        }
    }

    private record InfiniteLine(Vec3 anchor, Vec3 direction) {
        Vec3 pointAt(double parameter) {
            return anchor.add(direction.scale(parameter));
        }
    }

    private record LineSegment3(Vec3 start, Vec3 end) {
        Vec3 direction() {
            return end.subtract(start);
        }

        Vec3 pointAt(double parameter) {
            return start.add(direction().scale(parameter));
        }
    }

    private record NearestPair(Vec3 first, Vec3 second) {
    }

    private record NearestCandidate(Vec3 first, Vec3 second, double distance) {
    }

    private record MovementLine(Vec3 origin, Vec3 direction) {
    }

    private record SupportReference(BlockPos blockPos, double offsetX, double offsetZ) {
    }

    private record SupportCandidate(
        BlockPos blockPos,
        double overlapArea,
        double surfaceDelta,
        double horizontalDistanceSqr
    ) {
    }
}
