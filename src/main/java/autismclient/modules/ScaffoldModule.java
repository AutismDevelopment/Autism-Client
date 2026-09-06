

package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.util.AutismHumanRotation;
import autismclient.util.AutismInputClicker;
import autismclient.util.AutismKeyMappingBridge;
import autismclient.util.AutismRotationUtil;
import autismclient.util.AutismServerRotationView;
import autismclient.util.AutismSharedState;
import autismclient.util.QuantizedRotationSmoother;
import autismclient.util.RegistryListCodec;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.multi.MultiPilot;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
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
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class ScaffoldModule extends Module {
    static final int SLOT_RESET_TICKS = 5;
    static final int ROTATION_RESET_TICKS = 5;
    static final float ROTATION_RESET_THRESHOLD = 2.0F;

    static final float GRIM_AIM_ACCEL_BUDGET = 180.0F;
    static final double MIN_FACE_DISTANCE = 0.0D;
    static final float TIMER_MULTIPLIER = 1.0F;
    static final boolean SWITCH_BACK_DEFAULT = true;
    static final String SWITCH_BACK_TIP = "Restore previous hotbar slot.";

    static final String REMOVE_LIMITS_TIP = "Faster rotations and placements, no climb brake.";
    static final float GRIM_REMOVE_LIMITS_ROTATION_SCALE = 4.0F;

    private static final double FACE_INSET = 0.15D;
    private static final double GEOMETRY_EPSILON = 1.0E-9D;

    static final float GRIM_MAX_PITCH_STEP = 20.0F;

    static final float GRIM_PLACE_MAX_PITCH_STEP = 10.0F;

    private static final int GRIM_BODY_CLEAR_LOOKAHEAD_TICKS = 2;

    private static final int GRIM_FACE_PLANE_LEAD_TICKS = 4;

    private static final double TRAJECTORY_DRIFT_LEAD_TICKS = 2.0D;
    private static final double TRAJECTORY_DRIFT_MIN_SPEED = 0.05D;

    private static final double GRIM_GUARD_MAX_DISTANCE = 2.0D;
    private static final double GRIM_GUARD_SEED_BUCKET = 0.5D;

    private static final double COURSE_STABILIZER_MIN_SPEED = 0.05D;

    private static final double GRIM_LANE_INPUT_LOOKAHEAD = 1.1D;
    private static final double GRIM_LANE_INPUT_PREDICT_TICKS = 4.0D;
    static final float GRIM_LANE_INPUT_MAX_DEGREES = 38.0F;

    private static final double GRIM_LANE_INPUT_DEADBAND = 0.22D;

    static final double GRIM_LANE_CORRECT_ENGAGE = 0.15D;

    static final double GRIM_LANE_CORRECT_RELEASE = 0.06D;

    static final int GRIM_LANE_CORRECT_MAX_HOLD_TICKS = 2;

    static final int GRIM_LANE_CORRECT_RELOCK_TICKS = 3;

    static final double GRIM_LANE_CORRECT_SETTLE_LEAD = 2.2D;

    static final double GRIM_LANE_INPUT_LOOKAHEAD_LEGIT = 0.23D;

    private static final float GRIM_STEP_HOLD_DEGREES = 37.5F;

    private static final int GRIM_STEP_COMMIT_TICKS = 2;

    static final int COURSE_STEP_UNSET = -1;

    static final int GRIM_INPUT_SIDEWAYS_MIN_HOLD = 4;

    static final float GRIM_INPUT_OCTANT_BOUNDARY_DEGREES = 30.0F;

    static final float GRIM_INPUT_SIDEWAYS_BREAK_DEGREES = GRIM_INPUT_OCTANT_BOUNDARY_DEGREES + 6.0F;

    static final float GRIM_INPUT_REFERENCE_BREAK_DEGREES = 22.5F;
    private static final double GRIM_INPUT_OCTANT_ENTER = 0.62D;
    private static final double GRIM_INPUT_OCTANT_EXIT = 0.38D;

    static final float GRIM_LANE_OCTANT_MAX_RESIDUAL = 12.0F;

    static final float GRIM_LANE_SWEEP_SETTLED_DEGREES = 2.0F;

    private static final int GRIM_LANE_SWEEP_MAX_TICKS = 8;

    static final int GRIM_YAW_VETO_RETIRE_TICKS = 2;

    private static final int GRIM_LANE_COAST_MAX_TICKS = 6;

    static final double GRIM_FOOTING_OWED_OVERLAP = 0.30D;

    private static final int GRIM_FOOTING_OWED_MAX_TICKS = 10;

    private static final VoxelShape STANDABLE_CENTER_SHAPE = Block.column(2.0D, 0.0D, 10.0D);

    private static final double GRIM_LATERAL_BRINK_OFFSET = 0.45D;

    private static final double GRIM_COURSE_DIVERGENCE_DEGREES = 60.0D;
    private static final double GRIM_LATERAL_BRINK_PROBE = 0.35D;

    private static final double GRIM_BOX_HALF_WIDTH = 0.3D;
    private static final int GRIM_RISE_COLUMN_LEAD_TICKS = 3;
    private static final double GRIM_RISE_COLUMN_MIN_OVERLAP = 0.15D;

    private static final double GRIM_BEHIND_MARGIN = 0.8D;

    private static final int GRIM_AIM_HOLD_MAX_TICKS = 3;

    private static final int GRIM_BRIDGING_RECENT_TICKS = 20;

    private static final int GRIM_ROW_LOCK_TICKS = 10;

    private static final int GRIM_CLIMB_CONTINUATION_TICKS = 30;

    private static final int GRIM_STICKY_MAX_TICKS = 6;

    private static final double GRIM_AIM_CENTER_WINDOW = 0.2D;

    static final float GRIM_MAX_YAW_STEP = 18.0F;

    static final float GRIM_ANGLE_SNAP_MARGIN = 0.20F;

    private static final int GRIM_YAW_FIX_MAX_COUNTS = 64;

    static final float GRIM_PLACE_MAX_PITCH = 89.0F;
    static final float GRIM_PLACE_MAX_PITCH_HARD = 89.5F;

    private static final int GRIM_WIND_DOWN_SLACK_TICKS = 4;
    private static final int GRIM_WIND_DOWN_MIN_TICKS = 8;

    private static final int GRIM_WIND_DOWN_MAX_TICKS = 27;

    static final float GRIM_WIND_DOWN_MAX_YAW_STEP = 37.0F;
    static final float GRIM_WIND_DOWN_MAX_PITCH_STEP = 37.0F;

    static final float GRIM_WIND_DOWN_FIRST_STEP_MAX = 20.0F;

    private static final int GRIM_DESCENT_LOOKAHEAD_TICKS = 12;

    private static final double GRIM_AIR_COUNTER_IMPULSE = 0.02D;

    static final double GRIM_HORIZONTAL_ZERO_THRESHOLD = 0.003D;

    private static final double GRIM_LANDING_HALF_WIDTH = 0.29D;

    private static final double GRIM_LANDING_MIN_OVERLAP = 0.15D;

    private static final int GRIM_LANDING_MAX_AIR_TICKS = 20;

    static final double GRIM_LEG_SWAP_MARGIN = 0.25D;

    private static final double GRIM_FREE_AIM_BEARING_MIN_RUN = 0.75D;

    private static final double GRIM_ARC_BRAKE_MIN_LANE_SPEED = 0.03D;

    private static final double GRIM_XING_STANDDOWN_MAX_TRAVEL = 0.35D;

    private static final double GRIM_OCCLUDER_PLANE_EPS = 0.01D;

    private static final int GRIM_ARC_BRAKE_LOOKAHEAD_TICKS = 24;

    private static final double GRIM_JUMP_TAKEOFF_VELOCITY = 0.42D;

    private static final double GRIM_RISE_TAKEOFF_REACH = 1.7D;

    private static final int GRIM_AIM_MISS_FALLBACK_TICKS = 3;

    private static final int GRIM_AIM_WINDOW_WAIT_MAX_TICKS = 6;

    private static final double GRIM_SUPPORT_MAX_DISTANCE = 2.5D;

    private static final int GRIM_RISE_TAKEOFF_LATCH_TICKS = 4;

    static final int GRIM_PLACEMENT_RECONCILE_TICKS = 2;

    private static final int GRIM_PREDICTION_HISTORY = 64;

    private static final int GRIM_INTAVE_SNEAK_MEMORY_TICKS = 150;

    private static final int GRIM_PACE_SAMPLES = 8;

    private static final long GRIM_PACE_CROSS_Y_MS = 1000L;

    static final long GRIM_INTAVE_PLACE_MEAN_MS = 400L;

    static final long GRIM_INTAVE_PLACE_SAMPLE_CAP_MS = 1000L;

    static final float GRIM_INTAVE_PLACE_PITCH_MIN = 85.0F;

    static final float GRIM_INTAVE_FLICK_DIFF_MIN = 3.0F;
    static final float GRIM_INTAVE_FLICK_DIFF_MAX = 20.0F;
    static final float GRIM_INTAVE_FLICK_PITCH_MIN = 70.0F;

    static final long GRIM_INTAVE_FLICK_GAP_MS = 800L;

    static final int GRIM_INTAVE_FLICK_CELLS = 5;
    static final long GRIM_INTAVE_FLICK_CELL_TTL_MS = 5000L;

    static final float GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN = 0.5F;

    static final float GRIM_INTAVE_ROTATION_SAFE_PITCH =
        GRIM_INTAVE_PLACE_PITCH_MIN - GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN;

    static final int GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS = 2;

    static final int GRIM_INTAVE_ROTATION_PARK_MAX_TICKS = 3;

    static final int GRIM_INTAVE_PARK_LOOKAHEAD_GROUNDED_TICKS = 5;
    static final int GRIM_INTAVE_PARK_MAX_GROUNDED_TICKS = 6;

    private static final long GRIM_PACE_RECENT_JUMP_WINDOW_MS = 750L;

    private static final double GRIM_PACE_SAFETY = 1.08D;

    static final int GRIM_PACE_JITTER_MS = 25;

    static final double GRIM_FACE_OVERHEAD_MARGIN = 1.0D;

    static final double GRIM_SNEAK_INPUT_SCALE = 0.3D;

    static final double GRIM_OWN_RISER_MAX_CARRY = 0.80D;

    static final double GRIM_PACE_BRINK_OVERLAP = 0.12D;

    static final double GRIM_PACE_ACK_LEAD_OVERLAP = 0.30D;

    private static final int GRIM_SNEAK_REFRESH_TICKS = 90;

    private static final int GRIM_SNEAK_MIN_HOLD_TICKS = 2;

    private static final int GRIM_BRIDGE_ACTIVE_TICKS = 40;
    private static final float PREAIM_PITCH_MIN = 68.0F;
    private static final float PREAIM_PITCH_SPAN = 12.0F;
    private static final double PREAIM_MIN_SPEED = 0.03D;
    private static final double[] PREAIM_EDGE_DISTANCES = {1.5D, 2.0D, 2.5D};

    static final int GRIM_BRIDGE_HOLD_TICKS = 20;

    private static final float TELLY_LOOK_OFFSET_MIN = 0.18F;
    private static final float TELLY_LOOK_OFFSET_SPAN = 0.22F;
    private static final double[] SUPPORT_SAMPLES = {0.301D, 0.0D, -0.301D};
    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final int MAX_PLACEMENT_OFFSETS = 4;
    private static final double SUPPORT_SURFACE_EPSILON = 1.0E-3D;
    private static final double SUPPORT_OVERLAP_HYSTERESIS = 0.02D;
    private static final double PREDICTION_BACKOFF = 0.2D;
    private static final double PREDICTION_CUTOFF_DISTANCE = 0.05D;
    private static final double PREDICTION_LINE_LENGTH = 3.0D;
    private static final double GRIM_EDGE_TARGET_EPSILON = 1.0E-4D;

    static final double GRIM_GOAL_EYE_LEAD = 0.25D;

    static final double GRIM_PIN_CROSS_DEPTH = 0.5D;

    static final double GRIM_PIN_ACQUIRE_MARGIN = 0.10D;
    static final double GRIM_PIN_RELEASE_MARGIN = 0.02D;

    static final double GRIM_PIN_SIDE_ACQUIRE_MARGIN = 0.04D;

    static final double GRIM_PIN_CORNER_ACQUIRE_MARGIN = -0.12D;

    static final double GRIM_PIN_CORNER_RELEASE_MARGIN = -0.14D;

    static final double GRIM_PIN_DIAGONAL_LOOK_TOLERANCE = 0.05D;

    static final int GRIM_PIN_STALE_TICKS = 14;

    static final double GRIM_PIN_SIDE_RELEASE_MARGIN = -0.12D;

    static final int GRIM_PIN_LOOKAHEAD_TICKS = 2;

    static final int GRIM_GRID_FLIP_MAX_STEPS = 2;

    static final double GRIM_FACE_TOWARD_TRACK_MIN = 0.25D;

    private static final double GRIM_LANE_AIR_DRAG = 0.91D;

    private static final double GRIM_GROUND_TAKEOFF_DRAG = 0.546D;

    private static final double GRIM_GROUND_WALK_ACCEL = 0.096D;
    private static final double GRIM_COURSE_DOT_EPSILON = 0.999D;
    private static final int PREDICTION_WARMUP_PLACEMENTS = 2;
    private static final List<BlockPos> NORMAL_OFFSETS = normalOffsets();

    static final int RAGE_BLOCKS_MAX = 5;
    static final int RAGE_BLOCKS_DEFAULT = 0;

    private static final boolean TELLY_LIVE_TRACE = true;

    private static final boolean DEBUG_LOGS = false;

    private int originalSlot = -1;
    private int requestedSlot = -1;

    private int switchedToSlot = -1;
    private int slotResetTicks;
    private boolean selectionPending;
    private AutismRotationUtil.Rotation serverRotation;
    private MovementLine currentMovementLine;
    private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED_BLOCKS);
    private final ArrayDeque<Vec3> placementOffsets = new ArrayDeque<>(MAX_PLACEMENT_OFFSETS + 1);
    private BlockPos lastSupportPosition;
    private SupportReference lastSupportReference;

    private int supportMissTicks;
    private int supportMissClientTick = Integer.MIN_VALUE;

    private MovementLine grimEdgeLockedLine;

    private int grimCourseStep = COURSE_STEP_UNSET;

    private int grimCourseStepCandidate = COURSE_STEP_UNSET;
    private int grimCourseStepDwell;

    private int grimLaneOctant;

    private float grimPostureYawHeld = Float.NaN;
    private float grimPostureYawCandidate = Float.NaN;
    private int grimPostureYawStreak;

    private int grimPostureYawTick = Integer.MIN_VALUE;
    private String cachedFilterRaw = "";
    private Set<Block> cachedFilterBlocks = Set.of();
    private AutismRotationUtil.Rotation grimSilentRotation;
    private int grimRotationResetTicks;

    private int grimWindDownTicks;

    private final QuantizedRotationSmoother grimAimSmoother = new QuantizedRotationSmoother();
    private AutismRotationUtil.Rotation grimAimPrevGoal;
    private float grimAimDirectionChange;
    private final AutismHumanRotation.Stream tellyStream = new AutismHumanRotation.Stream();
    private int grimRotationStepTick = Integer.MIN_VALUE;

    private int lastGrimPlacementTick = Integer.MIN_VALUE;

    private final Random rotationRandom = new Random();

    private boolean grimWindingDown;

    private boolean grimEdgeSneakActive;

    private float sessionPitchOffset = PREAIM_PITCH_MIN + PREAIM_PITCH_SPAN * 0.5F;

    private float tellyLookYawOffset;

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

    private boolean tellyAimCommitted;
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

    private float tellyLaneBias;
    private float tellyGroundSteerOffset;

    private TellyStrafe tellyAirStrafeThisTick = TellyStrafe.NONE;
    private TellyStrafe tellyAirLastStrafe = TellyStrafe.NONE;
    private int tellyAirStrafeCooldown;
    private int tellyAirStrafePulses;
    private int tellyCourseDeviationTicks;
    private int tellyEdgeHoldTicks;
    private boolean tellyRotationHeldForPlacement;

    private int tellyRotationStepTick = Integer.MIN_VALUE;

    private int tellyFaceOffsetIndex = -1;

    private boolean tellyReturnCompleted = true;
    private int tellyHoldWatchdogTicks;
    private boolean tellyGroundLaunchAllowed;

    private AutismRotationUtil.Rotation tellySmoothedRotation;
    private boolean tellyTurnSettling;
    private int tellySettleHoldTicks;
    private int tellySettleDwellTicks;
    private BlockPos tellyLastBridge;

    private BlockPos tellyLastGroundedSupport;
    private int tellyLastGroundedTick = Integer.MIN_VALUE;
    private BlockPos tellyRaisedCell;
    private BlockPos tellyQueuedBlock;

    private BlockPos tellyTurnReserveSupport;
    private BlockPos tellyTurnReserveCell;
    private boolean tellyTurnReserveQueued;
    private Vec3 tellyLineOrigin;
    private TellyPlacement tellyTarget;
    private long tellyCycleSerial;

    ScaffoldModule() {
        super("scaffold", "Scaffold", ModuleCategory.MOVEMENT, "Places blocks beneath you.");
        add(new ChoiceSetting("mode", "Mode", "Legit", "Legit", "Rage", "Telly")
            .description("Choose scaffold mode.")
            .build());
        add(new BoolSetting("remove-limits", "Remove Limits", false)
            .visibleWhen(this::isGrimMode)
            .description(REMOVE_LIMITS_TIP)
            .build());
        add(new BoolSetting("switch-back", "Switch Back", SWITCH_BACK_DEFAULT)
            .description(SWITCH_BACK_TIP)
            .build());
        add(new ChoiceSetting("filter-mode", "Filter", "Off", "Off", "Whitelist", "Blacklist")
            .description("Choose filter mode.")
            .build());
        add(new IntSetting("rage-blocks", "Blocks Ahead", RAGE_BLOCKS_DEFAULT, 0, RAGE_BLOCKS_MAX, 1)
            .visibleWhen(this::isRageMode)
            .description("Blocks built ahead.")
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
        rollGrimSessionOffsets();
        grimLiveTraceTicks = DEBUG_LOGS ? 0 : -1;
        tellyLiveTraceTicks = tellyTraceArmTicks();
        tellyTracePrintedTick = Integer.MIN_VALUE;
        grimTracePrevYaw = Float.NaN;
        grimTracePrevPitch = Float.NaN;
        grimTraceYawSum = 0.0D;
        grimTracePitchSum = 0.0D;
        grimTraceMoveTicks = 0;
        pushAnimation();
    }

    @Override
    public String info() {
        return choice("mode");
    }

    @Override
    public void onDisable() {

        AutismRotationUtil.Rotation live = grimSilentRotation;
        AutismRotationUtil.Rotation liveRaw = grimAimRaw;

        float liveSent = grimSentYaw;
        boolean tellyLive = AutismHumanRotation.isInitialized(tellyStream);

        clearRuntime(false);
        if (live != null && MC != null && MC.player != null) {
            if (tellyLive) AutismHumanRotation.seed(tellyStream, live);
            grimSilentRotation = live;
            grimAimRaw = liveRaw != null ? liveRaw : live;
            grimSentYaw = liveSent;
            grimRotationResetTicks = ROTATION_RESET_TICKS;
            grimWindingDown = true;
        }
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
        if ("mode".equals(settingId)) {
            clearRuntime(true);
            grimLiveTraceTicks = DEBUG_LOGS ? 0 : -1;

            tellyLiveTraceTicks = tellyTraceArmTicks();
            tellyTracePrintedTick = Integer.MIN_VALUE;
        }
        pushAnimation();
    }

    private int tellyTraceArmTicks() {
        return TELLY_LIVE_TRACE && isTellyMode() && DEBUG_LOGS ? 0 : -1;
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

            snapshotGrimTickStep();
            runTellyTick();
        } else if (isGrimFamily()) {

            ensureGrimPredictionLevel();
            grimTraceSettledCell = null;
            grimTickArcBudget();
            settleGrimRealClick();
            snapshotGrimTickStep();
            runGrimPlacement();
        } else {
            runRagePlacement();
        }
    }

    private static final String[] COURSE_STEP_NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    static int compassStep(float yaw) {
        int step = Math.round(Mth.wrapDegrees(yaw) / 45.0F);
        return ((step % 8) + 8) % 8;
    }

    static float compassStepYaw(int step) {
        return Mth.wrapDegrees(step * 45.0F);
    }

    static boolean compassStepIsDiagonal(int step) {
        return step >= 0 && (step & 1) == 1;
    }

    static int[] nextCourseStep(int held, int candidate, int dwell, float cameraYaw, boolean frozen) {
        int want = compassStep(cameraYaw);

        if (held == COURSE_STEP_UNSET) return new int[] {want, COURSE_STEP_UNSET, 0};

        if (frozen) return new int[] {held, candidate, dwell};
        if (want == held) return new int[] {held, COURSE_STEP_UNSET, 0};
        if (Math.abs(Mth.wrapDegrees(cameraYaw - compassStepYaw(held))) <= GRIM_STEP_HOLD_DEGREES) {

            return new int[] {held, COURSE_STEP_UNSET, 0};
        }
        int nextDwell = want == candidate ? dwell + 1 : 1;
        if (nextDwell >= GRIM_STEP_COMMIT_TICKS) return new int[] {want, COURSE_STEP_UNSET, 0};
        return new int[] {held, want, nextDwell};
    }

    static float[] nextPostureYaw(float held, float candidate, int streak, float requested) {
        if (Float.isNaN(held)) return new float[] {requested, Float.NaN, 0};
        if (requested == held) return new float[] {held, Float.NaN, 0};
        int nextStreak = requested == candidate ? streak + 1 : 1;
        if (nextStreak >= GRIM_STEP_COMMIT_TICKS) return new float[] {requested, Float.NaN, 0};
        return new float[] {held, requested, nextStreak};
    }

    private void updateGrimCourseStep() {
        if (MC.player == null) return;
        int[] next = nextCourseStep(
            grimCourseStep, grimCourseStepCandidate, grimCourseStepDwell,
            MC.player.getYRot(), grimCourseFrozen());
        grimCourseStep = next[0];
        grimCourseStepCandidate = next[1];
        grimCourseStepDwell = next[2];
    }

    private float grimCourseStepYaw() {
        return grimCourseStep == COURSE_STEP_UNSET
            ? (MC.player == null ? 0.0F : MC.player.getYRot())
            : compassStepYaw(grimCourseStep);
    }

    static float inputOctantDegrees(Input input) {
        float yaw = 0.0F;
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

    static int inputOctantSteps(Input input) {
        if (!hasDirectionalInput(input)) return 0;
        int steps = Math.round(inputOctantDegrees(input) / 45.0F);
        return ((steps % 8) + 8) % 8;
    }

    static int laneStep(int courseStep, Input input) {
        return laneStep(courseStep, inputOctantSteps(input));
    }

    static int laneStep(int courseStep, int octantSteps) {
        if (courseStep == COURSE_STEP_UNSET) return COURSE_STEP_UNSET;
        return (((courseStep + octantSteps) % 8) + 8) % 8;
    }

    private void updateGrimLaneOctant(Input input) {
        if (!hasDirectionalInput(input) || grimCourseFrozen()) return;
        grimLaneOctant = inputOctantSteps(input);
    }

    private int grimLaneStep() {
        return laneStep(grimCourseStep, grimLaneOctant);
    }

    private float grimLaneStepYaw() {
        int lane = grimLaneStep();
        return lane == COURSE_STEP_UNSET ? grimCourseStepYaw() : compassStepYaw(lane);
    }

    private Vec3 grimLaneStepDirection() {
        return Vec3.directionFromRotation(0.0F, grimLaneStepYaw());
    }

    private float grimSteeredPostureYaw() {

        if (grimTowerActive() && grimLaneStep() == COURSE_STEP_UNSET) {
            return grimSilentRotation != null ? grimSilentRotation.yaw() : MC.player.getYRot();
        }
        float requested = grimPlacementPostureYaw(grimLaneStepYaw());
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != grimPostureYawTick) {
            float[] next = nextPostureYaw(
                grimPostureYawHeld, grimPostureYawCandidate, grimPostureYawStreak, requested);
            grimPostureYawHeld = next[0];
            grimPostureYawCandidate = next[1];
            grimPostureYawStreak = (int) next[2];
            grimPostureYawTick = tick;
        }
        return grimPostureYawHeld;
    }

    static Vec3 tellyTickStep(Vec3 previous, Vec3 current) {
        return previous == null ? Vec3.ZERO : current.subtract(previous);
    }

    private void snapshotGrimTickStep() {
        if (MC.player == null) return;
        Vec3 pos = MC.player.position();
        grimLastTickStep = tellyTickStep(grimPrevTickPos, pos);
        grimPrevTickPos = pos;

        grimTraceRiseAllowed = false;
    }

    private Vec3 grimLeadStep() {
        if (MC.player == null) return Vec3.ZERO;
        if (!MC.player.onGround()) {
            Vec3 delta = MC.player.getDeltaMovement();
            int tick = AutismSharedState.get().getClientTickCounter();
            if (tick != grimAirLeadTick) {
                grimAirLeadTick = tick;
                Input keys = MC.player.input == null ? null : MC.player.input.keyPresses;
                boolean directional = hasDirectionalInput(keys);

                boolean braking = directional
                    && (grimArcBrake(true, false) || grimDiagonalClimbBrake(true, false));
                grimAirLeadAccel = grimAirLeadAccel(delta, directional, braking,
                    keys != null && keys.shift());
            }
            return delta.add(grimAirLeadAccel);
        }
        return new Vec3(grimLastTickStep.x, 0.0D, grimLastTickStep.z);
    }

    private int grimAirLeadTick = Integer.MIN_VALUE;
    private Vec3 grimAirLeadAccel = Vec3.ZERO;

    static Vec3 grimAirLeadAccel(
        Vec3 velocity, boolean directional, boolean braking, boolean sneaking
    ) {
        double speed = Math.hypot(velocity.x, velocity.z);
        if (!directional || speed < GRIM_HORIZONTAL_ZERO_THRESHOLD) return Vec3.ZERO;
        double impulse = GRIM_AIR_COUNTER_IMPULSE * (sneaking ? 0.3D : 1.0D) / speed;
        if (braking) impulse = -impulse;
        return new Vec3(velocity.x * impulse, 0.0D, velocity.z * impulse);
    }

    private void runRagePlacement() {
        if (!canRun()) {
            currentMovementLine = null;
            tickSlotReset();
            return;
        }

        if (AutismBlinkManager.holdsActionsWithoutMovement()) return;
        selectionPending = false;
        InteractionHand hand = ensurePlacementHand();
        if (hand == null) {
            if (selectionPending) refreshSelectionReset();
            else tickSlotReset();
            return;
        }
        refreshSelectionReset();
        ItemStack stack = MC.player.getItemInHand(hand);
        if (!isValidBlock(stack)) return;

        BlockPos base = rageWalkwayCell();
        if (base == null) return;
        BlockPos step = rageCourseStep();

        int ahead = step == null ? 0 : Math.max(0, Math.min(RAGE_BLOCKS_MAX, integer("rage-blocks")));

        if (!autismclient.util.AutismPlacementTick.claim(id())) return;
        for (BlockPos cell : rageLaneCells(base, step, ahead, this::solidAt)) {
            placeRageCell(cell, hand, stack);
        }
    }

    static List<BlockPos> rageLaneCells(
        BlockPos base, BlockPos step, int ahead, Predicate<BlockPos> solid
    ) {
        List<BlockPos> cells = new ArrayList<>();
        if (base == null) return cells;
        if (step == null) {
            cells.add(base);
            return cells;
        }
        boolean diagonal = step.getX() != 0 && step.getZ() != 0;
        for (int i = 0; i <= Math.max(0, ahead); i++) {
            BlockPos lane = base.offset(step.getX() * i, 0, step.getZ() * i);
            cells.add(lane);

            if (!diagonal || i == 0) continue;
            BlockPos alongX = lane.offset(-step.getX(), 0, 0);
            BlockPos alongZ = lane.offset(0, 0, -step.getZ());
            if (!solid.test(alongX) && !solid.test(alongZ)) cells.add(alongX);
        }
        return cells;
    }

    private BlockPos rageWalkwayCell() {
        Vec3 vec = MC.player.position().add(MC.player.getDeltaMovement()).add(0.0D, -0.75D, 0.0D);
        BlockPos cell = BlockPos.containing(vec.x, vec.y, vec.z);
        int footY = MC.player.blockPosition().getY();
        if (cell.getY() >= footY) cell = new BlockPos(cell.getX(), footY - 1, cell.getZ());
        return MC.level.isOutsideBuildHeight(cell) ? null : cell;
    }

    private BlockPos rageCourseStep() {
        if (MC.options == null) return null;
        Vec3 look = Vec3.directionFromRotation(0.0F, MC.player.getYRot());
        double x = 0.0D;
        double z = 0.0D;
        if (physicallyDown(MC.options.keyUp)) { x += look.x; z += look.z; }
        if (physicallyDown(MC.options.keyDown)) { x -= look.x; z -= look.z; }
        if (physicallyDown(MC.options.keyLeft)) { x += look.z; z -= look.x; }
        if (physicallyDown(MC.options.keyRight)) { x -= look.z; z += look.x; }
        return rageStepFromDirection(x, z);
    }

    static BlockPos rageStepFromDirection(double x, double z) {
        if (x * x + z * z <= 1.0E-8D) return null;
        double yaw = Math.toRadians(compassStepYaw(
            compassStep((float) Math.toDegrees(Math.atan2(-x, z)))));
        int stepX = (int) Math.round(-Math.sin(yaw));
        int stepZ = (int) Math.round(Math.cos(yaw));
        return stepX == 0 && stepZ == 0 ? null : new BlockPos(stepX, 0, stepZ);
    }

    private boolean placeRageCell(BlockPos cell, InteractionHand hand, ItemStack stack) {
        if (cell == null || MC.level.isOutsideBuildHeight(cell)) return false;
        BlockState state = MC.level.getBlockState(cell);
        if (!state.canBeReplaced() || isSolidSupport(state, cell)) return false;

        if (!grimCellClearOfBody(MC.player.getBoundingBox(),
            MC.player.getDeltaMovement(), cell)) {
            return false;
        }

        Direction side = ragePlaceSide(cell);
        if (side == null) return false;
        BlockPos neighbour = cell.relative(side);

        Vec3 hitPos = grimFaceCentre(grimSupportBox(neighbour), side.getOpposite());
        Vec3 eye = MC.player.getEyePosition();
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        if (eye.distanceToSqr(hitPos) > reach * reach) return false;

        BlockHitResult hit = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

        if (ModuleRegistry.shouldCancelUseExcept(hit, hand, id())) return false;
        PlacementTarget target = new PlacementTarget(
            neighbour, cell, side.getOpposite(), hit,
            AutismRotationUtil.lookingAt(hitPos, eye), cell.getY());
        return place(target, hand, stack);
    }

    private Direction ragePlaceSide(BlockPos cell) {
        Vec3 look = Vec3.atCenterOf(cell).subtract(MC.player.getEyePosition());
        double bestRelevancy = -Double.MAX_VALUE;
        Direction best = null;
        for (Direction side : Direction.values()) {
            BlockPos neighbour = cell.relative(side);
            if (MC.level.isOutsideBuildHeight(neighbour)) continue;
            BlockState state = MC.level.getBlockState(neighbour);
            if (state.canBeReplaced() || !state.getFluidState().isEmpty()) continue;
            double relevancy = side.getAxis().choose(look.x, look.y, look.z)
                * side.getAxisDirection().getStep();
            if (relevancy > bestRelevancy) {
                bestRelevancy = relevancy;
                best = side;
            }
        }
        return best;
    }

    private void runGrimPlacement() {
        if (!canRun()) {
            currentMovementLine = null;
            grimEdgeSneakActive = false;
            grimEdgeLockedLine = null;

            advanceGrimIdleStream(false);
            tickSlotReset();
            return;
        }

        grimTraceClickLands = false;

        grimTracePaceBrink = false;
        grimTraceLastChance = false;
        grimTracePaceIntave = false;
        if (grimAttemptBlocksRearm()) {
            grimTraceWhy = "attempt-" + grimAttemptState.name().toLowerCase(java.util.Locale.ROOT);
            traceGrim("real-wait", grimRealPendingTarget);
            if (grimRealPendingTarget != null) {
                advanceGrimRotation(grimAimGoalWithMissFallback(grimRealPendingTarget));
            } else {
                advanceGrimNoTarget();
            }
            return;
        }

        if (AutismBlinkManager.holdsActionsWithoutMovement()) {
            grimTraceWhy = "blink-action-only";
            traceGrim("real-wait", grimRealPendingTarget);
            tickSlotReset();
            return;
        }

        grimEffectiveFootCell();
        ItemStack planningStack = planningStack();
        PlacementTarget target = findPlacementTarget(planningStack);
        if (target == null) {
            grimNoTargetTicks++;
            grimNotePin(null);
            grimTraceWhy = "plan";
            traceGrim("no-target " + grimLastPlanFail, null);
            advanceGrimNoTarget();
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

            grimNotePin(target);
            grimTraceWhy = "hand";
            traceGrim("hand-switch", target);
            advanceGrimRotation(target.rotation());
            return;
        }

        refreshSelectionReset();
        ItemStack stack = MC.player.getItemInHand(hand);
        target = findPlacementTarget(stack);
        if (target == null) {
            grimNoTargetTicks++;
            grimNotePin(null);
            grimTraceWhy = "plan";
            traceGrim("no-target-2 " + grimLastPlanFail, null);
            advanceGrimNoTarget();
            return;
        }
        grimNoTargetTicks = 0;

        if (grimRiseDropApplies(target)) {
            grimStickyTarget = null;
            grimNotePin(null);

            boolean droppedTopHasASide = false;
            if (target.face() == Direction.UP) {
                PlacementTarget viaSide = grimRiserSideFallback(target.placedBlock(),
                    target.supportBlock(), predictedPlacementPosition(currentMovementLine), stack,
                    currentMovementLine == null ? null : currentMovementLine.direction());
                if (viaSide != null && !grimRiseDropApplies(viaSide)) {
                    target = viaSide;
                    grimSegReplan++;
                    droppedTopHasASide = true;
                }
            }

            if (droppedTopHasASide) {

            } else if (grimRiseDropReselects(MC.player.onGround(), grimJumpKeyHeld())) {
                grimMarkCellDead(target.placedBlock());
                PlacementTarget replanned = findPlacementTarget(stack);
                if (replanned != null && !grimRiseDropApplies(replanned)) {
                    target = replanned;
                    grimSegReplan++;
                } else {
                    grimTraceWhy = "drop-" + grimTraceRiseDropWhy;
                    traceGrim("rise-drop", target);
                    advanceGrimRotation(grimRestPoseGoal());
                    return;
                }
            } else {
                grimTraceWhy = "drop-" + grimTraceRiseDropWhy;
                traceGrim("rise-drop", target);

                advanceGrimRotation(grimRestPoseGoal());
                return;
            }
        }
        grimNotePin(target);
        grimBridgePitchHold = target.rotation().pitch();

        GrimWireClickRotation wireClick = grimWireClickRotation();
        AutismRotationUtil.Rotation clickRotation = wireClick == null ? null : wireClick.current();
        boolean rayLands = clickRotation != null && grimRealClickLands(target, clickRotation);
        PlacementTarget laneRay = null;
        if (clickRotation != null && !rayLands) {

            laneRay = grimLaneRayTarget(target, grimLastArmRay);

            if (laneRay != null && grimRiseDropApplies(laneRay)) laneRay = null;
        }

        PlacementTarget clickable = rayLands ? target : laneRay;
        boolean paceFreesPitch = false;
        if (clickable != null && grimPaceHolds(clickable, clickRotation)) {
            paceFreesPitch = true;
            grimPitchFreedTick = AutismSharedState.get().getClientTickCounter();
        }

        advanceGrimRotation(grimAimGoalWithMissFallback(target),
            grimWirePitchFrozen(rayLands || laneRay != null, paceFreesPitch));
        if (wireClick == null) {
            grimTraceWhy = "wire-wait";
            traceGrim("aim-hold miss=no-wire", target);
            return;
        }
        if (!rayLands) {
            if (laneRay != null) {
                target = laneRay;
                grimNotePin(target);
            } else {

                if (grimTargetOutOfReach(target)) grimNoTargetTicks = Math.max(grimNoTargetTicks, 1);
                grimNoteAimMiss(target, clickRotation);
                String waitCode = grimAimWaitCode(target);

                grimNoteCrossingWait(target, waitCode);
                grimTraceWhy = "aim-" + waitCode;
                traceGrim("aim-hold" + traceClickMiss(target), target);
                return;
            }
        }

        if (clickRotation != null) {
            float emittedPitch = clickRotation.pitch();
            if (!grimPlacementPitchLegal(emittedPitch)) {
                grimTraceWhy = "pveto-" + (int) emittedPitch;
                traceGrim("pitch-veto", target);
                return;
            }

            if (grimYawOffPosture(clickRotation)) {

                if (++grimYawVetoTicks > GRIM_YAW_VETO_RETIRE_TICKS) {
                    grimMarkCellDead(target.placedBlock());
                    grimStickyTarget = null;
                    grimYawVetoTicks = 0;
                }
                float yawResidual = grimLaneStep() == COURSE_STEP_UNSET
                    ? Float.NaN
                    : grimLaneOctantResidual(grimLaneStepYaw(), clickRotation.yaw());
                grimTraceWhy = "yawoff-" + (Float.isNaN(yawResidual) ? "?"
                    : String.format(java.util.Locale.ROOT, "%.1f", yawResidual));
                traceGrim("yaw-veto", target);
                return;
            }
            grimYawVetoTicks = 0;

            float goalErr = Math.abs(Mth.wrapDegrees(
                target.rotation().yaw() - clickRotation.yaw()));
            if (target.face().getAxis().isHorizontal()
                && grimGoalYawUnconverged(target.rotation().yaw(), clickRotation.yaw())) {
                boolean closing = grimGoalErrorClosing(goalErr, grimGoalVetoLastErr);
                grimGoalVetoLastErr = goalErr;
                grimGoalVetoTicks = closing ? 0 : grimGoalVetoTicks + 1;
                if (grimGoalVetoTicks > GRIM_YAW_VETO_RETIRE_TICKS) {
                    grimMarkCellDead(target.placedBlock());
                    grimStickyTarget = null;
                    grimGoalVetoTicks = 0;
                    grimGoalVetoLastErr = Float.NaN;
                }
                grimTraceWhy = "goaloff-" + (int) goalErr;
                traceGrim("yaw-veto", target);
                return;
            }
            grimGoalVetoTicks = 0;
            grimGoalVetoLastErr = Float.NaN;

            float clickPitchStep = wireClick.previous() == null ? 0.0F
                : Math.abs(clickRotation.pitch() - wireClick.previous().pitch());
            boolean descending = !MC.player.onGround()
                && MC.player.getDeltaMovement().y < 0.0D;
            if (!descending && clickPitchStep > GRIM_PLACE_MAX_PITCH_STEP) {
                grimTraceWhy = "pstep-" + (int) clickPitchStep;
                traceGrim("pitch-step", target);
                return;
            }
        }

        grimAimMissStreak = 0;
        grimAimWindowWaitTicks = 0;

        grimAimOccludedTicks = 0;

        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == lastGrimPlacementTick) {
            grimTraceWhy = "sametick";
            traceGrim("pace-hold", target);
            return;
        }

        boolean pitchFreed = grimPitchFreedTick == tick;
        if (paceFreesPitch || pitchFreed || grimPaceHolds(target, clickRotation)) {

            if (target.placedBlock().getY() > target.supportBlock().getY()
                || grimLandingChainCell(target.placedBlock())) {
                grimPaceRiserHoldCell = target.placedBlock().immutable();
                grimPaceRiserHoldTick = AutismSharedState.get().getClientTickCounter();
            }
            grimTraceWhy = !paceFreesPitch && pitchFreed
                ? "pace-pfree"
                : "pace-" + grimTracePaceSince
                    + (grimTracePaceIntave ? "/imean" : "/" + grimTracePaceFloor + "ms");
            traceGrim("pace-hold", target);
            return;
        }

        if (AutismBlinkManager.holdsActionsWithoutMovement()) {
            grimTraceWhy = "blink-action-only";
            traceGrim("real-wait", target);
            return;
        }
        grimRealPendingTarget = target;
        grimRealPendingLine = currentMovementLine;
        grimRealPendingFallOff = findFallOffPosition(currentMovementLine);
        grimRealQueuedTick = tick;
        grimAttemptState = GrimPlacementAttemptState.ARMED;
        grimNextAttemptGeneration = grimNextAttemptGeneration(grimNextAttemptGeneration);
        grimAttemptGeneration = grimNextAttemptGeneration;
        grimAttemptHand = hand;
        grimAttemptBuildsPlannedCell = grimHitBuildsPlannedCell(grimLastArmRay, target);
        grimAttemptSubmittedCount = 0;
        grimAttemptDuplicateSubmitted = false;
        grimAttemptWriteCount = 0;
        grimCommittedClickRotation = clickRotation;
        grimCommittedPreviousRotation = wireClick.previous();
        grimAttemptSequence = -1;
        grimAttemptResultSeen = false;
        grimAttemptResultConsumed = false;
        grimAttemptPaceBooked = false;
        grimAttemptResult = "queued";
        lastGrimPlacementTick = grimRealQueuedTick;

        grimPaceQueuedNanos = Long.MIN_VALUE;
        grimPaceJitterMs = rotationRandom.nextInt(GRIM_PACE_JITTER_MS);
        grimTraceClickLands = true;
        grimTraceWhy = "ok";
        AutismInputClicker.queueScaffoldUseClick(grimAttemptGeneration);

        ((autismclient.mixin.accessor.AutismMinecraftAccessor) (Object) MC)
            .autism$setRightClickDelay(0);
        traceGrim("PLACE", target);
    }

    private void settleGrimRealClick() {
        drainGrimFinalUseWrite();
        reconcileGrimPlacementAcks();
        PlacementTarget pending = grimRealPendingTarget;
        if (pending == null || MC.level == null || MC.player == null) {
            if (pending == null && grimAttemptState != GrimPlacementAttemptState.IDLE) {
                clearGrimPlacementAttempt();
            }
            return;
        }

        int tick = AutismSharedState.get().getClientTickCounter();
        int age = Math.max(0, tick - grimRealQueuedTick);
        BlockPos cell = pending.placedBlock();
        boolean solid = isSolidSupport(MC.level.getBlockState(cell), cell);

        GrimAttemptDecision decision = grimReduceAttempt(
            grimAttemptState, age, grimAttemptResultSeen, grimAttemptResultConsumed, solid,
            grimAckCovers(grimAttemptSequence, grimHighestObservedAck));
        if (decision.state() == GrimPlacementAttemptState.FAILED) {
            String reason = "use".equals(decision.failure())
                ? "use-" + grimAttemptResult : decision.failure();
            failGrimPlacementAttempt(reason);
        } else {
            grimAttemptState = decision.state();
        }

        if (grimAttemptState == GrimPlacementAttemptState.RECONCILING) {
            grimTraceWhy = "reconcile";
            traceGrim("real-wait", pending);
            return;
        }

        if (grimAttemptState == GrimPlacementAttemptState.PREDICTED && solid) {
            grimUntrustedPredictions.remove(cell);
            trackSuccessfulPlacement(cell, grimRealPendingLine, grimRealPendingFallOff);
            grimTraceSettledCell = cell;
            if (!MC.player.onGround()) {
                grimArcPlacements++;

                if (!cell.equals(grimLaunchReservedStep)) {
                    grimAirborneBuiltRow = Math.max(grimAirborneBuiltRow, cell.getY());
                }
            }
            rememberGrimPrediction(grimAttemptSequence, cell);
            autismclient.util.AutismScaffoldPlaceRenderer.recordPlacement(cell);
            grimTraceWhy = "ok";
            traceGrim("settle-ok", pending);
            clearGrimPlacementAttempt();
            return;
        }

        if (grimAttemptState == GrimPlacementAttemptState.FAILED) {
            grimTraceWhy = grimAttemptResult;
            traceGrim("real-miss", pending);
            clearGrimPlacementAttempt();
        }
    }

    private boolean grimAttemptBlocksRearm() {
        return grimAttemptBlocksRearm(grimAttemptState);
    }

    private void failGrimPlacementAttempt(String reason) {
        if (grimRealPendingTarget != null && MC != null && MC.level != null) {
            BlockPos cell = grimRealPendingTarget.placedBlock();
            if (solidAt(cell)) quarantineGrimPrediction(cell, grimAttemptSequence);
        }
        grimAttemptState = GrimPlacementAttemptState.FAILED;
        grimAttemptResult = reason == null ? "failed" : reason;
        if (grimStickyTarget != null && grimRealPendingTarget != null
            && grimStickyTarget.placedBlock().equals(grimRealPendingTarget.placedBlock())) {
            grimStickyTarget = null;
        }
    }

    private void clearGrimPlacementAttempt() {
        grimRealPendingTarget = null;
        grimRealPendingLine = null;
        grimRealPendingFallOff = null;
        grimRealQueuedTick = Integer.MIN_VALUE;
        grimAttemptState = GrimPlacementAttemptState.IDLE;
        grimAttemptGeneration = 0L;
        grimAttemptHand = null;
        grimAttemptBuildsPlannedCell = false;
        grimAttemptSubmittedCount = 0;
        grimAttemptDuplicateSubmitted = false;
        grimAttemptWriteCount = 0;
        grimCommittedClickRotation = null;
        grimCommittedPreviousRotation = null;
        grimAttemptSequence = -1;
        grimAttemptResultSeen = false;
        grimAttemptResultConsumed = false;
        grimAttemptPaceBooked = false;
        grimAttemptResult = "--";
    }

    private void rememberGrimPrediction(int sequence, BlockPos cell) {
        if (sequence < 0 || cell == null
            || grimAckCovers(sequence, grimHighestObservedAck)) return;
        grimPredictedPlacements.addLast(new GrimPredictedPlacement(sequence, cell.immutable()));
        while (grimPredictedPlacements.size() > GRIM_PREDICTION_HISTORY) {
            grimPredictedPlacements.removeFirst();
        }
    }

    private void quarantineGrimPrediction(BlockPos cell, int sequence) {
        if (cell == null) return;
        grimUntrustedPredictions.merge(cell.immutable(), sequence,
            (oldSequence, newSequence) -> Math.max(oldSequence, newSequence));
    }

    private void ensureGrimPredictionLevel() {
        ClientLevel level = MC == null ? null : MC.level;
        if (!grimPredictionEpochChanged(grimPredictionLevel, level)) return;
        grimPredictionLevel = level;
        grimPredictedPlacements.clear();
        grimUntrustedPredictions.clear();
        grimHighestObservedAck = Integer.MIN_VALUE;
        grimHighestProcessedAck = Integer.MIN_VALUE;
        GRIM_FINAL_USE_WRITES.clear();
        if (grimRealPendingTarget != null || grimAttemptState != GrimPlacementAttemptState.IDLE) {
            clearGrimPlacementAttempt();
        }
        resetGrimLaunchReservation();

        grimLastRowGainTick = Integer.MIN_VALUE;
    }

    static boolean grimPredictionEpochChanged(Object previous, Object current) {
        return previous != current;
    }

    private void reconcileGrimPlacementAcks() {
        int acknowledged = grimHighestObservedAck;
        if (acknowledged == Integer.MIN_VALUE || acknowledged <= grimHighestProcessedAck
            || MC.level == null) return;
        grimHighestProcessedAck = acknowledged;
        Iterator<GrimPredictedPlacement> iterator = grimPredictedPlacements.iterator();
        while (iterator.hasNext()) {
            GrimPredictedPlacement predicted = iterator.next();
            if (!grimAckCovers(predicted.sequence(), acknowledged)) continue;
            iterator.remove();
            grimUntrustedPredictions.remove(predicted.cell());
            if (!isSolidSupport(MC.level.getBlockState(predicted.cell()), predicted.cell())) {
                invalidateGrimPredictedPlacement(predicted.cell());
            }
        }
    }

    private void reconcileGrimUntrustedAck(int acknowledged) {
        Iterator<Map.Entry<BlockPos, Integer>> iterator =
            grimUntrustedPredictions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            int highestSequence = entry.getValue();
            if (grimAckRetiresQuarantine(highestSequence, acknowledged)) {

                iterator.remove();
            }
        }
    }

    static boolean grimAckRetiresQuarantine(int highestSequence, int acknowledged) {
        return highestSequence >= 0 && grimAckCovers(highestSequence, acknowledged);
    }

    private void invalidateGrimPredictedPlacement(BlockPos cell) {
        if (cell == null) return;
        lastPlacedBlocks.removeIf(cell::equals);
        placementOffsets.clear();
        if (grimStickyTarget != null && cell.equals(grimStickyTarget.placedBlock())) {
            grimStickyTarget = null;
        }
        grimEdgeLockedLine = null;
        grimEffCell = null;
        grimLastRescueTick = Integer.MIN_VALUE;
        grimTraceWhy = "ack-air";
    }

    private void grimTickArcBudget() {
        if (MC.player == null || MC.player.onGround()) {
            grimArcTicks = 0;
            grimArcPlacements = 0;
            grimArcCarryOrigin = MC.player == null ? null : MC.player.position();
            return;
        }
        grimArcTicks++;
    }

    private int grimArcTicks;
    private int grimArcPlacements;

    private Vec3 grimArcCarryOrigin;

    private String traceArcCarry() {
        if (MC.player == null || MC.player.onGround() || grimArcCarryOrigin == null) {
            return "carry=--";
        }
        Vec3 now = MC.player.position();
        double dx = now.x - grimArcCarryOrigin.x;
        double dz = now.z - grimArcCarryOrigin.z;
        return String.format(java.util.Locale.ROOT, "carry=%.2f/%.2f",
            Math.sqrt(dx * dx + dz * dz), GRIM_OWN_RISER_MAX_CARRY);
    }

    private BlockPos grimAimMissSupport;
    private Direction grimAimMissFace;
    private int grimAimMissStreak;

    private int grimAimWindowWaitTicks;

    private int grimNoTargetTicks;

    private int grimPaceJitterMs;

    private AutismRotationUtil.Rotation grimAimGoalWithMissFallback(PlacementTarget target) {

        if (MC.player != null && target.face().getAxis().isHorizontal()
            && grimAimMissStreak >= GRIM_AIM_MISS_FALLBACK_TICKS
            && grimAimOccludedTicks == 0
            && target.supportBlock().equals(grimAimMissSupport)
            && target.face() == grimAimMissFace) {
            grimLastGoalEye = "miss-direct";
            AutismRotationUtil.Rotation direct = AutismRotationUtil.lookingAt(
                target.hit().getLocation(), MC.player.getEyePosition());

            return new AutismRotationUtil.Rotation(direct.yaw(),
                grimPlacementPitchCap(direct.pitch()));
        }
        return target.rotation();
    }

    static boolean grimHitInFrontOfFace(
        Vec3 eye, Vec3 hit, BlockPos support, Direction face
    ) {
        double eyePast = grimEyePastPlane(eye, support, face);
        double hitPast = grimEyePastPlane(hit, support, face);
        if (eyePast > 0.0D) {
            return hitPast > GRIM_OCCLUDER_PLANE_EPS && hitPast < eyePast;
        }
        return hitPast < -GRIM_OCCLUDER_PLANE_EPS && hitPast > eyePast;
    }

    private void grimNoteAimMiss(PlacementTarget target, AutismRotationUtil.Rotation clickRotation) {
        if (!target.supportBlock().equals(grimAimMissSupport) || target.face() != grimAimMissFace) {
            grimAimMissSupport = target.supportBlock();
            grimAimMissFace = target.face();
            grimAimMissStreak = 0;
            grimAimOccludedTicks = 0;
            grimAimWindowWaitTicks = 0;
        }

        boolean occluded = grimLastArmRay != null
            && !grimLastArmRay.getBlockPos().equals(target.supportBlock())
            && solidAt(grimLastArmRay.getBlockPos())
            && MC.player != null
            && grimHitInFrontOfFace(MC.player.getEyePosition(), grimLastArmRay.getLocation(),
                target.supportBlock(), target.face());
        boolean blank = grimLastArmRay == null;

        boolean aimArrived = clickRotation == null
            || !grimGoalYawUnconverged(target.rotation().yaw(), clickRotation.yaw());
        boolean pastAndBlank = blank && MC.player != null && grimEyePastTargetFace(target)
            && aimArrived;
        if (occluded || pastAndBlank) {
            if (++grimAimOccludedTicks >= 2) {

                grimMarkCellDead(target.placedBlock());
                grimStickyTarget = null;

                if (target.supportBlock().equals(grimPinSupport)
                    && target.face() == grimPinFace) {
                    grimPinSupport = null;
                    grimPinFace = null;
                }
                grimAimOccludedTicks = 0;
            }
        } else if (!blank) {

            grimAimOccludedTicks = 0;
        }

        if (MC.player == null || !grimEyePastTargetFace(target)) {
            grimAimMissStreak = 0;
            grimAimWindowWaitTicks = 0;
        } else if (grimAimWindowOpening(target)
            && grimAimWindowWaitTicks < GRIM_AIM_WINDOW_WAIT_MAX_TICKS) {
            grimAimMissStreak = 0;
            grimAimWindowWaitTicks++;
        } else {
            grimAimMissStreak++;
            grimAimWindowWaitTicks = 0;
        }
    }

    private boolean grimAimWindowOpening(PlacementTarget target) {
        if (MC.player == null || target.face().getAxis().isVertical()) return false;
        float yaw = target.rotation().yaw();
        Vec3 lead = grimLeadStep();
        if (lead.horizontalDistanceSqr() < 1.0E-4D && currentMovementLine != null) {

            lead = new Vec3(currentMovementLine.direction().x, 0.0D,
                currentMovementLine.direction().z).normalize().scale(0.05D);
        }
        Vec3 eye = MC.player.getEyePosition();
        for (int step = 1; step <= 2; step++) {
            Vec3 projected = eye.add(lead.scale(step));
            if (!grimCrossingLandsOnFace(
                projected, target.supportBlock(), target.face(), yaw, true)) {
                continue;
            }
            float solve = grimSideWindowSolvePitch(
                projected, target.supportBlock(), target.face(), yaw);
            if (Float.isFinite(solve) && solve <= GRIM_PLACE_MAX_PITCH_HARD) return true;
        }
        return false;
    }

    static boolean grimGoalYawUnconverged(float goalYaw, float emittedYaw) {
        return Math.abs(Mth.wrapDegrees(goalYaw - emittedYaw)) > GRIM_MAX_YAW_STEP + 2.0F;
    }

    private boolean grimFaceOutOfReachThroughApproach(
        Vec3 eye, BlockPos support, Direction face, float yaw) {
        Vec3 lead = grimLeadStep();
        for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            if (!grimPitchOutOfReach(grimSideWindowSolvePitch(
                eye.add(lead.scale(step)), support, face, yaw))) {
                return false;
            }
        }
        return true;
    }

    static boolean grimFaceSelfOccluded(
        Vec3 eye, BlockPos support, Direction face,
        java.util.function.Predicate<BlockPos> fullCubeAt) {
        if (eye == null || support == null || face == null || fullCubeAt == null) return false;
        if (!face.getAxis().isHorizontal()) return false;
        if (Mth.floor(eye.x) != support.getX() || Mth.floor(eye.z) != support.getZ()) return false;
        BlockPos lid = support.above();
        return eye.y >= lid.getY() + 1.0D && fullCubeAt.test(lid);
    }

    private boolean grimSelfOccludedThroughApproach(BlockPos support, Direction face) {
        if (MC.player == null || MC.level == null) return false;
        Vec3 eye = MC.player.getEyePosition();
        Vec3 lead = grimLeadStep();
        int horizon = Math.max(GRIM_PIN_LOOKAHEAD_TICKS, grimWalkLeadTicks(grimPaceWaitTicks));
        for (int step = 0; step <= horizon; step++) {
            if (!grimFaceSelfOccluded(
                eye.add(lead.scale(step)), support, face, this::grimFullCubeAt)) {
                return false;
            }
        }
        return true;
    }

    private boolean grimFullCubeAt(BlockPos pos) {
        if (MC.level == null || pos == null) return false;
        return MC.level.getBlockState(pos).isCollisionShapeFullBlock(MC.level, pos);
    }

    static float grimSideWindowSolvePitch(Vec3 eye, BlockPos support, Direction face, float yaw) {
        return grimSideWindowSolvePitch(eye, grimSupportBox(support), face, yaw);
    }

    static float grimSideWindowSolvePitch(Vec3 eye, AABB support, Direction face, float yaw) {
        if (support == null) return Float.NaN;
        double past = grimEyePastPlane(eye, support, face);
        if (past <= 0.0D) return Float.NaN;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return Float.NaN;
        double run = past / toward;
        double drop = eye.y - grimFaceCrossDepthY(support);
        return (float) Math.toDegrees(Math.atan2(drop, run));
    }

    static boolean grimRaySubstitutionIsNoOp(
        BlockPos plannedCell, Direction plannedFace, BlockPos rayCell, Direction rayFace
    ) {
        return grimRaySubstitutionIsNoOp(plannedCell, plannedFace, null, rayCell, rayFace, null);
    }

    static boolean grimRaySubstitutionIsNoOp(
        BlockPos plannedCell, Direction plannedFace, BlockPos plannedSupport,
        BlockPos rayCell, Direction rayFace, BlockPos raySupport
    ) {
        if (rayCell == null || !rayCell.equals(plannedCell) || rayFace != plannedFace) return false;
        return plannedSupport == null || raySupport == null || plannedSupport.equals(raySupport);
    }

    private PlacementTarget grimLaneRayTarget(PlacementTarget planned, BlockHitResult ray) {
        if (MC.player == null || MC.level == null || grimSilentRotation == null || ray == null) {
            return null;
        }
        BlockPos support = ray.getBlockPos().immutable();
        Direction face = ray.getDirection();

        BlockPos cell = grimPlacedCellFor(ray, planningStack());
        if (cell == null) return null;

        if (grimRaySubstitutionIsNoOp(planned.placedBlock(), planned.face(), planned.supportBlock(),
            cell, face, support)) {
            return null;
        }
        if (!MC.level.getBlockState(cell).canBeReplaced()) return null;

        if (!grimCellClearOfBody(MC.player.getBoundingBox(),
            MC.player.getDeltaMovement(), cell, grimFallingCatchPlan(cell))) {
            return null;
        }
        if (!grimLaneFrontierCell(cell)) return null;

        if (!grimRiseRowSubstitutionAllowed(cell, grimOracleFootingRow(),
            grimLaunchReservedSupport,
            currentMovementLine == null ? grimOwnRiserSupport() : null)) {
            return null;
        }

        if (grimIsOwnFootingCell(planned.placedBlock()) && !grimIsOwnFootingCell(cell)) return null;
        return new PlacementTarget(support, cell, face, ray, grimSilentRotation, cell.getY());
    }

    static boolean grimRiseRowSubstitutionAllowed(
        BlockPos cell, int oracleRow, BlockPos reservedSupport, BlockPos ownSupport
    ) {
        if (cell.getY() <= oracleRow) return true;
        return grimSameColumn(cell, reservedSupport) || grimSameColumn(cell, ownSupport);
    }

    private boolean grimIsOwnFootingCell(BlockPos cell) {
        return MC.player != null && grimIsOwnFootingCell(cell, grimLaneFootCell());
    }

    private BlockPos grimLaneFootCell() {
        int row = grimOracleFootingRow();
        return row == Integer.MIN_VALUE ? grimEffectiveFootCell() : grimFootCellAtRow(row);
    }

    private String grimAimWaitCode(PlacementTarget target) {
        grimNoteClickNumbers(target);
        if (grimLastArmRay == null) {
            return grimEyePastTargetFace(target) ? "blank" : "plane";
        }
        if (!grimLastArmRay.getBlockPos().equals(target.supportBlock())) return "occ";
        if (grimLastArmRay.getDirection() != target.face()) {
            return grimLastArmRay.getDirection() == Direction.UP ? "u" : "side";
        }
        return grimTargetOutOfReach(target) ? "reach" : "near";
    }

    private void grimNoteCrossingWait(PlacementTarget target, String waitCode) {
        grimCrossingWaitFace = null;
        if (MC.player == null || target == null) return;
        if (!"plane".equals(waitCode) && !"u".equals(waitCode)) return;
        if (!target.face().getAxis().isHorizontal()) return;
        if (grimEyePastTargetFace(target)) return;
        grimCrossingWaitFace = target.face();
        grimCrossingWaitTick = AutismSharedState.get().getClientTickCounter();
    }

    static boolean grimBrakeStarvesCrossing(
        Direction waitFace, int waitAge, boolean rising, Vec3 lane
    ) {
        if (waitFace == null || waitAge != 0 || !rising || lane == null) return false;
        return waitFace.getStepX() * lane.x + waitFace.getStepZ() * lane.z > 0.0D;
    }

    static boolean grimCrossingStanddownAllowed(boolean starves, double spentBlocks) {
        return starves && spentBlocks <= GRIM_XING_STANDDOWN_MAX_TRAVEL;
    }

    private boolean grimCrossingStanddown(boolean rising, Vec3 lane) {
        int tick = AutismSharedState.get().getClientTickCounter();
        boolean starves = grimBrakeStarvesCrossing(grimCrossingWaitFace,
            grimTicksSince(tick, grimCrossingWaitTick), rising, lane);
        if (tick != grimXingStandTick) {
            grimXingStandTick = tick;
            grimXingStandSpent = starves ? grimXingStandSpent + grimLaneTravel(lane) : 0.0D;
        }
        return grimCrossingStanddownAllowed(starves, grimXingStandSpent);
    }

    private double grimLaneTravel(Vec3 lane) {
        if (MC.player == null || lane == null) return 0.0D;
        Vec3 velocity = MC.player.getDeltaMovement();
        return Math.abs(velocity.x * lane.x + velocity.z * lane.z);
    }

    static boolean grimIsOwnFootingCell(BlockPos cell, BlockPos foot) {
        return cell != null && foot != null && cell.getY() == foot.getY() - 1
            && cell.getX() == foot.getX() && cell.getZ() == foot.getZ();
    }

    private boolean grimLaneFrontierCell(BlockPos cell) {
        if (currentMovementLine == null) return false;
        Vec3 direction = currentMovementLine.direction();
        return grimLaneFrontierCell(cell, grimLaneFootCell(),
            horizontalStep(direction.x), horizontalStep(direction.z));
    }

    static boolean grimLaneFrontierCell(BlockPos cell, BlockPos foot, int dx, int dz) {
        if (dx == 0 && dz == 0) return false;
        if (cell.getY() != foot.getY() - 1 && cell.getY() != foot.getY()) return false;
        int rx = foot.getX() - cell.getX();
        int rz = foot.getZ() - cell.getZ();
        return (rx == 0 || rx == dx) && (rz == 0 || rz == dz);
    }

    private boolean grimRiseDropApplies(PlacementTarget target) {
        if (target == null || MC.player == null || !target.face().getAxis().isVertical()) return false;
        boolean stairRiser = target.face() == Direction.UP && !MC.player.onGround()
            && target.placedBlock().getY() > grimOracleFootingRow();

        Vec3 dropCarry = MC.player.onGround() ? Vec3.ZERO : MC.player.getDeltaMovement();

        boolean own = stairRiser && grimSameColumn(target.placedBlock(), grimOwnRiserSupport());
        boolean drop = stairRiser
            ? !(own
                ? grimBoxOverColumn(MC.player.position(), target.placedBlock())
                : grimArcLandsOnColumnLive(MC.player.position(), MC.player.getDeltaMovement(),
                    target.placedBlock()))
            : !footprintOverlapsColumn(
                MC.player.position(), dropCarry, target.placedBlock());
        if (drop) grimTraceRiseDropWhy = stairRiser ? (own ? "own" : "arc") : "foot";
        return drop;
    }

    static boolean grimRiseDropReselects(boolean onGround, boolean jumpHeld) {
        return onGround && jumpHeld;
    }

    static int grimTicksSince(int tick, int lastTick) {
        if (lastTick == Integer.MIN_VALUE) return -1;
        int since = tick - lastTick;
        return since < 0 ? -1 : since;
    }

    private boolean grimPaceHolds(
        PlacementTarget target, AutismRotationUtil.Rotation clickRotation
    ) {
        if (MC.player == null) return false;

        long nowNanos = System.nanoTime();
        long elapsed = grimPaceElapsedMs(nowNanos);
        grimTracePaceSince = elapsed;

        if (grimRemoveLimits()) {
            grimPaceWaitTicks = 0;
            grimTracePaceFloor = 0;
            return false;
        }

        boolean airborne = !MC.player.onGround();
        boolean descending = airborne && MC.player.getDeltaMovement().y < 0.0D;
        boolean chainCell = airborne && grimLandingChainCell(target.placedBlock());

        boolean footCatch = grimFallingCatchPlan(target.placedBlock());
        boolean fallingCatch = footCatch && MC.player.fallDistance >= 1.0F;
        if (grimLandingLastChance(target)

            || (chainCell && descending)
            || (chainCell && grimChainWindowClosing(target))

            || fallingCatch) {
            grimTraceLastChance = true;
            grimTracePaceFloor = GRIM_LANDING_LAST_CHANCE_FLOOR_MS;
            return grimPaceFloorHolds(elapsed, GRIM_LANDING_LAST_CHANCE_FLOOR_MS);
        }
        if (grimPacesAsRiser(
            target.placedBlock(), target.supportBlock(), grimUpFaceSwapCell)) {

            boolean landingRiser = !MC.player.onGround()
                && MC.player.getDeltaMovement().y < 0.0D
                && target.placedBlock().getY() > target.supportBlock().getY();
            long floor = landingRiser
                ? GRIM_LANDING_LAST_CHANCE_FLOOR_MS : GRIM_PACE_RISER_FLOOR_MS;
            grimTracePaceFloor = floor;
            return grimPaceFloorHolds(elapsed, floor);
        }

        if (chainCell || footCatch) {
            grimTracePaceBrink = true;
            grimTracePaceFloor = GRIM_PACE_RISER_FLOOR_MS;
            return grimPaceFloorHolds(elapsed, GRIM_PACE_RISER_FLOOR_MS);
        }

        if (grimRiseFloorAwaiting(target.placedBlock())) {
            grimTracePaceBrink = true;
            grimTracePaceFloor = GRIM_PACE_RISER_FLOOR_MS;
            return grimPaceFloorHolds(elapsed, GRIM_PACE_RISER_FLOOR_MS);
        }

        if (grimFootingBrink(target)) {

            if (grimNoFootingUnderfoot()) {
                grimTraceLastChance = true;
                grimTracePaceFloor = GRIM_LANDING_LAST_CHANCE_FLOOR_MS;
                return grimPaceFloorHolds(elapsed, GRIM_LANDING_LAST_CHANCE_FLOOR_MS);
            }
            grimTracePaceBrink = true;
            grimTracePaceFloor = GRIM_PACE_RISER_FLOOR_MS;
            return grimPaceFloorHolds(elapsed, GRIM_PACE_RISER_FLOOR_MS);
        }

        List<BlockPos> booked = new ArrayList<>(grimPaceSamples.size() + 1);
        for (GrimPaceSample sample : grimPaceSamples) booked.add(sample.placed());
        booked.add(target.placedBlock());
        long limit = grimPaceLimitMs(
            grimPaceOneLine(booked),
            grimPaceYawBanded(clickRotation == null
                ? MC.player.getYRot() : clickRotation.yaw()),
            grimSneakedRecently(),
            grimPaceRecentJump());

        long floor = grimPaceFloorMs(limit) + grimPaceJitterMs;

        grimPaceWaitTicks = grimPaceWaitTicks(elapsed, floor);
        grimTracePaceFloor = floor;
        if (grimPaceFloorHolds(elapsed, floor)) return true;
        if (grimPaceSamples.isEmpty() || grimPaceLastBookedNanos == Long.MIN_VALUE) {
            return grimIntavePlaceHolds(target, clickRotation, nowNanos);
        }
        long candidate = elapsed;
        long[] recent = new long[grimPaceSamples.size()];
        int index = 0;
        for (GrimPaceSample sample : grimPaceSamples) {
            recent[index++] = sample.millis();
        }
        double mean = grimPaceProspectiveMean(recent, candidate, GRIM_PACE_SAMPLES);

        grimTracePaceFloor = 0;
        if (mean < limit * GRIM_PACE_SAFETY) return true;
        return grimIntavePlaceHolds(target, clickRotation, nowNanos);
    }

    private boolean grimIntavePlaceHolds(
        PlacementTarget target, AutismRotationUtil.Rotation clickRotation, long nowNanos) {
        if (MC.player == null || !MC.player.onGround()) return false;

        if (!grimIntaveRecordsPlacement(target.face())) return false;
        if (grimIntavePlaceGaps.isEmpty() || grimIntavePlaceNanos == Long.MIN_VALUE) return false;
        long candidate = grimIntavePlaceGap(nowNanos);
        if (candidate < 0L) return false;

        if (candidate >= GRIM_INTAVE_PLACE_SAMPLE_CAP_MS) return false;
        float pitch = clickRotation == null ? MC.player.getXRot() : clickRotation.pitch();
        List<BlockPos> cells = new ArrayList<>(grimIntavePlaceCells.size() + 1);
        if (candidate < GRIM_INTAVE_FLICK_CELL_TTL_MS) cells.addAll(grimIntavePlaceCells);
        while (cells.size() > GRIM_INTAVE_FLICK_CELLS - 1) cells.remove(0);
        cells.add(target.placedBlock());
        if (!grimIntaveMeanWorthHolding(
            pitch, grimIntavePlacePitch, candidate, grimIntaveFlickOneLine(cells))) return false;
        long[] recent = new long[grimIntavePlaceGaps.size()];
        int index = 0;
        for (long gap : grimIntavePlaceGaps) recent[index++] = gap;
        if (grimPaceProspectiveMean(recent, candidate, GRIM_PACE_SAMPLES)
            >= GRIM_INTAVE_PLACE_MEAN_MS * GRIM_PACE_SAFETY) return false;
        grimTracePaceSince = candidate;
        grimTracePaceIntave = true;
        return true;
    }

    static long grimPaceFloorMs(long limitMs) {
        return Math.round(limitMs * GRIM_PACE_SAFETY);
    }

    private long grimPaceElapsedMs(long nowNanos) {
        return grimMonotonicElapsedMs(nowNanos, grimPaceLastBookedNanos);
    }

    static long grimMonotonicElapsedMs(long nowNanos, long sinceNanos) {
        if (sinceNanos == Long.MIN_VALUE) return -1L;
        if (nowNanos <= sinceNanos) return 0L;
        return (nowNanos - sinceNanos) / 1_000_000L;
    }

    static long grimMonotonicTimestamp(long previousNanos, long observedNanos) {
        return previousNanos == Long.MIN_VALUE
            ? observedNanos : Math.max(previousNanos, observedNanos);
    }

    static final long GRIM_PACE_CLOCK_SLACK_MS = 3L;

    static boolean grimPaceFloorHolds(long elapsedMs, long floorMs) {
        return elapsedMs >= 0L && elapsedMs < floorMs - GRIM_PACE_CLOCK_SLACK_MS;
    }

    static boolean grimWirePitchFrozen(boolean rayClickable, boolean paceRefused) {
        return rayClickable && !paceRefused;
    }

    static long grimMatrixMinPlaceMs(boolean diagonal) {
        return diagonal ? GRIM_MATRIX_MIN_PLACE_DIAGONAL_MS : GRIM_MATRIX_MIN_PLACE_CARDINAL_MS;
    }

    static long grimPaceExemptFloorMs(long exemptionMs, boolean diagonal) {
        return Math.max(exemptionMs, grimMatrixMinPlaceMs(diagonal));
    }

    static int grimPaceWaitTicks(long elapsedMs, long floorMs) {
        if (elapsedMs < 0L || elapsedMs >= floorMs) return 0;
        return (int) Math.ceil((floorMs - elapsedMs) / 50.0D);
    }

    private int grimStandingRow() {
        if (MC.player == null) return Integer.MIN_VALUE;
        return MC.player.onGround() ? grimOracleFootingRow() : grimBuiltFloorRow();
    }

    private boolean grimFootingBrink(PlacementTarget target) {
        if (MC.player == null || MC.level == null) return false;
        int row = grimStandingRow();
        if (row == Integer.MIN_VALUE) return false;
        if (!grimIsOwnFootingCell(target.placedBlock(), grimFootCellAtRow(row))) return false;
        return grimFootingOverlap(MC.player.position(), row)
            <= grimBrinkOverlapFor(MC.player.onGround());
    }

    static double grimBrinkOverlapFor(boolean grounded) {
        return grounded ? GRIM_PACE_ACK_LEAD_OVERLAP : GRIM_PACE_BRINK_OVERLAP;
    }

    private BlockPos grimFootCellAtRow(int row) {
        Vec3 position = MC.player.position();
        return new BlockPos(Mth.floor(position.x), row + 1, Mth.floor(position.z));
    }

    private boolean grimLandingLastChance(PlacementTarget target) {
        if (MC.player == null || MC.level == null) return false;
        if (MC.player.onGround() || MC.player.getDeltaMovement().y >= 0.0D) return false;

        int row = grimStandingRow();
        if (row == Integer.MIN_VALUE) return false;
        Vec3 landing = grimDescentCrossing(MC.player.position(), MC.player.getDeltaMovement(),
            row + 1.0D, GRIM_DESCENT_LOOKAHEAD_TICKS);
        if (landing == null) return false;
        if (!target.placedBlock().equals(
            BlockPos.containing(landing.x, row + 0.5D, landing.z))) return false;
        return grimFootingOverlap(MC.player.position(), row) <= GRIM_PACE_BRINK_OVERLAP;
    }

    private boolean grimLandingChainCell(BlockPos placed) {
        return grimRiseFloorAwaiting(placed)
            || placed.equals(grimLaunchReservedSupport)
            || placed.equals(grimLaunchReservedConnector)
            || placed.equals(grimLaunchReservedStep)
            || placed.equals(grimLaunchReservedRiser);
    }

    private boolean grimChainWindowClosing(PlacementTarget target) {
        if (target.face().getAxis().isVertical()) return false;
        Vec3 eye = MC.player.getEyePosition();
        Vec3 velocity = MC.player.getDeltaMovement();
        float yaw = target.rotation().yaw();
        for (int step = 1; step <= 2; step++) {

            Vec3 projected = eye.add(velocity.scale(step));
            if (grimEyePastPlane(projected, target.supportBlock(), target.face()) <= 0.0D) {
                continue;
            }
            if (!grimCrossingLandsOnFace(
                projected, target.supportBlock(), target.face(), yaw, false)) {
                continue;
            }
            float solve = grimSideWindowSolvePitch(
                projected, target.supportBlock(), target.face(), yaw);
            if (Float.isFinite(solve) && solve <= GRIM_PLACE_MAX_PITCH_HARD) return false;
        }
        return true;
    }

    static final long GRIM_LANDING_LAST_CHANCE_FLOOR_MS = 50L;

    static final long GRIM_PACE_RISER_FLOOR_MS = 100L;

    static final long GRIM_MATRIX_MIN_PLACE_CARDINAL_MS = 350L;
    static final long GRIM_MATRIX_MIN_PLACE_DIAGONAL_MS = 250L;

    private record GrimPaceSample(long millis, BlockPos placed) {}

    private BlockPos grimPaceRiserHoldCell = null;
    private int grimPaceRiserHoldTick = Integer.MIN_VALUE;
    private static final int GRIM_PACE_RISER_HOLD_STAMP_TICKS = 8;

    static boolean grimDyingRiserPickSteal(boolean grounded, boolean descending,
        boolean stampFresh, boolean stampedCell, boolean faceHorizontal,
        boolean eyePastPlane, boolean windowAlive) {
        return !grounded && descending && stampFresh && !stampedCell
            && faceHorizontal && eyePastPlane && !windowAlive;
    }

    private final ArrayDeque<GrimPaceSample> grimPaceSamples = new ArrayDeque<>();

    private final ArrayDeque<Long> grimIntavePlaceGaps = new ArrayDeque<>();

    private final ArrayDeque<BlockPos> grimIntavePlaceCells = new ArrayDeque<>();

    private long grimIntavePlaceNanos = Long.MIN_VALUE;

    private float grimIntavePlacePitch = Float.NaN;

    private long grimPaceLastBookedNanos = Long.MIN_VALUE;

    private long grimPaceLastJumpNanos = Long.MIN_VALUE;

    private long grimPaceQueuedNanos = Long.MIN_VALUE;

    private void grimPaceBook(BlockPos placed, BlockPos against, Direction face, float wirePitch) {
        long observed = grimPaceQueuedNanos == Long.MIN_VALUE
            ? System.nanoTime() : grimPaceQueuedNanos;
        long now = grimMonotonicTimestamp(grimPaceLastBookedNanos, observed);
        long interval = grimPaceLastBookedNanos == Long.MIN_VALUE
            ? GRIM_PACE_CROSS_Y_MS
            : grimMonotonicElapsedMs(now, grimPaceLastBookedNanos);

        if (placed.getY() != against.getY()) interval += GRIM_PACE_CROSS_Y_MS;
        grimPaceLastBookedNanos = now;
        grimPaceSamples.addLast(new GrimPaceSample(interval, placed));
        while (grimPaceSamples.size() > GRIM_PACE_SAMPLES) grimPaceSamples.removeFirst();
        if (grimIntaveRecordsPlacement(face)) grimIntavePlaceBook(now, placed, wirePitch);
        grimPaceRiserHoldCell = null;
        grimPaceRiserHoldTick = Integer.MIN_VALUE;
        grimIntaveParkClear();
    }

    static boolean grimIntaveRecordsPlacement(Direction face) {
        return face != null && face.getAxis().isHorizontal();
    }

    private void grimIntavePlaceBook(long now, BlockPos placed, float wirePitch) {

        long gap = grimIntavePlaceGap(now);
        if (gap < 0L) gap = GRIM_INTAVE_PLACE_SAMPLE_CAP_MS;
        if (gap >= GRIM_INTAVE_FLICK_CELL_TTL_MS) grimIntavePlaceCells.clear();
        grimIntavePlaceNanos = now;
        grimIntavePlacePitch = wirePitch;
        grimIntavePlaceGaps.addLast(gap);
        while (grimIntavePlaceGaps.size() > GRIM_PACE_SAMPLES) grimIntavePlaceGaps.removeFirst();
        grimIntavePlaceCells.addLast(placed.immutable());
        while (grimIntavePlaceCells.size() > GRIM_INTAVE_FLICK_CELLS) {
            grimIntavePlaceCells.removeFirst();
        }
    }

    private long grimIntavePlaceGap(long nowNanos) {
        long elapsed = grimMonotonicElapsedMs(nowNanos, grimIntavePlaceNanos);
        return elapsed < 0L ? -1L : Math.min(GRIM_INTAVE_PLACE_SAMPLE_CAP_MS, elapsed);
    }

    private boolean grimPaceRecentJump() {
        long elapsed = grimMonotonicElapsedMs(System.nanoTime(), grimPaceLastJumpNanos);
        return elapsed >= 0L && elapsed < GRIM_PACE_RECENT_JUMP_WINDOW_MS;
    }

    private boolean grimPaceWasOnGround;

    private void updateGrimTakeoffClock() {
        if (MC.player == null) return;
        boolean onGround = MC.player.onGround();
        if (grimPaceWasOnGround && !onGround && MC.player.getDeltaMovement().y > 0.0D) {
            grimPaceLastJumpNanos = System.nanoTime();

            grimDeadCells.clear();
        }
        grimPaceWasOnGround = onGround;
    }

    static boolean grimIntaveFlickOneLine(List<BlockPos> blocks) {
        int lastX = 0;
        int lastY = 0;
        int lastZ = 0;
        boolean lockedOnX = false;
        boolean lockedOnZ = false;
        boolean first = true;
        int yTolerance = 1;
        for (BlockPos block : blocks) {
            if (!first) {
                if (lastY != block.getY()) {
                    if (yTolerance-- <= 0) return false;
                } else {
                    if (lastX == block.getX()) lockedOnX = true;
                    else if (lockedOnX) return false;
                    if (lastZ == block.getZ()) lockedOnZ = true;
                    else if (lockedOnZ) return false;
                }
            }
            lastX = block.getX();
            lastY = block.getY();
            lastZ = block.getZ();
            first = false;
        }
        return lockedOnX || lockedOnZ;
    }

    static boolean grimIntaveMeanWorthHolding(
        float pitch, float lastPitch, long gapMs, boolean oneLine) {
        if (pitch > GRIM_INTAVE_PLACE_PITCH_MIN) return true;
        if (oneLine || gapMs >= GRIM_INTAVE_FLICK_GAP_MS) return false;
        if (!(pitch > GRIM_INTAVE_FLICK_PITCH_MIN)) return false;
        float diff = Math.abs(pitch - lastPitch);
        return diff > GRIM_INTAVE_FLICK_DIFF_MIN && diff < GRIM_INTAVE_FLICK_DIFF_MAX;
    }

    static boolean grimIntaveFlickSafeDiff(float pitch, float lastPitch) {
        float diff = Math.abs(pitch - lastPitch);
        return !(diff > GRIM_INTAVE_FLICK_DIFF_MIN && diff < GRIM_INTAVE_FLICK_DIFF_MAX);
    }

    static float grimIntaveFlickPitchNudge(
        float goal, float lastPitch, double windowLow, double windowHigh) {
        if (grimIntaveFlickSafeDiff(goal, lastPitch)) return goal;
        float bound = (Double.isFinite(windowLow) && Double.isFinite(windowHigh)
            && windowLow <= windowHigh && windowLow > GRIM_PLACE_MAX_PITCH)
            ? GRIM_PLACE_PITCH_PARK : GRIM_PLACE_MAX_PITCH;
        double lo = Double.isFinite(windowLow) ? windowLow : Double.NEGATIVE_INFINITY;
        double hi = Math.min(Double.isFinite(windowHigh) ? windowHigh : Double.POSITIVE_INFINITY, bound);
        if (lo > hi) return goal;
        float sign = Math.signum(goal - lastPitch);
        float below = (float) Mth.clamp(lastPitch
            + sign * (GRIM_INTAVE_FLICK_DIFF_MIN - GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN), lo, hi);
        float above = (float) Mth.clamp(lastPitch
            + sign * (GRIM_INTAVE_FLICK_DIFF_MAX + GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN), lo, hi);
        Float chosen = null;
        if (grimIntaveFlickSafeDiff(below, lastPitch)) chosen = below;
        if (grimIntaveFlickSafeDiff(above, lastPitch)
            && (chosen == null || Math.abs(above - goal) < Math.abs(chosen - goal))) {
            chosen = above;
        }
        return chosen == null ? goal : chosen;
    }

    private float grimIntaveFlickPitchGoal(
        float goal, double windowLow, double windowHigh, Direction face, BlockPos placedBlock) {
        if (!isGrimFamily() || !grimIntaveRecordsPlacement(face)) return goal;
        if (Float.isNaN(grimIntavePlacePitch) || !(goal > GRIM_INTAVE_FLICK_PITCH_MIN)) return goal;
        long gap = grimIntavePlaceGap(System.nanoTime());
        if (gap < 0L || gap >= GRIM_INTAVE_FLICK_GAP_MS) return goal;
        if (grimIntaveFlickSafeDiff(goal, grimIntavePlacePitch)) return goal;
        List<BlockPos> cells = new ArrayList<>(grimIntavePlaceCells.size() + 1);
        if (gap < GRIM_INTAVE_FLICK_CELL_TTL_MS) cells.addAll(grimIntavePlaceCells);
        while (cells.size() > GRIM_INTAVE_FLICK_CELLS - 1) cells.remove(0);
        cells.add(placedBlock);
        if (!grimIntaveFlickOneLine(cells) && !grimIntaveRotationArmable()) return goal;
        return grimIntaveFlickPitchNudge(goal, grimIntavePlacePitch, windowLow, windowHigh);
    }

    static float grimIntaveSafeShipPitch(float lastPitch, long gapMs) {
        float park = GRIM_INTAVE_ROTATION_SAFE_PITCH;
        if (Float.isNaN(lastPitch) || gapMs < 0L || gapMs >= GRIM_INTAVE_FLICK_GAP_MS) {
            return park;
        }
        float bandTop = lastPitch + (GRIM_INTAVE_FLICK_DIFF_MIN - GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN);
        if (bandTop >= park || !(bandTop > GRIM_INTAVE_FLICK_PITCH_MIN)) return park;
        return bandTop;
    }

    static float grimIntaveRotationPitchDecision(float goal, double windowLow,
        boolean armable, boolean shallowAhead, boolean parkAllowed, float safePitch) {
        if (!armable || !(goal > safePitch)) return goal;
        if (Double.isFinite(windowLow) && windowLow <= safePitch) {
            return safePitch;
        }
        if (shallowAhead && parkAllowed) return safePitch;
        return goal;
    }

    static boolean grimIntaveShallowAhead(Vec3 eye, Vec3 step, BlockPos support, Direction face,
        float yaw, float safePitch, int lookaheadTicks) {
        if (step.lengthSqr() < 1.0E-6D) return false;
        Vec3 travelled = Vec3.ZERO;
        Vec3 perTick = step;
        for (int k = 1; k <= lookaheadTicks; k++) {
            travelled = travelled.add(perTick);
            perTick = perTick.scale(GRIM_LANE_AIR_DRAG);
            double[] win = grimFaceCrossingWindow(eye.add(travelled), support, face, yaw);
            if (win != null && win[0] <= safePitch) return true;
        }
        return false;
    }

    private boolean grimIntaveRotationArmable() {
        if (grimIntavePlaceGaps.size() < GRIM_PACE_SAMPLES - 1) return true;
        long candidate = grimIntavePlaceGap(System.nanoTime());
        if (candidate < 0L) return true;
        long[] recent = new long[grimIntavePlaceGaps.size()];
        int index = 0;
        for (long gap : grimIntavePlaceGaps) recent[index++] = gap;
        return grimPaceProspectiveMean(recent, candidate, GRIM_PACE_SAMPLES)
            < GRIM_INTAVE_PLACE_MEAN_MS * GRIM_PACE_SAFETY;
    }

    private BlockPos grimIntaveParkSupport;
    private Direction grimIntaveParkFace;
    private int grimIntaveParkTicks;
    private int grimIntaveParkClientTick = Integer.MIN_VALUE;

    private boolean grimIntaveParkAdvance(BlockPos support, Direction face, int maxTicks) {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (!support.equals(grimIntaveParkSupport) || face != grimIntaveParkFace) {
            grimIntaveParkSupport = support.immutable();
            grimIntaveParkFace = face;
            grimIntaveParkTicks = 1;
            grimIntaveParkClientTick = tick;
            return true;
        }
        if (tick != grimIntaveParkClientTick) {
            grimIntaveParkTicks++;
            grimIntaveParkClientTick = tick;
        }
        return grimIntaveParkTicks <= maxTicks;
    }

    private boolean grimParkStarvesChain(BlockPos placed) {
        return MC.player != null && !MC.player.onGround() && grimLandingChainCell(placed);
    }

    private void grimIntaveParkClear() {
        grimIntaveParkSupport = null;
        grimIntaveParkFace = null;
        grimIntaveParkTicks = 0;
        grimIntaveParkClientTick = Integer.MIN_VALUE;
    }

    private float grimIntaveRotationPitchGoal(float goal, double windowLow, Direction face,
        Vec3 probeEye, Vec3 leadStep, float yaw, BlockPos support) {
        if (!isGrimFamily() || !grimIntaveRecordsPlacement(face)) return goal;

        float safe = grimIntaveSafeShipPitch(
            grimIntavePlacePitch, grimIntavePlaceGap(System.nanoTime()));
        if (!(goal > safe) || !grimIntaveRotationArmable()) return goal;
        boolean grounded = MC.player != null && MC.player.onGround();
        boolean immediate = Double.isFinite(windowLow) && windowLow <= safe;
        if (immediate && support.equals(grimIntaveParkSupport) && face == grimIntaveParkFace) {
            grimIntaveParkClear();
        }
        boolean shallowAhead = !immediate && !grimCourseFrozen()
            && !grimParkStarvesChain(support.relative(face))
            && grimIntaveShallowAhead(probeEye, leadStep, support, face, yaw, safe,
                grounded ? GRIM_INTAVE_PARK_LOOKAHEAD_GROUNDED_TICKS
                    : GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS);
        boolean parkAllowed = shallowAhead && grimIntaveParkAdvance(support, face,
            grounded ? GRIM_INTAVE_PARK_MAX_GROUNDED_TICKS
                : GRIM_INTAVE_ROTATION_PARK_MAX_TICKS);
        return grimIntaveRotationPitchDecision(
            goal, windowLow, true, shallowAhead, parkAllowed, safe);
    }

    static boolean grimPaceOneLine(List<BlockPos> booked) {
        if (booked.size() < 2) return true;
        BlockPos first = booked.get(0);
        boolean sameX = true;
        boolean sameZ = true;
        for (BlockPos pos : booked) {
            if (pos.getY() != first.getY()) return false;
            if (pos.getX() != first.getX()) sameX = false;
            if (pos.getZ() != first.getZ()) sameZ = false;
        }
        return sameX || sameZ;
    }

    static boolean grimPaceYawBanded(float emittedYaw) {
        double band = Math.abs(emittedYaw) % 90.0D;
        return band < 10.0D || band > 80.0D;
    }

    static long grimPaceLimitMs(
        boolean oneLine, boolean banded, boolean sneakedRecently, boolean recentJump) {
        if (!oneLine) return banded || !sneakedRecently ? 300L : 150L;
        if (recentJump) return 300L;
        if (banded) return sneakedRecently ? 350L : 500L;
        return sneakedRecently ? 200L : 350L;
    }

    static double grimPaceProspectiveMean(long[] recent, long candidateMs, int capacity) {
        int keep = Math.min(recent.length, capacity - 1);
        double sum = candidateMs;
        for (int i = recent.length - keep; i < recent.length; i++) sum += recent[i];
        return sum / (keep + 1.0D);
    }

    private boolean grimSneakedRecently() {
        int since = grimTicksSince(AutismSharedState.get().getClientTickCounter(), grimLastSneakTick);
        return since >= 0 && since <= GRIM_INTAVE_SNEAK_MEMORY_TICKS;
    }

    private int grimLastSneakTick = Integer.MIN_VALUE;

    private int grimSneakHoldTicks;

    private boolean grimWireSneak(boolean wanted) {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (grimSneakHoldTicks > 0) {
            grimSneakHoldTicks--;
            grimLastSneakTick = tick;
            return true;
        }
        if (!wanted && !(grimBridgeRunning(tick) && grimSneakRefreshDue(grimTicksSince(tick, grimLastSneakTick)))) {
            return false;
        }
        grimSneakHoldTicks = GRIM_SNEAK_MIN_HOLD_TICKS - 1;
        grimLastSneakTick = tick;
        return true;
    }

    static boolean grimSneakRefreshDue(int sinceSneak) {
        return sinceSneak < 0 || sinceSneak >= GRIM_SNEAK_REFRESH_TICKS;
    }

    private int grimAimOccludedTicks;

    private final Map<BlockPos, Integer> grimDeadCells = new HashMap<>();

    private static final int GRIM_DEAD_CELL_TICKS = 10;
    private static final int GRIM_DEAD_CELL_MAX = 32;

    private void grimMarkCellDead(BlockPos cell) {
        if (cell == null) return;
        int tick = AutismSharedState.get().getClientTickCounter();
        grimDeadCells.entrySet().removeIf(
            entry -> grimTicksSince(tick, entry.getValue()) >= GRIM_DEAD_CELL_TICKS);
        if (grimDeadCells.size() >= GRIM_DEAD_CELL_MAX) return;
        grimDeadCells.put(cell.immutable(), tick);
    }

    private boolean grimCellOnCooldown(BlockPos cell) {
        if (grimDeadCells.isEmpty()) return false;
        Integer marked = grimDeadCells.get(cell);
        if (marked == null) return false;
        if (grimTicksSince(AutismSharedState.get().getClientTickCounter(), marked)
            >= GRIM_DEAD_CELL_TICKS) {
            grimDeadCells.remove(cell);
            return false;
        }
        return MC.player == null || !grimBoxOverColumn(MC.player.position(), cell);
    }

    static final double GRIM_EYE_PAST_FACE_MARGIN = 0.05D;

    private boolean grimEyePastTargetFace(PlacementTarget target) {
        return grimEyePastPlane(MC.player.getEyePosition(), target.supportBlock(), target.face())
            > GRIM_EYE_PAST_FACE_MARGIN;
    }

    public static void endMovementTick() {

    }

    private boolean grimBridgeRunning(int tick) {
        return lastGrimPlacementTick != Integer.MIN_VALUE
            && tick - lastGrimPlacementTick <= GRIM_BRIDGE_ACTIVE_TICKS;
    }

    private int grimLiveTraceTicks = -1;

    private int grimTraceLastPlaceTick = Integer.MIN_VALUE;
    private int tellyLiveTraceTicks = -1;
    private int tellyTracePrintedTick = Integer.MIN_VALUE;

    private boolean tellyTraceDelay;

    private PlacementTarget grimAimHoldTarget;

    private BlockPos grimUpFaceSwapCell;

    private double grimTraceDiagonalPaceMean = Double.NaN;

    private Direction grimCrossingWaitFace;
    private int grimCrossingWaitTick = Integer.MIN_VALUE;

    private double grimXingStandSpent;
    private int grimXingStandTick = Integer.MIN_VALUE;
    private int grimAimHoldTick = -1;

    private boolean grimAimHoldServed;

    private int grimAimHoldServedTick = Integer.MIN_VALUE;

    private PlacementTarget grimStickyTarget;
    private int grimStickySetTick = -1;

    private int grimStickyBandMissTicks;
    private int grimStickyBandTick = Integer.MIN_VALUE;

    enum GrimPlacementAttemptState {
        IDLE,
        ARMED,
        SENT,
        PREDICTED,
        RECONCILING,
        FAILED
    }

    record GrimAttemptDecision(GrimPlacementAttemptState state, String failure) {}

    static boolean grimAttemptBlocksRearm(GrimPlacementAttemptState state) {
        return state == GrimPlacementAttemptState.ARMED
            || state == GrimPlacementAttemptState.SENT
            || state == GrimPlacementAttemptState.RECONCILING;
    }

    static long grimNextAttemptGeneration(long previous) {
        return Math.incrementExact(Math.max(0L, previous));
    }

    static boolean grimAckCovers(int sequence, int acknowledged) {
        return sequence >= 0 && acknowledged >= sequence;
    }

    static GrimAttemptDecision grimReduceAttempt(
        GrimPlacementAttemptState state, int age, boolean resultSeen, boolean resultConsumed,
        boolean solid, boolean ackCovered
    ) {
        if (state == null) state = GrimPlacementAttemptState.IDLE;
        if (state == GrimPlacementAttemptState.IDLE || state == GrimPlacementAttemptState.FAILED) {
            return new GrimAttemptDecision(state, null);
        }
        if (resultSeen && !resultConsumed) {
            return new GrimAttemptDecision(GrimPlacementAttemptState.FAILED, "use");
        }
        if (state == GrimPlacementAttemptState.ARMED) {
            return age >= GRIM_PLACEMENT_RECONCILE_TICKS
                ? new GrimAttemptDecision(GrimPlacementAttemptState.FAILED, "not-sent")
                : new GrimAttemptDecision(state, null);
        }
        if (resultSeen && resultConsumed && solid) {
            return new GrimAttemptDecision(GrimPlacementAttemptState.PREDICTED, null);
        }
        if (state == GrimPlacementAttemptState.PREDICTED && !solid) {
            state = GrimPlacementAttemptState.RECONCILING;
        } else if (state == GrimPlacementAttemptState.SENT && resultSeen && resultConsumed) {
            state = GrimPlacementAttemptState.RECONCILING;
        }
        if (state == GrimPlacementAttemptState.RECONCILING) {
            if (ackCovered) {
                return new GrimAttemptDecision(GrimPlacementAttemptState.FAILED, "ack-air");
            }
            if (age >= GRIM_PLACEMENT_RECONCILE_TICKS) {
                return new GrimAttemptDecision(GrimPlacementAttemptState.FAILED, "timeout-air");
            }
        } else if (state == GrimPlacementAttemptState.SENT
            && age >= GRIM_PLACEMENT_RECONCILE_TICKS) {
            return new GrimAttemptDecision(GrimPlacementAttemptState.FAILED, "result-timeout");
        }
        return new GrimAttemptDecision(state, null);
    }

    private record GrimPredictedPlacement(int sequence, BlockPos cell) {}

    record GrimQueuedUse(
        long generation, int ordinal, InteractionHand hand,
        BlockPos placed, BlockPos against, Direction face,
        AutismRotationUtil.Rotation clickRotation
    ) {}

    record GrimFinalUseWrite(
        int sequence, BlockPos support, Direction face, Vec3 location, long nanos,
        InteractionHand hand, GrimQueuedUse queued, AutismServerRotationView.WireSnapshot wire
    ) {
        GrimFinalUseWrite(
            int sequence, BlockPos support, Direction face, Vec3 location, long nanos
        ) {
            this(sequence, support, face, location, nanos, InteractionHand.MAIN_HAND, null, null);
        }
    }

    private record GrimFinalMoveWrite(
        boolean onGround, boolean horizontalCollision, boolean hasPosition
    ) {}

    private static final ConcurrentLinkedQueue<GrimFinalUseWrite> GRIM_FINAL_USE_WRITES =
        new ConcurrentLinkedQueue<>();
    private static final AtomicReference<GrimFinalMoveWrite> GRIM_FINAL_MOVE_WRITE =
        new AtomicReference<>();

    private static final ReferenceQueue<Packet<?>> GRIM_QUEUED_USE_GC = new ReferenceQueue<>();
    private static final Map<GrimPacketIdentity, GrimQueuedUse> GRIM_QUEUED_USES = new HashMap<>();

    private static final class GrimPacketIdentity extends WeakReference<Packet<?>> {
        private final int identityHash;

        GrimPacketIdentity(Packet<?> packet) {
            super(packet);
            identityHash = System.identityHashCode(packet);
        }

        GrimPacketIdentity(Packet<?> packet, ReferenceQueue<Packet<?>> queue) {
            super(packet, queue);
            identityHash = System.identityHashCode(packet);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GrimPacketIdentity identity)) return false;
            Packet<?> packet = get();
            return packet != null && packet == identity.get();
        }
    }

    private static void purgeCollectedGrimQueuedUses() {
        GrimPacketIdentity collected;
        while ((collected = (GrimPacketIdentity) GRIM_QUEUED_USE_GC.poll()) != null) {
            GRIM_QUEUED_USES.remove(collected);
        }
    }

    private PlacementTarget grimRealPendingTarget;
    private MovementLine grimRealPendingLine;
    private Vec3 grimRealPendingFallOff;
    private int grimRealQueuedTick = Integer.MIN_VALUE;
    private GrimPlacementAttemptState grimAttemptState = GrimPlacementAttemptState.IDLE;
    private long grimAttemptGeneration;
    private long grimNextAttemptGeneration;
    private InteractionHand grimAttemptHand;

    private boolean grimAttemptBuildsPlannedCell;
    private int grimAttemptSubmittedCount;
    private boolean grimAttemptDuplicateSubmitted;
    private int grimAttemptWriteCount;
    private AutismRotationUtil.Rotation grimCommittedClickRotation;
    private AutismRotationUtil.Rotation grimCommittedPreviousRotation;
    private int grimAttemptSequence = -1;
    private boolean grimAttemptResultSeen;
    private boolean grimAttemptResultConsumed;
    private boolean grimAttemptPaceBooked;
    private String grimAttemptResult = "--";
    private final ArrayDeque<GrimPredictedPlacement> grimPredictedPlacements = new ArrayDeque<>();

    private final Map<BlockPos, Integer> grimUntrustedPredictions = new HashMap<>();
    private ClientLevel grimPredictionLevel;
    private volatile int grimHighestObservedAck = Integer.MIN_VALUE;
    private int grimHighestProcessedAck = Integer.MIN_VALUE;
    private boolean grimFinalMoveSeen;
    private boolean grimFinalWireGround;
    private boolean grimFinalWireHorizontalCollision;
    private boolean grimFinalWireHasPosition;

    private int grimSprintNoForwardTick = Integer.MIN_VALUE;

    private BlockPos grimEffCell;

    private Vec3 grimPrevTickPos;
    private Vec3 grimLastTickStep = Vec3.ZERO;

    private float grimLaneInputBias;

    private int grimInputForwardOctant;
    private int grimInputSidewaysOctant;
    private int grimInputSidewaysHold;

    private boolean grimInputSidewaysFromCorrection;
    private float grimInputRawForward;
    private float grimInputRawSideways;

    private float grimInputDeltaYaw = Float.NaN;

    private int grimLaneCoastTicks;

    enum GrimReservationNeed {
        CONNECTOR,
        SUPPORT,
        RISER,
        READY
    }

    static GrimReservationNeed grimReservationNeed(
        boolean supportSolid, int supportDeficit, boolean riserSolid
    ) {
        if (!supportSolid) {
            return supportDeficit <= 1
                ? GrimReservationNeed.SUPPORT : GrimReservationNeed.CONNECTOR;
        }
        return riserSolid ? GrimReservationNeed.READY : GrimReservationNeed.RISER;
    }

    private BlockPos grimLaunchReservedSupport;
    private BlockPos grimLaunchReservedConnector;
    private BlockPos grimLaunchReservedRiser;

    private BlockPos grimLaunchReservedStep;
    private boolean grimLaunchReservationAirborne;
    private String grimLaunchReservationStage = "--";

    private boolean grimPhysicalClimbIntent;

    private int grimLastRescueTick = Integer.MIN_VALUE;

    private int grimPitchFreedTick = Integer.MIN_VALUE;

    private boolean grimTraceEdgeDanger;
    private boolean grimTraceFallDanger;
    private boolean grimTraceLateralBrink;

    private String grimTraceBrake = "--";
    private String grimTraceArcCarry = "--";
    private double grimTraceArcTravel;

    private String grimTraceArcStand = "--";

    private Vec3 grimArcTravelStart;

    private boolean grimTraceFootingOwed;
    private int grimFootingOwedTicks;

    private boolean grimTracePaceBrink;

    private boolean grimTraceLastChance;

    private String grimTraceJump = "-";

    private BlockPos grimTraceRiseTakeoff;
    private boolean grimTraceRiseAllowed;
    private boolean grimTraceClickFeasible;

    private boolean grimTraceClickLands;

    private String grimTraceLaneAnchor = "cam";

    private String grimTraceRiserFail = "--";

    private String grimTraceLaunchLedger = "--";

    private final StringBuilder grimTraceStrip = new StringBuilder();

    private String grimTraceClickNumbers = "--";

    private int grimArcStartTick = -1;

    private int grimArcStartClientTick = -1;
    private Vec3 grimArcStartPos;
    private String grimArcStartGoal = "--";
    private int grimArcPlaceCount;

    private int grimArcSetCount;
    private int grimArcAimTicks;
    private int grimArcPaceTicks;
    private int grimArcNoTargetTicks;
    private int grimArcDropTicks;

    private String grimTraceReserveWhy = "--";

    private BlockPos grimArcChainSupport;
    private BlockPos grimArcChainRiser;
    private int grimArcChainSupportOk = -1;
    private int grimArcChainSupportSet = -1;
    private int grimArcChainRiserFirst = -1;
    private int grimArcChainRiserLast = -1;
    private int grimArcChainRiserSet = -1;
    private String grimArcChainRiserWhy = "--";

    private String grimArcChainRiserFail = "--";
    private int grimArcChainRelatch;

    private String grimTraceWhy = "--";

    private String grimTraceRiseDropWhy = "--";

    private long grimTracePaceSince = -1;
    private long grimTracePaceFloor = -1;

    private boolean grimTracePaceIntave;

    private boolean grimTracePrevGnd = true;
    private boolean grimTraceFallNoted;

    private int grimSegTicks;
    private int grimSegPlaces;
    private int grimSegSettled;
    private int grimSegMiss;
    private int grimSegAim;
    private int grimSegPace;
    private int grimSegNoTarget;
    private int grimSegDrop;
    private int grimSegReplan;
    private int grimSegVeto;
    private int grimSegRow = Integer.MIN_VALUE;

    private int grimEffCellRefreshTick = Integer.MIN_VALUE;

    private String grimLastPlanFail = "?";

    private final StringBuilder grimPlanDetail = new StringBuilder();

    private String grimLastGoalEye = "?";

    private String grimLastPick = "?";

    private BlockPos grimPinSupport;
    private Direction grimPinFace;

    private BlockPos grimStaleSupport;
    private Direction grimStaleFace;
    private int grimStaleTicks;

    private float grimTracePrevYaw = Float.NaN;
    private float grimTracePrevPitch = Float.NaN;
    private double grimTraceYawSum;
    private double grimTracePitchSum;
    private int grimTraceMoveTicks;
    private int grimTracePrevTick = Integer.MIN_VALUE;

    private float grimTracePrevSentYaw = Float.NaN;

    private boolean traceArmed() {
        return grimLiveTraceTicks >= 0;
    }

    private void traceGrim(String outcome, PlacementTarget target) {
        if (!traceArmed() || MC.player == null) return;
        if (grimLiveTraceTicks == 0) {
            autismclient.util.AutismTraceLog.println("[scaffold-live] capture start");

            autismclient.util.AutismTraceLog.println("[scaffold-why] legend"
                + " lr=the reservation ledger, advisory only - it decides no key since the 12:52"
                + " ruling (known=landing solved, deficit=blocks still owed, pend=click already on"
                + " the wire, landsolid=is the landing column's support already a block - winners"
                + " take off T, losers F);"
                + " strip=<stage>:<-removed+added> per stage, F/B/L/R/J/S/P keys, net=physical vs"
                + " emitted - the jump and travel keys are ALWAYS the player's own; the only"
                + " writers left are wire-sneak/lip-stop/reconcile/empty-stack (each sneak-only),"
                + " lane (grounded steering) and the sprint drop for the non-sprint predictor;"
                + " clk=<why code> past=<eye past face plane>/<margin needed>"
                + " demand=<pitch the crossing needs>/<hard cap> drop=<eye height over the face>;"
                + " rsv=<reserved chain cell>:<its own verdict> - oob/cooldown/behind/solid/"
                + "no-repl/rowlock/ok or plan[<per-face codes>], where ip:<box|side|strict>"
                + " is the body-clearance refusal and strict means the CATCH PREDICATE was shut;"
                + " pick=<selection tier> plan=<last plan reject>;"
                + " arcend=one line per landing, dy=+0.00 placed=0 is a jump that achieved nothing;"
                + " arcchain=the same arc's two-click window - the landing support (planned/became"
                + " a block) against every tick the riser above it was body-legal. clear ending"
                + " BEFORE set means the level was never placeable, not merely missed");
        }
        Vec3 p = MC.player.position();
        Vec3 v = MC.player.getDeltaMovement();

        if (grimTracePrevGnd && !MC.player.onGround() && grimJumpKeyHeld()) {
            autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
                "[scaffold-live] t%03d takeoff     vel=%.3f,%.3f,%.3f arc=%s foot=%s",
                grimLiveTraceTicks, v.x, v.y, v.z, traceArcBudget(), traceFootCell()));
            grimArcStartTick = grimLiveTraceTicks;
            grimArcStartClientTick = AutismSharedState.get().getClientTickCounter();
            grimArcStartPos = p;
            grimArcStartGoal = (grimLaunchReservedSupport == null
                ? "--" : grimLaunchReservedSupport.toShortString()) + ":" + grimLaunchReservationStage;
            grimArcPlaceCount = 0;
            grimArcSetCount = 0;
            grimArcAimTicks = 0;
            grimArcPaceTicks = 0;
            grimArcNoTargetTicks = 0;
            grimArcDropTicks = 0;
        }
        if (!MC.player.onGround()) grimArcNote(outcome);
        grimSampleArcChain();

        if (!grimTracePrevGnd && MC.player.onGround() && grimArcStartTick >= 0) {
            autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
                "[scaffold-why] t%03d arcend     from=t%03d ticks=%d lines=%d dy=%+.2f dxz=%.2f"
                    + " placed=%d set=%d aim=%d pace=%d notgt=%d drop=%d goal=%s end=%s",
                grimLiveTraceTicks, grimArcStartTick,
                grimArcStartClientTick < 0 ? -1
                    : AutismSharedState.get().getClientTickCounter() - grimArcStartClientTick,
                grimLiveTraceTicks - grimArcStartTick,
                grimArcStartPos == null ? Double.NaN : p.y - grimArcStartPos.y,
                grimArcStartPos == null ? Double.NaN
                    : Math.hypot(p.x - grimArcStartPos.x, p.z - grimArcStartPos.z),
                grimArcPlaceCount, grimArcSetCount, grimArcAimTicks, grimArcPaceTicks,
                grimArcNoTargetTicks, grimArcDropTicks, grimArcStartGoal, grimTraceWhy));

            autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
                "[scaffold-why] t%03d arcchain   sup=%s plan=%s set=%s"
                    + " | ris=%s clear=%s..%s set=%s clk=%s rfail=%s relatch=%d land=%s",
                grimLiveTraceTicks, traceCell(grimArcChainSupport),
                traceTick(grimArcChainSupportOk), traceTick(grimArcChainSupportSet),
                traceCell(grimArcChainRiser), traceTick(grimArcChainRiserFirst),
                traceTick(grimArcChainRiserLast), traceTick(grimArcChainRiserSet),
                grimArcChainRiserWhy, grimArcChainRiserFail, grimArcChainRelatch,
                BlockPos.containing(p.x, p.y - 0.05D, p.z).toShortString()));
            grimArcStartTick = -1;
            grimArcStartClientTick = -1;
            grimArcStartPos = null;
            grimArcChainRelatch = 0;
            grimResetArcChain(null);
        }

        if (!grimTraceFallNoted && MC.player != null && !MC.player.onGround()
            && MC.player.fallDistance >= 0.5F && MC.player.getDeltaMovement().y < 0.0D) {
            grimTraceFallNoted = true;
            int placeAge = grimTraceLastPlaceTick == Integer.MIN_VALUE ? -1
                : AutismSharedState.get().getClientTickCounter() - grimTraceLastPlaceTick;
            PlacementTarget held = grimRealPendingTarget != null
                ? grimRealPendingTarget : grimStickyTarget;
            autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
                "[scaffold-live] t%03d fall-start pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f"
                    + " fall=%.1f placeage=%d arc=%s tgt=%s",
                grimLiveTraceTicks, p.x, p.y, p.z, v.x, v.y, v.z, MC.player.fallDistance,
                placeAge, traceArcBudget(), fmtTarget(held)));
        }
        if (MC.player.onGround()) grimTraceFallNoted = false;
        grimTracePrevGnd = MC.player.onGround();

        autismclient.util.AutismTraceLog.println(String.format(
            java.util.Locale.ROOT,
            "[scaffold-live] t%03d %-11s pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f step=%.3f,%.3f "
                + "gnd=%s fall=%.1f sneak=%s twr=%s %s %s %s %s %s %s emitted=%s click=%s goal=%s %s %s src=%s eye=%s eff=%s target=%s %s",
            grimLiveTraceTicks, outcome, p.x, p.y, p.z, v.x, v.y, v.z,
            grimLastTickStep.x, grimLastTickStep.z,
            MC.player.onGround() ? "T" : "F", MC.player.fallDistance,
            grimEdgeSneakActive ? "T" : "F",
            grimTowerActive() ? "T" : "F",
            traceKeys(), traceCourse(), traceLane(), traceLock(), traceGates(), traceArcCarry(),
            fmtRot(grimSilentRotation), fmtRot(grimCommittedClickRotation),
            fmtRot(grimAimPrevGoal), traceRotationBudget(), tracePlacementProtocol(),
            grimLastPick, grimLastGoalEye, grimEffCell == null ? "null" : grimEffCell.toShortString(), fmtTarget(target),
            "place".equals(outcome) || outcome.startsWith("PLACE")
                ? traceClickGeometry(target) : ""));
        traceGrimWhy(target);

        grimTraceStrip.setLength(0);

        grimTraceClickNumbers = "--";
        grimLiveTraceTicks++;
        grimSegNote(outcome);
    }

    private void grimArcNote(String outcome) {
        if (outcome.startsWith("PLACE")) grimArcPlaceCount++;
        else if (outcome.startsWith("settle-ok")) grimArcSetCount++;
        else if (outcome.startsWith("aim-hold")) grimArcAimTicks++;
        else if (outcome.startsWith("pace-hold")) grimArcPaceTicks++;
        else if (outcome.startsWith("no-target")) grimArcNoTargetTicks++;
        else if (outcome.startsWith("rise-drop")) grimArcDropTicks++;
    }

    private void grimSegNote(String outcome) {
        grimSegTicks++;
        if (outcome.startsWith("PLACE")) grimSegPlaces++;
        else if (outcome.startsWith("settle-ok")) grimSegSettled++;
        else if (outcome.startsWith("real-miss")) grimSegMiss++;
        else if (outcome.startsWith("aim-hold")) grimSegAim++;
        else if (outcome.startsWith("pace-hold")) grimSegPace++;
        else if (outcome.startsWith("no-target")) grimSegNoTarget++;
        else if (outcome.startsWith("rise-drop")) grimSegDrop++;
        else if (outcome.startsWith("yaw-veto") || outcome.startsWith("pitch")) grimSegVeto++;
        int row = grimOracleFootingRow();
        if (row == Integer.MIN_VALUE) return;
        if (grimSegRow == Integer.MIN_VALUE) {
            grimSegRow = row;
            return;
        }
        if (row == grimSegRow) return;
        autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
            "[scaffold-live] rollup y=%d>%d ticks=%d place=%d set=%d miss=%d aim=%d pace=%d"
                + " notgt=%d drop=%d repl=%d veto=%d",
            grimSegRow, row, grimSegTicks, grimSegPlaces, grimSegSettled, grimSegMiss,
            grimSegAim, grimSegPace, grimSegNoTarget, grimSegDrop, grimSegReplan, grimSegVeto));
        grimSegRow = row;
        grimSegTicks = 0;
        grimSegPlaces = 0;
        grimSegSettled = 0;
        grimSegMiss = 0;
        grimSegAim = 0;
        grimSegPace = 0;
        grimSegNoTarget = 0;
        grimSegDrop = 0;
        grimSegReplan = 0;
        grimSegVeto = 0;
    }

    private String traceClickGeometry(PlacementTarget target) {
        if (target == null || target.hit() == null) return "hit=--";
        Vec3 loc = target.hit().getLocation();
        BlockPos block = target.hit().getBlockPos();
        int tick = AutismSharedState.get().getClientTickCounter();
        int gap = grimTraceLastPlaceTick == Integer.MIN_VALUE ? -1 : tick - grimTraceLastPlaceTick;
        grimTraceLastPlaceTick = tick;
        AutismRotationUtil.Rotation click = grimCommittedClickRotation;
        float yawOff = click == null ? Float.NaN
            : Mth.wrapDegrees(click.yaw() - grimCourseStepYaw());

        float residual = click == null || grimLaneStep() == COURSE_STEP_UNSET
            ? Float.NaN
            : grimLaneOctantResidual(grimLaneStepYaw(), click.yaw());
        return String.format(java.util.Locale.ROOT,
            "hit=%.3f,%.3f,%.3f face=%s gap=%d yawoff=%.1f res=%.1f",
            loc.x - block.getX(), loc.y - block.getY(), loc.z - block.getZ(),
            target.face(), gap, yawOff, residual);
    }

    private String traceRotationBudget() {
        if (grimSilentRotation == null) return "d=--,-- sum=--";
        float yaw = grimSilentRotation.yaw();
        float pitch = grimSilentRotation.pitch();
        float dYaw = Float.isNaN(grimTracePrevYaw) ? 0.0F : Mth.wrapDegrees(yaw - grimTracePrevYaw);
        float dPitch = Float.isNaN(grimTracePrevPitch) ? 0.0F : pitch - grimTracePrevPitch;
        grimTracePrevYaw = yaw;
        grimTracePrevPitch = pitch;

        float sent = grimSentYaw;
        float rawDelta = Float.isNaN(sent) || Float.isNaN(grimTracePrevSentYaw)
            ? 0.0F : Math.abs(sent - grimTracePrevSentYaw);
        grimTracePrevSentYaw = sent;
        grimTraceYawSum += rawDelta;
        grimTracePitchSum += Math.abs(dPitch);
        if (rawDelta > 1.0E-4F || Math.abs(dPitch) > 1.0E-4F) grimTraceMoveTicks++;

        int tick = AutismSharedState.get().getClientTickCounter();
        int dt = grimTracePrevTick == Integer.MIN_VALUE ? 1 : tick - grimTracePrevTick;
        grimTracePrevTick = tick;
        return String.format(java.util.Locale.ROOT,
            "d=%+.2f,%+.2f sum=%.0f,%.0f mv=%d dt=%d sent=%.2f/%.1f rd=%.2f",
            dYaw, dPitch, grimTraceYawSum, grimTracePitchSum, grimTraceMoveTicks, dt, sent,
            grimSilentRotation == null ? Float.NaN : grimSilentRotation.pitch(), rawDelta);
    }

    private String traceGroundClaim() {
        if (MC.player == null) return " ovl=? set=--";
        return String.format(java.util.Locale.ROOT, " ovl=%.2f/%s set=%s",

            grimFootingOverlap(MC.player.position(), grimOracleFootingRow()),
            MC.player.onGround() ? "T" : "F",
            grimTraceSettledCell == null ? "--" : grimTraceSettledCell.toShortString());
    }

    private BlockPos grimTraceSettledCell;

    private int tellyDriftLastTick = Integer.MIN_VALUE;
    private boolean tellyDriftWasGrounded = true;
    private int tellyDriftTicks;

    private int tellyDriftGroundTicks;
    private int tellyDriftSprintTicks;
    private double tellyDriftResidualSum;
    private double tellyDriftLateralSum;
    private double tellyDriftRunningLateral;
    private double tellyDriftLaneAtTakeoff = Double.NaN;
    private double tellyDriftCourseAtTakeoff = Double.NaN;

    private record TellyDriftSample(
        double octant, double residual, double forwardAccel, double lateralAccel,
        double flipMargin, boolean sprint, boolean grounded) {}

    private int tellyDriftPairTick = Integer.MIN_VALUE;
    private boolean tellyDriftPairGrounded;
    private boolean tellyDriftPairJumped;
    private float tellyDriftPairAnchorYaw = Float.NaN;

    private TellyDriftSample tellyDriftSample() {
        Input emitted = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
        int forwardImpulse = (emitted.forward() ? 1 : 0) - (emitted.backward() ? 1 : 0);
        int sidewaysImpulse = (emitted.left() ? 1 : 0) - (emitted.right() ? 1 : 0);
        double octant = emittedOctantDegrees(forwardImpulse, sidewaysImpulse);
        double flip = tellyStrafeFlipMargin(grimInputDeltaYaw);

        boolean paired =
            tellyDriftPairTick == AutismSharedState.get().getClientTickCounter() - 1;
        if (!paired || Double.isNaN(octant) || Float.isNaN(grimSentYaw)
            || Float.isNaN(tellyDriftPairAnchorYaw)) {
            return new TellyDriftSample(octant, Double.NaN, Double.NaN, Double.NaN, flip,
                emitted.sprint(), tellyDriftPairGrounded);
        }
        double residual = Mth.wrapDegrees(grimSentYaw + octant - tellyDriftPairAnchorYaw);
        double speed = tellyDriftSpeed(tellyDriftPairGrounded, emitted.sprint());
        double radians = residual * Mth.DEG_TO_RAD;

        double forwardAccel = speed * Math.cos(radians);
        double lateralAccel = -speed * Math.sin(radians);
        if (tellyDriftPairJumped && emitted.sprint()) {

            double impulse = (residual - octant) * Mth.DEG_TO_RAD;
            forwardAccel += 0.2D * Math.cos(impulse);
            lateralAccel += -0.2D * Math.sin(impulse);
        }
        return new TellyDriftSample(octant, residual, forwardAccel, lateralAccel, flip,
            emitted.sprint(), tellyDriftPairGrounded);
    }

    static double tellyDriftSpeed(boolean groundedWhenEmitted, boolean sprintWhenEmitted) {
        return (groundedWhenEmitted ? 0.1D : TELLY_AIR_CONTROL)
            * (sprintWhenEmitted ? 1.3D : 1.0D);
    }

    static double tellyDriftNextPerp(
        double perp, double lateralAccel, boolean groundedWhenEmitted) {
        return (perp + lateralAccel)
            * (groundedWhenEmitted ? GRIM_GROUND_TAKEOFF_DRAG : TELLY_AIR_DRAG);
    }

    private String traceTellyDrift(TellyDriftSample drift) {
        return "oct=" + (Double.isNaN(drift.octant()) ? "--" : fmtDeg(drift.octant()))
            + " res=" + fmtDeg(drift.residual())
            + " afwd=" + fmtAccel(drift.forwardAccel())
            + " alat=" + fmtAccel(drift.lateralAccel())
            + " dlat=" + fmtAccel(tellyDriftRunningLateral)
            + " drg=" + String.format(java.util.Locale.ROOT, "%.3f",
                drift.grounded() ? GRIM_GROUND_TAKEOFF_DRAG : TELLY_AIR_DRAG)
            + " spr=" + (drift.sprint() ? "T" : "F")
            + " flip=" + (Double.isNaN(drift.flipMargin()) ? "--"
                : String.format(java.util.Locale.ROOT, "%.0f/%.0f",
                    drift.flipMargin(), TELLY_LANE_BIAS_MAX));
    }

    private static String fmtDeg(double value) {
        return Double.isNaN(value) ? "--" : String.format(java.util.Locale.ROOT, "%+.1f", value);
    }

    private static String fmtAccel(double value) {
        return Double.isNaN(value) ? "--" : String.format(java.util.Locale.ROOT, "%+.4f", value);
    }

    private double tellyCourseCoordinate(Vec3 pos) {
        Vec3 forward = tellyForwardVector();
        if (forward.horizontalDistanceSqr() <= 1.0E-8D) return Double.NaN;
        return pos.dot(forward.normalize());
    }

    private void advanceTellyDrift(TellyDriftSample drift, double laneError, Vec3 pos) {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == tellyDriftLastTick) return;
        tellyDriftLastTick = tick;
        if (!Double.isNaN(drift.lateralAccel())) tellyDriftRunningLateral += drift.lateralAccel();

        boolean grounded = MC.player.onGround();
        double course = tellyCourseCoordinate(pos);
        if (!grounded) {
            if (tellyDriftWasGrounded) {
                tellyDriftTicks = 0;
                tellyDriftSprintTicks = 0;
                tellyDriftResidualSum = 0.0D;
                tellyDriftLateralSum = 0.0D;
            }
            tellyDriftTicks++;
            if (drift.sprint()) tellyDriftSprintTicks++;
            if (!Double.isNaN(drift.residual())) tellyDriftResidualSum += Math.abs(drift.residual());
            if (!Double.isNaN(drift.lateralAccel())) tellyDriftLateralSum += drift.lateralAccel();
        } else {
            if (!tellyDriftWasGrounded && tellyDriftTicks > 0) {

                printTellyCycle(laneError, course);
                tellyDriftLaneAtTakeoff = laneError;
                tellyDriftCourseAtTakeoff = course;
                tellyDriftGroundTicks = 0;
            }
            tellyDriftGroundTicks++;
        }
        tellyDriftWasGrounded = grounded;
    }

    static double tellyCycleBlocksPerSecond(double travel, int groundTicks, int airTicks) {
        int ticks = groundTicks + airTicks;
        return ticks <= 0 || Double.isNaN(travel) ? Double.NaN : travel * 20.0D / ticks;
    }

    private void printTellyCycle(double laneError, double course) {
        double laneMoved = Double.isNaN(tellyDriftLaneAtTakeoff)
            ? Double.NaN : laneError - tellyDriftLaneAtTakeoff;
        double travel = Double.isNaN(tellyDriftCourseAtTakeoff)
            ? Double.NaN : Math.abs(course - tellyDriftCourseAtTakeoff);
        double bps = tellyCycleBlocksPerSecond(travel, tellyDriftGroundTicks, tellyDriftTicks);
        autismclient.util.AutismTraceLog.println(
            "[telly-cycle] air=" + tellyDriftTicks + " ground=" + tellyDriftGroundTicks
                + " sprint=" + tellyDriftSprintTicks + "/" + tellyDriftTicks
                + " res=" + fmtDeg(tellyDriftTicks == 0
                    ? Double.NaN : tellyDriftResidualSum / tellyDriftTicks)
                + " dv=" + fmtAccel(tellyDriftLateralSum)
                + " dlane=" + fmtAccel(laneMoved)
                + " travel=" + fmtAccel(travel)
                + " bps=" + fmtAccel(bps));
    }

    private void traceTelly(String outcome) {
        if (!TELLY_LIVE_TRACE || MC.player == null || tellyLiveTraceTicks < 0) return;
        if (tellyLiveTraceTicks == 0) {
            autismclient.util.AutismTraceLog.println("[telly-live] capture start");
        }
        Vec3 pos = MC.player.position();
        Vec3 vel = MC.player.getDeltaMovement();
        double laneError = tellyLastBridge == null ? Double.NaN
            : tellyLaneCenter - laneCoordinate(pos, tellyAnchorYaw);
        double perp = vel.dot(tellyLeftVector());
        TellyDriftSample drift = tellyDriftSample();
        advanceTellyDrift(drift, laneError, pos);
        autismclient.util.AutismTraceLog.println(String.format(
            java.util.Locale.ROOT,
            "[telly-live] t%03d %-9s pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f step=%.3f,%.3f "
                + "gnd=%s fall=%.1f sneak=%s %s anchor=%.0f emitted=%s %s "
                + "lane=%+.3f/perp%+.3f/bias%+.1f/steer%+.1f%s off=%s "
                + "air=%s/cd%d/p%d hold=%s phase=%s motion=%s "
                + "bridgeY=%d bridge=%s tgt=%s raised=%s queued=%s cd=%d fail=%d delay=%s aimed=%s "
                + "%s",
            tellyLiveTraceTicks, outcome, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z,
            grimLastTickStep.x, grimLastTickStep.z,
            MC.player.onGround() ? "T" : "F", MC.player.fallDistance,
            tellySneakThisTick ? "T" : "F",
            traceKeys(), tellyAnchorYaw, fmtRot(grimSilentRotation), traceRotationBudget(),
            laneError, perp, grimLaneInputBias, tellyGroundSteerOffset,
            tellyGroundSteeringActive ? "*" : "", tellyLandingOffLaneSafe() ? "T" : "F",
            tellyAirStrafeThisTick, tellyAirStrafeCooldown, tellyAirStrafePulses,
            tellyHoldStrafe, tellyPhase, tellyMotion,
            tellyBridgeY, tellyLastBridge == null ? "--" : tellyLastBridge.toShortString(),
            tellyTarget == null ? "--" : tellyTarget.target().placedBlock().toShortString(),
            tellyTarget != null && tellyTarget.raised() ? "T" : "F",
            tellyPlacementQueued ? "T" : "F", tellyClickCooldown, tellyFailedClicks,
            tellyTraceDelay ? "T" : "F", tellyAimCommitted ? "T" : "F",
            traceTellyDrift(drift)));
        tellyLiveTraceTicks++;
    }

    private boolean tellyLandingOffLaneSafe() {
        try {
            return MC.player != null && tellyLastBridge != null && tellyLandingOffLane(MC.player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void traceTellyTick() {
        if (!TELLY_LIVE_TRACE) return;
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == tellyTracePrintedTick) return;
        tellyTracePrintedTick = tick;
        traceTelly(tellyOwnsInput ? "tick" : "idle");

        tellyDriftPairTick = tick;
        tellyDriftPairGrounded = MC.player != null && MC.player.onGround();
        tellyDriftPairJumped = tellyJumpThisTick;
        tellyDriftPairAnchorYaw = tellyAnchorYaw;
    }

    private String traceKeys() {
        Input emitted = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
        return "keys=" + (physicallyDown(MC.options.keyUp) ? "W" : "-")
            + (physicallyDown(MC.options.keyLeft) ? "A" : "-")
            + (physicallyDown(MC.options.keyDown) ? "S" : "-")
            + (physicallyDown(MC.options.keyRight) ? "D" : "-")
            + (grimJumpKeyHeld() ? "J" : "-")
            + (physicallyDown(MC.options.keyShift) ? "N" : "-")
            + (physicallyDown(MC.options.keySprint) ? "R" : "-")

            + " inPrev=" + (emitted.forward() ? "W" : "-") + (emitted.left() ? "A" : "-")
            + (emitted.backward() ? "S" : "-") + (emitted.right() ? "D" : "-")
            + (emitted.jump() ? "J" : "-") + (emitted.shift() ? "N" : "-")
            + (emitted.sprint() ? "R" : "-");
    }

    private String traceCourse() {
        String cand = grimCourseStepCandidate == COURSE_STEP_UNSET ? "--"
            : COURSE_STEP_NAMES[grimCourseStepCandidate] + ":" + grimCourseStepDwell;
        return String.format(java.util.Locale.ROOT,
            "crs=%s/%+.0f go=%s/%+.0f cam=%+.0f cand=%s dia=%s frz=%s lin=%+.0f",
            grimCourseStep == COURSE_STEP_UNSET ? "--" : COURSE_STEP_NAMES[grimCourseStep],
            grimCourseStepYaw(),

            grimLaneStep() == COURSE_STEP_UNSET ? "--" : COURSE_STEP_NAMES[grimLaneStep()],
            grimLaneStepYaw(),
            MC.player.getYRot(),
            cand,

            currentMovementLine != null
                && isGrimDiagonalDirection(currentMovementLine.direction()) ? "T" : "F",
            grimCourseFrozen() ? "T" : "F",
            grimSteeredPostureYaw());
    }

    private String traceLane() {
        MovementLine line = currentMovementLine;
        if (line == null || line.direction().horizontalDistanceSqr() <= 1.0E-8D) {
            return "lane=--";
        }
        Vec3 d = line.direction().normalize();
        Vec3 left = new Vec3(d.z, 0.0D, -d.x);
        Vec3 position = MC.player.position();
        double error = grimLaneError(line, position);
        Vec3 velocity = MC.player.getDeltaMovement();
        double perp = new Vec3(velocity.x, 0.0D, velocity.z).dot(left);
        return String.format(java.util.Locale.ROOT, "lane=%+.3f/perp%+.3f",
            error, perp);
    }

    private String traceLock() {
        GrimRowLock lock = isGrimFamily() ? grimActiveRowLock() : null;
        int age = grimStickySetTick < 0 ? -1
            : AutismSharedState.get().getClientTickCounter() - grimStickySetTick;
        String sticky = "stk=" + (grimStickyTarget == null ? "none" : age + "/" + grimStickyBandMissTicks);
        if (lock == null) return "lock=none " + sticky;
        return String.format(java.util.Locale.ROOT, "lock=%s/y%d/p%d/f[%d,%d] %s",
            lock.xAxis() ? "x" : "z", lock.rowY(), lock.rowPerp(),
            lock.frontNeg(), lock.frontPos(), sticky);
    }

    private void grimNoteStrip(String owner, Input before, Input after) {

        if (!traceArmed() || owner == null || before == null || after == null) return;
        StringBuilder diff = new StringBuilder();
        grimNoteBit(diff, 'F', before.forward(), after.forward());
        grimNoteBit(diff, 'B', before.backward(), after.backward());
        grimNoteBit(diff, 'L', before.left(), after.left());
        grimNoteBit(diff, 'R', before.right(), after.right());
        grimNoteBit(diff, 'J', before.jump(), after.jump());
        grimNoteBit(diff, 'S', before.shift(), after.shift());
        grimNoteBit(diff, 'P', before.sprint(), after.sprint());
        if (diff.length() == 0) return;
        if (grimTraceStrip.length() > 0) grimTraceStrip.append(',');
        grimTraceStrip.append(owner).append(':').append(diff);
    }

    private static void grimNoteBit(StringBuilder out, char name, boolean before, boolean after) {
        if (before != after) out.append(after ? '+' : '-').append(name);
    }

    private void grimNoteClickNumbers(PlacementTarget target) {
        if (MC.player == null || target == null) {
            grimTraceClickNumbers = "--";
            return;
        }
        Vec3 eye = MC.player.getEyePosition();
        float yaw = grimSilentRotation != null ? grimSilentRotation.yaw() : MC.player.getYRot();
        float demand = grimSideWindowSolvePitch(eye, target.supportBlock(), target.face(), yaw);

        double xing = grimCrossingFraction(eye, target.supportBlock(), target.face(), yaw);
        grimTraceClickNumbers = String.format(java.util.Locale.ROOT,
            "past=%+.3f/%.2f demand=%s/%.1f drop=%.2f xing=%s",
            grimEyePastPlane(eye, target.supportBlock(), target.face()),
            GRIM_EYE_PAST_FACE_MARGIN,
            Float.isNaN(demand) ? "nan"
                : String.format(java.util.Locale.ROOT, "%.1f", demand),
            GRIM_PLACE_MAX_PITCH_HARD,
            eye.y - (target.supportBlock().getY() + 1.0D - GRIM_PIN_CROSS_DEPTH),
            Double.isNaN(xing) ? "--" : String.format(java.util.Locale.ROOT, "%+.2f", xing));
    }

    private void traceGrimWhy(PlacementTarget target) {
        autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
            "[scaffold-why] t%03d lr=%s | strip=%s | clk=%s %s | rsv=%s | pick=%s plan=%s | tgt=%s",
            grimLiveTraceTicks, grimTraceLaunchLedger,
            grimTraceStrip.length() == 0 ? "none" : grimTraceStrip.toString(),
            grimTraceWhy, grimTraceClickNumbers, grimTraceReserveWhy,
            grimLastPick, grimLastPlanFail.isEmpty() ? "ok" : grimLastPlanFail,
            fmtTarget(target)));
    }

    private String traceGates() {
        return "gate=edge" + (grimTraceEdgeDanger ? "1" : "0")
            + ".fall" + (grimTraceFallDanger ? "1" : "0")
            + ".brink" + (grimTraceLateralBrink ? "1" : "0")

            + ".owed" + (grimTraceFootingOwed ? "1" : "0")
            + ".pbrink" + (grimTracePaceBrink ? "1" : "0")

            + ".last" + (grimTraceLastChance ? "1" : "0")
            + ".nofoot" + (grimNoFootingUnderfoot() ? "1" : "0")
            + ".resc" + (grimRecentlyRescued() ? "1" : "0")
            + ".div" + (grimCourseDiverged() ? "1" : "0")
            + ".rise" + (grimTraceRiseAllowed ? "1" : "0")

            + " brake=" + grimTraceBrake

            + (Double.isNaN(grimTraceDiagonalPaceMean) ? ""
                : String.format(java.util.Locale.ROOT, " dclimb=%.0f/%d",
                    grimTraceDiagonalPaceMean, GRIM_INTAVE_PLACE_MEAN_MS))
            + " lovl=" + grimTraceArcCarry
            + " stand=" + grimTraceArcStand
            + String.format(java.util.Locale.ROOT, " trv=%.2f", grimTraceArcTravel)
            + " jmp=" + grimTraceJump
            + " arc=" + traceArcBudget()
            + " " + traceLaunchReservation()
            + " tkoff=" + (grimTraceRiseTakeoff == null ? "--" : grimTraceRiseTakeoff.toShortString())
            + ":" + grimTraceTakeoffWhy
            + " riser=" + grimTraceRiserFail

            + " click=" + (grimTraceClickLands ? "1" : "0")
            + " free=" + (grimTraceClickFeasible ? "1" : "0")

            + " anch=" + grimTraceLaneAnchor
            + traceGroundClaim()
            + " pin=" + (grimPinFace == null ? "--" : grimPinFace.getName().charAt(0) + "@"
                + (grimPinSupport == null ? "?" : grimPinSupport.toShortString()))

            + (Double.isNaN(grimTraceCrossing) ? " cross=--"
                : String.format(java.util.Locale.ROOT, " cross=%.2f", grimTraceCrossing))
            + " foot=" + traceFootCell()
            + " held=" + planningStack().getCount()

            + " why=" + grimTraceWhy;
    }

    private String traceArcBudget() {
        if (MC.player == null || MC.level == null || MC.player.onGround()) return "--";
        String budget = grimArcTicks + "/" + grimArcPlacements;
        int row = grimOracleFootingRow();

        Vec3 landing = grimDescentCrossing(
            MC.player.position(), MC.player.getDeltaMovement(), row + 1.0D,
            GRIM_DESCENT_LOOKAHEAD_TICKS, GRIM_AIR_COUNTER_IMPULSE);
        if (landing == null) return budget + "@--";
        BlockPos cell = BlockPos.containing(landing.x, row + 0.5D, landing.z);
        if (MC.level.isOutsideBuildHeight(cell)) return budget + "@oob";
        String at = "@" + cell.getX() + "," + cell.getZ();
        int deficit = grimCellDeficit(cell);
        return budget + at + (deficit == 0 ? ":ok" : deficit == 1 ? ":n1" : ":n0");
    }

    private void grimSampleArcChain() {
        if (MC.player == null || MC.level == null || MC.player.onGround()) return;
        BlockPos support = grimLaunchReservedSupport;
        if (support == null) return;

        if (!support.equals(grimArcChainSupport)) {

            if (grimArcChainSupport != null) grimArcChainRelatch++;
            grimResetArcChain(support);
        }
        if (grimArcChainSupportOk < 0 && "support".equals(grimLaunchReservationStage)) {
            grimArcChainSupportOk = grimLiveTraceTicks;
        }
        if (grimArcChainSupportSet < 0 && solidAt(grimArcChainSupport)) {
            grimArcChainSupportSet = grimLiveTraceTicks;
        }
        BlockPos riser = grimArcChainRiser;
        if (riser == null || MC.level.isOutsideBuildHeight(riser)) return;
        if (grimArcChainRiserSet < 0 && solidAt(riser)) grimArcChainRiserSet = grimLiveTraceTicks;
        String clear = grimCellClearReason(MC.player.getBoundingBox(),
            MC.player.getDeltaMovement(), riser, grimFallingCatchPlan(riser));
        if ("ok".equals(clear)) {
            if (grimArcChainRiserFirst < 0) grimArcChainRiserFirst = grimLiveTraceTicks;
            grimArcChainRiserLast = grimLiveTraceTicks;

            grimArcChainRiserFail = grimTraceRiserFail;
        } else {
            grimArcChainRiserWhy = clear;
        }
    }

    private void grimResetArcChain(BlockPos support) {
        grimArcChainSupport = support == null ? null : support.immutable();
        grimArcChainRiser = support == null ? null : support.above().immutable();
        grimArcChainSupportOk = -1;
        grimArcChainSupportSet = -1;
        grimArcChainRiserFirst = -1;
        grimArcChainRiserLast = -1;
        grimArcChainRiserSet = -1;
        grimArcChainRiserWhy = "--";
        grimArcChainRiserFail = "--";
    }

    private static String traceTick(int tick) {
        return tick < 0 ? "--" : String.format(java.util.Locale.ROOT, "t%03d", tick);
    }

    private String traceLaunchReservation() {
        String support = grimLaunchReservedSupport == null
            ? "--" : grimLaunchReservedSupport.toShortString();
        String connector = grimLaunchReservedConnector == null
            ? "--" : grimLaunchReservedConnector.toShortString();
        String deficit = "?";
        if (grimLaunchReservedSupport != null && MC.level != null
            && !MC.level.isOutsideBuildHeight(grimLaunchReservedSupport)) {
            int count = grimCellDeficit(grimLaunchReservedSupport);
            deficit = count == 0 ? "ok" : count == 1 ? "n1" : "n0";
        }
        return "reserve=" + grimLaunchReservationStage
            + "@" + support + ":" + deficit + "/" + connector;
    }

    private String traceFootCell() {
        if (MC.level == null) return "?";
        BlockPos support = BlockPos.containing(MC.player.position()).below();
        if (MC.level.isOutsideBuildHeight(support)) return "oob";
        return isSolidSupport(MC.level.getBlockState(support), support) ? "solid" : "AIR";
    }

    private static String fmtRot(AutismRotationUtil.Rotation rotation) {
        return rotation == null ? "null"
            : String.format(java.util.Locale.ROOT, "%.1f,%.1f", rotation.yaw(), rotation.pitch());
    }

    private static String fmtTarget(PlacementTarget target) {
        if (target == null) return "none";
        Vec3 p = target.hit().getLocation();
        return target.face() + "@" + target.supportBlock().toShortString()
            + "->" + target.placedBlock().toShortString()
            + String.format(java.util.Locale.ROOT, " pt=%.2f,%.2f,%.2f", p.x, p.y, p.z);
    }

    private void advanceGrimNoTarget() {
        advanceGrimIdleStream(true);
    }

    private AutismRotationUtil.Rotation advanceGrimRotation(
        AutismRotationUtil.Rotation goal
    ) {
        return advanceGrimRotation(goal, false);
    }

    private AutismRotationUtil.Rotation advanceGrimRotation(
        AutismRotationUtil.Rotation goal, boolean holdPitch
    ) {
        int tick = AutismSharedState.get().getClientTickCounter();
        if (grimRotationStepTick == tick && grimSilentRotation != null) return grimSilentRotation;
        if (MC.player == null) return grimSilentRotation;
        grimWindingDown = false;

        grimWindDownElapsed = 0;
        if (grimAimRaw == null) {

            grimAimRaw = AutismRotationUtil.playerRotation(MC.player);
            grimAimSmoother.reset(rotationRandom.nextLong());
            grimAimPrevGoal = null;
            grimAimDirectionChange = 0.0F;
        }
        double gcd = AutismRotationUtil.sensitivityGcd();

        float capScale = grimRemoveLimits() ? GRIM_REMOVE_LIMITS_ROTATION_SCALE : 1.0F;
        grimAimRaw = stepGrimAimRotation(grimAimSmoother, grimAimRaw,
            AutismRotationUtil.angleDifference(goal.yaw(), grimAimRaw.yaw()),
            holdPitch ? 0.0F : Mth.clamp(goal.pitch(), -90.0F, 90.0F) - grimAimRaw.pitch(),
            grimAimDirectionSample(goal), gcd,
            GRIM_MAX_YAW_STEP * capScale, GRIM_MAX_PITCH_STEP * capScale);

        float previousPitch = grimSilentRotation == null ? Float.NaN : grimSilentRotation.pitch();
        grimSilentRotation = grimShapeOutgoing(
            grimAimRaw, grimSilentRotation, gcd, grimNextDitherCounts(), grimLastPlaceYaw);
        grimEmittedPitchStep = Float.isNaN(previousPitch)
            ? 0.0F : Math.abs(grimSilentRotation.pitch() - previousPitch);
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        grimRotationStepTick = tick;
        return grimSilentRotation;
    }

    private float grimEmittedPitchStep;

    private float grimLastPlaceYaw = Float.NaN;

    private AutismRotationUtil.Rotation grimAimRaw;

    static AutismRotationUtil.Rotation stepGrimAimRotation(
        QuantizedRotationSmoother smoother, AutismRotationUtil.Rotation current,
        AutismRotationUtil.Rotation goal, float directionChange, double gcd
    ) {
        return stepGrimAimRotation(smoother, current,
            AutismRotationUtil.angleDifference(goal.yaw(), current.yaw()),
            Mth.clamp(goal.pitch(), -90.0F, 90.0F) - current.pitch(),
            directionChange, gcd);
    }

    static AutismRotationUtil.Rotation stepGrimAimRotation(
        QuantizedRotationSmoother smoother, AutismRotationUtil.Rotation current,
        float yawError, float pitchError, float directionChange, double gcd
    ) {
        return stepGrimAimRotation(smoother, current, yawError, pitchError, directionChange, gcd,
            GRIM_MAX_YAW_STEP, GRIM_MAX_PITCH_STEP);
    }

    static AutismRotationUtil.Rotation stepGrimAimRotation(
        QuantizedRotationSmoother smoother, AutismRotationUtil.Rotation current,
        float yawError, float pitchError, float directionChange, double gcd,
        float maxYawStep, float maxPitchStep
    ) {
        QuantizedRotationSmoother.Step step = smoother.stepCapped(
            yawError, pitchError, gcd, maxYawStep, maxPitchStep,
            GRIM_AIM_ACCEL_BUDGET, directionChange, GRIM_AIM_MIDPOINT, true, true);

        AutismRotationUtil.Rotation delta = new QuantizedRotationSmoother.Step(
            grimAimDeadband(grimCapCounts(step.yawCounts(), maxYawStep, gcd), yawError, gcd),
            grimAimDeadband(grimCapCounts(step.pitchCounts(), maxPitchStep, gcd), pitchError, gcd)
        ).asDelta(gcd);
        return new AutismRotationUtil.Rotation(
            Mth.wrapDegrees(current.yaw() + delta.yaw()),
            Mth.clamp(current.pitch() + delta.pitch(), -90.0F, 90.0F));
    }

    static long grimAimDeadband(long counts, float error, double gcd) {
        if (!Double.isFinite(gcd) || gcd <= 1.0E-9D) return counts;
        return Math.abs(error) < gcd * 0.5D ? 0L : counts;
    }

    static long grimCapCounts(long counts, float maxDegrees, double gcd) {
        if (!Double.isFinite(gcd) || gcd <= 1.0E-9D) return counts;
        long limit = Math.max(1L, (long) Math.floor(maxDegrees / gcd));
        return Math.max(-limit, Math.min(limit, counts));
    }

    static final float GRIM_AIM_MIDPOINT = 0.35F;

    private float grimAimDirectionSample(AutismRotationUtil.Rotation goal) {
        float change = grimAimPrevGoal == null ? 0.0F
            : Math.min(1.0F, AutismRotationUtil.rotationAngleTo(grimAimPrevGoal, goal) / 45.0F);
        grimAimPrevGoal = goal;
        grimAimDirectionChange = grimAimDirectionChange * 0.65F + change * 0.35F;
        return grimAimDirectionChange;
    }

    static float grimDeintegrifyAngle(float angle, double gcd) {
        if (angle % 1.0F != 0.0F || angle == 0.0F || angle == 90.0F || angle == -90.0F) {
            return angle;
        }
        float step = (float) (gcd > 1.0E-6D ? gcd : 0.0225D);
        float nudged = angle + step;

        return nudged == 90.0F || nudged == -90.0F ? angle - step : nudged;
    }

    static float grimFortyFiveOffset(float yaw) {
        return (float) (yaw - Math.round(yaw / 45.0D) * 45.0D);
    }

    static boolean grimYawLegal(float yaw) {
        return Math.abs(grimFortyFiveOffset(yaw)) >= GRIM_ANGLE_SNAP_MARGIN && yaw % 1.0F != 0.0F;
    }

    static float grimLegalYaw(
        float candidate, double gcd, int preferSign, float forbidden, float lastPlaceYaw
    ) {
        float step = (float) (gcd > 1.0E-6D ? gcd : 0.0225D);
        int direction = preferSign != 0 ? preferSign
            : (grimFortyFiveOffset(candidate) >= 0.0F ? 1 : -1);

        if (grimYawLegal(candidate) && candidate != forbidden
            && candidate != lastPlaceYaw) return candidate;
        for (int ring = 1; ring <= GRIM_YAW_FIX_MAX_COUNTS; ring++) {
            float near = candidate + direction * ring * step;
            if (grimYawLegal(near) && near != forbidden && near != lastPlaceYaw) return near;
            float far = candidate - direction * ring * step;
            if (grimYawLegal(far) && far != forbidden && far != lastPlaceYaw) return far;
        }

        return candidate + step;
    }

    static AutismRotationUtil.Rotation grimShapeOutgoing(
        AutismRotationUtil.Rotation raw, AutismRotationUtil.Rotation previousEmitted,
        double gcd, int ditherCounts, float lastPlaceYaw
    ) {
        if (raw == null) return null;
        float previousYaw = previousEmitted == null ? Float.NaN : previousEmitted.yaw();

        float candidate = Mth.wrapDegrees((float) (raw.yaw() + ditherCounts * gcd));
        int prefer = previousEmitted == null ? 0
            : (int) Math.signum(AutismRotationUtil.angleDifference(raw.yaw(), previousYaw));
        return new AutismRotationUtil.Rotation(
            grimLegalYaw(candidate, gcd, prefer, previousYaw, lastPlaceYaw),
            grimDeintegrifyAngle(Mth.clamp(raw.pitch(), -90.0F, 90.0F), gcd));
    }

    private int grimDitherCounts;

    private int grimNextDitherCounts() {
        int next = grimDitherCounts;
        while (next == grimDitherCounts) next = rotationRandom.nextInt(3) - 1;
        grimDitherCounts = next;
        return next;
    }

    record GrimWireClickRotation(
        AutismRotationUtil.Rotation previous,
        AutismRotationUtil.Rotation current,
        int tick
    ) {}

    private GrimWireClickRotation grimWireClickRotation() {
        return grimWireClickRotation(AutismServerRotationView.snapshot());
    }

    static GrimWireClickRotation grimWireClickRotation(
        AutismServerRotationView.WireSnapshot snapshot
    ) {
        if (snapshot == null || !snapshot.initialized()
            || !Float.isFinite(snapshot.currentYaw())
            || !Float.isFinite(snapshot.currentPitch())) return null;
        AutismRotationUtil.Rotation current = new AutismRotationUtil.Rotation(
            snapshot.currentYaw(), snapshot.currentPitch());
        AutismRotationUtil.Rotation previous = Float.isFinite(snapshot.previousYaw())
            && Float.isFinite(snapshot.previousPitch())
            ? new AutismRotationUtil.Rotation(snapshot.previousYaw(), snapshot.previousPitch())
            : current;
        return new GrimWireClickRotation(previous, current, snapshot.tick());
    }

    static float grimWireClickPitchStep(AutismServerRotationView.WireSnapshot snapshot) {
        GrimWireClickRotation wire = grimWireClickRotation(snapshot);
        return wire == null ? Float.NaN
            : Math.abs(wire.current().pitch() - wire.previous().pitch());
    }

    private String tracePlacementProtocol() {
        drainGrimFinalMoveWrite();
        String wire = !grimFinalMoveSeen ? "--"
            : (grimFinalWireGround ? "T" : "F")
                + (grimFinalWireHorizontalCollision ? "H" : "-")
                + (grimFinalWireHasPosition ? "P" : "S");
        return String.format(java.util.Locale.ROOT,
            "use=%s/g%d/o%d:%d/s%d/%s%s ack=%d/%d q=%d+%d wire=%s",
            grimAttemptState.name().toLowerCase(java.util.Locale.ROOT),
            grimAttemptGeneration, grimAttemptSubmittedCount, grimAttemptWriteCount,
            grimAttemptSequence, grimAttemptResult,
            grimAttemptResultConsumed ? "C" : "-",
            grimHighestProcessedAck, grimHighestObservedAck,
            grimPredictedPlacements.size(), grimUntrustedPredictions.size(), wire)
            + (grimStandingOnUnackedPlacement() ? " pend=T" : "");
    }

    private boolean grimStandingOnUnackedPlacement() {
        if (MC.player == null || grimPredictedPlacements.isEmpty()) return false;
        AABB box = MC.player.getBoundingBox();
        int below = Mth.floor(box.minY - 1.0E-3D);
        for (GrimPredictedPlacement placement : grimPredictedPlacements) {
            BlockPos cell = placement.cell();
            if (cell.getY() != below) continue;
            if (cell.getX() + 1 > box.minX && cell.getX() < box.maxX
                && cell.getZ() + 1 > box.minZ && cell.getZ() < box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private boolean grimRealClickLands(
        PlacementTarget target, AutismRotationUtil.Rotation clickRotation
    ) {
        if (MC.player == null || MC.level == null || clickRotation == null) return false;
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        BlockHitResult ray = grimClickRay(
            MC.player.getEyePosition(), clickRotation, reach, MC.level, MC.player);
        grimLastArmRay = ray;
        return grimClickFeasible(ray, target, grimHitBuildsPlannedCell(ray, target));
    }

    private BlockHitResult grimLastArmRay;

    private String traceClickMiss(PlacementTarget target) {
        if (!traceArmed()) return "";
        AutismRotationUtil.Rotation rotation = grimCommittedClickRotation;
        if (rotation == null) {
            GrimWireClickRotation wire = grimWireClickRotation();
            rotation = wire == null ? null : wire.current();
        }
        if (MC.player == null || MC.level == null || rotation == null) {
            return " miss=no-state";
        }
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        BlockHitResult ray = grimClickRay(
            MC.player.getEyePosition(), rotation, reach, MC.level, MC.player);
        if (ray == null) return " miss=no-hit";
        return " miss=hit:" + ray.getBlockPos().toShortString() + "/" + ray.getDirection().getName().charAt(0)
            + String.format(java.util.Locale.ROOT, "@%.2f", ray.getLocation().y)
            + " want:" + target.supportBlock().toShortString() + "/" + target.face().getName().charAt(0)
            + String.format(java.util.Locale.ROOT, ">=%.2f", target.minPlacementY());
    }

    private void advanceGrimIdleStream(boolean allowPreAim) {
        if (MC == null || MC.player == null) return;
        if (allowPreAim && grimTowerActive()) {

            advanceGrimRotation(grimTowerPreAimGoal());
            return;
        }
        if (allowPreAim && (grimPreAimApplies() || grimAirbornePreAimApplies())) {

            advanceGrimRotation(grimRestPoseGoal());
            return;
        }
        if (allowPreAim && grimBridgeHoldApplies()) {

            advanceGrimRotation(grimRestPoseGoal());
            return;
        }
        if (grimSilentRotation != null) grimWindingDown = true;
        advanceGrimWindDown();
    }

    private boolean grimPreAimApplies() {
        LocalPlayer player = MC.player;
        if (!player.onGround()) return false;
        Vec3 motion = player.getDeltaMovement();
        if (!isValidBlock(planningStack())) return false;
        Vec3 direction;
        if (currentMovementLine != null
            && currentMovementLine.direction().horizontalDistanceSqr() > 1.0E-8D) {
            direction = currentMovementLine.direction();
        } else if (motion.horizontalDistance() > PREAIM_MIN_SPEED) {
            direction = new Vec3(motion.x, 0.0D, motion.z).normalize();
        } else {
            return false;
        }
        Vec3 position = player.position();
        for (double distance : PREAIM_EDGE_DISTANCES) {
            if (MC.level.getBlockState(targetedBase(position.add(direction.scale(distance)))).isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean grimAirbornePreAimApplies() {
        boolean hasCourse = currentMovementLine != null
            && currentMovementLine.direction().horizontalDistanceSqr() > 1.0E-8D;
        return grimAirbornePreAimEligible(
            MC.player.onGround(), hasCourse, isValidBlock(planningStack()));
    }

    static boolean grimAirbornePreAimEligible(
        boolean onGround, boolean hasCourse, boolean hasPlaceableBlock
    ) {
        return !onGround && hasCourse && hasPlaceableBlock;
    }

    private boolean grimBridgeHoldApplies() {
        if (!isValidBlock(planningStack())) return false;
        return grimBridgeHoldEligible(
            physicallyDown(MC.options.keyUp) || physicallyDown(MC.options.keyDown)
                || physicallyDown(MC.options.keyLeft) || physicallyDown(MC.options.keyRight),
            currentMovementLine == null ? null : currentMovementLine.direction(),
            findFallOffPosition(currentMovementLine),
            AutismSharedState.get().getClientTickCounter(), lastGrimPlacementTick);
    }

    static boolean grimBridgeHoldEligible(
        boolean directionalKeyHeld, Vec3 courseDirection, Vec3 fallOff,
        int clientTick, int lastPlacementTick
    ) {

        int sincePlacement = clientTick - lastPlacementTick;
        if (sincePlacement >= 0 && sincePlacement <= GRIM_BRIDGE_HOLD_TICKS) return true;

        return directionalKeyHeld && courseDirection != null
            && courseDirection.horizontalDistanceSqr() > 1.0E-8D && fallOff != null;
    }

    private AutismRotationUtil.Rotation grimRestPoseGoal() {

        float pitch = sessionPitchOffset;
        if (Float.isFinite(grimBridgePitchHold)
            && grimBridgeRunning(AutismSharedState.get().getClientTickCounter())) {
            pitch = grimBridgePitchHold;
        }
        float yaw = grimSteeredPostureYaw();

        if (grimRealQueuedTick != AutismSharedState.get().getClientTickCounter()
            && grimCourseStepCandidate != COURSE_STEP_UNSET
            && grimCourseStepDwell >= 2) {
            int candidateLane = laneStep(grimCourseStepCandidate, grimLaneOctant);
            if (candidateLane != COURSE_STEP_UNSET) {
                yaw = grimPlacementPostureYaw(compassStepYaw(candidateLane));
            }
        }
        return new AutismRotationUtil.Rotation(yaw, pitch);
    }

    private float grimBridgePitchHold = Float.NaN;

    static float grimPlacementPitchCap(float solved) {
        return Math.min(solved, GRIM_PLACE_MAX_PITCH);
    }

    static float grimPlacementPitchGoal(float solved, double windowLow, double windowHigh) {
        float goal = solved;
        float bound = GRIM_PLACE_MAX_PITCH;
        if (Double.isFinite(windowLow) && Double.isFinite(windowHigh) && windowLow <= windowHigh) {
            goal = (float) Mth.clamp(goal, windowLow, windowHigh);
            if (windowLow > GRIM_PLACE_MAX_PITCH) bound = GRIM_PLACE_PITCH_PARK;
            if (windowLow > GRIM_PLACE_PITCH_PARK) bound = GRIM_PLACE_MAX_PITCH_HARD;
        }
        return Math.min(goal, bound);
    }

    static boolean grimPlacementPitchLegal(float emitted) {
        return emitted <= GRIM_PLACE_MAX_PITCH_HARD;
    }

    static float grimPlacementPostureYaw(float movementYaw) {
        return Mth.wrapDegrees(movementYaw + 180.0F);
    }

    static float grimHandbackDebt(float sentYaw, float vanillaYaw) {
        return Float.isNaN(sentYaw) ? 0.0F : sentYaw - vanillaYaw;
    }

    static int grimWindDownBudget(float yawDebt, float pitchDelta) {
        int yawTicks = (int) Math.ceil(Math.abs(yawDebt) / GRIM_WIND_DOWN_MAX_YAW_STEP);
        int pitchTicks = (int) Math.ceil(Math.abs(pitchDelta) / GRIM_WIND_DOWN_MAX_PITCH_STEP);
        return Mth.clamp(Math.max(yawTicks, pitchTicks) + GRIM_WIND_DOWN_SLACK_TICKS,
            GRIM_WIND_DOWN_MIN_TICKS, GRIM_WIND_DOWN_MAX_TICKS);
    }

    static boolean grimWindDownReleasable(float yawDebt, float pitchDelta) {
        return Math.abs(yawDebt) <= ROTATION_RESET_THRESHOLD
            && Math.abs(pitchDelta) <= ROTATION_RESET_THRESHOLD;
    }

    private void advanceGrimWindDown() {
        if (!grimWindingDown) return;
        if (MC == null || MC.player == null || grimSilentRotation == null) {
            releaseGrimStreamNow();
            return;
        }
        if (grimAimRaw == null) grimAimRaw = grimSilentRotation;
        AutismRotationUtil.Rotation camera = AutismRotationUtil.playerRotation(MC.player);
        double gcd = AutismRotationUtil.sensitivityGcd();
        float debt = grimHandbackDebt(grimSentYaw, MC.player.getYRot());
        float pitchDelta = camera.pitch() - grimAimRaw.pitch();

        float yawCap = grimWindDownElapsed == 0
            ? GRIM_WIND_DOWN_FIRST_STEP_MAX : GRIM_WIND_DOWN_MAX_YAW_STEP;
        float pitchCap = grimWindDownElapsed == 0
            ? GRIM_WIND_DOWN_FIRST_STEP_MAX : GRIM_WIND_DOWN_MAX_PITCH_STEP;
        if (AutismHumanRotation.isInitialized(tellyStream)) {

            grimSilentRotation = AutismHumanRotation.step(
                tellyStream, camera, yawCap, pitchCap, gcd, false);

            grimAimRaw = grimSilentRotation;
        } else {
            grimAimRaw = stepGrimAimRotation(grimAimSmoother, grimAimRaw, -debt, pitchDelta,
                grimAimDirectionSample(camera), gcd, yawCap, pitchCap);

            grimSilentRotation = grimShapeOutgoing(
                grimAimRaw, grimSilentRotation, gcd, grimNextDitherCounts(), grimLastPlaceYaw);
        }
        grimWindDownElapsed++;
        grimWindDownTicks = Math.max(grimWindDownTicks,
            Math.min(grimWindDownBudget(debt, pitchDelta),
                GRIM_WIND_DOWN_MAX_TICKS - grimWindDownElapsed));
        if (grimWindDownTicks > 0) grimWindDownTicks--;
        grimRotationResetTicks = Math.max(grimRotationResetTicks, 1);
        if (grimWindDownReleasable(debt, pitchDelta)) {
            releaseGrimStreamNow();
        } else if (grimWindDownElapsed >= GRIM_WIND_DOWN_MAX_TICKS) {
            traceGrim("wind-down-forced d=" + String.format(
                java.util.Locale.ROOT, "%.2f", debt), null);
            releaseGrimStreamNow();
        }
    }

    void releaseGrimStreamNow() {
        grimSilentRotation = null;
        grimAimRaw = null;
        grimRotationResetTicks = 0;
        grimRotationStepTick = Integer.MIN_VALUE;
        grimWindingDown = false;
        grimWindDownTicks = 0;
        grimWindDownElapsed = 0;
        grimDitherCounts = 0;
        grimAimSmoother.halt();
        grimAimPrevGoal = null;
        grimAimDirectionChange = 0.0F;
        AutismHumanRotation.clear(tellyStream);
    }

    private int grimWindDownElapsed;

    void rollGrimSessionOffsets() {
        sessionPitchOffset = PREAIM_PITCH_MIN + rotationRandom.nextFloat() * PREAIM_PITCH_SPAN;
    }

    private void rollTellyLookOffset() {
        float magnitude = TELLY_LOOK_OFFSET_MIN + rotationRandom.nextFloat() * TELLY_LOOK_OFFSET_SPAN;
        tellyLookYawOffset = rotationRandom.nextBoolean() ? magnitude : -magnitude;
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
    static final double TELLY_AIR_CONTROL = 0.0196D;
    private static final double TELLY_AIR_DRAG = 0.91D;

    private static final double TELLY_AIR_LANE_ENTER = 0.30D;
    private static final double TELLY_AIR_LANE_EXIT = 0.22D;
    private static final double TELLY_AIR_LANE_EMERGENCY = 0.48D;
    private static final int TELLY_AIR_STRAFE_COOLDOWN_TICKS = 2;
    private static final int TELLY_AIR_ROUTINE_PULSE_LIMIT = 2;
    private static final int TELLY_AIR_EMERGENCY_PULSE_LIMIT = 3;
    private static final double TELLY_SAFE_OVERLAP = 0.12D;

    private static final int TELLY_FORWARD_DWELL_TICKS = 0;
    private static final double[] TELLY_FACE_OFFSETS = {0.0D, -0.16D, 0.16D, -0.28D, 0.28D};

    private static final float TELLY_GROUND_MAX_STEP = 130.0F;

    static final int TELLY_RETURN_LEAD_TICKS = 2;

    static final double TELLY_LANE_TICK_AUTHORITY = 0.026D;

    static final float TELLY_LANE_BIAS_MAX = 15.0F;

    static final float TELLY_LANE_BIAS_SLEW = 3.0F;

    private static final float TELLY_MOUSE_BURST_MAX_STEP = 95.0F;

    private static final float TELLY_MOUSE_BURST_RESCUE_STEP = 179.5F;

    private static final int TELLY_AIM_SWEEP_RESERVE_TICKS = 1;
    private static final float TELLY_SETTLE_YAW_EPSILON = 1.0F;

    private static final float TELLY_LAUNCH_YAW_EPSILON = 2.0F;
    private static final double TELLY_SETTLE_SPEED_FLOOR = 0.08D;
    private static final float TELLY_SETTLE_VELOCITY_ANGLE = 25.0F;

    private static final double TELLY_SETTLE_LANE_EPSILON = 0.45D;
    private static final int TELLY_SETTLE_DWELL_TICKS = 1;
    private static final int TELLY_SETTLE_TIMEOUT_TICKS = 30;

    private void runTellyTick() {
        tellyJumpThisTick = false;
        tellySneakThisTick = false;
        tellyGroundLaunchAllowed = false;

        tellyAirStrafeThisTick = TellyStrafe.NONE;
        tellyHoldStrafe = TellyStrafe.NONE;
        maintainTellyClickPipeline();

        if (!canRun()) {
            resetTellyState();

            advanceGrimWindDown();
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

            if (player.onGround()) {
                BlockPos groundedSupport = solidBlockUnder(player);
                if (groundedSupport != null) {
                    tellyLastGroundedSupport = groundedSupport.immutable();
                    tellyLastGroundedTick = AutismSharedState.get().getClientTickCounter();
                }
            }
            if (physicalMoving && !player.onGround() && tellyEmergencyCatchApplies(player)) {

                beginTellyEmergencyCatch(player);
            } else if (!physicalMoving || !player.onGround()) {
                if (!physicalMoving) tellyCourseLatched = false;
                advanceGrimIdleStream(false);
                tickSlotReset();
                return;
            } else {
                BlockPos under = solidBlockUnder(player);
                if (under == null) {
                    advanceGrimIdleStream(false);
                    tickSlotReset();
                    return;
                }

                if (!isValidBlock(planningStack())) {
                    advanceGrimIdleStream(false);
                    tickSlotReset();
                    return;
                }
                beginTellyControl(player, under);
            }
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

        continueTellyReturnAfterPlanning(player);

        if (tellyOwnsInput && player.onGround() && tellyMotion == TellyMotion.HOLD
            && !tellyStopRequested && !tellyFinishing) {
            if (++tellyHoldWatchdogTicks >= 30) {
                tellyHoldWatchdogTicks = 0;
                tellyCourseDeviationTicks = 0;
                tellyTurnSettling = false;
                tellySettleHoldTicks = 0;
                tellySettleDwellTicks = 0;
                tellyEdgeHoldTicks = 0;
                clearTellyTurnReserve();
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

        grimWindingDown = false;
        grimWindDownElapsed = 0;
        if (!AutismHumanRotation.isInitialized(tellyStream)) {

            AutismHumanRotation.seed(tellyStream, serverRotation());
        }
        tellySmoothedRotation = AutismHumanRotation.current(tellyStream);

        float lookYaw = tellyMovementYaw(player);
        boolean hadCourse = tellyCourseLatched && Float.isFinite(tellyAnchorYaw);
        float previousAnchor = tellyAnchorYaw;
        clearTellyTurnReserve();
        if (hadCourse) {

            float lookDeviation = Math.abs(AutismRotationUtil.angleDifference(tellyAnchorYaw, lookYaw));
            if (lookDeviation > 45.0F) {
                float snapped = snapTellyYaw(lookYaw);

                if (Float.compare(snapped, snapTellyYaw(previousAnchor)) != 0) rollTellyLookOffset();
                tellyAnchorYaw = Mth.wrapDegrees(snapped);
            } else {
                tellyAnchorYaw = Mth.wrapDegrees(tellyAnchorYaw);
            }
        } else {
            rollTellyLookOffset();
            tellyAnchorYaw = Mth.wrapDegrees(snapTellyYaw(lookYaw));
        }
        tellyCourseLatched = true;
        if (hadCourse && Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(tellyAnchorYaw)) != 0) {
            armTellyTurnReserve(under, tellyDirectionForYaw(previousAnchor));
            beginTellyTurnSettle();
        }
        tellyForwardPitch = Mth.clamp(player.getXRot(), -82.0F, 82.0F);
        tellyLineOrigin = laneOrigin(under, player.position(), tellyAnchorYaw);
        tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
        tellyCourseDeviationTicks = 0;
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
        clearTellyAirCorrection();
        tellyLastBridge = under.immutable();
        tellyBridgeY = under.getY();
        tellyForwardDwellTicks = 0;
        tellyAirTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyTarget = null;
        tellyQueuedBlock = null;
        tellyPlacementQueued = false;
        tellyFaceOffsetIndex = -1;
        tellyReturnCompleted = true;
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
        Direction previousDirection = tellyDirectionForYaw(previousAnchor);
        float snapped = snapTellyYaw(tellyMovementYaw(player));

        if (Float.compare(snapped, snapTellyYaw(previousAnchor)) != 0) rollTellyLookOffset();
        tellyAnchorYaw = Mth.wrapDegrees(snapped);
        tellyCourseLatched = true;
        tellyLineOrigin = laneOrigin(laneSupport, player.position(), tellyAnchorYaw);
        tellyLaneCenter = laneCoordinate(tellyLineOrigin, tellyAnchorYaw);
        clearTellyGroundSteering();
        clearTellyAirCorrection();
        if (Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(tellyAnchorYaw)) != 0) {
            armTellyTurnReserve(laneSupport, previousDirection);
            beginTellyTurnSettle();
        }
    }

    private void armTellyTurnReserve(BlockPos support, Direction entryDirection) {
        clearTellyTurnReserve();
        if (support == null || entryDirection == null || !entryDirection.getAxis().isHorizontal()) return;
        tellyTurnReserveSupport = support.immutable();
        tellyTurnReserveCell = support.relative(entryDirection).immutable();
    }

    private void clearTellyTurnReserve() {
        tellyTurnReserveSupport = null;
        tellyTurnReserveCell = null;
        tellyTurnReserveQueued = false;
    }

    private boolean tellyTurnIntentPending() {
        return tellyCourseDeviationTicks >= 2;
    }

    private void beginTellyTurnSettle() {

        tellyTarget = null;
        tellyFaceOffsetIndex = -1;

        if (tellyTurnSettling) return;
        tellyTurnSettling = true;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = TELLY_SETTLE_DWELL_TICKS;
        clearTellyGroundSteering();
    }

    private void runGroundedTelly(LocalPlayer player) {
        BlockPos under = solidBlockUnder(player);
        if (tellyPhase == TellyPhase.RECOVERING) {
            runTellyRunupRecovery(player, under != null ? under : tellyLastBridge);
            return;
        }
        if (under != null) {

            float anchorBeforeTurn = tellyAnchorYaw;
            applyTellyCourseTurn(player, under);

            double laneError = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
            if (anchorBeforeTurn != tellyAnchorYaw && Math.abs(laneError) > 0.55D) {
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

            completeTellyReturn(TELLY_MOUSE_BURST_MAX_STEP);

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

            if (tellyEarlyLaunchAllowed(player, under, first)) startTellyLaunch(player, first);
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
            tellySmoothedRotation.yaw(), tellyCourseLookYaw(),
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

            if (overhang != null && isTellyTurnReserveDirection(
                tellyTurnReserveSupport, tellyTurnReserveCell, overhang)
                && tryTellyTurnReservePlacement(player)) {
                tellySettleHoldTicks++;
                if (tellySettleHoldTicks >= TELLY_SETTLE_TIMEOUT_TICKS) {
                    clearTellyTurnReserve();
                    releaseTellyControl();
                }
                return true;
            }
            tellyHoldStrafe = tellyHoldLaneStrafe(player);
            tellySettleHoldTicks++;
            if (tellySettleHoldTicks >= TELLY_SETTLE_TIMEOUT_TICKS) {

                releaseTellyControl();
            }
            return true;
        }
        tellyTurnSettling = false;
        clearTellyTurnReserve();
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

                if (overhang != null && isTellyTurnReserveDirection(
                    tellyTurnReserveSupport, tellyTurnReserveCell, overhang)
                    && tryTellyTurnReservePlacement(player)) return;

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
        tellyQueuedBlock = null;
        tellyLastBridge = support.immutable();
        clearTellyGroundSteering();
        clearTellyAirCorrection();
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

        tellyHoldStrafe = tellyHoldLaneStrafe(player);
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
        clearTellyTurnReserve();
        tellyCycleSerial++;

        tellyCycleRises = tellyRiseQueued || tellySpaceHeld;
        tellyRiseQueued = false;
        tellyFinishing = false;
        tellyRaisedBlockPlaced = false;
        tellyAimCommitted = false;
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
        tellyReturnCompleted = true;
        tellyPhase = TellyPhase.LAUNCH;
        tellyMotion = TellyMotion.FORWARD;
        clearTellyGroundSteering();

        clearTellyAirCorrection();
        clearTellyLaunchStrafe();
        tellyJumpThisTick = true;
        tellySneakThisTick = false;
    }

    private void clearTellyLaunchStrafe() {
        grimInputSidewaysOctant = 0;
        grimInputSidewaysHold = 0;
        grimInputSidewaysFromCorrection = false;
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
            && !isTellySupport(MC.level.getBlockState(tellyRaisedCell), tellyRaisedCell)) {

            tellyRaisedBlockPlaced = false;

            if (tellyLastBridge != null && tellyLastBridge.getY() > tellyBridgeY) {
                tellyLastBridge = new BlockPos(
                    tellyLastBridge.getX(), tellyBridgeY, tellyLastBridge.getZ());
            }
            tellyRaisedCell = null;
        }

        int landingBlockY = tellyRaisedBlockPlaced ? tellyBridgeY + 1 : tellyBridgeY;
        boolean hasConfirmedCatchBlock = tellyFlatPlacements > 0;
        boolean descending = player.getDeltaMovement().y < -0.035D;

        boolean landingSecured = hasConfirmedCatchBlock
            && isTellyLandingSupported(player, landingBlockY);
        if (!landingSecured) {
            updateTellyAirCorrection(player, landingBlockY + 1.0D);
        } else {

            tellyAirStrafeCooldown = Math.max(0, tellyAirStrafeCooldown - 1);
        }
        if (!landingSecured && tellyRaisedBlockPlaced && descending && tellyRiseOvershootCoast(player)) {
            tellyMotion = TellyMotion.HOLD;
        }

        boolean turnPending = tellyTurnIntentPending();
        boolean normalRunwayCovered = landingSecured
            && tellyLandingRunwayCovered(player, landingBlockY, 1);
        boolean turnReserveCovered = !turnPending || landingSecured
            && tellyLandingRunwayCovered(
                player, landingBlockY, tellyRunwayReserveBlocks(turnPending));
        boolean chainCovered = tellyChainCovered(
            landingSecured, normalRunwayCovered, turnPending, turnReserveCovered);
        boolean walkOffGrace = tellyWalkOffCatch && tellyWalkOffGraceTicks > 0;
        if (tellyWalkOffGraceTicks > 0) tellyWalkOffGraceTicks--;
        if (!landingSecured && !walkOffGrace && !tellyPlacementQueued && tellyClickCooldown <= 0
            && (tellyLandingOffLane(player) || tellyCatchPlaneImminent(player, descending))) {

            tryTellyUrgentChainPlacement(player, stack);
        }
        if (!landingSecured && tellyFailedClicks >= 3) {

            abortMissedTelly(player);
            return;
        }
        if (!landingSecured && !walkOffGrace && missedTellyCatchWindow(player, descending)) {

            if (!tellyPlacementQueued && tellyClickCooldown <= 0
                && !tryTellyUrgentChainPlacement(player, stack)) {
                abortMissedTelly(player);
            }
            return;
        }

        if (!isValidBlock(stack)) {
            tellyPhase = TellyPhase.RETURNING;
            return;
        }
        TellyPhase coveredPhase = nextTellyCoveragePhase(
            tellyPhase, chainCovered, tellyCycleRises && !tellyRaisedBlockPlaced);
        if (coveredPhase == TellyPhase.RETURNING && tellyPhase != TellyPhase.RETURNING) {
            tellyTarget = null;
            tellyPhase = coveredPhase;
            return;
        }
        if (landingSecured && tellyCycleRises && !tellyRaisedBlockPlaced
            && player.getDeltaMovement().y < 0.0D && player.getY() < tellyBridgeY + 2.0D) {

            tellyCycleRises = false;
            tellyTarget = null;
            if (chainCovered) {
                tellyPhase = TellyPhase.RETURNING;
                return;
            }
        }

        if (tellyPhase == TellyPhase.RETURNING) {

            return;
        }

        if (tellyTarget == null) {
            if (chainCovered) {

                if (!tellyCycleRises || tellyRaisedBlockPlaced) {
                    tellyPhase = nextTellyCoveragePhase(tellyPhase, true, false);
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
        turnPending = tellyTurnIntentPending();
        normalRunwayCovered = landingSecured
            && tellyLandingRunwayCovered(player, landingBlockY, 1);
        turnReserveCovered = !turnPending || landingSecured
            && tellyLandingRunwayCovered(
                player, landingBlockY, tellyRunwayReserveBlocks(turnPending));
        chainCovered = tellyChainCovered(
            landingSecured, normalRunwayCovered, turnPending, turnReserveCovered);
        if (chainCovered
            && (!tellyCycleRises || tellyRaisedBlockPlaced)) {
            tellyPhase = nextTellyCoveragePhase(tellyPhase, true, false);
            tellyTarget = null;
        }
    }

    private boolean tellyEmergencyCatchApplies(LocalPlayer player) {
        if (!isValidBlock(planningStack())) return false;
        if (tellyLastGroundedSupport == null) return false;
        int sinceGrounded = AutismSharedState.get().getClientTickCounter() - tellyLastGroundedTick;
        if (sinceGrounded < 0 || sinceGrounded > 6) return false;
        return !hasSupportBelow(player.getX(), player.getY(), player.getZ(), 2);
    }

    private void beginTellyEmergencyCatch(LocalPlayer player) {
        beginTellyControl(player, tellyLastGroundedSupport);
        beginTellyWalkOffCatch(player);
    }

    private void beginTellyWalkOffCatch(LocalPlayer player) {
        tellyCycleSerial++;
        tellyCycleRises = false;
        tellyRaisedBlockPlaced = false;
        tellyAimCommitted = false;
        tellyRaisedCell = null;
        tellyFlatPlacements = 0;
        tellyWalkOffCatch = true;
        tellyWalkOffGraceTicks = 3;
        tellyPlacementQueued = false;
        tellyQueuedBlock = null;
        tellyReturnCompleted = true;
        tellyFailedClicks = 0;
        tellyAirTicks = Math.max(tellyAirTicks, 3);
        tellyBridgeY = tellyLastBridge.getY();
        tellyTakeoffY = tellyBridgeY + 1.0D;
        tellyTakeoffProgress = player.position().dot(tellyForwardVector()) - 1.45D;
        tellyPhase = TellyPhase.LAUNCH;
        tellyMotion = TellyMotion.FORWARD;
        clearTellyGroundSteering();
        clearTellyAirCorrection();
    }

    public static void beforeHandleKeybinds() {
        if (MC == null || MC.player == null || MC.level == null) return;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()) return;
        if (scaffold.isGrimFamily()) {
            scaffold.refreshGrimRealClickHit();
            return;
        }
        if (!scaffold.isTellyMode() || !scaffold.tellyOwnsInput || !scaffold.canRun()) return;

        scaffold.refreshTellyRealClickHit();
        if (MC.player.onGround()) return;
        scaffold.armTellySilentPlacement();
    }

    public static boolean ownsGrimUseInput() {
        Module module = ModuleRegistry.get("scaffold");
        return module instanceof ScaffoldModule scaffold
            && scaffold.isEnabled() && scaffold.ownsRealClickPipeline()
            && scaffold.grimAttemptState == GrimPlacementAttemptState.ARMED
            && scaffold.grimRealQueuedTick == AutismSharedState.get().getClientTickCounter();
    }

    public static boolean beginGrimUseInput() {
        if (!ownsGrimUseInput() || AutismBlinkManager.holdsActionsWithoutMovement()) return false;
        Module module = ModuleRegistry.get("scaffold");
        ScaffoldModule scaffold = (ScaffoldModule) module;
        return AutismInputClicker.beginScaffoldUseClick(scaffold.grimAttemptGeneration);
    }

    public static void onPacketQueued(Packet<?> packet) {
        if (!(packet instanceof ServerboundUseItemOnPacket use)) return;
        synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();

            if (GRIM_QUEUED_USES.containsKey(new GrimPacketIdentity(packet))) return;
        }
        long edgeGeneration = AutismInputClicker.scaffoldUseGenerationInProgress();
        if (edgeGeneration <= 0L) return;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.ownsRealClickPipeline()) return;
        PlacementTarget pending = scaffold.grimRealPendingTarget;
        BlockHitResult hit = use.getHitResult();
        if (pending == null || scaffold.grimAttemptState != GrimPlacementAttemptState.ARMED
            || edgeGeneration != scaffold.grimAttemptGeneration
            || use.getHand() != scaffold.grimAttemptHand
            || !grimClickFeasible(hit, pending,
                scaffold.grimAttemptBuildsPlannedCell)) return;
        synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();
            GrimPacketIdentity lookup = new GrimPacketIdentity(packet);
            if (GRIM_QUEUED_USES.containsKey(lookup)) return;
            int ordinal = scaffold.grimAttemptSubmittedCount++;
            GrimQueuedUse queued = new GrimQueuedUse(
                scaffold.grimAttemptGeneration, ordinal, use.getHand(),
                pending.placedBlock().immutable(), pending.supportBlock().immutable(),
                pending.face(), scaffold.grimCommittedClickRotation);
            GRIM_QUEUED_USES.put(
                new GrimPacketIdentity(packet, GRIM_QUEUED_USE_GC), queued);
            if (ordinal > 0) scaffold.grimAttemptDuplicateSubmitted = true;
        }
    }

    public static void onPacketAbandoned(Packet<?> packet) {
        if (!(packet instanceof ServerboundUseItemOnPacket)) return;
        synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();
            GRIM_QUEUED_USES.remove(new GrimPacketIdentity(packet));
        }
    }

    private void refreshGrimRealClickHit() {
        if (grimRealQueuedTick != AutismSharedState.get().getClientTickCounter()
            || grimRealPendingTarget == null || grimCommittedClickRotation == null
            || grimAttemptState != GrimPlacementAttemptState.ARMED) return;
        double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
        BlockHitResult ray = grimClickRay(
            MC.player.getEyePosition(), grimCommittedClickRotation, reach, MC.level, MC.player);
        if (ray != null) MC.hitResult = ray;
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

            TellyPlacement rise = pendingTellyRiseTarget(MC.player, held);
            if (rise != null) {
                TellyPlacement live = liveTellyPlacement(MC.player, rise);
                if (live != null && !ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) {

                    commitTellyPlacement(live, hand, held, null);
                    return;
                }
            }
        }

        if (tellyTarget == null
            && (tellyPhase == TellyPhase.LAUNCH || tellyPhase == TellyPhase.AIMING)
            && !(tellyRiseStillPossible(MC.player) && tellyRiseSupportCell(MC.player) != null)) {
            tellyTarget = nextFlatTellyPlacement(MC.player, held);
        }
        if (tellyTarget == null) return;

        boolean delayFirstClick = tellyShouldDelayFirstClick(MC.player);
        tellyTraceDelay = delayFirstClick;
        if (delayFirstClick) return;
        TellyPlacement live = liveTellyPlacement(MC.player, tellyTarget);
        if (live == null) return;
        if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return;

        commitTellyPlacement(live, hand, held, null);
    }

    private boolean missedTellyCatchWindow(LocalPlayer player, boolean descending) {
        if (!descending) return false;

        double targetTop = tellyBridgeY + 1.0D;
        return player.getY() < targetTop + 0.05D
            || player.getY() < tellyTakeoffY - 1.15D
            || tellyAirTicks > 14;
    }

    private boolean tellyCatchPlaneImminent(LocalPlayer player, boolean descending) {
        if (!descending || player == null) return false;
        double catchTop = tellyRaisedBlockPlaced ? tellyBridgeY + 2.0D : tellyBridgeY + 1.0D;
        double fall = Math.max(0.0D, -player.getDeltaMovement().y);
        return player.getY() <= catchTop + Math.max(0.35D, fall * 2.0D);
    }

    private boolean tellyLandingOffLane(LocalPlayer player) {
        double catchTop = tellyRaisedBlockPlaced ? tellyBridgeY + 2.0D : tellyBridgeY + 1.0D;
        double feetY = Math.min(catchTop, player.getY());
        Vec3 landing = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        double lane = laneCoordinate(new Vec3(landing.x, player.getY(), landing.z), tellyAnchorYaw);
        return Math.abs(lane - tellyLaneCenter) > 0.4D;
    }

    private boolean tryTellyUrgentChainPlacement(LocalPlayer player, ItemStack stack) {
        if (tellyPlacementQueued) return true;
        if (stack == null) return false;

        if (tellyTarget != null && tellyTarget.raised()) return true;
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
        TellyPlacement urgent = tellyTarget;
        if (urgent == null || !isTellyForwardChainPlacement(
            urgent.target().supportBlock(), urgent.target().placedBlock(),
            urgent.target().face(), tellyForwardDirection())) {
            urgent = nextFlatTellyPlacement(player, held);
        }
        if (urgent == null || !isTellyForwardChainPlacement(
            urgent.target().supportBlock(), urgent.target().placedBlock(),
            urgent.target().face(), tellyForwardDirection())) return false;
        tellyTarget = urgent;
        TellyPlacement live = liveTellyPlacement(player, urgent);
        if (live == null) return true;
        if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return false;

        commitTellyPlacement(live, hand, held, null, true);
        return true;
    }

    static boolean isTellyForwardChainPlacement(
        BlockPos support, BlockPos placed, Direction face, Direction forward
    ) {
        return support != null && placed != null && face != null && forward != null
            && face == forward
            && placed.equals(support.relative(forward))
            && placed.getY() == support.getY();
    }

    private void abortMissedTelly(LocalPlayer player) {

        tellyFinishing = true;
        tellyTarget = null;
        tellyPlacementQueued = false;
        tellyQueuedBlock = null;
        tellyPhase = TellyPhase.RETURNING;
        tellyMotion = TellyMotion.FORWARD;
    }

    private void maintainTellyClickPipeline() {
        if (MC == null || MC.player == null || MC.level == null) return;

        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick == tellyPipelineTick) return;
        tellyPipelineTick = tick;

        settleTellyRealClick();
        if (tellyClickCooldown > 0) tellyClickCooldown--;
        confirmTellyPlacement();
    }

    private void settleTellyRealClick() {
        if (!tellyUsesRealClicks()) return;
        drainGrimFinalUseWrite();
        resolveGrimUseOutcome();
        if (grimAttemptState == GrimPlacementAttemptState.IDLE) return;
        int age = Math.max(0,
            AutismSharedState.get().getClientTickCounter() - grimRealQueuedTick);
        if (grimAttemptState == GrimPlacementAttemptState.PREDICTED
            || grimAttemptState == GrimPlacementAttemptState.FAILED
            || age >= GRIM_PLACEMENT_RECONCILE_TICKS) {
            clearGrimPlacementAttempt();
        }
    }

    private int tellyPipelineTick = Integer.MIN_VALUE;

    private void confirmTellyPlacement() {
        if (!tellyPlacementQueued || tellyQueuedBlock == null) return;
        tellyPlacementQueued = false;
        BlockPos placed = tellyQueuedBlock;
        tellyQueuedBlock = null;
        boolean turnReserve = tellyTurnReserveQueued
            && tellyTurnReserveCell != null && tellyTurnReserveCell.equals(placed);
        tellyTurnReserveQueued = false;
        if (!isTellySupport(MC.level.getBlockState(placed), placed)) {

            if (!turnReserve) tellyFailedClicks++;
            return;
        }
        if (turnReserve) {
            tellyFailedClicks = 0;
            tellyTarget = null;
            clearTellyTurnReserve();
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
        clearTellyAirCorrection();
        tellyAirTicks = 0;
        tellyFlatPlacements = 0;
        tellyFailedClicks = 0;
        tellyForwardDwellTicks = 0;
        tellyCycleRises = false;
        tellyRaisedBlockPlaced = false;
        tellyAimCommitted = false;
        tellyRaisedCell = null;
        tellyPlacementQueued = false;
        tellyWalkOffCatch = false;
        tellyWalkOffGraceTicks = 0;
        tellyQueuedBlock = null;
        tellyTarget = null;
        tellyReturnCompleted = true;
        tellyLastBridge = under.immutable();
        tellyBridgeY = under.getY();
    }

    private boolean atTellyEdge(LocalPlayer player, BlockPos under) {
        Direction direction = tellyForwardDirection();
        BlockPos ahead = under.relative(direction);
        if (isTellySupport(MC.level.getBlockState(ahead), ahead)) return false;

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
                if (!isTellySupport(MC.level.getBlockState(pos), pos)) continue;
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
            if (isTellySupport(state, next)) {
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

        AABB box = grimSupportBox(support);
        Vec3 aim = grimFaceCentre(box, Direction.UP).add(lateral.scale(cross));
        PlacementTarget target = new PlacementTarget(
            support.immutable(), raised.immutable(), Direction.UP,
            new BlockHitResult(aim, Direction.UP, support, false),
            AutismRotationUtil.lookingAt(aim, player.getEyePosition()),
            box.maxY - 0.1D * box.getYsize());
        return new TellyPlacement(target, true);
    }

    private Vec3 tellySideAim(BlockPos support, Direction direction) {
        double faceY = Mth.clamp(0.78D - tellyFlatPlacements * 0.11D, 0.28D, 0.78D);
        AABB box = grimSupportBox(support);
        Vec3 centre = grimFaceCentre(box, direction);
        return new Vec3(centre.x, box.minY + faceY * box.getYsize(), centre.z);
    }

    private boolean tryTellyTurnReservePlacement(LocalPlayer player) {
        BlockPos support = tellyTurnReserveSupport;
        BlockPos cell = tellyTurnReserveCell;
        Direction direction = tellyTurnReserveDirection(support, cell);
        if (support == null || cell == null || direction == null) return false;
        if (isTellySupport(MC.level.getBlockState(cell), cell)) {
            if (tellyTarget != null && cell.equals(tellyTarget.target().placedBlock())) tellyTarget = null;
            clearTellyTurnReserve();
            return false;
        }
        if (!isTellySupport(MC.level.getBlockState(support), support)) {
            clearTellyTurnReserve();
            return false;
        }
        BlockState targetState = MC.level.getBlockState(cell);
        if (!targetState.isAir() && !targetState.canBeReplaced()) {
            clearTellyTurnReserve();
            return false;
        }
        if (tellyPlacementQueued || tellyClickCooldown > 0) return true;

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

        Vec3 aim = tellySideAim(support, direction);
        PlacementTarget target = new PlacementTarget(
            support, cell, direction,
            new BlockHitResult(aim, direction, support, false),
            AutismRotationUtil.lookingAt(aim, player.getEyePosition()), support.getY());
        TellyPlacement placement = new TellyPlacement(target, false);
        tellyTarget = placement;
        TellyPlacement live = liveTellyPlacement(player, placement);
        if (live == null) return true;
        if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, id())) return false;
        if (commitTellyPlacement(live, hand, held, cell)) tellyTurnReserveQueued = true;
        return true;
    }

    static Direction tellyTurnReserveDirection(BlockPos support, BlockPos cell) {
        if (support == null || cell == null || support.getY() != cell.getY()) return null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (support.relative(direction).equals(cell)) return direction;
        }
        return null;
    }

    static boolean isTellyTurnReserveDirection(
        BlockPos support, BlockPos cell, Direction overhang
    ) {
        return overhang != null && overhang == tellyTurnReserveDirection(support, cell);
    }

    static boolean cellBlocksPlayer(BlockPos cell, AABB playerBox) {
        return cell != null && playerBox != null && new AABB(cell).intersects(playerBox);
    }

    private boolean commitTellyPlacement(
        TellyPlacement live, InteractionHand hand, ItemStack held, BlockPos queuedCell
    ) {
        return commitTellyPlacement(live, hand, held, queuedCell, false);
    }

    private boolean commitTellyPlacement(
        TellyPlacement live, InteractionHand hand, ItemStack held, BlockPos queuedCell,
        boolean lastChance
    ) {

        if (cellBlocksPlayer(live.target().placedBlock(), MC.player.getBoundingBox())) return false;
        PlacementTarget aimed = tellyStreamAimedTarget(live.target());
        if (aimed == null) {

            aimed = tellyMouseBurstTarget(live.target(),
                lastChance ? TELLY_MOUSE_BURST_RESCUE_STEP : TELLY_MOUSE_BURST_MAX_STEP);
        }
        if (aimed == null) return false;

        adoptTellyPlacementRotation(aimed.rotation());

        if (!armTellyRealClick(aimed, hand)) return false;
        tellyTarget = live;
        tellyPlacementQueued = true;
        tellyQueuedBlock = queuedCell != null ? queuedCell : aimed.placedBlock().immutable();
        tellyClickCooldown = 1;
        traceTelly("PLACE");
        return true;
    }

    private boolean armTellyRealClick(PlacementTarget aimed, InteractionHand hand) {
        if (MC.player == null || aimed == null || hand == null) return false;
        int tick = AutismSharedState.get().getClientTickCounter();

        if (tick == lastGrimPlacementTick || grimAttemptBlocksRearm()) return false;
        if (AutismBlinkManager.holdsActionsWithoutMovement()) return false;
        grimRealPendingTarget = aimed;
        grimRealPendingLine = currentMovementLine;
        grimRealPendingFallOff = null;
        grimRealQueuedTick = tick;
        grimAttemptState = GrimPlacementAttemptState.ARMED;
        grimNextAttemptGeneration = grimNextAttemptGeneration(grimNextAttemptGeneration);
        grimAttemptGeneration = grimNextAttemptGeneration;
        grimAttemptHand = hand;
        grimAttemptBuildsPlannedCell =
            grimHitBuildsPlannedCell(aimed.hit(), aimed);
        grimAttemptSubmittedCount = 0;
        grimAttemptDuplicateSubmitted = false;
        grimAttemptWriteCount = 0;
        grimCommittedClickRotation = aimed.rotation();
        grimCommittedPreviousRotation = grimSilentRotation;
        grimAttemptSequence = -1;
        grimAttemptResultSeen = false;
        grimAttemptResultConsumed = false;
        grimAttemptPaceBooked = false;
        grimAttemptResult = "queued";
        lastGrimPlacementTick = tick;
        grimPaceQueuedNanos = Long.MIN_VALUE;
        MC.hitResult = aimed.hit();
        AutismInputClicker.queueScaffoldUseClick(grimAttemptGeneration);

        ((autismclient.mixin.accessor.AutismMinecraftAccessor) (Object) MC)
            .autism$setRightClickDelay(0);
        return true;
    }

    private void cancelTellyRealClick() {
        if (grimAttemptState == GrimPlacementAttemptState.IDLE
            && grimRealPendingTarget == null) return;
        AutismInputClicker.cancelScaffoldUseClick();
        clearGrimPlacementAttempt();
    }

    private void refreshTellyRealClickHit() {
        if (grimRealQueuedTick != AutismSharedState.get().getClientTickCounter()
            || grimRealPendingTarget == null
            || grimAttemptState != GrimPlacementAttemptState.ARMED) return;
        MC.hitResult = grimRealPendingTarget.hit();
    }

    private TellyPlacement liveTellyPlacement(LocalPlayer player, TellyPlacement placement) {
        PlacementTarget target = placement.target();
        Vec3 eye = player.getEyePosition(1.0F);

        if (target.face().getAxis().isVertical()) {

            if (!tellyRiseCellClear(player.getBoundingBox(), player.getDeltaMovement(), target.placedBlock())) {
                return null;
            }

            AutismRotationUtil.Rotation rotation =
                AutismRotationUtil.lookingAt(target.hit().getLocation(), eye);
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
        TellyFaceSample[] samples = new TellyFaceSample[TELLY_FACE_OFFSETS.length];
        double[] costs = new double[TELLY_FACE_OFFSETS.length];
        java.util.Arrays.fill(costs, Double.POSITIVE_INFINITY);
        Vec3 blockOffset = new Vec3(
            target.supportBlock().getX(), target.supportBlock().getY(), target.supportBlock().getZ());

        for (AABB localBox : shape.toAabbs()) {
            FaceRect face = FaceRect.fromBox(localBox, target.face()).trim(0.12D).offset(blockOffset);
            if (face.area() <= GEOMETRY_EPSILON) continue;
            if (eye.subtract(face.center()).dot(normal) <= TELLY_FACE_VISIBILITY_EPSILON) continue;

            for (int offsetIndex = 0; offsetIndex < TELLY_FACE_OFFSETS.length; offsetIndex++) {
                double offset = TELLY_FACE_OFFSETS[offsetIndex];
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
                if (cost < costs[offsetIndex]) {
                    costs[offsetIndex] = cost;
                    samples[offsetIndex] = new TellyFaceSample(
                        face, point, rotation, ray, offsetIndex);
                }
            }
        }
        int selectedOffset = selectTellyFaceOffset(tellyFaceOffsetIndex, costs);
        TellyFaceSample best = selectedOffset < 0 ? null : samples[selectedOffset];
        if (best == null) return null;
        tellyFaceOffsetIndex = selectedOffset;

        PlacementTarget live = new PlacementTarget(
            target.supportBlock(), target.placedBlock(), target.face(), best.verifiedHit(),
            best.rotation(), best.worldFace().from().y);
        return new TellyPlacement(live, placement.raised());
    }

    static int selectTellyFaceOffset(int lockedIndex, double[] costs) {
        if (costs == null || costs.length == 0) return -1;
        if (lockedIndex >= 0 && lockedIndex < costs.length
            && Double.isFinite(costs[lockedIndex])) return lockedIndex;
        int best = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int index = 0; index < costs.length; index++) {
            double cost = costs[index];
            if (Double.isFinite(cost) && cost < bestCost) {
                best = index;
                bestCost = cost;
            }
        }
        return best;
    }

    private PlacementTarget tellyStreamAimedTarget(PlacementTarget target) {
        if (tellySmoothedRotation == null) return null;
        BlockHitResult ray = raytrace(tellySmoothedRotation, MC.player.blockInteractionRange());
        if (ray == null || !ray.getBlockPos().equals(target.supportBlock())
            || ray.getDirection() != target.face()) return null;
        return new PlacementTarget(target.supportBlock(), target.placedBlock(), target.face(),
            ray, tellySmoothedRotation, target.minPlacementY());
    }

    private PlacementTarget tellyMouseBurstTarget(PlacementTarget live) {
        return tellyMouseBurstTarget(live, TELLY_MOUSE_BURST_MAX_STEP);
    }

    private PlacementTarget tellyMouseBurstTarget(PlacementTarget live, float cap) {
        AutismRotationUtil.Rotation from = serverRotation();
        AutismRotationUtil.Rotation stepped = tellyMouseBurstRotation(
            from, live.rotation(), cap, cap, AutismRotationUtil.sensitivityGcd());
        BlockHitResult ray = raytrace(stepped, MC.player.blockInteractionRange());
        if (ray != null && ray.getBlockPos().equals(live.supportBlock())
            && ray.getDirection() == live.face()) {
            return new PlacementTarget(live.supportBlock(), live.placedBlock(), live.face(),
                ray, stepped, live.minPlacementY());
        }

        double gcd = normalizedTellyMouseGcd(AutismRotationUtil.sensitivityGcd());
        for (int radius = 1; radius <= 3; radius++) {
            for (int yawCounts = -radius; yawCounts <= radius; yawCounts++) {
                int pitchCounts = radius - Math.abs(yawCounts);
                PlacementTarget adjusted = verifiedTellyMouseTarget(
                    live, from, stepped, yawCounts, pitchCounts, gcd, cap);
                if (adjusted != null) return adjusted;
                if (pitchCounts != 0) {
                    adjusted = verifiedTellyMouseTarget(
                        live, from, stepped, yawCounts, -pitchCounts, gcd, cap);
                    if (adjusted != null) return adjusted;
                }
            }
        }
        return null;
    }

    private PlacementTarget verifiedTellyMouseTarget(
        PlacementTarget live, AutismRotationUtil.Rotation from,
        AutismRotationUtil.Rotation base, int yawCounts, int pitchCounts, double gcd, float cap
    ) {
        AutismRotationUtil.Rotation candidate = new AutismRotationUtil.Rotation(
            Mth.wrapDegrees(base.yaw() + (float) (yawCounts * gcd)),
            Mth.clamp(base.pitch() + (float) (pitchCounts * gcd), -89.9F, 89.9F));
        float yawDelta = Math.abs(AutismRotationUtil.angleDifference(candidate.yaw(), from.yaw()));
        float pitchDelta = Math.abs(candidate.pitch() - from.pitch());
        double countSlack = gcd * 0.501D;
        if (yawDelta > cap + countSlack || pitchDelta > cap + countSlack) return null;
        BlockHitResult ray = raytrace(candidate, MC.player.blockInteractionRange());
        if (ray == null || !ray.getBlockPos().equals(live.supportBlock())
            || ray.getDirection() != live.face()) return null;
        return new PlacementTarget(live.supportBlock(), live.placedBlock(), live.face(),
            ray, candidate, live.minPlacementY());
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

        commitTellyPlacement(live, hand, held, null);
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
            if (!isTellySupport(MC.level.getBlockState(candidate), candidate)) continue;
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

    private boolean tellyLandingRunwayCovered(LocalPlayer player, int blockY, int distance) {
        double feetY = blockY + 1.0D;
        Vec3 projected = projectTellyLandingWithInput(
            player.position(), player.getDeltaMovement(), feetY, tellyEffectiveForward(player));
        BlockPos runway = tellyLaneCell(BlockPos.containing(projected.x, blockY, projected.z))
            .relative(tellyForwardDirection(), Math.max(1, distance));
        return isTellySupport(MC.level.getBlockState(runway), runway);
    }

    static boolean tellyChainCovered(
        boolean landingSecured, boolean runwayCovered,
        boolean turnPending, boolean turnReserveCovered
    ) {
        return landingSecured && runwayCovered && (!turnPending || turnReserveCovered);
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
                if (!isTellySupport(MC.level.getBlockState(pos), pos)) continue;
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
        if (isTellySupport(MC.level.getBlockState(catchBlock), catchBlock)) {

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
            if (!isTellySupport(state, cell) && !state.isAir() && !state.canBeReplaced()) {
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

    private boolean tellyEarlyLaunchAllowed(LocalPlayer player, BlockPos under, TellyPlacement first) {
        if (player == null || under == null || first == null) return false;
        if (tellyStopRequested || tellyTurnIntentPending() || tellyTurnSettling) return false;

        if (!tellyStreamAlignedForLaunch()) return false;

        boolean catchable = (tellyRiseQueued || tellySpaceHeld)
            ? isTellyRiseLaunchCatchable(player, under)
            : isTellyLaunchCatchable(player, under);
        if (!catchable) return false;

        if (!tellyEarlyLaunchLaneClear(tellyLaneError(player))) return false;

        return tellyEarlyLaunchSteps(tellyProjectedLaunchSteps(player, under));
    }

    private double tellyLaneError(LocalPlayer player) {
        if (player == null) return 0.0D;
        return tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
    }

    static boolean tellyEarlyLaunchSteps(int steps) {
        return steps >= 2 && steps <= 4;
    }

    static boolean tellyEarlyLaunchLaneClear(double laneError) {
        return Math.abs(laneError) <= TELLY_LANE_ENTER;
    }

    private int tellyProjectedLaunchSteps(LocalPlayer player, BlockPos support) {
        if (player == null || support == null || MC.level == null) return -1;
        Vec3 launchVelocity = predictedTellyLaunchVelocity(player);
        double launchDrag = MC.level.getBlockState(support).getBlock().getFriction() * 0.91D;
        Vec3 landing = projectTellyLaunchLanding(
            player.position(), launchVelocity, player.getY(), tellyForwardVector(), launchDrag);
        Direction direction = tellyForwardDirection();
        BlockPos catchBlock = BlockPos.containing(landing.x, support.getY(), landing.z);
        return (catchBlock.getX() - support.getX()) * direction.getStepX()
            + (catchBlock.getZ() - support.getZ()) * direction.getStepZ();
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

    static TellyPhase nextTellyCoveragePhase(
        TellyPhase current, boolean chainCovered, boolean risePending
    ) {
        if (current == TellyPhase.RETURNING) return TellyPhase.RETURNING;
        return chainCovered && !risePending ? TellyPhase.RETURNING : current;
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
        if (!isTellySupport(MC.level.getBlockState(cell), cell)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = cell.relative(direction);
                if (isTellySupport(MC.level.getBlockState(neighbor), neighbor)) {
                    return direction.getOpposite();
                }
            }
            return null;
        }
        double fracX = pos.x - Math.floor(pos.x);
        double fracZ = pos.z - Math.floor(pos.z);
        return tellyOverhangDirection(fracX, fracZ,
            !isTellySupport(MC.level.getBlockState(cell.north()), cell.north()),
            !isTellySupport(MC.level.getBlockState(cell.south()), cell.south()),
            !isTellySupport(MC.level.getBlockState(cell.west()), cell.west()),
            !isTellySupport(MC.level.getBlockState(cell.east()), cell.east()));
    }

    private BlockPos tellyLaneCell(BlockPos raw) {
        if (tellyLastBridge == null) return raw;
        return tellyForwardDirection().getAxis() == Direction.Axis.Z
            ? new BlockPos(tellyLastBridge.getX(), raw.getY(), raw.getZ())
            : new BlockPos(raw.getX(), raw.getY(), tellyLastBridge.getZ());
    }

    private BlockPos tellySolidChainRoot() {
        if (tellyLastBridge == null) return null;
        if (isTellySupport(MC.level.getBlockState(tellyLastBridge), tellyLastBridge)) return tellyLastBridge;
        Direction back = tellyForwardDirection().getOpposite();
        for (int step = 1; step <= 3; step++) {
            BlockPos candidate = tellyLastBridge.relative(back, step);
            if (isTellySupport(MC.level.getBlockState(candidate), candidate)) {
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

    static boolean grimCellClearOfBody(AABB box, Vec3 velocity, BlockPos cell) {
        return grimCellClearOfBody(box, velocity, cell, false);
    }

    static boolean grimCellClearOfBody(
        AABB box, Vec3 velocity, BlockPos cell, boolean fallingCatch
    ) {
        if (box == null || cell == null) return true;
        AABB cellBox = new AABB(cell);
        if (box.intersects(cellBox)) return false;
        if (velocity == null || velocity.y >= 0.0D) return true;
        double feet = box.minY;
        double top = cellBox.maxY;
        if (feet <= top) return true;

        double x = 0.0D;
        double z = 0.0D;
        double vy = velocity.y;
        boolean crossed = false;
        for (int tick = 0; tick < GRIM_BODY_CLEAR_LOOKAHEAD_TICKS; tick++) {
            x += velocity.x;
            feet += vy;
            z += velocity.z;
            vy = (vy - 0.08D) * 0.98D;
            if (feet >= top) continue;
            boolean firstCross = !crossed;
            crossed = true;
            AABB swept = box.move(x, 0.0D, z);
            boolean over = swept.maxX > cellBox.minX && swept.minX < cellBox.maxX
                && swept.maxZ > cellBox.minZ && swept.minZ < cellBox.maxZ;

            if (fallingCatch && firstCross && over) return true;
            if (over) return false;
        }
        return true;
    }

    static String grimCellClearReason(
        AABB box, Vec3 velocity, BlockPos cell, boolean fallingCatch
    ) {
        if (box == null || cell == null) return "ok";
        AABB cellBox = new AABB(cell);
        if (box.intersects(cellBox)) return "box";
        if (velocity == null || velocity.y >= 0.0D) return "ok";
        double feet = box.minY;
        double top = cellBox.maxY;
        if (feet <= top) return "ok";
        double x = 0.0D;
        double z = 0.0D;
        double vy = velocity.y;
        boolean crossed = false;
        for (int tick = 0; tick < GRIM_BODY_CLEAR_LOOKAHEAD_TICKS; tick++) {
            x += velocity.x;
            feet += vy;
            z += velocity.z;
            vy = (vy - 0.08D) * 0.98D;
            if (feet >= top) continue;
            boolean firstCross = !crossed;
            crossed = true;
            AABB swept = box.move(x, 0.0D, z);
            boolean over = swept.maxX > cellBox.minX && swept.minX < cellBox.maxX
                && swept.maxZ > cellBox.minZ && swept.minZ < cellBox.maxZ;
            if (fallingCatch && firstCross && over) return "ok";
            if (over) return !fallingCatch ? "strict" : "side";
        }
        return "ok";
    }

    static AutismRotationUtil.Rotation stepCappedRotation(
        AutismRotationUtil.Rotation current, AutismRotationUtil.Rotation goal,
        float yawCap, float pitchCap, double gcd) {
        if (current == null) return goal;
        if (goal == null) return current;
        AutismRotationUtil.Rotation stepped = AutismRotationUtil.towardsLinear(current, goal, yawCap, pitchCap);
        if (gcd <= 0.0D) return stepped;
        float yawDiff = AutismRotationUtil.angleDifference(stepped.yaw(), current.yaw());
        float pitchDiff = AutismRotationUtil.angleDifference(stepped.pitch(), current.pitch());
        float yaw = current.yaw() + (float) (Math.round(yawDiff / gcd) * gcd);
        float pitch = current.pitch() + (float) (Math.round(pitchDiff / gcd) * gcd);
        return new AutismRotationUtil.Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    static AutismRotationUtil.Rotation stepTellyRotation(
        AutismRotationUtil.Rotation current, AutismRotationUtil.Rotation goal,
        float stepCap, double gcd) {
        return stepCappedRotation(current, goal, stepCap, stepCap, gcd);
    }

    static AutismRotationUtil.Rotation tellyMouseBurstRotation(
        AutismRotationUtil.Rotation current, AutismRotationUtil.Rotation goal,
        float yawCap, float pitchCap, double gcd
    ) {
        if (current == null) return goal;
        if (goal == null) return current;
        double quantum = normalizedTellyMouseGcd(gcd);
        int yawCounts = cappedTellyMouseCounts(
            AutismRotationUtil.angleDifference(goal.yaw(), current.yaw()), yawCap, quantum);
        int pitchCounts = cappedTellyMouseCounts(goal.pitch() - current.pitch(), pitchCap, quantum);
        return new AutismRotationUtil.Rotation(
            Mth.wrapDegrees(current.yaw() + (float) (yawCounts * quantum)),
            Mth.clamp(current.pitch() + (float) (pitchCounts * quantum), -89.9F, 89.9F));
    }

    private static int cappedTellyMouseCounts(double delta, float cap, double gcd) {
        if (!Double.isFinite(delta) || !Float.isFinite(cap) || cap <= 0.0F) return 0;
        long wanted = Math.round(delta / gcd);
        long maximum = Math.max(0L, (long) Math.floor(cap / gcd + 1.0E-9D));
        return (int) Math.max(-maximum, Math.min(maximum, wanted));
    }

    private static double normalizedTellyMouseGcd(double gcd) {
        return Double.isFinite(gcd) && gcd > 0.0D ? gcd : 0.15D;
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
        return tellyForwardVector(tellyAnchorYaw);
    }

    static Vec3 tellyForwardVector(float anchorYaw) {
        float radians = anchorYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
    }

    private Vec3 tellyLeftVector() {
        Vec3 forward = tellyForwardVector();
        return new Vec3(forward.z, 0.0D, -forward.x);
    }

    private Direction tellyForwardDirection() {
        return tellyDirectionForYaw(tellyAnchorYaw);
    }

    static Direction tellyDirectionForYaw(float yaw) {
        int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
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
        return new AutismRotationUtil.Rotation(tellyCourseLookYaw(), tellyForwardPitch);
    }

    private AutismRotationUtil.Rotation tellyGroundSteeringRotation() {
        return new AutismRotationUtil.Rotation(
            Mth.wrapDegrees(tellyAnchorYaw + tellyGroundSteerOffset + tellyLookYawOffset), tellyForwardPitch);
    }

    private float tellyCourseLookYaw() {
        return tellyCourseLookYaw(tellyAnchorYaw, tellyLookYawOffset);
    }

    static float tellyCourseLookYaw(float anchorYaw, float lookOffset) {
        return Mth.wrapDegrees(anchorYaw + lookOffset);
    }

    private float tellyGroundStep() {
        return TELLY_GROUND_MAX_STEP;
    }

    private AutismRotationUtil.Rotation advanceTellyRotationStream(LocalPlayer player) {
        if (tellyRotationHeldForPlacement) {

            tellyRotationHeldForPlacement = false;
            if (tellySmoothedRotation != null) return tellySmoothedRotation;
        }
        if (tellyRotationAdvancedThisTick() && tellySmoothedRotation != null) {
            return tellySmoothedRotation;
        }
        TellyRotationGoal goal = selectTellyRotationGoal(player);
        if (goal.intent() == TellyRotationIntent.RETURN) {
            return completeTellyReturn();
        }
        if (usesTellyAirFlick(goal.intent())) {
            tellyReturnCompleted = false;
            return applyTellyMouseBurst(goal.rotation());
        }

        if (goal.intent() == TellyRotationIntent.FORWARD && !player.onGround()) {
            return completeTellyReturn();
        }

        if (goal.intent() == TellyRotationIntent.HOLD || !player.onGround()) {
            return holdTellyRotation();
        }
        return stepTellyStreamOnce(
            player, goal.rotation(), tellyGroundStep(),
            AutismHumanRotation.MotionProfile.TELLY_FLICK);
    }

    static boolean usesTellyAirFlick(TellyRotationIntent intent) {
        return intent == TellyRotationIntent.PLACEMENT;
    }

    private boolean tellyRotationAdvancedThisTick() {
        return tellyRotationStepTick == AutismSharedState.get().getClientTickCounter();
    }

    private AutismRotationUtil.Rotation holdTellyRotation() {
        if (tellySmoothedRotation == null) tellySmoothedRotation = serverRotation();
        tellyRotationStepTick = AutismSharedState.get().getClientTickCounter();
        grimSilentRotation = tellySmoothedRotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        return tellySmoothedRotation;
    }

    private AutismRotationUtil.Rotation stepTellyStreamOnce(
        LocalPlayer player, AutismRotationUtil.Rotation goal, float cap,
        AutismHumanRotation.MotionProfile profile
    ) {
        if (tellyRotationAdvancedThisTick() && tellySmoothedRotation != null) {
            return tellySmoothedRotation;
        }
        if (!AutismHumanRotation.isInitialized(tellyStream)) {
            AutismHumanRotation.seed(tellyStream,
                tellySmoothedRotation != null ? tellySmoothedRotation : serverRotation());
        }
        tellySmoothedRotation = AutismHumanRotation.step(
            tellyStream, goal, cap, cap, AutismRotationUtil.sensitivityGcd(), false,
            profile);
        tellyRotationStepTick = AutismSharedState.get().getClientTickCounter();
        grimSilentRotation = tellySmoothedRotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        return tellySmoothedRotation;
    }

    private AutismRotationUtil.Rotation applyTellyMouseBurst(AutismRotationUtil.Rotation goal) {
        return applyTellyMouseBurst(goal, TELLY_MOUSE_BURST_MAX_STEP);
    }

    private AutismRotationUtil.Rotation applyTellyMouseBurst(
        AutismRotationUtil.Rotation goal, float cap) {
        tellySmoothedRotation = tellyMouseBurstRotation(
            serverRotation(), goal, cap, cap, AutismRotationUtil.sensitivityGcd());
        AutismHumanRotation.seed(tellyStream, tellySmoothedRotation);
        tellyRotationStepTick = AutismSharedState.get().getClientTickCounter();
        grimSilentRotation = tellySmoothedRotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;
        return tellySmoothedRotation;
    }

    private void adoptTellyPlacementRotation(AutismRotationUtil.Rotation rotation) {
        tellySmoothedRotation = rotation;

        AutismHumanRotation.seed(tellyStream, rotation);
        grimSilentRotation = rotation;
        grimRotationResetTicks = ROTATION_RESET_TICKS;

        tellyRotationStepTick = AutismSharedState.get().getClientTickCounter();
        tellyRotationHeldForPlacement = true;
        tellyAimCommitted = true;
        tellyReturnCompleted = false;
    }

    private TellyRotationGoal selectTellyRotationGoal(LocalPlayer player) {
        if (player.onGround()) {
            if (tellyMotion == TellyMotion.HOLD) {

                boolean turnReserveTarget = tellyTarget != null && tellyTurnReserveCell != null
                    && tellyTurnReserveCell.equals(tellyTarget.target().placedBlock());
                TellyPlacement heldTarget = tellyGroundHoldUsesChainTarget(
                    tellyStopRequested, tellyFinishing) || turnReserveTarget ? tellyTarget : null;
                if (heldTarget != null) {
                    return new TellyRotationGoal(
                        tellyPlacementRotationGoal(player, heldTarget), TellyRotationIntent.PLACEMENT);
                }
            }
            return new TellyRotationGoal(
                tellyGroundSteeringActive ? tellyGroundSteeringRotation() : tellyForwardRotation(),
                TellyRotationIntent.FORWARD);
        }
        if (tellyPhase == TellyPhase.RETURNING || tellyFinishing) {
            return new TellyRotationGoal(tellyForwardRotation(), tellyAirRotationIntent(
                tellyPhase, false, tellyAimCommitted, tellyFinishing, true));
        }
        boolean aimingPhase = !tellyFinishing
            && (tellyPhase == TellyPhase.LAUNCH || tellyPhase == TellyPhase.AIMING);
        if (aimingPhase && tellyCycleRises && !tellyRaisedBlockPlaced) {

            TellyPlacement rise = pendingTellyRiseTarget(player, planningStack());
            if (rise != null) {
                return new TellyRotationGoal(tellyPlacementRotationGoal(player, rise),
                    tellyAirRotationIntent(tellyPhase, true, tellyAimCommitted, tellyFinishing,
                        tellyLandingImminent(player)));
            }
        }
        if (aimingPhase && tellyTarget != null && !tellyShouldDelayFirstClick(player)) {
            return new TellyRotationGoal(tellyPlacementRotationGoal(player, tellyTarget),
                tellyAirRotationIntent(tellyPhase, true, tellyAimCommitted, tellyFinishing,
                        tellyLandingImminent(player)));
        }

        TellyRotationIntent gapIntent = tellyAirRotationIntent(
            tellyPhase, false, tellyAimCommitted, tellyFinishing, tellyLandingImminent(player));
        if (gapIntent == TellyRotationIntent.HOLD && tellySmoothedRotation != null) {
            return new TellyRotationGoal(tellySmoothedRotation, gapIntent);
        }
        return new TellyRotationGoal(tellyForwardRotation(), TellyRotationIntent.FORWARD);
    }

    static TellyRotationIntent tellyAirRotationIntent(
        TellyPhase phase, boolean placementGoal, boolean aimCommitted, boolean finishing,
        boolean landingImminent
    ) {
        if (finishing || phase == TellyPhase.RETURNING) return TellyRotationIntent.RETURN;
        if (placementGoal) return TellyRotationIntent.PLACEMENT;

        if (landingImminent) return TellyRotationIntent.FORWARD;
        if (aimCommitted && (phase == TellyPhase.LAUNCH || phase == TellyPhase.AIMING)) {
            return TellyRotationIntent.HOLD;
        }
        return TellyRotationIntent.FORWARD;
    }

    private boolean tellyLandingImminent(LocalPlayer player) {
        if (player == null || player.onGround()) return false;
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y >= 0.0D) return false;
        double catchFeetY = tellyBridgeY + (tellyRaisedBlockPlaced ? 2.0D : 1.0D);
        return tellyTicksUntilCatch(player.position(), velocity, catchFeetY)
            <= TELLY_RETURN_LEAD_TICKS;
    }

    private TellyPlacement pendingTellyRiseTarget(LocalPlayer player, ItemStack stack) {
        if (player == null || tellyLastBridge == null || !tellyRiseStillPossible(player)
            || !isValidBlock(stack)) return null;
        BlockPos riseCell = tellyRiseSupportCell(player);
        return riseCell == null ? null : raisedTellyPlacement(player, stack, riseCell);
    }

    static boolean tellyGroundHoldUsesChainTarget(boolean stopRequested, boolean finishing) {
        return stopRequested || finishing;
    }

    private void continueTellyReturnAfterPlanning(LocalPlayer player) {
        if (!tellyOwnsInput || player == null || player.onGround()
            || tellyPhase != TellyPhase.RETURNING) return;
        completeTellyReturn();
    }

    private AutismRotationUtil.Rotation completeTellyReturn() {
        return completeTellyReturn(TELLY_MOUSE_BURST_MAX_STEP);
    }

    private AutismRotationUtil.Rotation completeTellyReturn(float cap) {
        if (tellyReturnCompleted && tellyStreamAlignedForLaunch()) return holdTellyRotation();
        AutismRotationUtil.Rotation returned = applyTellyMouseBurst(tellyForwardRotation(), cap);
        tellyReturnCompleted = true;
        tellyAimCommitted = false;
        return returned;
    }

    private AutismRotationUtil.Rotation tellyPlacementRotationGoal(
        LocalPlayer player, TellyPlacement placement
    ) {
        TellyPlacement live = liveTellyPlacement(player, placement);
        if (live != null) return live.target().rotation();
        AutismRotationUtil.Rotation stored = AutismRotationUtil.lookingAt(
            placement.target().hit().getLocation(), player.getEyePosition(1.0F));
        if (tellySmoothedRotation == null
            || !tellyHoldsAimWhileFaceIsAhead(false, tellyAimCommitted, player.onGround(),
                placement.target().face().getAxis().isVertical(), tellyLandingImminent(player))) {
            return stored;
        }
        return new AutismRotationUtil.Rotation(
            tellyHeldYawWithDither(tellySmoothedRotation.yaw()), stored.pitch());
    }

    static boolean tellyHoldsAimWhileFaceIsAhead(
        boolean hasLiveSample, boolean aimCommitted, boolean grounded,
        boolean verticalFace, boolean landingImminent
    ) {
        if (hasLiveSample || !aimCommitted || grounded) return false;

        if (verticalFace) return false;

        return !landingImminent;
    }

    private static final int TELLY_HELD_YAW_DITHER_PERCENT = 40;

    private float tellyHeldYawWithDither(float heldYaw) {
        double quantum = AutismRotationUtil.sensitivityGcd();
        if (quantum <= 0.0D || rotationRandom.nextInt(100) >= TELLY_HELD_YAW_DITHER_PERCENT) {
            return heldYaw;
        }
        return heldYaw + (float) (rotationRandom.nextBoolean() ? quantum : -quantum);
    }

    private boolean tellyStreamAlignedForLaunch() {
        return tellySmoothedRotation != null
            && Math.abs(AutismRotationUtil.angleDifference(
                tellyCourseLookYaw(), tellySmoothedRotation.yaw())) <= TELLY_LAUNCH_YAW_EPSILON;
    }

    private boolean tellyShouldDelayFirstClick(LocalPlayer player) {
        if (tellyAimCommitted) return false;

        if (tellyTurnIntentPending()) {
            tellyAimCommitted = true;
            return false;
        }
        if (tellyFlatPlacements > 0 || tellyWalkOffCatch || tellyFinishing) {
            tellyAimCommitted = true;
            return false;
        }
        if (tellyTarget == null) return false;
        Vec3 velocity = player.getDeltaMovement();

        boolean risePending = tellyCycleRises && !tellyRaisedBlockPlaced;
        double catchFeetY = tellyBridgeY + (risePending ? 2.0D : 1.0D);
        int ticksLeft = tellyTicksUntilCatch(player.position(), velocity, catchFeetY);
        Vec3 landing = projectTellyLandingWithInput(
            player.position(), velocity, catchFeetY, tellyEffectiveForward(player));

        int blocksNeeded = requiredTellyBlocksToLanding(
            landing, tellyForwardVector(), tellyLastBridge)
            + tellyRunwayReserveBlocks(tellyTurnIntentPending());
        double nextTickFaceDistance = player.getEyePosition().add(velocity)
            .distanceTo(tellyTarget.target().hit().getLocation());
        boolean delay = tellyAimDelayAllowed(
            ticksLeft, blocksNeeded, nextTickFaceDistance, player.blockInteractionRange(),
            risePending);

        tellyAimCommitted = nextTellyAimCommitted(tellyAimCommitted, delay);
        return delay;
    }

    static boolean nextTellyAimCommitted(boolean committed, boolean delayAllowed) {
        return committed || !delayAllowed;
    }

    static int tellyRunwayReserveBlocks(boolean turnPending) {
        return turnPending ? 2 : 1;
    }

    static boolean tellyAimDelayAllowed(
        int rawTicksLeft, int blocksNeeded, double nextTickFaceDistance, double reach
    ) {
        return tellyAimDelayAllowed(
            rawTicksLeft, blocksNeeded, nextTickFaceDistance, reach, false);
    }

    static boolean tellyAimDelayAllowed(
        int rawTicksLeft, int blocksNeeded, double nextTickFaceDistance, double reach,
        boolean risePending
    ) {
        int reserve = tellyAimSweepReserveTicks(blocksNeeded, risePending);
        return tellyLateFlickBudgetAllows(
            rawTicksLeft - reserve,
            blocksNeeded, nextTickFaceDistance, reach);
    }

    static int tellyAimSweepReserveTicks(int blocksNeeded, boolean risePending) {
        return TELLY_AIM_SWEEP_RESERVE_TICKS;
    }

    private boolean tellyFlickBackForLaunch() {
        if (tellyStreamAlignedForLaunch()) return true;

        completeTellyReturn(TELLY_MOUSE_BURST_RESCUE_STEP);
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

    private void releaseTellyRotationStream() {
        tellySmoothedRotation = null;
        if (grimWindingDown) return;
        if (grimSilentRotation != null && AutismHumanRotation.isInitialized(tellyStream)) {
            grimWindingDown = true;
        } else if (grimSilentRotation == null) {
            grimRotationResetTicks = 0;
            AutismHumanRotation.clear(tellyStream);
        }
    }

    private void resetTellyState() {
        releaseTellyRotationStream();
        clearTellyAirCorrection();

        clearTellyLaneBias();
        cancelTellyRealClick();
        tellyHoldStrafe = TellyStrafe.NONE;
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
        tellyAimCommitted = false;
        tellyRaisedCell = null;
        tellyPlacementQueued = false;
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
        tellyLookYawOffset = 0.0F;
        tellyRotationStepTick = Integer.MIN_VALUE;
        tellyPipelineTick = Integer.MIN_VALUE;
        tellyForwardPitch = 0.0F;
        tellyLaneCenter = 0.0D;
        tellyRecoveryTicks = 0;
        tellyCourseLatched = false;
        tellyCourseDeviationTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyGroundSteeringActive = false;
        tellyGroundSteerOffset = 0.0F;
        tellyTurnSettling = false;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = 0;
        tellyRotationHeldForPlacement = false;
        tellyFaceOffsetIndex = -1;
        tellyReturnCompleted = true;
        tellyHoldWatchdogTicks = 0;
        tellyLastBridge = null;
        tellyQueuedBlock = null;
        clearTellyTurnReserve();
        tellyLineOrigin = null;
        tellyTarget = null;
    }

    private void releaseTellyControl() {
        releaseTellyRotationStream();
        clearTellyAirCorrection();
        clearTellyLaneBias();
        cancelTellyRealClick();
        tellyHoldStrafe = TellyStrafe.NONE;
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
        tellyAimCommitted = false;
        tellyCourseDeviationTicks = 0;
        tellyEdgeHoldTicks = 0;
        tellyTurnSettling = false;
        tellySettleHoldTicks = 0;
        tellySettleDwellTicks = 0;
        tellyRotationHeldForPlacement = false;
        tellyFaceOffsetIndex = -1;
        tellyReturnCompleted = true;
        tellyRotationStepTick = Integer.MIN_VALUE;
        tellyPipelineTick = Integer.MIN_VALUE;
        tellyHoldWatchdogTicks = 0;
        tellyTarget = null;
        tellyQueuedBlock = null;
        tellyPlacementQueued = false;
        clearTellyTurnReserve();
    }

    @Override
    public void tick() {

        if (!isEnabled()) advanceGrimWindDown();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return grimWindingDown && grimSilentRotation != null;
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

    public static void onFinalPacketWritten(Packet<?> packet) {
        GrimQueuedUse queued = null;
        if (packet instanceof ServerboundUseItemOnPacket) {
            synchronized (GRIM_QUEUED_USES) {
                purgeCollectedGrimQueuedUses();
                queued = GRIM_QUEUED_USES.remove(new GrimPacketIdentity(packet));
            }
        }
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.ownsRealClickPipeline()) return;
        if (packet instanceof ServerboundMovePlayerPacket movement) {
            GRIM_FINAL_MOVE_WRITE.set(new GrimFinalMoveWrite(
                movement.isOnGround(), movement.horizontalCollision(), movement.hasPosition()));
            return;
        }
        if (!(packet instanceof ServerboundUseItemOnPacket use)) return;

        if (queued == null) return;
        BlockHitResult hit = use.getHitResult();
        GRIM_FINAL_USE_WRITES.offer(new GrimFinalUseWrite(
            use.getSequence(), hit.getBlockPos().immutable(), hit.getDirection(), hit.getLocation(),
            System.nanoTime(), use.getHand(), queued, AutismServerRotationView.snapshot()));
    }

    public static void onConnectionClosed() {
        synchronized (GRIM_QUEUED_USES) {
            GRIM_QUEUED_USES.clear();
            while (GRIM_QUEUED_USE_GC.poll() != null) {

            }
        }
        GRIM_FINAL_USE_WRITES.clear();
        GRIM_FINAL_MOVE_WRITE.set(null);
    }

    private void drainGrimFinalUseWrite() {
        GrimFinalUseWrite written;
        while ((written = GRIM_FINAL_USE_WRITES.poll()) != null) {
            grimOnFinalUseWritten(written);
        }
    }

    private void drainGrimFinalMoveWrite() {
        GrimFinalMoveWrite written = GRIM_FINAL_MOVE_WRITE.getAndSet(null);
        if (written == null) return;
        grimFinalMoveSeen = true;
        grimFinalWireGround = written.onGround();
        grimFinalWireHorizontalCollision = written.horizontalCollision();
        grimFinalWireHasPosition = written.hasPosition();
    }

    private void grimOnFinalUseWritten(GrimFinalUseWrite written) {
        GrimQueuedUse queued = written == null ? null : written.queued();
        if (queued == null) return;

        GrimWireClickRotation writtenWire = grimWireClickRotation(written.wire());
        AutismRotationUtil.Rotation actualClick = writtenWire == null ? null : writtenWire.current();
        grimPaceQueuedNanos = written.nanos();
        grimPaceBook(queued.placed(), queued.against(), written.face(),
            actualClick == null ? Float.NaN : actualClick.pitch());

        PlacementTarget pending = grimRealPendingTarget;
        boolean matches = grimFinalUseMatches(pending, written, grimAttemptBuildsPlannedCell);
        if (!grimFinalUseBelongsToAttempt(written, grimAttemptGeneration)) {
            if (queued.ordinal() > 0 || grimUntrustedPredictions.containsKey(queued.placed())) {
                quarantineGrimPrediction(queued.placed(), written.sequence());
            }
            return;
        }
        grimAttemptWriteCount++;
        grimAttemptSequence = Math.max(grimAttemptSequence, written.sequence());
        if (pending == null || !matches || written.hand() != queued.hand()
            || written.face() != queued.face()) {
            failGrimPlacementAttempt("packet-mismatch");
            return;
        }
        if (grimFinalWriteIsDuplicate(
            queued, grimAttemptDuplicateSubmitted, grimAttemptState, grimAttemptWriteCount)) {
            failGrimPlacementAttempt("duplicate-write");
            return;
        }

        if (!tellyUsesRealClicks() && !grimFinalWireMatches(queued, written.wire())) {

            failGrimPlacementAttempt("wire-rotation");
                return;
        }
        grimAttemptState = GrimPlacementAttemptState.SENT;
        grimAttemptResult = "sent";
        grimAttemptPaceBooked = true;
        if (actualClick != null) {
            grimLastPlaceYaw = actualClick.yaw();
        }
        resolveGrimUseOutcome();
    }

    static boolean grimFinalUseMatches(PlacementTarget pending, GrimFinalUseWrite written) {
        return grimFinalUseMatches(pending, written, false);
    }

    static boolean grimFinalUseMatches(
        PlacementTarget pending, GrimFinalUseWrite written, boolean buildsPlannedCell
    ) {
        if (pending == null || written == null) return false;
        if (!written.support().equals(pending.supportBlock())) return false;
        if (buildsPlannedCell) return true;
        return written.face() == pending.face()
            && written.location().y >= pending.minPlacementY();
    }

    static boolean grimFinalUseBelongsToAttempt(GrimFinalUseWrite written, long generation) {
        return written != null && written.queued() != null
            && written.queued().generation() == generation;
    }

    static boolean grimFirstFinalWriteForAttempt(
        GrimPlacementAttemptState state, int writeCount
    ) {
        return state == GrimPlacementAttemptState.ARMED && writeCount == 1;
    }

    static boolean grimFinalWriteIsDuplicate(
        GrimQueuedUse queued, boolean duplicateSubmitted,
        GrimPlacementAttemptState state, int writeCount
    ) {
        return queued == null || queued.ordinal() != 0 || duplicateSubmitted
            || !grimFirstFinalWriteForAttempt(state, writeCount);
    }

    static boolean grimFinalWireMatches(
        GrimQueuedUse queued, AutismServerRotationView.WireSnapshot snapshot
    ) {
        if (queued == null || queued.clickRotation() == null || snapshot == null) return false;
        GrimWireClickRotation wire = grimWireClickRotation(snapshot);
        return wire != null && sameRotation(queued.clickRotation(), wire.current());
    }

    public static void onVanillaUseItemOnResult(
        InteractionHand hand, BlockHitResult hit, InteractionResult result
    ) {
        long generation = AutismInputClicker.scaffoldUseGenerationInProgress();
        if (generation <= 0L) return;
        Module module = ModuleRegistry.get("scaffold");
        if (module instanceof ScaffoldModule scaffold && scaffold.isEnabled()
            && scaffold.ownsRealClickPipeline()) {
            scaffold.grimOnVanillaUseResult(generation, hand, hit, result);
        }
    }

    private void grimOnVanillaUseResult(
        long generation, InteractionHand hand, BlockHitResult hit, InteractionResult result
    ) {
        PlacementTarget pending = grimRealPendingTarget;
        if (grimAttemptState == GrimPlacementAttemptState.IDLE
            || !grimUseResultMatchesAttempt(
                generation, grimAttemptGeneration, hand, grimAttemptHand,
                grimAttemptResultSeen, hit, pending,
                grimAttemptBuildsPlannedCell)) return;
        grimAttemptResultSeen = true;
        grimAttemptResultConsumed = result != null && result.consumesAction();
        grimAttemptResult = result == null
            ? "null" : result.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        drainGrimFinalUseWrite();
        resolveGrimUseOutcome();
    }

    static boolean grimUseResultMatchesAttempt(
        long generation, long activeGeneration, InteractionHand hand, InteractionHand expectedHand,
        boolean resultSeen, BlockHitResult hit, PlacementTarget pending
    ) {
        return grimUseResultMatchesAttempt(generation, activeGeneration, hand, expectedHand,
            resultSeen, hit, pending, false);
    }

    static boolean grimUseResultMatchesAttempt(
        long generation, long activeGeneration, InteractionHand hand, InteractionHand expectedHand,
        boolean resultSeen, BlockHitResult hit, PlacementTarget pending, boolean buildsPlannedCell
    ) {
        return !resultSeen && generation > 0L && generation == activeGeneration
            && hand != null && hand == expectedHand && pending != null
            && hit != null && grimClickFeasible(hit, pending, buildsPlannedCell);
    }

    private void resolveGrimUseOutcome() {
        PlacementTarget pending = grimRealPendingTarget;
        if (pending == null || !grimAttemptResultSeen
            || grimAttemptState != GrimPlacementAttemptState.SENT) return;
        if (MC.level == null) return;
        BlockPos cell = pending.placedBlock();
        int age = Math.max(0,
            AutismSharedState.get().getClientTickCounter() - grimRealQueuedTick);
        GrimAttemptDecision decision = grimReduceAttempt(
            grimAttemptState, age, true, grimAttemptResultConsumed,
            isSolidSupport(MC.level.getBlockState(cell), cell),
            grimAckCovers(grimAttemptSequence, grimHighestObservedAck));
        if (decision.state() == GrimPlacementAttemptState.FAILED) {
            failGrimPlacementAttempt("use".equals(decision.failure())
                ? "use-" + grimAttemptResult : decision.failure());
        } else {
            grimAttemptState = decision.state();
        }
    }

    public static void onBlockChangedAckHandled(int sequence) {
        if (MC == null || MC.level == null) return;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.isEnabled()
            || !scaffold.ownsRealClickPipeline()) return;
        scaffold.ensureGrimPredictionLevel();
        if (sequence > scaffold.grimHighestObservedAck) {
            scaffold.grimHighestObservedAck = sequence;
        }
        scaffold.reconcileGrimPlacementAcks();
        scaffold.reconcileGrimUntrustedAck(sequence);
        PlacementTarget active = scaffold.grimRealPendingTarget;
        if (active != null && grimAckCovers(scaffold.grimAttemptSequence, sequence)) {
            boolean solid = scaffold.solidAt(active.placedBlock());
            if (scaffold.grimAttemptState == GrimPlacementAttemptState.FAILED) return;
            int age = Math.max(0, AutismSharedState.get().getClientTickCounter()
                - scaffold.grimRealQueuedTick);
            GrimAttemptDecision decision = grimReduceAttempt(
                scaffold.grimAttemptState, age, scaffold.grimAttemptResultSeen,
                scaffold.grimAttemptResultConsumed, solid, true);
            if (decision.state() == GrimPlacementAttemptState.FAILED) {
                scaffold.failGrimPlacementAttempt(decision.failure());
            } else {
                scaffold.grimAttemptState = decision.state();
            }
        }
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
            && !MacroExecutor.isRunning()

            && !AutoTotemModule.operationActive()
            && !AutoArmorModule.operationActive();
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
                || MC != null && MC.options != null && (physicallyDown(MC.options.keyUp)

                    || physicallyDown(MC.options.keyDown)
                    || physicallyDown(MC.options.keyLeft)
                    || physicallyDown(MC.options.keyRight)));
    }

    public static boolean reservesRageInput() {
        Module module = ModuleRegistry.get("scaffold");
        return module instanceof ScaffoldModule scaffold
            && scaffold.isEnabled()
            && scaffold.isRageMode();
    }

    public static Input modifyMovementInput(ClientInput source, Input original) {
        if (original == null || MC == null || MC.player == null || MC.player.input != source) return original;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold)) return original;
        if (!silentCorrectionOwnsInput(scaffold.isEnabled(), scaffold.canRun())) {
            scaffold.resetGrimLaunchReservation();

            return scaffold.grimWindingDown ? scaffold.transformSilentMovementInput(original) : original;
        }
        if (scaffold.isTellyMode()) {
            scaffold.resetGrimLaunchReservation();

            Input authored = scaffold.tellyMovementInput(original);
            Input result = scaffold.tellyOwnsInput
                ? scaffold.transformTellyAuthoredInput(authored)
                : scaffold.transformSilentMovementInput(authored);
            scaffold.traceTellyTick();
            return result;
        }

        if (scaffold.isGrimFamily()) {
            scaffold.updateGrimCourseStep();
            scaffold.updateGrimFootingSurface();
            scaffold.updateGrimTakeoffClock();

            scaffold.updateGrimLaneOctant(original);
        }

        boolean directionalInput = hasDirectionalInput(original);
        MovementLine previousLine = scaffold.currentMovementLine;
        MovementLine requestedLine = directionalInput
            ? scaffold.buildMovementLine(original) : null;
        boolean courseChanged = grimRequestedCourseChange(previousLine, requestedLine);
        scaffold.currentMovementLine = requestedLine;

        if (!scaffold.isGrimFamily()) {
            scaffold.resetGrimLaunchReservation();
            return original;
        }
        if (scaffold.grimAttemptDuplicateSubmitted
            || scaffold.grimHasUntrustedPrediction()) {

            scaffold.grimSprintNoForwardTick = AutismSharedState.get().getClientTickCounter();
            scaffold.grimTraceJump = "reconcile";
            Input reconciled = new Input(original.forward(), original.backward(), original.left(),
                original.right(), original.jump(), true, false);

            reconciled = scaffold.transformGrimLegitInput(reconciled, original);
            scaffold.grimNoteStrip("reconcile", original, reconciled);
            scaffold.grimNoteStrip("net", original, reconciled);
            scaffold.grimTraceLaunchLedger = "reconcile dup="
                + (scaffold.grimAttemptDuplicateSubmitted ? "T" : "F")
                + " untrusted=" + (scaffold.grimHasUntrustedPrediction() ? "T" : "F");
            return reconciled;
        }

        if (!scaffold.isValidBlock(scaffold.planningStack()) && scaffold.shouldSneakAtEdge()) {
            scaffold.resetGrimLaunchReservation();
            scaffold.grimEdgeSneakActive = true;
            scaffold.grimTraceEdgeDanger = false;
            scaffold.grimTraceFallDanger = false;
            scaffold.grimTraceLateralBrink = false;
            scaffold.grimTraceFootingOwed = false;
            scaffold.grimFootingOwedTicks = 0;
            scaffold.grimTraceJump = "empty";
            Input stopped = new Input(original.forward(), original.backward(), original.left(),
                original.right(), original.jump(), true, false);
            stopped = scaffold.transformGrimLegitInput(stopped, original);
            scaffold.grimNoteStrip("empty-stack", original, stopped);
            scaffold.grimNoteStrip("net", original, stopped);
            scaffold.grimTraceLaunchLedger = "empty-stack";
            return stopped;
        }

        boolean carryArc = scaffold.grimCarryArcActive();
        boolean atEdge = scaffold.shouldSneakAtEdge() && !carryArc;

        boolean fallAhead = scaffold.predictFallRisk() == FallRisk.IMMINENT && !carryArc;

        boolean clickArmed = scaffold.grimClickReadyForFullSpeed();
        boolean clickLands = (atEdge || fallAhead) && clickArmed;
        boolean edgeDanger = atEdge && !clickLands;
        boolean fallDanger = fallAhead && !clickLands;

        boolean lateralBrink = scaffold.grimLateralBrink();

        boolean footingOwedRaw = !clickArmed && scaffold.grimFootingOwed();
        scaffold.grimFootingOwedTicks = footingOwedRaw ? scaffold.grimFootingOwedTicks + 1 : 0;
        boolean footingOwed = footingOwedRaw
            && scaffold.grimFootingOwedTicks <= GRIM_FOOTING_OWED_MAX_TICKS;
        scaffold.grimEdgeSneakActive = edgeDanger || fallDanger || lateralBrink || footingOwed;
        scaffold.grimTraceEdgeDanger = edgeDanger;
        scaffold.grimTraceFallDanger = fallDanger;
        scaffold.grimTraceLateralBrink = lateralBrink;
        scaffold.grimTraceFootingOwed = footingOwed;
        scaffold.currentMovementLine = scaffold.retainGrimEdgeLine(
            requestedLine, scaffold.grimEdgeSneakActive);
        if (courseChanged) {

            if (MC.player == null || !MC.player.onGround()) scaffold.grimStickyTarget = null;

            scaffold.resetGrimInputOctant();
        }

        Input adjusted = original;
        BlockPos riseTakeoffCell = scaffold.grimRiseTakeoffCell();
        scaffold.grimTraceRiseTakeoff = riseTakeoffCell;

        boolean physicalClimbIntent = directionalInput && scaffold.grimJumpKeyHeld();
        scaffold.grimPhysicalClimbIntent = physicalClimbIntent;

        scaffold.grimRiseFloorPending();
        if (scaffold.grimRiseFloorCell != null) scaffold.grimTraceTakeoffWhy = "floor";
        scaffold.updateGrimLaunchReservation(physicalClimbIntent, courseChanged);
        boolean wireSneak = scaffold.grimWireSneak(
            adjusted.shift() || scaffold.grimEdgeSneakActive);
        if (!adjusted.shift() && wireSneak) {

            Input beforeSneak = adjusted;
            adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(), adjusted.right(),
                adjusted.jump(), true, adjusted.sprint());
            scaffold.grimNoteStrip("wire-sneak", beforeSneak, adjusted);
        }

        boolean lipStop = grimLipStopApplies(MC.player.onGround(), clickArmed, footingOwedRaw,
            scaffold.grimFootingOwedTicks, GRIM_FOOTING_OWED_MAX_TICKS);
        if (lipStop && !adjusted.shift()) {
            Input beforeLip = adjusted;
            adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(),
                adjusted.right(), adjusted.jump(), true, false);
            scaffold.grimNoteStrip("lip-stop", beforeLip, adjusted);
        }
        scaffold.grimTraceJump = "-";
        if (grimSuppressHeldArcSprint(
            physicalClimbIntent, MC.player.onGround(),
            scaffold.grimLaunchReservationAirborne)) {

            Input beforeSprint = adjusted;
            adjusted = grimWithoutSprint(adjusted);
            scaffold.grimNoteStrip("sprint-drop", beforeSprint, adjusted);
            scaffold.grimSprintNoForwardTick = AutismSharedState.get().getClientTickCounter();
        }
        Input emitted = scaffold.transformGrimLegitInput(adjusted, original);
        scaffold.grimNoteStrip("lane", adjusted, emitted);

        if (MC.player.onGround()) scaffold.grimArcTravelStart = MC.player.position();
        boolean arcBrake = scaffold.grimArcBrake(directionalInput, true);

        boolean climbBrake = scaffold.grimDiagonalClimbBrake(directionalInput, true);
        scaffold.grimTraceBrake = arcBrake
            ? (climbBrake ? "arc+dclimb" : "arc") : climbBrake ? "dclimb" : "--";
        arcBrake |= climbBrake;
        if (arcBrake) {
            Input beforeBrake = emitted;
            emitted = grimCounterMovement(emitted);
            scaffold.grimNoteStrip("brake", beforeBrake, emitted);
        }

        scaffold.grimNoteStrip("net", original, emitted);

        return emitted;
    }

    static boolean grimSuppressHeldArcSprint(
        boolean physicalIntent, boolean grounded, boolean reservationAirborne
    ) {
        return physicalIntent && (grounded || reservationAirborne);
    }

    static Input grimWithoutSprint(Input input) {
        if (input == null || !input.sprint()) return input;
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
            input.jump(), input.shift(), false);
    }

    private boolean grimClickReadyForFullSpeed() {
        drainGrimFinalUseWrite();
        resolveGrimUseOutcome();
        boolean ready = grimRealQueuedTick == AutismSharedState.get().getClientTickCounter()
            && grimAttemptState == GrimPlacementAttemptState.PREDICTED
            && !grimAttemptDuplicateSubmitted
            && grimRealPendingTarget != null
            && solidAt(grimRealPendingTarget.placedBlock());
        grimTraceClickFeasible = ready;
        return ready;
    }

    static boolean grimClickFeasible(BlockHitResult hit, PlacementTarget pending) {
        return grimClickFeasible(hit, pending, false);
    }

    static boolean grimClickFeasible(
        BlockHitResult hit, PlacementTarget pending, boolean buildsPlannedCell
    ) {
        if (hit == null || pending == null) return false;
        if (!hit.getBlockPos().equals(pending.supportBlock())) return false;
        if (buildsPlannedCell) return true;
        return hit.getDirection() == pending.face()
            && hit.getLocation().y >= pending.minPlacementY();
    }

    static boolean advancesCourse(Vec3 from, Vec3 lane, BlockPos cell) {
        if (lane == null) return false;
        double dx = cell.getX() + 0.5D - from.x;
        double dz = cell.getZ() + 0.5D - from.z;
        return dx * lane.x + dz * lane.z > 0.0D;
    }

    private int grimOracleFootingRow() {
        if (MC.player == null) return Integer.MIN_VALUE;
        if (!MC.player.onGround() && grimFootingSurfaceY != Integer.MIN_VALUE) {
            return grimFootingSurfaceY;
        }

        return grimFootingRowUnderFeet();
    }

    private int grimFootingRowUnderFeet() {
        int row = grimFootingRowFor(MC.player.getY());
        if (MC.level == null) return row;
        for (int drop = 0; drop < 2 && !grimRowReachesFeet(row, MC.player.getY()); drop++) row--;
        return row;
    }

    static int grimFootingRowFor(double feetY) {
        return Mth.floor(feetY - 1.0E-3D);
    }

    private boolean grimRowReachesFeet(int row, double feetY) {
        AABB box = MC.player.getBoundingBox();
        for (int x = Mth.floor(box.minX + 1.0E-3D); x <= Mth.floor(box.maxX - 1.0E-3D); x++) {
            for (int z = Mth.floor(box.minZ + 1.0E-3D); z <= Mth.floor(box.maxZ - 1.0E-3D); z++) {
                BlockPos cell = new BlockPos(x, row, z);
                VoxelShape shape = MC.level.getBlockState(cell).getCollisionShape(MC.level, cell);
                if (shape.isEmpty()) continue;
                if (row + shape.max(Direction.Axis.Y) >= feetY - 1.0E-3D) return true;
            }
        }
        return false;
    }

    private boolean grimJumpKeyHeld() {
        return MC.player != null && (physicallyDown(MC.options.keyJump)
            || MC.player.input != null && MC.player.input.keyPresses.jump());
    }

    private void updateGrimLaunchReservation(boolean physicalClimbIntent, boolean courseChanged) {
        if (courseChanged) resetGrimLaunchReservation();
        if (MC.player == null || MC.level == null) {
            resetGrimLaunchReservation();
            grimTraceLaunchLedger = "not-asked";
            return;
        }
        if (!MC.player.onGround()
            && (grimLaunchReservationAirborne || grimLaunchReservedSupport != null
                || physicalClimbIntent)) {

            BlockPos before = grimLaunchReservedSupport;
            BlockPos advanced = grimArcLandingSupport(grimOracleFootingRow());

            boolean stillLands = before != null && (grimArcLandsOnColumnLive(
                    MC.player.position(), MC.player.getDeltaMovement(), before)
                || grimArcLandsOnColumnLive(
                    MC.player.position(), MC.player.getDeltaMovement(), before.above()));
            grimLaunchReservedSupport = grimReservedSupportAfterSample(
                before, advanced == null ? null : advanced.immutable(), false, true, false,
                currentMovementLine == null ? null : currentMovementLine.direction(),
                stillLands);
            grimLaunchReservationAirborne = true;
            boolean moved = before != null && !before.equals(grimLaunchReservedSupport);
            if (moved) {

                grimLaunchReservedRiser = null;
                grimLaunchReservedConnector = null;
                grimLaunchReservedStep = null;
            }
            grimTraceLaunchLedger = "airborne reserved=" + (grimLaunchReservedSupport == null
                ? "--" : grimLaunchReservedSupport.toShortString())
                + (moved ? "<" + before.toShortString() : "");
            return;
        }
        if (!physicalClimbIntent || currentMovementLine == null) {
            resetGrimLaunchReservation();
            grimTraceLaunchLedger = "not-asked intent=" + (physicalClimbIntent ? "T" : "F")
                + " line=" + (currentMovementLine == null ? "F" : "T");
            return;
        }
        if (grimLaunchReservationAirborne) {

            resetGrimLaunchReservation();
        }
        int footingRow = grimOracleFootingRow();
        BlockPos landingSupport = grimArcLandingSupport(footingRow);
        boolean landingKnown = landingSupport != null
            && !MC.level.isOutsideBuildHeight(landingSupport);
        BlockPos beforeGrounded = grimLaunchReservedSupport;
        grimLaunchReservedSupport = grimReservedSupportAfterSample(
            grimLaunchReservedSupport,
            landingKnown ? landingSupport.immutable() : null, true, true, false);
        if (beforeGrounded == null || !beforeGrounded.equals(grimLaunchReservedSupport)) {

            grimLaunchReservedRiser = null;
            grimLaunchReservedStep = null;
        }
        grimLaunchReservedConnector = null;
        int deficit = landingKnown ? grimCellDeficit(landingSupport) : 2;
        if (landingKnown && deficit <= 0
            && (!grimTrustedSolidAt(landingSupport)
                || grimAttemptDuplicateSubmitted
                || !grimAttemptSupportReady(
                    grimAttemptState, grimRealPendingTarget, landingSupport))) {

            deficit = 1;
        }

        grimTraceLaunchLedger = "grounded known=" + (landingKnown ? "T" : "F")
            + " deficit=" + deficit
            + " pend=" + (grimRealPendingTarget != null ? "T" : "F")
            + " land=" + (landingSupport == null ? "--" : landingSupport.toShortString())
            + " landsolid=" + (landingSupport == null ? "--"
                : isSolidSupport(MC.level.getBlockState(landingSupport), landingSupport) ? "T" : "F")

            + " riser=" + (!landingKnown || !grimCourseAscends() ? "--"
                : solidAt(landingSupport.above()) ? "T" : "F");
    }

    static boolean grimAttemptSupportReady(
        GrimPlacementAttemptState state, PlacementTarget pending, BlockPos support
    ) {
        return pending == null || support == null || !pending.placedBlock().equals(support)
            || state == GrimPlacementAttemptState.PREDICTED;
    }

    private void resetGrimLaunchReservation() {
        grimLaunchReservedSupport = null;
        grimLaunchReservedConnector = null;
        grimLaunchReservedRiser = null;
        grimLaunchReservedStep = null;
        grimLaunchReservationAirborne = false;
        grimLaunchReservationStage = "--";
    }

    static BlockPos grimReservedSupportAfterSample(
        BlockPos reserved, BlockPos projected, boolean grounded,
        boolean physicalIntent, boolean courseChanged
    ) {
        return grimReservedSupportAfterSample(
            reserved, projected, grounded, physicalIntent, courseChanged, null, true);
    }

    static BlockPos grimReservedSupportAfterSample(
        BlockPos reserved, BlockPos projected, boolean grounded,
        boolean physicalIntent, boolean courseChanged,
        Vec3 lane, boolean reservedStillLands
    ) {
        if (!physicalIntent || courseChanged) return null;
        if (grounded) return projected;
        if (reserved == null) return projected;
        if (projected == null || reservedStillLands || lane == null) return reserved;
        if (projected.getY() != reserved.getY()) return reserved;
        double ahead = (projected.getX() - reserved.getX()) * lane.x
            + (projected.getZ() - reserved.getZ()) * lane.z;
        return ahead > 0.0D ? projected : reserved;
    }

    private BlockPos grimOwnRiserSupport() {
        if (MC.player == null) return null;
        BlockPos at = BlockPos.containing(MC.player.position());
        return new BlockPos(at.getX(), grimOracleFootingRow(), at.getZ());
    }

    static boolean grimSameColumn(BlockPos a, BlockPos b) {
        return a != null && b != null && a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    private int grimBuiltFloorRow() {
        GrimRowLock lock = grimActiveRowLock();
        return grimBuiltFloorRow(grimOracleFootingRow(),
            lock == null ? Integer.MIN_VALUE : lock.rowY(), grimAirborneBuiltRow);
    }

    static int grimBuiltFloorRow(int oracleRow, int lockRow, int airborneBuiltRow) {
        int floor = oracleRow;
        if (lockRow != Integer.MIN_VALUE) {
            floor = floor == Integer.MIN_VALUE ? lockRow : Math.max(floor, lockRow);
        }
        if (airborneBuiltRow != Integer.MIN_VALUE) {
            floor = floor == Integer.MIN_VALUE ? airborneBuiltRow : Math.max(floor, airborneBuiltRow);
        }
        return floor;
    }

    static boolean grimFloorGateRefuses(int candidateY, int footingRow, boolean descending) {
        return candidateY < footingRow && !descending;
    }

    static boolean grimTierServesBelowBuiltFloor(
        int placedY, int builtFloorRow, boolean fallingBelowFooting, boolean arcLandsOnColumn) {
        return grimFloorGateRefuses(placedY, builtFloorRow, fallingBelowFooting)
            && !arcLandsOnColumn;
    }

    private boolean grimBelowBuiltFloor(PlacementTarget target) {
        if (target == null || MC.player == null) return false;
        BlockPos placed = target.placedBlock();
        return grimTierServesBelowBuiltFloor(
            placed.getY(), grimBuiltFloorRow(), grimDescendingBelowFooting(),
            heldArcLandsOnColumn(MC.player.position(), MC.player.getDeltaMovement(), placed));
    }

    static boolean grimFallServesUncatchable(
        boolean fallingBelowFooting, int placedY, double feetY, double feetVelY) {
        return fallingBelowFooting
            && placedY + 1.0D > feetY + Math.min(feetVelY, 0.0D) + 1.0E-3D;
    }

    private boolean grimFallUncatchable(PlacementTarget target) {
        if (target == null || MC.player == null) return false;
        return grimFallServesUncatchable(
            grimDescendingBelowFooting(), target.placedBlock().getY(),
            MC.player.position().y, MC.player.getDeltaMovement().y);
    }

    static boolean grimGoalErrorClosing(float error, float lastError) {
        return Float.isNaN(lastError) || error < lastError - 0.5F;
    }

    private BlockPos grimRiseTakeoffCellNow() {
        if (MC.player == null || MC.level == null || !MC.player.onGround() || !grimJumpKeyHeld()) {
            grimTraceTakeoffWhy = "air";
            return null;
        }
        if (currentMovementLine == null || currentMovementLine.direction() == null) {
            grimTraceTakeoffWhy = "nolane";
            return null;
        }
        int footingRow = grimOracleFootingRow();
        Vec3 lane = currentMovementLine.direction();
        Vec3 position = MC.player.position();
        int riseY = footingRow + 1;
        GrimRowLock rowLock = grimActiveRowLock();
        BlockPos last = null;
        BlockPos far = null;
        for (double step = 0.5D; step <= GRIM_RISE_TAKEOFF_REACH; step += 0.3D) {
            BlockPos cell = BlockPos.containing(
                position.x + lane.x * step, riseY + 0.5D, position.z + lane.z * step);
            if (cell.equals(last)) continue;
            last = cell;
            if (grimCellBehind(position, lane, cell, 0.0D)) continue;
            BlockPos below = cell.below();
            if (solidAt(below) && grimBoxOverColumn(position, below)) continue;
            if (MC.level.isOutsideBuildHeight(cell)) continue;
            BlockState state = MC.level.getBlockState(cell);
            if (isSolidSupport(state, cell) || !state.isAir() && !state.canBeReplaced()) continue;
            boolean belowReady = grimTrustedSolidAt(below);
            if (!belowReady) continue;
            if (rowLock != null && !rowLock.allows(cell, true)) continue;

            double[] landing = grimHeldTakeoffLanding(
                position, MC.player.getDeltaMovement(), position.y);
            if (landing != null && solidAt(new BlockPos(
                (int) Math.floor(landing[0]), riseY - 1, (int) Math.floor(landing[1])))) {
                grimTraceTakeoffWhy = "lands";
                return null;
            }

            if (far == null) far = cell;
            if (heldArcLandsOnColumnFromGround(position, MC.player.getDeltaMovement(), cell)) {
                grimTraceTakeoffWhy = "cell";
                return cell;
            }
        }
        BlockPos own = grimOwnColumnRiseCell(position, riseY, rowLock);
        if (own != null) {
            grimTraceTakeoffWhy = "own";
            return own;
        }

        grimTraceTakeoffWhy = far != null ? "far" : "none";
        return far;
    }

    private String grimTraceTakeoffWhy = "air";

    private BlockPos grimOwnColumnRiseCell(Vec3 position, int riseY, GrimRowLock rowLock) {
        BlockPos cell = grimOwnColumnRiseCellIgnoringFloor(position, riseY, rowLock);
        return cell != null && solidAt(cell.below()) ? cell : null;
    }

    private BlockPos grimOwnColumnRiseCellIgnoringFloor(
        Vec3 position, int riseY, GrimRowLock rowLock) {

        if (!grimBridgeRunning(AutismSharedState.get().getClientTickCounter())) return null;
        BlockPos foot = grimEffectiveFootCell();
        BlockPos cell = new BlockPos(foot.getX(), riseY, foot.getZ());
        if (MC.level.isOutsideBuildHeight(cell)) return null;
        BlockState state = MC.level.getBlockState(cell);
        if (isSolidSupport(state, cell) || !state.isAir() && !state.canBeReplaced()) return null;
        if (!grimBoxOverColumn(position, cell.below())) return null;
        if (rowLock != null && !rowLock.allows(cell, true)) return null;
        return cell;
    }

    private boolean grimRiseFloorPending() {
        grimRiseFloorCell = null;
        if (MC.player == null || MC.level == null) return false;

        if (!grimJumpKeyHeld() && !grimLaunchReservationAirborne) return false;
        BlockPos support = grimArcLandingSupport(grimOracleFootingRow());
        if (support == null || solidAt(support)) return false;
        if (grimCellDeficit(support) >= 2) return false;
        grimRiseFloorCell = support;
        grimRiseFloorTick = AutismSharedState.get().getClientTickCounter();
        return true;
    }

    private int grimCellDeficit(BlockPos cell) {
        if (MC.level == null || cell == null || MC.level.isOutsideBuildHeight(cell)) return 0;
        if (isSolidSupport(MC.level.getBlockState(cell), cell)) return 0;
        for (Direction face : Direction.values()) {
            BlockPos neighbour = cell.relative(face);
            if (MC.level.isOutsideBuildHeight(neighbour)) continue;
            if (isSolidSupport(MC.level.getBlockState(neighbour), neighbour)) return 1;
        }
        return 2;
    }

    private BlockPos grimRiseFloorCell;
    private int grimRiseFloorTick = Integer.MIN_VALUE;

    private boolean grimRiseFloorAwaiting(BlockPos cell) {
        return grimRiseFloorCell != null && grimRiseFloorCell.equals(cell)
            && grimRiseFloorTick == AutismSharedState.get().getClientTickCounter();
    }

    private BlockPos grimRiseTakeoffCell() {
        BlockPos now = grimRiseTakeoffCellNow();
        if (now != null) {
            grimRiseTakeoffLatch = now;
            grimRiseTakeoffLatchTicks = GRIM_RISE_TAKEOFF_LATCH_TICKS;
            return now;
        }
        if (grimRiseTakeoffLatch == null) return null;
        boolean climbing = MC.player != null && MC.player.onGround() && grimJumpKeyHeld();
        if (!climbing || grimRiseTakeoffLatchTicks <= 0 || solidAt(grimRiseTakeoffLatch.below())) {
            grimRiseTakeoffLatch = null;
            grimRiseTakeoffLatchTicks = 0;
            return null;
        }
        grimRiseTakeoffLatchTicks--;

        grimTraceTakeoffWhy = "latch";
        return grimRiseTakeoffLatch;
    }

    private BlockPos grimRiseTakeoffLatch;
    private int grimRiseTakeoffLatchTicks;

    static boolean grimLipStopApplies(boolean onGround, boolean clickArmed, boolean footingOwed,
        int owedTicks, int maxTicks) {
        return onGround && !clickArmed && footingOwed && owedTicks > maxTicks;
    }

    static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy,
        double surfaceY, boolean grounded) {
        return grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded, 0);
    }

    static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy,
        double surfaceY, boolean grounded, int sneakedTicks) {
        return grimHeldArcLanding(
            position, velocity, initialVy, surfaceY, grounded, sneakedTicks, null, null, null);
    }

    static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy,
        double surfaceY, boolean grounded, int sneakedTicks, Vec3 inputDirection) {
        return grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded,
            sneakedTicks, inputDirection, null, null);
    }

    @FunctionalInterface
    private interface GrimArcCollisionResolver {
        Vec3 resolve(AABB box, Vec3 movement);
    }

    static double grimHeldControlMagnitude(Vec3 normalizedDirection, boolean sneaking) {
        if (normalizedDirection == null) return 0.0D;
        double maxAxis = Math.max(Math.abs(normalizedDirection.x),
            Math.abs(normalizedDirection.z));
        if (maxAxis <= 1.0E-9D) return 0.0D;
        double scaled = 0.98D * (sneaking ? GRIM_SNEAK_INPUT_SCALE : 1.0D);
        return Math.min(1.0D, scaled / maxAxis);
    }

    private static double[] grimHeldArcLanding(
        Vec3 position, Vec3 velocity, double initialVy, double surfaceY, boolean grounded,
        int sneakedTicks, Vec3 inputDirection, AABB initialBox,
        GrimArcCollisionResolver collisionResolver
    ) {
        if (position == null || velocity == null) return null;
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double vx = velocity.x;
        double vy = initialVy;
        double vz = velocity.z;
        Vec3 heldDirection = null;
        if (inputDirection != null && inputDirection.horizontalDistanceSqr() > 1.0E-12D) {
            double length = Math.sqrt(inputDirection.x * inputDirection.x
                + inputDirection.z * inputDirection.z);
            heldDirection = new Vec3(inputDirection.x / length, 0.0D,
                inputDirection.z / length);
        }
        AABB box = initialBox;
        boolean first = grounded;
        boolean descending = vy < 0.0D;
        if (descending && y < surfaceY) return null;
        for (int tick = 0; tick < GRIM_LANDING_MAX_AIR_TICKS; tick++) {

            if (vx * vx + vz * vz
                < GRIM_HORIZONTAL_ZERO_THRESHOLD * GRIM_HORIZONTAL_ZERO_THRESHOLD) {
                vx = 0.0D;
                vz = 0.0D;
            }
            if (Math.abs(vy) < 0.003D) vy = 0.0D;
            double speed = Math.sqrt(vx * vx + vz * vz);
            double scale = tick < sneakedTicks ? GRIM_SNEAK_INPUT_SCALE : 1.0D;
            double movedVx = vx;
            double movedVz = vz;
            double directionX = heldDirection == null
                ? speed <= 1.0E-6D ? 0.0D : vx / speed
                : heldDirection.x;
            double directionZ = heldDirection == null
                ? speed <= 1.0E-6D ? 0.0D : vz / speed
                : heldDirection.z;
            double controlMagnitude = heldDirection == null ? scale
                : grimHeldControlMagnitude(heldDirection, tick < sneakedTicks);
            if (first) {

                double acceleration = heldDirection == null
                    ? GRIM_GROUND_WALK_ACCEL * scale : 0.1D * controlMagnitude;
                movedVx += directionX * acceleration;
                movedVz += directionZ * acceleration;
            } else {
                double acceleration = heldDirection == null
                    ? GRIM_AIR_COUNTER_IMPULSE * scale
                    : GRIM_AIR_COUNTER_IMPULSE * controlMagnitude;
                movedVx += directionX * acceleration;
                movedVz += directionZ * acceleration;
            }
            double previousX = x;
            double previousY = y;
            double previousZ = z;
            Vec3 intended = new Vec3(movedVx, vy, movedVz);
            Vec3 movement = collisionResolver == null || box == null
                ? intended : collisionResolver.resolve(box, intended);
            if (movement == null) movement = intended;
            x += movement.x;
            y += movement.y;
            z += movement.z;
            if (box != null) box = box.move(movement);

            boolean xCollision = !Mth.equal(intended.x, movement.x);
            boolean yCollision = !Mth.equal(intended.y, movement.y);
            boolean zCollision = !Mth.equal(intended.z, movement.z);
            if (xCollision) movedVx = 0.0D;
            if (zCollision) movedVz = 0.0D;
            if (yCollision) {

                if (intended.y < 0.0D) return new double[] {x, z};
                vy = 0.0D;
            }

            if (vy < 0.0D) descending = true;
            if (descending && previousY >= surfaceY && y <= surfaceY) {
                double drop = previousY - y;
                double t = drop <= 1.0E-12D ? 1.0D
                    : Mth.clamp((previousY - surfaceY) / drop, 0.0D, 1.0D);
                return new double[] {
                    previousX + movement.x * t,
                    previousZ + movement.z * t
                };
            }
            if (first) {
                vx = movedVx * GRIM_GROUND_TAKEOFF_DRAG;
                vz = movedVz * GRIM_GROUND_TAKEOFF_DRAG;
                first = false;
            } else {
                vx = movedVx * GRIM_LANE_AIR_DRAG;
                vz = movedVz * GRIM_LANE_AIR_DRAG;
            }
            vy = (vy - 0.08D) * 0.98D;
        }
        return null;
    }

    private double[] grimHeldArcLandingLive(
        Vec3 position, Vec3 velocity, double initialVy, double surfaceY,
        boolean grounded, int sneakedTicks
    ) {
        Vec3 lane = currentMovementLine == null ? null : currentMovementLine.direction();
        if (MC.player == null || MC.level == null) {
            return grimHeldArcLanding(position, velocity, initialVy, surfaceY,
                grounded, sneakedTicks, lane);
        }
        return grimHeldArcLanding(
            position, velocity, initialVy, surfaceY, grounded, sneakedTicks, lane,
            MC.player.getBoundingBox(),
            (box, movement) -> Entity.collideBoundingBox(
                MC.player, movement, box, MC.level,
                MC.level.getEntityCollisions(MC.player, box.expandTowards(movement))));
    }

    static boolean heldArcLandsOnColumn(Vec3 position, Vec3 velocity, double initialVy, BlockPos cell,
        boolean grounded) {
        if (cell == null) return false;
        double[] landing = grimHeldArcLanding(position, velocity, initialVy, cell.getY() + 1.0D, grounded);
        return landing != null && grimLandingOverlaps(landing, cell);
    }

    static boolean heldArcLandsOnColumn(Vec3 position, Vec3 velocity, BlockPos cell) {
        return heldArcLandsOnColumn(position, velocity, velocity == null ? 0.0D : velocity.y, cell, false);
    }

    private boolean grimArcLandsOnColumnLive(Vec3 position, Vec3 velocity, BlockPos cell) {
        if (cell == null) return false;
        double[] landing = grimHeldArcLandingLive(position, velocity,
            velocity == null ? 0.0D : velocity.y, cell.getY() + 1.0D,
            MC.player != null && MC.player.onGround(), grimArcSneakTicks());
        return landing != null && grimLandingOverlaps(landing, cell);
    }

    private int grimArcSneakTicks() {

        int latchedCurrent = MC.player != null && MC.player.isCrouching() ? 1 : 0;
        return Math.max(latchedCurrent, Math.max(0, grimSneakHoldTicks));
    }

    static boolean heldArcLandsOnColumnFromGround(Vec3 position, Vec3 velocity, BlockPos cell) {
        return heldArcLandsOnColumn(position, velocity, GRIM_JUMP_TAKEOFF_VELOCITY, cell, true);
    }

    static double[] grimHeldTakeoffLanding(Vec3 position, Vec3 velocity, double surfaceY) {
        return grimHeldArcLanding(position, velocity, GRIM_JUMP_TAKEOFF_VELOCITY, surfaceY, true);
    }

    static boolean grimLandingOverlaps(double[] landing, BlockPos cell) {
        if (landing == null || cell == null) return false;
        double overlapX = Math.min(landing[0] + GRIM_LANDING_HALF_WIDTH, cell.getX() + 1.0D)
            - Math.max(landing[0] - GRIM_LANDING_HALF_WIDTH, cell.getX());
        double overlapZ = Math.min(landing[1] + GRIM_LANDING_HALF_WIDTH, cell.getZ() + 1.0D)
            - Math.max(landing[1] - GRIM_LANDING_HALF_WIDTH, cell.getZ());
        return overlapX >= GRIM_LANDING_MIN_OVERLAP && overlapZ >= GRIM_LANDING_MIN_OVERLAP;
    }

    private boolean grimCarryArcActive() {
        return MC.player != null && grimCarryArcActive(MC.player.onGround(), grimJumpKeyHeld());
    }

    static boolean grimCarryArcActive(boolean onGround, boolean jumpHeld) {
        return jumpHeld && !onGround;
    }

    static boolean grimRequestedCourseChange(MovementLine previous, MovementLine requested) {
        return previous != null && requested != null
            && !sameGrimCourse(previous.direction(), requested.direction());
    }

    private Input tellyMovementInput(Input original) {
        if (!tellyOwnsInput || tellyMotion == TellyMotion.RELEASED) return original;

        if (tellyMotion == TellyMotion.HOLD) {

            TellyStrafe strafe = tellyHoldStrafe != TellyStrafe.NONE
                ? tellyHoldStrafe : tellyAirStrafeThisTick;
            tellyHoldStrafe = TellyStrafe.NONE;
            return new Input(false, false,
                strafe == TellyStrafe.LEFT, strafe == TellyStrafe.RIGHT,
                tellyJumpThisTick, tellySneakThisTick, false);
        }

        if (MC.player != null && MC.player.onGround()
            && usesTellyGroundWOnly(tellyPhase)) {

            return tellyGroundForwardInput(tellyJumpThisTick, tellySneakThisTick);
        }

        return tellyAirForwardInput(
            tellyAirStrafeThisTick, tellyJumpThisTick, tellySneakThisTick);
    }

    private TellyStrafe tellyHoldStrafe = TellyStrafe.NONE;

    private TellyStrafe tellyHoldLaneStrafe(LocalPlayer player) {
        if (player == null || tellyLastBridge == null) return TellyStrafe.NONE;
        double error = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);

        if (Math.abs(error) <= 0.15D || Math.abs(error) > 0.55D) return TellyStrafe.NONE;
        return error > 0 ? TellyStrafe.LEFT : TellyStrafe.RIGHT;
    }

    static Input tellyAirForwardInput(TellyStrafe strafe, boolean jump, boolean sneak) {
        TellyStrafe safe = strafe == null ? TellyStrafe.NONE : strafe;
        return new Input(true, false,
            safe == TellyStrafe.LEFT, safe == TellyStrafe.RIGHT,
            jump, sneak, true);
    }

    static TellyAirCorrectionState nextTellyAirCorrection(
        int cooldown, TellyStrafe lastPulse, int pulsesUsed,
        double laneError, double lateralVelocity, int ticksUntilLanding
    ) {
        int cooled = Math.max(0, cooldown - 1);
        int used = Math.max(0, pulsesUsed);
        TellyStrafe previous = lastPulse == null ? TellyStrafe.NONE : lastPulse;
        if (!Double.isFinite(laneError) || !Double.isFinite(lateralVelocity)) {
            return new TellyAirCorrectionState(TellyStrafe.NONE, cooled, previous, used);
        }

        double predictedError = tellyPredictedLaneError(
            laneError, lateralVelocity, ticksUntilLanding);
        double projectedDistance = Math.abs(predictedError);
        if (projectedDistance <= TELLY_AIR_LANE_EXIT
            && Math.abs(laneError) <= TELLY_AIR_LANE_ENTER) {
            previous = TellyStrafe.NONE;
        }
        if (projectedDistance <= TELLY_AIR_LANE_ENTER || cooldown > 0) {
            return new TellyAirCorrectionState(TellyStrafe.NONE, cooled, previous, used);
        }

        double desired = Mth.clamp(laneError * TELLY_LANE_VELOCITY_GAIN,
            -TELLY_LANE_MAX_VELOCITY, TELLY_LANE_MAX_VELOCITY);
        TellyStrafe requested = tellyLaneDamperArm(lateralVelocity, desired);
        if (requested == TellyStrafe.NONE) {
            return new TellyAirCorrectionState(TellyStrafe.NONE, cooled, previous, used);
        }
        boolean reversing = previous != TellyStrafe.NONE && requested != previous;
        if (reversing && projectedDistance < TELLY_AIR_LANE_EMERGENCY) {
            return new TellyAirCorrectionState(TellyStrafe.NONE, cooled, previous, used);
        }
        int pulseLimit = projectedDistance >= TELLY_AIR_LANE_EMERGENCY
            ? TELLY_AIR_EMERGENCY_PULSE_LIMIT : TELLY_AIR_ROUTINE_PULSE_LIMIT;
        if (used >= pulseLimit) {
            return new TellyAirCorrectionState(TellyStrafe.NONE, cooled, previous, used);
        }
        return new TellyAirCorrectionState(
            requested, TELLY_AIR_STRAFE_COOLDOWN_TICKS, requested, used + 1);
    }

    static TellyStrafe tellyLaneDamperArm(double lateralVelocity, double desiredVelocity) {
        if (!Double.isFinite(lateralVelocity) || !Double.isFinite(desiredVelocity)) {
            return TellyStrafe.NONE;
        }
        if (lateralVelocity < desiredVelocity - TELLY_LANE_TICK_AUTHORITY) return TellyStrafe.LEFT;
        if (lateralVelocity > desiredVelocity + TELLY_LANE_TICK_AUTHORITY) return TellyStrafe.RIGHT;
        return TellyStrafe.NONE;
    }

    static double tellyPredictedLaneError(
        double laneError, double lateralVelocity, int ticksUntilLanding
    ) {
        int ticks = Mth.clamp(ticksUntilLanding, 1, 14);
        double travel = 0.0D;
        double motion = lateralVelocity;
        for (int tick = 0; tick < ticks; tick++) {
            travel += motion;
            motion *= TELLY_AIR_DRAG;
        }
        return laneError - travel;
    }

    private void updateTellyAirCorrection(LocalPlayer player, double catchFeetY) {
        if (player == null || tellyLastBridge == null) return;
        Vec3 left = tellyLeftVector();
        double laneError = tellyLaneCenter - laneCoordinate(player.position(), tellyAnchorYaw);
        double lateralVelocity = player.getDeltaMovement().dot(left);
        int ticksUntilLanding = tellyTicksUntilCatch(
            player.position(), player.getDeltaMovement(), catchFeetY);
        TellyAirCorrectionState next = nextTellyAirCorrection(
            tellyAirStrafeCooldown, tellyAirLastStrafe, tellyAirStrafePulses,
            laneError, lateralVelocity, ticksUntilLanding);
        tellyAirStrafeThisTick = next.pulse();
        tellyAirStrafeCooldown = next.cooldown();
        tellyAirLastStrafe = next.lastPulse();
        tellyAirStrafePulses = next.pulsesUsed();
    }

    private void clearTellyAirCorrection() {
        tellyAirStrafeThisTick = TellyStrafe.NONE;
        tellyAirLastStrafe = TellyStrafe.NONE;
        tellyAirStrafeCooldown = 0;
        tellyAirStrafePulses = 0;
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
            if (!isTellySupport(MC.level.getBlockState(ahead), ahead)) {
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
        clearTellyLaneBias();
    }

    private void clearTellyLaneBias() {
        tellyLaneBias = 0.0F;
        grimLaneInputBias = 0.0F;
        grimInputDeltaYaw = Float.NaN;
    }

    public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
        if (entity == null || MC == null || entity != MC.player) return vanillaYaw;

        if (BuiltinModules.ownsManualFastExp()) return vanillaYaw;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.silentRotationApplies()) return vanillaYaw;
        AutismRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
        if (rotation == null || scaffold.grimRotationResetTicks <= 0) return vanillaYaw;

        return outgoingMovementYaw(MC.player, vanillaYaw);
    }

    private static float grimSentYaw = Float.NaN;
    private static int grimSentYawTick = Integer.MIN_VALUE;

    public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
        AutismRotationUtil.Rotation rotation = activeGrimRotation(player);
        int tick = AutismSharedState.get().getClientTickCounter();
        if (tick != grimSentYawTick) {
            grimSentYawTick = tick;
            float anchor = Float.isNaN(grimSentYaw) ? vanillaYaw : grimSentYaw;
            grimSentYaw = rotation == null ? vanillaYaw : grimContinuousYaw(anchor, rotation.yaw());
        }
        return grimSentYaw;
    }

    static float grimContinuousYaw(float previousSentYaw, float silentYaw) {
        return previousSentYaw + Mth.wrapDegrees(silentYaw - previousSentYaw);
    }

    public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = activeGrimRotation(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
        AutismRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
        if (rotation == null) return vanillaImpulse;

        float sent = MC != null && entity == MC.player
            ? outgoingMovementYaw(MC.player, rotation.yaw()) : rotation.yaw();
        float yaw = sent * Mth.DEG_TO_RAD;
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
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.silentRotationApplies()) return null;
        AutismRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
        return rotation == null || scaffold.grimRotationResetTicks <= 0 ? null : rotation;
    }

    private static AutismRotationUtil.Rotation activeGrimRotation(LocalPlayer player) {
        if (player == null || MC == null || player != MC.player) return null;

        if (BuiltinModules.ownsManualFastExp()) return null;
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || !scaffold.silentRotationApplies()
            || scaffold.grimRotationResetTicks <= 0) return null;
        return scaffold.grimSilentRotation;
    }

    private boolean grimRiseAllowed(boolean jumping) {

        boolean rising = grimRiseAllowed(jumping, MC.player == null || MC.player.onGround())
            && grimCourseAscends();
        grimTraceRiseAllowed = rising;
        return rising;
    }

    static boolean grimRiseAllowed(boolean jumpKeyHeld, boolean onGround) {
        return jumpKeyHeld || !onGround;
    }

    public static boolean hasActiveSilentMovementRotation() {
        return MC != null && activeGrimRotation(MC.player) != null;
    }

    @Override
    public boolean shouldCancelAttack(HitResult hitResult) {
        return hitResult instanceof net.minecraft.world.phys.EntityHitResult
            && hasActiveSilentMovementRotation();
    }

    public static AutismRotationUtil.Rotation activeOutgoingRotation() {
        return MC == null ? null : activeGrimRotation(MC.player);
    }

    public static AutismRotationUtil.Rotation wireContinuityRotation() {
        AutismRotationUtil.Rotation active = activeOutgoingRotation();
        if (active == null) return null;
        float yaw = Float.isNaN(grimSentYaw) ? active.yaw()
            : grimContinuousYaw(grimSentYaw, active.yaw());
        return new AutismRotationUtil.Rotation(yaw, active.pitch());
    }

    public static Vec3 silentViewVector(LocalPlayer player, Vec3 original) {
        Module module = ModuleRegistry.get("scaffold");
        if (!(module instanceof ScaffoldModule scaffold) || scaffold.isTellyMode()) return original;
        AutismRotationUtil.Rotation rotation = activeGrimRotation(player);
        return rotation == null ? original
            : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    private boolean isGrimMode() {
        return !isRageMode() && !isTellyMode();
    }

    private boolean isRageMode() {
        String mode = choice("mode");
        return "Rage".equals(mode) || "Fast".equals(mode);
    }

    private boolean isTellyMode() {
        return "Telly".equals(choice("mode"));
    }

    private boolean isGrimFamily() {
        return isGrimMode();
    }

    private boolean tellyUsesRealClicks() {
        return isTellyMode() && tellyOwnsInput;
    }

    private boolean ownsRealClickPipeline() {
        return isGrimFamily() || tellyUsesRealClicks();
    }

    private boolean grimRemoveLimits() {
        return isGrimFamily() && bool("remove-limits");
    }

    private boolean usesSilentRotationPath() {
        return isGrimFamily() || (isTellyMode() && tellyOwnsInput);
    }

    private boolean silentRotationApplies() {
        return silentCorrectionApplies(grimWindingDown, isEnabled(), usesSilentRotationPath(), canRun());
    }

    static boolean silentCorrectionApplies(boolean windingDown, boolean enabled, boolean silentPath,
                                           boolean canRun) {
        return windingDown || enabled && silentPath && canRun;
    }

    static boolean silentCorrectionOwnsInput(boolean enabled, boolean canRun) {
        return enabled && canRun;
    }

    static boolean sameGrimCourse(Vec3 previous, Vec3 current) {
        if (previous == null || current == null
            || previous.horizontalDistanceSqr() <= 1.0E-12D
            || current.horizontalDistanceSqr() <= 1.0E-12D) return false;
        Vec3 first = new Vec3(previous.x, 0.0D, previous.z).normalize();
        Vec3 second = new Vec3(current.x, 0.0D, current.z).normalize();
        return first.dot(second) >= GRIM_COURSE_DOT_EPSILON;
    }

    private static double courseSignedAngleDegrees(Vec3 from, Vec3 to) {
        double cross = from.x * to.z - from.z * to.x;
        double dot = from.x * to.x + from.z * to.z;
        return Math.toDegrees(Math.atan2(cross, dot));
    }

    private boolean grimBridgingContext(Vec3 position, MovementLine line) {
        if (grimActiveRowLock() != null) return true;
        int since = AutismSharedState.get().getClientTickCounter() - lastGrimPlacementTick;
        if (since >= 0 && since <= GRIM_BRIDGING_RECENT_TICKS) return true;
        Vec3 heading = grimBridgingHeading(line);
        return heading != null && grimVoidAhead(position, heading, 1.0D);
    }

    private Vec3 grimBridgingHeading(MovementLine line) {
        Vec3 velocity = MC.player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontal.length() >= COURSE_STABILIZER_MIN_SPEED) return horizontal.normalize();
        if (line == null || line.direction().horizontalDistanceSqr() <= 1.0E-8D) return null;
        Vec3 direction = line.direction();
        return new Vec3(direction.x, 0.0D, direction.z).normalize();
    }

    private boolean grimLateralBrink() {
        if (MC.player == null || MC.level == null || !MC.player.onGround()) return false;

        if (grimNoFootingUnderfoot()) return true;
        if (grimWalkOffNextTick()) return true;
        if (grimCourseDiverged()) return true;
        MovementLine line = currentMovementLine;
        if (line == null || line.direction().horizontalDistanceSqr() <= 1.0E-8D) return false;
        Vec3 position = MC.player.position();
        if (Math.abs(grimLaneError(line, position)) <= GRIM_LATERAL_BRINK_OFFSET) return false;
        Vec3 probe = grimLateralDriftProbe(line, position, GRIM_LATERAL_BRINK_PROBE);
        return !hasSupportBelow(probe.x, position.y, probe.z, 1);
    }

    private boolean grimWalkOffNextTick() {
        if (MC.player == null || MC.level == null || !MC.player.onGround()) return false;
        Vec3 velocity = MC.player.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() <= 1.0E-8D) return false;
        Vec3 next = grimNextStepPosition(MC.player.position(), velocity);
        return grimNoFootingUnderfoot(grimFootingOverlap(next));
    }

    static Vec3 grimNextStepPosition(Vec3 position, Vec3 velocity) {
        if (position == null || velocity == null) return position;
        return position.add(velocity.x, 0.0D, velocity.z);
    }

    static Input grimCounterMovement(Input input) {
        if (input == null) return null;
        return new Input(input.backward(), input.forward(), input.right(), input.left(),
            input.jump(), input.shift(), false);
    }

    static double grimScoredGapMean(long[] gaps, long sinceLastMs, int window) {
        if (gaps == null || gaps.length == 0 || window <= 0 || sinceLastMs < 0L) {
            return Double.NaN;
        }
        long[] clamped = new long[gaps.length];
        for (int i = 0; i < gaps.length; i++) {
            clamped[i] = Math.min(GRIM_INTAVE_PLACE_SAMPLE_CAP_MS, Math.max(0L, gaps[i]));
        }
        return grimPaceProspectiveMean(clamped,
            Math.min(GRIM_INTAVE_PLACE_SAMPLE_CAP_MS, sinceLastMs), window);
    }

    static boolean grimDiagonalClimbBrakeApplies(
        boolean grimFamily, boolean diagonalLane, boolean ascending, boolean directionalInput,
        boolean airborne, boolean rising, double laneSpeed, double scoredMeanMs, long sinceLastMs
    ) {
        if (!grimFamily || !diagonalLane || !ascending || !directionalInput || !airborne) {
            return false;
        }

        if (!rising) return false;

        if (laneSpeed < GRIM_ARC_BRAKE_MIN_LANE_SPEED) return false;
        if (sinceLastMs < 0L || sinceLastMs >= GRIM_INTAVE_PLACE_SAMPLE_CAP_MS) return false;
        if (Double.isNaN(scoredMeanMs)) return false;
        return scoredMeanMs < GRIM_INTAVE_PLACE_MEAN_MS * GRIM_PACE_SAFETY;
    }

    private boolean grimDiagonalClimbBrake(boolean directionalInput, boolean traced) {
        if (traced) grimTraceDiagonalPaceMean = Double.NaN;
        if (!isGrimFamily() || MC.player == null) return false;
        Vec3 lane = currentMovementLine == null ? null : currentMovementLine.direction();
        if (!isGrimDiagonalDirection(lane)) return false;
        long[] gaps = new long[grimIntavePlaceGaps.size()];
        int index = 0;
        for (long gap : grimIntavePlaceGaps) gaps[index++] = gap;
        long sinceLast = grimIntavePlaceGap(System.nanoTime());
        double mean = grimScoredGapMean(gaps, sinceLast, GRIM_PACE_SAMPLES);
        if (traced) grimTraceDiagonalPaceMean = mean;

        if (grimRemoveLimits()) return false;
        Vec3 velocity = MC.player.getDeltaMovement();
        double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
        double laneSpeed = length < 1.0E-6D
            ? 0.0D : (velocity.x * lane.x + velocity.z * lane.z) / length;
        return grimDiagonalClimbBrakeApplies(true, true, grimCourseAscends(), directionalInput,
            !MC.player.onGround(), velocity.y > 0.0D, laneSpeed, mean, sinceLast);
    }

    private double grimLandingCarry(Vec3 landing, int row) {
        if (landing == null || MC.level == null) return Double.NaN;
        double[] point = { landing.x, landing.z };
        double best = Double.NaN;
        int minX = Mth.floor(landing.x - GRIM_LANDING_HALF_WIDTH);
        int maxX = Mth.floor(landing.x + GRIM_LANDING_HALF_WIDTH);
        int minZ = Mth.floor(landing.z - GRIM_LANDING_HALF_WIDTH);
        int maxZ = Mth.floor(landing.z + GRIM_LANDING_HALF_WIDTH);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos cell = new BlockPos(x, row, z);
                if (MC.level.isOutsideBuildHeight(cell)) continue;
                if (!grimLandingRowCellHolds(cell)) continue;
                double overlap = grimLandingMinOverlap(point, cell);
                if (Double.isNaN(best) || overlap > best) best = overlap;
            }
        }
        return best;
    }

    private boolean grimLandingRowCellHolds(BlockPos cell) {
        BlockState state = MC.level.getBlockState(cell);
        if (isSolidSupport(state, cell)) return true;
        if (!state.canBeReplaced()) return false;
        BlockPos below = cell.below();
        return !MC.level.isOutsideBuildHeight(below)
            && isSolidSupport(MC.level.getBlockState(below), below);
    }

    private boolean grimArcBrake(boolean directionalInput, boolean traced) {

        if (traced) {
            grimTraceArcTravel = 0.0D;
            grimTraceArcCarry = "--";
            grimTraceArcStand = "--";
        }

        if (grimRemoveLimits()) {
            if (traced) grimTraceArcStand = "off";
            return false;
        }
        if (!isGrimFamily() || MC.player == null || MC.player.onGround()) {
            if (traced) grimTraceArcStand = "nogrim";
            return false;
        }
        if (!directionalInput || !grimCourseAscends()) {
            if (traced) grimTraceArcStand = "noasc";
            return false;
        }
        BlockPos support = grimLaunchReservedSupport;
        if (support == null) {
            if (traced) grimTraceArcStand = "nosup";
            return false;
        }
        Vec3 velocity = MC.player.getDeltaMovement();

        MovementLine line = currentMovementLine;
        Vec3 lane = line == null ? null : line.direction();
        if (lane != null) {
            double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
            if (length > 1.0E-6D
                && (velocity.x * lane.x + velocity.z * lane.z) / length
                    < GRIM_ARC_BRAKE_MIN_LANE_SPEED) {
                if (traced) grimTraceArcStand = "speed";
                return false;
            }
        }
        Vec3 landing = grimDescentCrossing(MC.player.position(), velocity,
            support.getY() + 2.0D, GRIM_ARC_BRAKE_LOOKAHEAD_TICKS, GRIM_AIR_COUNTER_IMPULSE);
        if (landing == null) {
            if (traced) grimTraceArcStand = "noland";
            return false;
        }
        Vec3 start = grimArcTravelStart;

        if (traced) {
            grimTraceArcTravel = start == null
                ? 0.0D : Math.hypot(landing.x - start.x, landing.z - start.z);
        }
        double carry = grimLandingCarry(landing, support.getY() + 1);

        if (traced) {
            grimTraceArcCarry = Double.isNaN(carry)
                ? "bare" : String.format(java.util.Locale.ROOT, "%+.2f", carry);
        }

        if (carry >= GRIM_LANDING_MIN_OVERLAP) return false;

        if (grimCrossingStanddown(velocity.y > 0.0D, lane)) {
            if (traced) grimTraceArcStand = "xing";
            return false;
        }
        return true;
    }

    private boolean grimNoFootingUnderfoot() {
        if (MC.player == null || MC.level == null || !MC.player.onGround()) return false;

        return grimNoFootingUnderfoot(grimFootingOverlap(MC.player.position()));
    }

    static boolean grimNoFootingUnderfoot(double overlap) {
        return overlap <= 0.0D;
    }

    private double grimFootingOverlap(Vec3 point) {
        return grimFootingOverlap(point, Mth.floor(point.y) - 1);
    }

    private double grimFootingOverlap(Vec3 point, int y) {
        double half = MC.player.getBbWidth() * 0.5D;
        double minX = point.x - half, maxX = point.x + half;
        double minZ = point.z - half, maxZ = point.z + half;
        double best = 0.0D;
        for (int cx = Mth.floor(minX); cx <= Mth.floor(maxX); cx++) {
            for (int cz = Mth.floor(minZ); cz <= Mth.floor(maxZ); cz++) {
                BlockPos pos = new BlockPos(cx, y, cz);
                if (MC.level.isOutsideBuildHeight(pos)) continue;
                if (!isSolidSupport(MC.level.getBlockState(pos), pos)) continue;
                double overlapX = Math.min(maxX, cx + 1.0D) - Math.max(minX, cx);
                double overlapZ = Math.min(maxZ, cz + 1.0D) - Math.max(minZ, cz);
                best = Math.max(best, Math.min(overlapX, overlapZ));
            }
        }
        return best;
    }

    private boolean grimFootingOwed() {
        if (MC.player == null || MC.level == null || !MC.player.onGround()) return false;
        BlockPos footing = grimEffectiveFootCell().below();
        if (!MC.level.getBlockState(footing).canBeReplaced()) return false;
        Vec3 position = MC.player.position();
        Vec3 lead = grimLeadStep().scale(grimWalkLeadTicks(grimPaceWaitTicks));
        return grimFootingOwed(
            grimFootingOverlap(position), grimFootingOverlap(position.add(lead)));
    }

    static boolean grimFootingOwed(double overlap, double leadOverlap) {
        return overlap <= GRIM_FOOTING_OWED_OVERLAP && leadOverlap <= overlap;
    }

    private int grimPaceWaitTicks;

    static int grimWalkLeadTicks(int paceWaitTicks) {
        return Mth.clamp(1 + paceWaitTicks, 1, GRIM_WALK_LEAD_MAX_TICKS);
    }

    static final int GRIM_WALK_LEAD_MAX_TICKS = 4;

    private boolean grimRecentlyRescued() {
        int since = AutismSharedState.get().getClientTickCounter() - grimLastRescueTick;
        return since >= 0 && since <= 6;
    }

    private boolean grimCourseDiverged() {
        MovementLine line = currentMovementLine;
        if (line == null || line.direction().horizontalDistanceSqr() <= 1.0E-8D) return false;
        Vec3 velocity = MC.player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontal.length() < COURSE_STABILIZER_MIN_SPEED) return false;
        if (Math.abs(courseSignedAngleDegrees(horizontal, line.direction()))
            <= GRIM_COURSE_DIVERGENCE_DEGREES) {
            return false;
        }
        return grimBridgingContext(MC.player.position(), line);
    }

    static double grimLaneError(MovementLine line, Vec3 position) {
        Vec3 direction = line.direction().normalize();
        Vec3 left = new Vec3(direction.z, 0.0D, -direction.x);
        return nearestPointOnLine(line, position).subtract(position).dot(left);
    }

    static Vec3 grimLateralDriftProbe(MovementLine line, Vec3 position, double distance) {
        Vec3 direction = line.direction().normalize();
        Vec3 left = new Vec3(direction.z, 0.0D, -direction.x);
        double error = grimLaneError(line, position);
        return position.add(left.scale(error > 0.0D ? -distance : distance));
    }

    private boolean grimVoidAhead(Vec3 position, Vec3 horizontal, double speed) {
        Vec3 step = horizontal.scale(1.0D / speed);
        for (double distance = 0.5D; distance <= 2.5D; distance += 0.5D) {
            BlockPos feet = BlockPos.containing(position.add(step.scale(distance)));
            boolean supported = isSolidSupport(MC.level.getBlockState(feet), feet)
                || isSolidSupport(MC.level.getBlockState(feet.below()), feet.below())
                || isSolidSupport(MC.level.getBlockState(feet.below(2)), feet.below(2));
            if (!supported) return true;
        }
        return false;
    }

    private Input transformSilentMovementInput(Input input) {

        boolean tellyWindDown = grimWindingDown && AutismHumanRotation.isInitialized(tellyStream);
        if ((!usesSilentRotationPath() && !tellyWindDown) || grimSilentRotation == null
            || grimRotationResetTicks <= 0) return input;
        if (isTellyMode()) {
            return transformSilentMovementInputStable(
                input, MC.player.getYRot(), grimSilentRotation.yaw());
        }
        Input result = transformSilentMovementInput(
            input, MC.player.getYRot(), grimSilentRotation.yaw());

        if (result != null && !result.forward() && hasDirectionalInput(input)) {
            grimSprintNoForwardTick = AutismSharedState.get().getClientTickCounter();
        }
        return result;
    }

    static float grimInputWorldYaw(float forward, float sideways, float referenceYaw) {
        return Mth.wrapDegrees(
            referenceYaw - (float) Math.toDegrees(Math.atan2(sideways, forward)));
    }

    static boolean grimInputSteering(Input physical) {
        return physical != null && physical.left() != physical.right();
    }

    static float grimSnapYawToLane(float laneYaw, float freeYaw) {
        float off = Mth.wrapDegrees(freeYaw - laneYaw);
        return Mth.wrapDegrees(laneYaw + Math.round(off / 45.0F) * 45.0F);
    }

    static float grimLaneOctantResidual(float laneYaw, float emittedYaw) {
        float off = Mth.wrapDegrees(emittedYaw - laneYaw);
        return Mth.wrapDegrees(off - Math.round(off / 45.0F) * 45.0F);
    }

    static boolean grimLaneAnchorStandsDown(float bias) {
        return Math.abs(bias) > GRIM_STEP_HOLD_DEGREES;
    }

    private float grimLaneCentringBias() {
        MovementLine line = currentMovementLine;
        if (line == null || MC.player == null) {
            grimLaneCorrectStandDown();
            return 0.0F;
        }
        Vec3 direction = line.direction();
        if (direction.horizontalDistanceSqr() <= 1.0E-8D) {
            grimLaneCorrectStandDown();
            return 0.0F;
        }
        Vec3 unit = direction.normalize();
        Vec3 left = new Vec3(unit.z, 0.0D, -unit.x);
        double offset = grimLaneError(line, MC.player.position());
        int previous = grimLaneCorrectSide;
        grimLaneCorrectSide = grimLaneCorrectLatch(previous, grimLaneCorrectHoldTicks,
            grimLaneCorrectLockTicks, offset, MC.player.getDeltaMovement().dot(left));
        if (grimLaneCorrectSide == 0) {
            if (previous != 0) {
                grimLaneCorrectHoldTicks = 0;
                grimLaneCorrectLockTicks = GRIM_LANE_CORRECT_RELOCK_TICKS;
            } else {
                grimLaneCorrectStandDown();
            }
            return 0.0F;
        }
        grimLaneCorrectHoldTicks++;
        grimLaneCorrectLockTicks = 0;

        double demand = Math.max(Math.abs(offset), GRIM_LANE_CORRECT_RELEASE) * grimLaneCorrectSide;
        return grimLaneInputTarget(unit, left.scale(demand), Vec3.ZERO,
            0.0D, GRIM_LANE_INPUT_LOOKAHEAD_LEGIT);
    }

    private int grimLaneCorrectSide;

    private int grimLaneCorrectHoldTicks;

    private int grimLaneCorrectLockTicks;

    private void grimLaneCorrectStandDown() {
        grimLaneCorrectSide = 0;
        grimLaneCorrectHoldTicks = 0;
        if (grimLaneCorrectLockTicks > 0) grimLaneCorrectLockTicks--;
    }

    static int grimLaneCorrectLatch(
        int side, int heldTicks, int lockTicks, double offset, double perpendicularVelocity) {
        double predicted = offset - perpendicularVelocity * GRIM_LANE_INPUT_PREDICT_TICKS;
        if (side != 0) {
            if (Math.abs(offset) <= GRIM_LANE_CORRECT_RELEASE) return 0;
            if (heldTicks >= GRIM_LANE_CORRECT_MAX_HOLD_TICKS) return 0;

            double settles = offset - perpendicularVelocity * GRIM_LANE_CORRECT_SETTLE_LEAD;
            return side * settles <= GRIM_LANE_CORRECT_RELEASE ? 0 : side;
        }
        if (lockTicks > 0) return 0;
        if (Math.abs(predicted) < GRIM_LANE_CORRECT_ENGAGE) return 0;

        if (Math.abs(offset) <= GRIM_LANE_CORRECT_RELEASE) return 0;
        return offset > 0.0D ? 1 : -1;
    }

    private Input transformGrimLegitInput(Input input, Input physical) {
        if (!usesSilentRotationPath() || grimSilentRotation == null || grimRotationResetTicks <= 0) return input;
        float forward = inputImpulse(input.forward(), input.backward());
        float sideways = inputImpulse(input.left(), input.right());
        float emitted = grimSilentRotation.yaw();
        grimTraceLaneAnchor = "cam";
        grimLaneInputBias = 0.0F;
        grimLaneSweepActive = false;

        if (grimInputSteering(physical)) {
            grimTraceLaneAnchor = "steer";
            grimLaneCoastTicks = 0;
            grimLaneCorrectStandDown();
        } else if (forward == 0.0F && sideways == 0.0F) {
            grimLaneCoastTicks = 0;
            grimLaneCorrectStandDown();
        } else if (grimLaneStep() != COURSE_STEP_UNSET) {
            float laneYaw = grimLaneStepYaw();
            float bias = Mth.wrapDegrees(
                laneYaw - grimInputWorldYaw(forward, sideways, MC.player.getYRot()));
            if (grimLaneAnchorStandsDown(bias)) {

                grimLaneInputBias = Mth.clamp(grimLaneCentringBias(),
                    -GRIM_LANE_INPUT_MAX_DEGREES, GRIM_LANE_INPUT_MAX_DEGREES);
                grimLaneCoastTicks = 0;
            } else {

                grimLaneInputBias = bias + Mth.clamp(grimLaneCentringBias(),
                    -GRIM_LANE_INPUT_MAX_DEGREES, GRIM_LANE_INPUT_MAX_DEGREES);
                grimTraceLaneAnchor = "lane";

                float residual = Math.abs(grimLaneOctantResidual(laneYaw, emitted));
                if (residual > GRIM_LANE_SWEEP_SETTLED_DEGREES) {
                    grimLaneSweepActive = ++grimLaneSweepTicks <= GRIM_LANE_SWEEP_MAX_TICKS;
                } else {
                    grimLaneSweepTicks = 0;
                }
                if (residual > GRIM_LANE_OCTANT_MAX_RESIDUAL) {

                    if (++grimLaneCoastTicks <= GRIM_LANE_COAST_MAX_TICKS) {
                        Input coast = grimStripLateral(grimCoastInput(input));
                        if (coast != null) {
                            grimTraceLaneAnchor = grimLaneSweepActive ? "sweep" : "coast";
                            if (!coast.forward()) {
                                grimSprintNoForwardTick =
                                    AutismSharedState.get().getClientTickCounter();
                            }
                            return coast;
                        }
                    }
                } else {
                    grimLaneCoastTicks = 0;
                }
                if (grimLaneSweepActive) grimTraceLaneAnchor = "sweep";
            }
        }

        Input result = transformSilentMovementInputStable(input, MC.player.getYRot(), emitted);

        if (grimLaneSweepActive) result = grimStripLateral(result);
        if (result != null && !result.forward() && hasDirectionalInput(input)) {
            grimSprintNoForwardTick = AutismSharedState.get().getClientTickCounter();
        }
        return result;
    }

    static Input grimStripLateral(Input input) {
        if (input == null || input.left() == input.right()) return input;
        return new Input(input.forward(), input.backward(), false, false,
            input.jump(), input.shift(), silentSprintAllowed(input, input.forward()));
    }

    static boolean silentSprintAllowed(Input input, boolean emittedForward) {
        return input != null && input.sprint() && emittedForward;
    }

    private boolean grimLaneSweepActive;

    private int grimLaneSweepTicks;

    private Input grimCoastInput(Input input) {
        if (grimInputForwardOctant == 0 && grimInputSidewaysOctant == 0) return null;
        return new Input(
            grimInputForwardOctant > 0, grimInputForwardOctant < 0,
            grimInputSidewaysOctant > 0, grimInputSidewaysOctant < 0,
            input.jump(), input.shift(),
            silentSprintAllowed(input, grimInputForwardOctant > 0));
    }

    public static boolean blocksSprintWithoutForward() {
        Module module = ModuleRegistry.get("scaffold");
        return module instanceof ScaffoldModule scaffold
            && scaffold.grimSprintNoForwardTick == AutismSharedState.get().getClientTickCounter();
    }

    private void updateTellyLaneBias(float courseYaw) {
        tellyLaneBias = approachTellyLaneBias(tellyLaneBias, tellyLaneBiasTarget(courseYaw));
        grimLaneInputBias = tellyLaneBias;
    }

    private float tellyLaneBiasTarget(float courseYaw) {
        if (MC.player == null || tellyLastBridge == null || Float.isNaN(tellyAnchorYaw)) return 0.0F;
        double error = tellyLaneCenter - laneCoordinate(MC.player.position(), tellyAnchorYaw);

        if (!Double.isFinite(error) || Math.abs(error) > 0.55D) return 0.0F;
        Vec3 forward = tellyForwardVector();
        if (forward.horizontalDistanceSqr() <= 1.0E-8D) return 0.0F;
        Vec3 left = tellyLeftVector();
        return grimLaneInputTarget(
            forward.normalize(), left.scale(error), MC.player.getDeltaMovement());
    }

    static float approachTellyLaneBias(float current, float target) {
        float wanted = Mth.clamp(target, -TELLY_LANE_BIAS_MAX, TELLY_LANE_BIAS_MAX);
        float step = Mth.clamp(wanted - current, -TELLY_LANE_BIAS_SLEW, TELLY_LANE_BIAS_SLEW);
        return Mth.clamp(current + step, -TELLY_LANE_BIAS_MAX, TELLY_LANE_BIAS_MAX);
    }

    private Input transformTellyAuthoredInput(Input input) {
        if (!usesSilentRotationPath() || grimSilentRotation == null || grimRotationResetTicks <= 0) return input;

        float courseYaw = tellyGroundSteeringActive
            ? Mth.wrapDegrees(tellyAnchorYaw + tellyGroundSteerOffset)
            : tellyAnchorYaw;
        updateTellyLaneBias(courseYaw);
        return transformSilentMovementInputStable(input, courseYaw, grimSilentRotation.yaw());
    }

    public static Input transformSilentMovementInput(Input input, float playerYaw, float silentYaw) {
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
            input.jump(), input.shift(), silentSprintAllowed(input, roundedForward > 0));
    }

    private Input transformSilentMovementInputStable(Input input, float playerYaw, float silentYaw) {
        if (input == null) return null;
        float forward = inputImpulse(input.forward(), input.backward());
        float sideways = inputImpulse(input.left(), input.right());
        if (forward == 0.0F && sideways == 0.0F) {
            grimInputForwardOctant = 0;
            grimInputSidewaysOctant = 0;
            grimInputSidewaysHold = 0;
            grimInputSidewaysFromCorrection = false;
            grimInputRawForward = 0.0F;
            grimInputRawSideways = 0.0F;
            grimInputDeltaYaw = Float.NaN;
            return input;
        }

        float deltaDegrees = Mth.wrapDegrees(playerYaw + grimLaneInputBias - silentYaw);

        boolean referenceMoved = Float.isFinite(grimInputDeltaYaw)
            && Math.abs(Mth.wrapDegrees(deltaDegrees - grimInputDeltaYaw))
                >= GRIM_INPUT_REFERENCE_BREAK_DEGREES;
        grimInputDeltaYaw = deltaDegrees;
        float deltaYaw = deltaDegrees * Mth.DEG_TO_RAD;
        float cosine = Mth.cos(deltaYaw);
        float sine = Mth.sin(deltaYaw);
        float transformedSideways = sideways * cosine - forward * sine;
        float transformedForward = forward * cosine + sideways * sine;

        int steadyForward = octantWithHysteresis(transformedForward, grimInputForwardOctant);

        int steadySideways = dwellSideways(
            Math.round(transformedSideways), forward, sideways, referenceMoved);
        if (steadyForward == 0 && steadySideways == 0) {

            steadyForward = Math.round(transformedForward);
            steadySideways = Math.round(transformedSideways);
        }
        grimInputForwardOctant = steadyForward;
        grimInputSidewaysOctant = steadySideways;

        return new Input(
            steadyForward > 0,
            steadyForward < 0,
            steadySideways > 0,
            steadySideways < 0,
            input.jump(), input.shift(), silentSprintAllowed(input, steadyForward > 0));
    }

    private int dwellSideways(
        int requested, float rawForward, float rawSideways, boolean referenceMoved) {
        boolean playerChangedKeys = rawForward != grimInputRawForward || rawSideways != grimInputRawSideways;
        grimInputRawForward = rawForward;
        grimInputRawSideways = rawSideways;

        if (grimWindingDown) {
            grimInputSidewaysHold = 0;
            grimInputSidewaysFromCorrection = false;
            return requested;
        }
        if (requested == grimInputSidewaysOctant) {
            grimInputSidewaysHold++;
            if (grimLaneCorrectSide != 0 && requested != 0) grimInputSidewaysFromCorrection = true;
            return requested;
        }
        if (dwellReleases(playerChangedKeys,
            grimInputSidewaysFromCorrection && grimLaneCorrectSide == 0,
            grimInputSidewaysHold, grimLaneInputBias, referenceMoved)) {
            grimInputSidewaysHold = 0;
            grimInputSidewaysFromCorrection = grimLaneCorrectSide != 0 && requested != 0;
            return requested;
        }
        grimInputSidewaysHold++;
        return grimInputSidewaysOctant;
    }

    static boolean dwellReleases(
        boolean playerChangedKeys, boolean correctionEnded, int hold, float bias,
        boolean referenceMoved) {
        return playerChangedKeys
            || correctionEnded
            || referenceMoved
            || hold >= GRIM_INPUT_SIDEWAYS_MIN_HOLD
            || Math.abs(bias) >= GRIM_INPUT_SIDEWAYS_BREAK_DEGREES;
    }

    static int octantWithHysteresis(double component, int previous) {
        double magnitude = Math.abs(component);
        double threshold = previous == 0 ? GRIM_INPUT_OCTANT_ENTER : GRIM_INPUT_OCTANT_EXIT;
        if (magnitude < threshold) return 0;
        return component < 0.0D ? -1 : 1;
    }

    static double emittedOctantDegrees(int forwardImpulse, int sidewaysImpulse) {
        if (forwardImpulse == 0 && sidewaysImpulse == 0) return Double.NaN;
        return Math.toDegrees(Math.atan2(-sidewaysImpulse, forwardImpulse));
    }

    private static final double[] TELLY_STRAFE_FLIP_BOUNDARIES = {30.0D, 150.0D, -150.0D, -30.0D};

    static double tellyStrafeFlipMargin(double deltaDegrees) {
        if (!Double.isFinite(deltaDegrees)) return Double.NaN;
        double best = Double.MAX_VALUE;
        for (double boundary : TELLY_STRAFE_FLIP_BOUNDARIES) {
            best = Math.min(best, Math.abs(Mth.wrapDegrees(deltaDegrees - boundary)));
        }
        return best;
    }

    private void resetGrimInputOctant() {
        grimInputForwardOctant = 0;
        grimInputSidewaysOctant = 0;
        grimInputSidewaysHold = 0;
        grimInputSidewaysFromCorrection = false;
        grimInputRawForward = 0.0F;
        grimInputRawSideways = 0.0F;
        grimLaneInputBias = 0.0F;
        grimLaneCoastTicks = 0;
        grimLaneSweepTicks = 0;
        grimLaneCorrectStandDown();
        grimLaneCorrectLockTicks = 0;
    }

    static float grimLaneInputTarget(Vec3 laneDirection, Vec3 laneOffset, Vec3 horizontalVel) {
        return grimLaneInputTarget(laneDirection, laneOffset, horizontalVel,
            GRIM_LANE_INPUT_DEADBAND, GRIM_LANE_INPUT_LOOKAHEAD);
    }

    static float grimLaneInputTarget(Vec3 laneDirection, Vec3 laneOffset, Vec3 horizontalVel,
        double deadband, double lookahead) {
        Vec3 left = new Vec3(laneDirection.z, 0.0D, -laneDirection.x);
        double error = laneOffset.dot(left) - horizontalVel.dot(left) * GRIM_LANE_INPUT_PREDICT_TICKS;
        if (Math.abs(error) < deadband) return 0.0F;
        Vec3 desired = laneDirection.scale(lookahead).add(left.scale(error));
        if (desired.lengthSqr() <= 1.0E-12D) return 0.0F;
        return (float) Mth.clamp(courseSignedAngleDegrees(laneDirection, desired),
            -GRIM_LANE_INPUT_MAX_DEGREES, GRIM_LANE_INPUT_MAX_DEGREES);
    }

    private static float inputImpulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    private GrimRowLock grimActiveRowLock() {
        if (MC.player == null || currentMovementLine == null) return null;
        Vec3 direction = currentMovementLine.direction();
        if (isGrimDiagonalDirection(direction)) return null;
        int stepX = horizontalStep(direction.x);
        int stepZ = horizontalStep(direction.z);
        if (stepX == 0 && stepZ == 0) return null;
        int since = AutismSharedState.get().getClientTickCounter() - lastGrimPlacementTick;
        if (since < 0 || since > GRIM_ROW_LOCK_TICKS) return null;
        BlockPos lastPlaced = lastPlacedBlocks.peekLast();
        if (lastPlaced == null) return null;
        if (!isSolidSupport(MC.level.getBlockState(lastPlaced), lastPlaced)) return null;
        int rowY = grimLockRowFor(lastPlaced.getY(), grimOracleFootingRow());

        if (BlockPos.containing(MC.player.position()).getY() - 1 < rowY) return null;
        boolean xAxis = stepX != 0;
        BlockPos playerCell = BlockPos.containing(MC.player.position());
        return new GrimRowLock(
            xAxis,
            xAxis ? lastPlaced.getZ() : lastPlaced.getX(),
            rowY,
            xAxis ? lastPlaced.getX() : lastPlaced.getZ(),
            grimRowFrontierIndex(lastPlaced, xAxis, -1),
            grimRowFrontierIndex(lastPlaced, xAxis, 1),
            xAxis ? playerCell.getZ() : playerCell.getX());
    }

    static int grimLockRowFor(int placedRow, int footingRow) {
        return footingRow == placedRow + 1 ? footingRow : placedRow;
    }

    private int grimRowFrontierIndex(BlockPos lastPlaced, boolean xAxis, int sign) {
        for (int step = 1; step <= 12; step++) {
            BlockPos pos = xAxis
                ? lastPlaced.offset(sign * step, 0, 0)
                : lastPlaced.offset(0, 0, sign * step);
            if (MC.level.isOutsideBuildHeight(pos)) return Integer.MIN_VALUE;
            if (!isSolidSupport(MC.level.getBlockState(pos), pos)) {
                return xAxis ? pos.getX() : pos.getZ();
            }
        }
        return Integer.MIN_VALUE;
    }

    record GrimRowLock(
        boolean xAxis, int rowPerp, int rowY, int lastIndex, int frontNeg, int frontPos,
        int playerPerp
    ) {
        boolean allows(BlockPos pos, boolean rising) {
            int perp = xAxis ? pos.getZ() : pos.getX();
            int index = xAxis ? pos.getX() : pos.getZ();
            boolean onRow = perp == rowPerp;
            boolean ownColumn = perp == playerPerp;
            if (!onRow && !ownColumn) return false;
            if (pos.getY() == rowY) return ownColumn || index == frontNeg || index == frontPos;
            return rising && pos.getY() == rowY + 1
                && (ownColumn || index == lastIndex || index == frontNeg || index == frontPos);
        }

        Direction pinnedFace(BlockPos pos) {
            if (pos.getY() != rowY) return null;
            int perp = xAxis ? pos.getZ() : pos.getX();
            if (perp != rowPerp) return null;
            int index = xAxis ? pos.getX() : pos.getZ();
            if (xAxis) return index > lastIndex ? Direction.EAST : Direction.WEST;
            return index > lastIndex ? Direction.SOUTH : Direction.NORTH;
        }
    }

    static Vec3 grimDescentCrossing(Vec3 position, Vec3 velocity, double feetY, int maxTicks) {
        return grimDescentCrossing(position, velocity, feetY, maxTicks, 0.0D);
    }

    static Vec3 grimDescentCrossing(
        Vec3 position, Vec3 velocity, double feetY, int maxTicks, double impulse) {
        double x = position.x, y = position.y, z = position.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        for (int tick = 0; tick < maxTicks; tick++) {
            double previousY = y;
            x += vx;
            y += vy;
            z += vz;
            if (vy < 0.0D && previousY >= feetY && y < feetY) {
                double t = (previousY - feetY) / (previousY - y);
                return new Vec3(x - vx + vx * t, feetY, z - vz + vz * t);
            }
            double speed = Math.sqrt(vx * vx + vz * vz);
            double push = speed <= 1.0E-6D ? 0.0D : impulse / speed;
            vx = vx * 0.91D + vx * push;
            vy = (vy - 0.08D) * 0.98D;
            vz = vz * 0.91D + vz * push;
        }
        return null;
    }

    private PlacementTarget grimFootingRescueTarget(Vec3 predicted, ItemStack stack) {
        if (MC.player == null || MC.level == null) return null;
        Vec3 velocity = MC.player.getDeltaMovement();
        if (!MC.player.onGround() && velocity.y > 0.0D) return null;
        Vec3 lane = currentMovementLine == null ? null : currentMovementLine.direction();
        boolean jumping = grimJumpKeyHeld();
        if (!MC.player.onGround()) {

            int row = grimBuiltFloorRow();
            Vec3 landing = grimDescentCrossing(
                MC.player.position(), velocity, row + 1.0D, GRIM_DESCENT_LOOKAHEAD_TICKS);
            if (landing != null) {

                List<BlockPos> columns = grimCatchColumns(
                    new Vec3(landing.x, row + 1.5D, landing.z));
                BlockPos centered = columns.get(0);
                if (MC.level.isOutsideBuildHeight(centered)) return null;
                if (isSolidSupport(MC.level.getBlockState(centered), centered)) return null;
                for (BlockPos cell : columns) {
                    if (MC.level.isOutsideBuildHeight(cell)) continue;
                    BlockState state = MC.level.getBlockState(cell);
                    if (!state.canBeReplaced() || isSolidSupport(state, cell)) continue;
                    PlacementTarget target =
                        planTargetForCandidate(cell, predicted, false, lane, jumping);

                    if (grimPlannedFaceBlind(target)) continue;
                    if (target != null) return target;
                }
                return null;
            }

            return null;
        }
        return grimBelowFeetCatchTarget(predicted, lane, jumping);
    }

    private PlacementTarget grimBelowFeetCatchTarget(Vec3 predicted, Vec3 lane, boolean jumping) {
        Vec3 position = MC.player.position();
        List<BlockPos> columns = grimCatchColumns(position);
        BlockPos centered = columns.get(0);
        if (MC.level.isOutsideBuildHeight(centered)) return null;
        if (isSolidSupport(MC.level.getBlockState(centered), centered)) return null;
        for (BlockPos support : columns) {
            if (MC.level.isOutsideBuildHeight(support)) continue;
            BlockState state = MC.level.getBlockState(support);
            if (!state.canBeReplaced() || isSolidSupport(state, support)) continue;
            PlacementTarget target = planTargetForCandidate(support, predicted, false, lane, jumping);

            if (grimPlannedFaceBlind(target)) continue;
            if (target != null) return target;
        }
        return null;
    }

    private boolean grimPlannedFaceBlind(PlacementTarget target) {
        return target != null && MC.player != null
            && !grimLegRayLands(target, MC.player.getEyePosition(), grimLeadStep());
    }

    static List<BlockPos> grimCatchColumns(Vec3 position) {
        BlockPos centered = BlockPos.containing(position).below();
        List<BlockPos> columns = new ArrayList<>(4);
        columns.add(centered);
        double fx = position.x - Math.floor(position.x);
        double fz = position.z - Math.floor(position.z);
        int sx = fx < 0.3D ? -1 : fx > 0.7D ? 1 : 0;
        int sz = fz < 0.3D ? -1 : fz > 0.7D ? 1 : 0;
        double overlapX = sx == 0 ? 0.0D : sx < 0 ? 0.3D - fx : fx - 0.7D;
        double overlapZ = sz == 0 ? 0.0D : sz < 0 ? 0.3D - fz : fz - 0.7D;
        BlockPos xSide = sx == 0 ? null : centered.offset(sx, 0, 0);
        BlockPos zSide = sz == 0 ? null : centered.offset(0, 0, sz);
        BlockPos first = overlapX >= overlapZ ? xSide : zSide;
        BlockPos second = overlapX >= overlapZ ? zSide : xSide;
        if (first != null) columns.add(first);
        if (second != null) columns.add(second);
        if (sx != 0 && sz != 0) columns.add(centered.offset(sx, 0, sz));
        return columns;
    }

    private boolean grimTowerActive() {
        if (MC == null || MC.player == null || MC.options == null) return false;
        if (!isGrimFamily() || !grimJumpKeyHeld()) return false;
        if (!isValidBlock(planningStack())) return false;
        boolean directional = physicallyDown(MC.options.keyUp) || physicallyDown(MC.options.keyDown)
            || physicallyDown(MC.options.keyLeft) || physicallyDown(MC.options.keyRight);
        return !directional || MC.player.horizontalCollision;
    }

    private boolean grimCourseAscends() {
        if (grimTowerActive()) return true;
        Vec3 lane = currentMovementLine == null ? null : currentMovementLine.direction();
        if (lane == null || isGrimDiagonalDirection(lane)) return true;
        if (grimPhysicalClimbIntent || grimLaunchReservationAirborne) return true;
        int since = grimTicksSince(
            AutismSharedState.get().getClientTickCounter(), grimLastRowGainTick);
        return since >= 0 && since <= GRIM_CLIMB_CONTINUATION_TICKS;
    }

    private AutismRotationUtil.Rotation grimTowerPreAimGoal() {

        BlockPos support = BlockPos.containing(MC.player.position()).below();
        float yaw = grimSteeredPostureYaw();
        float pitch = grimTopCrossingPitch(MC.player.getEyePosition(), support, yaw);
        if (Float.isNaN(pitch)) {
            BlockPos cell = BlockPos.containing(MC.player.position());
            Vec3 point = new Vec3(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D);
            pitch = AutismRotationUtil.lookingAt(point, MC.player.getEyePosition()).pitch();
        }
        return new AutismRotationUtil.Rotation(yaw, grimPlacementPitchCap(pitch));
    }

    private PlacementTarget grimTowerTarget() {
        if (!grimTowerActive() || MC.player.onGround() || MC.level == null) return null;
        BlockPos cell = BlockPos.containing(MC.player.position());
        for (int depth = 0; depth < 3; depth++) {
            if (MC.level.isOutsideBuildHeight(cell)) return null;
            if (!MC.level.getBlockState(cell).canBeReplaced()) return null;
            BlockPos below = cell.below();
            if (MC.level.isOutsideBuildHeight(below)) return null;
            if (!MC.level.getBlockState(below).canBeReplaced()) {
                return planTargetForCandidate(
                    cell, MC.player.position(), false, null, true, Direction.UP);
            }
            cell = below;
        }
        return null;
    }

    private PlacementTarget grimStaircaseRiserTarget() {
        grimTraceRiserFail = "--";
        if (MC.player == null || MC.level == null) return null;
        if (MC.player.onGround() || !grimJumpKeyHeld()) return null;
        if (!grimCourseAscends()) {
            grimTraceRiserFail = "flat";
            return null;
        }
        Vec3 position = MC.player.position();
        Vec3 velocity = MC.player.getDeltaMovement();
        Vec3 lane = currentMovementLine == null ? null : currentMovementLine.direction();

        StringBuilder fails = new StringBuilder();
        PlacementTarget footing = null;
        BlockPos arcLanding = grimArcRiserSupport(grimOracleFootingRow());
        BlockPos ownSupport = grimOwnRiserSupport();
        for (BlockPos support : grimRiserSupportCandidates()) {
            BlockPos riser = support.above();
            if (MC.level.isOutsideBuildHeight(riser)) continue;
            String fail;

            boolean own = grimSameColumn(support, ownSupport) && lane == null;

            if (grimCellOnCooldown(riser)) {
                fail = "dead";
            } else if (!MC.level.getBlockState(riser).canBeReplaced()) {
                fail = "occupied";
            } else if (!(own
                ? grimBoxOverColumn(position, support)
                : grimArcLandsOnColumnLive(position, velocity, riser))) {

                fail = "arc";

                if (traceArmed() && !own) {
                    double[] landing = grimHeldArcLandingLive(position, velocity, velocity.y,
                        riser.getY() + 1.0D, false, grimArcSneakTicks());
                    fail = landing == null ? "arc(null)" : String.format(java.util.Locale.ROOT,
                        "arc(ovl%.2f)", grimLandingMinOverlap(landing, riser));
                }
            } else if (!isSolidSupport(MC.level.getBlockState(support), support)) {

                PlacementTarget sideReach =
                    planTargetForCandidate(riser, position, false, lane, true, null);
                if (sideReach != null) {
                    grimTraceRiserFail = "--";
                    return sideReach;
                }
                if (footing == null && support.equals(arcLanding)
                    && !grimCellOnCooldown(support)) {
                    footing = planTargetForCandidate(support, position, false, lane, true, null);
                }
                fail = footing == null ? "no-support" : "footing";
            } else {
                PlacementTarget plan = planTargetForCandidate(
                    riser, position, false, lane, true, Direction.UP);
                if (plan != null) {
                    grimTraceRiserFail = "--";
                    return plan;
                }
                fail = "plan";
            }
            if (fails.length() > 0) fails.append(',');
            fails.append(support.getX()).append('/').append(support.getZ()).append(':').append(fail);
        }
        if (traceArmed() && fails.length() > 0) {

            fails.append(";own=").append(ownSupport == null ? "--"
                : ownSupport.getX() + "/" + ownSupport.getZ());
            fails.append(";land=").append(arcLanding == null ? "--"
                : arcLanding.getX() + "/" + arcLanding.getZ());
        }
        grimTraceRiserFail = fails.length() == 0 ? "none" : fails.toString();
        return footing;
    }

    private static double grimLandingMinOverlap(double[] landing, BlockPos cell) {
        double overlapX = Math.min(landing[0] + GRIM_LANDING_HALF_WIDTH, cell.getX() + 1.0D)
            - Math.max(landing[0] - GRIM_LANDING_HALF_WIDTH, cell.getX());
        double overlapZ = Math.min(landing[1] + GRIM_LANDING_HALF_WIDTH, cell.getZ() + 1.0D)
            - Math.max(landing[1] - GRIM_LANDING_HALF_WIDTH, cell.getZ());
        return Math.min(overlapX, overlapZ);
    }

    private BlockPos grimArcRiserSupport(int footingRow) {
        if (MC.player == null) return null;
        Vec3 position = MC.player.position();
        Vec3 velocity = MC.player.getDeltaMovement();

        boolean grounded = MC.player.onGround();
        double[] landing = grimHeldArcLandingLive(
            position, velocity, grounded ? GRIM_JUMP_TAKEOFF_VELOCITY : velocity.y,
            footingRow + 2.0D, grounded, grimArcSneakTicks());
        if (landing == null) return null;
        return new BlockPos(Mth.floor(landing[0]), footingRow, Mth.floor(landing[1]));
    }

    private BlockPos grimArcLandingSupport(int footingRow) {
        if (MC.player == null) return null;
        Vec3 position = MC.player.position();
        Vec3 velocity = MC.player.getDeltaMovement();
        boolean grounded = MC.player.onGround();
        double takeoffVy = grounded ? GRIM_JUMP_TAKEOFF_VELOCITY : velocity.y;
        double[] landing = null;
        if (grimCourseAscends()) {
            landing = grimHeldArcLandingLive(
                position, velocity, takeoffVy, footingRow + 2.0D, grounded, grimArcSneakTicks());
        }
        if (landing == null) {
            landing = grimHeldArcLandingLive(
                position, velocity, takeoffVy, footingRow + 1.0D, grounded, grimArcSneakTicks());
        }
        if (landing == null) return null;
        return new BlockPos(Mth.floor(landing[0]), footingRow, Mth.floor(landing[1]));
    }

    private List<BlockPos> grimRiserSupportCandidates() {
        List<BlockPos> candidates = new ArrayList<>(6);
        int footingRow = grimOracleFootingRow();
        BlockPos foot = grimEffectiveFootCell();
        BlockPos own = new BlockPos(foot.getX(), footingRow, foot.getZ());
        BlockPos landing = grimArcRiserSupport(footingRow);

        candidates.addAll(grimPrimaryRiserSupports(
            own, landing, isGrimDiagonalDirection(grimLaneStepDirection())));

        Vec3 step = grimLaneStepDirection();
        BlockPos ahead = new BlockPos(
            own.getX() + horizontalStep(step.x), footingRow, own.getZ() + horizontalStep(step.z));
        if (!ahead.equals(own) && !candidates.contains(ahead)) candidates.add(ahead);
        int tried = 0;
        for (Iterator<BlockPos> recent = lastPlacedBlocks.descendingIterator();
             recent.hasNext() && tried < 3; tried++) {
            BlockPos support = recent.next();
            if (!candidates.contains(support)) candidates.add(support);
        }
        return candidates;
    }

    static List<BlockPos> grimPrimaryRiserSupports(
        BlockPos own, BlockPos landing, boolean diagonal
    ) {
        List<BlockPos> ordered = new ArrayList<>(2);
        BlockPos first = diagonal ? landing : own;
        BlockPos second = diagonal ? own : landing;
        if (first != null) ordered.add(first);
        if (second != null && !ordered.contains(second)) ordered.add(second);
        return ordered;
    }

    static List<BlockPos> grimLandingConnectorCandidates(BlockPos landing, Vec3 laneDirection) {
        List<BlockPos> connectors = new ArrayList<>(2);
        if (landing == null || laneDirection == null) return connectors;
        int stepX = horizontalStep(laneDirection.x);
        int stepZ = horizontalStep(laneDirection.z);
        if (stepX != 0) connectors.add(landing.offset(-stepX, 0, 0));
        if (stepZ != 0) {
            BlockPos zLeg = landing.offset(0, 0, -stepZ);
            if (!connectors.contains(zLeg)) connectors.add(zLeg);
        }
        return connectors;
    }

    private PlacementTarget grimRiserSideFallback(
        BlockPos riser, BlockPos landing, Vec3 predicted, ItemStack stack, Vec3 lane
    ) {
        if (riser == null || landing == null) return null;
        for (BlockPos connector : grimLandingConnectorCandidates(landing, lane)) {
            Direction face = grimRiserSideFace(landing, connector);
            if (face == null) continue;
            PlacementTarget viaStep = grimPlanReservedCell(riser, predicted, stack, lane, face);
            if (viaStep != null) return viaStep;
        }
        return null;
    }

    static Direction grimRiserSideFace(BlockPos landing, BlockPos connector) {
        if (landing == null || connector == null) return null;
        int dx = landing.getX() - connector.getX();
        int dz = landing.getZ() - connector.getZ();
        if (dx != 0 && dz != 0) return null;
        if (dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    private PlacementTarget grimLaunchReservationTarget(Vec3 predicted, ItemStack stack) {
        if (MC.player == null || MC.level == null || !grimPlanningJump()
            || currentMovementLine == null) {
            grimLaunchReservationStage = "--";
            return null;
        }
        Vec3 lane = currentMovementLine.direction();
        BlockPos landing = grimLaunchReservedSupport;
        if (landing == null) {
            landing = grimArcLandingSupport(grimOracleFootingRow());
            if (landing == null || MC.level.isOutsideBuildHeight(landing)) {
                grimLaunchReservationStage = "unknown";
                return null;
            }
            grimLaunchReservedSupport = landing.immutable();
        }

        boolean supportSolid = solidAt(landing);
        int deficit = supportSolid ? 0 : grimCellDeficit(landing);
        BlockPos riser = landing.above();
        boolean riserSolid = !MC.level.isOutsideBuildHeight(riser) && solidAt(riser);
        GrimReservationNeed need = grimReservationNeed(supportSolid, deficit, riserSolid);

        if (need == GrimReservationNeed.READY || MC.level.isOutsideBuildHeight(riser)) {
            grimLaunchReservedConnector = null;
            grimLaunchReservationStage = "ready";
            return null;
        }

        if (need == GrimReservationNeed.RISER) {
            grimLaunchReservedConnector = null;

            if (!grimCourseAscends()) {
                grimLaunchReservedRiser = null;
                grimLaunchReservationStage = "ready";
                return null;
            }

            grimLaunchReservedRiser = riser.immutable();
            PlacementTarget rise = grimPlanReservedCell(
                riser, predicted, stack, lane, Direction.UP);

            if (rise == null) {
                rise = grimRiserSideFallback(riser, landing, predicted, stack, lane);
            }
            grimLaunchReservationStage = rise == null ? "riser-wait" : "riser";
            return rise;
        }

        if (need == GrimReservationNeed.SUPPORT) {
            grimLaunchReservedConnector = null;

            PlacementTarget step = grimClimbStepTarget(landing, predicted, stack, lane);
            if (step != null) {
                grimLaunchReservationStage = "step";
                return step;
            }
            PlacementTarget support = grimPlanReservedCell(
                landing, predicted, stack, lane, null);
            grimLaunchReservationStage = support == null ? "support-wait" : "support";
            return support;
        }

        List<PlacementTarget> legs = new ArrayList<>(2);
        for (BlockPos connector : grimLandingConnectorCandidates(landing, lane)) {
            if (MC.level.isOutsideBuildHeight(connector) || solidAt(connector)
                || grimCellDeficit(connector) > 1
                || grimCellBehind(MC.player.position(), lane, connector, GRIM_BEHIND_MARGIN)) {
                continue;
            }
            PlacementTarget leg = grimPlanReservedCell(
                connector, predicted, stack, lane, null);
            if (leg != null) legs.add(leg);
        }
        if (legs.isEmpty()) {
            grimLaunchReservedConnector = null;
            grimLaunchReservationStage = "leg-wait";
            return null;
        }
        legs.sort(grimSeedOrder(predicted, grimRestPoseGoal()));
        PlacementTarget leg = grimNearestPlaneLeg(legs, MC.player.getEyePosition(),
            grimLeadStep(), landing, grimLaunchReservedConnector);
        grimLaunchReservedConnector = leg.placedBlock().immutable();
        grimLaunchReservationStage = leg == legs.get(0) ? "leg" : "leg-plane";
        return leg;
    }

    static PlacementTarget grimNearestPlaneLeg(List<PlacementTarget> legs, Vec3 eye, BlockPos held) {
        PlacementTarget best = legs.get(0);
        if (legs.size() < 2 || !best.face().getAxis().isHorizontal()) return best;
        double bestPast = grimEyePastPlane(eye, best.supportBlock(), best.face());
        for (int i = 1; i < legs.size(); i++) {
            PlacementTarget leg = legs.get(i);
            if (!leg.face().getAxis().isHorizontal()) continue;
            double past = grimEyePastPlane(eye, leg.supportBlock(), leg.face());
            if (past > bestPast) {
                best = leg;
                bestPast = past;
            }
        }
        if (held == null || best.placedBlock().equals(held)) return best;
        for (PlacementTarget leg : legs) {
            if (!leg.placedBlock().equals(held) || !leg.face().getAxis().isHorizontal()) continue;
            double heldPast = grimEyePastPlane(eye, leg.supportBlock(), leg.face());
            if (grimSwapAcrossClickMargin(bestPast, heldPast)) break;
            if (bestPast - heldPast <= GRIM_LEG_SWAP_MARGIN) return leg;
            break;
        }
        return best;
    }

    static boolean grimSwapAcrossClickMargin(double bestPast, double heldPast) {
        return bestPast >= GRIM_EYE_PAST_FACE_MARGIN && heldPast < GRIM_EYE_PAST_FACE_MARGIN;
    }

    static PlacementTarget grimNearestPlaneLeg(
        List<PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos held
    ) {
        return grimNearestPlaneLeg(legs, eye, lead, null, held);
    }

    static PlacementTarget grimNearestPlaneLeg(
        List<PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos landing, BlockPos held
    ) {
        List<PlacementTarget> live = grimSightedLegs(legs, eye, lead);
        if (held == null) {
            PlacementTarget successor = grimSuccessorReachableLeg(live, eye, lead, landing);
            if (successor != null) return successor;
        }
        return grimNearestPlaneLeg(live, eye, held);
    }

    static PlacementTarget grimSuccessorReachableLeg(
        List<PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos landing
    ) {
        if (legs == null || legs.size() < 2 || landing == null || eye == null) return null;
        PlacementTarget only = null;
        double bestPast = -Double.MAX_VALUE;
        for (PlacementTarget leg : legs) {
            bestPast = Math.max(bestPast, grimEyePastPlane(eye, leg.supportBlock(), leg.face()));
            if (!grimSuccessorRayLands(leg, landing, eye, lead)) continue;
            if (only != null) return null;
            only = leg;
        }
        if (only == null) return null;
        double past = grimEyePastPlane(eye, only.supportBlock(), only.face());
        return bestPast - past <= GRIM_LEG_SWAP_MARGIN ? only : null;
    }

    static boolean grimSuccessorRayLands(
        PlacementTarget leg, BlockPos landing, Vec3 eye, Vec3 lead
    ) {
        if (leg == null || landing == null) return false;
        Direction face = grimRiserSideFace(landing, leg.placedBlock());
        if (face == null) return false;
        Vec3 at = lead == null ? eye : eye.add(lead.scale(GRIM_PIN_LOOKAHEAD_TICKS));
        return grimCrossingInSquare(at, leg.placedBlock(), face, leg.rotation().yaw());
    }

    static List<PlacementTarget> grimSightedLegs(List<PlacementTarget> legs, Vec3 eye, Vec3 lead) {
        if (legs == null || legs.size() < 2) return legs;
        List<PlacementTarget> sighted = new ArrayList<>(legs.size());
        for (PlacementTarget leg : legs) {
            if (grimLegRayLands(leg, eye, lead)) sighted.add(leg);
        }
        return sighted.isEmpty() || sighted.size() == legs.size() ? legs : sighted;
    }

    static boolean grimLegRayLands(PlacementTarget leg, Vec3 eye, Vec3 lead) {
        if (leg == null || eye == null || !leg.face().getAxis().isHorizontal()) return true;
        float yaw = leg.rotation().yaw();
        for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            Vec3 at = lead == null ? eye : eye.add(lead.scale(step));
            if (grimEyePastPlane(at, leg.supportBlock(), leg.face()) <= 0.0D) return true;
            if (grimCrossingInSquare(at, leg.supportBlock(), leg.face(), yaw)) return true;
        }
        return false;
    }

    private PlacementTarget grimPlanReservedCell(
        BlockPos candidate, Vec3 predicted, ItemStack stack, Vec3 lane, Direction preferredFace
    ) {
        if (candidate == null || MC.level.isOutsideBuildHeight(candidate)) {
            return grimNoteReserveWhy(candidate, "oob", null);
        }
        if (grimCellOnCooldown(candidate)) {
            return grimNoteReserveWhy(candidate, "cooldown", null);
        }
        if (grimCellBehind(MC.player.position(), lane, candidate, GRIM_BEHIND_MARGIN)) {
            return grimNoteReserveWhy(candidate, "behind", null);
        }
        BlockState state = MC.level.getBlockState(candidate);
        if (isSolidSupport(state, candidate) || !state.canBeReplaced()) {
            return grimNoteReserveWhy(candidate, "solid", null);
        }
        boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
        if (replaceExisting && !canBeReplacedWith(state, candidate, stack)) {
            return grimNoteReserveWhy(candidate, "no-repl", null);
        }

        GrimRowLock rowLock = grimActiveRowLock();
        boolean rising = candidate.getY() > grimOracleFootingRow();
        if (rowLock != null && !rowLock.allows(candidate, rising)) {
            return grimNoteReserveWhy(candidate, "rowlock", null);
        }
        Direction onlyFace = preferredFace != null ? preferredFace
            : rowLock == null ? null : rowLock.pinnedFace(candidate);
        PlacementTarget plan = planTargetForCandidate(
            candidate, predicted, replaceExisting, lane, true, onlyFace, true);

        if (plan != null && grimPlannedFaceBlind(plan)) {
            return grimNoteReserveWhy(candidate, "blind", null);
        }
        return grimNoteReserveWhy(candidate, plan == null ? "plan" : "ok", plan);
    }

    static List<BlockPos> grimClimbStepCandidates(BlockPos landing, Vec3 lane) {
        List<BlockPos> candidates = new ArrayList<>(2);
        if (landing == null || lane == null) return candidates;
        int backX = -horizontalStep(lane.x);
        int backZ = -horizontalStep(lane.z);
        if (backX != 0) candidates.add(landing.offset(backX, 1, 0));
        if (backZ != 0) candidates.add(landing.offset(0, 1, backZ));
        return candidates;
    }

    private PlacementTarget grimClimbStepTarget(
        BlockPos landing, Vec3 predicted, ItemStack stack, Vec3 lane
    ) {
        if (landing == null || lane == null || MC.level == null || !grimCourseAscends()) {
            return null;
        }
        BlockPos rise = landing.above();
        if (MC.level.isOutsideBuildHeight(rise) || solidAt(rise)) return null;
        for (BlockPos step : grimClimbStepCandidates(landing, lane)) {
            if (MC.level.isOutsideBuildHeight(step) || solidAt(step)) continue;

            if (!solidAt(step.below())) continue;
            PlacementTarget plan = grimPlanReservedCell(step, predicted, stack, lane, Direction.UP);
            if (plan != null) {
                grimLaunchReservedStep = step.immutable();
                return plan;
            }
        }
        return null;
    }

    private PlacementTarget grimNoteReserveWhy(BlockPos cell, String why, PlacementTarget plan) {
        if (!traceArmed() || grimTraceReserveWhy.length() > 220) return plan;
        String detail = "plan".equals(why) && grimPlanDetail.length() > 0
            ? "plan[" + grimPlanDetail.toString().trim() + "]" : why;
        if (plan != null && MC.player != null && plan.face().getAxis().isHorizontal()) {
            detail += String.format(java.util.Locale.ROOT, "/past%+.2f", grimEyePastPlane(
                MC.player.getEyePosition(), plan.supportBlock(), plan.face()));
        }
        grimTraceReserveWhy = ("--".equals(grimTraceReserveWhy) ? "" : grimTraceReserveWhy + ",")
            + traceCell(cell) + ":" + detail;
        return plan;
    }

    private static String traceCell(BlockPos cell) {
        return cell == null ? "--" : cell.getX() + "," + cell.getY() + "," + cell.getZ();
    }

    private PlacementTarget findPlacementTarget(ItemStack stack) {
        grimAimHoldServed = false;

        grimTraceReserveWhy = "--";
        PlacementTarget target = selectPlacementTarget(stack);
        if (isGrimFamily() && target != null && MC.player != null) {
            int stampAge = grimTicksSince(
                AutismSharedState.get().getClientTickCounter(), grimPaceRiserHoldTick);
            boolean stampFresh = grimPaceRiserHoldCell != null
                && stampAge >= 0 && stampAge <= GRIM_PACE_RISER_HOLD_STAMP_TICKS;

            boolean windowAlive = grimAimWindowOpening(target)
                || grimCrossingLandsOnFace(MC.player.getEyePosition(),
                    target.supportBlock(), target.face(), target.rotation().yaw(), true);
            if (grimDyingRiserPickSteal(MC.player.onGround(),
                MC.player.getDeltaMovement().y < 0.0D, stampFresh,
                target.placedBlock().equals(grimPaceRiserHoldCell),
                target.face().getAxis().isHorizontal(),
                grimEyePastTargetFace(target), windowAlive)) {

                grimLastPick = "steal-veto:" + target.placedBlock().toShortString();
                grimStickyTarget = null;
                return null;
            }
        }

        target = grimUpFaceUpgrade(target);

        if (isGrimFamily() && !grimAimHoldServed) grimNoteAimCommit(target);
        return target;
    }

    private PlacementTarget grimUpFaceUpgrade(PlacementTarget target) {
        if (target == null || !isGrimFamily() || MC.player == null || MC.level == null) {
            return target;
        }
        BlockPos placed = target.placedBlock();
        BlockState state = MC.level.getBlockState(placed);
        boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
        if (!grimUpFaceUpgradeApplies(
            target.face(), replaceExisting, grimFullCubeAt(placed.below()))) {
            return target;
        }
        String planFail = grimLastPlanFail;

        PlacementTarget up = planTargetForCandidate(placed,
            predictedPlacementPosition(currentMovementLine), replaceExisting,
            currentMovementLine == null ? null : currentMovementLine.direction(),
            grimPlanningJump(), Direction.UP);
        grimLastPlanFail = planFail;
        if (up == null || !up.placedBlock().equals(placed) || up.face() != Direction.UP) {
            return target;
        }

        grimUpFaceSwapCell = placed.immutable();
        return up;
    }

    private PlacementTarget selectPlacementTarget(ItemStack stack) {
        if (!isValidBlock(stack)) return null;
        Vec3 predicted = predictedPlacementPosition(currentMovementLine);
        BlockPos predictedBase = targetedBase(predicted);
        if (isGrimFamily()) {

            PlacementTarget tower = grimTowerTarget();
            if (tower != null) {
                grimStickyTarget = tower;
                grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                grimStickyBandMissTicks = 0;
                grimLastPick = "tower:" + tower.placedBlock().toShortString();
                return tower;
            }

            PlacementTarget rescue = grimFootingRescueTarget(predicted, stack);
            if (rescue != null) {
                grimLastRescueTick = AutismSharedState.get().getClientTickCounter();
                grimStickyTarget = rescue;
                grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                grimStickyBandMissTicks = 0;
                grimLastPick = "rescue:" + rescue.placedBlock().toShortString();
                return rescue;
            }
            PlacementTarget riser = grimStaircaseRiserTarget();
            if (riser != null) {
                grimStickyTarget = riser;
                grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                grimStickyBandMissTicks = 0;
                grimLastPick = "riser:" + riser.placedBlock().toShortString();
                return riser;
            }

            PlacementTarget reservation = grimLaunchReservationTarget(predicted, stack);
            if (reservation != null && !grimBelowBuiltFloor(reservation)
                && !grimFallUncatchable(reservation)) {
                grimStickyTarget = reservation;
                grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                grimStickyBandMissTicks = 0;
                grimLastPick = "reserve-" + grimLaunchReservationStage + ":"
                    + reservation.placedBlock().toShortString();
                return reservation;
            }

            if (MC.player != null && !MC.player.onGround()
                && MC.player.getDeltaMovement().y < 0.0D
                && grimDescendingBelowFootingSoon()) {
                PlacementTarget catchBelow = grimBelowFeetCatchTarget(predicted,
                    currentMovementLine == null ? null : currentMovementLine.direction(),
                    grimJumpKeyHeld());
                if (catchBelow != null) {
                    grimLastRescueTick = AutismSharedState.get().getClientTickCounter();
                    grimStickyTarget = catchBelow;
                    grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                    grimStickyBandMissTicks = 0;
                    grimLastPick = "catch:" + catchBelow.placedBlock().toShortString();
                    return catchBelow;
                }
            }
            boolean rowLocked = grimActiveRowLock() != null;
            if (!rowLocked) {

                predictedBase = grimRowLockedBase(predictedBase);
                PlacementTarget drift = findTrajectoryDriftTarget(predictedBase);
                if (drift != null) {
                    grimStickyTarget = drift;
                    grimStickySetTick = AutismSharedState.get().getClientTickCounter();
                    grimStickyBandMissTicks = 0;
                    return drift;
                }
            }

            PlacementTarget held = grimAimCommitHold();
            if (held != null && !grimFallUncatchable(held)) {
                grimLastPick = "hold:" + held.placedBlock().toShortString();
                return held;
            }
            PlacementTarget sticky = replanGrimStickyTarget(predicted);
            if (sticky != null && !grimBelowBuiltFloor(sticky)
                && !grimFallUncatchable(sticky)) {
                grimLastPick = "sticky";
                return sticky;
            }

            PlacementTarget fresh = findFromBase(predictedBase, predicted, stack);
            String freshTier = rowLocked ? "row" : "lane";
            if (fresh != null && grimFallUncatchable(fresh)) {

                fresh = null;
                grimLastPick = freshTier + "-uncatch";
            } else {
                grimLastPick = fresh != null
                    ? freshTier + ":" + fresh.placedBlock().toShortString()
                    : freshTier + "-null";
            }
            grimStickyTarget = fresh;
            grimStickySetTick = AutismSharedState.get().getClientTickCounter();
            grimStickyBandMissTicks = 0;
            return fresh;
        }
        return findFromBase(predictedBase, predicted, stack);
    }

    private BlockPos grimRowLockedBase(BlockPos base) {
        if (base == null || grimEffCell == null || currentMovementLine == null) {
            return base;
        }
        Vec3 direction = currentMovementLine.direction();
        if (isGrimDiagonalDirection(direction)) return base;
        boolean northSouth = Math.abs(direction.z) > Math.abs(direction.x);
        int row = northSouth ? grimEffCell.getX() : grimEffCell.getZ();
        if (northSouth) {
            return base.getX() == row ? base
                : new BlockPos(row, base.getY(), base.getZ());
        }
        return base.getZ() == row ? base
            : new BlockPos(base.getX(), base.getY(), row);
    }

    private BlockPos grimEffectiveFootCell() {
        Vec3 velocity = MC.player.getDeltaMovement();
        Vec3 position = MC.player.position();
        Vec3 future = position.add(
            velocity.x * TRAJECTORY_DRIFT_LEAD_TICKS, 0.0D, velocity.z * TRAJECTORY_DRIFT_LEAD_TICKS);
        BlockPos foot = BlockPos.containing(future);
        int tick = AutismSharedState.get().getClientTickCounter();
        if (grimEffCellRefreshTick == tick && grimEffCell != null) {
            return new BlockPos(grimEffCell.getX(), foot.getY(), grimEffCell.getZ());
        }
        grimEffCellRefreshTick = tick;
        BlockPos committed = grimEffCell;
        if (committed == null
            || foot.getX() == committed.getX() && foot.getZ() == committed.getZ()) {
            grimEffCell = foot;
            return foot;
        }

        boolean projectionFirm = true;
        if (foot.getX() != committed.getX()) {
            projectionFirm &= grimBoundaryCrossedFirmly(future.x, foot.getX() > committed.getX());
        }
        if (foot.getZ() != committed.getZ()) {
            projectionFirm &= grimBoundaryCrossedFirmly(future.z, foot.getZ() > committed.getZ());
        }
        if (projectionFirm) {
            grimEffCell = foot;
        } else {
            BlockPos currentCell = BlockPos.containing(position);
            if (currentCell.getX() != committed.getX() || currentCell.getZ() != committed.getZ()) {
                boolean positionFirm = true;
                if (currentCell.getX() != committed.getX()) {
                    positionFirm &= grimInsideCellFirmly(position.x, currentCell.getX() > committed.getX());
                }
                if (currentCell.getZ() != committed.getZ()) {
                    positionFirm &= grimInsideCellFirmly(position.z, currentCell.getZ() > committed.getZ());
                }
                if (positionFirm) grimEffCell = currentCell;
            }
        }

        return new BlockPos(grimEffCell.getX(), foot.getY(), grimEffCell.getZ());
    }

    private static boolean grimInsideCellFirmly(double coordinate, boolean positive) {
        double fraction = coordinate - Math.floor(coordinate);
        return positive ? fraction > 0.10D : fraction < 0.90D;
    }

    private static boolean grimBoundaryCrossedFirmly(double coordinate, boolean positive) {
        double fraction = coordinate - Math.floor(coordinate);
        return positive ? fraction > 0.15D : fraction < 0.85D;
    }

    private PlacementTarget replanGrimStickyTarget(Vec3 predicted) {
        PlacementTarget sticky = grimStickyTarget;
        if (sticky == null) return null;
        grimStickyTarget = null;
        int tick = AutismSharedState.get().getClientTickCounter();
        if (grimStickySetTick < 0 || tick - grimStickySetTick > GRIM_STICKY_MAX_TICKS) return null;
        BlockPos placed = sticky.placedBlock();
        if (MC.level.isOutsideBuildHeight(placed)
            || isSolidSupport(MC.level.getBlockState(placed), placed)) return null;
        BlockPos support = sticky.supportBlock();
        if (MC.level.isOutsideBuildHeight(support)
            || MC.level.getBlockState(support).canBeReplaced()) return null;

        if (MC.player != null && MC.player.onGround()
            && placed.getY() < BlockPos.containing(MC.player.position()).getY() - 1) {
            return null;
        }
        Vec3 laneDirection = currentMovementLine == null ? null : currentMovementLine.direction();

        if (MC.player != null && !grimDescendingBelowFooting()
            && grimCellBehind(MC.player.position(), laneDirection, placed, GRIM_BEHIND_MARGIN)) {
            return null;
        }

        if (sticky.face().getAxis().isVertical() && MC.player != null
            && !footprintOverlapsColumn(
                MC.player.position(), MC.player.getDeltaMovement(), placed)) {
            return null;
        }

        if (MC.player != null
            && supportTooFarToHold(
                MC.player.position(), support, GRIM_SUPPORT_MAX_DISTANCE)) {
            return null;
        }
        boolean jumping = grimPlanningJump();

        GrimRowLock rowLock = grimActiveRowLock();
        if (rowLock != null && !rowLock.allows(placed, grimRiseAllowed(jumping))) return null;
        PlacementTarget replanned = planTargetForCandidate(
            placed, predicted, false, laneDirection, jumping, sticky.face());
        if (replanned == null || !replanned.supportBlock().equals(support)
            || replanned.face() != sticky.face()) return null;

        if (MC.player != null
            && !grimLegRayLands(replanned, MC.player.getEyePosition(), grimLeadStep())) {
            return null;
        }

        if (replanned.face().getAxis().isHorizontal() && MC.player != null) {

            boolean bandExit = grimEyeOutsideFaceBand(
                MC.player.getEyePosition(), replanned.supportBlock(), replanned.face());

            boolean outOfReach = grimPitchOutOfReach(grimSideWindowSolvePitch(
                MC.player.getEyePosition(), replanned.supportBlock(), replanned.face(),
                replanned.rotation().yaw()));
            if (grimStickyBandTick != tick) {
                grimStickyBandTick = tick;
                grimStickyBandMissTicks = bandExit ? grimStickyBandMissTicks + 1 : 0;

                grimStickyPitchMissTicks = outOfReach ? grimStickyPitchMissTicks + 1 : 0;
            }
            if (grimStickyBandMissTicks >= 2) {
                grimStickyBandMissTicks = 0;
                return null;
            }
            if (grimStickyPitchMissTicks >= 2) {
                grimStickyPitchMissTicks = 0;
                return null;
            }
        }
        grimStickyTarget = replanned;
        return replanned;
    }

    static boolean grimPitchOutOfReach(float requiredPitch) {
        return Float.isFinite(requiredPitch) && requiredPitch > GRIM_PLACE_MAX_PITCH_HARD;
    }

    private float grimSnapFreeAimYaw(float freeYaw) {
        if (grimLaneStep() == COURSE_STEP_UNSET) return freeYaw;
        if (grimNoTargetTicks >= 1 || grimDescendingBelowFooting()) return freeYaw;
        return grimSnapYawToLane(grimLaneStepYaw(), freeYaw);
    }

    private int grimYawVetoTicks;

    private int grimGoalVetoTicks;
    private float grimGoalVetoLastErr = Float.NaN;

    private boolean grimYawOffPosture() {
        return grimYawOffPosture(grimSilentRotation);
    }

    private boolean grimYawOffPosture(AutismRotationUtil.Rotation rotation) {
        if (rotation == null || grimLaneStep() == COURSE_STEP_UNSET) return false;

        if (grimNoTargetTicks >= 1 || grimDescendingBelowFooting()) return false;
        return Math.abs(grimLaneOctantResidual(grimLaneStepYaw(), rotation.yaw()))
            > GRIM_LANE_OCTANT_MAX_RESIDUAL;
    }

    private int grimStickyPitchMissTicks;

    private boolean grimTargetOutOfReach(PlacementTarget target) {
        if (target == null || MC.player == null || !target.face().getAxis().isHorizontal()) return false;
        return grimPitchOutOfReach(grimSideWindowSolvePitch(MC.player.getEyePosition(),
            target.supportBlock(), target.face(), target.rotation().yaw()));
    }

    static boolean grimEyeOutsideFaceBand(Vec3 eye, BlockPos support, Direction face) {
        boolean xAxisFace = face.getStepX() != 0;
        double lateral = xAxisFace ? eye.z : eye.x;
        double edge = xAxisFace ? support.getZ() : support.getX();
        return lateral < edge || lateral > edge + 1.0D;
    }

    private PlacementTarget findTrajectoryDriftTarget(BlockPos laneBase) {
        if (MC.player == null || laneBase == null) return null;
        Vec3 velocity = MC.player.getDeltaMovement();
        double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        if (horizontalSpeed < TRAJECTORY_DRIFT_MIN_SPEED) return null;

        BlockPos footCell = grimEffectiveFootCell();

        if (footCell.getY() - 1 != laneBase.getY()) return null;
        BlockPos driftBase = new BlockPos(footCell.getX(), laneBase.getY(), footCell.getZ());
        if (driftBase.equals(laneBase)) return null;

        if (grimCourseFrozen()
            && MC.player.position().y < driftBase.getY() + 1.5D) {
            return null;
        }
        if (Vec3.atCenterOf(driftBase).subtract(MC.player.position()).horizontalDistance()
            > GRIM_GUARD_MAX_DISTANCE) return null;
        if (MC.level.isOutsideBuildHeight(driftBase)) return null;
        if (isSolidSupport(MC.level.getBlockState(driftBase), driftBase)) return null;
        Vec3 future = MC.player.position().add(
            velocity.x * TRAJECTORY_DRIFT_LEAD_TICKS, 0.0D, velocity.z * TRAJECTORY_DRIFT_LEAD_TICKS);

        Vec3 laneDirection = currentMovementLine == null ? null : currentMovementLine.direction();
        boolean jumping = grimPlanningJump();

        if (!grimDescendingBelowFooting()
            && grimCellBehind(MC.player.position(), laneDirection, driftBase, GRIM_BEHIND_MARGIN)) {
            return null;
        }

        PlacementTarget direct = planTargetForCandidate(
            driftBase, future, false, laneDirection, jumping);
        if (direct != null) {
            grimLastPick = "guard:" + driftBase.toShortString();
            return direct;
        }

        List<PlacementTarget> seedPlans = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos seed = driftBase.relative(direction);
            if (MC.level.isOutsideBuildHeight(seed)) continue;
            if (Vec3.atCenterOf(seed).subtract(MC.player.position()).horizontalDistance()
                > GRIM_GUARD_MAX_DISTANCE) continue;
            BlockState seedState = MC.level.getBlockState(seed);
            if (!seedState.isAir()) continue;
            PlacementTarget seeded = planTargetForCandidate(
                seed, future, false, laneDirection, jumping);
            if (seeded != null) seedPlans.add(seeded);
        }
        Vec3 ahead = new Vec3(velocity.x, 0.0D, velocity.z);
        seedPlans.removeIf(plan ->
            Vec3.atCenterOf(plan.placedBlock()).subtract(MC.player.position()).dot(ahead) < 0.0D);
        if (seedPlans.isEmpty()) return null;
        seedPlans.sort(grimSeedOrder(future, grimRestPoseGoal()));
        grimLastPick = "gseed:" + seedPlans.get(0).placedBlock().toShortString();
        return seedPlans.get(0);
    }

    static Comparator<PlacementTarget> grimSeedOrder(Vec3 future, AutismRotationUtil.Rotation posture) {
        return Comparator
            .comparingDouble((PlacementTarget plan) -> Math.ceil(
                Vec3.atCenterOf(plan.placedBlock()).distanceTo(future) / GRIM_GUARD_SEED_BUCKET))
            .thenComparingDouble(plan -> rotationAngle(posture, plan.rotation()));
    }

    static BlockPos targetedBase(Vec3 position) {
        return BlockPos.containing(position).below();
    }

    private BlockPos grimCourseAheadBase(BlockPos realBase) {

        if (MC.player == null || !MC.player.onGround()) return null;

        if (currentMovementLine == null) return null;
        Vec3 direction = currentMovementLine.direction();
        if (direction == null) return null;
        int stepX = horizontalStep(direction.x);
        int stepZ = horizontalStep(direction.z);
        if (stepX == 0 && stepZ == 0) return null;
        BlockPos ahead = realBase.offset(stepX, 0, stepZ);
        if (MC.level.isOutsideBuildHeight(ahead)) return null;
        if (isSolidSupport(MC.level.getBlockState(ahead), ahead)) return null;
        return ahead;
    }

    private PlacementTarget findFromBase(BlockPos base, Vec3 plannedPosition, ItemStack stack) {
        if (base == null) {
            grimLastPlanFail = "base-null";
            return null;
        }
        if (MC.level.isOutsideBuildHeight(base)) {
            grimLastPlanFail = "base-outside-height " + base.toShortString();
            return null;
        }
        BlockState baseState = MC.level.getBlockState(base);
        if (isSolidSupport(baseState, base)) {

            BlockPos realBase = targetedBase(MC.player.position());
            if (!isGrimFamily() || realBase.equals(base)
                || MC.level.isOutsideBuildHeight(realBase)
                || isSolidSupport(MC.level.getBlockState(realBase), realBase)) {

                BlockPos ahead = grimCourseAheadBase(realBase);
                if (ahead == null) {
                    grimLastPlanFail = "base-solid " + base.toShortString();
                    return null;
                }
                base = ahead;
            } else {
                base = realBase;
            }
        }

        Vec3 laneDirection = currentMovementLine == null ? null : currentMovementLine.direction();
        boolean jumping = grimPlanningJump();
        boolean diagonal = isGrimDiagonalDirection(laneDirection);
        List<BlockPos> offsets = isGrimFamily()
            ? orderedOffsetsForWorld(
                grimCandidateOffsets(), base, plannedPosition, currentMovementLine)
            : orderedOffsetsForWorld(NORMAL_OFFSETS, base, plannedPosition, currentMovementLine);
        int solidSkips = 0, replaceSkips = 0, planNulls = 0;

        int gateAbove = 0, gateFloor = 0, gateBehind = 0, gateColumnHold = 0, gateRiseVeto = 0,
            gateRowLock = 0, gateHeight = 0, gateDead = 0, gateBelow = 0;
        String firstPlanDetail = null;

        grimLastPlanFail = "";

        GrimRowLock rowLock = isGrimFamily() ? grimActiveRowLock() : null;

        boolean gateRise = isGrimFamily() && !grimDescendingBelowFooting();
        Vec3 riseFrom = MC.player.position();
        Vec3 riseCarry = MC.player.getDeltaMovement();
        boolean gateColumn = isGrimFamily() && grimFootingSurfaceY != Integer.MIN_VALUE;

        boolean riseAllowed = grimRiseAllowed(jumping);
        boolean preferHeight = gateRise && riseAllowed;
        int footingRow = grimOracleFootingRow();
        int planTick = AutismSharedState.get().getClientTickCounter();
        PlacementTarget firstPlan = null;
        for (BlockPos offset : offsets) {
            BlockPos candidate = base.offset(offset);

            if (offset.getY() > 0 && MC.player.onGround() && !jumping) { gateAbove++; continue; }

            if (gateRise && offset.getY() > 0 && grimBoxOverColumn(riseFrom, candidate)
                && !grimRiseFloorReady(grimLaneStep(), candidate.below(),
                    advancesCourse(riseFrom, laneDirection, candidate), this::solidAt)) {
                gateFloor++;
                continue;
            }

            if (gateRise && grimCellBehind(riseFrom, laneDirection, candidate, GRIM_BEHIND_MARGIN)) {
                gateBehind++;
                continue;
            }

            if (gateColumn && candidate.getY() > grimFootingSurfaceY
                && grimBoxOverColumn(riseFrom, candidate)
                && !grimRiseColumnHeld(riseFrom, riseCarry, candidate,
                    GRIM_RISE_COLUMN_LEAD_TICKS, GRIM_RISE_COLUMN_MIN_OVERLAP)) {
                gateColumnHold++;
                continue;
            }

            if (grimRiseVetoApplies(preferHeight, candidate.getY(), footingRow)
                && !heldArcLandsOnColumn(riseFrom, riseCarry, candidate)
                && !footprintOverlapsColumn(riseFrom, riseCarry, candidate)) {
                gateRiseVeto++;
                continue;
            }

            if (isGrimFamily()
                && grimFloorGateRefuses(
                    candidate.getY(), grimBuiltFloorRow(), grimDescendingBelowFooting())) {
                gateBelow++;
                continue;
            }
            Direction pinnedFace = null;
            if (rowLock != null) {
                if (!rowLock.allows(candidate, riseAllowed)) { gateRowLock++; continue; }
                pinnedFace = rowLock.pinnedFace(candidate);
            }
            if (MC.level.isOutsideBuildHeight(candidate)) { gateHeight++; continue; }
            if (grimCellOnCooldown(candidate)) { gateDead++; continue; }
            BlockState candidateState = MC.level.getBlockState(candidate);
            if (isSolidSupport(candidateState, candidate)) { solidSkips++; continue; }

            boolean replaceExisting = !candidateState.isAir() && candidateState.getFluidState().isEmpty();
            if (replaceExisting && !canBeReplacedWith(candidateState, candidate, stack)) {
                replaceSkips++;
                continue;
            }

            PlacementTarget target = planTargetForCandidate(
                candidate, plannedPosition, replaceExisting,
                laneDirection, jumping, pinnedFace);
            if (target != null) {
                if (!preferHeight) return target;

                if (target.placedBlock().getY() > footingRow) return target;
                if (firstPlan == null) firstPlan = target;
            }
            if (firstPlanDetail == null) {
                firstPlanDetail = candidate.toShortString() + "{" + grimPlanDetail.toString().trim() + "}";
            }
            planNulls++;
        }
        if (firstPlan != null) return firstPlan;
        grimLastPlanFail = "base=" + base.toShortString() + " solid:" + solidSkips
            + " repl:" + replaceSkips
            + " gate[abv:" + gateAbove + " flr:" + gateFloor + " bhd:" + gateBehind
            + " col:" + gateColumnHold + " veto:" + gateRiseVeto + " lock:" + gateRowLock
            + " hgt:" + gateHeight + " dead:" + gateDead + " blw:" + gateBelow + "]"
            + " null:" + planNulls
            + (grimLastPlanFail.isEmpty() ? "" : " [" + grimLastPlanFail + "]")
            + (firstPlanDetail == null ? "" : " first=" + firstPlanDetail);
        return null;
    }

    static List<BlockPos> grimCandidateOffsets() {
        return NORMAL_OFFSETS;
    }

    static double grimFaceLaneDot(Direction face, Vec3 lane) {
        if (face == null || lane == null) return 0.0D;
        double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
        if (length < 1.0E-6D) return 0.0D;
        return (face.getStepX() * lane.x + face.getStepZ() * lane.z) / length;
    }

    private static boolean isGrimDiagonalDirection(Vec3 direction) {
        return direction != null
            && horizontalStep(direction.x) != 0
            && horizontalStep(direction.z) != 0;
    }

    private static int horizontalStep(double component) {
        if (component > 1.0E-6D) return 1;
        if (component < -1.0E-6D) return -1;
        return 0;
    }

    private List<BlockPos> orderedOffsetsForWorld(
        List<BlockPos> source, BlockPos base, Vec3 predictedPosition, MovementLine optimalLine
    ) {
        List<BlockPos> ordered = new ArrayList<>(source);
        ordered.sort(Comparator

            .comparingInt(ScaffoldModule::grimCandidatePriority)
            .thenComparingDouble((BlockPos offset) -> blockDistancePriority(
                base.offset(offset), predictedPosition, optimalLine))
            .thenComparingDouble(offset -> offset.distSqr(BlockPos.ZERO))
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
        return ordered;
    }

    static int grimCandidatePriority(BlockPos offset) {
        return BlockPos.ZERO.equals(offset) ? 0 : 1;
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

    private PlacementTarget planTargetForCandidate(
        BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting,
        Vec3 laneDirection, boolean jumping
    ) {
        return planTargetForCandidate(
            candidate, plannedPosition, replaceExisting, laneDirection, jumping,
            null);
    }

    private PlacementTarget planTargetForCandidate(
        BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting,
        Vec3 laneDirection, boolean jumping, Direction onlyFace
    ) {
        return planTargetForCandidate(candidate, plannedPosition, replaceExisting,
            laneDirection, jumping, onlyFace, false);
    }

    private PlacementTarget planTargetForCandidate(
        BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting,
        Vec3 laneDirection, boolean jumping, Direction onlyFace, boolean planeLead
    ) {
        TargetPlan bestPlan = null;
        double bestFaceAngle = Double.POSITIVE_INFINITY;

        grimPlanDetail.setLength(0);

        if (!replaceExisting && MC.player != null) {
            boolean catchMode = grimFallingCatchPlan(candidate);
            if (!grimCellClearOfBody(MC.player.getBoundingBox(),
                MC.player.getDeltaMovement(), candidate, catchMode)) {
                grimLastPlanFail = "inside-player";

                grimPlanDetail.append("ip:").append(grimCellClearReason(
                    MC.player.getBoundingBox(), MC.player.getDeltaMovement(),
                    candidate, catchMode));
                return null;
            }
        }
        Vec3 aimPosition = grimPlacementAimPosition(
            plannedPosition, MC.player.position(), isGrimFamily());
        Vec3 aimEye = aimPosition.add(0.0D, MC.player.getEyeHeight(), 0.0D);

        Vec3 selectionPosition = grimFaceSelectionPosition(plannedPosition, aimPosition);
        Vec3 selectionEye = selectionPosition.add(0.0D, MC.player.getEyeHeight(), 0.0D);

        Vec3 planeLeadEye = null;
        if (planeLead && isGrimFamily() && !MC.player.onGround()) {
            Vec3 travel = MC.player.getDeltaMovement();
            planeLeadEye = aimEye.add(travel.x * GRIM_FACE_PLANE_LEAD_TICKS, 0.0D,
                travel.z * GRIM_FACE_PLANE_LEAD_TICKS);
        }

        AutismRotationUtil.Rotation from = isGrimFamily() ? grimRestPoseGoal() : serverRotation();

        boolean pinnable = isGrimFamily() && MC.player != null;
        Vec3 pinEye = pinnable ? MC.player.getEyePosition().add(grimLeadStep()) : null;
        Vec3 pinLead = pinnable ? grimLeadStep() : Vec3.ZERO;
        float pinYaw = pinnable ? grimSteeredPostureYaw() : 0.0F;

        boolean heldPinStillPins = pinnable && grimPinFace != null
            && grimHeldPinOwnsCandidate(grimPinSupport, grimPinFace, candidate)
            && grimFacePinsSoon(pinEye, pinLead, grimPinSupport, grimPinFace, pinYaw, true,
                GRIM_PIN_LOOKAHEAD_TICKS);
        boolean bestFacePins = false;
        FaceSample bestSample = null;

        GrimRowLock visibilityLock = isGrimFamily() ? grimActiveRowLock() : null;
        boolean lockPinnedFace = visibilityLock != null && onlyFace != null
            && onlyFace == visibilityLock.pinnedFace(candidate);
        boolean requireExactVisibility = !lockPinnedFace && grimExactCornerVisibility(
            isGrimFamily(), grimEdgeSneakActive, MC.player.onGround(), jumping);
        double visibilityReach = requireExactVisibility
            ? Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange())
            : 0.0D;

        for (Direction face : Direction.values()) {
            if (onlyFace != null && face != onlyFace) continue;
            char faceCode = face.getName().charAt(0);
            BlockPos supportPos = replaceExisting ? candidate : candidate.relative(face.getOpposite());
            if (MC.level.isOutsideBuildHeight(supportPos)) continue;
            BlockState supportState = MC.level.getBlockState(supportPos);
            if (!replaceExisting && supportState.canBeReplaced()) {
                grimLastPlanFail = "no-support(" + face + ")";
                grimPlanDetail.append(faceCode).append(":ns ");
                continue;
            }

            if (isGrimFamily() && face.getAxis().isHorizontal() && laneDirection != null
                && !isGrimDiagonalDirection(laneDirection)
                && grimFaceLaneDot(face, laneDirection) <= -0.5D) {
                grimLastPlanFail = "reach-around(" + face + ")";
                grimPlanDetail.append(faceCode).append(":ra ");
                continue;
            }

            Vec3 faceCenter = Vec3.atCenterOf(supportPos).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
            );
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());

            boolean leadPasses = planeLeadEye != null
                && planeLeadEye.subtract(faceCenter).dot(normal) >= MIN_FACE_DISTANCE;
            if (!leadPasses
                && selectionEye.subtract(faceCenter).dot(normal) < MIN_FACE_DISTANCE
                && aimEye.subtract(faceCenter).dot(normal) < MIN_FACE_DISTANCE) {
                grimLastPlanFail = "normal-side(" + face + ")";
                grimPlanDetail.append(faceCode).append(":sd ");
                continue;
            }

            if (isGrimFamily() && grimSelfOccludedThroughApproach(supportPos, face)) {
                grimLastPlanFail = "self-occ(" + face + ")";
                grimPlanDetail.append(faceCode).append(":so ");
                continue;
            }

            boolean facePins = pinnable && grimFacePinsSoon(pinEye, pinLead, supportPos, face,
                pinYaw, grimPinHeldFor(supportPos, face), GRIM_PIN_LOOKAHEAD_TICKS);

            if (facePins
                && grimLatchSuppressesPin(heldPinStillPins, grimPinHeldFor(supportPos, face))) {
                facePins = false;
            }
            AutismRotationUtil.Rotation faceRotation = AutismRotationUtil.lookingAt(faceCenter, aimEye);
            double faceAngle = rotationAngle(from, faceRotation);

            if (isGrimFamily() && face.getAxis().isHorizontal()
                && aimPosition.y > supportPos.getY() + 1.0D + GRIM_FACE_OVERHEAD_MARGIN
                && grimFaceOutOfReachThroughApproach(
                    aimEye, supportPos, face, faceRotation.yaw())) {
                grimLastPlanFail = "pitch-cap(" + face + ")";
                grimPlanDetail.append(faceCode).append(":pc ");
                continue;
            }
            if (!betterFace(facePins, faceAngle, bestFacePins, bestFaceAngle)) continue;

            VoxelShape shape = supportState.getShape(
                MC.level, supportPos, CollisionContext.of(MC.player));
            FaceSample planSample = null;
            for (AABB localBox : shape.toAabbs()) {
                FaceRect sampledFace = FaceRect.fromBox(localBox, face);
                FaceRect searchFace = sampledFace;
                if (searchFace.to().y >= 0.9D) {
                    FaceRect truncated = searchFace.truncateY(0.6D);
                    if (truncated.area() > GEOMETRY_EPSILON) searchFace = truncated;
                }

                Vec3 point = stabilizedPointOnFace(
                    searchFace, sampledFace, supportPos, aimEye, from, currentMovementLine);
                if (point == null) {
                    grimLastPlanFail = "no-point(" + face + ")";
                    grimPlanDetail.append(faceCode).append(":np ");
                    continue;
                }
                if (requireExactVisibility) {

                    Vec3 candidatePoint = point.add(
                        supportPos.getX(), supportPos.getY(), supportPos.getZ());
                    AutismRotationUtil.Rotation visibleRotation =
                        AutismRotationUtil.lookingAt(candidatePoint, aimEye);
                    BlockHitResult visibleHit = raytrace(visibleRotation, visibilityReach);
                    if (visibleHit == null
                        || !visibleHit.getBlockPos().equals(supportPos)
                        || visibleHit.getDirection() != face) {
                        grimLastPlanFail = "visibility(" + face + ")";
                        grimPlanDetail.append(faceCode).append(":vs ");
                        continue;
                    }
                }
                FaceSample sample = new FaceSample(sampledFace, point, face);
                if (planSample == null || compareFaceSamples(sample, planSample) > 0) {
                    planSample = sample;
                }
            }
            if (planSample == null) continue;
            bestFaceAngle = faceAngle;
            bestFacePins = facePins;
            bestPlan = new TargetPlan(supportPos.immutable(), face);
            bestSample = planSample;
        }

        if (bestPlan == null || bestSample == null) return null;
        Vec3 worldPoint = bestSample.point().add(
            bestPlan.supportBlock().getX(), bestPlan.supportBlock().getY(), bestPlan.supportBlock().getZ());
        GrimRowLock goalLock = isGrimFamily() ? grimActiveRowLock() : null;
        Direction goalPinnedFace = goalLock == null ? null : goalLock.pinnedFace(candidate);
        AutismRotationUtil.Rotation rotation;
        if (isGrimFamily() && MC.player != null && !MC.player.onGround()) {

            Vec3 airEye = MC.player.getEyePosition().add(grimLeadStep());
            float airPitch = Float.NaN;
            float airYaw = pinYaw;
            if (bestFacePins && bestPlan.face().getAxis().isHorizontal()) {
                grimLastGoalEye = "air-pin";

                if (grimLaneStep() != COURSE_STEP_UNSET) {
                    float landing = grimLandingNudge(MC.player.getEyePosition(), grimLeadStep(),
                        bestPlan.supportBlock(), bestPlan.face(), airYaw, grimLaneStepYaw());
                    if (!Float.isNaN(landing)) {
                        airYaw = landing;
                        grimLastGoalEye = "air-land";
                    }

                    float flip = grimGridFlipYaw(MC.player.getEyePosition(), grimLeadStep(),
                        bestPlan.supportBlock(), bestPlan.face(), airYaw);
                    if (!Float.isNaN(flip)) {
                        airYaw = flip;
                        grimLastGoalEye = "air-flip";
                    }
                }

                airPitch = grimTwoEyeCrossingPitch(airEye, MC.player.getEyePosition(),
                    bestPlan.supportBlock(), bestPlan.face(), airYaw, GRIM_PLACE_MAX_PITCH_HARD);
            } else if (bestPlan.face() == Direction.UP) {

                airPitch = grimTopCrossingPitch(airEye, bestPlan.supportBlock(), pinYaw);
                if (Float.isNaN(airPitch)) {

                    airPitch = grimTopCrossingPitch(
                        MC.player.getEyePosition(), bestPlan.supportBlock(), pinYaw);
                }
                if (!Float.isNaN(airPitch)) {
                    grimLastGoalEye = "air-top";
                } else if (grimLaneStep() != COURSE_STEP_UNSET) {

                    float topFlip = grimTopGridFlipYaw(MC.player.getEyePosition(),
                        grimLeadStep(), bestPlan.supportBlock(), pinYaw);
                    if (!Float.isNaN(topFlip)) {
                        float flipPitch = grimTopCrossingPitch(
                            airEye, bestPlan.supportBlock(), topFlip);
                        if (Float.isNaN(flipPitch)) {
                            flipPitch = grimTopCrossingPitch(
                                MC.player.getEyePosition(), bestPlan.supportBlock(), topFlip);
                        }
                        if (!Float.isNaN(flipPitch)) {
                            airYaw = topFlip;
                            airPitch = flipPitch;
                            grimLastGoalEye = "air-flip";
                        }
                    }
                }
            }
            if (!Float.isNaN(airPitch)) {
                rotation = new AutismRotationUtil.Rotation(airYaw, airPitch);
            } else {
                if (bestPlan.face().getAxis().isHorizontal()) {

                    double lowY = bestPlan.supportBlock().getY() + bestSample.face().from().y + 0.15D;
                    if (lowY < worldPoint.y) worldPoint = new Vec3(worldPoint.x, lowY, worldPoint.z);
                }
                grimLastGoalEye = "air-direct";
                rotation = grimAirbornePredictedGoal(
                    AutismRotationUtil.lookingAt(worldPoint, MC.player.getEyePosition()), worldPoint);

                double bearingRun = Math.hypot(
                    worldPoint.x - MC.player.getEyePosition().x,
                    worldPoint.z - MC.player.getEyePosition().z);
                float chosen = bestPlan.face().getAxis().isHorizontal()
                        && bearingRun >= GRIM_FREE_AIM_BEARING_MIN_RUN
                    ? grimSnapFreeAimYaw(rotation.yaw())
                    : pinYaw;

                if (grimLaneStep() != COURSE_STEP_UNSET) {
                    float landing = grimLandingNudge(MC.player.getEyePosition(), grimLeadStep(),
                        bestPlan.supportBlock(), bestPlan.face(), chosen, grimLaneStepYaw());
                    if (!Float.isNaN(landing)) {
                        chosen = landing;
                        grimLastGoalEye = "air-land";
                    }

                    float flip = grimGridFlipYaw(MC.player.getEyePosition(), grimLeadStep(),
                        bestPlan.supportBlock(), bestPlan.face(), chosen);
                    if (!Float.isNaN(flip)) {
                        chosen = flip;
                        grimLastGoalEye = "air-flip";

                        float flipPitch = grimTwoEyeCrossingPitch(airEye,
                            MC.player.getEyePosition(), bestPlan.supportBlock(),
                            bestPlan.face(), chosen, GRIM_PLACE_MAX_PITCH_HARD);
                        if (!Float.isNaN(flipPitch)) {
                            rotation = new AutismRotationUtil.Rotation(
                                rotation.yaw(), flipPitch);
                        }
                    }
                }

                rotation = new AutismRotationUtil.Rotation(chosen, rotation.pitch());
            }
        } else if (isGrimFamily() && MC.player != null && bestPlan.face().getAxis().isHorizontal()) {

            Vec3 leadEye = pinEye == null ? MC.player.getEyePosition().add(grimLeadStep()) : pinEye;
            boolean postured = false;
            float goalYaw = 0.0F;
            if (bestFacePins) {
                grimLastGoalEye = MC.player.onGround() ? "lead-pin" : "air-pin";
                goalYaw = pinYaw;
                postured = true;
            }
            if (!postured) {
                Vec3 goalEye = grimGoalEyeFor(bestPlan.supportBlock(), bestPlan.face(), selectionEye, laneDirection);
                AutismRotationUtil.Rotation pointGoal = AutismRotationUtil.lookingAt(worldPoint, goalEye);
                goalYaw = grimSnapFreeAimYaw(grimAirbornePredictedGoal(pointGoal, worldPoint).yaw());
            }
            rotation = new AutismRotationUtil.Rotation(goalYaw, grimTwoEyeCrossingPitch(
                MC.player.getEyePosition(), leadEye,
                bestPlan.supportBlock(), bestPlan.face(), goalYaw, GRIM_PLACE_PITCH_PARK));
        } else if (bestFacePins && bestPlan.face() == Direction.UP
            && !Float.isNaN(grimTopCrossingPitch(
                pinEye == null ? MC.player.getEyePosition().add(grimLeadStep()) : pinEye,
                bestPlan.supportBlock(), pinYaw))) {

            grimLastGoalEye = MC.player.onGround() ? "lead-top" : "air-top";
            rotation = new AutismRotationUtil.Rotation(pinYaw, grimTopCrossingPitch(
                pinEye == null ? MC.player.getEyePosition().add(grimLeadStep()) : pinEye,
                bestPlan.supportBlock(), pinYaw));
        } else {

            Vec3 goalEye = grimGoalEyeFor(bestPlan.supportBlock(), bestPlan.face(), selectionEye, laneDirection);
            rotation = AutismRotationUtil.lookingAt(worldPoint, goalEye);
            rotation = grimAirbornePredictedGoal(rotation, worldPoint);

            if (goalPinnedFace != null) {
                float postureYaw = grimSteeredPostureYaw();
                if (bestPlan.face() == Direction.UP && MC.player != null) {

                    Vec3 topEye = pinEye == null
                        ? MC.player.getEyePosition().add(grimLeadStep()) : pinEye;
                    float topPitch = grimTopCrossingPitch(
                        topEye, bestPlan.supportBlock(), postureYaw);
                    if (Float.isNaN(topPitch)) {
                        grimPlanDetail.append("u:tx ");
                        return null;
                    }
                    grimLastGoalEye = MC.player.onGround() ? "lead-top" : "air-top";
                    rotation = new AutismRotationUtil.Rotation(postureYaw, topPitch);
                } else {
                    rotation = new AutismRotationUtil.Rotation(postureYaw, rotation.pitch());
                }
            } else {

                rotation = new AutismRotationUtil.Rotation(
                    grimSnapFreeAimYaw(rotation.yaw()), rotation.pitch());
            }
        }
        if (isGrimFamily()) {

            Vec3 clampEye = MC.player.getEyePosition();
            Vec3 clampLead = clampEye.add(grimLeadStep());
            double[] window = MC.player.onGround()
                ? grimTwoEyeCrossingWindow(clampEye, clampLead,
                    bestPlan.supportBlock(), bestPlan.face(), rotation.yaw())
                : grimTwoEyeCrossingWindow(clampLead, clampEye,
                    bestPlan.supportBlock(), bestPlan.face(), rotation.yaw());
            double windowLow = window == null ? Double.NaN : window[0];
            double windowHigh = window == null ? Double.NaN : window[1];
            float goalPitch = grimPlacementPitchGoal(rotation.pitch(), windowLow, windowHigh);

            float dodged = grimIntaveRotationPitchGoal(goalPitch, windowLow, bestPlan.face(),
                MC.player.onGround() ? clampEye : clampLead, grimLeadStep(),
                rotation.yaw(), bestPlan.supportBlock());
            double flickHigh = dodged < goalPitch
                ? (Double.isNaN(windowHigh) ? dodged : Math.min(windowHigh, dodged))
                : windowHigh;
            goalPitch = grimIntaveFlickPitchGoal(
                dodged, windowLow, flickHigh, bestPlan.face(), candidate.immutable());
            rotation = new AutismRotationUtil.Rotation(rotation.yaw(), goalPitch);
        }
        BlockHitResult plannedHit = new BlockHitResult(
            worldPoint, bestPlan.face(), bestPlan.supportBlock(), false);
        return new PlacementTarget(
            bestPlan.supportBlock(), candidate.immutable(), bestPlan.face(), plannedHit,
            rotation, bestPlan.supportBlock().getY() + bestSample.face().from().y);
    }

    private boolean grimPlanningJump() {

        return grimJumpKeyHeld()
            || (grimLaunchReservationAirborne && MC.player != null && !MC.player.onGround());
    }

    private boolean solidAt(BlockPos pos) {
        if (MC.level == null || MC.level.isOutsideBuildHeight(pos)) return false;
        return isSolidSupport(MC.level.getBlockState(pos), pos);
    }

    private boolean grimTrustedSolidAt(BlockPos pos) {
        if (!solidAt(pos)) {
            grimUntrustedPredictions.remove(pos);
            return false;
        }
        return !grimUntrustedPredictions.containsKey(pos);
    }

    private boolean grimHasUntrustedPrediction() {
        Iterator<BlockPos> iterator = grimUntrustedPredictions.keySet().iterator();
        while (iterator.hasNext()) {
            BlockPos cell = iterator.next();
            if (!solidAt(cell)) iterator.remove();
        }
        return !grimUntrustedPredictions.isEmpty();
    }

    static boolean grimRiseFloorReady(int laneStep, BlockPos below, Predicate<BlockPos> solid) {
        return grimRiseFloorReady(laneStep, below, false, solid);
    }

    static boolean grimRiseFloorReady(int laneStep, BlockPos below, boolean aheadRise, Predicate<BlockPos> solid) {
        if (laneStep == COURSE_STEP_UNSET || below == null) return true;
        double yaw = Math.toRadians(compassStepYaw(laneStep));
        int dx = (int) Math.round(-Math.sin(yaw));
        int dz = (int) Math.round(Math.cos(yaw));
        if (dx == 0 && dz == 0) return true;
        if (!solid.test(below.offset(dx, 0, dz))) return false;
        if (dx == 0 || dz == 0) return true;

        if (aheadRise) return true;
        return solid.test(below.offset(dx, 0, 0)) || solid.test(below.offset(0, 0, dz));
    }

    static boolean grimRiseVetoApplies(boolean preferHeight, int candidateY, int footingRow) {
        return preferHeight && candidateY > footingRow;
    }

    private int grimFootingSurfaceY = Integer.MIN_VALUE;

    private int grimLastRowGainTick = Integer.MIN_VALUE;

    private int grimAirborneBuiltRow = Integer.MIN_VALUE;

    private void updateGrimFootingSurface() {
        if (MC.player != null && MC.player.onGround()) {
            int row = grimFootingRowUnderFeet();
            if (grimFootingSurfaceY != Integer.MIN_VALUE && row > grimFootingSurfaceY) {
                grimLastRowGainTick = AutismSharedState.get().getClientTickCounter();
            } else if (grimFootingSurfaceY != Integer.MIN_VALUE && row < grimFootingSurfaceY) {

                grimLastRowGainTick = Integer.MIN_VALUE;
            }
            grimFootingSurfaceY = row;
            grimAirborneBuiltRow = Integer.MIN_VALUE;
        }
    }

    private boolean grimDescendingBelowFooting() {
        if (!grimCourseFrozen()) return false;
        return grimFootingSurfaceY == Integer.MIN_VALUE
            || MC.player.getY() < grimFootingSurfaceY + 1.0D;
    }

    static boolean grimFeetCrossFootingSoon(double feet, double vy, int footingRow, int lookahead) {
        double plane = footingRow + 1.0D;
        if (feet < plane) return true;
        for (int tick = 0; tick < lookahead; tick++) {
            feet += vy;
            vy = (vy - 0.08D) * 0.98D;
            if (feet < plane) return true;
        }
        return false;
    }

    private boolean grimDescendingBelowFootingSoon() {
        if (grimDescendingBelowFooting()) return true;
        if (!grimCourseFrozen()) return false;
        return grimFeetCrossFootingSoon(MC.player.getY(), MC.player.getDeltaMovement().y,
            grimFootingSurfaceY, GRIM_BODY_CLEAR_LOOKAHEAD_TICKS);
    }

    private boolean grimFallingCatchPlan(BlockPos candidate) {
        if (!isGrimFamily() || MC.player == null || MC.player.onGround()) return false;
        if (MC.player.getDeltaMovement().y >= 0.0D) return false;
        if (candidate.getY() + 1.0D > MC.player.getBoundingBox().minY) return false;
        if (grimDescendingBelowFooting()) return true;
        Vec3 landing = grimDescentCrossing(MC.player.position(), MC.player.getDeltaMovement(),
            candidate.getY() + 1.0D, GRIM_BODY_CLEAR_LOOKAHEAD_TICKS);
        return landing != null
            && Mth.floor(landing.x) == candidate.getX()
            && Mth.floor(landing.z) == candidate.getZ();
    }

    private static double grimAxisOverlap(double centre, int cell) {
        double low = Math.max(centre - GRIM_BOX_HALF_WIDTH, cell);
        double high = Math.min(centre + GRIM_BOX_HALF_WIDTH, cell + 1.0D);
        return Math.max(0.0D, high - low);
    }

    static boolean grimHoldRefreshBlocked(int servedTick, int now, BlockPos held, BlockPos noted) {
        return servedTick == now && held != null && held.equals(noted);
    }

    private void grimNoteAimCommit(PlacementTarget target) {
        if (target == null) return;
        int tick = AutismSharedState.get().getClientTickCounter();

        if (grimHoldRefreshBlocked(grimAimHoldServedTick, tick,
            grimAimHoldTarget == null ? null : grimAimHoldTarget.placedBlock(),
            target.placedBlock())) {
            return;
        }
        grimAimHoldTarget = target;
        grimAimHoldTick = tick;
    }

    static boolean grimAimHoldApplies(
        int heldTick, int now, int maxTicks, boolean cellOpen, boolean supported) {
        if (heldTick < 0) return false;
        int age = now - heldTick;
        return age >= 0 && age <= maxTicks && cellOpen && supported;
    }

    static boolean grimHoldRowAllowed(int heldRow, int footingRow) {
        return footingRow == Integer.MIN_VALUE || heldRow >= footingRow;
    }

    static int grimHoldFloorRow(int footingRow, int lastPlacedRow) {
        if (footingRow == Integer.MIN_VALUE) return lastPlacedRow;
        if (lastPlacedRow == Integer.MIN_VALUE) return footingRow;
        return Math.max(footingRow, lastPlacedRow);
    }

    static boolean grimHoldBehindLane(
        Vec3 position, Vec3 laneDirection, BlockPos placed, boolean descending) {
        return !descending && grimCellBehind(position, laneDirection, placed, GRIM_BEHIND_MARGIN);
    }

    private PlacementTarget grimAimCommitHold() {
        PlacementTarget held = grimAimHoldTarget;
        if (held == null || MC.level == null) return null;
        BlockPos placed = held.placedBlock();
        BlockPos support = held.supportBlock();
        if (MC.level.isOutsideBuildHeight(placed) || MC.level.isOutsideBuildHeight(support)) {
            grimAimHoldTarget = null;
            return null;
        }
        BlockPos lastPlaced = lastPlacedBlocks.peekLast();
        if (!grimHoldRowAllowed(placed.getY(), grimHoldFloorRow(grimOracleFootingRow(),
            lastPlaced == null ? Integer.MIN_VALUE : lastPlaced.getY()))) {
            grimAimHoldTarget = null;
            return null;
        }
        if (MC.player != null && grimHoldBehindLane(MC.player.position(),
            currentMovementLine == null ? null : currentMovementLine.direction(),
            placed, grimDescendingBelowFooting())) {
            grimAimHoldTarget = null;
            return null;
        }

        if (MC.player != null && !grimCellClearOfBody(
            MC.player.getBoundingBox(), MC.player.getDeltaMovement(), placed,
            grimFallingCatchPlan(placed))) {
            grimAimHoldTarget = null;
            return null;
        }
        if (grimCellOnCooldown(placed)) {
            grimAimHoldTarget = null;
            return null;
        }

        if (grimRiseDropApplies(held)) {
            grimAimHoldTarget = null;
            return null;
        }

        if (MC.player != null
            && !grimLegRayLands(held, MC.player.getEyePosition(), grimLeadStep())) {
            grimAimHoldTarget = null;
            return null;
        }
        boolean cellOpen = MC.level.getBlockState(placed).canBeReplaced();
        boolean supported = isSolidSupport(MC.level.getBlockState(support), support);
        if (!grimAimHoldApplies(
            grimAimHoldTick, AutismSharedState.get().getClientTickCounter(),
            GRIM_AIM_HOLD_MAX_TICKS, cellOpen, supported)) {
            grimAimHoldTarget = null;
            return null;
        }
        grimAimHoldServed = true;
        grimAimHoldServedTick = AutismSharedState.get().getClientTickCounter();

        PlacementTarget resolved = grimReSolveHeldTarget(held);
        return resolved == null ? held : resolved;
    }

    static boolean grimPacesAsRiser(BlockPos placed, BlockPos support, BlockPos upFaceSwapCell) {
        return placed.getY() != support.getY() && !placed.equals(upFaceSwapCell);
    }

    static boolean grimUpFaceUpgradeApplies(
        Direction heldFace, boolean replaceExisting, boolean fullCubeBelow
    ) {
        if (heldFace == null || !heldFace.getAxis().isHorizontal()) return false;

        return replaceExisting || fullCubeBelow;
    }

    private PlacementTarget grimReSolveHeldTarget(PlacementTarget held) {
        if (MC.player == null || MC.level == null) return null;
        BlockPos placed = held.placedBlock();
        BlockState state = MC.level.getBlockState(placed);
        boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
        String planFail = grimLastPlanFail;
        PlacementTarget upgraded = grimUpFaceUpgrade(held);
        if (upgraded != held) {
            grimAimHoldTarget = upgraded;
            return upgraded;
        }
        PlacementTarget fresh = planTargetForCandidate(placed,
            predictedPlacementPosition(currentMovementLine), replaceExisting,
            currentMovementLine == null ? null : currentMovementLine.direction(),
            grimPlanningJump(), held.face());

        grimLastPlanFail = planFail;
        return fresh != null && fresh.placedBlock().equals(placed)
            && fresh.face() == held.face() ? fresh : null;
    }

    static boolean grimCellBehind(Vec3 position, Vec3 laneDirection, BlockPos cell, double margin) {
        if (position == null || laneDirection == null || cell == null) return false;
        double lx = laneDirection.x;
        double lz = laneDirection.z;
        double length = Math.sqrt(lx * lx + lz * lz);
        if (length <= 1.0E-6D) return false;
        double along = (cell.getX() + 0.5D - position.x) * (lx / length)
            + (cell.getZ() + 0.5D - position.z) * (lz / length);
        return along < -margin;
    }

    static boolean grimBoxOverColumn(Vec3 position, BlockPos column) {
        return position != null && column != null
            && grimAxisOverlap(position.x, column.getX()) > 0.0D
            && grimAxisOverlap(position.z, column.getZ()) > 0.0D;
    }

    static boolean grimRiseColumnHeld(
        Vec3 position, Vec3 velocity, BlockPos column, int leadTicks, double minOverlap
    ) {
        if (position == null || column == null) return true;
        double x = position.x + (velocity == null ? 0.0D : velocity.x) * leadTicks;
        double z = position.z + (velocity == null ? 0.0D : velocity.z) * leadTicks;
        return grimAxisOverlap(x, column.getX()) >= minOverlap
            && grimAxisOverlap(z, column.getZ()) >= minOverlap;
    }

    private boolean grimPinHeldFor(BlockPos support, Direction face) {
        return grimPinFace == face && support != null && support.equals(grimPinSupport);
    }

    static boolean grimHeldPinOwnsCandidate(BlockPos pinSupport, Direction pinFace,
        BlockPos candidate) {
        return pinSupport != null && pinFace != null
            && pinSupport.relative(pinFace).equals(candidate);
    }

    static boolean grimLatchSuppressesPin(boolean heldPinStillPins, boolean heldForThisFace) {
        return heldPinStillPins && !heldForThisFace;
    }

    static boolean grimFacePins(Vec3 pinEye, BlockPos support, Direction face, float pinYaw, boolean holding) {
        if (face.getAxis().isHorizontal()) {
            return grimCrossingLandsOnFace(pinEye, support, face, pinYaw, holding);
        }
        return face == Direction.UP && grimTopCrossingLandsOnFace(pinEye, support, pinYaw, holding);
    }

    static boolean betterFace(boolean pins, double angle, boolean bestPins, double bestAngle) {
        if (pins != bestPins) return pins;
        return angle < bestAngle;
    }

    private void grimNotePin(PlacementTarget target) {
        grimTraceCrossing = target == null || MC.player == null ? Double.NaN
            : grimCrossingFraction(MC.player.getEyePosition().add(grimLeadStep()),
                target.supportBlock(), target.face(), grimSteeredPostureYaw());
        if (target == null || MC.player == null || !isGrimFamily()) {
            grimPinSupport = null;
            grimPinFace = null;
            return;
        }
        if (grimPinIsStale(target)
            || !grimFacePinsSoon(
                MC.player.getEyePosition().add(grimLeadStep()), grimLeadStep(),
                target.supportBlock(), target.face(), grimSteeredPostureYaw(),
                grimPinHeldFor(target.supportBlock(), target.face()),
                GRIM_PIN_LOOKAHEAD_TICKS)) {
            grimPinSupport = null;
            grimPinFace = null;
            return;
        }
        grimPinSupport = target.supportBlock();
        grimPinFace = target.face();
    }

    private boolean grimPinIsStale(PlacementTarget target) {
        if (target == null) {
            grimStaleSupport = null;
            grimStaleFace = null;
            grimStaleTicks = 0;
            return false;
        }
        boolean same = grimStaleFace == target.face() && target.supportBlock().equals(grimStaleSupport);
        grimStaleSupport = target.supportBlock();
        grimStaleFace = target.face();
        grimStaleTicks = grimStaleCount(grimStaleTicks, same, grimTraceClickLands);
        return grimStaleTicks > GRIM_PIN_STALE_TICKS;
    }

    static int grimStaleCount(int previous, boolean sameTarget, boolean clickLanded) {
        if (clickLanded) return 0;
        return sameTarget ? previous + 1 : 1;
    }

    private double grimTraceCrossing = Double.NaN;

    private AutismRotationUtil.Rotation grimAirbornePredictedGoal(
        AutismRotationUtil.Rotation pushGoal, Vec3 worldPoint
    ) {
        if (!isGrimFamily() || MC.player == null || MC.player.onGround()) return pushGoal;
        Vec3 eye = MC.player.getEyePosition();
        AutismRotationUtil.Rotation direct = AutismRotationUtil.lookingAt(worldPoint, eye);
        AutismRotationUtil.Rotation predicted = AutismRotationUtil.lookingAt(
            worldPoint, eye.add(MC.player.getDeltaMovement()));
        return grimAirbornePredictionGate(pushGoal, direct, predicted);
    }

    static AutismRotationUtil.Rotation grimAirbornePredictionGate(
        AutismRotationUtil.Rotation pushGoal, AutismRotationUtil.Rotation direct,
        AutismRotationUtil.Rotation predicted
    ) {
        if (Math.abs(AutismRotationUtil.angleDifference(predicted.yaw(), pushGoal.yaw())) > 100.0F) {
            return pushGoal;
        }
        return new AutismRotationUtil.Rotation(
            predicted.yaw(), Math.min(predicted.pitch(), 90.0F));
    }

    private boolean place(PlacementTarget target, InteractionHand hand, ItemStack stack) {
        return place(target, hand, stack, true, true);
    }

    private boolean place(PlacementTarget target, InteractionHand hand, ItemStack stack,
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
            if (result instanceof InteractionResult.Fail) return false;
            if (result instanceof InteractionResult.Pass) {
                if (stack.isEmpty()) return false;
                InteractionResult itemResult = MC.gameMode.useItem(MC.player, hand);
                if (itemResult instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.CLIENT) MC.player.swing(hand);
                    MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
                }
                return false;
            }
            if (!result.consumesAction()) return false;
            if (result instanceof InteractionResult.Success success
                && success.swingSource() != InteractionResult.SwingSource.CLIENT) return true;

            MC.player.swing(hand);
            autismclient.util.AutismCpsTracker.recordRight();
            autismclient.util.AutismScaffoldPlaceRenderer.recordPlacement(target.placedBlock());
            trackSuccessfulPlacement(target.placedBlock(), placementLine, previousFallOff);
            boolean wasStackUsed = !stack.isEmpty()
                && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials());
            if (wasStackUsed) {
                MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
            return true;
        } finally {
            if (restoreClientRotation) {
                AutismRotationUtil.Rotation clientRotation = new AutismRotationUtil.Rotation(clientYaw, clientPitch);
                if (!sameRotation(serverRotation(), clientRotation)) sendRotation(clientRotation);
            }
        }
    }

    static Vec3 grimPlacementAimPosition(Vec3 predictedPosition, Vec3 actualPosition,
                                         boolean edgeHoldActive) {
        if (predictedPosition == null) return actualPosition;
        if (actualPosition == null) return predictedPosition;
        return edgeHoldActive ? actualPosition : predictedPosition;
    }

    static Vec3 grimFaceSelectionPosition(Vec3 predictedPosition, Vec3 aimPosition) {
        return predictedPosition == null ? aimPosition : predictedPosition;
    }

    private void trackSuccessfulPlacement(BlockPos placed, MovementLine line, Vec3 previousFallOff) {

        grimEdgeLockedLine = null;
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
        return grimClickRay(MC.player.getEyePosition(), rotation, reach, MC.level, MC.player);
    }

    static BlockHitResult grimClickRay(
        Vec3 eye, AutismRotationUtil.Rotation rotation, double reach, BlockGetter world, Entity entity
    ) {
        Vec3 look = lookVector(rotation);
        Vec3 end = eye.add(look.scale(reach));
        HitResult result = world.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
            entity == null ? CollisionContext.empty() : CollisionContext.of(entity)
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

    private BlockPos grimPlacedCellFor(BlockHitResult hit, ItemStack stack) {
        if (hit == null) return null;
        if (MC.player == null || stack == null || stack.isEmpty()) {
            return hit.getBlockPos().relative(hit.getDirection());
        }
        return new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, hit)
            .getClickedPos().immutable();
    }

    private boolean grimHitBuildsPlannedCell(BlockHitResult hit, PlacementTarget pending) {
        if (hit == null || pending == null) return false;
        return pending.placedBlock().equals(grimPlacedCellFor(hit, planningStack()));
    }

    static AABB grimSupportBox(BlockPos support) {
        if (support == null) return null;
        if (MC == null || MC.level == null) return grimUnitBox(support);
        BlockState state = MC.level.getBlockState(support);

        if (support.equals(grimSupportBoxKey) && state == grimSupportBoxState) {
            return grimSupportBoxValue;
        }
        AABB box = grimUnitBox(support);
        VoxelShape shape = state.getShape(MC.level, support,
            MC.player == null ? CollisionContext.empty() : CollisionContext.of(MC.player));
        if (!shape.isEmpty()) {
            box = shape.bounds().move(support.getX(), support.getY(), support.getZ());
        }
        grimSupportBoxKey = support.immutable();
        grimSupportBoxState = state;
        grimSupportBoxValue = box;
        return box;
    }

    private static BlockPos grimSupportBoxKey;
    private static BlockState grimSupportBoxState;
    private static AABB grimSupportBoxValue;

    static Vec3 grimFaceCentre(AABB support, Direction face) {
        Vec3 c = support.getCenter();
        return switch (face) {
            case DOWN -> new Vec3(c.x, support.minY, c.z);
            case UP -> new Vec3(c.x, support.maxY, c.z);
            case NORTH -> new Vec3(c.x, c.y, support.minZ);
            case SOUTH -> new Vec3(c.x, c.y, support.maxZ);
            case WEST -> new Vec3(support.minX, c.y, c.z);
            case EAST -> new Vec3(support.maxX, c.y, c.z);
        };
    }

    static AABB grimUnitBox(BlockPos support) {
        return new AABB(
            support.getX(), support.getY(), support.getZ(),
            support.getX() + 1.0D, support.getY() + 1.0D, support.getZ() + 1.0D);
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

            if (!autismclient.util.AutismHandArbiter.beginHandPacketGroup(id())) return null;
            try {
                if (bool("switch-back") && originalSlot < 0) originalSlot = selected;
                requestedSlot = slot;
                switchedToSlot = slot;
                selectionPending = true;
                AutismInputClicker.queueHotbarSlot(slot);
            } finally {
                autismclient.util.AutismHandArbiter.endHandPacketGroup(id());
            }
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

            if (autismclient.util.AutismHandArbiter.slotReserved(slot, id())) continue;
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
            switchedToSlot = -1;
            slotResetTicks = 0;
            return;
        }
        if (originalSlot < 0 || MC == null || MC.player == null) return;

        if (switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {
            originalSlot = -1;
            requestedSlot = -1;
            switchedToSlot = -1;
            slotResetTicks = 0;
            return;
        }
        if (slotResetTicks > 0) {
            slotResetTicks--;
            return;
        }
        int selected = MC.player.getInventory().getSelectedSlot();
        if (selected != originalSlot) {

            if (!autismclient.util.AutismHandArbiter.beginHandPacketGroup(id())) {
                slotResetTicks = 1;
                return;
            }
            try {
                requestedSlot = originalSlot;
                AutismInputClicker.queueHotbarSlot(originalSlot);
            } finally {
                autismclient.util.AutismHandArbiter.endHandPacketGroup(id());
            }
            return;
        }
        originalSlot = -1;
        requestedSlot = -1;
        switchedToSlot = -1;
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

        Vec3 direction = grimLaneStepDirection();

        SupportReference support = findSupportReferenceUnderPlayer();
        if (support == null) {
            if (!isGrimFamily()) return null;

            Vec3 position = MC.player.position();
            MovementLine previous = currentMovementLine;
            Vec3 anchor = grimCornerLineAnchor(
                position,
                previous == null ? null : previous.origin(),
                previous == null ? null : previous.direction(),
                direction,
                supportMissTicks <= 2 ? lastSupportPosition : null);
            return new MovementLine(new Vec3(anchor.x, position.y, anchor.z), direction);
        }
        lastSupportReference = support;

        MovementLine placedLine = fitLineThroughLastPlacements();
        Vec3 anchor;
        if (placedLine != null && placedLine.direction().dot(direction) >= 0.5D) {
            anchor = nearestPointOnLine(placedLine, MC.player.position());
        } else {

            anchor = new Vec3(
                support.blockPos().getX() + 0.5D,
                MC.player.getY(),
                support.blockPos().getZ() + 0.5D);
        }
        return new MovementLine(new Vec3(anchor.x, MC.player.getY(), anchor.z), direction);
    }

    private MovementLine retainGrimEdgeLine(MovementLine requested, boolean edgeActive) {
        MovementLine retained = grimEdgeIntentLine(
            requested, grimEdgeLockedLine, edgeActive, MC.player.getY());
        if (!edgeActive || requested == null) {
            grimEdgeLockedLine = null;
            return retained;
        }
        if (grimEdgeLockedLine == null) grimEdgeLockedLine = requested;
        return retained;
    }

    static MovementLine grimEdgeIntentLine(
        MovementLine requested, MovementLine locked, boolean edgeActive, double playerY
    ) {
        if (!edgeActive || requested == null) return requested;
        MovementLine selected = locked == null ? requested : locked;

        Vec3 origin = selected.origin();
        return new MovementLine(
            new Vec3(origin.x, playerY, origin.z),
            selected.direction());
    }

    static Vec3 grimCornerLineAnchor(
        Vec3 position, Vec3 previousOrigin, Vec3 previousDirection,
        Vec3 requestedDirection, BlockPos lastSupport
    ) {
        if (position == null) return Vec3.ZERO;
        if (previousOrigin != null && previousDirection != null && requestedDirection != null
            && previousDirection.dot(requestedDirection) >= 0.5D
            && previousDirection.lengthSqr() > 1.0E-12D) {
            double parameter = position.subtract(previousOrigin).dot(previousDirection)
                / previousDirection.lengthSqr();
            return previousOrigin.add(previousDirection.scale(parameter));
        }
        if (lastSupport != null) {
            return new Vec3(lastSupport.getX() + 0.5D, position.y, lastSupport.getZ() + 0.5D);
        }
        return position;
    }

    private MovementLine fitLineThroughLastPlacements() {
        if (lastPlacedBlocks.size() < 2) return null;
        Iterator<BlockPos> iterator = lastPlacedBlocks.descendingIterator();
        BlockPos last = iterator.next();
        Vec3 lastCenter = new Vec3(last.getX() + 0.5D, last.getY(), last.getZ() + 0.5D);
        while (iterator.hasNext()) {
            BlockPos previous = iterator.next();
            Vec3 previousCenter =
                new Vec3(previous.getX() + 0.5D, previous.getY(), previous.getZ() + 0.5D);
            Vec3 direction = new Vec3(
                lastCenter.x - previousCenter.x, 0.0D, lastCenter.z - previousCenter.z);
            if (direction.lengthSqr() <= 1.0E-8D) continue;
            return new MovementLine(
                previousCenter.add(lastCenter).scale(0.5D), direction.normalize());
        }
        return null;
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
            if (!isGrimFamily()) {
                lastSupportPosition = null;
                lastSupportReference = null;
                supportMissTicks = 0;
                supportMissClientTick = Integer.MIN_VALUE;
                return null;
            }
            int clientTick = AutismSharedState.get().getClientTickCounter();
            if (supportMissClientTick != clientTick) {
                supportMissClientTick = clientTick;
                supportMissTicks++;
            }
            if (supportMissTicks > 2) {
                lastSupportPosition = null;
                lastSupportReference = null;
            }
            return null;
        }

        SupportCandidate best = candidates.stream().min(ScaffoldModule::compareSupportCandidates).orElse(null);
        if (best == null) return null;
        supportMissTicks = 0;
        supportMissClientTick = Integer.MIN_VALUE;
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
        boolean nearEdge = isCloseToEdge(PREDICTION_CUTOFF_DISTANCE, playerPosition);
        Vec3 fallOff = findFallOffPosition(line);
        if (fallOff == null) return playerPosition;
        if (grimGapPredictionApplies(isGrimFamily(), nearEdge, grimEdgeSneakActive)) {

            return grimEdgePlacementPrediction(fallOff, line.direction());
        }
        if (nearEdge) return playerPosition;

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

    static boolean footprintOverlapsColumn(Vec3 position, Vec3 velocity, BlockPos cell) {
        if (position == null || velocity == null || cell == null) return false;
        double surfaceY = cell.getY() + 1.0D;
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        for (int tick = 0; tick < GRIM_LANDING_MAX_AIR_TICKS && y > surfaceY; tick++) {
            x += vx;
            y += vy;
            z += vz;
            double speed = Math.sqrt(vx * vx + vz * vz);
            double push = speed <= 1.0E-6D ? 0.0D : GRIM_AIR_COUNTER_IMPULSE / speed;
            vx = vx * GRIM_LANE_AIR_DRAG + vx * push;
            vz = vz * GRIM_LANE_AIR_DRAG + vz * push;
            vy = (vy - 0.08D) * 0.98D;
        }
        double overlapX = Math.min(x + GRIM_LANDING_HALF_WIDTH, cell.getX() + 1.0D)
            - Math.max(x - GRIM_LANDING_HALF_WIDTH, cell.getX());
        double overlapZ = Math.min(z + GRIM_LANDING_HALF_WIDTH, cell.getZ() + 1.0D)
            - Math.max(z - GRIM_LANDING_HALF_WIDTH, cell.getZ());
        return overlapX >= GRIM_LANDING_MIN_OVERLAP && overlapZ >= GRIM_LANDING_MIN_OVERLAP;
    }

    static boolean supportTooFarToHold(Vec3 position, BlockPos support, double maxDistance) {
        if (position == null || support == null) return false;
        double dx = support.getX() + 0.5D - position.x;
        double dz = support.getZ() + 0.5D - position.z;
        return dx * dx + dz * dz > maxDistance * maxDistance;
    }

    static Vec3 grimEdgePlacementPrediction(Vec3 fallOff, Vec3 direction) {
        if (fallOff == null) return null;
        if (direction == null) return fallOff;
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() <= 1.0E-12D) return fallOff;
        return fallOff.add(horizontal.normalize().scale(GRIM_EDGE_TARGET_EPSILON));
    }

    static Vec3 grimClickGoalEye(Vec3 playerEye, Vec3 faceCenter, Vec3 normal, double lead) {
        double playerPast = playerEye.subtract(faceCenter).dot(normal);
        if (playerPast >= lead) return playerEye;
        return playerEye.add(normal.scale(lead - playerPast));
    }

    private Vec3 grimGoalEyeFor(BlockPos support, Direction face, Vec3 selectionEye, Vec3 laneDirection) {
        if (!isGrimFamily() || MC.player == null) return selectionEye;
        if (laneDirection == null || laneDirection.horizontalDistanceSqr() <= 1.0E-12D) {
            return selectionEye;
        }
        Vec3 course = new Vec3(laneDirection.x, 0.0D, laneDirection.z).normalize();
        Vec3 leadEye = MC.player.getEyePosition().add(MC.player.getDeltaMovement());
        Vec3 goalEye = grimClickGoalEyeForFace(
            leadEye, selectionEye, support, face, course, GRIM_GOAL_EYE_LEAD);
        grimLastGoalEye = goalEye == leadEye ? "lead" : "push";
        return goalEye;
    }

    static Vec3 grimClickGoalEyeForFace(
        Vec3 playerEye, Vec3 selectionEye, BlockPos support, Direction face, Vec3 course, double lead
    ) {
        if (course == null) return selectionEye;
        if (face == Direction.UP) {
            Vec3 farEdge = Vec3.atCenterOf(support).add(course.scale(0.5D));
            farEdge = new Vec3(farEdge.x, playerEye.y, farEdge.z);
            return grimClickGoalEye(playerEye, farEdge, course, lead);
        }
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 faceCenter = Vec3.atCenterOf(support).add(normal.scale(0.5D));
        return grimClickGoalEye(playerEye, faceCenter, normal, lead);
    }

    static float grimCrossingPitch(Vec3 eye, BlockPos support, Direction face, float yaw) {
        return grimCrossingPitch(eye, support, face, yaw, GRIM_PLACE_PITCH_PARK);
    }

    static final float GRIM_PLACE_PITCH_PARK = GRIM_PLACE_MAX_PITCH + 0.3F;

    private static final double GRIM_FACE_SPAN_MARGIN = 0.06D;

    private static double[] grimFaceCrossingWindow(Vec3 eye, BlockPos support, Direction face, float yaw) {
        return grimFaceCrossingWindow(eye, grimSupportBox(support), face, yaw);
    }

    static double[] grimFaceCrossingWindow(Vec3 eye, AABB support, Direction face, float yaw) {
        if (support == null) return null;
        double past = grimEyePastPlane(eye, support, face);
        if (past <= 0.0D) return null;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return null;
        double run = past / toward;
        double margin = GRIM_FACE_SPAN_MARGIN * support.getYsize();
        double shallowest = Math.toDegrees(Math.atan2(eye.y - (support.maxY - margin), run));
        double steepest = Math.toDegrees(Math.atan2(eye.y - (support.minY + margin), run));
        return new double[] {shallowest, steepest, run};
    }

    static float grimTwoEyeCrossingPitch(Vec3 primaryEye, Vec3 secondaryEye, BlockPos support,
        Direction face, float yaw, float maxPitch) {
        return grimTwoEyeCrossingPitch(
            primaryEye, secondaryEye, grimSupportBox(support), face, yaw, maxPitch);
    }

    static float grimTwoEyeCrossingPitch(Vec3 primaryEye, Vec3 secondaryEye, AABB support,
        Direction face, float yaw, float maxPitch) {
        double[] window = grimTwoEyeCrossingWindow(primaryEye, secondaryEye, support, face, yaw);

        if (window == null) return Math.min(maxPitch, GRIM_PLACE_PITCH_PARK);

        double preferred = Math.toDegrees(Math.atan2(
            primaryEye.y - grimFaceCrossDepthY(support), window[2]));
        return (float) Mth.clamp(Mth.clamp(preferred, window[0], window[1]), -maxPitch, maxPitch);
    }

    static double grimFaceCrossDepthY(AABB support) {
        return support.maxY - GRIM_PIN_CROSS_DEPTH * support.getYsize();
    }

    static double[] grimTwoEyeCrossingWindow(Vec3 primaryEye, Vec3 secondaryEye, BlockPos support,
        Direction face, float yaw) {
        return grimTwoEyeCrossingWindow(primaryEye, secondaryEye, grimSupportBox(support), face, yaw);
    }

    static double[] grimTwoEyeCrossingWindow(Vec3 primaryEye, Vec3 secondaryEye, AABB support,
        Direction face, float yaw) {
        double[] primary = grimFaceCrossingWindow(primaryEye, support, face, yaw);
        if (primary == null) return null;
        double low = primary[0];
        double high = primary[1];
        double[] secondary = grimFaceCrossingWindow(secondaryEye, support, face, yaw);
        if (secondary != null) {
            double bothLow = Math.max(low, secondary[0]);
            double bothHigh = Math.min(high, secondary[1]);
            if (bothLow <= bothHigh) {
                low = bothLow;
                high = bothHigh;
            }
        }
        return new double[] {low, high, primary[2]};
    }

    static float grimCrossingPitch(Vec3 eye, BlockPos support, Direction face, float yaw, float maxPitch) {
        return grimCrossingPitch(eye, grimSupportBox(support), face, yaw, maxPitch);
    }

    static float grimCrossingPitch(Vec3 eye, AABB support, Direction face, float yaw, float maxPitch) {

        float park = Math.min(maxPitch, GRIM_PLACE_PITCH_PARK);
        double past = grimEyePastPlane(eye, support, face);
        if (past <= 0.0D) return park;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return park;
        double run = past / toward;
        double drop = eye.y - grimFaceCrossDepthY(support);
        return (float) Mth.clamp(Math.toDegrees(Math.atan2(drop, run)), -maxPitch, maxPitch);
    }

    static double grimEyePastPlane(Vec3 eye, BlockPos support, Direction face) {
        return grimEyePastPlane(eye, grimSupportBox(support), face);
    }

    static double grimEyePastPlane(Vec3 eye, AABB support, Direction face) {
        if (support == null) return 0.0D;
        double plane = switch (face) {
            case DOWN -> support.minY;
            case UP -> support.maxY;
            case NORTH -> support.minZ;
            case SOUTH -> support.maxZ;
            case WEST -> support.minX;
            case EAST -> support.maxX;
        };
        double along = switch (face.getAxis()) {
            case X -> eye.x;
            case Y -> eye.y;
            case Z -> eye.z;
        };
        return (along - plane) * (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D);
    }

    static boolean grimCrossingLandsOnFace(Vec3 eye, BlockPos support, Direction face, float yaw) {
        return grimCrossingLandsOnFace(eye, support, face, yaw, false);
    }

    static double grimTopCrossingRun(Vec3 eye, BlockPos support, float yaw, double margin) {
        return grimTopCrossingRun(eye, grimSupportBox(support), yaw, margin);
    }

    static double grimTopCrossingRun(Vec3 eye, AABB support, float yaw, double margin) {
        double[] span = grimTopCrossingSpan(eye, support, yaw, margin);
        return span == null ? Double.NaN : (span[0] + span[1]) * 0.5D;
    }

    static double[] grimTopCrossingSpan(Vec3 eye, BlockPos support, float yaw, double margin) {
        return grimTopCrossingSpan(eye, grimSupportBox(support), yaw, margin);
    }

    static double[] grimTopCrossingSpan(Vec3 eye, AABB support, float yaw, double margin) {
        if (support == null) return null;
        double drop = eye.y - support.maxY;
        if (drop <= 1.0E-4D) return null;
        double yawRad = Math.toRadians(yaw);
        double[] origin = { eye.x, eye.z };
        double[] look = { -Math.sin(yawRad), Math.cos(yawRad) };
        double[] low = { support.minX + margin, support.minZ + margin };
        double[] high = { support.maxX - margin, support.maxZ - margin };
        double enter = 0.0D;
        double exit = Double.MAX_VALUE;
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(look[axis]) < 1.0E-6D) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) return null;
                continue;
            }
            double first = (low[axis] - origin[axis]) / look[axis];
            double second = (high[axis] - origin[axis]) / look[axis];
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
        }
        return exit <= enter ? null : new double[] { enter, exit };
    }

    static boolean grimTopCrossingLandsOnFace(Vec3 eye, BlockPos support, float yaw, boolean holding) {
        return grimTopCrossingLandsOnFace(eye, grimSupportBox(support), yaw, holding);
    }

    static boolean grimTopCrossingLandsOnFace(Vec3 eye, AABB support, float yaw, boolean holding) {
        return !Double.isNaN(grimTopCrossingRun(
            eye, support, yaw, holding ? GRIM_PIN_RELEASE_MARGIN : GRIM_PIN_ACQUIRE_MARGIN));
    }

    static float grimTopCrossingPitch(Vec3 eye, BlockPos support, float yaw) {
        return grimTopCrossingPitch(eye, grimSupportBox(support), yaw);
    }

    static float grimTopCrossingPitch(Vec3 eye, AABB support, float yaw) {
        double[] span = grimTopCrossingSpan(eye, support, yaw, GRIM_PIN_ACQUIRE_MARGIN);
        if (span == null) return Float.NaN;
        double drop = eye.y - support.maxY;
        double middle = (span[0] + span[1]) * 0.5D;
        return (float) Math.toDegrees(Math.atan2(drop, Math.max(middle, 1.0E-4D)));
    }

    static boolean grimCrossingLandsOnFace(
        Vec3 eye, BlockPos support, Direction face, float yaw, boolean holding
    ) {
        return grimCrossingLandsOnFace(eye, grimSupportBox(support), face, yaw, holding);
    }

    static boolean grimCrossingLandsOnFace(
        Vec3 eye, AABB support, Direction face, float yaw, boolean holding
    ) {
        if (support == null) return false;
        double past = grimEyePastPlane(eye, support, face);
        if (past <= 0.0D) return true;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return false;
        double run = past / toward;
        boolean xAxisFace = face.getStepX() != 0;
        double crossing = xAxisFace ? eye.z + lookZ * run : eye.x + lookX * run;
        double edge = xAxisFace ? support.minZ : support.minX;
        double far = xAxisFace ? support.maxZ : support.maxX;
        boolean corner = Math.abs(Math.abs(lookX) - Math.abs(lookZ)) <= GRIM_PIN_DIAGONAL_LOOK_TOLERANCE;
        double margin = corner
            ? (holding ? GRIM_PIN_CORNER_RELEASE_MARGIN : GRIM_PIN_CORNER_ACQUIRE_MARGIN)
            : (holding ? GRIM_PIN_SIDE_RELEASE_MARGIN : GRIM_PIN_SIDE_ACQUIRE_MARGIN);
        return crossing >= edge + margin && crossing <= far - margin;
    }

    static double grimCrossingFraction(Vec3 eye, BlockPos support, Direction face, float yaw) {
        return grimCrossingFraction(eye, grimSupportBox(support), face, yaw);
    }

    static double grimCrossingFraction(Vec3 eye, AABB support, Direction face, float yaw) {
        if (support == null || !face.getAxis().isHorizontal()) return Double.NaN;
        double past = grimEyePastPlane(eye, support, face);
        if (past <= 0.0D) return Double.NaN;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
        if (toward <= 0.1D) return Double.NaN;
        double run = past / toward;
        boolean xAxisFace = face.getStepX() != 0;
        double crossing = xAxisFace ? eye.z + lookZ * run : eye.x + lookX * run;
        double low = xAxisFace ? support.minZ : support.minX;

        double span = (xAxisFace ? support.getZsize() : support.getXsize());
        return span <= 1.0E-9D ? Double.NaN : (crossing - low) / span;
    }

    private static boolean grimCrossingInSquare(
        Vec3 eye, BlockPos support, Direction face, float yaw
    ) {
        double frac = grimCrossingFraction(eye, support, face, yaw);
        return !Double.isNaN(frac)
            && frac >= GRIM_PIN_SIDE_ACQUIRE_MARGIN
            && frac <= 1.0D - GRIM_PIN_SIDE_ACQUIRE_MARGIN;
    }

    private static float grimLandingNudge(
        Vec3 eye, Vec3 lead, BlockPos support, Direction face, float fromYaw, float laneYaw
    ) {
        if (!face.getAxis().isHorizontal()) return Float.NaN;
        for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            if (grimCrossingInSquare(eye.add(lead.scale(step)), support, face, fromYaw)) {
                return Float.NaN;
            }
        }
        for (float off = 1.0F; off <= GRIM_MAX_YAW_STEP; off += 1.0F) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                float yaw = Mth.wrapDegrees(fromYaw + sign * off);
                if (!grimCrossingInSquare(eye, support, face, yaw)) continue;
                if (Math.abs(grimLaneOctantResidual(laneYaw, yaw))
                    > GRIM_LANE_OCTANT_MAX_RESIDUAL) {
                    continue;
                }
                return yaw;
            }
        }
        return Float.NaN;
    }

    static float grimGridFlipYaw(
        Vec3 eye, Vec3 lead, BlockPos support, Direction face, float fromYaw
    ) {
        if (!face.getAxis().isHorizontal()) return Float.NaN;

        if (!grimYawCannotTrackFace(eye, lead, support, face, fromYaw)) {
            for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS; step++) {
                if (grimCrossingInSquare(eye.add(lead.scale(step)), support, face, fromYaw)) {
                    return Float.NaN;
                }
            }
        }
        for (int gridStep = 1; gridStep <= GRIM_GRID_FLIP_MAX_STEPS; gridStep++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                float yaw = Mth.wrapDegrees(fromYaw + sign * gridStep * 45.0F);
                if (grimYawCannotTrackFace(eye, lead, support, face, yaw)) continue;
                boolean lands = true;
                for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS && lands; step++) {
                    lands = grimCrossingInSquare(eye.add(lead.scale(step)), support, face, yaw);
                }
                if (lands) return yaw;
            }
        }
        return Float.NaN;
    }

    static float grimTopGridFlipYaw(Vec3 eye, Vec3 lead, BlockPos support, float fromYaw) {
        for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS; step++) {
            if (!Float.isNaN(grimTopCrossingPitch(eye.add(lead.scale(step)), support, fromYaw))) {
                return Float.NaN;
            }
        }
        for (int gridStep = 1; gridStep <= GRIM_GRID_FLIP_MAX_STEPS; gridStep++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                float yaw = Mth.wrapDegrees(fromYaw + sign * gridStep * 45.0F);
                boolean lands = true;
                for (int step = 0; step <= GRIM_PIN_LOOKAHEAD_TICKS && lands; step++) {
                    lands = !Float.isNaN(
                        grimTopCrossingPitch(eye.add(lead.scale(step)), support, yaw));
                }
                if (lands) return yaw;
            }
        }
        return Float.NaN;
    }

    static boolean grimYawCannotTrackFace(
        Vec3 eye, Vec3 lead, BlockPos support, Direction face, float yaw
    ) {
        if (!face.getAxis().isHorizontal()) return false;
        double yawRad = Math.toRadians(yaw);
        double toward = -(-Math.sin(yawRad) * face.getStepX() + Math.cos(yawRad) * face.getStepZ());
        if (toward >= GRIM_FACE_TOWARD_TRACK_MIN) return false;
        double[] real = grimFaceCrossingWindow(eye, support, face, yaw);
        if (real == null) return false;
        double[] ahead = grimFaceCrossingWindow(eye.add(lead), support, face, yaw);
        if (ahead == null) return false;
        return ahead[1] < real[0] || real[1] < ahead[0];
    }

    static boolean grimFacePinsSoon(
        Vec3 pinEye, Vec3 leadStep, BlockPos support, Direction face, float pinYaw,
        boolean holding, int lookahead
    ) {
        for (int step = 0; step <= lookahead; step++) {
            Vec3 eye = step == 0 ? pinEye : pinEye.add(leadStep.scale(step));
            if (grimFacePins(eye, support, face, pinYaw, holding)) return true;
        }
        return false;
    }

    static boolean grimGapPredictionApplies(
        boolean grimFamily, boolean nearEdge, boolean edgeSneakActive
    ) {
        return grimFamily && (nearEdge || edgeSneakActive);
    }

    static boolean grimExactCornerVisibility(
        boolean grimFamily, boolean edgeSneakActive, boolean onGround, boolean jumping
    ) {

        return grimFamily && edgeSneakActive && onGround && !jumping;
    }

    private Vec3 findFallOffPosition(MovementLine line) {
        if (line == null) return null;
        Vec3 nearest = nearestPointOnLine(line, MC.player.position());
        Vec3 from = nearest.add(0.0D, -0.1D, 0.0D);
        Vec3 to = from.add(line.direction().scale(PREDICTION_LINE_LENGTH));
        Vec3 collision = findEdgeCollision(from, to);
        return collision == null ? null : new Vec3(collision.x, MC.player.getY(), collision.z);
    }

    private Vec3 grimEdgeProbeDirection() {
        Input input = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
        Vec3 nextVelocity = MC.player.getDeltaMovement();
        if (nextVelocity.horizontalDistanceSqr() > 0.003D * 0.003D) {
            return new Vec3(nextVelocity.x, 0.0D, nextVelocity.z).normalize();
        }
        if (hasDirectionalInput(input)) {
            return Vec3.directionFromRotation(0.0F, movementYaw(input));
        }
        return Vec3.directionFromRotation(0.0F, MC.player.getYRot());
    }

    private boolean isCloseToEdge(double distance, Vec3 position) {
        Vec3 nextVelocity = MC.player.getDeltaMovement();
        Vec3 direction = grimEdgeProbeDirection();

        Vec3 from = position.add(0.0D, -0.1D, 0.0D);
        if (findEdgeCollision(from, from.add(direction.scale(distance))) != null) return true;

        Vec3 nextPosition = position.add(nextVelocity.x, nextVelocity.y, nextVelocity.z);
        Vec3 positionInTwoTicks = nextPosition.add(nextVelocity.x, 0.0D, nextVelocity.z);
        return wouldBeCloseToFallOff(position) || wouldBeCloseToFallOff(positionInTwoTicks);
    }

    private boolean shouldSneakAtEdge() {
        if (MC.player.onGround()) {
            double lookahead = Mth.clamp(0.12D + MC.player.getDeltaMovement().horizontalDistance() * 1.0D, 0.12D, 0.35D);
            return isCloseToEdge(lookahead, MC.player.position());
        }
        return MC.player.fallDistance > 0.0F
            && isCloseToEdge(0.1D, MC.player.position());
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

            if (isSolidSupport(MC.level.getBlockState(pos), pos)) return true;
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
        return MC.player.getYRot() + inputOctantDegrees(input);
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

    private boolean grimCourseFrozen() {
        return MC.player != null && !MC.player.onGround()
            && MC.player.getDeltaMovement().y < -0.08D;
    }

    private Vec3 stabilizedPointOnFace(FaceRect face, FaceRect fullFace, BlockPos targetPos, Vec3 eye,
                                       AutismRotationUtil.Rotation currentRotation,
                                       MovementLine optimalLine) {
        Vec3 offset = new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        FaceRect trimmed = face.trim(FACE_INSET).offset(offset);
        FaceRect targetFace = stabilizedTargetFace(trimmed, eye, optimalLine);
        Vec3 point = nearestPointToFace(targetFace, new InfiniteLine(eye, lookVector(currentRotation)));
        if (isGrimFamily()) point = clampToCenterWindow(point, fullFace, offset);
        return point.subtract(offset);
    }

    private static Vec3 clampToCenterWindow(Vec3 point, FaceRect face, Vec3 offset) {
        double cx = offset.x + (face.from().x + face.to().x) * 0.5D;
        double cy = offset.y + (face.from().y + face.to().y) * 0.5D;
        double cz = offset.z + (face.from().z + face.to().z) * 0.5D;
        double hx = centerWindowHalf(face.to().x - face.from().x);
        double hy = centerWindowHalf(face.to().y - face.from().y);
        double hz = centerWindowHalf(face.to().z - face.from().z);
        return new Vec3(
            Mth.clamp(point.x, cx - hx, cx + hx),
            Mth.clamp(point.y, cy - hy, cy + hy),
            Mth.clamp(point.z, cz - hz, cz + hz));
    }

    private static double centerWindowHalf(double span) {
        return Math.max(0.0D, Math.min(GRIM_AIM_CENTER_WINDOW, span * 0.5D - FACE_INSET));
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
        return standableSupportState(state, MC.level, pos, CollisionContext.of(MC.player));
    }

    static boolean standableSupportState(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        if (state == null || state.isAir()) return false;
        if (state.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER)) return true;
        VoxelShape collision = state.getCollisionShape(level, pos, context);
        if (collision.isEmpty()) return false;
        return !Shapes.joinIsNotEmpty(
            collision.getFaceShape(Direction.UP), STANDABLE_CENTER_SHAPE, BooleanOp.ONLY_SECOND);
    }

    private boolean isTellySupport(BlockState state, BlockPos pos) {
        return tellySupportState(state, MC.level, pos, CollisionContext.of(MC.player));
    }

    static boolean tellySupportState(BlockState state, BlockGetter level, BlockPos pos,
                                     CollisionContext context) {
        return !state.isAir()
            && !state.canBeReplaced()
            && !state.getCollisionShape(level, pos, context).isEmpty();
    }

    public static void onServerPositionCorrection() {
        Module module = ModuleRegistry.get("scaffold");
        if (module instanceof ScaffoldModule scaffold && scaffold.isEnabled()) {
            scaffold.grimServerCorrectionReset();
        }
    }

    public static void onServerRotationApplied(float appliedYaw, float appliedPitch) {
        grimSentYaw = appliedYaw;
        grimSentYawTick = AutismSharedState.get().getClientTickCounter();
        Module module = ModuleRegistry.get("scaffold");
        if (module instanceof ScaffoldModule scaffold && scaffold.isEnabled()) {
            scaffold.serverRotation = new AutismRotationUtil.Rotation(appliedYaw, appliedPitch);

            scaffold.grimTracePrevSentYaw = appliedYaw;
        }
    }

    private void grimServerCorrectionReset() {
        resetGrimLaunchReservation();
        grimStickyTarget = null;
        grimStickySetTick = -1;
        grimStickyBandMissTicks = 0;
        grimStickyPitchMissTicks = 0;
        grimPinSupport = null;
        grimPinFace = null;
        grimRiseTakeoffLatch = null;
        grimRiseTakeoffLatchTicks = 0;
        grimRiseFloorCell = null;
        grimRiseFloorTick = Integer.MIN_VALUE;
        grimAimMissSupport = null;
        grimAimMissFace = null;
        grimAimMissStreak = 0;
        grimAimWindowWaitTicks = 0;
        grimAimOccludedTicks = 0;
        grimStaleSupport = null;
        grimStaleFace = null;
        grimStaleTicks = 0;
        grimEffCell = null;
        grimEffCellRefreshTick = Integer.MIN_VALUE;

        grimPrevTickPos = null;
        grimLastTickStep = Vec3.ZERO;
        grimArcTicks = 0;
        grimArcPlacements = 0;
        grimAirborneBuiltRow = Integer.MIN_VALUE;
        grimPredictedPlacements.clear();
        if (grimAttemptState != GrimPlacementAttemptState.IDLE) {
            failGrimPlacementAttempt("position-correction");
        }
        if (traceArmed() && MC != null && MC.player != null) {
            Vec3 p = MC.player.position();
            autismclient.util.AutismTraceLog.println(String.format(java.util.Locale.ROOT,
                "[scaffold-live] t%03d setback     pos=%.2f,%.2f,%.2f - position state cleared",
                grimLiveTraceTicks, p.x, p.y, p.z));
        }
    }

    private void clearRuntime(boolean restoreSlot) {
        AutismInputClicker.cancelScaffoldUseClick();
        if (restoreSlot && bool("switch-back") && originalSlot >= 0 && MC != null && MC.player != null
            && !MultiPilot.isActive() && !MacroExecutor.isRunning()
            && MC.player.getInventory().getSelectedSlot() != originalSlot
            && autismclient.util.AutismHandArbiter.beginHandPacketGroup(id())) {

            try {
                AutismInputClicker.queueHotbarSlot(originalSlot);
            } finally {
                autismclient.util.AutismHandArbiter.endHandPacketGroup(id());
            }
        }
        originalSlot = -1;
        requestedSlot = -1;
        switchedToSlot = -1;
        slotResetTicks = 0;
        selectionPending = false;
        serverRotation = MC != null && MC.player != null
            ? AutismRotationUtil.playerRotation(MC.player)
            : null;

        releaseGrimStreamNow();

        rollGrimSessionOffsets();
        grimStickyTarget = null;
        grimStickySetTick = -1;
        grimRealPendingTarget = null;
        grimRealPendingLine = null;
        grimRealPendingFallOff = null;
        grimRealQueuedTick = Integer.MIN_VALUE;
        grimAttemptState = GrimPlacementAttemptState.IDLE;
        grimAttemptGeneration = 0L;
        grimAttemptHand = null;
        grimAttemptBuildsPlannedCell = false;
        grimAttemptSubmittedCount = 0;
        grimAttemptDuplicateSubmitted = false;
        grimAttemptWriteCount = 0;
        grimCommittedClickRotation = null;
        grimCommittedPreviousRotation = null;
        grimAttemptSequence = -1;
        grimAttemptResultSeen = false;
        grimAttemptResultConsumed = false;
        grimAttemptPaceBooked = false;
        grimAttemptResult = "--";
        grimPredictedPlacements.clear();
        grimUntrustedPredictions.clear();
        grimPredictionLevel = null;
        grimHighestObservedAck = Integer.MIN_VALUE;
        grimHighestProcessedAck = Integer.MIN_VALUE;
        GRIM_FINAL_USE_WRITES.clear();
        GRIM_FINAL_MOVE_WRITE.set(null);
        grimFinalMoveSeen = false;
        grimFinalWireGround = false;
        grimFinalWireHorizontalCollision = false;
        grimFinalWireHasPosition = false;
        grimSprintNoForwardTick = Integer.MIN_VALUE;
        grimPaceWaitTicks = 0;
        grimPitchFreedTick = Integer.MIN_VALUE;
        grimStickyBandMissTicks = 0;
        grimStickyPitchMissTicks = 0;
        grimStickyBandTick = Integer.MIN_VALUE;
        grimEffCell = null;
        grimEffCellRefreshTick = Integer.MIN_VALUE;
        grimPrevTickPos = null;
        grimLastTickStep = Vec3.ZERO;
        grimTraceEdgeDanger = false;
        grimTraceFallDanger = false;
        grimTraceLateralBrink = false;
        grimTraceFootingOwed = false;
        grimFootingOwedTicks = 0;
        grimTraceBrake = "--";
        grimTraceDiagonalPaceMean = Double.NaN;
        grimTraceArcCarry = "--";
        grimTraceArcStand = "--";
        grimTraceArcTravel = 0.0D;
        grimCrossingWaitFace = null;
        grimCrossingWaitTick = Integer.MIN_VALUE;
        grimXingStandSpent = 0.0D;
        grimXingStandTick = Integer.MIN_VALUE;
        grimTracePaceBrink = false;
        grimTraceLastChance = false;
        grimLastPlaceYaw = Float.NaN;
        grimTraceJump = "-";
        grimArcTicks = 0;
        grimArcPlacements = 0;
        grimLaneCorrectHoldTicks = 0;
        grimLaneCorrectLockTicks = 0;
        grimLastRescueTick = Integer.MIN_VALUE;
        grimTraceRiseTakeoff = null;
        grimTraceTakeoffWhy = "air";
        grimTraceRiseAllowed = false;
        grimAimHoldTarget = null;
        grimUpFaceSwapCell = null;
        grimAimHoldTick = -1;
        grimAimHoldServedTick = Integer.MIN_VALUE;
        grimTraceClickFeasible = false;
        grimTraceClickLands = false;
        grimTracePrevTick = Integer.MIN_VALUE;
        grimTracePrevSentYaw = Float.NaN;
        grimTraceWhy = "--";
        grimTraceRiseDropWhy = "--";
        grimTracePaceSince = -1;
        grimTracePaceFloor = -1;
        grimTracePaceIntave = false;
        grimTracePrevGnd = true;
        grimTraceFallNoted = false;
        grimArcCarryOrigin = null;
        grimTraceLaunchLedger = "--";
        grimTraceStrip.setLength(0);
        grimTraceClickNumbers = "--";
        grimArcStartTick = -1;
        grimArcStartClientTick = -1;
        grimArcStartPos = null;
        grimArcStartGoal = "--";
        grimArcPlaceCount = 0;
        grimArcSetCount = 0;
        grimArcAimTicks = 0;
        grimArcPaceTicks = 0;
        grimArcNoTargetTicks = 0;
        grimArcDropTicks = 0;
        grimArcChainRelatch = 0;
        grimTraceReserveWhy = "--";
        grimResetArcChain(null);
        grimSegTicks = 0;
        grimSegPlaces = 0;
        grimSegSettled = 0;
        grimSegMiss = 0;
        grimSegAim = 0;
        grimSegPace = 0;
        grimSegNoTarget = 0;
        grimSegDrop = 0;
        grimSegReplan = 0;
        grimSegVeto = 0;
        grimSegRow = Integer.MIN_VALUE;

        grimSentYaw = Float.NaN;
        grimSentYawTick = Integer.MIN_VALUE;
        grimPinSupport = null;
        grimPinFace = null;
        resetGrimLaunchReservation();
        grimPhysicalClimbIntent = false;
        grimFootingSurfaceY = Integer.MIN_VALUE;
        grimLastRowGainTick = Integer.MIN_VALUE;
        grimAirborneBuiltRow = Integer.MIN_VALUE;
        resetGrimInputOctant();
        grimStaleSupport = null;
        grimStaleFace = null;
        grimStaleTicks = 0;
        grimBridgePitchHold = Float.NaN;
        grimTraceCrossing = Double.NaN;
        resetTellyState();
        lastGrimPlacementTick = Integer.MIN_VALUE;
        tellyLastGroundedSupport = null;
        tellyLastGroundedTick = Integer.MIN_VALUE;
        currentMovementLine = null;
        grimEdgeSneakActive = false;
        grimRiseFloorCell = null;
        grimRiseFloorTick = Integer.MIN_VALUE;
        grimAimMissSupport = null;
        grimAimMissFace = null;
        grimAimMissStreak = 0;
        grimAimWindowWaitTicks = 0;
        grimAimOccludedTicks = 0;
        grimIntaveParkClear();
        grimLastArmRay = null;
        grimNoTargetTicks = 0;
        grimPaceJitterMs = 0;

        grimPaceSamples.clear();
        grimPaceLastBookedNanos = Long.MIN_VALUE;
        grimIntavePlaceGaps.clear();
        grimIntavePlaceCells.clear();
        grimIntavePlaceNanos = Long.MIN_VALUE;
        grimIntavePlacePitch = Float.NaN;
        grimPaceLastJumpNanos = Long.MIN_VALUE;
        grimPaceQueuedNanos = Long.MIN_VALUE;
        grimPaceRiserHoldCell = null;
        grimPaceRiserHoldTick = Integer.MIN_VALUE;
        grimTraceSettledCell = null;
        grimYawVetoTicks = 0;
        grimGoalVetoTicks = 0;
        grimGoalVetoLastErr = Float.NaN;
        grimDeadCells.clear();
        grimPaceWasOnGround = false;
        grimLastSneakTick = Integer.MIN_VALUE;
        grimSneakHoldTicks = 0;
        grimRiseTakeoffLatch = null;
        grimRiseTakeoffLatchTicks = 0;
        lastPlacedBlocks.clear();
        placementOffsets.clear();
        lastSupportPosition = null;
        lastSupportReference = null;
        supportMissTicks = 0;
        supportMissClientTick = Integer.MIN_VALUE;
        grimEdgeLockedLine = null;

        grimCourseStep = COURSE_STEP_UNSET;
        grimCourseStepCandidate = COURSE_STEP_UNSET;
        grimCourseStepDwell = 0;
        grimLaneOctant = 0;
        grimPostureYawHeld = Float.NaN;
        grimPostureYawCandidate = Float.NaN;
        grimPostureYawStreak = 0;
        grimPostureYawTick = Integer.MIN_VALUE;
    }

    private static List<BlockPos> normalOffsets() {
        List<BlockPos> offsets = new ArrayList<>(27);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                offsets.add(new BlockPos(x, 0, z));
                offsets.add(new BlockPos(x, -1, z));

                offsets.add(new BlockPos(x, 1, z));
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

    enum TellyStrafe {
        NONE,
        LEFT,
        RIGHT
    }

    private record TellyPlacement(PlacementTarget target, boolean raised) {
    }

    enum TellyRotationIntent {
        FORWARD,
        PLACEMENT,
        HOLD,
        RETURN
    }

    private record TellyRotationGoal(
        AutismRotationUtil.Rotation rotation,
        TellyRotationIntent intent
    ) {
    }

    private record TellyFaceSample(
        FaceRect worldFace,
        Vec3 point,
        AutismRotationUtil.Rotation rotation,
        BlockHitResult verifiedHit,
        int offsetIndex
    ) {
    }

    record TellyGroundSteeringState(boolean active, float offsetDegrees) {
    }

    record TellyAirCorrectionState(
        TellyStrafe pulse,
        int cooldown,
        TellyStrafe lastPulse,
        int pulsesUsed
    ) {
    }

    record PlacementTarget(
        BlockPos supportBlock,
        BlockPos placedBlock,
        Direction face,
        BlockHitResult hit,
        AutismRotationUtil.Rotation rotation,
        double minPlacementY
    ) {
    }

    record TargetPlan(BlockPos supportBlock, Direction face) {
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

    record MovementLine(Vec3 origin, Vec3 direction) {
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
