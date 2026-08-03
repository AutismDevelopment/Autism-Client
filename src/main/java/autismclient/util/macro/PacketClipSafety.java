package autismclient.util.macro;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class PacketClipSafety {
    public static final double DIRECT_FALL_LIMIT = 3.0D;
    public static final double FALL_RESET_NUDGE = 0.0625D;

    private PacketClipSafety() {
    }

    public record Step(Vec3 position, boolean onGround) {
        public Step {
            if (position == null) position = Vec3.ZERO;
        }
    }

    public static List<Step> positionSteps(Vec3 from, Vec3 to, boolean finalOnGround) {
        if (from == null || to == null) return List.of();
        if (to.y - from.y >= -DIRECT_FALL_LIMIT) return List.of(new Step(to, finalOnGround));
        Vec3 reset = new Vec3(to.x, to.y + FALL_RESET_NUDGE, to.z);
        return List.of(
            new Step(to, false),
            new Step(reset, false),
            new Step(to, finalOnGround)
        );
    }

    public static Vec3 sweptCollide(Entity entity, Vec3 from, Vec3 to) {
        if (entity == null || entity.level() == null || from == null || to == null) return Vec3.ZERO;
        AABB moved = entity.getBoundingBox().move(from.subtract(entity.position()));
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() <= 1.0E-10) return delta;
        List<VoxelShape> shapes = new ArrayList<>();
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, moved.expandTowards(delta))) {
            shapes.add(shape);
        }
        return Entity.collideBoundingBox(entity, delta, moved, entity.level(), shapes);
    }

    public static boolean sweptClear(Entity entity, Vec3 from, Vec3 to) {
        if (entity == null || from == null || to == null) return false;
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() <= 1.0E-10) return true;
        return sweptCollide(entity, from, to).equals(delta);
    }
}
