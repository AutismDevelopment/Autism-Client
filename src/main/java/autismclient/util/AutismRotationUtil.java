package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class AutismRotationUtil {
    private AutismRotationUtil() {
    }

    public record Rotation(float yaw, float pitch) {
    }

    public static Rotation playerRotation(LocalPlayer player) {
        return new Rotation(player.getYRot(), player.getXRot());
    }

    public static Rotation lookingAt(Vec3 point, Vec3 from) {
        Vec3 diff = point.subtract(from);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(Mth.wrapDegrees(pitch), -90.0f, 90.0f));
    }

    public static Rotation towardsLinear(Rotation current, Rotation target, float horizontalFactor, float verticalFactor) {
        float yawDiff = angleDifference(target.yaw, current.yaw);
        float pitchDiff = angleDifference(target.pitch, current.pitch);
        double length = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        if (length < 1.0E-9) return target;
        float yawStep = Math.abs((float) (yawDiff / length)) * horizontalFactor;
        float pitchStep = Math.abs((float) (pitchDiff / length)) * verticalFactor;
        return new Rotation(
            current.yaw + Mth.clamp(yawDiff, -yawStep, yawStep),
            current.pitch + Mth.clamp(pitchDiff, -pitchStep, pitchStep)
        );
    }

    public static Rotation interpolate(Rotation current, Rotation target, float factor) {
        float t = Mth.clamp(factor, 0.0f, 1.0f);
        return new Rotation(
            current.yaw + angleDifference(target.yaw, current.yaw) * t,
            Mth.clamp(current.pitch + angleDifference(target.pitch, current.pitch) * t, -90.0f, 90.0f)
        );
    }

    public static Rotation normalizeToSensitivity(Rotation rotation, Rotation current) {
        double gcd = mouseDegreesPerRawInput();
        if (gcd <= 0.0) return rotation;
        float yawDiff = angleDifference(rotation.yaw, current.yaw);
        float pitchDiff = angleDifference(rotation.pitch, current.pitch);
        float yaw = current.yaw + (float) (Math.round(yawDiff / gcd) * gcd);
        float pitch = current.pitch + (float) (Math.round(pitchDiff / gcd) * gcd);
        return new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public static double sensitivityGcd() {
        return mouseDegreesPerRawInput();
    }

    public static double mouseDegreesPerRawInput() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return 0.0;
        double f = mc.options.sensitivity().get() * 0.6D + 0.2D;
        float sensitivityFactor = (float) (f * f * f * 8.0D);
        return (double) (sensitivityFactor * 0.15F);
    }

    public static float angleDifference(float target, float current) {
        return Mth.wrapDegrees(target - current);
    }

    public static float angleTo(Rotation current, Rotation target) {
        float yaw = angleDifference(target.yaw, current.yaw);
        float pitch = angleDifference(target.pitch, current.pitch);
        return (float) Math.min(180.0, Math.sqrt(yaw * yaw + pitch * pitch));
    }

    public static float rotationAngleTo(Rotation a, Rotation b) {
        Vec3 first = Vec3.directionFromRotation(a.pitch(), a.yaw());
        Vec3 second = Vec3.directionFromRotation(b.pitch(), b.yaw());
        double cosine = Mth.clamp(first.dot(second), -1.0D, 1.0D);
        return (float) Math.toDegrees(Math.acos(cosine));
    }

    public static void apply(LocalPlayer player, Rotation rotation, boolean syncPrevious) {
        if (player == null || rotation == null) return;
        float yaw = Mth.wrapDegrees(rotation.yaw);
        float pitch = Mth.clamp(rotation.pitch, -90.0f, 90.0f);
        if (syncPrevious) {
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.setYBodyRot(yaw);
            player.yBodyRotO = yaw;
        } else {
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }
        player.setYRot(yaw);
        player.setXRot(pitch);
    }
}
