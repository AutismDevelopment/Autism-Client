package autismclient.modules;

import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.mixin.accessor.AutismEntityAccessor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

public final class PhaseModule extends Module {
    private static final int SHAPE_NONE = 0;
    private static final int SHAPE_SPIDER = 1;
    private static final int SHAPE_BLINK = 2;

    private static final ClipMode CLIP = new ClipMode();
    private static final IntaveMode INTAVE = new IntaveMode();
    private static final BlinkMode BLINK = new BlinkMode();
    private static final SpiderMode SPIDER = new SpiderMode();

    private static volatile PhaseModule instance;

    private static volatile int shapeMode;

    private volatile PhaseMode active;

    public PhaseModule() {
        super("phase", "Phase", ModuleCategory.MOVEMENT, "Phase through blocks.");
        add(new ChoiceSetting("mode", "Mode", "Clip", "Clip", "Intave", "Blink", "Spider")
            .description("Phase method.").build());
        add(new IntSetting("maximum", "Maximum", 120, 1, 300, 1).unit("ticks")
            .visibleWhen(() -> "Blink".equals(choice("mode")))
            .description("Blink tick budget.").build());
        instance = this;
    }

    @Override
    public String info() {
        String mode = choice("mode");
        PhaseMode current = active;
        if (current == null) return mode;
        String state = current.info();
        return state.isEmpty() ? mode : mode + " " + state;
    }

    @Override
    public void onEnable() {
        enterMode(modeFor(choice("mode")));
    }

    @Override
    public void onDisable() {
        exitMode();
    }

    @Override
    public void onGameJoin() {

        if (isEnabled() && active == null) enterMode(modeFor(choice("mode")));
    }

    @Override
    public void onGameLeft() {
        exitMode();
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("mode".equals(settingId) && isEnabled()) switchMode();
    }

    @Override
    protected void onSettingsReset() {
        if (isEnabled()) switchMode();
    }

