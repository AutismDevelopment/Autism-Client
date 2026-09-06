package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import autismclient.util.multi.PacketTeleportController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VClipAction implements MacroAction {
    private static final double AUTO_SCAN_STEP = 0.5;
    private static final double AUTO_SCAN_RANGE = 128.0;

    public enum Mode {
        MANUAL,
        TOP,
        BOTTOM
    }

    public Mode    mode = Mode.MANUAL;
    public double  deltaY = 0.0;
    public boolean useSegmented = true;
    public int     segmentBlocks = 10;
    public int     maxPackets = 20;
    public boolean updateLocalPosition = true;
    public boolean tryVehicleFirst = true;
    public boolean forceGrounded = true;
    private boolean enabled = true;

    public record Result(boolean success, int packetsRequired, String message) {}

    public static final class Options {
        public Mode mode = Mode.MANUAL;
        public double blocks = 0.0;
        public boolean useSegmented = true;
        public int segmentBlocks = 10;
        public int maxPackets = 20;
        public boolean updateLocalPosition = true;
        public boolean tryVehicleFirst = true;
        public boolean forceGrounded = true;
        public boolean showMessage = false;

        public static Options defaults(double blocks) {
            Options options = new Options();
            options.blocks = blocks;
            return options;
        }

        public Options singlePacket() {
            useSegmented = false;
            return this;
        }
    }

    @Override
    public void execute(Minecraft mc) {
        Options options = new Options();
        options.mode = mode;
        options.blocks = deltaY;
        options.useSegmented = useSegmented;
        options.segmentBlocks = segmentBlocks;
        options.maxPackets = maxPackets;
        options.updateLocalPosition = updateLocalPosition;
        options.tryVehicleFirst = tryVehicleFirst;
        options.forceGrounded = forceGrounded;
        perform(mc, options);
    }

    public static Result perform(Minecraft mc, Options options) {
        if (options == null) options = new Options();
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            if (options.showMessage) AutismClientMessaging.sendPrefixed("\u00a7cVClip: no world / connection.");
            return new Result(false, 0, "No world / connection");
        }

        LocalPlayer player = mc.player;
        double blocks = options.blocks;
        if (options.mode != Mode.MANUAL) {
            AutoVerticalTarget target = resolveAutoVerticalTarget(player, options.mode);
            if (!target.success()) {
                if (options.showMessage) AutismClientMessaging.sendPrefixed("\u00a7cVClip: " + target.message());
                return new Result(false, 0, target.message());
            }
            blocks = target.deltaY();
        }
        int segment = Math.max(1, options.segmentBlocks);
        int maxPackets = Math.max(1, options.maxPackets);
        int packetsRequired = options.useSegmented ? (int) Math.ceil(Math.abs(blocks) / (double) segment) : 1;
        if (packetsRequired > maxPackets) packetsRequired = 1;
        if (packetsRequired <= 0) packetsRequired = 1;

        Entity vehicle = options.tryVehicleFirst ? player.getVehicle() : null;
        if (vehicle != null) {
            try {
                for (int i = 0; i < packetsRequired - 1; i++) {
                    sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
                }
                vehicle.setPos(vehicle.getX(), vehicle.getY() + blocks, vehicle.getZ());
                sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
            } catch (Throwable t) {
                String message = "Vehicle vclip failed: " + t.getMessage();
                if (options.showMessage) AutismClientMessaging.sendPrefixed("\u00a7c" + message);
                return new Result(false, packetsRequired, message);
            }
        } else {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean grounded = options.forceGrounded || player.onGround();
            boolean horizontalCollision = player.horizontalCollision;
            if (blocks < -PacketClipSafety.DIRECT_FALL_LIMIT) {
                int padding = options.useSegmented ? Math.max(0, packetsRequired - 1) : 0;
                packetsRequired = sendFallSafeDownClip(mc, player, x, y, z, blocks, padding, options.updateLocalPosition);
            } else {
                player.resetFallDistance();
                for (int i = 0; i < packetsRequired - 1; i++) {
                    sendPacket(mc, new ServerboundMovePlayerPacket.StatusOnly(grounded, horizontalCollision));
                }
                sendPacket(mc, new ServerboundMovePlayerPacket.Pos(x, y + blocks, z, grounded, horizontalCollision));
                if (options.updateLocalPosition) {
                    player.setPos(x, y + blocks, z);
                    clearLocalFallState(player);
                }
            }
        }

        String prefix = options.mode == Mode.MANUAL ? "vclip " + blocks : "vclip " + options.mode.name().toLowerCase(java.util.Locale.ROOT) + " -> " + String.format(java.util.Locale.ROOT, "%.2f", blocks);
        String message = prefix + " (" + packetsRequired + " packet" + (packetsRequired == 1 ? "" : "s") + ")";
        if (options.showMessage) AutismClientMessaging.sendPrefixed("\u00a7a" + message);
        return new Result(true, packetsRequired, message);
    }

    private static AutoVerticalTarget resolveAutoVerticalTarget(LocalPlayer player, Mode mode) {
        if (mode == Mode.TOP) return resolveTopTarget(player);
        if (mode == Mode.BOTTOM) return resolveBottomTarget(player);
        return new AutoVerticalTarget(true, 0.0, "manual");
    }

    private static AutoVerticalTarget resolveTopTarget(LocalPlayer player) {
        Vec3 start = player.position();
        Vec3 candidate = PacketRoutePlanner.findTopLanding(
            PacketRoutePlanner.forEntity(player), start, AUTO_SCAN_RANGE);
        if (candidate != null) return new AutoVerticalTarget(true, candidate.y - start.y, "top");
        return new AutoVerticalTarget(false, 0.0, "no direct safe top target");
    }

    private static AutoVerticalTarget resolveBottomTarget(LocalPlayer player) {
        Vec3 start = player.position();
        for (double offset = AUTO_SCAN_STEP; offset <= AUTO_SCAN_RANGE; offset += AUTO_SCAN_STEP) {
            Vec3 candidate = new Vec3(start.x, start.y - offset, start.z);
            if (!isPositionLoaded(player, candidate)) continue;
            if (isPositionClear(player, candidate) && hasSupportBelow(player, candidate)) {
                return new AutoVerticalTarget(true, candidate.y - start.y, "bottom");
            }
        }
        return new AutoVerticalTarget(false, 0.0, "no direct safe bottom target");
    }

    private static boolean isPositionLoaded(LocalPlayer player, Vec3 pos) {
        return player != null && player.level() != null && player.level().isLoaded(BlockPos.containing(pos));
    }

    private static boolean isPositionClear(LocalPlayer player, Vec3 pos) {
        Vec3 delta = pos.subtract(player.position());
        return player.level().noCollision(player, player.getBoundingBox().move(delta));
    }

    private static boolean hasSupportBelow(LocalPlayer player, Vec3 pos) {
        Vec3 delta = pos.subtract(player.position());
        AABB moved = player.getBoundingBox().move(delta);
        return !player.level().noCollision(player, moved.move(0.0, -0.0625, 0.0));
    }

    private static int sendFallSafeDownClip(Minecraft mc, LocalPlayer player, double x, double y, double z, double blocks, int paddingPackets, boolean updateLocalPosition) {
        double targetY = y + blocks;
        clearLocalFallState(player);

        int sent = 0;
        for (int i = 0; i < paddingPackets; i++) {
            sendPacket(mc, new ServerboundMovePlayerPacket.StatusOnly(true, false));
            sent++;
        }

        sendPacket(mc, new ServerboundMovePlayerPacket.StatusOnly(true, false));
        sent++;
        Vec3 from = new Vec3(x, y, z);
        Vec3 target = new Vec3(x, targetY, z);
        for (PacketClipSafety.Step step : PacketClipSafety.positionSteps(from, target, true)) {
            sendPacket(mc, new ServerboundMovePlayerPacket.Pos(step.position(), step.onGround(), false));
            sent++;
        }

        if (updateLocalPosition) {
            player.setPos(x, targetY, z);
            clearLocalFallState(player);
        }
        return sent;
    }

    private static void sendPacket(Minecraft mc, Packet<?> packet) {
        PacketTeleportController.runAtomicClipSend(() -> mc.getConnection().send(packet));
    }

    private static void clearLocalFallState(LocalPlayer player) {
        player.resetFallDistance();
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0.0) {
            player.setDeltaMovement(velocity.x, 0.0, velocity.z);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("mode", mode.name());
        tag.putDouble("deltaY", deltaY);
        tag.putBoolean("useSegmented", useSegmented);
        tag.putInt("segmentBlocks", Math.max(1, segmentBlocks));
        tag.putInt("maxPackets", Math.max(1, maxPackets));
        tag.putBoolean("updateLocalPosition", updateLocalPosition);
        tag.putBoolean("tryVehicleFirst", tryVehicleFirst);
        tag.putBoolean("forceGrounded", forceGrounded);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        mode = parseMode(tag.getStringOr("mode", Mode.MANUAL.name()));
        deltaY = tag.getDoubleOr("deltaY", 0.0);
        useSegmented = tag.getBooleanOr("useSegmented", true);
        segmentBlocks = Math.max(1, tag.getIntOr("segmentBlocks", 10));
        maxPackets = Math.max(1, tag.getIntOr("maxPackets", 20));
        updateLocalPosition = tag.getBooleanOr("updateLocalPosition", true);
        tryVehicleFirst = tag.getBooleanOr("tryVehicleFirst", true);
        forceGrounded = tag.getBooleanOr("forceGrounded", true);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.VCLIP;
    }

    @Override
    public String getDisplayName() {
        if (mode == Mode.TOP) return "VClip Top";
        if (mode == Mode.BOTTOM) return "VClip Bottom";
        return "VClip Y=" + String.format(java.util.Locale.ROOT, "%.2f", deltaY);
    }

    @Override
    public String getIcon() { return "VC"; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value == null ? Mode.MANUAL.name() : value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Mode.MANUAL;
        }
    }

    private record AutoVerticalTarget(boolean success, double deltaY, String message) {}
}
