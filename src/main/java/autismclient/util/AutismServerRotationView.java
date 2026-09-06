package autismclient.util;

import autismclient.modules.PackFreecamState;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class AutismServerRotationView {

    private static volatile Timeline timeline = Timeline.empty();

    private AutismServerRotationView() {
    }

    public static void onPacketWritten(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket movement) || !movement.hasRotation()) return;
        updateAtTick(
            movement.getYRot(0.0F), movement.getXRot(0.0F),
            AutismSharedState.get().getClientTickCounter());
    }

    static void update(float yaw, float pitch) {
        updateAtTick(yaw, pitch, AutismSharedState.get().getClientTickCounter());
    }

    static synchronized void updateAtTick(float yaw, float pitch, int tick) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        Rotation next = sanitized(yaw, pitch);
        Timeline old = timeline;
        if (old.current() == null) {
            timeline = new Timeline(next, next, tick);
        } else if (old.tick() == tick) {

            timeline = new Timeline(old.previous(), next, tick);
        } else {
            timeline = new Timeline(old.current(), next, tick);
        }
    }

    public static synchronized void reset() {
        timeline = Timeline.empty();
    }

    public static WireSnapshot snapshot() {
        Timeline value = timeline;
        Rotation previous = value.previous();
        Rotation current = value.current();
        return new WireSnapshot(
            previous == null ? Float.NaN : previous.yaw(),
            previous == null ? Float.NaN : previous.pitch(),
            current == null ? Float.NaN : current.yaw(),
            current == null ? Float.NaN : current.pitch(),
            value.tick(),
            current != null);
    }

    static Rotation currentRotation() {
        return timeline.current();
    }

    static Timeline currentTimeline() {
        return timeline;
    }

    public static void applyLocalPlayerPose(Entity entity, EntityRenderState state, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        if (entity == null || entity != minecraft.player || entity != minecraft.getCameraEntity()
            || !(state instanceof LivingEntityRenderState livingState)) return;
        boolean thirdPerson = minecraft.options != null
            && !minecraft.options.getCameraType().isFirstPerson();
        boolean freecam = PackFreecamState.isActive();
        boolean hidden = PackHideState.isActive();
        if (!thirdPerson || freecam || hidden) return;
        boolean silentOwner = minecraft.player != null
            && AutismSilentAim.activeOutgoingRotation(minecraft.player) != null;
        if (!silentOwner) return;
        Rotation rotation = interpolatedRotation(
            timeline, partialTick, AutismSharedState.get().getClientTickCounter());
        if (rotation == null) return;
        applyRotation(livingState, rotation);
        if (state instanceof AvatarRenderState avatarState) {
            applyFallFlyingRotation(entity, avatarState, rotation);
        }
    }

    static boolean shouldApply(boolean localPlayer, boolean cameraEntity, boolean thirdPerson,
                               boolean freecam, boolean hidden, boolean silentOwner) {
        return localPlayer && cameraEntity && thirdPerson && !freecam && !hidden && silentOwner;
    }

    static Rotation interpolatedRotation(Timeline value, float partialTick, int renderTick) {
        if (value == null || value.current() == null) return null;
        Rotation previous = value.previous() == null ? value.current() : value.previous();
        float progress;
        if (renderTick < value.tick()) {
            progress = 0.0F;
        } else if (renderTick == value.tick()) {
            progress = Mth.clamp(partialTick, 0.0F, 1.0F);
        } else {
            progress = 1.0F;
        }
        return interpolate(previous, value.current(), progress);
    }

    static Rotation interpolate(Rotation previous, Rotation current, float progress) {
        if (previous == null) return current;
        if (current == null) return previous;
        float amount = Mth.clamp(progress, 0.0F, 1.0F);
        float yawDelta = Mth.wrapDegrees(current.yaw() - previous.yaw());
        float yaw = Mth.wrapDegrees(previous.yaw() + yawDelta * amount);
        float pitch = previous.pitch() + (current.pitch() - previous.pitch()) * amount;
        return sanitized(yaw, pitch);
    }

    private static Rotation sanitized(float yaw, float pitch) {
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F));
    }

    static void applyRotation(LivingEntityRenderState state, Rotation rotation) {
        if (state == null || rotation == null) return;

        state.bodyRot = rotation.yaw();
        state.yRot = 0.0F;
        state.xRot = state.isUpsideDown ? -rotation.pitch() : rotation.pitch();
    }

    private static void applyFallFlyingRotation(
        Entity entity, AvatarRenderState state, Rotation rotation
    ) {
        if (!state.isFallFlying || entity == null) return;
        Vec3 look = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
        Vec3 movement = entity.getDeltaMovement();
        if (movement.horizontalDistanceSqr() <= 1.0E-5D || look.horizontalDistanceSqr() <= 1.0E-5D) {
            state.shouldApplyFlyingYRot = false;
            state.flyingYRot = 0.0F;
            return;
        }
        Vec3 horizontalMovement = movement.horizontal().normalize();
        Vec3 horizontalLook = look.horizontal().normalize();
        double dot = Mth.clamp(horizontalMovement.dot(horizontalLook), -1.0D, 1.0D);
        double sign = movement.x * look.z - movement.z * look.x;
        state.shouldApplyFlyingYRot = true;
        state.flyingYRot = (float) (Math.signum(sign) * Math.acos(Math.abs(dot)));
    }

    record Rotation(float yaw, float pitch) {
    }

    public record WireSnapshot(
        float previousYaw,
        float previousPitch,
        float currentYaw,
        float currentPitch,
        int tick,
        boolean initialized
    ) {
    }

    record Timeline(Rotation previous, Rotation current, int tick) {
        static Timeline empty() {
            return new Timeline(null, null, Integer.MIN_VALUE);
        }
    }
}