    @Override
    public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        PhaseMode mode = active;
        if (mode != null) mode.preMovementTick();
    }

    @Override
    public void onNetworkMovementTickPre() {
        PhaseMode mode = active;
        if (mode != null) mode.onNetworkMovementTickPre();
    }

    @Override
    public void onPacketProcessFrame() {
        PhaseMode mode = active;
        if (mode != null) mode.onPacketProcessFrame();
    }

    @Override
    public boolean shouldCancelPlayerTick() {
        PhaseMode mode = active;
        return mode != null && mode.shouldCancelPlayerTick();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        PhaseMode mode = active;
        if (mode != null && packet instanceof ClientboundPlayerPositionPacket) mode.onTeleportPacket();
        return false;
    }

    public static VoxelShape blockShape(VoxelShape original, BlockState state, BlockPos pos) {
        int mode = shapeMode;
        if (mode == SHAPE_NONE) return original;
        LocalPlayer player = MC.player;
        if (player == null) return original;
        if (mode == SHAPE_BLINK) return BLINK.shape(original, player, pos);
        return SPIDER.shape(original, state, player, pos);
    }

    private void switchMode() {
        PhaseMode next = modeFor(choice("mode"));
        if (next == active) return;
        exitMode();
        enterMode(next);
    }

    private void enterMode(PhaseMode mode) {
        active = mode;
        publishShapeMode();
        mode.enable();
    }

    private void exitMode() {
        PhaseMode mode = active;
        active = null;
        publishShapeMode();
        if (mode != null) mode.disable();
    }

    private void publishShapeMode() {
        PhaseMode mode = active;
        if (mode == SPIDER) shapeMode = SHAPE_SPIDER;
        else if (mode == BLINK) shapeMode = SHAPE_BLINK;
        else shapeMode = SHAPE_NONE;
    }

    private static PhaseMode modeFor(String name) {
        return switch (name) {
            case "Intave" -> INTAVE;
            case "Blink" -> BLINK;
            case "Spider" -> SPIDER;
            default -> CLIP;
        };
    }

    private static boolean doesCollideAt(LocalPlayer player, Vec3 pos) {
        AABB box = player.getBoundingBox().move(pos.subtract(player.position()));
        for (VoxelShape shape : player.level().getBlockCollisions(player, box)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    private static void setDeltaY(LocalPlayer player, double y) {
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, y, velocity.z);
    }

    private interface PhaseMode {
        default void enable() {
        }

        default void disable() {
        }

        default void preMovementTick() {
        }

        default void onNetworkMovementTickPre() {
        }

        default void onPacketProcessFrame() {
        }

        default boolean shouldCancelPlayerTick() {
            return false;
        }

        default void onTeleportPacket() {
        }

        default String info() {
            return "";
        }
    }

    private static final class ClipMode implements PhaseMode {
        private static final double GRAVITY = 0.07840000152;

        @Override
        public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
            LocalPlayer player = MC.player;
            ClientPacketListener connection = MC.getConnection();
            if (player == null || connection == null) return;
            Vec3 center = Vec3.atCenterOf(player.blockPosition());
            connection.send(new ServerboundMovePlayerPacket.PosRot(
                center.x, player.getY() - GRAVITY, center.z,
                player.getYRot(), player.getXRot(), false, false));

            instance.disableWithToggleMessage("Phase: clip packet sent.");
        }
    }

    private static final class IntaveMode implements PhaseMode {

        private boolean mining;

        @Override
        public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
            LocalPlayer player = MC.player;
            ClientPacketListener connection = MC.getConnection();
            if (player == null || connection == null) return;
            boolean check = MC.options.keyAttack.isDown() && player.getXRot() > 80.0f;
            BlockPos below = player.blockPosition().offset(0, -1, 0);
            if (check) {
                connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, below, Direction.UP));
                mining = true;
            } else if (mining) {
                mining = false;
            }

            if (mining) {
                ((AutismEntityAccessor) player).autism$setPosition(
                    new Vec3(player.getX(), player.getY() - 0.0052, player.getZ()));
            }
            if (player.isShiftKeyDown()) {
                float distance = 0.005f;
                double rotation = Math.toRadians(player.getYRot());
                if (MC.options.keyUp.isDown()) move(player, rotation, distance, 1, 1);
                else if (MC.options.keyDown.isDown()) move(player, rotation, -distance, 1, -1);
                else if (MC.options.keyLeft.isDown()) move(player, rotation, distance, -1, 1);
                else if (MC.options.keyRight.isDown()) move(player, rotation, -distance, -1, -1);
            }
        }

        @Override
        public String info() {
            if (MC.player == null) return "";
            if (mining) return "sinking";
            return MC.options.keyAttack.isDown() ? "look down" : "hold attack";
        }

        private static void move(LocalPlayer player, double rotation, float distance, int xMultiplier, int zMultiplier) {
            double xx = Math.cos(rotation) * distance * xMultiplier;
            double zz = Math.sin(rotation) * distance * zMultiplier;
            player.setPos(player.getX() + xx, player.getY(), player.getZ() + zz);
        }
    }

    private static final class BlinkMode implements PhaseMode {
        private enum State {
            WAITING(false),
            PHASING(false),
            WALKING(true),
            FLUSHING(true);

            final boolean boxCollisions;

            State(boolean boxCollisions) {
                this.boxCollisions = boxCollisions;
            }
        }

        private static final int MAX_FRUITLESS_CYCLES = 3;

        private final AutismBlinkManager.HoldPolicy policy = this::classify;

        private volatile State state = State.WAITING;
        private volatile boolean inCollisionCheck;

        private volatile String standDown;

        private volatile boolean recycleRequested;
        private int currentTicks;
        private boolean reachedWalking;
        private int fruitlessCycles;

        @Override
        public void enable() {
            state = State.WAITING;
            currentTicks = 0;
            standDown = null;
            recycleRequested = false;
            reachedWalking = false;
            fruitlessCycles = 0;
            AutismBlinkManager.addPolicy(policy);
        }

        @Override
        public void disable() {

            state = State.FLUSHING;
            AutismBlinkManager.flushIncoming();
            state = State.WAITING;

            AutismBlinkManager.removePolicy(policy);
            AutismBlinkManager.requestFlush(true, true);
            currentTicks = 0;
            standDown = null;
            recycleRequested = false;
            reachedWalking = false;
            fruitlessCycles = 0;
        }

        @Override
        public void onNetworkMovementTickPre() {
            LocalPlayer player = MC.player;
            if (player == null) return;
            inCollisionCheck = true;
            try {
                if (state == State.WAITING) {
                    if (doesCollideAt(player, player.position())) state = State.PHASING;
                } else if (state == State.PHASING) {
                    if (!doesCollideAt(player, player.position())) {
                        state = State.WALKING;
                        reachedWalking = true;
                    }
                }
            } finally {
                inCollisionCheck = false;
            }
        }

        @Override
        public void onPacketProcessFrame() {
            String reason = standDown;
            if (reason != null) {
                standDown = null;
                instance.disableWithToggleMessage(reason);
                return;
            }
            if (recycleRequested) {
                recycleRequested = false;
                recycle();
                return;
            }
            if (state != State.WALKING) return;
            LocalPlayer player = MC.player;
            if (player == null) return;
            boolean collides = false;
            inCollisionCheck = true;
            try {

                for (Vec3 pos : AutismBlinkManager.heldOutgoingPositions()) {
                    if (doesCollideAt(player, pos)) {
                        collides = true;
                        break;
                    }
                }
            } finally {
                inCollisionCheck = false;
            }
            if (!collides) instance.setEnabled(false);
        }

        @Override
        public boolean shouldCancelPlayerTick() {
            if (currentTicks > instance.integer("maximum")) {
                recycleRequested = true;
                return true;
            }
            if (state == State.PHASING || state == State.WALKING) currentTicks++;
            return false;
        }

        private void recycle() {
            state = State.FLUSHING;
            AutismBlinkManager.flushIncoming();
            state = State.WAITING;
            AutismBlinkManager.requestFlush(false, true);
            currentTicks = 0;

            if (reachedWalking) fruitlessCycles = 0;
            else if (++fruitlessCycles >= MAX_FRUITLESS_CYCLES) standDown = "Phase disabled: block did not clear.";
            reachedWalking = false;
        }

        @Override
        public void onTeleportPacket() {

            standDown = "Phase disabled: server set you back.";
        }

        private AutismBlinkManager.Hold classify(Packet<?> packet, boolean incoming) {
            if (PackHideState.isHardLocked()) return AutismBlinkManager.Hold.FLUSH;
            State current = state;
            if (current == State.WAITING) return AutismBlinkManager.Hold.FLUSH;
            if (packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundBlockEventPacket
                || packet instanceof ClientboundSectionBlocksUpdatePacket
                || packet instanceof ClientboundLevelChunkWithLightPacket
                || packet instanceof ClientboundChunkBatchFinishedPacket
                || packet instanceof ClientboundSetTitleTextPacket) {
                return AutismBlinkManager.Hold.PASS;
            }
            return current == State.PHASING || current == State.WALKING
                ? AutismBlinkManager.Hold.QUEUE
                : AutismBlinkManager.Hold.PASS;
        }

        VoxelShape shape(VoxelShape original, LocalPlayer player, BlockPos pos) {
            if (inCollisionCheck || state.boxCollisions) return original;
            if (pos.getY() >= player.position().y || (player.isShiftKeyDown() && player.onGround())) {
                return Shapes.empty();
            }
            return original;
        }

        @Override
        public String info() {
            State current = state;
            return current == State.WAITING ? "" : current.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class SpiderMode implements PhaseMode {
        private int spiderTicks = 1;

        @Override
        public void enable() {
            spiderTicks = 1;
            LocalPlayer player = MC.player;
            if (player != null) setDeltaY(player, 0.0);
        }

        @Override
        public void disable() {
            if (MC.player != null) MC.player.noPhysics = false;
        }

        @Override
        public void preMovementTick() {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
            LocalPlayer player = MC.player;
            if (player == null || MC.level == null) return;
            switch (spiderTicks) {
                case 1 -> {
                    if (MC.options.keyJump.isDown() && !MC.level.getBlockState(player.blockPosition()).isAir()) {
                        setDeltaY(player, 0.42);
                        spiderTicks++;
                    }
                    player.setOnGround(true);
                }
                case 2 -> {
                    setDeltaY(player, 0.33);
                    spiderTicks++;
                }
                case 3 -> {
                    setDeltaY(player, 0.25);
                    spiderTicks++;
                }
            }
            if (spiderTicks > 3) spiderTicks = 1;

            player.noPhysics = true;
            if (player.isShiftKeyDown()) {
                player.setDeltaMovement(
                    ModuleMovementUtil.withStrafe(player, player.getDeltaMovement(), 0.179));
            }
        }

        VoxelShape shape(VoxelShape original, BlockState state, LocalPlayer player, BlockPos pos) {
            if (state.getBlock() instanceof LiquidBlock) return original;
            return pos.getY() >= player.getY() ? Shapes.empty() : original;
        }
    }
}
