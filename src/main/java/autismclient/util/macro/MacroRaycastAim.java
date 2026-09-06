package autismclient.util.macro;

import autismclient.util.AutismRotationUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MacroRaycastAim {
    private static volatile AutismRotationUtil.Rotation active;

    private MacroRaycastAim() {}

    public static AutismRotationUtil.Rotation active() {
        return active;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void hold(AutismRotationUtil.Rotation rotation) {
        active = rotation;
    }

    public static void release() {
        active = null;
    }

    private static final double[] SCAN = { 0.5D, 0.35D, 0.65D, 0.2D, 0.8D };

    private static final double INSET = 0.12D;

    public static AutismRotationUtil.Rotation aimFor(Minecraft mc, RaycastAim.Target target) {
        if (mc == null || mc.player == null || mc.level == null || target == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        if (target.entity() != null) return aimAtEntity(mc, eyes, target.entity());
        if (target.block() != null) return aimAtBlock(mc, eyes, target.block());
        return target.point() == null ? null : AutismRotationUtil.lookingAt(target.point(), eyes);
    }

    private static AutismRotationUtil.Rotation aimAtBlock(Minecraft mc, Vec3 eyes, BlockPos pos) {
        double reachSq = square(mc.player.blockInteractionRange());
        AABB box = blockAim(mc, pos);
        AutismRotationUtil.Rotation best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (Vec3 point : samplePoints(box)) {
            double distanceSq = eyes.distanceToSqr(point);
            if (distanceSq > reachSq || distanceSq >= bestDistanceSq) continue;
            BlockHitResult hit = mc.level.clip(new ClipContext(
                eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));

            if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
            best = AutismRotationUtil.lookingAt(point, eyes);
            bestDistanceSq = distanceSq;
        }
        return best;
    }

    private static AutismRotationUtil.Rotation aimAtEntity(Minecraft mc, Vec3 eyes, Entity entity) {
        double reachSq = square(mc.player.entityInteractionRange());
        AABB box = entity.getBoundingBox();
        if (box.getXsize() <= 0.0D || box.getYsize() <= 0.0D || box.getZsize() <= 0.0D) return null;
        for (Vec3 point : samplePoints(box)) {
            if (eyes.distanceToSqr(point) > reachSq) continue;
            if (!hasLineOfSight(mc, eyes, point)) continue;
            return AutismRotationUtil.lookingAt(point, eyes);
        }
        return null;
    }

    private static boolean hasLineOfSight(Minecraft mc, Vec3 eyes, Vec3 point) {
        BlockHitResult hit = mc.level.clip(new ClipContext(
            eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private static AABB blockAim(Minecraft mc, BlockPos pos) {
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        AABB local = shape.isEmpty() ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D) : shape.bounds();
        return local.move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static List<Vec3> samplePoints(AABB box) {
        double insetX = Math.min(INSET, box.getXsize() / 4.0D);
        double insetY = Math.min(INSET, box.getYsize() / 4.0D);
        double insetZ = Math.min(INSET, box.getZsize() / 4.0D);
        AABB inner = box.contract(0.0D, 0.0D, 0.0D).inflate(-insetX, -insetY, -insetZ);
        if (inner.getXsize() <= 0.0D || inner.getYsize() <= 0.0D || inner.getZsize() <= 0.0D) inner = box;
        List<Vec3> points = new ArrayList<>(SCAN.length * SCAN.length * SCAN.length);
        for (double y : SCAN) for (double x : SCAN) for (double z : SCAN) {
            points.add(new Vec3(
                inner.minX + inner.getXsize() * x,
                inner.minY + inner.getYsize() * y,
                inner.minZ + inner.getZsize() * z));
        }
        return points;
    }

    private static double square(double value) {
        return value * value;
    }

    public static float outgoingYaw(LocalPlayer player, float vanillaYaw) {
        AutismRotationUtil.Rotation rotation = rotationFor(player);
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingPitch(LocalPlayer player, float vanillaPitch) {
        AutismRotationUtil.Rotation rotation = rotationFor(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static Vec3 viewVector(LocalPlayer player, Vec3 vanillaVector) {
        AutismRotationUtil.Rotation rotation = rotationFor(player);
        return rotation == null ? vanillaVector : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
    }

    private static AutismRotationUtil.Rotation rotationFor(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || mc == null || player != mc.player) return null;
        return active;
    }
}
